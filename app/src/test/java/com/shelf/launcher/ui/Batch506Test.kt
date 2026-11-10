package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 506 (staging only): P0 popup fixes from the owner's review of the 505 visual report + the window's own dim / motion. */
class Batch506Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    /** Cross menu's second movement after closing / Case Flow fading with Motion Off: the platform dim and window fade. */
    @Test fun theWindowDrawsTheDimItselfAndOwnsItsMotion() {
        assertEquals(0.6f, winSysDim(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND, 0.6f), 0.0001f)
        assertEquals(0f, winSysDim(0, 0.6f), 0.0001f)
        assertEquals(1f, winSysDim(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND, 3f), 0.0001f)
        val g = src("ui/GameWindow.kt")
        assertTrue(g.contains("win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)"))
        assertTrue(g.contains("val ownMotion = anim || MotionLevel.scaleFactor == 0f"))
        assertTrue(g.contains("if (ownMotion) win.setWindowAnimations(0)"))
        // The dim follows the reveal (read while drawing), the flight's veil too.
        assertTrue(g.contains("drawRect(Color.Black.copy(alpha = sysDim * k))") && g.contains("if (sysDim > 0f && scene != WinScene.DISC_PANEL"))
    }

    @Test fun discStationSpeaksDiscAndOpensBesideTheDeck() {
        assertEquals("COMPACT DISC", discKindPlate(discKind("N64")))
        assertEquals("CD-ROM", discKindPlate(discKind("PS1")))
        assertEquals("DVD-ROM", discKindPlate(discKind("PS2")))
        for (p in listOf("N64", "SNES", "GBA", "PS1", "PS2")) {
            val l = discPanelLabels(p)
            assertFalse(p, l.first.contains("CARTRIDGE") || l.second.contains("SLOT"))
        }
        // Thor 1920x1080 at 2x (960 x 540 dp, window room 916 x 512): right of the 499 dp deck.
        val (left, w) = discPanelGeometry(916f, 512f)
        assertTrue("left $left", left in 495f..510f)
        assertTrue(w >= 300f && left + w <= 916.01f)
        // GT78 960x640 at 1.5x (640 x 427 dp, room 596 x 399): still beside the deck, keys never squeezed under 300 dp.
        val (l2, w2) = discPanelGeometry(596f, 399f)
        assertTrue(w2 >= 296f - 0.01f && l2 + w2 <= 596.01f)
        // A very narrow room: the panel takes the room it needs (it overlaps the deck's edge instead).
        val (l3, w3) = discPanelGeometry(320f, 300f)
        assertEquals(20f, l3, 0.01f); assertEquals(300f, w3, 0.01f)
        assertTrue(WinScene.DISC_PANEL in SELF_MOVING_SCENES)
        assertTrue(src("ui/WinScenesMore.kt").contains("translationX = (1f - e) * -48.dp.toPx()"))
    }

    @Test fun cinemaFollowsItsLayoutColours() {
        assertTrue(cinemaKeepsRed(Features.CnAccent.CINEMA, layoutAsks = false))
        assertFalse(cinemaKeepsRed(Features.CnAccent.CINEMA, layoutAsks = true))
        assertFalse(cinemaKeepsRed(Features.CnAccent.THEME, layoutAsks = false))
    }

    @Test fun channelGridRimFollowsItsLayoutColoursOnlyWhenSet() {
        val t = src("ui/WinScenesWii.kt")
        assertTrue(t.contains("Features.layoutColorsFor(com.shelf.launcher.data.HomeLayout.CHAN.name).acc == null) WiiBlue"))
        assertFalse(t.contains("if (on) WiiBlue"))
    }

    @Test fun layoutStudioHasItsOwnBalancedWindow() {
        assertEquals(WinScene.STUDIO_PANEL, winSceneFor(HomeLayout.CUSTOM))
        assertTrue(studioNarrow(380f, 500f)); assertTrue(studioNarrow(700f, 280f)); assertFalse(studioNarrow(700f, 500f))
        // The art well never takes more than a third, leaves the keys their room on a short screen, never under 56 dp narrow.
        assertEquals(190f, studioArtWidth(700f, 500f, false), 0.01f)
        assertTrue(studioArtWidth(700f, 300f, false) <= (300f - 130f) * 0.72f + 0.01f)
        assertEquals(56f, studioArtWidth(200f, 300f, true), 0.01f)
        assertEquals("N64", studioInitials("Nintendo 64"))
        assertEquals("PS", studioInitials("PlayStation"))
        assertEquals("?", studioInitials(""))
        val s = src("ui/WinSceneStudio.kt")
        // The art never stretched, the name never over it. (Batch 523, owner: no bars round the picture - Smart Fill: a light
        // crop, else the whole picture over its soft copy; SmartArt never stretches.)
        assertTrue(s.contains("SmartArt(") && !s.contains("ContentScale.FillBounds"))
        assertTrue(WinScene.STUDIO_PANEL in MORE_SCENES)
    }

    @Test fun caseFlowNameHasItsOwnBandAndEmptyArtIsShort() {
        assertEquals(92f, caseFlowEmptyBand(androidx.compose.ui.unit.Dp(300f)).value, 0.01f)
        assertEquals(64f, caseFlowEmptyBand(androidx.compose.ui.unit.Dp(50f)).value, 0.01f)
        val t = src("ui/WinScenes.kt")
        assertTrue(t.contains("// The name band: its own room under the picture"))
        assertFalse(t.contains("shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 6f)))\n                    Text(listOfNotNull(ctx.console, ctx.year, ctx.developer)"))
        assertTrue(t.contains("if (!artHere) CaseFlowNoArt(ctx, Modifier.fillMaxSize())"))
    }
}
