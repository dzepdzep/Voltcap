package com.shelf.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.shelf.launcher.data.TitlePlacement

/** How game names are shown on the cards, provided once at the root. */
data class TitleOptions(
    val placement: TitlePlacement = TitlePlacement.INSIDE,
    val hideRegions: Boolean = true,
    val coverAchievements: Boolean = true,
    val coverRecentUnlocks: Boolean = false,
    val masteredStyle: com.shelf.launcher.data.MasteredStyle = com.shelf.launcher.data.MasteredStyle.BADGE,
    val masteredShimmer: Boolean = true,
    /** Game logo next to the name (focused banner, details window). */
    val showLogos: Boolean = true,
    /** Console name under the title on the focused wide card. */
    val showConsoleName: Boolean = false,
    val logoPlacement: com.shelf.launcher.data.LogoPlacement = com.shelf.launcher.data.LogoPlacement.LEFT,
    /** Full console names ("PlayStation 2") instead of the short keys. */
    val fullConsoleNames: Boolean = true,
    /** Everything in brackets goes, not just regions and patch tags. */
    val hideAllBrackets: Boolean = false,
    /** Colour of the light sweeping over mastered games. */
    val shimmerStyle: com.shelf.launcher.data.ShimmerStyle = com.shelf.launcher.data.ShimmerStyle.GOLD,
    /** Update 255: names that would look the same as another game's of the same console, with what tells them apart. */
    val lookAlike: Map<String, String> = emptyMap()
) {
    fun console(platform: String): String = when {
        // Collections, franchises and play statuses (category label).
        com.shelf.launcher.data.isCollectionKey(platform) -> com.shelf.launcher.data.collectionLabel(platform)
        fullConsoleNames -> com.shelf.launcher.data.ConsoleNames.display(platform)
        else -> platform
    }

    fun display(name: String): String = when {
        // (Update 255, #74 / #101: two games of one console that would show the same name keep the tag that tells them
        // apart — "Pokemon Silver Version (GB Compatible) [patched]" next to the plain one.)
        (hideAllBrackets || hideRegions) && lookAlike.containsKey(name) -> lookAlike.getValue(name)
        hideAllBrackets -> stripAllBrackets(name)
        hideRegions -> stripRegionTags(name)
        else -> name
    }
}

val LocalTitleOptions = staticCompositionLocalOf { TitleOptions() }

/** How the cards in the row look and where the row sits (Appearance › Game row). */
data class CardLook(
    /** Cover height as a share of the screen height. */
    val heightFraction: Float = com.shelf.launcher.data.CardSize.MEDIUM.heightFraction,
    /** Voltcap row: this many games across the screen (0 = from [heightFraction], i.e. Card size). */
    val rowGames: Int = 0,
    val cornerDp: Int = com.shelf.launcher.data.CardCorners.ROUNDED.radiusDp,
    /** The game you're on becomes a wide capsule (false: it stays a cover, a bit bigger). */
    val focusedWide: Boolean = true,
    val otherGames: com.shelf.launcher.data.OtherGamesLook = com.shelf.launcher.data.OtherGamesLook.NORMAL,
    val focusPosition: com.shelf.launcher.data.FocusPosition = com.shelf.launcher.data.FocusPosition.LEFT,
    /** Time played for the game you're on, under the row (not on the card). */
    val playtimeOnHome: Boolean = true,
    val trophyCorner: com.shelf.launcher.data.CardCorner = com.shelf.launcher.data.CardCorner.TOP_RIGHT,
    val trophyOnBanner: Boolean = true,
    val progressOnBanner: Boolean = true,
    val focusBorder: Boolean = true,
    /** The row starts over when it runs out of games. */
    val loopRow: Boolean = true,
    /** The focused name line goes under the row (resolved from Name side + row position). */
    val nameBelow: Boolean = false,
    /** Other games lean away from the one you're on. */
    val tiltOthers: Boolean = false,
    /** The focused name on a dark pill (old switch; [nameBackdrop] is what's drawn). */
    val nameCapsule: Boolean = false,
    /** What sits behind the focused name. */
    val nameBackdrop: com.shelf.launcher.data.NameBackdrop = com.shelf.launcher.data.NameBackdrop.NONE,
    /** Progress bar above the title on the wide capsule. */
    val progressAbove: Boolean = false,
    val focusEffect: com.shelf.launcher.data.FocusEffect = com.shelf.launcher.data.FocusEffect.ELECTRIC,
    val masteredThemeColor: Boolean = false,
    val achievementsOnCards: Boolean = true,
    val favoriteStar: Boolean = true,
    val bannerButtons: Boolean = true,
    val lastPlayedOnBanner: Boolean = true,
    val infoSpacingDp: Int = 0,
    /** Game look "Tile": covers as squares (Voltcap row). */
    val squareTiles: Boolean = false,
    /** Update 113: game look "Spines": the games as their spines, the one you're on its cover (Voltcap row). */
    val spines: Boolean = false,
    /** Update 115: game look "Wide capsules": every game a real wide capsule (Voltcap row). */
    val capsules: Boolean = false
)

