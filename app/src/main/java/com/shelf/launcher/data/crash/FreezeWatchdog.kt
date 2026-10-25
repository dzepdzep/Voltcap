package com.shelf.launcher.data.crash

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Update 292. Records what the app was doing while it was frozen.
 *
 * Android's own ANR trace (see [CrashReports]) only exists if Android decides to
 * make one, and says nothing about what the app was busy with before. This adds:
 *
 *  - every [SAMPLE_MS] a cheap CPU reading of all threads (names + ticks from
 *    /proc/self/task) kept in memory for the last ~[HISTORY] samples — so a report
 *    shows e.g. "RenderThread 95 % of one core for the last minute";
 *  - a heartbeat posted to the main thread; when it is not answered for
 *    [STALL_MS] a snapshot is written to `filesDir/freeze-latest.txt`
 *    (and refreshed while the freeze goes on): stacks of every Java thread, the
 *    CPU history, memory, power state, breadcrumbs and the app state set with [state].
 *
 * The next start attaches that file to the ANR / crash report as `freeze.txt`.
 * RenderThread is native: its stack is not here, only its CPU; the native stack
 * comes from Android's ANR trace in exit-info.txt.
 * Nothing here throws, nothing touches the main thread except one tiny post per beat.
 */
object FreezeWatchdog {
    private const val BEAT_MS = 500L
    private const val STALL_MS = 2_000L
    private const val SAMPLE_MS = 5_000L
    private const val HISTORY = 14
    private const val FILE = "freeze-latest.txt"
    private const val MAX_FRAMES = 45

    private val states = ConcurrentHashMap<String, String>()

    /** App state for the report, e.g. `state("screensaver", "on")`. Cheap; call on change. */
    fun state(key: String, value: Any?) {
        if (value == null) states.remove(key) else states[key] = value.toString()
    }

    @Volatile private var started = false
    @Volatile private var lastBeat = SystemClock.elapsedRealtime()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val beat = Runnable { lastBeat = SystemClock.elapsedRealtime() }

    private class Sample(val atMs: Long, val ticks: Map<String, Long>)
    private val history = ArrayDeque<Sample>()

