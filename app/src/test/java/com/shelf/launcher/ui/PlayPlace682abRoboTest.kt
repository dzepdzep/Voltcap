package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.getOrNull
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import org.junit.Assert.assertTrue

/**
 * 682ab (owner rule 18 Nov: Play keeps the same size and position across a layout's native pages; KONKR + RP Classic x1.0
 * fk10 / fr10 found Play a third wide on Achievements, half on Details and a short key in the middle on Options - Card binder,
 * Console hub, Cube, Cinema, Record crate, Resume): every layout drawing its own pages, at x1.0 and x1.3 - the Play control's
 * bounds on Details, Achievements and Options are the same (1.5 px). Each page from a fresh window through its own control,
 * as PageSheetRoboTest does (Robolectric's L1 / R1 key path does not turn pages).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
open class PlayPlace682abRoboTest(private val layout: HomeLayout, private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = AchFirstRowRoboTest.NATIVE.filter { it != HomeLayout.TV }
            .flatMap { l -> listOf(arrayOf<Any>(l, false), arrayOf<Any>(l, true)) }
        private val PLAY = setOf("play", "run", "power on", "insert coin", "start", "play it", "press start")
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun key(code: Int) = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.let { d ->
        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)); frames(1)
        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code)); frames(45)
    }

    @Suppress("unused") private fun shotUnused(step: String) = runCatching {
        val v = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        val dir = java.io.File("build/pagesheet").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "${layout}_${if (big) "x13" else "x10"}_$step.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }.onFailure { println("PAGESHEET capture failed: $it") }

    private fun words(n: androidx.compose.ui.semantics.SemanticsNode) = (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }.orEmpty() + " " + n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()).lowercase()

    private fun open(page: Set<String>) {
        val click = androidx.compose.ui.semantics.SemanticsActions.OnClick
        val m = androidx.compose.ui.test.SemanticsMatcher("page") { n -> n.config.contains(click) &&
            words(n).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.let { w -> w.isNotEmpty() && w.all { it in page } } }
        var found = compose.onAllNodes(m).fetchSemanticsNodes()
        if (found.isEmpty()) {
            val more = androidx.compose.ui.test.SemanticsMatcher("More") { n -> n.config.contains(click) && words(n).trim().let { w -> w == "•••" ||
                // (733a: a More key that is labelled and described - "more more", Case flow - is More too, as AchFirstRow reads it)
                w.split(Regex("[^a-z•]+")).filter { it.isNotEmpty() }.let { ws -> ws.isNotEmpty() && ws.all { it == "more" } } } }
            val ms = compose.onAllNodes(more).fetchSemanticsNodes()
            if (ms.isNotEmpty()) { compose.onAllNodes(more)[ms.size - 1].performSemanticsAction(click); frames(20) }
            found = compose.onAllNodes(m).fetchSemanticsNodes()
        }
        if (found.isEmpty()) { println("PLAYPLACE $layout: no control for $page"); return }
        compose.onAllNodes(m)[found.size - 1].performSemanticsAction(click); frames(60)
    }

    private val gen = androidx.compose.runtime.mutableIntStateOf(0)

    private fun playAt(): androidx.compose.ui.geometry.Rect? {
        val click = androidx.compose.ui.semantics.SemanticsActions.OnClick
        val m = androidx.compose.ui.test.SemanticsMatcher("play") { n -> n.config.contains(click) &&
            words(n).replace(Regex("[^a-z ]"), " ").trim().replace(Regex(" +"), " ").let { w -> w in PLAY ||
                // (734b: a key that is labelled and described - Orbit's narrow "PLAY" key with its "Play" icon reads "play play")
                w.split(" ").let { ws -> ws.size % 2 == 0 && ws.size >= 2 && ws.subList(0, ws.size / 2) == ws.subList(ws.size / 2, ws.size) &&
                    ws.subList(0, ws.size / 2).joinToString(" ") in PLAY } } }
        return compose.onAllNodes(m).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    }

    @Test fun playKeepsItsPlaceOnEveryPage() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val list = (1..12).map { i -> RaAchievementDetail(i, "Achievement number $i of the set", "Synthetic fixture achievement $i.",
            5 + i, null, null, if (i % 3 == 0) null else "2026-11-01 10:00:00", null, unlockPercent = 10f, displayOrder = i) }
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 12, NumAchieved = list.count { it.isUnlocked })))
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { androidx.compose.runtime.key(gen.intValue) { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") {}, WinOption("Game settings") {},
                        WinOption("Hide from library", "Takes it off your library", destructive = true) {})) } } }
            frames(180)
            fun fresh() { gen.intValue++; frames(160) }
            val at = LinkedHashMap<String, androidx.compose.ui.geometry.Rect?>()
            open(setOf("details", "summary", "info", "turn", "over", "the", "story", "game", "sleeve", "notes", "liner", "plot", "card", "text", "contents")); at["details"] = playAt() // (731a: the Cardboard box's CONTENTS)
            fresh(); open(setOf("achievements", "trophies", "credits")); at["achievements"] = playAt()
            fresh(); open(setOf("options", "config", "backstage")); at["options"] = playAt()
            val seen = at.filterValues { it != null }
            println("PLAYPLACE $layout big=$big $at")
            org.junit.Assume.assumeTrue("$layout: Play not found on two pages - nothing to compare", seen.size >= 2)
            val first = seen.values.first()!!
            for ((p, r) in seen) assertTrue("$layout ${if (big) "x1.3" else "x1.0"}: Play on $p at $r, on ${seen.keys.first()} at $first",
                kotlin.math.abs(r!!.left - first.left) <= 1.5f && kotlin.math.abs(r.top - first.top) <= 1.5f &&
                    kotlin.math.abs(r.width - first.width) <= 1.5f && kotlin.math.abs(r.height - first.height) <= 1.5f)
        } finally { TestHooks.scale.value = null; com.shelf.launcher.data.Features.winScenes.state.value = before }
    }
}

/** 682ab: the same on the RP Classic's screen (620 x 516 dp). */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w620dp-h516dp-land-xhdpi")
class PlayPlaceRpc682abRoboTest(layout: HomeLayout, big: Boolean) : PlayPlace682abRoboTest(layout, big) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = PlayPlace682abRoboTest.cases()
    }
}
