package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 304: PS5 case thickness; Xbox One / Series and the rest of the PlayStation cases unchanged. */
class Cases304Test {
    private fun spec(p: String, r: CaseRegion = CaseRegion.US) = CaseCatalog.of(p, r)!!

    @Test fun ps5IsThePs4Shell() {
        for (r in CaseRegion.values()) {
            val s = spec("PS5", r); val ps4 = spec("PS4", r)
            assertEquals(ps4.wMm, s.wMm, 0.01f); assertEquals(ps4.hMm, s.hMm, 0.01f); assertEquals(14f, s.tMm, 0.01f)
            assertEquals(CaseShell.KEEP, s.shell)
            assertTrue(!s.mediaLeft) // (disc on the right, inside the back)
        }
    }

    @Test fun xboxBluRayCasesStay12AndLeftHanded() {
        for (p in listOf("XboxOne", "XboxSeries")) {
            val s = spec(p)
            assertEquals(135f, s.wMm, 0.01f); assertEquals(171.5f, s.hMm, 0.01f); assertEquals(12f, s.tMm, 0.01f)
            assertTrue(s.mediaLeft)
        }
    }

    @Test fun ps3Ps4Xbox360Unchanged() {
        assertEquals(14f, spec("PS3").tMm, 0.01f); assertEquals(14f, spec("PS4").tMm, 0.01f)
        assertEquals(190f, spec("Xbox360").hMm, 0.01f); assertEquals(14f, spec("Xbox360").tMm, 0.01f)
    }
}
