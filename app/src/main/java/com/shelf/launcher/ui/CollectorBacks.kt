package com.shelf.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/*
 * Update 305: the generated back of a case that has no back template of its own (Steam, Android, PlayStation 3 / 4 / 5,
 * the Xboxes and the rest MediaBacks has nothing for): laid out like a boxed collector's release — the platform's band on
 * top, the game's hero art with its title, a row of its real screenshots, its description, and a footer with only the
 * details that are known for this game (publisher, developer, year, players / controller support from its store or
 * ScreenScraper record) and its rating as a plain text badge ("USK 16") when a rating body's rating for this game was
 * found — never guessed, never an official rating icon. A line at the foot says it's a Voltcap case, not an official
 * release. Every part is optional: with less known it steps down to a hero-and-text back, a cover-coloured one, or a
 * plain title back — never invented screenshots or text.
 */
internal object CollectorBacks {
    /** How wide a back is made (its height from the case's shape). */
    const val WIDTH = 600

    private class Style(val top: Int, val bottom: Int, val accent: Int, val band: Int?, val name: String)

    private fun styleOf(platform: String?): Style = when (platform) {
        "Steam" -> Style(0xFF1B2838.toInt(), 0xFF0B1016.toInt(), 0xFF66C0F4.toInt(), com.shelf.launcher.R.drawable.cov_front_steam, "Steam")
        "Android", "App" -> Style(0xFF222624.toInt(), 0xFF101211.toInt(), 0xFF3DDC84.toInt(), com.shelf.launcher.R.drawable.cov_front_android, "Android")
        else -> Style(0xFF1C1E25.toInt(), 0xFF0C0D10.toInt(), 0xFFB8C2D6.toInt(), null,
            platform?.let { com.shelf.launcher.data.ConsoleNames.display(it) } ?: "")
    }

    /** The band at the top of Voltcap's own Steam / Android case fronts (MediaCovers: the top 11.58 % of the front). */
    private val bands = java.util.concurrent.ConcurrentHashMap<Int, Bitmap>()

    private fun band(context: android.content.Context, res: Int, share: Float = FRONT_HEADER, extra: Int = 2): Bitmap? = bands[res] ?: runCatching {
        val o = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        val full = android.graphics.BitmapFactory.decodeResource(context.resources, res, o) ?: return null
        val h = (full.height * share).toInt().coerceAtLeast(1)
        // (Its blue / green line under it kept: 2 px more.)
        Bitmap.createBitmap(full, 0, 0, full.width, (h + extra).coerceAtMost(full.height)).also { bands[res] = it }
    }.getOrNull()

    /** Update 331: the Steam / Android fronts' header, as MediaCovers declares it (the top of their art window). */
    private val FRONT_HEADER: Float get() = MediaCovers.own("Steam")?.win?.get(1) ?: 0.1158f

