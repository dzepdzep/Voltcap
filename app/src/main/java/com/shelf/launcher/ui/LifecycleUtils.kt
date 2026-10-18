package com.shelf.launcher.ui

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.data.ShelfNotificationListener
import kotlinx.coroutines.CoroutineScope

// Battery: helpers that keep timers and listeners alive only while Voltcap is actually
// on screen. "Active" = the host lifecycle is RESUMED *and* the display is interactive
// (screen on). Anything periodic in the UI should be gated on this and stop at once
// otherwise; the save-sync WorkManager workers are separate and never use these.

/** Process-wide screen on/off state (one receiver for every composable that asks). */
internal object ScreenState {
    val interactive = mutableStateOf(true)
    private var registered = false

    fun ensure(context: Context) {
        val app = context.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        interactive.value = pm?.isInteractive ?: true
        if (registered) return
        registered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                interactive.value = intent.action != Intent.ACTION_SCREEN_OFF
            }
        }
        runCatching {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            })
        }.onFailure { registered = false }
    }

    /** Plain check for non-Compose code. */
    fun isInteractive(context: Context): Boolean =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
}

/** True while the display is on (screen-off/on aware). */
@Composable
internal fun rememberIsInteractive(): Boolean {
    val context = LocalContext.current
    remember { ScreenState.ensure(context); true }
    return ScreenState.interactive.value
}

/** RESUMED and screen on: the only state in which UI timers should run. */
@Composable
internal fun rememberIsActive(): Boolean {
    val resumed = rememberIsResumed()
    val interactive = rememberIsInteractive()
    // Update 187: and you're using Voltcap — not resting while a game or another app has the controller (a two-screen
    // handheld keeps Voltcap resumed on the other screen; BatterySaver.away).
    // (Only the home screen and its second screen rest: Voltcap's own other windows — the companion, the mixer — are
    // what you're using then.)
    val home = androidx.lifecycle.compose.LocalLifecycleOwner.current is com.shelf.launcher.MainActivity
    return resumed && interactive && !(home && com.shelf.launcher.data.BatterySaver.away)
}

/** Like LaunchedEffect, but the block runs only while [rememberIsActive] and is cancelled the moment it stops. */
@Composable
internal fun LaunchedWhileActive(block: suspend CoroutineScope.() -> Unit) {
    val active = rememberIsActive()
    LaunchedEffect(active) { if (active) block() }
}

@Composable
internal fun LaunchedWhileActive(key1: Any?, block: suspend CoroutineScope.() -> Unit) {
    val active = rememberIsActive()
    LaunchedEffect(key1, active) { if (active) block() }
}

@Composable
internal fun LaunchedWhileActive(key1: Any?, key2: Any?, block: suspend CoroutineScope.() -> Unit) {
    val active = rememberIsActive()
    LaunchedEffect(key1, key2, active) { if (active) block() }
}

@Composable
internal fun LaunchedWhileActive(key1: Any?, key2: Any?, key3: Any?, block: suspend CoroutineScope.() -> Unit) {
    val active = rememberIsActive()
    LaunchedEffect(key1, key2, key3, active) { if (active) block() }
}

/** False when the system animator scale is 0 (users switch animations off to save power). Re-read on each resume. */
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val resumed = rememberIsResumed()
    return remember(resumed) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

/**
 * Calls [onChange] (debounced, on the main thread) whenever the active media session, its
 * track or its playback state changes - once right away, then only on real events, using
 * MediaController callbacks plus the active-sessions listener. Registered only while
 * [enabled] and the UI is active. Falls back to a slow 5 s poll only if registering fails
 * while notification access is granted. The playing position is not polled: NowPlaying
 * carries (position, positionAt, speed) and the UI extrapolates it.
 */
@Composable
internal fun NowPlayingUpdates(enabled: Boolean, onChange: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val active = rememberIsActive()
    val latest = rememberUpdatedState(onChange)
    DisposableEffect(enabled, active) {
        if (!enabled || !active) return@DisposableEffect onDispose { }
        val watch = MediaWatch(context) { latest.value() }
        watch.start()
        onDispose { watch.stop() }
    }
}

private class MediaWatch(private val context: Context, private val onChange: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = mutableListOf<MediaController>()
    private var manager: MediaSessionManager? = null
    private var stopped = false
    private var polling = false

    private val fire = Runnable { if (!stopped) onChange() }
    private fun poke() {
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, 150)
    }
    // (Update 229, #03: a new title / cover waited out the debounce too) A track change goes out at once; only the
    // bursts of playback-state changes keep the 150 ms debounce.
    private fun pokeNow() {
        handler.removeCallbacks(fire)
        handler.post(fire)
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = poke()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = pokeNow()
        override fun onSessionDestroyed() = poke()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        bind(list.orEmpty())
        poke()
    }

    private val slowPoll = object : Runnable {
        override fun run() {
            if (stopped) return
            onChange()
            handler.postDelayed(this, 5_000)
        }
    }

    private fun bind(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers.clear()
        list.forEach { c ->
            runCatching { c.registerCallback(callback, handler); controllers += c }
        }
    }

    fun start() {
        onChange() // current state straight away
        if (!ShelfNotificationListener.hasAccess(context)) return // nothing to read without access
        try {
            val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager = m
            val component = ComponentName(context, ShelfNotificationListener::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            bind(m.getActiveSessions(component))
        } catch (e: Exception) {
            polling = true
            handler.postDelayed(slowPoll, 5_000)
        }
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers.clear()
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
    }
}
