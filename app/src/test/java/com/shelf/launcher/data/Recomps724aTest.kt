package com.shelf.launcher.data

import com.shelf.launcher.ANDROID_GAMES_PLATFORM
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.RECOMPS_PLATFORM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 724a (owner Task 3): the Recomps category - installed recomp / native-port apps marked by hand, hidden while empty. */
class Recomps724aTest {
    private fun app(name: String, pkg: String) = Game(name, "App", 1, SourceType.APP, packageName = pkg)
    private val rom = Game("Quest", "N64", 1, SourceType.ROM, romPath = "/r/q.z64")
    private val port = app("Harbour Port", "org.example.port")       // not detected as a game
    private val racer = app("Racer", "com.studio.racer")              // detected Android game
    private val notes = app("Notes", "com.example.notes")             // ordinary app

    private fun state(recomps: Set<String>, filter: String? = null) = LauncherUiState(
        games = listOf(rom, port, racer, notes),
        gameAppPackages = setOf("com.studio.racer"),
        libraryApps = LibraryAppsSettings(recomps = recomps, added = setOf("com.example.notes")),
        platformFilter = filter
    )

    @Test fun emptyRecompsIsHiddenAndNothingIsGuessed() {
        val s = state(emptySet())
        assertFalse(RECOMPS_PLATFORM in s.availablePlatforms)
        assertTrue(state(emptySet(), RECOMPS_PLATFORM).visibleGames.isEmpty())
        assertFalse(s.isRecomp(port))
        assertFalse(s.isRecomp(rom))
    }

    @Test fun markedAppsMakeTheCategoryAndJoinAllGamesOnce() {
        val marks = setOf(port.packageName!!, racer.packageName!!)
        assertTrue(RECOMPS_PLATFORM in state(marks).availablePlatforms)
        assertEquals(listOf(port, racer).map { it.stableKey }.sorted(),
            state(marks, RECOMPS_PLATFORM).visibleGames.map { it.stableKey }.sorted())
        // In the library (All) once each, even though the port isn't a detected game; no duplicate record.
        val all = state(marks).visibleGames
        assertEquals(1, all.count { it.stableKey == port.stableKey })
        assertEquals(1, all.count { it.stableKey == racer.stableKey })
        assertTrue(notes in all) // added by hand: in All as before
        assertEquals(1, all.count { it.stableKey == notes.stableKey })
    }

    @Test fun aRecompIsNeverAnOtherAppButAnAndroidGameStaysOne() {
        val marks = setOf(port.packageName!!, racer.packageName!!)
        val other = state(marks, "App").visibleGames
        assertFalse(port in other)
        assertTrue(notes in other)
        // The detector's own verdict is untouched: Racer stays in Android games too, the port isn't made one.
        val android = state(marks, ANDROID_GAMES_PLATFORM).visibleGames
        assertTrue(racer in android)
        assertFalse(port in android)
        assertFalse(state(marks).isAndroidGame(port))
    }

    @Test fun markingChangesOnlyTheCategoryNotTheGameRecord() {
        val g = state(setOf(port.packageName!!), RECOMPS_PLATFORM).visibleGames.single()
        assertTrue(g === port) // same record: launch target, stable key (saves, play time, art) unchanged
    }

    @Test fun theMarkIsStoredWithTheOtherAppChoices() {
        val src = File("src/main/java/com/shelf/launcher/data/LibraryAppsStore.kt").readText()
        assertTrue(src.contains("getStringSet(\"recomps\""))
        assertTrue(src.contains("putStringSet(\"recomps\", s.recomps)"))
        val scopes = File("src/main/java/com/shelf/launcher/data/SettingsScopes.kt").readText()
        assertTrue("library_apps prefs are backed up", scopes.contains("\"library_apps\""))
    }
}
