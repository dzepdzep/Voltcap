package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Connected Ignition (owner-approved, revision 2 of tools/connected-ignition): at start-up the intro's capsule outline opens at
 * both ends, runs out and down into the profile row, traces each profile's ring the way the intro's two sparks trace the capsule
 * and meets at the profile that played last; the Ignition look keeps that gold line, settled and static, joining the profiles.
 * This file: the timing on the intro's clock, the pure wire geometry (unit-tested) and its drawing. No shaders, no offscreen
 * layers; nothing redraws once settled (a press sends one short spark, then it is still again).
 */

/** The continuation's times (ms) on the intro's own clock; it opens inside the final shine. */
internal class IgnitionTiming(
    val open: Float, val turn: Float, val rowEnd: Float, val recedeFrom: Float, val recedeTo: Float,
    val titleFrom: Float, val titleTo: Float, val upperOutFrom: Float, val upperOutTo: Float, val ringDur: Float,
    val focusTo: Float, val hintsFrom: Float, val hintsTo: Float, val settled: Float
)

internal val IG_FULL = IgnitionTiming(2080f, 2520f, 3140f, 2240f, 2860f, 2760f, 3160f, 2980f, 3480f, 240f, 3420f, 3260f, 3560f, 3600f)
internal val IG_SHORT = IgnitionTiming(430f, 620f, 900f, 470f, 760f, 700f, 900f, 820f, 1060f, 150f, 1060f, 960f, 1120f, 1140f)

