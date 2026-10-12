package com.shelf.launcher.data

/**
 * The achievement you're working on, pinned to the in-game RetroAchievements overlay (and marked in
 * the launcher's lists). One pin at a time; pinning another replaces it. Stored in
 * [Features.raPinned] as "gameId:achievementId" so it survives restarts and can be changed both
 * from the launcher and from the overlay while the game is running.
 */
object RaPin {
    /** (gameId, achievementId) of the pinned achievement, or null. Reads Compose state. */
    fun current(): Pair<Int, Int>? = parse(Features.raPinned.state.value)

    fun isPinned(gameId: Int, achievementId: Int): Boolean = current() == (gameId to achievementId)

    /** The pinned achievement of [gameId], if the pin belongs to that game. */
    fun pinnedFor(gameId: Int): Int? = current()?.takeIf { it.first == gameId }?.second

    fun pin(gameId: Int, achievementId: Int) { Features.raPinned.value = "$gameId:$achievementId" }

    fun unpin() { Features.raPinned.value = "" }

    /** Pins it, or unpins it when it is already the pinned one. Returns true when it is pinned now. */
    fun toggle(gameId: Int, achievementId: Int): Boolean =
        if (isPinned(gameId, achievementId)) { unpin(); false } else { pin(gameId, achievementId); true }

    private fun parse(s: String): Pair<Int, Int>? {
        val i = s.indexOf(':')
        if (i <= 0) return null
        val g = s.substring(0, i).toIntOrNull() ?: return null
        val a = s.substring(i + 1).toIntOrNull() ?: return null
        return g to a
    }
}
