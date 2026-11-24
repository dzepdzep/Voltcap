package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 712a (CLAUDE.md rule 11, QA R18 / matrix P13): the RetroAchievements dialog shows a recent achievement's name, its
 * game and each saved account's name whole (FullText: wrap, readable size, then slide). Fails on the 711a sources (3 Ellipsis uses).
 */
class RaDialogFullText712aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/RetroAchievementsDialog.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noEllipsis() {
        val bad = source().lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
        assertEquals("unjustified ellipses: $bad", 0, bad.size)
    }

    @Test fun textsUseFullText() {
        val s = source()
        assertTrue("achievement title", Regex("""FullText\(\s*achievement\.Title""").containsMatchIn(s))
        assertTrue("game title", Regex("""FullText\(\s*achievement\.GameTitle""").containsMatchIn(s))
        assertTrue("account name", s.contains("FullText(name, color = ShelfTheme.colors.textPrimary"))
    }
}
