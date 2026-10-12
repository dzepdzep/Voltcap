package com.shelf.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.shelf.launcher.device.RootShell
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Hiding the white gesture-navigation pill while Voltcap is open (Settings › Device › Gesture hint).
 *
 * Two layers:
 *  1. **Hide navigation bar** (on by default): normal Android immersive mode. Voltcap's window — and every
 *     Voltcap popup (see ui/SystemBars.kt) — asks Android to hide the navigation bar; a swipe from the edge shows
 *     it for a moment and it hides again by itself. Gestures, the keyboard, dialogs and the overlays work as
 *     usual, and nothing changes outside Voltcap.
 *  2. **Force-hide gesture hint** (off by default): for devices that still draw the pill. It switches the
 *     device's own hidden "gesture hint" setting off while Voltcap is in front — Samsung-style
 *     `global navigation_bar_gesture_hint 0`, Xiaomi-style `global hide_gesture_line 1` — and puts the value it
 *     had back when Voltcap goes to the background or the option is turned off. Only the drawn hint changes:
 *     gesture navigation itself is never touched.
 *
 * No Shizuku (removed in update 37: nothing extra has to run). Writing the setting needs one of:
 *  - the handheld's own root service (AYN / Retroid PServer, [RootShell]): works with no setup, and Voltcap
 *    also uses it once to give itself WRITE_SECURE_SETTINGS for good ([grantWithRoot]);
 *  - WRITE_SECURE_SETTINGS granted once from a computer ([ADB_GRANT]); it stays after reboots and updates.
 *
 * Device-only: its prefs file "gesture_hint" never travels to another device (SettingsScopes).
 * All shell / settings work runs on one background thread, in order, so a quick in-and-out can't
 * leave the hint in the wrong state.
 */
object GestureHint {
    private const val PREFS = "gesture_hint"

    /** The one-time command for devices without a root service (run from a computer with adb). */
    const val ADB_GRANT = "adb shell pm grant com.shelf.launcher android.permission.WRITE_SECURE_SETTINGS"

    /** A device's own hint setting: which table, which key, and the value that hides the hint. */
    private class HintKey(val table: String, val key: String, val hidden: String, val shown: String)

