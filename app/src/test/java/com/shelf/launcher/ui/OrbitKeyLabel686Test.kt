package com.shelf.launcher.ui

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 686a (H16 survey, Orbit x1.3): the key label "ACHIEVEMENTS" broke inside the word at the design's 11 sp / 1 sp spacing in its
 * 104 dp. odLabelFit keeps the design while the widest word fits and narrows spacing, then size, only as far as needed.
 */
class OrbitKeyLabel686Test {
    /** A fake measure: each letter 8 px at 11 sp, scaled by size, plus the spacing per letter (12 letters). */
    private fun widest(size: Float, spacing: Float) = (12 * (8f * size / 11f + spacing * 1.3f)).toInt()

    @Test fun theDesignStaysWhenTheWordFits() =
        assertEquals(11.sp to 1.sp, odLabelFit("ACHIEVEMENTS", 200) { s, l -> widest(s.value, l.value) })

    @Test fun spacingNarrowsBeforeTheSizeDrops() =
        assertEquals(11.sp to 0.sp, odLabelFit("ACHIEVEMENTS", 97) { s, l -> widest(s.value, l.value) })

    @Test fun theSizeStepsDownToTheReadableMinimumOnly() {
        assertEquals(10.sp to 0.sp, odLabelFit("ACHIEVEMENTS", 88) { s, l -> widest(s.value, l.value) })
        // (Still too wide at the minimum: the minimum, left to wrap - never smaller, never cut.)
        assertEquals(10.sp to 0.sp, odLabelFit("ACHIEVEMENTS", 40) { s, l -> widest(s.value, l.value) })
    }
}
