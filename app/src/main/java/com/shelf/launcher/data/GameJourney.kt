package com.shelf.launcher.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A game's story in Voltcap (Game Journey): first played → sessions → achievements → milestones →
 * completion / mastery, from the play-time log, the [PlayJournal] and RetroAchievements' unlock dates.
 * Plus the single contextual milestone line the normal game UI shows ([contextLine]).
 */
object GameJourney {
    enum class Kind { FIRST, SESSIONS, ACHIEVEMENT, MILESTONE, COMPLETION, NEXT }

    data class Step(
        val kind: Kind,
        val at: Long?,
        val title: String,
        val detail: String? = null,
        val badges: List<String> = emptyList(),
        /** False for the step still ahead (the way to mastery). */
        val done: Boolean = true
    )

    private val raDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    internal fun parseRa(d: String?): Long? = d?.let { runCatching { synchronized(raDate) { raDate.parse(it)?.time } }.getOrNull() }

    /** Blocking (reads the logs): call off the main thread. */
    fun build(
        context: Context,
        game: Game,
        achievements: List<RaAchievementDetail>,
        completion: RaCompletionEntry?
    ): List<Step> {
        val app = context.applicationContext
        val sessions = runCatching { PlaytimeStore(app).loadSessions().filter { it.key == game.stableKey } }.getOrDefault(emptyList())
        val journal = PlayJournal.forGame(app, game.stableKey)
        val unlocked = achievements.filter { it.isUnlocked }
            .mapNotNull { a -> parseRa(a.dateEarnedHardcore ?: a.dateEarned)?.let { it to a } }.sortedBy { it.first }
        val steps = ArrayList<Step>()

        val first = listOfNotNull(sessions.minOfOrNull { it.start }, unlocked.firstOrNull()?.first).minOrNull()
        if (first != null) steps += Step(Kind.FIRST, first, "First played")
        if (sessions.isNotEmpty()) {
            val total = sessions.sumOf { it.ms }
            val longest = sessions.maxOf { it.ms }
            steps += Step(
                Kind.SESSIONS, sessions.maxOf { it.start },
                "${sessions.size} session${if (sessions.size == 1) "" else "s"}",
                "${PlaytimeStore.format(total)} in total · longest ${PlaytimeStore.format(longest)}"
            )
        }
        // 0.6.15 (#120): play time synced from another handheld (Drive) has no session in this device's log — the
        // Thor showed "Not played yet" under "Playtime 10 min". Say where the time came from instead.
        val syncedMs = if (sessions.isEmpty()) runCatching { PlaytimeStore(app).loadTotals()[game.stableKey] ?: 0L }.getOrDefault(0L) else 0L
        val playedElsewhere = syncedMs >= 60_000L
        if (playedElsewhere && first == null) {
            steps += Step(Kind.SESSIONS, null, "Played on another device", "${PlaytimeStore.format(syncedMs)} in total · sessions here are listed from the next one")
        }
        unlocked.firstOrNull()?.let { (at, a) ->
            steps += Step(Kind.ACHIEVEMENT, at, "First achievement", a.title, listOfNotNull(RaBadges.url(a.badgeName)))
        }
        // Unlock days: from the journal when Voltcap saw them happen (with the completion change), otherwise
        // grouped by day from RetroAchievements' dates. The latest few.
        val journalUnlocks = journal.filter { it.kind == PlayJournal.Kind.UNLOCK }
        if (journalUnlocks.isNotEmpty()) {
            journalUnlocks.takeLast(5).forEach { e ->
                val change = if (e.percentBefore >= 0 && e.percentAfter > e.percentBefore) " · ${e.percentBefore}% → ${e.percentAfter}%" else ""
                steps += Step(Kind.ACHIEVEMENT, e.at, "+${e.count} achievement${if (e.count == 1) "" else "s"}",
                    (if (e.total > 0) achievementProgress(e.unlocked, e.total) else "") + change, e.badges)
            }
        } else if (unlocked.size > 1) {
            unlocked.drop(1).groupBy { it.first / 86_400_000L }.entries.toList().takeLast(4).forEach { (_, list) ->
                steps += Step(Kind.ACHIEVEMENT, list.last().first, "+${list.size} achievement${if (list.size == 1) "" else "s"}",
                    list.mapNotNull { it.second.title }.take(2).joinToString(", "), list.mapNotNull { RaBadges.url(it.second.badgeName) }.take(5))
            }
        }
        journal.filter { it.kind == PlayJournal.Kind.MILESTONE }.forEach { e ->
            steps += Step(Kind.MILESTONE, e.at, e.text ?: "Milestone")
        }
        // Completion.
        val total = achievements.size.takeIf { it > 0 } ?: completion?.MaxPossible ?: 0
        val earned = if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else completion?.NumAwarded ?: 0
        val award = completion?.HighestAwardKind
        val awardAt = parseRa(completion?.HighestAwardDate)
        when {
            award == "mastered" || (total > 0 && earned >= total) ->
                steps += Step(Kind.COMPLETION, awardAt ?: unlocked.lastOrNull()?.first, "Mastered", "Every achievement · ${achievementProgress(earned, total)}")
            award == "completed" -> steps += Step(Kind.COMPLETION, awardAt, "Completed")
            award?.startsWith("beaten") == true -> {
                steps += Step(Kind.COMPLETION, awardAt, if (award == "beaten-hardcore") "Beaten (hardcore)" else "Beaten")
                if (total > 0) steps += Step(Kind.NEXT, null, "Mastery", "${achievementProgress(earned, total)} · ${total - earned} to go", done = false)
            }
            total > 0 && (first != null || playedElsewhere) -> steps += Step(Kind.NEXT, null, "Mastery", "${achievementProgress(earned, total)} · ${total - earned} to go", done = false)
            first == null && !playedElsewhere -> steps += Step(Kind.NEXT, null, "Not played yet", "Your journey starts with the first session", done = false)
        }
        return steps.sortedWith(compareBy<Step> { it.at == null }.thenBy { it.at ?: 0L })
    }

