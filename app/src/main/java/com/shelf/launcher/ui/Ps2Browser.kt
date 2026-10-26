package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Ps2Icons
import com.shelf.launcher.data.SaveCapsules
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * (Update 239, #112: Save Vault's optional PS2 browser style — a PS2 save drawn as the console's memory-card browser draws
 * it: the save's own 3D icon (from its icon.sys + icon file, Ps2Icons) turning slowly on the save's background colours,
 * its two-line title and its size in KB. A save without a readable icon gets a plainly generic memory-card symbol marked
 * "No save icon" — never passed off as the save's own. Only the slot's picture changes: focus, touch, the lift / bob and
 * the flight into the details window are the slot's as always.)
 */

/** (Update 239, #112: one PS2 save of a capsule, for its tile.) */
internal data class Ps2SaveRef(val id: String, val title: String, val sizeBytes: Long)

/** (Update 239, #112: a capsule's PS2 memory-card saves, newest first — the tile shows the first one with an icon.) */
internal fun ps2RefsOf(capsule: SaveCapsules.Capsule?): List<Ps2SaveRef> =
    capsule?.saves.orEmpty().filter { it.kind == SaveCapsules.Kind.MEMORY_CARD }.sortedByDescending { it.modified }
        .map { Ps2SaveRef(Ps2Icons.idOf(it.where, it.sizeBytes, it.modified), it.title, it.sizeBytes) }

/**
 * (Update 239, #112: a PS2 icon ready to draw — shape 0 as textured triangles (android.graphics.Canvas.drawVertices with
 * the icon's texture as a BitmapShader), turned about the Y axis with perspective and drawn back to front (no depth
 * buffer: the triangles are sorted each frame). One per icon; drawn on the main thread only.)
 */
internal class Ps2IconMesh(icon: Ps2Icons.Icon) {
    private val n = icon.vertexCount - icon.vertexCount % 3
    private val tris = n / 3
    private val pos = icon.positions
    private val texture: Bitmap? = icon.texture?.let { px ->
        runCatching { Bitmap.createBitmap(px, 128, 128, Bitmap.Config.ARGB_8888) }.getOrNull()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        texture?.let { shader = BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }
    /** The texture's pixel coordinates per vertex (the shader's space). */
    private val texs = FloatArray(n * 2).also { t -> for (i in 0 until n * 2) t[i] = (icon.uvs[i] * 128f).coerceIn(-128f, 256f) }
    /** Untextured icons: their vertex colours. */
    private val colors: IntArray? = if (texture == null) icon.colors.copyOf(n) else null
    private val verts = FloatArray(n * 2)
    private val rz = FloatArray(n)
    private val depth = FloatArray(tris)
    private val order = IntArray(tris) { it }
    private val indices = ShortArray(tris * 3)
    private var sorted = false
    private val radius: Float
    private val yMid: Float
    private val yHalf: Float
    /** (Update 241, #49: the icon's radius about its middle in every direction — the free viewer fits it by this.) */
    private val radius3: Float

    init {
        var r = 0f
        var y0 = Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        for (i in 0 until n) {
            val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
            r = max(r, sqrt(x * x + z * z))
            y0 = min(y0, y); y1 = max(y1, y)
        }
        radius = r.coerceAtLeast(0.01f)
        yMid = if (n > 0) (y0 + y1) / 2f else 0f
        yHalf = if (n > 0) ((y1 - y0) / 2f).coerceAtLeast(0.01f) else 1f
        var r3 = 0f
        for (i in 0 until n) {
            val x = pos[i * 3]; val y = pos[i * 3 + 1] - yMid; val z = pos[i * 3 + 2]
            r3 = max(r3, sqrt(x * x + y * y + z * z))
        }
        radius3 = r3.coerceAtLeast(0.01f)
    }

    val ok: Boolean get() = tris > 0

    /** Draws into [c] at its origin, filling [w] × [h], turned [angleDeg] about the vertical axis. */
    fun draw(c: android.graphics.Canvas, w: Float, h: Float, angleDeg: Float) {
        if (tris == 0 || w <= 1f || h <= 1f) return
        val a = Math.toRadians(angleDeg.toDouble())
        val cs = cos(a).toFloat(); val sn = sin(a).toFloat()
        // The camera sits 3.5 radii in front; 1.4 = how much nearer parts grow at most.
        val d = radius * 3.5f
        val k = min(w * 0.42f / (radius * 1.4f), h * 0.42f / (yHalf * 1.4f))
        val cx = w / 2f; val cy = h / 2f
        for (i in 0 until n) {
            val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
            val xr = x * cs - z * sn
            val zr = x * sn + z * cs
            val f = d / (d + zr).coerceAtLeast(d * 0.2f)
            verts[i * 2] = cx + xr * k * f
            // y points down on the PS2 as on the screen.
            verts[i * 2 + 1] = cy + (y - yMid) * k * f
            rz[i] = zr
        }
        finish(c)
    }

    /**
     * (Update 241, #49: the Save Vault Inspect viewer's free turn — [yawDeg] about the upright, then [pitchDeg] about the
     * level (positive: its top comes towards you), fitted by its radius in every direction so it never leaves [w] × [h]
     * however it's turned. The vault's own tiles keep [draw].)
     */
    fun drawFree(c: android.graphics.Canvas, w: Float, h: Float, yawDeg: Float, pitchDeg: Float) {
        if (tris == 0 || w <= 1f || h <= 1f) return
        val a = Math.toRadians(yawDeg.toDouble()); val b = Math.toRadians(pitchDeg.toDouble())
        val cs = cos(a).toFloat(); val sn = sin(a).toFloat()
        val cp = cos(b).toFloat(); val sp = sin(b).toFloat()
        val d = radius3 * 3.5f
        val k = min(w, h) * 0.42f / (radius3 * 1.4f)
        val cx = w / 2f; val cy = h / 2f
        for (i in 0 until n) {
            val x = pos[i * 3]; val y = pos[i * 3 + 1] - yMid; val z = pos[i * 3 + 2]
            val xr = x * cs - z * sn
            val z1 = x * sn + z * cs
            // y points down: its top (−y) comes nearer (−z) as the pitch grows.
            val yr = y * cp - z1 * sp
            val zr = y * sp + z1 * cp
            val f = d / (d + zr).coerceAtLeast(d * 0.2f)
            verts[i * 2] = cx + xr * k * f
            verts[i * 2 + 1] = cy + yr * k * f
            rz[i] = zr
        }
        finish(c)
    }

    /** Back to front, then drawn (the projected [verts] / depths [rz] are set). */
    private fun finish(c: android.graphics.Canvas) {
        for (t in 0 until tris) depth[t] = rz[t * 3] + rz[t * 3 + 1] + rz[t * 3 + 2]
        sortBackToFront()
        for (t in 0 until tris) {
            val o = order[t] * 3
            indices[t * 3] = o.toShort(); indices[t * 3 + 1] = (o + 1).toShort(); indices[t * 3 + 2] = (o + 2).toShort()
        }
        c.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES, n * 2, verts, 0,
            if (texture != null) texs else null, 0, colors, 0, indices, 0, tris * 3, paint
        )
    }

    /** Farthest first. Frame to frame the order barely changes: insertion sort, a full sort the first time (or when far off). */
    private fun sortBackToFront() {
        if (sorted) {
            var moves = 0L
            val cap = tris.toLong() * 24
            var i = 1
            while (i < tris && moves <= cap) {
                val v = order[i]; val dv = depth[v]
                var j = i - 1
                while (j >= 0 && depth[order[j]] < dv) { order[j + 1] = order[j]; j--; moves++ }
                order[j + 1] = v
                i++
            }
            if (moves <= cap) return
        }
        val s = (0 until tris).sortedByDescending { depth[it] }
        for (t in 0 until tris) order[t] = s[t]
        sorted = true
    }
}

/** (Update 239, #112: an offscreen picture for hardware canvases that can't draw vertices — before Android 10.) */
internal class SoftBuffer {
    var bitmap: Bitmap? = null
    var canvas: android.graphics.Canvas? = null
    fun ensure(w: Int, h: Int): Bitmap? {
        val ww = w.coerceIn(1, 512); val hh = h.coerceIn(1, 512)
        val b = bitmap
        if (b != null && b.width == ww && b.height == hh) return b
        return runCatching { Bitmap.createBitmap(ww, hh, Bitmap.Config.ARGB_8888) }.getOrNull()?.also { nb ->
            bitmap = nb; canvas = android.graphics.Canvas(nb)
        }
    }
}

/**
 * (Update 239, #112: the turning icon. [turning]: it turns (else held at a three-quarter view); [fast]: the picked one's
 * pace. Drawn straight onto the screen's canvas where that can draw vertices (Android 10+, or a software canvas), else
 * through a small offscreen picture.)
 */
@Composable
internal fun Ps2IconView(mesh: Ps2IconMesh, turning: Boolean, fast: Boolean, modifier: Modifier = Modifier) {
    val angleState = if (turning) {
        rememberInfiniteTransition(label = "ps2Icon").animateFloat(
            0f, 360f, infiniteRepeatable(tween(if (fast) 6_000 else 14_000, easing = LinearEasing)), label = "ps2IconTurn"
        )
    } else null
    val buffer = remember { SoftBuffer() }
    Canvas(modifier) {
        // Read only while drawing: the turn redraws, never recomposes.
        val ang = angleState?.value ?: -25f
        drawMesh(mesh, buffer, ang)
    }
}

private fun DrawScope.drawMesh(mesh: Ps2IconMesh, buffer: SoftBuffer, angle: Float) = drawPs2Mesh(mesh, buffer, angle, null)

/**
 * (Update 241, #49: [mesh] filling this scope, turned [angle]° — and, with [pitch] (the Inspect viewer), tilted too and
 * fitted for any turn (Ps2IconMesh.drawFree). Straight onto the canvas where it can draw vertices, else through [buffer].)
 */
internal fun DrawScope.drawPs2Mesh(mesh: Ps2IconMesh, buffer: SoftBuffer, angle: Float, pitch: Float?) {
    val w = size.width; val h = size.height
    drawIntoCanvas { cv ->
        val nc = cv.nativeCanvas
        runCatching {
            if (!nc.isHardwareAccelerated || android.os.Build.VERSION.SDK_INT >= 29) {
                if (pitch != null) mesh.drawFree(nc, w, h, angle, pitch) else mesh.draw(nc, w, h, angle)
            } else {
                val b = buffer.ensure(w.toInt(), h.toInt()) ?: return@runCatching
                val bc = buffer.canvas ?: return@runCatching
                b.eraseColor(0)
                bc.save()
                bc.scale(b.width / w, b.height / h)
                if (pitch != null) mesh.drawFree(bc, w, h, angle, pitch) else mesh.draw(bc, w, h, angle)
                bc.restore()
                nc.drawBitmap(b, null, android.graphics.RectF(0f, 0f, w, h), null)
            }
        }
    }
}

/** (Update 239, #112: the browser's deep-blue space, when a save has no colours of its own.) */
private val SPACE = intArrayOf(0xFF14286E.toInt(), 0xFF0C1A4E.toInt(), 0xFF060C2A.toInt(), 0xFF02040F.toInt())

/** (Update 239, #112: the save's background — icon.sys's four corner colours blended across, over the dark-blue space.) */
@Composable
private fun Ps2Background(sys: Ps2Icons.IconSys?, modifier: Modifier) {
    val stars = remember { List(14) { i -> Triple(((i * 0.618f) % 1f), ((i * 0.377f + 0.13f) % 1f), 0.5f + (i % 3) * 0.35f) } }
    Canvas(modifier) {
        fourCorners(SPACE, 1f)
        stars.forEach { (x, y, s) -> drawCircle(Color.White.copy(alpha = 0.35f), 0.9.dp.toPx() * s, Offset(x * size.width, y * size.height)) }
        if (sys != null) fourCorners(sys.corners, 0.35f + 0.55f * sys.bgAlpha)
    }
}

/** Corner colours (TL, TR, BL, BR) blended across in thin bands. */
private fun DrawScope.fourCorners(c: IntArray, alpha: Float) {
    if (c.size < 4) return
    val tl = Color(c[0]); val tr = Color(c[1]); val bl = Color(c[2]); val br = Color(c[3])
    val bands = 12
    val bh = size.height / bands
    for (i in 0 until bands) {
        val t = (i + 0.5f) / bands
        val l = androidx.compose.ui.graphics.lerp(tl, bl, t).copy(alpha = alpha)
        val r = androidx.compose.ui.graphics.lerp(tr, br, t).copy(alpha = alpha)
        drawRect(Brush.horizontalGradient(listOf(l, r)), Offset(0f, i * bh), Size(size.width, bh + 1f))
    }
}

/** (Update 239, #112: a plain, generic memory-card symbol — Voltcap's own drawing, not the save's icon.) */
@Composable
internal fun GenericCardSymbol(modifier: Modifier) {
    Canvas(modifier) {
        val ch = size.height * 0.78f
        val cw = ch * 0.78f
        val x0 = (size.width - cw) / 2f; val y0 = (size.height - ch) / 2f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF9DA1A8), Color(0xFF6E7279)), y0, y0 + ch),
            Offset(x0, y0), Size(cw, ch), CornerRadius(cw * 0.08f)
        )
        drawRoundRect(Color(0xFF2A2D33), Offset(x0 + cw * 0.14f, y0 + ch * 0.5f), Size(cw * 0.72f, ch * 0.32f), CornerRadius(cw * 0.04f))
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(x0 + cw * 0.5f, y0 + ch * 0.06f); lineTo(x0 + cw * 0.62f, y0 + ch * 0.16f); lineTo(x0 + cw * 0.38f, y0 + ch * 0.16f); close()
        }
        drawPath(tri, Color(0xFF55595F))
    }
}

