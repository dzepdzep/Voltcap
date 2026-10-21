package com.shelf.launcher.ui

import androidx.compose.animation.togetherWith
import androidx.compose.runtime.produceState

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** One game in the slideshow. */
data class ScreensaverSlide(
    val title: String,
    val subtitle: String?,
    val imageUrl: String,
    val logoUrl: String?,
    /**
     * (Update 238, #87: the game's animated hero, played over [imageUrl] (its still hero) once the slide is in. Null:
     * a still slide, as before. Filled in by [heroSlides] from the library, so the callers stay as they are.)
     */
    val animatedUrl: String? = null
)

/**
 * (Update 238, #87: the slideshow's slides with their animated heroes — Settings › Screensaver › Heroes.)
 *  - All heroes: [given] as they are; a game whose hero is animated (SteamGridDB's animated hero, scraped beside the
 *    still one, or an animated hero picture) plays it, when animated artwork plays for that game.
 *  - Only animated heroes: only the games with an animated hero (heroes only — never covers or wide capsules), unless
 *    the game is set to "Still". None at all: [given], so the screen is never empty.
 */
private fun heroSlides(context: android.content.Context, given: List<ScreensaverSlide>): List<ScreensaverSlide> = runCatching {
    val onlyAnimated = com.shelf.launcher.data.ScreensaverStore(context).load().heroes == com.shelf.launcher.data.SaverHeroes.ANIMATED
    val state = CompanionBridge.vm?.get()?.uiState?.value
    if (state == null) {
        // (No library at hand: an animated hero picture in the slides themselves still plays.)
        return@runCatching given.map { s ->
            if (com.shelf.launcher.data.AnimatedArt.enabled && com.shelf.launcher.data.AnimatedArt.looksAnimated(s.imageUrl)) s.copy(animatedUrl = s.imageUrl) else s
        }
    }
    val byStill = HashMap<String, String>()
    val pool = ArrayList<ScreensaverSlide>()
    for (g in state.games) {
        val k = g.stableKey
        val look = com.shelf.launcher.data.GameLooks.of(k).animateArt
        // Only animated heroes: everything but "Still"; All heroes: as animated artwork plays for this game (ArtPick.animates).
        if (if (onlyAnimated) look == false else (look ?: com.shelf.launcher.data.AnimatedArt.enabled).not()) continue
        val o = state.artOverrides[k]
        val a = state.art[k]
        val ownHero = o?.hero
        val anim = if (ownHero != null) ownHero.takeIf { com.shelf.launcher.data.AnimatedArt.looksAnimated(it) }
            else a?.animatedHeroUrl ?: a?.heroUrl?.takeIf { com.shelf.launcher.data.AnimatedArt.looksAnimated(it) }
        if (anim == null) continue
        val still = ownHero ?: a?.heroUrl ?: anim
        byStill[still] = anim
        if (onlyAnimated) pool += ScreensaverSlide(g.name, g.platform, still, o?.logo ?: a?.logoUrl, animatedUrl = anim)
    }
    if (onlyAnimated && pool.isNotEmpty()) pool.shuffled().take(80)
    else given.map { s -> byStill[s.imageUrl]?.let { s.copy(animatedUrl = it) } ?: s }
}.getOrDefault(given)

/**
 * (Update 238, #87) The slideshow's still picture for [url] at [w]×[h] — the same request as before, kept still
 * ([AnimatedArt.PARAM_STILL]) when the slide's animated hero plays over it (its first frame, a bitmap in memory).
 */
private fun saverStillRequest(
    context: android.content.Context, url: String, still: Boolean, w: Int, h: Int,
    sharpen: Boolean, lite: Boolean, small: Boolean, low: Boolean
): coil.request.ImageRequest = coil.request.ImageRequest.Builder(context)
    .data(url)
    .size(w, h)
    .scale(coil.size.Scale.FILL)
    .precision(coil.size.Precision.INEXACT)
    .memoryCacheKey("saver|$url|${w}x$h")
    .apply {
        if (still) setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true, memoryCacheKey = null)
        if (small || low) bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
        if (sharpen && !lite && !small && !low) transformations(SharpUpscale())
    }
    .build()

