package com.shelf.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.shelf.launcher.ui.theme.ShelfColors
import com.shelf.launcher.ui.theme.ThemeMotif

/**
 * The theme's animated scene behind the home screen (Appearance › Behind the dock › Animated theme).
 * Everything is drawn in code from the theme's colours: no images, nothing borrowed.
 *
 * Android 13+: each scene is a GPU shader (ThemeShaders, generated from tools/themes/scenes) —
 * layered light, fog, water, fire, sky, all computed per pixel. Older Android, or a shader that
 * fails to build on some GPU, falls back to the simpler Canvas scenes (ThemeSceneLegacy).
 *
 * Cheap on purpose: only while Voltcap is resumed with the screen on, and a still frame with lite effects or the
 * system's animations off. (Update 126: about 60 frames a second on Automatic, stepping down only if the device
 * drops frames or the home screen sits idle.)
 */
@Composable
fun ThemeScene(
    motif: ThemeMotif,
    colors: ShelfColors,
    modifier: Modifier = Modifier,
    /** "Now playing" only: where its equalizer wave runs, 0..1 from the top (clear of the game row). */
    waveY: Float = 0.5f,
    /** Music-reactive only: how much room (share of the screen height, each side) the visualizer has. */
    waveH: Float = 0.1f,
    /** Music-reactive only: across, 0..1 from the left (0 = the middle). */
    waveX: Float = 0f
) {
    // Your own theme / visualizer: only while a valid, compiled, switched-on pack is chosen; otherwise the Voltcap theme / Now playing.
    @Suppress("NAME_SHADOWING") val motif = rememberEffectiveMotif(motif)
    // The music-reactive themes ("Now playing", Spectrum, Pulse ring, Beat tunnel) have their own
    // pipeline: the cover's colours and the music's levels.
    if (motif.lyrics) {
        // The lyric themes: their stage (a music scene like the others) with the words drawn over it —
        // scattered (Lyric cloud), karaoke, the scrolling sheet or neon.
        androidx.compose.foundation.layout.Box(modifier) {
            // Update 119: over the song's own cover (blurred, dimmed), when chosen and there is one.
            val cover = if (com.shelf.launcher.data.Features.lyricCoverBg.state.value) MusicTheme.sharpArt ?: MusicTheme.softArt else null
            if (cover != null) LyricCoverBackdrop(cover, Modifier.fillMaxSize())
            else NowPlayingScene(colors, Modifier.fillMaxSize(), waveY, motif, waveH, waveX)
            if (motif == ThemeMotif.LYRIC_CLOUD) LyricScatterLayer(colors, Modifier.fillMaxSize())
            else LyricStageLayer(motif, colors, Modifier.fillMaxSize())
        }
    } else if (motif == ThemeMotif.RETRO_TV && MusicTheme.nowPlaying == null) {
        // Retro TV: the album cover while music plays, otherwise the game you're on in the CRT room's showcase.
        ShowcaseScene(ThemeMotif.SHOWCASE_CRT, colors, modifier)
    } else if (motif == ThemeMotif.RETRO_TV && android.os.Build.VERSION.SDK_INT >= 33) {
        // The CRT room's 3D TV with the song's cover (the old flat TV only where that shader can't run).
        if (!MusicCrtRoom(colors, modifier)) NowPlayingScene(colors, modifier, waveY, motif, waveH, waveX)
    } else if (motif.reactive) {
        NowPlayingScene(colors, modifier, waveY, motif, waveH, waveX)
    } else if (motif == ThemeMotif.RETRO_BOUNCE) {
        // The pixel stage, with the controller parts and console icons bouncing on top.
        androidx.compose.foundation.layout.Box(modifier) {
            CodeScene(motif, colors, Modifier.fillMaxSize())
            RetroBounceLayer(colors, Modifier.fillMaxSize())
        }
    } else if (motif == ThemeMotif.BADGE_BOUNCE) {
        // The velvet backdrop, with your earned badges bouncing round on top.
        androidx.compose.foundation.layout.Box(modifier) {
            CodeScene(motif, colors, Modifier.fillMaxSize())
            BadgeBounceLayer(Modifier.fillMaxSize())
        }
    } else if (motif.showcase) {
        ShowcaseScene(motif, colors, modifier)
    } else if (motif == ThemeMotif.PIXEL_FALLS) {
        // A pixel-art picture with its own frames (8 frames, 100 ms each), played crisply.
        FramesScene(com.shelf.launcher.R.drawable.scene_falls, frames = 8, frameMs = 100L, modifier = modifier)
    } else {
        CodeScene(motif, colors, modifier)
    }
}

