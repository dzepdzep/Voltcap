package com.shelf.launcher.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaBadges
import kotlinx.coroutines.async

/**
 * Update 503 (owner: "achievement badges appear one after another while I move between games"). Cause: every badge of a
 * game's strip was its own AsyncImage — six to ten separate reads + decodes from the disk cache, each drawn the frame it
 * finished, so a strip filled in one square at a time on every move (and from the network on a first visit).
 *
 * Two small parts, both without a single extra network request (cache-only reads, [CachePolicy.DISABLED] network):
 *  - [warm]: the focused game's and its neighbours' badges are decoded from the disk cache into memory ahead of the move,
 *    at their own size (RetroAchievements badges are 64 px, never sampled — so the copy serves every strip size; Coil
 *    reuses an unsampled bitmap for any INEXACT request). Bounded: [PER_GAME] badges per game, [REACH] games each side;
 *    the previous batch is cancelled on every move, so stale work never piles up.
 *  - [badgeSetAlpha]: a strip whose badges are not all in memory yet stays invisible (its space kept: nothing moves) until
 *    they are read from disk or [GATE_MS] passed, then shows as one panel. Keyed by the game's own address list, so a
 *    previous game's badges are never shown; with every badge already in memory it shows in the first frame.
 */
internal object BadgeWarm {
    const val PER_GAME = 12
    const val REACH = 2
    const val GATE_MS = 450L
    /** QA A/B only (`--es voltcap_test_badgewarm off`, test hooks file): the strips as before update 503. */
    @Volatile var enabled = true
    private var handles: List<coil.request.Disposable> = emptyList()

    /** The badges a strip shows first: the earned ones and the next ones to get, in the set's order. */
    fun urls(list: List<RaAchievementDetail>, max: Int = PER_GAME): List<String> {
        if (list.isEmpty()) return emptyList()
        val ordered = list.sortedWith(com.shelf.launcher.data.RA_DISPLAY_ORDER)
        val pick = ordered.filter { it.isUnlocked }.take(max / 2) + ordered.filter { !it.isUnlocked }.take(max / 2) + ordered.take(max)
        return pick.mapNotNull { RaBadges.url(it.badgeName) }.distinct().take(max)
    }

    private fun request(context: Context, url: String): ImageRequest = ImageRequest.Builder(context)
        .data(url)
        .size(coil.size.Size.ORIGINAL)
        .networkCachePolicy(CachePolicy.DISABLED)
        .build()

    fun inMemory(context: Context, url: String): Boolean =
        runCatching { coil.Coil.imageLoader(context).memoryCache?.get(MemoryCache.Key(url)) != null }.getOrDefault(false)

    /** Main thread. Cancels the last batch, then reads [groups] (focused game first) from the disk cache into memory. */
    fun warm(context: Context, groups: List<List<String>>) {
        handles.forEach { it.dispose() }
        val loader = coil.Coil.imageLoader(context)
        handles = groups.flatten().distinct().filterNot { inMemory(context, it) }.map { loader.enqueue(request(context, it)) }
    }

    /** Reads [urls] from the disk cache together (never the network). Cancellable. */
    suspend fun readAll(context: Context, urls: List<String>) {
        val loader = coil.Coil.imageLoader(context)
        kotlinx.coroutines.coroutineScope {
            urls.filterNot { inMemory(context, it) }.map { u -> async { loader.execute(request(context, u)) } }.forEach { it.await() }
        }
    }
}

/**
 * The strip's opacity (read in the layer phase: a strip that changes doesn't recompose its parent). 1 at once when every
 * badge is in memory; else 0 until they are read together (or [BadgeWarm.GATE_MS]), then a short fade the app's motion
 * level scales (Off / reduced motion: at once).
 */
@Composable
internal fun badgeSetAlpha(urls: List<String>): () -> Float {
    val context = LocalContext.current
    if (!BadgeWarm.enabled) return { 1f }
    val anim = remember(urls) { Animatable(if (urls.all { BadgeWarm.inMemory(context, it) }) 1f else 0f) }
    LaunchedEffect(urls) {
        if (anim.value >= 1f) return@LaunchedEffect
        kotlinx.coroutines.withTimeoutOrNull(BadgeWarm.GATE_MS) { BadgeWarm.readAll(context, urls) }
        anim.animateTo(1f, tween(140))
    }
    return { anim.value }
}

/** [badgeSetAlpha] as a modifier for a strip's Row. */
@Composable
internal fun Modifier.badgeSet(urls: List<String>): Modifier {
    val a = badgeSetAlpha(urls)
    return this.graphicsLayer { alpha = a() }
}

/**
 * Update 503: [BadgeWarm.warm] for the game you're on and [BadgeWarm.REACH] games each side, a moment after the selection
 * settles (holding the D-pad restarts it: nothing is read for games only passed over).
 */
@Composable
internal fun BadgeWarmEffect(games: List<com.shelf.launcher.data.Game>, focus: Int, details: Map<String, List<RaAchievementDetail>>) {
    if (details.isEmpty() || !BadgeWarm.enabled) return
    val context = LocalContext.current
    val key = games.getOrNull(focus)?.stableKey
    LaunchedEffect(key, details) {
        kotlinx.coroutines.delay(120)
        val order = listOf(0, 1, -1, 2, -2).filter { kotlin.math.abs(it) <= BadgeWarm.REACH }
        val groups = order.mapNotNull { d -> games.getOrNull(focus + d)?.let { details[it.stableKey] } }
            .map { BadgeWarm.urls(it) }.filter { it.isNotEmpty() }
        if (groups.isNotEmpty()) BadgeWarm.warm(context, groups)
    }
}
