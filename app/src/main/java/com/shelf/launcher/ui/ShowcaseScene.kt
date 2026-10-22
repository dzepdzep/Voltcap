package com.shelf.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shelf.launcher.ui.theme.ShelfColors
import com.shelf.launcher.ui.theme.ThemeMotif
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** The art a game showcase theme shows: the focused game's hero (key art), cover and screenshots. */
internal data class ShowcaseArt(val key: String, val hero: String?, val cover: String?, val shots: List<String>)

/** Set by the home screen for the game you're on; read by the showcase scenes (and the theme picker's preview). */
internal object Showcase {
    var art by mutableStateOf<ShowcaseArt?>(null)
    /** Gaming room: the game's gameplay clip for the monitor (set once you've rested on a game, with Video previews on). */
    var video by mutableStateOf<String?>(null)
    var videoSound by mutableStateOf(false)
}

/**
 * Game showcase themes (Appearance › Theme › Game showcase): the focused game's hero / key art fills
 * the background and its screenshots move round it - floating cards in depth, cinematic cross-fades
 * with slow zooms, a sliding film strip, or a wall of screenshots drifting in perspective.
 *
 * Cheap on purpose: every picture is decoded once at about the size it's shown, and all the motion
 * is graphics-layer transforms driven by one clock read in the draw phase (nothing recomposes per
 * frame). The clock follows the background quality, slows down when idle and stops while covered,
 * exactly like the shader scenes. Until the game's art has loaded (or a game has none), the
 * showcase backdrop shader shows in the game's colours.
 */
@Composable
internal fun ShowcaseScene(motif: ThemeMotif, colors: ShelfColors, modifier: Modifier = Modifier) {
    val art = Showcase.art
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    val active = rememberIsActive() && rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current
    val time = remember { mutableFloatStateOf(STILL_FRAME_TIME) }
    val tuning = LocalSceneTuning.current
    SceneClock(active, time, idleFrameMs(), tuning.speed)
    // CRT room (Android 13+): the whole room is one 3D-rendered shader with the game's picture on the TV.
    if (motif == ThemeMotif.SHOWCASE_CRT && android.os.Build.VERSION.SDK_INT >= 33) {
        val crt = remember { SceneShader.create(ThemeMotif.SHOWCASE_CRT) }
        if (crt != null) {
            // With the game row at the top the room slides down (and by the theme's own Scene position).
            val avoid = LocalSceneAvoid.current
            var fullH by remember { mutableFloatStateOf(0f) }
            val shift = androidx.compose.animation.core.animateFloatAsState(SceneArt.shiftFocus(avoid, fullH, tuning.shift), tween(700), label = "crtShift")
            CrtRoom3D(crt, art, time, colors, modifier.onSizeChanged { fullH = it.height.toFloat() }, shift = shift)
            return
        }
    }
    // Gaming room (Android 13+): the painted room with the game on its monitor (the same channels as the CRT
    // room, or its gameplay clip) and its cover on the poster above it.
    if (motif == ThemeMotif.GAMING_ROOM && android.os.Build.VERSION.SDK_INT >= 33) {
        val scene = remember { SceneShader.create(ThemeMotif.GAMING_ROOM) }
        val context = LocalContext.current
        val room by androidx.compose.runtime.produceState(RoomPicture.cached()) { if (value == null) value = RoomPicture.load(context) }
        // The monitor goes in the middle of the biggest open space: above the game row when it's at the
        // bottom, below it when it's at the top (eased when the row moves).
        val avoid = LocalSceneAvoid.current
        var fullW by remember { mutableFloatStateOf(0f) }
        var fullH by remember { mutableFloatStateOf(0f) }
        val focus = androidx.compose.animation.core.animateFloatAsState(openMiddle(avoid, fullH), tween(700), label = "roomFocus")
        val posterUrl = art?.cover
        val poster = androidx.compose.runtime.produceState<CrtPicture?>(null, posterUrl) {
            value = posterUrl?.let { loadCrtPicture(context, it, 320, 480) }
        }
        if (scene != null && room != null) {
            Box(modifier.onSizeChanged { fullW = it.width.toFloat(); fullH = it.height.toFloat() }) {
                CrtRoom3D(scene, art, time, colors, Modifier.fillMaxSize(), room, focus, poster)
                // The gameplay clip plays on the monitor itself (a video view laid exactly over its screen).
                val clip = Showcase.video?.takeIf { !LocalSceneCovered.current && !liteStill }
                // (Update 307: one clip fading into the next on the monitor — VideoCrossfade.)
                if (fullW > 0f) {
                    val r = roomMonitorRect(fullW, fullH, focus.value)
                    val d = LocalDensity.current
                    VideoCrossfade(
                        clip, Showcase.videoSound, Modifier.fillMaxSize(),
                        clipModifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()) }
                            .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
                            .clip(RoundedCornerShape(with(d) { (r.width / 110f).toDp() }))
                    )
                }
            }
            return
        }
        if (scene != null) {
            // Only while the painting decodes (a moment, once): the room's own dark.
            Box(modifier.background(Color(0xFF0B0A12)))
            return
        }
    }
    Box(modifier) {
        // The backdrop in the game's colours: shows while art loads and through the soft edges.
        CodeScene(motif, colors, Modifier.fillMaxSize())
        if (art != null && (art.hero != null || art.cover != null || art.shots.isNotEmpty())) {
            // A new game cross-fades in over the last one.
            Crossfade(targetState = art, animationSpec = tween(700), label = "showcase") { a ->
                when (motif) {
                    ThemeMotif.SHOWCASE_CINEMA -> Cinema(a, time, colors)
                    ThemeMotif.SHOWCASE_FILMSTRIP -> Filmstrip(a, time, colors)
                    ThemeMotif.SHOWCASE_MOSAIC -> Mosaic(a, time, colors)
                    ThemeMotif.SHOWCASE_GLASS -> GlassPanes(a, time, colors)
                    ThemeMotif.SHOWCASE_CRT -> CrtRoom(a, time, colors)
                    else -> Parallax(a, time, colors)
                }
            }
        }
        // The same shade the other scenes have: darker where the row and its text sit.
        Box(Modifier.fillMaxSize().drawBehind { drawSceneShade() })
        if (tuning.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = tuning.dim)))
    }
}

