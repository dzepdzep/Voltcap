package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Update 133: "Only for this layout" — any Home setting can be kept for one home layout.
 *
 * Settings › Home starts with "Save changes for: All layouts / Only <this layout>". While Settings › Home is open,
 * what changes is noted (the look settings in the "appearance" prefs and every [Features] setting). With "Only this
 * layout" those changes become that layout's own values; with "All layouts" a change to a setting the layout has its
 * own value for makes it follow the shared one again.
 *
 * How it's kept: [own] holds each layout's own values (typed, as text); [shared] holds the shared value of every
 * setting the layout on screen has its own value for, while it is on screen (the prefs hold its own value then).
 * Switching layouts puts the shared values back and brings in the next layout's own ones ([leaving] / [entered],
 * called by the view model around the save). A layout's own value changed somewhere else while it's on screen (R1 tiles,
 * Edit Home) stays its own.
 */
object LayoutOnly {
    private const val PREFS = "layout_only"
    private const val GONE = "~"
    private val gson = Gson()

    /** "Only this layout" is picked (Settings › Home; back to "All layouts" when you leave Settings › Home). */
    val onlyThis = mutableStateOf(false)
    /** Bumped when a layout's own settings change (for the counts shown in Settings). */
    val version = mutableIntStateOf(0)

    private var loaded = false
    private val own = HashMap<String, HashMap<String, String>>()
    private val shared = HashMap<String, String>()

    /** The open Settings › Home: what everything was when it started (or was last noted), and for which layout. */
    private var before: Map<String, String>? = null
    private var sessionLayout: HomeLayout? = null

    /** Settings that are never one layout's own: the layout itself, the parked looks, device and account things. */
    private fun skip(key: String): Boolean {
        val k = key.substringAfter(':')
        return k == "home_layout" || k == "cat_layouts" || k == "cat_layout_default" || k == "cat_game_looks" || k == "arcade_restored" || k == "look_by_layout" || k.startsWith("defaults_v") ||
            k.startsWith("lights") || k.startsWith("discord") || k.startsWith("battery_watch") || k.startsWith("who_plays") ||
            k.contains("token", ignoreCase = true) || k.contains("api_key", ignoreCase = true) || k.contains("account", ignoreCase = true)
    }

