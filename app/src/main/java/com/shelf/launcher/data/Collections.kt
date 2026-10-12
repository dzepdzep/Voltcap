package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Collections: your own lists ("Backlog", "Couch co-op"), franchises found
 * automatically ("Final Fantasy", "Pokémon"), and play statuses (Playing,
 * Finished…). All three show up as categories next to the consoles — under
 * the COLLECTIONS entry of the category order — and each can be switched
 * off (Settings › Library › Collections & status).
 *
 * Keys work like platform keys in the category bar: "col:<name>" for yours,
 * "fr:<name>" for franchises, "st:<label>" for statuses. The part after the
 * colon is what's shown.
 */
data class ShelfCollection(val key: String, val name: String, val kind: Kind, val keys: Set<String>) {
    enum class Kind { USER, FRANCHISE, STATUS, GENRE }
}

/** Where you are with a game (Start › Status). */
enum class PlayStatus(val label: String, val glyph: String) {
    PLAYING("Playing", "▶"),
    FINISHED("Finished", "✓"),
    DROPPED("Dropped", "✕"),
    WISHLIST("Wishlist", "★"),
    /** Owned and lined up to play next (the backlog view is its category). */
    BACKLOG("Backlog", "☰")
}

/** A status and when it was set (for "finished this week"). */
data class StatusEntry(val status: PlayStatus, val at: Long)

fun isCollectionKey(key: String?): Boolean =
    key != null && (key.startsWith("col:") || key.startsWith("fr:") || key.startsWith("st:") || key.startsWith("ge:"))

/** "col:Backlog" → "Backlog". */
fun collectionLabel(key: String): String = key.substringAfter(':')

class CollectionsStore(context: Context) {
    private val prefs = context.getSharedPreferences("collections", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val userType = object : TypeToken<Map<String, Set<String>>>() {}.type
    private val statusType = object : TypeToken<Map<String, StatusEntry>>() {}.type

    /** Your collections: name → game keys. */
    fun loadUser(): Map<String, Set<String>> =
        prefs.getString("user_json", null)?.let { runCatching { gson.fromJson<Map<String, Set<String>>>(it, userType) }.getOrNull() }
            ?: emptyMap()

    fun saveUser(map: Map<String, Set<String>>) {
        prefs.edit().putString("user_json", gson.toJson(map)).apply()
    }

    @Suppress("SENSELESS_COMPARISON")
    fun loadStatuses(): Map<String, StatusEntry> =
        (prefs.getString("status_json", null)?.let { runCatching { gson.fromJson<Map<String, StatusEntry>>(it, statusType) }.getOrNull() }
            ?: emptyMap()).filterValues { it.status != null }

    fun saveStatuses(map: Map<String, StatusEntry>) {
        prefs.edit().putString("status_json", gson.toJson(map)).apply()
    }
}

/**
 * Franchises from game names: games sharing their first two words ("Final
 * Fantasy", "Mega Man", "Metal Gear") — or, failing that, a distinctive first
 * word ("Pokémon", "Castlevania") — with at least [minGames] games. Named by
 * what their names have in common ("The Legend of Zelda").
 */
object Franchises {
    /** First words too common to make a franchise on their own. */
    private val GENERIC = setOf(
        "super", "new", "the", "a", "an", "mega", "ultra", "big", "little", "my", "crazy", "disney", "world",
        "pro", "star", "street", "dead", "dark", "dragon", "king", "battle", "legend", "ultimate", "classic"
    )

    private fun words(name: String): List<String> =
        com.shelf.launcher.ui.stripRegionTags(name).lowercase()
            .replace("é", "e").replace("'", "")
            .split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
            .let { if (it.firstOrNull() == "the") it.drop(1) else it }

    /** Little words that don't end a name part: "Legend of …", "Tales of …". */
    private val CONNECTORS = setOf("of", "the", "and", "no", "de", "to")

    /** "legend of zelda", "final fantasy", "mega man" — or null for one-word names. */
    private fun twoKey(ws: List<String>): String? = when {
        ws.size >= 3 && ws[1] in CONNECTORS -> ws[0] + " " + ws[1] + " " + ws[2]
        ws.size >= 2 && ws[1] !in CONNECTORS -> ws[0] + " " + ws[1]
        else -> null
    }

    fun detect(games: List<Game>, minGames: Int = 3): Map<String, Set<String>> {
        val eligible = games.filter { it.sourceType != SourceType.APP }
        val w = eligible.associateWith { words(it.name) }
        val two = w.values.mapNotNull { twoKey(it) }.groupingBy { it }.eachCount()
        val one = w.values.filter { it.isNotEmpty() }.groupingBy { it[0] }.eachCount()
        val groups = mutableMapOf<String, MutableList<Game>>()
        for ((game, ws) in w) {
            if (ws.isEmpty()) continue
            val k2 = twoKey(ws)
            val key = when {
                k2 != null && (two[k2] ?: 0) >= minGames -> k2
                ws[0] !in GENERIC && ws[0].length > 2 && ws[0].any { it.isLetter() } && (one[ws[0]] ?: 0) >= minGames -> ws[0]
                else -> null
            } ?: continue
            groups.getOrPut(key) { mutableListOf() } += game
        }
        return groups.filterValues { list -> list.map { it.stableKey }.distinct().size >= minGames }
            .entries.associate { (_, list) -> displayName(list) to list.map { it.stableKey }.toSet() }
    }