/** Decode size for a picture shown at [w] × [h] dp: the shown size (capped), never the file's full size. */
@Composable
private fun px(w: Dp, h: Dp): IntSize {
    val d = LocalDensity.current.density
    val cap = if (com.shelf.launcher.device.MemoryBudget.small(LocalContext.current)) 1280 else 1920
    val sw = (w.value * d).toInt().coerceIn(64, cap)
    val sh = (h.value * d).toInt().coerceIn(64, cap)
    return IntSize(sw, sh)
}

@Composable
private fun ShowcaseImage(url: String, w: Dp, h: Dp, modifier: Modifier, crop: Boolean = true) {
    val context = LocalContext.current
    val size = px(w, h)
    val request = remember(url, size) {
        coil.request.ImageRequest.Builder(context)
            .data(url)
            .size(size.width, size.height)
            .scale(coil.size.Scale.FILL)
            .precision(coil.size.Precision.INEXACT)
            .memoryCacheKey("showcase|$url|${size.width}x${size.height}")
            .allowHardware(true)
            .crossfade(400)
            .build()
    }
    AsyncImage(
        model = request, contentDescription = null,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier
    )
}

/** Picture [i] of the slideshow: screenshots in turn, the hero / cover when there are none. */
private fun ShowcaseArt.pick(i: Int): String? {
    if (shots.isNotEmpty()) return shots[((i % shots.size) + shots.size) % shots.size]
    return hero ?: cover
}

private fun ShowcaseArt.backdrop(): String? = hero ?: shots.firstOrNull() ?: cover

/**
 * Hero parallax: the key art fills the screen, zoomed in a little and drifting slowly; three
 * screenshot cards float in front of it at different depths (the nearer, the more they move),
 * each taking a new screenshot every few seconds with a fade.
 */
