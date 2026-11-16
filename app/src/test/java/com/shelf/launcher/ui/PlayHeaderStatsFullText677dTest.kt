package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 677d (CLAUDE.md rule 11, QA R18 / matrix P13): the play header (continue line, stat chips, Play button, category
 * above the title) and the Stats page (tiles, weekly bars, per-game bars) never cut text with "…"; they wrap, shrink to a
 * readable size, then slide (FullText). reserve = false keeps today's heights. Fails on the 677c sources (11 Ellipsis uses).
 */
class PlayHeaderStatsFullText677dTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun playHeaderDoesNotEllipsize() {
        val s = source("PlayHeader.kt")
        assertTrue("PlayHeader still uses TextOverflow.Ellipsis", !s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses (1 older + 6)", 7, Regex("""FullText\(""").findAll(s).count())
    }

    @Test fun statsContentDoesNotEllipsize() {
        val s = source("StatsContent.kt")
        assertTrue("StatsContent still uses TextOverflow.Ellipsis", !s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses", 5, Regex("""FullText\(""").findAll(s).count())
    }
}
