package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 692a (CLAUDE.md rule 11, QA R18 / matrix P13): the shared game card (classic row and every layout that draws GameCard)
 * never cuts text with "…" - the NEW unlock pill, the trophy count, the blank card's name, the wide capsule's title, console
 * line and "Played" line, and the recent-unlock title go through FullText (shrink to a readable size, then slide), with their
 * old line counts and no reserved height so the cards keep their layout. Fails on the 691a sources (7 Ellipsis uses, 1 FullText).
 */
class GameCardFullText692aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun gameCardShowsTextWhole() {
        val s = source("GameCard.kt")
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses (1 before + 7)", 8, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("the 7 new FullTexts keep the card's layout (reserve = false)", 7, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun blankCardNameKeepsItsLineCount() {
        val s = source("GameCard.kt")
        assertEquals(1, Regex("""FullText\(\s*text = title,[^)]*maxLines = NO_ART_NAME_LINES""").findAll(s).count())
        assertEquals(1, Regex("""FullText\(\s*text = title,[^)]*maxLines = if \(tight\) 1 else 2""").findAll(s).count())
    }

    @Test fun recentUnlockSlidesInsideItsRow() {
        val s = source("GameCard.kt")
        assertEquals(1, Regex("""recentUnlockTitle \?: "No recent unlocks",[^)]*reserve = false,\s*modifier = Modifier\.weight\(1f, fill = false\)""").findAll(s).count())
    }
}
