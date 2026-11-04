package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/** Update 458 (D175): a turned cartridge's sides stay solid edge-on, no squeezed face, its copies cut to one straight top / bottom edge. */
class SideEdge458Test {
    private fun cosD(d: Float) = cos(Math.toRadians(d.toDouble())).toFloat()

    @Test fun sideCopiesNeverEdgeOn() {
        for (y in listOf(90f, -90f, 270f, 89.5f, 90.5f, 450f, -270f)) {
            val s = edgeOnSafeYaw(y)
            assertTrue("$y -> $s", abs(cosD(s)) >= EDGE_ON_MIN_COS - 1e-4f)
            // the same side keeps facing you
            assertTrue("$y -> $s", (cosD(s) >= 0f) == (cosD(y) >= 0f) || abs(cosD(y)) < 1e-6f)
            assertTrue("$y -> $s moved little", abs(s - y) < 3f)
        }
        // away from edge-on: untouched
        for (y in listOf(0f, 45f, 75f, 120f, 180f, -60f)) assertEquals(y, edgeOnSafeYaw(y), 0f)
    }

    @Test fun faceFadesOnlyEdgeOn() {
        assertEquals(0f, edgeOnFade(0f), 0f)
        assertEquals(1f, edgeOnFade(cosD(75f)), 0f)
        assertEquals(1f, edgeOnFade(-1f), 0f)
        assertTrue(edgeOnFade(cosD(87f)) in 0.01f..0.99f)
    }

    @Test fun wiredInBothRenderers() {
        val src = listOf(java.io.File("../src_copy/ui"), java.io.File("src/main/java/com/shelf/launcher/ui"), java.io.File("app/src/main/java/com/shelf/launcher/ui"))
            .first { it.exists() }
        val solid = java.io.File(src, "SolidPiece.kt").readText()
        assertTrue(solid.contains("edgeLeaf.rotationY = sideYaw"))
        assertTrue(solid.contains("litLeaf.alpha = faceA"))
        // the copies cut to the side's own top / bottom edge (the saw-tooth along a turned GBA cartridge's side)
        assertTrue(solid.contains("clipPath(cut) { drawSideCopies("))
        assertTrue(solid.contains("class SideClip"))
        assertTrue(solid.contains("drawPath(sideClip.wall"))
        val inspect = java.io.File(src, "MediaInspectView.kt").readText()
        assertTrue(inspect.contains("lit.alpha = faceA"))
    }
}
