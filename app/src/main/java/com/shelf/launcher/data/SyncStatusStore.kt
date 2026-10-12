package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncState { SYNCED, PENDING, UPLOADING, DOWNLOADING, ERROR, CONFLICT, NEEDS_SIGN_IN, OFFLINE }
enum class SyncDirection { UPLOAD, DOWNLOAD, NONE }

/** Where one game's saves stand with Google Drive. Times are epoch millis (0 = never). */
data class GameSyncStatus(
    val state: SyncState = SyncState.PENDING,
    val lastSuccessAt: Long = 0L,
    val lastDirection: SyncDirection = SyncDirection.NONE,
    /** Files moved by the last successful transfer. */
    val fileCount: Int = 0,
    val message: String? = null,
    val updatedAt: Long = 0L
)

/**
 * Per-game sync status (Game.stableKey → [GameSyncStatus]) in the "sync_status" prefs — plain, non-secret,
 * device-local (see SettingsScopes). [all] lets the UI react without reading files.
 */
object SyncStatusStore {
    private const val PREFS = "sync_status"
    private const val KEY = "by_game"
    private const val MAX_ENTRIES = 500

    private val gson = Gson()
    private val type = object : TypeToken<Map<String, GameSyncStatus>>() {}.type
    private val _all = MutableStateFlow<Map<String, GameSyncStatus>>(emptyMap())
    val all: StateFlow<Map<String, GameSyncStatus>> = _all.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val json = prefs(context).getString(KEY, null) ?: return
        _all.value = runCatching { gson.fromJson<Map<String, GameSyncStatus>>(json, type) }.getOrNull().orEmpty()
            .filterValues { it != null }
    }

    fun get(context: Context, key: String): GameSyncStatus? {
        ensureLoaded(context)
        return _all.value[key]
    }

    /** Loads the saved map into [all] (call once at start so the UI has it). */
    fun load(context: Context) = ensureLoaded(context)

    /** Forgets a game's entry (a sync that found nothing to move for a game that never had saves). */
    @Synchronized
    fun remove(context: Context, key: String) {
        ensureLoaded(context)
        if (key !in _all.value) return
        val map = _all.value - key
        _all.value = map
        prefs(context).edit().putString(KEY, gson.toJson(map)).apply()
    }

    @Synchronized
    fun update(context: Context, key: String, transform: (GameSyncStatus) -> GameSyncStatus) {
        ensureLoaded(context)
        val old = _all.value[key] ?: GameSyncStatus()
        val changed = transform(old)
        // Callers that don't set updatedAt themselves still get "now".
        val stamped = if (changed.updatedAt == old.updatedAt) changed.copy(updatedAt = System.currentTimeMillis()) else changed
        var map = _all.value + (key to stamped)
        if (map.size > MAX_ENTRIES) {
            map = map.entries.sortedByDescending { it.value.updatedAt }.take(MAX_ENTRIES).associate { it.key to it.value }
        }
        _all.value = map
        prefs(context).edit().putString(KEY, gson.toJson(map)).apply()
    }
}
