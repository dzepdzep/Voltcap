package com.shelf.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 330, phase 3: Orbit deck (1995) — a mid-90s disc console's system menu, in Voltcap's own drawing.
 *
 * Deep space (twinkling stars, a ringed planet's edge low down), a glowing core in the middle, and your games as discs on
 * an orbit round it, tipped toward you (Layout options › Tilt): the disc you're on at the front, big and lit, slowly
 * spinning; the others round the orbit behind, smaller and dimmer the further back they are (passing behind the core).
 * The game's name below in chunky silver italics, its console, time played and achievements under it; the category on a
 * bevelled capsule top left. A opens its window — the system's CD-player terminal (WinScenes.kt).
 * D-pad: ◀ ▶ round the orbit (Endless: on round past the last), ▲ ▼ beyond. Touch: tap a disc to pick it, again to open
 * it; swipe to turn the orbit.
 */

/** Update 330: Orbit deck's ▲ ▼ (beyond); ◀ ▶ are the home screen's own. */
internal fun orbitDeckKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionUp -> ctx.reach(true)
    Key.DirectionDown -> ctx.reach(false)
    else -> false
}

private fun odHash(i: Int, salt: Int): Float {
    var h = i * 7919 + salt * 104729
    h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
    return ((h ushr 1) % 10000) / 10000f
}

/** Update 330: deep space behind the orbit: stars (twinkling), a ringed planet's edge low down. */
@Composable
internal fun OrbitDeckBackdrop(modifier: Modifier) {
    val col = Features.odColor.state.value
    val twinkle = Features.odStars.state.value && rememberAnimationsEnabled() && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm
    val clock = rememberGcClock(twinkle, 1f, 20)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(col.top), Color(col.bottom))))
        drawRect(Brush.radialGradient(listOf(Color(col.glow).copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.48f), radius = size.minDimension * 0.7f))
        val t = clock.floatValue
        for (i in 0 until 160) {
            val x = odHash(i, 1) * size.width; val y = odHash(i, 2) * size.height
            val r = 0.6f + odHash(i, 3) * 1.8f
            val a = (0.25f + 0.6f * odHash(i, 4)) * (if (twinkle) 0.6f + 0.4f * sin(t * (1f + odHash(i, 5) * 2f) + i) else 1f)
            drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
        }
        // A planet's edge low down, with its ring.
        val pc = Offset(size.width * 0.82f, size.height * 1.55f); val pr = size.height * 0.75f
        drawCircle(Brush.radialGradient(listOf(Color(col.glow).copy(alpha = 0.35f), Color(col.top).copy(alpha = 0.9f), Color(col.bottom)), center = Offset(pc.x - pr * 0.3f, pc.y - pr * 0.4f), radius = pr * 1.3f), pr, pc)
        drawOval(Color(col.glow).copy(alpha = 0.22f), Offset(pc.x - pr * 1.6f, pc.y - pr * 0.22f), Size(pr * 3.2f, pr * 0.44f), style = androidx.compose.ui.graphics.drawscope.Stroke(size.height * 0.008f))
    }
}

@Composable
internal fun StyleOrbitDeck(c: StyleCtx, modifier: Modifier = Modifier) {
    val loop = loopRowOn(c)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleLoop.on = loop
    }
    if (c.games.isEmpty()) StyleEmpty(modifier) else OrbitRing(c, loop, modifier)
}

