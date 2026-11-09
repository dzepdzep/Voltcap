package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.data.Features.ChanPalette
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 497 (staging only): D295 remaining Drive paths, Channel windows in the layout's real colours, D294 at text ×1.6. */
class Batch497Test {
    private fun root(): File = listOf(File("src/main/java/com/shelf/launcher"), File("app/src/main/java/com/shelf/launcher")).first { it.exists() }
    private fun src(rel: String): String = File(root(), rel).readText()

    @Test fun qaSessionTouchesNoDriveAutomatically() {
        assertTrue(src("data/AutoBackupWorker.kt").contains("if (CloudSync.qaPaused(app)) return Result.success()"))
        assertTrue(src("data/LastPlayedSync.kt").contains("if (CloudSync.qaPaused(context)) return 0"))
        val d = src("data/DriveOnlySaves.kt")
        assertTrue(d.contains("else if (!allowFetch || CloudSync.qaPaused(app)) cached?.second.orEmpty()"))
        assertTrue(d.contains("if (!allowNetwork || CloudSync.qaPaused(app)) return@runCatching null"))
        // Every file that talks to Drive is either gated in a QA session or only runs on the owner's own button press.
        // A new Drive caller fails here until it is added to one of the two lists.
        val gated = setOf("CloudSync.kt", "LastPlayedSync.kt", "DriveOnlySaves.kt", "DriveClient.kt")
        val ownButton = setOf("Backup.kt", "SettingsTransfer.kt") // (Backup's automatic run: AutoBackupWorker, gated above)
        val callers = File(root(), "data").walkTopDown().filter { it.isFile && it.extension == "kt" && it.readText().contains("DriveClient(") }.map { it.name }.toSet()
        assertEquals(emptySet<String>(), callers - gated - ownButton)
        val backupCalls = root().walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("Backup.exportToDrive(") }.map { it.name }.toSet()
        assertEquals(setOf("AutoBackupWorker.kt", "SettingsDialog.kt"), backupCalls)
    }

    private val palettes = listOf(
        chanColorsFor(ChanPalette.PALE, 0), chanColorsFor(ChanPalette.OLED, 0), chanColorsFor(ChanPalette.BLUE, 0),
        chanColorsFor(ChanPalette.YELLOW, 0), chanColorsFor(ChanPalette.CUSTOM, 0xFFDCE8F4.toInt()),
        chanColorsFor(ChanPalette.CUSTOM, 0xFF402060.toInt()), chanColorsFor(ChanPalette.CUSTOM, 0xFFFFE9A0.toInt())
    )
    private val accents = listOf(Color(0xFF2F7BC0), Color(0xFF8E44D8), Color(0xFFFFE000), Color(0xFF808080), Color(0xFFFF2D55), Color(0xFF00FFC8))
    private val themeDark = Triple(Color(0xFF101218), Color(0xFF1A1D25), Color(0xFF252A34))

    @Test fun channelWindowTakesTheOwnersAccent() {
        // The owner's example: Channel grid's accent changed from blue to purple — the window shows purple, not the old blue.
        val purple = Color(0xFF8E44D8)
        val t = channelWindowTokens(purple, chanColorsFor(ChanPalette.PALE, 0), true, true, themeDark, Color.White, Color.White)
        val a = t.colors.accent
        assertTrue(a.blue > a.green && a.red > a.green)
        assertFalse(a == WINDOW_CHANNEL_BLUE)
        // The rim follows it too (tile edge mixed with the accent, as the home tiles do).
        assertTrue(t.rim.red > chanColorsFor(ChanPalette.PALE, 0).edge.red - 0.3f)
        // Older helper (tests, Save Vault fallback) also follows the base accent now.
        assertTrue(channelWindowColors(com.shelf.launcher.ui.theme.AppTheme.EMBER.colors).accent != WINDOW_CHANNEL_BLUE)
    }

    @Test fun everyPaletteAndAccentStaysReadable() {
        for (pal in palettes) for (acc in accents) for (own in listOf(true, false)) {
            val light = own && pal.light
            val t = channelWindowTokens(acc, pal, light, own, themeDark, Color(0xFFECEFF4), Color(0xFFAEB4BE))
            val c = t.colors
            val why = "${pal.palette} light=$light own=$own acc=$acc"
            assertEquals(why, light, t.light)
            assertTrue(why, contrastRatio(c.textPrimary, c.surface) >= 7f || contrastRatio(c.textPrimary, c.surface) >= 4.5f && !light)
            assertTrue(why, contrastRatio(c.textPrimary, c.surfaceRaised) >= 4.5f)
            assertTrue(why, contrastRatio(c.textMuted, c.surface) >= 4.5f)
            assertTrue(why, contrastRatio(c.accent, c.surface) >= 3f)
            assertTrue(why, contrastRatio(t.onAccent, c.accent) >= 4.5f)
            assertTrue(why, contrastRatio(t.outline, c.surface) >= 3f)
            assertTrue(why, contrastRatio(t.deep, t.soft) >= 4.5f)
            // Light panels for a light Channel grid, dark ones otherwise (not one rule for every layout).
            if (light) assertTrue(why, c.surface.luminance() > 0.5f) else assertTrue(why, c.surface.luminance() < 0.25f)
        }
    }

    @Test fun channelDrawnOnTheThemeUsesTheThemesPanels() {
        val t = channelWindowTokens(Color(0xFF8E44D8), chanColorsFor(ChanPalette.PALE, 0), false, false, themeDark, Color(0xFFECEFF4), Color(0xFFAEB4BE))
        assertEquals(themeDark.second, t.colors.surface)
        assertEquals(themeDark.third, t.colors.surfaceRaised)
        val w = src("ui/WindowLook.kt")
        // Keyed on Material's settled scheme (D137), never on the gliding ShelfTheme colours.
        assertTrue(w.contains("androidx.compose.runtime.remember(src.primary, src.background, src.surface, src.surfaceVariant, src.onSurface, pal.palette"))
        assertFalse(w.contains("remember(base) { channelWindowColors(base) }"))
        assertTrue(src("ui/SaveExplorer.kt").contains("val vaultRim = windowRimColor(winStyle)"))
    }

    @Test fun overlayPanelFitsAtLargeText() {
        // Thor-like screen (1080 px tall, ~2.6 px/dp), panel 94 %; fixed parts grow with the text scale.
        val maxH = 1015; val list = 540; val listMin = 250; val pinMin = 187; val floor = 146
        for (fixed in 200..560 step 20) for (pin in listOf(0, 120, 300, 432)) {
            val (l, p) = overlayPanelFit(maxH, fixed, pin, list, listMin, pinMin, floor)
            assertTrue("fixed=$fixed pin=$pin", l <= list && p <= pin)
            assertTrue("fixed=$fixed pin=$pin", l >= minOf(list, floor) && (pin == 0 || p >= minOf(pin, pinMin)))
            // Whenever it can fit with the minimums, it does: header, ✕ and tabs stay on screen.
            if (fixed + minOf(pin, pinMin) + floor <= maxH) assertTrue("fixed=$fixed pin=$pin", fixed + l + p <= maxH)
        }
        // At text ×1.0 nothing changes.
        assertEquals(list to 300, overlayPanelFit(maxH, 150, 300, list, listMin, pinMin, floor))
        val o = src("ui/RaOverlayView.kt")
        assertTrue(o.contains("panel.addView(pinnedFrame, LinearLayout.LayoutParams(px(320f)"))
        assertFalse(o.contains("if (pinnedRow) box.addView(MaxHeightScroll(")) // no scroll inside the pinned scroll
        assertTrue(LIST_FLOOR_DP < LIST_MIN_DP)
    }

    @Test fun layoutFamiliesAreFewAndReadable() {
        assertEquals(WindowLook.Style.ARCTIC, WindowLook.styleFor(HomeLayout.ARCTIC, true))
        assertEquals(WindowLook.Style.CINEMA, WindowLook.styleFor(HomeLayout.CINEMA, true))
        assertEquals(WindowLook.Style.DESK, WindowLook.styleFor(HomeLayout.TWIN_DESK, true))
        assertEquals(WindowLook.Style.SHELF, WindowLook.styleFor(HomeLayout.DISCS, true))
        assertEquals(WindowLook.Style.SHELF, WindowLook.styleFor(HomeLayout.CASEFLOW, true))
        // Off (the default for everyone): every layout keeps the window it had.
        for (l in HomeLayout.values()) assertEquals(WindowLook.Style.OWN, WindowLook.styleFor(l, false))
        // A handful of families, not one per layout.
        assertTrue(WindowLook.Style.values().size <= 8)
        for (acc in accents) for (t in listOf(arcticWindowTokens(acc), cinemaWindowTokens(acc), cinemaWindowTokens(CINEMA_RED))) {
            val c = t.colors
            assertTrue(contrastRatio(c.textPrimary, c.surface) >= 7f)
            assertTrue(contrastRatio(c.textMuted, c.surface) >= 4.5f)
            assertTrue(contrastRatio(c.accent, c.surface) >= 3f && contrastRatio(t.onAccent, c.accent) >= 4.5f)
            assertFalse(t.light)
        }
        // Game popup presentation styles (Own / Banner / Classic / Row) never read the window look.
        for (f in listOf("ui/GameWindow.kt", "ui/GameWindowBanner.kt", "ui/RowRevealWindow.kt", "ui/GameDetailsOverlay.kt"))
            assertFalse(f, src(f).contains("WindowLook") || src(f).contains("windowFrame("))
        assertTrue(src("ui/AchievementDetailDialog.kt").contains("layoutAware = true"))
    }
}
