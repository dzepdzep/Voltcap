package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * "Ignition" — what plays when a game starts (Appearance › Home screen).
 *
 * The launcher dims away and the game's cover rises into the middle over a
 * faint, blurred copy of its background art. Two sparks run around the
 * cover's edge — the same light as Voltcap's intro — and meet at the top; the
 * frame flares, a soft light sweeps across the art, a ring pulses out, and the
 * cover pushes towards you as everything fades to black, straight into the
 * game. About 0.86 s; it ends on black, so the launcher never flashes up
 * between the animation and the game.
 */

private object Ign {
    const val TOTAL = 860f
    const val RISE_END = 260f
    const val TRACE_FROM = 110f
    const val HIT = 500f
    const val SWEEP_TO = 720f
    const val RING_TO = 780f
    const val ZOOM_FROM = 560f
    const val TITLE_IN = 140f
    const val TITLE_IN_END = 360f
    const val TITLE_OUT = 600f
    const val TITLE_OUT_END = 740f
}

private val Spark = Color(0xFFFFBE50)
private val SparkHot = Color(0xFFFFD84A)

private fun ph(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun eOut3(x: Float) = 1f - (1f - x).pow(3)
private fun eInOut3(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Everything that moves, from the time [t] (ms). */
private class IgnState(t: Float) {
    val rise = eOut3(ph(t, 0f, Ign.RISE_END))
    val zoom = ph(t, Ign.ZOOM_FROM, Ign.TOTAL).let { it * it }
    val charge = eInOut3(ph(t, Ign.TRACE_FROM, Ign.HIT))
    val struck = t >= Ign.HIT
    val hitK = if (struck) (1f - ph(t, Ign.HIT, Ign.HIT + 300f)).let { it * it } else 0f
    private val pop = if (struck) sin(PI.toFloat() * ph(t, Ign.HIT, Ign.HIT + 160f)) * 0.035f else 0f
    /** Scale of the cover. */
    val scale = (0.92f + 0.08f * rise) * (1f + pop) * (1f + 0.55f * zoom)
    val sweep = ph(t, Ign.HIT, Ign.SWEEP_TO)
    val ring = ph(t, Ign.HIT, Ign.RING_TO)
    val title = eOut3(ph(t, Ign.TITLE_IN, Ign.TITLE_IN_END)) * (1f - ph(t, Ign.TITLE_OUT, Ign.TITLE_OUT_END))
    val flash = if (struck) (1f - ph(t, Ign.HIT, Ign.HIT + 200f)).let { it * it } else 0f
    val scrim = 0.93f * eOut3(ph(t, 0f, 200f)) + 0.07f * zoom
    val black = ph(t, Ign.ZOOM_FROM + 120f, Ign.TOTAL)
}

/**
 * A point on the outline of a rounded rectangle (half sizes [hw] × [hh], corner
 * [r]), [s] 0..1 of the way from the bottom middle to the top middle; [dir] -1
 * goes round the left side, 1 the right.
 */
private fun outlinePoint(hw: Float, hh: Float, r: Float, s: Float, dir: Float): Offset {
    val sx = hw - r
    val sy = hh - r
    val arc = PI.toFloat() * r / 2f
    val half = sx + arc + 2f * sy + arc + sx
    var d = s * half
    val x: Float
    val y: Float
    if (d < sx) { x = -d; y = hh } else {
        d -= sx
        if (d < arc) {
            val a = PI.toFloat() / 2f + d / r
            x = -sx + r * cos(a); y = sy + r * sin(a)
        } else {
            d -= arc
            if (d < 2f * sy) { x = -hw; y = sy - d } else {
                d -= 2f * sy
                if (d < arc) {
                    val a = PI.toFloat() + d / r
                    x = -sx + r * cos(a); y = -sy + r * sin(a)
                } else {
                    d -= arc
                    x = -sx + d; y = -hh
                }
            }
        }
    }
    return Offset(x * -dir, y)
}

/**
 * The launch animation. [coverUrl] is the game's cover, [heroUrl] its wide
 * background art (blurred faintly behind), [subtitle] the console. [onDone]
 * opens the game — called once, at the end (or right away when the system's
 * animations are switched off).
 */
@Composable
fun LaunchAnimation(
    coverUrl: String?,
    title: String,
    onDone: () -> Unit,
    heroUrl: String? = null,
    subtitle: String? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("LaunchAnimation")
    val done by rememberUpdatedState(onDone)
    val progress = remember { Animatable(0f) }
    val animationsOn = rememberAnimationsEnabled()
    LaunchedEffect(Unit) {
        if (animationsOn) progress.animateTo(1f, tween(Ign.TOTAL.toInt(), easing = LinearEasing))
        done()
    }
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    val path = remember { Path() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val coverH = maxHeight * 0.5f
        val coverW = coverH * (2f / 3f)
        val corner = coverH * 0.045f
        // The cover's centre sits a little above the middle, leaving room for the name.
        val centreY = maxHeight * 0.44f
        val density = LocalDensity.current
        val coverHpx = with(density) { coverH.toPx() }
        fun state() = IgnState(progress.value * Ign.TOTAL)

        // The launcher dims away.
        Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xFF040306).copy(alpha = state().scrim.coerceIn(0f, 1f))) }
        // A faint, soft copy of the game's background art.
        val backdrop = heroUrl ?: coverUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = state()
                        alpha = 0.16f * s.rise * (1f - s.zoom)
                        val k = 1.12f - 0.06f * s.rise + 0.2f * s.zoom
                        scaleX = k; scaleY = k
                    }
                    .blur(24.dp)
            )
        }
        // A warm glow behind the cover, building with the charge.
        Canvas(Modifier.fillMaxSize()) {
            val s = state()
            val c = Offset(size.width / 2f, centreY.toPx())
            val a = (0.07f + 0.08f * s.charge + 0.16f * s.hitK) * (1f - s.zoom)
            drawRect(
                Brush.radialGradient(
                    0f to Color(0xFFE6781E).copy(alpha = a), 0.5f to Color(0xFFA04614).copy(alpha = a * 0.4f), 1f to Color.Transparent,
                    center = c, radius = coverHpx * 1.15f
                )
            )
        }
        // The cover.
        val coverShape = RoundedCornerShape(corner)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = centreY - coverH / 2f)
                .size(coverW, coverH)
                .graphicsLayer {
                    val s = state()
                    scaleX = s.scale; scaleY = s.scale
                    translationY = (1f - s.rise) * coverHpx * 0.08f
                    alpha = s.rise * (1f - s.zoom)
                    shadowElevation = 24.dp.toPx()
                    shape = coverShape
                    clip = true
                }
                .background(Color(0xFF1A1410))
                .drawWithContent {
                    drawContent()
                    // A soft light sweeping across the art after the flare.
                    val sw = state().sweep
                    if (sw > 0f && sw < 1f) {
                        val bx = -size.width + 2f * size.width * eInOut3(sw)
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent, 0.5f to Color(0xFFFFF8E6).copy(alpha = 0.32f), 1f to Color.Transparent,
                                start = Offset(bx + size.width * 0.28f, 0f), end = Offset(bx + size.width * 0.72f, size.height)
                            ),
                            blendMode = BlendMode.Plus
                        )
                    }
                }
        ) {
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(14.dp)
                )
            }
        }
        // The light running round the edge, the flare and the ring.
        Canvas(Modifier.fillMaxSize()) {
            val s = state()
            val c = Offset(size.width / 2f, centreY.toPx() + (1f - s.rise) * coverHpx * 0.08f)
            val pad = 4.dp.toPx()
            val hw = (coverW.toPx() / 2f + pad) * s.scale
            val hh = (coverH.toPx() / 2f + pad) * s.scale
            val r = (corner.toPx() + pad) * s.scale
            val fade = s.rise * (1f - s.zoom)
            if (s.charge > 0f && fade > 0f) {
                for (dir in floatArrayOf(-1f, 1f)) {
                    path.reset()
                    val n = 80
                    for (i in 0..n) {
                        val q = outlinePoint(hw, hh, r, s.charge * i / n, dir)
                        if (i == 0) path.moveTo(c.x + q.x, c.y + q.y) else path.lineTo(c.x + q.x, c.y + q.y)
                    }
                    val glow = 1f + 1.5f * s.hitK
                    for ((w, a) in arrayOf(14f to 0.06f, 6f to 0.2f, 2.2f to 1f)) {
                        drawPath(
                            path, Spark.copy(alpha = (a * glow).coerceAtMost(1f) * fade),
                            style = Stroke((w * 0.5f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            blendMode = BlendMode.Plus
                        )
                    }
                    if (s.charge < 1f) {
                        val h = outlinePoint(hw, hh, r, s.charge, dir)
                        val hc = Offset(c.x + h.x, c.y + h.y)
                        val rad = 11.dp.toPx()
                        drawCircle(
                            Brush.radialGradient(
                                0f to Color.White, 0.3f to SparkHot.copy(alpha = 0.7f), 1f to Color(0xFFFFA028).copy(alpha = 0f),
                                center = hc, radius = rad
                            ),
                            radius = rad, center = hc, blendMode = BlendMode.Plus
                        )
                    }
                }
            }
            if (s.struck && s.ring < 1f) {
                val k = 1f + 0.5f * eOut3(s.ring)
                drawRoundRect(
                    Color(0xFFFFC86E).copy(alpha = 0.6f * (1f - s.ring) * (1f - s.ring)),
                    topLeft = Offset(c.x - hw * k, c.y - hh * k), size = Size(2f * hw * k, 2f * hh * k),
                    cornerRadius = CornerRadius(r * k), style = Stroke(1.5.dp.toPx() * (1f - s.ring) + 0.5f),
                    blendMode = BlendMode.Plus
                )
            }
        }
        // The game's name and console under the cover.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = centreY + coverH / 2f + maxHeight * 0.045f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    val s = state()
                    alpha = s.title
                    translationY = (1f - s.title) * 8.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title, color = Color.White, fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp), fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle.uppercase(), color = accent.copy(alpha = 0.9f), fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.em, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // A warm flash at the flare, then black — straight into the game.
        Canvas(Modifier.fillMaxSize()) {
            val s = state()
            if (s.flash > 0f) drawRect(Color(0xFFFFC878).copy(alpha = 0.08f * s.flash), blendMode = BlendMode.Plus)
            if (s.black > 0f) drawRect(Color.Black.copy(alpha = s.black))
        }
    }
}
