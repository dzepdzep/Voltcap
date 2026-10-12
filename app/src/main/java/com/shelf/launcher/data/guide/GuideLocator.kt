package com.shelf.launcher.data.guide

import kotlin.math.ln

data class LocatorInput(
    val richPresence: String?,
    val recentUnlockTitles: List<String> = emptyList(),
    val recentUnlockIds: Set<Int> = emptySet(),
    val currentSectionId: String? = null,
    val furthestSectionId: String? = null,
    /** Normalized Rich Presence key -> section id (GameGuideProgress.corrections). */
    val corrections: Map<String, String> = emptyMap()
)

data class LocatedSection(
    val sectionId: String,
    val order: Int,
    /** 0..1. */
    val confidence: Float,
    /** Human-readable, e.g. "Rich Presence mentions 'Viridian Forest'". */
    val reason: String,
    /** Earlier than the furthest section reached. */
    val isRevisit: Boolean = false,
    val fromCorrection: Boolean = false
)

data class LocatorResult(
    /** Null when unsure: the UI should keep the last position. */
    val best: LocatedSection?,
    /** Ranked, most likely first (also filled when unsure, so the user can pick one). */
    val candidates: List<LocatedSection>,
    val unsure: Boolean,
    /** Key to store a manual correction under. */
    val correctionKey: String
)

/**
 * Estimates the current guide section from Rich Presence text plus progression. Pure and
 * deterministic: the same inputs always give the same ranking. Never guesses below [THRESHOLD].
 */
class GuideLocator(private val guide: Guide) {
    private val titles = guide.sections.map { " " + GuideText.normalize(it.title) + " " }
    private val bodies = guide.sections.map { GuideText.normalizeLines(it.text) }

    fun locate(input: LocatorInput): LocatorResult {
        val key = correctionKey(input.richPresence)
        val secs = guide.sections
        val far = guide.orderOf(input.furthestSectionId)
        val cur = guide.orderOf(input.currentSectionId)

        input.corrections[key]?.takeIf { key.isNotEmpty() }?.let { id ->
            val idx = guide.orderOf(id)
            if (idx >= 0) {
                val loc = LocatedSection(id, idx, 1f, "You corrected this before", isRevisit = far >= 0 && idx < far, fromCorrection = true)
                return LocatorResult(loc, listOf(loc), false, key)
            }
        }
        val n = secs.size
        if (n == 0) return LocatorResult(null, emptyList(), true, key)

        val grams = rpGrams(input.richPresence)
        val g = grams.size
        val tHas = Array(n) { s -> BooleanArray(g) { titles[s].contains(" ${grams[it]} ") } }
        val bHas = Array(n) { s -> BooleanArray(g) { bodies[s].contains(" ${grams[it]} ") } }
        val dfT = IntArray(g) { j -> (0 until n).count { tHas[it][j] } }
        val dfB = IntArray(g) { j -> (0 until n).count { bHas[it][j] } }
        fun w(j: Int) = if (grams[j].contains(' ')) 2.0 else 1.0
        val idfMax = ln(1.0 + n)
        var maxScore = 0.0
        for (j in 0 until g) if (dfT[j] > 0 || dfB[j] > 0) maxScore += w(j) * idfMax

        val unlockTitles = input.recentUnlockTitles.map { GuideText.normalize(it) }.filter { it.length >= 4 }
        class Sc(val idx: Int, var base: Double, val strength: Double, val gramText: String?, val bodyOnly: Boolean)
        val scored = ArrayList<Sc>()
        for (s in 0 until n) {
            var t = 0.0; var b = 0.0; var best: String? = null; var bestLen = 0
            for (j in 0 until g) {
                if (tHas[s][j]) {
                    t += w(j) * ln(1.0 + n.toDouble() / dfT[j])
                    if (grams[j].length > bestLen) { best = grams[j]; bestLen = grams[j].length }
                }
                if (bHas[s][j]) b += BODY_WEIGHT * w(j) * ln(1.0 + n.toDouble() / dfB[j])
            }
            val base = if (maxScore > 0) minOf(1.0, (t + b) / maxScore) else 0.0
            scored.add(Sc(s, base, if (maxScore > 0) t / maxScore else 0.0, best, t == 0.0))
        }
        // Several sections matching about equally well means the text alone can't tell them apart.
        val top = scored.maxOfOrNull { it.base } ?: 0.0
        val tied = scored.filter { it.base >= 0.2 && top - it.base < 0.05 }
        val ambiguous = tied.size > 1
        if (ambiguous) tied.forEach { it.base *= 0.5 }

        val located = ArrayList<LocatedSection>()
        for (sc in scored) {
            val s = secs[sc.idx]
            val reasons = ArrayList<String>()
            var conf = sc.base
            if (sc.base > 0) {
                if (sc.gramText != null) reasons.add("Rich Presence mentions '${titleCase(sc.gramText)}'")
                else reasons.add("Rich Presence terms appear in this section's text")
                if (ambiguous && sc in tied) {
                    tied.firstOrNull { it !== sc }?.let { reasons.add("also matches '${secs[it.idx].title}'") }
                }
            }
            // Achievements the player just unlocked and this section talks about.
            val hit = s.mentionedAchievementIds.any { it in input.recentUnlockIds } ||
                unlockTitles.any { titles[sc.idx].contains(" $it ") || bodies[sc.idx].contains(" $it ") }
            if (hit) { conf += UNLOCK_BOOST; reasons.add("mentions an achievement you just unlocked") }
            if (sc.base > 0) {
                if (far >= 0 && sc.idx == far + 1 && sc.base >= 0.2) { conf += NEXT_BOOST; reasons.add("follows your furthest section") }
                if (sc.idx == cur) conf += CURRENT_BOOST
            }
            val revisit = far >= 0 && sc.idx < far
            if (revisit) {
                if (sc.strength >= STRONG) reasons.add("returning to an earlier section")
                else { conf *= BACKWARD_PENALTY; reasons.add("earlier than your furthest section") }
            }
            conf = conf.coerceIn(0.0, 1.0)
            if (conf > 0.05) located.add(LocatedSection(s.id, sc.idx, conf.toFloat(), reasons.joinToString("; "), revisit))
        }
        val ranked = located.sortedWith(compareByDescending<LocatedSection> { it.confidence }.thenBy { it.order }).take(MAX_CANDIDATES)
        val best = ranked.firstOrNull()?.takeIf { it.confidence >= THRESHOLD }
        return LocatorResult(best, ranked, best == null, key)
    }

