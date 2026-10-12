package com.shelf.launcher.data

/**
 * Full console names for the category row and screen: the scanner's short
 * keys ("PS1", "GC", …) as people know them ("PlayStation", "Nintendo
 * GameCube"). Anything not listed shows as it is.
 */
object ConsoleNames {
    private val names = mapOf(
        "PS1" to "PlayStation",
        "PS2" to "PlayStation 2",
        "PS3" to "PlayStation 3",
        "PS4" to "PlayStation 4",
        "PSP" to "PlayStation Portable",
        "PSVita" to "PlayStation Vita",
        "GameCube" to "Nintendo GameCube",
        "Wii" to "Nintendo Wii",
        "WiiU" to "Nintendo Wii U",
        "Switch" to "Nintendo Switch",
        "NES" to "Nintendo Entertainment System",
        "SNES" to "Super Nintendo",
        "N64" to "Nintendo 64",
        "GB" to "Game Boy",
        "GBC" to "Game Boy Color",
        "GBA" to "Game Boy Advance",
        "NDS" to "Nintendo DS",
        "3DS" to "Nintendo 3DS",
        "VirtualBoy" to "Virtual Boy",
        "Genesis" to "Sega Mega Drive / Genesis",
        "MasterSystem" to "Sega Master System",
        "SegaCD" to "Sega CD",
        "Saturn" to "Sega Saturn",
        "Dreamcast" to "Sega Dreamcast",
        "GameGear" to "Sega Game Gear",
        "PCEngine" to "PC Engine / TurboGrafx-16",
        "NeoGeo" to "Neo Geo",
        "NeoGeoPocket" to "Neo Geo Pocket",
        "WonderSwan" to "WonderSwan",
        "Atari2600" to "Atari 2600",
        "Arcade" to "Arcade",
        "32X" to "Sega 32X",
        "Naomi" to "Sega NAOMI",
        "Atomiswave" to "Atomiswave",
        "NeoGeoCD" to "Neo Geo CD",
        "PCEngineCD" to "PC Engine CD",
        "Atari7800" to "Atari 7800",
        "Lynx" to "Atari Lynx",
        "Jaguar" to "Atari Jaguar",
        "3DO" to "3DO",
        "Xbox" to "Xbox",
        "Xbox360" to "Xbox 360",
        "DOS" to "DOS",
        "ScummVM" to "ScummVM",
        "MSX" to "MSX",
        "C64" to "Commodore 64",
        "Amiga" to "Amiga",
        "PICO8" to "PICO-8",
        "PokemonMini" to "Pokémon mini",
        "ColecoVision" to "ColecoVision",
        "Intellivision" to "Intellivision",
        "Vectrex" to "Vectrex",
        "Android" to "Android",
        "Disc" to "Disc images"
    )

    fun display(platform: String): String = names[platform] ?: platform
}
