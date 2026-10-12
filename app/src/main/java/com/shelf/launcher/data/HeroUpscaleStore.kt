package com.shelf.launcher.data

import android.content.Context

/**
 * Per-game upscale factor for the background (hero) art, set under
 * Artwork & details. Not stored = automatic (the global setting);
 * 1 = off; 2 / 4 / 6 / 8 = that many times.
 */
class HeroUpscaleStore(context: Context) {
    private val prefs = context.getSharedPreferences("hero_upscale", Context.MODE_PRIVATE)

    fun loadAll(): Map<String, Int> =
        prefs.all.mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }.toMap()

    /** [factor] null goes back to automatic. */
    fun set(gameKey: String, factor: Int?) {
        prefs.edit().apply {
            if (factor == null) remove(gameKey) else putInt(gameKey, factor)
        }.apply()
    }

    companion object {
        /** The choices offered, in order. */
        val FACTORS = listOf(2, 4, 6, 8)
    }
}
