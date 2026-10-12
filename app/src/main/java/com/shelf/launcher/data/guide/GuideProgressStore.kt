package com.shelf.launcher.data.guide

import android.content.Context
import com.google.gson.Gson

/** Per-game walkthrough state. */
data class GameGuideProgress(
    val currentSectionId: String? = null,
    /** Furthest section reached, kept apart from [currentSectionId] so revisits don't lose progress. */
    val furthestSectionId: String? = null,
    val lastRichPresence: String? = null,
    /** Normalized Rich Presence key (see GuideLocator.correctionKey) -> section id. Always wins next time. */
    val corrections: Map<String, String> = emptyMap(),
    val spoilerLevel: SpoilerLevel = SpoilerLevel.NONE,
    val missablesMode: Boolean = false,
    val chosenGuideId: String? = null
)

/** SharedPreferences "guide_progress": one JSON blob per game (key "g:<gameKey>"). Nothing secret is stored. */
class GuideProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("guide_progress", Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun get(gameKey: String): GameGuideProgress = try {
        prefs.getString("g:$gameKey", null)?.let { gson.fromJson(it, GameGuideProgress::class.java) }
            ?.let { it.copy(corrections = it.corrections ?: emptyMap(), spoilerLevel = it.spoilerLevel ?: SpoilerLevel.NONE) }
            ?: GameGuideProgress()
    } catch (e: Exception) { GameGuideProgress() }

    @Synchronized
    fun update(gameKey: String, change: (GameGuideProgress) -> GameGuideProgress): GameGuideProgress {
        val next = change(get(gameKey))
        prefs.edit().putString("g:$gameKey", gson.toJson(next)).apply()
        return next
    }

    @Synchronized
    fun clear(gameKey: String) { prefs.edit().remove("g:$gameKey").apply() }

    /** Folder (SAF tree URI or path) with the user's own guides; null = none. */
    @Synchronized
    fun localFolder(): String? = prefs.getString("_local_folder", null)?.takeIf { it.isNotBlank() }

    @Synchronized
    fun setLocalFolder(folder: String?) {
        prefs.edit().apply { if (folder.isNullOrBlank()) remove("_local_folder") else putString("_local_folder", folder) }.apply()
    }
}
