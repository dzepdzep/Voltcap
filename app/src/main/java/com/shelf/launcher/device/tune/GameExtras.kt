package com.shelf.launcher.device.tune

import android.content.Context
import com.shelf.launcher.device.ChargeLimiter
import com.shelf.launcher.device.DeviceControls

/**
 * The parts of a game's own profile that aren't clocks: screen refresh rate, button layout and the
 * charge limit. Set when the game starts, and put back to what they were when you return.
 *
 * What to put back is also saved to disk ("game_extras", internal): Android often closes Voltcap while
 * a heavy game runs, and the buttons used to stay on the game's layout (Xbox / Retro) afterwards.
 */
object GameExtras {
    private const val PREFS = "game_extras"
    private data class Saved(val hz: Float?, val xbox: Boolean?, val limit: Boolean?)
    @Volatile private var saved: Saved? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What the running game's profile set (to tell Voltcap's own writes from changes made in Android's shade). */
    @Volatile private var appliedHz: Int? = null
    @Volatile private var appliedXbox: Boolean? = null

    /**
     * Android's shade (or the handheld's own tile) changed the refresh rate or the button layout while a
     * game runs with its own: that's the player's choice now, so it isn't undone when the game ends.
     * Blocking (a root read for the button layout).
     */
    fun onSystemChange(context: Context, key: String) {
        val s = saved ?: load(context) ?: return
        when (key) {
            "peak_refresh_rate", "min_refresh_rate" -> {
                val want = appliedHz ?: return
                // The screen takes a moment to switch (also after Voltcap's own change): look once it has.
                Thread.sleep(1_500L)
                if (appliedHz != want) return
                val now = Math.round(DeviceControls.currentRefreshRate(context))
                if (s.hz != null && kotlin.math.abs(now - want) > 1) update(context, s.copy(hz = null))
            }
            "flip_button_layout" -> {
                val want = appliedXbox ?: return
                val now = DeviceControls.controllerIsXbox() ?: return
                if (s.xbox != null && now != want) update(context, s.copy(xbox = null))
            }
        }
    }

    private fun update(context: Context, s: Saved) {
        saved = s
        store(context, s)
    }

    fun apply(context: Context, p: TuneProfile) {
        if (p.refreshHz <= 0 && p.buttonLayout == null && p.chargeLimit == null) return
        runCatching {
            // A game started straight after another: keep the values from before the first one.
            val earlier = saved ?: load(context)
            val prevHz = if (p.refreshHz > 0) earlier?.hz ?: DeviceControls.currentRefreshRate(context) else earlier?.hz
            val prevXbox = if (p.buttonLayout != null) earlier?.xbox ?: DeviceControls.controllerIsXbox() else earlier?.xbox
            val prevLimit = if (p.chargeLimit != null) earlier?.limit ?: ChargeLimiter.isOn(context) else earlier?.limit
            val s = Saved(prevHz, prevXbox, prevLimit)
            saved = s
            store(context, s)
            appliedHz = p.refreshHz.takeIf { it > 0 }
            appliedXbox = p.buttonLayout?.let { it == "XBOX" }
            if (p.refreshHz > 0) DeviceControls.setRefreshRate(p.refreshHz.toFloat())
            p.buttonLayout?.let { DeviceControls.setControllerXbox(it == "XBOX") }
            p.chargeLimit?.let { want -> if (ChargeLimiter.isOn(context) != want) ChargeLimiter.toggle(context) }
        }
    }

    /** Back in Voltcap: put back what the game changed (also after Voltcap was closed in between). */
    fun restore(context: Context) {
        val s = saved ?: load(context) ?: return
        saved = null
        appliedHz = null
        appliedXbox = null
        prefs(context).edit().clear().apply()
        runCatching {
            s.hz?.let { DeviceControls.setRefreshRate(it) }
            s.xbox?.let { DeviceControls.setControllerXbox(it) }
            s.limit?.let { want -> if (ChargeLimiter.isOn(context) != want) ChargeLimiter.toggle(context) }
        }
    }

    private fun store(context: Context, s: Saved) {
        prefs(context).edit()
            .putBoolean("pending", true)
            .putFloat("hz", s.hz ?: -1f)
            .putString("xbox", s.xbox?.let { if (it) "1" else "0" } ?: "")
            .putString("limit", s.limit?.let { if (it) "1" else "0" } ?: "")
            .commit()
    }

    private fun load(context: Context): Saved? {
        val p = prefs(context)
        if (!p.getBoolean("pending", false)) return null
        fun flag(k: String) = when (p.getString(k, "")) { "1" -> true; "0" -> false; else -> null }
        return Saved(p.getFloat("hz", -1f).takeIf { it > 0f }, flag("xbox"), flag("limit"))
    }
}
