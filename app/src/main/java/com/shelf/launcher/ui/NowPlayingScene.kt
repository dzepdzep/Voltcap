package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AudioReactive
import com.shelf.launcher.data.LyricsOptIn
import com.shelf.launcher.data.LyricsRepository
import com.shelf.launcher.data.MusicLyricsStyle
import com.shelf.launcher.data.MusicThemeSettings
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfColors
import com.shelf.launcher.ui.theme.ThemeMotif
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The "Now playing" theme's background: the song's colours (and, if wanted, its cover, very blurred)
 * with an equalizer wave across it that follows eight frequency bands, kicks and highs.
 *
 * Real levels come from [AudioReactive] when the person allowed it; otherwise a simulated beat and
 * spectrum drive the same effects. Runs ~30 fps only while Voltcap is on screen; still with lite
 * effects or animations off. The song, controls and lyrics are a separate panel the home screen
 * places clear of the game row (NowPlayingPanel); [waveY] (0..1 from the top) puts the wave there too.
 */
@Composable
internal fun NowPlayingScene(
    themeColors: ShelfColors,
    modifier: Modifier = Modifier,
    waveY: Float = 0.5f,
    /** Which music-reactive scene: the wave (MUSIC), Spectrum, Pulse ring or Beat tunnel. */
    motif: ThemeMotif = ThemeMotif.MUSIC,
    /** Room for the visualizer above / below its line (share of the screen height). */
    waveH: Float = 0.1f,
    /** Across: 0 = the middle; otherwise 0..1 from the left (the player sits beside the visualizer). */
    waveX: Float = 0f
) {
    val context = LocalContext.current
    // Your own visualizer only while a valid, compiled, switched-on pack is chosen (otherwise Now playing).
    @Suppress("NAME_SHADOWING") val motif = rememberEffectiveMotif(motif)
    // Picked as the scene behind another theme, it still gets the cover's colours (not the fallback).
    val isFallback = remember(themeColors) {
        com.shelf.launcher.ui.theme.AppTheme.values().any { it.motif.reactive && it.colors == themeColors }
    }
    // Kaleido cover is all about the album: always the cover's own colours (never the theme's or custom ones).
    // A custom visualizer also takes the cover's colours while a song plays (the theme's own otherwise).
    val coverOnly = motif == ThemeMotif.KALEIDO || motif == ThemeMotif.CUSTOM_VIZ
    val colors = if (isFallback || coverOnly) MusicTheme.colors ?: themeColors else themeColors
    val playing = MusicTheme.nowPlaying
    // A full theme preview drives the beat as if music played, so reactive effects can be judged.
    val isPlaying = playing?.isPlaying == true || ScenePreview.active
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    // While music plays the music scene never stops (Lite effects, covered by a window, system animations off):
    // it only rests when the screen is off / Voltcap is in the background.
    val active = rememberIsActive() && (isPlaying || (rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current))

    // Listen to the real sound only while it can matter: on screen, playing, allowed.
    val listen = active && isPlaying && MusicThemeSettings.realAudio && AudioReactive.hasPermission(context)
    DisposableEffect(listen) {
        val held = listen && AudioReactive.acquire(context)
        onDispose { if (held) AudioReactive.release() }
    }

    val levels = remember { SceneLevels() }
    val time = remember { mutableFloatStateOf(6f) }
    val playingNow by rememberUpdatedState(isPlaying)
    val listening by rememberUpdatedState(listen)
    // Idle with nothing playing: slow down like the other scenes. While music plays it keeps full
    // speed — the visualizer is what you're watching then.
    // A custom visualizer runs at up to ~60 fps (30 once it proved heavy) and is watched by PackGuard.
    if (motif.custom) PackGuard.Watch(motif)
    val idleMs = maxOf(if (motif.custom) PackGuard.frameMs() else 0, if (isPlaying) 0 else idleFrameMs())
    val tuning = LocalSceneTuning.current
    MusicClock(active, time, idleMs, tuning.speed) { dt -> levels.step(dt, time.floatValue, playingNow, listening && AudioReactive.live) }

    // The wave glides to a new height when the game row moves, instead of jumping.
    val waveAt by androidx.compose.animation.core.animateFloatAsState(waveY.coerceIn(0.08f, 0.92f), tween(600), label = "waveY")
    val waveState = rememberUpdatedState(waveAt)
    val roomAt by androidx.compose.animation.core.animateFloatAsState(waveH.coerceIn(0.03f, 0.35f), tween(600), label = "waveH")
    val roomState = rememberUpdatedState(roomAt)
    val xAt by androidx.compose.animation.core.animateFloatAsState(if (waveX <= 0f) 0.5f else waveX.coerceIn(0.15f, 0.85f), tween(600), label = "waveX")
    val xState = rememberUpdatedState(if (waveX <= 0f && xAt == 0.5f) 0f else xAt)
    Box(modifier) {
        val revision = if (motif.custom) CustomPacks.revision.intValue else 0
        val scene = if (Build.VERSION.SDK_INT >= 33) remember(motif, revision) { MusicShader.create(motif) } else null
        if (scene != null) {
            MusicShaderLayer(scene, colors, time, levels, waveState, roomState, xState, Modifier.fillMaxSize())
        } else {
            MusicCanvasLayer(colors, time, levels, waveState, roomState, Modifier.fillMaxSize())
        }
    }
}

