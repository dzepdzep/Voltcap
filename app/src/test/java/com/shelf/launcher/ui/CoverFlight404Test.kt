package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 404 (D95): the Cover details case comes out of its own shelf slot and goes back into the old one (no spawn). */
class CoverFlight404Test {
    private fun source(): String = listOf("src/main/java/com/shelf/launcher/ui/StyleCoverDetails.kt", "app/src/main/java/com/shelf/launcher/ui/StyleCoverDetails.kt")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun slotIsOnlyEmptyWhileTheCaseIsAway() {
        // nothing flying: drawn; at both ends of a flight: drawn; in the middle: empty
        assertEquals(0f, CoverFlight.hide("none"), 0f)
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            CoverFlight.flights["k"] = androidx.compose.runtime.mutableStateOf(0f)
            assertEquals(0f, CoverFlight.hide("k"), 0f)
            CoverFlight.flights["k"] = androidx.compose.runtime.mutableStateOf(1f)
            assertEquals(0f, CoverFlight.hide("k"), 0f)
            CoverFlight.flights["k"] = androidx.compose.runtime.mutableStateOf(0.5f)
            assertEquals(1f, CoverFlight.hide("k"), 0f)
            CoverFlight.flights.remove("k")
        }
    }

    @Test fun lerpEndsAreExact() {
        assertEquals(3f, CoverFlight.lerp(3f, 9f, 0f), 0f)
        assertEquals(9f, CoverFlight.lerp(3f, 9f, 1f), 0f)
    }

    @Test fun oneObjectPerGameAndNoFlightOnACategoryChange() {
        val s = source()
        assertTrue(s.contains("key(o.game.stableKey)"))
        assertTrue("a category swap is immediate (rule 14)", s.contains("CategorySlide.justChanged()"))
        assertTrue("animation off / Lite = no flight", s.contains("rememberAnimationsEnabled() && !LocalLiteEffects.current"))
        assertTrue("a game coming back reuses its leaving object", s.contains("hold.leaving.firstOrNull { it.game.stableKey == g.stableKey }"))
    }
}
