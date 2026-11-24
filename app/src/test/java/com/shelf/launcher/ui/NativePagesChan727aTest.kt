package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 727a (class C): the Channel grid window draws the channel's own screens in its preview well - not the shared page bodies. */
class NativePagesChan727aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun channelHasItsOwnScreens() {
        assertEquals(WinScene.WII_PREVIEW, winSceneFor(HomeLayout.CHAN))
        assertTrue(nativePages(WinScene.WII_PREVIEW))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.WII_PREVIEW, p))
        val scene = src("WinScenesWii.kt")
        assertFalse("no shared page body left in the preview", scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("ChanPage(ctx, pg, pageLive, pal, LocalWiiRim.current, Modifier.fillMaxSize())"))
        assertTrue(scene.contains("move = ::chanMove") && scene.contains(".nativeBumpers(ctx)"))
        // the More page of big keys stays the Info page's own
        assertTrue(scene.contains("WiiMorePage(small, pal, compact, Modifier.fillMaxSize())"))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesChan.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun screenNames() {
        assertEquals("Channel", chanPageName(WinPage.MAIN))
        assertEquals("Channel Info", chanPageName(WinPage.DETAILS))
        assertEquals("Stamps", chanPageName(WinPage.TROPHIES))
        assertEquals("Settings", chanPageName(WinPage.OPTIONS))
    }

    @Test fun stampsAndPager() {
        assertEquals("3 of 12 stamps", chanStampCount(3, 12)); assertEquals("0 of 1 stamp", chanStampCount(0, 1))
        assertEquals("25 pts", chanPoints(25)); assertEquals("1 pt", chanPoints(1)); assertEquals("—", chanPoints(null))
        assertEquals(listOf(0, 1, 2, 3), NATIVE_TAB_ORDER.map { chanDot(NATIVE_TAB_ORDER, it) })
        assertEquals(-1, chanDot(listOf(WinPage.MAIN, WinPage.DETAILS), WinPage.OPTIONS))
    }

    @Test fun noTruncationInTheScreens() {
        val c = src("WinPagesChan.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
    }
}
