package com.shelf.launcher.ui

import com.shelf.launcher.ui.theme.ColorOrigin
import com.shelf.launcher.ui.theme.describeColorOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 490 (staging only): S1 settings groups, settings search coverage, colour origin readout. */
class Batch490Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    /** The sidebar's category names in their order, read from the source (no Compose needed). */
    private fun order(): List<String> {
        val s = src("ui/SettingsDialog.kt")
        val block = s.substring(s.indexOf("private val CATEGORIES = listOf("), s.indexOf("internal val SETTINGS_CATEGORY_NAMES"))
        return Regex("SettingsCategory\\(\"([^\"]+)\"").findAll(block).map { it.groupValues[1] }.toList()
    }

    @Test fun s1GroupsKeepTheOrderAndAreContiguous() {
        val o = order()
        assertEquals(21, o.size)
        // nothing moved: the order the sidebar had before update 490
        assertEquals(listOf("Home", "Game look", "Theme", "Screensaver", "Categories", "Achievements", "Music & sound", "Controls",
            "Library", "Accounts", "Emulators", "Screens", "Metadata", "Performance", "Overlay", "Sync", "Permissions", "Device",
            "Diagnostics", "Backup", "Updates & downloads"), o)
        val starts = SETTINGS_GROUPS.map { o.indexOf(it.second) }
        assertTrue("every group starts at a real category", starts.all { it >= 0 })
        assertEquals("groups follow the sidebar order", starts.sorted(), starts)
        assertEquals("the first group starts at the top", 0, starts.first())
        assertEquals("Play & sound", settingsGroupOf("Music & sound", o))
        assertEquals("Look", settingsGroupOf("Categories", o))
        assertEquals("System & data", settingsGroupOf("Updates & downloads", o))
        assertFalse(settingsGroupOf("Music & sound", o) == "System & data")
        // headers only: category labels unchanged (search, QaExplore and saved sub-pages key on them)
        val sd = src("ui/SettingsDialog.kt")
        assertTrue(sd.contains("val groupTitle = settingsGroupStartingAt(entry.label)") && sd.contains("groupTitle?.let { g -> SettingsGroupHeader("))
    }

    private val entry = Regex("E\\(\"((?:[^\"\\\\]|\\\\.)*)\",\\s*\"([^\"]*)\",\\s*(null|\"[^\"]*\"),\\s*(null|\"[^\"]*\"),\\s*(null|\"[^\"]*\")\\)")

    @Test fun searchIndexPointsAtRealSectionsAndCoversNewControls() {
        val o = order().toSet()
        val idx = src("ui/SettingsIndex.kt")
        val all = entry.findAll(idx).map { it.groupValues }.toList()
        assertTrue(all.size > 850)
        all.forEach { assertTrue("section of ${it[1]}: ${it[2]}", it[2] in o) }
        val labels = all.map { it[1] }
        // the same label twice in one section would list twice
        assertEquals(labels.size, all.map { it[1] + "|" + it[2] }.toSet().size)
        for (want in listOf("Game spacing (room between the games, this layout)", "Case stacks: Game spacing", "Colours for this layout",
                "Cube menu: Cube colour", "Battery watcher", "Its motion (default for all layouts)", "Second screen tools"))
            assertTrue("search finds $want", want in labels)
        // layout options name their layout and open that layout's own group
        assertTrue(idx.contains("E(\"Dashboard: Panels\", \"Home\", \"Layout options\", \"style_DASH\", \"Dashboard options\")"))
        // outdated names now lead to the real control (the jump's fallback needs its title inside the label)
        assertTrue("Motion (scrolling animation)" in labels)
        assertFalse("Scrolling animation" in labels)
    }

    @Test fun colourOriginFollowsThePrecedence() {
        fun o(recolorable: Boolean = true, music: Boolean = false, useAlbum: Boolean = false, album: Boolean = false, game: Boolean = false,
              lBg: Boolean = false, lAcc: Boolean = false, cBg: Boolean = false, cAcc: Boolean = false, keep: Boolean = false,
              picked: Boolean = false, grey: Boolean = false, asks: Boolean = false) =
            describeColorOrigin(recolorable, music, useAlbum, album, game, lBg, lAcc, cBg, cAcc, keep, picked, grey, asks)
        assertEquals(ColorOrigin("Theme", "Theme"), o())
        assertEquals(ColorOrigin("Game", "Game"), o(game = true))
        assertEquals(ColorOrigin("Music", "Music"), o(useAlbum = true, album = true, game = true))
        assertEquals(ColorOrigin("Theme", "Theme"), o(useAlbum = true, album = false))
        assertEquals(ColorOrigin("Layout override", "Layout override"), o(game = true, lBg = true, lAcc = true, cBg = true, cAcc = true, asks = true))
        assertEquals(ColorOrigin("Custom", "Custom"), o(cBg = true, cAcc = true))
        assertEquals(ColorOrigin("Game", "Custom"), o(game = true, keep = true, picked = true))
        assertEquals(ColorOrigin("Game", "Theme"), o(game = true, keep = true))
        assertEquals(ColorOrigin("Game", "Theme"), o(game = true, grey = true))
        assertEquals("Colours from: Game", o(game = true).line)
        assertEquals("Background: Game · Accent: Theme", o(game = true, keep = true).line)
        assertTrue(o(recolorable = false, asks = true).note!!.contains("can't be recoloured"))
        assertTrue(o(music = true, album = true, asks = true).note!!.contains("music theme", ignoreCase = true))
        assertNull(o(game = true).note)
    }

    @Test fun colourOriginIsDisplayOnly() {
        val t = src("ui/theme/Theme.kt")
        // the readout is published after the colours are worked out and never read back by the theme
        assertTrue(t.contains("androidx.compose.runtime.SideEffect { if (ColorOrigin.now != origin) ColorOrigin.now = origin }"))
        assertEquals(1, Regex("ColorOrigin\\.now").findAll(t.substringBefore("data class ColorOrigin")).count() - 1)
        // the precedence lines are the ones from update 480 / 240, unchanged
        assertTrue(t.contains("val customBackground = layoutOwn.bg ?: if (custom && !music) look.background else null"))
        assertTrue(t.contains("val chosenAccent = ((layoutOwn.acc ?: (if (custom && !music) look.accent else manualAccent))?.let { Color(it) }"))
    }
}
