package com.shelf.launcher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import kotlin.math.cos
import kotlin.math.sin

/*
 * Update 181: Pocket classic — Voltcap's own take on the menu of the 2004 two-screen pocket handheld (original art and
 * names only):
 * - top screen: white paper with faint pinstripes, a thin bar in your colour (your name; date, time and battery), a big
 *   clock with hands on the left (or big digits), this month's calendar on the right (Sundays red, Saturdays blue, today
 *   lit);
 * - bottom screen: a light grid with four flat panels — the game panel (the game you're on: ◀ ▶ change it, A starts it),
 *   Achievements and the category you're in side by side, All categories under them — and three tiny icons along the
 *   bottom edge: Settings, Statistics, Sleep. The one you're on has small corner brackets in your colour.
 * The category panel opens the category's games, All categories every category, in pages of the same grid (A picks, B
 * back). Two screens: the top on the upper screen, the menu on the lower one; one screen: one above the other.
 */

private val PaperC = Color(0xFFFFFFFF)
private val StripeC = Color(0xFFF2F3F5)
private val GridC = Color(0xFFE2E5E9)
private val EdgeC = Color(0xFFA9AFB7)
private val EdgeDarkC = Color(0xFF7E858E)
private val InkC = Color(0xFF3A3F46)
private val MutedC = Color(0xFF8B919A)
private val PressC = Color(0xFFE9ECF0)
private val SundayC = Color(0xFFD5453D)
private val SaturdayC = Color(0xFF3B6FD1)
/** The handheld's body round the screens on one display. */
internal val PocketShell = Color(0xFFE8EAED)

/** Update 181: where the controller is in the Pocket layouts (kept while you play a game and come back). */
internal object PocketNav {
    enum class Page { HOME, GAMES, CATEGORIES }
    /** Pocket classic: the page on the bottom screen and the panel you're on (0 game … 6 sleep). */
    var page by mutableStateOf(Page.HOME)
    var spot by mutableIntStateOf(0)
    /** The cursor in a page of games / categories, and whether the games page came from All categories. */
    var cursor by mutableIntStateOf(0)
    var gamesFromAll = false
    /** A panel just pressed (a short grey flash). */
    var pressed by mutableIntStateOf(-1)
    /** Pocket ribbon: showing the games (else the categories), and the cursor among the categories. */
    var ribbonGames by mutableStateOf(true)
    var ribbonCat by mutableIntStateOf(0)
    /** Update 182: Pocket ribbon's controller below the row: a tool or a strip control (PocketSpot), −1 = the row. */
    var ribbonSpot by mutableIntStateOf(-1)
    /** Pocket ribbon: a tile starting (its launch moment, 0–1 while it plays). */
    var launching by mutableStateOf(false)
    /** The ribbon nudged at an end (−1 / +1), for its little bounce. */
    var bump by mutableIntStateOf(0)
    var bumpAt by mutableStateOf(0L)
    @Volatile var ctx: StyleCtx? = null
    internal val keysDown = HashSet<Int>()
    /** Everything the controller moves here, for the "▼ changed nothing" check (LauncherScreen). */
    fun signature(): Any = listOf(page, spot, cursor, ribbonGames, ribbonCat, ribbonSpot)
}

/** Panels of the bottom screen: 0 game, 1 achievements, 2 category, 3 all categories, 4 settings, 5 statistics, 6 sleep. */
private const val CARD = 0
private const val ACH = 1
private const val CAT = 2
private const val ALL = 3
private const val SET = PocketSpot.SET
private const val STATS = PocketSpot.STATS
private const val MOON = PocketSpot.MOON

@Composable
internal fun StylePocketClassic(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        PocketNav.ctx = c
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
    }
    PocketStartCategory(c)
    TwinHost(
        c, modifier,
        top = { m, framed -> PocketClassicTop(c, m, framed) },
        bottom = { m, framed -> PocketClassicBottom(c, m, framed) },
        topH = 240.dp, bottomH = 240.dp, minW = 320.dp,
        stacked = when (Features.pocketArrange.state.value) {
            Features.PocketArrange.STACKED -> true; Features.PocketArrange.SIDE -> false; else -> null
        }
    )
}

