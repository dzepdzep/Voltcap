package com.shelf.launcher.ui

/**
 * QA system (tools/qa): test-only event log for the frame-by-frame checks. Off unless the test-hooks flag file exists
 * (`voltcap_test_hooks` in Voltcap's external files folder, which only adb or Voltcap can create) — then it writes
 * one logcat line per event under the tag `VoltcapQA`, with the frame's vsync time, so a screen recording and the
 * events can be lined up:
 *   - `focus <index> <game>` / `category <key>`: what the home screen shows from that frame on;
 *   - `slow <ms> budget=<ms> in=… anim=… layout=… draw=… sync=… cmd=… gpu=…`: a frame that missed the display's
 *     frame time (measured with Android's frame metrics, every app frame — not a sample);
 *   - anything else a check adds with [event].
 * Reads nothing, saves nothing. `adb logcat -s VoltcapQA` (tools/qa/device/vqa.sh pulls it with each run).
 */
internal object QaTrace {
    @Volatile var on = false
        private set
    @Volatile private var budgetNs = 16_666_667L

    fun init(activity: android.app.Activity) {
        on = TestHooks.enabled(activity)
        if (!on) return
        val hz = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) activity.display?.refreshRate else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.refreshRate
        }.getOrNull() ?: 60f
        budgetNs = (1_000_000_000.0 / hz.coerceIn(30f, 240f)).toLong()
        event("start", "refresh=${"%.1f".format(hz)}Hz budget=${budgetNs / 100_000 / 10.0}ms")
    }

    /** Refresh rate changed (the Thor switches 60 / 120 Hz): the frame budget follows. */
    fun refresh(hz: Float) { if (on && hz > 1f) budgetNs = (1_000_000_000.0 / hz).toLong() }

    fun event(kind: String, detail: String = "") {
        if (!on) return
        android.util.Log.i("VoltcapQA", "${android.os.SystemClock.uptimeMillis()} $kind $detail")
    }

    @Volatile private var lastFocus: String? = null
    /** What the home screen shows; logged only when it changes (the home screen rebuilds many times a second). */
    fun focus(what: String) { if (on && what != lastFocus) { lastFocus = what; event("focus", what) } }

    /** From FrameWatch's frame-metrics listener (its own thread). */
    fun frame(m: android.view.FrameMetrics) {
        if (!on || android.os.Build.VERSION.SDK_INT < 24) return
        val total = m.getMetric(android.view.FrameMetrics.TOTAL_DURATION)
        if (total <= budgetNs * 3 / 2) return
        val vsync = if (android.os.Build.VERSION.SDK_INT >= 26) m.getMetric(android.view.FrameMetrics.INTENDED_VSYNC_TIMESTAMP) / 1_000_000 else 0L
        fun ms(id: Int) = "%.1f".format(m.getMetric(id) / 1e6)
        val gpu = if (android.os.Build.VERSION.SDK_INT >= 31) " gpu=${ms(android.view.FrameMetrics.GPU_DURATION)}" else ""
        android.util.Log.i(
            "VoltcapQA",
            "$vsync slow ${"%.1f".format(total / 1e6)} budget=${"%.1f".format(budgetNs / 1e6)} " +
                "in=${ms(android.view.FrameMetrics.INPUT_HANDLING_DURATION)} anim=${ms(android.view.FrameMetrics.ANIMATION_DURATION)} " +
                "layout=${ms(android.view.FrameMetrics.LAYOUT_MEASURE_DURATION)} draw=${ms(android.view.FrameMetrics.DRAW_DURATION)} " +
                "sync=${ms(android.view.FrameMetrics.SYNC_DURATION)} cmd=${ms(android.view.FrameMetrics.COMMAND_ISSUE_DURATION)}$gpu" +
                (if (m.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME) == 1L) " first" else "")
        )
    }
}
