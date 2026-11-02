package com.shelf.launcher.data

import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.SpecialFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 450: ordinary apps (PXPlay, Asobi) opted into Continue playing — still apps, never every app. */
class ContinueApps450Test {
    private val pxplay = "psplay.grill.com"
    private val asobi = "com.example.asobi" // stand-in name; the rules don't depend on it
    private fun app(name: String, pkg: String) = Game(name, "App", 1, SourceType.APP, packageName = pkg)
    private val rom = Game("Quest", "SNES", 1, SourceType.ROM, romPath = "/r/q.sfc")
    private val droidGame = app("Racer", "com.studio.racer")
    private val px = app("PXPlay", pxplay)
    private val ab = app("Asobi", asobi)
    private val spotify = app("Spotify", "com.spotify.music")

    private fun state(opted: Set<String>) = LauncherUiState(
        games = listOf(rom, droidGame, px, ab, spotify),
        gameAppPackages = setOf("com.studio.racer"),
        libraryApps = LibraryAppsSettings(continueApps = opted),
        lastPlayedAt = mapOf(rom.stableKey to 100L, droidGame.stableKey to 200L, pxplay to 400L, asobi to 300L, "com.spotify.music" to 500L),
        specialFilter = SpecialFilter.RECENT
    )

    @Test fun nothingOptedInMeansNoOrdinaryAppInContinuePlaying() {
        val keys = state(emptySet()).visibleGames.map { it.stableKey }
        assertEquals(listOf(droidGame.stableKey, rom.stableKey), keys)
    }

    @Test fun optedInAppsJoinNewestFirstButOtherAppsStayOut() {
        val keys = state(setOf(pxplay, asobi)).visibleGames.map { it.stableKey }
        assertEquals(listOf(pxplay, asobi, droidGame.stableKey, rom.stableKey), keys)
        assertFalse("com.spotify.music" in keys)
    }

    @Test fun optingInDoesNotMakeItAnAndroidGame() {
        val s = state(setOf(pxplay))
        assertFalse(s.isAndroidGame(px))
        assertTrue(s.isContinueApp(px))
        assertFalse(s.isContinueApp(spotify))
        assertFalse(s.isContinueApp(droidGame)) // a game is a game, not a "continue app"
    }

    @Test fun oneEntryPerAppWhateverOpenedIt() {
        val dup = px.copy(name = "PXPlay (shortcut)")
        val s = state(setOf(pxplay)).let { it.copy(games = it.games + dup) }
        assertEquals(1, s.visibleGames.count { it.packageName == pxplay })
    }

    private fun ev(pkg: String, type: Int, t: Long, cls: String = "Main") = ContinueApps.Ev(pkg, cls, type, t)

    @Test fun onlyTimeInFrontCounts() {
        val e = listOf(
            ev(pxplay, ContinueApps.RESUMED, 1_000),
            ev(pxplay, ContinueApps.PAUSED, 61_000), // 60 s in front
            ev("other.app", ContinueApps.RESUMED, 61_000), // another app: not counted
            ev(pxplay, ContinueApps.RESUMED, 120_000),
            ev("android", ContinueApps.SCREEN_OFF, 150_000), // screen off ends it (30 s)
            ev(pxplay, ContinueApps.STOPPED, 900_000), // the late STOPPED while asleep changes nothing
        )
        val r = ContinueApps.foreground(e, setOf(pxplay), 0, 1_000_000)
        assertEquals(90_000L, r.use[pxplay]!!.foregroundMs)
        assertEquals(150_000L, r.use[pxplay]!!.lastUsed)
        assertTrue(r.openAtEnd.isEmpty())
    }

    @Test fun splashHandOverCountsAsOneStretchAndBouncesDont() {
        val e = listOf(
            ev(asobi, ContinueApps.RESUMED, 0, "Splash"),
            ev(asobi, ContinueApps.RESUMED, 2_000, "Stream"),
            ev(asobi, ContinueApps.PAUSED, 2_100, "Splash"),
            ev(asobi, ContinueApps.PAUSED, 600_000, "Stream"),
            ev(pxplay, ContinueApps.RESUMED, 700_000),
            ev(pxplay, ContinueApps.PAUSED, 702_000), // 2 s: a bounce, not use
        )
        val r = ContinueApps.foreground(e, setOf(pxplay, asobi), 0, 1_000_000)
        assertEquals(600_000L, r.use[asobi]!!.foregroundMs)
        assertFalse(pxplay in r.use)
    }

    @Test fun backgroundOnlyAppsAndUnoptedAppsAreIgnored() {
        val e = listOf(ev("com.spotify.music", ContinueApps.RESUMED, 0), ev("com.spotify.music", ContinueApps.PAUSED, 500_000))
        assertTrue(ContinueApps.foreground(e, setOf(pxplay), 0, 1_000_000).use.isEmpty())
        assertTrue(ContinueApps.foreground(e, emptySet(), 0, 1_000_000).use.isEmpty())
    }

    @Test fun aStretchStillGoingIsCarriedIntoTheNextLookWithoutDoubleCounting() {
        val first = ContinueApps.foreground(listOf(ev(pxplay, ContinueApps.RESUMED, 10_000)), setOf(pxplay), 0, 70_000)
        assertEquals(60_000L, first.use[pxplay]!!.foregroundMs)
        assertEquals(setOf("Main"), first.openAtEnd[pxplay])
        val second = ContinueApps.foreground(listOf(ev(pxplay, ContinueApps.PAUSED, 100_000)), setOf(pxplay), 70_000, 200_000, first.openAtEnd)
        assertEquals(30_000L, second.use[pxplay]!!.foregroundMs)
        assertTrue(second.openAtEnd.isEmpty())
    }

    @Test fun noteExplainsWhenUsageAccessIsOff() {
        val off = ContinueApps.note(1_000L, null, usage = false, now = 1_000L + 2 * 3600_000L)
        assertTrue(off.startsWith("Used 2 h ago"))
        assertTrue(off.contains("Usage access is off"))
        val on = ContinueApps.note(1_000L, 80 * 60_000L, usage = true, now = 61_000L)
        assertEquals("Used 1 min ago · 1 h 20 min in front · also noticed when opened outside Voltcap", on)
    }

    /** Launching an opted-in app records only the time: no session, play time, save sync, tuning or a second instance. */
    @Test fun launchPathStaysLight() {
        val src = listOf("src/main/java/com/shelf/launcher/LauncherViewModel.kt", "app/src/main/java/com/shelf/launcher/LauncherViewModel.kt")
            .map(::File).first { it.exists() }.readText()
        val start = src.indexOf("fun launchApp(app: Game")
        val body = src.substring(start, src.indexOf("// ---- Live app list ----", start))
        assertTrue(body.contains("pkg !in state.emulatorPackages && !inContinue"))
        assertTrue(body.contains("recentlyPlayedStore.recordPlayed(pkg, System.currentTimeMillis())"))
        for (heavy in listOf("playtimeStore", "SaveWatchWorker", "SaveObserver", "Tuner", "RunningGames.noteLaunched", "startSession")) {
            assertFalse("launchApp must not use $heavy", body.contains(heavy))
        }
    }
}
