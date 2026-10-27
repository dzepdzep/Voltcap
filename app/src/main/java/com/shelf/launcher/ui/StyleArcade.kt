package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Arcade cabinets (update 146; rebuilt in update 347 — ArcadeCabinets.kt draws the machines).
 *
 * Showcase: the game you're on as a big machine on the left — its gameplay on the screen (a clip once you rest on it,
 * else a screenshot, the art or the cover; letterboxed in its own shape, never stretched), its logo or name on the lit
 * marquee, its art printed on the side panel — the game's name, console, play time and achievements beside it with Play
 * and Details, and a row of small machines along the bottom that glides as you move.
 * Arcade hall: the machines standing round the room in a gentle arc, at different depths and angles, the one you're on
 * stepping forward and lighting up while the camera glides along; each category its own section with a sign.
 * Four builds (Layout options › Cabinet): classic upright, candy cabinet, neo-retro, bartop — or Mixed, by console.
 * ◀ ▶ the games, ▼ Play and Details (◀ ▶ between them), ▲ above, L2 / R2 the categories, A the game's window — the Insert
 * Coin screen growing out of the monitor (WinScenesArcade.kt). Touch: tap a machine to pick it, again to open it; swipe.
 */

internal object ArcadeNav {
    /** The camera moving into the monitor as a game starts (0…1); back out when you come back. */
    val zoom = Animatable(0f)
}

private fun arSound(s: KitSound, fallback: UiSound?) = LayoutKitSounds.play(LayoutKit.ARCADE, s, fallback)

@Composable
internal fun StyleArcade(c: StyleCtx, modifier: Modifier = Modifier) {
    val g = c.game
    // A part hidden in Edit Home is skipped by the D-pad (Play and Details live in the info part).
    val infoOff = LayoutParts.off(HomeLayout.ARCADE, "info")
    val scope = rememberCoroutineScope()
    val motion = Features.arMotion.state.value && rememberAnimationsEnabled() && !LocalLiteEffects.current
    val active = rememberIsActive()
    val latestActive by rememberUpdatedState(active)
    val play by rememberUpdatedState(c.actions.play)
    // Play: the camera moves into the monitor first (Full / Automatic animations), then the game starts.
    val launch: (Game) -> Unit = remember(motion) {
        { gg: Game ->
            if (!motion) play(gg) else scope.launch {
                arSound(KitSound.SPECIAL, null)
                ArcadeNav.zoom.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                play(gg)
                // (Nothing started — no emulator, a question first: back out after a moment.)
                kotlinx.coroutines.delay(1400)
                if (latestActive) ArcadeNav.zoom.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
            }
        }
    }
    // Coming back from a game: out of the monitor.
    LaunchedEffect(active) {
        if (active && ArcadeNav.zoom.value > 0f) { kotlinx.coroutines.delay(120); ArcadeNav.zoom.animateTo(0f, tween(460, easing = FastOutSlowInEasing)) }
    }
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼: Play, then Details (the game window / details).
        StyleNav.bar = if (g == null || infoOff) emptyList() else listOf({ launch(g) }, { c.actions.open() })
        StyleLoop.on = loopRowOn(c)
    }
    rememberLayoutKit(LayoutKit.ARCADE, Features.arSounds.state.value, c.state.appearance.soundsEnabled)
    // Plain if / else, never an early return out of an inline composable lambda.
    if (g == null) StyleEmpty(modifier)
    else Box(modifier.graphicsLayer {
        val z = ArcadeNav.zoom.value
        // (Into the big machine's monitor in the showcase, the middle one's in the hall.)
        val hall = Features.arMode.value == Features.ArMode.HALL
        if (z > 0f) { val s = 1f + 0.9f * z; scaleX = s; scaleY = s; transformOrigin = if (hall) TransformOrigin(0.5f, 0.4f) else TransformOrigin(0.2f, 0.42f) }
    }) {
        if (Features.arMode.state.value == Features.ArMode.HALL) ArcadeHallMode(c, g, launch, Modifier.fillMaxSize())
        else ArcadeShowcase(c, g, launch, Modifier.fillMaxSize())
        // The dark as the camera reaches the monitor.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (ArcadeNav.zoom.value * 0.85f).coerceIn(0f, 1f) }.background(Color.Black))
    }
}

/** ▼ goes to Play (always on Play, not where it was left), ▲ to what lies above; ◀ ▶ are the home screen's own (Endless counts them). */
internal fun arcadeKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) {
        StyleNav.forget(StyleNav.Zone.BAR)
        ctx.enterZone(StyleNav.Zone.BAR)
    } else ctx.reach(false)
    Key.DirectionUp -> ctx.reach(true)
    else -> false
}

// ---- Options ----

