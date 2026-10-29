package com.shelf.launcher.ui

import com.shelf.launcher.data.Features.RowFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 390: the Dashboard / Side menu row's "Game you're on" choice (Left / Middle / Right, default = the designed second card). */
class RowFocus390Test {
    @Test fun designKeepsTheSecondCardWhateverTheWidth() {
        for (v in 1..12) assertEquals(1, rowBefore(RowFocus.DESIGN, v))
    }

    @Test fun leftMiddleRightPlaceTheCardInTheRow() {
        assertEquals(0, rowBefore(RowFocus.LEFT, 6))
        assertEquals(2, rowBefore(RowFocus.CENTER, 6)) // 6 cards: third from the left (two before, three after)
        assertEquals(3, rowBefore(RowFocus.CENTER, 7)) // 7 cards: the exact middle
        assertEquals(5, rowBefore(RowFocus.RIGHT, 6))
    }

    @Test fun neverMoreThanFitsAndNeverNegative() {
        for (pos in RowFocus.values()) for (v in 0..12) {
            val b = rowBefore(pos, v)
            assertTrue("$pos/$v", b >= 0 && (pos == RowFocus.DESIGN || b <= maxOf(0, v - 1)))
        }
    }

    @Test fun oneStepPerPressAtEveryPlace() {
        // the row's first visible item after each ▶ moves by exactly one, rapid or not, for every place (Endless: no clamping at 0 in the middle of the list)
        for (pos in RowFocus.values()) {
            val before = rowBefore(pos, 7)
            var prev = followTarget(20, before)
            for (f in 21..40) { val t = followTarget(f, before); assertEquals("$pos", 1, t - prev); prev = t }
            for (f in 39 downTo 21) { val t = followTarget(f, before); assertEquals("$pos", -1, t - prev); prev = t }
        }
    }

    @Test fun highlightStaysInTheSelectionsSlotWhileGliding() {
        for (pos in RowFocus.values()) {
            val before = rowBefore(pos, 7)
            val f = 30
            val items = followTarget(f, before)
            // the row is one card behind (just pressed ▶): the slot item is the old selection, strengths add to 1
            for (k in 0..10) {
                val slot = slotPosition(f, before, items - 1, (k * 10), 100)
                val total = (f - 3..f + 3).sumOf { highlightStrength(it, f, slot).toDouble() }
                assertEquals("$pos $k", 1.0, total, 1e-4)
            }
        }
    }
}
