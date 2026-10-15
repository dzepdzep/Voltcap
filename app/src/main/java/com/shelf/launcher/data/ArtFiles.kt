package com.shelf.launcher.data

import android.content.Context
import coil.intercept.Interceptor
import coil.request.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/*
 * Update 114: the art saved on the device as plain files, kept for good.
 *
 * Before, saved art lived in the image loader's disk cache: one journal for every picture, which a crash or a killed
 * launcher (a game starting on a small handheld) could leave unreadable — and an unreadable journal makes the cache
 * throw everything away. That's why "Saving art" ran again after updates and restarts and the store showed a few MB
 * for hundreds of games. Now each picture is its own file (named after its address), written whole or not at all:
 *  - every picture of your games the home screen shows is saved the first time it's downloaded ([SavedArtInterceptor]);
 *  - "Save all" fetches the rest (covers, wide art, backgrounds, logos, RetroAchievements icons, screenshots and box /
 *    disc / spine scans), first copying whatever the old cache still has;
 *  - a saved picture is always drawn from its file, never downloaded again.
 * The folder is Art/files in the Voltcap folder when that's in use, else the app's own files (both are looked in).
 */
object ArtFiles {
    /**
     * Update 119: every folder saved pictures may be in — the chosen one first (where new ones go), then the other.
     * A launcher starting with the device can come up before the memory is ready: the Voltcap folder then wasn't
     * there for a moment, the app's own folder was used instead, and everything looked unsaved and was fetched again
     * after a restart. Now both are always looked in, and the choice is made again when the chosen one appears.
     */
    @Volatile private var roots: List<File> = emptyList()
    @Volatile private var app: Context? = null
    /** The addresses worth keeping (your games' art); others load as before, through the cache. */
    @Volatile var wanted: Set<String> = emptySet()
    /** The store's size limit (Settings › Metadata › art store size). */
    @Volatile var maxBytes: Long = 1L shl 40
    @Volatile private var used: Long = -1L
    @Volatile private var resolvedAt = 0L
    /**
     * Update 144: the saved pictures by file name, read once in the background ([tidy]) and kept up to date, so the
     * image loader can tell a saved picture apart without touching the disk — it asks on the main thread, for every
     * picture shown (see [SavedArtInterceptor]).
     */
    private val names = java.util.concurrent.ConcurrentHashMap<String, File>()
    /** True once [names] has been read from the folders. */
    @Volatile var indexed = false
        private set

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS) // (Update 182)
            .followRedirects(true)
            .build()
    }

    /** Sets the folders up (once per process; the image loader calls it). */
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        resolve()
    }

    private fun resolve() {
        val c = app ?: return
        val inApp = File(File(c.filesDir, "saved_art"), "files")
        val visible = File(File(AppStorage.rootFolder(), "Art"), "files")
        roots = if (AppStorage.active(c)) listOf(visible, inApp) else listOf(inApp, visible)
        resolvedAt = android.os.SystemClock.uptimeMillis()
    }

    /** Where new pictures go: the chosen folder when it can be made, else the other one. */
    private fun target(): File? {
        // The chosen folder wasn't ready at start (see [roots]): looked at again now and then.
        if (android.os.SystemClock.uptimeMillis() - resolvedAt > 60_000L) resolve()
        return roots.firstOrNull { it.isDirectory || it.mkdirs() }
    }

    private fun name(url: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    /** Where [url]'s saved picture is (in any of the folders), or where it would go; null before [init]. */
    fun fileFor(url: String): File? {
        val n = name(url)
        roots.forEach { r -> File(r, n).let { if (it.isFile && it.length() > 0L) { names.putIfAbsent(n, it); return it } } }
        return target()?.let { File(it, n) }
    }

    /** Update 144: [url]'s saved picture from the list in memory (no disk access); null when it isn't listed. */
    fun savedFile(url: String): File? = names[name(url)]

    fun has(url: String): Boolean = fileFor(url)?.let { it.isFile && it.length() > 0L } == true

    /** Bytes the saved pictures take (counted once, then kept up to date). */
    fun usedBytes(): Long {
        if (used < 0L) used = roots.distinct().sumOf { r -> r.listFiles()?.sumOf { if (it.isFile && !it.name.endsWith(".part")) it.length() else 0L } ?: 0L }
        return used
    }

    fun full(): Boolean = maxBytes < (1L shl 40) && usedBytes() >= maxBytes * 9 / 10

    /** Keeps [bytes] of an already downloaded picture (a copy from the old cache). */
    fun keep(url: String, from: File): Boolean = write(url) { out -> from.inputStream().use { it.copyTo(out) } }

    /**
     * Downloads [url] into the store. True when it's saved; false when the server refused it ([refused] set) or the
     * connection failed.
     */
    fun download(url: String, refused: (() -> Unit)? = null): Boolean = runCatching {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Voltcap").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) { if (r.code in 400..499) refused?.invoke(); return@runCatching false }
            val body = r.body ?: return@runCatching false
            // Not a picture (an error page served with 200): left out.
            val type = body.contentType()?.type
            if (type != null && type != "image" && type != "application" && type != "binary") return@runCatching false
            write(url) { out -> body.byteStream().use { it.copyTo(out) } }
        }
    }.getOrDefault(false)

    /** Writes a picture whole (a part file, renamed when complete), so a stopped download never leaves half a file. */
    private fun write(url: String, fill: (java.io.OutputStream) -> Unit): Boolean {
        val f = fileFor(url) ?: return false
        f.parentFile?.mkdirs()
        val part = File(f.parentFile, f.name + "." + Thread.currentThread().id + ".part")
        return runCatching {
            part.outputStream().buffered().use(fill)
            if (part.length() <= 0L) { part.delete(); return false }
            val old = if (f.exists()) f.length() else 0L
            if (f.exists()) f.delete()
            if (!part.renameTo(f)) { part.delete(); names.remove(f.name); return false }
            names[f.name] = f
            if (used >= 0L) used += f.length() - old
            true
        }.getOrElse { part.delete(); false }
    }

    /**
     * Update 202: the art client for manuals (PDFs of many MB): the same connections, with time for a big file on a slow
     * line (the 2-minute limit is for pictures).
     */
    private val manualClient by lazy {
        client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(15, TimeUnit.MINUTES).build()
    }

    /** Update 202: where a manual downloaded while the store was full went (the app's cache folder). */
    private fun manualOverflow(key: String): File? = app?.let { File(File(it.cacheDir, "manuals"), name(key) + ".pdf") }

    /** Update 202: the manual saved for [key] (its address without the login), in the store or its overflow; null: none. */
    fun manualFile(key: String): File? {
        fileFor(key)?.takeIf { it.isFile && it.length() > 1000L }?.let { return it }
        return manualOverflow(key)?.takeIf { it.isFile && it.length() > 1000L }
    }

    /**
     * Update 202: downloads a game's manual (PDF) the first time it's opened, into this store — the same folders and
     * size limit as the saved art — named after [key] (its address without the login); [url] is fetched (with the login).
     * When the store is full it goes to the app's cache folder instead (Android may clear that one). Only a real PDF is
     * kept (ScreenScraper answers some errors with text). [onProgress] gets bytes done and the total (-1: unknown);
     * [cancelled] is asked between chunks and stops it. The file, or null.
     */
    fun downloadManual(key: String, url: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): File? = runCatching {
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Voltcap").build()
        manualClient.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@runCatching null
            val body = r.body ?: return@runCatching null
            val total = body.contentLength()
            val copy: (java.io.OutputStream) -> Unit = { out ->
                val input = java.io.BufferedInputStream(body.byteStream(), 64 * 1024)
                input.use { inp ->
                    // A PDF starts with "%PDF".
                    inp.mark(16)
                    val sig = ByteArray(4)
                    var got = 0
                    while (got < 4) { val k = inp.read(sig, got, 4 - got); if (k < 0) break; got += k }
                    if (got < 4 || String(sig, Charsets.ISO_8859_1) != "%PDF") throw java.io.IOException("not a PDF")
                    inp.reset()
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    onProgress(0L, total)
                    while (true) {
                        if (cancelled()) throw java.io.InterruptedIOException("stopped")
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            if (!full()) {
                if (write(key, copy)) manualFile(key) else null
            } else {
                val dest = manualOverflow(key) ?: return@runCatching null
                dest.parentFile?.mkdirs()
                val part = File(dest.parentFile, dest.name + ".part")
                try {
                    part.outputStream().buffered().use(copy)
                    if (dest.exists()) dest.delete()
                    if (part.renameTo(dest)) dest else null
                } finally {
                    part.delete()
                }
            }
        }
    }.getOrNull()

    /** Every saved picture removed (Settings › Metadata › Clear saved art). */
    fun clear() {
        roots.forEach { r -> r.listFiles()?.forEach { it.delete() } }
        names.clear()
        used = 0L
    }

    /**
     * Leftover part files of downloads cut short (process killed mid-way). Update 144: also reads the list of saved
     * pictures ([names]) and their total size, once, off the main thread (the image loader calls it on its own thread).
     */
    fun tidy() {
        var total = 0L
        roots.distinct().forEach { r ->
            r.listFiles()?.forEach { f ->
                if (f.name.endsWith(".part")) f.delete()
                else if (f.isFile) {
                    val size = f.length()
                    if (size > 0L) names.putIfAbsent(f.name, f)
                    total += size
                }
            }
        }
        if (used < 0L) used = total
        indexed = true
    }
}

/**
 * Update 114: draws a saved picture from its file, and saves one of your games' pictures the first time it's
 * downloaded (so the home screen never needs the network for it again). Other pictures go on as before.
 */
internal class SavedArtInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val url = when (data) {
            is String -> data
            is android.net.Uri -> data.toString()
            is okhttp3.HttpUrl -> data.toString()
            else -> null
        }
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return chain.proceed(chain.request)
        // Update 144: this runs on the main thread for every picture (memory-cache hits too), so a saved one is found
        // in the list kept in memory; the disk is only looked at (off the main thread) for your games' pictures not
        // listed yet, or for any picture in the first moment after start, before the list has been read.
        val file = ArtFiles.savedFile(url) ?: if (ArtFiles.indexed && url !in ArtFiles.wanted) null else withContext(Dispatchers.IO) {
            val f = ArtFiles.fileFor(url)
            when {
                f == null -> null
                f.isFile && f.length() > 0L -> f
                url !in ArtFiles.wanted || ArtFiles.full() -> null
                ArtFiles.download(url) && f.isFile -> f
                else -> null
            }
        }
        if (file == null) return chain.proceed(chain.request)
        // Drawn from the file (its size and any cut-out still apply, as for the address).
        val request = chain.request.newBuilder().data(file).build()
        return chain.proceed(request)
    }
}