/** Update 181: Layout options › Opens on: the category to jump to once, when the layout opens. */
@Composable
internal fun PocketStartCategory(c: StyleCtx) {
    val want = Features.pocketStartCategory.state.value
    LaunchedEffect(c.state.appearance.homeLayout, want) {
        if (want.isBlank() || c.stop < 0) return@LaunchedEffect
        val target = c.stops.indexOfFirst { pocketStopKey(it) == want }
        if (target >= 0 && target != c.stop) c.actions.category(pocketStepsTo(c, target))
    }
}

internal fun pocketStopKey(s: StyleStop): String = s.platform ?: s.special?.name ?: "ALL"

/** The category steps (L2 / R2 presses) from the one you're in to [target], the short way round. */
internal fun pocketStepsTo(c: StyleCtx, target: Int): Int {
    val n = c.stops.size
    if (n == 0 || c.stop < 0) return 0
    var d = (target - c.stop).mod(n)
    if (d > n / 2) d -= n
    return d
}

// ---- Top screen: clock and calendar ----

@Composable
private fun PocketClassicTop(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val accent = pocketAccent(runCatching { PocketAccent.valueOf(Features.classicAccent.state.value) }.getOrDefault(PocketAccent.BLUE))
    val font = pocketFont()
    val ticking = !HomeIdle.idle
    val time = rememberTwinTime(seconds = ticking)
    val tz = remember { java.util.TimeZone.getDefault() }
    val calendarOn = Features.classicCalendar.state.value
    val analogue = Features.classicAnalogue.state.value
    val battery = rememberBatteryInfo()
    val user = pocketUser(c.state)
    PocketFrame(modifier, framed) {
        PocketScreen(Modifier.fillMaxSize(), responsive = !Features.pocketPixel.state.value, outside = PaperC) {
            val uPx = d(1f).value * density
            Canvas(Modifier.atFull(0f, 0f, width, fullH)) { pocketPinstripes(uPx, PaperC, StripeC) }
            // The thin bar in your colour: your name; the date, time and battery (from the screen's very top).
            Box(Modifier.atFull(0f, 0f, width, 13f + cy).background(accent))
            PocketText(this, user, 4f, 2.5f, 120f, 10f, 8f, Color.White, font, TextAlign.Start)
            val day = pocketDay(time.longValue, tz)
            val clock = "%d/%d  %d:%02d".format(day.month, day.dayOfMonth, day.hour, day.minute)
            PocketText(this, clock, width - 4f - 90f - 18f - cx * 2f, 2.5f, 90f, 10f, 8f, Color.White, font, TextAlign.End, screenX = true)
            Canvas(Modifier.atScreen(width - 18f, 3f, 14f, 7f)) { pocketBattery(uPx, battery.percent, Color.White) }
            // The clock (left; in the middle without the calendar) and the calendar (right).
            val clockX = if (calendarOn) 18f else 78f
            if (analogue) {
                Canvas(Modifier.at(clockX, 40f, 100f, 100f)) { pocketAnalogue(uPx, time.longValue, tz, ticking, accent) }
            } else {
                val t = "%d:%02d".format(day.hour, day.minute)
                PocketText(this, t, clockX - 10f, 70f, 120f, 40f, 30f, InkC, font, TextAlign.Center)
                if (ticking) PocketText(this, ":%02d".format(day.second), clockX - 10f, 102f, 120f, 12f, 9f, accent, font, TextAlign.Center)
            }
            if (calendarOn) PocketCalendar(this, time, tz, accent, font, 134f, 24f)
        }
    }
}