@Composable
private fun Parallax(a: ShowcaseArt, time: State<Float>, colors: ShelfColors) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        a.backdrop()?.let { url ->
            ShowcaseImage(url, w, h, Modifier.fillMaxSize().graphicsLayer {
                val t = time.value
                scaleX = 1.12f + 0.03f * sin(t * 0.05f)
                scaleY = scaleX
                translationX = size.width * 0.025f * sin(t * 0.043f)
                translationY = size.height * 0.015f * cos(t * 0.037f)
                alpha = 0.92f
            })
        }
        if (a.shots.isEmpty()) return@BoxWithConstraints
        // Cards: position (share of the screen), depth (how far they move), size.
        val cards = listOf(
            Triple(0.05f to 0.10f, 0.6f, 0.26f),
            Triple(0.68f to 0.07f, 1.0f, 0.24f),
            Triple(0.38f to 0.26f, 0.35f, 0.20f)
        )
        val cardShots = if (LocalSceneQuality.current.scale < 0.5f) 2 else 3
        cards.take(cardShots).forEachIndexed { i, (pos, depth, share) ->
            val cw = w * share
            val ch = cw * 9f / 16f
            ShowcaseCard(a, i, time, cw, ch, colors, Modifier
                .offset(x = w * pos.first, y = h * pos.second)
                .graphicsLayer {
                    val t = time.value
                    translationX = size.width * 0.12f * depth * sin(t * 0.07f + i * 2.1f)
                    translationY = size.height * 0.10f * depth * cos(t * 0.06f + i * 1.3f)
                    rotationZ = 2.5f * sin(t * 0.05f + i)
                    rotationY = 6f * depth * sin(t * 0.04f + i * 0.7f)
                    flatTextFor3d()
                    cameraDistance = 14f * density
                })
        }
    }
}

/** One floating screenshot card, cross-fading to the next screenshot every [period] seconds. */
@Composable
private fun ShowcaseCard(a: ShowcaseArt, slot: Int, time: State<Float>, w: Dp, h: Dp, colors: ShelfColors, modifier: Modifier, period: Float = 9f) {
    // The picture changes only every few seconds: derived, so the card recomposes then and not per frame.
    val index by remember(a, slot) {
        androidx.compose.runtime.derivedStateOf { floor(time.value / period + slot * 0.37f).toInt() * 3 + slot }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(w, h)
            .clip(shape)
            .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), colors.accent.copy(alpha = 0.6f))), shape)
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        Crossfade(targetState = a.pick(index), animationSpec = tween(900), label = "card") { url ->
            if (url != null) ShowcaseImage(url, w, h, Modifier.fillMaxSize())
        }
    }
}

/**
 * Cinematic: the key art, then each screenshot in turn, full screen with a slow zoom and pan (a
 * different direction for each) and long cross-fades, between thin letterbox bars.
 */
@Composable
private fun Cinema(a: ShowcaseArt, time: State<Float>, colors: ShelfColors) {
    val period = 8f
    val seq = remember(a) { listOfNotNull(a.hero ?: a.cover) + a.shots }
    val index by remember(a) { androidx.compose.runtime.derivedStateOf { floor(time.value / period).toInt() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        if (seq.isNotEmpty()) {
            Crossfade(targetState = index, animationSpec = tween(1600), label = "cinema") { i ->
                val url = seq[((i % seq.size) + seq.size) % seq.size]
                // Each shot zooms from its own corner.
                val dx = if (i % 2 == 0) 1f else -1f
                val dy = if ((i / 2) % 2 == 0) 1f else -1f
                ShowcaseImage(url, w, h, Modifier.fillMaxSize().graphicsLayer {
                    val p = ((time.value / period) - i).coerceIn(-0.2f, 1.3f)
                    scaleX = 1.06f + 0.12f * p
                    scaleY = scaleX
                    translationX = size.width * 0.03f * dx * p
                    translationY = size.height * 0.02f * dy * p
                })
            }
        }
        // Letterbox bars.
        Canvas(Modifier.fillMaxSize()) {
            val bar = size.height * 0.055f
            drawRect(Color.Black.copy(alpha = 0.85f), size = Size(size.width, bar))
            drawRect(Color.Black.copy(alpha = 0.85f), topLeft = Offset(0f, size.height - bar), size = Size(size.width, bar))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.accent.copy(alpha = 0.10f)), startY = 0f, endY = size.height))
        }
    }
}

/**
 * Filmstrip: the key art behind, dimmed; a tilted strip of film with sprocket holes slides slowly
 * across the middle of the screen carrying the screenshots.
 */
