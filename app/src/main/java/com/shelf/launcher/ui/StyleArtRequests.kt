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

/**
 * The request a style home's card draws [url] with, and the one its warm-up queues (same size, same key). Update 202:
 * [trim] for a game's cover (a card / carousel / case showing the cover): flat strips that are part of its file trimmed
 * off once at decode (TrimMargins.kt), under its own memory key. Off for anything else drawn this way (labels, discs,
 * wide art, screenshots) — a card and its warm-up must pass the same value to share the decode.
 */
internal fun styleArtRequest(context: Context, url: String, px: Int = styleArtPx(context), trim: Boolean = false, still: Boolean = false): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(px, px)
        // (Update 317: a cartridge's / disc's printed picture always still — mediaArtModel.)
        .apply { if (still) setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true, memoryCacheKey = null) }
        // Fit (not fill): a wide picture doesn't decode to many times the card's size; cards crop it as before.
        // (The decode only bounds the picture's size; every card draws it with ContentScale.Crop, filling its slot.)
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        // (Update 202: an animated cover skips the trim and stays animated.)
        .apply { if (trim) transformations(TrimUniformMargins).allowConversionToBitmap(false) }
        .memoryCacheKey("$url|style|$px" + (if (trim) "|" + TrimUniformMargins.KEY else "") + (if (still) "|still" else ""))
        // The small thumbnail (ArtRequests.kt) stands in while the sharp picture is read.
        .placeholderMemoryCacheKey(thumbKey(url))
        .memoryCachePolicy(CachePolicy.ENABLED)
        .crossfade(100)
        .build()

/** [styleArtRequest] for a card, remembered per picture ([trim]: it's the game's cover, Update 202). */
@Composable
internal fun rememberStyleArtRequest(url: String, trim: Boolean = false, still: Boolean = false): ImageRequest {
    val context = LocalContext.current
    val px = styleArtPx(context)
    return remember(url, px, context, trim, still) { styleArtRequest(context, url, px, trim, still) }
}

/**
 * Queues [urls] as the cards will ask for them (skips the ones already in memory). Returns the handles to cancel.
 * Update 202: [trimOf] says which of them are covers drawn trimmed (the same as the cards pass to [rememberStyleArtRequest]).
 */
internal fun warmStyleArt(context: Context, urls: List<String>, trimOf: (String) -> Boolean = { false }): List<coil.request.Disposable> {
    val loader = coil.Coil.imageLoader(context)
    val cache = loader.memoryCache
    val px = styleArtPx(context)
    return urls.distinct().mapNotNull { url ->
        val req = styleArtRequest(context, url, px, trimOf(url))
        if (req.memoryCacheKey?.let { cache?.get(it) } != null) null else loader.enqueue(req)
    }
}

/**
 * Update 202: a game's cover drawn at its own place's size (the details screen, the game window, the second screen —
 * places bigger than the cards' shared shape) with flat strips that are part of its file trimmed off (TrimMargins.kt).
 * Coil's own memory key (address, size, the trim) applies; the shared-shape interceptor leaves it alone (it has a
 * transformation). An animated cover skips the trim and stays animated.
 */
@Composable
internal fun rememberCoverRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(url, context) {
        ImageRequest.Builder(context)
            .data(url)
            .transformations(TrimUniformMargins)
            .allowConversionToBitmap(false)
            // The small thumbnail (ArtRequests.kt) stands in while it's read.
            .placeholderMemoryCacheKey(thumbKey(url))
            .crossfade(100)
            .build()
    }
}

/** Update 202: [rememberCoverRequest] for an address, anything else (an app's icon, a file) as it is. */
@Composable
internal fun coverModel(model: Any?): Any? = if (model is String) rememberCoverRequest(model) else model

/**
 * Update 157: a disc's / cartridge's / case's printed picture (labels, real disc scans, the 3D box front) in the same
 * shape as the cards and their warm-up: these drew a plain picture at their own size, never warmed — moving a few games
 * on, the discs came in as blank console discs and their art a second later. Anything that isn't an address (a file,
 * a bitmap) is passed through as it is.
 */
@Composable
// (Update 317, Toni: no animated covers on the cartridges and discs at all — their first frame, whatever Animated artwork
// says. A playing label redrew its turned 3D piece every frame.)
internal fun mediaArtModel(model: Any?, trim: Boolean = false): Any? = if (model is String) rememberStyleArtRequest(model, trim, still = true) else model

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
