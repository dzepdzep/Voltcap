package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Circuit board (update 106): Voltcap's own home layout — no console's look, made for the name.
 *
 * The games are chips on a circuit board, plugged into one glowing power line across the middle of the screen,
 * one above it, the next below it, zig-zagging along. Each chip has its silkscreen label (U1, U2…) and a trace down to
 * its pad on the line. The game you're on is powered: its trace lights up, it lifts, and a spark runs along the line to
 * it as you move. Current flows along the line while you rest. Top left the category on a small LCD (◀ ▶ there, or
 * L2 / R2), top right a clock and battery LCD. Bottom left a readout of the game (name, console, time played, an
 * achievement "charge" bar); bottom right a power switch (Play) and Details. ◀ ▶ move along the board, ▼ reaches
 * Play and Details, ▲ the category.
 */

private val LcdGreen = Color(0xFF0D1410)
private val LcdEdgeGreen = Color(0xFF2C3A31)

/** Update 112: the LCDs follow the board's colours (the theme's surface on a theme-coloured board). */
private val Lcd: Color
    @Composable get() = when (Features.circuitColors.state.value) {
        Features.CircuitColors.THEME -> ShelfTheme.colors.surface
        Features.CircuitColors.GREEN -> LcdGreen
        Features.CircuitColors.DARK -> Color(0xFF0E1013)
    }
private val LcdEdge: Color
    @Composable get() = when (Features.circuitColors.state.value) {
        Features.CircuitColors.THEME -> ShelfTheme.colors.surfaceRaised
        Features.CircuitColors.GREEN -> LcdEdgeGreen
        Features.CircuitColors.DARK -> Color(0xFF2A2E35)
    }

/** The board's colour: the green board's green, else the theme's accent in the traces. */
@Composable
private fun boardInk(): Color = if (Features.circuitColors.state.value == Features.CircuitColors.GREEN) Color(0xFF7CE0A0) else ShelfTheme.accent

@Composable
internal fun StyleCircuit(c: StyleCtx, modifier: Modifier) {
    val g = c.game
    val ink = boardInk()
    // Update 109: a part hidden in Edit Home is skipped by the D-pad.
    val lcdOff = LayoutParts.off(HomeLayout.CIRCUIT, "category")
    val switchOff = LayoutParts.off(HomeLayout.CIRCUIT, "switch")
    SideEffect {
        StyleNav.side = emptyList()
        // ▲: the category LCD (◀ ▶ step through categories there, A goes to the next).
        StyleNav.top = if (c.stops.size > 1 && !lcdOff) listOf({ c.actions.category(1) }) else emptyList()
        StyleNav.topStart = 0
        // ▼ (update 112): the power switch (Play), then Settings (the game's own settings) and Details (its window).
        StyleNav.bar = if (g == null || switchOff) emptyList() else listOf({ c.actions.play(g) }, { c.actions.options(g) }, { c.actions.open() })
    }
    BoxWithConstraints(modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.editablePart(LayoutParts.id(HomeLayout.CIRCUIT, "category"), "Category LCD")) { CircuitCategory(c, ink) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.editablePart(LayoutParts.id(HomeLayout.CIRCUIT, "clock"), "Clock LCD")) { CircuitClock(ink) }
            }
            if (g == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                CircuitBoard(c, ink, Modifier.weight(1f).fillMaxWidth())
                // Update 112: a fixed height, so a game with an achievement bar (or a long name) never moves the board or the
                // buttons; the buttons are pinned bottom right (moved only in Edit Home), whatever the readout does.
                Box(Modifier.fillMaxWidth().height(if (compact) 78.dp else 96.dp).padding(bottom = if (compact) 2.dp else 6.dp)) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = g, label = "circuitReadout", transitionSpec = { gameSwap() },
                        contentAlignment = Alignment.BottomStart,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.58f).editablePart(LayoutParts.id(HomeLayout.CIRCUIT, "readout"), "Game readout")
                    ) { shown -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) { CircuitReadout(c, shown, ink, compact) } }
                    Box(Modifier.align(Alignment.BottomEnd).editablePart(LayoutParts.id(HomeLayout.CIRCUIT, "switch"), "Power switch, Settings & Details")) { CircuitSwitch(c, g, ink, compact) }
                }
            }
        }
    }
}

