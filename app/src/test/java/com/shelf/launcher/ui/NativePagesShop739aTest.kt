package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 739a (class C): Rental shop's rental tag prints its own pages (CATALOGUE CARD / REWARDS CARD / COUNTER) - not the shared page bodies. */
class NativePagesShop739aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun tag(): String { val f = src("WinScenesConsole.kt"); return f.substring(f.indexOf("private fun RentalTagCard("), f.indexOf("private fun tagKeyNeeds(")) }

    @Test fun theTagHasItsOwnPages() {
        assertEquals(WinScene.SHOP_TAG, winSceneFor(HomeLayout.SHOP))
        assertTrue(nativePages(WinScene.SHOP_TAG))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.SHOP_TAG, p))
        val c = tag()
        assertTrue(c.contains("ShopPageArea(ctx, k, Modifier.weight(1f).fillMaxWidth(), short)"))
        assertFalse("no shared page body left on the tag", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play first in one row of two page slots on every page, so it stays put
        assertTrue(c.contains("UniformKeyRow(btns, 4.dp, slots = PAGE_SLOTS) { a -> ShopPageKey(a, k, if (h < 440.dp) 30.dp else 36.dp) }"))
        assertTrue(c.contains("val btns = ctx.buttons.filter { it.primary } + ctx.buttons.filter { !it.primary && !it.back } + ctx.buttons.filter { it.back }"))
        val pages = src("WinPagesShop.kt")
        assertTrue(pages.contains("ShopPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("rentalKeyLabel(a.label, a.primary)")) // "RENT & PLAY", never "RENT & RENT & PLAY"
    }

    @Test fun pageNamesAndCardWords() {
        assertEquals(listOf("Rental tag", "Catalogue card", "Rewards card", "Counter"), NATIVE_TAB_ORDER.map(::shopPageName))
        assertEquals("3 / 12", shopAwardCount(3, 12)); assertEquals("12 / 12", shopAwardCount(15, 12))
        assertEquals("0 / 0", shopAwardCount(2, 0)); assertEquals("0 / 0", shopAwardCount(-1, -3))
        assertEquals("EARNED 2026-11-01", shopAwardLine(true, " 2026-11-01 "))
        assertEquals("EARNED", shopAwardLine(true, null)); assertEquals("EARNED", shopAwardLine(true, " "))
        assertEquals("NOT YET", shopAwardLine(false, "2026-11-01"))
    }

    @Test fun punchCardIsHonest() {
        assertEquals(0, shopPunched(0, 0, 10)); assertEquals(0, shopPunched(0, 50, 10))
        assertEquals(1, shopPunched(1, 50, 10)) // one earned always punches one
        assertEquals(9, shopPunched(49, 50, 10)) // never full until all are earned
        assertEquals(10, shopPunched(50, 50, 10)); assertEquals(10, shopPunched(70, 50, 10))
        assertEquals(5, shopPunched(25, 50, 10)); assertEquals(0, shopPunched(-3, 50, 10))
    }

    @Test fun noTruncationNoIntrinsicsNoMadeUpRentalFacts() {
        val c = src("WinPagesShop.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
        // update 346: only the game's own facts - no due date, rental period or price printed on the tag's pages
        for (w in listOf("\"DUE", "LATE FEE", "NIGHTS", "PRICE")) assertFalse(w, c.contains(w))
    }
}
