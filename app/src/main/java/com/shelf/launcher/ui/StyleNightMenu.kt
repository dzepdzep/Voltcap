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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
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

/**
 * Update 529 (owner, 13 Nov: "make Moonlight feel much closer to the fluid, stylish and expressive menus of Persona 3 Reload"):
 * the Night menu's motion, one language for everything - things travel along the menu's slant, in layers that follow one
 * another closely (a few tens of ms apart), ease out and settle without overshoot; a light blue wave runs across first when
 * something new arrives. Everything is drawn from progress values read in graphicsLayer / draw (no recomposition per frame),
 * nothing loops once settled, and keys are never held up: every movement is interruptible and the D-pad acts at once.
 * Lite: the same moves, shorter, without the waves and the list's bend. Motion off / reduced motion: MotionLevel / the
 * system animator scale finish them at once.
 */
internal object NightMotion {
    /** How far the Night menu's game window is open (0 closed … 1 open), written by its window ([NightStatus]); the list steps back. */
    val window = androidx.compose.runtime.mutableFloatStateOf(0f)
    /** Entrance: the whole timeline in ms (Full / Lite). */
    const val ENTER_MS = 520
    const val ENTER_LITE_MS = 300
    /** Category change: the games sweep in along the slant, row by row. */
    const val CAT_MS = 380
    /** Layer [i]'s own progress at overall progress [t]: it starts [gap] after the one before it and takes [span]. Pure. */
    fun layer(t: Float, i: Float, gap: Float, span: Float): Float =
        if (t >= 1f) 1f else ((t - i * gap) / span).coerceIn(0f, 1f) // (whatever the stagger, everything is in place at the end)
    /** A cubic ease-out (fast start, gentle settle, no overshoot). Pure. */
    fun ease(k: Float): Float { val c = k.coerceIn(0f, 1f); val r = 1f - c; return 1f - r * r * r }
    /**
     * The list's bend while it glides: the bars further from the middle trail out a little in proportion to how far the glide
     * still has to go ([lag], rows), relaxing to a straight column at rest. Pure.
     */
    fun bend(lag: Float, d: Float, rowH: Float): Float = kotlin.math.abs(lag.coerceIn(-2f, 2f)) * kotlin.math.abs(d) * rowH * 0.07f
}

