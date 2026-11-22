package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 696a (CLAUDE.md rule 11, QA R18 / matrix P13): the home widget board never cuts an app's name with "…" - the
 * sound widget's per-app rows and the single-app volume row go through FullText (shrink to a readable size, then slide),
 * one line each, no reserved height so the volume bars under them stay put. Fails on the 695a sources (2 Ellipsis uses).
 */
class WidgetBoardFullText696aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/WidgetBoard.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun boardShowsTextWhole() {
        val s = source()
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
    }

    @Test fun appNamesStayOneLineBesideTheirVolume() {
        val s = source()
        assertEquals("app rows", 1, Regex("""FullText\(a\.label, [^\n]*maxLines = 1, reserve = false, modifier = Modifier\.weight\(1f\)\)""").findAll(s).count())
        assertEquals("single app row", 1, Regex("""FullText\(label, [^\n]*maxLines = 1, reserve = false, modifier = Modifier\.weight\(1f\)\)""").findAll(s).count())
    }
}
