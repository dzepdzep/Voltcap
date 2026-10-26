package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 330, phase 3: Night menu (2006) — a mid-2000s RPG's night-time menu, in Voltcap's own drawing.
 *
 * A deep blue night (crimson / emerald), a full moon high on the right glowing over still water that ripples its
 * reflection, faint slanted light. Your games as a list of slanted bars down the right, tipped a little; the one you're
 * on pulled out to the left in white with a pointer, the others dark glass with a coloured edge. On the left, the game
 * you're on framed big in a slanted frame, the category written huge and faint behind it, its time played and
 * achievements on a slanted tag. A opens its window — angular status panels sliding in (WinScenes.kt).
 * D-pad: ▲ ▼ through the list (Endless: on round), ◀ ▶ a screen of games at a time. Touch: tap a bar to pick it, again
 * to open it; drag the list up / down.
 */

/** Update 330: the list as drawn (read by its key handler). */
internal object NightNav {
    @Volatile var rows: Int = 7
}

/** Update 330: Night menu's D-pad: ▲ ▼ through the list, ◀ ▶ a screen at a time; past the ends what lies there. */
internal fun nightMenuKey(ctx: StyleKeyCtx): Boolean {
    val n = ctx.count
    if (n == 0) return false
    val f = ctx.focus
    val loop = StyleLoop.on && n > 1
    val target: Int? = when (ctx.key) {
        Key.DirectionUp -> if (f > 0) f - 1 else if (loop) n - 1 else return ctx.reach(true)
        Key.DirectionDown -> if (f < n - 1) f + 1 else if (loop) 0 else return ctx.reach(false)
        Key.DirectionLeft -> (f - NightNav.rows).coerceAtLeast(0)
        Key.DirectionRight -> (f + NightNav.rows).coerceAtMost(n - 1)
        else -> return false
    }
    if (target != null && target != f) { ctx.setFocus(target); ctx.sound(UiSound.MOVE) }
    else if (ctx.first) EdgeBump.hit(if (ctx.key == Key.DirectionLeft || ctx.key == Key.DirectionUp) -1 else 1)
    return ctx.take()
}

/** A parallelogram leaning right by [skew] of its height. */
internal fun slantShape(skew: Float) = GenericShape { size, _ ->
    val s = size.height * skew
    moveTo(s, 0f); lineTo(size.width, 0f); lineTo(size.width - s, size.height); lineTo(0f, size.height); close()
}

/** Update 330: the night: moon, water, slanted light. */
@Composable
internal fun NightMenuBackdrop(modifier: Modifier) {
    val col = Features.nmColor.state.value
    val moon = Features.nmMoon.state.value
    val water = Features.nmWater.state.value
    val ripple = water && rememberAnimationsEnabled() && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm
    val clock = rememberGcClock(ripple, 1f, 24)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(col.top), Color(col.bottom))))
        // Slanted shafts of light.
        for (i in 0 until 5) {
            val x = size.width * (0.1f + i * 0.22f)
            val p = Path().apply { moveTo(x, 0f); lineTo(x + size.width * 0.06f, 0f); lineTo(x - size.width * 0.2f, size.height); lineTo(x - size.width * 0.26f, size.height); close() }
            drawPath(p, Color(col.hi).copy(alpha = 0.03f))
        }
        val horizon = size.height * 0.72f
        val mc = Offset(size.width * 0.8f, size.height * 0.2f); val mr = size.height * 0.13f
        if (moon) {
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color(col.hi).copy(alpha = 0.08f), Color.Transparent), center = mc, radius = mr * 4f), mr * 4f, mc)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFF4), Color(0xFFE6EDF2)), center = Offset(mc.x - mr * 0.3f, mc.y - mr * 0.3f), radius = mr * 1.4f), mr, mc)
            drawCircle(Color(0xFFC9D2DA).copy(alpha = 0.4f), mr * 0.18f, Offset(mc.x + mr * 0.3f, mc.y + mr * 0.1f))
            drawCircle(Color(0xFFC9D2DA).copy(alpha = 0.3f), mr * 0.12f, Offset(mc.x - mr * 0.35f, mc.y + mr * 0.35f))
        }
        if (water) {
            drawRect(Brush.verticalGradient(listOf(Color(col.bottom).copy(alpha = 0.4f), Color(col.bottom)), startY = horizon, endY = size.height), Offset(0f, horizon), Size(size.width, size.height - horizon))
            drawLine(Color(col.hi).copy(alpha = 0.25f), Offset(0f, horizon), Offset(size.width, horizon), 1.5f)
            val t = clock.floatValue
            var y = horizon + 6f; var k = 0
            while (y < size.height) {
                val w = (size.height - horizon) * 0.5f * (0.4f + (y - horizon) / (size.height - horizon))
                val dx = sin(t * 1.3f + k * 0.9f) * w * 0.15f
                if (moon) drawLine(Color.White.copy(alpha = 0.35f * (1f - (y - horizon) / (size.height - horizon))), Offset(mc.x - w * 0.5f + dx, y), Offset(mc.x + w * 0.5f + dx, y), 2f)
                drawLine(Color(col.hi).copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
                y += 9f + k * 0.6f; k++
            }
        }
    }
}