val LocalCardLook = staticCompositionLocalOf { CardLook() }

/**
 * True when the Lite effects are on (Settings › Appearance › Performance, or
 * Automatic on a slower handheld): no continuously animated glows or shimmer,
 * cheap blur from a tiny decode instead of a blur pass, no hero upscaling.
 */
val LocalLiteEffects = staticCompositionLocalOf { false }

private val REGION_WORDS = setOf(
    "usa", "us", "america", "europe", "eu", "eur", "japan", "jp", "jpn", "world", "asia", "korea", "china",
    "taiwan", "hong kong", "australia", "brazil", "canada", "france", "germany", "spain", "italy",
    "netherlands", "sweden", "scandinavia", "uk", "russia", "latin america", "mexico", "portugal",
    "denmark", "norway", "finland", "greece", "poland", "unknown", "u", "e", "j", "ue", "ju", "jue", "eu", "pal", "ntsc",
    "ntsc-u", "ntsc-j", "pal-e"
)

private val PATCH_SUFFIX = Regex(
    """\s*[-+_~]\s*(\d+\s*fps|widescreen|wide screen|16:9)\b.*$""",
    RegexOption.IGNORE_CASE
)

/** Loose trailing extras without brackets: "… QOL Improvements v1.2", "… Bug Fix Patch". */
private val LOOSE_SUFFIX = Regex(
    """(?:\s+|\s*[-+_~:|]\s*)\b(qol|quality[- ]of[- ]life|bug ?fix(?:es)?|patch(?:ed)?|improvements?|""" +
        """v\d+(?:\.\d+)+[a-z]?|ver(?:sion)?\.?\s*\d+(?:\.\d+)*)\b.*$""",
    RegexOption.IGNORE_CASE
)

/** Tags that mark a patched / hacked dump rather than a different game:
 * (60 FPS), [Widescreen Hack], (T+Eng), (Undub), (Patched)… */
private val PATCH_TAG = Regex(
    """\b(\d+\s*fps|widescreen|wide ?screen|16:9|hack(ed|s)?|patch(ed|es)?|translat\w*|t[+-]\w+|undub(bed)?|""" +
        """uncensored|fix(ed|es)?|mod(ded|s)?|improv\w*|enhanced|restor\w*|hd textures?|texture pack|60 ?hz|50 ?hz|""" +
        """qol|quality[- ]of[- ]life|v\d+(\.\d+)*[a-z]?|ver(sion)?\.? ?\d[\w.]*|update\w*|build ?\d+|romhack)\b""",
    RegexOption.IGNORE_CASE
)

private val LANGUAGE_CODE = Regex("^[A-Z][a-z](-[A-Z][a-z])?$")
private val TAG = Regex("""\s*[(\[]([^)\]]*)[)\]]""")

/**
 * "Super Mario World (USA) (En,Fr,De) [!]" → "Super Mario World".
 * Removes region tags, language lists and dump flags; keeps things that tell
 * versions apart, like (Disc 2), (Rev 1) or (Beta).
 */
fun stripRegionTags(name: String): String =
    // (Update 208: each name worked out once — a scrolling carousel of 3D cases asked again for every game it built.)
    REGION_CLEAN.get(name) ?: stripRegionTagsNow(name).also { REGION_CLEAN.put(name, it) }

/** Update 208: names already cleaned, as shown (both ways) — the regexes cost a frame's worth on a long row. */
private val REGION_CLEAN = android.util.LruCache<String, String>(4096)
private val ALL_CLEAN = android.util.LruCache<String, String>(4096)
private val FLAG_ONLY = Regex("^[!abhotfpc]\\d*$")

