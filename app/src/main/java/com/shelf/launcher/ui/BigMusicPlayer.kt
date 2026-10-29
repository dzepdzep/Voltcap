package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * The big music player that opens when you press a small player (the corner player, the player on the home
 * screen's background, the player card on the second screen): a large cover, big title, a long timeline and big
 * previous / play / next buttons over the cover blurred. Swipe left or press the "Playlists ›" arrow in the corner
 * for your Spotify playlists as big covers (tap one to play it); swipe right or "‹ Player" comes back.
 * Everything is reachable with the D-pad, controller and touch.
 */
@Composable
internal fun BigMusicPlayer(
    nowPlaying: NowPlaying,
    blurArt: Boolean,
    blurPercent: Int,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    /** Opens the Spotify library screen; null hides the chip. */
    onBrowseSpotify: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("BigMusicPlayer")
    var playlists by remember { mutableStateOf(false) }
    val artColor = rememberArtColor(nowPlaying.art, ShelfTheme.accent)
    val appLabel = rememberAppLabel(nowPlaying.packageName)
    val position = rememberPlaybackPosition(nowPlaying)
    val image = remember(nowPlaying.art) { nowPlaying.art?.let { runCatching { it.asImageBitmap() }.getOrNull() } }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swipe left: the playlists; swipe right: back to the player. (The timeline and the lists take their own drags first.)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = { total = 0f },
                    onDragEnd = {
                        if (total < -70.dp.toPx()) playlists = true else if (total > 70.dp.toPx()) playlists = false
                    },
                    onHorizontalDrag = { _, amount -> total += amount }
                )
            }
    ) {
        if (blurArt && image != null) {
            Image(
                image, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadiusFor(blurPercent, 26f))
                    .graphicsLayer { alpha = 0.6f }
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(artColor.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.82f)))
            )
        )

        if (!playlists) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                val wide = maxWidth > maxHeight * 1.15f
                val compact = maxHeight < 380.dp || maxWidth < 360.dp
                if (wide) {
                    // The cover as big as the height allows; the controls beside it, clear of the chips (top) and the arrow (bottom).
                    val artSize = minOf(maxHeight - 8.dp, maxWidth * 0.46f).coerceAtLeast(120.dp)
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        AlbumArt(nowPlaying.art, artSize)
                        Spacer(Modifier.width(if (compact) 18.dp else 30.dp))
                        BigInfo(
                            nowPlaying, appLabel, position, showLyrics, compact,
                            onPlayPause, onNext, onPrevious, onSeek,
                            Modifier.weight(1f).fillMaxHeight().padding(top = 44.dp, bottom = 52.dp)
                        )
                    }
                } else {
                    val artSize = minOf(maxWidth * 0.78f, maxHeight * (if (showLyrics) 0.30f else 0.42f)).coerceAtLeast(96.dp)
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(if (compact) 40.dp else 46.dp))
                        AlbumArt(nowPlaying.art, artSize)
                        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                        BigInfo(
                            nowPlaying, appLabel, position, showLyrics, compact,
                            onPlayPause, onNext, onPrevious, onSeek,
                            Modifier.fillMaxWidth().weight(1f).padding(bottom = 52.dp)
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 62.dp)) {
                Text(
                    "Playlists", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, end = 120.dp, bottom = 6.dp)
                )
                BigPlaylists(Modifier.weight(1f).fillMaxWidth()) { playlists = false }
            }
        }

        // Top-right chips.
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onBrowseSpotify?.let { Chip("Spotify", accent = false, onClick = it) }
            if (!playlists) Chip("Lyrics", accent = showLyrics, onClick = onToggleLyrics)
            Chip("Close", accent = false, onClick = onClose)
        }

        // The corner arrow: playlists (bottom right) / back to the player (bottom left).
        val pill = RoundedCornerShape(50)
        Row(
            Modifier
                .align(if (playlists) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(14.dp)
                .clip(pill)
                .background(Color.White.copy(alpha = 0.18f))
                .shelfClickable(pill) { playlists = !playlists }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlists) {
                Icon(Icons.Filled.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text("Player", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Playlists", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Title, artist, the timeline and the big buttons (and the lyrics under them when they're on). */
@Composable
private fun BigInfo(
    nowPlaying: NowPlaying,
    appLabel: String,
    position: Long,
    showLyrics: Boolean,
    compact: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        FullText(
            "PLAYING FROM ${appLabel.uppercase()}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        // (Update 120: with the lyrics on, the title and artist keep to one line each, so the lyrics always have room —
        // a long title used to take it all and the lyrics didn't show.)
        FitText(
            nowPlaying.title ?: "Unknown track", Color.White,
            maxSize = if (compact) 24.sp else 36.sp, minSize = 16.sp, maxLines = if (showLyrics) 1 else 2, fontWeight = FontWeight.Bold
        )
        FullText(
            listOfNotNull(nowPlaying.artist, nowPlaying.album).joinToString(" • ").ifEmpty { appLabel },
            color = Color.White.copy(alpha = 0.78f), fontSize = if (compact) 14.sp else 18.sp, maxLines = if (showLyrics) 1 else 2
        )
        Spacer(Modifier.height(if (compact || showLyrics) 10.dp else 20.dp))
        SeekBar(position, nowPlaying.durationMs, nowPlaying.canSeek, onSeek)
        if (nowPlaying.durationMs > 0) {
            Row(Modifier.fillMaxWidth()) {
                Text(formatTrackTime(position), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTrackTime(nowPlaying.durationMs), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(if (compact || showLyrics) 8.dp else 16.dp))
        val side = if (compact) 52.dp else 68.dp
        val mid = if (compact) 72.dp else 92.dp
        // Update 124: shuffle on the left while Spotify plays (MusicShuffle.kt), and as much room on the right so play
        // stays in the middle.
        val shuffle = MusicShuffle.isSpotify(nowPlaying.packageName)
        val small = side * 0.8f
        val gap = if (compact) 16.dp else 28.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (shuffle) {
                ShuffleButton(nowPlaying, small, filled = true)
                Spacer(Modifier.width(gap * 0.6f))
            }
            BigControl(Icons.Filled.SkipPrevious, "Previous", side, onPrevious)
            Spacer(Modifier.width(gap))
            BigPlay(nowPlaying.isPlaying, mid, onPlayPause)
            Spacer(Modifier.width(gap))
            BigControl(Icons.Filled.SkipNext, "Next", side, onNext)
            if (shuffle) {
                Spacer(Modifier.width(gap * 0.6f))
                Spacer(Modifier.size(small))
            }
        }
        if (showLyrics) {
            Spacer(Modifier.height(8.dp))
            // (Asks for at least two lines' room.)
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 56.dp).clipToBounds(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { LyricsOrHint(nowPlaying, position, compact) }
        }
    }
}

@Composable
private fun BigControl(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.58f)) }
}

@Composable
private fun BigPlay(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.White).shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play",
            tint = Color.Black, modifier = Modifier.size(size * 0.58f)
        )
    }
}


