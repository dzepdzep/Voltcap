package com.shelf.launcher.ui

import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.spotify.SpotifyConnection
import com.shelf.launcher.spotify.SpotifyRepository
import com.shelf.launcher.spotify.SpotifyResult
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Update 124: shuffle on the big music players (the big player popup, the second screen's music page and the in-game
 * music panel) while Spotify is playing. With Spotify connected (Settings › Accounts) it goes through the Spotify Web
 * API, which also says whether shuffle is on; without it, Spotify's own shuffle action in its media session is used
 * when it offers one (no on / off state then).
 */
internal object MusicShuffle {
    const val SPOTIFY = "com.spotify.music"

    fun isSpotify(packageName: String?): Boolean = packageName == SPOTIFY

    /** Spotify's own shuffle action in its media session (the one car screens show), if it offers one right now. */
    fun sessionAction(context: Context): Pair<MediaController, PlaybackState.CustomAction>? {
        val c = runCatching { com.shelf.launcher.data.ShelfNotificationListener.nowPlaying(context)?.second }.getOrNull() ?: return null
        if (c.packageName != SPOTIFY) return null
        val action = runCatching {
            c.playbackState?.customActions?.firstOrNull {
                it.action.contains("shuffle", ignoreCase = true) || it.name?.toString()?.contains("shuffle", ignoreCase = true) == true
            }
        }.getOrNull() ?: return null
        return c to action
    }

    /**
     * Turns shuffle to [want] (null: the other way round from what Spotify says now). Returns the new state (null when
     * it isn't known) and an error to show (null when it worked).
     */
    suspend fun toggle(context: Context, want: Boolean?): Pair<Boolean?, String?> {
        val repo = SpotifyRepository(context.applicationContext)
        if (repo.connection.value == SpotifyConnection.CONNECTED) {
            val target = want ?: run {
                runCatching { repo.refreshNowPlaying() }
                repo.nowPlaying.value?.shuffle_state != true
            }
            return when (val r = repo.setShuffle(target)) {
                is SpotifyResult.Ok -> target to null
                is SpotifyResult.Error -> null to r.message
            }
        }
        val s = sessionAction(context)
        if (s != null && runCatching { s.first.transportControls.sendCustomAction(s.second, null) }.isSuccess) return null to null
        return null to "Connect Spotify in Settings › Accounts to use shuffle here."
    }
}

/**
 * The shuffle button of a big player: shown only while Spotify is the app playing. Lit in the accent colour while
 * shuffle is on (when that's known). [filled]: a soft round backing like the big player's other buttons.
 */
@Composable
internal fun ShuffleButton(nowPlaying: NowPlaying, size: Dp, filled: Boolean) {
    if (!MusicShuffle.isSpotify(nowPlaying.packageName)) return
    val context = LocalContext.current
    val repo = remember { SpotifyRepository(context.applicationContext) }
    val connection by repo.connection.collectAsState()
    val connected = connection == SpotifyConnection.CONNECTED
    // Keeps Spotify's player state fresh while the button is on screen (the shared poller, reference-counted).
    DisposableEffect(connected) {
        if (connected) repo.startNowPlaying()
        onDispose { if (connected) repo.stopNowPlaying() }
    }
    val spotifyState by repo.nowPlaying.collectAsState()
    val reported = spotifyState?.shuffle_state
    // What you just tapped shows at once and holds until Spotify reports the same (or a few seconds pass).
    var picked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(reported, picked) {
        val p = picked ?: return@LaunchedEffect
        if (reported == p) picked = null else { delay(4_000L); picked = null }
    }
    val on = connected && (picked ?: reported) == true
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(
                when {
                    on -> ShelfTheme.accent.copy(alpha = 0.85f)
                    filled -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .shelfClickable(CircleShape) {
                val want = if (connected) !on else null
                if (want != null) picked = want
                scope.launch {
                    val (_, error) = MusicShuffle.toggle(context, want)
                    if (error != null) {
                        picked = null
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Shuffle, if (on) "Shuffle on" else "Shuffle off", tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}
