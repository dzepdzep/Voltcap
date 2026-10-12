package com.shelf.launcher.ui.spotify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.shelf.launcher.spotify.SPOTIFY_PREMIUM_MESSAGE
import com.shelf.launcher.spotify.SpAlbum
import com.shelf.launcher.spotify.SpArtist
import com.shelf.launcher.spotify.SpDevice
import com.shelf.launcher.spotify.SpPaging
import com.shelf.launcher.spotify.SpPlaylist
import com.shelf.launcher.spotify.SpSearchResult
import com.shelf.launcher.spotify.SpTrack
import com.shelf.launcher.spotify.SpotifyConnection
import com.shelf.launcher.spotify.SpotifyErrorKind
import com.shelf.launcher.spotify.SpotifyRepository
import com.shelf.launcher.spotify.SpotifyResult
import com.shelf.launcher.spotify.best
import com.shelf.launcher.ui.Chip
import com.shelf.launcher.ui.ChipFlow
import com.shelf.launcher.ui.MusicApps
import com.shelf.launcher.ui.MutedNote
import com.shelf.launcher.ui.ShelfTextField
import com.shelf.launcher.ui.formatTrackTime
import com.shelf.launcher.ui.shelfClickable
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private enum class SpTab(val label: String) { LIBRARY("Library"), SEARCH("Search"), NOW("Now Playing") }
private enum class LibSection(val label: String) { PLAYLISTS("Playlists"), ALBUMS("Albums"), ARTISTS("Artists"), LIKED("Liked songs") }

/** One page pushed on top of the tabs (B pops it). */
private sealed class Detail {
    data class Album(val album: SpAlbum) : Detail()
    data class Playlist(val playlist: SpPlaylist) : Detail()
    data class Artist(val artist: SpArtist) : Detail()
}

/** Something went wrong with playback: [retry] repeats the command (optionally on a chosen device). */
private class Issue(
    val kind: SpotifyErrorKind,
    val message: String,
    val openUri: String?,
    val retry: (suspend (String?) -> SpotifyResult<Unit>)?
)

private class PageResult<T>(val items: List<T>, val next: Any?, val hasMore: Boolean)

/** Loads a list a page at a time; [loadMore] is a no-op while loading or once everything is in. */
private class Pager<T>(private val load: suspend (Any?) -> SpotifyResult<PageResult<T>>) {
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false)
    var done by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var cursor: Any? = null

    suspend fun loadMore() {
        if (loading || done) return
        loading = true
        // (update 91) Always cleared: a load cancelled by leaving the list (other section, a detail page)
        // used to leave it "loading" for good, so it never loaded again.
        try {
            when (val r = load(cursor)) {
                is SpotifyResult.Ok -> { items.addAll(r.value.items); cursor = r.value.next; done = !r.value.hasMore; error = null }
                is SpotifyResult.Error -> { error = r.message; done = true }
            }
        } finally {
            loading = false
        }
    }
}

private fun <S, T> SpotifyResult<SpPaging<S>>.page(offset: Int, map: (List<S>) -> List<T>): SpotifyResult<PageResult<T>> = when (this) {
    is SpotifyResult.Ok -> SpotifyResult.Ok(PageResult(map(value.clean()), offset + (value.items?.size ?: 0), value.hasMore()))
    is SpotifyResult.Error -> this
}

private fun Any?.offset() = (this as? Int) ?: 0

/**
 * Browse and play your Spotify library: Library (playlists, albums, artists,
 * liked songs), Search and Now Playing, with a compact player bar. Fully
 * usable with D-pad + A + B. Playback control needs Premium; without it every
 * error offers "Open in Spotify" instead.
 */
