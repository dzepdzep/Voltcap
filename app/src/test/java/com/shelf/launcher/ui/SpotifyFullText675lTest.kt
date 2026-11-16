package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 675l (CLAUDE.md rule 11, QA R18 / matrix P13): the Spotify screen and player never cut a track, artist, album,
 * playlist or heading with "…"; they wrap, shrink to a readable size, then slide (FullText). Fails on the 675k sources
 * (13 TextOverflow.Ellipsis uses).
 */
class SpotifyFullText675lTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/spotify/$name.kt"
        val f = listOf(File(rel), File("app/$rel")).first { it.exists() }
        return f.readText()
    }

    @Test fun spotifyScreensDoNotEllipsize() {
        for (n in listOf("SpotifyScreen", "SpotifyPlayer")) {
            assertTrue("$n still uses TextOverflow.Ellipsis", !source(n).contains("TextOverflow.Ellipsis"))
        }
    }

    @Test fun spotifyNamesUseFullTextWithoutReservedLines() {
        // reserve = false: a one-line artist under a two-line slot keeps today's row height (no new gap in the lists).
        for ((n, count) in listOf("SpotifyScreen" to 9, "SpotifyPlayer" to 4)) {
            val s = source(n)
            assertEquals("$n FullText uses", count, Regex("""FullText\(""").findAll(s).count())
            assertEquals("$n reserve = false", count, Regex("""reserve = false""").findAll(s).count())
        }
    }
}
