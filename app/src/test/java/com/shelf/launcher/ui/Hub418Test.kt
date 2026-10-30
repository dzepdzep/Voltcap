package com.shelf.launcher.ui

import com.shelf.launcher.data.RaCompletionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 418: a remote-only RetroAchievements game is a stand-in with the account's data and an `ra:` path (never a launchable ROM). */
class Hub418Test {
    @Test fun remoteStandInKeepsAccountDataAndHasNoRom() {
        val g = raStandIn(1234, RaCompletionEntry(GameID = 1234, Title = "Some Game", ConsoleName = "SNES", MaxPossible = 50, NumAwarded = 49))
        assertEquals("Some Game", g.name)
        assertEquals("SNES", g.platform)
        assertEquals(1234, g.raGameId)
        assertTrue(g.romPath == "ra:1234")
    }

    @Test fun standInWithoutEntryStillHasAName() {
        assertTrue(raStandIn(7, null).name.contains("7"))
    }
}
