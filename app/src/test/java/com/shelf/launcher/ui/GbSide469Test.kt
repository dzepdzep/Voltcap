package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 469: the home screen's turned Game Boy paks get the measured sides (seam; clear walls with the board inside). */
class GbSide469Test {
    private fun side(id: String) = CartShells.of(id)!!.let { sh -> GbSide.of(sh, CartShells.colourOf(sh, null)) { 0x10 } }

    @Test fun onlyTheMeasuredPaks() {
        assertTrue(side("gbc")!!.clear)          // clear Color-only pak: clear, board inside
        assertEquals(0x10, side("gbc")!!.boardType)
        assertFalse(side("gbc_dual")!!.clear)    // black dual pak: opaque, seam only
        assertFalse(side("gb")!!.clear)
        assertNull(side("gba"))                  // GBA: not measured (no side reference) — unchanged
        assertNull(side("nes"))
    }

    @Test fun inspectAndHomeAgreeOnTheSeam() {
        // The seam / board depths are Inspect's (SlabSide) — one source for both views.
        assertEquals(5.8f, SlabSide.FRONT_HALF, 0.001f)
        assertTrue(SlabSide.BOARD_FROM_BACK + SlabSide.BOARD_T < CartShells.of("gbc")!!.t)
    }
}
