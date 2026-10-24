package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Update 214: your own order of the category tiles (Choose a category › Reorder), every tile on its own — a console,
 * Android games, Other apps, Continue playing, Never played, All games, a collection — by its key ("ALL", a special
 * filter's name, "p:<console or collection>"), which stays the same through restarts, rescans, scraping and new games.
 * Moving a tile used to move the whole block it belonged to (all the consoles, all the collections) past the next block.
 * A tile that isn't in the order yet (a new console, a new collection) stands after the tile it follows by default.
 * Empty: the order from Settings › Categories, as before.
 */
object CategoryTileOrder {
    /** Bumped when the order changes, so the tiles are worked out again. */
    val version = mutableStateOf(0)
    private var order: List<String> = emptyList()
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("category_tile_order", Context.MODE_PRIVATE)
        prefs = p
        order = p.getString("order", "").orEmpty().split('|').filter { it.isNotBlank() }
        hidden = p.getString("hidden", "").orEmpty().split('|').filter { it.isNotBlank() }.toSet()
        version.value++
    }

    // ---- Update 325 (Toni: "Other apps appears in the categories screen for some reason — add a way to add or remove
    // categories there"): tiles you took off — gone from the category screen and from L2 / R2, until added back. ----

    @Volatile private var hidden: Set<String> = emptySet()

    /** Whether the tile [key] ("ALL", a special filter's name, "p:<console or collection>") was taken off. */
    fun isHidden(key: String): Boolean = key in hidden

    fun setHidden(key: String, off: Boolean) {
        hidden = if (off) hidden + key else hidden - key
        prefs?.edit()?.apply { if (hidden.isEmpty()) remove("hidden") else putString("hidden", hidden.joinToString("|")) }?.apply()
        version.value++
    }

    /** The order as saved now (to put it back when a move is cancelled). */
    fun snapshot(): List<String> = order

    /** [tiles] (in their default order) in your order. */
    fun <T : Any> sort(tiles: List<T>, keyOf: (T) -> String): List<T> {
        val saved = order
        if (saved.isEmpty() || tiles.size < 2) return tiles
        val byKey = tiles.associateBy(keyOf)
        val out = saved.mapNotNull { byKey[it] }.distinctBy(keyOf).toMutableList()
        val placed = out.mapTo(HashSet()) { keyOf(it) }
        // Tiles not in your order yet: after the tile they follow by default (or first, when nothing before them is placed).
        tiles.forEachIndexed { i, t ->
            val k = keyOf(t)
            if (k in placed) return@forEachIndexed
            var at = 0
            for (j in i - 1 downTo 0) {
                val idx = out.indexOfFirst { keyOf(it) == keyOf(tiles[j]) }
                if (idx >= 0) { at = idx + 1; break }
            }
            out.add(at, t)
            placed.add(k)
        }
        return out
    }

    /** Moves the tile at [from] to [to] within [current] (the tiles' keys as on screen) and remembers the result. */
    fun move(current: List<String>, from: Int, to: Int) {
        if (from !in current.indices || to !in current.indices || from == to) return
        val list = current.toMutableList()
        list.add(to, list.removeAt(from))
        set(list)
    }

    /** Puts back [saved] (from [snapshot]). */
    fun restore(saved: List<String>) = set(saved)

    fun reset() = set(emptyList())

    private fun set(list: List<String>) {
        order = list
        prefs?.edit()?.apply { if (list.isEmpty()) remove("order") else putString("order", list.joinToString("|")) }?.apply()
        version.value++
    }
}