/** Update 114: Settings › Metadata › "Scrape spines" — the background look-up's controls and progress (set by the home screen). */
/**
 * Update 147: Settings › Metadata › Scraping (Everything at once) — artwork, details, real cases / discs / spines, screenshots and
 * video clips for every game that's missing them, one after another in the background.
 */
object EverythingScrape {
    enum class Part(val label: String) {
        ART("Artwork"), DETAILS("Details"), SCANS("Real cases, discs & spines"), SHOTS("Screenshots"), VIDEOS("Video clips")
    }
    @Volatile var run: ((Set<Part>) -> Unit)? = null
    @Volatile var stop: (() -> Unit)? = null
    /** Update 148: saves every clip already looked up on the device (Save clips on the device turned on). */
    @Volatile var saveClips: (() -> Unit)? = null
    /** What it's doing now ("Artwork", "Details", "Scans, screenshots & clips") and games done / to do, while it runs. */
    var stage by androidx.compose.runtime.mutableStateOf<String?>(null)
    var progress by androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null)
}

/**
 * Update 205: Settings › Metadata › Scraping › Real media — what the real-media look-ups (Scrape spines, Only the missing
 * real covers, Get everything's cases pass) are doing and what they found, each kind of picture counted on its own:
 * a game whose box front was found isn't counted as done for its back, spine, cartridge or label. Real counts only.
 * Also the library's own count of what every game has and what of it is saved on the device ([library]).
 */
