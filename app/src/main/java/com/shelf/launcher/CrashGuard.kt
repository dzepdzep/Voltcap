package com.shelf.launcher

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.shelf.launcher.data.crash.CrashRedactor
import com.shelf.launcher.data.crash.CrashReports

/**
 * The last parts of the screen that were drawn, newest last. Every drawing
 * function records its name here, so a crash report shows exactly where a
 * drawing crash happened (the error itself often doesn't say).
 */
object Breadcrumbs {
    private val trail = ArrayDeque<String>()

    fun mark(name: String) {
        synchronized(trail) {
            if (trail.lastOrNull() != name) {
                trail.addLast(name)
                if (trail.size > 40) trail.removeFirst()
            }
        }
    }

    fun dump(): String = synchronized(trail) { trail.joinToString("\n  ") }
}

/**
 * Keeps a crash from locking you out of the launcher.
 *
 *  - Any crash is written down (the error and where it happened) before the
 *    app closes, so the next start can show it and you can copy it.
 *  - Two crashes in a row within 15 s of starting (or a crash during a safe-mode
 *    run) mean something at startup is probably to blame: the next run starts
 *    in [safeMode] — no intro, Discord, second screen, video previews,
 *    music, controller lights, background sync or news — so Voltcap can open
 *    again. Display and app-library settings are only reset after a crash
 *    *during* safe mode, or when asked for on the crash screen. Games,
 *    accounts, art, achievements and emulator setup are never touched.
 *  - The dying process only stores the stack trace and breadcrumbs. At the next
 *    start the full report is put together in the background
 *    (see [CrashReports]); on Android 11+ native crashes and ANRs, which no
 *    JVM handler can see, are found through ApplicationExitInfo and counted
 *    the same way.
 */
