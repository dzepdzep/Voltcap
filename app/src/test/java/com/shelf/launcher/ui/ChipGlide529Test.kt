package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 529j: the pure parts of the category strips' glide (WholeChipRow's ChipGlide, the category bar's end fades, Live tiles'
 * LivePivot). Plain JUnit on purpose: a Robolectric class whose tests never set content upset the timing of later
 * Robolectric classes in the same run (WinFocusRoboTest, SpineCacheLifecycleRoboTest).
 */
class ChipGlide529Test {
    @Test fun theGlideIsPureAndTheFadesRamp() {
        assertEquals(listOf(0, 58, 138), ChipGlide.starts(intArrayOf(52, 74, 60), 6).toList())
        assertEquals(1f, ChipGlide.inside(10f, 50f, 0f, 100f), 0f)
        assertEquals(0.5f, ChipGlide.inside(75f, 50f, 0f, 100f), 0f)
        assertEquals(0f, ChipGlide.inside(120f, 50f, 0f, 100f), 0f)
        assertEquals(15f, ChipGlide.lerp(10f, 20f, 0.5f), 0f)
        // Category bar (GlidingChipRow): an end's fade grows with the room left that way, from 0 - never on in one frame.
        assertEquals(0f, GlidingChips.fadeStrength(0, 84f), 0f)
        assertEquals(0.5f, GlidingChips.fadeStrength(42, 84f), 0f)
        assertEquals(1f, GlidingChips.fadeStrength(500, 84f), 0f)
    }

    // Live tiles' category words (LivePivot): at rest the same window as before - the word before at the left edge, then the
    // one you're in, whole words only, "+N" for the rest; a step glides between two rests word for word.
    @Test fun liveWordsRestAsBeforeAndGlideAWordAtATime() {
        val ws = intArrayOf(80, 90, 100, 70, 120, 60, 90) // words for places 0..6 on the strip
        val ds = listOf(-1, 0, 1, 2, 3)
        val r2 = LivePivot.rest(ws, first = 0, v = 2, ds = ds, width = 360, hintRoom = 40)
        assertEquals(0f, r2.xs[1], 0f) // the word before (place 1) at the left edge
        assertEquals(90f, r2.xs[2], 0f) // the one you're in right after it
        assertEquals(0f, r2.left, 0f)
        assertEquals(260f, r2.right, 0f) // places 1, 2, 3 whole (0..260); place 4 (260..380) is not, less the hint's room
        assertEquals(2, r2.hidden)
        val r3 = LivePivot.rest(ws, first = 0, v = 3, ds = ds, width = 360, hintRoom = 40)
        assertEquals(0f, r3.xs[2], 0f)
        // Between the two rests every word moves by the same amount: one strip.
        for (k in ws.indices) assertEquals(r3.xs[k] - r2.xs[k], r3.xs[0] - r2.xs[0], 0.01f)
        val t = LivePivot.Track(5)
        assertEquals(1, t.follow(6, 8)); assertEquals(2, t.follow(7, 8)); assertEquals(3, t.follow(0, 8)) // goes round forward
        assertEquals(2, t.follow(7, 8))
        assertEquals(0, t.lo); assertEquals(3, t.hi)
        t.settle(2); assertEquals(2, t.lo); assertEquals(2, t.hi)
    }

    // 529j: the strip's glide frame by frame, as WholeChipRow places it (ChipGlide.restart / lerp / inside), on Compose's own
    // FastOutSlowIn curve at 60 fps. (Was a Robolectric test; that class upset the timing of later Robolectric classes in the
    // same JVM on CI - run 43484730908 - so the frame walk is pure now; the handheld frame log is tools/qa/tools/chipglide.py.)
    private val widths = intArrayOf(52, 74, 60, 88, 46, 70, 64, 92, 58, 66)
    private val gap = 6
    private val view = 300f
    private val starts = ChipGlide.starts(widths, gap)