    private val CANDIDATES = listOf(
        // Update 136: AYN's ROM (Odin 2 / Thor) draws its own home handle ("SecondaryHomeHandle") over full-screen apps
        // even with the navigation bar hidden; its own "hide navigation bar" setting is what removes it. First, so an
        // AYN handheld uses it rather than the Samsung key below (which it also has, but which doesn't hide the handle).
        HintKey("global", "hide_nav_bar", hidden = "1", shown = "0"),
        HintKey("global", "navigation_bar_gesture_hint", hidden = "0", shown = "1"), // Samsung One UI, some handheld ROMs
        HintKey("global", "hide_gesture_line", hidden = "1", shown = "0")            // MIUI / HyperOS
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Layer 1: immersive ----

    /** Compose state, so Voltcap's window and its open popups follow the switch at once. */
    val immersiveState = mutableStateOf(true)
    private var loaded = false

    /** Reads the switch once (main thread, quick); call before the first window shows. */
    fun init(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        immersiveState.value = p.getBoolean("immersive", true)
        // Older choice (Off / While Voltcap is open / Everywhere) → the force-hide switch.
        if (!p.contains("force") && p.contains("scope")) {
            p.edit().putBoolean("force", p.getString("scope", "OFF") != "OFF").remove("scope").apply()
        }
        // The older version saved the value as a number for navigation_bar_gesture_hint.
        (p.all["previous"] as? Int)?.let { p.edit().putString("previous", it.toString()).putString("previous_key", "navigation_bar_gesture_hint").apply() }
        // Left hidden by a crash or a killed process with the option off: give the hint back.
        val app = c.applicationContext
        worker.execute { runCatching { if (!forceOn(app)) restoreNow(app) } }
    }

    fun immersive(c: Context): Boolean = prefs(c).getBoolean("immersive", true)

    fun setImmersive(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("immersive", on).apply()
        immersiveState.value = on
        // (Update 137: where the force-hide follows this switch, the handle goes / comes back with it.)
        val app = c.applicationContext
        if (!prefs(app).contains("force")) worker.execute {
            runCatching { if (forceOn(app) && foreground) { grantWithRoot(app); hideNow(app, retries = 2) } else if (!forceOn(app)) restoreNow(app) }
        }
    }

    // ---- Layer 2: force-hide (the device setting) ----

    /**
     * Update 137: on a handheld with its own root service (AYN's Odin 2 / Thor) the force-hide is on by default together
     * with Hide navigation bar — there the pill is AYN's own home handle, which only AYN's "hide_nav_bar" setting
     * removes (checked on an Odin 2 Portal: with it 1, the handle's window is gone). Turning it off yourself still wins.
     */
    fun forceOn(c: Context): Boolean {
        val p = prefs(c)
        return if (p.contains("force")) p.getBoolean("force", false) else p.getBoolean("immersive", true) && RootShell.available
    }

    /** The option changed. Off puts back the value the system had. */
    fun setForce(c: Context, on: Boolean) {
        val app = c.applicationContext
        prefs(app).edit().putBoolean("force", on).apply()
        worker.execute {
            runCatching {
                // On a handheld with a root service: the permission for good, once (no other app needed).
                if (on) grantWithRoot(app)
                if (on && foreground) hideNow(app, retries = 0) else if (!on) restoreNow(app)
            }
        }
    }

    @Volatile private var foreground = false
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "gesture-hint").apply { isDaemon = true } }
    private var pendingRestore: ScheduledFuture<*>? = null

    /** Voltcap's home came to the front (onStart). */
    fun onForeground(c: Context) {
        val app = c.applicationContext
        foreground = true
        worker.execute {
            pendingRestore?.cancel(false); pendingRestore = null
            runCatching { if (forceOn(app)) { grantWithRoot(app); hideNow(app, retries = 4) } }
        }
    }

    /**
     * Voltcap's home left the front (onStop: a game started, Home on another launcher, screen off…).
     * A short delay, so passing through one of Voltcap's own screens doesn't flash the hint.
     */
    fun onBackground(c: Context) {
        val app = c.applicationContext
        foreground = false
        worker.execute {
            pendingRestore?.cancel(false)
            pendingRestore = worker.schedule({ runCatching { if (!foreground) restoreNow(app) } }, 700, TimeUnit.MILLISECONDS)
        }
    }

    private fun hideNow(c: Context, retries: Int) {
        val k = hintKey(c) ?: return
        val p = prefs(c)
        val now = read(c, k)
        // Remember the device's own value before Voltcap first changes it.
        if (!p.contains("previous") && now != k.hidden) p.edit().putString("previous", now ?: k.shown).putString("previous_key", k.key).apply()
        if (now != k.hidden && !write(c, k, k.hidden) && retries > 0) {
            worker.schedule({ runCatching { if (foreground && forceOn(c)) hideNow(c, retries - 1) } }, 1200, TimeUnit.MILLISECONDS)
        }
    }

    private fun restoreNow(c: Context) {
        val p = prefs(c)
        if (!p.contains("previous")) return
        val key = p.getString("previous_key", null)
        val k = CANDIDATES.firstOrNull { it.key == key } ?: hintKey(c) ?: return
        val value = p.getString("previous", k.shown) ?: k.shown
        if (read(c, k) == value || write(c, k, value)) p.edit().remove("previous").remove("previous_key").apply()
    }

    /** The hint setting this device has (found once, then remembered), or null when none / not readable yet. */
    private fun hintKey(c: Context): HintKey? {
        val p = prefs(c)
        // (Update 136: a key remembered before AYN's was known is looked for again, once.)
        if (p.getInt("keys_v", 0) < 2) p.edit().remove("key").putInt("keys_v", 2).apply()
        p.getString("key", null)?.let { saved -> CANDIDATES.firstOrNull { it.key == saved }?.let { return it } }
        for (k in CANDIDATES) {
            if (read(c, k) != null) { p.edit().putString("key", k.key).apply(); return k }
        }
        return null
    }

    /** A setting's value; null when the device doesn't have it (or nothing may read it yet). Blocking. */
    private fun read(c: Context, k: HintKey): String? {
        // Directly first (Android 12+ may refuse hidden keys to apps), then through the root service.
        runCatching {
            val v = when (k.table) {
                "global" -> Settings.Global.getString(c.contentResolver, k.key)
                "secure" -> Settings.Secure.getString(c.contentResolver, k.key)
                else -> Settings.System.getString(c.contentResolver, k.key)
            }
            if (v != null) return v.trim()
        }
        if (!RootShell.available) return null
        val out = RootShell.exec("settings get ${k.table} ${k.key}")?.trim()
        return if (out.isNullOrEmpty() || out == "null") null else out
    }

    private fun write(c: Context, k: HintKey, value: String): Boolean {
        if (canWriteDirectly(c) && k.table == "global") {
            if (runCatching { Settings.Global.putString(c.contentResolver, k.key, value) }.getOrDefault(false)) return true
        }
        if (RootShell.available) {
            RootShell.exec("settings put ${k.table} ${k.key} $value") ?: return false
            return read(c, k) == value
        }
        return false
    }

    fun canWriteDirectly(c: Context): Boolean =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    /**
     * On a handheld with a root service: gives Voltcap WRITE_SECURE_SETTINGS for good (the same as the adb
     * command), so the force-hide keeps working even where the root service can't read the setting back.
     * Blocking. True when Voltcap has the permission afterwards.
     */
    fun grantWithRoot(c: Context): Boolean {
        if (canWriteDirectly(c)) return true
        if (!RootShell.available) return false
        RootShell.exec("pm grant ${c.packageName} android.permission.WRITE_SECURE_SETTINGS")
        return canWriteDirectly(c)
    }

    /** No way to write the setting here yet: the computer command is the only route. */
    fun needsAdb(c: Context): Boolean = !canWriteDirectly(c) && !RootShell.available

    /** Where the force-hide stands, for the settings row. Blocking (may ask the root service): call off the main thread. */
    fun status(c: Context): String {
        val k = hintKey(c)
        val hiddenNow = k != null && read(c, k) == k.hidden
        val direct = canWriteDirectly(c)
        return when {
            k != null && (direct || RootShell.available) ->
                (if (direct) "Voltcap can change it directly." else "Works through the handheld's own root service, nothing to set up.") +
                    if (hiddenNow) " Hint hidden now." else ""
            k == null && (direct || RootShell.available) ->
                "This device has no gesture-hint setting Voltcap knows. Immersive mode is all there is here."
            else -> "Needs a one-time permission from a computer (command below). No extra app has to run."
        }
    }

    /** The force-hide can do something here right now (a known key and a way to write it). Blocking. */
    fun forceAvailable(c: Context): Boolean = hintKey(c) != null && (canWriteDirectly(c) || RootShell.available)
}
