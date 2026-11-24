package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 738a (class C): Panel dashboard's swung-round panel draws its own pages (OVERVIEW / ACHIEVEMENTS / MANAGE) - not the shared page bodies. */
class NativePagesPanels738aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun panel(): String { val f = src("WinScenesConsole.kt"); return f.substring(f.indexOf("internal fun PanelDetail("), f.indexOf("internal fun MagazinePage(")) }

    @Test fun panelsHaveTheirOwnPages() {
        assertEquals(WinScene.PANEL_DETAIL, winSceneFor(HomeLayout.PANELS))
        assertTrue(nativePages(WinScene.PANEL_DETAIL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.PANEL_DETAIL, p))
        val c = panel()
        assertTrue(c.contains("PanelPageArea(ctx, PanelInk(ink, p.accent, p.panel), Modifier.fillMaxSize(), compact)"))
        assertFalse("no shared page body left in the panel", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play stays the first command down the left on every page (the tools only on the first page)
        assertTrue(c.contains("ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }"))
        // the page's name is said once - by the page area's heading, not the art strip too
        assertFalse(c.contains("winPageName(ctx.page)"))
        val pages = src("WinPagesPanels.kt")
        assertTrue(pages.contains("PanelPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, compact)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
    }

    @Test fun pageNamesAndAchievementWords() {
        assertEquals(listOf("Game", "Overview", "Achievements", "Manage"), NATIVE_TAB_ORDER.map(::panelPageName))
        assertEquals("3 / 12", panelAwardCount(3, 12)); assertEquals("12 / 12", panelAwardCount(15, 12))
        assertEquals("0 / 0", panelAwardCount(2, 0)); assertEquals("0 / 0", panelAwardCount(-1, -3))
        assertEquals("Unlocked 2026-11-01", panelAwardLine(true, " 2026-11-01 "))
        assertEquals("Unlocked", panelAwardLine(true, null)); assertEquals("Unlocked", panelAwardLine(true, " "))
        assertEquals("Locked", panelAwardLine(false, "2026-11-01"))
    }

    @Test fun meterLightsSegmentsHonestly() {
        assertEquals(0, panelMeterLit(0, 0, 10)); assertEquals(0, panelMeterLit(0, 50, 10))
        assertEquals(1, panelMeterLit(1, 50, 10)) // one earned always shows
        assertEquals(9, panelMeterLit(49, 50, 10)) // never full until all are earned
        assertEquals(10, panelMeterLit(50, 50, 10)); assertEquals(10, panelMeterLit(70, 50, 10))
        assertEquals(5, panelMeterLit(25, 50, 10)); assertEquals(0, panelMeterLit(-3, 50, 10))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesPanels.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
