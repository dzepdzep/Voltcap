package com.shelf.launcher.data

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * "Last played" on every device (Settings › Sync › Last played on every device, on by default when Google
 * Drive is connected). One small file in the Drive folder Voltcap already uses: Voltcap/voltcap-last-played.json
 * (a few KB). A game played on another device rises to the top of the row here too.
 *
 * Kept light on purpose:
 *  - at most one round every 20 minutes while Voltcap is on screen, plus one shortly after a game ends
 *    (that's when there is something new to share); never while a game runs, never in the background;
 *  - a round is 1 listing + 1 download, and 1 upload only when this device has something newer;
 *  - only the newest 400 games are kept in the file.
 *
 * Games are matched across devices by a portable key (paths differ between devices): the platform and the
 * ROM's file name for ROMs, the package for apps, the AppID for Steam games.
 */
object LastPlayedSync {
    private const val TAG = "LastPlayedSync"
    private const val FOLDER = "Voltcap"
    private const val FILE = "voltcap-last-played.json"
    private const val MIN_GAP_MS = 20 * 60_000L
    private const val AFTER_GAME_GAP_MS = 2 * 60_000L
    private const val KEEP = 400

    @Volatile private var lastRoundAt = 0L
    @Volatile private var running = false

    /** The same game on any device: platform + ROM file name, the package, or the Steam AppID. */
    fun portableKey(game: Game): String = when {
        game.steamAppId != null -> "steam:${game.steamAppId}"
        game.sourceType == SourceType.APP && game.packageName != null -> "app:${game.packageName}"
        game.romPath != null -> {
            val file = game.romPath.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            "rom:${game.platform.lowercase()}:${normalize(file)}"
        }
        else -> "name:${game.platform.lowercase()}:${normalize(game.name)}"
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "")

    /** Whether a round may run now ([afterGame]: a game just ended, so a shorter wait is fine). */
    fun due(afterGame: Boolean): Boolean {
        if (running) return false
        val gap = if (afterGame) AFTER_GAME_GAP_MS else MIN_GAP_MS
        return System.currentTimeMillis() - lastRoundAt >= gap
    }

    /**
     * One round: merges the Drive file with this device's last-played times. Returns how many games got a
     * newer time from another device (0 = nothing changed here). Blocking (network): call on IO.
     */
    fun sync(context: Context, games: List<Game>, store: RecentlyPlayedStore, afterGame: Boolean = false): Int {
        if (!Features.syncLastPlayed.value || !CloudSync.isActive(context) || DriveAuth.needsSignIn(context)) return 0
        if (GameMode.active || !due(afterGame)) return 0
        running = true
        try {
            lastRoundAt = System.currentTimeMillis()
            val drive = DriveClient(context)
            val folder = drive.folder(FOLDER)
            val existing = drive.list(folder).firstOrNull { !it.isFolder && it.name == FILE }
            // The file is there but couldn't be read (dropped download, damaged file): skip this round. Writing
            // back only this device's games would drop every other device's entries from the file.
            val remote = if (existing == null) JSONObject() else
                runCatching { JSONObject(String(drive.downloadBytes(existing.id))).optJSONObject("games") ?: JSONObject() }.getOrNull()
                    ?: return 0

            val local = store.loadAll()
            val byPortable = HashMap<String, Game>()
            games.forEach { byPortable.putIfAbsent(portableKey(it), it) }

            // Newer elsewhere: record it here (only for games this device has).
            var pulled = 0
            remote.keys().forEach { key ->
                val g = byPortable[key] ?: return@forEach
                val t = remote.optJSONObject(key)?.optLong("t", 0L) ?: 0L
                if (t > (local[g.stableKey] ?: 0L) && t <= System.currentTimeMillis() + 60_000L) {
                    if (store.recordIfNewer(g.stableKey, t)) pulled++
                }
            }

            // Newer here: share it.
            var pushed = false
            val device = android.os.Build.MODEL
            val byStable = games.associateBy { it.stableKey }
            local.forEach { (stableKey, t) ->
                val g = byStable[stableKey] ?: return@forEach
                val key = portableKey(g)
                val theirs = remote.optJSONObject(key)?.optLong("t", 0L) ?: 0L
                if (t > theirs) {
                    remote.put(key, JSONObject().put("t", t).put("n", g.name).put("d", device))
                    pushed = true
                }
            }
            if (pushed || existing == null) {
                // Only the newest games travel.
                val all = remote.keys().asSequence().map { it to (remote.optJSONObject(it)?.optLong("t", 0L) ?: 0L) }
                    .sortedByDescending { it.second }.toList()
                val keep = JSONObject()
                all.take(KEEP).forEach { (k, _) -> keep.put(k, remote.getJSONObject(k)) }
                val doc = JSONObject().put("version", 1).put("updated", System.currentTimeMillis()).put("games", keep)
                drive.uploadBytes(doc.toString().toByteArray(), FILE, folder, existing?.id)
            }
            return pulled
        } catch (e: Exception) {
            Log.w(TAG, "Last-played sync skipped", e)
            return 0
        } finally {
            running = false
        }
    }
}
