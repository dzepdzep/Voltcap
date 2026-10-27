package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.ConsoleArt
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.PlatformInfo
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Pass 4: ten more looks for the category screen (Settings › Categories › Category screen › Look), beside Tiles, Slides,
 * Wall and Grid. Each one is its own composition with its own focused state and motion; they share only the building
 * blocks below (the console's picture / a category's symbol, its name, count and facts, the glide).
 *
 * - The console pictures are Voltcap's own hardware set (ConsoleArt, 1200 × 800 canvases, drawn whole with Fit — never
 *   cropped or stretched); a picture you set for the category wins; else the scraped photo.
 * - All games, Favorites, Continue playing… and collections get a symbol, never made-up hardware.
 * - Only the focused category and its neighbours are built (each look's window), so far-off art isn't loaded.
 * - Motion follows "Animate the platform browser" (Features.pbAnimate) and Lite effects (no idle loops).
 */

// ---------------------------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------------------------

/** A category's picture: your own image, the console's hardware, else its scraped photo; null for symbols. */
internal fun lookHardware(tile: CategoryTile): String? =
    tile.customImage ?: tile.platform?.let { ConsoleArt.model(it) ?: PlatformInfo.media(it)?.photo }

/** The symbol for categories with no console of their own (All games, Favorites, Continue playing, collections…). */
internal fun lookSymbol(tile: CategoryTile): ImageVector = when (tile.key) {
    "ALL" -> Icons.Filled.Apps
    "FAVORITES" -> Icons.Filled.Star
    "RECENT", "SMART_CONTINUE" -> Icons.Filled.PlayCircle
    "MOST_PLAYED" -> Icons.Filled.TrendingUp
    "MASTERED" -> Icons.Filled.MilitaryTech
    "IN_PROGRESS", "ALMOST_MASTERED" -> Icons.Filled.Bolt
    "NEVER_PLAYED" -> Icons.Filled.NewReleases
    "FORGOTTEN" -> Icons.Filled.HourglassEmpty
    "REDISCOVER" -> Icons.Filled.Explore
    "TROPHIES" -> Icons.Filled.EmojiEvents
    "EMULATORS" -> Icons.Filled.Memory
    else -> if (tile.platform == null) Icons.Filled.Collections else Icons.Filled.SportsEsports
}

/** The category's colour (its hue), at a lightness. */
internal fun lookHue(tile: CategoryTile, l: Float = 0.45f, s: Float = 0.55f) = Color.hsl(tile.hue.mod(360).toFloat(), s, l)

/** "SONY · 2000", or null when Voltcap doesn't know (nothing is made up). */
internal fun lookFacts(tile: CategoryTile): String? = tile.platform?.let { p ->
    listOfNotNull(PlatformInfo.maker(p), PlatformInfo.year(p)?.toString()).joinToString(" · ").takeIf { it.isNotBlank() }
}

internal fun lookCount(tile: CategoryTile): String = if (tile.count == 1) "1 game" else "${tile.count} games"

/**
 * Where a picture's visible part is (its non-transparent pixels), as fractions of the picture — left, top, right, bottom —
 * and its width / height. Voltcap's console pictures are 1200 × 800 canvases with room round the hardware, so without this
 * a console stands above its floor and the consoles come out at different sizes. Worked out once per picture (small decode,
 * off the main thread) and kept.
 */
internal object ArtBounds {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, FloatArray>()
    fun cached(url: String): FloatArray? = cache[url]

    suspend fun measure(context: android.content.Context, url: String): FloatArray? = cache[url] ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val req = coil.request.ImageRequest.Builder(context).data(url).size(300, 200).allowHardware(false).build()
            val d = (coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable
            val bmp = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching null
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            var l = w; var t = h; var r = -1; var b = -1
            for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 24) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
            if (r < 0) floatArrayOf(0f, 0f, 1f, 1f, w.toFloat() / h)
            else floatArrayOf(l.toFloat() / w, t.toFloat() / h, (r + 1f) / w, (b + 1f) / h, w.toFloat() / h)
        }.getOrNull()?.also { cache[url] = it }
    }
}

/**
 * The console's picture, its visible part fitted into the box (proportions kept, never stretched or cropped) and — with
 * [stand] — standing on the box's bottom edge (a floor, a plinth), else centred. Or the category's symbol in a soft disc.
 * Shown once its visible part is known (cached after the first time), so it never jumps into place.
 */
@Composable
internal fun LookPicture(tile: CategoryTile, modifier: Modifier, stand: Boolean = false, symbolTint: Color = Color.White) {
    val hw = lookHardware(tile)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (hw != null) {
            val bounds by androidx.compose.runtime.produceState(ArtBounds.cached(hw), hw) { if (value == null) value = ArtBounds.measure(ctx, hw) }
            // (Decoded at up to twice the box — the visible part is scaled up into it — so it stays sharp; never past 1200 px.)
            val dens = LocalDensity.current.density
            val reqW = (constraints.maxWidth.coerceAtMost(4000) * 2).coerceIn(64, 1200)
            val model = remember(hw, reqW) { coil.request.ImageRequest.Builder(ctx).data(hw).size(reqW, reqW * 2 / 3).build() }
            AsyncImage(
                model = model, contentDescription = tile.label, contentScale = ContentScale.Fit, filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val bb = bounds
                    if (bb == null) { alpha = 0f; return@graphicsLayer }
                    // The picture as Fit draws it, then the visible part scaled up to fill the box and moved into place.
                    val bw = size.width; val bh = size.height; val aspect = bb[4]
                    val dw = minOf(bw, bh * aspect); val dh = dw / aspect
                    val left = (bw - dw) / 2f; val top = (bh - dh) / 2f
                    val cw = dw * (bb[2] - bb[0]); val ch = dh * (bb[3] - bb[1])
                    val z = minOf(bw / cw, bh / ch).coerceIn(1f, 2.4f)
                    val cx = left + dw * (bb[0] + bb[2]) / 2f
                    val cyTop = top + dh * bb[1]; val cyBottom = top + dh * bb[3]
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = z; scaleY = z
                    translationX = bw / 2f - cx * z
                    translationY = if (stand) bh - cyBottom * z else bh / 2f - (cyTop + cyBottom) / 2f * z
                }
            )
        } else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = if (stand) Alignment.BottomCenter else Alignment.Center) {
            val d = minOf(maxWidth, maxHeight) * 0.78f
            Box(
                Modifier.size(d).clip(CircleShape).background(Brush.radialGradient(listOf(lookHue(tile, 0.5f).copy(alpha = 0.9f), lookHue(tile, 0.18f)))),
                contentAlignment = Alignment.Center
            ) { Icon(lookSymbol(tile), tile.label, tint = symbolTint, modifier = Modifier.size(d * 0.5f)) }
        }
    }
}

/** The glide every look follows: a float that runs to [selected] (snaps without animation, or when far). */
@Composable
internal fun rememberLookGlide(selected: Int, animate: Boolean, stiffness: Float = Spring.StiffnessMediumLow): Animatable<Float, *> {
    val pos = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        if (!animate || abs(pos.value - selected) > 6f) pos.snapTo(selected.toFloat())
        else pos.animateTo(selected.toFloat(), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness))
    }
    return pos
}

/**
 * A 0 → 1 animation that restarts at every new [key] — read through [now], which already gives 0 in the frame where the key
 * has changed but the effect hasn't restarted it yet (QA SNAP-ANIMATE: Constellation showed the new system whole for one
 * frame before it came in).
 */
internal class LookStep(private val a: Animatable<Float, *>, private val seen: Array<Any?>, private val key: () -> Any?) {
    // (The animation's value is read every time — returning early without reading it left a graphics layer that had read
    // it in its "not yet" frame never told when it moved: the Constellation focus and the Vault screen stayed invisible.)
    fun now(): Float { val v = a.value; return if (seen[0] != key()) 0f else v }
}

@Composable
internal fun rememberLookStep(key: Any?, animate: Boolean, ms: Int, delayMs: Long = 0L, easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing): LookStep {
    val a = remember { Animatable(1f) }
    val seen = remember { arrayOf(key) }
    val k by rememberUpdatedState(key)
    LaunchedEffect(key) {
        if (!animate) { a.snapTo(1f); seen[0] = key; return@LaunchedEffect }
        a.snapTo(0f); seen[0] = key
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        a.animateTo(1f, tween(ms, easing = easing))
    }
    if (!animate) seen[0] = key
    return remember { LookStep(a, seen) { k } }
}

/**
 * Which ones a look builds: [reach] either side of the one you're on AND of where the glide is now — pressed quickly, the
 * glide lags behind the selection, and building round the selection alone dropped the ones still on screen (QA: Pocket
 * Worlds, a world vanished mid-slide).
 */
@Composable
internal fun lookWindow(selected: Int, pos: Animatable<Float, *>, reach: Int, last: Int): IntRange {
    val at by remember { androidx.compose.runtime.derivedStateOf { pos.value.roundToInt() } }
    return (minOf(selected, at) - reach).coerceAtLeast(0)..(maxOf(selected, at) + reach).coerceAtMost(last)
}

/** Taps: on the focused one it opens, on another it focuses it. */
private fun Modifier.lookTap(index: Int, selected: Int, onFocus: (Int) -> Unit, onPick: (Int) -> Unit): Modifier =
    pointerInput(index, selected) { detectTapGestures { if (index == selected) onPick(index) else onFocus(index) } }

@Composable
private fun LookTitle(tile: CategoryTile, size: Int, color: Color = Color.White, align: TextAlign = TextAlign.Start, weight: FontWeight = FontWeight.Black, family: FontFamily? = null) {
    // (R18: shown whole — wraps, shrinks readably, else slides; never "…".)
    FullText(tile.label, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = family, textAlign = align, maxLines = 2)
}

/** Which way the D-pad steps a look (platformBrowserKey). */
internal enum class LookNav { ROW, COLUMN, GRID }

internal fun lookNavOf(v: Features.PlatformView): LookNav = when (v) {
    Features.PlatformView.VAULT, Features.PlatformView.CHANNEL -> LookNav.COLUMN
    Features.PlatformView.DRAWERS -> LookNav.GRID
    else -> LookNav.ROW
}

/** Drawers per row in Collector's Drawers. */
internal const val DRAWER_COLS = 4

