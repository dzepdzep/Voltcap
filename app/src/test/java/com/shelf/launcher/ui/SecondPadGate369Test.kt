package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Update 369: the category screen keeps the controller on the main screen unless ▼ is pressed twice at the edge. */
class SecondPadGate369Test {
    @Before fun clean() = SecondPadGate.reset()

    @Test fun singleDownOnCategoryScreenStays() {
        assertFalse(SecondPadGate.allowEnter(true, false, true, 1000))
    }

    @Test fun deliberateSecondDownEnters() {
        assertFalse(SecondPadGate.allowEnter(true, false, true, 1000))
        assertTrue(SecondPadGate.allowEnter(true, false, true, 1500))
    }

    @Test fun slowSecondDownDoesNotEnter() {
        assertFalse(SecondPadGate.allowEnter(true, false, true, 1000))
        assertFalse(SecondPadGate.allowEnter(true, false, true, 1000 + SecondPadGate.CATEGORY_CONFIRM_MS + 1))
    }

    @Test fun movingBetweenEdgePressesResetsTheConfirmation() {
        assertFalse(SecondPadGate.allowEnter(true, false, true, 1000))
        assertFalse(SecondPadGate.allowEnter(true, false, false, 1200)) // a ▼ that moved focus
        assertFalse(SecondPadGate.allowEnter(true, false, true, 1400))
    }

    @Test fun headerAndReorderNeverEnter() {
        repeat(3) { assertFalse(SecondPadGate.allowEnter(true, true, true, 1000L + it * 100)) }
    }

    @Test fun otherScreensKeepSingleDown() {
        assertTrue(SecondPadGate.allowEnter(false, false, true, 1000))
        assertFalse(SecondPadGate.allowEnter(false, false, false, 1000))
    }
}
