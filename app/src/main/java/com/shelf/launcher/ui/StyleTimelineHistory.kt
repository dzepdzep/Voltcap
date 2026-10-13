package com.shelf.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/*
 * Update 151: Timeline as a history. It looked too much like the Circuit board (one glowing line across the middle), so
 * it's now a vertical history: a thin time line runs down the left; along it big year headings (and, ordered by last
 * played or date added, a month under each year), and under each heading a row of that period's games. The row you're
 * in is bigger; the game you're on grows a little more, with its name and date under it. ▲ ▼ move between periods,
 * ◀ ▶ along a row (on into the next / previous period at its ends). The list scrolls to keep the row you're in at the
 * top and the game you're on in the middle of its row. Touch: swipe up / down or along a row, tap a game to pick it and
 * again to open it. Only the rows on screen are composed; nothing moves while you rest.
 * Options (Settings › Home › Layout options (Timeline)): the order (Features.timelineBy) and the dates (timelineDates).
 */

/** One period of the history: places [start] until [end] on the line (TimelineData.order), its headings. */
internal class HistoryPeriod(
    val start: Int,
    val end: Int,
    /** Stable for the period (year * 13 + month, the year, or Long.MIN_VALUE for "no date"). */
    val key: Long,
    /** The big heading (the year, "Never played"…), or null when it's the same year as the row above. */
    val title: String?,
    /** The month under the year (by last played / added), else null. */
    val sub: String?
) {
    val size: Int get() = end - start
}

/** The rows as drawn last, for [timelineHistoryKey] (which runs outside composition): each row's first place. */
internal object TimelineRows {
    @Volatile var starts: IntArray = IntArray(0)
}

/** "No date" in TimelineData.stamp (the same value StyleTimeline.kt uses). */
private const val NO_DATE = Long.MIN_VALUE

/** How much bigger the game you're on is than the others in its row. */
private const val ON_GROW = 1.12f

/** The row [p] (a place on the line) is in: the last row starting at or before it. */
internal fun timelineRowOf(starts: IntArray, p: Int): Int {
    if (starts.isEmpty()) return 0
    var lo = 0
    var hi = starts.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (starts[mid] <= p) lo = mid else hi = mid - 1
    }
    return lo
}

