package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Update 421: Orbit Deck discs never jump angle when the selection moves (the old spin = clock × 24 for the focused one only). */
class OrbitSpin421Test {
    @Test fun selectingStartsFromTheCurrentAngle() {
        val s = OdSpin()
        assertEquals(0f, s.angle(false, 10f), 0.001f)
        // Selected at t = 10: the old code jumped straight to 240°; now it starts at 0 and turns on.
        assertEquals(0f, s.angle(true, 10f), 0.001f)
        assertEquals(24f, s.angle(true, 11f), 0.001f)
    }

    @Test fun leavingEasesBackWithoutAJump() {
        val s = OdSpin()
        s.angle(true, 0f); assertEquals(48f, s.angle(true, 2f), 0.001f)
        // The frame it is deselected it is still where it was (no snap to 0) ...
        assertEquals(48f, s.angle(false, 2f), 0.001f)
        // ... then upright 0.4 s later.
        assertEquals(0f, s.angle(false, 2.4f), 0.001f)
        // Re-selected: continues from upright.
        assertEquals(0f, s.angle(true, 3f), 0.001f)
    }
}
