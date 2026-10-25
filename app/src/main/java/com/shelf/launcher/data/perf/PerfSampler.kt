package com.shelf.launcher.data.perf

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Everything the sampler could read this second. Null = not readable on this device (shown as "n/a"). */
data class PerfSnapshot(
    val fps: Float? = null,
    val frameMs: Float? = null,
    /** Why FPS is missing, for the hint line. */
    val fpsNote: String? = null,
    val cpuMaxMhz: Int? = null,
    val cpuAvgMhz: Int? = null,
    val cpuPercent: Int? = null,
    val gpuPercent: Int? = null,
    val gpuMhz: Int? = null,
    val ramUsedMb: Int? = null,
    val ramTotalMb: Int? = null,
    /** Hottest CPU / SoC / GPU thermal zone. */
    val socTempC: Float? = null,
    val batteryTempC: Float? = null,
    /** Approximate: battery current x voltage. */
    val powerW: Float? = null,
    val charging: Boolean? = null,
    val batteryPercent: Int? = null,
    /** Energy left in the battery, Wh (charge counter x voltage), when the driver reports it. */
    val batteryWh: Float? = null,
    /** Last 60 s, oldest first; NaN where nothing was readable. */
    val cpuHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList(),
    val fpsHistory: List<Float> = emptyList()
)

/**
 * Reads the sensors. Holds the little state needed between samples (previous
 * /proc/stat, discovered file paths). One instance per sampling run.
 */
class PerfReader(context: Context) {
    private val app = context.applicationContext
    private var prevTotal = -1L
    private var prevIdle = -1L
    private val cpuFreqFiles: List<File> by lazy {
        (File("/sys/devices/system/cpu").listFiles().orEmpty())
            .filter { it.name.matches(Regex("cpu[0-9]+")) }
            .map { File(it, "cpufreq/scaling_cur_freq") }
    }
    private val thermalTemps: List<File> by lazy {
        (File("/sys/class/thermal").listFiles().orEmpty())
            .filter { it.name.startsWith("thermal_zone") }
            .filter {
                val type = readText(File(it, "type"))?.lowercase().orEmpty()
                type.contains("cpu") || type.contains("soc") || type.contains("gpu")
            }
            .map { File(it, "temp") }
    }
    private val gpuClockFiles: List<File> by lazy {
        val mali = File("/sys/class/misc/mali0/device/devfreq").listFiles().orEmpty().map { File(it, "cur_freq") }
        listOf(File("/sys/class/kgsl/kgsl-3d0/gpuclk"), File("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")) + mali
    }

