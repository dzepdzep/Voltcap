package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 386: Side menu row (StyleSideMenu.SideRow) after the FollowFocus change of update 383, Endless on and off.
 * SideRow seeds its list at (focusItem - 1) and FollowFocus keeps following followTarget(focusItem, 1): both must be the
 * same place, so the row does not jump when it first shows, and the selected card has one fixed slot.
 */
class SideRowFollow386Test {
    private fun seed(row: LoopRow) = (row.focusItem - 1).coerceAtLeast(0) // as SideRow's rememberLazyListState

    @Test fun seedEqualsFollowTargetEndlessOnAndOff() {
        for (loop in listOf(true, false)) for (n in listOf(3, 11, 30)) for (f in 0 until n) {
            val row = LoopRow(loop, n, f)
            assertEquals("loop=$loop n=$n focus=$f", followTarget(row.focusItem, 1), seed(row))
        }
    }

    @Test fun endlessOnKeepsOneGameLeftOfTheSelectionEverywhere() {
        val n = 30
        for (f in 0 until n) {
            val row = LoopRow(true, n, f)
            assertEquals(1, row.focusItem - followTarget(row.focusItem, 1)) // first and last game too
        }
    }

    @Test fun endlessOffFirstGamesStayAtTheEdgeThenOneGameLeft() {
        val n = 12
        val left = (0 until n).map { f -> LoopRow(false, n, f).let { it.focusItem - followTarget(it.focusItem, 1) } }
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), left)
    }

    @Test fun lastGameWithEndlessOffNeverNeedsAPositionPastTheList() {
        val row = LoopRow(false, 12, 11)
        assertTrue(followTarget(row.focusItem, 1) in 0 until row.count)
    }

    @Test fun switchingEndlessOnMidRowJumpsInsteadOfGlidingAcrossTheCopies() {
        val off = LoopRow(false, 30, 5)
        val on = LoopRow(true, 30, 5)
        // FollowFocus scrolls instantly when the target is more than 12 items away (a new list), animated otherwise.
        assertTrue(kotlin.math.abs(followTarget(off.focusItem, 1) - followTarget(on.focusItem, 1)) > 12)
        assertEquals(4, followTarget(off.focusItem, 1))
    }
}
