package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay

/**
 * Spotify-style player: album art, a background gradient pulled from the art's
 * colours, "playing from", a seekable progress bar and a big white play button.
 * Everything is reachable with the D-pad; on the progress bar, ◀ ▶ seek 10 s.
 */
@Composable
internal fun MusicPlayer(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** The blurred album art inside the card; off where the screen behind is already blurred art. */
    artBackdrop: Boolean = true
) {
    com.shelf.launcher.Breadcrumbs.mark("MusicPlayer")
    val artColor = rememberArtColor(nowPlaying.art, ShelfTheme.accent)
    val appLabel = rememberAppLabel(nowPlaying.packageName)
    val position = rememberPlaybackPosition(nowPlaying)

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(artColor, ShelfTheme.colors.surfaceRaised)))
    ) {
        if (artBackdrop) ArtBackdrop(nowPlaying.art, Modifier.matchParentSize(), maxBlur = 28f, scrim = 0.45f)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(nowPlaying.art, 128.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                FullText(
                    "PLAYING FROM ${appLabel.uppercase()}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                FullText(
                    nowPlaying.title ?: "Unknown track",
                    color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                FullText(
                    listOfNotNull(nowPlaying.artist, nowPlaying.album).joinToString(" • ").ifEmpty { appLabel },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp, maxLines = 2
                )
                Spacer(Modifier.height(12.dp))
                SeekBar(position, nowPlaying.durationMs, nowPlaying.canSeek, onSeek)
                if (nowPlaying.durationMs > 0) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTrackTime(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatTrackTime(nowPlaying.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundControl(Icons.Filled.SkipPrevious, "Previous", onPrevious)
                    Spacer(Modifier.width(20.dp))
                    PlayPauseButton(nowPlaying.isPlaying, onPlayPause)
                    Spacer(Modifier.width(20.dp))
                    RoundControl(Icons.Filled.SkipNext, "Next", onNext)
                }
            }
        }
    }
}

/** Current position, ticking locally between the once-a-second refreshes. */
@Composable
internal fun rememberPlaybackPosition(nowPlaying: NowPlaying): Long {
    com.shelf.launcher.Breadcrumbs.mark("rememberPlaybackPosition")
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    // Extrapolated from PlaybackState (position + speed); the tick only redraws, and runs only on screen.
    LaunchedWhileActive(nowPlaying.isPlaying, nowPlaying.positionAt) {
        if (nowPlaying.isPlaying) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
    }
    return nowPlaying.currentPosition(now)
}

