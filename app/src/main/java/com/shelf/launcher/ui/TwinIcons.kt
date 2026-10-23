package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.Dashboard
import com.shelf.launcher.data.statusOn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.roundToInt

/*
 * Update 158: Twin icons, a two-screen layout (see TwinScreens.kt). The idea of a later two-screen handheld's home,
 * drawn in Voltcap's style:
 * - top (main) screen: the game you're on as a big icon in a framed slot on a dark patterned background (the games
 *   before and after it small and faded at its sides when there's room), its name and console on a card under it,
 *   and a slim status strip (clock, date, battery) when the home screen's status bar is off;
 * - bottom (second) screen: a grid of square game icons (4 to 7 a row) with the one you're on ringed, a row of small
 *   round tool buttons on top (your round shortcuts: Settings, Search, Achievements, Notifications…), an Open bar
 *   along the bottom.
 * ◀ ▶ ▲ ▼ move round the icons (◀ ▶ round the ends with Endless), ▲ from the top row the tool buttons, ▼ from the
 * bottom row the Open bar. Touch: tap an icon to pick it, tap the one you're on to open it.
 */

private val IconsBack = Color(0xFF161C27)
// (Update 309, Toni: "the same colour on top and bottom — black or white fully, or mix": Layout options › Colours.
// Mixed is the look as it was: the dark top, the light tray.)
private val topLight: Boolean get() = Features.twinIconsTone.state.value == Features.TwinTone.LIGHT
private val trayDark: Boolean get() = Features.twinIconsTone.state.value == Features.TwinTone.DARK
private val IconsDots: Color get() = if (topLight) Color(0x14000000) else Color(0x12FFFFFF)
/** Text on the top screen's background (the status strip). */
private val TopInk: Color get() = if (topLight) Color(0xE626313C) else Color.White.copy(alpha = 0.9f)
private val TrayBack: List<Color> get() = if (trayDark) listOf(Color(0xFF1C2330), Color(0xFF121820)) else listOf(Color(0xFFF1F3F6), Color(0xFFDDE2E8))
private val TrayInk: Color get() = if (trayDark) Color(0xFFE8EDF2) else Color(0xFF26313C)
private val TrayMuted: Color get() = if (trayDark) Color(0xFF97A3B0) else Color(0xFF6A7682)
private val TrayEdge: Color get() = if (trayDark) Color(0x33FFFFFF) else Color(0xFFC6CFD7)
/** The tray's buttons and icon frames. */
private val TraySurface: Color get() = if (trayDark) Color(0xFF242C39) else Color.White

/** The top screen's background: the theme-tinted dark one, or the tray's light grey. */
@Composable
private fun topBack(): Color = if (topLight) Color(0xFFEDF0F4) else themeDark(IconsBack)

/** Update 158: the grid's icons per row as last drawn (for the keys). */
internal object TwinNav {
    @Volatile var cols = 5
    /**
     * Update 325 (Toni: "Twin icons is missing a fitting category screen"): the Categories tool opens the tray as a grid
     * of the categories — each its console's logo on an icon — and the top screen shows the one you're on big. A picks
     * it, B (or the tool again) goes back to the games.
     */
    val cats = androidx.compose.runtime.mutableStateOf(false)
    val catCursor = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile var ctx: StyleCtx? = null

    fun openCats(c: StyleCtx) {
        if (cats.value) { closeCats(); return }
        if (c.stops.isEmpty()) return
        catCursor.intValue = c.stop.coerceIn(0, c.stops.size - 1)
        StyleNav.reset()
        cats.value = true
    }

    fun closeCats() { cats.value = false }

    fun pick(c: StyleCtx, i: Int) {
        val steps = pocketStepsTo(c, i)
        if (steps != 0) c.actions.category(steps)
        cats.value = false
    }
}

