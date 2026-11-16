package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678e (CLAUDE.md rule 11, QA R18 / matrix P13): the game window banner (console / year line, story card, Full details,
 * performance and options rows, the big name, the saves chip, achievement rows and comments) never cuts text with "…": window
 * text goes through WinFullText (wraps, shrinks to a readable size, then slides; no reserved height), an opened achievement /
 * comment shows its whole text, and the one cut left - the closed story - is marked and shows the More pill whenever it is cut.
 * Fails on the 678d sources (12 Ellipsis uses, 2 WinFullText).
 */
class GameWindowBannerFullText678eTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun bannerShowsTextWhole() {
        val s = source("GameWindowBanner.kt")
        val cuts = Regex("""TextOverflow\.Ellipsis[^\n]*""").findAll(s).map { it.value }.toList()
        assertEquals("only the closed story may still be cut", 1, cuts.size)
        assertTrue("the story cut is marked with where the rest is", cuts.single().contains("qa-ok: TRUNCATE"))
        assertTrue("the More pill follows the story's real overflow", s.contains("storyCut = it.hasVisualOverflow") && s.contains("|| storyCut"))
        assertEquals("WinFullText uses (2 before + 12)", 14, Regex("""WinFullText\(""").findAll(s).count())
        assertTrue("no StepBased autoSize left (FullText fits the name)", !s.contains("TextAutoSize.StepBased"))
        assertTrue("comments are not held to 6 lines", !s.contains("maxLines = 6"))
    }
}
