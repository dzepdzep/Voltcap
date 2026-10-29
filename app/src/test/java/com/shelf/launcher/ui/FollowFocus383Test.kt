package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 383 (D77, Dashboard with Endless on): the selected card must keep one place in the row, so the number of games drawn
 * left of it never changes and one press moves the row exactly one step. The old FollowFocus left a card that was already in
 * view where it was, so after one ▶ two games stood left of the selection.
 */
class FollowFocus383Test {
    /** First visible item after following [focusItem], as a lazy row ends up (the scroll target is the first item). */
    private fun firstVisible(focusItem: Int, before: Int = 1) = followTarget(focusItem, before)

    @Test fun gamesLeftOfTheSelectionStayTheSameWalkingRight() {
        val row = LoopRow(loop = true, n = 30, virtual = 0)
        for (step in 0..40) {
            val f = row.focusItem + step
            assertEquals(1, f - firstVisible(f))
        }
    }

    @Test fun gamesLeftOfTheSelectionStayTheSameWalkingLeft() {
        val row = LoopRow(loop = true, n = 30, virtual = 0)
        for (step in 0..40) {
            val f = row.focusItem - step
            assertEquals(1, f - firstVisible(f))
        }
    }

    /** One D-pad press moves the row by exactly one item, both ways, at any depth (a ◀ right after ▶ is one step back). */
    @Test fun onePressIsOneRowStep() {
        val row = LoopRow(loop = true, n = 12, virtual = 0)
        var f = row.focusItem
        val moves = "RRRLLLLRL"
        for (m in moves) {
            val before = firstVisible(f)
            f += if (m == 'R') 1 else -1
            assertEquals(if (m == 'R') 1 else -1, firstVisible(f) - before)
        }
    }

    @Test fun stoppingRowKeepsTheFirstGamesAtTheEdge() {
        assertEquals(0, followTarget(0, 1))
        assertEquals(0, followTarget(1, 1))
        assertEquals(1, followTarget(2, 1))
        assertTrue(followTarget(5, 2) == 3)
    }
}
