package com.shelf.launcher.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Steam achievements. Two ways in:
 * - Just your profile (no key): read from your public Steam Community pages (`?xml=1`). Your profile and
 *   its game details must be public.
 * - With a Steam Web API key (steamcommunity.com/dev/apikey) as well: the Web API, for when the public
 *   pages don't answer. Game details still have to be public.
 */
object SteamAchievements {
    /** [apiKey] blank = no key: the public profile pages are used. */
    data class Account(val apiKey: String, val steamId: String) {
        val keyless: Boolean get() = apiKey.isBlank()
    }

    data class Achievement(
        val id: String,
        val name: String,
        val description: String,
        val icon: String?,
        val iconGray: String?,
        val achieved: Boolean,
        val unlockedAt: Long,
        /** Share of all players who have it (Steam's global stats), when Steam gives it. */
        val percent: Float? = null
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("steam_account", Context.MODE_PRIVATE)

    /** Your Steam profile picture (saved when connecting, refreshed at start), or null. Compose state. */
    val avatarUrl = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Reads the saved picture into [avatarUrl] (call once at start). */
    fun loadAvatar(context: Context) { avatarUrl.value = prefs(context).getString("avatar", null) }

    private fun saveAvatar(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        prefs(context).edit().putString("avatar", url).apply()
        avatarUrl.value = url
    }

    /** Fetches the current profile picture again (it may have changed on Steam). */
    suspend fun refreshAvatar(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val a = account(context) ?: return@runCatching
            val url = if (a.keyless) getText("https://steamcommunity.com/profiles/${a.steamId}/?xml=1")?.let { tag(it, "avatarFull") }
            else get("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=${enc(a.apiKey)}&steamids=${a.steamId}")
                ?.getAsJsonObject("response")?.getAsJsonArray("players")?.firstOrNull()?.asJsonObject?.get("avatarfull")?.asString
            saveAvatar(context, url)
        }
    }

    fun account(context: Context): Account? {
        val p = prefs(context)
        val key = p.getString("key", null) ?: return null
        val id = p.getString("id", null) ?: return null
        return Account(key, id)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        avatarUrl.value = null
        SteamTrophies.clear()
    }

