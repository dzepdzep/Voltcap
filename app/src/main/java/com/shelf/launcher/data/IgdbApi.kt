package com.shelf.launcher.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * IGDB (igdb.com) — game metadata, aggregated critic scores, and official
 * "time to beat" figures.
 *
 * Chosen over scraping HowLongToBeat: HLTB has no public API, so pulling
 * from it means scraping their site, which is fragile and not something
 * they permit. IGDB exposes an official `game_time_to_beats` endpoint with
 * the same kind of data (hastily / normally / completely), so it's one
 * credentialed source instead of one API plus one scraper.
 *
 * IGDB is owned by Twitch, so auth is Twitch's client-credentials OAuth:
 * a Client ID + Client Secret exchange for a bearer token.
 */
interface IgdbApi {

    @POST
    suspend fun token(@Url url: String): IgdbTokenResponse

    // IGDB takes APICalypse query text as a raw POST body, not JSON or form
    // params — hence RequestBody rather than a typed request object.
    @POST("https://api.igdb.com/v4/games")
    suspend fun games(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") bearer: String,
        @Body query: RequestBody
    ): List<IgdbGame>

    @POST("https://api.igdb.com/v4/game_time_to_beats")
    suspend fun timeToBeat(
        @Header("Client-ID") clientId: String,
        @Header("Authorization") bearer: String,
        @Body query: RequestBody
    ): List<IgdbTimeToBeat>
}

data class IgdbTokenResponse(
    val access_token: String? = null,
    val expires_in: Long? = null
)

data class IgdbGame(
    val id: Int? = null,
    val name: String? = null,
    val summary: String? = null,
    val first_release_date: Long? = null, // unix seconds
    val aggregated_rating: Double? = null, // critic aggregate, 0-100
    val aggregated_rating_count: Int? = null,
    val rating: Double? = null, // IGDB user rating, 0-100
    val genres: List<IgdbNamed>? = null,
    val involved_companies: List<IgdbInvolvedCompany>? = null,
    val screenshots: List<IgdbImage>? = null,
    val artworks: List<IgdbImage>? = null,
    val cover: IgdbImage? = null,
    val platforms: List<Int>? = null
)

data class IgdbImage(val image_id: String? = null)

data class IgdbNamed(val id: Int? = null, val name: String? = null)

data class IgdbInvolvedCompany(
    val company: IgdbNamed? = null,
    val developer: Boolean? = null,
    val publisher: Boolean? = null
)

/** Times are returned in seconds. */
data class IgdbTimeToBeat(
    val game_id: Int? = null,
    val hastily: Long? = null,
    val normally: Long? = null,
    val completely: Long? = null
)

/** Flattened view for the UI. */
data class GameMetadata(
    val title: String?,
    val summary: String?,
    val releaseYear: Int?,
    val criticScore: Int?,
    val criticCount: Int?,
    val genres: List<String>,
    val developer: String?,
    val hastilyHours: Double?,
    val normallyHours: Double?,
    val completelyHours: Double?,
    /** Metacritic score (via RAWG), when known. */
    val metacritic: Int? = null,
    /** The publisher (ScreenScraper), when known. */
    val publisher: String? = null
)

