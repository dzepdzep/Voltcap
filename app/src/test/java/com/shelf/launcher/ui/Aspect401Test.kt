package com.shelf.launcher.ui

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Update 401: a cover is cropped to a face, never stretched (stretched-artwork report, PS2 case). */
class Aspect401Test {
    private fun check(w: Int, h: Int, aspect: Float) {
        val r = coverCropRect(w, h, aspect) ?: return
        val got = r[2].toFloat() / r[3]
        assertTrue("$w x $h -> ${r[2]} x ${r[3]} is not $aspect", kotlin.math.abs(got / aspect - 1f) < 0.02f)
        assertTrue(r[0] >= 0 && r[1] >= 0 && r[0] + r[2] <= w && r[1] + r[3] <= h)
        // Centred: equal margins (within a pixel).
        assertTrue(kotlin.math.abs(r[0] - (w - r[0] - r[2])) <= 1 && kotlin.math.abs(r[1] - (h - r[1] - r[3])) <= 1)
    }

    @Test fun cropsToTheFaceWithoutStretching() {
        val faces = listOf(0.71f, 0.76f, 0.9f, 1.0f, 0.6f, 1.25f)   // DVD case, N64 / NES box, DS, jewel, ...
        val covers = listOf(600 to 900, 1024 to 1024, 900 to 600, 342 to 482, 512 to 724, 700 to 1000)
        for (a in faces) for ((w, h) in covers) check(w, h, a)
    }

    @Test fun matchingShapeIsLeftAlone() {
        assertNull(coverCropRect(710, 1000, 0.71f))
        assertEquals(null, coverCropRect(600, 600, 1.0f))
    }
}
