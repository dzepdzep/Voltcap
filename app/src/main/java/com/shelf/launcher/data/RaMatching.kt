package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * On-device copy of RetroAchievements' per-console game lists (titles + every
 * accepted hash). The lists are large and rarely change, and RetroAchievements
 * asks apps to cache them, so each console's list is fetched at most once a
 * week. Stored as files — some lists are too big for SharedPreferences.
 */
class RaGameListCache(context: Context) {
    private val dir = AppStorage.dir(context, "Data/RA game lists", File(context.filesDir, "ra_game_lists")).apply { mkdirs() }
    private val gson = Gson()
    private val type = object : TypeToken<CachedList>() {}.type

    private data class CachedList(val fetchedAt: Long = 0, val games: List<RaListedGame>? = null)

    /** The cached list if it's younger than [maxAgeMs], else null. */
    fun get(consoleId: Int, maxAgeMs: Long): List<RaListedGame>? {
        val file = File(dir, "$consoleId.json")
        if (!file.isFile) return null
        val cached = runCatching { gson.fromJson<CachedList>(file.readText(), type) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - cached.fetchedAt > maxAgeMs) return null
        return cached.games
    }

    fun put(consoleId: Int, games: List<RaListedGame>) {
        runCatching {
            File(dir, "$consoleId.json").writeText(gson.toJson(CachedList(System.currentTimeMillis(), games)))
        }
    }

    companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * Title-based fallback for files that can't be hashed (CHD, CSO…) or whose
 * hash RetroAchievements doesn't know.
 *
 * Two fixes over plain title comparison:
 *  - Subsets ("Game [Subset - Bonus]"), hacks and other tagged entries share
 *    their base title with the main game once brackets are stripped. The main
 *    set is always preferred, and subsets/hacks are never picked by the fuzzy
 *    pass.
 *  - Near-misses in spelling ("Going" vs RetroAchievements' "Goin'") are
 *    accepted when they're within a small edit distance and unambiguous.
 */
object RaTitleMatcher {

    class Index internal constructor(internal val byKey: Map<String, Pair<RaListedGame, Int>>)

    fun buildIndex(games: List<RaListedGame>): Index {
        val byKey = HashMap<String, Pair<RaListedGame, Int>>()
        games.forEach { game ->
            val title = game.Title ?: return@forEach
            if (game.ID == null) return@forEach
            val key = normalise(title)
            if (key.isEmpty()) return@forEach
            val rank = rank(title)
            val current = byKey[key]
            if (current == null || rank < current.second) byKey[key] = game to rank
        }
        return Index(byKey)
    }

    fun match(localTitle: String, index: Index): RaListedGame? {
        val key = normalise(localTitle)
        if (key.isEmpty()) return null
        index.byKey[key]?.let { return it.first }

        // Fuzzy pass: compare with spaces removed, only against main entries.
        val compact = key.replace(" ", "")
        if (compact.length < 6) return null
        val limit = maxOf(1, compact.length / 10)
        var best: RaListedGame? = null
        var bestDistance = Int.MAX_VALUE
        var tie = false
        for ((candidateKey, pair) in index.byKey) {
            if (pair.second >= 2) continue // subsets and hacks
            val other = candidateKey.replace(" ", "")
            if (kotlin.math.abs(other.length - compact.length) > limit) continue
            val d = distance(compact, other, limit)
            if (d > limit) continue
            when {
                d < bestDistance -> { best = pair.first; bestDistance = d; tie = false }
                d == bestDistance -> tie = true
            }
        }
        return if (tie) null else best
    }

    /** 0 = main set, 1 = tagged (~Homebrew~, ~Prototype~…), 2 = hack, 3 = subset. */
    private fun rank(title: String): Int = when {
        title.contains("[subset", ignoreCase = true) -> 3
        title.contains("~hack~", ignoreCase = true) -> 2
        title.contains('~') -> 1
        else -> 0
    }

    /** Strips what differs between a ROM filename and a database title:
     * region/language/subset tags, ~Tag~ prefixes, punctuation, apostrophes,
     * leading articles. "&" reads as "and". */
    fun normalise(raw: String): String {
        var t = raw.lowercase()
        t = t.replace(Regex("~[^~]*~"), " ")
        t = t.replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        t = t.replace("&", " and ")
        t = t.replace("'", "").replace("’", "")
        t = t.replace(Regex("[^a-z0-9 ]"), " ")
        t = t.replace(Regex("\\b(the|a|an)\\b"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** Levenshtein distance, giving up early once it can't be within [limit]. */
    private fun distance(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
