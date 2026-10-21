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
        "Back of the box" to "Back of the box",
        // Update 301: a whole case scan of yours — back | spine | front side by side, as one picture.
        com.shelf.launcher.ui.BoxTexture.LABEL to "Full case scan (back | spine | front)",
        // Update 305: a cardboard box's other sides (Case3D: Settings › Game look › Box sides).
        "Box right side" to "Box right side (cardboard boxes)",
        "Box top" to "Box top (cardboard boxes)",
        "Box bottom" to "Box bottom (cardboard boxes)"
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
        // (Update 238, #111: which games' RetroAchievements links are refused, read before the first [apply].)
        runCatching { RaMatchStore(c.applicationContext).refusedLinks() }
    }

    /** (Update 238, #111) The pictures the details' scans list got from RetroAchievements (LauncherViewModel). */
    private val FROM_RA = setOf("Box (RetroAchievements)", "Title screen")

    /** Update 301: a game's console from its key, for the case's proportions (null when it can't be told). */
    @Volatile var platformOf: (String) -> String? = { null }

    /** Update 305: the games with picks of yours. */
    fun keys(): Set<String> = map.keys.toSet()

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
    fun apply(key: String, scraped: List<Pair<String, String>>): List<Pair<String, String>> {
        // (Update 238, #111: a game whose RetroAchievements link was refused — a ROM hack matched to the base game by
        // its title — no longer shows that game's RA box and title screen saved with its scans.)
        // (Update 302: TheGamesDB's back where the game has none of its own — TgdbBacks.)
        val scrapedPlus = TgdbBacks.fill(key, scraped)
        val list = if (key in RaMatchStore.refusedKeys) scrapedPlus.filter { it.first !in FROM_RA } else scrapedPlus
        var own = of(key)
        if (own.isEmpty()) return list
        // Update 301: your own full case scan stands for the front, back and spine (cut out of it — automatically, or
        // where you set the cuts) unless you picked one of those yourself; the scraped ones give way to it.
        own[com.shelf.launcher.ui.BoxTexture.LABEL]?.let { scan ->
            val cut = mapOf(
                "Box" to com.shelf.launcher.ui.BoxPart.FRONT,
                "Back of the box" to com.shelf.launcher.ui.BoxPart.BACK,
                "Spine" to com.shelf.launcher.ui.BoxPart.SPINE
            )
            own = own + cut.filterKeys { it !in own }.mapValues { (_, part) -> com.shelf.launcher.ui.BoxTexture.model(scan, part, platformOf(key)) }
        }
        // (Update 305: a label of yours — "Disc label" — is worn by the drawn disc / cartridge, so the scraped photo of the
        // real one gives way to it everywhere — unless you picked a photo for it too. It showed the photo, not your label.)
        val drop = if ("Disc label" in own && "Disc / cartridge" !in own) setOf("Disc / cartridge") else emptySet()
        val out = list.filter { it.first !in drop }.map { (label, url) -> label to (own[label] ?: url) }
        return out + own.filterKeys { k -> out.none { it.first == k } }.map { it.key to it.value }
    }
}
