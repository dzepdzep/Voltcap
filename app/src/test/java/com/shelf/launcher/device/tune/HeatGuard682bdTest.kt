package com.shelf.launcher.device.tune

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 682bd (owner 25 Nov 21:24): the heat guard tolerates spikes, steps in gradually after a sustained run over the limit,
 * holds in the hysteresis band, recovers once cool, resets per game, never writes the firmware's thermal control - and a
 * temporary cut never changes the saved profile. Synthetic traces, 1 s readings. The resume rule: only a library game named by
 * its package gets its profile from the app in front.
 */
@RunWith(RobolectricTestRunner::class)
class HeatGuard682bdTest {
    private fun run(g: HeatGuard, p: HeatGuardPreset, temps: List<Float>, limit: Int = 85): List<Int> =
        temps.map { g.tick(it, 1_000L, limit, p); g.step }

    private fun flat(c: Float, s: Int) = List(s) { c }

    @Test fun aShortSpikeIsNotHeat() {
        for (p in HeatGuardPreset.values()) {
            val g = HeatGuard()
            val steps = run(g, p, flat(75f, 30) + flat(98f, 4) + flat(75f, 60))
            assertEquals(p.name, 0, steps.max())
        }
    }

    @Test fun aSustainedRunStepsGraduallyAndNeverPastItsDepth() {
        for (p in HeatGuardPreset.values()) {
            val g = HeatGuard()
            val steps = run(g, p, flat(92f, 600))
            assertEquals(p.name, p.maxSteps, steps.last())
            // one step at a time, at least stepGapMs apart
            val changes = steps.withIndex().zipWithNext().filter { (a, b) -> a.value != b.value }.map { it.second.index }
            assertTrue(p.name, changes.zipWithNext().all { (a, b) -> (b - a) * 1000L >= p.stepGapMs })
            assertTrue(p.name, p.stepPercent <= 10 && g.cutPercent(p) <= 40)
        }
    }

    @Test fun aggressiveActsBeforeBalancedBeforeGentle() {
        fun firstStep(p: HeatGuardPreset) = run(HeatGuard(), p, flat(70f, 20) + flat(90f, 120)).indexOfFirst { it > 0 }
        val a = firstStep(HeatGuardPreset.AGGRESSIVE); val b = firstStep(HeatGuardPreset.BALANCED); val gentle = firstStep(HeatGuardPreset.GENTLE)
        assertTrue("$a < $b < $gentle", a in 0 until b && b < gentle)
    }

    @Test fun itGivesSpeedBackOnceCoolOneStepAtATime() {
        val p = HeatGuardPreset.BALANCED
        val g = HeatGuard()
        run(g, p, flat(92f, 200))
        val deep = g.step
        assertTrue(deep >= 2)
        val back = run(g, p, flat(70f, 300))
        assertEquals(0, back.last())
        assertTrue(back.zipWithNext().all { (a, b) -> a - b <= 1 })
    }

    @Test fun theHysteresisBandHoldsWithoutFlapping() {
        val p = HeatGuardPreset.BALANCED
        val g = HeatGuard()
        run(g, p, flat(92f, 60))
        assertTrue(g.step >= 1)
        val held = g.step
        // around the limit, inside limit - coolBelowC .. limit: neither down nor up
        val band = run(g, p, List(600) { if (it % 20 < 10) 82f else 84f })
        assertTrue(band.all { it == held })
    }

    @Test fun rapidChangesDoNotFlap() {
        for (p in HeatGuardPreset.values()) {
            val steps = run(HeatGuard(), p, List(600) { if ((it / 2) % 2 == 0) 70f else 100f })
            val changes = steps.zipWithNext().count { (a, b) -> a != b }
            assertTrue("${p.name}: $changes changes", changes <= p.maxSteps + 1)
        }
    }

    @Test fun aNewGameStartsFresh() {
        val g = HeatGuard()
        run(g, HeatGuardPreset.AGGRESSIVE, flat(95f, 120))
        assertTrue(g.step > 0)
        g.reset()
        assertEquals(0, g.step); assertNull(g.smoothedC); assertEquals(0, g.cutPercent(HeatGuardPreset.AGGRESSIVE))
        // and the first steady reading is not taken for a run over the limit
        assertFalse(g.tick(95f, 1_000L, 85, HeatGuardPreset.AGGRESSIVE))
    }

    @Test fun limitsStayUnderTheFirmwaresOwnTrip() {
        assertEquals(90, HeatGuard.maxLimitFor(95))
        assertEquals(95, HeatGuard.maxLimitFor(null))
        assertEquals(90, HeatGuard.validLimit(95, 95))
        assertEquals(60, HeatGuard.validLimit(40, null))
        assertEquals(85, HeatGuard.validLimit(85, 95))
    }

