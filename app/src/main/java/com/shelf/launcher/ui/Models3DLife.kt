package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Packs
import kotlin.math.roundToInt

/*
 * Update 151: the 3D models with more depth and life ("they just look flat"), all cheap:
 *  - a soft contact shadow under every piece, where it stands;
 *  - a faint reflection on the floor under it (Settings › Game look › 3D models › Floor reflection; off by default) —
 *    made once per picture, away from the screen's thread, then one more picture drawn;
 *  - a light sheen sweeping once across the piece you're on when it gets focus;
 *  - the piece you're on turning slowly to and fro between the pack's pre-rendered angles (20° apart), crossfading the
 *    two nearest pictures so the turn is smooth (3D models › Turn the one you're on). It stops while the home screen
 *    rests (HomeIdle), with Lite effects, with animations off and while the screen is covered.
 * Everything is drawn in one draw call of a plain Spacer: nothing recomposes while it turns, except when the pair of
 * pictures changes.
 */

/** How far the piece you're on turns each way, and how long one turn there and back takes. */
private const val TURN_AMP = 20f
private const val TURN_S = 9.0

/** The pack's step between angles. */
private const val ANGLE_STEP = 20

/** How far the floor reflection reaches under a piece (a share of its height). */
private const val REFLECT_K = 0.32f

/** The sheen's place while it isn't sweeping. */
private const val SHEEN_OFF = 2f

/** Where the piece turning on the clock is right now (degrees). */
private fun turnYaw(base: Float, nowNs: Long, startNs: Long): Float {
    // (Update 151: never before its start — the shared clock holds an older frame until the first new one comes.)
    val t = ((nowNs - startNs) / 1_000_000_000.0).coerceAtLeast(0.0)
    return (base + TURN_AMP * kotlin.math.sin(t * 2.0 * Math.PI / TURN_S).toFloat()).coerceIn(-60f, 60f)
}

/** The pack's angle at or just below [yaw] (so it and the next one up hold [yaw] between them). */
private fun lowAngle(yaw: Float): Int =
    (kotlin.math.floor((yaw + 60f) / ANGLE_STEP).toInt() * ANGLE_STEP - 60).coerceIn(-60, 60 - ANGLE_STEP)

/** See the note at the top; called by [Model3DArt] with life on. */
@Composable
internal fun Model3DLive(
    id: String, cover: Any?, yaw: Float, modifier: Modifier, shade: Float, name: String?, alwaysName: Boolean,
    platform: String?, focused: Boolean
) {
    val version = Packs.version.intValue
    val meta = remember(id, version) { Models3D.meta(id) }
    val coverBmp = rememberSoftBitmap(cover, 640)
    val title = if (alwaysName || Features.modelName.state.value) name?.let { gameDisplayName(it) } else null
    val pos = ArtPlace.of(model = true)
    ModelColors.version.intValue
    val tint = Models3D.tintFor(id, platform, name?.let { gameDisplayName(it) })
    val moving = !LocalLiteEffects.current && rememberAnimationsEnabled() && rememberIsActive()
    val turning = meta != null && focused && moving && Features.modelTurn.state.value && !HomeIdle.idle
    val reflect = Features.modelReflect.state.value
    // The turn's clock (frames only while it turns), from the moment it started (so it starts from where it stands).
    val clock = MediaClock.rememberFrameTime(running = turning)
    val start = remember(turning) { System.nanoTime() }
    // Which two pictures the turn is between: recomposes only when that pair changes, not every frame.
    val low by remember(yaw, start) { derivedStateOf { lowAngle(turnYaw(yaw, clock.longValue, start)) } }
    val a0 = if (turning) low else Models3D.nearest(yaw)
    val a1 = if (turning) a0 + ANGLE_STEP else a0
    val pic0 = rememberModelPicture(id, a0, coverBmp, title, pos, tint, version, cover)
    val pic1 = if (turning) rememberModelPicture(id, a1, coverBmp, title, pos, tint, version, cover) else null
    val img0 = remember(pic0) { pic0?.asImageBitmap() }
    val img1 = remember(pic1) { pic1?.asImageBitmap() }
    val refl0 = if (reflect) rememberReflection(pic0) else null
    val refl1 = if (reflect && pic1 != null) rememberReflection(pic1) else null
    val rimg0 = remember(refl0) { refl0?.asImageBitmap() }
    val rimg1 = remember(refl1) { refl1?.asImageBitmap() }
    // The sheen: once across the piece when it becomes the one you're on.
    val sheen = remember { Animatable(SHEEN_OFF) }
    LaunchedEffect(focused, moving) {
        if (focused && moving) {
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
        sheen.snapTo(SHEEN_OFF)
    }
    val dim = (1f - shade).coerceIn(0f, 1f)
    val filter = remember(dim) {
        if (dim >= 0.995f) null
        else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, 1f, 0f
        )))
    }
    val aspect = (meta?.aspect ?: 0.75f).coerceAtLeast(0.05f)
    val glow = if (focused) 1f else 0.8f
    // Update 166 (fluent): a piece standing between two of the pack's angles (a carousel sliding it round, Box flow's
    // stacks) is its nearest picture turned the last few degrees in 3D, so it turns smoothly instead of in 20° steps.
    val rest = if (turning) 0f else Models3D.wrap(yaw - a0).coerceIn(-12f, 12f)
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    Spacer(
        modifier.then(if (rest != 0f) Modifier.graphicsLayer { rotationY = rest; cameraDistance = 14f * dens ; flatTextFor3d() } else Modifier).drawWithCache {
            // The picture fitted in the middle (as ContentScale.Fit would), and the floor it stands on.
            val fw = minOf(size.width, size.height * aspect)
            val fh = fw / aspect
            val left = (size.width - fw) / 2f
            val top = (size.height - fh) / 2f
            val cx = size.width / 2f
            val floorY = top + fh * 0.985f
            val shadowR = fw * 0.54f
            val shadow = Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.5f * dim * glow), 0.55f to Color.Black.copy(alpha = 0.2f * dim * glow), 1f to Color.Transparent,
                center = Offset(cx, floorY), radius = shadowR
            )
            val band = fw * 0.42f
            val sheenBrush = Brush.horizontalGradient(
                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.32f), 1f to Color.Transparent, startX = 0f, endX = band
            )
            val dstOff = IntOffset(left.roundToInt(), top.roundToInt())
            val dstSize = IntSize(fw.roundToInt().coerceAtLeast(1), fh.roundToInt().coerceAtLeast(1))
            val reflOff = IntOffset(dstOff.x, (top + fh * 0.985f).roundToInt())
            val reflSize = IntSize(dstSize.width, (fh * REFLECT_K).roundToInt().coerceAtLeast(1))
            val bounds = Rect(left, top, left + fw, top + fh)
            val layerPaint = Paint()
            onDrawBehind {
                // The contact shadow: a soft pool squashed flat on the floor under its foot (once the piece is there).
                if (img0 != null) withTransform({ scale(1f, 0.14f, pivot = Offset(cx, floorY)) }) {
                    drawCircle(shadow, radius = shadowR, center = Offset(cx, floorY))
                }
                // While it turns: how far it is from the lower picture to the upper one (read here, so only this redraws).
                val f = if (img1 != null) ((turnYaw(yaw, clock.longValue, start) - a0) / ANGLE_STEP).coerceIn(0f, 1f) else 0f
                val aLow = if (img1 == null || f < 0.5f) 1f else (1f - f) * 2f
                val aHigh = if (f > 0.5f) 1f else f * 2f
                if (rimg0 != null) drawImage(rimg0, dstOffset = reflOff, dstSize = reflSize, alpha = aLow, colorFilter = filter)
                if (rimg1 != null) drawImage(rimg1, dstOffset = reflOff, dstSize = reflSize, alpha = aHigh, colorFilter = filter)
                val s = sheen.value
                val sweeping = s in 0f..1f
                // (Its own layer only while the sheen sweeps, so the light stays on the piece, not round it.)
                if (sweeping) drawIntoCanvas { it.saveLayer(bounds, layerPaint) }
                if (img0 != null) drawImage(img0, dstOffset = dstOff, dstSize = dstSize, alpha = aLow, colorFilter = filter)
                if (img1 != null) drawImage(img1, dstOffset = dstOff, dstSize = dstSize, alpha = aHigh, colorFilter = filter)
                if (sweeping) {
                    translate(left - band + (fw + band) * s, top) {
                        drawRect(sheenBrush, size = Size(band, fh), blendMode = BlendMode.SrcAtop)
                    }
                    drawIntoCanvas { it.restore() }
                }
            }
        }
    )
}

