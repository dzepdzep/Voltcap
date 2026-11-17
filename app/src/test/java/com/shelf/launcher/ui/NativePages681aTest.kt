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

    @Test fun destructiveRowsAskFirst() {
        val c = NativeConfirm()
        assertFalse(c.press(3, destructive = true)); assertEquals(3, c.armed)   // first press arms
        assertTrue(c.press(3, destructive = true)); assertEquals(null, c.armed) // second press does it
        assertFalse(c.press(4, destructive = true)); c.leave(4); assertEquals(null, c.armed) // moving off disarms
        assertTrue(c.press(1, destructive = false))                              // an ordinary row acts at once
    }

    @Test fun crateInsertIsAHandledSheet() {
        val c = src("WinPagesCrate.kt")
        assertTrue(c.contains("rotationZ = tilt") && c.contains("shadowElevation = 5.dp.toPx()") && c.contains("CRATE_INSERT_PAPER"))
        assertTrue(CRATE_INSERT_PULL_TILT < CRATE_INSERT_REST_TILT && CRATE_INSERT_REST_TILT < 0f)
    }

    @Test fun classicPanelHasItsOwnInstruments() {
        assertTrue(nativePages(WinScene.VOLT_PANEL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.VOLT_PANEL, p))
        assertEquals("CHARGE READOUT", voltPageName(WinPage.DETAILS)); assertEquals("CELLS", voltPageName(WinPage.TROPHIES))
        assertEquals("SWITCHES", voltPageName(WinPage.OPTIONS))
        val v = src("WinPagesVolt.kt")
        assertEquals(1, v.split("ctx.pageFor(").size - 1) // only Performance falls back
        assertTrue(src("WinScenesHomes2.kt").contains("VoltPage(ctx, pg, pageLive, Modifier.fillMaxSize())"))
    }

    @Test fun classicBatteryCells() {
        assertEquals(3 to 10, voltCells(3, 10))
        assertEquals(0 to 0, voltCells(0, 0))
        assertEquals(1 to 40, voltCells(1, 120))   // one earned never shows an empty battery
        assertEquals(39 to 40, voltCells(119, 120)) // one missing never shows a full one
        assertEquals(40 to 40, voltCells(120, 120))
        assertEquals(99, voltCharge(119, 120)); assertEquals(100, voltCharge(5, 5)); assertEquals(0, voltCharge(0, 0))
    }
}
