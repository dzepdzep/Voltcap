package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** D79 (CLAUDE.md rule 11): Now Playing title / artist / source are never cut with "…"; they wrap, shrink, then slide (FullText). */
class NowPlayingTruncate79Test {
    private val files = listOf("NowPlayingPanel", "MusicPlayer", "BigMusicPlayer", "FullMusicScreen", "ScreenSaver", "LyricsStage")

    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name.kt"
        val f = listOf(File(rel), File("app/$rel")).first { it.exists() }
        return f.readText()
    }

    @Test fun nowPlayingWidgetsDoNotEllipsize() {
        for (n in files) {
            assertTrue("$n still uses TextOverflow.Ellipsis", !source(n).contains("TextOverflow.Ellipsis"))
        }
    }

    @Test fun nowPlayingPanelUsesFullText() {
        val s = source("NowPlayingPanel")
        assertTrue(s.contains("FullText("))
    }
}
