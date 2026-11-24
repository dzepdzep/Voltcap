package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 740a (class C): Green core's glowing console reads out its own pages (SPEC / UNLOCKS / CONTROL) - not the shared page bodies. */
class NativePagesCore740aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun panel(): String { val f = src("WinScenesMore.kt"); return f.substring(f.indexOf("internal fun CorePanel("), f.indexOf("private val CutCornerShapeCompat")) }

    @Test fun theConsoleHasItsOwnPages() {
        assertEquals(WinScene.CORE_PANEL, winSceneFor(HomeLayout.GREENCORE))
        assertTrue(nativePages(WinScene.CORE_PANEL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CORE_PANEL, p))
        val c = panel()
        assertTrue(c.contains("CorePageArea(ctx, CoreInk(glow, ink, dim), Modifier.weight(1f).fillMaxWidth(), short)"))
        assertTrue("the readout only on the Title page", c.contains("if (ctx.page == WinPage.MAIN) Row(Modifier.weight(1f, fill = false))"))
        assertFalse("no shared page body left on the console", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play first with the page slots on every page, so it stays put
        assertTrue(c.contains("KeyRow(sceneKeys(ctx), slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS)"))
        val pages = src("WinPagesCore.kt")
        assertTrue(pages.contains("CorePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("move = ::coreMove"))
    }

    @Test fun pageNamesAndReadoutWords() {
        assertEquals(listOf("Title", "Spec", "Unlocks", "Control"), NATIVE_TAB_ORDER.map(::corePageName))
        assertEquals("3 / 12", coreAwardCount(3, 12)); assertEquals("12 / 12", coreAwardCount(15, 12))
        assertEquals("0 / 0", coreAwardCount(2, 0)); assertEquals("0 / 0", coreAwardCount(-1, -3))
        assertEquals("UNLOCKED 2026-11-01", coreAwardLine(true, " 2026-11-01 "))
        assertEquals("UNLOCKED", coreAwardLine(true, null)); assertEquals("UNLOCKED", coreAwardLine(true, " "))
        assertEquals("LOCKED", coreAwardLine(false, "2026-11-01"))
    }

    @Test fun chargeBarIsHonest() {
        assertEquals(0, coreCharge(0, 0, 12)); assertEquals(0, coreCharge(0, 50, 12))
        assertEquals(1, coreCharge(1, 50, 12)) // one earned always charges one cell
        assertEquals(11, coreCharge(49, 50, 12)) // never full until all are earned
        assertEquals(12, coreCharge(50, 50, 12)); assertEquals(12, coreCharge(70, 50, 12))
        assertEquals(6, coreCharge(25, 50, 12)); assertEquals(0, coreCharge(-3, 50, 12)); assertEquals(0, coreCharge(3, 5, 0))
    }

    @Test fun noTruncationNoIntrinsics() {
        val c = src("WinPagesCore.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