fun createIgdbApi(): IgdbApi = Retrofit.Builder()
    // Every call uses an absolute @Url/@POST, so this base is only a
    // placeholder Retrofit requires at build time.
    .baseUrl("https://api.igdb.com/")
    // Update 182: an overall limit per request (the default client has none).
    .client(okhttp3.OkHttpClient.Builder().callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(IgdbApi::class.java)

class IgdbRepository(private val api: IgdbApi) {

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAtMillis: Long = 0
    /** Which Client ID / secret the cached token belongs to (new keys must never reuse the old token). */
    @Volatile private var tokenFor: String? = null
    private val cache = java.util.concurrent.ConcurrentHashMap<String, GameMetadata>()

    private val textPlain = "text/plain".toMediaType()

    private suspend fun bearerToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        val owner = "$clientId\u0000$clientSecret"
        cachedToken?.let { if (now < tokenExpiresAtMillis && tokenFor == owner) return it }

        return try {
            val enc = { v: String -> java.net.URLEncoder.encode(v, "UTF-8") }
            val url = "https://id.twitch.tv/oauth2/token" +
                "?client_id=${enc(clientId)}&client_secret=${enc(clientSecret)}&grant_type=client_credentials"
            val response = api.token(url)
            val token = response.access_token ?: return null
            cachedToken = token
            tokenFor = owner
            // Refresh a minute early rather than racing the expiry.
            tokenExpiresAtMillis = now + ((response.expires_in ?: 3600L) - 60L) * 1000L
            token
        } catch (e: Exception) {
            null
        }
    }

    suspend fun testConnection(clientId: String, clientSecret: String): Boolean =
        bearerToken(clientId, clientSecret) != null

    suspend fun fetchMetadata(
        clientId: String,
        clientSecret: String,
        gameName: String,
        platform: String? = null
    ): GameMetadata? {
        val cacheKey = "$gameName|$platform"
        cache[cacheKey]?.let { return it }
        val token = bearerToken(clientId, clientSecret) ?: return null
        val bearer = "Bearer $token"

        return try {
            val resolved = findGame(
                clientId, bearer, gameName, platform,
                "name, summary, first_release_date, aggregated_rating, aggregated_rating_count, rating, " +
                    "genres.name, involved_companies.company.name, involved_companies.developer"
            ) ?: return null

            val ttb = resolved.id?.let { id ->
                runCatching {
                    val ttbQuery =
                        "fields game_id, hastily, normally, completely; where game_id = $id;"
                            .toRequestBody(textPlain)
                    api.timeToBeat(clientId, bearer, ttbQuery).firstOrNull()
                }.getOrNull()
            }

            val metadata = GameMetadata(
                title = resolved.name,
                summary = resolved.summary,
                releaseYear = resolved.first_release_date?.let {
                    java.util.Calendar.getInstance().apply { timeInMillis = it * 1000L }
                        .get(java.util.Calendar.YEAR)
                },
                criticScore = resolved.aggregated_rating?.let { Math.round(it).toInt() },
                criticCount = resolved.aggregated_rating_count,
                genres = resolved.genres?.mapNotNull { it.name }.orEmpty(),
                developer = resolved.involved_companies
                    ?.firstOrNull { it.developer == true }?.company?.name,
                hastilyHours = ttb?.hastily?.toHours(),
                normallyHours = ttb?.normally?.toHours(),
                completelyHours = ttb?.completely?.toHours()
            )
            cache[cacheKey] = metadata
            metadata
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The IGDB entry for a game: searched on the console it's for (so "Doom"
     * on SNES isn't matched to the 2016 PC game), several results compared,
     * and an exact title match preferred over IGDB's first guess. Falls back
     * to searching every platform, then to the raw filename.
     */
    private suspend fun findGame(
        clientId: String,
        bearer: String,
        gameName: String,
        platform: String?,
        fields: String
    ): IgdbGame? {
        val cleaned = cleanTitleForSearch(gameName).replace("\"", "")
        val platformId = platform?.let { IgdbPlatforms.idFor(it) }
        suspend fun search(term: String, withPlatform: Boolean): List<IgdbGame> {
            val where = if (withPlatform && platformId != null) "where platforms = ($platformId);" else ""
            val query = "search \"$term\"; fields $fields, platforms; $where limit 10;".toRequestBody(textPlain)
            return runCatching { api.games(clientId, bearer, query) }
                .onFailure { e ->
                    // Token revoked / expired early: drop it so the next call asks Twitch for a new one.
                    if (e is retrofit2.HttpException && e.code() == 401) { cachedToken = null; tokenExpiresAtMillis = 0 }
                }
                .getOrDefault(emptyList())
        }
        // Apps (platform "App") need the exact title; games the title's words as whole words — never a
        // result that only starts the same way ("Moonlight" is not "Moonlighter").
        val strict = platform.equals("App", ignoreCase = true)
        fun pick(results: List<IgdbGame>): IgdbGame? =
            TitleMatch.best(cleaned, results.map { it.name.orEmpty() }, strict)?.let { results[it] }

        if (platformId != null) pick(search(cleaned, true))?.let { return it }
        pick(search(cleaned, false))?.let { return it }
        if (cleaned != gameName) return pick(search(gameName.replace("\"", ""), false))
        return null
    }

    private fun normalizeTitle(t: String): String =
        t.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]"), "")

    /** Screenshot addresses for a game (IGDB's medium screenshot size). */
    suspend fun fetchScreenshots(
        clientId: String,
        clientSecret: String,
        gameName: String,
        platform: String? = null
    ): List<String> {
        val token = bearerToken(clientId, clientSecret) ?: return emptyList()
        return try {
            val game = findGame(clientId, "Bearer $token", gameName, platform, "name, screenshots.image_id, artworks.image_id")
                ?: return emptyList()
            (game.screenshots.orEmpty() + game.artworks.orEmpty())
                .mapNotNull { it.image_id }
                .distinct()
                .take(8)
                .map { image(it, "t_screenshot_med") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Cover, background (official artwork, else a screenshot) for one game. */
    suspend fun fetchArt(clientId: String, clientSecret: String, gameName: String, platform: String?): GameArt? {
        // (Update 238, #111: a ROM hack by its own title, and only an entry with that title — RomHacks.)
        val term = RomHacks.artSearchName(gameName) ?: return null
        val hack = RomHacks.fanHack(gameName, null)
        val token = bearerToken(clientId, clientSecret) ?: return null
        return try {
            val game = findGame(
                clientId, "Bearer $token", term, platform,
                "name, cover.image_id, artworks.image_id, screenshots.image_id"
            )?.takeIf { g -> hack == null || RomHacks.namesTheHack(hack, listOfNotNull(g.name)) } ?: return null
            val cover = game.cover?.image_id?.let { image(it, "t_cover_big_2x") }
            val wide = (game.artworks.orEmpty() + game.screenshots.orEmpty()).firstNotNullOfOrNull { it.image_id }
            GameArt(
                gridUrl = cover,
                wideGridUrl = wide?.let { image(it, "t_screenshot_big") },
                heroUrl = wide?.let { image(it, "t_1080p") },
                logoUrl = null
            ).takeIf { it.gridUrl != null || it.heroUrl != null }
        } catch (e: Exception) {
            null
        }
    }

    /** Every cover / artwork / screenshot for the artwork picker. */
    suspend fun fetchArtCandidates(clientId: String, clientSecret: String, name: String, platform: String?): ArtCandidates? {
        val token = bearerToken(clientId, clientSecret) ?: return null
        return try {
            val game = findGame(
                clientId, "Bearer $token", name, platform,
                "name, cover.image_id, artworks.image_id, screenshots.image_id"
            ) ?: return null
            val wides = (game.artworks.orEmpty() + game.screenshots.orEmpty()).mapNotNull { it.image_id }.distinct()
            ArtCandidates(
                grids = listOfNotNull(game.cover?.image_id).map { image(it, "t_cover_big_2x") },
                wideGrids = wides.map { image(it, "t_screenshot_big") },
                heroes = wides.map { image(it, "t_1080p") },
                logos = emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun image(id: String, size: String) = "https://images.igdb.com/igdb/image/upload/$size/$id.jpg"

    private fun buildQuery(term: String): RequestBody = """
        search "$term";
        fields name, summary, first_release_date, aggregated_rating,
               aggregated_rating_count, rating, genres.name,
               involved_companies.company.name, involved_companies.developer;
        limit 1;
    """.trimIndent().toRequestBody(textPlain)

    /** Strips the bracketed tags ROM filenames carry — (USA), [!], (Rev 1),
     * (Disc 1) — plus underscores, which IGDB titles never contain. */
    private fun cleanTitleForSearch(raw: String): String {
        var t = raw.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        t = t.replace('_', ' ')
        t = t.replace(Regex("\\s+"), " ").trim()
        return t.ifBlank { raw }
    }

    private fun Long.toHours(): Double? =
        if (this <= 0) null else Math.round(this / 360.0) / 10.0
}

/** Shelf's console names → IGDB platform IDs, for searching on the right console. */
object IgdbPlatforms {
    private val ids = mapOf(
        "nes" to 18, "famicom" to 99, "fds" to 51, "snes" to 19, "sfc" to 58, "n64" to 4, "gb" to 33, "gbc" to 22,
        "gba" to 24, "nds" to 20, "ds" to 20, "3ds" to 37, "gamecube" to 21, "gc" to 21, "wii" to 5, "wiiu" to 41,
        "switch" to 130, "virtualboy" to 87, "ps1" to 7, "psx" to 7, "ps2" to 8, "ps3" to 9, "psp" to 38,
        "psvita" to 46, "vita" to 46, "genesis" to 29, "megadrive" to 29, "md" to 29, "mastersystem" to 64,
        "sms" to 64, "gamegear" to 35, "gg" to 35, "segacd" to 78, "megacd" to 78, "32x" to 30, "saturn" to 32,
        "dreamcast" to 23, "dc" to 23, "arcade" to 52, "mame" to 52, "fbneo" to 52, "neogeo" to 80,
        "pcengine" to 86, "tg16" to 86, "turbografx16" to 86, "atari2600" to 59, "atari7800" to 60,
        "lynx" to 61, "wonderswan" to 57, "ngp" to 119, "ngpc" to 120, "msx" to 27, "c64" to 15,
        "dos" to 13, "pc" to 6, "steam" to 6, "app" to 34, "android" to 34, "xbox" to 11
    )

    fun idFor(platform: String): Int? = ids[platform.lowercase().replace(Regex("[^a-z0-9]"), "")]
}
