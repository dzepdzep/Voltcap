package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 513 (staging only): the Ken Burns hero drift is back to the smooth every-frame drift (511). On the Thor (Tiles,
 * 120 Hz) it is presented on every vsync at a constant latency; 512's sub-pixel steps and a paced 60 fps variant both made the
 * presentation uneven (latency jumping by a whole frame), so neither ships.
 */
class Batch513Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    @Test fun kenBurnsDriftIsTheSmoothOne() {
        val h = src("ui/HomeChrome.kt")
        // Read only while drawing (graphicsLayer): no recomposition per frame.
        assertTrue(h.contains("zoom = if (zoomOn) ({ layer.zoom.value }) else null,"))
        assertTrue(h.contains("val pan = ((layer.zoom.value - 1f) / (zoomTarget - 1f).coerceAtLeast(0.01f)).coerceIn(0f, 1f)"))
        // Same duration, easing and Motion Off snap as before.
        assertTrue(h.contains("private const val HeroZoomMillis = 22_000"))
        assertTrue(h.contains("easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.2f, 1f)"))
        assertTrue(h.contains("if (!zoomOn || !animationsOn) { layer.zoom.snapTo(1f); return@LaunchedEffect }"))
        // Neither the stepped nor the paced variant, nor the QA switch used to compare them.
        for (gone in listOf("drawnZoom", "heroZoomStep", "HERO_ZOOM_STEPS", "heroDrift", "qa_kb_mode")) assertFalse(gone, h.contains(gone))
        assertEquals(1, Regex("layer\\.zoom\\.animateTo\\(").findAll(h).count())
    }

    /**
     * Game shelf: until the focus effect has taken the new game over, the frame shows what that effect starts from — the new
     * game at its own pull (0 / on its way back), the game you left still at its pull — never the new game at the old pull.
     */
    @Test fun shelfFirstFrameAfterAMoveShowsTheRightPulls() {
        val h = src("ui/ShelfHome.kt")
        assertTrue(h.contains("fun focusPending(): Boolean = focusKeyNow != shownKey || lap != shownLap"))
        assertTrue(h.contains("if (i == c.focus && r == focusVRow) return focusPull()"))
        assertTrue(h.contains("var room = makeRoom(i, r, c.focus, focusVRow, focusPull())"))
        assertTrue(h.contains("pendingBack()?.let { pb -> if (seenKeys.add(pb.key)) add(pb) }"))
        assertTrue(h.contains("Pulled(idx, if (b == null) ({ focusPull() }) else ({ b.a.value })"))
        // Nothing in the drawing reads the raw pull for the focused game any more.
        assertFalse(h.contains("return pull.value\n"))
        assertFalse(h.contains("({ pull.value })"))
    }

    /** Narrow / near-square screens: the picture never squeezes the text column into broken words. */
    @Test fun narrowWindowsKeepWholeWords() {
        val c = src("ui/WinScenesConsole.kt")
        assertTrue(c.contains("Modifier.fillMaxHeight().widthIn(max = w * 0.45f)"))
        assertTrue(c.contains("val coverW = minOf(maxHeight * 0.72f, maxWidth * 0.6f)"))
        assertTrue(c.contains("WholeWordsText(value, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)"))
        val n = src("ui/WinScenes.kt")
        assertTrue(n.contains("val labelW = minOf(96.dp, maxWidth * 0.4f)"))
        assertTrue(n.contains("WholeWordsText(value ?: \"—\", color = Color.White, fontSize = 15.sp"))
    }

    /** "Who's playing?": the drifting light holds still with the home's idle settle and carries on from the same moment. */
    @Test fun pickerBackdropSettlesWithTheHome() {
        val p = src("ui/ProfileLooks.kt")
        // (Sprint 527: one ambient clock for the light and Midnight blue's sparks - it holds with the home's idle settle and
        // with the picker's own calm, and carries on from the same moment.)
        assertTrue(p.contains("val t = rememberAmbientClock(moving && !hold && !HomeIdle.settled, 24)"))
        assertTrue(p.contains("val t = rememberAmbientClock(run && !HomeIdle.settled, 30)"))
        assertTrue(p.contains("val start = first - (from * 1e9f).toLong()"))
        // The picker's own keys count as input (its window's keys never reach the activity).
        assertTrue(src("ui/ProfileSelect.kt").contains("if (e.type == KeyEventType.KeyDown) { HomeIdle.poke(); PickerCalm.poke() }"))
    }

    /**
     * Update 514 (owner, RP Classic): "Who's playing?" left the bottom 48 px uncovered — its Dialog window fitted the navigation
     * bar the owner's Voltcap leaves shown on a Retroid (the device's own hide_nav_bar hides it). The picker, its PIN pad and
     * the RetroAchievements "Who is playing?" are edge to edge like the game windows.
     */
    @Test fun whoIsPlayingCoversTheWholeDisplay() {
        val ps = src("ui/ProfileSelect.kt")
        assertTrue(ps.contains("DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)"))
        assertTrue(ps.contains("Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))"))
        assertEquals(2, Regex("Dialog\\(onDismissRequest").findAll(ps).count())
        assertTrue(src("ui/WhoIsPlaying.kt").contains("DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)"))
        // The background still fills the window (each look) and the content stays centred in it.
        assertTrue(ps.contains("PickerBackdrop(look, accentNow, animate = !lite, hold = stage != null && !stageSettled)"))
        assertTrue(ps.contains("contentAlignment = Alignment.Center"))
    }

    /**
     * Update 514 (Retroid, 16:9 art on keep cases): a wide picture was taken for a whole unfolded box (its right 40 % cut as
     * the front) — a wrap is about 2.0–2.25 fronts wide, 16:9 on a DVD case is 2.5.
     */
    @Test fun wideCoverIsNotAWrap() {
        val dvd = 135f / 190f
        assertFalse(looksUnfolded(16f / 9f, dvd))          // a 16:9 picture on a PS2 / DVD keep case
        assertFalse(looksUnfolded(3f, dvd))                 // ultrawide
        assertFalse(looksUnfolded(2f / 3f, dvd))            // portrait cover
        assertTrue(looksUnfolded(2.1f * dvd, dvd))          // a real DVD wrap (back | spine | front)
        assertTrue(looksUnfolded(2.25f * dvd, dvd))         // with a scanner's margin
        val n64 = 1.37f
        assertTrue(looksUnfolded(2.2f * n64, n64))          // an N64 box wrap
        assertFalse(looksUnfolded(16f / 9f, n64))           // a 16:9 picture on an N64 box: just a picture
        assertFalse(looksUnfolded(2f, 0f))
    }

    /** Update 514: a one-colour picture with thin lines / small writing is a picture; a real stand-in is still flat. */
    @Test fun flatStandInNeedsEveryPixelFlat() {
        val green = 0xFF3C8C3C.toInt()
        assertTrue(FlatArt.flatPixels(IntArray(32 * 32) { green }))
        assertTrue(FlatArt.flatPixels(IntArray(32 * 32) { i -> if (i % 7 == 0) green + 0x00030303 else green }))   // scanner noise
        val lined = IntArray(32 * 32) { i -> if (i % 32 == 2 || i / 32 == 29) 0xFF7FAF7F.toInt() else green }    // a frame line, writing
        assertFalse(FlatArt.flatPixels(lined))
        assertFalse(FlatArt.flatPixels(IntArray(0)))
        val src = File(listOf("../src_copy/ui/RealMedia.kt", "src/main/java/com/shelf/launcher/ui/RealMedia.kt", "app/src/main/java/com/shelf/launcher/ui/RealMedia.kt").first { File(it).exists() }).readText()
        assertTrue(src.contains("createScaledBitmap(soft, FLAT_GRID, FLAT_GRID, true)"))
        assertFalse(src.contains("for (i in 0..6) for (j in 0..6)"))
    }
}