/** Update 330: Night menu's D-pad: ▲ ▼ through the list, ◀ ▶ a screen at a time; past the ends what lies there. */
internal fun nightMenuKey(ctx: StyleKeyCtx): Boolean {
    val n = ctx.count
    if (n == 0) return false
    val f = ctx.focus
    val loop = StyleLoop.on && n > 1
    val target: Int? = when (ctx.key) {
        // (Batch 523: every ▲ ▼ step tells the list which way it went - StyleLoop - so fast presses and two or three games
        // step on round instead of bouncing back the short way.)
        Key.DirectionUp -> (if (f > 0) f - 1 else if (loop) n - 1 else return ctx.reach(true)).also { if (loop) StyleLoop.wrapped(-1) }
        Key.DirectionDown -> (if (f < n - 1) f + 1 else if (loop) 0 else return ctx.reach(false)).also { if (loop) StyleLoop.wrapped(1) }
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
    // (Update 529: Lite keeps the moves, shorter, without the waves and the bend - unless "Full animations on Lite" is on.)
    val lite = LocalLiteEffects.current && !Features.motionOnLite.state.value
    val rowsN = Features.nmRows.state.value.n
    SideEffect { NightNav.rows = rowsN }
    val col = Features.nmColor.state.value
    val hi = Color(col.hi)
    val titles = LocalTitleOptions.current
    val density = LocalDensity.current
    // (Batch 523: the list's place on the endless line counts every step - rememberLoopFocus - instead of guessing the
    // shorter way after each one: with two or three games, or fast presses, the selection bounced back and forth.)
    val target = rememberLoopFocus(focus, count, loop && count > 1, StyleLoop.tick, StyleLoop.step, c.games)
    val targetNow by rememberUpdatedState(target)
    val scroll = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target, count) {
        if (!animate || abs(scroll.value - target) > rowsN * 3f) scroll.snapTo(target.toFloat())
        else scroll.animateTo(target.toFloat(), spring(dampingRatio = 0.85f, stiffness = 320f))
    }
    // Update 529: the entrance - a wave runs across, then the layers follow it in: the category behind, the framed cover, its
    // tag, the bars one after another from the top. It only draws; the D-pad works from the first frame.
    val enter = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enter.value < 1f) enter.animateTo(1f, tween(if (lite) NightMotion.ENTER_LITE_MS else NightMotion.ENTER_MS, easing = androidx.compose.animation.core.LinearEasing))
    }
    // Update 529: a category change - the new games sweep in along the slant from the side the change came from, row after row,
    // behind a light wave (the GAMES only: the frame, the category's name, the tag and the night stay put and change at once -
    // permanent rule 14). As categorySlide(): the frame the category changes in is already drawn at the start position.
    MotionPrefs.observe()
    val catOn = animate && Features.categorySlide.state.value && !lite && MotionPrefs.page() != Features.PageAnim.NONE
    val catTick = CategorySlide.tick
    var catSeen by remember { androidx.compose.runtime.mutableIntStateOf(catTick) }
    val cat = remember { Animatable(1f) }
    LaunchedEffect(catTick) {
        if (catTick == catSeen) return@LaunchedEffect
        if (!catOn) { catSeen = catTick; return@LaunchedEffect }
        cat.snapTo(0f); catSeen = catTick
        androidx.compose.runtime.withFrameNanos { }
        cat.animateTo(1f, tween(NightMotion.CAT_MS, easing = androidx.compose.animation.core.LinearEasing))
    }
    val catDir = CategorySlide.dir.toFloat()
    fun catProgress(): Float = if (catOn && CategorySlide.tick != catSeen) 0f else cat.value
    // Update 529: the game you're on changed by a step (not by a category change): the framed cover takes the step - a short
    // dip and tilt the way the list went, settling back.
    val kick = remember { Animatable(0f) }
    val lastTarget = remember { intArrayOf(target) }
    LaunchedEffect(target) {
        val step = (target - lastTarget[0]).coerceIn(-1, 1)
        lastTarget[0] = target
        if (step == 0 || !animate || CategorySlide.justChanged()) { kick.snapTo(0f); return@LaunchedEffect }
        kick.snapTo(step.toFloat())
        kick.animateTo(0f, spring(dampingRatio = 1f, stiffness = if (lite) 900f else 420f))
    }
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    val g = c.games[focus]
    BoxWithConstraints(modifier.fillMaxSize()) {
        val hPx = with(density) { maxHeight.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        // The entrance's wave: a wide slanted band of light crossing the night once, left to right (Full only; drawn only
        // while it runs - nothing to draw afterwards).
        if (!lite) Canvas(Modifier.fillMaxSize()) {
            val t = enter.value
            if (t >= 1f) return@Canvas
            nightWave(NightMotion.ease(NightMotion.layer(t, 0f, 0f, 0.72f)), 1f, hi, 0.42f)
        }
        // The category, huge and faint, behind the framed cover.
        if (caption) Text(
            c.categoryLabel.uppercase(), color = hi.copy(alpha = 0.09f), fontSize = 120.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-12).dp, y = maxHeight * 0.02f).graphicsLayer {
                rotationZ = -6f
                // (It drifts in from the left, slower than the rest - the farthest layer.)
                val e = NightMotion.ease(NightMotion.layer(enter.value, 0f, 0f, 0.9f))
                translationX = -(1f - e) * 140.dp.toPx() - NightMotion.window.floatValue * 30.dp.toPx()
                alpha = e * (1f - 0.6f * NightMotion.window.floatValue)
            }
        )
        // The game you're on, framed big in a slanted frame on the left.
        val frameH = maxHeight * 0.66f
        Box(
            Modifier.padding(start = maxWidth * 0.05f, top = maxHeight * 0.12f).size(frameH * 0.78f, frameH)
                .graphicsLayer {
                    val e = NightMotion.ease(NightMotion.layer(enter.value, 1f, 0.06f, 0.55f))
                    val k = kick.value
                    val w = NightMotion.window.floatValue
                    // Arrives along the slant from the lower left; takes each step with a small dip and tilt; while the game's
                    // window is open it steps aside (the window grew out of it).
                    translationX = -(1f - e) * 90.dp.toPx() - w * 24.dp.toPx()
                    translationY = (1f - e) * 40.dp.toPx() + k * 9.dp.toPx()
                    rotationZ = -3f - (1f - e) * 4f + k * 1.4f
                    val sc = 1f - abs(k) * 0.025f
                    scaleX = sc; scaleY = sc
                    alpha = e * (1f - 0.85f * w)
                }
        ) {
            Box(Modifier.fillMaxSize().offset(10.dp, 10.dp).clip(slantShape(0.12f)).background(hi.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxSize().clip(slantShape(0.12f)).background(Color.White).padding(5.dp).clip(slantShape(0.12f)).background(Color(col.bottom)).gameOrigin(g.stableKey, true)) {
                val cover = c.cover(g)
                // (Update 529: a category change shows the new game's cover in the same frame - rule 14 - never a crossfade from the
                // old category's.)
                androidx.compose.animation.Crossfade(targetState = cover, label = "nmCover",
                    animationSpec = tween(if (animate && !CategorySlide.justChanged()) 200 else 0)) { cv ->
                    if (cv != null) AsyncImage(rememberStyleArtRequest(cv, trim = true), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                    else StyleNamePlate(titles.display(g.name))
                }
            }
        }
        // Its tag under the frame: console, time played, achievements.
        Row(Modifier.padding(start = maxWidth * 0.04f, top = maxHeight * 0.81f).graphicsLayer {
            rotationZ = -3f
            val e = NightMotion.ease(NightMotion.layer(enter.value, 2f, 0.1f, 0.5f))
            translationX = -(1f - e) * 120.dp.toPx() - NightMotion.window.floatValue * 30.dp.toPx()
            alpha = e * (1f - 0.7f * NightMotion.window.floatValue)
        }) {
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
            // The category change's wave, over the list only, crossing the way the change went.
            if (catOn) Canvas(Modifier.fillMaxSize()) {
                val t = catProgress()
                if (t >= 1f) return@Canvas
                val e = NightMotion.ease(NightMotion.layer(t, 0f, 0f, 0.8f))
                clipRect(left = listLeft - rowH, top = 0f, right = size.width, bottom = size.height) {
                    nightWave(e, if (catDir > 0f) -1f else 1f, hi, 0.3f) // (the games come in from the right for the next category: the wave runs right to left)
                }
            }
            val half = rowsN / 2 + 1
            // (Pass 5, D57: derived, so a glide doesn't re-compose every tile each frame; the place is read in graphicsLayer.)
            val base by remember(scroll) { androidx.compose.runtime.derivedStateOf { scroll.value.roundToInt() } }
            // (Batch 523: each game once when the list is shorter than the bars on screen - loopSlots; no second highlight.)
            // (Batch 524: one game, one lit bar - with Fill the row on, only the copy at the endless place is lit, and a
            // short list keys its copies by place so two copies never share one bar's state.)
            val fill = com.shelf.launcher.data.Features.endlessFill.value
            for ((slot, i) in loopSlots(base, count, half, loop, fill)) {
                val off = slot - base
                val gg = c.games[i]
                key(if (loop && !fill && count < 2 * half + 1) "g$i" else slot) {
                    val on = loopLit(loop, fill, slot, target, i, focus)
                    val pull by animateFloatAsState(if (on) 1f else 0f, if (animate) spring(dampingRatio = 0.86f, stiffness = 650f) else tween(0), label = "nmPull")
                    Box(
                        Modifier.size(with(density) { (wPx - listLeft + rowH).toDp() }, with(density) { (rowH * 0.82f).toDp() })
                            .graphicsLayer {
                                val d = (base + off) - scroll.value
                                val lag = scroll.value - targetNow
                                // Row order on screen (top first) for the entrance / category stagger.
                                val row = (d + half).coerceIn(0f, 2f * half)
                                val inE = NightMotion.ease(NightMotion.layer(enter.value, 3f + row * 0.6f, 0.05f, if (lite) 0.6f else 0.42f))
                                val catE = NightMotion.ease(NightMotion.layer(catProgress(), row, 0.045f, 0.55f))
                                val w = NightMotion.window.floatValue
                                translationX = listLeft - pull * wPx * 0.04f + abs(d) * rowH * 0.12f +
                                    (if (lite) 0f else NightMotion.bend(lag, d, rowH)) +
                                    (1f - inE) * wPx * 0.22f + (1f - catE) * catDir * wPx * 0.2f + w * wPx * 0.06f
                                translationY = hPx / 2f + d * rowH - size.height / 2f
                                // The one you're on stands a little forward.
                                val sc = 1f + pull * 0.03f
                                scaleX = sc; scaleY = sc
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                alpha = (1f - ((abs(d) - rowsN / 2f) / 1.2f).coerceIn(0f, 1f)) * inE * (1f - (1f - catE) * 0.85f) * (1f - 0.45f * w)
                            }
                            .zIndex(if (on) 2f else 1f).qaLit(on)
                            .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else select(i) } }
                    ) { NightBar(c, gg, on, { pull }, col, rowH) }
                }
            }
        }
    }
}

