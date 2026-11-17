package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678j (CLAUDE.md rule 11, QA R18 / matrix P13): the category screen (PlatformBrowser: Slides logo box + name line +
 * last-played line, Stage logo box + name line, the card's name + game count) never cuts text with "…": each goes through
 * FullText (same line count and readable shrink as before, then slides) with no reserved height, so the boxes keep their
 * sizes. Fails on the 678i sources (7 Ellipsis uses, no FullText).
 */
class PlatformBrowserFullText678jTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun categoryScreenShowsTextWhole() {
        val s = source("PlatformBrowser.kt")
        assertEquals("no text cut with an ellipsis", 0, Regex("""TextOverflow\.Ellipsis""").findAll(s).count())
        assertEquals("FullText uses (0 before + 7)", 7, Regex("""(?<![A-Za-z])FullText\(""").findAll(s).count())
        assertEquals("every new FullText keeps the old box sizes (reserve = false)", 7, Regex("""reserve = false""").findAll(s).count())
        assertTrue("the logo-less name keeps its two lines and tight spacing",
            s.contains("FullText(tile.label, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 2, lineHeight = androidx.compose.ui.unit.TextUnit(1.059f, androidx.compose.ui.unit.TextUnitType.Em), reserve = false)"))
        assertTrue("the stage name stays centred in its padded box",
            s.contains("FullText(t.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(14.dp), reserve = false)"))
        assertTrue("the game count stays one line", s.contains("FullText(countText(tile), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, reserve = false)"))
        // (CategoryLooks527Test: the name box still only shows with a logo picture or in the editor)
        assertTrue(s.contains("if (art.logo != null || editing) Box(Modifier.offset(boxW * name.x, boxH * name.y)"))
    }
}
