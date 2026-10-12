package com.shelf.launcher.data

import android.content.Context

/**
 * Optional sort order per category, on top of the global one in Settings ›
 * Library. Keys come from LauncherUiState.sortCategoryKey. Prefs
 * "category_sort" (GLOBAL scope).
 */
class CategorySortStore(context: Context) {
    private val prefs = context.getSharedPreferences("category_sort", Context.MODE_PRIVATE)

    fun loadAll(): Map<String, SortMode> =
        prefs.all.mapNotNull { (k, v) ->
            val mode = runCatching { SortMode.valueOf(v as? String ?: "") }.getOrNull()
            if (mode == null) null else k to mode
        }.toMap()

    /** Sets or (with null) clears one category's sort. */
    fun set(key: String, mode: SortMode?) {
        val e = prefs.edit()
        if (mode == null) e.remove(key) else e.putString(key, mode.name)
        e.apply()
    }

    /** Forgets every category choice (they all follow the global sort again). */
    fun clearAll() = prefs.edit().clear().apply()
}
