package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * The music player taking over the whole second screen: big album art (or,
 * on a squarish screen, art on top), title, seek bar, controls and optional
 * lyrics, over the album art blurred as far as the user wants (0–100 %).
 * The older card layout is still there (Settings › Appearance › Second screen).
 */
@Composable
internal fun FullMusicScreen(
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
    /** Writes the blur setting; null hides the blur control. */
    onBlurChange: ((Int) -> Unit)? = null,
    /** "Visualizer" layout: this music-reactive scene moves behind the player instead of the blurred cover. */
    visualizer: com.shelf.launcher.ui.theme.ThemeMotif? = null,
    /** Visualizer layout: the album cover card over the visualizer too (Appearance › Second screen; off by default). */
    visualizerArt: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showBlurSlider by remember { mutableStateOf(false) }
    val lyricsOptIn by com.shelf.launcher.data.LyricsOptIn.enabled.collectAsState()
    com.shelf.launcher.Breadcrumbs.mark("FullMusicScreen")
    val artColor = rememberArtColor(nowPlaying.art, ShelfTheme.accent)
    val appLabel = rememberAppLabel(nowPlaying.packageName)
    val position = rememberPlaybackPosition(nowPlaying)
    val image = remember(nowPlaying.art) { nowPlaying.art?.let { runCatching { it.asImageBitmap() }.getOrNull() } }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (visualizer != null) {
            NowPlayingScene(
                MusicTheme.colors ?: ShelfTheme.colors, Modifier.fillMaxSize(),
                waveY = 0.5f, motif = visualizer, waveH = visualizer.reactiveHalf.coerceAtLeast(0.12f)
            )
        } else if (blurArt && image != null) {
            // Blur needs Android 12+; older devices get the dimmed art as-is.
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
                if (visualizer != null) Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))
                else Brush.verticalGradient(listOf(artColor.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.82f)))
            )
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            val wide = maxWidth > maxHeight * 1.15f
            // Small screens (the AYN Thor's lower screen, 3.5" handhelds): everything scales down so the
            // whole player — lyrics included — fits on screen instead of running off the bottom.
            val compact = maxHeight < 420.dp || maxWidth < 380.dp
            val boxH = maxHeight
            if (visualizer != null && (visualizer in COVER_MOTIFS || !visualizerArt)) {
                // The visualizer is the player here: no card over it. Just the song's name in the top-left corner, the
                // lyrics beside it (to its right on a wide screen, in a band along the bottom on a tall one) and three
                // small controls, so nothing sits on top of the scene.
                Box(Modifier.fillMaxSize()) {
                    // Update 144: below the chips in the top-right corner (as the tall layout does), so a long
                    // title isn't drawn under Spotify / Lyrics / Close on narrow screens.
                    Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.6f).padding(top = if (compact) 46.dp else 50.dp)) {
                        FullText(
                            nowPlaying.title ?: "Unknown track", color = Color.White, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f))
                        )
                        FullText(
                            listOfNotNull(nowPlaying.artist, nowPlaying.album).joinToString(" • ").ifEmpty { appLabel },
                            color = Color.White.copy(alpha = 0.78f), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f))
                        )
                    }
                    if (showLyrics) {
                        val lyricsMod = if (wide) Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.4f).heightIn(max = boxH * 0.56f)
                        else Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = boxH * 0.3f).padding(bottom = if (compact) 44.dp else 56.dp)
                        Box(lyricsMod.clipToBounds().voltSurface(uiRounded(14.dp), base = Color.Black.copy(alpha = 0.42f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            LyricsOrHint(nowPlaying, position, compact)
                        }
                    }
                    Row(
                        Modifier.align(if (wide && showLyrics) Alignment.BottomStart else Alignment.BottomCenter).padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // (Update 124: shuffle first while Spotify plays, a same-size gap after next so play stays centred.)
                        val shuffle = MusicShuffle.isSpotify(nowPlaying.packageName)
                        if (shuffle) {
                            ShuffleButton(nowPlaying, 44.dp, filled = false)
                            Spacer(Modifier.width(8.dp))
                        }
                        RoundControl(Icons.Filled.SkipPrevious, "Previous", onPrevious)
                        Spacer(Modifier.width(8.dp))
                        PlayPauseButton(nowPlaying.isPlaying, onPlayPause)
                        Spacer(Modifier.width(8.dp))
                        RoundControl(Icons.Filled.SkipNext, "Next", onNext)
                        if (shuffle) {
                            Spacer(Modifier.width(8.dp))
                            Spacer(Modifier.size(44.dp))
                        }
                    }
                }
            } else if (wide) {
                val artSize: Dp = maxOf(minOf(maxHeight * 0.92f, maxWidth * 0.4f), 96.dp)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(nowPlaying.art, artSize)
                    Spacer(Modifier.width(if (compact) 14.dp else 24.dp))
                    PlayerInfoColumn(
                        nowPlaying, appLabel, position, showLyrics,
                        onPlayPause, onNext, onPrevious, onSeek,
                        Modifier.weight(1f).fillMaxHeight().padding(top = 34.dp), compact
                    )
                }
            } else {
                // Squarish or tall screens (the AYN Thor's lower screen): art on top, a smaller cover when
                // lyrics need the room.
                val artShare = if (showLyrics) 0.25f else 0.40f
                val artSize: Dp = maxOf(minOf(maxWidth * 0.55f, maxHeight * artShare), 72.dp)
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Below the chips in the top-right corner.
                    Spacer(Modifier.height(if (compact) 46.dp else 50.dp))
                    AlbumArt(nowPlaying.art, artSize)
                    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                    PlayerInfoColumn(
                        nowPlaying, appLabel, position, showLyrics,
                        onPlayPause, onNext, onPrevious, onSeek,
                        Modifier.fillMaxWidth().weight(1f), compact
                    )
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            onBrowseSpotify?.let { Chip("Spotify", accent = false, onClick = it) }
            if (onBlurChange != null && blurArt) Chip("Blur", accent = showBlurSlider) { showBlurSlider = !showBlurSlider }
            Chip("Lyrics", accent = showLyrics, onClick = onToggleLyrics)
            Chip("Close", accent = false, onClick = onClose)
        }
        if (showBlurSlider && onBlurChange != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 10.dp)) {
                PercentSlider("Album art blur", blurPercent, onBlurChange)
            }
        }
        if (showLyrics && !lyricsOptIn) {
            Box(Modifier.align(Alignment.BottomStart).padding(20.dp)) { com.shelf.launcher.ui.spotify.LyricsOffNote() }
        }
    }
}

