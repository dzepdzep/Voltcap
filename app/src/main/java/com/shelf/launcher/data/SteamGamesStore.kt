package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SteamGameEntry(val name: String, val steamAppId: Int)

/** Steam games are added manually (name + AppID) rather than scanned, since
 * there's no confirmed public API on this device for enumerating what's
 * actually installed in GameNative — see the note in AddSteamGameDialog. */
class SteamGamesStore(context: Context) {
    private val prefs = context.getSharedPreferences("steam_games", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<SteamGameEntry>>() {}.type

    fun loadAll(): List<SteamGameEntry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<SteamGameEntry>>(json, listType) }.getOrDefault(emptyList())
    }

    fun add(entry: SteamGameEntry) {
        val updated = loadAll().filterNot { it.steamAppId == entry.steamAppId } + entry
        save(updated)
    }

    /** Adds many entries with one write; returns how many AppIDs were new. Existing ones are left untouched. */
    fun addAll(entries: List<SteamGameEntry>): Int {
        val current = loadAll()
        val known = current.map { it.steamAppId }.toHashSet()
        val fresh = entries.filter { known.add(it.steamAppId) }
        if (fresh.isNotEmpty()) save(current + fresh)
        return fresh.size
    }

    fun remove(steamAppId: Int) {
        save(loadAll().filterNot { it.steamAppId == steamAppId })
    }

    private fun save(entries: List<SteamGameEntry>) {
        prefs.edit().putString(KEY, gson.toJson(entries)).apply()
    }

    companion object {
        private const val KEY = "entries_json"
    }
}
