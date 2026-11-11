package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
