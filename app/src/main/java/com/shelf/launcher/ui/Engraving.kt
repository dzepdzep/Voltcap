package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * (Update 241, #44/#45/#46: marks moulded into the physical objects.
 *  - Every Save Vault storage object (the drawn memory card, SD card, floppy and capsule, the 3D card) carries
 *    "SaveVault" and Voltcap's own small cloud — side by side ("SaveVault ☁") or stacked ("SaveVault" over the cloud) —
 *    on a blank face of its plastic: the front's blank area where there is one, always its back. The cloud is branding,
 *    not a sync indicator.
 *  - Voltcap's custom platform cartridges (your pack's shells, CustomCarts.kt) carry their console's logo on the back
 *    instead (CustomCartBacks): never SaveVault's. The two never mix.
 * A mark is a relief, not a sticker: a low-contrast fill of the plastic's own colour with a light edge and a dark edge
 * 1–2 px apart (pressed in: the dark edge above / left, the light one below / right; raised: the other way round). The
 * edges' strength follows the plastic's lightness, so it reads on black, white, translucent and every colour in between.
 * Each mark is made once per colour, size and layout (an LRU of small bitmaps) — never per frame.)
 */
internal object Engraving {
    enum class Layout { HORIZONTAL, STACKED }

    const val WORD = "SaveVault"

    /** The cloud's width for 1 tall. */
    const val CLOUD_ASPECT = 1.6f

    /** Room round the mark for its edges (px). */
    private const val PAD = 3

    private val cache = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** A clean, narrow, retro-tech face (the system's condensed sans, bold). */
    private val face: Typeface by lazy { runCatching { Typeface.create("sans-serif-condensed", Typeface.BOLD) }.getOrNull() ?: Typeface.DEFAULT_BOLD }

    private fun textPaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face
        textSize = size
        color = android.graphics.Color.WHITE
        letterSpacing = 0.05f
    }

    /**
     * Voltcap's cloud as one outline, [h] tall at ([x], [y]): three round lobes on a flat, pill-ended base, and — once
     * it's big enough to show — a thin slot across the base (the retro-tech vent that makes it ours, not an emoji).
     */
    fun cloudPath(x: Float, y: Float, h: Float): Path {
        val w = h * CLOUD_ASPECT
        val out = Path()
        out.addRoundRect(RectF(x, y + h * 0.45f, x + w, y + h), h * 0.275f, h * 0.275f, Path.Direction.CW)
        val part = Path()
        fun lobe(cx: Float, cy: Float, r: Float) {
            part.reset(); part.addCircle(cx, cy, r, Path.Direction.CW); out.op(part, Path.Op.UNION)
        }
        lobe(x + w * 0.30f, y + h * 0.58f, h * 0.30f)
        lobe(x + w * 0.55f, y + h * 0.40f, h * 0.40f)
        lobe(x + w * 0.77f, y + h * 0.62f, h * 0.26f)
        if (h >= 10f) {
            val t = max(1f, h * 0.075f)
            part.reset()
            part.addRoundRect(RectF(x + w * 0.24f, y + h * 0.77f - t / 2f, x + w * 0.76f, y + h * 0.77f + t / 2f), t / 2f, t / 2f, Path.Direction.CW)
            out.op(part, Path.Op.DIFFERENCE)
        }
        return out
    }

    /** The word's advance, its ink height and its bounds' left / top at text size 100. */
    private class Metrics(val tw: Float, val th: Float, val left: Float, val top: Float)

    private val metrics: Metrics by lazy {
        val p = textPaint(100f)
        val b = android.graphics.Rect()
        p.getTextBounds(WORD, 0, WORD.length, b)
        Metrics(p.measureText(WORD), b.height().toFloat().coerceAtLeast(1f), b.left.toFloat(), b.top.toFloat())
    }

    /** The mark's own size at text size 100: width, height, the cloud's height. */
    private fun unit(layout: Layout): FloatArray {
        val m = metrics
        return if (layout == Layout.HORIZONTAL) {
            val ch = m.th * 1.05f
            floatArrayOf(m.tw + m.th * 0.45f + ch * CLOUD_ASPECT, max(m.th, ch), ch)
        } else {
            val ch = m.th * 1.5f
            floatArrayOf(max(m.tw, ch * CLOUD_ASPECT), m.th + m.th * 0.32f + ch, ch)
        }
    }

    /** Height for 1 wide of the SaveVault mark in [layout] (to place it before it's made). */
    fun ratio(layout: Layout): Float = unit(layout).let { it[1] / it[0] }

