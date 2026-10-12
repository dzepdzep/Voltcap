package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Voltcap has already shown of each game's achievements, so an unlock is only ever "NEW" once:
 *
 *  - [observe] compares a fresh achievement list with the last one. Achievements unlocked since the
 *    previous check become *pending* (their automatic unlock animation plays once, then never again)
 *    and *glowing* (a NEW mark until the list has been looked at).
 *  - The first time a game is seen, everything already unlocked counts as seen: old unlocks are never
 *    replayed. Unlocks reported live while a game runs ([addFresh]) still count as new.
 *  - Reaching 100 % is celebrated once ([takeCompletion]).
 *
 * Keys: "ra:<RetroAchievements game ID>" or "steam:<app ID>". Plain prefs, device-local.
 */
object AchievementSeen {

    data class Rec(
        val seen: Set<Int> = emptySet(),
        /** Unlocked since the previous check, automatic animation not played yet. */
        val pending: Set<Int> = emptySet(),
        /** NEW until the list is viewed. */
        val glow: Set<Int> = emptySet(),
        /** False until a full list has been compared once (live unlocks may arrive before that). */
        val seeded: Boolean = false,
        val total: Int = 0,
        /** 100 % already celebrated. */
        val completeShown: Boolean = false,
        /** The 100 % celebration is waiting to be played. */
        val completePending: Boolean = false,
        /** Completion before the latest change (for "41 % → 48 %"), and when it changed. */
        val previousPercent: Int = -1,
        val changedAt: Long = 0L,
        /** Unlock order as Voltcap saw it (id → millis), newest kept (timeline fallback). */
        val seenAt: Map<Int, Long> = emptyMap()
    )

    private const val PREFS = "achievement_seen"
    private const val KEY = "by_game"
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, Rec>>() {}.type
    private val _all = MutableStateFlow<Map<String, Rec>>(emptyMap())
    val all: StateFlow<Map<String, Rec>> = _all.asStateFlow()
    @Volatile private var loaded = false

