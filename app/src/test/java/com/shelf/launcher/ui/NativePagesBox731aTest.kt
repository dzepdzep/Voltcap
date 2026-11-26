package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 731a (class C): the Cardboard box's insert sheet draws its own sheets on its paper - not the shared page bodies. */
class NativePagesBox731aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun boxHasTheInsertsOwnSheets() {
        assertEquals(WinScene.BOX_SHEET, winSceneFor(HomeLayout.BOX))
        assertTrue(nativePages(WinScene.BOX_SHEET))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.BOX_SHEET, p))
        val f = src("WinScenes.kt")
        val start = f.indexOf("private fun BoxInsertSheet(")
        val scene = f.substring(start, f.indexOf("\n}\n", start))
        assertTrue(scene.contains("BoxPage(ctx, pg, pageLive, accent, Modifier.fillMaxSize())"))
        assertTrue(scene.contains(".nativeBumpers(ctx)"))
        assertTrue(scene.contains("BoxPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading)"))
        // the sheet's header names the page after the old one has gone (rememberHeadingPage), never the shared page body
        assertTrue(scene.contains("\"INSERT · \" + boxPageName(heading)"))
        assertFalse(scene.contains("ctx.pageBody("))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesBox.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun pageNamesMatchTheButtons() {
        assertEquals(listOf("INVENTORY", "CONTENTS", "CHECKLIST", "SERVICE"), NATIVE_TAB_ORDER.map(::boxPageName))
        // the Details button on the sheet is called CONTENTS (WinLook BOX) and the slip's story hint points at it
        assertTrue(src("GameWindow.kt").contains("details = \"CONTENTS\""))
        assertEquals("CONTENTS", boxPageName(WinPage.DETAILS))
    }

    @Test fun sheetLine() {
        assertEquals("SHEET 2 OF 4", boxSheetLine(NATIVE_TAB_ORDER, WinPage.DETAILS))
        assertEquals("SHEET 1 OF 2", boxSheetLine(listOf(WinPage.MAIN, WinPage.OPTIONS), WinPage.MAIN))
        assertNull(boxSheetLine(NATIVE_TAB_ORDER, WinPage.PERF))
    }

    @Test fun typedWords() {
        assertEquals("01", boxItemNo(0)); assertEquals("10", boxItemNo(9)); assertEquals("100", boxItemNo(99))
        assertEquals("[X]", boxMark(true)); assertEquals("[ ]", boxMark(false))
        assertEquals("CHECKED 12 Mar 2024", boxEntryLine(true, "12 Mar 2024"))
        assertEquals("CHECKED", boxEntryLine(true, null)); assertEquals("CHECKED", boxEntryLine(true, " "))
        assertEquals("NOT YET", boxEntryLine(false, "12 Mar 2024"))
        assertEquals("3 OF 12 CHECKED", boxCheckedLine(3, 12)); assertEquals("0 OF 0 CHECKED", boxCheckedLine(0, 0))
        assertEquals("12 OF 12 CHECKED", boxCheckedLine(15, 12))
        assertEquals("25 PTS", boxPoints(25)); assertEquals("1 PT", boxPoints(1)); assertNull(boxPoints(null))
    }

    @Test fun tallyKeepsToOneRow() {
        assertEquals(12 to 3, mcBlocks(3, 12, BOX_TALLY_MAX))
        assertEquals(32 to 1, mcBlocks(1, 120, BOX_TALLY_MAX)); assertEquals(32 to 32, mcBlocks(120, 120, BOX_TALLY_MAX))
        assertEquals(32 to 31, mcBlocks(119, 120, BOX_TALLY_MAX))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesBox.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