@Composable
internal fun ArcadeSettings() {
    MutedNote(
        "Your games as arcade machines: its gameplay on the screen (a clip once you rest on it, where it has one), its logo on " +
            "the lit marquee, its art on the side panel. Showcase: the one you're on big, a row of small machines along the " +
            "bottom. Arcade hall: the machines standing round the room, the one you're on stepping forward. ◀ ▶ the games, ▼ Play " +
            "and Details, L2 / R2 the categories (each its own section), A the game's Insert Coin screen. Tap a machine to pick " +
            "it, tap it again to open it; swipe to move along. The R1 Size tile makes the machines bigger or smaller."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("View", Features.ArMode.values(), Features.arMode.state.value, { it.label }) { Features.arMode.value = it }
    StyleChoice("Cabinet", Features.ArCab.values(), Features.arCab.state.value, { it.label }) { Features.arCab.value = it }
    StyleChoice("Camera", Features.ArView.values(), Features.arView.state.value, { it.label }) { Features.arView.value = it }
    StyleChoice("Spacing", Features.ArSpacing.values(), Features.arSpacing.state.value, { it.label }) { Features.arSpacing.value = it }
    StyleChoice("Room", Features.ArRoom.values(), Features.arRoom.state.value, { it.label }) { Features.arRoom.value = it }
    StyleChoice("Screens show", Features.ArScreen.values(), Features.arScreen.state.value, { it.label }) { Features.arScreen.value = it }
    Toggle(Icons.Filled.Movie, "Gameplay clip", "Once you rest on a machine its gameplay clip plays on the screen (games with one; muted; paused while idle). Off: a still.", Features.arVideo)
    Toggle(Icons.Filled.WbSunny, "Marquee glow", "The sign on top lights up on the machine you're on, its light spilling round it. Off: unlit signs.", Features.arcadeGlow)
    Toggle(Icons.Filled.Waves, "Screen lines", "Scan lines and the tube's curve over the screens. Off: a clean picture.", Features.arcadeLines)
    if (Features.arcadeLines.state.value) StyleChoice("Tube effect", Features.ArCrt.values(), Features.arCrt.state.value, { it.label }) { Features.arCrt.value = it }
    Toggle(Icons.Filled.Style, "Cabinets in the game's colours", "Each machine painted in its game's own colours (from its art). Off: your theme's colours.", Features.arcadeGameColors)
    Toggle(Icons.Filled.Brush, "Side art", "The game's art printed on the side panels. Off: plain paint.", Features.arSideArt)
    Toggle(Icons.Filled.Animation, "Moving camera", "The camera glides between machines, the one you're on steps forward, and Play moves into the monitor. Off: straight there.", Features.arMotion)
    if (Features.arMotion.state.value) StyleChoice("Speed", Features.ArSpeed.values(), Features.arSpeed.state.value, { it.label }) { Features.arSpeed.value = it }
    Toggle(Icons.Filled.QueueMusic, "Arcade sounds", "A coin dropping in as a game's screen opens, the tube switching on as a game starts. Off: none of these.", Features.arSounds)
    if (Features.arSounds.state.value) {
        PercentSlider("Sound volume", Features.kitVolume.state.value, { Features.kitVolume.value = it.coerceIn(0, 100) })
        Spacer(Modifier.height(6.dp))
    }
}

// ---- Paint ----

/** The colour of the machine you're on: its game's art (a tiny decoded copy of its cover), else the theme's. Animated. */
@Composable
private fun rememberArcadeColor(c: StyleCtx, g: Game): State<Color> {
    val own = Features.arcadeGameColors.state.value
    val model = if (own) (c.cover(g) ?: c.wide(g)) else null
    val bmp = rememberSoftBitmap(model, 64)
    val art = rememberArtColor(bmp, ShelfTheme.accent)
    // While the next game's art is still decoding the machine keeps the last game's paint (no flash of the theme's).
    val held = remember { arrayOfNulls<Color>(1) }
    if (model == null) held[0] = null else if (bmp != null) held[0] = art
    val target = if (own && held[0] != null) held[0]!! else ShelfTheme.accent
    return animateColorAsState(target, tween(Features.rowGlide.state.value.carouselMs * 2), label = "arcadeColor")
}

/** Another machine's colour, with nothing to decode: from its game's hue, or the theme's. */
@Composable
private fun arcadeColorOf(g: Game): Color = if (Features.arcadeGameColors.state.value) Color.hsl(g.hue.toFloat(), 0.65f, 0.5f) else ShelfTheme.accent

// ---- One machine ----

/**
 * One machine of [build] for [g] in a [w] × [h] box, seen as [view] (read while drawing). Its body and side panel are
 * drawn; its screen and marquee are pictures laid onto their faces ([AffineBox]). [live]: the machine you're on — its
 * screen plays the gameplay clip and marks the game's place (the game window grows out of it). [lit] / [dim] read while
 * drawing.
 */
@Composable
private fun ArcadeMachine(
    c: StyleCtx, g: Game, build: CabBuild, color: () -> Color, w: Dp, h: Dp, view: () -> CabView, lit: () -> Float,
    dim: () -> Float, live: Boolean, detail: Boolean, modifier: Modifier = Modifier
) {
    val model = cabModel(build)
    val density = LocalDensity.current
    val wPx = with(density) { w.toPx() }; val hPx = with(density) { h.toPx() }
    val box = Size(wPx, hPx)
    val glow = Features.arcadeGlow.state.value
    val sideOn = Features.arSideArt.state.value
    val sideBmp = rememberSoftBitmap(if (sideOn) (c.wide(g) ?: c.cover(g)) else null, if (detail) 320 else 160)
    val side = remember(sideBmp) { sideBmp?.asImageBitmap() }
    // The faces' places, worked out while drawing (the view can change every frame as the camera glides).
    val frame: () -> CabFrame = { CabFrame(model, view(), box) }
    // The pictures' own flat sizes: the screen and marquee seen straight on.
    val flat = remember(build, wPx, hPx) { CabFrame(model, CabView(0f, 0f), box) }
    val (s0, s1, s3) = flat.screenQuad()
    val sW = (s1 - s0).getDistance().roundToInt().coerceAtLeast(8); val sH = (s3 - s0).getDistance().roundToInt().coerceAtLeast(8)
    val (m0, m1, m3) = flat.marqueeQuad()
    val mW = (m1 - m0).getDistance().roundToInt().coerceAtLeast(8); val mH = (m3 - m0).getDistance().roundToInt().coerceAtLeast(4)
    Box(modifier.size(w, h)) {
        val gloss = arcadeRoomGloss(Features.arRoom.state.value)
        Canvas(Modifier.fillMaxSize()) { drawCabBody(frame(), cabPaint(build, color()), lit(), glow, detail, dim(), gloss) }
        AffineBox(sW, sH, { frame().screenQuad() }, Modifier.then(if (live) Modifier.gameOrigin(g.stableKey, true) else Modifier)) {
            ArcadeScreen(c, g, live, detail, dim)
        }
        AffineBox(mW, mH, { frame().marqueeQuad() }) { ArcadeMarqueeSign(c, g, lit, glow, detail) }
        Canvas(Modifier.fillMaxSize()) { drawCabSide(frame(), cabPaint(build, color()), side, lit(), dim()) }
    }
}

/**
 * A machine's screen: the gameplay clip on the machine you're on (once you've rested on it a moment, where the game has
 * one), else a still — a gameplay screenshot, the art or the cover (Layout options › Screens show); gameplay letterboxed
 * in its own shape (a vertical game stands upright in black bars), never stretched. No picture: an attract screen with
 * its name. Over it the tube: scan lines, its curve, the glass; and a flash as the machine you're on wakes up.
 */
@Composable
private fun ArcadeScreen(c: StyleCtx, g: Game, live: Boolean, detail: Boolean, dim: () -> Float) {
    val pick = Features.arScreen.state.value
    val shots = c.state.screenshots[g.stableKey].orEmpty()
    val shot = shots.firstOrNull()
    // Update 348: what the screen can show, best first — a picture that fails to load gives way to the next one, and
    // with none left the attract screen: a machine's monitor is never left blank.
    val candidates = when (pick) {
        Features.ArScreen.SHOTS -> listOfNotNull(shot, c.backdrop(g), c.wide(g), c.cover(g))
        Features.ArScreen.ART -> listOfNotNull(c.backdrop(g), c.wide(g), c.cover(g))
        Features.ArScreen.COVER -> listOfNotNull(c.cover(g), c.wide(g))
    }.distinct()
    var failedUrls by remember(g.stableKey) { mutableStateOf(emptySet<Any>()) }
    val url = candidates.firstOrNull { it !in failedUrls }
    val gameplay = url != null && url == shot
    // The screenshots looked up once you rest on the machine you're on (the stills of the others are what's known).
    LaunchedEffect(g.stableKey, live, pick) {
        if (!live || pick != Features.ArScreen.SHOTS || c.state.screenshots.containsKey(g.stableKey)) return@LaunchedEffect
        kotlinx.coroutines.delay(260)
        c.actions.needMedia(g)
    }
    // The clip: asked for after a moment on the machine, shown a moment later; gone the moment you move on.
    val lite = LocalLiteEffects.current
    // (Not while the home is idle: a clip held there showed its first frame — often black — over the screenshot.)
    val clipOn = live && Features.arVideo.state.value && !lite && !HomeIdle.calm && !com.shelf.launcher.data.BatterySaver.resting && !com.shelf.launcher.CrashGuard.safeMode
    var clipReady by remember(g.stableKey) { mutableStateOf(false) }
    LaunchedEffect(g.stableKey, clipOn) {
        clipReady = false
        if (!clipOn) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        c.actions.needVideo(g)
        kotlinx.coroutines.delay(1100)
        clipReady = true
    }
    val clip = if (clipOn && clipReady) c.state.videoUrls[g.stableKey] else null
    // Waking up: a bright line opening into the picture as you arrive on the machine.
    val wake = remember { Animatable(0f) }
    val reduced = !rememberAnimationsEnabled()
    LaunchedEffect(g.stableKey, live) {
        if (!live || reduced || lite) { wake.snapTo(0f); return@LaunchedEffect }
        wake.snapTo(1f); wake.animateTo(0f, tween(380))
    }
    val lines = Features.arcadeLines.state.value
    val strong = Features.arCrt.state.value == Features.ArCrt.STRONG
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (url != null) {
            androidx.compose.runtime.key(url) {
                AsyncImage(
                    rememberStyleArtRequest(url), LocalTitleOptions.current.display(g.name),
                    contentScale = if (gameplay || pick == Features.ArScreen.COVER) ContentScale.Fit else ContentScale.Crop,
                    onError = { failedUrls = failedUrls + url },
                    modifier = Modifier.fillMaxSize().pixelArtwork()
                )
            }
        } else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) {
            AppIconArt(g.packageName, wide = true, modifier = Modifier.fillMaxSize())
        } else ArcadeAttract(g, detail)
        if (clip != null) androidx.compose.runtime.key(clip) {
            VideoBackdrop(clip, withSound = false, modifier = Modifier.fillMaxSize(), paused = HomeIdle.calm, fit = true, shade = false)
        }
        Canvas(Modifier.fillMaxSize()) {
            if (lines) {
                val pitch = if (detail) 3.dp.toPx() else 2.dp.toPx()
                val thick = maxOf(1f, pitch * 0.42f)
                val a = if (strong) 0.32f else 0.18f
                var y = 0f
                while (y < size.height) { drawRect(Color.Black.copy(alpha = a), Offset(0f, y), Size(size.width, thick)); y += pitch }
            }
            // The tube's curve: corners and edges falling into shadow; the glass catching the light.
            drawRect(Brush.radialGradient(0f to Color.Transparent, (if (strong) 0.5f else 0.62f) to Color.Transparent, 1f to Color.Black.copy(alpha = if (lines) 0.6f else 0.35f),
                center = center, radius = size.maxDimension * 0.66f))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.5f, size.height * 0.6f)))
            val k = wake.value
            if (k > 0f) {
                val band = size.height * (1f - k).coerceAtLeast(0.02f)
                drawRect(Color.Black.copy(alpha = k), size = size)
                drawRect(Color.White.copy(alpha = 0.85f * k), Offset(0f, (size.height - band) / 2f), Size(size.width, band.coerceAtMost(size.height)))
            }
            val d = dim()
            if (d > 0f) drawRect(Color.Black.copy(alpha = d * 0.55f), size = size)
        }
    }
}

