package com.shelf.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShelfNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val isMessage: Boolean,
    /** Can't be swiped away (music, sync services…) — not counted on the bell. */
    val ongoing: Boolean = false
)

/**
 * The active media session, as the player and screensaver need it. Position
 * is reported the way Android does: the position at [positionAt]
 * (SystemClock.elapsedRealtime), advancing at [speed] while playing — see
 * [currentPosition], so the UI can tick smoothly without re-reading it.
 */
data class NowPlaying(
    val title: String?,
    val artist: String?,
    val packageName: String,
    val isPlaying: Boolean,
    val album: String? = null,
    val art: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val positionAt: Long = 0L,
    val speed: Float = 1f,
    val canSeek: Boolean = false
) {
    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || positionAt == 0L) return positionMs
        val advanced = positionMs + ((nowElapsed - positionAt) * speed).toLong()
        return if (durationMs > 0) advanced.coerceIn(0L, durationMs) else advanced.coerceAtLeast(0L)
    }
}

/**
 * Reads active notifications so they can be shown in-launcher.
 *
 * This needs "Notification access", which Android treats as a sensitive
 * permission: it can only be granted by the user in a dedicated Settings
 * screen, never by a runtime dialog. The same grant is what unlocks
 * MediaSessionManager.getActiveSessions, so notifications and the music
 * controls stand or fall together.
 *
 * Nothing is stored or sent anywhere — notifications live in memory only, and
 * are dropped as soon as they're dismissed.
 */
class ShelfNotificationListener : NotificationListenerService() {

    // Battery: bursts of posted/removed callbacks are coalesced (500 ms), and while nothing
    // observes the flow (UI not resumed) the work is skipped and only marked dirty; the first
    // subscriber that comes back triggers the refresh.
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val debounced = Runnable { refreshIfObserved() }
    private var watcher: kotlinx.coroutines.Job? = null
    private var scope: kotlinx.coroutines.CoroutineScope? = null
    @Volatile private var dirty = true

    override fun onListenerConnected() {
        instance = this
        scope?.cancel() // connected again without a disconnect in between: never two watchers
        val s = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        scope = s
        watcher = s.launch {
            _notifications.subscriptionCount.collect { if (it > 0 && dirty) refresh() }
        }
        scheduleRefresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        handler.removeCallbacks(debounced)
        scope?.cancel()
        scope = null
        _notifications.value = emptyList()
        dirty = true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = scheduleRefresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = scheduleRefresh()

    private fun scheduleRefresh() {
        dirty = true
        handler.removeCallbacks(debounced)
        handler.postDelayed(debounced, 500)
    }

    private fun refreshIfObserved() {
        if (_notifications.subscriptionCount.value > 0) refresh()
        // else: stays dirty until someone observes the flow (see onListenerConnected).
    }

    private fun refresh() {
        dirty = false
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val pm = packageManager

        val mapped = active.mapNotNull { sbn ->
            val extras = sbn.notification?.extras ?: return@mapNotNull null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (title.isNullOrBlank() && text.isNullOrBlank()) return@mapNotNull null

            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)

            ShelfNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = label,
                title = title,
                text = text,
                postedAt = sbn.postTime,
                // "Messages" isn't a separate API — reading SMS directly is a
                // restricted permission. Android's own messaging category is
                // the honest approximation.
                isMessage = sbn.notification?.category == Notification.CATEGORY_MESSAGE,
                ongoing = sbn.isOngoing
            )
        }.sortedByDescending { it.postedAt }

