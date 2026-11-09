package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 510 (staging only): Game shelf's idle bob settles after ~10 s without input and stops redrawing. */
class Batch510Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    @Test fun settlesAfterTenSecondsOnlyWithThePowerSaver() {
        assertEquals(10_000L, HomeIdle.SETTLE_MS)
        assertFalse(HomeIdle.settles(9_999L, enabled = true))
        assertTrue(HomeIdle.settles(10_000L, enabled = true))
        // "Save power when idle" off: the bob never settles (the owner's setting decides, as for the other idle steps).
        assertFalse(HomeIdle.settles(600_000L, enabled = false))
        // It settles before the 30 s idle step, which keeps its own meaning.
        assertTrue(HomeIdle.SETTLE_MS < HomeIdle.IDLE_MS)
    }

    @Test fun anyInputWakesItAndTheWatchHasNoExtraTimer() {
        val p = src("ui/PowerSaver.kt")
        assertTrue(p.contains("if (settled) { settled = false; wake.trySend(Unit) }"))
        // One loop each, as before (home + second screen): the settle is one more step of HomeIdle.watch, not a timer of its own.
        assertEquals(2, Regex("suspend fun watch\\(\\)").findAll(p).count())
        assertTrue(p.contains("withTimeoutOrNull(next.coerceIn(500L, 30_000L)) { wake.receive() }"))
    }

    @Test fun theBobClockIsDroppedOnceSettled() {
        val s = src("ui/ShelfHome.kt")
        assertTrue(s.contains("val settle = resting && (HomeIdle.settled || LocalSceneCovered.current)"))
        assertTrue(s.contains("&& !(settle && bobAtRest)) rememberShelfBob() else null"))
        assertTrue(s.contains("val amp = bobK * bobAmp.value"))
        assertTrue(SHELF_BOB_SETTLE_MS in 500..1500 && SHELF_BOB_RESUME_MS in 300..1000)
        // The setting still turns it off entirely.
        assertTrue(s.contains("Features.shelfBob.state.value &&"))
    }
}
