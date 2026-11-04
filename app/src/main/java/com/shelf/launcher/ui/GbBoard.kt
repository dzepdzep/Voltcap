package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Update 468 (owner, Update 466 review: "the plain green rectangle must become a convincing PCB built from genuine photos"):
 * the board inside a clear Game Boy / Game Boy Color Game Pak, drawn — no photo is bundled (R25: photos are used to measure
 * only). Measured from:
 *  - chip side: https://commons.wikimedia.org/wiki/File:PokemonSilverBoard.jpg (Simon A. Eugster, CC BY-SA 3.0) —
 *    board DMG-KGDU-10 in its clear shell half: MBC3 (QFP) upper left, the CR2025 battery upper right, SRAM lower left,
 *    mask ROM lower right, the reset IC (MM1134) top middle, the 32 kHz crystal top left, 32 gold fingers along the foot,
 *    the screw hole between the two big chips, the corner notch top right (the shell's);
 *  - solder side: https://commons.wikimedia.org/wiki/File:PokemonSilverBoardBackside.jpg (same author, CC BY-SA 3.0) —
 *    no chips, a dense bundle of traces from the chips to the fingers, vias in rows, test pads TP1–TP4 down one edge,
 *    the finger stubs, the screw hole;
 *  - in the shell: https://commons.wikimedia.org/wiki/File:Nintendo-Game-Boy-Color-Cartridge.jpg (Evan-Amos, public
 *    domain) and https://commons.wikimedia.org/wiki/File:GBCartridges_Back.jpg (PiaCarrot, CC BY-SA 3.0).
 * Which parts are fitted follows the game's own ROM header (its cartridge type, 0x0147 — Pan Docs): a game without save
 * RAM has no SRAM and no battery, only MBC3 + timer games have the crystal, a ROM-only game no mapper. The positions are
 * the photographed board's (verified for MBC3 + timer + RAM + battery, e.g. Pokémon Gold / Silver / Crystal); for other
 * types the same places with parts left out — APPROXIMATE (their own boards — MBC1 / MBC5 layouts — not photographed).
 * Traces and vias are drawn as the photos show them in kind and density, not copied line by line.
 * All fractions are of the board (x right, y down) as seen from the label side.
 */
internal object GbBoard {
    /** What the header says is on the board. */
    data class Parts(val mapper: Boolean, val ram: Boolean, val battery: Boolean, val crystal: Boolean) {
        val key: String get() = "${if (mapper) "m" else ""}${if (ram) "r" else ""}${if (battery) "b" else ""}${if (crystal) "c" else ""}"
    }

    /** Parts for a Game Boy cartridge type byte (Pan Docs, "0147 — Cartridge Type"); null type: the photographed board's. */
    fun partsOf(type: Int?): Parts {
        if (type == null) return Parts(mapper = true, ram = true, battery = true, crystal = false)
        val ram = type in setOf(0x02, 0x03, 0x08, 0x09, 0x0C, 0x0D, 0x10, 0x12, 0x13, 0x1A, 0x1B, 0x1D, 0x1E, 0x22, 0xFF)
        val battery = type in setOf(0x03, 0x06, 0x09, 0x0D, 0x0F, 0x10, 0x13, 0x1B, 0x1E, 0x22, 0xFF)
        return Parts(mapper = type !in setOf(0x00, 0x08, 0x09), ram = ram, battery = battery, crystal = type == 0x0F || type == 0x10)
    }

