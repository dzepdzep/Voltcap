package com.shelf.launcher.device

import android.content.Context
import android.os.Build
import android.util.Log

/** What a vendor adapter can really do today. Only ones with code behind them are declared by adapters. */
enum class VendorCapability(val label: String) {
    RGB_STICKS("RGB stick lights"),
    DUAL_SCREEN("Dual screen"),
    PERFORMANCE_MODES("Performance modes"),
    FAN_CONTROL("Fan control"),
    VENDOR_BATTERY("Vendor battery")
}

/** The Build.* fields detection is allowed to look at. Nothing else, so it stays deterministic. */
data class BuildFingerprint(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String
) {
    /** Also used to notice that saved prefs were restored onto another device. */
    val deviceKey: String get() = "$manufacturer|$model|$device"

    companion object {
        fun current() = BuildFingerprint(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty()
        )
    }
}

/** A vendor route to lights the Android lights API cannot see (e.g. handheld stick LEDs). */
interface LightsBackend {
    fun isAvailable(): Boolean
    /** [color] as RGB int at [brightness] 0-100; color 0 turns the lights off. */
    fun set(color: Int, brightness: Int)
    /** Which driver was found (blocking; Settings › Device). */
    fun describe(): String? = null
}

interface VendorAdapter {
    val id: String
    val displayName: String
    fun matches(info: BuildFingerprint): Boolean
    /** A friendly name only for models known exactly; null means "show Build.MODEL". */
    fun marketingName(model: String): String?
    val capabilities: Set<VendorCapability>
    fun lights(): LightsBackend? = null
    fun extraInfo(context: Context): List<Pair<String, String>> = emptyList()
    /** Accessories with battery levels a vendor can really read (merged into the status bar). None implement it yet. */
    fun accessoryBatteries(context: Context): List<com.shelf.launcher.device.accessories.Accessory> = emptyList()
    /** A vendor route to the foreground game's FPS (performance overlay). None implement it yet. */
    fun fpsSource(context: Context): com.shelf.launcher.data.perf.FpsSource? = null
}

private fun BuildFingerprint.field(name: String) =
    manufacturer.equals(name, true) || brand.equals(name, true)

/** Lower-case letters and digits only, so "Odin 2 Portal", "Odin2Portal" and "AYN Odin2 Portal" compare equal. */
private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

class AynAdapter : VendorAdapter {
    override val id = "ayn"
    override val displayName = "AYN"
    override fun matches(info: BuildFingerprint) = info.field("AYN")
    // Exact (after dropping spaces/case and an optional "AYN" prefix) - anything else falls back to Build.MODEL.
    private val models = mapOf(
        "odin2" to "AYN Odin 2",
        "odin2portal" to "AYN Odin 2 Portal",
        "odin2mini" to "AYN Odin 2 Mini",
        "thor" to "AYN Thor"
    )
    override fun marketingName(model: String): String? = models[normalize(model).removePrefix("ayn")]
    override val capabilities = setOf(VendorCapability.RGB_STICKS)
    override fun lights(): LightsBackend = AynSticks

    /** AYN's stick LEDs, through its system command service ("PServerBinder"). */
    private object AynSticks : LightsBackend {
        private const val TAG = "AynAdapter"
        private val binder: android.os.IBinder? by lazy {
            runCatching {
                val sm = Class.forName("android.os.ServiceManager")
                sm.getDeclaredMethod("getService", String::class.java).invoke(null, "PServerBinder") as? android.os.IBinder
            }.getOrNull()
        }

        override fun isAvailable(): Boolean = binder != null

        private sealed class Layout {
            class Sn3112(val sides: List<String>) : Layout()
            class MultiColor(val leds: List<String>) : Layout()
            class Channels(val zones: List<Pair<String, List<Pair<Char, String>>>>) : Layout()
        }

        @Volatile private var layoutCache: Layout? = null
        @Volatile private var probed = false
        /**
         * Update 200: Voltcap switched the SN3112 drivers' `enable` off, so the next colour switches them back on.
         * Starts true: if Voltcap was closed while the lights were off, its first colour still switches them back on.
         */
        @Volatile private var enableHeld = true

