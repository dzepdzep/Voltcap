package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shelf 3D work (12 Nov): the pulled-out case's pose and its projected faces, with the same camera as Case3D's CaseDraw. Geometry
 * only - what the faces look like with their pictures and light on a handheld is NOT tested here (hardware pending).
 */
class ShelfCase3DTest {
    // A KONKR-like Game shelf: a DVD-style case on the shelf 120 px tall, its spine 11 px wide; out it is 260 px tall.
    private val slotCx = 300f; private val slotBottom = 400f; private val slotH = 120f; private val slotW = 11f
    private val bigCx = 320f; private val bigTop = 60f; private val bigH = 260f
    private val shape = 0.7f; private val depth = slotW / slotH
    private fun ease(t: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
    private val slide = { p: Float -> ease(p / 0.3f) }
    private val turn = { p: Float -> ease((p - 0.3f) / 0.4f) }
    private val grow = { p: Float -> ease((p - 0.62f) / 0.38f) }
    private fun pose(p: Float) = shelfCasePose(p, slotCx, slotBottom, slotH, bigCx, bigTop, bigH, shape, depth, 0.42f, slide, turn, grow)

    private enum class F { FRONT, SPINE, EDGE, TOP, BACK, BOTTOM }
    private fun faces(pp: ShelfCasePose) = shelfCaseFaces(pp.h * shape, pp.h, pp.h * depth, pp.yaw, pp.pitch)
    private fun width(q: FloatArray) = maxOf(q[0], q[2], q[4], q[6]) - minOf(q[0], q[2], q[4], q[6])
    private fun height(q: FloatArray) = maxOf(q[1], q[3], q[5], q[7]) - minOf(q[1], q[3], q[5], q[7])

    @Test fun handOverLooksExactlyLikeTheShelfsSpine() {
        val pp = pose(0.0001f)
        assertEquals(90f, pp.yaw, 0.01f); assertEquals(0f, pp.pitch, 0.01f)
        val f = faces(pp)
        val spine = f[F.SPINE.ordinal]
        assertTrue("the spine faces you", spine.second)
        assertFalse("the front is edge-on, not drawn", f[F.FRONT.ordinal].second && width(f[F.FRONT.ordinal].first) > 0.5f)
        assertEquals("spine height = the slot's", slotH, height(spine.first), slotH * 0.01f)
        assertEquals("spine width = the slot's", slotW, width(spine.first), 0.5f)
        assertEquals(slotCx, pp.cx, 0.01f)
    }

    @Test fun endIsExactlyTheBigFront() {
        val pp = pose(1f)
        assertEquals(0f, pp.yaw, 0.01f); assertEquals(0f, pp.pitch, 0.01f)
        val front = faces(pp)[F.FRONT.ordinal]
        assertTrue(front.second)
        assertEquals(bigH, height(front.first), bigH * 0.01f)
        assertEquals(bigH * shape, width(front.first), bigH * shape * 0.01f)
        assertEquals(bigCx, pp.cx, 0.01f)
        assertEquals(bigTop + bigH / 2f, pp.cy, 0.01f)
    }

    @Test fun continuousEveryFrameNoJumps() {
        // 1 000 samples (a 620 ms pull at 60 fps is ~37 frames: each sample is far finer than a frame)
        var prev = pose(0f)
        for (i in 1..1000) {
            val pp = pose(i / 1000f)
            assertTrue("yaw jump at $i", abs(pp.yaw - prev.yaw) < 1.5f)
            assertTrue("pitch jump at $i", abs(pp.pitch - prev.pitch) < 0.5f)
            assertTrue("size jump at $i", abs(pp.apparentH - prev.apparentH) < 2f)
            assertTrue("move jump at $i", hypot(pp.cx - prev.cx, pp.cy - prev.cy) < 3f)
            assertTrue("the turn never goes back", pp.yaw <= prev.yaw + 1e-4f)
            prev = pp
        }
    }

    @Test fun rigidAndNeverShowsItsBackOrAMirroredFront() {
        for (i in 0..200) {
            val pp = pose(i / 200f)
            val f = faces(pp)
            assertFalse("back seen at ${i / 200f}", f[F.BACK.ordinal].second)
            assertFalse("the far edge seen at ${i / 200f}", f[F.EDGE.ordinal].second)
            val front = f[F.FRONT.ordinal]
            if (front.second) assertTrue("front mirrored at ${i / 200f}", front.first[2] > front.first[0])   // TR right of TL
            // one rigid box: the spine's front edge IS the front's left edge (TL / BL shared) in every pose
            val sp = f[F.SPINE.ordinal].first; val fr = front.first
            assertEquals(0f, hypot(sp[2] - fr[0], sp[3] - fr[1]), 0.01f)
            assertEquals(0f, hypot(sp[4] - fr[6], sp[5] - fr[7]), 0.01f)
        }
    }

    @Test fun topShowsOnlyWhileItTurns() {
        assertFalse(faces(pose(0.0001f))[F.TOP.ordinal].let { it.second && height(it.first) > 0.5f })
        assertTrue(faces(pose(pFor(0.5f)))[F.TOP.ordinal].second)                 // mid-turn: tipped toward you, its top shows
        assertFalse(faces(pose(1f))[F.TOP.ordinal].let { it.second && height(it.first) > 0.5f })
    }

    @Test fun goingBackRetracesTheSameWayToTheSlot() {
        val back = pose(0.0001f)
        assertEquals(slotCx, back.cx, 0.01f); assertEquals(slotH, back.apparentH, 0.05f)
        for (i in 0..100) assertEquals(pose(i / 100f), pose(i / 100f))           // a function of the pull alone: the way back is the way out
    }

    @Test fun handOverRule() {
        val W = Shelf3D.Mode.WAITING; val C = Shelf3D.Mode.CASE_3D; val FL = Shelf3D.Mode.FLAT
        assertEquals(W, Shelf3D.decide(W, ready = false, turn = 0f))               // still made, still rising: wait (flat spine shows)
        assertEquals(C, Shelf3D.decide(W, ready = true, turn = 0f))                // whole and still spine-on: take over
        assertEquals(FL, Shelf3D.decide(W, ready = false, turn = 0.01f))           // not whole when the turn starts: flat, whole pull
        assertEquals(FL, Shelf3D.decide(W, ready = true, turn = 0.01f))            // never a swap mid-turn
        assertEquals(C, Shelf3D.decide(C, ready = false, turn = 0.5f))             // decided stays decided
        assertEquals(FL, Shelf3D.decide(FL, ready = true, turn = 0f))
    }

    @Test fun nearFaceIsWhatTheCameraEnlarges() {
        for (yaw in listOf(0f, 30f, 60f, 90f)) {
            val k = nearFaceCorrection(yaw, shape, depth)
            assertTrue(k in 0.8f..1f)
        }
        assertTrue(nearFaceCorrection(90f, shape, depth) < nearFaceCorrection(0f, shape, depth))
    }

    /** Evidence (not a check): the projected case at the owner's frames, written to build/shelf3d/frames.png. */
    @Test fun contactSheet() {
        val frames = listOf("on shelf" to 0f, "extracting" to 0.12f, "partly out" to 0.28f, "turn 25%" to pFor(0.25f), "turn 50%" to pFor(0.5f),
            "turn 75%" to pFor(0.75f), "turned" to 0.7f, "out" to 1f, "back 75%" to pFor(0.75f), "back 25%" to pFor(0.25f), "returned" to 0f)
        val cw = 220; val ch = 300
        val img = java.awt.image.BufferedImage(cw * frames.size, ch, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val gr = img.createGraphics()
        gr.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        val colours = mapOf(F.FRONT to java.awt.Color(0x3A6FD8), F.SPINE to java.awt.Color(0xD84A3A), F.EDGE to java.awt.Color(0x888888),
            F.TOP to java.awt.Color(0xE8E2D0), F.BACK to java.awt.Color(0x222222), F.BOTTOM to java.awt.Color(0x555555))
        frames.forEachIndexed { n, (name, p) ->
            gr.color = java.awt.Color(0x15100A); gr.fillRect(n * cw, 0, cw, ch)
            val pp = pose(p)
            // scaled into the cell: the shelf's board line and the slot
            val s = 0.6f; val ox = n * cw + cw / 2f - pp.cx * s + (pp.cx - slotCx) * s * 0.25f; val oy = 20f
            gr.color = java.awt.Color(0x6B4A2E); gr.fillRect(n * cw, (oy + slotBottom * s).toInt(), cw, 6)
            faces(pp).forEachIndexed { fi, (q, vis) ->
                if (!vis) return@forEachIndexed
                val poly = java.awt.Polygon()
                for (i in 0 until 4) poly.addPoint((ox + (pp.cx + q[i * 2]) * s).toInt(), (oy + (pp.cy + q[i * 2 + 1]) * s).toInt())
                gr.color = colours.getValue(F.values()[fi]); gr.fillPolygon(poly)
                gr.color = java.awt.Color.WHITE; gr.drawPolygon(poly)
                if (F.values()[fi] == F.FRONT && width(q) > 12f) {     // an "F" on the front shows it is never mirrored
                    gr.drawString("F", (ox + (pp.cx + q[0]) * s + 4).toInt(), (oy + (pp.cy + q[1]) * s + 14).toInt())
                }
            }
            gr.color = java.awt.Color.WHITE
            gr.drawString("$name  p=${"%.2f".format(p)}  yaw=${"%.0f".format(pp.yaw)}", n * cw + 6, ch - 10)
        }
        gr.dispose()
        val out = java.io.File("build/shelf3d"); out.mkdirs()
        javax.imageio.ImageIO.write(img, "png", java.io.File(out, "frames.png"))
    }

    /** The pull at which the turn has reached [t]. */
    private fun pFor(t: Float): Float { var lo = 0.3f; var hi = 0.7f; repeat(40) { val m = (lo + hi) / 2; if (turn(m) < t) lo = m else hi = m }; return (lo + hi) / 2 }

    @Test fun handOverBlendIsSmoothAndShort() {
        assertEquals(0f, Shelf3D.blendIn(0f), 0f)                       // the hand-over frame: only the shelf's spine
        assertEquals(1f, Shelf3D.blendIn(Shelf3D.BLEND), 0f)            // done early in the turn, while the two coincide
        assertEquals(1f, Shelf3D.blendIn(0.9f), 0f)
        var prev = 0f
        for (i in 1..200) { val v = Shelf3D.blendIn(i / 1000f); assertTrue(v >= prev); assertTrue(v - prev < 0.02f); prev = v }
        // the slope starts and ends flat (smoothstep): no visible step in or out
        assertTrue(Shelf3D.blendIn(0.002f) < 0.001f)
        // the shelf's spine stays whole under the fading-in 3D case, and goes only once that is whole
        assertEquals(1f, Shelf3D.shelfSpineAlpha(0f), 0f); assertEquals(1f, Shelf3D.shelfSpineAlpha(Shelf3D.BLEND * 0.99f), 0f)
        assertEquals(0f, Shelf3D.shelfSpineAlpha(Shelf3D.BLEND), 0f)
    }

    /** Hardware QA, 12 Nov (Flip 2): the 3D case only where it draws the shelf's own spine and front - no artwork substitution. */
    @Test fun threeDOnlyWithTheShelfsOwnPictures() {
        val scans = listOf("Box" to "front.jpg", "Spine" to "spine.jpg")
        assertTrue(Shelf3D.artSourcesAgree(scans, scans, "PS1"))
        // no scans (the QA library on the Flip 2: cover strips and the console's case art on the shelf): the shelf's path
        assertFalse(Shelf3D.artSourcesAgree(emptyList(), emptyList(), "PS1"))
        assertFalse(Shelf3D.artSourcesAgree(listOf("Box" to "front.jpg"), listOf("Box" to "front.jpg"), "PS1"))       // no spine of its own
        assertFalse(Shelf3D.artSourcesAgree(listOf("Spine" to "spine.jpg"), listOf("Spine" to "spine.jpg"), "PS1"))   // no scanned front
        // a picture one side leaves out (a flat stand-in) gives a different picture: the shelf's path
        assertFalse(Shelf3D.artSourcesAgree(scans, listOf("Box" to "front.jpg"), "PS1"))
        assertFalse(Shelf3D.sameArtSources("PS1", null))                           // scans not known yet
        assertFalse(Shelf3D.sameArtSources("Android", scans))                     // Voltcap's own Android case
    }

    /** Hardware QA, 12 Nov: a scan Case3D would visibly cut to the real case's shape keeps the shelf's path. */
    @Test fun threeDOnlyWhereTheScanFitsTheRealCase() {
        assertTrue(Shelf3D.shapeAgrees(1.0f, 1.05f))
        assertTrue(Shelf3D.shapeAgrees(0.7f, null))                 // no real case known: Case3D keeps the given shape
        assertFalse(Shelf3D.shapeAgrees(0.75f, 1.12f))              // the Flip 2 case: a portrait scan on a jewel case
        assertFalse(Shelf3D.shapeAgrees(1.4f, 0.7f))
    }

    /** Owner, 12 Nov: the depth a little more apparent through the tilt, not the thickness - the top shows clearly mid-turn. */
    @Test fun topReadsMidTurnWithTheRealThickness() {
        val pp = pose(pFor(0.5f))
        val top = faces(pp)[F.TOP.ordinal]
        assertTrue(top.second)
        assertTrue("top ${height(top.first) / pp.h}", height(top.first) / pp.h > 0.04f)
        assertEquals(slotW / slotH, depth, 1e-6f)                     // the thickness is the shelf's, unchanged
        assertEquals(0f, pose(0.0001f).pitch, 0.01f); assertEquals(0f, pose(1f).pitch, 0.01f)   // level at both ends
    }

    /** Flip 2, 12 Nov: a dropped frame at the hand-over never shows neither the spine nor the 3D case. */
    @Test fun spineStaysUntilTheCaseWasDrawnWhole() {
        assertEquals(1f, Shelf3D.shelfSpineAlpha(0.5f, drawnWhole = false), 0f)   // past the blend by the clock, not yet drawn
        assertEquals(0f, Shelf3D.shelfSpineAlpha(0.5f, drawnWhole = true), 0f)
        assertEquals(1f, Shelf3D.shelfSpineAlpha(0.1f, drawnWhole = true), 0f)    // mid-blend (and on the way back): spine under it
        // drawn but invisible while it rises (pictures ready), then the blend
        assertTrue(Shelf3D.caseAlpha(0f) > 0f && Shelf3D.caseAlpha(0f) < 0.005f)
        assertEquals(1f, Shelf3D.caseAlpha(Shelf3D.BLEND), 0f)
    }
}
