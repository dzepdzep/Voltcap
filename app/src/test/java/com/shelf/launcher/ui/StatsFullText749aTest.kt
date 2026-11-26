package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 749a (CLAUDE.md rule 11, QA R18 / matrix P13): the play-stats boxes, the performance page's tiles and the game
 * popup's status rows show their texts whole (FullText: wrap, readable size, then slide). Fails on the 748a sources
 * (6 Ellipsis uses in these three files).
 */
class StatsFullText749aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noEllipsis() {
        for (f in listOf("GamePlayStats.kt", "PerfPage.kt", "GamePopupStatus.kt")) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        val stats = source("GamePlayStats.kt")
        assertTrue("stat value", stats.contains("FullText(value, color = ShelfTheme.colors.textPrimary, fontSize = 16.sp"))
        assertTrue("stat label", stats.contains("FullText(label, color = ShelfTheme.textMuted, fontSize = 11.sp"))
        val perf = source("PerfPage.kt")
        assertTrue("perf value", perf.contains("FullText(value, color = ShelfTheme.colors.textPrimary, fontSize = 22.sp"))
        // The sub line keeps two lines of room so the six tiles stay one size (update 112).
        assertTrue("perf sub keeps its two lines", perf.contains("FullText(sub ?: \" \", color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 2, reserve = true)"))
        val status = source("GamePopupStatus.kt")
        assertTrue("status label", Regex("""FullText\(\s*row\.label,""").containsMatchIn(status))
        assertTrue("status value", status.contains("FullText(row.value, color = ShelfTheme.textMuted, fontSize = 12.sp, maxLines = 2"))
    }
}
