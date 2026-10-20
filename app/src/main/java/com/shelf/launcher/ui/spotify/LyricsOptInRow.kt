package com.shelf.launcher.ui.spotify

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.LyricsOptIn
import com.shelf.launcher.ui.MutedNote
import com.shelf.launcher.ui.SwitchRow

/** The master switch for lyrics (off until turned on), with the note about where they come from. */
@Composable
internal fun LyricsOptInRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val on by LyricsOptIn.enabled.collectAsState()
    SwitchRow(
        icon = Icons.Filled.MusicNote,
        title = "Fetch lyrics online",
        subtitle = LyricsOptIn.NOTE + " Song title and artist are sent to lrclib.net. Off by default.",
        checked = on,
        onChange = { LyricsOptIn.set(context, it) },
        modifier = modifier
    )
}

/** Shown where lyrics would appear when the master switch is off. */
@Composable
internal fun LyricsOffNote() {
    MutedNote("Lyrics are off. Turn on \"Fetch lyrics online\" in Settings › Music & sound. " + LyricsOptIn.NOTE)
    Spacer(Modifier.height(4.dp))
}
