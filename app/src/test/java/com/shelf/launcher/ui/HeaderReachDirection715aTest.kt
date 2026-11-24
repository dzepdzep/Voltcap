package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 715a (owner Task 1, Row-position collisions - Play controls / moved widgets): on the Voltcap row with the game header off,
 * an added "Play / Continue" (or "Play & buttons") part is what ▲ / ▼ reach. The D-pad used to decide the direction from the
 * row's place (Row at the top -> ▼) or from a stored spot of the switched-off header, so ▼ went to a Play drawn above the games.
 */
class HeaderReachDirection715aTest {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun addedPlayPartDecidesWithTheHeaderOff() {
        // Row at the top, header off, Play part drawn in the upper part of the screen: ▲ (was ▼).
        assertTrue(HomeReach.headerAbove(classic = true, headerOn = false, dockTop = true, headerMovedY = null, addedPlayY = 0.18f))
        // Row at the bottom, Play part placed low: ▼ (was ▲).
        assertFalse(HomeReach.headerAbove(classic = true, headerOn = false, dockTop = false, headerMovedY = null, addedPlayY = 0.85f))
        // A stored spot of the switched-off header no longer wins over where Play really is.
        assertFalse(HomeReach.headerAbove(classic = true, headerOn = false, dockTop = false, headerMovedY = 0.1f, addedPlayY = 0.9f))
    }

    @Test fun headerOnKeepsTheEarlierRules() {
        // Header on: the header (moved or in its place) decides, as before 715a - an added Play part does not.
        assertFalse(HomeReach.headerAbove(classic = true, headerOn = true, dockTop = true, headerMovedY = null, addedPlayY = 0.1f))
        assertTrue(HomeReach.headerAbove(classic = true, headerOn = true, dockTop = false, headerMovedY = null, addedPlayY = 0.9f))
        assertTrue(HomeReach.headerAbove(classic = true, headerOn = true, dockTop = true, headerMovedY = 0.2f, addedPlayY = null))
        assertFalse(HomeReach.headerAbove(classic = true, headerOn = true, dockTop = false, headerMovedY = 0.8f, addedPlayY = null))
        // Nothing added, header off: the row's place, unchanged.
        assertFalse(HomeReach.headerAbove(classic = true, headerOn = false, dockTop = true, headerMovedY = null, addedPlayY = null))
        assertTrue(HomeReach.headerAbove(classic = true, headerOn = false, dockTop = false, headerMovedY = null, addedPlayY = null))
        // Other layouts: unchanged (their own Play & buttons; added parts are not read there).
        assertTrue(HomeReach.headerAbove(classic = false, headerOn = false, dockTop = true, headerMovedY = null, addedPlayY = 0.9f))
        assertFalse(HomeReach.headerAbove(classic = false, headerOn = true, dockTop = false, headerMovedY = 0.7f, addedPlayY = null))
    }

    @Test fun homeReadsWherePartsAreDrawn() {
        val screen = src("ui/LauncherScreen.kt")
        assertTrue("prepareReach asks HomeReach.headerAbove", screen.contains("HomeReach.headerAbove("))
        assertTrue("it reads the added Play part's drawn place", screen.contains("PartRects.frac[HomeWidgets.overlayId(l, HomeWidgets.H_PLAY)]"))
        val edit = src("ui/HomeEdit.kt")
        assertTrue("MovableWidget records where it is drawn", edit.contains("PartRects.frac[id] ="))
        assertTrue("and forgets it when it leaves", edit.contains("PartRects.frac.remove(id)"))
    }
}
