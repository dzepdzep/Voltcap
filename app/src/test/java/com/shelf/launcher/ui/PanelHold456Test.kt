package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 456 (owner, D64 option B): the app drawer and Settings hold the home's background — scene, wallpaper, clips — on
 * its current frame (paused, never dropped or blacked out); nothing else is added to the hold.
 */
class PanelHold456Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    @Test fun onlyDrawerAndSettingsHold() {
        val s = src("LauncherScreen.kt")
        assertTrue(s.contains("val panelHold = (showAppDrawer || showMenu || showSettings) && TestHooks.panelHold.value"))
        assertTrue(s.contains("HomeIdle.rest || panelHold), //"))
        assertTrue(s.contains("LocalScenePanelHold provides panelHold"))
        // Every home clip pauses (keeps its place) rather than being removed.
        // (Update 478, B2: the backdrop clip also pauses under an opaque full-screen page — clipCovered.)
        assertEquals(2, Regex("HomeIdle.rest \\|\\| panelHold( \\|\\| clipCovered)? \\|\\| \\(com.shelf.launcher.data.Features.clipIdlePause").findAll(s).count())
        assertTrue(s.contains("val clipCovered = categoryScreenOpen || guideForGame != null || manualFor != null || artViewer != null || viewingShots != null"))
    }

    @Test fun showcaseMonitorClipPausedNotDropped() {
        val s = src("ShowcaseScene.kt")
        assertTrue(s.contains("(!LocalSceneCovered.current || panelHold)"))
        assertTrue(s.contains("paused = panelHold"))
    }

    @Test fun homeMotionHeldOnlyInTheHomeWindow() {
        val p = src("PowerSaver.kt")
        assertTrue(p.contains("internal fun calmHere(): Boolean"))
        assertTrue(p.contains("window?.decorView === v.rootView"))
        assertTrue(src("LauncherScreen.kt").contains("SideEffect { HomeIdle.panelHeld = panelHold; MediaClock.hold(panelHold) }"))
        assertTrue(src("LauncherScreen.kt").contains("EffectClockDriver(hold = (showAppDrawer || showMenu || showSettings) && TestHooks.panelHold.value)"))
        // The home's sliding names, focused piece's motion and focus bolts use it (the panels' own windows keep moving).
        assertTrue(src("FitText.kt").contains("if (calmHere()) modifier else modifier.basicMarquee("))
        assertTrue(src("FocusEffects.kt").contains("!calmHere()"))
        // Turning / wobbling media keep their pose: the shared media clock holds and runs on from there.
        assertTrue(src("PhysicalMedia.kt").contains("val held = calmHere() && !HomeIdle.calm"))
        assertTrue(src("PhysicalMedia.kt").contains("t.longValue = it - shiftNs"))
        assertTrue(src("LauncherScreen.kt").contains("MediaClock.hold(panelHold)"))
    }

    @Test fun sceneClockGoesOnFromItsFrame() {
        // A held scene's clock restarts from the time it stopped at (no jump back to the still frame).
        assertTrue(src("ThemeScene.kt").contains("val base = time.floatValue"))
    }
}