        /** Which LED driver this AYN model has (Odin 2: SN3112; Thor: LED class devices). Found once. */
        private fun layout(): Layout? {
            if (probed) return layoutCache
            probed = true
            val sh = { cmd: String -> com.shelf.launcher.device.RootShell.exec(cmd).orEmpty() }
            val sn = listOf("sn3112l", "sn3112r").filter { sh("ls /sys/class/$it/led/brightness 2>/dev/null").contains("brightness") }
            layoutCache = if (sn.isNotEmpty()) Layout.Sn3112(sn) else {
                // Update 201: `ls -d` printed one LED per line and the root service returns only the first line; the glob
                // prints them all on one line.
                val leds = sh("echo /sys/class/leds/*").split(Regex("\\s+")).map { it.trim() }
                    .filter { it.startsWith("/sys/class/leds/") && !it.contains('*') }
                val wanted = { n: String -> val l = n.lowercase(); listOf("stick", "joy", "rgb", "ring", "led_l", "led_r", "left", "right").any { it in l } }
                // Every RGB LED device except the obviously unrelated ones: on the Thor the left stick's LED
                // has a name none of the words above match, so "Off" only reached the right side.
                val unrelated = { n: String -> val l = n.substringAfterLast('/').lowercase(); listOf("backlight", "flash", "torch", "wlan", "bt", "charg", "batt", "vibrator", "mmc", "kbd", "lcd", "white").any { it in l } }
                val multi = leds.filter { !unrelated(it) && sh("ls $it/multi_intensity 2>/dev/null").contains("multi_intensity") }
                    .sortedByDescending { wanted(it) }
                if (multi.isNotEmpty()) Layout.MultiColor(multi) else {
                    val chans = leds.filter(wanted).mapNotNull { path ->
                        val name = path.substringAfterLast('/').lowercase()
                        val ch = when {
                            name.contains("red") || name.endsWith("_r") || name.endsWith(":r") -> 'r'
                            name.contains("green") || name.endsWith("_g") || name.endsWith(":g") -> 'g'
                            name.contains("blue") || name.endsWith("_b") || name.endsWith(":b") -> 'b'
                            else -> null
                        } ?: return@mapNotNull null
                        val zone = name.replace(Regex("(red|green|blue|[_:][rgb])$"), "")
                        Triple(zone, ch, path)
                    }.groupBy { it.first }.map { (zone, list) -> zone to list.map { it.second to it.third } }
                    if (chans.isNotEmpty()) Layout.Channels(chans) else null
                }
            }
            return layoutCache
        }

        /** For Settings › Device: which light driver was found. */
        override fun describe(): String = when (val l = layout()) {
            is Layout.Sn3112 -> "Stick lights: SN3112 (${l.sides.size} sides)"
            is Layout.MultiColor -> "Stick lights: ${l.leds.size} RGB LEDs (" + l.leds.joinToString { it.substringAfterLast('/') } + ")"
            is Layout.Channels -> "Stick lights: ${l.zones.size} zones (red / green / blue LEDs)"
            null -> "Stick lights: no driver found"
        }

