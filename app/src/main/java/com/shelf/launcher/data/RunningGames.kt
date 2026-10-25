package com.shelf.launcher.data

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Games still open in the background, console style: they glow on the grid, A brings one back to the
 * front instead of starting it again (no lost progress), X closes it, starting another game in the
 * same emulator closes the old one first, and starting a game while a different one is open asks.
 *
 * What's open is worked out from the games Voltcap started, then checked:
 *  - "Enhanced access" (AYN handhelds: the firmware's own system service, no root needed): the
 *    emulator's process is asked for directly, and closing a game really ends it (force stop).
 *  - Usage access (Settings › Library › Running games, a system switch): Android's own record of
 *    which app was in front tells when an emulator was closed (back out / closed from the app list),
 *    and play time counts only the minutes the game was really in front.
 *  - Otherwise: a game you left less than [ASSUME_OPEN_MS] ago counts as open (Android rarely ends a
 *    backgrounded emulator that soon); closing asks Android to end it and, if it can't, opens the
 *    app's page with Force stop.
 */
object RunningGames {
    private const val PREFS = "running_games"
    private const val KEY = "entries"
    /** Without a way to check, a game left this recently is assumed to be still open. */
    private const val ASSUME_OPEN_MS = 20 * 60_000L
    /** Nothing counts as open after this long in the background. */
    private const val MAX_OPEN_MS = 8 * 60 * 60_000L

    /** One game Voltcap started: its key, the app that runs it, when it started and when you left it. */
    data class Entry(val key: String, val pkg: String, val launchedAt: Long, val leftAt: Long = 0L)

    /** Keys of the games open right now (Game.stableKey). Compose state: cards read it directly. */
    var keys by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * The subset of [keys] that's *known* to be open (the process was seen, or Android's usage record
     * says the game wasn't closed) - not just assumed from the time. Only these are resumed instead
     * of started, and only these make Voltcap ask before starting another game.
     */
    var confirmed by mutableStateOf<Set<String>>(emptySet())
        private set

    private val gson = Gson()
    private val type = object : TypeToken<List<Entry>>() {}.type
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(c: Context): List<Entry> =
        runCatching { gson.fromJson<List<Entry>>(prefs(c).getString(KEY, null), type) }.getOrNull().orEmpty()

    private fun save(c: Context, list: List<Entry>) {
        prefs(c).edit().putString(KEY, gson.toJson(list)).apply()
        keys = list.map { it.key }.toSet()
        confirmed = confirmed intersect keys
    }

    /** The app running [key], if it's open. */
    fun packageFor(c: Context, key: String): String? = load(c).firstOrNull { it.key == key }?.pkg

    /** Open games other than [exceptKey] (key → package). */
    fun others(c: Context, exceptKey: String): List<Entry> = load(c).filter { it.key != exceptKey }

    /** True when Voltcap can really see and end processes (AYN's system service). */
    val enhanced: Boolean get() = com.shelf.launcher.device.RootShell.available

    /** Usage access granted (Settings › Apps › Special access › Usage access). */
    fun hasUsageAccess(c: Context): Boolean = Permissions.usageAccess(c)

    /** A game started: it's the one open in [pkg] now (an emulator runs one game at a time). */
    fun noteLaunched(c: Context, key: String, pkg: String?) {
        if (pkg == null) return
        val now = System.currentTimeMillis()
        save(c, load(c).filter { it.pkg != pkg && it.key != key } + Entry(key, pkg, now))
    }

    /** Back on the home screen: the game that was in front is now in the background. */
    fun noteLeft(c: Context) {
        val now = System.currentTimeMillis()
        val list = load(c)
        if (list.isEmpty()) return
        val last = list.maxByOrNull { it.launchedAt } ?: return
        if (last.leftAt == 0L) save(c, list.map { if (it === last) it.copy(leftAt = now) else it })
    }

    /** Checks every open game again; closed ones drop off. Blocking (binder / usage queries): call off the main thread. */
    fun refresh(c: Context) {
        val now = System.currentTimeMillis()
        val list = load(c)
        if (list.isEmpty()) {
            if (keys.isNotEmpty()) keys = emptySet()
            if (confirmed.isNotEmpty()) confirmed = emptySet()
            return
        }
        val usage = hasUsageAccess(c)
        val sure = mutableSetOf<String>()
        val alive = list.filter { e ->
            if (e.leftAt > 0L && now - e.leftAt > MAX_OPEN_MS) return@filter false
            val known = com.shelf.launcher.device.RootShell.isRunning(e.pkg)
                ?: (if (usage) closedPerUsage(c, e)?.not() else null)
            if (known == true) sure += e.key
            known ?: (e.leftAt == 0L || now - e.leftAt < ASSUME_OPEN_MS)
        }
        confirmed = sure
        if (alive.size != list.size || keys != alive.map { it.key }.toSet()) save(c, alive)
    }

    /**
     * Android's usage record for [e]'s app since it started: true when its last activity was destroyed
     * (backed out of / closed from the app list), false when it's still there, null when there's no record.
     */
    private fun closedPerUsage(c: Context, e: Entry): Boolean? = runCatching {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(e.launchedAt - 5_000L, System.currentTimeMillis())
        val ev = UsageEvents.Event()
        // Update 200: the last event per activity class, not for the whole app. An Android game's splash / launcher
        // activity is destroyed right after it hands over to the real game activity; read app-wide, that late
        // DESTROYED made a game that was still open count as closed. Closed = every activity seen was destroyed.
        val last = HashMap<String, Int>()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.packageName != e.pkg) continue
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> last[ev.className ?: ""] = ev.eventType
                // ACTIVITY_DESTROYED (API 29+).
                24 -> last[ev.className ?: ""] = 24
            }
        }
        if (last.isEmpty()) null else last.values.all { it == 24 }
    }.getOrNull()

    /**
     * How long [pkg] was really in front between [from] and [to] (ms), from Android's usage record;
     * null without usage access (the caller counts the whole time then). 0 when [pkg] never came to the front.
     */
    fun foregroundMs(c: Context, pkg: String?, from: Long, to: Long): Long? {
        if (!hasUsageAccess(c)) return null
        // (Update 219, #01: the game's app wasn't known any more — Voltcap restarted and the entry had gone — so the
        // whole time from launch to coming back counted, sleep included, up to 8 h. Now: the time any other app was
        // really in front, which leaves out the screen being off and time spent in Voltcap.)
        if (pkg == null) return anyOtherForegroundMs(c, from, to)
        return runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            // Update 200: a little before [from]: the game's first "resumed" can be stamped a moment before the launch
            // time was saved (startActivity is asynchronous). Time before [from] is never counted (clamped below).
            val events = usm.queryEvents(from - 2_000L, to)
            val ev = UsageEvents.Event()
            var total = 0L
            var since = -1L
            var seen = false
            // Update 200: the game counts as in front while ANY of its activities is resumed (per activity class).
            // Android games almost always open through a splash / launcher activity that hands over to the real one
            // (seen on the Thor: LauncherActivity → resumed → paused → WWEActivity resumed → LauncherActivity STOPPED).
            // The old single "since" closed the open stretch on the splash's late STOPPED, so everything after the
            // hand-over was lost: a session came out at ~1 s, under PlaytimeStore's 20 s minimum, and was dropped —
            // no play time, no session for the recaps / Drive. Emulators started straight into one activity, so
            // they were fine. Switching away (home, recents, another app) pauses the last activity and ends the stretch.
            val open = HashSet<String>()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.packageName != pkg) continue
                seen = true
                val cls = ev.className ?: ""
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (open.isEmpty()) since = maxOf(ev.timeStamp, from)
                        open += cls
                    }
                    // ACTIVITY_DESTROYED (24, API 29+) too: an activity can go without a STOPPED in the record.
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 24 -> {
                        // An activity that wasn't resumed in this window (the splash's late STOPPED) changes nothing.
                        if (open.remove(cls) && open.isEmpty() && since >= 0) {
                            total += maxOf(0L, ev.timeStamp - since)
                            since = -1L
                        }
                    }
                }
            }
            if (since >= 0) total += maxOf(0L, to - since)
            // (Update 219, #01: with usage access, no record at all of the game's app in that time means it never came
            // to the front — a launch that failed or bounced straight back. That used to count the whole wall-clock time
            // until Voltcap was next opened (up to 8 h). Now it counts nothing, so it isn't a session.)
            if (!seen) 0L else total
        }.getOrNull()
    }

    /** Update 219: how long any app other than Voltcap (and the system bars) was in front between [from] and [to]. */
    private fun anyOtherForegroundMs(c: Context, from: Long, to: Long): Long? = runCatching {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from - 2_000L, to)
        val ev = UsageEvents.Event()
        val own = c.packageName
        val open = HashSet<String>()
        var since = -1L
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            if (pkg == own || pkg == "com.android.systemui") continue
            val id = pkg + "/" + (ev.className ?: "")
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (open.isEmpty()) since = maxOf(ev.timeStamp, from)
                    open += id
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 24 -> {
                    if (open.remove(id) && open.isEmpty() && since >= 0) {
                        total += maxOf(0L, ev.timeStamp - since)
                        since = -1L
                    }
                }
            }
        }
        if (since >= 0) total += maxOf(0L, to - since)
        total
    }.getOrNull()

    /**
     * Closes [key]'s game. With enhanced access it's ended for real (force stop); otherwise Android is
     * asked to end its background process. Returns true when it's known to be gone.
     */
    fun close(c: Context, key: String): Boolean {
        val e = load(c).firstOrNull { it.key == key } ?: return true
        return closePackage(c, e.pkg).also { gone -> if (gone) save(c, load(c).filter { it.key != key }) }
    }

    /** Ends whatever runs in [pkg] (and forgets its open game). */
    fun closePackage(c: Context, pkg: String): Boolean {
        if (!pkg.matches(Regex("[A-Za-z0-9._]+"))) return false
        val viaRoot = com.shelf.launcher.device.RootShell.exec("am force-stop $pkg")
        if (viaRoot != null) {
            save(c, load(c).filter { it.pkg != pkg })
            return com.shelf.launcher.device.RootShell.isRunning(pkg) != true
        }
        runCatching {
            (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        }
        // Android 14+ only lets apps end their own processes: on those the caller opens the app's page
        // with Force stop. Either way the game stops counting as open here.
        save(c, load(c).filter { it.pkg != pkg })
        return Build.VERSION.SDK_INT < 34
    }

    /** Brings [key]'s game back to the front (no restart). */
    fun resume(c: Context, key: String, displayId: Int? = null): Boolean {
        val pkg = packageFor(c, key) ?: return false
        return QuickResume.resume(c, pkg, displayId)
    }

    /** Opens Android's page for [pkg] (with Force stop), for when Voltcap can't end it itself. */
    fun openAppInfo(c: Context, pkg: String) = runCatching {
        c.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", pkg, null))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    /** Loads the saved list at start-up (shows the glow before the first check finishes). */
    fun init(c: Context) {
        keys = load(c).map { it.key }.toSet()
        marked = prefs(c).getBoolean("marked", true)
    }

    /** Mark open games on the grid (green ring + "Open" tag). Settings › Library › Running games. */
    var marked by mutableStateOf(true)
        private set

    fun setMarked(c: Context, on: Boolean) {
        marked = on
        prefs(c).edit().putBoolean("marked", on).apply()
    }
}
