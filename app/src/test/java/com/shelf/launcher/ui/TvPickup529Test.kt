package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 529s (owner P3): TV room - the picked case goes into the set's switch-on line and comes back out to its place. */
class TvPickup529Test {
    private val case = Rect(700f, 300f, 790f, 420f)
    private val tv = Rect(60f, 80f, 520f, 420f)

    @Test fun startsAndEndsExactlyAtItsPlace() {
        val f = tvCaseFlight(case, tv, 0f)!!
        assertEquals(0f, f[0], 0.001f); assertEquals(0f, f[1], 0.001f)
        assertEquals(1f, f[2], 0.001f); assertEquals(1f, f[3], 0.001f); assertEquals(1f, f[4], 0.001f)
    }

    @Test fun reachesTheSetThenGoesIntoItsLine() {
        val there = tvCaseFlight(case, tv, 0.5f)!!
        assertEquals(tv.center.x, case.center.x + there[0], 1f); assertEquals(tv.center.y, case.center.y + there[1], 1f)
        val at = tvCaseFlight(case, tv, 0.45f)!!
        val nearlyIn = tvCaseFlight(case, tv, 0.6f)!!
        assertTrue("flattened into the line", nearlyIn[3] < 0.15f * at[3])
        assertNull(tvCaseFlight(case, tv, TV_CASE_IN)); assertNull(tvCaseFlight(case, tv, 1f))
    }

    @Test fun movesWithoutJumps() {
        var last = tvCaseFlight(case, tv, 0f)!!
        var i = 1
        while (i / 200f < TV_CASE_IN) {
            val now = tvCaseFlight(case, tv, i / 200f) ?: break
            assertTrue(kotlin.math.abs(now[0] - last[0]) < 12f && kotlin.math.abs(now[1] - last[1]) < 12f)
            assertTrue(kotlin.math.abs(now[2] - last[2]) < 0.08f && kotlin.math.abs(now[3] - last[3]) < 0.08f && kotlin.math.abs(now[4] - last[4]) < 0.12f)
            last = now; i++
        }
        // It has faded right out by the time it is gone: no pop.
        assertTrue(tvCaseFlight(case, tv, TV_CASE_IN - 0.001f)!![4] < 0.02f)
    }

    @Test fun theSetSwitchesOnOnceTheCaseIsThere() {
        assertEquals(0f, tvSetOn(0.3f), 0f); assertEquals(0f, tvSetOn(0.5f), 0f); assertEquals(1f, tvSetOn(1f), 0f)
        // The line is lit while the case goes into it.
        assertTrue(tvSetOn(0.58f) > 0f)
        assertNotNull(tvCaseFlight(case, tv, 0.58f))
    }
}
