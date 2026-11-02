package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 458 (D174): a drawn Game Boy pak's sticker fills its recess like a real one; moulded top panel; grain. */
class GbPak458Test {
    @Test fun portraitCoverKeepsItsTop() {
        // a box front cut to its art (≈0.81 wide for 1 tall) into the 42.6 × 38.5 sticker
        val r = CartShells.stickerCrop(810, 1000, 42.6f / 38.5f)
        assertEquals(0, r[0]); assertEquals(810, r[2])
        val keptH = r[3] - r[1]
        assertEquals(810f / (42.6f / 38.5f), keptH.toFloat(), 2f)
        // the upper part is kept: far less cut off the top than the foot
        assertTrue(r[1] < (1000 - r[3]))
    }

    @Test fun wideCoverCutInTheMiddle() {
        val r = CartShells.stickerCrop(2000, 1000, 1.1f)
        assertEquals(1000, r[3] - r[1])
        assertEquals(1100f, (r[2] - r[0]).toFloat(), 2f)
        assertTrue(kotlin.math.abs(r[0] - (2000 - r[2])) <= 1)
    }

    @Test fun wired() {
        val src = listOf(java.io.File("../src_copy/ui"), java.io.File("src/main/java/com/shelf/launcher/ui"), java.io.File("app/src/main/java/com/shelf/launcher/ui"))
            .first { it.exists() }
        val s = java.io.File(src, "CartShells.kt").readText()
        assertTrue(s.contains("val fullSticker = s.id in GB_FAMILY"))
        assertTrue(s.contains("mouldedTop(c, mould"))
        assertTrue(s.contains("grain(c, bw, bh, k, p)"))
        // a real label scan is still drawn as it is (never cropped by the pak's own fitting)
        assertTrue(s.contains("if (scan) c.drawBitmap(art, foldCrop(s, art)"))
    }
}
