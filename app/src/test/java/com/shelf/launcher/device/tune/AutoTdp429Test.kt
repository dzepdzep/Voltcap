package com.shelf.launcher.device.tune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 429: AutoTDP / underclock audit. Deterministic traces through the controllers (no hardware): each test is a
 * scripted frame-rate / power sequence or a simple "plant" (fps and watts as a function of the level). Several fail on
 * the Update 428 controller (noted per test); the numbers are from the same traces run through both versions.
 */
class AutoTdp429Test {
    private fun ctl(target: Int = 60, mode: AutoTdpMode = AutoTdpMode.EFFICIENT) = AutoTdpController(mode, target)

    /** Runs [n] ticks; fps / watts from the level. Returns (level, fps) per tick. */
    private fun run(
        c: AutoTdpController, n: Int, start: Float, fps: (Float, Int) -> Float?, watts: (Float, Int) -> Float? = { _, _ -> null },
        fresh: Boolean = true, display: Boolean = false, hz: Float? = null
    ): List<Pair<Float, Float?>> {
        var l = start
        val out = ArrayList<Pair<Float, Float?>>()
        for (i in 0 until n) {
            val f = fps(l, i)
            l = c.tick(l, TdpInput(fps = f, fresh = fresh, displayCounter = display, batteryW = watts(l, i), screenHz = hz))
            out += l to f
        }
        return out
    }

    // ---- power ----

    @Test fun overBudgetWithFramesBelowTargetEasesInsteadOfCrashing() {
        // 50 fps against 60, 10 W against a 7 W budget, for 15 s. Update 428: 0.04 a tick → 0.35 (the floor).
        val c = ctl()
        val out = run(c, 15, 0.8f, { _, _ -> 50f }, { _, _ -> 10f })
        assertTrue("level ${out.last().first}", out.last().first >= 0.65f)
        assertEquals(TdpState.POWER_LIMITED, c.state)
    }

    @Test fun budgetIsRespectedWithoutSawing() {
        // fps = 70 × level (capped 60), watts = 2 + 8 × level: the 7 W budget allows level 0.625 (≈ 44 fps).
        // Update 428 swung 0.62 … 0.74 and peaked at 7.9 W; now it settles under the budget and says why.
        val c = ctl()
        val out = run(c, 300, 0.9f, { l, _ -> minOf(60f, 70f * l) }, { l, _ -> 2f + 8f * l })
        val tail = out.drop(150).map { it.first }
        assertTrue("range ${tail.minOrNull()} … ${tail.maxOrNull()}", tail.maxOrNull()!! - tail.minOrNull()!! < 0.03f)
        assertTrue("watts ${2f + 8f * tail.maxOrNull()!!}", 2f + 8f * tail.maxOrNull()!! <= 7.05f)
        assertEquals(TdpState.POWER_LIMITED, c.state)
    }

    @Test fun aOneSecondPowerSpikeDoesNotThrottle() {
        val c = ctl()
        val out = run(c, 10, 0.8f, { _, _ -> 60f }, { _, i -> if (i == 4) 15f else 5f })
        // Only the ordinary on-target steps (0.025 every 3 ticks), no 0.04 power cut.
        assertTrue("level ${out.last().first}", out.last().first >= 0.8f - 3 * 0.025f - 0.001f)
    }

    @Test fun chargingOrUnknownPowerIsNeverAReading() {
        assertNull(TdpSignals.from(60f, "GAME_LAYER", 18f, plugged = true, charging = true, screenHz = 60f).batteryW)
        // At a charge limit: plugged in, not charging — the battery current is ~0, not the draw.
        assertNull(TdpSignals.from(60f, "GAME_LAYER", 0.4f, plugged = true, charging = false, screenHz = 60f).batteryW)
        assertNull(TdpSignals.from(60f, "GAME_LAYER", 6f, plugged = null, charging = null, screenHz = 60f).batteryW)
        assertEquals(6f, TdpSignals.from(60f, "GAME_LAYER", 6f, plugged = false, charging = false, screenHz = 60f).batteryW)
    }