@Composable
internal fun SeekBar(position: Long, duration: Long, canSeek: Boolean, onSeek: (Long) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("SeekBar")
    // Seeks show immediately instead of waiting for the next refresh, so
    // several quick ▶ presses add up instead of repeating the same jump.
    var pending by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(position / 1000) { pending = null }
    // Paused and the player ignored the seek: don't keep showing it.
    LaunchedEffect(pending) { if (pending != null) { delay(3_000); pending = null } }
    val shown = pending ?: position
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val seekable = canSeek && duration > 0
    // The tap handler outlives recompositions: always seek through the latest callback.
    val seekLatest by androidx.compose.runtime.rememberUpdatedState(onSeek)
    fun seekTo(target: Long) {
        val clamped = target.coerceIn(0L, duration)
        pending = clamped
        seekLatest(clamped)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(
                if (seekable) Modifier
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> { seekTo(shown - 10_000); true }
                            Key.DirectionRight -> { seekTo(shown + 10_000); true }
                            else -> false
                        }
                    }
                    .focusable(interactionSource = source)
                    .pointerInput(duration) {
                        detectTapGestures { offset -> seekTo((offset.x / size.width * duration).toLong()) }
                    }
                else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val barHeight = if (focused) 6.dp else 4.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f))
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(barHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(if (focused) ShelfTheme.accent else Color.White)
        )
        if (focused) {
            Box(
                Modifier
                    .offset(x = maxWidth * fraction - 7.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
internal fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("PlayPauseButton")
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White)
            .shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            tint = Color.Black,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
internal fun RoundControl(icon: ImageVector, label: String, onClick: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("RoundControl")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .shelfClickable(CircleShape) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
internal fun AlbumArt(art: Bitmap?, size: Dp) {
    com.shelf.launcher.Breadcrumbs.mark("AlbumArt")
    val image = remember(art) { art?.let { runCatching { it.asImageBitmap() }.getOrNull() } }
    if (image != null) {
        Image(
            image, null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(size * 0.4f))
        }
    }
}

/**
 * The art's overall colour, pushed to a rich but dark tone so white text
 * stays readable on top — the same trick Spotify's player uses.
 */
@Composable
internal fun rememberArtColor(art: Bitmap?, fallback: Color): Color {
    com.shelf.launcher.Breadcrumbs.mark("rememberArtColor")
    val sampled = remember(art) {
        art?.let { bitmap ->
            runCatching {
                // Scaling to a single pixel averages the whole image.
                val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(pixel, hsv)
                hsv[1] = hsv[1].coerceAtLeast(0.45f)
                hsv[2] = hsv[2].coerceIn(0.32f, 0.55f)
                Color(android.graphics.Color.HSVToColor(hsv))
            }.getOrNull()
        }
    }
    return sampled ?: fallback.copy(alpha = 0.6f)
}

@Composable
internal fun rememberAppLabel(packageName: String): String {
    com.shelf.launcher.Breadcrumbs.mark("rememberAppLabel")
    val context = LocalContext.current
    return remember(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}

/** Blur strength for a 0–100 % slider: 0 % = sharp, 100 % = [maxDp] of blur. */
internal fun blurRadiusFor(percent: Int, maxDp: Float = 64f): Dp =
    (percent.coerceIn(0, 100) / 100f * maxDp).dp

internal fun formatTrackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * The small player for the top-left corner of the home screen: album art,
 * title / artist, previous / play-pause / next, and optionally the current
 * lyric line underneath. Touch only — the full player with D-pad controls
 * lives in the R1 panel.
 */
@Composable
internal fun MiniMusicPlayer(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    showLyrics: Boolean = false,
    modifier: Modifier = Modifier,
    /** Which control the D-pad is on (MINI_* constants), or null. */
    focusedSlot: Int? = null,
    onSeek: (Long) -> Unit = {},
    /** Always this wide (narrower only when the screen really has no room), whatever the song's name. */
    width: Dp = 200.dp,
    /** Update 117: the layout it's in, for its look (PlayerSkins.kt). */
    layout: com.shelf.launcher.data.HomeLayout = MotionPrefs.layout
) {
    com.shelf.launcher.Breadcrumbs.mark("MiniMusicPlayer")
    val skin = playerSkinFor(layout)
    androidx.compose.runtime.CompositionLocalProvider(LocalPlayerInk provides skin.ink) {
        MiniPlayerBody(nowPlaying, onPlayPause, onNext, onPrevious, showLyrics, modifier, focusedSlot, onSeek, width, skin)
    }
}

@Composable
private fun MiniPlayerBody(
    nowPlaying: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, showLyrics: Boolean,
    modifier: Modifier, focusedSlot: Int?, onSeek: (Long) -> Unit, width: Dp, skin: PlayerSkin
) {
    val ink = skin.ink
    val artColor = rememberArtColor(nowPlaying.art, ShelfTheme.accent)
    val appLabel = rememberAppLabel(nowPlaying.packageName)
    // Swipe left: playlists, swipe right: lyrics, double tap: the big music popup.
    var page by remember { mutableStateOf(PlayerPage.PLAYER) }
    // A fixed size: it used to be as wide as the song's name and grew / shrank (and got squashed by the
    // sync text next to it) every time something changed.
    Box(
        modifier
            .width(width)
            .playerGestures(page) { page = it }
            .clip(skin.shape)
            .background(skin.background ?: Brush.horizontalGradient(listOf(artColor, Color.Black.copy(alpha = 0.55f))))
            .then(if (skin.border != null) Modifier.border(1.5.dp, skin.border, skin.shape) else Modifier)
            .padding(horizontal = if (skin.shape == RoundedCornerShape(50)) 8.dp else 0.dp)
    ) {
        if (skin.artBehind) ArtBackdrop(nowPlaying.art, Modifier.matchParentSize())
        Column(Modifier.padding(4.dp)) {
            if (page != PlayerPage.PLAYER) {
                Row(Modifier.widthIn(min = 170.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageBack(14.dp) { page = PlayerPage.PLAYER }
                    if (page == PlayerPage.PLAYLISTS) {
                        CompactPlaylists(height = 32.dp, textSize = 8.sp, modifier = Modifier.weight(1f)) { page = PlayerPage.PLAYER }
                    } else {
                        CompactLyrics(nowPlaying, height = 32.dp, textSize = 9.sp, modifier = Modifier.weight(1f))
                    }
                }
            } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(nowPlaying.art, 22.dp)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                FullText(
                    nowPlaying.title ?: "Unknown track",
                    color = ink,
                    fontFamily = if (skin.mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                    fontSize = 9.sp, lineHeight = androidx.compose.ui.unit.TextUnit(1.111f, androidx.compose.ui.unit.TextUnitType.Em),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                FullText(
                    nowPlaying.artist ?: appLabel,
                    color = ink.copy(alpha = 0.7f),
                    fontFamily = if (skin.mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                    fontSize = 8.sp, lineHeight = androidx.compose.ui.unit.TextUnit(1.125f, androidx.compose.ui.unit.TextUnitType.Em),
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(3.dp))
            MiniControl(Icons.Filled.SkipPrevious, "Previous", focusedSlot == MINI_PREVIOUS, onPrevious)
            Box(
                Modifier
                    .size(18.dp)
                    .focusRing(focusedSlot == MINI_PLAY, CircleShape)
                    .clip(CircleShape)
                    .background(skin.button)
                    .tapOnly(onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (nowPlaying.isPlaying) "Pause" else "Play",
                    tint = skin.buttonInk,
                    modifier = Modifier.size(11.dp)
                )
            }
            MiniControl(Icons.Filled.SkipNext, "Next", focusedSlot == MINI_NEXT, onNext)
        }
        // The bar's row is always there (empty without a length), so the player keeps its height.
        if (nowPlaying.durationMs > 0) {
            MiniSeekBar(nowPlaying, focused = focusedSlot == MINI_SEEK, onSeek = onSeek)
        } else {
            Spacer(Modifier.height(13.dp))
        }
        if (showLyrics) Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) { MiniLyricLine(nowPlaying) }
            }
        }
    }
}

@Composable
private fun MiniControl(icon: ImageVector, label: String, focused: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(18.dp)
            .focusRing(focused, CircleShape)
            .clip(CircleShape)
            .tapOnly(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = LocalPlayerInk.current, modifier = Modifier.size(12.dp))
    }
}

internal const val MINI_PREVIOUS = 0
internal const val MINI_PLAY = 1
internal const val MINI_NEXT = 2
internal const val MINI_SEEK = 3

/** Thin progress bar with times. Tap or drag to move in the song; with the
 * D-pad on it (focused), ◀ ▶ skip 10 seconds. */
@Composable
private fun MiniSeekBar(nowPlaying: NowPlaying, focused: Boolean, onSeek: (Long) -> Unit) {
    val position = rememberPlaybackPosition(nowPlaying)
    val duration = nowPlaying.durationMs
    val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
    val miniSeek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    Row(
        Modifier
            .padding(start = 2.dp, end = 2.dp, top = 3.dp)
            .widthIn(min = 120.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = LocalPlayerInk.current
        Text(formatTrackTime(position), color = ink.copy(alpha = 0.7f), fontSize = 7.sp)
        Spacer(Modifier.width(4.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(10.dp)
                .focusRing(focused, RoundedCornerShape(7.dp))
                .pointerInput(duration, nowPlaying.canSeek) {
                    if (!nowPlaying.canSeek) return@pointerInput
                    detectTapGestures { offset ->
                        miniSeek((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (focused) 3.dp else 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ink.copy(alpha = 0.25f))
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (focused) 3.dp else 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ink)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(formatTrackTime(duration), color = ink.copy(alpha = 0.7f), fontSize = 7.sp)
    }
}

/** The line of the song playing right now (synced lyrics), or the first
 * lines when only unsynced lyrics exist. Fetched from LRCLIB per song. */
@Composable
internal fun MiniLyricLine(
    nowPlaying: NowPlaying,
    /** On its own line on the home screen (not under the small player): bigger, and aligned per the lyrics layout. */
    standalone: Boolean = false
) {
    val title = nowPlaying.title ?: return
    val lyricsOn by com.shelf.launcher.data.LyricsOptIn.enabled.collectAsState()
    val lyrics by androidx.compose.runtime.produceState<com.shelf.launcher.data.Lyrics?>(null, title, nowPlaying.artist, lyricsOn) {
        // Lyrics switched off: nothing is looked up (no request to LRCLIB at all).
        value = if (!lyricsOn) null
        else com.shelf.launcher.data.LyricsRepository.fetch(title, nowPlaying.artist, nowPlaying.album, nowPlaying.durationMs)
    }
    val position = rememberPlaybackPosition(nowPlaying)
    val current = lyrics
    val line = when {
        current == null -> null
        current.isSynced -> current.lineAt(position) ?: "♪"
        else -> current.plain.firstOrNull()
    } ?: return
    androidx.compose.animation.AnimatedContent(
        targetState = line,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)))
        },
        label = "lyric"
    ) { text ->
        // Wraps to two lines and shrinks a little before it would cut the line off.
        if (standalone) FitText(
            text, Color.White.copy(alpha = 0.95f), maxSize = 13.sp, minSize = 10.sp, maxLines = 2,
            fontWeight = FontWeight.SemiBold, lineHeightRatio = 1.2f,
            textAlign = com.shelf.launcher.data.Features.lyricsAlign.state.value.text()
        ) else FitText(
            text, LocalPlayerInk.current.copy(alpha = 0.9f), maxSize = 8.sp, minSize = 6.5.sp, maxLines = 2,
            fontWeight = FontWeight.Medium, lineHeightRatio = 1.2f,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp)
        )
    }
}

/**
 * A tap that never takes focus. The corner player's buttons used clickable, which made a touched button
 * the focused element — after that the D-pad no longer reached the home screen and you were stuck there.
 */
@Composable
internal fun Modifier.tapOnly(onClick: () -> Unit): Modifier {
    val latest = androidx.compose.runtime.rememberUpdatedState(onClick)
    return this.pointerInput(Unit) { detectTapGestures { latest.value() } }
}

/** What a sideways swipe on the corner music player does (set by the home screen: open the playlists). */
internal object MusicSwipe {
    @Volatile var handler: (() -> Unit)? = null
    /** Double tap on a small player: open the big music popup (set by the home screen). */
    @Volatile var openFull: (() -> Unit)? = null
}
