package com.shelf.launcher.ui

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * The "upscaler" for backgrounds. Some heroes are small — a 320×240 retro
 * screenshot or a 640-wide thumbnail — and stretching them over a 1080p
 * screen with smooth filtering turns them into mush.
 *
 * This first blows a small image up by a whole-number factor with
 * nearest-neighbour (every source pixel becomes a clean square block), and
 * only then lets the screen do the last, small smooth scaling step. That's
 * the "sharp bilinear" trick emulators use: pixel art stays crisp, photos
 * lose the blocky / blurry look. The proportions never change, so the
 * picture fits the screen exactly as before — it's only sharper.
 *
 * [factor] null picks one automatically (images already big enough are
 * untouched); 2 / 4 / 6 / 8 force that factor — set per game under
 * Artwork & details. Either way the result is capped at [MAX_SIDE] pixels
 * so a big image can't run the device out of memory.
 */
class SharpUpscale(
    private val targetWidth: Int = 1920,
    private val factor: Int? = null
) : Transformation {
    override val cacheKey: String = "sharp-upscale-$targetWidth-${factor ?: "auto"}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width <= 0 || input.height <= 0) return input
        val wanted = factor ?: run {
            if (input.width >= targetWidth * 2 / 3) return input
            ((targetWidth + input.width - 1) / input.width).coerceIn(2, 6)
        }
        val largestSide = maxOf(input.width, input.height)
        val applied = minOf(wanted, MAX_SIDE / largestSide)
        if (applied < 2) return input
        return Bitmap.createScaledBitmap(input, input.width * applied, input.height * applied, false)
    }

    companion object {
        /** Longest side of an upscaled image, in pixels. */
        const val MAX_SIDE = 4096
    }
}
