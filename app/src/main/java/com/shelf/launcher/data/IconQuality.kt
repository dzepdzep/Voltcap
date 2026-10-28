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
     * Update 373: the symbol of a flat-background icon (an opaque icon whose background is one colour, e.g. a red square with a
     * white mark): pixels that match the background colour turn transparent (softly, so anti-aliased edges stay smooth), the
     * rest keep their colour. Interior holes and thin lines survive because every pixel is judged alone, not flood-filled.
     * Null when the icon has no flat background (a photo, a gradient), when nothing would remain, or when what remains is still
     * a block ([assess]): then the caller moves on to the next candidate. Returns a new w x h ARGB array.
     */
    fun extractSymbol(argb: IntArray, w: Int, h: Int): IntArray? {
        val n = w * h
        if (n <= 0 || argb.size < n) return null
        val ring = maxOf(2, w / 16)
        val buckets = HashMap<Int, IntArray>() // quantised rgb -> [count, r, g, b]
        var ringOpaque = 0
        var ringAll = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!(x < ring || y < ring || x >= w - ring || y >= h - ring)) continue
            ringAll++
            val p = argb[y * w + x]
            if ((p ushr 24) < 240) continue
            ringOpaque++
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val e = buckets.getOrPut(((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)) { IntArray(4) }
            e[0]++; e[1] += r; e[2] += g; e[3] += b
        }
        // A rounded or circular mask leaves transparent corners: the opaque part of the ring is what is sampled.
        if (ringAll == 0 || ringOpaque < ringAll * 0.2) return null
        val top = buckets.values.maxByOrNull { it[0] } ?: return null
        if (top[0] < ringOpaque * 0.8) return null // not one flat colour
        val br = top[1] / top[0]; val bg = top[2] / top[0]; val bb = top[3] / top[0]
        val out = IntArray(n)
        for (i in 0 until n) {
            val p = argb[i]
            val a = p ushr 24
            if (a == 0) continue
            val d = maxOf(Math.abs(((p shr 16) and 255) - br), Math.abs(((p shr 8) and 255) - bg), Math.abs((p and 255) - bb))
            // 0 up to 18 away from the background colour, full from 60 on.
            val f = ((d - 18) / 42.0).coerceIn(0.0, 1.0)
            val na = (a * f).toInt()
            if (na > 0) out[i] = (na shl 24) or (p and 0xFFFFFF)
        }
        var visible = 0
        for (p in out) if ((p ushr 24) >= 16) visible++
        val vis = visible.toDouble() / n
        // Too little is a stray speck; too much means the background was not what filled the icon.
        if (vis < 0.04 || vis > 0.75) return null
        return if (assess(out, w, h, false).usable) out else null
    }

    /**
     * Update 373: the letters a monogram shows for an app named [label]: the first letters of its first two words, or the first
     * two characters of a single word ("Ch" for "Chrome", "AR" for "ARMSX2": a word written in capitals keeps them). Built from the app's
     * own name only: no made-up logo. Never empty (a "?" for a label without letters or digits).
     */
    fun monogram(label: String): String {
        val words = label.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "?"
        if (words.size >= 2) return words.take(2).joinToString("") { it.substring(0, it.offsetByCodePoints(0, 1)).uppercase() }
        val w = words[0]
        val cp = minOf(2, w.codePointCount(0, w.length))
        val two = w.substring(0, w.offsetByCodePoints(0, cp))
        return if (w == w.uppercase()) two else two.replaceFirstChar { it.uppercase() }
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
