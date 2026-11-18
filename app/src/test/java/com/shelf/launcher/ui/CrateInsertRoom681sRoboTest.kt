package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 681s (P2 batch 1, emulator review 44194071430 finding 1): Record crate's TRACKLIST at the KONKR's x1.3 showed one to two
 * tracks under the sleeve's full header. On the sleeve's own pages the header steps back (crateHeader), so the insert shows at
 * least three whole tracks at x1.0 and two at x1.3 (640 x 427 dp, a dozen achievements; the same window AchFirstRowRoboTest uses).
 * Info keeps the full header. Fails on the old header (cover 0.26 of the sleeve, name 3 x 21 sp) with the counts in the message.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class CrateInsertRoom681sRoboTest(private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "big={0}")
        fun cases(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
        /** Whole tracks wanted on the insert: three at x1.0, two at x1.3 (each track is the title and its words' line). */
        fun minWhole(big: Boolean) = if (big) 2 else 3
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).lowercase()

    @Test fun headerStepsBackOnTheSleevesOwnPagesOnly() {
        val info = crateHeader(false); val page = crateHeader(true)
        assertEquals(CrateHeader(0.26f, 19, 21, 3, 8, true), info) // Info: the sleeve as it was (owner-seen design)
        assertTrue(page.cover < info.cover && page.name < info.name && page.lines < info.lines && page.rule < info.rule)
        assertTrue("a readable name", page.name >= 14 && page.line > page.name && page.lines >= 1) // one line that slides (rule 11)
        assertTrue(!page.console)
        // the count left the sheet's first line for the header, beside TRACKLIST only
        assertEquals("TRACKLIST  ·  8 / 12", crateHeaderName(WinPage.TROPHIES, 8, 12))
        assertEquals("TRACKLIST", crateHeaderName(WinPage.TROPHIES, 0, 0))
        assertEquals("LINER NOTES", crateHeaderName(WinPage.DETAILS, 8, 12))
        assertEquals("SIDE A", crateHeaderName(WinPage.MAIN, 8, 12))
    }

    @Test fun tracklistShowsThreeWholeTracks() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val list = (1..12).map { i ->
            RaAchievementDetail(i, "Track fixture number $i", "Synthetic fixture achievement $i, a description that runs to a second line.",
                5 + i, null, null, if (i % 3 == 0) null else "2026-11-01 10:00:00", null, unlockPercent = 10f, displayOrder = i)
        }
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 12, NumAchieved = list.count { it.isUnlocked })))
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(HomeLayout.CRATE, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(180)
            fun tracks() = compose.onAllNodes(SemanticsMatcher("track") { n -> n.config.contains(SemanticsActions.OnClick) && "track fixture number" in words(n) })
                .fetchSemanticsNodes()
            // Open the TRACKLIST as a person would: its key (behind ••• when the sleeve keeps it there), else R1 R1.
            val ach = SemanticsMatcher("achievements control") { n -> n.config.contains(SemanticsActions.OnClick) &&
                words(n).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.let { w -> w.isNotEmpty() && w.all { it in setOf("achievements", "trophies") } } }
            var found = compose.onAllNodes(ach).fetchSemanticsNodes()
            if (found.isEmpty()) {
                val more = SemanticsMatcher("More") { n -> n.config.contains(SemanticsActions.OnClick) && words(n).trim().let { it == "•••" || it == "more" } }
                val ms = compose.onAllNodes(more).fetchSemanticsNodes()
                if (ms.isNotEmpty()) { compose.onAllNodes(more)[ms.size - 1].performSemanticsAction(SemanticsActions.OnClick); frames(20) }
                found = compose.onAllNodes(ach).fetchSemanticsNodes()
            }
            if (found.isNotEmpty()) compose.onAllNodes(ach)[found.size - 1].performSemanticsAction(SemanticsActions.OnClick)
            else org.robolectric.shadows.ShadowDialog.getLatestDialog()?.let { d ->
                repeat(2) {
                    d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_R1)); frames(1)
                    d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_R1)); frames(30)
                }
            }
            frames(90)
            val rows = tracks()
            assertTrue("x${if (big) 1.3 else 1.0}: the TRACKLIST did not open", rows.isNotEmpty())
            // (The insert leans -0.7 deg, so a whole row's box in the root is a little taller than the row: a track is whole when it
            // shows as much as the first one, which AchFirstRowRoboTest keeps whole.)
            val ref = rows.first().boundsInRoot.height
            val whole = rows.count { r -> r.size.height > 0 && r.boundsInRoot.height >= ref - 1.5f }
            assertTrue("x${if (big) 1.3 else 1.0}: $whole whole tracks on the insert (want >= ${minWhole(big)}; shown heights " +
                rows.take(5).map { "${it.boundsInRoot.height.toInt()}/${it.size.height}" } + ")", whole >= minWhole(big))
        } finally { TestHooks.scale.value = null }
    }
}
