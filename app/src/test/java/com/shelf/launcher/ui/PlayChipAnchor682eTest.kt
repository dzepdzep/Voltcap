package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 682e (owner 18 Nov): the home's Play button and the selected category chip share one anchor - HomeAnchor.kt. */
class PlayChipAnchor682eTest {
    @Test fun chipsStartOnPlaysEdge() {
        assertEquals(16f, PlayChipAnchor.start(playLeft = 24f, rowLeft = 8f, fallback = 8f))  // Play 24 px in, row at 8 -> 16 inside
        assertEquals(8f, PlayChipAnchor.start(null, 0f, 8f))                                  // no Play on the home: as before
        assertEquals(8f, PlayChipAnchor.start(4f, 10f, 8f))                                   // Play left of the row: the row's own column
    }

    @Test fun selectedChipGlidesOntoTheEdge() {
        // the selected chip 300 px in on screen, the anchor 24 px in: scroll 276 further
        assertEquals(276, PlayChipAnchor.target(cur = 0, onScreenLeft = 300f, start = 24f, max = 1000))
        assertEquals(400, PlayChipAnchor.target(cur = 100, onScreenLeft = 324f, start = 24f, max = 1000))
        assertEquals(0, PlayChipAnchor.target(cur = 50, onScreenLeft = -26f, start = 24f, max = 1000)) // the first chips: the row's start
        assertEquals(1000, PlayChipAnchor.target(cur = 900, onScreenLeft = 524f, start = 24f, max = 1000))
    }

    @Test fun theLastChipCanReachTheEdgeOnlyWhenTheyOverflow() {
        assertTrue(PlayChipAnchor.overflows(start = 24f, chipsWidth = 900f, end = 24f, viewW = 900f))
        assertFalse(PlayChipAnchor.overflows(24f, 500f, 24f, 900f))
        assertEquals(776f, PlayChipAnchor.endRoom(viewW = 900f, start = 24f, lastChipW = 100f, end = 24f)) // last chip can sit at 24
        assertEquals(24f, PlayChipAnchor.endRoom(viewW = 100f, start = 24f, lastChipW = 100f, end = 24f))
    }

    /** 682m (owner 18 Nov 14:54): the strip starts on Play's edge, then scrolls naturally - neighbours kept, the end at its end. */
    @Test fun naturalScrollKeepsNeighboursAndTheEndAtItsEnd() {
        // 10 chips 100 px wide, 10 px apart, the strip starting at the Play edge (24 px): chip i at 24 + 110 i; 500 px view
        val lefts = IntArray(10) { 24 + 110 * it }; val widths = IntArray(10) { 100 }
        val max = 24 + 110 * 9 + 100 - 500   // = 614: the last chip's right edge on the view's right edge
        val fade = 56
        // at the start nothing moves while the chip, the one before and the one after are in view
        assertEquals(0, PlayChipAnchor.natural(0, lefts, widths, cur = 0, viewW = 500, start = 24, fade = fade, max = max))
        assertEquals(0, PlayChipAnchor.natural(1, lefts, widths, 0, 500, 24, fade, max))
        // going on: chip 3's neighbour (4) would sit under the fade - the row moves just enough for it to show whole
        val t3 = PlayChipAnchor.natural(3, lefts, widths, 0, 500, 24, fade, max)
        assertEquals(24 + 110 * 4 + 100 - 500 + fade, t3)
        assertTrue("the chip you're on is not pinned to the Play edge", lefts[3] - t3 > 24)
        // going back: the one before comes to the Play edge, so the strip starts there again with the chip beside it
        assertEquals(lefts[4] - 24, PlayChipAnchor.natural(5, lefts, widths, cur = 600, viewW = 500, start = 24, fade = fade, max = max))
        // the last chip: the list ends at its own end - it sits on the right with its neighbours, never alone on the left
        val tl = PlayChipAnchor.natural(9, lefts, widths, cur = 300, viewW = 500, start = 24, fade = fade, max = max)
        assertEquals(max, tl)
        assertTrue("neighbours of the last chip in view", lefts[8] - tl >= 24 && lefts[7] - tl >= 0)
        // all chips fit: no scroll at all
        assertEquals(0, PlayChipAnchor.natural(1, IntArray(3) { 24 + 110 * it }, IntArray(3) { 100 }, 0, 500, 24, fade, max = 0))
    }

    @Test fun theRuleIsWiredOnBothEnds() {
        fun src(n: String) = java.io.File(listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + n) }.first { it.exists() }.path).readText()
        val h = src("HomeChrome.kt"); val p = src("PlayHeader.kt"); val l = src("LauncherScreen.kt")
        assertTrue(l.contains("LocalHomeAnchor provides homeAnchor"))
        assertTrue(p.contains("LocalHomeHeader provides true") && p.contains("anchor.playLeftPx = x") && p.contains("homeAnchor = \"play\""))
        assertTrue(h.contains("startPadding = anchoredStart ?: e") && h.contains("anchored = anchoredStart != null") && h.contains("homeAnchor = \"chip\""))
    }

    @Test fun adaptedVersionsInTheOwnLayouts() {
        // Library: the tab you're in leads its strip (after L2) - steady - unless every tab fits (then nothing moves)
        // (682m: one chip of context before the one you're on)
        assertEquals(6, ChipLead.start(keep = 7, allFit = false)); assertEquals(0, ChipLead.start(7, allFit = true))
        assertEquals(0, ChipLead.start(keep = 0, allFit = false))
        fun src(n: String) = java.io.File(listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + n) }.first { it.exists() }.path).readText()
        assertTrue(src("StyleLibrary.kt").contains("leadFocused = true"))
        // Bar: the category tab on the Play button's column (6 dp, as the title), both marked for anchorcheck
        val bar = src("StyleIconBar.kt")
        assertTrue(bar.contains("Modifier.padding(start = 6.dp)\n            .semantics { homeAnchor = \"chip\" }") && bar.contains("homeAnchor = \"play\""))
        // Dash: already on one column - marked so anchorcheck keeps it so
        val dash = src("StyleDashboard.kt"); assertTrue(dash.contains("homeAnchor = \"play\"") && dash.contains("homeAnchor = \"chip\""))
    }
}
