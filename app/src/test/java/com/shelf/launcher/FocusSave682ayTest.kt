package com.shelf.launcher

import com.shelf.launcher.data.CategorySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 682ay (Arcade B, experimental): a category save never writes an older focus key back over the newest saved one. */
class FocusSave682ayTest {
    @Test fun noSavedKeyKeepsTheSettings() {
        val s = CategorySettings(lastFocusedKey = "a")
        assertSame(s, FocusSave.merge(s, null))
    }

    @Test fun theSameKeyKeepsTheSettings() {
        val s = CategorySettings(lastFocusedKey = "a")
        assertSame(s, FocusSave.merge(s, "a"))
    }

    @Test fun aNewerSavedKeyWinsAndTheRestStays() {
        val s = CategorySettings(order = listOf("x", "y"), hidden = setOf("y"), lastFocusedKey = "old")
        val m = FocusSave.merge(s, "new")
        assertEquals("new", m.lastFocusedKey)
        assertEquals(s.order, m.order)
        assertEquals(s.hidden, m.hidden)
    }
}
