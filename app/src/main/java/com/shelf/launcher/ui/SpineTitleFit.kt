package com.shelf.launcher.ui

import android.graphics.Canvas
import android.graphics.Paint

/*
 * Update 391: a custom spine's title fits without moving and without "…": the size drops step by step, and as soon as a second
 * (third …) line fits across the spine's thickness the name wraps onto it (at spaces, a longer word broken where it must).
 * Used by every spine picture Voltcap draws itself (template spines, the 3D case's spine, own Steam / Android spines, a
 * drawn box's side). The home rows' Compose spines do the same in `SpineName` (ShelfHome.kt). Pure functions: unit-tested.
 */
internal object SpineTitleFit {
    const val LINE = 1.08f

    /** [title] split into at most [maxLines] lines no wider than [along] at the paint's current size, or null if it doesn't fit. */
    fun wrap(measure: (String) -> Float, title: String, along: Float, maxLines: Int): List<String>? {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        var cur = ""
        fun flush() { if (cur.isNotEmpty()) { lines.add(cur); cur = "" } }
        for (w in words) {
            var word = w
            val joined = if (cur.isEmpty()) word else "$cur $word"
            if (measure(joined) <= along) { cur = joined; continue }
            flush()
            // A word wider than the strip: broken where it has to be.
            while (measure(word) > along) {
                var n = word.length - 1
                while (n > 1 && measure(word.substring(0, n)) > along) n--
                if (n < 1) return null
                lines.add(word.substring(0, n)); word = word.substring(n)
                if (lines.size > maxLines) return null
            }
            cur = word
        }
        flush()
        return if (lines.size <= maxLines) lines else null
    }

    /** The size at which [title] fits [along] x [across] (as many lines as fit), and the lines. Sets [p]'s size. */
    fun fit(p: Paint, title: String, along: Float, across: Float, startSize: Float, floor: Float): List<String> {
        var s = startSize
        val t = title.trim()
        while (true) {
            p.textSize = s
            val rows = (across / (s * LINE)).toInt().coerceAtLeast(1)
            val w = wrap({ p.measureText(it) }, t, along, rows)
            if (w != null) return w
            if (s <= floor) break
            s = (s - 1f).coerceAtLeast(floor)
        }
        // Below the floor (rare: an extremely long name on a slim spine): keep shrinking, the whole name stays.
        while (s > 2f) {
            s -= 1f; p.textSize = s
            val w = wrap({ p.measureText(it) }, t, along, (across / (s * LINE)).toInt().coerceAtLeast(1))
            if (w != null) return w
        }
        return listOf(t)
    }

    /** [lines] drawn centred in a [0, along] x [-across/2, across/2] strip (the canvas is already moved / turned onto it). */
    fun draw(c: Canvas, p: Paint, lines: List<String>, along: Float) {
        val fm = p.fontMetrics
        val lh = p.textSize * LINE
        val first = -(lines.size - 1) * lh / 2f - (fm.ascent + fm.descent) / 2f
        lines.forEachIndexed { i, l -> c.drawText(l, (along - p.measureText(l)) / 2f, first + i * lh, p) }
    }
}
