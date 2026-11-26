package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 737a (class C): Pocket camera's cartridge menu draws its own pages (DATA / ALBUM / SETUP) - not the shared page bodies. */
class NativePagesCamera737aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun menu(): String { val f = src("WinScenesConsole.kt"); return f.substring(f.indexOf("internal fun CameraMenu("), f.indexOf("internal fun PanelDetail(")) }

    @Test fun cameraHasTheMenusOwnPages() {
        assertEquals(WinScene.CAMERA_MENU, winSceneFor(HomeLayout.CAMERA))
        assertTrue(nativePages(WinScene.CAMERA_MENU))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CAMERA_MENU, p))
        val c = menu()
        assertTrue(c.contains("CameraPageArea(ctx, CameraInk(c0, c1, c2, c3, font, u, fs), Modifier.weight(1f).fillMaxWidth())"))
        assertFalse("no shared page body left in the menu", c.contains("ctx.pageBody("))
        // L1 / R1 on the window's root
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // the keys stay one uniform column under the page on every page, so Play stays put (PlayPlace)
        assertTrue(c.contains("UniformKeyColumn(ctx.buttons, 0.dp, fill = true, slots = PAGE_SLOTS)"))
        val pages = src("WinPagesCamera.kt")
        // the heading names the page after the old one has gone (rememberHeadingPage)
        assertTrue(pages.contains("CameraPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k)"))
        // only Performance falls back to the shared page
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        // a removing setting asks first
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        // the scan reveal follows the window's own stepped switch-on, and none with Motion Off
        assertTrue(pages.contains("cameraStep(scan)")); assertTrue(pages.contains("if (anim) tween(NATIVE_PAGE_MS) else snap()"))
    }

    @Test fun pageNamesAndAlbumWords() {
        assertEquals(listOf("GAME", "DATA", "ALBUM", "SETUP"), NATIVE_TAB_ORDER.map(::cameraPageName))
        assertEquals("03/12", cameraAlbumCount(3, 12)); assertEquals("12/12", cameraAlbumCount(15, 12))
        assertEquals("00/00", cameraAlbumCount(2, 0)); assertEquals("00/00", cameraAlbumCount(-1, -3))
        assertEquals("120/140", cameraAlbumCount(120, 140))
        assertEquals("NO.01", cameraEntryNo(0)); assertEquals("NO.12", cameraEntryNo(11)); assertEquals("NO.01", cameraEntryNo(-2))
        assertEquals("GOT 2026-11-01", cameraAwardLine(true, " 2026-11-01 "))
        assertEquals("GOT", cameraAwardLine(true, null)); assertEquals("GOT", cameraAwardLine(true, " "))
        assertEquals("NOT YET", cameraAwardLine(false, "2026-11-01"))
    }

    @Test fun meterCellsNeverLie() {
        assertEquals(0, cameraMeterCells(0, 0, 10)); assertEquals(0, cameraMeterCells(0, 12, 10))
        assertEquals(1, cameraMeterCells(1, 140, 10)) // one earned always shows
        assertEquals(9, cameraMeterCells(139, 140, 10)) // full only when everything is earned
        assertEquals(10, cameraMeterCells(140, 140, 10)); assertEquals(10, cameraMeterCells(200, 140, 10))
        assertEquals(5, cameraMeterCells(5, 10, 10)); assertEquals(0, cameraMeterCells(3, 10, 0))
        assertEquals(1, cameraMeterCells(1, 2, 2))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesCamera.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