    /** A colour from [b] with some life in it (the most saturated of its averaged patches), or null. */
    fun accentOf(b: Bitmap?): Int? {
        if (b == null || b.width < 2 || b.height < 2) return null
        val s = runCatching { Bitmap.createScaledBitmap(b, 12, 12, true) }.getOrNull() ?: return null
        var best = 0; var bestScore = -1f
        val hsv = FloatArray(3)
        for (y in 0 until s.height) for (x in 0 until s.width) {
            val c = s.getPixel(x, y)
            android.graphics.Color.colorToHSV(c, hsv)
            val score = hsv[1] * (0.35f + hsv[2]) * (if (hsv[2] < 0.18f) 0.2f else 1f)
            if (score > bestScore) { bestScore = score; best = c }
        }
        if (bestScore < 0.12f) return null
        android.graphics.Color.colorToHSV(best, hsv)
        // (Lifted so it reads on the dark back: bright, a little less saturated than the raw pixel.)
        hsv[1] = hsv[1].coerceIn(0.35f, 0.8f); hsv[2] = hsv[2].coerceIn(0.75f, 0.95f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    fun compose(context: android.content.Context, spec: PrintedBack, art: Bitmap?, shots: List<Bitmap>): Bitmap {
        val w = WIDTH
        val aspect = spec.aspect.coerceIn(0.3f, 3f)
        val h = (w / aspect).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val st = styleOf(spec.platform)
        val accent = accentOf(art) ?: st.accent
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // The card: a deep two-tone gradient, a faint printed grain of fine diagonal lines.
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), st.top, st.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        grain(c, w, h)
        if (aspect > 1.15f) landscape(context, c, w, h, spec, st, accent, art, shots) else portrait(context, c, w, h, spec, st, accent, art, shots)
        return out
    }

    // ---- portrait (the usual case) ----

    private fun portrait(context: android.content.Context, c: Canvas, w: Int, h: Int, spec: PrintedBack, st: Style, accent: Int,
                         art: Bitmap?, shots: List<Bitmap>) {
        val m = w * 0.055f
        var y = header(context, c, w, h, spec, st)
        val text = spec.text?.trim()?.takeIf { it.isNotEmpty() }
        val footer = footerHeight(w, h, spec)
        val foot = h - footer
        // How much room the hero takes: more when there's little else to show.
        val heroH = when {
            art == null -> 0f
            shots.isEmpty() && text == null -> (foot - y) * 0.78f
            shots.isEmpty() || text == null -> (foot - y) * 0.52f
            else -> (foot - y) * 0.40f
        }
        if (art != null) {
            hero(c, art, RectF(0f, y, w.toFloat(), y + heroH), st.bottom)
            title(c, spec.title, RectF(m, y + heroH * 0.45f, w - m, y + heroH - h * 0.012f), accent, bottomAligned = true)
            y += heroH + h * 0.018f
        } else {
            // (No art at all: the title alone, large, on the card — a back that's designed, not empty.)
            val tH = (foot - y) * (if (shots.isEmpty() && text == null) 0.6f else 0.28f)
            title(c, spec.title, RectF(m, y + h * 0.02f, w - m, y + tH), accent, bottomAligned = false, big = true)
            y += tH + h * 0.012f
        }
        if (shots.isNotEmpty()) {
            val n = shots.size.coerceAtMost(3)
            val gap = w * 0.025f
            val sw = (w - 2 * m - gap * (n - 1)) / n
            var sh = sw * 9f / 16f
            if (text == null) sh = (sh * 1.25f).coerceAtMost((foot - y) * 0.6f)
            val sw2 = if (text == null && n < 3) sw else sw
            var x = m
            for (k in 0 until n) {
                shot(c, shots[k], RectF(x, y, x + sw2, y + sh), w)
                x += sw2 + gap
            }
            y += sh + h * 0.022f
        }
        if (text != null) {
            val box = RectF(m, y, w - m, foot - h * 0.018f)
            paragraph(c, text, box, w * 0.034f, 0xF0E6E9EF.toInt())
        }
        footer(c, w, h, foot, spec, st, accent)
    }

    // ---- landscape (a wide case without a template): hero and title left, the rest right ----

    private fun landscape(context: android.content.Context, c: Canvas, w: Int, h: Int, spec: PrintedBack, st: Style, accent: Int,
                          art: Bitmap?, shots: List<Bitmap>) {
        val m = w * 0.035f
        val top = header(context, c, w, h, spec, st)
        val footer = footerHeight(w, h, spec)
        val foot = h - footer
        val split = w * 0.5f
        if (art != null) hero(c, art, RectF(0f, top, split, foot), st.bottom, sideFade = true)
        title(c, spec.title, RectF(m, top + (foot - top) * 0.5f, split - m, foot - h * 0.03f), accent, bottomAligned = true)
        var y = top + h * 0.03f
        val x0 = split + m; val x1 = w - m
        if (shots.isNotEmpty()) {
            val n = shots.size.coerceAtMost(2)
            val gap = w * 0.015f
            val sw = (x1 - x0 - gap * (n - 1)) / n; val sh = sw * 9f / 16f
            var x = x0
            for (k in 0 until n) { shot(c, shots[k], RectF(x, y, x + sw, y + sh), w); x += sw + gap }
            y += sh + h * 0.03f
        }
        spec.text?.trim()?.takeIf { it.isNotEmpty() }?.let { paragraph(c, it, RectF(x0, y, x1, foot - h * 0.02f), w * 0.022f, 0xF0E6E9EF.toInt()) }
        footer(c, w, h, foot, spec, st, accent)
    }

    // ---- the parts ----

    /** The platform's band across the top; returns where it ends. */
    private fun header(context: android.content.Context, c: Canvas, w: Int, h: Int, spec: PrintedBack, st: Style): Float {
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // (Update 331: the matching case pack's back header for this console — its logo and line, as on its front and
        // spine — when the pack has one: Steam, Android, PlayStation 3 / 4, the Xboxes, the computers and arcades.)
        MediaBacks.headerRes(spec.platform)?.let { res -> band(context, res, MediaBacks.HEADER, 0) }?.let { b ->
            val bh = w * b.height.toFloat() / b.width
            c.drawBitmap(b, null, RectF(0f, 0f, w.toFloat(), bh), p)
            return bh
        }
        st.band?.let { res -> band(context, res) }?.let { b ->
            // Voltcap's own Steam / Android band, as on the case's front (its logo and line), at the back's width.
            val bh = w * b.height.toFloat() / b.width
            c.drawBitmap(b, null, RectF(0f, 0f, w.toFloat(), bh), p)
            return bh
        }
        // Another console: a dark bar with its logo (the redrawn logo set Voltcap ships, PlatformLogos) or its name.
        val bh = (h * 0.075f).coerceAtMost(w * 0.12f)
        p.color = 0xFF0A0B0E.toInt(); c.drawRect(0f, 0f, w.toFloat(), bh, p)
        p.color = 0x33FFFFFF; c.drawRect(0f, bh - 1.5f, w.toFloat(), bh, p)
        val logo = spec.platform?.let { runCatching { PlatformLogos.baked(context, it)?.asAndroidBitmap() }.getOrNull() }
        val pad = bh * 0.2f
        var x = w * 0.055f
        if (logo != null && logo.height > 0) {
            val lh = bh - 2 * pad
            val lw = (lh * logo.width / logo.height).coerceAtMost(w * 0.32f)
            val lhh = lw * logo.height / logo.width
            val dst = RectF(x, (bh - lhh) / 2f, x + lw, (bh + lhh) / 2f)
            if (spec.platform != null && PlatformLogos.mono(spec.platform)) {
                p.colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
            }
            p.color = 0xFFFFFFFF.toInt() // (opaque: a paint's alpha applies to the picture too)
            c.drawBitmap(logo, null, dst, p); p.colorFilter = null
            x += lw + w * 0.03f
        }
        if (logo == null && st.name.isNotEmpty()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF2F4F8.toInt(); textSize = bh * 0.42f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.08f
            }
            c.drawText(st.name.uppercase(), x, bh / 2f - (tp.ascent() + tp.descent()) / 2f, tp)
        }
        return bh
    }

