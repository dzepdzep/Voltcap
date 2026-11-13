package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 525 (owner: "a three-game collection visually looks like three games, including at viewport boundaries"):
 * the Tiles row going round a few more games than fit, Fill the row off. At every scroll place along a step (and for
 * every anchor's place of the game you're on) each game touches the screen at most once, the game you're on is drawn,
 * and no screen place is left empty when there are enough games to fill it.
 */
class TilesEdgeOnceTest {
    private fun touching(x: Float, tile: Float, w: Float) = x + tile > 0f && x < w

    @Test fun eachGameOnceAcrossTheGlide() {
        for (w in listOf(640f, 853f, 960f, 1280f)) for (tile in listOf(110f, 140f, 186f)) {
            val start = 22f
            val stride = tile + maxOf(10f, tile * 0.075f)
            val visible = kotlin.math.floor((w - start * 2 + (stride - tile)) / stride).toInt().coerceAtLeast(1)
            val maxK = tilesTouchingScreen(w, tile, stride)
            val mid = tilesMiddle(w, start, tile, stride)
            for (n in visible + 1 until maxK) for (first in -n..n) for (step in 0..20) for (edge in listOf(0, visible - 1)) {
                val s = first + step / 20f // the row gliding one place on
                val focus = first + edge // EDGES: the one you're on at either edge of the row, the row moving on
                val l = tilesOnceFirst(s, mid, n, focus)
                val drawn = (l until l + n)
                assertTrue(focus in drawn)
                val shown = drawn.filter { touching(start + (it - s) * stride, tile, w) }
                val games = shown.map { it.mod(n) }
                assertEquals("w=$w tile=$tile n=$n s=$s: $shown", games.size, games.toSet().size)
                // settled: every whole place on the screen holds a game (nothing missing inside the row)
                if (step == 0) for (i in first until first + visible) assertTrue("w=$w n=$n first=$first: $i missing", i in drawn)
            }
        }
    }

    @Test fun moreGamesThanTouchTheScreenNeedNothing() {
        assertEquals(6, tilesTouchingScreen(640f, 140f, 150.5f))
        assertEquals(Int.MAX_VALUE, tilesTouchingScreen(640f, 140f, 0f))
    }
}