/** Splits the line into its periods: a month each by last played / added, a year each by release, "no date" last. */
internal fun timelineRows(data: TimelineData, mode: Features.TimelineBy): List<HistoryPeriod> {
    val n = data.order.size
    val out = ArrayList<HistoryPeriod>()
    val byMonth = mode != Features.TimelineBy.RELEASED
    val cal = Calendar.getInstance()
    // "LLLL": the month's name on its own (some languages bend "MMMM" as if a day came before it).
    val monthFmt = SimpleDateFormat("LLLL", Locale.getDefault())
    fun keyOf(v: Long): Long = when {
        v == NO_DATE -> Long.MIN_VALUE
        !byMonth -> v
        else -> { cal.timeInMillis = v; cal.get(Calendar.YEAR) * 13L + cal.get(Calendar.MONTH) }
    }
    var lastYear: Long? = null
    var p = 0
    while (p < n) {
        val key = keyOf(data.stamp[p])
        var e = p + 1
        while (e < n && keyOf(data.stamp[e]) == key) e++
        val title: String?
        val sub: String?
        if (key == Long.MIN_VALUE) {
            title = when (mode) {
                Features.TimelineBy.LAST_PLAYED -> "Never played"
                Features.TimelineBy.ADDED -> "Earlier"
                Features.TimelineBy.RELEASED -> "Unknown year"
            }
            sub = null
            lastYear = null
        } else if (!byMonth) {
            title = key.toString()
            sub = null
        } else {
            val year = key / 13L
            title = if (year != lastYear) year.toString() else null
            lastYear = year
            cal.timeInMillis = data.stamp[p]
            sub = monthFmt.format(cal.time).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
        out += HistoryPeriod(p, e, key, title, sub)
        p = e
    }
    return out
}

/** Card sizes for the row you're in ([bigH]) and the others ([smallH]), and the caption under the game you're on. */
private class HistoryGeo(val bigW: Dp, val bigH: Dp, val smallW: Dp, val smallH: Dp, val capW: Dp, val gutter: Dp, val gap: Dp)

/**
 * The history itself: a list of periods, each a heading and a row of games. [data] is the line's order (StyleTimeline.kt).
 */
@Composable
internal fun TimelineHistory(c: StyleCtx, data: TimelineData, mode: Features.TimelineBy, compact: Boolean, modifier: Modifier) {
    val rows = remember(data, mode) { timelineRows(data, mode) }
    val starts = remember(rows) { IntArray(rows.size) { rows[it].start } }
    SideEffect { TimelineRows.starts = starts }
    val place = data.rank.getOrElse(c.focus) { 0 }
    // Update 156: Endless on: the history repeats below its end (and above its start), the place you're on counted
    // on round, so ▶ on the last game glides on down to the first period like any step into the next one.
    val n = data.order.size
    // (Update 173: not with a single period — the same heading and games stood three times down the screen.)
    val loop = loopRowOn(c) && n == c.games.size && rows.size >= 2
    SideEffect { StyleLoop.on = loopRowOn(c) }
    val tap = remember { LoopTap() }
    val v = rememberWalkedPlace(place, n, loop, StyleLoop.walked, data, tap)
    val nRows = rows.size.coerceAtLeast(1)
    val half = LOOP_COPIES / 2
    // The rows in the list (every round of the history with Endless), and the one you're in.
    val total = if (loop) rows.size * LOOP_COPIES else rows.size
    val focusRow = if (loop) ((half + Math.floorDiv(v, n)) * nRows + timelineRowOf(starts, v.mod(n))).coerceIn(0, total - 1)
        else timelineRowOf(starts, place)
    val keys = remember(c.games) { gameKeys(c.games) }
    val text = remember { TimelineText() }
    text.refresh(System.currentTimeMillis())
    val accent = ShelfTheme.accent
    val dates = Features.timelineDates.state.value
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    val list = rememberLazyListState(initialFirstVisibleItemIndex = focusRow)
    // The rows the list last moved for: a new category / order jumps straight to the row instead of gliding.
    val shownRows = remember { arrayOfNulls<Any>(2) }
    LaunchedEffect(focusRow, rows, loop) {
        // (Update 156: Endless switched on or off also jumps straight there: the list's rows are other ones then.)
        val fresh = shownRows[0] !== rows || shownRows.getOrNull(1) != loop
        shownRows[0] = rows
        shownRows[1] = loop
        if (focusRow !in 0 until total) return@LaunchedEffect
        if (fresh || abs(focusRow - list.firstVisibleItemIndex) > 3) list.scrollToItem(focusRow)
        else if (list.firstVisibleItemIndex != focusRow || list.firstVisibleItemScrollOffset != 0) list.animateScrollToItem(focusRow)
    }
    BoxWithConstraints(modifier.categorySlide()) {
        val k = GameSize.scale(HomeLayout.TIMELINE)
        val aspect = styleAspect(c.type).coerceIn(0.13f, 1.8f)
        // The row you're in: cards about a third of the height (never wider than about a third of the width); the
        // other rows smaller, so the periods around it show too.
        val bigH = minOf(maxHeight * 0.34f * k, maxHeight * 0.42f, maxWidth * 0.34f / aspect).coerceAtLeast(40.dp)
        val smallH = bigH * 0.62f
        val geo = HistoryGeo(
            bigW = bigH * aspect, bigH = bigH, smallW = smallH * aspect, smallH = smallH,
            capW = minOf(maxOf(bigH * aspect * 1.5f, 150.dp), maxWidth * 0.6f),
            gutter = if (compact) 20.dp else 26.dp, gap = if (compact) 10.dp else 14.dp
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            // Update 156: with Endless, item r is period r % rows of round r / rows (the time line runs on through them all).
            items(total, key = { if (loop) "${rows[it % nRows].key}#${it / nRows}" else rows[it].key }) { r ->
                val row = rows[if (loop) r % nRows else r]
                val round = r / nRows - half
                HistoryRow(
                    c, data, mode, row, first = !loop && r == 0, last = !loop && r == rows.size - 1, on = r == focusRow,
                    place = place, keys = keys, text = text, dates = dates, inGames = inGames,
                    geo = geo, accent = accent, compact = compact,
                    // A tap picks the round that was tapped (so the list doesn't run off to another one).
                    onPick = if (loop) ({ p -> tap.virtual = round * n + p }) else null
                )
            }
        }
    }
}

/** One period: the time line and its dot in the gutter, the heading(s), and the row of games. */
@Composable
private fun HistoryRow(
    c: StyleCtx, data: TimelineData, mode: Features.TimelineBy, row: HistoryPeriod, first: Boolean, last: Boolean, on: Boolean,
    place: Int, keys: List<String>, text: TimelineText, dates: Boolean, inGames: Boolean,
    geo: HistoryGeo, accent: Color, compact: Boolean,
    // Update 156: with Endless, told the place of a game tapped in this row before it is selected.
    onPick: ((Int) -> Unit)? = null
) {
    val glide = Features.rowGlide.state.value
    val col = if (on) (place - row.start).coerceIn(0, (row.size - 1).coerceAtLeast(0)) else 0
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = if (on) (col - 1).coerceAtLeast(0) else 0)
    // The row you're in keeps the game you're on in its middle (as far as the row's ends allow).
    LaunchedEffect(col, on) {
        if (!on) return@LaunchedEffect
        // (One frame first, so a row that has just grown is measured at its new size.)
        androidx.compose.runtime.withFrameNanos { }
        val info = rowState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == col }
        if (item == null) {
            if (abs(col - rowState.firstVisibleItemIndex) > 6) rowState.scrollToItem((col - 1).coerceAtLeast(0))
            else rowState.animateScrollToItem((col - 1).coerceAtLeast(0))
        } else {
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val delta = item.offset + item.size / 2f - mid
            if (abs(delta) > 1f) rowState.animateScrollBy(delta, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
        }
    }
    val cardW = if (on) geo.bigW else geo.smallW
    val cardH = if (on) geo.bigH else geo.smallH
    val dotY = if (compact) 11.dp else 14.dp
    Column(
        Modifier
            .fillMaxWidth()
            // The time line down the gutter (one piece per row, so it runs on unbroken) and the period's dot; drawn
            // when the row is laid out, never per frame.
            .drawBehind {
                val x = geo.gutter.toPx() / 2f
                val y = dotY.toPx()
                val top = if (first) y else 0f
                val bottom = if (last) y else size.height
                if (bottom > top) drawLine(accent.copy(alpha = 0.45f), Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                if (on) {
                    drawCircle(accent.copy(alpha = 0.28f), radius = 9.dp.toPx(), center = Offset(x, y))
                    drawCircle(accent, radius = 6.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
                } else {
                    drawCircle(accent, radius = 4.5.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = 1.8.dp.toPx(), center = Offset(x, y))
                }
            }
            .padding(start = geo.gutter, bottom = if (compact) 6.dp else 10.dp)
    ) {
        HistoryHeading(row, on, accent, compact)
        val pad = if (on) maxOf(8.dp, (geo.capW - cardW) / 2f) else 8.dp
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(geo.gap),
            // Room above for the game you're on to grow into, and at the ends for its name under it.
            contentPadding = PaddingValues(start = pad, end = pad, top = if (on) cardH * (ON_GROW - 1f) + 4.dp else 4.dp, bottom = 2.dp)
        ) {
            items(row.size, key = { keys.getOrElse(data.order[row.start + it]) { "t$it" } }) { i ->
                val p = row.start + i
                val real = data.order[p]
                val g = c.games.getOrNull(real)
                if (g != null) {
                    val here = on && p == place
                    HistoryTile(
                        c, g, real, here = here, sel = here && inGames, big = on, cardW = cardW, cardH = cardH, capW = geo.capW,
                        date = if (on && (dates || here)) text.date(mode, data.stamp[p]) else null, accent = accent, compact = compact,
                        onPick = if (onPick != null) ({ onPick(p) }) else null // (Update 156)
                    )
                }
            }
        }
    }
}

/** The period's heading: the year big (when it changes), the month beside the dot, and how many games. */
@Composable
private fun HistoryHeading(row: HistoryPeriod, on: Boolean, accent: Color, compact: Boolean) {
    val shadow = nameTextStyle(NameBackdrop.SHADOW)
    val count = if (row.size == 1) "1 game" else "${row.size} games"
    val lineH = if (compact) 22.dp else 28.dp
    // The first line sits beside the period's dot.
    if (row.title != null && row.sub != null) {
        // A new year's first month: the year big on the dot's line, its month (and the count) under it.
        Row(Modifier.height(lineH), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.title, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Black,
                maxLines = 1, softWrap = false, style = shadow
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.sub, color = if (on) accent else Color.White.copy(alpha = 0.88f), fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, style = shadow
            )
            Spacer(Modifier.width(8.dp))
            Text(count, color = Color.White.copy(alpha = 0.6f), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, softWrap = false, style = shadow)
        }
    } else {
        Row(Modifier.height(lineH), verticalAlignment = Alignment.CenterVertically) {
            val big = row.title != null
            Text(
                row.title ?: row.sub.orEmpty(),
                color = if (big) Color.White else if (on) accent else Color.White.copy(alpha = 0.88f),
                fontSize = if (big) (if (compact) 20.sp else 26.sp) else (if (compact) 13.sp else 15.sp),
                fontWeight = if (big) FontWeight.Black else FontWeight.Bold, maxLines = 1, softWrap = false, style = shadow
            )
            Spacer(Modifier.width(8.dp))
            Text(count, color = Color.White.copy(alpha = 0.6f), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, softWrap = false, style = shadow)
        }
    }
}

