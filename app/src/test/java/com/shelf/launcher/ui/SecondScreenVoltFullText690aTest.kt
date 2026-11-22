package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 690a (CLAUDE.md rule 11, QA R18 / matrix P13): the Volt second-screen companion never cuts text with "…" - the
 * clock beside the page tabs, the game name + console on the progress page, the achievement filter labels, the achievement
 * titles / descriptions in the list and on the detail card, and the console label on the game card go through FullText
 * (wrap to their old line count, shrink to a readable size, then slide), with no reserved height so the panels keep their
 * layout. Fails on the 689a sources (8 Ellipsis uses, 0 FullText).
 */
class SecondScreenVoltFullText690aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun voltSecondScreenShowsTextWhole() {
        val s = source("SecondScreenVolt.kt")
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses (0 before + 8)", 8, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("every FullText keeps the panel's layout (reserve = false)", 8, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun oldLineCountsKept() {
        val s = source("SecondScreenVolt.kt")
        // Names / titles keep the lines they had; descriptions two, the detail title three.
        assertEquals(1, Regex("""FullText\(gameDisplayName\(game\.name\)[^\n]*maxLines = 2""").findAll(s).count())
        assertEquals(1, Regex("""FullText\(a\.description\.orEmpty\(\)[^\n]*maxLines = 2""").findAll(s).count())
        assertEquals(1, Regex("""FullText\(a\.title\.orEmpty\(\)[^\n]*fontSize = 15\.sp[^\n]*maxLines = 3""").findAll(s).count())
    }
}