/**
 * The lyrics under the player, or a line saying why there are none: lyrics are opt-in (they come from LRCLIB), so
 * with the switch off pressing "Lyrics" used to show nothing at all. Now it says so and turns them on in one tap.
 */
@Composable
internal fun LyricsOrHint(nowPlaying: NowPlaying, position: Long, compact: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val on by com.shelf.launcher.data.LyricsOptIn.enabled.collectAsState()
    val title = nowPlaying.title
    // 0 = looking, 1 = found, 2 = none found.
    // Update 120: the same look-up as the lyric themes, which tries again when it failed (offline for a moment, Wi-Fi
    // waking up after a game, LRCLIB slow). A failed look-up used to read as "No lyrics found" for the whole song.
    val lookup = rememberSongLyrics(nowPlaying)
    val state = when (lookup) {
        is com.shelf.launcher.data.LyricsRepository.Lookup.Found -> 1
        is com.shelf.launcher.data.LyricsRepository.Lookup.NotFound -> 2
        else -> 0
    }
    if (title != null) {
        if (!on) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    "Lyrics are off. " + com.shelf.launcher.data.LyricsOptIn.NOTE,
                    color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 11.sp else 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Chip("Turn on lyrics", accent = true) { com.shelf.launcher.data.LyricsOptIn.set(context, true) }
            }
        } else if (state == 1) {
            ScreensaverLyrics(nowPlaying, position, compact, known = (lookup as? com.shelf.launcher.data.LyricsRepository.Lookup.Found)?.lyrics)
        } else {
            Text(
                if (lookup is com.shelf.launcher.data.LyricsRepository.Lookup.Failed) "Couldn't reach the lyrics right now — trying again…"
                else if (state == 0) "Looking for lyrics…" else "No lyrics found for this song.",
                color = Color.White.copy(alpha = 0.6f), fontSize = if (compact) 12.sp else 14.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
