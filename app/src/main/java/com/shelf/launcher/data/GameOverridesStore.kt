package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Manual per-game ID overrides — a SteamGridDB game ID, a RetroAchievements
 * game ID, or both — keyed by Game.stableKey. */
data class GameOverride(
    val sgdbGameId: Int? = null,
    val raGameId: Int? = null
)

/**
 * Stores manual per-game ID overrides. Separate from EmulatorSettingsStore
 * since this is keyed by individual game, not by platform.
 *
 * Note: this used to store a bare Int (SteamGridDB ID only) per key. That
 * shape can't parse as a GameOverride object, so loadAll() falls back to an
 * empty map if it hits old data in that format rather than crashing — a
 * one-time reset of manual IDs on upgrade, not a runtime failure.
 */
class GameOverridesStore(context: Context) {
    private val prefs = context.getSharedPreferences("game_overrides", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, GameOverride>>() {}.type

    fun loadAll(): Map<String, GameOverride> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, GameOverride>>(json, mapType) }.getOrDefault(emptyMap())
    }

    fun setArtId(stableKey: String, sgdbGameId: Int?) {
        update(stableKey) { it.copy(sgdbGameId = sgdbGameId) }
    }

    fun setRaId(stableKey: String, raGameId: Int?) {
        update(stableKey) { it.copy(raGameId = raGameId) }
    }

    private fun update(stableKey: String, transform: (GameOverride) -> GameOverride) {
        val current = loadAll().toMutableMap()
        val updated = transform(current[stableKey] ?: GameOverride())
        if (updated == GameOverride()) current.remove(stableKey) else current[stableKey] = updated
        prefs.edit().putString(KEY, gson.toJson(current)).apply()
    }

    companion object {
        private const val KEY = "overrides_json"
    }
}
