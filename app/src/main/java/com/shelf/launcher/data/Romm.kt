package com.shelf.launcher.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * RomM (https://romm.app), your own ROM server (update 93): browse its library and download games onto the handheld.
 *
 * Signing in: the server's address and a Client API token (RomM › Administration › Client API Tokens, "rmm_…"), or a
 * user name and password. Requests go to <server>/api with "Authorization: Bearer <token>" (or Basic).
 *
 * Downloads go through Android's own download manager (it keeps going with the screen off or Voltcap closed, shows its
 * progress in the notifications, and resumes after a dropped connection) into Voltcap's own folder, then the finished
 * file is moved into the console's folder in your ROM folder (an existing one with a name Voltcap knows, else a new
 * folder named after RomM's console folder) and the library is scanned again. Games that are several files come as a
 * zip and are unpacked into their own folder.
 */

data class RommCredentials(val server: String, val token: String, val user: String, val password: String) {
    /** The server address without a trailing slash (http:// added when it has no scheme). */
    val base: String get() = server.trim().trimEnd('/').let { if ("://" in it) it else "http://$it" }
    val authHeader: String get() =
        if (token.isNotBlank()) "Bearer ${token.trim()}"
        else "Basic " + Base64.encodeToString("$user:$password".toByteArray(), Base64.NO_WRAP)
}

class RommCredentialsStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = SecureStore.open(app, "romm_credentials_encrypted", "RomM", listOf("romm_credentials")).prefs

    fun load(): RommCredentials? {
        val server = prefs.getString("server", null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString("token", "").orEmpty()
        val user = prefs.getString("user", "").orEmpty()
        val pass = prefs.getString("password", "").orEmpty()
        if (token.isBlank() && user.isBlank()) return null
        return RommCredentials(server, token, user, pass)
    }

    fun save(c: RommCredentials) {
        prefs.edit().putString("server", c.server.trim()).putString("token", c.token.trim())
            .putString("user", c.user.trim()).putString("password", c.password).apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}

data class RommPlatform(val id: Int, val slug: String, val fsSlug: String, val name: String, val romCount: Int) {
    /** The Voltcap console this RomM platform is (by its folder name, then its slug), or null. */
    val voltcapPlatform: String? get() = RomScanner.platformForFolderName(fsSlug) ?: RomScanner.platformForFolderName(slug)
}

data class RommRom(
    val id: Int, val platformId: Int, val name: String, val fsName: String, val sizeBytes: Long,
    val multi: Boolean, val coverPath: String?, val summary: String?
)

class RommPage(val items: List<RommRom>, val total: Int)

object Romm {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(c: RommCredentials, path: String): String {
        val req = Request.Builder().url(c.base + path).header("Authorization", c.authHeader).header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw RommException("RomM refused the sign-in (check the token or password).")
            if (!r.isSuccessful) throw RommException("RomM answered ${r.code}.")
            return r.body?.string() ?: throw RommException("RomM sent nothing back.")
        }
    }

    /** Your RomM consoles that have games, biggest first. */
    suspend fun platforms(c: RommCredentials): List<RommPlatform> = withContext(Dispatchers.IO) {
        val arr = JsonParser.parseString(get(c, "/api/platforms")).asJsonArrayOrNull() ?: JsonArray()
        arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            RommPlatform(
                id = o.int("id") ?: return@mapNotNull null,
                slug = o.str("slug").orEmpty(),
                fsSlug = o.str("fs_slug").orEmpty(),
                name = o.str("display_name") ?: o.str("custom_name") ?: o.str("name").orEmpty(),
                romCount = o.int("rom_count") ?: 0
            )
        }.filter { it.romCount > 0 }.sortedByDescending { it.romCount }
    }

    /** A page of [platformId]'s games (all consoles when null), optionally matching [search]. */
    suspend fun roms(c: RommCredentials, platformId: Int?, search: String, offset: Int, limit: Int = 60): RommPage = withContext(Dispatchers.IO) {
        val q = buildString {
            append("/api/roms?limit=").append(limit).append("&offset=").append(offset).append("&order_by=name&order_dir=asc")
            if (platformId != null) append("&platform_ids=").append(platformId)
            if (search.isNotBlank()) append("&search_term=").append(Uri.encode(search.trim()))
        }
        val root = JsonParser.parseString(get(c, q))
        // Newer RomM answers a page object ({items, total}); older ones a plain list.
        val items = (root as? JsonObject)?.getAsJsonArray("items") ?: root.asJsonArrayOrNull() ?: JsonArray()
        val total = (root as? JsonObject)?.int("total") ?: items.size()
        RommPage(items.mapNotNull { e -> (e as? JsonObject)?.let(::romOf) }, total)
    }

    private fun romOf(o: JsonObject): RommRom? {
        val id = o.int("id") ?: return null
        val name = o.str("name") ?: o.str("fs_name_no_ext") ?: o.str("fs_name") ?: return null
        val fsName = o.str("fs_name") ?: return null
        return RommRom(
        id = id,
        platformId = o.int("platform_id") ?: 0,
        name = name,
        fsName = fsName,
        sizeBytes = o.get("fs_size_bytes")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
        multi = o.get("has_multiple_files")?.takeIf { !it.isJsonNull }?.asBoolean ?: (o.get("multi")?.takeIf { !it.isJsonNull }?.asBoolean ?: false),
        coverPath = o.str("path_cover_small") ?: o.str("path_cover_large"),
        summary = o.str("summary")
        )
    }

    /** The full address of a cover path from RomM (it gives them relative to the server). */
    fun coverUrl(c: RommCredentials, path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> c.base + (if (path.startsWith("/")) path else "/$path")
    }

    /** True when the server answers with these sign-in details. */
    suspend fun test(c: RommCredentials): String? = runCatching { platforms(c); null }.getOrElse { it.message ?: "Couldn't reach RomM." }

    // ---- Downloads ----

    /** Where a RomM game goes: the console's folder in your ROM folders (an existing one Voltcap recognises, else a new one). */
    fun targetDir(context: Context, platform: RommPlatform): File {
        val roots = LibrarySettingsStore(context).loadRootPaths().map { File(it) }
        val want = platform.voltcapPlatform
        if (want != null) {
            for (root in roots) {
                val match = root.listFiles()?.firstOrNull { it.isDirectory && RomScanner.platformForFolderName(it.name) == want }
                if (match != null) return match
            }
        }
        val name = platform.fsSlug.ifBlank { platform.slug }.ifBlank { "romm" }
        return File(roots.first(), name)
    }

    /** The file (or folder, for games of several files) the game is saved as. */
    fun targetFor(context: Context, platform: RommPlatform, rom: RommRom): File =
        File(targetDir(context, platform), if (rom.multi) rom.fsName.substringBeforeLast('.', rom.fsName) else rom.fsName)

    fun isOnDevice(context: Context, platform: RommPlatform, rom: RommRom): Boolean = runCatching {
        val f = targetFor(context, platform, rom)
        f.exists() && (f.isDirectory || f.length() > 0)
    }.getOrDefault(false)

    /** Download state per RomM game id: 0..1 while downloading, null when not. */
    private val _progress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val progress: StateFlow<Map<Int, Float>> get() = _progress

    /** The last thing that went wrong (or finished), for the browser to show. */
    val message = MutableStateFlow<String?>(null)

    private fun pending(context: Context) = context.getSharedPreferences("romm_downloads", Context.MODE_PRIVATE)

    /** Starts downloading [rom] (nothing when it's already downloading). */
    fun download(context: Context, c: RommCredentials, platform: RommPlatform, rom: RommRom): String? {
        val app = context.applicationContext
        val prefs = pending(app)
        if (prefs.all.values.any { (it as? String)?.startsWith("${rom.id}\t") == true }) return null
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return "Android's download manager isn't available."
        val target = targetFor(app, platform, rom)
        val fileName = if (rom.multi) "${target.name}.zip" else rom.fsName
        val url = c.base + "/api/roms/${rom.id}/content/" + Uri.encode(if (rom.multi) "${target.name}.zip" else rom.fsName)
        return runCatching {
            val req = DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("Authorization", c.authHeader)
                .setTitle(rom.name)
                .setDescription("RomM › ${platform.name}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(app, "romm", "${rom.id}_$fileName")
            val id = dm.enqueue(req)
            // id → "romId \t target path \t multi"
            prefs.edit().putString(id.toString(), "${rom.id}\t${target.absolutePath}\t${rom.multi}").apply()
            _progress.value = _progress.value + (rom.id to 0f)
            null
        }.getOrElse { "Couldn't start the download: ${it.message}" }
    }

    fun cancel(context: Context, romId: Int) {
        val app = context.applicationContext
        val prefs = pending(app)
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        prefs.all.forEach { (k, v) ->
            if ((v as? String)?.startsWith("$romId\t") == true) {
                runCatching { dm.remove(k.toLong()) }
                prefs.edit().remove(k).apply()
            }
        }
        _progress.value = _progress.value - romId
    }

    /**
     * Checks the running downloads: updates their progress, moves finished ones into the ROM folder (unpacking games
     * of several files) and forgets failed ones. Returns how many games were added. Call off the main thread.
     */
    fun poll(context: Context): Int {
        val app = context.applicationContext
        val prefs = pending(app)
        val all = prefs.all
        if (all.isEmpty()) { if (_progress.value.isNotEmpty()) _progress.value = emptyMap(); return 0 }
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return 0
        var added = 0
        val progress = HashMap<Int, Float>()
        for ((key, value) in all) {
            val parts = (value as? String)?.split('\t') ?: continue
            if (parts.size < 3) { prefs.edit().remove(key).apply(); continue }
            val romId = parts[0].toIntOrNull() ?: continue
            val target = File(parts[1])
            val multi = parts[2].toBoolean()
            val id = key.toLongOrNull() ?: continue
            val cursor = runCatching { dm.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close(); prefs.edit().remove(key).apply(); continue
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val local = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            cursor.close()
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = local?.let { Uri.parse(it).path }?.let(::File)
                    val ok = file != null && file.exists() && runCatching { place(file, target, multi) }.getOrDefault(false)
                    if (ok) added++ else message.value = "Downloaded, but couldn't move it to ${target.parent} (does Voltcap have access to all files?)."
                    runCatching { dm.remove(id) }
                    prefs.edit().remove(key).apply()
                }
                DownloadManager.STATUS_FAILED -> {
                    message.value = "A RomM download failed" + (if (reason == 401 || reason == 403) ": RomM refused the sign-in." else " (error $reason).")
                    runCatching { dm.remove(id) }
                    prefs.edit().remove(key).apply()
                }
                else -> progress[romId] = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
            }
        }
        _progress.value = progress
        if (added > 0) message.value = if (added == 1) "1 game downloaded and added." else "$added games downloaded and added."
        return added
    }

    /** Moves the downloaded [file] to [target] (a folder for a zip of several files, unpacked there). */
    private fun place(file: File, target: File, multi: Boolean): Boolean {
        target.parentFile?.mkdirs()
        if (!multi) {
            if (target.exists()) target.delete()
            if (file.renameTo(target)) return true
            file.copyTo(target, overwrite = true)
            file.delete()
            return target.length() > 0
        }
        target.mkdirs()
        val canon = target.canonicalPath + File.separator
        java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val out = File(target, e.name)
                // Never outside the game's folder (a "../" entry).
                if (!out.canonicalPath.startsWith(canon)) continue
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        file.delete()
        return true
    }

    // ---- JSON helpers ----
    private fun com.google.gson.JsonElement.asJsonArrayOrNull(): JsonArray? = if (isJsonArray) asJsonArray else null
    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
    private fun JsonObject.int(k: String): Int? = get(k)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asInt
}

class RommException(message: String) : Exception(message)
