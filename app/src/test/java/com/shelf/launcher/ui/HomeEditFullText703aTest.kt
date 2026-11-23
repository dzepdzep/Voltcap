package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 703a (CLAUDE.md rule 11, QA R18 / matrix P13): the home editor never cuts text with "…" - the inspector's widget
 * name and scope line, and the widget list's names and status lines go through FullText (shrink to a readable size, then
 * slide), no reserved height so the panel keeps its spacing. Fails on the 702a sources (4 Ellipsis uses).
 */
class HomeEditFullText703aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/HomeEditSession.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun homeEditorShowsTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText($head") + """[\s\S]{0,400}?reserve = false""").findAll(s).count()
        assertEquals("inspector widget name", 1, count("HomeWidgets.label(id), color = ShelfTheme.colors.textPrimary, fontSize = 15.sp"))
        assertEquals("inspector scope line", 1, count("HomeEditSession.scopeOf(id), color = ShelfTheme.textMuted, fontSize = 11.sp"))
        assertEquals("list widget name", 1, count("\n                        (if (sel) \"● \" else \"\") + HomeWidgets.label(id)"))
        assertEquals("list status line", 1, count("\n                        listOfNotNull(if (!on) \"Hidden\" else null"))
    }
}
