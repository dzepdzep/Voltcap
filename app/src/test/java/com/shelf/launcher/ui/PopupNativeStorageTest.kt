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
        assertFalse(nativePages(WinScene.NIGHT_STATUS))
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
}
