package com.shelf.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.getValue

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/*
 * Update 305: a cardboard box's other sides — its right side, top and bottom (Case3D faces 2, 3 and 5). Update 294 made
 * them plain card (repeating the spine there printed the wrong side's text). Now, independently for each side
 * (Settings › Game look › Box sides):
 *  1. a picture of that very side you've added (Artwork & details: "Box right side", "Box top", "Box bottom") — with
 *     "Authentic only" just the ones you've marked as a real packaging scan;
 *  2. otherwise ("Fill missing sides") a print made for this game from what's known for sure: its title, its console's logo
 *     (the logo set Voltcap ships) and the colours of its own box — a thin pinstripe at the ends, nothing else (no
 *     invented text, ratings or codes); it isn't presented as a scan;
 *  3. or plain card ("Plain cardboard", and for anything that fails).
 * The box's size, shape, folds and opening are unchanged; the pictures are fitted onto each side as it is.
 */
internal enum class BoxSide(val label: String) {
    RIGHT("Box right side"), TOP("Box top"), BOTTOM("Box bottom")
}

internal object BoxSides {
    /** The pixel size a side is made at: the box's height ([h] = 1) to this many pixels. */
    // (Made at about the size a box is drawn in the game window — 560 px tall — so a shelf of boxes stays light: under
    // 1.5 MB of pictures a box, kept in DerivedArt with the rest.)
    private const val PX = 560f

    /** The size of [side]'s picture for a box [shape] wide (width / height) and [depth] deep (depth / height). */
    fun sizeOf(side: BoxSide, shape: Float, depth: Float): Pair<Int, Int> = when (side) {
        BoxSide.RIGHT -> (PX * depth).toInt().coerceAtLeast(8) to PX.toInt()
        BoxSide.TOP, BoxSide.BOTTOM -> (PX * shape).toInt().coerceAtLeast(8) to (PX * depth).toInt().coerceAtLeast(8)
    }

