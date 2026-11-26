package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 679d (CLAUDE.md rule 11, QA R18, D353): the Terminal home never ends a path, a preview line or a system name in
 * "…". The title-bar path and the "Directory of" header slide whole (D353: "Directory of C:\GAMES\PLAYST…"); the preview's
 * title / SYS / TIME / root name + note / system name / played-last lines go through FullText at one size (autoFit = false,
 * the monospace grid stays) and keep their boxes (reserve = false). termFit stays only for the fixed listing columns, where
 * the line you're on now slides its whole text in every directory (root and systems too, not only games).
 * Fails on the 679c sources (7 Ellipsis uses, the header cut by termFit, no termWhole).
 */
class TerminalFullText679dTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun terminalShowsTextWhole() {
        val s = source("StyleTerminal.kt")
        assertEquals("no text cut with an ellipsis overflow", 0, Regex("""TextOverflow\.Ellipsis""").findAll(s).count())
        assertEquals("FullText uses (0 before + 9)", 9, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("one size everywhere (no shrink in the monospace grid)", 9, Regex("""autoFit = false""").findAll(s).count())
        assertEquals("boxes keep their sizes", 9, Regex("""reserve = false""").findAll(s).count())
        assertTrue("the header path is whole", s.contains("""FullText(" Directory of " + termPath(c), color = tc.dim"""))
        assertTrue("the header is no longer cut to the listing's columns", !s.contains("""termFit(" Directory of """))
        assertTrue("the title-bar path takes the room the name leaves, right-aligned",
            s.contains("FullText(termPath(c) + \" \", color = tc.hiInk, fontSize = style.fontSize, style = style.copy(shadow = null), textAlign = TextAlign.End, autoFit = false, reserve = false, modifier = Modifier.weight(1f))"))
        assertTrue("root lines slide whole when you're on them", s.contains("if (on && marquee && full.length > cols) { cut = true; full } else termFit(full, cols)"))
        assertTrue("system lines slide whole when you're on them", s.contains("head + (if (cut) termWhole(label, room) else termFit(label, room))"))
    }

    @Test fun termWholeKeepsEveryCharacter() {
        assertEquals("AB ", termWhole("AB", 3))
        assertEquals("ABCDEF", termWhole("ABCDEF", 3))
        // termFit unchanged for the unfocused listing columns (Layouts346Test)
        assertEquals("AB…", termFit("ABCDEF", 3))
    }
}
