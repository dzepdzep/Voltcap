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

    @Test fun theRuleIsWiredOnBothEnds() {
        fun src(n: String) = java.io.File(listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + n) }.first { it.exists() }.path).readText()
        val h = src("HomeChrome.kt"); val p = src("PlayHeader.kt"); val l = src("LauncherScreen.kt")
        assertTrue(l.contains("LocalHomeAnchor provides homeAnchor"))
        assertTrue(p.contains("LocalHomeHeader provides true") && p.contains("anchor.playLeftPx = x") && p.contains("homeAnchor = \"play\""))
        assertTrue(h.contains("startPadding = anchoredStart ?: e") && h.contains("anchored = anchoredStart != null") && h.contains("homeAnchor = \"chip\""))
    }
}
