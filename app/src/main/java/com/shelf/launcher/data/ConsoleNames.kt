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
        // (Update 306: the consoles CaseCatalog has cases for, named too — they read as their short keys.)
        "PS5" to "PlayStation 5",
        "XboxOne" to "Xbox One",
        "XboxSeries" to "Xbox Series X|S",
        "CDi" to "CD-i",
        "PSP" to "PlayStation Portable",
        "PSVita" to "PlayStation Vita",
        "GameCube" to "Nintendo GameCube",
        "Wii" to "Nintendo Wii",
        "WiiU" to "Nintendo Wii U",
        "Switch" to "Nintendo Switch",
        "NES" to "Nintendo Entertainment System",
        "SNES" to "Super Nintendo Entertainment System",
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
        "Disc" to "Disc images",
        // (Update 325, Toni: "always the official names": the keys that still showed as they are.)
        "MegaDrive" to "Sega Mega Drive / Genesis",
        "Pico8" to "PICO-8",
        "Famicom" to "Nintendo Famicom",
        "FDS" to "Famicom Disk System",
        "SFC" to "Super Famicom",
        "Switch2" to "Nintendo Switch 2",
        "SG1000" to "Sega SG-1000",
        "Atari5200" to "Atari 5200",
        "NeoGeoPocketColor" to "Neo Geo Pocket Color",
        "WonderSwanColor" to "WonderSwan Color",
        "TG16" to "TurboGrafx-16",
        "TGCD" to "TurboGrafx-CD",
        "App" to "Other apps"
    )

    /** The official name — always (printed on cases, cartridges and discs, logo look-ups, reports). */
    fun display(platform: String): String = names[platform] ?: platform

    /**
     * Update 329 (Toni: "on by default the real official names, not GBA, PS2… — those the user can turn on"): the short
     * names people use, for Settings › Home › Name & logo › Short console names. Consoles not listed use their key, which
     * already is their usual short name (PS2, GBA, N64, SNES…).
     */
    private val shortNames = mapOf(
        "GameCube" to "GameCube", "MegaDrive" to "Mega Drive", "Genesis" to "Mega Drive", "MasterSystem" to "Master System",
        "GameGear" to "Game Gear", "SegaCD" to "Sega CD", "VirtualBoy" to "Virtual Boy", "NeoGeoPocket" to "NGP",
        "NeoGeoPocketColor" to "NGPC", "PCEngine" to "PC Engine", "PCEngineCD" to "PCE CD", "WonderSwan" to "WS",
        "WonderSwanColor" to "WSC", "Atari2600" to "2600", "Atari5200" to "5200", "Atari7800" to "7800", "XboxOne" to "Xbox One",
        "XboxSeries" to "Xbox Series", "Xbox360" to "Xbox 360", "PokemonMini" to "Pokémon mini", "PSVita" to "Vita",
        "Switch2" to "Switch 2", "WiiU" to "Wii U", "ColecoVision" to "Coleco", "Intellivision" to "Intellivision",
        "NeoGeo" to "Neo Geo", "NeoGeoCD" to "Neo Geo CD", "Pico8" to "PICO-8", "PICO8" to "PICO-8", "Naomi" to "NAOMI",
        "CDi" to "CD-i", "SG1000" to "SG-1000", "C64" to "C64", "App" to "Other apps", "Android" to "Android",
        "Disc" to "Discs", "Arcade" to "Arcade", "DOS" to "DOS", "ScummVM" to "ScummVM", "Amiga" to "Amiga", "MSX" to "MSX",
        "Lynx" to "Lynx", "Jaguar" to "Jaguar", "Saturn" to "Saturn", "Dreamcast" to "Dreamcast", "Atomiswave" to "Atomiswave",
        "Vectrex" to "Vectrex", "Switch" to "Switch", "Wii" to "Wii", "3DO" to "3DO", "Xbox" to "Xbox", "TG16" to "TG-16",
        "TGCD" to "TG-CD", "Famicom" to "Famicom", "SFC" to "Super Famicom", "FDS" to "FDS"
    )

    /** The short name ("PS2", "GBA", "Mega Drive"…). */
    fun short(platform: String): String = shortNames[platform] ?: platform

    /**
     * Update 329: what the screens show for a console — its official name, or the short one with Settings › Home ›
     * Name & logo › Short console names on (off by default). Use [display] for anything printed on the game's media.
     */
    fun label(platform: String): String = if (Features.shortConsoleNames.state.value) short(platform) else display(platform)
}
