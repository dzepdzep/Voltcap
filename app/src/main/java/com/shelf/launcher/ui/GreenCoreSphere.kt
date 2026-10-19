package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.shelf.launcher.data.Features
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Update 202: Green Core's drawings, all made in code (no pictures): the living sphere (a 3D latitude / longitude grid
 * turning slowly, a pulsing energy core, tilted rings going round, a slow gauge ring and faint circuit traces), the
 * near-black green backdrop, the angled panel shape and the little preview for the layout list. Original drawings only —
 * an homage to the feel of a 2001 console dashboard, nobody's logos or artwork.
 *
 * Performance (handhelds): ONE Canvas for the sphere, its time read only in the draw phase (no recomposition per frame),
 * the brushes, strokes and paths made once per size / colour in drawWithCache (the paths are reset and refilled each
 * frame, nothing allocated per frame), frames capped (60, 30 with Lite effects, 15 while idle) and stopped while hidden,
 * idle, with reduced motion or the system's animations off.
 */

/** Update 202: Green Core's light colour (Layout options › Accent colour). Reads the pref's state (redraws on change). */
internal fun greenCoreAccent(): Color = Color(Features.gcAccent.state.value.argb)

/** All the clock's periods divide this, so wrapping the time round at it never shows a jump. */
private const val GC_PERIOD = 3600f
private const val TAU = (2.0 * PI).toFloat()

/**
 * Update 202: the sphere's time in seconds (already scaled by the speed), written at most [fps] times a second while
 * [running]. Read it in the draw phase only.
 */
@Composable
internal fun rememberGcClock(running: Boolean, speed: Float, fps: Int): FloatState {
    val t = remember { mutableFloatStateOf(0f) }
    val speedNow by rememberUpdatedState(speed)
    LaunchedEffect(running, fps) {
        if (!running) return@LaunchedEffect
        val minNs = (1_000_000_000L / fps.coerceIn(10, 120)) - 2_000_000L
        var last = -1L
        var shown = 0L
        var acc = 0f
        while (true) {
            withFrameNanos { now ->
                if (last < 0L) { last = now; shown = now }
                val dt = (now - last).coerceIn(0L, 100_000_000L) / 1_000_000_000f
                last = now
                acc += dt * speedNow
                if (now - shown >= minNs) {
                    shown = now
                    var v = t.floatValue + acc
                    if (v >= GC_PERIOD) v -= GC_PERIOD
                    t.floatValue = v
                    acc = 0f
                }
            }
        }
    }
    return t
}

/**
 * 0.6.15: line pieces of the sphere's grid and rings, drawn as single lines inside one layer (see [drawGcSegs]) instead
 * of as a stroked path: the rebuilt-every-frame path was rasterised on the CPU each frame (Odin 2 Portal: about half
 * of the render work while Green Core was on screen); separate lines are drawn by the GPU.
 */
internal class GcSegs(capacity: Int) {
    var pts = FloatArray(capacity * 4)
    var count = 0
    fun reset() { count = 0 }
    fun add(x0: Float, y0: Float, x1: Float, y1: Float) {
        if ((count + 1) * 4 > pts.size) pts = pts.copyOf(pts.size * 2)
        val o = count * 4
        pts[o] = x0; pts[o + 1] = y0; pts[o + 2] = x1; pts[o + 3] = y1
        count++
    }
}

/**
 * The pieces in [segs] at [color]: drawn opaque into a layer that gets the colour's alpha as a whole, so where pieces
 * meet or cross nothing gets brighter — the same look as the one stroked path it replaces.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGcSegs(segs: GcSegs, color: Color, width: Float, bound: Float, layer: androidx.compose.ui.graphics.Paint) {
    if (segs.count == 0 || color.alpha <= 0f) return
    val solid = color.copy(alpha = 1f)
    layer.alpha = color.alpha.coerceIn(0f, 1f)
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(-bound, -bound, bound, bound), layer)
    val p = segs.pts
    for (k in 0 until segs.count) {
        val o = k * 4
        drawLine(solid, Offset(p[o], p[o + 1]), Offset(p[o + 2], p[o + 3]), strokeWidth = width)
    }
    drawContext.canvas.restore()
}

/** The sphere's unit geometry: latitude rings and great circles through the poles, made once. */
private class GcGeo(lite: Boolean) {
    val lats = if (lite) 5 else 7
    val latSeg = if (lite) 36 else 48
    val lons = if (lite) 7 else 11
    val lonSeg = if (lite) 28 else 36
    val ringSeg = 64
    val latPts = FloatArray(lats * (latSeg + 1) * 3)
    val lonPts = FloatArray(lons * (lonSeg + 1) * 3)
    val ringPts = FloatArray((ringSeg + 1) * 2)
    /** Scratch for one polyline's projected points (x, y, depth). */
    private val proj = FloatArray((max(max(latSeg, lonSeg), ringSeg) + 1) * 3)

