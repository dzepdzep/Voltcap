package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
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

    /** Opens this layout's game window (first page) at this scale and settles it. */
    private fun open(pageFixture: Boolean = false) {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val state = LauncherUiState().copy(raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 40, NumAchieved = 12)),
            raAchievementDetails = if (pageFixture) mapOf(g.stableKey to pageSurveyAchievements()) else emptyMap())
        val options = if (pageFixture) pageSurveyOptions() else emptyList()
        compose.mainClock.autoAdvance = false
        compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
            GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}, options = options) } }
        frames(240)
    }

    @Test fun survey() {
        TestHooks.scale.value = scale to (if (scale > 1f) 1.15f else 1f)
        val found = try {
            open()
            wordFindings()
        } finally { TestHooks.scale.value = null }
        report("findings.txt", "H16 SURVEY", found)
        // 682g: with the inked width all 26 cases came back clean (cloud run 18 Nov) - from now on the survey is a guard:
        // a word broken inside itself or a text cut at its box on any of these 13 windows fails the build.
        assertTrue("$layout x$scale H16 findings:\n" + found.joinToString("\n"), found.isEmpty())
    }

    private fun report(file: String, title: String, found: List<String>, tag: String = "") {
        val lines = if (found.isEmpty()) listOf("$layout x$scale$tag: clean") else found.map { "$layout x$scale$tag: $it" }
        val dir = File("build/reports/h16-survey").apply { mkdirs() }
        File(dir, file).appendText(lines.joinToString("\n", postfix = "\n"))
        println(lines.joinToString("\n", prefix = "$title\n"))
    }

    /** Words broken inside themselves or texts cut at their own box, on what the window shows now. */
    private fun wordFindings(): List<String> {
        val all = texts()
        assertTrue("$layout x$scale: no text of the window reached", all.isNotEmpty())
        // (682b: not r.didOverflowWidth alone - in the 682 run it flagged every chip ("Back", "Play") of all 13 windows at
        // both scales, identical at x1.0 and x1.3, which no real cut does (suspected: sub-pixel rounding of wrap-content
        // text). A text is cut when its lines run past the box by a whole pixel or more, or lines past maxLines are dropped.
        // Each finding carries its numbers so the next run can tell a real cut from a measuring artefact.)
        // (682g: the 682b run still listed 187 "cuts" - every one with "text" = the max width, e.g. 'Play' 53 px in 28 px:
        // MultiParagraph.width is the width the paragraph was laid out AT (the constraint), not the width its glyphs use.
        // The inked width is the widest line's right - left.)
        return all.mapNotNull { r ->
            val inkW = inkedWidth((0 until r.lineCount).map { r.getLineLeft(it) }, (0 until r.lineCount).map { r.getLineRight(it) })
            val cut = cutAtEdge(inkW, r.size.width, r.multiParagraph.height, r.size.height, r.multiParagraph.didExceedMaxLines)
            surveyRule(r.layoutInput.text.text, brokenWord(r), cut, r.layoutInput.softWrap, r.lineCount)?.let {
                "$it [ink ${"%.1f".format(inkW)}x${"%.1f".format(r.multiParagraph.height)} px in ${r.size.width}x${r.size.height} (max width ${r.layoutInput.constraints.maxWidth}), lines ${r.lineCount}]"
            }
        }.distinct()
    }

    /**
     * 683 (H16, what the words survey cannot see): a text whose glyphs are cut by a PARENT - a clipping card, panel, page or
     * the window's edge - while the text's own box is big enough (the words survey guards that). Measured on the unmerged tree:
     * the text's own box in the root (position + size) vs its visible bounds in the root (clipped by every clipping ancestor);
     * the part cut off counts only beyond the box's empty room (box minus the glyphs' inked width / height), so a parent that
     * trims a wide text box's empty side is no finding. (Not the lines' left / right placed at the box: for centred
     * wrap-content text those are relative to the paragraph's layout width - the first 683 run reported 'PlayStation 2' in the
     * Twin windows "cut 36 px" while its whole 83 px box was visible.) Not counted: one sliding line (rule 11's marquee clips by
     * design) and texts inside a scrolling ancestor (scrolled out of view is reachable). Findings: h16-survey/parent_clip.txt.
     */
    @Test fun parentClip() {
        TestHooks.scale.value = scale to (if (scale > 1f) 1.15f else 1f)
        val found = try {
            open()
            clipFindings()
        } finally { TestHooks.scale.value = null }
        report("parent_clip.txt", "H16 PARENT CLIP", found)
        assertTrue("$layout x$scale H16 parent-clip findings:\n" + found.joinToString("\n"), found.isEmpty())
    }

    private fun clipFindings(): List<String> {
        val nodes = compose.onAllNodes(SemanticsMatcher("any text") { it.config.getOrNull(SemanticsProperties.Text) != null }, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("$layout x$scale: no text of the window reached", nodes.isNotEmpty())
        return nodes.mapNotNull { n -> clipFindingOf(n) }.distinct()
    }

    /**
     * 682m (H16, the More half of each window - the k678n13 sheet compares first / More pages): the windows that keep Play,
     * Details and Back in view and fold the rest behind More (MORE_SCENES: Hero, Hero tiles, Live, Hub, Twin icons, Twin
     * showcase of this list) are opened, More is opened as its own button does (TestHooks.winMore, the QA hook), and the same
     * two guards run on what it reveals: no word broken inside itself or cut at its box, no text cut by a parent. Proof More
     * opened: texts appear that the first page did not show (printed). Windows without More: nothing to check here (their whole row is the first page).
     * Findings: h16-survey/more_findings.txt.
     */
    @Test fun morePage() {
        val scene = winSceneFor(layout)
        org.junit.Assume.assumeTrue("$layout has no More", scene != null && scene in MORE_SCENES)
        TestHooks.scale.value = scale to (if (scale > 1f) 1.15f else 1f)
        val found = try {
            open()
            val before = texts().map { it.layoutInput.text.text }.toSet()
            TestHooks.winMore.value = true
            frames(120)
            val revealed = texts().map { it.layoutInput.text.text }.toSet() - before
            assertTrue("$layout x$scale: More did not open (no new text revealed; first page: $before)", revealed.isNotEmpty())
            println("H16 MORE $layout x$scale revealed: $revealed")
            wordFindings().map { "words: $it" } + clipFindings().map { "parent: $it" }
        } finally { TestHooks.scale.value = null; TestHooks.winMore.value = null }
        report("more_findings.txt", "H16 MORE", found, " More")
        assertTrue("$layout x$scale H16 More findings:\n" + found.joinToString("\n"), found.isEmpty())
    }

    /**
     * 682s (H16, the windows' other pages - the k678n13 sheet's last column): Details, Options and Achievements of each of the
     * 13 windows (shared expanded page or the window's own - Twin icons / Twin showcase draw theirs on the touch screen), opened
     * as their own buttons do (TestHooks.winPage -> the window's own callbacks), with the app's real option labels (the long
     * "Sync, second screen, artwork, look ..." line, a destructive row) and an achievement list with long titles. The same two
     * guards run on each page. Proof the page turned: texts appear that the first page did not show (printed). Cross's
     * achievements open as the home menu's own column (outside the window) and are not part of this survey.
     * Findings: h16-survey/page_findings.txt.
     */
    @Test fun otherPages() {
        TestHooks.scale.value = scale to (if (scale > 1f) 1.15f else 1f)
        val found = mutableListOf<String>()
        try {
            open(pageFixture = true)
            val first = texts().map { it.layoutInput.text.text }.toSet()
            // (Cross: with "achievements as the menu's own column" on (the default) its achievements open on the home screen,
            // outside the window - CrossTrophies.kt, not this window's page.)
            val pages = listOf(WinPage.DETAILS, WinPage.OPTIONS) + (if (crossTrophiesOn(layout)) emptyList() else listOf(WinPage.TROPHIES))
            for (p in pages) {
                TestHooks.winPage.value = p
                frames(120)
                val shown = texts().map { it.layoutInput.text.text }.toSet()
                val revealed = shown - first
                assertTrue("$layout x$scale: the $p page did not open (no new text; shown: $shown)", revealed.isNotEmpty())
                println("H16 PAGE $layout x$scale $p revealed: $revealed")
                found += wordFindings().map { "$p words: $it" } + clipFindings().map { "$p parent: $it" }
                TestHooks.winPage.value = WinPage.MAIN
                frames(120)
            }
        } finally { TestHooks.scale.value = null; TestHooks.winPage.value = null }
        report("page_findings.txt", "H16 PAGES", found.distinct(), " pages")
        assertTrue("$layout x$scale H16 page findings:\n" + found.distinct().joinToString("\n"), found.isEmpty())
    }
}

