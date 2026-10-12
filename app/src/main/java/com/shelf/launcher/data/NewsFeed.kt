package com.shelf.launcher.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** One headline. */
data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: Long
)

/** A news feed Voltcap can show next to the row. */
data class NewsSource(val id: String, val name: String, val url: String, val retro: Boolean)

/**
 * Headlines from retro-gaming and general game news sites, through their
 * public RSS / Atom feeds — no accounts, no keys. Fetched at most every
 * 30 minutes; the newest items from every chosen feed, merged by date.
 */
object NewsFeed {
    /** Checked September 2026: all of these served valid RSS 2.0 (Reddit: Atom). */
    val SOURCES: List<NewsSource> = listOf(
        NewsSource("timeextension", "Time Extension", "https://www.timeextension.com/feeds/latest", retro = true),
        NewsSource("retrohandhelds", "Retro Handhelds", "https://retrohandhelds.gg/rss/", retro = true),
        NewsSource("retrododo", "Retro Dodo", "https://retrododo.com/feed/", retro = true),
        NewsSource("sbcgaming", "r/SBCGaming", "https://www.reddit.com/r/SBCGaming/.rss", retro = true),
        NewsSource("nintendolife", "Nintendo Life", "https://www.nintendolife.com/feeds/latest", retro = false),
        NewsSource("vgc", "VGC", "https://www.videogameschronicle.com/feed/", retro = false)
    )

    private const val REFRESH_MS = 30 * 60_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class Cached(val at: Long, val items: List<NewsItem>)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    /** Newest first, up to [limit], from the feeds in [sourceIds]. Never throws. */
    suspend fun load(sourceIds: Set<String>, limit: Int = 20): List<NewsItem> = coroutineScope {
        SOURCES.filter { it.id in sourceIds }
            .map { source -> async { itemsFor(source) } }
            .awaitAll()
            .flatten()
            .sortedByDescending { it.publishedAt }
            .take(limit)
    }

    private suspend fun itemsFor(source: NewsSource): List<NewsItem> {
        val now = System.currentTimeMillis()
        cache[source.id]?.let { if (now - it.at < REFRESH_MS) return it.items }
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(source.url)
                    // Some sites (Reddit) refuse requests without a real user agent.
                    .header("User-Agent", "Voltcap/1.0 (Android game launcher)")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.byteStream()?.use { parse(it, source.name) }
                }
            }.getOrNull()
        }
        // A failed fetch keeps showing what was there before.
        val items = fresh ?: cache[source.id]?.items.orEmpty()
        if (fresh != null) cache[source.id] = Cached(now, fresh)
        return items
    }

    /** RSS 2.0 (<item><title><link><pubDate>) and Atom (<entry><title><link href><updated>). */
    private fun parse(input: java.io.InputStream, sourceName: String): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val out = mutableListOf<NewsItem>()
        var inItem = false
        var title: String? = null
        var link: String? = null
        var date: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < 30) {
            val tag = parser.name?.substringAfter(':')?.lowercase()
            when (event) {
                XmlPullParser.START_TAG -> when {
                    tag == "item" || tag == "entry" -> {
                        inItem = true; title = null; link = null; date = null
                    }
                    // First <title> only (media:title etc. come later).
                    inItem && tag == "title" && title == null -> title = parser.nextText().trim()
                    inItem && tag == "link" -> {
                        // Atom: <link href="…" rel="alternate"/>; RSS: <link>…</link>
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        if (href != null) {
                            if (link == null || rel == null || rel == "alternate") link = href
                        } else {
                            link = parser.nextText().trim()
                        }
                    }
                    inItem && (tag == "pubdate" || tag == "published" || tag == "updated" || tag == "date") ->
                        if (date == null) date = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (tag == "item" || tag == "entry") {
                    inItem = false
                    val t = title
                    val l = link
                    if (!t.isNullOrBlank() && !l.isNullOrBlank()) {
                        out += NewsItem(unescape(t), l, sourceName, parseDate(date))
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun unescape(s: String) = s
        .replace("&amp;", "&").replace("&#039;", "'").replace("&#39;", "'")
        .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",   // RSS
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssXXX",      // Atom
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    private fun parseDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        for (pattern in dateFormats) {
            val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val parsed = runCatching { f.parse(s) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return 0L
    }
}
