package com.shelf.launcher.ui

import com.shelf.launcher.data.LabelFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 460: real label first, your own / fan-made label second, no invented generated designs by default; plus the
 * Cube hub's steady name lines, the chip row crash and the case layouts' decoding ahead.
 */
class Labels460Test {
    private fun src(name: String, dir: String = "ui"): String =
        java.io.File(listOf("../src_copy/$dir", "src/main/java/com/shelf/launcher/$dir", "app/src/main/java/com/shelf/launcher/$dir").first { java.io.File(it).exists() }, name).readText()

    @Test fun folderNamesMatchGames() {
        assertEquals(LabelFolder.norm("Pokemon Brown.png"), LabelFolder.norm("/storage/1234/ROMs/gbc/Pokemon Brown (Hack).gbc"))
        assertEquals(LabelFolder.norm("Pokemon - Crystal Clear.jpg"), LabelFolder.norm("/r/gbc/Pokemon - Crystal Clear (v2.2) [Hack].gbc"))
        assertTrue(LabelFolder.norm("Pokemon Silver.png") != LabelFolder.norm("Pokemon Gold.png"))
    }

    @Test fun precedence() {
        val scraped = listOf("Box" to "b", "Disc label" to "real", "Disc / cartridge" to "photo")
        // an official game keeps its real label scan
        assertEquals(scraped, LabelFolder.merge(scraped, "file://mine", hack = false))
        // a ROM hack: its scraped label and photo are the base game's — your folder's label instead
        val hack = LabelFolder.merge(scraped, "file://mine", hack = true)
        assertEquals("file://mine", hack.first { it.first == "Disc label" }.second)
        assertTrue(hack.none { it.first == "Disc / cartridge" })
        assertEquals("b", hack.first { it.first == "Box" }.second)
        // no real label: the folder's
        assertEquals("file://mine", LabelFolder.merge(listOf("Box" to "b"), "file://mine", hack = false).last().second)
        // nothing in the folder: unchanged
        assertEquals(scraped, LabelFolder.merge(scraped, null, hack = true))
    }

    @Test fun folderScan() {
        val root = kotlin.io.path.createTempDirectory("labels").toFile()
        java.io.File(root, "Pokemon Brown.png").writeBytes(byteArrayOf(1, 2, 3))
        java.io.File(root, "notes.txt").writeText("x")
        java.io.File(root, "empty.png").createNewFile()
        java.io.File(root, "GBA").mkdirs(); java.io.File(root, "GBA/Advance Wars 2.jpg").writeBytes(byteArrayOf(1))
        val map = LabelFolder.scan(listOf(root))
        assertEquals(setOf("pokemonbrown", "advancewars2"), map.keys)
        LabelFolder.setForTest(map)
        assertTrue(LabelFolder.forKey("/r/gb/Pokemon Brown (Hack).gb")!!.startsWith("file://"))
        assertNull(LabelFolder.forKey("/r/gb/Pokemon Red.gb"))
        LabelFolder.setForTest(emptyMap())
        root.deleteRecursively()
    }

    @Test fun generatedDesignsOffByDefault() {
        assertFalse(GeneratedLabels.enabled)
        val cs = src("CartShells.kt")
        assertTrue(cs.contains("val labelKind = if (GeneratedLabels.enabled &&"))
        // ROM-hack status is never printed on a label; only a real header code
        assertTrue(cs.contains("val idText = ids?.code"))
        assertFalse(cs.contains("GeneratedLabels.idLine(ids)"))
    }

    @Test fun cubeHubLinesKeepTheirHeight() {
        val hub = src("CubeHub.kt")
        assertTrue(hub.contains("reservedLinesHeight(19.sp"))
        assertTrue(hub.contains("reservedLinesHeight(12.sp, lines = 2)"))
        val fit = src("FitText.kt")
        assertTrue(fit.contains("reserve: Boolean = true"))
    }

    @Test fun chipRowNeverAsksForAGoneItem() {
        assertTrue(src("WholeChipRow.kt").contains("if (i < liveCount.value) chip(i)"))
        assertTrue(src("CubeHub.kt").contains("c.stops.getOrNull(i) ?: return@WholeChipRow"))
    }

    @Test fun caseLayoutsDecodeAhead() {
        for (f in listOf("StyleCaseFlow.kt", "StyleCaseStacks.kt", "StyleRentalShop.kt")) assertTrue(f, src(f).contains("WarmCaseNeighbours("))
    }
}