object ScanReport {
    /** The kinds of real-media pictures, as saved for each game, with their names here. */
    val KINDS: List<Pair<String, String>> = listOf(
        "Box" to "Box fronts", "Back of the box" to "Backs", "Spine" to "Spines", "Disc / cartridge" to "Cartridge / disc photos",
        "Disc label" to "Labels", "Box (full scan)" to "Full box scans", "Box (3D)" to "3D boxes"
    )

    var running by androidx.compose.runtime.mutableStateOf(false)
    /** What's being looked up now: the game's name and its console. */
    var game by androidx.compose.runtime.mutableStateOf<String?>(null)
    var console by androidx.compose.runtime.mutableStateOf<String?>(null)
    var done by androidx.compose.runtime.mutableIntStateOf(0)
    var total by androidx.compose.runtime.mutableIntStateOf(0)
    /** Per kind: games it was found for / games ScreenScraper knew but had none of it. */
    var found by androidx.compose.runtime.mutableStateOf<Map<String, Int>>(emptyMap())
    var none by androidx.compose.runtime.mutableStateOf<Map<String, Int>>(emptyMap())
    /** Games ScreenScraper doesn't know (or without a sure match), and look-ups that failed — with the reasons. */
    var unknown by androidx.compose.runtime.mutableIntStateOf(0)
    var failed by androidx.compose.runtime.mutableIntStateOf(0)
    var failReasons by androidx.compose.runtime.mutableStateOf<Map<String, Int>>(emptyMap())
    /** When the last run finished (0: none yet this time). */
    var finishedAt by androidx.compose.runtime.mutableLongStateOf(0L)