        _notifications.value = mapped
    }

    companion object {
        // Only the connected service instance can cancel a notification, so
        // it's held here for the UI to reach. Cleared on disconnect so a stale
        // reference can't outlive the binding.
        @Volatile
        private var instance: ShelfNotificationListener? = null

        /** Brings the list up to date if notifications changed while nobody was observing it. */
        fun flushIfDirty() {
            instance?.let { l -> if (l.dirty) l.handler.post { l.refresh() } }
        }

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        /** Many at once, in one call to Android (clearing one by one was slow with a long list). */
        fun dismissAll(keys: List<String>) {
            if (keys.isEmpty()) return
            runCatching { instance?.cancelNotifications(keys.toTypedArray()) }
        }

        private val _notifications = MutableStateFlow<List<ShelfNotification>>(emptyList())
        val notifications: StateFlow<List<ShelfNotification>> = _notifications.asStateFlow()

        /**
         * Notification access really switched on for THIS listener (not just "some component of the
         * package"), read from Android each time. Android 8.1+ answers per component.
         */
        fun hasAccess(context: Context): Boolean = runCatching {
            val component = ComponentName(context, ShelfNotificationListener::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                if (nm != null) return@runCatching nm.isNotificationListenerAccessGranted(component)
            }
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (flat != null) flat.split(':').any { ComponentName.unflattenFromString(it) == component }
            else NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }.getOrDefault(false)

        /** Android has bound the listener right now (access alone isn't enough after a restart or update). */
        val connected: Boolean get() = instance != null

        /**
         * Access is on but Android never re-bound the listener (common after a reboot or an app update):
         * ask it to. Without this, music controls and the notification list stay empty though "allowed".
         */
        fun rebindIfNeeded(context: Context) {
            if (instance != null || android.os.Build.VERSION.SDK_INT < 24 || !hasAccess(context)) return
            runCatching { requestRebind(ComponentName(context, ShelfNotificationListener::class.java)) }
        }

        /** Active media session, if any app is playing. Requires the same
         * notification-access grant. */
        /** The app that was playing most recently: preferred while a player is between states. */
        @Volatile private var lastActivePackage: String? = null

        private fun isBusy(state: Int?): Boolean = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING ||
            state == PlaybackState.STATE_SKIPPING_TO_NEXT || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
            state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM || state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING

        /** Whether any player is playing or between songs (the background music waits for that). */
        fun anyBusy(context: Context): Boolean {
            if (!hasAccess(context)) return false
            return runCatching {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return false
                manager.getActiveSessions(ComponentName(context, ShelfNotificationListener::class.java))
                    .any { isBusy(it.playbackState?.state) }
            }.getOrDefault(false)
        }

        /** The cover handed out last time: kept so the same picture stays the same object. */
        @Volatile private var lastArt: Bitmap? = null

        // (Update 229, #03: each read gave a new copy of the same cover, so the album art reloaded / faded on every
        // play / pause / position change) The same picture as last time comes back as last time's object. Never recycled.
        private fun sameArt(art: Bitmap?): Bitmap? {
            if (art == null) return null
            val last = lastArt
            if (last != null && last !== art && !last.isRecycled && !art.isRecycled &&
                last.width == art.width && last.height == art.height &&
                runCatching { last.sameAs(art) }.getOrDefault(false)) return last
            lastArt = art
            return art
        }

        fun nowPlaying(context: Context): Pair<NowPlaying, MediaController>? {
            if (!hasAccess(context)) return null
            return try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                    as? MediaSessionManager ?: return null
                val component = ComponentName(context, ShelfNotificationListener::class.java)
                val sessions = manager.getActiveSessions(component)
                // Between two songs a player reports "skipping" / "connecting" / "buffering" for a moment.
                // Those count as playing: they used to make this fall back to whichever session came
                // first — often another, paused app — so a quick second "next" went to that app, which
                // started up, took the audio and paused the real music.
                fun active(c: MediaController) = isBusy(c.playbackState?.state)
                val controller = sessions.firstOrNull { active(it) }
                    ?: lastActivePackage?.let { pkg -> sessions.firstOrNull { it.packageName == pkg } }
                    ?: sessions.firstOrNull() ?: return null
                if (active(controller)) lastActivePackage = controller.packageName

                val metadata = controller.metadata
                val state = controller.playbackState
                NowPlaying(
                    title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                    packageName = controller.packageName,
                    isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
                    album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    // Apps put the cover in different keys; try them in order.
                    art = sameArt(metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)),
                    durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
                    positionMs = state?.position?.coerceAtLeast(0L) ?: 0L,
                    positionAt = state?.lastPositionUpdateTime ?: 0L,
                    speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                    canSeek = state != null && (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                ) to controller
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}