    companion object {
        const val THRESHOLD = 0.4f
        private const val STRONG = 0.6
        private const val BODY_WEIGHT = 0.2
        private const val UNLOCK_BOOST = 0.3
        private const val NEXT_BOOST = 0.08
        private const val CURRENT_BOOST = 0.03
        private const val BACKWARD_PENALTY = 0.6
        private const val MAX_CANDIDATES = 5

        /** Words that say nothing about where the player is. */
        private val GENERIC = setOf(
            "exploring", "explore", "in", "at", "on", "of", "to", "a", "an", "the", "and", "with", "is", "playing", "walking",
            "battle", "battling", "fighting", "currently", "lv", "lvl", "level", "hp", "money", "badge", "badges",
            "party", "menu", "title", "screen", "location"
        )
        /** A number followed by one of these is a stat, not part of a place name. */
        private val UNITS = setOf("badge", "badges", "hp", "pokemon", "lives", "coins", "gold", "money", "exp", "pts", "points", "x")
        private val SEGMENT_SPLIT = Regex("[|;,\\n\\r/:\\u2022\\u00B7\\u2013\\u2014]+")

        /** Tokens per segment of a Rich Presence string: generic words dropped, numbers kept only right after a place word. */
        internal fun rpSegments(rp: String?): List<List<String>> {
            if (rp.isNullOrBlank()) return emptyList()
            return rp.split(SEGMENT_SPLIT).mapNotNull { seg ->
                val words = GuideText.normalize(seg).split(' ').filter { it.isNotEmpty() }
                val out = ArrayList<String>()
                words.forEachIndexed { i, wd ->
                    if (wd.all { it.isDigit() }) {
                        val prevKept = i > 0 && out.isNotEmpty() && out.last() == words[i - 1] && !words[i - 1].all { it.isDigit() }
                        val unit = words.getOrNull(i + 1) in UNITS
                        if (prevKept && !unit) out.add(wd)
                    } else if (wd !in GENERIC) out.add(wd)
                }
                out.takeIf { it.isNotEmpty() }
            }
        }

        /** Stable key for storing a manual correction for this Rich Presence text. */
        fun correctionKey(rp: String?): String = rpSegments(rp).joinToString(" | ") { it.joinToString(" ") }

        /** Unigrams (words) and bigrams (adjacent word pairs) of the Rich Presence, in first-seen order. */
        internal fun rpGrams(rp: String?): List<String> {
            val out = LinkedHashSet<String>()
            for (seg in rpSegments(rp)) {
                for (t in seg) if (!t.all { it.isDigit() }) out.add(t)
                for (i in 0 until seg.size - 1) out.add(seg[i] + " " + seg[i + 1])
            }
            return out.toList()
        }

        private fun titleCase(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
    }
}