        /** [color] at [brightness] (0-100) on both sticks, both rings. */
        override fun set(color: Int, brightness: Int) {
            val b = binder ?: return
            // The driver ignores its own brightness field, so dimming is done
            // by scaling the colour itself.
            val level = brightness.coerceIn(0, 100)
            val r = (color shr 16 and 0xFF) * level / 100
            val g = (color shr 8 and 0xFF) * level / 100
            val bl = (color and 0xFF) * level / 100
            val value = "$r:$g:$bl:255"
            val commands: List<String> = when (val kind = layout()) {
                // Odin 2 family: two SN3112 drivers, two rings each.
                // Update 200: the AYN Thor has the same two SN3112 drivers (/sys/class/sn3112l and sn3112r, checked
                // on a Thor: its /sys/class/leds only holds the PMIC red/green/blue, flash and torch LEDs), so it
                // takes this route too. Each driver also has an `enable` switch, which AYN's own light code uses per
                // side (its setting is "joystick_light_enabled=L,R" on the Thor): a black colour alone left one stick
                // lit on the Thor when the lights timer ran out, so "off" now also switches both drivers' `enable` off,
                // and the next colour switches them back on (only if Voltcap switched them off).
                is Layout.Sn3112 -> {
                    val off = r + g + bl == 0
                    val zones = kind.sides.flatMap { side ->
                        listOf(1, 2).map { zone -> "echo $zone-$value > /sys/class/$side/led/brightness" }
                    }
                    val enables = when {
                        off -> kind.sides.map { side -> "echo 0 > /sys/class/$side/led/enable" }.also { enableHeld = true }
                        enableHeld -> kind.sides.map { side -> "echo 1 > /sys/class/$side/led/enable" }.also { enableHeld = false }
                        else -> emptyList()
                    }
                    if (off) zones + enables else enables + zones
                }
                // Thor and newer firmware: standard multicolour LED class devices.
                is Layout.MultiColor -> kind.leds.flatMap { led ->
                    listOf("echo $r $g $bl > $led/multi_intensity", "echo ${if (r + g + bl == 0) 0 else 255} > $led/brightness")
                }
                // Separate red / green / blue LEDs per zone.
                is Layout.Channels -> kind.zones.flatMap { (_, chans) ->
                    chans.map { (ch, path) ->
                        val v = when (ch) { 'r' -> r; 'g' -> g; else -> bl }
                        "echo $v > $path/brightness"
                    }
                }
                null -> return
            }
            // Update 201: AYN's root service cuts a command after 255 characters (see RootShell). The SN3112 "off" chain
            // (four rings + two enables, ~300 characters) lost both `enable` writes, and the first colour after it lost the
            // last ring — so update 200's "switch both drivers off" never ran. Sent in pieces that fit, in order (one-way
            // calls to the same binder are delivered in order).
            val batches = ArrayList<String>()
            val sb = StringBuilder()
            commands.forEach { c ->
                if (sb.isNotEmpty() && sb.length + 3 + c.length > com.shelf.launcher.device.RootShell.MAX_COMMAND) { batches += sb.toString(); sb.setLength(0) }
                if (sb.isNotEmpty()) sb.append(" ; ")
                sb.append(c)
            }
            if (sb.isNotEmpty()) batches += sb.toString()
            batches.forEach { command ->
                val data = android.os.Parcel.obtain()
                val reply = android.os.Parcel.obtain()
                try {
                    data.writeStringArray(arrayOf(command, "1"))
                    b.transact(0, data, reply, android.os.IBinder.FLAG_ONEWAY)
                } catch (e: Exception) {
                    Log.w(TAG, "AYN stick lights failed", e)
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}

class RetroidAdapter : VendorAdapter {
    override val id = "retroid"
    override val displayName = "Retroid"
    override fun matches(info: BuildFingerprint) =
        listOf(info.manufacturer, info.brand, info.model).any { it.contains("retroid", ignoreCase = true) }
    override fun marketingName(model: String): String? = null
    override val capabilities = emptySet<VendorCapability>()
}

class AyaneoAdapter : VendorAdapter {
    override val id = "ayaneo"
    override val displayName = "AYANEO"
    override fun matches(info: BuildFingerprint) = info.field("AYANEO")
    override fun marketingName(model: String): String? = null
    override val capabilities = emptySet<VendorCapability>()
}

class AnberNicAdapter : VendorAdapter {
    override val id = "anbernic"
    override val displayName = "Anbernic"
    override fun matches(info: BuildFingerprint) = info.field("Anbernic")
    override fun marketingName(model: String): String? = null
    override val capabilities = emptySet<VendorCapability>()
}

class GenericAdapter : VendorAdapter {
    override val id = "generic"
    override val displayName = "Generic Android"
    override fun matches(info: BuildFingerprint) = true
    override fun marketingName(model: String): String? = null
    override val capabilities = emptySet<VendorCapability>()
}

object DeviceProfiles {
    /** Specific adapters in match order; [GenericAdapter] is the fallback and always last. */
    val all: List<VendorAdapter> = listOf(AynAdapter(), RetroidAdapter(), AyaneoAdapter(), AnberNicAdapter(), GenericAdapter())

    @Volatile private var cached: VendorAdapter? = null

    /** Pure detection from Build fields; the first matching adapter wins. */
    fun detect(info: BuildFingerprint = BuildFingerprint.current()): VendorAdapter =
        all.first { it.matches(info) }

    /** The adapter in effect: the user's override if valid for this device, else detection. Cached per process. */
    fun current(context: Context): VendorAdapter {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val store = DeviceProfileStore(context.applicationContext)
                val detected = detect()
                store.recordDetected(detected.id)
                (store.override()?.let { id -> all.firstOrNull { it.id == id } } ?: detected).also { cached = it }
            }
        }
    }

    /** Forces [adapterId], or back to automatic detection when null. */
    fun setOverride(context: Context, adapterId: String?): VendorAdapter {
        DeviceProfileStore(context.applicationContext).setOverride(adapterId?.takeIf { id -> all.any { it.id == id } })
        synchronized(this) { cached = null }
        return current(context)
    }
}