    // ---- measured places (fractions of the board, label side) ----
    /** The shell's corner notch, cut out of the board's top right. */
    const val NOTCH_W = 0.065f; const val NOTCH_H = 0.04f
    val MAPPER = floatArrayOf(0.174f, 0.306f, 0.178f, 0.167f)
    val RAM = floatArrayOf(0.157f, 0.523f, 0.250f, 0.306f)
    val ROM = floatArrayOf(0.608f, 0.505f, 0.243f, 0.315f)
    val RESET = floatArrayOf(0.357f, 0.158f, 0.100f, 0.059f)
    val CRYSTAL = floatArrayOf(0.189f, 0.098f, 0.146f, 0.067f)
    /** The battery: centre x, y and radius (of the board's width). */
    val BATTERY = floatArrayOf(0.739f, 0.291f, 0.191f)
    /** The screw hole: centre x, y, ring radius, hole radius (of the width). */
    val SCREW = floatArrayOf(0.496f, 0.751f, 0.065f, 0.040f)
    val SMALL_HOLE = floatArrayOf(0.496f, 0.343f, 0.022f)
    /** The gold fingers' band (y from, y to) and their count. */
    const val FINGERS_TOP = 0.876f; const val FINGERS = 32
    const val FINGERS_L = 0.026f; const val FINGERS_R = 0.965f
    /** Small parts (capacitors / resistors) seen on the chip side: x, y, w, h. */
    private val PASSIVES = listOf(
        floatArrayOf(0.233f, 0.200f, 0.03f, 0.016f), floatArrayOf(0.298f, 0.200f, 0.03f, 0.016f), floatArrayOf(0.135f, 0.250f, 0.03f, 0.016f),
        floatArrayOf(0.465f, 0.208f, 0.018f, 0.03f), floatArrayOf(0.413f, 0.495f, 0.03f, 0.016f), floatArrayOf(0.472f, 0.523f, 0.018f, 0.03f),
        floatArrayOf(0.903f, 0.786f, 0.018f, 0.03f)
    )
    /** Test pads TP1–TP4 on the solder side (seen from behind): x, y, w, h. */
    private val TEST_PADS = listOf(
        floatArrayOf(0.925f, 0.500f, 0.035f, 0.055f), floatArrayOf(0.925f, 0.595f, 0.035f, 0.055f),
        floatArrayOf(0.925f, 0.690f, 0.035f, 0.055f), floatArrayOf(0.925f, 0.770f, 0.035f, 0.045f)
    )

    private const val MASK = 0xFF1E6A45.toInt()        // solder mask over bare board (green, as photographed)
    private const val MASK_DARK = 0xFF17573A.toInt()
    private const val COPPER = 0xFF2F8A58.toInt()      // a trace under the mask: a lighter green
    private const val GOLD = 0xFFCFA448.toInt()
    private const val GOLD_DARK = 0xFF9C7A30.toInt()
    private const val CHIP = 0xFF1A1A1C.toInt()
    private const val LEAD = 0xFFB8BCC0.toInt()

    private val cache = android.util.LruCache<String, Bitmap>(8)

    /** The board's outline (notch on the top right as seen from the label side; [mirror]: seen from behind). */
    fun outline(w: Float, h: Float, mirror: Boolean): Path = Path().apply {
        val nw = w * NOTCH_W; val nh = h * NOTCH_H
        if (!mirror) { moveTo(0f, 0f); lineTo(w - nw, 0f); lineTo(w - nw, nh); lineTo(w, nh); lineTo(w, h); lineTo(0f, h) }
        else { moveTo(nw, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h); lineTo(0f, nh); lineTo(nw, nh) }
        close()
    }

    /** The chip (label) side, [w]×[h] px. */
    fun chipSide(type: Int?, w: Int, h: Int): Bitmap = draw("c", partsOf(type), w, h)
    /** The solder side, as seen from behind (reads right from behind), [w]×[h] px. */
    fun solderSide(type: Int?, w: Int, h: Int): Bitmap = draw("s", partsOf(type), w, h)
    /** Only the parts standing on the chip side (their silhouettes, for their thickness seen through the side). */
    fun partsOnly(type: Int?, w: Int, h: Int): Bitmap = draw("p", partsOf(type), w, h)

    private fun draw(side: String, parts: Parts, w0: Int, h0: Int): Bitmap {
        val w = w0.coerceAtLeast(8); val h = h0.coerceAtLeast(8)
        val key = "$side|${parts.key}|$w|$h"
        synchronized(cache) { cache.get(key)?.let { return it } }
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val fw = w.toFloat(); val fh = h.toFloat()
        when (side) {
            "c" -> chip(c, p, fw, fh, parts)
            "s" -> solder(c, p, fw, fh, parts)
            else -> standing(c, p, fw, fh, parts)
        }
        synchronized(cache) { cache.put(key, b) }
        return b
    }

