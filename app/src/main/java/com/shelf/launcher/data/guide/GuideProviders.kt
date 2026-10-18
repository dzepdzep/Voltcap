package com.shelf.launcher.data.guide

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.shelf.launcher.data.bytesCapped
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
private fun unb64(s: String) = String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)

/** Rebuilds a candidate from its self-describing id (titles are unknown then). Null for a bad id. */
internal fun candidateFromId(id: String, gameKey: String = ""): GuideCandidate? = try {
    val prefix = id.substringBefore(':')
    val ref = unb64(id.substringAfter(':'))
    when (prefix) {
        "ra" -> GuideCandidate(id, "ra", "", null, null, GuideCandidate.Kind.TEXT, ref, gameKey)
        "ext" -> GuideCandidate(id, "ra", "", null, ref, GuideCandidate.Kind.EXTERNAL_LINK, ref, gameKey)
        "local" -> GuideCandidate(id, "local", "", null, null, GuideCandidate.Kind.TEXT, ref, gameKey)
        "sw" -> GuideCandidate(id, "sw", ref, "StrategyWiki", null, GuideCandidate.Kind.TEXT, ref, gameKey)
        "fandom" -> GuideCandidate(id, "fandom", ref.substringAfter('|'), "Fandom wiki", null, GuideCandidate.Kind.TEXT, ref, gameKey)
        else -> null
    }
} catch (e: Exception) { null }

/** Parsed guides as JSON files in filesDir/guides/, so a guide survives offline. */
class GuideCache(context: Context) {
    private val dir = com.shelf.launcher.data.AppStorage.dir(context, "Guides", File(context.filesDir, "guides")).apply { mkdirs() }
    private val gson = Gson()

    private fun file(id: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }

    fun read(id: String): Guide? = try {
        file(id).takeIf { it.exists() }?.readText()?.let { gson.fromJson(it, Guide::class.java) }
    } catch (e: Exception) { null }

    fun write(guide: Guide) {
        try {
            val f = file(guide.id)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(gson.toJson(guide))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) { /* cache is best effort */ }
    }

    fun isFresh(guide: Guide, now: Long = System.currentTimeMillis()) = now - guide.fetchedAt < MAX_AGE_MS

    companion object { const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 }
}

/**
 * RetroAchievements' official guides. The game's GuideURL usually points into the public
 * RetroAchievements/guides GitHub repo (markdown), which is fetched from raw.githubusercontent.com.
 * Any other URL is offered as an "external link only" candidate and is never scraped.
 */
class RetroAchievementsGuideProvider(
    context: Context,
    private val cache: GuideCache = GuideCache(context),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
) : GuideProvider {
    override val id = "ra"

    override suspend fun find(game: GuideGameRef): List<GuideCandidate> {
        val url = game.guideUrl?.trim()?.takeIf { it.startsWith("http") } ?: return emptyList()
        val raw = toRawUrl(url)
        return if (raw != null) {
            listOf(GuideCandidate("ra:" + b64(raw), id, "${game.title} guide", "RetroAchievements guide",
                url, GuideCandidate.Kind.TEXT, raw, game.gameKey, game.raGameId))
        } else {
            listOf(GuideCandidate("ext:" + b64(url), id, "${game.title} guide", "Opens in your browser",
                url, GuideCandidate.Kind.EXTERNAL_LINK, url, game.gameKey, game.raGameId))
        }
    }

    // All of it off the main thread: the cached guide is a file read, parsing a long guide takes a moment.
    override suspend fun load(candidate: GuideCandidate): Guide? = withContext(Dispatchers.IO) { loadNow(candidate) }

    private fun loadNow(candidate: GuideCandidate): Guide? {
        if (candidate.kind != GuideCandidate.Kind.TEXT) return null
        val cached = cache.read(candidate.id)
        if (cached != null && cache.isFresh(cached)) return cached.copy(gameKey = candidate.gameKey.ifEmpty { cached.gameKey })
        val text = fetch(candidate.ref)
        if (text == null) return cached // offline: keep the old copy
        val sections = GuideParser.parseMarkdown(text, candidate.title.ifEmpty { "Guide" })
        if (sections.isEmpty()) return cached
        val guide = Guide(
            id = candidate.id, gameKey = candidate.gameKey,
            title = GuideParser.titleOf(sections) ?: candidate.title.ifEmpty { "Guide" },
            source = "retroachievements", sections = sections, fetchedAt = System.currentTimeMillis(),
            attribution = ATTRIBUTION, sourceUrl = candidate.sourceUrl ?: candidate.ref
        )
        cache.write(guide)
        return guide
    }

    private fun fetch(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "VoltCap").build()).execute().use { r ->
            if (!r.isSuccessful) null
            else r.body?.bytesCapped(MAX_BYTES.toLong())?.toString(Charsets.UTF_8)
        }
    } catch (e: Exception) { null }

    companion object {
        const val ATTRIBUTION = "Guide from RetroAchievements/guides on GitHub"
        private const val MAX_BYTES = 3 * 1024 * 1024
        private val BLOB = Regex("^https?://(?:www\\.)?github\\.com/RetroAchievements/guides/blob/([^/?#]+)/([^?#]+)(?:[?#].*)?$", RegexOption.IGNORE_CASE)
        private val RAW = Regex("^https://raw\\.githubusercontent\\.com/RetroAchievements/guides/.+", RegexOption.IGNORE_CASE)

        /** github.com/RetroAchievements/guides/blob/<branch>/<path>.md -> raw URL; null if not such a link. */
        fun toRawUrl(url: String): String? {
            val clean = url.substringBefore('#').substringBefore('?')
            fun isDoc(p: String) = p.lowercase().let { it.endsWith(".md") || it.endsWith(".markdown") || it.endsWith(".txt") }
            BLOB.find(url)?.let { m ->
                val path = m.groupValues[2]
                if (isDoc(path)) return "https://raw.githubusercontent.com/RetroAchievements/guides/${m.groupValues[1]}/$path"
                return null
            }
            if (RAW.matches(clean) && isDoc(clean)) return clean
            return null
        }
    }
}

