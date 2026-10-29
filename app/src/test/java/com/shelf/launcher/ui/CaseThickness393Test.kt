package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 393: one case thickness for the shelf, the box, Cover details and the selected 3D case, and a spine scan close to
 * its slot's shape fills it (no hairline gaps) while a very different one keeps its own shape (intentional differences).
 */
class CaseThickness393Test {
    @Test fun scanNearSlotFillsIt() {
        assertEquals(0.10f, CaseThickness.spineFill(0.10f, 0.095f), 0f)
        assertEquals(0.10f, CaseThickness.spineFill(0.10f, 0.113f), 0f)
    }

    @Test fun scanFarFromSlotKeepsItsOwnShape() {
        assertEquals(0.06f, CaseThickness.spineFill(0.10f, 0.06f), 0f)
    }

    @Test fun neverWiderThanSlot() {
        assertEquals(0.10f, CaseThickness.spineFill(0.10f, 0.30f), 0f)
    }

    @Test fun depthIsKeptToRealCases() {
        assertEquals(CaseThickness.MIN_DEPTH, CaseThickness.clamp(0.01f), 0f)
        assertEquals(CaseThickness.MAX_DEPTH, CaseThickness.clamp(0.9f), 0f)
        assertEquals(0.12f, CaseThickness.clamp(0.12f), 0f)
    }

    @Test fun everyCatalogCaseThicknessIsPhysical() {
        for (p in listOf("PS2", "PS1", "NES", "SNES", "N64", "GBA", "NDS", "Switch", "Xbox360")) for (r in CaseRegion.values()) {
            val s = CaseCatalog.of(p, r) ?: continue
            assertTrue("$p $r", CaseThickness.clamp(s.depth) in CaseThickness.MIN_DEPTH..CaseThickness.MAX_DEPTH)
        }
    }
}
