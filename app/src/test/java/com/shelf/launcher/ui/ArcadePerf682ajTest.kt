package com.shelf.launcher.ui

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 682aj (owner 23 Nov 18:23 - Arcade stutter, KONKR x1.0 kp10 framestats: ~8 ms issuing draw commands + ~11 ms GPU per frame
 * on the Arcade home, Cinema 2 + 5; the game window's INSERT COIN asked for a frame every vsync). Fails on 682ai: no layer
 * caching, the blink an endless transition.
 */
class ArcadePerf682ajTest {
    private fun src(name: String) = File("src/main/java/com/shelf/launcher/ui/$name").readText()

    @Test fun uncachedPartsKeepTheirModifier() {
        val m = Modifier
        assertSame(m, m.arcadeCached(false))
        assertNotSame(m, m.arcadeCached(true))
    }

    @Test fun insertCoinBlinksByStateChangesOnly() {
        assertEquals(560L, ARCADE_BLINK_MS)
        val s = src("WinScenesArcade.kt")
        assertFalse("an endless transition draws every frame", s.contains("rememberInfiniteTransition"))
        assertTrue(s.contains("delay(ARCADE_BLINK_MS)"))
    }

    @Test fun staticArcadePartsAreCached() {
        val s = src("StyleArcade.kt")
        assertTrue("the room", s.contains("Canvas(modifier.arcadeCached(true)) { drawArcadeRoom"))
        assertTrue("the row's machines you're not on", s.contains("cache = !on,"))
        assertTrue("the big machine's body", s.contains("cacheBody = true)"))
        // the hall's machines turn and scale every frame of a glide: never cached there
        val hall = s.substringAfter("private fun ArcadeHallMode").substringBefore("private fun ArcadeCategory")
        assertFalse(hall.contains("cache = ") || hall.contains("cacheBody"))
    }
}