    fun keyRa(gameId: Int) = "ra:$gameId"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val json = prefs(context).getString(KEY, null) ?: return
        @Suppress("SENSELESS_COMPARISON")
        _all.value = runCatching { gson.fromJson<Map<String, Rec>>(json, type) }.getOrNull().orEmpty()
            .filterValues { it != null }
            .mapValues { (_, r) ->
                // Gson leaves sets missing from older data null.
                Rec(
                    seen = r.seen ?: emptySet(), pending = r.pending ?: emptySet(), glow = r.glow ?: emptySet(),
                    seeded = r.seeded, total = r.total, completeShown = r.completeShown, completePending = r.completePending,
                    previousPercent = r.previousPercent, changedAt = r.changedAt, seenAt = r.seenAt ?: emptyMap()
                )
            }
    }

    fun get(context: Context, key: String): Rec? { load(context); return _all.value[key] }

    @Synchronized
    private fun edit(context: Context, key: String, f: (Rec?) -> Rec?) {
        load(context)
        val old = _all.value[key]
        val new = f(old)
        if (new == old) return
        _all.value = if (new == null) _all.value - key else _all.value + (key to new)
        prefs(context).edit().putString(KEY, gson.toJson(_all.value)).apply()
    }

    /**
     * A fresh list for [key]: [unlocked] IDs out of [total]. Returns the IDs that are new since the
     * previous check (empty the first time a game is seen — old unlocks never count as new).
     */
    fun observe(context: Context, key: String, unlocked: Set<Int>, total: Int): Set<Int> {
        var fresh = emptySet<Int>()
        val now = System.currentTimeMillis()
        edit(context, key) { old ->
            val pct = if (total > 0) unlocked.size * 100 / total else 0
            if (old == null || !old.seeded) {
                // First full look: what's unlocked is old news — except live unlocks noted already.
                val live = old?.pending.orEmpty() intersect unlocked
                fresh = live
                Rec(
                    seen = unlocked, pending = live, glow = live + old?.glow.orEmpty().intersect(unlocked), seeded = true,
                    total = total, completeShown = total > 0 && unlocked.size >= total && live.isEmpty(),
                    completePending = total > 0 && unlocked.size >= total && live.isNotEmpty(),
                    previousPercent = if (live.isNotEmpty() && total > 0) (unlocked.size - live.size) * 100 / total else pct,
                    changedAt = if (live.isNotEmpty()) now else 0L,
                    seenAt = old?.seenAt.orEmpty()
                )
            } else {
                fresh = unlocked - old.seen
                // A reset on RetroAchievements (fewer unlocked): just follow it, nothing to celebrate.
                val complete = total > 0 && unlocked.size >= total
                val oldPct = if (old.total > 0) old.seen.size * 100 / old.total else 0
                old.copy(
                    seen = unlocked,
                    pending = (old.pending + fresh) intersect unlocked,
                    glow = (old.glow + fresh) intersect unlocked,
                    total = total,
                    completePending = old.completePending || (complete && !old.completeShown && fresh.isNotEmpty()),
                    completeShown = if (!complete) false else old.completeShown,
                    previousPercent = if (fresh.isNotEmpty()) oldPct else old.previousPercent,
                    changedAt = if (fresh.isNotEmpty()) now else old.changedAt,
                    seenAt = (old.seenAt + fresh.associateWith { now }).entries.sortedByDescending { it.value }.take(400).associate { it.toPair() }
                )
            }
        }
        return fresh
    }

    /** Unlocks reported live while the game runs (before the full list is fetched again). */
    fun addFresh(context: Context, key: String, ids: Collection<Int>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        edit(context, key) { old ->
            val r = old ?: Rec()
            val really = ids.filter { it !in r.seen }
            if (really.isEmpty()) r
            else r.copy(
                pending = r.pending + really, glow = r.glow + really,
                seenAt = r.seenAt + really.associateWith { now }
            )
        }
    }

    /** IDs whose automatic unlock animation hasn't played yet. */
    fun pending(context: Context, key: String): Set<Int> = get(context, key)?.pending.orEmpty()

    /** The automatic animation for [ids] has played: never again (manual replay is separate). */
    fun markAnimated(context: Context, key: String, ids: Collection<Int>) =
        edit(context, key) { it?.copy(pending = it.pending - ids.toSet()) }

    /** The list was looked at: NEW marks go. */
    fun markViewed(context: Context, key: String) = edit(context, key) { it?.copy(glow = emptySet()) }

    /** True once when the game reached 100 % since the last look (the celebration plays then). */
    fun takeCompletion(context: Context, key: String): Boolean {
        var take = false
        edit(context, key) { r ->
            if (r != null && r.completePending) { take = true; r.copy(completePending = false, completeShown = true) } else r
        }
        return take
    }

    /** Games with unlocks not looked at yet (for "NEW" marks on covers). */
    fun glowingKeys(): Set<String> = _all.value.filterValues { it.glow.isNotEmpty() }.keys
}

/** Unlocks from the session that just ended (status-bar trophy). */
data class ReturnTrophies(val gameKey: String, val raGameId: Int?, val count: Int, val at: Long = System.currentTimeMillis())

/** "31/70 · 44%" — always count and percentage; near the end "4 remaining" is added by [remainingNote]. */
fun achievementProgress(unlocked: Int, total: Int): String {
    if (total <= 0) return "$unlocked"
    val pct = (unlocked * 100L / total).toInt()
    return "$unlocked/$total · $pct%"
}

/** "4 remaining" when a game is close to complete (≥ 75 %, or at most 5 left); null otherwise or when done. */
fun remainingNote(unlocked: Int, total: Int): String? {
    if (total <= 0 || unlocked >= total) return null
    val left = total - unlocked
    return if (left <= 5 || unlocked * 4 >= total * 3) "$left remaining" else null
}
