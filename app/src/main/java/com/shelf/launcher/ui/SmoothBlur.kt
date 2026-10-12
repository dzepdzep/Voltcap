package com.shelf.launcher.ui

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * A real, smooth blur baked into the picture (three box-blur passes ≈ a Gaussian), done once when
 * the image loads. Used for "Blurred art": the picture is decoded small, blurred here, then
 * stretched with bilinear filtering — soft and clean on every Android version and in Lite mode,
 * instead of a blocky low-resolution copy or a costly per-frame blur.
 */
class SmoothBlur(private val radius: Int) : Transformation {
    override val cacheKey: String = "smoothBlur($radius)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = blur(input, radius)

    companion object {
        /** [input] blurred by [radius] px (a mutable ARGB copy unless it already is one). Also used by BakedBlur. */
        fun blur(input: Bitmap, radius: Int): Bitmap {
            val src = if (input.config == Bitmap.Config.ARGB_8888 && input.isMutable) input
            else input.copy(Bitmap.Config.ARGB_8888, true)
            val w = src.width
            val h = src.height
            if (w < 2 || h < 2 || radius < 1) return src
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            repeat(3) {
                boxH(px, tmp, w, h, radius)
                boxV(tmp, px, w, h, radius)
            }
            src.setPixels(px, 0, w, 0, 0, w, h)
            return src
        }

        private fun boxH(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int) {
            val div = 2 * r + 1
            for (y in 0 until h) {
                val row = y * w
                var a = 0; var rr = 0; var g = 0; var b = 0
                for (i in -r..r) {
                    val c = inp[row + i.coerceIn(0, w - 1)]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
                for (x in 0 until w) {
                    out[row + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                    val add = inp[row + (x + r + 1).coerceAtMost(w - 1)]
                    val sub = inp[row + (x - r).coerceAtLeast(0)]
                    a += (add ushr 24) - (sub ushr 24)
                    rr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                    g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                    b += (add and 0xFF) - (sub and 0xFF)
                }
            }
        }

        private fun boxV(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int) {
            val div = 2 * r + 1
            for (x in 0 until w) {
                var a = 0; var rr = 0; var g = 0; var b = 0
                for (i in -r..r) {
                    val c = inp[i.coerceIn(0, h - 1) * w + x]
                    a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
                for (y in 0 until h) {
                    out[y * w + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                    val add = inp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                    val sub = inp[(y - r).coerceAtLeast(0) * w + x]
                    a += (add ushr 24) - (sub ushr 24)
                    rr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                    g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                    b += (add and 0xFF) - (sub and 0xFF)
                }
            }
        }
    }
}
