package com.shelf.launcher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shelf.launcher.device.tune.TuneProfile
import com.shelf.launcher.device.tune.TuneStore
import com.shelf.launcher.device.tune.Underclock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 682bd (owner 25 Nov 21:24 - "prove that v0.7.0 preserves existing user data"): a synthetic v0.6.73-style configuration -
 * every settings file Voltcap writes, typed values of every kind, per-game data, a device's own files, a per-game Underclock
 * Off - goes through backup, wipe, restore, a second restore, the safety snapshot, a cross-device import and the start-up
 * migrations run twice. Nothing may be lost, defaulted or changed on the way.
 */
@RunWith(RobolectricTestRunner::class)
class DataIntegrity682bdTest {
    private lateinit var ctx: Context

    /** Every prefs file name the app writes (read from the sources) plus the stores named by constant. */
    private fun allNames(): List<String> {
        val root = listOf("src/main/java/com/shelf/launcher", "app/src/main/java/com/shelf/launcher").map { File(it) }.first { it.exists() }
        val found = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            Regex("getSharedPreferences\\(\"([a-zA-Z0-9_]+)\"").findAll(f.readText()).map { it.groupValues[1] }
        }.toSet()
        return (found + SettingsScopes.GLOBAL_FILES + SettingsScopes.DEVICE_FILES + Backup.GAME_DATA + listOf("voltcap_features", "tuner", "r1_tiles"))
            .filter { SettingsScopes.scopeOf(it).let { s -> s == SettingsScope.GLOBAL || s == SettingsScope.DEVICE } }
            .sorted()
    }

    private fun prefs(n: String) = ctx.getSharedPreferences(n, Context.MODE_PRIVATE)

    /** Typed synthetic values per file - no ScreenScraper addresses (those are cleaned on import on purpose). */
    private fun seed(names: List<String>) {
        names.forEachIndexed { i, n ->
            prefs(n).edit().clear()
                .putString("qa_s", "v0.6.73 value $i · ünï · 完全版")
                .putInt("qa_i", 1000 + i).putLong("qa_l", 1_700_000_000_000L + i).putFloat("qa_f", i + 0.5f)
                .putBoolean("qa_b_true", true).putBoolean("qa_b_false", false) // an explicit false is a choice too
                .putStringSet("qa_set", setOf("a$i", "b$i", "c d"))
                .putString("qa_json", """{"k":"v$i","n":[1,2,3],"off":"OFF"}""")
                .commit()
        }
        // The real tuner format: a game's explicit Underclock Off and the migration flag.
        TuneStore(ctx).setForKey("com.example.wwe13", TuneProfile(autoTdp = true, underclock = Underclock.OFF))
    }

    private fun state(names: List<String>): Map<String, Map<String, Any?>> = names.associateWith { prefs(it).all.toSortedMap() }

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    @Test fun aBackupRestoresTheWholeConfigurationAndAgainIsTheSame() {
        val names = allNames()
        assertTrue("files found: ${names.size}", names.size > 60)
        seed(names)
        val before = state(names)
        val file = File(ctx.cacheDir, "qa-backup.json")
        val count = Backup.exportTo(ctx, file, includeGameData = true, includeDeviceSetup = true)
        assertTrue(count >= names.size)
        // Something else happened since: values changed, keys added, files emptied.
        names.forEachIndexed { i, n -> prefs(n).edit().apply { if (i % 3 == 0) clear() else putString("qa_s", "changed").putInt("junk", 7) }.commit() }
        val r1 = Backup.applyDocument(ctx, Backup.parseDocument(file.readText()), importDeviceSetup = true, reason = "qa", importAccounts = false)
        assertEquals(before, state(names))
        // A second restore of the same backup changes nothing.
        Backup.applyDocument(ctx, Backup.parseDocument(file.readText()), importDeviceSetup = true, reason = "qa", importAccounts = false)
        assertEquals(before, state(names))
        // The explicit Off is still Off when the tuner reads it again (its migration check runs on every start).
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey("com.example.wwe13")!!.underclock)
        assertEquals(Underclock.OFF, TuneStore(ctx).forKey("com.example.wwe13")!!.underclock)
        assertNotNull(r1.snapshotId)
    }

    @Test fun theSafetySnapshotPutsBackWhatTheImportReplaced() {
        val names = allNames()
        seed(names)
        val file = File(ctx.cacheDir, "qa-backup2.json")
        Backup.exportTo(ctx, file, includeGameData = true, includeDeviceSetup = true)
        names.forEach { n -> prefs(n).edit().putString("qa_s", "mine now").commit() }
        val mine = state(names)
        val r = Backup.applyDocument(ctx, Backup.parseDocument(file.readText()), importDeviceSetup = true, reason = "qa", importAccounts = false)
        Backup.restoreSnapshot(ctx, r.snapshotId!!)
        assertEquals(mine, state(names))
    }

    @Test fun anotherDevicesBackupLeavesThisDevicesOwnSettingsAlone() {
        val names = allNames()
        seed(names)
        val file = File(ctx.cacheDir, "qa-backup3.json")
        Backup.exportTo(ctx, file, includeGameData = true, includeDeviceSetup = true)
        val device = names.filter { SettingsScopes.scopeOf(it) == SettingsScope.DEVICE }
        assertTrue(device.contains("tuner") && device.contains("r1_tiles") && device.contains("emulator_configs"))
        device.forEach { n -> prefs(n).edit().putString("qa_s", "this handheld").commit() }
        val here = state(device)
        Backup.applyDocument(ctx, Backup.parseDocument(file.readText()), importDeviceSetup = false, reason = "qa", importAccounts = false)
        assertEquals(here, state(device))
        // and the appearance file's device keys (lights, second screen) stay too
        prefs("appearance").edit().putBoolean("lights_on", true).commit()
        val exported = File(ctx.cacheDir, "qa-backup4.json")
        Backup.exportTo(ctx, exported, includeGameData = false, includeDeviceSetup = false)
        prefs("appearance").edit().putBoolean("lights_on", false).commit()
        Backup.applyDocument(ctx, Backup.parseDocument(exported.readText()), importDeviceSetup = false, reason = "qa", importAccounts = false)
        assertEquals(false, prefs("appearance").getBoolean("lights_on", true))
    }

    @Test fun aBackupWithoutGameDataLeavesThisDevicesGameDataAlone() {
        val names = allNames()
        seed(names)
        val file = File(ctx.cacheDir, "qa-backup5.json")
        Backup.exportTo(ctx, file, includeGameData = false, includeDeviceSetup = true)
        val games = names.filter { it in Backup.GAME_DATA }
        assertTrue(games.containsAll(listOf("favorites", "playtime", "art_overrides", "game_overrides", "ra_matches")))
        games.forEach { n -> prefs(n).edit().putString("qa_s", "played since").commit() }
        val now = state(games)
        Backup.applyDocument(ctx, Backup.parseDocument(file.readText()), importDeviceSetup = true, reason = "qa", importAccounts = false)
        assertEquals(now, state(games))
    }

    @Test fun startUpMigrationsAreSafeToRunTwice() {
        val names = allNames()
        seed(names)
        SsMedia.migrateOnce(ctx)
        TuneStore(ctx)
        val once = state(names)
        SsMedia.migrateOnce(ctx)
        TuneStore(ctx); TuneStore(ctx)
        assertEquals(once, state(names))
        // and none of the synthetic (address-free) values was touched by the address clean-up at all
        names.forEach { n -> assertEquals(n, 1000 + names.indexOf(n), prefs(n).getInt("qa_i", -1)) }
    }
}
