package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 682az (the 53rd layout): Disc station's tracks play the deck's own pages on its LCD - DISC INFO / PROGRAM / SETUP - not the
 *  shared page bodies; Play stays put (on the tracks the panel grows upward only, livePageHeight). */
class NativePagesDisc682azTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun panel(): String { val f = src("WinScenesMore.kt"); return f.substring(f.indexOf("internal fun DiscPlayerPanel("), f.indexOf("internal fun discPanelGeometry")) }

    @Test fun theDeckHasItsOwnPages() {
        assertEquals(WinScene.DISC_PANEL, winSceneFor(HomeLayout.DISCS))
        assertTrue(nativePages(WinScene.DISC_PANEL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.DISC_PANEL, p))
        val c = panel()
        assertTrue(c.contains("DiscPage(ctx, p, live, discInk, Modifier.fillMaxSize())"))
        assertTrue(c.contains("DiscCalendar(tracks, p, discInk)"))
        assertFalse("no shared page body left on the deck's LCD", c.contains("ctx.pageFor("))
        assertFalse(c.contains("ctx.pageBody("))
        assertTrue(c.contains("discPageName(p).uppercase()"))
        assertTrue("the rows know which track is live", c.contains("LocalNativeLive provides live"))
        // Play stays put: on the tracks a fixed height grown upward from the Info page's (measured with More closed), as Live tiles
        assertTrue(c.contains("pageH != null -> Modifier.height(pageH).offset(y = -(pageH - mainH.value!!) / 2)"))
        assertTrue(c.contains("onMain -> Modifier.heightIn(max = ctx.maxH).onSizeChanged { if (moreShut) mainH.value"))
        assertTrue(c.contains(".weight(1f, fill = !onMain)"))
        assertTrue("the keys keep the page slots on the tracks", c.contains("KeyRow(sceneKeys(ctx), 8.dp, slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS)"))
        val pages = src("WinPagesDisc.kt")
        assertEquals("only Performance keeps the shared page", 1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("DiscKeyMark(o.destructive"))
    }

    @Test fun pageNamesAndReadoutWords() {
        assertEquals(listOf("Now loaded", "Disc info", "Program", "Setup"), NATIVE_TAB_ORDER.map(::discPageName))
        assertEquals("3 / 12", discAwardCount(3, 12)); assertEquals("12 / 12", discAwardCount(15, 12))
        assertEquals("0 / 0", discAwardCount(2, 0)); assertEquals("0 / 0", discAwardCount(-1, -3))
        assertEquals("PLAYED 2026-11-01", discAwardLine(true, " 2026-11-01 "))
        assertEquals("PLAYED", discAwardLine(true, null)); assertEquals("PLAYED", discAwardLine(true, " "))
        assertEquals("NOT PLAYED", discAwardLine(false, "2026-11-01"))
        assertEquals("01", discTrackNo(0)); assertEquals("12", discTrackNo(11)); assertEquals("120", discTrackNo(119)); assertEquals("01", discTrackNo(-4))
    }

    @Test fun levelMeterIsHonest() {
        assertEquals(0, discLevel(0, 0, 16)); assertEquals(0, discLevel(0, 50, 16))
        assertEquals(1, discLevel(1, 120, 16)) // one earned always lights one bar
        assertEquals(15, discLevel(119, 120, 16)) // never full until all are earned
        assertEquals(16, discLevel(120, 120, 16)); assertEquals(16, discLevel(130, 120, 16))
        assertEquals(8, discLevel(60, 120, 16)); assertEquals(0, discLevel(-3, 50, 16)); assertEquals(0, discLevel(3, 5, 0))
    }

    @Test fun noTruncationNoIntrinsicsNoFontSymbols() {
        val c = src("WinPagesDisc.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
        assertFalse("the eject mark is drawn, not a font glyph", c.contains("⏏"))
    }
}
