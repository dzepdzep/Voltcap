package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.shelf.launcher.data.AchievementSeen
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.SourceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 681y (owner H12, "progress animations"; v0.6.74 item 7): the Achievements page's progress counted the new unlocks before the
 * new-unlocks stage had shown them - Power cells' battery read 30 % while three badges still waited dim in their slots. It now
 * counts each as it locks in (22 % -> 25 % -> 27 % -> 30 %). With motion Off / reduced motion the stage no longer sits through
 * its waits: every badge locks in at once and the progress is complete on open. Fails on the old code (30 % at once with
 * motion on). KONKR-sized screen (640 x 427 dp), 40 achievements, 12 unlocked of which 3 are new.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class AchProgressCount681yTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    @After fun restore() { Features.motion.value = Features.Motion.NORMAL; Features.achView.value = Features.AchView.POWER }

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()).trim()

    private fun shown(text: String) = compose.onAllNodes(SemanticsMatcher(text) { words(it) == text }, useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    private fun open(raId: Int, fresh: Set<Int>) {
        val ctx = RuntimeEnvironment.getApplication()
        Features.achView.value = Features.AchView.POWER
        if (fresh.isNotEmpty()) AchievementSeen.addFresh(ctx, AchievementSeen.keyRa(raId), fresh)
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/p$raId.iso", raGameId = raId)
        val list = (1..40).map { i ->
            RaAchievementDetail(i, "Achievement number $i of the set", "Synthetic fixture achievement $i.",
                5 + i, null, null, if (i <= 12) "2026-11-17 06:00:0${i % 10}" else null, null, unlockPercent = 10f, displayOrder = i)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
            AchievementListDialog(game = g, achievements = list, isLoading = false, onDismiss = {})
        } }
    }

    @Test fun powerCellsCountEachNewUnlockAsItLocksIn() {
        open(681_001, setOf(10, 11, 12))
        frames(12)
        assertTrue("the battery counted the 3 new unlocks before the stage showed them (expected 22 % = 9/40 on open)", shown("22%"))
        assertFalse("30 % shown while the new unlocks are still waiting", shown("30%"))
        frames(500) // 350 ms lead + 3 x (1.5 s + 140 ms) + the 520 ms fill
        assertTrue("after the stage the battery does not read 30 % (12/40)", shown("30%"))
        assertTrue("the stage's summary line is missing", shown("3 achievements unlocked · 12/40 · 30% complete"))
    }

    @Test fun nothingNewShowsTheProgressAsItIs() {
        open(681_002, emptySet())
        frames(4)
        assertTrue("with nothing new the battery should read 30 % straight away", shown("30%"))
    }

    @Test fun motionOffLocksEveryNewUnlockInAtOnce() {
        Features.motion.value = Features.Motion.OFF
        open(681_003, setOf(10, 11, 12))
        frames(60) // < 1 s: with motion on the first badge locks in only after 1.85 s (the test clock runs the 520 ms fill as-is)
        assertTrue("motion Off: the stage still waited before counting its badges", shown("30%"))
        assertTrue("motion Off: the summary line did not show at once", shown("3 achievements unlocked · 12/40 · 30% complete"))
    }

    @Test fun stagePlanFollowsTheMotionSpeed() {
        val off = unlockStagePlan(1f, 0f)
        assertFalse(off.animate)
        val normal = unlockStagePlan(1f, 1f)
        assertTrue(normal.animate)
        assertEquals(350L, normal.leadMs); assertEquals(1500, normal.badgeMs); assertEquals(510L, normal.impactMs)
        assertEquals(930L, normal.lockMs); assertEquals(140L, normal.gapMs)
        val subtle = unlockStagePlan(1f, 0.6f)
        assertEquals(1500, subtle.badgeMs) // the tween itself is scaled by Compose's MotionDurationScale
        assertEquals(306L, subtle.impactMs); assertEquals(84L, subtle.gapMs)
        assertEquals((1500 / 1.3f).toInt(), unlockStagePlan(2f, 1f).badgeMs)
    }

    @Test fun pendingAndShownCounts() {
        assertEquals(setOf(10, 11, 12), achStagePending(listOf(10, 11, 12), 0))
        assertEquals(setOf(12), achStagePending(listOf(10, 11, 12), 2))
        assertEquals(emptySet<Int>(), achStagePending(listOf(10, 11, 12), 3))
        assertEquals(setOf(10, 11, 12), achStagePending(listOf(10, 11, 12), -1))
        val list = (1..5).map { i -> RaAchievementDetail(i, "A$i", null, 5, null, null, if (i <= 3) "2026-11-17" else null, null) }
        assertEquals(3, achShownUnlocked(list, emptySet()))
        assertEquals(2, achShownUnlocked(list, setOf(3)))
        assertEquals(3, achShownUnlocked(list, setOf(4))) // a locked one in pending changes nothing
    }
}
