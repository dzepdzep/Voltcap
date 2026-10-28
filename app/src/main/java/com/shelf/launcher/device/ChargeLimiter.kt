package com.shelf.launcher.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The 80 % charge limit: with it on, the battery charges normally up to 80 % and then stops — it is
 * never charged past 80 %. This is NOT charge bypass (AYN's "run only from the charger" mode): the
 * two are separate, and switching the limit on switches bypass off (and the other way round, see
 * [DeviceControls]), so they never fight over the same battery switch.
 *
 * Plugged in above 80 % (say at 95 %), the charger is cut off entirely where the device has a switch
 * for that (input_suspend), so the battery runs down to 80 % — then it is held there.
 *
 * Voltcap pauses charging itself through battery-driver switches at 80 % and lets it charge again
 * below 77 %. The switches that exist on the device are found once, best first:
 *
 *  1. `/sys/class/power_supply/battery/charge_control_end_threshold` (the kernel stops at the value itself)
 *  2. AYN `/sys/class/qcom-battery/restrict_chg` **together with** `restrict_cur` (the pair AYN's own
 *     charge bypass writes). `restrict_chg` alone only *limits* the current to `restrict_cur`, which
 *     is about 1 A by default, so both are written and both are checked.
 *  3. `/sys/class/power_supply/battery/input_suspend` (1 = charger input suspended)
 *  4. `/sys/class/power_supply/battery/charge_control_limit` (max level = least charge current)
 *  5. `/sys/class/power_supply/{any}/charging_enabled`, `battery/battery_charging_enabled` (0 = not charging)
 *
 * **Overnight fix (Oct 2026).** The battery reached 86 % overnight. What went wrong before:
 *  - only `restrict_chg` was read back, so when the driver or firmware put `restrict_cur` back to full
 *    current the battery charged on while Voltcap still thought it was stopped;
 *  - a switch was judged "not working" when the battery said "charging" for two minutes — AYN's
 *    driver says "charging" while held at 1 mA — or when the level rose 2 %; it was then **released**
 *    and the next one tried. Each weaker switch let another 2 % through: 80 → 82 → 84 → 86.
 * Now every node of a held switch is read back on every check and put back when it came undone
 * ([restored]); the "still says charging" test is gone; and when the battery really does rise while
 * stopped, the next switch is **added** to the ones already held (a working one is never let go). The
 * set that does the job is remembered ([KEY_STACK]). AYN's own 80 % setting is kept off, so its
 * firmware never works the same switch at the same time; switching it on in AYN's menu hands it to Voltcap.
 *
 * Writes go through the vendor root service ([RootShell]) where there is one, else a plain file
 * write (works only where the kernel lets apps write the node). When nothing works the R1 tile says
 * "Not supported on this device" instead of being a toggle that does nothing.
 *
 * While the limit is on and the charger is in, [ChargeLimitService] (a minimum-priority foreground
 * service) keeps this running — battery broadcasts stop when the process dies. A stop switch that is
 * set stays set in the driver even if Voltcap's process goes away.
 */
object ChargeLimiter {
    const val STOP_AT = 80
    private const val RESUME_BELOW = 77
    private const val PREFS = "charge_limit"
    private const val KEY_ON = "on"
    /** Before Oct 2026: switches judged "bad". Dropped on start — it could hold the one that works. */
    private const val KEY_BAD = "bad_switches"
    /** The switches that together stop charging on this device: paths, best first, joined by ','. */
    private const val KEY_STACK = "switch_stack"
    private const val AYN_LIMIT_80 = "charging_limit_greater_than_80"
    private const val AYN_SEPARATION = "is_charging_separation"
    private const val RESTRICT_CHG = "/sys/class/qcom-battery/restrict_chg"
    private const val RESTRICT_CUR = "/sys/class/qcom-battery/restrict_cur"
    private const val RECHECK_MS = 15_000L
    /** How far the level may rise while stopped (gauge lag) before the stop counts as not enough. */
    private const val RISE_TOLERANCE = 2

    enum class Kind { STOP_FLAG, ENABLE_FLAG, THRESHOLD, LEVEL_MAX }

    /**
     * One charge switch. [path] is the node whose value tells whether it's paused; [beforeStop] are
     * extra writes done before pausing, [afterResume] extra writes done after resuming.
     */
    data class ChargeSwitch(
        val path: String,
        val kind: Kind,
        val stopValue: String,
        val resumeValue: String,
        val beforeStop: List<Pair<String, String>> = emptyList(),
        val afterResume: List<Pair<String, String>> = emptyList()
    ) {
        fun isStopped(value: String?): Boolean = when (kind) {
            Kind.THRESHOLD -> value?.trim()?.toIntOrNull()?.let { it < 100 } == true
            else -> value?.trim() == stopValue
        }
        /** Every node a stop writes, with the value it has to read back. */
        val stopWrites: List<Pair<String, String>> get() = beforeStop + (path to stopValue)
        val shortName: String get() = path.substringAfterLast('/')
    }

    /** What the R1 tile and the service notification show. */
    enum class Status(val label: String) {
        UNSUPPORTED("Not supported on this device"),
        OFF("Off"),
        CHARGING("Charging"),
        ON("On"),
        LIMITING("Limiting"),
        PAUSED("Stopped at $STOP_AT %"),
        DRAINING("Coming down to $STOP_AT %")
    }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status

    @Volatile private var registered = false
    @Volatile private var probed = false
    @Volatile private var switches: List<ChargeSwitch> = emptyList()
    /** Indices into [switches] held together, best first. Not empty once probed (when any switch works). */
    @Volatile private var stack: List<Int> = emptyList()
    /** What Voltcap last set the stop switches to: true = stopped, false = charging, null = not known yet. */
    @Volatile private var paused: Boolean? = null
    /** Battery level when charging was stopped (it may have been plugged in above 80 %). */
    @Volatile private var pausedAtPercent = STOP_AT
    @Volatile private var lastNodeCheck = 0L
    @Volatile private var lastPlugged: Boolean? = null
    @Volatile private var lastSettingCheck = 0L
    /** A kernel threshold in [stack] is set to [STOP_AT] (null = not looked at yet). */
    @Volatile private var thresholdOn: Boolean? = null
    /** Every switch is held and the level still rose: nothing more to add. */
    @Volatile private var exhausted = false
    /** Plugged in above the limit: the charger is cut off so the battery comes down to 80 %. */
    @Volatile private var draining = false
    @Volatile private var bypassOn = false
    /** How often a held switch was found undone (by the driver or firmware) and put back. */
    @Volatile var restored = 0
        private set
    /** Why the last write didn't stick, or what was last put right (shown in Settings › Device), or null. */
    @Volatile var lastError: String? = null
        private set
    /** The switches in use, for Settings › Device / diagnostics. */
    val activeSwitch: String?
        get() = stack.mapNotNull { switches.getOrNull(it)?.path }.takeIf { it.isNotEmpty() }?.joinToString(" + ")

    /** Call once (any time): listens to battery changes for as long as Voltcap's process lives. */
    fun start(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        Thread { runCatching { check(app, null) } }.start()
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val pending = goAsync()
                    Thread {
                        try { runCatching { check(app, i) } } finally { pending.finish() }
                    }.start()
                }
            }, IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            })
        }
    }

    /** The setting was just switched: apply it right away. */
    fun refresh(context: Context) {
        lastSettingCheck = 0L
        lastNodeCheck = 0L
        Thread { runCatching { check(context.applicationContext, null) } }.start()
    }

    /** Whether some charge switch on this device works (blocking: probes once). */
    fun supported(context: Context): Boolean = activeFor(context) != null

    /** Whether the limit is switched on. Blocking (may read AYN's setting once a minute). */
    fun isOn(context: Context): Boolean {
        refreshSettings(context)
        return ownSwitch(context)
    }

    /** Switches the limit on / off (blocking). False when this device has no working switch. */
    fun toggle(context: Context): Boolean {
        if (!supported(context)) return false
        val on = !isOn(context)
        setOn(context, on)
        return true
    }

    /**
     * Switches the limit on / off (blocking). On: AYN's charge bypass is switched off first — the
     * limit charges the battery to 80 % and stops; bypass would never charge it at all.
     */
    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        if (RootShell.available && isAyn()) {
            // AYN's own 80 % setting stays off: its firmware could work the same battery switch as
            // Voltcap, and the two would undo each other's writes.
            RootShell.exec("settings put system $AYN_LIMIT_80 0")
            if (on && RootShell.exec("settings get system $AYN_SEPARATION")?.trim() == "1") {
                RootShell.exec("settings put system $AYN_SEPARATION 0")
                // Hand the battery switch back to normal charging; the limit takes it from here.
                RootShell.exec("echo 0 > $RESTRICT_CHG ; echo 1000000 > $RESTRICT_CUR")
                paused = null
            }
        }
        exhausted = false
        lastError = null
        lastSettingCheck = 0L
        lastNodeCheck = 0L
        runCatching { check(context.applicationContext, null) }
    }

    /** Charge bypass was switched on from the R1 menu: the limit steps aside and lets go of its switches. */
    fun disableForBypass(context: Context) {
        prefs(context).edit().putBoolean(KEY_ON, false).apply()
        if (RootShell.available && isAyn()) RootShell.exec("settings put system $AYN_LIMIT_80 0")
        synchronized(this) {
            // Bypass takes restrict_chg / restrict_cur over; anything else Voltcap holds (an input
            // cut-off above all) must go, or bypass would run the handheld from the battery.
            releaseAllBut(RESTRICT_CHG)
            draining = false
            paused = null
        }
        lastSettingCheck = 0L
    }

    /** Current status, freshly worked out (blocking). */
    fun currentStatus(context: Context): Status {
        runCatching { check(context.applicationContext, null) }
        return _status.value
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ownSwitch(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    private fun isRetroid(): Boolean = BuildFingerprint.current().let {
        it.model.contains("Retroid", true) || it.manufacturer.equals("Moorechip", true) || it.brand.contains("Retroid", true)
    }

    private fun isAyn(): Boolean = BuildFingerprint.current().let {
        it.manufacturer.equals("AYN", true) || it.brand.equals("AYN", true)
    }

    /**
     * The firmware settings are re-read at most once a minute (root calls); battery events are frequent.
     * AYN's own 80 % setting switched on in AYN's menu is taken over: Voltcap's limit goes on, AYN's off.
     */
    private fun refreshSettings(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSettingCheck < 60_000L) return
        lastSettingCheck = now
        if (RootShell.available && isAyn()) {
            bypassOn = RootShell.exec("settings get system $AYN_SEPARATION")?.trim() == "1"
            if (RootShell.exec("settings get system $AYN_LIMIT_80")?.trim() == "1") {
                RootShell.exec("settings put system $AYN_LIMIT_80 0")
                if (!bypassOn) prefs(context).edit().putBoolean(KEY_ON, true).apply()
            }
        } else {
            bypassOn = false
        }
    }

    private fun held(): List<ChargeSwitch> = stack.mapNotNull { switches.getOrNull(it) }

    /** The input cut-off used to bring the level down, when it isn't already one of the held switches. */
    private fun drainSwitch(): ChargeSwitch? {
        val heldPaths = held().map { it.path }
        return switches.firstOrNull { it.path.endsWith("/input_suspend") && it.path !in heldPaths }
    }

    /** Lets go of every held switch (and the input cut-off) except the one at [keep]. */
    private fun releaseAllBut(keep: String) {
        held().filter { it.path != keep }.forEach { sw ->
            if (sw.kind == Kind.THRESHOLD) writeChecked(sw.path, sw.resumeValue) else setSwitch(sw, false)
        }
        if (draining) drainSwitch()?.let { setSwitch(it, false) }
        thresholdOn = null
    }

    @Synchronized
    private fun check(context: Context, intent: Intent?) {
        if (activeFor(context) == null) {
            _status.value = Status.UNSUPPORTED
            // (Update 360: not stopped while an AYN handheld's root service is only out of reach for now — the next battery
            // change tries again; stopping the service let Android end Voltcap and the Thor charged to 100 %.)
            if (isAyn() && !probed) { lastError = "The handheld's root service isn't reachable yet; trying again."; return }
            ChargeLimitService.stop(context)
            return
        }
        refreshSettings(context)
        val limitOn = ownSwitch(context)
        // POWER_CONNECTED / DISCONNECTED carry no level: always read the sticky battery state.
        val battery = intent?.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED }
            ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val percent = level * 100 / scale
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val now = System.currentTimeMillis()

        val plugChanged = lastPlugged != plugged
        lastPlugged = plugged
        val all = held()
        val flags = all.filter { it.kind != Kind.THRESHOLD }
        val thresholds = all.filter { it.kind == Kind.THRESHOLD }

        // First look after a start: a stop switch may still be set from before (Voltcap was restarted).
        // Measure any rise from here, not from 80 % — it isn't the switch's fault if it was already higher.
        // Only adopted while the limit is on: with it off, a stop set by the firmware or another tool isn't
        // Voltcap's to undo (it used to switch charging back on at every app start) (update 91).
        if (paused == null) {
            lastNodeCheck = now
            paused = limitOn && flags.any { it.isStopped(read(it.path)) }
            if (paused == true) pausedAtPercent = maxOf(percent, STOP_AT)
        }

        // Limit off and charge bypass on: bypass owns AYN's switch; let go of anything else still held.
        if (!limitOn && bypassOn) {
            if (paused == true || draining || thresholdOn == true) {
                releaseAllBut(RESTRICT_CHG)
                paused = false
                draining = false
            }
            _status.value = if (plugged) Status.CHARGING else Status.OFF
            ChargeLimitService.stop(context)
            return
        }

        // Root calls only when they matter: the limit is on or something is still held. Re-read the
        // real nodes now and then and whenever the cable changed (drivers reset them on replug).
        val busy = limitOn || paused == true || draining || thresholdOn == true
        val recheck = busy && (plugChanged || now - lastNodeCheck > RECHECK_MS)
        if (recheck) lastNodeCheck = now

        // Kernel thresholds: the kernel does the stopping, just keep the value right. Put back to 100 only when
        // Voltcap set it: with the limit off from the start, a vendor's / your own threshold stays (update 91).
        if (thresholds.isNotEmpty() && (limitOn || thresholdOn == true) && (recheck || thresholdOn != limitOn)) {
            thresholds.forEach { t ->
                val want = if (limitOn) STOP_AT.toString() else t.resumeValue
                if (read(t.path)?.trim() != want) writeChecked(t.path, want)
            }
            thresholdOn = limitOn
        }

        // Plugged in above the limit: cut the charger off (where the device can) until it's down to 80 %.
        val drain = drainSwitch()
        if (drain == null) {
            draining = false
        } else {
            val wantDrain = limitOn && plugged && percent > STOP_AT + 1
            if (wantDrain != draining) {
                draining = if (setSwitch(drain, wantDrain)) wantDrain else drain.isStopped(read(drain.path))
            } else if (draining && recheck && !holds(drain)) {
                setSwitch(drain, true)
            }
        }

        if (flags.isNotEmpty()) {
            val wantPaused = when {
                !limitOn || !plugged -> false
                percent >= STOP_AT -> true
                percent < RESUME_BELOW -> false
                else -> paused ?: false // in between: keep whatever it is doing
            }
            if (paused != wantPaused) {
                val list = if (wantPaused) flags else flags.asReversed()
                list.forEach { setSwitch(it, wantPaused) }
                paused = wantPaused
                if (wantPaused) pausedAtPercent = maxOf(percent, STOP_AT)
            } else if (wantPaused && recheck) {
                // Drivers and firmware undo these (on a replug, a charger renegotiation, or restrict_cur
                // going back to full current while restrict_chg still says "stopped"): put back whatever
                // came undone instead of blaming the switch.
                val undone = flags.filterNot { holds(it) }
                if (undone.isNotEmpty()) {
                    undone.forEach { setSwitch(it, true) }
                    restored++
                    lastError = "${undone.joinToString { it.shortName }} had come undone; put back ($restored× so far)."
                }
            }
        } else {
            paused = limitOn && plugged && percent >= STOP_AT
        }

        // Still rising while stopped (and every node reads back right)? This set isn't enough on
        // this device: add the next switch on top. Nothing that works is ever let go.
        if (limitOn && plugged && paused == true && !draining && percent >= pausedAtPercent + RISE_TOLERANCE) {
            if (!exhausted) addNextSwitch(context, percent)
            pausedAtPercent = percent
        }

        _status.value = when {
            !limitOn -> if (plugged) Status.CHARGING else Status.OFF
            draining -> Status.DRAINING
            paused == true -> Status.PAUSED
            plugged -> Status.LIMITING
            else -> Status.ON
        }
        // Keep enforcing while a game runs or Voltcap is closed: only needed while plugged in.
        if (limitOn && plugged) ChargeLimitService.ensureRunning(context, _status.value)
        else ChargeLimitService.stop(context)
    }

    /** Holds the next switch too (on top of the ones already held) and remembers the set. */
    private fun addNextSwitch(context: Context, percent: Int) {
        val next = switches.indices.firstOrNull { it !in stack }
        if (next == null) {
            exhausted = true
            lastError = "Every charge switch on this device is set and it still charged to $percent % (${switches.size} tried)."
            return
        }
        val sw = switches[next]
        stack = stack + next
        prefs(context).edit().putString(KEY_STACK, stack.joinToString(",") { switches[it].path }).apply()
        val ok = if (sw.kind == Kind.THRESHOLD) writeChecked(sw.path, STOP_AT.toString()).also { thresholdOn = true }
            else setSwitch(sw, true)
        lastError = "Still charging at $percent %: added ${sw.shortName}${if (ok) "" else " (didn't stick)"} — now ${activeSwitch}."
    }

    /** Whether every node of a held switch still reads its "stopped" value. */
    private fun holds(sw: ChargeSwitch): Boolean = sw.stopWrites.all { (path, value) ->
        if (path == sw.path) sw.isStopped(read(path)) else read(path)?.trim() == value
    }

    private fun setSwitch(sw: ChargeSwitch, stop: Boolean): Boolean = if (stop) {
        sw.beforeStop.forEach { (path, value) -> writeChecked(path, value) }
        writeChecked(sw.path, sw.stopValue)
    } else {
        val ok = writeChecked(sw.path, sw.resumeValue)
        sw.afterResume.forEach { (path, value) -> writeChecked(path, value) }
        ok
    }

    // ---- switch discovery ----

    private fun activeFor(context: Context): ChargeSwitch? {
        if (!probed) synchronized(this) {
            if (!probed) {
                switches = runCatching { probe() }.getOrDefault(emptyList())
                // (Update 360: on an AYN handheld with its root service out of reach right now — Voltcap just restarted —
                // nothing works without it; look again on the next check instead of settling on "no switch" for the run.)
                if (switches.isEmpty() && isAyn() && !RootShell.available) return null
                val p = prefs(context)
                p.edit().remove(KEY_BAD).apply()
                val saved = p.getString(KEY_STACK, null).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                stack = saved.mapNotNull { path -> switches.indexOfFirst { it.path == path }.takeIf { it >= 0 } }
                    .distinct()
                    .ifEmpty { if (switches.isEmpty()) emptyList() else listOf(0) }
                probed = true
            }
        }
        return stack.firstOrNull()?.let { switches.getOrNull(it) }
    }

    private fun probe(): List<ChargeSwitch> {
        val candidates = mutableListOf<ChargeSwitch>()
        // Best: the kernel's own charge limit (it stops at the value itself, nothing to babysit).
        candidates += ChargeSwitch("/sys/class/power_supply/battery/charge_control_end_threshold", Kind.THRESHOLD, STOP_AT.toString(), "100")
        val restrictChg = RESTRICT_CHG
        val restrictCur = RESTRICT_CUR
        if (read(restrictCur)?.trim()?.toIntOrNull() != null) {
            // Same values AYN's own charge bypass writes. (RP Classic QA, 27 Oct 2026: on the Retroid Pocket Classic's driver
            // restrict_cur = 1000 still let 180-310 mA in at 91 % with restrict_chg = 1 — measured; 0 stops it: the battery
            // drew -250..-540 mA from the charger's side within seconds, read back 0. So the Retroid stops at 0.)
            candidates += ChargeSwitch(restrictChg, Kind.STOP_FLAG, "1", "0",
                beforeStop = listOf(restrictCur to (if (isRetroid()) "0" else "1000")), afterResume = listOf(restrictCur to "1000000"))
        } else {
            candidates += ChargeSwitch(restrictChg, Kind.STOP_FLAG, "1", "0")
        }
        candidates += ChargeSwitch("/sys/class/power_supply/battery/input_suspend", Kind.STOP_FLAG, "1", "0")
        val limitPath = "/sys/class/power_supply/battery/charge_control_limit"
        read("${limitPath}_max")?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let { max ->
            candidates += ChargeSwitch(limitPath, Kind.LEVEL_MAX, max.toString(), "0")
        }
        listChargingEnabled().forEach { candidates += ChargeSwitch(it, Kind.ENABLE_FLAG, "0", "1") }
        candidates += ChargeSwitch("/sys/class/power_supply/battery/battery_charging_enabled", Kind.ENABLE_FLAG, "0", "1")
        return candidates.distinctBy { it.path }.filter { works(it) }
    }

    /** A switch works when writing back its current value succeeds and reads back the same. Changes nothing. */
    private fun works(sw: ChargeSwitch): Boolean {
        val current = read(sw.path)?.trim()?.takeIf { it.isNotEmpty() && it.matches(Regex("[0-9]+")) } ?: return false
        return writeChecked(sw.path, current)
    }

    private fun listChargingEnabled(): List<String> {
        if (RootShell.available) {
            // Update 201: AYN's root service returns only the first line of output, so `ls -d` (one path per line) found
            // one charging_enabled switch at most; the glob prints them all on one line (unmatched, it stays a literal "*").
            val out = RootShell.exec("echo /sys/class/power_supply/*/charging_enabled") ?: return emptyList()
            return out.split(Regex("\\s+")).map { it.trim() }
                .filter { it.startsWith("/sys/") && it.endsWith("/charging_enabled") && !it.contains('*') }
        }
        return File("/sys/class/power_supply").listFiles().orEmpty()
            .map { File(it, "charging_enabled") }.filter { it.exists() }.map { it.path }
    }

    // ---- file access (root service where there is one, else plain file I/O) ----

    private fun safePath(path: String) = path.matches(Regex("[A-Za-z0-9_/.\\-]+"))

    private fun read(path: String): String? {
        if (!safePath(path)) return null
        if (RootShell.available) {
            val out = RootShell.exec("cat $path 2>/dev/null") ?: return null
            return out.takeUnless { it.isBlank() || it.contains("No such file") || it.contains("Permission denied") }
        }
        return runCatching { File(path).takeIf { it.exists() }?.readText()?.trim() }.getOrNull()
    }

    /** Writes [value] and reads it back; true only when it stuck. */
    private fun writeChecked(path: String, value: String): Boolean {
        if (!safePath(path) || !value.matches(Regex("[0-9]+"))) return false
        val wrote = if (RootShell.available) {
            RootShell.exec("echo $value > $path") != null
        } else {
            runCatching { File(path).writeText(value) }.isSuccess
        }
        val back = read(path)?.trim()
        val ok = wrote && back == value
        // Failures are kept (a later success elsewhere doesn't hide them); switching the limit clears it.
        if (!ok) lastError = "Couldn't set $path (read back \"${back ?: "nothing"}\")."
        return ok
    }
}
