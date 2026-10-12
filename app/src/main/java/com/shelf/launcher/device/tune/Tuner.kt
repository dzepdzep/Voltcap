package com.shelf.launcher.device.tune

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the tuner is doing right now (settings page, R1 tile, notification). */
data class TunerStatus(
    val supported: Boolean = false,
    /** "Device" or the game / app the active profile belongs to. */
    val source: String = "Device",
    val profile: TuneProfile = TuneProfile(),
    /** AutoTDP's current scale (1 = the profile's own caps). */
    val level: Float = 1f,
    val targetFps: Int? = null,
    val fps: Float? = null,
    val powerW: Float? = null,
    val tempC: Float? = null,
    val fanPercent: Int? = null,
    val sleeping: Boolean = false,
    /** Last write that didn't stick, or null. */
    val note: String? = null,
    /** Extra underclock steps the heat guard is holding (0 = none). */
    val heatStep: Int = 0
)

/**
 * The performance tuner: turns a [TuneProfile] into CPU / GPU caps, floors and governors, keeps the
 * device's own values to put back, swaps profiles when a game starts and ends, and handles the
 * screen-off "sleep" profile. The live parts (AutoTDP, fan control, re-applying caps that the
 * vendor's own game daemon resets mid-game) run in [TuneService].
 */
object Tuner {
    private val _status = MutableStateFlow(TunerStatus())
    val status: StateFlow<TunerStatus> = _status

    private data class Stock(
        val max: Map<String, Int>, val min: Map<String, Int>, val gov: Map<String, String>,
        val gpuMax: Int?, val gpuMin: Int?, val fanMode: Int?,
        val msmMax: String? = null, val msmMin: String? = null,
        val gpuMaxHz: String? = null, val gpuMinHz: String? = null
    )

    /**
     * [limit] = a real limit is active: the vendor's boosts are switched off and the caps are also
     * written where the perf HAL looks (msm_performance, GPU devfreq). False = put everything back.
     */
    data class Target(
        val max: Map<String, Int>, val min: Map<String, Int>, val gov: Map<String, String>,
        val gpuMax: Int?, val gpuMin: Int?, val limit: Boolean = false
    )

    @Volatile private var stock: Stock? = null
    @Volatile private var lastTarget: Target? = null
    @Volatile private var current: TuneProfile = TuneProfile()
    @Volatile private var currentKey: String? = null
    /** Where the running game's profile comes from: its store key, or null for the device-wide one. */
    @Volatile private var currentSourceKey: String? = null
    @Volatile private var level: Float = 1f
    @Volatile private var sleeping = false
    @Volatile private var started = false
    /** Stick lights as a battery / temperature light (run by [TuneService]). */
    @Volatile var ledStatus: LedStatus = LedStatus.OFF
        private set

    fun setLedStatus(context: Context, mode: LedStatus) {
        TuneStore(context).ledStatus = mode
        ledStatus = mode
        if (mode != LedStatus.OFF) TuneService.ensure(context)
        else {
            runCatching { com.shelf.launcher.data.ControllerLights.apply(context, false, false, 0, 0) }
            // Still needed for an active profile, or mid-game for the heat guard / measurements.
            if (!current.needsLoop && !current.active && !(currentKey != null && (heatGuardOn || measuring))) TuneService.stop(context)
        }
    }

    val supported: Boolean get() = TuneHardware.available && TuneHardware.clusters().isNotEmpty()

    /** Once per process: applies the device-wide profile and watches the screen for the sleep profile. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread {
            runCatching {
                _status.value = _status.value.copy(supported = supported)
                if (supported) applyGlobal(app)
            }
        }.start()
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val off = i.action == Intent.ACTION_SCREEN_OFF
                    Thread { runCatching { onScreen(app, !off) } }.start()
                }
            }, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
        }
    }

    /** The device-wide profile (Settings › Device › Performance tuner), when no game has its own. Blocking. */
    @Synchronized
    fun applyGlobal(context: Context) {
        if (currentKey != null) return // a game's profile is in charge right now
        apply(context, TuneStore(context).global(), key = null, source = "Device")
    }

    /** The running game, as launched (to work its profile out again when profiles change mid-game). */
    private data class RunningGame(val key: String, val name: String, val packageName: String?, val platform: String?, val consoleName: String?)
    @Volatile private var game: RunningGame? = null

