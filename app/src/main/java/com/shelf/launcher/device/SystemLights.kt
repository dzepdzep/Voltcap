package com.shelf.launcher.device

import android.content.Context

/**
 * The handheld's own stick-light setting (the RGB switch / mode in Android's Quick Settings on AYN handhelds).
 *
 * While it's on, the firmware keeps putting its own colour back, so Voltcap's "Off" (Controller lights › Off)
 * only blanked the lights for a moment and the system colour returned. With "Off" chosen, Voltcap now also
 * switches the system's own light setting off — through the firmware's root service, every light-related key
 * it finds — and remembers what each was, to put it back exactly when you pick a colour or "System setting"
 * again.
 *
 * Only keys that are clearly about the stick / RGB lights are touched, and only plain numbers
 * (0 = off): never screen brightness, the notification light or button backlights.
 */
object SystemLights {
    private const val PREFS = "system_lights"
    private val KEY = Regex("(?i)(rgb|joystick[_]?light|stick[_]?light|led[_]?(light|mode|switch|enable|effect|state)|light[_]?(mode|effect|switch|enable)|breath)")
    private val NOT = Regex("(?i)(screen|display|notification|button|key|backlight|brightness_mode|torch|flash|battery|charg)")

    /** Update 200: a plain number or a comma list of them ("1" on the Odin 2 Portal, "1,1" = left,right on the Thor). */
    private val NUMS = Regex("-?[0-9]+(,-?[0-9]+)*")
    private fun isOff(v: String) = v.split(',').all { it.trim() == "0" }
    private fun offOf(v: String) = v.split(',').joinToString(",") { "0" }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Switches the system's light keys off (once; remembered). Blocking, off the main thread. */
    @Synchronized
    fun holdOff(c: Context) {
        if (!RootShell.available) return
        val p = prefs(c)
        if (p.getBoolean("held", false)) {
            // Already off: if the firmware switched one back on, off again (cheap: only the saved keys).
            p.all.keys.filter { it.contains('/') }.forEach { k ->
                val (table, key) = k.split('/', limit = 2)
                val now = RootShell.exec("settings get $table $key")?.trim()
                if (now != null && now.matches(NUMS) && !isOff(now)) RootShell.exec("settings put $table $key ${offOf(now)}")
            }
            return
        }
        val e = p.edit()
        for (table in listOf("system", "secure", "global")) {
            val list = RootShell.exec("settings list $table") ?: continue
            list.lines().forEach { line ->
                val key = line.substringBefore('=', "").trim()
                val value = line.substringAfter('=', "").trim()
                if (key.isEmpty() || !key.matches(Regex("[A-Za-z0-9_.]+"))) return@forEach
                if (!KEY.containsMatchIn(key) || NOT.containsMatchIn(key)) return@forEach
                // Update 200: also a list of numbers, one per side: the AYN Thor keeps its stick lights in
                // joystick_light_enabled=1,1 (left,right) and has no separate left_/right_ keys like the Odin 2 Portal,
                // so its key was skipped as "not a number" and nothing was held off there. "1,1" goes to "0,0".
                if (!value.matches(NUMS) || isOff(value)) return@forEach
                e.putString("$table/$key", value)
                RootShell.exec("settings put $table $key ${offOf(value)}")
            }
        }
        e.putBoolean("held", true).apply()
    }

    /** Puts every key [holdOff] changed back the way it was. Blocking. */
    @Synchronized
    fun release(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("held", false)) return
        if (RootShell.available) {
            p.all.forEach { (k, v) ->
                if (!k.contains('/')) return@forEach
                val (table, key) = k.split('/', limit = 2)
                val value = (v as? String)?.takeIf { it.matches(NUMS) } ?: return@forEach
                RootShell.exec("settings put $table $key $value")
            }
        }
        p.edit().clear().apply()
    }

    /** For the lights card: which system keys are held off right now. */
    fun heldKeys(c: Context): List<String> = prefs(c).all.keys.filter { it.contains('/') }.map { it.substringAfter('/') }
}
