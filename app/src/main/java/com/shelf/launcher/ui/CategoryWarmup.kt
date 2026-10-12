package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.SpecialFilter
import com.shelf.launcher.data.CategorySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Update 156: the categories either side of the one you're in (the next L2 / R2) are got ready while you look at this
 * one: their list of games worked out, and their first games' covers decoded (a small copy of each, and the card's
 * own size for the first few). Switching category sometimes showed empty cards for a second while every cover was
 * read from storage at once. Starts a moment after you land in a category and stops the moment you leave it.
 */
@Composable
internal fun CategoryWarmup(state: LauncherUiState) {
    val context = LocalContext.current.applicationContext
    val coverPx = cardArtPx(ArtKind.COVER)
    LaunchedEffect(state.platformFilter, state.specialFilter, state.games.size, coverPx) {
        delay(1_200)
        val small = com.shelf.launcher.device.MemoryBudget.small(context)
        val perSide = if (small) 10 else 16
        val sharp = if (small) 4 else 8
        val sides = withContext(Dispatchers.Default) {
            runCatching {
                // The same stops as L2 / R2 (LauncherViewModel.cycleCategory).
                val stops = state.categoryOrder.flatMap { key ->
                    when (key) {
                        CategorySettings.ALL -> listOf<Pair<SpecialFilter?, String?>>(null to null)
                        CategorySettings.CONSOLES -> state.availablePlatforms.map { null to it }
                        CategorySettings.COLLECTIONS -> state.collections.map { null to it.key }
                        else -> runCatching { SpecialFilter.valueOf(key) }.getOrNull()?.let { listOf(it to null) }.orEmpty()
                    }
                }
                if (stops.size < 2) return@runCatching emptyList<List<String>>()
                val cur = stops.indexOfFirst { (s, p) -> s == state.specialFilter && p == state.platformFilter }.coerceAtLeast(0)
                listOf(1, -1).map { d -> stops[(cur + d + stops.size) % stops.size] }.distinct().map { (s, p) ->
                    // (Worked out once here: the switch itself then finds the list ready.)
                    state.copy(specialFilter = s, platformFilter = p).visibleGames.take(perSide).mapNotNull { g ->
                        state.artOverrides[g.stableKey]?.cover ?: state.art[g.stableKey]?.gridUrl
                    }
                }
            }.getOrDefault(emptyList())
        }
        if (sides.isEmpty()) return@LaunchedEffect
        // (Left to finish even when you switch: what's on its way is what the next category shows.)
        prefetchThumbs(context, sides.flatten())
        val loader = coil.Coil.imageLoader(context)
        val cache = loader.memoryCache
        sides.flatMap { it.take(sharp) }.distinct().forEach { url ->
            val req = artRequest(context, url, ArtKind.COVER, coverPx)
            if (req.memoryCacheKey?.let { cache?.get(it) } == null) loader.enqueue(req)
        }
    }
}