/**
 * Guides the user keeps in a folder of their own (.md / .txt). [folder] is a SAF tree URI
 * ("content://...") or a plain file path. Files are matched to a game by RA game id in the
 * file name ("ra1234", "[RA 1234]") or by normalized title.
 */
class LocalFolderGuideProvider(
    private val context: Context,
    private val folder: () -> String?
) : GuideProvider {
    override val id = "local"

    private class Entry(val name: String, val ref: String)

    override suspend fun find(game: GuideGameRef): List<GuideCandidate> = withContext(Dispatchers.IO) {
        val f = folder()?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val title = GuideText.normalize(game.title.replace(Regex("\\(.*?\\)|\\[.*?]"), " "))
        val scored = list(f).mapNotNull { e ->
            val s = matchScore(e.name, title, game.raGameId)
            if (s > 0) s to e else null
        }.sortedWith(compareByDescending<Pair<Int, Entry>> { it.first }.thenBy { it.second.name.lowercase() })
        scored.map { (_, e) ->
            GuideCandidate("local:" + b64(e.ref), id, e.name.substringBeforeLast('.'), e.name, null,
                GuideCandidate.Kind.TEXT, e.ref, game.gameKey, game.raGameId)
        }
    }

    override suspend fun load(candidate: GuideCandidate): Guide? = withContext(Dispatchers.IO) {
        val text = read(candidate.ref) ?: return@withContext null
        val name = candidate.subtitle ?: candidate.ref
        val md = when {
            name.lowercase().let { it.endsWith(".md") || it.endsWith(".markdown") } -> true
            name.lowercase().endsWith(".txt") -> false
            else -> Regex("(?m)^\\s{0,3}#{1,6}\\s+\\S").containsMatchIn(text)
        }
        val fallback = candidate.title.ifEmpty { "Guide" }
        val sections = if (md) GuideParser.parseMarkdown(text, fallback) else GuideParser.parsePlainText(text, fallback)
        if (sections.isEmpty()) return@withContext null
        Guide(
            id = candidate.id, gameKey = candidate.gameKey,
            title = GuideParser.titleOf(sections) ?: fallback,
            source = "local", sections = sections, fetchedAt = System.currentTimeMillis(),
            attribution = "Your own guide file: ${candidate.subtitle ?: fallback}", sourceUrl = null
        )
    }

    private fun matchScore(fileName: String, titleNorm: String, raId: Int?): Int {
        if (raId != null) {
            val m = RA_ID.find(fileName)
            if (m != null && m.groupValues[1].toIntOrNull() == raId) return 4
        }
        if (titleNorm.length < 3) return 0
        val base = GuideText.normalize(fileName.substringBeforeLast('.'))
            .split(' ').filter { it !in NOISE }.joinToString(" ")
        if (base.length < 3) return 0
        return when {
            base == titleNorm -> 3
            " $base ".contains(" $titleNorm ") || (base.length >= 4 && " $titleNorm ".contains(" $base ")) -> 2
            else -> 0
        }
    }

    private fun isDoc(name: String) = name.lowercase().let { it.endsWith(".md") || it.endsWith(".markdown") || it.endsWith(".txt") }

    private fun list(folder: String): List<Entry> = try {
        if (folder.startsWith("content://")) listTree(Uri.parse(folder))
        else File(folder).walkTopDown().maxDepth(3).filter { it.isFile && isDoc(it.name) }
            .take(MAX_FILES).map { Entry(it.name, it.absolutePath) }.toList()
    } catch (e: Exception) { emptyList() }

    private fun listTree(tree: Uri): List<Entry> {
        val out = ArrayList<Entry>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < MAX_FILES) {
                    val childId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 3) walk(childId, depth + 1)
                    } else if (isDoc(name)) {
                        out.add(Entry(name, DocumentsContract.buildDocumentUriUsingTree(tree, childId).toString()))
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }

    private fun read(ref: String): String? = try {
        val bytes = if (ref.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(ref))?.use { it.readBytes() }
        else File(ref).takeIf { it.isFile }?.readBytes()
        bytes?.takeIf { it.size <= MAX_BYTES }?.let { b ->
            val s = String(b, Charsets.UTF_8)
            // Old FAQs are often Windows-1252; UTF-8 decoding then produces replacement characters.
            if (s.contains('�')) String(b, Charsets.ISO_8859_1) else s
        }
    } catch (e: Exception) { null }

    private companion object {
        const val MAX_FILES = 2000
        const val MAX_BYTES = 4 * 1024 * 1024
        val RA_ID = Regex("(?i)(?:^|[^a-z0-9])(?:ra|retroachievements|gameid)[ _-]?(\\d+)(?!\\d)")
        val NOISE = setOf("guide", "walkthrough", "faq", "faqs", "complete", "full", "text", "achievement", "achievements")
    }
}