@Composable
internal fun CodeScene(motif: ThemeMotif, colors: ShelfColors, modifier: Modifier) {
    // Lite normally freezes the scene; "Animate on Lite" (Appearance › Behind the dock) keeps it moving.
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    // Covered by the screensaver, settings or the details window: hold a still frame.
    // Static themes are one still picture: drawn once, no clock at all. (Update 126: calm ones run at the full rate too.)
    // A music-reactive scene keeps moving the whole time music plays (never frozen by Lite / a window on top).
    val musicOn = motif.reactive && MusicTheme.nowPlaying?.isPlaying == true
    val active = !motif.still && rememberIsActive() && (musicOn || (rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current))
    val time = remember { mutableFloatStateOf(STILL_FRAME_TIME) }
    val tuning = LocalSceneTuning.current
    // A custom theme pack runs at up to ~60 fps (30 once it proved heavy) and is watched by PackGuard.
    val pack = motif.packKind()
    if (pack != null) PackGuard.Watch(motif)
    val packMs = if (pack != null) PackGuard.frameMs() else 0
    // (Update 126: calm themes no longer held at ~15 fps on Automatic: they move at the same rate as the others.)
    SceneClock(active, time, maxOf(packMs, if (musicOn) 0 else idleFrameMs()), tuning.speed)
    val revision = if (motif.custom) CustomPacks.revision.intValue else 0
    val scene = if (Build.VERSION.SDK_INT >= 33) remember(motif, revision) { SceneShader.create(motif) } else null
    // Holo foil follows the handheld's tilt (motion sensor on only while it's on screen).
    val tilt = if (motif == ThemeMotif.HOLO) rememberDeviceTilt(active) else null
    // Trophy vault / Badge constellations: your own mastered games and latest badges (TrophyShelf).
    val trophies = if ((motif == ThemeMotif.TROPHY_VAULT || motif == ThemeMotif.BADGE_STARS) && scene != null) rememberTrophyAtlas() else null
    // Specimen lab / Console disc: the game's own cartridge or disc; Poster wall: the row's covers (SceneArt).
    val media = if (motif in SceneArt.MEDIA_MOTIFS && scene != null) rememberSceneMedia() else null
    val covers = if (motif == ThemeMotif.POSTER_WALL && scene != null) rememberRowCovers() else null
    // Scenes with a subject in the middle move it down, clear of a game row at the top (and by the theme's own
    // Scene position); the bands to keep clear come from the home screen.
    val avoid = LocalSceneAvoid.current
    val shiftOn = scene != null && SceneArt.usesShift(motif)
    // The painted scenes: the user's painting, with the shader's motion on top (ScenePainting).
    val painting = if (scene != null && "uPaint" in SceneSource.children(motif)) rememberPainting(motif) else null
    val paintRes = ScenePainting.resOf(motif)
    if (scene != null) {
        ShaderScene(scene, colors, time, modifier, tilt, tuning, pack, trophies, media, covers, if (shiftOn) avoid else null, motif != ThemeMotif.XMB_WAVE, painting)
    } else if (paintRes != null) {
        androidx.compose.foundation.layout.Box(modifier) {
            PaintingStill(paintRes, Modifier.fillMaxSize())
            if (tuning.dim > 0f) Spacer(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = tuning.dim)) })
        }
    } else {
        LegacyThemeScene(motif, colors, time, modifier)
        if (tuning.dim > 0f) Spacer(modifier.drawBehind { drawRect(Color.Black.copy(alpha = tuning.dim)) })
    }
}

/** Resolved background quality (never AUTO): resolution share and frame interval for the scenes. */
val LocalSceneQuality = androidx.compose.runtime.compositionLocalOf { com.shelf.launcher.data.SceneQuality.HIGH }

/**
 * Noise octaves the scenes may skip (uLod): at Low and Lowest the scene is drawn at 40 / 28 % of the
 * screen's resolution, where the finest noise is smaller than a pixel anyway — skipping it saves a
 * good part of each pixel's work on the weaker GPUs.
 */
internal val com.shelf.launcher.data.SceneQuality.lod: Float
    get() = when {
        scale <= 0.3f -> 2f
        scale <= 0.45f -> 1f
        else -> 0f
    }