/**
 * Idle screen. Shows the playing track when music is on (and allowed),
 * otherwise a slow slideshow of the library's artwork, with a clock.
 *
 * Built for OLED screens:
 *  - Nothing stays put: the clock and all text drift to a new spot every
 *    minute, so no pixel shows the same thing for long (burn-in).
 *  - Artwork is dimmed and slowly zooms/pans (Ken Burns), never static.
 *  - After [blackoutMinutes] it fades to pure black — on OLED that's the same
 *    as the pixels being switched off. 0 = never.
 *
 * Any button or tap dismisses it (handled by the caller for buttons).
 */
@Composable
fun ScreenSaver(
    slides: List<ScreensaverSlide>,
    nowPlaying: NowPlaying?,
    showMusic: Boolean,
    blackoutMinutes: Int,
    onDismiss: () -> Unit,
    showLyrics: Boolean = false,
    /** Upscale small images crisply (Settings › "Upscale small backgrounds"). */
    sharpen: Boolean = true,
    /** Blurred album art behind the song (Second screen settings). */
    albumBlur: Boolean = true,
    /** Its blur strength, 0–100 %. */
    albumBlurPercent: Int = 60,
    /** Only a big clock and date (second screen's "Clock" idle mode). */
    clockOnly: Boolean = false,
    /** Without music: this animated theme (with a drifting clock) instead of the slideshow. */
    animatedTheme: com.shelf.launcher.ui.theme.AppTheme? = null,
    /** With music: this music theme full screen (with the song) instead of the song card. */
    musicTheme: com.shelf.launcher.ui.theme.AppTheme? = null,
    /** While music plays: no fade to black, and the screen is kept on. */
    stayWithMusic: Boolean = true,
    /**
     * Update 202: told when it has faded to black (true) and when it's lit again / gone (false). Only the main
     * screen's screensaver passes it (MusicBlackout.setSaverBlack, the background music fades out); the second
     * screen's own blackout doesn't stop the music.
     */
    onBlackout: ((Boolean) -> Unit)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("ScreenSaver")
    var blackout by remember { mutableStateOf(false) }
    val musicNow = nowPlaying?.isPlaying == true
    val holdForMusic = stayWithMusic && musicNow
    LaunchedEffect(blackoutMinutes, holdForMusic) {
        // Music playing (and "stay while music plays" on): the screensaver keeps showing.
        if (holdForMusic) { blackout = false; return@LaunchedEffect }
        if (blackoutMinutes > 0) {
            delay(blackoutMinutes * 60_000L)
            blackout = true
        }
    }
    // Update 202: the background music follows the fade to black (and comes back the moment it's dismissed).
    val blackoutListener = androidx.compose.runtime.rememberUpdatedState(onBlackout)
    LaunchedEffect(blackout) { blackoutListener.value?.invoke(blackout) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { blackoutListener.value?.invoke(false) }
    }
    // Keep the display on while it plays along with the music.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(holdForMusic) {
        view.keepScreenOn = holdForMusic
        onDispose { view.keepScreenOn = false }
    }
    val contentAlpha by animateFloatAsState(if (blackout) 0f else 1f, tween(4000), label = "blackout")

    var driftX by remember { mutableStateOf(0.dp) }
    var driftY by remember { mutableStateOf(0.dp) }
    LaunchedWhileActive {
        while (true) {
            delay(60_000)
            driftX = Random.nextInt(-28, 29).dp
            driftY = Random.nextInt(-20, 21).dp
        }
    }
    val dx by animateDpAsState(driftX, tween(8000), label = "driftX")
    val dy by animateDpAsState(driftY, tween(8000), label = "driftY")

    val music = showMusic && nowPlaying != null && nowPlaying.isPlaying

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        // Update 125: the screensaver's theme / visualizer always runs at full speed (only Background quality and
        // Background frame rate, which you pick, decide its frame rate — never the idle battery steps).
        androidx.compose.runtime.CompositionLocalProvider(LocalSceneFullSpeed provides true, LocalSceneMinFrameMs provides 0) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
            if (clockOnly) {
                BigClockScene(dx, dy)
            } else Crossfade(targetState = music, animationSpec = tween(1200), label = "mode") { showTrack ->
                if (showTrack && nowPlaying != null && musicTheme != null) {
                    ThemeMusicSaver(musicTheme, nowPlaying, dx, dy, showLyrics)
                } else if (showTrack && nowPlaying != null) {
                    MusicScene(nowPlaying, dx, dy, showLyrics, albumBlur, albumBlurPercent)
                } else if (animatedTheme != null) {
                    Box(Modifier.fillMaxSize()) {
                        ThemeScene(animatedTheme.motif, animatedTheme.colors, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                        BigClockScene(dx, dy)
                    }
                } else {
                    SlideshowScene(slides, dx, dy, sharpen)
                }
            }
        }
        }
    }
}

