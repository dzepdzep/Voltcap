package com.shelf.launcher.data

/**
 * Update 371: is an app's one-colour candidate (its monochrome drawable, its adaptive foreground, or a plain icon) fit to be
 * painted in the theme's colour? Conservative: only obviously unsuitable candidates are refused (a nearly solid square, a
 * background plate taken for a foreground, a candidate with nothing left to see); simple logos stay themed. Pure — pixels in,
 * verdict out — so it is unit tested without a device. Nothing here names an app.
 */
object IconQuality {
    class Verdict(val usable: Boolean, val reason: String)

    /** What an icon is drawn as on a themed surface. */
    enum class Look { ORIGINAL, MONO, DETAIL, SOLID }

    /**
     * [argb] is a [w] x [h] bitmap of the candidate (the visible middle of the layer). [trueMono]: the app's own
     * monochrome drawable (its shape alone is the picture, so no luminance detail is asked of it).
     */
    fun assess(argb: IntArray, w: Int, h: Int, trueMono: Boolean): Verdict {
        val n = w * h
        if (n <= 0 || argb.size < n) return Verdict(false, "empty")
        var visible = 0
        var opaque = 0
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        var lumaSum = 0.0; var lumaSq = 0.0
        var edge = 0; var edgeOpaque = 0
        val ring = maxOf(1, w / 25)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = argb[y * w + x]
                val a = p ushr 24
                val onEdge = x < ring || y < ring || x >= w - ring || y >= h - ring
                if (onEdge) edge++
                if (a >= 16) visible++
                if (a >= 200) {
                    opaque++
                    if (onEdge) edgeOpaque++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    val l = (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)) / 255.0
                    lumaSum += l
                    lumaSq += l * l
                }
            }
        }
        val vis = visible.toDouble() / n
        val opq = opaque.toDouble() / n
        if (vis < 0.02) return Verdict(false, "almost nothing visible")
        if (edge > 0 && edgeOpaque.toDouble() / edge >= 0.9) return Verdict(false, "fills to the edges: a background plate")
        if (opq >= 0.90) return Verdict(false, "nearly solid")
        if (opaque > 0) {
            val bw = (maxX - minX + 1).toDouble() / w
            val bh = (maxY - minY + 1).toDouble() / h
            // A filled rounded square covers about 0.92-0.96 of its box, a full circle 0.785: only the former is refused.
            if (opq >= 0.84 && bw >= 0.9 && bh >= 0.9) return Verdict(false, "filled square")
            if (!trueMono) {
                val mean = lumaSum / opaque
                val sd = Math.sqrt(maxOf(0.0, lumaSq / opaque - mean * mean))
                if (opq > 0.6 && sd < 0.06) return Verdict(false, "flat blob: no detail would remain")
                if (mean < 0.12) return Verdict(false, "too dark to show in one colour")
            }
        }
        return Verdict(true, "ok")
    }

    /**
     * How a themed surface draws an icon. [themed]: the surface's themed option is on; [style]: the app's own or the
     * global style; [usable]: [assess] accepted the candidate; [trueMono]: it is the app's own monochrome drawable.
     */
    fun look(themed: Boolean, style: Features.MonoIconStyle, usable: Boolean, trueMono: Boolean): Look = when {
        !themed || style == Features.MonoIconStyle.ORIGINAL || !usable -> Look.ORIGINAL
        trueMono -> Look.MONO
        style == Features.MonoIconStyle.SOLID -> Look.SOLID
        else -> Look.DETAIL
    }
}
