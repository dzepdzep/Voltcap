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

    /** Before update 191 (no catalogue entry): by the console alone. */
    fun kindOf(platform0: String?): Kind = when (val p = LookStyles.casePlatform(platform0)) {
        "NDS", "3DS", "PSVita", "PSP", "Switch" -> Kind.SLOT
        else -> when (caseStyleOf(p)) {
            CASE_JEWEL, CASE_JEWEL_BLACK -> Kind.JEWEL
            CASE_CARD -> Kind.BOX
            else -> Kind.HUB
        }
    }

    /**
     * Update 191: what kind of inside a case has — from its real case ([spec], CaseCatalog.kt), and from what lies in it
     * ([itemRound]: a disc or not). What lies in it decides: a disc case holding a cartridge (Voltcap's own case of an
     * Android game with your cartridge on it) shows a cartridge in a recess, not a disc that turns into a cartridge on
     * its way out.
     */
    fun kindFor(spec: CaseSpec?, platform: String?, itemRound: Boolean?): Kind {
        val base = if (spec != null) when {
            spec.cardboard -> Kind.BOX
            spec.media.round -> if (spec.shell == CaseShell.JEWEL) Kind.JEWEL else Kind.HUB
            else -> Kind.SLOT
        } else kindOf(platform)
        if (base == Kind.BOX || itemRound == null) return base
        return when {
            itemRound && base == Kind.SLOT -> if (spec?.shell == CaseShell.JEWEL) Kind.JEWEL else Kind.HUB
            !itemRound && (base == Kind.HUB || base == Kind.JEWEL) -> Kind.SLOT
            else -> base
        }
    }

    /** Update 215: cases whose lid shows the insert's plain back when the game has no manual (no manual in the real case). */
    private val INSERT_BACK_ONLY = setOf("Switch", "Switch2", "Xbox360")

    /** The case's inside plastic, its real colour (before update 191, by console). */
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

    /**
     * Update 191: the inside's colour from the real case: a see-through keep case shows its plastic over the paper
     * insert's white back (a PS4 case light blue inside, a Switch case near white), a jewel case its tray.
     */
    fun plastic(spec: CaseSpec?, platform: String?): Int {
        if (spec == null) return plastic(platform)
        return when (spec.shell) {
            CaseShell.KEEP, CaseShell.CARDCASE -> mix(0xFFDCDFE4.toInt(), spec.plastic, (0.25f + 0.75f * spec.opacity).coerceAtMost(1f))
            CaseShell.CLAMSHELL -> spec.plastic
            else -> spec.tray
        }
    }

    /** [a] towards [b] by [k] (0…1). */
    fun mix(a: Int, b: Int, k: Float): Int {
        val t = k.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return AColor.argb(255, ch(AColor.red(a), AColor.red(b)), ch(AColor.green(a), AColor.green(b)), ch(AColor.blue(a), AColor.blue(b)))
    }

    /**
     * Where the disc / card lies (u0, v0, u1, v1 of its side's panel), for a case of [aspect] (width / height). Update
     * 191: at its real size and place in the real case when there's one ([spec]): an 8 cm GameCube disc smaller, a Switch
     * card low on the right, a DS card at the top. Xbox One / Series: on the lid's inside (see [CaseSpec.mediaLeft]).
     */
    fun itemRect(kind: Kind, aspect: Float, itemAspect: Float, spec: CaseSpec? = null): FloatArray {
        val a = aspect.coerceAtLeast(0.2f)
        if (spec != null && kind != Kind.BOX) {
            val round = kind == Kind.HUB || kind == Kind.JEWEL
            val mm = when {
                round && spec.media.round -> if (spec.media == CaseHeld.DISC80) 80f else 120f
                round -> 120f
                !spec.media.round -> spec.mediaWMm
                // (A cartridge in a disc case — your cartridge on Voltcap's own case: about a third of its width.)
                else -> spec.wMm * 0.34f
            }
            val cw = (mm / spec.wMm).coerceIn(0.08f, 0.94f)
            val ch = if (round) (cw * a).coerceAtMost(0.96f) else (cw * a / itemAspect.coerceIn(0.4f, 2.5f)).coerceAtMost(0.9f)
            val cx = spec.mediaU.coerceIn(cw / 2f, 1f - cw / 2f)
            val cy = spec.mediaV.coerceIn(ch / 2f, 1f - ch / 2f)
            return floatArrayOf(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
        }
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

    private fun specKey(spec: CaseSpec?): String = spec?.let { "${it.platform}.${it.region}.${it.shell}" } ?: "-"

    /** The tray side (the case's back half, seen from inside). */
    fun tray(platform: String?, aspect: Float, itemAspect: Float, spec: CaseSpec? = null, kind0: Kind? = null, umd: Boolean = false): Bitmap {
        val kind = kind0 ?: kindFor(spec, platform, null)
        val col = plastic(spec, platform)
        val key = "t|$kind|$col|${(aspect * 100).toInt()}|${(itemAspect * 100).toInt()}|${specKey(spec)}|$umd"
        cache.get(key)?.let { return it }
        val h = 512
        val w = (h * aspect).toInt().coerceIn(160, 1200)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        plasticBase(c, w, h, col, p)
        val light = isLight(col)
        if (spec?.mediaLeft == true) {
            // Update 191 (Xbox One / Series): the disc is on the other side; this side only has its paper clips.
            tabs(c, w, h, shade(col, if (light) 0.85f else 1.5f), p, onLeft = true)
        } else {
            mediaBed(c, w, h, kind, aspect, itemAspect, spec, col, p, umd)
        }
        // Update 189: the opening edge's thumb grip (keep and card cases have one; jewel cases don't): a raised strip
        // along the right edge's middle with a dip in it for your thumb. (Update 191: where the real case has one.)
        if (spec?.grip ?: (kind == Kind.HUB || kind == Kind.SLOT)) {
            p.style = Paint.Style.FILL
            val gx = w - h * 0.07f
            val top = h * 0.3f; val bot = h * 0.7f
            p.color = shade(col, if (light) 0.8f else 0.55f)
            c.drawRoundRect(RectF(gx + h * 0.006f, top + h * 0.008f, w - h * 0.02f + h * 0.006f, bot + h * 0.008f), h * 0.02f, h * 0.02f, p)
            p.color = shade(col, if (light) 0.97f else 1.5f)
            c.drawRoundRect(RectF(gx, top, w - h * 0.02f, bot), h * 0.02f, h * 0.02f, p)
            p.color = shade(col, if (light) 0.86f else 1.15f)
            c.drawOval(RectF(gx + h * 0.008f, h * 0.43f, w - h * 0.028f, h * 0.57f), p)
        }
        cache.put(key, b)
        return b
    }

    /** What holds the disc / card: the hub and its ridges, a jewel tray's seat, a card's recess and the case's other slots. */
    private fun mediaBed(c: Canvas, w: Int, h: Int, kind: Kind, aspect: Float, itemAspect: Float, spec: CaseSpec?, col: Int, p: Paint, umd: Boolean = false) {
        val light = isLight(col)
        val r = itemRect(kind, aspect, itemAspect, spec)
        val ir = RectF(r[0] * w, r[1] * h, r[2] * w, r[3] * h)
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
                    // A keep case: three curved ridges hold the disc's edge (upper left, right, lower left), as moulded
                    // in the real tray — not a full ring.
                    p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.022f; p.strokeCap = Paint.Cap.ROUND
                    val box = RectF(cx - rad * 1.04f, cy - rad * 1.04f, cx + rad * 1.04f, cy + rad * 1.04f)
                    for ((start, sweep) in listOf(200f to 38f, 330f to 60f, 122f to 38f)) {
                        p.color = shade(col, if (light) 0.78f else 0.55f)
                        c.drawArc(RectF(box.left + h * 0.006f, box.top + h * 0.008f, box.right + h * 0.006f, box.bottom + h * 0.008f), start, sweep, false, p)
                        p.color = shade(col, if (light) 0.95f else 1.7f)
                        c.drawArc(box, start, sweep, false, p)
                    }
                    p.strokeCap = Paint.Cap.BUTT
                    p.style = Paint.Style.FILL
                }
                hub(c, cx, cy, rad * (if (spec?.media == CaseHeld.DISC80) 1.5f else 1f), col, p)
            }
            Kind.SLOT -> if (umd) {
                // Update 260: a PSP case holds its UMD in a recess of the UMD's own shape (round top, straight bottom), with
                // the round hub in the middle that clips into the UMD's spindle window and a thumb notch under it — it was
                // a square card slot.
                p.style = Paint.Style.FILL
                val m = h * 0.012f
                val bed = RectF(ir.left - m, ir.top - m, ir.right + m, ir.bottom + m)
                p.color = shade(col, if (light) 0.8f else 0.6f)
                c.drawPath(UmdBack.outline(RectF(bed.left + h * 0.004f, bed.top + h * 0.006f, bed.right + h * 0.004f, bed.bottom + h * 0.006f)), p)
                p.color = shade(col, if (light) 0.88f else 0.72f)
                c.drawPath(UmdBack.outline(bed), p)
                // The hub (the disc's middle is 32 mm down the 65 mm UMD).
                val hx = ir.centerX(); val hy = ir.top + ir.height() * (32f / 65f); val hr = ir.width() * (9f / 64f)
                p.color = shade(col, if (light) 0.7f else 0.5f)
                c.drawCircle(hx + h * 0.004f, hy + h * 0.006f, hr, p)
                p.color = shade(col, if (light) 0.97f else 1.35f)
                c.drawCircle(hx, hy, hr, p)
                p.color = shade(col, if (light) 0.85f else 1.1f)
                c.drawCircle(hx, hy, hr * 0.55f, p)
                // The thumb notch at the bottom edge.
                p.color = shade(col, 0.5f)
                c.drawCircle(ir.centerX(), ir.bottom + m, ir.width() * 0.12f, p)
            } else {
                // The card's recess with a thumb notch under it.
                // (Update 287: on black plastic — the North American / Japanese DS case — a darker recess was black on black,
                // invisible: there the recesses are lit a little instead, as moulded plastic catches the light.)
                fun rec(k: Float) = if (light || AColor.red(col) + AColor.green(col) + AColor.blue(col) > 150) shade(col, k) else shade(col, 1f + (1f - k) * 4.5f)
                p.style = Paint.Style.FILL
                p.color = rec(0.62f)
                val rr = h * 0.02f
                val m = h * 0.012f
                c.drawRoundRect(RectF(ir.left - m, ir.top - m, ir.right + m, ir.bottom + m), rr, rr, p)
                p.color = rec(0.5f)
                c.drawCircle(ir.centerX(), ir.bottom + m, ir.width() * 0.16f, p)
                when {
                    // Update 287: the North American eco DS case — no GBA holder; recycling cut-outs through the tray (the
                    // dark of the case behind them) and the recycle mark moulded under the card.
                    spec?.eco == true -> {
                        p.color = 0xFF050506.toInt()
                        val cw = w * 0.16f; val ch2 = h * 0.2f; val cy = h * 0.66f
                        for (k in -1..1) {
                            val cx = w / 2f + k * cw * 1.35f
                            c.drawRoundRect(RectF(cx - cw / 2f, cy - ch2 / 2f, cx + cw / 2f, cy + ch2 / 2f), cw * 0.25f, cw * 0.25f, p)
                        }
                        // The recycle mark: a small triangle of three arrows (drawn as its outline).
                        p.color = rec(0.55f)
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = h * 0.006f
                        val tr = h * 0.028f; val tx = w / 2f; val ty = ir.bottom + m + h * 0.075f
                        val path = android.graphics.Path().apply {
                            moveTo(tx, ty - tr); lineTo(tx + tr * 0.87f, ty + tr * 0.5f); lineTo(tx - tr * 0.87f, ty + tr * 0.5f); close()
                        }
                        c.drawPath(path, p)
                        p.style = Paint.Style.FILL
                    }
                    // Update 191: a DS case's Game Boy Advance slot under the card (Game Boy Advance cartridge size).
                    spec?.gbaSlot == true -> {
                        p.color = rec(0.66f)
                        val gw = 57.5f / spec.wMm * w; val gh = 35f / spec.hMm * h
                        val gy = (ir.bottom + m + h * 0.06f + gh / 2f).coerceAtMost(h * 0.86f)
                        c.drawRoundRect(RectF(w / 2f - gw / 2f, gy - gh / 2f, w / 2f + gw / 2f, gy + gh / 2f), rr, rr, p)
                        p.color = rec(0.54f)
                        c.drawCircle(w / 2f, gy + gh / 2f, gw * 0.1f, p)
                    }
                    // Update 191: a Switch case's two tabs above the card, for the paper inside.
                    spec != null && (spec.platform == "Switch" || spec.platform == "Switch2") -> {
                        p.color = shade(col, if (light) 0.82f else 1.4f)
                        val ty = ir.top - h * 0.1f
                        for (tx in floatArrayOf(w * 0.18f, w * 0.72f)) c.drawRoundRect(RectF(tx, ty, tx + w * 0.1f, ty + h * 0.03f), h * 0.01f, h * 0.01f, p)
                    }
                    // A second, flat recess lower down (where a card case keeps its little leaflet).
                    spec == null -> {
                        p.color = shade(col, 0.85f)
                        val lw = w * 0.5f; val lt = h * 0.6f
                        c.drawRoundRect(RectF((w - lw) / 2f, lt, (w + lw) / 2f, lt + h * 0.24f), rr, rr, p)
                    }
                    else -> Unit
                }
            }
            Kind.BOX -> Unit
        }
    }

    /**
     * Update 202: where the game's manual — a booklet you can tap to read — lies in the lid (u0, v0, u1, v1 of the lid's
     * picture, as [lid] draws it), or null where there's none: a cardboard box has no lid, and Xbox One / Series cases
     * hold the disc there (no room beside it).
     */
    fun bookletRect(spec: CaseSpec?, kind: Kind, aspect: Float): FloatArray? {
        if (spec?.mediaLeft == true || kind == Kind.BOX) return null
        val a = aspect.coerceAtLeast(0.2f)
        val jewelLid = if (spec != null) spec.shell == CaseShell.JEWEL else kind == Kind.JEWEL
        if (jewelLid) { val m = 0.05f; return floatArrayOf(m / a, m, 1f - m / a, 1f - m) }
        val mem = spec?.memCard == true
        val mh = if (mem) 0.66f else 0.86f
        val top = if (mem) 0.05f else (1f - mh) / 2f
        return floatArrayOf(0.07f, top, 0.93f, top + mh)
    }

    /**
     * The lid side, seen from inside: the manual in its clips (keep / card cases) or the booklet's back (jewel).
     * Update 202: [booklet] — the game has a manual to read: it lies there as a booklet (its cover, the pages' edges under
     * it, its fold and staples), wearing its own first page once downloaded ([bookletPage]), else the game's cover dressed
     * as an instruction manual. Not in a case that holds its disc in the lid (see [bookletRect]).
     */
    fun lid(
        platform: String?, aspect: Float, cover: Bitmap?, spec: CaseSpec? = null, kind0: Kind? = null, itemAspect: Float = 1f,
        booklet: Boolean = false, bookletPage: Bitmap? = null
    ): Bitmap {
        val kind = kind0 ?: kindFor(spec, platform, null)
        val col = plastic(spec, platform)
        val book = if (booklet) bookletRect(spec, kind, aspect) else null
        val key = "l|$kind|$col|${(aspect * 100).toInt()}|${cover?.let { System.identityHashCode(it) } ?: 0}|${specKey(spec)}|${(itemAspect * 100).toInt()}" +
            "|${if (book != null) 1 else 0}|${bookletPage?.let { System.identityHashCode(it) } ?: 0}"
        cache.get(key)?.let { return it }
        val h = 512
        val w = (h * aspect).toInt().coerceIn(160, 1200)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val jewelLid = if (spec != null) spec.shell == CaseShell.JEWEL else kind == Kind.JEWEL
        if (spec?.mediaLeft == true) {
            // Update 191 (Xbox One / Series): the disc's hub is inside the front cover.
            plasticBase(c, w, h, col, p)
            mediaBed(c, w, h, kind, aspect, itemAspect, spec, col, p)
        } else if (jewelLid) {
            // Clear plastic over the booklet: the booklet's back page (its cover, faded like a printed back), a little
            // smaller than the lid, with the lid's two tabs on the opening side.
            c.drawColor(0xFF0E0F12.toInt())
            val m = h * 0.035f
            val page = RectF(m, m, w - m, h - m)
            if (book != null) {
                // Update 202: the booklet itself, its front up (the manual to read), under the clear lid.
                drawBooklet(c, RectF(book[0] * w, book[1] * h, book[2] * w, book[3] * h), bookletPage, cover, h)
            } else if (cover != null) {
                p.alpha = 255
                p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply {
                    setSaturation(0.35f)
                    postConcat(android.graphics.ColorMatrix().apply { setScale(0.45f, 0.45f, 0.48f, 1f) })
                })
                c.drawBitmap(cover, null, page, p)
                p.colorFilter = null
            } else { p.color = 0xFF2A2C33.toInt(); c.drawRect(page, p) }
            // Faint printed lines (the back page's text). (Update 202: not over a booklet's front.)
            p.color = 0x22FFFFFF
            p.strokeWidth = h * 0.006f
            var y = h * 0.62f
            while (book == null && y < h * 0.9f) { c.drawLine(w * 0.12f, y, w * (0.55f + (y * 7 % 30) / 100f), y, p); y += h * 0.035f }
            tabs(c, w, h, 0xFF9AA0AB.toInt(), p, onLeft = true)
            glare(c, w, h, p)
        } else {
            plasticBase(c, w, h, col, p)
            // Update 191: a memory card holder (PS2, GameCube) below the manual — where exactly isn't confirmed.
            val mem = spec?.memCard == true
            // The manual, its front facing you, held by two clips.
            val mw = w * 0.86f; val mh = h * (if (mem) 0.66f else 0.86f)
            val top = if (mem) h * 0.05f else (h - mh) / 2f
            val page = RectF((w - mw) / 2f, top, (w + mw) / 2f, top + mh)
            if (book != null) {
                // Update 202: the manual as a booklet you can tap to read (same place as the drawn manual).
                drawBooklet(c, RectF(book[0] * w, book[1] * h, book[2] * w, book[3] * h), bookletPage, cover, h)
            } else if (LookStyles.casePlatform(platform) in INSERT_BACK_ONLY) {
                // (Update 215: a Switch or Xbox 360 game without a manual: the lid's inside is the clear plastic over the
                // paper insert's plain back, as on the real case — it showed the front cover again, as a manual, which
                // isn't in these cases.)
                p.color = 0x14000000
                var y = h * 0.14f
                while (y < h * 0.3f) { c.drawRect(w * 0.14f, y, w * (0.5f + (y * 7 % 30) / 100f), y + h * 0.006f, p); y += h * 0.03f }
                glare(c, w, h, p)
            } else {
                p.color = 0x55000000
                c.drawRect(RectF(page.left + h * 0.008f, page.top + h * 0.01f, page.right + h * 0.008f, page.bottom + h * 0.01f), p)
                if (cover != null) c.drawBitmap(cover, null, page, Paint(Paint.FILTER_BITMAP_FLAG))
                else { p.color = 0xFFE9E9EC.toInt(); c.drawRect(page, p) }
            }
            if (mem) {
                val cardW = (if (spec?.platform == "GameCube") 39f else 42f) / (spec?.wMm ?: 135f) * w
                val cardH = (if (spec?.platform == "GameCube") 46f else 56f) / (spec?.hMm ?: 190f) * h
                val cx = w * 0.62f; val cy = h * 0.86f
                val rr = h * 0.015f
                p.color = shade(col, if (isLight(col)) 0.8f else 0.55f)
                c.drawRoundRect(RectF(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, (cy + cardH / 2f).coerceAtMost(h - h * 0.03f)), rr, rr, p)
                p.color = shade(col, if (isLight(col)) 0.7f else 0.45f)
                c.drawRoundRect(RectF(cx - cardW / 2f + h * 0.008f, cy - cardH / 2f + h * 0.008f, cx + cardW / 2f - h * 0.008f, (cy + cardH / 2f).coerceAtMost(h - h * 0.03f) - h * 0.008f), rr, rr, p)
            }
            // (Update 189: the clips sit by the hinge, top and bottom, as in a real keep case.)
            tabs(c, w, h, shade(col, if (isLight(col)) 0.85f else 1.5f), p, onLeft = false)
        }
        cache.put(key, b)
        return b
    }

    /**
     * The disc / card lying in the case: the game's own picture of it when there is one ([real]), else drawn — a disc
     * with the cover as its label, or a card with the cover on it.
     */
    fun item(kind: Kind, real: Bitmap?, label: Bitmap?, itemAspect: Float, tone: Int, umd: Boolean = false): Bitmap? {
        val key = "i|$kind|${real?.let { System.identityHashCode(it) } ?: 0}|${label?.let { System.identityHashCode(it) } ?: 0}|${(itemAspect * 100).toInt()}|$umd"
        cache.get(key)?.let { return it }
        val round = kind == Kind.HUB || kind == Kind.JEWEL
        // (Update 260: a PSP game without a photo of its UMD shows a drawn UMD, not a grey card with the cover squeezed in.)
        val out: Bitmap = if (real != null) real else if (umd && !round) UmdBack.front(label) else if (round) {
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

    /**
     * Update 202: a manual booklet lying in [r] (lid picture px; [h] the picture's height): its shadow, the pages' edges
     * under its cover (its thickness), the cover — the manual's own first page ([firstPage]) or, until it's downloaded,
     * the game's [cover] with an "Instruction manual" band — then the fold along its left edge with two staples and a
     * soft sheen.
     */
    private fun drawBooklet(c: Canvas, r: RectF, firstPage: Bitmap?, cover: Bitmap?, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val t = h * 0.006f
        p.color = 0x66000000
        c.drawRect(RectF(r.left + t * 2f, r.top + t * 3f, r.right + t * 4f, r.bottom + t * 4.5f), p)
        // The sheets' edges, lighter and darker in turn, showing below and right of the cover.
        for (i in 3 downTo 1) {
            p.color = if (i % 2 == 0) 0xFFD9D4C8.toInt() else 0xFFF1EEE6.toInt()
            c.drawRect(RectF(r.left + t * i * 0.5f, r.top + t * i, r.right + t * i, r.bottom + t * i), p)
        }
        val art = firstPage ?: cover
        if (art != null && art.width > 0 && art.height > 0) {
            // Cut to the booklet's shape round its middle (not stretched).
            val ra = r.width() / r.height().coerceAtLeast(1f)
            val ba = art.width.toFloat() / art.height
            val src = if (ba > ra) {
                val sw = (art.height * ra).toInt().coerceIn(1, art.width)
                android.graphics.Rect((art.width - sw) / 2, 0, (art.width + sw) / 2, art.height)
            } else {
                val sh = (art.width / ra).toInt().coerceIn(1, art.height)
                android.graphics.Rect(0, (art.height - sh) / 2, art.width, (art.height + sh) / 2)
            }
            c.drawBitmap(art, src, r, p)
        } else {
            p.color = 0xFFE9E6DE.toInt()
            c.drawRect(r, p)
        }
        if (firstPage == null) {
            // Not downloaded yet: the cover dressed as the game's manual.
            val band = RectF(r.left, r.top, r.right, r.top + r.height() * 0.12f)
            p.color = 0xE6141518.toInt()
            c.drawRect(band, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            p.letterSpacing = 0.14f
            p.textSize = band.height() * 0.42f
            val label = "INSTRUCTION MANUAL"
            val maxW = band.width() * 0.88f
            val tw = p.measureText(label)
            if (tw > maxW) p.textSize = p.textSize * maxW / tw
            c.drawText(label, band.centerX(), band.centerY() - (p.descent() + p.ascent()) / 2f, p)
            p.textAlign = Paint.Align.LEFT
            p.typeface = null
            p.letterSpacing = 0f
        }
        // The fold along the left edge, and its two staples.
        val fold = r.width() * 0.06f
        p.shader = LinearGradient(r.left, 0f, r.left + fold, 0f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(RectF(r.left, r.top, r.left + fold, r.bottom), p)
        p.shader = null
        p.color = 0xFFB8BCC4.toInt()
        val sx = r.left + r.width() * 0.02f
        val sw = (r.width() * 0.012f).coerceAtLeast(1.5f)
        for (fy in floatArrayOf(0.22f, 0.78f)) {
            val cy = r.top + r.height() * fy
            c.drawRect(sx, cy - r.height() * 0.035f, sx + sw, cy + r.height() * 0.035f, p)
        }
        // A soft sheen on the printed cover, and its edge.
        p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(0x26FFFFFF, 0x00FFFFFF, 0x12000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = t * 0.6f
        p.color = 0x33000000
        c.drawRect(r, p)
        p.style = Paint.Style.FILL
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
        /** Update 193: drawn from either side (the lid's rim). */
        var twoSided = false
    }
    private val quads = Array(16) { Quad() }

    @Synchronized
    fun draw(
        c: Canvas, cx: Float, cy: Float, w: Float, h: Float, d: Float, yaw: Float, pitch: Float, open: Float,
        front: Bitmap?, spine: Bitmap, back: Bitmap?, tray: Bitmap, lidInside: Bitmap, item: Bitmap?, itemRect: FloatArray,
        showItem: Boolean, plastic: Int, light: Float,
        /** Update 191: the disc lies inside the lid (Xbox One / Series), not in the tray. */
        itemOnLid: Boolean = false,
        /** Update 193: the front lid's share of the case's depth (its own thickness; the tray's walls are the rest). */
        lidFrac: Float = 0f,
        /** Update 193: the lid's edge colour (its rim, seen as it swings). */
        lidEdge: Int = 0
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
        fun next(): Quad = quads[n++].also { it.depth = 0f; it.bmp = null; it.color = 0; it.ok = true; it.light = 1f; it.edge = false; it.twoSided = false }
        fun area(q: Quad): Float {
            var a = 0f
            for (i in 0 until 4) { val j = (i + 1) % 4; a += q.pts[i * 2] * q.pts[j * 2 + 1] - q.pts[j * 2] * q.pts[i * 2 + 1] }
            return a
        }
        val floor = -hd + d * 0.12f
        // The tray (the back half's inside).
        val trayQ = next().also { q -> q.bmp = tray; q.light = 0.95f
            put(q, 0, -hw, -hh, floor); put(q, 1, hw, -hh, floor); put(q, 2, hw, hh, floor); put(q, 3, -hw, hh, floor) }
        // The disc / card lying in it.
        // (Update 215: always drawn just after the tray it lies in. Sorted by its own middle, a card low in the tray
        // (the Switch's) came out "behind" the tray's middle with the case tilted towards you, so the tray covered it —
        // the card was only there when the case was turned.)
        if (showItem && item != null && !itemOnLid) next().also { q -> q.bmp = item
            val z = floor + d * 0.18f
            val x0 = -hw + itemRect[0] * w; val x1 = -hw + itemRect[2] * w
            val y0 = -hh + itemRect[1] * h; val y1 = -hh + itemRect[3] * h
            put(q, 0, x0, y0, z); put(q, 1, x1, y0, z); put(q, 2, x1, y1, z); put(q, 3, x0, y1, z)
            q.depth = trayQ.depth + 0.001f }
        // The walls: each drawn on the side that faces you (outside: the spine picture / plastic; inside: plastic).
        val wall = CaseInside.shade(plastic, 0.8f)
        // (Update 193: the tray's walls stop where the lid sits on them — the lid has its own thickness.)
        val lidT = d * lidFrac.coerceIn(0f, 0.6f)
        val zTop = hd - lidT
        // top (outside faces up), bottom, open edge, spine.
        next().also { q -> q.color = wall; q.light = 0.75f
            put(q, 0, -hw, -hh, -hd); put(q, 1, hw, -hh, -hd); put(q, 2, hw, -hh, zTop); put(q, 3, -hw, -hh, zTop) }
        next().also { q -> q.color = wall; q.light = 0.6f
            put(q, 0, -hw, hh, zTop); put(q, 1, hw, hh, zTop); put(q, 2, hw, hh, -hd); put(q, 3, -hw, hh, -hd) }
        next().also { q -> q.color = wall; q.light = 0.7f
            put(q, 0, hw, -hh, zTop); put(q, 1, hw, -hh, -hd); put(q, 2, hw, hh, -hd); put(q, 3, hw, hh, zTop) }
        next().also { q -> q.bmp = spine; q.light = 0.85f
            put(q, 0, -hw, -hh, -hd); put(q, 1, -hw, -hh, hd); put(q, 2, -hw, hh, hd); put(q, 3, -hw, hh, -hd) }
        // The back, from behind.
        next().also { q -> q.bmp = back; q.color = CaseInside.shade(plastic, 0.7f); q.light = 0.8f
            put(q, 0, hw, -hh, -hd); put(q, 1, -hw, -hh, -hd); put(q, 2, -hw, hh, -hd); put(q, 3, hw, hh, -hd) }
        // The lid, swung round the hinge (x = -hw, z = +hd): its outside (the cover) and its inside.
        val th = Math.toRadians((open.coerceIn(0f, 1f) * 178f).toDouble())
        val ct = kotlin.math.cos(th).toFloat(); val st = kotlin.math.sin(th).toFloat()
        // (Update 193: the lid's inside on the tray's walls, its outside its own thickness further out.)
        fun lx(u: Float) = -hw + u * w * ct
        fun lz(u: Float) = zTop + u * w * st
        val ox = -st * lidT; val oz = ct * lidT
        next().also { q -> q.bmp = front; q.color = CaseInside.shade(plastic, 0.9f)
            put(q, 0, lx(0f) + ox, -hh, lz(0f) + oz); put(q, 1, lx(1f) + ox, -hh, lz(1f) + oz); put(q, 2, lx(1f) + ox, hh, lz(1f) + oz); put(q, 3, lx(0f) + ox, hh, lz(0f) + oz) }
        // Its opening edge (the rim you put your thumb on), as it swings.
        if (lidT > 0.5f) next().also { q -> q.color = if (lidEdge != 0) lidEdge else wall; q.light = 0.8f; q.twoSided = true
            put(q, 0, lx(1f), -hh, lz(1f)); put(q, 1, lx(1f) + ox, -hh, lz(1f) + oz); put(q, 2, lx(1f) + ox, hh, lz(1f) + oz); put(q, 3, lx(1f), hh, lz(1f)) }
        val lidQ = next().also { q -> q.bmp = lidInside; q.light = 0.97f
            put(q, 0, lx(1f), -hh, lz(1f)); put(q, 1, lx(0f), -hh, lz(0f)); put(q, 2, lx(0f), hh, lz(0f)); put(q, 3, lx(1f), hh, lz(1f)) }
        // Update 191: a disc held inside the lid (its place on the lid's picture: across from the lid's outer edge).
        if (showItem && item != null && itemOnLid) next().also { q -> q.bmp = item; q.light = 0.97f
            val e = d * 0.18f
            fun ix(u: Float) = lx(1f - u) + st * e
            fun iz(u: Float) = lz(1f - u) - ct * e
            val y0 = -hh + itemRect[1] * h; val y1 = -hh + itemRect[3] * h
            put(q, 0, ix(itemRect[0]), y0, iz(itemRect[0])); put(q, 1, ix(itemRect[2]), y0, iz(itemRect[2]))
            put(q, 2, ix(itemRect[2]), y1, iz(itemRect[2])); put(q, 3, ix(itemRect[0]), y1, iz(itemRect[0]))
            // (Update 215: just after the lid's inside it lies on, as the tray's item.)
            q.depth = lidQ.depth + 0.001f }

        // Far to near.
        val order = (0 until n).filter { quads[it].ok }.sortedBy { quads[it].depth }
        for (i in order) {
            val q = quads[i]
            val a = area(q)
            val isWall = q.bmp == null && q.color == wall
            // The walls show their inside when their outside faces away (they're open boxes): draw either way, darker inside.
            if (a <= 0f && !isWall && !q.twoSided) continue
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
            // (Update 189 (Thor): no outlines — a square showed round the disc and lines round every face.)
        }
    }

    private val filters = arrayOfNulls<android.graphics.ColorFilter>(256)
    private fun lightFilter(l: Float): android.graphics.ColorFilter? {
        if (l >= 0.995f) return null
        val v = (l.coerceIn(0f, 1f) * 255f).toInt()
        return filters[v] ?: android.graphics.LightingColorFilter(AColor.rgb(v, v, v), 0).also { filters[v] = it }
    }
}
