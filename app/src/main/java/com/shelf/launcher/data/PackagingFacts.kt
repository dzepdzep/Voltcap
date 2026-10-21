package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.launch

/**
 * Update 305: an age rating as one rating body gave it to this game — "USK" "16", "PEGI" "12", "ESRB" "M"… — and where
 * Voltcap read it ([source]: "Steam" — the store's own record for that app id — or "ScreenScraper" — the game's record
 * matched by its file's checksum / name). [generated]: the store marks it as derived (an IARC rating for the digital
 * release), not a physical release's classification. Never worked out from anything else.
 */
data class AgeRating(val system: String, val value: String, val source: String, val generated: Boolean = false) {
    /** "USK 16", "PEGI 12", "ESRB M". */
    val label: String get() = "$system $value"
}

/**
 * Update 305: the verified details a generated case back may print for a game — from the game's own record at Steam
 * (app id) or ScreenScraper (matched by checksum / name), never guessed: its age ratings, publisher, developer, release
 * year, players and (Steam) controller support; for Steam games its store description and screenshots too.
 */
data class PackagingFacts(
    val ratings: List<AgeRating> = emptyList(),
    val publisher: String? = null,
    val developer: String? = null,
    val year: Int? = null,
    /** e.g. "Single-player", "Online co-op", "Full controller support" — as the source names them. */
    val features: List<String> = emptyList(),
    /** ScreenScraper's players ("1-2"), when given. */
    val players: String? = null,
    val description: String? = null,
    val shots: List<String> = emptyList(),
    /** Where these came from ("Steam", "ScreenScraper"). */
    val source: String = ""
)

/**
 * Update 305: [PackagingFacts] per game, asked for once — only when a generated back is wanted (so a big library doesn't
 * hammer anyone) — and kept on the device (prefs "packaging_facts"). A game with nothing found is asked again after two
 * weeks; a failed look-up (no network) isn't kept, and the sources are left alone for an hour after one (as TgdbBacks).
 */
object PackagingFactsStore {
    private const val RECHECK_MS = 14L * 24 * 60 * 60 * 1000
    private val gson = com.google.gson.Gson()
    private var prefs: android.content.SharedPreferences? = null
    private val known = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val parsed = java.util.concurrent.ConcurrentHashMap<String, PackagingFacts>()
    private val asking: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    @Volatile private var pausedUntil = 0L
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Bumped when facts arrive (the backs that print them are made again). */
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    /** Set by the view model: ScreenScraper's look-up for a ROM (null without an account). Throws when it failed. */
    @Volatile var screenScraper: (suspend (Game) -> PackagingFacts?)? = null

    fun init(c: Context) {
        if (prefs != null) return
        val p = c.applicationContext.getSharedPreferences("packaging_facts", Context.MODE_PRIVATE)
        prefs = p
        for ((k, v) in p.all) (v as? String)?.let { known[k] = it }
    }

    /** What's known for [key], or null. */
    fun of(key: String): PackagingFacts? {
        version.intValue // (read: a back built from it is rebuilt when facts arrive)
        parsed[key]?.let { return it }
        val raw = known[key]?.takeUnless { it.startsWith("-|") } ?: return null
        val f = runCatching { gson.fromJson(raw, PackagingFacts::class.java) }.getOrNull() ?: return null
        // (Gson leaves lists null when a field is missing from older saves.)
        val fixed = f.copy(ratings = f.ratings ?: emptyList(), features = f.features ?: emptyList(), shots = f.shots ?: emptyList(), source = f.source ?: "")
        parsed[key] = fixed
        return fixed
    }

