package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

/** 523z: kp523y genuine FAILs - Row opens Details room (KONKR x31). */
class Batch523zTest {
    @Test fun detailsTakeTheBiggerRoom() {
        // KONKR Row opens: ~90 px between name and buttons, ~170 px free under them -> under the buttons
        assertTrue(detailsBelow(true, inPlace = true, inPlaceRoom = 90f, belowRoom = 170f, minBelow = 120f))
        // Row reveal: buttons at the bottom, no room under them -> stays in place
        assertFalse(detailsBelow(true, inPlace = true, inPlaceRoom = 300f, belowRoom = 40f, minBelow = 120f))
        // a big room in place stays in place
        assertFalse(detailsBelow(true, inPlace = true, inPlaceRoom = 400f, belowRoom = 200f, minBelow = 120f))
        // no room in place -> under the buttons when it's usable
        assertTrue(detailsBelow(true, inPlace = false, inPlaceRoom = 0f, belowRoom = 200f, minBelow = 120f))
        // Details closed: never
        assertFalse(detailsBelow(false, inPlace = false, inPlaceRoom = 0f, belowRoom = 500f, minBelow = 120f))
    }

    @Test fun detailsBannerLeavesRoomOnVeryShortWindows() {
        // KONKR at x1.3 / density x1.15: ~370 dp high - the banner is ~118 dp, not the old 140 dp floor
        assertTrue(detailsBannerH(370.dp) < 125.dp && detailsBannerH(370.dp) >= 104.dp)
        // unchanged from 400 dp up
        assertTrue(detailsBannerH(426.dp) == 426.dp * 0.36f)
        assertTrue(detailsBannerH(720.dp) == 720.dp * 0.42f)
    }
}
