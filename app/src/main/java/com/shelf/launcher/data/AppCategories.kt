package com.shelf.launcher.data

/**
 * 725a (owner Task 4): START > Categories on an installed app or game - the categories it is listed in, several at once
 * (Recomps, Android games or Other apps, consoles such as Nintendo 64, your collections). Pure rules, no Android.
 *
 * Every choice is made by hand and is kept with the other app choices (prefs "library_apps", backed up, keyed by package
 * name: an app update, a rescan or a restart keeps it). Only where the app is listed changes: the same Game record (launch,
 * stable key - saves, play time, art, favourites, achievements, metadata) and never a second copy in All games.
 */
object AppCategories {
    private const val SEP = '|'

    /** Keys that are categories of their own, never offered as a console. Aliases of a console the scanner names otherwise
     *  (MegaDrive / Pico8) and the stores GameNative fills are left out too. */
    private val notConsoles = setOf(
        "App", "Android", "Disc", "Recomps", "Steam", "Epic", "GOG", "Amazon", "PC", "MegaDrive", "Pico8"
    )

    fun encode(map: Map<String, Set<String>>): Set<String> =
        map.flatMap { (pkg, set) -> set.map { "$pkg$SEP$it" } }.toSet()

    fun decode(raw: Set<String>?): Map<String, Set<String>> =
        raw.orEmpty().mapNotNull { e ->
            val i = e.indexOf(SEP)
            if (i <= 0 || i == e.length - 1) null else e.substring(0, i) to e.substring(i + 1)
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    fun consolesOf(s: LibraryAppsSettings, pkg: String): Set<String> = s.appConsoles[pkg].orEmpty()

    /** [pkg] listed under [platform] too ([on]) or not; an app with no console left drops out of the map. */
    fun withConsole(s: LibraryAppsSettings, pkg: String, platform: String, on: Boolean): LibraryAppsSettings {
        if (platform in notConsoles) return s
        val now = consolesOf(s, pkg).let { if (on) it + platform else it - platform }
        return s.copy(appConsoles = if (now.isEmpty()) s.appConsoles - pkg else s.appConsoles + (pkg to now))
    }

    fun isConsole(platform: String): Boolean = platform !in notConsoles && !isCollectionKey(platform)

    /**
     * The consoles START > Categories offers: the ones in your library first (as L2 / R2 orders them), then the ones this
     * app is already in; [all] adds every other console Voltcap names (the "More consoles" list). No duplicates.
     */
    fun consoleChoices(libraryPlatforms: List<String>, assigned: Set<String>, all: Boolean): List<String> {
        val first = (libraryPlatforms.filter { isConsole(it) } + assigned.sorted()).distinct()
        return if (!all) first else (first + ConsoleNames.keys.filter { isConsole(it) }).distinct()
    }

    /** One line naming where the app is listed (the Categories row's subtitle). */
    fun summary(androidGame: Boolean, recomp: Boolean, otherApp: Boolean, consoles: Set<String>, collections: Int): String {
        val parts = buildList {
            if (androidGame) add("Android games")
            if (recomp) add("Recomps")
            if (otherApp) add("Other apps")
            consoles.sortedBy { ConsoleNames.display(it) }.forEach { add(ConsoleNames.display(it)) }
            if (collections == 1) add("1 collection") else if (collections > 1) add("$collections collections")
        }
        return if (parts.isEmpty()) "Only in All games - pick where else it's listed" else parts.joinToString(" · ")
    }
}
