package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Update 146: Home layout › Timeline. The category's games hang along one glowing time line across the middle of the
 * screen, one above it, the next below it, each with a dot on the line and its date beside the dot (Today, Yesterday,
 * 12 Oct, Mar 2024, 1998). Where the month (or, by release year, the decade) changes, a marker sits on the line. The
 * game you're on is bigger, glowing and in the middle; the line glides along under it. Above: its name, console and
 * time played, and at the right the order button (Last played / Release year / Added to Voltcap).
 *
 * The line has its own order, not the category's: [TimelineOrder] keeps both ways round so ◀ ▶ step along the line.
 * ◀ ▶ move along the line, ▲ the order button (A changes the order, ▼ / B back), ▲ again / ▼ what lies beyond (HomeReach).
 * Swipe to scroll, tap a game to pick it and again to open it.
 * Options: Settings › Home › Layout options (Timeline).
 */

/** The line's order (shared with [timelineKey], which runs outside composition): set as the layout draws. */
internal object TimelineOrder {
    /** Place on the line → the game's index in the category. */
    @Volatile var order: IntArray = IntArray(0)
    /** The game's index in the category → its place on the line. */
    @Volatile var rank: IntArray = IntArray(0)
}

/** "No date" (never played, added before Voltcap kept track, release year not known): these go to the far end. */
private const val UNKNOWN = Long.MIN_VALUE

/** How much bigger the game you're on is. */
private const val FOCUS_GROW = 1.22f

/**
 * The line worked out once per category / order: [order] and [rank] as in [TimelineOrder], [stamp] each place's date
 * (epoch ms, or the year by release) and [period] its month / decade, so a marker goes where it changes.
 */
private class TimelineData(val order: IntArray, val rank: IntArray, val stamp: LongArray, val period: IntArray)

private fun timelineData(games: List<Game>, mode: Features.TimelineBy, state: LauncherUiState): TimelineData {
    val n = games.size
    val value = LongArray(n) { i ->
        val k = games[i].stableKey
        when (mode) {
            Features.TimelineBy.LAST_PLAYED -> state.lastPlayedAt[k]?.takeIf { it > 0L } ?: UNKNOWN
            // Voltcap's own "first seen" dates (FirstSeenStore), already loaded off the main thread into the state;
            // 0 there means the game was already here before it kept track.
            Features.TimelineBy.ADDED -> state.firstSeen[k]?.takeIf { it > 0L } ?: UNKNOWN
            Features.TimelineBy.RELEASED -> state.metadata[k]?.releaseYear?.takeIf { it > 0 }?.toLong() ?: UNKNOWN
        }
    }
    // Played / added: the newest first (what you're into now is right at the start). Release: history from the oldest.
    // Same date: the category's own order. No date: at the far end.
    val newestFirst = mode != Features.TimelineBy.RELEASED
    val sorted = (0 until n).sortedWith(Comparator<Int> { a, b ->
        val va = value[a]
        val vb = value[b]
        when {
            va == UNKNOWN && vb == UNKNOWN -> a.compareTo(b)
            va == UNKNOWN -> 1
            vb == UNKNOWN -> -1
            va != vb -> if (newestFirst) vb.compareTo(va) else va.compareTo(vb)
            else -> a.compareTo(b)
        }
    })
    val order = IntArray(n) { sorted[it] }
    val rank = IntArray(n)
    for (p in 0 until n) rank[order[p]] = p
    val stamp = LongArray(n) { value[order[it]] }
    val cal = Calendar.getInstance()
    val period = IntArray(n) { p ->
        val v = stamp[p]
        when {
            v == UNKNOWN -> Int.MIN_VALUE
            mode == Features.TimelineBy.RELEASED -> (v / 10L).toInt()
            else -> { cal.timeInMillis = v; cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH) }
        }
    }
    return TimelineData(order, rank, stamp, period)
}

/** The dates' wording (kept for the layout's life; only used on the main thread). */
private class TimelineText {
    private val cal = Calendar.getInstance()
    private val dayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
    private val monthYear = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    // "LLLL": the month's name on its own (some languages bend "MMMM" as if a day came before it).
    private val month = SimpleDateFormat("LLLL", Locale.getDefault())
    private var today = 0L
    private var yesterday = 0L
    private var thisYear = 0

    /** Where today starts (so a launcher left on overnight still says Today / Yesterday right). */
    fun refresh(now: Long) {
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        today = cal.timeInMillis
        thisYear = cal.get(Calendar.YEAR)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        yesterday = cal.timeInMillis
    }

