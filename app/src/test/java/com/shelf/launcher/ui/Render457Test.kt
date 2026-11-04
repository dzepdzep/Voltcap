package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 457: D172 (the Settings window's unseen shadow insets; rows popping in at the bottom edge).
 */
class Render457Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    @Test fun settingsWindowIsFlat() {
        val s = src("SettingsDialog.kt")
        assertTrue(s.contains("SettingsWindowFlat()"))
        assertTrue(s.contains("window.setElevation(0f)"))
    }

    /** The old focus scroll stopped with the focused row flush on the edge (the next row unseen); now it keeps a margin. */
    @Test fun focusScrollKeepsNextRowInView() {
        // pane 400, row 60 just below the edge (offset 380): old default = 40 (row flush), now + 56 margin.
        assertEquals(96f, lookAheadDistance(380f, 60f, 400f, 56f), 0.01f)
        // already inside with room: no scroll.
        assertEquals(0f, lookAheadDistance(150f, 60f, 400f, 56f), 0.01f)
        // moving up: row above the top keeps its margin too.
        assertEquals(-76f, lookAheadDistance(-20f, 60f, 400f, 56f), 0.01f)
        // a row taller than the pane: no margin, no fight.
        assertEquals(0f, lookAheadDistance(-10f, 500f, 400f, 56f), 0.01f)
        // margin never above a quarter of the pane.
        assertEquals(30f + 50f, lookAheadDistance(170f, 60f, 200f, 56f), 0.01f)
    }

    @Test fun lookAheadIsWired() {
        val s = src("SettingsDialog.kt")
        assertTrue(s.contains("SettingsLookAhead {"))
        assertTrue(s.contains("LocalBringIntoViewSpec provides spec"))
    }
}
