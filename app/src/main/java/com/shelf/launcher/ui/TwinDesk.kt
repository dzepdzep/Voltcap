package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 158: Twin desk, a two-screen layout (see TwinScreens.kt). The idea of a two-screen handheld's own home menu,
 * drawn in Voltcap's style:
 * - top (main) screen: a desk on squared paper — a big clock (a square face with hour marks and three hands, or big
 *   digits) and this month's calendar (weekends tinted, today ringed), the game you're on named under them with its
 *   console and time played;
 * - bottom (second) screen: your games as wide rounded panels in a list (picture on the left, name and console on the
 *   right, the one you're on framed by corner brackets in the theme's colour), two smaller panels under the list for
 *   the next two games, and a slim bar of round buttons (quick settings, Settings, screensaver).
 * ▲ ▼ move through the games (round at the end with Endless), ▶ the round buttons (◀ ▶ along them, ▲ or B back).
 * Touch: tap a panel to pick it, tap the one you're on to open it, swipe the list up / down.
 */

private val DeskPaper = Color(0xFFF5F6F1)
private val DeskGrid = Color(0xFFD9E1E7)
private val DeskInk = Color(0xFF26313C)
private val DeskMuted = Color(0xFF6A7682)
private val DeskEdge = Color(0xFFC6CFD7)
private val DeskSat = Color(0xFF2F74C9)
private val DeskSun = Color(0xFFD5473F)
private val DeskBack = listOf(Color(0xFFEEF1F4), Color(0xFFDCE2E8))

@Composable
internal fun StyleTwinDesk(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        StyleLoop.on = loopRowOn(c)
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // The round buttons along the bottom of the lower screen.
        // (Update 180: and the second screen's dashboard, while the bottom part is on the second screen.)
        StyleNav.bar = listOf(c.actions.quickMenu, c.actions.settings, c.actions.screensaver) +
            if (twinOnSecond(c.state)) listOf({ TwinScreens.dashboard = true }) else emptyList()
    }
    TwinHost(
        c, modifier,
        top = { m, framed -> TwinDeskTop(c, m, framed) },
        bottom = { m, framed -> TwinDeskBottom(c, m, framed) }
    )
}

/** Update 158: ▲ ▼ (and ◀) through the games (round at the end with Endless), ▶ the round buttons. */
internal fun twinDeskKey(ctx: StyleKeyCtx): Boolean {
    val n = ctx.count
    val loop = StyleLoop.on && n >= 2
    fun go(target: Int, d: Int): Boolean {
        if (target != ctx.focus && target in 0 until n) {
            StyleLoop.walked += d
            ctx.setFocus(target)
            ctx.sound(UiSound.MOVE)
        }
        return ctx.take()
    }
    return when (ctx.key) {
        Key.DirectionUp -> when {
            n == 0 -> ctx.reach(true)
            ctx.focus > 0 -> go(ctx.focus - 1, -1)
            loop -> go(n - 1, -1)
            else -> ctx.reach(true)
        }
        Key.DirectionDown -> when {
            n > 0 && ctx.focus < n - 1 -> go(ctx.focus + 1, 1)
            n > 0 && loop -> go(0, 1)
            StyleNav.bar.isNotEmpty() -> ctx.enterZone(StyleNav.Zone.BAR)
            else -> ctx.reach(false)
        }
        Key.DirectionRight -> if (StyleNav.bar.isNotEmpty()) ctx.enterZone(StyleNav.Zone.BAR) else ctx.take()
        // (Review: ◀ is the previous game here too, so its step round the end is counted like ▲'s; with two games the
        // home screen's own ◀ slid the list the wrong way. At the start without Endless: the home screen's bump.)
        Key.DirectionLeft -> when {
            n > 0 && ctx.focus > 0 -> go(ctx.focus - 1, -1)
            loop -> go(n - 1, -1)
            else -> false
        }
        else -> false
    }
}

