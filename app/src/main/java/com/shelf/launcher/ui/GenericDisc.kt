package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.SystemLogos

/*
 * The blank CD for a game whose console has no disc of its own ("Discs only" with cartridge games as discs, or
 * "All CDs" in lists mixing consoles). It used to be the PlayStation 2 disc with its print; now it's a plain silver
 * CD with the game's cover printed on it and ITS console's branding: the console's logo from ES-DE's system logo
 * collection (SystemLogos) on a band across the top, the console's name along the bottom.
 */

/** "~generic-disc:<console>": the generic CD for a game of <console> (mediaFor). */
internal fun genericDiscKey(platform: String): String = "$GENERIC_DISC:$platform"

/** The console a generic CD key belongs to, or null when [platform] isn't one. */
internal fun genericDiscConsole(platform: String?): String? =
    platform?.takeIf { it.startsWith(GENERIC_DISC) }?.substringAfter(':', "")?.takeIf { it.isNotEmpty() }

/** A decoded console logo and whether it's light (white-ish) — light logos get a dark band, dark ones a light band. */
private class DiscLogo(val image: ImageBitmap, val light: Boolean)

private val logoCache = java.util.concurrent.ConcurrentHashMap<String, DiscLogo>()

/*
 * Update 329 (found on the AYN Thor: PS2 games drawn as plain discs carried the Nintendo 64 band after the N64 list was
 * shown): produceState's state isn't keyed — only its producer restarts on a new key — and the producer returned at
 * once when the state already held a picture, so a disc reused for another game (rows keep their slots while the
 * category changes) kept the previous console's logo / system disc for good. Keyed now: a new console starts fresh.
 */
@Composable
private fun rememberDiscLogo(platform: String): DiscLogo? = androidx.compose.runtime.key(platform) { rememberDiscLogoOf(platform) }

@Composable
private fun rememberDiscLogoOf(platform: String): DiscLogo? {
    val context = LocalContext.current
    val logo by produceState(logoCache[platform], platform) {
        if (value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = SystemLogos.model(context, platform) ?: return@withContext null
            runCatching {
                val req = coil.request.ImageRequest.Builder(context).data(file)
                    .decoderFactory(coil.decode.SvgDecoder.Factory())
                    .size(640, 320).allowHardware(false).build()
                val drawable = (coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable ?: return@runCatching null
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 640
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 320
                val s = minOf(640f / w, 320f / h, 4f)
                val bmp = android.graphics.Bitmap.createBitmap((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, bmp.width, bmp.height)
                drawable.draw(c)
                DiscLogo(bmp.asImageBitmap(), isLight(bmp)).also { logoCache[platform] = it }
            }.getOrNull()
        }
    }
    return logo
}

/** True when the logo's visible pixels are mostly light (average luminance of the opaque ones). */
private fun isLight(b: android.graphics.Bitmap): Boolean {
    var sum = 0.0; var n = 0
    val step = maxOf(1, minOf(b.width, b.height) / 48)
    var y = 0
    while (y < b.height) {
        var x = 0
        while (x < b.width) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) > 140) {
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * bl; n++
            }
            x += step
        }
        y += step
    }
    return n > 0 && sum / n > 170
}

/** A ring between [inner] and [outer] (fractions of the radius). */
private class DiscRingShape(private val inner: Float, private val outer: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2
        return Outline.Generic(Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r * outer))
            addOval(Rect(c, r * inner))
        })
    }
}

/**
 * The plain CD for a game of [console]: [art] (its cover / label) printed on the disc, the console's logo on a band
 * across the top and its name along the bottom. Square, fitted inside [modifier].
 */
