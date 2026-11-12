package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 527: the shading under a background clip is a deep tone of the clip, never a pale fog (ClipLight). */
class ClipLightTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText()
    private fun bright(c: Color) = maxOf(c.red, c.green, c.blue)

    @Test fun theToneIsAlwaysDeepWhateverTheColour() {
        for (c in listOf(Color.White, Color(1f, 0.95f, 0.7f), Color(0.2f, 0.6f, 1f), Color.Black, Color(0.9f, 0.1f, 0.1f))) {
            val t = ClipLight.tone(c)
            assertTrue("$c -> $t", bright(t) <= 0.143f && bright(t) >= 0.039f) // (8-bit sRGB colour: within a step)
        }
        // The hue survives: a blue clip shades blue, a red one red.
        val blue = ClipLight.tone(Color(0.2f, 0.6f, 1f)); assertTrue(blue.blue > blue.red)
        val red = ClipLight.tone(Color(0.9f, 0.1f, 0.1f)); assertTrue(red.red > red.green)
        // At most half saturated (no neon shadow).
        assertTrue(red.green / red.red >= 0.49f)
    }

    @Test fun theShadingEasesInAndStaysBelowOpaque() {
        assertEquals(0.45f, ClipLight.strength(0f), 0.031f)
        assertTrue(ClipLight.strength(1f) <= 0.78f)
        assertTrue(ClipLight.strength(1f) > ClipLight.strength(0.1f))
        val st = ClipLight.stops(Color.Black, ClipLight.strength(1f))
        // Clear through the middle; rises steadily toward the bottom; the top band lighter than the old 0.55.
        assertEquals(0f, st.first { it.first == 0.5f }.second.alpha, 0f)
        val lower = st.filter { it.first >= 0.5f }.map { it.second.alpha }
        assertEquals(lower, lower.sorted())
        assertTrue(st.first().second.alpha < 0.55f)
        // The top band follows the clip's top: light on a dark clip (more video), firm on a white one (the chips stay readable).
        assertTrue(ClipLight.topStrength(0f) < 0.35f)
        assertTrue(ClipLight.topStrength(1f) >= 0.7f && ClipLight.topStrength(1f) <= 0.75f)
        assertEquals(0.7f, ClipLight.stops(Color.Black, 0.5f, 0.7f).first().second.alpha, 0.01f)
        assertTrue(st.last().second.alpha < 0.8f)
    }

    @Test fun onlyARealChangeOfLightMovesIt() {
        val a = ClipLight(0.5f, Color(0.5f, 0.5f, 0.5f))
        assertTrue(ClipLight.moved(null, a))
        assertFalse(ClipLight.moved(a, ClipLight(0.53f, Color(0.52f, 0.5f, 0.49f))))
        assertTrue(ClipLight.moved(a, ClipLight(0.9f, Color(0.5f, 0.5f, 0.5f))))
        assertTrue(ClipLight.moved(a, ClipLight(0.5f, Color(0.2f, 0.4f, 0.8f))))
        assertTrue(ClipLight.moved(a, ClipLight(0.5f, Color(0.5f, 0.5f, 0.5f), upperLuma = 0.95f)))
    }

    @Test fun textOverAClipGetsALocalScrimNotADarkerVideo() {
        assertTrue(src("ui/HomeChrome.kt").contains("else if (ClipBackdrop.on) Color.Black.copy(alpha = 0.46f)"))
        assertTrue(src("ui/HomeChrome.kt").contains("then(if (ClipBackdrop.on) Modifier.videoTextScrim() else Modifier)"))
        assertTrue(src("ui/GameDock.kt").contains("return (if (clipScrim) this.videoTextScrim() else this).nameBackdropStyle(backdrop)"))
        // The old fog only as a QA before / after.
        assertEquals(0.97f, ClipLight.legacyStops(Color.Black).last().second.alpha, 0.005f)
        assertTrue(src("ui/TestHooks.kt").contains("ClipBackdrop.legacy = v == \"legacy\""))
    }

    @Test fun theClipNoLongerUsesTheThemeBackgroundAsAFog() {
        val v = src("ui/VideoPreview.kt")
        assertFalse(v.contains("0.8f to ShelfTheme.background.copy(alpha = 0.85f)"))
        assertTrue(v.contains("Brush.verticalGradient(*ClipLight.stops(toneNow, strength, topNow))"))
        // Sampled rarely and only while playing, never while held.
        assertTrue(v.contains("if (!playing || hold) return@LaunchedEffect"))
        assertTrue(v.contains("kotlinx.coroutines.delay(1500)"))
        assertTrue(v.contains("v.getBitmap(24, 14)"))
    }
}