    @Test fun noPowerReadingNeverDriftsTheLevel() {
        // Update 428 drifted up 0.01 a tick with no fps and low watts; now nothing moves without a game reading.
        val c = ctl()
        val out = run(c, 30, 0.6f, { _, _ -> null }, { _, _ -> 3f })
        assertEquals(0.6f, out.last().first, 0.0001f)
        assertEquals(TdpState.NO_SIGNAL, c.state)
    }

    // ---- frame-rate signal ----

    @Test fun screenRefreshIsNotAGameFrameRate() {
        val s = TdpSignals.from(120f, "SCREEN", null, plugged = false, charging = false, screenHz = 120f)
        assertNull(s.fps)
        // Fed that "120 fps", the Update 428 controller held a target of 120 and lowered clocks every 3 s.
        val c = ctl(target = 0)
        val out = run(c, 40, 0.9f, { _, _ -> s.fps })
        assertEquals(0.9f, out.last().first, 0.0001f)
        assertNull(c.target)
    }

    @Test fun heldReadingsDoNotLowerTheLevel() {
        val c = ctl()
        val out = run(c, 12, 0.8f, { _, _ -> 60f }, fresh = false)
        assertEquals(0.8f, out.last().first, 0.0001f)
        assertTrue(!TdpSignals.from(60f, "HELD", null, false, false, 60f).fresh)
    }

    @Test fun anUnverifiedPanelCounterCannotTakeClocksFarDown() {
        // A display counter that reads the refresh rate whatever the clocks do: it stops at the unverified floor.
        val c = ctl(target = 0)
        val out = run(c, 120, 1f, { _, _ -> 60f }, display = true, hz = 60f)
        assertEquals(AutoTdpController.UNVERIFIED_FLOOR, out.last().first, 0.001f)
        assertEquals(TdpState.UNVERIFIED, c.state)
        // (Update 430, review: one dip — a loading screen — is no longer proof that it follows the game's frames. 429 took it
        // as proof and went to 0.35.)
        val d = ctl(target = 0)
        val out2 = run(d, 200, 1f, { _, i -> if (i in 10..12) 41f else 60f }, display = true, hz = 60f)
        assertEquals(AutoTdpController.UNVERIFIED_FLOOR, out2.last().first, 0.001f)
        assertTrue(!d.displayVerified)
    }

    @Test fun detectedTargetNeverExceedsTheRefreshRate() {
        val c = ctl(target = 0)
        run(c, 10, 1f, { _, _ -> 118f }, hz = 90f)
        assertEquals(90, c.target)
    }

    @Test fun lowFramesTheBudgetCausedAreNotMistakenForTheGamesCap() {
        // Held back by the budget, the game runs 45 for 25 s: Update 428 then dropped its target from 60 to 45 and kept
        // lowering. The budget is the reason, so the target stays (and POWER_LIMITED says so).
        val c = ctl(target = 0)
        run(c, 10, 1f, { _, _ -> 60f })
        assertEquals(60, c.target)
        run(c, 25, 0.6f, { _, _ -> 45f }, { _, _ -> 10f })
        assertEquals(60, c.target)
        assertEquals(TdpState.POWER_LIMITED, c.state)
    }

    @Test fun aCapThatRefusesRaisesStillLetsTheTargetComeDown() {
        // A manual underclock binds: every raise is refused (the level fed back stays 0.7) and the game runs 30.
        val c = ctl(target = 0)
        run(c, 10, 1f, { _, _ -> 60f })
        repeat(25) { c.tick(0.7f, TdpInput(fps = 30f)) }
        assertEquals(30, c.target)
    }

    @Test fun aRealCapIsFoundWhenTheClocksAreFull() {
        val c = ctl(target = 0)
        run(c, 10, 1f, { _, _ -> 60f })
        run(c, 25, 1f, { _, _ -> 30f })
        assertEquals(30, c.target)
    }

    // ---- recovery and stability ----

    @Test fun recoveryFromABigMissIsQuick() {
        // fps = 80 × level: level 0.5 gives 40 fps, 0.75 is needed. Update 428 needed 3 ticks to reach 58.9.
        val c = ctl()
        val out = run(c, 2, 0.5f, { l, _ -> minOf(60f, 80f * l) })
        assertTrue("after one tick ${out[0].first}", out[0].first >= 0.749f)
    }