    private fun unknown(mode: Features.TimelineBy): String = when (mode) {
        Features.TimelineBy.LAST_PLAYED -> "Never played"
        Features.TimelineBy.ADDED -> "Earlier"
        Features.TimelineBy.RELEASED -> "Unknown year"
    }

    private fun yearOf(v: Long): Int { cal.timeInMillis = v; return cal.get(Calendar.YEAR) }

    // Some languages write their month names small: the label always starts with a capital.
    private fun cap(s: String): String = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** The date beside a game's dot. */
    fun date(mode: Features.TimelineBy, v: Long): String = when {
        v == UNKNOWN -> if (mode == Features.TimelineBy.RELEASED) "Unknown" else unknown(mode)
        mode == Features.TimelineBy.RELEASED -> v.toString()
        v >= today -> "Today"
        v >= yesterday -> "Yesterday"
        yearOf(v) == thisYear -> cap(dayMonth.format(Date(v)))
        else -> cap(monthYear.format(Date(v)))
    }

    /** The marker where a new month (this year's by name, older ones with the year) or a new decade starts. */
    fun marker(mode: Features.TimelineBy, v: Long): String = when {
        v == UNKNOWN -> unknown(mode)
        mode == Features.TimelineBy.RELEASED -> "${(v / 10L) * 10L}s"
        yearOf(v) == thisYear -> cap(month.format(Date(v)))
        else -> cap(monthYear.format(Date(v)))
    }
}

private fun nextTimelineBy(mode: Features.TimelineBy): Features.TimelineBy {
    val all = Features.TimelineBy.values()
    return all[(mode.ordinal + 1) % all.size]
}

/** Sizes along the line (dp), worked out from the room there is. */
private class TimelineGeo(
    val cx: Dp, val lineY: Dp, val step: Dp, val cardW: Dp, val cardH: Dp, val lead: Dp, val dateRoom: Dp, val dateW: Dp
)

