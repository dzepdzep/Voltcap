package com.shelf.launcher.ui

import com.shelf.launcher.data.PlatformInfo
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 762a (P5 polish, Slides): the slide's game count and maker / year line are never cut. Both sat as plain one-line Texts in
 * a box of a fixed share of the slide; at x1.3 on the KONKR (640 x 427 dp) the built-in "MICROSOFT 2005" already needs most of
 * the maker box and a scraped maker or a box made smaller in the slide editor was clipped. They now go through FullText
 * (shrink to the readable minimum, then slide) with no reserved height, so the boxes keep their sizes. Source half fails on 761a.
 */
class SlidesFactsFullText762aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    @Test fun countAndMakerGoThroughFullText() {
        val s = src("ui/PlatformBrowser.kt")
        assertTrue(s.contains("FullText(countText(tile), color = ShelfTheme.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, reserve = false)"))
        assertTrue(s.contains("if (line != null) FullText(line, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, reserve = false)"))
        assertFalse("the old clipping Texts are gone", Regex("""(?<![A-Za-z])Text\(countText\(tile\), color = ShelfTheme\.accent, fontSize = 16\.sp""").containsMatchIn(s))
        assertFalse(s.contains("if (line != null) Text(line,"))
    }

    @Test fun theMakerBoxIsTightAtX13() {
        // the default maker box on the KONKR, and the widest built-in maker line at x1.3 (bold caps ~0.68 em + 1 sp spacing)
        val box = SlideLayout.slideDefaults.getValue(SlideEl.MAKER.name)
        val boxW = 640f * box.w
        val longest = listOf("Xbox360", "Amiga", "3DO", "ColecoVision", "Intellivision").mapNotNull { PlatformInfo.line(it) }.maxOf { it.length }
        val need = longest * (13f * 1.3f * 0.68f + 1.3f)
        assertTrue("x1.3 $need of $boxW dp: tight enough that a longer scraped maker overflows", need > boxW * 0.85f)
    }
}
