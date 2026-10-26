package com.shelf.launcher.data

/**
 * Update 330: Voltcap's own console pictures — 74 curated transparent pictures of the hardware (Toni's
 * VoltCap_Good_Only_74 set: 1200 × 800 canvases, shipped as WebP in assets/console_hw/, named by ES-DE system). They win
 * over the Wikipedia photo (ConsolePhotos) wherever a console's picture is shown: category / platform cards with "Photo of
 * the console", the Console stack, the Save Vault's console headers.
 *
 * Matched by the game's platform: Voltcap's own keys ("PS1", "Genesis"…) through [DEFAULT], any other platform by its ES-DE
 * system name (a folder Voltcap keeps as it is — "ps5", "n64dd", "adam"…). A console with more than one model has its
 * [VARIANTS]; the one shown is your pick (Settings › Categories › Console pictures), else the first — the closed DS and
 * 3DS, the Steam Machine for Steam. Drawn whole (ContentScale.Fit, never cropped) with room round it.
 */
object ConsoleArt {
    /** The pictures shipped (assets/console_hw/<name>.webp). */
    val FILES: Set<String> = setOf(
        "3do", "3ds_open", "adam", "amiga", "amiga1200", "android", "arduboy", "astrocde", "atari2600", "atari5200", "atari7800",
        "atarijaguar", "atarijaguarcd", "atarilynx", "c64", "colecovision", "dreamcast", "ds_open", "famicom", "gamegear", "gb", "gba",
        "gba_sp", "gbc", "gc", "genesis", "intellivision", "mark3", "mastersystem", "megadrivejp", "n3ds", "n64", "n64dd", "nds", "neogeo",
        "neogeocd", "nes", "ngage", "ngp", "ngpc", "pcengine", "ps2", "ps2_slim", "ps3", "ps3_slim", "ps3_superslim", "ps4", "ps5", "psp",
        "psvita", "psx", "saturn", "saturnjp", "sega32x", "segacd", "snes", "snesna", "steam", "switch", "switch_lite", "switch_oled",
        "tg16", "vectrex", "virtualboy", "wii", "wiiu", "wonderswancolor", "xbox", "xbox360", "xbox360_slim", "xboxone", "xboxone_x",
        "xboxseries_s", "xboxseries_x"
    )

    /** Voltcap platform key → its pictures, the default first. */
    val VARIANTS: Map<String, List<String>> = mapOf(
        "NES" to listOf("nes", "famicom"), "SNES" to listOf("snes", "snesna"), "N64" to listOf("n64", "n64dd"),
        "GB" to listOf("gb"), "GBC" to listOf("gbc"), "GBA" to listOf("gba", "gba_sp"),
        // (The closed DS and 3DS stay the defaults; the open ones are there to pick.)
        "NDS" to listOf("nds", "ds_open"), "3DS" to listOf("n3ds", "3ds_open"),
        "VirtualBoy" to listOf("virtualboy"), "GameCube" to listOf("gc"), "Wii" to listOf("wii"), "WiiU" to listOf("wiiu"),
        "Switch" to listOf("switch", "switch_oled", "switch_lite"),
        "PS1" to listOf("psx"), "PS2" to listOf("ps2", "ps2_slim"), "PS3" to listOf("ps3", "ps3_slim", "ps3_superslim"), "PS4" to listOf("ps4"),
        "PS5" to listOf("ps5"), "PSP" to listOf("psp"), "PSVita" to listOf("psvita"),
        "Genesis" to listOf("genesis", "megadrivejp"), "MasterSystem" to listOf("mastersystem", "mark3"), "GameGear" to listOf("gamegear"),
        "SegaCD" to listOf("segacd"), "32X" to listOf("sega32x"), "Saturn" to listOf("saturn", "saturnjp"), "Dreamcast" to listOf("dreamcast"),
        "PCEngine" to listOf("pcengine", "tg16"), "NeoGeo" to listOf("neogeo"), "NeoGeoCD" to listOf("neogeocd"),
        "NeoGeoPocket" to listOf("ngpc", "ngp"), "WonderSwan" to listOf("wonderswancolor"),
        "Atari2600" to listOf("atari2600"), "Atari5200" to listOf("atari5200"), "Atari7800" to listOf("atari7800"),
        "Lynx" to listOf("atarilynx"), "Jaguar" to listOf("atarijaguar", "atarijaguarcd"), "3DO" to listOf("3do"),
        "ColecoVision" to listOf("colecovision", "adam"), "Intellivision" to listOf("intellivision"), "Vectrex" to listOf("vectrex"),
        "C64" to listOf("c64"), "Amiga" to listOf("amiga", "amiga1200"),
        "Xbox" to listOf("xbox"), "Xbox360" to listOf("xbox360", "xbox360_slim"),
        "XboxOne" to listOf("xboxone", "xboxone_x"), "XboxSeries" to listOf("xboxseries_x", "xboxseries_s"),
        "Android" to listOf("android"),
        // (Steam: the Steam Machine — never a Steam Deck photo.)
        "Steam" to listOf("steam")
    )

