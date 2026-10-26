package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * Update 170: the printed backs of the 3D cases. Turning an opened game round showed plain plastic unless the game had a
 * full box scan; now its back is printed like a real one: the console's back cover from the template pack (barcode,
 * seals, the rating box — MediaBacks.kt) over the game's art, its screenshots in the back's windows (or a row of two in
 * its plain band) and its description under them. Steam, Android and the consoles without a back in the pack get a
 * plain one: the art, the screenshots and the description.
 */

/** What a game's printed back is made of; equal specs make the same picture (kept, see [PrintedBacks]). */
internal data class PrintedBack(
    val platform: String?,
    /** The art behind it (the hero art, else the cover). */
    val art: Any?,
    val shots: List<String>,
    val text: String?,
    /** The case's width / height (the back is made in its shape). */
    val aspect: Float,
    /** Update 173: the game's name, printed when there's no description (an app with nothing scraped had a bare back). */
    val title: String? = null,
    // Update 305: what's known for this game (PackagingFacts / its metadata) — printed on a back without a template of its
    // own (CollectorBacks); a console's template back keeps its own print and gets none of these on top.
    val publisher: String? = null,
    val developer: String? = null,
    val year: Int? = null,
    val players: String? = null,
    val features: List<String> = emptyList(),
    /** The age rating to print (the one picked by Settings › Game look › Age rating on generated backs), or null. */
    val rating: com.shelf.launcher.data.AgeRating? = null
)

/**
 * Update 305: what any case on screen needs for its printed back — the screen's state, kept here by the view model — so a
 * case on a shelf, in a carousel or a stack gets the same back as Inspect (it was made with no screenshots, no description
 * and the cover instead of the hero art: Case3D's defaultCaseBack).
 */
internal object BackSources {
    val state = androidx.compose.runtime.mutableStateOf<com.shelf.launcher.LauncherUiState?>(null)
}

internal object PrintedBacks {
    private val cache = android.util.LruCache<PrintedBack, Bitmap>(6)

    fun cached(spec: PrintedBack): Bitmap? = cache.get(spec)