@Composable
internal fun TwinDeskSettings() {
    MutedNote("Made for handhelds with two screens. The top screen is a desk: a clock and this month's calendar on squared paper, with the game you're on under them. The bottom screen lists your games as wide panels, the next two under them, and round buttons for quick settings, Settings and the screensaver. ▲ ▼ move through the games, ▶ goes to the round buttons. With one screen, both halves show together.")
    Spacer(Modifier.height(10.dp))
    // Update 180: what the top screen shows (one thing alone sits in the middle, bigger).
    SectionLabel("Top screen shows")
    ChipFlow {
        listOf(
            "Clock" to Features.twinDeskClock, "Calendar" to Features.twinCalendar,
            "Game's picture" to Features.twinDeskArt, "Game's name" to Features.twinDeskGame
        ).forEach { (label, pref) ->
            val on = pref.state.value
            Chip((if (on) "✓ " else "") + label, accent = on) { pref.value = !on }
        }
    }
    MutedNote("Pick any of them. Only one: it sits in the middle of the top screen, as big as fits. None: the clock.")
    Spacer(Modifier.height(8.dp))
    StyleChoice("Clock", Features.TwinClock.values(), Features.twinClock.state.value, { it.label }) { Features.twinClock.value = it }
    // Update 179.
    StyleChoice("Gap between panels", TWIN_GAPS.filter { it <= 24 }.toTypedArray(), Features.twinDeskGap.state.value, { gapLabel(it) }) { Features.twinDeskGap.value = it }
    TwinFullTopSwitch()
    Spacer(Modifier.height(8.dp))
    MutedNote("The bottom half goes to the second screen when Settings › Screens › Use the second screen is on.")
}

// ---- Top screen: the desk ----

@Composable
private fun TwinDeskTop(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(if (framed) 14.dp else 18.dp)
    // Update 180: on two screens the desk can fill the whole top screen (its paper drawn behind everything, the theme's
    // background off — TwinScreens.fullTop): then no rounded sheet of its own.
    val full = !framed && TwinScreens.fullTop
    // The second hand moves once a second, only while the home screen isn't idle.
    val ticking = !HomeIdle.idle
    val analogue = Features.twinClock.state.value == Features.TwinClock.ANALOGUE
    // Update 180: what the top shows is your choice (Layout options › Top screen shows): clock, calendar, the game's
    // picture, the game's name. One thing alone sits in the middle, bigger.
    val clockOn = Features.twinDeskClock.state.value
    val calendarOn = Features.twinCalendar.state.value
    val artOn = Features.twinDeskArt.state.value
    val gameOn = Features.twinDeskGame.state.value
    val count = listOf(clockOn, calendarOn, artOn, gameOn).count { it }
    val time = rememberTwinTime(seconds = ticking)
    val tz = remember { java.util.TimeZone.getDefault() }
    BoxWithConstraints(if (full) modifier else modifier.clip(shape).deskPaper().border(1.dp, DeskEdge, shape)) {
        val compact = maxHeight < 230.dp
        val pad = if (compact) 10.dp else 16.dp
        val stripH = if (compact) 42.dp else 56.dp
        val mw = maxWidth
        val mh = maxHeight
        if (count <= 1) {
            // One thing (or none: the clock): in the middle, as big as fits.
            val side = minOf(mw, mh) * 0.78f
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                when {
                    calendarOn -> DeskCalendar(time, tz, accent,
                        Modifier.width(minOf(mw * 0.8f, mh * 1.3f)).height(mh * 0.82f).editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "calendar"), "Calendar"))
                    artOn -> DeskPicture(c, Modifier.width(minOf(mw * 0.8f, mh * 1.5f)).height(minOf(mh * 0.8f, mw * 0.45f))
                        .editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "picture"), "Game's picture"))
                    gameOn -> DeskNameStrip(c, compact, Modifier.fillMaxWidth(0.82f).height(stripH * 1.4f).editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "game"), "Game name"))
                    else -> Box(Modifier.size(side).editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "clock"), "Clock"), contentAlignment = Alignment.Center) {
                        if (analogue) DeskAnalogue(time, tz, ticking, accent, Modifier.fillMaxSize())
                        else DeskDigital(time, tz, ticking, accent, side)
                    }
                }
            }
        } else {
            val rowOn = clockOn || calendarOn || artOn
            val rowH = if (gameOn) (maxHeight - pad * 2 - stripH - pad).coerceAtLeast(40.dp) else (maxHeight - pad * 2).coerceAtLeast(40.dp)
            val wide = calendarOn || artOn
            val clockSide = if (wide) minOf(rowH, (maxWidth - pad * 3) * 0.42f) else rowH
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
                if (rowOn) Row(
                    Modifier.fillMaxWidth().height(rowH),
                    horizontalArrangement = Arrangement.spacedBy(pad, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (clockOn) Box(
                        Modifier.size(clockSide).editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "clock"), "Clock"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (analogue) DeskAnalogue(time, tz, ticking, accent, Modifier.fillMaxSize())
                        else DeskDigital(time, tz, ticking, accent, clockSide)
                    }
                    if (calendarOn) DeskCalendar(
                        time, tz, accent,
                        Modifier.weight(1f).fillMaxHeight().editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "calendar"), "Calendar")
                    )
                    if (artOn) DeskPicture(c, Modifier.weight(1f).fillMaxHeight().editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "picture"), "Game's picture"))
                }
                if (rowOn && gameOn) Spacer(Modifier.height(pad))
                if (gameOn) DeskNameStrip(c, compact, Modifier.fillMaxWidth().height(stripH).editablePart(LayoutParts.id(HomeLayout.TWIN_DESK, "game"), "Game name"))
            }
        }
    }
}