/** Ticks at the scenes' rate (~60 fps; update 126) while [active]; [onFrame] gets the seconds since the last tick. */
@Composable
private fun MusicClock(active: Boolean, time: MutableFloatState, idleMs: Int, speed: Float, onFrame: (Float) -> Unit) {
    val frame by rememberUpdatedState(onFrame)
    // (Update 126: Settings › Background frame rate applies here too — it only reached the themes.)
    val frameNs = maxOf(sceneFrameMs(), idleMs) * 1_000_000L
    LaunchedEffect(active, frameNs, speed) {
        if (!active) return@LaunchedEffect
        val base = time.floatValue
        val start = withFrameNanos { it }
        var last = start
        // Like SceneClock: rest most of the interval after a step instead of waking on every display frame.
        val nap = (frameNs / 1_000_000L - 10L).coerceAtLeast(0L)
        while (true) {
            val stepped = withFrameNanos { now ->
                if (now - last >= frameNs) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    last = now
                    time.floatValue = (base + (now - start) / 1_000_000_000f * speed) % 3600f
                    frame(dt)
                    true
                } else false
            }
            if (stepped && nap > 0L) delay(nap)
        }
    }
}

/**
 * The levels the shader sees, smoothed: quick to rise, slow to fall, so the picture moves with
 * the music without flickering. Plain fields (not Compose state): the draw reads them each frame.
 */
private class SceneLevels {
    var bass = 0f
    var mid = 0f
    var high = 0f
    var beatAge = 9f
    var playing = 0f
    /** The eight equalizer bands, smoothed like a meter: jump up, fall back steadily. */
    val eq = FloatArray(AudioReactive.EQ_BANDS)
    private val eqTarget = FloatArray(AudioReactive.EQ_BANDS)

