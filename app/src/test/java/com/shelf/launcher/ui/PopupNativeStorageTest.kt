package com.shelf.launcher.ui
import androidx.compose.material.icons.filled.PlayArrow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Popup motion work, Storage boxes: native pages, the B focus contract, L1 / R1 order (WinPageFlow.kt, WinScenes.kt). */
class PopupNativeStorageTest {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name")
    ).first { it.exists() }.readText()

    @Test fun backOnTheFirstPageGoesToTheOpenerNeverPlay() {
        assertEquals(PageFocus.OPENER, pageFocusTarget(WinPage.MAIN, fromPerf = false, hasOpener = true))
        // Performance keeps its own way back
        assertEquals(PageFocus.PERF, pageFocusTarget(WinPage.MAIN, fromPerf = true, hasOpener = true))
        // opened with no opener (the window's first open): Play
        assertEquals(PageFocus.PLAY, pageFocusTarget(WinPage.MAIN, fromPerf = false, hasOpener = false))
        assertEquals(PageFocus.LIST, pageFocusTarget(WinPage.TROPHIES, fromPerf = false, hasOpener = true))
        assertEquals(PageFocus.LIST, pageFocusTarget(WinPage.OPTIONS, fromPerf = false, hasOpener = false))
        assertEquals(PageFocus.PLAY, pageFocusTarget(WinPage.DETAILS, fromPerf = false, hasOpener = true))
    }

    @Test fun theOpenerIsNotPrecededByPlay() {
        val gw = src("GameWindow.kt")
        // the old order: Play asked at 60 ms, the opener only at 120 ms (Play lit in between; a quick A started the game)
        assertFalse(gw.contains("kotlinx.coroutines.delay(120)\n        runCatching { f.requestFocus() }"))
        val effect = gw.substringAfter("PageFocus.OPENER -> {").substringBefore("PageFocus.PLAY ->")
        assertFalse("the opener branch must not ask for Play first", effect.substringBefore("focusWhenAttached(opener").contains("playFocus"))
    }

    @Test fun storageKeepsItsOwnPages() {
        assertTrue(nativePages(WinScene.STORAGE_RECORD))
        assertFalse(nativePages(WinScene.CASE_MEDIA))
        assertFalse(nativePages(null))
        val gw = src("GameWindow.kt")
        assertTrue(gw.contains("scene != WinScene.CROSS_OPTIONS && !nativePages(scene)) SceneExpandedPage"))
        val st = src("WinScenes.kt").substringAfter("private fun StorageRecord(").substringBefore("internal val STORAGE_TABS")
        assertTrue("Storage draws every page inside its record", st.contains("StorageRecordPage(ctx, p, red, live)"))
        assertFalse(st.contains("SceneExpandedPage"))
    }

    @Test fun l1R1WalkTheTabsBothWays() {
        val t = STORAGE_TABS
        assertEquals(WinPage.DETAILS, pageStep(t, WinPage.MAIN, 1))
        assertEquals(WinPage.OPTIONS, pageStep(t, WinPage.MAIN, -1))
        assertEquals(WinPage.MAIN, pageStep(t, WinPage.OPTIONS, 1))
        assertEquals(WinPage.TROPHIES, pageStep(t, WinPage.OPTIONS, -1))
        // a page that isn't a tab (Performance) steps from the first tab
        assertEquals(WinPage.DETAILS, pageStep(t, WinPage.PERF, 1))
        assertEquals(1, pageDirection(t, WinPage.MAIN, WinPage.TROPHIES))
        assertEquals(-1, pageDirection(t, WinPage.OPTIONS, WinPage.DETAILS))
        assertEquals(-1, pageDirection(t, WinPage.DETAILS, WinPage.MAIN))
    }

    @Test fun tabsOpenThroughTheirButtonsSoBReturnsThere() {
        val gw = src("GameWindow.kt")
        // a tab opens as its button does (opener recorded); a page turned to from another page keeps the first opener
        assertTrue(gw.contains("fun opener(f: FocusRequester, act: () -> Unit): () -> Unit = { if (page == WinPage.MAIN) pageOpener[0] = f; act() }"))
        val st = src("WinScenes.kt").substringAfter("private fun StorageRecord(").substringBefore("internal val STORAGE_TABS")
        assertTrue(st.contains("ctx.open(t)"))
    }

    @Test fun theLeavingSheetHoldsNoFocus() {
        val a = WinAction("Play", androidx.compose.material.icons.Icons.Filled.PlayArrow, {}, focus = androidx.compose.ui.focus.FocusRequester(), primary = true)
        val i = a.inert()
        assertTrue(i.focus == null && i.primary && i.label == "Play")
    }

    @Test fun nightDrawsItsOwnPagesAlongTheSlant() {
        assertTrue(nativePages(WinScene.NIGHT_STATUS))
        assertEquals(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS), NIGHT_TABS)
        val n = src("WinScenes.kt").substringAfter("private fun NightStatus(").substringBefore("internal val NIGHT_TABS")
        assertTrue(n.contains("NightPagePanel(ctx, p, live"))                     // the page in the menu's own big slanted panel
        assertTrue(n.contains("Key.ButtonL1 -> -1; Key.ButtonR1 -> 1"))           // L1 / R1 walk the pages
        assertTrue(n.contains("ctx.open(to)"))                                     // ... through their commands (B returns there)
        assertTrue(n.contains("kotlin.math.tan(slant)"))                           // panels move along the slant
        assertFalse(n.contains("ctx.pageBody("))                                   // no page squeezed into the stats panel
        // the banner is drawn after the panels (a panel sweeping up the slant passes under it)
        assertTrue(n.indexOf("NightBannerWipe(") > n.indexOf("AnimatedContent("))
        val wipe = src("WinScenes.kt").substringAfter("private fun NightBannerWipe(").substringBefore("private fun NightPagePanel(")
        assertTrue(wipe.contains("drawRect(Color(0xFF0B1424)"))                    // the dark band at the wipe's edge
        assertTrue(wipe.contains("if (!anim) { shown = page"))                     // animations off: the new words at once
    }

    @Test fun tilesTurnTheirCardForEachPage() {
        assertTrue(nativePages(WinScene.TILE_CARD))
        val t = src("WinSceneFamilies.kt").substringAfter("internal fun TileLiftCard(").substringBefore("internal val TILE_TABS")
        assertTrue(t.contains("rotationY = turn"))                                   // the writing turns like a tile
        assertTrue(t.contains("-90f * turnDir[0]") && t.contains("90f * turnDir[0]")) // forward and back turn opposite ways
        assertTrue(t.contains("TileLiftPage(ctx, p, live"))
        assertTrue(t.contains("Key.ButtonL1 -> -1; Key.ButtonR1 -> 1") && t.contains("ctx.open(to)"))
        assertTrue(t.contains("if (!anim) snap()"))                                  // animations off: no turn
    }

    @Test fun bladesLayerAPageBladeAndKeepTheEdgeReadable() {
        assertTrue(nativePages(WinScene.BLADE_GUIDE))
        val b = src("WinScenesConsole.kt").substringAfter("internal fun BladeGuide(").substringBefore("internal val BLADE_TABS")
        // forward: the new blade slides in on top; back: the blade you were on slides away on top of the one under it
        assertTrue(b.contains("apply { targetContentZIndex = 1f }") && b.contains("apply { targetContentZIndex = -1f }"))
        assertTrue(b.contains("BladePageBlade(ctx, pg, pg == ctx.page"))
        assertTrue(b.contains("BladeEdgeName(ctx.name"))                              // the covered game blade keeps its name
        assertFalse(b.contains("ctx.pageBody("))
        // no state written while composing (the first page's commands are kept in a plain holder)
        assertFalse(b.contains("mainRows.value ="))
        val edge = src("WinScenesConsole.kt").substringAfter("private fun BladeEdgeName(").substringBefore("private fun BladePageBlade(")
        assertTrue(edge.contains("maxLines = 2"))                                     // two lines across the strip, never cut
        assertEquals(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS), BLADE_TABS)
    }
}
