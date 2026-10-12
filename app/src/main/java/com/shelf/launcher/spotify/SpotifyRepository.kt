package com.shelf.launcher.spotify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException

enum class SpotifyErrorKind { AUTH, PREMIUM, NETWORK, RATE_LIMIT, NOT_FOUND, NO_ACTIVE_DEVICE, OTHER }

/** The id Voltcap gives Spotify's "Liked Songs" when listing it with the playlists. */
const val LIKED_ID = "__liked_songs__"

sealed class SpotifyResult<out T> {
    data class Ok<T>(val value: T) : SpotifyResult<T>()
    data class Error(val kind: SpotifyErrorKind, val message: String) : SpotifyResult<Nothing>()

    fun getOrNull(): T? = (this as? Ok)?.value
}

const val SPOTIFY_PREMIUM_MESSAGE =
    "Playback control needs Spotify Premium — you can still browse and open items in the Spotify app"

/**
 * The one entry point for the UI. Cheap to construct: all instances share the
 * same sign-in, HTTP client and now-playing state (kept in [Shared]).
 */
class SpotifyRepository(context: Context) {
    private val appContext = context.applicationContext
    private val auth = SpotifyAuth.get(appContext)
    private val api get() = Shared.api(auth)

    // ---- Connection ----
    val connection: StateFlow<SpotifyConnection> get() = auth.state
    /** Plain-words reason the last sign-in attempt failed, or null. */
    val authError: StateFlow<String?> get() = auth.lastError
    val redirectUri: String get() = SpotifyAuth.REDIRECT_URI

    suspend fun clientId(): String = auth.clientId()
    suspend fun setClientId(id: String) = auth.setClientId(id)
    /** Opens Spotify's consent page in a Custom Tab; the result arrives via [connection]. False if it couldn't start. */
    suspend fun connect(context: Context): Boolean = auth.beginLogin(context)
    fun cancelConnect() = auth.cancelLogin()
    suspend fun disconnect() { auth.disconnect(); Shared.nowPlaying.value = null } // the poll loop (if a UI is watching) idles while not connected and carries on after reconnecting

    // ---- Browse ----
    suspend fun me() = call { api.me() }
    suspend fun myPlaylists(offset: Int = 0, limit: Int = 50) = call { api.myPlaylists(limit.coerceIn(1, 50), offset) }
    suspend fun playlistTracks(playlistId: String, offset: Int = 0, limit: Int = 100) =
        call { api.playlistTracks(playlistId, limit.coerceIn(1, 100), offset) }
    suspend fun savedAlbums(offset: Int = 0, limit: Int = 50) = call { api.savedAlbums(limit.coerceIn(1, 50), offset) }
    suspend fun savedTracks(offset: Int = 0, limit: Int = 50) = call { api.savedTracks(limit.coerceIn(1, 50), offset) }

    /**
     * Spotify's own "Liked Songs" as a playlist entry (the Web API doesn't list it under playlists).
     * Its uri is the user's collection, which [playLiked] plays; the picture is the newest liked song's cover.
     * Null when there are no liked songs or the account can't be read.
     */
    suspend fun likedSongsPlaylist(): SpPlaylist? {
        val tracks = (savedTracks(0, 1) as? SpotifyResult.Ok)?.value ?: return null
        val total = tracks.total ?: 0
        if (total <= 0) return null
        val userId = (me() as? SpotifyResult.Ok)?.value?.id
        return SpPlaylist(
            id = LIKED_ID, name = "Liked Songs",
            uri = if (userId != null) "spotify:user:$userId:collection" else LIKED_ID,
            images = tracks.clean().firstOrNull()?.track?.album?.images,
            owner = SpOwner(display_name = "Spotify"), tracks = SpTracksRef(total)
        )
    }

    /** Your playlists with Liked Songs first (where the Spotify app has it too). */
    suspend fun myPlaylistsWithLiked(): SpotifyResult<List<SpPlaylist>> {
        val r = myPlaylists()
        if (r is SpotifyResult.Error) return r
        r as SpotifyResult.Ok
        val liked = runCatching { likedSongsPlaylist() }.getOrNull()
        return SpotifyResult.Ok(listOfNotNull(liked) + r.value.clean())
    }

