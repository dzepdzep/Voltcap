package com.shelf.launcher.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.InputDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** One "Label — value" line; a null value means the system did not tell us. */
data class InfoRow(val label: String, val value: String?)

data class InfoSection(val title: String, val rows: List<InfoRow>)

/**
 * A snapshot of what this device reports. Every value comes from a system API
 * or a readable kernel file; nothing is guessed, and anything unreadable is
 * simply null ("Not available" in the UI).
 */
data class DeviceInfo(val sections: List<InfoSection>) {

    /** Plain text for the clipboard. Contains no account data, IDs or serial numbers. */
    fun toText(): String = sections.joinToString("\n\n") { s ->
        s.title + "\n" + s.rows.joinToString("\n") { "${it.label}: ${it.value ?: "Not available"}" }
    }

    companion object {
        suspend fun collect(context: Context): DeviceInfo = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            DeviceInfo(
                listOf(
                    guarded("Device") { deviceSection(app) },
                    guarded("Processor") { processorSection() },
                    guarded("Graphics") { graphicsSection(app) },
                    guarded("Memory & storage") { memorySection(app) },
                    guarded("Android") { androidSection() },
                    guarded("Displays") { displaySection(app) },
                    guarded("Battery") { batterySection(app) },
                    guarded("Controllers") { controllerSection(app) }
                )
            )
        }

        private inline fun guarded(title: String, block: () -> List<InfoRow>): InfoSection =
            InfoSection(title, runCatching(block).getOrDefault(listOf(InfoRow("Details", null))))

