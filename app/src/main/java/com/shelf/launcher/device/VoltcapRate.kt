package com.shelf.launcher.device

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 0.6.15: "Voltcap at the screen's fastest rate" (Settings › Device › Screen rate). While Voltcap is in front it asks
 * for the screen's highest refresh rate (120 Hz on the Thor, Odin 2 Portal, Flip 2); games and other apps keep the
 * rate Android (or their own profile) gives them.
 *
 * Two parts:
 *  - Voltcap's window asks for the fastest mode (any device). Android only grants it up to its own limit: a handheld
 *    set to 60 Hz in Android's Display settings stays at 60 for every app, Voltcap too ([status] says so).
 *  - On handhelds whose rate Voltcap can set (AYN's root service, as the R1 tile does): the rate goes up when Voltcap
 *    comes to the front and back to what it was before a game starts or Voltcap leaves the front. What to put back is
 *    kept on disk, so it's put back after Voltcap was closed too.
 * Its own prefs file (not Features): a device setting, never carried by layout presets or backups.
 */
object VoltcapRate {
    private const val PREFS = "voltcap_rate"
    private const val KEY_ON = "on"
    private const val KEY_SAVED = "saved_hz"

    /** The switch, as Compose state (read once from the prefs in [load]). */
    val on = mutableStateOf(false)
    @Volatile private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        on.value = prefs(c).getBoolean(KEY_ON, false)
        loaded = true
    }

    /** Switched in Settings. Off: the window stops asking, and a raised rate goes back now. */
    fun set(activity: Activity?, c: Context, value: Boolean) {
        load(c)
        on.value = value
        prefs(c).edit().putBoolean(KEY_ON, value).apply()
        activity?.let { applyWindow(it) }
        val app = c.applicationContext
        Thread { runCatching { if (value) enter(app) else leave(app) } }.start()
    }

    /** The fastest refresh rate the screen offers at its current resolution (null: only one). */
    fun fastest(c: Context): Float? = DeviceControls.refreshRates(c).takeIf { it.size > 1 }?.maxOrNull()

    /** Voltcap's window asks for the fastest mode (or nothing in particular when off). Main thread. */
    fun applyWindow(activity: Activity) {
        load(activity)
        runCatching {
            val w = activity.window ?: return
            val d = if (android.os.Build.VERSION.SDK_INT >= 30) activity.display else @Suppress("DEPRECATION") w.windowManager.defaultDisplay
            d ?: return
            val mode = d.mode
            val want = if (on.value) d.supportedModes
                .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
                .maxByOrNull { it.refreshRate }?.modeId ?: 0 else 0
            val lp = w.attributes
            if (lp.preferredDisplayModeId != want) {
                lp.preferredDisplayModeId = want
                w.attributes = lp
            }
        }
    }

    /**
     * Voltcap is in front (no game): raise the rate where Voltcap can set it, keeping what to put back. Blocking
     * (root service). Does nothing when off, when the rate already is the fastest, or where it can't be set.
     */
    @Synchronized
    fun enter(c: Context) {
        load(c)
        if (!on.value || !RootShell.available) return
        val max = fastest(c) ?: return
        val now = DeviceControls.currentRefreshRate(c)
        if (now >= max - 1f) return
        // Kept from an earlier raise (Voltcap closed meanwhile): that one is still what to put back.
        val p = prefs(c)
        if (p.getFloat(KEY_SAVED, -1f) <= 0f) p.edit().putFloat(KEY_SAVED, now).commit()
        DeviceControls.setRefreshRate(max)
    }

    /** A game starts / Voltcap leaves the front: the rate from before [enter]. Blocking (root service). */
    @Synchronized
    fun leave(c: Context) {
        val p = prefs(c)
        val saved = p.getFloat(KEY_SAVED, -1f)
        if (saved <= 0f) return
        p.edit().remove(KEY_SAVED).commit()
        if (RootShell.available) DeviceControls.setRefreshRate(saved)
    }

    /** The rate was changed by hand (R1's screen-rate tile) while raised: that's the one to keep, nothing to put back. */
    fun forget(c: Context) {
        prefs(c).edit().remove(KEY_SAVED).apply()
    }

    /** What Settings says under the switch. Blocking only for the root check (cached by RootShell). */
    fun status(c: Context): String {
        load(c)
        val max = fastest(c) ?: return "This screen has one refresh rate only."
        val now = DeviceControls.currentRefreshRate(c)
        val hz = "${max.toInt()} Hz"
        return when {
            !on.value -> "Off: Voltcap runs at the rate Android uses (now ${now.toInt()} Hz)."
            RootShell.available -> "On: $hz while Voltcap is in front; games get back the rate they had."
            now >= max - 1f -> "On: $hz in Voltcap."
            else -> "Android keeps this handheld at ${now.toInt()} Hz for every app. To let Voltcap use $hz, raise the " +
                "refresh rate in Android's Settings › Display (games then follow that setting too)."
        }
    }
}
