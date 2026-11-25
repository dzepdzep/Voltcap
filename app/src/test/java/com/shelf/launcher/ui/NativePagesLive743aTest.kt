package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 743a (class C): the Live tiles panel has its own pages (about / achievements / options under a pivot) - not the shared ones. */
class NativePagesLive743aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun panel(): String { val f = src("WinScenesHomes2.kt"); val a = f.indexOf("internal fun LiveTilesPanel("); return f.substring(a, f.indexOf("\n}\n", a)) }

    @Test fun thePanelHasItsOwnPages() {
        assertEquals(WinScene.LIVE_PANEL, winSceneFor(HomeLayout.LIVE))
        assertTrue(nativePages(WinScene.LIVE_PANEL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.LIVE_PANEL, p))
        val c = panel()
        assertTrue(c.contains("else LivePageArea(ctx, LiveInk(a), Modifier.fillMaxWidth().weight(1f), short)"))
        assertFalse("no shared page body on the panel", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play first with the first page's slots on every page, so it stays put
        assertTrue(c.contains("KeyRow(sceneKeys(ctx), 4.dp, slots = sceneKeySlots(ctx))"))
        // and the panel grows upward only on its pages (More closed measured), so Play stays where the first page had it
        assertTrue(c.contains("pageH != null -> Modifier.height(pageH).offset(y = -(pageH - mainH.value!!) / 2)"))
        assertTrue(c.contains("onSizeChanged { if (moreShut) mainH.value"))
        val pages = src("WinPagesLive.kt")
        assertTrue(pages.contains("LivePivot(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("move = ::liveMove"))
        assertTrue("the turnstile turns flat text (D285)", pages.contains("flatTextFor3d()"))
    }

    @Test fun pageNamesAndPivot() {
        assertEquals(listOf("game", "about", "achievements", "options"), NATIVE_TAB_ORDER.map(::livePageName))
        val t = NATIVE_TAB_ORDER
        assertEquals(t, livePivot(t, WinPage.MAIN))
        assertEquals(listOf(WinPage.TROPHIES, WinPage.OPTIONS, WinPage.MAIN, WinPage.DETAILS), livePivot(t, WinPage.TROPHIES))
        assertEquals(listOf(WinPage.OPTIONS, WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES), livePivot(t, WinPage.OPTIONS))
        assertEquals(t, livePivot(t, WinPage.PERF)) // not a tab: as given
        assertEquals(emptyList<WinPage>(), livePivot(emptyList(), WinPage.DETAILS))
        for (p in t) assertEquals(t.toSet(), livePivot(t, p).toSet())
    }

    @Test fun achievementWordsAndPercent() {
        assertEquals("3 of 12", liveAwardCount(3, 12)); assertEquals("12 of 12", liveAwardCount(15, 12))
        assertEquals("0 of 0", liveAwardCount(2, 0)); assertEquals("0 of 0", liveAwardCount(-1, -3))
        assertEquals("earned 2026-11-01", liveAwardLine(true, " 2026-11-01 "))
        assertEquals("earned", liveAwardLine(true, null)); assertEquals("earned", liveAwardLine(true, " "))
        assertEquals("not yet", liveAwardLine(false, "2026-11-01"))
        assertEquals(0, livePercent(0, 0)); assertEquals(0, livePercent(0, 500)); assertEquals(0, livePercent(-2, 50))
        assertEquals(1, livePercent(1, 500)) // one earned always shows
        assertEquals(99, livePercent(499, 500)) // never full until all are earned
        assertEquals(100, livePercent(500, 500)); assertEquals(100, livePercent(700, 500))
        assertEquals(50, livePercent(25, 50)); assertEquals(0, livePercent(3, -1))
    }

    @Test fun pagesGrowUpwardOnlyWithTheTopOnScreen() {
        fun h(m: Int, max: Int) = livePageHeight(androidx.compose.ui.unit.Dp(m.toFloat()), androidx.compose.ui.unit.Dp(max.toFloat())).value
        assertEquals(400f, h(300, 500)) // half the free room: the bottom stays, the top reaches the window's top
        assertEquals(500f, h(500, 500)); assertEquals(500f, h(620, 500)) // never taller than the window
        for (m in 100..500 step 50) { val p = h(m, 500); val top = (500 - p) / 2 - (p - m) / 2
            assertTrue("top $top on screen", top >= -0.01f); assertTrue(p >= m) }
    }

    @Test fun noTruncationNoIntrinsics() {
        val c = src("WinPagesLive.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
