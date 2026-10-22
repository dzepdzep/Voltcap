package com.shelf.launcher.data

/**
 * Voltcap's own screens as L1 shortcuts, next to your apps: they're stored like apps (with a
 * "voltcap:" name instead of a package) and open the screen instead of launching anything.
 */
object BuiltinShortcuts {
    const val PREFIX = "voltcap:"

    data class Entry(val id: String, val label: String)

    /** Every R1 tile that can be pinned to L1 (id → name): tapping one presses it in the R1 panel. */
    val R1_TILES = listOf(
        "wifi" to "Wi-Fi", "bt" to "Bluetooth", "dnd" to "Do not disturb", "lights" to "Lights",
        "music" to "Play / pause music", "musicspot" to "Music player position", "edithome" to "Edit home",
        "dual" to "Dual screen", "navbar" to "Hide nav bar",
        "fps" to "Overlay", "sort" to "Sort", "gamelook" to "Game look", "media" to "Cartridges & discs", "badges" to "Console badges", "layout" to "Layout", "endless" to "Endless row",
        "roundshort" to "Round shortcuts", "progressbar" to "Achievements place", "gamesize" to "Game size", "corners" to "Cover corners", "categories" to "Category names", "rowpos" to "Row position",
        "romm" to "RomM", "carousel" to "Carousel", "theme" to "Theme", "colors" to "Colours follow", "rescan" to "Rescan library", "scrape" to "Missing art",
        "profile" to "Power profile", "batterywatch" to "Battery watcher", "battery" to "Battery & charge limit",
        "mixer" to "Volume mixer", "settings" to "Android settings",
        "shelflook" to "Shelf look", "spotlight" to "Spotlight tiles", "bartabs" to "Icon bar tabs", "crosscut" to "Cross menu logos",
        "headertight" to "Header layout", "drawnmedia" to "The game's own photo (cartridges & discs)", "magnifier" to "Magnifier", "whoplays" to "Who is playing",
        "herofit" to "Background picture fit"
    )

    val all = listOf(
        Entry("voltcap:achievements", "Achievements"),
        Entry("voltcap:stats", "Play stats"),
        Entry("voltcap:profile", "Profile"),
        Entry("voltcap:spotify", "Play Spotify"),
        Entry("voltcap:music", "Music"),
        Entry("voltcap:saves", SAVES_NAME),
        Entry("voltcap:recap", "Your recap"),
        Entry("voltcap:whattoplay", "What should I play?"),
        Entry("voltcap:rediscover", "Rediscover"),
        Entry("voltcap:explore", "Explore (show all equally)"),
        Entry("voltcap:categories", "Categories"),
        Entry("voltcap:screensaver", "Screensaver"),
        Entry("voltcap:settings", "Settings")
    ) + R1_TILES.map { (id, label) -> Entry("voltcap:r1:$id", "R1 · $label") }


    fun isBuiltin(id: String?) = id?.startsWith(PREFIX) == true

    /** The entry as a library item, so the panel can show and order it like an app. */
    fun game(id: String): Game? = all.firstOrNull { it.id == id }?.let {
        Game(name = it.label, platform = "Voltcap", hue = (id.hashCode() and 0x7fffffff) % 360, sourceType = SourceType.APP, packageName = id)
    }
}

/** The save browser's name everywhere: your saves kept safe (and synced) in one place. */
const val SAVES_NAME = "Save Vault"
