package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson

/**
 * Keeps downloaded game details (summary, score, playtime…) on the device,
 * keyed by Game.stableKey, so the details window opens instantly next time —
 * even offline — instead of asking IGDB again after every restart.
 */
class MetadataCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("metadata_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Gson fills objects by reflection and ignores Kotlin's null-safety, so a
    // cached entry written without genres comes back with a null list even
    // though the type says it can't be. The compiler can't know that and
    // calls the ?: below useless — it isn't, so that warning is silenced here.
    @Suppress("USELESS_ELVIS")
    fun loadAll(): Map<String, GameMetadata> =
        prefs.all.mapNotNull { (key, value) ->
            val json = value as? String ?: return@mapNotNull null
            runCatching { gson.fromJson(json, GameMetadata::class.java) }
                .getOrNull()
                ?.let { key to it.copy(genres = it.genres ?: emptyList()) }
        }.toMap()

    fun put(stableKey: String, metadata: GameMetadata) {
        prefs.edit().putString(stableKey, gson.toJson(metadata)).apply()
    }

    fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