/** The category on a small LCD; ◀ ▶ (or a tap on the arrows) step through them. */
@Composable
private fun CircuitCategory(c: StyleCtx, ink: Color) {
    val on = StyleNav.on(StyleNav.Zone.TOP, 0)
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).background(Lcd.copy(alpha = 0.92f))
            .border(if (on) 2.dp else 1.dp, if (on) ink else LcdEdge, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("◀", color = ink.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures { c.actions.category(-1) } })
        Spacer(Modifier.width(8.dp))
        CategoryTitle.Native() // (Update 212: the layout's own category title.)
        Text(
            c.categoryLabel.uppercase(), color = ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false, modifier = Modifier.widthIn(max = 260.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("▶", color = ink.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures { c.actions.category(1) } })
    }
}

/** Clock and battery on an LCD. */
@Composable
private fun CircuitClock(ink: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(15_000) } }
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    val battery = rememberBatteryInfo()
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).background(Lcd.copy(alpha = 0.92f)).border(1.dp, LcdEdge, shape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(batteryIconFor(battery.percent, battery.charging), null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text("${battery.percent}%", color = ink.copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
        Text(time, color = ink, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * The board: the games as chips zig-zagging along the power line, the one you're on at about a third of the width.
 * Swipe to move along it, tap a chip to pick it (tap it again to open it).
 */
@Composable
private fun CircuitBoard(c: StyleCtx, ink: Color, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    // Update 125: with Endless on the board goes round — the chips repeat, and from the last game the board glides on
    // to the first ([vf] is the place on that endless board, EndlessRow.kt).
    val loop = loopRowOn(c)
    val vf = rememberLoopFocus(c)
    // Where the board is (in games): glides between them, and the spark runs while it does.
    val pos = remember { Animatable(vf.toFloat()) }
    LaunchedEffect(vf) {
        // A long jump (a new category, the first / last game): straight there, no spark racing across the board.
        if (kotlin.math.abs(pos.value - vf) > 6f) pos.snapTo(vf.toFloat())
        else pos.animateTo(vf.toFloat(), tween(320, easing = FastOutSlowInEasing))
    }
    val moving = !LocalLiteEffects.current && rememberAnimationsEnabled() && !HomeIdle.calm && Features.circuitCurrent.state.value
    val clock = MediaClock.rememberFrameTime(running = moving)
    val labels = Features.circuitLabels.state.value
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != com.shelf.launcher.data.FocusEffect.NONE)
    BoxWithConstraints(
        modifier.pointerInput(count) {
            var acc = 0f
            detectHorizontalDragGestures(onDragEnd = { acc = 0f }) { change, dx ->
                change.consume()
                acc += dx
                val step = 56.dp.toPx()
                var target = latestFocus
                while (acc <= -step) { acc += step; target = if (loop) (target + 1).mod(count) else (target + 1).coerceAtMost(count - 1); select(target) }
                while (acc >= step) { acc -= step; target = if (loop) (target - 1).mod(count) else (target - 1).coerceAtLeast(0); select(target) }
            }
        }.categorySlide()
    ) {
        val k = GameSize.scale(HomeLayout.CIRCUIT)
        val aspect = styleAspect(c.type).coerceIn(0.6f, 1.8f)
        // A chip: as tall as fits above (or below) the line with its trace and label.
        val chipH = ((maxHeight / 2f - 30.dp) * 0.86f * k).coerceIn(44.dp, 220.dp)
        val chipW = chipH * aspect
        val step = chipW * 0.62f
        val busY = maxHeight / 2f
        val anchorX = maxWidth * 0.3f
        val lead = 14.dp
        // (Pass 5, D57: the chips on screen as a derived range — read raw here, the whole board was re-composed on every
        // frame of a glide; it changes only when a chip comes in or goes out at an edge.)
        val before = (anchorX / step).toInt() + 2; val after = ((maxWidth - anchorX) / step).toInt() + 2
        val span by remember(loop, count, before, after) {
            androidx.compose.runtime.derivedStateOf {
                val f = (kotlin.math.floor(pos.value).toInt() - before).let { if (loop) it else it.coerceAtLeast(0) }
                val l = (kotlin.math.ceil(pos.value).toInt() + after).let { if (loop) it else it.coerceAtMost(count - 1) }
                f..l
            }
        }
        val first = span.first; val last = span.last

        // The traces, pads and the power line under the chips.
        Canvas(Modifier.fillMaxSize()) {
            val y = busY.toPx()
            val stepPx = step.toPx()
            val ax = anchorX.toPx()
            val leadPx = lead.toPx()
            val chipHPx = chipH.toPx()
            // The line: a soft glow under a bright core.
            drawLine(ink.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.75f), Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            // Current flowing along it (dashes moving right), while you rest on a game.
            if (moving) {
                val phase = -((clock.longValue / 1_000_000L) % 1200L) / 1200f * 24.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 19.dp.toPx()), phase)
                )
            }
            for (i in first..last) {
                val x = ax + (i - pos.value) * stepPx
                val up = Math.floorMod(i, 2) == 0
                val on = i == vf
                val col = if (on) ink else ink.copy(alpha = 0.35f)
                val endY = if (up) y - leadPx else y + leadPx
                // The trace from the pad to the chip, with a little dog-leg.
                drawLine(col, Offset(x, y), Offset(x, endY), strokeWidth = (if (on) 3.dp else 2.dp).toPx())
                drawCircle(if (on) Color.White else col, radius = (if (on) 5.dp else 3.5.dp).toPx(), center = Offset(x, y))
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = 1.5.dp.toPx(), center = Offset(x, y))
                // A via beside the pad.
                drawCircle(col, radius = 3.dp.toPx(), center = Offset(x + stepPx * 0.5f, if (up) y + leadPx * 0.9f else y - leadPx * 0.9f), style = Stroke(1.dp.toPx()))
                if (on) {
                    // The chip's outline glow on the board.
                    val top = if (up) endY - chipHPx else endY
                    drawRect(ink.copy(alpha = 0.12f + 0.08f * pulse), Offset(x - chipW.toPx() / 2 - 6.dp.toPx(), top - 6.dp.toPx()),
                        androidx.compose.ui.geometry.Size(chipW.toPx() + 12.dp.toPx(), chipHPx + 12.dp.toPx()))
                }
            }
            // The spark: runs along the line to the game you're on while the board glides.
            val gap = vf - pos.value
            if (kotlin.math.abs(gap) > 0.02f) {
                val padX = ax + (vf - pos.value) * stepPx
                val tail = (if (gap > 0) -1f else 1f) * minOf(kotlin.math.abs(gap) * stepPx, 90.dp.toPx())
                drawLine(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.White), startX = padX + tail, endX = padX),
                    Offset(padX + tail, y), Offset(padX, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
                )
                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(padX, y))
                drawCircle(ink.copy(alpha = 0.45f), radius = 12.dp.toPx(), center = Offset(padX, y))
            }
        }
        // The chips.
        // (Update 246, #60: the places picked before the loop — a `continue` in it made Compose give each pass its
        // own group, so a step that moved every game one place along threw them all away and built them again.)
        for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - vf, count, minOf(kotlin.math.ceil(anchorX / step).toInt(), (count - 1) / 2))) }) {
            // (Update 174: each game once in a short category.)
            // (Update 226, #105: as many before yours as fit left of it — the rest go after it, on screen.)
            val real = if (loop) i.mod(count) else i
            val g = c.games[real]
            val up = Math.floorMod(i, 2) == 0
            val on = i == vf
            val sel = on && StyleNav.zone == StyleNav.Zone.GAMES
            androidx.compose.runtime.key(if (loop) "${g.stableKey}#${Math.floorDiv(i, count)}" else g.stableKey) {
                val lift = styleLift(sel)
                // (Placed in the layout step, so the glide moves the chip without composing it again.)
                fun xNow() = anchorX + step * (i - pos.value) - chipW / 2
                val y = if (up) busY - lead - chipH else busY + lead
                Box(
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(xNow().roundToPx(), y.roundToPx()) }
                        .size(chipW, chipH)
                        .zIndex(if (on) 2f else 1f)
                        // Update 125: the game window grows out of the chip, like on the other layouts.
                        .then(if (on) Modifier.focusedCard() else Modifier)
                        .graphicsLayer {
                            val s = 1f + 0.1f * lift
                            scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (up) 1f else 0f)
                            alpha = if (on) 1f else 0.82f
                        }
                        .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else select(real) } }
                ) {
                    StyleArt(c, g, Modifier.fillMaxSize(), corner = 4.dp, focused = sel, fx = if (sel) pulse else null, ring = ink)
                    // The chip's legs along the side facing away from the line.
                    Canvas(Modifier.fillMaxSize()) {
                        val legs = 6
                        val lw = 2.dp.toPx(); val lh = 4.dp.toPx()
                        val ly = if (up) 0f else size.height - lh
                        for (n in 0 until legs) {
                            val lx = size.width * (n + 1) / (legs + 1f) - lw / 2
                            drawRect(Color(0xFFB8BEC6).copy(alpha = if (on) 0.9f else 0.55f), Offset(lx, if (up) ly - lh else ly + lh), androidx.compose.ui.geometry.Size(lw, lh))
                        }
                    }
                }
                // Silkscreen label beside the chip.
                if (labels) Text(
                    "U${real + 1}", color = (if (on) ink else Color.White.copy(alpha = 0.45f)), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((xNow() + chipW + 3.dp).roundToPx(), (if (up) busY - lead - 14.dp else busY + lead + 2.dp).roundToPx()) }
                )
            }
        }
    }
}