/** The order the D-pad goes through the categories in a look (Generations: by release year); null = the saved order. */
internal fun lookOrder(v: Features.PlatformView, tiles: List<CategoryTile>): List<Int>? =
    if (v == Features.PlatformView.GENERATIONS) generationOrder(tiles) else null


/** The scraped wordmark / logo of a platform (Platform Options › Scrape platform media), or null. */
internal fun lookLogo(tile: CategoryTile): String? = tile.platform?.let { PlatformInfo.media(it)?.logo }

/**
 * A special category's object (All games, Favorites, Continue playing, a collection…): a moulded acrylic block with the
 * symbol set into it — clearly an emblem, never made-up console hardware.
 */
@Composable
internal fun LookEmblem(tile: CategoryTile, modifier: Modifier, stand: Boolean = true) {
    BoxWithConstraints(modifier, contentAlignment = if (stand) Alignment.BottomCenter else Alignment.Center) {
        val s = minOf(maxWidth, maxHeight * 1.1f) * 0.62f
        Box(
            Modifier.size(s, s * 0.92f).clip(RoundedCornerShape(s * 0.14f))
                .background(Brush.linearGradient(listOf(lookHue(tile, 0.62f, 0.5f).copy(alpha = 0.55f), lookHue(tile, 0.30f, 0.6f).copy(alpha = 0.85f)), Offset.Zero, Offset(400f, 500f)))
                .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(s * 0.14f))
                .drawBehind {
                    // Glass: a bright bevel along the top, a soft sheen across the face.
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), Offset(0f, 0f), Offset(size.width * 0.55f, size.height * 0.55f)))
                    drawRect(Color.White.copy(alpha = 0.18f), size = Size(size.width, 3.dp.toPx()))
                },
            contentAlignment = Alignment.Center
        ) { Icon(lookSymbol(tile), tile.label, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(s * 0.46f)) }
    }
}

/** The hardware for a console, the emblem for anything else. */
@Composable
internal fun LookObject(tile: CategoryTile, modifier: Modifier, stand: Boolean = false) {
    if (lookHardware(tile) != null) LookPicture(tile, modifier, stand) else LookEmblem(tile, modifier, stand)
}

// ---------------------------------------------------------------------------------------------
// 1. System Stage — a museum hall: each system on its own lit plinth
// ---------------------------------------------------------------------------------------------