    /**
     * Your picture of a side fitted onto it: turned a quarter when its long side runs the other way (a side photographed
     * lying down), then cut in the middle when it's within 15 % of the side's shape, else fitted whole on a band of its own
     * edge colour — never stretched.
     */
    fun fit(src: Bitmap, w: Int, h: Int): Bitmap {
        var b = src
        if ((b.width > b.height) != (w > h) && b.width != b.height) {
            val m = android.graphics.Matrix().apply { postRotate(90f) }
            b = runCatching { Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true) }.getOrDefault(b)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val want = w.toFloat() / h; val have = b.width.toFloat() / b.height
        if (kotlin.math.abs(have / want - 1f) <= 0.15f) {
            val r = if (have > want) {
                val cw = (b.height * want).toInt().coerceAtLeast(1); Rect((b.width - cw) / 2, 0, (b.width - cw) / 2 + cw, b.height)
            } else {
                val ch = (b.width / want).toInt().coerceAtLeast(1); Rect(0, (b.height - ch) / 2, b.width, (b.height - ch) / 2 + ch)
            }
            c.drawBitmap(b, r, Rect(0, 0, w, h), p)
        } else {
            c.drawColor(averageColor(b))
            val s = minOf(w.toFloat() / b.width, h.toFloat() / b.height)
            val dw = b.width * s; val dh = b.height * s
            c.drawBitmap(b, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), p)
        }
        return out
    }

    /**
     * Update 319: [side] in one colour — [color], the cover's edge that meets it — a touch darker towards its ends, as the
     * printed sides are (nothing printed on it).
     */
    fun colored(side: BoxSide, w: Int, h: Int, color: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = if (side == BoxSide.RIGHT) LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(shade(color, 0.9f), color, shade(color, 0.86f)), null, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(shade(color, 0.88f), color, shade(color, 0.88f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return out
    }

    /**
     * Update 465: the one colour a box's blank sides are printed in, from its cover ([front]) — the colour that covers most
     * of the cover's artwork, not its edge: the middle of the cover only (the outer 12 % left out — corner logos, the
     * console's band, the rating and the white border sit there), colours counted in coarse buckets with very light and
     * very dark ones (paper, outlines, text) passed over unless they ARE the cover, a coloured bucket weighted up a little
     * (a box's print reads as its colour, not its grey; only a little — on the KONKR a 0.6 weight made Silver's small gold emblem
     * beat its blue). The winning bucket's own pixels are averaged; a touch muted, as
     * card takes ink. Null: unreadable.
     */
    fun boxColour(front: Bitmap, platform: String? = null): Int? = runCatching {
        val f = if (front.config == Bitmap.Config.HARDWARE) front.copy(Bitmap.Config.ARGB_8888, false) else front
        val w = f.width; val h = f.height
        if (w < 8 || h < 8) return@runCatching null
        // (Where the console's box has a printed band — the N64's red NINTENDO 64 strip, a DS box's banner — only its art
        // window (the cover template's, MediaCovers) is looked at, then its middle.)
        val win = platform?.let { MediaCovers.of(it) }?.takeIf { kotlin.math.abs((w.toFloat() / h) / it.frontAspect - 1f) <= 0.15f }?.win
        val wl = win?.get(0) ?: 0f; val wt = win?.get(1) ?: 0f; val wr = win?.get(2) ?: 1f; val wb = win?.get(3) ?: 1f
        val mx = (wr - wl) * 0.12f; val my = (wb - wt) * 0.12f
        val x0 = (w * (wl + mx)).toInt(); val x1 = (w * (wr - mx)).toInt(); val y0 = (h * (wt + my)).toInt(); val y1 = (h * (wb - my)).toInt()
        if (x1 - x0 < 4 || y1 - y0 < 4) return@runCatching null
        val sx = ((x1 - x0) / 64).coerceAtLeast(1); val sy = ((y1 - y0) / 64).coerceAtLeast(1)
        val count = FloatArray(4096); val rs = LongArray(4096); val gs = LongArray(4096); val bs = LongArray(4096); val ns = IntArray(4096)
        var total = 0; var extremes = 0
        // (Update 466: the samples on their grid first, so a dark pixel can be checked against its neighbours — see below.)
        val gw = (x1 - x0 + sx - 1) / sx; val gh = (y1 - y0 + sy - 1) / sy
        val grid = IntArray(gw * gh)
        for (j in 0 until gh) for (i in 0 until gw) grid[j * gw + i] = f.getPixel((x0 + i * sx).coerceAtMost(x1 - 1), (y0 + j * sy).coerceAtMost(y1 - 1))
        fun dist(a: Int, b: Int) = kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) + kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) + kotlin.math.abs((a and 255) - (b and 255))
        for (j in 0 until gh) for (i in 0 until gw) {
            val c = grid[j * gw + i]
            if ((c ushr 24) > 200) {
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
                val sat = if (mx == 0) 0f else (mx - mn).toFloat() / mx
                total++
                val extreme = (mx > 232 && sat < 0.12f) || mx < 24
                if (extreme) extremes++
                // (Update 466, owner: "without accidentally selecting … logos": a dark pixel counts only as much as its 3×3
                // neighbours share its colour — the black outlines and strokes round drawn characters and lettering are thin,
                // a dark background is not. Paper Mario's sides were the near-black of its outlines.)
                var flat = 1f
                if (mx < 64) {
                    var same = 0; var all = 0
                    for (dj in -1..1) for (di in -1..1) {
                        val ii = i + di; val jj = j + dj
                        if (ii in 0 until gw && jj in 0 until gh) { all++; if (dist(c, grid[jj * gw + ii]) < 40) same++ }
                    }
                    flat = same.toFloat() / all
                }
                val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                count[k] += (if (extreme) 0.15f else 1f + 0.3f * sat) * flat
                rs[k] += r.toLong(); gs[k] += g.toLong(); bs[k] += b.toLong(); ns[k]++
            }
        }
        if (total == 0) return@runCatching null
        // (A cover that's mostly white or black — a white Nintendo 64 Kirby box — is that colour.)
        if (extremes > total * 0.55f) for (k in 0 until 4096) if (ns[k] > 0) count[k] = ns[k].toFloat()
        // Neighbouring buckets count together (a gradient's shades are one colour), the best one wins.
        fun near(k: Int): Float {
            val r = k shr 8; val g = (k shr 4) and 15; val b = k and 15; var s = 0f
            for (dr in -1..1) for (dg in -1..1) for (db in -1..1) {
                val rr = r + dr; val gg = g + dg; val bb = b + db
                if (rr in 0..15 && gg in 0..15 && bb in 0..15) s += count[(rr shl 8) or (gg shl 4) or bb] * (if (dr == 0 && dg == 0 && db == 0) 1f else 0.5f)
            }
            return s
        }
        var best = -1; var bestS = 0f
        for (k in 0 until 4096) if (ns[k] > 0) { val s = near(k); if (s > bestS) { bestS = s; best = k } }
        if (best < 0) return@runCatching null
        val n = ns[best].coerceAtLeast(1)
        val r = (rs[best] / n).toInt(); val g = (gs[best] / n).toInt(); val b = (bs[best] / n).toInt()
        val grey = (r * 30 + g * 59 + b * 11) / 100
        fun mute(v: Int) = (v * 0.9f + grey * 0.1f).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (mute(r) shl 16) or (mute(g) shl 8) or mute(b)
    }.getOrNull()

    /**
     * Update 465: [side] as plain printed card in [color]: flat (no strong gradient, no gloss), a fine fibre grain so it reads
     * as card, and the folds — a slightly darker line along each edge where the board bends round the corner. Nothing
     * printed on it.
     */
    fun card(side: BoxSide, w: Int, h: Int, color: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        // A fixed seed per size: the same side looks the same every time it's drawn (no shimmer while turning).
        var seed = 0x2F6E2B1 xor (w * 7919) xor (h * 104729)
        fun rnd(): Int { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5); return seed }
        val r0 = (color shr 16) and 255; val g0 = (color shr 8) and 255; val b0 = color and 255
        val fold = (minOf(w, h) * 0.035f).coerceIn(1f, 6f)
        for (y in 0 until h) {
            // (Fibres run along the board: a faint streak per row, plus fine grain.)
            val streak = ((rnd() and 7) - 3) * 0.6f
            for (x in 0 until w) {
                val grain = ((rnd() and 15) - 7) * 0.45f + streak
                val edge = minOf(minOf(x, w - 1 - x), minOf(y, h - 1 - y)).toFloat()
                val f = if (edge < fold) 0.86f + 0.14f * (edge / fold) else 1f
                val r = (r0 * f + grain).toInt().coerceIn(0, 255)
                val g = (g0 * f + grain).toInt().coerceIn(0, 255)
                val b = (b0 * f + grain).toInt().coerceIn(0, 255)
                px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Update 319: the average colour of [front]'s edge that meets [side] (its outer 6 %), or null. */
    fun edgeColor(front: Bitmap, side: BoxSide): Int? = runCatching {
        val f = if (front.config == Bitmap.Config.HARDWARE) front.copy(Bitmap.Config.ARGB_8888, false) else front
        val w = f.width; val h = f.height
        if (w < 4 || h < 4) return@runCatching null
        val band = (minOf(w, h) * 0.06f).toInt().coerceAtLeast(1)
        val r = when (side) {
            BoxSide.RIGHT -> Rect(w - band, 0, w, h)
            BoxSide.TOP -> Rect(0, 0, w, band)
            BoxSide.BOTTOM -> Rect(0, h - band, w, h)
        }
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        val stepX = ((r.width()) / 24).coerceAtLeast(1); val stepY = ((r.height()) / 24).coerceAtLeast(1)
        var y = r.top
        while (y < r.bottom) {
            var x = r.left
            while (x < r.right) {
                val px = f.getPixel(x, y); rs += (px shr 16) and 255; gs += (px shr 8) and 255; bs += px and 255; n++
                x += stepX
            }
            y += stepY
        }
        if (n == 0) null else (0xFF shl 24) or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
    }.getOrNull()

    /**
     * A print for [side] of this game's box: [base] the colour it's printed on (the box's own — its front's edge for the
     * right side, its spine's for the top and bottom), [title], [platform]'s logo, [accent] (the cover's) in the pinstripes.
     */
    fun print(context: android.content.Context, side: BoxSide, w: Int, h: Int, base: Int, title: String, platform: String?, accent: Int?): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val lum = lum(base)
        val ink = if (lum > 150f) 0xFF15171B.toInt() else 0xFFF6F6F2.toInt()
        val stripe = accent ?: (if (lum > 150f) 0xFF3A3F48.toInt() else 0xFFE8E2D0.toInt())
        // The card, a touch darker towards its ends (printed card, lit from the front).
        p.shader = if (side == BoxSide.RIGHT) LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(shade(base, 0.9f), base, shade(base, 0.86f)), null, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(shade(base, 0.88f), base, shade(base, 0.88f)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        val logo = platform?.let { runCatching { PlatformLogos.baked(context, it)?.asAndroidBitmap() }.getOrNull() }
        val monoLogo = platform != null && PlatformLogos.mono(platform)
        when (side) {
            BoxSide.RIGHT -> {
                // Pinstripes at both ends, the title down the middle (reading top to bottom, as the spine), the logo at the foot.
                pinstripes(c, p, 0f, w * 0.18f, w.toFloat(), stripe, vertical = false, along = w.toFloat(), count = 3, thick = w * 0.035f)
                pinstripes(c, p, 0f, h - w * 0.18f - w * 0.035f * 5, w.toFloat(), stripe, vertical = false, along = w.toFloat(), count = 3, thick = w * 0.035f)
                val logoRoom = if (logo != null) w * 0.9f else 0f
                val top = w * 0.55f; val bottom = h - w * 0.55f - logoRoom
                drawTitle(c, title, ink, along = bottom - top, across = w * 0.62f) { tw ->
                    c.translate(w / 2f, top + (bottom - top - tw) / 2f); c.rotate(90f)
                }
                if (logo != null) drawLogo(c, p, logo, monoLogo, ink, RectF(w * 0.18f, h - w * 0.5f - logoRoom * 0.75f, w * 0.82f, h - w * 0.5f))
            }
            BoxSide.TOP, BoxSide.BOTTOM -> {
                pinstripes(c, p, 0f, h * 0.1f, w.toFloat(), stripe, vertical = false, along = w.toFloat(), count = 2, thick = h * 0.035f)
                pinstripes(c, p, 0f, h * 0.83f, w.toFloat(), stripe, vertical = false, along = w.toFloat(), count = 2, thick = h * 0.035f)
                val logoW = if (logo != null) minOf(h * 0.55f * logo.width / logo.height.coerceAtLeast(1), w * 0.2f) else 0f
                val x0 = w * 0.05f + (if (logo != null) logoW + w * 0.03f else 0f)
                if (logo != null) {
                    val lh = logoW * logo.height / logo.width.coerceAtLeast(1)
                    drawLogo(c, p, logo, monoLogo, ink, RectF(w * 0.05f, (h - lh) / 2f, w * 0.05f + logoW, (h + lh) / 2f))
                }
                val across = h * (if (side == BoxSide.TOP) 0.42f else 0.3f)
                drawTitle(c, title, ink, along = w * 0.95f - x0, across = across) { tw ->
                    c.translate(x0 + (w * 0.95f - x0 - tw) / 2f, h / 2f)
                }
            }
        }
        return out
    }

    /** [title] in bold, as big as [across] allows and smaller for long names; [place] moves to where it starts. */
    private inline fun drawTitle(c: Canvas, title: String, ink: Int, along: Float, across: Float, place: (Float) -> Unit) {
        val t = title.trim().takeIf { it.isNotEmpty() } ?: return
        if (along < 10f || across < 4f) return
        val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); textSize = across * 0.8f
        }
        // (Update 391: smaller, then a second line; never "…" — SpineTitleFit. [place] gets the full run, the text centres in it.)
        val lines = SpineTitleFit.fit(tp, t, along, across, tp.textSize, across * 0.26f)
        c.save()
        place(along)
        SpineTitleFit.draw(c, tp, lines, along)
        c.restore()
    }

    private fun drawLogo(c: Canvas, p: Paint, logo: Bitmap, mono: Boolean, ink: Int, r: RectF) {
        val s = minOf(r.width() / logo.width, r.height() / logo.height)
        val dw = logo.width * s; val dh = logo.height * s
        val dst = RectF(r.centerX() - dw / 2f, r.centerY() - dh / 2f, r.centerX() + dw / 2f, r.centerY() + dh / 2f)
        // (A black logo on a dark box is drawn in the ink colour.)
        if (mono && lum(ink) > 150f) p.colorFilter = android.graphics.PorterDuffColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN)
        p.color = 0xFFFFFFFF.toInt()
        c.drawBitmap(logo, null, dst, p); p.colorFilter = null
    }

    private fun pinstripes(c: Canvas, p: Paint, x: Float, y: Float, w: Float, color: Int, vertical: Boolean, along: Float, count: Int, thick: Float) {
        p.color = color
        for (i in 0 until count) {
            val yy = y + i * thick * 2f
            p.alpha = (230 - i * 60).coerceAtLeast(80)
            c.drawRect(x, yy, x + w, yy + thick, p)
        }
        p.alpha = 255
    }

    private fun lum(c: Int): Float = ((c shr 16) and 255) * 0.3f + ((c shr 8) and 255) * 0.59f + (c and 255) * 0.11f

    private fun shade(c: Int, k: Float): Int {
        val r = (((c shr 16) and 255) * k).toInt().coerceIn(0, 255); val g = (((c shr 8) and 255) * k).toInt().coerceIn(0, 255)
        val b = ((c and 255) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * Update 305: a cardboard box's right side, top and bottom pictures (index: [BoxSide.ordinal]; null: plain card for that
 * side), as Settings › Game look › Box sides says — made away from the screen's thread and kept (DerivedArt); null until
 * they're ready, and for every side with "Plain cardboard".
 */
@androidx.compose.runtime.Composable
internal fun rememberBoxSides(
    g: com.shelf.launcher.data.Game, scans: List<Pair<String, String>>?, look: CaseLook, shape: Float, depth: Float, front: Bitmap?
): Array<Bitmap?>? {
    val mode = com.shelf.launcher.data.Features.boxSides.state.value
    val provVersion = com.shelf.launcher.data.ArtProvenance.version.intValue
    fun pick(s: BoxSide): String? = scans?.firstOrNull { it.first == s.label }?.second
        ?.takeIf { mode == com.shelf.launcher.data.Features.BoxSides.FILL || mode == com.shelf.launcher.data.Features.BoxSides.COLOUR || com.shelf.launcher.data.ArtProvenance.isScan(it) }
    val plain = mode == com.shelf.launcher.data.Features.BoxSides.PLAIN
    val u0 = if (plain) null else pick(BoxSide.RIGHT)
    // (Update 317: no picture of its top — a landscape box's top cut from its full case scan lying on its side, BoxPart.TOP.)
    val u1 = if (plain) null else pick(BoxSide.TOP) ?: (if (shape > 1.1f) BoxTexture.wrapTop(scans, g.platform) else null)
    val u2 = if (plain) null else pick(BoxSide.BOTTOM)
    val b0 = rememberSoftBitmap(u0, 900)
    val b1 = rememberSoftBitmap(u1, 900)
    val b2 = rememberSoftBitmap(u2, 900)
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // (A printed title is the game's name alone — no "(Rumble Version) (GB Compatible)" file tags on the card.)
    val title = printTitle(g.name)
    val platform = LookStyles.casePlatform(g.platform)
    val fill = mode == com.shelf.launcher.data.Features.BoxSides.FILL
    // (Update 465: one colour from the cover's artwork on every side without a picture of its own — your pictures and real
    // scans of a side still win, as with "Fill missing sides".)
    val oneColour = mode == com.shelf.launcher.data.Features.BoxSides.COLOUR
    // (Update 319: with the print off, these sides take the cover's edge colour instead of plain card.)
    val colorSides = com.shelf.launcher.data.Features.boxSideColors.state.value
    val made by produceKeyed<Array<Bitmap?>?>(null, title to platform, mode, b0, b1, b2, look.edge, look.top, shape, depth, front, title, platform, provVersion, colorSides) {
        if (plain && (colorSides.isEmpty() || front == null)) { value = null; return@produceKeyed }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val accent = front?.let { f -> DerivedArt.of(f, "accent") { CollectorBacks.accentOf(f) ?: 0 } }?.takeIf { it != 0 }
                arrayOf(b0, b1, b2).mapIndexed { i, b ->
                    val side = BoxSide.values()[i]
                    val (w, h) = BoxSides.sizeOf(side, shape, depth)
                    if (b != null) DerivedArt.of(b, "side|$i|${w}x$h") { BoxSides.fit(b, w, h) }
                    else if (fill) {
                        val base = if (side == BoxSide.RIGHT) look.edge else look.top
                        DerivedArt.named("sideprint|$i|$w|$h|$base|$platform|$accent|$title") { BoxSides.print(context, side, w, h, base, title, platform, accent) }
                    } else if (oneColour && front != null) {
                        val col = DerivedArt.of(front, "boxcolour|$platform") { BoxSides.boxColour(front, platform) ?: 0 }.takeIf { it != 0 }
        BoxColourQa.dump(g.name, front, col)
                        col?.let { c -> DerivedArt.named("sidecard|$i|$w|$h|$c") { BoxSides.card(side, w, h, c) } }
                    } else if (side.name in colorSides && front != null) {
                        val edge = DerivedArt.of(front, "edge|$i") { BoxSides.edgeColor(front, side) ?: 0 }.takeIf { it != 0 }
                        edge?.let { col -> DerivedArt.named("sidecolor|$i|$w|$h|$col") { BoxSides.colored(side, w, h, col) } }
                    } else null
                }.toTypedArray()
            }.getOrNull()
        }
    }
    return made
}

