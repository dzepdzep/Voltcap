package com.shelf.launcher.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 682at (owner 24 Nov, option 2: "investigate why normal controller presses restart ~10 s of charging-related redraws ... avoid
 * restarting it on unrelated game navigation ... a static charging indicator unless charging state or relevant battery information
 * changes"): the status bar's charging light runs as feedback when charging starts and when its level changes, then rests - a
 * press (HomeIdle settling and waking) never starts it again. The legacy rule (Update 511) restarts it on every press - the check
 * fails on the old implementation.
 */
@RunWith(RobolectricTestRunner::class)
class ChargeLight682atTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private val percent = mutableIntStateOf(50)
    private val charging = mutableStateOf(true)

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeBy(16); left -= 16 }
        compose.waitForIdle()
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { PlainBattery(percent.intValue, charging.value, Color.White, 18.dp) }
        advance(200)
    }

    /** What a press does to the idle state: settled after 10 s without input, woken by the press. */
    private fun press() {
        HomeIdle.settleForTest(true); advance(500)
        HomeIdle.settleForTest(false); advance(4000)
    }

    @After fun reset() { ChargeLight.legacy.value = false; HomeIdle.settleForTest(false) }

    @Test fun pressesNeverRestartTheLight() {
        ChargeLight.legacy.value = false
        ChargeLight.runs = 0
        show()
        advance(6000) // two sweeps and on to the still place
        assertEquals("the light did not run once when charging started", 1, ChargeLight.runs)
        repeat(3) { press() }
        assertEquals("a press started the light again", 1, ChargeLight.runs)
        percent.intValue = 51; advance(6000)
        assertEquals("a new level while charging is feedback", 2, ChargeLight.runs)
        charging.value = false; advance(500); charging.value = true; advance(6000)
        assertEquals("charging starting again is feedback", 3, ChargeLight.runs)
    }

    @Test fun legacyRuleRestartsOnEveryPress() {
        ChargeLight.legacy.value = true
        show()
        ChargeLight.runs = 0
        repeat(3) { press() }
        assertTrue("the old rule no longer restarts on a press - the fixture lost the case (${ChargeLight.runs})", ChargeLight.runs >= 3)
    }
}