@Composable
private fun Filmstrip(a: ShowcaseArt, time: State<Float>, colors: ShelfColors) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        a.backdrop()?.let { url ->
            ShowcaseImage(url, w, h, Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.08f + 0.02f * sin(time.value * 0.05f); scaleY = scaleX; alpha = 0.75f
            })
        }
        val frames = if (a.shots.isNotEmpty()) a.shots else listOfNotNull(a.cover, a.hero)
        if (frames.isEmpty()) return@BoxWithConstraints
        val fw = w * 0.30f
        val fh = fw * 9f / 16f
        val gap = 14.dp
        val step = fw + gap
        val count = (w / step).toInt() + 3
        val stripH = fh + 34.dp
        Box(
            Modifier
                .offset(x = -step, y = (h - stripH) / 2)
                .size(w + step * 2, stripH)
                .graphicsLayer { rotationZ = -6f }
        ) {
            // The film itself.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xE6101012))
                val hole = 7.dp.toPx()
                val pitch = 18.dp.toPx()
                val shift = (time.value * 22.dp.toPx()) % pitch
                var x = -shift
                while (x < size.width) {
                    drawRoundRect(Color(0x66FFFFFF), Offset(x, 5.dp.toPx()), Size(hole, hole * 0.8f), CornerRadius(2f))
                    drawRoundRect(Color(0x66FFFFFF), Offset(x, size.height - 5.dp.toPx() - hole * 0.8f), Size(hole, hole * 0.8f), CornerRadius(2f))
                    x += pitch
                }
            }
            // Frames: placed in a row that slides left; each slot shows a screenshot by its index.
            val density = LocalDensity.current
            val stepPx = with(density) { step.toPx() }
            val speedPx = with(density) { 22.dp.toPx() } // per second
            val first by remember(a, stepPx, speedPx) { androidx.compose.runtime.derivedStateOf { floor(time.value * speedPx / stepPx).toInt() } }
            for (j in 0 until count) {
                val idx = first + j
                Box(
                    Modifier
                        .offset(x = step * j, y = 17.dp)
                        .size(fw, fh)
                        .graphicsLayer {
                            translationX = -((time.value * speedPx) % stepPx)
                        }
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    ShowcaseImage(frames[((idx % frames.size) + frames.size) % frames.size], fw, fh, Modifier.fillMaxSize())
                }
            }
        }
        // A glint of the accent colour along the strip.
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.08f), Color.Transparent), startY = 0f, endY = size.height * 0.6f))
        })
    }
}

/**
 * Screenshot wall: a grid of screenshots seen at an angle, drifting slowly sideways; the key art
 * fades in and out through it.
 */
@Composable
private fun Mosaic(a: ShowcaseArt, time: State<Float>, colors: ShelfColors) {
    val pics = remember(a) { (a.shots + listOfNotNull(a.cover, a.hero)).ifEmpty { listOfNotNull(a.hero) } }
    val low = LocalSceneQuality.current.scale < 0.5f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val cols = if (low) 3 else 4
        val rows = if (low) 2 else 3
        val tw = w * 0.36f
        val th = tw * 9f / 16f
        val gap = 10.dp
        Box(
            Modifier
                .offset(x = -w * 0.15f, y = -h * 0.08f)
                .size(w * 1.4f, h * 1.2f)
                .graphicsLayer {
                    val t = time.value
                    rotationY = -14f
                    rotationX = 8f
                    flatTextFor3d()
                    rotationZ = -4f
                    cameraDistance = 10f * density
                    translationX = size.width * 0.06f * sin(t * 0.03f)
                    translationY = size.height * 0.03f * cos(t * 0.025f)
                }
        ) {
            if (pics.isNotEmpty()) for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                Box(
                    Modifier
                        .offset(x = (tw + gap) * c + (if (r % 2 == 1) tw * 0.5f else 0.dp), y = (th + gap) * r)
                        .size(tw, th)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    ShowcaseImage(pics[i % pics.size], tw, th, Modifier.fillMaxSize().graphicsLayer {
                        // Each tile breathes a little out of step with the others.
                        val s = 1.04f + 0.04f * sin(time.value * 0.3f + i * 1.7f)
                        scaleX = s; scaleY = s
                    })
                }
            }
        }
        // The key art fading through the wall.
        a.hero?.let { url ->
            ShowcaseImage(url, w, h, Modifier.fillMaxSize().graphicsLayer {
                val t = time.value
                alpha = 0.25f + 0.45f * (0.5f + 0.5f * sin(t * 0.12f))
                scaleX = 1.05f + 0.03f * sin(t * 0.05f); scaleY = scaleX
            })
        }
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.55f)), radius = size.maxDimension * 0.75f))
        })
    }
}


/**
 * Glass: the key art fills the screen, drifting slowly; three frosted glass panes float in front of
 * it at different depths. Each pane is the art behind it, frosted (a tiny decode stretched up: soft
 * for free, no blur pass), with a screenshot set into it, a bright bevel and a light sweeping across.
 */
