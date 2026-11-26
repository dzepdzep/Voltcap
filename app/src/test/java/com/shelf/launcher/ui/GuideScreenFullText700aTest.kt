package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 700a (CLAUDE.md rule 11, QA R18 / matrix P13): the game guide screen never cuts text with "…" - the guide's
 * title, its attribution / source line, the section titles in the contents list, and a guide row's title and subtitle in
 * the guide picker go through FullText (shrink to a readable size, then slide), no reserved height so the rows keep their
 * spacing. Fails on the 699a sources (5 Ellipsis uses).
 */
class GuideScreenFullText700aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/GuideScreen.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun guideScreenShowsTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText($head") + """[\s\S]{0,600}?reserve = false""").findAll(s).count()
        assertEquals("guide title", 1, count("\n                guide?.title ?: (\"Guide · \""))
        assertEquals("attribution / source line", 1, count("\n                    listOfNotNull(guide.attribution"))
        assertEquals("contents section title", 1, count("\n                                        (if (isCur) \"● \""))
        assertEquals("guide row title", 1, count("title, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp"))
        assertEquals("guide row subtitle", 1, count("it, color = ShelfTheme.textMuted, fontSize = 11.sp"))
    }
}