@Composable
internal fun StageLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val pos = rememberLookGlide(selected, animate, Spring.StiffnessLow)
    val accent = ShelfTheme.accent
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
        val w = maxWidth; val h = maxHeight
        // The hall: a dark wall meeting a floor that catches the light; a soft pool of light on the middle plinth.
        Canvas(Modifier.fillMaxSize()) {
            val floorY = size.height * 0.60f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF111217), Color(0xFF1A1B21)), 0f, floorY), size = Size(size.width, floorY))
            drawRect(Brush.verticalGradient(listOf(Color(0xFF26272D), Color(0xFF0C0C0F)), floorY, size.height), Offset(0f, floorY), Size(size.width, size.height - floorY))
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, floorY), Offset(size.width, floorY), 1.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.13f), Color.Transparent), Offset(size.width / 2f, floorY - size.height * 0.08f), size.height * 0.55f), size.height * 0.55f, Offset(size.width / 2f, floorY - size.height * 0.08f))
        }
        val win = lookWindow(selected, pos, 2, tiles.lastIndex)
        val lo = win.first; val hi = win.last
        // Far ones first, so the near plinth is drawn over them.
        for (i in (lo..hi).sortedByDescending { abs(it - selected) }) androidx.compose.runtime.key(tiles[i].key) {
            val tile = tiles[i]
            val plW = w * 0.30f; val plH = h * 0.26f  // the plinth
            val objW = w * 0.34f; val objH = h * 0.32f // the object on it
            val topFace = plH * 0.18f
            Box(
                Modifier.size(objW, objH + plH).align(Alignment.TopStart)
                    .graphicsLayer {
                        val r = i - pos.value
                        val a = abs(r).coerceAtMost(2.2f)
                        // Back along the hall to each side: smaller, higher, darker.
                        val k = 1f - 0.32f * a.coerceAtMost(1.5f)
                        scaleX = k; scaleY = k
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationX = (w.toPx() - objW.toPx()) / 2f + r * w.toPx() * 0.33f
                        translationY = h.toPx() * 0.92f - (objH + plH).toPx() - a * h.toPx() * 0.07f
                        alpha = (1f - 0.32f * a).coerceIn(0f, 1f)
                    }
                    .zIndex(10f - abs(i - selected))
                    .lookTap(i, selected, onFocus, onPick)
            ) {
                // (Read in the draw lambda only: in composition it rebuilt every plinth on every frame of the glide.)
                fun near() = (1f - abs(i - pos.value)).coerceIn(0f, 1f)
                // The plinth: its top seen a little from above (lit, in perspective), the front falling into shade, a
                // seam of light along the front edge; the pool of the spotlight on its top.
                Canvas(Modifier.align(Alignment.BottomCenter).size(plW, plH)) {
                    val tf = topFace.toPx(); val inset = size.width * 0.06f
                    val topPath = Path().apply { moveTo(inset, 0f); lineTo(size.width - inset, 0f); lineTo(size.width, tf); lineTo(0f, tf); close() }
                    drawPath(topPath, Brush.verticalGradient(listOf(Color(0xFF4A4B52), Color(0xFF5C5D64)), 0f, tf))
                    drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f + 0.22f * near()), Color.Transparent), Offset(size.width / 2f, tf * 0.55f), size.width * 0.42f), Offset(size.width * 0.12f, 0f), Size(size.width * 0.76f, tf))
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF26272C), Color(0xFF111114)), tf, size.height), Offset(0f, tf), Size(size.width, size.height - tf))
                    drawLine(Color.White.copy(alpha = 0.18f + 0.30f * near()), Offset(0f, tf), Offset(size.width, tf), 1.2.dp.toPx())
                    // The shadow the object casts on the top.
                    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), Offset(size.width / 2f, tf * 0.62f), size.width * 0.36f), Offset(size.width * 0.14f, tf * 0.30f), Size(size.width * 0.72f, tf * 0.66f))
                }
                // The museum label on the front: a small dark plate with the name, maker and year, the count.
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = plH * 0.14f).width(plW * 0.80f)
                        .graphicsLayer { alpha = near() }
                        .clip(RoundedCornerShape(3.dp)).background(Color(0xFF0B0B0D)).border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    FullText(tile.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOfNotNull(lookFacts(tile)?.uppercase(), lookCount(tile)).joinToString("  ·  "), color = accent.copy(alpha = 0.9f), fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 10.sp)
                    )
                }
                // The object, standing on the plinth's top.
                LookObject(tile, Modifier.align(Alignment.BottomCenter).padding(bottom = plH - topFace * 0.62f).size(objW, objH), stand = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. System Blades — a stack of glossy blades; the one you're on swings open, the others stand by as edges
// ---------------------------------------------------------------------------------------------

@Composable
internal fun BladesLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val pos = rememberLookGlide(selected, animate)
    val accent = ShelfTheme.accent
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2A3A2A), Color(0xFF0C110C)), Offset(900f, 300f), 1600f))) {
        val w = maxWidth; val h = maxHeight
        val top = 72.dp; val bottom = 52.dp
        val bladeH = h - top - bottom
        val edge = 54.dp                // a blade waiting at the side shows this much
        val openW = w * 0.62f            // the blade you're on
        // (±3: further blades are hidden behind these anyway — each one is a big overlapping layer, QA: 19 % janky frames.)
        val win = lookWindow(selected, pos, 3, tiles.lastIndex)
        for (i in win) androidx.compose.runtime.key(tiles[i].key) {
            val tile = tiles[i]
            val on = i == selected
            Box(
                Modifier.size(openW, bladeH).align(Alignment.TopStart)
                    .graphicsLayer {
                        val r = i - pos.value
                        val a = abs(r)
                        // The one you're on: brought forward, facing you. Before it: stacked at the left, edge on; after it:
                        // at the right, behind, each one blade-edge further away.
                        val openX = (w.toPx() - openW.toPx()) / 2f + 30.dp.toPx()
                        translationX = when {
                            r <= -1f -> openX - openW.toPx() + edge.toPx() * (1f + r) - 16.dp.toPx()
                            r >= 1f -> openX + openW.toPx() - edge.toPx() + (r - 1f) * edge.toPx() + 16.dp.toPx()
                            r < 0f -> openX + r * (openW.toPx() - edge.toPx() + 16.dp.toPx())
                            else -> openX + r * (openW.toPx() - edge.toPx() + 16.dp.toPx())
                        }
                        translationY = top.toPx()
                        rotationY = (r * 14f).coerceIn(-26f, 26f)
                        cameraDistance = 18f * density
                        val k = 1f - 0.06f * a.coerceAtMost(1f)
                        scaleX = k; scaleY = k
                        alpha = (1f - 0.12f * (a - 1f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                        // (A shadow only under the blade coming forward — the GPU's cost was every blade's.)
                        shadowElevation = if (a < 1f) (14f * (1f - a)).dp.toPx() else 0f
                        shape = RoundedCornerShape(14.dp); clip = true
                    }
                    .zIndex(if (on) 30f else 20f - abs(i - selected))
                    .background(Brush.linearGradient(listOf(lookHue(tile, 0.34f, 0.45f), lookHue(tile, 0.14f, 0.5f)), Offset.Zero, Offset(900f, 900f)))
                    .drawBehind {
                        // Bevel: a lit top-left edge, a dark lower edge; gloss across the upper third.
                        drawRect(Color.White.copy(alpha = 0.22f), size = Size(size.width, 1.5.dp.toPx()))
                        drawRect(Color.White.copy(alpha = 0.12f), size = Size(1.5.dp.toPx(), size.height))
                        drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
                        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, size.height * 0.35f), size = Size(size.width, size.height * 0.35f))
                    }
                    .then(if (on) Modifier.border(2.dp, accent.copy(alpha = 0.9f), RoundedCornerShape(14.dp)) else Modifier)
                    .lookTap(i, selected, onFocus, onPick)
            ) {
                // (Read only in the layers below: read here it rebuilt every blade on every frame of the glide.)
                fun open() = (1f - abs(i - pos.value)).coerceIn(0f, 1f)
                // Its face (fades in as it comes forward): the art, the console, the name, count and facts — built only for
                // the one you're on and its neighbours (QA: every blade's slideshow made 19 % janky frames).
                if (abs(i - selected) <= 1) Box(Modifier.fillMaxSize().graphicsLayer { alpha = open() }) {
                    CategoryArt(tile, CardShape.WIDE, Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f })
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                    LookObject(tile, Modifier.align(Alignment.TopCenter).padding(top = 30.dp).fillMaxWidth(0.52f).fillMaxHeight(0.44f))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        LookTitle(tile, 30)
                        Spacer(Modifier.height(4.dp))
                        Text(listOfNotNull(lookCount(tile), lookFacts(tile)).joinToString("   ·   "), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                // Its edge (when it stands at the side): the name read bottom to top, as on a spine.
                Box(Modifier.fillMaxHeight().width(edge).align(if (i < selected) Alignment.CenterEnd else Alignment.CenterStart).graphicsLayer { alpha = 1f - open() }) {
                    FullText(
                        tile.label, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center).requiredWidth(bladeH * 0.8f).graphicsLayer { rotationZ = -90f }, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Collector's Drawers — a wooden specimen cabinet; the drawer you're on slides out with its games inside
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DrawersLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val accent = ShelfTheme.accent
    val woodLight = Color(0xFF8B5E38); val woodDark = Color(0xFF4E3018); val brass = Color(0xFFC9A45C)
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C140E), Color(0xFF0E0906))))) {
        val w = maxWidth; val h = maxHeight
        val cols = DRAWER_COLS
        val rowsVisible = 3
        val row = selected / cols
        val totalRows = (tiles.size + cols - 1) / cols
        val firstRow = (row - 1).coerceIn(0, (totalRows - rowsVisible).coerceAtLeast(0))
        val cabX = 64.dp; val cabTop = 82.dp
        val cabW = w - cabX * 2
        val cellH = (h - cabTop - 64.dp - 40.dp) / rowsVisible
        val cellW = cabW / cols
        // The cabinet: thick frame, the dark recesses the drawers sit in.
        Box(Modifier.offset(cabX - 14.dp, cabTop - 14.dp).size(cabW + 28.dp, cellH * rowsVisible + 28.dp).clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF5E3C20), Color(0xFF3A2412))))
            .drawBehind {
                for (k in 0 until 60) drawLine(Color.Black.copy(alpha = 0.05f), Offset(0f, k * size.height / 60f), Offset(size.width, k * size.height / 60f + 6f), 1.2f)
                for (r in 0 until rowsVisible) for (c in 0 until cols) {
                    val x = 14.dp.toPx() + c * cellW.toPx() + 5.dp.toPx(); val y = 14.dp.toPx() + r * cellH.toPx() + 5.dp.toPx()
                    drawRoundRect(Color(0xFF120A04), Offset(x, y), Size(cellW.toPx() - 10.dp.toPx(), cellH.toPx() - 10.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
            })
        for (r in firstRow until firstRow + rowsVisible) for (c in 0 until cols) {
            val i = r * cols + c
            val tile0 = tiles.getOrNull(i)
            if (tile0 != null) androidx.compose.runtime.key(tile0.key) {
                val tile = tile0
                val on = i == selected
                // (Each drawer its own: the one you leave slides shut while the next one opens.) The opening is read only in
                // the layer / draw lambdas: read in composition it rebuilt both moving drawers every frame, their auto-sized
                // labels included (QA: ~10 % janky frames).
                val oS = androidx.compose.animation.core.animateFloatAsState(
                    if (on) 1f else 0f,
                    if (animate) tween(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.25f, 1f)) else androidx.compose.animation.core.snap(),
                    label = "drawer"
                )
                // The insides exist while the drawer is open or still closing (changes twice per move, not every frame).
                val inside by remember(oS) { androidx.compose.runtime.derivedStateOf { oS.value > 0.02f } }
                val pullMax = cellH * 0.55f
                Box(
                    Modifier.offset(cabX + cellW * c, cabTop + cellH * (r - firstRow)).size(cellW, cellH).padding(5.dp)
                        .zIndex(if (on) 5f else if (inside) 4f else 0f).lookTap(i, selected, onFocus, onPick)
                ) {
                    // Drawn out towards you: the front moves down and grows a little (nearer), the drawer's box shows
                    // above it — its inner walls, a divider, and the games standing in it, seen from above. The box has its
                    // full size; only the part the front has uncovered is drawn.
                    if (inside) Box(Modifier.fillMaxWidth().height(pullMax + 8.dp).drawWithContent {
                        // The box moves out with the front: its far end follows the front's top edge.
                        val shown = pullMax.toPx() * oS.value + 8.dp.toPx()
                        clipRect(bottom = shown) { translate(top = shown - size.height) { this@drawWithContent.drawContent() } }
                    }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val wall = 6.dp.toPx()
                            // Floor of the drawer, then its side walls (lighter end grain), in perspective.
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A190C), Color(0xFF6A4628)), 0f, size.height), size = size)
                            val side = Path().apply { moveTo(0f, 0f); lineTo(wall * 1.8f, 0f); lineTo(wall, size.height); lineTo(0f, size.height); close() }
                            drawPath(side, woodLight)
                            val side2 = Path().apply { moveTo(size.width, 0f); lineTo(size.width - wall * 1.8f, 0f); lineTo(size.width - wall, size.height); lineTo(size.width, size.height); close() }
                            drawPath(side2, woodLight)
                            drawRect(Color.Black.copy(alpha = 0.55f), size = Size(size.width, size.height * 0.18f)) // shade under the cabinet
                        }
                        // The games, upright, leaning back a little.
                        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                            val covers = tile.coverSlides.take(5)
                            if (covers.isEmpty()) LookObject(tile, Modifier.fillMaxSize())
                            covers.forEach { u ->
                                AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(0.92f)
                                    .graphicsLayer { rotationX = 18f; transformOrigin = TransformOrigin(0.5f, 1f); cameraDistance = 10f * density; shadowElevation = 3.dp.toPx() }
                                    .clip(RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                    // The drawer front: raised panel, a brass card-frame with the name, a brass cup pull.
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            val o = oS.value
                            translationY = pullMax.toPx() * o
                            val k = 1f + 0.05f * o; scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0.5f, 0f)
                            shadowElevation = (2f + 14f * o).dp.toPx(); shape = RoundedCornerShape(4.dp); clip = true
                        }
                            .background(Brush.verticalGradient(listOf(woodLight, woodDark)))
                            .drawBehind {
                                // The raised panel's bevel.
                                val inset = 7.dp.toPx()
                                drawRect(Color.White.copy(alpha = 0.10f), Offset(inset, inset), Size(size.width - inset * 2, 1.dp.toPx()))
                                drawRect(Color.Black.copy(alpha = 0.30f), Offset(inset, size.height - inset), Size(size.width - inset * 2, 1.5.dp.toPx()))
                                for (k in 0 until 14) drawLine(Color.Black.copy(alpha = 0.06f), Offset(0f, k * size.height / 14f), Offset(size.width, k * size.height / 14f + 3f), 1f)
                            }
                            .border(if (on) 2.dp else 0.dp, if (on) accent else Color.Transparent, RoundedCornerShape(4.dp))
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                            Box(Modifier.fillMaxWidth(0.80f).height(cellH * 0.36f).clip(RoundedCornerShape(2.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFE8CF8E), brass, Color(0xFF8E6F33))))
                                .padding(3.dp).background(Color(0xFFF2EAD6)).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                FullText(tile.label, color = Color(0xFF2B2016), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
                                    style = androidx.compose.ui.text.TextStyle(lineHeight = 14.sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
                            }
                            // Cup pull: a half-round brass shell with its shadow.
                            Canvas(Modifier.width(cellW * 0.26f).height(12.dp)) {
                                drawArc(Color.Black.copy(alpha = 0.35f), 0f, 180f, true, Offset(0f, -size.height * 0.2f), Size(size.width, size.height * 2.2f))
                                drawArc(Brush.verticalGradient(listOf(Color(0xFFF1DDA5), brass, Color(0xFF7A5C26))), 0f, 180f, true, Offset(0f, -size.height * 0.6f), Size(size.width, size.height * 2f))
                            }
                        }
                    }
                }
            }
        }
        tiles.getOrNull(selected)?.let { t ->
            Row(Modifier.align(Alignment.BottomStart).padding(start = cabX, bottom = 50.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t.label, color = Color(0xFFF2EAD6), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, maxLines = 1)
                Spacer(Modifier.width(14.dp))
                Text(listOfNotNull(lookCount(t), lookFacts(t)).joinToString("  ·  "), color = Color(0xFFF2EAD6).copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Pocket Worlds — a layered pixel-art scene per system; the console on a stone pedestal in it
// ---------------------------------------------------------------------------------------------

/** A world: its kind (decides the props) and palette. */
private enum class WorldKind { MEADOW, COAST, CITY, FOREST, DESERT, NEON, TWILIGHT }

private class World(
    val kind: WorldKind, val skyTop: Color, val skyBottom: Color, val far: Color, val mid: Color, val near: Color,
    val ground: Color, val light: Color, val night: Boolean
)

private fun worldFor(tile: CategoryTile): World = when (tile.platform?.let { PlatformInfo.maker(it) }) {
    "NINTENDO" -> World(WorldKind.MEADOW, Color(0xFF4FA8F0), Color(0xFFBDE6FF), Color(0xFF5B78A6), Color(0xFF3F9A48), Color(0xFF2F7A35), Color(0xFF6B4A2B), Color(0xFFFFE27A), false)
    "SEGA" -> World(WorldKind.COAST, Color(0xFF2C6BE0), Color(0xFF9CCBFF), Color(0xFF4F6FA8), Color(0xFF1E5FB5), Color(0xFFE6CF8F), Color(0xFFC9A96A), Color(0xFFFFFFFF), false)
    "SONY" -> World(WorldKind.CITY, Color(0xFF070B24), Color(0xFF2B2160), Color(0xFF1B1C46), Color(0xFF131433), Color(0xFF0D0D22), Color(0xFF15152E), Color(0xFFDDE4FF), true)
    "MICROSOFT" -> World(WorldKind.FOREST, Color(0xFF0E2A1A), Color(0xFF3E7E4E), Color(0xFF244A31), Color(0xFF17371F), Color(0xFF0E2614), Color(0xFF1A2A14), Color(0xFFB9F6CA), true)
    "ATARI", "COLECO", "MATTEL", "GCE" -> World(WorldKind.DESERT, Color(0xFF3B1206), Color(0xFFFF9A4A), Color(0xFFA0482A), Color(0xFF7A3A1E), Color(0xFFC9884E), Color(0xFF8E5A30), Color(0xFFFFE0A0), false)
    "SNK", "NEC", "BANDAI", "PANASONIC", "COMMODORE" -> World(WorldKind.NEON, Color(0xFF1A0B2E), Color(0xFFD2508A), Color(0xFF4A1F5E), Color(0xFF2E1240), Color(0xFF1C0A28), Color(0xFF241030), Color(0xFFFFD7F0), true)
    else -> World(WorldKind.TWILIGHT, lookHue(tile, 0.18f, 0.5f), lookHue(tile, 0.60f, 0.45f), lookHue(tile, 0.40f, 0.30f), lookHue(tile, 0.26f, 0.35f), lookHue(tile, 0.16f, 0.35f), lookHue(tile, 0.12f, 0.3f), Color.White, true)
}

/** One pixel world drawn into [s]: [r] is its place against the one in the middle (parallax), [t] the ambient clock (0…1). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorld(wd: World, seed: Int, r: Float, t: Float) {
    val px = 5.dp.toPx()
    fun q(v: Float) = kotlin.math.floor(v / px) * px
    fun rnd(k: Int): Float = (((seed.toLong() * 6364136223846793005L + k * 1442695040888963407L) ushr 33) % 10000L) / 10000f
    val W = size.width; val H = size.height
    fun shift(f: Float) = -r * W * f
    // Sky: bands with a checker dither between them (no smooth gradient).
    val bands = 9
    val bandH = q(H * 0.60f / bands) + px
    for (b in 0 until bands) {
        val c = androidx.compose.ui.graphics.lerp(wd.skyTop, wd.skyBottom, b / (bands - 1f))
        drawRect(c, Offset(0f, b * bandH), Size(W, bandH + 1f))
        if (b < bands - 1) {
            val n = androidx.compose.ui.graphics.lerp(wd.skyTop, wd.skyBottom, (b + 1) / (bands - 1f))
            // (A light checker of the next band's colour along the seam.)
            var x = if (b % 2 == 0) 0f else px
            while (x < W) { drawRect(n.copy(alpha = 0.55f), Offset(x, (b + 1) * bandH - px), Size(px, px)); x += px * 3 }
        }
    }
    // Sun or moon, stars.
    val sx = q(W * 0.16f + shift(0.05f)); val sy = q(H * 0.24f)
    if (wd.night) {
        for (k in 0 until 40) {
            val tw = if (((t * 8f + rnd(k) * 8f).toInt() % 5) == 0) 0.25f else 0.85f
            drawRect(Color.White.copy(alpha = tw * (0.4f + 0.6f * rnd(k + 99))), Offset(q(rnd(k + 7) * W + shift(0.03f)), q(rnd(k + 31) * H * 0.42f)), Size(px * 0.8f, px * 0.8f))
        }
        for (dy in 0 until 6) for (dx in 0 until 6) if ((dx - 2.5f) * (dx - 2.5f) + (dy - 2.5f) * (dy - 2.5f) < 8f && !(dx > 3 && dy < 3)) drawRect(wd.light, Offset(sx + dx * px, sy + dy * px), Size(px + 0.5f, px + 0.5f))
    } else {
        for (dy in 0 until 7) for (dx in 0 until 7) if ((dx - 3f) * (dx - 3f) + (dy - 3f) * (dy - 3f) < 10.5f) drawRect(wd.light, Offset(sx + dx * px, sy + dy * px), Size(px + 0.5f, px + 0.5f))
        // Clouds drifting.
        for (k in 0 until 4) {
            // (Parallax added after the wrap: wrapped together, a cloud jumped from one edge to the other mid-slide.)
            val cx = ((rnd(k + 3) * W + t * W * (0.25f + 0.1f * k)).mod(W + 60 * px)) - 30 * px + shift(0.15f)
            val cy = q(H * (0.07f + 0.07f * k))
            drawRect(Color.White.copy(alpha = 0.92f), Offset(q(cx), cy), Size(px * 14, px * 2))
            drawRect(Color.White.copy(alpha = 0.92f), Offset(q(cx) + px * 3, cy - px * 2), Size(px * 7, px * 2))
            drawRect(Color.White.copy(alpha = 0.5f), Offset(q(cx) + px, cy + px * 2), Size(px * 12, px))
        }
    }
    // Far range: stepped peaks (snow caps by day).
    run {
        val base = H * 0.58f
        var x = q(shift(0.2f).mod(px * 2)) - px * 2
        while (x < W + px * 2) {
            val u = (x - shift(0.2f)) / W
            val peak = 0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin(u * 5.3f + seed % 7)) * kotlin.math.sin(u * 1.7f + 1.3f + seed % 3)
            val top = q(base - H * 0.20f * peak.coerceAtLeast(0.1f))
            drawRect(wd.far, Offset(x, top), Size(px * 2 + 1f, base - top + H))
            if (!wd.night && peak > 0.62f) drawRect(Color.White.copy(alpha = 0.85f), Offset(x, top), Size(px * 2 + 1f, px * 2))
            x += px * 2
        }
    }
    // Middle layer: rolling hills with the world's props.
    val midBase = H * 0.70f
    run {
        var x = q(shift(0.45f).mod(px * 2)) - px * 2
        while (x < W + px * 2) {
            val u = (x - shift(0.45f)) / W
            val top = q(midBase - H * 0.07f * (0.5f + 0.5f * kotlin.math.sin(u * 7.1f + seed % 5)))
            drawRect(wd.mid, Offset(x, top), Size(px * 2 + 1f, H - top))
            x += px * 2
        }
        val n = 9
        for (k in 0 until n) {
            val x0 = q(rnd(k + 11) * W * 1.3f - W * 0.15f + shift(0.45f))
            val y0 = q(midBase - H * 0.02f)
            when (wd.kind) {
                WorldKind.MEADOW, WorldKind.FOREST, WorldKind.TWILIGHT -> {
                    // A pine: stacked triangles of blocks on a trunk.
                    val tone = androidx.compose.ui.graphics.lerp(wd.mid, Color.Black, 0.28f)
                    drawRect(Color(0xFF4A2E18), Offset(x0 + px * 2, y0 - px * 2), Size(px, px * 2))
                    for (l in 0 until 4) drawRect(tone, Offset(x0 + px * (l / 2f), y0 - px * (3 + l * 2)), Size(px * (5 - l), px * 2))
                }
                WorldKind.CITY, WorldKind.NEON -> {
                    val bh = px * (8 + (rnd(k + 51) * 10).toInt())
                    val bw = px * (4 + (rnd(k + 61) * 3).toInt())
                    drawRect(androidx.compose.ui.graphics.lerp(wd.mid, Color.Black, 0.2f), Offset(x0, y0 - bh), Size(bw, bh))
                    var yy = y0 - bh + px
                    while (yy < y0 - px) {
                        var xx = x0 + px
                        while (xx < x0 + bw - px) {
                            if (rnd((xx + yy).toInt() + k) > 0.45f) drawRect(wd.light.copy(alpha = 0.75f), Offset(xx, yy), Size(px * 0.8f, px * 0.8f))
                            xx += px * 2
                        }
                        yy += px * 2
                    }
                }
                WorldKind.DESERT -> {
                    // A mesa.
                    val mw = px * (8 + (rnd(k + 71) * 8).toInt())
                    drawRect(androidx.compose.ui.graphics.lerp(wd.mid, Color.Black, 0.15f), Offset(x0, y0 - px * 6), Size(mw, px * 6))
                    drawRect(androidx.compose.ui.graphics.lerp(wd.mid, Color.White, 0.12f), Offset(x0, y0 - px * 6), Size(mw, px))
                }
                WorldKind.COAST -> Unit
            }
        }
    }
    // Coast: the sea between the hills and the beach, sparkling.
    if (wd.kind == WorldKind.COAST) {
        val seaTop = q(H * 0.66f)
        drawRect(wd.mid, Offset(0f, seaTop), Size(W, H * 0.12f))
        for (k in 0 until 30) {
            val on = ((t * 10f + rnd(k) * 10f).toInt() % 3) == 0
            if (on) drawRect(Color.White.copy(alpha = 0.8f), Offset(q(rnd(k + 5) * W + shift(0.6f)), q(seaTop + rnd(k + 9) * H * 0.11f)), Size(px * 2, px * 0.6f))
        }
    }
    // Near ground: a grass (or sand / street) top edge with tufts, the soil below with flecks.
    val groundTop = q(H * 0.79f)
    drawRect(wd.near, Offset(0f, groundTop), Size(W, H - groundTop))
    drawRect(androidx.compose.ui.graphics.lerp(wd.near, Color.White, 0.18f), Offset(0f, groundTop), Size(W, px))
    run {
        var x = q(shift(1f).mod(px * 6)) - px * 6
        while (x < W) { drawRect(androidx.compose.ui.graphics.lerp(wd.near, Color.White, 0.25f), Offset(x, groundTop - px), Size(px, px)); x += px * 6 }
    }
    drawRect(wd.ground, Offset(0f, groundTop + px * 4), Size(W, H - groundTop - px * 4))
    for (k in 0 until 50) drawRect(androidx.compose.ui.graphics.lerp(wd.ground, Color.Black, 0.25f), Offset(q(rnd(k + 200) * W + shift(1f)), q(groundTop + px * 5 + rnd(k + 300) * (H - groundTop - px * 6))), Size(px, px))
    // Night: fireflies / sparks drifting.
    if (wd.night) for (k in 0 until 10) {
        val a = (0.5f + 0.5f * kotlin.math.sin((t * 6.283f * 3f) + k)).coerceIn(0f, 1f)
        drawRect(wd.light.copy(alpha = 0.6f * a), Offset(q(rnd(k + 400) * W + shift(0.8f)), q(H * (0.62f + 0.14f * rnd(k + 500)))), Size(px * 0.8f, px * 0.8f))
    }
}

@Composable
internal fun WorldsLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val pos = rememberLookGlide(selected, animate, Spring.StiffnessLow)
    val lite = LocalLiteEffects.current
    val clock = if (lite || !animate) null else rememberInfiniteTransition(label = "worlds").animateFloat(0f, 1f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth; val h = maxHeight
        val win = lookWindow(selected, pos, 1, tiles.lastIndex)
        val lo = win.first; val hi = win.last
        for (i in lo..hi) androidx.compose.runtime.key(tiles[i].key) {
            val tile = tiles[i]
            val wd = remember(tile.key) { worldFor(tile) }
            val seed = tile.key.hashCode()
            // One world gives way to the next with a short pan of its layers and a dissolve (side by side, the layers moving
            // at their own speeds cut each other off at the seam — QA: clouds and peaks popped in at the edge).
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (1f - abs(i - pos.value)).coerceIn(0f, 1f) }.lookTap(i, selected, onFocus, onPick)) {
                drawWorld(wd, seed, (i - pos.value) * 0.35f, clock?.value ?: 0f)
            }
            // The stone pedestal and the console on it (its own picture at its own proportions — no pixel filter).
            Box(
                Modifier.fillMaxSize().graphicsLayer { val r = i - pos.value; translationX = r * w.toPx() * 0.45f; alpha = (1f - abs(r) * 1.4f).coerceIn(0f, 1f) }
            ) {
                val pedW = w * 0.30f; val pedH = h * 0.09f
                // Stone blocks, two courses, with mortar lines and a lit top edge.
                Canvas(Modifier.align(Alignment.TopStart).offset((w - pedW) / 2, h * 0.79f - pedH).size(pedW, pedH)) {
                    val px = 5.dp.toPx()
                    val stone = Color(0xFF7C7670); val dark = Color(0xFF4F4A45); val lit = Color(0xFFA29B93)
                    drawRect(stone, size = size)
                    drawRect(lit, size = Size(size.width, px))
                    val course = (size.height - px) / 2f
                    for (c in 0 until 2) {
                        val y = px + c * course
                        drawRect(dark, Offset(0f, y + course - px * 0.6f), Size(size.width, px * 0.6f))
                        var x = if (c == 0) 0f else px * 5
                        while (x < size.width) { drawRect(dark, Offset(x, y), Size(px * 0.6f, course)); x += px * 10 }
                    }
                    drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, size.height - px), Size(size.width, px))
                }
                LookObject(tile, Modifier.align(Alignment.TopStart).offset((w - w * 0.30f) / 2, h * 0.79f - pedH - h * 0.26f + 2.dp).size(w * 0.30f, h * 0.26f), stand = true)
            }
        }
        // A shade under the screen's title row: on a bright daytime sky the title and the game count were hard to read.
        Box(Modifier.fillMaxWidth().height(72.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.Transparent))))
        // (The sign changes once the new world has slid in: before, the new name stood over the old world while it slid.)
        val sign = rememberLookStep(selected, animate, 260, delayMs = 180)
        tiles.getOrNull(selected)?.let { t ->
            // The world's name on a pixel signboard.
            Column(Modifier.align(Alignment.TopCenter).padding(top = 70.dp).graphicsLayer { alpha = sign.now(); translationY = (1f - sign.now()) * -8.dp.toPx() }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(Color(0xFF1B1410)).border(4.dp, Color(0xFFE8D8B0)).padding(4.dp).border(2.dp, Color(0xFF8C6A3E)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(t.label.uppercase(), color = Color(0xFFFFF4D6), fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Text("${t.count} GAMES" + (lookFacts(t)?.let { "  ·  " + it.uppercase() } ?: ""), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Archive 1994 — a page of a hardware catalogue
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ArchiveLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val pos = rememberLookGlide(selected, animate)
    val paper = Color(0xFFF0ECE3); val ink = Color(0xFF1B1A18); val faint = Color(0xFF8A8478); val plate = Color(0xFFE4DFD3)
    val titles = LocalTitleOptions.current
    BoxWithConstraints(Modifier.fillMaxSize().background(paper)) {
        val w = maxWidth
        // Masthead and foot in ink (the screen's own title, buttons and legend sit on them, white on dark).
        Box(Modifier.fillMaxWidth().height(64.dp).background(ink).align(Alignment.TopCenter))
        Box(Modifier.fillMaxWidth().height(44.dp).background(ink).align(Alignment.BottomCenter))
        val win = lookWindow(selected, pos, 1, tiles.lastIndex)
        val lo = win.first; val hi = win.last
        for (i in lo..hi) androidx.compose.runtime.key(tiles[i].key) {
            val t = tiles[i]
            Row(
                Modifier.fillMaxSize().padding(top = 64.dp, bottom = 44.dp)
                    .graphicsLayer { val r = i - pos.value; translationX = r * w.toPx() * 0.35f; alpha = (1f - abs(r) * 1.6f).coerceIn(0f, 1f) }
                    .padding(horizontal = 64.dp, vertical = 18.dp).lookTap(i, selected, onFocus, onPick)
            ) {
                // The plate: the object on a grey ground, its figure caption under it.
                Column(Modifier.weight(1.15f).fillMaxHeight()) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(plate).padding(28.dp)) {
                        LookObject(t, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Fig. %02d".format(i + 1), color = ink, fontSize = 11.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                        FullText(t.label + (lookFacts(t)?.let { ", $it" } ?: ""), color = faint, fontSize = 11.sp, fontFamily = FontFamily.Serif)
                    }
                }
                Spacer(Modifier.width(44.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text("SYSTEM %02d / %02d".format(i + 1, tiles.size), color = faint, fontSize = 11.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.height(10.dp))
                    // (A fixed size, two lines at most: an auto size re-fitted while the page slid and the title flickered.)
                    // (R18: a name longer than two lines slides along one, never "…".)
                    FullText(t.label, color = ink, fontSize = if (t.label.length > 22) 28.sp else 34.sp, fontFamily = FontFamily.Serif, lineHeight = 34.sp, maxLines = 2, autoFit = false)
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.width(48.dp).height(3.dp).background(ink))
                    Spacer(Modifier.weight(1f))
                    // The facts as a two-column table: small label over its value, a hairline above each row.
                    val facts = buildList {
                        t.platform?.let { p -> PlatformInfo.maker(p)?.let { add("Manufacturer" to it.lowercase().split(' ').joinToString(" ") { s -> s.replaceFirstChar { c -> c.uppercase() } }) } }
                        t.platform?.let { p -> PlatformInfo.year(p)?.let { add("First released" to it.toString()) } }
                        // (Favourites go with the count, and the last-played game takes the fourth cell: as a row of its own
                        // it didn't fit under a two-line title and was cut off.)
                        add("In the collection" to (lookCount(t) + if (t.favorites > 0) "  ·  ${t.favorites} ★" else ""))
                        t.lastPlayedTitle?.let { add("Last played" to titles.display(it)) } ?: add("Favourites" to t.favorites.toString())
                    }
                    facts.chunked(2).forEach { row ->
                        Box(Modifier.fillMaxWidth().height(0.6.dp).background(ink.copy(alpha = 0.35f)))
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            row.forEach { (k, v) ->
                                Column(Modifier.weight(1f)) {
                                    Text(k.uppercase(), color = faint, fontSize = 9.sp, letterSpacing = 2.sp, maxLines = 1)
                                    Spacer(Modifier.height(2.dp))
                                    FullText(v, color = ink, fontSize = 14.sp, fontFamily = FontFamily.Serif)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.6.dp).background(ink.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Channel Surf — a CRT set: each system is a channel with its own ident
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ChannelLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    // The change: the picture collapses (0 → 0.35), a burst of snow (0.35 → 0.7), the new channel rolls in (0.7 → 1).
    val change = rememberLookStep(selected, animate, 360, easing = LinearEasing)
    // The clip comes on after you stay on a channel a moment (gameplay when the platform has one, else its art).
    var dwell by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(selected) { dwell = false; kotlinx.coroutines.delay(900); dwell = true }
    val t0 = tiles.getOrNull(selected)
    if (t0 != null) BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17130F), Color(0xFF0A0806))))) {
        val t = t0
        val boxW = maxWidth; val boxH = maxHeight
        val c = change.now()
        // The set: dark plastic cabinet, the glass in a rounded "tube" shape.
        Box(
            Modifier.padding(start = 64.dp, end = 64.dp, top = 70.dp, bottom = 50.dp).fillMaxSize()
                .clip(RoundedCornerShape(30.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2C2A28), Color(0xFF161514))))
                .padding(16.dp)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(44.dp)).background(Color.Black).lookTap(selected, selected, onFocus, onPick)) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    // Collapse to a bright line, then open again with a short roll.
                    val collapse = if (c < 0.35f) 1f - c / 0.35f * 0.98f else if (c < 0.7f) 0.02f else ((c - 0.7f) / 0.3f).coerceIn(0.02f, 1f)
                    scaleY = collapse
                    translationY = if (c >= 0.7f) (1f - (c - 0.7f) / 0.3f) * size.height * 0.08f else 0f
                    alpha = if (c in 0.35f..0.7f) 0f else 1f
                }) {
                    CategoryArt(t, CardShape.WIDE, Modifier.fillMaxSize())
                    val video = t.platform?.let { PlatformInfo.media(it)?.video }
                    if (dwell && video != null && !LocalLiteEffects.current) VideoBackdrop(video, withSound = false, modifier = Modifier.fillMaxSize(), shade = false)
                    // The ident: the console on the right, lower-third in the system's colour.
                    LookObject(t, Modifier.align(Alignment.CenterEnd).padding(end = 30.dp).size(boxW * 0.26f, boxH * 0.34f))
                }
                // Glass: scanlines, curvature shading at the edges, a soft reflection top-left.
                Canvas(Modifier.fillMaxSize()) {
                    val step = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, y), Size(size.width, step * 0.45f)); y += step }
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), center, size.maxDimension * 0.62f))
                    drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), Offset(size.width * 0.25f, size.height * 0.15f), size.width * 0.4f), Offset(-size.width * 0.1f, -size.height * 0.2f), Size(size.width * 0.75f, size.height * 0.7f))
                    if (c in 0.35f..0.7f) {
                        val rnd = java.util.Random((c * 997).toLong())
                        for (k in 0 until 520) drawRect(Color.White.copy(alpha = 0.18f + 0.35f * rnd.nextFloat()), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), Size(4f, 2f))
                    }
                    if (c < 0.35f) drawRect(Color.White.copy(alpha = 0.5f * (c / 0.35f)), Offset(0f, size.height / 2f - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
                }
                // On-screen display (green, outlined, as a set draws it).
                Text(
                    "CH %02d".format(selected + 1), color = Color(0xFF8CFF8C), fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(2f, 2f), 2f)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 34.dp).graphicsLayer { alpha = if (c < 1f || !dwell) 1f else 0.85f }
                )
                // Lower third: the system's colour bar, its logo (or name) and the count.
                Row(Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 28.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(8.dp).fillMaxHeight().background(lookHue(t, 0.55f, 0.7f)))
                    Column(Modifier.background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        val logo = lookLogo(t)
                        if (logo != null) AsyncImage(logo, t.label, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.height(26.dp).width(200.dp))
                        else Text(t.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(listOfNotNull(lookCount(t), lookFacts(t)).joinToString("  ·  ").uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, letterSpacing = 1.5.sp, maxLines = 1)
                    }
                }
            }
        }
        // The set's front: a brand plate and two knobs under the glass.
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 54.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(2) { Box(Modifier.padding(start = 10.dp).size(14.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF6A6560), Color(0xFF242220))))) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 7. Generations — a museum timeline: home consoles above the line, handhelds below, by release year
// ---------------------------------------------------------------------------------------------

/**
 * Console generations as Wikipedia's "History of video game consoles" groups the home systems. Handhelds are kept on their
 * own lane (they don't follow the home generations neatly) and are not given a generation here; anything else (computers,
 * PC, Android, Steam, collections, smart lists) is in "Other" — nothing is guessed.
 */
private val GENERATION: Map<String, Int> = mapOf(
    "Atari2600" to 2, "Intellivision" to 2, "ColecoVision" to 2, "Vectrex" to 2, "Atari5200" to 2,
    "NES" to 3, "MasterSystem" to 3, "Atari7800" to 3,
    "PCEngine" to 4, "PCEngineCD" to 4, "Genesis" to 4, "SNES" to 4, "NeoGeo" to 4, "SegaCD" to 4, "NeoGeoCD" to 4, "32X" to 4,
    "3DO" to 5, "Jaguar" to 5, "Saturn" to 5, "PS1" to 5, "N64" to 5,
    "Dreamcast" to 6, "PS2" to 6, "GameCube" to 6, "Xbox" to 6,
    "Xbox360" to 7, "PS3" to 7, "Wii" to 7,
    "WiiU" to 8, "PS4" to 8, "XboxOne" to 8, "Switch" to 8,
    "PS5" to 9, "XboxSeries" to 9
)
private val HANDHELD = setOf("GB", "GBC", "GBA", "NDS", "3DS", "PSP", "PSVita", "GameGear", "Lynx", "NeoGeoPocket", "WonderSwan", "VirtualBoy")

private fun keyOf(t: CategoryTile): String? = t.platform?.let { p -> ConsoleArt.keyFor(p) ?: p }
private fun generationOf(t: CategoryTile): Int? = keyOf(t)?.let { GENERATION[it] }
private fun handheldOf(t: CategoryTile): Boolean = keyOf(t) in HANDHELD
private fun yearOf(t: CategoryTile): Int? = t.platform?.let { PlatformInfo.year(it) }

/** By release year (then the saved order); systems without a known year after them, in the saved order. Never saved. */
internal fun generationOrder(tiles: List<CategoryTile>): List<Int> {
    val (dated, rest) = tiles.indices.partition { yearOf(tiles[it]) != null && (generationOf(tiles[it]) != null || handheldOf(tiles[it])) }
    return dated.sortedWith(compareBy({ yearOf(tiles[it]) }, { it })) + rest
}

private fun ordinal(n: Int) = n.toString() + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

@Composable
internal fun GenerationsLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val order = remember(tiles) { generationOrder(tiles) }
    val at = order.indexOf(selected).coerceAtLeast(0)
    val accent = ShelfTheme.accent
    // Where each stop sits on the line: by year (one year = [yearW]); undated ones after the last year, one slot apart.
    val years = order.map { yearOf(tiles[it]) }
    val firstYear = years.filterNotNull().minOrNull() ?: 1980
    val lastYear = years.filterNotNull().maxOrNull() ?: firstYear
    // (Two systems of the same year side by side: each takes its own slot along the line.)
    val slots = remember(order) {
        val out = FloatArray(order.size); var prevX = -10f; var undated = 0
        order.forEachIndexed { k, i ->
            val y = yearOf(tiles[i])
            val x = if (y != null) (y - firstYear).toFloat() else (lastYear - firstYear + 3 + undated++ * 1.6f).toFloat()
            out[k] = maxOf(x, prevX + 1.15f); prevX = out[k]
        }
        out
    }
    val pos = rememberLookGlide(at, animate)
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101318), Color(0xFF181C23))))) {
        val w = maxWidth; val h = maxHeight
        val yearW = w * 0.11f
        // (Line a little above the middle: at 0.50 the handheld lane reached down into the name of the system you're on.)
        val axisY = h * 0.46f
        fun slotX(p: Float): Float { // fractional stop → slot position (interpolated)
            val k = p.toInt().coerceIn(0, slots.lastIndex); val f = p - k
            val a = slots[k]; val b = slots.getOrElse(k + 1) { a + 1f }
            return a + (b - a) * f
        }
        // The line, a tick per year, a label every 5 years; generation bands above (home), "Handheld" lane below.
        Canvas(Modifier.fillMaxSize()) {
            val y = axisY.toPx(); val cx = size.width / 2f; val yw = yearW.toPx()
            val origin = cx - slotX(pos.value) * yw
            drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            // (Ticks only over the years — the undated lists after them stand past a break in the line.)
            val breakX = origin + (lastYear - firstYear + 1.6f) * yw
            drawRect(Color(0xFF14181E), Offset(breakX - 4.dp.toPx(), y - 3.dp.toPx()), Size(8.dp.toPx(), 6.dp.toPx()))
            for (yr in firstYear - 2..lastYear + 1) {
                val x = origin + (yr - firstYear) * yw
                if (x < -40f || x > size.width + 40f) continue
                drawLine(Color.White.copy(alpha = if (yr % 5 == 0) 0.5f else 0.2f), Offset(x, y - (if (yr % 5 == 0) 8 else 4).dp.toPx()), Offset(x, y), 1.dp.toPx())
            }
            // Generation bands (home consoles), from their first to last system on screen.
            GENERATION.values.toSet().forEach { g ->
                val xs = order.indices.filter { generationOf(tiles[order[it]]) == g }.map { origin + slots[it] * yw }
                if (xs.isNotEmpty()) {
                    val x0 = xs.min() - yw * 0.45f; val x1 = xs.max() + yw * 0.45f
                    drawRoundRect(if (g % 2 == 0) accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f), Offset(x0, y - 30.dp.toPx()), Size(x1 - x0, 18.dp.toPx()), CornerRadius(9.dp.toPx()))
                }
            }
            // Stems from each system to the line.
            order.indices.forEach { k ->
                val x = origin + slots[k] * yw
                if (x < -40f || x > size.width + 40f) return@forEach
                val hand = handheldOf(tiles[order[k]])
                drawLine(Color.White.copy(alpha = 0.25f), Offset(x, y), Offset(x, if (hand) y + 26.dp.toPx() else y - 34.dp.toPx()), 1.dp.toPx())
                drawCircle(if (k == at) accent else Color.White.copy(alpha = 0.75f), if (k == at) 6.dp.toPx() else 3.5.dp.toPx(), Offset(x, y))
            }
        }
        // Year labels every 5 years, under the line.
        for (yr in (firstYear / 5) * 5..lastYear step 5) if (yr >= firstYear - 2) Text(
            yr.toString(), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart).graphicsLayer {
                translationX = w.toPx() / 2f + ((yr - firstYear) - slotX(pos.value)) * yearW.toPx() - 14.dp.toPx()
                translationY = axisY.toPx() + 8.dp.toPx()
            }
        )
        // Generation names on their bands.
        GENERATION.values.toSet().forEach { g ->
            val ks = order.indices.filter { generationOf(tiles[order[it]]) == g }
            if (ks.isNotEmpty()) Text(
                "${ordinal(g)} generation".uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.5.sp, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).graphicsLayer {
                    translationX = w.toPx() / 2f + (slots[ks.first()] - slotX(pos.value) - 0.35f) * yearW.toPx()
                    translationY = axisY.toPx() - 27.dp.toPx()
                }
            )
        }
        // The systems: home consoles above the line, handhelds below.
        val lo = (at - 5).coerceAtLeast(0); val hi = (at + 5).coerceAtMost(order.lastIndex)
        for (k in lo..hi) {
            val i = order[k]; val t = tiles[i]
            androidx.compose.runtime.key(t.key) {
                val on = k == at
                val hand = handheldOf(t)
                val objW = yearW * 1.05f; val objH = objW / (if (hand) 1.8f else 1.5f)
                Column(
                    Modifier.width(objW).align(Alignment.TopStart)
                        .graphicsLayer {
                            translationX = w.toPx() / 2f + (slots[k] - slotX(pos.value)) * yearW.toPx() - objW.toPx() / 2f
                            translationY = if (hand) axisY.toPx() + 24.dp.toPx() else axisY.toPx() - 38.dp.toPx() - objH.toPx() - 16.dp.toPx()
                            val s = if (on) 1.15f else 0.9f
                            scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, if (hand) 0f else 1f)
                        }
                        .zIndex(if (on) 5f else 0f)
                        .lookTap(i, selected, onFocus, onPick),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hand) Text(yearOf(t)?.toString() ?: "", color = if (on) accent else Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LookObject(t, Modifier.fillMaxWidth().height(objH), stand = !hand)
                    if (!hand) Text(yearOf(t)?.toString() ?: "—", color = if (on) accent else Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Lane names at the left.
        @Composable fun lane(text: String, y: Dp) = Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, letterSpacing = 3.sp,
            modifier = Modifier.offset(0.dp, y).clip(RoundedCornerShape(4.dp)).background(Color(0xFF101318)).padding(horizontal = 6.dp, vertical = 2.dp))
        // (The lanes named down the left edge, clear of the systems and the generation names.)
        Text("HOME", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 3.sp, softWrap = false,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-14).dp, y = axisY - 60.dp).graphicsLayer { rotationZ = -90f })
        Text("HANDHELD", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 3.sp, softWrap = false,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-26).dp, y = axisY + 46.dp).graphicsLayer { rotationZ = -90f })
        // The system you're on, at the top: its generation (or lane), name and facts.
        tiles.getOrNull(selected)?.let { t ->
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp).width(w * 0.6f), horizontalAlignment = Alignment.CenterHorizontally) {
                val g = generationOf(t)
                Text(
                    when { g != null -> "${ordinal(g)} generation · home"; handheldOf(t) -> "Handheld"; else -> "Other systems & lists" }.uppercase(),
                    color = accent, fontSize = 11.sp, letterSpacing = 2.sp
                )
                LookTitle(t, 24, align = TextAlign.Center)
                Text(listOfNotNull(lookFacts(t), lookCount(t)).joinToString("  ·  "), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 8. Constellation — an orbital diagram: systems spread round a core; the one you're on comes forward
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ConstellationLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val n = tiles.size.coerceAtLeast(1)
    // The ring turns so the one you're on is at the front; it is taken out of the ring into the light at the right.
    val pos = rememberLookGlide(selected, animate, Spring.StiffnessLow)
    val come = rememberLookStep(selected, animate, 420)
    val accent = ShelfTheme.accent
    val lite = LocalLiteEffects.current
    val pulse = if (lite || !animate) null else rememberInfiniteTransition(label = "core").animateFloat(0.9f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "pulse")
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF111528), Color(0xFF04050B)), Offset(500f, 500f), 1400f))) {
        val w = maxWidth; val h = maxHeight
        val cxF = 0.29f; val cyF = 0.52f
        val rxF = 0.21f; val ryF = 0.26f
        // Shown on the ring: up to 18, every one evenly spaced (with more, the ones nearest the one you're on).
        val ringN = minOf(n, 18)
        fun angle(i: Int, p: Float): Float = ((i - p) / ringN) * 2f * Math.PI.toFloat() + Math.PI.toFloat() / 2f
        val shown = if (n <= ringN) (0 until n).toList() else (selected - ringN / 2 until selected + ringN / 2).map { it.mod(n) }
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width * cxF; val cy = size.height * cyF
            val rx = size.width * rxF; val ry = size.height * ryF
            // Faint star field.
            val rnd = java.util.Random(7)
            repeat(70) { drawCircle(Color.White.copy(alpha = 0.05f + 0.2f * rnd.nextFloat()), 0.6.dp.toPx() + rnd.nextFloat() * 0.8.dp.toPx(), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)) }
            // Orbits.
            drawOval(Color.White.copy(alpha = 0.12f), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(1.dp.toPx()))
            drawOval(Color.White.copy(alpha = 0.05f), Offset(cx - rx * 0.6f, cy - ry * 0.6f), Size(rx * 1.2f, ry * 1.2f), style = Stroke(1.dp.toPx()))
            // The core.
            val glow = pulse?.value ?: 1f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f * glow), accent.copy(alpha = 0.06f), Color.Transparent), Offset(cx, cy), size.minDimension * 0.22f), size.minDimension * 0.22f, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.95f), 5.dp.toPx(), Offset(cx, cy))
            // A line from the core to the place on the ring the selected system left, and on to the focus point.
            val a = angle(selected, pos.value)
            val ringPt = Offset(cx + rx * cos(a), cy + ry * sin(a))
            drawLine(accent.copy(alpha = 0.5f), Offset(cx, cy), ringPt, 1.2.dp.toPx())
            drawLine(accent.copy(alpha = 0.35f * come.now()), ringPt, Offset(size.width * 0.72f, size.height * 0.46f), 1.dp.toPx(), cap = StrokeCap.Round)
        }
        // The ring's systems: small, evenly spread; nearer (lower) ones a little bigger and brighter.
        for (i in shown) androidx.compose.runtime.key(tiles[i].key) {
            val t = tiles[i]
            val nodeW = minOf(w * 0.075f, h * 0.12f); val nodeH = nodeW
            val on = i == selected
            // Drawing order by depth, in steps: read raw, every node re-composed on every frame of the turn.
            val z by remember(i, n) { androidx.compose.runtime.derivedStateOf { ((sin(angle(i, pos.value)) + 1f) * 10f).roundToInt().toFloat() } }
            Box(
                Modifier.size(nodeW, nodeH).align(Alignment.TopStart)
                    .graphicsLayer {
                        val a = angle(i, pos.value)
                        val depth = (sin(a) + 1f) / 2f
                        translationX = w.toPx() * (cxF + rxF * cos(a)) - nodeW.toPx() / 2f
                        translationY = h.toPx() * (cyF + ryF * sin(a)) - nodeH.toPx() / 2f
                        val k = 0.7f + 0.45f * depth
                        scaleX = k; scaleY = k
                        alpha = (if (on) 1f - come.now() * 0.75f else 1f) * (0.45f + 0.55f * depth)
                    }
                    .zIndex(z)
                    .lookTap(i, selected, onFocus, onPick),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.radialGradient(listOf(lookHue(t, 0.4f).copy(alpha = 0.35f), Color.Transparent))).border(1.dp, if (on) accent else Color.White.copy(alpha = 0.18f), CircleShape))
                LookObject(t, Modifier.fillMaxSize().padding(8.dp))
            }
        }
        // The one you're on, brought out to the focus point at the right, with its information.
        tiles.getOrNull(selected)?.let { t ->
            Box(
                Modifier.align(Alignment.TopStart).size(w * 0.30f, h * 0.36f)
                    .graphicsLayer {
                        val k = 0.6f + 0.4f * come.now()
                        scaleX = k; scaleY = k; alpha = come.now()
                        translationX = w.toPx() * 0.72f - (w * 0.15f).toPx()
                        translationY = h.toPx() * 0.42f - (h * 0.18f).toPx()
                    }
            ) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(lookHue(t, 0.5f).copy(alpha = 0.35f), Color.Transparent))))
                LookObject(t, Modifier.fillMaxSize().padding(10.dp))
            }
            Column(Modifier.align(Alignment.TopStart).offset(w * 0.58f, h * 0.64f).width(w * 0.36f).graphicsLayer { alpha = come.now() }) {
                LookTitle(t, 24)
                Spacer(Modifier.height(4.dp))
                Text(listOfNotNull(lookCount(t), lookFacts(t)).joinToString("  ·  "), color = accent, fontSize = 13.sp)
                if (t.favorites > 0) Text("${t.favorites} favourites", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 9. System Vault — modules in a recessed rack; the one you're on is ejected and carried into the reader
// ---------------------------------------------------------------------------------------------

@Composable
internal fun VaultLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val pos = rememberLookGlide(selected, animate)
    val accent = ShelfTheme.accent
    // 0 → 0.3: pushed out of its slot; 0.3 → 0.8: carried across; 0.8 → 1: pushed home in the reader (a small bump).
    val move = rememberLookStep(selected, animate, 620, delayMs = 300, easing = LinearEasing)
    // What the reader's screen shows: the module that is in it — the old one (dimmed) until the new one is pushed home.
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(selected) }
    LaunchedEffect(selected) { if (animate) kotlinx.coroutines.delay(300L + 500L); shown = selected }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF15171B), Color(0xFF23272E))))) {
        val w = maxWidth; val h = maxHeight
        val top = 78.dp; val bottom = 56.dp
        val slots = 5
        val slotH = (h - top - bottom) / slots
        val modH = slotH - 14.dp
        val rackX = 48.dp; val rackW = w * 0.36f
        val readerX = rackX + rackW + w * 0.08f; val readerW = w - readerX - 48.dp
        // The rack: a recessed panel, one dark slot per row; the middle row is the one you're on.
        Box(Modifier.offset(rackX - 10.dp, top - 8.dp).size(rackW + 20.dp, slotH * slots + 16.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0D0E11)).drawBehind {
            for (s in 0 until slots) {
                val y = 8.dp.toPx() + s * slotH.toPx() + 4.dp.toPx()
                drawRoundRect(Color(0xFF050506), Offset(6.dp.toPx(), y), Size(size.width - 12.dp.toPx(), slotH.toPx() - 8.dp.toPx()), CornerRadius(5.dp.toPx()))
                drawRect(Color.White.copy(alpha = 0.04f), Offset(6.dp.toPx(), y + slotH.toPx() - 9.dp.toPx()), Size(size.width - 12.dp.toPx(), 1.dp.toPx()))
            }
        })
        // The reader: a unit with a slot on its left and a screen.
        Box(Modifier.offset(readerX, top - 8.dp).size(readerW, h - top - bottom + 16.dp).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF30343B), Color(0xFF1C1F24)))).border(1.dp, Color(0xFF3E434B), RoundedCornerShape(16.dp))) {
            // Its slot (the module goes in here) with a light that turns on once it's home.
            Box(Modifier.padding(14.dp).fillMaxWidth().height(modH + 10.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF060708)))
            val home = move.now() >= 0.98f
            Box(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 22.dp).size(8.dp).clip(CircleShape).background(if (home) Color(0xFF59E37A) else Color(0xFF3A3F45)))
            // Its screen: the collection of the module inside.
            val t = tiles.getOrNull(shown)
            val current = shown == selected
            if (t != null) Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = modH + 38.dp, bottom = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0C0E)).padding(12.dp)
                .graphicsLayer { val m = move.now(); alpha = if (current) ((m - 0.8f) / 0.2f).coerceIn(0.3f, 1f) else 0.3f }) {
                FullText("READING  ·  ${t.label.uppercase()}", color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.coverSlides.take(5).forEach { u -> AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp))) }
                    if (t.coverSlides.isEmpty()) LookObject(t, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(8.dp))
                Text(listOfNotNull(lookCount(t), if (t.favorites > 0) "${t.favorites} fav" else null, lookFacts(t)).joinToString("  ·  "), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
        // The modules. The middle slot holds the one you're on until it's carried away (its slot then stays empty).
        val win = lookWindow(selected, pos, 2, tiles.lastIndex)
        for (i in win) androidx.compose.runtime.key(tiles[i].key) {
            val t = tiles[i]
            val on = i == selected
            // Its own trip in and out of the reader: the one you leave goes back to its slot while the new one goes in.
            // (The one leaving goes back quickly first; the new one starts once the reader is free — they crossed mid-air.)
            val mvS = androidx.compose.animation.core.animateFloatAsState(
                if (on) 1f else 0f,
                if (!animate) androidx.compose.animation.core.snap() else if (on) tween(620, delayMillis = 320, easing = LinearEasing) else tween(380, easing = FastOutSlowInEasing),
                label = "vaultMove"
            )
            // (Read in the layer only; in composition the moving modules were rebuilt every frame.)
            val away by remember(mvS) { androidx.compose.runtime.derivedStateOf { mvS.value > 0.001f } }
            Box(
                Modifier.size(rackW - 16.dp, modH).align(Alignment.TopStart)
                    .graphicsLayer {
                        val r = i - pos.value
                        val slotX = (rackX + 8.dp).toPx()
                        val slotY = top.toPx() + (slots / 2 + r) * slotH.toPx() + 7.dp.toPx()
                        val inX = (readerX + 18.dp).toPx(); val inY = (top + 3.dp).toPx()
                        val mv = mvS.value
                        if (mv > 0.001f) {
                            val m = mv
                            val out = 30.dp.toPx()
                            when {
                                m < 0.3f -> { translationX = slotX + out * (m / 0.3f); translationY = slotY }
                                m < 0.8f -> {
                                    val f = FastOutSlowInEasing.transform((m - 0.3f) / 0.5f)
                                    translationX = slotX + out + (inX - 20.dp.toPx() - slotX - out) * f
                                    translationY = slotY + (inY - slotY) * f
                                }
                                else -> {
                                    val f = (m - 0.8f) / 0.2f
                                    translationX = inX - 20.dp.toPx() + 20.dp.toPx() * f + (if (f > 0.85f) -2.dp.toPx() * (1f - f) / 0.15f else 0f)
                                    translationY = inY
                                }
                            }
                            shadowElevation = (if (mv in 0.3f..0.8f) 16f else 4f).dp.toPx()
                        } else {
                            translationX = slotX; translationY = slotY
                            alpha = (1f - 0.18f * abs(r)).coerceIn(0f, 1f) * (if (abs(r) > 2.4f) 0f else 1f)
                        }
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp); clip = true
                    }
                    .zIndex(if (on || away) 10f else 0f)
                    .background(Brush.verticalGradient(listOf(Color(0xFF5A5F68), Color(0xFF3A3E45))))
                    .border(1.dp, if (on) accent else Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp))
                    .lookTap(i, selected, onFocus, onPick)
            ) {
                Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The label: a sticker with the colour band, the console and the name.
                    Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color(0xFFE9E6DE)), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(lookHue(t, 0.48f, 0.6f)))
                        LookObject(t, Modifier.fillMaxHeight().width(modH * 1.25f).padding(4.dp))
                        FullText(t.label, color = Color(0xFF22252A), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 6.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    // Grip ridges and the gold contacts at its far end.
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { repeat(5) { Box(Modifier.size(10.dp, 2.dp).background(Color(0xFFD1B266))) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 10. Pocket Dashboard — a big preview of the system you're on over a row of round system icons
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DashboardLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val pos = rememberLookGlide(selected, animate, Spring.StiffnessMedium)
    val accent = ShelfTheme.accent
    val titles = LocalTitleOptions.current
    // Two screens (the Thor): the preview fills the top screen and the row of systems goes to the lower one, as a grid
    // you can also touch (LookSecondScreen → LauncherScreen's second-screen content).
    val dual = SecondScreenReal.active
    val tilesNow by rememberUpdatedState(tiles); val selNow by rememberUpdatedState(selected)
    val focusNow by rememberUpdatedState(onFocus); val pickNow by rememberUpdatedState(onPick)
    androidx.compose.runtime.DisposableEffect(dual) {
        if (dual) LookSecondScreen.content = { DashboardLower(tilesNow, selNow, { focusNow(it) }, { pickNow(it) }) }
        onDispose { if (dual) LookSecondScreen.content = null }
    }
    BoxWithConstraints(Modifier.fillMaxSize().lookDarkGround()) {
        val w = maxWidth; val h = maxHeight
        val t = tiles.getOrNull(selected)
        val previewH = if (dual) h - 72.dp - 64.dp else h * 0.46f
        // The preview: the system's art, its hardware, and what it holds, in one panel.
        if (t != null) Box(
            Modifier.padding(start = 40.dp, end = 40.dp, top = 72.dp).fillMaxWidth().height(previewH)
                .clip(RoundedCornerShape(22.dp)).background(Color.Black)
        ) {
            CategoryArt(t, CardShape.WIDE, Modifier.fillMaxSize().graphicsLayer { alpha = 0.7f })
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.55f to Color.Black.copy(alpha = 0.35f), 1f to Color.Transparent)))
            Row(Modifier.fillMaxSize().padding(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lookFacts(t)?.uppercase() ?: if (t.platform == null) "LIST" else "", color = accent, fontSize = 11.sp, letterSpacing = 2.sp)
                    FullText(t.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        @Composable fun stat(v: String, k: String) = Column {
                            Text(v, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(k, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                        stat(t.count.toString(), "games")
                        stat(t.favorites.toString(), "favourites")
                    }
                    t.lastPlayedTitle?.let {
                        Spacer(Modifier.height(10.dp))
                        FullText("Last played  ·  " + titles.display(it), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                }
                LookObject(t, Modifier.fillMaxHeight().weight(0.9f))
            }
        }
        // The row of systems: round icons, the one you're on lifted with a ring and its name under it.
        // (On the theme's background, kept dark — lookDarkBackground.)
        val ink = Color.White
        val iconD = 64.dp; val gap = 18.dp
        val rowY = 72.dp + h * 0.46f + 18.dp
        val win = lookWindow(selected, pos, 6, tiles.lastIndex)
        val lo = win.first; val hi = win.last
        if (!dual) for (i in lo..hi) androidx.compose.runtime.key(tiles[i].key) {
            val tile = tiles[i]
            val on = i == selected
            Column(
                Modifier.width(iconD + gap).align(Alignment.TopStart)
                    .graphicsLayer {
                        translationX = (w.toPx() - (iconD + gap).toPx()) / 2f + (i - pos.value) * (iconD + gap).toPx()
                        translationY = rowY.toPx() - (if (on) 4.dp.toPx() else 0f)
                    }
                    .lookTap(i, selected, onFocus, onPick),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(iconD).graphicsLayer { val k = if (on) 1.12f else 1f; scaleX = k; scaleY = k }.clip(CircleShape).background(ink.copy(alpha = if (on) 0.16f else 0.07f))
                        .border(if (on) 3.dp else 1.dp, if (on) accent else ink.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { LookObject(tile, Modifier.fillMaxSize().padding(12.dp)) }
                // (Its name is in the preview above; under the row only the ring marks it — the legend stays clear.)
            }
        }
    }
}


/**
 * Pass 5: Pocket Dashboard's ground — the theme's background, darkened when the theme is a light one (Lobby): the
 * category screen's own title, buttons and legend are drawn light on every look (QA: on Lobby they were white on white).
 */
// (Read while drawing: the theme's colours glide after a change and would re-compose the whole look every frame.)
@Composable
private fun Modifier.lookDarkGround(): Modifier {
    val live = ShelfTheme.live
    return drawBehind { drawLookGround(live.value.background) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLookGround(bg: Color) {
    drawRect(if (bg.luminance() > 0.45f) androidx.compose.ui.graphics.lerp(bg, Color(0xFF101216), 0.88f) else bg)
}

/** What a category look puts on the second screen while it's open (null: the usual second-screen page). */
internal object LookSecondScreen {
    var content by androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)
}

/**
 * Pocket Dashboard's lower screen (two-screen handhelds): every system as a round icon in a grid, the one you're on
 * ringed, its name under it; tap one to go to it, tap it again to open it. The D-pad still drives the top screen.
 */
@Composable
private fun DashboardLower(tiles: List<CategoryTile>, selected: Int, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val accent = ShelfTheme.accent
    val ink = Color.White
    BoxWithConstraints(Modifier.fillMaxSize().lookDarkGround()) {
        val cell = 92.dp
        val cols = (maxWidth / cell).toInt().coerceIn(3, 8)
        val rowH = 104.dp
        val rows = (tiles.size + cols - 1) / cols
        val visibleRows = ((maxHeight - 56.dp) / rowH).toInt().coerceAtLeast(1)
        // Scrolls by whole rows only when the one you're on leaves the view (QA: scrolling to centre it on every move put
        // another system under your finger — the second tap, meant to open the first, picked that one).
        val selRow = selected / cols
        val firstHeld = remember { intArrayOf(-1) }
        val maxFirst = (rows - visibleRows).coerceAtLeast(0)
        val firstRow = run {
            var f = firstHeld[0]
            if (f < 0) f = selRow - visibleRows / 2
            if (selRow < f) f = selRow
            if (selRow >= f + visibleRows) f = selRow - visibleRows + 1
            f.coerceIn(0, maxFirst).also { firstHeld[0] = it }
        }
        val scroll = androidx.compose.animation.core.animateFloatAsState(firstRow.toFloat(), tween(260, easing = FastOutSlowInEasing), label = "dashLower")
        val gridW = cell * cols
        val left = (maxWidth - gridW) / 2
        Text("Systems", color = ink.copy(alpha = 0.55f), fontSize = 12.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 16.dp))
        Text("${selected + 1} / ${tiles.size}", color = ink.copy(alpha = 0.55f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 16.dp))
        Box(Modifier.fillMaxSize().padding(top = 44.dp).clipToBounds()) {
            for (i in tiles.indices) {
                val r = i / cols; val c = i % cols
                if (r < firstRow - 2 || r > firstRow + visibleRows + 2) continue
                androidx.compose.runtime.key(tiles[i].key) {
                    val tile = tiles[i]
                    val on = i == selected
                    Column(
                        Modifier.width(cell).align(Alignment.TopStart)
                            .graphicsLayer {
                                translationX = (left + cell * c).toPx()
                                translationY = ((r - scroll.value) * rowH.toPx())
                            }
                            .lookTap(i, selected, onFocus, onPick),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(68.dp).graphicsLayer { val k = if (on) 1.08f else 1f; scaleX = k; scaleY = k }.clip(CircleShape)
                                .background(ink.copy(alpha = if (on) 0.16f else 0.07f))
                                .border(if (on) 3.dp else 1.dp, if (on) accent else ink.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { LookObject(tile, Modifier.fillMaxSize().padding(13.dp)) }
                        Spacer(Modifier.height(5.dp))
                        FullText(tile.label, color = if (on) ink else ink.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 2,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}