/** Update 305: a game's name as printed on generated packaging — without the file's "(USA)", "(Rumble Version)", "[!]" tags. */
internal fun printTitle(name: String): String = printTitles.getOrPut(name) {
    // (Update 322, Thor performance recording: 0.8 s of 15 s compiling these two patterns again for every case drawn in
    // every frame — compiled once now, and each title worked out once.)
    val t = gameDisplayName(name).replace(PRINT_TAGS, "").replace(PRINT_SPACES, " ").trim()
    t.ifBlank { gameDisplayName(name) }
}

private val PRINT_TAGS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
private val PRINT_SPACES = Regex("\\s+")
private val printTitles = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * Update 466 (QA only, test hooks on): the box's front picture as the 3D box gets it and the side colour taken from it,
 * once per game, into <external files>/qa_boxcolour/ (PNG + colours.tsv) — so the colour choice and the front's fit can be
 * checked against the real cover. Nothing is written without the hooks flag.
 */
internal object BoxColourQa {
    private val done = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun dump(name: String, front: Bitmap, col: Int?) {
        if (!QaTrace.on) return
        val d = QaTrace.dir ?: return
        if (!done.add(name)) return
        Thread {
            runCatching {
                val o = java.io.File(d, "qa_boxcolour").apply { mkdirs() }
                val safe = name.replace(Regex("[^A-Za-z0-9]+"), "_").take(60)
                val b = if (front.config == Bitmap.Config.HARDWARE) front.copy(Bitmap.Config.ARGB_8888, false) else front
                java.io.FileOutputStream(java.io.File(o, "$safe.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                java.io.File(o, "colours.tsv").appendText("$name\t${b.width}x${b.height}\t" + (col?.let { "#%06x".format(it and 0xffffff) } ?: "none") + "\n")
            }
        }.start()
    }
}