    /** ES-DE / folder names that mean one of Voltcap's keys above (lower case, no spaces), where they differ from a file. */
    private val ALIASES: Map<String, String> = mapOf(
        "ps1" to "PS1", "playstation" to "PS1", "gamecube" to "GameCube", "ngc" to "GameCube", "megadrive" to "Genesis", "md" to "Genesis",
        "sms" to "MasterSystem", "megacd" to "SegaCD", "32x" to "32X", "turbografx16" to "PCEngine", "neogeopocket" to "NeoGeoPocket",
        "wonderswan" to "WonderSwan", "lynx" to "Lynx", "jaguar" to "Jaguar", "3ds" to "3DS", "ds" to "NDS", "sfc" to "SNES",
        "superfamicom" to "SNES", "xboxseriesx" to "XboxSeries", "xboxseries" to "XboxSeries", "xboxseriess" to "XboxSeries",
        "commodore64" to "C64", "vb" to "VirtualBoy", "gg" to "GameGear", "nintendoswitch" to "Switch"
    )

    /** Names for the pictures (Settings › Categories › Console pictures). */
    val NAMES: Map<String, String> = mapOf(
        "nes" to "NES", "famicom" to "Famicom", "snes" to "Super Nintendo", "snesna" to "Super NES (North America)",
        "n64" to "Nintendo 64", "n64dd" to "Nintendo 64 + 64DD", "gba" to "Game Boy Advance", "gba_sp" to "Game Boy Advance SP",
        "nds" to "DS Lite (closed)", "ds_open" to "DS Lite (open)", "n3ds" to "3DS XL (closed)", "3ds_open" to "3DS (open)",
        "switch" to "Switch", "switch_oled" to "Switch OLED", "switch_lite" to "Switch Lite", "ps2" to "PlayStation 2",
        "ps2_slim" to "PlayStation 2 Slim", "ps3" to "PlayStation 3", "ps3_slim" to "PlayStation 3 Slim", "ps3_superslim" to "PlayStation 3 Super Slim",
        "genesis" to "Genesis", "megadrivejp" to "Mega Drive (Japan)", "mastersystem" to "Master System", "mark3" to "Mark III",
        "saturn" to "Saturn", "saturnjp" to "Saturn (white)", "pcengine" to "PC Engine", "tg16" to "TurboGrafx-16",
        "ngpc" to "Neo Geo Pocket Color", "ngp" to "Neo Geo Pocket", "atarijaguar" to "Jaguar", "atarijaguarcd" to "Jaguar + CD",
        "colecovision" to "ColecoVision", "adam" to "Adam", "amiga" to "Amiga 500", "amiga1200" to "Amiga 1200",
        "xbox360" to "Xbox 360", "xbox360_slim" to "Xbox 360 S", "xboxone" to "Xbox One", "xboxone_x" to "Xbox One X",
        "xboxseries_x" to "Xbox Series X", "xboxseries_s" to "Xbox Series S"
    )

    /** Your pick per console, "KEY=name" (Settings › Categories › Console pictures). */
    val picks get() = Features.consoleHwPick

    /** Voltcap's key for [platform] when it is one of them (also from an ES-DE name), else null. */
    fun keyFor(platform: String): String? {
        VARIANTS.keys.firstOrNull { it.equals(platform, true) }?.let { return it }
        val n = platform.lowercase().replace(" ", "").replace("-", "").replace("_", "")
        return ALIASES[n] ?: VARIANTS.entries.firstOrNull { (_, v) -> v.first() == n }?.key
    }

    /** The picture's name for [platform] (your pick, else its default; another platform by its own ES-DE name), or null. */
    fun nameFor(platform: String): String? {
        val key = keyFor(platform)
        if (key != null) {
            val list = VARIANTS[key].orEmpty()
            val pick = picks.state.value.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
            return pick?.takeIf { it in list && it in FILES } ?: list.firstOrNull { it in FILES }
        }
        // (A console Voltcap has no key for keeps its folder's name — that is its ES-DE system name.)
        val n = platform.lowercase().replace(" ", "")
        return n.takeIf { it in FILES && '_' !in it && it != "ds" }
    }

    /** Coil's address of [platform]'s picture, or null. */
    fun model(platform: String): String? = nameFor(platform)?.let { asset(it) }

    fun asset(name: String) = "file:///android_asset/console_hw/$name.webp"

    /** True for an address [model] gave (drawn whole with room round it, never cropped). */
    fun isOurs(url: String?): Boolean = url != null && url.startsWith("file:///android_asset/console_hw/")

    fun pick(key: String, name: String) {
        picks.value = picks.value.filterNot { it.startsWith("$key=") }.toSet() + "$key=$name"
    }
}
