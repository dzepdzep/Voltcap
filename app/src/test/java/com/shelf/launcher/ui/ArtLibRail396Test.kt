package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 396: the Artwork library rail's labels never wrap, move or spawn while the rail's width animates. */
class ArtLibRail396Test {
    @Test
    fun labelsAreHiddenWhileNarrowAndFullWhenOpen() {
        assertEquals(0f, artLibRailLabelAlpha(ART_RAIL_CLOSED_DP), 0f)
        assertEquals(1f, artLibRailLabelAlpha(ART_RAIL_OPEN_DP), 0f)
    }

    @Test
    fun alphaIsMonotonicAndNeverShowsAWrappingWidth() {
        var last = 0f
        var w = ART_RAIL_CLOSED_DP
        while (w <= ART_RAIL_OPEN_DP) {
            val a = artLibRailLabelAlpha(w)
            assertTrue(a >= last)
            last = a
            // visible only once at least 110 dp wide, i.e. never at the widths where a label measured by weight() would wrap
            if (w < 110f) assertEquals(0f, a, 0f)
            w += 1f
        }
    }

    @Test
    fun labelWidthIsTheOpenRailMinusItsChrome() {
        assertEquals(ART_RAIL_OPEN_DP - 12f - 16f - 22f - 10f, ART_RAIL_LABEL_DP, 0f)
    }
}
