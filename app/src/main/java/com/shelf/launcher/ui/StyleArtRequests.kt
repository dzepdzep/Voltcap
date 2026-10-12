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

/**
 * Update 157: a disc's / cartridge's / case's printed picture (labels, real disc scans, the 3D box front) in the same
 * shape as the cards and their warm-up: these drew a plain picture at their own size, never warmed — moving a few games
 * on, the discs came in as blank console discs and their art a second later. Anything that isn't an address (a file,
 * a bitmap) is passed through as it is.
 */
@Composable
internal fun mediaArtModel(model: Any?): Any? = if (model is String) rememberStyleArtRequest(model) else model

/**
 * Update 157: every plain game picture of a card's size (a layout's own AsyncImage(url) with no key of its own) is
 * decoded once, in the shared style shape ([styleArtRequest]: one size, one memory key). The layouts each asked at
 * their own size, so a picture warmed for one shape was read from storage again for another, and moving a few games on
 * showed blank cards for a second on every layout. Big pictures (backgrounds, heroes), tiny ones (badges, icons),
 * requests with their own key, transformations or parameters are left as they are.
 */
internal class SharedArtInterceptor(private val px: Int) : coil.intercept.Interceptor {
    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val r = chain.request
        val url = r.data as? String
        val w = (chain.size.width as? coil.size.Dimension.Pixels)?.px
        val h = (chain.size.height as? coil.size.Dimension.Pixels)?.px
        val shareable = url != null && r.memoryCacheKey == null && r.transformations.isEmpty() && r.parameters.isEmpty() &&
            w != null && h != null && w <= px && h <= px && (w >= 160 || h >= 160) &&
            (url.startsWith("http") || url.startsWith("/") || url.startsWith("file:") || url.startsWith("content:"))
        if (!shareable) return chain.proceed(r)
        // Update 159: the size only through chain.withSize — Coil refuses a request whose size resolver an interceptor
        // changed (every such picture failed: the Grid's cards stayed plain colours).
        val shared = r.newBuilder()
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .memoryCacheKey("$url|style|$px")
            .build()
        return chain.withSize(coil.size.Size(px, px)).proceed(shared)
    }
}
