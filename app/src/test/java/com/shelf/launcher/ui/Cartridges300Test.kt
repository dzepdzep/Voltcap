package com.shelf.launcher.ui

import com.shelf.launcher.data.RomHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 300: which cartridge a game is drawn on — ROM headers, editions, the Disk System. */
class Cartridges300Test {
    /** A Game Boy header with [flag] at 0x143 and a correct checksum at 0x14D. */
    private fun gbHeader(flag: Int, goodSum: Boolean = true): ByteArray {
        val h = ByteArray(0x150)
        "POKEMON".toByteArray().copyInto(h, 0x134)
        h[0x143] = flag.toByte()
        var x = 0
        for (i in 0x134..0x14C) x = (x - (h[i].toInt() and 0xFF) - 1) and 0xFF
        h[0x14D] = (if (goodSum) x else x xor 0x55).toByte()
        return h
    }

    /** A DS header with [unit] at 0x012 and a correct CRC at 0x15E. */
    private fun ndsHeader(unit: Int, goodCrc: Boolean = true): ByteArray {
        val h = ByteArray(0x200)
        "GAMETITLE".toByteArray().copyInto(h, 0)
        h[0x12] = unit.toByte()
        val crc = RomHeaders.crc16(h, 0x15E) xor (if (goodCrc) 0 else 0x1234)
        h[0x15E] = (crc and 0xFF).toByte(); h[0x15F] = (crc shr 8).toByte()
        return h
    }

    @Test fun gbcDualModeHeader() = assertEquals(RomHeaders.Gb.CGB_DUAL, RomHeaders.gbOf(gbHeader(0x80)))
    @Test fun gbcOnlyHeader() = assertEquals(RomHeaders.Gb.CGB_ONLY, RomHeaders.gbOf(gbHeader(0xC0)))
    @Test fun originalGameBoyHeader() = assertEquals(RomHeaders.Gb.DMG, RomHeaders.gbOf(gbHeader(0x00)))

    @Test fun untrustedOrShortGbHeader() {
        assertNull(RomHeaders.gbOf(gbHeader(0x80, goodSum = false)))
        assertNull(RomHeaders.gbOf(ByteArray(0x100)))
        assertNull(RomHeaders.gbOf(ByteArray(0)))
    }

    @Test fun crc16KnownValue() = assertEquals(0x4B37, RomHeaders.crc16("123456789".toByteArray(), 9))

    @Test fun dsUnitCodes() {
        assertEquals(RomHeaders.Nds.NDS, RomHeaders.ndsOf(ndsHeader(0x00)))
        assertEquals(RomHeaders.Nds.DSI_ENHANCED, RomHeaders.ndsOf(ndsHeader(0x02)))
        assertEquals(RomHeaders.Nds.DSI_ONLY, RomHeaders.ndsOf(ndsHeader(0x03)))
        assertNull(RomHeaders.ndsOf(ndsHeader(0x03, goodCrc = false)))
        assertNull(RomHeaders.ndsOf(ndsHeader(0x07)))
        assertNull(RomHeaders.ndsOf(ByteArray(0x100)))
    }

    @Test fun aGbHeaderIsNotReadAsDs() = assertNull(RomHeaders.ndsOf(gbHeader(0x80).copyOf(0x200)))

    @Test fun disksystemFiles() {
        assertTrue(CartShells.isFdsFile("/sdcard/ROMs/fds/Zelda no Densetsu (Japan).fds"))
        assertTrue(CartShells.isFdsFile("/x/Metroid (Japan).FDS.zip"))
        assertFalse(CartShells.isFdsFile("/x/Metroid (USA).nes"))
        assertEquals("fds", CartShells.authenticIdFor("NES", CaseRegion.JP, "/roms/nes/Metroid (Japan).fds"))
        assertEquals("famicom", CartShells.authenticIdFor("NES", CaseRegion.JP, "/roms/nes/Gradius (Japan).nes"))
    }

    @Test fun fileNameTagStaysTheFallback() {
        // (No readable header here — no file, no context: the "(GB Compatible)" tag decides, as before.)
        assertEquals("gbc_dual", CartShells.authenticIdFor("GBC", CaseRegion.US, "/nope/Mario Golf (USA) (GB Compatible).gbc"))
        assertEquals("gbc", CartShells.authenticIdFor("GBC", CaseRegion.US, "/nope/Perfect Dark (USA).gbc"))
        assertEquals("gb", CartShells.authenticIdFor("GB", CaseRegion.US, "/nope/Tetris (World).gb"))
    }

