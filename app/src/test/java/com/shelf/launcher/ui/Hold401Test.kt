package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Update 401 (D86): the card a piece flies out of stays hidden while it flies; it returns as the piece goes home. */
class Hold401Test {
    @Test fun heldCardIsHiddenWhateverTheAmount() {
        for (a in listOf(0f, 0.01f, 0.4f, 1f)) assertEquals(0f, MediaInspect.cardAlpha(a, true), 0f)
    }
    @Test fun releasedCardFollowsTheAmount() {
        assertEquals(1f, MediaInspect.cardAlpha(0f, false), 0f)
        assertEquals(0.6f, MediaInspect.cardAlpha(0.4f, false), 1e-6f)
        assertEquals(0f, MediaInspect.cardAlpha(1.2f, false), 0f)
    }
}
