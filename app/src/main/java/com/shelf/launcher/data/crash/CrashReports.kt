package com.shelf.launcher.data.crash

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.StatFs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Crash reports as files: `filesDir/crash_reports/<yyyyMMdd-HHmmss>/` with
 *  - `report.txt`    always: versions, device, stack trace, breadcrumbs …
 *  - `logcat.txt`    when the log of the crashed process could still be read
 *  - `exit-info.txt` for ANRs / native crashes: what Android recorded about the exit
 *
 * The dying process only stores the stack trace and breadcrumbs (see
 * [com.shelf.launcher.CrashGuard]); everything expensive — device info, logcat,
 * exit info — is collected here, on a background thread, at the next start.
 * That means "free memory", "displays" etc. describe the device at that next
 * start, and the report says so. Everything written goes through
 * [CrashRedactor]; SharedPreferences contents are never included.
 *
 * Only the last [MAX_REPORTS] reports and [MAX_BYTES] in total are kept.
 * Nothing here throws.
 */
object CrashReports {
    const val MAX_REPORTS = 5
    const val MAX_BYTES = 2L * 1024 * 1024
    private const val DIR = "crash_reports"
    private const val REPORT = "report.txt"
    private const val LOGCAT = "logcat.txt"
    private const val EXIT = "exit-info.txt"
    private const val FREEZE = "freeze.txt"
    private const val MAX_LOGCAT_BYTES = 200_000
    private const val MAX_TRACE_BYTES = 300_000

    /** What Android recorded about a process exit (API 30+). */
    class ExitEvent(
        val timeMillis: Long,
        val pid: Int,
        val reasonCode: Int,
        val reasonName: String,
        val description: String?,
        val status: Int,
        val importance: Int,
        val pssKb: Long,
        val rssKb: Long,
        /** Opens the ANR / tombstone trace; called on the writer thread. */
        val trace: (() -> InputStream?)?
    )

    /** One crash to write down. [details] is the raw stack trace + breadcrumbs (JVM crashes). */
    class Event(
        val timeMillis: Long,
        val kind: String,
        val pid: Int,
        val thread: String?,
        val details: String?,
        /** The crashed run was in safe mode. */
        val safeRun: Boolean,
        var exit: ExitEvent? = null
    ) {
        val id: String get() = idFor(timeMillis)
    }

    /** One saved report, for lists. */
    class Entry(val id: String, val timeMillis: Long, val summary: String, val sizeBytes: Long, val files: List<String>)

    class Export(val fileName: String, val mime: String, val write: (OutputStream) -> Unit)

    @Volatile private var writer: Thread? = null

    private fun root(context: Context) = com.shelf.launcher.data.AppStorage.dir(context, "Crash reports", File(context.filesDir, DIR))

    private val idFormat get() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun idFor(timeMillis: Long): String = idFormat.format(Date(timeMillis))

    // ---- listing / deleting ------------------------------------------------