    private fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            gson.fromJson<Map<String, Map<String, String>>>(p.getString("own", null) ?: "{}", object : TypeToken<Map<String, Map<String, String>>>() {}.type)
                ?.forEach { (l, m) -> own[l] = HashMap(m) }
        }
        runCatching {
            gson.fromJson<Map<String, String>>(p.getString("shared", null) ?: "{}", object : TypeToken<Map<String, String>>() {}.type)
                ?.let { shared.putAll(it) }
        }
    }

    private fun persist(c: Context) {
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("own", gson.toJson(own.filterValues { it.isNotEmpty() }))
            .putString("shared", gson.toJson(shared))
            .commit()
        version.intValue++
    }

    // ---- Reading and writing the settings, typed ----

    /** Every look setting ("a:" + key, typed b:/i:/l:/f:/s:/S:) and every feature setting ("f:" + key). */
    private fun snapshot(c: Context): Map<String, String> {
        val out = HashMap<String, String>()
        c.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE).all.forEach { (k, v) ->
            typed(v)?.let { out["a:$k"] = it }
        }
        Features.snapshot().forEach { (k, v) -> out["f:$k"] = v }
        return out.filterKeys { !skip(it) }
    }

    private fun typed(v: Any?): String? = when (v) {
        is Boolean -> "b:$v"
        is Int -> "i:$v"
        is Long -> "l:$v"
        is Float -> "f:$v"
        is String -> "s:$v"
        is Set<*> -> "S:" + gson.toJson(v.map { it.toString() })
        else -> null
    }

    private fun read(c: Context, key: String): String = when {
        key.startsWith("a:") -> typed(c.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE).all[key.substring(2)]) ?: GONE
        else -> Features.snapshot()[key.substring(2)] ?: GONE
    }

    /** Writes [values] (keys as in [snapshot]; [GONE] removes a look setting so its default applies). */
    private fun write(c: Context, values: Map<String, String>) {
        if (values.isEmpty()) return
        val e = c.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        val feats = HashMap<String, String>()
        values.forEach { (key, v) ->
            if (key.startsWith("f:")) { if (v != GONE) feats[key.substring(2)] = v; return@forEach }
            val k = key.substring(2)
            if (v == GONE || v.length < 2) { e.remove(k); return@forEach }
            val body = v.substring(2)
            when (v.take(2)) {
                "b:" -> e.putBoolean(k, body == "true")
                "i:" -> body.toIntOrNull()?.let { e.putInt(k, it) }
                "l:" -> body.toLongOrNull()?.let { e.putLong(k, it) }
                "f:" -> body.toFloatOrNull()?.let { e.putFloat(k, it) }
                "s:" -> e.putString(k, body)
                "S:" -> runCatching { gson.fromJson<List<String>>(body, object : TypeToken<List<String>>() {}.type) }.getOrNull()?.let { e.putStringSet(k, it.toSet()) }
            }
        }
        e.commit()
        if (feats.isNotEmpty()) Features.restore(feats)
    }

    // ---- Settings › Home ----

    /** Settings › Home opened (or the layout changed while it's open): notes what everything is now. */
    fun begin(c: Context, layout: HomeLayout) {
        load(c)
        before = snapshot(c)
        sessionLayout = layout
    }

    /** Settings › Home closed: what changed is kept (for this layout only, or for all), and "All layouts" is back. */
    fun end(c: Context) {
        commit(c)
        before = null
        sessionLayout = null
        onlyThis.value = false
    }

    /** "All layouts" / "Only this layout": what changed until now is kept the old way, then the new way from here. */
    fun setOnlyThis(c: Context, on: Boolean) {
        if (onlyThis.value == on) return
        commit(c)
        onlyThis.value = on
        before = snapshot(c)
    }

    /** What changed since [begin] (or the last commit) is kept: as the layout's own values, or as the shared ones. */
    private fun commit(c: Context) {
        val b = before ?: return
        val layout = sessionLayout ?: return
        val now = snapshot(c)
        val changed = (b.keys + now.keys).filter { !skip(it) && b[it] != now[it] }
        if (changed.isNotEmpty()) {
            val mine = own.getOrPut(layout.name) { HashMap() }
            changed.forEach { k ->
                val v = now[k] ?: GONE
                if (onlyThis.value) {
                    // The shared value is what it was before this layout first had its own.
                    if (k !in mine) shared[k] = b[k] ?: GONE
                    mine[k] = v
                } else if (k in mine) {
                    // Changed for all layouts: this layout follows the shared value again.
                    mine.remove(k)
                    shared.remove(k)
                }
            }
            persist(c)
        }
        before = now
    }

    // ---- Switching layouts (the view model, around saving the new layout) ----

    /** Before the switch away from [old] is saved: keeps what changed in Settings › Home and anything changed elsewhere. */
    fun leaving(c: Context, old: HomeLayout) {
        load(c)
        if (sessionLayout == old) commit(c)
        val mine = own[old.name] ?: return
        var changed = false
        mine.keys.toList().forEach { k ->
            val live = read(c, k)
            if (live != mine[k]) { mine[k] = live; changed = true }
        }
        if (changed) persist(c)
    }

    /**
     * After the switch from [old] to [new] is saved: the shared values come back for [old]'s own settings, and [new]'s
     * own ones come in. True when anything was written (the caller reads the look settings again).
     */
    fun entered(c: Context, old: HomeLayout, new: HomeLayout): Boolean {
        load(c)
        var wrote = false
        val back = HashMap<String, String>()
        own[old.name]?.forEach { (k, v) ->
            // (Only where the switch itself didn't bring another value, e.g. the next layout's parked card size.)
            if (read(c, k) == v) back[k] = shared[k] ?: return@forEach
        }
        if (back.isNotEmpty()) { write(c, back); wrote = true }
        shared.clear()
        val next = own[new.name].orEmpty()
        if (next.isNotEmpty()) {
            next.keys.forEach { k -> shared[k] = read(c, k) }
            write(c, next)
            wrote = true
        }
        persist(c)
        if (sessionLayout != null) { before = snapshot(c); sessionLayout = new }
        return wrote
    }

    /** How many settings [layout] has of its own. */
    fun count(c: Context, layout: HomeLayout): Int { load(c); version.intValue; return own[layout.name]?.size ?: 0 }

    /** [layout] (the one on screen) follows the shared settings again everywhere. True when anything was written. */
    fun clear(c: Context, layout: HomeLayout): Boolean {
        load(c)
        commit(c)
        val mine = own.remove(layout.name) ?: return false
        val back = mine.keys.mapNotNull { k -> shared[k]?.let { k to it } }.toMap()
        write(c, back)
        mine.keys.forEach { shared.remove(it) }
        persist(c)
        if (before != null) before = snapshot(c)
        return back.isNotEmpty()
    }
}
