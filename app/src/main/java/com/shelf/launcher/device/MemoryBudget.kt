package com.shelf.launcher.device

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context

/**
 * How much memory Voltcap lets itself use. Small handhelds (3–4 GB of RAM, or Android's own
 * "low RAM" flag, or a small per-app heap) run the emulator right next to the launcher, so there
 * Voltcap keeps far fewer decoded pictures around, decodes them leaner, and lets go of them the
 * moment a game starts. Worked out once per process.
 */
object MemoryBudget {
    @Volatile private var cached: Boolean? = null

    /** True on handhelds where every megabyte counts. */
    fun small(context: Context): Boolean = cached ?: detect(context.applicationContext).also { cached = it }

    private fun detect(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching false
        if (am.isLowRamDevice) return@runCatching true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val gb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        (gb > 0.1 && gb < 6.5) || am.memoryClass <= 256
    }.getOrDefault(false)

    /**
     * Update 159: Settings › Effects & battery › Keep art in memory (on by default): read once when the image loader is
     * made. Off: the old behaviour (small handhelds keep much less and drop it all when a game starts).
     */
    val keepArt: Boolean by lazy { runCatching { com.shelf.launcher.data.Features.keepArt.value }.getOrDefault(true) }

    /** Share of the app's memory for decoded art: small devices keep much less (unless the art is kept, [keepArt]). */
    fun imageCacheShare(context: Context): Double = if (keepArt) 0.3 else if (small(context)) 0.12 else 0.2

    /** Android is really short of memory (not just Voltcap going to the background). */
    private fun critical(level: Int): Boolean =
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL

    /**
     * Update 159: the image loader's memory cache, kept through Android's ordinary trims ([keepArt]): Coil empties its
     * cache itself whenever the app goes to the background, so every picture was read from storage again on return.
     */
    class KeepingCache(private val inner: coil.memory.MemoryCache) : coil.memory.MemoryCache by inner {
        override fun trimMemory(level: Int) {
            if (!keepArt || critical(level)) inner.trimMemory(level)
        }
    }

    /** Widest a full-screen background is decoded (px). Full 4K heroes cost ~30 MB each. */
    fun heroMaxWidth(context: Context): Int = if (small(context)) 1280 else 1920

    /**
     * Voltcap went into the background (a game started) or Android asked for memory back: drop the
     * decoded pictures and other caches. They come back from the disk store in a moment.
     */
    fun release(context: Context, level: Int) {
        val app = context.applicationContext
        // Update 159: with the art kept, only when Android is really short.
        if (keepArt && !critical(level)) return
        val full = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && small(app)) ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        runCatching {
            val cache = coil.Coil.imageLoader(app).memoryCache ?: return@runCatching
            if (full) cache.clear() else cache.trimMemory(level)
        }
        if (full) runCatching { com.shelf.launcher.ui.MusicTheme.trim() }
    }
}
