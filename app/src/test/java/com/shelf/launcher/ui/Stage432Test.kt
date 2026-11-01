package com.shelf.launcher.ui

import com.shelf.launcher.ui.theme.ThemeMotif
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 432: theme readability from the Odin 2 Portal (1080p) and GT78 (960×640) captures, `.shots/432`. */
class Stage432Test {
    private fun alphaAt(y: Float): Float {
        val st = SCENE_TEXT_SHADE_STOPS
        val i = st.indexOfLast { it.first <= y }.coerceAtLeast(0)
        if (i >= st.lastIndex) return st.last().second
        val (y0, a0) = st[i]; val (y1, a1) = st[i + 1]
        return a0 + (a1 - a0) * ((y - y0) / (y1 - y0))
    }

    /** sRGB channel → linear, as WCAG. */
    private fun lin(c: Float) = if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    @Test fun whiteWritingOverAWhiteSceneIsReadableWhereTitlesSit() {
        // Worst case: a pure white scene under the shade, white words on top. Large text needs 3:1 (WCAG AA large).
        for (y in listOf(0.0f, 0.03f, 0.45f, 0.5f, 0.55f, 0.7f, 0.85f, 0.95f, 1.0f)) {
            val bg = 1f - alphaAt(y)
            val ratio = 1.05f / (lin(bg) + 0.05f)
            assertTrue("shade at $y gives ${"%.2f".format(ratio)}:1", ratio >= 3.0f)
        }
        // The upper scene stays visible: at most a light veil between the status band and the title zone.
        assertTrue(alphaAt(0.2f) <= 0.25f)
    }

    @Test fun theNewlyMeasuredBrightScenesAreShaded() {
        assertTrue(BRIGHT_SCENES.containsAll(listOf(ThemeMotif.MEADOW, ThemeMotif.CEL_SKY, ThemeMotif.PIXEL_SKY, ThemeMotif.PIXEL_FALLS,
            ThemeMotif.SHOWCASE_FILMSTRIP, ThemeMotif.BADGE_BOUNCE)))
    }
}