    /**
     * Plays Liked Songs: the collection as a context (so shuffle / next work over all of them); where Spotify
     * refuses that, the newest 50 liked songs as a list.
     */
    suspend fun playLiked(collectionUri: String?, deviceId: String? = null): SpotifyResult<Unit> {
        if (collectionUri != null && collectionUri.startsWith("spotify:user:")) {
            val r = play(contextUri = collectionUri, deviceId = deviceId)
            if (r is SpotifyResult.Ok) return r
        }
        val uris = (savedTracks(0, 50) as? SpotifyResult.Ok)?.value?.clean()?.mapNotNull { it.track?.uri }.orEmpty()
        if (uris.isEmpty()) return SpotifyResult.Ok(Unit)
        return play(uris = uris, deviceId = deviceId)
    }

    /** Plays a playlist entry, Liked Songs included. */
    suspend fun playPlaylist(p: SpPlaylist, deviceId: String? = null): SpotifyResult<Unit>? {
        if (p.id == LIKED_ID) return playLiked(p.uri, deviceId)
        val uri = p.uri ?: return null
        return play(contextUri = uri, deviceId = deviceId)
    }
    /** Cursor paged: pass the previous page's [SpCursorPaging.nextCursor] as [after]. */
    suspend fun followedArtists(after: String? = null, limit: Int = 50): SpotifyResult<SpCursorPaging<SpArtist>> =
        when (val r = call { api.followedArtists(limit.coerceIn(1, 50), after) }) {
            is SpotifyResult.Ok -> SpotifyResult.Ok(r.value.artists ?: SpCursorPaging())
            is SpotifyResult.Error -> r
        }
    suspend fun artistAlbums(artistId: String, offset: Int = 0, limit: Int = 50) =
        call { api.artistAlbums(artistId, limit.coerceIn(1, 50), offset) }
    suspend fun albumTracks(albumId: String, offset: Int = 0, limit: Int = 50) =
        call { api.albumTracks(albumId, limit.coerceIn(1, 50), offset) }
    suspend fun search(query: String, types: List<String> = listOf("track", "album", "artist", "playlist"), limit: Int = 10) =
        call { api.search(query, types.joinToString(","), limit.coerceIn(1, 50)) }

    // ---- Player (control needs Premium; reads do not) ----
    /** Ok(null) when nothing is active (Spotify answers 204). */
    suspend fun playerState(): SpotifyResult<SpPlaybackState?> = callNullable { api.playerState() }
    suspend fun currentlyPlaying(): SpotifyResult<SpPlaybackState?> = callNullable { api.currentlyPlaying() }
    suspend fun devices(): SpotifyResult<List<SpDevice>> =
        when (val r = call { api.devices() }) {
            is SpotifyResult.Ok -> SpotifyResult.Ok(r.value.devices.orEmpty())
            is SpotifyResult.Error -> r
        }

    /** Plays a context (album / playlist / artist URI) from [offsetPosition] or [offsetUri], or a list of track [uris]. */
    suspend fun play(
        contextUri: String? = null, uris: List<String>? = null,
        offsetPosition: Int? = null, offsetUri: String? = null, positionMs: Long? = null, deviceId: String? = null
    ): SpotifyResult<Unit> {
        val offset = if (offsetPosition != null || offsetUri != null) SpOffset(offsetPosition, if (offsetPosition == null) offsetUri else null) else null
        // Voltcap's own background loop goes quiet the moment Spotify is asked to play.
        com.shelf.launcher.ui.BackgroundMusicGate.otherMusicStarting()
        return control { api.play(deviceId, SpPlayBody(contextUri, uris, offset, positionMs)) }
    }
    /** Resume whatever was playing. */
    suspend fun resume(deviceId: String? = null): SpotifyResult<Unit> {
        com.shelf.launcher.ui.BackgroundMusicGate.otherMusicStarting()
        return control { api.play(deviceId, SpPlayBody()) }
    }
    suspend fun pause(deviceId: String? = null) = control { api.pause(deviceId) }
    suspend fun next(deviceId: String? = null): SpotifyResult<Unit> {
        com.shelf.launcher.ui.BackgroundMusicGate.songChanging()
        return control { api.next(deviceId) }
    }
    suspend fun previous(deviceId: String? = null): SpotifyResult<Unit> {
        com.shelf.launcher.ui.BackgroundMusicGate.songChanging()
        return control { api.previous(deviceId) }
    }
    suspend fun seek(positionMs: Long, deviceId: String? = null) = control { api.seek(positionMs.coerceAtLeast(0), deviceId) }
    suspend fun setShuffle(on: Boolean, deviceId: String? = null) = control { api.shuffle(on, deviceId) }

