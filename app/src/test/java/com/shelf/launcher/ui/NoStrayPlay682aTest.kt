package com.shelf.launcher.ui

import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createComposeRule
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/**
 * P2 682a (owner 18 Nov, release-blocking): "page navigation, B, L1 / R1 and rapid inputs NEVER trigger Play unintentionally."
 * Every batch-1 layout with its own pages (TV room on its set's screen too), x1.0 and x1.3, a game with achievements and
 * options so every page exists. Only the controller, as a person mashes it:
 *  1. L1 / R1 storms - 12 each way a frame apart, then 10 alternations;
 *  2. every page: the D-pad hammered past both ends of its list (15 down, 15 up, a frame apart), then B back to Info;
 *  3. A straight after each L1 / R1 page turn (the focus lands on Play on Details) - the page-turn guard;
 *  4. B on every page, B B quickly;
 * and none of it starts the game or closes the window by itself. Last, Play still answers one deliberate A.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class NoStrayPlay682aTest(private val layout: HomeLayout, private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = listOf(HomeLayout.CRATE, HomeLayout.CLASSIC, HomeLayout.HUB, HomeLayout.RESUME, HomeLayout.TV)
            .flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, it) } }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    private var plays = 0
    private var closes = 0
    private var optionRuns = 0

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    private fun key(code: Int, after: Int = 1) {
        val d = ShadowDialog.getLatestDialog()
        if (d != null && d.isShowing) {
            d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); frames(1); d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        frames(after)
    }
    private fun ok(step: String) {
        assertEquals("$layout x${if (big) "1.3" else "1.0"}: $step started the game", 0, plays)
        assertEquals("$layout x${if (big) "1.3" else "1.0"}: $step closed the window", 0, closes)
    }

    @Test fun controllerNavigationNeverStartsTheGame() {
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        val oldRect = TvScreen.rect; val oldId = TvScreen.displayId
        if (layout == HomeLayout.TV) { TvScreen.rect = Rect(97f, 117f, 314f, 265f); TvScreen.displayId = -1 }
        try {
            val g = Game("Probe Quest", "PS2", 1, SourceType.ROM, romPath = "/r/probe.iso")
            val list = (1..12).map { i ->
                RaAchievementDetail(i, "Probe achievement $i", "A synthetic description long enough to wrap to a second line, number $i.",
                    5 + i, null, null, if (i % 3 == 0) "2026-11-01 10:00:00" else null, null, unlockPercent = 10f + i, displayOrder = i)
            }
            val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
                raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 12, NumAchieved = list.count { it.isUnlocked })))
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = { closes++ }, onPlay = { plays++ }, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") { optionRuns++ }, WinOption("Game settings") { optionRuns++ },
                        WinOption("Hide from library", "Takes it off your library", destructive = true) { optionRuns++ }))
            } }
            frames(160) // (past the window's own opening guard)

            // 1. L1 / R1 storms.
            repeat(12) { key(KeyEvent.KEYCODE_BUTTON_R1) }; ok("an R1 storm")
            repeat(12) { key(KeyEvent.KEYCODE_BUTTON_L1) }; ok("an L1 storm")
            repeat(10) { key(KeyEvent.KEYCODE_BUTTON_R1); key(KeyEvent.KEYCODE_BUTTON_L1) }; ok("alternating L1 / R1")
            // (the storms wrap round to Info; one more R1, then B goes back to Info - never out, never Play)
            frames(40); key(KeyEvent.KEYCODE_BUTTON_R1, 40); key(KeyEvent.KEYCODE_BACK, 40)
            ok("B after the storms")

            // 2. + 3. + 4. every page: A right after the turn, the D-pad past both ends, B.
            // (three pages after Info: Details, Achievements, Options - a fourth R1 wraps back to Info, where B closes)
            repeat(3) { p ->
                repeat(p + 1) { key(KeyEvent.KEYCODE_BUTTON_R1) }
                // A a moment after the turn, at three points inside the guard (the focus has reached Play on Details by then;
                // the clock is checked so the press really falls inside it)
                for (wait in listOf(0, 2, 4)) {
                    frames(wait)
                    val since = android.os.SystemClock.uptimeMillis() - NativePageTurn.at
                    assertTrue("$layout: the press meant for inside the guard came $since ms after the turn", since < PAGE_TURN_PLAY_GUARD_MS)
                    key(KeyEvent.KEYCODE_DPAD_CENTER, 1)
                    ok("A $since ms after turning to page ${p + 1}")
                    key(KeyEvent.KEYCODE_BUTTON_L1, 1); key(KeyEvent.KEYCODE_BUTTON_R1, 0) // (turn away and back: a fresh turn)
                }
                frames(30)
                repeat(15) { key(KeyEvent.KEYCODE_DPAD_DOWN) }; repeat(15) { key(KeyEvent.KEYCODE_DPAD_UP) }
                ok("the D-pad past both ends of page ${p + 1}")
                frames(30); key(KeyEvent.KEYCODE_BACK, 40)
                ok("B on page ${p + 1}")
            }

            // Play still answers one deliberate press: from Info, settled, with the focus where the window puts it.
            frames(60)
            val playsBefore = plays
            // (find Play: the window's first key - Left walks to it from anywhere on the key row)
            repeat(6) { key(KeyEvent.KEYCODE_DPAD_LEFT, 3) }
            frames(40); key(KeyEvent.KEYCODE_DPAD_CENTER, 10)
            assertEquals("$layout: after all that, one deliberate A on Play starts the game once", playsBefore + 1, plays)
        } finally {
            TestHooks.scale.value = null; TvScreen.rect = oldRect; TvScreen.displayId = oldId
            com.shelf.launcher.data.Features.winScenes.state.value = before
        }
    }
}
