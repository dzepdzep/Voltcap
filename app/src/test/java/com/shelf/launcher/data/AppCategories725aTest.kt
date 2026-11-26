package com.shelf.launcher.data

import com.shelf.launcher.ANDROID_GAMES_PLATFORM
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.RECOMPS_PLATFORM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 725a (owner Task 4): START > Categories - an installed app in several categories at once, manual beats automatic. */
class AppCategories725aTest {
    private fun app(name: String, pkg: String) = Game(name, "App", 1, SourceType.APP, packageName = pkg)
    private val rom = Game("Quest", "N64", 1, SourceType.ROM, romPath = "/r/q.z64")
    private val port = app("Harbour Port", "org.example.port")   // detected Android game (example: a Zelda-style recomp)
    private val notes = app("Notes", "com.example.notes")         // ordinary app, not in the library by itself

    private fun state(lib: LibraryAppsSettings, filter: String? = null) = LauncherUiState(
        games = listOf(rom, port, notes),
        gameAppPackages = setOf("org.example.port"),
        libraryApps = lib,
        platformFilter = filter
    )

    private fun keys(s: LauncherUiState) = s.visibleGames.map { it.stableKey }

    @Test fun oneAppInRecompsAndroidGamesAndN64AtOnce() {
        var lib = LibraryAppsSettings(recomps = setOf("org.example.port"))
        lib = AppCategories.withConsole(lib, "org.example.port", "N64", true)
        assertTrue(port.stableKey in keys(state(lib, RECOMPS_PLATFORM)))
        assertTrue(port.stableKey in keys(state(lib, ANDROID_GAMES_PLATFORM)))
        val n64 = keys(state(lib, "N64"))
        assertEquals(listOf(port.stableKey, rom.stableKey).sorted(), n64.sorted())
        // All games: once each, the same record (launch target, saves, play time, art keyed as before).
        val all = state(lib).visibleGames
        assertEquals(1, all.count { it.stableKey == port.stableKey })
        assertTrue(all.single { it.stableKey == port.stableKey } === port)
        // Never shows in Other apps (it is a game and a recomp).
        assertFalse(port.stableKey in keys(state(lib, "App")))
    }

    @Test fun aConsoleWithOnlyAnAppAppearsAndHidesAgainWhenEmpty() {
        val on = AppCategories.withConsole(LibraryAppsSettings(), "com.example.notes", "GameCube", true)
        assertTrue("GameCube" in state(on).availablePlatforms)
        assertEquals(listOf(notes.stableKey), keys(state(on, "GameCube")))
        // Listed under a console = in your library (it isn't otherwise in Games + apps you add).
        assertTrue(notes.stableKey in keys(state(on)))
        assertFalse(notes.stableKey in keys(state(LibraryAppsSettings())))
        val off = AppCategories.withConsole(on, "com.example.notes", "GameCube", false)
        assertFalse("GameCube" in state(off).availablePlatforms)
        assertTrue(off.appConsoles.isEmpty())
    }

    @Test fun manualKindBeatsAutomaticAndRecomp() {
        // Set as an app by hand: in Other apps, not Android games - even though Android says it's a game.
        val asApp = LibraryAppsSettings(forcedApps = setOf("org.example.port"), recomps = setOf("org.example.port"))
        assertTrue(port.stableKey in keys(state(asApp, "App")))
        assertFalse(port.stableKey in keys(state(asApp, ANDROID_GAMES_PLATFORM)))
        assertTrue(port.stableKey in keys(state(asApp, RECOMPS_PLATFORM)))
        // Automatic recomp (724a): still never an Other app.
        assertFalse(port.stableKey in keys(state(LibraryAppsSettings(recomps = setOf("org.example.port"), mode = AppsMode.ALL), "App")))
    }

    @Test fun storedChoicesSurviveARoundTripAndIgnoreJunk() {
        val m = mapOf("org.example.port" to setOf("N64", "GameCube"), "com.example.notes" to setOf("PS1"))
        assertEquals(m, AppCategories.decode(AppCategories.encode(m)))
        assertEquals(emptyMap<String, Set<String>>(), AppCategories.decode(setOf("|N64", "pkg|", "nosep")))
        assertEquals(emptyMap<String, Set<String>>(), AppCategories.decode(null))
        val src = File("src/main/java/com/shelf/launcher/data/LibraryAppsStore.kt").readText()
        assertTrue(src.contains("getStringSet(\"app_consoles\""))
        assertTrue(src.contains("putStringSet(\"app_consoles\""))
    }

    @Test fun specialCategoriesAreNeverOfferedAsConsoles() {
        val lib = AppCategories.withConsole(LibraryAppsSettings(), "p", "Android", true)
        assertTrue(lib.appConsoles.isEmpty())
        val choices = AppCategories.consoleChoices(listOf("App", "Android", RECOMPS_PLATFORM, "N64", "Steam"), setOf("GameCube"), all = false)
        assertEquals(listOf("N64", "GameCube"), choices)
        val all = AppCategories.consoleChoices(listOf("N64"), emptySet(), all = true)
        assertEquals(all.distinct(), all)
        assertTrue("N64" in all && "GameCube" in all && "Xbox360" in all)
        assertFalse("App" in all || "Android" in all || "MegaDrive" in all)
    }

    @Test fun summaryNamesEveryCategoryInFull() {
        assertEquals("Android games · Recomps · Nintendo 64",
            AppCategories.summary(androidGame = true, recomp = true, otherApp = false, consoles = setOf("N64"), collections = 0))
        assertTrue(AppCategories.summary(false, false, false, emptySet(), 0).startsWith("Only in All games"))
    }
}
