package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF

/*
 * Update 282 (Toni: "the Android cartridge inside shows the cartridge a bit different inside the case and different when
 * pulled out"): a case that opens showed a stand-in card lying in it — a plain rounded plate in the shell's colour with the
 * cover on it — while the piece that came out was the real drawn cartridge (Voltcap's green Android cartridge, a
 * template cartridge, a library shell or your pack's shell). Now the piece lying in the case is made the same way the
 * piece out of it is drawn: the same shell picture, the same label window, the label fitted the same way.
 * Off the main thread is not needed: it's made once per game and label (remembered), like the case's other pictures.
 */
internal object CaseFaces {
    private val PAPER = 0xFFE9E6DF.toInt()

    /** The front of the piece [pick] (a MediaPick platform) with [label] on it, as it is drawn out of the case; null = the old card. */
    fun of(c: Context, pick: String?, label: Bitmap?, labelIsScan: Boolean): Bitmap? = runCatching {
        pick ?: return null
        SwitchLabel.prime(c)
        when {
            pick.startsWith(SHELL_PREFIX) -> shell(pick, label, labelIsScan)
            pick.startsWith(CUSTOM_CART) -> pack(c, pick.removePrefix(CUSTOM_CART), label)
            else -> template(c, pick, label, labelIsScan)
        }
    }.getOrNull()

    /** A library shell (CartShellArt): CartShells.front, as drawn. Your pack's shapes picked as a shell: the pack's. */
    private fun shell(pick: String, label: Bitmap?, scan: Boolean): Bitmap? {
        val (choice0, console) = CartShells.split(pick)
        choice0 ?: return null
        // (Update 308: a pack shape with a real cartridge: that cartridge in your pack's plastic — CartShells.authentic.)
        val choice = CartShells.authentic(choice0, console) ?: choice0
        choice.pack?.let { look -> return packShape(console ?: "", look, choice.clear, label) }
        val sh = choice.shell ?: return null
        val brand = console?.let { com.shelf.launcher.data.ConsoleNames.display(it) } ?: ""
        return CartShells.frontFor(sh, choice.colourKey, console, label, brand, null, 480, scan)
    }

    /** Your pack (CustomCart): its optical-system shell for the console, else the cartridge shape picked. */
    private fun pack(c: Context, console: String, label: Bitmap?): Bitmap? {
        discShellFor(console)?.let { ds ->
            val file = com.shelf.launcher.data.Packs.discFile(ds.file) ?: return null
            val shellBmp = decode(file) ?: return null
            val w = shellBmp.width; val h = shellBmp.height
            val win = RectF(w * ds.win[0], h * ds.win[1], w * (ds.win[0] + ds.win[2]), h * (ds.win[1] + ds.win[3]))
            return compose(shellBmp, win, w * ds.corner, label?.let { CartShells.packSticker(console, it, ds.aspect * ds.win[2] / ds.win[3].coerceAtLeast(0.01f)) }, 1f)
        }
        val finish = com.shelf.launcher.data.Features.modelFinish.state.value
        val clear = com.shelf.launcher.data.Features.cartClear.state.value && (finish == ModelFinish.CLEAR || finish == ModelFinish.OFF)
        return packShape(console, CustomCarts.lookFor(console), clear, label)
    }

    private fun packShape(console: String, look: CartLook, clear: Boolean, label: Bitmap?): Bitmap? {
        val id = CustomCarts.idFor(console) ?: return null
        val file = CustomCarts.asset(look, clear, id) ?: return null
        val shellBmp = decode(file) ?: return null
        val k = shellBmp.width / look.w
        val win = look.win
        val r = RectF(win[0] * k, win[1] * k, (win[0] + win[2]) * k, (win[1] + win[3]) * k)
        return compose(shellBmp, r, win[4] * k, label?.let { CartShells.packSticker(console, it, win[2] / win[3]) }, if (clear) 0.82f else 1f)
    }

