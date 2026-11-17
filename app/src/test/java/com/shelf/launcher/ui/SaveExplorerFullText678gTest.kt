package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678g (CLAUDE.md rule 11, QA R18 / matrix P13): the Save Vault (header name + count line, "Not connected" + the
 * connect problem, console counts, save rows, the last-sync line, the game info bar and the Browse saves button) never cuts
 * text with "…": every one of those labels goes through FullText (wraps, shrinks to a readable size, then slides) with no
 * reserved height, so the rows keep the heights they had. Fails on the 678f sources (13 Ellipsis uses, no FullText).
 */
class SaveExplorerFullText678gTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun saveVaultShowsTextWhole() {
        val s = source("SaveExplorer.kt")
        assertEquals("no text cut with an ellipsis", 0, Regex("""TextOverflow\.Ellipsis""").findAll(s).count())
        assertEquals("FullText uses (0 before + 13)", 13, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("every new FullText keeps the old row heights (reserve = false)", 13, Regex("""reserve = false""").findAll(s).count())
        assertTrue("the vault's name", s.contains("com.shelf.launcher.data.SAVES_NAME, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,\n                            reserve = false"))
        assertTrue("the connect problem keeps its 5 lines and line height", s.contains("problem, color = Color(0xFFFFB4A0), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 5,"))
        assertTrue("save rows stay one line each", s.contains("FullText(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, reserve = false)"))
        assertTrue("Browse saves", s.contains("FullText(\"Browse saves\", color = onAccentColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold, reserve = false)"))
    }
}
