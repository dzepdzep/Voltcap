package com.shelf.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Home-screen shortcuts other apps add, as games. DroidDeck (0.3.1+) offers "Add to home screen" for its Steam
 * and added games; Android sends that request to the default launcher — Voltcap — which used to ignore it. Now
 * Voltcap asks "Add to Voltcap?" (PinShortcutActivity) and the game shows up under the app's name ("DroidDeck"),
 * launching through the shortcut itself, exactly like the stock home screen would.
 *
 * Stored as plain lines "package\tshortcutId\tlabel" (device-only). The game's key is "shortcut:package/id".
 */
object PinnedShortcuts {
    private const val PREFS = "pinned_shortcuts"
    private const val PREFIX = "shortcut:"

    data class Entry(val packageName: String, val id: String, val label: String)

    private val _version = MutableStateFlow(0)
    /** Bumps when a shortcut is added or removed (the library re-reads). */
    val version: StateFlow<Int> = _version

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): List<Entry> =
        prefs(c).getString("list", null).orEmpty().lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3 || p[0].isBlank() || p[1].isBlank()) null else Entry(p[0], p[1], p[2])
        }

    private fun save(c: Context, list: List<Entry>) {
        prefs(c).edit().putString("list", list.joinToString("\n") { e ->
            listOf(e.packageName, e.id, e.label).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
        }).apply()
        _version.value++
    }

    fun add(c: Context, e: Entry) {
        val list = load(c).filterNot { it.packageName == e.packageName && it.id == e.id } + e
        save(c, list)
    }

    fun remove(c: Context, key: String) {
        val (pkg, id) = parse(key) ?: return
        save(c, load(c).filterNot { it.packageName == pkg && it.id == id })
    }

    fun isShortcut(game: Game): Boolean = game.romPath?.startsWith(PREFIX) == true

    private fun parse(key: String): Pair<String, String>? {
        if (!key.startsWith(PREFIX)) return null
        val rest = key.removePrefix(PREFIX)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.length - 1) return null
        return rest.substring(0, slash) to rest.substring(slash + 1)
    }

    /** The games: platform = the app's name (DroidDeck → "Steam"), so they get their own category. */
    fun games(c: Context): List<Game> {
        val pm = c.packageManager
        return load(c).map { e ->
            // DroidDeck's games are Steam games: they go in the Steam category (art scraped like any Steam game).
            val appName = if (e.packageName == GameClients.DROIDDECK) "Steam"
                else runCatching { pm.getApplicationLabel(pm.getApplicationInfo(e.packageName, 0)).toString() }.getOrDefault("Shortcuts")
            Game(
                name = e.label,
                platform = appName,
                hue = (e.label.hashCode() and 0x7fffffff) % 360,
                sourceType = SourceType.ROM,
                romPath = PREFIX + e.packageName + "/" + e.id
            )
        }
    }

    /** Starts the shortcut (Voltcap must be the home app: Android only lets the launcher do this). */
    fun launch(c: Context, game: Game, options: android.os.Bundle?): LaunchOutcome {
        val (pkg, id) = parse(game.romPath.orEmpty()) ?: return LaunchOutcome.Error("This shortcut entry is broken.")
        if (Build.VERSION.SDK_INT < 25) return LaunchOutcome.Error("Shortcuts need Android 7.1 or newer.")
        val apps = c.getSystemService(LauncherApps::class.java) ?: return LaunchOutcome.Error("Android's launcher service isn't there.")
        if (!runCatching { apps.hasShortcutHostPermission() }.getOrDefault(false)) {
            return LaunchOutcome.Error("Only the home app can open shortcuts: make Voltcap your home app.")
        }
        return try {
            apps.startShortcut(pkg, id, null, options, Process.myUserHandle())
            LaunchOutcome.Launched
        } catch (e: android.content.ActivityNotFoundException) {
            LaunchOutcome.EmulatorNotFound(pkg)
        } catch (e: Exception) {
            // Removed or disabled in the app (a game uninstalled from DroidDeck, say).
            LaunchOutcome.Error("${game.name}: the app no longer has this shortcut (${e.message ?: "it was removed"}). Add it again from the app.")
        }
    }
}
