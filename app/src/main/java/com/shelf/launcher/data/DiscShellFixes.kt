package com.shelf.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import java.io.File

/*
 * Update 277 (Toni: "PSP label has to be fixed, it's too big"), 278 (Toni: "it's not really on the label, it's
 * misaligned"): the optical-system pack's PSP shell (disc_psp.webp) has its grey "PSP · PlayStation Portable" label wider
 * than the shell (3–557 px of the 560 px picture, the shell 23–518) and set below the shell's label recess (the darker
 * rounded plate at 68–478 px, from 57 px down), covering most of the recess and the shell between it and the window.
 * The pack's own file isn't changed (it's your download); Voltcap makes a fitted copy once, next to it, and draws that:
 * - the label (its plain face and logo, scaled to 74 %) laid into the recess, the recess's own size, round corners,
 *   with a soft shadow;
 * - where the label stood past the shell: nothing (see-through, as round the rest of the shell);
 * - where it covered the shell: plain shell plastic — the texture from the shell's own plain lower part, shaded to match
 *   the plastic just above (and, towards the window, beside) it; the side rails with their groove from further down.
 * Measured on the 560 × 846 picture; any other size is scaled to it.
 */
internal object DiscShellFixes {
    // Update 280: one fixed copy per broken picture — its version (a new version makes it again).
    private val VERSIONS = mapOf("psp" to 2, "sega_cd" to 1)
    private val busy = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Whether the pack's picture [name] is drawn from a fitted copy. */
    fun fixes(name: String): Boolean = name in VERSIONS

    /** [orig] (the pack's shell [name]) as drawn: the fitted copy once it's made; until then (first use only) [orig]. */
    fun fixed(c: Context, name: String, orig: File, done: () -> Unit): File {
        val v = VERSIONS[name] ?: return orig
        val fixed = File(c.filesDir, "packs/fixes/${name}_fit$v.png")
        if (fixed.exists() && fixed.lastModified() >= orig.lastModified()) return fixed
        if (busy.add(name)) {
            val app = c.applicationContext
            Thread {
                runCatching { if (name == "psp") make(orig, fixed) else segaCd(app, orig, fixed) }
                // (An earlier version's copy, no longer used.)
                for (old in 1 until v) runCatching { File(fixed.parentFile, "${name}_fit$old.png").delete() }
                busy.remove(name)
                if (fixed.exists()) android.os.Handler(android.os.Looper.getMainLooper()).post(done)
            }.apply { this.name = "voltcap-shell-fit"; isDaemon = true }.start()
        }
        return orig
    }

    /** (Update 277–278: the PSP shell, as before.) */
    fun psp(c: Context, orig: File, done: () -> Unit): File = fixed(c, "psp", orig, done)

