package com.shelf.launcher.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Is this a slower handheld (Retroid Pocket Classic and friends)? Decides what
 * Settings › Performance › "Automatic" means: on these, Voltcap uses its Lite
 * effects (no continuously animated glows, cheap blur, no hero upscaling) so
 * scrolling stays smooth. Worked out once per process.
 */
object DevicePerformance {
    @Volatile private var cached: Boolean? = null

    /** Handhelds known to be on the slow side, by normalised model name. */
    private val KNOWN_LOW_END = listOf(
        "pocketclassic", "rpclassic", "retroidpocket2", "retroidpocket3", "rp2s", "rp3plus",
        "rg353", "rg405", "rg505", "rg556", "rg557", "rg35xx", "rgcube", "miyoo", "trimui", "powkiddy",
        // Konkr handhelds (Pocket Fit and friends): fine for games, but the animated home screen lagged.
        "konkr", "pocketfit"
    )

    fun isLowEnd(context: Context): Boolean = cached ?: detect(context.applicationContext).also { cached = it }

    private fun detect(context: Context): Boolean = runCatching {
        val name = (Build.MANUFACTURER + Build.MODEL + Build.DEVICE + Build.PRODUCT)
            .lowercase().filter { it.isLetterOrDigit() }
        if (KNOWN_LOW_END.any { name.contains(it) }) return@runCatching true
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) return@runCatching true
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val gb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        if (gb > 0.1 && gb < 4.6) return@runCatching true
        // The fastest core's top clock (kHz). Flagship-class chips run their big cores near 3 GHz.
        val maxKhz = (0 until Runtime.getRuntime().availableProcessors()).maxOfOrNull { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }
                .getOrDefault(0L)
        } ?: 0L
        maxKhz in 1L until 2_350_000L
    }.getOrDefault(false)
}
