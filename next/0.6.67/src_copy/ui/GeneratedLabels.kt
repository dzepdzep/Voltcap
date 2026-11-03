package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Update 459 (owner: "the difference should be the generated printed label, not the physical cartridge itself"): the
 * printed sticker for a game with no real label scan — ROM hacks, homebrew, games without a scan — on its console's own
 * drawn cartridge (CartShells, unchanged). The sticker is always the real sticker's full size and shape; the game's
 * cover keeps its own proportions, whole (never cut, never stretched), placed where that console's labels carry their
 * art, and the rest of the sticker is printed the way that console's labels are: the Game Boy's grey side borders with
 * the serial and region, the Game Boy Advance's dark panel with the title, the Nintendo 64's black foot, the Mega Drive's
 * header band, the Master System's grid… Printed identification is only what's true (LabelIds): a real product code from
 * the ROM's header, "FAN-MADE" / "HOMEBREW" for unofficial games, otherwise none — no ratings, seals or licences.
 *
 * Pure drawing on a bitmap, deterministic for the same inputs, cached by the shell's front (CartShells.front).
 */
internal object GeneratedLabels {
    /** Update 460: off by default — the owner rejected generated label designs; QA only (`voltcap_test_genlabels`). */
    @Volatile var enabled = false

    /**
     * Update 461 (owner, Pokémon Brown "option D"): the one design that is on by default — a Game Boy / Game Boy Color pak
     * with no label scan gets a full-size printed sticker in the style of the real ones (printedGameBoy): grey printed
     * borders and side areas with a fine darker frame line, the cover whole at its own proportions, a thin keyline round it.
     * Nothing printed that identifies anything: no product code, region, rating, seal or "FAN-MADE".
     */
    fun printed(kind: Kind?): Boolean = kind == Kind.DMG || kind == Kind.CGB

    enum class Kind { DMG, CGB, AGB, N64, NES, FAMICOM, SNES, SFC, VB, MEGA_DRIVE, MASTER_SYSTEM, GAME_GEAR, SEGA_32X, ATARI, CARD, PLAIN }

    /** The label design for a drawn shell, or null where the shell keeps its own (DS / 3DS / Switch cards — SwitchLabel). */
    fun kindOf(shellId: String): Kind? = when (shellId) {
        "ds", "3ds", "switch", "switch2", "switch2_key" -> null
        "gb", "gbc_dual", "np_gb", "gb_camera", "pokemon_pinball", "megaduck", "supervision" -> Kind.DMG
        "gbc" -> Kind.CGB
        "gba", "warioware_twisted", "boktai", "drill_dozer", "yoshi_topsy", "kirby_tilt" -> Kind.AGB
        "n64" -> Kind.N64
        "nes" -> Kind.NES
        "famicom", "fds" -> Kind.FAMICOM
        "snes_na" -> Kind.SNES
        "sfc", "np_sfc" -> Kind.SFC
        "vb" -> Kind.VB
        "genesis", "megadrive_jp", "genesis_ea", "virtua_racing", "sonic_knuckles", "codemasters", "jcart" -> Kind.MEGA_DRIVE
        "sms", "sms_jp", "sg1000", "sega_card" -> Kind.MASTER_SYSTEM
        "game_gear" -> Kind.GAME_GEAR
        "32x" -> Kind.SEGA_32X
        "atari2600", "atari2600_activision", "atari5200", "atari7800", "jaguar", "lynx" -> Kind.ATARI
        "hucard", "ngp", "wonderswan", "pokemini", "ngage", "evercade" -> Kind.CARD
        else -> Kind.PLAIN
    }