    /** The SaveVault mark's shape in white on clear, [widthPx] wide. */
    private fun saveVaultMask(widthPx: Int, layout: Layout): Bitmap? {
        val m = metrics
        val u = unit(layout)
        val uw = u[0]; val uh = u[1]; val ch = u[2]
        val s = (widthPx - 2 * PAD) / uw
        if (s <= 0f) return null
        val hPx = ceil(uh * s).toInt() + 2 * PAD
        val bmp = runCatching { Bitmap.createBitmap(widthPx, hPx, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        val p = textPaint(100f * s)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        if (layout == Layout.HORIZONTAL) {
            val top = PAD + (uh - m.th) / 2f * s
            c.drawText(WORD, PAD - m.left * s, top - m.top * s, p)
            c.drawPath(cloudPath(PAD + (m.tw + m.th * 0.45f) * s, PAD + (uh - ch) / 2f * s, ch * s), fill)
        } else {
            c.drawText(WORD, PAD + (uw - m.tw) / 2f * s - m.left * s, PAD - m.top * s, p)
            c.drawPath(cloudPath(PAD + (uw - ch * CLOUD_ASPECT) / 2f * s, PAD + m.th * 1.32f * s, ch * s), fill)
        }
        return bmp
    }

    private fun mixRgb(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int): Int { val x = (a shr sh) and 255; val y = (b shr sh) and 255; return (x + (y - x) * t).roundToInt().coerceIn(0, 255) }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun withAlpha(c: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (c and 0xFFFFFF)

    /** Relative lightness of an opaque colour (0 black … 1 white). */
    fun lightness(c: Int): Float {
        val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** The fill, light edge and dark edge for a mark in [shell]'s plastic. */
    private fun tones(shell: Int): IntArray {
        val l = lightness(shell)
        val fill = if (l > 0.45f) mixRgb(shell, 0xFF000000.toInt(), 0.13f) else mixRgb(shell, 0xFFFFFFFF.toInt(), 0.09f)
        val hi = withAlpha(mixRgb(shell, 0xFFFFFFFF.toInt(), 0.78f), 0.45f + 0.40f * (1f - l))
        val sh = withAlpha(mixRgb(shell, 0xFF000000.toInt(), 0.82f), 0.30f + 0.45f * l)
        return intArrayOf(fill, hi, sh)
    }

    /** [mask] as a relief in [shell]'s plastic: [recessed] pressed into it, else raised from it. */
    private fun relief(mask: Bitmap, shell: Int, recessed: Boolean): Bitmap? {
        val out = runCatching { Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(out)
        val t = tones(shell or 0xFF000000.toInt())
        val d = (mask.height * 0.035f).coerceIn(1f, (PAD - 1).toFloat())
        val light = if (recessed) d else -d
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        p.colorFilter = PorterDuffColorFilter(t[1], PorterDuff.Mode.SRC_IN); c.drawBitmap(mask, light, light, p)
        p.colorFilter = PorterDuffColorFilter(t[2], PorterDuff.Mode.SRC_IN); c.drawBitmap(mask, -light, -light, p)
        p.colorFilter = PorterDuffColorFilter(t[0], PorterDuff.Mode.SRC_IN); c.drawBitmap(mask, 0f, 0f, p)
        return out
    }

    /**
     * The SaveVault mark in [shell]'s plastic (opaque ARGB), [widthPx] wide (to 4 px steps, so neighbouring sizes share
     * one picture); null when it would be too small to read. Cached.
     */
    fun saveVault(shell: Int, widthPx: Int, layout: Layout, recessed: Boolean = true): Bitmap? {
        val w = (widthPx.coerceAtMost(1600) / 4) * 4
        if (w < 28) return null
        val key = "sv|${shell or 0xFF000000.toInt()}|$w|${layout.ordinal}|$recessed"
        cache.get(key)?.let { return it }
        val mask = saveVaultMask(w, layout) ?: return null
        val out = relief(mask, shell, recessed)
        mask.recycle()
        if (out != null) cache.put(key, out)
        return out
    }

    /** The average colour of [b]'s visible pixels in [r] (a few samples), as opaque ARGB; null when none show there. */
    fun sampleTone(b: Bitmap, r: RectF): Int? {
        if (b.config == Bitmap.Config.HARDWARE) return null
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        for (iy in 0 until 4) for (ix in 0 until 6) {
            val x = (r.left + (ix + 0.5f) / 6f * r.width()).toInt().coerceIn(0, b.width - 1)
            val y = (r.top + (iy + 0.5f) / 4f * r.height()).toInt().coerceIn(0, b.height - 1)
            val px = b.getPixel(x, y)
            if ((px ushr 24) < 40) continue
            rs += (px shr 16) and 255; gs += (px shr 8) and 255; bs += px and 255; n++
        }
        if (n == 0) return null
        return (0xFF shl 24) or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
    }

    private val atop = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }

    /**
     * The SaveVault mark moulded into the picture [target] (mutable) at centre ([cx], [cy]), [widthPx] wide, in the colour
     * of the plastic already there — only where [target] itself shows (never spilling past its outline). False when it
     * couldn't be made.
     */
    fun engraveOnto(target: Bitmap, cx: Float, cy: Float, widthPx: Int, layout: Layout): Boolean {
        val h = widthPx * ratio(layout)
        val tone = sampleTone(target, RectF(cx - widthPx / 2f, cy - h / 2f, cx + widthPx / 2f, cy + h / 2f)) ?: return false
        val m = saveVault(tone, widthPx, layout) ?: return false
        return runCatching { Canvas(target).drawBitmap(m, cx - m.width / 2f, cy - m.height / 2f, atop); true }.getOrDefault(false)
    }

    // ---- (Update 241, #45: a console's logo, moulded) ----

    /** [platform]'s logo (the app's baked one) — else its name in moulded letters — in white, fitted in [w] × [h]. */
    private fun platformMask(context: android.content.Context, platform: String, w: Int, h: Int): Bitmap? {
        val out = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(out)
        val raw = runCatching { PlatformLogos.baked(context, platform)?.asAndroidBitmap() }.getOrNull()
        val logo = raw?.let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        if (logo != null && logo.width > 0 && logo.height > 0) {
            val sc = min((w - 2 * PAD).toFloat() / logo.width, (h - 2 * PAD).toFloat() / logo.height)
            val dw = logo.width * sc; val dh = logo.height * sc
            // Only its shape counts (SRC_IN later): the logo's own colours never show.
            c.drawBitmap(logo, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val name = com.shelf.launcher.data.ConsoleNames.display(platform).uppercase()
            val p = textPaint(100f)
            val tw = p.measureText(name).coerceAtLeast(1f)
            p.textSize = min(100f * (w - 2 * PAD) / tw, (h - 2 * PAD) * 0.8f)
            p.textAlign = Paint.Align.CENTER
            val fm = p.fontMetrics
            c.drawText(name, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p)
        }
        return out
    }

    /**
     * [platform]'s logo raised from (or, [raised] false, pressed into) [shell]'s plastic, fitted in [w] × [h] px. For the
     * backs of Voltcap's custom platform cartridges only. Cached.
     */
    fun platformLogo(context: android.content.Context, platform: String, shell: Int, w: Int, h: Int, raised: Boolean = true): Bitmap? {
        if (w < 24 || h < 12) return null
        val key = "pl|$platform|${shell or 0xFF000000.toInt()}|$w|$h|$raised"
        cache.get(key)?.let { return it }
        val mask = platformMask(context.applicationContext, platform, w, h) ?: return null
        val out = relief(mask, shell, recessed = !raised)
        mask.recycle()
        if (out != null) cache.put(key, out)
        return out
    }
}

/**
 * (Update 241, #44/#46: the SaveVault mark moulded into this node's plastic, under its content: [where] gives the blank
 * area it may use (its centre, and its width and height at most; null: none on this face), [shell] the plastic's colour
 * there. Left out while the node is narrower than [minWidth] — a mark too small to read would only be noise (a slot in
 * the grid) — and with Save Vault › SaveVault engraving off. The picture is made when the node's size or colour changes,
 * never per frame.)
 */
internal fun Modifier.saveVaultMark(
    shell: Int, layout: Engraving.Layout, minWidth: Dp = 110.dp, where: (Size) -> Rect?
): Modifier = this.drawWithCache {
    val r = if (Features.saveEngraving.state.value && size.width >= minWidth.toPx()) where(size) else null
    val img = r?.let {
        val w = min(it.width, it.height / Engraving.ratio(layout))
        Engraving.saveVault(shell, w.roundToInt(), layout)?.asImageBitmap()
    }
    val at = if (img != null && r != null) IntOffset((r.center.x - img.width / 2f).roundToInt(), (r.center.y - img.height / 2f).roundToInt()) else IntOffset.Zero
    onDrawWithContent {
        if (img != null) drawImage(img, dstOffset = at)
        drawContent()
    }
}

/** (Update 241, #44: a box [w] × [h] round ([cx], [cy]).) */
internal fun markArea(cx: Float, cy: Float, w: Float, h: Float): Rect = Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
