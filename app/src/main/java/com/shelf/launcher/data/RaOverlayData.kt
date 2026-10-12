package com.shelf.launcher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the RetroAchievements overlay shows over a running game, worked out from the data Voltcap
 * already has (the game's achievement list, its unlocks, the "I missed this" marks):
 *  - progress (unlocked / total, points),
 *  - missables you can still get - the overlay's warning line, by name,
 *  - the next achievement worth going for: a missable you can still get first (it can't wait), then
 *    the easiest locked one (most players have it), then the next in the set's own order.
 * Published by the launcher when a game starts and after every unlock check; read by the overlay.
 */
object RaOverlayData {
    data class State(
        val gameTitle: String,
        val unlocked: Int,
        val total: Int,
        val points: Int,
        val totalPoints: Int,
        /** Missables not unlocked and not marked missed, in set order. */
        val missablesLeft: List<String>,
        val nextTitle: String?,
        val nextDescription: String?,
        val nextIsMissable: Boolean,
        /** Why that one: "missable", "easiest left (61 % of players)", "next in the set". */
        val nextWhy: String?,
        /** The last unlock this session, shown for a while. */
        val lastUnlock: String? = null,
        /** Every achievement still to get, in set order (the overlay's "Left" list; missables flagged). */
        val left: List<Item> = emptyList(),
        /** Everything unlocked, newest first (the overlay's "Unlocked" list). */
        val unlockedList: List<Item> = emptyList(),
        /** Badge pictures for the summary: the next one to go for, and the last unlock. */
        val nextBadge: String? = null,
        val lastUnlockBadge: String? = null,
        /** RetroAchievements game id (pinning from the overlay). */
        val gameId: Int? = null,
        /** Every achievement of the set (the pinned one is looked up here, unlocked or not). */
        val all: List<Item> = emptyList(),
        /** Ids unlocked so far. */
        val unlockedIds: Set<Int> = emptySet()
    )

    /** One achievement in the overlay's lists. */
    data class Item(
        val id: Int, val title: String, val description: String?, val points: Int, val percent: Float?, val missable: Boolean,
        val badge: String? = null,
        /** When it was unlocked ("2026-10-04 18:22:10"), for the Unlocked list. */
        val earned: String? = null
    )

    private fun RaAchievementDetail.item() = Item(
        id, title ?: "Achievement", description, points ?: 0, unlockPercent, isMissable,
        badge = badgeName?.takeIf { it.isNotBlank() }?.let { "https://media.retroachievements.org/Badge/$it.png" },
        earned = dateEarnedHardcore ?: dateEarned
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    /** For the game being played (null clears the overlay). */
    fun publish(gameTitle: String?, list: List<RaAchievementDetail>?, missed: Set<Int>, lastUnlock: String? = _state.value?.lastUnlock, gameId: Int? = null) {
        if (gameTitle == null || list.isNullOrEmpty()) { _state.value = null; return }
        val ordered = list.sortedWith(RA_DISPLAY_ORDER)
        val locked = ordered.filter { !it.isUnlocked }
        val missLeft = locked.filter { it.isMissable && it.id !in missed }
        // Ones marked "missed" can't be got any more: never suggested as the next one.
        val gettable = locked.filter { it.id !in missed }
        val easiest = gettable.filter { it.unlockPercent != null }.maxByOrNull { it.unlockPercent ?: 0f }
        // (Update 144: each starts with a capital - it's shown as its own line.)
        val (next, why) = when {
            missLeft.isNotEmpty() -> missLeft.first() to "Missable · get it before it's gone"
            easiest != null -> easiest to "Easiest left · ${String.format(java.util.Locale.ROOT, "%.0f", easiest.unlockPercent ?: 0f)} % of players have it"
            gettable.isNotEmpty() -> gettable.first() to "Next in the set"
            else -> null to null
        }
        _state.value = State(
            gameTitle = gameTitle,
            unlocked = ordered.size - locked.size,
            total = ordered.size,
            points = ordered.filter { it.isUnlocked }.sumOf { it.points ?: 0 },
            totalPoints = ordered.sumOf { it.points ?: 0 },
            missablesLeft = missLeft.mapNotNull { it.title },
            nextTitle = next?.title,
            nextDescription = next?.description,
            nextIsMissable = next?.isMissable == true,
            nextWhy = why,
            lastUnlock = lastUnlock,
            left = locked.filter { it.id !in missed }.map { it.item() },
            unlockedList = ordered.filter { it.isUnlocked }.map { it.item() }.sortedByDescending { it.earned.orEmpty() },
            nextBadge = next?.item()?.badge,
            lastUnlockBadge = lastUnlock?.let { t -> ordered.firstOrNull { it.title == t } }?.item()?.badge,
            gameId = gameId,
            all = ordered.map { it.item() },
            unlockedIds = ordered.filter { it.isUnlocked }.map { it.id }.toSet()
        )
        // A pinned achievement that has just been unlocked is done: the pin comes off by itself.
        if (gameId != null) RaPin.pinnedFor(gameId)?.let { pid -> if (ordered.any { it.id == pid && it.isUnlocked }) RaPin.unpin() }
    }

    fun clear() { _state.value = null }
}
