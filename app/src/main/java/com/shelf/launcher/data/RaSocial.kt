package com.shelf.launcher.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** RetroAchievements friends (people you follow) and leaderboards, through the public Web API. */
object RaSocial {
    data class Friend(
        val user: String, val points: Int, val followsYou: Boolean, val playing: String?, val gameId: Int?, val avatar: String,
        /** Their most recent mastered (or, failing that, beaten) game: title, console, icon URL, "Mastered"/"Beaten". */
        val lastAward: Award? = null
    )
    data class Award(val title: String, val console: String, val icon: String?, val kind: String, val gameId: Int?)
    data class Leaderboard(val id: Int, val title: String, val description: String, val top: String?, val topUser: String?, val myRank: Int?, val myScore: String?)
    data class Entry(val rank: Int, val user: String, val score: String)

    private const val BASE = "https://retroachievements.org/API/"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(path: String): JsonObject? = runCatching {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000; conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Voltcap")
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() }).asJsonObject
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    // A field of an unexpected type (an object, "" where a number goes) reads as missing instead of throwing.
    private fun JsonObject.s(k: String) = runCatching { get(k)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()
    private fun JsonObject.i(k: String) = runCatching { get(k)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()
    private fun JsonObject.results(): JsonArray? = get("Results")?.takeIf { it.isJsonArray }?.asJsonArray
    /** A nested object, or null when it's missing, null or not an object (getAsJsonObject would throw). */
    private fun JsonObject.obj(k: String): JsonObject? = get(k)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.arr(k: String): JsonArray? = get(k)?.takeIf { it.isJsonArray }?.asJsonArray

    /** (user, when, list): never shown for another account. */
    private var cached: Triple<String, Long, List<Friend>>? = null

    /** Like [friends], but reuses the list for 10 minutes so opening the profile again is instant. */
    suspend fun friendsCached(c: RaCredentials): List<Friend>? {
        val mine = cached?.takeIf { it.first.equals(c.username, true) }
        mine?.let { (_, at, list) -> if (System.currentTimeMillis() - at < 10 * 60_000L) return list }
        val fresh = friends(c) ?: return mine?.third
        cached = Triple(c.username, System.currentTimeMillis(), fresh)
        return fresh
    }

    /** The newest mastery (hardcore completion first) or beaten award on a user's profile. */
    private fun lastAward(user: String, c: RaCredentials): Award? {
        val list = get("API_GetUserAwards.php?u=${enc(user)}&y=${enc(c.apiKey)}")?.arr("VisibleUserAwards") ?: return null
        val awards = list.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
        fun pick(type: String) = awards.filter { it.s("AwardType") == type }.maxByOrNull { it.s("AwardedAt").orEmpty() }
        val o = pick("Mastery/Completion") ?: pick("Game Beaten") ?: return null
        val kind = when (o.s("AwardType")) {
            "Mastery/Completion" -> if ((o.i("AwardDataExtra") ?: 0) == 1) "Mastered" else "Completed"
            else -> if ((o.i("AwardDataExtra") ?: 0) == 1) "Beaten" else "Beaten (softcore)"
        }
        return Award(
            o.s("Title").orEmpty(), o.s("ConsoleName").orEmpty(),
            o.s("ImageIcon")?.let { if (it.startsWith("http")) it else "https://media.retroachievements.org$it" },
            kind, o.i("AwardData")
        )
    }

    /** People you follow, with what they're playing now (newest activity first). */
    suspend fun friends(c: RaCredentials): List<Friend>? = withContext(Dispatchers.IO) {
        val list = get("API_GetUsersIFollow.php?y=${enc(c.apiKey)}&c=100")?.results() ?: return@withContext null
        val base = list.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.map { o ->
            Triple(o.s("User").orEmpty(), (o.i("Points") ?: 0) + 0, runCatching { o.get("IsFollowingMe")?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull() == true)
        }.filter { it.first.isNotBlank() }
        coroutineScope {
            base.take(30).map { (user, points, follows) ->
                async {
                    val p = get("API_GetUserProfile.php?u=${enc(user)}&y=${enc(c.apiKey)}")
                    val award = runCatching { lastAward(user, c) }.getOrNull()
                    Friend(user, points, follows, p?.s("RichPresenceMsg"), p?.i("LastGameID"), "https://media.retroachievements.org/UserPic/$user.png", award)
                }
            }.awaitAll()
        }
    }

    /** RetroAchievements' Achievement of the Week: the achievement, its game, and how many have it. */
    data class WeeklyPick(
        val title: String, val description: String, val points: Int, val badge: String?,
        val gameId: Int?, val gameTitle: String, val console: String, val gameIcon: String?,
        val startAt: String?, val players: Int, val unlocks: Int, val youHaveIt: Boolean
    )

    private var weeklyCache: Triple<String, Long, WeeklyPick>? = null

    suspend fun achievementOfTheWeek(c: RaCredentials): WeeklyPick? = withContext(Dispatchers.IO) {
        weeklyCache?.takeIf { it.first.equals(c.username, true) }?.let { (_, at, w) -> if (System.currentTimeMillis() - at < 60 * 60_000L) return@withContext w }
        val o = get("API_GetAchievementOfTheWeek.php?y=${enc(c.apiKey)}") ?: return@withContext null
        val ach = o.obj("Achievement") ?: return@withContext null
        val game = o.obj("Game")
        val gameId = game?.i("ID")
        // The badge isn't always in this answer; the game's icon and the achievement's badge come from the game's info.
        val info = gameId?.let { get("API_GetGameExtended.php?i=$it&y=${enc(c.apiKey)}") }
        val badgeName = ach.s("BadgeName") ?: info?.obj("Achievements")?.obj((ach.i("ID") ?: 0).toString())?.s("BadgeName")
        val me = c.username
        val pick = WeeklyPick(
            title = ach.s("Title").orEmpty(), description = ach.s("Description").orEmpty(), points = ach.i("Points") ?: 0,
            badge = badgeName?.let { "https://media.retroachievements.org/Badge/$it.png" },
            gameId = gameId, gameTitle = game?.s("Title").orEmpty(),
            console = o.obj("Console")?.s("Title").orEmpty(),
            gameIcon = info?.s("ImageIcon")?.let { if (it.startsWith("http")) it else "https://media.retroachievements.org$it" },
            startAt = o.s("StartAt"), players = o.i("TotalPlayers") ?: 0, unlocks = o.i("UnlocksCount") ?: 0,
            youHaveIt = o.arr("Unlocks")?.any { it.isJsonObject && it.asJsonObject.s("User").equals(me, ignoreCase = true) } == true
        )
        weeklyCache = Triple(c.username, System.currentTimeMillis(), pick)
        pick
    }

    /** The game's leaderboards with the top score and your own rank. */
    suspend fun leaderboards(c: RaCredentials, gameId: Int): List<Leaderboard>? = withContext(Dispatchers.IO) {
        val all = get("API_GetGameLeaderboards.php?i=$gameId&c=200&y=${enc(c.apiKey)}")?.results() ?: return@withContext null
        val mine = get("API_GetUserGameLeaderboards.php?i=$gameId&u=${enc(c.username)}&c=200&y=${enc(c.apiKey)}")?.results()
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }?.associate { o ->
                val e = o.obj("UserEntry")
                (o.i("ID") ?: 0) to (e?.i("Rank") to e?.s("FormattedScore"))
            }.orEmpty()
        all.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.map { o ->
            val id = o.i("ID") ?: 0
            val top = o.obj("TopEntry")
            Leaderboard(id, o.s("Title").orEmpty(), o.s("Description").orEmpty(), top?.s("FormattedScore"), top?.s("User"), mine[id]?.first, mine[id]?.second)
        }
    }

    suspend fun entries(c: RaCredentials, leaderboardId: Int): List<Entry> = withContext(Dispatchers.IO) {
        get("API_GetLeaderboardEntries.php?i=$leaderboardId&c=25&y=${enc(c.apiKey)}")?.results()
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }?.map { o -> Entry(o.i("Rank") ?: 0, o.s("User").orEmpty(), o.s("FormattedScore").orEmpty()) }
            .orEmpty()
    }
}
