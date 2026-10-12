package com.shelf.launcher.data

import android.content.Context
import com.shelf.launcher.ui.theme.AppTheme

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun load(): AppTheme {
        // New installs start on Voltcap's own theme; saved choices stay.
        val name = prefs.getString(KEY, null) ?: return AppTheme.VOLT
        // Falls back to Voltcap's own theme rather than crashing if a theme was renamed/removed
        // (update 20 removed QUEST, REEF, JUNGLE, SEWER_CITY).
        return runCatching { AppTheme.valueOf(name) }.getOrDefault(AppTheme.VOLT)
    }

    fun save(theme: AppTheme) {
        prefs.edit().putString(KEY, theme.name).apply()
    }

    companion object {
        private const val KEY = "selected_theme"
    }
}