/** A screen on one display gets a thin dark bezel; on its own screen it fills it. */
@Composable
private fun PocketFrame(modifier: Modifier, framed: Boolean, content: @Composable () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    Box(
        if (framed) modifier.clip(shape).background(Color(0xFF2B2F35)).padding(3.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(PaperC)
        else modifier.background(PaperC)
    ) { content() }
}

/** Text placed in dots. [screenX]: x is from the screen's left edge (not the centred design). */
@Composable
internal fun PocketText(
    s: PocketScope, text: String, x: Float, y: Float, w: Float, h: Float, size: Float, color: Color,
    font: androidx.compose.ui.text.font.FontFamily, align: TextAlign, maxLines: Int = 1, screenX: Boolean = false
) {
    with(s) {
        Box(if (screenX) Modifier.atScreen(x, y, w, h) else Modifier.at(x, y, w, h), contentAlignment = when (align) {
            TextAlign.Center -> Alignment.Center; TextAlign.End -> Alignment.CenterEnd; else -> Alignment.CenterStart
        }) {
            Text(
                text, maxLines = maxLines, overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1,
                style = TextStyle(color = color, fontSize = sp(size), lineHeight = sp(size * 1.15f), fontFamily = font, textAlign = align)
            )
        }
    }
}

internal data class PocketDay(val year: Int, val month: Int, val dayOfMonth: Int, val hour: Int, val minute: Int, val second: Int)

internal fun pocketDay(t: Long, tz: java.util.TimeZone): PocketDay {
    val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = t }
    return PocketDay(
        cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND)
    )
}

/** A small battery: an outline and bars for the charge. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.pocketBattery(u: Float, percent: Int, ink: Color) {
    pocketOutline(u, ink, 1f)
    drawRect(ink, Offset(size.width, size.height * 0.3f), Size(u, size.height * 0.4f))
    val bars = ((percent + 24) / 25).coerceIn(0, 4)
    val bw = (size.width - u * 4f) / 4f
    for (i in 0 until bars) drawRect(if (percent < 15) SundayC else ink, Offset(u * 2f + i * bw, u * 2f), Size(bw - u * 0.6f, size.height - u * 4f))
}

/** The clock with hands: a white face with a thin rim, twelve marks, the hands; the second hand in your colour. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pocketAnalogue(u: Float, t: Long, tz: java.util.TimeZone, seconds: Boolean, accent: Color) {
    val r = size.minDimension / 2f
    drawCircle(PaperC, r, center)
    drawCircle(EdgeC, r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(u * 1.2f))
    for (i in 0 until 60) {
        val a = i * (Math.PI / 30.0).toFloat()
        val big = i % 5 == 0
        val r0 = r * (if (big) 0.82f else 0.88f)
        val r1 = r * 0.92f
        if (big || i % 1 == 0) drawLine(if (big) InkC else GridC, Offset(center.x + sin(a) * r0, center.y - cos(a) * r0), Offset(center.x + sin(a) * r1, center.y - cos(a) * r1), u * (if (big) 1.6f else 0.8f))
    }
    val day = pocketDay(t, tz)
    fun hand(deg: Float, len: Float, w: Float, color: Color) {
        val a = Math.toRadians(deg.toDouble()).toFloat()
        drawLine(color, Offset(center.x - sin(a) * r * 0.08f, center.y + cos(a) * r * 0.08f), Offset(center.x + sin(a) * len, center.y - cos(a) * len), w, StrokeCap.Square)
    }
    hand((day.hour % 12) * 30f + day.minute * 0.5f, r * 0.5f, u * 3f, InkC)
    hand(day.minute * 6f + day.second * 0.1f, r * 0.74f, u * 2f, InkC)
    if (seconds) hand(day.second * 6f, r * 0.8f, u * 0.9f, accent)
    drawCircle(InkC, u * 2.4f, center)
}

/** This month: its number and year, the weekdays (Sunday red, Saturday blue), the days; today lit in your colour. */
@Composable
private fun PocketCalendar(s: PocketScope, time: LongState, tz: java.util.TimeZone, accent: Color, font: androidx.compose.ui.text.font.FontFamily, x: Float, y: Float) {
    val day = pocketDay(time.longValue, tz)
    val month = remember(day.year, day.month) {
        val cal = java.util.Calendar.getInstance(tz).apply { clear(); set(day.year, day.month - 1, 1) }
        val first = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
        first to cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }
    val (first, days) = month
    // (Update 182: wider cells and smaller digits — two-digit days ran into each other.)
    val cell = 17f
    val rowH = 20f
    with(s) {
        // The month, small and centred over the grid.
        PocketText(s, "${day.month} / ${day.year}", x, y, cell * 7, 12f, 9f, InkC, font, TextAlign.Center)
        Canvas(Modifier.at(x, y + 14f, cell * 7, 1f)) { drawRect(EdgeC) }
        val names = listOf("S", "M", "T", "W", "T", "F", "S")
        names.forEachIndexed { i, n -> PocketText(s, n, x + i * cell, y + 16f, cell, 10f, 7f, if (i == 0) SundayC else if (i == 6) SaturdayC else MutedC, font, TextAlign.Center) }
        for (d in 1..days) {
            val pos = first + d - 1
            val col = pos % 7
            val row = pos / 7
            val cx0 = x + col * cell
            val cy0 = y + 28f + row * rowH
            val today = d == day.dayOfMonth
            if (today) Box(Modifier.at(cx0 + 1f, cy0 + 2f, cell - 2f, rowH - 4f).background(accent))
            PocketText(s, "$d", cx0, cy0, cell, rowH, 6.5f, if (today) Color.White else if (col == 0) SundayC else if (col == 6) SaturdayC else InkC, font, TextAlign.Center)
        }
        // Thin lines between the weeks.
        val rows = (first + days + 6) / 7
        Canvas(Modifier.at(x, y + 28f, cell * 7, rows * rowH)) {
            val u = size.height / (rows * rowH)
            for (r in 1 until rows) drawRect(GridC, Offset(0f, r * rowH * u), Size(size.width, u * 0.6f))
        }
    }
}

