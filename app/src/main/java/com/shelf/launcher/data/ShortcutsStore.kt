package com.shelf.launcher.data

import android.content.Context

/** Apps pinned to the L1 shortcuts panel, stored as package names and kept
 * in the order they were added. */
class ShortcutsStore(context: Context) {
    private val prefs = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    fun add(packageName: String): List<String> {
        val updated = (load() - packageName) + packageName
        save(updated)
        return updated
    }

    fun remove(packageName: String): List<String> {
        val updated = load() - packageName
        save(updated)
        return updated
    }

    /** Saves a new order (from Edit mode). Anything not listed keeps its place at the end. */
    fun reorder(order: List<String>): List<String> {
        val current = load()
        val updated = order.filter { it in current } + current.filterNot { it in order }
        save(updated)
        return updated
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString("|")).apply()
    }

    companion object {
        private const val KEY = "packages"
    }
}
