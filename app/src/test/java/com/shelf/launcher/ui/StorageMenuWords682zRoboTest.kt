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
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * D364 (682z, RP Classic x1.3, run pr14): Storage boxes' Info sheet drew "ACHIEVEMENT / S" - a menu cell narrower than the
 * word at its smallest readable size. Every text of the Info sheet, on the RP Classic's screen (620 x 516 dp) and the
 * KONKR's (640 x 427 dp), at x1.3: no line ends inside a word (brokenWord, as QaClip checks on the device).
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w620dp-h516dp-land-xhdpi")
open class StorageMenuWords682zRoboTest {
    protected open val qualifiers = "RP Classic 620 x 516 dp"
    private val compose = createComposeRule()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    @Test fun infoSheetWordsStayWhole() {
        val g = Game("Tetris (World)", "GB", 1, SourceType.ROM, romPath = "/r/t.gb")
        TestHooks.scale.value = 1.3f to 1.15f
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(HomeLayout.STORAGE, g, LauncherUiState(), emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(180)
            val broken = mutableListOf<String>()
            var seen = 0
            compose.onAllNodes(SemanticsMatcher("text") { it.config.getOrNull(SemanticsProperties.Text) != null }, useUnmergedTree = true)
                .fetchSemanticsNodes().forEach { n ->
                    val rs = mutableListOf<TextLayoutResult>()
                    n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(rs)
                    rs.forEach { r -> seen++; if (brokenWord(r)) broken += r.layoutInput.text.text }
                }
            assertTrue("$qualifiers: no text laid out", seen > 0)
            assertFalse("$qualifiers: words broken inside: $broken", broken.isNotEmpty())
        } finally { TestHooks.scale.value = null }
    }
}

/** D364 on the KONKR's screen (640 x 427 dp). */
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class StorageMenuWordsKonkr682zRoboTest : StorageMenuWords682zRoboTest() { override val qualifiers = "KONKR 640 x 427 dp" }
