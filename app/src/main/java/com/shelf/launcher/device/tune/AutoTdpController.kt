package com.shelf.launcher.device.tune

/**
 * (Update 429, AutoTDP audit) What the controllers may act on this second, already cleaned up by [TdpSignals.from]:
 *  - [fps]: the game's frame rate, or null. Never the screen's refresh rate (no game package) — that used to reach the
 *    controller as "fps" and read as "on target", so it lowered clocks with no game signal at all.
 *  - [fresh]: false for the sampler's 4 s hold of the last good value (a repeat, not a new measurement).
 *  - [displayCounter]: the reading counts frames the panel showed (SurfaceFlinger's frame counter, the display driver's
 *    measured_fps), not the game's own layer — it can sit at the refresh rate whatever the game does.
 *  - [batteryW]: the battery's draw, only while running from the battery. It is the WHOLE handheld (screen, radios,
 *    fan, SoC), not SoC TDP, and while a charger is in it is the charge current (or, at a charge limit, near zero) —
 *    so it is null then, never used as a power reading.
 *  - [screenHz]: the panel's refresh rate (no game can show more frames than that).
 */
data class TdpInput(
    val fps: Float?,
    val fresh: Boolean = true,
    val displayCounter: Boolean = false,
    val batteryW: Float? = null,
    val screenHz: Float? = null
)

/** What AutoTDP is doing right now (shown on the tuner page). */
enum class TdpState(val label: String?) {
    NO_SIGNAL("no game frame rate read: holding"),
    HOLDING(null),
    SAVING(null),
    RECOVERING("recovering"),
    /** Over the power budget with frames below the target: the target can't be held within it. */
    POWER_LIMITED("target not reachable within the power budget"),
    /** A panel-level counter that has never dipped below the refresh rate: not trusted to lower clocks far. */
    UNVERIFIED("frame counter unverified: limited savings")
}

object TdpSignals {
    /**
     * [kind]: [com.shelf.launcher.data.perf.FpsKind] name of the sampler's reading. [plugged]: a charger / USB power is
     * connected (null = unknown, treated as plugged: no power reading is better than a wrong one).
     */
    fun from(fps: Float?, kind: String?, powerW: Float?, plugged: Boolean?, charging: Boolean?, screenHz: Float?): TdpInput {
        val gameFps = when (kind) { "GAME_LAYER", "DISPLAY", "HELD" -> fps?.takeIf { it > 1f }; else -> null }
        val onBattery = plugged == false && charging != true
        return TdpInput(
            fps = gameFps,
            fresh = kind != "HELD",
            displayCounter = kind == "DISPLAY",
            batteryW = powerW?.takeIf { onBattery && it in 0.2f..60f },
            screenHz = screenHz?.takeIf { it > 20f }
        )
    }
}

/**
 * AutoTDP: finds the lowest clock scale that still holds the target frame rate, under the power budget of the chosen
 * [AutoTdpMode]. One [tick] per sample (about once a second). The level is a multiplier on the profile's own caps
 * (1 = the profile as set, [MIN_LEVEL] = the floor).
 *
 * (Update 429, from the source audit — each rule has a deterministic test in AutoTdp429Test:)
 *  - Frames below target come first. Over the power budget with frames below the target, it no longer cuts at 0.04 a
 *    second (that took a game already short of its target down to the floor in ~15 s): it eases down 0.01 a second,
 *    never raises, and reports [TdpState.POWER_LIMITED]. Over budget with the target held it lowers at the old rate.
 *  - Power is smoothed (EMA) and counts as over only after [OVER_TICKS] readings above the budget, with a band below it
 *    before it counts as under again (a one-second spike from the radio or screen no longer throttles).
 *  - Recovery is proportional: a big miss jumps towards level × target / fps (at most [MAX_JUMP] a tick), so a scene
 *    change recovers in one or two seconds instead of up to eight.
 *  - Anti-oscillation: a step down that has to be undone within [FAIL_TICKS] makes the level it came from a floor for a
 *    back-off ([BACKOFF_START] … [BACKOFF_MAX] ticks, doubling) — no more 3 s down / 1 s up sawtooth.
 *  - Only fresh readings lower the level (the sampler's 4 s hold is a repeat) and feed target detection.
 *  - A panel-level counter that has never shown a dip below the refresh rate can't lower the level below
 *    [UNVERIFIED_FLOOR]: it may be counting refreshes, not game frames.
 *  - The detected target never goes above the refresh rate and only comes down when the level is near full, or when
 *    raises keep being refused because a cap above it binds (a low frame rate AutoTDP itself caused is not mistaken for
 *    the game's cap).
 */
