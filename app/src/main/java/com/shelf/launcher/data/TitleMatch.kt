package com.shelf.launcher.data

/**
 * Picking the right search result when scraping by name.
 *
 * The sources return their closest titles, and taking the first one (or the first that merely *starts*
 * with the name) matched apps with games of a similar name - Moonlight (the game-streaming app) got
 * Moonlighter's art. Now:
 *  1. Known non-game apps (streaming, remote play, emulators, frontends, tools) are recognised by their
 *     package first and are never matched to a game by name - only an exact title will do.
 *  2. Every other app (platform "App") needs an exact title (ignoring case, punctuation, ™/®, "the").
 *  3. Games need the title's words as whole words: "Zelda" finds "The Legend of Zelda", but
 *     "Moonlight" never finds "Moonlighter" (a word that only begins the same way doesn't count).
 * Among results that qualify, the closest wins; ties keep the source's own order.
 */
object TitleMatch {

    /** What an app is, when its package tells us. */
    enum class AppKind { STREAMING, EMULATOR, FRONTEND, TOOL }

    /** Package (or package prefix) → kind, for apps that look like games by name but aren't. */
    private val KNOWN_APPS: List<Pair<String, AppKind>> = listOf(
        // Game streaming / remote play.
        "com.limelight" to AppKind.STREAMING,                 // Moonlight
        "com.moonlight" to AppKind.STREAMING,
        "com.artemis" to AppKind.STREAMING,                   // Artemis (Moonlight fork)
        "com.valvesoftware.steamlink" to AppKind.STREAMING,
        "com.parsec.app" to AppKind.STREAMING,
        "com.playstation.remoteplay" to AppKind.STREAMING,
        "com.metallic.chiaki" to AppKind.STREAMING,
        "com.grill.psplay" to AppKind.STREAMING,
        "psplay.grill.com" to AppKind.STREAMING, "com.asobi.droid" to AppKind.STREAMING,
        "com.droiddeck.launcher" to AppKind.FRONTEND,
        "com.grill.xbxplay" to AppKind.STREAMING,
        "com.microsoft.xboxone.smartglass" to AppKind.STREAMING,
        "com.microsoft.xcloud" to AppKind.STREAMING,
        "com.gamepass" to AppKind.STREAMING,
        "com.nvidia.geforcenow" to AppKind.STREAMING,
        "com.nvidia.tegrazone3" to AppKind.STREAMING,
        "com.shadow.blade" to AppKind.STREAMING,
        "com.rainway" to AppKind.STREAMING,
        "com.amazon.luna" to AppKind.STREAMING,
        "com.boosteroid" to AppKind.STREAMING,
        "com.antstream" to AppKind.STREAMING,
        "com.vortex" to AppKind.STREAMING,
        "com.ea.gp.apexlegendsmobilefps.cloud" to AppKind.STREAMING,
        // Emulators.
        "org.ppsspp" to AppKind.EMULATOR, "com.retroarch" to AppKind.EMULATOR, "com.explusalpha" to AppKind.EMULATOR,
        "org.dolphinemu" to AppKind.EMULATOR, "xyz.aethersx2" to AppKind.EMULATOR, "com.github.stenzek" to AppKind.EMULATOR,
        "org.citra" to AppKind.EMULATOR, "io.github.lime3ds" to AppKind.EMULATOR, "io.github.azahar" to AppKind.EMULATOR,
        "org.yuzu" to AppKind.EMULATOR, "dev.eden" to AppKind.EMULATOR, "com.antutu.ABenchMark" to AppKind.TOOL,
        "info.cemu" to AppKind.EMULATOR, "me.magnum.melonds" to AppKind.EMULATOR, "com.drastic" to AppKind.EMULATOR,
        "com.flycast" to AppKind.EMULATOR, "com.reicast" to AppKind.EMULATOR, "skyline.emu" to AppKind.EMULATOR,
        "org.mupen64plusae" to AppKind.EMULATOR, "com.dsemu" to AppKind.EMULATOR, "com.winlator" to AppKind.EMULATOR,
        "app.gamenative" to AppKind.EMULATOR, "com.termux" to AppKind.TOOL,
        // Frontends and launchers.
        "org.es_de" to AppKind.FRONTEND, "com.magneticchen.daijishou" to AppKind.FRONTEND, "org.pegasus_frontend" to AppKind.FRONTEND,
        "com.retroid" to AppKind.TOOL, "com.ayaneo" to AppKind.TOOL, "com.odin" to AppKind.TOOL, "com.ayn" to AppKind.TOOL,
        "com.android" to AppKind.TOOL, "com.google.android" to AppKind.TOOL
    )

