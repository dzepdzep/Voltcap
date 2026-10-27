package com.shelf.launcher.ui

/**
 * QA (pass 3): a stall profiler for release builds (simpleperf / am profile can't attach to them). A background thread
 * looks at the main thread's stack every 3 ms; when the main thread stays inside the same code for more than [STALL_MS],
 * or is decoding a picture at all, the stack is logged once (`stall <ms>ms <frames…>`, logcat VoltcapQA). Names are R8's
 * short ones: tools/qa/tools/retrace.py turns them back with the build's mapping.txt.
 *
 * Only with the test hooks AND the file <external files>/voltcap_test_sampler (read at start, so a cold start is covered).
 * The sampler itself costs a few % of one core while it runs.
 */
internal object QaSampler {
    private const val STALL_MS = 24L
    @Volatile private var thread: Thread? = null

    fun startIfAsked(context: android.content.Context) {
        if (thread != null) return
        val f = runCatching { java.io.File(context.getExternalFilesDir(null), "voltcap_test_sampler").exists() }.getOrDefault(false)
        if (!f) return
        val main = android.os.Looper.getMainLooper().thread
        val seen = HashSet<Int>()
        thread = Thread({
            var key = 0; var since = 0L; var logged = false
            var stack: Array<StackTraceElement> = emptyArray()
            while (true) {
                try { Thread.sleep(3) } catch (_: InterruptedException) { return@Thread }
                val st = main.stackTrace
                if (st.isEmpty()) continue
                val top = st.take(6)
                // Idle in the message queue: nothing to report.
                if (top.any { it.methodName == "nativePollOnce" }) { key = 0; continue }
                val k = top.joinToString { it.className + it.methodName }.hashCode()
                val now = android.os.SystemClock.uptimeMillis()
                val decode = top.any { it.className.contains("BitmapFactory") || it.className.contains("ImageDecoder") || it.methodName.contains("decodeBitmap") }
                if (k != key) {
                    if (key != 0 && !logged && now - since >= STALL_MS) report(now - since, stack, seen)
                    key = k; since = now; logged = false; stack = st
                    if (decode && seen.add(st.take(14).joinToString().hashCode())) QaTrace.event("decode", "on main: " + fmt(st))
                } else if (!logged && now - since >= 200) { report(now - since, st, seen); logged = true }
            }
        }, "voltcap-qa-sampler").also { it.isDaemon = true; it.priority = Thread.MAX_PRIORITY; it.start() }
        QaTrace.event("sampler", "on")
    }

    private fun fmt(st: Array<StackTraceElement>) =
        st.take(40).joinToString(" < ") { "${it.className}.${it.methodName}:${it.lineNumber}" }

    private fun report(ms: Long, st: Array<StackTraceElement>, seen: HashSet<Int>) {
        val h = st.take(14).joinToString().hashCode()
        if (!seen.add(h)) return
        QaTrace.event("stall", "${ms}ms " + fmt(st))
    }
}
