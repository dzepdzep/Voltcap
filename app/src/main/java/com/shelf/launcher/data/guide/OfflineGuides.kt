package com.shelf.launcher.data.guide

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Guide pages saved for reading offline, per game: the page exactly as it was shown (a web archive,
 * .mht, written by Android's own WebView), kept in Voltcap's private files - for your own reading,
 * like a browser's "save page". They're never shared or uploaded; delete them any time.
 */
object OfflineGuides {
    data class Saved(val title: String, val url: String, val file: String, val savedAt: Long)

    private val gson = Gson()
    private val type = object : TypeToken<List<Saved>>() {}.type

    private fun dir(context: Context, gameKey: String) =
        File(context.filesDir, "guides_offline/" + gameKey.hashCode().toUInt().toString(16)).apply { mkdirs() }

    private fun index(context: Context, gameKey: String) = File(dir(context, gameKey), "index.json")

    fun list(context: Context, gameKey: String): List<Saved> = runCatching {
        gson.fromJson<List<Saved>>(index(context, gameKey).readText(), type)
    }.getOrNull().orEmpty().filter { File(it.file).isFile }

    /** Where a new archive for [title] goes. */
    fun newFile(context: Context, gameKey: String, title: String): File {
        val safe = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifEmpty { "guide" }
        return File(dir(context, gameKey), "${safe}_${System.currentTimeMillis()}.mht")
    }

    /** Records an archive that was written to [file]. */
    fun add(context: Context, gameKey: String, title: String, url: String, file: File) {
        val old = list(context, gameKey)
        // Saving the same page again replaces it: the earlier archive goes, so copies don't pile up.
        old.filter { it.url == url && it.file != file.path }.forEach { runCatching { File(it.file).delete() } }
        val l = old.filter { it.url != url } + Saved(title, url, file.path, System.currentTimeMillis())
        runCatching { index(context, gameKey).writeText(gson.toJson(l)) }
    }

    fun remove(context: Context, gameKey: String, saved: Saved) {
        runCatching { File(saved.file).delete() }
        val l = list(context, gameKey).filter { it.file != saved.file }
        runCatching { index(context, gameKey).writeText(gson.toJson(l)) }
    }

    /** True when [path] is one of Voltcap's saved guides (the reader only opens local files from there). */
    fun isSavedFile(context: Context, path: String): Boolean =
        runCatching { File(path).canonicalPath.startsWith(File(context.filesDir, "guides_offline").canonicalPath) }.getOrDefault(false)
}
