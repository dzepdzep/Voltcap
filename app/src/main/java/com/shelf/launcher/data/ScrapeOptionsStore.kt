package com.shelf.launcher.data

import android.content.Context

/** Artwork kinds a scrape can fetch. */
enum class ArtField(val label: String) {
    COVER("Covers"),
    WIDE("Wide cards"),
    HERO("Backgrounds"),
    LOGO("Logos")
}

/** Groups of game details a scrape can fetch. */
enum class DetailField(val label: String) {
    SUMMARY("Summary"),
    INFO("Year, genres, developer"),
    SCORE("Critic score"),
    METACRITIC("Metacritic (RAWG)"),
    PLAYTIME("How long to beat")
}

/** What the next scrapes download. Unticked things are left as they are. */
data class ScrapeOptions(
    val art: Set<ArtField> = ArtField.values().toSet(),
    val details: Set<DetailField> = DetailField.values().toSet(),
    /** Scanned manuals (ScreenScraper) downloaded with the artwork. On unless switched off. */
    val manuals: Boolean = true
)

class ScrapeOptionsStore(context: Context) {
    private val prefs = context.getSharedPreferences("scrape_options", Context.MODE_PRIVATE)

    fun load(): ScrapeOptions = ScrapeOptions(
        art = prefs.getString("art", null)?.let { parse<ArtField>(it) } ?: ArtField.values().toSet(),
        details = prefs.getString("details", null)?.let { parse<DetailField>(it) } ?: DetailField.values().toSet(),
        manuals = prefs.getBoolean("manuals", true)
    )

    fun save(options: ScrapeOptions) {
        prefs.edit()
            .putString("art", options.art.joinToString(",") { it.name })
            .putString("details", options.details.joinToString(",") { it.name })
            .putBoolean("manuals", options.manuals)
            .apply()
    }

    private inline fun <reified T : Enum<T>> parse(saved: String): Set<T> =
        saved.split(',').filter { it.isNotBlank() }
            .mapNotNull { name -> runCatching { enumValueOf<T>(name) }.getOrNull() }
            .toSet()
}

/** [old] with only the [fields] taken from [fresh] (where [fresh] has them). */
fun GameMetadata?.mergeFields(fresh: GameMetadata, fields: Set<DetailField>): GameMetadata {
    val base = this ?: GameMetadata(
        title = fresh.title, summary = null, releaseYear = null, criticScore = null, criticCount = null,
        genres = emptyList(), developer = null, hastilyHours = null, normallyHours = null, completelyHours = null
    )
    var out = base.copy(title = base.title ?: fresh.title)
    if (DetailField.SUMMARY in fields) out = out.copy(summary = fresh.summary ?: out.summary)
    if (DetailField.INFO in fields) out = out.copy(
        releaseYear = fresh.releaseYear ?: out.releaseYear,
        genres = fresh.genres.ifEmpty { out.genres },
        developer = fresh.developer ?: out.developer,
        publisher = fresh.publisher ?: out.publisher
    )
    if (DetailField.SCORE in fields && fresh.criticScore != null) out = out.copy(
        criticScore = fresh.criticScore,
        criticCount = fresh.criticCount
    )
    if (DetailField.METACRITIC in fields) out = out.copy(metacritic = fresh.metacritic ?: out.metacritic)
    if (DetailField.PLAYTIME in fields) out = out.copy(
        hastilyHours = fresh.hastilyHours ?: out.hastilyHours,
        normallyHours = fresh.normallyHours ?: out.normallyHours,
        completelyHours = fresh.completelyHours ?: out.completelyHours
    )
    return out
}

/** Whether [meta] is missing any of [fields] (so "download missing" should visit it). */
fun GameMetadata?.lacksAny(fields: Set<DetailField>, rawgConnected: Boolean): Boolean {
    if (this == null) return true
    return fields.any { f ->
        when (f) {
            DetailField.SUMMARY -> summary.isNullOrBlank()
            DetailField.INFO -> releaseYear == null && developer == null && genres.isEmpty()
            DetailField.SCORE -> criticScore == null
            DetailField.METACRITIC -> rawgConnected && metacritic == null
            DetailField.PLAYTIME -> normallyHours == null && hastilyHours == null && completelyHours == null
        }
    }
}

/** [old] with only the [fields] kinds of art taken from [fresh]. */
fun GameArt?.mergeArt(fresh: GameArt, fields: Set<ArtField>): GameArt {
    val base = this ?: GameArt(gridUrl = null, heroUrl = null)
    return base.copy(
        gridUrl = if (ArtField.COVER in fields) fresh.gridUrl ?: base.gridUrl else base.gridUrl,
        wideGridUrl = if (ArtField.WIDE in fields) fresh.wideGridUrl ?: base.wideGridUrl else base.wideGridUrl,
        heroUrl = if (ArtField.HERO in fields) fresh.heroUrl ?: base.heroUrl else base.heroUrl,
        logoUrl = if (ArtField.LOGO in fields) fresh.logoUrl ?: base.logoUrl else base.logoUrl,
        animatedHeroUrl = if (ArtField.HERO in fields) fresh.animatedHeroUrl ?: base.animatedHeroUrl else base.animatedHeroUrl
    )
}

/** Whether [art] is missing any of the selected kinds. */
fun GameArt?.lacksAny(fields: Set<ArtField>): Boolean {
    if (this == null) return true
    return fields.any { f ->
        when (f) {
            ArtField.COVER -> gridUrl == null
            ArtField.WIDE -> wideGridUrl == null
            ArtField.HERO -> heroUrl == null
            ArtField.LOGO -> logoUrl == null
        }
    }
}
