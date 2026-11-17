package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678o (KONKR x1.3 popup gate k678n13, Channel readability FAIL "home overlap: '48/120' and 'Shadow Of The Colossus' drawn
 * over each other"): at Larger text the focused channel's name wraps to two lines and its plate rose over the achievement count.
 * The Channel tile now shares one badge lane with its art (StyleArt badgeLane) and the name plate joins it as fixed CONTENT
 * (laneContentAt), so the count keeps clear of the name or is left out - never under it. Fails on the 678n sources.
 */
class ChanNameLane678oTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun channelNamePlateJoinsTheTilesBadgeLane() {
        val s = source("StyleConsoleHomes.kt")
        val tile = s.substring(s.indexOf("private fun ChanTile("), s.indexOf("internal fun ContinuousGrid("))
        assertTrue("one lane for the tile", tile.contains("val lane = rememberBadgeLane()"))
        assertTrue("the art uses the tile's lane", tile.contains("StyleArt(c, g, Modifier.fillMaxSize().padding(3.dp), wide = true, corner = 13.dp, badgeLane = lane)"))
        val plate = tile.indexOf(".laneContentAt(lane, Alignment.BottomStart, 3.dp, 3.dp)")
        assertTrue("the name plate is lane CONTENT, measured as drawn (before its rise / clip)", plate >= 0 && plate < tile.indexOf(".riseOnEnter()"))
    }

    @Test fun styleArtTakesTheCallersLaneInBothBranches() {
        val s = source("StyleHomes.kt")
        assertTrue(s.contains("badgeLane: BadgeLane? = null"))
        assertEquals("both StyleArt branches use the caller's lane when given", 2, Regex("""val lane = badgeLane \?: ownLane""").findAll(s).count())
    }
}