@Composable
internal fun StyleNightMenu(c: StyleCtx, modifier: Modifier = Modifier) {
    val loop = loopRowOn(c)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleLoop.on = loop
    }
    if (c.games.isEmpty()) StyleEmpty(modifier) else NightList(c, loop, modifier)
}

@Composable
private fun NightList(c: StyleCtx, loop: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val focus = c.focus.coerceIn(0, count - 1)
    val animate = rememberAnimationsEnabled()
    val rowsN = Features.nmRows.state.value.n
    SideEffect { NightNav.rows = rowsN }
    val col = Features.nmColor.state.value
    val hi = Color(col.hi)
    val titles = LocalTitleOptions.current
    val density = LocalDensity.current
    val scroll = remember { Animatable(focus.toFloat()) }
    LaunchedEffect(focus, count) {
        var cur = scroll.value
        if (loop && count > 1) {
            while (focus - cur > count / 2f) cur += count
            while (cur - focus > count / 2f) cur -= count
            scroll.snapTo(cur)
        }
        if (!animate || abs(cur - focus) > rowsN * 3f) scroll.snapTo(focus.toFloat())
        else scroll.animateTo(focus.toFloat(), spring(dampingRatio = 0.85f, stiffness = 320f))
    }
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    val g = c.games[focus]
    BoxWithConstraints(modifier.fillMaxSize()) {
        val hPx = with(density) { maxHeight.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        // The category, huge and faint, behind the framed cover.
        if (caption) Text(
            c.categoryLabel.uppercase(), color = hi.copy(alpha = 0.09f), fontSize = 120.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-12).dp, y = maxHeight * 0.02f).graphicsLayer { rotationZ = -6f }
        )
        // The game you're on, framed big in a slanted frame on the left.
        val frameH = maxHeight * 0.66f
        Box(
            Modifier.padding(start = maxWidth * 0.05f, top = maxHeight * 0.12f).size(frameH * 0.78f, frameH)
                .graphicsLayer { rotationZ = -3f }
        ) {
            Box(Modifier.fillMaxSize().offset(10.dp, 10.dp).clip(slantShape(0.12f)).background(hi.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxSize().clip(slantShape(0.12f)).background(Color.White).padding(5.dp).clip(slantShape(0.12f)).background(Color(col.bottom)).gameOrigin(g.stableKey, true)) {
                val cover = c.cover(g)
                androidx.compose.animation.Crossfade(targetState = cover, label = "nmCover", animationSpec = tween(if (animate) 220 else 0)) { cv ->
                    if (cv != null) AsyncImage(rememberStyleArtRequest(cv, trim = true), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                    else StyleNamePlate(titles.display(g.name))
                }
            }
        }
        // Its tag under the frame: console, time played, achievements.
        Row(Modifier.padding(start = maxWidth * 0.04f, top = maxHeight * 0.81f).graphicsLayer { rotationZ = -3f }) {
            Box(Modifier.clip(slantShape(0.4f)).background(hi).padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(titles.console(g.platform).uppercase(), color = Color(col.bottom), fontSize = 13.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.clip(slantShape(0.4f)).background(Color.White).padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(listOfNotNull(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), c.progress(g)?.let { (e, t) -> "★ $e/$t" }).joinToString("   "),
                    color = Color(0xFF101828), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1)
            }
        }
        // The list of slanted bars down the right, tipped a little.
        val rowH = hPx / (rowsN + 1.2f)
        val listLeft = wPx * 0.44f
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = -4f }
                .pointerInput(count) {
                    var acc = 0f
                    detectVerticalDragGestures(onDragStart = { acc = 0f }) { ch, dy ->
                        ch.consume(); acc += dy
                        val s = rowH * 0.8f
                        while (acc <= -s) { acc += s; val n = latestFocus + 1; if (n < latestCount) select(n) else if (loop) select(0) }
                        while (acc >= s) { acc -= s; val p = latestFocus - 1; if (p >= 0) select(p) else if (loop) select(latestCount - 1) }
                    }
                }
        ) {
            val half = rowsN / 2 + 1
            val base = scroll.value.roundToInt()
            for (off in -half..half) {
                var i = base + off
                if (loop) i = ((i % count) + count) % count else if (i !in 0 until count) continue
                val gg = c.games[i]
                key(base + off) {
                    val on = i == focus
                    val pull by animateFloatAsState(if (on) 1f else 0f, if (animate) spring(dampingRatio = 0.7f, stiffness = 500f) else tween(0), label = "nmPull")
                    Box(
                        Modifier.size(with(density) { (wPx - listLeft + rowH).toDp() }, with(density) { (rowH * 0.82f).toDp() })
                            .graphicsLayer {
                                val d = (base + off) - scroll.value
                                translationX = listLeft - pull * wPx * 0.04f + abs(d) * rowH * 0.12f
                                translationY = hPx / 2f + d * rowH - size.height / 2f
                                alpha = (1f - ((abs(d) - rowsN / 2f) / 1.2f).coerceIn(0f, 1f))
                            }
                            .zIndex(if (on) 2f else 1f)
                            .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else select(i) } }
                    ) { NightBar(c, gg, on, pull, col, rowH) }
                }
            }
        }
    }
}

