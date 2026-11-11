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

    @Test fun profilesAppearAsTheTraceReachesThem() {
        // Traced inward from both ends to the focused ring (x = 500): outer ones first, the focused one last.
        val outer = WireGeo.appearAt(60f, 500f, 0f, 1000f, 450f, 550f, IG_FULL)
        val inner = WireGeo.appearAt(400f, 500f, 0f, 1000f, 450f, 550f, IG_FULL)
        val focused = WireGeo.appearAt(500f, 500f, 0f, 1000f, 450f, 550f, IG_FULL)
        assertTrue(outer < inner && inner < focused)
        assertTrue(focused <= IG_FULL.rowEnd)
        assertTrue("opens inside the final shine", IG_FULL.open in FULL_TIMING.shineFrom..FULL_TIMING.shineTo)
        assertTrue(IG_FULL.settled > FULL_TIMING.total && IG_SHORT.settled > SHORT_TIMING.total)
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
