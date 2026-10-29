package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 405: the slot and its case are one object (crossfade by one ramp), box side faces are a thin attached rim. */
class Flight405Test {
    @Test fun rampStartsAtZeroEndsAtOneAndNeverDecreases() {
        assertEquals(0f, CoverFlight.appear(0f), 0f)
        assertEquals(1f, CoverFlight.appear(1f), 0f)
        var prev = 0f
        for (i in 0..100) { val a = CoverFlight.appear(i / 100f); assertTrue(a >= prev); prev = a }
    }
    @Test fun slotEmptyWhenCaseIsOut() {
        CoverFlight.flights["k"] = androidx.compose.runtime.mutableStateOf(1f)
        assertEquals(1f, CoverFlight.hide("k"), 0f)
        CoverFlight.flights["k"] = androidx.compose.runtime.mutableStateOf(0f)
        assertEquals(0f, CoverFlight.hide("k"), 0f)
        CoverFlight.flights.remove("k")
    }
    @Test fun boxSideIsOnlyARim() { assertTrue(BOX_SIDE_KEEP in 0.5f..0.9f) }
}
