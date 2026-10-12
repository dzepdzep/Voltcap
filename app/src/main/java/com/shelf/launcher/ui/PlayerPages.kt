package com.shelf.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.NowPlaying
import kotlinx.coroutines.launch

/** Which face a small music player shows: the player, a playlist strip, or the lyrics. */
enum class PlayerPage { PLAYER, PLAYLISTS, LYRICS }

/**
 * Gestures shared by the small players (corner player, R1 player):
 * swipe left → playlists, swipe right → lyrics (and back again), a tap → the big music player.
 */
@Composable
internal fun Modifier.playerGestures(page: PlayerPage, onPage: (PlayerPage) -> Unit): Modifier {
    val current = rememberUpdatedState(page)
    val set = rememberUpdatedState(onPage)
    return this
        .pointerInput(Unit) {
            var dragged = 0f
            var fired = false
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f; fired = false },
                onHorizontalDrag = { change, amount ->
                    dragged += amount
                    if (!fired && kotlin.math.abs(dragged) > 40.dp.toPx()) {
                        fired = true
                        change.consume()
                        val left = dragged < 0
                        set.value(
                            when (current.value) {
                                PlayerPage.PLAYER -> if (left) PlayerPage.PLAYLISTS else PlayerPage.LYRICS
                                PlayerPage.PLAYLISTS -> if (left) PlayerPage.PLAYLISTS else PlayerPage.PLAYER
                                PlayerPage.LYRICS -> if (left) PlayerPage.PLAYER else PlayerPage.LYRICS
                            }
                        )
                    }
                }
            )
        }
        .pointerInput(Unit) { detectTapGestures(onTap = { MusicSwipe.openFull?.invoke() }) }
}

/** The user's Spotify playlists, loaded once per run and shared by every player. */
private object PlaylistCache {
    @Volatile var items: List<com.shelf.launcher.spotify.SpPlaylist>? = null
}

/**
 * A strip of playlists the size of the player it replaces: tap one to play it (Spotify). Without a
 * Spotify connection it says where to connect.
 */
@Composable
internal fun CompactPlaylists(
    height: Dp, textSize: TextUnit = 10.sp, modifier: Modifier = Modifier,
    /** D-pad reachable items (R1 sheet / popup); the corner player stays touch-only so it never traps focus. */
    focusableItems: Boolean = false,
    onPlayed: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { com.shelf.launcher.spotify.SpotifyRepository(context.applicationContext) }
    val connection by repo.connection.collectAsState()
    val scope = rememberCoroutineScope()
    val playlists by produceState(PlaylistCache.items, connection) {
        if (connection == com.shelf.launcher.spotify.SpotifyConnection.CONNECTED && value == null) {
            // Liked Songs first, like in the Spotify app (the Web API doesn't list it with the playlists).
            val r = runCatching { repo.myPlaylistsWithLiked() }.getOrNull()
            value = if (r is com.shelf.launcher.spotify.SpotifyResult.Ok) r.value else emptyList()
            if (r is com.shelf.launcher.spotify.SpotifyResult.Ok) PlaylistCache.items = value
        }
    }
    // Update 144: the player skin's ink (dark on the light skins), not always white; white outside a skinned player.
    val ink = LocalPlayerInk.current
    fun playIt(p: com.shelf.launcher.spotify.SpPlaylist) {
        if (p.uri == null) return
        scope.launch { runCatching { repo.playPlaylist(p) } }
        onPlayed()
    }
    Box(modifier.height(height).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        when {
            connection != com.shelf.launcher.spotify.SpotifyConnection.CONNECTED ->
                Text("Connect Spotify (Settings › Accounts) for playlists", color = ink.copy(alpha = 0.8f),
                    fontSize = textSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(textSize), maxFontSize = textSize), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            playlists == null -> Text("Loading playlists…", color = ink.copy(alpha = 0.8f), fontSize = textSize, modifier = Modifier.padding(horizontal = 4.dp))
            playlists!!.isEmpty() -> Text("No playlists", color = ink.copy(alpha = 0.8f), fontSize = textSize, modifier = Modifier.padding(horizontal = 4.dp))
            // A plain scrolling Row (not LazyRow): safe inside any parent measurement.
            else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                playlists!!.distinctBy { it.id ?: it.uri ?: it.name }.take(40).forEach { p ->
                    Row(
                        Modifier
                            .height(height - 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ink.copy(alpha = 0.14f))
                            .then(
                                if (focusableItems) Modifier.shelfClickable(RoundedCornerShape(6.dp)) { playIt(p) }
                                else Modifier.tapOnly { playIt(p) }
                            )
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = p.images?.lastOrNull()?.url ?: p.images?.firstOrNull()?.url, contentDescription = null,
                            modifier = Modifier.size(height - 4.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(p.name ?: "Playlist", color = ink, fontSize = textSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(textSize), maxFontSize = textSize), fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(height * 2.4f))
                    }
                }
            }
        }
    }
}