/** A machine with no picture: its name in big letters on the tube, the console under it — an attract screen. */
@Composable
private fun ArcadeAttract(g: Game, detail: Boolean) {
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14104A), Color(0xFF05040F)))).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FitText(titles.display(g.name).uppercase(), Color(0xFFFFE15A), maxSize = if (detail) 20.sp else 10.sp, minSize = 6.sp, maxLines = 3,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center, style = TextStyle(fontFamily = pocketFont(), shadow = Shadow(Color(0xFFFF3D7F), Offset(2f, 2f), 0f)))
        if (detail) Text(titles.console(g.platform).uppercase(), color = Color(0xFF7FDBFF), fontSize = 9.sp, fontFamily = pocketFont(), maxLines = 1)
    }
}

/** The marquee's sign: the game's logo (Game names › Logos on), else its name in bold letters; dimmer while unlit. */
@Composable
private fun ArcadeMarqueeSign(c: StyleCtx, g: Game, lit: () -> Float, glow: Boolean, detail: Boolean) {
    val titles = LocalTitleOptions.current
    val logo = if (titles.showLogos) c.logo(g) else null
    var failed by remember(logo) { mutableStateOf(false) }
    // Update 348: a real marquee is printed artwork lit from behind — the game's wide art (cropped to the sign, its own
    // proportions kept) darkened under the logo; only on the machines near you (the far ones: the logo alone).
    val art = if (detail) (c.wide(g) ?: c.backdrop(g)) else null
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f + 0.45f * lit() }) {
        if (art != null) {
            AsyncImage(rememberStyleArtRequest(art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.45f)))))
        }
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp).graphicsLayer { alpha = 0.55f + 0.45f * lit() }, contentAlignment = Alignment.Center) {
        if (logo != null && !failed) {
            AsyncImage(logo, titles.display(g.name), contentScale = ContentScale.Fit, onError = { failed = true }, modifier = Modifier.fillMaxSize())
        } else {
            FitText(
                titles.display(g.name).uppercase(), Color.White, maxSize = if (detail) 22.sp else 10.sp, minSize = 5.sp, maxLines = 2,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                style = uiFontStyle().copy(fontStyle = FontStyle.Italic, shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), 6f)),
                lineHeightRatio = 1.05f
            )
        }
    }
}

