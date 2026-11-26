package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 707a (CLAUDE.md rule 11, QA R18 / matrix P13): the home editor's toolbar never cuts text with "…" - the
 * "Edit Home · <widget>" title and the widget's scope line go through FullText (shrink to a readable size, then slide),
 * no reserved height so the toolbar keeps its size. Fails on the 706a sources (2 Ellipsis uses).
 */
class HomeEditLegendFullText707aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/HomeEditLegend.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun toolbarShowsTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun formerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText($head") + """[\s\S]{0,400}?reserve = false""").findAll(s).count()
        assertEquals("toolbar title", 1, count("\n                        \"Edit Home · \" + HomeWidgets.label(sel)"))
        assertEquals("scope line", 1, count("HomeEditSession.scopeOf(sel), color = ShelfTheme.textMuted, fontSize = 10.sp"))
    }
}