@Composable
private fun GlassPanes(a: ShowcaseArt, time: State<Float>, colors: ShelfColors) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val back = a.backdrop()
        back?.let { url ->
            ShowcaseImage(url, w, h, Modifier.fillMaxSize().graphicsLayer {
                val t = time.value
                scaleX = 1.1f + 0.03f * sin(t * 0.04f)
                scaleY = scaleX
                translationX = size.width * 0.02f * sin(t * 0.037f)
                translationY = size.height * 0.012f * cos(t * 0.031f)
            })
        }
        // Pane: position (share of the screen), size (share of the width), depth.
        val panes = listOf(
            Triple(0.06f to 0.12f, 0.30f, 0.7f),
            Triple(0.62f to 0.08f, 0.32f, 1.0f),
            Triple(0.36f to 0.30f, 0.22f, 0.4f)
        )
        val count = if (LocalSceneQuality.current.scale < 0.5f) 2 else 3
        panes.take(count).forEachIndexed { i, (pos, share, depth) ->
            val pw = w * share
            val ph = pw * 0.68f
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .offset(x = w * pos.first, y = h * pos.second)
                    .size(pw, ph)
                    .graphicsLayer {
                        val t = time.value
                        translationX = size.width * 0.10f * depth * sin(t * 0.06f + i * 2.3f)
                        translationY = size.height * 0.08f * depth * cos(t * 0.05f + i * 1.1f)
                        rotationZ = 1.5f * sin(t * 0.045f + i)
                        rotationY = 5f * depth * sin(t * 0.035f + i * 0.9f)
                        flatTextFor3d()
                        cameraDistance = 14f * density
                        shadowElevation = 18f
                        this.shape = shape
                        clip = true
                    }
                    .clip(shape)
            ) {
                // Frost: the art behind, decoded tiny and stretched (soft), brightened.
                if (back != null) {
                    // (Update 130: really blurred, once — the tiny copy alone, stretched ten times, was blocky.)
                    BakedBlurImage(back, 20.dp, Modifier.fillMaxSize(), alpha = 0.95f)
                }
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.16f)))
                // A screenshot set into the glass.
                if (a.shots.isNotEmpty()) {
                    val inset = 10.dp
                    ShowcaseCard(
                        a, i, time, pw - inset * 2, ph - inset * 2 - 14.dp, colors,
                        Modifier.offset(x = inset, y = inset), period = 11f
                    )
                }
                // Bevel and a light sweeping across the glass.
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), colors.accent.copy(alpha = 0.55f))), shape)
                        .drawBehind {
                            val t = time.value
                            val x = ((t * 0.09f + i * 0.33f) % 1.6f - 0.3f) * size.width
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                                    start = Offset(x - size.width * 0.15f, 0f), end = Offset(x + size.width * 0.15f, size.height)
                                )
                            )
                        }
                )
            }
        }
    }
}


/**
 * Where the Gaming room's monitor screen lands on a [w] × [h] screen with the monitor at [focusY] — the same
 * fit as gaming_room.agsl (keep the two in step), so the clip lies exactly over it.
 */
internal fun roomMonitorRect(w: Float, h: Float, focusY: Float): androidx.compose.ui.geometry.Rect {
    val roomW = 1692f
    val roomH = 845f
    val s0x = 811f; val s0y = 324f; val s1x = 1134f; val s1y = 492f
    val mx = (s0x + s1x) / 2f
    val my = (s0y + s1y) / 2f
    val fy = focusY.coerceIn(0.25f, 0.75f)
    val k = maxOf(1.12f * maxOf(w / roomW, h / roomH), fy * h / my, (1f - fy) * h / (roomH - my))
    fun cl(v: Float, lo: Float, hi: Float) = minOf(maxOf(v, lo), hi)
    val px = cl(w * 0.55f, w - (roomW - mx) * k, mx * k)
    val py = cl(h * fy, h - (roomH - my) * k, my * k)
    return androidx.compose.ui.geometry.Rect(px + (s0x - mx) * k, py + (s0y - my) * k, px + (s1x - mx) * k, py + (s1y - my) * k)
}

/**
 * 0..1 down a [h]-pixel-tall screen: the middle of the biggest stretch that [avoid] leaves open (the status
 * bar, the game row, the legend). Kept to 0.25..0.75; 0.5 when there's nothing to avoid.
 */
internal fun openMiddle(avoid: List<ClosedFloatingPointRange<Float>>, h: Float): Float {
    if (h <= 0f || avoid.isEmpty()) return 0.5f
    var best = 0f
    var mid = h / 2f
    var y = 0f
    for (r in avoid.sortedBy { it.start } + listOf(h..h)) {
        val s = r.start.coerceIn(0f, h)
        if (s - y > best) {
            best = s - y
            mid = (y + s) / 2f
        }
        y = maxOf(y, r.endInclusive.coerceIn(0f, h))
    }
    return (mid / h).coerceIn(0.25f, 0.75f)
}