    /**
     * The one milestone worth showing next to a game right now (only one at a time):
     * mastered lately, the last few achievements, the last session's unlocks or completion gain, the
     * last session's length.
     */
    fun contextLine(
        context: Context,
        key: String,
        earned: Int,
        total: Int,
        completion: RaCompletionEntry?,
        lastPlayed: Long?
    ): String? {
        val now = System.currentTimeMillis()
        fun ago(at: Long): String {
            val d = ((now - at) / 86_400_000L).toInt()
            return when (d) { 0 -> "today"; 1 -> "yesterday"; in 2..6 -> "$d days ago"; else -> "on " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(at)) }
        }
        val awardAt = parseRa(completion?.HighestAwardDate)
        if (completion?.HighestAwardKind == "mastered" && awardAt != null && now - awardAt < 14 * 86_400_000L) return "Mastered ${ago(awardAt)}"
        if (total > 0 && total - earned == 1) return "1 achievement remaining"
        val app = context.applicationContext
        val lastUnlock = PlayJournal.forGame(app, key).lastOrNull { it.kind == PlayJournal.Kind.UNLOCK }
        if (lastUnlock != null && now - lastUnlock.at < 7 * 86_400_000L) {
            val gain = if (lastUnlock.percentBefore >= 0) lastUnlock.percentAfter - lastUnlock.percentBefore else 0
            return if (gain >= 5) "Completion +$gain%"
            else "${lastUnlock.count} achievement${if (lastUnlock.count == 1) "" else "s"} unlocked"
        }
        if (total > 0 && total - earned in 2..5) return "${total - earned} achievements remaining"
        if (total > 0 && earned >= total) return "Mastered"
        val last = runCatching { PlaytimeStore(app).loadSessions().lastOrNull { it.key == key } }.getOrNull()
        if (last != null && last.ms >= 60_000L) return "Last session: ${PlaytimeStore.format(last.ms)}"
        if (lastPlayed == null) return null
        return null
    }
}
