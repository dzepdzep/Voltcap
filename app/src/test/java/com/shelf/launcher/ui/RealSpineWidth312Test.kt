package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: update 311's intact scans should not look like narrow stickers on N64 3D box sides. */
class RealSpineWidth312Test {
    @Test fun narrowRealSpineUsesMoreOfTheThreeDimensionalSide() {
        // An 80px-wide spine in a 120px-wide box should extend close to the side,
        // rather than leaving the original 20px green borders on both sides.
        assertEquals(108f, widenedRealSpineWidth(80f, 120f), 0.01f)
    }

    @Test fun veryNarrowScanIsOnlyExpandedByThirtyFivePercent() {
        // Don't turn an unrelated/top-edge scan into a massively stretched title.
        assertEquals(54f, widenedRealSpineWidth(40f, 120f), 0.01f)
    }

    @Test fun alreadyFittingRealSpineIsNotChanged() {
        // Tarzan and other real scans that already fit must stay intact.
        assertEquals(115f, widenedRealSpineWidth(115f, 120f), 0.01f)
    }

    @Test fun neverExceedsTheRealBoxSide() {
        assertEquals(120f, widenedRealSpineWidth(140f, 120f), 0.01f)
        assertEquals(0f, widenedRealSpineWidth(0f, 120f), 0.01f)
        assertEquals(0f, widenedRealSpineWidth(20f, 0f), 0.01f)
    }
}
