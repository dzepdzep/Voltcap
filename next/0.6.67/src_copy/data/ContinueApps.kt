package com.shelf.launcher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * v0.6.67 (update 450): ordinary apps you opt into Continue playing — streaming clients such as PXPlay or Asobi —
 * without classifying them as Android games.
 *
 * Which apps: [LibraryAppsSettings.continueApps] (Settings › Library › Apps / the app's own options, "Show in Continue
 * playing"); nothing is in it by default, so no ordinary app shows up unless you put it there.
 *
 * What is recorded (the playing profile's own, like Continue playing itself — [Profiles.fileFor]):
 *  - a launch from Voltcap: the time, in [RecentlyPlayedStore] (the same record Continue playing reads);
 *  - with Usage access (optional; Android's own switch, never turned on by Voltcap): when the app was really in front,
 *    also outside Voltcap, and for how long ([scan]). Only the time one of its activities was resumed counts — not
 *    the app running in the background, the screen off / locked, or another app in front.
 * Nothing else happens for these apps: no game session, play time, save sync, AutoTDP / tuning, Discord or unlock
 * watch. Voltcap never claims to know what is being streamed in them.
 */
object ContinueApps {
    private const val PREFS = "continue_apps"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_FOREGROUND = "foreground_ms_json"
    /** A stretch in front shorter than this (a crash, a bounce) doesn't count as using the app. */
    const val MIN_STRETCH_MS = 5_000L
    /** How far back a first look (or a long gap) goes. */
    const val MAX_LOOKBACK_MS = 7L * 24 * 3600_000L

    // Usage event types (UsageEvents.Event constants; some are API 28+/29+, kept as numbers).
    const val RESUMED = 1
    const val PAUSED = 2
    const val STOPPED = 23
    const val DESTROYED = 24
    const val SCREEN_OFF = 16
    const val KEYGUARD_SHOWN = 17
    const val SHUTDOWN = 26

    /** One usage record entry (what [scan] reads; a plain class so the rules can be unit-tested). */
    data class Ev(val pkg: String, val cls: String, val type: Int, val time: Long)

    /** What an app did in a window: when it was last in front (the end of its last stretch) and for how long. */
    data class Use(val lastUsed: Long, val foregroundMs: Long)

    /** [use] per package, and the activities still resumed at the end of the window (carried into the next look). */
    data class Result(val use: Map<String, Use>, val openAtEnd: Map<String, Set<String>>)

    /**
     * Works out [Use] per package in [pkgs] from [events] (in time order) between [from] and [to]. An app is in front
     * while any of its activities is resumed; the stretch ends when the last one pauses / stops / goes, the screen goes
     * off, the lock screen shows or the device shuts down. Stretches under [MIN_STRETCH_MS] are left out. A stretch still
     * open at [to] is counted up to [to] and reported in [Result.openAtEnd]; [seedOpen] (the last look's) continues
     * such stretches from [from].
     */
    fun foreground(
        events: List<Ev>, pkgs: Set<String>, from: Long, to: Long,
        seedOpen: Map<String, Set<String>> = emptyMap()
    ): Result {
        if (pkgs.isEmpty()) return Result(emptyMap(), emptyMap())
        val open = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        seedOpen.filterKeys { it in pkgs }.forEach { (p, cls) -> if (cls.isNotEmpty()) { open[p] = cls.toHashSet(); since[p] = from } }
        val total = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        fun close(pkg: String, at: Long) {
            val s = since.remove(pkg) ?: return
            open.remove(pkg)
            val end = at.coerceAtMost(to)
            val len = end - s
            if (len >= MIN_STRETCH_MS) {
                total[pkg] = (total[pkg] ?: 0L) + len
                last[pkg] = maxOf(last[pkg] ?: 0L, end)
            }
        }
        for (e in events) {
            if (e.time > to) break
            when (e.type) {
                SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN -> since.keys.toList().forEach { close(it, e.time) }
                RESUMED -> if (e.pkg in pkgs) {
                    val set = open.getOrPut(e.pkg) { HashSet() }
                    if (set.isEmpty()) since[e.pkg] = maxOf(e.time, from)
                    set += e.cls
                }
                PAUSED, STOPPED, DESTROYED -> if (e.pkg in pkgs) {
                    val set = open[e.pkg] ?: continue
                    if (set.remove(e.cls) && set.isEmpty()) close(e.pkg, e.time)
                }
            }
        }
        val stillOpen = open.filterValues { it.isNotEmpty() }.mapValues { it.value.toSet() }
        since.keys.toList().forEach { close(it, to) }
        return Result(pkgs.mapNotNull { p -> last[p]?.let { p to Use(it, total[p] ?: 0L) } }.toMap(), stillOpen)
    }

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Long>>() {}.type
    private val openType = object : TypeToken<Map<String, Set<String>>>() {}.type
    private const val KEY_OPEN = "open_json"
    /** The scan position is the device's (one usage record, read once): time goes to the profile playing when it's read. */
    private fun scanPrefs(c: Context) = c.applicationContext.getSharedPreferences("continue_apps_scan", Context.MODE_PRIVATE)
    private fun prefs(c: Context) = c.applicationContext.let { a -> a.getSharedPreferences(Profiles.run { ensure(a); fileFor(PREFS) }, Context.MODE_PRIVATE) }

    /** Total time in front per package (this profile), from Usage access. */
    fun foregroundTotals(c: Context): Map<String, Long> =
        runCatching { gson.fromJson<Map<String, Long>>(prefs(c).getString(KEY_FOREGROUND, null), mapType) }.getOrNull().orEmpty()

    /** Forgets [pkg]'s time in front (Clear from Continue playing). */
    fun clear(c: Context, pkg: String) {
        val m = foregroundTotals(c) - pkg
        prefs(c).edit().putString(KEY_FOREGROUND, gson.toJson(m)).apply()
    }

    /** An app was just put in Continue playing: its use is counted from now on, never from before. */
    fun noteOptIn(c: Context, pkg: String, now: Long = System.currentTimeMillis()) {
        scanPrefs(c).edit().putLong("optin_$pkg", now).apply()
    }

    /** Usage access granted (optional). */
    fun usageAvailable(c: Context): Boolean = Permissions.usageAccess(c)

    /**
     * Reads Android's usage record since the last look (this profile) for [pkgs] and adds the time in front. Null
     * without Usage access (nothing is read, nothing turned on). The first look starts now — nothing from before you
     * opted an app in is counted.
     */
    fun scan(c: Context, pkgs: Set<String>, now: Long = System.currentTimeMillis()): Map<String, Use>? {
        if (pkgs.isEmpty() || !usageAvailable(c)) return null
        val p = scanPrefs(c)
        val cursor = p.getLong(KEY_CURSOR, 0L)
        if (cursor <= 0L) { p.edit().putLong(KEY_CURSOR, now).apply(); return emptyMap() }
        val from = maxOf(cursor, now - MAX_LOOKBACK_MS)
        if (now <= from) return emptyMap()
        val events = runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val q = usm.queryEvents(from, now)
            val ev = UsageEvents.Event()
            val out = ArrayList<Ev>()
            while (q.hasNextEvent()) {
                q.getNextEvent(ev)
                val t = ev.eventType
                if (t == SCREEN_OFF || t == KEYGUARD_SHOWN || t == SHUTDOWN || (ev.packageName in pkgs && (t == RESUMED || t == PAUSED || t == STOPPED || t == DESTROYED))) {
                    // Nothing from before the app was put in Continue playing.
                    if (ev.packageName in pkgs && ev.timeStamp < p.getLong("optin_${ev.packageName}", 0L)) continue
                    out += Ev(ev.packageName ?: "", ev.className ?: "", t, ev.timeStamp)
                }
            }
            out
        }.getOrNull() ?: return null
        val seed = runCatching { gson.fromJson<Map<String, Set<String>>>(p.getString(KEY_OPEN, null), openType) }.getOrNull().orEmpty()
        val r = foreground(events, pkgs, from, now, seed)
        if (r.use.isNotEmpty()) {
            val totals = foregroundTotals(c).toMutableMap()
            r.use.forEach { (pkg, u) -> totals[pkg] = (totals[pkg] ?: 0L) + u.foregroundMs }
            prefs(c).edit().putString(KEY_FOREGROUND, gson.toJson(totals)).apply()
        }
        p.edit().putLong(KEY_CURSOR, now).putString(KEY_OPEN, gson.toJson(r.openAtEnd)).apply()
        return r.use
    }

    /**
     * The line under "Show in Continue playing": "Used 2 h ago · 1 h 20 min in front", plus why only launches from
     * Voltcap are noted when Usage access is off.
     */
    fun note(lastUsed: Long?, foregroundMs: Long?, usage: Boolean, now: Long): String {
        val parts = ArrayList<String>()
        if (lastUsed != null && lastUsed > 0L) parts += "Used " + agoLabel(now - lastUsed)
        if (usage && foregroundMs != null && foregroundMs > 0L) parts += durationLabel(foregroundMs) + " in front"
        parts += if (usage) "also noticed when opened outside Voltcap"
            else "Usage access is off: only opening it from Voltcap is noted (Settings › Library › Running games & quick resume)"
        return parts.joinToString(" · ")
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    fun agoLabel(ms: Long): String {
        val min = (ms / 60_000L).coerceAtLeast(0)
        return when {
            min < 1 -> "just now"
            min < 60 -> "$min min ago"
            min < 48 * 60 -> "${min / 60} h ago"
            else -> "${min / (24 * 60)} days ago"
        }
    }

    /** "1 h 20 min", "12 min", "under a minute". */
    fun durationLabel(ms: Long): String {
        val min = ms / 60_000L
        return when {
            min < 1 -> "under a minute"
            min < 60 -> "$min min"
            else -> "${min / 60} h" + (min % 60).let { if (it > 0) " $it min" else "" }
        }
    }
}
