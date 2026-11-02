package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 455: the Pocket Ribbon upper screen shows a larger, raised cartridge with the game's title and one quiet info line
 * (platform · year · last played) below it — no left blur panel, tint or extra buttons; missing metadata simply drops out.
 */
class RibbonTop455Test {
    private val src: String = listOf(
        File("../src_copy/ui/PocketRibbon.kt"), File("src/main/java/com/shelf/launcher/ui/PocketRibbon.kt"),
        File("app/src/main/java/com/shelf/launcher/ui/PocketRibbon.kt"),
    ).first { it.exists() }.readText()

    @Test fun captionUnderTheArt() {
        assertTrue(src.contains("RIBBON_CAPTION"))
        assertTrue(src.contains("titles.display(g.name)"))
        assertTrue("missing year / last played drop out", src.contains("listOfNotNull(titles.console(g.platform)"))
    }

    @Test fun noBlurPanelOrTint() {
        val top = src.substringAfter("fun PocketRibbonTop").substringBefore("\n}\n")
        assertFalse(top.contains("blur("))
        assertFalse(top.contains("Brush.horizontalGradient"))
    }
}