@Composable
private fun OrbitRing(c: StyleCtx, loop: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val focus = c.focus.coerceIn(0, count - 1)
    val animate = rememberAnimationsEnabled()
    // (With Endless and fewer games than places round the orbit, the orbit holds just those — no game twice.)
    val ringN = Features.odRing.state.value.n.let { n -> if (loop && count <= n + 1) count.coerceAtLeast(2) else n }
    val tiltDeg = Features.odTilt.state.value.toFloat()
    val spin = Features.odSpin.state.value && animate && rememberIsActive() && !HomeIdle.calm
    val col = Features.odColor.state.value
    val titles = LocalTitleOptions.current
    val density = LocalDensity.current
    // Where the orbit is turned to (in games). It is never wrapped back: with Endless it glides on to the copy of the
    // game nearest where it is, so going round past the last game is one smooth step and no disc is rebuilt.
    val scroll = remember { Animatable(focus.toFloat()) }
    LaunchedEffect(focus, count) {
        val cur = scroll.value
        var target = focus.toFloat()
        if (loop && count > 1) {
            val k = kotlin.math.round((cur - focus) / count)
            target = focus + k * count
        }
        if (!animate || abs(cur - target) > ringN * 1.5f) scroll.snapTo(target)
        else scroll.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 150f))
    }
    val clock = rememberGcClock(spin, 1f, 30)
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    BoxWithConstraints(modifier.fillMaxSize().pointerInput(count) {
        var acc = 0f
        detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
            ch.consume(); acc += dx
            val s = 70f
            while (acc <= -s) { acc += s; val n = latestFocus + 1; if (n < latestCount) select(n) else if (loop) select(0) }
            while (acc >= s) { acc -= s; val p = latestFocus - 1; if (p >= 0) select(p) else if (loop) select(latestCount - 1) }
        }
    }) {
        val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
        // The name block's room at the bottom (its own type sizes, so a bigger font keeps clear of the discs too).
        val nameTop = hPx - with(density) { 18.dp.toPx() + 30.sp.toPx() * 1.3f + 14.sp.toPx() * 1.45f + 10.dp.toPx() }
        val topLimit = hPx * 0.1f
        val disc = minOf(hPx * 0.33f, (nameTop - topLimit) * 0.52f, wPx * 0.2f)
        val frontScale = 1.12f; val backScale = 0.42f
        val frontBottom = nameTop - with(density) { 6.dp.toPx() }
        val cx = wPx / 2f
        val rx = wPx * 0.36f
        // The orbit's height from its tilt — kept so the front disc clears the name and the back one the top.
        val ryWant = rx * sin(Math.toRadians(tiltDeg.toDouble())).toFloat().coerceAtLeast(0.06f) * 1.6f
        val ryMax = ((frontBottom - disc * frontScale / 2f) - (topLimit + disc * backScale / 2f)) / 2f
        val ry = ryWant.coerceAtMost(ryMax).coerceAtLeast(hPx * 0.03f)
        val cy = (frontBottom - disc * frontScale / 2f - ry).coerceAtMost(hPx * 0.52f)
        val step = (2.0 * PI / ringN).toFloat()
        val glow = Color(col.glow)
        // The back half of the orbit (behind the core and the far discs), a wider guide ring and its turning marks.
        Canvas(Modifier.fillMaxSize().zIndex(0.05f)) {
            drawOrbitPath(cx, cy, rx, ry, glow, front = false, turn = clock.floatValue)
        }
        // The core: a small bright star with a soft, tight halo (not a floodlight).
        Canvas(Modifier.fillMaxSize().zIndex(0.5f)) {
            val r = hPx * 0.05f
            val pulse = if (spin) 1f + 0.05f * sin(clock.floatValue * 1.6f) else 1f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.32f), glow.copy(alpha = 0.08f), Color.Transparent), center = Offset(cx, cy), radius = r * 3f * pulse), r * 3f * pulse, Offset(cx, cy))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.85f), glow.copy(alpha = 0.6f), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
            // Two thin flare lines through it.
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent), startX = cx - r * 3.4f, endX = cx + r * 3.4f),
                Offset(cx - r * 3.4f, cy), Offset(cx + r * 3.4f, cy), 1.5f)
            drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), startY = cy - r * 2f, endY = cy + r * 2f),
                Offset(cx, cy - r * 2f), Offset(cx, cy + r * 2f), 1.2f)
        }
        // The front half of the orbit: over the far discs and the core, under the near ones.
        Canvas(Modifier.fillMaxSize().zIndex(0.9f)) {
            drawOrbitPath(cx, cy, rx, ry, glow, front = true, turn = clock.floatValue)
        }
        val half = ringN / 2
        // (Pass 5, D57: the ring's place as a derived whole number — read raw, every disc was re-composed on every frame
        // of a turn; it changes once per place now.)
        val base by remember { androidx.compose.runtime.derivedStateOf { scroll.value.roundToInt() } }
        val lo = if (loop && count <= ringN + 1) -((count - 1) / 2) else -half - 1
        val hi = if (loop && count <= ringN + 1) count / 2 else half + 1
        for (off in lo..hi) {
            val slot = base + off
            val i = if (loop) ((slot % count) + count) % count else slot
            if (i !in 0 until count) continue
            val g = c.games[i]
            key(g.stableKey) {
                val on = i == focus
                // The one you're on comes forward a little more and grows, easing in and out.
                val lift by animateFloatAsState(if (on) 1f else 0f, tween(if (animate) 420 else 0, easing = FastOutSlowInEasing), label = "odLift")
                // (Drawing order by depth, in small steps, so it changes a few times per turn instead of every frame.)
                val zNow by remember(slot, step) {
                    androidx.compose.runtime.derivedStateOf {
                        val th = (slot - scroll.value) * step
                        val depth = (cos(th) + 1f) / 2f
                        val z = if (depth < 0.5f) 0.1f + depth * 0.3f else 1f + depth + lift
                        (z * 40f).roundToInt() / 40f
                    }
                }
                Box(
                    Modifier.size(with(density) { disc.toDp() })
                        .graphicsLayer {
                            val d = slot - scroll.value
                            val th = d * step
                            val depth = (cos(th) + 1f) / 2f
                            val x = cx + rx * sin(th)
                            val y = cy + ry * cos(th) + disc * 0.03f * lift
                            val sc = (backScale + (1f - backScale) * depth.pow(1.3f)) * (1f + (frontScale - 1f) * lift)
                            translationX = x - size.width / 2f
                            translationY = y - size.height / 2f
                            scaleX = sc; scaleY = sc
                            // Turned a little along the orbit at its sides, as a disc travelling round would be.
                            rotationY = -sin(th) * 34f * (1f - lift)
                            cameraDistance = 14f * density.density
                            // (Past the last place at the back it fades — it never pops in or out.)
                            val edge = (half + 0.5f - abs(d)).coerceIn(0f, 1f)
                            alpha = (if (loop && count <= ringN + 1) 1f else edge) *
                                // (Its window's disc takes over as it flies out — and hands back as it lands.)
                                (if (on && MediaInspect.key == g.stableKey) 1f - MediaInspect.amount.floatValue else 1f)
                        }
                        .zIndex(zNow)
                        .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else select(i) } }
                ) {
                    OrbitDisc(
                        c, g, on, spinDeg = { if (on) clock.floatValue * 24f else 0f }, col = col,
                        light = {
                            // The core's light falls on the side facing it; the far discs are in its shadow.
                            val th = (slot - scroll.value) * step
                            (Math.toDegrees(kotlin.math.atan2(-(ry * cos(th)).toDouble(), -(rx * sin(th)).toDouble())).toFloat())
                        },
                        dim = { val th = (slot - scroll.value) * step; ((1f - (cos(th) + 1f) / 2f) * 0.75f) * (1f - lift) }
                    )
                }
            }
        }
        // The name below, in chunky silver italics; the facts under it.
        val g = c.games[focus]
        Crossfade(g.stableKey, Modifier.align(Alignment.BottomCenter).zIndex(4f).padding(bottom = 18.dp).fillMaxWidth(0.8f),
            animationSpec = tween(if (animate) 200 else 0), label = "odName") { k ->
            val gg = c.games.firstOrNull { it.stableKey == k } ?: g
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    titles.display(gg.name).uppercase(), fontSize = 30.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = TextStyle(brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB9C4D6), Color(0xFF6F7C92), Color(0xFFE6ECF5))),
                        shadow = Shadow(glow.copy(alpha = 0.55f), Offset(0f, 0f), 12f))
                )
                val bits = listOfNotNull(titles.console(gg.platform).takeIf { it.isNotBlank() }, stylePlaytime(c.playtime(gg), c.lastPlayed(gg) != null),
                    c.progress(gg)?.let { (e, t) -> "★ $e / $t" })
                Text(bits.joinToString("   ·   "), color = Color(0xFFCFD8E6), fontSize = 14.sp, maxLines = 1, letterSpacing = 1.5.sp)
            }
        }
        if (caption) Box(
            Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 10.dp).clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(0xFFE9EEF6), Color(0xFF8C98AC), Color(0xFF4A5468))))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(2.dp).clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(col.top), Color(col.bottom)))).padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            Text(c.categoryLabel.uppercase(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, letterSpacing = 2.sp, maxLines = 1)
        }
    }
}

