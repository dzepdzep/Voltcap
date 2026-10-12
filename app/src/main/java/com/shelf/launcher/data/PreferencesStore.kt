package com.shelf.launcher.data

import android.content.Context

/**
 * UI preferences that were previously held only in memory, so they reset on
 * every app start. Anything the person adjusts should survive a restart.
 */
class PreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    fun badgeModeIsRecent(): Boolean = prefs.getBoolean(KEY_BADGE_RECENT, false)
    fun setBadgeModeIsRecent(value: Boolean) =
        prefs.edit().putBoolean(KEY_BADGE_RECENT, value).apply()

    fun badgeNextToTitle(): Boolean = prefs.getBoolean(KEY_BADGE_PLACEMENT, false)
    fun setBadgeNextToTitle(value: Boolean) =
        prefs.edit().putBoolean(KEY_BADGE_PLACEMENT, value).apply()

    /** Remembers the last category so the launcher reopens where you left it. */
    fun lastPlatformFilter(): String? = prefs.getString(KEY_PLATFORM, null)
    fun setLastPlatformFilter(value: String?) =
        prefs.edit().putString(KEY_PLATFORM, value).apply()

    fun lastSpecialFilter(): String? = prefs.getString(KEY_SPECIAL, null)
    fun setLastSpecialFilter(value: String?) =
        prefs.edit().putString(KEY_SPECIAL, value).apply()

    /** Set once defaults have been applied, so a deliberate change is never
     * overwritten by the defaults running again. */
    fun defaultsApplied(): Boolean = prefs.getBoolean(KEY_DEFAULTS, false)
    fun setDefaultsApplied() = prefs.edit().putBoolean(KEY_DEFAULTS, true).apply()

    companion object {
        private const val KEY_BADGE_RECENT = "badge_recent"
        private const val KEY_BADGE_PLACEMENT = "badge_next_to_title"
        private const val KEY_PLATFORM = "last_platform"
        private const val KEY_SPECIAL = "last_special"
        private const val KEY_DEFAULTS = "defaults_applied"
    }
}
