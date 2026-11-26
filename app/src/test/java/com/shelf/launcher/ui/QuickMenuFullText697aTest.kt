package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 697a (CLAUDE.md rule 11, QA R18 / matrix P13): the in-game quick menu never cuts text with "…" - the running
 * game's title, missable achievements, each achievement's title / description and paired Bluetooth device names go
 * through FullText (shrink to a readable size, then slide), no reserved height so the rows keep their spacing.
 * Fails on the 696a sources (5 Ellipsis uses).
 */
class QuickMenuFullText697aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/QuickMenuActivity.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun quickMenuShowsTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText($head, ") + """[^\n]*reserve = false\)""").findAll(s).count()
        assertEquals("game title", 1, count("s.gameTitle"))
        assertEquals("missable lines", 1, count("\"• \$it\""))
        assertEquals("achievement title", 1, count("title"))
        assertEquals("achievement description", 1, count("description"))
        assertEquals("bluetooth device name", 1, count("runCatching { d.name }.getOrNull() ?: d.address"))
    }
}
