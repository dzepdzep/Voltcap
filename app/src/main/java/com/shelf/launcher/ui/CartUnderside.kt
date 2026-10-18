package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/*
 * Update 240 (#92): the cartridge's bottom — the narrow edge that goes into the console, seen looking up into it. The
 * turned piece (InspectSlab's slices, SolidPiece) makes that edge out of its outline's slices in its plastic colour:
 * a solid edge with no opening. This is the edge as a face of its own for the shells with a researched connector
 * opening: the plastic, the opening between the halves and the board's edge inside it (with its contact rows where
 * the connector's pitch is published). Nothing is invented for a shell without one: [CartUndersides.of] is null and the
 * piece keeps its plain edge. One small picture per shell and colour (cached); the turned view draws it on the bottom
 * plane with [CartUndersides.bottomMatrix] when the piece is tilted to show its underside.
 *
 * Where the numbers come from (per shell below):
 *  - the opening's place and width across the cartridge: the connector area measured off rear photos in CartBacks
 *    ("photo"; Famicom / Super Famicom "unverified"), mirrored to the front's left-to-right;
 *  - contact rows: NES 36 a side at 2.50 mm (https://www.nesdev.org/wiki/Cartridge_connector), Famicom 30 a side at
 *    2.54 mm (same page), SNES / Super Famicom 31 a side (https://consolemods.org/wiki/SNES:Connector_Pinouts) at 2.5 mm
 *    on a 1.2 mm board (calipers, https://nesdev.nes.science/f12/t10449.xhtml) — only the main block (pins 5–27) is
 *    drawn, the outer 4 a side are on expansion-chip games only;
 *  - Game Boy / Color / Advance and Game Gear: the contacts on the board's face left bare at the back, the moulded front
 *    half in front of it (https://consoleartisan.com/reference/cartridge-level-shifting/);
 *  - ESTIMATED everywhere else: how deep the opening is across the thickness (the walls), where the board sits in it,
 *    and every board thickness but the SNES's. No underside photo could be measured here, so every entry is
 *    [CaseCheck.ESTIMATED] overall.
 */

/**
 * A shell's bottom edge, mm. x across the cartridge from its left (seen from the front); z through its thickness from
 * the front face (0) to the back (the shell's t).
 */
internal class CartBottom(
    val id: String,
    /** The connector opening: x, width. */
    val openX: Float, val openW: Float,
    /** The opening through the thickness: from z0 to z1 (z1 = t: open to the back, the board's face bare there). */
    val openZ0: Float, val openZ1: Float,
    /** The board's edge in the opening: its front face at [pcbZ], [pcbT] thick. */
    val pcbZ: Float, val pcbT: Float,
    /** The contact rows' width (centred in the opening; 0: pitch unpublished, not drawn) and sides: 1 front, 2 back, 3 both. */
    val padW: Float, val padSides: Int,
    val check: CaseCheck,
    /** Where each value comes from (SOURCED with its address / ESTIMATED). */
    val source: String
)

internal object CartUndersides {
    private fun b(id: String, x: Float, w: Float, z0: Float, z1: Float, pz: Float, pt: Float, padW: Float, sides: Int, src: String) =
        CartBottom(id, x, w, z0, z1, pz, pt, padW, sides, CaseCheck.ESTIMATED, src)

    // Double-sided edge connectors (the board's edge between the two halves): walls 1.5 mm and the board in the middle
    // are ESTIMATED; the board 1.2 mm (SOURCED for the SNES only).
    private fun mid(id: String, x: Float, w: Float, t: Float, padW: Float, src: String) =
        b(id, x, w, 1.5f, t - 1.5f, t / 2f - 0.6f, 1.2f, padW, 3, src)

    // Contacts on the board's back face, bare at the back (Game Boy family, Game Gear): the board 1.2 mm flush with the
    // back and a 1.2 mm gap in front of it — both ESTIMATED.
    private fun face(id: String, x: Float, w: Float, t: Float, src: String) =
        b(id, x, w, t - 2.4f, t, t - 1.2f, 1.2f, 0f, 2, src)

