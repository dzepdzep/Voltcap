package com.shelf.launcher.data

import com.shelf.launcher.SpecialFilter

/**
 * Pass 4: every category (All games, a console, Favorites, Continue playing, a collection…) can have its own home layout.
 *
 * Keys are the view model's category keys: "ALL", "S:<special filter>", "P:<platform / collection filter>". A category
 * without one of its own shows the default layout ([defaultLayout]). Switching category (L2 / R2, the category screen,
 * a tab) switches the layout through the usual layout switch (LauncherViewModel.setAppearance's path: each layout keeps
 * its own look and its "Only this layout" settings), so one category's layout settings never leak into another's.
 *
 * Picking a layout (Settings, R1) while you're in a category that has its own changes that category's; in any other
 * category it changes the default. Nothing here touches the category order, the game looks or ScreenLayouts (the
 * game-launch / display rules).
 */
object CategoryLayouts {
    private fun parse(raw: String): Map<String, HomeLayout> = runCatching {
        val o = org.json.JSONObject(raw.ifBlank { "{}" })
        o.keys().asSequence().mapNotNull { k -> HomeLayout.values().firstOrNull { it.name == o.optString(k) }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    private fun write(m: Map<String, HomeLayout>) {
        val o = org.json.JSONObject()
        m.forEach { (k, v) -> o.put(k, v.name) }
        Features.catLayouts.value = if (m.isEmpty()) "" else o.toString()
    }

    /** Every category's own layout. */
    fun all(): Map<String, HomeLayout> = parse(Features.catLayouts.value)

    /** [key]'s own layout, or null (it follows the default). */
    fun of(key: String): HomeLayout? = all()[key]

    /** The layout of the categories without their own; null until one category has its own. */
    fun defaultLayout(): HomeLayout? = HomeLayout.values().firstOrNull { it.name == Features.catLayoutDefault.value }

    fun setDefault(l: HomeLayout) { if (Features.catLayoutDefault.value != l.name) Features.catLayoutDefault.value = l.name }

    /** Gives [key] its own [layout] (null: back to the default). [current] is the layout on screen, kept as the default
     *  the first time any category gets its own. */
    fun set(key: String, layout: HomeLayout?, current: HomeLayout) {
        if (layout != null && defaultLayout() == null) setDefault(current)
        val m = all().toMutableMap()
        if (layout == null) m.remove(key) else m[key] = layout
        write(m)
    }

    /** Every category back to the default. */
    fun clear() = write(emptyMap())

    /** The layout [key] should show: its own, else the default (null: nothing to switch). */
    fun effective(key: String): HomeLayout? = of(key) ?: defaultLayout()
}

/** Pass 4: a category key's name as people read it ("All games", "Favorites", "PlayStation 2", a collection's name). */
fun categoryKeyLabel(key: String): String = when {
    key == "ALL" -> "All games"
    key.startsWith("S:") -> runCatching { SpecialFilter.valueOf(key.removePrefix("S:")).label }.getOrDefault(key.removePrefix("S:"))
    key.startsWith("P:") -> key.removePrefix("P:").let { p -> if (isCollectionKey(p)) collectionLabel(p) else ConsoleNames.label(p) }
    else -> key
}
