package com.shelf.launcher.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TheGamesDB (thegamesdb.net) as a second art source.
 *
 * Chosen over the alternatives because it has a documented public API and its
 * image types map onto the four slots this launcher uses:
 *   boxart (front) -> cover, fanart -> hero, banner -> wide, clearlogo -> logo
 *
 * Its retro coverage is often better than SteamGridDB's, which is
 * Steam-centric — so it's a genuine alternative rather than a duplicate.
 */
interface TheGamesDbApi {

    @GET("v1/Games/ByGameName")
    suspend fun searchByName(
        @Query("apikey") apiKey: String,
        @Query("name") name: String,
        @Query("fields") fields: String = "game_title"
    ): TgdbSearchResponse

    @GET("v1/Games/Images")
    suspend fun images(
        @Query("apikey") apiKey: String,
        @Query("games_id") gameId: Int
    ): TgdbImagesResponse
}

data class TgdbSearchResponse(val data: TgdbSearchData? = null)
data class TgdbSearchData(val games: List<TgdbGame>? = null)
data class TgdbGame(val id: Int? = null, val game_title: String? = null)

data class TgdbImagesResponse(val data: TgdbImagesData? = null)
data class TgdbImagesData(
    val base_url: TgdbBaseUrls? = null,
    /** Keyed by game id as a string. */
    val images: Map<String, List<TgdbImage>>? = null
)

data class TgdbBaseUrls(val original: String? = null, val large: String? = null)

data class TgdbImage(
    val type: String? = null,   // boxart | fanart | banner | clearlogo | screenshot
    val side: String? = null,   // front | back, for boxart
    val filename: String? = null
)

fun createTheGamesDbApi(): TheGamesDbApi = Retrofit.Builder()
    .baseUrl("https://api.thegamesdb.net/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(TheGamesDbApi::class.java)

class TheGamesDbRepository(private val api: TheGamesDbApi) {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, GameArt>() // shared by parallel scrapes

    suspend fun testConnection(apiKey: String): Boolean = try {
        api.searchByName(apiKey, "mario").data?.games != null
    } catch (e: Exception) {
        false
    }

    suspend fun fetchArt(apiKey: String, game: Game): GameArt? {
        cache[game.stableKey]?.let { return it }
        return try {
            val cleaned = cleanTitle(game.name)
            val strict = TitleMatch.strictFor(game)
            fun pick(list: List<TgdbGame>?): TgdbGame? =
                list?.let { l -> TitleMatch.best(cleaned, l.map { it.game_title.orEmpty() }, strict)?.let { l[it] } }
            val match = pick(api.searchByName(apiKey, cleaned).data?.games)
                ?: pick(api.searchByName(apiKey, game.name).data?.games)
                ?: return null
            val id = match.id ?: return null

            val response = api.images(apiKey, id)
            val base = response.data?.base_url?.original
                ?: response.data?.base_url?.large
                ?: return null
            val images = response.data?.images?.get(id.toString()).orEmpty()

            fun url(image: TgdbImage?): String? =
                image?.filename?.let { base.trimEnd('/') + "/" + it.trimStart('/') }

            val art = GameArt(
                gridUrl = url(
                    images.firstOrNull { it.type == "boxart" && it.side == "front" }
                        ?: images.firstOrNull { it.type == "boxart" }
                ),
                wideGridUrl = url(images.firstOrNull { it.type == "banner" }),
                heroUrl = url(images.firstOrNull { it.type == "fanart" }),
                logoUrl = url(images.firstOrNull { it.type == "clearlogo" })
            )
            cache[game.stableKey] = art
            art
        } catch (e: Exception) {
            null
        }
    }

    /** Same reasoning as the IGDB search: ROM filenames carry region and
     * revision tags that database titles don't. */
    private fun cleanTitle(raw: String): String {
        var t = raw.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        t = t.replace('_', ' ')
        t = t.replace(Regex("\\s+"), " ").trim()
        return t.ifBlank { raw }
    }
}
