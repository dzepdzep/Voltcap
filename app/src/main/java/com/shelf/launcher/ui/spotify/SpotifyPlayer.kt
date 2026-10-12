package com.shelf.launcher.ui.spotify

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.spotify.SpPlaybackState
import com.shelf.launcher.spotify.SpotifyRepository
import com.shelf.launcher.spotify.SpotifyResult
import com.shelf.launcher.spotify.best
import com.shelf.launcher.ui.Chip
import com.shelf.launcher.ui.ChipFlow
import com.shelf.launcher.ui.PercentSlider
import com.shelf.launcher.ui.PlayPauseButton
import com.shelf.launcher.ui.RoundControl
import com.shelf.launcher.ui.SeekBar
import com.shelf.launcher.ui.blurRadiusFor
import com.shelf.launcher.ui.formatTrackTime
import com.shelf.launcher.ui.shelfClickable
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sends player commands and reports failures to [onFailure] (Premium / no
 * active device get their own inline help in the screen). After every command
 * the shared now-playing state is refreshed once so the UI doesn't wait for
 * the poller.
 */
internal class SpotifyControls(
    private val repo: SpotifyRepository,
    private val scope: CoroutineScope,
    private val onFailure: (SpotifyResult.Error, (suspend (String?) -> SpotifyResult<Unit>)?) -> Unit
) {
    fun run(retry: (suspend (String?) -> SpotifyResult<Unit>)) {
        scope.launch {
            val r = retry(null)
            if (r is SpotifyResult.Error) onFailure(r, retry) else repo.refreshNowPlaying()
        }
    }

    fun playPause(s: SpPlaybackState?) = run { d -> if (s?.playing() == true) repo.pause(d) else repo.resume(d) }
    fun next() = run { d -> repo.next(d) }
    fun previous() = run { d -> repo.previous(d) }
    fun seek(ms: Long) = run { d -> repo.seek(ms, d) }
    fun shuffle(on: Boolean) = run { d -> repo.setShuffle(on, d) }
    fun repeat(state: String) = run { d -> repo.setRepeat(state, d) }
    fun volume(percent: Int) = run { d -> repo.setVolume(percent, d) }
}

/** Playback position that ticks locally between the poller's updates (only while composed). */
@Composable
internal fun rememberSpotifyPosition(state: SpPlaybackState?): Long {
    var pos by remember { mutableLongStateOf(state?.progress_ms ?: 0L) }
    val playing = state?.playing() == true
    val active = com.shelf.launcher.ui.rememberIsActive()
    LaunchedEffect(state?.progress_ms, state?.item?.uri, playing, active) {
        pos = state?.progress_ms ?: 0L
        while (playing && active) { delay(500); pos += 500 }
    }
    return pos
}

/** The slim bar under the lists: cover, title, controls. Tap the text to open the full player. */
@Composable
internal fun SpotifyPlayerBar(
    state: SpPlaybackState?,
    controls: SpotifyControls,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state?.item ?: return
    val position = rememberSpotifyPosition(state)
    val duration = item.duration_ms ?: 0L
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).shelfClickable(RoundedCornerShape(10.dp)) { onOpen() }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.album?.images.best(128), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name ?: "Unknown track", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.artistLine(), color = ShelfTheme.textMuted, fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            RoundControl(Icons.Filled.SkipPrevious, "Previous") { controls.previous() }
            PlayPauseButton(state.playing()) { controls.playPause(state) }
            RoundControl(Icons.Filled.SkipNext, "Next") { controls.next() }
        }
        if (duration > 0) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).height(2.dp).background(ShelfTheme.accent))
            }
        }
    }
}

/**
 * The full-screen player: big art, title / artist / album, seek bar, transport,
 * shuffle / repeat and volume, over the cover blurred by [blurPercent] (0–100 %).
 * The blur is the same setting the second-screen player uses.
 */
@Composable
internal fun SpotifyFullPlayer(
    state: SpPlaybackState?,
    controls: SpotifyControls,
    blurPercent: Int,
    onBlurChange: (Int) -> Unit,
    onOpenInSpotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state?.item
    if (state == null || item == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing is playing on Spotify right now. Pick something in the Library or Search tab.",
                color = ShelfTheme.textMuted, fontSize = 14.sp, modifier = Modifier.padding(24.dp)
            )
        }
        return
    }
    val cover = item.album?.images.best(640)
    val position = rememberSpotifyPosition(state)
    var showBlur by remember { mutableStateOf(false) }
    val repeat = state.repeat_state ?: "off"
    val shuffleOn = state.shuffle_state == true
    val volume = state.device?.volume_percent

    Box(modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        if (cover != null && com.shelf.launcher.ui.AlbumArtBlur.enabled) {
            // Blur needs Android 12+; older devices get the dimmed art as-is. Same blur as every player.
            AsyncImage(
                model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(blurRadiusFor(blurPercent, 40f)).graphicsLayer { alpha = 0.6f }
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.82f)))))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(12.dp))
            Text(item.name ?: "Unknown track", color = Color.White, fontSize = 22.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(22.sp), maxFontSize = 22.sp), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.artistLine(), item.album?.name.orEmpty()).filter { it.isNotBlank() }.joinToString(" • "),
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            val duration = item.duration_ms ?: 0L
            SeekBar(position, duration, true) { controls.seek(it) }
            Row(Modifier.fillMaxWidth()) {
                Text(formatTrackTime(position), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTrackTime(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundControl(Icons.Filled.Shuffle, if (shuffleOn) "Shuffle on" else "Shuffle off") { controls.shuffle(!shuffleOn) }
                Spacer(Modifier.width(14.dp))
                RoundControl(Icons.Filled.SkipPrevious, "Previous") { controls.previous() }
                Spacer(Modifier.width(14.dp))
                PlayPauseButton(state.playing()) { controls.playPause(state) }
                Spacer(Modifier.width(14.dp))
                RoundControl(Icons.Filled.SkipNext, "Next") { controls.next() }
                Spacer(Modifier.width(14.dp))
                RoundControl(if (repeat == "track") Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat $repeat") {
                    controls.repeat(when (repeat) { "off" -> "context"; "context" -> "track"; else -> "off" })
                }
            }
            Text(
                "Shuffle ${if (shuffleOn) "on" else "off"} · Repeat ${when (repeat) { "context" -> "all"; "track" -> "one"; else -> "off" }}",
                color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundControl(Icons.Filled.VolumeDown, "Volume down") { volume?.let { controls.volume((it - 10).coerceAtLeast(0)) } }
                Text(volume?.let { "$it %" } ?: "Volume n/a", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                RoundControl(Icons.Filled.VolumeUp, "Volume up") { volume?.let { controls.volume((it + 10).coerceAtMost(100)) } }
            }
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Background blur", accent = showBlur, onClick = { showBlur = !showBlur })
                item.uri?.let { uri -> Chip("Open in Spotify", accent = false, onClick = { onOpenInSpotify(uri) }) }
            }
            if (showBlur) {
                Spacer(Modifier.height(6.dp))
                PercentSlider("Album art blur", blurPercent, onBlurChange)
            }
        }
    }
}

/** Opens [uri] in the Spotify app; shared by the screen's inline help. */
internal fun openInSpotify(context: Context, repo: SpotifyRepository, uri: String) { repo.openInSpotifyApp(context, uri) }
