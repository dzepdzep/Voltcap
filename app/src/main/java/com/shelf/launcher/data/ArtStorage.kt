package com.shelf.launcher.data

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import java.io.File

/**
 * Where artwork lives on the device.
 *
 * Coil (the image loader) normally keeps downloaded images in the app's
 * *cache* folder, which Android is free to wipe when storage runs low, and it
 * honours the servers' "expire after" headers. So art had to be re-downloaded
 * now and then, and never worked offline.
 *
 * This moves the image store into the app's own files folder — which Android
 * never clears on its own — and ignores expiry headers, so once an image is
 * downloaded it stays until the size limit pushes out the least-recently used
 * ones or the person clears it in Settings.
 */
object ArtStorage {
    private const val FOLDER = "saved_art"

    @Volatile private var installed: ImageLoader? = null

    /**
     * The app's one image loader, with the permanent art store, made once per process and installed as Coil's
     * default (update 89). Before, the activity replaced it with a plain loader whose disk cache was the wipeable
     * cache folder every time it was recreated, so art went to the wrong place, the saved art looked "missing", and
     * it was saved again on the next start. Two disk caches on one folder also aren't safe, hence one instance.
     */
    fun install(context: Context, maxBytes: Long): ImageLoader {
        val loader = installed ?: synchronized(this) {
            installed ?: createImageLoader(context.applicationContext, maxBytes).also { installed = it }
        }
        coil.Coil.setImageLoader(loader)
        return loader
    }

    private const val FAILED = "art_failed"
    /** How long an image that the server refused (404, 403…) is left alone before it's tried again. */
    private const val RETRY_FAILED_MS = 3L * 24 * 60 * 60 * 1000

    /** Images the server refused recently: not counted as "missing", not downloaded again at every start. */
    fun recentlyFailed(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(FAILED, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return prefs.all.mapNotNullTo(HashSet()) { (url, at) -> url.takeIf { at is Long && now - at < RETRY_FAILED_MS } }
    }

    fun markFailed(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val now = System.currentTimeMillis()
        val e = context.getSharedPreferences(FAILED, Context.MODE_PRIVATE).edit()
        urls.forEach { e.putLong(it, now) }
        e.apply()
    }

    fun forgetFailed(context: Context) {
        context.getSharedPreferences(FAILED, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** The store is (nearly) full: saving more would only push out art that's already saved, at every start. */
    fun nearlyFull(used: Long, maxBytes: Long): Boolean = maxBytes < (1L shl 40) && used >= maxBytes * 9 / 10

    /** Coil's own files: "journal", "journal.bkp", "journal.tmp" and entries named "<sha-256>.<n>" (".tmp" while written). */
    private val COIL_FILE = Regex("journal(\\.bkp|\\.tmp)?|[0-9a-f]{64}\\.\\d+(\\.tmp)?")

    /**
     * Update 144: the image cache gets its own folder (Art/cache, saved_art/cache). It used to be the art folder
     * itself, so the saved art (Art/files, saved_art/files — ArtFiles) sat inside it, and an unreadable cache
     * journal (a crash, or the launcher killed when a game starts) made the cache empty its folder — saved art
     * included. Now the two are side by side. The old cache's files are moved into the new folder once (so
     * "Save all" can still copy from them); the old folder is never cleared, which would take the saved art too.
     */
    private fun cacheDir(context: Context): File {
        val art = AppStorage.dir(context, "Art", context.filesDir.resolve(FOLDER), migrate = false)
        val cache = File(art, "cache")
        runCatching {
            cache.mkdirs()
            val old = art.listFiles { f -> f.isFile && COIL_FILE.matches(f.name) }.orEmpty()
            if (old.isNotEmpty()) {
                // A journal already in the new folder: it's the one in use; the leftovers are dropped.
                val keep = !File(cache, "journal").exists()
                old.forEach { f -> if (!keep || !f.renameTo(File(cache, f.name))) f.delete() }
            }
        }
        return cache
    }

    fun createImageLoader(context: Context, maxBytes: Long): ImageLoader {
        // Update 114: the saved art's own folder (plain files, kept for good — ArtFiles.kt).
        ArtFiles.init(context)
        ArtFiles.maxBytes = maxBytes
        Thread { runCatching { ArtFiles.tidy() } }.start()
        return ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir(context))
                    .maxSizeBytes(maxBytes)
                    .build()
            }
            // Decoded art stays in memory so scrolling back over games never decodes them again —
            // a fifth of the app's budget, much less on small handhelds (MemoryBudget), where the
            // emulator needs the room.
            .memoryCache {
                coil.memory.MemoryCache.Builder(context)
                    .maxSizePercent(com.shelf.launcher.device.MemoryBudget.imageCacheShare(context))
                    .build()
            }
            // Small handhelds: pictures without transparency (covers, backgrounds) decode at
            // 2 bytes a pixel instead of 4 — half the memory, hardly visible on these screens.
            .allowRgb565(com.shelf.launcher.device.MemoryBudget.small(context))
            .respectCacheHeaders(false)
            // GIF / animated WebP / APNG covers and heroes play when Animated artwork is on (AnimatedArt).
            .components {
                add(AnimatedArt.Factory())
                // "voltcrop:" models: the spine / front cut out of a game's full box scan (BoxTexture.kt).
                add(com.shelf.launcher.ui.BoxCropInterceptor())
                // Update 114: saved pictures from their files; your games' pictures saved the first time they load.
                add(SavedArtInterceptor())
            }
            .build()
    }
}

data class ArtStorageSettings(
    /** Size limit of the on-device art store, MB; [UNLIMITED] = no limit. */
    val maxMegabytes: Int = 1024,
    val autoSave: Boolean = true,
    /** Also fetch and keep every game's details (summary, score, playtimes). */
    val autoSaveDetails: Boolean = true
) {
    val unlimited: Boolean get() = maxMegabytes == UNLIMITED

    /** The limit in bytes for the image loader (a terabyte stands in for "no limit"). */
    val maxBytes: Long get() = if (unlimited) 1L shl 40 else maxMegabytes * 1024L * 1024L

    companion object {
        const val UNLIMITED = -1
    }
}

class ArtStorageStore(context: Context) {
    private val prefs = context.getSharedPreferences("art_storage", Context.MODE_PRIVATE)

    fun load() = ArtStorageSettings(
        maxMegabytes = prefs.getInt("max_mb", 1024),
        autoSave = prefs.getBoolean("auto_save", true),
        autoSaveDetails = prefs.getBoolean("auto_save_details", true)
    )

    fun save(s: ArtStorageSettings) = prefs.edit()
        .putInt("max_mb", s.maxMegabytes)
        .putBoolean("auto_save", s.autoSave)
        .putBoolean("auto_save_details", s.autoSaveDetails)
        .apply()
}
