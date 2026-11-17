package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678i (CLAUDE.md rule 11, QA R18 / matrix P13): the save-sync prompts (the sync toast's title + detail, the Google
 * Drive card's status line, the game row's name, the sync log's time + entry lines and the conflict list's game names)
 * never cut text with "…": each goes through FullText (wraps to its old line count, shrinks to a readable size, then
 * slides) with no reserved height, so the rows keep their heights. Fails on the 678h sources (7 Ellipsis uses, no FullText).
 */
class SyncPromptsFullText678iTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun syncPromptsShowTextWhole() {
        val s = source("SyncPrompts.kt")
        assertEquals("no text cut with an ellipsis", 0, Regex("""TextOverflow\.Ellipsis""").findAll(s).count())
        assertEquals("FullText uses (0 before + 7)", 7, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("every new FullText keeps the old row heights (reserve = false)", 7, Regex("""reserve = false""").findAll(s).count())
        assertTrue("the sync log time keeps its 72 dp column", s.contains("FullText(syncAgo(e.at), color = ShelfTheme.textMuted, fontSize = 11.sp, modifier = Modifier.width(72.dp), reserve = false)"))
        assertTrue("log entries keep their two lines", s.contains("fontSize = 11.sp, maxLines = 2,\n                                reserve = false"))
        assertTrue("game names keep their weight in the row", s.contains("gameName, color = ShelfTheme.textMuted, fontSize = 12.sp, maxLines = 2,\n                        reserve = false, modifier = Modifier.weight(1f)"))
    }
}
