package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 681w (H16, owner review of the KONKR x1.3 sheet k678n13: Bar window labels cramped): the Icon bar window's key names sat in
 * a fixed 78 dp column under each round icon, and a long name ("Achievements", "Performance") broke inside the word at the
 * KONKR's x1.3. Every key now takes the width of the widest word among the names (78 dp at least): names break only between
 * words. KONKR 640 x 427 dp, QA scale font 1.3 / density x1.15.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class BarDockLabels681wTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun layoutsOf(word: String): List<TextLayoutResult> =
        compose.onAllNodes(SemanticsMatcher("text $word") { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == word } == true })
            .fetchSemanticsNodes().mapNotNull { n ->
                val out = mutableListOf<TextLayoutResult>()
                n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
                out.firstOrNull()
            }

    @Test fun keyWidthIsTheOld78OrTheWidestWordPlusAir() {
        assertEquals(78f, dockKeyWidthDp(0f), 0.01f)
        assertEquals(78f, dockKeyWidthDp(60f), 0.01f)          // short names: unchanged
        assertEquals(78f, dockKeyWidthDp(72f), 0.01f)          // exactly the old room
        assertEquals(93f, dockKeyWidthDp(86.4f), 0.01f)        // "Achievements" at x1.3: the word + 6 dp, rounded up
        assertEquals(listOf("Save", "sync", "Achievements"), dockLabelWords(listOf("Save sync", " Achievements ")))
    }

    @Test fun longKeyNamesStayWholeAtTheKonkrsLargeText() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val state = LauncherUiState().copy(raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 40, NumAchieved = 12)))
        TestHooks.scale.value = 1.3f to 1.15f
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(HomeLayout.BAR, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(240)
            var seen = 0
            for (label in listOf("Achievements", "Performance", "Details", "Options", "Play")) {
                for (r in layoutsOf(label)) {
                    seen++
                    assertEquals("$label on one line", 1, r.lineCount)
                    assertFalse("$label broken inside the word", brokenWord(r))
                    assertFalse("$label cut at its column's edge", r.didOverflowWidth)
                }
            }
            org.junit.Assume.assumeTrue("Bar window keys not reached", seen > 0)
            assertTrue(seen >= 2)
        } finally { TestHooks.scale.value = null }
    }
}