/** Half of the orbit (the near half, or the far one): a fine bright line in a soft band, an outer guide ring broken into
 *  segments that slowly turn, and small marks along the orbit. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrbitPath(cx: Float, cy: Float, rx: Float, ry: Float, glow: Color, front: Boolean, turn: Float) {
    val start = if (front) 0f else 180f
    val a = if (front) 1f else 0.55f
    fun stroke(w: Float) = androidx.compose.ui.graphics.drawscope.Stroke(w)
    drawArc(glow.copy(alpha = 0.07f * a), start, 180f, false, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = stroke(size.height * 0.014f))
    drawArc(Brush.horizontalGradient(listOf(glow.copy(alpha = 0.1f * a), Color.White.copy(alpha = 0.5f * a), glow.copy(alpha = 0.1f * a)), startX = cx - rx, endX = cx + rx),
        start, 180f, false, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = stroke(1.6f))
    // The outer guide ring: dashes turning slowly.
    val ox = rx * 1.12f; val oy = ry * 1.12f
    val segs = 36; val sweep = 360f / segs
    val shift = (turn * 3f) % sweep
    for (k in 0 until segs) {
        val s0 = k * sweep + shift
        val mid = ((s0 + sweep * 0.3f) % 360f)
        val inFront = mid < 180f
        if (inFront != front) continue
        drawArc(glow.copy(alpha = (if (k % 6 == 0) 0.4f else 0.16f) * a), s0, sweep * 0.55f, false, Offset(cx - ox, cy - oy), Size(ox * 2, oy * 2), style = stroke(1.2f))
    }
    // An inner ring close round the core.
    val ix = rx * 0.32f; val iy = ry * 0.32f
    drawArc(glow.copy(alpha = 0.22f * a), start, 180f, false, Offset(cx - ix, cy - iy), Size(ix * 2, iy * 2), style = stroke(1f))
}

/**
 * One game as a disc: its cover printed on it, the hole, the rainbow sheen, the rim; lit from [light] (degrees, the
 * direction the light comes from) with a gloss on top and its edge's thickness showing on the shaded side, darkened by
 * [dim] (the far side of the orbit). The one you're on glows and slowly spins. All read while drawing.
 */
