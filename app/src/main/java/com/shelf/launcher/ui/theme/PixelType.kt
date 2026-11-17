package com.shelf.launcher.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 679b (owner 17 Nov, pixel font decision B): Voltcap's pixel face draws at [SCALE] of its nominal size - volt_pixel.ttf's
 * unitsPerEm is 1600 (was 1000), so every pixel text shrinks in one place. Small labels, subtitles and status-bar text keep a
 * comfortable minimum: [size] raises a nominal size so the drawn text is never below [MIN_SP] (and never larger than it was
 * before the change). One shared rule - layouts pass their pixel sizes through it, no per-layout factors.
 */
internal object PixelType {
    /** volt_pixel.ttf unitsPerEm 1000 -> 1600. */
    const val SCALE = 0.625f
    /** The smallest drawn pixel text, in sp (before the user's text size, which still scales it). */
    const val MIN_SP = 8.5f

    /** The one pixel family (the same instance everywhere, so [isPixel] can tell it). */
    val family: FontFamily = FontFamily(Font(com.shelf.launcher.R.font.volt_pixel))

    fun isPixel(f: FontFamily?): Boolean = f === family

    /** Drawn size of a nominal pixel size [v] (sp): 62.5 %, but not below min(v, [MIN_SP]). */
    fun drawn(v: Float): Float = maxOf(v * SCALE, minOf(v, MIN_SP))

    /** The nominal size to ask the pixel font for, so it draws at [drawn]. Not sp (em, unspecified): unchanged. */
    fun size(s: TextUnit): TextUnit = if (!s.isSp) s else (drawn(s.value) / SCALE).sp

    /** [s] for text in [f]: through [size] when [f] is the pixel face, else unchanged. */
    fun sizeFor(f: FontFamily?, s: TextUnit): TextUnit = if (isPixel(f)) size(s) else s
}
