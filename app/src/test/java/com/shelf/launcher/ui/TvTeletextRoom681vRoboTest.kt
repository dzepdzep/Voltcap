package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
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
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 681v (P2 batch 1, emulator review 44194071430, TV room): the window on the room's television showed its teletext pages with
 * no body - P200 drew its bar and ORDER key but not one achievement row (every row 0 px on screen, x1.0 and x1.3), P100 not one
 * line of the story, and at x1.3 "VOLTCAP TXT" stood one letter a line. D-pad Down then went straight to the buttons. On the
 * set's screen (the emulator's rect: 217 x 148 dp on a 640 x 427 dp screen, the buttons under the set) the masthead is one line, so the TROPHIES page
 * shows two whole achievements at x1.0 and one at x1.3. Fails on the old masthead with the counts in the message.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class TvTeletextRoom681vRoboTest(private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "big={0}")
        fun cases(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
        fun minWhole(big: Boolean) = if (big) 1 else 2
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).lowercase()

    @Test fun tightMastheadRule() {
        assertTrue(ttxTight(150f)); assertTrue(ttxTight(199f)); assertFalse(ttxTight(200f)); assertFalse(ttxTight(320f))
        assertEquals(listOf("PICTURE", "INFO", "TROPHIES", "OPTIONS"), listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS).map(::ttxKey))
    }

    @Test fun trophiesPageShowsWholeRowsOnTheSet() {
        val g = Game("Okami", "PS2", 1, SourceType.ROM, romPath = "/r/okami.iso")
        val list = (1..10).map { i ->
            RaAchievementDetail(i, "Teletext fixture number $i", "Synthetic fixture achievement $i, a description that runs to a second line.",
                5 + i, null, null, if (i % 3 == 0) "2026-11-01 10:00:00" else null, null, unlockPercent = 10f, displayOrder = i)
        }
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 10, NumAchieved = list.count { it.isUnlocked })))
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        val oldRect = TvScreen.rect; val oldId = TvScreen.displayId
        TvScreen.rect = Rect(97f, 117f, 314f, 265f); TvScreen.displayId = -1
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(HomeLayout.TV, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(180)
                                    // Info (the set's P100) as a person would, then R1 to P200.
            val info = SemanticsMatcher("Info") { n -> n.config.contains(SemanticsActions.OnClick) && words(n).trim() == "info" }
            assertTrue("the TV window's Info key is missing", compose.onAllNodes(info).fetchSemanticsNodes().isNotEmpty())
            compose.onAllNodes(info)[0].performSemanticsAction(SemanticsActions.OnClick); frames(60)
            // ...then its Fastext key for the achievements (a tap, as on the touch screen).
            val key = SemanticsMatcher("Fastext") { n -> words(n).trim().let { it == "trophies" || it == "achievements" } && n.boundsInRoot.top > 0f &&
                n.config.getOrNull(SemanticsProperties.Text) != null }
            val keys = compose.onAllNodes(key).fetchSemanticsNodes()
            assertTrue("no Fastext key for the achievements", keys.isNotEmpty())
            compose.onAllNodes(key)[keys.indices.maxBy { keys[it].boundsInRoot.top }].performTouchInput { click() }; frames(60)
            frames(90)
            val rows = compose.onAllNodes(SemanticsMatcher("row") { n -> n.config.contains(SemanticsActions.OnClick) && "teletext fixture number" in words(n) })
                .fetchSemanticsNodes()
            val sx = if (big) 1.3 else 1.0
            assertTrue("x$sx: the TROPHIES page did not open (no achievement rows composed; on screen: " +
                compose.onAllNodes(SemanticsMatcher("any") { true }).fetchSemanticsNodes().map { words(it).trim() }.filter { it.isNotEmpty() }.take(30) + ")", rows.isNotEmpty())
            val whole = rows.count { r -> r.size.height > 0 && r.boundsInRoot.height >= r.size.height - 1.5f }
            assertTrue("x$sx: $whole whole achievements on P200 (want >= ${minWhole(big)}; shown/full heights " +
                rows.take(4).map { "${it.boundsInRoot.height.toInt()}/${it.size.height}" } + ")", whole >= minWhole(big))
            // The masthead's texts each stay on one line (the old header put VOLTCAP TXT one letter a line at x1.3).
            val mast = compose.onAllNodes(SemanticsMatcher("masthead") { n -> words(n).trim().let { it == "achievements" || it.startsWith("p2") || it == "voltcap txt" } })
                .fetchSemanticsNodes()
            assertTrue("x$sx: no masthead text found", mast.isNotEmpty())
            val line = mast.minOf { it.size.height }
            mast.forEach { m -> assertTrue("x$sx: masthead '${words(m).trim()}' wraps (${m.size.height} px vs one line $line px)", m.size.height < line * 1.6f) }
        } finally { TestHooks.scale.value = null; TvScreen.rect = oldRect; TvScreen.displayId = oldId }
    }
}
