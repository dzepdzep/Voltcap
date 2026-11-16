package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 676i (owner 16 Nov, option C): Cube's status bar is one facet panel, not separate outlined boxes; no other layout changes. */
class CubeFacet676iTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()

    @Test fun cubeUsesTheFacetOnly() {
        val s = src("StatusLooks.kt")
        val cube = s.substring(s.indexOf("HomeLayout.CUBE -> Features.cubeTint"), s.indexOf("HomeLayout.MEMCARD ->"))
        assertTrue(cube.contains("StatusLook(BarBacking.FACET"))
        assertTrue(!cube.contains("BarBacking.SEGMENTS"))
        // only Cube wears it: every layout keeps its own identity
        assertTrue(s.split("StatusLook(BarBacking.FACET").size == 2)
        assertTrue(src("HomeChrome.kt").contains(".statusContainer(look, atBottom)"))
    }
}

/** 676m: Cube's A / X / Y hints wrap whole, never squeezed ("Achievements" was cut to "Achieve" at x1.3 on the Thor). */
class CubeHints676mTest {
    @Test fun hintsWrapWhole() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/CubeHub.kt", "app/src/main/java/com/shelf/launcher/ui/CubeHub.kt").map { File(it) }.first { it.exists() }.readText()
        val i = s.indexOf("HubHint(\"Y\", \"Achievements\")")
        val block = s.substring(s.lastIndexOf("FlowRow(", i), i)
        assertTrue(block.contains("Arrangement.spacedBy(12.dp, Alignment.End)"))
        assertTrue(s.contains("Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, softWrap = false)"))
    }
}

/** 676n (Thor x1.3 t676n_x13): the menu hint is never cut, and a short panel keeps the cover flow instead of two-line reserves. */
class CubeShort676nTest {
    private val s = listOf("src/main/java/com/shelf/launcher/ui/CubeHub.kt", "app/src/main/java/com/shelf/launcher/ui/CubeHub.kt").map { File(it) }.first { it.exists() }.readText()

    @Test fun menuHintWrapsInHalves() {
        assertTrue(!s.contains("\"▲ ▼ Choose   Ⓐ Open\""))
        assertTrue(s.contains("listOf(\"▲ ▼ Choose\", \"Ⓐ Open\")"))
    }

    @Test fun shortPanelGivesTheCoversRoom() {
        val lib = s.substring(s.indexOf("private fun HubLibrary"), s.indexOf("private fun HubHint"))
        assertTrue(lib.contains("val lines = if (maxHeight - fixed < CUBE_COVERS_MIN) 1 else 2"))
        // one line still shown whole: FullText shrinks, then slides (rule 11) and reserves its line (Update 460)
        assertTrue(lib.contains("if (lines == 1) FullText(\n                    titles.display(g.name)"))
        assertTrue(lib.contains("if (lines == 1) FullText(line,"))
        assertTrue(!lib.contains("Ellipsis"))
    }
}
