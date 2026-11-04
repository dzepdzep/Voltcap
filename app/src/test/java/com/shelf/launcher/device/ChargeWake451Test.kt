package com.shelf.launcher.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 451: the Retroid charge-stop wake lock only when the limit has something to do; one stop check at a time. */
class ChargeWake451Test {
    @Test fun wakeLockOnlyWhileTheLimitHasWork() {
        assertTrue(ChargeLimiter.wantsAwake(limitOn = true, plugged = true, cableChanged = false, holding = false))
        assertTrue(ChargeLimiter.wantsAwake(limitOn = true, plugged = false, cableChanged = true, holding = false))
        assertTrue(ChargeLimiter.wantsAwake(limitOn = false, plugged = false, cableChanged = false, holding = true)) // a stop still held
        assertFalse(ChargeLimiter.wantsAwake(limitOn = true, plugged = false, cableChanged = false, holding = false)) // on battery
        assertFalse(ChargeLimiter.wantsAwake(limitOn = false, plugged = true, cableChanged = false, holding = false)) // limit off
        assertFalse(ChargeLimiter.wantsAwake(limitOn = false, plugged = false, cableChanged = true, holding = false))
    }

    private val src = listOf("src/main/java/com/shelf/launcher/device/ChargeLimiter.kt", "app/src/main/java/com/shelf/launcher/device/ChargeLimiter.kt")
        .map(::File).first { it.exists() }.readText()

    @Test fun olderStopChecksGiveWayAndRespectUnplugAndSetting() {
        assertTrue(src.contains("val gen = ++verifyGen"))
        assertTrue(src.contains("if (gen != verifyGen) break"))
        assertTrue(src.contains("if (paused != true || draining || lastPlugged != true || !ownSwitch(context)) return false"))
        assertTrue(src.contains("val awake = if (needsAwake(app, i)) holdAwake(app, 10_000L) else null"))
        assertTrue(src.contains("if (!isRetroid()) return false")) // needsAwake: never on the AYN path
    }
}
