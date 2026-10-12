package com.shelf.launcher.data.guide

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Guide pages you added to a game yourself (X in the guide reader, after searching or following
 * links): they show in the game's guide list from then on, like its own guides. Only the address and
 * title are kept, per game, on this device.
 */
object GameGuideLinks {
    data class Link(val title: String, val url: String, val addedAt: Long)

    private const val PREFS = "game_guide_links"
    private val gson = Gson()
    private val type = object : TypeToken<List<Link>>() {}.type

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(c: Context, gameKey: String): List<Link> =
        runCatching { gson.fromJson<List<Link>>(prefs(c).getString(gameKey, null) ?: "[]", type) }.getOrNull().orEmpty()

    /** Adds [url] (once) for [gameKey]. Returns false when it was already there. */
    fun add(c: Context, gameKey: String, title: String, url: String): Boolean {
        val l = list(c, gameKey)
        if (l.any { it.url == url }) return false
        prefs(c).edit().putString(gameKey, gson.toJson(l + Link(title, url, System.currentTimeMillis()))).apply()
        return true
    }

    fun remove(c: Context, gameKey: String, url: String) {
        prefs(c).edit().putString(gameKey, gson.toJson(list(c, gameKey).filter { it.url != url })).apply()
    }
}
