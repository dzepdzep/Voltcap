package com.shelf.launcher.data

import android.content.Context

/**
 * Category order and visibility, plus "where you left off" (selected game,
 * last Settings section) — so the launcher comes back exactly as it was.
 *
 * Categories are keys: "ALL", "CONSOLES" (every console, alphabetically), and
 * the names of the special categories ("FAVORITES", "RECENT", ...).
 */
data class CategorySettings(
    val order: List<String> = DEFAULT_ORDER,
    val hidden: Set<String> = DEFAULT_HIDDEN,
    val lastFocusedKey: String? = null,
    val lastSettingsSection: String? = null
) {
    companion object {
        const val ALL = "ALL"
        const val CONSOLES = "CONSOLES"
        /** Your collections, franchises and play statuses (each one). */
        const val COLLECTIONS = "COLLECTIONS"

        /** Every category key, in the default place new ones go. */
        fun known(specials: List<String>): List<String> = listOf(ALL) + specials + CONSOLES + COLLECTIONS
        /** On by default: All, Recent and the consoles. Everything else is
         * switched on in Settings › Library › Categories. */
        val DEFAULT_HIDDEN = setOf(
            "FAVORITES", "MOST_PLAYED", "MASTERED", "IN_PROGRESS", "ALMOST_MASTERED", "NEVER_PLAYED", "FORGOTTEN", "EMULATORS", COLLECTIONS
        )
        val DEFAULT_ORDER = listOf(
            ALL, "FAVORITES", "RECENT", "MOST_PLAYED", "MASTERED", "IN_PROGRESS", "EMULATORS", CONSOLES
        )
    }

    /** The order with anything new added at the end and anything unknown dropped. */
    fun normalized(known: List<String>): List<String> =
        order.filter { it in known }.distinct() + known.filter { it !in order }
}

class CategoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)

    fun load(): CategorySettings {
        // Once: the new defaults (only All, Recent and the consoles showing)
        // also reach installs that never hid anything themselves. A set you
        // changed yourself is left exactly as it is.
        if (!prefs.getBoolean("defaults_v2", false)) {
            val saved = prefs.getStringSet("hidden", null)
            val edit = prefs.edit().putBoolean("defaults_v2", true)
            if (saved.isNullOrEmpty()) edit.putStringSet("hidden", CategorySettings.DEFAULT_HIDDEN)
            edit.apply()
        }
        return loadSaved()
    }

    private fun loadSaved() = CategorySettings(
        order = prefs.getString("order", null)?.split(',')?.filter { it.isNotBlank() }
            ?: CategorySettings.DEFAULT_ORDER,
        hidden = prefs.getStringSet("hidden", null)?.toSet() ?: CategorySettings.DEFAULT_HIDDEN,
        lastFocusedKey = prefs.getString("last_focused", null),
        lastSettingsSection = prefs.getString("last_settings_section", null)
    )

    fun save(s: CategorySettings) = prefs.edit()
        .putString("order", s.order.joinToString(","))
        .putStringSet("hidden", s.hidden)
        .putString("last_focused", s.lastFocusedKey)
        .putString("last_settings_section", s.lastSettingsSection)
        .apply()
}
