package com.shelf.launcher.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * RAWG (rawg.io) as an alternative metadata source to IGDB.
 *
 * Worth preferring in most cases: a single API key instead of IGDB's Twitch
 * OAuth client-id/secret exchange, and one lookup returns the Metacritic
 * score, average playtime, description, genres and developers together.
 *
 * The tradeoff versus IGDB: `playtime` is a single average in hours rather
 * than IGDB's three-way rushed/normally/completionist split, so the details
 * panel shows one figure when this source is used.
 */
interface RawgApi {

    @GET("api/games")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("search") query: String,
        @Query("page_size") pageSize: Int = 5,
        @Query("platforms") platforms: String? = null,
        @Query("search_precise") precise: Boolean = true
    ): RawgSearchResponse

    @GET("api/games/{id}")
    suspend fun details(
        @Path("id") id: Int,
        @Query("key") apiKey: String
    ): RawgGameDetail

    @GET("api/games/{id}/screenshots")
    suspend fun screenshots(
        @Path("id") id: Int,
        @Query("key") apiKey: String
    ): RawgScreenshots
}

data class RawgImage(val id: Int? = null, val image: String? = null)

data class RawgScreenshots(val results: List<RawgImage>? = null)

data class RawgSearchResponse(val results: List<RawgGame>? = null)

data class RawgGame(
    val id: Int? = null,
    val name: String? = null,
    val released: String? = null,     // yyyy-MM-dd
    val metacritic: Int? = null,
    val playtime: Int? = null,        // average hours
    val genres: List<RawgNamed>? = null,
    val background_image: String? = null,
    val short_screenshots: List<RawgImage>? = null
)

data class RawgGameDetail(
    val id: Int? = null,
    val name: String? = null,
    val released: String? = null,
    val metacritic: Int? = null,
    val playtime: Int? = null,
    val description_raw: String? = null,
    val genres: List<RawgNamed>? = null,
    val developers: List<RawgNamed>? = null,
    val background_image: String? = null,
    val background_image_additional: String? = null
)

data class RawgNamed(val id: Int? = null, val name: String? = null)

