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
 * Empty categories are NOT removed here (the category screen shows them with their count, a collection with no games
 * aside); L2 / R2 step past them themselves ([com.shelf.launcher.LauncherViewModel.cycleCategory]).
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
                CategorySettings.COLLECTIONS -> state.collections.map { null to it.key }
                else -> runCatching { SpecialFilter.valueOf(key) }.getOrNull()?.let { listOf(it to null) }.orEmpty()
            }
        }) { (special, platform) -> keyOf(special, platform) }
            .filterNot { (special, platform) -> CategoryTileOrder.isHidden(keyOf(special, platform)) }

    /** The category keys of [stops], as the category screen's tiles name them. */
    fun keys(state: LauncherUiState): List<String> = stops(state).map { (s, p) -> keyOf(s, p) }

    /** The stop one [step] (+1 / -1) from [current] in a list of [size]. [current] -1 (a category outside the list): +1 → the first, -1 → the last. */
    fun stepFrom(current: Int, step: Int, size: Int): Int =
        if (size <= 0) -1 else if (current < 0) (if (step > 0) 0 else size - 1) else ((current + step) % size + size) % size
}