    private fun get(url: String): JsonObject? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000; conn.readTimeout = 15_000
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() }).asJsonObject
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** A Steam Community page as text (they follow redirects to the profile's own address). */
    private fun getText(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000; conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept-Language", "en")
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** The text of the first <[tag]> in [xml] (CDATA unwrapped), or null. */
    private fun tag(xml: String, tag: String): String? =
        Regex("<$tag>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?\\s*</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

    /** Checks the key, turns a profile name into a SteamID64 and saves both; an error message or null. */
    suspend fun connect(context: Context, apiKey: String, profile: String): String? = withContext(Dispatchers.IO) {
        runCatching { connectOrThrow(context, apiKey, profile) }.getOrElse { "Steam's answer couldn't be read — try again." }
    }

    private fun connectOrThrow(context: Context, apiKey: String, profile: String): String? {
        val key = apiKey.trim()
        var id = profile.trim().removeSuffix("/").substringAfterLast("/")
        if (id.isBlank()) return "Enter your profile name, link or SteamID64."
        if (key.isEmpty()) return connectKeyless(context, id)
        if (key.length < 20) return "That doesn't look like a Steam Web API key."
        if (!id.matches(Regex("\\d{17}"))) {
            val r = get("https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=${enc(key)}&vanityurl=${enc(id)}")
                ?: return "Steam didn't answer — check the key."
            val resp = r.getAsJsonObject("response")
            id = resp?.get("steamid")?.asString ?: return "No Steam profile called \"$id\"."
        }
        val check = get("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=${enc(key)}&steamids=$id")
            ?: return "Steam refused the key."
        val players = check.getAsJsonObject("response")?.getAsJsonArray("players")
        if (players == null || players.size() == 0) return "Profile not found."
        prefs(context).edit().putString("key", key).putString("id", id).apply()
        saveAvatar(context, players[0].asJsonObject.get("avatarfull")?.asString)
        return null
    }

    /** No key: finds the profile on its public page and checks it's public. */
    private fun connectKeyless(context: Context, name: String): String? {
        val url = if (name.matches(Regex("\\d{17}"))) "https://steamcommunity.com/profiles/$name/?xml=1"
        else "https://steamcommunity.com/id/${enc(name)}/?xml=1"
        val xml = getText(url) ?: return "Steam didn't answer — check your connection and try again."
        val id = tag(xml, "steamID64")?.takeIf { it.matches(Regex("\\d{17}")) }
            ?: return "No Steam profile called \"$name\". Try the link from your profile page, or your SteamID64."
        val privacy = tag(xml, "privacyState")
        if (privacy != null && privacy != "public") {
            return "That profile isn't public. In Steam › Edit profile › Privacy settings, set \"My profile\" and \"Game details\" to Public — or add a Web API key."
        }
        prefs(context).edit().putString("key", "").putString("id", id).apply()
        saveAvatar(context, tag(xml, "avatarFull"))
        return null
    }

    /** Achievements for [appId], unlocked first (newest first), then the rest; null when Steam won't say. */
    suspend fun forGame(context: Context, appId: Int, percents: Boolean = true): List<Achievement>? = withContext(Dispatchers.IO) {
        // An answer in an unexpected shape (a field missing or null) means "Steam won't say", never a crash.
        runCatching { forGameOrThrow(context, appId, percents) }.getOrNull()
    }

    /**
     * AppIDs of the games played lately (the last two weeks), most recent first: the Web API with a key,
     * otherwise the public profile page's "recently played" list. Empty when Steam won't say.
     */
    suspend fun recentlyPlayed(context: Context): List<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val a = account(context) ?: return@runCatching emptyList<Int>()
            if (!a.keyless) {
                get("https://api.steampowered.com/IPlayerService/GetRecentlyPlayedGames/v1/?key=${enc(a.apiKey)}&steamid=${a.steamId}&count=8")
                    ?.getAsJsonObject("response")?.getAsJsonArray("games")
                    ?.mapNotNull { it.asJsonObject.get("appid")?.asInt }.orEmpty()
            } else {
                val xml = getText("https://steamcommunity.com/profiles/${a.steamId}/?xml=1") ?: return@runCatching emptyList<Int>()
                val section = Regex("<mostPlayedGames>(.*?)</mostPlayedGames>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
                    ?: return@runCatching emptyList<Int>()
                Regex("<mostPlayedGame>(.*?)</mostPlayedGame>", RegexOption.DOT_MATCHES_ALL).findAll(section).mapNotNull { m ->
                    val g = m.groupValues[1]
                    Regex("/app/(\\d+)").find(tag(g, "gameLink").orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("/apps/(\\d+)/").find(g)?.groupValues?.get(1)?.toIntOrNull()
                }.distinct().toList()
            }
        }.getOrNull().orEmpty()
    }

    private fun forGameOrThrow(context: Context, appId: Int, percents: Boolean = true): List<Achievement>? {
        val a = account(context) ?: return null
        if (a.keyless) return forGameKeyless(a, appId, percents)
        val schema = get("https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=${enc(a.apiKey)}&appid=$appId&l=english")
            ?.getAsJsonObject("game")?.getAsJsonObject("availableGameStats")?.getAsJsonArray("achievements")
            ?: return emptyList()
        val player = get("https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?key=${enc(a.apiKey)}&steamid=${a.steamId}&appid=$appId")
            ?.getAsJsonObject("playerstats")?.getAsJsonArray("achievements")
        val mine: Map<String, Pair<Boolean, Long>> = player?.associate { e ->
            val o = e.asJsonObject
            val got: Boolean = o.get("achieved").asInt == 1
            val at: Long = (o.get("unlocktime")?.asLong ?: 0L) * 1000L
            o.get("apiname").asString to Pair(got, at)
        }.orEmpty()
        val global: Map<String, Float> = if (percents) globalPercents(appId) else emptyMap()
        return schema.map { e ->
            val o = e.asJsonObject
            val id = o.get("name").asString
            val (got, at) = mine[id] ?: Pair(false, 0L)
            Achievement(
                id, o.get("displayName")?.asString ?: id, o.get("description")?.asString.orEmpty(),
                o.get("icon")?.asString, o.get("icongray")?.asString, got, at, global[id]
            )
        }.sortedWith(compareByDescending<Achievement> { it.achieved }.thenByDescending { it.unlockedAt })
    }

    /** Global unlock rates (public, no key needed); missing → no percentages. */
    private fun globalPercents(appId: Int): Map<String, Float> = runCatching {
        get("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v0002/?gameid=$appId")
            ?.getAsJsonObject("achievementpercentages")?.getAsJsonArray("achievements")
            ?.associate { e -> val o = e.asJsonObject; o.get("name").asString to o.get("percent").asFloat }
    }.getOrNull().orEmpty()

    /**
     * No key: the game's public stats page (`/stats/<appid>/achievements/?xml=1`). Each <achievement> has
     * closed="1" when unlocked, iconClosed (colour) / iconOpen (grey), name, apiname, description and
     * unlockTimestamp. Null when the page isn't there (private profile, no stats page for the game).
     */
    private fun forGameKeyless(a: Account, appId: Int, percents: Boolean = true): List<Achievement>? {
        val xml = getText("https://steamcommunity.com/profiles/${a.steamId}/stats/$appId/achievements/?xml=1&l=english") ?: return null
        if (!xml.contains("<playerstats")) return null
        val global = if (percents) globalPercents(appId) else emptyMap()
        return Regex("<achievement\\b([^>]*)>(.*?)</achievement>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { m ->
            val attrs = m.groupValues[1]
            val body = m.groupValues[2]
            val apiName = tag(body, "apiname").orEmpty()
            val got = Regex("closed\\s*=\\s*\"1\"").containsMatchIn(attrs)
            val at = (tag(body, "unlockTimestamp")?.toLongOrNull() ?: 0L) * 1000L
            Achievement(
                apiName.ifBlank { tag(body, "name").orEmpty() }, tag(body, "name") ?: apiName, tag(body, "description").orEmpty(),
                tag(body, "iconClosed"), tag(body, "iconOpen"), got, at, global[apiName] ?: global[apiName.lowercase()]
            )
        }.toList().sortedWith(compareByDescending<Achievement> { it.achieved }.thenByDescending { it.unlockedAt })
    }
}