    init {
        var o = 0
        for (j in 0 until lats) {
            val phi = (-PI / 2 + PI * (j + 1) / (lats + 1)).toFloat()
            val y = -sin(phi)
            val r = cos(phi)
            for (k in 0..latSeg) {
                val a = TAU * k / latSeg
                latPts[o++] = r * cos(a); latPts[o++] = y; latPts[o++] = r * sin(a)
            }
        }
        o = 0
        for (m in 0 until lons) {
            val th = (PI * m / lons).toFloat()
            for (k in 0..lonSeg) {
                val b = TAU * k / lonSeg
                lonPts[o++] = cos(b) * cos(th); lonPts[o++] = sin(b); lonPts[o++] = cos(b) * sin(th)
            }
        }
        for (k in 0..ringSeg) {
            val b = TAU * k / ringSeg
            ringPts[k * 2] = cos(b); ringPts[k * 2 + 1] = sin(b)
        }
    }

    /**
     * Projects one polyline of [n] + 1 points starting at [src][start] (x, y, z triples, times [r], first turned [tiltX]
     * round the X axis — a ring's own tilt), turned [yaw] round the upright axis and [tilt] towards you, into [front] or
     * [back] by which side each piece faces.
     */
    fun polyline(
        src: FloatArray, start: Int, n: Int, r: Float, tiltX: Float, yaw: Float, tilt: Float, radius: Float,
        front: GcSegs, back: GcSegs
    ) {
        val cy = cos(yaw); val sy = sin(yaw)
        val ct = cos(tilt); val st = sin(tilt)
        val cx = cos(tiltX); val sx = sin(tiltX)
        for (k in 0..n) {
            val i = start + k * 3
            var x = src[i] * r; var y = src[i + 1] * r; var z = src[i + 2] * r
            if (tiltX != 0f) { val y0 = y * cx - z * sx; z = y * sx + z * cx; y = y0 }
            val x1 = x * cy + z * sy
            val z1 = -x * sy + z * cy
            val y2 = y * ct - z1 * st
            val z2 = y * st + z1 * ct
            val p = 1f / (1f - 0.12f * z2 / max(r, 1f))
            proj[k * 3] = x1 * radius * p
            proj[k * 3 + 1] = y2 * radius * p
            proj[k * 3 + 2] = z2
        }
        for (k in 0 until n) {
            val f = proj[k * 3 + 2] + proj[k * 3 + 5] >= 0f
            (if (f) front else back).add(proj[k * 3], proj[k * 3 + 1], proj[k * 3 + 3], proj[k * 3 + 4])
        }
    }

    /** A ring as (x, y, z) triples, built on the fly from the unit circle (into [buf]). */
    fun ring(buf: FloatArray): FloatArray {
        for (k in 0..ringSeg) { buf[k * 3] = ringPts[k * 2]; buf[k * 3 + 1] = 0f; buf[k * 3 + 2] = ringPts[k * 2 + 1] }
        return buf
    }
}

/**
 * Update 202: the living sphere, filling [modifier] (the layout's whole area). Its centre sits at ([cx], [cy]) as shares of
 * the area, [sphereScale] times its size; [dim] fades it (0–1); [flash] (0–1) brightens the core for a moment after a move.
 * Everything animated is read in the draw phase.
 */
