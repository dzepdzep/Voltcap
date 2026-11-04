package com.shelf.launcher.ui

import com.shelf.launcher.data.LabelIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 468: the clear Game Boy pak's board follows the ROM header; its measured places stay on the board. */
class GbBoard468Test {
    private fun header(type: Int, good: Boolean = true): ByteArray {
        val h = ByteArray(0x150)
        h[0x143] = 0xC0.toByte(); h[0x147] = type.toByte()
        var x = 0
        for (i in 0x134..0x14C) x = (x - (h[i].toInt() and 0xFF) - 1) and 0xFF
        h[0x14D] = (if (good) x else x + 1).toByte()
        return h
    }

    @Test fun cartTypeOnlyFromAGoodHeader() {
        assertEquals(0x10, LabelIds.gbCartType(header(0x10)))
        assertNull(LabelIds.gbCartType(header(0x10, good = false)))
        assertNull(LabelIds.gbCartType(ByteArray(0x100)))
    }

    @Test fun partsFollowTheCartridgeType() {
        // Pokémon Gold / Silver / Crystal: MBC3 + timer + RAM + battery — the photographed board, all parts.
        assertEquals(GbBoard.Parts(mapper = true, ram = true, battery = true, crystal = true), GbBoard.partsOf(0x10))
        // MBC5 + RAM + battery: no crystal.
        assertEquals(GbBoard.Parts(mapper = true, ram = true, battery = true, crystal = false), GbBoard.partsOf(0x1B))
        // MBC5 alone: no RAM, no battery.
        val mbc5 = GbBoard.partsOf(0x19)
        assertTrue(mbc5.mapper); assertFalse(mbc5.ram); assertFalse(mbc5.battery)
        // ROM only: no mapper either.
        assertFalse(GbBoard.partsOf(0x00).mapper)
        // Different boards for different types (not one board for every game).
        assertTrue(GbBoard.partsOf(0x10).key != GbBoard.partsOf(0x19).key)
    }

    @Test fun measuredPartsAreOnTheBoardAndDontOverlapTheBigChips() {
        for (a in listOf(GbBoard.MAPPER, GbBoard.RAM, GbBoard.ROM, GbBoard.RESET, GbBoard.CRYSTAL))
            assertTrue(a[0] >= 0f && a[1] >= 0f && a[0] + a[2] <= 1f && a[1] + a[3] <= GbBoard.FINGERS_TOP)
        // RAM (left) and ROM (right) side by side, the screw hole between them.
        assertTrue(GbBoard.RAM[0] + GbBoard.RAM[2] < GbBoard.SCREW[0] - GbBoard.SCREW[2])
        assertTrue(GbBoard.ROM[0] > GbBoard.SCREW[0] + GbBoard.SCREW[2])
    }

    @Test fun gameBoyPaksAreTheMeasuredThickness() {
        for (id in listOf("gb", "gbc_dual", "gbc")) assertEquals(id, 7.8f, CartShells.of(id)!!.t, 0.05f)
    }
}