class AutoTdpController(private val mode: AutoTdpMode, private val fixedTarget: Int) {
    private var onTarget = 0
    private val recentFps = ArrayDeque<Float>()
    private var detected: Int? = null
    private var powerEma: Float? = null
    private var overTicks = 0
    private var powerOver = false
    private var tickNo = 0L
    private var lastDownTick = Long.MIN_VALUE / 2
    private var levelBeforeDown = 1f
    private var floor = MIN_LEVEL
    private var floorUntil = 0L
    private var backoff = BACKOFF_START
    private var lastFailTick = Long.MIN_VALUE / 2
    private var counterDipped = false
    /** What the last tick asked for, and how many ticks in a row a raise it asked for didn't happen (a cap above it binds). */
    private var asked = Float.NaN
    private var askedFrom = Float.NaN
    private var raiseRefused = 0

    /** The frame rate being held: fixed, or worked out from what the game reaches. */
    val target: Int? get() = if (fixedTarget > 0) fixedTarget else detected

    var state: TdpState = TdpState.NO_SIGNAL
        private set

    /** The smoothed battery draw the budget is compared with (whole handheld), or null. */
    val smoothedW: Float? get() = powerEma

    /** Compatibility form (old callers / tests): a fresh game reading and a battery reading. */
    fun tick(level: Float, fps: Float?, powerW: Float?): Float = tick(level, TdpInput(fps = fps, batteryW = powerW))

