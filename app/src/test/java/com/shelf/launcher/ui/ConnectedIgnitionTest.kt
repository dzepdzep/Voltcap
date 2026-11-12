package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.IntroStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Connected Ignition (owner-approved rev 2): the intro flows into "Who's playing?" on one stage; the Ignition look keeps the wire. */
class ConnectedIgnitionTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText()

    @Test fun onlyFullOrShortIntrosWithAnimationsOnContinueIntoThePicker() {
        assertTrue(ignitionIntro(IntroStyle.FULL, 1f))
        assertTrue(ignitionIntro(IntroStyle.SHORT, 0.5f))
        assertFalse(ignitionIntro(IntroStyle.PIXEL, 1f))
        assertFalse(ignitionIntro(IntroStyle.OFF, 1f))
        assertFalse("system animations off: the intro and picker as before", ignitionIntro(IntroStyle.FULL, 0f))
    }

    @Test fun wireRingsWrapEachProfileAndTheLineRunsBetween() {
        val g = WireGeo.build(floatArrayOf(100f, 300f), floatArrayOf(0f, 1f), cy = 200f, rho = 50f, rise = 10f, left = 0f, right = 400f, step = 1f)
        // Between the rings both halves lie on the centre line.
        val mid = g.idx(200f)
        assertEquals(-1, g.ringOf[mid]); assertEquals(200f, g.top[mid], 0.01f); assertEquals(200f, g.bottom[mid], 0.01f)
        // Over a resting ring's centre: top above, bottom below, by its radius.
        val c0 = g.idx(100f)
        assertEquals(0, g.ringOf[c0]); assertEquals(150f, g.top[c0], 0.5f); assertEquals(250f, g.bottom[c0], 0.5f)
        // The focused (lifted) ring is bigger and raised.
        val c1 = g.idx(300f)
        assertEquals(1, g.ringOf[c1]); assertEquals(190f - 58f, g.top[c1], 0.5f); assertEquals(190f + 58f, g.bottom[c1], 0.5f)
    }

    @Test fun focusGlidesAndRapidPressesStayQuick() {
        assertEquals(1f, IgnK.liftAt(2f, 2), 1e-4f)
        assertEquals(0f, IgnK.liftAt(2f, 3), 1e-4f)
        assertEquals(0.5f, IgnK.liftAt(2.5f, 2), 1e-4f)
        assertEquals(195, IgnK.glideMs(1f, 1000L))          // a tap: ~200 ms
        assertTrue(IgnK.glideMs(1f, 90L) < IgnK.glideMs(1f, 1000L)) // auto-repeat: quicker, never lags behind
        assertEquals(300, IgnK.glideMs(9f, 1000L))           // capped
    }

    @Test fun theSelectedProfileLeadsAndNeighboursFollowNearestFirst() {
        // Sprint 527: the light goes straight to the focused ring (x = 300), which emerges first; then it spreads outward from it
        // and the others appear nearest first, on both sides at the same speed - whatever side of the row the focus is on.
        val rho = 50f
        val reach = WireGeo.reach(250f, 350f, 0f, 1000f, 1000f)
        val focused = WireGeo.appearAt(300f, 300f, rho, rho, reach, IG_FULL)
        val leftNear = WireGeo.appearAt(150f, 300f, rho, rho, reach, IG_FULL)
        val rightNear = WireGeo.appearAt(450f, 300f, rho, rho, reach, IG_FULL)
        val rightFar = WireGeo.appearAt(900f, 300f, rho, rho, reach, IG_FULL)
        assertEquals(IG_FULL.arrive, focused, 0.01f)
        assertTrue(focused < leftNear && focused < rightNear)
        assertEquals("same distance, same moment", leftNear, rightNear, 0.5f)
        assertTrue(rightNear < rightFar && rightFar <= IG_FULL.spreadTo)
        // A row that runs off the screen: the ones beyond it come at the end of the visible run, never later.
        val off = WireGeo.appearAt(5000f, 300f, rho, rho, reach, IG_FULL)
        assertEquals(IG_FULL.spreadTo, off, 0.01f)
        assertTrue("opens inside the final shine", IG_FULL.open in FULL_TIMING.shineFrom..FULL_TIMING.shineTo)
        assertTrue(IG_FULL.settled > FULL_TIMING.total && IG_SHORT.settled > SHORT_TIMING.total)
    }

    @Test fun everythingHasFinishedWhenItSettles() {
        // Sprint 527: nothing is left half-faded (the wire, the pulse) when the clock stops at `settled`.
        for (tm in listOf(IG_FULL, IG_SHORT)) {
            assertTrue(tm.open < tm.arrive && tm.arrive < tm.ringTo && tm.arrive < tm.liftTo)
            assertTrue(tm.spreadFrom < tm.spreadTo && tm.spreadFrom >= tm.arrive)
            assertTrue(tm.tailTo <= tm.ringTo)
            for (end in floatArrayOf(tm.spreadTo, tm.pulseTo, tm.hintsTo, tm.titleTo, tm.liftTo, tm.tailTo, tm.recedeTo)) assertTrue(end <= tm.settled)
            assertTrue(tm.wireOutFrom < tm.settled)
        }
    }

    @Test fun theLightComesInFromTheCapsuleEndNearestTheRing() {
        // 1920 x 1080: capsule r = 216, receded ends at 960 -/+ 147.
        assertEquals(WireGeo.ARRIVE_RIGHT, WireGeo.arrival(200f, 70f, 1920f, 1080f))
        assertEquals(WireGeo.ARRIVE_LEFT, WireGeo.arrival(1700f, 70f, 1920f, 1080f))
        assertEquals(WireGeo.ARRIVE_BOTH, WireGeo.arrival(960f, 70f, 1920f, 1080f))
        assertEquals("a ring overlapping the capsule's end turns in from both", WireGeo.arrival(780f, 70f, 1920f, 1080f), WireGeo.ARRIVE_BOTH)
    }

    @Test fun theWireEndsAtTheRowNotTheScreenEdges() {
        val e = WireGeo.ends(200f, 800f, 50f, 30f)
        assertEquals(120f, e[0], 0.01f); assertEquals(880f, e[1], 0.01f)
        // The visible run is clipped to the screen.
        assertEquals(1100f, WireGeo.reach(1100f, 1200f, -400f, 2000f, 1920f), 0.01f)
    }

    @Test fun scrollingRowFadesEachPictureAtTheEdgesAndTheKeepHintShortens() {
        assertEquals(1f, PickerGrid.edgeFade(500f, 0f, 1000f, 200f), 1e-4f)
        assertEquals(0f, PickerGrid.edgeFade(-60f, 0f, 1000f, 200f), 1e-4f)
        val half = PickerGrid.edgeFade(0f, 0f, 1000f, 200f)
        assertTrue(half > 0f && half < 1f)
        assertTrue(PickerGrid.hintFits(100f, 500f, 220f, 1000f))
        assertFalse("long name", PickerGrid.hintFits(260f, 600f, 220f, 1000f))
        assertFalse("row too wide", PickerGrid.hintFits(150f, 900f, 220f, 1000f))
        assertFalse("too close to the limit (wrapped on the Flip 2 at x1.3)", PickerGrid.hintFits(211f, 600f, 220f, 1000f))
    }

    @Test fun glowIsNeverCutByALayerAndTheIdleBackdropHolds() {
        val ps = src("ui/ProfileSelect.kt")
        assertFalse("no offscreen scroll fade", ps.contains("CompositingStrategy.Offscreen"))
        assertEquals(2, Regex("compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha").findAll(ps).count())
        val looks = src("ui/ProfileLooks.kt")
        assertFalse("no unbounded frame loop", looks.contains("while (true) withFrameNanos"))
        assertTrue(looks.contains("PickerCalm.SETTLE_MS"))
        assertTrue(ps.contains("PickerCalm.poke()"))
    }

    @Test fun aChoiceIgnoresLaterPressesAndBuildsTheHomeEarly() {
        val ps = src("ui/ProfileSelect.kt")
        assertTrue(ps.contains("if (leaving) return@onPreviewKeyEvent true"))
        assertTrue(ps.contains("onAccept(id)"))
        val ls = src("ui/LauncherScreen.kt")
        assertTrue(ls.contains("Rollout.arm()"))
        // (Flip 2: a second A's release reached the home built under the stage and opened a game window.)
        assertTrue(ls.contains("showIntro && stageOn && stageLeaving -> true"))
        assertTrue(src("ui/IgnitionStage.kt").contains("skipFade.animateTo(1f, tween(SKIP_FADE_MS"))
        // (Flip 2 h09: the armed roll-out must see start() - its start time is Compose state, so Run's clock restarts;
        // a plain var left the home dim with its games hidden after a choice.)
        assertTrue(ps.contains("private var startedAt by androidx.compose.runtime.mutableLongStateOf(0L)"))
        assertTrue(ps.contains("LaunchedEffect(startedAt)"))
        // (Final recording: the light starts after the home's first long frame, so the hand-over has no step.)
        val pk = ps.substringAfter("fun pick(i: Int)")
        assertTrue(pk.indexOf("onAccept(id)") < pk.indexOf("if (stage != null) { androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { } }"))
        assertTrue(pk.indexOf("withFrameNanos { } }") < pk.indexOf("exit.animateTo(1f"))
    }

    @Test fun startUpUsesOneWindowAndTheOtherPickersStayDialogs() {
        val ps = src("ui/ProfileSelect.kt")
        assertTrue(ps.contains("if (inStage) body() else Dialog(onDismissRequest = { if (!atStart) onSkip() }"))
        // A press before it settles jumps to the settled picker, never to the home.
        assertTrue(ps.contains("if (stage != null && stageT() < stage.tm.settled) { onStageSkip(); return@onPreviewKeyEvent true }"))
        val ls = src("ui/LauncherScreen.kt")
        assertTrue(ls.contains("showIntro && !stageOn && !isSystemKey(event.nativeKeyEvent.keyCode)"))
        assertTrue(ls.contains("}.also { if (it) WhoPlaying.asked = true })"))
        // The intro keeps its own path for every other start-up.
        assertTrue(ls.contains("} else if (showIntro) {\n                VoltcapIntro("))
        // Settled: the stage's clock loop ends; no frame is drawn until input.
        assertTrue(src("ui/IgnitionStage.kt").contains("while (clock.floatValue < tm.settled) {"))
    }

    @Test fun ignitionIsTheSeventhLookAndSavedLooksKeepTheirNames() {
        assertEquals(7, Features.PickerLook.values().size)
        assertEquals(Features.PickerLook.IGNITION, Features.PickerLook.values().last())
        assertEquals(Features.PickerLook.MIDNIGHT, Features.pickerLook.default)
    }
}
