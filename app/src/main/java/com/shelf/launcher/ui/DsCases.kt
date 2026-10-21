package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game

/*
 * Update 287 (Toni: "Nintendo DS case accuracy"): which Nintendo DS case a game is drawn in. One model per region used to
 * stand for every DS game; real cases differ by region, by production period and by printing:
 *
 *  - North America, until late 2010: black, 135 × 122 × 15 mm, the DS card holder and a moulded Game Boy Advance cartridge
 *    holder under it.
 *  - North America, eco case (from November 2010; Mario vs. Donkey Kong: Mini-Land Mayhem! was reported as the first): no
 *    GBA holder, less plastic, recycling cut-outs and the recycle mark.
 *  - Europe: clear polypropylene, 13.5 × 12 × 2 cm, a 4 × 4 cm card holder with four clips, a 6 × 2.5 cm GBA holder with
 *    two side clips, two 2.5 cm manual tongues, "CE D-63760" (made in Großostheim).
 *  - Europe, black: an Asian-made case found on some PAL games, 13.5 × 12.3 × 1.5 cm, "GAME CARD" moulded at the card
 *    holder, GBA holder present.
 *  - Japan: as the North American black case.
 *
 * A game's case: your choice for it (the game's menu › DS case…), else the evidence below for that game and region,
 * else a default by region and release year — shown as an unverified default, never as a fact about a printing. Unknown
 * stays unknown: a game with copies of both kinds (Mario Kart DS) keeps the default until you pick one. Only how the
 * case is drawn: the GBA holder is a moulded recess, never an unrelated cartridge, and has nothing to do with the
 * console's Slot-2.
 *
 * Update 296 (Toni: "a GBA cartridge holder appears in every DS case — show it only where the real retail case had one"):
 * the cause was the defaults — every game without evidence was drawn in its region's case WITH the holder (Europe and
 * Japan always; North America for anything before 2011, and for every game whose release year wasn't known yet). Now:
 *  - a holder is drawn only for a game whose evidence says its case had one (PRESENT), for that region;
 *  - a game whose evidence says it hadn't (ABSENT) gets that region's slotless case (the eco case in North America from
 *    2011, else the plain case with just the card holder);
 *  - everything else is UNKNOWN: no holder is invented — the region's plain case with only the card holder (a documented
 *    fallback, shown as "unknown" in the game's menu › DS case…, where any case can still be picked);
 *  - evidence names its region; it's never copied to the game's other regional editions;
 *  - a game with genuine variants of both kinds (an early printing with the holder, a later reprint without) stays
 *    unknown until you pick the one you have.
 * The research and every source: docs/claude/DS_CASES.md.
 */
internal enum class DsCaseVariant(val label: String, val detail: String, val region: CaseRegion, val gbaHolder: Boolean, val eco: Boolean) {
    NA_GBA("North America · black, with GBA holder", "Black 15 mm case: DS card holder, GBA cartridge holder under it.", CaseRegion.US, true, false),
    NA_ECO("North America · eco case, no GBA holder", "Later black eco case (from late 2010): card holder only, recycling cut-outs.", CaseRegion.US, false, true),
    EU_CLEAR("Europe · clear, with GBA holder", "Clear 20 mm PAL case: 4 × 4 cm card holder, 6 × 2.5 cm GBA holder.", CaseRegion.EU, true, false),
    EU_BLACK("Europe · black (Asian-made), with GBA holder", "Black 15 mm case found on some PAL games, GBA holder present.", CaseRegion.EU, true, false),
    JP("Japan · black, with GBA holder", "As the North American black case.", CaseRegion.JP, true, false),
    // Update 296: the same cases without the GBA holder — the DS card holder only (no mounting, recess or clips left behind).
    NA_PLAIN("North America · black, no GBA holder", "Black 15 mm case with the DS card holder only (not the eco case).", CaseRegion.US, false, false),
    EU_CLEAR_PLAIN("Europe · clear, no GBA holder", "Clear 20 mm PAL case with the 4 × 4 cm card holder only.", CaseRegion.EU, false, false),
    JP_PLAIN("Japan · black, no GBA holder", "Black 15 mm case with the DS card holder only.", CaseRegion.JP, false, false)
}

/** How sure a game's case is. */
internal enum class DsCaseStatus(val label: String) {
    PICKED("Your choice"),
    VERIFIED("Verified"),
    REPORTED("Reported by collectors — not verified"),
    DEFAULT("Default for its region and year — not verified"),
    /** Update 296: nothing found for this game in this region — no GBA holder is drawn (the plain case). */
    UNKNOWN("Unknown — no evidence for this game and region, so no GBA holder is drawn"),
}