@Composable
fun SpotifyScreen(
    onDismiss: () -> Unit,
    onOpenAccounts: () -> Unit,
    blurPercent: Int,
    onBlurChange: (Int) -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("SpotifyScreen")
    val context = LocalContext.current
    val repo = remember { SpotifyRepository(context) }
    val scope = rememberCoroutineScope()
    val connection by repo.connection.collectAsState()
    val authError by repo.authError.collectAsState()
    val nowPlaying by repo.nowPlaying.collectAsState()
    val connected = connection == SpotifyConnection.CONNECTED

    var tab by remember { mutableStateOf(SpTab.LIBRARY) }
    var lastBrowse by remember { mutableStateOf(SpTab.LIBRARY) }
    var section by remember { mutableStateOf(LibSection.PLAYLISTS) }
    val detail = remember { mutableStateListOf<Detail>() }
    var issue by remember { mutableStateOf<Issue?>(null) }
    var lastUri by remember { mutableStateOf<String?>(null) }

    // The poller runs only while this screen is visible AND the app is in the foreground.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(connected, lifecycleOwner) {
        var started = false
        fun start() { if (connected && !started) { repo.startNowPlaying(); started = true } }
        fun stop() { if (started) { repo.stopNowPlaying(); started = false } }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> start(); Lifecycle.Event.ON_STOP -> stop(); else -> {} }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(connected) { if (connected) repo.refreshNowPlaying() }

    val controls = remember(repo) {
        SpotifyControls(repo, scope) { err, retry ->
            issue = Issue(err.kind, err.message, lastUri ?: repo.nowPlaying.value?.item?.uri, retry)
        }
    }

    fun goBack() {
        when {
            detail.isNotEmpty() -> detail.removeAt(detail.lastIndex)
            tab == SpTab.NOW -> tab = lastBrowse
            else -> onDismiss()
        }
    }
    fun selectTab(t: SpTab) {
        if (t != SpTab.NOW && t != lastBrowse) detail.clear()
        if (t != SpTab.NOW) lastBrowse = t
        tab = t
    }
    fun play(contextUri: String? = null, uris: List<String>? = null, offsetUri: String? = null, openUri: String?) {
        lastUri = openUri
        issue = null
        // A picked song (in a playlist, an album, Liked Songs) plays first even with shuffle on.
        val picked = offsetUri != null || (contextUri == null && !uris.isNullOrEmpty())
        controls.run { d ->
            suspend fun go(dev: String?): SpotifyResult<Unit> =
                if (picked) repo.playPicked(contextUri, uris, offsetUri, dev)
                else repo.play(contextUri = contextUri, uris = uris, offsetUri = offsetUri, deviceId = dev)
            val r = go(d)
            // Nothing playing anywhere: wake Spotify here in the background (no app opening) and play there.
            if (r is SpotifyResult.Error && r.kind == SpotifyErrorKind.NO_ACTIVE_DEVICE && d == null &&
                com.shelf.launcher.data.Features.spotifyQuietWake.value
            ) {
                val here = repo.wakeLocalSpotify(context)
                if (here != null) go(here) else r
            } else r
        }
    }

    // Everything is reloaded after a (re)connect.
    // Liked Songs leads the playlists (as in the Spotify app; the Web API doesn't list it there).
    // Spotify-made playlists pinned by hand (SpotifyPins: the Web API hides them from apps like this one) come
    // right after Liked Songs.
    var pinsRev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var addingLink by remember { mutableStateOf(false) }
    val playlists = remember(connected, pinsRev) {
        Pager<SpPlaylist> { c ->
            val off = c.offset()
            val liked = if (off == 0) runCatching { repo.likedSongsPlaylist() }.getOrNull() else null
            val pins = if (off == 0) com.shelf.launcher.spotify.SpotifyPins.all(context) else emptyList()
            repo.myPlaylists(off).page(off) { l -> listOfNotNull(liked) + pins.filter { p -> l.none { it?.id == p.id } } + l }
        }
    }
    val albums = remember(connected) { Pager<SpAlbum> { c -> repo.savedAlbums(c.offset()).page(c.offset()) { l -> l.mapNotNull { it.album } } } }
    val liked = remember(connected) { Pager<SpTrack> { c -> repo.savedTracks(c.offset()).page(c.offset()) { l -> l.mapNotNull { it.track } } } }
    val artists = remember(connected) {
        Pager<SpArtist> { c ->
            when (val r = repo.followedArtists(c as? String)) {
                is SpotifyResult.Ok -> SpotifyResult.Ok(PageResult(r.value.clean(), r.value.nextCursor(), r.value.nextCursor() != null))
                is SpotifyResult.Error -> r
            }
        }
    }

    Dialog(onDismissRequest = { goBack() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.shelf.launcher.ui.ImmersiveDialogWindow()
        Box(Modifier.fillMaxSize().background(ShelfTheme.background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Spotify", color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    // The tabs wrap onto a second line on a narrow screen instead of pushing Close off it.
                    Box(Modifier.weight(1f)) {
                        if (connected) ChipFlow { SpTab.values().forEach { t -> Chip(t.label, accent = tab == t) { selectTab(t) } } }
                    }
                    Chip("Close", accent = false) { onDismiss() }
                }
                Spacer(Modifier.height(8.dp))

                if (!connected) {
                    NotConnectedPanel(connection, authError, onConnect = {
                        scope.launch { if (repo.clientId().isBlank()) onOpenAccounts() else repo.connect(context) }
                    }, onCancel = { repo.cancelConnect() }, onOpenAccounts = onOpenAccounts)
                } else {
                    issue?.let { i ->
                        IssueBanner(i, repo, onDismiss = { issue = null }, onFixed = { issue = null })
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val top = detail.lastOrNull()
                        when {
                            tab == SpTab.NOW -> SpotifyFullPlayer(
                                nowPlaying, controls, blurPercent, onBlurChange,
                                onOpenInSpotify = { repo.openInSpotifyApp(context, it) }
                            )
                            top is Detail.Album -> AlbumPage(repo, top.album, nowPlaying?.item?.uri, ::play)
                            top is Detail.Playlist -> PlaylistPage(repo, top.playlist, nowPlaying?.item?.uri, ::play, onUnpin = {
                                com.shelf.launcher.spotify.SpotifyPins.remove(context, top.playlist.id.orEmpty()); pinsRev++; detail.removeAt(detail.lastIndex)
                            })
                            top is Detail.Artist -> ArtistPage(repo, top.artist, ::play) { detail.add(Detail.Album(it)) }
                            tab == SpTab.SEARCH -> SearchPage(
                                repo, nowPlaying?.item?.uri, ::play,
                                onAlbum = { detail.add(Detail.Album(it)) },
                                onArtist = { detail.add(Detail.Artist(it)) },
                                onPlaylist = { detail.add(Detail.Playlist(it)) }
                            )
                            else -> Column(Modifier.fillMaxSize()) {
                                ChipFlow {
                                    LibSection.values().forEach { s -> Chip(s.label, accent = section == s) { section = s } }
                                }
                                Spacer(Modifier.height(8.dp))
                                when (section) {
                                    LibSection.PLAYLISTS -> Column(Modifier.fillMaxSize()) {
                                        // Discover Weekly, Release Radar, Daily Mixes…: Spotify hides its own playlists from apps
                                        // like this one, so they're added by link or from what's playing.
                                        val ctxUri = nowPlaying?.context?.uri
                                        val ctxId = ctxUri?.takeIf { it.startsWith("spotify:playlist:") }?.substringAfterLast(':')
                                        val showPinNow = ctxId != null && playlists.items.none { it.id == ctxId }
                                        ChipFlow {
                                            Chip("+ Spotify playlist by link", accent = false) { addingLink = true }
                                            if (showPinNow && ctxId != null) Chip("Pin what's playing") {
                                                scope.launch { com.shelf.launcher.spotify.SpotifyPins.add(context, ctxId); pinsRev++ }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        PagedGrid(playlists, "No playlists yet.") { p ->
                                            CoverCard(p.images.best(300), p.name ?: "Playlist", p.owner?.display_name.orEmpty()) {
                                                if (p.id == com.shelf.launcher.spotify.LIKED_ID) section = LibSection.LIKED else detail.add(Detail.Playlist(p))
                                            }
                                        }
                                    }
                                    LibSection.ALBUMS -> PagedGrid(albums, "No saved albums yet.") { a ->
                                        CoverCard(a.images.best(300), a.name ?: "Album", a.artistLine()) { detail.add(Detail.Album(a)) }
                                    }
                                    LibSection.ARTISTS -> PagedGrid(artists, "You don't follow any artists yet.") { a ->
                                        CoverCard(a.images.best(300), a.name ?: "Artist", "Artist", round = true) { detail.add(Detail.Artist(a)) }
                                    }
                                    LibSection.LIKED -> PagedTracks(liked, "No liked songs yet.", nowPlaying?.item?.uri, showCover = true) { idx, t ->
                                        // Liked songs have no context: queue this one and the next few.
                                        val uris = liked.items.drop(idx).take(50).filter { it.playable() }.mapNotNull { it.uri }
                                        play(uris = uris, openUri = t.uri)
                                    }
                                }
                            }
                        }
                    }
                    if (tab != SpTab.NOW && nowPlaying?.item != null) {
                        Spacer(Modifier.height(8.dp))
                        SpotifyPlayerBar(nowPlaying, controls, onOpen = { selectTab(SpTab.NOW) })
                    }
                    if (addingLink) AddPlaylistLinkDialog(onDismiss = { addingLink = false }) { id ->
                        scope.launch { com.shelf.launcher.spotify.SpotifyPins.add(context, id); pinsRev++ }
                        addingLink = false
                    }
                }
            }
        }
    }
}

// ---- Not connected ----

@Composable
private fun NotConnectedPanel(
    connection: SpotifyConnection,
    error: String?,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onOpenAccounts: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center) {
        Text(
            when (connection) {
                SpotifyConnection.CONNECTING -> "Waiting for Spotify in your browser…"
                SpotifyConnection.NEEDS_RECONNECT -> "Spotify needs to be reconnected."
                SpotifyConnection.NO_CLIENT_ID -> "Spotify isn't set up yet."
                else -> "Connect your Spotify account to browse your playlists, albums and liked songs."
            },
            color = ShelfTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        error?.let { Spacer(Modifier.height(4.dp)); MutedNote(it) }
        Spacer(Modifier.height(6.dp))
        MutedNote("You need a free Spotify developer app (Settings › Accounts › Spotify explains it). Playing music from here needs Spotify Premium; browsing works with any account.")
        Spacer(Modifier.height(12.dp))
        ChipFlow {
            Chip(if (connection == SpotifyConnection.NEEDS_RECONNECT) "Reconnect" else "Connect Spotify") { onConnect() }
            if (connection == SpotifyConnection.CONNECTING) Chip("Cancel", accent = false) { onCancel() }
            Chip("Settings › Accounts", accent = false) { onOpenAccounts() }
        }
    }
}

// ---- Inline problem help ----

@Composable
private fun IssueBanner(issue: Issue, repo: SpotifyRepository, onDismiss: () -> Unit, onFixed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<SpDevice>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33E0A030)).padding(12.dp)
    ) {
        Text(
            when (issue.kind) {
                SpotifyErrorKind.PREMIUM -> SPOTIFY_PREMIUM_MESSAGE
                SpotifyErrorKind.NO_ACTIVE_DEVICE -> "Spotify isn't playing on any device right now. Pick a device, or open the Spotify app once."
                else -> issue.message
            },
            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp
        )
        status?.let { Spacer(Modifier.height(4.dp)); MutedNote(it) }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            issue.openUri?.let { uri ->
                Chip("Open in Spotify") { if (!repo.openInSpotifyApp(context, uri)) status = "Couldn't open Spotify." }
            }
            if (issue.kind == SpotifyErrorKind.NO_ACTIVE_DEVICE) {
                Chip("Choose device", accent = false) {
                    scope.launch {
                        when (val r = repo.devices()) {
                            is SpotifyResult.Ok -> {
                                devices = r.value.filter { it.id != null }
                                if (r.value.isEmpty()) status = "No Spotify devices found. Open the Spotify app on this device first."
                            }
                            is SpotifyResult.Error -> status = r.message
                        }
                    }
                }
                Chip("Play here (without opening Spotify)") {
                    status = "Waking Spotify in the background…"
                    scope.launch {
                        val id = repo.wakeLocalSpotify(context)
                        if (id == null) { status = "Spotify didn't wake up. Open it once and sign in, then try again."; return@launch }
                        val r = issue.retry?.invoke(id) ?: SpotifyResult.Ok(Unit)
                        when (r) {
                            is SpotifyResult.Ok -> { repo.refreshNowPlaying(); onFixed() }
                            is SpotifyResult.Error -> status = r.message
                        }
                    }
                }
                Chip("Open Spotify app", accent = false) { MusicApps.open(context, MusicApps.SPOTIFY) }
            }
            Chip("Dismiss", accent = false) { onDismiss() }
        }
        devices?.takeIf { it.isNotEmpty() }?.let { list ->
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                list.forEach { d ->
                    Chip(d.name ?: "Device", accent = d.is_active == true) {
                        scope.launch {
                            val id = d.id ?: return@launch
                            var r: SpotifyResult<Unit> = repo.transferPlayback(id, true)
                            if (r is SpotifyResult.Ok) issue.retry?.let { r = it(id) }
                            when (val res = r) {
                                is SpotifyResult.Ok -> { repo.refreshNowPlaying(); onFixed() }
                                is SpotifyResult.Error -> status = res.message
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Lists ----

@Composable
private fun <T> LoadMoreOnScrollEnd(state: LazyListState, pager: Pager<T>) {
    LaunchedEffect(pager) {
        snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to state.layoutInfo.totalItemsCount }
            .filter { (last, total) -> total == 0 || last >= total - 6 }
            .collect { pager.loadMore() }
    }
}

@Composable
private fun <T> LoadMoreOnScrollEnd(state: LazyGridState, pager: Pager<T>) {
    LaunchedEffect(pager) {
        snapshotFlow { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to state.layoutInfo.totalItemsCount }
            .filter { (last, total) -> total == 0 || last >= total - 6 }
            .collect { pager.loadMore() }
    }
}

@Composable
private fun ListFooter(pager: Pager<*>, emptyText: String) {
    when {
        pager.error != null -> Text(pager.error ?: "", color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        pager.loading -> Text("Loading…", color = ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        pager.done && pager.items.isEmpty() -> Text(emptyText, color = ShelfTheme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun <T> PagedGrid(pager: Pager<T>, emptyText: String, cell: @Composable (T) -> Unit) {
    val state = rememberLazyGridState()
    LoadMoreOnScrollEnd(state, pager)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp), state = state, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pager.items.toList()) { cell(it) }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { ListFooter(pager, emptyText) }
    }
}

@Composable
private fun PagedTracks(
    pager: Pager<SpTrack>, emptyText: String, playingUri: String?, showCover: Boolean,
    onPlay: (Int, SpTrack) -> Unit
) {
    val state = rememberLazyListState()
    LoadMoreOnScrollEnd(state, pager)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(pager.items.toList()) { i, t -> TrackRow(null, t, showCover, playingUri) { onPlay(i, t) } }
        item { ListFooter(pager, emptyText) }
    }
}

@Composable
private fun CoverCard(url: String?, title: String, subtitle: String, round: Boolean = false, onClick: () -> Unit) {
    val shape: Shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).shelfClickable(RoundedCornerShape(12.dp)) { onClick() }.padding(6.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start
    ) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(Modifier.height(6.dp))
        Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** A track line: number (album / playlist) or cover, title, artists, duration. Unplayable tracks are dimmed. */
@Composable
private fun TrackRow(number: Int?, t: SpTrack, showCover: Boolean, playingUri: String?, onClick: () -> Unit) {
    val current = playingUri != null && playingUri == t.uri
    val alpha = if (t.playable()) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).shelfClickable(RoundedCornerShape(10.dp), enabled = t.playable()) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Text("$number", color = if (current) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 12.sp, modifier = Modifier.width(30.dp))
        } else if (showCover) {
            AsyncImage(
                model = t.album?.images.best(96), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.name ?: "Unknown track", fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = (if (current) ShelfTheme.accent else ShelfTheme.colors.textPrimary).copy(alpha = alpha)
            )
            val sub = if (t.playable()) t.artistLine() else "Not available in your region"
            if (sub.isNotBlank()) Text(sub, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        t.duration_ms?.let { Text(formatTrackTime(it), color = ShelfTheme.textMuted, fontSize = 12.sp) }
    }
}

// ---- Detail pages ----

@Composable
private fun DetailHeader(cover: String?, title: String, subtitle: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = cover, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            ChipFlow { actions() }
        }
    }
}

@Composable
private fun AlbumPage(
    repo: SpotifyRepository, album: SpAlbum, playingUri: String?,
    play: (String?, List<String>?, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val pager = remember(album.id) { Pager<SpTrack> { c -> repo.albumTracks(album.id.orEmpty(), c.offset()).page(c.offset()) { it } } }
    val state = rememberLazyListState()
    LoadMoreOnScrollEnd(state, pager)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            DetailHeader(
                album.images.best(300), album.name ?: "Album",
                listOf(album.artistLine(), album.release_date?.take(4).orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            ) {
                Chip("Play") { play(album.uri, null, null, album.uri) }
                album.uri?.let { u -> Chip("Open in Spotify", accent = false) { repo.openInSpotifyApp(context, u) } }
            }
        }
        itemsIndexed(pager.items.toList()) { i, t ->
            TrackRow(t.track_number ?: (i + 1), t, false, playingUri) { play(album.uri, null, t.uri, t.uri ?: album.uri) }
        }
        item { ListFooter(pager, "No tracks.") }
    }
}

@Composable
private fun PlaylistPage(
    repo: SpotifyRepository, playlist: SpPlaylist, playingUri: String?,
    play: (String?, List<String>?, String?, String?) -> Unit,
    onUnpin: () -> Unit = {}
) {
    val context = LocalContext.current
    val pinned = remember(playlist.id) { com.shelf.launcher.spotify.SpotifyPins.isPinned(context, playlist.id.orEmpty()) }
    val pager = remember(playlist.id) {
        Pager<SpTrack> { c ->
            repo.playlistTracks(playlist.id.orEmpty(), c.offset()).page(c.offset()) { l ->
                // Removed items come back without a track; podcast episodes aren't tracks.
                l.mapNotNull { it.track }.filter { it.type == null || it.type == "track" }
            }
        }
    }
    val state = rememberLazyListState()
    LoadMoreOnScrollEnd(state, pager)
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            DetailHeader(
                playlist.images.best(300), playlist.name ?: "Playlist",
                listOfNotNull(playlist.owner?.display_name, playlist.tracks?.total?.let { "$it songs" }).joinToString(" · ")
            ) {
                Chip("Play") { play(playlist.uri, null, null, playlist.uri) }
                playlist.uri?.let { u -> Chip("Open in Spotify", accent = false) { repo.openInSpotifyApp(context, u) } }
                if (pinned) Chip("Remove from list", accent = false) { onUnpin() }
            }
            if (pinned) com.shelf.launcher.ui.MutedNote(
                "A Spotify-made playlist: Spotify doesn't let apps like Voltcap read its songs, but Play works — Spotify plays it as usual."
            )
        }
        itemsIndexed(pager.items.toList()) { i, t ->
            TrackRow(i + 1, t, false, playingUri) { play(playlist.uri, null, t.uri, t.uri ?: playlist.uri) }
        }
        item { ListFooter(pager, "This playlist is empty.") }
    }
}

