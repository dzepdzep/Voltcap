package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/*
 * Update 189: cases that open. Tap a case in the game window (or click the right stick) and its lid swings open on the
 * hinge so you see inside — the tray with the disc on its hub, the card in its slot, the manual in its clips, the
 * booklet in a jewel case's lid — then the case moves aside and the disc / cartridge comes out to the middle
 * (MediaInspectView.kt). The insides are drawn here, per kind of case and in its real colour:
 *  - keep cases (PS2/3/4/5, GameCube, Wii, Wii U, Switch, Xbox…): the disc hub (or the card slot), the manual on the
 *    other side in its clips;
 *  - jewel cases (PS1 with its black tray, Dreamcast, Saturn, Sega CD…): the tray's hub rosette and finger notches, the
 *    booklet in the lid;
 *  - card cases (DS, 3DS, Vita, PSP): the slot with the card in it;
 *  - cardboard boxes (NES, SNES, N64, Game Boy…): no hinge — the cartridge slides out of the top.
 */

internal object CaseInside {
    enum class Kind { HUB, JEWEL, SLOT, BOX }

    fun kindOf(platform0: String?): Kind = when (val p = LookStyles.casePlatform(platform0)) {
        "NDS", "3DS", "PSVita", "PSP", "Switch" -> Kind.SLOT
        else -> when (caseStyleOf(p)) {
            CASE_JEWEL, CASE_JEWEL_BLACK -> Kind.JEWEL
            CASE_CARD -> Kind.BOX
            else -> Kind.HUB
        }
    }

    /** The case's inside plastic, its real colour. */
    fun plastic(platform0: String?): Int = when (LookStyles.casePlatform(platform0)) {
        "PS3" -> 0xFF1F2733.toInt()
        "PS4", "WiiU" -> 0xFF2458B4.toInt()
        "PS5" -> 0xFFE8EBF0.toInt()
        "Wii", "3DS" -> 0xFFEEEFF2.toInt()
        "Switch" -> 0xFFCF2229.toInt()
        "Xbox360", "XboxOne", "XboxSeries" -> 0xFF2C8A3C.toInt()
        "NDS" -> 0xFFC9CDD4.toInt()
        "PSVita" -> 0xFF5A8CCB.toInt()
        "Saturn", "SegaCD", "PCEngineCD", "3DO", "CDi" -> 0xFF2B2D32.toInt()
        else -> 0xFF17181B.toInt()
    }