/**
 * Update 529: a wave of light crossing the night - a wide slanted band, brightest along its leading edge, at [t] (0 … 1) of
 * its way across in direction [dir] (+1 left to right). Used by the entrance and (over the list) by a category change.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.nightWave(t: Float, dir: Float, hi: Color, strength: Float) {
    val band = size.width * 0.34f
    val lean = size.height * 0.32f
    val x = if (dir >= 0f) -band - lean + (size.width + 2f * band + lean) * t else size.width + band - (size.width + 2f * band + lean) * t
    val fade = (1f - t) * strength
    if (fade <= 0.001f) return
    val p = Path().apply { moveTo(x + lean, 0f); lineTo(x + lean + band, 0f); lineTo(x + band, size.height); lineTo(x, size.height); close() }
    drawPath(p, Brush.horizontalGradient(
        if (dir >= 0f) listOf(Color.Transparent, hi.copy(alpha = fade * 0.5f), Color.White.copy(alpha = fade)) else listOf(Color.White.copy(alpha = fade), hi.copy(alpha = fade * 0.5f), Color.Transparent),
        startX = x, endX = x + band + lean))
    val edge = if (dir >= 0f) x + band else x
    drawLine(Color.White.copy(alpha = fade * 0.9f), Offset(edge + lean, 0f), Offset(edge, size.height), 2.5f)
}

/**
 * One game's bar: dark glass with a coloured edge; the one you're on white, pulled out, with a pointer.
 * (Update 529: the white sweeps in across the bar from its left as it is picked - and back out as you move on - instead of
 * appearing at once; [pull] is read while drawing only.)
 */
