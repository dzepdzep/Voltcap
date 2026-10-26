package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 330: Voltcap's own console pictures (the curated 74) — the files, the matching, the defaults, picks. */
class ConsoleArt330Test {

    @Test
    fun everyPictureIsShippedAndNothingElse() {
        val dir = listOf(java.io.File("src/main/assets/console_hw"), java.io.File("app/src/main/assets/console_hw")).first { it.isDirectory }
        val shipped = dir.listFiles()!!.map { it.name.removeSuffix(".webp") }.toSet()
        assertEquals(74, shipped.size)
        assertEquals(ConsoleArt.FILES, shipped)
        ConsoleArt.VARIANTS.values.flatten().forEach { assertTrue("$it shipped", it in ConsoleArt.FILES) }
    }

    @Test
    fun defaults() {
        assertEquals("nds", ConsoleArt.nameFor("NDS"))
        assertEquals("n3ds", ConsoleArt.nameFor("3DS"))
        // The Steam Machine, not a Steam Deck.
        assertEquals("steam", ConsoleArt.nameFor("Steam"))
        assertEquals("psx", ConsoleArt.nameFor("PS1"))
        assertEquals("genesis", ConsoleArt.nameFor("Genesis"))
        assertEquals("gc", ConsoleArt.nameFor("GameCube"))
        assertEquals("wonderswancolor", ConsoleArt.nameFor("WonderSwan"))
        assertEquals("sega32x", ConsoleArt.nameFor("32X"))
    }

    @Test
    fun esdeNamesMatch() {
        // ES-DE system names Voltcap folds into its own keys…
        assertEquals("genesis", ConsoleArt.nameFor("megadrive"))
        assertEquals("n3ds", ConsoleArt.nameFor("n3ds"))
        assertEquals("psx", ConsoleArt.nameFor("psx"))
        // …and the special hardware a folder keeps as its own system.
        assertEquals("ps5", ConsoleArt.nameFor("ps5"))
        assertEquals("n64dd", ConsoleArt.nameFor("n64dd"))
        assertEquals("famicom", ConsoleArt.nameFor("famicom"))
        assertEquals("atarijaguarcd", ConsoleArt.nameFor("atarijaguarcd"))
        assertEquals("arduboy", ConsoleArt.nameFor("arduboy"))
        assertEquals("xboxseries_x", ConsoleArt.nameFor("xboxseriesx"))
        // Variant files are never a system of their own.
        assertNull(ConsoleArt.nameFor("ds_open"))
        assertNull(ConsoleArt.nameFor("Arcade"))
    }

    @Test
    fun aPickWinsAndStaysPerConsole() {
        val before = Features.consoleHwPick.value
        try {
            ConsoleArt.pick("NDS", "ds_open")
            ConsoleArt.pick("PS2", "ps2_slim")
            assertEquals("ds_open", ConsoleArt.nameFor("NDS"))
            assertEquals("ps2_slim", ConsoleArt.nameFor("PS2"))
            assertEquals("n3ds", ConsoleArt.nameFor("3DS"))
            ConsoleArt.pick("NDS", "nds")
            assertEquals("nds", ConsoleArt.nameFor("NDS"))
            // A pick that isn't that console's is ignored.
            ConsoleArt.pick("GBA", "ps5")
            assertEquals("gba", ConsoleArt.nameFor("GBA"))
            assertTrue(ConsoleArt.isOurs(ConsoleArt.model("NDS")))
        } finally {
            Features.consoleHwPick.value = before
        }
    }
}
