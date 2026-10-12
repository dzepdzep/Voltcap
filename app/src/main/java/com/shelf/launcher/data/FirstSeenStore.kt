package com.shelf.launcher.data

import android.content.Context

/**
 * When each game first showed up in the library (epoch ms), for the
 * "Newly added" sort. 0 means "unknown" — games that were already here when
 * this feature arrived — and sorts after genuinely new ones.
 * Prefs "first_seen" (GLOBAL scope). Reads and writes belong off the main thread.
 */
class FirstSeenStore(context: Context) {
    private val prefs = context.getSharedPreferences("first_seen", Context.MODE_PRIVATE)

    /** True once the first-run seeding has been done. */
    fun isSeeded(): Boolean = prefs.getBoolean(SEEDED, false)

    fun markSeeded() = prefs.edit().putBoolean(SEEDED, true).apply()

    fun loadAll(): Map<String, Long> =
        prefs.all.mapNotNull { (k, v) -> if (k == SEEDED) null else (v as? Long)?.let { k to it } }.toMap()

    /** Adds [entries] in one batch. */
    fun putAll(entries: Map<String, Long>) {
        if (entries.isEmpty()) return
        val e = prefs.edit()
        entries.forEach { (k, v) -> e.putLong(k, v) }
        e.apply()
    }

    private companion object {
        // Game keys are paths / package names, so this can't clash with one.
        const val SEEDED = "__seeded__"
    }
}
