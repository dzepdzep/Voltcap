package com.shelf.launcher.ui

import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features.CapsuleSwap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 373: "selected game wide" moves one continuous row (no card vanishing and coming back); landscape art first in the capsule look. */
class WideCapsules373Test {
    @Test fun defaultSlidesInKeepsTheRowContinuousNoFixedSlot() {
        assertFalse(fixedSlotWanted(false, false, true, false, true, CapsuleSwap.SLIDE))
    }

    @Test fun fadesInAndJustAppearsStillKeepTheFixedPlace() {
        assertTrue(fixedSlotWanted(false, false, true, false, true, CapsuleSwap.FADE))
        assertTrue(fixedSlotWanted(false, false, true, false, true, CapsuleSwap.NONE))
    }

    @Test fun neverFixedForCarouselPinnedFirstFreeMediaCentreOrCoverFocus() {
        for (swap in CapsuleSwap.values()) {
            assertFalse(fixedSlotWanted(true, false, true, false, true, swap))
            assertFalse(fixedSlotWanted(false, true, true, false, true, swap))
            assertFalse(fixedSlotWanted(false, false, false, false, true, swap))
            assertFalse(fixedSlotWanted(false, false, true, true, true, swap))
            assertFalse(fixedSlotWanted(false, false, true, false, false, swap))
        }
    }

    @Test fun capsuleLookPrefersLandscapeArtToAPortraitCover() {
        assertEquals("wide", cardArtUrl(CardArtType.CAPSULE, false, "cover", "wide", "hero", null))
        assertEquals("hero", cardArtUrl(CardArtType.CAPSULE, false, "cover", null, "hero", null))
        assertEquals("cover", cardArtUrl(CardArtType.CAPSULE, false, "cover", null, null, null))
        // Other looks keep their order.
        assertEquals("cover", cardArtUrl(CardArtType.COVER, false, "cover", "wide", "hero", null))
        assertEquals("wide", cardArtUrl(CardArtType.COVER, true, "cover", "wide", "hero", null))
    }
}
