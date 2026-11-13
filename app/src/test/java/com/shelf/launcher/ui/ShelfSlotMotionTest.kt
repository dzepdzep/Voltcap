package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 525 (owner: "when the outgoing case returns, the next case's slot shifts approximately 120-170 pixels over only a
 * few frames"): the slot of the game coming out closes the room it made for the game going back across that game's whole
 * way back (620 ms, linear in its pull), never faster than ~2.5 % of the room in one 120 Hz frame, never backwards, from all
 * of it to none, with no step at either end. (Before: shelfFront's middle third, up to 5.2 % a frame and 15.5 % in three.)
 * Visual verification on hardware is separate.
 */
class ShelfSlotMotionTest {
    private fun frames(curve: (Float) -> Float): List<Float> {
        val n = (0.62f * 120f).toInt()
        return (0..n).map { f -> curve(1f - f / n.toFloat()) } // the game going back: pull 1 -> 0
    }

    @Test fun comingOutSlotGlides() {
        val k = frames(::shelfClose)
        assertEquals(1f, k.first(), 1e-4f)
        assertEquals(0f, k.last(), 1e-4f)
        val steps = k.zipWithNext { a, b -> a - b }
        assertTrue("never backwards", steps.all { it >= -1e-6f })
        assertTrue("max per frame ${steps.max()}", steps.max() <= 0.025f)
        val three = k.windowed(4).maxOf { it.first() - it.last() }
        assertTrue("max in three frames $three", three <= 0.075f)
        // starts and ends gently (no step when the room starts / stops closing)
        assertTrue(steps.first() < 0.002f && steps.last() < 0.002f)
    }

    @Test fun oldCurveWasTheJump() {
        val steps = frames(::shelfFrontFor).zipWithNext { a, b -> a - b }
        assertTrue("the old curve's fastest frame ${steps.max()} is over twice the new one's", steps.max() > 0.05f)
    }

    @Test fun curvesAreBounded() {
        for (i in -10..110) {
            val p = i / 100f
            assertTrue(shelfClose(p) in 0f..1f)
            assertTrue(shelfFrontFor(p) in 0f..1f)
        }
    }
}
