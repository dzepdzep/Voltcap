package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 708a (CLAUDE.md rule 11, QA R18 / matrix P13): the second screen's folder games page never cuts a game's name
 * with "…" - the list row, the missing-cover placeholder and the name under a tile go through FullText (wrap, shrink to a
 * readable size, then slide). Fails on the 707a sources (3 Ellipsis uses).
 */
class SecondGamesPageFullText708aTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/SecondGamesPage.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun gameNamesShowWhole() {
        assertFalse("no text ends in an ellipsis", source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun formerlyCutNamesUseFullText() {
        val s = source()
        val heads = Regex("""FullText\(\s*gameDisplayName\(g\.name\)[^)]*?fontSize = (\d+)\.sp[\s\S]{0,200}?maxLines = (\d)""")
            .findAll(s).map { it.groupValues[1] + "/" + it.groupValues[2] }.toList()
        assertEquals("list row, placeholder, tile label", listOf("13/2", "10/4", "10/1"), heads)
        assertEquals("only the centred placeholder skips the reserve", 1, Regex("""maxLines = 4[^\n]*reserve = false""").findAll(s).count())
    }
}
