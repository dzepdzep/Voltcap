package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Records when each game (by stableKey) was last launched, so the library
 * can surface "continue playing" by sorting recently-played games to the
 * front instead of needing a separate shelf/section. */
class RecentlyPlayedStore(context: Context) {
    private val prefs = context.getSharedPreferences("recently_played", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Long>>() {}.type

    fun loadAll(): Map<String, Long> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, Long>>(json, mapType) }.getOrDefault(emptyMap())
    }

    /** Records [at] only when nothing newer is known for [stableKey]; true when it did. */
    fun recordIfNewer(stableKey: String, at: Long): Boolean {
        if ((loadAll()[stableKey] ?: 0L) >= at) return false
        recordPlayed(stableKey, at)
        return true
    }

    fun recordPlayed(stableKey: String, timestampMillis: Long) {
        val updated = loadAll() + (stableKey to timestampMillis)
        prefs.edit().putString(KEY, gson.toJson(updated)).apply()
    }

    companion object {
        private const val KEY = "last_played_json"
    }
}
