package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 677a (CLAUDE.md rule 11, QA R18 / matrix P13): the post-game session card and the status-bar session note never
 * cut the game name, play time, achievement count, completion line or milestone with "…"; they wrap, shrink to a readable
 * size, then slide (FullText). Fails on the 676g sources (6 TextOverflow.Ellipsis uses, no FullText).
 */
class SessionSummaryFullText677aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/SessionSummary.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun sessionSummaryDoesNotEllipsize() {
        assertTrue("SessionSummary still uses TextOverflow.Ellipsis", !source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun sessionTextsUseFullTextWithoutReservedLines() {
        // reserve = false: a one-line name in a two-line slot keeps today's card height.
        val s = source()
        assertEquals("FullText uses", 6, Regex("""FullText\(""").findAll(s).count())
        assertEquals("reserve = false", 6, Regex("""reserve = false""").findAll(s).count())
    }
}
