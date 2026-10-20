package com.shelf.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import java.io.File

/*
 * Update 277 (Toni: "PSP label has to be fixed, it's too big"): the optical-system pack's PSP shell (disc_psp.webp) has its
 * grey "PSP · PlayStation Portable" label plate wider than the shell — it stuck out ~20 px past both sides (label 3–557 px
 * of the 560 px picture, the shell 23–518). The other shells' labels sit inside them. The pack's own file isn't changed
 * (it's your download); Voltcap makes a fitted copy once, next to it, and draws that:
 * - the label narrowed to 46–494 px (its rounded ends kept, the middle trimmed — the logo isn't touched or scaled);
 * - where it stood past the shell: nothing (see-through, as round the rest of the shell);
 * - where it covered the shell's sides: the shell's own side rail (with its groove), from further down the shell.
 * Measured on the 560 × 846 picture; any other size is scaled to it.
 */
internal object DiscShellFixes {
    private const val VERSION = 1
    @Volatile private var busy = false

    /** [orig] (the pack's PSP shell) as drawn: the fitted copy once it's made; until then (first use only) [orig]. */
    fun psp(c: Context, orig: File, done: () -> Unit): File {
        val fixed = File(c.filesDir, "packs/fixes/psp_fit$VERSION.png")
        if (fixed.exists() && fixed.lastModified() >= orig.lastModified()) return fixed
        if (!busy) {
            busy = true
            Thread {
                runCatching { make(orig, fixed) }
                busy = false
                if (fixed.exists()) android.os.Handler(android.os.Looper.getMainLooper()).post(done)
            }.apply { name = "voltcap-psp-fit"; isDaemon = true }.start()
        }
        return orig
    }

    /** Testing only (TestHooks `voltcap_test_pspfit`). */
    fun test(orig: File, out: File) = make(orig, out)

    private fun make(orig: File, out: File) {
        val src = BitmapFactory.decodeFile(orig.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: return
        val w = src.width; val h = src.height
        val s = w / 560f
        fun px(v: Int) = (v * s).toInt()
        val top = px(95); val bot = px(236).coerceAtMost(h)
        val bodyL = px(23); val bodyR = px(518)
        val nl = px(46); val nr = px(494)
        val cap = px(40); val labelL = px(3); val labelR = px(557).coerceAtMost(w)
        val centre = px(280); val railRow = px(300).coerceAtMost(h - 1); val corner = px(20)
        val res = src.copy(Bitmap.Config.ARGB_8888, true)
        // The band the old label covered: outside the shell see-through; its sides (and under the new label's rounded
        // corners) the shell's side rail.
        val rail = IntArray(w).also { src.getPixels(it, 0, w, 0, railRow, w, 1) }
        val row = IntArray(w)
        for (y in top until bot) {
            res.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if (x < bodyL || x >= bodyR) row[x] = 0
                else if (x < nl + corner || x >= nr - corner) row[x] = rail[x]
            }
            res.setPixels(row, 0, w, 0, y, w, 1)
        }
        // The label, narrowed: its left end, the middle round the logo, its right end.
        val midW = (nr - nl) - 2 * cap
        val cv = Canvas(res)
        cv.drawBitmap(src, Rect(labelL, top, labelL + cap, bot), Rect(nl, top, nl + cap, bot), null)
        cv.drawBitmap(src, Rect(centre - midW / 2, top, centre - midW / 2 + midW, bot), Rect(nl + cap, top, nl + cap + midW, bot), null)
        cv.drawBitmap(src, Rect(labelR - cap, top, labelR, bot), Rect(nl + cap + midW, top, nr, bot), null)
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { res.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        src.recycle(); res.recycle()
    }
}
