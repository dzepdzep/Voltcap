package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 461: a ROM hack matched to its base game never wears that game's label (Pokémon Crystal Kaizo, KONKR). */
class HackLabels461Test {
    private fun scans(id: Int) = listOf("Box" to "https://x/api2/mediaJeu.php?systemeid=10&jeuid=$id&media=box-2D(us)",
        "Disc label" to "https://x/api2/mediaJeu.php?systemeid=10&jeuid=$id&media=support-texture(us)",
        "Disc / cartridge" to "https://x/api2/mediaJeu.php?systemeid=10&jeuid=$id&media=support-2D(us)")
    private val crystal = "/r/gbc/Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc"
    private val kaizo = "/r/gbc/Pokemon - Crystal Kaizo (Hack).gbc"
    private val clear = "/r/gbc/Pokemon - Crystal Clear (ShockSlayer) (v2.5.11) (Hack).gbc"
    private val hack: (String) -> Boolean = { it.contains("(Hack)") }

    @Test fun gameId() {
        assertEquals("82606", HackLabels.gameIdOf(scans(82606)))
        assertNull(HackLabels.gameIdOf(listOf("Box" to "file:///sdcard/x.png")))
    }

    @Test fun baseGamesLabelDropsOwnEntryKept() {
        val raw = mapOf(crystal to scans(82606), kaizo to scans(82606), clear to scans(490544))
        val keys = HackLabels.baseMatched(raw, hack)
        assertEquals(setOf(kaizo), keys)
        HackLabels.keys = keys
        val k = HackLabels.strip(kaizo, scans(82606))
        assertTrue(k.none { it.first in HackLabels.DROP })
        assertTrue(k.any { it.first == "Box" })
        assertEquals(scans(490544), HackLabels.strip(clear, scans(490544)))
        assertEquals(scans(82606), HackLabels.strip(crystal, scans(82606)))
        HackLabels.keys = emptySet()
    }

    @Test fun wiredBeforeYourPicks() {
        val src = java.io.File(listOf("../src_copy/data", "src/main/java/com/shelf/launcher/data", "app/src/main/java/com/shelf/launcher/data").first { java.io.File(it).exists() }, "PhysicalOverrides.kt").readText()
        assertTrue(src.contains("val list0 = HackLabels.strip(key,"))
    }
}