    private fun r(a: FloatArray, w: Float, h: Float) = RectF(a[0] * w, a[1] * h, (a[0] + a[2]) * w, (a[1] + a[3]) * h)

    /** Deterministic "random" (the same board every time). */
    private class Rng(var s: Int) { fun next(): Float { s = s * 1103515245 + 12345; return ((s ushr 8) and 0xFFFF) / 65535f } }

    private fun substrate(c: Canvas, p: Paint, w: Float, h: Float, mirror: Boolean) {
        val o = outline(w, h, mirror)
        p.style = Paint.Style.FILL
        p.shader = android.graphics.LinearGradient(0f, 0f, w, h, MASK, MASK_DARK, android.graphics.Shader.TileMode.CLAMP)
        c.drawPath(o, p); p.shader = null
        c.save(); c.clipPath(o)
    }

    /** Traces: groups of parallel lines with 45° corners between [from] and [to] points, under the mask. */
    private fun bus(c: Canvas, p: Paint, x0: Float, y0: Float, x1: Float, y1: Float, n: Int, gap: Float, stroke: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = stroke; p.color = COPPER; p.strokeJoin = Paint.Join.ROUND
        for (i in 0 until n) {
            val o = i * gap
            val path = Path()
            path.moveTo(x0 + o, y0)
            val mid = y0 + (y1 - y0) * 0.45f - o
            path.lineTo(x0 + o, mid)
            val dx = (x1 + o) - (x0 + o)
            val run = kotlin.math.abs(dx)
            path.lineTo(x1 + o, mid + run.coerceAtMost(kotlin.math.abs(y1 - mid)) * kotlin.math.sign(y1 - mid))
            path.lineTo(x1 + o, y1)
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun via(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.color = GOLD_DARK; c.drawCircle(x, y, r, p)
        p.color = 0xFF0E2A1C.toInt(); c.drawCircle(x, y, r * 0.45f, p)
    }

    private fun fingers(c: Canvas, p: Paint, w: Float, h: Float, gold: Boolean) {
        val top = FINGERS_TOP * h
        val l = FINGERS_L * w; val rgt = FINGERS_R * w
        val pitch = (rgt - l) / FINGERS
        for (i in 0 until FINGERS) {
            val x = l + i * pitch
            if (gold) {
                p.color = GOLD; c.drawRect(x + pitch * 0.14f, top, x + pitch * 0.86f, h, p)
                p.color = 0x33FFFFFF; c.drawRect(x + pitch * 0.2f, top, x + pitch * 0.38f, h, p)
            } else {
                // (Behind: the fingers' stubs under the mask, a via at each one's top — PokemonSilverBoardBackside.)
                p.color = COPPER; c.drawRect(x + pitch * 0.22f, top + h * 0.01f, x + pitch * 0.78f, h, p)
                via(c, p, x + pitch / 2f, top - h * 0.012f, pitch * 0.22f)
            }
        }
    }

    private fun holes(c: Canvas, p: Paint, w: Float, h: Float, mirror: Boolean) {
        fun mx(x: Float) = if (mirror) 1f - x else x
        p.color = GOLD_DARK; c.drawCircle(mx(SCREW[0]) * w, SCREW[1] * h, SCREW[2] * w, p)
        p.color = 0xFFB0B4B8.toInt(); c.drawCircle(mx(SCREW[0]) * w, SCREW[1] * h, SCREW[3] * w, p)
        p.color = 0xFF0C0C0C.toInt(); c.drawCircle(mx(SMALL_HOLE[0]) * w, SMALL_HOLE[1] * h, SMALL_HOLE[2] * w, p)
    }

    private fun soic(c: Canvas, p: Paint, rc: RectF, leadsAcross: Boolean, n: Int) {
        // Leads on two sides (SOP), then the body.
        p.color = LEAD
        val lead = if (leadsAcross) rc.width() * 0.09f else rc.height() * 0.09f
        for (i in 0 until n) {
            if (leadsAcross) {
                val y = rc.top + rc.height() * (i + 0.5f) / n
                val t = rc.height() / n * 0.28f
                c.drawRect(rc.left, y - t, rc.left + lead, y + t, p); c.drawRect(rc.right - lead, y - t, rc.right, y + t, p)
            } else {
                val x = rc.left + rc.width() * (i + 0.5f) / n
                val t = rc.width() / n * 0.28f
                c.drawRect(x - t, rc.top, x + t, rc.top + lead, p); c.drawRect(x - t, rc.bottom - lead, x + t, rc.bottom, p)
            }
        }
        p.color = CHIP
        val body = if (leadsAcross) RectF(rc.left + lead * 0.8f, rc.top, rc.right - lead * 0.8f, rc.bottom) else RectF(rc.left, rc.top + lead * 0.8f, rc.right, rc.bottom - lead * 0.8f)
        c.drawRect(body, p)
        p.color = 0x22FFFFFF; c.drawRect(body.left, body.top, body.right, body.top + body.height() * 0.06f, p)
        // pin 1 dot
        p.color = 0xFF333336.toInt(); c.drawCircle(body.left + body.width() * 0.14f, body.bottom - body.width() * 0.14f, body.width() * 0.05f, p)
    }

    private fun qfp(c: Canvas, p: Paint, rc: RectF, perSide: Int) {
        p.color = LEAD
        val lead = rc.width() * 0.12f
        for (i in 0 until perSide) {
            val f = (i + 0.5f) / perSide
            val tx = rc.width() * 0.6f / perSide * 0.3f
            val x = rc.left + lead + (rc.width() - 2 * lead) * f
            val y = rc.top + lead + (rc.height() - 2 * lead) * f
            c.drawRect(x - tx, rc.top, x + tx, rc.top + lead, p); c.drawRect(x - tx, rc.bottom - lead, x + tx, rc.bottom, p)
            c.drawRect(rc.left, y - tx, rc.left + lead, y + tx, p); c.drawRect(rc.right - lead, y - tx, rc.right, y + tx, p)
        }
        p.color = CHIP; c.drawRect(rc.left + lead, rc.top + lead, rc.right - lead, rc.bottom - lead, p)
    }

    private fun chip(c: Canvas, p: Paint, w: Float, h: Float, parts: Parts) {
        substrate(c, p, w, h, mirror = false)
        val rng = Rng(parts.key.hashCode() + 7)
        val st = (w * 0.006f).coerceAtLeast(0.8f)
        // Traces from the chips down to the fingers, and round the battery (as the photo's kind and density).
        bus(c, p, w * 0.17f, h * 0.48f, w * 0.06f, h * 0.87f, 7, w * 0.012f, st)
        bus(c, p, w * 0.42f, h * 0.48f, w * 0.40f, h * 0.87f, 6, w * 0.012f, st)
        bus(c, p, w * 0.86f, h * 0.48f, w * 0.80f, h * 0.87f, 6, w * 0.012f, st)
        bus(c, p, w * 0.36f, h * 0.34f, w * 0.58f, h * 0.50f, 5, w * 0.012f, st)
        bus(c, p, w * 0.05f, h * 0.10f, w * 0.03f, h * 0.86f, 2, w * 0.012f, st)
        p.style = Paint.Style.STROKE; p.strokeWidth = st; p.color = COPPER
        c.drawCircle(BATTERY[0] * w, BATTERY[1] * h, BATTERY[2] * w * 1.12f, p)
        p.style = Paint.Style.FILL
        // Vias.
        for (i in 0 until 46) {
            val x = 0.06f + rng.next() * 0.88f; val y = 0.08f + rng.next() * 0.78f
            if (x in 0.55f..0.93f && y in 0.12f..0.47f) continue // under the battery
            via(c, p, x * w, y * h, w * 0.0075f)
        }
        fingers(c, p, w, h, gold = true)
        holes(c, p, w, h, mirror = false)
        // Silkscreen outlines round the parts' places (white-ish, as on the board).
        p.style = Paint.Style.STROKE; p.strokeWidth = st * 0.8f; p.color = 0x66E8E4C8
        for (a in listOf(MAPPER, RAM, ROM)) c.drawRect(r(a, w, h).apply { inset(-w * 0.008f, -w * 0.008f) }, p)
        p.style = Paint.Style.FILL
        standing(c, p, w, h, parts)
        c.restore()
    }

    /** The parts standing on the chip side. */
    private fun standing(c: Canvas, p: Paint, w: Float, h: Float, parts: Parts) {
        soic(c, p, r(ROM, w, h), leadsAcross = true, n = 16)
        if (parts.ram) soic(c, p, r(RAM, w, h), leadsAcross = true, n = 14)
        if (parts.mapper) qfp(c, p, r(MAPPER, w, h), 8)
        if (parts.battery) {
            soic(c, p, r(RESET, w, h), leadsAcross = false, n = 4)
            val cx = BATTERY[0] * w; val cy = BATTERY[1] * h; val rr = BATTERY[2] * w
            p.shader = android.graphics.RadialGradient(cx - rr * 0.3f, cy - rr * 0.3f, rr * 1.3f, 0xFFD9B9A0.toInt(), 0xFF8E6A55.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, rr, p); p.shader = null
            p.color = 0xFF7A5A48.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = rr * 0.05f; c.drawCircle(cx, cy, rr * 0.93f, p); p.style = Paint.Style.FILL
            // The spot-welded tab across it, to the lower right (as photographed).
            p.color = 0xFFB4B8BC.toInt()
            c.save(); c.rotate(48f, cx, cy); c.drawRect(cx - rr * 0.12f, cy - rr * 0.55f, cx + rr * 0.12f, cy + rr * 1.15f, p); c.restore()
        }
        if (parts.crystal) {
            val rc = r(CRYSTAL, w, h)
            p.shader = android.graphics.LinearGradient(0f, rc.top, 0f, rc.bottom, 0xFFD8DADC.toInt(), 0xFF8C9094.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRoundRect(rc, rc.height() / 2f, rc.height() / 2f, p); p.shader = null
        }
        p.color = 0xFFA89880.toInt()
        for (a in PASSIVES) c.drawRect(r(a, w, h), p)
    }

    private fun solder(c: Canvas, p: Paint, w: Float, h: Float, parts: Parts) {
        substrate(c, p, w, h, mirror = true)
        val st = (w * 0.006f).coerceAtLeast(0.8f)
        // The big bundle (PokemonSilverBoardBackside: from the chips' places to the fingers, mostly lower middle).
        bus(c, p, w * 0.20f, h * 0.38f, w * 0.12f, h * 0.86f, 12, w * 0.011f, st)
        bus(c, p, w * 0.45f, h * 0.32f, w * 0.50f, h * 0.86f, 10, w * 0.011f, st)
        bus(c, p, w * 0.70f, h * 0.45f, w * 0.78f, h * 0.86f, 6, w * 0.011f, st)
        bus(c, p, w * 0.30f, h * 0.20f, w * 0.62f, h * 0.30f, 5, w * 0.011f, st)
        // Vias in rows under where the chips' pins are (mirrored: seen from behind).
        fun row(a: FloatArray, n: Int) {
            val rc = r(floatArrayOf(1f - a[0] - a[2], a[1], a[2], a[3]), w, h)
            for (i in 0 until n) { val y = rc.top + rc.height() * (i + 0.5f) / n; via(c, p, rc.left - w * 0.012f, y, w * 0.007f); via(c, p, rc.right + w * 0.012f, y, w * 0.007f) }
        }
        row(ROM, 8)
        if (parts.ram) row(RAM, 7)
        if (parts.mapper) row(MAPPER, 4)
        if (parts.battery) {
            // The battery's two solder lugs, through from the front.
            val cx = (1f - BATTERY[0]) * w; val cy = BATTERY[1] * h; val rr = BATTERY[2] * w
            p.color = 0xFFB4B8BC.toInt(); c.drawRect(cx - rr * 0.15f, cy - rr * 1.02f, cx + rr * 0.15f, cy - rr * 0.82f, p)
            c.drawRect(cx + rr * 0.65f, cy + rr * 0.62f, cx + rr * 0.95f, cy + rr * 0.82f, p)
        }
        p.color = GOLD
        for (a in TEST_PADS) { val rc = r(a, w, h); c.drawRoundRect(rc, rc.width() / 2f, rc.width() / 2f, p) }
        fingers(c, p, w, h, gold = false)
        holes(c, p, w, h, mirror = true)
        c.restore()
    }
}
