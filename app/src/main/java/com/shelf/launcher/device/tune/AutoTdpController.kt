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

/**
 * (Update 237, #85: AutoTDP's own underclock — the decision half; [Tuner.autoUnderclockTick] holds the step and
 * writes it. Works on the ladder Off → Light → Medium → Heavy (0 → 3), only ever one step at a time, inside the range
 * the caller gives (0 .. the AutoTDP style's deepest preset), so it can never go past stock speeds nor deeper than the
 * style allows. The fast AutoTdpController level does the second-to-second work underneath; this only moves when the
 * level can't help any more (shallower) or when a deeper preset would really lower a clock (deeper).
 *
 *  - Shallower (raise the caps): frames missed by more than [MISS] — or by more than [HOLD] with the CPU or GPU
 *    saturated — for [STRUGGLE_MS], while raising the level would change nothing and the power ceiling isn't what's
 *    holding it back, and at least [MIN_DWELL_UP_MS] since the last step.
 *  - Deeper (lower the caps): the target held within [HOLD] with the GPU under [GPU_IDLE] % and the CPU under [CPU_IDLE] %
 *    busy (when those are readable) for [HEADROOM_MS], at least [MIN_DWELL_DOWN_MS] since the last step.
 *  - Hysteresis: the miss / hold thresholds are apart, and a deeper step that had to be undone within [FAIL_WINDOW_MS]
 *    blocks that depth for a back-off that doubles each time ([BACKOFF_START_MS] … [BACKOFF_MAX_MS]).
 *  - No frame-rate reading or no target: holds where it is (no signal is invented).
 * Thresholds are an interim guess pending on-device measurement.)
 */
class AutoUnderclock {
    private var struggleMs = 0L
    private var headroomMs = 0L
    private var sinceStepMs = 0L
    private var sinceDeeperMs = Long.MAX_VALUE / 2
    /** Steps at or deeper than this are off-limits until [blockMs] runs out. */
    private var blockedFrom = Int.MAX_VALUE
    private var blockMs = 0L
    private var backoffMs = BACKOFF_START_MS

    fun reset() {
        struggleMs = 0L; headroomMs = 0L; sinceStepMs = 0L; sinceDeeperMs = Long.MAX_VALUE / 2
        blockedFrom = Int.MAX_VALUE; blockMs = 0L; backoffMs = BACKOFF_START_MS
    }

    fun tick(
        step: Int, range: IntRange, fps: Float?, target: Int?, cpuPct: Int?, gpuPct: Int?,
        overPower: Boolean, levelHasRoom: Boolean, deeperLowers: Boolean, dtMs: Long
    ): Int {
        val s = step.coerceIn(range)
        sinceStepMs += dtMs
        sinceDeeperMs += dtMs
        if (blockMs > 0L) { blockMs -= dtMs; if (blockMs <= 0L) { blockMs = 0L; blockedFrom = Int.MAX_VALUE } }
        if (fps == null || fps <= 1f || target == null || target <= 0) { struggleMs = 0L; headroomMs = 0L; return s }
        val deficit = (target - fps) / target
        val busy = (gpuPct != null && gpuPct >= GPU_BUSY) || (cpuPct != null && cpuPct >= CPU_BUSY)
        val idle = (gpuPct == null || gpuPct < GPU_IDLE) && (cpuPct == null || cpuPct < CPU_IDLE)
        val struggling = !overPower && !levelHasRoom && (deficit > MISS || (deficit > HOLD && busy))
        val headroom = deficit <= HOLD && idle
        when {
            struggling -> { struggleMs += dtMs; headroomMs = 0L }
            headroom -> { headroomMs += dtMs; struggleMs = 0L }
            else -> { struggleMs = 0L; headroomMs = 0L }
        }
        if (struggleMs >= STRUGGLE_MS && sinceStepMs >= MIN_DWELL_UP_MS && s > range.first) {
            // The depth just tried didn't hold the game: keep away from it for a while, longer each time.
            if (sinceDeeperMs < FAIL_WINDOW_MS) {
                blockedFrom = s; blockMs = backoffMs
                backoffMs = (backoffMs * 2).coerceAtMost(BACKOFF_MAX_MS)
            }
            struggleMs = 0L; headroomMs = 0L; sinceStepMs = 0L
            return s - 1
        }
        if (headroomMs >= HEADROOM_MS && sinceStepMs >= MIN_DWELL_DOWN_MS && s < range.last && s + 1 < blockedFrom && deeperLowers) {
            struggleMs = 0L; headroomMs = 0L; sinceStepMs = 0L; sinceDeeperMs = 0L
            return s + 1
        }
        return s
    }

    companion object {
        /** Frame deficit (share of the target) that counts as missing it, and the one that counts as holding it. */
        const val MISS = 0.05f
        const val HOLD = 0.02f
        const val GPU_BUSY = 90
        const val CPU_BUSY = 85
        const val GPU_IDLE = 75
        /** Overall CPU busy % (all cores together): a single-thread-bound game can read low, so it's only a guard. */
        const val CPU_IDLE = 60
        const val STRUGGLE_MS = 3_000L
        const val HEADROOM_MS = 30_000L
        const val MIN_DWELL_UP_MS = 5_000L
        const val MIN_DWELL_DOWN_MS = 30_000L
        const val FAIL_WINDOW_MS = 90_000L
        const val BACKOFF_START_MS = 120_000L
        const val BACKOFF_MAX_MS = 600_000L
    }
}
