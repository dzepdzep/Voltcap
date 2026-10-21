package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect

/*
 * Update 297 (Toni: "the Switch cartridge's sticker is wrong"): every real Nintendo Switch game card's sticker has a red
 * horizontal header with the NINTENDO SWITCH logo across its top, the game's art under it (reference: Wikimedia Commons,
 * "Nintendo Switch Speicherkarte Vorder- und Rückseite (HWDE) 2022-05-21"). Voltcap drew a Switch game with only its cover
 * (no real label scan) as the whole box cover fitted into the plain card's window: no header, and a box front's own
 * printing — its corner logo tile, its rating box — copied onto the card.
 *
 * Now, without a real label scan:
 *  - the template card (TemplateMedia, the card in an opened case — CaseFaces) uses the template pack's label frame
 *    (tpl_cartl_switch), which carries the real red header; the cover is cut into the art window under it — its upper
 *    middle, which leaves a box front's corner logo tile (top ~12 %) and its rating (bottom ~18 %) off the card;
 *  - the drawn Switch shell (CartShells, "switch") puts the same header, taken from that frame, across the top of its
 *    sticker and the cut cover under it.
 * A real label scan is the whole printed sticker (its own header, its own product code) and is drawn as before. No
 * rating, logo or product code is ever made up; the header is the template pack's own picture. Switch 2 is untouched.
 */
internal object SwitchLabel {
    /** Where a tall cover is cut: this share of what's cut comes off its top, the rest off its bottom. */
    const val TOP_SHARE = 0.326f

    /** The same cut for Compose's ContentScale.Crop (bias -1 = top … 1 = bottom). */
    val CROP = androidx.compose.ui.BiasAlignment(0f, TOP_SHARE * 2f - 1f)

    /** A Switch template card drawn from a cover (no label scan): it wears the label frame with the red header. */
    fun useFrame(tpl: MediaTpl?, labelIsScan: Boolean): Boolean =
        !labelIsScan && tpl?.cart?.res == com.shelf.launcher.R.drawable.tpl_cart_switch && tpl.label?.res == com.shelf.launcher.R.drawable.tpl_cartl_switch

    /**
     * The part of a [w] × [h] picture cut to [aspect] (width / height), never stretched: a wide one's middle, a tall one
     * cut [TOP_SHARE] from the top. (left, top, right, bottom) in px.
     */
    fun cut(w: Int, h: Int, aspect: Float): IntArray {
        val a = w.toFloat() / h.coerceAtLeast(1)
        return if (a > aspect) {
            val cw = (h * aspect).toInt().coerceIn(1, w)
            intArrayOf((w - cw) / 2, 0, (w - cw) / 2 + cw, h)
        } else {
            val ch = (w / aspect).toInt().coerceIn(1, h)
            val top = ((h - ch) * TOP_SHARE).toInt().coerceIn(0, h - ch)
            intArrayOf(0, top, w, top + ch)
        }
    }

    fun cutRect(b: Bitmap, aspect: Float): Rect = cut(b.width, b.height, aspect).let { Rect(it[0], it[1], it[2], it[3]) }

    @Volatile private var header: Bitmap? = null
    @Volatile private var tried = false

    /** The red header strip of the real sticker (from the template pack's label frame), loaded once. */
    fun prime(c: Context) {
        if (tried) return
        tried = true
        header = runCatching {
            val tpl = MediaTemplates.of("Switch") ?: return@runCatching null
            val cart = tpl.cart ?: return@runCatching null
            val lab = tpl.label ?: return@runCatching null
            val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val b = BitmapFactory.decodeResource(c.resources, lab.res, opts) ?: return@runCatching null
            // From the sticker's top (the plain card's window top) down to the label frame's art window, across the window.
            // (Inset ~1 % so the frame's dark rim round the sticker's top corners stays out.)
            val inset = (b.width * 0.011f).toInt()
            val l = (lab.win[0] * b.width).toInt() + inset; val r = (lab.win[2] * b.width).toInt() - inset
            val t = (cart.win[1] * b.height).toInt() + inset; val bot = (lab.win[1] * b.height).toInt()
            if (r - l < 8 || bot - t < 4) null else Bitmap.createBitmap(b, l, t, r - l, bot - t).also { if (it !== b) b.recycle() }
        }.getOrNull()
    }

    /** The header strip, or null before [prime] / when the template pack has none. */
    fun header(): Bitmap? = header
}
