package com.shelf.launcher.device.tune

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * 682bd (owner 25 Nov 20:58): an Android game set to Underclock Off showed "AutoTDP Balanced · Underclock Light" at launch.
 * A game's explicit Off must survive a restart, the one-time migration and a backup restore; AutoTDP must not move it; the
 * launch, the popup and the settings must pick the same profile; and the screens must say whose profile runs.
 */
@RunWith(RobolectricTestRunner::class)
class TuneOverride682bdTest {
    private lateinit var ctx: Context
    private val app = "com.example.androidgame" // an Android game: its stableKey is its package
    private val off = TuneProfile(autoTdp = true, autoMode = AutoTdpMode.BALANCED, underclock = Underclock.OFF)
    private val withPower = TuneProfile(autoTdp = true, autoMode = AutoTdpMode.BALANCED) // underclock AUTO (default)

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    private fun prefs() = ctx.getSharedPreferences("tuner", Context.MODE_PRIVATE)

    @Test fun anExplicitOffStaysOffAfterARestart() {
        TuneStore(ctx).setForKey(app, off)
        TuneStore(ctx).setGlobal(withPower)
        val again = TuneStore(ctx) // a new process: a new store, the migration check runs again
        val p = again.forKey(app)!!
        assertEquals(Underclock.OFF, p.underclock)
        assertEquals(Underclock.OFF, p.effectiveUnderclock)
        assertFalse(p.underclocked)
        assertEquals(Underclock.AUTO, again.global().underclock) // the device-wide one is untouched too
    }

