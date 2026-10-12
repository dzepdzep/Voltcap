package com.shelf.launcher.spotify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Spotify-made playlists (Discover Weekly, Release Radar, Daily Mixes, Spotify's editorial lists) kept in Voltcap's
 * playlist list by hand.
 *
 * Since November 2024 Spotify's Web API doesn't give apps in development mode — which a personal Spotify client id
 * like Voltcap's is — Spotify's own algorithmic and editorial playlists: they're left out of "your playlists" and
 * their songs can't be read ("not found"). Playing them still works (Spotify plays the playlist itself), so they're
 * pinned here: from a share link ("Add by link"), or from what's playing right now ("Pin what's playing"). Their name
 * and picture come from Spotify's public oEmbed page (no sign-in needed).
 */
object SpotifyPins {
    private const val PREFS = "spotify_pins"

    /** The pinned playlists, newest first, as playlist entries (owner "Spotify"). */
    fun all(context: Context): List<SpPlaylist> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                SpPlaylist(
                    id = id, name = o.optString("name").ifBlank { "Spotify playlist" }, uri = "spotify:playlist:$id",
                    images = o.optString("image").takeIf { it.isNotBlank() }?.let { listOf(SpImage(it)) },
                    owner = SpOwner(display_name = "Spotify · pinned")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun isPinned(context: Context, id: String): Boolean = all(context).any { it.id == id }

    private fun save(context: Context, list: List<SpPlaylist>) {
        val arr = org.json.JSONArray()
        list.forEach { p ->
            arr.put(org.json.JSONObject().put("id", p.id).put("name", p.name.orEmpty()).put("image", p.images?.firstOrNull()?.url.orEmpty()))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    /** Pins playlist [id] (its name / picture looked up); returns it, or null when the id is no good. */
    suspend fun add(context: Context, id: String): SpPlaylist? {
        val clean = id.trim().takeIf { it.matches(Regex("[A-Za-z0-9]{16,40}")) } ?: return null
        val (name, image) = lookup(clean)
        val entry = SpPlaylist(
            id = clean, name = name ?: "Spotify playlist", uri = "spotify:playlist:$clean",
            images = image?.let { listOf(SpImage(it)) }, owner = SpOwner(display_name = "Spotify · pinned")
        )
        save(context, listOf(entry) + all(context).filter { it.id != clean })
        return entry
    }

    fun remove(context: Context, id: String) = save(context, all(context).filter { it.id != id })

    /** The playlist id in a share link ("https://open.spotify.com/playlist/37i9dQZ…?si=…") or uri ("spotify:playlist:…"). */
    fun idFrom(text: String): String? {
        val t = text.trim()
        Regex("playlist[/:]([A-Za-z0-9]{16,40})").find(t)?.let { return it.groupValues[1] }
        return t.takeIf { it.matches(Regex("[A-Za-z0-9]{16,40}")) }
    }

    /** Name and picture from Spotify's public oEmbed (no account needed); nulls when it can't be reached. */
    private suspend fun lookup(id: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://open.spotify.com/oembed?url=" + URLEncoder.encode("https://open.spotify.com/playlist/$id", "UTF-8")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000; conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", "Voltcap")
            try {
                if (conn.responseCode !in 200..299) null to null
                else {
                    val o = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    o.optString("title").takeIf { it.isNotBlank() } to o.optString("thumbnail_url").takeIf { it.isNotBlank() }
                }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(null to null)
    }
}
