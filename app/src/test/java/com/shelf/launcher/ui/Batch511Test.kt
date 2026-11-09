package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 511 (staging only): the decorative effects that ran until the 30 s idle step — focus pulse, shimmer / glow clock,
 * charging light — settle with HomeIdle.settled (10 s) and come back without a jump.
 */
class Batch511Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    /** The breathing curve is the one the pulse always had (0.35 … 1 … 0.35 over 2.2 s, eased). */
    @Test fun pulseCurveUnchanged() {
        fun old(ms: Long): Float {
            val ph = (ms % 2200L) / 1100f
            val tri = if (ph <= 1f) ph else 2f - ph
            return 0.35f + 0.65f * (tri * tri * (3f - 2f * tri))
        }
        for (ms in 0L..4400L step 37L) assertEquals(old(ms), focusPulseAt(ms), 1e-6f)
    }

    /** A resume starts the breath where it has the value it rested at — the steady 0.75 — so nothing jumps. */
    @Test fun resumeStartsAtTheRestingBrightness() {
        for (v in listOf(0.35f, 0.5f, FOCUS_PULSE_REST, 0.9f, 1f)) {
            assertEquals(v, focusPulseAt(focusPulsePhaseMs(v)), 0.01f)
        }
        // 50 ms on (one update) it has moved only a little.
        val p0 = focusPulsePhaseMs(FOCUS_PULSE_REST)
        assertTrue(kotlin.math.abs(focusPulseAt(p0 + 50) - FOCUS_PULSE_REST) < 0.05f)
        assertTrue(FOCUS_PULSE_SETTLE_MS in 400L..1200L)
    }

    /** The charging light stops only once it reaches its still place (wrap at 1 included). */
    @Test fun chargingLightStopsAtItsStillPlace() {
        assertTrue(chargePhasePassed(0.5f, 0.6f, CHARGE_STILL_PHASE))
        assertFalse(chargePhasePassed(0.1f, 0.2f, CHARGE_STILL_PHASE))
        assertTrue(chargePhasePassed(0.9f, 0.05f, 0.95f))
        assertTrue(chargePhasePassed(0.9f, 0.05f, 0.02f))
        assertFalse(chargePhasePassed(0.9f, 0.05f, CHARGE_STILL_PHASE))
    }

    @Test fun effectsReadTheSettleAndInputWakesThem() {
        val p = src("ui/PowerSaver.kt")
        assertTrue(p.contains("val on = active && !HomeIdle.calm && !HomeIdle.settled"))
        assertTrue(p.contains("val shimmerOn = active && !HomeIdle.calm && !HomeIdle.settled"))
        assertTrue(p.contains("LaunchedEffect(on, settle)"))
        // The settle counts input on either screen; the 30 s step still counts only this screen's.
        assertTrue(p.contains("if (settles(sinceAny, true) && !settled) settled = true"))
        assertTrue(p.contains("if (since >= IDLE_MS && !idle)"))
        assertTrue(p.contains("fun touchedElsewhere()"))
        // Still one loop each (home + second screen): no new timer.
        assertEquals(2, Regex("suspend fun watch\\(\\)").findAll(p).count())
        val c = src("ui/HomeChrome.kt")
        assertTrue(c.contains("LaunchedEffect(moving, settle)"))
        assertTrue(c.contains("val reached = settle && chargePhasePassed(last, p, CHARGE_STILL_PHASE)"))
        assertTrue(src("ui/SecondScreen.kt").contains("HomeIdle.touchedElsewhere()"))
    }

    /** What the screen is for keeps running: the background scene, video and marquee never read the settle. */
    @Test fun legitimateMotionDoesNotSettle() {
        assertFalse(src("ui/ThemeScene.kt").contains("HomeIdle.settled"))
        assertFalse(src("ui/FitText.kt").contains("HomeIdle.settled"))
        assertFalse(src("ui/VideoPreview.kt").contains("HomeIdle.settled"))
    }
}
