package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 400: the plain-window storage layouts that still open out of the picked case (GameWindow's cardFlight). */
class Pickup400Test {
    @Test fun cardFlightLayoutsHaveNoSceneOfTheirOwn() {
        // (A layout with its own window scene owns its open / close; the card flight is only for the plain window.)
        for (l in PICKUP_CARD_LAYOUTS) assertNull("$l has a scene", winSceneFor(l))
    }

    @Test fun theStorageLayoutsAreCovered() {
        assertTrue(HomeLayout.SHELF in PICKUP_CARD_LAYOUTS)
        assertTrue(HomeLayout.CONSOLES in PICKUP_CARD_LAYOUTS)
        assertTrue(HomeLayout.CRATE in PICKUP_CARD_LAYOUTS)
        // Cardboard box has its own scene and its own pull-out: not in the set.
        assertEquals(false, HomeLayout.BOX in PICKUP_CARD_LAYOUTS)
    }
}
