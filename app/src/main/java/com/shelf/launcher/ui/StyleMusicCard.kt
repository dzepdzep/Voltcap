package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/**
 * The music player built into the style homes (Dashboard, Live tiles): the same player as the corner one, sized
 * to its slot, shown only while something plays and only if Features.styleMusic is on.
 */
@Composable
internal fun StyleMusicCard(c: StyleCtx, width: Dp, modifier: Modifier = Modifier, focusedSlot: Int? = null) {
    val playing = c.state.nowPlaying
    if (!Features.styleMusic.state.value || playing == null) return
    Box(modifier) {
        MiniMusicPlayer(
            nowPlaying = playing,
            onPlayPause = c.actions.mediaPlayPause,
            onNext = c.actions.mediaNext,
            onPrevious = c.actions.mediaPrevious,
            onSeek = c.actions.mediaSeek,
            showLyrics = c.state.appearance.miniPlayerLyrics,
            width = width,
            focusedSlot = focusedSlot
        )
    }
}

/** The card's ⏮ ⏯ ⏭ for the D-pad (update 101): added after a layout's bottom-bar buttons when the card shows. */
internal fun styleMusicActions(c: StyleCtx): List<() -> Unit> =
    listOf(c.actions.mediaPrevious, c.actions.mediaPlayPause, c.actions.mediaNext)

/** The music card's control the D-pad is on, when the bar's buttons before it are [before] many (else null). */
internal fun styleMusicFocus(before: Int): Int? =
    if (StyleNav.zone == StyleNav.Zone.BAR && StyleNav.index >= before) (StyleNav.index - before).coerceIn(0, 2) else null

/** True when the card would show (so a layout can make room for it). */
@Composable
internal fun styleMusicShown(c: StyleCtx): Boolean = Features.styleMusic.state.value && c.state.nowPlaying != null