/** Update 325: the category page's keys — the grid, A picks, B back to the games. */
internal fun twinCatsKey(event: androidx.compose.ui.input.key.KeyEvent, sound: (UiSound) -> Unit): Boolean {
    val c = TwinNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val type = event.type
    if (type == androidx.compose.ui.input.key.KeyEventType.KeyUp) return StyleNav.keysDown.remove(code)
    if (type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
    val first = event.nativeKeyEvent.repeatCount == 0
    fun take(): Boolean { StyleNav.keysDown += code; return true }
    val n = c.stops.size
    if (n == 0) { TwinNav.closeCats(); return take() }
    val cols = TwinNav.cols.coerceAtLeast(1)
    val cur = TwinNav.catCursor.intValue.coerceIn(0, n - 1)
    val next = when (event.key) {
        Key.DirectionLeft -> (cur - 1).mod(n)
        Key.DirectionRight -> (cur + 1).mod(n)
        Key.DirectionUp -> if (cur - cols >= 0) cur - cols else cur
        Key.DirectionDown -> if (cur / cols < (n - 1) / cols) minOf(cur + cols, n - 1) else cur
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> { if (first) { sound(UiSound.SELECT); TwinNav.pick(c, cur) }; return take() }
        Key.ButtonB, Key.Back, Key.Escape -> { if (first) { sound(UiSound.BACK); TwinNav.closeCats() }; return take() }
        else -> return false
    }
    if (next != cur) { TwinNav.catCursor.intValue = next; sound(UiSound.MOVE) }
    return take()
}

/** The round shortcuts used as tool buttons, in this order (the ones you've kept). */
internal val TwinToolOrder = listOf("Settings", "Search", "Achievements", "Notifications", "Music", "Categories")

private fun twinTools(c: StyleCtx): List<HomeShortcut> =
    TwinToolOrder.mapNotNull { l -> c.shortcuts.firstOrNull { it.label == l } }.ifEmpty { c.shortcuts.take(6) }
        // (Update 325: Categories opens Twin icons' own category page.)
        .map { t -> if (t.label == "Categories" && c.stops.isNotEmpty()) HomeShortcut(t.label, t.icon, t.tint) { TwinNav.openCats(c) } else t } +
        // Update 180: the second screen's own dashboard (its pages, dock, Games page); ↩ there comes back.
        if (twinOnSecond(c.state) && Features.twinDashboardAccess.state.value) listOf(HomeShortcut("Second screen", androidx.compose.material.icons.Icons.Filled.Dashboard, Color(0xFF7C5CFF)) { TwinScreens.dashboard = true }) else emptyList()

@Composable
internal fun StyleTwinIcons(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        TwinNav.ctx = c
        StyleLoop.on = loopRowOn(c)
        StyleNav.side = emptyList()
        StyleNav.top = twinTools(c).map { it.onClick }
        // (Another layout's tab start must not pick the tool button ▲ lands on.)
        StyleNav.topStart = -1
        StyleNav.bar = if (c.game == null) emptyList() else listOf(c.actions.open)
    }
    TwinHost(
        c, modifier,
        top = { m, framed -> TwinIconsTop(c, m, framed) },
        bottom = { m, framed -> TwinIconsBottom(c, m, framed) }
    )
}

/** Update 158: the grid's keys (like the Library panel's); ▲ from the top row the tool buttons, ▼ from the bottom row Open. */
internal fun twinIconsKey(ctx: StyleKeyCtx): Boolean {
    val n = ctx.count
    if (n <= 0) {
        return when (ctx.key) {
            Key.DirectionUp -> if (StyleNav.top.isNotEmpty()) ctx.enterZone(StyleNav.Zone.TOP) else ctx.reach(true)
            Key.DirectionDown -> ctx.reach(false)
            else -> ctx.take()
        }
    }
    val cols = TwinNav.cols.coerceAtLeast(1)
    val f = ctx.focus.coerceIn(0, n - 1)
    val row = f / cols
    val lastRow = (n - 1) / cols
    val loop = StyleLoop.on && n >= 2
    // Every move adds its step to StyleLoop.walked, so with Endless the grid counts on exactly (see TwinIconsBottom).
    fun go(target: Int, d: Int = target - f): Boolean {
        if (target != f && target in 0 until n) {
            StyleLoop.walked += d
            ctx.setFocus(target)
            ctx.sound(UiSound.MOVE)
        }
        return ctx.take()
    }
    return when (ctx.key) {
        Key.DirectionUp -> when {
            row > 0 -> go(f - cols)
            StyleNav.top.isNotEmpty() -> ctx.enterZone(StyleNav.Zone.TOP)
            else -> ctx.reach(true)
        }
        Key.DirectionDown -> when {
            // The row below is shorter: its last game.
            row < lastRow -> go(minOf(f + cols, n - 1))
            StyleNav.bar.isNotEmpty() -> ctx.enterZone(StyleNav.Zone.BAR)
            else -> ctx.reach(false)
        }
        Key.DirectionLeft -> if (f > 0) go(f - 1) else if (loop) go(n - 1, -1) else ctx.take()
        Key.DirectionRight -> if (f < n - 1) go(f + 1) else if (loop) go(0, 1) else ctx.take()
        else -> false
    }
}

