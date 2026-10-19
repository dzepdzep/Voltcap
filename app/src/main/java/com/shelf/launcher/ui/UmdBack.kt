package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient

/**
 * Update 257 (#35): a PSP UMD seen from behind — its data side — instead of a plain plate.
 *
 * Sources:
 * - ECMA-365 (1st edition, June 2005), §10.3.1: case 65.00 × 64.00 mm, top edge an arc of R 32 mm (concentric with the
 *   disc), §10.3.5: spindle window Ø 18 mm, §10.3.6: a substantially rectangular head window on the left-hand side,
 *   §11.1: disc Ø 60 mm. https://ecma-international.org/wp-content/uploads/ECMA-365_1st_edition_june_2005.pdf
 * - Photo "Pursuit Force UMD (back).jpg", Wikimedia Commons (public domain, by Znupi): from behind the disc shows through
 *   almost the whole circle inside a thin frame; the clamping plate and its clear ring in the middle; a clear head window
 *   from the hub to the left edge; two round holes near the bottom corners; a small notch on the right edge.
 *
 * Measured from that photo (ESTIMATED, not in the standard): head window ±10 mm high, out to 11.3 mm from the centre;
 * the clear ring round the hub to 13 mm; the holes Ø 4 mm at ±21 mm, 26.6 mm below the centre; the notch 41.5–45 mm down.
 * Drawn in mm on the 64 × 65 mm case; the piece's own outline (its front picture) cuts it to shape.
 */
internal object UmdBack {
    private const val W = 64f
    private const val H = 65f
    private const val CX = 32f
    private const val CY = 32f

    private val cache = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The back at [px] wide in the shell's [tone] (ARGB). Cached. */
    fun picture(tone: Int, px: Int = 512): Bitmap {
        val key = "$tone|$px"
        cache.get(key)?.let { return it }
        val k = px / W
        val hpx = (H * k).toInt()
        val b = Bitmap.createBitmap(px, hpx, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(k, k)
        // Drawn mirrored, as the drawn shells' backs are: turned round, it reads the right way (window on the left).
        c.scale(-1f, 1f, W / 2f, 0f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // The frame: the shell's plastic, a little lighter at the top.
        p.shader = LinearGradient(0f, 0f, 0f, H, shade(tone, 1.08f), shade(tone, 0.9f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null

        // The disc's data side through the clear back: silver with the rainbow a pressed disc throws.
        val discR = 30f
        p.shader = RadialGradient(CX, CY, discR, intArrayOf(0xFF8E939B.toInt(), 0xFFC9CDD3.toInt(), 0xFFA4A9B1.toInt()),
            floatArrayOf(0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(CX, CY, discR, p)
        p.shader = SweepGradient(CX, CY,
            intArrayOf(0x00FFFFFF, 0x55FF6A3D, 0x55FFD23D, 0x5547E07A, 0x553DA8FF, 0x55B06BFF, 0x00FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.08f, 0.14f, 0.2f, 0.26f, 0.32f, 0.4f, 0.9f, 1f))
        c.save(); c.rotate(110f, CX, CY); c.drawCircle(CX, CY, discR, p); c.restore()
        p.shader = null
        // The thin clear lip of the window round the disc.
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.6f; p.color = 0x55FFFFFF
        c.drawCircle(CX, CY, discR + 0.3f, p)
        p.style = Paint.Style.FILL

        // The head window: a clear plate from the hub to the left edge, a touch lighter than the disc behind it.
        val win = RectF(2f, CY - 10f, CX - 11.3f, CY + 10f)
        p.color = 0x33FFFFFF
        c.drawRect(win, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.6f; p.color = 0xCCFFFFFF.toInt()
        c.drawRect(win, p)
        p.style = Paint.Style.FILL

        // The clear ring round the hub (darker: the disc's mirror band seen through it), then the clamping plate.
        p.color = 0xFF8C929B.toInt()
        c.drawCircle(CX, CY, 13f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.5f; p.color = 0x66FFFFFF
        c.drawCircle(CX, CY, 13f, p)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(CX - 2f, CY - 2f, 10f, intArrayOf(0xFFD7DADF.toInt(), 0xFF8D929A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(CX, CY, 9f, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.4f; p.color = 0xFF5E636B.toInt()
        c.drawCircle(CX, CY, 6.5f, p)
        c.drawCircle(CX, CY, 9f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF1C1E22.toInt()
        c.drawCircle(CX, CY, 0.8f, p)

        // The two holes near the bottom corners and the notch on the right edge, in the frame's shadow.
        p.color = shade(tone, 0.55f)
        c.drawCircle(CX - 21f, CY + 26.6f, 2f, p)
        c.drawCircle(CX + 21f, CY + 26.6f, 2f, p)
        c.drawRect(W - 1.5f, 41.5f, W, 45f, p) // (right as seen from behind)

        cache.put(key, b)
        return b
    }

    private fun shade(argb: Int, f: Float): Int = AColor.argb(
        255,
        (AColor.red(argb) * f).toInt().coerceIn(0, 255),
        (AColor.green(argb) * f).toInt().coerceIn(0, 255),
        (AColor.blue(argb) * f).toInt().coerceIn(0, 255)
    )
}
