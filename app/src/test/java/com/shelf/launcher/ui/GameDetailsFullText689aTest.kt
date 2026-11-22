package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 689a (CLAUDE.md rule 11, QA R18 / matrix P13): the classic game details window never cuts text with "…" - the
 * banner title, the info line, "Last session", the stat labels / values, the page tabs, the milestone line, the achievement
 * progress strip and the inline achievement titles / descriptions go through FullText (wrap to their old line count, shrink
 * to a readable size, then slide), with no reserved height so the window keeps its layout; RetroAchievements comments wrap
 * whole inside the scrolling page. The summary stays collapsed only where the More chip can open the rest: a summary
 * shorter than the More threshold that still ran past 4 lines (narrow window, x1.3) was cut with no way to read it.
 * Fails on the 688b sources (13 Ellipsis uses, collapsed summary without a More chip, 4-line comments).
 */
class GameDetailsFullText689aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun detailsWindowShowsTextWhole() {
        val s = source("GameDetailsOverlay.kt")
        val ellipsis = Regex("""TextOverflow\.Ellipsis.*""").findAll(s).map { it.value }.toList()
        assertEquals("only the collapsed summary (opened by More) may still end in an ellipsis", 1, ellipsis.size)
        assertTrue("that one is marked with where the full text is", ellipsis[0].contains("qa-ok: TRUNCATE") && ellipsis[0].contains("More"))
        assertEquals("FullText uses (0 before + 11)", 11, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("every FullText keeps the window's layout (reserve = false)", 11, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun summaryIsCollapsedOnlyWhenMoreCanOpenIt() {
        val s = source("GameDetailsOverlay.kt")
        assertTrue(s.contains("maxLines = if (summaryExpanded || !detailsHasMore(metadata, sections)) Int.MAX_VALUE else SummaryCollapsedLines"))
        assertFalse(s.contains("maxLines = if (summaryExpanded) Int.MAX_VALUE else SummaryCollapsedLines"))
        // The More chip is drawn exactly when detailsHasMore says so - the same test the collapse now uses.
        assertTrue(s.contains("if (detailsHasMore(metadata, sections)) {"))
    }

    @Test fun commentsWrapWhole() {
        val s = source("GameDetailsOverlay.kt")
        assertFalse("no 4-line cap on a comment", s.contains("maxLines = 4"))
        assertTrue("the details page scrolls, so a long comment has room", s.contains(".verticalScroll(scrollState)"))
    }
}
