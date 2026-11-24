package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 682au (owner 24 Nov - Arcade B, the ~100 ms game change on the RP Classic: FitText's one-by-one step-down measured each text up to
 * ~20 times, ~11 ms per change): the search by halves picks exactly the step the old walk picked, for every place the fit can
 * flip, with far fewer measurements.
 */
class FitSearch682auTest {
    @Test fun sameStepAsTheOldWalkWithFewerMeasurements() {
        for ((top, bottom) in listOf(20f to 6f, 22f to 5f, 10f to 6f, 9f to 7f, 13f to 13f, 16f to 9.6f)) {
            // every threshold between the bottom and above the top: sizes at or under it fit
            var th = bottom - 1f
            while (th <= top + 1f) {
                var oldN = 0; var newN = 0
                val old = fitStepSize(top, bottom, legacy = true) { oldN++; it <= th }
                val new = fitStepSize(top, bottom, legacy = false) { newN++; it <= th }
                assertEquals("top=$top bottom=$bottom th=$th", old, new, 0f)
                assertTrue("top=$top bottom=$bottom th=$th: $newN measurements, the old walk $oldN", newN <= oldN + 2)
                th += 0.37f
            }
        }
        // the worst case of the old walk (only the bottom fits): ~20 measurements -> ~6
        var oldN = 0; var newN = 0
        fitStepSize(20f, 6f, true) { oldN++; it <= 6f }; fitStepSize(20f, 6f, false) { newN++; it <= 6f }
        assertTrue("old $oldN new $newN", oldN >= 15 && newN <= 10)
    }
}
