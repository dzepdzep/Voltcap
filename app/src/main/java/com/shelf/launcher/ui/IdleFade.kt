package com.shelf.launcher.ui

import android.animation.ValueAnimator
import android.view.View
import com.shelf.launcher.data.perf.PerfSettings

/**
 * Update 224 (#107 / #108 / #84): an overlay control that quiets down when it isn't used — fully visible after a touch,
 * faded to Settings › Overlay › "Faded visibility" after "Fade after", invisible after "Fully hide after" (total time
 * since the last touch, not added on top; "Never" keeps it faded). Only its alpha changes: the view stays VISIBLE,
 * enabled and the same size, so an invisible control is still exactly where it was and the first tap on it both shows
 * it again (the owner calls [poke] on ACTION_DOWN) and does what it does.
 *
 * Two posted callbacks per touch (removed on the next touch, [stop] and when the view leaves the window) — no loop.
 * [canHide]: false keeps it at the faded level at most (e.g. a folded overlay's only tab).
 */
internal class IdleFade(private val view: View, private val canHide: () -> Boolean = { true }) {
    private val fadeStep = Runnable { to(PerfSettings.state.value.ctrlFadedPct.coerceIn(0, 100) / 100f) }
    private val hideStep = Runnable { if (canHide()) to(0f) }

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { poke() }
            override fun onViewDetachedFromWindow(v: View) { cancel() }
        })
    }

    /** A touch (or the control was just shown): full visibility now, the countdown starts again. */
    fun poke() {
        cancel()
        view.alpha = 1f
        val c = PerfSettings.state.value
        if (!c.ctrlFade) return
        val fadeMs = c.ctrlFadeAfterS.coerceIn(1, 120) * 1000L
        view.postDelayed(fadeStep, fadeMs)
        if (c.ctrlHideAfterS > 0) view.postDelayed(hideStep, maxOf(c.ctrlHideAfterS * 1000L, fadeMs + 1000L))
    }

    /** Back to plain: fully visible, no countdown (the setting was switched off, or the control isn't in use). */
    fun stop() {
        cancel()
        view.alpha = 1f
    }

    private fun cancel() {
        view.removeCallbacks(fadeStep)
        view.removeCallbacks(hideStep)
        view.animate().cancel()
    }

    private fun to(alpha: Float) {
        // Reduced motion (animations off in Android's settings): straight to the new opacity.
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) { view.alpha = alpha; return }
        view.animate().alpha(alpha).setDuration(450L).start()
    }
}