/** Update 179: the gaps offered for the two-screen layouts (dp), and how they read. */
internal val TWIN_GAPS = arrayOf(0, 2, 4, 8, 12, 16, 24)
internal fun gapLabel(dp: Int): String = if (dp == 0) "None" else "$dp dp"

@Composable
internal fun TwinIconsSettings() {
    MutedNote("Made for handhelds with two screens. The top screen shows the game you're on as a big icon in a frame, its name and console on a card under it. The bottom screen is a grid of square game icons, round tool buttons above it and an Open bar under it. ◀ ▶ ▲ ▼ move round the icons, ▲ from the top row reaches the tool buttons, ▼ from the bottom row the Open bar. With one screen, both halves show together.")
    Spacer(Modifier.height(10.dp))
    StyleChoice("Icons per row", arrayOf(4, 5, 6, 7), Features.twinCols.state.value, { "$it" }) { Features.twinCols.value = it }
    // Update 179.
    StyleChoice("Gap between icons", TWIN_GAPS, Features.twinGap.state.value, { gapLabel(it) }) { Features.twinGap.value = it }
    // Update 309.
    StyleChoice("Colours", Features.TwinTone.values(), Features.twinIconsTone.state.value, { it.label }) { Features.twinIconsTone.value = it }
    TwinFullTopSwitch()
    MutedNote("The tool buttons are your round shortcuts (Settings, Search, Achievements, Notifications, Music, Categories: the ones you keep in Settings › Home › Round shortcuts). The bottom half goes to the second screen when Settings › Screens › Use the second screen is on.")
}

// ---- Top screen: the game you're on ----

@Composable
private fun TwinIconsTop(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val shape = RoundedCornerShape(if (framed) 14.dp else 18.dp)
    val back = topBack()
    // The home screen's status bar already shows the time and battery above a full-screen top; a small framed one
    // (one screen) or no status bar: its own strip.
    // (Update 158: only without the status bar — stacked on one screen it drew the clock twice.)
    val strip = !c.state.appearance.statusOn
    // (Update 180: filling the top screen, its background is drawn behind everything — TwinIconsBackdrop.)
    val full = !framed && TwinScreens.fullTop
    BoxWithConstraints(
        if (full) modifier else modifier.clip(shape).background(back).iconsPattern()
            .then(if (framed) Modifier.border(1.dp, if (topLight) TrayEdge else Color.White.copy(alpha = 0.12f), shape) else Modifier)
    ) {
        val compact = maxHeight < 230.dp
        val pad = if (compact) 8.dp else 14.dp
        val cardH = if (compact) 40.dp else 54.dp
        Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            if (strip) IconsStatus(compact, Modifier.fillMaxWidth().height(if (compact) 18.dp else 22.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val slot = minOf(maxHeight, maxWidth * 0.62f)
                // The games before and after, small and faded at the sides, when there's room for them.
                val side = slot * 0.5f
                val roomForSides = maxWidth > slot + side * 2 + 24.dp
                if (roomForSides) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconsNeighbour(c, -1, side)
                        IconsNeighbour(c, 1, side)
                    }
                }
                if (TwinNav.cats.value) CatSlot(c, slot) else IconsSlot(c, slot)
            }
            Spacer(Modifier.height(pad * 0.7f))
            if (TwinNav.cats.value) CatCard(c, compact, Modifier.fillMaxWidth(if (framed) 1f else 0.72f).height(cardH))
            else IconsCard(c, compact, Modifier.fillMaxWidth(if (framed) 1f else 0.72f).height(cardH).editablePart(LayoutParts.id(HomeLayout.TWIN_ICONS, "card"), "Name card"))
        }
    }
}