    /**
     * Where the cover goes on a [w] × [h] sticker for [kind] — left, top, right, bottom — at the cover's own aspect [a]
     * (width / height): as large as fits the design's art area, never cut or stretched (pure — unit-tested).
     */
    fun artRect(kind: Kind, w: Float, h: Float, a: Float): FloatArray {
        val area = area(kind, w, h)
        val aw = area[2] - area[0]; val ah = area[3] - area[1]
        // (A cover much narrower than a wide sticker's art area — most covers are portrait — goes to the right at the
        // area's full height, the printed panel left of it carrying the title: no wide empty margins either side.)
        if (split(kind, aw, ah, a)) {
            val fw = ah * a
            return floatArrayOf(area[2] - fw, area[1], area[2], area[3])
        }
        val fw: Float; val fh: Float
        if (a >= aw / ah) { fw = aw; fh = aw / a } else { fh = ah; fw = ah * a }
        // (GBA: the art bleeds off the sticker's right edge, the printed panel left of it.)
        val x = if (kind == Kind.AGB) area[2] - fw else area[0] + (aw - fw) / 2f
        // (Top-aligned where the design prints its text below the art; else centred.)
        val y = when (kind) {
            Kind.N64, Kind.NES, Kind.FAMICOM, Kind.SNES, Kind.VB, Kind.ATARI, Kind.CARD, Kind.PLAIN -> area[1]
            else -> area[1] + (ah - fh) / 2f
        }
        return floatArrayOf(x, y, x + fw, y + fh)
    }

    /** Whether a cover of aspect [a] is set beside a printed title panel in an art area [aw] × [ah] (pure). */
    fun split(kind: Kind, aw: Float, ah: Float, a: Float): Boolean = when (kind) {
        Kind.DMG, Kind.CGB, Kind.AGB, Kind.N64 -> false
        else -> a < (aw / ah) * 0.62f
    }

    /** The design's art area (left, top, right, bottom) on a [w] × [h] sticker. */
    private fun area(kind: Kind, w: Float, h: Float): FloatArray = when (kind) {
        // Game Boy: the grey side borders are at least ~7 % each; the art takes the full height between them.
        Kind.DMG, Kind.CGB -> floatArrayOf(w * 0.075f, 0f, w * 0.925f, h)
        // GBA: the art the full height; the dark panel beside it carries the title and code.
        Kind.AGB -> floatArrayOf(w * 0.30f, 0f, w, h)
        // N64: under the arched top, the black foot below for the title and code.
        Kind.N64 -> floatArrayOf(0f, h * 0.04f, w, h * 0.80f)
        Kind.NES -> floatArrayOf(w * 0.04f, h * 0.05f, w * 0.96f, h * 0.80f)
        Kind.FAMICOM -> floatArrayOf(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.84f)
        Kind.SNES -> floatArrayOf(w * 0.03f, h * 0.04f, w * 0.97f, h * 0.82f)
        Kind.SFC -> floatArrayOf(w * 0.04f, h * 0.17f, w * 0.96f, h * 0.96f)
        Kind.VB -> floatArrayOf(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.82f)
        Kind.MEGA_DRIVE, Kind.SEGA_32X -> floatArrayOf(w * 0.03f, h * 0.17f, w * 0.97f, h * 0.97f)
        Kind.GAME_GEAR -> floatArrayOf(w * 0.04f, h * 0.19f, w * 0.96f, h * 0.97f)
        Kind.MASTER_SYSTEM -> floatArrayOf(w * 0.05f, h * 0.15f, w * 0.95f, h * 0.95f)
        Kind.ATARI -> floatArrayOf(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.82f)
        Kind.CARD, Kind.PLAIN -> floatArrayOf(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.84f)
    }

    /**
     * Update 461: where the cover goes on the printed Game Boy sticker [w] × [h] ([mm] px per millimetre) at its aspect
     * [a]: the full height of the sticker, as on the real labels (Pokémon Silver, the Crystal Clear fan label), between grey
     * printed side borders at least 3.6 mm wide; a cover too wide for that fits the width instead, grey above and below.
     * Centred, never cut or stretched (pure — unit-tested).
     */
    fun printedArtRect(w: Float, h: Float, a: Float, mm: Float): FloatArray {
        val l = minOf(3.6f * mm, w * 0.12f)
        val aw = (w - 2 * l).coerceAtLeast(1f); val ah = h.coerceAtLeast(1f)
        val fw: Float; val fh: Float
        if (a >= aw / ah) { fw = aw; fh = aw / a } else { fh = ah; fw = ah * a }
        val x = (w - fw) / 2f; val y = (h - fh) / 2f
        return floatArrayOf(x, y, x + fw, y + fh)
    }

