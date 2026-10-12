package com.shelf.launcher.data

import android.content.Context
import java.util.Calendar

/**
 * "Your year in Voltcap": a recap of a whole year of play, offered
 *  - every December (the calendar year so far), and
 *  - on each anniversary of when you started using Voltcap (the 12 months before it).
 * Each one is offered once; it can be opened any time from Settings › Stats.
 * Everything comes from Voltcap's own play-time log and play statuses (and RA awards, when given).
 */
object YearRecap {
    private const val PREFS = "year_recap"

    /** How long a recap covers. */
    enum class Kind(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

    /** Which stretch a recap covers and why it's shown. [offset] 0 = the current week/month/year, -1 the one before. */
    data class Period(val from: Long, val to: Long, val title: String, val id: String, val kind: Kind = Kind.YEAR, val offset: Int = 0)

    data class GameTime(val key: String, val ms: Long, val sessions: Int)

    data class Recap(
        val period: Period,
        val totalMs: Long,
        val days: Int,
        val sessions: Int,
        val longestSessionMs: Long,
        val longestSessionKey: String?,
        val topGames: List<GameTime>,
        /** Platform → time. */
        val topPlatforms: List<Pair<String, Long>>,
        val gamesPlayed: Int,
        val finished: Int,
        val longestStreakDays: Int,
        /** Time per bar of the chart: days for a week or month, months for a year. */
        val buckets: LongArray,
        val bucketLabels: List<String>,
        /** The busiest bar ("Saturday", "the 14th", "March"). */
        val busiestLabel: String,
        val firstKey: String?,
        val busiest: Int,
        /** Update 117: games played on your other devices: name, console and device (by key). */
        val otherInfo: Map<String, OtherDevices.Info> = emptyMap()
    )

    /** Update 117: this device's sessions, plus the other devices' (Features.recapOtherDevices). */
    private fun allSessions(c: Context): List<PlaytimeStore.Session> {
        val here = PlaytimeStore(c.applicationContext).loadSessions()
        if (!Features.recapOtherDevices.value) return here
        val there = runCatching { OtherDevices.sessions(c) }.getOrDefault(emptyList())
        // The same session seen twice (a game played here that came back in the synced data): once.
        val seen = here.mapTo(HashSet()) { it.key to it.start }
        return here + there.filter { (it.key to it.start) !in seen }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** When Voltcap was first used: the earliest of the install time and the first logged session. */
    fun firstUse(c: Context): Long {
        val p = prefs(c)
        val saved = p.getLong("first_use", 0L)
        if (saved > 0) return saved
        val install = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime }.getOrDefault(System.currentTimeMillis())
        val firstSession = runCatching { PlaytimeStore(c.applicationContext).loadSessions().minOfOrNull { it.start } }.getOrNull()
        val first = listOfNotNull(install, firstSession).minOrNull() ?: install
        p.edit().putLong("first_use", first).apply()
        return first
    }

    /** The calendar year so far (shown in December). */
    fun calendarYear(now: Long = System.currentTimeMillis()): Period {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        val start = Calendar.getInstance().apply { clear(); set(year, Calendar.JANUARY, 1) }.timeInMillis
        return Period(start, now, "Your $year in Voltcap", "year-$year")
    }

    /** A week (Monday → Sunday) or calendar month; [offset] 0 = this one, -1 = the one before, … */
    fun period(kind: Kind, offset: Int = 0, now: Long = System.currentTimeMillis()): Period {
        val cal = Calendar.getInstance().apply { timeInMillis = now; firstDayOfWeek = Calendar.MONDAY }
        when (kind) {
            Kind.YEAR -> {
                if (offset == 0) return calendarYear(now)
                val y = cal.get(Calendar.YEAR) + offset
                val from = Calendar.getInstance().apply { clear(); set(y, Calendar.JANUARY, 1) }.timeInMillis
                val to = Calendar.getInstance().apply { clear(); set(y + 1, Calendar.JANUARY, 1) }.timeInMillis - 1
                return Period(from, to, "Your $y in Voltcap", "year-$y", Kind.YEAR, offset)
            }
            Kind.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1); midnight(cal); cal.add(Calendar.MONTH, offset)
                val from = cal.timeInMillis
                val name = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(cal.time)
                cal.add(Calendar.MONTH, 1)
                val to = minOf(cal.timeInMillis - 1, now)
                return Period(from, to, if (offset == 0) "This month · $name" else name, "month-$from", Kind.MONTH, offset)
            }
            Kind.WEEK -> {
                midnight(cal)
                val back = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                cal.add(Calendar.DAY_OF_MONTH, -back + 7 * offset)
                val from = cal.timeInMillis
                val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                val startName = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val endName = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val to = minOf(cal.timeInMillis - 1, now)
                val title = when (offset) { 0 -> "This week"; -1 -> "Last week"; else -> "Week of $startName" }
                return Period(from, to, "$title · $startName – $endName", "week-$from", Kind.WEEK, offset)
            }
        }
    }

    private fun midnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    }

    /** Apps taken out of recaps (a recorded app that isn't really a game). Games can't be hidden. */
    fun hiddenKeys(c: Context): Set<String> = prefs(c).getStringSet("hidden_keys", emptySet()).orEmpty()

    fun setHidden(c: Context, key: String, hidden: Boolean) {
        val now = hiddenKeys(c).toMutableSet()
        if (hidden) now += key else now -= key
        prefs(c).edit().putStringSet("hidden_keys", now).apply()
    }

    /** Every key with play in [period] (hidden ones too), most time first: for the "Remove apps" list. */
    fun keysIn(c: Context, period: Period): List<GameTime> = runCatching {
        allSessions(c).filter { it.start in period.from..period.to && it.ms > 0 }
            .groupBy { it.key }.map { (k, s) -> GameTime(k, s.sumOf { it.ms }, s.size) }.sortedByDescending { it.ms }
    }.getOrDefault(emptyList())

    /** The last 12 months, ending on the anniversary [n] years after [first]. */
    private fun anniversary(first: Long, n: Int): Period {
        val end = Calendar.getInstance().apply { timeInMillis = first; add(Calendar.YEAR, n) }.timeInMillis
        val start = Calendar.getInstance().apply { timeInMillis = first; add(Calendar.YEAR, n - 1) }.timeInMillis
        return Period(start, end, if (n == 1) "Your first year with Voltcap" else "$n years with Voltcap", "anniv-$n")
    }

    /** A recap that's due and hasn't been shown yet, or null. Cheap enough for start-up (one prefs read + the session log). */
    fun due(c: Context, now: Long = System.currentTimeMillis()): Period? {
        val p = prefs(c)
        val first = firstUse(c)
        // Anniversary: the most recent full year passed, within 30 days of it.
        val years = run {
            var n = 0
            while (Calendar.getInstance().apply { timeInMillis = first; add(Calendar.YEAR, n + 1) }.timeInMillis <= now) n++
            n
        }
        if (years >= 1) {
            val a = anniversary(first, years)
            if (now - a.to < 30L * 86_400_000L && !p.getBoolean(a.id, false) && hasPlay(c, a)) return a
        }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        if (cal.get(Calendar.MONTH) == Calendar.DECEMBER) {
            val y = calendarYear(now)
            if (!p.getBoolean(y.id, false) && hasPlay(c, y)) return y
        }
        return null
    }

    fun markShown(c: Context, period: Period) {
        prefs(c).edit().putBoolean(period.id, true).apply()
    }

    private fun hasPlay(c: Context, period: Period): Boolean = runCatching {
        allSessions(c).filter { it.start in period.from..period.to }.sumOf { it.ms } >= 30 * 60_000L
    }.getOrDefault(false)

    /** Builds the recap. [platformOf] maps a game key to its platform (from the library). Blocking: call off the main thread. */
    fun build(c: Context, period: Period, platformOf: (String) -> String?): Recap {
        val hidden = hiddenKeys(c)
        val sessions = allSessions(c).filter { it.start in period.from..period.to && it.ms > 0 && it.key !in hidden }
        val others = if (Features.recapOtherDevices.value) runCatching { OtherDevices.info(c) }.getOrDefault(emptyMap()) else emptyMap()
        val total = sessions.sumOf { it.ms }
        val dayOf = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR) } }
        val days = sessions.map { dayOf(it.start) }.toSortedSet()
        // Longest run of days in a row with play.
        var streak = 0
        var best = 0
        var prev = Int.MIN_VALUE
        days.forEach { d ->
            // Year boundaries: day numbers jump (400 per year); treat Dec 31 → Jan 1 as consecutive.
            val consecutive = d == prev + 1 || (d % 400 == 1 && prev % 400 >= 365 && d / 400 == prev / 400 + 1)
            streak = if (consecutive) streak + 1 else 1
            best = maxOf(best, streak)
            prev = d
        }
        val byGame = sessions.groupBy { it.key }.map { (k, s) -> GameTime(k, s.sumOf { it.ms }, s.size) }.sortedByDescending { it.ms }
        val byPlatform = sessions.groupBy { platformOf(it.key) ?: others[it.key]?.platform?.takeIf { p -> p.isNotBlank() } ?: "Other" }.mapValues { (_, s) -> s.sumOf { it.ms } }
            .toList().sortedByDescending { it.second }
        // Bars: months of a year, days of a week or month.
        val locale = java.util.Locale.getDefault()
        val (buckets, labels, names) = when (period.kind) {
            Kind.YEAR -> {
                val b = LongArray(12)
                sessions.forEach { s -> b[Calendar.getInstance().apply { timeInMillis = s.start }.get(Calendar.MONTH)] += s.ms }
                val n = (0 until 12).map { m -> java.text.SimpleDateFormat("MMMM", locale).format(Calendar.getInstance().apply { clear(); set(2000, m, 1) }.time) }
                Triple(b, n.map { it.take(1) }, n)
            }
            Kind.WEEK -> {
                val b = LongArray(7)
                sessions.forEach { s -> b[((s.start - period.from) / 86_400_000L).toInt().coerceIn(0, 6)] += s.ms }
                val n = (0 until 7).map { d -> java.text.SimpleDateFormat("EEEE", locale).format(java.util.Date(period.from + d * 86_400_000L + 3_600_000L)) }
                Triple(b, n.map { it.take(2) }, n)
            }
            Kind.MONTH -> {
                val len = Calendar.getInstance().apply { timeInMillis = period.from }.getActualMaximum(Calendar.DAY_OF_MONTH)
                val b = LongArray(len)
                sessions.forEach { s -> b[(Calendar.getInstance().apply { timeInMillis = s.start }.get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, len - 1)] += s.ms }
                val n = (1..len).map { d -> "the $d${ordinal(d)}" }
                Triple(b, (1..len).map { d -> if (d == 1 || d % 5 == 0) d.toString() else "" }, n)
            }
        }
        val longest = sessions.maxByOrNull { it.ms }
        val finished = runCatching {
            CollectionsStore(c.applicationContext).loadStatuses().values.count { it.status == PlayStatus.FINISHED && it.at in period.from..period.to }
        }.getOrDefault(0)
        return Recap(
            period = period,
            totalMs = total,
            days = days.size,
            sessions = sessions.size,
            longestSessionMs = longest?.ms ?: 0L,
            longestSessionKey = longest?.key,
            topGames = byGame.take(5),
            topPlatforms = byPlatform.take(4),
            gamesPlayed = byGame.size,
            finished = finished,
            longestStreakDays = best,
            buckets = buckets,
            bucketLabels = labels,
            busiestLabel = names[buckets.indices.maxByOrNull { buckets[it] } ?: 0],
            firstKey = sessions.minByOrNull { it.start }?.key,
            busiest = buckets.indices.maxByOrNull { buckets[it] } ?: 0,
            otherInfo = others.filterKeys { k -> platformOf(k) == null }
        )
    }

    private fun ordinal(d: Int): String = if (d in 11..13) "th" else when (d % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
}
