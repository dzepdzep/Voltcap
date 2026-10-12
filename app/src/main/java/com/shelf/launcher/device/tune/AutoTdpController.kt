package com.shelf.launcher.device.tune

/**
 * AutoTDP: finds the lowest clock scale that still holds the target frame rate, under the power
 * ceiling of the chosen [AutoTdpMode]. One [tick] per sample (about once a second). The level is a
 * multiplier on the profile's own caps (1 = the profile as set, [MIN_LEVEL] = the floor).
 *
 * Voltcap's own controller: step up fast when frames are being missed, settle down slowly once the
 * target has held for a few seconds, and back off whenever the power reading is over the ceiling.
 */
class AutoTdpController(private val mode: AutoTdpMode, private val fixedTarget: Int) {
    private var onTarget = 0
    private val recentFps = ArrayDeque<Float>()
    private var detected: Int? = null

    /** The frame rate being held: fixed, or worked out from what the game reaches. */
    val target: Int? get() = if (fixedTarget > 0) fixedTarget else detected

    fun tick(level: Float, fps: Float?, powerW: Float?): Float {
        var l = level
        if (fps != null && fps > 1f) {
            recentFps.addLast(fps); while (recentFps.size > 20) recentFps.removeFirst()
            if (fixedTarget <= 0) detect()
        }
        val t = target
        val over = powerW != null && powerW > mode.ceilingW
        if (fps != null && fps > 1f && t != null) {
            val deficit = (t - fps) / t
            when {
                over -> { l -= 0.04f; onTarget = 0 }
                deficit > 0.08f -> { l += minOf(0.12f, 0.03f + deficit * 0.5f); onTarget = 0 }
                deficit > 0.02f -> { l += 0.02f; onTarget = 0 }
                else -> {
                    onTarget++
                    if (onTarget >= 3) { l -= 0.025f; onTarget = 0 }
                }
            }
        } else if (over) {
            l -= 0.04f
        } else if (powerW != null && powerW < mode.ceilingW * 0.7f && fps == null) {
            // No frame reading: drift up slowly while there's plenty of headroom.
            l += 0.01f
        }
        return l.coerceIn(MIN_LEVEL, 1f)
    }

    /** Snaps the best recent frame rate to the nearest common target (a game capped at 30 holds 30). */
    private fun detect() {
        if (recentFps.size < 5) return
        val peak = recentFps.sorted()[(recentFps.size * 0.9f).toInt().coerceAtMost(recentFps.size - 1)]
        val snapped = TARGETS.lastOrNull { it <= peak * 1.06f } ?: TARGETS.first()
        // Only move the target up when the game really runs faster, so a dip isn't mistaken for a cap.
        val cur = detected
        detected = if (cur == null || snapped > cur || recentFps.size >= 20 && recentFps.all { it < cur * 0.8f }) snapped else cur
    }

    companion object {
        const val MIN_LEVEL = 0.35f
        val TARGETS = listOf(30, 40, 45, 60, 72, 90, 120)
    }
}
