package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 742a (class C): the Cross menu reads out its own pages (Information / Trophies / Options) on the left - not the shared page bodies. */
class NativePagesCross742aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun menu(): String { val f = src("WinScenesHomes2.kt"); val a = f.indexOf("internal fun CrossOptions("); return f.substring(a, f.indexOf("\n}\n", a)) }

    @Test fun theMenuHasItsOwnPages() {
        assertEquals(WinScene.CROSS_OPTIONS, winSceneFor(HomeLayout.CROSS))
        assertTrue(nativePages(WinScene.CROSS_OPTIONS))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CROSS_OPTIONS, p))
        val c = menu()
        assertTrue(c.contains("CrossPageArea(ctx, CrossInk(band), Modifier.fillMaxWidth().weight(1f), short, narrow = ctx.maxW - w - 16.dp < 300.dp)"))
        assertFalse("no shared page body left on the menu", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play first with the page slots on every page, so it stays put
        assertTrue(c.contains("slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS, scrollKey = ctx.page)"))
        // and the pages' keys start where the first page's did (the first page stays centred)
        assertTrue(c.contains("crossKeysTop.value = with(density) { c.positionInParent().y.coerceAtLeast(0f).toDp() }"))
        assertTrue(c.contains("else Modifier.padding(top = keysTop)"))
        assertTrue(src("WinScenesHomes2.kt").contains("UniformKeyColumn(actions, 4.dp, fill = true, slots = slots, key = key)"))
        val pages = src("WinPagesCross.kt")
        assertTrue(pages.contains("CrossPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("move = ::crossMove"))
    }

    @Test fun pageNamesAndTrophyWords() {
        assertEquals(listOf("Game", "Information", "Trophies", "Options"), NATIVE_TAB_ORDER.map(::crossPageName))
        assertEquals("3 / 12", crossAwardCount(3, 12)); assertEquals("12 / 12", crossAwardCount(15, 12))
        assertEquals("0 / 0", crossAwardCount(2, 0)); assertEquals("0 / 0", crossAwardCount(-1, -3))
        assertEquals("Earned 2026-11-01", crossAwardLine(true, " 2026-11-01 "))
        assertEquals("Earned", crossAwardLine(true, null)); assertEquals("Earned", crossAwardLine(true, " "))
        assertEquals("Not earned", crossAwardLine(false, "2026-11-01"))
    }

    @Test fun thePercentIsHonest() {
        assertEquals(0, crossPercent(0, 0)); assertEquals(0, crossPercent(0, 500)); assertEquals(0, crossPercent(-2, 50))
        assertEquals(1, crossPercent(1, 500)) // one earned always shows
        assertEquals(99, crossPercent(499, 500)) // never full until all are earned
        assertEquals(100, crossPercent(500, 500)); assertEquals(100, crossPercent(700, 500))
        assertEquals(50, crossPercent(25, 50)); assertEquals(0, crossPercent(3, -1))
    }

    @Test fun noTruncationNoIntrinsics() {
        val c = src("WinPagesCross.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