@Composable
private fun PlayerInfoColumn(
    nowPlaying: NowPlaying,
    appLabel: String,
    position: Long,
    showLyrics: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    compact: Boolean = false
) {
    com.shelf.launcher.Breadcrumbs.mark("PlayerInfoColumn")
    Column(modifier) {
        FullText(
            "PLAYING FROM ${appLabel.uppercase()}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 2
        )
        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
        FitText(
            nowPlaying.title ?: "Unknown track", Color.White,
            maxSize = if (compact) 19.sp else 24.sp, minSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold
        )
        FullText(
            listOfNotNull(nowPlaying.artist, nowPlaying.album).joinToString(" • ").ifEmpty { appLabel },
            color = Color.White.copy(alpha = 0.75f),
            fontSize = if (compact) 12.sp else 14.sp, maxLines = 2
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        SeekBar(position, nowPlaying.durationMs, nowPlaying.canSeek, onSeek)
        if (nowPlaying.durationMs > 0) {
            Row(Modifier.fillMaxWidth()) {
                Text(formatTrackTime(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTrackTime(nowPlaying.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Update 124: shuffle on the left while Spotify plays, a same-size gap on the right so play stays centred.
            val shuffle = MusicShuffle.isSpotify(nowPlaying.packageName)
            val gap = if (compact) 14.dp else 22.dp
            if (shuffle) {
                ShuffleButton(nowPlaying, 44.dp, filled = false)
                Spacer(Modifier.width(gap))
            }
            RoundControl(Icons.Filled.SkipPrevious, "Previous", onPrevious)
            Spacer(Modifier.width(gap))
            PlayPauseButton(nowPlaying.isPlaying, onPlayPause)
            Spacer(Modifier.width(gap))
            RoundControl(Icons.Filled.SkipNext, "Next", onNext)
            if (shuffle) {
                Spacer(Modifier.width(gap))
                Spacer(Modifier.size(44.dp))
            }
        }
        if (showLyrics) {
            // The lyrics take whatever height is left (and shrink / wrap into it), so they never end up
            // below the bottom of the screen.
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).clipToBounds(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { LyricsOrHint(nowPlaying, position, compact) }
        }
    }
}

/** Music themes that draw the album cover themselves: the full player then leaves its own cover out. */
internal val COVER_MOTIFS = setOf(
    com.shelf.launcher.ui.theme.ThemeMotif.PULSE_RING, com.shelf.launcher.ui.theme.ThemeMotif.FIRE_32,
    com.shelf.launcher.ui.theme.ThemeMotif.KALEIDO, com.shelf.launcher.ui.theme.ThemeMotif.SHATTER,
    com.shelf.launcher.ui.theme.ThemeMotif.VINYL, com.shelf.launcher.ui.theme.ThemeMotif.CD_SPIN,
    com.shelf.launcher.ui.theme.ThemeMotif.ARENA, com.shelf.launcher.ui.theme.ThemeMotif.ART_WALL,
    com.shelf.launcher.ui.theme.ThemeMotif.ART_TUNNEL, com.shelf.launcher.ui.theme.ThemeMotif.POP_ART,
    com.shelf.launcher.ui.theme.ThemeMotif.COVER_RIPPLE, com.shelf.launcher.ui.theme.ThemeMotif.AURA_BEAT,
    com.shelf.launcher.ui.theme.ThemeMotif.SPECIMEN_BEAT, com.shelf.launcher.ui.theme.ThemeMotif.TOWERS_128,
    com.shelf.launcher.ui.theme.ThemeMotif.CARTRIDGE_16, com.shelf.launcher.ui.theme.ThemeMotif.HANDHELD,
    com.shelf.launcher.ui.theme.ThemeMotif.PIXEL_PARADE, com.shelf.launcher.ui.theme.ThemeMotif.CAMPFIRE_BEAT,
    com.shelf.launcher.ui.theme.ThemeMotif.MANGA_BEAT, com.shelf.launcher.ui.theme.ThemeMotif.COVER_FLOW,
    com.shelf.launcher.ui.theme.ThemeMotif.RECORD_SHELF, com.shelf.launcher.ui.theme.ThemeMotif.COVER_CUBE,
    com.shelf.launcher.ui.theme.ThemeMotif.COVER_FLOAT, com.shelf.launcher.ui.theme.ThemeMotif.HOLO_COVER,
    com.shelf.launcher.ui.theme.ThemeMotif.COVER_HALO, com.shelf.launcher.ui.theme.ThemeMotif.BIG_TYPE,
    com.shelf.launcher.ui.theme.ThemeMotif.PIXEL_COVER, com.shelf.launcher.ui.theme.ThemeMotif.POLAROID,
    com.shelf.launcher.ui.theme.ThemeMotif.RETRO_TV, com.shelf.launcher.ui.theme.ThemeMotif.JUKEBOX,
    com.shelf.launcher.ui.theme.ThemeMotif.NEON_FRAME,
    com.shelf.launcher.ui.theme.ThemeMotif.MAGAZINE, com.shelf.launcher.ui.theme.ThemeMotif.COVER_FOCUS,
    com.shelf.launcher.ui.theme.ThemeMotif.DISC_SLEEVE
)