    fun step(dt: Float, t: Float, isPlaying: Boolean, real: Boolean) {
        val tb: Float
        val tm: Float
        val th: Float
        if (real) {
            tb = AudioReactive.bass; tm = AudioReactive.mid; th = AudioReactive.high
            val at = AudioReactive.beatAt
            beatAge = if (at == 0L) 9f else ((SystemClock.elapsedRealtime() - at) / 1000f).coerceAtMost(9f)
            for (i in eqTarget.indices) eqTarget[i] = AudioReactive.eq[i]
        } else if (isPlaying) {
            // Simulated: a 112 bpm kick with an off-beat snare and hats, plus swells, so the bands move
            // the way a real equalizer does (the lows thump, the highs flicker) rather than drifting.
            // Not a metronome: the song has calmer and busier parts (a ~30 s swell), every eighth bar
            // drops the kick for a breath, and each hit lands a little harder or softer.
            val beat = 60f / 112f
            val n = (t / beat).toInt()
            val age = t % beat
            val half = (t + beat / 2f) % beat
            val section = 0.55f + 0.45f * sin(t * 0.21f) * sin(t * 0.077f + 1.3f)
            val breakBar = (n / 4) % 8 == 7
            val accent = 0.65f + 0.35f * (((n * 7919) % 13) / 12f)
            val kickEnv = if (breakBar) 0f else exp(-age * 7f) * accent * section
            val snareEnv = if ((n and 1) == 1) exp(-age * 9f) * (0.5f + 0.5f * section) else 0f
            val hatEnv = exp(-half * 14f) * (0.4f + 0.6f * section)
            tb = 0.2f * section + 0.65f * kickEnv
            tm = 0.4f + 0.3f * snareEnv + 0.2f * sin(t * 1.7f) * sin(t * 0.43f + 1f)
            th = 0.3f + 0.45f * hatEnv * (0.6f + 0.4f * sin(t * 0.37f))
            beatAge = if (breakBar) 9f else age + (1f - accent) * 0.15f
            for (i in eqTarget.indices) {
                val f = i / (eqTarget.size - 1f) // 0 = lowest band, 1 = highest
                val wobble = 0.5f + 0.5f * sin(t * (1.3f + i * 0.71f) + i * 2.1f) * sin(t * (0.37f + i * 0.13f) + i)
                val hit = kickEnv * (1f - f) * (1f - f) + snareEnv * exp(-(f - 0.45f) * (f - 0.45f) * 12f) + hatEnv * f * f
                eqTarget[i] = (0.15f + 0.35f * wobble * (1f - 0.4f * f) + 0.7f * hit).coerceIn(0f, 1f)
            }
        } else {
            tb = 0f; tm = 0f; th = 0f
            beatAge = (beatAge + dt).coerceAtMost(9f)
            eqTarget.fill(0f)
        }
        val k = (dt * 30f).coerceIn(0.3f, 3f) // per-frame rates assume ~30 fps
        bass = approach(bass, tb, k)
        mid = approach(mid, tm, k)
        high = approach(high, th, k)
        playing += ((if (isPlaying) 1f else 0f) - playing) * (0.04f * k).coerceAtMost(1f)
        // Meter-like: rises almost at once, falls a bit slower (but much faster than the light levels).
        for (i in eq.indices) {
            val target = eqTarget[i]
            val rate = if (target > eq[i]) 0.75f else 0.22f
            eq[i] += (target - eq[i]) * (rate * k).coerceAtMost(1f)
        }
    }

    private fun approach(v: Float, target: Float, k: Float): Float {
        val rate = if (target > v) 0.5f else 0.12f
        return v + (target - v) * (rate * k).coerceAtMost(1f)
    }
}

@RequiresApi(33)
private class MusicShader private constructor(val shader: RuntimeShader, private val children: Set<String>, uniforms: Set<String>, val motif: ThemeMotif) {
    /** Everything this scene really has (plain uniforms and pictures); nothing else is ever set. */
    private val used = uniforms + children
    private val missing = HashSet<String>()
    /**
     * Child shaders (pictures) the scene really samples. The AGSL compiler drops a declared but unused
     * `uniform shader`, and on Android 13 setting a child the shader doesn't have doesn't throw a
     * catchable exception: the platform's error message is built from a bad string and the whole app
     * aborts (the crash after switching music themes). So a child is only ever set when the source
     * samples it ([ThemeShaders.children], worked out by tools/themes/gen.py from what main() reaches).
     */
    private var artFor: Bitmap? = null
    private var coverFor: Bitmap? = null
    /** When the cover last changed (scene time), for scenes that animate the swap (uSwapAge). */
    private var swapAt = -1000f
    private val placeholder by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    /** The scene writes the song on something (the tape's label): it samples uLabel. */
    val wantsLabel get() = "uLabel" in children
    private var labelFor: Bitmap? = null