    /**
     * Which profile a game uses, and where it comes from: its own (or its app package's), else its
     * console's ("plat:<platform>", when console profiles are on), else the device-wide one.
     * Returns (profile, store key or null for device-wide, label for "from …").
     */
    private fun resolve(store: TuneStore, g: RunningGame): Triple<TuneProfile, String?, String> {
        store.forKey(g.key)?.let { return Triple(it, g.key, g.name) }
        g.packageName?.let { pkg -> store.forKey("pkg:$pkg")?.let { return Triple(it, "pkg:$pkg", g.name) } }
        if (store.consoleProfiles && !g.platform.isNullOrBlank()) {
            store.forKey("plat:${g.platform}")?.let { return Triple(it, "plat:${g.platform}", "${g.consoleName ?: g.platform} profile") }
        }
        return Triple(store.global(), null, "Device")
    }

    /**
     * A game is starting: its own profile, its console's, or the device-wide one. Blocking.
     * [platform] / [consoleName]: ROM games only (console profiles).
     */
    @Synchronized
    fun onGameLaunch(context: Context, key: String, name: String, packageName: String?, platform: String? = null, consoleName: String? = null) {
        if (!supported) return
        val store = TuneStore(context)
        val g = RunningGame(key, name, packageName, platform, consoleName)
        game = g
        heatReset()
        val (profile, sourceKey, label) = resolve(store, g)
        level = if (profile.autoTdp) (store.learnedLevel(key) ?: 0.85f).coerceIn(AutoTdpController.MIN_LEVEL, 1f) else 1f
        currentSourceKey = sourceKey
        apply(context, profile, key = key, source = label)
        // Screen rate, button layout, charge limit: a game's own or its console's profile changes those.
        if (sourceKey != null) GameExtras.apply(context, profile)
        if (store.measure) measureStart(context, key, profile)
    }

    /** Back in Voltcap: the game's profile ends; the device-wide one (or stock) returns. Blocking. */
    @Synchronized
    fun onGameEnded(context: Context) {
        // Voltcap was closed while the game ran: no session here, but the buttons / screen rate it set still
        // have to go back.
        val key = currentKey ?: run { GameExtras.restore(context); return }
        if (current.autoTdp) TuneStore(context).setLearnedLevel(key, level)
        measureEnd(context)
        currentKey = null
        currentSourceKey = null
        game = null
        heatReset()
        level = 1f
        GameExtras.restore(context)
        apply(context, TuneStore(context).global(), key = null, source = "Device")
    }

    /**
     * A profile was edited (tuner page, a game's settings, a tile, the notification): apply it at once if it
     * concerns what's running. [key] null = the device-wide profile. Mid-game the game's profile is worked
     * out again, so adding / removing a game's or console's profile takes effect too (it used to wait until
     * the game ended).
     */
    fun profileChanged(context: Context, key: String?) {
        Thread {
            runCatching {
                synchronized(this) {
                    val g = game
                    if (currentKey == null || g == null) {
                        if (key == null) applyGlobal(context)
                        return@synchronized
                    }
                    val relevant = key == null || key == g.key || key == "pkg:${g.packageName}" || key == "plat:${g.platform}"
                    if (!relevant) return@synchronized
                    val store = TuneStore(context)
                    val (profile, sourceKey, label) = resolve(store, g)
                    if (profile.autoTdp && !current.autoTdp) level = (store.learnedLevel(g.key) ?: 0.85f).coerceIn(AutoTdpController.MIN_LEVEL, 1f)
                    if (!profile.autoTdp) level = 1f
                    currentSourceKey = sourceKey
                    apply(context, profile, g.key, label)
                    if (sourceKey != null) GameExtras.apply(context, profile)
                    measureProfile(context, profile)
                }
            }
        }.start()
    }

    private fun apply(context: Context, p: TuneProfile, key: String?, source: String) {
        if (!supported) return
        captureStock()
        current = p
        currentKey = key
        val store = TuneStore(context)
        ledStatus = store.ledStatus
        heatGuardOn = store.heatGuard
        heatGuardC = store.heatGuardC
        measuring = key != null && store.measure
        // Heat guard switched off, or no game: its extra steps end with it.
        if (!heatGuardOn || key == null) heatReset()
        val t = compute(p, level)
        write(t)
        _status.value = _status.value.copy(supported = true, source = source, profile = p, level = level, heatStep = heatStep)
        // Any active profile needs the loop: the vendor's daemon lifts caps within seconds, also
        // outside games (the device-wide tier used to be written once and then quietly undone).
        // During a game the heat guard and the measurements need it too.
        val gameLoop = key != null && (heatGuardOn || measuring)
        if (p.needsLoop || p.active || ledStatus != LedStatus.OFF || gameLoop) TuneService.ensure(context) else TuneService.stop(context)
        // Fan: vendor modes are set once here; Voltcap's own curve / hold-temp runs in the service.
        applyFanMode(p)
    }