// ---- The room ----

/**
 * Behind the whole screen (round the layout's own room — the status bar's strip, the edges): the room's wall and floor
 * colours, so no theme picture shows round it ("Your theme": nothing, Voltcap's background stays).
 */
@Composable
internal fun ArcadeBackdrop(modifier: Modifier) {
    val room = Features.arRoom.state.value
    if (room == Features.ArRoom.THEME) return
    val (top, bottom) = arcadeRoomEdges(room)
    Canvas(modifier) { drawRect(Brush.verticalGradient(listOf(top, bottom))) }
}

/** The arcade's room ([drawArcadeRoom]): the wall meeting the floor at [horizon] (share of the height). Static (its own layer). */
@Composable
private fun ArcadeRoomBackdrop(horizon: Float, accent: Color, modifier: Modifier, calm: Boolean = false) {
    val room = Features.arRoom.state.value
    Canvas(modifier.graphicsLayer()) { drawArcadeRoom(room, horizon, accent, calm) }
}

// ---- Showcase ----

/** Showcase: the big machine on the left, the game's info at the right, the row of small machines under it. */
@Composable
private fun ArcadeShowcase(c: StyleCtx, g: Game, launch: (Game) -> Unit, modifier: Modifier) {
    val color by rememberArcadeColor(c, g)
    val view = Features.arView.state.value
    val pick = Features.arCab.state.value
    BoxWithConstraints(modifier) {
        ArcadeRoomBackdrop(0.66f, color, Modifier.fillMaxSize(), calm = true)
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp)) {
            val compact = this@BoxWithConstraints.maxHeight < 380.dp
            val k = GameSize.scale(HomeLayout.ARCADE)
            val build = cabBuildFor(pick, g.platform)
            val big = CabView(view.yaw, view.elev)
            val aspect = cabAspect(cabModel(build), big)
            val fullH = this@BoxWithConstraints.maxHeight - 8.dp
            val fullW = this@BoxWithConstraints.maxWidth - 28.dp
            var cabH = fullH * 0.97f * k.coerceIn(0.7f, 1f)
            var cabW = cabH * aspect
            if (cabW > fullW * 0.44f) { cabW = fullW * 0.44f; cabH = cabW / aspect }
            val gap = if (compact) 16.dp else 30.dp
            val rightX = cabW + gap
            val rightW = (fullW - rightX).coerceAtLeast(120.dp)
            val miniH = (fullH * 0.36f * k).coerceIn(70.dp, 200.dp)
            val rowH = miniH + 14.dp
            val infoH = (fullH - rowH - 6.dp).coerceAtLeast(80.dp)
            // The light on the floor and wall round the big machine.
            Canvas(Modifier.size(cabW * 1.6f, fullH).offset(x = -cabW * 0.3f)) {
                drawRect(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.05f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.35f), radius = size.width * 0.75f))
            }
            val litAnim = remember { Animatable(1f) }
            val glow = Features.arcadeGlow.state.value
            LaunchedEffect(g.stableKey, glow) {
                if (!glow) { litAnim.snapTo(0.3f); return@LaunchedEffect }
                litAnim.snapTo(0.25f); litAnim.animateTo(1f, tween(520))
            }
            Box(
                Modifier.offset(y = fullH - cabH).categorySlide()
                    .editablePart(LayoutParts.id(HomeLayout.ARCADE, "cabinet"), "Big cabinet")
                    .pointerInput(Unit) { detectTapGestures { c.actions.open() } }
            ) {
                AnimatedContent(targetState = g, label = "arcadeBig", transitionSpec = { gameSwap() }) { shown ->
                    val b = cabBuildFor(pick, shown.platform)
                    val a = cabAspect(cabModel(b), big)
                    val hh = minOf(cabH, cabW / a)
                    Box(Modifier.size(cabW, cabH), contentAlignment = Alignment.BottomCenter) {
                        ArcadeMachine(c, shown, b, { color }, hh * a, hh, { big }, { litAnim.value }, { 0f }, live = shown.stableKey == g.stableKey, detail = true)
                    }
                }
            }
            Column(
                Modifier.offset(x = rightX).width(rightW).height(infoH)
                    .editablePart(LayoutParts.id(HomeLayout.ARCADE, "info"), "Name, Play & Details")
            ) {
                ArcadeCategory(c, compact)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                AnimatedContent(
                    targetState = g, label = "arcadeInfo", transitionSpec = { gameSwap() },
                    contentAlignment = Alignment.TopStart,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { shown -> ArcadeInfo(c, shown, richArcade(color), compact, plaque = compact) }   // (short screens: the plaque's 2 lines)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                ArcadeButtons(c, g, richArcade(color), compact, launch)
            }
            ArcadeRow(
                c, miniH,
                Modifier.offset(x = rightX, y = fullH - rowH).width(rightW).height(rowH)
                    .editablePart(LayoutParts.id(HomeLayout.ARCADE, "row"), "Row of cabinets")
            )
        }
    }
}