    // Outside crash_reports/: that folder's cleanup deletes anything that is not a report folder.
    fun file(context: Context) = File(context.filesDir, FILE)

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val t = Thread({ loop(app) }, "voltcap-watchdog")
        t.isDaemon = true
        runCatching { t.priority = Thread.MIN_PRIORITY }
        runCatching { t.start() }
    }

    private fun loop(app: Context) {
        var lastSample = 0L
        var stallStart = 0L
        var lastWrite = 0L
        var prev = SystemClock.elapsedRealtime()
        while (true) {
            runCatching { Thread.sleep(BEAT_MS) }
            val now = SystemClock.elapsedRealtime()
            val watcherDelayed = now - prev > BEAT_MS * 3 // device slept / process frozen: not the app's fault
            prev = now
            if (watcherDelayed) {
                lastBeat = now
                stallStart = 0L
                mainHandler.post(beat)
                continue
            }
            if (now - lastSample >= SAMPLE_MS) {
                lastSample = now
                // Update 328: no CPU reading while the screen is off (every thread's stat file every 5 s for nothing —
                // nobody is looking). A freeze still writes its snapshot below with a fresh reading.
                if (awake(app)) runCatching { sampleCpu(now) }
            }
            val silent = now - lastBeat
            if (silent >= STALL_MS) {
                if (stallStart == 0L) stallStart = now - silent
                // First snapshot at once, then every 5 s while the freeze lasts (max ~2 min).
                if (now - lastWrite >= 5_000L && now - stallStart < 120_000L) {
                    lastWrite = now
                    runCatching { sampleCpu(now) }
                    runCatching { writeSnapshot(app, silent) }
                }
            } else {
                stallStart = 0L
                lastWrite = 0L
                mainHandler.post(beat)
            }
            // Keep exactly one beat queued even while stalled, so recovery is seen at once.
            if (silent >= STALL_MS) {
                mainHandler.removeCallbacks(beat)
                mainHandler.post(beat)
            }
        }
    }

    private var power: android.os.PowerManager? = null
    private fun awake(app: Context): Boolean {
        val pm = power ?: (app.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.also { power = it }
        return runCatching { pm?.isInteractive }.getOrNull() != false
    }

    // ---- CPU -------------------------------------------------------------------

    /** thread name (+ tid) → utime+stime ticks. */
    private fun readTicks(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        File("/proc/self/task").listFiles()?.forEach { dir ->
            runCatching {
                val stat = File(dir, "stat").readText()
                val open = stat.indexOf('(')
                val close = stat.lastIndexOf(')')
                val name = stat.substring(open + 1, close)
                val rest = stat.substring(close + 2).split(' ')
                // After ") ": state(0) … utime is field 11, stime 12 (0-based, from state).
                val ticks = rest[11].toLong() + rest[12].toLong()
                out["$name#${dir.name}"] = ticks
            }
        }
        return out
    }

    private fun sampleCpu(now: Long) {
        val s = Sample(now, readTicks())
        synchronized(history) {
            history.addLast(s)
            while (history.size > HISTORY) history.removeFirst()
        }
    }

    /** Busiest threads between consecutive samples: "RenderThread 97% | main 3% ..." per window. */
    private fun cpuHistoryText(): String {
        val h = synchronized(history) { history.toList() }
        if (h.size < 2) return "(not enough samples yet)"
        val sb = StringBuilder()
        sb.appendLine("Each line = one ${SAMPLE_MS / 1000} s window, newest last. 100% = one full core. Top threads only.")
        for (i in 1 until h.size) {
            val a = h[i - 1]
            val b = h[i]
            val secs = (b.atMs - a.atMs) / 1000.0
            if (secs <= 0) continue
            val top = b.ticks.mapNotNull { (k, v) ->
                val d = v - (a.ticks[k] ?: v)
                // 100 ticks per second = 100 % of one core.
                if (d <= 0) null else k.substringBefore('#') to d / secs
            }.groupBy({ it.first }, { it.second }).map { (n, l) -> n to l.sum() }
                .sortedByDescending { it.second }.take(4)
            val ageS = ((h.last().atMs - b.atMs) / 1000)
            sb.append("  -${ageS}s: ")
            sb.appendLine(if (top.isEmpty()) "idle" else top.joinToString(" | ") { "${it.first} ${"%.0f".format(Locale.US, it.second)}%" })
        }
        return sb.toString().trimEnd()
    }

    // ---- snapshot ----------------------------------------------------------------

    private fun writeSnapshot(app: Context, silentMs: Long) {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        sb.appendLine("VoltCap freeze snapshot (written while the app was not responding)")
        sb.appendLine("Time: ${fmt.format(Date())}   Main thread silent for: ${silentMs} ms")
        sb.appendLine("Process id: ${android.os.Process.myPid()}   Uptime of process: ${(SystemClock.elapsedRealtime() - processStart()) / 1000} s")
        sb.appendLine()
        sb.appendLine("== App state (set by the app) ==")
        if (states.isEmpty()) sb.appendLine("(none set)") else states.toSortedMap().forEach { (k, v) -> sb.appendLine("$k = $v") }
        sb.appendLine()
        sb.appendLine("== CPU per thread, recent windows ==")
        sb.appendLine("NOTE: RenderThread is the GPU/drawing thread. If it sits near 100 %, the app is drawing too much, not waiting on something.")
        sb.appendLine(cpuHistoryText())
        sb.appendLine()
        sb.appendLine("== Device ==")
        sb.appendLine(deviceLine(app))
        sb.appendLine()
        sb.appendLine("== Last drawn (newest last) ==")
        sb.appendLine("  " + com.shelf.launcher.Breadcrumbs.dump())
        sb.appendLine()
        sb.appendLine("== Java thread stacks (main first) ==")
        val all = Thread.getAllStackTraces()
        val main = Looper.getMainLooper().thread
        val ordered = all.entries.sortedBy { if (it.key === main) 0 else 1 }
        for ((thread, frames) in ordered) {
            sb.appendLine("\"${thread.name}\" ${thread.state}${if (thread === main) "  <== MAIN" else ""}")
            frames.take(MAX_FRAMES).forEach { sb.appendLine("    at $it") }
            if (frames.size > MAX_FRAMES) sb.appendLine("    ... ${frames.size - MAX_FRAMES} more")
            sb.appendLine()
        }
        val f = file(app)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(sb.toString())
        tmp.renameTo(f)
    }

    private fun processStart(): Long = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 24) android.os.Process.getStartElapsedRealtime() else 0L
    }.getOrDefault(0L)

    private fun deviceLine(app: Context): String = runCatching {
        val rt = Runtime.getRuntime()
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus.toString() else "n/a"
        val dm = app.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val disp = dm.displays.joinToString("; ") { "#${it.displayId} ${"%.0f".format(Locale.US, it.refreshRate)}Hz state=${it.state}" }
        "Java heap ${(rt.totalMemory() - rt.freeMemory()) shr 20}/${rt.maxMemory() shr 20} MB, native heap ${Debug.getNativeHeapAllocatedSize() shr 20} MB, " +
            "threads ${Thread.getAllStackTraces().size} (java), screen interactive=${pm.isInteractive}, idleMode=${pm.isDeviceIdleMode}, " +
            "powerSave=${pm.isPowerSaveMode}, thermalStatus=$thermal (0 none … 6 shutdown), displays: $disp"
    }.getOrDefault("not available")

    /**
     * For [CrashReports]: the saved snapshot if it was written within [maxAgeMs] before [eventTimeMs]
     * (or after it, for an ANR that Android logs a little late); then removed. Null when there is none.
     */
    fun takeSnapshot(app: Context, eventTimeMs: Long, maxAgeMs: Long = 15 * 60_000L): String? = runCatching {
        val f = file(app)
        if (!f.isFile || f.length() == 0L) return@runCatching null
        val modified = f.lastModified()
        if (modified < eventTimeMs - maxAgeMs || modified > eventTimeMs + 60_000L) return@runCatching null
        val text = f.readText()
        f.delete()
        text
    }.getOrNull()

    /** Drops a snapshot older than a day so a stale one is never attached to a later crash. */
    fun dropStale(app: Context) {
        runCatching {
            val f = file(app)
            if (f.isFile && System.currentTimeMillis() - f.lastModified() > 24 * 3600_000L) f.delete()
        }
    }
}
