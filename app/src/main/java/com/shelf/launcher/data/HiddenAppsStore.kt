package com.shelf.launcher.data

import android.content.Context

/** Package names the person has chosen to hide from the app half of the
 * library (e.g. Settings, Files — anything that isn't actually a game). */
class HiddenAppsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_apps", Context.MODE_PRIVATE)

    fun loadAll(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    fun hide(packageName: String) {
        prefs.edit().putStringSet(KEY, loadAll() + packageName).apply()
    }

    fun unhide(packageName: String) {
        prefs.edit().putStringSet(KEY, loadAll() - packageName).apply()
    }

    companion object {
        private const val KEY = "hidden_package_names"
    }
}
