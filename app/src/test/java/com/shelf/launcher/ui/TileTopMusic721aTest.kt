package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 721a (Task 1, Tile home Top vs the profile row): with the Switch-style profile pictures shown, Top kept the rows clear of
 * the pictures only, so a music player Home shows in a top corner (its bottom ~112 dp) sat on the tiles / first pill.
 * The old rule was maxOf(60, badge + 8) = 60 dp here; the stack now starts 8 dp under whichever reaches lower.
 */
class TileTopMusic721aTest {
    private fun src(rel: String): String {
        val f = listOf("src/main/java/com/shelf/launcher/$rel", "app/src/main/java/com/shelf/launcher/$rel").map(::File).first { it.exists() }
        return f.readText()
    }

    @Test fun profilesAndATopCornerPlayerBothKeepTheRowsClear() {
        // Pictures 12 + 40 dp (bottom 52), player 52 dp down + ~60 dp tall (bottom 112).
        assertEquals(120f, TileRows.topPadDp(profilesShown = true, badgeBottomDp = 52f, musicBottomDp = 112f))
        // The pictures reaching lower (x1.3 measured bottom) still win.
        assertEquals(138f, TileRows.topPadDp(profilesShown = true, badgeBottomDp = 130f, musicBottomDp = 112f))
    }

    @Test fun unchangedWhereOnlyOneOrNeitherIsShown() {
        assertEquals(60f, TileRows.topPadDp(profilesShown = true, badgeBottomDp = 52f, musicBottomDp = 0f))
        assertEquals(60f, TileRows.topPadDp(profilesShown = true, badgeBottomDp = 0f, musicBottomDp = 0f))
        assertEquals(10f, TileRows.topPadDp(profilesShown = false, badgeBottomDp = 52f, musicBottomDp = 0f))
        assertEquals(10f, TileRows.topPadDp(profilesShown = false, badgeBottomDp = 0f, musicBottomDp = 1f))
        assertEquals(70f, TileRows.topPadDp(profilesShown = false, badgeBottomDp = 0f, musicBottomDp = 62f))
    }

    @Test fun tileHomeTopUsesTheSharedRule() {
        val ls = src("ui/LauncherScreen.kt")
        assertTrue(ls.contains("TileRows.topPadDp("))
        assertTrue(ls.contains("musicBottomDp = TileRowEdit.musicBottomPx.toDp().value"))
        // Middle keeps both floors as before.
        assertTrue(ls.contains("badgeFloor, musicFloor)"))
    }
}