fun createRawgApi(): RawgApi = Retrofit.Builder()
    .baseUrl("https://api.rawg.io/")
    // Update 182: an overall limit per request (the default client has none).
    .client(okhttp3.OkHttpClient.Builder().callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(RawgApi::class.java)

class RawgRepository(private val api: RawgApi) {

    // Scraping runs several games at once: both caches are shared between coroutines.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, GameMetadata>()

    suspend fun testConnection(apiKey: String): Boolean = try {
        api.search(apiKey, "mario").results != null
    } catch (e: Exception) {
        false
    }

    private val matches: MutableMap<String, RawgGame?> = java.util.Collections.synchronizedMap(HashMap())

    /** RAWG's entry for a game: searched on the right console first,
     * preferring an exact title match. */
    private suspend fun find(apiKey: String, gameName: String, platform: String?): RawgGame? {
        val cacheKey = "$gameName|$platform"
        if (matches.containsKey(cacheKey)) return matches[cacheKey]
        // Same filename cleanup as the other sources: ROM names carry region
        // and revision tags that databases don't have.
        val cleaned = cleanTitle(gameName)
        val platformId = platform?.let { RawgPlatforms.idFor(it) }?.toString()
        // Same rules as the other sources (TitleMatch): exact for apps, whole words for games.
        val strict = platform.equals("App", ignoreCase = true)
        fun pick(results: List<RawgGame>?): RawgGame? = results?.let { list ->
            TitleMatch.best(cleaned, list.map { it.name.orEmpty() }, strict)?.let { list[it] }
        }
        val match = (if (platformId != null) pick(api.search(apiKey, cleaned, platforms = platformId).results) else null)
            ?: pick(api.search(apiKey, cleaned).results)
            ?: pick(api.search(apiKey, gameName).results)
        matches[cacheKey] = match
        return match
    }

    suspend fun fetchMetadata(apiKey: String, gameName: String, platform: String? = null): GameMetadata? {
        val cacheKey = "$gameName|$platform"
        cache[cacheKey]?.let { return it }
        return try {
            val match = find(apiKey, gameName, platform) ?: return null
            val id = match.id ?: return null

            val detail = runCatching { api.details(id, apiKey) }.getOrNull()

            val metadata = GameMetadata(
                title = detail?.name ?: match.name,
                summary = detail?.description_raw,
                releaseYear = (detail?.released ?: match.released)
                    ?.take(4)?.toIntOrNull(),
                criticScore = detail?.metacritic ?: match.metacritic,
                metacritic = detail?.metacritic ?: match.metacritic,
                criticCount = null, // RAWG exposes the Metacritic score, not its review count
                genres = (detail?.genres ?: match.genres)?.mapNotNull { it.name }.orEmpty(),
                developer = detail?.developers?.firstOrNull()?.name,
                hastilyHours = null,
                // RAWG gives one average rather than a three-way split.
                normallyHours = (detail?.playtime ?: match.playtime)
                    ?.takeIf { it > 0 }?.toDouble(),
                completelyHours = null
            )
            cache[cacheKey] = metadata
            metadata
        } catch (e: Exception) {
            null
        }
    }

    /** Background art from RAWG. It has no box covers — only wide images —
     * so this fills the wide capsule and background. */
    suspend fun fetchArt(apiKey: String, gameName: String, platform: String?): GameArt? = try {
        // (Update 238, #111: a ROM hack by its own title, and only an entry with that title — RomHacks.)
        val hack = RomHacks.fanHack(gameName, null)
        val match = RomHacks.artSearchName(gameName)?.let { term -> find(apiKey, term, platform) }
            ?.takeIf { m -> hack == null || RomHacks.namesTheHack(hack, listOfNotNull(m.name)) }
        val detail = match?.id?.let { runCatching { api.details(it, apiKey) }.getOrNull() }
        val bg = detail?.background_image ?: match?.background_image
        val extra = detail?.background_image_additional ?: match?.short_screenshots?.drop(1)?.firstOrNull()?.image
        if (bg == null) null else GameArt(gridUrl = null, wideGridUrl = bg, heroUrl = extra ?: bg, logoUrl = null)
    } catch (e: Exception) {
        null
    }

    /** Screenshot addresses for the details window. */
    suspend fun fetchScreenshots(apiKey: String, gameName: String, platform: String?): List<String> = try {
        val match = find(apiKey, gameName, platform)
        val fromSearch = match?.short_screenshots?.mapNotNull { it.image }.orEmpty()
        val list = fromSearch.ifEmpty {
            match?.id?.let { runCatching { api.screenshots(it, apiKey).results }.getOrNull() }
                ?.mapNotNull { it.image }.orEmpty()
        }
        list.distinct().take(8).map { resized(it, 640) }
    } catch (e: Exception) {
        emptyList()
    }

    /** Backgrounds and screenshots for the artwork picker. */
    suspend fun fetchArtCandidates(apiKey: String, gameName: String, platform: String?): ArtCandidates? = try {
        val match = find(apiKey, gameName, platform)
        if (match == null) null else {
            val detail = match.id?.let { runCatching { api.details(it, apiKey) }.getOrNull() }
            val images = (listOfNotNull(detail?.background_image ?: match.background_image, detail?.background_image_additional) +
                match.short_screenshots?.mapNotNull { it.image }.orEmpty()).distinct()
            ArtCandidates(
                grids = emptyList(),
                wideGrids = images.map { resized(it, 640) },
                heroes = images,
                logos = emptyList()
            )
        }
    } catch (e: Exception) {
        null
    }

    /** RAWG's image CDN can serve smaller copies: /media/ → /media/resize/640/-/. */
    private fun resized(url: String, width: Int): String =
        if ("/media/resize/" in url) url else url.replace("/media/", "/media/resize/$width/-/")

    private fun normalize(t: String) = t.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")

    private fun cleanTitle(raw: String): String {
        var t = raw.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        t = t.replace('_', ' ')
        t = t.replace(Regex("\\s+"), " ").trim()
        return t.ifBlank { raw }
    }
}

/** Shelf's console names → RAWG platform IDs. */
object RawgPlatforms {
    private val ids = mapOf(
        "nes" to 49, "famicom" to 49, "snes" to 79, "sfc" to 79, "n64" to 83, "gb" to 26, "gbc" to 43, "gba" to 24,
        "nds" to 9, "ds" to 9, "3ds" to 8, "gamecube" to 105, "gc" to 105, "wii" to 11, "wiiu" to 10, "switch" to 7,
        "ps1" to 27, "psx" to 27, "ps2" to 15, "ps3" to 16, "psp" to 17, "psvita" to 19, "vita" to 19,
        "genesis" to 167, "megadrive" to 167, "md" to 167, "mastersystem" to 74, "sms" to 74, "gamegear" to 77,
        "gg" to 77, "segacd" to 119, "megacd" to 119, "32x" to 117, "saturn" to 107, "dreamcast" to 106, "dc" to 106,
        "neogeo" to 12, "atari2600" to 23, "atari7800" to 28, "lynx" to 46, "pc" to 4, "steam" to 4,
        "app" to 21, "android" to 21
    )

    fun idFor(platform: String): Int? = ids[platform.lowercase().replace(Regex("[^a-z0-9]"), "")]
}

/** The RAWG API key (only used for Metacritic scores and average playtime). */
class RawgKeyStore(context: android.content.Context) {
    private val app = context.applicationContext
    // Encrypted at rest; the old plain "rawg" file is migrated in once by SecureStore.
    private val prefs = SecureStore.open(app, "rawg_encrypted", "RAWG", listOf("rawg")).prefs
    fun load(): String? = prefs.getString("key", null)?.takeIf { it.isNotBlank() }
    fun save(key: String) {
        prefs.edit().putString("key", key).apply()
        SecureStore.clearRelogin(app, "RAWG")
    }
    fun clear() = prefs.edit().remove("key").apply()
}
