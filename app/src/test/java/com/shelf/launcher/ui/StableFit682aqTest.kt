package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 682aq (D386, owner 22:43 - "don't change text appearance just to improve measurements"): a window's text fitted inside its own
 * measure (StableFitAutoSize) draws the same pixels as the old fit in a BoxWithConstraints - short, long, one long word, several
 * lines; narrow to wide; 1 to 4 lines; x1.0 and the KONKR's x1.3 text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class StableFit682aqTest {
    private val compose = createComposeRule()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    private val texts = listOf("Go", "Defeat the first colossus", "Supercalifragilisticexpialidocious",
        "Finish the game on the hardest difficulty without using a single continue, in one sitting, with every secret found")

    /** What a text draws: its box, and per text node its size, font size, line height, lines and each line's end and width. */
    private fun look(tag: String): String {
        val box = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().single()
        val texts = compose.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(tag)) and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().map { n ->
                val rs = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                n.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(rs)
                val r = rs.single()
                // (the font size itself: with auto-size the layout's input style keeps the size asked for - the lines' ends, widths and
                // bottoms below are where the chosen size shows)
                "${r.size} lines=${r.lineCount} ov=${r.hasVisualOverflow} " +
                    (0 until r.lineCount).joinToString(",") { "${r.getLineEnd(it)}:${r.getLineRight(it).toInt()}:${r.getLineBottom(it)}:${r.getLineLeft(it)}" } + " at=${n.boundsInRoot.size}"
            }
        val sel = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().single()
        return "box=${box.size} " + texts.joinToString(" | ")
    }

    @Test fun sameTextSameLook() {
        var cases = 0; val diffs = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        var case by androidx.compose.runtime.mutableStateOf(0)
        val all = mutableListOf<Triple<Float, String, Pair<Int, Int>>>()
        for (scale in listOf(1f, 1.3f)) for (t in texts) for (w in listOf(60, 140, 260, 420)) for (lines in 1..4) all += Triple(scale, t, w to lines)
        compose.setContent {
                val (scale, t, wl) = all[case]; val (w, lines) = wl
                androidx.compose.runtime.key(case) { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, scale)) {
                    Column {
                        Box(Modifier.width(w.dp).testTag("old")) { StableFullText(t, Color.White, 16.sp, maxLines = lines, lineHeight = 20.sp, reserve = false, inMeasure = false) }
                        Box(Modifier.width(w.dp).testTag("new")) { StableFullText(t, Color.White, 16.sp, maxLines = lines, lineHeight = 20.sp, reserve = false, inMeasure = true) }
                    }
                } }
        }
        for (i in all.indices) {
            case = i
            compose.mainClock.advanceTimeBy(200)
            val (scale, t, wl) = all[i]; val (w, lines) = wl
            cases++
            val tag = "x$scale w=$w lines=$lines '${t.take(20)}'"
            val oa = look("old"); val ob = look("new")
            if (oa != ob) diffs += "$tag old=$oa new=$ob"
        }
        assertEquals(128, cases)
        assertTrue("old vs new fit differ in ${diffs.size} cases: ${diffs.take(8)}", diffs.isEmpty())
    }
}