/**
 * Providers in priority order plus the per-game override (the guide the user picked, first in the list).
 * Default order: the user's own local files, then RetroAchievements.
 */
class GuideRegistry(
    private val providers: List<GuideProvider>,
    private val store: GuideProgressStore
) {
    suspend fun guidesFor(game: GuideGameRef): List<GuideCandidate> {
        val all = LinkedHashMap<String, GuideCandidate>()
        for (p in providers) {
            val found = try { p.find(game) } catch (e: Exception) { emptyList() }
            for (c in found) all.putIfAbsent(c.id, c)
        }
        val chosen = store.get(game.gameKey).chosenGuideId
        val list = all.values.toMutableList()
        if (chosen != null) {
            val idx = list.indexOfFirst { it.id == chosen }
            if (idx > 0) list.add(0, list.removeAt(idx))
            else if (idx < 0) candidateFromId(chosen, game.gameKey)?.let { list.add(0, it) }
        }
        return list
    }

    suspend fun load(guideId: String, gameKey: String = ""): Guide? {
        val cand = candidateFromId(guideId, gameKey) ?: return null
        val p = providers.firstOrNull { it.id == cand.providerId } ?: return null
        return try { p.load(cand) } catch (e: Exception) { null }
    }

    suspend fun load(candidate: GuideCandidate): Guide? {
        val p = providers.firstOrNull { it.id == candidate.providerId } ?: return null
        return try { p.load(candidate) } catch (e: Exception) { null }
    }
}


/**
 * StrategyWiki (strategywiki.org): community walkthroughs under CC BY-SA, read through the wiki's
 * public API (no page scraping). A game's guide is its main page plus its sub-pages
 * ("Game/Walkthrough", "Game/World 1"…), joined into one guide with a heading per page.
 */