    /** The window starting at chip [s]: (strip offset, span left, span right) - chip s at x 0, the chips that fit whole. */
    private fun window(s: Int): FloatArray {
        var e = s; var x = widths[s]
        while (e + 1 < widths.size && x + gap + widths[e + 1] <= view) { e++; x += gap + widths[e] }
        return floatArrayOf(-starts[s].toFloat(), 0f, x.toFloat())
    }

    private class Frame(val xs: FloatArray, val al: FloatArray)

    /** Frames of a walk through window starts [targets], a new target every [every] frames; [legacy] = the old instant re-layout. */
    private fun walk(targets: List<Int>, every: Int, legacy: Boolean = false): List<Frame> {
        val ease = androidx.compose.animation.core.FastOutSlowInEasing
        var from = window(targets[0]); var to = from
        var t = 1f
        val frames = mutableListOf<Frame>()
        val steps = (ChipGlide.MS / 16f)
        for ((n, s) in targets.withIndex()) {
            if (n > 0) {
                val k = ease.transform(t)
                val w = window(s)
                if (legacy) { from = w; to = w; t = 1f } else { from = FloatArray(3) { ChipGlide.restart(from[it], to[it], k) }; to = w; t = 0f }
            }
            repeat(every) {
                t = (t + 1f / steps).coerceAtMost(1f)
                val k = if (legacy) 1f else ease.transform(t)
                val shift = ChipGlide.lerp(from[0], to[0], k); val l = ChipGlide.lerp(from[1], to[1], k); val r = ChipGlide.lerp(from[2], to[2], k)
                val xs = FloatArray(widths.size) { starts[it] + shift }
                frames += Frame(xs, FloatArray(widths.size) { ChipGlide.inside(xs[it], widths[it].toFloat(), l, r) })
            }
        }
        return frames
    }

    private fun biggestRise(f: List<Frame>) = (1 until f.size).maxOf { n -> f[n].al.indices.maxOf { f[n].al[it] - f[n - 1].al[it] } }
    private fun biggestJump(f: List<Frame>) = (1 until f.size).maxOf { n -> f[n].xs.indices.maxOf { i ->
        if (f[n - 1].al[i] > 0f && f[n].al[i] > 0f) kotlin.math.abs(f[n].xs[i] - f[n - 1].xs[i]) else 0f } }

    @Test fun incomingChipsGlideInFrameByFrame() {
        val f = walk(listOf(0, 1, 2, 3, 4, 5, 4, 3, 2, 1, 0), every = 30)
        assertTrue("a chip came in by ${biggestRise(f)} in one frame", biggestRise(f) <= 0.35f)
        assertTrue("a chip jumped ${biggestJump(f)} px in one frame", biggestJump(f) <= 24f)
        // At rest: whole chips only.
        for (fr in listOf(f[29], f[59], f.last())) for (a in fr.al) assertTrue("half-shown chip at rest: $a", a == 0f || a == 1f)
    }

    @Test fun fastPressesNeverJumpBack() {
        // A new window every 4 frames (presses faster than the glide): each glide starts where the strip is drawn.
        val f = walk(listOf(0, 1, 2, 3, 4, 5, 6, 5, 4), every = 4)
        // (Faster presses move it faster - about 26 px / 0.44 at most here - but never in one step: the old row moved a whole
        // chip, 60-100 px, and showed it whole in a single frame.)
        assertTrue("a chip jumped ${biggestJump(f)} px", biggestJump(f) <= 32f)
        assertTrue("a chip came in by ${biggestRise(f)} in one frame", biggestRise(f) <= 0.5f)
    }

    @Test fun theOldInstantRowPopsAndTheCheckSeesIt() {
        val f = walk(listOf(0, 1, 2, 3), every = 10, legacy = true)
        assertTrue("the legacy row shows a chip whole in one frame: ${biggestRise(f)}", biggestRise(f) >= 0.99f)
    }
}