/**
 * The playlists as big covers with their names (the big music player's second page): tap or press one to play it.
 * Shares [PlaylistCache] with the small strips.
 */
@Composable
internal fun BigPlaylists(modifier: Modifier = Modifier, onPlayed: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { com.shelf.launcher.spotify.SpotifyRepository(context.applicationContext) }
    val connection by repo.connection.collectAsState()
    val scope = rememberCoroutineScope()
    val playlists by produceState(PlaylistCache.items, connection) {
        if (connection == com.shelf.launcher.spotify.SpotifyConnection.CONNECTED && value == null) {
            val r = runCatching { repo.myPlaylistsWithLiked() }.getOrNull()
            value = if (r is com.shelf.launcher.spotify.SpotifyResult.Ok) r.value else emptyList()
            if (r is com.shelf.launcher.spotify.SpotifyResult.Ok) PlaylistCache.items = value
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val list = playlists
        when {
            connection != com.shelf.launcher.spotify.SpotifyConnection.CONNECTED ->
                Text("Connect Spotify (Settings › Accounts) to see your playlists here.", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            list == null -> Text("Loading playlists…", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            list.isEmpty() -> Text("No playlists", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            else -> LazyVerticalGrid(
                GridCells.Adaptive(132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list.distinctBy { it.id ?: it.uri ?: it.name }.take(60)) { p ->
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .clip(shape)
                            .shelfClickable(shape) {
                                if (p.uri != null) {
                                    scope.launch { runCatching { repo.playPlaylist(p) } }
                                    onPlayed()
                                }
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = p.images?.firstOrNull()?.url ?: p.images?.lastOrNull()?.url, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            p.name ?: "Playlist", color = Color.White, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The current and next lyric line, the size of the player it replaces (LRCLIB, if switched on). */
@Composable
internal fun CompactLyrics(nowPlaying: NowPlaying, height: Dp, textSize: TextUnit = 10.sp, modifier: Modifier = Modifier) {
    val title = nowPlaying.title
    val lyricsOn by com.shelf.launcher.data.LyricsOptIn.enabled.collectAsState()
    val lyrics by produceState<com.shelf.launcher.data.Lyrics?>(null, title, nowPlaying.artist, lyricsOn) {
        value = if (title == null || !lyricsOn) null
        else com.shelf.launcher.data.LyricsRepository.fetch(title, nowPlaying.artist, nowPlaying.album, nowPlaying.durationMs)
    }
    val position = rememberPlaybackPosition(nowPlaying)
    val l = lyrics
    // Update 144: the player skin's ink (dark on the light skins), not always white.
    val ink = LocalPlayerInk.current
    val (now, next) = when {
        !lyricsOn -> "Lyrics are off — Settings › Audio › Lyrics" to null
        l == null -> "No lyrics found" to null
        l.isSynced -> {
            val i = l.synced.indexOfLast { it.atMs <= position }
            (l.synced.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪") to l.synced.getOrNull(i + 1)?.text
        }
        else -> (l.plain.firstOrNull() ?: "♪") to l.plain.getOrNull(1)
    }
    Column(modifier.height(height).fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
        Text(now, color = ink, fontSize = textSize, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(textSize), maxFontSize = textSize), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (next != null) Text(next, color = ink.copy(alpha = 0.55f), fontSize = textSize * 0.9f, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(textSize * 0.9f), maxFontSize = textSize * 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The album art, blurred and darkened, behind a player (pass Modifier.matchParentSize()). The blur
 * follows the one shared setting ([AlbumArtBlur]); [maxBlur] is this player's strongest blur.
 */
@Composable
internal fun ArtBackdrop(art: Bitmap?, modifier: Modifier, maxBlur: Float = 22f, scrim: Float = 0.42f) {
    val image = remember(art) { art?.let { runCatching { it.asImageBitmap() }.getOrNull() } }
    val blur = AlbumArtBlur.radius(maxBlur)
    if (image != null && AlbumArtBlur.enabled) {
        Image(image, null, contentScale = ContentScale.Crop, modifier = modifier.blur(blur))
        Box(modifier.background(Color.Black.copy(alpha = scrim)))
    }
}

/** A small "back to the player" chevron for the playlist / lyrics pages. */
@Composable
internal fun PageBack(size: Dp, onClick: () -> Unit) {
    // Update 144: the player skin's ink, so the chevron shows on the light skins too.
    val ink = LocalPlayerInk.current
    Box(
        Modifier.size(size + 6.dp).clip(CircleShape).tapOnly(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.ChevronLeft, "Back to player", tint = ink, modifier = Modifier.size(size))
    }
}
