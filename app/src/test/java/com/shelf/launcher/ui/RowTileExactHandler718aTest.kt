package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.dockHere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 718a (owner 23 Nov 22:43, Task 1: "verify it is the exact handler"): R1's Row tile and the QA hook `voltcap_test_dock`
 * (row714.sh, HW-24 714a) share one step, rowTilePress, so a hardware run without the R1 panel proves the tile's own change.
 * Fails on 717a: the tile and the hook each wrote their own copy of the reset + new place.
 */
class RowTileExactHandler718aTest {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun nextFollowsTheTilesCycle() {
        val a = AppearanceSettings(homeLayout = HomeLayout.CLASSIC)
        var cur = a
        val seen = mutableListOf<DockPosition>()
        repeat(3) { cur = rowTilePress(cur, rowTileNext(cur)); seen += cur.dockHere }
        assertEquals("three presses visit every place once and return", DockPosition.values().toSet(), seen.toSet())
        assertEquals(a.dockHere, seen.last())
    }

    @Test fun pressMovesTheLayoutInUse() {
        for (l in listOf(HomeLayout.CLASSIC, HomeLayout.HERO)) for (to in DockPosition.values()) {  // (Tile home's reset path: TileRowPlaceSpots714aTest)
            val a = AppearanceSettings(homeLayout = l)
            assertEquals("$l -> $to", to, rowTilePress(a, to).dockHere)
        }
    }

    @Test fun tileAndHookCallTheSameStep() {
        val qs = src("ui/QuickSettings.kt")
        val tile = qs.substringAfter("add(R1Tile(\"rowpos\", Icons.Filled.SwapVert, \"Row\\n\"").substringBefore("})")
        assertTrue("R1 tile saves rowTilePress(a, rowTileNext(a))", tile.contains("home.setAppearance(rowTilePress(a, rowTileNext(a)))"))
        assertFalse("no private copy of the reset in the tile", tile.contains("tileRowPlaceReset("))
        val hook = src("ui/LauncherScreen.kt").substringAfter("val testDock = TestHooks.dock.value").substringBefore("// (Pass 5:")
        assertTrue("hook previews the same step", hook.contains("viewModel.previewAppearance(rowTilePress(a0, d))"))
        assertTrue("hook NEXT = the tile's own next place", hook.contains("if (name == \"NEXT\") rowTileNext(state.appearance)"))
        assertFalse("no private copy of the reset in the hook", hook.contains("tileRowPlaceReset("))
        assertTrue("hook stays preview-only", hook.contains("if (TestHooks.previewOnly)"))
        assertTrue("intent accepts NEXT", src("ui/TestHooks.kt").contains("d == \"NEXT\" ||"))
    }
}
