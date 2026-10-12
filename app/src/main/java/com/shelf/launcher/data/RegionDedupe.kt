package com.shelf.launcher.data

/**
 * Region and duplicate handling: the same game in several regions / revisions ("(USA)", "(Europe)",
 * "(Japan) (Rev 1)") shows once, as the copy in the region you prefer. A copy you've played or
 * starred always wins, so nothing you use disappears. Only ROMs of the same console are merged.
 */
object RegionDedupe {
    val DEFAULT_ORDER = listOf("USA", "Europe", "World", "Japan")

    private val REGION_TAG = Regex("""[(\[]([^)\]]*)[)\]]""")

    /** Region words in a file name, lower case ("usa", "europe"…). */
    private fun regions(name: String): List<String> = REGION_TAG.findAll(name)
        .flatMap { it.groupValues[1].split(',', '/', '+').map { p -> p.trim().lowercase() } }
        .toList()

    private val ALIASES = mapOf(
        "usa" to listOf("usa", "us", "u", "america", "ntsc-u", "ntsc"),
        "europe" to listOf("europe", "eu", "eur", "e", "pal", "pal-e", "uk"),
        "world" to listOf("world", "jue", "ue"),
        "japan" to listOf("japan", "jp", "jpn", "j", "ntsc-j")
    )

    private fun rank(name: String, order: List<String>): Int {
        val rs = regions(name)
        order.forEachIndexed { i, region ->
            val words = ALIASES[region.lowercase()] ?: listOf(region.lowercase())
            if (rs.any { it in words }) return i
        }
        return order.size
    }

    /** Revision / version number, higher is newer ("Rev 2", "v1.1"). */
    private fun revision(name: String): Float =
        Regex("""(?:rev|v)\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

    fun apply(
        games: List<Game>,
        order: List<String>,
        used: (Game) -> Boolean
    ): List<Game> {
        val roms = games.filter { it.sourceType == SourceType.ROM }
        if (roms.size < 2) return games
        val keep = HashSet<String>()
        roms.groupBy { it.platform + "\u0000" + com.shelf.launcher.ui.stripRegionTags(it.name).lowercase() }.values.forEach { group ->
            if (group.size == 1) { keep += group[0].stableKey; return@forEach }
            val best = group.sortedWith(
                compareByDescending<Game> { used(it) }
                    .thenBy { rank(it.name, order) }
                    .thenByDescending { revision(it.name) }
                    .thenBy { it.name.length }
            ).first()
            keep += best.stableKey
            // Every copy you've played or starred stays too.
            group.filter { used(it) }.forEach { keep += it.stableKey }
        }
        return games.filter { it.sourceType != SourceType.ROM || it.stableKey in keep }
    }
}