/**
 * A game in a row: its card art (the layout's artwork). The game you're on grows a little and glows (the layout's focus
 * effect) with its name and date under it; the others in the row you're in show their date (Dates on the line).
 * Tap: pick it; tap the one you're on: open it.
 */
@Composable
private fun HistoryTile(
    c: StyleCtx, g: Game, real: Int, here: Boolean, sel: Boolean, big: Boolean, cardW: Dp, cardH: Dp, capW: Dp,
    date: String?, accent: Color, compact: Boolean,
    // Update 156: runs before a tap selects the game (Endless: which round was tapped).
    onPick: (() -> Unit)? = null
) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val pick by rememberUpdatedState(onPick) // (Update 156)
    val titles = LocalTitleOptions.current
    val glide = Features.rowGlide.state.value
    val grow by animateFloatAsState(
        if (here) 1f else 0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness), label = "historyGrow"
    )
    // The focus pulse only on the game you're on, so resting re-draws that one card alone.
    val pulse: Float? = if (sel) rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE) else null
    Column(Modifier.width(cardW).zIndex(if (here) 2f else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(cardW, cardH)
                // The game window grows out of the card, like on the other layouts.
                .then(if (here) Modifier.focusedCard() else Modifier)
                .graphicsLayer {
                    val s = 1f + (ON_GROW - 1f) * grow
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else { pick?.invoke(); select(real) } } } // (Update 156: pick)
        ) {
            StyleArt(c, g, Modifier.fillMaxSize(), focused = sel, fx = pulse, ring = accent)
        }
        if (big) {
            if (here) {
                // Its name and date, wider than the card (over its neighbours' dates), on a soft pill so it reads.
                Column(
                    Modifier
                        .padding(top = 4.dp)
                        .requiredWidth(capW)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        titles.display(g.name), color = Color.White, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                    Text(
                        listOfNotNull(date, titles.console(g.platform)).joinToString("  ·  "),
                        color = accent, fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                    )
                }
            } else {
                Box(Modifier.height(if (compact) 32.dp else 38.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (date != null) Text(
                        date, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp), style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                }
            }
        }
    }
}

