package com.shelf.launcher.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Power saving while nobody is using the handheld (Appearance › Behind the dock › Save power when idle).
 *
 * After [IDLE_MS] without a button, stick or touch the home screen counts as *idle*: the animated
 * background drops to ~15 frames a second, the focus pulse, shimmer and electric bolts hold still.
 * After [DEEP_MS] it is *deeply idle*: the background drops to ~8 frames a second. The first press
 * brings everything back at once. A music visualizer keeps its full speed while music plays — that's
 * what you're watching then.
 *
 * Input is reported from MainActivity's dispatch methods, so every kind of input counts.
 */
object HomeIdle {
    const val IDLE_MS = 30_000L
    const val DEEP_MS = 3 * 60_000L

    /** Switched by the setting; when off the home screen never counts as idle. */
    @Volatile var enabled = true

    var idle by mutableStateOf(false)
        private set
    var deep by mutableStateOf(false)
        private set

    @Volatile private var lastInput = SystemClock.elapsedRealtime()
    /** When the last input came (elapsedRealtime). */
    val lastInputAt: Long get() = lastInput
    /** Idle focus mode (hero carousel): the UI is faded out until the next input. */
    var focusHide by mutableStateOf(false)

    /** Any input. Cheap: only writes state when the home screen was idle. */
    fun poke() {
        lastInput = SystemClock.elapsedRealtime()
        if (focusHide) focusHide = false
        if (idle) idle = false
        if (deep) deep = false
    }

    /** Keeps [idle] / [deep] up to date; runs while the home screen is on screen (one wake-up per step). */
    suspend fun watch() {
        while (true) {
            if (!enabled) {
                if (idle) idle = false
                if (deep) deep = false
                delay(IDLE_MS)
                continue
            }
            val since = SystemClock.elapsedRealtime() - lastInput
            if (since >= IDLE_MS && !idle) idle = true
            if (since >= DEEP_MS && !deep) deep = true
            val next = when {
                since < IDLE_MS -> IDLE_MS - since
                since < DEEP_MS -> DEEP_MS - since
                else -> 10_000L
            }
            delay(next.coerceIn(500L, 30_000L))
        }
    }
}

/**
 * True while something opaque or nearly so covers the home screen (screensaver, settings, the details
 * window, a category page): the animated background then holds a still frame instead of drawing
 * frames nobody sees.
 */
val LocalSceneCovered = compositionLocalOf { false }

/**
 * Update 121: the slowest a background may step on this screen right now (ms per frame, 0 = no limit),
 * besides the background quality and [HomeIdle]. ~15 fps while a popup leaves the home screen showing
 * round its edges, and on a second screen nobody has touched for a while ([SecondIdle]). A music
 * visualizer keeps its full speed while music plays.
 */
val LocalSceneMinFrameMs = compositionLocalOf { 0 }

/** Update 125: a scene that is the whole point of the screen (the screensaver's theme): never slowed down for being idle. */
val LocalSceneFullSpeed = compositionLocalOf { false }

/**
 * Update 121: the second screen counts as not in use once nobody has touched it for [IDLE_MS]: its
 * animated background then runs at ~15 frames a second (Appearance › Second screen slower when unused).
 * The first touch there brings it back to full speed at once.
 */
object SecondIdle {
    const val IDLE_MS = 20_000L

    var idle by mutableStateOf(false)
        private set

    @Volatile private var last = SystemClock.elapsedRealtime()

    /** A touch on the second screen. Cheap: only writes state when it was idle. */
    fun poke() {
        last = SystemClock.elapsedRealtime()
        if (idle) idle = false
    }

    /** Keeps [idle] up to date while the second screen is showing (one wake-up per step). */
    suspend fun watch() {
        while (true) {
            val since = SystemClock.elapsedRealtime() - last
            if (since >= IDLE_MS) {
                if (!idle) idle = true
                delay(5_000L)
            } else delay((IDLE_MS - since).coerceIn(500L, IDLE_MS))
        }
    }
}

/**
 * One slow clock for the small looping effects (focus pulse, mastered shimmer): about 24 updates a
 * second instead of every display frame (60–120 Hz), shared by every card on screen, and stopped while
 * the handheld sits idle. Draw code reads [seconds] in the draw phase, so only drawing repeats.
 */
object EffectClock {
    val seconds = mutableFloatStateOf(0f)
    /** Update 144: the mastered shimmer's own clock: it keeps going when the home screen goes idle (30 s without a
     *  button) and only stops at the deep idle (3 minutes) — the shine used to freeze after half a minute. */
    val shimmer = mutableFloatStateOf(0f)
}

/** Drives [EffectClock]; placed once on the home screen. */
@Composable
internal fun EffectClockDriver() {
    val active = rememberIsActive() && rememberAnimationsEnabled()
    val on = active && !HomeIdle.idle
    // Update 144: the shimmer's clock (see EffectClock.shimmer), slower while idle (~12 a second instead of ~24).
    val shimmerOn = active && !HomeIdle.deep
    val idleNow = HomeIdle.idle
    LaunchedEffect(shimmerOn, idleNow) {
        if (!shimmerOn) return@LaunchedEffect
        val base = EffectClock.shimmer.floatValue
        val start = SystemClock.uptimeMillis()
        val step = if (idleNow) 84L else 42L
        while (true) {
            EffectClock.shimmer.floatValue = (base + (SystemClock.uptimeMillis() - start) / 1000f) % 3600f
            delay(step)
        }
    }
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        val base = EffectClock.seconds.floatValue
        val start = SystemClock.uptimeMillis()
        while (true) {
            EffectClock.seconds.floatValue = (base + (SystemClock.uptimeMillis() - start) / 1000f) % 3600f
            delay(42)
        }
    }
}

/**
 * The focus pulse (0.35…1, breathing every 2.2 s) for the card you're on; a steady 0.75 when [animate]
 * is false, with Lite effects, or while idle. Updated ~20 times a second rather than every frame.
 */
@Composable
internal fun rememberFocusPulse(animate: Boolean): Float {
    var pulse by remember { mutableFloatStateOf(0.75f) }
    val on = animate && rememberIsActive() && rememberAnimationsEnabled() && !HomeIdle.idle
    LaunchedEffect(on) {
        if (!on) { pulse = 0.75f; return@LaunchedEffect }
        val start = SystemClock.uptimeMillis()
        while (true) {
            val ph = ((SystemClock.uptimeMillis() - start) % 2200L) / 1100f
            val tri = if (ph <= 1f) ph else 2f - ph
            pulse = 0.35f + 0.65f * (tri * tri * (3f - 2f * tri))
            delay(50)
        }
    }
    return pulse
}
