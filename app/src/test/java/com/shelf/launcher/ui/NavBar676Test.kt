package com.shelf.launcher.ui

import com.shelf.launcher.data.GestureHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 676b: Hide navigation bar's white pill on Retroid (RP Nova, 16 Nov). Android's NavigationBar0 was hidden; the pill was
 * SystemUI's SecondaryHomeHandle0, shown exactly while the focused window asked for the bar hidden with the device's own
 * hide_nav_bar at 1. Voltcap's "don't ask there" rule was gated on the root service and decided once per process.
 */
class NavBar676Test {
    private fun src(rel: String) = listOf("src/main/java/com/shelf/launcher/$rel", "app/src/main/java/com/shelf/launcher/$rel")
        .map(::File).first { it.exists() }.readText()

    @Test fun retroidIsTheFirmwareNotTheRootService() {
        assertTrue(GestureHint.retroidFirmware("Retroid Pocket Nova", "Retroid"))
        assertTrue(GestureHint.retroidFirmware("Retroid Pocket Classic", null))
        assertFalse(GestureHint.retroidFirmware("AYN Thor", "AYN"))
        assertFalse(GestureHint.retroidFirmware("KONKR Pocket FIT", "KONKR"))
        val g = src("data/GestureHint.kt")
        val def = g.substring(g.indexOf("val systemOwnsNav"), g.indexOf("internal fun retroidFirmware("))
        assertFalse("decided without the root service", def.contains("RootShell"))
        // the AYN note is read when shown, not once per process
        val ayn = g.substring(g.indexOf("val aynHandle"), g.indexOf("val aynHandle") + 200)
        assertFalse(ayn.contains("by lazy"))
    }

    @Test fun novaWithItsOwnHideNavBarDoesNotAsk() {
        // the Nova's state: Retroid firmware, hide_nav_bar 1, Hide navigation bar on -> the window does not ask (no handle)
        assertFalse(GestureHint.windowHidesNavRule(immersive = true, systemOwns = GestureHint.retroidFirmware("Retroid Pocket Nova", "Retroid"), deviceHides = true))
        // its own setting off: Voltcap's window hides the bar itself
        assertTrue(GestureHint.windowHidesNavRule(immersive = true, systemOwns = true, deviceHides = false))
        // Hide navigation bar off: never asks
        assertFalse(GestureHint.windowHidesNavRule(immersive = false, systemOwns = false, deviceHides = false))
    }

    @Test fun barsComeBackOnlyOnEventsNeverOnATimer() {
        // keyboard closed -> ask again
        assertTrue(SystemBars.reapplyAfter(imeAnim = true, navAnim = false, imeShown = false, navShown = false, wantHidden = true, focused = true))
        // the bar came in for real while it should be hidden -> once
        assertTrue(SystemBars.reapplyAfter(imeAnim = false, navAnim = true, imeShown = false, navShown = true, wantHidden = true, focused = true))
        // not while the keyboard is up, not without focus (another window's choice), not when the setting is off
        assertFalse(SystemBars.reapplyAfter(false, true, imeShown = true, navShown = true, wantHidden = true, focused = true))
        assertFalse(SystemBars.reapplyAfter(false, true, false, true, wantHidden = true, focused = false))
        assertFalse(SystemBars.reapplyAfter(false, true, false, true, wantHidden = false, focused = true))
        // a bar that went away -> nothing (no loop)
        assertFalse(SystemBars.reapplyAfter(false, true, false, navShown = false, wantHidden = true, focused = true))
        val main = src("MainActivity.kt")
        assertFalse("the 1.5 s / 15 s polling loop is gone", main.contains("SystemBars.navStuck"))
        assertTrue(main.contains("if (hasFocus) hideSystemBars()"))
    }
}

/** 676d (D334b): Back at the home's root never hands the screen to the owner's Voltcap while a QA script drives the QA app. */
class QaHarnessBack676Test {
    @Test fun backAtRoot() {
        // the owner's Voltcap / the QA app by hand, not the home app: to the background as before
        assertTrue(com.shelf.launcher.data.QaVariant.backAtRootLeaves(isDefaultHome = false, harness = false))
        // the home app: stays
        assertFalse(com.shelf.launcher.data.QaVariant.backAtRootLeaves(isDefaultHome = true, harness = false))
        // a QA script running: stays in front
        assertFalse(com.shelf.launcher.data.QaVariant.backAtRootLeaves(isDefaultHome = false, harness = true))
        val main = listOf("src/main/java/com/shelf/launcher/MainActivity.kt", "app/src/main/java/com/shelf/launcher/MainActivity.kt").map(::File).first { it.exists() }.readText()
        assertTrue(main.contains("QaVariant.backAtRootLeaves(com.shelf.launcher.data.HomeLauncher.isDefault(this@MainActivity), harness)) moveTaskToBack(true)"))
        val qa = listOf("src/main/java/com/shelf/launcher/data/QaVariant.kt", "app/src/main/java/com/shelf/launcher/data/QaVariant.kt").map(::File).first { it.exists() }.readText()
        assertTrue(qa.contains("fun harnessActive(context: Context): Boolean = isolated(context) &&"))
    }
}