private class Ps2Pick(val ref: Ps2SaveRef?, val parsed: Ps2Icons.Parsed?, val done: Boolean)

/**
 * (Update 239, #112: a PS2 save's slot picture in the PS2 browser style — the same size, lift, bob and hidden state as the
 * other styles' pictures, so the slot (focus, A / tap, the flight) works exactly as before.)
 */
@Composable
internal fun Ps2SaveTile(
    refs: List<Ps2SaveRef>,
    name: String,
    state: SyncState,
    selected: Boolean,
    lift: Float,
    bob: () -> Float,
    hidden: Boolean,
    onIconPlaced: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    val context = LocalContext.current
    val version = Ps2Icons.version.intValue
    val initial = remember(refs) {
        refs.firstNotNullOfOrNull { r -> Ps2Icons.cached(r.id)?.takeIf { it.icon != null }?.let { Ps2Pick(r, it, true) } }
            ?: Ps2Pick(refs.firstOrNull(), null, false)
    }
    val pick by produceState(initial, refs, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var first: Ps2Pick? = null
            for (r in refs.take(8)) {
                val p = runCatching { Ps2Icons.load(context, r.id) }.getOrNull()
                if (p?.icon != null) return@withContext Ps2Pick(r, p, true)
                if (first == null) first = Ps2Pick(r, p, true)
            }
            first ?: Ps2Pick(null, null, true)
        }
    }
    val icon = pick.parsed?.icon
    val mesh = remember(icon) { icon?.let { runCatching { Ps2IconMesh(it) }.getOrNull() }?.takeIf { it.ok } }
    val sys = pick.parsed?.sys
    val ref = pick.ref
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned { onIconPlaced(it.boundsInRoot()) }
            .graphicsLayer {
                val b = bob()
                translationY = -(3.dp.toPx() * b) - 10.dp.toPx() * lift
                val s = 1f + 0.10f * lift
                scaleX = s; scaleY = s
                alpha = if (hidden) 0.12f else 1f
            }
            .clip(shape)
            .border(1.dp + 1.5.dp * lift, if (selected) accent else Color(0xFF6F8CFF).copy(alpha = 0.35f), shape)
    ) {
        Ps2Background(sys, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (mesh != null) {
                    // The picked one turns; the others too where the vault's "3D turn" motion is on and the canvas can draw
                    // them cheaply (on older Android each turning icon is drawn offscreen, so only the picked one turns).
                    val cheap = android.os.Build.VERSION.SDK_INT >= 29
                    Ps2IconView(mesh, turning = selected || (cheap && Features.saveTurn.state.value), fast = selected, modifier = Modifier.fillMaxSize().padding(4.dp))
                } else if (pick.done) GenericCardSymbol(Modifier.fillMaxSize(0.7f))
            }
            // The browser's caption: the save's own title on two lines, its size.
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF02040F).copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val l1 = sys?.line1?.takeIf { it.isNotBlank() } ?: ref?.title?.takeIf { it.isNotBlank() } ?: name
                val l2 = sys?.line2?.takeIf { it.isNotBlank() }
                Text(l1, color = Color.White, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (l2 != null) Text(l2, color = Color.White.copy(alpha = 0.85f), fontSize = 8.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ref?.let { Text("${(it.sizeBytes + 1023) / 1024} KB", color = Color(0xFFB9C6FF), fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1) }
            }
        }
        // What the picture is, said plainly: the save's own icon, or a generic symbol.
        if (pick.done) {
            Text(
                if (mesh != null) "PS2 save icon" else "No save icon",
                color = if (mesh != null) Color(0xFFCFE0FF) else Color(0xFFFFD6A0),
                fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
        SyncBadge(state, 16.dp, Modifier.align(Alignment.TopEnd).padding(3.dp))
    }
}
