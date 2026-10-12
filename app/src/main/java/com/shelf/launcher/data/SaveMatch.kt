package com.shelf.launcher.data

/**
 * Update 151: matching a save file to a library game, for the Save Vault. Saves synced from another device often carry
 * more than the ROM's name: the emulator core in front ("Gambatte_Pokemon - Red Version…" when a core folder was
 * flattened), tags the ROM doesn't have ("[patched]", "(Subset - …)"), accents the ROM name lacks ("POKéMON"), or "_"
 * where the ROM has an apostrophe. Library names are worked out once ([index]), not again for every file.
 */
internal object SaveMatch {

    /** One library game's names, ready to compare. */
    class Named(val game: Game, val stem: String, val tagKey: String, val titleKey: String)

    fun index(games: List<Game>): List<Named> = games.mapNotNull { g ->
        val path = g.romPath ?: return@mapNotNull null
        val stem = path.substringAfterLast('/').substringBeforeLast('.')
        Named(g, fold(stem), tagKey(stem), MemoryCards.titleKey(com.shelf.launcher.ui.stripRegionTags(g.name)))
    }

    /** Lower case, accents off ("Pokémon" → "pokemon"). */
    fun fold(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(ACCENTS, "").lowercase().trim()

    /** Letters and digits only, tags kept: "Mario_s Picross X (Hack)" and "Mario's Picross X (Hack)" are the same. */
    fun tagKey(s: String): String = fold(s).replace(NOT_ALNUM, " ").trim()

    /**
     * "Gambatte_Pokemon - Red…" → ("gambatte", "Pokemon - Red…"): the RetroArch core in front of a save's name, when it
     * has one. A name without a known core in front comes back as it is (with null).
     */
    fun splitCore(stem: String): Pair<String?, String> {
        var i = stem.indexOf('_')
        var tries = 0
        while (i > 0 && tries < 4) {
            val key = stem.substring(0, i).lowercase().replace(NOT_ALNUM_RAW, "")
            if (key in CORES) {
                val rest = stem.substring(i + 1).trim()
                return if (rest.length >= 2) key to rest else null to stem
            }
            i = stem.indexOf('_', i + 1)
            tries++
        }
        return null to stem
    }

    /** The console a core runs ("gambatte" → "gb"), or null. */
    fun platformOfCore(core: String?): String? = core?.let { CORES[it] }

    /** Same console for the vault: "gc" and "GameCube" agree, and Game Boy and Game Boy Color go together. */
    fun samePlatform(hint: String?, platform: String): Boolean {
        if (hint == null || hint == "other") return false
        val a = SaveCapsules.canonicalPlatform(hint).lowercase()
        val b = SaveCapsules.canonicalPlatform(platform).lowercase()
        if (a == b) return true
        return a in GB_FAMILY && b in GB_FAMILY
    }

    /**
     * The library game a save named [fileStem] (its file name without the type, core already taken off) belongs to:
     * the same file name first, then the same name with its tags, then the same title without tags. Within each step a
     * game of the save's console ([platformHint]) wins. [allowed] says which games may hold this kind of save.
     */
    fun best(fileStem: String, platformHint: String?, among: List<Named>, allowed: (Game) -> Boolean = { true }): Game? {
        if (among.isEmpty()) return null
        val stem = fold(fileStem)
        val tags = tagKey(fileStem)
        val title = MemoryCards.titleKey(com.shelf.launcher.ui.stripRegionTags(fileStem))
        fun pick(test: (Named) -> Boolean): Game? {
            var other: Game? = null
            for (n in among) {
                if (!test(n) || !allowed(n.game)) continue
                if (samePlatform(platformHint, n.game.platform)) return n.game
                if (other == null) other = n.game
            }
            return other
        }
        return pick { it.stem == stem }
            ?: (if (tags.length >= 3) pick { it.tagKey == tags } else null)
            ?: pick { keysMatch(title, it.titleKey) || keysMatch(it.titleKey, title) }
    }

    /** [MemoryCards.titleMatches] on names already turned into keys. */
    private fun keysMatch(a: String, b: String): Boolean {
        if (a.length < 3 || b.length < 3) return false
        if (a == b) return true
        return a.startsWith("$b ") || (b.length >= 6 && a.startsWith(b) && !a[b.length].isDigit())
    }

    private val ACCENTS = Regex("\\p{M}+")
    private val NOT_ALNUM = Regex("[^a-z0-9]+")
    private val NOT_ALNUM_RAW = Regex("[^a-z0-9]")
    private val GB_FAMILY = setOf("gb", "gbc")

    /** RetroArch cores as they name their save folders (letters and digits only, lower case) → their console. */
    private val CORES: Map<String, String> = mapOf(
        "gambatte" to "gb", "sameboy" to "gb", "gearboy" to "gb", "tgbdual" to "gb",
        "mgba" to "gba", "vbam" to "gba", "vbanext" to "gba", "gpsp" to "gba", "meteor" to "gba",
        "snes9x" to "snes", "snes9x2010" to "snes", "snes9x2005" to "snes", "snes9x2005plus" to "snes", "snes9x2002" to "snes",
        "bsnes" to "snes", "bsneshdbeta" to "snes", "bsnesmercuryaccuracy" to "snes", "bsnesmercurybalanced" to "snes",
        "bsnesmercuryperformance" to "snes", "mesens" to "snes", "beetlesupafaust" to "snes",
        "nestopia" to "nes", "fceumm" to "nes", "quicknes" to "nes", "mesen" to "nes",
        "mupen64plus" to "n64", "mupen64plusnext" to "n64", "paralleln64" to "n64",
        "genesisplusgx" to "megadrive", "genesisplusgxwide" to "megadrive", "picodrive" to "megadrive", "blastem" to "megadrive",
        "beetlepsx" to "psx", "beetlepsxhw" to "psx", "swanstation" to "psx", "duckstation" to "psx", "pcsxrearmed" to "psx",
        "flycast" to "dreamcast", "melonds" to "nds", "melondsds" to "nds", "desmume" to "nds", "desmume2015" to "nds",
        "ppsspp" to "psp", "beetlepce" to "pcengine", "beetlepcefast" to "pcengine", "beetlesupergrafx" to "pcengine",
        "beetlesaturn" to "saturn", "yabause" to "saturn", "kronos" to "saturn", "yabasanshiro" to "saturn",
        "stella" to "atari2600", "stella2014" to "atari2600", "prosystem" to "atari7800", "handy" to "atarilynx",
        "beetlelynx" to "atarilynx", "beetlengp" to "ngp", "beetleneopop" to "ngp", "beetlewonderswan" to "wonderswan",
        "beetlecygne" to "wonderswan", "beetlevb" to "virtualboy", "virtualjaguar" to "atarijaguar",
        "fbneo" to "arcade", "finalburnneo" to "arcade", "mame" to "arcade", "mame2003plus" to "arcade", "mame2003" to "arcade",
        "gearsystem" to "mastersystem", "smsplusgx" to "mastersystem", "pokemini" to "pokemini", "opera" to "3do"
    )
}