    @Test fun blackDualModeCartIsTheGameBoyCase() {
        val gb = CartShells.of("gb")!!; val dual = CartShells.of("gbc_dual")!!
        assertEquals(gb.w, dual.w, 0.001f); assertEquals(gb.h, dual.h, 0.001f); assertEquals(gb.t, dual.t, 0.001f)
        assertEquals(9f, CartShells.of("gbc")!!.t, 0.001f) // (the clear Color-only cart unchanged)
        assertEquals(3.64f, CartShells.of("ds")!!.t, 0.001f)
    }

    @Test fun japaneseGoldAndSilverAreVerified() {
        for (key in listOf("/r/Pocket Monsters - Kin (Japan) (SGB Enhanced) (GB Compatible).gbc", "/r/Pocket Monsters - Gin (Japan) (SGB Enhanced) (GB Compatible).gbc")) {
            val e = MediaEditions.forGame("GBC", key)
            assertEquals(1, e.size)
            assertEquals(EditionStatus.VERIFIED, e[0].status)
            assertTrue(e[0].auto)
            assertEquals(setOf("JP"), e[0].regions)
        }
        // (Western Gold keeps its own gold shell; Crystal its clear cyan.)
        assertEquals("gb_pkmn_gold", MediaEditions.forGame("GBC", "/r/Pokemon - Gold Version (USA, Europe) (SGB Enhanced) (GB Compatible).gbc").single().id)
        assertEquals("gbc_crystal", MediaEditions.forGame("GBC", "/r/Pokemon - Crystal Version (USA, Europe) (Rev 1).gbc").single().id)
    }

    @Test fun ntr031IncludesKorea() {
        val e = MediaEditions.all.single { it.id == "ds_ntr031" }
        assertTrue("KOREA" in e.regions)
        assertTrue(MediaEditions.regionsOf("Pokemon - Black Version (Korea)").all { it in e.regions })
    }

    @Test fun yoshiTopsyTurvyNotAutoInPal() {
        val e = MediaEditions.all.single { it.id == "gba_yoshi_topsy" }
        assertFalse("EU" in e.regions); assertFalse("AU" in e.regions)
    }

    @Test fun dsiWhiteEditionUsesTheExistingMaterial() {
        assertEquals("ds", MediaEditions.DSI_WHITE.shell)
        assertNotNull(ShellMaterials.of("dsi_white"))
    }

    @Test fun nintendoPowerShellsAreWhite() {
        assertEquals("White", CartShells.of("np_gb")!!.colours.first().name)
        assertEquals("White", CartShells.of("np_sfc")!!.colours.first().name)
    }

    @Test fun dsiWareIsNotACard() {
        val h = ByteArray(0x240)
        h[0x12] = 3
        h[0x234] = 4; h[0x236] = 3 // title ID category 00030004
        val crc = RomHeaders.crc16(h, 0x15E); h[0x15E] = (crc and 0xFF).toByte(); h[0x15F] = (crc shr 8).toByte()
        assertEquals(RomHeaders.Nds.DSIWARE, RomHeaders.ndsOf(h))
        h[0x234] = 0 // 00030000: a game card
        val crc2 = RomHeaders.crc16(h, 0x15E); h[0x15E] = (crc2 and 0xFF).toByte(); h[0x15F] = (crc2 shr 8).toByte()
        assertEquals(RomHeaders.Nds.DSI_ONLY, RomHeaders.ndsOf(h))
    }

    @Test fun zippedRomNamesMatchTheirEdition() {
        assertEquals("Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible) [patched]",
            MediaEditions.fileNameOf("/r/Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible) [patched].gbc.zip"))
        assertEquals("gb_pkmn_silver", MediaEditions.forGame("GBC", "/r/Pokemon - Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible) [patched].gbc.zip").single().id)
        assertEquals("Super Mario Bros. 3 (USA)", MediaEditions.fileNameOf("/r/Super Mario Bros. 3 (USA).zip"))
        assertEquals("Game v1.1", MediaEditions.fileNameOf("/r/Game v1.1.zip"))
    }
}