    val all: Map<String, CartBottom> = listOf(
        // NES: CartBacks opening 6 + 108.5 from behind (photo) → 5.5 from the left; 36 × 2.50 = 90 mm of contacts
        // (SOURCED, NESdev). The cartridge has no door of its own (the dust door is the console's): none drawn.
        mid("nes", 5.5f, 108.5f, 17f, 90f, "opening: CartBacks photo; contacts 36 × 2.50 mm https://www.nesdev.org/wiki/Cartridge_connector; walls, board place ESTIMATED"),
        // Famicom: opening 9 + 90 (CartBacks unverified) → 9; 30 × 2.54 = 76.2 mm (SOURCED, NESdev).
        mid("famicom", 9f, 90f, 17f, 76.2f, "opening: CartBacks unverified; contacts 30 × 2.54 mm https://www.nesdev.org/wiki/Cartridge_connector; rest ESTIMATED"),
        // SNES (North America): opening 20 + 96 (photo) → 19.85; main block 23 × 2.5 = 57.5 mm. Its two keying grooves
        // are drawn on the back (CartBacks) — their depth into the bottom isn't known, so not here.
        mid("snes_na", 19.85f, 96f, 19.7f, 57.5f, "opening: CartBacks photo; 31 a side https://consolemods.org/wiki/SNES:Connector_Pinouts, 2.5 mm pitch, 1.2 mm board https://nesdev.nes.science/f12/t10449.xhtml; walls ESTIMATED"),
        // Super Famicom / PAL: opening 15 + 100 (CartBacks unverified) → 15; the same connector.
        mid("sfc", 15f, 100f, 20f, 57.5f, "opening: CartBacks unverified; connector as SNES (SOURCED); walls ESTIMATED"),
        // N64: opening 22 + 72 (photo) → 22. Pitch not found: no contact rows.
        mid("n64", 22f, 72f, 18.5f, 0f, "opening: CartBacks photo; walls, board place and thickness ESTIMATED; pitch not found"),
        // Mega Drive / Genesis: 8 + 93 (photo) → 8; Japan 8 + 77 (photo) → 8; Master System 6 + 97 (photo) → 6.1.
        mid("genesis", 8f, 93f, 17f, 0f, "opening: CartBacks photo; walls, board ESTIMATED; pitch not found"),
        mid("megadrive_jp", 8f, 77f, 17f, 0f, "opening: CartBacks photo; walls, board ESTIMATED; pitch not found"),
        mid("sms", 6.1f, 97f, 16.9f, 0f, "opening: CartBacks photo; walls, board ESTIMATED; pitch not found"),
        // Game Boy 3 + 51 (photo) → 3; Color (black) 3 + 51 → 3; Color (clear) 4 + 49 → 4; Advance 3 + 54 → 3; Game Gear
        // 6 + 54 → 6. Contacts on the board's back face (consoleartisan) — pitch unpublished in the sources read.
        face("gb", 3f, 51f, 7.8f, "opening: CartBacks photo; bare board face at the back https://consoleartisan.com/reference/cartridge-level-shifting/; depths ESTIMATED"),
        face("gbc_dual", 3f, 51f, 9f, "as Game Boy (CartBacks photo); depths ESTIMATED"),
        face("gbc", 4f, 49f, 9f, "as Game Boy (CartBacks photo); depths ESTIMATED"),
        face("gba", 3f, 54f, 8f, "opening: CartBacks photo; board face as Game Boy; depths ESTIMATED; the rear-corner shape-detector notch (Wikipedia) not drawn — its size not found"),
        face("game_gear", 6f, 54f, 10f, "opening: CartBacks photo; bare board face https://consoleartisan.com/reference/cartridge-level-shifting/; depths ESTIMATED")
    ).associateBy { it.id }

    /** [s]'s bottom, or null: no researched opening — the piece keeps its plain edge. */
    fun of(s: CartShell?): CartBottom? = s?.let { all[it.id] }

    /** The x range (mm) of [s]'s lowest edge (the outline's lowest points): an N64's or a Genesis's narrower foot. */
    fun span(s: CartShell): FloatArray {
        val v = s.outline
        var maxY = -1f
        var i = 0
        while (i + 2 < v.size) { maxY = maxOf(maxY, v[i + 1]); i += 3 }
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        i = 0
        while (i + 2 < v.size) {
            if (v[i + 1] >= maxY - 0.01f) { lo = minOf(lo, v[i]); hi = maxOf(hi, v[i]) }
            i += 3
        }
        return if (hi > lo) floatArrayOf(lo, hi) else floatArrayOf(0f, s.w)
    }

    private val cache = android.util.LruCache<String, Bitmap>(12)

