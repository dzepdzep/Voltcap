package com.shelf.launcher.data

/**
 * (Update 238, #111) Recognises ROM hacks (fan hacks such as Pokémon Radical Red, Unbound, Emerald Kaizo, and files
 * tagged "(Hack)", "[Hack]", "~Hack~", "[h1]") from the file name and the library name, so that:
 *  - RetroAchievements links them only by RA's own hash match (RaTitleMatcher, RaMatchStore.isAcceptedLink), never by a
 *    title that, once its tags are stripped, reads as the base game's;
 *  - name-searched artwork (SteamGridDB, TheGamesDB, IGDB, RAWG) and ScreenScraper's answers are the hack's own, searched
 *    by the hack's own title ([ownTitle]), and nothing (the plain placeholder) when only the base game would be found.
 *
 * Nothing here is an achievement status: it only says what the file is.
 */
object RomHacks {

    /** What is known about a fan hack. [ownTitle]: its title with region / version / hack tags gone but the hack's own
     * name kept ("Pokemon Radical Red"). [distinctive]: that title names the hack itself (a known hack name or a
     * kaizo / randomizer word in the title), so a database entry with that exact title really is the hack. When false
     * the title is just the base game's ("Pokemon Emerald (Hack)") and only a checksum can tell them apart. */
    data class FanHack(val ownTitle: String, val distinctive: Boolean)

    /** A bracketed part naming a hack: "(Hack)", "[Hack]", "(Rom Hack)", "(Kaizo Hack)", "[Hack by X]", "(Randomizer)". */
    private val HACK_TAG = Regex(
        """[(\[{]([^)\]}]*\b(hack|hacked|romhack|rom hack|kaizo|randomi[sz]ed|randomi[sz]er|nuzlocke)\b[^)\]}]*)[)\]}]""",
        RegexOption.IGNORE_CASE
    )

    /** Hack tags that are only a technical patch of the same game (not a fan hack): widescreen, frame rate, intro. */
    private val TECHNICAL = Regex("""\b(widescreen|wide screen|16:9|\d+\s*fps|\d+\s*hz|intro|trainer|fix)\b""", RegexOption.IGNORE_CASE)

    /** GoodTools' "[h1]", "[h2C]": a hacked dump (often only a cosmetic or trainer hack of the same game). */
    private val GOODTOOLS_HACK = Regex("""\[h\d*[a-z]?\d*\]""", RegexOption.IGNORE_CASE)

    /** Words in the title itself that make it a hack's own name. */
    private val HACK_WORD = Regex("""\b(kaizo|romhack|rom hack|randomi[sz]er|nuzlocke)\b""", RegexOption.IGNORE_CASE)

    /** Well-known Pokémon fan hacks (only counted when the title also says Pokémon); none is an official title. */
    private val POKEMON_HACKS = listOf(
        "radical red", "unbound", "emerald kaizo", "glazed", "blazed glazed", "gaia", "clover", "light platinum",
        "dark rising", "flora sky", "ash gray", "ash grey", "liquid crystal", "crystal clear", "prism", "polished crystal",
        "inclement emerald", "emerald rogue", "rogue", "seaglass", "imperium", "elite redux", "ultra shiny gold",
        "snakewood", "theta emerald", "sienna", "quetzal", "brown", "fire ash", "mega power", "lazarus", "resolute",
        "adventure red", "lost gold", "naranja", "sors", "throwback", "dreamstone", "sweet version", "rocket edition",
        "procyon", "vega", "altair", "eclipse", "kanto expansion pack", "perfect crystal",
        "cloud white", "shining opal", "emerald plus", "renegade platinum", "blaze black", "volt white",
        "sacred gold", "storm silver", "drayano", "moemon"
    )

    /** The file name (no folder, no extension) when there is one, else the library name. */
    private fun sourceName(name: String?, path: String?): String {
        val file = path?.replace('\\', '/')?.substringAfterLast('/')?.substringBeforeLast('.')
        return file?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    }

    private fun hasHackTag(s: String): Boolean =
        HACK_TAG.findAll(s).any { !TECHNICAL.containsMatchIn(it.groupValues[1]) } || s.contains("~hack~", ignoreCase = true)

    private fun bare(s: String): String = s.replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ").replace(Regex("~[^~]*~"), " ")

    private fun plain(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().replace('_', ' ').replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun knownPokemonHack(s: String): Boolean {
        val p = " ${plain(bare(s))} "
        if (!p.contains(" pokemon ") && !p.contains(" pocket monsters ")) return false
        return POKEMON_HACKS.any { p.contains(" $it ") }
    }

    /** The fan hack [name] / [path] is, or null when it doesn't look like one. Either one may say so. */
    fun fanHack(name: String?, path: String?): FanHack? {
        val candidates = listOfNotNull(path?.let { sourceName(null, it) }, name).filter { it.isNotBlank() }
        if (candidates.isEmpty()) return null
        val tagged = candidates.any { hasHackTag(it) }
        val named = candidates.any { knownPokemonHack(it) || HACK_WORD.containsMatchIn(bare(it)) }
        if (!tagged && !named) return null
        val title = ownTitle(candidates.first())
        return FanHack(title, distinctive = named)
    }

    fun fanHack(game: Game): FanHack? = if (game.sourceType != SourceType.ROM) null else fanHack(game.name, game.romPath)

    /** True for any modified ROM — a fan hack, or a GoodTools "[hN]" hacked dump — whose RetroAchievements link must
     * come from RA's hash match only. */
    fun isModified(name: String?, path: String?): Boolean =
        fanHack(name, path) != null || listOfNotNull(name, path?.let { sourceName(null, it) }).any { GOODTOOLS_HACK.containsMatchIn(it) }

    /**
     * The term to search name-based art sources with for a game called [name] (and at [path]): [name] itself for
     * anything that isn't a fan hack; the hack's own title for a hack whose title names it; null for a hack known only
     * by a tag ("Pokemon Emerald (Hack)") — a search would find the base game, so it gets the plain placeholder instead.
     */
    fun artSearchName(name: String, path: String? = null): String? {
        val hack = fanHack(name, path) ?: return name
        return hack.ownTitle.takeIf { hack.distinctive && it.isNotBlank() }
    }

    /** "Pokemon_-_Radical_Red_v4.1_(Hack)_(USA)" → "Pokemon - Radical Red": tags, ~Hack~, versions gone; the hack's name kept. */
    fun ownTitle(raw: String): String {
        var t = raw.replace('_', ' ')
        t = t.replace(Regex("~[^~]*~"), " ")
        t = t.replace(Regex("""[(\[{][^)\]}]*[)\]}]"""), " ")
        t = t.replace(Regex("""\b(rom ?hack|hack(ed)?)\b""", RegexOption.IGNORE_CASE), " ")
        t = t.replace(Regex("""\s+v(er(sion)?)?\.?\s*\d+(\.\d+)*[a-z]?\b.*$""", RegexOption.IGNORE_CASE), " ")
        t = t.replace(Regex("\\s+"), " ").trim().trim('-', ':', '|', '~', ',').trim()
        return t.ifBlank { raw.trim() }
    }

    /** True when one of [names] (a database's titles) is [hack]'s own title, whole (TitleMatch: that title, or it plus a short subtitle). */
    fun namesTheHack(hack: FanHack, names: List<String>): Boolean =
        hack.distinctive && names.any { TitleMatch.score(hack.ownTitle, it) >= 0.85 }
}