// ---- Bottom screen: the menu ----

@Composable
internal fun PocketClassicBottom(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val accent = pocketAccent(runCatching { PocketAccent.valueOf(Features.classicAccent.state.value) }.getOrDefault(PocketAccent.BLUE))
    val font = pocketFont()
    PocketFrame(modifier, framed) {
        val strip = pocketStripOn()
        PocketScreen(Modifier.fillMaxSize(), responsive = !Features.pocketPixel.state.value, outside = PaperC,
            designH = POCKET_H + if (strip) POCKET_STRIP_H else 0f) {
            val uPx = d(1f).value * density
            Canvas(Modifier.atFull(0f, 0f, width, fullH)) {
                pocketPinstripes(uPx, PaperC, StripeC)
                pocketGrid(uPx, GridC, 44f, cx - 2f, cy - 22f) // (from the screen's top: the design's grid lines)
            }
            when (PocketNav.page) {
                PocketNav.Page.HOME -> PocketHome(this, c, accent, font)
                PocketNav.Page.GAMES -> PocketBrowser(this, c, accent, font, games = true)
                PocketNav.Page.CATEGORIES -> PocketBrowser(this, c, accent, font, games = false)
            }
            // Update 182: the home strip under the menu.
            if (strip) PocketHomeStrip(this, c, POCKET_H, accent, font, InkC, MutedC, EdgeC, Color(0xFFF4F5F7),
                PocketNav.spot.takeIf { PocketNav.page == PocketNav.Page.HOME })
        }
    }
}