/** 682s: the app's own option rows (LauncherScreen's list for a ROM game) and achievements with long titles, for the page survey. */
internal fun pageSurveyOptions(): List<WinOption> = listOf(
    WinOption("Add to favorites") {},
    WinOption("Game settings", "Sync, second screen, artwork, look — for this game") {},
    WinOption("Music", "Music of its own while you're on it") {},
    WinOption("Guide", "Walkthrough that follows where you are") {},
    WinOption("Emulator for PlayStation 2", "Which app — and core — runs this console") {},
    WinOption("Achievement matching", "Hash, custom hash or game ID") {},
    WinOption("Remove shortcut", "Takes it out of Voltcap (the game stays in its app)", destructive = true) {},
)

internal fun pageSurveyAchievements(): List<RaAchievementDetail> = listOf(
    RaAchievementDetail(1, "The Colossus of the Forbidden Lands", "Defeat the first colossus without losing your grip on its fur", 10, null, null, "2026-11-01 10:00:00", null, 41.5f, 1),
    RaAchievementDetail(2, "Unbreakable", "Finish a fight without being knocked down", 25, null, "missable", null, null, 3.2f, 2),
    RaAchievementDetail(3, "Wanderer", "Ride across the whole bridge", 5, null, null, null, null, null, 3),
)

