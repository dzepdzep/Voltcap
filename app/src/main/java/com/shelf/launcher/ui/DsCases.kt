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
 */
internal enum class DsCaseVariant(val label: String, val detail: String, val region: CaseRegion, val gbaHolder: Boolean, val eco: Boolean) {
    NA_GBA("North America · black, with GBA holder", "Black 15 mm case: DS card holder, GBA cartridge holder under it.", CaseRegion.US, true, false),
    NA_ECO("North America · eco case, no GBA holder", "Later black eco case (from late 2010): card holder only, recycling cut-outs.", CaseRegion.US, false, true),
    EU_CLEAR("Europe · clear, with GBA holder", "Clear 20 mm PAL case: 4 × 4 cm card holder, 6 × 2.5 cm GBA holder.", CaseRegion.EU, true, false),
    EU_BLACK("Europe · black (Asian-made), with GBA holder", "Black 15 mm case found on some PAL games, GBA holder present.", CaseRegion.EU, true, false),
    JP("Japan · black, with GBA holder", "As the North American black case.", CaseRegion.JP, true, false)
}

/** How sure a game's case is. */
internal enum class DsCaseStatus(val label: String) {
    PICKED("Your choice"),
    VERIFIED("Verified"),
    REPORTED("Reported by collectors — not verified"),
    DEFAULT("Default for its region and year — not verified"),
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

    /** The evidence, as Toni gathered it and as checked (Reddit could not be opened from here: those stay "reported"). */
    val evidence: List<DsCaseEvidence> = listOf(
        DsCaseEvidence("mario vs donkey kong miniland mayhem", CaseRegion.US, false, DsCaseStatus.VERIFIED, "high",
            listOf(NINTENDO_LIFE_ECO), "Contemporary report (Nov 2010): the first DS game in the eco case, GBA slot removed."),
        DsCaseEvidence("pokemon platinum", null, true, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1p4aclb/"), "Owner photograph showing the GBA holder."),
        DsCaseEvidence("kingdom hearts 3582 days", null, true, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1ryj5ux/"), "A collector's checked case had the GBA holder."),
        DsCaseEvidence("super mario 64 ds", null, true, DsCaseStatus.REPORTED, "low",
            emptyList(), "Early-case collector reports."),
        DsCaseEvidence("pokemon diamond", CaseRegion.EU, true, DsCaseStatus.REPORTED, "low",
            emptyList(), "UK PAL owner report; whether that case is original isn't confirmed."),
        DsCaseEvidence("kirby mass attack", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/gamecollecting/comments/wyu0o9/"), "Collector report of the later case without the holder."),
        DsCaseEvidence("pokemon black version 2", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/nds/comments/17edbew/"), "Owner report: no holder, recycling cut-out."),
        DsCaseEvidence("pokemon black 2", CaseRegion.US, false, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/nds/comments/17edbew/"), "Owner report: no holder, recycling cut-out."),
        DsCaseEvidence("mario kart ds", null, null, DsCaseStatus.REPORTED, "medium",
            listOf("https://www.reddit.com/r/gamecollecting/comments/vnkr67/"), "Both kinds: the early black case with the holder, a later red-case reprint without."),
        DsCaseEvidence("chrono trigger", null, null, DsCaseStatus.REPORTED, "low",
            listOf("https://www.reddit.com/r/NintendoDS/comments/1ryj5ux/"), "A collector reports no holder; whether that case is original is uncertain."),
    )

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
        val e = evidenceFor(g, region)
        if (e?.hasGba != null) {
            val v = when (region) {
                CaseRegion.US -> if (e.hasGba) DsCaseVariant.NA_GBA else DsCaseVariant.NA_ECO
                CaseRegion.EU -> DsCaseVariant.EU_CLEAR
                CaseRegion.JP -> DsCaseVariant.JP
            }
            // (A PAL or Japanese game reported without a holder isn't drawn without one: no such case is documented.)
            if (region == CaseRegion.US || e.hasGba) return v to e.status
        }
        return defaultFor(g, region) to DsCaseStatus.DEFAULT
    }

    /** By region; in North America by release year — the eco case from 2011 on (2010 releases stay on the older case). */
    fun defaultFor(g: Game, region: CaseRegion): DsCaseVariant = when (region) {
        CaseRegion.EU -> DsCaseVariant.EU_CLEAR
        CaseRegion.JP -> DsCaseVariant.JP
        CaseRegion.US -> if ((years[g.stableKey] ?: 0) >= 2011) DsCaseVariant.NA_ECO else DsCaseVariant.NA_GBA
    }

    /** [base] (the region's DS case) as [v] draws it. */
    fun spec(base: CaseSpec, v: DsCaseVariant): CaseSpec {
        val black = v != DsCaseVariant.EU_CLEAR
        val t = if (v == DsCaseVariant.EU_CLEAR) 20f else 15f
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
