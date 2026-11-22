package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Update 691a (CLAUDE.md rule 11, QA R18 / matrix P13): the Twin icons layout never cuts text with "…" - the focused game's
 * name and console / playtime line, the category card's name and hint, the small category labels under the icons, and the
 * Open bar's category / game name go through FullText (shrink to a readable size, then slide), one line as before, with no
 * reserved height so the tray keeps its layout. Fails on the 690a sources (7 Ellipsis uses, 0 FullText).
 */
class TwinIconsFullText691aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun twinIconsShowsTextWhole() {
        val s = source("TwinIcons.kt")
        assertFalse("no text ends in an ellipsis", s.contains("TextOverflow.Ellipsis"))
        assertEquals("FullText uses (0 before + 7)", 7, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("every FullText keeps the tray's layout (reserve = false)", 7, Regex("""reserve = false""").findAll(s).count())
    }

    @Test fun openBarNameIsBoundedSoItCanSlide() {
        val s = source("TwinIcons.kt")
        // Both Open-bar names take only the room left beside the icon, so a long one slides instead of pushing out of the bar.
        assertEquals(2, Regex("""reserve = false,\s*modifier = Modifier\.weight\(1f, fill = false\)""").findAll(s).count())
    }
}