    @Test fun holdsTheTargetWithoutASawtooth() {
        // Same plant from full: Update 428 stepped up 59 times in the last 200 ticks and missed the target 47 times.
        val c = ctl()
        val out = run(c, 300, 1f, { l, _ -> minOf(60f, 80f * l) })
        val tail = out.drop(100)
        val ups = tail.zipWithNext().count { (a, b) -> b.first > a.first + 1e-6f }
        val misses = tail.count { (it.second ?: 0f) < 60f * 0.98f }
        assertTrue("ups $ups", ups <= 3)
        assertTrue("misses $misses", misses <= 3)
        assertEquals(0.75f, out.last().first, 0.03f)
    }

    @Test fun aSessionStartsWithAMarginOverWhatItLearned() {
        assertEquals(0.85f, AutoTdpController.startLevel(null), 0.0001f)
        assertEquals(0.5f, AutoTdpController.startLevel(0.4f), 0.0001f)
        assertEquals(1f, AutoTdpController.startLevel(0.97f), 0.0001f)
        assertEquals(AutoTdpController.MIN_LEVEL, AutoTdpController.startLevel(0.1f).coerceAtLeast(AutoTdpController.MIN_LEVEL), 0.0001f)
    }

    // ---- Update 430 (review): no / stale / misleading signals ----

    @Test fun sustainedHighPowerWithoutFpsStopsAtTheSignalFloor() {
        // 429 eased 0.02 a tick with no frame rate at all: 12 W for 2 min took it to 0.35 with nothing showing the cost.
        val c = ctl()
        val out = run(c, 120, 1f, { _, _ -> null }, { _, _ -> 12f })
        assertEquals(AutoTdpController.UNVERIFIED_FLOOR, out.last().first, 0.001f)
        assertEquals(TdpState.NO_SIGNAL, c.state)
        // Already below the floor (a learned level): it isn't lowered further, nor raised.
        val d = ctl()
        assertEquals(0.5f, run(d, 60, 0.5f, { _, _ -> null }, { _, _ -> 12f }).last().first, 0.001f)
    }

    @Test fun sustainedHighPowerWithOnlyStaleFpsStopsAtTheSignalFloor() {
        val c = ctl()
        val out = run(c, 120, 1f, { _, _ -> 45f }, { _, _ -> 12f }, fresh = false)
        assertEquals(AutoTdpController.UNVERIFIED_FLOOR, out.last().first, 0.001f)
    }

    @Test fun sustainedHighPowerWithAnUnprovedCounterStopsAtTheSignalFloor() {
        // On target over budget the budget lowers 0.04 a tick — with a counter that may be counting refreshes, not past 0.6.
        val c = ctl()
        val out = run(c, 120, 1f, { _, _ -> 60f }, { _, _ -> 12f }, display = true, hz = 60f)
        assertEquals(AutoTdpController.UNVERIFIED_FLOOR, out.last().first, 0.001f)
        // A reliable reading (the game's layer) still lets the budget go below it.
        val d = ctl()
        assertTrue(run(d, 120, 1f, { _, _ -> 60f }, { _, _ -> 12f }).last().first < AutoTdpController.UNVERIFIED_FLOOR)
    }

    @Test fun aHeldMissTriggersNoRecoveryJump() {
        // 429: a held 40 against 60 jumped the level up every tick for the 4 s hold (a stale value, not a new miss).
        val c = ctl()
        run(c, 6, 0.7f, { _, _ -> 60f })
        val before = 0.7f
        val out = run(c, 4, before, { _, _ -> 40f }, fresh = false)
        out.forEach { assertEquals(before, it.first, 0.0001f) }
        assertTrue(c.state != TdpState.RECOVERING)
    }

    @Test fun chanceDipsOfAMisleadingCounterAreNotProof() {
        // A counter at the refresh rate with a one-tick dip every 15 s (system composition): never trusted, never below 0.6.
        val c = ctl()
        val out = run(c, 400, 1f, { _, i -> if (i % 15 == 14) 52f else 60f }, display = true, hz = 60f)
        assertTrue(!c.displayVerified)
        assertTrue("min ${out.minOf { it.first }}", out.minOf { it.first } >= AutoTdpController.UNVERIFIED_FLOOR - 0.001f)
    }

