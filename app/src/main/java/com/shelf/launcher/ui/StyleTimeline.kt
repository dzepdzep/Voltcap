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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
 *
 * Update 151: no longer one line across the middle (it looked like the Circuit board): a vertical history instead —
 * year headings (and months), a row of each period's games under them (StyleTimelineHistory.kt). This file keeps the
 * order (TimelineOrder, timelineData), the date wording, the header with the order button and the options.
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

/**
 * The line worked out once per category / order: [order] and [rank] as in [TimelineOrder], [stamp] each place's date
 * (epoch ms, or the year by release) and [period] its month / decade, so a marker goes where it changes.
 */
// (Update 151: internal, for the history in StyleTimelineHistory.kt.)
internal class TimelineData(val order: IntArray, val rank: IntArray, val stamp: LongArray, val period: IntArray)

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

/** The dates' wording (kept for the layout's life; only used on the main thread). (Update 151: internal, for the history.) */
internal class TimelineText {
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
    // Update 152: a soft shade from the left, so the headings, names and dates read over a bright, busy background
    // picture (on the Pocket Classic they were lost in it); it fades out before the middle.
    BoxWithConstraints(
        modifier
            .drawBehind {
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                        0.45f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.28f),
                        0.8f to androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        val compact = maxHeight < 380.dp
        Column(Modifier.fillMaxSize()) {
            TimelineHeader(c, mode, compact)
            // Plain if / else, never an early return out of the Column.
            if (c.game == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                // Update 151: the history (periods down the screen, each a row of games), not the line across it.
                // (Update 261, #120 Thor: cut to its own area — scrolled down a period, the year heading above slid up
                // under the header's name and time and showed through it.)
                TimelineHistory(c, data, mode, compact, Modifier.weight(1f).fillMaxWidth().clipToBounds())
            }
        }
    }
}

/** Above the history: the category, the game's name and time played; the order button at the right. */
@Composable
private fun TimelineHeader(c: StyleCtx, mode: Features.TimelineBy, compact: Boolean) {
    val titles = LocalTitleOptions.current
    val g = c.game
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = if (compact) 2.dp else 6.dp), verticalAlignment = Alignment.Top) {
        // Update 151: the game's name and date are under it in the history now; up here its time played (and the
        // category), one short line.
        Box(Modifier.weight(1f).editablePart(LayoutParts.id(HomeLayout.TIMELINE, "title"), "Name & time played")) {
            Column(Modifier.fillMaxWidth()) {
                // (Update 315: off for this layout — Categories tile / Settings — no caption.)
                if (CategoryTitle.captionOn(c.state.appearance)) {
                CategoryTitle.Native(LayoutParts.id(HomeLayout.TIMELINE, "title"))
                Text(
                    c.categoryLabel, color = Color.White, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
                )
                }
                if (g != null) Text(
                    titles.display(g.name) + "  ·  " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                    color = Color.White.copy(alpha = 0.75f), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, softWrap = false,
                    overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
                )
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
 * Update 151: the history's keys (StyleTimelineHistory.kt): ◀ ▶ along the line (its own order, not the category's), on
 * into the next / previous period at a row's ends; ▲ ▼ between periods; ▲ on the first the order button (then past it),
 * ▼ on the last what lies below (HomeReach).
 */
internal fun timelineKey(ctx: StyleKeyCtx): Boolean = timelineHistoryKey(ctx)

@Composable
internal fun TimelineSettings() {
    // Update 151: the history (periods down the screen) instead of the line across it.
    MutedNote("Your games as a history: big year headings (and months, by last played or added) down a time line on the left, each with a row of that time's games. The row you're in is bigger; the game you're on has its name and date under it. ▲ ▼ move between years / months, ◀ ▶ along a row, ▲ at the top the order button (A changes the order). Swipe to scroll; tap a game to pick it, tap it again to open it.")
    Spacer(Modifier.height(10.dp))
    val current = Features.timelineBy.state.value
    Text("Order", color = ShelfTheme.textMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    ChipFlow { Features.TimelineBy.values().forEach { v -> Chip(v.label, accent = v == current) { Features.timelineBy.value = v } } }
    Spacer(Modifier.height(6.dp))
    MutedNote(
        when (current) {
            Features.TimelineBy.LAST_PLAYED -> "The game you played last comes first; games you've never played wait at the far end."
            Features.TimelineBy.RELEASED -> "From the oldest game to the newest, a row for each year. Games without a known release year wait at the far end."
            Features.TimelineBy.ADDED -> "The newest arrivals first. Games that were already here before Voltcap kept track wait at the far end."
        }
    )
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        Icons.Filled.Label, "Dates on the line",
        "Under each game in the row you're in: Today, Yesterday, 12 Oct, Mar 2024, or its release year.",
        Features.timelineDates.state.value, { Features.timelineDates.value = it }
    )
    Spacer(Modifier.height(8.dp))
}