@Composable
private fun PocketHome(s: PocketScope, c: StyleCtx, accent: Color, font: androidx.compose.ui.text.font.FontFamily) {
    val g = c.game
    val live by androidx.compose.runtime.rememberUpdatedState(c)
    val titles = LocalTitleOptions.current
    val spot = PocketNav.spot
    val toolbar = Features.classicToolbar.state.value
    with(s) {
        // The game panel.
        PocketPanel(s, CARD, 42f, 22f, 180f, 36f, accent, spot, onTap = { pocketActivate(c, CARD) },
            onSwipe = { d -> pocketStepGame(c, d) }) {
            Box(Modifier.at(4f - cx, 2f, 32f, 32f).background(Color(0xFFF4F5F7))) {
                if (g != null) PocketGameArt(c, g, Modifier.fillMaxSize())
            }
            if (g == null) PocketText(s, "There are no games here.", 40f - cx, 0f, 136f, 36f, 8f, MutedC, font, TextAlign.Center, maxLines = 2)
            else {
                PocketText(s, titles.display(g.name), 40f - cx, 4f, 136f, 18f, 8.5f, InkC, font, TextAlign.Center, maxLines = 2)
                PocketText(s, titles.console(g.platform), 40f - cx, 23f, 136f, 10f, 6.5f, MutedC, font, TextAlign.Center)
            }
        }
        // ◀ ▶ beside the game panel while it's lit (more games either way).
        if (spot == CARD && PocketNav.page == PocketNav.Page.HOME && c.games.size > 1) {
            Canvas(Modifier.at(33f, 36f, 5f, 8f).pointerInput(Unit) { detectTapGestures { pocketStepGame(live, -1) } }) { pocketArrow(accent, left = true) }
            Canvas(Modifier.at(226f, 36f, 5f, 8f).pointerInput(Unit) { detectTapGestures { pocketStepGame(live, 1) } }) { pocketArrow(accent, left = false) }
        }
        // Achievements and the category you're in.
        PocketPanel(s, ACH, 42f, 66f, 88f, 38f, accent, spot, thick = true, onTap = { pocketActivate(c, ACH) }) {
            Canvas(Modifier.at(4f - cx, 11f, 16f, 16f)) { pocketIcon(PocketIcons.TROPHY, InkC, accent) }
            // (Update 182: one line, a size that fits — it broke as "Achieveme / nts".)
            PocketText(s, "Achievements", 21f - cx, 5f, 66f, 28f, 5.3f, InkC, font, TextAlign.Center)
            Box(Modifier.at(80f - cx, 30f, 6f, 6f).background(accent))
        }
        val stop = c.stopAt(0)
        PocketPanel(s, CAT, 134f, 66f, 88f, 38f, accent, spot, thick = true, onTap = { pocketActivate(c, CAT) }) {
            Box(Modifier.at(4f - cx, 9f, 22f, 20f), contentAlignment = Alignment.Center) {
                val p = stop?.platform
                if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) PlatformLogo(p, c.categoryLabel, Modifier.fillMaxSize(), tint = InkC, textColor = InkC, textSize = sp(5f))
                else Icon(stop?.icon ?: Icons.Filled.GridView, null, tint = InkC, modifier = Modifier.fillMaxSize())
            }
            PocketText(s, c.categoryLabel, 28f - cx, 4f, 58f, 30f, 7f, InkC, font, TextAlign.Center, maxLines = 2)
            Box(Modifier.at(80f - cx, 30f, 6f, 6f).background(accent))
        }
        // All categories.
        PocketPanel(s, ALL, 42f, 112f, 180f, 36f, accent, spot, onTap = { pocketActivate(c, ALL) }) {
            Box(Modifier.at(4f - cx, 2f, 32f, 32f).background(Color(0xFFF4F5F7))) {
                Canvas(Modifier.at(8f - cx, 8f, 16f, 16f)) { pocketIcon(PocketIcons.ALL, InkC, accent) }
            }
            PocketText(s, "All Categories", 40f - cx, 4f, 136f, 18f, 8.5f, InkC, font, TextAlign.Center)
            PocketText(s, "${c.stops.size} to choose from", 40f - cx, 22f, 136f, 10f, 6.5f, MutedC, font, TextAlign.Center)
        }
        // The small icons along the bottom edge (update 182: redrawn, and Second screen with two screens).
        if (toolbar) {
            val tools = pocketTools(c, ribbon = false)
            tools.forEachIndexed { i, (id, icon) ->
                PocketToolButton(s, pocketToolX(i, tools.size), 168f, icon, InkC, accent, spot == id, PocketNav.pressed == id) {
                    PocketNav.spot = id; pocketActivate(live, id)
                }
            }
        }
    }
}

/** A flat panel: white, a thin grey outline (darker for the two middle buttons), corner brackets when it's the one you're on. */
@Composable
private fun PocketPanel(
    s: PocketScope, id: Int, x: Float, y: Float, w: Float, h: Float, accent: Color, spot: Int,
    thick: Boolean = false, onTap: () -> Unit, onSwipe: ((Int) -> Unit)? = null, content: @Composable () -> Unit
) {
    val lit = spot == id
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val swipe by androidx.compose.runtime.rememberUpdatedState(onSwipe)
    val shown by animateFloatAsState(if (lit) 1f else 0f, tween(pocketMs(110)), label = "pocketLit")
    val pressed = PocketNav.pressed == id
    with(s) {
        val uPx = d(1f).value * density
        Box(
            Modifier.at(x, y, w, h)
                .pointerInput(id) { detectTapGestures { PocketNav.spot = id; tap() } }
                .then(if (onSwipe != null) Modifier.pointerInput(id) {
                    var acc = 0f
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                        ch.consume(); acc += dx
                        val step = uPx * 24f
                        if (acc > step) { acc = 0f; swipe?.invoke(-1) } else if (acc < -step) { acc = 0f; swipe?.invoke(1) }
                    }
                } else Modifier)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(if (pressed) PressC else PaperC)
                pocketOutline(uPx, if (thick) EdgeDarkC else EdgeC, if (thick) 1.5f else 1f)
            }
            // Its contents, placed in the screen's dots from the panel's corner.
            // (Its contents are placed with at(…) measured from the panel's own corner: x − cx.)
            Box(Modifier.fillMaxSize().clipToBounds()) { content() }
            if (shown > 0f) Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) { pocketBrackets(uPx, accent) }
        }
    }
}

