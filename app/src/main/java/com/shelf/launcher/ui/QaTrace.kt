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
        QaWalk.install()
        QaSampler.startIfAsked(activity)
        event("start", "refresh=${"%.1f".format(hz)}Hz budget=${budgetNs / 100_000 / 10.0}ms")
    }

    /**
     * State churn: which snapshot states change most often (logged once a second as `churn N× desc | …`). A state that
     * changes every frame and is read in composition rebuilds its readers every frame — this names it.
     */
    private val churn = java.util.concurrent.ConcurrentHashMap<Int, Pair<String, java.util.concurrent.atomic.AtomicInteger>>()
    @Volatile private var churnSince = 0L
    private var churnHandle: androidx.compose.runtime.snapshots.ObserverHandle? = null
    /**
     * Only on request (`--ez voltcap_test_churn true`, false stops it): naming every changed state on the main thread is
     * far too costly to leave on — left on with the hook file it made the home screen crawl (QA defect D21).
     */
    fun watchChurn(want: Boolean = true) {
        QaInval.on = want && on
        if (!want) { churnHandle?.dispose(); churnHandle = null; churn.clear(); return }
        if (!on || churnHandle != null) return
        churnHandle = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { changed, _ ->
            for (o in changed) {
                val k = System.identityHashCode(o)
                churn.getOrPut(k) {
                    // Never toString() an arbitrary value (pass 2, D21: a state holding the game list took seconds):
                    // the state's class + the value's class, and the value itself only when it is small.
                    val v = runCatching { (o as? androidx.compose.runtime.State<*>)?.value }.getOrNull()
                    if (v is com.shelf.launcher.ui.theme.ShelfColors) QaInval.themeStates.add(k)
                    val vs = when (v) {
                        null -> "null"
                        is Number, is Boolean, is Enum<*> -> v.toString()
                        is String -> v.take(24)
                        else -> v.javaClass.name.substringAfterLast('.')
                    }
                    "${o.javaClass.simpleName}($vs)@$k" to java.util.concurrent.atomic.AtomicInteger()
                }.second.incrementAndGet()
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (churnSince == 0L) churnSince = now
            if (now - churnSince >= 1000) {
                val top = churn.values.filter { it.second.get() >= 10 }.sortedByDescending { it.second.get() }.take(8)
                if (top.isNotEmpty()) event("churn", top.joinToString(" | ") { "${it.second.get()}x ${it.first}" })
                churn.clear(); churnSince = now
            }
        }
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

    private val counts = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    @Volatile private var countSince = 0L
    /**
     * How often [name] was (re)composed: called from a SideEffect in that composable. Logged once a second as
     * `recomp a=12 b=3 …` (only the ones that ran), so "the home screen rebuilds N times a second" is measured, per part.
     */
    fun count(name: String) {
        if (!on) return
        counts.getOrPut(name) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
        val now = android.os.SystemClock.uptimeMillis()
        if (countSince == 0L) countSince = now
        if (now - countSince >= 1000) {
            val line = counts.entries.filter { it.value.get() > 0 }.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value.getAndSet(0)}" }
            event("recomp", "${now - countSince}ms $line")
            countSince = now
        }
    }

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
