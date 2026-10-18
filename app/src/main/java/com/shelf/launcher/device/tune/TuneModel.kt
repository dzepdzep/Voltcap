package com.shelf.launcher.device.tune

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Ready-made power envelopes. A Snapdragon handheld has no settable wattage limit, so a tier is a
 * share of each CPU cluster's and the GPU's real top speed (snapped to speeds they really have),
 * plus the governor that suits it. CUSTOM uses your own per-cluster / GPU values.
 */
enum class PowerTier(val label: String, val blurb: String, val cpu: Int, val gpu: Int, val governor: GovernorChoice) {
    OFF("Stock", "The device's own clocks", 100, 100, GovernorChoice.SYSTEM),
    MAX("Max", "Full speed", 100, 100, GovernorChoice.PERFORMANCE),
    BALANCED("Balanced", "Cooler, longer sessions", 75, 65, GovernorChoice.BALANCED),
    SAVER("Power saving", "Long battery life, most 3D up to PS2 / GameCube", 55, 45, GovernorChoice.POWERSAVE),
    ECO("Eco", "Lightest: 2D, handhelds and PS1-era systems", 40, 30, GovernorChoice.POWERSAVE),
    CUSTOM("Custom", "Your own limits", 100, 100, GovernorChoice.SYSTEM)
}

/**
 * Underclock, the way ClusterTune and PULSE do it: a hard ceiling on each CPU cluster's top speed (and the
 * GPU's), whatever the power tier, governor or AutoTDP does underneath. Percentages are of each part's own
 * top speed, snapped to a speed it really has; the slower clusters are trimmed least, the prime core most
 * (it's where the heat comes from). CUSTOM uses exact speeds per cluster ([TuneProfile.underclockKhz]).
 *
 * (Update 237, #86: Light was too aggressive — it took the prime core to 80 %, which on a coarse frequency table snapped
 * down to ~75 %. Interim rule, pending on-device measurement: every preset is resolved against each part's own available
 * frequency table ([TuneHardware.presetKhz] / [TuneHardware.presetGpuLevel]): the highest real step within
 * [TuneHardware.PRESET_TOLERANCE] points below the preset's percentage, else the step nearest to it — which can be the
 * top speed itself, i.e. no ceiling for that part. Light now = big and prime at 85–90 % of their own top speed (only the
 * top boost steps go), little cores untouched, GPU at 85–90 %. Medium / Heavy keep their percentages; on fine tables they
 * resolve exactly as before. The enum names are unchanged, so saved choices stay valid.)
 */
enum class Underclock(
    val label: String, val blurb: String,
    val little: Int, val big: Int, val prime: Int, val gpu: Int
) {
    /** Set by the power tier (or AutoTDP's style): Stock / Max off, Balanced light, Power saving medium, Eco heavy. */
    // (Update 237, #85: with AutoTDP on, "With power" is moved by AutoTDP itself between Off and its style's deepest preset.)
    AUTO("With power", "Follows the power tier (Max and Stock none, Balanced light, Power saving medium, Eco heavy), or with AutoTDP on, AutoTDP moves it with the game", 100, 100, 100, 100),
    OFF("Off", "Every core and the GPU can reach their own top speed", 100, 100, 100, 100),
    // (Update 237, #86: was 100 / 90 / 80 / 90 — the prime core now loses only its top boost steps, like big.)
    LIGHT("Light", "Only the top boost steps of the big and prime cores and the GPU: cooler, feels the same", 100, 90, 90, 90),
    MEDIUM("Medium", "Big and prime cores lower: most emulation up to PS2 / GameCube", 90, 80, 65, 80),
    HEAVY("Heavy", "Everything lower: 2D, handhelds and PS1-era systems, longest battery", 80, 65, 50, 65),
    CUSTOM("Custom", "Your own top speed per cluster", 100, 100, 100, 100)
}

enum class GovernorChoice(val label: String) {
    SYSTEM("System"), PERFORMANCE("Performance"), BALANCED("Balanced"), POWERSAVE("Power save")
}

/**
 * AutoTDP styles: the wattage it stays under while holding the target frame rate.
 * (Update 237, #85: [ucDeepest] = the deepest underclock AutoTDP may step to by itself when the underclock is
 * "With power"; it starts at the style's own preset ([TuneProfile.effectiveUnderclock]) and never goes past Off.)
 */
