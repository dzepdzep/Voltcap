package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 695a (CLAUDE.md rule 11, QA R18 / matrix P13): the Twin desk layout never cuts text with "…" - the calendar's
 * month title, the focused game's console · playtime line, a list row's console name and the bottom bar's category / zone
 * name go through FullText (shrink to a readable size, then slide), one line each, no reserved height so the desk keeps
 * its layout. Fails on the 694a sources (4 Ellipsis uses, 0 FullText).
 */
class TwinDeskFullText695aTest {
    private val inCall = """(?:(?!FullText\()[\s\S])*?"""

    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/TwinDesk.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun deskShowsTextWhole() {
        val s = source()
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses", 4, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("desk keeps its layout (reserve = false)", 4, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun eachTextStaysOneLine() {
        val s = source()
        assertEquals("month title", 1, Regex("""FullText\(month\.title,$inCall maxLines = 1, reserve = false""").findAll(s).count())
        assertEquals("console + playtime", 1, Regex("""FullText\(\s*titles\.console\(g\.platform\) \+ " · "$inCall maxLines = 1, reserve = false""").findAll(s).count())
        assertEquals("row console", 1, Regex("""FullText\(titles\.console\(g\.platform\),$inCall maxLines = 1,""").findAll(s).count())
        assertEquals("bar label keeps its centred slot", 1,
            Regex("""FullText\(\s*if \(inBar\)$inCall maxLines = 1, textAlign = TextAlign\.Center, reserve = false, modifier = Modifier\.weight\(1f\)""").findAll(s).count())
    }
}