/**
 * The row of small machines: the one you're on (lit, a little bigger) a third of the way along, the games before it to
 * its left and the next ones to its right. It glides as you move; only the machines on screen (and one past each edge)
 * are composed, so big libraries stay light. Tap one to pick it, tap the one you're on to open it; swipe to move along.
 */
@Composable
private fun ArcadeRow(c: StyleCtx, miniH: Dp, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val loop = loopRowOn(c)
    val latestLoop by rememberUpdatedState(loop)
    val vf = rememberLoopFocus(c)
    val glide = Features.rowGlide.state.value
    val pos = remember { Animatable(vf.toFloat()) }
    val motion = Features.arMotion.state.value
    LaunchedEffect(vf) {
        if (!motion || abs(pos.value - vf) > 6f) pos.snapTo(vf.toFloat())
        else pos.animateTo(vf.toFloat(), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness / Features.arSpeed.value.k))
    }
    val whole by remember { derivedStateOf { kotlin.math.floor(pos.value).toInt() } }
    val pick = Features.arCab.state.value
    val view = Features.arView.state.value
    val small = CabView(view.yaw * 0.7f, view.elev)
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    val spacing = Features.arSpacing.state.value.k
    val miniW = miniH * 0.62f
    val latestMiniW by rememberUpdatedState(miniW)
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            var acc = 0f
            var target = -1
            detectHorizontalDragGestures(onDragStart = { acc = 0f; target = latestFocus }, onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
                change.consume(); acc += dx
                val stepPx = latestMiniW.toPx() * 1.1f
                val n = latestCount
                if (n > 0) {
                    if (target !in 0 until n) target = latestFocus.coerceIn(0, n - 1)
                    val was = target
                    while (acc <= -stepPx) { acc += stepPx; target = if (latestLoop) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1) }
                    while (acc >= stepPx) { acc -= stepPx; target = if (latestLoop) (target - 1).mod(n) else (target - 1).coerceAtLeast(0) }
                    if (target != was) select(target)
                }
            }
        }.categorySlide().clipToBounds()
    ) {
        val step = miniW * 1.18f * spacing
        val anchorX = maxWidth * 0.3f
        val floorY = maxHeight - 4.dp
        val before = (anchorX / step).toInt() + 2
        val after = ((maxWidth - anchorX) / step).toInt() + 2
        val first = (whole - before).let { if (loop) it else it.coerceAtLeast(0) }
        val last = (whole + 1 + after).let { if (loop) it else it.coerceAtMost(count - 1) }
        for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - whole, count, minOf(before, (count - 1) / 2))) }) {
            val real = if (loop) i.mod(count) else i
            val gg = c.games[real]
            val on = i == vf
            androidx.compose.runtime.key(if (loop) "${gg.stableKey}#${Math.floorDiv(i, count)}" else gg.stableKey) {
                val lift = styleLift(on && inGames)
                val b = cabBuildFor(pick, gg.platform)
                val col = arcadeColorOf(gg)
                ArcadeMachine(
                    c, gg, b, { col }, miniW, miniH, { small }, { if (on) 0.9f else 0.25f }, { if (on) 0f else 0.25f }, live = false, detail = false,
                    modifier = Modifier
                        .offset { IntOffset((anchorX.toPx() + step.toPx() * (i - pos.value) - miniW.toPx() / 2f).roundToInt(), (floorY - miniH).roundToPx()) }
                        .zIndex(if (on) 2f else 1f)
                        .graphicsLayer { val s = 1f + 0.1f * lift; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f) }
                        .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else select(real) } }
                )
            }
        }
    }
}

// ---- Arcade hall ----

/**
 * Arcade hall: the machines stand round the room in a gentle arc — the one you're on in the middle, stepping forward
 * and lit, the others further back and turned, their sides showing, dimmer the further they stand. The camera glides
 * along as you move (Layout options › Moving camera, Speed); the category is the hall's section, its sign over the
 * machines. The game's name, play time and achievements and Play / Details on a plaque at the bottom.
 */