enum class AutoTdpMode(val label: String, val ceilingW: Float, val blurb: String, val ucDeepest: Underclock) {
    EFFICIENT("Efficient", 7f, "Lowest power, quietest", Underclock.HEAVY),
    BALANCED("Balanced", 9f, "Middle ground", Underclock.MEDIUM),
    SMOOTH("Smooth", 12f, "Steadiest frame rate", Underclock.LIGHT)
}

enum class FanMode(val label: String, val vendorMode: Int?) {
    SYSTEM("System", null),
    QUIET("Quiet", 1),
    SMART("Smart", 4),
    SPORT("Sport", 5),
    /** Voltcap drives the fan from a temperature → speed curve. */
    CURVE("Curve", 6),
    /** Voltcap holds the chip at a temperature with as little fan as it needs. */
    HOLD_TEMP("Hold temp", 6)
}

/** Stick lights as a status light while the tuner runs (games included). */
enum class LedStatus(val label: String) { OFF("Off"), BATTERY("Battery"), TEMPERATURE("Temperature") }

data class FanPoint(val tempC: Int, val percent: Int)

val DEFAULT_FAN_CURVE = listOf(FanPoint(45, 25), FanPoint(55, 40), FanPoint(65, 60), FanPoint(75, 85), FanPoint(82, 100))

/** Everything the tuner can set, for the whole device or for one game / app. */
data class TuneProfile(
    val tier: PowerTier = PowerTier.OFF,
    /** One master slider (30–100 %) that scales every cluster and the GPU together. */
    val powerTarget: Int = 100,
    /** The master slider leaves the GPU alone. */
    val cpuOnly: Boolean = false,
    /** CUSTOM: cap per cluster name ("Little", "Big", "Prime"…), % of its top speed. */
    val clusterCaps: Map<String, Int> = emptyMap(),
    /** Extra limit on the fastest (prime) cluster's boost, % of its top speed. */
    val primeBoost: Int = 100,
    /** CPU floor: every cluster held at least this % of its top speed (0 = off). */
    val cpuFloor: Int = 0,
    /** CUSTOM: GPU cap, % of its top speed. */
    val gpuCap: Int = 100,
    /** GPU floor, % of its top speed (0 = off). */
    val gpuFloor: Int = 0,
    val governor: GovernorChoice = GovernorChoice.SYSTEM,
    val autoTdp: Boolean = false,
    /** 0 = work it out from the game itself. */
    val targetFps: Int = 0,
    val autoMode: AutoTdpMode = AutoTdpMode.BALANCED,
    val fan: FanMode = FanMode.SYSTEM,
    val fanCurve: List<FanPoint> = DEFAULT_FAN_CURVE,
    val holdTempC: Int = 70,
    /** Per-game extras, set at launch and put back afterwards. 0 = leave the screen's rate alone. */
    val refreshHz: Int = 0,
    /** Hard top-speed ceiling per cluster / GPU, like ClusterTune / PULSE ([Underclock]). */
    val underclock: Underclock = Underclock.AUTO,
    /** CUSTOM underclock: top speed per cluster name ("Little", "Big", "Prime"…), kHz. */
    val underclockKhz: Map<String, Int> = emptyMap(),
    /** CUSTOM underclock: GPU top speed, MHz (0 = not capped). */
    val underclockGpuMhz: Int = 0,
    /** "XBOX" / "NINTENDO" button layout (AYN), null = leave it. */
    val buttonLayout: String? = null,
    /** 80 % charge limit on / off while this game runs, null = leave it. */
    val chargeLimit: Boolean? = null
) {
    /** Changes anything at all. */
    val active: Boolean get() = tier != PowerTier.OFF || powerTarget < 100 || autoTdp || fan != FanMode.SYSTEM ||
        cpuFloor > 0 || gpuFloor > 0 || primeBoost < 100 || governor != GovernorChoice.SYSTEM || underclocked
    /** An underclock that really lowers something (CUSTOM with no speeds set doesn't). */
    val underclocked: Boolean get() = effectiveUnderclock != Underclock.OFF &&
        (effectiveUnderclock != Underclock.CUSTOM || underclockKhz.isNotEmpty() || underclockGpuMhz > 0)
    /**
     * The underclock really used: your own pick, or with "With power" the one that goes with the power tier — or,
     * with AutoTDP on, with its style (Efficient medium, Balanced light, Smooth none).
     */
    val effectiveUnderclock: Underclock get() = if (underclock != Underclock.AUTO) underclock else when {
        autoTdp -> when (autoMode) {
            AutoTdpMode.EFFICIENT -> Underclock.MEDIUM
            AutoTdpMode.BALANCED -> Underclock.LIGHT
            AutoTdpMode.SMOOTH -> Underclock.OFF
        }
        else -> when (tier) {
            PowerTier.BALANCED -> Underclock.LIGHT
            PowerTier.SAVER -> Underclock.MEDIUM
            PowerTier.ECO -> Underclock.HEAVY
            else -> Underclock.OFF
        }
    }
    /** Needs the background loop (re-asserting, AutoTDP, fan control). */
    val needsLoop: Boolean get() = autoTdp || fan == FanMode.CURVE || fan == FanMode.HOLD_TEMP

    /**
     * What decides power use, as one line ("Balanced · AutoTDP Smooth · Underclock Medium · Fan Quiet · 60 Hz"):
     * the label the per-game measurements are grouped under.
     */
    fun powerLabel(): String = listOfNotNull(
        tier.label,
        "AutoTDP ${autoMode.label}".takeIf { autoTdp },
        "Underclock ${effectiveUnderclock.label}".takeIf { underclocked },
        "Fan ${fan.label}".takeIf { fan != FanMode.SYSTEM },
        "$refreshHz Hz".takeIf { refreshHz > 0 }
    ).joinToString(" · ")
}