    /**
     * Plays the song you picked first (from a playlist / album with [offsetUri], or a list of [uris] starting with it).
     * With shuffle on, Spotify ignores the starting song and begins anywhere, so shuffle is switched off first and
     * only switched back on once the picked song is really playing (update 90: switching it straight back raced the
     * start, and Liked Songs still began on a random song). If Spotify started somewhere else anyway, it's asked once
     * more. The rest stays shuffled, like in the Spotify app.
     */
    suspend fun playPicked(contextUri: String?, uris: List<String>?, offsetUri: String?, deviceId: String? = null): SpotifyResult<Unit> {
        // The last known player state is good enough to know the device and the shuffle flag: asking Spotify again
        // first cost a full round trip before anything started. Only when nothing is known yet is it fetched.
        // Only while it is fresh (a few seconds): else the shuffle flag or the device may be an old one (the shuffle setting
        // would be flipped, or a speaker that has gone away asked).
        val cached = Shared.nowPlaying.value?.takeIf { android.os.SystemClock.uptimeMillis() - Shared.nowPlayingAt < 10_000 }
        val state = cached ?: (playerState() as? SpotifyResult.Ok)?.value
        // A newer pick takes over: the check behind the older one must not start its song again.
        Shared.pickJob?.cancel()
        val dev = deviceId ?: state?.device?.id
        val shuffled = state?.shuffle_state == true
        // Shuffle off and play go out back to back (Spotify handles them in order); no more waiting between them.
        if (shuffled) setShuffle(false, dev)
        val want = offsetUri ?: uris?.firstOrNull()
        var r = play(contextUri = contextUri, uris = uris, offsetUri = offsetUri, deviceId = dev)
        // The remembered device is gone: Spotify's own current one will do.
        if (r is SpotifyResult.Error && r.kind == SpotifyErrorKind.NOT_FOUND && deviceId == null && dev != null) {
            r = play(contextUri = contextUri, uris = uris, offsetUri = offsetUri, deviceId = null)
        }
        if (r is SpotifyResult.Ok) {
            // Show the new song right away instead of waiting for the next poll.
            Shared.scope.launch { delay(250); refreshNowPlaying() }
            // The safety checks (right song really playing? shuffle back on) no longer hold the tap up: they run behind.
            if (want != null || shuffled) Shared.pickJob = Shared.scope.launch {
                if (want != null) {
                    suspend fun onPicked(): Boolean = (currentlyPlaying() as? SpotifyResult.Ok)?.value?.item.let { it != null && (it.uri == want || it.linked_from?.uri == want) }
                    delay(350)
                    if (!waitFor(2500) { onPicked() }) {
                        play(contextUri = contextUri, uris = uris, offsetUri = offsetUri, deviceId = dev)
                        waitFor(2500) { onPicked() }
                    }
                }
                // Shuffle goes back on even when a newer pick cancels this check (its own play already took over).
                if (shuffled) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { setShuffle(true, dev) }
                refreshNowPlaying()
            }
        } else if (shuffled) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { setShuffle(true, dev) }
        return r
    }

    /** Polls [check] about every 300 ms until it's true or [timeoutMs] passes. */
    private suspend fun waitFor(timeoutMs: Long, check: suspend () -> Boolean): Boolean {
        val until = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (android.os.SystemClock.uptimeMillis() < until) {
            if (runCatching { check() }.getOrDefault(false)) return true
            delay(300)
        }
        return false
    }

    /**
     * Nothing is playing anywhere (no active device): wakes the Spotify app on this handheld in the background, without
     * opening it, by sending it a media "play" key, and waits until it shows up as a Spotify device. Returns its id,
     * or null if it didn't come up (not installed / not signed in). Settings › Music › "Play without opening Spotify".
     */
    suspend fun wakeLocalSpotify(context: Context, timeoutMs: Long = 9000): String? {
        localDeviceId()?.let { return it }
        com.shelf.launcher.ui.BackgroundMusicGate.otherMusicStarting()
        for (action in intArrayOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
            runCatching {
                context.sendBroadcast(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setPackage("com.spotify.music")
                        .putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                )
            }
        }
        var id: String? = null
        waitFor(timeoutMs) { id = localDeviceId(); id != null }
        return id
    }