    fun sample(): PerfSnapshot {
        val freqs = cpuFreqFiles.mapNotNull { readText(it)?.trim()?.toLongOrNull() }.filter { it > 0 }.map { it / 1000 }
        val (gpuPct, gpuMhz) = gpu()
        val mem = runCatching {
            val info = ActivityManager.MemoryInfo()
            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            if (info.totalMem > 0) ((info.totalMem - info.availMem) / 1_048_576L).toInt() to (info.totalMem / 1_048_576L).toInt() else null
        }.getOrNull()
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE && it in -200..1500 }?.let { it / 10f }
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status?.takeIf { it > 0 }?.let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        return PerfSnapshot(
            cpuMaxMhz = freqs.maxOrNull()?.toInt(),
            cpuAvgMhz = if (freqs.isNotEmpty()) freqs.average().toInt() else null,
            cpuPercent = cpuPercent(),
            gpuPercent = gpuPct, gpuMhz = gpuMhz,
            ramUsedMb = mem?.first, ramTotalMb = mem?.second,
            socTempC = thermalTemps.mapNotNull { readText(it)?.trim()?.toFloatOrNull() }
                .map { if (it > 1000f) it / 1000f else if (it > 150f) it / 10f else it }
                .filter { it in 0f..150f }.maxOrNull(),
            batteryTempC = batteryTemp,
            powerW = power(battery),
            charging = charging,
            batteryPercent = battery?.let {
                val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val sc = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && sc > 0) l * 100 / sc else null
            },
            batteryWh = runCatching {
                val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val uah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                val mv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                if (uah > 0 && mv > 0) uah / 1_000_000f * (mv / 1000f) else null
            }.getOrNull()
        )
    }

    /** Only when /proc/stat is readable (Android 8+ usually blocks it for apps). */
    private fun cpuPercent(): Int? {
        val line = readText(File("/proc/stat"))?.lineSequence()?.firstOrNull { it.startsWith("cpu ") } ?: return null
        val n = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (n.size < 5) return null
        val idle = n[3] + n[4]
        val total = n.sum()
        val pct = if (prevTotal >= 0 && total > prevTotal) (100L * ((total - prevTotal) - (idle - prevIdle)) / (total - prevTotal)).toInt() else null
        prevTotal = total; prevIdle = idle
        return pct?.coerceIn(0, 100)
    }

    private fun gpu(): Pair<Int?, Int?> {
        var pct: Int? = null
        // Adreno: "busy total" counters since the last read.
        readText(File("/sys/class/kgsl/kgsl-3d0/gpubusy"))?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size >= 2 && it[1] > 0 }?.let { pct = (100L * it[0] / it[1]).toInt().coerceIn(0, 100) }
        // Mali: plain percentage.
        if (pct == null) readText(File("/sys/class/misc/mali0/device/utilization"))?.trim()?.toIntOrNull()?.let { pct = it.coerceIn(0, 100) }
        val hz = gpuClockFiles.firstNotNullOfOrNull { readText(it)?.trim()?.toLongOrNull() }
        val mhz = hz?.takeIf { it > 0 }?.let { (if (it > 10_000_000L) it / 1_000_000L else it / 1000L).toInt() }
        return pct to mhz
    }

    private fun power(battery: Intent?): Float? {
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val raw = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull() ?: return null
        if (raw == Int.MIN_VALUE || raw == 0) return null
        val mv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (mv <= 0) return null
        // Most devices report microamps; a few report milliamps (values far too small to be microamps).
        val amps = Math.abs(raw.toDouble()) / (if (Math.abs(raw) < 20_000) 1_000.0 else 1_000_000.0)
        return (amps * mv / 1000.0).toFloat().takeIf { it in 0.01f..100f }
    }

    companion object {
        fun readText(f: File): String? = runCatching { f.takeIf { it.canRead() }?.readText() }.getOrNull()
    }
}

/**
 * Samples once a second, but only while something displays it. Every display
 * calls [acquire] when it becomes visible and [release] when it stops; the
 * loop runs while the count is above zero and [paused] is false, so nothing
 * runs in the background or with the screen off.
 */