class StrategyWikiGuideProvider(
    context: Context,
    private val cache: GuideCache = GuideCache(context),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
) : GuideProvider {
    override val id = "sw"

    override suspend fun find(game: GuideGameRef): List<GuideCandidate> = withContext(Dispatchers.IO) {
        val q = GuideQuery.clean(game.title)
        if (q.length < 2) return@withContext emptyList()
        // 1) The page with exactly this name (redirects followed: "Pokemon Red" → "Pokémon Red and Blue").
        val exact = get("$API?action=query&redirects=1&format=json&titles=" + Uri.encode(q))?.let { j ->
            runCatching {
                val pages = org.json.JSONObject(j).getJSONObject("query").getJSONObject("pages")
                pages.keys().asSequence().map { pages.getJSONObject(it) }.filter { !it.has("missing") }.map { it.getString("title") }.firstOrNull()
            }.getOrNull()
        }
        // 2) Title search, then full-text search.
        fun search(what: String) = get("$API?action=query&list=search&srnamespace=0&srlimit=8&format=json&srwhat=$what&srsearch=" + Uri.encode(q))?.let { json ->
            runCatching {
                val arr = org.json.JSONObject(json).getJSONObject("query").getJSONArray("search")
                (0 until arr.length()).map { arr.getJSONObject(it).getString("title") }
            }.getOrNull()
        }.orEmpty()
        val hits = (listOfNotNull(exact) + search("title") + search("text"))
            // Main game pages only (sub-pages come in when the guide loads).
            .map { it.substringBefore('/') }.distinct()
        val want = GuideText.normalize(q)
        val ranked = hits.sortedByDescending { GuideQuery.similarity(GuideText.normalize(it), want) }
            .filter { GuideQuery.similarity(GuideText.normalize(it), want) >= 0.5f }
        ranked.take(2).map { title ->
            GuideCandidate("sw:" + b64(title), id, title, "StrategyWiki walkthrough", "https://strategywiki.org/wiki/" + Uri.encode(title.replace(' ', '_')),
                GuideCandidate.Kind.TEXT, title, game.gameKey, game.raGameId)
        }
    }

    override suspend fun load(candidate: GuideCandidate): Guide? = withContext(Dispatchers.IO) {
        val cached = cache.read(candidate.id)
        if (cached != null && cache.isFresh(cached)) return@withContext cached.copy(gameKey = candidate.gameKey.ifEmpty { cached.gameKey })
        val title = candidate.ref
        val subJson = get("$API?action=query&list=allpages&apnamespace=0&aplimit=60&format=json&apprefix=" + Uri.encode("$title/"))
        val subs = runCatching {
            val arr = org.json.JSONObject(subJson ?: "{}").getJSONObject("query").getJSONArray("allpages")
            (0 until arr.length()).map { arr.getJSONObject(it).getString("title") }
        }.getOrDefault(emptyList())
        val pages = (listOf(title) + subs).take(MAX_PAGES)
        val md = StringBuilder()
        for (page in pages) {
            val html = get("$API?action=parse&prop=text&disableeditsection=1&disabletoc=1&format=json&page=" + Uri.encode(page))
                ?.let { runCatching { org.json.JSONObject(it).getJSONObject("parse").getJSONObject("text").getString("*") }.getOrNull() }
                ?: continue
            val name = if (page == title) title else page.substringAfter("$title/")
            md.append("# ").append(name).append("\n\n").append(htmlToMarkdown(html)).append("\n\n")
        }
        if (md.isBlank()) return@withContext cached
        val sections = GuideParser.parseMarkdown(md.toString(), title)
        if (sections.isEmpty()) return@withContext cached
        val guide = Guide(
            id = candidate.id, gameKey = candidate.gameKey, title = "$title (StrategyWiki)",
            source = "strategywiki", sections = sections, fetchedAt = System.currentTimeMillis(),
            attribution = "From StrategyWiki (strategywiki.org), CC BY-SA 4.0 — by its contributors",
            sourceUrl = candidate.sourceUrl ?: ("https://strategywiki.org/wiki/" + Uri.encode(title.replace(' ', '_')))
        )
        cache.write(guide)
        guide
    }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "VoltCap guide reader").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.bytesCapped(4L * 1024 * 1024)?.toString(Charsets.UTF_8)
        }
    } catch (e: Exception) { null }

    companion object {
        private const val API = "https://strategywiki.org/w/api.php"
        private const val MAX_PAGES = 30

        /** Wiki HTML → the markdown subset GuideParser reads (headings, list items, paragraphs). */
        internal fun htmlToMarkdown(html: String): String {
            var t = html
            t = t.replace(Regex("(?is)<(script|style|table class=\"navbox|div class=\"navbox)[^>]*>.*?</(script|style|table|div)>"), " ")
            t = t.replace(Regex("(?is)<h([1-6])[^>]*>(.*?)</h\\1>")) { m ->
                val level = (m.groupValues[1].toInt()).coerceIn(2, 6)
                "\n\n" + "#".repeat(level) + " " + m.groupValues[2].replace(Regex("<[^>]+>"), "").trim() + "\n\n"
            }
            t = t.replace(Regex("(?i)<li[^>]*>"), "\n- ")
            t = t.replace(Regex("(?i)<br\\s*/?>"), "\n")
            t = t.replace(Regex("(?i)</(p|div|tr|ul|ol|dl|dd|dt)>"), "\n")
            t = t.replace(Regex("(?i)</t[dh]>"), "  ")
            t = t.replace(Regex("<[^>]+>"), "")
            t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
            t = t.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
            t = t.replace(Regex("[ \t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n")
            return t.trim()
        }
    }
}

