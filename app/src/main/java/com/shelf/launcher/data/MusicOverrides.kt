package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * Music of your own for one platform ("p:<platform>") or one game ("g:<stableKey>"): it plays while you are
 * on that platform / game and fades into the next one when you leave (Features.musicCrossfade). A game's own
 * track wins over its platform's. Files are copied into the app's storage (prefs "music_overrides", this device).
 */
object MusicOverrides {
    private const val MAX_BYTES = 30L * 1024 * 1024

    /** Bumped when something is set or cleared, so the player re-reads. */
    val version = mutableStateOf(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("music_overrides", Context.MODE_PRIVATE)
    private fun dir(context: Context) = File(context.applicationContext.filesDir, "music_overrides").apply { mkdirs() }

    fun platformKey(platform: String) = "p:$platform"
    fun gameKey(stableKey: String) = "g:$stableKey"

    fun file(context: Context, key: String): File? =
        prefs(context).getString("f:$key", null)?.let { File(dir(context), it) }?.takeIf { it.exists() }

    fun name(context: Context, key: String): String? = prefs(context).getString("n:$key", null)

    /** The override that applies: the game's own, else its platform's; null when neither has one. */
    fun active(context: Context, gameStableKey: String?, platform: String?): String? {
        if (gameStableKey != null && file(context, gameKey(gameStableKey)) != null) return gameKey(gameStableKey)
        if (platform != null && file(context, platformKey(platform)) != null) return platformKey(platform)
        return null
    }

    /** Copies the picked audio file in. Returns an error text, or null when it worked. */
    fun import(context: Context, key: String, uri: Uri): String? {
        val resolver = context.contentResolver
        val display = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Your music"
        val ext = display.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: "audio"
        val target = File(dir(context), "${key.hashCode().toUInt().toString(16)}.$ext")
        val temp = File(dir(context), "incoming.tmp")
        return try {
            var total = 0L
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) { temp.delete(); return "That file is too big — pick one under 30 MB." }
                        out.write(buffer, 0, read)
                    }
                }
            } ?: return "Couldn't open that file."
            clearFile(context, key)
            if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
            prefs(context).edit().putString("f:$key", target.name).putString("n:$key", display).apply()
            version.value++
            null
        } catch (e: Exception) {
            temp.delete()
            "Couldn't use that file: ${e.message}"
        }
    }

    private fun clearFile(context: Context, key: String) {
        prefs(context).getString("f:$key", null)?.let { File(dir(context), it).delete() }
    }

    fun clear(context: Context, key: String) {
        clearFile(context, key)
        prefs(context).edit().remove("f:$key").remove("n:$key").apply()
        version.value++
    }
}
