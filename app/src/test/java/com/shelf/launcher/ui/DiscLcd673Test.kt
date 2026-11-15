package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 673e (D328, Flip 2 x1.3 gate f673g): Disc station's LCD - the readouts' own line heights, the name the lines it needs - so
 * its facts have room; and the story cue of any window is left out, never laid out 0 px high, where its line doesn't fit.
 */
class DiscLcd673Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    private fun discPanel(): String {
        val s = src("WinScenesMore.kt")
        val i = s.indexOf("internal fun DiscPlayerPanel(")
        return s.substring(i, s.indexOf("internal fun discPanelGeometry", i))
    }

    @Test fun theLcdNameTakesTheLinesItNeeds() {
        val body = discPanel()
        assertTrue(body.contains("maxLines = 3, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, reserve = false)"))
    }

    @Test fun theReadoutsHaveTheirOwnLineHeights() {
        val body = discPanel()
        // NOW LOADED / the disc kind, DISC 1 - TRACK 01 / the played line: none left on the theme's 24 sp line.
        assertTrue(Regex("Text\\(\"NOW LOADED\"[^\\n]*lineHeight = 12\\.sp\\)").containsMatchIn(body))
        assertTrue(Regex("Text\\(labels\\.first[^\\n]*lineHeight = 12\\.sp\\)").containsMatchIn(body))
        // (673f: the same lines name the track and the page on a page)
        assertTrue(Regex("labels\\.second[\\s\\S]{0,200}?lineHeight = 13\\.sp").containsMatchIn(body))
        assertTrue(Regex("ctx\\.played\\.uppercase\\(\\)[\\s\\S]{0,200}?lineHeight = 13\\.sp").containsMatchIn(body))
        assertTrue(body.contains("SceneFactsBody(ctx, lcd.copy(alpha = 0.55f), lcd, FontFamily.Monospace, cueLineHeight = 12.sp)"))
    }

    @Test fun theStoryCueIsLeftOutRatherThanSqueezed() {
        val s = src("WinScenesMore.kt")
        val i = s.indexOf("internal fun ColumnScope.SceneFactsBody(")
        val body = s.substring(i, s.indexOf("internal fun sceneCueBack", i))
        assertTrue(body.contains("cueFits = !c.hasBoundedHeight || c.maxHeight >= cueH - 1"))
        // At the window's end whether the facts show or have given way (it moved between More and Less).
        assertTrue(body.contains("Column(Modifier.weight(1f, fill = false).fillMaxWidth().layout { m, c ->"))
        assertTrue(body.contains("sc.canScrollForward && cueFits) FullText(STORY_CUE"))
        assertTrue(body.contains("lineHeight = cueLineHeight,"))
        // Unchanged for every other window: the cue's line height is the theme's unless a window asks.
        assertTrue(body.contains("cueLineHeight: TextUnit = TextUnit.Unspecified"))
        assertFalse(body.contains("FullText(STORY_CUE, color = label, fontSize = 9.sp, lineHeight = 12.sp"))
    }
}
