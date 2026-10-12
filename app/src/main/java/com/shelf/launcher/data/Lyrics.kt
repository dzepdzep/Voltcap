package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One timed line of a song. [atMs] is when it starts. */
data class LyricLine(val atMs: Long, val text: String)

/** Lyrics for a song: timed lines when available, otherwise plain text lines. */
data class Lyrics(val synced: List<LyricLine>, val plain: List<String>) {
    val isSynced get() = synced.isNotEmpty()

    /** The line being sung at [positionMs], or null before the first one. */
    fun lineAt(positionMs: Long): String? =
        synced.lastOrNull { it.atMs <= positionMs }?.text?.takeIf { it.isNotBlank() }
}

/**
 * Lyrics are opt-in: OFF for everyone (new installs and existing users) until
 * the user turns them on after reading [NOTE]. While off, nothing is sent to
 * LRCLIB. [init] must run once (LauncherScreen does it) before the stored choice is read;
 * until then lyrics stay off.
 */
object LyricsOptIn {
    const val NOTE = "Lyrics come from LRCLIB, a free community lyrics database, not from Spotify or the artists' publishers."
    private const val PREFS = "lyrics_opt_in"
    private const val KEY = "lrclib_enabled"
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> get() = _enabled

    fun init(context: Context) {
        _enabled.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        _enabled.value = on
    }
}

/**
 * Song lyrics from LRCLIB (lrclib.net) — a free, open lyrics database that
 * needs no account or key and returns time-synced lyrics for most songs.
 * Results (including "none found") are kept in memory for the session.
 */
object LyricsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    // ConcurrentHashMap refuses null values, so "not found" is a sentinel. Network failures are never cached.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val NOT_FOUND = Any()

    /** What [lookup] found: lyrics, "LRCLIB has none" (cached), or a failure worth retrying later. */
    sealed class Lookup {
        class Found(val lyrics: Lyrics) : Lookup()
        object NotFound : Lookup()
        object Failed : Lookup()
        object Off : Lookup()
    }

    /** Like [fetch], but tells a missing song apart from a network failure (which is worth retrying). */
    suspend fun lookup(title: String, artist: String?, album: String?, durationMs: Long): Lookup {
        if (!LyricsOptIn.enabled.value) return Lookup.Off
        val found = fetch(title, artist, album, durationMs)
        return when {
            found != null -> Lookup.Found(found)
            cache["$title|$artist"] === NOT_FOUND -> Lookup.NotFound
            else -> Lookup.Failed
        }
    }

    @Suppress("UNUSED_PARAMETER") // album: kept for callers; matching works better without it
    suspend fun fetch(title: String, artist: String?, album: String?, durationMs: Long): Lyrics? {
        if (!LyricsOptIn.enabled.value) return null // opt-in: never contact LRCLIB otherwise
        val key = "$title|$artist"
        cache[key]?.let { return it as? Lyrics }
        var failed = false
        val result = withContext(Dispatchers.IO) {
            val onFail = { failed = true }
            val cleanTitle = cleanTrack(title)
            val mainArtist = artist?.let { cleanArtist(it) }
            val seconds = if (durationMs > 0) durationMs / 1000 else null
            // 1. Exact lookup (needs artist + duration to match closely).
            exact(cleanTitle, mainArtist, seconds, onFail)
                // 2. Search by title + artist, best duration match first.
                ?: search(cleanTitle, mainArtist, seconds, onFail = onFail)
                // 3. Search by title alone, still checking the artist loosely.
                ?: search(cleanTitle, null, seconds, artistHint = mainArtist, onFail = onFail)
        }
        if (result != null) cache[key] = result else if (!failed) cache[key] = NOT_FOUND
        return result
    }

    private fun exact(title: String, artist: String?, seconds: Long?, onFail: () -> Unit): Lyrics? = runCatching {
        if (artist.isNullOrBlank()) return@runCatching null
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder().apply {
            addQueryParameter("track_name", title)
            addQueryParameter("artist_name", artist)
            if (seconds != null) addQueryParameter("duration", seconds.toString())
        }.build()
        get(url.toString())?.let { toLyrics(JSONObject(it)) }
    }.getOrElse { onFail(); null }

    private fun search(title: String, artist: String?, seconds: Long?, artistHint: String? = null, onFail: () -> Unit): Lyrics? = runCatching {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("track_name", title)
            if (!artist.isNullOrBlank()) addQueryParameter("artist_name", artist)
        }.build()
        val body = get(url.toString()) ?: return@runCatching null
        val array = org.json.JSONArray(body)
        val candidates = (0 until array.length()).map { array.getJSONObject(it) }
            .filter { artistHint == null || it.optString("artistName").contains(artistHint, ignoreCase = true) ||
                artistHint.contains(it.optString("artistName"), ignoreCase = true) }
        // Synced lyrics first, then the closest length.
        val best = candidates.sortedWith(
            compareBy<JSONObject>({ it.optString("syncedLyrics", "").let { l -> l.isBlank() || l == "null" } },
                { if (seconds == null) 0.0 else kotlin.math.abs(it.optDouble("duration", 0.0) - seconds) })
        ).firstOrNull() ?: return@runCatching null
        if (seconds != null && kotlin.math.abs(best.optDouble("duration", seconds.toDouble()) - seconds) > 8) {
            return@runCatching null
        }
        toLyrics(best)
    }.getOrElse { onFail(); null }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ShelfLauncher (Android game launcher)")
            .build()
        return client.newCall(request).execute().use { response ->
            // 404 = LRCLIB has nothing (cacheable); anything else unsuccessful is a failure (thrown, not cached).
            if (response.isSuccessful) response.body?.string()
            else if (response.code == 404) null
            else throw java.io.IOException("LRCLIB HTTP ${response.code}")
        }
    }

    private fun toLyrics(json: JSONObject): Lyrics? {
        val synced = parseLrc(json.optString("syncedLyrics", ""))
        val plain = json.optString("plainLyrics", "").takeIf { it != "null" }.orEmpty()
            .lines().filter { it.isNotBlank() }
        return if (synced.isEmpty() && plain.isEmpty()) null else Lyrics(synced, plain)
    }

    /** "Song - Remastered 2011", "Song (feat. X)", "Song - Radio Edit" → "Song". */
    private fun cleanTrack(title: String): String =
        title.replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|from|remaster|live|radio|explicit|clean|mono|stereo|bonus)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(.*remaster.*|live.*|radio edit|single version|mono|stereo|explicit|bonus track)$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { title }

    /** "Artist, Other Artist" / "Artist feat. X" → "Artist". */
    private fun cleanArtist(artist: String): String =
        artist.split(",", " & ", " feat. ", " feat ", " ft. ", " x ", " X ").first().trim().ifBlank { artist }

    private val timeTag = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

    /** "[01:23.45] line" → LyricLine(83450, "line"). Lines may carry several tags. */
    private fun parseLrc(text: String): List<LyricLine> {
        if (text.isBlank() || text == "null") return emptyList()
        val lines = mutableListOf<LyricLine>()
        text.lines().forEach { raw ->
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) return@forEach
            val words = raw.substring(tags.last().range.last + 1).trim()
            tags.forEach tag@{ tag ->
                val min = tag.groupValues[1].toLongOrNull() ?: return@tag
                val sec = tag.groupValues[2].toLongOrNull() ?: return@tag
                val fraction = tag.groupValues[3]
                val ms = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                lines += LyricLine(min * 60_000 + sec * 1000 + ms, words)
            }
        }
        return lines.sortedBy { it.atMs }
    }
}