class SurveyRule682Test {
    /** 682s: the QA page request names exactly the window's pages; anything else is no request. */
    @Test fun theQaPageRequestNamesTheWindowsPages() {
        assertEquals(WinPage.DETAILS, qaWinPage("details"))
        assertEquals(WinPage.OPTIONS, qaWinPage(" Options "))
        assertEquals(WinPage.TROPHIES, qaWinPage("achievements"))
        assertEquals(WinPage.TROPHIES, qaWinPage("trophies"))
        assertEquals(WinPage.MAIN, qaWinPage("main"))
        assertEquals(null, qaWinPage("perf"))
        assertEquals(null, qaWinPage(""))
    }

    /** 683: what counts as a parent cutting a text's glyphs - box (l, t, r, b), inked size (w, h), visible part. */
    @Test fun aParentCutIsAWholePixelOfInkOutsideTheVisibleBounds() {
        // 'Achievements' 90 x 18 box full of ink, a clipping card shows 0..70 of it -> 20 px of glyphs cut on the right
        assertEquals("'Achievements' cut by a parent (right 20.0 px)", parentClipRule("Achievements", 0f, 0f, 90f, 18f, 90f, 18f, 0f, 0f, 70f, 18f, false, false))
        assertEquals(null, parentClipRule("Play", 10f, 0f, 38f, 16f, 27.6f, 16f, 10.4f, 0f, 37.6f, 16f, false, false))   // sub-pixel: rounding
        assertEquals(null, parentClipRule("A long name that slides", 0f, 0f, 300f, 18f, 300f, 18f, 0f, 0f, 120f, 18f, true, false))  // marquee
        assertEquals(null, parentClipRule("Released 2005", 0f, 400f, 90f, 418f, 90f, 18f, 0f, 400f, 90f, 410f, false, true))   // scrolls
        assertEquals("'Back' hidden by a parent", parentClipRule("Back", 0f, 0f, 30f, 16f, 30f, 16f, 0f, 0f, 0f, 0f, false, false))
        assertEquals("'Two lines' cut by a parent (bottom 18.0 px)", parentClipRule("Two lines", 0f, 0f, 80f, 36f, 80f, 36f, 0f, 0f, 80f, 18f, false, false))
        // a fillMaxWidth title: 300 px box, 120 px of glyphs; a panel trims 100 px of the box's empty room -> no finding
        assertEquals(null, parentClipRule("Shadow", 0f, 0f, 300f, 18f, 120f, 18f, 0f, 0f, 200f, 18f, false, false))
        // ... but trimming 200 px of it reaches the glyphs (200 - 180 empty = 20 px)
        assertEquals("'Shadow' cut by a parent (right 20.0 px)", parentClipRule("Shadow", 0f, 0f, 300f, 18f, 120f, 18f, 0f, 0f, 100f, 18f, false, false))
        // the first 683 run's artefact: the whole 83 px box visible -> clean, whatever the centred lines' offsets say
        assertEquals(null, parentClipRule("PlayStation 2", 77f, 110f, 160f, 125f, 82f, 15f, 77f, 110f, 160f, 125f, false, false))
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

/**
 * 683 (pure): a text's box (l, t, r, b) with (inkW, inkH) of glyphs in it vs the part clipping ancestors leave visible
 * (vl, vt, vr, vb), all in root pixels. What is cut off beyond the box's empty room, a whole pixel or more, is a cut;
 * nothing visible -> hidden. One sliding line and scrolling content are excluded.
 */
internal fun parentClipRule(text: String, l: Float, t: Float, r: Float, b: Float, inkW: Float, inkH: Float,
                            vl: Float, vt: Float, vr: Float, vb: Float, sliding: Boolean, inScroll: Boolean): String? {
    if (sliding || inScroll || r - l < 1f || b - t < 1f) return null
    if (vr - vl < 1f || vb - vt < 1f) return "'$text' hidden by a parent"
    val cutL = (vl - l).coerceAtLeast(0f); val cutR = (r - vr).coerceAtLeast(0f)
    val cutT = (vt - t).coerceAtLeast(0f); val cutB = (b - vb).coerceAtLeast(0f)
    val overW = cutL + cutR - (r - l - inkW).coerceAtLeast(0f)
    val overH = cutT + cutB - (b - t - inkH).coerceAtLeast(0f)
    val sides = buildList {
        if (overW >= 1f) { if (cutL >= cutR) add("left" to overW) else add("right" to overW) }
        if (overH >= 1f) { if (cutT >= cutB) add("top" to overH) else add("bottom" to overH) }
    }
    if (sides.isEmpty()) return null
    return "'$text' cut by a parent (" + sides.joinToString(", ") { "${it.first} ${"%.1f".format(it.second)} px" } + ")"
}

/** 683: one text node's parent-clip finding (see [WindowWordsSurvey682Test.parentClip]), or null. */
internal fun clipFindingOf(n: SemanticsNode): String? {
    val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: return null
    if (text.isBlank()) return null
    val out = mutableListOf<TextLayoutResult>()
    n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
    val r = out.firstOrNull()
    val pos = n.positionInRoot
    val inkW = if (r != null && r.lineCount > 0) inkedWidth((0 until r.lineCount).map { r.getLineLeft(it) }, (0 until r.lineCount).map { r.getLineRight(it) })
        else n.size.width.toFloat()
    val inkH = if (r != null) minOf(r.multiParagraph.height, n.size.height.toFloat()) else n.size.height.toFloat()
    val v = n.boundsInRoot
    val sliding = r != null && !r.layoutInput.softWrap && r.lineCount == 1
    val inScroll = generateSequence(n.parent) { it.parent }.any {
        it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
            it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
    }
    return parentClipRule(text, pos.x, pos.y, pos.x + n.size.width, pos.y + n.size.height, minOf(inkW, n.size.width.toFloat()), inkH,
        v.left, v.top, v.right, v.bottom, sliding, inScroll)
}

/** 683: the parent-clip measure is not vacuous - a text a clipping box cuts is found, the same text in room is not. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class ParentClipProbe683Test {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun findings(boxDp: Int): List<String> {
        compose.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(boxDp.dp, 40.dp).clipToBounds()) {
                androidx.compose.material3.Text("Achievements unlocked", softWrap = true,
                    modifier = androidx.compose.ui.Modifier.wrapContentWidth(unbounded = true))
            }
        }
        compose.waitForIdle()
        return compose.onAllNodes(SemanticsMatcher("any text") { it.config.getOrNull(SemanticsProperties.Text) != null }, useUnmergedTree = true)
            .fetchSemanticsNodes().mapNotNull { clipFindingOf(it) }
    }

    @Test fun aClippingBoxThatCutsTheGlyphsIsFound() {
        val f = findings(60)
        assertTrue("expected a parent cut, got $f", f.size == 1 && f[0].startsWith("'Achievements unlocked' cut by a parent"))
    }

    @Test fun theSameTextWithRoomIsClean() = assertEquals(emptyList<String>(), findings(400))
}
