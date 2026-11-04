package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 456 (owner, RP Classic: "Continue playing", "Game Boy Advance", "Super Nintendo Entertainment System" tabs lost their
 * plate and were cut off / covered by the "+N ›" hint): a Pocket Quest tab's name is bounded by the bar's room, wraps onto a
 * second line, steps down to a readable minimum and only then slides — never measured as wide as the whole bar.
 */
class PqTabs456Test {
    private val src: String = listOf(
        File("../src_copy/ui/StylePocketQuest.kt"), File("src/main/java/com/shelf/launcher/ui/StylePocketQuest.kt"),
        File("app/src/main/java/com/shelf/launcher/ui/StylePocketQuest.kt"),
    ).first { it.exists() }.readText()
    private val tabs = src.substringAfter("private fun PqTabs(").substringBefore("/** Update 456: the widest")

    @Test fun nameBoundedByTheBar() {
        assertTrue(tabs.contains("val textMax = minOf("))
        assertTrue(tabs.contains("Modifier.widthIn(max = textMax)"))
        assertTrue(tabs.contains("StableFullText("))
        assertTrue(tabs.contains("maxLines = 2"))
    }

    @Test fun noUnboundedOneLineName() {
        assertFalse(tabs.contains("maxLines = 1, softWrap = false"))
    }
}
