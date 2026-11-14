package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 529o (owner #15 6866376184): the shelf family's page keys are full-width bars in as few even rows as keep every word whole. */
class PageBars529Test {
    @Test fun allInOneRowWhenEachWordFitsItsShare() = assertEquals(listOf(listOf(0, 1, 2)), pageBarRows(listOf(90, 110, 80), 360, 6))

    // Play | Full details | Back at large text: Play | Back over a whole-width Full details - two rows, not three
    @Test fun aLongBarTakesItsOwnRowAndTheShortOnesPair() =
        assertEquals(listOf(listOf(0, 2), listOf(1)), pageBarRows(listOf(90, 300, 80), 360, 6))

    @Test fun inOrderWhenThatAlsoFitsInTwoRows() = assertEquals(listOf(listOf(0, 1), listOf(2)), pageBarRows(listOf(120, 170, 80), 360, 6))

    @Test fun oneToARowOnlyWhenNothingPairs() = assertEquals(listOf(listOf(0), listOf(1), listOf(2)), pageBarRows(listOf(200, 190, 220), 360, 6))

    @Test fun aWordWiderThanTheCardStillGetsARow() = assertEquals(listOf(listOf(0), listOf(1)), pageBarRows(listOf(500, 90), 360, 6))

    @Test fun aRowSpansTheCardExactly() {
        val ws = pageBarWidths(listOf(90, 110, 80), 360, 6)
        assertEquals(listOf(112, 132, 104), ws); assertEquals(360, ws.sum() + 2 * 6)
        assertEquals(listOf(360), pageBarWidths(listOf(500), 360, 6))
        assertEquals(360, pageBarWidths(listOf(200, 200), 360, 6).sum() + 6)
    }

    @Test fun theRoundMoreKeepsItsSize() {
        val ws = pageBarWidths(listOf(90, 110, 36), 360, 6, listOf(false, false, true))
        assertEquals(36, ws[2]); assertEquals(360, ws.sum() + 2 * 6)
    }

    // 529p: the pattern on Twin (both screens' keys) and Resume & discover
    @Test fun twinAndDiscoverUseTheBars() {
        val twin = java.io.File("src/main/java/com/shelf/launcher/ui/WinSceneTwin.kt").readText()
        assertTrue(twin.contains("PageBars(keys, 6.dp) { k -> twinKey(k) }") && twin.contains("PageBars(keysOf(p, live), 6.dp) { k -> key(k) }"))
        assertFalse(twin.contains("KeyRow("))
        val h2 = java.io.File("src/main/java/com/shelf/launcher/ui/WinScenesHomes2.kt").readText()
        val card = h2.substring(h2.indexOf("internal fun DiscoverCard("), h2.indexOf("internal fun ChipSheet("))
        assertTrue(card.contains("PageBars(sceneKeys(ctx), 6.dp)") && card.contains("wide = true"))
    }

    @Test fun neverMoreThanThreeToARow() = assertTrue(pageBarRows(listOf(10, 10, 10, 10, 10), 900, 6).all { it.size <= 3 })

    @Test fun theShelfFamilyPagesUseTheBars() {
        val src = java.io.File("src/main/java/com/shelf/launcher/ui/WinSceneShelfPages.kt").readText()
        assertTrue(src.contains("PageBars(keysOf(p, live), 6.dp)"))
        assertFalse(src.contains("KeyRow(keysOf(p, live)"))
        // each caller's page key is a bar (SceneKey wide) in the window's own make
        val fam = java.io.File("src/main/java/com/shelf/launcher/ui/WinSceneFamilies.kt").readText()
        val sty = java.io.File("src/main/java/com/shelf/launcher/ui/WinScenesStyled.kt").readText()
        assertEquals(4, Regex("ShelfFamilyPages\\(").findAll(fam + sty).count())
        assertTrue(Regex("ShelfFamilyPages\\(ctx, Modifier, a, ink, Color.White.copy\\(alpha = 0.05f\\), pageKey, nameOnCard = false,").containsMatchIn(sty))
        for (m in Regex("ShelfFamilyPages\\(ctx, [^\\n]*").findAll(fam + sty)) {
            val after = (fam + sty).substring(m.range.first).take(700)
            assertTrue("bars for ${m.value}", after.contains("wide = true") || m.value.contains("pageKey"))
        }
        assertTrue(Regex("val pageKey[^}]*wide = true").containsMatchIn(sty))
    }
}