/**
 * How hard the screensaver may work on this device. Weaker handhelds (Lite effects, the known slower
 * models, small memory, or a Low / Lowest background quality) get smaller pictures at 2 bytes a pixel,
 * a ~20 fps zoom instead of one every display frame, a pre-blurred cover instead of a live blur, and
 * one picture decoded at a time. Stronger ones keep full quality.
 */
@Composable
internal fun rememberSaverLow(): Boolean {
    val context = LocalContext.current
    val lite = LocalLiteEffects.current
    val quality = LocalSceneQuality.current
    return remember(lite, quality) {
        lite || quality.scale < 0.5f || com.shelf.launcher.device.DevicePerformance.isLowEnd(context) ||
            com.shelf.launcher.device.MemoryBudget.small(context)
    }
}

/**
 * Seconds since the screensaver appeared, advanced at most every [frameMs] (0 = every frame) and only
 * while the screen is on and Voltcap in front. Read in graphics layers, so the slow zoom only redraws.
 */
@Composable
private fun rememberSaverClock(frameMs: Int): androidx.compose.runtime.State<Float> {
    val t = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val active = rememberIsActive()
    LaunchedEffect(active, frameMs) {
        if (!active) return@LaunchedEffect
        val base = t.floatValue
        val start = androidx.compose.runtime.withFrameNanos { it }
        var last = 0L
        // Rest most of the interval after a step (the slow zoom doesn't need every display frame).
        val nap = (frameMs - 10L).coerceAtLeast(0L)
        while (true) {
            val stepped = androidx.compose.runtime.withFrameNanos { now ->
                if (now - last >= frameMs * 1_000_000L) {
                    last = now
                    t.floatValue = base + (now - start) / 1_000_000_000f
                    true
                } else false
            }
            if (stepped && nap > 0L) delay(nap)
        }
    }
    return t
}