    fun list(context: Context): List<Entry> = runCatching {
        val dirs = root(context).listFiles { f -> f.isDirectory }?.sortedByDescending { it.name }.orEmpty()
        dirs.mapNotNull { dir ->
            runCatching {
                val files = dir.listFiles { f -> f.isFile && f.length() > 0 }.orEmpty()
                if (files.none { it.name == REPORT }) return@runCatching null
                val summary = File(dir, REPORT).bufferedReader().useLines { lines ->
                    lines.take(6).firstOrNull { it.startsWith("Summary:") }?.removePrefix("Summary:")?.trim()
                } ?: "Crash"
                val time = runCatching { idFormat.parse(dir.name)?.time }.getOrNull() ?: dir.lastModified()
                Entry(dir.name, time, summary, files.sumOf { it.length() }, files.map { it.name }.sorted())
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun latestId(context: Context): String? {
        awaitPending(3000)
        return list(context).firstOrNull()?.id
    }

    fun delete(context: Context, id: String) {
        runCatching {
            if (id.contains('/') || id.contains("..")) return
            File(root(context), id).deleteRecursively()
        }
    }

    /** Keeps the newest [MAX_REPORTS] reports within [MAX_BYTES]; never throws. */
    fun cleanup(context: Context) {
        runCatching {
            val dirs = root(context).listFiles().orEmpty().sortedByDescending { it.name }
            var kept = 0
            var bytes = 0L
            for (dir in dirs) {
                runCatching {
                    val size = if (dir.isDirectory) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else dir.length()
                    if (dir.isDirectory && kept < MAX_REPORTS && bytes + size <= MAX_BYTES) {
                        kept++
                        bytes += size
                    } else {
                        dir.deleteRecursively()
                    }
                }
            }
        }
    }

    /** Adds a line to a saved report, e.g. that the user reset settings afterwards. */
    fun appendNote(context: Context, id: String, note: String) {
        runCatching {
            awaitPending(3000)
            if (id.contains('/') || id.contains("..")) return
            val file = File(File(root(context), id), REPORT)
            if (file.isFile) file.appendText("\n" + CrashRedactor.redact(note) + "\n")
        }
    }

    // ---- export ---------------------------------------------------------------

    /** A single file goes out as .txt, several as one .zip. Null if the report is gone. */
    fun prepareExport(context: Context, id: String): Export? = runCatching {
        awaitPending(5000)
        if (id.contains('/') || id.contains("..")) return null
        val dir = File(root(context), id)
        val files = listOf(REPORT, LOGCAT, EXIT, FREEZE).map { File(dir, it) }.filter { it.isFile && it.length() > 0 }
        if (files.none { it.name == REPORT }) return null
        if (files.size == 1) {
            Export("voltcap-crash-$id.txt", "text/plain") { out ->
                out.write(CrashRedactor.redact(files[0].readText()).toByteArray())
            }
        } else {
            Export("voltcap-crash-$id.zip", "application/zip") { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    files.forEach { f ->
                        zip.putNextEntry(ZipEntry(f.name))
                        // Redacted again on the way out, in case an older report predates a rule.
                        zip.write(CrashRedactor.redact(f.readText()).toByteArray())
                        zip.closeEntry()
                    }
                }
            }
        }
    }.getOrNull()

    // ---- writing ---------------------------------------------------------------

    /** Wait for the background writer (used before exporting the report it is writing). */
    fun awaitPending(timeoutMs: Long) {
        runCatching { writer?.takeIf { it !== Thread.currentThread() }?.join(timeoutMs) }
    }

    /**
     * Writes [events] in the background. [onDone] runs afterwards on the writer
     * thread (used to mark the crash as processed).
     */
    fun writeAsync(
        context: Context,
        events: List<Event>,
        resetNames: List<String>,
        safeModeNow: Boolean,
        safeReason: String,
        onDone: () -> Unit
    ) {
        val app = context.applicationContext
        val thread = Thread({
            events.forEach { runCatching { write(app, it, resetNames, safeModeNow, safeReason) } }
            cleanup(app)
            runCatching(onDone)
        }, "crash-report-writer")
        writer = thread
        runCatching { thread.start() }
    }

    private fun write(context: Context, event: Event, resetNames: List<String>, safeModeNow: Boolean, safeReason: String) {
        var dir = File(root(context), event.id)
        dir.mkdirs()
        File(dir, REPORT).writeText(CrashRedactor.redact(buildReport(context, event, resetNames, safeModeNow, safeReason)))

        if (event.pid > 0) {
            val log = readLogcat(event.pid)
            if (!log.isNullOrBlank()) File(dir, LOGCAT).writeText(CrashRedactor.redact(log))
        }
        event.exit?.let { exit ->
            val text = buildExitInfo(exit)
            File(dir, EXIT).writeText(CrashRedactor.redact(text))
        }
        // Update 292: what the app was doing while frozen (see FreezeWatchdog).
        FreezeWatchdog.takeSnapshot(context, event.timeMillis)?.let { snap ->
            File(dir, FREEZE).writeText(CrashRedactor.redact(snap))
            // The short top part (state + CPU per thread) also goes into report.txt, so one file is enough to start with.
            val head = snap.substringBefore("== Java thread stacks").trimEnd()
            File(dir, REPORT).appendText(CrashRedactor.redact("\n== Freeze snapshot (full stacks in freeze.txt) ==\n$head\n"))
        }
    }

    /**
     * A freeze that left no crash record (the app was force-closed, or recovered after seconds):
     * its snapshot becomes a report of its own. Only freezes of 4 s or more. Call at start when no crash was found.
     */
    fun adoptOrphanFreeze(context: Context) {
        runCatching {
            val app = context.applicationContext
            FreezeWatchdog.dropStale(app)
            val f = FreezeWatchdog.file(app)
            if (!f.isFile) return
            val head = f.bufferedReader().useLines { it.take(3).toList() }
            val ms = Regex("silent for: (\\d+) ms").find(head.joinToString("\n"))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (ms < 4000L) {
                f.delete()
                return
            }
            val pid = Regex("Process id: (\\d+)").find(head.joinToString("\n"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val event = Event(
                timeMillis = f.lastModified(), kind = "Freeze", pid = pid, thread = "main",
                details = "Main thread not responding for $ms ms (no crash or ANR was recorded for it)",
                safeRun = false
            )
            writeAsync(app, listOf(event), emptyList(), false, "") { }
        }
    }

    private fun stamp(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        return f.format(Date(ms)) + " (" + TimeZone.getDefault().id + ")"
    }

    private fun mb(bytes: Long) = "${bytes / (1024 * 1024)} MB"

    private fun buildReport(context: Context, e: Event, resetNames: List<String>, safeNow: Boolean, safeReason: String): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() }
        val firstLine = e.details?.lineSequence()?.firstOrNull { it.isNotBlank() }
            ?: e.exit?.let { it.reasonName + (it.description?.let { d -> ": $d" } ?: "") }
            ?: "unknown"
        return buildString {
            appendLine("VoltCap crash report")
            appendLine("Summary: ${e.kind} - ${firstLine.take(200)}")
            appendLine()
            appendLine("== App ==")
            appendLine("Package: ${context.packageName}")
            appendLine("Version: ${info?.versionName ?: "unknown"} (code ${code ?: "unknown"})")
            appendLine()
            appendLine("== When ==")
            appendLine("Crash time: ${stamp(e.timeMillis)}")
            appendLine("Report written: ${stamp(System.currentTimeMillis())}")
            appendLine("Process id: ${if (e.pid > 0) e.pid else "unknown"}")
            appendLine("Thread: ${e.thread ?: "unknown"}")
            appendLine()
            appendLine("== Recovery ==")
            appendLine("Crashed run was in safe mode: ${if (e.safeRun) "yes" else "no"}")
            appendLine("Safe mode for the next run: ${if (safeNow) "yes - $safeReason" else "no"}")
            appendLine(
                "Settings reset: " + if (resetNames.isEmpty()) "none" else
                    resetNames.joinToString(", ") + " (names only; contents are never included)"
            )
            appendLine()
            appendLine("== Device (measured at the next start unless noted) ==")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), security patch ${if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "n/a"}")
            appendLine("Manufacturer / model: ${Build.MANUFACTURER} / ${Build.MODEL}")
            appendLine("Brand / device / board / hardware: ${Build.BRAND} / ${Build.DEVICE} / ${Build.BOARD} / ${Build.HARDWARE}")
            if (Build.VERSION.SDK_INT >= 31) appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Displays: ${displays(context)}")
            appendLine("Memory: ${memory(context)}")
            appendLine("Storage (app data): ${storage(context)}")
            appendLine()
            appendLine("== Stack trace ==")
            appendLine(e.details?.substringBefore("\n\nLast drawn") ?: "(none - see exit-info.txt)")
            appendLine()
            appendLine("== Last drawn (newest last) ==")
            appendLine(e.details?.substringAfter("Last drawn (newest last):", "")?.trim().orEmpty().ifBlank { "(none)" })
            e.exit?.let {
                appendLine()
                appendLine("== Android exit info ==")
                appendLine("${it.reasonName}: ${it.description ?: "no description"} (details in exit-info.txt)")
            }
        }
    }

    private fun displays(context: Context): String = runCatching {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays.joinToString("; ") { d ->
            val mode = d.mode
            "#${d.displayId} ${d.name} ${mode.physicalWidth}x${mode.physicalHeight} @${"%.0f".format(Locale.US, d.refreshRate)} Hz"
        }.ifBlank { "none" }
    }.getOrDefault("not available")

    private fun memory(context: Context): String = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        "${mb(mi.availMem)} free of ${mb(mi.totalMem)}" + if (mi.lowMemory) " (low memory)" else ""
    }.getOrDefault("not available")