/** Update 180: the game's picture on the desk, like a photo pinned to it (white border, a soft shadow). */
@Composable
private fun DeskPicture(c: StyleCtx, modifier: Modifier) {
    val g = c.game
    val open by rememberUpdatedState(c.actions.open)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.clip(shape).background(Color.White).border(1.dp, DeskEdge, shape).padding(6.dp)
            .tapOnly { if (c.game != null) open() }
    ) {
        val url = g?.let { c.wide(it) }
        if (g != null && url != null) HeldImage(url, Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)), imageModifier = Modifier.pixelArtwork())
        else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(g?.let { placeholderBrush(it.hue) } ?: androidx.compose.ui.graphics.SolidColor(DeskGrid)))
    }
}

/** Update 180: the desk's paper across the whole top screen (behind the status bar too), when the top fills it. */
@Composable
internal fun TwinDeskBackdrop(modifier: Modifier) {
    Box(modifier.deskPaper())
}

/** Squared paper (drawn once per size). */
private fun Modifier.deskPaper(): Modifier = this.drawWithCache {
    val step = 14.dp.toPx()
    val line = 1.dp.toPx().coerceAtLeast(1f)
    onDrawBehind {
        drawRect(DeskPaper)
        var x = step
        while (x < size.width) {
            drawLine(DeskGrid, Offset(x, 0f), Offset(x, size.height), line)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(DeskGrid, Offset(0f, y), Offset(size.width, y), line)
            y += step
        }
    }
}

/** Seconds since midnight, local time. */
private fun deskDaySeconds(t: Long, tz: java.util.TimeZone): Int {
    val local = t + tz.getOffset(t)
    return (((local / 1000L) % 86_400L + 86_400L) % 86_400L).toInt()
}

/**
 * The clock with hands: a square face with hour marks, the hour and minute hands, and the second hand in the theme's
 * colour while [seconds] (hidden while idle, when the time only moves once a minute). Its own layer: only it redraws.
 */
