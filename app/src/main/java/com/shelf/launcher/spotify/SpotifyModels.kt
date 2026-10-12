package com.shelf.launcher.spotify

/*
 * Minimal Gson models for the parts of the Spotify Web API Voltcap reads.
 * Everything is nullable with a default: Gson fills fields straight from the
 * JSON and skips constructors, so a missing/null field must never crash.
 * Property names match the API's snake_case so no annotations are needed.
 */

data class SpImage(val url: String? = null, val width: Int? = null, val height: Int? = null)

/** Picks the image closest to [targetPx] on its long side, preferring one at least that big. */
fun List<SpImage>?.best(targetPx: Int = 300): String? {
    val list = this?.filter { !it.url.isNullOrBlank() } ?: return null
    if (list.isEmpty()) return null
    // Images without a size (playlist mosaics) are treated as "large".
    fun side(i: SpImage) = maxOf(i.width ?: 0, i.height ?: 0).let { if (it == 0) 10_000 else it }
    val big = list.filter { side(it) >= targetPx }.minByOrNull { side(it) }
    return (big ?: list.maxByOrNull { side(it) })?.url
}

data class SpArtistRef(val id: String? = null, val name: String? = null, val uri: String? = null)

data class SpArtist(
    val id: String? = null, val name: String? = null, val uri: String? = null,
    val images: List<SpImage>? = null, val genres: List<String>? = null
)

data class SpAlbum(
    val id: String? = null, val name: String? = null, val uri: String? = null,
    val images: List<SpImage>? = null, val artists: List<SpArtistRef>? = null,
    val release_date: String? = null, val total_tracks: Int? = null, val album_type: String? = null
) {
    fun artistLine() = artists?.mapNotNull { it.name }?.joinToString(", ").orEmpty()
}

data class SpTrack(
    val id: String? = null, val name: String? = null, val uri: String? = null,
    val duration_ms: Long? = null, val track_number: Int? = null, val is_playable: Boolean? = null,
    val is_local: Boolean? = null, val explicit: Boolean? = null, val type: String? = null,
    val artists: List<SpArtistRef>? = null, val album: SpAlbum? = null,
    /** Set when Spotify swapped in another copy of the song for your country (track relinking): the one you picked. */
    val linked_from: SpLinked? = null
) {
    fun artistLine() = artists?.mapNotNull { it.name }?.joinToString(", ").orEmpty()
    /** Region-restricted tracks report is_playable=false; unknown (null) counts as playable. */
    fun playable() = is_playable != false
}

data class SpOwner(val id: String? = null, val display_name: String? = null)
data class SpTracksRef(val total: Int? = null)

data class SpLinked(val id: String? = null, val uri: String? = null)

data class SpPlaylist(
    val id: String? = null, val name: String? = null, val uri: String? = null,
    val images: List<SpImage>? = null, val owner: SpOwner? = null,
    val tracks: SpTracksRef? = null, val description: String? = null
)

data class SpUser(
    val id: String? = null, val display_name: String? = null, val product: String? = null,
    val country: String? = null, val images: List<SpImage>? = null
) {
    fun isPremium() = product == "premium"
}

data class SpDevice(
    val id: String? = null, val name: String? = null, val type: String? = null,
    val is_active: Boolean? = null, val is_restricted: Boolean? = null, val volume_percent: Int? = null
)

data class SpContext(val uri: String? = null, val type: String? = null)

data class SpPlaybackState(
    val device: SpDevice? = null, val repeat_state: String? = null, val shuffle_state: Boolean? = null,
    val is_playing: Boolean? = null, val progress_ms: Long? = null, val item: SpTrack? = null,
    val context: SpContext? = null, val currently_playing_type: String? = null
) {
    fun playing() = is_playing == true
}

/** Offset paging. Items may hold nulls (deleted playlists / unavailable tracks) — use [clean]. */
data class SpPaging<T>(
    val items: List<T?>? = null, val total: Int? = null, val limit: Int? = null,
    val offset: Int? = null, val next: String? = null
) {
    fun clean(): List<T> = items?.filterNotNull().orEmpty()
    fun hasMore() = next != null
}

data class SpCursors(val after: String? = null)

/** Cursor paging (followed artists). Pass [SpCursorPaging.cursors].after back as `after`. */
data class SpCursorPaging<T>(
    val items: List<T?>? = null, val cursors: SpCursors? = null, val total: Int? = null, val next: String? = null
) {
    fun clean(): List<T> = items?.filterNotNull().orEmpty()
    fun nextCursor(): String? = if (next != null) cursors?.after else null
}

data class SpSavedAlbum(val added_at: String? = null, val album: SpAlbum? = null)
data class SpSavedTrack(val added_at: String? = null, val track: SpTrack? = null)
/** A playlist entry; [track] is null for removed items and may be a podcast episode (type != "track"). */
data class SpPlaylistItem(val added_at: String? = null, val track: SpTrack? = null)

data class SpFollowedArtists(val artists: SpCursorPaging<SpArtist>? = null)
data class SpDevices(val devices: List<SpDevice>? = null)

data class SpSearchResult(
    val tracks: SpPaging<SpTrack>? = null, val albums: SpPaging<SpAlbum>? = null,
    val artists: SpPaging<SpArtist>? = null, val playlists: SpPaging<SpPlaylist>? = null
)

// ---- Request bodies ----
data class SpOffset(val position: Int? = null, val uri: String? = null)
data class SpPlayBody(
    val context_uri: String? = null, val uris: List<String>? = null,
    val offset: SpOffset? = null, val position_ms: Long? = null
)
data class SpTransferBody(val device_ids: List<String>, val play: Boolean)
