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
 * P2 682a / 682j (owner 18 Nov, release-blocking): "page navigation, B, L1 / R1 and rapid inputs NEVER trigger Play
 * unintentionally" - and (682j) "prefer correct focus ownership over a fixed cooldown; test intentional A presses immediately
 * after page transitions". The page you land on owns the focus (WinPageFlow.pageFocusTarget): Details' own first item, never
 * Play - no input is held back, so a deliberate A acts at once (Info's A on Play starts the game on the very next frame).
 * Every batch-1 layout with its own pages (TV room on its set's screen too), x1.0 and x1.3, a game with achievements and
 * options so every page exists. Only the controller, as a person mashes it:
 *  1. L1 / R1 storms - 12 each way a frame apart, then 10 alternations;
 *  2. every page: the D-pad hammered past both ends of its list (15 down, 15 up, a frame apart), then B back to Info;
 *  3. A straight after each L1 / R1 page turn - on the very frame, and 1 and 2 frames on (682j: kept through the hand-over and
 *     delivered to the new page's own control; Details' story, never Play);
 *  4. B on every page, B B quickly;
 * and none of it starts the game or closes the window by itself. Last, intentional presses right after a turn act at once:
 * L1 + A runs Options' first row once, B + A opens Options again (B lands on its key), Left x6 + A starts the game once.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class NoStrayPlay682jTest(private val layout: HomeLayout, private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        // (682j: every layout whose window draws its own pages, and the TV room's set)
        fun cases(): List<Array<Any>> = (HomeLayout.values().filter { nativePages(winSceneFor(it)) } + HomeLayout.TV).distinct()
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
    private fun focusedText(): String = runCatching {
        compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes(false).joinToString {
            (it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null } ?: "").toString() }
    }.getOrDefault("")
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
            val playText = focusedText() // (the window opens on Play)

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
                // A straight after the turn - on the very frame, and 1 and 2 frames on: it acts on what the new page lit (Details' own
                // first item, the list's first row), never on Play
                for (wait in listOf(0, 1, 2)) {
                    frames(wait)
                    key(KeyEvent.KEYCODE_DPAD_CENTER, 1)
                    ok("A $wait frames after turning to page ${p + 1}")
                    key(KeyEvent.KEYCODE_BUTTON_L1, 1); key(KeyEvent.KEYCODE_BUTTON_R1, 0) // (turn away and back: a fresh turn)
                }
                frames(30)
                repeat(15) { key(KeyEvent.KEYCODE_DPAD_DOWN) }; repeat(15) { key(KeyEvent.KEYCODE_DPAD_UP) }
                ok("the D-pad past both ends of page ${p + 1}")
                frames(30); key(KeyEvent.KEYCODE_BACK, 40)
                ok("B on page ${p + 1}")
            }

            // Intentional presses straight after a transition are answered at once - no cooldown, none lost (682j hand-over):
            // L1 from Info (wraps to Options) and A on the very next frame runs Options' first row ("Add to favorites"), once.
            frames(40)
            val runsBefore = optionRuns
            key(KeyEvent.KEYCODE_BUTTON_L1, 0)             // Info -> Options
            key(KeyEvent.KEYCODE_DPAD_CENTER, 12)          // A, kept through the hand-over, delivered to the first row
            assertEquals("$layout: A straight after L1 to Options runs its first row, once", runsBefore + 1, optionRuns)
            ok("A straight after L1 to Options")
            key(KeyEvent.KEYCODE_BACK, 0)                  // B straight back to Info (it lands on Options' key or Details', not Play)
            key(KeyEvent.KEYCODE_DPAD_CENTER, 12)          // A at once: that page again, never the game
            ok("A straight after B back to Info")
            assertEquals("$layout: A straight after B back to Info ran an option", runsBefore + 1, optionRuns)
            key(KeyEvent.KEYCODE_BACK, 0)
            // ...and a deliberate walk to Play and A, pressed as fast as the frames allow, starts the game once.
            // (Each layout lays its keys out its own way: the walk goes left, up, right, down until Play - the control lit when the
            // window opened - has the D-pad; every step one frame apart, and the A comes on the very next frame.)
            run walk@{
                for (dir in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN))
                    repeat(6) { if (focusedText() == playText) return@walk; key(dir, 0) }
            }
            assertEquals("$layout: the D-pad reaches Play", playText, focusedText())
            key(KeyEvent.KEYCODE_DPAD_CENTER, 12)
            assertEquals("$layout: the D-pad to Play then A, at once, starts the game once", 1, plays)
            assertEquals("$layout: nothing closed the window", 0, closes)
        } finally {
            TestHooks.scale.value = null; TvScreen.rect = oldRect; TvScreen.displayId = oldId
            com.shelf.launcher.data.Features.winScenes.state.value = before
        }
    }
}
