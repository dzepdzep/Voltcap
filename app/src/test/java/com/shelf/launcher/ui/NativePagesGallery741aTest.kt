package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 741a (class C): the Art library's gallery wall hangs its own pages (Catalogue / Collection / Curator) on its mat - not the shared page bodies. */
class NativePagesGallery741aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun wall(): String { val f = src("WinScenesMore.kt"); val a = f.indexOf("internal fun GalleryPlacard("); return f.substring(a, f.indexOf("\n}\n", a)) }

    @Test fun theWallHasItsOwnPages() {
        assertEquals(WinScene.GALLERY_PLACARD, winSceneFor(HomeLayout.ARTLIB))
        assertTrue(nativePages(WinScene.GALLERY_PLACARD))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.GALLERY_PLACARD, p))
        val c = wall()
        assertTrue(c.contains("GalleryPageArea(ctx, GalleryInk(lit, ink), Modifier.fillMaxSize(), short)"))
        assertFalse("no shared page body left on the mat", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play first with the page slots on every page, so it stays put
        assertTrue(c.contains("KeyRow(sceneKeys(ctx), slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS)"))
        val pages = src("WinPagesGallery.kt")
        assertTrue(pages.contains("GalleryPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("move = ::galleryMove"))
    }

    @Test fun pageNamesRoomsAndPlacardWords() {
        assertEquals(listOf("The work", "Catalogue", "Collection", "Curator"), NATIVE_TAB_ORDER.map(::galleryPageName))
        assertEquals("Room 3 of 4", galleryRoom(NATIVE_TAB_ORDER, WinPage.TROPHIES))
        assertEquals("Room 2 of 2", galleryRoom(listOf(WinPage.MAIN, WinPage.OPTIONS), WinPage.OPTIONS))
        assertEquals("", galleryRoom(NATIVE_TAB_ORDER, WinPage.PERF)); assertEquals("", galleryRoom(emptyList(), WinPage.MAIN))
        assertEquals("3 of 12 acquired", galleryAwardCount(3, 12)); assertEquals("12 of 12 acquired", galleryAwardCount(15, 12))
        assertEquals("0 of 0 acquired", galleryAwardCount(2, 0)); assertEquals("0 of 0 acquired", galleryAwardCount(-1, -3))
        assertEquals("Acquired 2026-11-01", galleryAwardLine(true, " 2026-11-01 "))
        assertEquals("Acquired", galleryAwardLine(true, null)); assertEquals("Acquired", galleryAwardLine(true, " "))
        assertEquals("Not yet acquired", galleryAwardLine(false, "2026-11-01"))
    }

    @Test fun theHangIsHonest() {
        assertEquals(0, galleryHung(0, 0, 10)); assertEquals(0, galleryHung(0, 50, 10))
        assertEquals(1, galleryHung(1, 50, 10)) // one acquired always fills one frame
        assertEquals(9, galleryHung(49, 50, 10)) // never full until all are acquired
        assertEquals(10, galleryHung(50, 50, 10)); assertEquals(10, galleryHung(70, 50, 10))
        assertEquals(5, galleryHung(25, 50, 10)); assertEquals(0, galleryHung(-3, 50, 10)); assertEquals(0, galleryHung(3, 5, 0))
    }

    @Test fun noTruncationNoIntrinsics() {
        val c = src("WinPagesGallery.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
