package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 693a (CLAUDE.md rule 11, QA R18 / matrix P13): the volume mixer never cuts text with "…" - its title, status line,
 * stream labels, app names, app notes ("Playing …"), volume values and the app picker's names go through FullText (shrink to a
 * readable size, then slide), with their old line counts and no reserved height so the rows keep their layout.
 * Fails on the 692b sources (7 Ellipsis uses, 0 FullText).
 */
class MixerFullText693aTest {
    private val inCall = """(?:(?!FullText\()[\s\S])*?"""

    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/MixerActivity.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun mixerShowsTextWhole() {
        val s = source()
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses", 7, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("rows keep their layout (reserve = false)", 7, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun statusLineKeepsTwoLines() {
        val s = source()
        assertEquals(1, Regex("""FullText\(\s*when \{\s*!AppMixer\.available$inCall maxLines = 2, reserve = false""").findAll(s).count())
    }

    @Test fun weightedLabelsKeepTheirWeight() {
        val s = source()
        assertEquals(2, Regex("""FullText\(label,$inCall Modifier\.weight\(1f\)""").findAll(s).count())
    }
}
