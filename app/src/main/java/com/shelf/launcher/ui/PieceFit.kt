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
 * Update 314: a carousel game is wholly off the screen — its middle at [centerX] (window px), reaching [halfExtent] px
 * either side of it — with [margin] px kept round the screen ([viewW] px wide). "To the edge" builds far more games each
 * side than the screen shows (up to ~45 in Box flow on the Thor); each turned 3D cartridge or case drew its pictures
 * every frame while out of sight. Unknown sizes (0) never count as off the screen.
 */
internal fun carouselOffScreen(centerX: Float, halfExtent: Float, viewW: Float, margin: Float): Boolean {
    if (viewW <= 0f || centerX.isNaN() || halfExtent.isNaN()) return false
    return centerX + halfExtent < -margin || centerX - halfExtent > viewW + margin
}
