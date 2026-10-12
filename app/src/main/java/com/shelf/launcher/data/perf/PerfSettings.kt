package com.shelf.launcher.data.perf

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the overlay chip sits on the screen. */
enum class PerfCorner(val label: String) {
    TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right")
}

/** What the performance overlay shows, and whether it appears during games. */
data class PerfConfig(
    val showDuringGames: Boolean = false,
    val fps: Boolean = true,
    val frameTime: Boolean = true,
    val cpu: Boolean = true,
    val gpu: Boolean = true,
    val ram: Boolean = false,
    val temp: Boolean = true,
    val power: Boolean = false,
    /** Time left on the battery at the current power draw. */
    val batteryLeft: Boolean = false,
    /** How long this game has been running. */
    val sessionTime: Boolean = false,
    /** Time of day. */
    val clock: Boolean = false,
    /** A warning line when the chip runs hot (80 °C+) or the battery is low (15 % and not charging). */
    val warnings: Boolean = true,
    val corner: PerfCorner = PerfCorner.TOP_LEFT,
    /** A small music player (art, title, lyric line) on top of the game while music plays. */
    val musicDuringGames: Boolean = false,
    val musicLyrics: Boolean = true,
    val musicCorner: PerfCorner = PerfCorner.TOP_RIGHT,
    /** Tap the music chip to open controls and the Spotify library (touch only; the controller stays with the game). */
    val musicInteractive: Boolean = true,
    /** ⏮ ⏯ ⏭ right on the small chip (no need to open it first). */
    val musicChipButtons: Boolean = true,
    /** RetroAchievements over the game: progress, what's left, missables to get, the next one to go for. */
    val raDuringGames: Boolean = false,
    val raCorner: PerfCorner = PerfCorner.BOTTOM_LEFT,
    /** Show the next achievement worth going for (with its description). */
    val raNext: Boolean = true,
    /** Warn about missables you can still get (by name). */
    val raMissables: Boolean = true,
    /** Achievement badges (pictures) in the overlay and its lists. */
    val raBadges: Boolean = true,
    /** A progress capsule at the top: how far you are into the set. */
    val raProgressBar: Boolean = true,
    /** The "Unlocked" tab in the open panel (newest first, with when). */
    val raUnlockedTab: Boolean = true,
    /** Points next to the progress. */
    val raPoints: Boolean = true,
    /** The last unlock of this session in the summary. */
    val raLastUnlock: Boolean = true,
    /** Voltcap look (your accent, glass panel, capsule bar); off = the plain dark box. */
    val raThemed: Boolean = true,
    /** Overlays can be dragged to another corner with a finger (and swap places with what's there). */
    val overlaysMovable: Boolean = true,
    /** Size of every overlay over games, 50–200 % (Settings › Overlay, R1 › Overlay, the overlay notification). */
    val overlayScale: Int = 100,
    /** How solid the overlays are, 20–100 % (lower = more see-through). */
    val overlayOpacity: Int = 100
) {
    /**
     * Where each overlay really goes: alone, it sits top left; together, each keeps its own corner and
     * two that want the same one are spread over the free corners (stats, music, achievements order).
     */
    fun effectiveCorners(
        /** Whether the music player is really on screen now (music playing); a hidden player frees its corner. */
        musicVisible: Boolean = musicDuringGames
    ): Triple<PerfCorner, PerfCorner, PerfCorner> {
        val on = listOf(showDuringGames, musicDuringGames && musicVisible, raDuringGames)
        val wanted = listOf(corner, musicCorner, raCorner)
        // Alone on screen, an overlay simply takes the corner picked for it (it used to be forced top left,
        // which kept the achievements overlay on the left even with the right corner selected).
        if (on.count { it } <= 1) return Triple(corner, musicCorner, raCorner)
        val result = wanted.toMutableList()
        val used = mutableSetOf<PerfCorner>()
        for (i in 0..2) {
            if (!on[i]) continue
            if (result[i] in used) result[i] = PerfCorner.values().firstOrNull { it !in used && it !in wanted.filterIndexed { j, _ -> on[j] && j > i } }
                ?: PerfCorner.values().first { it !in used }
            used += result[i]
        }
        return Triple(result[0], result[1], result[2])
    }
}

/**
 * Prefs "perf_settings" (DEVICE scope: sensor paths and overlay position only make
 * sense on the handheld they were set on). [state] lets the overlay service and the
 * settings card follow changes live.
 */
object PerfSettings {
    private const val FILE = "perf_settings"
    private val _state = MutableStateFlow(PerfConfig())
    val state: StateFlow<PerfConfig> = _state.asStateFlow()
    @Volatile private var loaded = false

    fun config(context: Context): PerfConfig {
        ensureLoaded(context)
        return _state.value
    }