/**
 * Draws the scene into its own cached layer at [scale] of the screen resolution and stretches it to
 * fill. Two wins on weaker handhelds: far fewer pixels for the shader each frame, and the scene is no
 * longer re-drawn every time something else on the home screen animates (the focused card's glow
 * used to re-run the whole full-screen shader each frame, even for a still frame).
 */
internal fun Modifier.sceneLayer(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth.coerceAtLeast(1)
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight.coerceAtLeast(1)
        val s = scale.coerceIn(0.2f, 1f)
        val sw = (w * s).toInt().coerceAtLeast(1)
        val sh = (h * s).toInt().coerceAtLeast(1)
        val p = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(sw, sh))
        layout(w, h) {
            p.placeWithLayer(0, 0) {
                scaleX = w.toFloat() / sw
                scaleY = h.toFloat() / sh
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
        }
    }
)

/** The main display's refresh rate (60 when unknown). */
internal fun displayHz(context: android.content.Context): Float = runCatching {
    @Suppress("DEPRECATION")
    (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.refreshRate
}.getOrDefault(60f)

/**
 * The slowest frame interval allowed right now: ~30 fps once the home screen has sat idle for a while,
 * ~15 fps after a few minutes (see [HomeIdle]), and this screen's own limit ([LocalSceneMinFrameMs]: a
 * popup partly over it, an unused second screen); 0 = no limit beyond the background quality.
 */
@Composable
internal fun idleFrameMs(): Int {
    // Update 125: a fixed frame rate (Settings › Background frame rate) or the screensaver's theme: no battery-saving steps.
    if (com.shelf.launcher.data.Features.sceneFps.state.value != com.shelf.launcher.data.Features.SceneFps.AUTO || LocalSceneFullSpeed.current) return 0
    return maxOf(LocalSceneMinFrameMs.current, when {
        // Calmer when nobody's touching it, but still smooth (a theme is something you look at).
        HomeIdle.deep -> 66
        HomeIdle.idle -> 33
        else -> 0
    })
}

/** True when the user wants the animated background to keep moving although Lite effects are on. */
val LocalAnimateOnLite = androidx.compose.runtime.compositionLocalOf { false }

/** A moment where every scene looks good, for the still frame. */
internal const val STILL_FRAME_TIME = 6f

/**
 * The scenes' frame interval (ms) before idling: the fixed rate picked in Settings › Background frame rate, else the
 * background quality's own (update 125; update 126: the music visualizers follow it too).
 */
@Composable
internal fun sceneFrameMs(): Int {
    val fixed = com.shelf.launcher.data.Features.sceneFps.state.value
    return if (fixed == com.shelf.launcher.data.Features.SceneFps.AUTO) LocalSceneQuality.current.frameMs else fixed.frameMs
}

/**
 * Advances [time] once per frame interval while [active] (~60 or ~30 times a second); it carries on from where it
 * stopped. The 14 / 28 ms thresholds (not 16 / 33) keep an even rate on 60/90/120 Hz screens despite frame jitter.
 */
@Composable
internal fun SceneClock(active: Boolean, time: MutableFloatState, idleMs: Int = 0, speed: Float = 1f) {
    val frameNs = maxOf(sceneFrameMs(), idleMs) * 1_000_000L
    LaunchedEffect(active, frameNs, speed) {
        if (!active) return@LaunchedEffect
        val base = time.floatValue
        val start = withFrameNanos { it }
        var last = 0L
        // After a step, sleep most of the interval: the loop then wakes once or twice per step instead of
        // on every display frame (90 / 120 Hz screens), and the pacing stays even.
        val nap = (frameNs / 1_000_000L - 10L).coerceAtLeast(0L)
        while (true) {
            val stepped = withFrameNanos { now ->
                if (now - last >= frameNs) {
                    last = now
                    // Wrapped so the shaders never see huge numbers (precision); one jump an hour.
                    time.floatValue = (base + (now - start) / 1_000_000_000f * speed) % 3600f
                    true
                } else false
            }
            if (stepped && nap > 0L) kotlinx.coroutines.delay(nap)
        }
    }
}

@RequiresApi(33)
@Composable
private fun ShaderScene(
    scene: SceneShader, colors: ShelfColors, time: State<Float>, modifier: Modifier,
    tilt: State<androidx.compose.ui.geometry.Offset>? = null, tuning: SceneTuning = SceneTuning(), pack: PackKind? = null,
    trophies: State<TrophyShelf.Atlas?>? = null,
    media: State<SceneArt.Media?>? = null,
    covers: State<SceneArt.Covers?>? = null,
    /** Bands to keep the scene's subject clear of (null: the scene doesn't move). */
    avoid: List<ClosedFloatingPointRange<Float>>? = null,
    /** The usual darker band at the bottom (the Wave menu stays even all the way down). */
    shade: Boolean = true,
    painting: State<ScenePainting?>? = null
) {
    val quality = LocalSceneQuality.current
    // A custom theme is drawn at a capped resolution, whatever the background quality says.
    val fullScale = if (pack != null) PackGuard.scale(quality.scale, pack) else quality.scale
    // Update 121, Appearance › Half-resolution scenes: at most half the screen's resolution (a quarter of the
    // pixels each frame). The scenes are soft light, noise and glow, so it hardly shows.
    val scale = if (com.shelf.launcher.data.Features.sceneHalfRes.state.value) minOf(fullScale, 0.5f) else fullScale
    // The layer is drawn smaller than the screen: the bands to avoid are in screen pixels.
    var fullH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val focus = if (avoid == null) null else androidx.compose.animation.core.animateFloatAsState(
        SceneArt.shiftFocus(avoid, fullH, tuning.shift), androidx.compose.animation.core.tween(700), label = "sceneShift"
    )
    Spacer(
        modifier.onSizeChanged { fullH = it.height.toFloat() }.sceneLayer(scale).drawWithCache {
            val brush = ShaderBrush(scene.shader)
            onDrawBehind {
                // Reading time here only redraws; nothing recomposes per frame.
                scene.update(size.width, size.height, time.value, colors, quality.lod, tuning.particles)
                if (tilt != null) { val v = tilt.value; scene.tilt(v.x, v.y) }
                if (trophies != null) scene.trophies(trophies.value)
                if (media != null) scene.media(media.value)
                if (covers != null) scene.covers(covers.value)
                if (focus != null) scene.focus(focus.value)
                if (painting != null) scene.painting(painting.value)
                drawRect(brush)
                if (shade) drawSceneShade()
                if (tuning.dim > 0f) drawRect(Color.Black.copy(alpha = tuning.dim))
            }
        }
    )
}

/** Darker towards the bottom, where the row and its text sit. */
internal fun DrawScope.drawSceneShade() {
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            startY = size.height * 0.35f,
            endY = size.height
        )
    )
}