    /** The label on paper in [win] (round corners [corner]), fitted at its own shape; the shell picture over it. */
    private fun compose(shellBmp: Bitmap, win: RectF, corner: Float, label: Bitmap?, shellAlpha: Float): Bitmap {
        val out = Bitmap.createBitmap(shellBmp.width, shellBmp.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        cv.save()
        cv.clipPath(android.graphics.Path().apply { addRoundRect(win, corner, corner, android.graphics.Path.Direction.CW) })
        p.color = PAPER
        cv.drawRect(win, p)
        if (label != null) cv.drawBitmap(label, null, fit(label, win), p)
        cv.restore()
        p.alpha = (shellAlpha * 255).toInt()
        cv.drawBitmap(shellBmp, 0f, 0f, p)
        shellBmp.recycle()
        return out
    }

    /** A template cartridge (TemplateMedia): the label in the shell's window, cut by its mask, the shell over it. */
    private fun template(c: Context, pick: String, label: Bitmap?, scan: Boolean): Bitmap? {
        val tpl = MediaTemplates.of(pick)
        // (Update 297: a Switch card from a cover wears its label frame with the red header, as TemplateMedia draws it.)
        val switchFrame = SwitchLabel.useFrame(tpl, scan)
        val cart = (if (switchFrame) tpl?.label else tpl?.cart) ?: return null
        // (A UMD keeps its drawn UMD — UmdBack — as before.)
        if (cart.res == com.shelf.launcher.R.drawable.tpl_cart_psp) return null
        val opts = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val shellBmp = BitmapFactory.decodeResource(c.resources, cart.res, opts) ?: return null
        val mask = BitmapFactory.decodeResource(c.resources, cart.mask, opts)
        val w = shellBmp.width; val h = shellBmp.height
        val win = cart.win
        val r = RectF(w * win[0], h * win[1], w * win[2], h * win[3])
        val ww = r.width().toInt().coerceAtLeast(1); val wh = r.height().toInt().coerceAtLeast(1)
        // The window on its own: paper, the label (a scan stretched to it, a cover fitted), then the mask's part cut out.
        val winBmp = Bitmap.createBitmap(ww, wh, Bitmap.Config.ARGB_8888)
        val wc = Canvas(winBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        wc.drawColor(PAPER)
        val full = RectF(0f, 0f, ww.toFloat(), wh.toFloat())
        // (Update 284: on the plain cartridge a cover fills the window, cut to it, as TemplateMedia draws it.)
        val fillCover = cart.res == com.shelf.launcher.R.drawable.tpl_cart_generic
        if (label != null) {
            if (scan) wc.drawBitmap(label, null, full, p)
            else if (switchFrame) wc.drawBitmap(label, SwitchLabel.cutRect(label, ww.toFloat() / wh), full, p)
            else if (fillCover) wc.drawBitmap(label, cropSrc(label, ww.toFloat() / wh), full, p)
            else wc.drawBitmap(label, null, fit(label, full), p)
        }
        if (mask != null) {
            val mw = mask.width; val mh = mask.height
            val src = Rect((win[0] * mw).toInt(), (win[1] * mh).toInt(), (win[2] * mw).toInt().coerceAtLeast((win[0] * mw).toInt() + 1), (win[3] * mh).toInt().coerceAtLeast((win[1] * mh).toInt() + 1))
            wc.drawBitmap(mask, src, full, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            mask.recycle()
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawBitmap(winBmp, null, r, p)
        cv.drawBitmap(shellBmp, 0f, 0f, p)
        winBmp.recycle(); shellBmp.recycle()
        return out
    }

    /** The part of [b] cut to [aspect] (width / height), never stretched: a wide picture's middle, a tall one's upper part. */
    private fun cropSrc(b: Bitmap, aspect: Float): Rect {
        val a = b.width.toFloat() / b.height.coerceAtLeast(1)
        return if (a > aspect) { val cw = (b.height * aspect).toInt().coerceIn(1, b.width); Rect((b.width - cw) / 2, 0, (b.width + cw) / 2, b.height) }
        else { val ch = (b.width / aspect).toInt().coerceIn(1, b.height); val top = ((b.height - ch) * 0.3f).toInt(); Rect(0, top, b.width, top + ch) }
    }

    private fun fit(b: Bitmap, r: RectF): RectF {
        val a = b.width.toFloat() / b.height.coerceAtLeast(1)
        val ra = r.width() / r.height().coerceAtLeast(1f)
        return if (a > ra) { val h = r.width() / a; RectF(r.left, r.centerY() - h / 2f, r.right, r.centerY() + h / 2f) }
        else { val w = r.height() * a; RectF(r.centerX() - w / 2f, r.top, r.centerX() + w / 2f, r.bottom) }
    }

    private fun decode(f: java.io.File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 640) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.ARGB_8888 })
    }
}
