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
import java.io.File
import java.time.Duration

/**
 * 682 (H16 window polish, the owner's order after Pocket / Night / Bar: Hero, Hero tiles, Live, Cross, Hub, Resume, Channel,
 * Twin icons, Twin showcase, Memory card, Bubbles, Panels, Cinema): a SURVEY of every text on each window's first page at the
 * KONKR's size (640 x 427 dp), x1.0 and x1.3 - a word broken inside itself, or a wrapping text cut at its box's edge (a
 * one-line text that slides is rule 11's allowed form and is not counted). The findings are written to
 * app/build/reports/h16-survey/findings.txt (uploaded with CI's test results) and printed; they feed the next H16 fixes.
 * 682g: measured by the glyphs' inked width, all 13 windows are clean at both scales, so the survey is now a GUARD - any finding
 * (or a window that cannot be reached) fails it; SurveyRule682Test pins what counts as a finding.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class WindowWordsSurvey682Test(private val layout: HomeLayout, private val scale: Float) {
    companion object {
        /** The owner's H16 order after Pocket / Night / Bar. */
        val LAYOUTS = listOf(HomeLayout.HERO, HomeLayout.HERO_TILES, HomeLayout.LIVE, HomeLayout.CROSS, HomeLayout.HUB, HomeLayout.RESUME,
            HomeLayout.CHAN, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE, HomeLayout.MEMCARD, HomeLayout.BUBBLES, HomeLayout.PANELS,
            HomeLayout.CINEMA)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} x{1}")
        fun params(): List<Array<Any>> = LAYOUTS.flatMap { l -> listOf(arrayOf<Any>(l, 1.0f), arrayOf<Any>(l, 1.3f)) }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun texts(): List<TextLayoutResult> =
        compose.onAllNodes(SemanticsMatcher("any text") { it.config.getOrNull(SemanticsProperties.Text) != null })
            .fetchSemanticsNodes().mapNotNull { n ->
                val out = mutableListOf<TextLayoutResult>()
                n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
                out.firstOrNull()
            }

    @Test fun survey() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val state = LauncherUiState().copy(raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 40, NumAchieved = 12)))
        TestHooks.scale.value = scale to (if (scale > 1f) 1.15f else 1f)
        val found = try {
            compose.mainClock.autoAdvance = false
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(240)
            val all = texts()
            assertTrue("$layout x$scale: no text of the window reached", all.isNotEmpty())
            // (682b: not r.didOverflowWidth alone - in the 682 run it flagged every chip ("Back", "Play") of all 13 windows at
            // both scales, identical at x1.0 and x1.3, which no real cut does (suspected: sub-pixel rounding of wrap-content
            // text). A text is cut when its lines run past the box by a whole pixel or more, or lines past maxLines are dropped.
            // Each finding carries its numbers so the next run can tell a real cut from a measuring artefact.)
            // (682g: the 682b run still listed 187 "cuts" - every one with "text" = the max width, e.g. 'Play' 53 px in 28 px:
            // MultiParagraph.width is the width the paragraph was laid out AT (the constraint), not the width its glyphs use.
            // The inked width is the widest line's right - left.)
            all.mapNotNull { r ->
                val inkW = inkedWidth((0 until r.lineCount).map { r.getLineLeft(it) }, (0 until r.lineCount).map { r.getLineRight(it) })
                val cut = cutAtEdge(inkW, r.size.width, r.multiParagraph.height, r.size.height, r.multiParagraph.didExceedMaxLines)
                surveyRule(r.layoutInput.text.text, brokenWord(r), cut, r.layoutInput.softWrap, r.lineCount)?.let {
                    "$it [ink ${"%.1f".format(inkW)}x${"%.1f".format(r.multiParagraph.height)} px in ${r.size.width}x${r.size.height} (max width ${r.layoutInput.constraints.maxWidth}), lines ${r.lineCount}]"
                }
            }.distinct()
        } finally { TestHooks.scale.value = null }
        val lines = if (found.isEmpty()) listOf("$layout x$scale: clean") else found.map { "$layout x$scale: $it" }
        val dir = File("build/reports/h16-survey").apply { mkdirs() }
        File(dir, "findings.txt").appendText(lines.joinToString("\n", postfix = "\n"))
        println(lines.joinToString("\n", prefix = "H16 SURVEY\n"))
        // 682g: with the inked width all 26 cases came back clean (cloud run 18 Nov) - from now on the survey is a guard:
        // a word broken inside itself or a text cut at its box on any of these 13 windows fails the build.
        assertTrue("$layout x$scale H16 findings:\n" + found.joinToString("\n"), found.isEmpty())
    }
}

