package com.shelf.launcher.ui

import com.shelf.launcher.data.AppDisplaySource
import com.shelf.launcher.data.AppDisplayTarget
import com.shelf.launcher.data.ScreenLayouts
import com.shelf.launcher.data.ScreenSetup
import com.shelf.launcher.data.ScreenSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 676j (v0.6.74): "Open on screen" in the app menus - one row, the existing stores, never a launch. */
class ScreenTarget676jTest {
    private val thor = screenNamesFor("AYN Thor", ScreenSetup.DUAL, hasSecond = true)

    @Test fun namesPerDevice() {
        assertEquals("Top screen", thor.first); assertEquals("Bottom screen", thor.second)
        assertEquals("Handheld", screenNamesFor("AYN Odin 2 Portal", ScreenSetup.TV, true).first)
        assertEquals("TV", screenNamesFor("AYN Odin 2 Portal", ScreenSetup.TV, true).second)
        assertEquals("Main screen", screenNamesFor("Retroid Pocket Nova", null, false).first)
        assertTrue(screenNamesFor("Retroid Pocket Nova", null, false).oneScreen)
    }

    @Test fun appsShowOwnOrInherited() {
        val own = appScreenNow(AppDisplayTarget.SECOND, AppDisplaySource.APP, thor)
        assertEquals(ScreenPick.SECOND, own.picked); assertEquals("Set for this app: Bottom screen", own.summary)
        val inherited = appScreenNow(AppDisplayTarget.WHERE_LAUNCHED, AppDisplaySource.CATEGORY, thor)
        assertEquals(ScreenPick.DEFAULT, inherited.picked); assertEquals("Follows the Emulators default: where you open it", inherited.summary)
        assertEquals(AppDisplayTarget.MAIN, appTargetFor(ScreenPick.FIRST)); assertNull(appTargetFor(ScreenPick.DEFAULT))
    }

    @Test fun androidGamesUseScreenLayoutRules() {
        assertEquals(ScreenLayouts.INFO, gameLayoutFor(ScreenPick.FIRST))
        assertEquals(ScreenLayouts.FLIPPED, gameLayoutFor(ScreenPick.SECOND))
        assertNull(gameLayoutFor(ScreenPick.DEFAULT))
        val own = gameScreenNow(ScreenSide.SECOND, false, ownRule = true, reason = "set for this game", layoutName = "x", names = thor)
        assertEquals(ScreenPick.SECOND, own.picked); assertEquals("Set for this game: Bottom screen", own.summary)
        val both = gameScreenNow(ScreenSide.MAIN, true, ownRule = false, reason = "set for NDS", layoutName = "Emulator uses both screens", names = thor)
        assertEquals("Follows set for NDS: both screens (Emulator uses both screens)", both.summary)
    }

    @Test fun drawerMenuHasTheRowAndNeverLaunches() {
        fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()
        val d = src("AppDrawer.kt")
        assertTrue(d.contains("screenRow?.invoke(app)"))
        assertTrue(d.contains("screenRow = screenRow"))
        assertTrue(src("LauncherScreen.kt").contains("screenRow = { app -> ScreenTargetRow(app, viewModel"))
        val t = src("ScreenTarget.kt")
        // choosing saves through the existing stores only, never starts anything
        assertTrue(!t.contains("launchApp(") && !t.contains("launchNow(") && !t.contains("startActivity("))
        assertTrue(t.contains("setScreenRule(RuleScope.GAME, app.stableKey, gameLayoutFor(pick))"))
        assertTrue(t.contains("setAppDisplay(it, appTargetFor(pick))"))
    }
}

/** 676j: a QA build logs the screen an app would open on, then blocks the launch as before (the block never moves earlier than the choice). */
class AppScreenLog676jTest {
    @Test fun blockAfterTheChoiceAndLogged() {
        val s = listOf("src/main/java/com/shelf/launcher/LauncherViewModel.kt", "app/src/main/java/com/shelf/launcher/LauncherViewModel.kt").map { File(it) }.first { it.exists() }.readText()
        val f = s.substring(s.indexOf("fun launchApp(app: Game"), s.indexOf("// ---- Live app list ----"))
        val log = f.indexOf("\"APP SCREEN \$pkg display=")
        val block = f.indexOf("TestHooks.interceptLaunch(\"vm.app\", pkg)")
        val start = f.indexOf("GameLauncher.launchAppOn(")
        assertTrue(log in 1 until block); assertTrue(block < start)
        assertEquals(1, f.split("TestHooks.interceptLaunch(\"vm.app\", pkg)").size - 1)
    }
}
