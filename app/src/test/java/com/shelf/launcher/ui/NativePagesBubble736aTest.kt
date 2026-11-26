package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 736a (class C): Bubble home's LiveArea card draws its own pages (INFO / AWARDS / SETTINGS) - not the shared page bodies. */
class NativePagesBubble736aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun card(): String { val f = src("WinScenesConsole.kt"); return f.substring(f.indexOf("internal fun BubbleCard("), f.indexOf("internal fun CameraMenu(")) }

    @Test fun bubbleHasTheCardsOwnPages() {
        assertEquals(WinScene.BUBBLE_CARD, winSceneFor(HomeLayout.BUBBLES))
        assertTrue(nativePages(WinScene.BUBBLE_CARD))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.BUBBLE_CARD, p))
        val c = card()
        assertTrue(c.contains("BubblePageArea(ctx, blue, Modifier.fillMaxSize(), compact)"))
        assertFalse("no shared page body left in the card", c.contains("ctx.pageBody("))
        // L1 / R1 on the window's root
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play stays the first key down the left on every page (the tools only on the first page, after the other buttons)
        assertTrue(c.contains("ctx.buttons.filter { !it.back } + (if (ctx.page == WinPage.MAIN) ctx.tools else emptyList()) + ctx.buttons.filter { it.back }"))
        val pages = src("WinPagesBubble.kt")
        // the heading names the page after the old one has gone (rememberHeadingPage)
        assertTrue(pages.contains("BubblePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, blue, compact)"))
        // only Performance falls back to the shared page
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        // a removing setting asks first
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
    }

    @Test fun pageNamesAndAwardWords() {
        assertEquals(listOf("Game", "Info", "Awards", "Settings"), NATIVE_TAB_ORDER.map(::bubblePageName))
        assertEquals("3 / 12", bubbleAwardCount(3, 12)); assertEquals("12 / 12", bubbleAwardCount(15, 12))
        assertEquals("0 / 0", bubbleAwardCount(2, 0)); assertEquals("0 / 0", bubbleAwardCount(-1, -3))
        assertEquals("Earned 2026-11-01", bubbleAwardLine(true, " 2026-11-01 "))
        assertEquals("Earned", bubbleAwardLine(true, null)); assertEquals("Earned", bubbleAwardLine(true, " "))
        assertEquals("Not yet", bubbleAwardLine(false, "2026-11-01"))
    }

    @Test fun meterFillsInProportion() {
        assertEquals(0f, bubbleMeter(0, 0), 0f); assertEquals(0.5f, bubbleMeter(5, 10), 1e-6f); assertEquals(1f, bubbleMeter(20, 10), 0f)
        assertEquals(0f, bubbleMeter(-4, 12), 0f)
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesBubble.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