private fun stripRegionTagsNow(name: String): String {
    val cleaned = TAG.replace(name) { match ->
        val inner = match.groupValues[1].trim()
        val parts = inner.split(',', '/', '+').map { it.trim() }.filter { it.isNotEmpty() }
        val isRegion = parts.isNotEmpty() && parts.all { it.lowercase() in REGION_WORDS }
        val isLanguages = parts.isNotEmpty() && parts.all { LANGUAGE_CODE.matches(it) }
        val lower = inner.lowercase()
        val isPatch = PATCH_TAG.containsMatchIn(lower)
        val isFlag = inner.isEmpty() || inner in setOf("!", "a", "b", "h", "o", "t", "f", "p", "c") ||
            inner.matches(FLAG_ONLY)
        if (isRegion || isLanguages || isPatch || (match.value.trim().startsWith("[") && isFlag)) "" else match.value
    }
    // Loose " - Widescreen Hack" / " + 60 FPS Patch" style suffixes too.
    val noSuffix = cleaned.replace(PATCH_SUFFIX, "").replace(LOOSE_SUFFIX, "")
    return noSuffix.replace(Regex("\\s+"), " ").trim().trimEnd('-', ':', '|', '~').trim().ifBlank { name }
}

/** One bracketed part with no bracket inside it (round, square, curly and full-width brackets). */
private val INNER_TAG = Regex("""\s*[(\[{（【]([^()\[\]{}（）【】]*)[)\]}）】]""")
private val OPEN_TAIL = Regex("""\s*[(\[{（【][^)\]}）】]*$""")
private val STRAY_CLOSE = Regex("""[)\]}）】]""")

/**
 * "Game (USA) (QOL Improvements) (v1.1)" → "Game": every bracketed part goes.
 * (Update 125: brackets inside brackets "(Disc 1 (USA))", curly and full-width ones, and a bracket that never closes
 * "Game (Hack" go too; they used to stay, which is why the switch didn't seem to work on some names.)
 */
fun stripAllBrackets(name: String): String = ALL_CLEAN.get(name) ?: stripAllBracketsNow(name).also { ALL_CLEAN.put(name, it) }

private fun stripAllBracketsNow(name: String): String {
    var s = name
    while (true) {
        val next = INNER_TAG.replace(s, "")
        if (next == s) break
        s = next
    }
    s = STRAY_CLOSE.replace(OPEN_TAIL.replace(s, ""), "")
    val cleaned = stripRegionTags(s)
    return cleaned.ifBlank { stripRegionTags(name) }
}

/**
 * Update 125: Name & logo › Hide everything in brackets for every place a game's name is shown (it only reached the
 * main cards before: popups, the second screen, the shelf spines, the carousels, overlays and the rest used the
 * region-only cleaning). Set from the appearance settings (AppearanceStore); a state, so names redraw when it changes.
 * Matching, sorting and grouping keep [stripRegionTags] (they need "(Disc 2)" and the like).
 */
object TitleStrip {
    val allBrackets = androidx.compose.runtime.mutableStateOf(false)
}

/** A game's name as shown: without region tags, or without anything in brackets when that's switched on. */
fun gameDisplayName(name: String): String =
    if (TitleStrip.allBrackets.value) stripAllBrackets(name) else stripRegionTags(name)

/**
 * Update 255 (#74 / #101): for games whose shown names would be the same as another ROM's of the same console, the shown
 * name plus the bracketed parts only that file has ("[patched]", "(Europe)", "(Rev 1)"). The RP Classic had two Pokémon
 * Silver games both shown as "Pokemon Silver Version (GB Compatible)" — the real one and a patched copy with an empty save
 * — side by side in Continue playing. Games without a look-alike aren't in the map (they show as before).
 */
fun lookAlikeNames(games: List<com.shelf.launcher.data.Game>, hideAll: Boolean): Map<String, String> {
    fun shown(n: String) = if (hideAll) stripAllBrackets(n) else stripRegionTags(n)
    val out = HashMap<String, String>()
    games.asSequence()
        .filter { it.sourceType == com.shelf.launcher.data.SourceType.ROM }
        .groupBy { it.platform.lowercase() + "|" + shown(it.name).lowercase() }
        .values.forEach { group ->
            val names = group.map { it.name }.distinct()
            if (names.size < 2) return@forEach
            val tags = names.associateWith { n -> TAG.findAll(n).map { it.value.trim() }.toList() }
            val common = tags.values.map { it.toSet() }.reduce { a, b -> a intersect b }
            for (n in names) {
                val base = shown(n)
                val extra = tags.getValue(n).filter { it !in common && !base.contains(it) }
                if (extra.isNotEmpty()) out[n] = base + " " + extra.joinToString(" ")
            }
        }
    return out
}