/** The game's readout: name, console, time played and its achievements as a charge bar. */
@Composable
private fun CircuitReadout(c: StyleCtx, g: com.shelf.launcher.data.Game, ink: Color, compact: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val titles = LocalTitleOptions.current
    Column(
        Modifier.widthIn(max = 460.dp).clip(shape).background(Lcd.copy(alpha = 0.9f)).border(1.dp, LcdEdge, shape)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp)
    ) {
        FitText(titles.display(g.name), Color.White, maxSize = if (compact) 16.sp else 20.sp, minSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
        Text(
            titles.console(g.platform).uppercase() + "  ·  " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null).uppercase(),
            color = ink.copy(alpha = 0.85f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false
        )
        val p = c.progress(g)
        if (p != null) {
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", color = ink, fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                // A battery-like charge: ten cells.
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val lit = ((p.first.toFloat() / p.second) * 10f).toInt().coerceIn(0, 10)
                    for (n in 0 until 10) Box(Modifier.size(9.dp, 8.dp).background(if (n < lit) ink else ink.copy(alpha = 0.15f)))
                }
                Spacer(Modifier.width(6.dp))
                Text("${p.first}/${p.second}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Play as a power switch; under it two small LCD buttons (update 112): Settings (the game's own settings) and Details
 * (its details window). Always the same size, whatever the game.
 */
@Composable
private fun CircuitSwitch(c: StyleCtx, g: com.shelf.launcher.data.Game, ink: Color, compact: Boolean) {
    val playOn = StyleNav.on(StyleNav.Zone.BAR, 0)
    val setOn = StyleNav.on(StyleNav.Zone.BAR, 1)
    val moreOn = StyleNav.on(StyleNav.Zone.BAR, 2)
    val play by rememberUpdatedState(c.actions.play)
    val options by rememberUpdatedState(c.actions.options)
    val open by rememberUpdatedState(c.actions.open)
    val lcd = Lcd
    val lcdEdge = LcdEdge
    val knob by androidx.compose.animation.core.animateFloatAsState(if (playOn) 1f else 0f, tween(200), label = "circuitKnob")
    Column(horizontalAlignment = Alignment.End) {
        val w = if (compact) 116.dp else 140.dp
        val h = if (compact) 40.dp else 48.dp
        Box(
            Modifier.size(w, h).clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(lcd, if (playOn) ink.copy(alpha = 0.55f) else lcdEdge)))
                .border(if (playOn) 2.dp else 1.dp, if (playOn) ink else lcdEdge, RoundedCornerShape(50))
                .pointerInput(g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 0); play(g) } }
        ) {
            Text(
                if (c.lastPlayed(g) != null) "CONTINUE" else "PLAY", color = Color.White, fontSize = if (compact) 11.sp else 13.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)
            )
            // The switch's knob slides over when it's picked.
            Box(
                Modifier.align(Alignment.CenterEnd).padding(4.dp).offset(x = -(w - h) * 0.12f * (1f - knob)).size(h - 8.dp)
                    .clip(RoundedCornerShape(50)).background(if (playOn) Color.White else Color(0xFF9AA39D))
            )
        }
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(6.dp)
        Row(Modifier.width(w), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable
            fun Lcdb(label: String, on: Boolean, index: Int, m: Modifier, action: () -> Unit) {
                Box(
                    m.clip(shape).background(lcd.copy(alpha = 0.9f)).border(if (on) 2.dp else 1.dp, if (on) ink else lcdEdge, shape)
                        .pointerInput(index) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, index); action() } }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (on) ink else Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
            Lcdb("SETTINGS", setOn, 1, Modifier.weight(1f)) { options(g) }
            Lcdb("DETAILS", moreOn, 2, Modifier.weight(1f)) { open() }
        }
    }
}