@Composable
private fun ArcadeHallMode(c: StyleCtx, g: Game, launch: (Game) -> Unit, modifier: Modifier) {
    val color by rememberArcadeColor(c, g)
    val view = Features.arView.state.value
    val pick = Features.arCab.state.value
    val spacing = Features.arSpacing.state.value.k
    val motion = Features.arMotion.state.value && rememberAnimationsEnabled() && !LocalLiteEffects.current
    // (Update 348: a new camera angle eases in instead of jumping; read while drawing only.)
    val viewYaw = animateFloatAsState(view.yaw, if (motion) tween(600, easing = FastOutSlowInEasing) else snap(), label = "hallYaw")
    val viewElev = animateFloatAsState(view.elev, if (motion) tween(600, easing = FastOutSlowInEasing) else snap(), label = "hallElev")
    val speed = Features.arSpeed.state.value.k
    val loop = loopRowOn(c)
    val vf = rememberLoopFocus(c)
    val count = c.games.size
    val pos = remember { Animatable(vf.toFloat()) }
    LaunchedEffect(vf) {
        if (!motion || abs(pos.value - vf) > 6f) pos.snapTo(vf.toFloat())
        else pos.animateTo(vf.toFloat(), spring(dampingRatio = 0.92f, stiffness = 90f / speed))
    }
    // The machine you're on stepping forward (and back as you leave it).
    val step = remember { Animatable(1f) }
    LaunchedEffect(vf) {
        if (!motion) { step.snapTo(1f); return@LaunchedEffect }
        step.snapTo(0f); step.animateTo(1f, tween((520 * speed).toInt(), delayMillis = 120, easing = FastOutSlowInEasing))
    }
    val glow = Features.arcadeGlow.state.value
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(count)
    val latestLoop by rememberUpdatedState(loop)
    val whole by remember { derivedStateOf { kotlin.math.floor(pos.value).toInt() } }
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    BoxWithConstraints(modifier.clipToBounds().pointerInput(Unit) {
        var acc = 0f; var target = -1
        detectHorizontalDragGestures(onDragStart = { acc = 0f; target = latestFocus }, onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
            change.consume(); acc += dx
            val stepPx = 90.dp.toPx()
            val n = latestCount
            if (n > 0) {
                if (target !in 0 until n) target = latestFocus.coerceIn(0, n - 1)
                val was = target
                while (acc <= -stepPx) { acc += stepPx; target = if (latestLoop) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1) }
                while (acc >= stepPx) { acc -= stepPx; target = if (latestLoop) (target - 1).mod(n) else (target - 1).coerceAtLeast(0) }
                if (target != was) select(target)
            }
        }
    }) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
        val compact = maxHeight < 380.dp
        val hallW = maxWidth
        // The plaque's real height (measured); the machines stand on the floor just above it.
        var plaquePx by remember { mutableIntStateOf(0) }
        val signOn = CategoryTitle.captionOn(c.state.appearance)
        val floorY = hPx - (if (plaquePx > 0) plaquePx.toFloat() else with(density) { 92.dp.toPx() }) - with(density) { 18.dp.toPx() }
        // The horizon from the room left between the sign and the floor, so the machines fill it on any screen.
        val signB = with(density) { (if (!signOn) 16.dp else if (compact) 40.dp else 52.dp).toPx() }
        val horizon = ((floorY - (floorY - signB) * 0.54f) / hPx).coerceIn(0.2f, 0.6f)
        ArcadeRoomBackdrop(horizon, color, Modifier.fillMaxSize())
        // The camera: the machines on an arc round a point behind them; perspective from the eye at [eyeH] m.
        val k = GameSize.scale(HomeLayout.ARCADE).coerceIn(0.7f, 1.2f)
        val z0 = 3.4f / k
        val f = (floorY - hPx * horizon) * z0 / 1.15f   // the floor at z0 sits at floorY (eye 1.15 m above it)
        // (Update 348: a flatter arc — 10 m — so three machines each side fit on screen, side by side, turned to you.)
        val radius = 10f
        val maxPhi = 1.25f
        // (Update 348: each machine's place on the arc so no two overlap on screen — [hallSlots].)
        val slots = remember(z0, spacing, pick, view) {
            val builds = if (pick == Features.ArCab.MIXED) CabBuild.values().toList() else listOf(cabBuildFor(pick, null))
            val ms = builds.map { cabModel(it) }
            val wMax = ms.maxOf { m -> maxOf(m.w, m.boxes.maxOfOrNull { it.u1 - it.u0 } ?: 0f) }
            val dMax = ms.maxOf { it.d }
            hallSlots(radius, z0, wMax, dMax, 0.16f * spacing, view.yaw * 0.55f, 0.55f, maxPhi = maxPhi + 0.1f)
        }
        // The places that fit (the next ones are past the edge of the arc: faded out).
        val fits = ((slots.indexOfFirst { it >= maxPhi }.takeIf { it > 0 } ?: slots.size) - 1).coerceAtLeast(1)
        val span = fits + 2
        val first = (whole - span).let { if (loop) it else it.coerceAtLeast(0) }
        val last = (whole + 1 + span).let { if (loop) it else it.coerceAtMost(count - 1) }
        // The box each machine is drawn in: the size it has standing at z0 (it's scaled for its distance as it moves).
        val unitH = f / z0   // px per metre at z0
        val sectionLabel = c.categoryLabel
        // The section's sign, hanging over the machines (the layout's own category title — Home › Category name).
        if (signOn) CategoryTitle.Native(null)
        // (Update 348: an LED section sign hung from the ceiling on two rods.)
        if (signOn) Column(Modifier.align(Alignment.TopCenter).categorySlide().zIndex(50f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(if (compact) 120.dp else 180.dp).height(6.dp).drawBehind {
                drawLine(Color(0xFF3A3C42), Offset(size.width * 0.12f, 0f), Offset(size.width * 0.12f, size.height), 2f)
                drawLine(Color(0xFF3A3C42), Offset(size.width * 0.88f, 0f), Offset(size.width * 0.88f, size.height), 2f)
            })
            ArcadeLedSign(sectionLabel.uppercase(), if (compact) 15.sp else 20.sp, Modifier.widthIn(max = hallW * 0.6f))
        }
        // The pool of light on the floor in front of the machine you're on.
        Canvas(Modifier.fillMaxSize()) {
            val s = step.value
            val c0 = Offset(size.width / 2f, floorY + 6f)
            drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.32f * s), Color.Transparent), center = c0, radius = size.width * 0.22f),
                Offset(c0.x - size.width * 0.22f, c0.y - size.height * 0.06f), Size(size.width * 0.44f, size.height * 0.12f))
            // A beam from the ceiling.
            val beam = Path().apply { moveTo(size.width * 0.46f, 0f); lineTo(size.width * 0.54f, 0f); lineTo(size.width * 0.68f, floorY); lineTo(size.width * 0.32f, floorY); close() }
            drawPath(beam, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f * s), Color.Transparent), endY = floorY))
        }
        for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - whole, count, minOf(span, (count - 1) / 2))) }) {
            val real = if (loop) i.mod(count) else i
            val gg = c.games[real]
            val on = i == vf
            val b = cabBuildFor(pick, gg.platform)
            val model = cabModel(b)
            // The box at z0, wide enough for the machine seen turned.
            val boxH = model.h * unitH * 1.04f
            val boxW = boxH * 0.95f
            val boxWdp = with(density) { boxW.toDp() }; val boxHdp = with(density) { boxH.toDp() }
            val other = arcadeColorOf(gg)
            val col: () -> Color = if (on) ({ color }) else ({ other })
            // Where it stands now (read while placing / drawing): angle on the arc, depth, its turn.
            fun phi() = hallPhi(slots, i - pos.value)
            fun placeZ(): Float {
                val p = phi()
                val fwd = if (on) 0.55f * step.value else 0f
                return z0 + radius * (1f - cos(p)) - fwd
            }
            fun placeX(): Float = radius * sin(phi())
            val viewOf: () -> CabView = {
                val p = phi(); val x = placeX(); val z = placeZ()
                // Facing out from the arc's centre; seen from the eye: its turn is the difference.
                val yaw = Math.toDegrees((atan2(x, z) - p).toDouble()).toFloat() + viewYaw.value * 0.55f
                CabView(yaw.coerceIn(-55f, 55f), viewElev.value + 3f)
            }
            androidx.compose.runtime.key(if (loop) "${gg.stableKey}#${Math.floorDiv(i, count)}" else gg.stableKey) {
                val lit = remember { Animatable(if (on) 1f else 0.2f) }
                LaunchedEffect(on, glow) { lit.animateTo(if (on && glow) 1f else if (on) 0.45f else 0.2f, tween(450)) }
                ArcadeMachine(
                    c, gg, b, col, boxWdp, boxHdp, viewOf, { lit.value },
                    { (abs(i - pos.value) / (fits + 1f) * 0.6f + if (on) 0f else 0.12f).coerceIn(0f, 0.8f) },
                    live = on, detail = on || abs(i - vf) <= 1,
                    modifier = Modifier
                        .layout { m, _ -> val pl = m.measure(Constraints.fixed(boxW.roundToInt(), boxH.roundToInt())); layout(pl.width, pl.height) { pl.place(0, 0) } }
                        // (Nearer in front; by places from the one you're on, so the glide doesn't recompose it.)
                        .zIndex(30f - abs(i - vf).toFloat())
                        .graphicsLayer {
                            val z = placeZ()
                            val s = z0 / z
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            // Bottom centre of the box on the floor at (x, z).
                            val sx = wPx / 2f + placeX() * f / z
                            val sy = hPx * horizon + 1.15f * f / z
                            translationX = sx - boxW / 2f
                            translationY = sy - boxH
                            alpha = (fits + 0.85f - abs(i - pos.value)).coerceIn(0f, 1f)
                        }
                        .pointerInput(real) { detectTapGestures { if (real == GridHome.focus) open() else select(real) } }
                )
            }
        }
        // The plaque: the game, and Play / Details.
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp).zIndex(60f)
                .onSizeChanged { plaquePx = it.height + with(density) { 6.dp.roundToPx() } }
                .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.62f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .editablePart(LayoutParts.id(HomeLayout.ARCADE, "info"), "Name, Play & Details"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.widthIn(max = hallW * 0.42f)) {
                AnimatedContent(targetState = g, label = "hallInfo", transitionSpec = { gameSwap() }) { shown -> ArcadeInfo(c, shown, richArcade(color), compact, plaque = true) }
            }
            Spacer(Modifier.width(12.dp))
            ArcadeButtons(c, g, richArcade(color), compact, launch)
        }
    }
}

