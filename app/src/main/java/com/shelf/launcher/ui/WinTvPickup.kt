package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * 529s (owner P3, physical pickups — TV room): the case you picked leaves the games beside the set, travels to the set and
 * goes into it — it flattens into the bright line an old TV switches on from, and the picture opens out of that line (the
 * set's own switch-on, kept). Closing runs it back: the picture shrinks into the line, the case comes out of it and goes
 * back to its place among the games. One case: the home's own copy is hidden for the whole trip (GameWindow's tvFlight,
 * [caseHomeHidden]) and the flying one is that very picture ([GameOrigin.layer]).
 */
internal const val TV_FLIGHT_OPEN_MS = 760
internal const val TV_FLIGHT_CLOSE_MS = 620
/** Reveal where the case has gone into the line (it is not drawn from here on). */
internal const val TV_CASE_IN = 0.62f

private fun stage(k: Float, a: Float, b: Float) = ((k - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(t: Float) = t * t * (3f - 2f * t)

/** How far the set has switched on at reveal [k] (its second half, eased out). Pure. */
internal fun tvSetOn(k: Float): Float { val t = stage(k, 0.5f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t) }

/** The case's transform at reveal [k]: (dx, dy, scaleX, scaleY, alpha) from its place [from] (window); null once it is in. Pure. */
internal fun tvCaseFlight(from: Rect, tv: Rect, k: Float): FloatArray? {
    if (k >= TV_CASE_IN) return null
    val p = smooth(stage(k, 0f, 0.5f))
    val s1 = minOf(1f, tv.height * 0.5f / from.height.coerceAtLeast(1f))
    val dist = kotlin.math.hypot(tv.center.x - from.center.x, tv.center.y - from.center.y)
    val cx = from.center.x + (tv.center.x - from.center.x) * p
    val cy = from.center.y + (tv.center.y - from.center.y) * p - kotlin.math.sin(p * Math.PI).toFloat() * dist * 0.12f
    val s = 1f + (s1 - 1f) * p
    // Into the line: flattening to its height, widening a little toward its width, fading out as the line lights.
    val q = smooth(stage(k, 0.45f, TV_CASE_IN))
    val lineW = (tv.width * 0.6f) / (from.width * s1).coerceAtLeast(1f)
    val sx = s * (1f + (minOf(lineW, 2.2f) - 1f) * q)
    val sy = s * (1f - 0.96f * q)
    val a = 1f - stage(q, 0.6f, 1f)
    return floatArrayOf(cx - from.center.x, cy - from.center.y, sx, sy, a)
}

@Composable
internal fun TvCaseFlyer(from: Rect, tv: Rect, snap: GraphicsLayer, reveal: () -> Float) {
    val dens = LocalDensity.current
    Box(
        Modifier.offset { IntOffset(from.left.roundToInt(), from.top.roundToInt()) }
            .size(with(dens) { from.width.toDp() }, with(dens) { from.height.toDp() })
            .onGloballyPositioned { if (!PickupStage.drawn) PickupStage.drawn = true }
            .graphicsLayer {
                val f = tvCaseFlight(from, tv, reveal())
                if (f == null) { alpha = 0f; return@graphicsLayer }
                transformOrigin = TransformOrigin.Center
                translationX = f[0]; translationY = f[1]; scaleX = f[2]; scaleY = f[3]; alpha = f[4]
            }
            .drawBehind {
                if (snap.isReleased) return@drawBehind
                val sw = snap.size.width.toFloat(); val sh = snap.size.height.toFloat()
                if (sw > 0f && sh > 0f) scale(size.width / sw, size.height / sh, pivot = Offset.Zero) { drawLayer(snap) }
            }
    )
}
