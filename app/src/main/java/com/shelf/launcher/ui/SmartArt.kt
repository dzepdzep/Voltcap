package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Batch 523 (owner, WWE SmackDown popup: "empty grey bars around the cover", "an image floating inside a large empty box"):
 * Smart Fill — how a popup's picture fills the room a style gives it, whatever the picture's shape.
 *
 *  - A centred crop that cuts little ([MAX_CROP] of the picture or less) fills the room edge to edge. A portrait cover cut to
 *    a wider room keeps its upper part (the title / logo sit there) rather than its exact middle.
 *  - A crop that would cut more keeps the WHOLE picture in front of a soft, darkened copy of itself filling the room (the
 *    same artwork, blurred by decoding it tiny and letting it scale up — every Android version, no per-frame blur cost),
 *    so there is never a grey or black bar.
 *  - Never stretched: only Crop and Fit are used. No picture: [noArt] (each style's own purposeful plate).
 * The picture's shape is the one the home already measured (CoverAspects) or the one it reports when it loads; until it is
 * known the whole picture shows over its soft copy (the safe form), then the crop if it qualifies.
 */
internal object SmartFill {
    /** The largest share of the picture a fill may cut away before the whole picture is kept instead. */
    const val MAX_CROP = 0.16f

    /** Share of a picture of shape [art] (w / h) cut away when it fills a room of shape [room] by a centred crop. Pure. */
    fun cropLoss(art: Float, room: Float): Float = when {
        art <= 0f || room <= 0f || !art.isFinite() || !room.isFinite() -> 0f
        art > room -> 1f - room / art
        else -> 1f - art / room
    }

    /** Whether the picture fills by a crop (true) or shows whole over its soft copy (false). Pure. */
    fun fills(art: Float, room: Float, maxCrop: Float = MAX_CROP): Boolean = cropLoss(art, room) <= maxCrop

    /**
     * The crop a picture may take in a room: a cover (box art - its title and logo run to its edges) [MAX_CROP]; a
     * backdrop / screenshot / fan art (scenery, nothing at its edges to lose) up to 45 %. Pure.
     */
    fun maxCropFor(url: String?, cover: String?): Float = if (url != null && url == cover) MAX_CROP else 0.45f

    /** Where the crop is anchored: a picture taller than its room keeps its upper part (vertical bias -0.4). Pure. */
    fun cropBias(art: Float, room: Float): Float = if (art < room) -0.4f else 0f
}

/**
 * Batch 523: a picture's soft copy — decoded tiny (28 px) and scaled up, which blurs it for free on every Android version.
 * Used instead of Modifier.blur behind cards that move: a RenderEffect blur is redrawn on every frame of a scroll (KONKR).
 */
@Composable
internal fun rememberSoftArt(url: Any, px: Int = 28): ImageRequest {
    val context = LocalContext.current
    return remember(url, px, context) { ImageRequest.Builder(context).data(url).size(px, px).memoryCacheKey("$url|soft$px").allowHardware(true).build() }
}

/**
 * (523p) The decode size whose upscale looks like a [blurPx] blur over a room [roomPx] wide: about one source pixel per blur
 * radius, never under 28 (the strongest soft copy) or over 160. Pure.
 */
internal fun softArtPx(roomPx: Float, blurPx: Float): Int =
    if (blurPx <= 0f || roomPx <= 0f) 160 else (roomPx / blurPx).toInt().coerceIn(28, 160)

@Composable
internal fun SmartArt(
    url: String?, contentDescription: String?, modifier: Modifier,
    /** What shows round a whole picture under its soft copy's dim (the style's well colour). */
    well: Color = Color.Black,
    /** The style's own request for [url] (backdrop / trimmed cover); default the shared style-art request. */
    request: Any? = null,
    /** The largest share a crop may cut ([SmartFill.maxCropFor]: less for a cover than for scenery). */
    maxCrop: Float = SmartFill.MAX_CROP,
    noArt: @Composable (Modifier) -> Unit = { m -> Box(m.background(well)) }
) {
    if (url == null) { noArt(modifier); return }
    BoxWithConstraints(modifier) {
        val room = if (maxHeight.value > 0f && maxHeight.value.isFinite() && maxWidth.value.isFinite()) maxWidth / maxHeight else 0.72f
        var art by remember(url) { mutableStateOf(CoverAspects.rawOf(url)) }
        val req = request ?: rememberStyleArtRequest(url)
        val a = art
        val onSuccess: (coil.compose.AsyncImagePainter.State.Success) -> Unit = { s ->
            val sz = s.painter.intrinsicSize
            if (sz.width > 0f && sz.height > 0f && sz.width.isFinite() && sz.height.isFinite()) {
                CoverAspects.note(url, sz.width, sz.height); art = sz.width / sz.height
            }
        }
        if (a != null && SmartFill.fills(a, room, maxCrop)) {
            AsyncImage(req, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, SmartFill.cropBias(a, room)), onSuccess = onSuccess)
        } else {
            val soft = rememberSoftArt(url)
            Box(Modifier.fillMaxSize().background(well))
            AsyncImage(soft, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f },
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            // Dimmed toward the well, a little more at the edges, so the whole picture reads as the subject.
            Box(Modifier.fillMaxSize().background(well.copy(alpha = 0.28f)))
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)))))
            AsyncImage(req, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.Center,
                onSuccess = onSuccess)
        }
    }
}
