package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 463: photo-made shells for every console with a real cartridge or card — only from photos of that very shell. */
class PhotoShells463Test {
    private fun src(n: String) = File("src/main/java/com/shelf/launcher/ui/$n").readText()

    @Test fun realCartridgeShellsSupported() {
        for (id in listOf("famicom", "snes_na", "sfc", "n64", "ds", "3ds", "switch", "genesis", "megadrive_jp", "sms", "sms_jp",
                "game_gear", "32x", "hucard", "ngp", "wonderswan", "atari2600", "lynx")) assertTrue(id, PhotoShells.supports(id))
        // Shells a photo can't be told apart for, or with no source of their own: the drawn shell stays.
        for (id in listOf("nes", "neogeo_aes", "neogeo_mvs", "switch2", "switch2_key", "genesis_ea", "gb_camera", "pokemon_pinball", "sonic_knuckles"))
            assertFalse(id, PhotoShells.supports(id))
    }

    @Test fun photoRegionFromItsOwnAddress() {
        assertEquals(CaseRegion.US, PhotoShells.photoRegion("https://x/m?systemeid=4&region=us&media=support-2D"))
        assertEquals(CaseRegion.JP, PhotoShells.photoRegion("https://x/m?systemeid=4&region=jp"))
        assertEquals(CaseRegion.EU, PhotoShells.photoRegion("https://x/m?systemeid=4&region=eu"))
        assertNull(PhotoShells.photoRegion("https://x/m?systemeid=4&region=wor"))
        assertNull(PhotoShells.photoRegion(null))
    }

    @Test fun regionalShellsFromThePhoto() {
        // A US SNES photo is the North American pak; a Japanese / PAL one the Super Famicom shape — never the other's.
        assertEquals("snes_na", PhotoShells.shellOfGame("Super Metroid (Japan, USA).sfc", "4", "https://x/m?systemeid=4&region=us"))
        assertEquals("sfc", PhotoShells.shellOfGame("Super Metroid (Japan, USA).sfc", "4", "https://x/m?systemeid=4&region=jp"))
        assertEquals("sfc", PhotoShells.shellOfGame("Zelda (Europe).sfc", "4", "https://x/m?systemeid=4&region=wor"))
        assertEquals("famicom", PhotoShells.shellOfGame("Mario (Japan).nes", "3", "https://x/m?systemeid=3&region=jp"))
        assertEquals("nes", PhotoShells.shellOfGame("Mario (USA).nes", "3", "https://x/m?systemeid=3&region=us"))
        assertEquals("megadrive_jp", PhotoShells.shellOfGame("Sonic (Japan).md", "1", "https://x/m?systemeid=1&region=jp"))
        assertEquals("genesis", PhotoShells.shellOfGame("Sonic (USA).md", "1", "https://x/m?systemeid=1&region=us"))
        assertEquals("n64", PhotoShells.shellOfGame("Mario (USA).z64", "14", "https://x/m?systemeid=14&region=us"))
        assertNull(PhotoShells.shellOfGame("x.bin", "999", null))
    }

    @Test fun noRecolouringOutsideGameBoy() {
        val s = src("PhotoShells.kt")
        assertTrue(s.contains("if (shellId in GB_FAMILY) 120f else 90f"))
        assertTrue(s.contains("fun tinted(shellId: String): Boolean = shellId in GB_FAMILY"))
        assertTrue(s.contains("shell.colours.any { it.opacity < 0.85f } && boardShows(out, est, plastic)"))
        assertTrue(s.contains("colourDistance(it.plastic, colour.argb) < tolerance(shell.id) && (tinted(shell.id) || sameKind(it.plastic, colour.argb))"))
        // The photo game's own shell decides (authenticIdFor), not the region setting.
        assertTrue(s.contains("CartShells.authenticIdFor(con, photoRegion(url) ?: CaseCatalog.regionOf(key, com.shelf.launcher.data.Features.CaseRegionChoice.AUTO_US), key)"))
    }
}
