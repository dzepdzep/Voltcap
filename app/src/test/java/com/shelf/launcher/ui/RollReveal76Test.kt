package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D76: a black strip inside a wide capsule for ~3 frames on a rapid Left / Right reversal. The roll-out leaves the cover
 * showing right of its edge; with that cover not drawn yet the card's near-black placeholder showed there. Locks that a
 * card without its cover is fully unrolled (no uncovered strip, no drift gap), and that the normal sweep is unchanged.
 */
class RollReveal76Test {
    @Test fun noCoverMeansWideArtCoversTheWholeCard() {
        for (r in listOf(0f, 0.1f, 0.5f, 0.9f)) {
            assertTrue(rollEdge(r, 350f, baseReady = false) >= 350f * (1f + ROLL_FEATHER))
            assertEquals(0f, rollDrift(r, baseReady = false), 0f)
        }
    }

    @Test fun withCoverTheSweepIsUnchanged() {
        assertEquals(0f, rollEdge(0f, 400f, true), 0f)
        assertEquals(400f * (1f + ROLL_FEATHER), rollEdge(1f, 400f, true), 0.001f)
        assertTrue(rollEdge(0.4f, 400f, true) < rollEdge(0.6f, 400f, true))
        assertEquals(0.05f, rollDrift(0f, true), 0f)
        assertEquals(0f, rollDrift(1f, true), 0f)
    }

    @Test fun sourceHidesTheWideLayerOnlyWhenCoverIsThere() {
        val src = listOf(java.io.File("../src_copy/ui/GameCard.kt"), java.io.File("src/main/java/com/shelf/launcher/ui/GameCard.kt"),
            java.io.File("app/src/main/java/com/shelf/launcher/ui/GameCard.kt")).first { it.exists() }.readText()
        assertTrue(src.contains("alpha = if (r <= 0f && baseLoaded) 0f else 1f"))
        assertTrue(src.contains("onSuccess = { baseLoaded = true }"))
    }
}
