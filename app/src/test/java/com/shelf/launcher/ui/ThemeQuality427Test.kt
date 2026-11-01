package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 427: theme palettes readable, the review gallery covering every theme, covers fitted rather than cut. */
class ThemeQuality427Test {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    // The same pairs and aims as tools/themes/contrast_check.py: 4.5:1 for normal text, 3:1 for meaningful controls (no tolerance).
    private val TEXT = 4.5f
    private val CONTROL = 3.0f

    @Test fun everyPaletteMeetsTheTextAndControlAims() {
        val bad = AppTheme.values().filter { !it.motif.custom }.mapNotNull { t ->
            val c = t.colors
            val pairs = mapOf(
                "text/raised" to (contrast(c.textPrimary, c.surfaceRaised) to TEXT),
                "muted/bg" to (contrast(c.textMuted, c.background) to TEXT),
                "muted/surface" to (contrast(c.textMuted, c.surface) to TEXT),
                "muted/raised" to (contrast(c.textMuted, c.surfaceRaised) to TEXT),
                // The words on an accent fill: onAccentColor() draws black or white, whichever contrasts more.
                "onAccent/accent" to (maxOf(contrast(Color.White, c.accent), contrast(Color.Black, c.accent)) to TEXT),
                "accent/raised" to (contrast(c.accent, c.surfaceRaised) to CONTROL),
                "accent/bg" to (contrast(c.accent, c.background) to CONTROL)
            )
            pairs.filter { (_, v) -> v.first < v.second }.takeIf { it.isNotEmpty() }
                ?.let { f -> t.name + " " + f.entries.joinToString { "${it.key} %.2f".format(it.value.first) } }
        }
        assertTrue("palette pairs under 4.5:1 text / 3:1 control: $bad", bad.isEmpty())
    }

    @Test fun fixedPalettesKeepTheirCharacter() {
        // Lobby stays light, Bold split's accent stays red, Pocket LCD's background stays dark green.
        assertTrue(AppTheme.LOBBY.colors.background.luminance() > 0.8f)
        val r = AppTheme.BOLD_SPLIT.colors.accent
        assertTrue(r.red > 0.9f && r.green < 0.45f && r.blue < 0.5f)
        assertTrue(AppTheme.POCKET_LCD.colors.background.green > AppTheme.POCKET_LCD.colors.background.red)
    }

    @Test fun wideCoversAreFittedNotCut() {
        val cell = 160f / 224f
        assertTrue("a DS box (wider than tall) is shown whole", atlasFitsWhole(1.12f, cell))
        assertTrue("a square CD case is shown whole", atlasFitsWhole(1f, cell))
        assertTrue("a DVD-shaped cover fills its cell", !atlasFitsWhole(0.71f, cell))
        val r = atlasFitRect(400, 300, 160, 224)
        assertEquals(160, r[2] - r[0])
        assertEquals(120, r[3] - r[1])
        assertEquals(52, r[1]) // centred
    }

    @Test fun reviewGalleryCoversEveryShaderTheme() {
        val gen = File(listOf("tools/themes/gen.py", "../tools/themes/gen.py").first { File(it).exists() }).readText()
        val listed = Regex("\\(\"([A-Z0-9_]+)\", \"").findAll(gen).map { it.groupValues[1] }.toSet() + setOf("PIXEL_FALLS", "LOBBY")
        val missing = AppTheme.values().filter { !it.motif.custom && it.motif.name !in listed }.map { it.name }
        assertTrue("themes gen.py (and so the review gallery) doesn't know: $missing", missing.isEmpty())
    }
}