    fun tick(level: Float, input: TdpInput): Float {
        tickNo++
        var l = level
        if (!asked.isNaN() && asked > askedFrom + 0.01f) { if (level <= askedFrom + 0.001f) raiseRefused++ else raiseRefused = 0 }
        else raiseRefused = 0
        val fps = input.fps?.takeIf { it > 1f }
        val hz = input.screenHz
        // Power: smoothed, with a few readings and a band before it counts as over / under.
        val w = input.batteryW
        if (w == null) { powerEma = null; overTicks = 0; powerOver = false }
        else {
            val e = powerEma?.let { it + (w - it) * EMA } ?: w
            powerEma = e
            if (e > mode.ceilingW) overTicks++ else overTicks = 0
            if (overTicks >= OVER_TICKS) powerOver = true
            else if (e < mode.ceilingW * UNDER_BAND) powerOver = false
        }
        val over = powerOver
        if (fps != null && input.fresh) {
            recentFps.addLast(fps); while (recentFps.size > 20) recentFps.removeFirst()
            if (input.displayCounter && hz != null && fps < hz * 0.97f) counterDipped = true
            if (fixedTarget <= 0) detect(level, over, hz)
        }
        if (fps != null && input.fresh && !input.displayCounter) counterDipped = true // the game's own layer: real frames
        val t = target?.let { if (hz != null) minOf(it, snapTarget(hz)) else it }
        if (floorUntil in 1..tickNo) { floor = MIN_LEVEL; floorUntil = 0L }
        val minL = if (input.displayCounter && !counterDipped) maxOf(floor, UNVERIFIED_FLOOR) else floor
        if (fps != null && t != null) {
            val deficit = (t - fps) / t
            when {
                deficit > HOLD && over -> {
                    // Short of the target AND over the budget: the budget wins, but gently (and only while the smoothed
                    // draw is really above it — inside the band it holds), and it's reported.
                    if ((powerEma ?: 0f) > mode.ceilingW) l -= 0.01f
                    onTarget = 0; state = TdpState.POWER_LIMITED
                }
                deficit > HOLD -> {
                    var up = when {
                        deficit > 0.15f -> minOf(MAX_JUMP, maxOf(0.05f, level * (t / fps) * 1.05f - level))
                        deficit > 0.08f -> minOf(0.12f, 0.03f + deficit * 0.5f)
                        else -> 0.02f
                    }
                    // Close to the budget: only as far up as its headroom allows (else the jump overshoots, the budget
                    // pulls it back down, and it saws). No headroom left = the target can't be held within the budget.
                    var limited = false
                    val e = powerEma
                    if (e != null && e > mode.ceilingW * 0.8f) {
                        up = minOf(up, maxOf(0f, level * (mode.ceilingW / e - 1f) * 0.5f) + 0.005f)
                        limited = up <= 0.0051f && e >= mode.ceilingW * 0.97f
                    }
                    // A step down that didn't hold: the level it came from is a floor for a while. Only a modest miss
                    // counts — a loading screen's collapse isn't the clocks' fault — and the back-off starts again after a
                    // long run without a failed step.
                    if (tickNo - lastFailTick > BACKOFF_RESET) backoff = BACKOFF_START
                    if (tickNo - lastDownTick <= FAIL_TICKS && deficit <= FAIL_DEFICIT && levelBeforeDown > floor) {
                        floor = levelBeforeDown; floorUntil = tickNo + backoff; lastFailTick = tickNo
                        backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX)
                        l = maxOf(l + up, levelBeforeDown)
                    } else l += up
                    onTarget = 0; state = if (limited) TdpState.POWER_LIMITED else TdpState.RECOVERING
                }
                over -> { if (input.fresh) l -= 0.04f; onTarget = 0; state = TdpState.SAVING }
                else -> {
                    state = TdpState.HOLDING
                    if (input.fresh) {
                        onTarget++
                        if (onTarget >= 3) {
                            onTarget = 0
                            val down = maxOf(l - 0.025f, minL)
                            if (down < l - 0.001f) { levelBeforeDown = l; lastDownTick = tickNo; l = down; state = TdpState.SAVING }
                        }
                    }
                    if (input.displayCounter && !counterDipped && l <= UNVERIFIED_FLOOR + 0.001f) state = TdpState.UNVERIFIED
                }
            }
        } else {
            onTarget = 0
            state = TdpState.NO_SIGNAL
            // No game frame rate: only the budget may move it (down, gently); it never drifts on its own.
            if (over) l -= 0.02f
        }
        // The budget may go below the floors (it's the user's ceiling); frame-rate lowering never does. Over budget: no raise.
        val lo = if (over) MIN_LEVEL else minOf(minL, level).coerceAtLeast(MIN_LEVEL)
        val out = l.coerceIn(lo, 1f).let { if (over && it > level) level else it }
        asked = out; askedFrom = level
        return out
    }

    private fun snapTarget(v: Float): Int = TARGETS.lastOrNull { it <= v * 1.06f } ?: TARGETS.first()

    /** Snaps the best recent frame rate to the nearest common target (a game capped at 30 holds 30). */
    private fun detect(level: Float, over: Boolean, hz: Float?) {
        if (recentFps.size < 5) return
        val peak = recentFps.sorted()[(recentFps.size * 0.9f).toInt().coerceAtMost(recentFps.size - 1)]
        var snapped = snapTarget(peak)
        if (hz != null) snapped = minOf(snapped, snapTarget(hz))
        val cur = detected
        // Up when the game really runs faster; down only when it stays well below with the clocks near full (and not
        // held back by the budget) — so neither a dip nor AutoTDP's own lowering is mistaken for a cap.
        val lower = cur != null && recentFps.size >= 20 && recentFps.all { it < cur * 0.8f } && (level >= 0.95f || raiseRefused >= 5) && !over
        detected = if (cur == null || snapped > cur || lower) snapped else cur
    }

    companion object {
        const val MIN_LEVEL = 0.35f
        val TARGETS = listOf(30, 40, 45, 60, 72, 90, 120)
        const val HOLD = 0.02f
        const val EMA = 0.35f
        const val OVER_TICKS = 3
        const val UNDER_BAND = 0.92f
        const val MAX_JUMP = 0.25f
        const val FAIL_TICKS = 8L
        const val BACKOFF_START = 60L
        const val BACKOFF_MAX = 480L
        const val BACKOFF_RESET = 600L
        /** A miss bigger than this right after a step down is a scene / loading collapse, not the step's fault. */
        const val FAIL_DEFICIT = 0.25f
        const val UNVERIFIED_FLOOR = 0.6f
        const val START_DEFAULT = 0.85f
        const val START_MARGIN = 0.1f

        /** Where a game's session starts: its learned level plus [START_MARGIN], else [START_DEFAULT]. */
        fun startLevel(learned: Float?): Float =
            (if (learned == null || learned.isNaN()) START_DEFAULT else learned + START_MARGIN).coerceIn(MIN_LEVEL, 1f)
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
        // (Update 429: an unreadable GPU load is no longer "idle" — deeper presets need a real reading. The overall CPU % is
        // rarely readable on Android (/proc/stat is closed to apps), so without it the GPU has to be clearly idle.)
        val idle = gpuPct != null && (if (cpuPct == null) gpuPct < GPU_IDLE_NO_CPU else gpuPct < GPU_IDLE && cpuPct < CPU_IDLE)
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
        /** GPU idle threshold when the CPU load can't be read. */
        const val GPU_IDLE_NO_CPU = 60
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
