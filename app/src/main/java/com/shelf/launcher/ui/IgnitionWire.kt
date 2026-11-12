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

/**
 * The continuation's times (ms) on the intro's own clock; it opens inside the final shine.
 * Sprint 527 (owner: "the selected player should be the visual anchor"): the capsule's two ends run straight down into the
 * profile that played last ([open] → [arrive]), trace its ring ([arrive] → [ringTo]) while it emerges, then the light spreads
 * outward along the row from it ([spreadFrom] → [spreadTo]) and each neighbour appears as it passes, nearest first; the ring
 * gets its pulse ([pulseFrom] → [pulseTo]) and everything has finished by [settled] (nothing is left half-faded).
 */
internal class IgnitionTiming(
    val open: Float, val arrive: Float, val ringTo: Float, val liftTo: Float, val spreadFrom: Float, val spreadTo: Float,
    val recedeFrom: Float, val recedeTo: Float, val tailFrom: Float, val tailTo: Float,
    val titleFrom: Float, val titleTo: Float, val hintsFrom: Float, val hintsTo: Float,
    val pulseFrom: Float, val pulseTo: Float, val wireOutFrom: Float, val settled: Float
)

internal val IG_FULL = IgnitionTiming(
    open = 2080f, arrive = 2600f, ringTo = 2840f, liftTo = 3080f, spreadFrom = 2760f, spreadTo = 3300f,
    recedeFrom = 2240f, recedeTo = 2860f, tailFrom = 2440f, tailTo = 2840f,
    titleFrom = 2760f, titleTo = 3160f, hintsFrom = 3260f, hintsTo = 3560f,
    pulseFrom = 3200f, pulseTo = 3660f, wireOutFrom = 3300f, settled = 3700f
)
internal val IG_SHORT = IgnitionTiming(
    open = 430f, arrive = 600f, ringTo = 700f, liftTo = 800f, spreadFrom = 660f, spreadTo = 900f,
    recedeFrom = 470f, recedeTo = 760f, tailFrom = 540f, tailTo = 700f,
    titleFrom = 700f, titleTo = 900f, hintsFrom = 960f, hintsTo = 1120f,
    pulseFrom = 880f, pulseTo = 1160f, wireOutFrom = 900f, settled = 1180f
)

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
         * Sprint 527: when the profile centred at [x] appears. The focused one ([x] == [focusX]) emerges as the light reaches it
         * ([IgnitionTiming.arrive]); the others as the light spreading outward from the focused ring's edge passes their near
         * edge - nearest first, on both sides at the same speed, [reach] px (the visible run) in [spreadFrom] → [spreadTo]
         * (ease-out). [focusR] the focused ring's radius, [rho] a resting ring's. Pure.
         */
        fun appearAt(x: Float, focusX: Float, focusR: Float, rho: Float, reach: Float, tm: IgnitionTiming): Float {
            if (abs(x - focusX) < 0.5f) return tm.arrive
            val d = (abs(x - focusX) - focusR - rho).coerceAtLeast(0f)
            val f = (d / reach.coerceAtLeast(1f)).coerceIn(0f, 1f)
            // (the inverse of the spread's ease-out 1 - (1 - u)^3: the moment its tip is at d)
            val u = 1f - Math.cbrt((1.0 - f)).toFloat()
            return tm.spreadFrom + (tm.spreadTo - tm.spreadFrom) * u
        }

        /** The wire's ends: just past the outer rings (it fades out there - no line runs on to the screen edges). Pure. */
        fun ends(firstX: Float, lastX: Float, rho: Float, margin: Float): FloatArray = floatArrayOf(firstX - rho - margin, lastX + rho + margin)

        /**
         * How far the spread runs while it is seen: the longer of the two visible runs from the focused ring's edges
         * ([focusL] / [focusR]) to the wire's ends, clipped to the screen ([width]); rows that scroll go on past it unseen. Pure.
         */
        fun reach(focusL: Float, focusR: Float, leftEnd: Float, rightEnd: Float, width: Float): Float =
            max(focusL - max(leftEnd, 0f), min(rightEnd, width) - focusR).coerceAtLeast(1f)
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
        val conv = if (tm != null) IgnK.eOut(IgnK.ph(t, tm.arrive, tm.liftTo)) else 1f
        val pos = inp.focusPos()
        val lifts = FloatArray(cs.size) { IgnK.liftAt(pos, it) * conv }
        val cy = cs[focus].y // (the row's centre line at rest; a lifted ring is raised by WireGeo)
        // (Sprint 527: the wire ends just past the outer rings and fades there - no line runs on to the screen edges.)
        val ends = WireGeo.ends(cs.first().x, cs.last().x, inp.rho, 30f * d)
        val leftEnd = ends[0]; val rightEnd = ends[1]
        val geo = WireGeo.build(FloatArray(cs.size) { cs[it].x }, lifts, cy, inp.rho, inp.rise, leftEnd, rightEnd, max(1f, 1.2f * d))
        val last = geo.size - 1
        val wireVis = if (inp.keep) 1f else if (tm != null) 1f - IgnK.eInOut(IgnK.ph(t, tm.wireOutFrom, tm.settled)) else 0f
        val rrF = inp.rho * (1f + 0.16f * lifts[focus])
        // ---- the capsule's two ends running down into the focused ring (start-up only)
        if (tm != null) drawDescent(t, tm, Offset(cs[focus].x - rrF, cy - inp.rise * lifts[focus]), Offset(cs[focus].x + rrF, cy - inp.rise * lifts[focus]), cy, inp.lite)
        if (wireVis <= 0.003f) return@Canvas
        val iFL = geo.idx(cs[focus].x - rrF); val iFR = geo.idx(cs[focus].x + rrF)
        val mid = geo.idx(cs[focus].x)
        // The focused ring traced from both sides as the light arrives, then the light spreading outward along the row.
        val cc = if (tm != null) IgnK.eOut(IgnK.ph(t, tm.arrive, tm.ringTo)) else 1f
        val spreadU = if (tm != null) IgnK.eOut(IgnK.ph(t, tm.spreadFrom, tm.spreadTo)) else 1f
        val reach = WireGeo.reach(geo.x(iFL), geo.x(iFR), leftEnd, rightEnd, size.width)
        val leftTo = if (spreadU >= 1f) 0 else geo.idx(geo.x(iFL) - reach * spreadU)
        val rightTo = if (spreadU >= 1f) last else geo.idx(geo.x(iFR) + reach * spreadU)
        val drain = IgnK.eInOut(IgnK.ph(inp.exit(), 0f, 0.9f))
        val l0 = max(leftTo, (iFL * drain).roundToInt()); val r0 = min(rightTo, (last - (last - iFR) * drain).roundToInt())
        val ranges = ArrayList<IntArray>(4)
        if (spreadU > 0f) { ranges += intArrayOf(l0, iFL); ranges += intArrayOf(iFR, r0) }
        if (cc > 0f) { ranges += intArrayOf(iFL, (iFL + (mid - iFL) * cc).roundToInt()); ranges += intArrayOf((iFR - (iFR - mid) * cc).roundToInt(), iFR) }
        val a = wireVis
        val fadeK = (44f * d / (rightEnd - leftEnd).coerceAtLeast(1f)).coerceIn(0.02f, 0.3f)
        val grad = Brush.horizontalGradient(
            0f to IgnK.Orange.copy(alpha = 0f), fadeK to IgnK.Orange, 0.5f to IgnK.Yellow, 1f - fadeK to IgnK.Orange, 1f to IgnK.Orange.copy(alpha = 0f),
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
        // (Sprint 527: the focused ring is drawn bright from the moment the light reaches it, before it lifts.)
        val lit = FloatArray(cs.size) { if (it == focus && tm != null) max(lifts[it], 0.6f * cc) else lifts[it] }
        val rest = paths[1]; build(rest) { it >= 0 && lit[it] < 0.02f }
        drawPath(rest, grad, alpha = 0.30f * a, style = Stroke(1f * d), blendMode = BlendMode.Plus)
        for (i in cs.indices) {
            val lf = lit[i]; if (lf < 0.02f) continue
            val p = paths[2]; build(p) { it == i }
            val col = lerp(IgnK.Yellow, inp.accents.getOrElse(i) { IgnK.Yellow }, 0.7f)
            if (!inp.lite) drawPath(p, col.copy(alpha = 0.16f * lf * a), style = Stroke(6f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawPath(p, lerp(Color(0xFFFFECBE), col, 0.6f).copy(alpha = (0.30f + 0.65f * lf) * a), style = Stroke((1f + lf) * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        }
        // Sparks: the ring's tracing heads, the spread's two tips, the pulse, select drain heads, D-pad impulses.
        if (tm != null && cc > 0f && cc < 1f) {
            val kl = (iFL + (mid - iFL) * cc).roundToInt().coerceIn(0, last); val kr = (iFR - (iFR - mid) * cc).roundToInt().coerceIn(0, last)
            spark(Offset(geo.x(kl), geo.top[kl]), 11f * d); spark(Offset(geo.x(kr), geo.top[kr]), 11f * d)
            spark(Offset(geo.x(kl), geo.bottom[kl]), 9f * d); spark(Offset(geo.x(kr), geo.bottom[kr]), 9f * d)
        }
        if (tm != null && spreadU > 0f && spreadU < 1f) {
            val fadeTip = 1f - IgnK.ph(spreadU, 0.8f, 1f)
            if (leftTo > 0) spark(Offset(geo.x(leftTo), geo.top[leftTo]), 12f * d, fadeTip)
            if (rightTo < last) spark(Offset(geo.x(rightTo), geo.top[rightTo]), 12f * d, fadeTip)
        }
        if (tm != null) {
            val u = IgnK.ph(t, tm.pulseFrom, tm.pulseTo)
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
 * Sprint 527: the capsule's outline opening at both ends (same capsule geometry as drawIntro: r = min(0.2 w, 0.26 h) at
 * (w / 2, 0.43 h)) and each end running in one smooth curve down into the focused ring - out of the capsule's end, then
 * arriving level with the ring at its left ([toL]) / right ([toR]) edge - so the light goes straight to the profile that played
 * last. Its tail lets go as the capsule recedes ([IgnitionTiming.tailFrom] → [IgnitionTiming.tailTo]): the line drains into
 * the ring and nothing is left hanging at the screen edges.
 */
private fun DrawScope.drawDescent(t: Float, tm: IgnitionTiming, toL: Offset, toR: Offset, rowY: Float, lite: Boolean) {
    if (t < tm.open || t >= tm.tailTo) return
    val w = size.width; val h = size.height; val d = density
    val r = min(0.2f * w, 0.26f * h)
    val rec = IgnK.eInOut(IgnK.ph(t, tm.recedeFrom, tm.recedeTo))
    val capY = h * 0.43f - rec * r * 0.35f
    val u = IgnK.eInOut(IgnK.ph(t, tm.open, tm.arrive))
    val tail = IgnK.eInOut(IgnK.ph(t, tm.tailFrom, tm.tailTo))
    val dy = abs(rowY - capY)
    for (s in intArrayOf(-1, 1)) {
        val p0 = Offset(w / 2f + s * r * (1f - 0.32f * rec), capY)
        val p3 = if (s < 0) toL else toR
        val a = max(36f * d, 0.55f * dy); val b = max(36f * d, 0.75f * dy)
        val p1 = Offset(p0.x + s * a, p0.y)
        val p2 = Offset(p3.x + s * b, p3.y)
        val pts = descentPoints(p0, p1, p2, p3, 40)
        val lens = FloatArray(pts.size); for (k in 1 until pts.size) lens[k] = lens[k - 1] + (pts[k] - pts[k - 1]).getDistance()
        val total = lens.last()
        val to = total * u; val from = total * tail
        if (from >= to) continue
        val path = Path()
        var started = false
        fun at(dist: Float): Offset { var k = 1; while (k < lens.size - 1 && lens[k] < dist) k++; val f = ((dist - lens[k - 1]) / (lens[k] - lens[k - 1]).coerceAtLeast(1e-3f)).coerceIn(0f, 1f); return pts[k - 1] + (pts[k] - pts[k - 1]) * f }
        val head = at(to)
        path.moveTo(at(from).x, at(from).y); started = true
        for (k in pts.indices) if (lens[k] > from && lens[k] < to) path.lineTo(pts[k].x, pts[k].y)
        if (started) path.lineTo(head.x, head.y)
        val grad = Brush.linearGradient(listOf(IgnK.Orange, IgnK.Yellow), start = p0, end = p3)
        if (!lite) drawPath(path, grad, alpha = 0.07f, style = Stroke(9f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, grad, alpha = 0.22f, style = Stroke(4f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, grad, style = Stroke(1.6f * d, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        if (u > 0f && u < 1f) spark(head, 14f * d)
    }
}

/** A cubic curve sampled at [n] + 1 points. Pure. */
internal fun descentPoints(p0: Offset, p1: Offset, p2: Offset, p3: Offset, n: Int): Array<Offset> = Array(n + 1) { k ->
    val q = k / n.toFloat(); val m = 1f - q
    Offset(m * m * m * p0.x + 3f * m * m * q * p1.x + 3f * m * q * q * p2.x + q * q * q * p3.x, m * m * m * p0.y + 3f * m * m * q * p1.y + 3f * m * q * q * p2.y + q * q * q * p3.y)
}
