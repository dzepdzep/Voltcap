package com.shelf.launcher.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shelf.launcher.device.RootShell
import java.util.concurrent.Executors

/**
 * Sleep mode for the radios (Settings › Device › Sleep mode), in the spirit of SleepManager: a while
 * after the screen goes off, Wi-Fi and/or Bluetooth are switched off; when the screen comes back on,
 * exactly what was switched off is switched back on - never something you'd turned off yourself.
 *
 * Skipped while charging, while music plays (streaming / Bluetooth audio), while a save sync runs and,
 * for Bluetooth, while headphones or a speaker are connected. The wait ([graceMinutes]) means a quick
 * screen-off (a pocket, a short pause) changes nothing.
 *
 * Switching radios needs system rights: on AYN handhelds Voltcap has them through the firmware's own
 * service ("enhanced access"); elsewhere the setting says it isn't available. A wake-up alarm (not a
 * plain timer) starts it, so it still happens while the handheld sleeps.
 */
object SleepRadios {
    private const val PREFS = "sleep_radios"
    private const val TAG_ALARM = "voltcap.sleepRadios"

    data class Settings(
        val enabled: Boolean = false,
        val wifi: Boolean = true,
        val bluetooth: Boolean = true,
        /** Minutes after the screen goes off. */
        val graceMinutes: Int = 5,
        /** Leave everything on while charging. */
        val skipWhileCharging: Boolean = true
    )

    val GRACE_CHOICES = listOf(1, 5, 15, 30)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Update 144: what sleep mode switched off here (and the last-sleep line) lives in its own file that is never
    // exported, so a settings transfer can't make another handheld switch radios on. Older versions kept it in [PREFS].
    private fun state(c: Context) = c.applicationContext.getSharedPreferences("sleep_radios_state", Context.MODE_PRIVATE)

    fun load(c: Context): Settings {
        val p = prefs(c)
        return Settings(
            enabled = p.getBoolean("enabled", false),
            wifi = p.getBoolean("wifi", true),
            bluetooth = p.getBoolean("bluetooth", true),
            graceMinutes = p.getInt("grace", 5),
            skipWhileCharging = p.getBoolean("skip_charging", true)
        )
    }

    fun save(c: Context, s: Settings) {
        prefs(c).edit()
            .putBoolean("enabled", s.enabled).putBoolean("wifi", s.wifi).putBoolean("bluetooth", s.bluetooth)
            .putInt("grace", s.graceMinutes).putBoolean("skip_charging", s.skipWhileCharging)
            .apply()
        init(c)
    }

    /** Radios need system rights (AYN's firmware service). */
    val available: Boolean get() = RootShell.available

    /** One background thread: radio commands are blocking binder calls. */
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var alarm: AlarmManager.OnAlarmListener? = null
    private var retries = 0
    // (Update 125: more tries — a song still fading out or a sync could use up the four and leave the radios on.)
    private const val MAX_RETRIES = 10

    /** What the last screen-off switched off (restored on screen-on), with when, for the settings card. */
    fun lastAction(c: Context): String? = state(c).getString("last_action", null) ?: prefs(c).getString("last_action", null)

