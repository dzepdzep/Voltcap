package com.shelf.launcher.ui

import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 423: the stand-in library used for the layout preview pictures. */
class DemoLibrary423Test {
    private val games = listOf(
        Game("Zelda-like Quest", "SNES", 1, SourceType.ROM, romPath = "/r/a.sfc", raGameId = 11),
        Game("Alpha Racer", "N64", 2, SourceType.ROM, romPath = "/r/b.z64", sgdbGameId = 5),
        Game("Middle Tales", "GBA", 3, SourceType.ROM, romPath = "/r/c.gba")
    )

    @Test fun madeUpNamesKeepTheAlphabeticalOrder() {
        val m = DemoLibrary.names(games)
        val realOrder = games.sortedBy { it.name.lowercase() }.map { it.stableKey }
        val demoOrder = games.sortedBy { m[it.stableKey]!!.lowercase() }.map { it.stableKey }
        assertEquals(realOrder, demoOrder)
        assertTrue(games.none { g -> m[g.stableKey] == g.name })
    }

    @Test fun dressedListIsStableWhileTheRealListIs() {
        // A new dressed list on every state change made the Cardboard box and the shelf restart their reveal forever.
        val s = LauncherUiState(games = games)
        val a = DemoLibrary.dress(s)
        val b = DemoLibrary.dress(s.copy(focusedIndex = 2))
        assertSame(a.games, b.games)
        assertSame(a.physicalMedia, b.physicalMedia)
    }

    @Test fun nothingRealLeaksIntoThePicture() {
        val d = DemoLibrary.dress(LauncherUiState(games = games))
        assertTrue(d.games.all { it.raGameId == null && it.sgdbGameId == null && it.steamAppId == null })
        assertTrue(d.raIcons.isEmpty() && d.screenshots.isEmpty() && d.metadata.isEmpty())
        assertNull(d.raUsername)
        // Every game counts as looked up with no scans (drawn from its console's own spine, never waiting).
        assertTrue(d.games.all { d.physicalMedia[it.stableKey]?.isEmpty() == true })
        // Same keys, consoles and order as the real library.
        assertEquals(games.map { it.stableKey to it.platform }, d.games.map { it.stableKey to it.platform })
    }
}