    /** This handheld among your Spotify devices (its name, else the only phone / tablet listed). */
    private suspend fun localDeviceId(): String? {
        val list = (devices() as? SpotifyResult.Ok)?.value?.filter { it.id != null && it.is_restricted != true } ?: return null
        val names = listOfNotNull(android.os.Build.MODEL, android.os.Build.DEVICE, android.os.Build.PRODUCT).filter { it.isNotBlank() }
        val handheld = list.filter { it.type.equals("Smartphone", true) || it.type.equals("Tablet", true) }
        return (handheld.firstOrNull { d -> names.any { n -> d.name?.contains(n, true) == true } }
            ?: handheld.singleOrNull())?.id
    }

    /**
     * Update 144: this handheld's Spotify device, only while it is the active one (else null). The per-app mixer
     * sets Spotify's volume only on it, never on a speaker or PC that happens to be playing.
     */
    internal suspend fun localActiveDeviceId(): String? {
        val id = localDeviceId() ?: return null
        val active = (devices() as? SpotifyResult.Ok)?.value?.firstOrNull { it.id == id }?.is_active == true
        return if (active) id else null
    }

    /** [state] is "off", "context" or "track". */
    suspend fun setRepeat(state: String, deviceId: String? = null) = control { api.repeat(state, deviceId) }
    suspend fun setVolume(percent: Int, deviceId: String? = null) = control { api.volume(percent.coerceIn(0, 100), deviceId) }
    suspend fun transferPlayback(deviceId: String, play: Boolean = true): SpotifyResult<Unit> {
        if (play) com.shelf.launcher.ui.BackgroundMusicGate.otherMusicStarting()
        return control { api.transfer(SpTransferBody(listOf(deviceId), play)) }
    }

