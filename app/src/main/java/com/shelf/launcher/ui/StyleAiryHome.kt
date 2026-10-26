package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * Update 330, phase 3: Airy home (1999) — a late-90s console's light, airy system screen, in Voltcap's own drawing.
 *
 * A pale white-to-sky screen with soft bubbles drifting up (Layout options), your games as a gently bowed row of rounded
 * tiles floating over it: the one you're on in the middle, bigger, lifted, ringed in the accent colour (orange, blue or
 * green), bobbing gently. Its name above the row in light type, its console, time played and achievements as small white
 * pills under it; the category top left; top right a small pixel display (a memory unit's screen) showing the game you're
 * on in two shades. A opens its window — a floating system panel with big round buttons (WinScenes.kt).
 * D-pad: ◀ ▶ along the row (Endless: on round), ▲ ▼ beyond. Touch: tap a tile to pick it, again to open it; swipe along.
 */

/** Update 330: Airy home's ▲ ▼ (beyond); ◀ ▶ are the home screen's own. */
internal fun airyHomeKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionUp -> ctx.reach(true)
    Key.DirectionDown -> ctx.reach(false)
    else -> false
}

/** Update 330: the pale screen with its drifting bubbles. */
@Composable
internal fun AiryHomeBackdrop(modifier: Modifier) {
    val accent = Color(Features.ahAccent.state.value.argb)
    val drift = Features.ahBubbles.state.value && rememberAnimationsEnabled() && !LocalLiteEffects.current && rememberIsActive() && !HomeIdle.calm
    val clock = rememberGcClock(drift, 1f, 30)
    val bubbles = Features.ahBubbles.state.value
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFCFDFF), Color(0xFFEFF4FA), Color(0xFFDCE7F3))))
        // A soft horizon of light across the middle.
        drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 0.62f to Color.White.copy(alpha = 0.7f), 0.8f to Color.Transparent))
        if (!bubbles) return@Canvas
        val t = clock.floatValue
        for (i in 0 until 14) {
            val r = size.minDimension * (0.03f + 0.07f * odHashA(i, 1))
            val x = size.width * odHashA(i, 2) + sin(t * 0.3f + i) * r * 0.6f
            val span = size.height + r * 4f
            val y = size.height + r * 2f - ((odHashA(i, 3) * span + t * (10f + 18f * odHashA(i, 4))) % span)
            val c = if (i % 3 == 0) accent else Color(0xFF8DB7E8)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), c.copy(alpha = 0.16f), c.copy(alpha = 0.04f)), center = Offset(x - r * 0.3f, y - r * 0.3f), radius = r * 1.2f), r, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.5f), r, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
        }
    }
}

private fun odHashA(i: Int, salt: Int): Float {
    var h = i * 92821 + salt * 68917
    h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
    return ((h ushr 1) % 10000) / 10000f
}

@Composable
internal fun StyleAiryHome(c: StyleCtx, modifier: Modifier = Modifier) {
    val loop = loopRowOn(c)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        StyleLoop.on = loop
    }
    if (c.games.isEmpty()) StyleEmpty(modifier) else AiryRow(c, loop, modifier)
}

