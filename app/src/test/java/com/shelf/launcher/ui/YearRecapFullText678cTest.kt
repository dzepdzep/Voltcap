package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678c (CLAUDE.md rule 11, QA R18 / matrix P13): the year recap (top games, monthly bar labels, the game shelf names +
 * times, stat tiles, facts, achievement and completion rows) never cuts text with "…"; it wraps, shrinks to a readable size,
 * then slides (FullText). reserve = false keeps today's heights. Fails on the 678b sources (9 Ellipsis uses).
 */
class YearRecapFullText678cTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun yearRecapDoesNotEllipsize() {
        val s = source("YearRecapDialog.kt")
        assertTrue("YearRecapDialog still uses TextOverflow.Ellipsis", !s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses", 9, Regex("""FullText\(""").findAll(s).count())
        assertTrue("every FullText keeps today's height (reserve = false)",
            s.split("FullText(").drop(1).all { it.substringBefore("\n\n").substringBefore(")\n").contains("reserve = false") })
    }
}
