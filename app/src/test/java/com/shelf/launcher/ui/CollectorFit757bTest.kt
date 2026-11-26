package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 757b (CLAUDE.md rule 11, QA R18 / matrix P13): the printed case back draws its detail lines and its foot note whole
 * (smaller, then narrower - [wholeLineFit]) instead of TextUtils.ellipsize; a custom layout's text pieces and the Magazine
 * story no longer end on a bare "…" (FullText / the "Full story on Details" cue); the two remaining ellipses (unfocused
 * fixed-height names, StoryLines) carry their documented exception. Fails on the 757a sources.
 */
class CollectorFit757bTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    /** A line whose width is proportional to its size: 10 px per size unit. */
    private val tenPerUnit: (Float) -> Float = { it * 10f }

    @Test fun fitsAtFullSize() {
        assertEquals(20f to 1f, wholeLineFit(20f, 14f, 300f, tenPerUnit))
    }

    @Test fun shrinksToFitAboveTheMinimum() {
        val (size, scale) = wholeLineFit(20f, 14f, 160f, tenPerUnit)
        assertEquals(16f, size, 0.001f)
        assertEquals(1f, scale, 0.001f)
        assertTrue(tenPerUnit(size) * scale <= 160f + 0.001f)
    }

    @Test fun belowTheMinimumItIsDrawnNarrowerButWhole() {
        val (size, scale) = wholeLineFit(20f, 14f, 70f, tenPerUnit)
        assertEquals(14f, size, 0.001f)
        assertEquals(0.5f, scale, 0.001f)
        assertTrue(tenPerUnit(size) * scale <= 70f + 0.001f)
    }

    @Test fun sourcesDrawWholeOrJustify() {
        val backs = source("CollectorBacks.kt")
        assertFalse("no TextUtils.ellipsize on the case back", backs.contains("TextUtils.ellipsize("))
        assertEquals("both drawn lines go through drawWhole", 2, Regex("""drawWhole\(c, """).findAll(backs).count())
        for (f in listOf("ScrollingName.kt", "WholeRows.kt", "StyleCustom.kt", "StyleMagazine.kt")) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
        assertTrue("custom text piece", source("StyleCustom.kt").contains("FullText(\n            text, color = sceneColor(p.color ?: \"#FFFFFF\"), fontSize = fontSp, maxLines = p.maxLines, reserve = false,"))
        val mag = source("StyleMagazine.kt")
        assertTrue("magazine story cue", mag.contains("if (story != null && storyCut) FullText(\n                    STORY_CUE,"))
        assertTrue("cue set from the story's own layout", mag.contains("onTextLayout = { if (it.hasVisualOverflow) storyCut = true }"))
    }
}
