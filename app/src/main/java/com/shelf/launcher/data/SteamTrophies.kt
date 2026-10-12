package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Steam's side of the Trophy vault: your latest Steam unlocks and the Steam games you've 100 %'d. Steam has
 * no "everything I unlocked lately" call, so this looks at the games you played most recently — the
 * profile's recently played list (public page, or the Web API with a key) plus the Steam games you started
 * from Voltcap — at most [MAX_GAMES], each re-read at most every [TTL_MS]. While you play a Steam game it is
 * re-read every 90 s ([refreshGame], from the unlock watch), so new unlocks reach the vault at once.
 */
object SteamTrophies {
    /** One unlocked Steam achievement. [at] in ms. */
    data class Unlock(val appId: Int, val id: String, val icon: String, val at: Long)

    /** A Steam game with every achievement unlocked; [at] = when the last one came, in ms. */
    data class Mastered(val appId: Int, val at: Long)

    /** Latest unlocks first (last 30 days, at most 30) and 100 %'d games, newest first. Compose state. */
    val recent = mutableStateOf<List<Unlock>>(emptyList())
    val mastered = mutableStateOf<List<Mastered>>(emptyList())

    private const val MAX_GAMES = 8
    private const val TTL_MS = 30 * 60_000L
    private const val WINDOW_MS = 30L * 24 * 60 * 60_000L

    private class Entry(val list: List<SteamAchievements.Achievement>, val at: Long)

    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Entry>()
    @Volatile private var recentAppsAt = 0L
    @Volatile private var recentApps: List<Int> = emptyList()

    /**
     * Brings the lists up to date for the recently played games ([played]: Steam AppIDs Voltcap started,
     * most recent first). Cheap when called often: nothing is fetched again within [TTL_MS].
     */
    suspend fun refresh(context: Context, played: List<Int>) = withContext(Dispatchers.IO) {
        if (SteamAchievements.account(context) == null) return@withContext
        val now = System.currentTimeMillis()
        if (now - recentAppsAt > TTL_MS) {
            recentApps = runCatching { SteamAchievements.recentlyPlayed(context) }.getOrNull().orEmpty()
            recentAppsAt = now
        }
        val apps = (recentApps + played).distinct().take(MAX_GAMES)
        for (app in apps) {
            val stale = cache[app]?.let { now - it.at > TTL_MS } ?: true
            if (stale) fetch(context, app)
        }
        publish()
    }

    /**
     * Re-reads one game now (the one being played); returns the achievements unlocked since it was last
     * read (empty the first time, so old unlocks never count as new).
     */
    suspend fun refreshGame(context: Context, appId: Int): List<SteamAchievements.Achievement> = withContext(Dispatchers.IO) {
        if (SteamAchievements.account(context) == null) return@withContext emptyList()
        val before = cache[appId]?.list
        val after = fetch(context, appId) ?: return@withContext emptyList()
        publish()
        if (before == null) emptyList()
        else {
            val had = before.filter { it.achieved }.map { it.id }.toSet()
            after.filter { it.achieved && it.id !in had }
        }
    }

    private suspend fun fetch(context: Context, appId: Int): List<SteamAchievements.Achievement>? {
        val list = SteamAchievements.forGame(context, appId, percents = false) ?: return null
        cache[appId] = Entry(list, System.currentTimeMillis())
        return list
    }

    private fun publish() {
        val all = cache.toMap()
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        recent.value = all.flatMap { (app, e) ->
            e.list.filter { it.achieved && it.unlockedAt >= cutoff && !it.icon.isNullOrBlank() }
                .map { Unlock(app, it.id, it.icon!!, it.unlockedAt) }
        }.sortedByDescending { it.at }.take(30)
        mastered.value = all.mapNotNull { (app, e) ->
            if (e.list.isNotEmpty() && e.list.all { it.achieved }) Mastered(app, e.list.maxOf { it.unlockedAt }) else null
        }.sortedByDescending { it.at }
    }

    /** Forget everything (the Steam account was disconnected). */
    fun clear() {
        cache.clear()
        recentApps = emptyList(); recentAppsAt = 0L
        recent.value = emptyList(); mastered.value = emptyList()
    }
}