    fun update(context: Context, change: (PerfConfig) -> PerfConfig) {
        ensureLoaded(context)
        val next = synchronized(this) { change(_state.value).also { _state.value = it } }
        runCatching {
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean("show_during_games", next.showDuringGames)
                .putBoolean("fps", next.fps).putBoolean("frame_time", next.frameTime)
                .putBoolean("cpu", next.cpu).putBoolean("gpu", next.gpu)
                .putBoolean("ram", next.ram).putBoolean("temp", next.temp).putBoolean("power", next.power)
                .putBoolean("battery_left", next.batteryLeft).putBoolean("session_time", next.sessionTime)
                .putBoolean("overlay_clock", next.clock).putBoolean("overlay_warnings", next.warnings)
                .putString("corner", next.corner.name)
                .putBoolean("music_during_games", next.musicDuringGames)
                .putBoolean("music_lyrics", next.musicLyrics)
                .putString("music_corner", next.musicCorner.name)
                .putBoolean("music_interactive", next.musicInteractive)
                .putBoolean("music_chip_buttons", next.musicChipButtons)
                .putBoolean("ra_during_games", next.raDuringGames)
                .putString("ra_corner", next.raCorner.name)
                .putBoolean("ra_next", next.raNext)
                .putBoolean("ra_missables", next.raMissables)
                .putBoolean("ra_badges", next.raBadges)
                .putBoolean("ra_progress_bar", next.raProgressBar)
                .putBoolean("ra_unlocked_tab", next.raUnlockedTab)
                .putBoolean("ra_points", next.raPoints)
                .putBoolean("ra_last_unlock", next.raLastUnlock)
                .putBoolean("ra_themed", next.raThemed)
                .putBoolean("overlays_movable", next.overlaysMovable)
                .putInt("overlay_scale", next.overlayScale.coerceIn(50, 200))
                .putInt("overlay_opacity", next.overlayOpacity.coerceIn(20, 100))
                .apply()
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                val d = PerfConfig()
                _state.value = PerfConfig(
                    showDuringGames = p.getBoolean("show_during_games", d.showDuringGames),
                    fps = p.getBoolean("fps", d.fps), frameTime = p.getBoolean("frame_time", d.frameTime),
                    cpu = p.getBoolean("cpu", d.cpu), gpu = p.getBoolean("gpu", d.gpu),
                    ram = p.getBoolean("ram", d.ram), temp = p.getBoolean("temp", d.temp),
                    power = p.getBoolean("power", d.power),
                    batteryLeft = p.getBoolean("battery_left", d.batteryLeft),
                    sessionTime = p.getBoolean("session_time", d.sessionTime),
                    clock = p.getBoolean("overlay_clock", d.clock),
                    warnings = p.getBoolean("overlay_warnings", d.warnings),
                    corner = runCatching { PerfCorner.valueOf(p.getString("corner", null).orEmpty()) }.getOrDefault(d.corner),
                    musicDuringGames = p.getBoolean("music_during_games", d.musicDuringGames),
                    musicLyrics = p.getBoolean("music_lyrics", d.musicLyrics),
                    musicCorner = runCatching { PerfCorner.valueOf(p.getString("music_corner", null).orEmpty()) }.getOrDefault(d.musicCorner),
                    musicInteractive = p.getBoolean("music_interactive", d.musicInteractive),
                    musicChipButtons = p.getBoolean("music_chip_buttons", d.musicChipButtons),
                    raDuringGames = p.getBoolean("ra_during_games", d.raDuringGames),
                    raCorner = runCatching { PerfCorner.valueOf(p.getString("ra_corner", null).orEmpty()) }.getOrDefault(d.raCorner),
                    raNext = p.getBoolean("ra_next", d.raNext),
                    raMissables = p.getBoolean("ra_missables", d.raMissables),
                    raBadges = p.getBoolean("ra_badges", d.raBadges),
                    raProgressBar = p.getBoolean("ra_progress_bar", d.raProgressBar),
                    raUnlockedTab = p.getBoolean("ra_unlocked_tab", d.raUnlockedTab),
                    raPoints = p.getBoolean("ra_points", d.raPoints),
                    raLastUnlock = p.getBoolean("ra_last_unlock", d.raLastUnlock),
                    raThemed = p.getBoolean("ra_themed", d.raThemed),
                    overlaysMovable = p.getBoolean("overlays_movable", d.overlaysMovable),
                    overlayScale = p.getInt("overlay_scale", d.overlayScale).coerceIn(50, 200),
                    overlayOpacity = p.getInt("overlay_opacity", d.overlayOpacity).coerceIn(20, 100)
                )
            }
            loaded = true
        }
    }
}
