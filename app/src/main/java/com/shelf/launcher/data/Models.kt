package com.shelf.launcher.data

enum class SourceType { ROM, APP, STEAM }

/** A single entry in the library — a scanned ROM file, an installed app, or
 * a Steam game handed off to GameNative. `hue` drives the placeholder
 * gradient until real art has loaded. `sgdbGameId` is an optional manual
 * SteamGridDB game ID (the number at the end of a steamgriddb.com/game/<id>
 * URL) that skips the fuzzy name search and fetches art for that exact
 * entry — more reliable than auto-matching, same idea as Vapor Launcher's
 * manual ID linking. `steamAppId` is the numeric Steam AppID (from the
 * store page URL) for a STEAM entry — used both to hand the launch off to
 * GameNative and, doubling as a shortcut, to fetch exact art from
 * SteamGridDB's Steam-AppID lookup without needing a manual SGDB ID too. */
data class Game(
    val name: String,
    val platform: String,
    val hue: Int,
    val sourceType: SourceType,
    val romPath: String? = null,
    val packageName: String? = null,
    val sgdbGameId: Int? = null,
    val steamAppId: Int? = null,
    val raGameId: Int? = null
) {
    /** Stable identity for persisting per-game overrides across rescans —
     * the display name alone isn't a safe key since two different ROMs or
     * apps could share a label. */
    val stableKey: String
        get() = romPath ?: packageName ?: steamAppId?.let { "steam:$it" } ?: name
}

/** Every candidate asset SteamGridDB has for a game, for the artwork picker. */
data class ArtCandidates(
    val grids: List<String> = emptyList(),
    val wideGrids: List<String> = emptyList(),
    val heroes: List<String> = emptyList(),
    val logos: List<String> = emptyList(),
    /** The same pictures split up by where they came from ("SteamGridDB",
     * "IGDB"…), for the source filter in Artwork & details. */
    val sources: Map<String, ArtCandidates> = emptyMap(),
    /** Pictures known to be animated (SteamGridDB's animated list), for the picker's "Animated only"
     * filter and its ▶ tag. Others are judged by their file type ([AnimatedArt.looksAnimated]). */
    val animated: Set<String> = emptySet()
) {
    fun isAnimated(url: String): Boolean = url in animated || AnimatedArt.looksAnimated(url)
}

/** Candidates made from one scraped set (ScreenScraper, TheGamesDB). */
fun GameArt.asCandidates(): ArtCandidates = ArtCandidates(
    grids = listOfNotNull(gridUrl),
    wideGrids = listOfNotNull(wideGridUrl),
    heroes = listOfNotNull(heroUrl),
    logos = listOfNotNull(logoUrl)
)

/** Art fetched for one game from SteamGridDB. Dimensions per SteamGridDB's
 * own conventions: grid 600x900 (portrait cover, used for the dock's
 * unfocused covers), wideGrid ~920x430 (the "capsule" used for the focused
 * banner instead of stretching the portrait cover), hero ~3840x1240 (the
 * full-bleed background), logo ~512x512 canvas (transparent title logo,
 * shown in place of plain text where available). Any of these can be null
 * if SteamGridDB has no matching asset for that type. */
data class GameArt(
    val gridUrl: String?,
    val wideGridUrl: String? = null,
    val heroUrl: String?,
    val logoUrl: String? = null,
    /** SteamGridDB's first animated hero, kept beside the still one for the "Animated hero → Hero →
     * Screenshot → Cover" artwork priority ([ArtPick]). Null when the game has none (or older caches). */
    val animatedHeroUrl: String? = null
)

val sampleLibrary = listOf(
    Game("Ember Trail", "SNES", 22, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/SNES/ember_trail.sfc"),
    Game("Deep Signal", "PS1", 190, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/PS1/deep_signal.bin"),
    Game("Circuit Break", "Arcade", 280, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/Arcade/circuit_break.zip"),
    Game("Hollow Reactor", "GBA", 140, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/GBA/hollow_reactor.gba"),
    Game("Skybound Rally", "N64", 10, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/N64/skybound_rally.z64"),
    Game("Paper Fortress", "NES", 45, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/NES/paper_fortress.nes"),
    Game("Nightshade Run", "SNES", 320, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/SNES/nightshade_run.sfc"),
    Game("Vector Keep", "PS1", 200, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/PS1/vector_keep.bin"),
    Game("Neon Fold", "Arcade", 300, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/Arcade/neon_fold.zip"),
    Game("Tin Soldier", "NES", 250, SourceType.ROM, romPath = "/storage/emulated/0/ROMs/NES/tin_soldier.nes"),
)