@Composable
private fun NightBar(c: StyleCtx, g: Game, on: Boolean, pull: () -> Float, col: Features.NmColor, rowH: Float) {
    val titles = LocalTitleOptions.current
    val hi = Color(col.hi)
    val density = LocalDensity.current
    val shape = slantShape(0.45f)
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // The pointer.
        Canvas(Modifier.size(with(density) { (rowH * 0.42f).toDp() }).graphicsLayer { val p = pull(); alpha = p; translationX = -(1f - p) * 20f }) {
            val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }
            drawPath(p, hi)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.fillMaxHeight().weight(1f).clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(col.top).copy(alpha = 0.85f), Color(col.bottom).copy(alpha = 0.6f))))
                .drawBehind {
                    // The white sweeping across (a slanted edge leading).
                    val p = pull()
                    if (p > 0.001f) {
                        val s = size.height * 0.45f
                        val r = -s + (size.width + s) * p
                        val path = Path().apply { moveTo(0f, 0f); lineTo(r + s, 0f); lineTo(r, size.height); lineTo(0f, size.height); close() }
                        drawPath(path, Brush.horizontalGradient(listOf(Color.White, Color(0xFFE8F4FF))))
                    }
                }
                .then(if (!on) Modifier.border(1.5.dp, hi.copy(alpha = 0.55f), shape) else Modifier)
                .gameOrigin(g.stableKey, false)
        ) {
            Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight(0.78f).aspectRatio(1.3f).clip(slantShape(0.3f)).background(Color.Black)) {
                    c.cover(g)?.let { AsyncImage(rememberStyleArtRequest(it, trim = true), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(Modifier.width(14.dp))
                FullText(titles.display(g.name).uppercase(), color = if (on) Color(0xFF0B1424) else Color.White, fontSize = if (on) 22.sp else 18.sp,
                    fontWeight = FontWeight.Black, style = androidx.compose.ui.text.TextStyle(fontStyle = FontStyle.Italic), modifier = Modifier.weight(1f))
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
