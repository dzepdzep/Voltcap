package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 694a (CLAUDE.md rule 11, QA R18 / matrix P13): the home row's info panel never cuts text with "…" - the facts line
 * (year · genres · developer · console), the score / time / trophy chips, the summary, the news source line and the news
 * headline go through FullText (shrink to a readable size, then slide), with their old line counts and no reserved height so
 * the panel keeps its layout. Fails on the 693a sources (5 Ellipsis uses, 0 FullText).
 */
class RowInfoPanelFullText694aTest {
    private val inCall = """(?:(?!FullText\()[\s\S])*?"""

    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/RowInfoPanel.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun panelShowsTextWhole() {
        val s = source()
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses", 5, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("panel keeps its layout (reserve = false)", 5, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun twoLineTextsKeepTwoLines() {
        val s = source()
        assertEquals("facts line", 1, Regex("""FullText\(\s*facts\.joinToString$inCall maxLines = 2,\s*reserve = false""").findAll(s).count())
        assertEquals("summary", 1, Regex("""FullText\(\s*summary,$inCall maxLines = 2,\s*reserve = false""").findAll(s).count())
        assertEquals("headline", 1, Regex("""FullText\(\s*shown\?\.title$inCall maxLines = 2,\s*reserve = false""").findAll(s).count())
    }

    @Test fun chipsKeepTheirShape() {
        val s = source()
        assertEquals(1, Regex("""FullText\(\s*text,$inCall reserve = false,\s*modifier = Modifier\s*\.clip\(RoundedCornerShape\(6\.dp\)\)""").findAll(s).count())
    }
}
