package com.shelf.launcher.device

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Display

/**
 * The handheld's own switches in the R1 quick menu. On AYN firmware they go through its root command
 * service (the same settings AYN's own menu writes — keys documented by the open-source OdinTools);
 * elsewhere a control is either done the standard Android way or opens Android's own screen for it.
 * Every call that touches the root service blocks briefly: call [read]/[next] off the main thread.
 */
enum class DeviceControl(val label: String) {
    CHARGE_LIMIT("80% charge limit"),
    BYPASS_CHARGING("Charge bypass"),
    AIRPLANE("Airplane mode"),
    PERFORMANCE("Performance"),
    FAN("Fan"),
    REFRESH_RATE("Refresh rate"),
    CONTROLLER_STYLE("Controller style"),
    FLOATING_BUTTON("Floating button"),
    /** ClusterTune / PULSE, when installed: opens its quick controls. */
    TUNER_APP("CPU tuner"),
    /** Voltcap's own power tier (Stock / Max / Balanced / Power saving) — the R1 underclock tile. */
    CPU_CAP("Power tier"),
    /** AutoTDP on / off for the device-wide tuner profile. */
    AUTO_TDP("AutoTDP"),
    /** Underclock, ClusterTune / PULSE style: Off → Light → Medium → Heavy (→ Custom) ceilings per cluster. */
    UNDERCLOCK("Underclock")
}

/** What a tile shows: on/off look plus a short value ("Smart", "120 Hz"); null value = plain toggle. */
data class ControlState(val on: Boolean, val value: String? = null, val supported: Boolean = true)

object DeviceControls {
    private const val PERF = "performance_mode"            // 0 standard, 1 performance, 2 high performance
    private const val FAN = "fan_mode"                     // 0 off, 1 quiet, 4 smart, 5 sport (6 custom)
    private const val SEPARATION = "is_charging_separation"
    private const val RESTRICT_CHG = "/sys/class/qcom-battery/restrict_chg"
    private const val RESTRICT_CUR = "/sys/class/qcom-battery/restrict_cur"
    private const val ABXY_TEMP = "temp_abxy_layout_mode"
    private const val NO_GAMEPAD_LAYOUT = "no_create_gamepad_button_layout"
    private const val FLIP_LAYOUT = "flip_button_layout"
    /** 0.6.18: what the handheld's own Controller style (Settings › Handheld Settings › Input, and its Quick Settings tile)
     *  also sets — 1 Xbox, 0 Retro — seen on the Odin 2 Portal and the Retroid Pocket Flip 2 (the same firmware family). */
    private const val GAMEPAD_TYPE_PROP = "persist.sys.gamepad.type"

    private val perfModes = listOf(0 to "Standard", 1 to "Performance", 2 to "High")
    private val fanModes = listOf(1 to "Quiet", 4 to "Smart", 5 to "Sport", 0 to "Off")

    /** The floating-button setting's name differs between firmwares: found once by looking at the settings list. */
    @Volatile private var floatingKey: String? = null
    @Volatile private var floatingSearched = false

    private fun isAyn(): Boolean = BuildFingerprint.current().let {
        it.manufacturer.equals("AYN", true) || it.brand.equals("AYN", true)
    }

    /** Which controls this device can really do (blocking: may probe the root service once). */
    fun available(context: Context): List<DeviceControl> {
        val root = RootShell.available && isAyn()
        return DeviceControl.values().filter { c ->
            when (c) {
                DeviceControl.AIRPLANE -> true // root toggles it; otherwise the tile opens Android's screen
                DeviceControl.REFRESH_RATE -> refreshRates(context).size > 1
                DeviceControl.FLOATING_BUTTON -> root && floatingSettingKey() != null
                // 0.6.18: every handheld whose firmware has the Xbox / Retro switch (AYN and Retroid) — found by its setting.
                DeviceControl.CONTROLLER_STYLE -> hasControllerStyle()
                // Any device: Voltcap probes the battery driver's switches itself. Shown even when none
                // works, saying so, rather than as a toggle that does nothing.
                DeviceControl.CHARGE_LIMIT -> true
                DeviceControl.TUNER_APP -> CpuTuning.tunerApp(context) != null
                DeviceControl.CPU_CAP -> RootShell.available && (com.shelf.launcher.device.tune.Tuner.supported || CpuTuning.capSupported())
                DeviceControl.AUTO_TDP -> RootShell.available && com.shelf.launcher.device.tune.Tuner.supported
                DeviceControl.UNDERCLOCK -> RootShell.available && com.shelf.launcher.device.tune.Tuner.supported
                else -> root
            }
        }
    }