    /**
     * Update 280 (found in the custom-shell walk): the optical-system pack's Sega CD shell has a blank white bar where its
     * logo belongs (the Mega CD shell beside it has its logo). The bar (112–452 × 127–173 px of the 560 px picture) is
     * painted back to the label plate's grey (shaded as the plate above and below it) and the Sega CD badge Voltcap
     * already has (assets/platform_logos_png/SegaCD.png, the black-and-silver bar) set in its place, at the bar's size.
     */
    private fun segaCd(context: Context, orig: File, out: File) {
        val src = BitmapFactory.decodeFile(orig.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return
        val w = src.width; val h = src.height
        val s = w / 560f
        fun px(v: Int) = (v * s).toInt().coerceIn(0, w - 1)
        fun py(v: Int) = (v * s).toInt().coerceIn(0, h - 1)
        val res = src.copy(Bitmap.Config.ARGB_8888, true)
        val x0 = px(104); val x1 = px(470); val y0 = py(121); val y1 = py(180)
        val ya = (y0 - 3).coerceAtLeast(0); val yb = (y1 + 3).coerceAtMost(h - 1)
        for (x in x0 until x1) {
            val a = src.getPixel(x, ya); val b = src.getPixel(x, yb)
            for (y in y0 until y1) {
                val t = (y - ya).toFloat() / (yb - ya).coerceAtLeast(1)
                fun ch(sh: Int) = ((((a shr sh) and 255) * (1 - t)) + (((b shr sh) and 255) * t)).toInt().coerceIn(0, 255)
                res.setPixel(x, y, (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0))
            }
        }
        val logo = context.assets.open("platform_logos_png/SegaCD.png").use { BitmapFactory.decodeStream(it) }
        if (logo != null) {
            val bx0 = 112 * s; val bx1 = 453 * s; val by0 = 127 * s; val by1 = 174 * s
            val sc = minOf((bx1 - bx0) / logo.width, (by1 - by0) / logo.height)
            val lw = logo.width * sc; val lh = logo.height * sc
            val cx = (bx0 + bx1) / 2f; val cy = (by0 + by1) / 2f
            Canvas(res).drawBitmap(logo, null, android.graphics.RectF(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
            logo.recycle()
        }
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { res.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        src.recycle(); res.recycle()
    }

    /** Testing only (TestHooks `voltcap_test_pspfit`). */
    fun test(orig: File, out: File) = make(orig, out)

    /** Testing only (TestHooks `voltcap_test_pspfit`, with segacd_in.webp). */
    fun testSegaCd(c: Context, orig: File, out: File) = segaCd(c, orig, out)

    private fun make(orig: File, out: File) {
        val src = BitmapFactory.decodeFile(orig.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: return
        val w = src.width; val h = src.height
        val s = w / 560f
        fun px(v: Int) = (v * s).toInt().coerceIn(0, maxOf(w, h))
        fun py(v: Int) = (v * s).toInt().coerceIn(0, h - 1)
        val bodyL = px(23); val bodyR = px(518).coerceAtMost(w)
        val frameL = px(80); val frameR = px(466)
        val railL = px(58); val railR = px(498)
        val t0 = py(95); val t1 = py(237); val frameTop = py(231)
        val sb0 = py(745); val sb1 = py(805).coerceAtLeast(sb0 + 1)
        val railRow = py(300)
        val all = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        fun lum(c: Int) = (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3f
        fun colMean(x: Int, y0: Int, y1: Int): Float { var t = 0f; for (y in y0 until y1) t += lum(all[y * w + x]); return t / (y1 - y0).coerceAtLeast(1) }
        fun smooth(a: FloatArray, k: Int = (7 * s).toInt().coerceAtLeast(1)): FloatArray = FloatArray(a.size) { i ->
            val lo = maxOf(0, i - k); val hi = minOf(a.size - 1, i + k); var t = 0f; for (j in lo..hi) t += a[j]; t / (hi - lo + 1)
        }
        val inBody = { x: Int -> x in bodyL until bodyR }
        val bot = smooth(FloatArray(w) { x -> if (inBody(x)) colMean(x, sb0, sb1) else 1f })
        val top = smooth(FloatArray(w) { x -> if (inBody(x)) colMean(x, py(22), py(56)) else 1f })
        val side = smooth(FloatArray(w) { x -> if (inBody(x)) colMean(x, py(240), railRow) else 1f })
        val sideL = side[(frameL - 1).coerceAtLeast(0)]; val sideR = side[frameR.coerceAtMost(w - 1)]
        val res = all.copyOf()
        for (y in t0 until t1) {
            val sy = sb0 + (y - t0) % (sb1 - sb0)
            val t = (y - t0).toFloat() / (t1 - 1 - t0).coerceAtLeast(1)
            for (x in 0 until w) {
                val i = y * w + x
                if (!inBody(x)) { res[i] = 0; continue }
                if (y >= frameTop && x in frameL until frameR) continue
                if (x < railL || x >= railR) { res[i] = all[railRow * w + x]; continue }
                val sTarget = if (x in frameL until frameR) sideL + (sideR - sideL) * (x - frameL) / (frameR - frameL).toFloat() else side[x]
                val g = (top[x] * (1f - t) + sTarget * t) / bot[x].coerceAtLeast(1f)
                val c = all[sy * w + x]
                val r = (((c shr 16) and 255) * g).toInt().coerceAtMost(255)
                val gg = (((c shr 8) and 255) * g).toInt().coerceAtMost(255)
                val b = ((c and 255) * g).toInt().coerceAtMost(255)
                res[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (gg shl 8) or b
            }
        }
        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(res, 0, w, 0, 0, w, h)
        // The label in the recess: its plain face and logo, scaled, round corners, a soft shadow under it.
        val rl = px(68).toFloat(); val rt = py(57).toFloat(); val rr = px(478).toFloat()
        val nh = (233 - 97) * (rr - rl) / 554f
        val radius = 12f * s
        val cv = Canvas(outBmp)
        val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x46000000 }
        cv.drawRoundRect(android.graphics.RectF(rl, rt + 2f * s, rr, rt + 2f * s + nh), radius, radius, shadow)
        val lab = Bitmap.createBitmap((rr - rl).toInt().coerceAtLeast(1), nh.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val lc = Canvas(lab)
        val filter = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        lc.drawBitmap(src, Rect(px(20), py(100), px(540).coerceAtMost(w), py(229)), android.graphics.RectF(0f, 0f, lab.width.toFloat(), lab.height.toFloat()), filter)
        val mask = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        val maskBmp = Bitmap.createBitmap(lab.width, lab.height, Bitmap.Config.ARGB_8888)
        Canvas(maskBmp).drawRoundRect(android.graphics.RectF(0f, 0f, lab.width.toFloat(), lab.height.toFloat()), radius, radius,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
        lc.drawBitmap(maskBmp, 0f, 0f, mask)
        cv.drawBitmap(lab, rl, rt, null)
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { outBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        src.recycle(); outBmp.recycle(); lab.recycle(); maskBmp.recycle()
    }
}
