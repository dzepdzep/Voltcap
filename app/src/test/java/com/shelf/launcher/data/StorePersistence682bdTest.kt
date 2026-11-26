package com.shelf.launcher.data

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

/**
 * 682bd audit (owner 25 Nov 21:24): the stores that had no persistence test - play time and a game's own emulator / launch
 * settings - keep what was saved across a restart (a new store), keep an explicit "off" as off, and a change to one game
 * leaves every other game and the console's settings alone.
 */
@RunWith(RobolectricTestRunner::class)
class StorePersistence682bdTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        listOf("playtime", "game_profiles").forEach { ctx.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun playTimeSurvivesARestartAndTheOneTimeCleanUp() {
        val a = "/roms/ps2/wwe13.iso"; val b = "com.example.androidgame"
        val s = PlaytimeStore(ctx, main = true)
        s.startSession(a, 1_000_000L)
        assertEquals(a, s.endSession(1_000_000L + 3_600_000L)?.first)
        assertTrue(s.addPlayed(b, 5_000_000L, 1_200_000L))
        val totals = s.loadTotals()
        assertEquals(3_600_000L, totals[a]); assertEquals(1_200_000L, totals[b])
        // restart: a new store, its one-time clean-up ran already - the same totals, twice
        assertEquals(totals, PlaytimeStore(ctx, main = true).loadTotals())
        assertEquals(totals, PlaytimeStore(ctx, main = true).loadTotals())
        // too short to count, and a clock that went back: nothing added, nothing lost
        s.startSession(a, 9_000_000L); assertNull(s.endSession(9_000_000L + 5_000L))
        s.startSession(a, 9_000_000L); assertNull(s.endSession(8_000_000L))
        assertEquals(totals, PlaytimeStore(ctx, main = true).loadTotals())
    }

    @Test fun aGamesOwnEmulatorAndLaunchSettingsStayAndStayItsOwn() {
        val st = GameProfileStore(ctx)
        val a = "/roms/switch/game.nsp"; val b = "/roms/switch/other.nsp"
        st.updateGame(a) { it.copy(emulatorPackage = "dev.eden.duo", launchExtras = listOf(LaunchExtra("mode", "2", LaunchExtraType.INT)), syncSaves = false) }
        st.updateGame(b) { it.copy(perfOverlay = true) }
        st.updateCategory("switch") { it.copy(syncSaves = true) }
        val again = GameProfileStore(ctx) // restart
        assertEquals("dev.eden.duo", again.game(a).emulatorPackage)
        assertEquals(listOf(LaunchExtra("mode", "2", LaunchExtraType.INT)), again.game(a).launchExtras)
        assertEquals(false, again.game(a).syncSaves) // an explicit off is kept as off, not "follow the setting"
        assertEquals(true, again.game(b).perfOverlay)
        assertEquals(true, again.category("switch").syncSaves)
        // one game changed or cleared: the other and the console untouched
        again.updateGame(a) { it.copy(emulatorPackage = null) }
        assertEquals(true, GameProfileStore(ctx).game(b).perfOverlay)
        assertEquals(true, GameProfileStore(ctx).category("switch").syncSaves)
        assertEquals(false, GameProfileStore(ctx).game(a).syncSaves)
        again.clearGame(a)
        assertTrue(GameProfileStore(ctx).game(a).isEmpty())
        assertFalse(GameProfileStore(ctx).game(b).isEmpty())
        assertEquals(true, GameProfileStore(ctx).category("switch").syncSaves)
    }
}
