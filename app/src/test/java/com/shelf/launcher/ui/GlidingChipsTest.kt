package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sprint 527: the Home category pill bar - a steady place for the selected pill, long names fitted (GlidingChips, PillFit). */
class GlidingChipsTest {
    @Test fun theSelectedPillSitsAThirdIn() {
        val view = 1800; val margin = 100
        // Far right of the view: brought back to a third in (it used to stop a margin short of the right edge).
        assertEquals(1500 - (view * 0.33f).toInt(), GlidingChips.target(left = 1500, width = 150, viewW = view, cur = 0, margin = margin))
        // Already between a third and the middle: nothing moves (no motion for every press).
        assertEquals(0, GlidingChips.target(left = 700, width = 150, viewW = view, cur = 0, margin = margin))
        // Left of the anchor near the start: brought to the anchor (the row's scroll clamps it at 0 at the list's start).
        assertEquals(1000 - (view * 0.33f).toInt(), GlidingChips.target(left = 1000, width = 150, viewW = view, cur = 950, margin = margin))
        assertEquals(56, GlidingChips.FADE_DP)
    }

    @Test fun aLongNameShrinksThenWraps() {
        // fits at 11 sp
        assertEquals(11f to 1, PillFit.fit(12f, 10f) { it <= 11f })
        // never fits on one line: 10 sp on two lines (whole name, no "…")
        assertEquals(10f to 2, PillFit.fit(12f, 10f) { false })
        assertEquals(12f to 1, PillFit.fit(12f, 10f) { true })
    }
}