    private fun storage(context: Context): String = runCatching {
        val s = StatFs(context.filesDir.absolutePath)
        "${mb(s.availableBytes)} free of ${mb(s.totalBytes)}"
    }.getOrDefault("not available")

    /** Own process only (`--pid`), best effort: a device may refuse or the lines may have rotated out. */
    /**
     * Update 149, for Settings › Diagnostics › Share report: the newest crash report's text (at most [max] characters)
     * and this run's own recent log, both with personal details taken out. Blocking: call off the main thread.
     */
    fun latestReportText(context: Context, max: Int = 8_000): String? = runCatching {
        val id = latestId(context) ?: return@runCatching null
        File(File(root(context), id), REPORT).takeIf { it.isFile }?.readText()?.take(max)
    }.getOrNull()

    fun recentLog(max: Int = 20_000): String? =
        readLogcat(android.os.Process.myPid())?.let { CrashRedactor.redact(it.takeLast(max)) }

    private fun readLogcat(pid: Int): String? = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "500", "--pid=$pid").redirectErrorStream(true).start()
        val out = ByteArrayOutputStream()
        val reader = Thread {
            runCatching {
                val buf = ByteArray(8192)
                process.inputStream.use { input ->
                    while (out.size() < MAX_LOGCAT_BYTES) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
        }
        reader.start()
        reader.join(4000)
        // A logcat that hangs is abandoned; whatever was read so far is still useful.
        if (reader.isAlive) process.destroy()
        runCatching { reader.join(500) }
        runCatching { process.destroy() }
        out.toString("UTF-8").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun buildExitInfo(x: ExitEvent): String = buildString {
        appendLine("Android exit info (ApplicationExitInfo)")
        appendLine("Reason: ${x.reasonName} (${x.reasonCode})")
        appendLine("Time: ${stamp(x.timeMillis)}")
        appendLine("Process id: ${x.pid}")
        appendLine("Description: ${x.description ?: "none"}")
        appendLine("Status: ${x.status}")
        appendLine("Importance at exit: ${x.importance}")
        appendLine("Memory at exit: pss ${x.pssKb} KB, rss ${x.rssKb} KB")
        appendLine()
        appendLine("== Trace ==")
        val raw = runCatching { x.trace?.invoke()?.use { it.readCapped(MAX_TRACE_BYTES) } }.getOrNull()
        if (raw == null || raw.isEmpty()) {
            appendLine("(Android did not provide a trace for this exit)")
        } else if (looksLikeText(raw)) {
            appendLine(String(raw, Charsets.UTF_8))
        } else {
            // Native crashes: the tombstone is a binary (protobuf) file. The readable
            // strings in it (signal, backtrace frames, library paths) are what matters.
            appendLine("(binary tombstone, ${raw.size} bytes; readable strings only)")
            appendLine(printableRuns(raw))
        }
    }

    private fun InputStream.readCapped(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun looksLikeText(b: ByteArray): Boolean {
        val n = minOf(b.size, 4096)
        if (n == 0) return true
        var odd = 0
        for (i in 0 until n) {
            val c = b[i].toInt() and 0xFF
            if (c == 0 || (c < 32 && c != 9 && c != 10 && c != 13)) odd++
        }
        return odd * 100 / n < 2
    }

    private fun printableRuns(b: ByteArray): String {
        val sb = StringBuilder()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 6) sb.append(run).append('\n')
            run.setLength(0)
        }
        for (x in b) {
            val c = x.toInt() and 0xFF
            if (c in 32..126) run.append(c.toChar()) else flush()
        }
        flush()
        return sb.toString()
    }

    // ---- what Android knows about earlier exits (API 30+) -------------------------

    class ExitScan(val relevant: List<ExitEvent>, val newestTimestamp: Long)

    /**
     * Process exits newer than [sinceTimestamp] that were crashes / ANRs / native
     * crashes. One short binder call; empty on API < 30 or on any failure.
     */
    fun scanExits(context: Context, sinceTimestamp: Long): ExitScan {
        if (Build.VERSION.SDK_INT < 30) return ExitScan(emptyList(), sinceTimestamp)
        return runCatching { scanExits30(context, sinceTimestamp) }.getOrDefault(ExitScan(emptyList(), sinceTimestamp))
    }

    @androidx.annotation.RequiresApi(30)
    private fun scanExits30(context: Context, since: Long): ExitScan {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val all = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        val myPid = android.os.Process.myPid()
        val fresh = all.filter { it.timestamp > since && it.pid != myPid }
        val relevant = fresh.mapNotNull { info ->
            val name = when (info.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
                android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
                else -> return@mapNotNull null
            }
            ExitEvent(
                timeMillis = info.timestamp, pid = info.pid, reasonCode = info.reason, reasonName = name,
                description = info.description, status = info.status, importance = info.importance,
                pssKb = info.pss, rssKb = info.rss,
                trace = { runCatching { info.traceInputStream }.getOrNull() }
            )
        }
        return ExitScan(relevant.sortedBy { it.timeMillis }, maxOf(since, all.maxOfOrNull { it.timestamp } ?: since))
    }

    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6

    /** Human name of an [Event.kind]. */
    fun kindFor(exit: ExitEvent): String = when (exit.reasonCode) {
        REASON_CRASH_NATIVE -> "Native crash"
        REASON_ANR -> "App not responding (ANR)"
        else -> "Crash"
    }
}
