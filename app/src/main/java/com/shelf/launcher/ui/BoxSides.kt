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
        while (tp.measureText(t) > along && tp.textSize > across * 0.26f) tp.textSize -= 1f
        var s = t
        if (tp.measureText(s) > along) {
            while (s.length > 2 && tp.measureText("$s…") > along) s = s.dropLast(1)
            s = s.trimEnd() + "…"
        }
        val fm = tp.fontMetrics
        c.save()
        place(tp.measureText(s))
        c.translate(0f, -(fm.ascent + fm.descent) / 2f)
        c.drawText(s, 0f, 0f, tp)
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
        ?.takeIf { mode == com.shelf.launcher.data.Features.BoxSides.FILL || com.shelf.launcher.data.ArtProvenance.isScan(it) }
    val plain = mode == com.shelf.launcher.data.Features.BoxSides.PLAIN
    val u0 = if (plain) null else pick(BoxSide.RIGHT)
    val u1 = if (plain) null else pick(BoxSide.TOP)
    val u2 = if (plain) null else pick(BoxSide.BOTTOM)
    val b0 = rememberSoftBitmap(u0, 900)
    val b1 = rememberSoftBitmap(u1, 900)
    val b2 = rememberSoftBitmap(u2, 900)
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // (A printed title is the game's name alone — no "(Rumble Version) (GB Compatible)" file tags on the card.)
    val title = printTitle(g.name)
    val platform = LookStyles.casePlatform(g.platform)
    val fill = mode == com.shelf.launcher.data.Features.BoxSides.FILL
    val made by androidx.compose.runtime.produceState<Array<Bitmap?>?>(null, mode, b0, b1, b2, look.edge, look.top, shape, depth, front, title, platform, provVersion) {
        if (plain) { value = null; return@produceState }
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
                    } else null
                }.toTypedArray()
            }.getOrNull()
        }
    }
    return made
}

/** Update 305: a game's name as printed on generated packaging — without the file's "(USA)", "(Rumble Version)", "[!]" tags. */
internal fun printTitle(name: String): String {
    val t = gameDisplayName(name).replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace(Regex("\\s+"), " ").trim()
    return t.ifBlank { gameDisplayName(name) }
}
