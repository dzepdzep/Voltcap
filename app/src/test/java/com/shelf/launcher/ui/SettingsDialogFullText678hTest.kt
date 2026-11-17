package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678h (CLAUDE.md rule 11, QA R18 / matrix P13): Settings never cuts text with "…". The page / group titles, search
 * results and their section path, the theme strip line, theme and tab captions, the ScreenScraper look-up lines, the custom
 * music name and the RetroAchievements cards (game names, console line, latest unlock, stat values) go through FullText
 * (wraps, shrinks to a readable size, then slides). Rows keep their heights (reserve = false), except the theme caption,
 * whose two reserved lines replace the old minLines = 2. Fails on the 678g sources (17 Ellipsis uses, no FullText).
 */
class SettingsDialogFullText678hTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun settingsShowTextWhole() {
        val s = source("SettingsDialog.kt")
        assertEquals("no text cut with an ellipsis", 0, Regex("""TextOverflow\.Ellipsis""").findAll(s).count())
        assertEquals("FullText uses (0 before + 17)", 17, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("16 keep their old heights (reserve = false)", 16, Regex("""reserve = false""").findAll(s).count())
        assertTrue("page title", s.contains("FullText(title, color = ShelfTheme.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, reserve = false)"))
        assertTrue("search result label", s.contains("FullText(e.label, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, reserve = false)"))
        assertTrue("group title", s.contains("FullText(openGroup.label, color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, reserve = false)"))
        // The theme caption keeps two lines of room (it had minLines = 2) and its 1.182 em spacing, so the grid stays even.
        val cap = s.indexOf("            theme.label,\n")
        assertTrue("theme caption found", cap > 0)
        val capCall = s.substring(cap, s.indexOf("\n        )", cap))
        assertTrue(capCall.contains("TextUnit(1.182f, androidx.compose.ui.unit.TextUnitType.Em), maxLines = 2"))
        assertFalse("reserved lines, not minLines", capCall.contains("minLines") || capCall.contains("reserve = false"))
        assertTrue("stat values", s.contains("FullText(value, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, reserve = false)"))
    }
}
