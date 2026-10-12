package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences

/** Which source artwork is scraped from first. The others fill in whatever
 * the first one doesn't have. */
enum class ArtSource(val label: String) {
    STEAMGRIDDB("SteamGridDB"),
    THEGAMESDB("TheGamesDB"),
    SCREENSCRAPER("ScreenScraper (ROMs only)"),
    IGDB("IGDB (covers, artwork, screenshots)"),
    RAWG("RAWG (backgrounds and screenshots, no covers)")
}

/** Encrypted at rest, same approach as the other credential stores. Also
 * remembers which source is currently selected. */
class TgdbKeyStore(context: Context) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences = SecureStore.open(app, ENCRYPTED, "TheGamesDB", listOf(FALLBACK)).prefs

    fun loadKey(): String? = prefs.getString(KEY_API, null)
    fun saveKey(key: String) {
        prefs.edit().putString(KEY_API, key).apply()
        SecureStore.clearRelogin(app, "TheGamesDB")
    }
    fun clearKey() = prefs.edit().remove(KEY_API).apply()

    fun loadSource(): ArtSource {
        val name = prefs.getString(KEY_SOURCE, null) ?: return ArtSource.STEAMGRIDDB
        return runCatching { ArtSource.valueOf(name) }.getOrDefault(ArtSource.STEAMGRIDDB)
    }

    fun saveSource(source: ArtSource) = prefs.edit().putString(KEY_SOURCE, source.name).apply()


    companion object {
        private const val ENCRYPTED = "tgdb_credentials_encrypted"
        private const val FALLBACK = "tgdb_credentials"
        private const val KEY_API = "api_key"
        private const val KEY_SOURCE = "art_source"
    }
}
