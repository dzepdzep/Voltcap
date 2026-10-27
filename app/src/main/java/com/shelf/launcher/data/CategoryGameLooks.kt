package com.shelf.launcher.data

/**
 * Pass 5: every category (All games, a console, Favorites, a collection…) can have its own game look — Covers, Box art,
 * Cartridges & discs, Discs only… — over the look its home layout keeps.
 *
 * Keys are the category keys of [CategoryLayouts] ("ALL", "S:…", "P:…"). Only the game look (CardArtType) is the
 * category's own: the settings inside a look (Mix, Customize consoles, cases with their media…) stay shared. What is
 * saved as the layout's look never changes because of a category: the category's look is shown over it
 * (LauncherViewModel.withCategoryArt), and picking a look while you're in a category that has its own changes that
 * category's.
 */
object CategoryGameLooks {
    private fun parse(raw: String): Map<String, CardArtType> = runCatching {
        val o = org.json.JSONObject(raw.ifBlank { "{}" })
        o.keys().asSequence().mapNotNull { k -> CardArtType.values().firstOrNull { it.name == o.optString(k) }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    private fun write(m: Map<String, CardArtType>) {
        val o = org.json.JSONObject()
        m.forEach { (k, v) -> o.put(k, v.name) }
        Features.catGameLooks.value = if (m.isEmpty()) "" else o.toString()
    }

    fun all(): Map<String, CardArtType> = parse(Features.catGameLooks.value)

    /** [key]'s own game look, or null (it shows its layout's). */
    fun of(key: String): CardArtType? = all()[key]

    fun set(key: String, look: CardArtType?) {
        val m = all().toMutableMap()
        if (look == null) m.remove(key) else m[key] = look
        write(m)
    }

    fun clear() = write(emptyMap())
}
