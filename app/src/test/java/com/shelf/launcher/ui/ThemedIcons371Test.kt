package com.shelf.launcher.ui

import com.shelf.launcher.data.Features.MonoIconStyle
import com.shelf.launcher.data.IconQuality
import com.shelf.launcher.data.IconQuality.Look
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 371: smart themed icons (candidate quality, looks, per-surface policy). Pixels are synthetic; no app is named in the code under test. */
class ThemedIcons371Test {
    private val n = 192
    private fun canvas(f: (Int, Int) -> Int): IntArray = IntArray(n * n) { f(it % n, it / n) }
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private val clear = 0
    private val white = argb(255, 255, 255, 255)
    private val red = argb(255, 200, 30, 30)

    private fun inRect(x: Int, y: Int, x0: Int, y0: Int, x1: Int, y1: Int) = x in x0 until x1 && y in y0 until y1
    private fun inCircle(x: Int, y: Int, r: Double) = Math.hypot(x - n / 2.0, y - n / 2.0) <= r

    /** A logo: a bold ring with a bar through it, on transparency (a good simple monochrome symbol). */
    private fun logo() = canvas { x, y ->
        val d = Math.hypot(x - n / 2.0, y - n / 2.0)
        if ((d in 50.0..72.0) || inRect(x, y, 40, 90, 152, 104)) white else clear
    }

    @Test fun goodMonochromeLogoStaysThemed() {
        assertTrue(IconQuality.assess(logo(), n, n, true).usable)
        assertTrue(IconQuality.assess(logo(), n, n, false).usable)
    }

    @Test fun simpleSolidCircleLogoIsNotRefusedAsTrueMono() {
        val disc = canvas { x, y -> if (inCircle(x, y, 80.0)) white else clear }
        assertTrue(IconQuality.assess(disc, n, n, true).usable)
    }

    @Test fun filledSquareFallsBack() {
        val sq = canvas { _, _ -> red }
        assertFalse(IconQuality.assess(sq, n, n, true).usable)
        assertFalse(IconQuality.assess(sq, n, n, false).usable)
        val inset = canvas { x, y -> if (inRect(x, y, 14, 14, 178, 178)) red else clear }
        assertFalse(IconQuality.assess(inset, n, n, false).usable)
    }

    /** An adaptive icon whose "foreground" is really the red background plate with a small light mark on it. */
    @Test fun armsx2LikePlateWithSmallMarkFallsBack() {
        val plate = canvas { x, y -> if (inRect(x, y, 0, 0, n, n)) (if (inRect(x, y, 70, 70, 122, 122)) white else red) else clear }
        assertFalse(IconQuality.assess(plate, n, n, false).usable)
        val roundedPlate = canvas { x, y ->
            val corner = Math.hypot(Math.max(0, 40 - x).toDouble(), Math.max(0, 40 - y).toDouble()) > 34.0
            if (inRect(x, y, 6, 6, 186, 186) && !corner) (if (inRect(x, y, 70, 70, 122, 122)) white else red) else clear
        }
        assertFalse(IconQuality.assess(roundedPlate, n, n, false).usable)
    }

    @Test fun flatBlobAndNearlyEmptyAndTooDarkFallBack() {
        val blob = canvas { x, y -> if (inCircle(x, y, 88.0)) red else clear }
        assertFalse(IconQuality.assess(blob, n, n, false).usable)
        assertFalse(IconQuality.assess(IntArray(n * n), n, n, true).usable)
        val speck = canvas { x, y -> if (inRect(x, y, 90, 90, 94, 94)) white else clear }
        assertFalse(IconQuality.assess(speck, n, n, true).usable)
        val dark = canvas { x, y -> if (inRect(x, y, 60, 60, 130, 130) && (x / 8 + y / 8) % 2 == 0) argb(255, 10, 10, 10) else clear }
        assertFalse(IconQuality.assess(dark, n, n, false).usable)
    }

    @Test fun smallOrMismatchedPixelBufferIsRefusedNotCrashed() {
        assertFalse(IconQuality.assess(IntArray(4), n, n, true).usable)
    }

