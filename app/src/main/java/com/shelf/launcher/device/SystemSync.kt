package com.shelf.launcher.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps Voltcap's screens in step with changes made outside them — Android's own Quick Settings
 * (refresh rate, Wi-Fi, Bluetooth, Do not disturb, airplane mode, the handheld's fan / performance
 * tiles, the volume keys) and Voltcap's own Quick Settings tiles (power, underclock, overlay), which
 * used to show up only after Voltcap was left and opened again (pulling the shade down over Voltcap
 * doesn't pause it).
 *
 * Everything that can change from outside bumps [version]; screens that show such a value read it again
 * when it changes (the R1 menu, Settings › Performance, a game's performance profile, the in-game panel,
 * the mixer). Bumps are gathered (150 ms) so a burst of changes is one re-read.
 *
 * It also hands changes to the tuner instead of letting it undo them: a fan mode picked in Android's shade
 * becomes the tuner's ([com.shelf.launcher.device.tune.Tuner.onSystemFanMode]), and a refresh rate or button
 * layout picked during a game with its own isn't put back when the game ends
 * ([com.shelf.launcher.device.tune.GameExtras.onSystemChange]).
 */
object SystemSync {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val main = Handler(Looper.getMainLooper())
    private val bumpNow = Runnable { _version.value = _version.value + 1 }
    @Volatile private var started = false

    /** Something changed: screens showing it read it again (gathered: one re-read per burst). */
    fun bump() {
        main.removeCallbacks(bumpNow)
        main.postDelayed(bumpNow, 150)
    }

    // Kept here: SharedPreferences holds its listeners weakly.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> bump() }
    private val prefsWatched = ArrayList<SharedPreferences>()

    /** The settings Android's shade (and the handheld's own tiles) change, as (table, key). */
    private val WATCHED = listOf(
        "system" to "peak_refresh_rate", "system" to "min_refresh_rate", "system" to "fan_mode",
        "system" to "performance_mode", "system" to "flip_button_layout", "system" to "is_charging_separation",
        "system" to "screen_brightness", "system" to "screen_brightness_mode",
        "global" to "airplane_mode_on", "global" to "zen_mode", "global" to "wifi_on", "global" to "bluetooth_on"
    )

    /** Once per process (Voltcap's start, its tiles and panels call it). Cheap. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val resolver = app.contentResolver
        val observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                bump()
                when (val key = uri?.lastPathSegment) {
                    "fan_mode" -> Thread { runCatching { com.shelf.launcher.device.tune.Tuner.onSystemFanMode(app) } }.start()
                    // Picked in the shade during a game with its own rate / layout: not undone afterwards.
                    "peak_refresh_rate", "min_refresh_rate", "flip_button_layout" ->
                        Thread { runCatching { com.shelf.launcher.device.tune.GameExtras.onSystemChange(app, key) } }.start()
                }
            }
        }
        WATCHED.forEach { (table, key) ->
            val uri = if (table == "global") Settings.Global.getUriFor(key) else Settings.System.getUriFor(key)
            runCatching { resolver.registerContentObserver(uri, false, observer) }
        }
        // Refresh rate and resolution changes on the screen itself.
        runCatching {
            (app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = bump()
                override fun onDisplayRemoved(displayId: Int) = bump()
                override fun onDisplayChanged(displayId: Int) = bump()
            }, main)
        }
        // Wi-Fi, Bluetooth, Do not disturb, the volume keys.
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = bump()
            }, IntentFilter().apply {
                addAction("android.net.wifi.WIFI_STATE_CHANGED")
                addAction("android.bluetooth.adapter.action.STATE_CHANGED")
                addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction("android.media.VOLUME_CHANGED_ACTION")
            }, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        // Voltcap's own settings changed by its Quick Settings tiles, notification or in-game panel.
        listOf("tuner", "perf_settings", "charge_limit", "mixer").forEach { name ->
            runCatching {
                val p = app.getSharedPreferences(name, Context.MODE_PRIVATE)
                p.registerOnSharedPreferenceChangeListener(prefsListener)
                prefsWatched += p
            }
        }
    }
}
