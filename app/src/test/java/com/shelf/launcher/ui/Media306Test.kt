package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 306: disc mappings, pack shapes on their real cartridges, the game's own console on another console's shell. */
class Media306Test {
    @Test fun discConsolesAreDiscs() {
        for (p in listOf("PS5", "XboxOne", "XboxSeries", "CDi")) {
            val m = MediaShell.of(p)
            assertNotNull(p, m); assertTrue(p, m!!.isDisc)
            assertEquals(p, "disc", mediaKind(p))
        }
        // Unchanged neighbours.
        assertEquals(MediaShell.DISC, MediaShell.of("PS4")); assertEquals(MediaShell.N64, MediaShell.of("N64"))
        assertEquals("PlayStation 5", com.shelf.launcher.data.ConsoleNames.display("PS5"))
    }

    @Test fun packShapesUseTheirRealCartridge() {
        assertEquals("n64", CustomCarts.authenticShell(CartLook.N64)!!.id)
        assertEquals("gb", CustomCarts.authenticShell(CartLook.GB)!!.id)
        assertEquals("snes_na", CustomCarts.authenticShell(CartLook.SNES)!!.id)
        assertNull(CustomCarts.authenticShell(CartLook.HUCARD))
        assertNull(CustomCarts.authenticShell(CartLook.VITA))
        val (sh, console) = CustomCarts.authenticForPick(SHELL_PREFIX + "pack:n64~opaque|PS2")!!
        assertEquals("n64", sh.id); assertEquals("PS2", console)
        assertEquals(sh.depth, CustomCarts.depthForPick(SHELL_PREFIX + "pack:n64~opaque|PS2")!!, 0.0001f)
        // A library shell (not a pack shape) isn't a custom piece's real cartridge — it is one.
        assertNull(CustomCarts.authenticForPick(SHELL_PREFIX + "n64~grey|PS2"))
    }

    @Test fun anotherConsolesGameIsBrandedWithItsOwnConsole() {
        val n64 = CartBacks.of("n64")!!
        val ps2 = CartShells.rebranded(n64, "PS2")
        assertTrue(ps2.texts.contains("PlayStation 2"))
        assertFalse(ps2.texts.any { it.contains("Nintendo", ignoreCase = true) })
        assertEquals(ps2.texts.size * 5, ps2.textPos.size)
        // Generic markings stay, model numbers and second maker lines go.
        val gba = CartBacks.all.values.first { b -> b.texts.any { it.startsWith("MODEL") } }
        val r = CartShells.rebranded(gba, "PS2")
        assertTrue(r.texts.contains("PlayStation 2"))
        assertFalse(r.texts.any { it.startsWith("MODEL") })
        assertTrue(r.texts.any { it.contains("MADE IN", ignoreCase = true) })
        assertFalse(CartShells.isBrandText("MADE IN JAPAN")); assertFalse(CartShells.isBrandText("PAT. PEND."))
        assertTrue(CartShells.isBrandText("SEGA")); assertTrue(CartShells.isBrandText("GAME BOY"))
        // The shell's own console keeps its real back (no rebrand needed).
        assertTrue(CartShells.belongsTo("n64", "N64")); assertFalse(CartShells.belongsTo("n64", "PS2"))
    }
}
