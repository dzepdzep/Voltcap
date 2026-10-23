package com.shelf.launcher.data

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock

/**
 * Battery watcher: how much of the battery Voltcap itself is using, and what could be changed.
 *
 * Android gives an app no per-app battery numbers, so this measures what an app *can* see, once a minute
 * and only while Voltcap's process lives (a sleeping thread, no wake lock, nothing in the background):
 *  - Voltcap's own CPU time against the clock, split by where the user is: Voltcap in front, another app
 *    or a game in front, or the screen off. CPU is what costs the battery in a launcher.
 *  - The battery's current draw while unplugged (screen on only: a reading taken while the device is
 *    awake with the screen off is the wake-up itself, so that one is measured as percent per hour instead).
 *  - How much the battery fell while the screen was off for at least 10 minutes.
 * Android's own estimate (dumpsys batterystats) is added when this app is allowed to read it.
 * Measurements live in prefs "battery_watch" (this device only) and restart every 7 days.
 */
object BatteryWatch {
    enum class Where(val key: String, val label: String) {
        FRONT("front", "Voltcap in front"),
        OTHER("other", "A game or another app in front"),
        OFF("off", "Screen off")
    }

    private const val PREFS = "battery_watch"
    private const val WEEK = 7L * 24 * 3600 * 1000
    private const val EVERY_MS = 60_000L