@Composable
private fun AiryRow(c: StyleCtx, loop: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val focus = c.focus.coerceIn(0, count - 1)
    val animate = rememberAnimationsEnabled()
    val sizeK = Features.ahSize.state.value.k
    val accent = Color(Features.ahAccent.state.value.argb)
    val curve = Features.ahCurve.state.value / 100f
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
        if (!animate || abs(cur - focus) > 30f) scroll.snapTo(focus.toFloat())
        else scroll.animateTo(focus.toFloat(), spring(dampingRatio = 0.9f, stiffness = 260f))
    }
    val bob = rememberGcClock(animate && rememberIsActive() && !HomeIdle.calm, 1f, 30)
    val caption = CategoryTitle.captionOn(c.state.appearance)
    if (caption) CategoryTitle.Native(null)
    BoxWithConstraints(modifier.fillMaxSize().pointerInput(count) {
        var acc = 0f
        detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
            ch.consume(); acc += dx
            val s = 90f
            while (acc <= -s) { acc += s; val n = latestFocus + 1; if (n < latestCount) select(n) else if (loop) select(0) }
            while (acc >= s) { acc -= s; val p = latestFocus - 1; if (p >= 0) select(p) else if (loop) select(latestCount - 1) }
        }
    }) {
        val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
        val tileH = hPx * 0.42f * sizeK
        val tileW = tileH * 0.74f
        val pitch = tileW * 0.82f
        val cy = hPx * 0.53f
        val visible = (wPx / (pitch * 0.7f) / 2f).toInt() + 2
        val base = scroll.value.roundToInt()
        for (off in -visible..visible) {
            var i = base + off
            if (loop) i = ((i % count) + count) % count else if (i !in 0 until count) continue
            val g = c.games[i]
            key(base + off) {
                val on = i == focus
                val lit by animateFloatAsState(if (on) 1f else 0f, if (animate) spring(dampingRatio = 0.6f) else tween(0), label = "ahLit")
                Box(
                    Modifier.size(with(density) { tileW.toDp() }, with(density) { tileH.toDp() })
                        .zIndex(if (on) 10f else 5f - abs(off) * 0.1f)
                        .graphicsLayer {
                            val d = (base + off) - scroll.value
                            val s = 0.66f + 0.34f * (1f - (abs(d) / 1.2f).coerceIn(0f, 1f)) + 0.06f * lit
                            // The row bows away at its ends; the one you're on lifts and bobs.
                            val x = wPx / 2f + d * pitch * (if (abs(d) < 1f) 1f else 1f + (abs(d) - 1f) * 0.02f)
                            val y = cy + d * d * hPx * 0.012f * curve * 3f - lit * hPx * 0.03f + (if (on) sin(bob.floatValue * 2.2f) * hPx * 0.006f else 0f)
                            translationX = x - size.width / 2f
                            translationY = y - size.height / 2f
                            scaleX = s; scaleY = s
                            alpha = (1f - ((abs(d) - visible + 2.5f) / 1.5f).coerceIn(0f, 1f))
                        }
                        .pointerInput(i) { detectTapGestures { if (latestFocus == i) open() else select(i) } }
                ) { AiryTile(c, g, on, lit, accent) }
            }
        }
        // The name above the row, the pills under it.
        val g = c.games[focus]
        Text(
            titles.display(g.name), color = Color(0xFF2D3440), fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = with(density) { (cy - tileH / 2f - hPx * 0.15f).coerceAtLeast(8f).toDp() }).fillMaxWidth(0.7f)
        )
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = with(density) { (cy + tileH / 2f + hPx * 0.04f).toDp() }),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOfNotNull(titles.console(g.platform).takeIf { it.isNotBlank() }, stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
                c.progress(g)?.let { (e, t) -> "★ $e / $t" }).forEach {
                Box(
                    Modifier.drawBehind { drawRoundRect(Color.Black.copy(alpha = 0.06f), Offset(0f, 3f), size, CornerRadius(size.height / 2f)) }
                        .clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 5.dp)
                ) { Text(it, color = Color(0xFF55606F), fontSize = 13.sp, maxLines = 1) }
            }
        }
        if (caption) Row(Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).border(3.dp, accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(c.categoryLabel, color = Color(0xFF3B4452), fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1)
        }
        if (Features.ahLcd.state.value) Box(Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 52.dp)) {
            AiryLcd(c.cover(g), Modifier.size(92.dp, 70.dp))
        }
    }
}

