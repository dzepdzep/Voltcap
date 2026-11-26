package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 710a (CLAUDE.md rule 11, QA R18 / matrix P13): the game window's performance page shows each row's name and value
 * whole (FullText: wrap, readable size, then slide). The main page's story stays a short preview, marked qa-ok, because the
 * Details page shows the whole story. Fails on the 709a sources (3 unmarked Ellipsis uses).
 */
class GameWindowFullText710aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/GameWindow.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun everyEllipsisIsJustified() {
        val bad = source().lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
        assertEquals("unjustified ellipses: $bad", 0, bad.size)
    }

    @Test fun perfRowsUseFullText() {
        val s = source()
        assertTrue("perf row name", Regex("""FullText\(\s*if \(look\.caps\) row\.label\.uppercase\(\) else row\.label""").containsMatchIn(s))
        assertTrue("perf row value", Regex("""FullText\(\s*row\.value,[\s\S]{0,120}?maxLines = 2""").containsMatchIn(s))
    }

    @Test fun fullStoryStaysOnDetails() {
        val s = source()
        val details = s.substringAfter("private fun WindowDetails(").substringBefore("\n}\n")
        assertTrue("Details page shows the summary without a line limit",
            Regex("""Text\(meta\?\.summary\?\.takeIf \{ it\.isNotBlank\(\) \} \?: "No description yet\.", [^\n]*\)""").find(details)?.value?.contains("maxLines") == false)
    }
}