    @Test fun theMigrationOnlyTouchesDataFromBeforeWithPower() {
        // Before Update 237 "Off" was the default nobody chose; Light was a choice.
        prefs().edit()
            .putString("global", """{"tier":"BALANCED","underclock":"OFF"}""")
            .putString("per_game", """{"$app":{"tier":"OFF","underclock":"OFF"},"rom.iso":{"tier":"OFF","underclock":"LIGHT"}}""")
            .commit()
        val s = TuneStore(ctx)
        assertEquals(Underclock.AUTO, s.global().underclock)
        assertEquals(Underclock.AUTO, s.forKey(app)!!.underclock)
        assertEquals(Underclock.LIGHT, s.forKey("rom.iso")!!.underclock)
        assertTrue(prefs().getBoolean(TuneStore.UC_FLAG, false))
        // After it: Off chosen now stays Off, also in the next process.
        s.setForKey(app, off)
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey(app)!!.underclock)
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey(app)!!.underclock)
    }

    @Test fun everySaveMarksTheStoreSoTheMigrationNeverRunsOverIt() {
        val s = TuneStore(ctx)
        prefs().edit().remove(TuneStore.UC_FLAG).commit()
        s.setForKey(app, off)
        assertTrue(prefs().getBoolean(TuneStore.UC_FLAG, false))
        prefs().edit().remove(TuneStore.UC_FLAG).commit()
        s.setForKeys(listOf("a.iso", "b.iso"), off)
        assertTrue(prefs().getBoolean(TuneStore.UC_FLAG, false))
        prefs().edit().remove(TuneStore.UC_FLAG).commit()
        s.setGlobal(withPower)
        assertTrue(prefs().getBoolean(TuneStore.UC_FLAG, false))
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey(app)!!.underclock)
    }

    @Test fun aBackupRestoreOfTheseSettingsKeepsOff() {
        TuneStore(ctx).setForKey(app, off)
        // Backup.kt: a DEVICE file is replaced whole - every key removed, the backup's written.
        val backup = prefs().all.toMap()
        prefs().edit().clear().commit()
        val e = prefs().edit()
        backup.forEach { (k, v) -> when (v) { is Boolean -> e.putBoolean(k, v); is String -> e.putString(k, v) } }
        e.commit()
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey(app)!!.underclock)
    }

    @Test fun autoTdpNeverMovesAChosenUnderclock() {
        assertFalse(Tuner.autoTdpMovesUnderclock(off))
        assertTrue(Tuner.autoTdpMovesUnderclock(withPower))
        for (m in AutoTdpMode.values()) {
            assertEquals(m.name, Underclock.OFF, off.copy(autoMode = m).effectiveUnderclock)
            assertFalse(Tuner.autoTdpMovesUnderclock(off.copy(autoMode = m)))
        }
        // "With power" + AutoTDP Balanced is the Light the notification showed - only when the underclock follows the power.
        assertEquals(Underclock.LIGHT, withPower.effectiveUnderclock)
    }

    @Test fun theGamesOwnProfileWinsOverAppConsoleAndDevice() {
        val saved = mapOf(app to off, "pkg:$app" to withPower, "plat:psx" to withPower)
        val (p, key) = TuneResolve.pick(saved::get, { withPower }, app, app, null, true)
        assertEquals(app, key); assertEquals(Underclock.OFF, p.underclock)
        // Without its own: the app package's, then the console's (only when console profiles are on), then the device-wide.
        assertEquals("pkg:$app", TuneResolve.pick(mapOf("pkg:$app" to off)::get, { withPower }, app, app, null, true).second)
        val rom = "/sdcard/roms/psx/game.chd"
        assertEquals("plat:psx", TuneResolve.pick(mapOf("plat:psx" to off)::get, { withPower }, rom, "emu.pkg", "psx", true).second)
        assertNull(TuneResolve.pick(mapOf("plat:psx" to off)::get, { withPower }, rom, "emu.pkg", "psx", false).second)
        assertNull(TuneResolve.pick(emptyMap<String, TuneProfile>()::get, { withPower }, app, app, null, true).second)
    }

    @Test fun anAndroidGamesSettingsAndItsLaunchUseTheSameKey() {
        val models = src("data/Models.kt")
        assertTrue("an app's stableKey is its package", models.contains("get() = romPath ?: packageName ?:"))
        val vm = src("LauncherViewModel.kt")
        assertTrue(vm.contains("Tuner.onGameLaunch(app, game.stableKey,"))
        assertTrue(src("ui/GameProfileDialog.kt").contains("GamePerformanceProfile(\n            game.stableKey,"))
        assertTrue(src("ui/GamePopupStatus.kt").contains("store.setForKey(g.stableKey,"))
        // One rule for the launch and the popup.
        assertTrue(src("device/tune/Tuner.kt").contains("TuneResolve.pick("))
        assertTrue(src("ui/GamePopupStatus.kt").contains("TuneResolve.pick("))
    }

    @Test fun copyToConsoleAndTheConsoleProfileLeaveTheGamesOwnAlone() {
        val s = TuneStore(ctx)
        s.setForKey("mine.iso", off)
        s.setForKey("plat:ps2", withPower)
        s.setForKeys(listOf("a.iso", "b.iso"), withPower.copy(underclock = Underclock.HEAVY)) // Copy to all - the OTHER games
        s.setForKey("plat:ps2", withPower.copy(underclock = Underclock.MEDIUM))           // the console's own profile edited
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey("mine.iso")!!.underclock)
        // The siblings list never holds the game itself (GameProfileDialog filters its own key out).
        assertTrue(src("ui/GameProfileDialog.kt").contains("it.stableKey != game.stableKey"))
    }

    @Test fun theSourceIsNamed() {
        assertEquals(TuneSource.GAME, TuneSource.of(app, app))
        assertEquals(TuneSource.APP, TuneSource.of("pkg:$app", app))
        assertEquals(TuneSource.EMULATOR, TuneSource.of("pkg:org.ppsspp", "/roms/psp/x.iso"))
        assertEquals(TuneSource.CONSOLE, TuneSource.of("plat:ps2", "/roms/ps2/x.iso"))
        assertEquals(TuneSource.DEVICE, TuneSource.of(null, app))
        assertEquals("AutoTDP Balanced · Underclock Off · Game profile", powerLine(off, null, 0, TuneSource.GAME, "Doom"))
        assertEquals("AutoTDP Balanced · Underclock Light (with power) · Device-wide", powerLine(withPower, null, 0, TuneSource.DEVICE, "Device"))
        assertEquals("AutoTDP Balanced · Underclock Medium (with power) · PS2 profile",
            powerLine(withPower, Underclock.MEDIUM, 0, TuneSource.CONSOLE, "PS2 profile"))
        assertEquals("Stock · Underclock Off · Heat guard −7 % for now · App profile",
            powerLine(TuneProfile(underclock = Underclock.OFF), null, 7, TuneSource.APP, "Doom"))
        assertEquals("Stock · Underclock Off · Handheld's own thermal limit · Device-wide",
            powerLine(TuneProfile(underclock = Underclock.OFF), null, 0, TuneSource.DEVICE, "Device", vendorThrottle = true))
        assertEquals("Game profile: Doom", TuneSource.GAME.text("Doom"))
        assertEquals("PS2 profile", TuneSource.CONSOLE.text("PS2 profile"))
        // "With power" that lowers nothing names nothing; a chosen level is named plainly.
        assertNull(TuneProfile().underclockPart())
        assertEquals("Underclock Heavy", TuneProfile(underclock = Underclock.HEAVY).underclockPart())
    }

    private fun src(n: String): String = listOf("src/main/java/com/shelf/launcher/$n", "app/src/main/java/com/shelf/launcher/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")
}
