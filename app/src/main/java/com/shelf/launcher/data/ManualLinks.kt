package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * Update 202: game manuals from ScreenScraper, kept as addresses and downloaded only when one is opened.
 *
 * ScreenScraper's answer for a game (the same one the artwork, details, box scans and clips come from) lists its scanned
 * manual as a media of type "manuel", one per region. Whenever Voltcap asks ScreenScraper about a game for anything, the
 * manual's address in the best region (the region your case look uses for that game — CaseCatalog.regionOf — then your
 * language, then world / US / Europe / Japan) is noted here, at no extra request. Nothing is downloaded then: the PDF is
 * fetched the first time you open it (the booklet in the opened case, Manual in the game window or its details) into
 * the saved-art store (ArtFiles: same folder and size limit), with a progress window ([progress]).
 *
 * Addresses are kept without your ScreenScraper login (ssid / sspassword); it's added again when downloading.
 * "" means "asked, ScreenScraper has no manual for it".
 */
object ManualLinks {
    private const val PREFS = "manual_urls"
    @Volatile private var prefs: SharedPreferences? = null
    private val urls = mutableStateMapOf<String, String>()

    /** Opens [Game]'s manual (found, or downloaded first) — set by the home screen (LauncherScreen), used everywhere else. */
    @Volatile var open: ((Game) -> Unit)? = null

    /** Bumped when a manual arrives on the device (downloaded or picked): booklets show its first page from then on. */
    var version by mutableIntStateOf(0)

    /** A manual being downloaded: the game, bytes done and the total (-1 unknown). */
    class Progress(val game: Game, val done: Long, val total: Long)

    /** The download under way, for its progress window; null when none. */
    var progress by mutableStateOf<Progress?>(null)

    /** Stops the download under way (set while one runs). */
    @Volatile var cancel: (() -> Unit)? = null

