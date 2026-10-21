package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Update 301: a picture you upload in Artwork & details (cover, background, logo, box, back, spine, full case scan…) is
 * copied into Voltcap's own storage on this device, so it keeps working after the original file is moved or deleted (it
 * used to be a link to the file). Never uploaded or shared anywhere. (Clearing a pick stops using it; the copy stays in Voltcap's
 * storage, shared by any other game that uses the same picture.)
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
}
