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
 *   boxart (back) -> the case's back where no back scan was found (Update 302, TgdbBacks)
 *
 * Its retro coverage is often better than SteamGridDB's, which is
 * Steam-centric — so it's a genuine alternative rather than a duplicate.
 */
interface TheGamesDbApi {

    @GET("v1/Games/ByGameName")
    suspend fun searchByName(
        @Query("apikey") apiKey: String,
        @Query("name") name: String,
        @Query("fields") fields: String = "game_title",
        /** Update 329: TheGamesDB's platform id(s) to search on (null = every platform). */
        @Query("filter[platform]") platform: String? = null
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
    // Update 182: an overall limit per request (the default client has none).
    .client(okhttp3.OkHttpClient.Builder().callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(TheGamesDbApi::class.java)

class TheGamesDbRepository(private val api: TheGamesDbApi) {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, GameArt>() // shared by parallel scrapes
    /** Update 302: the back cover found with the art ("" = none), by game. */
    private val backs = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Update 302: the game's back cover (boxart, side "back"), or null. Same look-up as [fetchArt] (one search and one
     * images call, shared with it); the back is only used where no back scan was found (TgdbBacks).
     */
    suspend fun fetchBack(apiKey: String, game: Game): String? {
        backs[game.stableKey]?.let { return it.ifEmpty { null } }
        cache.remove(game.stableKey) // (looked up again so the back comes with it)
        lastFailure = null
        if (fetchArt(apiKey, game) == null) lastFailure?.let { throw it } // (a failed look-up isn't "no back")
        return backs[game.stableKey]?.ifEmpty { null }
    }

    /** Update 302: why the last look-up failed (network, HTTP 429 — the key's allowance used up…), else null. */
    @Volatile private var lastFailure: Exception? = null

    suspend fun testConnection(apiKey: String): Boolean = try {
        api.searchByName(apiKey, "mario").data?.games != null
    } catch (e: Exception) {
        false
    }

    suspend fun fetchArt(apiKey: String, game: Game): GameArt? {
        cache[game.stableKey]?.let { return it }
        return try {
            // (Update 238, #111: a ROM hack is searched by its own title, exact titles only — never the base game's
            // entry; one known only by a "(Hack)" tag isn't searched by name at all. RomHacks.)
            val hack = RomHacks.fanHack(game)
            if (hack != null && !hack.distinctive) return null
            val searchName = hack?.ownTitle ?: game.name
            val cleaned = cleanTitle(searchName)
            val strict = hack != null || TitleMatch.strictFor(game)
            fun pick(list: List<TgdbGame>?): TgdbGame? =
                list?.let { l -> TitleMatch.best(cleaned, l.map { it.game_title.orEmpty() }, strict)?.let { l[it] } }
            // (Update 329, seen on the AYN Thor: the PS2 "7 Wonders of the Ancient World" got the DS game's entry — its
            // back cover with Nintendo's logo on the PS2 case — because the search took every platform. Searched on the
            // game's own console first now, like IGDB / RAWG; every platform only when that finds nothing.)
            val platformId = TgdbPlatforms.idFor(game.platform)?.toString()
            val match = (if (platformId != null) pick(api.searchByName(apiKey, cleaned, platform = platformId).data?.games) else null)
                ?: pick(api.searchByName(apiKey, cleaned).data?.games)
                ?: pick(api.searchByName(apiKey, searchName).data?.games)
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
            backs[game.stableKey] = url(images.firstOrNull { it.type == "boxart" && it.side == "back" }).orEmpty()
            cache[game.stableKey] = art
            art
        } catch (e: Exception) {
            lastFailure = e
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

/**
 * Update 329: Voltcap's console names → TheGamesDB platform ids (its Platforms list), for searching on the right
 * console. A console not listed is searched on every platform, as before.
 */
object TgdbPlatforms {
    private val ids = mapOf(
        "pc" to 1, "steam" to 1, "dos" to 1, "gamecube" to 2, "gc" to 2, "ngc" to 2, "n64" to 3, "gb" to 4, "gba" to 5,
        "snes" to 6, "sfc" to 6, "nes" to 7, "famicom" to 7, "nds" to 8, "ds" to 8, "wii" to 9, "ps1" to 10, "psx" to 10,
        "ps2" to 11, "ps3" to 12, "psp" to 13, "xbox" to 14, "xbox360" to 15, "dreamcast" to 16, "dc" to 16,
        "saturn" to 17, "genesis" to 18, "megadrive" to 18, "md" to 18, "gamegear" to 20, "gg" to 20,
        "segacd" to 21, "megacd" to 21, "atari2600" to 22, "arcade" to 23, "mame" to 23, "fbneo" to 23,
        "neogeo" to 24, "3do" to 25, "atari5200" to 26, "atari7800" to 27, "jaguar" to 28, "32x" to 33,
        "pcengine" to 34, "tg16" to 34, "turbografx16" to 34, "mastersystem" to 35, "sms" to 35, "wiiu" to 38,
        "psvita" to 39, "vita" to 39, "c64" to 40, "gbc" to 41
    )

    fun idFor(platform: String?): Int? = platform?.let { ids[it.lowercase().replace(Regex("[^a-z0-9]"), "")] }
}
