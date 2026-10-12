package com.shelf.launcher.device

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.mutableIntStateOf

/**
 * Adaptive quality for weaker devices: watches the frames Voltcap really draws (Android's frame
 * metrics — it never asks for extra frames, so it costs nothing while the screen is still) and, when
 * the home screen keeps missing its frames, lowers "Automatic" background quality one step for the
 * rest of the session (and from two steps, Automatic lite effects too). Settings chosen by hand are
 * never changed. Start-up, the first frames after resuming and single hiccups don't count.
 */
object FrameWatch {
    /** How many steps Automatic quality was lowered this session (0–2). */
    val steps = mutableIntStateOf(0)

    private const val WINDOW = 120
    private const val SLOW_NS = 34_000_000L
    private const val SLOW_SHARE = 0.35f
    private const val GRACE_MS = 12_000L

    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    @Volatile private var ignoreUntil = 0L
    private var frames = 0
    private var slow = 0
    private var badWindows = 0

    fun start(activity: Activity) {
        if (Build.VERSION.SDK_INT < 24 || listener != null) return
        val t = HandlerThread("voltcap-frames").also { it.start() }
        thread = t
        ignoreUntil = SystemClock.uptimeMillis() + GRACE_MS
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> onFrame(metrics) }
        listener = l
        runCatching { activity.window.addOnFrameMetricsAvailableListener(l, Handler(t.looper)) }
            .onFailure { listener = null; t.quitSafely(); thread = null }
    }

    fun stop(activity: Activity) {
        if (Build.VERSION.SDK_INT < 24) return
        listener?.let { runCatching { activity.window.removeOnFrameMetricsAvailableListener(it) } }
        listener = null
        thread?.quitSafely(); thread = null
    }

    /** Back in front (a game ended, the screen came on): its first frames are always slow. */
    fun pause(ms: Long = 4_000L) { ignoreUntil = maxOf(ignoreUntil, SystemClock.uptimeMillis() + ms) }

    private fun onFrame(m: FrameMetrics) {
        if (Build.VERSION.SDK_INT < 24) return
        if (SystemClock.uptimeMillis() < ignoreUntil) { frames = 0; slow = 0; return }
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        frames++
        if (m.getMetric(FrameMetrics.TOTAL_DURATION) > SLOW_NS) slow++
        if (frames < WINDOW) return
        val bad = slow >= WINDOW * SLOW_SHARE
        frames = 0; slow = 0
        badWindows = if (bad) badWindows + 1 else 0
        // Two slow windows in a row (about 8 s of drawing) before stepping down; then start over.
        if (badWindows >= 2 && steps.intValue < 2) {
            badWindows = 0
            ignoreUntil = SystemClock.uptimeMillis() + 3_000L
            android.os.Handler(android.os.Looper.getMainLooper()).post { steps.intValue = (steps.intValue + 1).coerceAtMost(2) }
        }
    }
}
