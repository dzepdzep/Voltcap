package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 489 (staging only). */
class Batch489Test {
    private fun src(name: String): String = listOf(
        File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name")
    ).first { it.exists() }.readText()

    /** Every graphicsLayer block in [file] that turns its content (rotationX / rotationY) draws its text flat. */
    private fun turnedBlocksFlat(file: String): Int {
        val s = src(file); var n = 0
        Regex("graphicsLayer\\s*\\{").findAll(s).forEach { m ->
            var i = m.range.last + 1; var d = 1
            while (d > 0) { if (s[i] == '{') d++ else if (s[i] == '}') d--; i++ }
            val b = s.substring(m.range.first, i)
            if (Regex("\\brotation[XY]\\s*=").containsMatchIn(b)) {
                assertTrue("$file: a turned layer without flatTextFor3d", b.contains("flatTextFor3d()") || b.contains("Offscreen")); n++
            }
        }
        return n
    }

    @Test fun d285TurnedPanelsAndWindowsDrawTextFlat() {
        // Thor, v0.6.67 and staging: Panel dashboard + a game window closing -> ANR in Skia's text cache (D285).
        assertEquals(1, turnedBlocksFlat("StylePanels.kt"))
        assertEquals(4, turnedBlocksFlat("WinScenesConsole.kt"))
        assertTrue(turnedBlocksFlat("WinScenesHomes2.kt") >= 1)
        assertTrue(turnedBlocksFlat("WinSceneTwin.kt") >= 1)
        assertTrue(turnedBlocksFlat("WinScenesMore.kt") >= 0)
        // (Update 507: the card binder and cube windows moved to WinSceneFamilies.kt.)
        assertTrue(turnedBlocksFlat("WinSceneFamilies.kt") >= 2)
    }

    @Test fun k4SpinesAheadUseCase3dsOwnRule() {
        val c = src("Case3D.kt"); val r = src("RealMedia.kt")
        assertTrue(c.contains("internal fun case3dSpineRes(platform: String): Int?"))
        assertTrue(r.contains("val res = case3dSpineRes(g.platform) ?: continue"))
        assertTrue(r.contains("spineWindow.indices(focus, SPINE_AHEAD, games.size, loop)"))
        assertTrue(r.contains("SpineCache.titled(context, res, title, false, key)"))
    }

    @Test fun k1StacksSpacingReachesItsCarousel() {
        assertTrue(src("StyleCaseStacks.kt").contains("hostGap = Features.stacksSpacing.state.value / 100f"))
        assertTrue(src("Carousel.kt").contains("Carousel.spread = spreadNow * hostGap"))
        assertTrue(src("Carousel.kt").contains("var spread by androidx.compose.runtime.mutableFloatStateOf(1f)"))
    }

    @Test fun everyTurnedLayerDrawsItsTextFlat() {
        // D285 / update 177: text under a changing 3D turn fills Android 13's text cache until the app stops answering.
        // Left out on purpose: a fixed tilt (CategoryLooks' 18° floor) and the carousel's Canvas side band (no text).
        val dir = listOf(File("src/main/java/com/shelf/launcher/ui"), File("app/src/main/java/com/shelf/launcher/ui")).first { it.exists() }
        val missing = mutableListOf<String>()
        dir.listFiles()!!.filter { it.name.endsWith(".kt") && it.name != "ThemeShaders.kt" }.forEach { f ->
            val s = f.readText()
            Regex("graphicsLayer\\s*\\{").findAll(s).forEach { m ->
                var i = m.range.last + 1; var d = 1
                while (d > 0 && i < s.length) { if (s[i] == '{') d++ else if (s[i] == '}') d--; i++ }
                val b = s.substring(m.range.first, i)
                if (Regex("\\brotation[XY]\\s*=").containsMatchIn(b) && !b.contains("flatTextFor3d") && !b.contains("Offscreen") &&
                    !b.contains("rotationX = 18f") && !b.contains("rotationY = turnNow.value()")) missing += f.name + ":" + s.substring(0, m.range.first).count { it == '\n' }.plus(1)
            }
        }
        assertTrue("turned layers without flatTextFor3d: $missing", missing.isEmpty())
    }

    @Test fun d287QaBuildsBlockLaunchesUnlessAllowed() {
        val t = src("TestHooks.kt")
        val f = t.substring(t.indexOf("fun interceptLaunch("), t.indexOf("fun launchState()"))
        // normal installs (no hooks): untouched
        assertTrue(f.contains("if (!QaTrace.on) return false"))
        // with the hooks: blocked unless qa_allow_launch — the nolaunch switch / file can't unblock it
        assertTrue(f.contains("val block = launchBlocked()"))
        assertTrue(f.contains("fun launchBlocked(): Boolean = com.shelf.launcher.data.QaVariant.isolatedNow || !QaClip.allowLaunchFile()")) // (500: + the isolated QA app)
        assertTrue(src("QaClip.kt").contains("\"qa_allow_launch\""))
        // a forgotten hooks file stops counting after a day
        assertTrue(TestHooks.hooksFresh(1_000L, 1_000L + 60_000L))
        assertTrue(!TestHooks.hooksFresh(1_000L, 1_000L + TestHooks.QA_HOOKS_TTL_MS + 1))
        assertTrue(!TestHooks.hooksFresh(0L, 5L))
    }

    private fun main(rel: String): String = listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()

    /** 489c (D287): every way Voltcap starts a game or an app goes through the QA block, down to the last call before Android. */
    @Test fun d287EveryLaunchPathIsGuarded() {
        val vm = main("LauncherViewModel.kt")
        for ((fn, tag) in listOf("fun launch(game: Game)" to "vm.launch", "private fun launchNow(game: Game)" to "vm.launchNow", "fun launchApp(app: Game" to "vm.app")) {
            val body = vm.substring(vm.indexOf(fn)).take(1200)
            assertTrue("$fn guarded", body.contains("TestHooks.interceptLaunch(\"$tag\""))
        }
        val gl = main("data/GameLauncher.kt")
        assertTrue(gl.substring(gl.indexOf("fun launchAppOn(")).take(600).contains("interceptLaunch(\"launcher.app\""))
        assertTrue(gl.substring(gl.indexOf("    fun launch(")).take(900).contains("interceptLaunch(\"launcher.game\""))
        assertTrue(main("data/QuickResume.kt").let { it.substring(it.indexOf("fun resume(")).take(300) }.contains("interceptLaunch(\"resume\""))
        assertTrue(main("data/ScreenLayouts.kt").let { it.substring(it.indexOf("fun startApp(")).take(300) }.contains("interceptLaunch(\"screens.app\""))
        assertTrue(src("StyleHeroTiles.kt").let { it.substring(it.indexOf("private fun heroLaunch(")).take(300) }.contains("interceptLaunch(\"hero\""))
    }
}
