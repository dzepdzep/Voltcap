package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Update 344: game art turned into a few-shade pixel picture (Pocket camera's dithered photos, Terminal's preview): the
 * picture shrunk to a handheld camera's size, its levels stretched, then ordered (Bayer 4 × 4) dithering into the
 * palette's shades, darkest first. Made off the main thread once per picture / size / palette and kept in a small cache;
 * drawn scaled up with no smoothing so every pixel stays a sharp block.
 */

internal object DitherCache {
    val cache = LruCache<String, ImageBitmap>(64)
}

private val BAYER4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** [url]'s picture as a [w] × [h] dithered picture in [palette] (ARGB, darkest first); null until it's made. */
@Composable
internal fun rememberDithered(url: String?, w: Int, h: Int, palette: IntArray, crop: Boolean = true): ImageBitmap? {
    val src = rememberSoftBitmap(url, maxSide = max(w, h) * 3)
    val key = remember(url, w, h, palette.contentHashCode(), crop) { url?.let { "$it|$w|$h|${palette.contentHashCode()}|$crop" } }
    val cached = remember(key) { key?.let { DitherCache.cache.get(it) } }
    val out by produceKeyed(cached, key, key, src) {
        if (cached != null) { value = cached; return@produceKeyed }
        val s = src ?: run { value = null; return@produceKeyed }
        val made = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { ditherBitmap(s, w, h, palette, crop) }.getOrNull() }
        if (made != null && key != null) DitherCache.cache.put(key, made)
        value = made
    }
    return out
}

/** The work itself (kept free of Compose so a test can run it). */
internal fun ditherPixels(src: IntArray, w: Int, h: Int, palette: IntArray): IntArray {
    val n = w * h
    val lum = FloatArray(n)
    for (i in 0 until n) {
        val p = src[i]
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
    }
    // Stretch the levels (2nd–98th percentile) so dull covers still use every shade.
    val sorted = lum.copyOf().also { it.sort() }
    val lo = sorted[(n * 0.02f).toInt().coerceIn(0, n - 1)]
    val hi = sorted[(n * 0.98f).toInt().coerceIn(0, n - 1)].let { if (it - lo < 8f) lo + 8f else it }
    val levels = palette.size
    val out = IntArray(n)
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        val v = ((lum[i] - lo) / (hi - lo)).coerceIn(0f, 1f) * (levels - 1)
        val t = (BAYER4[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f - 0.5f
        val idx = (v + t).roundToInt().coerceIn(0, levels - 1)
        out[i] = palette[idx]
    }
    return out
}

private fun ditherBitmap(src: Bitmap, w: Int, h: Int, palette: IntArray, crop: Boolean): ImageBitmap {
    val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(scaled)
    canvas.drawColor(palette.last())
    val sw = src.width.toFloat(); val sh = src.height.toFloat()
    val k = if (crop) max(w / sw, h / sh) else min(w / sw, h / sh)
    val dw = sw * k; val dh = sh * k
    val left = (w - dw) / 2f; val top = (h - dh) / 2f
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(src, null, android.graphics.RectF(left, top, left + dw, top + dh), paint)
    val px = IntArray(w * h)
    scaled.getPixels(px, 0, w, 0, 0, w, h)
    val out = ditherPixels(px, w, h, palette)
    scaled.setPixels(out, 0, w, 0, 0, w, h)
    return scaled.asImageBitmap()
}
