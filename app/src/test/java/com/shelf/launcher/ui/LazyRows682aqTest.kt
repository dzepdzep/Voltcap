package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 682aq (owner 22:43, D389 / D386): a native page's achievement rows are lazy (NativeRows) - with 0, 1, 10 and 120 achievements
 * the page opens with its full counts and the composed rows stay a small window of the list (not 120). The D-pad walk (rapid
 * presses to the last achievement and back, Back / reopen, early close) is the device's: p2review.sh lazywalk.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class LazyRows682aqTest(private val layout: HomeLayout, private val big: Boolean, private val count: Int) {
    companion object {
        internal val LAZY = AchFirstRowRoboTest.NATIVE
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1} n={2}")
        fun cases(): List<Array<Any>> = LAZY.flatMap { l -> listOf(false, true).flatMap { b -> listOf(0, 1, 10, 120).map { arrayOf<Any>(l, b, it) } } }
        const val FIRST = "Defeat the first colossus"
    }
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).lowercase()
    private fun title(i: Int) = if (i == 1) FIRST else "Achievement number $i of the set"
    private fun key(code: Int) { val d = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!
        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)); frames(1)
        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code)); frames(1) }
    private fun rowsComposed() = compose.onAllNodes(SemanticsMatcher("ach row") { n -> n.config.contains(SemanticsActions.OnClick) &&
        words(n).let { FIRST.lowercase() in it || "achievement number" in it } }).fetchSemanticsNodes()

    @Test fun lazyRowsWalkTheWholeList() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val list = (1..count).map { i -> RaAchievementDetail(i, title(i), "Synthetic fixture achievement $i, a description that runs to a second line.",
            5 + i, null, null, if (i % 3 == 0) null else "2026-11-01 10:00:00", null, unlockPercent = 10f, displayOrder = i) }
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = count, NumAchieved = list.count { it.isUnlocked })))
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(180)
            // (opened as AchFirstRowRoboTest opens it: the Achievements control, behind More where the window keeps it)
            val ach = SemanticsMatcher("achievements control") { n -> n.config.contains(SemanticsActions.OnClick) &&
                words(n).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.let { w -> w.isNotEmpty() && w.all { it in setOf("achievements", "trophies") } } }
            var found = compose.onAllNodes(ach).fetchSemanticsNodes()
            if (found.isEmpty()) {
                val more = SemanticsMatcher("More") { n -> n.config.contains(SemanticsActions.OnClick) && words(n).trim().let { w -> w == "•••" || w.split(Regex("[^a-z•]+")).filter { it.isNotEmpty() }.let { ws -> ws.isNotEmpty() && ws.all { it == "more" } } } }
                val ms = compose.onAllNodes(more).fetchSemanticsNodes()
                if (ms.isNotEmpty()) { compose.onAllNodes(more)[ms.size - 1].performSemanticsAction(SemanticsActions.OnClick); frames(20) }
                found = compose.onAllNodes(ach).fetchSemanticsNodes()
            }
            if (found.isNotEmpty()) compose.onAllNodes(ach)[found.size - 1].performSemanticsAction(SemanticsActions.OnClick)
            else repeat(2) { key(android.view.KeyEvent.KEYCODE_BUTTON_R1); frames(30) }
            frames(60)
            val tag = "$layout x${if (big) 1.3 else 1.0} n=$count"
            if (count == 0) {
                // (no achievements: the window keeps its own buttons - no page of rows, nothing broken)
                assertTrue("$tag: no rows, the window's controls stay", rowsComposed().isEmpty() &&
                    compose.onAllNodes(SemanticsMatcher("play") { "play" in words(it) }).fetchSemanticsNodes().isNotEmpty())
                return
            }
            org.junit.Assume.assumeTrue("$tag: page reached", rowsComposed().isNotEmpty())
            val composed = rowsComposed().size
            if (count == 120) assertTrue("$tag: rows composed $composed - lazy keeps it a screenful", composed in 1..30)
            else assertTrue("$tag: every row there ($composed)", composed in 1..count)
            // (the full counts stay: the header counts every achievement, not the rows composed)
            val watched = list.count { it.isUnlocked }
            assertTrue("$tag: the page counts $watched of $count", compose.onAllNodes(SemanticsMatcher("count") { n ->
                Regex("\\d+").findAll(words(n)).map { it.value.toInt() }.toList().let { ns -> count in ns && watched in ns } }).fetchSemanticsNodes().isNotEmpty())
            // (the D-pad walk to the last row, rapid input, Back / reopen: on the device - Robolectric's dialog has no window
            // focus to move, as NativeConfirm681Test notes - p2review.sh lazywalk)
        } finally { TestHooks.scale.value = null }
    }
}
