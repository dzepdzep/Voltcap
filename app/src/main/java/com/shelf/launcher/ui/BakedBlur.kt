package com.shelf.launcher.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Update 121: blurred backgrounds without a blur on every frame. A blurred picture is made once —
 * shrunk to a tiny bitmap and softened on the CPU ([SmoothBlur]) — and then simply stretched (bilinear)
 * over the screen. Before, a full-screen blur ran on the GPU each time anything on top of it redrew
 * (a clock, a lyric line, the scene in front), and only on Android 12 and newer.
 */
internal object BakedBlur {
    /** Long side of the small copy: soft enough for any blur, and only ~100 × 60 pixels to process. */
    private const val SIDE = 96

    /**
     * Update 130: how big the small copy is for a [blur] over a screen whose long side is [screenDp]: one small pixel is
     * at most about a third of the blur, so the stretched copy never shows blocks (a light blur was a few pixels
     * blurred by one, stretched 15–20 times: visibly blocky). Between 96 and 480 px on its long side.
     */
    fun sideFor(blur: Dp, screenDp: Float): Int = (screenDp / (blur.value / 3f).coerceAtLeast(1f)).roundToInt().coerceIn(SIDE, 480)

    /** The radius at that size: the blur's own strength in small pixels, at least 3 (a smooth, round blur). */
    fun radiusAt(blur: Dp, side: Int, screenDp: Float): Int =
        (blur.value * side / screenDp.coerceAtLeast(1f)).roundToInt().coerceIn(3, (side / 6).coerceAtLeast(3))

    /** [src] shrunk to at most [side] px on its long side, then blurred. Null if it can't be read. */
    fun of(src: Bitmap, radius: Int, side: Int = SIDE): Bitmap? = runCatching {
        val s = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val k = side / max(s.width, s.height).toFloat()
        val small = if (k >= 1f) s.copy(Bitmap.Config.ARGB_8888, true)
        else Bitmap.createScaledBitmap(s, (s.width * k).toInt().coerceAtLeast(2), (s.height * k).toInt().coerceAtLeast(2), true)
        SmoothBlur.blur(small, radius)
    }.getOrNull()
}

/** [src] blurred once by about [blur] (null until it's ready, or with no picture). Made off the main thread. */
@Composable
internal fun rememberBakedBlur(src: Bitmap?, blur: Dp): ImageBitmap? {
    // (Update 130: sized by the blur and the screen, so it's never blocky — see BakedBlur.sideFor.)
    val screenDp = screenLongDp()
    val side = BakedBlur.sideFor(blur, screenDp)
    val r = BakedBlur.radiusAt(blur, side, screenDp)
    return produceState<ImageBitmap?>(null, src, r, side) {
        value = if (src == null) null
        else withContext(Dispatchers.Default) { BakedBlur.of(src, r, side)?.asImageBitmap() }
    }.value
}

/** The screen's long side in dp (for the blurred copies' size). */
@Composable
private fun screenLongDp(): Float {
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    return max(conf.screenWidthDp, conf.screenHeightDp).toFloat().coerceAtLeast(320f)
}

/**
 * A picture from a URL (or any Coil model), decoded tiny and blurred once by about [blur] — kept in Coil's
 * cache like any picture — and stretched over [modifier]. [blur] under 1 dp shows it sharp.
 */
@Composable
internal fun BakedBlurImage(model: Any?, blur: Dp, modifier: Modifier, alpha: Float = 1f) {
    if (blur.value < 1f) {
        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, alpha = alpha, modifier = modifier)
        return
    }
    val ctx = LocalContext.current
    // Update 130: decoded as big as the blur needs (it was always 64 px, and a light blur showed the blocks).
    val screenDp = screenLongDp()
    val side = BakedBlur.sideFor(blur, screenDp)
    val r = BakedBlur.radiusAt(blur, side, screenDp)
    // The short side of a screen-shaped copy: Fill makes the picture cover side × that.
    val short = (side * 9 / 16).coerceAtLeast(48)
    val request = remember(model, r, side) {
        coil.request.ImageRequest.Builder(ctx)
            .data(model)
            .size(side, short)
            .scale(coil.size.Scale.FILL)
            .precision(coil.size.Precision.INEXACT)
            .transformations(SmoothBlur(r))
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request, contentDescription = null, contentScale = ContentScale.Crop, alpha = alpha,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low, modifier = modifier
    )
}

/**
 * Update 121: a blur over moving content (Theme look › Blur over the whole screen, on an animated scene)
 * done on a small copy: the content is drawn at 1/k of the screen's resolution, blurred there by 1/k of
 * the radius, and stretched back up. Blurred, the lost detail can't be seen, and the blur — which has to
 * run every frame here — does a fraction of the work. Layout and touches are unchanged (drawing only).
 * Android 12+ (no blur before); [radius] in pixels.
 */
internal fun Modifier.lowResBlur(radius: Float): Modifier = if (radius < 1f) this else this.then(
    Modifier.drawWithCache {
        val full = size
        // Bigger blurs hide more detail, so they can be drawn smaller (at most a quarter across).
        val k = (radius / 6f).coerceIn(1f, 4f)
        val w = ceil(full.width / k).toInt().coerceAtLeast(1)
        val h = ceil(full.height / k).toInt().coerceAtLeast(1)
        val layer = obtainGraphicsLayer()
        layer.compositingStrategy = CompositingStrategy.Offscreen
        layer.pivotOffset = Offset.Zero
        layer.clip = true
        layer.scaleX = full.width / w
        layer.scaleY = full.height / h
        layer.renderEffect = BlurEffect(radius / k, radius / k, TileMode.Clamp)
        onDrawWithContent {
            layer.record(IntSize(w, h)) {
                scale(w / full.width, h / full.height, pivot = Offset.Zero) {
                    this@onDrawWithContent.drawContent()
                }
            }
            drawLayer(layer)
        }
    }
)
