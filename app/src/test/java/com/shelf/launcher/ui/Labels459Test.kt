package com.shelf.launcher.ui

import com.shelf.launcher.data.LabelIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 459: generated cartridge labels — the full sticker, the cover whole at its own proportions, true identification
 * only (header codes, FAN-MADE / HOMEBREW), and the Settings pages' slide direction.
 */
class Labels459Test {
    private fun inside(r: FloatArray, w: Float, h: Float) = r[0] >= -0.01f && r[1] >= -0.01f && r[2] <= w + 0.01f && r[3] <= h + 0.01f

    @Test fun coverKeepsItsShapeAndIsWhole() {
        // Pokémon Brown's cover (≈0.81 wide for 1 tall) on the Game Boy sticker (42.6 × 38.5 mm)
        for (kind in GeneratedLabels.Kind.values()) for (a in listOf(0.62f, 0.81f, 1f, 1.38f, 1.9f)) {
            val r = GeneratedLabels.artRect(kind, 426f, 385f, a)
            assertEquals("$kind $a aspect kept", a, (r[2] - r[0]) / (r[3] - r[1]), 0.01f)
            assertTrue("$kind $a inside the sticker", inside(r, 426f, 385f))
        }
        // Game Boy: full height between the grey borders, centred
        val gb = GeneratedLabels.artRect(GeneratedLabels.Kind.DMG, 426f, 385f, 0.81f)
        assertEquals(385f, gb[3] - gb[1], 0.5f)
        assertEquals(gb[0], 426f - gb[2], 0.5f)
        // GBA: the art against the right edge, the printed panel left of it
        val agb = GeneratedLabels.artRect(GeneratedLabels.Kind.AGB, 430f, 220f, 0.9f)
        assertEquals(430f, agb[2], 0.5f)
    }

    @Test fun platformDesigns() {
        assertEquals(GeneratedLabels.Kind.DMG, GeneratedLabels.kindOf("gb"))
        assertEquals(GeneratedLabels.Kind.DMG, GeneratedLabels.kindOf("gbc_dual"))
        assertEquals(GeneratedLabels.Kind.CGB, GeneratedLabels.kindOf("gbc"))
        assertEquals(GeneratedLabels.Kind.AGB, GeneratedLabels.kindOf("gba"))
        assertEquals(GeneratedLabels.Kind.N64, GeneratedLabels.kindOf("n64"))
        assertEquals(GeneratedLabels.Kind.SNES, GeneratedLabels.kindOf("snes_na"))
        assertEquals(GeneratedLabels.Kind.SFC, GeneratedLabels.kindOf("sfc"))
        assertEquals(GeneratedLabels.Kind.NES, GeneratedLabels.kindOf("nes"))
        assertEquals(GeneratedLabels.Kind.FAMICOM, GeneratedLabels.kindOf("famicom"))
        assertEquals(GeneratedLabels.Kind.MEGA_DRIVE, GeneratedLabels.kindOf("genesis"))
        assertEquals(GeneratedLabels.Kind.MASTER_SYSTEM, GeneratedLabels.kindOf("sms"))
        assertEquals(GeneratedLabels.Kind.GAME_GEAR, GeneratedLabels.kindOf("game_gear"))
        assertEquals(GeneratedLabels.Kind.SEGA_32X, GeneratedLabels.kindOf("32x"))
        // DS / 3DS / Switch keep their own sticker frames (SwitchLabel)
        assertNull(GeneratedLabels.kindOf("ds")); assertNull(GeneratedLabels.kindOf("3ds")); assertNull(GeneratedLabels.kindOf("switch"))
    }

    @Test fun onlyTrueIdentification() {
        val gba = ByteArray(0x200); "AW2E".toByteArray().copyInto(gba, 0xAC)
        assertEquals("AGB-AW2E-USA", LabelIds.codeFromHeader("gba", gba))
        val nds = ByteArray(0x200); "ADME".toByteArray().copyInto(nds, 0x0C)
        assertEquals("NTR-ADME-USA", LabelIds.codeFromHeader("nds", nds))
        val n64 = ByteArray(0x200); n64[0] = 0x80.toByte(); n64[1] = 0x37; "NSME".toByteArray().copyInto(n64, 0x3B)
        assertEquals("NUS-NSME-USA", LabelIds.codeFromHeader("z64", n64))
        val dmg = ByteArray(0x200) // an original Game Boy game: no code in its header
        assertNull(LabelIds.codeFromHeader("gb", dmg))
        val junk = ByteArray(0x200) { 0x7F }
        assertNull(LabelIds.codeFromHeader("gba", junk))
        // a hack is FAN-MADE, never its base game's code; homebrew is HOMEBREW
        assertEquals("FAN-MADE", LabelIds.unofficialOf("Pokemon - Brown Version (Hack)", "/r/Pokemon - Brown Version (Hack).gb"))
        assertEquals("HOMEBREW", LabelIds.unofficialOf("Adulting! (World) (v2.0) (Aftermarket)", null))
        assertNull(LabelIds.unofficialOf("Advance Wars 2 - Black Hole Rising (USA)", null))
        assertEquals("FAN-MADE", GeneratedLabels.idLine(LabelIds.Id("AGB-BPRE-USA", "FAN-MADE", "USA")))
        assertEquals("EUR", LabelIds.regionOf("Asterix & Obelix XXL (Europe) (En,Fr,De)"))
        assertEquals("USA", LabelIds.regionOf("Aladdin (USA) (En,Fr,De,Es)"))
    }

    @Test fun inventedCodesGone() {
        val src = java.io.File(listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it).exists() }, "CartShells.kt").readText()
        assertTrue("no made-up DS codes", !src.contains("fun productCode("))
        assertTrue(src.contains("GeneratedLabels.compose(labelKind"))
        // the moulded branding no longer depends on the Cartridge backs setting
        val top = src.substringAfter("internal fun gbTopText").substringBefore("\n    }\n")
        assertTrue(!top.contains("cartBackStyle"))
    }

    @Test fun settingsSlideDirection() {
        val order = listOf("Home", "Game look", "Theme")
        assertEquals(1, slideDirection(order, "Home", "Theme"))
        assertEquals(-1, slideDirection(order, "Theme", "Game look"))
        assertEquals(0, slideDirection(order, "Theme", "Theme"))
        val src = java.io.File(listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it).exists() }, "SettingsDialog.kt").readText()
        assertTrue(src.contains("SettingsPageSlide(category, CATEGORIES, paneWeight)"))
    }
}
