package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Game manuals (PDF) for the details window. Looked for in this order:
 *  1. next to the ROM: `<rom name>.pdf`, or in a `manuals` / `Manuals` folder beside it;
 *  2. Voltcap's own `manuals` folder (files/manuals/<platform>/<rom name>.pdf);
 *  3. ScreenScraper's scanned manual (when connected), downloaded once and kept in (2).
 * Update 202: (3) is now ManualLinks — the address is noted while scraping and the PDF downloads into the saved-art store
 * the first time it's opened ([find] is no longer used; manuals it saved in (2) still open from there).
 */
object Manuals {
    private fun base(game: Game): String? = game.romPath?.replace('\\', '/')?.substringAfterLast('/')?.substringBeforeLast('.')

    private fun ownDir(context: Context, game: Game) = File(context.filesDir, "manuals/" + game.platform.replace(Regex("[^A-Za-z0-9 _-]"), "_"))

    /** The file name a manual is kept under: the ROM's name, or (apps, Steam games) the game's own name. */
    private fun fileName(game: Game): String = base(game) ?: game.name.replace(Regex("[^A-Za-z0-9 _-]"), "_")

    fun local(context: Context, game: Game): File? {
        // Apps and Steam games have no ROM: a PDF picked for them is kept under the game's name (see import).
        val name = fileName(game)
        val romDir = game.romPath?.takeIf { it.startsWith("/") }?.let { File(it).parentFile }
        val candidates = listOfNotNull(
            romDir?.let { File(it, "$name.pdf") },
            romDir?.let { File(it, "manuals/$name.pdf") },
            romDir?.let { File(it, "Manuals/$name.pdf") },
            File(ownDir(context, game), "$name.pdf")
        )
        return candidates.firstOrNull { it.isFile && it.length() > 1000 }
    }

    /** Copies a PDF the person picked into Voltcap's manuals folder for [game]; the file, or null if it isn't a PDF. */
    suspend fun import(context: Context, game: Game, uri: android.net.Uri): File? = withContext(Dispatchers.IO) {
        val name = fileName(game)
        val dir = ownDir(context, game).apply { mkdirs() }
        val out = File(dir, "$name.pdf")
        val tmp = File(dir, "$name.pdf.part")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val head = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            if (String(head) == "%PDF") { out.delete(); tmp.renameTo(out) } else tmp.delete()
        }.onFailure { tmp.delete() }
        out.takeIf { it.isFile && it.length() > 1000 }
    }

    /** The manual for [game], downloading it if needed; null when there's none. */
    suspend fun find(context: Context, game: Game, fetchUrl: suspend () -> String?): File? = withContext(Dispatchers.IO) {
        local(context, game)?.let { return@withContext it }
        val name = base(game) ?: return@withContext null
        val url = runCatching { fetchUrl() }.getOrNull() ?: return@withContext null
        val dir = ownDir(context, game).apply { mkdirs() }
        val out = File(dir, "$name.pdf")
        val tmp = File(dir, "$name.pdf.part")
        runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            try {
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            // ScreenScraper answers errors as text: only keep a real PDF.
            val head = tmp.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            if (String(head) == "%PDF") tmp.renameTo(out) else tmp.delete()
        }.onFailure { tmp.delete() }
        out.takeIf { it.isFile && it.length() > 1000 }
    }
}
