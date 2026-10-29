package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 395: Cardboard box packing geometry (no overlaps, no unintended gaps, rows against their walls, the thickness the
 * 3D case has) and the Artwork library list position (starts at the top with Endless off, never scrolls past its ends,
 * continuous, constant with Endless on).
 */
class BoxArtLib395Test {
    private val platforms = listOf("PS2", "PS1", "NES", "SNES", "N64", "GBA", "NDS", "Switch", "Xbox360", "GameCube", "Wii", "Genesis")
    private fun games(n: Int) = List(n) { i -> Game("Game $i", platforms[i % platforms.size], 0, SourceType.ROM, "/x/g$i.rom") }
    private fun cam() = boxCam(1920f, 1080f, 0.5f, 1f)

    @Test fun neatRowsAreTightlyPackedWithNoOverlap() {
        val cam = cam(); val gap = 0.6f
        val slots = packBoxes(games(80), cam, gap, messy = false)
        for ((_, row) in slots.groupBy { it.box to it.row }) for (k in 1 until row.size) {
            assertEquals("gap between neighbours", gap, row[k].x0 - row[k - 1].x1, 0.001f)
        }
    }

    @Test fun everyCaseStaysInsideItsBoxAndRowsLeanOnTheirWalls() {
        val cam = cam()
        for (messy in listOf(false, true)) for (s in packBoxes(games(120), cam, 0.6f, messy)) {
            assertTrue("left wall", s.x0 >= -cam.wMm / 2f + BOX_MARGIN - 0.001f)
            assertTrue("right wall", s.x1 <= cam.wMm / 2f - BOX_MARGIN + 3.1f)
            assertTrue("back wall", s.y0 >= BOX_MARGIN - 0.001f)
            assertTrue("front wall", s.y1 <= cam.dMm - BOX_MARGIN + 0.001f)
            if (!messy) assertTrue(if (s.row == 0) s.y0 == BOX_MARGIN else Math.abs(s.y1 - (cam.dMm - BOX_MARGIN)) < 0.001f)
        }
    }

    @Test fun rowsOfOneBoxDoNotMeet() {
        val cam = cam()
        for (box in packBoxes(games(120), cam, 0.6f, false).groupBy { it.box }.values) {
            val back = box.filter { it.row == 0 }.maxOfOrNull { it.y1 } ?: continue
            val front = box.filter { it.row == 1 }.minOfOrNull { it.y0 } ?: continue
            assertTrue("rows overlap", back < front)
        }
    }

    @Test fun thicknessIsTheCaseThicknessTimesItsLength() {
        val cam = cam()
        for ((g, s) in games(24).zip(packBoxes(games(24), cam, 0.6f, false))) {
            val want = (s.len * CaseThickness.orElse(g, shelfSpineAspect(g.platform))).coerceIn(5f, 60f)
            assertEquals(g.platform, want, s.thick, 0.001f)
        }
    }

    @Test fun sideFaceIsTheOneTowardTheMiddle() {
        assertEquals(10f to true, boxSideFaceX(10f, 20f))
        assertEquals(-10f to false, boxSideFaceX(-20f, -10f))
    }

    private val rowH = 50f; private val listH = 900f; private val anchor = listH * 0.42f; private val pad = 4f
    private fun top(pos: Float, n: Int, loop: Boolean = false) = artLibRowTop(pos, rowH, anchor, pad, n * rowH + 2 * pad, listH, loop)

    @Test fun endlessOffStartsAtTheTop() {
        assertEquals(pad, top(0f, 36), 0.001f)
        assertEquals(pad, top(0f, 1), 0.001f)
    }

    @Test fun endlessOffNeverLeavesEmptySpaceAtEitherEnd() {
        for (n in listOf(1, 2, 3, 8, 36, 200)) {
            val total = n * rowH + 2 * pad
            for (step in 0..(n - 1) * 4) {
                val pos = step / 4f
                val scroll = pad + pos * rowH - top(pos, n)
                assertTrue("n=$n pos=$pos scroll", scroll >= -0.001f)
                if (total > listH) assertTrue("past the end n=$n pos=$pos", scroll <= total - listH + 0.001f)
                else assertEquals("short list stays put n=$n", 0f, scroll, 0.001f)
            }
            val last = top((n - 1).toFloat(), n)
            if (n * rowH + 2 * pad > listH) assertEquals("last game at the bottom n=$n", listH - pad - rowH, last, 0.001f)
        }
    }

    @Test fun endlessOffGlidesWithoutJumps() {
        var prev = top(0f, 60)
        var p = 0.05f
        while (p <= 59f) { val t = top(p, 60); assertTrue("jump at $p", Math.abs(t - prev) <= rowH * 0.05f + 0.01f); prev = t; p += 0.05f }
    }

    @Test fun highlightReachesTheAnchorInTheMiddleOfALongList() {
        assertEquals(anchor - rowH / 2f, top(30f, 100), 0.001f)
    }

    @Test fun endlessOnKeepsTheHighlightStill() {
        for (p in listOf(0f, 0.5f, 7f, 35.9f)) assertEquals(anchor - rowH / 2f, top(p, 36, loop = true), 0.001f)
        assertFalse(top(0f, 36, loop = true) == top(0f, 36))
    }
}
