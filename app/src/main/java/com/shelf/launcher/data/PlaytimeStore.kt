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
class PlaytimeStore(
    context: Context,
    /** Update 332: the first profile's (Google Drive syncs only that one's), whoever is playing. */
    main: Boolean = false
) {
    private val app = context.applicationContext
    private val mainOnly = main
    // (Update 332: the playing profile's own — Profiles.fileFor; the first profile's is the old "playtime".)
    private val prefs get() = app.getSharedPreferences(if (mainOnly) "playtime" else Profiles.run { ensure(app); fileFor("playtime") }, Context.MODE_PRIVATE)
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
        // A device left on overnight in-game shouldn't add 10 hours. (Update 274, Toni — "Silver only says 3 hours": only
        // when the wall clock is all there is. With usage access [activeMs] is the time the game was really in front,
        // screen-off left out, so a game kept open for days and played a few hours at a time counts all of it; it was
        // cut to 8 h.)
        val counted = if (activeMs != null) minOf(duration, MAX_MEASURED_MS) else minOf(duration, MAX_SESSION_MS)
        add(key, now - counted, counted)
        return key to counted
    }

    /**
     * Update 274: time played without a Voltcap launch — Home pressed mid-game (Voltcap is the home screen) and the game
     * picked again from Recents. That ended the session at Home and the rest was never counted. [start] / [ms]: when and
     * how long the game was really in front (RunningGames.foregroundMs). Under 20 s doesn't count.
     */
    fun addPlayed(key: String, start: Long, ms: Long): Boolean {
        if (StatsExclusions.excluded(app, key) || ms < MIN_SESSION_MS) return false
        add(key, start, minOf(ms, MAX_MEASURED_MS))
        return true
    }

    /** Update 274: Voltcap went behind another app / the screen went off (MainActivity.onStop): kept until it's back. */
    fun markAway(at: Long) { if (prefs.getLong(KEY_AWAY_SINCE, 0L) <= 0L) prefs.edit().putLong(KEY_AWAY_SINCE, at).apply() }
    /** Update 274: when Voltcap last left the front (0: unknown), and forgetting it. */
    fun awaySince(): Long = prefs.getLong(KEY_AWAY_SINCE, 0L)
    fun clearAway() { prefs.edit().remove(KEY_AWAY_SINCE).apply() }

    private fun add(key: String, start: Long, counted: Long) {
        val now = start + counted
        // Update 202: under LOCK, so a Drive sync seeding / reading this device's own counter never misses a session.
        synchronized(LOCK) {
            val totals = loadTotals().toMutableMap()
            totals[key] = (totals[key] ?: 0L) + counted
            // Update 202: this device's own play time (only ever added to here) — what Drive sync shares under this
            // device's id. The displayed total is worked out from it on every sync (see CloudSync, playtime v2).
            val own = loadOwn().toMutableMap()
            own[key] = (own[key] ?: 0L) + counted
            // Every session is also logged (for Settings › Stats: weeks, streaks).
            val log = (loadSessions() + Session(key, now - counted, counted)).takeLast(MAX_SESSIONS)
            prefs.edit().putString(KEY_TOTALS, gson.toJson(totals)).putString(KEY_OWN, gson.toJson(own))
                .putString(KEY_SESSIONS, gson.toJson(log)).apply()
        }
    }

    // ---- Update 202: per-device play time (playtime format 2) ----
    // Before: one total per game, and Drive sync merged it as "Drive's total + what changed here since the last sync".
    // That isn't safe to repeat: a sync cut off after its upload (Wi-Fi drop, Voltcap killed while a game starts) or a
    // restore / Settings import changing the totals without the sync base made the next sync add the same hours again,
    // so a game played on two handhelds could show twice its real time (25 h → 50 h).
    // Now each device keeps its OWN counter (own_json, local game keys, only endSession adds to it, never synced in), Drive
    // keeps every device's counter under its device id plus the time from before this change ("legacy", set once by the
    // repair), and the shown total = legacy + the sum of all devices' counters. Copies merge by max, never by adding, so
    // a repeated or interrupted sync can't count anything twice. own_json and the marks below are this device's own
    // (SettingsScopes: never brought over from another device's backup).

    /** Update 202: the time played on THIS device per game (local keys) — since the per-device change, or seeded by the repair. */
    fun loadOwn(): Map<String, Long> {
        val json = prefs.getString(KEY_OWN, null) ?: return emptyMap()
        val all = runCatching { gson.fromJson<Map<String, Long>>(json, type) }.getOrNull() ?: emptyMap()
        return all.filterKeys { !StatsExclusions.excluded(app, it) }
    }

    /** Update 202: true once the one-time repair has set this device's own counter (CloudSync). */
    fun ownSeeded(): Boolean = prefs.getBoolean(KEY_OWN_SEEDED, false)

    /** Update 202: the moment play started counting per device (shared by all devices through Drive); 0 before. */
    fun ownSince(): Long = prefs.getLong(KEY_OWN_SINCE, 0L)

    /** Update 202: the one-time repair's result for this device (call while holding [LOCK]). */
    fun seedOwn(own: Map<String, Long>, since: Long) {
        prefs.edit().putString(KEY_OWN, gson.toJson(own)).putLong(KEY_OWN_SINCE, since).putBoolean(KEY_OWN_SEEDED, true).commit()
    }

    /**
     * Update 202: puts the synced totals (local keys) in place. [ownSeen] is the own counter the sync worked from: a
     * session that ended while the sync ran is added on top, so it isn't lost until the next sync.
     */
    fun replaceTotals(totals: Map<String, Long>, ownSeen: Map<String, Long>?) {
        synchronized(LOCK) {
            val out = totals.toMutableMap()
            if (ownSeen != null) {
                loadOwn().forEach { (k, v) ->
                    val extra = v - (ownSeen[k] ?: 0L)
                    if (extra > 0L) out[k] = (out[k] ?: 0L) + extra
                }
            }
            prefs.edit().putString(KEY_TOTALS, gson.toJson(out)).commit()
        }
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
        // Update 202: this device's own counter and the one-time repair's marks (device keys, SettingsScopes).
        const val KEY_OWN = "own_json"
        const val KEY_OWN_SEEDED = "own_seeded_v2"
        const val KEY_OWN_SINCE = "own_since"
        /** Update 202: held while the own counter / totals change (endSession, the sync's repair and write-back). */
        val LOCK = Any()
        private const val MAX_SESSIONS = 3000
        /** Update 202: the session log keeps this many (older ones drop off): a full log may miss a game's early play. */
        const val SESSION_LOG_MAX = MAX_SESSIONS
        private const val MIN_SESSION_MS = 20_000L
        private const val MAX_SESSION_MS = 8L * 60 * 60 * 1000
        /** Update 274: the most one measured stretch can add (time really in front; a sanity bound, not a cut). */
        private const val MAX_MEASURED_MS = 24L * 60 * 60 * 1000
        private const val KEY_AWAY_SINCE = "away_since"

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
