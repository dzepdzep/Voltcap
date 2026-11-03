package com.shelf.launcher.data

/**
 * Update 461 (owner: "for ROM hacks, never substitute the base game's label"): a ROM hack that ScreenScraper matched to its
 * base game gets the base game's scans — Pokémon Crystal Kaizo wore Pokémon Crystal's real label (same ScreenScraper game,
 * jeuid 82606, on the KONKR). A hack whose saved scans are the same ScreenScraper game as an official game in the library
 * keeps its box but not that game's label scan or cartridge photo ([DROP]): it gets its own label from the Labels folder
 * (LabelFolder), your pick, or the printed fallback. A hack with its own ScreenScraper entry (Crystal Clear's fan label) is
 * untouched.
 */
object HackLabels {
    val DROP = setOf("Disc label", "Disc / cartridge")
    private val JEU = Regex("""[?&]jeuid=(\d+)""")

    /** The ScreenScraper game id the pictures in [list] were taken from, or null (pure — unit-tested). */
    fun gameIdOf(list: List<Pair<String, String>>): String? = list.firstNotNullOfOrNull { JEU.find(it.second)?.groupValues?.get(1) }

    /** The keys of the hacks in [raw] whose scans are an official game's (same game id as a game that isn't a hack) (pure). */
    fun baseMatched(raw: Map<String, List<Pair<String, String>>>, isHack: (String) -> Boolean): Set<String> {
        val official = raw.filterKeys { !isHack(it) }.values.mapNotNull { gameIdOf(it) }.toSet()
        if (official.isEmpty()) return emptySet()
        return raw.filter { (k, v) -> isHack(k) && gameIdOf(v)?.let { it in official } == true }.keys
    }

    /** The hacks found by [baseMatched] for the library (set by the view model). */
    @Volatile var keys: Set<String> = emptySet()

    /** [list] without the base game's label scan and cartridge photo when [key] is such a hack (pure apart from [keys]). */
    fun strip(key: String, list: List<Pair<String, String>>): List<Pair<String, String>> =
        if (key in keys) list.filter { it.first !in DROP } else list
}
