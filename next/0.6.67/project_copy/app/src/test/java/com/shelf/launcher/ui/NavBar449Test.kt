package com.shelf.launcher.ui

import com.shelf.launcher.data.GestureHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 449: "Hide navigation bar" and "Force-hide gesture hint" — what Voltcap's windows ask for, and that the screens show
 * the live state. Retroid (the device's own hide_nav_bar owns the bar): the window asks only while that setting is off.
 */
class NavBar449Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/", "../src_copy/", "app/src/main/java/com/shelf/launcher/", "src/main/java/com/shelf/launcher/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test fun windowRequestTruthTable() {
        // Hide navigation bar off: never asks.
        assertEquals(false, GestureHint.windowHidesNavRule(immersive = false, systemOwns = false, deviceHides = false))
        assertEquals(false, GestureHint.windowHidesNavRule(immersive = false, systemOwns = true, deviceHides = true))
        // On, any device but a Retroid with its own setting hiding the bar: asks.
        assertEquals(true, GestureHint.windowHidesNavRule(immersive = true, systemOwns = false, deviceHides = false))
        assertEquals(true, GestureHint.windowHidesNavRule(immersive = true, systemOwns = false, deviceHides = true))  // AYN: both
        assertEquals(true, GestureHint.windowHidesNavRule(immersive = true, systemOwns = true, deviceHides = false))  // Retroid, setting off: was false (pill showed)
        // Retroid with its own setting hiding the bar: stays out of it (update 155: asking brought the pill back).
        assertEquals(false, GestureHint.windowHidesNavRule(immersive = true, systemOwns = true, deviceHides = true))
    }

    @Test fun followsTheDeviceSettingWithoutPolling() {
        val g = src("data/GestureHint.kt")
        assertTrue(g.contains("registerContentObserver(Settings.Global.getUriFor(\"hide_nav_bar\")"))
        assertTrue(g.contains("immersive && !(systemOwnsNav && deviceNavHidden.value)"))
        // The windows re-apply when it changes.
        assertTrue(src("MainActivity.kt").contains("LaunchedEffect(immersive, deviceHides)"))
        assertTrue(src("ui/SystemBars.kt").contains("DisposableEffect(view, immersive, deviceHides)"))
        assertTrue(src("ui/SecondScreen.kt").contains("LaunchedEffect(showing, hideNavBar, deviceHidesNav)"))
    }

    @Test fun settingsCardShowsTheLiveForceState() {
        val c = src("ui/GestureHintCard.kt")
        assertTrue(c.contains("val force = GestureHint.forceState.value"))
        assertTrue(!c.contains("remember { mutableStateOf(GestureHint.forceOn(context)) }"))
        val g = src("data/GestureHint.kt")
        assertTrue(g.contains("immersiveState.value = on\n        forceState.value = forceOn(c)"))
        assertTrue(g.contains("forceState.value = on"))
    }

    @Test fun limitNotesOnlyWhenTheSwitchesCantDoIt() {
        // AYN, Hide on, Force-hide off, device setting not hiding: the pill can stay — say so.
        assertTrue(GestureHint.limitNote(immersive = true, force = false, ayn = true, deviceHides = false) != null)
        // Everything consistent: nothing to say.
        assertEquals(null, GestureHint.limitNote(immersive = true, force = true, ayn = true, deviceHides = true))
        assertEquals(null, GestureHint.limitNote(immersive = true, force = false, ayn = false, deviceHides = false))
        assertEquals(null, GestureHint.limitNote(immersive = false, force = false, ayn = true, deviceHides = false))
        // Hide off while the device's own setting hides it: explained.
        assertTrue(GestureHint.limitNote(immersive = false, force = false, ayn = false, deviceHides = true) != null)
    }
}
