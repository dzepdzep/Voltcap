package com.shelf.launcher.data

import android.content.Context

/** Which installed apps appear in the library (the dock's normal categories). */
enum class AppsMode(val label: String, val blurb: String) {
    ALL("All apps", "Every installed app is in the library."),
    GAMES_AND_ADDED("Games + apps you add", "Android games are included automatically; other apps only if you add them."),
    ONLY_ADDED("Only apps you add", "No apps unless you add them yourself.")
}

/**
 * How installed apps are treated. Package names throughout.
 *
 * @param added apps put in the library by hand (always shown).
 * @param emulators apps marked as emulators by hand, on top of the ones
 *   Shelf recognises itself.
 * @param iconArt apps that should show their own icon instead of scraped art.
 * @param alwaysUseIcons every app shows its own icon; apps aren't scraped.
 */
data class LibraryAppsSettings(
    val mode: AppsMode = AppsMode.GAMES_AND_ADDED,
    val added: Set<String> = emptySet(),
    val emulators: Set<String> = emptySet(),
    val iconArt: Set<String> = emptySet(),
    val alwaysUseIcons: Boolean = false,
    /** Set by hand: counts as an Android game, whatever the detector says. */
    val forcedGames: Set<String> = emptySet(),
    /** Set by hand: just an app, never an Android game. */
    val forcedApps: Set<String> = emptySet()
)

/** Game-or-app choice for one installed app. */
enum class AppKind { AUTO, GAME, APP }

fun LibraryAppsSettings.kindOf(pkg: String): AppKind = when (pkg) {
    in forcedGames -> AppKind.GAME
    in forcedApps -> AppKind.APP
    else -> AppKind.AUTO
}

class LibraryAppsStore(context: Context) {
    private val prefs = context.getSharedPreferences("library_apps", Context.MODE_PRIVATE)

    fun load() = LibraryAppsSettings(
        mode = runCatching { AppsMode.valueOf(prefs.getString("mode", null) ?: "") }.getOrDefault(AppsMode.GAMES_AND_ADDED),
        added = prefs.getStringSet("added", null)?.toSet() ?: emptySet(),
        emulators = prefs.getStringSet("emulators", null)?.toSet() ?: emptySet(),
        iconArt = prefs.getStringSet("icon_art", null)?.toSet() ?: emptySet(),
        alwaysUseIcons = prefs.getBoolean("always_icons", false),
        forcedGames = prefs.getStringSet("forced_games", null)?.toSet() ?: emptySet(),
        forcedApps = prefs.getStringSet("forced_apps", null)?.toSet() ?: emptySet()
    )

    fun save(s: LibraryAppsSettings) = prefs.edit()
        .putString("mode", s.mode.name)
        .putStringSet("added", s.added)
        .putStringSet("emulators", s.emulators)
        .putStringSet("icon_art", s.iconArt)
        .putBoolean("always_icons", s.alwaysUseIcons)
        .putStringSet("forced_games", s.forcedGames)
        .putStringSet("forced_apps", s.forcedApps)
        .apply()
}