    fun read(context: Context, c: DeviceControl): ControlState = runCatching {
        when (c) {
            DeviceControl.CHARGE_LIMIT -> ChargeLimiter.currentStatus(context).let { st ->
                ControlState(
                    st == ChargeLimiter.Status.ON || st == ChargeLimiter.Status.LIMITING ||
                        st == ChargeLimiter.Status.PAUSED || st == ChargeLimiter.Status.DRAINING,
                    when (st) {
                        ChargeLimiter.Status.UNSUPPORTED -> "Not supported"
                        ChargeLimiter.Status.PAUSED -> "Stopped at 80%"
                        ChargeLimiter.Status.DRAINING -> "Down to 80%"
                        else -> st.label
                    },
                    supported = st != ChargeLimiter.Status.UNSUPPORTED
                )
            }
            DeviceControl.BYPASS_CHARGING -> ControlState(getSystem(SEPARATION) == "1")
            DeviceControl.AIRPLANE -> ControlState(Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            DeviceControl.PERFORMANCE -> {
                val v = getSystem(PERF)?.toIntOrNull() ?: 0
                ControlState(v > 0, perfModes.firstOrNull { it.first == v }?.second ?: "Standard")
            }
            DeviceControl.FAN -> {
                val v = getSystem(FAN)?.toIntOrNull() ?: 1
                ControlState(v != 0, fanModes.firstOrNull { it.first == v }?.second ?: "Custom")
            }
            DeviceControl.REFRESH_RATE -> {
                val now = currentRate(context)
                ControlState(now > 61f, "${now.toInt()} Hz")
            }
            DeviceControl.CONTROLLER_STYLE -> {
                // (Android's third choice, Disconnect, takes the built-in controller away: shown as such.)
                if (getSystem(NO_GAMEPAD_LAYOUT) == "1") ControlState(false, "Disconnected")
                else {
                    val xbox = getSystem(FLIP_LAYOUT) == "1"
                    ControlState(xbox, if (xbox) "Xbox" else "Retro")
                }
            }
            DeviceControl.FLOATING_BUTTON -> ControlState(floatingSettingKey()?.let { getSystem(it) } == "1")
            DeviceControl.TUNER_APP -> ControlState(false, CpuTuning.tunerApp(context)?.label ?: "Tuner")
            DeviceControl.CPU_CAP -> if (com.shelf.launcher.device.tune.Tuner.supported) {
                val g = com.shelf.launcher.device.tune.TuneStore(context).global()
                ControlState(g.tier != com.shelf.launcher.device.tune.PowerTier.OFF, g.tier.label)
            } else CpuTuning.currentCap().let { ControlState(it < 98, if (it >= 98) "Off" else "$it%") }
            DeviceControl.AUTO_TDP -> com.shelf.launcher.device.tune.TuneStore(context).global().let {
                ControlState(it.autoTdp, if (it.autoTdp) it.autoMode.label else "Off")
            }
            DeviceControl.UNDERCLOCK -> com.shelf.launcher.device.tune.TuneStore(context).global().let {
                // While it is on, the top speed it holds right now follows the level ("Light 2.4 GHz").
                val ghz = runCatching { com.shelf.launcher.device.tune.Tuner.underclockTopGhz(it) }.getOrNull()
                ControlState(it.underclocked, it.effectiveUnderclock.label + (ghz?.let { g -> " $g" } ?: ""))
            }
        }
    }.getOrDefault(ControlState(false))

    /**
     * Switches / cycles [c]. Returns false when it couldn't be done here — the caller then opens
     * [fallbackIntent] if there is one.
     */
    fun next(context: Context, c: DeviceControl): Boolean {
        val cur = read(context, c)
        return when (c) {
            DeviceControl.CHARGE_LIMIT -> ChargeLimiter.toggle(context)
            DeviceControl.BYPASS_CHARGING -> {
                val on = !cur.on
                // Bypass and the 80 % limit are opposites (never charge / charge to 80 %): bypass on = limit off.
                // Done before the writes (it releases the other battery nodes Voltcap holds, which must not undo them).
                // (update 91) Only when bypass can really be switched (root service there): a switch that can't
                // happen no longer drops the limit as well.
                if (on && !RootShell.available) return false
                if (on) ChargeLimiter.disableForBypass(context)
                putSystem(SEPARATION, if (on) "1" else "0") &&
                    RootShell.exec("echo ${if (on) 1000 else 1000000} > $RESTRICT_CUR && echo ${if (on) 1 else 0} > $RESTRICT_CHG") != null
            }
            DeviceControl.AIRPLANE ->
                RootShell.available && RootShell.exec("cmd connectivity airplane-mode ${if (cur.on) "disable" else "enable"}") != null
            DeviceControl.PERFORMANCE -> {
                val v = getSystem(PERF)?.toIntOrNull() ?: 0
                val i = perfModes.indexOfFirst { it.first == v }
                putSystem(PERF, perfModes[(i + 1).mod(perfModes.size)].first.toString())
            }
            DeviceControl.FAN -> {
                val v = getSystem(FAN)?.toIntOrNull() ?: 1
                val i = fanModes.indexOfFirst { it.first == v }
                putSystem(FAN, fanModes[(i + 1).mod(fanModes.size)].first.toString())
            }
            DeviceControl.REFRESH_RATE -> {
                val rates = refreshRates(context)
                if (rates.size < 2 || !RootShell.available) return false
                // 0.6.15: picked by hand: that's the rate to keep (Screen rate won't put an older one back).
                com.shelf.launcher.device.VoltcapRate.forget(context)
                val now = currentRate(context)
                val target = rates.firstOrNull { it > now + 1f } ?: rates.first()
                putSystem("peak_refresh_rate", "%.1f".format(java.util.Locale.US, target)) &&
                    putSystem("min_refresh_rate", "%.1f".format(java.util.Locale.US, target))
            }
            DeviceControl.CONTROLLER_STYLE -> setControllerStyle(xbox = !cur.on)
            DeviceControl.FLOATING_BUTTON -> floatingSettingKey()?.let { putSystem(it, if (cur.on) "0" else "1") } ?: false
            DeviceControl.TUNER_APP -> CpuTuning.tunerApp(context)?.let { CpuTuning.openTuner(context, it) } ?: false
            DeviceControl.CPU_CAP -> if (com.shelf.launcher.device.tune.Tuner.supported) { com.shelf.launcher.device.tune.Tuner.cycleTier(context); true }
                else CpuTuning.cycleCap() != null
            DeviceControl.AUTO_TDP -> { com.shelf.launcher.device.tune.Tuner.cycleAutoTdp(context); true }
            DeviceControl.UNDERCLOCK -> { com.shelf.launcher.device.tune.Tuner.cycleUnderclock(context); true }
        }
    }

    /**
     * 0.6.18: Xbox or Retro, written exactly as the handheld's own Controller style does it (its Quick Settings tile and
     * Settings › Handheld Settings › Input › Controller style), so all three always agree: temp_abxy_layout_mode
     * (0 Xbox / 1 Retro), flip_button_layout (1 / 0) and persist.sys.gamepad.type (1 / 0). From "Disconnected" it
     * brings the controller back. Blocking (root service).
     */
    fun setControllerStyle(xbox: Boolean): Boolean {
        if (!RootShell.available) return false
        val back = if (getSystem(NO_GAMEPAD_LAYOUT) == "1") putSystem(NO_GAMEPAD_LAYOUT, "0") else true
        return back &&
            putSystem(ABXY_TEMP, if (xbox) "0" else "1") &&
            putSystem(FLIP_LAYOUT, if (xbox) "1" else "0") &&
            RootShell.exec("setprop $GAMEPAD_TYPE_PROP ${if (xbox) 1 else 0}") != null
    }

    /** 0.6.18: the firmware has the Xbox / Retro switch (its setting exists) and Voltcap can write it. */
    private fun hasControllerStyle(): Boolean = RootShell.available && getSystem(FLIP_LAYOUT) != null

    /**
     * 0.6.18: the handheld's own settings app (AYN's / Retroid's "Handheld Settings", where Input › Controller style
     * is), or null on other devices.
     */
    fun handheldSettingsIntent(context: Context): Intent? {
        if (handheldSearched) return handheldIntent?.let { Intent(it) }
        handheldSearched = true
        handheldIntent = findHandheldSettings(context)
        return handheldIntent?.let { Intent(it) }
    }
    @Volatile private var handheldSearched = false
    @Volatile private var handheldIntent: Intent? = null

    private fun findHandheldSettings(context: Context): Intent? {
        val pm = context.packageManager
        val cls = "com.ro.settings.activity.MainSettingsActivity"
        val known = listOf("com.odin.settings", "com.rp.settings", "com.ayn.settings")
        val found = runCatching { pm.getInstalledApplications(0).map { it.packageName }.filter { it.endsWith(".settings") && it !in known } }
            .getOrDefault(emptyList())
        for (pkg in known + found) {
            val i = Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { pm.resolveActivity(i, 0) }.getOrNull() != null) return i
        }
        return null
    }

