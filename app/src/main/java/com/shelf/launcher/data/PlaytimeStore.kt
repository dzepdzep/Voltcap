package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Total time played per game (Game.stableKey → milliseconds), measured from
 * launching a game in Shelf to coming back to Shelf.
 *
 * The open session is saved, not kept in memory: Android often kills the
 * launcher while a heavy emulator runs, and the session must survive that.
 */
class PlaytimeStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = context.getSharedPreferences("playtime", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, Long>>() {}.type

    init { cleanUpOnce() }

    fun loadTotals(): Map<String, Long> {
        val json = prefs.getString(KEY_TOTALS, null) ?: return emptyMap()
        val all = runCatching { gson.fromJson<Map<String, Long>>(json, type) }.getOrNull() ?: emptyMap()
        return all.filterKeys { !StatsExclusions.excluded(app, it) }
    }

    fun startSession(stableKey: String, startedAt: Long) {
        // Voltcap itself, other launchers and system apps never count as play.
        if (StatsExclusions.excluded(app, stableKey)) return
        prefs.edit().putString(KEY_OPEN_GAME, stableKey).putLong(KEY_OPEN_SINCE, startedAt).apply()
    }

    /**
     * Closes the open session, if any, and adds it to that game's total.
     * Returns (game, counted duration) — or null when there was no session or
     * it was too short to count (a crash, or backing straight out).
     */
    fun endSession(now: Long, activeMs: Long? = null): Pair<String, Long>? {
        val key = prefs.getString(KEY_OPEN_GAME, null) ?: return null
        val since = prefs.getLong(KEY_OPEN_SINCE, 0L)
        prefs.edit().remove(KEY_OPEN_GAME).remove(KEY_OPEN_SINCE).apply()

        // [activeMs]: the time the game was really in front (usage record), when known.
        // The clock moved back during the game (time zone, network time): nothing sensible to count,
        // and coerceIn would throw with an upper bound below zero.
        if (since <= 0L || now <= since) return null
        val duration = activeMs?.coerceIn(0L, now - since) ?: (now - since)
        if (duration < MIN_SESSION_MS) return null
        // A device left on overnight in-game shouldn't add 10 hours.
        val counted = minOf(duration, MAX_SESSION_MS)

        val totals = loadTotals().toMutableMap()
        totals[key] = (totals[key] ?: 0L) + counted
        // Every session is also logged (for Settings › Stats: weeks, streaks).
        val log = (loadSessions() + Session(key, now - counted, counted)).takeLast(MAX_SESSIONS)
        prefs.edit().putString(KEY_TOTALS, gson.toJson(totals)).putString(KEY_SESSIONS, gson.toJson(log)).apply()
        return key to counted
    }

    /** One play session: which game, when it started, how long (ms). */
    data class Session(val key: String, val start: Long, val ms: Long)

    private val sessionsType = object : TypeToken<List<Session>>() {}.type

    /** Sessions played since this log began (older play only counts in the totals). */
    fun loadSessions(): List<Session> = loadSessionsRaw().filter { !StatsExclusions.excluded(app, it.key) }

    private fun loadSessionsRaw(): List<Session> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Session>>(json, sessionsType) }.getOrNull().orEmpty()
    }

    /** Once: drops what was already recorded for Voltcap, launchers and system apps. */
    private fun cleanUpOnce() {
        if (prefs.getBoolean(KEY_CLEANED, false)) return
        runCatching {
            val totals = runCatching { gson.fromJson<Map<String, Long>>(prefs.getString(KEY_TOTALS, null), type) }.getOrNull().orEmpty()
            val keptTotals = totals.filterKeys { !StatsExclusions.excluded(app, it) }
            val sessions = loadSessionsRaw()
            val keptSessions = sessions.filter { !StatsExclusions.excluded(app, it.key) }
            val e = prefs.edit()
            if (keptTotals.size != totals.size) e.putString(KEY_TOTALS, gson.toJson(keptTotals))
            if (keptSessions.size != sessions.size) e.putString(KEY_SESSIONS, gson.toJson(keptSessions))
            prefs.getString(KEY_OPEN_GAME, null)?.let { if (StatsExclusions.excluded(app, it)) e.remove(KEY_OPEN_GAME).remove(KEY_OPEN_SINCE) }
            e.putBoolean(KEY_CLEANED, true).apply()
        }
    }

    companion object {
        private const val KEY_TOTALS = "totals_json"
        private const val KEY_OPEN_GAME = "open_game"
        private const val KEY_OPEN_SINCE = "open_since"
        private const val KEY_SESSIONS = "sessions_json"
        private const val KEY_CLEANED = "cleaned_exclusions_v1"
        private const val MAX_SESSIONS = 3000
        private const val MIN_SESSION_MS = 20_000L
        private const val MAX_SESSION_MS = 8L * 60 * 60 * 1000

        /** "42 min", "3.5 h", "120 h". */
        fun format(ms: Long): String {
            val minutes = ms / 60_000
            return when {
                // Update 144: short enough for the narrow time columns, and no lower-case label on its own.
                minutes < 1 -> "<1 min"
                minutes < 60 -> "$minutes min"
                minutes < 600 -> {
                    val tenths = Math.round(ms / 360_000.0)
                    if (tenths % 10 == 0L) "${tenths / 10} h" else "${tenths / 10}.${tenths % 10} h"
                }
                else -> "${minutes / 60} h"
            }
        }
    }
}
