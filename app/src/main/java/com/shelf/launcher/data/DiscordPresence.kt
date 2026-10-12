package com.shelf.launcher.data

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * "Playing GoldenEye 007" on your Discord profile, through Discord's own
 * Social SDK (Rich Presence). No Discord login inside Voltcap: the SDK hands
 * the status to the Discord app on the same device, which has to be
 * installed and signed in.
 *
 * All SDK calls run on one background thread, as the SDK expects, and its
 * callbacks are pumped from that same thread.
 */
object DiscordPresence {
    /** The "Voltcap" application registered on discord.com/developers. */
    private const val APPLICATION_ID = 1552821662812995715L
    /** Art asset uploaded under Rich Presence › Art Assets. */
    private const val LOGO_ASSET = "voltcap"

    private const val TAG = "DiscordPresence"
    private const val KEY_REFRESH = "refresh_token"
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "discord-sdk").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + thread.asCoroutineDispatcher())
    private var pump: Job? = null
    @Volatile private var available: Boolean? = null
    @Volatile private var lastStatus: Int = -1

    private external fun nativeInit(applicationId: Long): Boolean
    private external fun nativeRunCallbacks()
    private external fun nativeStatus(): Int
    private external fun nativeSetPlaying(
        details: String, state: String?, startSeconds: Long,
        largeImage: String?, largeText: String?, smallImage: String?, smallText: String?
    )
    private external fun nativeClear()
    private external fun nativeAuthorize(): Boolean
    private external fun nativeRefresh(refreshToken: String): Boolean
    private external fun nativeAuthState(): Int
    private external fun nativeAuthError(): String
    private external fun nativeTakeRefreshToken(): String?

    @Volatile private var appContext: android.content.Context? = null
    @Volatile private var linkState: Int = 0
    @Volatile private var linkError: String = ""

    // The refresh token is a credential: encrypted at rest (old plain "discord" file migrates in once).
    private fun prefs() = appContext?.let { SecureStore.open(it, "discord_encrypted", "Discord", listOf("discord")).prefs }

    /** Call once from the activity's onCreate (the SDK needs it for its Android side). */
    fun attach(activity: Activity) {
        appContext = activity.applicationContext
        enabled = true
        authUntil = System.currentTimeMillis() + 30_000L // quiet sign-in with the saved token
        val ref = java.lang.ref.WeakReference(activity)
        // Loading Discord's 14 MB library takes a moment: it happens on the SDK's own thread, so the
        // home screen's first frames (and the intro) don't wait for it. Everything else the SDK does
        // is queued on that same thread, so nothing can run before it's ready.
        scope.launch {
            val loaded = runCatching {
                System.loadLibrary("discord_partner_sdk")
                System.loadLibrary("voltcap_discord")
            }.onFailure { Log.w(TAG, "Discord SDK unavailable", it) }.isSuccess
            val act = ref.get()
            if (!loaded || act == null) {
                available = false
                return@launch
            }
            // The SDK keeps the activity for its Android side; handed over on the main thread.
            val done = java.util.concurrent.CountDownLatch(1)
            var engineOk = false
            act.runOnUiThread {
                engineOk = runCatching { com.discord.socialsdk.DiscordSocialSdkInit.setEngineActivity(act) }
                    .onFailure { Log.w(TAG, "Discord SDK unavailable", it) }.isSuccess
                done.countDown()
            }
            if (!done.await(10, java.util.concurrent.TimeUnit.SECONDS) || !engineOk) {
                available = false
                return@launch
            }
            // Linked before: sign back in quietly with the saved token.
            if (start()) prefs()?.getString(KEY_REFRESH, null)?.let { token ->
                runCatching { nativeRefresh(token) }
            }
        }
    }

    /**
     * "Connect Discord": opens Discord's sign-in (the Discord app, or a browser
     * tab) once. Afterwards Voltcap keeps a refresh token and signs back in
     * by itself. Needed on phones and handhelds — Rich Presence there only
     * works for a linked account.
     */
    fun connect() {
        scope.launch {
            if (!start()) { linkState = 3; linkError = "Discord's library couldn't start on this device."; return@launch }
            val ok = runCatching { nativeAuthorize() }.getOrDefault(false)
            authUntil = System.currentTimeMillis() + 120_000L
            ensurePump()
            if (!ok) { linkState = 3; linkError = "This Discord SDK can't link accounts." }
        }
    }

    /** Forgets the link (the next start won't sign in). */
    fun disconnect() {
        prefs()?.edit()?.remove(KEY_REFRESH)?.apply()
        linkState = 0
        clear()
    }

    /** 0 not linked, 1 signing in, 2 linked, 3 failed. */
    fun linkState(): Int = linkState

    fun linkError(): String = linkError

    fun hasSavedLink(): Boolean = prefs()?.getString(KEY_REFRESH, null) != null

    /** Creates the client and starts pumping its callbacks. False if it can't run here. */
    private fun start(): Boolean {
        available?.let { return it }
        val ok = runCatching { nativeInit(APPLICATION_ID) }
            .onFailure { Log.w(TAG, "Discord client failed", it) }.getOrDefault(false)
        available = ok
        if (ok) ensurePump()
        return ok
    }

    // ---- Callback pump (battery) ----
    // The SDK only delivers callbacks when nativeRunCallbacks() is called. The pump therefore runs
    // only while it has something to deliver, and parks itself otherwise:
    //   - 250 ms while signing in or connecting (auth redirects are time sensitive),
    //   - 1 s while a game session is shown, or while Voltcap is in the foreground with presence
    //     on (the slow rate is an assumption that the SDK tolerates it; events are queued
    //     inside the SDK and delivered on the next call, so nothing is lost),
    //   - parked (no thread wakeups at all) in the background with no game running.
    // Every wake-up path (showPlaying, connect, setForeground) calls ensurePump() again.
    @Volatile private var enabled = false
    @Volatile private var foreground = false
    /** A sign-in was just started (connect / saved-token refresh): pump fast until then. */
    @Volatile private var authUntil = 0L

    /** Voltcap came to / left the foreground (called from the ViewModel's resume/pause hooks). */
    fun setForeground(on: Boolean) {
        foreground = on
        if (on && available == true) scope.launch { ensurePump() }
    }

    /** Presence switched on/off in Settings (attach() also turns it on). */
    fun setEnabled(on: Boolean) {
        enabled = on
        if (on && available == true) scope.launch { ensurePump() }
    }

    private fun pumpDelayMs(): Long? {
        if (linkState == 1 || System.currentTimeMillis() < authUntil) return 250L
        // Screen off / locked: the presence only needs an occasional keep-alive, not one a second.
        if ((current != null || (enabled && foreground)) && !com.shelf.launcher.ui.ScreenState.interactive.value) return 20_000L
        // A game is running (GameMode): the status only needs an occasional pump, not one a second.
        if (current != null && com.shelf.launcher.data.GameMode.active) return 5_000L
        if (current != null || (enabled && foreground)) return 1000L
        return null // park
    }

    private val pumpLock = Any()

    private fun ensurePump() {
        if (available != true) return
        synchronized(pumpLock) {
            if (pump?.isActive == true) return
            if (pumpDelayMs() == null) return
            pump = scope.launch {
                val self = kotlinx.coroutines.currentCoroutineContext()[Job]
                try {
                    while (isActive) {
                        runCatching {
                            nativeRunCallbacks()
                            lastStatus = nativeStatus()
                            linkState = nativeAuthState()
                            if (linkState == 3) linkError = nativeAuthError()
                            // Each sign-in hands out a new refresh token — keep the newest.
                            nativeTakeRefreshToken()?.let { token ->
                                prefs()?.edit()?.putString(KEY_REFRESH, token)?.apply()
                            }
                        }
                        delay(pumpDelayMs() ?: break)
                    }
                } finally {
                    // Only clear the field if it still points at this pump (a newer one may have replaced it).
                    synchronized(pumpLock) { if (pump === self) pump = null }
                }
            }
        }
    }

    /**
     * Shows [game] as being played on [platform], counting up from now.
     * [coverUrl] (an https link) becomes the big picture when Discord accepts
     * it; the Voltcap logo is the small one.
     */
    fun showPlaying(game: String, platform: String, coverUrl: String?) {
        val playing = Playing(game, platform, coverUrl, System.currentTimeMillis() / 1000)
        current = playing
        scope.launch {
            if (!start()) return@launch
            ensurePump()
            runCatching {
                nativeSetPlaying(
                    details = game,
                    state = platform,
                    startSeconds = playing.start, // same start as later status updates, so the timer never jumps
                    largeImage = coverUrl?.takeIf { it.startsWith("https://") } ?: LOGO_ASSET,
                    largeText = game,
                    smallImage = LOGO_ASSET,
                    smallText = "Voltcap"
                )
            }.onFailure { Log.w(TAG, "Couldn't set presence", it) }
        }
    }

    private data class Playing(val game: String, val platform: String, val cover: String?, val start: Long)
    @Volatile private var current: Playing? = null

    /**
     * The game's own status from RetroAchievements (Rich Presence: "Route 3 ·
     * 50 Pokémon caught") as the second line under the game, keeping the timer.
     */
    fun showStatus(status: String) {
        val playing = current ?: return
        val line = "${playing.platform} · $status".take(128)
        scope.launch {
            if (available != true) return@launch
            runCatching {
                nativeSetPlaying(
                    details = playing.game,
                    state = line,
                    startSeconds = playing.start,
                    largeImage = playing.cover?.takeIf { it.startsWith("https://") } ?: LOGO_ASSET,
                    largeText = playing.game,
                    smallImage = LOGO_ASSET,
                    smallText = "Voltcap"
                )
            }.onFailure { Log.w(TAG, "Couldn't update presence", it) }
        }
    }

    /** Back in Voltcap: nothing is being played any more. */
    fun clear() {
        current = null
        scope.launch {
            if (available != true) return@launch
            runCatching { nativeClear() }
        }
    }

    /** -1 not running, else the SDK's client status (3 = ready). For settings. */
    fun status(): Int = if (available == true) lastStatus else -1

    /** Whether the SDK loaded at all on this device. */
    fun isAvailable(): Boolean = available == true
}
