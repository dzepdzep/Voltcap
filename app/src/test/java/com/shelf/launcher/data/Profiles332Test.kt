package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 332: profiles — the first one keeps the old files (and Google Drive's), the others their own. */
class Profiles332Test {
    @Test
    fun filesPerProfile() {
        assertEquals("favorites", Profiles.fileFor("favorites", 0))
        assertEquals("favorites__p2", Profiles.fileFor("favorites", 2))
        assertEquals("playtime__p7", Profiles.fileFor("playtime", 7))
        // A profile's files are still game data for the backup (the part before "__p").
        assertTrue("recently_played__p3".substringBefore("__p") in Backup.GAME_DATA)
        assertEquals(listOf("favorites", "recently_played", "playtime"), Profiles.PER_PROFILE)
    }

    @Test
    fun startupModesAndSpeeds() {
        assertEquals(Features.StartupMode.PROFILES_LAYOUT, Features.startupMode.default)
        assertTrue(Features.RolloutSpeed.values().all { it.durMs in 300..900 && it.stepMs in 20..120 })
    }
}
