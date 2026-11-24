package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 713a (CLAUDE.md rule 11, QA R18 / matrix P13): the shared dialog chrome shows a text field's value, a menu sheet's
 * title and a chip's label whole (FullText: wrap, readable size, then slide). Fails on the 712a sources (3 Ellipsis uses).
 */
class DialogChromeFullText713aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/DialogChrome.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noEllipsis() {
        val bad = source().lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
        assertEquals("unjustified ellipses: $bad", 0, bad.size)
    }

    @Test fun textsUseFullText() {
        val s = source()
        assertTrue("text field value", Regex("""FullText\(\s*when \{\s*value\.isBlank\(\) -> "Press A to type"""").containsMatchIn(s))
        assertTrue("menu title", Regex("""FullText\(\s*title, color = ShelfTheme\.colors\.textPrimary, fontSize = 20\.sp""").containsMatchIn(s))
        assertTrue("chip label", Regex("""FullText\(\s*label,\s*color = when \{\s*!enabled""").containsMatchIn(s))
    }
}
