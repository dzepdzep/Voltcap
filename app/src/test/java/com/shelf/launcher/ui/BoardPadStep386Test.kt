package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Update 386: on the second screen's Widget Board the controller (SecondPad) must reach every tile with ▲ ▼, also those
 * below the fold (another app's widget cannot take focus itself, and the dock is always the nearest thing below).
 */
class BoardPadStep386Test {
    @Test fun downWalksEveryTileOfOneColumn() {
        var i = 0
        val seen = mutableListOf(0)
        while (true) { i = boardPadStep(i, 4, 1, 3) ?: break; seen += i }
        assertEquals(listOf(0, 1, 2, 3), seen)
    }

    @Test fun upWalksBackAndStopsAtTheFirstTile() {
        assertEquals(2, boardPadStep(3, 4, 1, 2))
        assertEquals(0, boardPadStep(1, 4, 1, 2))
        assertNull(boardPadStep(0, 4, 1, 2)) // falls through to SecondPad's own routing (header / arrows)
    }

    @Test fun lastTileLetsDownGoOnToTheDock() {
        assertNull(boardPadStep(3, 4, 1, 3))
    }

    @Test fun twoColumnsStepARowAndReachAShortLastRow() {
        assertEquals(2, boardPadStep(0, 5, 2, 3))
        assertEquals(4, boardPadStep(2, 5, 2, 3))
        assertEquals(4, boardPadStep(3, 5, 2, 3)) // the last row has one tile: reached from the one above it
        assertNull(boardPadStep(4, 5, 2, 3))
        assertEquals(1, boardPadStep(3, 5, 2, 2))
    }

    @Test fun leftRightAndBadIndexAreNotHandled() {
        assertNull(boardPadStep(1, 4, 1, 0))
        assertNull(boardPadStep(1, 4, 1, 1))
        assertNull(boardPadStep(-1, 4, 1, 3))
        assertNull(boardPadStep(0, 0, 1, 3))
    }
}
