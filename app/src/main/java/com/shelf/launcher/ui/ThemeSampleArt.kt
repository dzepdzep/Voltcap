package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Update 427: made-up sample pictures for the theme browser's still thumbnails (ThemeThumbs). The music themes and the ones
 * that show the row's covers, the game's disc or your trophies showed a flat accent-coloured square instead — the tile
 * didn't look like the finished theme. These are drawn here (no network, no real art, no words on the covers), always the
 * same, made once per process and only for thumbnails; the live scenes keep using the real cover / media / trophies.
 */
internal object ThemeSampleArt {
    private fun hsv(h: Float, s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s, v))

    /** One made-up box cover (sky, a sun, hills, a top band) in cell [x, y, w, h] of [c]; [i] varies it. */
    private fun drawCover(c: Canvas, x: Float, y: Float, w: Float, h: Float, i: Int) {
        val hue = (i * 47 + 200) % 360f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, y, 0f, y + h, hsv(hue, 0.65f, 0.25f), hsv(hue + 40f, 0.7f, 0.85f), Shader.TileMode.CLAMP)
        c.drawRect(x, y, x + w, y + h, p)
        p.shader = null
        p.color = hsv(38f + (i % 4) * 9f, 0.72f, 1f) // a warm sun, like a sunset sleeve
        c.drawCircle(x + w * (0.3f + 0.04f * ((i * 37) % 10)), y + h * 0.38f, w * 0.18f, p)
        p.color = hsv(hue, 0.5f, 0.14f)
        val hill = Path().apply {
            moveTo(x, y + h)
            for (k in 0..8) lineTo(x + w * k / 8f, y + h * (0.72f - 0.12f * kotlin.math.abs(kotlin.math.sin(k * 1.3f + i))))
            lineTo(x + w, y + h); close()
        }
        c.drawPath(hill, p)
        p.color = 0xD9FFFFFF.toInt(); c.drawRect(x, y, x + w, y + h * 0.09f, p)
        p.color = hsv(hue + 90f, 0.7f, 0.6f); c.drawRect(x + w * 0.08f, y + h * 0.02f, x + w * 0.48f, y + h * 0.07f, p)
    }

    private fun bmp(w: Int, h: Int, draw: (Canvas) -> Unit): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

    /** A square album cover (the sharp one, 256 px). */
    val cover: Bitmap by lazy { bmp(256, 256) { drawCover(it, 0f, 0f, 256f, 256f, 2) } }

    /** The soft cover (96 px, blurred like MusicTheme.softArt: down to 12 px and smoothly back up). */
    val softCover: Bitmap by lazy { Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(cover, 12, 12, true), 96, 96, true) }

    /** The song's title and artist in dark ink on a clear picture (like NowPlayingScene.songLabel): 640 × 280, or 640 × 170 clean. */
    fun label(clean: Boolean): Bitmap = if (clean) labelClean else labelHand
    private val labelHand: Bitmap by lazy { songLabel(640, 280, Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC), 96f, 62f) }
    private val labelClean: Bitmap by lazy { songLabel(640, 170, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), 86f, 50f) }
    private fun songLabel(w: Int, h: Int, face: Typeface, big: Float, small: Float) = bmp(w, h) { c ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1E4E.toInt(); typeface = face; textAlign = Paint.Align.CENTER }
        p.textSize = big; c.drawText("Paper Lanterns", w / 2f, h * 0.48f, p)
        p.textSize = small; p.alpha = 210; c.drawText("The Night Shift", w / 2f, h * 0.86f, p)
    }

    /** The row's covers: 4 × 2 cells of 160 × 224 (SceneArt's atlas layout). */
    val coversAtlas: Bitmap by lazy { bmp(640, 448) { c -> for (i in 0 until 8) drawCover(c, (i % 4) * 160f, (i / 4) * 224f, 160f, 224f, i) } }

    /** The game's disc (a printed disc with its hub), 256 px. */
    val disc: Bitmap by lazy {
        bmp(256, 256) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = 0xFFD9DDE3.toInt(); c.drawCircle(128f, 128f, 126f, p)
            c.save(); c.clipPath(Path().apply { addCircle(128f, 128f, 118f, Path.Direction.CW) }); drawCover(c, 10f, 10f, 236f, 236f, 5); c.restore()
            p.color = 0xFFC9CED6.toInt(); c.drawCircle(128f, 128f, 30f, p)
            p.color = 0xFF000000.toInt(); c.drawCircle(128f, 128f, 12f, p)
        }
    }

    /** Trophies side by side: 7 square cells of 96 px (TrophyShelf's layout). */
    val trophies: Bitmap by lazy {
        bmp(7 * 96, 96) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 7) {
                val x = i * 96f; val hue = (i * 53 + 30) % 360f
                p.color = hsv(hue, 0.55f, 0.35f); c.drawRect(x, 0f, x + 96f, 96f, p)
                p.color = hsv(hue, 0.6f, 0.95f)
                c.drawPath(Path().apply { moveTo(x + 48f, 14f); lineTo(x + 80f, 48f); lineTo(x + 48f, 82f); lineTo(x + 16f, 48f); close() }, p)
                p.color = 0xCCFFFFFF.toInt(); c.drawCircle(x + 48f, 48f, 10f, p)
            }
        }
    }
}