/**
 * Links to the big guide sites (GameFAQs, IGN, Fandom, StrategyWiki search). Their guides are the
 * authors' own work and those sites don't allow copying them out, so these open in Voltcap's in-app
 * browser instead of being scraped.
 */
class GuideSiteLinksProvider(private val context: android.content.Context) : GuideProvider {
    override val id = "web"

    override suspend fun find(game: GuideGameRef): List<GuideCandidate> {
        val q = game.title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("\\s+"), " ").trim()
        if (q.isEmpty()) return emptyList()
        val e = Uri.encode(q)
        fun link(title: String, sub: String, url: String) =
            GuideCandidate("ext:" + b64(url), id, title, sub, url, GuideCandidate.Kind.EXTERNAL_LINK, url, game.gameKey, game.raGameId)
        // The game's own guide list on GameFAQs when it can be found; the search page otherwise.
        val faqs = runCatching { GameFaqs.gamePage(context, game.gameKey, q, game.platform) }.getOrNull()
        // Update 144: subtitles start with a capital, like every label.
        return listOf(
            if (faqs != null) link("GameFAQs guides", "This game's guide list · Opens in Voltcap", faqs)
            else link("GameFAQs guides", "Search on GameFAQs · Opens in Voltcap", GameFaqs.searchUrl(q, game.platform)),
            link("IGN wiki guide", "IGN · Opens in Voltcap", "https://www.ign.com/search?q=$e%20walkthrough"),
            link("Fandom wiki", "Fandom · Opens in Voltcap", "https://www.fandom.com/?s=$e"),
            link("More on StrategyWiki", "StrategyWiki · Opens in Voltcap", "https://strategywiki.org/w/index.php?search=$e")
        )
    }

    override suspend fun load(candidate: GuideCandidate): Guide? = null
}


/** Cleaning and matching game titles for guide searches. */
internal object GuideQuery {
    /** "Legend of Zelda, The - A Link to the Past (USA) [!]" → "The Legend of Zelda A Link to the Past". */
    fun clean(title: String): String {
        var t = title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace('_', ' ').trim()
        Regex("^(.*?), (The|A|An)\\b(.*)$").find(t)?.let { m -> t = m.groupValues[2] + " " + m.groupValues[1] + m.groupValues[3] }
        return t.replace(" - ", ": ").replace(Regex("\\s+"), " ").trim().trimEnd(':', '-')
    }

    /** Share of the wanted words found in [have] (0..1), both already normalized. */
    fun similarity(have: String, want: String): Float {
        val w = want.split(' ').filter { it.length > 1 && it !in setOf("the", "of", "and", "a") }.toSet()
        if (w.isEmpty()) return if (have == want) 1f else 0f
        val h = have.split(' ').toSet()
        return w.count { it in h }.toFloat() / w.size
    }
}

/**
 * Fandom game wikis (CC BY-SA), read through each wiki's public MediaWiki API: finds the game's
 * community wiki (by series name), then its walkthrough pages, and shows the text in the guide
 * reader — the actual walkthrough, not a link.
 */
