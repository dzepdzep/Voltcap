package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 711a (CLAUDE.md rule 11, QA R18 / matrix P13): the Game Journey timeline shows each step's title, date and detail
 * whole (FullText: wrap, readable size, then slide). Fails on the 710a sources (3 Ellipsis uses).
 */
class GameJourneyFullText711aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/GameJourneyUi.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noEllipsis() {
        val bad = source().lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
        assertEquals("unjustified ellipses: $bad", 0, bad.size)
    }

    @Test fun timelineTextsUseFullText() {
        val t = source().substringAfter("internal fun GameJourneyTimeline(")
        assertTrue("title", Regex("""FullText\(\s*s\.title,""").containsMatchIn(t))
        assertTrue("date", t.contains("FullText(fmt.format(java.util.Date(it))"))
        assertTrue("detail", Regex("""FullText\(it, [^\n]*maxLines = 3""").containsMatchIn(t))
    }
}
