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

    @Test fun hubPlateHasItsOwnFaces() {
        assertTrue(nativePages(WinScene.HUB_PLATE))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.HUB_PLATE, p))
        assertEquals("SERVICE MEDALS", hubPageName(WinPage.TROPHIES)); assertEquals("SWITCH PANEL", hubPageName(WinPage.OPTIONS))
        assertEquals(1, src("WinPagesHub.kt").split("ctx.pageFor(").size - 1)
        assertTrue(src("WinSceneFamilies.kt").contains("HubPage(ctx, pg, pageLive, Modifier.fillMaxSize())"))
        assertEquals(5 to 0, hubMedalRow(5, 10)); assertEquals(9 to 11, hubMedalRow(20, 10)); assertEquals(0 to 0, hubMedalRow(0, 10))
    }

    @Test fun discoverFeedHasItsOwnPages() {
        assertTrue(nativePages(WinScene.DISCOVER_CARD))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.DISCOVER_CARD, p))
        assertEquals("ABOUT THIS GAME", discoverPageName(WinPage.DETAILS)); assertEquals("PROGRESS", discoverPageName(WinPage.TROPHIES))
        assertEquals(1, src("WinPagesDiscover.kt").split("ctx.pageFor(").size - 1)
        assertTrue(src("WinScenesHomes2.kt").contains("DiscoverPage(ctx, pg, pageLive, Modifier.fillMaxSize())"))
    }

    @Test fun discoverShelvesAreRunsNotRepeats() {
        assertEquals("EARNED", discoverShelf(true, 0)); assertEquals(null, discoverShelf(true, 1))
        assertEquals("NEXT UP", discoverShelf(false, 0)); assertEquals(null, discoverShelf(false, 2))
        assertEquals("STILL TO GET", discoverShelf(false, 3)); assertEquals(null, discoverShelf(false, 4))
    }

    @Test fun tvRoomHasATeletextService() {
        assertEquals(100, ttxPageNo(WinPage.DETAILS)); assertEquals(200, ttxPageNo(WinPage.TROPHIES)); assertEquals(300, ttxPageNo(WinPage.OPTIONS))
        assertEquals(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.OPTIONS), ttxFastext(setOf(WinPage.MAIN, WinPage.DETAILS, WinPage.OPTIONS)).map { it.second })
        assertEquals("█████░░░░░", ttxBar(5, 10, 10)); assertEquals("█░░░░░░░░░", ttxBar(1, 120, 10)); assertEquals("█████████░", ttxBar(119, 120, 10))
        val g = src("GameWindow.kt")
        val body = g.substring(g.indexOf("private fun TvWindowBody("), g.indexOf("private fun TvControls("))
        assertTrue(body.contains("TvTeletext(tctx, pg, live, Modifier.fillMaxSize(), footRoom)"))
        assertFalse(body.contains("WindowTrophies(") || body.contains("WindowOptions(") || body.contains("WindowDetails("))
        assertEquals(1, src("WinPagesTv.kt").split("ctx.pageFor(").size - 1)
    }
}