/** The circuit board behind the layout (whole screen): faint traces, vias and pads, in the board's colour. */
@Composable
internal fun CircuitBackdrop(modifier: Modifier = Modifier) {
    val ink = boardInk()
    val bg = when (Features.circuitColors.state.value) {
        Features.CircuitColors.GREEN -> Brush.verticalGradient(listOf(Color(0xFF0B1A12), Color(0xFF06100B)))
        Features.CircuitColors.DARK -> Brush.verticalGradient(listOf(Color(0xFF0D0F14), Color(0xFF07080B)))
        // Update 112: the board in your theme's colours (its background, a touch lighter at the top).
        Features.CircuitColors.THEME -> ShelfTheme.colors.let { t -> Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(t.background, t.surface, 0.5f), t.background)) }
    }
    Canvas(modifier.background(bg)) {
        val rnd = java.util.Random(4242)
        val grid = 28.dp.toPx()
        val cols = (size.width / grid).toInt() + 1
        val rows = (size.height / grid).toInt() + 1
        val line = ink.copy(alpha = 0.07f)
        repeat((cols * rows) / 5) {
            var x = rnd.nextInt(cols) * grid
            var y = rnd.nextInt(rows) * grid
            val segs = 2 + rnd.nextInt(3)
            repeat(segs) {
                val horizontal = rnd.nextBoolean()
                val len = (1 + rnd.nextInt(4)) * grid * (if (rnd.nextBoolean()) 1 else -1)
                val nx = if (horizontal) x + len else x
                val ny = if (horizontal) y else y + len
                drawLine(line, Offset(x, y), Offset(nx, ny), strokeWidth = 1.5.dp.toPx())
                x = nx; y = ny
            }
            drawCircle(line.copy(alpha = 0.12f), radius = 3.dp.toPx(), center = Offset(x, y), style = Stroke(1.dp.toPx()))
        }
    }
}