/** One tile: the cover, rounded, on a white mount with a soft shadow; ringed in the accent colour when it's the one you're on. */
@Composable
private fun AiryTile(c: StyleCtx, g: Game, on: Boolean, lit: Float, accent: Color) {
    val cover = c.cover(g)
    Box(
        Modifier.fillMaxSize()
            .drawBehind {
                drawRoundRect(Color(0xFF1C3150).copy(alpha = 0.10f + 0.10f * lit), Offset(0f, size.height * (0.04f + 0.05f * lit)), size, CornerRadius(22.dp.toPx()))
            }
            .clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(if (on) 3.dp else 1.dp, if (on) accent else Color(0xFFDDE5EF), RoundedCornerShape(20.dp))
            .padding(5.dp).clip(RoundedCornerShape(15.dp)).gameOrigin(g.stableKey, on)
    ) {
        if (cover != null) AsyncImage(rememberStyleArtRequest(cover, trim = true), g.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
        else StyleNamePlate(LocalTitleOptions.current.display(g.name))
        // A soft gloss.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Transparent))))
    }
}

/**
 * A small two-shade pixel display, as on a memory unit: [model] brought down to 48 × 32 pixels and dithered to dark /
 * light on a grey-green screen, in a pale rounded case.
 */
@Composable
internal fun AiryLcd(model: Any?, modifier: Modifier) {
    val src = rememberSoftBitmap(model, 96)
    val lcd = remember(src) { src?.let { runCatching { lcdOf(it) }.getOrNull() } }
    Box(
        modifier.drawBehind { drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(0f, 4f), size, CornerRadius(14.dp.toPx())) }
            .clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF4F6F9), Color(0xFFD5DBE3))))
            .padding(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF9DAF96)).padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (lcd != null) Image(lcd, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
    }
}

private val Bayer4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

private fun lcdOf(src: android.graphics.Bitmap): androidx.compose.ui.graphics.ImageBitmap {
    val w = 48; val h = 32
    // The picture's middle at the screen's shape.
    val sa = src.width.toFloat() / src.height
    val (cw, ch) = if (sa > w / h.toFloat()) (src.height * w / h) to src.height else src.width to (src.width * h / w)
    val crop = android.graphics.Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
    val small = android.graphics.Bitmap.createScaledBitmap(crop, w, h, true)
    val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
    var lo = 255; var hi = 0
    val lum = IntArray(w * h) { val c = px[it]; ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10 }
    for (v in lum) { lo = minOf(lo, v); hi = maxOf(hi, v) }
    val dark = 0xFF26331F.toInt(); val light = 0xFF9DAF96.toInt()
    val out = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val v = (lum[y * w + x] - lo) * 16 / (hi - lo).coerceAtLeast(1)
        out[y * w + x] = if (v <= Bayer4[(y % 4) * 4 + x % 4]) dark else light
    }
    val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    b.setPixels(out, 0, w, 0, 0, w, h)
    return b.asImageBitmap()
}

/** Update 330: Settings › Home › Layout options (Airy home). Every one its own "ah_…" Features pref. */
@Composable
internal fun AiryHomeSettings() {
    MutedNote(
        "A light, airy row of rounded tiles under drifting bubbles: the one you're on in the middle, lifted and ringed in " +
            "the accent colour, its name above the row, a small pixel display top right. ◀ ▶ along the row, A opens the game, " +
            "L2 / R2 the next category. Tap a tile to pick it, again to open it; swipe along."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Tile size", Features.AhSize.values(), Features.ahSize.state.value, { it.label }) { Features.ahSize.value = it }
    StyleChoice("Accent colour", Features.AhAccent.values(), Features.ahAccent.state.value, { it.label }) { Features.ahAccent.value = it }
    PercentSlider("How much the row bows", Features.ahCurve.state.value, { Features.ahCurve.value = it.coerceIn(0, 60) }, min = 0, max = 60,
        valueText = { if (it == 0) "Straight" else "$it%" })
    Toggle(Icons.Filled.BubbleChart, "Drifting bubbles", "Soft bubbles drifting up behind the row. Off: just the pale screen.", Features.ahBubbles)
    Toggle(Icons.Filled.Memory, "Pixel display", "The small two-shade display top right showing the game you're on.", Features.ahLcd)
}
