package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 699a (CLAUDE.md rule 11, QA R18 / matrix P13): the achievement hub screens never cut text with "…" - a game's
 * title, its progress line and note (e.g. "Mastered") in the progress rows, and an unlocked achievement's title and its
 * game · console line in the timeline go through FullText (shrink to a readable size, then slide), no reserved height so
 * the rows keep their spacing. Fails on the 698a sources (5 Ellipsis uses).
 */
class HubScreensFullText699aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/HubScreens.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun hubScreensShowTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText($head") + """[^()]*?(\([^()]*\)[^()]*?)*reserve = false""").findAll(s).count()
        assertEquals("progress row game title", 1, count("title, "))
        assertEquals("progress line", 1, count("line, "))
        assertEquals("progress note", 1, count("note, "))
        assertEquals("timeline achievement title", 1, count("a.Title ?: \"Achievement\", "))
        assertEquals("timeline game / console line", 1, count("listOfNotNull(a.GameTitle, a.ConsoleName).joinToString(\" · \"), "))
    }
}
