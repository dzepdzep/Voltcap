package com.shelf.launcher.data

import android.content.Context
import android.content.SharedPreferences

enum class MetadataSource(val label: String, val blurb: String) {
    IGDB("IGDB", "Summary, critic score and rushed/normal/completionist times — needs Twitch client ID + secret"),
    SCREENSCRAPER(
        "ScreenScraper",
        "Best for retro ROMs: summary, rating, genres, year, developer. IGDB fills in playtimes and apps if connected"
    ),
    RAWG(
        "RAWG",
        "One free key: summary, Metacritic score, genres, developer, average playtime and screenshots"
    ),
    COMBINED(
        "Combine all",
        "IGDB, RAWG and (for ROMs) ScreenScraper together — each fills in what the others don't have"
    ),
    NONE("Off", "Details window shows art and achievements only")
}

/** Which metadata source is used. Encrypted at rest, same approach as the
 * other credential stores. */
class MetadataSourceStore(context: Context) {
    private val prefs: SharedPreferences =
        SecureStore.open(context, ENCRYPTED, "Metadata source", listOf(FALLBACK)).prefs

    init {
        // The RAWG key now lives in RawgKeyStore — drop any copy left here.
        if (prefs.contains(LEGACY_KEY_RAWG)) prefs.edit().remove(LEGACY_KEY_RAWG).apply()
    }

    /** Defaults to IGDB. */
    fun loadSource(): MetadataSource {
        val name = prefs.getString(KEY_SOURCE, null) ?: return MetadataSource.IGDB
        return runCatching { MetadataSource.valueOf(name) }.getOrDefault(MetadataSource.IGDB)
    }

    fun saveSource(source: MetadataSource) =
        prefs.edit().putString(KEY_SOURCE, source.name).apply()


    companion object {
        private const val ENCRYPTED = "metadata_source_encrypted"
        private const val FALLBACK = "metadata_source"
        private const val KEY_SOURCE = "source"
        private const val LEGACY_KEY_RAWG = "rawg_key"
    }
}