@Composable
internal fun StyleTimeline(c: StyleCtx, modifier: Modifier = Modifier) {
    val mode = Features.timelineBy.state.value
    // Only the map the order is read from re-sorts the line when it changes.
    val source: Any = when (mode) {
        Features.TimelineBy.LAST_PLAYED -> c.state.lastPlayedAt
        Features.TimelineBy.ADDED -> c.state.firstSeen
        Features.TimelineBy.RELEASED -> c.state.metadata
    }
    val data = remember(c.games, mode, source) { timelineData(c.games, mode, c.state) }
    // A part hidden in Edit Home is skipped by the D-pad.
    val orderOff = LayoutParts.off(HomeLayout.TIMELINE, "order")
    SideEffect {
        TimelineOrder.order = data.order
        TimelineOrder.rank = data.rank
        StyleNav.side = emptyList()
        StyleNav.bar = emptyList()
        // ▲: the order button (A goes on to the next order).
        StyleNav.top = if (orderOff) emptyList() else listOf({ Features.timelineBy.value = nextTimelineBy(Features.timelineBy.value) })
        StyleNav.topStart = 0
    }
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        Column(Modifier.fillMaxSize()) {
            TimelineHeader(c, mode, compact)
            // Plain if / else, never an early return out of the Column.
            if (c.game == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                TimelineLine(c, data, mode, compact, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** Above the line: the game's name, its console and time played; the order button at the right. */
@Composable
private fun TimelineHeader(c: StyleCtx, mode: Features.TimelineBy, compact: Boolean) {
    val titles = LocalTitleOptions.current
    val g = c.game
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = if (compact) 2.dp else 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f).editablePart(LayoutParts.id(HomeLayout.TIMELINE, "title"), "Name & time played")) {
            if (g != null) AnimatedContent(targetState = g, label = "timelineTitle", transitionSpec = { gameSwap() }) { shown ->
                Column(Modifier.fillMaxWidth()) {
                    FitText(
                        titles.display(shown.name), Color.White, maxSize = if (compact) 18.sp else 24.sp, minSize = 12.sp,
                        maxLines = 1, fontWeight = FontWeight.Bold, style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                    Text(
                        titles.console(shown.platform) + "  ·  " + stylePlaytime(c.playtime(shown), c.lastPlayed(shown) != null),
                        color = Color.White.copy(alpha = 0.82f), fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.editablePart(LayoutParts.id(HomeLayout.TIMELINE, "order"), "Order button")) { TimelineOrderChip(mode, compact) }
    }
}

/** What the line is ordered by; A on it (▲ from the games) or a tap goes on to the next order. */
@Composable
private fun TimelineOrderChip(mode: Features.TimelineBy, compact: Boolean) {
    val on = StyleNav.on(StyleNav.Zone.TOP, 0)
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(Color.Black.copy(alpha = if (on) 0.62f else 0.4f))
            .border(if (on) 2.dp else 1.dp, if (on) accent else Color.White.copy(alpha = 0.25f), shape)
            // (A tap leaves the D-pad on the games, so the game you're on keeps its glow.)
            .tapOnly { Features.timelineBy.value = nextTimelineBy(Features.timelineBy.value) }
            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Schedule, null, tint = accent, modifier = Modifier.size(if (compact) 13.dp else 15.dp))
        Spacer(Modifier.width(6.dp))
        Text(mode.label, color = Color.White, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/**
 * The line itself: only the games near the screen are composed (a window round the one you're on); everything moves
 * with one [Animatable] read in the layout / draw phase, so a glide redraws without recomposing every frame, and
 * nothing runs while you rest.
 */
@Composable
private fun TimelineLine(c: StyleCtx, data: TimelineData, mode: Features.TimelineBy, compact: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val latestData by rememberUpdatedState(data)
    val latestFocus by rememberUpdatedState(c.focus)
    val glide = Features.rowGlide.state.value
    val n = data.order.size
    val target = data.rank.getOrElse(c.focus) { 0 }
    val pos = remember { Animatable(target.toFloat()) }
    // The line it last glided on, so a new line (category, order) is never glided across from the old one's place.
    val shownData = remember { arrayOfNulls<TimelineData>(1) }
    LaunchedEffect(target, data) {
        val newLine = shownData[0] !== data
        shownData[0] = data
        // A new line or a long jump (the far end): straight there rather than racing along the line.
        if (newLine || abs(pos.value - target) > 8f) pos.snapTo(target.toFloat())
        else pos.animateTo(target.toFloat(), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
    }
    // The window only changes when the line has moved a whole game.
    val center by remember { derivedStateOf { pos.value.roundToInt() } }
    val keys = remember(c.games) { gameKeys(c.games) }
    val text = remember { TimelineText() }
    text.refresh(System.currentTimeMillis())
    val accent = ShelfTheme.accent
    val dates = Features.timelineDates.state.value
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    // The drag reads the step from here (the drag handler is never restarted for a size).
    val stepPx = remember { floatArrayOf(1f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            var acc = 0f
            var cur = 0
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f; cur = latestData.rank.getOrElse(latestFocus) { 0 } },
                onDragEnd = { acc = 0f },
                onDragCancel = { acc = 0f }
            ) { change, dx ->
                change.consume()
                acc += dx
                val d = latestData
                val s = (stepPx[0] * 0.8f).coerceAtLeast(24.dp.toPx())
                // Dragging left brings the later games in, like scrolling the line.
                while (acc <= -s) { acc += s; if (cur + 1 < d.order.size) { cur++; select(d.order[cur]) } }
                while (acc >= s) { acc -= s; if (cur > 0) { cur--; select(d.order[cur]) } }
            }
        }.categorySlide()
    ) {
        val k = GameSize.scale(HomeLayout.TIMELINE)
        val aspect = styleAspect(c.type).coerceIn(0.13f, 1.8f)
        val lead = if (compact) 10.dp else 14.dp
        val dateRoom = if (dates) (if (compact) 16.dp else 18.dp) else 0.dp
        // A card (grown, for the game you're on) with its date and stem fits in half the height; never so wide that
        // fewer than about four games show across a small screen.
        val avail = (maxHeight / 2f - lead - dateRoom - 4.dp).coerceAtLeast(30.dp)
        val cardH = minOf(avail / FOCUS_GROW * 0.88f * k, avail / FOCUS_GROW, maxWidth * 0.24f / aspect).coerceAtLeast(28.dp)
        val cardW = cardH * aspect
        // One above, the next below: neighbours on the same side are two steps apart, so they can overlap a little sideways.
        val step = maxOf(cardW * 0.64f, if (compact) 50.dp else 56.dp)
        val geo = TimelineGeo(
            cx = maxWidth / 2f, lineY = maxHeight / 2f, step = step, cardW = cardW, cardH = cardH,
            lead = lead, dateRoom = dateRoom, dateW = step * 1.9f
        )
        SideEffect { stepPx[0] = with(density) { step.toPx() } }
        val half = (maxWidth / 2f / step).toInt() + 2
        val first = (center - half).coerceAtLeast(0)
        val last = (center + half).coerceAtMost(n - 1)

        // The line and the period ticks (drawn every frame only while the line glides). The stems and dots are drawn
        // after the markers' names, so a name never hides a game's dot on a narrow screen.
        Canvas(Modifier.fillMaxSize()) {
            val y = geo.lineY.toPx()
            val sp = step.toPx()
            val ax = geo.cx.toPx()
            val p0 = pos.value
            // The line runs from a little before the first game to a little after the last, fading at the screen's edges.
            val startX = (ax + (0 - p0) * sp - sp).coerceAtLeast(0f)
            val endX = (ax + (n - 1 - p0) * sp + sp).coerceAtMost(size.width)
            if (endX > startX) {
                val fade = Brush.horizontalGradient(
                    0f to Color.Transparent, 0.07f to accent, 0.93f to accent, 1f to Color.Transparent,
                    startX = 0f, endX = size.width
                )
                drawLine(fade, Offset(startX, y), Offset(endX, y), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round, alpha = 0.12f)
                drawLine(fade, Offset(startX, y), Offset(endX, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round, alpha = 0.28f)
                drawLine(fade, Offset(startX, y), Offset(endX, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round, alpha = 0.9f)
            }
            for (p in first..last) {
                // A tick across the line where a new month / decade starts (its name is drawn over it below; the
                // tick peeks out above and below the name).
                if (p == 0 || data.period[p] != data.period[p - 1]) {
                    val xm = ax + (p - p0) * sp - sp / 2f
                    drawLine(accent.copy(alpha = 0.75f), Offset(xm, y - 13.dp.toPx()), Offset(xm, y + 13.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        // The markers' names, on the line between the two games where the period changes.
        for (p in first..last) {
            if (p == 0 || data.period[p] != data.period[p - 1]) key("m", p) {
                TimelineMarker(text.marker(mode, data.stamp[p]), p, pos, geo, accent)
            }
        }
        // The stems and the dots, over the markers' names.
        Canvas(Modifier.fillMaxSize()) {
            val y = geo.lineY.toPx()
            val sp = step.toPx()
            val ax = geo.cx.toPx()
            val p0 = pos.value
            val leadPx = lead.toPx()
            for (p in first..last) {
                val x = ax + (p - p0) * sp
                val up = p % 2 == 0
                val on = p == target
                // The stem from the dot to the card.
                drawLine(
                    if (on) accent else Color.White.copy(alpha = 0.35f), Offset(x, y), Offset(x, if (up) y - leadPx else y + leadPx),
                    strokeWidth = (if (on) 3.dp else 1.5.dp).toPx()
                )
                if (on) {
                    drawCircle(accent.copy(alpha = 0.3f), radius = 13.dp.toPx(), center = Offset(x, y))
                    drawCircle(accent, radius = 8.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(x, y))
                } else {
                    drawCircle(accent, radius = 4.5.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.Black.copy(alpha = 0.55f), radius = 1.8.dp.toPx(), center = Offset(x, y))
                }
            }
        }
        // The games.
        for (p in first..last) {
            val real = data.order[p]
            val g = c.games.getOrNull(real)
            if (g != null) key(keys[real]) {
                val on = p == target
                TimelineCard(
                    c, g, real, p, on = on, sel = on && inGames, pos = pos, geo = geo,
                    date = if (dates) text.date(mode, data.stamp[p]) else null, accent = accent
                )
            }
        }
    }
}

/** A month / decade name on the line. */
@Composable
private fun TimelineMarker(label: String, p: Int, pos: Animatable<Float, AnimationVector1D>, geo: TimelineGeo, accent: Color) {
    val w = 120.dp
    val h = 18.dp
    Box(
        Modifier
            .offset { IntOffset((geo.cx + geo.step * (p - pos.value) - geo.step / 2f - w / 2f).roundToPx(), (geo.lineY - h / 2f).roundToPx()) }
            .size(w, h),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f))
                .border(1.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

/**
 * A game on the line: its card art (the layout's artwork) above or below the line and its date by the dot. The game
 * you're on grows away from the line and glows (the layout's focus effect). Tap: pick it; tap the one you're on: open it.
 */
@Composable
private fun TimelineCard(
    c: StyleCtx, g: Game, real: Int, p: Int, on: Boolean, sel: Boolean,
    pos: Animatable<Float, AnimationVector1D>, geo: TimelineGeo, date: String?, accent: Color
) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val glide = Features.rowGlide.state.value
    val grow by animateFloatAsState(
        if (on) 1f else 0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness), label = "timelineGrow"
    )
    // The focus pulse only on the game you're on, so resting re-draws that one card alone.
    val pulse: Float? = if (sel) rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE) else null
    val up = p % 2 == 0
    Box(
        Modifier
            .offset {
                IntOffset(
                    (geo.cx + geo.step * (p - pos.value) - geo.cardW / 2f).roundToPx(),
                    (if (up) geo.lineY - geo.lead - geo.dateRoom - geo.cardH else geo.lineY + geo.lead + geo.dateRoom).roundToPx()
                )
            }
            .size(geo.cardW, geo.cardH)
            .zIndex(if (on) 2f else 1f)
            // The game window grows out of the card, like on the other layouts.
            .then(if (on) Modifier.focusedCard() else Modifier)
            .graphicsLayer {
                val s = 1f + (FOCUS_GROW - 1f) * grow
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, if (up) 1f else 0f)
                alpha = 0.78f + 0.22f * grow
            }
            .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else select(real) } }
    ) {
        StyleArt(c, g, Modifier.fillMaxSize(), focused = sel, fx = pulse, ring = accent)
    }
    if (date != null) Box(
        Modifier
            .offset {
                IntOffset(
                    (geo.cx + geo.step * (p - pos.value) - geo.dateW / 2f).roundToPx(),
                    (if (up) geo.lineY - geo.lead - geo.dateRoom else geo.lineY + geo.lead).roundToPx()
                )
            }
            .size(geo.dateW, geo.dateRoom)
            .zIndex(if (on) 2f else 1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            date, color = if (on) accent else Color.White.copy(alpha = 0.72f), fontSize = if (on) 11.sp else 10.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = nameTextStyle(NameBackdrop.SHADOW)
        )
    }
}

/**
 * ◀ ▶ go to the previous / next game along the line (its own order, not the category's); at either end they stay put.
 * ▲ the order button (then past it), ▼ what lies below (HomeReach).
 */
internal fun timelineKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionUp -> if (StyleNav.top.isNotEmpty()) ctx.enterZone(StyleNav.Zone.TOP) else ctx.reach(true)
    Key.DirectionDown -> ctx.reach(false)
    Key.DirectionLeft, Key.DirectionRight -> {
        val order = TimelineOrder.order
        val rank = TimelineOrder.rank
        // Not drawn yet for this category (the order is from another one): the home screen's own ◀ ▶ this once.
        if (ctx.count <= 0 || order.size != ctx.count || ctx.focus !in rank.indices) {
            false
        } else {
            val t = rank[ctx.focus] + if (ctx.key == Key.DirectionRight) 1 else -1
            if (t in order.indices && order[t] != ctx.focus) {
                ctx.setFocus(order[t])
                ctx.sound(UiSound.MOVE)
            }
            ctx.take()
        }
    }
    else -> false
}

@Composable
internal fun TimelineSettings() {
    MutedNote("Your games along a glowing time line across the middle of the screen, one above it, the next below it, with the date by each one and a marker where a new month (or decade) starts. The game you're on is bigger and in the middle, its name, console and time played above. ◀ ▶ move along the line, ▲ the order button (A changes the order). Swipe to scroll; tap a game to pick it, tap it again to open it.")
    Spacer(Modifier.height(10.dp))
    val current = Features.timelineBy.state.value
    Text("Order along the line", color = ShelfTheme.textMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    ChipFlow { Features.TimelineBy.values().forEach { v -> Chip(v.label, accent = v == current) { Features.timelineBy.value = v } } }
    Spacer(Modifier.height(6.dp))
    MutedNote(
        when (current) {
            Features.TimelineBy.LAST_PLAYED -> "The game you played last comes first; games you've never played wait at the far end."
            Features.TimelineBy.RELEASED -> "From the oldest game to the newest, with a marker at each new decade. Games without a known release year wait at the far end."
            Features.TimelineBy.ADDED -> "The newest arrivals first. Games that were already here before Voltcap kept track wait at the far end."
        }
    )
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        Icons.Filled.Label, "Dates on the line",
        "By each game's dot: Today, Yesterday, 12 Oct, Mar 2024, or its release year.",
        Features.timelineDates.state.value, { Features.timelineDates.value = it }
    )
    Spacer(Modifier.height(8.dp))
}
