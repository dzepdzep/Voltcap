package com.shelf.launcher.data

import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.SpecialFilter

/**
 * Update 367: the one list of categories you can be in — what L2 / R2, the tabs of the style homes, the category screen's
 * tiles and the neighbour warm-up all step through. Before, each built its own copy and they disagreed: the tabs still
 * listed a tile you had taken off the category screen (so a tapped tab opened the wrong category, or one L2 / R2 never
 * stops on), and L2 / R2 from a category outside the list (a hidden start category) skipped the first one.
 *
 * Identity: "ALL", a special filter's name, or "p:<console or collection key>" (the category tiles' keys).
 * Visibility: [LauncherUiState.categoryOrder] (Settings › Categories: order, hidden groups, switches), your tile order
 * ([CategoryTileOrder]) and the tiles taken off the category screen ([CategoryTileOrder.isHidden]).
 * Empty consoles and specials are NOT removed here (the category screen shows them with their count); a collection with no games
 * is no stop and no tile. L2 / R2 step past the empty ones themselves ([com.shelf.launcher.LauncherViewModel.cycleCategory]).
 */
object CategoryStops {
    fun keyOf(special: SpecialFilter?, platform: String?): String = special?.name ?: platform?.let { "p:$it" } ?: "ALL"

    /** Every category in the order L2 / R2 step, as (special filter, console or collection key); (null, null) is All. */
    fun stops(state: LauncherUiState): List<Pair<SpecialFilter?, String?>> =
        CategoryTileOrder.sort(state.categoryOrder.flatMap { key ->
            when (key) {
                CategorySettings.ALL -> listOf<Pair<SpecialFilter?, String?>>(null to null)
                // (In your own console order — Choose a category › Reorder.)
                CategorySettings.CONSOLES -> PlatformOrder.sort(state.availablePlatforms).map { null to it }
                // (Update 368: a collection with no games is no stop, as it is no tile — it used to be a stop and a tab only.)
                CategorySettings.COLLECTIONS -> state.collections.filter { c -> hasGames(state, c.key) }.map { null to it.key }
                else -> runCatching { SpecialFilter.valueOf(key) }.getOrNull()?.let { listOf(it to null) }.orEmpty()
            }
        }) { (special, platform) -> keyOf(special, platform) }
            .filterNot { (special, platform) -> CategoryTileOrder.isHidden(keyOf(special, platform)) }

    private fun hasGames(state: LauncherUiState, key: String) =
        state.copy(platformFilter = key, specialFilter = null, searchQuery = "").visibleGames.isNotEmpty()

    /** The category keys of [stops], as the category screen's tiles name them. */
    fun keys(state: LauncherUiState): List<String> = stops(state).map { (s, p) -> keyOf(s, p) }

    /** The stop one [step] (+1 / -1) from [current] in a list of [size]. [current] -1 (a category outside the list): +1 → the first, -1 → the last. */
    fun stepFrom(current: Int, step: Int, size: Int): Int =
        if (size <= 0) -1 else if (current < 0) (if (step > 0) 0 else size - 1) else ((current + step) % size + size) % size
}