    @Volatile private var started = false
    private var lastWall = 0L
    private var lastCpu = 0L
    private var offAt = 0L
    private var offLevel = -1f
    private var offPlugged = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Starts the once-a-minute check (idempotent; a no-op while the switch is off). */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    runCatching { if (i.action == Intent.ACTION_SCREEN_OFF) screenOff(app) else screenOn(app) }
                }
            }, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
        }
        Thread({
            lastWall = SystemClock.elapsedRealtime(); lastCpu = Process.getElapsedCpuTime()
            while (true) {
                try { Thread.sleep(EVERY_MS) } catch (_: InterruptedException) { return@Thread }
                runCatching { if (Features.batteryWatch.value) sample(app) else { lastWall = SystemClock.elapsedRealtime(); lastCpu = Process.getElapsedCpuTime() } }
            }
        }, "voltcap-battery-watch").apply { isDaemon = true; start() }
    }

    private fun levelAndPlugged(c: Context): Pair<Float, Boolean> {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return (if (level < 0) -1f else level * 100f / scale.coerceAtLeast(1)) to plugged
    }

    private fun screenOff(c: Context) {
        val (lvl, plugged) = levelAndPlugged(c)
        offAt = SystemClock.elapsedRealtime(); offLevel = lvl; offPlugged = plugged
    }

    private fun screenOn(c: Context) {
        if (offAt == 0L || !Features.batteryWatch.value) { offAt = 0L; return }
        val (lvl, plugged) = levelAndPlugged(c)
        val hours = (SystemClock.elapsedRealtime() - offAt) / 3_600_000.0
        // Ten minutes at least, unplugged all the way, and a battery level that could be read both times.
        if (hours >= 10 / 60.0 && !plugged && !offPlugged && lvl >= 0 && offLevel >= 0 && lvl <= offLevel) {
            val p = prefs(c)
            roll(p)
            p.edit().putFloat("off_hours", p.getFloat("off_hours", 0f) + hours.toFloat())
                .putFloat("off_drop", p.getFloat("off_drop", 0f) + (offLevel - lvl)).apply()
        }
        offAt = 0L
    }

    /** Starts a fresh week when the measurements are older than that. */
    private fun roll(p: android.content.SharedPreferences) {
        val now = System.currentTimeMillis()
        val since = p.getLong("since", 0L)
        if (since == 0L) p.edit().putLong("since", now).apply()
        else if (now - since > WEEK) p.edit().clear().putLong("since", now).apply()
    }

    private fun currentMa(c: Context): Double? {
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Long.MIN_VALUE || raw == 0L) return null
        val a = kotlin.math.abs(raw.toDouble())
        // Most devices report microamps; a few report milliamps.
        return if (a > 20_000) a / 1000.0 else a
    }

    private fun sample(c: Context) {
        val wall = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val dWall = wall - lastWall
        val dCpu = cpu - lastCpu
        lastWall = wall; lastCpu = cpu
        if (dWall < 20_000L || dCpu < 0) return
        // (Update 321, Toni: "the battery watcher doesn't work well": a long gap — the handheld asleep, the screen off for
        // hours, the sleeping thread not counting — was thrown away, so the night was never measured. It's the screen-off
        // time, with the CPU Voltcap used in it: counted as that.)
        val asleep = dWall > 3 * EVERY_MS
        val interactive = !asleep && (c.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
        val info = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        val front = info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val where = when {
            !interactive -> Where.OFF
            front -> Where.FRONT
            else -> Where.OTHER
        }
        val (_, plugged) = levelAndPlugged(c)
        val p = prefs(c)
        roll(p)
        val k = where.key
        val e = p.edit()
        e.putFloat("${k}_wall", p.getFloat("${k}_wall", 0f) + dWall / 1000f)
        e.putFloat("${k}_cpu", p.getFloat("${k}_cpu", 0f) + dCpu / 1000f)
        if (where != Where.OFF && !plugged) currentMa(c)?.let {
            e.putFloat("${k}_ma", p.getFloat("${k}_ma", 0f) + it.toFloat())
            e.putInt("${k}_man", p.getInt("${k}_man", 0) + 1)
        }
        e.apply()
    }

    /** What was measured in one situation. */
    data class Slice(val where: Where, val hours: Double, val cpuPercent: Double, val avgMa: Double?) {
        /** Update 321: [cpuPercent] is of one core (all Voltcap's threads together); this is of the whole processor. */
        val wholeCpuPercent: Double get() = cpuPercent / CORES
    }

    /** Update 321: the processor's cores (the watcher's "% of a core" against all of them). */
    val CORES: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    data class Report(
        val slices: List<Slice>,
        /** Percent of the battery lost per hour with the screen off (null: not enough yet). */
        val standbyPerHour: Double?,
        val standbyHours: Double,
        /** Android's own estimate for Voltcap, e.g. "42 mAh (3 % of the battery)", or null. */
        val androidEstimate: String?,
        val verdict: Verdict,
        val findings: List<String>,
        val suggestions: List<String>,
        val sinceMs: Long
    )

    enum class Verdict(val label: String) {
        NOT_ENOUGH("Still measuring"),
        LIGHT("Voltcap is light on the battery"),
        MODERATE("Voltcap uses a fair amount"),
        HEAVY("Voltcap uses a lot")
    }

    /** The measurements so far, with what they say and what could be changed. Blocking (reads Android's estimate). */
    fun report(c: Context, withAndroid: Boolean = true): Report {
        val p = prefs(c)
        val slices = Where.values().map { w ->
            val wall = p.getFloat("${w.key}_wall", 0f).toDouble()
            val cpu = p.getFloat("${w.key}_cpu", 0f).toDouble()
            val n = p.getInt("${w.key}_man", 0)
            Slice(w, wall / 3600.0, if (wall > 0) cpu * 100.0 / wall else 0.0, if (n > 0) p.getFloat("${w.key}_ma", 0f) / n.toDouble() else null)
        }
        val sh = p.getFloat("off_hours", 0f).toDouble()
        val standby = if (sh >= 0.5) p.getFloat("off_drop", 0f) / sh else null
        val front = slices.first { it.where == Where.FRONT }
        val other = slices.first { it.where == Where.OTHER }
        val off = slices.first { it.where == Where.OFF }
        val findings = ArrayList<String>()
        val tips = ArrayList<String>()
        var level = 0
        val enough = front.hours >= 0.05 || other.hours >= 0.05 || off.hours >= 0.25
        if (front.hours >= 0.05) {
            findings += "With Voltcap in front it keeps about %.0f %% of one processor core busy (%.1f %% of all %d cores).".format(front.cpuPercent, front.wholeCpuPercent, CORES)
            if (front.cpuPercent >= 35) {
                level = maxOf(level, 2)
                tips += "Use Lite effects: Settings › Performance. Animated backgrounds, glows and live scenes redraw the screen all the time."
                tips += "Pick a still or simpler theme, and turn video previews and animated game art off (Settings › Theme, Settings › Home)."
                tips += "Lower the blur strength or switch blur off in the music player and details window."
            } else if (front.cpuPercent >= 18) {
                level = maxOf(level, 1)
                tips += "A little lighter: switch to Lite effects (Settings › Performance) or a theme without animated scenes."
            }
        }
        if (other.hours >= 0.05) {
            findings += "While a game or another app is in front Voltcap still uses about %.1f %% of a core.".format(other.cpuPercent)
            if (other.cpuPercent >= 6) {
                level = maxOf(level, 2)
                tips += "Voltcap works behind your games. Turn off the performance overlay, the music visualizer and controller lights while playing (Settings › Overlay, Settings › Theme)."
                tips += "Stop Spotify and sync checks in the background: Settings › Sync (sync less often) and Settings › Accounts."
            } else if (other.cpuPercent >= 2.5) {
                level = maxOf(level, 1)
                tips += "Voltcap does a little work behind games. The performance overlay and music player are the usual cause (Settings › Overlay)."
            }
        }
        if (off.hours >= 0.25) {
            findings += "With the screen off Voltcap uses about %.2f %% of a core.".format(off.cpuPercent)
            if (off.cpuPercent >= 0.8) {
                level = maxOf(level, 2)
                tips += "Voltcap is busy while the screen is off. Turn off background music and screensaver tricks, and use Sleep mode (Settings › Device)."
            }
        }
        if (standby != null) {
            findings += "The battery falls about %.1f %% per hour with the screen off (over %.1f h).".format(standby, sh)
            if (standby >= 3.0) {
                level = maxOf(level, 1)
                tips += "That is a high standby loss. Check Settings › Device › Sleep mode (Wi-Fi / Bluetooth off while the screen is off) and what else is running in Android's battery screen."
            }
        }
        val onMa = listOfNotNull(front.avgMa, other.avgMa)
        if (front.avgMa != null) findings += "Current draw with Voltcap in front: about %.0f mA.".format(front.avgMa)
        if (other.avgMa != null) findings += "Current draw with a game or other app in front: about %.0f mA.".format(other.avgMa)
        if (onMa.size == 2 && front.avgMa!! > 0 && front.avgMa > other.avgMa!! * 1.15) {
            tips += "The home screen draws more than your games do. Dim effects, animated themes and the screen brightness first."
        }
        val android = if (withAndroid) runCatching { androidEstimate(c) }.getOrNull() else null
        if (android != null) findings += "Android's own estimate for Voltcap: $android."
        if (enough && tips.isEmpty()) tips += "Nothing to change: Voltcap's share is small. The screen and your games use far more."
        val verdict = when {
            !enough -> Verdict.NOT_ENOUGH
            level >= 2 -> Verdict.HEAVY
            level == 1 -> Verdict.MODERATE
            else -> Verdict.LIGHT
        }
        return Report(slices, standby, sh, android, verdict, findings, tips, p.getLong("since", 0L))
    }

    fun reset(c: Context) {
        prefs(c).edit().clear().apply()
        lastWall = SystemClock.elapsedRealtime(); lastCpu = Process.getElapsedCpuTime()
    }

    /**
     * Android's own per-app estimate from "dumpsys batterystats", read in Voltcap's process. Only works when
     * Voltcap holds the DUMP permission (and Android lets it read this service); null otherwise.
     */
    private fun androidEstimate(c: Context): String? {
        if (!com.shelf.launcher.device.audio.AudioReport.hasDump(c)) return null
        val text = com.shelf.launcher.device.audio.AudioReport.dump("batterystats", "--charged") ?: return null
        val at = text.indexOf("Estimated power use (mAh)")
        if (at < 0) return null
        val section = text.substring(at, minOf(text.length, at + 20_000))
        val me = "u0a" + (Process.myUid() % 100000 - 10000)
        val mine = Regex("UID " + Regex.escape(me) + ":\\s*([0-9.]+)").find(section)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val total = Regex("Computed drain:\\s*([0-9.]+)").find(section)?.groupValues?.get(1)?.toDoubleOrNull()
        return if (total != null && total > 0) "%.0f mAh since the last full charge (%.0f %% of what the apps used)".format(mine, mine * 100 / total)
        else "%.0f mAh since the last full charge".format(mine)
    }
}