        private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) }

        // ---- Device ----

        private fun deviceSection(context: Context): List<InfoRow> {
            val adapter = DeviceProfiles.current(context)
            return listOf(
                InfoRow("Marketing name", adapter.marketingName(Build.MODEL.orEmpty())),
                InfoRow("Manufacturer", clean(Build.MANUFACTURER)),
                InfoRow("Brand", clean(Build.BRAND)),
                InfoRow("Model", clean(Build.MODEL)),
                InfoRow("Device codename", clean(Build.DEVICE)),
                InfoRow("Product", clean(Build.PRODUCT)),
                InfoRow("Board", clean(Build.BOARD)),
                InfoRow("Hardware", clean(Build.HARDWARE))
            )
        }

        // ---- Processor ----

        /** Exact code -> marketing name. Anything not listed is shown as the raw code. */
        private val SOC_NAMES = mapOf(
            "SM8650" to "Snapdragon 8 Gen 3",
            "SM8550" to "Snapdragon 8 Gen 2",
            "SM8475" to "Snapdragon 8+ Gen 1",
            "SM8450" to "Snapdragon 8 Gen 1",
            "SM8250" to "Snapdragon 865",
            "SM7325" to "Snapdragon 778G",
            "SM6115" to "Snapdragon 662",
            "MT6789" to "MediaTek Helio G99",
            "T618" to "Unisoc T618",
            "T820" to "Unisoc T820",
            "RK3566" to "Rockchip RK3566",
            "RK3588" to "Rockchip RK3588"
        )

        private fun socRows(): List<InfoRow> {
            var code: String? = null
            var vendor: String? = null
            var source: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                code = clean(Build.SOC_MODEL)
                vendor = clean(Build.SOC_MANUFACTURER)
                if (code != null) source = "Android"
            }
            if (code == null) {
                code = cpuInfoHardware()
                if (code != null) source = "/proc/cpuinfo"
            }
            val rows = mutableListOf<InfoRow>()
            if (code != null) {
                val name = SOC_NAMES[code.uppercase(Locale.ROOT)]
                rows += InfoRow("Chipset", if (name != null) "$name ($code)" else code)
                rows += InfoRow("Chipset source", source)
                if (vendor != null) rows += InfoRow("Chipset maker", vendor)
            } else {
                rows += InfoRow("Chipset", null)
            }
            // The board name is the platform the firmware was built for; it is not necessarily the chipset.
            rows += InfoRow("Platform", clean(Build.BOARD))
            return rows
        }

        private fun cpuInfoHardware(): String? = runCatching {
            File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                    ?.substringAfter(':')?.let { clean(it) }
            }
        }.getOrNull()

        /** e.g. "4× 2.0 GHz + 3× 2.8 GHz + 1× 3.2 GHz", from the cores whose max frequency is readable. */
        private fun clusters(): String? {
            val freqs = runCatching {
                File("/sys/devices/system/cpu").listFiles().orEmpty()
                    .filter { it.name.matches(Regex("cpu\\d+")) }
                    .mapNotNull { runCatching { File(it, "cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull() }
            }.getOrDefault(emptyList())
            if (freqs.isEmpty()) return null
            return freqs.groupingBy { it }.eachCount().toSortedMap().entries.joinToString(" + ") { (khz, n) ->
                val ghz = String.format(Locale.US, "%.2f", khz / 1_000_000.0).trimEnd('0').let { if (it.endsWith(".")) it + "0" else it }
                "$n× $ghz GHz"
            }
        }

        private fun processorSection(): List<InfoRow> = socRows() + listOf(
            InfoRow("CPU cores", Runtime.getRuntime().availableProcessors().toString()),
            InfoRow("Max frequencies", clusters()),
            InfoRow("ABIs", Build.SUPPORTED_ABIS.orEmpty().joinToString(", ").ifBlank { null })
        )

        // ---- Graphics ----

        private class Gl(val renderer: String?, val vendor: String?, val version: String?)

        /** A 1x1 pbuffer context, made and destroyed on the calling (background) thread. */
        private fun queryGl(): Gl? {
            var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var surface: EGLSurface = EGL14.EGL_NO_SURFACE
            var ctx: EGLContext = EGL14.EGL_NO_CONTEXT
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (display == EGL14.EGL_NO_DISPLAY) return null
                val ver = IntArray(2)
                if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return null
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                val attribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE
                )
                if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] < 1) return null
                val config = configs[0] ?: return null
                surface = EGL14.eglCreatePbufferSurface(
                    display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
                )
                ctx = EGL14.eglCreateContext(
                    display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
                )
                if (surface == EGL14.EGL_NO_SURFACE || ctx == EGL14.EGL_NO_CONTEXT) return null
                if (!EGL14.eglMakeCurrent(display, surface, surface, ctx)) return null
                return Gl(
                    clean(GLES20.glGetString(GLES20.GL_RENDERER)),
                    clean(GLES20.glGetString(GLES20.GL_VENDOR)),
                    clean(GLES20.glGetString(GLES20.GL_VERSION))
                )
            } catch (_: Throwable) {
                return null
            } finally {
                // eglTerminate is deliberately not called: the default display is shared with the
                // app's own renderer, and terminating it would pull the rug from under Compose.
                runCatching {
                    if (display != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                        if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, ctx)
                    }
                    EGL14.eglReleaseThread()
                }
            }
        }

        private fun vulkan(context: Context): String? = runCatching {
            val v = context.packageManager.systemAvailableFeatures
                ?.filter { it.name == "android.hardware.vulkan.version" }
                ?.maxOfOrNull { it.version } ?: return@runCatching null
            "${v shr 22}.${(v shr 12) and 0x3FF}.${v and 0xFFF}"
        }.getOrNull()

        private fun graphicsSection(context: Context): List<InfoRow> {
            val gl = queryGl()
            return listOf(
                InfoRow("GPU", gl?.renderer),
                InfoRow("GPU vendor", gl?.vendor),
                InfoRow("OpenGL ES", gl?.version),
                InfoRow("Vulkan", vulkan(context)?.let { "Supported, version $it" } ?: "Not reported by the system")
            )
        }

        // ---- Memory & storage ----

        private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

        private fun space(path: String): String? = runCatching {
            val s = StatFs(path)
            "${gb(s.availableBlocksLong * s.blockSizeLong)} free of ${gb(s.blockCountLong * s.blockSizeLong)}"
        }.getOrNull()

        private fun memorySection(context: Context): List<InfoRow> {
            val rows = mutableListOf<InfoRow>()
            val mem = ActivityManager.MemoryInfo()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                am.getMemoryInfo(mem)
                rows += InfoRow("RAM total", gb(mem.totalMem))
                rows += InfoRow("RAM available", gb(mem.availMem) + if (mem.lowMemory) " (system reports low memory)" else "")
            } else {
                rows += InfoRow("RAM", null)
            }
            rows += InfoRow("Internal storage", space(Environment.getDataDirectory().path))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
                sm?.storageVolumes.orEmpty().filter { it.isRemovable || !it.isPrimary }.forEach { v ->
                    val desc = runCatching { v.getDescription(context) }.getOrNull() ?: "Storage"
                    val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) v.directory else null
                    val state = if (v.state == Environment.MEDIA_MOUNTED) space(dir?.path ?: "") else null
                    rows += InfoRow(desc, state ?: if (v.state == Environment.MEDIA_MOUNTED) "Mounted" else v.state)
                }
            }
            return rows
        }

        // ---- Android ----

        private fun androidSection(): List<InfoRow> = listOf(
            InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            InfoRow("Security patch", if (Build.VERSION.SDK_INT >= 23) clean(Build.VERSION.SECURITY_PATCH) else null),
            InfoRow("Build ID", clean(Build.ID)),
            InfoRow("Build", clean(Build.DISPLAY)),
            InfoRow("Kernel", clean(System.getProperty("os.version")))
        )

        // ---- Displays ----

        private fun hdrName(t: Int) = when (t) {
            1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "type $t"
        }

        @Suppress("DEPRECATION")
        private fun displaySection(context: Context): List<InfoRow> {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return listOf(InfoRow("Displays", null))
            val rows = mutableListOf<InfoRow>()
            dm.displays.forEach { d ->
                val tags = buildList {
                    if (d.displayId == Display.DEFAULT_DISPLAY) add("default")
                    if (d.flags and Display.FLAG_PRESENTATION != 0) add("presentation")
                    if (d.displayId != Display.DEFAULT_DISPLAY && d.flags and Display.FLAG_PRESENTATION == 0) add("second")
                }
                val m = DisplayMetrics()
                d.getRealMetrics(m)
                val rates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    d.supportedModes.map { Math.round(it.refreshRate) }.distinct().sorted().joinToString(", ") { "$it" }.ifBlank { null }
                } else null
                val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    d.hdrCapabilities?.supportedHdrTypes?.takeIf { it.isNotEmpty() }?.joinToString(", ") { hdrName(it) } ?: "None reported"
                } else null
                val parts = listOfNotNull(
                    "${m.widthPixels}×${m.heightPixels} px",
                    "${m.densityDpi} dpi",
                    String.format(Locale.US, "%.0f Hz now", d.refreshRate),
                    rates?.let { "modes: $it Hz" },
                    hdr?.let { "HDR: $it" }
                )
                rows += InfoRow("${d.name} · id ${d.displayId}" + if (tags.isEmpty()) "" else " (${tags.joinToString(", ")})", parts.joinToString(" · "))
            }
            return rows.ifEmpty { listOf(InfoRow("Displays", null)) }
        }

        // ---- Battery ----

        private fun batterySection(context: Context): List<InfoRow> {
            val i: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return listOf(InfoRow("Battery", null))
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else null
            val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> null
            }
            val plugged = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                0 -> "Not plugged in"
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                8 -> "Dock"
                else -> null
            }
            val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }?.let { String.format(Locale.US, "%.1f °C", it / 10.0) }
            val volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { it > 0 }?.let { "$it mV" }
            val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                else -> null
            }
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            fun prop(id: Int) = bm?.getIntProperty(id)?.takeIf { it != Int.MIN_VALUE && it != 0 }
            val counter = prop(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.let { "${it / 1000} mAh (as reported by the system)" }
            val current = prop(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let { "${it / 1000} mA (as reported by the system)" }
            return listOf(
                InfoRow("Level", pct),
                InfoRow("Status", status),
                InfoRow("Power source", plugged),
                InfoRow("Temperature", temp),
                InfoRow("Voltage", volt),
                InfoRow("Health", health),
                InfoRow("Technology", clean(i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY))),
                InfoRow("Charge counter", counter),
                InfoRow("Current now", current)
            )
        }

        // ---- Controllers ----

        private fun controllerSection(context: Context): List<InfoRow> {
            val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager ?: return listOf(InfoRow("Controllers", null))
            val pads = im.inputDeviceIds.toList().mapNotNull { im.getInputDevice(it) }.filter { d ->
                !d.isVirtual && ((d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
            }
            if (pads.isEmpty()) return listOf(InfoRow("Connected", "None found"))
            return pads.map { d ->
                InfoRow(d.name, String.format(Locale.US, "Vendor 0x%04X · Product 0x%04X", d.vendorId, d.productId))
            }
        }
    }
}
