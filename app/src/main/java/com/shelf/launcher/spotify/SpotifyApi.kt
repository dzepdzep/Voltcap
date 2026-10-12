package com.shelf.launcher.spotify

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TRACK_FIELDS =
    "id,name,uri,duration_ms,track_number,is_playable,is_local,type,artists(id,name,uri),album(id,name,uri,images)"

/** Official Spotify Web API (https://api.spotify.com/v1/). Only endpoints the docs list. */
interface SpotifyApi {
    @GET("me") suspend fun me(): retrofit2.Response<SpUser>

    @GET("me/playlists")
    suspend fun myPlaylists(@Query("limit") limit: Int, @Query("offset") offset: Int): retrofit2.Response<SpPaging<SpPlaylist>>

    @GET("playlists/{id}/tracks")
    suspend fun playlistTracks(
        @Path("id") id: String, @Query("limit") limit: Int, @Query("offset") offset: Int,
        @Query("market") market: String = "from_token",
        @Query("fields") fields: String = "items(added_at,track($TRACK_FIELDS)),next,total,limit,offset"
    ): retrofit2.Response<SpPaging<SpPlaylistItem>>

    @GET("me/albums")
    suspend fun savedAlbums(@Query("limit") limit: Int, @Query("offset") offset: Int): retrofit2.Response<SpPaging<SpSavedAlbum>>

    @GET("me/tracks")
    suspend fun savedTracks(
        @Query("limit") limit: Int, @Query("offset") offset: Int, @Query("market") market: String = "from_token"
    ): retrofit2.Response<SpPaging<SpSavedTrack>>

    @GET("me/following")
    suspend fun followedArtists(
        @Query("limit") limit: Int, @Query("after") after: String?, @Query("type") type: String = "artist"
    ): retrofit2.Response<SpFollowedArtists>

    @GET("artists/{id}/albums")
    suspend fun artistAlbums(
        @Path("id") id: String, @Query("limit") limit: Int, @Query("offset") offset: Int,
        @Query("include_groups") groups: String = "album,single", @Query("market") market: String = "from_token"
    ): retrofit2.Response<SpPaging<SpAlbum>>

    @GET("albums/{id}/tracks")
    suspend fun albumTracks(
        @Path("id") id: String, @Query("limit") limit: Int, @Query("offset") offset: Int,
        @Query("market") market: String = "from_token"
    ): retrofit2.Response<SpPaging<SpTrack>>

    @GET("search")
    suspend fun search(
        @Query("q") q: String, @Query("type") type: String, @Query("limit") limit: Int,
        @Query("market") market: String = "from_token"
    ): retrofit2.Response<SpSearchResult>

    // ---- Player ----
    @GET("me/player") suspend fun playerState(@Query("additional_types") types: String = "track"): retrofit2.Response<SpPlaybackState>
    @GET("me/player/currently-playing")
    suspend fun currentlyPlaying(@Query("additional_types") types: String = "track"): retrofit2.Response<SpPlaybackState>
    @GET("me/player/devices") suspend fun devices(): retrofit2.Response<SpDevices>

    @PUT("me/player/play")
    suspend fun play(@Query("device_id") deviceId: String?, @Body body: SpPlayBody): retrofit2.Response<Unit>
    @PUT("me/player/pause") suspend fun pause(@Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @POST("me/player/next") suspend fun next(@Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @POST("me/player/previous") suspend fun previous(@Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @PUT("me/player/seek")
    suspend fun seek(@Query("position_ms") positionMs: Long, @Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @PUT("me/player/shuffle")
    suspend fun shuffle(@Query("state") on: Boolean, @Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @PUT("me/player/repeat")
    suspend fun repeat(@Query("state") state: String, @Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @PUT("me/player/volume")
    suspend fun volume(@Query("volume_percent") percent: Int, @Query("device_id") deviceId: String?): retrofit2.Response<Unit>
    @PUT("me/player") suspend fun transfer(@Body body: SpTransferBody): retrofit2.Response<Unit>

    companion object {
        fun create(auth: SpotifyAuth): SpotifyApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(auth)) // no logging interceptor on purpose: headers carry the token
                .build()
            return Retrofit.Builder().baseUrl("https://api.spotify.com/v1/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(SpotifyApi::class.java)
        }
    }
}

/**
 * Adds the bearer token, retries once after a 401 with a refreshed token
 * (single-flight inside [SpotifyAuth]) and once after a short 429 Retry-After.
 * Runs on OkHttp's own threads, so blocking here is fine.
 */
private class AuthInterceptor(private val auth: SpotifyAuth) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        fun signed(token: String?) = if (token == null) original else original.newBuilder().header("Authorization", "Bearer $token").build()
        val token = try { runBlocking { auth.accessToken() } } catch (e: IOException) { throw e } catch (e: Exception) { throw IOException("Spotify sign-in check failed") }
        var resp = chain.proceed(signed(token))
        var used = token
        if (resp.code == 401 && token != null) {
            val fresh = try { runBlocking { auth.refreshAfterRejection(token) } } catch (e: IOException) { throw e } catch (e: Exception) { null }
            if (fresh != null) { resp.close(); resp = chain.proceed(signed(fresh)); used = fresh }
        }
        if (resp.code == 429) {
            val wait = resp.header("Retry-After")?.trim()?.toLongOrNull() ?: 1L
            // Only wait out short limits; a long one is reported to the caller with its Retry-After header.
            if (wait <= MAX_WAIT_S) {
                resp.close()
                try { Thread.sleep(wait * 1000 + 100) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("Interrupted") }
                resp = chain.proceed(signed(used))
            }
        }
        return resp
    }

    private companion object { const val MAX_WAIT_S = 8L }
}