/**
 * The Gaming room's painting (res/drawable-nodpi/gaming_room.webp, 1692 × 845), decoded once and kept while
 * memory allows: half size on low-memory devices. Also used for the theme's thumbnail.
 */
internal class RoomPicture private constructor(val shader: android.graphics.BitmapShader, val w: Int, val h: Int) {
    companion object {
        @Volatile private var ref: java.lang.ref.SoftReference<RoomPicture>? = null

        fun cached(): RoomPicture? = ref?.get()

        /** Decodes it now (call off the main thread, or from [load]). Null when it can't. */
        fun decode(context: android.content.Context): RoomPicture? {
            cached()?.let { return it }
            return runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = if (com.shelf.launcher.device.MemoryBudget.small(context)) 2 else 1
                }
                val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, com.shelf.launcher.R.drawable.gaming_room, opts)
                    ?: return null
                RoomPicture(
                    android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP),
                    bmp.width, bmp.height
                ).also { ref = java.lang.ref.SoftReference(it) }
            }.getOrNull()
        }

        suspend fun load(context: android.content.Context): RoomPicture? =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decode(context) }
    }
}

/** Seconds per channel on the CRT room's TV (crt_room.agsl's CH_LEN: static and the channel number follow it). */
private const val CRT_CHANNEL_S = 9f

/** A picture ready for the CRT room's TV, with the average colour of its two halves (the room's light). */
private class CrtPicture(val shader: android.graphics.BitmapShader, val w: Int, val h: Int, val left: FloatArray, val right: FloatArray)

/** Decodes [url] for the TV (about 768 × 576, a normal bitmap the shader can sample). Null when it can't. */
private suspend fun loadCrtPicture(context: android.content.Context, url: Any, sizeW: Int = 0, sizeH: Int = 0): CrtPicture? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val small = com.shelf.launcher.device.MemoryBudget.small(context)
            val w = if (sizeW > 0) (if (small) sizeW * 2 / 3 else sizeW) else if (small) 512 else 768
            val h = if (sizeH > 0) (if (small) sizeH * 2 / 3 else sizeH) else w * 3 / 4
            val request = coil.request.ImageRequest.Builder(context)
                .data(url)
                .size(w, h)
                .scale(coil.size.Scale.FILL)
                .precision(coil.size.Precision.INEXACT)
                .allowHardware(false)
                // Always a still bitmap: the TV's shader needs a BitmapDrawable, not an animated one (update 91).
                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
                .memoryCacheKey("crt|${if (url is String) url else System.identityHashCode(url).toString()}|${w}x$h")
                .build()
            val result = coil.Coil.imageLoader(context).execute(request) as? coil.request.SuccessResult
            val bmp = (result?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp == null || bmp.isRecycled) null else {
                val avg = android.graphics.Bitmap.createScaledBitmap(bmp, 2, 1, true)
                fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
                val left = rgb(avg.getPixel(0, 0))
                val right = rgb(avg.getPixel(1, 0))
                if (avg !== bmp) avg.recycle()
                CrtPicture(
                    android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP),
                    bmp.width, bmp.height, left, right
                )
            }
        }.getOrNull()
    }

/**
 * CRT room, Android 13+: a dark gaming den rendered in 3D by one shader (crt_room.agsl) — the TV, the
 * cabinet with the console and VCR, the pad, shelves, a rainy window — with the game's hero art and then
 * its screenshots on the TV's curved glass, a new "channel" every 9 s. The room is lit by the picture's
 * own colours. Drawn at the background quality's resolution and frame rate like the other scenes; the
 * next channel's picture is decoded ahead so it's ready when the static clears. No art: a test card.
 */