    /**
     * Starts or stops listening for screen on/off, following the setting. Cheap; call at start-up and
     * whenever the setting changes. Also restores anything left off by a previous run (crash, update).
     */
    fun init(c: Context) {
        val app = c.applicationContext
        val s = load(app)
        val want = s.enabled && available
        if (want && receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> { retries = 0; schedule(app) }
                        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> { cancel(app); restore(app) }
                    }
                }
            }
            runCatching {
                app.registerReceiver(r, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                })
                receiver = r
            }
        } else if (!want && receiver != null) {
            runCatching { app.unregisterReceiver(receiver) }
            receiver = null
            cancel(app)
        }
        // Screen is on now (we're being set up from the UI): nothing should stay off.
        if (com.shelf.launcher.ui.ScreenState.isInteractive(app)) restore(app)
    }

    private fun schedule(app: Context) {
        cancel(app)
        val s = load(app)
        if (!s.enabled || (!s.wifi && !s.bluetooth)) return
        if (android.os.Build.VERSION.SDK_INT < 24) return
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val listener = AlarmManager.OnAlarmListener {
            alarm = null
            // Skipped for now (music, charging, a sync): look again after another grace period.
            // At most a few times per sleep, so it never turns into a regular wake-up.
            worker.execute {
                if (sleepNow(app)) main.post {
                    if (retries < MAX_RETRIES && !com.shelf.launcher.ui.ScreenState.isInteractive(app)) { retries++; schedule(app) }
                }
            }
        }
        alarm = listener
        val at = SystemClock.elapsedRealtime() + s.graceMinutes.coerceIn(1, 120) * 60_000L
        // Update 125: an exact wake-up (a listener alarm needs no extra permission); a plain one could be put off by
        // Android for a long time, so the radios sometimes stayed on.
        runCatching { am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, TAG_ALARM, listener, main) }
            .recoverCatching { am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, TAG_ALARM, listener, main) }
            .onFailure { alarm = null }
    }

    private fun cancel(app: Context) {
        val l = alarm ?: return
        alarm = null
        runCatching { (app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(l) }
    }

    /**
     * The grace period is over: switch off what's on and allowed to go off, remembering what that was.
     * Returns true when it held off for now (charging, music, a sync) and should look again later.
     */
    private fun sleepNow(app: Context): Boolean {
        if (com.shelf.launcher.ui.ScreenState.isInteractive(app)) return false
        val s = load(app)
        if (!s.enabled) return false
        // (Update 125: system rights not ready yet — e.g. the firmware's service still connecting: try again later.)
        if (!available) return true
        if (s.skipWhileCharging && charging(app)) return true
        // Music playing (an online stream, Bluetooth headphones): leave both radios alone.
        if (musicPlaying(app)) return true
        var retry = (s.wifi && CloudSync.busy) || (s.bluetooth && audioDeviceConnected(app))
        val turnedOff = mutableListOf<String>()
        if (s.wifi && !CloudSync.busy && wifiOn(app)) {
            // (Update 125: the other command when the first one isn't there, and a second go below if it's still on.)
            val ok = RootShell.exec("svc wifi disable") ?: RootShell.exec("cmd wifi set-wifi-enabled disabled")
            if (ok != null) turnedOff += "wifi"
        }
        if (s.bluetooth && bluetoothOn() && !audioDeviceConnected(app)) {
            val ok = RootShell.exec("svc bluetooth disable") ?: RootShell.exec("cmd bluetooth_manager disable")
            if (ok != null) turnedOff += "bluetooth"
        }
        // Still on a moment later (a radio that was busy): once more the other way, else look again later.
        if (turnedOff.isNotEmpty()) runCatching { Thread.sleep(2500) }
        if ("wifi" in turnedOff && wifiOn(app)) { RootShell.exec("cmd wifi set-wifi-enabled disabled"); retry = true }
        if ("bluetooth" in turnedOff && bluetoothOn()) { RootShell.exec("cmd bluetooth_manager disable"); retry = true }
        if (turnedOff.isEmpty()) return retry
        val p = state(app)
        val already = p.getStringSet("off", emptySet()).orEmpty()
        p.edit()
            .putStringSet("off", already + turnedOff)
            .putString("last_action", "Last sleep: switched off ${turnedOff.joinToString(" and ") { if (it == "wifi") "Wi-Fi" else "Bluetooth" }} at ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())}")
            .apply()
        // The screen came on while the radios were going off: restore() may already have run and
        // found nothing to switch back on.
        if (com.shelf.launcher.ui.ScreenState.isInteractive(app)) main.post { restore(app) }
        return retry
    }

    /** Screen back on: switch back on exactly what [sleepNow] switched off. */
    private fun restore(app: Context) {
        val p = state(app)
        val old = prefs(app)
        val off = p.getStringSet("off", emptySet()).orEmpty() + old.getStringSet("off", emptySet()).orEmpty()
        if (off.isEmpty()) return
        p.edit().remove("off").apply()
        if (old.contains("off")) old.edit().remove("off").apply()
        worker.execute {
            // Update 144: like switching off, check a moment later and use the other command when the first one did
            // nothing (RootShell gives "" even then, so the ?: never ran) - Wi-Fi used to stay off on such firmware.
            if ("wifi" in off) RootShell.exec("svc wifi enable")
            if ("bluetooth" in off) RootShell.exec("svc bluetooth enable")
            runCatching { Thread.sleep(1500) }
            if ("wifi" in off && !wifiOn(app)) RootShell.exec("cmd wifi set-wifi-enabled enabled")
            if ("bluetooth" in off && !bluetoothOn()) RootShell.exec("cmd bluetooth_manager enable")
            // Still off after both: keep it listed, so the next screen-on (or unlock) tries again.
            runCatching { Thread.sleep(2500) }
            val still = off.filter { (it == "wifi" && !wifiOn(app)) || (it == "bluetooth" && !bluetoothOn()) }
            if (still.isNotEmpty()) p.edit().putStringSet("off", p.getStringSet("off", emptySet()).orEmpty() + still).apply()
        }
    }

    private fun charging(c: Context): Boolean = runCatching {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL ||
            (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }.getOrDefault(false)

    private fun musicPlaying(c: Context): Boolean =
        runCatching { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive }.getOrDefault(false)

    private fun wifiOn(c: Context): Boolean = runCatching {
        (c.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled
    }.getOrDefault(true)

    @Suppress("DEPRECATION", "MissingPermission")
    private fun bluetoothOn(): Boolean =
        runCatching { android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(true)

    /** Headphones / a speaker on Bluetooth (an audio output); controllers don't count. */
    private fun audioDeviceConnected(c: Context): Boolean = runCatching {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (android.os.Build.VERSION.SDK_INT >= 31 && it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }.getOrDefault(false)
}
