package com.shelf.launcher.ui

import androidx.compose.ui.graphics.asAndroidBitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/*
 * Update 305: Voltcap's own Steam and Android case spines as collector's-edition spines — Steam's dark navy into charcoal
 * with its logo block (from your Steam template, cov_spine_steam), Android's dark graphite with the Android robot (the
 * redrawn logo Voltcap ships; the robot is CC BY 3.0, Google) and a restrained green line; the game's title large down
 * the spine, and a thin accent stripe in a colour taken from the game's own cover. Same size and shape as the template
 * spines they replace (so every case, shelf and wall keeps its proportions). A real spine scan, your spine pack and
 * R1 › Spine still come first — this is only what's drawn when the game has none.
 */
internal object OwnSpines {
    private const val H = 1400

    /** Whether [res] is one of Voltcap's own spines this draws (Steam's, Android's). */
    fun isOwn(res: Int?): Boolean = res == com.shelf.launcher.R.drawable.cov_spine_steam || res == com.shelf.launcher.R.drawable.cov_spine_android

    private val steamTop = java.util.concurrent.atomic.AtomicReference<Bitmap?>(null)

    /** The Steam template spine's logo block (its top 29 %: the logo and the word, above the blue line). */
    private fun steamBlock(context: android.content.Context): Bitmap? = steamTop.get() ?: runCatching {
        val o = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        val full = android.graphics.BitmapFactory.decodeResource(context.resources, com.shelf.launcher.R.drawable.cov_spine_steam, o) ?: return null
        Bitmap.createBitmap(full, 0, 0, full.width, (full.height * 0.2902f).toInt().coerceAtLeast(1))
    }.getOrNull()?.also { steamTop.set(it) }

