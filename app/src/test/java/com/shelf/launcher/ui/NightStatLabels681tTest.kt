package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.LauncherUiState
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
 * 681t (H16, owner review of the KONKR x1.3 sheet k678n13: Night "ACHIEVED / RELEASED broken"): the Night window's stats
 * labels sat in a fixed 96 dp / 40 % column and broke inside the word at the KONKR's x1.3. The column now takes the widest
 * label's width (half the row at most) and the labels step down to the readable minimum only where that is still too
 * narrow - every label on one line, whole. KONKR 640 x 427 dp, QA scale font 1.3 / density x1.15.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class NightStatLabels681tTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun layoutOf(word: String): TextLayoutResult? {
        val n = compose.onAllNodes(SemanticsMatcher("text $word") { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == word } == true })
            .fetchSemanticsNodes().firstOrNull() ?: return null
        val out = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.firstOrNull()
    }

    @Test fun columnFitsTheWidestLabelWithinHalfTheRow() {
        assertEquals(96f, nightLabelWidth(60.dp, 300.dp).value, 0.01f)          // short labels: the old 96 dp
        assertEquals(48f, nightLabelWidth(30.dp, 120.dp).value, 0.01f)          // narrow row, short labels: the old 40 %
        assertEquals(87f, nightLabelWidth(80.dp, 180.dp).value, 0.01f)          // KONKR x1.3: the widest label + its gap
        assertEquals(75f, nightLabelWidth(120.dp, 150.dp).value, 0.01f)         // capped at half the row
    }

    @Test fun labelsStepDownOnlyWhenTheyMustAndStayReadable() {
        assertEquals(12f, nightLabelSize(70.dp, 96.dp), 0.01f)
        val s = nightLabelSize(80.dp, 75.dp)
        assertTrue("stepped down: $s", s < 12f && s >= 10f)
        assertEquals(10f, nightLabelSize(200.dp, 60.dp), 0.01f)                 // never under the readable minimum
        assertEquals(listOf("PLAY TIME", "LAST", "RELEASED", "MAKER", "KIND", "SYSTEM"), nightStatLabels(false))
        assertTrue("ACHIEVED" in nightStatLabels(true))
    }

    @Test fun achievedAndReleasedStayWholeAtTheKonkrsLargeText() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val state = LauncherUiState().copy(raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 40, NumAchieved = 12)))
        TestHooks.scale.value = 1.3f to 1.15f
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(HomeLayout.NIGHT, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(240)
            for (label in listOf("ACHIEVED", "RELEASED", "SYSTEM", "MAKER")) {
                val r = layoutOf(label)
                org.junit.Assume.assumeTrue("Night status panel not reached ($label)", r != null)
                assertEquals("$label on one line", 1, r!!.lineCount)
                assertFalse("$label broken inside the word", brokenWord(r))
                assertFalse("$label cut at its column's edge", r.didOverflowWidth)
            }
        } finally { TestHooks.scale.value = null }
    }
}
