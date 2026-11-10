package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Batch 521: Tile home placement on the RP Flip 2 (Edit Home move vs the R1 Row place; Top under the profile pictures). */
class Batch521Test {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    private val row = "part:TILES:row"

    @Test fun newRowPlaceDropsOnlyTheVerticalMove() {
        // A left / right move is kept; format() drops all-zero entries.
        assertEquals("$row=12.0,0.0", LayoutParts.withoutVerticalShift("$row=12.0,-90.0", row))
        assertEquals("", LayoutParts.withoutVerticalShift("$row=0.0,-90.0", row))
        val other = "part:TILES:legend=0.0,40.0"
        assertEquals(other, LayoutParts.withoutVerticalShift("$row=0.0,-90.0;$other", row))
        // Nothing to drop: unchanged, other parts untouched.
        assertEquals("$row=30.0,0.0", LayoutParts.withoutVerticalShift("$row=30.0,0.0", row))
        assertEquals(other, LayoutParts.withoutVerticalShift(other, row))
        assertEquals("", LayoutParts.withoutVerticalShift("", row))
    }

    @Test fun bothPlacePickersResetTheTileRow() {
        val qs = src("ui/QuickSettings.kt")
        assertTrue(qs.contains("if (a.homeLayout == com.shelf.launcher.data.HomeLayout.TILES) tileRowPlaceReset(a)"))
        assertTrue(qs.contains("LayoutParts.dropVerticalShift(LayoutParts.id(com.shelf.launcher.data.HomeLayout.TILES, \"row\"))"))
        assertTrue("buttons by the tiles go back", qs.contains("a.shortcutSpot == com.shelf.launcher.data.ShortcutSpot.ROW && HomeWidgets.position(HomeWidgets.SHORTCUTS) != null"))
        val settings = src("ui/SettingsDialog.kt")
        assertTrue(settings.contains("if (it != appearance.tileDockPosition) tileRowPlaceReset(appearance)"))
    }

    @Test fun middleCentresTheUnmovedTilesAndTopClearsTheProfiles() {
        val ls = src("ui/LauncherScreen.kt")
        assertTrue(ls.contains("val line = p[TilesMidLine].let { if (it == androidx.compose.ui.layout.AlignmentLine.Unspecified) it else (it - rowShiftPx).toInt() }"))
        assertTrue(ls.contains("val top = p[TilesTopLine].let { if (it == androidx.compose.ui.layout.AlignmentLine.Unspecified) it else (it - rowShiftPx).toInt() }"))
        assertTrue(ls.contains("com.shelf.launcher.data.Profiles.list.value.isNotEmpty()) 60.dp else 10.dp"))
        assertTrue("a moved row stays on the screen", ls.contains("y0.toFloat().coerceIn(minOf(minY, maxY), maxOf(minY, maxY)).toInt()"))
        // The profiles row itself: 12 dp from the top, 40 dp tall at most (TileSwitchProfiles) - 60 dp clears it.
        assertTrue(ls.contains("padding(start = 22.dp, top = 12.dp)"))
        assertTrue(src("ui/TileSwitch.kt").contains("val d = if (on) 40.dp else 32.dp"))
    }

    @Test fun buttonLettersAreCentredByTheirInk() {
        // The shared glyph draws at the glyph's own bounds (not the line box) and the button hints use it.
        val chrome = src("ui/HomeChrome.kt")
        assertTrue(chrome.contains("size.height / 2f - (bounds.top + bounds.bottom) / 2f"))
        assertTrue(chrome.contains("InkGlyph(text, tint, sizeDp.dp)"))
        for ((file, call) in listOf("ui/ProfileSelect.kt" to "InkGlyph(key,", "ui/CubeHub.kt" to "InkGlyph(button,",
            "ui/StyleMemoryCard.kt" to "InkGlyph(button,", "ui/StyleCardBinder.kt" to "InkGlyph(letter,", "ui/PlatformBrowser.kt" to "InkGlyph(button,",
            "ui/StylePocketQuest.kt" to "InkGlyph(label,", "ui/SecondShowcase.kt" to "InkGlyph(\"P1\""))
            assertTrue(file, src(file).contains(call))
    }
}