@androidx.annotation.RequiresApi(33)
@Composable
private fun CrtRoom3D(
    scene: SceneShader, a: ShowcaseArt?, time: State<Float>, colors: ShelfColors, modifier: Modifier,
    room: RoomPicture? = null, focus: State<Float>? = null, poster: State<CrtPicture?>? = null,
    /** CRT room: 0.5 + how far down the view slides (SceneArt.shiftFocus). */
    shift: State<Float>? = null
) {
    val context = LocalContext.current
    val quality = LocalSceneQuality.current
    val tuning = LocalSceneTuning.current
    val seq = remember(a) { if (a == null) emptyList() else listOfNotNull(a.hero ?: a.cover) + a.shots }
    val index by remember { androidx.compose.runtime.derivedStateOf { floor(time.value / CRT_CHANNEL_S).toInt() } }
    fun at(i: Int): String? = if (seq.isEmpty()) null else seq[((i % seq.size) + seq.size) % seq.size]
    val url = at(index)
    val next = if (seq.size > 1) at(index + 1) else null
    val picture = remember { mutableStateOf<CrtPicture?>(null) }
    val swappedAt = remember { mutableFloatStateOf(-10f) }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url == null) {
            picture.value = null
            return@LaunchedEffect
        }
        val pic = loadCrtPicture(context, url)
        if (pic != null) {
            picture.value = pic
            swappedAt.floatValue = time.value
        }
    }
    // Decoded ahead (into the image cache) so the next channel is there when the static clears.
    androidx.compose.runtime.LaunchedEffect(next) { if (next != null) loadCrtPicture(context, next) }
    androidx.compose.foundation.layout.Spacer(
        modifier.sceneLayer(quality.scale).drawWithCache {
            val brush = androidx.compose.ui.graphics.ShaderBrush(scene.shader)
            onDrawBehind {
                // Read in the draw phase only: nothing recomposes per frame.
                val tt = time.value
                scene.update(size.width, size.height, tt, colors, quality.lod, tuning.particles)
                val pic = picture.value
                // A burst of static when the picture changes between channels (a new game was picked).
                val since = tt - swappedAt.floatValue
                val tune = if (since < 0f) 0f else (1f - since / 0.45f).coerceIn(0f, 1f)
                scene.tvPicture(pic?.shader, pic?.w ?: 1, pic?.h ?: 1, pic?.left, pic?.right, tune)
                if (room == null && shift != null) scene.focus(shift.value)
                if (room != null) {
                    scene.roomPicture(room.shader, room.w, room.h, focus?.value ?: 0.5f)
                    val pp = poster?.value
                    scene.poster(pp?.shader, pp?.w ?: 1, pp?.h ?: 1)
                }
                drawRect(brush)
                drawSceneShade()
                if (tuning.dim > 0f) drawRect(Color.Black.copy(alpha = tuning.dim))
            }
        }
    )
}

/**
 * Retro TV (music, Android 13+): the CRT room's 3D den from the game showcase — the same high-quality TV —
 * with the song's cover on the screen, a burst of static when the song changes and a little on the kicks.
 * Returns false when the shader isn't available (the older flat Retro TV draws instead).
 */
@Composable
internal fun MusicCrtRoom(colors: ShelfColors, modifier: Modifier): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 33) return false
    val scene = remember { SceneShader.create(ThemeMotif.SHOWCASE_CRT) } ?: return false
    val context = LocalContext.current
    val quality = LocalSceneQuality.current
    val tuning = LocalSceneTuning.current
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    val active = rememberIsActive() && rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current
    val time = remember { mutableFloatStateOf(STILL_FRAME_TIME) }
    SceneClock(active, time, idleFrameMs(), tuning.speed)
    val cover = MusicTheme.nowPlaying?.art
    val picture = remember { mutableStateOf<CrtPicture?>(null) }
    val swappedAt = remember { mutableFloatStateOf(-10f) }
    androidx.compose.runtime.LaunchedEffect(cover) {
        val pic = cover?.takeIf { !it.isRecycled }?.let { loadCrtPicture(context, it) }
        picture.value = pic
        swappedAt.floatValue = time.value
    }
    androidx.compose.foundation.layout.Spacer(
        modifier.sceneLayer(quality.scale).drawWithCache {
            val brush = androidx.compose.ui.graphics.ShaderBrush(scene.shader)
            onDrawBehind {
                val tt = time.value
                scene.update(size.width, size.height, tt, colors, quality.lod, tuning.particles)
                val pic = picture.value
                val since = tt - swappedAt.floatValue
                val swap = if (since < 0f) 0f else (1f - since / 0.45f).coerceIn(0f, 1f)
                val kick = if (com.shelf.launcher.data.AudioReactive.live) (com.shelf.launcher.data.AudioReactive.bass - 0.75f).coerceIn(0f, 0.25f) * 0.5f else 0f
                scene.tvPicture(pic?.shader, pic?.w ?: 1, pic?.h ?: 1, pic?.left, pic?.right, maxOf(swap, kick))
                drawRect(brush)
                drawSceneShade()
                if (tuning.dim > 0f) drawRect(Color.Black.copy(alpha = tuning.dim))
            }
        }
    )
    return true
}

