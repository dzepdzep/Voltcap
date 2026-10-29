package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game

/**
 * Update 400: how many rows deep the Cardboard box is (Layout options › Rows: Auto / 1 / 2 / 3).
 *
 * The box is built to measure (mm), so a row count is a physical question, not a scale factor: N lanes of the longest case
 * in the category plus the gaps between them must fit a storage box no deeper than [MAX_DEPTH_MM] (the existing two-row,
 * two-DVD-length box is 414 mm), and the cases must still be big enough on THIS screen to read ([MIN_LANE_DP] long,
 * [MIN_THICK_DP] thick). Nothing is shrunk to cram a row in: a request that can't be met falls back to two rows and says why.
 * Two rows keep exactly the geometry of updates 330-399.
 */
internal object BoxRows {
    /** Deepest storage box (mm): the two-row box is 414 mm; a three-row box of compact cases (DS 122 mm) is ~400 mm. */
    const val MAX_DEPTH_MM = 420f
    /** The gap between the lanes of a three-row box (mm): narrower than the 26 mm two-row gap, which is a hand's width. */
    const val GAP_THREE = 14f
    /** Smallest case length / thickness on the screen that still reads (dp). */
    const val MIN_LANE_DP = 70f
    const val MIN_THICK_DP = 5f

    enum class Limit { NONE, TOO_DEEP, TOO_SMALL }

    /** [rows] is what is drawn, [requested] what the setting asked for (0 = Auto); [limit] why a request was not met. */
    class Decision(val rows: Int, val requested: Int, val limit: Limit) {
        val fellBack get() = limit != Limit.NONE
    }

    /** What the category's cases need (mm): the longest, the tallest standing, the thinnest, and the rows they fill. */
    class Plan(val lane: Float, val stand: Float, val thin: Float, val needRows: Int)

    fun gapFor(rows: Int): Float = when (rows) { 1 -> 0f; 2 -> BOX_ROW_GAP; else -> GAP_THREE }

    /** Inside depth of a [rows]-row box for lanes [laneMm] long (two rows: the fixed two-DVD box). */
    fun depthFor(rows: Int, laneMm: Float): Float =
        if (rows == 2) BOX_LEN * 2 + BOX_ROW_GAP + BOX_MARGIN * 2
        else rows * laneMm + (rows - 1) * gapFor(rows) + BOX_MARGIN * 2

    /** The lane a [rows]-row box is drawn with: two rows keep the DVD lane; the others the category's longest case. */
    fun laneFor(rows: Int, plan: Plan): Float = if (rows == 2) BOX_LEN else plan.lane.coerceIn(90f, BOX_LEN)

    /** Whether [rows] rows of lanes [laneMm] long fit a real storage box. */
    fun fitsDepth(rows: Int, laneMm: Float): Boolean = laneMm <= BOX_LEN && depthFor(rows, laneMm) <= MAX_DEPTH_MM + 0.01f

    fun plan(games: List<Game>, wMm: Float, gapMm: Float, messy: Boolean): Plan {
        var lane = 0f; var stand = 0f; var thin = Float.MAX_VALUE
        val usable = wMm - 2 * BOX_MARGIN
        var rows = 1; var x = 0f
        for (g in games) {
            val m = caseMm(g, messy)
            lane = maxOf(lane, m.len); stand = maxOf(stand, m.stand); thin = minOf(thin, m.thick)
            if (x > 0f && x + m.extra + m.thick > usable) { rows++; x = 0f }
            x += m.extra + m.thick + gapMm
        }
        if (games.isEmpty()) { lane = BOX_LEN; stand = 135f; thin = 12f }
        return Plan(lane, stand, thin, rows)
    }

    /** Can [rows] rows be drawn on this screen: physical depth first, then size on the screen. */
    fun limitFor(rows: Int, plan: Plan, wPx: Float, hPx: Float, tilt: Float, sizeK: Float, density: Float): Limit {
        val lane = laneFor(rows, plan)
        if (!fitsDepth(rows, lane)) return Limit.TOO_DEEP
        val cam = boxCam(wPx, hPx, tilt, sizeK, rows, lane)
        val k = cam.k(plan.stand.coerceIn(60f, 200f))
        val d = density.coerceAtLeast(0.5f)
        if (lane * k / d < MIN_LANE_DP || plan.thin * k / d < MIN_THICK_DP) return Limit.TOO_SMALL
        return Limit.NONE
    }

    /**
     * Auto: three rows only when the cases are compact enough to fit and read AND the games need more than two rows
     * (so a few games keep the familiar two-row box); otherwise two. 1 / 2 / 3 force that count; a forced 3 that can't
     * be drawn falls back to 2 with the reason.
     */
    fun decide(pref: Features.BoxRowsPref, plan: Plan, wPx: Float, hPx: Float, tilt: Float, sizeK: Float, density: Float): Decision = when (pref) {
        Features.BoxRowsPref.ONE -> Decision(1, 1, Limit.NONE)
        Features.BoxRowsPref.TWO -> Decision(2, 2, Limit.NONE)
        Features.BoxRowsPref.THREE -> limitFor(3, plan, wPx, hPx, tilt, sizeK, density).let { l ->
            if (l == Limit.NONE) Decision(3, 3, Limit.NONE) else Decision(2, 3, l)
        }
        Features.BoxRowsPref.AUTO ->
            if (plan.needRows > 2 && limitFor(3, plan, wPx, hPx, tilt, sizeK, density) == Limit.NONE) Decision(3, 0, Limit.NONE)
            else Decision(2, 0, Limit.NONE)
    }

    fun limitText(d: Decision): String? = when (d.limit) {
        Limit.TOO_DEEP -> "3 rows need shorter cases than this category has: showing 2"
        Limit.TOO_SMALL -> "3 rows would make the cases too small on this screen: showing 2"
        Limit.NONE -> null
    }

    /** Row above (-1) / below (+1) [row] in a box of [rows] rows, or null at the box's first / last row. */
    fun rowStep(row: Int, dir: Int, rows: Int): Int? = (row + dir).takeIf { it in 0 until rows }
}
