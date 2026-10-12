package com.shelf.launcher.data

import android.content.Context

/**
 * Where the ROMs are: one or more folders (Settings › Library › ROM folders),
 * and whether console folders are recognised at any depth ("deep search").
 * The first folder is the main one — storage links and the BIOS check use it.
 */
class LibrarySettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    fun loadRootPath(): String = loadRootPaths().first()

    /** Every ROM folder, main one first. Older saves had just one. */
    fun loadRootPaths(): List<String> {
        val many = prefs.getString(KEY_ROOT_PATHS, null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
        if (!many.isNullOrEmpty()) return many
        return listOf(prefs.getString(KEY_ROOT_PATH, DEFAULT_ROOT_PATH) ?: DEFAULT_ROOT_PATH)
    }

    fun saveRootPath(path: String) {
        val rest = loadRootPaths().drop(1).filter { it != path }
        saveRootPaths(listOf(path) + rest)
    }

    fun saveRootPaths(paths: List<String>) {
        val clean = paths.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { listOf(DEFAULT_ROOT_PATH) }
        prefs.edit()
            .putString(KEY_ROOT_PATHS, clean.joinToString("\n"))
            .putString(KEY_ROOT_PATH, clean.first()) // what older builds read
            .apply()
    }

    fun loadDeepSearch(): Boolean = prefs.getBoolean(KEY_DEEP, true)

    fun saveDeepSearch(on: Boolean) {
        prefs.edit().putBoolean(KEY_DEEP, on).apply()
    }

    companion object {
        private const val KEY_ROOT_PATH = "root_path"
        private const val KEY_ROOT_PATHS = "root_paths"
        private const val KEY_DEEP = "deep_search"
        const val DEFAULT_ROOT_PATH = "/storage/emulated/0/ROMs"
    }
}