    /**
     * Longest word prefix the games' names share, e.g. "The Legend of Zelda". Names are compared without a leading
     * "The" and without a trailing-article form ("Legend Of Zelda, The Ocarina Of Time"), so one oddly written
     * title no longer cuts the group's name down to "Legend".
     */
    private fun displayName(list: List<Game>): String {
        fun tokens(name: String): List<String> =
            com.shelf.launcher.ui.stripRegionTags(name).replace(Regex(",\\s*[Tt]he\\b"), "")
                .split(' ').filter { w -> w.isNotBlank() }
                .let { if (it.firstOrNull().equals("the", ignoreCase = true)) it.drop(1) else it }
        fun norm(w: String) = w.trimEnd(':', '-', ',').lowercase().replace("é", "e")
        fun startsWithThe(name: String) = com.shelf.launcher.ui.stripRegionTags(name).trimStart().startsWith("the ", ignoreCase = true)
        // The spelling comes from a game that starts with "The" when there is one (its capitals are the usual ones).
        val ref = list.firstOrNull { startsWithThe(it.name) } ?: list.first()
        val first = tokens(ref.name)
        val names = list.map { tokens(it.name) }
        var n = 0
        while (n < first.size && names.all { it.size > n && norm(it[n]) == norm(first[n]) }) n++
        val prefix = first.take(maxOf(n, 1)).joinToString(" ").trimEnd(':', '-', ',', ' ')
            .ifBlank { first.firstOrNull() ?: ref.name }
        val the = list.count { startsWithThe(it.name) } * 2 >= list.size && prefix.contains(' ')
        return ((if (the) "The " else "") + prefix).replace("Pokemon", "Pokémon")
    }
}

/** Everything that shows as a collection category, in a stable order. */
fun buildCollections(
    games: List<Game>,
    user: Map<String, Set<String>>,
    statuses: Map<String, StatusEntry>,
    userOn: Boolean,
    franchisesOn: Boolean,
    statusOn: Boolean,
    /** Genres from scraped details, "ge:<genre>" (off unless switched on). */
    genresOn: Boolean = false,
    metadata: Map<String, GameMetadata> = emptyMap()
): List<ShelfCollection> {
    val present = games.map { it.stableKey }.toSet()
    val out = mutableListOf<ShelfCollection>()
    if (statusOn) {
        PlayStatus.values().forEach { st ->
            val keys = statuses.filterValues { it.status == st }.keys.filter { it in present }.toSet()
            if (keys.isNotEmpty()) out += ShelfCollection("st:${st.label}", st.label, ShelfCollection.Kind.STATUS, keys)
        }
    }
    if (userOn) {
        user.entries.sortedBy { it.key.lowercase() }.forEach { (name, keys) ->
            out += ShelfCollection("col:$name", name, ShelfCollection.Kind.USER, keys.filter { it in present }.toSet())
        }
    }
    if (franchisesOn) {
        Franchises.detect(games).entries.sortedBy { it.key.lowercase() }.forEach { (name, keys) ->
            if (user.keys.none { it.equals(name, ignoreCase = true) }) {
                out += ShelfCollection("fr:$name", name, ShelfCollection.Kind.FRANCHISE, keys)
            }
        }
    }
    if (genresOn) {
        Genres.detect(games, metadata).entries.sortedBy { it.key.lowercase() }.forEach { (name, keys) ->
            out += ShelfCollection("ge:$name", name, ShelfCollection.Kind.GENRE, keys)
        }
    }
    return out
}

/** Genre categories from the scraped details: every genre with at least
 * [minGames] games. Spelled as the first game that had it. */
object Genres {
    @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
    fun detect(games: List<Game>, metadata: Map<String, GameMetadata>, minGames: Int = 2): Map<String, Set<String>> {
        val names = mutableMapOf<String, String>() // lowercase → shown
        val groups = mutableMapOf<String, MutableSet<String>>()
        for (game in games) {
            val genres = metadata[game.stableKey]?.genres ?: continue
            for (raw in genres) {
                if (raw == null) continue
                val g = raw.trim()
                if (g.isEmpty()) continue
                val low = g.lowercase()
                names.getOrPut(low) { g }
                groups.getOrPut(low) { mutableSetOf() } += game.stableKey
            }
        }
        return groups.filterValues { it.size >= minGames }.entries.associate { (low, keys) -> names.getValue(low) to keys.toSet() }
    }
}
