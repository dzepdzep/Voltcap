package com.shelf.launcher.data

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Build
import android.util.Log
import android.view.InputDevice
import androidx.annotation.RequiresApi
import com.shelf.launcher.device.DeviceProfiles
import com.shelf.launcher.device.LightsBackend
import java.io.File

/**
 * RGB lights on controllers and handheld sticks.
 *
 * Three routes, tried together:
 *  0. AYN handhelds (Odin 2 / Odin 2 Portal …): the stick LEDs sit behind
 *     AYN's own system service ("PServerBinder"), which runs a command that
 *     writes the colour to /sys/class/sn3112l and sn3112r. Android's lights
 *     API doesn't see them at all. The service name and command format were
 *     worked out by the open-source Bifrost app (Pollux-MoonBench/Bifrost) —
 *     this is our own implementation of that interface.
 *  1. Android's own controller-lights API (Android 12+). Works wherever the
 *     driver reports its lights to Android. Every input device is checked —
 *     built-in handheld controls often show up as a keyboard or "gpio-keys"
 *     device rather than a gamepad, which is why the old gamepad-only scan
 *     found nothing on some handhelds.
 *  2. The kernel's LED class (/sys/class/leds) for stick/joystick LEDs whose
 *     files the firmware leaves writable. Silently skipped when they aren't.
 *
 * Android only honours an app's light request while that app is on screen, so
 * games keep the device's normal lights; Shelf's colour comes back with Shelf.
 */
object ControllerLights {

    private const val TAG = "ControllerLights"
    private var aynTouched = false

    /** The vendor's own route (e.g. AYN stick LEDs), if this device's profile has one. */
    private fun vendor(context: Context): LightsBackend? =
        runCatching { DeviceProfiles.current(context).lights() }.getOrNull()
            // Before the adapter layer this route was found by capability (the AYN
            // service exists), not by brand: keep that, so a firmware reporting an
            // unexpected manufacturer still gets its stick lights.
            ?: runCatching { com.shelf.launcher.device.AynAdapter().lights().takeIf { it.isAvailable() } }.getOrNull()
    private val sessions = mutableListOf<Any>() // LightsManager.LightsSession, kept untyped for old Android

    /** Whether anything this app can colour was found. */
    fun isAvailable(context: Context): Boolean =
        runCatching { describe(context).isNotEmpty() }.getOrDefault(false)

