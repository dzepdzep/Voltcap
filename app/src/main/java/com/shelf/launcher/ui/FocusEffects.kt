package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A soft blurred paint (real blur on Android 9+, where hardware drawing supports it). */
private fun blurPaint(color: Color, radiusPx: Float, strokePx: Float? = null): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        if (strokePx != null) {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        if (radiusPx > 0f && android.os.Build.VERSION.SDK_INT >= 28) {
            maskFilter = android.graphics.BlurMaskFilter(radiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

/**
 * Soft glow focus effect: a real blurred halo of [color] round the card ([corner] radius), breathing with
 * [strength] (0..1.4). Drawn behind the card, before any clip, so it spreads past the edges. Older Android
 * versions get stacked soft strokes instead of the blur.
 */
internal fun Modifier.softGlow(color: Color, corner: Dp, strength: Float, outset: Dp = 0.dp): Modifier = this.drawWithCache {
    val out = outset.toPx()
    val r = corner.toPx() + out
    val spread = 14.dp.toPx()
    val blurOk = android.os.Build.VERSION.SDK_INT >= 28
    val paint = blurPaint(color, spread)
    onDrawBehind {
        val a = (0.62f * strength).coerceIn(0f, 1f)
        if (a <= 0f) return@onDrawBehind
        if (blurOk) {
            paint.alpha = (a * 255).toInt()
            drawIntoCanvas { c ->
                c.nativeCanvas.drawRoundRect(-out - 2f, -out - 2f, size.width + out + 2f, size.height + out + 2f, r, r, paint)
            }
        } else {
            for (i in 1..6) {
                val g = i * spread / 6f
                drawRoundRect(
                    color.copy(alpha = a * (0.32f - i * 0.045f).coerceAtLeast(0.02f)),
                    topLeft = Offset(-out - g, -out - g), size = androidx.compose.ui.geometry.Size(size.width + 2 * (out + g), size.height + 2 * (out + g)),
                    cornerRadius = CornerRadius(r + g), style = Stroke(spread / 6f)
                )
            }
        }
    }
}

private const val ELECTRIC_BOLTS = false

/**
 * Electric focus effect, shared by every home style: two arcs of lightning run round the card's real
 * outline in opposite directions, crackling as they go and throwing small forks outwards, with now and then
 * a spark jumping elsewhere — over a blurred halo. ~30 redraws a second (only the drawing, never layout).
 *
 * Drawn after the content and **before** any clip in the chain, so the bolts sit on top of the art
 * and are never cut off by the card's own shape. [outset] pushes the outline outwards (Tile home
 * draws it around its ring); [corner] is the outline's corner radius at that outset. The caller
 * gives the focused card a higher zIndex so neighbours don't paint over the bolts.
 */
@Composable
internal fun Modifier.electricFrame(color: Color, corner: Dp, outset: Dp = 0.dp, pulse: Float = 1f): Modifier {
    // The lightning arcs around the art are switched off everywhere (the soft glow stays).
    if (!ELECTRIC_BOLTS) return this
    var time by remember { mutableStateOf(0L) }
    // Only while resumed with the screen on, and not when the system animator scale is 0. While the
    // handheld sits idle the bolts hold still (no redraws at all).
    val on = rememberIsActive() && rememberAnimationsEnabled() && !HomeIdle.calm
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        val start = android.os.SystemClock.uptimeMillis()
        while (true) {
            kotlinx.coroutines.delay(33)
            time = android.os.SystemClock.uptimeMillis() - start
        }
    }
    val bright = androidx.compose.ui.graphics.lerp(color, Color.White, 0.45f)
    return this.drawWithCache {
        val out = outset.toPx()
        val r = corner.toPx().coerceAtMost(minOf(size.width, size.height) / 2f + out)
        val outline = Path().apply {
            addRoundRect(RoundRect(-out, -out, size.width + out, size.height + out, CornerRadius(r)))
        }
        val measure = PathMeasure().apply { setPath(outline, false) }
        val length = measure.length
        val jitter = 4.5.dp.toPx()
        val fork = 9.dp.toPx()
        val haloPaint = blurPaint(color, 7.dp.toPx(), 3.dp.toPx())
        val boltGlow = blurPaint(color, 5.dp.toPx(), 5.dp.toPx())
        val coreW = 1.2.dp.toPx()
        val midW = 2.4.dp.toPx()
        val blurOk = android.os.Build.VERSION.SDK_INT >= 28
        val round = Stroke(width = midW, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val roundCore = Stroke(width = coreW, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)

        fun bolt(center: Float, span: Float, seed: Long, withFork: Boolean): Path {
            val random = kotlin.random.Random(seed)
            val path = Path()
            val steps = 10
            var forkAt = -1
            if (withFork) forkAt = 3 + random.nextInt(5)
            var forkFrom: Offset? = null
            var forkNormal = Offset.Zero
            for (i in 0..steps) {
                val d = (center - span / 2 + span * i / steps).mod(length)
                val p = measure.getPosition(d)
                val tg = measure.getTangent(d)
                val n = Offset(-tg.y, tg.x)
                // Tapered: calm at the ends, wild in the middle.
                val taper = 1f - kotlin.math.abs(i - steps / 2f) / (steps / 2f)
                val off = (random.nextFloat() * 2 - 1) * jitter * (0.35f + 0.65f * taper)
                val x = p.x + n.x * off
                val y = p.y + n.y * off
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                if (i == forkAt) { forkFrom = Offset(x, y); forkNormal = n }
            }
            val f = forkFrom
            if (f != null) {
                // A small fork thrown outwards (the outline runs clockwise: -normal points out).
                path.moveTo(f.x, f.y)
                var x = f.x; var y = f.y
                repeat(3) { k ->
                    val len = fork * (1f - k * 0.25f)
                    x += -forkNormal.x * len + (random.nextFloat() * 2 - 1) * len * 0.6f
                    y += -forkNormal.y * len + (random.nextFloat() * 2 - 1) * len * 0.6f
                    path.lineTo(x, y)
                }
            }
            return path
        }

        onDrawWithContent {
            drawContent()
            if (length <= 0f) return@onDrawWithContent
            val t = time
            // The halo all the way round, breathing with the pulse.
            if (blurOk) {
                haloPaint.alpha = ((0.30f + 0.30f * pulse) * 255).toInt()
                drawIntoCanvas { it.nativeCanvas.drawPath(outline.asAndroidPath(), haloPaint) }
            } else {
                drawPath(outline, color.copy(alpha = 0.18f + 0.22f * pulse), style = Stroke(3.dp.toPx()))
            }
            drawPath(outline, color.copy(alpha = 0.35f + 0.25f * pulse), style = Stroke(1.dp.toPx()))
            val bolts = ArrayList<Path>(4)
            // Two runners, opposite ways, re-crackling every 50 ms.
            val crackle = t / 50
            bolts += bolt((t * 0.00024f * length), length * 0.13f, crackle * 31 + 1, withFork = crackle % 3 == 0L)
            bolts += bolt((-t * 0.00017f * length + length * 0.5f), length * 0.10f, crackle * 57 + 7, withFork = crackle % 4 == 1L)
            // Now and then a stray spark somewhere else.
            val sparkSlot = t / 130
            val sr = kotlin.random.Random(sparkSlot * 13 + 5)
            if (sr.nextFloat() < 0.45f) bolts += bolt(sr.nextFloat() * length, length * (0.04f + sr.nextFloat() * 0.04f), sparkSlot * 91, withFork = false)
            for (b in bolts) {
                if (blurOk) drawIntoCanvas { it.nativeCanvas.drawPath(b.asAndroidPath(), boltGlow) }
                else drawPath(b, color.copy(alpha = 0.45f), style = Stroke(width = 6.dp.toPx()))
                drawPath(b, bright, style = round)
                drawPath(b, Color.White, style = roundCore)
            }
        }
    }
}
