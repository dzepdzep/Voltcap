package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 529s (owner P3): Storage boxes' sprite pickup - one sprite from the box slot to the stand and back. */
class StorageFlight529Test {
    private val slot = Rect(100f, 400f, 140f, 440f)
    private val stand = Rect(300f, 150f, 420f, 270f)

    @Test fun startsExactlyOnTheBoxSlot() {
        val (dx, dy, s) = storageSpriteFlight(slot, stand, 0f)
        assertEquals(slot.center.x, stand.center.x + dx, 0.01f)
        assertEquals(slot.center.y, stand.center.y + dy, 0.01f)
        assertEquals(slot.width, stand.width * s, 0.01f)
    }

    @Test fun endsExactlyOnTheStand() {
        val (dx, dy, s) = storageSpriteFlight(slot, stand, 1f)
        assertEquals(0f, dx, 0.01f); assertEquals(0f, dy, 0.01f); assertEquals(1f, s, 0.0001f)
    }

    @Test fun travelsOnAnArcWithoutJumps() {
        var last = storageSpriteFlight(slot, stand, 0f)
        var liftedMid = false
        for (i in 1..100) {
            val k = i / 100f
            val now = storageSpriteFlight(slot, stand, k)
            // No step bigger than a few percent of the trip between neighbouring frames.
            assertTrue(kotlin.math.abs(now.first - last.first) < 10f && kotlin.math.abs(now.second - last.second) < 12f)
            val straightY = (slot.center.y - stand.center.y) * (1f - k)
            if (k in 0.4f..0.6f && now.second < straightY - 10f) liftedMid = true
            last = now
        }
        assertTrue("rises on the way", liftedMid)
    }

    @Test fun standCopyHiddenOnlyWhileTravelling() {
        assertTrue(storageSlotHidden(true, 0f)); assertTrue(storageSlotHidden(true, 0.5f))
        assertFalse(storageSlotHidden(true, 1f)); assertFalse(storageSlotHidden(false, 0.3f))
    }

    @Test fun boxCopyHiddenForTheWholeTrip() {
        assertEquals(0f, caseHomeHidden(0f), 0f); assertEquals(1f, caseHomeHidden(0.01f), 0f); assertEquals(1f, caseHomeHidden(1f), 0f)
    }

    @Test fun tripTimes() {
        assertEquals(520, pickupFlightMs(320, false, true, open = true)); assertEquals(420, pickupFlightMs(260, false, true, open = false))
        assertEquals(470, pickupFlightMs(320, true, false, open = true)); assertEquals(320, pickupFlightMs(320, false, false, open = true))
    }

    @Test fun leavesAsTheHomesPictureAndLandsAsIt() {
        // The home's own picture at both ends of the trip, the window's picture once it has arrived; no jump in between.
        assertEquals(0f, pickupMorph(0f), 0f); assertEquals(0f, pickupMorph(0.2f), 0f)
        assertEquals(1f, pickupMorph(0.8f), 0f); assertEquals(1f, pickupMorph(1f), 0f)
        for (i in 1..100) assertTrue(kotlin.math.abs(pickupMorph(i / 100f) - pickupMorph((i - 1) / 100f)) <= 0.021f)
    }

    @Test fun theLandedSpriteIsHandedOverBeforeTheWindowGoes() {
        val g = listOf(java.io.File("../src_copy/ui/GameWindow.kt"), java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/GameWindow.kt")).first { it.exists() }.readText()
        assertTrue(g.contains("if (spriteFlight || tvFlight) { closedHandOver[0] = true; pickupHandBack(); androidx.compose.runtime.withFrameNanos { } }"))
    }

    @Test fun theHomeCopyGoesOnTheTripsOwnFrames() {
        // Hidden on the very frame the object first moves (the animation's frame), shown again only when handed back.
        pickupFrame(0f); assertEquals(1f, MediaInspect.cardAlpha(MediaInspect.amount.floatValue, MediaInspect.hold.value), 0f)
        pickupFrame(0.004f); assertEquals(0f, MediaInspect.cardAlpha(MediaInspect.amount.floatValue, MediaInspect.hold.value), 0f)
        pickupFrame(0.5f); assertEquals(0f, MediaInspect.cardAlpha(MediaInspect.amount.floatValue, MediaInspect.hold.value), 0f)
        assertEquals(0.5f, MediaInspect.amount.floatValue, 0f) // (the others round it follow the trip)
        pickupFrame(0.01f); assertEquals(0f, MediaInspect.cardAlpha(MediaInspect.amount.floatValue, MediaInspect.hold.value), 0f)
        pickupHandBack(); assertEquals(1f, MediaInspect.cardAlpha(MediaInspect.amount.floatValue, MediaInspect.hold.value), 0f)
        val g = listOf(java.io.File("../src_copy/ui/GameWindow.kt"), java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/GameWindow.kt")).first { it.exists() }.readText()
        assertTrue(g.contains("{ if (spriteFlight && originLayer != null) pickupFrame(value) }"))
        assertTrue(g.contains("grow.animateTo(1f, tween(TV_FLIGHT_OPEN_MS, easing = LinearEasing)) { pickupFrame(value) }"))
    }

    @Test fun theSleeveTurnsOverAtItsPlaceNeverShowingTwoSides() {
        // Front facing you as it leaves, edge-on exactly when the back starts to turn in, back facing you at the end.
        assertEquals(0f, flipTurn(0f), 0f); assertEquals(0f, flipTurn(0.3f), 0f); assertEquals(1f, flipTurn(FLIP_EDGE), 0f)
        assertEquals(1f, flipBackTurn(FLIP_EDGE), 0f); assertEquals(0f, flipBackTurn(1f), 0f)
        for (i in 1..100) {
            val a = i / 100f; val b = (i - 1) / 100f
            assertTrue(kotlin.math.abs(flipTurn(a) - flipTurn(b)) < 0.06f && kotlin.math.abs(flipBackTurn(a) - flipBackTurn(b)) < 0.08f)
        }
    }
}