    /** Fallback for free accounts: opens the item in the Spotify app (or its web page). False if nothing could open it. */
    fun openInSpotifyApp(context: Context, uri: String): Boolean {
        fun start(i: Intent): Boolean = try {
            if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i); true
        } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
        val spotifyUri = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage("com.spotify.music")
        if (start(spotifyUri)) return true
        val parts = uri.split(":")
        val web = if (parts.size >= 3 && parts[0] == "spotify") "https://open.spotify.com/${parts[1]}/${parts[2]}" else return false
        return start(Intent(Intent.ACTION_VIEW, Uri.parse(web)))
    }

    // ---- Now playing poller ----
    /** Latest playback state; only kept fresh while a visible UI has called [startNowPlaying]. */
    val nowPlaying: StateFlow<SpPlaybackState?> get() = Shared.nowPlaying

    /** Call when a UI showing Spotify playback becomes visible (pair with [stopNowPlaying] when hidden). Reference-counted. */
    fun startNowPlaying() {
        synchronized(Shared) {
            Shared.watchers++
            if (Shared.pollJob?.isActive != true) Shared.pollJob = Shared.scope.launch { pollLoop() }
        }
    }

    fun stopNowPlaying() {
        synchronized(Shared) {
            if (Shared.watchers > 0) Shared.watchers--
            if (Shared.watchers == 0) { Shared.pollJob?.cancel(); Shared.pollJob = null }
        }
    }

    private fun stopAllPolling() = synchronized(Shared) { Shared.watchers = 0; Shared.pollJob?.cancel(); Shared.pollJob = null }


    /** One immediate refresh (e.g. right after a control tap). */
    suspend fun refreshNowPlaying() {
        val r = playerState()
        if (r is SpotifyResult.Ok) { Shared.nowPlaying.value = r.value; Shared.nowPlayingAt = android.os.SystemClock.uptimeMillis() }
    }

    private suspend fun pollLoop() {
        // This coroutine's own job: stopNowPlaying() cancels it (a fresh scope here was always "active").
        while (kotlinx.coroutines.currentCoroutineContext().isActive && Shared.watchers > 0) {
            var wait: Long
            if (connection.value != SpotifyConnection.CONNECTED) {
                wait = 5_000
            } else when (val r = playerState()) {
                is SpotifyResult.Ok -> { Shared.nowPlaying.value = r.value; Shared.nowPlayingAt = android.os.SystemClock.uptimeMillis(); wait = if (r.value?.playing() == true) 5_000 else 20_000 }
                is SpotifyResult.Error -> wait = if (r.kind == SpotifyErrorKind.RATE_LIMIT) 30_000 else 20_000
            }
            delay(wait)
        }
    }

    // ---- Plumbing ----
    private fun notReady(): SpotifyResult.Error? = when (connection.value) {
        SpotifyConnection.CONNECTED -> null
        SpotifyConnection.NO_CLIENT_ID -> SpotifyResult.Error(SpotifyErrorKind.AUTH, "Add your Spotify Client ID in Settings › Accounts first.")
        SpotifyConnection.NEEDS_RECONNECT -> SpotifyResult.Error(SpotifyErrorKind.AUTH, "Spotify needs to be reconnected in Settings › Accounts.")
        else -> SpotifyResult.Error(SpotifyErrorKind.AUTH, "Connect Spotify in Settings › Accounts.")
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): SpotifyResult<T> =
        when (val r = callNullable(block)) {
            is SpotifyResult.Ok -> r.value?.let { SpotifyResult.Ok(it) } ?: SpotifyResult.Error(SpotifyErrorKind.OTHER, "Spotify sent an empty answer.")
            is SpotifyResult.Error -> r
        }

    private suspend fun <T> callNullable(block: suspend () -> Response<T>): SpotifyResult<T?> {
        notReady()?.let { return it }
        return try {
            val r = block()
            if (r.isSuccessful) SpotifyResult.Ok(r.body()) else mapError(r)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // being cancelled is not a Spotify answer
        } catch (e: IOException) {
            SpotifyResult.Error(SpotifyErrorKind.NETWORK, "Couldn't reach Spotify. Check the connection.")
        } catch (e: Exception) {
            SpotifyResult.Error(SpotifyErrorKind.OTHER, "Unexpected Spotify answer.")
        }
    }

    private suspend fun control(block: suspend () -> Response<Unit>): SpotifyResult<Unit> {
        val r = call2(block)
        // Let the poller (if any UI is watching) pick up the new state soon after.
        if (r is SpotifyResult.Ok && Shared.watchers > 0) Shared.scope.launch { delay(250); refreshNowPlaying(); delay(900); refreshNowPlaying() }
        return r
    }

    private suspend fun call2(block: suspend () -> Response<Unit>): SpotifyResult<Unit> =
        when (val r = callNullable(block)) { is SpotifyResult.Ok -> SpotifyResult.Ok(Unit); is SpotifyResult.Error -> r }

    private fun <T> mapError(r: Response<T>): SpotifyResult.Error {
        val obj = runCatching { Gson().fromJson(r.errorBody()?.string().orEmpty(), JsonObject::class.java) }.getOrNull()
        val err = obj?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        val reason = err?.get("reason")?.takeIf { it.isJsonPrimitive }?.asString
        val msg = err?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
        return when {
            reason == "PREMIUM_REQUIRED" -> SpotifyResult.Error(SpotifyErrorKind.PREMIUM, SPOTIFY_PREMIUM_MESSAGE)
            reason == "NO_ACTIVE_DEVICE" -> SpotifyResult.Error(SpotifyErrorKind.NO_ACTIVE_DEVICE, "No active Spotify device. Open Spotify on a phone or speaker, start something, then try again.")
            r.code() == 401 -> SpotifyResult.Error(SpotifyErrorKind.AUTH, "Spotify sign-in expired. Reconnect in Settings › Accounts.")
            r.code() == 403 && msg?.contains("scope", true) == true ->
                SpotifyResult.Error(SpotifyErrorKind.AUTH, "Spotify needs extra permission. Disconnect and connect again in Settings › Accounts.")
            r.code() == 403 -> SpotifyResult.Error(SpotifyErrorKind.OTHER, msg ?: "Spotify refused this request. Development-mode apps only work for users added in the Spotify dashboard.")
            r.code() == 404 -> SpotifyResult.Error(SpotifyErrorKind.NOT_FOUND, msg ?: "Not found on Spotify.")
            r.code() == 429 -> SpotifyResult.Error(SpotifyErrorKind.RATE_LIMIT,
                "Spotify is busy — try again in ${r.headers()["Retry-After"] ?: "a few"} seconds.")
            else -> SpotifyResult.Error(SpotifyErrorKind.OTHER, msg ?: "Spotify error (${r.code()}).")
        }
    }

    /** Process-wide state shared by every repository instance. */
    private object Shared {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val nowPlaying = MutableStateFlow<SpPlaybackState?>(null)
        /** When [nowPlaying] was last looked up (uptime ms): it is only trusted while fresh. */
        @Volatile var nowPlayingAt = 0L
        /** The check behind the last picked song: a newer pick replaces it. */
        @Volatile var pickJob: Job? = null
        @Volatile var watchers = 0
        var pollJob: Job? = null
        @Volatile private var apiInstance: SpotifyApi? = null
        fun api(auth: SpotifyAuth): SpotifyApi = apiInstance ?: synchronized(this) {
            apiInstance ?: SpotifyApi.create(auth).also { apiInstance = it }
        }
    }
}