/** One compiled scene with its uniforms. One per ThemeScene on screen (the picker preview has its own). */
@RequiresApi(33)
internal class SceneShader private constructor(
    val shader: RuntimeShader,
    private val used: Set<String>,
    /** Pictures the scene samples (ThemeShaders.children): only these may be set. */
    private val kids: Set<String> = emptySet()
) {
    // Uniforms a GPU driver optimised away; skipped instead of throwing every frame.
    private val missing = HashSet<String>()
    private var lastPicture: android.graphics.Shader? = null
    private var lastRoom: android.graphics.Shader? = null
    private var lastTrophies: android.graphics.Shader? = null
    private var lastPoster: android.graphics.Shader? = null
    private var lastMedia: android.graphics.Shader? = null
    private var lastCovers: android.graphics.Shader? = null
    private var lastPaint: android.graphics.Shader? = null

    init {
        // A scene that samples a picture starts with an empty one, so it never draws with none set.
        kids.forEach { name -> runCatching { shader.setInputShader(name, emptyPicture()) } }
    }

    /**
     * CRT room: the picture on the TV ([pic] null = the test card), the average colour of its left and
     * right half (the room's light), and extra static (0..1) after a new game was picked.
     */
    fun tvPicture(pic: android.graphics.BitmapShader?, w: Int, h: Int, left: FloatArray?, right: FloatArray?, tune: Float) {
        if ("uShot" in kids && pic != null && pic !== lastPicture) {
            runCatching { shader.setInputShader("uShot", pic) }
            lastPicture = pic
        }
        set("uHasShot") { shader.setFloatUniform("uHasShot", if (pic != null) 1f else 0f) }
        set("uShotSize") { shader.setFloatUniform("uShotSize", w.coerceAtLeast(1).toFloat(), h.coerceAtLeast(1).toFloat()) }
        if (left != null) set("uTvL") { shader.setFloatUniform("uTvL", left[0], left[1], left[2]) }
        if (right != null) set("uTvR") { shader.setFloatUniform("uTvR", right[0], right[1], right[2]) }
        set("uTune") { shader.setFloatUniform("uTune", tune) }
    }

    /**
     * Gaming room: the painted room ([pic], decoded at [w] × [h]) that the monitor sits in, and where down the
     * screen (0..1) the monitor goes. Cheap to repeat.
     */
    fun roomPicture(pic: android.graphics.BitmapShader, w: Int, h: Int, focusY: Float) {
        if ("uRoom" in kids && pic !== lastRoom) {
            runCatching { shader.setInputShader("uRoom", pic) }
            lastRoom = pic
        }
        set("uRoomSize") { shader.setFloatUniform("uRoomSize", w.coerceAtLeast(1).toFloat(), h.coerceAtLeast(1).toFloat()) }
        set("uFocusY") { shader.setFloatUniform("uFocusY", focusY) }
    }

    /** Trophy vault: your trophies side by side ([a] null = none: the drawn badges). */
    fun trophies(a: TrophyShelf.Atlas?) {
        if (a != null && "uTrophies" in kids && a.shader !== lastTrophies) {
            runCatching { shader.setInputShader("uTrophies", a.shader) }
            lastTrophies = a.shader
        }
        set("uTrophySize") { shader.setFloatUniform("uTrophySize", (a?.w ?: 1).toFloat(), (a?.h ?: 1).toFloat()) }
        set("uTrophyN") { shader.setFloatUniform("uTrophyN", (a?.count ?: 0).toFloat()) }
        set("uMasteredN") { shader.setFloatUniform("uMasteredN", (a?.mastered ?: 0).toFloat()) }
    }

    /** Gaming room: the game's cover on the framed poster ([pic] null = the painting's own poster). */
    fun poster(pic: android.graphics.BitmapShader?, w: Int, h: Int) {
        if (pic != null && "uPoster" in kids && pic !== lastPoster) {
            runCatching { shader.setInputShader("uPoster", pic) }
            lastPoster = pic
        }
        set("uHasPoster") { shader.setFloatUniform("uHasPoster", if (pic != null) 1f else 0f) }
        set("uPosterSize") { shader.setFloatUniform("uPosterSize", w.coerceAtLeast(1).toFloat(), h.coerceAtLeast(1).toFloat()) }
    }

    /** Specimen lab / Console disc: the game's cartridge or disc picture, or its cover ([m] null = the scene's own). */
    fun media(m: SceneArt.Media?) {
        if (m != null && "uMedia" in kids && m.shader !== lastMedia) {
            runCatching { shader.setInputShader("uMedia", m.shader) }
            lastMedia = m.shader
        }
        set("uHasMedia") { shader.setFloatUniform("uHasMedia", if (m != null) 1f else 0f) }
        set("uMediaKind") { shader.setFloatUniform("uMediaKind", (m?.kind ?: 0).toFloat()) }
        set("uMediaSize") { shader.setFloatUniform("uMediaSize", (m?.w ?: 1).toFloat(), (m?.h ?: 1).toFloat()) }
    }

    /** Poster wall: the row's covers ([a] null = only the original posters). */
    fun covers(a: SceneArt.Covers?) {
        if (a != null && "uCovers" in kids && a.shader !== lastCovers) {
            runCatching { shader.setInputShader("uCovers", a.shader) }
            lastCovers = a.shader
        }
        set("uCoversSize") { shader.setFloatUniform("uCoversSize", (a?.w ?: 1).toFloat(), (a?.h ?: 1).toFloat()) }
        set("uCoverN") { shader.setFloatUniform("uCoverN", (a?.count ?: 0).toFloat()) }
    }

    /** The painted scenes: the painting ([p] null while it decodes: the scene's own plain stand-in shows). */
    fun painting(p: ScenePainting?) {
        if (p != null && "uPaint" in kids && p.shader !== lastPaint) {
            runCatching { shader.setInputShader("uPaint", p.shader) }
            lastPaint = p.shader
        }
        set("uHasPaint") { shader.setFloatUniform("uHasPaint", if (p != null) 1f else 0f) }
        set("uPaintSize") { shader.setFloatUniform("uPaintSize", (p?.w ?: 1).toFloat(), (p?.h ?: 1).toFloat()) }
    }

    /** 0.5 + how far down to move the scene's subject (SceneArt.shiftFocus). */
    fun focus(f: Float) = set("uFocusY") { shader.setFloatUniform("uFocusY", f) }

    fun update(w: Float, h: Float, t: Float, c: ShelfColors, lod: Float = 0f, particles: Float = 1f) {
        set("uRes") { shader.setFloatUniform("uRes", w, h) }
        set("uTime") { shader.setFloatUniform("uTime", t) }
        set("uLod") { shader.setFloatUniform("uLod", lod) }
        set("uDensity") { shader.setFloatUniform("uDensity", particles + 1f) }
        color("uBg", c.background)
        color("uSurf", c.surface)
        color("uAcc", c.accent)
        color("uSec", c.secondary)
    }

    fun tilt(x: Float, y: Float) = set("uTilt") { shader.setFloatUniform("uTilt", x, y) }

    private fun color(name: String, col: Color) = set(name) {
        shader.setFloatUniform(name, col.red, col.green, col.blue, 1f)
    }

    private inline fun set(name: String, block: () -> Unit) {
        // Only uniforms the scene really reads: the compiler drops the rest, and on Android 13 setting a
        // dropped one aborts the whole app instead of throwing (ThemeShaders.uniforms).
        if (name !in used || name in missing) return
        try { block() } catch (_: IllegalArgumentException) { missing += name }
    }

    companion object {
        /** Scenes that failed to compile on this device — not retried this session. */
        private val broken = HashSet<ThemeMotif>()

        private var empty: android.graphics.Bitmap? = null

        private fun emptyPicture(): android.graphics.BitmapShader {
            val b = empty ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).also { empty = it }
            return android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        }

        fun create(motif: ThemeMotif): SceneShader? {
            // A custom pack is checked and test-compiled before it gets here (CustomPacks.active); one that still
            // fails just returns null (no "broken" memory, since the person can edit and re-add it).
            if (!motif.custom && motif in broken) return null
            return try {
                SceneShader(RuntimeShader(SceneSource.source(motif)), SceneSource.uniforms(motif), SceneSource.children(motif))
            } catch (e: Throwable) {
                if (!motif.custom) broken += motif
                android.util.Log.w("ThemeScene", "Scene $motif fell back to Canvas", e)
                null
            }
        }
    }
}

