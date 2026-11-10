package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 476: the objective fixes from the phase-2 review (D253, D255, D258, D259, D256, D260). */
class Readability476Test {
    private fun src(name: String): String =
        listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
            .map(::File).first { it.exists() }.readText()

    // D253: the KONKR Cross details page measured 298 x 300 dp inside its padding; the full-height cover (216 dp) left 61 dp.
    @Test fun crossDetailsOnTheKonkrKeepsAReadableTextColumn() {
        val w = 298f; val h = 300f
        val cover = detailsCoverFit(w, h)
        assertNotNull(cover)
        assertTrue("text column ${w - cover!! - DETAILS_GAP} dp", w - cover - DETAILS_GAP >= DETAILS_MIN_TEXT)
        assertTrue(cover >= DETAILS_MIN_COVER)
    }

    @Test fun aWidePageKeepsTheFullHeightCover() {
        assertEquals(400f * 0.72f, detailsCoverFit(900f, 400f)!!, 0.01f)
    }

    @Test fun aVeryNarrowPagePutsTheCoverAboveTheText() {
        assertNull(detailsCoverFit(240f, 400f))   // 240 − 14 − 200 = 26 dp: no room for even a small cover beside the text
    }

    @Test fun neverLessThanTheMinimumTextWhenTheCoverIsBeside() {
        for (w in 100..1200 step 7) for (h in 80..700 step 13) {
            val c = detailsCoverFit(w.toFloat(), h.toFloat()) ?: continue
            assertTrue("w $w h $h", w - c - DETAILS_GAP >= DETAILS_MIN_TEXT - 0.01f)
        }
    }

    // D258: the Bar dock's status keys.
    @Test fun dockStatusKeysKeepTheirFirstPart() {
        assertEquals("Balanced", dockShortLabel("Balanced · AutoTDP Balanced · Device default"))
        assertEquals("Saves: up to date", dockShortLabel("Saves: up to date · 1 min ago"))
        assertEquals("Achievements", dockShortLabel("Achievements"))
        assertEquals("·", dockShortLabel("·"))
    }

    // D259: one size per button row — only ever down, a bad reading ignored.
    @Test fun buttonsOfARowShareTheSmallestSize() {
        var s = 99f
        for (used in listOf(15f, 15f, 11.5f, 15f)) s = sharedButtonSize(s, used)
        assertEquals(11.5f, s, 0f)
        assertEquals(11.5f, sharedButtonSize(s, Float.NaN), 0f)
        assertEquals(11.5f, sharedButtonSize(s, 0f), 0f)
    }

    @Test fun aButtonWordTakesTheLargestSizeItFitsAt() {
        assertEquals(15f, buttonFitSize(15f) { true }, 0f)
        assertEquals(11.5f, buttonFitSize(15f) { it <= 11.5f }, 0f)
        assertEquals(9f, buttonFitSize(15f) { false }, 0f)
    }

    // 476a took the size back from auto-size's layout (it reports the asked size): the TV still showed three sizes.
    @Test fun theSharedSizeIsMeasuredNotReadBackFromAutoSize() {
        val s = src("GameWindow.kt")
        assertFalse(s.contains("r.layoutInput.style.fontSize"))
        assertTrue(s.contains("buttonFitSize(base)"))
    }

    @Test fun everyWindowButtonRowSharesItsSize() {
        val s = src("GameWindow.kt")
        // (476a missed the shared-width row — the TV's narrow window — and its Details page still had three sizes.)
        // Every call of WinButton (its definition left out) sits inside a ButtonRow: as many rows as calls.
        val calls = Regex("(?<!fun )WinButton\\(\\s*look").findAll(s).count()
        assertEquals(5, calls)
        assertEquals("WinButton callers inside a ButtonRow", calls, Regex("ButtonRow\\(").findAll(s).count() - 1)
    }

    // D255: the story's cue line.
    @Test fun aCutStoryGivesItsLastLineToTheCue() {
        assertEquals(3 to false, storyCueSplit(3, 3))
        assertEquals(2 to true, storyCueSplit(7, 3))
        assertEquals(1 to false, storyCueSplit(7, 1))
        assertEquals(0 to false, storyCueSplit(7, 0))
        assertEquals(2 to false, storyCueSplit(2, 5))
    }

    @Test fun dashAndClassicSayWhereTheWholeStoryIs() {
        val homes = src("WinScenesHomes2.kt")
        val dash = homes.substring(homes.indexOf("internal fun DashCard"), homes.indexOf("internal fun SideDrawer"))
        assertTrue(dash.contains("StoryWithCue("))
        assertFalse("Dash story cut silently", dash.contains("maxLines = 3, modifier = Modifier.padding(top = 8.dp)"))
        assertTrue(src("WinScenesMore.kt").contains("STORY_CUE"))
    }

    // D256: window-wide pictures in the scenes are decoded at the screen's size, not the cards'.
    @Test fun windowWidePicturesUseTheBackdropRequest() {
        val homes = src("WinScenesHomes2.kt")
        val spot = homes.substring(homes.indexOf("internal fun SpotPoster"), homes.indexOf("internal fun SpotPoster") + 2500)
        // (Batch 523: through Smart Fill, still with the backdrop request.)
        assertTrue(spot.contains("(ctx.backdrop ?: ctx.wide)?.let { SmartArt(it, null, ") && spot.contains("request = rememberBackdropRequest(it)"))
        for (f in listOf("WinScenesHomes2.kt", "WinScenesConsole.kt"))
            assertFalse(f, Regex("\\(ctx\\.backdrop \\?: ctx\\.wide[^)]*\\)\\?\\.let \\{ AsyncImage\\(rememberStyleArtRequest").containsMatchIn(src(f)))
    }

    // D262: Storage's details page on the plain paper — kept through the game-colour tint (476a lost it there: hardware
    // run k476a still showed 'Details' at 1.1 : 1).
    @Test fun storagePageKeepsItsPlainPaperThroughTheTint() {
        val s = src("GameWindow.kt")
        assertTrue(s.contains("keepsPalette = true, bandedPanel = true"))
        assertTrue(s.contains("stripes = stripes, keepsPalette = true, bandedPanel = bandedPanel"))
        assertTrue(s.contains("(if (look.bandedPanel) look.paper?.let { solid(it) } else null) ?: look.panel"))
    }

    // D254: the hero info plate is drawn whatever the picture layers (476a drew it only over a layered pane).
    @Test fun heroInfoAlwaysHasItsPlate() {
        val s = src("HeroCarousel.kt")
        assertTrue(s.contains(".background(heroTextPlate(right), RoundedCornerShape(14.dp))"))
        assertFalse(s.contains("if (layered) Modifier.background(heroTextPlate"))
    }

    @Test fun theCueIsReadableOnLightAndDarkWindows() {
        assertTrue(sceneCueBack(androidx.compose.ui.graphics.Color.White).red < 0.1f)
        assertTrue(sceneCueBack(androidx.compose.ui.graphics.Color(0xFF1E2A3A)).red > 0.9f)
    }
}