    // ---- AutoTDP (called by the service) ----

    fun autoTdpLevel(): Float = level

    @Synchronized
    fun setLevel(newLevel: Float) {
        val l = newLevel.coerceIn(AutoTdpController.MIN_LEVEL, 1f)
        if (kotlin.math.abs(l - level) < 0.015f) return
        level = l
        if (!sleeping) write(compute(current, level))
        _status.value = _status.value.copy(level = level)
    }

    fun currentProfile(): TuneProfile = current

    fun updateLive(fps: Float?, target: Int?, powerW: Float?, tempC: Float?, fan: Int?) {
        _status.value = _status.value.copy(fps = fps, targetFps = target, powerW = powerW, tempC = tempC, fanPercent = fan)
    }

    /** The vendor's game daemon resets caps mid-game: read back and write again if it did. */
    @Synchronized
    fun reassert() {
        val t = lastTarget ?: return
        if (sleeping || !t.limit) return
        val drifted = TuneHardware.clusters().any { c ->
            t.max[c.policy]?.let { it != TuneHardware.currentMax(c) } == true ||
                t.gov[c.policy]?.let { it != TuneHardware.currentGovernor(c) } == true
        } || TuneHardware.gpu()?.let { g -> t.gpuMax != null && TuneHardware.gpuLevels(g).first != t.gpuMax } == true ||
            TuneHardware.overrides()?.let { o ->
                // The perf HAL raised its own limit again, or switched a boost back on.
                (o.msmMax != null && !msmHolds(TuneHardware.read(o.msmMax), msmList(t.max))) ||
                    o.boosts.keys.any { TuneHardware.read(it) != "0" }
            } == true
        if (drifted) write(t)
    }

    // ---- screen off / on ----

    private fun onScreen(context: Context, on: Boolean) {
        // Screen-off time isn't play: the measurements leave it out.
        run?.let { r ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (!on && r.pausedAt == 0L) r.pausedAt = now
            if (on && r.pausedAt > 0L) { r.pausedMs += now - r.pausedAt; r.pausedAt = 0L }
        }
        if (!supported || !TuneStore(context).sleepProfile) { if (on && sleeping) synchronized(this) { if (sleeping) wake() }; return }
        synchronized(this) {
            if (!on && !sleeping) {
                sleeping = true
                write(compute(TuneProfile(tier = PowerTier.SAVER, powerTarget = 50, governor = GovernorChoice.POWERSAVE), 1f), remember = false)
                _status.value = _status.value.copy(sleeping = true)
            } else if (on) wake()
        }
    }

    private fun wake() {
        sleeping = false
        write(compute(current, level))
        _status.value = _status.value.copy(sleeping = false)
    }

    // ---- the maths: profile → targets ----