    @Test fun theFirmwaresThermalControlIsNeverWritten() {
        val root = listOf("src/main/java/com/shelf/launcher", "app/src/main/java/com/shelf/launcher").map { File(it) }.first { it.exists() }
        val bad = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) ->
                (l.contains("echo") || l.contains("writeAll") || l.contains("> ")) &&
                    (l.contains("/sys/class/thermal") || l.contains("trip_point") || l.contains("cooling_device") || l.contains("thermal-engine")) &&
                    !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")
            }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        // reading is fine (cat ... temp / trip_point_*_temp); a write is not
        assertTrue(bad.toString(), bad.all { it.startsWith("TuneHardware.kt") })
        val hw = File(root, "device/tune/TuneHardware.kt").readText()
        assertFalse(Regex("echo[^\\n]*>\\s*/sys/class/thermal").containsMatchIn(hw))
    }

    // ---- on the QA app's simulated hardware: the tuner itself ----

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("tuner", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(TuneSim.set(true, isolatedQa = true))
        Tuner.resetSupported()
    }

    @After fun tearDown() {
        runCatching { Tuner.onGameEnded(ctx) }
        Tuner.libraryApps = emptyMap()
        TuneSim.set(false, isolatedQa = true)
        Tuner.resetSupported()
    }

    @Test fun theSimulatorNeverTurnsOnOutsideTheQaApp() {
        TuneSim.set(false, true)
        assertFalse(TuneSim.set(true, isolatedQa = false))
        assertFalse(TuneSim.on)
    }

    @Test fun aHotGameKeepsItsSavedOffAndShowsTheGuardApart() {
        val app = "com.example.wwe"
        val store = TuneStore(ctx)
        val off = TuneProfile(autoTdp = true, autoMode = AutoTdpMode.BALANCED, underclock = Underclock.OFF)
        store.setForKey(app, off)
        store.heatGuard = true
        store.heatGuardPreset = HeatGuardPreset.AGGRESSIVE
        store.heatGuardC = 85
        Tuner.onGameLaunch(ctx, app, "WWE", app)
        repeat(60) { Tuner.heatTick(95f, 1_000L) }
        val st = Tuner.status.value
        assertTrue(st.heatCut > 0)
        assertEquals(TuneSource.GAME, st.sourceKind)
        assertEquals(Underclock.OFF, st.profile.underclock)
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey(app)!!.underclock) // nothing written into the profile
        val line = powerLine(st.profile, st.autoUnderclock, st.heatCut, st.sourceKind, st.source, st.vendorThrottle)
        assertTrue(line, line.contains("Underclock Off") && line.contains("Heat guard −") && line.endsWith("Game profile"))
        // the sim's big cores really are capped lower while hot - and back once the game ends
        val big = TuneSim.read("/sys/devices/system/cpu/cpufreq/policy3/scaling_max_freq")!!.toInt()
        assertTrue("$big", big < 2803200)
        Tuner.onGameEnded(ctx)
        assertEquals(0, Tuner.status.value.heatCut)
        assertEquals(TuneSource.DEVICE, Tuner.status.value.sourceKind)
    }

    @Test fun aLibraryGameBackFromRecentsGetsItsOwnProfileAndNothingElseIsGuessed() {
        val app = "com.example.wwe"
        val store = TuneStore(ctx)
        store.setForKey(app, TuneProfile(underclock = Underclock.OFF, tier = PowerTier.MAX))
        store.setGlobal(TuneProfile(autoTdp = true))
        Tuner.libraryApps = mapOf(app to "WWE", "org.retroarch" to "RetroArch")
        assertFalse(Tuner.onForeground(ctx, "com.unknown.app"))              // not a library game: nothing
        assertTrue(Tuner.onForeground(ctx, app))                              // the game from Recents: its own
        assertEquals(TuneSource.GAME, Tuner.status.value.sourceKind)
        assertEquals(PowerTier.MAX, Tuner.status.value.profile.tier)
        assertFalse(Tuner.onForeground(ctx, app))                             // already in charge
        // A ROM game started from Voltcap runs in its emulator: the emulator in front is not "another game".
        Tuner.onGameEnded(ctx)
        store.setForKey("/roms/psx/x.chd", TuneProfile(tier = PowerTier.SAVER))
        Tuner.onGameLaunch(ctx, "/roms/psx/x.chd", "X", "org.retroarch", "psx", "PS1")
        assertFalse(Tuner.onForeground(ctx, "org.retroarch"))
        assertEquals("X", Tuner.status.value.source)
        // Switching from it to the library app game: that game's own now.
        assertTrue(Tuner.onForeground(ctx, app))
        assertEquals("WWE", Tuner.status.value.source)
    }

    @Test fun onlyAndroidGamesNamedByTheirPackageAreRecognised() {
        val vm = listOf("src/main/java/com/shelf/launcher/LauncherViewModel.kt", "app/src/main/java/com/shelf/launcher/LauncherViewModel.kt")
            .map { File(it) }.first { it.exists() }.readText()
        assertTrue(vm.contains("it.sourceType == SourceType.APP && it.packageName != null && it.stableKey == it.packageName"))
    }
}