/**
 * One piece of evidence: games whose name holds [match] (lower case, punctuation removed), in [region] (null: any),
 * [hasGba] true / false, or null when copies of both kinds are reported. Exact sources, so they can be checked.
 */
internal class DsCaseEvidence(
    val match: String, val region: CaseRegion?, val hasGba: Boolean?, val status: DsCaseStatus,
    val confidence: String, val urls: List<String>, val note: String
)

internal object DsCases {
    private const val NINTENDO_LIFE_ECO = "https://www.nintendolife.com/forums/ds/ds_gets_its_eco_friendly_box"
    private const val PADERETRO = "https://paderetro.com/2014/03/24/tout-savoir-sur-les-jeux-nintendo-ds-euro/"

    private const val CAG_ECO = "https://www.cheapassgamer.com/threads/eco-friendly-ds-cases-no-more-gba-slot.246662/"

    /**
     * The evidence (Update 296: every entry names its region — none is copied to another regional edition; [hasGba]
     * null = genuine copies of both kinds are reported). Reddit and Stack Exchange could not be opened from the research
     * session (blocked), so what comes only from them stays REPORTED, as Toni passed it on; the North American region of
     * those posts is assumed from the posts' English-language US context and says so in the note. Details and the
     * conflicts: docs/claude/DS_CASES.md.
     */
    val evidence: List<DsCaseEvidence> = listOf(
        // ---- North America ----
        DsCaseEvidence("mario vs donkey kong miniland mayhem", CaseRegion.US, false, DsCaseStatus.VERIFIED, "high",
            listOf(NINTENDO_LIFE_ECO), "Contemporary report (Nov 2010): the first DS game in the eco case, GBA slot removed."),
        DsCaseEvidence("golden sun dark dawn", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf(CAG_ECO), "Buyer report, Dec 2010 (US retailers): \"My new Golden Sun game came that way\" — the eco case, no GBA slot."),
        DsCaseEvidence("super mario 64 ds", CaseRegion.US, null, DsCaseStatus.REPORTED, "medium",
            listOf(CAG_ECO), "Both kinds: the 2004 printing in the black case with the holder (collector reports), and a reprint bought Jan 2011 \"came in an eco case\". Pick the one you have."),
        DsCaseEvidence("pokemon platinum", CaseRegion.US, true, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1p4aclb/whats_supposed_to_go_here/"), "Owner photograph of the GBA holder (\"what's supposed to go here?\"). Region assumed North America (English-language post; the page couldn't be opened to confirm)."),
        DsCaseEvidence("animal crossing wild world", CaseRegion.US, true, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1dk2hqa/"), "Owner photograph of the case with the GBA holder. Region assumed North America (not confirmed)."),
        DsCaseEvidence("kingdom hearts 3582 days", CaseRegion.US, null, DsCaseStatus.REPORTED, "low",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1ryj5ux/nintendo_ds_game_case/"), "Conflicting: one collector's original case had the holder, another copy hasn't — possibly a replacement case. Region assumed North America."),
        DsCaseEvidence("chrono trigger", CaseRegion.US, null, DsCaseStatus.REPORTED, "low",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1ryj5ux/nintendo_ds_game_case/"), "A slotless copy is reported, but whether that case is original is uncertain. Region assumed North America."),
        DsCaseEvidence("shin megami tensei devil survivor 2", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/nds/comments/vfg30c/"), "Collectors report copies without the GBA holder. Region assumed North America (Atlus USA)."),
        DsCaseEvidence("shin megami tensei devil survivor", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/nds/comments/vfg30c/"), "Collectors report copies without the GBA holder. Region assumed North America (Atlus USA)."),
        DsCaseEvidence("kirby mass attack", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/gamecollecting/comments/wyu0o9/"), "Collector report of the later case without the holder."),
        DsCaseEvidence("pokemon black version 2", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/nds/comments/17edbew/"), "Owner report: no holder, recycling cut-out (the eco case)."),
        DsCaseEvidence("pokemon black 2", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/nds/comments/17edbew/"), "Owner report: no holder, recycling cut-out (the eco case)."),
        DsCaseEvidence("mario kart ds", CaseRegion.US, null, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/gamecollecting/comments/vnkr67/"), "Both kinds: the early black case with the holder, a later red-case reprint without. Pick the one you have."),
        // ---- Europe ----
        DsCaseEvidence("pokemon diamond", CaseRegion.EU, true, DsCaseStatus.REPORTED, "low",
            emptyList(), "UK PAL owner report; whether that case is original isn't confirmed."),
        // The black, Asian-made PAL cases (they carry a GBA slot with a cartridge-shaped logo), by name in Paderetro's list.
        DsCaseEvidence("geometry wars galaxy", CaseRegion.EU, true, DsCaseStatus.REPORTED, "medium",
            listOf(PADERETRO), "Listed (EUR) among the PAL games in the black Asian-made case, which has the GBA slot."),
        DsCaseEvidence("42 jeux indemodables", CaseRegion.EU, true, DsCaseStatus.REPORTED, "medium",
            listOf(PADERETRO), "Listed (FRA) among the PAL games in the black Asian-made case, which has the GBA slot."),
        DsCaseEvidence("english training", CaseRegion.EU, true, DsCaseStatus.REPORTED, "medium",
            listOf(PADERETRO), "Listed (FRA) among the PAL games in the black Asian-made case, which has the GBA slot."),
        DsCaseEvidence("entrainement cerebral", CaseRegion.EU, true, DsCaseStatus.REPORTED, "medium",
            listOf(PADERETRO), "\"Programme d'entraînement cérébral du Dr Kawashima\" (FRA): listed among the PAL games in the black Asian-made case, which has the GBA slot."),
        DsCaseEvidence("mundo misterioso equipo de rescate azul", CaseRegion.EU, true, DsCaseStatus.REPORTED, "medium",
            listOf(PADERETRO), "Spanish (ESP) edition: listed among the PAL games in the black Asian-made case, which has the GBA slot."),
    )

    /** Update 296: the North American games reported in the eco case (recycling cut-outs) rather than a plain slotless one. */
    private val naEco = setOf("mario vs donkey kong miniland mayhem", "golden sun dark dawn", "kirby mass attack", "pokemon black version 2", "pokemon black 2")

    /** Update 296: the evidence's black PAL games are drawn in the black PAL case (the source lists them as such). */
    private val euBlack = setOf("geometry wars galaxy", "42 jeux indemodables", "english training", "entrainement cerebral", "mundo misterioso equipo de rescate azul")

    /** Release years by game key (set by the home screen from the metadata), for the default. */
    @Volatile var years: Map<String, Int> = emptyMap()

    // Your choices: game key → variant name (a plain map, loaded at start — see CartShellStore for why).
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val version = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile private var loaded = false

    fun warmUp() { load() }

    @Synchronized private fun fill() {
        if (loaded) return
        Features.dsCaseChoices.value.split('\n').forEach { line ->
            val i = line.indexOf('\t')
            if (i > 0) map[line.substring(0, i)] = line.substring(i + 1)
        }
        loaded = true
    }

    private fun load() { if (!loaded) fill(); runCatching { version.intValue } }

    fun picked(key: String): DsCaseVariant? { load(); return map[key]?.let { n -> DsCaseVariant.values().firstOrNull { it.name == n } } }

    fun setPicked(key: String, v: DsCaseVariant?) {
        load()
        if (v == null) map.remove(key) else map[key] = v.name
        Features.dsCaseChoices.value = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        changed()
        version.intValue++
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        .lowercase().replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()

    /** The evidence for [g] in [region] (the longest match), or null. */
    fun evidenceFor(g: Game, region: CaseRegion): DsCaseEvidence? {
        val n = norm(gameDisplayName(g.name))
        return evidence.filter { e -> n.contains(e.match) && (e.region == null || e.region == region) }.maxByOrNull { it.match.length }
    }

    /** Each game's answer, worked out once (the name matching isn't free) until a choice or the years change. */
    private val known = android.util.LruCache<String, Pair<DsCaseVariant, DsCaseStatus>>(512)

    /** The release years or a choice changed: work the answers out again (CaseCatalog keys its cases by the answer). */
    fun changed() { known.evictAll() }

    /** [g]'s case, and how sure that is. */
    fun of(g: Game, region: CaseRegion): Pair<DsCaseVariant, DsCaseStatus> {
        load()
        val k = "${g.stableKey}|${region.name}"
        known.get(k)?.let { return it }
        return ofNow(g, region).also { known.put(k, it) }
    }

    private fun ofNow(g: Game, region: CaseRegion): Pair<DsCaseVariant, DsCaseStatus> {
        picked(g.stableKey)?.let { return it to DsCaseStatus.PICKED }
        // (Update 296: only evidence for this game in this region; PRESENT → the holder, ABSENT → that region's slotless
        // case, both kinds or nothing → UNKNOWN: the plain case, no holder invented.)
        val e = evidenceFor(g, region)
        val has = e?.hasGba
        if (e != null && has != null) {
            val v = when (region) {
                CaseRegion.US -> if (has) DsCaseVariant.NA_GBA else if ((years[g.stableKey] ?: 0) >= 2011 || e.match in naEco) DsCaseVariant.NA_ECO else DsCaseVariant.NA_PLAIN
                CaseRegion.EU -> if (!has) DsCaseVariant.EU_CLEAR_PLAIN else if (e.match in euBlack) DsCaseVariant.EU_BLACK else DsCaseVariant.EU_CLEAR
                CaseRegion.JP -> if (has) DsCaseVariant.JP else DsCaseVariant.JP_PLAIN
            }
            return v to e.status
        }
        return defaultFor(g, region) to DsCaseStatus.UNKNOWN
    }

    /**
     * Update 296: a game with nothing known for its region — never a holder: the region's plain case. In North America
     * from 2011 that's the eco case (documented: Nintendo's DS games moved to it from Nov 2010), before then the plain black
     * case with the card holder only.
     */
    fun defaultFor(g: Game, region: CaseRegion): DsCaseVariant = when (region) {
        CaseRegion.EU -> DsCaseVariant.EU_CLEAR_PLAIN
        CaseRegion.JP -> DsCaseVariant.JP_PLAIN
        CaseRegion.US -> if ((years[g.stableKey] ?: 0) >= 2011) DsCaseVariant.NA_ECO else DsCaseVariant.NA_PLAIN
    }

    /** [base] (the region's DS case) as [v] draws it. */
    fun spec(base: CaseSpec, v: DsCaseVariant): CaseSpec {
        val clear = v == DsCaseVariant.EU_CLEAR || v == DsCaseVariant.EU_CLEAR_PLAIN
        val black = !clear
        val t = if (clear) 20f else 15f
        val h = if (v == DsCaseVariant.EU_BLACK) 123f else base.hMm
        return CaseSpec(
            base.platform, base.region, base.shell, base.wMm, h, t,
            if (black) 0xFF111111.toInt() else 0xFFEEF2F5.toInt(), if (black) 1f else 0.2f, if (black) 0xFF111111.toInt() else 0xFFEEF2F5.toInt(),
            base.media, base.mediaWMm, base.mediaLeft, base.mediaU, base.mediaV, base.memCard, v.gbaHolder, base.grip,
            base.check, v.detail, eco = v.eco
        )
    }
}

/**
 * Update 287: the game's menu › DS case…: the case it's drawn in — as it is now and how sure that is (with the evidence
 * and its sources), and each case to pick for it; "As found" goes back to the evidence / the default.
 */
@androidx.compose.runtime.Composable
internal fun DsCasePicker(game: Game, onDismiss: () -> Unit) {
    val region = androidx.compose.runtime.remember(game.stableKey) { CaseCatalog.regionOf(game.romPath ?: game.name) }
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val (now, status) = androidx.compose.runtime.remember(tick) { DsCases.of(game, region) }
    val e = androidx.compose.runtime.remember(game.stableKey) { DsCases.evidenceFor(game, region) }
    ShelfMenuDialog(
        title = "DS case",
        subtitle = "${gameDisplayName(game.name)} · only how its case is drawn",
        onDismiss = onDismiss,
        icon = androidx.compose.material.icons.Icons.Filled.ViewInAr
    ) {
        MutedNote("Now: ${now.label} — ${status.label}." + (e?.let { "\nFound: ${it.note}" + (if (it.urls.isNotEmpty()) "\n" + it.urls.joinToString("\n") else "") } ?: ""))
        MenuRow(androidx.compose.material.icons.Icons.Filled.Refresh, "As found", "The evidence for this game, else ${DsCases.defaultFor(game, region).label} (its region's default)") {
            DsCases.setPicked(game.stableKey, null); tick++
        }
        DsCaseVariant.values().forEach { v ->
            MenuRow(androidx.compose.material.icons.Icons.Filled.ViewInAr, (if (status == DsCaseStatus.PICKED && v == now) "✓ " else "") + v.label, v.detail) {
                DsCases.setPicked(game.stableKey, v); tick++
            }
        }
    }
}
