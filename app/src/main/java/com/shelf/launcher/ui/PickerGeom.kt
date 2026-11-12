package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.hypot
import kotlin.math.max

/**
 * Sprint 527 (owner: "one consistent geometric centre" for each profile's picture, its circle, the rings, the halo and the
 * wire): the pure geometry the bubbles and the wire share. A bubble is a column - picture (padded by [pad]) over its name -
 * so its middle is lower than the picture's: every grow / shrink pivots on the picture's centre ([pivot]), and the
 * picture's centre is measured with the layout's own whole-pixel rounding ([centreY]).
 */
internal object PickerGeom {
    /** The picture's centre from the column's top (px): its padding and its size as the layout rounds them. Pure. */
    fun centreY(padPx: Int, sizePx: Int): Float = padPx + sizePx / 2f

    /** The transform origin that keeps the picture's centre still while the column scales ([columnH] px tall). Pure. */
    fun pivot(padPx: Int, sizePx: Int, columnH: Float): TransformOrigin =
        TransformOrigin(0.5f, if (columnH > 0f) (centreY(padPx, sizePx) / columnH).coerceIn(0f, 1f) else 0.5f)

    /**
     * Where a point [p] of a column lands after a scale [k] round [origin] (both in the column's px) and a [dy] lift. Pure:
     * what graphicsLayer does - for the tests (the old column-centre pivot moved the picture by (k - 1) x the distance).
     */
    fun scaled(p: Offset, k: Float, origin: Offset, dy: Float): Offset = Offset(origin.x + (p.x - origin.x) * k, origin.y + (p.y - origin.y) * k + dy)

    /** Concentric within [tolPx]? (the QA assertion's rule) Pure. */
    fun concentric(a: Offset, b: Offset, tolPx: Float): Boolean = hypot(a.x - b.x, a.y - b.y) <= tolPx

    /** The QA tolerance: 0.75 dp, at least one pixel. Pure. */
    fun tolerancePx(density: Float): Float = max(1f, 0.75f * density)
}

/**
 * QA only (`--ez voltcap_test_geom true`, the isolated QA app): each profile's picture, image, rings and halo record their
 * layout node; [PickerGeomOverlay] draws every centre (and the wire's ring centre and the scale pivot) and logs the largest
 * distance from the wire's centre, so alignment is measured, not judged by eye. Nothing is recorded when it is off.
 */
internal object PickerGeomQa {
    const val AVATAR = 0; const val IMAGE = 1; const val RING = 2; const val HALO = 3
    val on = mutableStateOf(false)
    val nodes = java.util.concurrent.ConcurrentHashMap<Int, LayoutCoordinates>()
    fun key(i: Int, part: Int) = i * 8 + part
}

/** Records this node for [PickerGeomQa] (QA only; nothing when off or [i] < 0). */
internal fun Modifier.qaGeom(i: Int, part: Int): Modifier =
    if (i < 0 || !PickerGeomQa.on.value) this else this.onPlaced { PickerGeomQa.nodes[PickerGeomQa.key(i, part)] = it }

/**
 * QA only: draws, in the picker's frame, each profile's wire / ring centre (green cross: the row's centre raised by the lift),
 * the picture's centre (magenta), the image's bounds (yellow) and centre, the halo's centre (cyan ring) and the rings' own
 * centre; logs per profile the largest distance to the wire centre ("VoltcapGeom") about 4 times a second and when it
 * settles. [tick] is read in the drawing so it redraws with the animations.
 */
@Composable
internal fun PickerGeomOverlay(
    frame: Array<LayoutCoordinates?>, centers: SnapshotStateList<Offset>, count: Int, cursor: Int, rise: Float, wire: Boolean,
    liftFor: (Int) -> Float, tick: () -> Float, leaving: Boolean
) {
    val density = LocalDensity.current.density
    val tol = PickerGeom.tolerancePx(density)
    fun measure(i: Int): Map<String, Offset> {
        val f = frame[0]?.takeIf { it.isAttached } ?: return emptyMap()
        val out = LinkedHashMap<String, Offset>()
        val c = centers.getOrNull(i) ?: return out
        if (!c.x.isNaN()) out["wire"] = Offset(c.x, c.y - rise * liftFor(i))
        fun centreOf(part: Int): Offset? = PickerGeomQa.nodes[PickerGeomQa.key(i, part)]?.takeIf { it.isAttached }?.let { n ->
            val r: Rect = f.localBoundingBoxOf(n, clipBounds = false); r.center
        }
        centreOf(PickerGeomQa.AVATAR)?.let { out["avatar"] = it }
        centreOf(PickerGeomQa.IMAGE)?.let { out["image"] = it }
        centreOf(PickerGeomQa.RING)?.let { out["ring"] = it }
        centreOf(PickerGeomQa.HALO)?.let { out["halo"] = it }
        return out
    }
    LaunchedEffect(count, cursor, leaving) {
        while (true) {
            var worst = 0f
            val sb = StringBuilder()
            for (i in 0 until count) {
                val m = measure(i); if (m.isEmpty()) continue
                val ref = m["wire"].takeIf { wire } ?: m["avatar"] ?: continue
                var d = 0f; for ((_, v) in m) d = max(d, hypot(v.x - ref.x, v.y - ref.y))
                if (!leaving) worst = max(worst, d)
                sb.append(" | i=$i on=${i == cursor} lift=${"%.2f".format(liftFor(i))} ")
                m.forEach { (k, v) -> sb.append("$k=(${"%.1f".format(v.x)},${"%.1f".format(v.y)}) ") }
                sb.append("d=${"%.2f".format(d)}")
            }
            android.util.Log.i("VoltcapGeom", "worst=${"%.2f".format(worst)} tol=${"%.2f".format(tol)} pass=${worst <= tol} leaving=$leaving$sb")
            kotlinx.coroutines.delay(250)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val t = tick()
        val f = frame[0]?.takeIf { it.isAttached } ?: return@Canvas
        for (i in 0 until count) {
            val m = measure(i)
            val ref = m["wire"].takeIf { wire } ?: m["avatar"] ?: continue
            var d = 0f; for ((_, v) in m) d = max(d, hypot(v.x - ref.x, v.y - ref.y))
            val bad = d > tol
            fun cross(o: Offset, c: Color, r: Float) {
                drawLine(c, Offset(o.x - r, o.y), Offset(o.x + r, o.y), 1.5f); drawLine(c, Offset(o.x, o.y - r), Offset(o.x, o.y + r), 1.5f)
            }
            // (the scale pivot: the picture's centre at rest, where the column grows / shrinks round)
            centers.getOrNull(i)?.takeIf { !it.x.isNaN() }?.let { drawCircle(Color.White, 3f, it) }
            m["wire"]?.let { cross(it, Color.Green, 22f) }
            m["avatar"]?.let { cross(it, Color.Magenta, 14f) }
            m["ring"]?.let { drawCircle(Color(0xFFFF8800), 4f, it) }
            m["halo"]?.let { drawCircle(Color.Cyan, 7f, it, style = Stroke(1.5f)) }
            PickerGeomQa.nodes[PickerGeomQa.key(i, PickerGeomQa.IMAGE)]?.takeIf { it.isAttached }?.let { n ->
                val r = f.localBoundingBoxOf(n, clipBounds = false)
                drawRect(Color.Yellow, r.topLeft, r.size, style = Stroke(1.5f))
                cross(r.center, Color.Yellow, 8f)
            }
            if (bad) drawCircle(Color.Red, 30f, ref, style = Stroke(3f))
        }
    }
}
