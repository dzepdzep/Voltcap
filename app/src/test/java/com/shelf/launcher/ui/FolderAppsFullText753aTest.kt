package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 753a (CLAUDE.md rule 11, QA R18 / matrix P13): the folder menu and top-screen folder preview, the artwork gallery's
 * label, the apps library's app name, a folded settings group's summary, a slider's value, the L1 shortcut names and the
 * Tiles home's achievement count show their texts whole (FullText: wrap, readable size, then slide). Fails on the 752a
 * sources (8 Ellipsis uses in these seven files).
 */
class FolderAppsFullText753aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private val files = listOf(
        "FolderIcons.kt", "ArtworkGallery.kt", "AppsLibraryDialog.kt", "FoldGroup.kt", "SettingsSlider.kt", "ShortcutsPanel.kt"
    )

    @Test fun noEllipsis() {
        for (f in files) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
        // TileHome: the achievement count pill (its game name is a later batch)
        val tile = source("TileHome.kt")
        val pill = tile.substring(tile.indexOf("\"\$earned/\$possible\""), tile.indexOf("\"\$earned/\$possible\"") + 300)
        assertTrue("tile count pill: $pill", !pill.contains("Ellipsis"))
    }

    @Test fun textsUseFullText() {
        val folder = source("FolderIcons.kt")
        assertTrue("folder menu name", Regex("""FullText\(\s*folder\.name""").containsMatchIn(folder))
        assertTrue("folder preview name", Regex("""FullText\(\s*f\.name, color = ShelfTheme\.colors\.textPrimary, fontSize = 24\.sp""").containsMatchIn(folder))
        assertTrue("gallery label", Regex("""FullText\(\s*if \(focused\) items\.getOrNull\(selected\)""").containsMatchIn(source("ArtworkGallery.kt")))
        assertTrue("app name", Regex("""FullText\(\s*app\.name, color = ShelfTheme\.colors\.textPrimary, fontSize = 14\.sp""").containsMatchIn(source("AppsLibraryDialog.kt")))
        assertTrue("group summary", source("FoldGroup.kt").contains("FullText(summary, color = ShelfTheme.textMuted"))
        assertTrue("slider value", Regex("""FullText\(\s*valueText\?\.invoke""").containsMatchIn(source("SettingsSlider.kt")))
        assertTrue("shortcut name", Regex("""FullText\(\s*app\.name,\s*color""").containsMatchIn(source("ShortcutsPanel.kt")))
        assertTrue("tile count", Regex("""FullText\(\s*"\${'$'}earned/\${'$'}possible"""").containsMatchIn(source("TileHome.kt")))
    }
}
