package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How the "Now playing" theme shows lyrics on the home background. */
enum class MusicLyricsStyle(val label: String) {
    BIG("Big"),
    SMALL("Small"),
    OFF("Off")
}

/** Where the "Now playing" equalizer wave (with the player right under it) sits on the home screen. */
enum class MusicSpot(val label: String) {
    AUTO("Beside the row"),
    TOP("Top"),
    MIDDLE("Middle"),
    BOTTOM("Bottom")
}

/** How the visualizer and the player share the room on the home screen. */
enum class MusicPlayerLayout(val label: String) {
    /** Side by side on wide screens (the visualizer gets the whole height), stacked on narrow ones. */
    AUTO("Automatic"),
    /** The visualizer on the left, the player beside it on the right: the visualizer is much bigger. */
    SIDE("Side by side"),
    /** The player under the visualizer, both in the middle (as before). */
    STACKED("Stacked")
}

/** Where the "Now playing" lyrics go. */
enum class MusicLyricsSpot(val label: String) {
    IN_PLAYER("In the player"),
    ON_WAVE("On the wave"),
    /** Right above the visualizer (the animation), never on top of it. */
    ABOVE_WAVE("Above the visualizer"),
    /** Right below the visualizer, between it and the player. */
    BELOW_WAVE("Below the visualizer"),
    TOP("Top"),
    BOTTOM("Bottom")
}

/**
 * Settings of the music-reactive "Now playing" theme (Appearance › Theme). Kept on their own,
 * like [LyricsOptIn], and read as Compose state so the scene updates the moment they change.
 * [init] runs once at start (LauncherScreen); until then the defaults apply.
 */
object MusicThemeSettings {
    private const val PREFS = "music_theme"

    var lyrics by mutableStateOf(MusicLyricsStyle.BIG)
        private set

    /**
     * The person agreed to let the theme listen to what's playing (Android's audio Visualizer,
     * which needs the "Microphone" permission). Off by default: the effects are then simulated.
     */
    var realAudio by mutableStateOf(false)
        private set

    /** The song's cover, very blurred, behind the wave. Off: only its colours (a soft swirl). */
    var cover by mutableStateOf(true)
        private set

    /** The small player on the background: song, ⏮ ⏯ ⏭ and a timeline to tap / drag through the song. */
    var controls by mutableStateOf(true)
        private set

    /** Where the equalizer wave and its player sit. */
    var eqSpot by mutableStateOf(MusicSpot.AUTO)
        private set

    /** Where the lyrics go (in the player, on the wave, or at the top / bottom of the screen). */
    var lyricsSpot by mutableStateOf(MusicLyricsSpot.IN_PLAYER)
        private set

    /** Side by side / stacked (Appearance › Music player). */
    var playerLayout by mutableStateOf(MusicPlayerLayout.AUTO)
        private set

    /** 60-160 %: how big the visualizer is drawn (it still keeps clear of the player and the row). */
    var vizSize by mutableIntStateOf(120)
        private set

    /** Colourful covers react more strongly, muted ones more calmly. */
    var adaptive by mutableStateOf(true)
        private set

    fun setAdaptive(context: Context, on: Boolean) {
        adaptive = on
        prefs(context).edit().putBoolean("adaptive", on).apply()
    }

    /** 0-100: how strongly the effects react. */
    var intensity by mutableIntStateOf(70)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lyrics = runCatching { MusicLyricsStyle.valueOf(p.getString("lyrics", null) ?: "") }.getOrDefault(MusicLyricsStyle.BIG)
        realAudio = p.getBoolean("real_audio", false)
        intensity = p.getInt("intensity", 70).coerceIn(0, 100)
        cover = p.getBoolean("cover", true)
        controls = p.getBoolean("controls", true)
        eqSpot = runCatching { MusicSpot.valueOf(p.getString("eq_spot", null) ?: "") }.getOrDefault(MusicSpot.AUTO)
        lyricsSpot = runCatching { MusicLyricsSpot.valueOf(p.getString("lyrics_spot", null) ?: "") }.getOrDefault(MusicLyricsSpot.IN_PLAYER)
        playerLayout = runCatching { MusicPlayerLayout.valueOf(p.getString("player_layout", null) ?: "") }.getOrDefault(MusicPlayerLayout.AUTO)
        vizSize = p.getInt("viz_size", 120).coerceIn(60, 160)
        adaptive = p.getBoolean("adaptive", true)
    }

    fun setPlayerLayout(context: Context, layout: MusicPlayerLayout) {
        playerLayout = layout
        prefs(context).edit().putString("player_layout", layout.name).apply()
    }

    fun setVizSize(context: Context, percent: Int) {
        vizSize = percent.coerceIn(60, 160)
        prefs(context).edit().putInt("viz_size", vizSize).apply()
    }

    fun setEqSpot(context: Context, spot: MusicSpot) {
        eqSpot = spot
        prefs(context).edit().putString("eq_spot", spot.name).apply()
    }

    fun setLyricsSpot(context: Context, spot: MusicLyricsSpot) {
        lyricsSpot = spot
        prefs(context).edit().putString("lyrics_spot", spot.name).apply()
    }

    fun setCover(context: Context, on: Boolean) {
        cover = on
        prefs(context).edit().putBoolean("cover", on).apply()
    }

    fun setControls(context: Context, on: Boolean) {
        controls = on
        prefs(context).edit().putBoolean("controls", on).apply()
    }

    fun setLyrics(context: Context, style: MusicLyricsStyle) {
        lyrics = style
        prefs(context).edit().putString("lyrics", style.name).apply()
    }

    fun setRealAudio(context: Context, on: Boolean) {
        realAudio = on
        prefs(context).edit().putBoolean("real_audio", on).apply()
    }

    fun setIntensity(context: Context, value: Int) {
        intensity = value.coerceIn(0, 100)
        prefs(context).edit().putInt("intensity", intensity).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