    /** The last download was stopped by you (B in its progress window), not failed. */
    @Volatile var stoppedLast = false

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = runCatching { p.all }.getOrNull().orEmpty()
        val loaded = HashMap<String, String>()
        all.forEach { (k, v) -> if (v is String) loaded[k] = v }
        prefs = p
        // (Written straight into the global state: the scrapers note addresses from several threads at once, where
        // separate snapshots could clash.)
        urls.putAll(loaded)
    }

    /** [key]'s manual address (without the login), or null: none known. Read in composition, it follows changes. */
    fun url(key: String): String? = urls[key]?.takeIf { it.isNotBlank() }

    /** Whether ScreenScraper was asked about [key]'s manual already (an address or "none"). */
    fun known(key: String): Boolean = urls.containsKey(key)

    /** Notes [key]'s manual address ([url] null: ScreenScraper has none). Any thread. */
    fun note(key: String, url: String?) {
        val p = prefs ?: return
        val v = url?.let { withoutLogin(it) }.orEmpty()
        if (urls[key] == v) return
        urls[key] = v
        p.edit().putString(key, v).apply()
    }

    /**
     * Update 204: Settings › Artwork › Scraping › Manuals — find every game's manual on ScreenScraper (and download them
     * all onto the device if asked), in the background with a progress line. Set by the view model.
     */
    @Volatile var runAll: ((download: Boolean) -> Unit)? = null
    @Volatile var stopAll: (() -> Unit)? = null

    /** Update 204: the find / download under way — what it's doing ([downloading]), done / total and manuals found so far. */
    class Bulk(val downloading: Boolean, val done: Int, val total: Int, val found: Int)

    /** Update 204: the find / download under way, for its progress line; null when none. */
    var bulk by mutableStateOf<Bulk?>(null)

    /** Update 204: how many games have a manual address noted (read in composition, it follows changes). */
    fun foundCount(): Int = urls.values.count { it.isNotBlank() }

    /** Forgets every noted address (Settings › clear caches). */
    fun clear() {
        urls.clear()
        prefs?.edit()?.clear()?.apply()
    }

    /** [url] without the person's ScreenScraper login (it's never kept in the noted addresses). */
    fun withoutLogin(url: String): String = url.toHttpUrlOrNull()?.newBuilder()
        ?.removeAllQueryParameters("ssid")
        ?.removeAllQueryParameters("sspassword")
        ?.build()?.toString() ?: url

    /**
     * ScreenScraper's region codes in the order a manual is picked for [game]: the region its case is drawn in (from the
     * file name, else Settings › Game look › Case region), with your language's own country first in Europe.
     */
    fun regionOrder(game: Game): List<String> {
        val region = runCatching { com.shelf.launcher.ui.CaseCatalog.regionOf(game.romPath ?: game.name).name }.getOrDefault("US")
        val lang = java.util.Locale.getDefault().language.lowercase()
        val own = when (lang) {
            "de" -> "de"; "fr" -> "fr"; "es" -> "sp"; "it" -> "it"; "nl" -> "nl"; "sv" -> "se"; "pt" -> "pt"
            "da" -> "dk"; "fi" -> "fi"; "no", "nb", "nn" -> "no"; "pl" -> "pl"; "en" -> "uk"
            else -> null
        }
        return when (region) {
            "EU" -> listOfNotNull(own, "eu", "uk", "wor", "us", "ss", "jp")
            "JP" -> listOf("jp", "wor", "us", "eu", "uk", "ss")
            else -> listOf("us", "wor", "ca", "uk", "eu", "ss", "jp")
        }.distinct()
    }

    /** The manual already on the device: a PDF beside the ROM / picked by you (Manuals.local), or one downloaded before. IO. */
    fun fileOnDevice(context: Context, game: Game): File? {
        Manuals.local(context, game)?.let { return it }
        val u = url(game.stableKey) ?: return null
        ArtFiles.init(context)
        return ArtFiles.manualFile(u)
    }

    /**
     * Downloads [game]'s manual from [rawUrl] (the noted address; [loginUrl] the address to fetch, with the login) into
     * the saved-art store, showing [progress] meanwhile. The file, or null (failed, not a PDF, or stopped: [stoppedLast]).
     */
    suspend fun download(context: Context, game: Game, rawUrl: String, loginUrl: String): File? = withContext(Dispatchers.IO) {
        ArtFiles.init(context)
        val job = coroutineContext[Job]
        stoppedLast = false
        cancel = { stoppedLast = true }
        progress = Progress(game, 0L, -1L)
        try {
            var shownAt = 0L
            val file = ArtFiles.downloadManual(rawUrl, loginUrl, onProgress = { done, total ->
                // (Not every 64 KB: about ten updates a second at most.)
                val now = android.os.SystemClock.uptimeMillis()
                if (now - shownAt > 90L || done == total) { shownAt = now; progress = Progress(game, done, total) }
            }, cancelled = { stoppedLast || job?.isActive == false })
            if (file != null) version++
            file
        } finally {
            progress = null
            cancel = null
        }
    }

    private val firstPages = android.util.LruCache<String, Bitmap>(6)

    /** One PDF page drawn at a time in the whole app (the reader and the booklets' covers): the renderer isn't made for more. */
    val pdfLock = Any()

    /**
     * The manual's first page, small (about [height] px tall), for the booklet in an opened case; null when the manual
     * isn't on the device yet (the booklet then wears the game's cover). IO.
     */
    fun firstPage(context: Context, game: Game, height: Int = 420): Bitmap? {
        val file = fileOnDevice(context.applicationContext, game) ?: return null
        val key = file.absolutePath + "|" + file.length() + "|" + height
        firstPages.get(key)?.let { return it }
        val bmp = runCatching { synchronized(pdfLock) {
            val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            // (The renderer closes the file when it's closed.)
            val renderer = try { android.graphics.pdf.PdfRenderer(pfd) } catch (e: Exception) { pfd.close(); throw e }
            try {
                val r = renderer
                if (r.pageCount < 1) null else r.openPage(0).use { p ->
                    val h = height.coerceIn(64, 1200)
                    val w = (h.toFloat() * p.width / p.height.coerceAtLeast(1)).toInt().coerceIn(32, 1600)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                        b.eraseColor(android.graphics.Color.WHITE)
                        p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            } finally {
                runCatching { renderer.close() }
            }
        } }.getOrNull() ?: return null
        firstPages.put(key, bmp)
        return bmp
    }
}