    fun compute(p: TuneProfile, lvl: Float): Target {
        val s = stock
        val clusters = TuneHardware.clusters()
        val max = HashMap<String, Int>()
        val min = HashMap<String, Int>()
        val gov = HashMap<String, String>()
        val scale = (p.powerTarget.coerceIn(30, 100) / 100f) * lvl
        val limit = (p.active && (p.tier != PowerTier.OFF || p.powerTarget < 100 || p.autoTdp || p.primeBoost < 100 || p.underclocked)) ||
            heatCeiling(p) != null
        clusters.forEachIndexed { i, c ->
            val base = when (p.tier) {
                PowerTier.CUSTOM -> p.clusterCaps[c.name] ?: 100
                else -> p.tier.cpu
            }
            var pct = base * scale
            if (i == clusters.lastIndex && clusters.size > 1) pct = minOf(pct, p.primeBoost.toFloat())
            val stockMax = s?.max?.get(c.policy) ?: c.maxKhz
            var khz = if (pct >= 99.5f) stockMax else TuneHardware.snap(c, (c.maxKhz * pct / 100f).toInt())
            // Underclock: a hard ceiling on top of whatever the tier / AutoTDP chose; the heat guard's
            // extra steps on top of that while the chip runs hot.
            underclockKhz(p, c, i, clusters.size)?.let { khz = minOf(khz, it) }
            heatCeiling(p)?.let { h -> underclockKhz(h, c, i, clusters.size)?.let { khz = minOf(khz, it) } }
            max[c.policy] = khz
            val floorKhz = if (p.cpuFloor > 0) TuneHardware.snap(c, (c.maxKhz * p.cpuFloor / 100f).toInt()) else (s?.min?.get(c.policy) ?: c.minKhz)
            min[c.policy] = minOf(floorKhz, khz)
            var choice = if (p.tier == PowerTier.CUSTOM || p.tier == PowerTier.OFF) p.governor else p.tier.governor
            // AutoTDP sets the ceiling; "performance" would pin every core at it even on easy frames.
            if (p.autoTdp && choice == GovernorChoice.PERFORMANCE) choice = GovernorChoice.BALANCED
            (TuneHardware.governorFor(c, choice) ?: s?.gov?.get(c.policy))?.let { gov[c.policy] = it }
        }
        val g = TuneHardware.gpu()
        var gpuMax: Int? = null
        var gpuMin: Int? = null
        if (g != null) {
            val base = if (p.tier == PowerTier.CUSTOM) p.gpuCap else p.tier.gpu
            val pct = base * (if (p.cpuOnly) lvl else scale)
            val tierMax = if (pct >= 99.5f) (s?.gpuMax ?: 0) else TuneHardware.gpuLevelFor(g, pct.toInt())
            // Higher level = slower: the underclock wins when it's the slower of the two.
            val ucLevel = listOfNotNull(underclockGpuLevel(p, g), heatCeiling(p)?.let { underclockGpuLevel(it, g) }).maxOrNull()
            gpuMax = if (ucLevel != null) maxOf(tierMax, ucLevel) else tierMax
            gpuMin = if (p.gpuFloor > 0) maxOf(TuneHardware.gpuLevelFor(g, p.gpuFloor), gpuMax) else (s?.gpuMin ?: g.slowest)
        }
        return Target(max, min, gov, gpuMax, gpuMin, limit)
    }

    // ---- heat guard ----

    /** Extra underclock steps the heat guard has taken (0 = none, at most 3: Heavy). */
    @Volatile private var heatStep = 0
    @Volatile private var heatGuardOn = false
    @Volatile private var heatGuardC = 85
    private var hotMs = 0L
    private var coolMs = 0L
    /** Time since the last step: the chip needs a while to cool after one, so steps are at least 30 s apart. */
    private var sinceStepMs = Long.MAX_VALUE / 2

    private fun heatReset() { heatStep = 0; hotMs = 0L; coolMs = 0L; sinceStepMs = Long.MAX_VALUE / 2 }

    /** The preset the heat guard holds the clocks to on top of [p]'s own underclock, or null. */
    private fun heatCeiling(p: TuneProfile): TuneProfile? {
        if (heatStep <= 0) return null
        val base = when (p.effectiveUnderclock) {
            Underclock.LIGHT -> 1; Underclock.MEDIUM -> 2; Underclock.HEAVY -> 3
            else -> 0 // Off, or Custom: its own speeds stay, the preset of the step goes over them
        }
        val presets = listOf(Underclock.LIGHT, Underclock.MEDIUM, Underclock.HEAVY)
        return TuneProfile(underclock = presets[(base + heatStep - 1).coerceIn(0, 2)])
    }

    /**
     * Called by the service with each temperature reading while a game runs. Over the limit for 8 s:
     * one step lower (Light → Medium → Heavy on top of the profile), at most one step per 30 s so the
     * chip has time to react. Cooled 8 °C below it for 45 s: one step back. Steps only while a game
     * runs; reset when it ends.
     */
    @Synchronized
    fun heatTick(tempC: Float?, dtMs: Long) {
        if (!heatGuardOn || currentKey == null || sleeping || tempC == null) return
        val limit = heatGuardC.toFloat()
        when {
            tempC >= limit -> { hotMs += dtMs; coolMs = 0L }
            tempC <= limit - 8f -> { coolMs += dtMs; hotMs = 0L }
            else -> { hotMs = 0L; coolMs = 0L }
        }
        sinceStepMs += dtMs
        val before = heatStep
        if (hotMs >= 8_000L && heatStep < 3 && sinceStepMs >= 30_000L) { heatStep++; hotMs = 0L }
        if (coolMs >= 45_000L && heatStep > 0) { heatStep--; coolMs = 0L }
        if (heatStep != before) {
            sinceStepMs = 0L
            write(compute(current, level))
            _status.value = _status.value.copy(heatStep = heatStep)
        }
    }

    // ---- measurements (what each profile costs, per game) ----

