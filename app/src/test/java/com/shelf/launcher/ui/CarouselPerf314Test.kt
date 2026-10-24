package com.shelf.launcher.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 314: the 3D cartridge carousel's off-screen check and the turned shell's picture placement. */
class CarouselPerf314Test {
    // --- pieceArtRect: where CartShellArt draws the front (a box of the shell's shape, the picture fitted in it) ---

    @Test fun pictureOfTheShellsOwnShapeFillsItsCentredBox() {
        // A 300 × 400 node, a 0.75 shell: the box is the whole node; a 480 × 640 front fills it.
        assertArrayEquals(intArrayOf(0, 0, 300, 400), pieceArtRect(300f, 400f, 0.75f, 480, 640))
    }

    @Test fun wideShellIsCentredVertically() {
        // SNES-like 1.55 shell in a 400 × 400 node: 400 × 258 box, 71 px from the top.
        assertArrayEquals(intArrayOf(0, 71, 400, 258), pieceArtRect(400f, 400f, 1.55f, 640, 413))
    }

    @Test fun tallShellIsCentredHorizontally() {
        // NES-like 0.895 shell in a 500 × 300 node: 269 × 300 box, 116 px from the left.
        val r = pieceArtRect(500f, 300f, 0.895f, 573, 640)
        assertArrayEquals(intArrayOf(116, 0, 269, 300), r)
    }

    @Test fun pictureSlightlyOffTheShellsShapeIsFittedInsideTheBox() {
        val r = pieceArtRect(400f, 400f, 1.55f, 640, 400)
        // Never past the box (0, 71, 400, 258).
        assertTrue(r[0] >= 0 && r[1] >= 71 && r[0] + r[2] <= 400 && r[1] + r[3] <= 71 + 258)
        assertTrue(r[2] == 400 || r[3] == 258)
    }

    @Test fun emptyPictureGivesTheBox() {
        assertArrayEquals(intArrayOf(0, 71, 400, 258), pieceArtRect(400f, 400f, 1.55f, 0, 0))
    }

    // --- pieceFrameRect: where AuthenticPackCart draws its front (update 316) ---

    @Test fun packFrontSitsAtItsFrameInTheCentredBox() {
        // A 1.5 shape in a 600 × 300 node: a 450 × 300 box, 75 px in; the front at 10 %–90 % across, 5 %–95 % down.
        assertArrayEquals(intArrayOf(75 + 45, 15, 360, 270), pieceFrameRect(600f, 300f, 1.5f, floatArrayOf(0.1f, 0.05f, 0.9f, 0.95f)))
    }

    @Test fun wholeFrameIsTheBox() {
        assertArrayEquals(intArrayOf(0, 71, 400, 258), pieceFrameRect(400f, 400f, 1.55f, floatArrayOf(0f, 0f, 1f, 1f)))
    }

    // --- the sides' picture (update 316) ---

    @Test fun sideAlphaMatchesTheSolidFilter() {
        // (Update 325: SIDE_CUT — a nearly clear background (under ~20 %) makes no sides; the shell's edge stays solid.)
        assertTrue(sideAlpha(0) == 0 && sideAlpha(40) == 0 && sideAlpha(255) == 255)
        assertTrue(sideAlpha(64) == 56 && sideAlpha(128) == 255)
    }

    @Test fun sideColourIsTheToneUnderTheSheen() {
        val tone = 0x406080
        // The middle: no sheen, the plastic itself.
        assertTrue(sideRowRgb(0.5f, tone) == tone)
        // The top: a little lighter; the bottom: a little darker.
        assertTrue(((sideRowRgb(0f, tone) shr 16) and 255) > 0x40)
        assertTrue((sideRowRgb(1f, tone) and 255) < 0x80)
    }

    // --- carouselOffScreen ---

    @Test fun gameOnTheScreenIsDrawn() {
        assertFalse(carouselOffScreen(960f, 200f, 1920f, 480f))
        assertFalse(carouselOffScreen(0f, 10f, 1920f, 0f))
        assertFalse(carouselOffScreen(1920f, 10f, 1920f, 0f))
    }

    @Test fun gameJustPastTheEdgeIsStillDrawnInsideTheMargin() {
        // Its right side 100 px left of the screen, inside a 480 px margin.
        assertFalse(carouselOffScreen(-300f, 200f, 1920f, 480f))
        assertFalse(carouselOffScreen(2220f, 200f, 1920f, 480f))
    }

    @Test fun gameFarPastEitherEdgeIsLeftOut() {
        assertTrue(carouselOffScreen(-1000f, 200f, 1920f, 480f))
        assertTrue(carouselOffScreen(3000f, 200f, 1920f, 480f))
    }

    @Test fun unknownScreenNeverLeavesAGameOut() {
        assertFalse(carouselOffScreen(-99999f, 1f, 0f, 0f))
        assertFalse(carouselOffScreen(Float.NaN, 1f, 1920f, 0f))
        assertFalse(carouselOffScreen(-99999f, Float.NaN, 1920f, 0f))
    }

    // --- "To the edge" packing (Flip 2: only 2 games each side) ---

    /** Games each side whose middle is within [half] middle-game widths of the middle, in Tucked behind. */
    private fun shown(side: Int, half: Float): Int = (1..60).count { d -> Carousel.offset(d.toFloat(), side) <= half }

    @Test fun toTheEdgeShowsAtLeastAsManyAsFiveEachSide() {
        // Flip 2, Stacks: about 1.8 middle-game widths from the middle to the screen's edge.
        for (half in floatArrayOf(1.5f, 1.8f, 2.5f, 4f)) {
            assertTrue("half $half", shown(Carousel.EDGE_SIDE, half) >= minOf(5, shown(5, half)))
        }
        assertTrue(shown(Carousel.EDGE_SIDE, 1.8f) >= 5)
    }

    @Test fun toTheEdgeKeepsItsBiggerGames() {
        assertTrue(Carousel.sideScale(Carousel.EDGE_SIDE) > Carousel.sideScale(5))
    }
}
