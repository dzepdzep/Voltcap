package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Little console logos next to the category names (Appearance › Categories › Console logos): the
 * system icons RetroAchievements uses on its site, looked up once through its console list (needs a
 * RetroAchievements account; kept for 30 days) and matched to Voltcap's console keys.
 */
object ConsoleLogos {
    private const val PREFS = "console_logos"
    private const val MAX_AGE = 30L * 86_400_000L

    /** RA console name (lower case) → icon URL. Compose state, so labels fill in once it's loaded. */
    val icons = mutableStateOf<Map<String, String>>(emptyMap())

    /** Shown at all (the appearance switch). */
    val enabled = mutableStateOf(true)

    /** Extra names RetroAchievements uses for consoles that aren't in the matching map. */
    private val EXTRA = mapOf(
        "3DS" to "Nintendo 3DS", "32X" to "32X", "GameGear" to "Game Gear", "Atari7800" to "Atari 7800",
        "Lynx" to "Atari Lynx", "Jaguar" to "Atari Jaguar", "3DO" to "3DO Interactive Multiplayer",
        "NeoGeoCD" to "Neo Geo CD", "PCEngineCD" to "PC Engine CD/TurboGrafx-CD", "MSX" to "MSX",
        "ColecoVision" to "ColecoVision", "Intellivision" to "Intellivision", "Vectrex" to "Vectrex",
        "PokemonMini" to "Pokemon Mini", "DOS" to "DOS", "C64" to "Commodore 64", "Amiga" to "Amiga",
        "Naomi" to "Arcade", "Atomiswave" to "Arcade", "PSVita" to "PlayStation Vita", "Wii" to "Wii",
        "WiiU" to "Wii U", "Xbox" to "Xbox", "PICO8" to "PICO-8"
    )

    /** The logo for one of Voltcap's console keys ("SNES", "PS1"…), or null. */
    fun iconFor(platform: String?): String? {
        if (!enabled.value) return null
        return raIcon(platform)
    }

    /** RetroAchievements' system icon for a console key, whether or not the category logos are switched on. */
    fun raIcon(platform: String?): String? {
        if (platform == null) return null
        val map = icons.value
        if (map.isEmpty()) return null
        val known = RaMatchStore.PLATFORM_TO_RA_CONSOLE[platform] ?: EXTRA[platform]
        // Consoles we don't know RetroAchievements' name for: only an exact name match (no guessing —
        // "Other apps" must never pick up "Apple II").
        if (known == null) return map[ConsoleNames.display(platform).lowercase()]
        val ra = known.lowercase()
        return map[ra] ?: map.entries.firstOrNull { (k, _) -> k.length >= 4 && (k.contains(ra) || ra.contains(k)) }?.value
    }

    /** Loads the saved list, and refreshes it from RetroAchievements when it's old (blocking work runs on IO). */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("json", null)
        if (saved != null) runCatching { icons.value = parse(saved) }
        if (saved != null && System.currentTimeMillis() - prefs.getLong("at", 0L) < MAX_AGE) return@withContext
        val creds = RaCredentialsStore(context).load() ?: return@withContext
        val text = runCatching {
            val conn = URL("https://retroachievements.org/API/API_GetConsoleIDs.php?y=" + URLEncoder.encode(creds.apiKey, "UTF-8"))
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000; conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Voltcap")
            try {
                if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: return@withContext
        val parsed = runCatching { parse(text) }.getOrNull() ?: return@withContext
        if (parsed.isEmpty()) return@withContext
        icons.value = parsed
        prefs.edit().putString("json", text).putLong("at", System.currentTimeMillis()).apply()
    }

    private fun parse(json: String): Map<String, String> =
        JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
            val o = e.asJsonObject
            val name = o.get("Name")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            val icon = o.get("IconURL")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            name.lowercase() to icon
        }.toMap()
}
