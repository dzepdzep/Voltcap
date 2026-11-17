package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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
 * D354 (681p, emulator e679r13 POCKET x1.3): beside the pulled-out disc the Pocket Quest window is 294 dp wide (its key row
 * 265.5 dp: keys 196 px at 1.725 px/dp) and "Play game" wrapped onto two lines - the uniform row's one-line keys missed two to
 * a view by a few px, so it took two-line keys. The keys now fit on one line, two whole keys between the row's arrows.
 * KONKR scale as the QA hook applies it (font 1.3, density x1.15).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class PqPlayKeyOneLine681pTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun lines(word: String): Int {
        val n = compose.onAllNodes(SemanticsMatcher("text $word") { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == word } == true })
            .fetchSemanticsNodes().first()
        val out = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.first().lineCount
    }

    private fun shownWidth(word: String): Float = compose.onAllNodes(SemanticsMatcher("text $word") {
        it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == word } == true }).fetchSemanticsNodes().first().boundsInRoot.width

    @Test fun playGameStaysOnOneLineBesideTheDisc() {
        val keys = listOf(
            WinAction("Play Game", Icons.Filled.PlayArrow, {}, focus = FocusRequester(), primary = true),
            WinAction("Status", Icons.Filled.Info, {}),
            WinAction("More", Icons.Filled.Layers, {})
        )
        var chosen = 0f
        TestHooks.scale.value = 1.3f to 1.15f
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme { WithTestScale {
                val p = pqPalette()
                // (the window's width on the emulator: 265.5 dp of keys + the old 14 dp padding each side)
                val w = 293.5.dp
                val size = pqKeyTextSize(keys, w - pqPanelPadX(true) * 2, 6.dp)
                chosen = size.value
                Box(Modifier.width(w)) {
                    Column(Modifier.padding(horizontal = pqPanelPadX(true))) { KeyRow(keys, 6.dp, twoLineOnlyWhenAlone = true) { PqSceneKey(it, p, under = true, size = size) } }
                }
            } } }
            frames(30)
            assertEquals("\"Play game\" wraps in the Pocket Quest window at x1.3", 1, lines("Play Game"))
            assertEquals(1, lines("Status"))
            assertTrue("key words readable: $chosen sp", chosen >= 10f)
            // two whole keys in view (the row scrolls to More), not one at a time
            assertTrue("Play shown", shownWidth("Play Game") > 0f)
            assertTrue("Status shown", shownWidth("Status") > 0f)
        } finally { TestHooks.scale.value = null }
    }

    @Test fun oneLineKeysWhileTwoShowTwoLineOnlyWhenAlone() {
        // one-line: two keys at a time (scrolls); two-line: all three - the default still takes two-line keys
        assertTrue(uniformRowPick(3, 130, 90, 314, 7, 18).let { !it.scrolls && it.keyW < 130 })
        val g = uniformRowPick(3, 130, 90, 314, 7, 18, onlyWhenAlone = true)
        assertTrue(g.scrolls && g.visible == 2 && g.keyW >= 130)
        // one key at a time on one line: two-line keys still win (674c)
        assertTrue(uniformRowPick(3, 300, 90, 314, 7, 18, onlyWhenAlone = true).keyW < 300)
    }

    @Test fun pocketQuestAsksForIt() {
        val s = listOf(java.io.File("src/main/java/com/shelf/launcher/ui/WinScenesStyled.kt"), java.io.File("app/src/main/java/com/shelf/launcher/ui/WinScenesStyled.kt"))
            .first { it.exists() }.readText().substringAfter("private fun PocketQuestPanel(").substringBefore("internal fun pqPanelPadX(")
        assertTrue(s.contains("KeyRow(sceneKeys(ctx), 6.dp, twoLineOnlyWhenAlone = true)"))
    }

    @Test fun twoFitCountsTheGutters() {
        assertTrue(pqTwoFit(3, 80.dp, 273.dp, 6.dp))                 // all three at once
        assertTrue(pqTwoFit(3, 117.dp, 273.dp, 6.dp))                // two between the arrows: 240 <= 241
        assertTrue(!pqTwoFit(3, 128.dp, 273.dp, 6.dp))               // the e679r13 width at 12 sp: one at a time
        assertEquals(12f, PQ_KEY_SIZES.first()); assertEquals(10f, PQ_KEY_SIZES.last())
    }

    @Test fun normalTextKeepsTheOldPadding() {
        assertEquals(14.dp, pqPanelPadX(false))
        assertEquals(10.dp, pqPanelPadX(true))
    }
}