@Composable
internal fun GreenCoreSphere(
    modifier: Modifier,
    clock: FloatState,
    cx: State<Float>,
    cy: State<Float>,
    sphereScale: State<Float>,
    dim: State<Float>,
    flash: () -> Float,
    lite: Boolean,
    bgMotion: Boolean
) {
    val accent = greenCoreAccent()
    val glow = Features.gcGlow.state.value / 100f
    val geo = remember(lite) { GcGeo(lite) }
    val ringBuf = remember(geo) { FloatArray((geo.ringSeg + 1) * 3) }
    val flashNow by rememberUpdatedState(flash)
    val draw = remember(accent, glow, geo, bgMotion) {
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val r0 = min(w * 0.24f, h * 0.40f).coerceAtLeast(1f)
            val px = density
            val dark = Color(0xFF031007)
            val deep = mixGc(accent, Color.Black, 0.72f)
            val halo = Brush.radialGradient(
                0f to accent.copy(alpha = 0.30f * glow + 0.04f), 0.42f to accent.copy(alpha = 0.10f * glow + 0.02f), 1f to Color.Transparent,
                center = Offset.Zero, radius = r0 * 1.9f
            )
            val body = Brush.radialGradient(
                0f to deep.copy(alpha = 0.62f), 0.72f to dark.copy(alpha = 0.55f), 0.93f to accent.copy(alpha = 0.10f * glow + 0.05f),
                1f to accent.copy(alpha = 0.22f * glow + 0.06f),
                center = Offset.Zero, radius = r0
            )
            val core = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.95f), 0.22f to mixGc(accent, Color.White, 0.55f), 0.55f to accent.copy(alpha = 0.55f),
                1f to Color.Transparent, center = Offset.Zero, radius = r0 * 0.34f
            )
            val coreHalo = Brush.radialGradient(
                0f to accent.copy(alpha = 0.42f * glow + 0.08f), 1f to Color.Transparent, center = Offset.Zero, radius = r0 * 0.78f
            )
            val specAt = Offset(-r0 * 0.36f, -r0 * 0.42f)
            val spec = Brush.radialGradient(0f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent, center = specAt, radius = r0 * 0.5f)
            val bgR = max(w, h) * 0.55f
            val bgGlow = Brush.radialGradient(0f to accent.copy(alpha = 0.06f * glow + 0.02f), 1f to Color.Transparent, center = Offset.Zero, radius = bgR)
            val thin = Stroke(1f * px)
            val med = Stroke(1.3f * px)
            val arcStroke = Stroke(2f * px, cap = StrokeCap.Round)
            val dashed = Stroke(1f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * px, 8f * px)))
            val ticks = Stroke(5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.2f * px, 14f * px)))
            val front = GcSegs(geo.lats * geo.latSeg + geo.lons * geo.lonSeg)
            val back = GcSegs(geo.lats * geo.latSeg + geo.lons * geo.lonSeg)
            val ringFront = GcSegs(geo.ringSeg * 3)
            val ringBack = GcSegs(geo.ringSeg * 3)
            val segLayer = androidx.compose.ui.graphics.Paint()
            val segBound = r0 * 1.8f
            // Faint circuit traces leaving the sphere to the right (towards the menu) and down-left: right-angled runs
            // ending in small pads. Fixed to the sphere, so they move with it between the pages.
            val traces = Path().apply {
                moveTo(r0 * 1.02f, -r0 * 0.30f); lineTo(r0 * 1.45f, -r0 * 0.30f); lineTo(r0 * 1.62f, -r0 * 0.48f); lineTo(r0 * 2.2f, -r0 * 0.48f)
                moveTo(r0 * 1.0f, r0 * 0.12f); lineTo(r0 * 1.7f, r0 * 0.12f); lineTo(r0 * 1.86f, r0 * 0.28f); lineTo(r0 * 2.4f, r0 * 0.28f)
                moveTo(r0 * 0.82f, r0 * 0.62f); lineTo(r0 * 1.12f, r0 * 0.92f); lineTo(r0 * 1.9f, r0 * 0.92f)
                moveTo(-r0 * 0.9f, r0 * 0.5f); lineTo(-r0 * 1.3f, r0 * 0.9f); lineTo(-r0 * 1.3f, r0 * 1.3f)
                moveTo(-r0 * 0.7f, -r0 * 0.75f); lineTo(-r0 * 1.0f, -r0 * 1.05f); lineTo(-r0 * 1.6f, -r0 * 1.05f)
            }
            val pads = floatArrayOf(
                r0 * 2.2f, -r0 * 0.48f, r0 * 2.4f, r0 * 0.28f, r0 * 1.9f, r0 * 0.92f, -r0 * 1.3f, r0 * 1.3f, -r0 * 1.6f, -r0 * 1.05f
            )
            val gridBack = accent.copy(alpha = 0.07f + 0.08f * glow)
            val gridFront = accent.copy(alpha = 0.16f + 0.26f * glow)
            val ringCol = mixGc(accent, Color.White, 0.15f)
            onDrawBehind {
                val t = clock.floatValue
                val s = sphereScale.value
                val d = dim.value.coerceIn(0f, 1f)
                val fl = flashNow().coerceIn(0f, 1f)
                val pulse = 0.5f + 0.5f * sin(t * TAU / 2.4f)
                val flick = 0.93f + 0.045f * sin(t * TAU / 7.2f) + 0.025f * sin(t * TAU / 3.6f)
                // The slow drifting light behind everything (Background motion).
                if (bgMotion) {
                    val bx = w * (0.5f + 0.32f * sin(t * TAU / 45f))
                    val by = h * (0.48f + 0.22f * cos(t * TAU / 45f))
                    translate(bx, by) { drawCircle(bgGlow, bgR, Offset.Zero, alpha = 0.8f + 0.2f * flick) }
                }
                translate(cx.value * w, cy.value * h) {
                    scale(s, s, pivot = Offset.Zero) {
                        drawCircle(halo, r0 * 1.9f, Offset.Zero, alpha = ((0.72f + 0.28f * pulse) * flick + 0.25f * fl).coerceAtMost(1f) * d)
                        // The machine round it: a dashed gauge ring, ticks and four bracket arcs turning slowly the other way.
                        rotate(t * 1.5f, pivot = Offset.Zero) {
                            drawCircle(accent.copy(alpha = (0.10f + 0.14f * glow) * d), r0 * 1.62f, Offset.Zero, style = dashed)
                            drawCircle(accent.copy(alpha = (0.06f + 0.1f * glow) * d), r0 * 1.71f, Offset.Zero, style = ticks)
                        }
                        rotate(-t * 2f, pivot = Offset.Zero) {
                            val a = r0 * 1.8f
                            for (q in 0 until 4) drawArc(
                                accent.copy(alpha = (0.14f + 0.16f * glow) * d), q * 90f + 12f, 46f, false,
                                Offset(-a, -a), Size(a * 2f, a * 2f), style = med
                            )
                        }
                        drawPath(traces, accent.copy(alpha = (0.06f + 0.07f * glow) * d), style = thin)
                        var i = 0
                        while (i < pads.size) { drawCircle(accent.copy(alpha = (0.10f + 0.12f * glow) * d), 2.4f * px, Offset(pads[i], pads[i + 1])); i += 2 }
                        // The sphere's body: dark glass with a lit rim.
                        drawCircle(body, r0, Offset.Zero, alpha = d)
                        // Its grid, turning (one turn a minute), tilted towards you; the far side faint.
                        val yaw = t * TAU / 60f
                        val tilt = -0.36f
                        front.reset(); back.reset()
                        for (j in 0 until geo.lats) geo.polyline(geo.latPts, j * (geo.latSeg + 1) * 3, geo.latSeg, 1f, 0f, yaw, tilt, r0, front, back)
                        for (m in 0 until geo.lons) geo.polyline(geo.lonPts, m * (geo.lonSeg + 1) * 3, geo.lonSeg, 1f, 0f, yaw, tilt, r0, front, back)
                        drawGcSegs(back, gridBack.copy(alpha = gridBack.alpha * d), thin.width, segBound, segLayer)
                        // Three tilted rings going round at their own speeds (the far halves behind the core).
                        ringFront.reset(); ringBack.reset()
                        val rb = geo.ring(ringBuf)
                        geo.polyline(rb, 0, geo.ringSeg, 1.24f, 0.42f, -t * TAU / 90f, tilt, r0, ringFront, ringBack)
                        geo.polyline(rb, 0, geo.ringSeg, 1.38f, -0.62f, t * TAU / 120f, tilt, r0, ringFront, ringBack)
                        geo.polyline(rb, 0, geo.ringSeg, 1.12f, 1.25f, t * TAU / 180f, tilt, r0, ringFront, ringBack)
                        drawGcSegs(ringBack, ringCol.copy(alpha = (0.06f + 0.08f * glow) * d), thin.width, segBound, segLayer)
                        // The energy core: it breathes, flashes on a move, and its filaments turn round it.
                        val cs = 1f + 0.07f * pulse + 0.22f * fl
                        scale(cs, cs, pivot = Offset.Zero) {
                            drawCircle(coreHalo, r0 * 0.78f, Offset.Zero, alpha = ((0.65f + 0.35f * pulse) * flick).coerceAtMost(1f) * d)
                            drawCircle(core, r0 * 0.34f, Offset.Zero, alpha = d)
                        }
                        val fr = r0 * 0.44f
                        rotate(t * 40f, pivot = Offset.Zero) {
                            drawArc(accent.copy(alpha = (0.25f + 0.35f * glow) * d * flick), 0f, 64f, false, Offset(-fr, -fr), Size(fr * 2f, fr * 2f), style = arcStroke)
                            drawArc(accent.copy(alpha = (0.18f + 0.3f * glow) * d * flick), 180f, 40f, false, Offset(-fr, -fr), Size(fr * 2f, fr * 2f), style = arcStroke)
                        }
                        val fr2 = r0 * 0.56f
                        rotate(-t * 26f, pivot = Offset.Zero) {
                            drawArc(accent.copy(alpha = (0.12f + 0.22f * glow) * d), 90f, 80f, false, Offset(-fr2, -fr2), Size(fr2 * 2f, fr2 * 2f), style = thin)
                            drawArc(accent.copy(alpha = (0.12f + 0.22f * glow) * d), 270f, 50f, false, Offset(-fr2, -fr2), Size(fr2 * 2f, fr2 * 2f), style = thin)
                        }
                        // The near side of the grid and the rings, over the core.
                        drawGcSegs(front, gridFront.copy(alpha = gridFront.alpha * d * flick), med.width, segBound, segLayer)
                        drawGcSegs(ringFront, ringCol.copy(alpha = (0.2f + 0.3f * glow) * d), med.width, segBound, segLayer)
                        // A soft highlight up on the left and the rim line.
                        drawCircle(spec, r0 * 0.5f, specAt, alpha = d)
                        drawCircle(accent.copy(alpha = (0.18f + 0.3f * glow) * d * flick), r0, Offset.Zero, style = thin)
                    }
                }
            }
        }
    }
    Spacer(modifier.then(draw))
}