@Composable
private fun DeskAnalogue(time: LongState, tz: java.util.TimeZone, seconds: Boolean, accent: Color, modifier: Modifier) {
    Spacer(
        modifier.graphicsLayer {}.drawWithCache {
            val side = size.minDimension
            val r = side / 2f
            // (CacheDrawScope has no center of its own: the middle from the size.)
            val faceTL = Offset(size.width / 2f - r, size.height / 2f - r)
            val faceSize = Size(side, side)
            val corner = CornerRadius(side * 0.18f)
            val rim = Stroke(side * 0.025f)
            val shadowTL = Offset(faceTL.x, faceTL.y + side * 0.025f)
            onDrawBehind {
                drawRoundRect(Color(0x1F000000), shadowTL, faceSize, corner)
                drawRoundRect(Color.White, faceTL, faceSize, corner)
                drawRoundRect(DeskEdge, faceTL, faceSize, corner, style = rim)
                for (i in 0 until 12) {
                    val a = i * (Math.PI / 6.0).toFloat()
                    val sx = sin(a)
                    val sy = -cos(a)
                    val quarter = i % 3 == 0
                    val r0 = r * (if (quarter) 0.66f else 0.74f)
                    val r1 = r * 0.84f
                    drawLine(
                        if (quarter) DeskInk else DeskMuted,
                        Offset(center.x + sx * r0, center.y + sy * r0), Offset(center.x + sx * r1, center.y + sy * r1),
                        side * (if (quarter) 0.034f else 0.018f), StrokeCap.Round
                    )
                }
                val day = deskDaySeconds(time.longValue, tz)
                val s = day % 60
                val m = (day / 60) % 60
                val h = (day / 3600) % 12
                deskHand(h * 30f + m * 0.5f, r * 0.46f, r * 0.08f, side * 0.05f, DeskInk)
                deskHand(m * 6f + s * 0.1f, r * 0.7f, r * 0.1f, side * 0.032f, DeskInk)
                if (seconds) deskHand(s * 6f, r * 0.78f, r * 0.16f, side * 0.014f, accent)
                drawCircle(DeskInk, side * 0.04f, center)
                drawCircle(accent, side * 0.02f, center)
            }
        }
    )
}

/** One hand at [deg] (0 = twelve o'clock), [len] long, with a short tail. */
private fun DrawScope.deskHand(deg: Float, len: Float, tail: Float, width: Float, color: Color) {
    val a = Math.toRadians(deg.toDouble()).toFloat()
    val sx = sin(a)
    val sy = -cos(a)
    drawLine(color, Offset(center.x - sx * tail, center.y - sy * tail), Offset(center.x + sx * len, center.y + sy * len), width, StrokeCap.Round)
}

