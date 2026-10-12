package com.shelf.launcher.data

import android.content.Context

/**
 * Update 137: your own picks for a game's real-media pictures (Game settings › Artwork & details): its box front, back,
 * 3D box, disc / cartridge, label and spine — any scan of the game, or a picture of yours — instead of only the cover,
 * capsule, background and logo. They replace what ScreenScraper found under the same name, so every look that draws the
 * box, disc, cartridge or spine (Box art, Cartridge / disc, Discs only, Spines, the 3D boxes) uses them.
 */
object PhysicalOverrides {
    /** The slots offered, with what they're called in the scraped list (ScreenScraper.physicalMedia). */
    val SLOTS: List<Pair<String, String>> = listOf(
        "Box" to "Box front (Box art looks)",
        "Disc / cartridge" to "Disc / cartridge (Cartridge / disc look)",
        "Disc label" to "Disc / cartridge label (drawn media)",
        "Spine" to "Spine (Spines look, shelves)",
        "Box (3D)" to "3D box",
        "Back of the box" to "Back of the box"
    )

    private var prefs: android.content.SharedPreferences? = null
    private val map = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    fun init(c: Context) {
        val p = c.applicationContext.getSharedPreferences("physical_overrides", Context.MODE_PRIVATE)
        prefs = p
        map.clear()
        for ((k, v) in p.all) {
            val s = v as? String ?: continue
            val (game, label) = k.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: continue
            map[game] = (map[game] ?: emptyMap()) + (label to s)
        }
    }

    /** This game's picks (label → address). */
    fun of(key: String): Map<String, String> = map[key] ?: emptyMap()

    fun set(key: String, label: String, url: String?) {
        val now = of(key)
        map[key] = if (url == null) now - label else now + (label to url)
        prefs?.edit()?.apply { if (url == null) remove("$key\t$label") else putString("$key\t$label", url) }?.apply()
    }

    fun clear(key: String) {
        val labels = of(key).keys
        map.remove(key)
        prefs?.edit()?.apply { labels.forEach { remove("$key\t$it") } }?.apply()
    }

    /** [list] (the scraped pictures) with this game's picks in place of the scraped ones of the same name. */
    fun apply(key: String, list: List<Pair<String, String>>): List<Pair<String, String>> {
        val own = of(key)
        if (own.isEmpty()) return list
        val out = list.map { (label, url) -> label to (own[label] ?: url) }
        return out + own.filterKeys { k -> out.none { it.first == k } }.map { it.key to it.value }
    }
}
