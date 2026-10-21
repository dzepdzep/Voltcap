package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 297: regression tests for the physical-media audit's fixes. */
class PhysicalMediaFixesTest {

    private fun ds(v: DsCaseVariant): CaseSpec = DsCases.spec(CaseCatalog.of("NDS", v.region)!!, v)

    /** The opened case's inside is cached by specKey: DS cases with and without the GBA holder must never share one. */
    @Test fun dsVariantsHaveTheirOwnInside() {
        val keys = DsCaseVariant.values().map { CaseInside.specKey(ds(it)) }
        assertEquals("every DS variant draws its own inside", keys.size, keys.toSet().size)
        assertNotEquals(CaseInside.specKey(ds(DsCaseVariant.NA_GBA)), CaseInside.specKey(ds(DsCaseVariant.NA_PLAIN)))
        assertNotEquals(CaseInside.specKey(ds(DsCaseVariant.NA_PLAIN)), CaseInside.specKey(ds(DsCaseVariant.NA_ECO)))
        assertNotEquals(CaseInside.specKey(ds(DsCaseVariant.EU_CLEAR)), CaseInside.specKey(ds(DsCaseVariant.EU_CLEAR_PLAIN)))
    }

    /** Only the variants documented with a holder carry one; the slotless ones have no holder (and no eco cut-outs unless eco). */
    @Test fun gbaHolderOnlyOnHolderVariants() {
        for (v in DsCaseVariant.values()) {
            val s = ds(v)
            assertEquals(v.name, v.gbaHolder, s.gbaSlot)
            assertEquals(v.name, v.eco, s.eco)
        }
        assertFalse(ds(DsCaseVariant.NA_PLAIN).gbaSlot)
        assertFalse(ds(DsCaseVariant.JP_PLAIN).gbaSlot)
    }

    /** The same spec always gives the same key (the cache still works). */
    @Test fun specKeyIsStable() {
        assertEquals(CaseInside.specKey(ds(DsCaseVariant.JP)), CaseInside.specKey(ds(DsCaseVariant.JP)))
        assertEquals("-", CaseInside.specKey(null))
    }

    /** A Switch box front (logo tile across its top ~13 %, rating box from ~82 % down) cut into the card's art window. */
    @Test fun switchCoverCutLeavesBoxPrintingOff() {
        val w = 620; val h = 1000
        val cut = SwitchLabel.cut(w, h, 1.083f)
        assertTrue("top logo tile cut off: ${cut[1]}", cut[1] >= (h * 0.13f).toInt())
        assertTrue("rating box cut off: ${cut[3]}", cut[3] <= (h * 0.82f).toInt())
        assertEquals(0, cut[0]); assertEquals(w, cut[2])
    }

    /** A wide picture keeps its middle, never stretched. */
    @Test fun switchWideCutKeepsMiddle() {
        val cut = SwitchLabel.cut(2000, 1000, 1f)
        assertEquals(500, cut[0]); assertEquals(1500, cut[2]); assertEquals(0, cut[1]); assertEquals(1000, cut[3])
    }

    /** PS1 multi-disc: 2+ discs in North America / Japan, 3+ in Europe (two-disc PAL games keep the Full Jewel case). */
    @Test fun ps1MultiDiscCases() {
        assertNull(CaseCatalog.ps1Multi(CaseRegion.US, 1))
        assertEquals(2, CaseCatalog.ps1Multi(CaseRegion.US, 2)!!.discs)
        assertEquals(CaseInner.FLIP_TRAY, CaseCatalog.ps1Multi(CaseRegion.JP, 3)!!.inner)
        assertNull(CaseCatalog.ps1Multi(CaseRegion.EU, 2))
        assertNotNull(CaseCatalog.ps1Multi(CaseRegion.EU, 4))
        assertEquals(25f, CaseCatalog.ps1Multi(CaseRegion.EU, 4)!!.tMm)
    }

    /** The disc count comes from the physical-disc tags, not from how many files a dump has. */
    @Test fun discCountFromTags() {
        assertEquals(3, CaseCatalog.discCount("/x/Game (USA) (Disc 1 of 3).chd"))
        assertEquals(1, CaseCatalog.discCount("/x/Gran Turismo 2 (USA) (Combined Disc) (Rev 2).chd"))
        assertEquals(1, CaseCatalog.discCount("/x/Ridge Racer (Europe).cue"))
    }

    /** The PAL Dreamcast case is its own three-part case, not a tinted jewel case; North America and Japan unchanged. */
    @Test fun palDreamcastCase() {
        val eu = CaseCatalog.of("Dreamcast", CaseRegion.EU)!!
        assertEquals(CaseInner.DC_PAL, eu.inner)
        assertEquals(CaseInner.NONE, CaseCatalog.of("Dreamcast", CaseRegion.US)!!.inner)
        assertEquals(10.2f, CaseCatalog.of("Dreamcast", CaseRegion.US)!!.tMm)
        assertEquals(CaseInner.NONE, CaseCatalog.of("Dreamcast", CaseRegion.JP)!!.inner)
        assertNull("manual isn't in the PAL Dreamcast case's front", CaseInside.bookletRect(eu, CaseInside.Kind.JEWEL, 142f / 125f))
    }
}