@Composable
internal fun OrbitDisc(
    c: StyleCtx?, g: Game, on: Boolean, spinDeg: () -> Float, col: Features.OdColor, coverOverride: String? = null,
    light: () -> Float = { -125f }, dim: () -> Float = { 0f }, physical: List<Pair<String, String>>? = c?.state?.physicalMedia?.get(g.stableKey)
) {
    val real = orbitMedia(g, physical)
    if (real != null && real.second == OdMedia.CART) { OrbitCart(c, g, on, real.first, col, dim); return }
    val cover = if (real != null) null else coverOverride ?: c?.cover(g)
    val glow = Color(col.glow)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.43f
            if (on) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), Color.Transparent), radius = size.minDimension * 0.6f), size.minDimension * 0.6f)
            // The disc's edge, showing on its shaded side.
            val la = Math.toRadians(light().toDouble())
            val ex = -cos(la).toFloat() * r * 0.05f; val ey = -sin(la).toFloat() * r * 0.05f
            drawCircle(Color(0xFF1B2030).copy(alpha = 0.55f), r * 1.02f, center + Offset(ex * 2.2f, ey * 2.2f))
            drawCircle(Color(0xFF59606E), r, center + Offset(ex, ey))
        }
        Box(
            Modifier.fillMaxSize(0.86f).graphicsLayer { rotationZ = spinDeg() }.clip(CircleShape)
                .background(Color(0xFFD7DCE3)).then(if (c != null) Modifier.gameOrigin(g.stableKey, on) else Modifier)
        ) {
            if (real != null) {
                // The game's own disc: its photo whole (it has its own hub and hole), or its label scan cut to the disc.
                AsyncImage(rememberStyleArtRequest(real.first, trim = real.second == OdMedia.DISC), g.name, Modifier.fillMaxSize(),
                    contentScale = if (real.second == OdMedia.DISC) ContentScale.Fit else ContentScale.Crop)
            } else if (cover != null) AsyncImage(rememberStyleArtRequest(cover, trim = true), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
            if (real == null || real.second == OdMedia.LABEL) Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                // The rainbow sheen of a disc's data side showing through at the edge, the clear hub and the hole.
                drawCircle(Brush.sweepGradient(listOf(Color(0x55FF6B6B), Color(0x55FFE36B), Color(0x556BFF9A), Color(0x556BD6FF), Color(0x55B06BFF), Color(0x55FF6B6B))), r, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.24f)
                drawCircle(Color(0xFFB8BEC8), r * 0.2f, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.03f))
                drawCircle(Color(0xFF0A0C12), r * 0.08f)
            }
        }
        // Light and gloss stay put while the disc spins under them.
        Canvas(Modifier.fillMaxSize(0.86f)) {
            val r = size.minDimension / 2f
            val la = Math.toRadians(light().toDouble())
            val dir = Offset(cos(la).toFloat(), sin(la).toFloat())
            // Lit from the core's side, shaded away from it.
            drawCircle(Brush.linearGradient(listOf(glow.copy(alpha = 0.3f), Color.Transparent, Color(0xFF000814).copy(alpha = 0.42f)),
                start = center + dir * r, end = center - dir * r), r)
            // The gloss: a soft highlight up on the lit side and a thin bright crescent along that edge.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.0f)), center = center + Offset(-r * 0.38f, -r * 0.42f), radius = r * 0.75f), r)
            drawArc(Color.White.copy(alpha = 0.55f), (Math.toDegrees(la).toFloat()) - 50f, 100f, false, Offset(r * 0.06f, r * 0.06f), Size(r * 1.88f, r * 1.88f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.035f))
            val dm = dim()
            if (dm > 0.01f) drawCircle(Color(0xFF02040C).copy(alpha = dm.coerceIn(0f, 0.85f)), r)
            drawCircle(Color.White.copy(alpha = if (on) 0.85f else 0.3f), r - 1.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(if (on) 3f else 2f))
        }
    }
}