/**
 * What one profile cost while playing one game, added up over sessions (Settings › game › Measured).
 * Battery drain is only counted for sessions on battery; watts, temperature and frame rate from the
 * tuner's readings while the game ran.
 */
data class ProfileStat(
    val sessions: Int = 0,
    val minutes: Float = 0f,
    /** Battery % used, and the minutes it was measured over (on battery only). */
    val drainPct: Float = 0f,
    val drainMinutes: Float = 0f,
    val wattSum: Double = 0.0, val wattN: Int = 0,
    val tempSum: Double = 0.0, val tempN: Int = 0,
    val fpsSum: Double = 0.0, val fpsN: Int = 0,
    val lastAt: Long = 0L
) {
    val avgW: Float? get() = if (wattN > 0) (wattSum / wattN).toFloat() else null
    val avgTemp: Float? get() = if (tempN > 0) (tempSum / tempN).toFloat() else null
    val avgFps: Float? get() = if (fpsN > 0) (fpsSum / fpsN).toFloat() else null
    /** Battery % per hour, when at least 10 minutes on battery were measured. */
    val drainPerHour: Float? get() = if (drainMinutes >= 10f) drainPct * 60f / drainMinutes else null
}

/**
 * Prefs "tuner" (DEVICE scope: clocks only make sense on the handheld they were set on).
 * The device-wide profile, per-game / per-app profiles (key = game stableKey, "pkg:<package>", or
 * "plat:<platform>" for a console's own profile),
 * AutoTDP's learned level per game, and the automation switches.
 */
class TuneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, TuneProfile>>() {}.type
    private val levelType = object : TypeToken<Map<String, Float>>() {}.type

    init { migrateUnderclockOnce() }

    companion object {
        /** Update 213: counts saved changes (any screen), so what shows a profile — the game popup — reads it again. */
        val edits = androidx.compose.runtime.mutableIntStateOf(0)
    }

    /**
     * Once: profiles saved with the underclock "Off" (the old default) follow the power tier from now on ("With
     * power") — a tier of Stock / Max still means no underclock. A level you picked (Light…Custom) stays.
     */
    private fun migrateUnderclockOnce() {
        if (prefs.getBoolean("uc_with_power", false)) return
        val e = prefs.edit().putBoolean("uc_with_power", true)
        fun fix(json: String?): String? = json?.replace("\"underclock\":\"OFF\"", "\"underclock\":\"AUTO\"")
        prefs.getString("global", null)?.let { e.putString("global", fix(it)) }
        prefs.getString("per_game", null)?.let { e.putString("per_game", fix(it)) }
        e.apply()
    }

    fun global(): TuneProfile = runCatching { gson.fromJson(prefs.getString("global", null), TuneProfile::class.java) }.getOrNull()?.sane() ?: TuneProfile()
    fun setGlobal(p: TuneProfile) { prefs.edit().putString("global", gson.toJson(p)).apply(); edits.intValue++ }

    fun perGame(): Map<String, TuneProfile> =
        runCatching { gson.fromJson<Map<String, TuneProfile>>(prefs.getString("per_game", null), mapType) }.getOrNull()
            ?.mapValues { it.value.sane() }.orEmpty()

    fun forKey(key: String): TuneProfile? = perGame()[key]

    fun setForKey(key: String, p: TuneProfile?) {
        val m = perGame().toMutableMap()
        if (p == null) m.remove(key) else m[key] = p
        prefs.edit().putString("per_game", gson.toJson(m)).apply()
        edits.intValue++
    }

    fun learnedLevel(key: String): Float? =
        runCatching { gson.fromJson<Map<String, Float>>(prefs.getString("learned", null), levelType) }.getOrNull()?.get(key)

    fun setLearnedLevel(key: String, level: Float) {
        val m = runCatching { gson.fromJson<Map<String, Float>>(prefs.getString("learned", null), levelType) }.getOrNull().orEmpty().toMutableMap()
        m[key] = level
        prefs.edit().putString("learned", gson.toJson(m)).apply()
    }

    var sleepProfile: Boolean
        get() = prefs.getBoolean("sleep_profile", false)
        set(v) { prefs.edit().putBoolean("sleep_profile", v).apply() }

    var ledStatus: LedStatus
        get() = runCatching { LedStatus.valueOf(prefs.getString("led_status", null).orEmpty()) }.getOrDefault(LedStatus.OFF)
        set(v) { prefs.edit().putString("led_status", v.name).apply() }

    /** Games without their own profile use their console's profile ("plat:<platform>") when there is one. */
    var consoleProfiles: Boolean
        get() = prefs.getBoolean("console_profiles", true)
        set(v) { prefs.edit().putBoolean("console_profiles", v).apply() }

    /** Heat guard: steps the underclock down while a game runs hot, and back up once it cools. */
    var heatGuard: Boolean
        get() = prefs.getBoolean("heat_guard", false)
        set(v) { prefs.edit().putBoolean("heat_guard", v).apply() }

    var heatGuardC: Int
        get() = prefs.getInt("heat_guard_c", 85).coerceIn(60, 95)
        set(v) { prefs.edit().putInt("heat_guard_c", v.coerceIn(60, 95)).apply() }

    /** Records what each profile costs per game (battery, watts, temperature, frame rate). */
    var measure: Boolean
        get() = prefs.getBoolean("measure", true)
        set(v) { prefs.edit().putBoolean("measure", v).apply() }

    private val statType = object : TypeToken<Map<String, Map<String, ProfileStat>>>() {}.type

    private fun allStats(): Map<String, Map<String, ProfileStat>> =
        runCatching { gson.fromJson<Map<String, Map<String, ProfileStat>>>(prefs.getString("measured", null), statType) }
            .getOrNull().orEmpty()

    /** Per profile label, what it cost for this game, most used first. */
    fun stats(key: String): List<Pair<String, ProfileStat>> =
        allStats()[key].orEmpty().entries.map { it.key to it.value }.sortedByDescending { it.second.minutes }

    @Synchronized
    fun addStat(key: String, label: String, add: (ProfileStat) -> ProfileStat) {
        val all = allStats().toMutableMap()
        val forGame = all[key].orEmpty().toMutableMap()
        forGame[label] = add(forGame[label] ?: ProfileStat())
        // Ten labels per game at most (the longest unused go): settings tried once don't pile up.
        all[key] = forGame.entries.sortedByDescending { it.value.lastAt }.take(10).associate { it.key to it.value }
        prefs.edit().putString("measured", gson.toJson(all)).apply()
    }

    fun clearStats(key: String) {
        val all = allStats().toMutableMap()
        if (all.remove(key) != null) prefs.edit().putString("measured", gson.toJson(all)).apply()
    }

    /** One profile written to many games at once (Copy to all of a console's games). */
    fun setForKeys(keys: Collection<String>, p: TuneProfile) {
        val m = perGame().toMutableMap()
        keys.forEach { m[it] = p }
        prefs.edit().putString("per_game", gson.toJson(m)).apply()
        edits.intValue++
    }

    var applyOnBoot: Boolean
        get() = prefs.getBoolean("apply_on_boot", true)
        set(v) { prefs.edit().putBoolean("apply_on_boot", v).apply() }

    /** Gson leaves missing (newer) fields null even for non-null types: fill them with defaults. */
    @Suppress("SENSELESS_COMPARISON")
    private fun TuneProfile.sane(): TuneProfile {
        val d = TuneProfile()
        return copy(
            tier = tier ?: d.tier,
            clusterCaps = clusterCaps ?: d.clusterCaps,
            governor = governor ?: d.governor,
            autoMode = autoMode ?: d.autoMode,
            fan = fan ?: d.fan,
            fanCurve = fanCurve?.takeIf { it.size >= 2 } ?: d.fanCurve,
            underclock = underclock ?: d.underclock,
            underclockKhz = underclockKhz ?: d.underclockKhz
        )
    }
}
