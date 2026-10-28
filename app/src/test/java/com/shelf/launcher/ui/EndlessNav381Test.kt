package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 381 (Endless navigation): the Dashboard row shows the last game left of the first one, but ◀ on the first game
 * went into the menu, so that card could never be reached from there. With Endless on, ◀ goes round to it; the menu stays
 * reachable by ▲.
 */
class EndlessNav381Test {
    @Test fun leftOnFirstGameGoesRoundWhenTheRowLoops() {
        assertFalse(leftReachesMenu(focus = 0, hasMenu = true, rowLoops = true))
    }

    @Test fun leftOnFirstGameEntersTheMenuWhenTheRowStops() {
        assertTrue(leftReachesMenu(focus = 0, hasMenu = true, rowLoops = false))
    }

    @Test fun leftElsewhereOrWithoutAMenuIsPlainMovement() {
        assertFalse(leftReachesMenu(focus = 3, hasMenu = true, rowLoops = false))
        assertFalse(leftReachesMenu(focus = 0, hasMenu = false, rowLoops = false))
    }

    /** The card drawn left of the first game is the last game: the one ◀ now reaches. */
    @Test fun cardLeftOfTheFirstGameIsTheLastGame() {
        val n = 20
        val row = LoopRow(loop = true, n = n, virtual = 0)
        assertEquals(0, row.game(row.focusItem))
        assertEquals(n - 1, row.game(row.focusItem - 1))
    }

    /** Every card a looping row can draw maps to a real game, so each one can be the focus. */
    @Test fun everyDrawnItemIsAGame() {
        val n = 9
        val row = LoopRow(loop = true, n = n, virtual = 0)
        val seen = (row.focusItem - 6..row.focusItem + 6).map { row.game(it) }.toSet()
        assertEquals((0 until n).toSet(), seen)
    }
}
