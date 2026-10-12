package com.shelf.launcher.data.perf

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** One FPS reading: frames per second and the average time per frame. */
data class FpsReading(val fps: Float, val frameMs: Float)

/**
 * Where FPS can come from. Normal apps cannot read another app's frame rate, so
 * every source is optional and returns null when it cannot give a real number.
 */
interface FpsSource {
    /** Short name for the settings list. */
    val name: String
    /** Whether this source can work at all right now (e.g. permission granted). */
    fun isUsable(): Boolean
    /** Called off the main thread, at most once a second. Null = no reading. */
    fun read(gamePackage: String?): FpsReading?
}

/**
 * Reads SurfaceFlinger's frame timestamps for the game's layer. Needs the DUMP
 * permission, which Android only lets a user grant over ADB:
 * `adb shell pm grant com.shelf.launcher android.permission.DUMP`
 */
class SurfaceFlingerFpsSource(private val context: Context) : FpsSource {
    override val name = "SurfaceFlinger (DUMP permission)"

    override fun isUsable() = hasDump(context)

    @Volatile private var layerPackage: String? = null
    @Volatile private var layer: String? = null

    override fun read(gamePackage: String?): FpsReading? {
        if (gamePackage.isNullOrBlank() || !isUsable()) return null
        if (layerPackage != gamePackage) { layer = null; layerPackage = gamePackage; findFailures = 0 }
        var name = layer ?: lookupLayer(gamePackage)?.also { layer = it } ?: return null
        var reading = latency(name)
        if (reading == null) {
            // The layer may have been recreated (new surface): look it up once more.
            val again = lookupLayer(gamePackage)
            if (again != null && again != name) {
                layer = again
                name = again
                reading = latency(name)
            }
        }
        return reading
    }

    // Each lookup spawns `dumpsys`; after 3 misses in a row try only once per 10 s (a game that
    // draws no layer we can match would otherwise cost a process every second).
    private var findFailures = 0
    private var lastFindAt = 0L

    private fun lookupLayer(pkg: String): String? {
        val now = System.currentTimeMillis()
        if (findFailures >= 3 && now - lastFindAt < 10_000L) return null
        lastFindAt = now
        val found = findLayer(pkg)
        findFailures = if (found == null) findFailures + 1 else 0
        return found
    }

    private fun findLayer(pkg: String): String? {
        val out = PerfExec.run(listOf("dumpsys", "SurfaceFlinger", "--list"), 1500) ?: return null
        val candidates = out.lines().map { it.trim() }
            .filter { it.contains(pkg) && !it.startsWith("Background for") && !it.startsWith("Bounds for") }
        // The game draws into its SurfaceView; fall back to the window layer.
        return candidates.firstOrNull { it.startsWith("SurfaceView") && it.contains("BLAST") }
            ?: candidates.firstOrNull { it.startsWith("SurfaceView") }
            ?: candidates.firstOrNull()
    }

    private fun latency(layerName: String): FpsReading? {
        val out = PerfExec.run(listOf("dumpsys", "SurfaceFlinger", "--latency", layerName), 1500) ?: return null
        val times = out.lines().drop(1).mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            // Column 2 is the actual present time; 0 and Long.MAX_VALUE mean "not presented".
            cols.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L && it != Long.MAX_VALUE }
        }
        if (times.size < 3) return null
        // SurfaceFlinger stamps use the monotonic clock, same as nanoTime. Stale data = not rendering.
        if (System.nanoTime() - times.max() > 2_000_000_000L) return null
        val span = times.max() - times.min()
        if (span <= 0L) return null
        val frameNs = span.toDouble() / (times.size - 1)
        return FpsReading((1_000_000_000.0 / frameNs).toFloat(), (frameNs / 1_000_000.0).toFloat())
    }

    companion object {
        const val ADB_COMMAND = "adb shell pm grant com.shelf.launcher android.permission.DUMP"
        fun hasDump(context: Context) =
            context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED
    }
}

/** Runs a command with a hard time limit; null on any failure or timeout. */
internal object PerfExec {
    fun run(cmd: List<String>, timeoutMs: Long): String? = runCatching {
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val sb = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { if (sb.length < 200_000) sb.appendLine(it) } }
        }.also { it.isDaemon = true; it.start() }
        reader.join(timeoutMs)
        if (reader.isAlive) { process.destroy(); return@runCatching null }
        process.destroy()
        sb.toString().takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * Fallback for when SurfaceFlinger's per-layer timings come back empty (Android 12+ often reports
 * nothing for BLAST layers): the display's own frame counter through the AYN root service
 * (`service call SurfaceFlinger 1013`), read once a second. That counts every frame the screen
 * shows, which while a game is in front is the game's frame rate.
 */
class RootFrameCounterFpsSource : FpsSource {
    override val name = "Display frame counter (root)"
    override fun isUsable() = com.shelf.launcher.device.RootShell.available

    private var lastCount = -1L
    private var lastAt = 0L

