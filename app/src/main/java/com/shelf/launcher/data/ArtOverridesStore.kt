package com.shelf.launcher.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Which slot an overridden image fills. */
enum class ArtSlot { COVER, WIDE, HERO, LOGO }

/** A per-game artwork override. Values are either an https URL (picked from a
 * scraper's alternatives) or a content:// URI (a custom image picked from the
 * device) — both load through Coil identically, so nothing downstream needs
 * to care which it is. */
data class ArtOverride(
    val cover: String? = null,
    val wide: String? = null,
    val hero: String? = null,
    val logo: String? = null
) {
    fun withSlot(slot: ArtSlot, value: String?): ArtOverride = when (slot) {
        ArtSlot.COVER -> copy(cover = value)
        ArtSlot.WIDE -> copy(wide = value)
        ArtSlot.HERO -> copy(hero = value)
        ArtSlot.LOGO -> copy(logo = value)
    }

    fun isEmpty(): Boolean = cover == null && wide == null && hero == null && logo == null
}

class ArtOverridesStore(context: Context) {
    private val prefs = context.getSharedPreferences("art_overrides", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, ArtOverride>>() {}.type

    fun loadAll(): Map<String, ArtOverride> {
        val json = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, ArtOverride>>(json, mapType) }.getOrDefault(emptyMap())
    }

    fun set(stableKey: String, slot: ArtSlot, value: String?) {
        val current = loadAll().toMutableMap()
        val updated = (current[stableKey] ?: ArtOverride()).withSlot(slot, value)
        if (updated.isEmpty()) current.remove(stableKey) else current[stableKey] = updated
        prefs.edit().putString(KEY, gson.toJson(current)).apply()
    }

    fun clear(stableKey: String) {
        val current = loadAll().toMutableMap()
        current.remove(stableKey)
        prefs.edit().putString(KEY, gson.toJson(current)).apply()
    }

    companion object {
        private const val KEY = "overrides_json"
    }
}
