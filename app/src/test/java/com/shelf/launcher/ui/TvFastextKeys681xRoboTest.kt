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
import androidx.compose.ui.text.TextLayoutResult
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
 * 681x (P2 emulator review 44233448467, TV room): on the set's screen the four Fastext keys had equal widths, so the long
 * words slid inside their keys ("TROPHIE" at x1.0, "PICTU / TROPH / OPTIO" at x1.3) while INFO had room to spare. Each key now
 * takes its own word's width, and where the full words cannot stand side by side the set's short words (PIC / OPTS) do.
 * The device test: every key's word is laid out no wider than its key (a sliding word is laid out wider). Fails on the old
 * equal-width row with the words and widths in the message.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class TvFastextKeys681xRoboTest(private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "big={0}")
        fun cases(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
        val KEY_WORDS = setOf("picture", "pic", "info", "trophies", "options", "opts")
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).trim().lowercase()

    @Test fun planFullWordsWhenTheyFit() {
        val full = listOf("PICTURE", "INFO", "TROPHIES", "OPTIONS"); val short = listOf("PIC", "INFO", "TROPHIES", "OPTS")
        val w: (String) -> Float = { it.length * 6f }
        val (shown, weights) = ttxKeyPlan(full, short, w, avail = 26 * 6f + 12f, gap = 4f)
        assertEquals(full, shown)
        assertEquals(listOf(42f, 24f, 48f, 42f), weights)
        // One px short of room -> the short words, each key its own word's width.
        val (s2, w2) = ttxKeyPlan(full, short, w, avail = 26 * 6f + 11f, gap = 4f)
        assertEquals(short, s2); assertEquals(listOf(18f, 24f, 48f, 24f), w2)
        assertEquals(listOf("PIC", "INFO", "TROPHIES", "OPTS"), listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS).map(::ttxKeyShort))
    }

    @Test fun fastextWordsStandWholeInTheirKeysOnTheSet() {
        val g = Game("Okami", "PS2", 1, SourceType.ROM, romPath = "/r/okami.iso")
        // Achievements, so the set has its TROPHIES page and key (a game without them has PICTURE / INFO only).
        val list = (1..6).map { i ->
            RaAchievementDetail(i, "Fastext fixture $i", "Synthetic fixture achievement $i.", 5, null, null, if (i % 2 == 0) "2026-11-01 10:00:00" else null, null,
                unlockPercent = 10f, displayOrder = i)
        }
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 6, NumAchieved = 3)))
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        val oldRect = TvScreen.rect; val oldId = TvScreen.displayId
        TvScreen.rect = Rect(97f, 117f, 314f, 265f); TvScreen.displayId = -1
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(HomeLayout.TV, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(180)
            val info = SemanticsMatcher("Info") { n -> n.config.contains(SemanticsActions.OnClick) && words(n) == "info" }
            assertTrue("the TV window's Info key is missing", compose.onAllNodes(info).fetchSemanticsNodes().isNotEmpty())
            compose.onAllNodes(info)[0].performSemanticsAction(SemanticsActions.OnClick); frames(150)
            val sx = if (big) 1.3 else 1.0
            val keys = compose.onAllNodes(SemanticsMatcher("fastext") { n -> words(n) in KEY_WORDS && !n.config.contains(SemanticsActions.OnClick) &&
                n.config.getOrNull(SemanticsProperties.Text) != null }).fetchSemanticsNodes()
            // The Fastext row is the lowest line of key words on the set's page.
            val bottom = keys.maxOfOrNull { it.boundsInRoot.top } ?: -1f
            val row = keys.filter { kotlin.math.abs(it.boundsInRoot.top - bottom) < 4f }.sortedBy { it.boundsInRoot.left }
            assertTrue("x$sx: Fastext keys on the set's page (found ${row.map(::words)})", row.size >= 3 && row.any { words(it) == "trophies" })
            val report = row.map { k ->
                val res = mutableListOf<TextLayoutResult>()
                k.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(res)
                val laid = res.firstOrNull()?.let { maxOf(it.size.width.toFloat(), (0 until it.lineCount).maxOf { l -> it.getLineRight(l) - it.getLineLeft(l) }) } ?: -1f
                Triple(words(k), laid, k.boundsInRoot.width)
            }
            assertTrue("x$sx: no text layout for the keys $report", report.all { it.second > 0f })
            report.forEach { (w, laid, box) ->
                assertTrue("x$sx: Fastext '$w' laid out ${laid.toInt()} px in a ${box.toInt()} px key - it slides (all: " +
                    report.map { "${it.first} ${it.second.toInt()}/${it.third.toInt()}" } + ")", laid <= box + 1.5f)
            }
        } finally { TestHooks.scale.value = null; TvScreen.rect = oldRect; TvScreen.displayId = oldId }
    }
}
