package com.shelf.launcher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.AudioReactive
import com.shelf.launcher.data.BackgroundStyle
import com.shelf.launcher.data.LyricsOptIn
import com.shelf.launcher.data.MusicLyricsStyle
import com.shelf.launcher.data.MusicThemeSettings

/** Appearance › Theme, shown while "Now playing" is the theme: lyrics, real-beat listening, strength. */
@Composable
internal fun NowPlayingThemeCard(appearance: AppearanceSettings, onAppearanceChange: (AppearanceSettings) -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("NowPlayingThemeCard")
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            denied = false
            MusicThemeSettings.setRealAudio(context, true)
        } else {
            denied = true
        }
    }

    Column {
        SectionLabel("Now playing")
        MutedNote(
            "Takes its colours from the cover of whatever is playing (Spotify or any player that shows a media " +
                "notification) and moves with the music. With nothing playing it keeps the last song's colours."
        )
        if (appearance.backgroundStyle != BackgroundStyle.ANIMATED) {
            Spacer(Modifier.height(8.dp))
            MutedNote("The moving background shows when \"Behind the dock\" is set to Animated theme.")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Use it as the background") { onAppearanceChange(appearance.copy(backgroundStyle = BackgroundStyle.ANIMATED)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("On the background")
        ChipFlow {
            // Each part on its own; the player is there while the name or the buttons are, and whatever is left
            // grows to fill the room (one part alone = big).
            val f = com.shelf.launcher.data.Features
            val title = f.musicShowTitle.state.value && MusicThemeSettings.controls
            val buttons = f.musicShowControls.state.value && MusicThemeSettings.controls
            Chip("Song name", accent = title) {
                f.musicShowTitle.value = !title
                if (!title && !MusicThemeSettings.controls) { f.musicShowControls.value = false; MusicThemeSettings.setControls(context, true) }
                else if (title && !buttons) MusicThemeSettings.setControls(context, false)
            }
            Chip("Buttons & timeline", accent = buttons) {
                f.musicShowControls.value = !buttons
                if (!buttons && !MusicThemeSettings.controls) { f.musicShowTitle.value = false; MusicThemeSettings.setControls(context, true) }
                else if (buttons && !title) MusicThemeSettings.setControls(context, false)
            }
            Chip("Lyrics", accent = f.musicShowLyrics.state.value) { f.musicShowLyrics.toggle() }
            Chip("Blurred cover", accent = MusicThemeSettings.cover) { MusicThemeSettings.setCover(context, !MusicThemeSettings.cover) }
        }
        MutedNote(
            "Song name, buttons & timeline and lyrics each switch on their own. With only one of them on, it grows " +
                "to fill the room under the equalizer. The player takes the corner player's place while this background " +
                "is on. Blurred cover off: only the cover's colours."
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("Equalizer")
        ChipFlow {
            com.shelf.launcher.data.MusicSpot.values().forEach { spot ->
                Chip(spot.label, accent = MusicThemeSettings.eqSpot == spot) { MusicThemeSettings.setEqSpot(context, spot) }
            }
        }
        MutedNote(
            when (MusicThemeSettings.eqSpot) {
                com.shelf.launcher.data.MusicSpot.AUTO -> "In the free space beside the game row (above it, below it, or the roomier side), with the player under it."
                com.shelf.launcher.data.MusicSpot.TOP -> "Along the top of the screen, with the player under it."
                com.shelf.launcher.data.MusicSpot.MIDDLE -> "Across the middle of the screen, with the player under it."
                com.shelf.launcher.data.MusicSpot.BOTTOM -> "Along the bottom of the screen, with the player under it."
            }
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("Layout & size")
        ChipFlow {
            com.shelf.launcher.data.MusicPlayerLayout.values().forEach { l ->
                Chip(l.label, accent = MusicThemeSettings.playerLayout == l) { MusicThemeSettings.setPlayerLayout(context, l) }
            }
        }
        MutedNote(
            when (MusicThemeSettings.playerLayout) {
                com.shelf.launcher.data.MusicPlayerLayout.AUTO -> "Side by side on wide screens (the visualizer gets the whole height and is much bigger), " +
                    "stacked on narrow ones. Wide visualizers (spectrum, ribbons, terrain…) always stay stacked."
                com.shelf.launcher.data.MusicPlayerLayout.SIDE -> "The visualizer on the left, the player on the right - as big as the free space allows."
                com.shelf.launcher.data.MusicPlayerLayout.STACKED -> "The player under the visualizer, both centred."
            }
        )
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            listOf("Small" to 80, "Normal" to 100, "Big" to 120, "Huge" to 150).forEach { (label, v) ->
                Chip(label, accent = MusicThemeSettings.vizSize == v) { MusicThemeSettings.setVizSize(context, v) }
            }
        }
        MutedNote("How big the visualizer is drawn. It never runs under the player or the game row: when the room runs out it stops growing.")

        Spacer(Modifier.height(12.dp))
        SectionLabel("Lyrics")
        ChipFlow {
            MusicLyricsStyle.values().forEach { style ->
                Chip(style.label, accent = MusicThemeSettings.lyrics == style) { MusicThemeSettings.setLyrics(context, style) }
            }
        }
        if (MusicThemeSettings.lyrics != MusicLyricsStyle.OFF) {
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.MusicLyricsSpot.values().forEach { spot ->
                    Chip(spot.label, accent = MusicThemeSettings.lyricsSpot == spot) { MusicThemeSettings.setLyricsSpot(context, spot) }
                }
            }
        }
        val where = when (MusicThemeSettings.lyricsSpot) {
            com.shelf.launcher.data.MusicLyricsSpot.IN_PLAYER -> "In the player"
            com.shelf.launcher.data.MusicLyricsSpot.ON_WAVE -> "Centred on the equalizer wave"
            com.shelf.launcher.data.MusicLyricsSpot.ABOVE_WAVE -> "Just above the visualizer: never over the animation"
            com.shelf.launcher.data.MusicLyricsSpot.BELOW_WAVE -> "Just below the visualizer, above the player"
            com.shelf.launcher.data.MusicLyricsSpot.TOP -> "Centred at the top of the screen"
            com.shelf.launcher.data.MusicLyricsSpot.BOTTOM -> "Centred at the bottom of the screen"
        }
        MutedNote(
            when (MusicThemeSettings.lyrics) {
                MusicLyricsStyle.BIG -> "$where: the line being sung, larger, with the next one under it."
                MusicLyricsStyle.SMALL -> "$where: one smaller line."
                MusicLyricsStyle.OFF -> "No lyrics on the background."
            }
        )
        val lyricsOn by LyricsOptIn.enabled.collectAsState()
        if (MusicThemeSettings.lyrics != MusicLyricsStyle.OFF) {
            Spacer(Modifier.height(6.dp))
            LyricsLayoutSettings()
        }
        if (MusicThemeSettings.lyrics != MusicLyricsStyle.OFF && !lyricsOn) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Lyrics are switched off. " + LyricsOptIn.NOTE)
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Turn on lyrics") { LyricsOptIn.set(context, true) } }
        }

        Spacer(Modifier.height(12.dp))
        val hasMic = AudioReactive.hasPermission(context)
        SwitchRow(
            icon = Icons.Filled.GraphicEq,
            title = "React to the real beat",
            subtitle = when {
                !MusicThemeSettings.realAudio -> "Off: the effects follow a gentle simulated beat."
                !hasMic -> "Needs Android's Microphone permission."
                AudioReactive.unsupported -> "This device doesn't share its sound, so the beat is simulated."
                else -> "On: bass, mids and kicks drive the effects while music plays."
            },
            checked = MusicThemeSettings.realAudio && hasMic,
            onChange = { on ->
                if (!on) {
                    MusicThemeSettings.setRealAudio(context, false)
                } else if (hasMic) {
                    MusicThemeSettings.setRealAudio(context, true)
                } else {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
        if (MusicThemeSettings.realAudio && hasMic && !AudioReactive.unsupported) {
            // What's happening right now (checked every second while this card is open).
            val liveNow by androidx.compose.runtime.produceState(AudioReactive.live) {
                while (true) { value = AudioReactive.live; kotlinx.coroutines.delay(1000) }
            }
            Spacer(Modifier.height(4.dp))
            MutedNote(
                if (liveNow) "Right now: hearing the music ✓ - the theme moves with the real song."
                else "Right now: no sound reaches Voltcap, so a simulated beat plays. Some players (often Spotify " +
                    "with \"Hardware acceleration\" / offload) hand the sound straight to the hardware; turning that off " +
                    "in the player, or playing something, lets the theme hear it."
            )
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(
            "Android calls this the Microphone permission. Voltcap only reads the sound that is going to the " +
                "speaker, a few times a second, while this background is on screen: nothing is recorded, " +
                "saved or sent anywhere. Players that hand their audio straight to the hardware can't be heard; " +
                "the beat is simulated for them."
        )
        if (denied) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Permission not given. You can allow \"Microphone\" for Voltcap in Android's app settings.")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip("Open app settings") {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Strength")
        ChipFlow {
            listOf("Calm" to 35, "Normal" to 70, "Strong" to 100).forEach { (label, value) ->
                Chip(label, accent = MusicThemeSettings.intensity == value) { MusicThemeSettings.setIntensity(context, value) }
            }
            Chip(if (MusicThemeSettings.adaptive) "✓ Adapt to the cover" else "Adapt to the cover", accent = MusicThemeSettings.adaptive) {
                MusicThemeSettings.setAdaptive(context, !MusicThemeSettings.adaptive)
            }
        }
        MutedNote("Adapt to the cover: bright, colourful covers make the effects bolder, muted or grey ones calmer.")
    }
}