    /** Asks for [game]'s facts once, in the background: Steam games by their app id, ROMs at ScreenScraper. */
    fun request(game: Game) {
        val key = game.stableKey
        val fetch: (suspend () -> PackagingFacts?) = when {
            game.steamAppId != null -> { { SteamStore.facts(game.steamAppId) } }
            game.sourceType == SourceType.ROM -> screenScraper?.let { f -> { f(game) } } ?: return
            else -> return // (Android: there's no public source for an app's rating — none is printed)
        }
        known[key]?.let { v ->
            if (!v.startsWith("-|")) return
            val at = v.removePrefix("-|").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at < RECHECK_MS) return
        }
        if (System.currentTimeMillis() < pausedUntil) return
        if (!asking.add(key)) return
        scope.launch {
            try {
                val result = runCatching { fetch() }
                if (result.isFailure) {
                    android.util.Log.i("Voltcap", "packaging facts: ${game.name}: look-up failed (${result.exceptionOrNull()?.javaClass?.simpleName}), paused 1 h")
                    pausedUntil = System.currentTimeMillis() + 60 * 60 * 1000L; return@launch
                }
                val f = result.getOrNull()
                android.util.Log.i("Voltcap", "packaging facts: ${game.name}: " + (f?.let { "${it.source}, ratings ${it.ratings.map { r -> r.label }}" } ?: "none found"))
                val v = if (f == null) "-|${System.currentTimeMillis()}" else gson.toJson(f)
                known[key] = v
                parsed.remove(key)
                prefs?.edit()?.putString(key, v)?.apply()
                if (f != null) version.intValue++
            } finally { asking.remove(key) }
        }
    }

    /**
     * The rating to print from [list]: the body [preferred] names; with [others], else another body's in the order USK,
     * PEGI, ESRB, then the rest. Null: none (or [preferred] null — ratings off).
     */
    fun pick(list: List<AgeRating>, preferred: String?, others: Boolean): AgeRating? {
        if (preferred == null || list.isEmpty()) return null
        list.firstOrNull { it.system == preferred }?.let { return it }
        if (!others) return null
        val order = listOf("USK", "PEGI", "ESRB", "ACB", "CERO")
        return list.minByOrNull { order.indexOf(it.system).let { i -> if (i < 0) 99 else i } }
    }

    /** For tests (and the test hook voltcap_test_facts): facts as if they had been found — this session only, not saved. */
    fun put(key: String, f: PackagingFacts) { known[key] = gson.toJson(f); parsed.remove(key); runCatching { version.intValue++ } }

    /**
     * A rating body's name and a rating as a source writes them → an [AgeRating] (null for anything that isn't a known
     * body's rating). ESRB's letters as on the box ("M", "T", "E10+"); the others' ages as given.
     */
    fun rating(system: String?, value: String?, source: String, generated: Boolean = false): AgeRating? {
        val s = when (system?.trim()?.lowercase()) {
            "usk" -> "USK"; "pegi" -> "PEGI"; "esrb" -> "ESRB"; "cero" -> "CERO"; "acb", "oflc" -> "ACB"
            else -> return null
        }
        var v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (s == "ESRB") v = when (v.lowercase()) {
            "e" -> "E"; "e10", "e10+" -> "E10+"; "t" -> "T"; "m" -> "M"; "ao" -> "AO"; "ec" -> "EC"; "rp" -> "RP"
            "k-a", "ka" -> "K-A"
            else -> return null
        } else if (s == "USK" || s == "PEGI") {
            v = v.removeSuffix("+").trim()
            if (v.toIntOrNull() == null) return null
            if (s == "USK" && v.toInt() !in setOf(0, 6, 12, 16, 18)) return null
            if (s == "PEGI" && v.toInt() !in setOf(3, 7, 12, 16, 18)) return null
        } else v = v.uppercase()
        return AgeRating(s, v, source, generated)
    }
}

/**
 * Update 305: Steam's own store record for an app id (store.steampowered.com/api/appdetails — public, no key): its age
 * ratings (USK, PEGI, ESRB… as Steam lists them), publisher, developer, release year, the store's player / controller
 * categories, its short description and screenshots. Only the game's own app id is asked — never a name search.
 */
object SteamStore {
    private val client by lazy {
        okhttp3.OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    }

    /** Store categories worth printing, by Steam's id (their names come from Steam). */
    private val FEATURES = listOf(2, 1, 49, 36, 9, 38, 24, 28, 18)

    suspend fun facts(appId: Int): PackagingFacts? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = okhttp3.Request.Builder()
            .url("https://store.steampowered.com/api/appdetails?appids=$appId&cc=de&l=english")
            .header("User-Agent", "Voltcap")
            .build()
        val body = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            r.body?.string() ?: throw java.io.IOException("empty")
        }
        parse(appId, body)
    }

    /** HTML (Steam's descriptions have tags and entities) as plain text. */
    internal fun plain(html: String): String = html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()

    /** [body] (appdetails' answer) → the facts, or null when Steam has no data for it. */
    fun parse(appId: Int, body: String): PackagingFacts? {
        val root = runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val entry = root.get(appId.toString())?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        if (entry.get("success")?.asBoolean != true) return null
        val d = entry.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        fun str(o: com.google.gson.JsonObject, k: String): String? =
            o.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
        fun names(k: String): String? = d.get(k)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() } }
            ?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val ratings = d.get("ratings")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.mapNotNull { (k, v) ->
            val o = v.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            // (Steam marks a rating it worked out itself — "steam_germany", "rating_generated" — those aren't a body's.)
            PackagingFactsStore.rating(k, str(o, "rating"), "Steam", generated = str(o, "rating_generated") == "1")
        }.orEmpty()
        val year = d.get("release_date")?.takeIf { it.isJsonObject }?.asJsonObject?.let { str(it, "date") }
            ?.let { Regex("(19|20)\\d\\d").find(it)?.value?.toIntOrNull() }
        val cats = d.get("categories")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e ->
            val o = e.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.get("id")?.asInt ?: return@mapNotNull null
            if (id in FEATURES) id to (str(o, "description") ?: return@mapNotNull null) else null
        }.orEmpty().sortedBy { FEATURES.indexOf(it.first) }.map { it.second }.distinct()
        val shots = d.get("screenshots")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { e ->
            e.takeIf { it.isJsonObject }?.asJsonObject?.let { str(it, "path_full") ?: str(it, "path_thumbnail") }
        }.orEmpty().take(4)
        val desc = str(d, "short_description")?.let { plain(it) }?.takeIf { it.isNotEmpty() }
        return PackagingFacts(
            ratings = ratings, publisher = names("publishers"), developer = names("developers"), year = year,
            features = cats, description = desc, shots = shots, source = "Steam"
        )
    }
}