internal object IgnK {
    val Orange = Color(0xFFF59A2C)
    val Yellow = Color(0xFFFFD84A)
    val Cream = Color(0xFFFFF6D6)
    val Stage = Color(0xFF08070B)
    fun ph(t: Float, a: Float, b: Float): Float = if (b <= a) (if (t >= a) 1f else 0f) else ((t - a) / (b - a)).coerceIn(0f, 1f)
    fun eOut(x: Float): Float = 1f - (1f - x).pow(3)
    fun eInOut(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    /** Lift of profile [i] when the drawn focus is at [pos] (smoothstep of the distance; 1 = focused). Pure. */
    fun liftAt(pos: Float, i: Int): Float { val x = (1f - abs(pos - i)).coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

    /**
     * How long the drawn focus takes to glide [dist] profiles. A press that comes [sinceLast] ms after the previous one (held /
     * auto-repeat, < 140 ms) glides quicker so the focus never falls behind the cursor. Pure.
     */
    fun glideMs(dist: Float, sinceLast: Long): Int = ((if (sinceLast in 0 until 140) 110f else 150f) + 45f * dist).roundToInt().coerceIn(120, 300)
}

/**
 * The wire's two halves (top over each ring, bottom under it), sampled every [step] px from [left] to [right]. Between rings
 * both halves lie on the row's centre line; [ringOf] says which profile's ring a sample belongs to (-1: the line between). Pure.
 */
internal class WireGeo(val left: Float, val step: Float, val top: FloatArray, val bottom: FloatArray, val ringOf: IntArray) {
    val size: Int get() = top.size
    fun x(i: Int): Float = left + i * step
    fun idx(x: Float): Int = ((x - left) / step).roundToInt().coerceIn(0, size - 1)

    companion object {
        /**
         * [xs] the profiles' centres, [lifts] 0..1 each (a lifted ring is a little bigger and higher: [rise] px at 1), [rho] the
         * ring's radius at rest, [cy] the row's centre line.
         */
        fun build(xs: FloatArray, lifts: FloatArray, cy: Float, rho: Float, rise: Float, left: Float, right: Float, step: Float): WireGeo {
            val n = (((right - left) / step).toInt() + 1).coerceAtLeast(2)
            val top = FloatArray(n); val bottom = FloatArray(n); val ring = IntArray(n) { -1 }
            for (k in 0 until n) {
                val x = left + k * step
                var yT = cy; var yB = cy
                for (i in xs.indices) {
                    val l = lifts.getOrElse(i) { 0f }
                    val rr = rho * (1f + 0.16f * l)
                    val dx = x - xs[i]
                    if (abs(dx) < rr) {
                        val c = cy - rise * l
                        val h = sqrt(rr * rr - dx * dx)
                        yT = c - h; yB = c + h; ring[k] = i
                        break
                    }
                }
                top[k] = yT; bottom[k] = yB
            }
            return WireGeo(left, step, top, bottom, ring)
        }

        /**
         * When profile at [x] appears while the row is traced inward from both ends ([leftEnd] / [rightEnd]) to the focused ring
         * ([focusL] / [focusR] its edges): its share of that side's run, on [tm]'s turn → rowEnd. Pure.
         */
        fun appearAt(x: Float, focusX: Float, leftEnd: Float, rightEnd: Float, focusL: Float, focusR: Float, tm: IgnitionTiming): Float {
            val f = when {
                x < focusX - 0.5f -> ((x - leftEnd) / (focusL - leftEnd).coerceAtLeast(1f))
                x > focusX + 0.5f -> ((rightEnd - x) / (rightEnd - focusR).coerceAtLeast(1f))
                else -> 1f
            }.coerceIn(0f, 1f)
            return tm.turn + (tm.rowEnd - tm.turn) * f * 0.92f
        }
    }
}

/** A D-pad press's spark along the wire: from the drawn focus [from] to profile [to], starting at [t0] (uptime ms). */
internal class WireImpulse(val from: Float, val to: Int, val t0: Long, val dur: Int)

/** What the wire layer needs from the picker: the profiles' centres in the layer, their colours, the focus and the reveal. */
internal class WireInputs(
    val centers: SnapshotStateList<Offset>,
    val accents: List<Color>,
    /** The ring's radius at rest (px) and how high a lifted profile rises (px). */
    val rho: Float, val rise: Float,
    val cursor: Int,
    val focusPos: () -> Float,
    /** 0..1 of the pick (the wire drains into the chosen ring). */
    val exit: () -> Float,
    val impulses: SnapshotStateList<WireImpulse>,
    /** The wire stays (the Ignition look); otherwise it only carries the start-up and hands over. */
    val keep: Boolean,
    val lite: Boolean
)

/** The intro clock and its timings while the start-up runs (null afterwards and whenever the picker opens from elsewhere). */
internal class IgnitionReveal(val clock: State<Float>, val intro: IntroTiming, val tm: IgnitionTiming)

private val wireStroke = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
internal fun IgnitionWireLayer(inp: WireInputs, reveal: IgnitionReveal?, modifier: Modifier) {
    // A frame clock only while sparks run (each ≤ 340 ms); none at rest.
    val tick = remember { mutableLongStateOf(0L) }
    val busy = inp.impulses.isNotEmpty()
    if (busy) LaunchedEffect(inp.impulses.size) {
        while (inp.impulses.isNotEmpty()) {
            withFrameNanos { tick.longValue = it }
            val now = android.os.SystemClock.uptimeMillis()
            inp.impulses.removeAll { now > it.t0 + it.dur }
        }
    }
    val paths = remember { Array(6) { Path() } }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val frame = tick.longValue
        val cs = inp.centers
        if (cs.isEmpty() || cs.any { it.x.isNaN() }) return@Canvas
        val t = reveal?.clock?.value ?: Float.MAX_VALUE
        val tm = reveal?.tm
        val d = density
        val focus = inp.cursor.coerceIn(0, cs.size - 1)
        val conv = if (tm != null) IgnK.eOut(IgnK.ph(t, tm.rowEnd, tm.focusTo)) else 1f
        val pos = inp.focusPos()
        val lifts = FloatArray(cs.size) { IgnK.liftAt(pos, it) * conv }
        val cy = cs[focus].y // (the row's centre line at rest; a lifted ring is raised by WireGeo)
        val leftEnd = min(24f * d + 6f * d, cs.first().x - inp.rho - 18f * d)
        val rightEnd = max(size.width - 30f * d, cs.last().x + inp.rho + 18f * d)
        val geo = WireGeo.build(FloatArray(cs.size) { cs[it].x }, lifts, cy, inp.rho, inp.rise, leftEnd, rightEnd, max(1f, 1.2f * d))
        val last = geo.size - 1
        val wireVis = if (inp.keep) 1f else if (tm != null) 1f - IgnK.eInOut(IgnK.ph(t, tm.titleTo, tm.settled + 200f)) else 0f
        // ---- the opened outline (start-up only): from each capsule end out, round the capsule's own end, back along the row
        if (tm != null) drawOpening(t, tm, cy, leftEnd, rightEnd, inp.lite)
        if (wireVis <= 0.003f) return@Canvas
        val rowU = if (tm != null) IgnK.eInOut(IgnK.ph(t, tm.turn, tm.rowEnd)) else 1f
        val rrF = inp.rho * (1f + 0.16f * lifts[focus])
        val iFL = geo.idx(cs[focus].x - rrF); val iFR = geo.idx(cs[focus].x + rrF)
        val leftTo = (iFL * rowU).roundToInt(); val rightFrom = (last - (last - iFR) * rowU).roundToInt()
        val drain = IgnK.eInOut(IgnK.ph(inp.exit(), 0f, 0.9f))
        val l0 = (iFL * drain).roundToInt(); val r0 = (last - (last - iFR) * drain).roundToInt()
        val ranges = ArrayList<IntArray>(4)
        ranges += intArrayOf(l0, leftTo); ranges += intArrayOf(rightFrom, r0)
        if (rowU >= 1f) {
            val cc = if (tm != null) IgnK.eOut(IgnK.ph(t, tm.rowEnd, tm.rowEnd + tm.ringDur)) else 1f
            val mid = geo.idx(cs[focus].x)
            ranges += intArrayOf(iFL, (iFL + (mid - iFL) * cc).roundToInt()); ranges += intArrayOf((iFR - (iFR - mid) * cc).roundToInt(), iFR)
        }
        val a = wireVis
        val grad = Brush.horizontalGradient(
            0f to IgnK.Orange.copy(alpha = 0f), 0.07f to IgnK.Orange, 0.5f to IgnK.Yellow, 0.93f to IgnK.Orange, 1f to IgnK.Orange.copy(alpha = 0f),
            startX = leftEnd, endX = rightEnd
        )
        // The line between rings: warm gold, a narrow glow; resting rings a fine quiet socket line; the focused ring its colour.
        fun build(p: Path, want: (Int) -> Boolean) {
            p.reset()
            for (half in 0..1) {
                val ys = if (half == 0) geo.top else geo.bottom
                for (r in ranges) {
                    var open = false
                    val lo = r[0].coerceIn(0, last); val hi = r[1].coerceIn(0, last)
                    for (k in lo..hi) {
                        if (want(geo.ringOf[k])) {
                            if (!open) { p.moveTo(geo.x(k), ys[k]); open = true } else p.lineTo(geo.x(k), ys[k])
                        } else open = false
                    }
                }
            }
        }
        val line = paths[0]; build(line) { it < 0 }
        if (!inp.lite) drawPath(line, grad, alpha = 0.10f * a, style = Stroke(4.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        drawPath(line, grad, alpha = 0.66f * a, style = Stroke(1.3f * d, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
        val rest = paths[1]; build(rest) { it >= 0 && lifts[it] < 0.02f }
        drawPath(rest, grad, alpha = 0.30f * a, style = Stroke(1f * d), blendMode = BlendMode.Plus)
        for (i in cs.indices) {
            val lf = lifts[i]; if (lf < 0.02f) continue
            val p = paths[2]; build(p) { it == i }
            val col = lerp(IgnK.Yellow, inp.accents.getOrElse(i) { IgnK.Yellow }, 0.7f)
            if (!inp.lite) drawPath(p, col.copy(alpha = 0.16f * lf * a), style = Stroke(6f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawPath(p, lerp(Color(0xFFFFECBE), col, 0.6f).copy(alpha = (0.30f + 0.65f * lf) * a), style = Stroke((1f + lf) * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        }
        // Sparks: tracing heads, the meeting hit, select drain heads, D-pad impulses.
        if (tm != null && rowU > 0f && rowU < 1f) {
            spark(Offset(geo.x(leftTo), geo.top[leftTo]), 12f * d); spark(Offset(geo.x(rightFrom), geo.top[rightFrom]), 12f * d)
            if (abs(geo.top[leftTo] - geo.bottom[leftTo]) > 1f) { spark(Offset(geo.x(leftTo), geo.bottom[leftTo]), 10f * d); spark(Offset(geo.x(rightFrom), geo.bottom[rightFrom]), 10f * d) }
        }
        if (tm != null) {
            val u = IgnK.ph(t, tm.rowEnd + tm.ringDur * 0.7f, tm.rowEnd + tm.ringDur + 420f)
            if (u > 0f && u < 1f) {
                val c = Offset(cs[focus].x, cy - inp.rise * lifts[focus]); val k = (1f - u) * (1f - u)
                drawCircle(Color(0xFFFFC86E).copy(alpha = 0.55f * k), inp.rho * (1.05f + 0.6f * IgnK.eOut(u)), c, style = Stroke((5f * (1f - u) + 0.6f) * d), blendMode = BlendMode.Plus)
            }
        }
        if (drain > 0f && drain < 1f) { spark(Offset(geo.x(l0), geo.top[l0]), 13f * d); spark(Offset(geo.x(r0), geo.top[r0]), 13f * d) }
        if (inp.exit() > 0f) {
            val fr = sin(PI.toFloat() * IgnK.ph(inp.exit(), 0.45f, 1f))
            if (fr > 0f) { val p = paths[3]; build(p) { it == focus }; drawPath(p, Color(0xFFFFECBE).copy(alpha = 0.9f * fr), style = Stroke(2.4f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus) }
        }
        if (inp.exit() <= 0f && wireVis > 0.5f && inp.impulses.isNotEmpty()) {
            val now = android.os.SystemClock.uptimeMillis()
            fun posX(q: Float): Float { val i0 = q.toInt().coerceIn(0, cs.size - 1); val i1 = (i0 + 1).coerceAtMost(cs.size - 1); return cs[i0].x + (cs[i1].x - cs[i0].x) * (q - i0) }
            for (m in inp.impulses) {
                val u = ((now - m.t0).toFloat() / m.dur).coerceIn(0f, 1f); if (u <= 0f || u >= 1f) continue
                val e = IgnK.eOut(u); val i0 = geo.idx(posX(m.from)); val i1 = geo.idx(cs[m.to.coerceIn(0, cs.size - 1)].x)
                val ii = (i0 + (i1 - i0) * e).roundToInt().coerceIn(0, last); val tail = ((i1 - i0) * 0.22f).roundToInt()
                val fade = 1f - IgnK.ph(u, 0.75f, 1f)
                for (half in 0..1) {
                    val ys = if (half == 0) geo.top else geo.bottom
                    val j0 = min(ii, ii - tail).coerceIn(0, last); val j1 = max(ii, ii - tail).coerceIn(0, last)
                    val p = paths[4]; p.reset(); p.moveTo(geo.x(j0), ys[j0]); for (k in j0 + 1..j1) p.lineTo(geo.x(k), ys[k])
                    drawPath(p, IgnK.Yellow.copy(alpha = 0.85f * fade), style = Stroke(2.2f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                    spark(Offset(geo.x(ii), ys[ii]), 8f * d, 0.85f * fade)
                }
            }
        }
    }
}

/** The intro's spark head: white core, yellow, fading orange (additive). */
private fun DrawScope.spark(c: Offset, r: Float, a: Float = 1f) {
    drawCircle(
        Brush.radialGradient(0f to Color.White.copy(alpha = a), 0.25f to IgnK.Yellow.copy(alpha = 0.8f * a), 1f to Color(0xFFFFA028).copy(alpha = 0f), center = c, radius = r),
        radius = r, center = c, blendMode = BlendMode.Plus
    )
}

/**
 * The outline opening at the capsule's ends (same capsule geometry as drawIntro: r = min(0.2 w, 0.26 h) at (w / 2, 0.43 h)):
 * each side runs out along the capsule's centre line, turns down round a half circle the height of the gap, and joins the row
 * at its end; its inner end lets go as the capsule recedes, so the light pours out and down into the row.
 */
private fun DrawScope.drawOpening(t: Float, tm: IgnitionTiming, rowY: Float, leftEnd: Float, rightEnd: Float, lite: Boolean) {
    val upperA = 1f - IgnK.ph(t, tm.upperOutFrom, tm.upperOutTo)
    if (upperA <= 0f || t < tm.open) return
    val w = size.width; val h = size.height; val d = density
    val r = min(0.2f * w, 0.26f * h)
    val rec = IgnK.eInOut(IgnK.ph(t, tm.recedeFrom, tm.recedeTo))
    val capY = h * 0.43f - rec * r * 0.35f
    val u = IgnK.eInOut(IgnK.ph(t, tm.open, tm.turn))
    for (s in intArrayOf(-1, 1)) {
        val capX = w / 2f + s * r * (1f - 0.32f * rec)
        val endX = if (s < 0) leftEnd else rightEnd
        val turnR = max(8f * d, (rowY - capY) / 2f)
        val straight = (abs(endX - capX) - turnR).coerceAtLeast(0f)
        val total = straight + PI.toFloat() * turnR
        val len = total * u
        val from = total * IgnK.eInOut(IgnK.ph(t, tm.recedeFrom + 260f, tm.upperOutTo))
        if (from >= len) continue
        val p = Path()
        fun pt(dist: Float): Offset = if (dist <= straight) Offset(capX + s * dist, capY) else {
            val ang = (dist - straight) / turnR
            val ox = endX - s * turnR; val oy = capY + turnR
            Offset(ox + s * turnR * sin(ang), oy - turnR * cos(ang))
        }
        val n = 48
        for (k in 0..n) { val q = pt(from + (len - from) * k / n); if (k == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
        val grad = Brush.horizontalGradient(listOf(IgnK.Orange, IgnK.Yellow), startX = w / 2f, endX = endX)
        if (!lite) drawPath(p, grad, alpha = 0.07f * upperA, style = Stroke(9f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(p, grad, alpha = 0.22f * upperA, style = Stroke(4f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(p, grad, alpha = upperA, style = Stroke(1.6f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        if (u > 0f && u < 1f) spark(pt(len), 14f * d)
    }
}
