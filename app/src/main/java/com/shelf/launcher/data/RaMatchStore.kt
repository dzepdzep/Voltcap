package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** How a game got linked to RetroAchievements. */
/** MANUAL is never stored in a [RaMatchEntry]; it's what a hand-typed ID reads as (see ra/RaMatchInfo). */
enum class RaMatchSource { HASH, CUSTOM_HASH, TITLE, NONE, MANUAL }

/**
 * The saved outcome of matching one library entry. Kept on the device so the
 * whole library is only hashed once: an entry is re-checked only when its
 * file changes (size or modified time), on request, or — for games that
 * didn't match by hash — once a week, in case RetroAchievements added the dump.
 *
 * Fields are nullable because Gson ignores Kotlin defaults when reading.
 */
data class RaMatchEntry(
    val gameId: Int? = null,
    val raTitle: String? = null,
    val source: RaMatchSource? = null,
    /** The file's RetroAchievements hash (or the custom one), if it could be computed. */
    val hash: String? = null,
    /** Why there's no hash / no hash match — shown in the per-game dialog. */
    val note: String? = null,
    val fileSize: Long = -1,
    val fileModified: Long = -1,
    val checkedAt: Long = 0,
    /** RaHasher.VERSION this entry was made with (0 = before versioning). */
    val hasherVersion: Int = 0,
    /** RetroAchievements' console name for the matched set (null in older saves). */
    val raConsole: String? = null
) {
    val kind: RaMatchSource get() = source ?: RaMatchSource.TITLE
    val isHashVerified: Boolean get() = kind == RaMatchSource.HASH || kind == RaMatchSource.CUSTOM_HASH
}

/**
 * Stores auto-match results keyed by Game.stableKey, plus any hash a person
 * entered by hand. Manual IDs in GameOverridesStore always win over both.
 */
class RaMatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("ra_matches", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val entriesType = object : TypeToken<Map<String, RaMatchEntry>>() {}.type
    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type
    private val legacyType = object : TypeToken<Map<String, Int>>() {}.type

    fun loadEntries(): Map<String, RaMatchEntry> {
        prefs.getString(KEY_ENTRIES, null)?.let { json ->
            return runCatching { gson.fromJson<Map<String, RaMatchEntry>>(json, entriesType) }
                .getOrNull() ?: emptyMap()
        }
        // Older builds matched by title only and stored bare IDs. Carry them
        // over as title matches with no file info, so the first run with hash
        // matching re-checks every one of them (fixing subset mix-ups).
        val legacy = prefs.getString(KEY_LEGACY, null) ?: return emptyMap()
        val ids = runCatching { gson.fromJson<Map<String, Int>>(legacy, legacyType) }.getOrNull() ?: return emptyMap()
        return ids.mapValues { (_, id) -> RaMatchEntry(gameId = id, source = RaMatchSource.TITLE) }
    }

    /** Merges [entries] into what's saved. */
    fun putEntries(entries: Map<String, RaMatchEntry>) {
        if (entries.isEmpty()) return
        prefs.edit()
            .putString(KEY_ENTRIES, gson.toJson(loadEntries() + entries))
            .remove(KEY_LEGACY)
            .apply()
    }

    /** Linked game IDs only — what the library applies to each Game. */
    fun loadAll(): Map<String, Int> =
        loadEntries().mapNotNull { (key, entry) -> entry.gameId?.let { key to it } }.toMap()

    fun clear() = prefs.edit().remove(KEY_ENTRIES).remove(KEY_LEGACY).apply()

    fun loadCustomHashes(): Map<String, String> {
        val json = prefs.getString(KEY_CUSTOM, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(json, stringMapType) }.getOrNull() ?: emptyMap()
    }

    fun setCustomHash(stableKey: String, hash: String?) {
        val updated = loadCustomHashes().toMutableMap()
        if (hash == null) updated.remove(stableKey) else updated[stableKey] = hash
        prefs.edit().putString(KEY_CUSTOM, gson.toJson(updated)).apply()
    }

    companion object {
        private const val KEY_LEGACY = "matches_json"
        private const val KEY_ENTRIES = "entries_v2_json"
        private const val KEY_CUSTOM = "custom_hashes_json"

        /** Shelf platform names mapped to RetroAchievements console names, so
         * the right game list gets pulled per system. RA's names are verbose
         * ("Super Nintendo Entertainment System"), ours are short. */
        val PLATFORM_TO_RA_CONSOLE = mapOf(
            "NES" to "NES/Famicom",
            "SNES" to "SNES/Super Famicom",
            "N64" to "Nintendo 64",
            "GB" to "Game Boy",
            "GBC" to "Game Boy Color",
            "GBA" to "Game Boy Advance",
            "NDS" to "Nintendo DS",
            "PS1" to "PlayStation",
            "PS2" to "PlayStation 2",
            "PSP" to "PlayStation Portable",
            "Genesis" to "Genesis/Mega Drive",
            "MasterSystem" to "Master System",
            "SegaCD" to "Sega CD",
            "Dreamcast" to "Dreamcast",
            "Saturn" to "Saturn",
            "Arcade" to "Arcade",
            "NeoGeo" to "Arcade",
            "PCEngine" to "PC Engine/TurboGrafx-16",
            "NeoGeoPocket" to "Neo Geo Pocket",
            "Atari2600" to "Atari 2600",
            "VirtualBoy" to "Virtual Boy",
            "WonderSwan" to "WonderSwan",
            "GameCube" to "GameCube",
        )
    }
}