    /** Where the disc / card lies on the tray (u0, v0, u1, v1 of the tray), for a case of [aspect] (width / height). */
    fun itemRect(kind: Kind, aspect: Float, itemAspect: Float): FloatArray {
        val a = aspect.coerceAtLeast(0.2f)
        return when (kind) {
            Kind.HUB, Kind.JEWEL -> {
                // A round disc: its diameter against the case's shorter side.
                val dia = if (kind == Kind.JEWEL) 0.86f else 0.84f
                val dw = dia * minOf(1f, 1f / a) // as a share of the width
                val dh = dia * minOf(1f, a)      // as a share of the height
                floatArrayOf(0.5f - dw / 2f, 0.5f - dh / 2f, 0.5f + dw / 2f, 0.5f + dh / 2f)
            }
            Kind.SLOT -> {
                // A card in its slot in the upper middle: about a third of the case's width.
                val cw = 0.34f
                val ch = (cw * a / itemAspect.coerceIn(0.4f, 2.5f)).coerceAtMost(0.42f)
                floatArrayOf(0.5f - cw / 2f, 0.30f - ch / 2f, 0.5f + cw / 2f, 0.30f + ch / 2f)
            }
            Kind.BOX -> floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f)
        }
    }

    private val cache = android.util.LruCache<String, Bitmap>(12)

    /** The tray side (the case's back half, seen from inside). */
    fun tray(platform: String?, aspect: Float, itemAspect: Float): Bitmap {
        val kind = kindOf(platform)
        val col = plastic(platform)
        val key = "t|$kind|$col|${(aspect * 100).toInt()}|${(itemAspect * 100).toInt()}"
        cache.get(key)?.let { return it }
        val h = 512
        val w = (h * aspect).toInt().coerceIn(160, 1200)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        plasticBase(c, w, h, col, p)
        val r = itemRect(kind, aspect, itemAspect)
        val ir = RectF(r[0] * w, r[1] * h, r[2] * w, r[3] * h)
        val light = isLight(col)
        when (kind) {
            Kind.HUB, Kind.JEWEL -> {
                val cx = ir.centerX(); val cy = ir.centerY(); val rad = ir.width() / 2f
                if (kind == Kind.JEWEL) {
                    // The tray's raised disc seat and the two finger notches either side of it.
                    p.style = Paint.Style.FILL
                    p.color = shade(col, if (light) 0.9f else 1.35f)
                    c.drawCircle(cx, cy, rad * 1.01f, p)
                    p.color = shade(col, 0.55f)
                    for (sx in floatArrayOf(-1f, 1f)) c.drawCircle(cx + sx * rad * 0.99f, cy, rad * 0.16f, p)
                    p.color = shade(col, if (light) 0.85f else 1.2f)
                    c.drawCircle(cx, cy, rad * 0.97f, p)
                } else {
                    // A keep case's shallow ring round the disc and the foam-like pads.
                    p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.006f
                    p.color = shade(col, if (light) 0.82f else 1.6f)
                    c.drawCircle(cx, cy, rad * 1.02f, p)
                    p.style = Paint.Style.FILL
                }
                hub(c, cx, cy, rad, col, p)
            }
            Kind.SLOT -> {
                // The card's recess with a thumb notch under it.
                p.style = Paint.Style.FILL
                p.color = shade(col, 0.62f)
                val rr = h * 0.02f
                c.drawRoundRect(RectF(ir.left - h * 0.012f, ir.top - h * 0.012f, ir.right + h * 0.012f, ir.bottom + h * 0.012f), rr, rr, p)
                p.color = shade(col, 0.5f)
                c.drawCircle(ir.centerX(), ir.bottom + h * 0.012f, ir.width() * 0.16f, p)
                // A second, flat recess lower down (where a card case keeps its little leaflet).
                p.color = shade(col, 0.85f)
                val lw = w * 0.5f; val lt = h * 0.6f
                c.drawRoundRect(RectF((w - lw) / 2f, lt, (w + lw) / 2f, lt + h * 0.24f), rr, rr, p)
            }
            Kind.BOX -> Unit
        }
        cache.put(key, b)
        return b
    }

    /** The lid side, seen from inside: the manual in its clips (keep / card cases) or the booklet's back (jewel). */
    fun lid(platform: String?, aspect: Float, cover: Bitmap?): Bitmap {
        val kind = kindOf(platform)
        val col = plastic(platform)
        val key = "l|$kind|$col|${(aspect * 100).toInt()}|${cover?.let { System.identityHashCode(it) } ?: 0}"
        cache.get(key)?.let { return it }
        val h = 512
        val w = (h * aspect).toInt().coerceIn(160, 1200)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (kind == Kind.JEWEL) {
            // Clear plastic over the booklet: the booklet's back page (its cover, faded like a printed back), a little
            // smaller than the lid, with the lid's two tabs on the opening side.
            c.drawColor(0xFF0E0F12.toInt())
            val m = h * 0.035f
            val page = RectF(m, m, w - m, h - m)
            if (cover != null) {
                p.alpha = 255
                p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
                    setSaturation(0.35f)
                    postConcat(android.graphics.ColorMatrix().apply { setScale(0.45f, 0.45f, 0.48f, 1f) })
                })
                c.drawBitmap(cover, null, page, p)
                p.colorFilter = null
            } else { p.color = 0xFF2A2C33.toInt(); c.drawRect(page, p) }
            // Faint printed lines (the back page's text).
            p.color = 0x22FFFFFF
            p.strokeWidth = h * 0.006f
            var y = h * 0.62f
            while (y < h * 0.9f) { c.drawLine(w * 0.12f, y, w * (0.55f + (y * 7 % 30) / 100f), y, p); y += h * 0.035f }
            tabs(c, w, h, 0xFF9AA0AB.toInt(), p, onLeft = true)
            glare(c, w, h, p)
        } else {
            plasticBase(c, w, h, col, p)
            // The manual, its front facing you, held by two clips.
            val mw = w * 0.86f; val mh = h * 0.86f
            val page = RectF((w - mw) / 2f, (h - mh) / 2f, (w + mw) / 2f, (h + mh) / 2f)
            p.color = 0x55000000
            c.drawRect(RectF(page.left + h * 0.008f, page.top + h * 0.01f, page.right + h * 0.008f, page.bottom + h * 0.01f), p)
            if (cover != null) c.drawBitmap(cover, null, page, Paint(Paint.FILTER_BITMAP_FLAG))
            else { p.color = 0xFFE9E9EC.toInt(); c.drawRect(page, p) }
            tabs(c, w, h, shade(col, if (isLight(col)) 0.85f else 1.5f), p, onLeft = true)
        }
        cache.put(key, b)
        return b
    }

    /**
     * The disc / card lying in the case: the game's own picture of it when there is one ([real]), else drawn — a disc
     * with the cover as its label, or a card with the cover on it.
     */
    fun item(kind: Kind, real: Bitmap?, label: Bitmap?, itemAspect: Float, tone: Int): Bitmap? {
        val key = "i|$kind|${real?.let { System.identityHashCode(it) } ?: 0}|${label?.let { System.identityHashCode(it) } ?: 0}|${(itemAspect * 100).toInt()}"
        cache.get(key)?.let { return it }
        val round = kind == Kind.HUB || kind == Kind.JEWEL
        val out: Bitmap = if (real != null) real else if (round) {
            val s = 512
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val r = s / 2f
            p.shader = RadialGradient(r, r, r, intArrayOf(0xFFDADDE3.toInt(), 0xFFB5B9C2.toInt(), 0xFF8E939C.toInt()), null, Shader.TileMode.CLAMP)
            c.drawCircle(r, r, r, p)
            p.shader = null
            if (label != null) {
                c.save()
                val clip = Path().apply { addCircle(r, r, r * 0.97f, Path.Direction.CW); addCircle(r, r, r * 0.34f, Path.Direction.CCW) }
                c.clipPath(clip)
                // The cover cut square, filling the label.
                val sq = minOf(label.width, label.height)
                val src = android.graphics.Rect((label.width - sq) / 2, (label.height - sq) / 2, (label.width + sq) / 2, (label.height + sq) / 2)
                c.drawBitmap(label, src, RectF(0f, 0f, s.toFloat(), s.toFloat()), p)
                c.restore()
            }
            p.color = 0xFFCBCFD6.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.012f
            c.drawCircle(r, r, r * 0.34f, p)
            p.style = Paint.Style.FILL
            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            c.drawCircle(r, r, r * 0.12f, p)
            p.xfermode = null
            b
        } else {
            val h = 400
            val w = (h * itemAspect.coerceIn(0.4f, 2.5f)).toInt()
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            p.color = tone
            val rr = h * 0.06f
            c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), rr, rr, p)
            if (label != null) {
                val m = w * 0.08f
                c.drawBitmap(label, null, RectF(m, h * 0.1f, w - m, h * 0.78f), p)
            }
            p.color = 0x33000000
            for (i in 0 until 6) c.drawRect(w * (0.2f + i * 0.11f), h * 0.86f, w * (0.26f + i * 0.11f), h.toFloat(), p)
            b
        }
        cache.put(key, out)
        return out
    }

    private fun plasticBase(c: Canvas, w: Int, h: Int, col: Int, p: Paint) {
        val light = isLight(col)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), shade(col, if (light) 1.0f else 1.25f), shade(col, if (light) 0.86f else 0.85f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // The raised rim round the inside, lit along its top.
        p.style = Paint.Style.STROKE
        val m = h * 0.025f
        p.strokeWidth = h * 0.012f
        p.color = shade(col, if (light) 0.8f else 0.6f)
        c.drawRect(m, m, w - m, h - m, p)
        p.strokeWidth = h * 0.004f
        p.color = if (light) 0x66FFFFFF else 0x33FFFFFF
        c.drawLine(m, m - h * 0.006f, w - m, m - h * 0.006f, p)
        p.style = Paint.Style.FILL
    }

    /** A disc hub: a ring of small teeth round a button. */
    private fun hub(c: Canvas, cx: Float, cy: Float, rad: Float, col: Int, p: Paint) {
        val light = isLight(col)
        p.style = Paint.Style.FILL
        p.color = shade(col, if (light) 0.8f else 1.7f)
        c.drawCircle(cx, cy, rad * 0.2f, p)
        p.color = shade(col, if (light) 0.7f else 0.7f)
        c.drawCircle(cx, cy, rad * 0.165f, p)
        val teeth = 12
        p.color = shade(col, if (light) 0.92f else 2.1f)
        for (i in 0 until teeth) {
            val a = (i * 2.0 * Math.PI / teeth)
            val x = cx + kotlin.math.cos(a).toFloat() * rad * 0.115f
            val y = cy + kotlin.math.sin(a).toFloat() * rad * 0.115f
            c.drawCircle(x, y, rad * 0.024f, p)
        }
        p.color = shade(col, if (light) 0.95f else 1.45f)
        c.drawCircle(cx, cy, rad * 0.075f, p)
    }

    /** Two small tabs on one side (the clips holding a manual / booklet). */
    private fun tabs(c: Canvas, w: Int, h: Int, col: Int, p: Paint, onLeft: Boolean) {
        p.style = Paint.Style.FILL
        p.color = col
        val tw = w * 0.07f; val th = h * 0.06f
        val x0 = if (onLeft) w * 0.035f else w - w * 0.035f - tw
        for (y in floatArrayOf(h * 0.12f, h * 0.82f)) c.drawRoundRect(RectF(x0, y, x0 + tw, y + th), th * 0.3f, th * 0.3f, p)
    }

    /** A clear lid's soft glare. */
    private fun glare(c: Canvas, w: Int, h: Int, p: Paint) {
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x18FFFFFF), floatArrayOf(0f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
    }

    private fun isLight(col: Int): Boolean =
        (AColor.red(col) * 299 + AColor.green(col) * 587 + AColor.blue(col) * 114) / 1000 > 150

    /** [col] times [k] (above 1 lightens, also from black). */
    fun shade(col: Int, k: Float): Int {
        fun ch(v: Int): Int = if (k <= 1f) (v * k).toInt() else (v + (255 - v) * ((k - 1f) * 0.18f) + v * (k - 1f) * 0.3f).toInt()
        return AColor.argb(AColor.alpha(col), ch(AColor.red(col)).coerceIn(0, 255), ch(AColor.green(col)).coerceIn(0, 255), ch(AColor.blue(col)).coerceIn(0, 255))
    }
}

