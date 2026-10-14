package com.shelf.launcher.ui

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/*
 * Update 202: grey / black / white strips that are part of a cover's picture file, trimmed off once when it's decoded.
 *
 * Some covers (SteamGridDB uploads, users' own scans or screenshots of a box) carry flat bars or scanner margins in
 * the file itself: drawn edge to edge they still look like a cover sitting in a second frame. This finds, at each edge,
 * the lines (rows / columns) that are one flat, neutral colour (grey, black, white, near-neutral), fully opaque and
 * with no detail in them, and cuts them off. It is careful, so artwork and branding are never cut:
 *  - a line counts only when its sampled pixels are all (98 %: one stray JPEG pixel) within [TOL] of the band's
 *    colour: a line with a logo, text, gradient or artwork in it stops the band there;
 *  - a coloured band (a Switch red, an Xbox 360 green) is never a margin: only near-neutral ones ([NEUTRAL]);
 *  - a picture with see-through pixels at its edges (an icon, a logo, a disc / cartridge cut-out) is left as it is;
 *  - a band is trimmed only when it is between 1 % and 25 % of that side's length. One reaching 25 % is design (a
 *    white box, a plain sky), so nothing at all is trimmed then;
 *  - bands are trimmed only in matching pairs (left and right, or top and bottom, of the same colour and about the same
 *    size), the way letterboxing / a frame looks. A single band at one edge is usually the case's own printed band
 *    (the PS2 / GameCube black header, the DS / Wii white one, the PlayStation band down the left) and is kept;
 *  - what is left must still be a sensible cover (at most 40 % of either side gone, a cover-like shape).
 * Cheap: each edge reads one line at a time and every Nth pixel of it ([SAMPLES] per line), stopping at the first
 * line with detail. Most covers stop at their very first line. It runs once per decode, and the requests that use it
 * put [KEY] in their memory-cache key, so the trimmed picture is what's kept in memory and reused.
 * Used for covers only (ArtRequests.kt artRequest for ArtKind.COVER, StyleArtRequests.kt with trim, Case3D's front),
 * so the same for users' own covers (content:// / file addresses go through the same requests). Never for app icons,
 * backgrounds, heroes, screenshots or logos.
 */
internal object TrimUniformMargins : Transformation {
    /** Goes into the memory-cache key of every request that trims (bump it when the rules below change). */
    const val KEY = "trim1"
    override val cacheKey: String = "voltcapTrimUniformMargins(1)"

    /** Per channel: how far a pixel may be from the band's colour (JPEG noise in a flat band). */
    private const val TOL = 14
    /** Largest difference between a band colour's channels for it to count as grey / black / white. */
    private const val NEUTRAL = 28
    /** Pixels looked at along each line. */
    private const val SAMPLES = 96
    /** Share of a line's samples that must be the band's colour (one stray JPEG pixel in 96 is still flat). */
    private const val FLAT_SHARE = 0.98f
    /** Smallest and largest band, as a share of the side it's on. */
    private const val MIN_SHARE = 0.01f
    private const val MAX_SHARE = 0.25f
    /** Most of a side's length that may go in all (both bands together). */
    private const val MAX_AXIS_SHARE = 0.40f

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = runCatching { trim(input) }.getOrDefault(input)

    /** [src] with its flat margins cut off, or [src] itself when it has none (or they don't pass the rules above). */
    fun trim(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        if (w < 48 || h < 48 || src.config == Bitmap.Config.HARDWARE) return src
        val buf = IntArray(maxOf(w, h))
        // See-through edges: an icon, a logo, a cut-out disc. Left alone.
        if (src.hasAlpha() && (0..3).any { e -> seeThrough(src, e, buf) }) return src
        val maxV = (h * MAX_SHARE).toInt(); val maxH = (w * MAX_SHARE).toInt()
        val top = band(src, TOP, maxV, buf) ?: return src
        val bottom = band(src, BOTTOM, maxV, buf) ?: return src
        val left = band(src, LEFT, maxH, buf) ?: return src
        val right = band(src, RIGHT, maxH, buf) ?: return src
        val (t, b) = pair(top, bottom, h)
        val (l, r) = pair(left, right, w)
        if (t + b + l + r == 0) return src
        val cw = w - l - r; val ch = h - t - b
        // What's left is still a cover: most of each side kept, a cover-like shape.
        if (cw < w * (1f - MAX_AXIS_SHARE) || ch < h * (1f - MAX_AXIS_SHARE)) return src
        val aspect = cw.toFloat() / ch
        if (aspect < 0.4f || aspect > 2.6f) return src
        return Bitmap.createBitmap(src, l, t, cw, ch)
    }

