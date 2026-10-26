package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * A photo of each console for the category screen: the main picture of the console's Wikipedia
 * article (mostly public-domain product photos on Wikimedia Commons), looked up once through
 * Wikipedia's public page-summary API and remembered. Your own picture (Y on a tile) always wins.
 */
object ConsolePhotos {
    /** Platform key → English Wikipedia article. */
    private val ARTICLES = mapOf(
        "NES" to "Nintendo Entertainment System", "SNES" to "Super Nintendo Entertainment System",
        "N64" to "Nintendo 64", "GameCube" to "GameCube", "Wii" to "Wii", "WiiU" to "Wii U",
        "Switch" to "Nintendo Switch", "GB" to "Game Boy", "GBC" to "Game Boy Color",
        "GBA" to "Game Boy Advance", "NDS" to "Nintendo DS", "3DS" to "Nintendo 3DS",
        "VirtualBoy" to "Virtual Boy", "PokemonMini" to "Pokémon Mini",
        "PS1" to "PlayStation (console)", "PS2" to "PlayStation 2", "PS3" to "PlayStation 3",
        "PS4" to "PlayStation 4", "PSP" to "PlayStation Portable", "PSVita" to "PlayStation Vita",
        "Genesis" to "Sega Genesis", "MegaDrive" to "Sega Genesis", "MasterSystem" to "Master System",
        "SegaCD" to "Sega CD", "32X" to "32X", "Saturn" to "Sega Saturn", "Dreamcast" to "Dreamcast",
        "GameGear" to "Game Gear", "Naomi" to "Sega NAOMI", "Atomiswave" to "Atomiswave",
        "PCEngine" to "TurboGrafx-16", "PCEngineCD" to "TurboGrafx-CD",
        "NeoGeo" to "Neo Geo (system)", "NeoGeoCD" to "Neo Geo CD", "NeoGeoPocket" to "Neo Geo Pocket Color",
        "WonderSwan" to "WonderSwan", "Atari2600" to "Atari 2600", "Atari7800" to "Atari 7800",
        "Lynx" to "Atari Lynx", "Jaguar" to "Atari Jaguar", "3DO" to "3DO Interactive Multiplayer",
        "Xbox" to "Xbox (console)", "Xbox360" to "Xbox 360", "MSX" to "MSX", "C64" to "Commodore 64",
        "Amiga" to "Amiga", "ColecoVision" to "ColecoVision", "Intellivision" to "Intellivision",
        "Vectrex" to "Vectrex", "Arcade" to "Arcade cabinet", "DOS" to "MS-DOS", "PICO8" to "PICO-8",
        "Steam" to "Steam Deck"
    )

    private const val PREFS = "console_photos"
    private const val NONE = "-"
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    }
    private val memory = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun hasPhoto(platform: String): Boolean = ConsoleArt.nameFor(platform) != null || ARTICLES.keys.any { it.equals(platform, true) }

    /**
     * The photo URL for [platform] (cached), or null when there is none / no network. Update 330: Voltcap's own picture of
     * the console (ConsoleArt, shipped with the app) first; the Wikipedia photo only for a console without one.
     */
    suspend fun url(context: Context, platform: String): String? {
        ConsoleArt.model(platform)?.let { return it }
        return wikiUrl(context, platform)
    }

    private suspend fun wikiUrl(context: Context, platform: String): String? = withContext(Dispatchers.IO) {
        val article = ARTICLES.entries.firstOrNull { it.key.equals(platform, true) }?.value ?: return@withContext null
        memory[platform]?.let { return@withContext it.takeIf { v -> v != NONE } }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(platform, null)?.let { memory[platform] = it; return@withContext it.takeIf { v -> v != NONE } }
        // null = network/server failure (try again next time); NONE = the article has no picture (remembered).
        val found = runCatching {
            val req = Request.Builder()
                .url("https://en.wikipedia.org/api/rest_v1/page/summary/" + Uri.encode(article.replace(' ', '_')))
                .header("User-Agent", "VoltCap launcher (console pictures)")
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null
                else {
                    val json = org.json.JSONObject(r.body?.string().orEmpty())
                    // The 640-px-ish thumbnail is plenty for a tile and much lighter than the original.
                    json.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("http") }
                        ?: json.optJSONObject("originalimage")?.optString("source")?.takeIf { it.startsWith("http") }
                        ?: NONE
                }
            }
        }.getOrNull()
        // Only a real answer is remembered; a network failure is tried again next time.
        if (found != null) {
            memory[platform] = found
            prefs.edit().putString(platform, found).apply()
        }
        found?.takeIf { it != NONE }
    }
}
