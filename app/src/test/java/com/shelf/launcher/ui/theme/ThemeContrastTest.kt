package com.shelf.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #79: ThemeContrast — WCAG contrast ratio, lightness nudges and text colour choice. */
class ThemeContrastTest {

    private val eps = 0.01f

    @Test
    fun whiteOnBlackIs21() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), eps)
        assertEquals(21f, contrastRatio(Color.Black, Color.White), eps)
    }

    @Test
    fun sameColourIs1() {
        assertEquals(1f, contrastRatio(Color.White, Color.White), eps)
        assertEquals(1f, contrastRatio(Color(0xFF3366CC), Color(0xFF3366CC)), eps)
    }

    @Test
    fun alphaIsIgnored() {
        assertEquals(21f, contrastRatio(Color.White.copy(alpha = 0.1f), Color.Black), eps)
    }

    @Test
    fun ratioAlwaysInRange() {
        val colours = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color(0xFF777777), Color(0xFF101010))
        for (a in colours) for (b in colours) {
            val r = contrastRatio(a, b)
            assertTrue("$a/$b = $r", r >= 1f - 1e-4f && r <= 21f + 1e-4f)
            assertEquals(r, contrastRatio(b, a), 1e-4f)
        }
    }

    @Test
    fun legibleOnUnchangedWhenAlreadyEnough() {
        assertEquals(Color.White, Color.White.legibleOn(4.5f, listOf(Color.Black)))
        val c = Color(0xFF3366CC)
        assertEquals(c, c.legibleOn(4.5f, emptyList()))
    }

    @Test
    fun legibleOnDarkBackgroundGetsLighterAndReachesMinimum() {
        val fg = Color(0xFF333333)
        val bg = Color(0xFF000000)
        val out = fg.legibleOn(4.5f, listOf(bg))
        assertTrue(contrastRatio(out, bg) >= 4.5f)
        assertTrue(out.luminance() > fg.luminance())
    }

    @Test
    fun legibleOnLightBackgroundGetsDarkerAndReachesMinimum() {
        val fg = Color(0xFFDDDDDD)
        val bg = Color.White
        val out = fg.legibleOn(3f, listOf(bg))
        assertTrue(contrastRatio(out, bg) >= 3f)
        assertTrue(out.luminance() < fg.luminance())
    }

    @Test
    fun legibleOnSeveralBackgrounds() {
        val fg = Color(0xFF8A2BE2) // violet
        val bgs = listOf(Color(0xFF101018), Color(0xFF202030))
        val out = fg.legibleOn(4.5f, bgs)
        for (b in bgs) assertTrue(contrastRatio(out, b) >= 4.5f)
    }

    @Test
    fun legibleOnKeepsAlpha() {
        val out = Color(0x80333333).legibleOn(4.5f, listOf(Color.Black))
        assertEquals(0x80 / 255f, out.alpha, 0.01f)
    }

    @Test
    fun legibleOnUnreachableGoesToTheExtreme() {
        // 22:1 can't be reached: the colour goes all the way to white on a dark background.
        val out = Color(0xFF808080).legibleOn(22f, listOf(Color.Black))
        assertEquals(Color.White.toArgb(), out.toArgb())
    }

    @Test
    fun textOnKeepsPreferredWhenReadable() {
        assertEquals(Color.Black, textOn(Color.White, preferred = Color.Black))
        assertEquals(Color(0xFF101010), textOn(Color(0xFFFFEE88), preferred = Color(0xFF101010)))
    }

    @Test
    fun textOnPicksBlackOnLightAccent() {
        assertEquals(Color.Black, textOn(Color.Yellow, preferred = Color(0xFF777777)))
        assertEquals(Color.Black, textOn(Color(0xFFEEEEEE), preferred = Color.White))
    }

    @Test
    fun textOnPicksWhiteOnDarkAccent() {
        assertEquals(Color.White, textOn(Color(0xFF000080), preferred = Color.Black))
        assertEquals(Color.White, textOn(Color(0xFF202020), preferred = Color(0xFF303030)))
    }

    @Test
    fun legibleArtAccentMeetsBothRules() {
        val accents = listOf(Color(0xFF777777), Color(0xFF2050A0), Color(0xFFE0C000), Color(0xFF111111), Color(0xFFFF2020))
        val themes = listOf(Color(0xFF101010) to Color(0xFF202020), Color(0xFFF5F5F5) to Color.White)
        for ((bg, raised) in themes) for (a in accents) {
            val out = legibleArtAccent(a, bg, raised)
            val text = maxOf(contrastRatio(Color.White, out), contrastRatio(Color.Black, out))
            assertTrue("text on $out from $a", text >= 4.5f - 1e-3f)
            assertTrue("$out on $bg", contrastRatio(out, bg) >= 3f - 1e-3f)
            assertTrue("$out on $raised", contrastRatio(out, raised) >= 3f - 1e-3f)
        }
    }

    @Test
    fun saturationOfGreysAndPureColours() {
        assertEquals(0f, saturationOf(Color.Black), 1e-6f)
        assertEquals(0f, saturationOf(Color(0xFF808080)), 1e-6f)
        assertEquals(1f, saturationOf(Color.Red), 1e-6f)
    }
}
