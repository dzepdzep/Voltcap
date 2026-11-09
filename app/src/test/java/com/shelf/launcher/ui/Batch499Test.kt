package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.data.Features.ChanPalette
import com.shelf.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 499 (staging only): Save Vault colours beyond the frame, QA cloud-hold hook (D295 option C). */
class Batch499Test {
    private fun root(): File = listOf(File("src/main/java/com/shelf/launcher"), File("app/src/main/java/com/shelf/launcher")).first { it.exists() }
    private fun src(rel: String): String = File(root(), rel).readText()
    private val ember = com.shelf.launcher.ui.theme.AppTheme.EMBER.colors

    @Test fun vaultTakesADarkFamilyAndStaysDarkOtherwise() {
        // No family (OFF / frame-only styles): exactly the vault's old colours.
        val off = vaultFamilyColors(ember, null)
        assertEquals(if (ember.textPrimary.luminance() < 0.5f) Color.White else ember.textPrimary, off.textPrimary)
        assertEquals(Color(0xFF05060B), vaultFloor(null))
        // Arctic / Cinema / dark Channel: the family's own dark panels and accent, readable.
        for (t in listOf(arcticWindowTokens(Color(0xFF8E44D8)), cinemaWindowTokens(CINEMA_RED),
            channelWindowTokens(Color(0xFF8E44D8), chanColorsFor(ChanPalette.OLED, 0), false, true,
                Triple(Color.Black, Color.Black, Color.Black), Color.White, Color.White))) {
            val v = vaultFamilyColors(ember, t)
            assertEquals(t.colors, v)
            assertTrue(v.surface.luminance() < 0.25f)
            assertTrue(contrastRatio(v.textPrimary, v.surface) >= 7f)
            assertTrue(vaultFloor(t).luminance() <= t.colors.background.luminance() + 0.001f)
        }
        // A light family (Channel Pale): the vault keeps its dark scene — no white panels inside it.
        val pale = channelWindowTokens(Color(0xFF8E44D8), chanColorsFor(ChanPalette.PALE, 0), true, true,
            Triple(Color.Black, Color.Black, Color.Black), Color.White, Color.White)
        assertFalse(vaultFamilyColors(ember, pale).surface == pale.colors.surface)
        val s = src("ui/SaveExplorer.kt")
        assertTrue(s.contains("val vaultColors = remember(baseColors, family) { vaultFamilyColors(baseColors, family) }"))
        assertFalse(s.contains("WindowColors(")) // the save cards / their art are not re-themed
    }

    @Test fun cloudHoldOnlyTurnsThingsOffAndOnlyInQa() {
        val t = src("ui/TestHooks.kt")
        assertTrue(t.contains("intent?.getStringExtra(\"voltcap_test_cloudhold\")?.let { m -> if (enabled(context)) cloudHold(context.applicationContext, m) }"))
        val f = t.substring(t.indexOf("internal fun cloudHold"))
        assertTrue(f.contains("syncSaves = false, syncLauncherData = false"))
        assertFalse(Regex("(syncSaves|syncLauncherData|connected) = true").containsMatchIn(f)) // never switches anything on
        assertFalse(f.contains("sync(") || f.contains("disconnect"))
        assertTrue(f.contains("if (!f.exists()) f.writeText(now().toString())")) // the first note (owner's original) is kept
    }

    @Test fun inBetweenArtNeverShowsWhileMovingFast() {
        // [on screen A, B loading, C newest]: B finishing first is outdated (dropped, not faded in); A and C are not.
        assertTrue(heldOutdated(1, 3))
        assertFalse(heldOutdated(0, 3)); assertFalse(heldOutdated(2, 3))
        // Normal change [A, B]: B is the newest — it fades in as before.
        assertFalse(heldOutdated(1, 2)); assertFalse(heldOutdated(0, 1)); assertFalse(heldOutdated(-1, 2))
        assertTrue(src("ui/Motion.kt").contains("if (heldOutdated(layers.indexOf(l), layers.size)) { layers.remove(l); return@LaunchedEffect }"))
    }

    @Test fun shelvesGetAContactShadowAndTheVaultItsFamilyPanels() {
        assertEquals(0.34f, shelfContactAlpha(com.shelf.launcher.data.Features.ShelfLook.WOOD), 0.001f)
        assertTrue(shelfContactAlpha(com.shelf.launcher.data.Features.ShelfLook.GLASS) < shelfContactAlpha(com.shelf.launcher.data.Features.ShelfLook.DARK))
        val h = src("ui/ShelfHome.kt")
        // drawn once per shelf inside the board's Canvas (the kept layer), before the board's front: no per-spine cost
        val board = h.substring(h.indexOf("private fun ShelfBoard"), h.indexOf("// … its front …"))
        assertTrue(board.contains("shelfContactAlpha(look)"))
        val s = src("ui/SaveExplorer.kt")
        assertTrue(s.contains("LocalVaultFamily provides (family != null && !family.light)"))
        assertTrue(s.contains(".background(if (LocalVaultFamily.current) ShelfTheme.colors.surface.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.07f))"))
    }
}
