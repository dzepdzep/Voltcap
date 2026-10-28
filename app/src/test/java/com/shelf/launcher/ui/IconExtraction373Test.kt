package com.shelf.launcher.ui

import com.shelf.launcher.data.IconQuality
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 373: the symbol of a flat coloured plate is lifted off its background (an ARMSX2-like icon is no solid block); initials are the last fallback. */
class IconExtraction373Test {
    private val n = 96
    private val red = 0xFFC62828.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** A flat [bg] square with a white ring (a hole in the middle showing the background) and a bar. */
    private fun plateWithMark(bg: Int = red): IntArray {
        val px = IntArray(n * n) { bg }
        val c = n / 2.0
        for (y in 0 until n) for (x in 0 until n) {
            val d = Math.hypot(x - c, y - c)
            if (d in 22.0..32.0) px[y * n + x] = white
            if (y in 44..52 && x in 30..66) px[y * n + x] = white
        }
        return px
    }

    private fun alphaAt(a: IntArray, x: Int, y: Int) = a[y * n + x] ushr 24

    @Test fun markIsLiftedOffTheBackgroundAndHolesStayOpen() {
        val plate = plateWithMark()
        assertFalse(IconQuality.assess(plate, n, n, false).usable) // the plate itself is refused
        val out = IconQuality.extractSymbol(plate, n, n)
        assertNotNull(out)
        out!!
        assertEquals(0, alphaAt(out, 2, 2))          // the background is gone
        assertEquals(0, alphaAt(out, 20, 20))        // inside the ring, off the bar: still a hole
        assertEquals(255, alphaAt(out, n / 2 + 25, n / 2)) // the ring itself stays
        assertEquals(255, alphaAt(out, 48, 48))      // the bar stays
        assertTrue(IconQuality.assess(out, n, n, false).usable)
    }

    @Test fun solidPlateWithNothingOnItIsNotInvented() {
        assertNull(IconQuality.extractSymbol(IntArray(n * n) { red }, n, n))
    }

    @Test fun gradientAndPhotoLikeIconsAreLeftAlone() {
        val grad = IntArray(n * n) { i -> val x = i % n; (0xFF shl 24) or ((x * 255 / n) shl 16) or (((i / n) * 255 / n) shl 8) or 0x40 }
        assertNull(IconQuality.extractSymbol(grad, n, n))
    }

    @Test fun maskedCornersAreKeptTransparent() {
        val plate = plateWithMark()
        for (y in 0 until 10) for (x in 0 until 10) plate[y * n + x] = 0 // a rounded corner
        val out = IconQuality.extractSymbol(plate, n, n)
        assertNotNull(out)
        assertEquals(0, alphaAt(out!!, 3, 3))
    }

    @Test fun backgroundTouchingMostOfTheIconIsRefused() {
        // Almost everything differs from the border colour: the "background" was not what filled the icon.
        val px = IntArray(n * n) { white }
        for (y in 0 until n) for (x in 0 until n) if (x < 3 || y < 3 || x >= n - 3 || y >= n - 3) px[y * n + x] = red
        assertNull(IconQuality.extractSymbol(px, n, n))
    }

    @Test fun monogramUsesTheAppsOwnName() {
        assertEquals("AR", IconQuality.monogram("ARMSX2"))
        assertEquals("Ch", IconQuality.monogram("Chrome"))
        assertEquals("MT", IconQuality.monogram("Moon Tools"))
        assertEquals("DF", IconQuality.monogram("dolphin  - fork"))
        assertEquals("?", IconQuality.monogram("···"))
        assertEquals("R", IconQuality.monogram("R"))
    }

    @Test fun shortcutsNeverFallBackToFullColourButDrawerAndDockDo() {
        val roots = listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/")
        val icons = roots.map { File(it + "AppIcons.kt") }.first { it.exists() }.readText()
        val themed = icons.substring(icons.indexOf("fun rememberThemedAppIcon("), icons.indexOf("internal fun themedStyle("))
        assertTrue(themed.contains("surface == IconSurface.SHORTCUTS && cand.monogram != null"))
        // Main drawer / home layouts: rememberAppIcon stays the original.
        val drawer = icons.substring(icons.indexOf("fun rememberAppIcon("), icons.indexOf("internal enum class IconSurface"))
        assertTrue(drawer.contains("rememberOwnAppIcon(packageName)") && !drawer.contains("Themed"))
        assertFalse(themed.contains("ColorFilter"))
    }
}
