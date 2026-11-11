package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 523b: Smart Fill (popup pictures fill their room: a light crop, else whole over a soft copy — never bars, never
 * stretched) and pinch zoom on what came out of an inspected case (it had none: the case's handler is on a sibling).
 */
class Batch523bTest {
    @Test fun smartFillCropsOnlyWhenLittleIsLost() {
        assertEquals(0f, SmartFill.cropLoss(0.7f, 0.7f), 1e-4f)
        assertTrue(SmartFill.fills(0.70f, 0.75f))            // a portrait cover in a portrait room: crop, fills
        assertFalse(SmartFill.fills(0.70f, 1f))              // the WWE case: portrait cover in a square tile -> whole + soft copy
        assertFalse(SmartFill.fills(1.78f, 0.72f))           // a wide picture in a cover room
        assertTrue(SmartFill.fills(1.6f, 1.78f))             // wide in wide
        assertEquals(1f - 0.7f, SmartFill.cropLoss(0.7f, 1f), 1e-4f)
        assertEquals(0f, SmartFill.cropLoss(Float.NaN, 1f), 0f) // unknown shape: never a crop decision from garbage
    }

    @Test fun portraitCropKeepsTheUpperPart() {
        assertTrue(SmartFill.cropBias(0.7f, 0.8f) < 0f)       // taller than its room: biased up (logo / title)
        assertEquals(0f, SmartFill.cropBias(1.9f, 1.78f), 0f) // wider than its room: centred
    }

    @Test fun liftedTileFollowsThePicturesShape() {
        assertEquals(200f to 200f, tileLiftSize(200f, null, 500f))          // unknown: the square, as before
        val (w, h) = tileLiftSize(200f, 0.7f, 500f)                         // portrait cover
        assertEquals(172f, w, 0.01f); assertTrue(h > w && h <= 300f)       // narrower, taller, max 1.5 tiles
        assertTrue(SmartFill.cropLoss(0.7f, w / h) <= SmartFill.MAX_CROP)   // -> the cover fills it (no bars)
        val (_, hw) = tileLiftSize(200f, 1.78f, 500f)                       // wide art: a lower tile, never under 0.62
        assertTrue(hw in 124f..200f)
        assertTrue(tileLiftSize(200f, 0.3f, 220f).second <= 220f)           // never past the room it has
    }

    @Test fun extractedPieceZoomsAndComesBack() {
        val m = InspectMotion()
        m.started = true
        m.contentPinchOk(0f)                                                  // still in the case: no zoom
        assertEquals(1f, m.contentZoom.floatValue, 0f)
        m.out.floatValue = 1f
        m.pinchContent(2f, 30f, -20f, 300f, 300f)
        assertEquals(2f, m.contentZoom.floatValue, 1e-4f)
        assertEquals(30f, m.contentPanX.floatValue, 1e-4f)
        m.pinchContent(10f, 0f, 0f, 300f, 300f)
        assertEquals(INSPECT_ZOOM_MAX, m.contentZoom.floatValue, 1e-4f)    // limited
        m.pinchContent(1f, 5000f, 5000f, 300f, 300f)                        // never panned off its place
        assertEquals((INSPECT_ZOOM_MAX - 1f) * 150f, m.contentPanX.floatValue, 1e-3f)
        assertEquals(1f, m.zoom.floatValue, 0f)                              // the case's own zoom untouched
        m.resetZoom()                                                         // Y / long press: everything back
        assertEquals(1f, m.contentZoom.floatValue, 0f); assertEquals(0f, m.contentPanY.floatValue, 0f)
    }

    private fun InspectMotion.contentPinchOk(o: Float) { out.floatValue = o; pinchContent(2f, 0f, 0f, 100f, 100f) }
}
