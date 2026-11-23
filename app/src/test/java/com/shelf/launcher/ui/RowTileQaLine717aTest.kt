package com.shelf.launcher.ui

import com.shelf.launcher.data.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 717a: the QA log line of the Row tile hook (`voltcap_test_dock`), so 714a (the round buttons' hand-placed spot kept per Tile
 * home Row place) can be graded on a handheld by tools/qa/tools/row714.py without opening R1 (its Quick row has system tiles).
 */
class RowTileQaLine717aTest {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    private val sc = HomeWidgets.SHORTCUTS

    @Test fun lineNamesTheButtonsSpotAndEveryKeptSpot() {
        val start = HomeWidgets.format(mapOf(sc to (0.3f to 0.2f), HomeWidgets.MUSIC to (0.9f to 0.1f)))
        val atTop = tileRowPlaceSpots(start, DockPosition.MIDDLE, DockPosition.TOP)
        assertEquals("TILES MIDDLE->TOP spot=ROW shortcuts=none kept=MIDDLE:0.3000,0.2000",
            rowTileQaLine("TILES", "MIDDLE", "TOP", "ROW", atTop))
        val back = tileRowPlaceSpots(atTop, DockPosition.TOP, DockPosition.MIDDLE)
        assertEquals("TILES TOP->MIDDLE spot=ROW shortcuts=0.3000,0.2000 kept=-",
            rowTileQaLine("TILES", "TOP", "MIDDLE", "ROW", back))
    }

    @Test fun keptSpotsListedInPlaceOrder() {
        val s = HomeWidgets.format(mapOf(tileRowSpotKey(DockPosition.BOTTOM) to (0.7f to 0.7f), tileRowSpotKey(DockPosition.TOP) to (0.1f to 0.5f)))
        assertEquals("TILES MIDDLE->MIDDLE spot=ROW shortcuts=none kept=TOP:0.1000,0.5000;BOTTOM:0.7000,0.7000",
            rowTileQaLine("TILES", "MIDDLE", "MIDDLE", "ROW", s))
    }

    @Test fun hookLogsAfterTheRowTilesOwnChange() {
        val fx = src("ui/LauncherScreen.kt").substringAfter("val testDock = TestHooks.dock.value").substringBefore("// (Pass 5:")
        assertTrue("still preview-only", fx.contains("if (TestHooks.previewOnly)"))
        // (718a: the reset now lives in rowTilePress, the one step the R1 tile and the hook share - RowTileExactHandler718aTest.)
        val reset = fx.indexOf("rowTilePress(a0, d)"); val log = fx.indexOf("QaTrace.event(\"rowtile\"")
        assertTrue("the Row tile's own change, then the log line", reset in 0 until log)
    }
}