    /** The holo card prints the song in a clean type; the others (Tape deck…) handwrite it. */
    val cleanLabel get() = motif == ThemeMotif.HOLO_COVER || motif == ThemeMotif.ALBUM_TIDE

    /**
     * Clocks that only run while the music plays (uPlayT and the levels' uBassT / uMidT / uHighT): a
     * spin written as t * (a + b * level) jumps every time the level moves (the cube turning in jerks),
     * these grow smoothly instead and ease to a stop on pause.
     */
    private var lastT = -1f
    private var playT = 0f
    private var bassT = 0f
    private var midT = 0f
    private var highT = 0f

    /** The scene shows earlier songs' covers too (uHistory). */
    val wantsHistory get() = "uHistory" in children
    private var historyFor: Bitmap? = null

    fun update(w: Float, h: Float, t: Float, c: ShelfColors, l: SceneLevels, art: Bitmap?, waveY: Float, waveH: Float = 0.1f, lod: Float = 0f, particles: Float = 1f, cover: Bitmap? = null, waveX: Float = 0f, label: Bitmap? = null, history: Bitmap? = null, historyCount: Int = 0, artOn: Boolean = MusicThemeSettings.cover) {
        if (wantsHistory) {
            val hb = history ?: placeholder
            if (hb !== historyFor) {
                set("uHistory") { shader.setInputShader("uHistory", BitmapShader(hb, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)) }
                historyFor = hb
            }
            set("uHistCount") { shader.setFloatUniform("uHistCount", if (history == null) 0f else historyCount.toFloat()) }
        }
        if (wantsLabel) {
            val lb = label ?: placeholder
            if (lb !== labelFor) {
                // Smooth (bilinear) sampling: the label is drawn bigger than it shows.
                set("uLabel") {
                    shader.setInputShader("uLabel", BitmapShader(lb, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
                }
                set("uLabelSize") { shader.setFloatUniform("uLabelSize", lb.width.toFloat(), lb.height.toFloat()) }
                labelFor = lb
            }
        }
        set("uWaveX") { shader.setFloatUniform("uWaveX", waveX) }
        // The cover's subject (Pan & zoom aims at it); only the scenes that use it have it.
        set("uSubject") { val sub = MusicTheme.subject; shader.setFloatUniform("uSubject", sub[0], sub[1], sub[2]) }
        set("uLod") { shader.setFloatUniform("uLod", lod) }
        set("uDensity") { shader.setFloatUniform("uDensity", particles + 1f) }
        // The child shader must always be set (an unset one draws nothing): 1 px stand-in without a cover.
        val bmp = art ?: placeholder
        if (bmp !== artFor) {
            if ("uArt" in children) set("uArt") { shader.setInputShader("uArt", BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)) }
            set("uArtSize") { shader.setFloatUniform("uArtSize", bmp.width.toFloat(), bmp.height.toFloat()) }
            artFor = bmp
        }
        // The sharp cover for the scenes with a record / disc label (only those declare it).
        val sharp = cover ?: bmp
        if (sharp !== coverFor) {
            if (coverFor != null && coverFor !== placeholder) swapAt = t
            if ("uCover" in children) set("uCover") { shader.setInputShader("uCover", BitmapShader(sharp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)) }
            set("uCoverSize") { shader.setFloatUniform("uCoverSize", sharp.width.toFloat(), sharp.height.toFloat()) }
            coverFor = sharp
        }
        set("uHasArt") { shader.setFloatUniform("uHasArt", if (art != null && artOn) 1f else 0f) }
        set("uRes") { shader.setFloatUniform("uRes", w, h) }
        set("uTime") { shader.setFloatUniform("uTime", t) }
        color("uBg", c.background); color("uSurf", c.surface); color("uAcc", c.accent); color("uSec", c.secondary)
        set("uBass") { shader.setFloatUniform("uBass", l.bass) }
        set("uMid") { shader.setFloatUniform("uMid", l.mid) }
        set("uHigh") { shader.setFloatUniform("uHigh", l.high) }
        set("uBeatAge") { shader.setFloatUniform("uBeatAge", l.beatAge) }
        if (t < swapAt) swapAt = -1000f // the scene's clock started over
        set("uSwapAge") { shader.setFloatUniform("uSwapAge", t - swapAt) }
        set("uPlaying") { shader.setFloatUniform("uPlaying", l.playing) }
        // The playing clocks: advance by this frame's step (none on the first frame, or when the scene's
        // clock started over; capped so a long pause in drawing doesn't make a jump).
        val dt = if (lastT < 0f || t < lastT) 0f else (t - lastT).coerceAtMost(0.1f)
        lastT = t
        playT += dt * l.playing
        bassT += dt * l.bass * l.playing
        midT += dt * l.mid * l.playing
        highT += dt * l.high * l.playing
        // Wrapped like uTime (an hour of playing), so float precision never runs out.
        if (playT > 3600f) playT -= 3600f
        if (bassT > 3600f) bassT -= 3600f
        if (midT > 3600f) midT -= 3600f
        if (highT > 3600f) highT -= 3600f
        set("uPlayT") { shader.setFloatUniform("uPlayT", playT) }
        set("uBassT") { shader.setFloatUniform("uBassT", bassT) }
        set("uMidT") { shader.setFloatUniform("uMidT", midT) }
        set("uHighT") { shader.setFloatUniform("uHighT", highT) }
        set("uIntensity") { shader.setFloatUniform("uIntensity", effectiveIntensity(c)) }
        val e = l.eq
        set("uEqA") { shader.setFloatUniform("uEqA", e[0], e[1], e[2], e[3]) }
        set("uEqB") { shader.setFloatUniform("uEqB", e[4], e[5], e[6], e[7]) }
        set("uWaveY") { shader.setFloatUniform("uWaveY", waveY) }
        set("uWaveH") { shader.setFloatUniform("uWaveH", waveH) }
    }

    private fun color(name: String, col: Color) = set(name) { shader.setFloatUniform(name, col.red, col.green, col.blue, 1f) }

    private inline fun set(name: String, block: () -> Unit) {
        if (name !in used || name in missing) return
        try { block() } catch (_: IllegalArgumentException) { missing += name }
    }

    companion object {
        /** Scenes that failed to compile on this device; the wave (or Canvas) is used instead. */
        private val broken = HashSet<ThemeMotif>()

        fun create(motif: ThemeMotif): MusicShader? {
            if (!motif.custom && motif in broken) return if (motif != ThemeMotif.MUSIC) create(ThemeMotif.MUSIC) else null
            return try {
                MusicShader(RuntimeShader(SceneSource.source(motif)), SceneSource.children(motif), SceneSource.uniforms(motif), motif)
            } catch (e: Throwable) {
                if (!motif.custom) broken += motif
                android.util.Log.w("NowPlayingScene", "Music scene $motif fell back", e)
                if (motif != ThemeMotif.MUSIC) create(ThemeMotif.MUSIC) else null
            }
        }
    }
}

@RequiresApi(33)
@Composable
private fun MusicShaderLayer(scene: MusicShader, colors: ShelfColors, time: State<Float>, levels: SceneLevels, waveY: State<Float>, waveH: State<Float>, waveX: State<Float>, modifier: Modifier) {
    // The console-era scenes can show the game you're on instead of the album (Theme look › Picture in the scene):
    // its hero art blurred behind, its cover on the screen / cartridge / banner.
    val np = MusicTheme.nowPlaying
    val eraMode = com.shelf.launcher.data.Features.eraArt.state.value
    val gameArtOn = scene.motif in SceneArt.ERA_MOTIFS && eraMode != com.shelf.launcher.data.Features.EraArt.ALBUM &&
        (eraMode == com.shelf.launcher.data.Features.EraArt.GAME || np?.isPlaying != true)
    val gameArt by rememberGameMusicArt(gameArtOn)
    val shownGame = gameArt.takeIf { gameArtOn }
    val art = shownGame?.soft ?: MusicTheme.softArt
    // The song written on a label (Tape deck): drawn once per song, only for scenes that show it.
    val label = remember(np?.title, np?.artist, scene) { if (scene.wantsLabel) songLabel(np?.title, np?.artist, clean = scene.cleanLabel) else null }
    val history = MusicTheme.historyAtlas
    val historyCount = MusicTheme.historyCount
    val sharp = shownGame?.sharp ?: MusicTheme.sharpArt
    val artOn = shownGame != null || MusicThemeSettings.cover
    val quality = LocalSceneQuality.current
    val tuning = LocalSceneTuning.current
    Spacer(
        // Music scenes are what you watch while a song plays (the cover is in them): never drawn at a low
        // resolution — a weaker quality setting only lowers their frame rate, not their sharpness.
        // A custom visualizer is the exception: it is drawn at a capped resolution (PackGuard.scale).
        modifier.sceneLayer(scene.motif.packKind()?.let { PackGuard.scale(quality.scale, it) } ?: maxOf(quality.scale, 0.9f)).drawWithCache {
            val brush = ShaderBrush(scene.shader)
            onDrawBehind {
                scene.update(size.width, size.height, time.value, colors, levels, art, waveY.value, waveH.value, if (quality.scale < 0.3f) 1f else 0f, tuning.particles, sharp, waveX.value, label, history, historyCount, artOn)
                drawRect(brush)
                drawSceneShade()
                if (tuning.dim > 0f) drawRect(Color.Black.copy(alpha = tuning.dim))
            }
        }
    )
}

/** Android 12 and older: the soft cover, gently zooming with the bass, a glow and a simple equalizer wave. */
@Composable
private fun MusicCanvasLayer(colors: ShelfColors, time: State<Float>, levels: SceneLevels, waveY: State<Float>, waveH: State<Float>, modifier: Modifier) {
    val art = MusicTheme.softArt?.takeIf { MusicThemeSettings.cover }
    val image = remember(art) { art?.asImageBitmap() }
    val path = remember { androidx.compose.ui.graphics.Path() }
    Canvas(modifier) {
        val t = time.value
        drawRect(colors.background)
        if (image != null) {
            val zoom = 1.6f + 0.06f * sin(t * 0.045f) - 0.05f * levels.bass
            val side = (max(size.width, size.height) * zoom).toInt()
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt()),
                dstSize = IntSize(side, side),
                alpha = 0.45f,
                filterQuality = FilterQuality.Low
            )
        }
        val cy = size.height * waveY.value
        val glow = 0.12f + 0.35f * levels.bass * levels.playing
        drawCircle(
            Brush.radialGradient(listOf(colors.accent.copy(alpha = glow), Color.Transparent), Offset(size.width / 2, cy), size.minDimension * 0.7f),
            size.minDimension * 0.7f, Offset(size.width / 2, cy)
        )
        // The equalizer outline: band height across the width, mirrored above and below the centre line.
        val steps = 64
        fun heightAt(i: Int): Float {
            val x = i / steps.toFloat()
            val m = kotlin.math.abs(x - 0.5f) * 2f
            val f = (m * 7f).coerceIn(0f, 7f)
            val lo = f.toInt().coerceAtMost(7)
            val hi = (lo + 1).coerceAtMost(7)
            val u = (f - lo).let { it * it * (3 - 2 * it) }
            val e = (levels.eq[lo] + (levels.eq[hi] - levels.eq[lo]) * u) * levels.playing
            val edge = ((1f - m) / 0.18f).coerceIn(0f, 1f)
            // Never taller than the room it was given (the player sits right under it).
            return (0.006f + (waveH.value - 0.012f).coerceAtLeast(0.02f) * e) * size.height * edge
        }
        for (sign in floatArrayOf(-1f, 1f)) {
            path.reset()
            for (i in 0..steps) {
                val x = size.width * i / steps
                val y = cy + sign * heightAt(i)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colors.accent.copy(alpha = 0.10f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx()))
            drawPath(path, Brush.horizontalGradient(listOf(colors.secondary, colors.accent, colors.secondary)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        }
        drawSceneShade()
    }
}


/**
 * The song's title (and artist under it) written like on a cassette's paper label: dark ink on a
 * transparent picture the Tape deck scene lays over its label. Shrinks to fit; nothing = empty.
 */
internal fun songLabel(title: String?, artist: String?, clean: Boolean = false): Bitmap? {
    if (title.isNullOrBlank()) return null
    if (clean) return cleanSongLabel(title, artist)
    val w = 640; val h = 280
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val hand = runCatching { android.graphics.Typeface.create("casual", android.graphics.Typeface.BOLD) }.getOrNull()
        ?: android.graphics.Typeface.DEFAULT_BOLD
    val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(28, 30, 78); typeface = hand
    }
    fun fit(text: String, maxSize: Float, maxW: Float): Float {
        var size = maxSize
        ink.textSize = size
        while (size > 18f && ink.measureText(text) > maxW) { size -= 2f; ink.textSize = size }
        return size
    }
    var t = title.trim()
    fit(t, 104f, w - 24f)
    // Still too long at the smallest size: cut with an ellipsis.
    while (t.length > 3 && ink.measureText(t) > w - 24f) t = t.dropLast(2) + "…"
    canvas.save(); canvas.rotate(-2.5f, w / 2f, h / 2f)
    canvas.drawText(t, 12f, h * 0.48f, ink)
    if (!artist.isNullOrBlank()) {
        ink.alpha = 200
        var a = artist.trim()
        fit(a, 66f, w - 40f)
        while (a.length > 3 && ink.measureText(a) > w - 40f) a = a.dropLast(2) + "…"
        canvas.drawText(a, 28f, h * 0.86f, ink)
    }
    canvas.restore()
    return bmp
}


