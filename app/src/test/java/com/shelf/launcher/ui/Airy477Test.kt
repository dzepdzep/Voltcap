package com.shelf.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 477 (D266): Airy's window keeps its facts — one row of round keys along the bottom, the rest behind More in a pill row. */
class Airy477Test {
    private fun a(l: String) = WinAction(l, Icons.Filled.Info, {})
    private fun src(name: String): String =
        listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
            .map(::File).first { it.exists() }.readText()

    @Test fun moreClosedKeepsInspectAndMoreAlongTheBottom() {
        val (bottom, well) = airySplit(listOf(a("Inspect"), a("More")))
        assertEquals(listOf("Inspect", "More"), bottom.map { it.label }); assertTrue(well.isEmpty())
    }

    @Test fun moreOpenPutsTheRestInTheWellNothingLost() {
        val tools = listOf("Options", "Achievements", "Save sync", "Manual", "Inspect", "Less").map(::a)
        val (bottom, well) = airySplit(tools)
        assertEquals(listOf("Inspect", "Less"), bottom.map { it.label })
        assertEquals(listOf("Options", "Achievements", "Save sync", "Manual"), well.map { it.label })
        assertEquals(tools.size, bottom.size + well.size)
    }

    @Test fun aWindowWithoutMoreKeepsEveryKeyAlongTheBottom() {
        val tools = listOf("Options", "Achievements", "Inspect").map(::a)
        assertEquals(tools, airySplit(tools).first)
    }

    // 477a put More's keys in the well (squeezed to 14 px on the KONKR), 477b as small round keys after the others (the row
    // wrapped and the chips' second line was dropped): More's keys are a short pill row of their own.
    @Test fun moreKeysAreAPillRowNotTheWellOrASecondRowOfRoundKeys() {
        val s = src("WinScenes.kt")
        val airy = s.substring(s.indexOf("private fun AiryPanel"), s.indexOf("internal fun airySplit"))
        assertTrue(airy.contains("secondary.forEach { a -> AiryPill(a, accent) }"))
        assertTrue(!airy.contains("small = true"))
    }

    // A long name shrinks, then slides — never stopped at a line count (k477f: cut at 3 lines).
    @Test fun theNameIsNeverCut() {
        val s = src("WinScenes.kt")
        val airy = s.substring(s.indexOf("private fun AiryPanel"), s.indexOf("internal fun airySplit"))
        assertTrue(airy.contains("FullText(ctx.name"))
    }

    // A sliding name is reachable (rule 11): the QA gate is told so instead of reading it as cut (k477i).
    @Test fun aSlidingTextIsMarkedForTheGate() {
        assertTrue(src("FitText.kt").contains("this[QaClip.Slides] = true"))
    }

    @Test fun airyIsACompactMoreWindow() {
        assertTrue(WinScene.AIRY_PANEL in MORE_SCENES)
    }
}
