package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Update 469: the test hook's game match (the long-name run stayed on the old game) and the shorter save line. */
class Fixtures469Test {
    private fun g(name: String, path: String) = Game(name, "gbc", 0, SourceType.ROM, romPath = path)
    private val games = listOf(
        g("Pokemon Stadium 2", "/r/n64/Pokemon Stadium 2 (USA).z64"),
        g("From TV Animation One Piece: Yume no Luffy Kaizokudan Tanjou!", "/r/gbc/From TV Animation One Piece - Yume no Luffy Kaizokudan Tanjou! (Japan) (En).gbc"),
        g("Kirby Tilt 'n' Tumble", "/r/gbc/Kirby - Tilt 'n' Tumble (USA).gbc"),
    )

    @Test fun punctuationAndSpacesDoNotMatter() {
        assertEquals(games[1], TestHooks.matchGame(games, "One Piece - Yume no Luffy"))
        assertEquals(games[2], TestHooks.matchGame(games, "Kirby - Tilt"))
        assertEquals(games[0], TestHooks.matchGame(games, "pokemon stadium 2"))
    }

    @Test fun fileNameAndNoMatch() {
        assertEquals(games[1], TestHooks.matchGame(games, "(Japan) (En)"))
        assertNull(TestHooks.matchGame(games, "Zelda"))
        assertNull(TestHooks.matchGame(games, " - "))
    }

    @Test fun savesLineSaysSavesOnce() {
        // (529m, owner #15: a status that names the saves / the sync stands as it is - no "Saves:" in front of it.)
        assertEquals("Saves up to date · 2 min ago", savesLabel("Saves up to date · 2 min ago"))
        assertEquals("Save sync is not set up", savesLabel("Save sync is not set up"))
        assertEquals("Uploading saves…", savesLabel("Uploading saves…"))
        assertEquals("Saves: Not checked yet", savesLabel("Not checked yet"))
    }
}
