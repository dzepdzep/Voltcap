package com.shelf.launcher.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 674i (owner report 15 Nov, Flip 2): at the 80 % limit the charger input was suspended (input_suspend), Android reported
 * "not plugged", the pause was released, the charger came back as a fresh plug-in (the system's charging sparkle) and was
 * suspended again - every 1-2 s. Voltcap's own suspension is not an unplug.
 */
class ChargeLoop674Test {
    private fun want(plugged: Boolean, held: Boolean, pct: Int, paused: Boolean?) =
        ChargeLimiter.wantPause(limitOn = true, plugged = plugged, inputHeld = held, percent = pct, stopAt = 80, paused = paused)

    @Test fun theFlip2LoopNoLongerReleasesThePause() {
        // paused at 80 through input_suspend: Android now says "not plugged" - the pause holds (the old rule said false)
        assertTrue(want(plugged = false, held = true, pct = 80, paused = true))
        assertTrue(want(plugged = false, held = true, pct = 79, paused = true))   // between resume and stop: keep it
        assertTrue(want(plugged = false, held = true, pct = 77, paused = true))
    }

    @Test fun underTheResumePointItChargesAgainOnce() =
        assertFalse(want(plugged = false, held = true, pct = 76, paused = true))

    @Test fun aRealUnplugStillReleases() =
        assertFalse(want(plugged = false, held = false, pct = 80, paused = true))

    @Test fun pluggedBehaviourIsUnchanged() {
        assertTrue(want(plugged = true, held = false, pct = 80, paused = false))
        assertFalse(want(plugged = true, held = false, pct = 78, paused = false))
        assertTrue(want(plugged = true, held = false, pct = 78, paused = true))
        assertFalse(want(plugged = true, held = false, pct = 76, paused = true))
    }

    @Test fun limitOffNeverPauses() =
        assertFalse(ChargeLimiter.wantPause(false, true, false, 95, 80, true))
}
