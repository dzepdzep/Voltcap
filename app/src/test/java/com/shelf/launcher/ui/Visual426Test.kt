package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 426: the visual fixes from the Thor photos (Live tiles, Wii window, expanded pages, Save Vault sway). */
class Visual426Test {
    private fun src(name: String): String =
        File(listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name").first { File(it).exists() }).readText()

    @Test fun gridSwayNeverTurnsACartridgeEdgeOn() {
        var a = 0f
        while (a < 720f) { assertTrue("sway at $a", kotlin.math.abs(gridSway(a)) <= 30.001f); a += 0.5f }
    }

    @Test fun liveTilesBackdropFillsTheScreen() {
        val s = src("StyleHomes.kt")
        val line = s.lines().first { it.contains("HomeLayout.LIVE ->") && it.contains("LiveBackdrop") }
        assertTrue("no inset band of game art: $line", !line.contains("padding(top = topInset"))
        assertTrue("own slate instead of a game-art theme background", s.contains("LiveSlateBackdrop"))
    }

    @Test fun pagesOpenAsAReadablePage() {
        val s = src("GameWindow.kt")
        // (Popup motion work: every layout window still opens its pages as the readable shared page, except the scenes that
        // draw readable pages of their own - WinPageFlow.NATIVE_PAGE_SCENES, Storage boxes first.)
        assertTrue(s.contains("if (sharedPageFor(scene, page)) SceneExpandedPage("))
        assertTrue(src("WinPageFlow.kt").contains("page != WinPage.MAIN && scene != WinScene.CROSS_OPTIONS && !nativePages(scene)"))
        assertTrue(s.contains("if (details) ExpandedWinPage("))
    }

    @Test fun wiiWindowHasNoStripOfTinyKeys() {
        val s = src("WinScenesWii.kt")
        val body = s.substring(s.indexOf("internal fun WiiChannelPreview("), s.indexOf("private fun WiiMorePage("))
        assertTrue("secondary actions behind More", body.contains("\"More\""))
        assertTrue("no 11 sp key strip", !body.contains("fontSize = 11.sp"))
        assertTrue("B closes the More page first", body.contains("BackHandler(enabled = more)"))
    }

    @Test fun sceneWindowsNeverEndInAnEllipsis() {
        for (f in listOf("WinScenes.kt", "WinScenesConsole.kt", "WinScenesArcade.kt", "WinScenesMore.kt", "WinScenesHomes2.kt", "WinScenesStyled.kt", "WinScenesWii.kt", "WinSceneFamilies.kt"))
            assertTrue("$f uses TextOverflow.Ellipsis", !src(f).contains("TextOverflow.Ellipsis"))
    }
}