class SurveyRule682Test {
    @Test fun whatCountsAsAFinding() {
        assertEquals("'Achievements' broken inside a word (2 lines)", surveyRule("Achievements", true, false, true, 2))
        assertEquals("'Last played' cut at its box's edge", surveyRule("Last played", false, true, true, 1))
        assertEquals(null, surveyRule("A long name that slides", false, true, false, 1))   // one sliding line: rule 11 allows it
        assertEquals(null, surveyRule("Played", false, false, true, 1))
    }

    /** 682b: the 682 survey reported every wrap-content chip as cut; a fraction of a pixel is rounding, not a cut. */
    @Test fun aFractionOfAPixelIsNoCut() {
        assertFalse("'Back' 41.6 px in a 41 px box", cutAtEdge(41.6f, 41, 18f, 18, false))
        assertFalse(cutAtEdge(120f, 120, 18.4f, 18, false))
        assertTrue("a word running 1 px past the edge", cutAtEdge(42f, 41, 18f, 18, false))
        assertTrue("a second line clipped by the box", cutAtEdge(80f, 80, 36f, 18, false))
        assertTrue("lines past maxLines dropped", cutAtEdge(80f, 80, 18f, 18, true))
    }

    /** 682g: the 682b run measured the layout width (the constraint, 53 px for 'Play') instead of the glyphs (28 px) -> 187 false cuts. */
    @Test fun theInkedWidthIsTheWidestLineNotTheLayoutWidth() {
        assertEquals(27.6f, inkedWidth(listOf(12.7f), listOf(40.3f)), 0.01f)          // 'Play' centred in a 53 px layout
        assertFalse("'Play' centred: 27.6 px of glyphs in a 28 px box", cutAtEdge(inkedWidth(listOf(12.7f), listOf(40.3f)), 28, 16f, 16, false))
        assertEquals(90f, inkedWidth(listOf(0f, 0f), listOf(60f, 90f)), 0.01f)         // the widest of two lines
        assertTrue("a 90 px line in an 80 px box", cutAtEdge(inkedWidth(listOf(0f, 0f), listOf(60f, 90f)), 80, 36f, 36, false))
        assertEquals(0f, inkedWidth(emptyList(), emptyList()), 0f)
    }
}

/** 682g (pure): the width a text's glyphs use - its widest line, right edge minus left edge. */
internal fun inkedWidth(lefts: List<Float>, rights: List<Float>): Float =
    lefts.indices.maxOfOrNull { rights[it] - lefts[it] } ?: 0f

/** 682b (pure): a text is cut at its box's edge when its lines run a whole pixel or more past the box, or lines past maxLines are dropped. */
internal fun cutAtEdge(textW: Float, boxW: Int, textH: Float, boxH: Int, droppedLines: Boolean): Boolean =
    droppedLines || textW - boxW >= 1f || textH - boxH >= 1f

/** 682 (pure): what the H16 survey reports for one text - a word broken inside itself, or a wrapping text cut at its edge. */
internal fun surveyRule(text: String, broken: Boolean, overflowW: Boolean, softWrap: Boolean, lines: Int): String? = when {
    broken -> "'$text' broken inside a word ($lines lines)"
    overflowW && softWrap -> "'$text' cut at its box's edge"
    else -> null
}
