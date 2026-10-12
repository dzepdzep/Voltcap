package com.shelf.launcher.data

import android.content.Context

/** Games the person has starred, by Game.stableKey — survives rescans, since
 * stableKey is the ROM path / package name / steam:<id>, not the title. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun loadAll(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun toggle(stableKey: String): Set<String> {
        val current = loadAll()
        val updated = if (stableKey in current) current - stableKey else current + stableKey
        prefs.edit().putStringSet(KEY, updated).apply()
        return updated
    }

    /** Adds [keys] (import from another frontend); returns the new set. */
    fun addAll(keys: Set<String>): Set<String> {
        val updated = loadAll() + keys
        prefs.edit().putStringSet(KEY, updated).apply()
        return updated
    }

    companion object {
        private const val KEY = "favorite_keys"
    }
}