/** Update 180: Twin icons' dark patterned background across the whole top screen (behind the status bar too). */
@Composable
internal fun TwinIconsBackdrop(modifier: Modifier) {
    Box(modifier.background(topBack()).iconsPattern()) {
        // (Update 309: on the light top, a soft shade behind Voltcap's white status bar so it stays readable.)
        if (topLight && TwinScreens.ctx?.state?.appearance?.statusOn == true) Box(
            Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent)))
        )
    }
}

/** Small diamonds over the dark background, and a soft shade (drawn once per size). */
private fun Modifier.iconsPattern(): Modifier = this.drawWithCache {
    val step = 22.dp.toPx()
    val d = 2.5.dp.toPx()
    val path = Path()
    var y = 0f
    var odd = false
    while (y < size.height + step) {
        var x = if (odd) step / 2f else 0f
        while (x < size.width + step) {
            path.moveTo(x, y - d)
            path.lineTo(x + d, y)
            path.lineTo(x, y + d)
            path.lineTo(x - d, y)
            path.close()
            x += step
        }
        y += step / 2f
        odd = !odd
    }
    val shade = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = if (topLight) 0.06f else 0.28f)))
    onDrawBehind {
        drawPath(path, IconsDots)
        drawRect(shade)
    }
}

/** The clock with the date, and the battery (the status bar's own readings). */
@Composable
private fun IconsStatus(compact: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val bar by remember(context) { com.shelf.launcher.data.StatusBarPrefs.state(context) }.collectAsState()
    val clock = rememberClockText(true, bar.hours, true)
    val battery = rememberBatteryInfo()
    val fs = if (compact) 10.sp else 12.sp
    Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(clock.orEmpty(), color = TopInk, fontSize = fs, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Icon(batteryIconFor(battery.percent, battery.charging), null, tint = TopInk, modifier = Modifier.size(if (compact) 12.dp else 15.dp))
        Spacer(Modifier.width(3.dp))
        Text("${battery.percent}%", color = TopInk, fontSize = fs, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The game you're on in its framed slot, a soft light of the theme's colour behind it (tap: open it). */
@Composable
private fun IconsSlot(c: StyleCtx, slot: Dp) {
    val accent = ShelfTheme.accent
    val open by rememberUpdatedState(c.actions.open)
    val frame = RoundedCornerShape(slot * 0.16f)
    Box(
        Modifier
            .size(slot)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.3f), Color.Transparent), center = center, radius = size.minDimension * 0.75f),
                    radius = size.minDimension * 0.75f
                )
            }
            .clip(frame)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), accent, Color.White.copy(alpha = 0.55f))))
            .padding(slot * 0.03f)
            .clip(RoundedCornerShape(slot * 0.14f))
            .background(if (topLight) Color.White else Color(0xFF0E131B))
            .padding(slot * 0.05f)
            .tapOnly { if (c.game != null) open() },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = c.game, animationSpec = tween(160), label = "twinIcon") { g ->
            if (g != null) StyleArt(c, g, Modifier.fillMaxSize(), square = true, corner = slot * 0.1f)
        }
    }
}

/** The game [d] steps from the one you're on (round the ends with Endless), small and faded; tap: pick it. */
@Composable
private fun IconsNeighbour(c: StyleCtx, d: Int, size: Dp) {
    val n = c.games.size
    val i = if (loopRowOn(c)) (c.focus + d).mod(n.coerceAtLeast(1)) else c.focus + d
    val g = c.games.getOrNull(i)?.takeIf { i != c.focus }
    // (Update 307: the side games change with the same short crossfade as the big icon — they used to swap at once.)
    Crossfade(targetState = g, animationSpec = tween(160), label = "twinSide", modifier = Modifier.size(size)) { sg ->
        if (sg != null) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f }.tapOnly {
                    StyleLoop.walked += d
                    c.actions.select(i)
                }
            ) { StyleArt(c, sg, Modifier.fillMaxSize(), square = true, corner = size * 0.14f) }
        } else {
            Spacer(Modifier.size(size))
        }
    }
}