    private class MeasureRun(
        val key: String, var label: String, val startedAt: Long, val startPct: Int?, var onBattery: Boolean,
        /** Screen-off time (not play), and when the screen went off. */
        var pausedMs: Long = 0L, var pausedAt: Long = 0L,
        var wattSum: Double = 0.0, var wattN: Int = 0, var tempSum: Double = 0.0, var tempN: Int = 0,
        var fpsSum: Double = 0.0, var fpsN: Int = 0
    )
    @Volatile private var run: MeasureRun? = null
    @Volatile private var measuring = false

    private fun batteryNow(context: Context): Pair<Int?, Boolean> = runCatching {
        val b = context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = b?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = b?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (b?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        (if (l >= 0 && sc > 0) l * 100 / sc else null) to plugged
    }.getOrDefault(null to true)

    private fun measureStart(context: Context, key: String, p: TuneProfile) {
        val (pct, plugged) = batteryNow(context)
        run = MeasureRun(key, p.powerLabel(), android.os.SystemClock.elapsedRealtime(), pct, !plugged)
    }

    /** The profile changed mid-game: what was measured so far goes to the old profile, the rest to the new one. */
    private fun measureProfile(context: Context, p: TuneProfile) {
        val r = run ?: return
        if (r.label == p.powerLabel()) return
        measureEnd(context)
        measureStart(context, r.key, p)
    }

    /** Adds a reading from the service (watts only on battery: a charger makes the current meaningless). */
    fun measureSample(fps: Float?, powerW: Float?, tempC: Float?, onBattery: Boolean) {
        val r = run ?: return
        if (!onBattery) r.onBattery = false
        if (powerW != null && onBattery && powerW in 0.3f..60f) { r.wattSum += powerW; r.wattN++ }
        if (tempC != null) { r.tempSum += tempC; r.tempN++ }
        if (fps != null && fps > 1f) { r.fpsSum += fps; r.fpsN++ }
    }

    private fun measureEnd(context: Context) {
        val r = run ?: return
        run = null
        val now = android.os.SystemClock.elapsedRealtime()
        val paused = r.pausedMs + if (r.pausedAt > 0L) now - r.pausedAt else 0L
        val minutes = (now - r.startedAt - paused).coerceAtLeast(0L) / 60_000f
        if (minutes < 3f) return // backing straight out says nothing about a profile
        val (pct, plugged) = batteryNow(context)
        // Battery used with the screen off can't be told apart: a session that was mostly screen-off doesn't count it.
        val mostlyAwake = paused <= (now - r.startedAt) / 5
        val drained = if (mostlyAwake && r.onBattery && !plugged && r.startPct != null && pct != null && r.startPct >= pct) (r.startPct - pct).toFloat() else null
        TuneStore(context).addStat(r.key, r.label) { s ->
            s.copy(
                sessions = s.sessions + 1,
                minutes = s.minutes + minutes,
                drainPct = s.drainPct + (drained ?: 0f),
                drainMinutes = s.drainMinutes + (if (drained != null) minutes else 0f),
                wattSum = s.wattSum + r.wattSum, wattN = s.wattN + r.wattN,
                tempSum = s.tempSum + r.tempSum, tempN = s.tempN + r.tempN,
                fpsSum = s.fpsSum + r.fpsSum, fpsN = s.fpsN + r.fpsN,
                lastAt = System.currentTimeMillis()
            )
        }
    }

    /** Whether the service should read the battery current and temperature for the measurements / heat guard. */
    val wantsReadings: Boolean get() = currentKey != null && (measuring || heatGuardOn)
    val heatGuardActive: Boolean get() = currentKey != null && heatGuardOn

    // ---- underclock (ClusterTune / PULSE style ceilings) ----

    /** The underclock's top speed for cluster [index] of [count] (slowest first), kHz; null = no ceiling. */
    fun underclockKhz(p: TuneProfile, c: TuneHardware.Cluster, index: Int, count: Int): Int? {
        if (!p.underclocked) return null
        if (p.effectiveUnderclock == Underclock.CUSTOM) {
            val want = p.underclockKhz[c.name] ?: return null
            return if (want >= c.maxKhz) null else TuneHardware.snap(c, want)
        }
        val u = p.effectiveUnderclock
        val pct = when {
            count <= 1 -> u.big
            index == 0 -> u.little
            index == count - 1 && count >= 3 -> u.prime
            else -> u.big
        }
        return if (pct >= 100) null else TuneHardware.snap(c, c.maxKhz * pct / 100)
    }

    /** The underclock's GPU ceiling as a power level (0 = fastest); null = no ceiling. */
    fun underclockGpuLevel(p: TuneProfile, g: TuneHardware.Gpu): Int? {
        if (!p.underclocked) return null
        if (p.effectiveUnderclock == Underclock.CUSTOM) {
            val mhz = p.underclockGpuMhz.takeIf { it > 0 } ?: return null
            if (mhz >= (g.levelMhz.firstOrNull() ?: 0)) return null
            return g.levelMhz.indexOfFirst { it <= mhz }.let { if (it < 0) g.slowest else it }
        }
        return if (p.effectiveUnderclock.gpu >= 100) null else TuneHardware.gpuLevelFor(g, p.effectiveUnderclock.gpu)
    }

    /** "1.8 / 2.2 / 2.4 GHz · GPU 600 MHz": the underclock's real ceilings on this chip (blocking, cached reads). */
    fun underclockSummary(p: TuneProfile): String {
        if (!p.underclocked) return "Stock speeds"
        val clusters = TuneHardware.clusters()
        val cpu = clusters.mapIndexed { i, c ->
            val khz = underclockKhz(p, c, i, clusters.size) ?: c.maxKhz
            "%.1f".format(java.util.Locale.US, khz / 1_000_000f)
        }.joinToString(" / ")
        val gpu = TuneHardware.gpu()?.let { g -> underclockGpuLevel(p, g)?.let { g.levelMhz.getOrNull(it) } }
        return (if (cpu.isNotEmpty()) "$cpu GHz" else "") + (gpu?.let { " · GPU $it MHz" } ?: "")
    }

    /** "2.4 GHz": the highest ceiling the underclock sets right now (null when it is off or the chip can't be read). Blocking. */
    fun underclockTopGhz(p: TuneProfile): String? {
        if (!p.underclocked) return null
        val clusters = TuneHardware.clusters()
        val top = clusters.mapIndexedNotNull { i, c -> underclockKhz(p, c, i, clusters.size) ?: c.maxKhz }.maxOrNull() ?: return null
        return "%.1f GHz".format(java.util.Locale.US, top / 1_000_000f)
    }

    /** Whether msm_performance's [current] list still holds every "cpu:kHz" pair of [wanted] (kernels print all CPUs). */
    private fun msmHolds(current: String?, wanted: String): Boolean {
        val have = current?.split(Regex("\\s+"))?.toSet() ?: return true
        return wanted.split(' ').filter { it.isNotBlank() }.all { it in have }
    }

    /** "0:1200000 4:1800000 7:2000000" for msm_performance, from per-policy kHz. */
    private fun msmList(perPolicy: Map<String, Int>): String = TuneHardware.clusters().mapNotNull { c ->
        val cpu = TuneHardware.firstCpu(c) ?: return@mapNotNull null
        perPolicy[c.policy]?.let { "$cpu:$it" }
    }.joinToString(" ")

    private fun write(t: Target, remember: Boolean = true) {
        val writes = ArrayList<Pair<String, String>>()
        TuneHardware.clusters().forEach { c ->
            t.gov[c.policy]?.let { writes += "${c.policy}/scaling_governor" to it }
            // Max first (it may be going up), then the floor, then max again in case the floor clamped it.
            t.max[c.policy]?.let { writes += "${c.policy}/scaling_max_freq" to it.toString() }
            t.min[c.policy]?.let { writes += "${c.policy}/scaling_min_freq" to it.toString() }
            t.max[c.policy]?.let { writes += "${c.policy}/scaling_max_freq" to it.toString() }
        }
        TuneHardware.gpu()?.let { g ->
            if (t.gpuMax != null) {
                // Widen the floor first, or the kernel refuses a cap slower than it.
                writes += "${g.root}/min_pwrlevel" to g.slowest.toString()
                writes += "${g.root}/max_pwrlevel" to t.gpuMax.toString()
                writes += "${g.root}/min_pwrlevel" to (t.gpuMin ?: g.slowest).toString()
                // devfreq's own window: thermal and the perf HAL raise the GPU through it.
                if (TuneHardware.hasGpuDevfreq(g)) {
                    val s = stock
                    if (t.limit) {
                        writes += "${g.root}/devfreq/min_freq" to (TuneHardware.gpuHz(g, g.slowest) ?: 0L).toString()
                        TuneHardware.gpuHz(g, t.gpuMax)?.let { writes += "${g.root}/devfreq/max_freq" to it.toString() }
                        TuneHardware.gpuHz(g, t.gpuMin ?: g.slowest)?.let { writes += "${g.root}/devfreq/min_freq" to it.toString() }
                    } else {
                        s?.gpuMaxHz?.let { writes += "${g.root}/devfreq/max_freq" to it }
                        s?.gpuMinHz?.let { writes += "${g.root}/devfreq/min_freq" to it }
                    }
                }
            }
        }
        TuneHardware.overrides()?.let { o ->
            val s = stock
            if (t.limit) {
                o.msmMin?.let { writes += it to msmList(t.min) }
                o.msmMax?.let { writes += it to msmList(t.max) }
                o.boosts.keys.forEach { writes += it to "0" }
            } else {
                o.msmMax?.let { path -> (s?.msmMax ?: msmList(TuneHardware.clusters().associate { it.policy to it.maxKhz })).let { writes += path to it } }
                o.msmMin?.let { path -> s?.msmMin?.let { writes += path to it } }
                o.boosts.forEach { (path, value) -> writes += path to value }
            }
        }
        val ok = TuneHardware.writeAll(writes)
        if (remember) lastTarget = t
        _status.value = _status.value.copy(note = if (ok) null else "The root service didn't take the new clocks.")
    }

    private fun captureStock() {
        if (stock != null) return
        val clusters = TuneHardware.clusters()
        val g = TuneHardware.gpu()
        val lv = g?.let { TuneHardware.gpuLevels(it) }
        val o = TuneHardware.overrides()
        stock = Stock(
            max = clusters.associate { it.policy to (TuneHardware.currentMax(it) ?: it.maxKhz) },
            min = clusters.associate { it.policy to (TuneHardware.currentMin(it) ?: it.minKhz) },
            gov = clusters.mapNotNull { c -> TuneHardware.currentGovernor(c)?.let { c.policy to it } }.toMap(),
            gpuMax = lv?.first, gpuMin = lv?.second,
            fanMode = TuneHardware.fanMode(),
            msmMax = o?.msmMax?.let { TuneHardware.read(it) }?.takeIf { it.matches(Regex("[0-9: ]+")) },
            msmMin = o?.msmMin?.let { TuneHardware.read(it) }?.takeIf { it.matches(Regex("[0-9: ]+")) },
            gpuMaxHz = g?.let { TuneHardware.read("${it.root}/devfreq/max_freq") }?.takeIf { it.matches(Regex("[0-9]+")) },
            gpuMinHz = g?.let { TuneHardware.read("${it.root}/devfreq/min_freq") }?.takeIf { it.matches(Regex("[0-9]+")) }
        )
    }

    // ---- fan ----

    private var fanOwned = false
    /** The fan mode Voltcap itself wrote last: a change to anything else came from outside (Android's shade). */
    @Volatile private var lastFanWritten: Int? = null

    private fun setFan(mode: Int) {
        lastFanWritten = mode
        TuneHardware.setFanMode(mode)
    }

    /**
     * The handheld's fan setting changed (SystemSync saw it). When it wasn't Voltcap — the handheld's own
     * fan tile in Android's shade — the tuner takes it over instead of fighting it: with a fan mode in the
     * profile in charge, the device-wide profile's fan becomes the new mode (so Voltcap's screens show it),
     * and a game's own profile follows it until the game ends. With "System" nothing needs to change.
     * Blocking (one root read).
     */
    fun onSystemFanMode(context: Context) {
        if (!supported) return
        val mode = TuneHardware.fanMode() ?: return
        synchronized(this) {
            if (mode == lastFanWritten) return
            // "System" already means the handheld decides: keep it, and remember the new choice as the
            // one to put back after a profile with its own fan.
            if (current.fan == FanMode.SYSTEM) { stock = stock?.copy(fanMode = mode); return }
            val fm = FanMode.values().firstOrNull { it.vendorMode == mode && it != FanMode.CURVE && it != FanMode.HOLD_TEMP } ?: return
            lastFanWritten = mode
            fanOwned = true
            val store = TuneStore(context)
            if (currentSourceKey == null) {
                val g = store.global()
                if (g.fan != fm) store.setGlobal(g.copy(fan = fm))
            }
            current = current.copy(fan = fm)
            _status.value = _status.value.copy(profile = current)
        }
    }

    private fun applyFanMode(p: TuneProfile) {
        val mode = p.fan.vendorMode
        when {
            mode == null -> if (fanOwned) { stock?.fanMode?.let { setFan(it) }; fanOwned = false }
            p.fan == FanMode.CURVE || p.fan == FanMode.HOLD_TEMP -> if (TuneHardware.hasCustomFan()) {
                setFan(6); fanOwned = true
            }
            else -> { setFan(mode); fanOwned = true }
        }
    }

    /** Puts every clock back to how the device had it (Settings › Performance tuner › Restore). */
    @Synchronized
    fun restoreStock(context: Context) {
        val s = stock ?: return
        val t = Target(s.max, s.min, s.gov, s.gpuMax, s.gpuMin)
        write(t, remember = false)
        lastTarget = null
        s.fanMode?.let { setFan(it) }
        fanOwned = false
        current = TuneProfile()
        currentKey = null
        currentSourceKey = null
        level = 1f
        TuneService.stop(context)
        _status.value = _status.value.copy(source = "Device", profile = current, level = 1f)
    }

    // ---- quick controls (R1 tiles, Quick Settings tile) ----

    private val CYCLE = listOf(PowerTier.OFF, PowerTier.MAX, PowerTier.BALANCED, PowerTier.SAVER, PowerTier.ECO)

    /** Next device-wide tier; returns its label. Applies in the background. */
    fun cycleTier(context: Context): String {
        val store = TuneStore(context)
        val g = store.global()
        val next = CYCLE[(CYCLE.indexOf(g.tier).coerceAtLeast(0) + 1) % CYCLE.size]
        store.setGlobal(g.copy(tier = next, governor = next.governor))
        profileChanged(context, null)
        return next.label
    }

    /**
     * AutoTDP for the device-wide profile, stepping through every mode: Off → Efficient → Balanced →
     * Smooth → Off (it used to be on / off only, which always meant Balanced). Returns the new label.
     */
    fun cycleAutoTdp(context: Context): String {
        val store = TuneStore(context)
        val g = store.global()
        val modes = AutoTdpMode.values()
        val next = when {
            !g.autoTdp -> g.copy(autoTdp = true, autoMode = modes.first(), tier = if (g.tier == PowerTier.OFF) PowerTier.MAX else g.tier)
            g.autoMode == modes.last() -> g.copy(autoTdp = false)
            else -> g.copy(autoMode = modes[g.autoMode.ordinal + 1])
        }
        store.setGlobal(next)
        profileChanged(context, null)
        return if (next.autoTdp) "AutoTDP ${next.autoMode.label}" else "AutoTDP off"
    }

    /**
     * The Quick Settings tile's one cycle: the power tiers, then AutoTDP in each of its modes, then back
     * to Stock — so TDP is reachable from the tile without a notification.
     */
    fun cycleTile(context: Context): String {
        val store = TuneStore(context)
        val g = store.global()
        val modes = AutoTdpMode.values()
        val next = when {
            g.autoTdp && g.autoMode == modes.last() -> g.copy(autoTdp = false, tier = PowerTier.OFF, governor = PowerTier.OFF.governor)
            g.autoTdp -> g.copy(autoMode = modes[g.autoMode.ordinal + 1])
            g.tier == CYCLE.last() -> g.copy(autoTdp = true, autoMode = modes.first(), tier = PowerTier.MAX, governor = PowerTier.MAX.governor)
            else -> CYCLE[(CYCLE.indexOf(g.tier).coerceAtLeast(0) + 1) % CYCLE.size].let { t -> g.copy(tier = t, governor = t.governor) }
        }
        store.setGlobal(next)
        profileChanged(context, null)
        return if (next.autoTdp) "AutoTDP ${next.autoMode.label}" else next.tier.label
    }

    /**
     * The underclock tile (R1 and Android Quick Settings): Off → Light → Medium → Heavy → (Custom, once
     * you've set your own speeds) → Off, for the device-wide profile. Returns the new level.
     */
    fun cycleUnderclock(context: Context): Underclock {
        val store = TuneStore(context)
        val g = store.global()
        val order = buildList {
            add(Underclock.AUTO); add(Underclock.OFF); add(Underclock.LIGHT); add(Underclock.MEDIUM); add(Underclock.HEAVY)
            if (g.underclockKhz.isNotEmpty() || g.underclockGpuMhz > 0) add(Underclock.CUSTOM)
        }
        val next = order[(order.indexOf(g.underclock).coerceAtLeast(0) + 1) % order.size]
        store.setGlobal(g.copy(underclock = next))
        profileChanged(context, null)
        return next
    }

    /** AutoTDP on / off for the device-wide profile; returns the new state. */
    fun toggleAutoTdp(context: Context): Boolean {
        val store = TuneStore(context)
        val g = store.global()
        store.setGlobal(g.copy(autoTdp = !g.autoTdp))
        profileChanged(context, null)
        return !g.autoTdp
    }
}
