package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 batch 1 (681a): the native page kit and Record crate's own pages. */
class NativePages681aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun tabsFollowTheWindowsPages() {
        assertEquals(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.OPTIONS), nativeTabs(setOf(WinPage.OPTIONS, WinPage.MAIN, WinPage.DETAILS)))
        assertEquals(NATIVE_TAB_ORDER, nativeTabs(WinPage.values().toSet()))
        assertTrue(NATIVE_ROW_MIN >= 24)
    }

    @Test fun crateNamesItsPrintedMatter() {
        assertEquals("SIDE A", cratePageName(WinPage.MAIN))
        assertEquals("LINER NOTES", cratePageName(WinPage.DETAILS))
        assertEquals("TRACKLIST", cratePageName(WinPage.TROPHIES))
        assertEquals("PRESSING NOTES", cratePageName(WinPage.OPTIONS))
        assertEquals("FORMAT", crateCreditName("Console")); assertEquals("LABEL", crateCreditName("Made by"))
        assertEquals("A1", crateTrackNo(true, 0)); assertEquals("B3", crateTrackNo(false, 2))
    }

    @Test fun crateDrawsItsOwnPagesNotTheSharedOnes() {
        assertTrue(nativePages(WinScene.RECORD_SLEEVE))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.RECORD_SLEEVE, p))
        val s = src("WinScenesMore.kt")
        val body = s.substring(s.indexOf("internal fun RecordSleeve("), s.indexOf("internal fun crateKeysVertical("))
        assertFalse(body.contains("ctx.pageBody("))
        assertTrue(body.contains("CratePage(ctx, pg, pageLive, paper, ink, red, Modifier.fillMaxSize())"))
        // not the shared page bodies in the sleeve's colours: the crate's pages are its own (only Performance falls back)
        val c = src("WinPagesCrate.kt")
        assertEquals(1, c.split("ctx.pageFor(").size - 1)
    }
}