/** Update 202: Green Core's near-black green backdrop over the whole screen: a deep gradient, a wash of the light, a faint
 *  floor grid and a vignette. Still (the sphere is the only thing that moves), made once per size / colour. */
@Composable
internal fun GreenCoreBackdrop(modifier: Modifier) {
    val accent = greenCoreAccent()
    val glow = Features.gcGlow.state.value / 100f
    val draw = remember(accent, glow) {
        Modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val bg = Brush.verticalGradient(listOf(Color(0xFF030806), Color(0xFF07150B), Color(0xFF020503)))
            val wash = Brush.radialGradient(
                0f to accent.copy(alpha = 0.05f + 0.07f * glow), 1f to Color.Transparent,
                center = Offset(w * 0.3f, h * 0.52f), radius = max(w, h) * 0.62f
            )
            val vignette = Brush.radialGradient(
                0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f),
                center = Offset(w * 0.42f, h * 0.5f), radius = max(w, h) * 0.78f
            )
            // A floor of thin lines receding to a horizon low on the screen.
            val horizon = h * 0.8f
            val floor = Path().apply {
                for (k in -12..12) { moveTo(w * 0.5f + k * w * 0.02f, horizon); lineTo(w * 0.5f + k * w * 0.16f, h) }
                var y = horizon
                var gap = h * 0.012f
                while (y < h) { moveTo(0f, y); lineTo(w, y); y += gap; gap *= 1.45f }
            }
            val floorFade = Brush.verticalGradient(0f to Color.Transparent, 1f to accent.copy(alpha = 0.05f + 0.06f * glow), startY = horizon, endY = h)
            val line = Stroke(1f * density)
            onDrawBehind {
                drawRect(bg)
                drawRect(wash)
                drawPath(floor, floorFade, style = line)
                drawRect(vignette)
            }
        }
    }
    Spacer(modifier.then(draw))
}

