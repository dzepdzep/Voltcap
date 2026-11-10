package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 520 (flat case art): every flat case view fits a picture the way the 3D faces do (update 402, CropUnlessMuchCut:
 * centred fill-crop while it keeps at least COVER_MIN_KEEP, else shown whole), and a picture whose shape decides what is drawn
 * (a whole wrap / unfolded box scan, cut to its front) loads hidden until it is measured — no flash of the whole scan first.
 */
class Batch520Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    @Test fun wideImageInACoverWindowIsShownWhole() {
        // A 16:9 picture you picked in a 0.7 cover window: a centre crop would keep 39 %.
        assertTrue(coverKeepFraction(16f / 9f, 0.7f) < COVER_MIN_KEEP)
        val k = CropUnlessMuchCut.computeScaleFactor(Size(1600f, 900f), Size(70f, 100f))
        assertEquals(70f / 1600f, k.scaleX, 1e-6f) // fit: the whole width
        assertEquals(k.scaleX, k.scaleY, 0f) // never stretched
    }

    @Test fun coverOfNearlyTheWindowsShapeStillFills() {
        val k = CropUnlessMuchCut.computeScaleFactor(Size(700f, 1000f), Size(72f, 100f))
        assertEquals(maxOf(72f / 700f, 100f / 1000f), k.scaleX, 1e-6f) // fill-crop as before
        assertEquals(k.scaleX, k.scaleY, 0f)
    }

    @Test fun flatCaseViewsUseTheSharedFit() {
        val c = src("ui/CoverCases.kt")
        // Plain keep / jewel case (PS3–PS5, Xbox …): no longer a plain centre crop.
        assertTrue(c.contains("cardArtModel(shown), null, contentScale = CropUnlessMuchCut, alignment = Alignment.Center,"))
        assertFalse(c.contains("cardArtModel(shown), null, contentScale = ContentScale.Crop, alignment = Alignment.Center,"))
        val s = src("ui/ShelfHome.kt")
        assertTrue(s.contains("AsyncImage(coverModel(cover), g.name, contentScale = CropUnlessMuchCut, modifier = Modifier.fillMaxSize().pixelArtwork())"))
    }

    @Test fun unmeasuredWrapsAndUnfoldedScansLoadHidden() {
        val c = src("ui/CoverCases.kt")
        assertTrue(c.contains("val probing = platform != null && cutLeft <= 0f && scan is String && BoxTexture.parse(scan) == null && raw == null"))
        assertTrue(c.contains("val scanIn = loaded && !probing"))
        assertTrue(c.contains("graphicsLayer { alpha = if (probing) 0f else 1f }"))
        assertTrue(c.contains("alpha = if (hidden) 0f else 1f"))
        assertTrue(c.contains("art != null && artShown && !hidden && !icon && !moved && owner != null -> Modifier"))
        for (f in listOf("ui/ShelfHome.kt", "ui/Carousel.kt")) assertTrue(f, src(f).contains("hidden = wrapShapePending(cover, g.platform)"))
        assertTrue(src("ui/RealMedia.kt").contains("internal fun wrapShapePending(cover: Any?, platform: String?): Boolean"))
    }

    @Test fun nonStringOrKnownCoversAreNeverHidden() {
        assertFalse(wrapShapePending(null, "ps2"))
        assertFalse(wrapShapePending(Any(), "ps2"))
        assertFalse(wrapShapePending("https://x/y.png", null))
    }

    @Test fun statusMessagesStayLongEnoughToRead() {
        assertEquals(3500L, statusReadMs("Connected to ScreenScraper."))
        assertTrue(statusReadMs("x".repeat(160)) > 8000L)
        assertEquals(10_000L, statusReadMs("x".repeat(1000)))
        val l = src("ui/LauncherScreen.kt")
        assertTrue(l.contains("val accountWindowOpen = showScreenScraper || showIgdb || showRawg || showTgdb"))
        assertTrue(l.contains("if (message != null && !accountWindowOpen) {"))
        assertFalse(l.contains("kotlinx.coroutines.delay(3500)\n            viewModel.clearStatusMessage()"))
    }

    @Test fun screenScraperCheckIsOnePressAndSaysItCanBeSlow() {
        val d = src("ui/ScreenScraperDialog.kt")
        assertTrue(d.contains("val testing = statusMessage == SS_TESTING_MESSAGE"))
        assertTrue(d.contains("enabled = !testing && if (builtInKey)"))
        assertTrue(src("LauncherViewModel.kt").contains("statusMessage = com.shelf.launcher.ui.SS_TESTING_MESSAGE"))
    }

    @Test fun darkColourLogosOnTheCollectorBackBarArePrintedWhite() {
        val ps4Blue = CollectorBacks.relLuminance(0xFF003791.toInt()).toFloat()
        assertTrue(CollectorBacks.barContrast(ps4Blue) < 3f)
        assertTrue(CollectorBacks.barContrast(CollectorBacks.relLuminance(0xFFFFFFFF.toInt()).toFloat()) > 15f)
        assertTrue(CollectorBacks.barContrast(CollectorBacks.relLuminance(0xFF107C10.toInt()).toFloat()) >= 3f) // Xbox green stays
        assertTrue(src("ui/CollectorBacks.kt").contains("} else if (logoTooDarkOnBar(logo)) {"))
    }

    @Test fun aFailedWrapCutShowsThePictureNotAnEmptyCase() {
        val url = "https://example.invalid/wide-${System.nanoTime()}.png"
        assertFalse(BoxTexture.frontCutFailed(url))
        BoxTexture.noteFrontCutFailed(url)
        assertTrue(BoxTexture.frontCutFailed(url))
        assertFalse(BoxTexture.frontCutFailed(null))
        assertTrue(src("ui/RealMedia.kt").contains("looksUnfolded(raw, real) && !BoxTexture.frontCutFailed(s)"))
        assertTrue(src("ui/CoverCases.kt").contains("looksUnfolded(raw, hi) && !BoxTexture.frontCutFailed(scan)"))
        assertTrue(src("ui/BoxTexture.kt").contains("if (part == BoxPart.FRONT && result is coil.request.ErrorResult && BoxTexture.cutOf(chain.request.data) == null) BoxTexture.noteFrontCutFailed(url)"))
    }

    @Test fun spacingTileUsesEachLayoutsOwnRange() {
        val ranges = mapOf(
            com.shelf.launcher.data.HomeLayout.CASEFLOW to (60 to 160), com.shelf.launcher.data.HomeLayout.BOX to (70 to 150),
            com.shelf.launcher.data.HomeLayout.STORAGE to (60 to 150), com.shelf.launcher.data.HomeLayout.FILESELECT to (60 to 150),
            com.shelf.launcher.data.HomeLayout.CLASSIC to (50 to 300), com.shelf.launcher.data.HomeLayout.STACKS to (50 to 250))
        for ((l, r) in ranges) { assertEquals("$l", r.first, R1Spacing.minFor(l)); assertEquals("$l", r.second, R1Spacing.maxFor(l)) }
        // the layouts' own sliders keep the same ranges
        assertTrue(src("ui/StyleCaseFlow.kt").contains("Features.cfSpacing.value = it.coerceIn(60, 160) }, min = 60, max = 160"))
        assertTrue(src("ui/StyleCardboardBox.kt").contains("Features.boxSpacing.value = it.coerceIn(70, 150) }, min = 70, max = 150"))
        assertTrue(src("ui/StyleStorageBoxes.kt").contains("Features.stSpacing.value = it.coerceIn(60, 150) }, min = 60, max = 150"))
        assertTrue(src("ui/StyleFileSelect.kt").contains("Features.fsSpacing.value = it.coerceIn(60, 150) }, min = 60, max = 150"))
        assertFalse(src("ui/QuickSettings.kt").contains("R1Spacing.MIN, R1Spacing.maxFor"))
    }

    @Test fun serverTroubleIsNotReportedAsNotFound() {
        val f: (String?) -> String? = { com.shelf.launcher.data.screenScraperFailureNote(it) }
        assertEquals(null, f(null))
        assertEquals(null, f(com.shelf.launcher.data.OUTCOME_FOUND))
        assertEquals(null, f(com.shelf.launcher.data.OUTCOME_UNKNOWN))
        assertTrue(f(com.shelf.launcher.data.OUTCOME_OFFLINE)!!.contains("didn't answer"))
        assertTrue(f("ScreenScraper answered 504")!!.contains("in time"))
        assertTrue(f("ScreenScraper answered 430")!!.contains("limit"))
        assertTrue(f("ScreenScraper answered 403")!!.contains("username and password"))
        assertTrue(f("ScreenScraper answered 503")!!.contains("busy"))
        assertTrue(f("ScreenScraper answered 500")!!.contains("(500)"))
        assertTrue(src("LauncherViewModel.kt").contains("source == MetadataSource.SCREENSCRAPER && com.shelf.launcher.data.screenScraperFailureNote(screenScraperRepository.lookupOutcome[key]) != null ->"))
    }

    @Test fun gameGridSpacingDefaultsToTodaysGaps() {
        assertEquals(100, com.shelf.launcher.data.Features.gridSpacing.default)
        assertEquals(50, R1Spacing.minFor(com.shelf.launcher.data.HomeLayout.GRID)); assertEquals(250, R1Spacing.maxFor(com.shelf.launcher.data.HomeLayout.GRID))
        val g = src("ui/GridHome.kt")
        assertTrue(g.contains("val spacingK = if (secondScreen || spines) 1f else com.shelf.launcher.data.Features.gridSpacing.state.value / 100f"))
        assertTrue(g.contains("val gap = if (spines) 4.dp else if (secondScreen) 10.dp else 14.dp * spacingK"))
        assertTrue(g.contains("verticalArrangement = Arrangement.spacedBy(if (secondScreen) 10.dp else 12.dp * spacingK)"))
        assertTrue(src("ui/SettingsDialog.kt").contains("com.shelf.launcher.data.Features.gridSpacing.value = it.coerceIn(50, 250) }, min = 50, max = 250"))
    }

    @Test fun gridUpDownStayPutInASingleRow() {
        assertFalse(gridLoopRowMoves(3, 4)) // 3 games, 4 across: one row
        assertFalse(gridLoopRowMoves(4, 4)) // exactly one full row
        assertFalse(gridLoopRowMoves(1, 4))
        assertTrue(gridLoopRowMoves(5, 4)) // two rows: Endless moves a row as before
        assertTrue(gridLoopRowMoves(40, 6))
        assertTrue(src("ui/LauncherScreen.kt").contains("if (state.appearance.loopRow && gridLoopRowMoves(n, cols)) { viewModel.moveFocus(if (down) cols else -cols)"))
    }

    @Test fun heroTilesAndCinemaSpacingDefaultToTodaysGaps() {
        assertEquals(100, com.shelf.launcher.data.Features.heroTilesSpacing.default)
        assertEquals(100, com.shelf.launcher.data.Features.cnSpacing.default)
        assertTrue(src("ui/StyleHeroTiles.kt").contains("val gap = (if (maxHeight < 160.dp) 8.dp else 12.dp) * (Features.heroTilesSpacing.state.value / 100f)"))
        assertTrue(src("ui/StyleCinema.kt").contains("val gap = 8.dp * (Features.cnSpacing.state.value / 100f)"))
        for (l in listOf(com.shelf.launcher.data.HomeLayout.HERO_TILES, com.shelf.launcher.data.HomeLayout.CINEMA)) {
            assertEquals(50, R1Spacing.minFor(l)); assertEquals(250, R1Spacing.maxFor(l))
        }
    }
}