    /** The kind of [pkg] if it's a known non-game app, else null. */
    fun knownAppKind(pkg: String?): AppKind? {
        val p = pkg?.lowercase() ?: return null
        return KNOWN_APPS.firstOrNull { (prefix, _) -> p == prefix.lowercase() || p.startsWith(prefix.lowercase() + ".") }?.second
    }

    /** Lower case, ™ / ® gone, "&" → "and", punctuation → spaces, a leading "the" dropped, spaces collapsed. */
    fun normalize(title: String): String {
        // ROM file tags "(USA)", "[!]" and accents ("Pokémon") aren't part of the title.
        val base = java.text.Normalizer.normalize(title.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " "), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val words = base.lowercase()
            .replace(Regex("[™®©]"), "")
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(' ').filter { it.isNotBlank() }
            // Roman numerals as numbers ("Final Fantasy VII" = "Final Fantasy 7").
            .map { ROMAN[it] ?: it }
        return (if (words.firstOrNull() == "the") words.drop(1) else words).joinToString(" ")
    }

    // (update 91) No "x" → "10": "Mega Man X" and "Mega Man 10" are different games but matched each other exactly.
    private val ROMAN = mapOf(
        "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7", "viii" to "8",
        "ix" to "9", "xi" to "11", "xii" to "12", "xiii" to "13", "xiv" to "14", "xv" to "15"
    )

    private fun words(t: String): List<String> = normalize(t).split(' ').filter { it.isNotBlank() }

    /**
     * How well [candidate] matches what we're looking for ([wanted]), 0..1: 1 = the same title,
     * 0.9 = the same title plus a subtitle ("Doom: Eternal" for "Doom" counts lower than that),
     * up to 0.8 = all of the words, whole, with a few extra; 0 = some word missing or only a partial word.
     */
    fun score(wanted: String, candidate: String): Double {
        val a = normalize(wanted)
        val b = normalize(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.replace(" ", "") == b.replace(" ", "")) return 0.97 // "Mega Man" / "MegaMan"
        val wa = words(wanted)
        val wb = words(candidate)
        // Every wanted word must be a whole word of the candidate. Long titles may miss one word
        // (a subtitle spelled differently) at a much lower score; a single-word title never partly matches.
        if (!wa.all { it in wb }) {
            val missing = wa.filter { it !in wb }
            // A missing number is a different game in the series ("Bros. 3" is not "Bros.").
            if (missing.any { m -> m.all { it.isDigit() } }) return 0.0
            val overlap = (wa.size - missing.size).toDouble() / wa.size
            return if (wa.size >= 3 && overlap >= 0.67) 0.45 * overlap else 0.0
        }
        // The candidate is the wanted title followed by a subtitle.
        if (b.startsWith("$a ")) return 0.9 - 0.02 * (wb.size - wa.size).coerceAtMost(5)
        val extra = (wb.size - wa.size).coerceAtLeast(0)
        return (0.8 - 0.06 * extra).coerceAtLeast(0.45)
    }

    /**
     * The index of the best of [candidates] for [wanted], or null when none qualifies.
     * [strict]: only (near-)exact titles — apps.
     */
    fun best(wanted: String, candidates: List<String>, strict: Boolean): Int? {
        var bestI = -1
        var bestS = 0.0
        candidates.forEachIndexed { i, c ->
            val s = score(wanted, c)
            if (s > bestS + 1e-9) { bestS = s; bestI = i }
        }
        val needed = if (strict) 0.95 else 0.3
        return bestI.takeIf { it >= 0 && bestS >= needed }
    }

    /** True when [game] must only ever get an exact-title match (any app). */
    fun strictFor(game: Game): Boolean = game.sourceType == SourceType.APP

    /** Apps the scraper leaves alone (their own icon is shown): known streaming apps, emulators, tools. */
    fun skipScraping(game: Game): Boolean = game.sourceType == SourceType.APP && knownAppKind(game.packageName) != null
}
