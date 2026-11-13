package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
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
}