/**
 * The history's keys: ◀ ▶ the previous / next game along the line (on into the period before / after at a row's ends;
 * at the very ends they stay put), ▲ ▼ the period above / below (about the same place in its row); ▲ on the first
 * period the order button (then past it), ▼ on the last what lies below (HomeReach).
 */
internal fun timelineHistoryKey(ctx: StyleKeyCtx): Boolean {
    val order = TimelineOrder.order
    val rank = TimelineOrder.rank
    val starts = TimelineRows.starts
    val ready = ctx.count > 0 && order.size == ctx.count && ctx.focus in rank.indices && starts.isNotEmpty() && starts.last() < order.size
    fun up(): Boolean = if (StyleNav.top.isNotEmpty()) ctx.enterZone(StyleNav.Zone.TOP) else ctx.reach(true)
    // Not drawn yet for this category (the order is from another one): ◀ ▶ the home screen's own this once.
    if (!ready) return when (ctx.key) {
        Key.DirectionUp -> up()
        Key.DirectionDown -> ctx.reach(false)
        else -> false
    }
    val p = rank[ctx.focus]
    val r = timelineRowOf(starts, p)
    val col = p - starts[r]
    // Update 156: each step is added up in places along the line ([d]: -1 / +1 going round), so with Endless the
    // history counts on round instead of racing back to its start.
    fun go(t: Int, d: Int = t - p): Boolean {
        if (t in order.indices && order[t] != ctx.focus) {
            StyleLoop.walked += d
            ctx.setFocus(order[t])
            ctx.sound(UiSound.MOVE)
        }
        return ctx.take()
    }
    fun rowEnd(i: Int): Int = if (i + 1 < starts.size) starts[i + 1] else order.size
    // Update 156: Endless on: ◀ on the first game goes on round to the last, ▶ on the last to the first.
    val loop = StyleLoop.on && order.size >= 2
    return when (ctx.key) {
        Key.DirectionLeft -> if (p > 0) go(p - 1) else if (loop) go(order.size - 1, -1) else ctx.take()
        Key.DirectionRight -> if (p < order.size - 1) go(p + 1) else if (loop) go(0, 1) else ctx.take()
        Key.DirectionUp -> if (r > 0) go((starts[r - 1] + col).coerceAtMost(rowEnd(r - 1) - 1)) else up()
        Key.DirectionDown -> if (r < starts.size - 1) go((starts[r + 1] + col).coerceAtMost(rowEnd(r + 1) - 1))
            // Update 166: Endless on: ▼ from the last year / month goes on round to the first (it stopped there, only ◀ ▶
            // moved on), counted forward so the history carries on instead of racing back.
            else if (loop && starts.size > 1) { val t = (starts[0] + col).coerceAtMost(rowEnd(0) - 1); go(t, order.size - p + t) }
            else ctx.reach(false)
        else -> false
    }
}