    @Test fun looksFollowStyleAndQuality() {
        assertEquals(Look.MONO, IconQuality.look(true, MonoIconStyle.DETAIL, true, true))
        assertEquals(Look.MONO, IconQuality.look(true, MonoIconStyle.SOLID, true, true))
        assertEquals(Look.DETAIL, IconQuality.look(true, MonoIconStyle.DETAIL, true, false))
        assertEquals(Look.SOLID, IconQuality.look(true, MonoIconStyle.SOLID, true, false))
        assertEquals(Look.ORIGINAL, IconQuality.look(true, MonoIconStyle.DETAIL, false, true))
        // a manual "own colours" choice and a surface with the option off both keep the original
        assertEquals(Look.ORIGINAL, IconQuality.look(true, MonoIconStyle.ORIGINAL, true, true))
        assertEquals(Look.ORIGINAL, IconQuality.look(false, MonoIconStyle.DETAIL, true, true))
    }

    @Test fun perAppOverrideDecidesTheSurfaceStyle() {
        // dock: the app's own style (or the global one) is used as is
        assertEquals(MonoIconStyle.SOLID, themedStyle(false, true, MonoIconStyle.SOLID))
        assertEquals(MonoIconStyle.ORIGINAL, themedStyle(false, true, MonoIconStyle.ORIGINAL))
        // L1: only an app's own "own colours" choice opts out; a global style never reaches it
        assertEquals(MonoIconStyle.ORIGINAL, themedStyle(true, true, MonoIconStyle.ORIGINAL))
        assertEquals(MonoIconStyle.DETAIL, themedStyle(true, true, MonoIconStyle.SOLID))
        assertEquals(MonoIconStyle.DETAIL, themedStyle(true, false, MonoIconStyle.ORIGINAL))
    }

    // ---- source guards: the places the behaviour lives, so a later edit cannot quietly undo it ----
    private fun src(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/")
        val f = roots.map { File(it + name) }.first { it.exists() }
        return f.readText()
    }

    private fun body(text: String, fn: String): String {
        val i = text.indexOf("fun $fn(")
        val j = text.indexOf("\n}\n", i)
        return text.substring(i, j)
    }

    @Test fun mainDrawerAndHomeLayoutsKeepOriginalColours() {
        val icons = src("AppIcons.kt")
        val rememberApp = body(icons, "rememberAppIcon")
        assertFalse(rememberApp.contains("secondDockMono"))
        assertFalse(rememberApp.contains("IconStyles"))
        assertTrue(rememberApp.contains("rememberOwnAppIcon"))
        val art = body(icons, "AppIconArt")
        assertFalse(art.contains("secondDockMono"))
        assertFalse(art.contains("IconStyles"))
        assertFalse(art.contains("rememberThemedAppIcon"))
        assertTrue(src("AppDrawer.kt").contains("rememberAppIcon(app.packageName)"))
    }

    @Test fun themedSurfacesBakeTheTintAndAddNoColourFilter() {
        val panel = src("ShortcutsPanel.kt")
        assertTrue(panel.contains("rememberThemedAppIcon(app.packageName, IconSurface.SHORTCUTS)"))
        assertFalse(panel.contains("themedIconFilter"))
        assertFalse(panel.contains("colorFilter"))
        assertTrue(src("SecondDockShortcuts.kt").contains("rememberThemedAppIcon(id, IconSurface.SECOND_DOCK)"))
        assertFalse(src("AppIcons.kt").contains("fun themedIconFilter"))
    }

    @Test fun nothingWrongIsShownWhileLoading() {
        val themed = body(src("AppIcons.kt"), "rememberThemedAppIcon")
        assertTrue(themed.contains("rememberIconCandidate(packageName) ?: return null"))
        val cand = body(src("AppIcons.kt"), "rememberIconCandidate")
        assertTrue(cand.contains("if (st.first == packageName) st.second"))
        // the loading tile keeps its 36 dp slot
        assertTrue(src("ShortcutsPanel.kt").contains("Box(Modifier.size(36.dp))"))
    }
}
