package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Update 475 (D246): a column that shows its rows whole or not at all. Rows are laid out top to bottom at their own height;
 * the first row that would not fit in the room left — and every row after it — is not shown (never squeezed to a sliver
 * or to no height: the Storage window's facts on the KONKR, k474a). Use only where the same information is one press away
 * (a Details page); the part is marked as giving way for the QA gate (QaClip.GivesWay).
 */
@Composable
internal fun WholeRows(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.semantics { this[QaClip.GivesWay] = true }) { measurables, constraints ->
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val loose = Constraints(minWidth = 0, maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity)
        val all = measurables.map { it.measure(loose) }
        val shown = all.take(wholeRowsThatFit(all.map { it.height }, maxH))
        val used = shown.sumOf { it.height }
        val w = (shown.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(w, used.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            shown.forEach { it.placeRelative(0, y); y += it.height }
        }
    }
}

/** How many of [heights] fit whole, top to bottom, in [room] (the rule [WholeRows] lays out by; unit-tested). */
internal fun wholeRowsThatFit(heights: List<Int>, room: Int): Int {
    var used = 0
    heights.forEachIndexed { i, h -> if (used + h > room) return i; used += h }
    return heights.size
}

/**
 * Update 475 (D245 / D247): a story (a game's summary) in whatever room is left — as many WHOLE lines as fit, up to
 * [maxLines], ending in an ellipsis when cut (the whole story is one press away: Details); nothing at all when not even one
 * line fits, never a half line or a line of no height. Put it in a Column with `Modifier.weight(1f, fill = false)` so the
 * facts and keys around it are laid out first. Marked as giving way for the QA gate.
 */
@Composable
internal fun StoryLines(
    text: String, color: androidx.compose.ui.graphics.Color, fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit, maxLines: Int, modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.semantics { this[QaClip.GivesWay] = true }) {
        val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { lineHeight.toPx() }
        val fit = storyLinesThatFit(constraints.maxHeight, lineH, maxLines)
        if (fit >= 1) androidx.compose.material3.Text(
            text, color = color, fontSize = fontSize, lineHeight = lineHeight, maxLines = fit,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Whole lines of height [lineH] px that fit in [room] px, at most [maxLines] (unbounded room = [maxLines]). */
internal fun storyLinesThatFit(room: Int, lineH: Float, maxLines: Int): Int =
    if (room == androidx.compose.ui.unit.Constraints.Infinity) maxLines else (room / lineH).toInt().coerceIn(0, maxLines)

/** Update 476 (D255 / D260): the words under a story that was cut — the whole story is on the Details page. */
internal const val STORY_CUE = "Full story on Details ›"

/**
 * Update 476 (D255): a story of at most [maxLines] whole lines; when it is longer than that, its last line is given to
 * [STORY_CUE] so the cut never looks like the end. Lines are counted with the real text layout at the box's width.
 */
@Composable
internal fun StoryWithCue(
    text: String, color: androidx.compose.ui.graphics.Color, cueColor: androidx.compose.ui.graphics.Color,
    fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, maxLines: Int, modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.semantics { this[QaClip.GivesWay] = true }) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val style = androidx.compose.ui.text.TextStyle(fontSize = fontSize, lineHeight = lineHeight)
        val widthPx = constraints.maxWidth
        val total = androidx.compose.runtime.remember(text, widthPx, fontSize, lineHeight) {
            if (widthPx == androidx.compose.ui.unit.Constraints.Infinity) 1
            else measurer.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx)).lineCount
        }
        val lineH = with(androidx.compose.ui.platform.LocalDensity.current) { lineHeight.toPx() }
        val room = storyLinesThatFit(constraints.maxHeight, lineH, maxLines)
        val (shown, cue) = storyCueSplit(total, room)
        androidx.compose.foundation.layout.Column {
            if (shown >= 1) androidx.compose.material3.Text(
                text, color = color, fontSize = fontSize, lineHeight = lineHeight, maxLines = shown,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis   // qa-ok: TRUNCATE (summary; whole story on Details, cue below)
            )
            if (cue) FullText(STORY_CUE, color = cueColor, fontSize = fontSize * 0.9f, lineHeight = lineHeight,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}

/**
 * Lines of story shown and whether the cue line is drawn, for a story [total] lines long in [room] whole lines:
 * all of it when it fits; else room − 1 story lines + the cue (room ≥ 2); one line, no cue, when only one fits.
 */
internal fun storyCueSplit(total: Int, room: Int): Pair<Int, Boolean> = when {
    room <= 0 -> 0 to false
    total <= room -> total to false
    room == 1 -> 1 to false
    else -> (room - 1) to true
}

/**
 * Update 487 (E7, owner-approved D263 / D265): the first line of each page of a story [total] lines long shown [room] whole
 * lines at a time. Pages follow on whole lines; the last page ends with the story's last line and is filled from above
 * (it repeats the end of the page before), so a page is never half empty or cut. Empty when not one line fits.
 */
internal fun storyPageStarts(total: Int, room: Int): List<Int> {
    if (room <= 0 || total <= 0) return emptyList()
    if (total <= room) return listOf(0)
    val out = ArrayList<Int>()
    var s = 0
    while (s + room < total) { out += s; s += room }
    out += total - room
    return out.distinct()
}

/** Which page a story is on, turned by the window's keys (manual) or by [PagedStory] itself (auto). */
internal class StoryPager {
    var page by androidx.compose.runtime.mutableIntStateOf(0)
    var pages by androidx.compose.runtime.mutableIntStateOf(1)
    /** One page on (wraps to the first); false when the story has only one page (the key is not used). */
    fun next(): Boolean { if (pages <= 1) return false; page = (page + 1) % pages; return true }
    fun prev(): Boolean { if (pages <= 1) return false; page = (page - 1 + pages) % pages; return true }
}

/**
 * Update 487 (D265): the story pager of the game window that is open (Twin hinge window), turned by L2 / R2 (and Page Up /
 * Page Down) in GameWindow's key handler. Set while that story is on screen with more than one page.
 */
internal object WinStory {
    var pager: StoryPager? = null
    /** Turns the open window's story for [key]; true when it was used. */
    fun key(key: androidx.compose.ui.input.key.Key): Boolean {
        val p = pager ?: return false
        return when (key) {
            androidx.compose.ui.input.key.Key.ButtonR2, androidx.compose.ui.input.key.Key.PageDown -> p.next()
            androidx.compose.ui.input.key.Key.ButtonL2, androidx.compose.ui.input.key.Key.PageUp -> p.prev()
            else -> false
        }
    }
}

/** The "2 / 3" under-the-story count. */
internal fun storyPageLabel(page: Int, pages: Int): String = "${page + 1} / $pages"

/**
 * Update 487 (E7, D263 / D265): a story shown as pages of whole lines, never a moving or half line. When it fits, it is
 * plain text. When it doesn't, a page count ("1 / 3") sits beside its last line (width, not height, so no line is lost to
 * it); [auto] turns a page every [holdMs] while the home is awake and animations are on, else [pager] is turned by keys.
 * The text is laid out once (TextMeasurer); each page shows its own lines from that layout, so it starts and ends on a line.
 */
@Composable
internal fun PagedStory(
    text: String, color: androidx.compose.ui.graphics.Color, fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit, maxLines: Int, modifier: Modifier = Modifier,
    pager: StoryPager = androidx.compose.runtime.remember { StoryPager() }, auto: Boolean = true, holdMs: Long = 7000,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    countColor: androidx.compose.ui.graphics.Color = color.copy(alpha = color.alpha * 0.6f),
    /** A tap on the story turns its page (windows, where the story is touched; the home screens leave taps to the games). */
    tapToTurn: Boolean = false,
    /** The window's own story: registered as [WinStory.pager] while it has pages, so L2 / R2 turn it. */
    windowKeys: Boolean = false
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.semantics { this[QaClip.GivesWay] = true }) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val style = androidx.compose.ui.text.TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight, fontFamily = fontFamily)
        val countStyle = androidx.compose.ui.text.TextStyle(color = countColor, fontSize = fontSize * 0.8f, lineHeight = lineHeight,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        val w = constraints.maxWidth
        if (w == Constraints.Infinity || w <= 0) return@BoxWithConstraints
        val lineH = with(density) { lineHeight.toPx() }
        val full = androidx.compose.runtime.remember(text, w, style) { measurer.measure(text, style, constraints = Constraints(maxWidth = w)) }
        val roomAll = storyLinesThatFit(constraints.maxHeight, lineH, maxLines)
        if (roomAll < 1) return@BoxWithConstraints
        if (full.lineCount <= roomAll) {
            androidx.compose.material3.Text(text, style = style)
            return@BoxWithConstraints
        }
        // Paged: the count takes a column at the right, the story wraps in the rest.
        val countW = androidx.compose.runtime.remember(countStyle) { measurer.measure("99 / 99", countStyle).size.width }
        val gap = with(density) { 10.dp.roundToPx() }
        val textW = (w - countW - gap).coerceAtLeast(w / 2)
        val lay = androidx.compose.runtime.remember(text, textW, style) { measurer.measure(text, style, constraints = Constraints(maxWidth = textW)) }
        val starts = androidx.compose.runtime.remember(lay.lineCount, roomAll) { storyPageStarts(lay.lineCount, roomAll) }
        androidx.compose.runtime.SideEffect { pager.pages = starts.size; if (pager.page >= starts.size) pager.page = 0 }
        if (windowKeys) androidx.compose.runtime.DisposableEffect(pager) {
            WinStory.pager = pager
            onDispose { if (WinStory.pager === pager) WinStory.pager = null }
        }
        androidx.compose.runtime.LaunchedEffect(text) { pager.page = 0 }
        val calm = HomeIdle.calm
        val animate = rememberAnimationsEnabled()
        androidx.compose.runtime.LaunchedEffect(text, auto, calm, animate, pager.page, starts.size) {
            if (!auto || calm || !animate || starts.size <= 1) return@LaunchedEffect
            kotlinx.coroutines.delay(holdMs)
            pager.next()
        }
        val page = pager.page.coerceIn(0, (starts.size - 1).coerceAtLeast(0))
        val boxH = with(density) { (lineH * roomAll).toDp() }
        androidx.compose.foundation.layout.Row(
            Modifier.semantics { this[androidx.compose.ui.semantics.SemanticsProperties.Text] = listOf(androidx.compose.ui.text.AnnotatedString(text)) }
                .then(if (tapToTurn) Modifier.pointerInput(starts.size) {
                    detectTapGestures { pager.next() }
                } else Modifier),
            verticalAlignment = androidx.compose.ui.Alignment.Bottom
        ) {
            androidx.compose.animation.Crossfade(
                page, label = "story-page",
                animationSpec = androidx.compose.animation.core.tween(if (animate) 320 else 0),
                modifier = Modifier.width(with(density) { textW.toDp() }).height(boxH)
            ) { p ->
                // The page's own lines as text of their own (the same breaks: each page starts at a line start at the same
                // width) — never a picture of the whole story clipped to the box, which cut the last line's descenders.
                val st = starts.getOrElse(p) { 0 }
                val a = lay.getLineStart(st)
                val e = lay.getLineEnd((st + roomAll).coerceAtMost(lay.lineCount) - 1)
                // (Not clipped to the box: a last line's descenders ("g", "p") reach a little below its line height.)
                androidx.compose.material3.Text(text.substring(a, e).trimEnd(), style = style, maxLines = roomAll,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(with(density) { gap.toDp() }))
            androidx.compose.material3.Text(storyPageLabel(page, starts.size), style = countStyle, maxLines = 1)
        }
    }
}

/**
 * Update 488 (D284, Thor 1080p: Back cut at the bottom of Arctic / Panels / Side): the height for each of [n] action rows
 * so that all of them fit [avail] with [gap] between them — [max] (the design's own) when there is room, never below [min];
 * null when even [min] doesn't fit (the list keeps scrolling then, as before).
 */
internal fun fitRowHeight(avail: androidx.compose.ui.unit.Dp, n: Int, gap: androidx.compose.ui.unit.Dp,
                          max: androidx.compose.ui.unit.Dp, min: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp? {
    if (n <= 0) return max
    val each = (avail - gap * (n - 1)) / n
    return when {
        each >= max -> max
        each >= min -> each
        else -> null
    }
}

/** (Update 488b, D284) When the rows can't all fit, the scrolling list is cut into whole rows: the row height is stretched or
 *  shrunk a little (never under [min]) so a whole number of rows fills [avail] exactly. Focus scrolling then moves by whole rows,
 *  so no row is ever shown cut at the list's edge. Near [target]; null when even one row at [min] doesn't fit or [avail] is unbounded. */
internal fun snapRowHeight(avail: androidx.compose.ui.unit.Dp, n: Int, gap: androidx.compose.ui.unit.Dp,
                           target: androidx.compose.ui.unit.Dp, min: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp? {
    if (n <= 0 || avail == androidx.compose.ui.unit.Dp.Infinity || avail < min) return null
    var v = kotlin.math.round((avail + gap) / (target + gap)).toInt().coerceIn(1, n)
    while (v > 1 && (avail - gap * (v - 1)) / v < min) v--
    return (avail - gap * (v - 1)) / v
}
