package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * QA system self-test (tools/qa/README.md › "Testing the tester"): controlled, reversible faults, so the detectors can be
 * shown to catch what they claim. Test builds with the hook file only (`voltcap_test_hooks`), this session only, never
 * saved: `am start … --es voltcap_test_fault "counter,flash:20,jank:10"` (`off` ends it). Every injected event is logged
 * (logcat VoltcapQA `fault …`) so a run can count what the recording / logs found against what was injected.
 *
 * - `counter`: a 12-bit frame number drawn as black/white squares at the bottom-left corner on every app frame — the
 *   recording shows which app frames it really captured (tools/qa/tools/counter.py).
 * - `flash:N`: N single-frame magenta squares (one app frame each) at random moments.
 * - `jank:N`: N frames held 45 ms on the main thread (a dropped frame / microstutter).
 * - `focus_skip:N`: the next N ◀ ▶ moves jump two games (a wrong-focus defect for the focus-sequence check).
 * - `slide_old`, `rise_old`, `stale_soft`: put back the old behaviour of fixed defects D1, D2, D3 (known flashes).
 */
internal object QaFault {
    @Volatile private var counts: Map<String, Int> = emptyMap()
    /** Bumped when the set changes, so the overlay starts / stops. */
    val version = mutableIntStateOf(0)

    fun set(spec: String) {
        counts = if (spec == "off") emptyMap() else spec.split(',').mapNotNull { p ->
            val t = p.trim(); if (t.isEmpty()) null else t.substringBefore(':') to (t.substringAfter(':', "1").toIntOrNull() ?: 1)
        }.toMap()
        version.intValue++
        QaTrace.event("fault", "set $spec")
    }

    fun on(name: String): Boolean = counts.containsKey(name)
    fun left(name: String): Int = counts[name] ?: 0

    /** Uses one of [name]'s events; false when none are left. */
    @Synchronized fun take(name: String): Boolean {
        val n = counts[name] ?: return false
        if (n <= 0) return false
        counts = counts + (name to n - 1)
        return true
    }

    /** moveFocus: the wrong step, while focus_skip events are left. */
    fun focusDelta(delta: Int): Int =
        if ((delta == 1 || delta == -1) && take("focus_skip")) { QaTrace.event("fault", "focus_skip $delta->${delta * 2}"); delta * 2 } else delta
}

@Composable
internal fun QaFaultOverlay() {
    val v = QaFault.version.intValue
    val active = QaFault.on("counter") || QaFault.on("flash") || QaFault.on("jank")
    if (!active) return
    var frame by remember { mutableIntStateOf(0) }
    var flashFrame by remember { mutableStateOf(-1) }
    LaunchedEffect(v) {
        val rnd = java.util.Random(350L)
        var nextFlash = 60 + rnd.nextInt(60)
        var nextJank = 90 + rnd.nextInt(90)
        var n = 0
        while (true) {
            withFrameNanos { }
            n++
            if (n == nextFlash) {
                if (QaFault.take("flash")) { flashFrame = n; QaTrace.event("fault", "flash frame=$n") }
                nextFlash = n + 50 + rnd.nextInt(80)
            }
            if (n == nextJank) {
                if (QaFault.take("jank")) { QaTrace.event("fault", "jank frame=$n"); Thread.sleep(45) }
                nextJank = n + 70 + rnd.nextInt(90)
            }
            frame = n
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val f = frame
        if (QaFault.on("counter")) {
            val s = 16f
            val y = size.height - s
            drawRect(Color.Black, Offset(0f, y - 4f), Size(s * 12 + 8f, s + 4f))
            for (b in 0 until 12) if ((f shr b) and 1 == 1) drawRect(Color.White, Offset(4f + b * s, y), Size(s, s))
        }
        if (f == flashFrame) drawRect(Color(0xFFFF00FF), Offset(size.width * 0.62f, size.height * 0.34f), Size(160f, 160f))
    }
}
