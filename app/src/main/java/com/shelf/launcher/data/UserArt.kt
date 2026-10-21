package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Update 301: a picture you upload in Artwork & details (cover, background, logo, box, back, spine, full case scan…) is
 * copied into Voltcap's own storage on this device, so it keeps working after the original file is moved or deleted (it
 * used to be a link to the file). Never uploaded or shared anywhere. Update 302: copies no pick uses any more are removed at start
 * ([cleanup]).
 */
object UserArt {
    private const val DIR = "user_art"

    private fun dir(c: Context) = File(c.applicationContext.filesDir, DIR).apply { mkdirs() }

    /** Copies [uri] (blocking — call off the main thread); its address ("file://…"), or null when it couldn't be read. */
    fun importLocal(c: Context, uri: Uri): String? = runCatching {
        val r = c.contentResolver
        val type = r.getType(uri).orEmpty()
        val ext = when {
            type.endsWith("png") -> "png"; type.endsWith("webp") -> "webp"; type.endsWith("gif") -> "gif"
            type.endsWith("jpeg") || type.endsWith("jpg") -> "jpg"
            else -> uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 3..4 && it.all(Char::isLetter) } ?: "img"
        }
        val tmp = File(dir(c), "import_${System.nanoTime()}.tmp")
        val md = java.security.MessageDigest.getInstance("SHA-1")
        r.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n); out.write(buf, 0, n) }
            }
        } ?: return@runCatching null
        if (tmp.length() == 0L) { tmp.delete(); return@runCatching null }
        val name = md.digest().joinToString("") { "%02x".format(it) } + "." + ext
        val dest = File(dir(c), name)
        if (dest.exists()) tmp.delete() else if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
        Uri.fromFile(dest).toString()
    }.getOrNull()

    /**
     * Update 302: removes the copies no pick refers to any more (your artwork and real-media picks), and the cuts kept for
     * them. Blocking — off the main thread. A copy made in the last hour is kept (its pick may be about to be saved).
     */
    fun cleanup(c: Context) {
        runCatching {
            val app = c.applicationContext
            val used = StringBuilder()
            for (name in listOf("physical_overrides", "art_overrides")) {
                app.getSharedPreferences(name, Context.MODE_PRIVATE).all.values.forEach { used.append(it.toString()).append('\n') }
            }
            val text = used.toString()
            val now = System.currentTimeMillis()
            dir(app).listFiles()?.forEach { f ->
                if (f.isFile && now - f.lastModified() > 60 * 60 * 1000L && !text.contains(f.name)) f.delete()
            }
            FullScanCuts.dropMissingFiles()
            // (Update 305: and the provenance kept for a copy that's gone.)
            app.getSharedPreferences("art_provenance", Context.MODE_PRIVATE).let { p ->
                val gone = p.all.keys.filter { it.startsWith("file://") && runCatching { !File(Uri.parse(it).path!!).exists() }.getOrDefault(false) }
                if (gone.isNotEmpty()) p.edit().apply { gone.forEach { remove(it) } }.apply()
            }
        }
    }
}