    /**
     * Update 461: the printed Game Boy sticker (see [printed]) — laid out as the real ones are: the art the full height of the
     * sticker, the printed grey either side; the right border carries the "THIS SIDE OUT" every Game Boy label has, the left
     * one the game's name where a real label has its product code (never a code, region, rating or seal).
     */
    fun printedGameBoy(art: Bitmap, w: Int, h: Int, mm: Float, colour: Boolean, title: String? = null): Bitmap {
        val b = Bitmap.createBitmap(w.coerceAtLeast(8), h.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val W = b.width.toFloat(); val H = b.height.toFloat()
        // The printed grey (a touch lighter on the Color paks), lit a little from the top as the paper is.
        val top = if (colour) 0xFFB9B9BE.toInt() else 0xFFB0B0B4.toInt()
        val bottom = if (colour) 0xFFA9A9AE.toInt() else 0xFFA0A0A5.toInt()
        p.shader = LinearGradient(0f, 0f, 0f, H, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p); p.shader = null
        fine(c, p, W, H, 0x0A000000)
        val r = printedArtRect(W, H, art.width.toFloat() / art.height.coerceAtLeast(1), mm).let { RectF(it[0], it[1], it[2], it[3]) }
        c.drawBitmap(art, null, r, p)
        val ink = 0xFF2A2A2D.toInt()
        val size = minOf(1.3f * mm, r.left * 0.42f)
        if (r.left > 1.8f * mm) {
            vertical(c, "THIS SIDE OUT", RectF(r.right, 0f, W, H), size, ink, up = false, bold = true)
            val name = title?.let { TAGS.replace(gameDisplayName(it), " ").replace(SPACES, " ").trim() }?.takeIf { it.isNotBlank() }
            if (name != null) vertical(c, name.uppercase(), RectF(0f, 0f, r.left, H), size, ink, up = true, bold = true)
        }
        return b
    }

    /** What is printed to identify the game: the real code, else "FAN-MADE" / "HOMEBREW", else nothing (pure). */
    fun idLine(id: com.shelf.launcher.data.LabelIds.Id?): String? = id?.unofficial ?: id?.code

    /**
     * The sticker for [kind] at [w] × [h] px ([mm]: px per millimetre, for the print's text sizes), [art] whole on it.
     * [title]: the game's name (typeset only where the design prints one); [brand]: its console's name (header bands).
     */
    fun compose(kind: Kind, art: Bitmap, w: Int, h: Int, mm: Float, id: com.shelf.launcher.data.LabelIds.Id?, title: String?, brand: String): Bitmap {
        if (!enabled && printed(kind)) return printedGameBoy(art, w, h, mm, colour = kind == Kind.CGB, title = title)
        val b = Bitmap.createBitmap(w.coerceAtLeast(8), h.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val W = b.width.toFloat(); val H = b.height.toFloat()
        val a = art.width.toFloat() / art.height.coerceAtLeast(1)
        val r = artRect(kind, W, H, a).let { RectF(it[0], it[1], it[2], it[3]) }
        val code = idLine(id)
        val region = id?.region
        // (The name as printed: no region / version / dump tags.)
        val name = title?.let { TAGS.replace(gameDisplayName(it), " ").replace(SPACES, " ").trim() }?.takeIf { it.isNotBlank() }
        val ar = area(kind, W, H)
        val splitPanel = if (split(kind, ar[2] - ar[0], ar[3] - ar[1], a)) RectF(ar[0] + 1.2f * mm, ar[1] + 0.8f * mm, r.left - 1.4f * mm, ar[3] - 3.4f * mm) else null
        val head = header(kind, brand, region)
        when (kind) {
            Kind.DMG, Kind.CGB -> {
                // The grey printed borders (ConsoleMods: "grey borders on the left and right sides with the serial code and
                // region"), the art between them at its full height, a fine dark rule round the art.
                c.drawColor(if (kind == Kind.DMG) 0xFFBDBEC0.toInt() else 0xFFC9CACC.toInt())
                fine(c, p, W, H, 0x0A000000)
                c.drawBitmap(art, null, r, p)
                rule(c, p, r, 0.12f * mm, 0xCC1A1A1A.toInt())
                val left = r.left; val right = W - r.right
                if (code != null && left > 1.6f * mm) vertical(c, code, RectF(0f, 0f, left, H), 1.35f * mm, 0xFF2A2A2C.toInt(), up = true, bold = true)
                if (region != null && right > 1.6f * mm) vertical(c, region, RectF(r.right, 0f, W, H), 1.35f * mm, 0xFF2A2A2C.toInt(), up = false, bold = true)
                // (A landscape cover leaves room above / below: the same grey border, the code along the foot.)
                if (H - r.bottom > 2.2f * mm && left <= 1.6f * mm && code != null) line(c, code, RectF(0f, r.bottom, W, H), 1.3f * mm, 0xFF2A2A2C.toInt(), Paint.Align.CENTER)
            }
            Kind.AGB -> {
                // The dark printed panel left of the art, the title set in it, the code at its foot.
                p.shader = LinearGradient(0f, 0f, W, H, 0xFF1C1D22.toInt(), 0xFF2B2D35.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, p); p.shader = null
                fine(c, p, W, H, 0x10FFFFFF)
                c.drawBitmap(art, null, r, p)
                val panel = if (r.left > 2f * mm) RectF(1.3f * mm, 1.2f * mm, r.left - 1.0f * mm, H - 3.2f * mm)
                    else if (W - r.right > 2f * mm) RectF(r.right + 1.0f * mm, 1.2f * mm, W - 1.3f * mm, H - 3.2f * mm) else null
                if (panel != null && name != null) typeset(c, name, panel, 3.2f * mm, 1.6f * mm, 0xFFF2F2F4.toInt(), maxLines = 5, align = Layout.Alignment.ALIGN_NORMAL)
                if (code != null) line(c, code, RectF(panel?.left ?: 1.3f * mm, H - 3.0f * mm, panel?.right ?: W, H - 0.6f * mm), 1.15f * mm, 0xFFA9ABB2.toInt(), Paint.Align.LEFT)
                p.color = 0x55FFFFFF; c.drawRect(r.left - 0.15f * mm, 0f, r.left, H, p)
            }
            Kind.N64 -> {
                c.drawColor(0xFF0E0E10.toInt())
                c.drawBitmap(art, null, r, p)
                val foot = RectF(2.5f * mm, r.bottom + 1.2f * mm, W - 2.5f * mm, H - 4.2f * mm)
                if (name != null) typeset(c, name, foot, 4.2f * mm, 2.0f * mm, 0xFFF4F4F4.toInt(), maxLines = 2)
                if (code != null) line(c, code, RectF(3f * mm, H - 3.6f * mm, W * 0.7f, H - 1.0f * mm), 1.5f * mm, 0xFFB8B8BC.toInt(), Paint.Align.LEFT)
                // (PAL labels are marked PAL; the NTSC ones aren't.)
                if (region == "EUR") line(c, "PAL", RectF(W * 0.7f, H - 3.6f * mm, W - 3f * mm, H - 1.0f * mm), 1.7f * mm, 0xFFE8E8EA.toInt(), Paint.Align.RIGHT, bold = true)
            }
            Kind.NES, Kind.SNES, Kind.VB, Kind.ATARI -> {
                // Black-framed labels: a black field, a thin light keyline round the art, the title and code below it.
                c.drawColor(if (kind == Kind.SNES) 0xFF2B2B2F.toInt() else 0xFF0F0F11.toInt())
                c.drawBitmap(art, null, r, p)
                rule(c, p, r, 0.25f * mm, if (kind == Kind.ATARI) 0xFFC8C8CC.toInt() else 0x66FFFFFF)
                val foot = RectF(W * 0.05f, r.bottom + 1.0f * mm, W * 0.95f, H - 3.6f * mm)
                if (splitPanel == null && name != null && foot.height() > 2.5f * mm) typeset(c, name, foot, 5f * mm, 2.2f * mm, 0xFFF0F0F2.toInt(), maxLines = 2)
                if (code != null) line(c, code, RectF(W * 0.05f, H - 3.2f * mm, W * 0.95f, H - 0.8f * mm), 1.6f * mm, 0xFFAAAAAE.toInt(), Paint.Align.LEFT)
            }
            Kind.FAMICOM, Kind.CARD, Kind.PLAIN -> {
                // White printed paper, the art in it, the title (and code) under it.
                c.drawColor(0xFFF2F1EC.toInt())
                fine(c, p, W, H, 0x08000000)
                c.drawBitmap(art, null, r, p)
                rule(c, p, r, 0.15f * mm, 0x55000000)
                val foot = RectF(W * 0.05f, r.bottom + 0.6f * mm, W * 0.95f, H - 2.6f * mm)
                if (splitPanel == null && name != null && foot.height() > 1.8f * mm) typeset(c, name, foot, 3.4f * mm, 1.4f * mm, 0xFF202022.toInt(), maxLines = 2)
                if (code != null) line(c, code, RectF(W * 0.05f, H - 2.4f * mm, W * 0.95f, H - 0.5f * mm), 1.2f * mm, 0xFF55555A.toInt(), Paint.Align.RIGHT)
            }
            Kind.SFC -> {
                // Light printed field, a header band with the console's name across the top, the art below.
                c.drawColor(0xFFE4E4E6.toInt())
                p.color = 0xFF56565C.toInt(); c.drawRect(0f, 0f, W, H * 0.13f, p)
                line(c, head, RectF(W * 0.05f, 0f, W * 0.95f, H * 0.13f), H * 0.065f, 0xFFF4F4F6.toInt(), Paint.Align.LEFT, bold = true, italic = true)
                c.drawBitmap(art, null, r, p)
                rule(c, p, r, 0.15f * mm, 0x66000000)
                if (code != null) line(c, code, RectF(W * 0.05f, H - 2.4f * mm, W * 0.95f, H - 0.4f * mm), 1.3f * mm, 0xFF505055.toInt(), Paint.Align.RIGHT)
            }
            Kind.MEGA_DRIVE, Kind.SEGA_32X, Kind.GAME_GEAR -> {
                // Black label with the console's name in a header band (the Sega labels' layout; no maker's logo).
                c.drawColor(0xFF0B0B0D.toInt())
                val band = RectF(0f, 0f, W, area(kind, W, H)[1] - 0.6f * mm)
                p.shader = LinearGradient(0f, 0f, 0f, band.bottom, 0xFF26262B.toInt(), 0xFF0B0B0D.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(band, p); p.shader = null
                line(c, head, RectF(W * 0.05f, band.top, W * 0.95f, band.bottom), band.height() * 0.5f, 0xFFF2F2F2.toInt(), Paint.Align.LEFT, bold = true, italic = true)
                if (code != null) line(c, code, RectF(W * 0.5f, band.top, W * 0.95f, band.bottom), band.height() * 0.28f, 0xFF9C9CA2.toInt(), Paint.Align.RIGHT)
                c.drawBitmap(art, null, r, p)
            }
            Kind.MASTER_SYSTEM -> {
                // The Master System's white label with its printed grid; the console's name above the art.
                c.drawColor(0xFFF4F4F2.toInt())
                p.color = 0x1E000000; p.strokeWidth = 0.12f * mm
                val step = 2.4f * mm
                var x = 0f; while (x < W) { c.drawLine(x, 0f, x, H, p); x += step }
                var y = 0f; while (y < H) { c.drawLine(0f, y, W, y, p); y += step }
                p.color = 0xFF000000.toInt()
                line(c, head, RectF(W * 0.06f, 0f, W * 0.94f, H * 0.13f), H * 0.06f, 0xFF1A1A1C.toInt(), Paint.Align.LEFT, bold = true)
                c.drawBitmap(art, null, r, p)
                rule(c, p, r, 0.2f * mm, 0xFF1A1A1C.toInt())
                if (code != null) line(c, code, RectF(W * 0.5f, 0f, W * 0.94f, H * 0.13f), H * 0.04f, 0xFF505055.toInt(), Paint.Align.RIGHT)
            }
        }
        // (Beside a narrow cover: the title set in the printed panel, in the design's ink.)
        if (splitPanel != null && name != null && splitPanel.width() > 3f * mm) {
            val ink = when (kind) { Kind.FAMICOM, Kind.CARD, Kind.PLAIN, Kind.MASTER_SYSTEM, Kind.SFC -> 0xFF1E1E20.toInt(); else -> 0xFFF0F0F2.toInt() }
            // (Update 459, owner check on the RP: a small title left the panel mostly empty — set as large as the panel takes,
            // like a printed logo line, shrinking only to fit.)
            typeset(c, name, splitPanel, maxOf(4.2f * mm, splitPanel.height() * 0.24f), 1.6f * mm, ink, maxLines = 4, align = Layout.Alignment.ALIGN_NORMAL)
        }
        return b
    }

    private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val SPACES = Regex("""\s+""")

    /** The console's name as its labels print it in their header band, by region (pure). */
    fun header(kind: Kind, brand: String, region: String?): String = when (kind) {
        Kind.SFC -> if (region == "JPN") "SUPER FAMICOM" else "SUPER NINTENDO"
        Kind.MEGA_DRIVE -> if (region == "USA") "GENESIS" else "MEGA DRIVE"
        Kind.SEGA_32X -> "32X"
        Kind.GAME_GEAR -> "GAME GEAR"
        Kind.MASTER_SYSTEM -> if (region == "JPN") "MARK III" else "MASTER SYSTEM"
        else -> brand.uppercase()
    }

    // ---- printing helpers ----

    private fun rule(c: Canvas, p: Paint, r: RectF, width: Float, argb: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = width; p.color = argb
        c.drawRect(r.left + width / 2f, r.top + width / 2f, r.right - width / 2f, r.bottom - width / 2f, p)
        p.style = Paint.Style.FILL
    }

    /** A faint printed-paper tooth (seeded, always the same), so a flat colour reads as ink on paper. */
    private fun fine(c: Canvas, p: Paint, w: Float, h: Float, argb: Int) {
        val rnd = java.util.Random(459L)
        p.color = argb
        val n = ((w * h) / 90f).toInt().coerceIn(0, 6000)
        for (i in 0 until n) c.drawPoint(rnd.nextFloat() * w, rnd.nextFloat() * h, p)
        // (The paint goes on to draw the cover: back to opaque, or the cover came out at the tooth's 4 % — washed out.)
        p.color = 0xFF000000.toInt()
    }

    private fun face(bold: Boolean, italic: Boolean): Typeface =
        Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL),
            when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })

    /** One line of print in [r], at most [size] high, shrunk to fit its width. */
    private fun line(c: Canvas, s: String, r: RectF, size: Float, argb: Int, align: Paint.Align, bold: Boolean = false, italic: Boolean = false) {
        if (r.width() <= 2f || r.height() <= 2f) return
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; typeface = face(bold, italic); textAlign = align; letterSpacing = 0.04f }
        tp.textSize = size
        val wNeed = tp.measureText(s)
        if (wNeed > r.width()) tp.textSize = size * r.width() / wNeed
        val x = when (align) { Paint.Align.LEFT -> r.left; Paint.Align.RIGHT -> r.right; else -> r.centerX() }
        val y = r.centerY() - (tp.ascent() + tp.descent()) / 2f
        c.drawText(s, x, y, tp)
    }

    /** One line turned on its side down the middle of [r] (a Game Boy label's side border). */
    private fun vertical(c: Canvas, s: String, r: RectF, size: Float, argb: Int, up: Boolean, bold: Boolean) {
        c.save()
        c.translate(r.centerX(), r.centerY())
        c.rotate(if (up) -90f else 90f)
        val turned = RectF(-r.height() / 2f + r.height() * 0.06f, -r.width() / 2f, r.height() / 2f - r.height() * 0.06f, r.width() / 2f)
        line(c, s, turned, minOf(size, r.width() * 0.62f), argb, Paint.Align.CENTER, bold)
        c.restore()
    }

    /** The game's name set in [r]: the largest size from [max] down to [min] that fits in [maxLines] lines, whole. */
    private fun typeset(c: Canvas, s: String, r: RectF, max: Float, min: Float, argb: Int, maxLines: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER) {
        if (r.width() < 4f || r.height() < 4f) return
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; typeface = face(bold = true, italic = false); letterSpacing = 0.02f }
        var size = max
        var lay: StaticLayout
        while (true) {
            tp.textSize = size
            lay = StaticLayout.Builder.obtain(s, 0, s.length, tp, r.width().toInt().coerceAtLeast(1)).setAlignment(align).setIncludePad(false).build()
            val fitsW = (0 until lay.lineCount).all { lay.getLineWidth(it) <= r.width() + 0.5f }
            if ((lay.lineCount <= maxLines && lay.height <= r.height() && fitsW) || size <= min) break
            size *= 0.92f
        }
        c.save()
        c.clipRect(r)
        c.translate(r.left, r.top + ((r.height() - lay.height) / 2f).coerceAtLeast(0f))
        lay.draw(c)
        c.restore()
    }
}
