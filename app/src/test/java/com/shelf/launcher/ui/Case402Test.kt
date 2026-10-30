package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 402: (1) a printed box-front scan a few percent off the case's shape is still "the whole front" (PS2 Yu-Gi-Oh:
 * the console band was drawn twice), (2) the Cardboard box's selected case has no rectangle behind / round it, (3) a pick
 * flies straight from the case's real pose (no separate in-box rise), (4) the card is hidden until the piece lands.
 */
class Case402Test {
    private val ps2 = 0.7147f

    @Test fun boxFrontShapeTestIsUnchanged() {
        // (A 9 % slack trial for the PS2 Yu-Gi-Oh report was reverted: its band is baked into the source picture, not drawn twice by Voltcap.)
        assertTrue(looksLikeBoxFront(ps2, 0.7147f))
        assertTrue(looksLikeBoxFront(ps2, 0.70f))
        assertFalse(looksLikeBoxFront(ps2, 0.77f))
        assertEquals(0.045f, BOX_FRONT_SLACK, 0f)
    }

    @Test fun plainCoversAreStillFramed() {
        assertFalse(looksLikeBoxFront(ps2, 2f / 3f))  // SteamGridDB 600 x 900
        assertFalse(looksLikeBoxFront(ps2, 1.0f))     // square art
        assertFalse(looksLikeBoxFront(ps2, 0.5f))
        assertFalse(looksLikeBoxFront(ps2, null))
        assertFalse(looksLikeBoxFront(ps2, 0.82f))
    }

    @Test fun boxFrontIsNeverStretchedOnlyCroppedToTheFace() {
        for (scan in listOf(0.68f, 0.71f, 0.75f, 0.77f)) {
            val r = coverCropRect(1000, (1000 / scan).toInt(), ps2) ?: continue
            assertEquals(ps2, r[2].toFloat() / r[3], 0.02f)
            // never more than ~9 % of the scan is cut
            assertTrue(r[2] * r[3] >= 0.9f * 1000 * (1000 / scan).toInt())
        }
    }

    @Test fun pickStartsFromTheCaseRealPose() {
        assertEquals(90f, boxPickStartYaw(false), 0f)
        assertEquals(90f - BoxNav.OUT_TURN - 55f, boxPickStartYaw(true), 0f)
    }

    @Test fun cardStaysHiddenWhileThePieceFlies() {
        assertEquals(0f, MediaInspect.cardAlpha(0.5f, true), 0f)
        assertEquals(0.5f, MediaInspect.cardAlpha(0.5f, false), 0.001f)
        assertEquals(1f, MediaInspect.cardAlpha(0f, false), 0f)
    }

    @Test fun portraitCoverOnLandscapeN64FaceIsShownWholeNotZoomed() {
        // N64 case 1.425 vs SteamGridDB 2:3: only 47 % would survive a crop (the zoomed, stretched-looking slice).
        assertTrue(coverKeepFraction(2f / 3f, 1.425f) < COVER_MIN_KEEP)
        // the usual cases stay cropped: PS2 window 0.80, GameCube, SNES, DS, square art in a portrait window
        assertTrue(coverKeepFraction(2f / 3f, 0.80f) >= COVER_MIN_KEEP)
        assertTrue(coverKeepFraction(2f / 3f, 0.7147f) >= COVER_MIN_KEEP)
        assertTrue(coverKeepFraction(2f / 3f, 1.0f) >= COVER_MIN_KEEP)
        assertEquals(1f, coverKeepFraction(0.75f, 0.75f), 0f)
        assertEquals(1f, coverKeepFraction(0f, 0.75f), 0f)
        // symmetric: a landscape scan on a portrait face is judged the same
        assertEquals(coverKeepFraction(1.5f, 0.7f), coverKeepFraction(0.7f, 1.5f), 0.0001f)
    }

    private fun source(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText()
    }

    /** The regression for "a coloured block behind the selected game": the lit case draws no fill / outline of its own. */
    @Test fun cardboardBoxSelectedCaseHasNoPlateOrOutline() {
        val s = source("StyleCardboardBox.kt")
        val from = s.indexOf("private fun BoxCase(")
        val to = s.indexOf("private fun smoothUp")
        assertTrue(from > 0 && to > from)
        val body = s.substring(from, to).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        // the only .background( left is none; the only border is the closed spine's hairline gap
        // (Update 409, D97: the one background left is the closed spine's solid case body in the case's own side material.)
        assertEquals("only the solid case body", 1, Regex("[.]background[(]").findAll(body).count())
        assertTrue(body.contains(".background(boxSideTone(g.platform, BoxTones.map[k])"))
        assertFalse("a selection outline", body.contains("Color.White.copy(alpha = 0.85f)"))
        // (Update 409: the hairline gap is two vertical separators, not a rectangle round the case.)
        assertEquals("no rectangle border round a case", 0, Regex("[.]border[(]").findAll(body).count())
        assertTrue(body.contains("drawBehind"))
        // no flat dark row tint in the sheen
        val sheen = s.substring(s.indexOf("private fun BoxSheen("), s.indexOf("private fun BoxScene(")).lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(sheen.contains("drawRect(Color.Black.copy(alpha = 0.12f), a, sz)"))
    }

    @Test fun cardboardBoxPickDoesNotWaitForAnInBoxRise() {
        val s = source("StyleCardboardBox.kt")
        assertFalse("the 900 ms in-box wait is back", s.contains("kotlinx.coroutines.delay((900 / speed).toLong())"))
        assertTrue(s.contains("val target = when { flippedNow -> 1f; on -> peek; else -> 0f }"))
    }
}