/**
 * How far the handheld is tilted from where it rested when this started (-1..1 each way), smoothed.
 * The motion sensor is registered only while [active]; devices without one just report zero.
 * Read the state while drawing, so the scene redraws without recomposing.
 */
@Composable
internal fun rememberDeviceTilt(active: Boolean): State<androidx.compose.ui.geometry.Offset> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tilt = remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.runtime.DisposableEffect(active) {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (!active || sm == null || sensor == null) return@DisposableEffect onDispose { }
        var baseX = Float.NaN
        var baseY = 0f
        var sx = 0f
        var sy = 0f
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                // Landscape handhelds: the screen's x runs along the sensor's x.
                val gx = e.values[0] / 9.81f
                val gy = e.values[1] / 9.81f
                if (baseX.isNaN()) { baseX = gx; baseY = gy }
                sx += ((gx - baseX).coerceIn(-1f, 1f) - sx) * 0.15f
                sy += ((gy - baseY).coerceIn(-1f, 1f) - sy) * 0.15f
                // Let the resting point follow slowly, so a new way of holding it becomes "level".
                baseX += (gx - baseX) * 0.002f
                baseY += (gy - baseY) * 0.002f
                tilt.value = androidx.compose.ui.geometry.Offset(-sx * 2.5f, sy * 2.5f)
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}

