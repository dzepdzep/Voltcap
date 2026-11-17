package com.shelf.launcher.ui

import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/**
 * P2 681h (owner 17 Nov: "show focused buttons and destructive-option confirmations"): on each batch-1 layout's own Options
 * page, a row that removes something asks first - one A arms it (the game's option is NOT run, the confirmation line shows),
 * moving off cancels, A A runs it once. At x1.0 and the KONKR's x1.3. (Behaviour only: Robolectric's native drawing gives the
 * window no focus, so the armed page's picture comes from the emulator / device walk, not from here.)
 * (TV room: its teletext needs the room's TV on screen - TvScreen.rect - not drawn here; its rows share NativeConfirm.)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class NativeConfirm681Test(private val layout: HomeLayout, private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = listOf(HomeLayout.CRATE, HomeLayout.CLASSIC, HomeLayout.HUB, HomeLayout.RESUME)
            .flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, it) } }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    private var hides = 0
    private var favs = 0

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    private fun key(code: Int) {
        val d = ShadowDialog.getLatestDialog()!!
        d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); frames(1); d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); frames(20)
    }
    private fun shows(t: String) = compose.onAllNodes(hasText(t, substring = true, ignoreCase = true), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    private fun words(n: androidx.compose.ui.semantics.SemanticsNode) = ((n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: "") + " " +
        (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")).lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.distinct()
    private val optionsControl = SemanticsMatcher("Options") { n -> n.config.contains(SemanticsActions.OnClick) && words(n) in listOf(listOf("options"), listOf("game", "options")) }
    private fun focusedWords() = compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes(atLeastOneRootRequired = false).map { words(it) }
    private fun onRemoving() = compose.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = false).fetchSemanticsNodes(atLeastOneRootRequired = false)
        .any { n -> "hide" in words(n) || n.children.any { "hide" in words(it) } } ||
        compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes(atLeastOneRootRequired = false).any { n ->
            compose.onAllNodes(hasText("Hide from library", substring = true), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { t -> n.boundsInRoot.contains(t.boundsInRoot.center) } }

    @Test fun aRemovingRowAsksFirst() {
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            compose.mainClock.autoAdvance = false
            val g = Game("Probe Quest", "SNES", 1, SourceType.ROM, romPath = "/r/p.sfc")
            compose.setContent {
                GameWindow(layout, g, LauncherUiState(), emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") { favs++ },
                        WinOption("Hide from library", "Takes it off your library - nothing is deleted", destructive = true) { hides++ }))
            }
            frames(150)
            if (compose.onAllNodes(optionsControl).fetchSemanticsNodes().isEmpty()) {
                val more = SemanticsMatcher("More") { n -> n.config.contains(SemanticsActions.OnClick) && (words(n) == listOf("more")) }
                val ms = compose.onAllNodes(more).fetchSemanticsNodes()
                if (ms.isNotEmpty()) { compose.onAllNodes(more)[ms.size - 1].performSemanticsAction(SemanticsActions.OnClick); frames(12) }
            }
            val os = compose.onAllNodes(optionsControl).fetchSemanticsNodes()
            assertTrue("$layout: no Options control", os.isNotEmpty())
            compose.onAllNodes(optionsControl)[os.size - 1].performSemanticsAction(SemanticsActions.OnClick)
            frames(40)
            assertTrue("$layout: the Options page shows the removing row", shows("Hide from library"))
            // (opened by touch: the first D-pad press lights a row - walk down until the removing row is the lit one)
            var n = 0
            while (!onRemoving() && n++ < 5) key(KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue("$layout: the removing row takes the focus; lit ${focusedWords()}", onRemoving())
            key(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("$layout: one A on a removing row must not run it", 0, hides)
            assertTrue("$layout: one A arms it - the confirmation shows", shows("again to confirm"))
            key(KeyEvent.KEYCODE_DPAD_UP)
            assertFalse("$layout: moving off cancels", shows("again to confirm"))
            n = 0
            while (!onRemoving() && n++ < 5) key(KeyEvent.KEYCODE_DPAD_DOWN)
            key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("$layout: A A runs it once", 1, hides)
            assertEquals("$layout: the ordinary row was never run", 0, favs)
        } finally {
            TestHooks.scale.value = null
            com.shelf.launcher.data.Features.winScenes.state.value = before
        }
    }
}