/** [id] at [angle] with the cover in it: at once when it's already made, else made away from the screen's thread. */
@Composable
private fun rememberModelPicture(
    id: String, angle: Int, cover: android.graphics.Bitmap?, title: String?, pos: ArtPos, tint: Models3D.Tint?, version: Int,
    /** Update 151: the cover as asked for (its address): while it loads, the piece made before with it shows at once. */
    source: Any? = null
): android.graphics.Bitmap? {
    val waiting = source != null && cover == null
    val now = if (waiting && source is String) Models3D.cachedFor(id, angle, source, title, pos, tint) else Models3D.cached(id, angle, cover, title, pos, tint)
    val made by produceState(now, id, angle, cover, version, title, pos.key, tint?.key) {
        if (now == null && !waiting) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Models3D.compose(id, angle, cover, title, pos, tint) }
    }
    return now ?: made
}

/** [src]'s floor reflection (made once, away from the screen's thread), or null while it's being made. */
@Composable
private fun rememberReflection(src: android.graphics.Bitmap?): android.graphics.Bitmap? {
    val now = src?.let { ModelReflections.cached(it) }
    val made by produceState(now, src) {
        if (src != null && now == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ModelReflections.make(src) }
    }
    return now ?: made?.takeIf { src != null }
}

/** Update 151: the 3D pieces' floor reflections: the bottom of the picture upside down, fading out (kept a while). */
internal object ModelReflections {
    private val cache = object : android.util.LruCache<android.graphics.Bitmap, android.graphics.Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: android.graphics.Bitmap, value: android.graphics.Bitmap) = value.byteCount
    }

    fun cached(src: android.graphics.Bitmap): android.graphics.Bitmap? = cache.get(src)

    fun make(src: android.graphics.Bitmap): android.graphics.Bitmap? {
        cache.get(src)?.let { return it }
        return runCatching {
            val w = src.width
            val h = (src.height * REFLECT_K).toInt().coerceAtLeast(1)
            val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            // Flipped about its foot: the picture's bottom row at the reflection's top.
            val m = android.graphics.Matrix().apply { setScale(1f, -1f); postTranslate(0f, src.height.toFloat()) }
            c.drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            // Faint, and fading out downwards.
            val fade = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0x48000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fade)
            cache.put(src, out)
            out
        }.getOrNull()
    }
}
