package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 461 (owner's option D): the printed Game Boy sticker for a pak with no label scan. */
class PrintedGameBoy461Test {
    private val mm = 10f
    private val w = 426f; private val h = 385f // the Game Boy sticker, 42.6 × 38.5 mm

    @Test fun onlyTheGameBoyFamily() {
        assertTrue(GeneratedLabels.printed(GeneratedLabels.Kind.DMG))
        assertTrue(GeneratedLabels.printed(GeneratedLabels.Kind.CGB))
        for (k in GeneratedLabels.Kind.values().filter { it != GeneratedLabels.Kind.DMG && it != GeneratedLabels.Kind.CGB }) assertFalse(k.name, GeneratedLabels.printed(k))
        assertEquals(GeneratedLabels.Kind.DMG, GeneratedLabels.kindOf("gb"))
        assertEquals(GeneratedLabels.Kind.CGB, GeneratedLabels.kindOf("gbc"))
        // the 459 designs stay off
        assertFalse(GeneratedLabels.enabled)
    }

    @Test fun coverWholeAtItsOwnProportions() {
        for (a in listOf(0.72f, 0.8f, 1f, 1.3f, 1.8f)) {
            val r = GeneratedLabels.printedArtRect(w, h, a, mm)
            val rw = r[2] - r[0]; val rh = r[3] - r[1]
            assertEquals("aspect $a", a, rw / rh, 0.001f) // never stretched
            assertTrue(r[0] >= 3.6f * mm - 0.01f && r[2] <= w - 3.6f * mm + 0.01f) // grey printed sides
            assertTrue(r[1] >= -0.01f && r[3] <= h + 0.01f)
            assertTrue(rw >= w - 2 * 3.6f * mm - 0.01f || rh >= h - 0.01f) // as large as fits: a portrait cover the full height
            assertEquals(w / 2f, (r[0] + r[2]) / 2f, 0.01f)
        }
        assertEquals(h, GeneratedLabels.printedArtRect(w, h, 0.79f, mm).let { it[3] - it[1] }, 0.01f)
    }

    @Test fun printsNoMarkings() {
        val src = java.io.File(listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it).exists() }, "GeneratedLabels.kt").readText()
        val body = src.substring(src.indexOf("fun printedGameBoy"), src.indexOf("return b", src.indexOf("fun printedGameBoy")))
        for (bad in listOf("idLine", "code", "region", "FAN", "HOMEBREW", "ESRB", "Nintendo", "drawText")) assertFalse(bad, body.contains(bad))
        assertTrue(body.contains("\"THIS SIDE OUT\""))
    }
}