    /** The back drawn (blocking, off the main thread): [art] and [shots] already decoded. */
    fun compose(context: android.content.Context, spec: PrintedBack, art: Bitmap?, shots: List<Bitmap>): Bitmap {
        val tpl = MediaBacks.of(spec.platform)
        // (Update 305: no back template for this console — Steam, Android, PS3–5, the Xboxes… — a collector's back.)
        if (tpl == null) return CollectorBacks.compose(context, spec, art, shots).also { cache.put(spec, it) }
        val w = 480
        val h = (w / spec.aspect.coerceIn(0.3f, 3f)).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        // The game's art behind it, soft and dark (a picture made small and drawn big again), so the print reads.
        // (Update 331: not behind the matching case pack's opaque backs — nothing of it would show.)
        c.drawColor(0xFF1E2028.toInt())
        if (art != null && tpl.overArt) {
            val small = runCatching { centreCrop(art, 0.12f * w, 0.12f * h) }.getOrNull()
            if (small != null) c.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), p)
            c.drawColor(0x8C000000.toInt())
        }
        // The console's back cover in the case's shape. (Update 331: never stretched as a whole — its header and foot keep
        // their proportions at the back's width and only the plain band between them takes up the difference, so a logo
        // is never squashed on a PS1 jewel case's wider back or a Japanese case's taller one. [mapY]: a height on the
        // template → on this back.)
        val o = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        val base = runCatching { android.graphics.BitmapFactory.decodeResource(context.resources, tpl.res, o) }.getOrNull()
        val mapY: (Float) -> Float = if (base != null) drawSliced(c, base, w, h, tpl.band, p) else { y -> y }
        // Its screenshot windows filled.
        var next = 0
        tpl.wins.forEach { r ->
            val shot = shots.getOrNull(next) ?: art ?: return@forEach
            next++
            val dst = RectF(r[0] * w, mapY(r[1]) * h, r[2] * w, mapY(r[3]) * h)
            c.drawBitmap(centreCrop(shot, dst.width(), dst.height()), null, dst, p)
        }
        // The plain band: a row of two screenshots (when there were no windows) and the description under them.
        val band = tpl.band?.let { b -> floatArrayOf(b[0], mapY(b[1]), b[2], mapY(b[3])) }
        if (band != null) {
            val bx0 = band[0] * w; val bx1 = band[2] * w; val by1 = band[3] * h
            var y = band[1] * h + 0.02f * h
            val overArt = tpl.overArt
            val rest = shots.drop(next)
            if (tpl.wins.isEmpty() && rest.isNotEmpty()) {
                val gap = 0.03f * w
                val bw = bx1 - bx0; val bh = by1 - y
                var sw = (bw - gap) / 2f; var sh = sw * 0.75f
                if (sh > bh * 0.45f) { sh = bh * 0.45f; sw = sh / 0.75f }
                val n = rest.size.coerceAtMost(2)
                val total = sw * n + gap * (n - 1)
                var x = bx0 + (bw - total) / 2f
                val edge = Paint().apply { color = Color.WHITE }
                for (k in 0 until n) {
                    c.drawRect(x - 2f, y - 2f, x + sw + 2f, y + sh + 2f, edge)
                    c.drawBitmap(centreCrop(rest[k], sw, sh), null, RectF(x, y, x + sw, y + sh), p)
                    x += sw + gap
                }
                y += sh + 0.035f * h
            }
            val text = spec.text?.trim()?.takeIf { it.isNotEmpty() } ?: spec.title?.takeIf { shots.isEmpty() }
            if (text != null && by1 - y > 0.06f * h) {
                val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 0.032f * w
                    color = if (tpl.darkInk) 0xFF1A1A1A.toInt() else Color.WHITE
                }
                val pad = 0.02f * w
                val tw = (bx1 - bx0 - 2 * pad).toInt().coerceAtLeast(10)
                val lineH = tp.fontSpacing
                val maxLines = ((by1 - y - 2 * pad) / lineH).toInt()
                if (maxLines >= 2) {
                    val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, tp, tw)
                        .setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
                    if (overArt) {
                        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C000000.toInt() }
                        c.drawRoundRect(RectF(bx0, y - pad, bx1, y + layout.height + pad), 0.02f * w, 0.02f * w, bg)
                    }
                    c.save(); c.translate(bx0 + pad, y); layout.draw(c); c.restore()
                }
            }
        }
        cache.put(spec, out)
        return out
    }

    /**
     * Update 331: [b] (a back base) drawn [w] × [h] in three parts down its height — above [band], the band, below it —
     * the outer two at [b]'s own proportions for the width, the band taking what's left (it's plain). Very wide backs
     * squeeze the outer two a little too, keeping the band at least a quarter of the height. Returns where a height on
     * [b] (a share) lands on the back (a share).
     */
    private fun drawSliced(c: Canvas, b: Bitmap, w: Int, h: Int, band: FloatArray?, p: Paint): (Float) -> Float {
        val t0 = (band?.get(1) ?: MediaBacks.HEADER).coerceIn(0f, 0.9f)
        val t1 = (band?.get(3) ?: MediaBacks.FOOT).coerceIn(t0 + 0.05f, 1f)
        val s = w / b.width.toFloat()
        var top = t0 * b.height * s
        var bot = (1f - t1) * b.height * s
        if (top + bot > h * 0.75f) { val k = h * 0.75f / (top + bot); top *= k; bot *= k }
        val ih = b.height
        fun part(a: Float, z: Float, y0: Float, y1: Float) {
            if (z - a <= 0f || y1 - y0 <= 0f) return
            val ra = (a * ih).toInt(); val rz = (z * ih).toInt().coerceAtLeast(ra + 1).coerceAtMost(ih)
            c.drawBitmap(b, Rect(0, ra, b.width, rz), RectF(0f, y0, w.toFloat(), y1), p)
        }
        part(0f, t0, 0f, top)
        part(t0, t1, top, h - bot)
        part(t1, 1f, h - bot, h.toFloat())
        val hh = h.toFloat()
        return { y ->
            when {
                y <= t0 -> (if (t0 > 0f) y / t0 * top else 0f) / hh
                y >= t1 -> (hh - bot + (if (t1 < 1f) (y - t1) / (1f - t1) * bot else 0f)) / hh
                else -> (top + (y - t0) / (t1 - t0) * (hh - top - bot)) / hh
            }
        }
    }

    /** [b] cut in the middle to the shape [w] × [h] (not stretched). */
    private fun centreCrop(b: Bitmap, w: Float, h: Float): Bitmap {
        if (w <= 0f || h <= 0f) return b
        val want = w / h; val have = b.width.toFloat() / b.height
        val r = if (have > want) {
            val cw = (b.height * want).toInt().coerceAtLeast(1); Rect((b.width - cw) / 2, 0, (b.width - cw) / 2 + cw, b.height)
        } else {
            val ch = (b.width / want).toInt().coerceAtLeast(1); Rect(0, (b.height - ch) / 2, b.width, (b.height - ch) / 2 + ch)
        }
        val ow = w.toInt().coerceIn(1, r.width()); val oh = h.toInt().coerceIn(1, r.height())
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, r, Rect(0, 0, ow, oh), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

/** Update 170: [spec]'s printed back, made away from the screen's thread (null while it's being made). */
@Composable
internal fun rememberPrintedBack(spec: PrintedBack?): Bitmap? {
    val context = LocalContext.current.applicationContext
    val now = remember(spec) { spec?.let { PrintedBacks.cached(it) } }
    val made by produceState(now, spec) {
        if (spec == null || now != null) { value = now; return@produceState }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val loader = coil.Coil.imageLoader(context)
                suspend fun load(model: Any?, side: Int): Bitmap? = model?.let { m ->
                    runCatching {
                        val req = coil.request.ImageRequest.Builder(context).data(m).size(side, side)
                            .precision(coil.size.Precision.INEXACT).allowHardware(false)
                            .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true).build()
                        drawableBitmap((loader.execute(req) as? coil.request.SuccessResult)?.drawable, side)
                    }.getOrNull()
                }
                val art = load(spec.art, if (MediaBacks.of(spec.platform) == null) 720 else 360)
                val shots = spec.shots.take(4).mapNotNull { load(it, 400) }
                PrintedBacks.compose(context, spec, art, shots)
            }.getOrNull()
        }
    }
    return made
}
