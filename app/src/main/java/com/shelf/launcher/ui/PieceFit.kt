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