class FandomGuideProvider(
    context: Context,
    private val cache: GuideCache = GuideCache(context),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
) : GuideProvider {
    override val id = "fandom"

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "VoltCap guide reader").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.bytesCapped(4L * 1024 * 1024)?.toString(Charsets.UTF_8)
        }
    } catch (e: Exception) { null }

    /** Likely wiki names: "pokemon red" → pokemon; "super mario world" → mario, supermario; "final fantasy vi" → finalfantasy. */
    private fun wikiNames(q: String): List<String> {
        val words = GuideText.normalize(q).split(' ').filter { it.isNotBlank() && it !in setOf("the", "a", "an") }
        if (words.isEmpty()) return emptyList()
        return listOfNotNull(
            words.take(2).joinToString(""),
            words.first(),
            words.getOrNull(1)?.takeIf { words.first() in setOf("super", "new", "legend") },
            words.joinToString("").take(30)
        ).filter { it.length >= 3 }.distinct()
    }

    override suspend fun find(game: GuideGameRef): List<GuideCandidate> = withContext(Dispatchers.IO) {
        val q = GuideQuery.clean(game.title)
        if (q.length < 3) return@withContext emptyList()
        val want = GuideText.normalize(q)
        for (wiki in wikiNames(q)) {
            val api = "https://$wiki.fandom.com/api.php"
            val json = get("$api?action=query&list=search&srnamespace=0&srlimit=10&format=json&srsearch=" + Uri.encode("$q walkthrough")) ?: continue
            val hits = runCatching {
                val arr = org.json.JSONObject(json).getJSONObject("query").getJSONArray("search")
                (0 until arr.length()).map { arr.getJSONObject(it).getString("title") }
            }.getOrNull().orEmpty()
            if (hits.isEmpty()) continue
            // A walkthrough page for this game, else the game's own page.
            val walk = hits.filter { it.contains("walkthrough", true) || it.contains("guide", true) }
                .maxByOrNull { GuideQuery.similarity(GuideText.normalize(it), want) }
                ?.takeIf { GuideQuery.similarity(GuideText.normalize(it), want) >= 0.5f }
            val main = hits.maxByOrNull { GuideQuery.similarity(GuideText.normalize(it), want) }
                ?.takeIf { GuideQuery.similarity(GuideText.normalize(it), want) >= 0.75f }
            val page = walk ?: main ?: continue
            val ref = "$wiki|$page"
            return@withContext listOf(
                GuideCandidate("fandom:" + b64(ref), id, page, "Fandom walkthrough · $wiki.fandom.com",
                    "https://$wiki.fandom.com/wiki/" + Uri.encode(page.replace(' ', '_')),
                    GuideCandidate.Kind.TEXT, ref, game.gameKey, game.raGameId)
            )
        }
        emptyList()
    }

    override suspend fun load(candidate: GuideCandidate): Guide? = withContext(Dispatchers.IO) {
        val cached = cache.read(candidate.id)
        if (cached != null && cache.isFresh(cached)) return@withContext cached.copy(gameKey = candidate.gameKey.ifEmpty { cached.gameKey })
        val wiki = candidate.ref.substringBefore('|')
        val title = candidate.ref.substringAfter('|')
        val api = "https://$wiki.fandom.com/api.php"
        val subJson = get("$api?action=query&list=allpages&apnamespace=0&aplimit=40&format=json&apprefix=" + Uri.encode("$title/"))
        val subs = runCatching {
            val arr = org.json.JSONObject(subJson ?: "{}").getJSONObject("query").getJSONArray("allpages")
            (0 until arr.length()).map { arr.getJSONObject(it).getString("title") }
        }.getOrDefault(emptyList())
        val md = StringBuilder()
        for (page in (listOf(title) + subs).take(25)) {
            val html = get("$api?action=parse&prop=text&disableeditsection=1&disabletoc=1&format=json&page=" + Uri.encode(page))
                ?.let { runCatching { org.json.JSONObject(it).getJSONObject("parse").getJSONObject("text").getString("*") }.getOrNull() }
                ?: continue
            val name = if (page == title) title else page.substringAfter("$title/")
            md.append("# ").append(name).append("\n\n").append(StrategyWikiGuideProvider.htmlToMarkdown(html)).append("\n\n")
        }
        if (md.isBlank()) return@withContext cached
        val sections = GuideParser.parseMarkdown(md.toString(), title)
        if (sections.isEmpty()) return@withContext cached
        val guide = Guide(
            id = candidate.id, gameKey = candidate.gameKey, title = "$title ($wiki wiki)",
            source = "fandom", sections = sections, fetchedAt = System.currentTimeMillis(),
            attribution = "From the $wiki community wiki on Fandom, CC BY-SA — by its contributors",
            sourceUrl = candidate.sourceUrl
        )
        cache.write(guide)
        guide
    }
}