/**
 * The home's scene handed to the second screen ("Animate only on the second screen"): set by the main
 * screen's background while that option is on and a second screen is in use, null otherwise.
 */
internal object SecondScene {
    var motif by androidx.compose.runtime.mutableStateOf<com.shelf.launcher.ui.theme.ThemeMotif?>(null)
        private set
    var colors by androidx.compose.runtime.mutableStateOf<com.shelf.launcher.ui.theme.ShelfColors?>(null)
        private set

    fun set(m: com.shelf.launcher.ui.theme.ThemeMotif?, c: com.shelf.launcher.ui.theme.ShelfColors?) {
        if (motif != m) motif = m
        if (m != null && colors != c) colors = c
    }
}

/** Update 119: a lyric theme's backdrop of the song's cover — filling the screen, softened and dimmed so the words read. */
@Composable
private fun LyricCoverBackdrop(cover: android.graphics.Bitmap, modifier: Modifier) {
    // Update 121: blurred once (small, stretched smooth) instead of a full-screen blur under every frame of words.
    val img = rememberBakedBlur(cover, 18.dp)
    androidx.compose.foundation.layout.Box(modifier) {
        if (img != null) androidx.compose.foundation.Image(
            img, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
            modifier = Modifier.fillMaxSize()
        )
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.62f)))
            )
        )
    }
}