object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val EARLY_WINDOW_MS = 15_000L
    private const val MAX_STACK_CHARS = 24_000

    /** Settings that only change how things look or which apps show — safe
     * to reset, and the likeliest to break startup. */
    private val RESETTABLE = listOf("appearance", "library_apps", "categories", "screensaver")

    private var startedAt = 0L
    @Volatile private var installed = false

    /** True for this whole process when it was started in safe mode; read by the start points that safe mode skips. */
    @Volatile var safeMode: Boolean = false
        private set

    /** Why [safeMode] is on, for the report. */
    @Volatile private var safeReason: String = ""

    /**
     * [id] names the saved report (see [CrashReports]) for export; null when nothing was saved.
     * [resetNames] are the names of settings that were reset, never their contents.
     */
    data class Report(
        val details: String,
        val settingsWereReset: Boolean,
        val id: String? = null,
        val resetNames: List<String> = emptyList(),
        val safeMode: Boolean = false
    )

    /** Safe to call from every entry point (MainActivity, CompanionActivity): only the first call installs. */
    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        // Counts from when the process itself started.
        startedAt = runCatching {
            if (Build.VERSION.SDK_INT >= 24) android.os.Process.getStartElapsedRealtime() else SystemClock.elapsedRealtime()
        }.getOrDefault(SystemClock.elapsedRealtime())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Quick and IPC-free: the stack trace and breadcrumbs only. Everything
            // else is collected at the next start.
            runCatching {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val early = SystemClock.elapsedRealtime() - startedAt < EARLY_WINDOW_MS
                prefs.edit()
                    .putString("report", describe(error))
                    .putLong("crash_time", System.currentTimeMillis())
                    .putInt("crash_pid", android.os.Process.myPid())
                    .putString("crash_thread", thread.name)
                    .putBoolean("crash_safe", safeMode)
                    .putInt("early_crashes", if (early) prefs.getInt("early_crashes", 0) + 1 else 0)
                    .commit() // must finish before the process dies
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Call once at start (main thread, quick): finds out what happened last time —
     * a JVM crash recorded by the handler, and on Android 11+ native crashes / ANRs —
     * decides on safe mode, and starts writing the report files in the background.
     * Returns the newest crash for the crash screen, or null when the last run was fine.
     * Never throws.
     */
    fun takeReport(context: Context): Report? = runCatching { takeReportInner(context.applicationContext) }.getOrNull()

    private fun takeReportInner(app: Context): Report? {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val prevPid = prefs.getInt("run_pid", 0)
        val prevStart = prefs.getLong("run_start", 0L)
        val prevSafe = prefs.getBoolean("run_safe", false)
        var early = prefs.getInt("early_crashes", 0)
        val events = mutableListOf<CrashReports.Event>()

        val stack = prefs.getString("report", null)
        val jvmTime = prefs.getLong("crash_time", now)
        if (stack != null) {
            events += CrashReports.Event(
                timeMillis = jvmTime, kind = "JVM crash", pid = prefs.getInt("crash_pid", 0),
                thread = prefs.getString("crash_thread", null), details = stack,
                safeRun = prefs.getBoolean("crash_safe", false)
            )
        }

        // Crashes the JVM handler can't see (native, ANR), and dedupe with the JVM report.
        val seeded = prefs.contains("last_exit_ts")
        val scan = CrashReports.scanExits(app, prefs.getLong("last_exit_ts", 0L))
        if (seeded) {
            for (exit in scan.relevant) {
                val same = events.firstOrNull { it.kind == "JVM crash" && it.pid == exit.pid && it.exit == null }
                if (same != null && exit.reasonCode == CrashReports.REASON_CRASH) {
                    same.exit = exit
                    continue
                }
                val ofPrevRun = prevPid != 0 && exit.pid == prevPid
                events += CrashReports.Event(
                    timeMillis = exit.timeMillis, kind = CrashReports.kindFor(exit), pid = exit.pid, thread = null,
                    details = null, safeRun = ofPrevRun && prevSafe, exit = exit
                )
                if (ofPrevRun) {
                    early = if (exit.timeMillis - prevStart in 0 until EARLY_WINDOW_MS) early + 1 else 0
                }
            }
        }
        events.sortBy { it.timeMillis }

        val crashedInSafe = events.any { it.safeRun }
        val loop = early >= 2
        val safe = loop || crashedInSafe
        safeMode = safe
        safeReason = when {
            crashedInSafe -> "crashed again while in safe mode"
            loop -> "crashed twice right after starting"
            else -> ""
        }
        // Only a crash during safe mode resets display settings; a crash loop alone doesn't.
        val resetNames = if (crashedInSafe) resetSettings(app) else emptyList()
        if (safe) early = 0

        prefs.edit()
            .putInt("early_crashes", early)
            .putInt("run_pid", android.os.Process.myPid())
            .putLong("run_start", now)
            .putBoolean("run_safe", safe)
            .apply { if (!seeded || events.isEmpty()) putLong("last_exit_ts", scan.newestTimestamp) }
            .apply()

        if (events.isEmpty()) return null

        CrashReports.writeAsync(app, events, resetNames, safe, safeReason) {
            // Processed: forget the JVM record (unless a newer crash replaced it) and remember how far we have read.
            prefs.edit().apply {
                if (prefs.getLong("crash_time", jvmTime) == jvmTime) {
                    remove("report"); remove("crash_time"); remove("crash_pid"); remove("crash_thread"); remove("crash_safe")
                }
                putLong("last_exit_ts", scan.newestTimestamp)
            }.commit()
        }

        val newest = events.last()
        val header = "Voltcap ${versionName(app)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL}\n${newest.kind}" +
            (if (newest.safeRun) " (during safe mode)" else "") + "\n\n"
        val body = newest.details
            ?: (newest.exit?.let { "${it.reasonName}: ${it.description ?: "no description"}\n" } ?: "") +
            "Details are in the exported report."
        return Report(
            details = CrashRedactor.redact(header + body),
            settingsWereReset = resetNames.isNotEmpty(),
            id = newest.id,
            resetNames = resetNames,
            safeMode = safe
        )
    }

    private fun versionName(app: Context): String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /**
     * "Start normally" from the safe-mode banner: forgets safe mode and restarts the
     * app process, so everything that safe mode skipped starts up properly.
     */
    fun restartNormally(activity: android.app.Activity) {
        runCatching {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("early_crashes", 0).putBoolean("run_safe", false).commit()
            val launch = android.content.Intent.makeRestartActivityTask(activity.componentName)
            activity.startActivity(launch)
            activity.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** Call when the launcher itself starts showing (not the report screen):
     * crashes within the next 15 s count as "crashing on start", and after 15 s
     * without one, earlier crashes stop counting toward a loop. */
    fun beginLauncher(context: Context) {
        val app = context.applicationContext
        startedAt = SystemClock.elapsedRealtime()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("early_crashes", 0).apply()
        }, EARLY_WINDOW_MS)
    }

    /** Clears the display and app-library settings. Returns the names of those that had anything saved (never their contents). */
    fun resetSettings(context: Context): List<String> {
        val cleared = mutableListOf<String>()
        RESETTABLE.forEach { name ->
            runCatching {
                val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                if (prefs.all.isNotEmpty()) cleared += name
                prefs.edit().clear().commit()
            }
        }
        return cleared
    }

    /** Only the stack trace (with causes) and breadcrumbs: no IPC, no disk beyond the caller's prefs write. */
    private fun describe(error: Throwable): String {
        var trace = error.stackTraceToString()
        if (trace.length > MAX_STACK_CHARS) {
            // Keep the start and the end: the last "Caused by" is usually the root cause.
            trace = trace.take(MAX_STACK_CHARS * 2 / 3) + "\n... (trace shortened) ...\n" + trace.takeLast(MAX_STACK_CHARS / 3)
        }
        return buildString {
            append(trace)
            appendLine()
            appendLine()
            appendLine("Last drawn (newest last):")
            append("  ")
            append(Breadcrumbs.dump())
        }
    }
}