/**
 * How strongly the music scenes react: the Strength setting, and with "Adapt to the cover" on, a
 * colourful, saturated cover pushes it up (to 130 %) and a muted, grey one calms it down (to 60 %).
 */
internal fun effectiveIntensity(c: ShelfColors): Float {
    val base = MusicThemeSettings.intensity / 100f
    if (!MusicThemeSettings.adaptive) return base
    fun sat(col: Color): Float {
        val mx = maxOf(col.red, col.green, col.blue)
        val mn = minOf(col.red, col.green, col.blue)
        return if (mx <= 0f) 0f else (mx - mn) / mx
    }
    val vivid = ((sat(c.accent) + sat(c.secondary)) / 2f).coerceIn(0f, 1f)
    return (base * (0.6f + 0.7f * vivid)).coerceIn(0f, 1.3f)
}

/**
 * The song's title and artist printed clean and centred (the Holo card's name strip): bold condensed
 * title, the artist smaller under it, ink on a clear 640 × 170 picture. Shrinks to fit, then cuts with "…".
 */
private fun cleanSongLabel(title: String, artist: String?): Bitmap {
    val w = 640; val h = 170
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(28, 30, 78); textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    val maxW = w - 48f
    fun fit(text: String, maxSize: Float): String {
        var size = maxSize
        ink.textSize = size
        while (size > 22f && ink.measureText(text) > maxW) { size -= 2f; ink.textSize = size }
        var t = text
        while (t.length > 3 && ink.measureText(t) > maxW) t = t.dropLast(2) + "…"
        return t
    }
    val hasArtist = !artist.isNullOrBlank()
    canvas.drawText(fit(title.trim(), if (hasArtist) 80f else 96f), w / 2f, if (hasArtist) h * 0.52f else h * 0.68f, ink)
    if (hasArtist) {
        ink.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        ink.alpha = 215
        canvas.drawText(fit(artist!!.trim(), 48f), w / 2f, h * 0.9f, ink)
    }
    return bmp
}
