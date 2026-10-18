package com.shelf.launcher.data

import android.content.Context

/**
 * @param idleMinutes minutes without input before the screensaver starts.
 * @param blackoutMinutes further minutes before it fades to pure black
 *   (OLED burn-in protection and battery); 0 = never.
 * @param showMusic show the playing track instead of the slideshow.
 */
/** What the second screen does while the screensaver runs on the main one. */
enum class SecondScreenSaver(val label: String) {
    /** Music if something is playing, else a slideshow of recent games. */
    RECOMMENDED("Recommended"),
    SAME("Same as main screen"),
    KEEP("Keep normal view"),
    BLACK("Black"),
    ART_SLIDESHOW("Art slideshow"),
    CLOCK("Clock"),
    MUSIC("Music (else art)");

    companion object {
        /** Unknown or missing stored values fall back to the recommended mode. */
        fun parse(name: String?): SecondScreenSaver =
            values().firstOrNull { it.name == name } ?: BLACK
    }
}

data class ScreensaverSettings(
    val enabled: Boolean = true,
    val idleMinutes: Int = 5,
    val blackoutMinutes: Int = 10,
    val showMusic: Boolean = true,
    /** With the music screen: the song's lyrics, line by line. */
    val showLyrics: Boolean = true,
    /** What a second screen shows while the screensaver runs. */
    /** Off (black) by default (update 42): two screensavers at once showed two different games. */
    val secondScreen: SecondScreenSaver = SecondScreenSaver.BLACK,
    /** Without music: your library's art (slideshow) or an animated theme. */
    val style: SaverStyle = SaverStyle.SLIDESHOW,
    /** The animated theme it shows (AppTheme name); null = the theme you're using. */
    val theme: String? = null,
    /** With music: the song card, or a music theme playing along full screen. */
    val musicStyle: SaverMusicStyle = SaverMusicStyle.CARD,
    /** The music theme for that (AppTheme name); null = "Now playing". */
    val musicTheme: String? = null,
    /** While music plays the screensaver stays: no fade to black and the screen doesn't switch off. */
    val stayWithMusic: Boolean = true,
    /** (Update 238, #87: which games' heroes the slideshow shows — every hero, or only the animated ones.) */
    val heroes: SaverHeroes = SaverHeroes.ALL
)

enum class SaverStyle(val label: String) { SLIDESHOW("Library art"), THEME("A theme or visualizer") }
enum class SaverMusicStyle(val label: String) { CARD("Song card"), THEME("Music theme") }
// (Update 238, #87: the slideshow's heroes — all of them (as before), or only games with an animated hero.)
enum class SaverHeroes(val label: String) { ALL("All heroes"), ANIMATED("Only animated heroes") }

class ScreensaverStore(context: Context) {
    private val prefs = context.getSharedPreferences("screensaver", Context.MODE_PRIVATE)

    fun load() = ScreensaverSettings(
        enabled = prefs.getBoolean("enabled", true),
        idleMinutes = prefs.getInt("idle_minutes", 5),
        blackoutMinutes = prefs.getInt("blackout_minutes", 10),
        showMusic = prefs.getBoolean("show_music", true),
        showLyrics = prefs.getBoolean("show_lyrics", true),
        // New key in update 42: everyone starts from "Black" once (it was the recommended mix before).
        secondScreen = SecondScreenSaver.parse(prefs.getString("second_screen_v42", null)),
        style = runCatching { SaverStyle.valueOf(prefs.getString("style", null) ?: "") }.getOrDefault(SaverStyle.SLIDESHOW),
        theme = prefs.getString("theme", null),
        musicStyle = runCatching { SaverMusicStyle.valueOf(prefs.getString("music_style", null) ?: "") }.getOrDefault(SaverMusicStyle.CARD),
        musicTheme = prefs.getString("music_theme", null),
        stayWithMusic = prefs.getBoolean("stay_with_music", true),
        // (Update 238, #87)
        heroes = runCatching { SaverHeroes.valueOf(prefs.getString("heroes", null) ?: "") }.getOrDefault(SaverHeroes.ALL)
    )

    fun save(s: ScreensaverSettings) = prefs.edit()
        .putBoolean("enabled", s.enabled)
        .putInt("idle_minutes", s.idleMinutes)
        .putInt("blackout_minutes", s.blackoutMinutes)
        .putBoolean("show_music", s.showMusic)
        .putBoolean("show_lyrics", s.showLyrics)
        .putString("second_screen_v42", s.secondScreen.name)
        .putString("style", s.style.name)
        .putString("theme", s.theme)
        .putString("music_style", s.musicStyle.name)
        .putString("music_theme", s.musicTheme)
        .putBoolean("stay_with_music", s.stayWithMusic)
        .putString("heroes", s.heroes.name) // (Update 238, #87)
        .apply()
}
