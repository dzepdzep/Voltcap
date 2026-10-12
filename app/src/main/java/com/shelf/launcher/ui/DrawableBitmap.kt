package com.shelf.launcher.ui

/**
 * Update 147: a loaded picture as a plain bitmap, whatever kind of drawable the image loader gave back. An animated
 * cover (SteamGridDB's animated WebP / GIF, played when Animated artwork is on) comes back as an
 * AnimatedImageDrawable, not a BitmapDrawable: the code that read only BitmapDrawables got nothing, so the 3D pieces
 * and cases stayed a plain grey card and the game's colours never came. This draws its current (first) frame instead,
 * at most [maxSide] pixels on its longer side.
 */
internal fun drawableBitmap(d: android.graphics.drawable.Drawable?, maxSide: Int = 720): android.graphics.Bitmap? {
    if (d == null) return null
    (d as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { return it }
    return runCatching {
        val w0 = d.intrinsicWidth.takeIf { it > 0 } ?: maxSide
        val h0 = d.intrinsicHeight.takeIf { it > 0 } ?: maxSide
        val k = minOf(1f, maxSide.toFloat() / maxOf(w0, h0))
        val w = (w0 * k).toInt().coerceAtLeast(1)
        val h = (h0 * k).toInt().coerceAtLeast(1)
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val old = android.graphics.Rect(d.bounds)
        d.setBounds(0, 0, w, h)
        d.draw(c)
        d.bounds = old
        out
    }.getOrNull()
}

/** Update 147: "1 game", "2 games" (the category screen said "1 games"). */
internal fun gamesCount(n: Int): String = if (n == 1) "1 game" else "$n games"