object PerfSampler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var refs = 0
    private var job: Job? = null
    private var appContext: Context? = null

    private val _snapshot = MutableStateFlow(PerfSnapshot())
    val snapshot: StateFlow<PerfSnapshot> = _snapshot.asStateFlow()

    /** The running game's package, for FPS (set when a game starts from Voltcap). */
    @Volatile var gamePackage: String? = null

    @Volatile private var paused = false

    fun acquire(context: Context) {
        synchronized(lock) {
            appContext = context.applicationContext
            refs++
            if (refs == 1) startLoop()
        }
    }

    fun release() {
        synchronized(lock) {
            if (refs == 0) return
            refs--
            if (refs == 0) { job?.cancel(); job = null }
        }
    }

    /** Screen off / on: keeps the references but stops reading. */
    fun setPaused(value: Boolean) { paused = value }

    val activeRefs: Int get() = synchronized(lock) { refs }

    private fun startLoop() {
        val ctx = appContext ?: return
        job = scope.launch {
            // History lives inside the job: a stopped loop and a restarted one never touch the same deques.
            val cpuHist = ArrayDeque<Float>()
            val gpuHist = ArrayDeque<Float>()
            val fpsHist = ArrayDeque<Float>()
            val reader = PerfReader(ctx)
            val rootCounter = RootFrameCounterFpsSource()
            val driverFps = DisplayMeasuredFpsSource()
            val fpsSource: FpsSource? = runCatching {
                com.shelf.launcher.device.DeviceProfiles.current(ctx).fpsSource(ctx)
            }.getOrNull()?.takeIf { it.isUsable() }
                ?: SurfaceFlingerFpsSource(ctx).takeIf { it.isUsable() }
            // Update 112: the last good game reading is held for a few seconds (one empty read no longer flashes "n/a"),
            // and with no game running the screen's own rate shows (the display's frame counter, else its refresh rate).
            var lastGood: FpsReading? = null
            var lastGoodAt = 0L
            fun screenRate(): Float? = runCatching {
                (ctx.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
                    .getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate
            }.getOrNull()?.takeIf { it > 1f }
            // Update 328: also asleep → no reading, whoever holds the sampler. Found on the AYN Thor: during a game with the
            // in-game tuner's AutoTDP on (TuneService holds it), Voltcap kept reading the CPU / GPU / FPS files every
            // second with the screen off (≈3 % of a core, a garbage collection every ~2 min). The overlay already paused
            // itself on screen off; the tuner didn't.
            val power = ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            while (isActive) {
                if (!paused && power?.isInteractive != false) {
                    val base = runCatching { reader.sample() }.getOrDefault(PerfSnapshot())
                    val pkg = gamePackage
                    val now = System.currentTimeMillis()
                    val gameFps = (if (fpsSource != null && pkg != null) runCatching { fpsSource.read(pkg) }.getOrNull() else null)
                        // Per-layer timings empty (common on Android 12+): the display's frame counter via root.
                        ?: (if (pkg != null) runCatching { rootCounter.read(pkg) }.getOrNull() else null)
                        // Qualcomm display driver's measured_fps (Retroid Pocket Classic and other Snapdragon handhelds).
                        ?: (if (pkg != null) runCatching { driverFps.read(pkg) }.getOrNull() else null)
                    if (gameFps != null) { lastGood = gameFps; lastGoodAt = now }
                    val held = if (gameFps == null && pkg != null && now - lastGoodAt < 4_000L) lastGood else null
                    // No game: what the screen shows (the home screen) — its frame counter, else its refresh rate.
                    val screen = if (pkg == null) {
                        runCatching { rootCounter.read("display") }.getOrNull()
                            ?: runCatching { driverFps.read("display") }.getOrNull()
                            ?: screenRate()?.let { FpsReading(it, 1000f / it) }
                    } else null
                    val fps = gameFps ?: held ?: screen
                    val note = when {
                        gameFps != null || held != null -> null
                        screen != null -> "Screen rate · No game running" // (Update 144: starts with a capital, like the other notes.)
                        fpsSource == null && !rootCounter.isUsable() && !driverFps.isUsable() -> "No FPS source on this device. Tap \"Why n/a?\" for what would fix it."
                        pkg == null -> "No game running."
                        else -> "No frames read for this game."
                    }
                    push(cpuHist, base.cpuMaxMhz?.toFloat())
                    push(gpuHist, base.gpuPercent?.toFloat())
                    push(fpsHist, fps?.fps)
                    _snapshot.value = base.copy(
                        fps = fps?.fps, frameMs = fps?.frameMs, fpsNote = note,
                        cpuHistory = cpuHist.toList(), gpuHistory = gpuHist.toList(), fpsHistory = fpsHist.toList()
                    )
                }
                delay(1000)
            }
        }
    }

    private fun push(q: ArrayDeque<Float>, v: Float?) {
        q.addLast(v ?: Float.NaN)
        while (q.size > 60) q.removeFirst()
    }

    /** One-off read for the settings list of what this device can report. */
    fun probe(context: Context): PerfSnapshot = PerfReader(context).let { r ->
        r.sample(); Thread.sleep(300); r.sample() // second read so CPU % (if readable) has a delta
    }
}