/** The clock as big digits, the seconds small beside them while [seconds]. Its own layer: only it redraws. */
@Composable
private fun DeskDigital(time: LongState, tz: java.util.TimeZone, seconds: Boolean, accent: Color, side: Dp) {
    val context = LocalContext.current
    val h24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val density = LocalDensity.current
    val day = deskDaySeconds(time.longValue, tz)
    val h = day / 3600
    val m = (day / 60) % 60
    val s = day % 60
    val shown = if (h24) h else (h % 12).let { if (it == 0) 12 else it }
    val big = with(density) { (side * 0.22f).toSp() }
    val small = with(density) { (side * 0.09f).toSp() }
    Column(
        Modifier.graphicsLayer {}.clip(RoundedCornerShape(side * 0.14f)).background(Color.White).border(2.dp, DeskEdge, RoundedCornerShape(side * 0.14f))
            .padding(horizontal = side * 0.06f, vertical = side * 0.08f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                (if (h24) "%02d:%02d" else "%d:%02d").format(shown, m), color = DeskInk, fontSize = big,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
            )
            if (seconds) Text(":%02d".format(s), color = accent, fontSize = small, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        if (!h24) Text(if (h < 12) "AM" else "PM", color = DeskMuted, fontSize = small, fontWeight = FontWeight.SemiBold)
    }
}

/** This month: its name, the weekdays, the days (weekends tinted, today ringed). Built again only when the day changes. */
@Composable
private fun DeskCalendar(time: LongState, tz: java.util.TimeZone, accent: Color, modifier: Modifier) {
    val dayNumber by remember(time, tz) {
        derivedStateOf { val t = time.longValue; Math.floorDiv(t + tz.getOffset(t), 86_400_000L) }
    }
    val month = remember(dayNumber) { deskMonth() }
    val shape = RoundedCornerShape(10.dp)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(shape).background(Color.White.copy(alpha = 0.8f)).border(1.dp, DeskEdge, shape).padding(6.dp)) {
        val lines = month.rows + 2
        val cellH = maxHeight / lines
        val cellW = maxWidth / 7
        val fs = with(density) { (minOf(cellH, cellW) * 0.44f).toSp() }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(cellH), contentAlignment = Alignment.CenterStart) {
                Text(month.title, color = DeskInk, fontSize = fs * 1.15f, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
            Row(Modifier.fillMaxWidth().height(cellH)) {
                for (col in 0 until 7) {
                    Box(Modifier.width(cellW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            month.heads[col], fontSize = fs * 0.9f, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            color = when (col) { month.sat -> DeskSat; month.sun -> DeskSun; else -> DeskMuted }
                        )
                    }
                }
            }
            for (r in 0 until month.rows) {
                Row(Modifier.fillMaxWidth().height(cellH)) {
                    for (col in 0 until 7) {
                        val d = month.cells[r * 7 + col]
                        val weekend = col == month.sat || col == month.sun
                        Box(
                            Modifier.width(cellW).fillMaxHeight()
                                .background(if (weekend) (if (col == month.sat) DeskSat else DeskSun).copy(alpha = 0.07f) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (d > 0) {
                                if (d == month.today) Box(Modifier.size(minOf(cellW, cellH) * 0.92f).clip(CircleShape).background(accent.copy(alpha = 0.16f)).border(1.5.dp, accent, CircleShape))
                                Text(
                                    "$d", fontSize = fs, maxLines = 1,
                                    fontWeight = if (d == month.today) FontWeight.Bold else FontWeight.Normal,
                                    color = when (col) { month.sat -> DeskSat; month.sun -> DeskSun; else -> DeskInk }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A month laid out for the calendar: [cells] has the day in each place (0 = empty), weeks starting as the locale does. */
private class DeskMonth(val title: String, val heads: List<String>, val sat: Int, val sun: Int, val cells: IntArray, val rows: Int, val today: Int)

private fun deskMonth(): DeskMonth {
    val locale = java.util.Locale.getDefault()
    val cal = java.util.Calendar.getInstance(locale)
    val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val title = runCatching {
        java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "LLLLyyyy"), locale).format(cal.time)
    }.getOrDefault("").replaceFirstChar { it.titlecase(locale) }
    val fdw = cal.firstDayOfWeek
    cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val offset = (cal.get(java.util.Calendar.DAY_OF_WEEK) - fdw + 7) % 7
    val days = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val rows = (offset + days + 6) / 7
    val cells = IntArray(rows * 7)
    for (d in 1..days) cells[offset + d - 1] = d
    val names = java.text.DateFormatSymbols.getInstance(locale).shortWeekdays
    val heads = (0 until 7).map { col ->
        val dow = (fdw - 1 + col) % 7 + 1
        names.getOrNull(dow).orEmpty().take(2).replaceFirstChar { it.titlecase(locale) }
    }
    val sat = (java.util.Calendar.SATURDAY - fdw + 7) % 7
    val sun = (java.util.Calendar.SUNDAY - fdw + 7) % 7
    return DeskMonth(title, heads, sat, sun, cells, rows, today)
}

/** The game you're on under the clock: its name, console and time played (tap: open it). */
@Composable
private fun DeskNameStrip(c: StyleCtx, compact: Boolean, modifier: Modifier) {
    val g = c.game
    val titles = LocalTitleOptions.current
    val accent = ShelfTheme.accent
    val open by rememberUpdatedState(c.actions.open)
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.clip(shape).background(Color.White.copy(alpha = 0.88f)).border(1.dp, DeskEdge, shape)
            .tapOnly { if (c.game != null) open() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight(0.62f).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(10.dp))
        if (g == null) {
            Text("No games match.", color = DeskMuted, fontSize = if (compact) 12.sp else 14.sp, modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    titles.display(g.name), color = DeskInk, fontSize = if (compact) 14.sp else 18.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    titles.console(g.platform) + " · " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                    color = DeskMuted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text("${c.focus + 1} / ${c.games.size}", color = DeskMuted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1)
        }
    }
}

// ---- Bottom screen: the games ----

@Composable
internal fun TwinDeskBottom(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val shape = RoundedCornerShape(if (framed) 14.dp else 0.dp)
    val n = c.games.size
    val loop = loopRowOn(c)
    val tap = remember { LoopTap() }
    // The place you're on, counted on round the end with Endless (twinDeskKey adds its steps to StyleLoop.walked).
    val v = rememberWalkedPlace(c.focus, n, loop, StyleLoop.walked, c.games, tap)
    val keys = remember(c.games) { gameKeys(c.games) }
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    BoxWithConstraints(
        modifier.clip(shape).background(Brush.verticalGradient(DeskBack))
            .then(if (framed) Modifier.border(1.dp, DeskEdge, shape) else Modifier)
    ) {
        val compact = maxHeight < 260.dp
        val pad = if (compact) 6.dp else 10.dp
        // (Update 179: Layout options › Gap between panels, and the R1 Gaps tile; smaller on a small screen.)
        val gap = (Features.twinDeskGap.state.value * if (compact) 0.7f else 1f).dp
        val smallH = if (compact) 34.dp else 46.dp
        val barH = if (compact) 34.dp else 42.dp
        // The list keeps 4 dp round its panels for the corner brackets.
        val listH = (maxHeight - pad * 2 - smallH - barH - gap * 2).coerceAtLeast(48.dp)
        // Update 159: whole panels only (up to 4) — the list used to show a cut-off part of a 4th one at its bottom edge.
        val avail = listH - 8.dp
        val rows = ((avail + gap) / (38.dp + gap)).toInt().coerceIn(1, 4)
        val panelH = ((avail - gap * (rows - 1)) / rows).coerceIn(30.dp, 64.dp)
        val before = if (rows >= 3) 1 else 0
        val at = if (loop) v else c.focus
        val topRow = if (loop) at - before else (at - before).coerceIn(0, (n - rows).coerceAtLeast(0))
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (n == 0) {
                Box(Modifier.fillMaxWidth().height(listH), contentAlignment = Alignment.Center) {
                    Text("No games match.", color = DeskMuted, fontSize = 13.sp)
                }
            } else {
                DeskList(c, keys, tap, loop, at, topRow, rows, panelH, gap, inGames, compact, Modifier.fillMaxWidth().height(listH))
            }
            // The next two games after the list.
            Row(Modifier.fillMaxWidth().height(smallH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (k in 0..1) {
                    val vi = topRow + rows + k
                    val real = if (loop && n > 0) vi.mod(n) else vi
                    val slot = Modifier.weight(1f).fillMaxHeight()
                    if (n > 0 && real in 0 until n) {
                        DeskSmall(c, real, compact, slot) {
                            if (loop) tap.virtual = vi
                            c.actions.select(real)
                        }
                    } else {
                        Box(slot.clip(RoundedCornerShape(8.dp)).border(1.dp, DeskEdge.copy(alpha = 0.6f), RoundedCornerShape(8.dp)))
                    }
                }
            }
            DeskBar(c, barH, Modifier.fillMaxWidth().height(barH))
        }
    }
}

/** The panels of the list, only the ones in view (and one more each side), sliding as the list moves. */
@Composable
private fun DeskList(
    c: StyleCtx, keys: List<String>, tap: LoopTap, loop: Boolean, at: Int, topRow: Int, rows: Int,
    panelH: Dp, gap: Dp, inGames: Boolean, compact: Boolean, modifier: Modifier
) {
    val n = c.games.size
    val scroll = rememberTwinScroll(topRow.toFloat(), rows + 2f, n)
    val first by remember(scroll) { derivedStateOf { kotlin.math.floor(scroll.value).toInt() } }
    val pitchPx = with(LocalDensity.current) { (panelH + gap).toPx() }
    Box(modifier.clipToBounds().deskSwipe(c, loop, pitchPx).padding(4.dp)) {
        for (i in first..first + rows + 1) {
            if (loop || i in 0 until n) {
                val real = if (loop) i.mod(n) else i
                key(if (loop) "${keys[real]}#${Math.floorDiv(i, n)}" else keys[real]) {
                    DeskPanel(
                        c, real, on = i == at, inGames = inGames, compact = compact,
                        modifier = Modifier.fillMaxWidth().height(panelH).offset { IntOffset(0, ((i - scroll.value) * pitchPx).roundToInt()) }
                    ) {
                        if (i == at) c.actions.open()
                        else {
                            if (loop) tap.virtual = i
                            c.actions.select(real)
                        }
                    }
                }
            }
        }
    }
}

/** A swipe up / down the list moves the game you're on (one game per panel's height). */
@Composable
private fun Modifier.deskSwipe(c: StyleCtx, loop: Boolean, pitchPx: Float): Modifier {
    val latest by rememberUpdatedState(c)
    val wraps by rememberUpdatedState(loop)
    return this.pointerInput(pitchPx) {
        var acc = 0f
        var idx = 0
        detectVerticalDragGestures(
            onDragStart = { acc = 0f; idx = latest.focus },
            onVerticalDrag = { change, dy ->
                change.consume()
                acc += dy
                while (pitchPx > 1f && kotlin.math.abs(acc) >= pitchPx) {
                    // Finger up: on to the next game.
                    val d = if (acc < 0f) 1 else -1
                    acc += d * pitchPx
                    val count = latest.games.size
                    val next = idx + d
                    val target = if (next in 0 until count) next else if (wraps && count >= 2) next.mod(count) else -1
                    if (target >= 0) {
                        StyleLoop.walked += d
                        idx = target
                        latest.actions.select(target)
                    }
                }
            }
        )
    }
}

/** One game as a wide panel: its picture on the left, name and console on the right; corner brackets when you're on it. */
@Composable
private fun DeskPanel(c: StyleCtx, i: Int, on: Boolean, inGames: Boolean, compact: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val g = c.games[i]
    val titles = LocalTitleOptions.current
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(if (compact) 8.dp else 11.dp)
    val lift = styleLift(on && inGames)
    Row(
        modifier
            .graphicsLayer { val k = 1f + 0.025f * lift; scaleX = k; scaleY = k }
            .deskBrackets(on, accent.copy(alpha = if (inGames) 1f else 0.45f))
            .clip(shape)
            .background(if (on) Color.White else Color(0xFFF8F9FA))
            .border(1.dp, if (on) accent.copy(alpha = 0.5f) else DeskEdge, shape)
            .tapOnly(onTap)
            .padding(if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleArt(c, g, Modifier.fillMaxHeight().aspectRatio(1f), square = true, corner = if (compact) 6.dp else 8.dp)
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titles.display(g.name), color = DeskInk, fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(titles.console(g.platform), color = DeskMuted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (on) Icon(Icons.Filled.PlayArrow, null, tint = accent, modifier = Modifier.size(if (compact) 16.dp else 20.dp))
    }
}

/** Four corner brackets just outside the panel (drawn over it, never clipped by its rounded corners). */
private fun Modifier.deskBrackets(on: Boolean, color: Color): Modifier = if (!on) this else this.drawWithCache {
    val w = 3.dp.toPx()
    val o = 2.5.dp.toPx()
    val len = minOf(16.dp.toPx(), size.minDimension * 0.32f)
    onDrawWithContent {
        drawContent()
        val l = -o
        val t = -o
        val r = size.width + o
        val b = size.height + o
        drawLine(color, Offset(l, t), Offset(l + len, t), w, StrokeCap.Square)
        drawLine(color, Offset(l, t), Offset(l, t + len), w, StrokeCap.Square)
        drawLine(color, Offset(r, t), Offset(r - len, t), w, StrokeCap.Square)
        drawLine(color, Offset(r, t), Offset(r, t + len), w, StrokeCap.Square)
        drawLine(color, Offset(l, b), Offset(l + len, b), w, StrokeCap.Square)
        drawLine(color, Offset(l, b), Offset(l, b - len), w, StrokeCap.Square)
        drawLine(color, Offset(r, b), Offset(r - len, b), w, StrokeCap.Square)
        drawLine(color, Offset(r, b), Offset(r, b - len), w, StrokeCap.Square)
    }
}

/** One of the two smaller panels under the list. */
@Composable
private fun DeskSmall(c: StyleCtx, i: Int, compact: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val g = c.games[i]
    val titles = LocalTitleOptions.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.clip(shape).background(Color(0xFFF8F9FA)).border(1.dp, DeskEdge, shape).tapOnly(onTap).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StyleArt(c, g, Modifier.fillMaxHeight().aspectRatio(1f), square = true, corner = 5.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            titles.display(g.name), color = DeskInk, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, lineHeight = if (compact) 12.sp else 14.sp
        )
    }
}

/** The slim bar along the bottom: quick settings and Settings on the left, the screensaver on the right. */
@Composable
private fun DeskBar(c: StyleCtx, h: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    val inBar = StyleNav.zone == StyleNav.Zone.BAR
    val names = remember { listOf("Quick settings", "Settings", "Screensaver", "Second screen") }
    val round = h - 8.dp
    Row(
        modifier.clip(shape).background(Color.White.copy(alpha = 0.72f)).border(1.dp, DeskEdge, shape).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeskRound(Icons.Filled.Tune, names[0], StyleNav.on(StyleNav.Zone.BAR, 0), round) { c.actions.quickMenu() }
        Spacer(Modifier.width(6.dp))
        DeskRound(Icons.Filled.Settings, names[1], StyleNav.on(StyleNav.Zone.BAR, 1), round) { c.actions.settings() }
        Text(
            if (inBar) names.getOrElse(StyleNav.index) { "" } else c.categoryLabel,
            color = if (inBar) DeskInk else DeskMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
        )
        DeskRound(Icons.Filled.Bedtime, names[2], StyleNav.on(StyleNav.Zone.BAR, 2), round) { c.actions.screensaver() }
        // Update 180: the second screen's own dashboard (its pages, dock, Games page); ↩ there comes back.
        if (twinOnSecond(c.state)) {
            Spacer(Modifier.width(6.dp))
            DeskRound(Icons.Filled.Dashboard, "Second screen", StyleNav.on(StyleNav.Zone.BAR, 3), round) { TwinScreens.dashboard = true }
        }
    }
}

@Composable
private fun DeskRound(icon: ImageVector, label: String, on: Boolean, size: Dp, onTap: () -> Unit) {
    val accent = ShelfTheme.accent
    Box(
        Modifier
            .graphicsLayer { val k = if (on) 1.1f else 1f; scaleX = k; scaleY = k }
            .size(size)
            .clip(CircleShape)
            .background(if (on) accent.copy(alpha = 0.18f) else Color.White)
            .border(if (on) 2.dp else 1.dp, if (on) accent else DeskEdge, CircleShape)
            .tapOnly(onTap),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = if (on) DeskInk else Color(0xFF4A5562), modifier = Modifier.size(size * 0.56f)) }
}
