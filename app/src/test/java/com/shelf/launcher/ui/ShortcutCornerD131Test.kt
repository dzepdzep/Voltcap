package com.shelf.launcher.ui

import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.ShortcutSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.6.66 (D131): the GT78 (game-row setting "Top") put the corner ☰ of every corner on the bottom edge of Disc station, over the
 * disc panel and its "DISC LOADED" label, because the corner was flipped by a game row that whole-screen layouts don't draw.
 */
class ShortcutCornerD131Test {
    private val corners = listOf(ShortcutSpot.TOP_LEFT, ShortcutSpot.TOP_RIGHT, ShortcutSpot.BOTTOM_LEFT, ShortcutSpot.BOTTOM_RIGHT)

    @Test fun wholeScreenLayoutWithTheRowSettingAtTheTopKeepsTheTopBand() {
        // Old code: top corners → bottom (over Disc station's panel). Fails on f0f71bc.
        for (c in corners) assertTrue("$c", ShortcutCorner.resolve(c, DockPosition.TOP, fullScreen = true).top)
    }

    @Test fun wholeScreenLayoutsDontDependOnTheRowSetting() {
        for (c in corners) {
            val places = DockPosition.entries.map { ShortcutCorner.resolve(c, it, fullScreen = true) }.toSet()
            assertEquals("$c gives one place on every device", 1, places.size)
        }
    }

    @Test fun theSidePickedIsKept() {
        for (d in DockPosition.entries) for (fs in listOf(true, false)) {
            assertTrue(ShortcutCorner.resolve(ShortcutSpot.TOP_LEFT, d, fs).start)
            assertTrue(ShortcutCorner.resolve(ShortcutSpot.BOTTOM_LEFT, d, fs).start)
            assertTrue(!ShortcutCorner.resolve(ShortcutSpot.TOP_RIGHT, d, fs).start)
            assertTrue(!ShortcutCorner.resolve(ShortcutSpot.BOTTOM_RIGHT, d, fs).start)
        }
    }

    @Test fun rowLayoutsStillStepOffTheRowsEdge() {
        // Classic / Tile home / Hero: unchanged.
        assertEquals(false, ShortcutCorner.resolve(ShortcutSpot.TOP_LEFT, DockPosition.TOP, fullScreen = false).top)
        assertEquals(true, ShortcutCorner.resolve(ShortcutSpot.BOTTOM_RIGHT, DockPosition.BOTTOM, fullScreen = false).top)
        assertEquals(true, ShortcutCorner.resolve(ShortcutSpot.TOP_RIGHT, DockPosition.MIDDLE, fullScreen = false).top)
        assertEquals(false, ShortcutCorner.resolve(ShortcutSpot.BOTTOM_LEFT, DockPosition.MIDDLE, fullScreen = false).top)
    }

    @Test fun barRoomStopsBeforeTheClockCluster() {
        // GT78-like: bar room at 21..87 px, cluster from 360 px, screen 960: the row folds right up to 360 - gap.
        val r = ShortcutCorner.bandRoom(21f, 87f, foldRight = true, busy = listOf(360f..940f), screenW = 960f, margin = 12f, gap = 9f)
        assertEquals(360f - 9f - 21f, r, 0.01f)
    }

    @Test fun barRoomBeforeTheClockFoldsLeftToTheScreenEdge() {
        // Room inside the cluster (TOP_RIGHT): the cluster itself doesn't block; nothing to its left → screen edge.
        val r = ShortcutCorner.bandRoom(360f, 426f, foldRight = false, busy = listOf(360f..940f), screenW = 960f, margin = 12f, gap = 9f)
        assertEquals(426f - 12f, r, 0.01f)
    }

    @Test fun barRoomStopsAtTheMusicPlayer() {
        val r = ShortcutCorner.bandRoom(600f, 660f, foldRight = false, busy = listOf(600f..1900f, 30f..400f), screenW = 1920f, margin = 18f, gap = 14f)
        assertEquals(660f - 414f, r, 0.01f)
    }

    @Test fun barRoomIsNeverSmallerThanTheRoomItself() {
        val r = ShortcutCorner.bandRoom(20f, 86f, foldRight = true, busy = listOf(88f..400f), screenW = 960f, margin = 12f, gap = 9f)
        assertEquals(66f, r, 0.01f)
    }

    @Test fun scrollBringsTheFocusedButtonIntoView() {
        // 9 buttons, stride 60, size 51, window 200 px.
        assertNull(ShortcutCorner.scrollFor(0, 9, false, 60f, 51f, 200f, 0f))
        assertEquals(4 * 60f + 51f - 200f, ShortcutCorner.scrollFor(4, 9, false, 60f, 51f, 200f, 0f)!!, 0.01f)
        assertEquals(60f, ShortcutCorner.scrollFor(1, 9, false, 60f, 51f, 200f, 300f)!!, 0.01f)
        // Right-hand ☰ (laid out from the end): the last drawn button is the nearest.
        assertNull(ShortcutCorner.scrollFor(8, 9, true, 60f, 51f, 200f, 0f))
        assertEquals(8 * 60f + 51f - 200f, ShortcutCorner.scrollFor(0, 9, true, 60f, 51f, 200f, 0f)!!, 0.01f)
        assertNull(ShortcutCorner.scrollFor(3, 9, false, 60f, 51f, 0f, 0f))
    }
}
