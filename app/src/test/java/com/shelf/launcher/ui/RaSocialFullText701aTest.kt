package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 701a (CLAUDE.md rule 11, QA R18 / matrix P13): the RetroAchievements social cards never cut text with "…" - a
 * friend's last award line, and the Achievement of the Week's title, description and game / console line go through
 * FullText (shrink to a readable size, then slide), no reserved height so the cards keep their spacing. Fails on the
 * 700a sources (4 Ellipsis uses).
 */
class RaSocialFullText701aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/RaSocialUi.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun raSocialShowsTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText($head") + """[\s\S]{0,600}?reserve = false""").findAll(s).count()
        assertEquals("friend's last award", 1, count("\n                                        \"\${aw.kind}: \${aw.title}\""))
        assertEquals("weekly title", 1, count("w.title, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp"))
        assertEquals("weekly description", 1, count("w.description, color = ShelfTheme.textMuted, fontSize = 12.sp"))
        assertEquals("weekly game / console", 1, count("\n                    listOf(w.gameTitle, w.console)"))
    }
}