    /** [art] cut to [r] (never stretched), fading into the card at its foot (or its right edge). */
    private fun hero(c: Canvas, art: Bitmap, r: RectF, into: Int, sideFade: Boolean = false) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(art, cropRect(art, r.width(), r.height(), 0.4f), r, p)
        val clear = into and 0x00FFFFFF
        p.shader = if (sideFade) LinearGradient(r.left + r.width() * 0.55f, 0f, r.right, 0f, clear, into, Shader.TileMode.CLAMP)
        else LinearGradient(0f, r.top + r.height() * 0.45f, 0f, r.bottom, clear, into, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.18f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.18f, p)
        p.shader = null
    }

    /** The title in big type (as many lines as fit, up to 3), with a short accent rule under it. */
    private fun title(c: Canvas, title: String?, r: RectF, accent: Int, bottomAligned: Boolean, big: Boolean = false) {
        val t = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (r.height() < 10f || r.width() < 20f) return
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setShadowLayer(r.width() * 0.012f, 0f, r.width() * 0.004f, 0xCC000000.toInt())
        }
        var size = r.width() * (if (big) 0.13f else 0.095f)
        val min = r.width() * 0.045f
        var layout: StaticLayout
        while (true) {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(t, 0, t.length, tp, r.width().toInt())
                .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 0.92f)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            val rule = size * 0.45f
            if ((layout.height + rule <= r.height() && !ellipsized(layout)) || size <= min) break
            size *= 0.92f
        }
        val ruleH = (size * 0.09f).coerceAtLeast(2f)
        val total = layout.height + size * 0.45f
        val y0 = if (bottomAligned) r.bottom - total else r.top
        c.save(); c.translate(r.left, y0); layout.draw(c); c.restore()
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val ry = y0 + layout.height + size * 0.2f
        c.drawRoundRect(RectF(r.left, ry, r.left + r.width() * 0.22f, ry + ruleH), ruleH, ruleH, rp)
    }

    private fun ellipsized(l: StaticLayout): Boolean = (0 until l.lineCount).any { l.getEllipsisCount(it) > 0 }

    /** A screenshot in a thin light frame with rounded corners and a soft shadow. */
    private fun shot(c: Canvas, b: Bitmap, r: RectF, w: Int) {
        val rad = w * 0.012f
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.color = 0x66000000; c.drawRoundRect(RectF(r.left + 2f, r.top + 3f, r.right + 2f, r.bottom + 3f), rad, rad, p)
        p.color = 0xDDFFFFFF.toInt(); c.drawRoundRect(RectF(r.left - 2f, r.top - 2f, r.right + 2f, r.bottom + 2f), rad + 2f, rad + 2f, p)
        c.save()
        val clip = android.graphics.Path().apply { addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        p.color = 0xFFFFFFFF.toInt()
        c.drawBitmap(b, cropRect(b, r.width(), r.height(), 0.5f), r, p)
        c.restore()
    }

    /** [text] set in [r] at [size] (smaller for a long text, down to a readable minimum), "…" where it runs out. */
    private fun paragraph(c: Canvas, text: String, r: RectF, size0: Float, ink: Int) {
        if (r.height() < size0 * 2.2f || r.width() < 40f) return
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        var size = size0
        var lines: Int
        var layout: StaticLayout
        while (true) {
            tp.textSize = size
            lines = (r.height() / (tp.fontSpacing * 1.12f)).toInt().coerceAtLeast(1)
            layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, r.width().toInt())
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.12f).build()
            if (!ellipsized(layout) || size <= size0 * 0.82f) break
            size *= 0.95f
        }
        c.save(); c.translate(r.left, r.top); layout.draw(c); c.restore()
    }

    private fun detailLines(spec: PrintedBack): List<String> {
        val out = ArrayList<String>()
        val who = listOfNotNull(spec.publisher?.let { "Publisher: $it" }, spec.developer?.takeIf { it != spec.publisher }?.let { "Developer: $it" })
        out += who
        val misc = listOfNotNull(spec.year?.let { "Released $it" }, spec.players?.let { "Players: $it" })
        if (misc.isNotEmpty()) out += misc.joinToString("   ·   ")
        if (spec.features.isNotEmpty()) out += spec.features.take(4).joinToString("  ·  ")
        return out
    }

    private fun footerHeight(w: Int, h: Int, spec: PrintedBack): Float {
        val lines = detailLines(spec).size
        val strip = h * 0.034f
        if (lines == 0 && spec.rating == null) return strip
        return strip + (w * 0.034f * 1.35f * lines.coerceAtLeast(2)) + h * 0.03f
    }

    /** The details known for this game, its rating badge, and the line saying it's a Voltcap case. */
    private fun footer(c: Canvas, w: Int, h: Int, top: Float, spec: PrintedBack, st: Style, accent: Int) {
        val m = w * 0.055f
        val strip = h * 0.034f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val lines = detailLines(spec)
        if (lines.isNotEmpty() || spec.rating != null) {
            p.color = 0x40000000; c.drawRect(0f, top, w.toFloat(), h - strip, p)
            p.color = accent and 0x00FFFFFF or 0x99000000.toInt(); c.drawRect(m, top, w - m, top + 2f, p)
            val badge = spec.rating?.let { ratingBadge(c, it, w - m, top + h * 0.015f, (h - strip - top) - h * 0.03f, w) } ?: 0f
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6DDE2EA.toInt(); textSize = w * 0.026f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
            val maxW = (w - 2 * m - badge - w * 0.03f).coerceAtLeast(40f)
            var y = top + h * 0.015f - tp.ascent()
            for (line in lines) {
                val t = TextUtils.ellipsize(line, tp, maxW, TextUtils.TruncateAt.END).toString()
                c.drawText(t, m, y, tp)
                y += tp.fontSpacing * 1.3f
            }
        }
        // The foot: "a Voltcap case" — a custom case for a digital / unboxed game, not an official release.
        p.color = 0xFF07080A.toInt(); c.drawRect(0f, h - strip, w.toFloat(), h.toFloat(), p)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99C8CDD6.toInt(); textSize = strip * 0.42f; letterSpacing = 0.04f }
        val note = when (spec.platform) {
            "Steam", "Android", "App" -> "VOLTCAP COLLECTOR'S CASE  ·  NOT AN OFFICIAL RETAIL RELEASE"
            else -> "VOLTCAP PRINTED BACK  ·  NOT AN ORIGINAL PACKAGING SCAN"
        }
        c.drawText(TextUtils.ellipsize(note, tp, w - 2 * m, TextUtils.TruncateAt.END).toString(), m, h - strip / 2f - (tp.ascent() + tp.descent()) / 2f, tp)
    }

    /**
     * A rating as plain text on a neutral plate — the body's name over its rating ("USK" / "16"), right-aligned at [right].
     * Not the body's own symbol (those are licensed to publishers). Returns its width.
     */
    private fun ratingBadge(c: Canvas, r: com.shelf.launcher.data.AgeRating, right: Float, top: Float, room: Float, w: Int): Float {
        val size = (w * 0.115f).coerceAtMost(room).coerceAtLeast(w * 0.07f)
        val bw = size * (if (r.value.length > 2) 1.15f else 0.9f)
        val rect = RectF(right - bw, top, right, top + size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFF4F5F7.toInt(); c.drawRoundRect(rect, size * 0.1f, size * 0.1f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = size * 0.03f; p.color = 0xFF2A2D33.toInt()
        c.drawRoundRect(RectF(rect.left + size * 0.05f, rect.top + size * 0.05f, rect.right - size * 0.05f, rect.bottom - size * 0.05f), size * 0.07f, size * 0.07f, p)
        p.style = Paint.Style.FILL
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1C20.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD) }
        tp.textSize = size * 0.2f
        c.drawText(r.system, rect.centerX(), rect.top + size * 0.29f, tp)
        tp.textSize = size * 0.5f
        while (tp.measureText(r.value) > bw * 0.84f && tp.textSize > size * 0.25f) tp.textSize -= 1f
        c.drawText(r.value, rect.centerX(), rect.top + size * 0.82f, tp)
        return bw
    }

    /** A faint printed grain: fine diagonal lines across the card. */
    private fun grain(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0AFFFFFF; strokeWidth = 1f }
        var x = -h.toFloat()
        val step = w * 0.018f
        while (x < w) { c.drawLine(x, h.toFloat(), x + h, 0f, p); x += step }
    }

    /** The part of [b] with the shape [w] × [h] (its middle across, [vy] of the way down), so nothing is stretched. */
    private fun cropRect(b: Bitmap, w: Float, h: Float, vy: Float): Rect {
        if (w <= 0f || h <= 0f) return Rect(0, 0, b.width, b.height)
        val want = w / h; val have = b.width.toFloat() / b.height
        return if (have > want) {
            val cw = (b.height * want).toInt().coerceAtLeast(1); Rect((b.width - cw) / 2, 0, (b.width - cw) / 2 + cw, b.height)
        } else {
            val ch = (b.width / want).toInt().coerceAtLeast(1); val y0 = ((b.height - ch) * vy).toInt(); Rect(0, y0, b.width, y0 + ch)
        }
    }
}