/** The name card under the slot: the game's name, console and time played (tap: open it). */
@Composable
private fun IconsCard(c: StyleCtx, compact: Boolean, modifier: Modifier) {
    val g = c.game
    val titles = LocalTitleOptions.current
    val accent = ShelfTheme.accent
    val open by rememberUpdatedState(c.actions.open)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.clip(shape).background(if (trayDark) TraySurface else Color.White.copy(alpha = 0.92f)).tapOnly { if (c.game != null) open() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (compact) 8.dp else 10.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        if (g == null) {
            Text(EmptyShelf.text, color = TrayMuted, fontSize = if (compact) 12.sp else 14.sp)
        } else {
            // (Update 307: the name and console fade over with the big icon instead of jumping to the next game's.)
            Crossfade(targetState = g, animationSpec = tween(160), label = "twinCard", modifier = Modifier.weight(1f)) { cg ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        titles.display(cg.name), color = TrayInk, fontSize = if (compact) 13.sp else 17.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        titles.console(cg.platform) + " · " + stylePlaytime(c.playtime(cg), c.lastPlayed(cg) != null),
                        color = TrayMuted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ---- Bottom screen: the icons ----

@Composable
internal fun TwinIconsBottom(c: StyleCtx, modifier: Modifier, framed: Boolean) {
    val shape = RoundedCornerShape(if (framed) 14.dp else 0.dp)
    val n = c.games.size
    val tap = remember { LoopTap() }
    val keys = remember(c.games) { gameKeys(c.games) }
    val tools = twinTools(c)
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    BoxWithConstraints(
        modifier.clip(shape).background(Brush.verticalGradient(TrayBack))
            .then(if (framed) Modifier.border(1.dp, TrayEdge, shape) else Modifier)
    ) {
        val compact = maxHeight < 260.dp
        val pad = if (compact) 6.dp else 10.dp
        val gap = if (compact) 6.dp else 8.dp
        val toolH = if (compact) 28.dp else 36.dp
        val openH = if (compact) 30.dp else 38.dp
        val gridH = (maxHeight - pad * 2 - toolH - openH - gap * 2).coerceAtLeast(40.dp)
        val cols = Features.twinCols.state.value.coerceIn(4, 7)
        SideEffect { TwinNav.cols = cols }
        // Endless (more than one row): the games repeat down the grid, each round on a new row, and the place you're on
        // counts on round (twinIconsKey's steps), so the last game to the first is one more step, like any ▶.
        // (Update 226, #105: only with more rows of games than the tray shows — with fewer, the next round's rows stood
        // in view under the first ones, the same icons twice. Counted as TrayGrid sizes its cells.)
        val trayGap = Features.twinGap.state.value.dp
        val trayCell = minOf((maxWidth - pad * 2 - 10.dp - trayGap * (cols - 1)) / cols, gridH - 10.dp).coerceAtLeast(16.dp)
        val trayRows = ((gridH - 10.dp + trayGap) / (trayCell + trayGap)).toInt().coerceAtLeast(1)
        val loop = loopRowOn(c) && n > cols && (n + cols - 1) / cols >= trayRows + 2
        val v = rememberWalkedPlace(c.focus, n, loop, StyleLoop.walked, c.games, tap)
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
            TrayTools(tools, toolH, Modifier.fillMaxWidth().height(toolH))
            if (TwinNav.cats.value) {
                CatGrid(c, cols, Modifier.fillMaxWidth().height(gridH))
            } else if (n == 0) {
                Box(Modifier.fillMaxWidth().height(gridH), contentAlignment = Alignment.Center) {
                    Text(EmptyShelf.text, color = TrayMuted, fontSize = 13.sp)
                }
            } else {
                TrayGrid(c, keys, tap, loop, v, cols, inGames, Modifier.fillMaxWidth().height(gridH))
            }
            TrayOpen(c, tools, Modifier.fillMaxWidth().height(openH))
        }
    }
}

// ---- Update 325: the category page ----

/** A category's picture: its console's logo, else its icon. */
@Composable
private fun CatArt(st: StyleStop, ink: Color, onDark: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val p = st.platform
        if (p != null && !com.shelf.launcher.data.isCollectionKey(p)) PlatformLogo(p, st.label, Modifier.fillMaxSize(), textColor = ink, blackToWhite = onDark)
        else Icon(st.icon, st.label, tint = ink, modifier = Modifier.fillMaxSize(0.6f))
    }
}

/** The top screen on the category page: the category you're on big in the frame (tap: pick it). */
@Composable
private fun CatSlot(c: StyleCtx, slot: Dp) {
    val accent = ShelfTheme.accent
    val i = TwinNav.catCursor.intValue.coerceIn(0, (c.stops.size - 1).coerceAtLeast(0))
    val st = c.stops.getOrNull(i) ?: return
    Box(
        Modifier
            .size(slot)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.3f), Color.Transparent), center = center, radius = size.minDimension * 0.75f),
                    radius = size.minDimension * 0.75f
                )
            }
            .clip(RoundedCornerShape(slot * 0.16f))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), accent, Color.White.copy(alpha = 0.55f))))
            .padding(slot * 0.03f)
            .clip(RoundedCornerShape(slot * 0.14f))
            .background(if (topLight) Color.White else Color(0xFF0E131B))
            .padding(slot * 0.14f)
            .tapOnly { TwinNav.pick(c, i) },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = st, animationSpec = tween(160), label = "twinCat") { s ->
            CatArt(s, if (topLight) TrayInk else Color.White, !topLight, Modifier.fillMaxSize())
        }
    }
}