/** The CRT effect laid over the TV's picture (Android 12 / no shader): curved glass, scanlines, colour fringes, flicker. */
private const val CRT_AGSL = """
uniform shader content;
uniform float2 size;
uniform float time;
uniform float noise;   // 0..1 static between channels
float h12(float2 p) { p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
half4 main(float2 fc) {
    float2 q = fc / size * 2.0 - 1.0;
    // Barrel distortion: the picture bulges like the glass.
    float2 d = q * (1.0 + 0.06 * dot(q, q));
    float2 p = (d * 0.5 + 0.5) * size;
    float inside = step(abs(d.x), 1.0) * step(abs(d.y), 1.0);
    // Colour fringes towards the edges.
    float2 off = d * 1.6;
    half4 c;
    c.r = content.eval(p + off).r;
    c.g = content.eval(p).g;
    c.b = content.eval(p - off).b;
    c.a = 1.0;
    float3 col = float3(c.rgb);
    // Scanlines and the RGB mask.
    col *= 0.78 + 0.22 * step(0.5, fract(fc.y / 3.0));
    float m = mod(floor(fc.x), 3.0);
    col *= float3(m < 0.5 ? 1.05 : 0.92, m > 0.5 && m < 1.5 ? 1.05 : 0.92, m > 1.5 ? 1.05 : 0.92);
    // Vignette, flicker, a rolling bright band.
    col *= 1.0 - 0.35 * dot(q, q) * 0.5;
    col *= 0.95 + 0.05 * sin(time * 50.0);
    col += 0.05 * exp(-abs(fract(fc.y / size.y - time * 0.08) - 0.5) * 20.0);
    // Static between channels.
    float n = h12(floor(fc / 2.0) + floor(time * 60.0));
    col = mix(col, float3(n), noise);
    return half4(col * inside, 1.0);
}
"""

/**
 * CRT room: the room and the old TV are drawn by the backdrop shader (crt_room.agsl); here the game's
 * hero art, then its screenshots, play on the TV's screen - a new "channel" every 9 seconds with a
 * burst of static - under a CRT effect (curved glass, scanlines, colour fringes) on Android 13+.
 * The screen's place matches the shader: centre (width/2, 0.40 h), half size (0.375 h, 0.28 h).
 */
@Composable
private fun CrtRoom(a: ShowcaseArt, time: State<Float>, colors: ShelfColors) {
    val period = 9f
    val seq = remember(a) { listOfNotNull(a.hero ?: a.cover) + a.shots }
    val index by remember(a) { androidx.compose.runtime.derivedStateOf { floor(time.value / period).toInt() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val sw = h * 0.75f
        val shh = h * 0.56f
        val left = maxWidth / 2 - sw / 2
        val top = h * 0.12f
        if (seq.isEmpty()) return@BoxWithConstraints
        val crt = remember {
            if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { android.graphics.RuntimeShader(CRT_AGSL) }.getOrNull() else null
        }
        Box(
            Modifier
                .offset(left, top)
                .size(sw, shh)
                .clip(RoundedCornerShape(sw * 0.04f))
                .graphicsLayer {
                    if (crt != null && android.os.Build.VERSION.SDK_INT >= 33) {
                        val tt = time.value
                        val phase = tt / period - floor(tt / period)
                        // Static for the first moment of each channel.
                        val noise = (1f - phase * 12f).coerceIn(0f, 1f) * 0.85f
                        crt.setFloatUniform("size", size.width, size.height)
                        crt.setFloatUniform("time", tt)
                        crt.setFloatUniform("noise", noise)
                        renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(crt, "content").asComposeRenderEffect()
                    }
                }
        ) {
            val url = seq[((index % seq.size) + seq.size) % seq.size]
            ShowcaseImage(url, sw, shh, Modifier.fillMaxSize().graphicsLayer {
                val p = (time.value / period - index).coerceIn(0f, 1f)
                scaleX = 1.04f + 0.06f * p
                scaleY = scaleX
            })
            // Android 12: plain scanlines over the picture instead of the shader.
            if (crt == null) Canvas(Modifier.fillMaxSize()) {
                var y = 0f
                while (y < size.height) {
                    drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, y), size = Size(size.width, 1.5f))
                    y += 4f
                }
            }
        }
        // A soft glass glare over the screen.
        Canvas(Modifier.offset(left, top).size(sw, shh)) {
            drawRoundRect(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 0.6f)),
                cornerRadius = CornerRadius(size.width * 0.04f)
            )
        }
    }
}
