package com.shelf.launcher.ui

/**
 * Update 208: debug builds only — how long frames take, logged under the tag "VoltcapPerf" (adb logcat -s VoltcapPerf),
 * so a slow category switch or a sluggish row can be measured on the device instead of guessed at:
 *  - every 5 s while anything is drawn: frames drawn, how many were slow (over 33 ms: under 30 a second), the slowest,
 *    and the picture caches' hits and misses (rememberSoftBitmap: a picture already decoded / decoded now; DerivedArt:
 *    a case's face, back and spine already made / made now);
 *  - after a category switch (L2 / R2, [mark]): how long until the frames settled, and how many were slow on the way.
 * Read from the window's own frame reports (FrameMetrics, on a thread of their own): nothing extra runs on the screen's
 * thread, and nothing at all while the screen is still. Release builds never start it.
 */
internal object PerfDiag {
    private const val TAG = "VoltcapPerf"
    @Volatile var on = false
        private set

    @Volatile var softHits = 0
    @Volatile var softMisses = 0

    private var thread: android.os.HandlerThread? = null

    // The 5-second window.
    private var winStart = 0L
    private var frames = 0
    private var slow = 0
    private var worstNs = 0L
    private var totalNs = 0L
    // A marked event (a category switch) being followed.
    @Volatile private var markLabel: String? = null
    @Volatile private var markAt = 0L
    private var markFrames = 0
    private var markSlow = 0
    private var markWorstNs = 0L
    private var markLastSlowAt = 0L

    fun attach(activity: android.app.Activity) {
        on = (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!on || android.os.Build.VERSION.SDK_INT < 24) return
        runCatching {
            val t = thread ?: android.os.HandlerThread("VoltcapPerf").also { it.start(); thread = it }
            activity.window.addOnFrameMetricsAvailableListener({ _, metrics, _ ->
                frame(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION))
            }, android.os.Handler(t.looper))
        }
    }

    /** Starts following an event ([label]: "Category →"): logged once its frames have settled. */
    fun mark(label: String) {
        if (!on) return
        markLabel = label
        markAt = android.os.SystemClock.uptimeMillis()
    }

    /** One line in the log (debug builds only). */
    fun note(text: String) {
        if (on) android.util.Log.d(TAG, text)
    }

    private fun frame(ns: Long) {
        val now = android.os.SystemClock.uptimeMillis()
        if (winStart == 0L) winStart = now
        frames++; totalNs += ns
        if (ns > 33_000_000L) slow++
        if (ns > worstNs) worstNs = ns
        if (now - winStart >= 5_000L) {
            android.util.Log.d(
                TAG, "frames $frames · slow (>33 ms) $slow · average ${totalNs / frames.coerceAtLeast(1) / 1_000_000} ms · " +
                    "slowest ${worstNs / 1_000_000} ms · pictures ready $softHits / decoded $softMisses · " +
                    "case faces kept ${DerivedArt.hits} / made ${DerivedArt.misses}"
            )
            winStart = now; frames = 0; slow = 0; worstNs = 0L; totalNs = 0L
        }
        val label = markLabel ?: return
        markFrames++
        if (ns > 33_000_000L) { markSlow++; markLastSlowAt = now }
        if (ns > markWorstNs) markWorstNs = ns
        val since = now - markAt
        // Settled: 400 ms without a slow frame (or 4 s at most).
        if ((since > 400L && now - maxOf(markLastSlowAt, markAt) > 400L) || since > 4_000L) {
            val settledIn = (if (markLastSlowAt >= markAt) markLastSlowAt else now) - markAt
            android.util.Log.d(TAG, "$label: settled in $settledIn ms · frames $markFrames · slow $markSlow · slowest ${markWorstNs / 1_000_000} ms")
            markLabel = null; markFrames = 0; markSlow = 0; markWorstNs = 0L; markLastSlowAt = 0L
        }
    }
}
