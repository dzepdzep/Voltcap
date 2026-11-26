package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 764a (P5 polish, Grid). Wide cards sit in two rows filled column by column. ▲ ▼ used to step through the list (index ± 1,
 * wrapping): ▲ on a top card jumped to the previous column's bottom, ▼ on a bottom card to the next column's top, and ▼ on the
 * last card of an odd count wrapped to the very first; ◀ from the first card landed in the second-to-last column. gridStep
 * keeps ▲ ▼ inside the column and ◀ ▶ inside the row. Source half fails on 763a.
 */
class GridSteps764aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    // 5 cards in 2 rows: columns [0,1] [2,3] [4]
    @Test fun upAndDownStayInTheColumn() {
        assertEquals(0, gridStep(0, 0, -1, 5, 2)) // top row: ▲ stays (was 4: the last card)
        assertEquals(1, gridStep(0, 0, 1, 5, 2))
        assertEquals(1, gridStep(1, 0, 1, 5, 2)) // bottom row: ▼ stays (was 2: the next column's top)
        assertEquals(2, gridStep(3, 0, -1, 5, 2))
        assertEquals(2, gridStep(2, 0, -1, 5, 2)) // was 1: the previous column's bottom
        assertEquals(4, gridStep(4, 0, 1, 5, 2)) // last card, empty slot below: stays (was 0: wrapped to the first)
    }

    @Test fun leftAndRightKeepTheRowAndWrapRoundTheColumns() {
        assertEquals(2, gridStep(0, 1, 0, 5, 2))
        assertEquals(3, gridStep(1, 1, 0, 5, 2))
        assertEquals(4, gridStep(3, 1, 0, 5, 2)) // bottom row onto the shorter last column: its card (was 0)
        assertEquals(0, gridStep(4, 1, 0, 5, 2))
        assertEquals(4, gridStep(0, -1, 0, 5, 2)) // first card ◀: the last column (was 3: the middle column)
        assertEquals(4, gridStep(1, -1, 0, 5, 2))
        // even count, 6 cards: plain wrap in the row
        assertEquals(5, gridStep(1, -1, 0, 6, 2))
        assertEquals(1, gridStep(5, 1, 0, 6, 2))
    }

    @Test fun pickedUpItStopsAtTheEnds() {
        assertEquals(0, gridStep(0, -1, 0, 5, 2, wrap = false))
        assertEquals(4, gridStep(4, 1, 0, 5, 2, wrap = false))
        assertEquals(4, gridStep(3, 1, 0, 5, 2, wrap = false))
        assertEquals(2, gridStep(4, -1, 0, 5, 2, wrap = false))
        assertEquals(1, gridStep(0, 0, 1, 5, 2, wrap = false))
    }

    @Test fun oneRowAndEdgeCases() {
        assertEquals(1, gridStep(0, 1, 0, 3, 1))
        assertEquals(2, gridStep(0, -1, 0, 3, 1))
        assertEquals(0, gridStep(0, 0, 1, 3, 1))
        assertEquals(0, gridStep(0, 1, 0, 1, 2))
        assertEquals(0, gridStep(0, 0, 1, 1, 2))
        assertEquals(0, gridStep(0, 1, 0, 0, 2))
    }

    @Test fun theGridKeysGoThroughGridStep() {
        val b = src("ui/PlatformBrowser.kt")
        assertTrue(b.contains("val next = if (nav == LookNav.ROW && rows > 1) gridStep(index, if (vertical) 0 else step.sign, if (vertical) step.sign else 0, count, rows, wrap = st.grabbed < 0)"))
        assertTrue(b.contains("if (tall) \"◀ ▶\" else \"◀ ▶ ▲ ▼\""))
        // only Grid's wide cards have two rows; the other looks keep their own steps
        assertTrue(b.contains("val rows = if (view == Features.PlatformView.GRID && !Features.pbTallCards.value) 2 else 1"))
        assertEquals(LookNav.ROW, lookNavOf(com.shelf.launcher.data.Features.PlatformView.GRID))
    }
}