    /** Android's own screen for a control that couldn't be switched directly. */
    fun fallbackIntent(c: DeviceControl): Intent? = when (c) {
        DeviceControl.AIRPLANE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        DeviceControl.REFRESH_RATE -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        DeviceControl.BYPASS_CHARGING -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        else -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---- helpers ----

    private fun getSystem(key: String): String? = RootShell.exec("settings get system $key")?.takeIf { it.isNotBlank() && it != "null" }

    private fun putSystem(key: String, value: String): Boolean {
        if (!key.matches(Regex("[A-Za-z0-9_.]+")) || !value.matches(Regex("[0-9.]+"))) return false
        return RootShell.exec("settings put system $key $value") != null
    }

    private fun floatingSettingKey(): String? {
        if (floatingSearched) return floatingKey
        floatingSearched = true
        // Update 201: AYN's root service returns only the first line of output ("settings list" gave one setting), so the
        // candidates are picked by grep and come back on one line, ';'-separated.
        val list = RootShell.execJoined("settings list system | grep -i -E 'float|ball'", ';') ?: return null
        floatingKey = list.split(';')
            .mapNotNull { line -> line.substringBefore('=', "").trim().takeIf { it.isNotEmpty() }?.let { it to line.substringAfter('=').trim() } }
            .firstOrNull { (k, v) -> (k.contains("float", true) || k.contains("suspend_ball", true) || k.contains("ball", true)) && (v == "0" || v == "1") }
            ?.first
        return floatingKey
    }

    private fun display(context: Context): Display? = runCatching {
        (context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
    }.getOrNull()

    /** The distinct refresh rates the built-in screen offers at its current resolution, ascending. */
    fun refreshRates(context: Context): List<Float> {
        val d = display(context) ?: return emptyList()
        val mode = d.mode
        return d.supportedModes.filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { Math.round(it.refreshRate).toFloat() }.distinct().sorted()
    }

    // ---- direct setters for per-game profiles ----

    fun currentRefreshRate(context: Context): Float = currentRate(context)

    fun setRefreshRate(hz: Float): Boolean =
        RootShell.available && putSystem("peak_refresh_rate", "%.1f".format(java.util.Locale.US, hz)) &&
            putSystem("min_refresh_rate", "%.1f".format(java.util.Locale.US, hz))

    /** True = Xbox layout, false = Nintendo, null = can't tell (no root / not AYN). */
    fun controllerIsXbox(): Boolean? = if (!RootShell.available || !isAyn()) null else getSystem(FLIP_LAYOUT) == "1"

    fun setControllerXbox(xbox: Boolean): Boolean =
        RootShell.available && isAyn() && putSystem(ABXY_TEMP, if (xbox) "0" else "1") &&
            putSystem(NO_GAMEPAD_LAYOUT, "0") && putSystem(FLIP_LAYOUT, if (xbox) "1" else "0")

    private fun currentRate(context: Context): Float = display(context)?.refreshRate?.let { Math.round(it).toFloat() } ?: 60f
}