// ---- Words and buttons ----

/** The category you're in and where you are in it, like a high-score line; ◀ ▶ tap through the categories. */
@Composable
private fun ArcadeCategory(c: StyleCtx, compact: Boolean) {
    val step by rememberUpdatedState(c.actions.category)
    val size = if (compact) 11.sp else 13.sp
    // (Update 348: an LED readout — the section and where you are in it, like a machine's credit display.)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (c.stops.size > 1) {
            Text("◀", color = ArcadeLed.copy(alpha = 0.8f), fontSize = size, modifier = Modifier.tapOnly { step(-1) }.padding(end = 6.dp, top = 6.dp, bottom = 6.dp))
        }
        CategoryTitle.Native() // (Update 212: the layout's own category title.)
        ArcadeLedSign(c.categoryLabel.uppercase() + "   ${c.focus + 1}/${c.games.size}", size, Modifier.weight(1f, fill = false))
        if (c.stops.size > 1) {
            Text("▶", color = ArcadeLed.copy(alpha = 0.8f), fontSize = size, modifier = Modifier.tapOnly { step(1) }.padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 4.dp))
        }
    }
}

/** The arcade's LED colour (amber, as the status bar's readout). */
internal val ArcadeLed = Color(0xFFFFB84D)

/**
 * Update 348: an LED sign — amber pixel letters glowing on a smoked panel in a dark metal frame, the dot matrix showing
 * through (fine dark lines over the letters). Static (drawn once).
 */
