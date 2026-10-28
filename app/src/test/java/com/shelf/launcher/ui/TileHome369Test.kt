package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 369: Tile home's row geometry — the middle anchor and when the row glides or jumps. */
class TileHome369Test {
    private val tile = 160f; private val start = 24f; private val stride = 176f

    @Test fun focusedTileCentreIsTheRowCentreForEveryIndex() {
        for (rowW in listOf(1280f, 1240f, 960f)) for (i in 0..40) {
            val first = tileCenterAnchor(i, rowW, tile, start, stride)
            val centre = start + (i - first) * stride + tile / 2f
            assertEquals("row $rowW tile $i", rowW / 2f, centre, 0.01f)
        }
    }

    @Test fun oneStepMovesTheRowByExactlyOneStride() {
        val a = tileCenterAnchor(5, 1240f, tile, start, stride)
        val b = tileCenterAnchor(6, 1240f, tile, start, stride)
        assertEquals(1f, b - a, 0.0001f)
    }

    @Test fun aSingleStepGlidesALongJumpAndAResizeSnap() {
        val vis = 6
        assertFalse(tileScrollSnaps(stride * 5, stride * 4, stride, vis, resized = false))
        assertTrue(tileScrollSnaps(stride * 30, 0f, stride, vis, resized = false))
        assertTrue("a newly measured width must not glide", tileScrollSnaps(stride * 4 + 7f, stride * 4, stride, vis, resized = true))
    }
}