/** A tiny toolbar icon (12 dots), brackets round it when it's the one you're on. */
@Composable
private fun PocketTool(s: PocketScope, id: Int, x: Float, y: Float, icon: List<String>, accent: Color, spot: Int, onTap: () -> Unit) {
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    with(s) {
        val uPx = d(1f).value * density
        Box(Modifier.at(x, y, 14f, 14f).pointerInput(id) { detectTapGestures { PocketNav.spot = id; tap() } }) {
            Canvas(Modifier.fillMaxSize()) {
                if (PocketNav.pressed == id) drawRect(PressC)
                inset(uPx, uPx) { pocketIcon(icon, InkC, accent) }
                if (spot == id) pocketBrackets(uPx, accent, len = 4f, thick = 1f, out = 1f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.inset(l: Float, t: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.transform.inset(l, t, l, t); block(); drawContext.transform.inset(-l, -t, -l, -t)
}

/** A small solid triangle pointing left or right. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.pocketArrow(color: Color, left: Boolean) {
    val p = androidx.compose.ui.graphics.Path().apply {
        if (left) { moveTo(size.width, 0f); lineTo(0f, size.height / 2f); lineTo(size.width, size.height) }
        else { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height) }
        close()
    }
    drawPath(p, color)
}

/** The game's picture in a panel: its cover cut square, its logo, or a screenshot (Layout options › Game picture). */
@Composable
internal fun PocketGameArt(c: StyleCtx, g: Game, modifier: Modifier) {
    val url = when (Features.classicArt.state.value) {
        Features.PocketArt.LOGO -> c.logo(g) ?: c.cover(g)
        Features.PocketArt.SHOT -> c.state.screenshots[g.stableKey]?.firstOrNull() ?: c.backdrop(g) ?: c.cover(g)
        else -> c.cover(g)
    }
    val model = coverOrAppIcon(g, url)
    if (model != null) AsyncImage(
        model, g.name,
        contentScale = if (Features.classicArt.state.value == Features.PocketArt.LOGO) ContentScale.Fit else ContentScale.Crop,
        modifier = modifier.pixelArtwork()
    ) else Box(modifier.background(placeholderBrush(g.hue)))
}

/** A page of games (the category you're in) or of categories, in the menu's own grid: 5 × 3, the name under it. */
@Composable
private fun PocketBrowser(s: PocketScope, c: StyleCtx, accent: Color, font: androidx.compose.ui.text.font.FontFamily, games: Boolean) {
    val titles = LocalTitleOptions.current
    val live by androidx.compose.runtime.rememberUpdatedState(c)
    val n = if (games) c.games.size else c.stops.size
    val cur = PocketNav.cursor.coerceIn(0, (n - 1).coerceAtLeast(0))
    val perPage = 15
    val pageStart = cur / perPage * perPage
    with(s) {
        val uPx = d(1f).value * density
        // The header: back, what's shown, where you are.
        Box(Modifier.atScreen(0f, 0f, width, 15f).background(Color(0xFFEFF1F4)))
        Canvas(Modifier.atScreen(0f, 15f, width, 1f)) { drawRect(EdgeC) }
        Box(Modifier.atScreen(3f, 1.5f, 12f, 12f).pointerInput(Unit) { detectTapGestures { pocketBack() } }) {
            Canvas(Modifier.fillMaxSize()) { pocketIcon(PocketIcons.BACK, InkC, accent) }
        }
        PocketText(s, if (games) c.categoryLabel else "All Categories", 18f, 2f, 160f, 12f, 8f, InkC, font, TextAlign.Start, screenX = true)
        PocketText(s, if (n == 0) "" else "${cur + 1} / $n", width - 64f, 2f, 60f, 12f, 7f, MutedC, font, TextAlign.End, screenX = true)
        for (k in 0 until perPage) {
            val i = pageStart + k
            if (i >= n) break
            val col = k % 5
            val row = k / 5
            val x = 18f + col * 44f
            val y = 24f + row * 46f
            val lit = i == cur
            Box(Modifier.at(x, y, 40f, 40f).pointerInput(i, games) {
                detectTapGestures { if (PocketNav.cursor == i) pocketPick(live, games, i) else PocketNav.cursor = i }
            }) {
                Canvas(Modifier.fillMaxSize()) { drawRect(PaperC); pocketOutline(uPx, EdgeC) }
                Box(Modifier.fillMaxSize().padding(d(2f))) {
                    if (games) PocketGameArt(c, c.games[i], Modifier.fillMaxSize())
                    else {
                        val st = c.stops[i]
                        val p = st.platform
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) PlatformLogo(p, st.label, Modifier.fillMaxSize(), tint = InkC, textColor = InkC, textSize = sp(6f))
                            else Icon(st.icon, null, tint = InkC, modifier = Modifier.fillMaxSize(0.6f))
                        }
                    }
                }
                if (lit) Canvas(Modifier.fillMaxSize()) { pocketBrackets(uPx, accent, len = 5f) }
            }
        }
        // The name of the one you're on, in a flat panel along the bottom.
        Box(Modifier.at(18f, 162f, 220f, 26f)) { Canvas(Modifier.fillMaxSize()) { drawRect(PaperC); pocketOutline(uPx, EdgeC) } }
        if (n == 0) PocketText(s, "There's nothing here.", 22f, 162f, 212f, 26f, 8f, MutedC, font, TextAlign.Center)
        else if (games) {
            val g = c.games[cur]
            PocketText(s, titles.display(g.name), 22f, 163f, 212f, 14f, 8f, InkC, font, TextAlign.Center)
            PocketText(s, titles.console(g.platform) + " · " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), 22f, 176f, 212f, 10f, 6.5f, MutedC, font, TextAlign.Center)
        } else {
            PocketText(s, c.stops[cur].label, 22f, 163f, 212f, 14f, 8f, InkC, font, TextAlign.Center)
            PocketText(s, if (cur == c.stop) "You're here" else "A: its games", 22f, 176f, 212f, 10f, 6.5f, MutedC, font, TextAlign.Center)
        }
    }
}

// ---- What the panels do ----

private fun pocketPlaySound(s: UiSound) { PocketSounds.play?.invoke(s) }

/** Update 181: the layouts play their sounds through this (set by the home screen). */
internal object PocketSounds { @Volatile var play: ((UiSound) -> Unit)? = null }

internal fun pocketStepGame(c: StyleCtx, d: Int) {
    val n = c.games.size
    if (n == 0) return
    val next = if (StyleLoop.on) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
    if (next != c.focus) { c.actions.select(next); pocketPlaySound(UiSound.MOVE) }
}

private fun pocketFlash(id: Int) {
    PocketNav.pressed = id
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (PocketNav.pressed == id) PocketNav.pressed = -1 }, 140L)
}