    /**
     * [s]'s bottom in [colour] at [pxPerMm] (capped at 2048 px wide), rows from the front face (top row) to the back
     * (bottom row), columns left to right as seen from the front; null for a shell without [of].
     */
    fun picture(s: CartShell, colour: ShellColour, pxPerMm: Float): Bitmap? {
        val d = of(s) ?: return null
        val sp = span(s)
        val k = pxPerMm.coerceIn(1f, 2048f / (sp[1] - sp[0]).coerceAtLeast(1f))
        val key = "u|${s.id}|${colour.key}|${(k * 4f).toInt()}"
        cache.get(key)?.let { return it }
        val bw = ((sp[1] - sp[0]) * k).toInt().coerceAtLeast(4)
        val bh = (s.t * k).toInt().coerceAtLeast(2)
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun x(mm: Float) = (mm - sp[0]) * k
        fun z(mm: Float) = mm * k
        val op = colour.opacity
        val base = if (colour.backArgb != 0) colour.backArgb else colour.argb
        // The plastic, a little darker (it faces down, away from the light). (No seam line: where the halves meet on the
        // bottom wasn't found.)
        p.color = withAlpha(CaseInside.mix(base, 0xFF000000.toInt(), 0.18f), op)
        c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
        if (d.openW > 0f && d.openZ1 > d.openZ0) {
            val r = RectF(x(d.openX), z(d.openZ0), x(d.openX + d.openW), z(d.openZ1.coerceAtMost(s.t)))
            // The opening: dark inside, its lip lit along the front edge.
            p.color = 0xFF0C0C0E.toInt(); c.drawRoundRect(r, 0.4f * k, 0.4f * k, p)
            p.color = withAlpha(CaseInside.mix(base, 0xFFFFFFFF.toInt(), 0.2f), maxOf(op, 0.6f))
            c.drawRect(r.left, r.top - 0.25f * k, r.right, r.top, p)
            // The board's edge (green), 0.4 mm in from the opening's ends.
            val pcb = RectF(r.left + 0.4f * k, z(d.pcbZ), r.right - 0.4f * k, z(d.pcbZ + d.pcbT))
            p.color = 0xFF2C5B3B.toInt(); c.drawRect(pcb, p)
            // Its contact rows, where the pitch is published: the plated edge of each row, a gold hairline on that face.
            if (d.padW > 0f && d.padSides != 0) {
                val mid = d.openX + d.openW / 2f
                val l = x(mid - d.padW / 2f); val rr = x(mid + d.padW / 2f)
                val hair = maxOf(0.18f * k, 1f)
                p.color = 0xFFC9A646.toInt()
                if (d.padSides and 1 != 0) c.drawRect(l, pcb.top, rr, pcb.top + hair, p)
                if (d.padSides and 2 != 0) c.drawRect(l, pcb.bottom - hair, rr, pcb.bottom, p)
            }
        }
        cache.put(key, out)
        return out
    }

    /**
     * Sets [m] to draw [s]'s [picture] ([bmpW] px wide) on the piece's bottom plane in a node [nodeW] × [nodeH] where
     * the piece is fitted in the middle at its own shape and turned like InspectSlab's PiecePose: yaw cos [cy] / sin
     * [sy], tilt cos [cp] / sin [sp] round the node's middle, [depthPx] its thickness on screen. A point (x, y) of the
     * face at depth z (towards you) lands at (x·cy + z·sy, y·cp − x·sy·sp + z·cy·sp) about the middle, as there.
     * Returns whether the bottom faces you (it does when the top is tilted away, [sp] below 0) — draw it only then,
     * after the side slices and before the front / back face.
     */
    fun bottomMatrix(
        m: androidx.compose.ui.graphics.Matrix, s: CartShell, bmpW: Int, nodeW: Float, nodeH: Float, depthPx: Float,
        cy: Float, sy: Float, cp: Float, sp: Float
    ): Boolean {
        val spn = span(s)
        val pw = minOf(nodeW, nodeH * s.aspect); val ph = pw / s.aspect
        val left = (nodeW - pw) / 2f; val bottom = (nodeH + ph) / 2f
        val q = pw / s.w                         // node px per mm
        val kb = bmpW / (spn[1] - spn[0]).coerceAtLeast(0.01f)  // picture px per mm
        val zk = depthPx / s.t.coerceAtLeast(0.01f)            // depth px per mm
        val px = nodeW / 2f; val py = nodeH / 2f
        val x0 = left + spn[0] * q - px          // the picture's left column, from the middle
        val z0 = depthPx / 2f                    // its top row: the front face
        // X = px + cy·(x0 + i·q/kb) + sy·(z0 − j·zk/kb)
        // Y = py − sy·sp·(x0 + i·q/kb) + cp·(bottom − py) + cy·sp·(z0 − j·zk/kb)
        m.reset()
        val v = m.values
        v[0] = cy * q / kb
        v[1] = -sy * sp * q / kb
        v[4] = -sy * zk / kb
        v[5] = -cy * sp * zk / kb
        v[12] = px + cy * x0 + sy * z0
        v[13] = py - sy * sp * x0 + cp * (bottom - py) + cy * sp * z0
        return sp < -0.01f
    }

    private fun withAlpha(argb: Int, a: Float): Int = (argb and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)
}