@Composable
internal fun GenericDisc(console: String, art: Any?, modifier: Modifier = Modifier) {
    // Update 90: the console's own system disc (ES-DE style, shipped with the app) when there is one.
    if (com.shelf.launcher.data.SystemLogos.discAsset(console) != null) { SystemDisc(console, art, modifier); return }
    val logo = rememberDiscLogo(console)
    val name = ConsoleNames.display(console)
    val lightBand = logo?.light != true
    val bandColor = if (lightBand) Color(0xF2F3F3F5) else Color(0xEE1B1D23)
    val ink = if (lightBand) Color(0xFF1B1D23) else Color(0xFFF3F3F5)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side)) {
            // The silver disc under the print (shows at the rim and round the hub).
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2; val c = center
                drawCircle(
                    Brush.sweepGradient(
                        listOf(Color(0xFFD9DCE3), Color(0xFF9EA4B3), Color(0xFFEDEFF3), Color(0xFFB4BAC7), Color(0xFFE4E6EC), Color(0xFFD9DCE3)), c
                    ), r, c
                )
            }
            // The printed ring: the game's art, the console's band on top and its name below.
            Box(Modifier.fillMaxSize().clip(DiscRingShape(0.30f, 0.965f)).background(Color(0xFFE9E6DF))) {
                // (Update 240, #32/#57: an app / Android game without art of its own wears its icon on the disc — the icon
                // was blown up to the whole disc and cut by the hub, so only a smeared corner of it showed. Now a soft wash
                // of its colours across the print and the icon itself whole, under the hub, like a printed logo.)
                val icon = appIconArt(art)
                if (icon != null) DiscIconPrint(icon, side)
                else if (art != null) AsyncImage(mediaArtModel(art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) // (Update 157)
                // Top band with the logo (or the console's name while it loads / where there's none).
                Box(
                    Modifier.fillMaxWidth().height(side * 0.33f).align(Alignment.TopCenter).background(bandColor),
                    contentAlignment = Alignment.Center
                ) {
                    val logoW = side * 0.5f; val logoH = side * 0.15f
                    if (logo != null) Image(
                        logo.image, contentDescription = name, contentScale = ContentScale.Fit,
                        modifier = Modifier.offset(y = side * 0.035f).width(logoW).height(logoH)
                    ) else Text(
                        name, color = ink, fontSize = (side.value * 0.075f).coerceIn(6f, 22f).sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                        modifier = Modifier.offset(y = side * 0.04f).width(side * 0.62f)
                    )
                }
                // Bottom band: the console's name, like the format line on a real disc.
                Box(
                    Modifier.fillMaxWidth().height(side * 0.2f).align(Alignment.BottomCenter).background(bandColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, color = ink, fontSize = (side.value * 0.05f).coerceIn(5f, 15f).sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                        modifier = Modifier.offset(y = -side * 0.035f).width(side * 0.5f)
                    )
                }
            }
            // The clear hub, its rings and the hole; a soft shine across the disc.
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2; val c = center
                drawCircle(Color(0x66D7DBE3), r * 0.30f, c)
                drawCircle(Color(0xFFC9CDD6), r * 0.22f, c)
                drawCircle(Color(0x55FFFFFF), r * 0.22f, c, style = Stroke(r * 0.012f))
                drawCircle(Color(0x33000000), r * 0.30f, c, style = Stroke(r * 0.01f))
                drawCircle(Color(0x40000000), r * 0.995f, c, style = Stroke(r * 0.012f))
                // The hole.
                drawCircle(Color(0xCC24262D), r * 0.075f, c)
                drawCircle(Color(0x66FFFFFF), r * 0.075f, c, style = Stroke(r * 0.01f))
                drawCircle(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                        start = Offset(0f, 0f), end = Offset(size.width, size.height)
                    ), r, c
                )
            }
        }
    }
}


/**
 * (Update 240, #32/#57) [art] when it's an app's icon (the square ≤ 192 px bitmap AppIconCache hands out for an app
 * without art of its own) rather than a cover or label, as an ImageBitmap; else null.
 */
@Composable
private fun appIconArt(art: Any?): ImageBitmap? {
    val bmp = (art as? android.graphics.Bitmap)?.takeIf { it.width == it.height && it.width <= 192 } ?: return null
    return androidx.compose.runtime.remember(bmp) { bmp.asImageBitmap() }
}

/** (Update 240, #32/#57) An app icon printed on the plain CD: its colours washed across the ring, the icon whole below the hub. */
@Composable
private fun DiscIconPrint(icon: ImageBitmap, side: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxSize()) {
        Image(icon, null, contentScale = ContentScale.Crop, alpha = 0.32f, modifier = Modifier.fillMaxSize())
        // Between the hub (to 0.65 of the side) and the bottom band (from 0.8).
        Image(
            icon, null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.Center).offset(y = side * 0.225f).size(side * 0.15f)
        )
    }
}

private val discTplCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

@Composable
private fun rememberSystemDiscOverlay(console: String): ImageBitmap? {
    val path = com.shelf.launcher.data.SystemLogos.discAsset(console) ?: return null
    // (Update 329: keyed by the overlay, as rememberDiscLogo — a reused disc kept the previous console's overlay.)
    return androidx.compose.runtime.key(path) { rememberSystemDiscOverlayAt(path) }
}

@Composable
private fun rememberSystemDiscOverlayAt(path: String): ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState(discTplCache[path], path) {
        if (value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }?.also { discTplCache[path] = it }
    }
    return bmp
}

/**
 * A disc for [console] from its system disc (update 90, ES-DE's "PS2 style basic system discs"): the game's [art] printed
 * on the ring, the console's rim, hub, small logo and branded band from the overlay on top. Only for games of consoles
 * that never had CDs (cartridge games shown as a CD: "Discs only" / "All CDs"). Disc consoles keep their own discs as
 * they were (update 91).
 */
@Composable
internal fun SystemDisc(console: String, art: Any?, modifier: Modifier = Modifier) {
    val overlay = rememberSystemDiscOverlay(console)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side)) {
            // The disc under the print (shows while the art loads).
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Color(0xFF1B1C22), r * 0.955f, center)
            }
            Box(Modifier.fillMaxSize().clip(DiscRingShape(0.225f, 0.955f))) {
                if (art != null) AsyncImage(mediaArtModel(art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) // (Update 157)
            }
            // The clear hub round the hole.
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2; val c = center
                drawCircle(
                    Brush.sweepGradient(listOf(Color(0xCCD9DCE3), Color(0xAA9EA4B3), Color(0xCCEDEFF3), Color(0xAAB4BAC7), Color(0xCCD9DCE3)), c),
                    r * 0.225f, c
                )
                drawCircle(Color(0x9924262D), r * 0.19f, c)
            }
            if (overlay != null) Image(overlay, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            // A soft shine across it.
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                        start = Offset(0f, 0f), end = Offset(size.width, size.height)
                    ), r * 0.955f, center
                )
            }
        }
    }
}