    private const val TOP = 0
    private const val BOTTOM = 1
    private const val LEFT = 2
    private const val RIGHT = 3

    /** A flat band at one edge: how many lines deep, and its colour. */
    private class Band(val lines: Int, val color: Int)

    /** Band sizes trimmed off two opposite edges: both when they make a matching pair (letterbox / frame), else none. */
    private fun pair(a: Band, b: Band, side: Int): Pair<Int, Int> {
        val min = maxOf(2, kotlin.math.ceil(side * MIN_SHARE).toInt())
        if (a.lines < min || b.lines < min) return 0 to 0
        if (!near(a.color, b.color, 2 * TOL)) return 0 to 0
        // About the same size (centred letterboxing is within a pixel or two; a little more for a scan).
        val big = maxOf(a.lines, b.lines); val small = minOf(a.lines, b.lines)
        if (big - small > maxOf(2, (side * 0.01f).toInt()) && small < big * 0.6f) return 0 to 0
        return a.lines to b.lines
    }

    /**
     * How many lines from [edge] in are one flat, neutral, opaque colour (0: none). Null when the band runs [limit]
     * lines or more: a flat area that big is the cover's own design, so nothing at all is trimmed.
     */
    private fun band(src: Bitmap, edge: Int, limit: Int, buf: IntArray): Band? {
        val first = line(src, edge, 0, buf)
        val len = lineLength(src, edge)
        val ref = meanColor(first, len)
        if (!neutral(ref) || !flat(first, len, ref, TOL, FLAT_SHARE)) return Band(0, ref)
        var n = 1
        while (n < limit && flat(line(src, edge, n, buf), len, ref, TOL, FLAT_SHARE)) n++
        if (n >= limit) return null
        // The soft seam where the bar meets the art (a line or two blurred by JPEG): goes with the bar. Stricter than
        // it looks: 90 % of the line still has to be the bar's colour, so a dark edge of the art itself stays.
        var seam = 0
        while (seam < 2 && n + 1 < limit && flat(line(src, edge, n, buf), len, ref, 2 * TOL, 0.9f)) { n++; seam++ }
        return Band(n, ref)
    }

    private fun lineLength(src: Bitmap, edge: Int): Int = if (edge == TOP || edge == BOTTOM) src.width else src.height

    /** Line [i] in from [edge], read into [buf] (a row for top / bottom, a column for left / right). */
    private fun line(src: Bitmap, edge: Int, i: Int, buf: IntArray): IntArray {
        when (edge) {
            TOP -> src.getPixels(buf, 0, src.width, 0, i, src.width, 1)
            BOTTOM -> src.getPixels(buf, 0, src.width, 0, src.height - 1 - i, src.width, 1)
            LEFT -> src.getPixels(buf, 0, 1, i, 0, 1, src.height)
            else -> src.getPixels(buf, 0, 1, src.width - 1 - i, 0, 1, src.height)
        }
        return buf
    }

    private fun seeThrough(src: Bitmap, edge: Int, buf: IntArray): Boolean {
        val l = line(src, edge, 0, buf); val len = lineLength(src, edge)
        val step = (len / SAMPLES).coerceAtLeast(1)
        var i = 0
        while (i < len) { if ((l[i] ushr 24) < 250) return true; i += step }
        return false
    }

    private fun meanColor(l: IntArray, len: Int): Int {
        val step = (len / SAMPLES).coerceAtLeast(1)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        var i = 0
        while (i < len) { val p = l[i]; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++; i += step }
        if (n == 0L) return 0
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** At least [share] of the sampled pixels opaque and within [tol] of [ref] on every channel. */
    private fun flat(l: IntArray, len: Int, ref: Int, tol: Int, share: Float): Boolean {
        val step = (len / SAMPLES).coerceAtLeast(1)
        var ok = 0; var n = 0
        var i = 0
        while (i < len) {
            val p = l[i]
            if ((p ushr 24) >= 250 && near(p, ref, tol)) ok++
            n++; i += step
        }
        return n > 0 && ok >= n * share
    }

    private fun near(a: Int, b: Int, tol: Int): Boolean =
        kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) <= tol &&
            kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) <= tol &&
            kotlin.math.abs((a and 255) - (b and 255)) <= tol

    private fun neutral(c: Int): Boolean {
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        return maxOf(r, g, b) - minOf(r, g, b) <= NEUTRAL
    }
}