internal fun pocketActivate(c: StyleCtx, spot: Int) {
    pocketFlash(spot)
    when (spot) {
        CARD -> c.game?.let { c.actions.play(it) }
        ACH -> { val g = c.game; if (g != null) c.actions.achievements(g) else c.actions.achievementsHub() }
        CAT -> { pocketPlaySound(UiSound.OPEN); PocketNav.gamesFromAll = false; PocketNav.cursor = c.focus; PocketNav.page = PocketNav.Page.GAMES }
        ALL -> { pocketPlaySound(UiSound.OPEN); PocketNav.cursor = c.stop.coerceAtLeast(0); PocketNav.page = PocketNav.Page.CATEGORIES }
        else -> pocketShared(c, spot)
    }
}

private fun pocketPick(c: StyleCtx, games: Boolean, i: Int) {
    if (games) {
        c.actions.select(i)
        pocketPlaySound(UiSound.SELECT)
        PocketNav.page = PocketNav.Page.HOME
        PocketNav.spot = CARD
    } else {
        val steps = pocketStepsTo(c, i)
        if (steps != 0) c.actions.category(steps)
        pocketPlaySound(UiSound.SELECT)
        PocketNav.gamesFromAll = true
        PocketNav.cursor = 0
        PocketNav.page = PocketNav.Page.GAMES
    }
}

