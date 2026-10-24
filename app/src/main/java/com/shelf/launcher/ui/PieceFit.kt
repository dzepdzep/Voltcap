package com.shelf.launcher.ui

import kotlin.math.roundToInt

/*
 * Update 314 (scrolling the 3D cartridges lagged): plain sums shared by the carousel and the solid pieces, kept apart
 * from Compose so they can be checked by the unit tests.
 */

/**
 * Where CartShellArt draws a shell's front picture ([bw] × [bh] px) in a node of [nodeW] × [nodeH] px: a box of the
 * shell's [aspect] fitted in the middle (Modifier.size, rounded to whole pixels, centred), the picture fitted in that
 * box (ContentScale.Fit, centred). [x, y, w, h] in whole pixels, so a turned piece drawn from the picture lies exactly
 * where the flat one was. Keep in step with CartShellArt.
 */
internal fun pieceArtRect(nodeW: Float, nodeH: Float, aspect: Float, bw: Int, bh: Int): IntArray {
    val a = aspect.coerceAtLeast(0.01f)
    val boxWf = minOf(nodeW, nodeH * a)
    val boxW = boxWf.roundToInt().coerceAtLeast(0)
    val boxH = (boxWf / a).roundToInt().coerceAtLeast(0)
    val boxX = ((nodeW - boxW) / 2f).roundToInt()
    val boxY = ((nodeH - boxH) / 2f).roundToInt()
    if (bw <= 0 || bh <= 0) return intArrayOf(boxX, boxY, boxW, boxH)
    val k = minOf(boxW / bw.toFloat(), boxH / bh.toFloat())
    val dw = (bw * k).roundToInt()
    val dh = (bh * k).roundToInt()
    return intArrayOf(boxX + ((boxW - dw) / 2f).roundToInt(), boxY + ((boxH - dh) / 2f).roundToInt(), dw, dh)
}

/**
 * Update 316: where AuthenticPackCart draws its front ([bw] × [bh] px, stretched to the box) in a node of [nodeW] × [nodeH]
 * px: the pack shape's box of [aspect] fitted in the middle (Modifier.size, centred), the front at [frame] (left, top,
 * right, bottom as shares of that box: Modifier.offset + size, each rounded as Compose rounds Dp). Keep in step with
 * AuthenticPackCart.
 */
internal fun pieceFrameRect(nodeW: Float, nodeH: Float, aspect: Float, frame: FloatArray): IntArray {
    val a = aspect.coerceAtLeast(0.01f)
    val boxWf = minOf(nodeW, nodeH * a)
    val boxHf = boxWf / a
    val boxW = boxWf.roundToInt().coerceAtLeast(0)
    val boxH = boxHf.roundToInt().coerceAtLeast(0)
    val boxX = ((nodeW - boxW) / 2f).roundToInt()
    val boxY = ((nodeH - boxH) / 2f).roundToInt()
    val f0 = frame.getOrElse(0) { 0f }; val f1 = frame.getOrElse(1) { 0f }
    val f2 = frame.getOrElse(2) { 1f }; val f3 = frame.getOrElse(3) { 1f }
    return intArrayOf(
        boxX + (boxWf * f0).roundToInt(), boxY + (boxHf * f1).roundToInt(),
        (boxWf * (f2 - f0)).roundToInt().coerceAtLeast(0), (boxHf * (f3 - f1)).roundToInt().coerceAtLeast(0)
    )
}

/**
 * Update 314: a carousel game is far off the screen — its middle at [centerX] (px from the carousel's left edge),
 * reaching [halfExtent] px either side of it — more than [margin] px past either edge of the carousel ([viewW] px wide).
 * "To the edge" builds far more games each side than the screen shows (up to ~45 in Box flow on the Thor); each turned
 * 3D cartridge or case drew its pictures every frame while out of sight. Unknown sizes (0) never count as off it.
 */
internal fun carouselOffScreen(centerX: Float, halfExtent: Float, viewW: Float, margin: Float): Boolean {
    if (viewW <= 0f || centerX.isNaN() || halfExtent.isNaN()) return false
    return centerX + halfExtent < -margin || centerX - halfExtent > viewW + margin
}

/** Update 316: the sides' outline alpha from a front pixel's [a] (0–255): × 4 − [SIDE_CUT], as the solid filter's matrix makes it. */
internal fun sideAlpha(a: Int): Int = (a * 4 - SIDE_CUT).coerceIn(0, 255)

/**
 * Update 325 (Toni's screenshots: a faint straight line over a Game Boy cartridge's top, past its right side, and down its
 * left side in the game window's Inspect): the sides were made from every pixel of the face over 2 % — a nearly clear
 * background in the face's box came out as a ghost slab round the piece. Now under ~20 % is left out; the shell's own
 * edge (its smoothed outline, ~50 % and up) is solid as before.
 */
internal const val SIDE_CUT = 200

/**
 * Update 316: the sides' colour (0xRRGGBB) [t] of the way down: [tone] under the sheen — white 12 % at the top, clear in
 * the middle, black 18 % at the bottom (each stop's colour and alpha blended in a straight line), laid on as SrcAtop.
 */
internal fun sideRowRgb(t: Float, tone: Int): Int {
    val tr = (tone shr 16) and 255; val tg = (tone shr 8) and 255; val tb = tone and 255
    val sa: Float; val sc: Float
    if (t <= 0.5f) { val k = (t / 0.5f).coerceIn(0f, 1f); sa = 0.12f * (1f - k); sc = 255f * (1f - k) }
    else { val k = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f); sa = 0.18f * k; sc = 0f }
    val r = (sc * sa + tr * (1f - sa)).toInt().coerceIn(0, 255)
    val g = (sc * sa + tg * (1f - sa)).toInt().coerceIn(0, 255)
    val b = (sc * sa + tb * (1f - sa)).toInt().coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}
