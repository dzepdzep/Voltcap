package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 705a (CLAUDE.md rule 11, QA R18 / matrix P13): the cross-bar trophy list never cuts text with "…" - each
 * achievement's title, the focused one's description and meta line, and the comments panel title go through FullText
 * (no reserved height, so the rows keep their spacing). The row title grows with focus, so it slides without auto-fit.
 * Fails on the 704a sources (4 Ellipsis uses).
 */
class CrossTrophiesFullText705aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/CrossTrophies.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun trophyListShowsTextWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun everyFormerlyCutTextUsesFullText() {
        val s = source()
        fun count(head: String) = Regex(Regex.escape("FullText(\n") + """\s*""" + Regex.escape(head) + """[\s\S]{0,300}?reserve = false""").findAll(s).count()
        assertEquals("row title (slides, no auto-fit)", 1, Regex("""FullText\(\s*a\.title \?: "Achievement", color = Color\.White\.copy\(alpha = if \(earned\)[\s\S]{0,300}?autoFit = false, reserve = false""").findAll(s).count())
        assertEquals("description", 1, count("it, color = Color.White.copy(alpha = 0.85f)"))
        assertEquals("meta line", 1, count("trophyMeta(a), color = Color.White.copy(alpha = 0.6f)"))
        assertEquals("comments title", 1, count("a.title ?: \"Achievement\", color = Color.White, fontSize = 14.sp * k"))
    }
}
