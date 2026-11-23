package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 698a (CLAUDE.md rule 11, QA R18 / matrix P13): the music player pages never cut text with "…" - the Spotify
 * connect hint, playlist names in the strip and in the grid, and the current / next lyric line go through FullText
 * (shrink to a readable size, then slide), no reserved height so the rows keep their spacing.
 * Fails on the 697a sources (5 Ellipsis uses).
 */
class PlayerPagesFullText698aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/PlayerPages.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun playerPagesShowTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        // One FullText call: from "FullText(" + head up to the first "reserve = false" (multi-line calls allowed).
        fun count(head: String) = Regex(Regex.escape("FullText($head") + """[^()]*?(\([^()]*\)[^()]*?)*reserve = false""").findAll(s).count()
        assertEquals("spotify connect hint", 1, count("\"Connect Spotify (Settings › Accounts) for playlists\", "))
        assertEquals("playlist names (strip + grid)", 2, count("p.name ?: \"Playlist\", ") + count("\n                            p.name ?: \"Playlist\", "))
        assertEquals("current lyric line", 1, count("now, "))
        assertEquals("next lyric line", 1, count("next, "))
    }
}