    @Test fun aLoadingCollapseIsNotProof() {
        val c = ctl(target = 0)
        run(c, 100, 1f, { _, i -> if (i in 30..37) 4f else 60f }, display = true, hz = 60f)
        assertTrue(!c.displayVerified)
    }

    @Test fun aCounterThatMovesWithTheClocksIsTrusted() {
        // fps = 85 × level on a panel counter: misses at the same level after AutoTDP's own steps, twice → proof.
        val c = ctl()
        run(c, 300, 1f, { l, _ -> minOf(60f, 85f * l) }, display = true, hz = 60f)
        assertTrue(c.displayVerified)
    }

    @Test fun aSteadyCapIsProof() {
        val c = ctl(target = 0)
        run(c, 20, 1f, { _, i -> 30f + (i % 3) * 0.3f }, display = true, hz = 60f)
        assertTrue(c.displayVerified)
        // A steady loading screen at a few frames is not.
        val d = ctl(target = 0)
        run(d, 20, 1f, { _, _ -> 8f }, display = true, hz = 60f)
        assertTrue(!d.displayVerified)
    }

    @Test fun screenRateIsNeverShownAsGameFps() {
        val s = com.shelf.launcher.data.perf.PerfSnapshot(fps = 120f, frameMs = 8.3f, fpsKind = com.shelf.launcher.data.perf.FpsKind.SCREEN)
        val text = com.shelf.launcher.ui.overlayText(com.shelf.launcher.data.perf.PerfConfig(), s)
        assertTrue(text, !text.contains("FPS 120"))
        assertTrue(text, text.contains("Screen 120 Hz"))
        val src = java.io.File(listOf("src/main/java/com/shelf/launcher/device/tune/TuneService.kt", "app/src/main/java/com/shelf/launcher/device/tune/TuneService.kt").first { java.io.File(it).exists() }).readText()
        assertTrue("tuner page fps only from game readings", src.contains("val shownFps = snap?.fps?.takeIf { lastFreshKind != null }"))
    }

    @Test fun theLaunchersOwnFramesAreNotTheGamesTarget() {
        // Update 431, seen on the Odin 2 Portal: right after a launch the panel counter still counted Voltcap's animated
        // screen (118 on a 120 Hz panel) and AutoTDP locked a target of 120 for a game running 60.
        val c = ctl(target = 0)
        run(c, 30, 1f, { _, i -> if (i < 3) 118f else 60f }, display = true, hz = 120f)
        assertEquals(60, c.target)
    }

    // ---- AutoTDP's own underclock ----

    private fun ucRun(u: AutoUnderclock, secs: Int, cpu: Int?, gpu: Int?, fps: Float = 60f): Int {
        var step = 1
        repeat(secs) { step = u.tick(step, 0..3, fps, 60, cpu, gpu, overPower = false, levelHasRoom = false, deeperLowers = true, dtMs = 1000L) }
        return step
    }

    @Test fun unreadableLoadIsNotSpareCapacity() {
        // Update 428: GPU and CPU both unreadable counted as idle → a deeper preset every 30 s.
        assertEquals(1, ucRun(AutoUnderclock(), 120, cpu = null, gpu = null))
    }

    @Test fun clearlyIdleGpuStillAllowsADeeperPreset() {
        assertEquals(2, ucRun(AutoUnderclock(), 40, cpu = null, gpu = 40))
        // Without a CPU reading the GPU has to be clearly idle, not just under 75 %.
        assertEquals(1, ucRun(AutoUnderclock(), 40, cpu = null, gpu = 70))
        assertEquals(2, ucRun(AutoUnderclock(), 40, cpu = 30, gpu = 70))
    }

    @Test fun missingFrameRateHoldsTheUnderclock() {
        val u = AutoUnderclock()
        var step = 2
        repeat(60) { step = u.tick(step, 0..3, null, 60, 20, 20, false, false, true, 1000L) }
        assertEquals(2, step)
    }
}