    /** The spine for [res] (standing, reading top to bottom), [title] on it, [accent] its stripe (null: the brand's). */
    fun compose(context: android.content.Context, res: Int, title: String?, accent: Int?): Bitmap {
        val steam = res == com.shelf.launcher.R.drawable.cov_spine_steam
        val aspect = if (steam) 70f / 803f else 0.0737f
        val w = (H * aspect).toInt().coerceAtLeast(8)
        val out = Bitmap.createBitmap(w, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val brand = if (steam) 0xFF66C0F4.toInt() else 0xFF3DDC84.toInt()
        val acc = accent ?: brand
        // The card.
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(),
            if (steam) intArrayOf(0xFF1E2A38.toInt(), 0xFF1A1F27.toInt(), 0xFF14171C.toInt()) else intArrayOf(0xFF262A28.toInt(), 0xFF1D201F.toInt(), 0xFF141615.toInt()),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), H.toFloat(), p); p.shader = null
        // A faint printed grain across it.
        p.color = 0x08FFFFFF; p.strokeWidth = 1f
        var gy = 0f
        while (gy < H) { c.drawLine(0f, gy, w.toFloat(), gy + w * 0.6f, p); gy += w * 0.11f }
        // The brand block on top. (Pictures with their own paint: the grain's faint colour would draw them faint too.)
        val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var y: Float
        if (steam) {
            val block = steamBlock(context)
            y = if (block != null) {
                val bh = w * block.height.toFloat() / block.width
                c.drawBitmap(block, null, RectF(0f, 0f, w.toFloat(), bh), bp); bh
            } else w * 3.3f
        } else {
            val bh = w * 2.5f
            p.color = 0xFF151716.toInt(); c.drawRect(0f, 0f, w.toFloat(), bh, p)
            val robot = runCatching { PlatformLogos.baked(context, "Android")?.asAndroidBitmap() }.getOrNull()
            if (robot != null && robot.width > 0) {
                val rw = w * 0.56f; val rh = rw * robot.height / robot.width
                c.drawBitmap(robot, null, RectF((w - rw) / 2f, bh * 0.16f, (w + rw) / 2f, bh * 0.16f + rh), bp)
            }
            val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE9EDEA.toInt(); textAlign = Paint.Align.CENTER; textSize = w * 0.15f; letterSpacing = 0.06f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            c.drawText("ANDROID", w / 2f, bh * 0.86f, tp)
            y = bh
        }
        // The brand's line under it.
        p.color = brand; c.drawRect(0f, y, w.toFloat(), y + (w * 0.035f).coerceAtLeast(2f), p)
        y += w * 0.035f
        // The foot: a darker block with the game's accent bar.
        val footH = w * 1.5f
        val foot = H - footH
        p.color = if (steam) 0xFF101318.toInt() else 0xFF0F1110.toInt(); c.drawRect(0f, foot, w.toFloat(), H.toFloat(), p)
        p.color = brand and 0x00FFFFFF or 0x66000000.toInt(); c.drawRect(0f, foot, w.toFloat(), foot + 1.5f, p)
        val bar = RectF(w * 0.22f, foot + footH * 0.42f, w * 0.78f, foot + footH * 0.42f + w * 0.07f)
        p.color = acc; c.drawRoundRect(bar, bar.height(), bar.height(), p)
        // The accent stripe down the title's length, near the opening side.
        p.color = acc and 0x00FFFFFF or 0xB0000000.toInt()
        c.drawRect(w * 0.88f, y + w * 0.3f, w * 0.915f, foot - w * 0.3f, p)
        // The title, large, reading top to bottom.
        val t = title?.trim()?.takeIf { it.isNotEmpty() }
        if (t != null) {
            val along = (foot - y) - w * 0.9f
            val across = w * 0.74f
            val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setShadowLayer(w * 0.03f, 0f, w * 0.01f, 0xAA000000.toInt())
                textSize = across * 0.66f
            }
            // (Update 391: smaller, then a second line across the spine; never "…".)
            val lines = SpineTitleFit.fit(tp, t, along, w * 0.8f, tp.textSize, across * 0.3f)
            c.save()
            // (Centred in the room between the brand block and the foot, a little towards the hinge side of the stripe.)
            c.translate(w * 0.45f, y + w * 0.45f)
            c.rotate(90f)
            SpineTitleFit.draw(c, tp, lines, along)
            c.restore()
        }
        // The edges' fine bevel.
        p.color = 0x22FFFFFF; c.drawRect(0f, 0f, 1f, H.toFloat(), p)
        p.color = 0x33000000; c.drawRect(w - 1f, 0f, w.toFloat(), H.toFloat(), p)
        return out
    }

    /** The cover's accent colour for [model] (decoded small through Coil; blocking — call off the main thread). */
    suspend fun accentFor(context: android.content.Context, model: Any?): Int? {
        if (model == null) return null
        accents.get(model.toString())?.let { return it.takeIf { v -> v != 0 } }
        val b = runCatching {
            val req = coil.request.ImageRequest.Builder(context).data(model).size(64, 64)
                .precision(coil.size.Precision.INEXACT).allowHardware(false)
                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true).build()
            drawableBitmap((coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable, 64)
        }.getOrNull()
        val a = CollectorBacks.accentOf(b)
        accents.put(model.toString(), a ?: 0)
        return a
    }

    private val accents = android.util.LruCache<String, Int>(256)
}

/**
 * Update 305: a Steam / Android game's spine as [OwnSpines] draws it (its title, its cover's accent), filling [modifier]
 * — the flat shelves' (Game shelf, TV room) spine; a dark strip while it's being made.
 */
@Composable
internal fun OwnSpine(res: Int, title: String, cover: Any?, modifier: Modifier) {
    val t = rememberTitledSpine(res, title, landscape = false, accentFrom = cover)
    Box(modifier.background(Color(0xFF1A1E24))) {
        t.bitmap?.let { b ->
            val img = androidx.compose.runtime.remember(b) { b.asImageBitmap() }
            Image(img, title, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}