/**
 * Update 202: an elongated panel with angular edges — the left edge slanted, the top-right and bottom-left corners cut.
 * [cut] the corner cut, [slant] how far the top of the left edge leans in.
 */
internal class GcPanelShape(private val cut: Dp, private val slant: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(size.height / 2f).coerceAtMost(size.width / 4f)
        val s = with(density) { slant.toPx() }.coerceAtMost(size.width / 4f)
        val p = Path().apply {
            moveTo(s, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            close()
        }
        return Outline.Generic(p)
    }

    override fun equals(other: Any?): Boolean = other is GcPanelShape && other.cut == cut && other.slant == slant
    override fun hashCode(): Int = cut.hashCode() * 31 + slant.hashCode()
}

internal fun mixGc(a: Color, b: Color, t: Float): Color =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t)

/**
 * Update 202: the little drawn screen of Green Core for the layout list and presets (PresetPreview): the dark green
 * screen, the sphere with its grid, core and a ring, and the six angled panels on the right, the first one lit.
 */
internal fun greenCorePreview(d: DrawScope, @Suppress("UNUSED_PARAMETER") themeAccent: Color) {
    with(d) {
        val a = greenCoreAccent()
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF040A06), Color(0xFF08180D), Color(0xFF020503))))
        val c = Offset(w * 0.3f, h * 0.54f)
        val r = h * 0.28f
        drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.32f), Color.Transparent), center = c, radius = r * 1.9f), r * 1.9f, c)
        drawCircle(Color(0xFF06140A), r, c)
        val line = Stroke(maxOf(1f, w / 360f))
        for (j in 1..4) {
            val yy = c.y - r + j * (2f * r / 5f)
            val half = kotlin.math.sqrt((r * r - (yy - c.y) * (yy - c.y)).coerceAtLeast(0f))
            drawOval(a.copy(alpha = 0.35f), Offset(c.x - half, yy - half * 0.22f), Size(half * 2f, half * 0.44f), style = line)
        }
        for (m in 1..3) {
            val ww = r * (m / 3f)
            drawOval(a.copy(alpha = 0.3f), Offset(c.x - ww, c.y - r), Size(ww * 2f, r * 2f), style = line)
        }
        rotate(-18f, c) { drawOval(a.copy(alpha = 0.45f), Offset(c.x - r * 1.35f, c.y - r * 0.3f), Size(r * 2.7f, r * 0.6f), style = line) }
        drawCircle(Brush.radialGradient(listOf(Color.White, a, Color.Transparent), center = c, radius = r * 0.32f), r * 0.32f, c)
        drawCircle(a.copy(alpha = 0.5f), r, c, style = line)
        // The panels, curving round the sphere.
        for (i in 0 until 6) {
            val bend = abs(i - 2.5f)
            val x = w * 0.6f + bend * bend * w * 0.008f
            val y = h * (0.2f + i * 0.115f)
            val pw = w * 0.34f
            val ph = h * 0.085f
            val cut = ph * 0.3f
            val p = Path().apply {
                moveTo(x + cut, y); lineTo(x + pw - cut, y); lineTo(x + pw, y + cut); lineTo(x + pw, y + ph)
                lineTo(x + cut, y + ph); lineTo(x, y + ph - cut); close()
            }
            val on = i == 0
            drawPath(p, a.copy(alpha = if (on) 0.45f else 0.12f))
            drawPath(p, a.copy(alpha = if (on) 0.95f else 0.35f), style = line)
            drawCircle(a.copy(alpha = if (on) 1f else 0.5f), ph * 0.2f, Offset(x + ph * 0.55f, y + ph / 2f))
            drawLine(Color.White.copy(alpha = if (on) 0.9f else 0.5f), Offset(x + ph * 1.1f, y + ph / 2f), Offset(x + ph * 1.1f + pw * (0.3f + (i % 3) * 0.08f), y + ph / 2f), strokeWidth = ph * 0.18f)
        }
    }
}
