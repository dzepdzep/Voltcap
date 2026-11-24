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
 * 682ar (owner 24 Nov, safety): Record crate / Dock - the page's first focus, the order key, is printed in the lazy list's first
 * row. Broken (682aq lazy rows): a page scrolled down or re-sorted, turned away and straight back with L1 / R1, had no order key
 * built - the window's request for it fell back to Play and an A pressed during the turn started the game. Now the list goes
 * back to its head (NativeRows head), the key is lit, and a confirm press kept through a turn is never handed to Play
 * (PageHandoff.release). Synthetic game, 40 achievements, x1.0 / x1.3.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class ListHeadTurn682arTest(private val layout: HomeLayout, private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = listOf(HomeLayout.CRATE, HomeLayout.BAR)
            .filter { nativePages(winSceneFor(it)) }.flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, it) } }
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

    /** The page's order key is lit: the crate's stamp ("ORDER · ..."), the Dock's key ("⇅"). */
    private fun orderLit() = focusedText().let { if (layout == HomeLayout.BAR) it.contains("⇅") else it.uppercase().contains("ORDER") }

    @Test fun aKeptThroughATurnNeverStartsTheGameAndTheOrderKeyIsLit() {
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            val g = Game("Probe Quest", "PS2", 1, SourceType.ROM, romPath = "/r/probe.iso")
            val list = (1..40).map { i ->
                RaAchievementDetail(i, "Probe achievement $i", "A synthetic description, number $i.", 5 + i, null, null,
                    if (i % 3 == 0) "2026-11-01 10:00:00" else null, null, unlockPercent = 10f + i, displayOrder = i)
            }
            val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
                raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 40, NumAchieved = list.count { it.isUnlocked })))
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = { closes++ }, onPlay = { plays++ }, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") { optionRuns++ }))
            } }
            frames(160)
            val tag = "$layout x${if (big) "1.3" else "1.0"}"
            // (deep into the list, still on it: the crate's tracks one a press; the Dock's icon lines hold several a line)
            val downs = if (layout == HomeLayout.BAR) 4 else 25
            // to the achievements page (Info -> Details -> Achievements); the order key is its first stop
            key(KeyEvent.KEYCODE_BUTTON_R1, 20); key(KeyEvent.KEYCODE_BUTTON_R1, 40)
            org.junit.Assume.assumeTrue("$tag: achievements page reached (focus '${focusedText()}')", orderLit())
            // 1. scrolled deep, then L1 / R1 straight back and A on the next frame
            repeat(downs) { key(KeyEvent.KEYCODE_DPAD_DOWN, 2) }
            frames(20)
            assertTrue("$tag: the D-pad left the order key (on '${focusedText()}')", !orderLit())
            // (turned away and back - a fresh turn, as NoStrayPlay682jTest turns: L1, a frame, R1, A on the next frame)
            key(KeyEvent.KEYCODE_BUTTON_L1, 1); key(KeyEvent.KEYCODE_BUTTON_R1, 0); key(KeyEvent.KEYCODE_DPAD_CENTER, 1)
            frames(30)
            ok("A kept through L1 / R1 on a scrolled list")
            assertTrue("$tag: back on the page, its order key is lit (on '${focusedText()}')", orderLit())
            // 2. re-sorted (A on the key), then the same quick turn and A
            key(KeyEvent.KEYCODE_DPAD_CENTER, 20)
            ok("A on the order key")
            repeat(downs) { key(KeyEvent.KEYCODE_DPAD_DOWN, 2) }; frames(10)
            if (focusedText().contains("Probe achievement")) key(KeyEvent.KEYCODE_DPAD_CENTER, 10) // (opens the row it is on)
            key(KeyEvent.KEYCODE_BUTTON_L1, 1); key(KeyEvent.KEYCODE_BUTTON_R1, 0); key(KeyEvent.KEYCODE_DPAD_CENTER, 0)
            key(KeyEvent.KEYCODE_BUTTON_L1, 1); key(KeyEvent.KEYCODE_BUTTON_R1, 1); key(KeyEvent.KEYCODE_DPAD_CENTER, 2)
            frames(30)
            ok("A after re-sort, opened row and two quick turns")
            assertTrue("$tag: the order key is lit after the turns (on '${focusedText()}')", orderLit())
            // 3. a deliberate press on Play still starts the game once: B to Info (its opener), Left to Play, A
            key(KeyEvent.KEYCODE_BACK, 40)
            // (to Play by the D-pad, whatever the layout puts between: Left, then Up, until Play is lit)
            var steps = 0
            while (!focusedText().uppercase().contains("PLAY") && steps < 16) {
                key(if (steps < 8) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_UP, 2); steps++
            }
            org.junit.Assume.assumeTrue("$tag: Play reached by the D-pad (on '${focusedText()}')", focusedText().uppercase().contains("PLAY"))
            frames(10)
            key(KeyEvent.KEYCODE_DPAD_CENTER, 10)
            assertEquals("$tag: a deliberate A on Play starts the game once", 1, plays)
        } finally {
            TestHooks.scale.value = null
            com.shelf.launcher.data.Features.winScenes.state.value = before
        }
    }

    /** Keys straight after each other, no frame between (a burst the way `input keyevent A B C` sends them). */
    private fun burst(vararg codes: Int) {
        val d = ShadowDialog.getLatestDialog() ?: return
        codes.forEach { c -> d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, c)); d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, c)) }
    }

    /**
     * 682ar (KONKR storm kst10): L1 and R1 in the same frame end on the page they started from; the hand-over's holder took the
     * focus, but its release was keyed by the page - which "did not change" - so every press after it was kept for ever: the
     * D-pad did nothing and the A meant for the order key was lost. Now the release follows every turn.
     */
    @Test fun anL1R1BurstInOneFrameLeavesTheDpadWorking() {
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            val g = Game("Probe Quest", "PS2", 1, SourceType.ROM, romPath = "/r/probe.iso")
            val list = (1..40).map { i -> RaAchievementDetail(i, "Probe achievement $i", "A synthetic description, number $i.", 5 + i, null, null,
                if (i % 3 == 0) "2026-11-01 10:00:00" else null, null, unlockPercent = 10f + i, displayOrder = i) }
            val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
                raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 40, NumAchieved = list.count { it.isUnlocked })))
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = { closes++ }, onPlay = { plays++ }, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") { optionRuns++ }))
            } }
            frames(160)
            val tag = "$layout x${if (big) "1.3" else "1.0"}"
            key(KeyEvent.KEYCODE_BUTTON_R1, 20); key(KeyEvent.KEYCODE_BUTTON_R1, 40)
            org.junit.Assume.assumeTrue("$tag: achievements page reached", orderLit())
            val order0 = focusedText()
            burst(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_DPAD_CENTER)
            frames(30)
            ok("an L1 R1 A burst")
            assertTrue("$tag: the order key is lit after the burst (on '${focusedText()}')", orderLit())
            // (the crate's stamp names its order: the A kept through the burst stepped it; the Dock's key shows only ⇅)
            if (layout == HomeLayout.CRATE) assertTrue("$tag: the A kept through the burst stepped the order (was $order0, now ${focusedText()})", focusedText() != order0)
            repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN, 4) }
            frames(10)
            assertTrue("$tag: the D-pad works after the burst (still on '${focusedText()}')", !orderLit() && focusedText().isNotBlank() && focusedText() != "[]")
        } finally {
            TestHooks.scale.value = null
            com.shelf.launcher.data.Features.winScenes.state.value = before
        }
    }
}
