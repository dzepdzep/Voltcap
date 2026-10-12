package com.shelf.launcher.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/**
 * One place that builds the image requests for game art.
 *
 * Cards animate their size, and a plain `AsyncImage(model = url)` re-resolves
 * its decode size (and so misses the memory cache) as that size changes. Here
 * each art slot decodes at one fixed size — the card's largest on-screen size
 * for that slot — with a fixed memory-cache key, so the card, the prefetch in
 * [GameDock] and the next card all share the same in-memory bitmap.
 */
internal enum class ArtKind { COVER, WIDE, HERO }

/** Shortest fade: art that is already in memory shows at once. */
private const val ArtFadeMillis = 100

/** Decode size in pixels for [kind] at the current card look — stable while the card animates. */
@Composable
internal fun cardArtPx(kind: ArtKind): IntSize {
    val sizes = cardSizes()
    val look = LocalCardLook.current
    val density = LocalDensity.current.density
    return remember(sizes, look.focusedWide, kind, density) { cardArtPx(sizes, look.focusedWide, kind, density) }
}

internal fun cardArtPx(sizes: CardSizes, focusedWide: Boolean, kind: ArtKind, density: Float): IntSize {
    // Covers: the biggest a cover gets (the focused, slightly larger one when
    // the focused card isn't a banner). Wide art: the banner, or a cover-sized crop.
    val bigCover = androidx.compose.ui.unit.DpSize(sizes.cover.width * 1.15f, sizes.cover.height * 1.15f)
    val dp = when (kind) {
        ArtKind.COVER -> bigCover
        ArtKind.WIDE, ArtKind.HERO -> if (focusedWide) sizes.focused else bigCover
    }
    return IntSize((dp.width.value * density).toInt().coerceAtLeast(1), (dp.height.value * density).toInt().coerceAtLeast(1))
}

/** A request for [url] at a fixed decode [px] size and cache key. */
internal fun artRequest(
    context: Context,
    url: String,
    kind: ArtKind,
    px: IntSize,
    fadeMillis: Int = ArtFadeMillis
): ImageRequest = ImageRequest.Builder(context)
    .data(url)
    .size(px.width, px.height)
    // Crop fills the card, so decode to cover it (not just fit inside).
    .scale(Scale.FILL)
    .precision(Precision.INEXACT)
    .memoryCacheKey("$url|${kind.name}|${px.width}x${px.height}")
    // While the full-size art decodes, its small thumbnail (if already in memory) stands in,
    // so fast scrolling shows soft covers instead of empty dark cards.
    .placeholderMemoryCacheKey(thumbKey(url))
    .memoryCachePolicy(CachePolicy.ENABLED)
    .allowHardware(true)
    .crossfade(fadeMillis)
    .build()

/** [artRequest] for a card, remembered per url / slot / size. */
@Composable
internal fun rememberCardArtRequest(url: String, kind: ArtKind): ImageRequest {
    val context = LocalContext.current
    val px = cardArtPx(kind)
    return remember(url, kind, px, context) { artRequest(context, url, kind, px) }
}

/** Memory-cache key of [url]'s small thumbnail (see [thumbRequest]). */
internal fun thumbKey(url: String) = coil.memory.MemoryCache.Key("thumb|$url")

/**
 * A tiny (~120 px, 2 bytes a pixel) decode of [url]. Cheap enough to keep many around: fast scrolling
 * shows these at once while the sharp art loads (every [artRequest] uses it as its placeholder).
 */
internal fun thumbRequest(context: Context, url: String): ImageRequest = ImageRequest.Builder(context)
    .data(url)
    .size(120, 180)
    .scale(Scale.FILL)
    .precision(Precision.INEXACT)
    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
    .allowHardware(false)
    .memoryCacheKey(thumbKey(url))
    .build()

/** Queues thumbnails for [urls] (skips the ones already in memory). Returns the handles to cancel. */
internal fun prefetchThumbs(context: Context, urls: List<String>): List<coil.request.Disposable> {
    val loader = coil.Coil.imageLoader(context)
    val cache = loader.memoryCache
    return urls.distinct().filter { cache?.get(thumbKey(it)) == null }.map { loader.enqueue(thumbRequest(context, it)) }
}
