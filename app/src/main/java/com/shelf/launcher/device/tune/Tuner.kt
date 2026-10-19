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
    val heatStep: Int = 0,
    /**
     * Update 201: how often the handheld put its own clocks back over Voltcap's limits since they were set (AYN's
     * root service does it every 10 s while its performance mode is Standard / Performance / High), and Voltcap
     * wrote them again. Shown on the tuner page so the fight isn't hidden.
     */
    val resets: Int = 0,
    /** (Update 237, #85: the underclock AutoTDP has stepped to right now, or null when it isn't AutoTDP's to move.) */
    val autoUnderclock: Underclock? = null
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
        // (Update 237, #85: AutoTDP's underclock starts from what this game learned (read in apply → ucBegin).)
        ucStep = -1
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
        // (Update 237, #85: and the underclock step AutoTDP ended on, under "uc:<key>" in the same map.)
        if (ucStep >= 0) TuneStore(context).setLearnedLevel("uc:$key", ucStep.toFloat())
        ucStep = -1
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
        appCtx = context.applicationContext
        captureStock(context)
        current = p
        currentKey = key
        val store = TuneStore(context)
        ledStatus = store.ledStatus
        heatGuardOn = store.heatGuard
        heatGuardC = store.heatGuardC
        measuring = key != null && store.measure
        // Heat guard switched off, or no game: its extra steps end with it.
        if (!heatGuardOn || key == null) heatReset()
        // (Update 237, #85: AutoTDP's own underclock, when the profile leaves it to AutoTDP.)
        ucBegin(store, p, key)
        val t = compute(p, level)
        // Update 201: the count of the handheld's resets starts again with each profile.
        resets = 0
        lastResetAt = 0L
        write(t)
        _status.value = _status.value.copy(supported = true, source = source, profile = p, level = level, heatStep = heatStep, resets = 0, autoUnderclock = liveUcLabel())
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
        // (Update 237, #85: when a ceiling — your own underclock, or the one AutoTDP stepped to — already holds every
        // clock, a higher level changes nothing: it isn't raised, so AutoTDP doesn't wind up to 100 % against a manual
        // underclock (it respects it as the upper bound) and can come down at once when the game gets easier. A miss
        // then shows up as "no room" for autoUnderclockTick, which lifts its own ceiling instead.)
        if (l > level && !sleeping && compute(current, level) == compute(current, 1f)) return
        level = l
        if (!sleeping) write(compute(current, level))
        _status.value = _status.value.copy(level = level)
    }

    fun currentProfile(): TuneProfile = current

    // ---- AutoTDP's own underclock (Update 237, #85) ----

    /**
     * (Update 237, #85: with AutoTDP on and the underclock "With power", the underclock is AutoTDP's to move: [ucStep]
     * indexes [UC_LADDER] (0 = Off), within 0 .. the style's [AutoTdpMode.ucDeepest], starting at the style's own preset
     * (or what this game learned last time). -1 = not adaptive: the profile's own underclock, unchanged — a manual pick
     * (Off, Light…Custom) is never moved by AutoTDP and stays the upper bound for its level.)
     */
    @Volatile private var ucStep = -1
    @Volatile private var ucMode: AutoTdpMode? = null
    private val ucCtl = AutoUnderclock()
    private val UC_LADDER = listOf(Underclock.OFF, Underclock.LIGHT, Underclock.MEDIUM, Underclock.HEAVY)

    private fun adaptiveUc(p: TuneProfile): Boolean = p.autoTdp && p.underclock == Underclock.AUTO
    private fun ucRange(p: TuneProfile): IntRange = 0..UC_LADDER.indexOf(p.autoMode.ucDeepest).coerceAtLeast(0)
    private fun ucDefault(p: TuneProfile): Int = UC_LADDER.indexOf(p.effectiveUnderclock).coerceIn(ucRange(p))

    /** [p] with the preset AutoTDP has stepped to in place of "With power"; [p] itself otherwise. */
    private fun liveUc(p: TuneProfile, step: Int): TuneProfile =
        if (adaptiveUc(p) && step >= 0) p.copy(underclock = UC_LADDER[step.coerceIn(ucRange(p))]) else p

    private fun liveUcLabel(): Underclock? = if (ucStep >= 0 && adaptiveUc(current)) UC_LADDER[ucStep.coerceIn(ucRange(current))] else null

    /** Sets [ucStep] up for a newly applied profile: kept while AutoTDP's style stays, else its start (or the learned one). */
    private fun ucBegin(store: TuneStore, p: TuneProfile, key: String?) {
        if (!adaptiveUc(p)) { ucStep = -1; ucMode = null; return }
        if (ucStep >= 0 && ucMode == p.autoMode) { ucStep = ucStep.coerceIn(ucRange(p)); return }
        val learned = key?.let { store.learnedLevel("uc:$it") }?.toInt()
        ucStep = (learned ?: ucDefault(p)).coerceIn(ucRange(p))
        ucMode = p.autoMode
        ucCtl.reset()
    }

    /**
     * (Update 237, #85: called by the service after each AutoTDP tick. Steps AutoTDP's own underclock one preset
     * shallower when frames are missed (or the load is saturated) and the level has no room left, one deeper when the
     * target has held at low load and the deeper preset would really lower a clock; see [AutoUnderclock] for the
     * dwell times and hysteresis. Uses only what the sampler reads: frame rate, CPU / GPU busy %, power.)
     */
    @Synchronized
    fun autoUnderclockTick(fps: Float?, target: Int?, cpuPct: Int?, gpuPct: Int?, powerW: Float?, dtMs: Long) {
        val p = current
        if (!adaptiveUc(p) || ucStep < 0 || sleeping) return
        val now = compute(p, level)
        val levelHasRoom = compute(p, 1f) != now
        val range = ucRange(p)
        val deeperLowers = ucStep < range.last && computeAt(p, level, ucStep + 1) != now
        val over = powerW != null && powerW > p.autoMode.ceilingW
        val next = ucCtl.tick(ucStep, range, fps, target, cpuPct, gpuPct, over, levelHasRoom, deeperLowers, dtMs)
        if (next != ucStep) {
            ucStep = next
            write(compute(p, level))
            _status.value = _status.value.copy(autoUnderclock = liveUcLabel())
        }
    }

    fun updateLive(fps: Float?, target: Int?, powerW: Float?, tempC: Float?, fan: Int?) {
        _status.value = _status.value.copy(fps = fps, targetFps = target, powerW = powerW, tempC = tempC, fanPercent = fan)
    }

    /** Update 201: when the handheld last put its own clocks back over the limits (elapsedRealtime), 0 = not yet. */
    @Volatile private var lastResetAt = 0L
    @Volatile private var resets = 0

    /**
     * Update 201: the handheld is putting its clocks back over the limits right now (seen within the last 30 s).
     * AYN's root service ("pservice", read from its binary and seen in its log as "cpu_init boot_completed
     * cpugpumode=N" every 10.05 s on both the Odin 2 Portal and the Thor) writes, every 10 s while the screen is on:
     * full scaling_max_freq on all three clusters, its performance-mode floors in scaling_min_freq
     * (Performance: 1.23 / 2.05 / 2.48 GHz), GPU max_pwrlevel 0 and min_pwrlevel 2. The caps that msm_performance and
     * GPU devfreq hold survive that (they are separate limits the kernel takes the lowest of), but the floors and the
     * GPU's min level don't: with them back, the cores sit pinned at the cap and the GPU can't go below 550 MHz. While
     * this is true, [TuneService] looks every second ([quickCheck]) instead of every 4 s.
     */
    /** Update 201: a clock limit (not just a fan mode or Stock) is what's written right now. */
    val limitOn: Boolean get() = lastTarget?.limit == true && !sleeping

    fun vendorFighting(nowMs: Long = android.os.SystemClock.elapsedRealtime()): Boolean =
        lastResetAt > 0L && nowMs - lastResetAt < 30_000L && lastTarget?.limit == true && !sleeping

    /**
     * Update 201: one cheap root call (the fastest cluster's floor and top speed, and the GPU's min level): when the
     * handheld has just put its own values back, the full [reassert] runs at once.
     */
    fun quickCheck() {
        val t = lastTarget ?: return
        if (sleeping || !t.limit) return
        val c = TuneHardware.clusters().lastOrNull() ?: return
        val g = TuneHardware.gpu()
        val paths = listOfNotNull("${c.policy}/scaling_min_freq", "${c.policy}/scaling_max_freq", g?.let { "${it.root}/min_pwrlevel" })
        val v = TuneHardware.readMany(paths)
        val min = v.getOrNull(0)?.toIntOrNull()
        val max = v.getOrNull(1)?.toIntOrNull()
        val gpuMin = v.getOrNull(2)?.toIntOrNull()
        val undone = (min != null && t.min[c.policy]?.let { min > it } == true) ||
            (max != null && t.max[c.policy]?.let { max != it } == true) ||
            (gpuMin != null && t.gpuMin != null && gpuMin < t.gpuMin)
        if (undone) reassert()
    }

    /** The vendor's game daemon resets caps mid-game: read back and write again if it did. */
    @Synchronized
    fun reassert() {
        val t = lastTarget ?: return
        if (sleeping || !t.limit) return
        // Update 201: every value in a few root calls ([TuneHardware.readMany]) instead of one call per file (it was
        // 3 per cluster + 2 for the GPU + one per boost switch, every 4 s).
        val clusters = TuneHardware.clusters()
        val g = TuneHardware.gpu()
        val o = TuneHardware.overrides()
        val paths = ArrayList<String>()
        clusters.forEach { c -> paths += "${c.policy}/scaling_max_freq"; paths += "${c.policy}/scaling_min_freq"; paths += "${c.policy}/scaling_governor" }
        g?.let { paths += "${it.root}/max_pwrlevel"; paths += "${it.root}/min_pwrlevel" }
        o?.msmMax?.let { paths += it }
        o?.boosts?.keys?.forEach { paths += it }
        val read = TuneHardware.readMany(paths)
        val v = HashMap<String, String?>().apply { paths.forEachIndexed { i, p -> put(p, read.getOrNull(i)) } }
        val drifted = clusters.any { c ->
            val max = v["${c.policy}/scaling_max_freq"]?.toIntOrNull()
            val min = v["${c.policy}/scaling_min_freq"]?.toIntOrNull()
            val gov = v["${c.policy}/scaling_governor"]
            t.max[c.policy]?.let { max != null && it != max } == true ||
                // Update 200: AYN's root service writes its performance-mode floors (scaling_min_freq, GPU min_pwrlevel)
                // again every 10 s (seen on both the Thor and the Odin 2 Portal): those count as drift too.
                t.min[c.policy]?.let { want -> (min ?: want) > want } == true ||
                t.gov[c.policy]?.let { gov != null && it != gov } == true
        } || g?.let {
            val maxLv = v["${it.root}/max_pwrlevel"]?.toIntOrNull()
            val minLv = v["${it.root}/min_pwrlevel"]?.toIntOrNull()
            // A min level below the target's = a faster GPU floor (AYN's performance mode puts level 2 back).
            (t.gpuMax != null && maxLv != null && maxLv != t.gpuMax) || (t.gpuMin != null && minLv != null && minLv < t.gpuMin)
        } == true ||
            o?.let {
                // The perf HAL raised its own limit again, or switched a boost back on.
                (it.msmMax != null && !msmHolds(v[it.msmMax], msmList(t.max))) ||
                    it.boosts.keys.any { k -> v[k].let { b -> b != null && b != "0" } }
            } == true
        if (drifted) {
            lastResetAt = android.os.SystemClock.elapsedRealtime()
            resets++
            write(t)
            _status.value = _status.value.copy(resets = resets)
        }
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

    fun compute(profile: TuneProfile, lvl: Float): Target = computeAt(profile, lvl, ucStep)

    private fun computeAt(profile: TuneProfile, lvl: Float, step: Int): Target {
        // (Update 237, #85: with AutoTDP on and the underclock "With power", the preset AutoTDP has stepped to.)
        val adaptive = adaptiveUc(profile) && step >= 0
        val p = liveUc(profile, step)
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
                // Update 201: before this update AYN handhelds showed one cluster named "CPU" (really the little cores, see
                // TuneHardware.clusters): a cap saved under that name goes on to the little cluster.
                PowerTier.CUSTOM -> p.clusterCaps[c.name] ?: p.clusterCaps["CPU"].takeIf { i == 0 && clusters.size > 1 } ?: 100
                else -> p.tier.cpu
            }
            var pct = base * scale
            if (i == clusters.lastIndex && clusters.size > 1) pct = minOf(pct, p.primeBoost.toFloat())
            val stockMax = s?.max?.get(c.policy) ?: c.maxKhz
            var khz = if (pct >= 99.5f) stockMax else TuneHardware.snap(c, (c.maxKhz * pct / 100f).toInt())
            // Underclock: a hard ceiling on top of whatever the tier / AutoTDP chose; the heat guard's
            // extra steps on top of that while the chip runs hot.
            underclockKhz(p, c, i, clusters.size)?.let { uc ->
                // (Update 237, #85: AutoTDP's own underclock never goes below the profile's CPU floor.)
                val bounded = if (adaptive && p.cpuFloor > 0) maxOf(uc, TuneHardware.snap(c, (c.maxKhz * p.cpuFloor / 100f).toInt())) else uc
                khz = minOf(khz, bounded)
            }
            heatCeiling(p)?.let { h -> underclockKhz(h, c, i, clusters.size)?.let { khz = minOf(khz, it) } }
            max[c.policy] = khz
            // Update 200: with a limit the floor is the chip's own lowest speed, not the "stock" one. On AYN handhelds the
            // stock floor is the handheld's performance mode (system setting performance_mode, applied by AYN's root
            // service): on Performance it holds Little / Big / Prime at >= 1.23 / 2.05 / 2.48 GHz even in the menu, so
            // an underclock kept as floor what it was meant to lower, and the cores sat pinned at the cap.
            val floorKhz = if (p.cpuFloor > 0) TuneHardware.snap(c, (c.maxKhz * p.cpuFloor / 100f).toInt())
                else if (limit) c.minKhz else (s?.min?.get(c.policy) ?: c.minKhz)
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
            // (Update 237, #85: AutoTDP's own GPU underclock never goes below the profile's GPU floor; lower level = faster.)
            val ownUc = underclockGpuLevel(p, g)?.let { if (adaptive && p.gpuFloor > 0) minOf(it, TuneHardware.gpuLevelFor(g, p.gpuFloor)) else it }
            val ucLevel = listOfNotNull(ownUc, heatCeiling(p)?.let { underclockGpuLevel(it, g) }).maxOrNull()
            gpuMax = if (ucLevel != null) maxOf(tierMax, ucLevel) else tierMax
            // Update 200: same for the GPU — AYN's performance mode sets min_pwrlevel 2 (550 MHz) as its floor; with a limit
            // the floor is the slowest level, and never faster than the cap (a min level below the max level is invalid).
            gpuMin = if (p.gpuFloor > 0) maxOf(TuneHardware.gpuLevelFor(g, p.gpuFloor), gpuMax)
                else if (limit) g.slowest else maxOf(s?.gpuMin ?: g.slowest, gpuMax)
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
            // Update 201: a speed saved under the old single "CPU" cluster (the little cores, see TuneHardware.clusters).
            val want = p.underclockKhz[c.name] ?: p.underclockKhz["CPU"].takeIf { index == 0 && count > 1 } ?: return null
            return if (want >= c.maxKhz) null else TuneHardware.snap(c, want)
        }
        val u = p.effectiveUnderclock
        val pct = when {
            count <= 1 -> u.big
            index == 0 -> u.little
            index == count - 1 && count >= 3 -> u.prime
            else -> u.big
        }
        // (Update 237, #86: resolved against this cluster's own frequency table, see TuneHardware.presetKhz.)
        return TuneHardware.presetKhz(c, pct)
    }

    /** The underclock's GPU ceiling as a power level (0 = fastest); null = no ceiling. */
    fun underclockGpuLevel(p: TuneProfile, g: TuneHardware.Gpu): Int? {
        if (!p.underclocked) return null
        if (p.effectiveUnderclock == Underclock.CUSTOM) {
            val mhz = p.underclockGpuMhz.takeIf { it > 0 } ?: return null
            if (mhz >= (g.levelMhz.firstOrNull() ?: 0)) return null
            return g.levelMhz.indexOfFirst { it <= mhz }.let { if (it < 0) g.slowest else it }
        }
        // (Update 237, #86: same rule for the GPU's level table, see TuneHardware.presetGpuLevel.)
        return TuneHardware.presetGpuLevel(g, p.effectiveUnderclock.gpu)
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

    /**
     * "0:1200000 1:1200000 2:1200000 3:1800000 …" for msm_performance, from per-policy kHz.
     * Update 201: every CPU of a cluster, not just its first: msm_performance keeps one limit per CPU and the kernel
     * takes the highest floor / lowest cap of them, so a floor the perf HAL left on CPUs 1 and 2 (1.46 GHz on the Odin 2
     * Portal) kept holding the whole little cluster up when only CPU 0's was lowered.
     */
    private fun msmList(perPolicy: Map<String, Int>): String = TuneHardware.clusters().flatMap { c ->
        val khz = perPolicy[c.policy] ?: return@flatMap emptyList<String>()
        TuneHardware.cpusOf(c).map { cpu -> "$cpu:$khz" }
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
            if (t.limit) {
                // Update 201: what the perf HAL / game service had there is kept right before Voltcap's first limit and
                // put back after the last one. (It used to put back what it read when the tuner first started, which
                // can be a stale boost: the Odin 2 Portal's game service had /proc/sys/walt/sched_boost at 1 —
                // "full throttle" — after a game, and the msm_performance floors held the little cores at 1.46 GHz.
                // Those writes never ran before update 201, see TuneHardware.writeAll, so it didn't show.)
                if (vendorSaved == null) {
                    val paths = listOfNotNull(o.msmMax, o.msmMin) + o.boosts.keys
                    val now = TuneHardware.readMany(paths)
                    vendorSaved = paths.indices.mapNotNull { i ->
                        now.getOrNull(i)?.takeIf { v -> v.matches(Regex("[0-9: ]+")) || v.matches(Regex("-?[0-9]+")) }?.let { paths[i] to it }
                    }.toMap()
                    // (Update 255: kept with the stock clocks, for a Voltcap that restarts while its limits are on.)
                    appCtx?.let { c -> runCatching {
                        c.getSharedPreferences(STOCK_PREFS, Context.MODE_PRIVATE).edit()
                            .putString("vendor", org.json.JSONObject().also { o -> vendorSaved?.forEach { (k, v) -> o.put(k, v) } }.toString()).apply()
                    } }
                }
                o.msmMin?.let { writes += it to msmList(t.min) }
                o.msmMax?.let { writes += it to msmList(t.max) }
                o.boosts.keys.forEach { writes += it to "0" }
            } else {
                vendorSaved?.forEach { (path, value) -> writes += path to value }
                vendorSaved = null
            }
        }
        val ok = TuneHardware.writeAll(writes)
        if (ok) markDirty(t.limit)
        if (remember) lastTarget = t
        // Update 200: "the command ran" isn't "the caps hold". On the Thor the notification said "Underclock Medium"
        // while every core could still reach its full speed, so with a limit the top speeds are read back and a cap
        // that didn't take is said on the tuner page instead of being assumed.
        // Update 201: the GPU's cap is read back too (it was never written on AYN handhelds and nothing said so), all in
        // one or two root calls.
        val clusters = TuneHardware.clusters()
        val g = TuneHardware.gpu()
        val back = if (ok && t.limit) TuneHardware.readMany(clusters.map { "${it.policy}/scaling_max_freq" } + listOfNotNull(g?.let { "${it.root}/max_pwrlevel" }))
            else emptyList()
        val missed = if (ok && t.limit) clusters.filterIndexed { i, c ->
            t.max[c.policy]?.let { want -> back.getOrNull(i)?.toIntOrNull()?.let { it > want } } == true
        }.map { it.name } + listOfNotNull(
            "GPU".takeIf { t.gpuMax != null && back.getOrNull(clusters.size)?.toIntOrNull()?.let { it < t.gpuMax } == true }
        ) else emptyList()
        _status.value = _status.value.copy(
            note = when {
                !ok -> "The root service didn't take the new clocks."
                missed.isNotEmpty() -> "The handheld didn't keep the top speed for " + missed.joinToString() +
                    " (its own performance mode or game service sets clocks too). Voltcap writes it again within seconds."
                else -> null
            }
        )
    }

    /** Update 201: msm_performance / boost values from right before Voltcap's first limit, put back after the last one. */
    @Volatile private var vendorSaved: Map<String, String>? = null

    /**
     * Update 254 (#96 / #104): the stock clocks are kept on disk with the boot they were read in, and whether Voltcap's own
     * limits were on the chip ("dirty"). A Voltcap that starts again in the same boot while its limits are still written
     * (an app update, Android ending the process, a crash) takes the kept stock instead of reading its own caps back as
     * the device's. Before, that's what happened: on the RP Classic (whose own software never puts the clocks back) the
     * Balanced caps — 1.29 / 1.65 GHz, GPU 605 MHz — were read as "stock" after a restart and from then on Stock and even
     * Max stayed at them until the handheld was restarted.
     */
    @Volatile private var appCtx: Context? = null
    @Volatile private var dirtyWritten: Boolean? = null
    private const val STOCK_PREFS = "tuner_stock"

    /** When this boot started (wall clock, ±1 s): a different value means the handheld restarted since. */
    private fun bootStamp(): Long = (System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()) / 1000L

    private fun sameBoot(saved: Long): Boolean = kotlin.math.abs(saved - bootStamp()) <= 60L

    private fun captureStock(context: Context) {
        if (stock != null) return
        val prefs = context.applicationContext.getSharedPreferences(STOCK_PREFS, Context.MODE_PRIVATE)
        val kept = runCatching {
            if (prefs.getBoolean("dirty", false) && sameBoot(prefs.getLong("boot", 0L)))
                prefs.getString("stock", null)?.let { stockFromJson(it) }
            else null
        }.getOrNull()
        if (kept != null && kept.max.isNotEmpty()) {
            stock = kept
            dirtyWritten = true
            // (The vendor values under Voltcap's limits too — msm_performance's caps, the boosts: read now they'd be
            // Voltcap's own Balanced caps, and Stock put those back. Seen on the RP Classic in testing.)
            vendorSaved = runCatching {
                prefs.getString("vendor", null)?.let { t -> org.json.JSONObject(t).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }
            }.getOrNull()
            return
        }
        readStock()
        runCatching {
            prefs.edit().putLong("boot", bootStamp()).putString("stock", stockToJson(stock!!)).putBoolean("dirty", false).apply()
            dirtyWritten = false
        }
    }

    // (Written by hand, not with Gson: the release build renames fields, and an update would read an older file wrong.)
    private fun stockToJson(s: Stock): String = org.json.JSONObject().apply {
        fun ints(m: Map<String, Int>) = org.json.JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, v) } }
        put("max", ints(s.max)); put("min", ints(s.min))
        put("gov", org.json.JSONObject().also { o -> s.gov.forEach { (k, v) -> o.put(k, v) } })
        s.gpuMax?.let { put("gpuMax", it) }; s.gpuMin?.let { put("gpuMin", it) }; s.fanMode?.let { put("fanMode", it) }
        s.msmMax?.let { put("msmMax", it) }; s.msmMin?.let { put("msmMin", it) }
        s.gpuMaxHz?.let { put("gpuMaxHz", it) }; s.gpuMinHz?.let { put("gpuMinHz", it) }
    }.toString()

    private fun stockFromJson(text: String): Stock {
        val o = org.json.JSONObject(text)
        fun ints(k: String): Map<String, Int> = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.getInt(it) } } ?: emptyMap()
        fun str(k: String): String? = if (o.has(k)) o.getString(k) else null
        fun int(k: String): Int? = if (o.has(k)) o.getInt(k) else null
        val gov = o.optJSONObject("gov")?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
        return Stock(ints("max"), ints("min"), gov, int("gpuMax"), int("gpuMin"), int("fanMode"), str("msmMax"), str("msmMin"), str("gpuMaxHz"), str("gpuMinHz"))
    }

    /** Whether Voltcap's own limits are on the chip now (kept for [captureStock]); written only when it changes. */
    private fun markDirty(on: Boolean) {
        if (dirtyWritten == on) return
        val c = appCtx ?: return
        runCatching {
            c.getSharedPreferences(STOCK_PREFS, Context.MODE_PRIVATE).edit().putBoolean("dirty", on).putLong("boot", bootStamp()).apply()
            dirtyWritten = on
        }
    }

    private fun readStock() {
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
        ucStep = -1; ucMode = null
        TuneService.stop(context)
        _status.value = _status.value.copy(source = "Device", profile = current, level = 1f, autoUnderclock = null)
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
