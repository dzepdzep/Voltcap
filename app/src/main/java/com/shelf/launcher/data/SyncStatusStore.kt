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
    val updatedAt: Long = 0L,
    /**
     * Update 493 (D292): when files last actually moved (0 = not recorded: entries from before 493, or never). A check that
     * moves nothing updates [lastSuccessAt] only, so the two tell "checked" from "uploaded / downloaded".
     */
    val lastTransferAt: Long = 0L
)

/** Update 493 (D292): what Save Vault says about a game's last sync — from the recorded events only, nothing guessed. */
data class SyncLine(val at: Long, val label: String)

fun syncLine(st: GameSyncStatus): SyncLine? {
    fun files() = if (st.fileCount > 0) " · ${st.fileCount} file${if (st.fileCount == 1) "" else "s"}" else ""
    return when (st.state) {
        SyncState.OFFLINE -> SyncLine(st.updatedAt, "Offline · sync skipped")
        SyncState.ERROR -> SyncLine(st.updatedAt, "Sync failed")
        SyncState.NEEDS_SIGN_IN -> SyncLine(st.updatedAt, "Sign-in needed")
        SyncState.CONFLICT -> SyncLine(st.updatedAt, "Conflict found")
        SyncState.UPLOADING -> SyncLine(st.updatedAt, "Uploading")
        SyncState.DOWNLOADING -> SyncLine(st.updatedAt, "Downloading")
        SyncState.SYNCED, SyncState.PENDING -> when {
            st.lastSuccessAt <= 0L -> null
            // The last successful sync itself moved files: say which way.
            st.lastTransferAt > 0L && st.lastTransferAt == st.lastSuccessAt -> SyncLine(st.lastSuccessAt, when (st.lastDirection) {
                SyncDirection.UPLOAD -> "Uploaded to Drive" + files()
                SyncDirection.DOWNLOAD -> "Downloaded from Drive" + files()
                SyncDirection.NONE -> "Synced with Drive"
            })
            // Recorded: files moved earlier, the last check found nothing new.
            st.lastTransferAt > 0L -> SyncLine(st.lastSuccessAt, "Checked · no changes")
            // Never moved anything, as far as recorded.
            st.lastDirection == SyncDirection.NONE -> SyncLine(st.lastSuccessAt, "Checked · no changes")
            // Older entry: a transfer happened once, but whether the last check moved anything wasn't recorded.
            else -> SyncLine(st.lastSuccessAt, "Last checked")
        }
    }
}

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