    fun start(count: Int) {
        running = true; game = null; console = null; done = 0; total = count
        found = emptyMap(); none = emptyMap(); unknown = 0; failed = 0; failReasons = emptyMap(); finishedAt = 0L
    }

    /** One game looked up: [list] what it has now, [outcome] how the look-up went (ScreenScraperRepository.lookupOutcome). */
    fun tally(list: List<Pair<String, String>>?, outcome: String?) {
        val have = list.orEmpty().map { it.first }.toSet()
        val ssKnown = KINDS.any { it.first in have }
        when {
            ssKnown -> {
                val f = found.toMutableMap(); val n = none.toMutableMap()
                for ((label, _) in KINDS) if (label in have) f[label] = (f[label] ?: 0) + 1 else n[label] = (n[label] ?: 0) + 1
                found = f; none = n
            }
            outcome == null || outcome == OUTCOME_UNKNOWN || outcome == OUTCOME_UNSURE || outcome == OUTCOME_FOUND -> unknown++
            else -> { failed++; failReasons = failReasons + (outcome to ((failReasons[outcome] ?: 0) + 1)) }
        }
        done++
    }

    fun finish() { running = false; game = null; console = null; finishedAt = System.currentTimeMillis() }

    /** The library's count: per kind, how many games have it and how many of those pictures are saved on the device. */
    class Library(val games: Int, val scanned: Int, val have: Map<String, Int>, val saved: Map<String, Int>)
    var library by androidx.compose.runtime.mutableStateOf<Library?>(null)
    /** Counts [library] again (set by the home screen's model). */
    @Volatile var count: (() -> Unit)? = null
    /** Saves every game's real-media pictures on the device now (set by the home screen's model). */
    @Volatile var saveAll: (() -> Unit)? = null
}

object SpineScrape {
    /** Starts it (true: every game without its own spine is asked again, even recently asked ones). */
    @Volatile var run: ((Boolean) -> Unit)? = null
    @Volatile var stop: (() -> Unit)? = null
    /**
     * Update 141: only the games still without real-media pictures (box scan, disc / cartridge, full box) are looked up on
     * ScreenScraper — the ones that have them aren't asked again.
     */
    @Volatile var runMissingReal: (() -> Unit)? = null
    /** Games done / to do while it runs. */
    var progress by androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null)
}
