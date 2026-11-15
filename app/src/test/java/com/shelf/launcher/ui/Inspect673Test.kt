package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 673b, owner design 14 Nov: "Disc Station: the disc stays seated for ordinary info panels; the real disc lifts and returns for
 * Inspect / Zoom." "Coverflow: the case stays anchored for the side info panel; the actual case is pulled and rotated for
 * Inspect, then restored."
 */
class Inspect673Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    // The home pose is all there as it starts, gone as it lands, eased at both ends (no jump as the pull starts or lands).
    @Test fun pullEaseStartsAtHomeAndEndsFacingYou() {
        assertEquals(1f, pullEase(0f), 0f)
        assertEquals(0f, pullEase(1f), 0f)
        assertEquals(0.5f, pullEase(0.5f), 1e-6f)
        assertEquals(1f, pullEase(-0.2f), 0f); assertEquals(0f, pullEase(1.3f), 0f)
        var last = 2f
        for (i in 0..20) { val v = pullEase(i / 20f); assertTrue(v <= last); last = v }
        assertTrue("slow out of its place", 1f - pullEase(0.05f) < 0.01f)
        assertTrue("slow into its place", pullEase(0.95f) < 0.01f)
    }

    // Coverflow details' big case at rest: its yaw / pitch / size are where the pull starts (Spine depth: Subtle 14° / 9°,
    // drawn at 0.86 of its stage; the inspected case draws at 0.97 of its box).
    @Test fun coverflowPullStartsAsTheBigCaseStands() {
        val d = com.shelf.launcher.data.Features.SpineDepth.SUBTLE
        assertEquals(14f, bigCaseYaw(d), 0f)
        assertEquals(9f - CASE_PITCH, bigCasePitch(d) - CASE_PITCH, 0f)
        assertEquals(0.86f / 0.97f, bigCaseFill(d) / INSPECT_CASE_FILL, 1e-6f)
        val g = src("GameWindow.kt")
        assertTrue(g.contains("val coverPull = scene == WinScene.INDEX_CARD && layout == HomeLayout.COVERDETAILS && piece?.kind == InspectKind.BOX && origin != null"))
        assertTrue(g.contains("motion.fromYaw = if (!coverPull) 0f else if (PickedFacing.faces) bigCaseYaw(spineDepthNow) else PickedFacing.TURN"))
        assertTrue(g.contains("motion.fromPitch = if (coverPull) bigCasePitch(spineDepthNow) - CASE_PITCH else 0f"))
        assertTrue(g.contains("motion.fromFill = if (coverPull) bigCaseFill(spineDepthNow) / INSPECT_CASE_FILL else PULL_FILL"))
        // The side panel keeps its own entrance: it never grows out of the case.
        assertTrue(g.contains("val from = if (scene == WinScene.INDEX_CARD && layout == HomeLayout.COVERDETAILS) null else origin"))
        // The big case is the origin, and hides while its piece is out.
        assertTrue(src("StyleCoverDetails.kt").contains(".gameOrigin(gm.stableKey, o === cur)"))
    }

    @Test fun theInspectedPieceStartsFromTheHomePose() {
        val m = src("MediaInspectView.kt")
        assertTrue(m.contains("else if (fromYaw != 0f) fromYaw * pullEase(k)"))
        assertTrue(m.contains("minOf(f.width * motion.fromFill, f.height * motion.fromFill * aspect)"))
        assertTrue(m.contains("(if (motion.fromPitch != 0f) motion.fromPitch * pullEase(motion.lift.floatValue) else 0f)"))
        assertTrue(m.contains("back = piece.back, yawOf = yawOf, fill = INSPECT_CASE_FILL,"))
        // Every other piece flies exactly as before.
        assertEquals(0.94f, PULL_FILL, 0f)
    }

    // Disc station: A opens the deck's panel with the disc still in the tray; Inspect lifts that disc, not a case round it.
    @Test fun discStationOpensSeatedAndInspectLiftsTheDisc() {
        val g = src("GameWindow.kt")
        val i = g.indexOf("val sceneOwnsOpen = ")
        val owns = g.substring(i, g.indexOf("MediaInspect.force != g.stableKey", i))
        assertTrue(owns.contains("scene == WinScene.DISC_PANEL"))
        val m = src("MediaInspectView.kt")
        val f = m.indexOf("private fun forcedPieceOf(")
        val body = m.substring(f, m.indexOf("return runCatching { casePiece(g, physical, cover, mixed) }", f))
        assertTrue(body.contains("if (state.appearance.homeLayout == HomeLayout.DISCS && mediaKind(g.platform) == \"disc\") inspectPieceOf(g, state)?.let { return it }"))
        // Its tray's disc is the window's origin (it hides while its piece is out).
        assertTrue(src("StyleDiscStation.kt").contains(".gameOrigin(shown.stableKey, shown.stableKey == g.stableKey)"))
    }

    // Lifted where it stands: its place grown round its own middle (the piece stays centred on its home place), the case's
    // hint room under it.
    @Test fun inPlaceSlotGrowsRoundItsHomePlace() {
        val o = androidx.compose.ui.geometry.Rect(100f, 200f, 300f, 400f)
        val r = inPlaceSlot(o, 1.29f, 0f)
        assertEquals(o.center.x, r.center.x, 1e-3f); assertEquals(o.center.y, r.center.y, 1e-3f)
        assertEquals(258f, r.width, 1e-3f); assertEquals(258f, r.height, 1e-3f)
        val c = inPlaceSlot(o, 1.08f, 60f)
        assertEquals(o.center.y - 108f, c.top, 1e-3f); assertEquals(o.center.y + 108f + 60f, c.bottom, 1e-3f)
        // Disc station's disc rises toward the deck's open side - its right edge stays (the panel beside it on a narrow screen).
        val d = inPlaceSlot(o, 1.29f, 0f, keepRight = true)
        assertEquals(o.right, d.right, 1e-3f); assertEquals(258f, d.width, 1e-3f); assertEquals(o.center.y, d.center.y, 1e-3f)
        assertTrue(src("GameWindow.kt").contains("keepRight = scene == WinScene.DISC_PANEL)"))
        assertTrue(inspectsInPlace(WinScene.DISC_PANEL, com.shelf.launcher.data.HomeLayout.DISCS))
        assertTrue(inspectsInPlace(WinScene.INDEX_CARD, com.shelf.launcher.data.HomeLayout.COVERDETAILS))
        assertTrue(!inspectsInPlace(WinScene.INDEX_CARD, com.shelf.launcher.data.HomeLayout.SHELF))
        assertTrue(!inspectsInPlace(WinScene.STORAGE_RECORD, com.shelf.launcher.data.HomeLayout.STORAGE))
        val g = src("GameWindow.kt")
        assertTrue(g.contains("val inPlace = piece != null && !pieceAtOpen && inspectsInPlace(scene, layout) && origin != null && origin.top != Float.MAX_VALUE"))
        assertTrue(g.contains("if (piece != null && !inPlace) {"))
        assertTrue(g.contains("(if (piece != null && !inPlace) flightVeil(motion.lift.floatValue) else 1f)"))
    }

    // The disc leaves its turning tray at the tray's angle: the shorter turn back to upright.
    @Test fun shortTurnTakesTheShorterWay() {
        assertEquals(0f, shortTurn(0f), 0f); assertEquals(170f, shortTurn(170f), 1e-4f)
        assertEquals(-170f, shortTurn(190f), 1e-4f); assertEquals(180f, shortTurn(180f), 1e-4f)
        assertEquals(-90f, shortTurn(-450f), 1e-4f); assertEquals(10f, shortTurn(730f), 1e-4f)
        assertTrue(src("StyleDiscStation.kt").contains("DiscTray.set(g.stableKey, rotationZ)"))
        // (D326) The lifted disc keeps the station's sheen.
        assertTrue(src("MediaInspectView.kt").contains("InspectKind.DISC -> InspectDisc(piece, yawOf, pitchOf, sheen = motion.rollHome)"))
        assertTrue(src("MediaInspectView.kt").contains(".then(if (sheen) Modifier.discSheen(if (p.smallDisc) 80f / 120f else 1f) else Modifier)"))
        assertTrue(src("MediaInspectView.kt").contains("if (motion.rollHome) rotationZ = shortTurn(if (motion.closing) DiscTray.angleOf(key) ?: motion.fromRoll else motion.fromRoll) * pullEase(k)"))
    }

    // Its home copy goes only once the piece has been drawn in the game window (its own surface) - no frame of an empty tray.
    @Test fun theHomeCopyGoesOnceThePieceIsDrawn() {
        val m = src("MediaInspectView.kt")
        assertTrue(m.contains("if (!awaitDrawn) MediaInspect.hold.value = true"))
        assertTrue(m.contains("while (!drawnOnce.value && android.os.SystemClock.uptimeMillis() - t0 < 400L) withFrameNanos { }"))
        assertTrue(m.contains(".drawWithContent { drawContent(); if (motion.started && !motion.drawnOnce.value) motion.drawnOnce.value = true }"))
        assertTrue(m.contains("var started by mutableStateOf(false)"))
        assertTrue(src("GameWindow.kt").contains("motion.open(inspectScope, fullInspect, spin = inspectTurn, awaitDrawn = inPlace)"))
    }

    @Test fun qaCanPressInspectLikeItsKey() {
        assertTrue(src("TestHooks.kt").contains("val winInspect = mutableStateOf<Boolean?>(null)"))
        assertTrue(src("TestHooks.kt").contains("intent?.getStringExtra(\"voltcap_test_wininspect\")"))
        assertTrue(src("GameWindow.kt").contains("if (qaInspect == true) {"))
    }
}
