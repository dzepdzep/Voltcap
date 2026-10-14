package com.shelf.launcher.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/*
 * Update 141: Voltcap's version and its update checker (Settings › Updates & downloads).
 *
 * New versions are GitHub releases on dzepdzep/Voltcap (the same repo as the art packs): a release tagged "v0.3.0"
 * with the APK attached. The check reads the latest release, compares its tag with this build's versionName, and the
 * Download button fetches the APK and hands it to Android's own installer (it asks before installing; the first time,
 * Android asks to allow installs from Voltcap). The APK has to be signed with the same key as the installed app.
 */
object AppUpdates {
    const val REPO = "dzepdzep/Voltcap"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    class Release(val tag: String, val name: String, val notes: String, val apkUrl: String?, val apkBytes: Long, val published: String)

    sealed class State {
        object Idle : State()
        object Checking : State()
        class UpToDate(val latest: String) : State()
        class Available(val release: Release) : State()
        class Downloading(val release: Release, val done: Long, val total: Long) : State()
        class Ready(val release: Release, val file: File) : State()
        class Failed(val why: String) : State()
    }

    val state = mutableStateOf<State>(State.Idle)

    /** When the last check ran (ms), 0 = never this session. */
    val lastChecked = mutableStateOf(0L)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "updates").apply { isDaemon = true } }
    private val client by lazy {
        okhttp3.OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    /** This build's version name ("0.2.0"), its version code and when it was installed / updated. */
    fun installed(c: Context): Triple<String, Long, Long> = runCatching {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        Triple(pi.versionName ?: "?", code, pi.lastUpdateTime)
    }.getOrDefault(Triple("?", 0L, 0L))

    /** "v0.3.0" / "0.3.0" → [0, 3, 0]; anything after the numbers ("-beta") is ignored. */
    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").split('.', '-', ' ').map { p -> p.takeWhile { it.isDigit() } }
            .takeWhile { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }

    /** Whether version [a] is newer than [b]. */
    fun newer(a: String, b: String): Boolean {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /**
     * Looks for a newer release (in the background).
     * Update 202: [auto] = the automatic check (start / every 12 hours): a failure there leaves Settings as it was (no
     * "Couldn't reach GitHub" for something the user didn't ask for), and a newer version not skipped becomes [prompt],
     * the home screen's "Voltcap x is out" question. The Check button in Settings never pops that question.
     */
    fun check(c: Context, auto: Boolean = false) {
        val app = c.applicationContext
        if (state.value is State.Checking || state.value is State.Downloading) return
        val before = state.value
        // Update 204: the automatic check runs often now (start, coming back, every hour), so it doesn't flash
        // "Checking GitHub…" in Settings each time; the Check button still shows it.
        if (!auto) state.value = State.Checking
        worker.execute {
            val result: State = runCatching {
                val b = okhttp3.Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json")
                // Update 204: asked again with the answer's ETag — GitHub answers "304 nothing new" then, which doesn't
                // count against its 60-an-hour limit, so checking often costs nothing.
                val known = lastRelease
                val tag = etag
                if (known != null && tag != null) b.header("If-None-Match", tag)
                client.newCall(b.build()).execute().use { res ->
                    if (res.code == 304 && known != null) {
                        return@use if (newer(known.tag, installed(app).first)) State.Available(known) else State.UpToDate(known.tag)
                    }
                    if (res.code == 404) return@use State.Failed("No release on GitHub yet ($REPO › Releases).")
                    if (!res.isSuccessful) return@use State.Failed("GitHub answered ${res.code}. Try again later.")
                    val j = org.json.JSONObject(res.body?.string().orEmpty())
                    val assets = j.optJSONArray("assets")
                    var url: String? = null; var bytes = 0L
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                            url = a.optString("browser_download_url"); bytes = a.optLong("size"); break
                        }
                    }
                    val r = Release(
                        j.optString("tag_name"), j.optString("name").ifBlank { j.optString("tag_name") },
                        j.optString("body"), url, bytes, j.optString("published_at").take(10)
                    )
                    // Update 204: kept with its ETag for the next (conditional) ask.
                    if (r.tag.isNotBlank()) { lastRelease = r; etag = res.header("ETag") }
                    if (newer(r.tag, installed(app).first)) State.Available(r) else State.UpToDate(r.tag)
                }
            }.getOrElse { State.Failed("Couldn't reach GitHub (${it.javaClass.simpleName}). Check the connection.") }
            val now = System.currentTimeMillis()
            lastChecked.value = now
            // Update 202: an automatic check that failed keeps what Settings showed (it tries again later on its own).
            // (Update 204: nor does it replace a download under way or one waiting to install, started meanwhile.)
            val cur = state.value
            if (!(auto && (result is State.Failed || cur is State.Downloading || cur is State.Ready))) state.value = result
            else if (auto && result is State.Failed && cur is State.Checking) state.value = before
            // Update 202: the last check that reached GitHub, kept across restarts.
            if (result !is State.Failed) prefs(app).edit().putLong(KEY_LAST, now).apply()
            // Update 204: a failed automatic check tries again after RETRY_MS (not the full spacing).
            if (auto) lastFailed = if (result is State.Failed) now else 0L
            if (auto && result is State.Available && result.release.apkUrl != null && !isSkipped(result.release.tag) &&
                !laterFor(result.release.tag, now)
            ) {
                prompt.value = result.release
            }
        }
    }

    /** Update 204: the newest release seen and its ETag (this run only), for the conditional ask. */
    @Volatile private var lastRelease: Release? = null
    @Volatile private var etag: String? = null

    /** Downloads [r]'s APK, then opens Android's installer for it. */
    fun download(c: Context, r: Release) {
        val app = c.applicationContext
        val url = r.apkUrl ?: run { state.value = State.Failed("That release has no APK attached."); return }
        if (state.value is State.Downloading) return
        state.value = State.Downloading(r, 0, r.apkBytes)
        worker.execute {
            val dir = File(app.externalCacheDir ?: app.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "voltcap-${r.tag.removePrefix("v")}.apk")
            state.value = runCatching {
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) return@use State.Failed("Download failed (${res.code}).")
                    val body = res.body ?: return@use State.Failed("Download failed (empty).")
                    val total = body.contentLength().takeIf { it > 0 } ?: r.apkBytes
                    var done = 0L; var shown = 0L
                    body.byteStream().use { input ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                o.write(buf, 0, n); done += n
                                if (done - shown > 512 * 1024) { shown = done; state.value = State.Downloading(r, done, total) }
                            }
                        }
                    }
                    State.Ready(r, out)
                }
            }.getOrElse { out.delete(); State.Failed("Download stopped (${it.javaClass.simpleName}). Try again.") }
            (state.value as? State.Ready)?.let { install(app, it.file) }
        }
    }

    /** Hands [apk] to Android's installer (which asks before installing). */
    fun install(c: Context, apk: File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(c, c.packageName + ".fileprovider", apk)
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        }.onFailure { state.value = State.Failed("Couldn't open the installer (${it.javaClass.simpleName}).") }
    }

    /** Whether Android lets Voltcap install apps (Android 8+ asks once, in its own settings screen). */
    fun canInstall(c: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    /** Android's screen to allow installs from Voltcap. */
    fun allowInstalls(c: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        runCatching {
            c.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + c.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ---- Checking on its own ----

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    /*
     * Update 202: these stay in their own "app_updates" prefs file (where Update 141 already kept "auto" and "last")
     * rather than in Features: everything in Features is copied into layout presets and Undo snapshots, and putting a
     * preset back must never switch the update check off again or forget a skipped version.
     *   "auto"    Boolean  true  "Check for updates automatically"
     *   "last"    Long     0     when a check last reached GitHub (ms)
     *   "skipped" String   ""    the version "Skip this version" was picked for ("v0.3.0"); "" = none
     */
    private const val KEY_AUTO = "auto"
    private const val KEY_LAST = "last"
    private const val KEY_SKIPPED = "skipped"

    /**
     * Update 204: while Voltcap stays on screen, an automatic check every hour (it was every 12 hours — a version
     * released the same day was never noticed until the next day).
     */
    const val AUTO_EVERY_MS = 3600_000L
    /** Update 204: after an automatic check that failed (GitHub down, rate limit), the next try waits this long. */
    private const val RETRY_MS = 15L * 60_000L
    /**
     * Update 204: Voltcap starting and coming back to it (from a game, an app) check too, at most this often — a quick
     * restart or popping in and out of a game doesn't ask GitHub every time. (Before, coming back didn't check at all
     * within the 12 hours.)
     */
    private const val START_MIN_GAP_MS = 10L * 60_000L
    /** Update 204: "Later" holds the question for this release this long (or until Voltcap starts again). */
    private const val LATER_MS = 12L * 3600_000L

    /** Update 202: the newer release the home screen should ask about now ("Voltcap x is out"), or null. */
    val prompt = mutableStateOf<Release?>(null)

    /** Update 202: the skipped version as Compose state ("" = none), so Settings and the badge follow it. */
    val skipped = mutableStateOf("")

    /** Update 202: "Check for updates automatically" as Compose state (mirrors the "auto" pref). */
    val auto = mutableStateOf(true)

    @Volatile private var loaded = false
    /** Update 204: when an automatic check last asked GitHub (this run), and when one last failed (0 = it didn't). */
    @Volatile private var lastAttempt = 0L
    @Volatile private var lastFailed = 0L
    /** Update 204: the release "Later" was picked for, and when (this run only: a new start asks again). */
    @Volatile private var laterTag: String? = null
    @Volatile private var laterAt = 0L

    private fun laterFor(tag: String, now: Long): Boolean = laterTag == tag && now - laterAt in 0L until LATER_MS

    /** Update 202: reads the prefs into [skipped] / [auto] once per run. */
    fun load(c: Context) {
        if (loaded) return
        val p = prefs(c)
        skipped.value = p.getString(KEY_SKIPPED, "") ?: ""
        auto.value = p.getBoolean(KEY_AUTO, true)
        loaded = true
    }

    /** "Check for updates automatically" (default on). */
    fun autoCheck(c: Context): Boolean { load(c); return auto.value }
    fun setAutoCheck(c: Context, on: Boolean) {
        load(c)
        auto.value = on
        prefs(c).edit().putBoolean(KEY_AUTO, on).apply()
        // Switched off: nothing pops up any more this run either.
        if (!on) prompt.value = null
    }

    /** When a check last reached GitHub (ms, 0 = never), kept across restarts. */
    fun lastCheckedAt(c: Context): Long = prefs(c).getLong(KEY_LAST, 0L)

    /** Update 202: whether [tag] is the skipped version (or older than it). */
    fun isSkipped(tag: String): Boolean {
        val s = skipped.value
        return s.isNotBlank() && !newer(tag, s)
    }

    /** Update 202: "Skip this version" — never asked about [tag] again (Settings still shows it and can install it). */
    fun skip(c: Context, tag: String) {
        load(c)
        skipped.value = tag
        prefs(c).edit().putString(KEY_SKIPPED, tag).apply()
        if (prompt.value?.tag == tag) prompt.value = null
    }

    /** Update 202: Settings' "Ask about it again". */
    fun unskip(c: Context) {
        load(c)
        skipped.value = ""
        prefs(c).edit().remove(KEY_SKIPPED).apply()
    }

    /**
     * Update 202: "Later" — no question for now. Update 204: not again for this version for 12 hours while Voltcap
     * runs (the checks are hourly now), and again at the next start.
     */
    fun later() {
        prompt.value?.let { laterTag = it.tag; laterAt = System.currentTimeMillis() }
        prompt.value = null
    }

    /**
     * Update 202: whether Settings' "Updates & downloads" gets its dot: a newer version is known (or downloading /
     * downloaded) and it isn't the skipped one. Reads Compose state, so the dot follows on its own.
     */
    fun hasNews(): Boolean = when (val s = state.value) {
        is State.Available -> !isSkipped(s.release.tag)
        is State.Downloading -> !isSkipped(s.release.tag)
        is State.Ready -> !isSkipped(s.release.tag)
        else -> false
    }

    /** Update 202: only with a connection (the automatic check never waits on a timeout offline). */
    private fun online(c: Context): Boolean = runCatching {
        val cm = c.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION") (cm.activeNetworkInfo?.isConnected == true)
        }
    }.getOrDefault(true)

    /**
     * Update 202: the automatic check (quiet: never shows an error).
     * Update 204: [atStart] a little after Voltcap starts, [resumed] on coming back to it (from a game, an app): each
     * when no check ran in the last 10 minutes. Otherwise (the hourly look while Voltcap is on screen) once an hour has
     * passed. Before, everything after the start waited 12 hours, so a version out the same day was never asked about.
     * A failed try waits 15 minutes before the next. Only with a connection.
     */
    fun autoCheckIfDue(c: Context, atStart: Boolean = false, resumed: Boolean = false) {
        load(c)
        // The start's check comes first (a little after start); until then coming back to Voltcap doesn't check.
        if (atStart) startRan = true
        if (!startRan || !auto.value) return
        val now = System.currentTimeMillis()
        if (now - lastFailed in 0L until RETRY_MS) return
        // The last ask: this run's, else the one saved (a restart within 10 minutes doesn't ask again).
        val last = maxOf(lastAttempt, lastCheckedAt(c))
        val gap = if (atStart || resumed) START_MIN_GAP_MS else AUTO_EVERY_MS
        if (now - last in 0L until gap) return
        if (!online(c)) return
        if (state.value is State.Checking || state.value is State.Downloading) return
        lastAttempt = now
        check(c, auto = true)
    }
    @Volatile private var startRan = false

    /** Update 141's name for the start's check (kept for any caller). */
    fun checkOnStart(c: Context) = autoCheckIfDue(c, atStart = true)
}
