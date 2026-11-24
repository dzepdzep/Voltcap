package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 735a (class C): Airy home's floating panel draws its own pages (ABOUT / STARS / SETTINGS) - not the shared page bodies. */
class NativePagesAiry735aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun airy(): String { val f = src("WinScenes.kt"); return f.substring(f.indexOf("private fun AiryPanel("), f.indexOf("internal fun airyFacts(")) }

    @Test fun airyHasThePanelsOwnPages() {
        assertEquals(WinScene.AIRY_PANEL, winSceneFor(HomeLayout.AIRY))
        assertTrue(nativePages(WinScene.AIRY_PANEL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.AIRY_PANEL, p))
        val panel = airy()
        assertTrue(panel.contains("AiryPageArea(ctx, accent, Modifier.fillMaxSize(), tight)"))
        assertFalse("no shared page body left in the panel", panel.contains("ctx.pageBody("))
        // L1 / R1 on the window's root
        assertTrue(panel.contains("Modifier.size(w, h).nativeBumpers(ctx)"))
        // Play keeps its slot on every page (PlayPlace682ab)
        assertTrue(panel.contains("slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS"))
        val pages = src("WinPagesAiry.kt")
        // the heading names the page after the old one has gone (rememberHeadingPage)
        assertTrue(pages.contains("AiryPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, accent, tight)"))
        // only Performance falls back to the shared page
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        // a removing setting asks first
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
    }

    @Test fun pageNamesAndStarWords() {
        assertEquals(listOf("Game", "About", "Stars", "Settings"), NATIVE_TAB_ORDER.map(::airyPageName))
        assertEquals("★ 3 of 12", airyStarCount(3, 12)); assertEquals("★ 12 of 12", airyStarCount(15, 12))
        assertEquals("★ 0 of 0", airyStarCount(2, 0)); assertEquals("★ 0 of 0", airyStarCount(-1, -3))
        assertEquals("Got it 2026-11-01", airyStarLine(true, " 2026-11-01 "))
        assertEquals("Got it", airyStarLine(true, null)); assertEquals("Got it", airyStarLine(true, " "))
        assertEquals("Still to get", airyStarLine(false, "2026-11-01"))
    }

    @Test fun bubbleMeterFillsInProportion() {
        assertEquals(0, airyFilledBubbles(0, 0)); assertEquals(5, airyFilledBubbles(5, 10)); assertEquals(10, airyFilledBubbles(20, 10))
        assertEquals(6, airyFilledBubbles(60, 120)); assertEquals(12, airyFilledBubbles(120, 120)); assertEquals(0, airyFilledBubbles(0, 120))
        assertEquals(0, airyFilledBubbles(-4, 12))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesAiry.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
