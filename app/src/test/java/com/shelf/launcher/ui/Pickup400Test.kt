package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 400: the plain-window storage layouts that still open out of the picked case (GameWindow's cardFlight). */
class Pickup400Test {
    @Test fun cardFlightLayoutsHaveNoSceneOfTheirOwn() {
        // (Update 421: the storage layouts got scenes of their own (index card, record sleeve, card back); those scenes
        // keep the card flight — the picked case / sleeve / card still fades out of its slot as the window grows from it.)
        val flightScenes = setOf(WinScene.INDEX_CARD, WinScene.RECORD_SLEEVE, WinScene.CARD_BACK)
        for (l in PICKUP_CARD_LAYOUTS) { val s = winSceneFor(l); assertTrue("$l: $s", s == null || s in flightScenes) }
    }

    @Test fun theStorageLayoutsAreCovered() {
        assertTrue(HomeLayout.SHELF in PICKUP_CARD_LAYOUTS)
        assertTrue(HomeLayout.CONSOLES in PICKUP_CARD_LAYOUTS)
        assertTrue(HomeLayout.CRATE in PICKUP_CARD_LAYOUTS)
        // Cardboard box has its own scene and its own pull-out: not in the set.
        assertEquals(false, HomeLayout.BOX in PICKUP_CARD_LAYOUTS)
    }
}
