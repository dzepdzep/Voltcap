package com.shelf.launcher.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SteamGridDbApi {

    @GET("search/autocomplete/{term}")
    suspend fun search(@Path("term") term: String): SgdbResponse<SgdbSearchResult>

    // Verified against SteamGridDB's v2 API docs: this endpoint returns a
    // single game object (not a list) keyed by Steam's own numeric AppID —
    // exact, no fuzzy matching, which is why fetchArt() prefers this for
    // STEAM-source games over the name-search path.
    @GET("games/steam/{steamAppId}")
    suspend fun gameBySteamId(@Path("steamAppId") steamAppId: Int): SgdbSingleResponse<SgdbSearchResult>

    /** [types]: "static", "animated" or both ("static,animated"); null = SteamGridDB's default. */
    @GET("grids/game/{id}")
    suspend fun grids(
        @Path("id") gameId: Int,
        @Query("dimensions") dimensions: String = "600x900",
        @Query("types") types: String? = null
    ): SgdbResponse<SgdbArt>

    @GET("heroes/game/{id}")
    suspend fun heroes(@Path("id") gameId: Int, @Query("types") types: String? = null): SgdbResponse<SgdbArt>

    /** Transparent title logos (~512x512 canvas) — shown in place of the
     * plain text title on the focused banner where available. */
    @GET("logos/game/{id}")
    suspend fun logos(@Path("id") gameId: Int): SgdbResponse<SgdbArt>
}

data class SgdbResponse<T>(
    val success: Boolean = true,
    val data: List<T> = emptyList()
)

/** Single-object counterpart to SgdbResponse — some SteamGridDB v2 endpoints
 * (like the Steam-AppID lookup) return `data` as one object, not an array. */
data class SgdbSingleResponse<T>(
    val success: Boolean = true,
    val data: T? = null
)

data class SgdbSearchResult(
    val id: Int,
    val name: String
)

data class SgdbArt(
    val id: Int,
    val url: String
)
