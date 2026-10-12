package com.shelf.launcher.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/*
 * Update 156: one request shape for a style home's picture (StyleArt), for the carousel's covers (GameCarousel) and for
 * their warm-up: one fixed size and one memory key. A plain AsyncImage(url) asks at the card's own size, known only once
 * it is laid out; the warm-up decoded a smaller copy under the same key, so the card found it too small and read it from
 * storage again: an empty card for about a second, on every layout. With the same request on both sides, a warmed
 * picture shows in the card's very first frame, and one that isn't warmed shows its small thumbnail until it's in.
 */

/** The square box (px) a style home's picture is decoded to fit: half the screen's short side, 360 to 720. */
internal fun styleArtPx(context: Context): Int {
    val m = context.resources.displayMetrics
    return (minOf(m.widthPixels, m.heightPixels) / 2).coerceIn(360, 720)
}

/** The request a style home's card draws [url] with, and the one its warm-up queues (same size, same key). */
internal fun styleArtRequest(context: Context, url: String, px: Int = styleArtPx(context)): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(px, px)
        // Fit (not fill): a wide picture doesn't decode to many times the card's size; cards crop it as before.
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        .memoryCacheKey("$url|style|$px")
        // The small thumbnail (ArtRequests.kt) stands in while the sharp picture is read.
        .placeholderMemoryCacheKey(thumbKey(url))
        .memoryCachePolicy(CachePolicy.ENABLED)
        .crossfade(100)
        .build()

/** [styleArtRequest] for a card, remembered per picture. */
@Composable
internal fun rememberStyleArtRequest(url: String): ImageRequest {
    val context = LocalContext.current
    val px = styleArtPx(context)
    return remember(url, px, context) { styleArtRequest(context, url, px) }
}

/** Queues [urls] as the cards will ask for them (skips the ones already in memory). Returns the handles to cancel. */
internal fun warmStyleArt(context: Context, urls: List<String>): List<coil.request.Disposable> {
    val loader = coil.Coil.imageLoader(context)
    val cache = loader.memoryCache
    val px = styleArtPx(context)
    return urls.distinct().mapNotNull { url ->
        val req = styleArtRequest(context, url, px)
        if (req.memoryCacheKey?.let { cache?.get(it) } != null) null else loader.enqueue(req)
    }
}
