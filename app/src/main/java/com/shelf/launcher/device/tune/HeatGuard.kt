package com.shelf.launcher.device.tune

import kotlin.math.exp

/**
 * 682bd (owner 25 Nov 21:24 - "Heat Guard feels too aggressive ... improve the behaviour instead of disabling it").
 *
 * What it reads: [TuneHardware.socTempC] - the hottest CPU / GPU / SoC thermal zone, i.e. the hottest core's junction
 * temperature (per-core sensors such as cpu-1-2 or gpuss; not the battery, not the skin). Junction readings jump by several
 * degrees within one frame burst, so the old rule (8 s at or over the limit, then a whole underclock preset) stepped down on
 * ordinary heavy scenes. The firmware's own thermal limits sit higher - the RP Classic's first passive trip is 95 °C, then
 * 110 / 115 / 125 °C - and stay in charge: the guard only ever LOWERS Voltcap's own speed caps, it never writes a thermal
 * zone, a trip point or a cooling device ([VENDOR_THERMAL_UNTOUCHED]).
 *
 * The guard now:
 *  - smooths the readings (an exponential average with a time constant per preset), so a spike is not heat;
 *  - acts after the SMOOTHED temperature stayed at or over the limit for a while, one small step at a time (a few % of the
 *    current caps - not a jump to Medium / Heavy), with a pause between steps;
 *  - gives a step back once the smoothed temperature stayed a few degrees under the limit (hysteresis), one at a time;
 *  - forgets everything when the game ends ([reset]).
 * Pure: the tuner feeds it readings ([tick]) and applies [cutPercent]; tests feed it synthetic traces.
 */
enum class HeatGuardPreset(
    val label: String, val blurb: String,
    /** Smoothing time constant, s: a spike shorter than this hardly moves the average. */
    val tauS: Float,
    /** Smoothed temperature at / over the limit this long before the first step, ms. */
    val triggerMs: Long,
    /** At least this long between two steps down, ms. */
    val stepGapMs: Long,
    /** How much each step takes off the current speed caps, % (the little cores get half). */
    val stepPercent: Int,
    /** Steps at most. */
    val maxSteps: Int,
    /** A step comes back once the smoothed temperature stayed this many degrees under the limit ... */
    val coolBelowC: Float,
    /** ... for this long, ms (and again for the next step). */
    val coolMs: Long
) {
    GENTLE("Gentle", "Lets short spikes pass; small steps, at most 15 % lower; quick to give speed back",
        tauS = 10f, triggerMs = 20_000L, stepGapMs = 40_000L, stepPercent = 5, maxSteps = 3, coolBelowC = 4f, coolMs = 20_000L),
    BALANCED("Balanced", "Steps in after a sustained run over the limit; up to 28 % lower",
        tauS = 6f, triggerMs = 12_000L, stepGapMs = 30_000L, stepPercent = 7, maxSteps = 4, coolBelowC = 5f, coolMs = 30_000L),
    AGGRESSIVE("Aggressive", "Steps in early to keep it cooler; up to 40 % lower; slower to give speed back",
        tauS = 3f, triggerMs = 6_000L, stepGapMs = 20_000L, stepPercent = 10, maxSteps = 4, coolBelowC = 7f, coolMs = 45_000L);

    companion object {
        fun of(name: String?): HeatGuardPreset = values().firstOrNull { it.name == name } ?: BALANCED
    }
}

class HeatGuard {
    /** Smoothed temperature, °C (null until the first reading). */
    var smoothedC: Float? = null
        private set
    /** Steps taken (0 = not restricting). */
    var step = 0
        private set
    private var hotMs = 0L
    private var coolMs = 0L
    private var sinceStepMs = Long.MAX_VALUE / 2

    fun reset() { smoothedC = null; step = 0; hotMs = 0L; coolMs = 0L; sinceStepMs = Long.MAX_VALUE / 2 }

    /** How much the guard takes off the current caps right now, % (0 = nothing). */
    fun cutPercent(p: HeatGuardPreset): Int = (step.coerceIn(0, p.maxSteps) * p.stepPercent)

    /**
     * One reading [tempC] (null = no reading: nothing changes), [dtMs] since the last one, the user's [limitC]. Returns true
     * when [step] changed.
     */
    fun tick(tempC: Float?, dtMs: Long, limitC: Int, p: HeatGuardPreset): Boolean {
        if (tempC == null || dtMs <= 0L) return false
        val dt = dtMs.coerceAtMost(30_000L)
        val prev = smoothedC
        val ema = if (prev == null) tempC else prev + (tempC - prev) * (1f - exp(-(dt / 1000f) / p.tauS))
        smoothedC = ema
        sinceStepMs += dt
        val limit = limitC.toFloat()
        when {
            ema >= limit -> { hotMs += dt; coolMs = 0L }
            ema <= limit - p.coolBelowC -> { coolMs += dt; hotMs = 0L }
            // In the band between: neither - the guard holds what it has (hysteresis).
            else -> { hotMs = 0L; coolMs = 0L }
        }
        val before = step
        if (hotMs >= p.triggerMs && step < p.maxSteps && sinceStepMs >= p.stepGapMs) { step++; hotMs = 0L; sinceStepMs = 0L }
        else if (coolMs >= p.coolMs && step > 0) { step--; coolMs = 0L; sinceStepMs = 0L }
        // A preset changed to one with fewer steps: down to its deepest at once.
        if (step > p.maxSteps) step = p.maxSteps
        return step != before
    }

    companion object {
        /** The limits offered: under the firmware's own first throttle, never above 95 °C. */
        val THRESHOLDS = listOf(75, 80, 85, 90)
        const val MIN_C = 60
        const val MAX_C = 95

        /**
         * The highest limit worth offering on this handheld: 5 °C under its firmware's first passive trip on the zones the
         * guard reads ([vendorTripC], null = not known), never above [MAX_C].
         */
        fun maxLimitFor(vendorTripC: Int?): Int = if (vendorTripC == null) MAX_C else (vendorTripC - 5).coerceIn(MIN_C, MAX_C)

        fun validLimit(c: Int, vendorTripC: Int?): Int = c.coerceIn(MIN_C, maxLimitFor(vendorTripC))

        /** Never written by Voltcap (checked by a test over the sources): the firmware's own thermal control. */
        val VENDOR_THERMAL_UNTOUCHED = listOf("/sys/class/thermal", "thermal-engine", "thermal_message", "/sys/devices/virtual/thermal")
    }
}
