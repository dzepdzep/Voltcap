package com.shelf.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The achievement side of a recap: what was unlocked in the period (from RetroAchievements when an
 * account is linked, otherwise from Voltcap's own [PlayJournal]), per game with count + percentage,
 * the completion changes (41% → 48%) and the milestones (mastered, beaten, "1 achievement remaining").
 */
object RecapAchievements {
    data class GameLine(
        val key: String?, val name: String, val icon: String?,
        /** Unlocked in the period. */
        val count: Int,
        /** Progress now (0 total = unknown). */
        val unlocked: Int, val total: Int
    )

    data class Change(val key: String?, val name: String, val before: Int, val after: Int)

    data class Result(
        val count: Int,
        val points: Int,
        val badges: List<String>,
        val games: List<GameLine>,
        val changes: List<Change>,
        /** Game name → line ("Mastered", "Beaten", "1 achievement remaining"…). */
        val milestones: List<Pair<String, String>>,
        val fromRa: Boolean
    ) {
        val isEmpty get() = count == 0 && milestones.isEmpty()
    }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Result>()

    private val raDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private fun parse(d: String?): Long? = d?.let { runCatching { synchronized(raDate) { raDate.parse(it)?.time } }.getOrNull() }

    suspend fun build(
        context: Context,
        period: YearRecap.Period,
        games: List<Game>,
        completion: Map<Int, RaCompletionEntry>
    ): Result = withContext(Dispatchers.IO) {
        // Past periods don't change: keep them. The current one is fetched again each time.
        val cacheable = period.to < System.currentTimeMillis() - 86_400_000L
        if (cacheable) cache[period.id]?.let { return@withContext it }
        val app = context.applicationContext
        val byRa = games.filter { it.raGameId != null }.associateBy { it.raGameId!! }
        val journal = PlayJournal.between(app, period.from, period.to)
        val creds = runCatching { RaCredentialsStore(app).load() }.getOrNull()
        val ra = creds?.let {
            RetroAchievementsRepository(createRetroAchievementsApi()).fetchEarnedBetween(it.username, it.apiKey, period.from, period.to)
        }

        val lines: List<GameLine>
        val count: Int
        val points: Int
        val badges: List<String>
        if (ra != null) {
            // Hardcore and softcore of the same achievement count once.
            val unique = ra.distinctBy { it.AchievementID }
            count = unique.size
            points = unique.sumOf { it.Points ?: 0 }
            badges = unique.sortedByDescending { it.Date }.mapNotNull { RaBadges.url(it.BadgeName) }.distinct().take(60)
            lines = unique.groupBy { it.GameID }.map { (gid, list) ->
                val g = gid?.let { byRa[it] }
                val c = gid?.let { completion[it] }
                GameLine(
                    key = g?.stableKey, name = g?.name ?: list.first().GameTitle ?: "Game",
                    icon = RaBadges.mediaUrl(list.first().GameIcon),
                    count = list.size, unlocked = c?.NumAwarded ?: 0, total = c?.MaxPossible ?: 0
                )
            }.sortedByDescending { it.count }
        } else {
            val unlocks = journal.filter { it.kind == PlayJournal.Kind.UNLOCK }
            count = unlocks.sumOf { it.count }
            points = 0
            badges = unlocks.sortedByDescending { it.at }.flatMap { it.badges }.distinct().take(60)
            lines = unlocks.groupBy { it.key }.map { (k, list) ->
                val last = list.maxByOrNull { it.at }!!
                GameLine(k, games.firstOrNull { it.stableKey == k }?.name ?: last.name ?: k, null, list.sumOf { it.count }, last.unlocked, last.total)
            }.sortedByDescending { it.count }
        }

        // Completion changes: the journal knows before → after for sessions played through Voltcap; for the
        // period still running, RetroAchievements' totals fill in the rest.
        val changes = ArrayList<Change>()
        journal.filter { it.kind == PlayJournal.Kind.UNLOCK && it.percentBefore >= 0 && it.percentAfter >= 0 }
            .groupBy { it.key }.forEach { (k, list) ->
                val first = list.minByOrNull { it.at }!!
                val last = list.maxByOrNull { it.at }!!
                if (last.percentAfter > first.percentBefore) {
                    changes += Change(k, games.firstOrNull { it.stableKey == k }?.name ?: last.name ?: k, first.percentBefore, last.percentAfter)
                }
            }
        if (ra != null && period.offset == 0) {
            lines.filter { l -> l.total > 0 && changes.none { it.key != null && it.key == l.key } }.forEach { l ->
                val before = ((l.unlocked - l.count).coerceAtLeast(0)) * 100 / l.total
                val after = l.unlocked * 100 / l.total
                if (after > before) changes += Change(l.key, l.name, before, after)
            }
        }

        // Milestones: masteries and beatens from RetroAchievements, then the journal's own lines.
        val milestones = ArrayList<Pair<String, String>>()
        completion.values.forEach { c ->
            val at = parse(c.HighestAwardDate) ?: return@forEach
            if (at !in period.from..period.to) return@forEach
            val label = when (c.HighestAwardKind) {
                "mastered" -> "Mastered"
                "completed" -> "Completed"
                "beaten-hardcore" -> "Beaten (hardcore)"
                "beaten-softcore" -> "Beaten"
                else -> return@forEach
            }
            milestones += (c.GameID?.let { byRa[it]?.name } ?: c.Title ?: "Game") to label
        }
        journal.filter { it.kind == PlayJournal.Kind.MASTERED || it.kind == PlayJournal.Kind.MILESTONE }.sortedBy { it.at }.forEach { e ->
            val name = games.firstOrNull { it.stableKey == e.key }?.name ?: e.name ?: e.key
            val text = if (e.kind == PlayJournal.Kind.MASTERED) "Mastered" else e.text ?: return@forEach
            if (milestones.none { it.first == name && it.second.startsWith(text.take(8)) }) milestones += name to text
        }
        lines.filter { it.total > 0 && it.total - it.unlocked in 1..2 }.forEach { l ->
            val left = l.total - l.unlocked
            milestones += l.name to "$left achievement${if (left == 1) "" else "s"} remaining"
        }

        val result = Result(count, points, badges, lines, changes.sortedByDescending { it.after - it.before }, milestones.distinct().take(10), ra != null)
        if (cacheable) cache[period.id] = result
        result
    }
}
