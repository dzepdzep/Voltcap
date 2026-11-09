package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.ui.theme.dangerOn
import com.shelf.launcher.ui.theme.statusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 482: C1 status tones that stay readable on light surfaces; B5 scrolling / layout stability. */
class Batch482Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    // C1: the bright red / amber were ~2:1 on Plain light or Mint; a light surface gets the deeper tone.
    @Test fun statusTonesFollowTheSurface() {
        val light = Color(0xFFB3261E); val dark = Color(0xFFFF8A80)
        assertEquals(light, statusTone(Color(0xFFF4F6F8), light, dark))
        assertEquals(light, statusTone(Color(0xFFDDF3E6), light, dark))
        assertEquals(dark, statusTone(Color(0xFF1B1D24), light, dark))
        assertEquals(dark, statusTone(Color(0xFF000000), light, dark))
    }

    @Test fun chromeUsesTheStatusTones() {
        assertTrue(src("GamePopupStatus.kt").contains("if (row.destructive) ShelfTheme.danger else"))
        assertTrue(src("CartShellPicker.kt").contains("EditionStatus.PENDING) ShelfTheme.warning else"))
        val a = src("AchievementDetailDialog.kt")
        assertFalse(a.contains("tint = MissableAmber"))
        assertTrue(a.contains("tint = ShelfTheme.warning"))
    }

    // B5: Cardboard box with one box — ◀ on the first case wrapped (Endless) but ▶ on the last only bumped.
    @Test fun singleBoxWrapsBothWays() {
        val b = src("StyleCardboardBox.kt")
        assertTrue(b.contains("if (next >= BoxNav.boxes) {\n                    if (StyleLoop.on) {"))
        assertFalse(b.contains("StyleLoop.on && BoxNav.boxes > 1"))
    }

    // A5: counts beside a category = what that category opens. "Android games" read 0 (no game has platform "Android").
    @Test fun categoryCountsMatchTheCategory() {
        val rom = Game("Quest", "SNES", 1, SourceType.ROM, romPath = "/r/q.sfc")
        val racer = Game("Racer", "App", 1, SourceType.APP, packageName = "com.studio.racer")
        val s = LauncherUiState(games = listOf(rom, racer), gameAppPackages = setOf("com.studio.racer"))
        assertEquals(1, s.countIn("Android"))
        assertEquals(listOf(racer.stableKey), s.gamesIn("Android").map { it.stableKey })
        assertEquals(1, s.countIn("SNES"))
        assertEquals(0, s.countIn("NES"))
    }

    @Test fun layoutsUseTheCategoryCount() {
        for (f in listOf("StyleBlades.kt", "StyleRentalShop.kt", "StyleGreenCore.kt", "StyleBubbles.kt", "StylePocketQuest.kt", "StylePanels.kt")) {
            val t = src(f)
            assertTrue(f, t.contains("categoryCounts(c)"))
            assertFalse(f, t.contains("groupingBy { it.platform }"))
        }
        val t = src("StyleTerminal.kt")
        assertTrue(t.contains("c.state.countIn(it)") && t.contains("c.state.gamesIn(it)"))
    }

    // A5 (483): "All games" in the category screen / Dashboard = what All opens (apps only when in the library).
    @Test fun allGamesCountsTheLibrary() {
        val rom = Game("Quest", "SNES", 1, SourceType.ROM, romPath = "/r/q.sfc")
        val racer = Game("Racer", "App", 1, SourceType.APP, packageName = "com.studio.racer")
        val spotify = Game("Spotify", "App", 1, SourceType.APP, packageName = "com.spotify.music")
        val s = LauncherUiState(games = listOf(rom, racer, spotify), gameAppPackages = setOf("com.studio.racer"),
            libraryApps = com.shelf.launcher.data.LibraryAppsSettings(mode = com.shelf.launcher.data.AppsMode.GAMES_AND_ADDED))
        assertEquals(setOf(rom.stableKey, racer.stableKey), s.libraryGames().map { it.stableKey }.toSet())
        val cs = src("CategoryScreen.kt")
        assertTrue(cs.contains("val library = state.libraryGames()") && cs.contains("count = library.size"))
        assertTrue(src("LauncherScreen.kt").contains("categoryTilesStale(categoryTiles, libraryTotal)"))
        assertFalse(src("StyleDashboard.kt").contains("c.state.games.size"))
    }

    // C1 (483): destructive rows in the layouts' own game windows follow the window's writing colour.
    @Test fun windowDangerFollowsTheLook() {
        assertEquals(Color(0xFFB3261E), dangerOn(Color(0xFF202124)))
        assertEquals(Color(0xFFFF8A80), dangerOn(Color(0xFFF2F2F2)))
        assertFalse(src("GameWindow.kt").contains("Color(0xFFFF8A80)"))
        assertFalse(src("GameWindowBanner.kt").contains("Color(0xFFFF8A80)"))
    }
}