internal fun pocketBack() {
    pocketPlaySound(UiSound.BACK)
    PocketNav.page = when {
        PocketNav.page == PocketNav.Page.GAMES && PocketNav.gamesFromAll -> {
            PocketNav.ctx?.let { PocketNav.cursor = it.stop.coerceAtLeast(0) }
            PocketNav.Page.CATEGORIES
        }
        else -> PocketNav.Page.HOME
    }
    PocketNav.gamesFromAll = false
}

/**
 * Update 181: Pocket classic's keys (all of them while it's on, before the home screen's own): the D-pad between the
 * panels (◀ ▶ change the game on the game panel), A presses, B goes back; in a page: the D-pad in the grid, A picks,
 * B back. Past the bottom: what lies below (the second screen). True = handled.
 */
internal fun pocketClassicKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = PocketNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && PocketNav.keysDown.remove(code)) return true
    if (!down) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { PocketNav.keysDown += code; return true }
    val k = event.key
    if (PocketNav.page != PocketNav.Page.HOME) {
        val games = PocketNav.page == PocketNav.Page.GAMES
        val n = if (games) c.games.size else c.stops.size
        val cur = PocketNav.cursor
        val next = when (k) {
            Key.DirectionLeft -> cur - 1
            Key.DirectionRight -> cur + 1
            Key.DirectionUp -> cur - 5
            Key.DirectionDown -> cur + 5
            Key.ButtonL1 -> cur - 15
            Key.ButtonR1 -> cur + 15
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> { if (first && n > 0) pocketPick(c, games, cur.coerceIn(0, n - 1)); return take() }
            Key.ButtonB, Key.Back -> { if (first) pocketBack(); return take() }
            else -> return false
        }
        val clamped = next.coerceIn(0, (n - 1).coerceAtLeast(0))
        if (clamped != cur) { PocketNav.cursor = clamped; pocketPlaySound(UiSound.MOVE) }
        return take()
    }
    val toolbar = Features.classicToolbar.value
    val spot = PocketNav.spot
    fun go(s: Int): Boolean { if (s != spot) { PocketNav.spot = s; pocketPlaySound(UiSound.MOVE) }; return take() }
    // Update 182: the places as rows (the panels, the small tools, the home strip): ▲ ▼ to the nearest one.
    val tools = if (toolbar) pocketTools(c, ribbon = false) else emptyList()
    val rows = listOf(
        listOf(CARD to 132f), listOf(ACH to 86f, CAT to 178f), listOf(ALL to 132f),
        tools.mapIndexed { i, (id, _) -> id to pocketToolX(i, tools.size) + 9f },
        if (pocketStripOn()) pocketStripSpots(c) else emptyList()
    ).filter { it.isNotEmpty() }
    fun dirKey(d: Int): Boolean {
        val next = pocketRowStep(rows, spot, d)
        return when {
            next != null -> go(next)
            d == 2 -> { if (first) reach(true); take() }
            d == 3 -> { if (first) reach(false); take() }
            else -> take()
        }
    }
    return when (k) {
        Key.DirectionLeft -> if (spot == CARD) { pocketStepGame(c, -1); take() } else dirKey(0)
        Key.DirectionRight -> if (spot == CARD) { pocketStepGame(c, 1); take() } else dirKey(1)
        Key.DirectionUp -> dirKey(2)
        Key.DirectionDown -> dirKey(3)
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> { if (first) pocketActivate(c, spot); take() }
        // B: back to the game panel (on it: the home screen's own B).
        Key.ButtonB, Key.Back -> if (spot != CARD) go(CARD) else false
        else -> false
    }
}
