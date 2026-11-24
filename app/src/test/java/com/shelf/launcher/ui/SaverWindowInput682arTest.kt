package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 682ar (D391): keys in a game window keep the screensaver away - its wait counts the last input of any kind. */
class SaverWindowInput682arTest {
    private val idle = 5 * 60_000L
    @Test fun aWindowKeyAMomentAgoKeepsTheSaverAway() {
        // the wait began 6 minutes ago (no home input since), but a window key came 10 s ago: 4 min 50 s still to wait
        assertEquals(idle - 10_000L, ScreensaverGate.waitMs(idle, sinceStartMs = 6 * 60_000L, sinceAnyInputMs = 10_000L))
    }
    @Test fun noInputAnywhereStartsItOnTime() {
        assertTrue(ScreensaverGate.waitMs(idle, sinceStartMs = idle, sinceAnyInputMs = idle + 1) <= 0L)
    }
    @Test fun theHomesOwnRestartStillCounts() {
        // home input restarted the wait 1 min ago; the last input of any kind was that same press
        assertEquals(4 * 60_000L, ScreensaverGate.waitMs(idle, sinceStartMs = 60_000L, sinceAnyInputMs = 60_000L))
    }
    @Test fun windowKeysPokeTheIdleClock() {
        val src = java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt").readText()
        val i = src.indexOf("\"winkey \${e.nativeKeyEvent.keyCode}")
        assertTrue("the game window's key preview pokes HomeIdle", i > 0 && src.substring(i, i + 600).contains("HomeIdle.poke()"))
    }
}