/** The card under it: the category's name and where it leads. */
@Composable
private fun CatCard(c: StyleCtx, compact: Boolean, modifier: Modifier) {
    val accent = ShelfTheme.accent
    val i = TwinNav.catCursor.intValue.coerceIn(0, (c.stops.size - 1).coerceAtLeast(0))
    val st = c.stops.getOrNull(i)
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (trayDark) TraySurface else Color.White.copy(alpha = 0.92f))
            .tapOnly { if (st != null) TwinNav.pick(c, i) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(if (compact) 8.dp else 10.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Crossfade(targetState = st, animationSpec = tween(160), label = "twinCatCard", modifier = Modifier.weight(1f)) { s ->
            Column(Modifier.fillMaxWidth()) {
                Text(s?.label.orEmpty(), color = TrayInk, fontSize = if (compact) 13.sp else 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (i == c.stop) "You're here · ${c.games.size} games" else "A: its games · B: back",
                    color = TrayMuted, fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The tray as the categories: the same square icons and ring as the games, each its console's logo and name. */
@Composable
private fun CatGrid(c: StyleCtx, cols: Int, modifier: Modifier) {
    val n = c.stops.size
    val density = LocalDensity.current
    val accent = ShelfTheme.accent
    val cur = TwinNav.catCursor.intValue.coerceIn(0, (n - 1).coerceAtLeast(0))
    BoxWithConstraints(modifier.clipToBounds()) {
        val gap = Features.twinGap.state.value.dp
        val inset = 5.dp
        val cellW = (maxWidth - inset * 2 - gap * (cols - 1)) / cols
        val cell = minOf(cellW, maxHeight - inset * 2).coerceAtLeast(16.dp)
        val pitch = cell + gap
        val rows = ((maxHeight - inset * 2 + gap) / pitch).toInt().coerceAtLeast(1)
        val gridW = cell * cols + gap * (cols - 1)
        val totalRows = (n + cols - 1) / cols
        val target = (cur / cols - (if (rows >= 3) 1 else 0)).coerceIn(0, (totalRows - rows).coerceAtLeast(0))
        val scroll = rememberTwinScroll(target.toFloat(), rows + 2f, n * 8 + cols)
        val pitchPx = with(density) { pitch.toPx() }
        val x0Px = with(density) { ((maxWidth - gridW) / 2).toPx() }
        val insetPx = with(density) { inset.toPx() }
        val shape = RoundedCornerShape(18)
        for (i in 0 until n) {
            val st = c.stops[i]
            val r = i / cols
            val col = i % cols
            key(st.label, st.platform, st.special) {
                val on = i == cur
                val ring by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, tween(if (on) 140 else 180), label = "twinCatRing")
                Column(
                    Modifier.size(cell)
                        .offset { IntOffset((x0Px + col * pitchPx).roundToInt(), (insetPx + (r - scroll.value) * pitchPx).roundToInt()) }
                        .graphicsLayer { val k = 1f + 0.06f * ring; scaleX = k; scaleY = k }
                        .trayRing(ring > 0.01f, accent.copy(alpha = ring), ring)
                        .clip(shape)
                        .background(if (i == c.stop) lerp(TraySurface, accent, 0.12f) else TraySurface)
                        .border(1.dp, TrayEdge, shape)
                        .tapOnly { if (on) TwinNav.pick(c, i) else TwinNav.catCursor.intValue = i }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CatArt(st, TrayInk, trayDark, Modifier.fillMaxWidth().weight(1f).padding(2.dp))
                    if (cell >= 44.dp) Text(
                        st.label, color = TrayMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        lineHeight = 9.sp
                    )
                }
            }
        }
    }
}

/** The tool buttons along the top (your round shortcuts, each in its own colour). */
@Composable
private fun TrayTools(tools: List<HomeShortcut>, h: Dp, modifier: Modifier) {
    val accent = ShelfTheme.accent
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        tools.forEachIndexed { i, t ->
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            Box(
                Modifier
                    .graphicsLayer { val k = if (on) 1.12f else 1f; scaleX = k; scaleY = k }
                    .size(h)
                    .clip(CircleShape)
                    .background(if (on) t.tint.copy(alpha = 0.22f) else TraySurface)
                    .border(if (on) 2.dp else 1.dp, if (on) accent else TrayEdge, CircleShape)
                    .tapOnly { t.onClick() },
                contentAlignment = Alignment.Center
            ) { Icon(t.icon, t.label, tint = lerp(t.tint, Color.Black, 0.3f), modifier = Modifier.size(h * 0.56f)) }
        }
    }
}

/** The grid of icons: only the rows in view (and one more each side), gliding as the one you're on moves. */
@Composable
private fun TrayGrid(c: StyleCtx, keys: List<String>, tap: LoopTap, loop: Boolean, v: Int, cols: Int, inGames: Boolean, modifier: Modifier) {
    val n = c.games.size
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds()) {
        // (Update 179: Layout options › Gap between icons, and the R1 Gaps tile.)
        val gap = Features.twinGap.state.value.dp
        // Room round the grid for the ring of the one you're on.
        val inset = 5.dp
        val cellW = (maxWidth - inset * 2 - gap * (cols - 1)) / cols
        val cell = minOf(cellW, maxHeight - inset * 2).coerceAtLeast(16.dp)
        val pitch = cell + gap
        val rows = ((maxHeight - inset * 2 + gap) / pitch).toInt().coerceAtLeast(1)
        val gridW = cell * cols + gap * (cols - 1)
        // One round of the grid: its games and the empty places that fill out its last row.
        val stride = ((n + cols - 1) / cols).coerceAtLeast(1) * cols
        val focusCell = if (loop) Math.floorDiv(v, n) * stride + v.mod(n) else c.focus
        val focusRow = Math.floorDiv(focusCell, cols)
        val totalRows = (n + cols - 1) / cols
        val before = if (rows >= 3) 1 else 0
        val target = if (loop) focusRow - before else (focusRow - before).coerceIn(0, (totalRows - rows).coerceAtLeast(0))
        // (A change of icons per row or of the list jumps instead of gliding.)
        val scroll = rememberTwinScroll(target.toFloat(), rows + 2f, n * 8 + cols)
        val first by remember(scroll) { derivedStateOf { kotlin.math.floor(scroll.value).toInt() } }
        val pitchPx = with(density) { pitch.toPx() }
        val x0Px = with(density) { ((maxWidth - gridW) / 2).toPx() }
        val insetPx = with(density) { inset.toPx() }
        for (r in first..first + rows + 1) {
            for (col in 0 until cols) {
                val cellIdx = r * cols + col
                val j = if (loop) cellIdx.mod(stride) else cellIdx
                if (j in 0 until n) {
                    val round = if (loop) Math.floorDiv(cellIdx, stride) else 0
                    key(if (loop) "${keys[j]}#$round" else keys[j]) {
                        TrayIcon(
                            c, j, on = cellIdx == focusCell, inGames = inGames,
                            modifier = Modifier.size(cell).offset {
                                IntOffset((x0Px + col * pitchPx).roundToInt(), (insetPx + (r - scroll.value) * pitchPx).roundToInt())
                            }
                        ) {
                            if (cellIdx == focusCell) c.actions.open()
                            else {
                                // A tap picks the round that was tapped (so the grid doesn't run off to another one).
                                if (loop) tap.virtual = round * n + j
                                c.actions.select(j)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One square icon; the one you're on lifted and ringed in the theme's colour. */
@Composable
private fun TrayIcon(c: StyleCtx, i: Int, on: Boolean, inGames: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val g = c.games[i]
    val accent = ShelfTheme.accent
    val lift = styleLift(on && inGames)
    // (Update 307: the ring fades onto the next icon and off the last one instead of jumping across.)
    val ring by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, tween(if (on) 140 else 180), label = "twinRing")
    val shape = RoundedCornerShape(18)
    Box(
        modifier
            .graphicsLayer { val k = 1f + 0.06f * lift; scaleX = k; scaleY = k }
            .trayRing(ring > 0.01f, accent.copy(alpha = (if (inGames) 1f else 0.45f) * ring), ring)
            .clip(shape)
            .background(TraySurface)
            .border(1.dp, TrayEdge, shape)
            .tapOnly(onTap)
            .padding(2.dp)
    ) { StyleArt(c, g, Modifier.fillMaxSize(), square = true, corner = 7.dp) }
}

/** The ring round the icon you're on: the theme's colour with a thin white line inside, just outside the icon. */
private fun Modifier.trayRing(on: Boolean, color: Color, fade: Float = 1f): Modifier = if (!on) this else this.drawWithCache {
    val w = 3.dp.toPx()
    val o = 3.dp.toPx()
    val outer = Stroke(w)
    val inner = Stroke(1.dp.toPx())
    val corner = CornerRadius(size.minDimension * 0.22f)
    val tl = Offset(-o, -o)
    val sz = Size(size.width + o * 2, size.height + o * 2)
    onDrawWithContent {
        drawContent()
        drawRoundRect(color, tl, sz, corner, style = outer)
        drawRoundRect(Color.White.copy(alpha = fade), Offset(-o + w / 2f, -o + w / 2f), Size(sz.width - w, sz.height - w), corner, style = inner)
    }
}

/** The Open bar along the bottom (on the tool buttons: the name of the one you're on). */
@Composable
private fun TrayOpen(c: StyleCtx, tools: List<HomeShortcut>, modifier: Modifier) {
    val accent = ShelfTheme.accent
    val titles = LocalTitleOptions.current
    val on = StyleNav.on(StyleNav.Zone.BAR, 0)
    val inTools = StyleNav.zone == StyleNav.Zone.TOP
    val open by rememberUpdatedState(c.actions.open)
    val g = c.game
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .graphicsLayer { val k = if (on) 1.03f else 1f; scaleX = k; scaleY = k }
            .clip(shape)
            .background(if (on) lerp(TraySurface, accent, 0.22f) else TraySurface)
            .border(if (on) 2.dp else 1.dp, if (on) accent else TrayEdge, shape)
            .tapOnly { if (TwinNav.cats.value) TwinNav.pick(c, TwinNav.catCursor.intValue) else if (c.game != null) open() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (inTools) {
            Text(tools.getOrNull(StyleNav.index)?.label.orEmpty(), color = TrayInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        } else if (TwinNav.cats.value) {
            // (Update 325: on the category page, the bar opens the category you're on.)
            val ci = TwinNav.catCursor.intValue
            Icon(Icons.Filled.PlayArrow, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open  ·  " + c.stops.getOrNull(ci)?.label.orEmpty(), color = TrayInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Icon(Icons.Filled.PlayArrow, null, tint = if (on) TrayInk else accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open", color = TrayInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (g != null) {
                Text(
                    "  ·  " + titles.display(g.name), color = TrayMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}
