package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 529 blur hotfix (owner: "pixelated / blocky blur"): the baked blurs were made at 1/8 - 1/20 of the screen and left crease
 * grids when stretched back up (8 - 20 px apart, seen on smooth gradients and bright edges). They are made at about half the screen.
 */
class BlurHotfix529Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun bakedCopiesAreAboutHalfTheScreen() {
        // Flip 2 (1920 px long side) and KONKR (960 px): stretched about 2x, never 8 - 20x.
        assertEquals(960, BakedBlur.sideFor(32.dp, 853f, 1920))
        assertEquals(480, BakedBlur.sideFor(32.dp, 640f, 960))
        assertEquals(BakedBlur.SIDE_MAX, BakedBlur.sideFor(18.dp, 1280f, 3840))
        assertEquals(BakedBlur.SIDE_MIN, BakedBlur.sideFor(18.dp, 320f, 320))
    }

    @Test fun theBlurKeepsItsStrengthOnTheScreen() {
        // The radius in small pixels times the stretch is the same blur on the screen, whatever the copy's size.
        val screenDp = 853f; val screenPx = 1920f
        val side = BakedBlur.sideFor(32.dp, screenDp, 1920)
        val onScreen = BakedBlur.radiusAt(32.dp, side, screenDp) * screenPx / side
        assertEquals(32f * screenPx / screenDp, onScreen, 3f)
        // Home's blurred hero: 5 px at an eighth (~40 px on the screen) then; ~42 px now at a half.
        assertEquals(2, HERO_BLUR_DIV)
        assertEquals(21, heroBlurRadius(1920 / HERO_BLUR_DIV))
        assertEquals(20, softBlurRadius(1920 / HERO_BLUR_DIV))
    }

    @Test fun homeHeroNoLongerDecodedAtAnEighthOrFourteenth() {
        val s = src("HomeChrome.kt")
        assertTrue(s.contains("val heroDiv = if (blurred) HERO_BLUR_DIV else 1"))
        assertFalse(s.contains("heroW / 14"))
        assertFalse(s.contains("SmoothBlur(radius = 3)"))
    }
}