@Composable
private fun SlideshowScene(given: List<ScreensaverSlide>, dx: Dp, dy: Dp, sharpen: Boolean = true) {
    com.shelf.launcher.Breadcrumbs.mark("SlideshowScene")
    // (Update 238, #87: the slides with their animated heroes, or only the animated ones — Settings › Screensaver › Heroes.)
    val outerContext = LocalContext.current
    val slides = remember(given) { heroSlides(outerContext, given) }
    // (Update 238, #87: its heroes may play while the home sleeps behind it — AnimatedArt.Gate.)
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.shelf.launcher.data.AnimatedArt.setSaverShowing(true)
        onDispose { com.shelf.launcher.data.AnimatedArt.setSaverShowing(false) }
    }
    var index by remember { mutableStateOf(0) }
    val low = rememberSaverLow()
    // Pictures that failed in a row: after a few the slideshow stops hammering the network / decoder
    // and waits longer (offline after a game, art moved) instead of racing through every slide.
    var failures by remember { mutableStateOf(0) }
    LaunchedWhileActive(slides.size, failures) {
        if (slides.size > 1) {
            while (true) {
                // (Update 238, #87: an animated hero gets its full slide once it moves — a few seconds more.)
                val moving = slides.getOrNull(index % slides.size)?.animatedUrl != null && failures < 3
                delay((if (failures >= 3) 30_000L else if (low) 15_000L else 12_000L) + if (moving) 6_000L else 0L)
                index = (index + 1) % slides.size
            }
        }
    }
    LaunchedEffect(failures) {
        if (failures in 1..2 && slides.size > 1) {
            delay(1500)
            index = (index + 1) % slides.size
        }
    }
    val slide = slides.getOrNull(index % maxOf(1, slides.size))
    val clock = rememberSaverClock(if (low) 50 else 16)
    val fadeMs = if (low) 900 else 1800
    // (Update 238, #87: the pictures' size, worked out once here — the slide and the next one's still share it.)
    val lite = LocalLiteEffects.current
    val small = remember { com.shelf.launcher.device.MemoryBudget.small(outerContext) }
    val maxW = remember(low) { if (low) 1280 else com.shelf.launcher.device.MemoryBudget.heroMaxWidth(outerContext) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val screen = screenSizeDp()
    val screenW = (screen.width.value * density).toInt().coerceAtLeast(1)
    val screenH = (screen.height.value * density).toInt().coerceAtLeast(1)
    // The zoom ends at 1.12×: decoding at screen size is enough (more only costs memory).
    val w = minOf(screenW, maxW)
    val h = (screenH.toLong() * w / screenW).toInt().coerceAtLeast(1)
    // (Update 238, #87: the next slide's still picture readied a few seconds in, so it's there when it fades up — only
    // the still: its animated hero loads once it's on screen, so one animated picture plays at a time.)
    LaunchedEffect(index, slides, w, h, sharpen, lite, low) {
        if (slides.size < 2) return@LaunchedEffect
        delay(4_000)
        val next = slides[(index + 1) % slides.size]
        runCatching {
            coil.Coil.imageLoader(outerContext).enqueue(
                saverStillRequest(outerContext, next.imageUrl, next.animatedUrl != null, w, h, sharpen, lite, small, low)
            )
        }
    }

    // Update 294 (Toni: "screensavers sometimes don't display anything" — seen on the Thor: only the clock and the
    // console's name, no picture, no game name): a slide whose picture never arrives (a request that hangs, a file that
    // isn't there yet) left the screen black until the next slide, and a logo that failed left no name at all.
    //  - a dim backdrop in the theme's colour is always under the picture, so the screen is never just black;
    //  - a picture that hasn't loaded within 8 s counts as failed (on to the next one, as an error does);
    //  - the game's name shows when its logo can't be loaded.
    var loaded by remember(slide) { mutableStateOf(false) }
    var logoFailed by remember(slide) { mutableStateOf(false) }
    LaunchedEffect(slide) {
        if (slide == null) return@LaunchedEffect
        delay(8_000)
        if (!loaded) failures++
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ShelfTheme.accent.copy(alpha = 0.14f), Color.Black))))
        if (slide != null) {
            // Weaker devices: no cross-fade (two full-screen pictures decoded and drawn at once) — the
            // next one fades up from black instead.
            Crossfade(targetState = slide, animationSpec = tween(fadeMs), label = "slide") { current ->
                val shownAt = remember(current) { clock.value }
                val context = androidx.compose.ui.platform.LocalContext.current
                val request = remember(current.imageUrl, current.animatedUrl, sharpen, lite, low, w, h) {
                    saverStillRequest(context, current.imageUrl, current.animatedUrl != null, w, h, sharpen, lite, small, low)
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Dimmed with the paint's alpha (no extra off-screen layer).
                    alpha = 0.75f,
                    filterQuality = if (low) androidx.compose.ui.graphics.FilterQuality.Low else androidx.compose.ui.graphics.FilterQuality.High,
                    // A picture that can't be loaded (gone from the server, no network): on to the next one
                    // after a short pause instead of a black screen (or a race through every slide).
                    onError = { failures++ },
                    onSuccess = { failures = 0; if (current == slide) loaded = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Ken Burns: 1 → 1.12 over 14 s, read from the throttled clock (redraw only).
                            val p = ((clock.value - shownAt) / 14f).coerceIn(0f, 1f)
                            scaleX = 1f + 0.12f * p
                            scaleY = scaleX
                        }
                )
                // (Update 238, #87: the animated hero over its still, faded in once the slide has faded up — so only one
                // animated picture is decoded at a time — and once it's ready; if it can't load, the still stays.)
                val anim = current.animatedUrl
                if (anim != null && android.os.Build.VERSION.SDK_INT >= 28) {
                    var play by remember(current) { mutableStateOf(false) }
                    var ready by remember(current) { mutableStateOf(false) }
                    LaunchedEffect(current) { delay(fadeMs + 300L); play = true }
                    val shown by animateFloatAsState(if (ready) 1f else 0f, tween(1200), label = "heroMotion")
                    if (play) {
                        val moving = remember(anim, w, h) {
                            coil.request.ImageRequest.Builder(context)
                                .data(anim)
                                .size(w, h)
                                .scale(coil.size.Scale.FILL)
                                .precision(coil.size.Precision.INEXACT)
                                // Forced to play (even with Animated artwork off: "Only animated heroes" asked for it) and
                                // marked as the screensaver's, so it plays while the home sleeps. Its address tags it, so
                                // its first frame is kept as the still-first stand-in (AnimatedArt.StillFirst).
                                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_ANIMATE, true, memoryCacheKey = null)
                                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_SAVER, true, memoryCacheKey = null)
                                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_SRC, anim, memoryCacheKey = null)
                                .build()
                        }
                        AsyncImage(
                            model = moving,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.75f * shown,
                            filterQuality = if (low) androidx.compose.ui.graphics.FilterQuality.Low else androidx.compose.ui.graphics.FilterQuality.High,
                            onSuccess = { ready = true },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = ((clock.value - shownAt) / 14f).coerceIn(0f, 1f)
                                    scaleX = 1f + 0.12f * p
                                    scaleY = scaleX
                                }
                        )
                    }
                }
            }
            if (failures >= 3) {
                // Nothing loads (offline?): a slow gradient so the screen isn't just black, and the clock.
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(ShelfTheme.accent.copy(alpha = 0.18f), Color.Black))))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.45f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(dx, dy)
                    .padding(36.dp)
            ) {
                if (slide.logoUrl != null && !logoFailed) {
                    AsyncImage(
                        model = slide.logoUrl,
                        contentDescription = slide.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { logoFailed = true },
                        modifier = Modifier
                            .heightIn(max = 64.dp)
                            .widthIn(max = 260.dp)
                    )
                } else {
                    Text(slide.title, color = Color.White.copy(alpha = 0.9f), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                slide.subtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
        Clock(
            Modifier
                .align(Alignment.TopEnd)
                .offset(dx, dy)
                .padding(32.dp)
        )
    }
}

@Composable
private fun MusicScene(
    nowPlaying: NowPlaying,
    dx: Dp,
    dy: Dp,
    showLyrics: Boolean,
    albumBlur: Boolean = true,
    albumBlurPercent: Int = 60
) {
    com.shelf.launcher.Breadcrumbs.mark("MusicScene")
    val art = remember(nowPlaying.art) { nowPlaying.art?.let { runCatching { it.asImageBitmap() }.getOrNull() } }
    val color = rememberArtColor(nowPlaying.art, ShelfTheme.accent)
    val position = rememberPlaybackPosition(nowPlaying)

    Box(Modifier.fillMaxSize()) {
        val low = rememberSaverLow()
        if (art != null && albumBlur) {
            if (low) {
                // Weaker devices: the cover blurred once on the CPU (a tiny bitmap, stretched smooth)
                // instead of a full-screen live blur that re-ran on every redraw (clock, progress, lyrics).
                val soft = remember(nowPlaying.art) { nowPlaying.art?.let { MusicTheme.softCopy(it) }?.asImageBitmap() }
                if (soft != null) Image(
                    soft, null, contentScale = ContentScale.Crop, alpha = 0.45f,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Update 121: blurred once at the chosen strength (a small copy, stretched smooth) — no live
                // blur re-run on every clock / progress / lyric redraw, and soft on every Android version.
                // No blur chosen (0 %): the cover sharp.
                val blur = blurRadiusFor(albumBlurPercent, 40f)
                val blurred = blur.value >= 1f
                val baked = rememberBakedBlur(nowPlaying.art?.takeIf { blurred }, blur)
                // (Nothing for the moment the blurred copy takes, rather than a flash of the sharp cover.)
                val shown = if (blurred) baked else art
                if (shown != null) Image(
                    shown, null,
                    contentScale = ContentScale.Crop,
                    filterQuality = if (blurred) androidx.compose.ui.graphics.FilterQuality.Low else androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.4f }
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.9f))))
        )
        Clock(
            Modifier
                .align(Alignment.TopEnd)
                .offset(dx, dy)
                .padding(32.dp)
        )
        BoxWithConstraints(
            Modifier
                .align(Alignment.CenterStart)
                .offset(dx, dy)
                .padding(start = 48.dp, end = 48.dp)
                .fillMaxWidth()
        ) {
            val artSize = minOf(200.dp, maxWidth * 0.32f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(nowPlaying.art, artSize)
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        nowPlaying.title ?: "Unknown track",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 28.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(28.sp), maxFontSize = 28.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    nowPlaying.artist?.let {
                        Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 18.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(18.sp), maxFontSize = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    nowPlaying.album?.let {
                        Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (nowPlaying.durationMs > 0) {
                        Spacer(Modifier.height(18.dp))
                        val fraction = (position.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(0.8f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.85f))
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatTrackTime(position)} / ${formatTrackTime(nowPlaying.durationMs)}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    if (showLyrics) ScreensaverLyrics(nowPlaying, position)
                }
            }
        }
    }
}