/**
 * Update 189: an open case in perspective (plain maths, like CaseDraw): the tray half with its walls, the disc / card on
 * it, and the lid swung [open] (0 closed … 1 wide open) round the hinge at the spine. Faces are sorted far to near.
 */
internal object CaseOpenDraw {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val matrix = android.graphics.Matrix()
    private val path = Path()
    private val src = FloatArray(8)

    private class Quad {
        val pts = FloatArray(8)
        var depth = 0f
        var bmp: Bitmap? = null
        var color = 0
        var light = 1f
        var ok = true
        /** Flip the picture left-right (a face seen from its back). */
        var edge = false
    }
    private val quads = Array(16) { Quad() }

    @Synchronized
    fun draw(
        c: Canvas, cx: Float, cy: Float, w: Float, h: Float, d: Float, yaw: Float, pitch: Float, open: Float,
        front: Bitmap?, spine: Bitmap, back: Bitmap?, tray: Bitmap, lidInside: Bitmap, item: Bitmap?, itemRect: FloatArray,
        showItem: Boolean, plastic: Int, light: Float
    ) {
        val ty = Math.toRadians(yaw.toDouble()); val tp = Math.toRadians(pitch.toDouble())
        val cy0 = kotlin.math.cos(ty).toFloat(); val sy0 = kotlin.math.sin(ty).toFloat()
        val cp = kotlin.math.cos(tp).toFloat(); val sp = kotlin.math.sin(tp).toFloat()
        val focal = h * 3.4f
        val hw = w / 2f; val hh = h / 2f; val hd = d / 2f
        var n = 0
        // Projects (x, y, z) into [q] at corner [i], adding to its depth.
        fun put(q: Quad, i: Int, x: Float, y: Float, z: Float) {
            val x1 = x * cy0 + z * sy0
            val z1 = -x * sy0 + z * cy0
            val y2 = y * cp + z1 * sp
            val z2 = -y * sp + z1 * cp
            val k = focal - z2
            if (k <= 1f) { q.ok = false; return }
            val s = focal / k
            q.pts[i * 2] = cx + x1 * s
            q.pts[i * 2 + 1] = cy + y2 * s
            q.depth += z2 / 4f
        }
        fun next(): Quad = quads[n++].also { it.depth = 0f; it.bmp = null; it.color = 0; it.ok = true; it.light = 1f; it.edge = false }
        fun area(q: Quad): Float {
            var a = 0f
            for (i in 0 until 4) { val j = (i + 1) % 4; a += q.pts[i * 2] * q.pts[j * 2 + 1] - q.pts[j * 2] * q.pts[i * 2 + 1] }
            return a
        }
        val floor = -hd + d * 0.12f
        // The tray (the back half's inside).
        next().also { q -> q.bmp = tray; q.light = 0.95f
            put(q, 0, -hw, -hh, floor); put(q, 1, hw, -hh, floor); put(q, 2, hw, hh, floor); put(q, 3, -hw, hh, floor) }
        // The disc / card lying in it.
        if (showItem && item != null) next().also { q -> q.bmp = item
            val z = floor + d * 0.18f
            val x0 = -hw + itemRect[0] * w; val x1 = -hw + itemRect[2] * w
            val y0 = -hh + itemRect[1] * h; val y1 = -hh + itemRect[3] * h
            put(q, 0, x0, y0, z); put(q, 1, x1, y0, z); put(q, 2, x1, y1, z); put(q, 3, x0, y1, z) }
        // The walls: each drawn on the side that faces you (outside: the spine picture / plastic; inside: plastic).
        val wall = CaseInside.shade(plastic, 0.8f)
        // top (outside faces up), bottom, open edge, spine.
        next().also { q -> q.color = wall; q.light = 0.75f
            put(q, 0, -hw, -hh, -hd); put(q, 1, hw, -hh, -hd); put(q, 2, hw, -hh, hd); put(q, 3, -hw, -hh, hd) }
        next().also { q -> q.color = wall; q.light = 0.6f
            put(q, 0, -hw, hh, hd); put(q, 1, hw, hh, hd); put(q, 2, hw, hh, -hd); put(q, 3, -hw, hh, -hd) }
        next().also { q -> q.color = wall; q.light = 0.7f
            put(q, 0, hw, -hh, hd); put(q, 1, hw, -hh, -hd); put(q, 2, hw, hh, -hd); put(q, 3, hw, hh, hd) }
        next().also { q -> q.bmp = spine; q.light = 0.85f
            put(q, 0, -hw, -hh, -hd); put(q, 1, -hw, -hh, hd); put(q, 2, -hw, hh, hd); put(q, 3, -hw, hh, -hd) }
        // The back, from behind.
        next().also { q -> q.bmp = back; q.color = CaseInside.shade(plastic, 0.7f); q.light = 0.8f
            put(q, 0, hw, -hh, -hd); put(q, 1, -hw, -hh, -hd); put(q, 2, -hw, hh, -hd); put(q, 3, hw, hh, -hd) }
        // The lid, swung round the hinge (x = -hw, z = +hd): its outside (the cover) and its inside.
        val th = Math.toRadians((open.coerceIn(0f, 1f) * 178f).toDouble())
        val ct = kotlin.math.cos(th).toFloat(); val st = kotlin.math.sin(th).toFloat()
        fun lx(u: Float) = -hw + u * w * ct
        fun lz(u: Float) = hd + u * w * st
        next().also { q -> q.bmp = front; q.color = CaseInside.shade(plastic, 0.9f)
            put(q, 0, lx(0f), -hh, lz(0f)); put(q, 1, lx(1f), -hh, lz(1f)); put(q, 2, lx(1f), hh, lz(1f)); put(q, 3, lx(0f), hh, lz(0f)) }
        next().also { q -> q.bmp = lidInside; q.light = 0.97f
            put(q, 0, lx(1f), -hh, lz(1f)); put(q, 1, lx(0f), -hh, lz(0f)); put(q, 2, lx(0f), hh, lz(0f)); put(q, 3, lx(1f), hh, lz(1f)) }

        // Far to near.
        val order = (0 until n).filter { quads[it].ok }.sortedBy { quads[it].depth }
        for (i in order) {
            val q = quads[i]
            val a = area(q)
            val isWall = q.bmp == null && q.color == wall
            // The walls show their inside when their outside faces away (they're open boxes): draw either way, darker inside.
            if (a <= 0f && !isWall) continue
            val lit = (q.light * light * (if (isWall && a <= 0f) 0.85f else 1f)).coerceIn(0f, 1f)
            val b = q.bmp
            if (b != null) {
                src[0] = 0f; src[1] = 0f; src[2] = b.width.toFloat(); src[3] = 0f
                src[4] = b.width.toFloat(); src[5] = b.height.toFloat(); src[6] = 0f; src[7] = b.height.toFloat()
                matrix.reset()
                if (matrix.setPolyToPoly(src, 0, q.pts, 0, 4)) {
                    paint.colorFilter = lightFilter(lit)
                    c.save(); c.concat(matrix); c.drawBitmap(b, 0f, 0f, paint); c.restore()
                    paint.colorFilter = null
                }
            } else {
                fill.color = CaseInside.shade(q.color, lit)
                path.reset()
                path.moveTo(q.pts[0], q.pts[1]); path.lineTo(q.pts[2], q.pts[3]); path.lineTo(q.pts[4], q.pts[5]); path.lineTo(q.pts[6], q.pts[7]); path.close()
                c.drawPath(path, fill)
            }
            // A thin lit edge round each face (the plastic's rim).
            line.strokeWidth = (w / 300f).coerceIn(0.8f, 2.5f)
            line.color = 0x40FFFFFF
            path.reset()
            path.moveTo(q.pts[0], q.pts[1]); path.lineTo(q.pts[2], q.pts[3]); path.lineTo(q.pts[4], q.pts[5]); path.lineTo(q.pts[6], q.pts[7]); path.close()
            c.drawPath(path, line)
        }
    }

    private val filters = arrayOfNulls<android.graphics.ColorFilter>(256)
    private fun lightFilter(l: Float): android.graphics.ColorFilter? {
        if (l >= 0.995f) return null
        val v = (l.coerceIn(0f, 1f) * 255f).toInt()
        return filters[v] ?: android.graphics.LightingColorFilter(AColor.rgb(v, v, v), 0).also { filters[v] = it }
    }
}
