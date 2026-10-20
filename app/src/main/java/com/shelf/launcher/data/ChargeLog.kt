package com.shelf.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 0.6.18: Charging log — what the handheld does while it charges, written down so a hot charge (the RP Classic on a
 * 65 W charger, #99) can be looked at afterwards: once a minute while plugged in, and at plug / unplug.
 *
 * Read-only: it never changes a charging setting, only reads Android's battery broadcast (temperature, voltage, level,
 * charger, status), the battery's current, the chip's temperature (the vendor root service, where there is one),
 * whether the screen is on and whether Voltcap or another app is in front.
 *
 * Kept as CSV in Voltcap's own folder on the storage (Android/data/com.shelf.launcher/files/charge_log.csv, so it can be
 * read over USB later), the last 7 days. Runs only while Voltcap's process lives (a sleeping thread, no wake lock).
 */
object ChargeLog {
    private const val FILE = "charge_log.csv"
    private const val KEEP_MS = 7L * 24 * 3600 * 1000
    private const val EVERY_MS = 60_000L
    private const val HEADER = "time,event,level_pct,plugged,status,battery_temp_c,soc_temp_c,voltage_mv,current_ma,screen_on,in_front"

    @Volatile private var started = false
    @Volatile var inFront: Boolean = true

    private fun file(c: Context): File? = c.applicationContext.getExternalFilesDir(null)?.let { File(it, FILE) }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        runCatching {
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val ev = if (i.action == Intent.ACTION_POWER_CONNECTED) "plugged" else "unplugged"
                    Thread { runCatching { sample(app, ev) } }.start()
                }
            }, IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) })
        }
        Thread({
            runCatching { trim(app) }
            while (true) {
                runCatching { if (plugged(app)) sample(app, "tick") }
                SystemClock.sleep(EVERY_MS)
            }
        }, "ChargeLog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun battery(c: Context): Intent? = runCatching { c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()

    private fun plugged(c: Context): Boolean = (battery(c)?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    @Synchronized
    private fun sample(c: Context, event: String) {
        val i = battery(c) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level < 0) -1 else level * 100 / scale
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            0 -> "none"
            else -> "other"
        }
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        // µA on most devices (some firmwares give mA): shown in mA, + = into the battery.
        val cur = runCatching { bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
            ?.takeIf { it != Long.MIN_VALUE }?.let { if (kotlin.math.abs(it) > 20_000) it / 1000 else it }
        val soc = runCatching { com.shelf.launcher.device.tune.TuneHardware.socTempC() }.getOrNull()
        val screen = (c.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val row = listOf(
            time, event, pct.toString(), plug, status, temp?.let { "%.1f".format(Locale.US, it) } ?: "",
            soc?.let { "%.1f".format(Locale.US, it) } ?: "", if (volt > 0) volt.toString() else "", cur?.toString() ?: "",
            if (screen) "1" else "0", if (inFront) "voltcap" else "other"
        ).joinToString(",")
        val f = file(c) ?: return
        if (!f.exists()) f.writeText(HEADER + "\n")
        f.appendText(row + "\n")
    }

    /** Rows older than 7 days go (once at start). */
    private fun trim(c: Context) {
        val f = file(c) ?: return
        if (!f.exists()) return
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val lines = f.readLines()
        val kept = lines.drop(1).filter { l -> runCatching { fmt.parse(l.substringBefore(','))!!.time >= cutoff }.getOrDefault(false) }
        if (kept.size != lines.size - 1) f.writeText((listOf(HEADER) + kept).joinToString("\n", postfix = "\n"))
    }

    /** For Diagnostics: the hottest charge in the log ("41.5 °C battery, 63 °C chip — 20 Oct 22:14, charger, screen off"), or null. */
    fun summary(c: Context): String? {
        val f = file(c) ?: return null
        if (!f.exists()) return null
        val rows = f.readLines().drop(1).map { it.split(',') }.filter { it.size >= 11 && it[3] != "none" }
        if (rows.isEmpty()) return null
        val hot = rows.maxByOrNull { it[5].toFloatOrNull() ?: -99f } ?: return null
        val socMax = rows.mapNotNull { it[6].toFloatOrNull() }.maxOrNull()
        val hours = rows.count { it[1] == "tick" } / 60f
        return "Hottest: battery ${hot[5]} °C at ${hot[0].substring(5, 16)} (${hot[3]}, ${if (hot[9] == "1") "screen on" else "screen off"})" +
            (socMax?.let { " · chip up to ${"%.0f".format(Locale.US, it)} °C" } ?: "") +
            " · %.1f h of charging logged (last 7 days)".format(Locale.US, hours)
    }
}