@Composable
private fun ArtistPage(
    repo: SpotifyRepository, artist: SpArtist,
    play: (String?, List<String>?, String?, String?) -> Unit,
    onAlbum: (SpAlbum) -> Unit
) {
    val context = LocalContext.current
    val pager = remember(artist.id) { Pager<SpAlbum> { c -> repo.artistAlbums(artist.id.orEmpty(), c.offset()).page(c.offset()) { it } } }
    Column(Modifier.fillMaxSize()) {
        DetailHeader(artist.images.best(300), artist.name ?: "Artist", artist.genres?.take(3)?.joinToString(", ").orEmpty()) {
            Chip("Play") { play(artist.uri, null, null, artist.uri) }
            artist.uri?.let { u -> Chip("Open in Spotify", accent = false) { repo.openInSpotifyApp(context, u) } }
        }
        PagedGrid(pager, "No albums found.") { a ->
            CoverCard(a.images.best(300), a.name ?: "Album", listOf(a.release_date?.take(4).orEmpty(), a.album_type.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")) { onAlbum(a) }
        }
    }
}

// ---- Search ----

@Composable
private fun SearchPage(
    repo: SpotifyRepository, playingUri: String?,
    play: (String?, List<String>?, String?, String?) -> Unit,
    onAlbum: (SpAlbum) -> Unit, onArtist: (SpArtist) -> Unit, onPlaylist: (SpPlaylist) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SpSearchResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run() {
        val q = query.trim()
        if (q.isEmpty() || busy) return
        busy = true
        scope.launch {
            when (val r = repo.search(q)) {
                is SpotifyResult.Ok -> { result = r.value; error = null }
                is SpotifyResult.Error -> error = r.message
            }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        ShelfTextField(value = query, onValueChange = { query = it }, label = "Search Spotify", onSubmit = { run() })
        Spacer(Modifier.height(8.dp))
        if (busy) MutedNote("Searching…")
        error?.let { MutedNote(it) }
        val res = result
        if (res != null) {
            val tracks = res.tracks?.clean().orEmpty().filter { it.name != null }
            val albums = res.albums?.clean().orEmpty()
            val artists = res.artists?.clean().orEmpty()
            val playlists = res.playlists?.clean().orEmpty()
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (tracks.isNotEmpty()) {
                    item { SectionTitle("Songs") }
                    items(tracks.size) { i -> val t = tracks[i]; TrackRow(null, t, true, playingUri) { play(null, listOfNotNull(t.uri), null, t.uri) } }
                }
                if (albums.isNotEmpty()) {
                    item { SectionTitle("Albums") }
                    items(albums.size) { i -> val a = albums[i]; ResultRow(a.images.best(96), a.name ?: "Album", a.artistLine(), false) { onAlbum(a) } }
                }
                if (artists.isNotEmpty()) {
                    item { SectionTitle("Artists") }
                    items(artists.size) { i -> val a = artists[i]; ResultRow(a.images.best(96), a.name ?: "Artist", "Artist", true) { onArtist(a) } }
                }
                if (playlists.isNotEmpty()) {
                    item { SectionTitle("Playlists") }
                    items(playlists.size) { i -> val p = playlists[i]; ResultRow(p.images.best(96), p.name ?: "Playlist", p.owner?.display_name.orEmpty(), false) { onPlaylist(p) } }
                }
                if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()) {
                    item { MutedNote("Nothing found for \"${query.trim()}\".") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun ResultRow(cover: String?, title: String, subtitle: String, round: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).shelfClickable(RoundedCornerShape(10.dp)) { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = cover, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}


/** "+ Spotify playlist by link": paste a share link of one of Spotify's own playlists (Share › Copy link in Spotify). */
@Composable
private fun AddPlaylistLinkDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val id = com.shelf.launcher.spotify.SpotifyPins.idFrom(text)
    com.shelf.launcher.ui.ShelfDialog(
        icon = androidx.compose.material.icons.Icons.Filled.LibraryMusic,
        title = "Add a Spotify playlist",
        subtitle = "Discover Weekly, Release Radar, Daily Mixes and Spotify's own lists don't come through Spotify's app access. " +
            "In Spotify: the playlist's ••• › Share › Copy link, then paste it here.",
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.5f
    ) {
        com.shelf.launcher.ui.ShelfTextField(text, { text = it.take(300) }, "Playlist link", onSubmit = { id?.let(onAdd) })
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            Chip("Paste", accent = false) {
                text = runCatching {
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .primaryClip?.getItemAt(0)?.text?.toString()
                }.getOrNull().orEmpty()
            }
            Chip("Add", enabled = id != null) { id?.let(onAdd) }
        }
        if (text.isNotBlank() && id == null) com.shelf.launcher.ui.MutedNote("That isn't a playlist link (it looks like https://open.spotify.com/playlist/…).")
    }
}
