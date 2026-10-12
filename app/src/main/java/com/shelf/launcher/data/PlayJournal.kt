package com.shelf.launcher.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Voltcap's own little diary of what happened in play, for Recap, Stats and Game Journey: unlocks
 * (with the game's progress before → after), milestones and masteries. Sessions themselves live in
 * [PlaytimeStore]; this only adds what the play-time log doesn't know.
 *
 * One JSON object per line in the app's files (a few hundred bytes a day of play), the oldest lines
 * trimmed past [MAX_LINES]. Written only by real events — opening a game's popup never writes here.
 */
object PlayJournal {
    private const val FILE = "play_journal.jsonl"
    private const val MAX_LINES = 6000

    enum class Kind { UNLOCK, MILESTONE, MASTERED }

    data class Entry(
        val kind: Kind,
        val key: String,
        val at: Long,
        /** UNLOCK: how many this time; their badge pictures (a few). */
        val count: Int = 0,
        val badges: List<String> = emptyList(),
        /** Progress after (unlocked / total) and the completion before → after (-1 = unknown). */
        val unlocked: Int = 0,
        val total: Int = 0,
        val percentBefore: Int = -1,
        val percentAfter: Int = -1,
        /** MILESTONE: its line ("Longest session yet"). */
        val text: String? = null,
        val name: String? = null
    )

    @Volatile private var cache: List<Entry>? = null
    private val lock = Any()

    private fun file(c: Context) = File(c.applicationContext.filesDir, FILE)

    fun all(c: Context): List<Entry> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val list = runCatching {
                val f = file(c)
                if (!f.exists()) emptyList() else f.readLines().mapNotNull { parse(it) }
            }.getOrDefault(emptyList())
            cache = list
            return list
        }
    }

    fun forGame(c: Context, key: String): List<Entry> = all(c).filter { it.key == key }

    fun between(c: Context, from: Long, to: Long): List<Entry> = all(c).filter { it.at in from..to }

    fun add(c: Context, e: Entry) {
        synchronized(lock) {
            runCatching {
                // Loaded before appending: reading the file after would count [e] twice.
                val before = all(c)
                val f = file(c)
                f.appendText(toJson(e) + "\n")
                val now = before + e
                if (now.size > MAX_LINES) {
                    val keep = now.takeLast(MAX_LINES * 9 / 10)
                    // Written beside it and swapped in: a crash mid-write never leaves half a journal.
                    val tmp = File(f.parentFile, "$FILE.tmp")
                    tmp.writeText(keep.joinToString("\n", postfix = "\n") { toJson(it) })
                    if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
                    cache = keep
                } else cache = now
            }
        }
    }

    /** Unlocks of a session (or found by a check), with the game's progress before → after. */
    fun unlocks(c: Context, key: String, name: String?, count: Int, badges: List<String>, unlocked: Int, total: Int, pb: Int, pa: Int) {
        if (count <= 0) return
        add(c, Entry(Kind.UNLOCK, key, System.currentTimeMillis(), count, badges.take(6), unlocked, total, pb, pa, name = name))
        if (total > 0 && unlocked >= total) add(c, Entry(Kind.MASTERED, key, System.currentTimeMillis(), unlocked = unlocked, total = total, name = name))
    }

    fun milestone(c: Context, key: String, name: String?, text: String) {
        // The same line for the same game on the same day is said once.
        val day = System.currentTimeMillis() / 86_400_000L
        if (all(c).any { it.kind == Kind.MILESTONE && it.key == key && it.text == text && it.at / 86_400_000L == day }) return
        add(c, Entry(Kind.MILESTONE, key, System.currentTimeMillis(), text = text, name = name))
    }

    private fun toJson(e: Entry): String = JSONObject().apply {
        put("k", e.kind.name); put("g", e.key); put("t", e.at)
        if (e.count > 0) put("c", e.count)
        if (e.badges.isNotEmpty()) put("b", org.json.JSONArray(e.badges))
        if (e.total > 0) { put("u", e.unlocked); put("n", e.total) }
        if (e.percentBefore >= 0) put("pb", e.percentBefore)
        if (e.percentAfter >= 0) put("pa", e.percentAfter)
        e.text?.let { put("x", it) }
        e.name?.let { put("m", it) }
    }.toString()

    private fun parse(line: String): Entry? = runCatching {
        if (line.isBlank()) return null
        val o = JSONObject(line)
        val b = o.optJSONArray("b")
        Entry(
            kind = Kind.valueOf(o.getString("k")), key = o.getString("g"), at = o.getLong("t"),
            count = o.optInt("c", 0),
            badges = if (b == null) emptyList() else (0 until b.length()).map { b.getString(it) },
            unlocked = o.optInt("u", 0), total = o.optInt("n", 0),
            percentBefore = o.optInt("pb", -1), percentAfter = o.optInt("pa", -1),
            text = o.optString("x").takeIf { it.isNotEmpty() }, name = o.optString("m").takeIf { it.isNotEmpty() }
        )
    }.getOrNull()
}