/** The line being sung, big, with the one before and after it dimmed.
 * Unsynced lyrics show their first lines. Nothing shows if none are found. */
@Composable
internal fun ScreensaverLyrics(
    nowPlaying: NowPlaying,
    position: Long,
    /** Small screens (the lower screen of a dual-screen handheld): smaller text and spacing. */
    compact: Boolean = false,
    /** Update 120: the lyrics already found (the big player's look-up), so they never go missing here. */
    known: com.shelf.launcher.data.Lyrics? = null
) {
    val title = nowPlaying.title ?: return
    val lyricsOn by com.shelf.launcher.data.LyricsOptIn.enabled.collectAsState()
    val fetched by produceState<com.shelf.launcher.data.Lyrics?>(null, title, nowPlaying.artist, lyricsOn, known == null) {
        if (known == null) value = com.shelf.launcher.data.LyricsRepository.fetch(title, nowPlaying.artist, nowPlaying.album, nowPlaying.durationMs)
    }
    val lyrics = known ?: fetched
    val current = lyrics ?: return
    val (before, now, after) = if (current.isSynced) {
        val index = current.synced.indexOfLast { it.atMs <= position }
        Triple(
            current.synced.getOrNull(index - 1)?.text,
            current.synced.getOrNull(index)?.text?.ifBlank { "♪" } ?: "♪",
            current.synced.getOrNull(index + 1)?.text
        )
    } else {
        Triple(null, current.plain.firstOrNull() ?: return, current.plain.getOrNull(1))
    }
    val side = if (compact) 12f else 15f
    val main = if (compact) 18f else 22f
    // Left / centre / right and the gentle float follow the lyrics layout (Settings › Music & sound), here too.
    val align = com.shelf.launcher.data.Features.lyricsAlign.state.value
    // Only the lines that fit in the room it has are shown, whole: never a line cut in half at the bottom.
    // The previous line goes first, then the next one, then the current one keeps to one line.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val room = if (constraints.hasBoundedHeight) maxHeight else 10_000.dp
    val sideH = (side * 1.35f * fs).dp
    val mainLineH = (main * 1.25f * fs).dp
    val top = if (compact) 4.dp else 22.dp
    val hasBefore = !compact && !before.isNullOrBlank()
    val hasAfter = !after.isNullOrBlank()
    // (gap above, lines of the current lyric, next line, previous line), best first; the first that fits wins.
    val options = listOf(
        listOf(1, 2, 1, 1), listOf(1, 2, 1, 0), listOf(1, 2, 0, 0), listOf(0, 2, 1, 0), listOf(0, 2, 0, 0), listOf(0, 1, 1, 0), listOf(0, 1, 0, 0)
    ).filter { (_, _, a, b) -> (a == 0 || hasAfter) && (b == 0 || hasBefore) }
    val pick = options.firstOrNull { (g, m, a, b) -> top * g + mainLineH * m + 8.dp + sideH * (a + b) <= room } ?: listOf(0, 1, 0, 0)
    val topGap = top * pick[0]
    val mainLines = pick[1]
    val showAfter = pick[2] == 1
    val showBefore = pick[3] == 1
    Column(Modifier.fillMaxWidth().lyricFloat(), horizontalAlignment = align.horizontal()) {
        Spacer(Modifier.height(topGap))
        // Small screens: no previous line, so the current one and the next fit instead of being cut off.
        if (showBefore) before?.takeIf { it.isNotBlank() }?.let {
            FitText(it, Color.White.copy(alpha = 0.35f), maxSize = side.sp, minSize = 10.sp, maxLines = 1, textAlign = align.text())
        }
        androidx.compose.animation.AnimatedContent(
            targetState = now,
            transitionSpec = {
                androidx.compose.animation.fadeIn(tween(300)) togetherWith androidx.compose.animation.fadeOut(tween(200))
            },
            label = "screensaverLyric",
            contentAlignment = when (align) {
                com.shelf.launcher.data.Features.LyricsAlign.START -> Alignment.TopStart
                com.shelf.launcher.data.Features.LyricsAlign.CENTER -> Alignment.TopCenter
                com.shelf.launcher.data.Features.LyricsAlign.END -> Alignment.TopEnd
            },
            modifier = Modifier.fillMaxWidth()
        ) { line ->
            // Long lines wrap (up to three lines) and get smaller instead of ending in "…".
            FitText(
                line, Color.White.copy(alpha = 0.95f), maxSize = main.sp, minSize = 12.sp, maxLines = mainLines,
                fontWeight = FontWeight.SemiBold, textAlign = align.text(),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (showAfter) after?.takeIf { it.isNotBlank() }?.let {
            FitText(it, Color.White.copy(alpha = 0.35f), maxSize = side.sp, minSize = 10.sp, maxLines = 1, textAlign = align.text())
        }
    }
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("Clock")
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedWhileActive {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    // Follows the device's 12/24-hour setting.
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(timeFormat.format(now), color = Color.White.copy(alpha = 0.85f), fontSize = 52.sp, fontWeight = FontWeight.Light)
        Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
    }
}

/** A large clock and date on black; drifts like the other scenes to spare OLED pixels. */
@Composable
private fun BigClockScene(dx: Dp, dy: Dp) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedWhileActive {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.offset(dx, dy), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeFormat.format(now), color = Color.White.copy(alpha = 0.85f), fontSize = 88.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(88.sp), maxFontSize = 88.sp), fontWeight = FontWeight.Light, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(dateFormat.format(now), color = Color.White.copy(alpha = 0.55f), fontSize = 20.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(20.sp), maxFontSize = 20.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}


/**
 * The screensaver with music and a music theme picked: the theme full screen (its visualizer in the
 * middle), the song's title and artist and the lyric line drifting slowly underneath.
 */
@Composable
private fun ThemeMusicSaver(theme: com.shelf.launcher.ui.theme.AppTheme, nowPlaying: NowPlaying, dx: Dp, dy: Dp, showLyrics: Boolean) {
    val position = rememberPlaybackPosition(nowPlaying)
    val colors = MusicTheme.colors ?: theme.colors
    Box(Modifier.fillMaxSize()) {
        ThemeScene(theme.motif, colors, Modifier.fillMaxSize(), waveY = 0.42f, waveH = 0.27f)
        Column(
            Modifier.align(Alignment.BottomCenter).offset(dx, dy).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(nowPlaying.title ?: "", color = Color.White, fontSize = 26.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(26.sp), maxFontSize = 26.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            nowPlaying.artist?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(17.sp), maxFontSize = 17.sp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            // Lyric cloud scatters them over the scene itself.
            if (showLyrics && !theme.motif.lyrics) ScreensaverLyrics(nowPlaying, position)
        }
    }
}
