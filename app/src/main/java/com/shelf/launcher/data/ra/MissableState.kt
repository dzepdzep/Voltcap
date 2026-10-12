package com.shelf.launcher.data.ra

import android.content.Context
import com.shelf.launcher.data.RaAchievementDetail

/**
 * What a person should make of one achievement's "missable" flag.
 *
 * RetroAchievements only gives us a *design-time* flag (the set author marks
 * an achievement `type = missable`). It has NO signal for "you already missed
 * this" — nothing server-side tracks that, and we must not guess it from
 * progress, playtime or story position. So [MARKED_MISSED] exists only when the
 * person told us so; [OPEN] just means "flagged missable and not unlocked yet".
 */
enum class MissableState {
    NOT_MISSABLE,
    /** Flagged missable and already earned. */
    UNLOCKED,
    /** Flagged missable, not earned yet — may or may not still be obtainable. */
    OPEN,
    /** Flagged missable, not earned, and the person confirmed they missed it. */
    MARKED_MISSED
}

/** [markedMissed] = achievement IDs the person marked "I missed this" ([MissedMarksStore]). */
fun RaAchievementDetail.missableState(markedMissed: Set<Int>): MissableState = when {
    !isMissable -> MissableState.NOT_MISSABLE
    isUnlocked -> MissableState.UNLOCKED // unlocking beats a wrong "missed" mark
    id in markedMissed -> MissableState.MARKED_MISSED
    else -> MissableState.OPEN
}

/**
 * The person's own "I missed this" marks, by RetroAchievements achievement ID
 * (globally unique, so it survives re-linking a game). User-confirmed only.
 */
class MissedMarksStore(context: Context) {
    private val prefs = context.getSharedPreferences("ra_missed_marks", Context.MODE_PRIVATE)

    fun load(): Set<Int> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun set(achievementId: Int, missed: Boolean): Set<Int> {
        val updated = if (missed) load() + achievementId else load() - achievementId
        prefs.edit().putStringSet(KEY, updated.map { it.toString() }.toSet()).apply()
        return updated
    }

    private companion object { const val KEY = "missed_ids" }
}