internal enum class OdMedia { DISC, LABEL, CART }

/**
 * Update 330 (Toni: "always prefer the real media if possible"): what Orbit deck puts on the orbit for [g] — its own disc
 * (ScreenScraper's photo of it, else its label scan on a drawn disc), a cartridge game its cartridge; null: the cover on a
 * drawn disc (no scan, games without media of their own, or Layout options › The games' own discs off).
 */
internal fun orbitMedia(g: Game, physical: List<Pair<String, String>>?): Pair<String, OdMedia>? =
    if (!Features.odReal.state.value) null else ownMediaArt(g, physical)

/** Update 330: [g]'s own disc / cartridge picture from its scans (photo first, a disc's label scan second), or null. */
internal fun ownMediaArt(g: Game, physical: List<Pair<String, String>>?): Pair<String, OdMedia>? {
    if (physical == null || noOwnMedia(g.platform)) return null
    val photo = physical.firstOrNull { it.first == "Disc / cartridge" }?.second
    return if (mediaKind(g.platform) == "disc") {
        photo?.let { it to OdMedia.DISC } ?: physical.firstOrNull { it.first == "Disc label" }?.second?.let { it to OdMedia.LABEL }
    } else photo?.let { it to OdMedia.CART }
}

/** A cartridge game on the orbit: its own cartridge, whole, lit by the core, dimmed with the far side. */
@Composable
private fun OrbitCart(c: StyleCtx?, g: Game, on: Boolean, url: String, col: Features.OdColor, dim: () -> Float) {
    val glow = Color(col.glow)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (on) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.42f), Color.Transparent), radius = size.minDimension * 0.6f), size.minDimension * 0.6f)
        }
        AsyncImage(
            rememberStyleArtRequest(url, trim = true), g.name,
            Modifier.fillMaxSize(0.84f).then(if (c != null) Modifier.gameOrigin(g.stableKey, on) else Modifier)
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Lit from above, darkened on the far side of the orbit (only where the cartridge is).
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.25f))),
                        blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    val dm = dim()
                    if (dm > 0.01f) drawRect(Color(0xFF02040C).copy(alpha = dm.coerceIn(0f, 0.85f)), blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                },
            contentScale = ContentScale.Fit
        )
    }
}

/** Update 330: Settings › Home › Layout options (Orbit deck). Every one its own "od_…" Features pref. */
@Composable
internal fun OrbitDeckSettings() {
    MutedNote(
        "Your games as discs on an orbit round a glowing core in deep space: the one you're on at the front, big and slowly " +
            "spinning, the others round the orbit behind it. ◀ ▶ turn the orbit, A opens the game, L2 / R2 the next category. " +
            "Tap a disc to pick it, again to open it; swipe to turn the orbit."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Discs round the orbit", Features.OdRing.values(), Features.odRing.state.value, { it.label }) { Features.odRing.value = it }
    PercentSlider("Tilt", Features.odTilt.state.value, { Features.odTilt.value = it.coerceIn(4, 30) }, min = 4, max = 30, valueText = { "$it°" })
    StyleChoice("Colour", Features.OdColor.values(), Features.odColor.state.value, { it.label }) { Features.odColor.value = it }
    Toggle(Icons.Filled.Sync, "The disc you're on spins", "Slowly, as a disc in a player. Off: it stands still.", Features.odSpin)
    Toggle(Icons.Filled.AutoAwesome, "Twinkling stars", "The stars behind twinkle. Off: still stars.", Features.odStars)
    Toggle(Icons.Filled.Album, "The games' own discs", "Each game's real disc where it has a scan (its cartridge for a cartridge game). Off: every disc printed with its cover.", Features.odReal)
}
