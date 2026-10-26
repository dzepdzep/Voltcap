package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CategoryBars
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.LayoutPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 344: the console-inspired layouts — in the picker, their presets, R1 tiles, windows and sound kits. */
class Layouts344Test {

    private fun ctx(l: HomeLayout) = R1Ctx(
        AppearanceSettings(homeLayout = l), secondDisplay = true, bluetooth = true, lights = true, romm = false, raAccounts = 1,
        themeRecolorable = true, chargeLimitWorks = true
    )

    private val all = listOf(HomeLayout.BLADES, HomeLayout.BUBBLES, HomeLayout.CAMERA, HomeLayout.PANELS, HomeLayout.MAGAZINE, HomeLayout.SHOP, HomeLayout.TERMINAL)

    @Test fun pickableStyleHomesWithPresets() {
        for (l in all) {
            assertTrue(l in HomeLayout.pickable)
            assertTrue(l.styleHome)
            assertTrue("has presets", LayoutPresets.forLayout(l).isNotEmpty())
            assertTrue("its categories: its own menu or caption", l in CategoryBars.MENU || l in CategoryBars.OWN)
        }
    }

    /** Blades draws the game in your look: the look tiles stay; Game size (it sizes to the blade) is hidden. */
    @Test fun bladesR1() {
        assertEquals(R1Fit.Hide, r1TileFit("gamesize", ctx(HomeLayout.BLADES)))
        assertEquals(R1Fit.Show, r1TileFit("gamelook", ctx(HomeLayout.BLADES)))
        assertEquals(R1Fit.Show, r1TileFit("endless", ctx(HomeLayout.BLADES)))
        assertEquals(null, layoutOwnLook(HomeLayout.BLADES))
    }

    /** Bubble home draws bubbles: the look, badges, the achievement chip and game size do nothing there. */
    @Test fun bubblesR1() {
        for (id in listOf("gamelook", "badges", "progressbar", "gamesize")) assertEquals(id, R1Fit.Hide, r1TileFit(id, ctx(HomeLayout.BUBBLES)))
        assertEquals(R1Fit.Show, r1TileFit("endless", ctx(HomeLayout.BUBBLES)))
    }

    /** The staggered bubble rows: ▲ ▼ go to the nearest bubble of the next row, on to the next page. */
    @Test fun bubbleRows() {
        // 5 across: 5 / 4 / 5 = 14 a page.
        assertEquals(0 to 4, bubbleSlot(4, 5)); assertEquals(1 to 0, bubbleSlot(5, 5)); assertEquals(2 to 4, bubbleSlot(13, 5))
        assertEquals(8, bubbleVertical(4, true, 5, 40))   // top row's last → middle row's last
        assertEquals(10, bubbleVertical(6, true, 5, 40))  // middle col 1 → bottom col 1
        assertEquals(15, bubbleVertical(10, true, 5, 40)) // bottom col 1 → next page's top col 1
        assertEquals(10, bubbleVertical(15, false, 5, 40))
        assertEquals(null, bubbleVertical(2, false, 5, 40))
        assertEquals(15, bubbleVertical(12, true, 5, 16)) // the next page is short: its last bubble
        assertEquals(null, bubbleVertical(15, true, 5, 16))
    }

    @Test fun ownWindows() {
        assertEquals(WinScene.BLADE_GUIDE, winSceneFor(HomeLayout.BLADES))
        assertEquals(WinScene.BUBBLE_CARD, winSceneFor(HomeLayout.BUBBLES))
        assertEquals(WinScene.CAMERA_MENU, winSceneFor(HomeLayout.CAMERA))
        assertEquals(WinScene.PANEL_DETAIL, winSceneFor(HomeLayout.PANELS))
        assertEquals(WinScene.MAG_PAGE, winSceneFor(HomeLayout.MAGAZINE))
        assertEquals(WinScene.SHOP_TAG, winSceneFor(HomeLayout.SHOP))
        assertEquals(WinScene.TERM_RUN, winSceneFor(HomeLayout.TERMINAL))
    }

    /** Dithering uses only the palette's shades, the darkest for black and the lightest for white. */
    @Test fun ditherKeepsToThePalette() {
        val pal = intArrayOf(0xFF000000.toInt(), 0xFF555555.toInt(), 0xFFAAAAAA.toInt(), 0xFFFFFFFF.toInt())
        val src = IntArray(16 * 16) { i -> if (i % 16 < 8) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val out = ditherPixels(src, 16, 16, pal)
        assertTrue(out.all { it in pal })
        assertEquals(pal[0], out[0]); assertEquals(pal[3], out[15])
        for (id in listOf("gamelook", "badges", "progressbar", "gamesize")) assertEquals(id, R1Fit.Hide, r1TileFit(id, ctx(HomeLayout.CAMERA)))
    }

    /** The Terminal's short file names: capitals and digits, at most eight, an old system's "~1" when cut. */
    @Test fun terminalFileNames() {
        assertEquals("ALADDIN", termFileName("Aladdin"))
        assertEquals("ASTERI~1", termFileName("Asterix & Obelix XXL"))
        assertEquals("MARIOK~1", termFileName("Mario Kart (USA) [!]"))
        assertEquals("GAME", termFileName("???"))
    }

    /** Every kit makes every sound: short, not silent, never clipping. */
    @Test fun everyKitSoundIsMade() {
        for (k in LayoutKit.values()) for (s in KitSound.values()) {
            val pcm = LayoutKitSounds.synth(k, s)
            assertTrue("$k $s has length", pcm.size in 200..30000)
            assertTrue("$k $s is audible", pcm.any { kotlin.math.abs(it.toInt()) > 2000 })
        }
    }
}
