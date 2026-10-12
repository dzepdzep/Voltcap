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

    /** Looks for a newer release (in the background). */
    fun check(c: Context) {
        val app = c.applicationContext
        if (state.value is State.Checking || state.value is State.Downloading) return
        state.value = State.Checking
        worker.execute {
            state.value = runCatching {
                val req = okhttp3.Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
                client.newCall(req).execute().use { res ->
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
                    if (newer(r.tag, installed(app).first)) State.Available(r) else State.UpToDate(r.tag)
                }
            }.getOrElse { State.Failed("Couldn't reach GitHub (${it.javaClass.simpleName}). Check the connection.") }
            lastChecked.value = System.currentTimeMillis()
        }
    }

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

    /** "Check for updates when Voltcap starts" (default on, at most once a day). */
    fun autoCheck(c: Context): Boolean = prefs(c).getBoolean("auto", true)
    fun setAutoCheck(c: Context, on: Boolean) { prefs(c).edit().putBoolean("auto", on).apply() }

    /** At start: checks quietly when it's on and the last check is over a day old. */
    fun checkOnStart(c: Context) {
        if (!autoCheck(c)) return
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last", 0L) < 24L * 3600_000L) return
        p.edit().putLong("last", now).apply()
        check(c)
    }
}