/** One game's bar: dark glass with a coloured edge; the one you're on white, pulled out, with a pointer. */
@Composable
private fun NightBar(c: StyleCtx, g: Game, on: Boolean, pull: Float, col: Features.NmColor, rowH: Float) {
    val titles = LocalTitleOptions.current
    val hi = Color(col.hi)
    val density = LocalDensity.current
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // The pointer.
        Canvas(Modifier.size(with(density) { (rowH * 0.42f).toDp() }).graphicsLayer { alpha = pull; translationX = -(1f - pull) * 20f }) {
            val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }
            drawPath(p, hi)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.fillMaxHeight().weight(1f).clip(slantShape(0.45f))
                .background(if (on) Brush.horizontalGradient(listOf(Color.White, Color(0xFFE8F4FF))) else Brush.horizontalGradient(listOf(Color(col.top).copy(alpha = 0.85f), Color(col.bottom).copy(alpha = 0.6f))))
                .then(if (!on) Modifier.border(1.5.dp, hi.copy(alpha = 0.55f), slantShape(0.45f)) else Modifier)
                .gameOrigin(g.stableKey, false)
        ) {
            Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight(0.78f).aspectRatio(1.3f).clip(slantShape(0.3f)).background(Color.Black)) {
                    c.cover(g)?.let { AsyncImage(rememberStyleArtRequest(it, trim = true), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(14.dp))
                Text(titles.display(g.name).uppercase(), color = if (on) Color(0xFF0B1424) else Color.White, fontSize = if (on) 22.sp else 18.sp,
                    fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Update 330: Settings › Home › Layout options (Night menu). Every one its own "nm_…" Features pref. */
@Composable
internal fun NightMenuSettings() {
    MutedNote(
        "Your games as slanted bars under a full moon over water: the one you're on pulled out in white with a pointer and " +
            "framed big on the left. ▲ ▼ through the list, ◀ ▶ a screen at a time, A opens the game, L2 / R2 the next category. " +
            "Tap a bar to pick it, again to open it; drag the list."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Games on the screen", Features.NmRows.values(), Features.nmRows.state.value, { it.label }) { Features.nmRows.value = it }
    StyleChoice("Colour", Features.NmColor.values(), Features.nmColor.state.value, { it.label }) { Features.nmColor.value = it }
    Toggle(Icons.Filled.NightsStay, "Full moon", "The moon high on the right, glowing. Off: just the night.", Features.nmMoon)
    Toggle(Icons.Filled.Water, "Water", "Still water below with the moon's reflection rippling. Off: no water.", Features.nmWater)
}
