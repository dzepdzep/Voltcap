package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.6.66 (D142): the Twin hinge window cut its last facts line in half above the keys on the GT78, and a scrolling fix collapsed
 * the facts to nothing; Row reveal's buttons sat over the Channel grid's tiles. Whole-row paging maths + source checks.
 */
class TwinHinge142Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    // Four fact rows of 30 px and a summary of 5 lines of 14 px (2 px top padding) = 120 + 72.
    private val heights = listOf(30, 30, 30, 30, 72)
    private val b = SnapPaging.bounds(heights, textLine = 14, textTop = 2)

    @Test fun boundsAreRowsAndSummaryLines() {
        assertEquals(listOf(0, 30, 60, 90, 120, 136, 150, 164, 178, 192), b)
    }

    @Test fun aWindowNeverEndsMidRow() {
        // GT78 before: 75 px of room showed 2½ rows. Now: two whole rows.
        assertEquals(60, SnapPaging.window(b, 0, 75))
        for (avail in 1..250) for (top in b) {
            val w = SnapPaging.window(b, top, avail)
            assertTrue("never empty ($top/$avail)", w > 0 || top == b.last())
            assertTrue("fits ($top/$avail)", w <= avail)
            val end = top + w
            assertTrue("ends on a boundary unless one row is taller than the room ($top/$avail -> $end)", end in b || w == avail)
        }
    }

    @Test fun everythingFitsMeansNoPaging() {
        assertEquals(192, SnapPaging.window(b, 0, 400))
        assertNull(SnapPaging.next(b, 0, 400))
    }

    @Test fun pagesStepFromTheFirstRowNotShownWhole() {
        assertEquals(60, SnapPaging.next(b, 0, 75))
        assertEquals(120, SnapPaging.next(b, 60, 75))
        // The last page reaches the end: no further page (the pan goes back to the top).
        var top = 0; var pages = 0
        while (true) { val n = SnapPaging.next(b, top, 75) ?: break; top = n; pages++; assertTrue(pages < 20) }
        assertEquals(192, top + SnapPaging.window(b, top, 75))
    }

    @Test fun aRowTallerThanTheRoomIsCutNotHidden() {
        val bb = SnapPaging.bounds(listOf(80, 30), 0, 0)
        assertEquals(50, SnapPaging.window(bb, 0, 50))
    }

    @Test fun hingeReservesTheKeysAndSnapsTheFacts() {
        val s = src("WinSceneTwin.kt")
        // (529l / 529m: the keys of the Info screen - TwinTouchPages - are each page's own, inert on the copy that is leaving.)
        assertTrue("facts take what the keys leave", s.contains("Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(bottom = 8.dp)) {") &&
            s.contains("PageBars(keys, 6.dp) { k -> twinKey(k) }") && s.contains("val keysOf = rememberPageKeys(ctx)")) // (529p: bars)
        // (Update 487, D265: whole rows of facts that stay, the story in whole-line pages.)
        assertTrue("whole rows", s.contains("WholeRows(Modifier.fillMaxWidth())") && s.contains("PagedStory("))
        assertTrue("touch half keeps its room", s.contains("coerceAtMost((h - 200.dp).coerceAtLeast(72.dp))"))
        assertTrue("old hinge removed", !src("WinScenesMore.kt").contains("internal fun TwinHinge("))
        assertTrue("Twin desk's own pale veil", src("GameWindow.kt").contains("layout == HomeLayout.TWIN_DESK) twinDeskVeil"))
    }

    // 529l: with a page open the top screen is a strip - room for the page's name over the game's, never taller than on Info.
    @Test fun topScreenFoldsToAStripOnPages() {
        for (h in listOf(300, 395, 427, 520)) {
            val main = 120f
            val strip = twinTopPageHeight(androidx.compose.ui.unit.Dp(h.toFloat()), androidx.compose.ui.unit.Dp(main)).value
            assertTrue("h=$h strip $strip", strip in 76f..104f && strip <= main)
        }
        assertEquals(92f, twinTopPageHeight(androidx.compose.ui.unit.Dp(520f), androidx.compose.ui.unit.Dp(92f)).value, 0.01f)
        assertTrue("Twin draws its own pages", nativePages(WinScene.TWIN_HINGE))
    }

    @Test fun gridRevealButtonsHaveTheirOwnPanel() {
        val s = src("RowRevealWindow.kt")
        assertTrue(s.contains("val panel = !opens && state.appearance.homeLayout.styleHome &&") && s.contains("HomeLayout.CHAN || !sideRoom) && (fitsBelow || fitsAbove)"))
        assertTrue("name inside the panel", s.contains("else if (panel) Offset(pad + panelPad, panelTop + panelPad)"))
        assertTrue("the notch closes its path, not the window", s.contains("this.close()") && !s.contains("lineTo(nx + notch, bottom - 1f) }\n                    close()"))
        assertTrue("the badge strip stays off the tiles", s.contains("if (!panel && !showAll && buttonsSize != IntSize.Zero"))
    }
}
