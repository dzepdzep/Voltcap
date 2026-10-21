package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 303: Xbox 360, PS3 and PS4 retail cases. */
class Cases303Test {
    private fun spec(p: String) = CaseCatalog.of(p, CaseRegion.US)!!

    @Test fun ps3AndPs4AreGameCaseThick() {
        for (p in listOf("PS3", "PS4")) {
            val s = spec(p)
            assertEquals(135f, s.wMm, 0.01f); assertEquals(171.5f, s.hMm, 0.01f); assertEquals(14f, s.tMm, 0.01f)
            assertEquals(CaseShell.KEEP, s.shell)
            assertTrue(!s.mediaLeft)
        }
    }

    @Test fun xbox360IsDvdSizeLimeGreen() {
        val s = spec("Xbox360")
        assertEquals(135f, s.wMm, 0.01f); assertEquals(190f, s.hMm, 0.01f); assertEquals(14f, s.tMm, 0.01f)
        assertTrue(s.opacity < 1f) // see-through
    }

    @Test fun sameInEveryRegion() {
        for (p in listOf("PS3", "PS4", "Xbox360")) for (r in CaseRegion.values()) assertEquals(spec(p).tMm, CaseCatalog.of(p, r)!!.tMm, 0.01f)
    }

    @Test fun othersUntouched() {
        // (Update 304: PS5 now 14 mm — Cases304Test.)
        assertEquals(12f, spec("XboxOne").tMm, 0.01f)
    }
}
