package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 675r: the RP Nova x1.3 popup gate n675d (640 x 480 dp at x1.3) - Record crate, Pocket ribbon, Storage boxes. */
class Batch675rTest {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun crateMenuCountsItsStatusLine() {
        // Nova x1.3, More open: ~185 dp under the rule. Without the status line counted, three keys left the track list less
        // than one row (the empty band); with it, two keys and two whole fact rows.
        assertEquals(3, crateVisibleKeys(region = 185.dp, keyH = 34.dp, gap = 6.dp, facts = 46.dp, n = 7))
        assertEquals(2, crateVisibleKeys(region = 185.dp, keyH = 34.dp, gap = 6.dp, facts = 46.dp + 36.dp, n = 7))
        val s = src("WinScenesMore.kt")
        val body = s.substring(s.indexOf("internal fun RecordSleeve("), s.indexOf("internal fun crateKeysVertical("))
        assertTrue(body.contains("(if (ctx.page == WinPage.MAIN) 46.dp else 0.dp) + statusRoom"))
        // a list that shows no whole row takes no room (no empty band)
        assertTrue(body.contains("Column(Modifier.weight(1f, fill = false).layout { m, c ->"))
        assertTrue(!body.contains("Column(Modifier.weight(1f).layout { m, c ->"))
    }

    @Test fun inlineFactLabelsAreNeverCut() {
        assertEquals(78f, factLabelWidth(widest = 50.dp, row = 400.dp).value, 0.01f)    // short labels: the old 78 dp
        assertEquals(105f, factLabelWidth(widest = 100.dp, row = 400.dp).value, 0.01f)  // Pocket ribbon's CONSOLE / TROPHIES
        assertEquals(135f, factLabelWidth(widest = 200.dp, row = 300.dp).value, 0.01f)  // capped at 45 % - the label wraps
        val s = src("WinScenesMore.kt")
        val body = s.substring(s.indexOf("internal fun ColumnScope.SceneFactsBody("), s.indexOf("internal fun factLabelWidth("))
        assertTrue(!body.contains("softWrap = false, modifier = Modifier.width(78.dp)"))
        assertTrue(body.contains("Modifier.width(labelW)"))
    }

    @Test fun theFlyingCopyHasNoSemantics() {
        val s = src("WinSceneStorageFlight.kt")
        val body = s.substring(s.indexOf("internal fun PickupOverlay("))
        assertTrue(body.substring(0, body.indexOf(".graphicsLayer {")).contains(".clearAndSetSemantics {}"))
    }
    @Test fun aCoverlessSpriteNamesItsGameOnlyWhereTheNameReads() {
        // 675s (n675r13: the 426 x 1 px title was the record header's 22 dp sprite drawing the name plate, not the flying copy)
        assertTrue(!spriteShowsName(21.8.dp))
        assertTrue(spriteShowsName(58.dp))
        val s = src("StyleStorageBoxes.kt")
        val body = s.substring(s.indexOf("internal fun StorageSprite("), s.indexOf("internal fun spriteShowsName("))
        assertTrue(body.contains("if (spriteShowsName(maxWidth)) StyleNamePlate("))
    }
}
