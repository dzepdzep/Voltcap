package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 709a (CLAUDE.md rule 11, QA R18 / matrix P13): the R1 quick panel never cuts its text with "…" - the undo
 * strip's "Changed: <setting>", the picked tile's name in the tile editor and a tile caption with spaces go through
 * FullText (wrap, shrink to a readable size, then slide). Fails on the 708a sources (3 Ellipsis uses).
 */
class QuickSettingsFullText709aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/QuickSettings.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noEllipsis() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun formerlyCutTextsUseFullText() {
        val s = source()
        assertTrue("undo strip", Regex("""FullText\(\s*"Changed: \${'$'}undoLabel"""").containsMatchIn(s))
        assertTrue("tile editor name", Regex("""FullText\(p\.label""").containsMatchIn(s))
        assertTrue("spaced tile caption", Regex("""if \(spaced\) FullText\(\s*label,[\s\S]{0,300}?maxLines = 2""").containsMatchIn(s))
        assertEquals("one-word / line-break captions keep their clip-and-shrink path", 1,
            Regex("""maxLines = if \(label\.trim\(\)\.contains\('\\n'\)\) 2 else 1""").findAll(s).count())
    }
}