    override fun read(gamePackage: String?): FpsReading? {
        if (gamePackage.isNullOrBlank() || !isUsable()) return null
        val out = com.shelf.launcher.device.RootShell.exec("service call SurfaceFlinger 1013") ?: return null
        // "Result: Parcel(00000000 0001a2b3   '........')" — the second word is the frame count (hex).
        val count = Regex("Parcel\\(\\s*[0-9a-fA-F]+\\s+([0-9a-fA-F]+)").find(out)?.groupValues?.get(1)?.toLongOrNull(16) ?: return null
        val now = System.nanoTime()
        val prevCount = lastCount
        val prevAt = lastAt
        lastCount = count
        lastAt = now
        if (prevCount < 0 || count < prevCount) return null
        val seconds = (now - prevAt) / 1_000_000_000.0
        if (seconds < 0.3 || seconds > 5.0) return null
        val fps = ((count - prevCount) / seconds).toFloat()
        if (fps <= 0f) return null
        return FpsReading(fps, 1000f / fps)
    }
}

/**
 * Qualcomm display driver's own measured frame rate (`sde-crtc-N/measured_fps`, "fps: 59.8 …").
 * Many Snapdragon handhelds (the Retroid Pocket Classic among them) expose it; some firmware lets
 * apps read it directly, otherwise it's read through the vendor root service where there is one.
 * It counts frames the panel really showed, which with a game in front is the game's frame rate.
 */
class DisplayMeasuredFpsSource : FpsSource {
    override val name = "Display driver measured FPS"

    @Volatile private var path: String? = null
    @Volatile private var viaRoot = false
    @Volatile private var probed = false

    override fun isUsable(): Boolean { probe(); return path != null }

    private fun probe() {
        if (probed) return
        probed = true
        val candidates = CANDIDATES + (runCatching {
            java.io.File("/sys/class/drm").listFiles()?.filter { it.name.startsWith("sde-crtc-") }
                ?.map { it.absolutePath + "/measured_fps" }
        }.getOrNull().orEmpty())
        for (p in candidates.distinct()) {
            if (parse(runCatching { java.io.File(p).readText() }.getOrNull()) != null) { path = p; viaRoot = false; return }
        }
        if (com.shelf.launcher.device.RootShell.available) {
            for (p in candidates.distinct()) {
                if (parse(com.shelf.launcher.device.RootShell.exec("cat $p")) != null) { path = p; viaRoot = true; return }
            }
        }
    }

    override fun read(gamePackage: String?): FpsReading? {
        if (gamePackage.isNullOrBlank()) return null
        probe()
        val p = path ?: return null
        val text = if (viaRoot) com.shelf.launcher.device.RootShell.exec("cat $p") else runCatching { java.io.File(p).readText() }.getOrNull()
        val fps = parse(text)?.takeIf { it > 0.5f } ?: return null
        return FpsReading(fps, 1000f / fps)
    }

    /** Whether the file exists but this app may not read it (for "Why n/a?"). */
    fun blockedPath(): String? = (CANDIDATES).firstOrNull { java.io.File(it).exists() && !java.io.File(it).canRead() }

    companion object {
        val CANDIDATES = listOf(
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/drm/sde-crtc-1/measured_fps",
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/sde-crtc-0/measured_fps",
            "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/measured_fps"
        )

        fun parse(text: String?): Float? {
            val t = text?.trim().orEmpty()
            if (t.isEmpty()) return null
            Regex("fps:\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(t)?.let { return it.groupValues[1].toFloatOrNull() }
            return t.split(Regex("\\s+")).firstOrNull()?.toFloatOrNull()
        }
    }
}

/** "Why n/a?": every FPS route Voltcap knows, whether it works here, and what would fix it. */
object FpsDiagnosis {
    data class Line(val source: String, val ok: Boolean, val detail: String)

    fun explain(context: Context, gamePackage: String?): List<Line> {
        val lines = mutableListOf<Line>()
        val dump = SurfaceFlingerFpsSource.hasDump(context)
        lines += Line(
            "Frame timings (DUMP permission)", dump,
            if (dump) "Allowed." else "Not allowed. Settings › Permissions › \"Read frame rate\", or once from a PC: ${SurfaceFlingerFpsSource.ADB_COMMAND}"
        )
        val root = com.shelf.launcher.device.RootShell.available
        lines += Line(
            "Display frame counter (vendor root service)", root,
            if (root) "Available." else "This firmware has no vendor root service (AYN has one; Retroid and most others don't)."
        )
        val drv = DisplayMeasuredFpsSource()
        val drvOk = drv.isUsable()
        lines += Line(
            "Display driver measured FPS", drvOk,
            when {
                drvOk -> "Available."
                drv.blockedPath() != null -> "The driver reports it, but Android doesn't let apps read it here."
                else -> "This display driver doesn't report it."
            }
        )
        lines += Line(
            "A game started from Voltcap", gamePackage != null,
            if (gamePackage != null) "Watching $gamePackage." else "FPS is read only for games and apps started from Voltcap."
        )
        if (Build.VERSION.SDK_INT >= 31 && dump) {
            lines += Line("Android 12+ layer timings", true, "Some emulators draw through layers Android reports no timings for; then a display counter above is used.")
        }
        return lines
    }
}
