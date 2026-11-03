package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 465: (1) the plain-colour box sides are a separate choice — the default stays "Fill missing sides", and a real scan
 * or your picture of a side still wins; (2) no doubled print on a cartridge label: a real label scan gets no generated
 * header, footer or code, and a photo-made DS / 3DS card covers the photo's whole sticker (its own code strip included).
 */
class BoxSidesLabels465Test {
    private fun src(n: String) = File("src/main/java/com/shelf/launcher/$n").readText()

    @Test fun plainColourIsAnOptionNotTheDefault() {
        val f = src("data/Features.kt")
        assertTrue(f.contains("""COLOUR("Plain colour from the box art")"""))
        assertTrue(f.contains("""val boxSides = EnumPref("box_sides", BoxSides.FILL, BoxSides.values())"""))
        assertEquals("FILL", com.shelf.launcher.data.Features.BoxSides.values().first().name)
    }

    @Test fun realSideScansAndYourPicturesWinOverTheColour() {
        val s = src("ui/BoxSides.kt")
        // A side's own picture is taken first in the colour mode too (pick), the colour only where there's none.
        assertTrue(s.contains("mode == com.shelf.launcher.data.Features.BoxSides.FILL || mode == com.shelf.launcher.data.Features.BoxSides.COLOUR || com.shelf.launcher.data.ArtProvenance.isScan(it)"))
        val i = s.indexOf("if (b != null) DerivedArt.of(b, \"side|")
        assertTrue(i > 0 && i < s.indexOf("} else if (oneColour && front != null) {"))
        // The colour comes from the cover's artwork (its middle / art window), not a printed band at its edge.
        assertTrue(s.contains("fun boxColour(front: Bitmap, platform: String? = null): Int?"))
        // The spine is never a generated side: it's the case's own scan / cut (case3dModels), not one of these sides.
        assertEquals(listOf("RIGHT", "TOP", "BOTTOM"), BoxSide.values().map { it.name })
    }

    @Test fun noGeneratedPrintOverARealLabelScan() {
        val cs = src("ui/CartShells.kt")
        assertTrue(cs.contains("val plaque = header?.takeIf { !scan }"))
        assertTrue(cs.contains("""val switchHeader = if (plaque == null && s.id == "switch" && !scan)"""))
        assertTrue(cs.contains("""val paperOn = plaque == null && switchHeader == null && !scan && art != null && (s.id == "ds" || s.id == "3ds")"""))
        assertTrue(cs.contains("val ids = if (!scan) com.shelf.launcher.data.LabelIds.of(title) else null"))
        // The product code is printed only from the ROM header (no invented codes) and only with a cover.
        assertTrue(cs.contains("val idText = ids?.code"))
    }

    @Test fun photoCardsCoverTheirWholeSticker() {
        val ps = src("ui/PhotoShells.kt")
        assertTrue(ps.contains("""private val WHOLE = setOf("ds", "3ds")"""))
        assertTrue(ps.contains("""val label = if (clear || shell.id in WHOLE) est else findSticker(out, est, plastic)"""))
        assertTrue(ps.contains("""val sl = if (shell.id in WHOLE) STICKER[shell.id]!! else shell.label"""))
        // The DS / 3DS sticker runs to the code strip (31 mm from 1.6 mm of a 35 mm card).
        assertTrue(ps.contains(""""ds" to floatArrayOf(2.9f, 1.6f, 27f, 31f), "3ds" to floatArrayOf(2.9f, 1.6f, 27f, 31f)"""))
    }
}
