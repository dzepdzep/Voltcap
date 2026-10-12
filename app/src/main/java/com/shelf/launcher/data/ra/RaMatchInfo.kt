package com.shelf.launcher.data.ra

import com.shelf.launcher.data.RaMatchEntry
import com.shelf.launcher.data.RaMatchSource

/** How a library entry is linked to RetroAchievements, for showing next to its achievements. */
data class RaMatchInfo(
    val source: RaMatchSource,
    val gameId: Int,
    /** The RetroAchievements title it's linked to (null when unknown, e.g. a hand-typed ID). */
    val raTitle: String?,
    val raConsole: String?
) {
    /** Only a hash (or a hash you entered) proves it's the same dump. */
    val isVerified: Boolean get() = source == RaMatchSource.HASH || source == RaMatchSource.CUSTOM_HASH
}

/**
 * [linkedId] is Game.raGameId (manual override or auto match), [entry] the saved
 * auto-match. A link that differs from the auto-match — or has none — was typed by hand.
 */
fun raMatchInfo(linkedId: Int?, entry: RaMatchEntry?): RaMatchInfo? {
    if (linkedId == null) return null
    if (entry == null || entry.gameId != linkedId) {
        return RaMatchInfo(RaMatchSource.MANUAL, linkedId, null, null)
    }
    return RaMatchInfo(entry.kind, linkedId, entry.raTitle, entry.raConsole)
}
