package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists scraped art URLs to disk, keyed by Game.stableKey.
 *
 * Without this, art lived only in memory: every app start re-scraped the
 * entire library from SteamGridDB, which is slow, wasteful of a rate-limited
 * API, and means a cold start shows placeholder gradients until the whole
 * library has been walked again. Now the cache loads instantly at startup and
 * only games with no entry get fetched.
 *
 * Only URLs are stored — the images themselves stay in Coil's own cache, so
 * this file remains small even for a large library.
 */
class ArtCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("art_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, GameArt>>() {}.type

    // Writes are gathered and saved together a moment later, off the calling thread: a scrape used to read
    // and rewrite the whole (large) art file once per game, on the main thread. What's waiting is merged
    // with the file at save time, so anything else that wrote the file meanwhile (an import) is kept.
    private val lock = Any()
    private val pending = LinkedHashMap<String, GameArt>()
    private val pendingRemovals = HashSet<String>()
    private var flushScheduled = false

    private fun readDisk(): Map<String, GameArt> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, GameArt>>(json, mapType) }.getOrNull().orEmpty()
    }

    fun loadAll(): Map<String, GameArt> = synchronized(lock) {
        if (pending.isEmpty() && pendingRemovals.isEmpty()) readDisk()
        else readDisk() - pendingRemovals + pending
    }

    /** Merges one game's art into the cache. Called incrementally as a
     * background scrape progresses, so interrupting it never loses the work
     * already done (saved within a second). */
    fun put(stableKey: String, art: GameArt) {
        synchronized(lock) {
            pending[stableKey] = art
            pendingRemovals -= stableKey
        }
        scheduleFlush()
    }

    /** Drops these games' scraped art (wrong matches). */
    fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        synchronized(lock) {
            keys.forEach { pending -= it }
            pendingRemovals += keys
        }
        scheduleFlush()
    }

    fun clear() {
        synchronized(lock) {
            pending.clear()
            pendingRemovals.clear()
            prefs.edit().remove(KEY).apply()
        }
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushScheduled) return
            flushScheduled = true
        }
        writer.schedule({ flush() }, 800, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun flush() {
        synchronized(lock) {
            flushScheduled = false
            if (pending.isEmpty() && pendingRemovals.isEmpty()) return
            val updated = readDisk() - pendingRemovals + pending
            pending.clear()
            pendingRemovals.clear()
            prefs.edit().putString(KEY, gson.toJson(updated)).apply()
        }
    }

    companion object {
        private const val KEY = "art_json"
        private val writer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "art-cache").apply { isDaemon = true }
        }
    }
}
