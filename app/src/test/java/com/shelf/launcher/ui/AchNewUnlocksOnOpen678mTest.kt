package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.shelf.launcher.data.AchievementSeen
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 678m (owner 17 Nov, H12: "Recently Unlocked is skipped - the page auto-scrolls to the list"): the per-game Achievements page
 * opened its focus on the first achievement row (initialFocus), and focusing it scrolled the page body down so that row was in
 * view - past the new-unlocks stage at the top, which is the part that had just been earned. With new unlocks waiting, the page
 * now opens on that stage (focusable, so a controller has the focus on open and ▼ goes on to the controls and the list); without
 * any, it still opens on the first achievement. KONKR-sized screen (640 × 427 dp) at ×1.0, 40 achievements, 3 new unlocks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class AchNewUnlocksOnOpen678mTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).lowercase()

    private fun open(raId: Int, fresh: Set<Int>) {
        val ctx = RuntimeEnvironment.getApplication()
        if (fresh.isNotEmpty()) AchievementSeen.addFresh(ctx, AchievementSeen.keyRa(raId), fresh)
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s$raId.iso", raGameId = raId)
        val list = (1..40).map { i ->
            RaAchievementDetail(i, "Achievement number $i of the set", "Synthetic fixture achievement $i, a description that runs to a second line.",
                5 + i, null, null, if (i <= 12) "2026-11-17 06:00:0${i % 10}" else null, null, unlockPercent = 10f, displayOrder = i)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
            AchievementListDialog(game = g, achievements = list, isLoading = false, onDismiss = {})
        } }
        // past initialFocus's last try (80 + 120 + ... + 900 ms) and the scroll it starts
        frames(220)
    }

    private fun nodes(m: SemanticsMatcher, unmerged: Boolean = true) = compose.onAllNodes(m, useUnmergedTree = unmerged).fetchSemanticsNodes()

    @Test fun pageOpensWithTheNewUnlocksInView() {
        open(678_001, setOf(10, 11, 12))
        val label = nodes(SemanticsMatcher("NEW UNLOCKS") { n -> words(n).trim() == "new unlocks" })
        assertTrue("the new-unlocks stage was not drawn", label.isNotEmpty())
        val l = label.first()
        val full = l.size.height.toFloat(); val shown = l.boundsInRoot.height
        assertTrue("NEW UNLOCKS label shown ${shown}px of ${full}px (at ${l.boundsInRoot}) - the page scrolled past the new unlocks on open",
            full > 0f && shown >= full - 1.5f)
        // the controller still has a place to start from (one press = one step): the stage itself has the focus
        val focused = nodes(SemanticsMatcher("focused") { it.config.getOrNull(SemanticsProperties.Focused) == true }, unmerged = false)
        assertTrue("nothing has the focus after opening", focused.isNotEmpty())
        assertTrue("the focus is not on the new-unlocks stage: ${focused.map { words(it).trim().take(40) }}",
            focused.any { "new unlocks" in words(it) })
    }

    @Test fun withoutNewUnlocksThePageStillOpensOnTheFirstAchievement() {
        open(678_002, emptySet())
        assertTrue("a new-unlocks stage was drawn with nothing new", nodes(SemanticsMatcher("NEW UNLOCKS") { n -> "new unlock" in words(n) }).isEmpty())
        val focused = nodes(SemanticsMatcher("focused") { it.config.getOrNull(SemanticsProperties.Focused) == true }, unmerged = false)
        assertTrue("the first achievement does not have the focus: ${focused.map { words(it).trim().take(40) }}",
            focused.any { "achievement number 1 of the set" in words(it) })
    }
}
