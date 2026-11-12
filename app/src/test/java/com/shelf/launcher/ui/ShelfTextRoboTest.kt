package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Phase 7 (12 Nov): text and achievement collisions in the game window, measured on the JVM with Robolectric's native graphics
 * (real text layout, Android's own fonts) at the KONKR's 640 × 427 dp: the Shelf-family windows (Game shelf, Case stacks,
 * Console stack, Coverflow details - one window, INDEX_CARD) and the four native-page windows, settled, with a short and a
 * very long title, text ×1.0 and ×1.3, and no / a usual / a huge achievement count. Fails when any text is cut (its layout
 * overflows the room it was given) or two texts overlap on screen. Not hardware: Android's real fonts on the device and its
 * GPU may differ (popclip R26 on the handheld stays the gate).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class ShelfTextRoboTest(private val layout: HomeLayout, private val title: String, private val scale: Float, private val ach: Int?) {
    companion object {
        const val LONG = "The Legend of the Extraordinarily Long Subtitle: Chronicles of the Forgotten Kingdom - Definitive Edition"
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {2} ach={3} {1}")
        fun cases(): List<Array<Any?>> {
            val layouts = listOf(HomeLayout.SHELF, HomeLayout.STACKS, HomeLayout.STORAGE, HomeLayout.NIGHT, HomeLayout.TILES, HomeLayout.BLADES)
            val out = mutableListOf<Array<Any?>>()
            for (l in layouts) for (t in listOf("Ico", LONG)) for (s in listOf(1.0f, 1.3f)) for (a in listOf<Int?>(null, 160, 9999))
                out += arrayOf<Any?>(l, t, s, a)
            return out
        }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun texts(): List<SemanticsNode> {
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { if (n.config.getOrNull(SemanticsProperties.Text) != null) out += n; n.children.forEach(::walk) }
        roots.forEach { r -> walk(r) }
        return out
    }

    private fun name(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    private fun layoutOf(n: SemanticsNode): TextLayoutResult? {
        val got = mutableListOf<TextLayoutResult>()
        val act = n.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
        runCatching { act.action?.invoke(got) }
        return got.firstOrNull()
    }

    @Test fun noCutTextNoOverlap() {
        RuntimeEnvironment.setFontScale(scale)
        val g = Game(title, "SNES", 1, SourceType.ROM, romPath = "/r/p.sfc")
        val state = LauncherUiState().let { s ->
            if (ach == null) s else s.copy(raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = ach, NumAchieved = ach * 7 / 8)))
        }
        compose.mainClock.autoAdvance = false
        compose.setContent { GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) }
        frames(180)                                       // settled: open motion, focus asks, marquee pauses
        val all = texts().filter { n -> n.boundsInRoot.width > 0.5f && n.boundsInRoot.height > 0.5f }
        assertTrue("$layout: nothing drawn", all.isNotEmpty())
        val problems = mutableListOf<String>()
        val notes = mutableListOf<String>()
        for (n in all) {
            val r = layoutOf(n) ?: continue
            val mp = r.multiParagraph
            // cut: lines that don't fit the text's own height, a line running past its own width, or an ellipsis (rule 11)
            val hiddenLines = mp.height - r.size.height > 1.5f && mp.lineCount > 1 || (mp.didExceedMaxLines && r.layoutInput.overflow != androidx.compose.ui.text.style.TextOverflow.Visible)
            val wide = (0 until r.lineCount).any { i -> r.getLineRight(i) - r.size.width > 1.5f && r.layoutInput.softWrap }
            val ellipsis = r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)
            if (hiddenLines || wide || ellipsis)
                problems += "cut: '${name(n)}' (${r.lineCount} lines${if (ellipsis) ", ellipsis" else ""}, text ${r.size} in ${n.boundsInRoot})"
            // wider than what is shown of it: a scrolling title (allowed, rule 11) or cut by a parent - for the device check
            else if (r.size.width - n.boundsInRoot.width > 1.5f || r.size.height - n.boundsInRoot.height > 1.5f)
                notes += "shown in part (scrolls, or cut by a parent): '${name(n).take(60)}' text ${r.size} shown ${n.boundsInRoot.size}"
        }
        if (notes.isNotEmpty()) println("TEXT-NOTES $layout ×$scale ach=$ach '${title.take(20)}':\n" + notes.joinToString("\n"))
        // two texts over each other where both are drawn (their visible, clipped bounds), beyond a 2 px touch
        for (i in all.indices) for (j in i + 1 until all.size) {
            val a = all[i].boundsInRoot; val b = all[j].boundsInRoot
            val o: Rect = a.intersect(b)
            if (o.width > 2f && o.height > 2f && !nested(all[i], all[j])) problems += "overlap: '${name(all[i])}' $a × '${name(all[j])}' $b"
        }
        assertTrue("$layout ×$scale ach=$ach '${title.take(20)}': ${problems.size} problem(s)\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** A text inside another text's node (a label and its own merged copy) is one text, not two. */
    private fun nested(a: SemanticsNode, b: SemanticsNode): Boolean {
        var p: SemanticsNode? = a.parent
        while (p != null) { if (p.id == b.id) return true; p = p.parent }
        p = b.parent
        while (p != null) { if (p.id == a.id) return true; p = p.parent }
        return false
    }
}
