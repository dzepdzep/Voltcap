package com.shelf.launcher.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** D148 (update 450): the Retroid's fast-charge stop — no fixed offset, a learned lead only on a fast charge. */
class ChargeFastStop450Test {
    @Test fun slowChargeAlwaysStopsAtTheLimit() {
        for (lead in 0..5) assertEquals(80, ChargeLimiter.stopLevel(lead, fast = false))
    }

    @Test fun fastChargeStopsEarlyOnlyByTheLearnedLeadCappedAtTwo() {
        assertEquals(80, ChargeLimiter.stopLevel(0, fast = true))
        assertEquals(79, ChargeLimiter.stopLevel(1, fast = true))
        assertEquals(78, ChargeLimiter.stopLevel(2, fast = true))
        assertEquals(78, ChargeLimiter.stopLevel(9, fast = true))
        assertEquals(80, ChargeLimiter.stopLevel(-3, fast = true))
    }

    @Test fun fastMeansUnderNinetySecondsPerPercent() {
        assertTrue(ChargeLimiter.isFast(38_000L))
        assertTrue(ChargeLimiter.isFast(90_000L))
        assertFalse(ChargeLimiter.isFast(90_001L))
        assertFalse(ChargeLimiter.isFast(0L)) // not measured yet
        assertFalse(ChargeLimiter.isFast(6 * 60_000L)) // the 500 mA PC port
    }

    @Test fun leadLearnsFromTheOvershootAndBacksOff() {
        assertEquals(2, ChargeLimiter.nextLead(0, 83)) // 80 -> 83: capped at 2
        assertEquals(1, ChargeLimiter.nextLead(0, 81))
        assertEquals(1, ChargeLimiter.nextLead(1, 80)) // right on: keep
        assertEquals(0, ChargeLimiter.nextLead(1, 79)) // stopped too early: back off
        assertEquals(0, ChargeLimiter.nextLead(0, 79))
        assertEquals(2, ChargeLimiter.nextLead(2, 81))
    }

    @Test fun chipShowsTheRealLevelWhenItIsNotEighty() {
        assertEquals("Stopped at 80%", ChargeLimiter.pausedLabel(null))
        assertEquals("Stopped at 80%", ChargeLimiter.pausedLabel(80))
        assertEquals("Stopped at 83% (limit 80%)", ChargeLimiter.pausedLabel(83))
    }

    /** The limit, the resume point and the AYN path are untouched; everything new is gated to the Retroid. */
    @Test fun limitHysteresisAndAynPathUnchanged() {
        val src = listOf("src/main/java/com/shelf/launcher/device/ChargeLimiter.kt", "app/src/main/java/com/shelf/launcher/device/ChargeLimiter.kt")
            .map(::File).first { it.exists() }.readText()
        assertTrue(src.contains("const val STOP_AT = 80"))
        assertTrue(src.contains("private const val RESUME_BELOW = 77"))
        assertTrue(src.contains("private const val RISE_TOLERANCE = 2"))
        assertTrue(src.contains("if (!isRetroid()) return null"))
        assertTrue(src.contains("val stopAt = if (isRetroid()) stopLevel(lead(context), fast) else STOP_AT"))
        assertTrue(src.contains("private fun verifyLater(context: Context) {\n        if (!isRetroid()) return"))
        assertTrue(src.contains("if (isRetroid()) runCatching { com.shelf.launcher.data.ChargeLog.event(context, event) }"))
    }
}