    /**
     * Human-readable list of what was found ("Xbox Wireless Controller · RGB",
     * "sysfs: joystick_l"), shown under the lights controls so it's clear
     * what Shelf can and can't reach on this device.
     */
    fun describe(context: Context): List<String> {
        val out = mutableListOf<String>()
        vendor(context)?.takeIf { it.isAvailable() }?.let { v -> out += (runCatching { v.describe() }.getOrNull() ?: "AYN stick lights") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                devices(context).forEach { device ->
                    device.lightsManager.lights.filter { it.isControllable() }.forEach { light ->
                        val kind = when {
                            light.hasRgbControl() -> "RGB"
                            light.hasBrightnessControl() -> "brightness"
                            else -> "on/off"
                        }
                        out += "${device.name} · ${light.name.ifBlank { "light ${light.id}" }} ($kind)"
                    }
                }
            }
        }
        sysfsLeds().forEach { out += "LED ${it.name}" }
        return out
    }

    /**
     * Sets every controllable light to [color] at [brightness] (0-100), or
     * turns them off. [enabled] = false hands them back to the system.
     */
    // (update 91) apply / pulse / endPulse / release run on IO, Default and the main thread at once (resend loop,
    // unlock flash, music pulse, TuneService): one at a time, so the session lists are never changed concurrently.
    @Synchronized
    fun apply(context: Context, enabled: Boolean, on: Boolean, color: Int, brightness: Int) {
        if (enabled) {
            val v = vendor(context)
            runCatching { v?.set(if (on) color else 0, brightness) }
            aynTouched = v?.isAvailable() == true
        } else if (aynTouched) {
            // Handed back to the system ("System setting"): Voltcap stops writing. The handheld's own colour
            // comes back the next time its firmware applies it. (Voltcap's own "Off" is enabled + on = false,
            // re-sent while on screen, so it stays off.)
            aynTouched = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { applyS(context, enabled, on, color, brightness) }
                .onFailure { Log.w(TAG, "Lights API failed", it) }
        }
        if (enabled) runCatching { applySysfs(on, color, brightness) }
    }

    // ---- Update 308: screen off / on (AYN Thor) ----

    /** Whether this handheld's stick lights are put back by Voltcap after the screen comes on (the AYN SN3112 route). */
    fun restoresOnWake(context: Context): Boolean = runCatching { vendor(context)?.restoresOnWake() == true }.getOrDefault(false)

    /** Whether the handheld's own light setting has a stick lit (null: unknown). Not blocking. */
    fun systemLightsOn(context: Context): Boolean? = runCatching { vendor(context)?.systemOn(context) }.getOrNull()

    /**
     * Update 308 (Toni: "the RGB lights stay off after turning the screen off and back on"): the screen came back on —
     * the lights as they were before it went off, once, after the firmware's own wake-up writes ([wasOn]: they were lit;
     * nothing is switched on that was off, or that you switched off since). [enabled]: Voltcap runs the lights (its
     * colour, [on] = not held off), else the handheld's own setting is put back as the system has it. Blocking.
     */
    @Synchronized
    fun restoreAfterWake(context: Context, wasOn: Boolean, enabled: Boolean, on: Boolean, color: Int, brightness: Int) {
        val v = vendor(context)?.takeIf { it.restoresOnWake() } ?: return
        if (!wasOn) return
        if (enabled) {
            if (!on) return
            v.rearm()
            apply(context, true, on = true, color = color, brightness = brightness)
        } else if (v.systemOn(context) == true) {
            v.restoreSystem(context)
        }
    }

    /**
     * Blinks the lights [times] times in [color] at full brightness, then puts
     * back [restoreColor] / [restoreBrightness]. Used for achievement
     * unlocks and the intro. The AYN stick lights also react while a game is
     * running; Android's own lights API only while Shelf is on screen.
     */
    suspend fun flash(
        context: Context,
        color: Int,
        times: Int,
        restoreColor: Int,
        restoreBrightness: Int,
        onMs: Long = 170,
        offMs: Long = 130
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // (QA pass 3: off the main thread — on the AYN handhelds a step goes through the root shell, a binder call that
        // held the home screen up 60 ms on each flash, e.g. right after start.)
        repeat(times) {
            apply(context, enabled = true, on = true, color = color, brightness = 100)
            kotlinx.coroutines.delay(onMs)
            apply(context, enabled = true, on = false, color = color, brightness = 0)
            kotlinx.coroutines.delay(offMs)
        }
        apply(context, enabled = true, on = true, color = restoreColor, brightness = restoreBrightness)
    }

    // ---- Music pulse: many small updates, so the light route is looked up once and reused ----

    private var pulseVendor: LightsBackend? = null
    private var pulseVendorChecked = false
    private var pulseSysfs: List<SysLed>? = null
    private val pulseSessions = HashMap<Int, Any>() // device id -> LightsManager.LightsSession
    private var lastPulse = -1

    /**
     * One step of "Lights pulse with the music": [color] at [brightness] (0-100). Cheap enough to call
     * ~10 times a second: same-looking steps are skipped, the vendor route and LED list are found once,
     * and Android's lights session is kept open. Call [endPulse] when the music stops.
     */
    @Synchronized
    fun pulse(context: Context, color: Int, brightness: Int) {
        val level = (brightness.coerceIn(0, 100) / 8) * 8
        val key = (color and 0xFFFFFF) * 31 + level
        if (key == lastPulse) return
        lastPulse = key
        if (!pulseVendorChecked) {
            pulseVendor = vendor(context)?.takeIf { it.isAvailable() }
            pulseVendorChecked = true
        }
        runCatching { pulseVendor?.set(color, level) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { pulseS(context, color, level) }
        val leds = pulseSysfs ?: sysfsLeds().also { pulseSysfs = it }
        if (leds.isNotEmpty()) runCatching { applySysfs(true, color, level, leds) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun pulseS(context: Context, color: Int, level: Int) {
        val a = (level * 255 / 100).coerceIn(0, 255)
        val argb = if (a > 0) (a shl 24) or (color and 0xFFFFFF) else 0
        devices(context).forEach { device ->
            val manager = device.lightsManager
            val targets = manager.lights.filter { it.isControllable() }
            if (targets.isEmpty()) return@forEach
            val session = (pulseSessions[device.id] as? LightsManager.LightsSession)
                ?: manager.openSession().also { pulseSessions[device.id] = it }
            val request = LightsRequest.Builder().apply {
                targets.forEach { light -> addLight(light, LightState.Builder().setColor(argb).build()) }
            }.build()
            session.requestLights(request)
        }
    }

    /** The music stopped: drop the pulse session; the normal colour is put back by the caller. */
    @Synchronized
    fun endPulse() {
        lastPulse = -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pulseSessions.values.forEach { runCatching { (it as? LightsManager.LightsSession)?.close() } }
        }
        pulseSessions.clear()
    }

    /** Gives the lights back to the system (e.g. when the feature is switched off). */
    @Synchronized
    fun release() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching { releaseS() }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun releaseS() {
        sessions.forEach { runCatching { (it as? LightsManager.LightsSession)?.close() } }
        sessions.clear()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyS(context: Context, enabled: Boolean, on: Boolean, color: Int, brightness: Int) {
        if (!enabled) { releaseS(); return }
        val level = (brightness.coerceIn(0, 100) * 255 / 100)
        val rgb = color and 0xFFFFFF
        // Android reads the alpha byte as the light's brightness; the RGB part
        // stays the full colour so dim settings don't turn it muddy.
        val scaled = run {
            val r = (rgb shr 16 and 0xFF) * level / 255
            val g = (rgb shr 8 and 0xFF) * level / 255
            val b = (rgb and 0xFF) * level / 255
            (r shl 16) or (g shl 8) or b
        }
        val argbRgb = if (on && level > 0) (level shl 24) or rgb else 0
        val argbMono = if (on && level > 0) (level shl 24) or scaled else 0

        // A fresh session per change: requests on an old one are sometimes
        // ignored after the app has been in the background.
        releaseS()
        devices(context).forEach { device ->
            val manager = device.lightsManager
            val targets = manager.lights.filter { it.isControllable() }
            if (targets.isEmpty()) return@forEach
            val session = manager.openSession()
            val request = LightsRequest.Builder().apply {
                targets.forEach { light ->
                    val value = if (light.hasRgbControl()) argbRgb else argbMono
                    addLight(light, LightState.Builder().setColor(value).build())
                }
            }.build()
            session.requestLights(request)
            sessions += session
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun Light.isControllable(): Boolean =
        type != Light.LIGHT_TYPE_PLAYER_ID &&
            (hasRgbControl() || hasBrightnessControl() || type == Light.LIGHT_TYPE_INPUT)

    /** Every input device — not only ones calling themselves gamepads. */
    private fun devices(context: Context): List<InputDevice> {
        val input = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        return input.inputDeviceIds.toList().mapNotNull { input.getInputDevice(it) }.filter { !it.isVirtual }
    }

    // ---- Kernel LED class fallback ----

    private val LED_NAME_HINTS = listOf("joy", "stick", "rgb", "gamepad", "multicolor", "ring")

    private data class SysLed(val name: String, val dir: File)

    private fun sysfsLeds(): List<SysLed> = runCatching {
        File("/sys/class/leds").listFiles().orEmpty()
            .filter { dir -> LED_NAME_HINTS.any { dir.name.contains(it, ignoreCase = true) } }
            .filter { dir ->
                File(dir, "brightness").canWrite() || File(dir, "multi_intensity").canWrite()
            }
            .map { SysLed(it.name, it) }
    }.getOrDefault(emptyList())

    private fun applySysfs(on: Boolean, color: Int, brightness: Int, found: List<SysLed>? = null) {
        val leds = found ?: sysfsLeds()
        if (leds.isEmpty()) return
        val r = color shr 16 and 0xFF
        val g = color shr 8 and 0xFF
        val b = color and 0xFF
        leds.forEach { led ->
            val max = runCatching { File(led.dir, "max_brightness").readText().trim().toInt() }.getOrDefault(255)
            val level = if (on) brightness.coerceIn(0, 100) * max / 100 else 0
            val multi = File(led.dir, "multi_intensity")
            if (multi.canWrite()) {
                // Channel order comes from multi_index ("red green blue" usually).
                val order = runCatching { File(led.dir, "multi_index").readText().trim().split(Regex("\\s+")) }
                    .getOrDefault(listOf("red", "green", "blue"))
                val values = order.map {
                    when (it.lowercase()) { "red" -> r; "green" -> g; "blue" -> b; else -> 0 }
                }
                runCatching { multi.writeText(values.joinToString(" ")) }
                runCatching { File(led.dir, "brightness").writeText(level.toString()) }
            } else {
                // Single-colour LED named after its channel, e.g. "joystick_red".
                val channel = when {
                    led.name.contains("red", true) -> r
                    led.name.contains("green", true) -> g
                    led.name.contains("blue", true) -> b
                    else -> 255
                }
                runCatching { File(led.dir, "brightness").writeText((level * channel / 255).toString()) }
            }
        }
    }
}
