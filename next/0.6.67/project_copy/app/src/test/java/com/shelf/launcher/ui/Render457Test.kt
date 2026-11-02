package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 457: D172 (the Settings window's unseen shadow insets).
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
}
