package com.shelf.launcher.data

import android.content.Context
import android.os.SystemClock

/**
 * Where the launcher was when a game or app was started, so coming back
 * (even after Android killed Voltcap while an emulator ran) lands on the same
 * category, game, dock position and details window.
 *
 * [pendingReturn] is only true when the launcher went to the background
 * because Voltcap itself started something; a plain fresh start (launcher
 * icon a day later) never restores stale context.
 */
data class LaunchContext(
    val platformFilter: String?,
    val specialFilter: String?,
    val searchQuery: String,
    val focusedKey: String?,
    val focusedIndex: Int,
    val categoryScreenOpen: Boolean,
    val detailsOpen: Boolean,
    val detailsKey: String?,
    val launchedKey: String?,
    val savedAt: Long,
    val savedElapsed: Long,
    val pendingReturn: Boolean
)

class LaunchContextStore(context: Context) {
    private val prefs = context.getSharedPreferences("launch_context", Context.MODE_PRIVATE)

    fun save(c: LaunchContext) {
        prefs.edit()
            .putString("platform", c.platformFilter)
            .putString("special", c.specialFilter)
            .putString("search", c.searchQuery)
            .putString("focused_key", c.focusedKey)
            .putInt("focused_index", c.focusedIndex)
            .putBoolean("category_open", c.categoryScreenOpen)
            .putBoolean("details_open", c.detailsOpen)
            .putString("details_key", c.detailsKey)
            .putString("launched_key", c.launchedKey)
            .putLong("saved_at", c.savedAt)
            .putLong("saved_elapsed", c.savedElapsed)
            .putBoolean("pending_return", c.pendingReturn)
            .apply()
    }

    fun clearPending() {
        prefs.edit().putBoolean("pending_return", false).apply()
    }

    /** The saved context, only if it's a return from our own launch that is still fresh. */
    fun loadPendingReturn(maxAgeMs: Long = 12 * 60 * 60_000L): LaunchContext? {
        if (!prefs.getBoolean("pending_return", false)) return null
        val savedAt = prefs.getLong("saved_at", 0L)
        val now = System.currentTimeMillis()
        if (savedAt <= 0L || now - savedAt !in 0..maxAgeMs) return null
        // A reboot in between: the launcher was not left for a game any more.
        if (SystemClock.elapsedRealtime() < prefs.getLong("saved_elapsed", 0L)) return null
        return LaunchContext(
            platformFilter = prefs.getString("platform", null),
            specialFilter = prefs.getString("special", null),
            searchQuery = prefs.getString("search", "") ?: "",
            focusedKey = prefs.getString("focused_key", null),
            focusedIndex = prefs.getInt("focused_index", 0),
            categoryScreenOpen = prefs.getBoolean("category_open", false),
            detailsOpen = prefs.getBoolean("details_open", false),
            detailsKey = prefs.getString("details_key", null),
            launchedKey = prefs.getString("launched_key", null),
            savedAt = savedAt,
            savedElapsed = prefs.getLong("saved_elapsed", 0L),
            pendingReturn = true
        )
    }
}
