package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 758a (CLAUDE.md rule 11, QA R18 / matrix P13): the home's category name and the Tiles layout's game name show their
 * text whole (FullText: readable size, then slide along the line). The category name keeps CategoryNameSwap (rule 14: a
 * category change swaps the name in place); the Tiles name keeps its one-go fitting (no auto-size pop-in) and fixed box.
 * Fails on the 757b sources.
 */
class CategoryTileFullText758aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noUnjustifiedEllipsis() {
        for (f in listOf("LauncherScreen.kt", "TileHome.kt")) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun categoryNameWholeInsideTheSwap() {
        val s = source("LauncherScreen.kt")
        val swap = s.indexOf("CategoryNameSwap(catLabel, state.appearance.categoryAnim) { label ->")
        assertTrue("swap kept", swap > 0)
        val block = s.substring(swap, swap + 2200)
        assertTrue("category name through FullText", Regex("""FullText\(\n\s*label,\n\s*color = Color\.White,\n\s*fontSize = catSize,""").containsMatchIn(block))
    }

    @Test fun tileNameKeepsItsFitAndBox() {
        val s = source("TileHome.kt")
        assertTrue("tile name through FullText", Regex("""FullText\(\n\s*shownName,""").containsMatchIn(s))
        assertTrue("sized in one go, fixed box", s.contains("maxLines = 2,\n                    autoFit = false, reserve = false,"))
        assertTrue("still rises on change", s.contains(".riseOnChange(focusedGame.stableKey, floor = 0.5f, ms = 180)"))
    }
}