@Composable
private fun ArcadeLedSign(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.5.dp, Brush.verticalGradient(listOf(Color(0xFF5A5D65), Color(0xFF26282D))), RoundedCornerShape(4.dp))
            .background(Color(0xF00A0A0D), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        FullText(
            text, color = ArcadeLed, fontSize = size, fontFamily = pocketFont(), maxLines = 1,
            letterSpacing = 1.5.sp, style = TextStyle(shadow = Shadow(Color(0xFFFF8A1E).copy(alpha = 0.85f), Offset.Zero, 14f)),
            modifier = Modifier.drawWithContent {
                drawContent()
                val pitch = 2.dp.toPx()
                var x = 0f
                while (x < this.size.width) { drawLine(Color(0xFF0A0A0D).copy(alpha = 0.55f), Offset(x, 0f), Offset(x, this.size.height), 0.7f); x += pitch }
                var y = 0f
                while (y < this.size.height) { drawLine(Color(0xFF0A0A0D).copy(alpha = 0.55f), Offset(0f, y), Offset(this.size.width, y), 0.7f); y += pitch }
            }
        )
    }
}

/** The game's name, console, time played (and last played) and its achievements. [plaque]: the hall's short version. */
@Composable
private fun ArcadeInfo(c: StyleCtx, g: Game, trim: Color, compact: Boolean, plaque: Boolean = false) {
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxWidth()) {
        FitText(
            titles.display(g.name), Color.White, maxSize = if (plaque) 18.sp else if (compact) 22.sp else 30.sp, minSize = 12.sp, maxLines = if (plaque) 1 else 2,
            fontWeight = FontWeight.Bold, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Spacer(Modifier.height(if (plaque) 1.dp else 4.dp))
        val last = c.lastPlayed(g)
        FullText(
            (if (g.sourceType == com.shelf.launcher.data.SourceType.APP) "Android" else titles.console(g.platform)) +
                if (plaque) "  ·  " + stylePlaytime(c.playtime(g), last != null) else "",
            color = lerp(trim, Color.White, 0.35f), fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        if (!plaque) {
            Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
            Text(
                stylePlaytime(c.playtime(g), last != null) + (last?.let { "  ·  Last played: " + lastPlayedLabel(it) } ?: ""),
                color = Color.White.copy(alpha = 0.82f), fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, softWrap = false,
                style = nameTextStyle(NameBackdrop.SHADOW)
            )
        }
        val p = c.progress(g).takeUnless { plaque && compact }   // (short screens: the room for the machines instead)
        if (p != null) {
            Spacer(Modifier.height(if (compact || plaque) 2.dp else 4.dp))
            GameProgressChip(p.first, p.second, Modifier.offset(x = (-5).dp))
        }
    }
}

/** Play / Continue and Details as round arcade push buttons with their names (▼ reaches them). */
@Composable
private fun ArcadeButtons(c: StyleCtx, g: Game, trim: Color, compact: Boolean, launch: (Game) -> Unit) {
    val open by rememberUpdatedState(c.actions.open)
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ArcadePush(if (c.lastPlayed(g) != null) "Continue" else "Play", trim, StyleNav.on(StyleNav.Zone.BAR, 0), compact) {
            StyleNav.tapped(StyleNav.Zone.BAR, 0); launch(g)
        }
        ArcadePush("Details", Color(0xFFF2C14E), StyleNav.on(StyleNav.Zone.BAR, 1), compact) {
            StyleNav.tapped(StyleNav.Zone.BAR, 1); open()
        }
    }
}

/** One arcade push button: a round coloured button with a shine, its name beside it; picked = ringed and a bit bigger. */
@Composable
private fun ArcadePush(label: String, color: Color, on: Boolean, compact: Boolean, onTap: () -> Unit) {
    val d = if (compact) 26.dp else 34.dp
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .graphicsLayer { val s = if (on) 1.06f else 1f; scaleX = s; scaleY = s }
            .clip(pill)
            .background(Color.Black.copy(alpha = if (on) 0.62f else 0.42f))
            .border(if (on) 2.dp else 1.dp, if (on) color else Color.White.copy(alpha = 0.22f), pill)
            .tapOnly(onTap)
            .padding(start = 4.dp, end = if (compact) 12.dp else 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(d)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF16171B), radius = r)
            drawCircle(
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.35f)),
                    center = center - Offset(r * 0.25f, r * 0.25f), radius = r * 0.95f
                ),
                radius = r * 0.76f
            )
        }
        Spacer(Modifier.width(if (compact) 6.dp else 9.dp))
        Text(
            label.uppercase(), color = Color.White, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false
        )
    }
}
