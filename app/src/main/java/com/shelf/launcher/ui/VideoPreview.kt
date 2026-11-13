package com.shelf.launcher.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * A game's gameplay clip playing behind the dock, cropped to fill the screen
 * (no black bars), looping, muted unless [withSound]. Fades in once the first
 * frame is ready, so a slow or failed download just leaves the artwork.
 */
@Composable
fun VideoBackdrop(
    url: String, withSound: Boolean, modifier: Modifier = Modifier, paused: Boolean = false,
    /** Update 307: false fades the clip out (VideoCrossfade), then [onHidden]. */
    show: Boolean = true,
    onHidden: (() -> Unit)? = null,
    /** Update 347: the whole picture inside the view, its own shape kept (black bars) — an arcade monitor; else cropped to fill. */
    fit: Boolean = false,
    /** The dock's shading over the clip (off: the clip as it is). */
    shade: Boolean = true
) {
    com.shelf.launcher.Breadcrumbs.mark("VideoBackdrop")
    var playing by remember(url) { mutableStateOf(false) }
    // Update 221 (#60 / #96): [paused] — the home screen is idle (HomeIdle.calm: 30 s without input, or the battery
    // saver's rest). The clip holds its frame: a looping clip redrew the whole screen ~30 times a second forever (on the
    // RP Classic ~17 ms of GPU per frame, so it never reached 60 and kept the GPU busy). The first press plays it on.
    // Update 251: also held while Voltcap isn't the app you're using (a game in front, the screen off, or a game on the other
    // screen holding the controller): a clip left playing on the second screen behind a game redrew that whole screen
    // 30 times a second the entire session (~30 % of a CPU core on the AYN Thor, measured while 3DS games ran).
    val active = rememberIsActive()
    val hold = paused || !active
    val pausedNow = androidx.compose.runtime.rememberUpdatedState(hold)
    val prepared = remember(url) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val alpha by animateFloatAsState(if (playing && show) 1f else 0f, tween(if (show) 700 else CLIP_FADE_OUT_MS), label = "video")
    // (v0.6.66 D137: when a clip starts / is put away, for the frame-pacing runs.)
    if (QaTrace.on) androidx.compose.runtime.LaunchedEffect(playing, show) { android.util.Log.e("VoltcapQA", "clip playing=$playing show=$show") }
    // (Update 307: a clip being replaced or put away fades out over the artwork, then goes — VideoCrossfade.)
    val hidden = androidx.compose.runtime.rememberUpdatedState(onHidden)
    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) return@LaunchedEffect
        kotlinx.coroutines.delay(CLIP_FADE_OUT_MS + 60L)
        hidden.value?.invoke()
    }

    // Sprint 527 (owner: the clip behind Tile home washed out the lower half): the clip's light is measured from the clip
    // itself - a 24 x 14 copy of its picture every 1.5 s while it plays (one tiny read-back, no decoding of our own) - so
    // the shading under the dock follows the clip: a bright clip gets a little more, a dark one less, in the clip's own
    // colour, never the theme's pale colour as a fog. Changes glide over ~1.2 s (a scene cut never flashes the light).
    val viewRef = remember { arrayOfNulls<TextureView>(1) }
    val sample = remember(url) { androidx.compose.runtime.mutableStateOf<ClipLight?>(null) }
    if (shade) androidx.compose.runtime.LaunchedEffect(url, playing, hold) {
        if (!playing || hold) return@LaunchedEffect
        while (true) {
            // Only a real change of light moves the shading (a still-similar picture keeps it, so nothing re-animates).
            viewRef[0]?.let { v -> ClipLight.measure(v)?.let { if (ClipLight.moved(sample.value, it)) sample.value = it } }
            kotlinx.coroutines.delay(1500)
        }
    }
    // (Sprint 527, owner: chips, names and status icons hard to read on a near-white clip - the home's own text over the
    // clip takes a local scrim while one plays (ClipBackdrop.on), instead of a darker video.)
    if (shade) androidx.compose.runtime.DisposableEffect(playing && show) {
        val counted = playing && show
        if (counted) ClipBackdrop.showing++
        onDispose { if (counted) ClipBackdrop.showing-- }
    }
    Box(modifier.graphicsLayer { this.alpha = alpha }) {
        AndroidView(
            factory = { context ->
                val view = TextureView(context)
                viewRef[0] = view
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    // Released with the player (each one holds a native buffer queue).
                    var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val player = MediaPlayer()
                        view.tag = player
                        runCatching {
                            surface = Surface(texture)
                            player.setSurface(surface)
                            com.shelf.launcher.data.SsMedia.forPlayer(url).let { (u, h) -> player.setDataSource(context, Uri.parse(u), h) } // (Batch 517: key added only here)
                            player.isLooping = true
                            if (!withSound) player.setVolume(0f, 0f)
                            player.setOnPreparedListener { mp ->
                                sizeClip(view, mp.videoWidth, mp.videoHeight, fit)
                                // (Update 470, QA only: the clip's own size against the room it fills — the "soft Stadium 2
                                // window" was its 2-D gameplay clip, not a picture decoded small.)
                                if (QaTrace.on) android.util.Log.e("VoltcapQA", "clip size ${mp.videoWidth}x${mp.videoHeight} in ${view.width}x${view.height}")
                                prepared.set(true)
                                // (Already idle when it got ready: the first frame is shown, then it holds.)
                                mp.start()
                                if (pausedNow.value) view.postDelayed({ runCatching { if (pausedNow.value && mp.isPlaying) mp.pause() } }, 400)
                            }
                            player.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) playing = true
                                false
                            }
                            // (Update 307: a clip whose looping the player didn't honour — some streamed clips end
                            // after one play — starts over from its first frame, keeping its last one on screen meanwhile.)
                            player.setOnCompletionListener { mp -> runCatching { if (view.tag === mp && !pausedNow.value) { mp.seekTo(0); mp.start() } } }
                            player.setOnVideoSizeChangedListener { _, w, h -> sizeClip(view, w, h, fit) }
                            player.setOnErrorListener { _, _, _ -> playing = false; true }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        (view.tag as? MediaPlayer)?.let { sizeClip(view, it.videoWidth, it.videoHeight, fit) }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        // (Update 231, #59: released on a background thread — see releaseInBackground.)
                        val player = view.tag as? MediaPlayer
                        view.tag = null
                        if (player != null) releaseInBackground(player, surface)
                        else surface?.let { runCatching { it.release() } }
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                }
                view
            },
            // The sound setting changed while this clip plays: apply it now (not only for the next clip).
            update = { view ->
                (view.tag as? MediaPlayer)?.let { mp -> runCatching { val v = if (withSound) 1f else 0f; mp.setVolume(v, v) } }
                // (Update 221: hold / play on with the home screen's idle state.)
                (view.tag as? MediaPlayer)?.takeIf { prepared.get() }?.let { mp ->
                    runCatching { if (hold && mp.isPlaying) mp.pause() else if (!hold && !mp.isPlaying) mp.start() }
                }
            },
            onRelease = { view ->
                val player = view.tag as? MediaPlayer
                view.tag = null
                if (player != null) releaseInBackground(player, null)
            },
            modifier = Modifier.fillMaxSize()
        )
        // The shading under the dock so it stays readable (Sprint 527): a deep tone of the clip's own colour (the theme's
        // background before - pale when the theme took its colours from bright art, so the lower half went to a white fog),
        // eased in from the middle, never a solid band: at most ~0.75 at the very bottom of a white clip, ~0.45 of a dark one.
        if (shade) {
            val light = sample.value
            val tone = ClipLight.tone(light?.color ?: ShelfTheme.background)
            val strength by animateFloatAsState(ClipLight.strength(light?.lowerLuma), tween(1200), label = "clipShade")
            val toneNow by androidx.compose.animation.animateColorAsState(tone, tween(1200), label = "clipTone")
            val topNow by animateFloatAsState(ClipLight.topStrength(light?.upperLuma), tween(1200), label = "clipTop")
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        // (QA only - voltcap_test_clipshade legacy: the old theme-background ramp, for a same-frame before / after.)
                        if (ClipBackdrop.legacy) Brush.verticalGradient(*ClipLight.legacyStops(ShelfTheme.background))
                        else Brush.verticalGradient(*ClipLight.stops(toneNow, strength, topNow))
                    )
            )
        }
    }
}

/**
 * Sprint 527: whether a background clip is showing behind the home now ([on]) - the home's text over it (category pills,
 * the game's name, the status bar) then takes a small local scrim of its own. [legacy] (QA only) draws the old shading.
 */
internal fun Modifier.videoTextScrim(): Modifier = this.drawBehind {
    // A soft dark capsule just round the text (two layers: a wider faint one so it has no hard edge) - local, the clip stays.
    val padX = 10.dp.toPx(); val padY = 4.dp.toPx()
    for ((grow, a) in listOf(1.6f to 0.16f, 1f to 0.30f)) {
        val px = padX * grow; val py = padY * grow
        drawRoundRect(
            Color.Black.copy(alpha = a), topLeft = androidx.compose.ui.geometry.Offset(-px, -py),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * px, size.height + 2 * py),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius((size.height + 2 * py) / 2f)
        )
    }
}

internal object ClipBackdrop {
    var showing by androidx.compose.runtime.mutableIntStateOf(0)
    val on: Boolean get() = showing > 0 && !legacy
    var legacy by mutableStateOf(false)
}

/**
 * Sprint 527: what a background clip's picture is like now - its lower half's luminance (0..1) and its average colour -
 * measured from a 24 x 14 copy of the view. Pure helpers ([tone], [strength], [stops]) are unit-tested (ClipLightTest).
 */
internal class ClipLight(val lowerLuma: Float, val color: Color, val upperLuma: Float = 0.5f) {
    companion object {
        fun measure(v: TextureView): ClipLight? = runCatching {
            if (!v.isAvailable) return null
            val b = v.getBitmap(24, 14) ?: return null
            var lum = 0f; var r = 0f; var g = 0f; var bl = 0f; var n = 0; var up = 0f
            // (The top band too - the category chips and the name sit on it: Flip 2 t527e, a white clip under them.)
            for (y in 0 until 3) for (x in 0 until 24) {
                val c = b.getPixel(x, y)
                up += 0.2126f * ((c shr 16) and 0xFF) / 255f + 0.7152f * ((c shr 8) and 0xFF) / 255f + 0.0722f * (c and 0xFF) / 255f
            }
            for (y in 7 until 14) for (x in 0 until 24) {
                val c = b.getPixel(x, y)
                val cr = ((c shr 16) and 0xFF) / 255f; val cg = ((c shr 8) and 0xFF) / 255f; val cb = (c and 0xFF) / 255f
                lum += 0.2126f * cr + 0.7152f * cg + 0.0722f * cb; r += cr; g += cg; bl += cb; n++
            }
            b.recycle()
            ClipLight(lum / n, Color(r / n, g / n, bl / n), up / 72f)
        }.getOrNull()

        /** Whether [now] differs enough from [was] to move the shading (lower-half light by 0.08, or its colour clearly). Pure. */
        fun moved(was: ClipLight?, now: ClipLight): Boolean = was == null ||
            kotlin.math.abs(was.lowerLuma - now.lowerLuma) > 0.08f || kotlin.math.abs(was.upperLuma - now.upperLuma) > 0.08f ||
            kotlin.math.abs(was.color.red - now.color.red) + kotlin.math.abs(was.color.green - now.color.green) +
            kotlin.math.abs(was.color.blue - now.color.blue) > 0.18f

        /** A deep tone of [c]: its hue, at most half saturated, at most 14 % bright (a shadow, never a fog). Pure. */
        fun tone(c: Color): Color {
            val mx = maxOf(c.red, c.green, c.blue); val mn = minOf(c.red, c.green, c.blue)
            val v = mx.coerceIn(0.04f, 0.14f)
            if (mx - mn < 1e-4f) return Color(v, v, v)
            val sat = (mx - mn) / mx; val s2 = sat.coerceAtMost(0.5f)
            fun ch(x: Float) = v * (1f - s2 * (mx - x) / (mx - mn))
            return Color(ch(c.red), ch(c.green), ch(c.blue))
        }

        /** How strong the shading is at the very bottom for a clip whose lower half has luminance [l] (null: not measured yet). Pure. */
        fun strength(l: Float?): Float = (0.45f + 0.35f * (l ?: 0.5f)).coerceIn(0.42f, 0.78f)

        /** The shading before Sprint 527 (the theme's background, a fog at the bottom) - only for the QA before / after. Pure. */
        fun legacyStops(bg: Color): Array<Pair<Float, Color>> = arrayOf(
            0.0f to bg.copy(alpha = 0.55f), 0.2f to Color.Transparent, 0.55f to Color.Transparent,
            0.8f to bg.copy(alpha = 0.85f), 1.0f to bg.copy(alpha = 0.97f)
        )

        /** How strong the band under the status bar is for a clip whose top has luminance [l]: light on a dark clip, enough on a white one. Pure. */
        fun topStrength(l: Float?): Float = (0.30f + 0.45f * (l ?: 0.4f)).coerceIn(0.30f, 0.75f)

        /** The shading's stops: a band under the status bar ([top]), clear through the middle, eased in under the dock. Pure. */
        fun stops(tone: Color, strength: Float, top: Float = 0.42f): Array<Pair<Float, Color>> = arrayOf(
            0.0f to tone.copy(alpha = top),
            0.09f to tone.copy(alpha = top * 0.7f),
            0.20f to tone.copy(alpha = 0f),
            0.50f to tone.copy(alpha = 0f),
            0.64f to tone.copy(alpha = strength * 0.18f),
            0.76f to tone.copy(alpha = strength * 0.45f),
            0.88f to tone.copy(alpha = strength * 0.78f),
            1.0f to tone.copy(alpha = strength)
        )
    }
}

private const val CLIP_FADE_OUT_MS = 450

/**
 * Update 307 (Toni: "loop preview clips continuously, with smooth transitions between different clips and no black
 * flashes"): the clip for [url] that changes smoothly — the clip it replaces (or one put away, [url] null) keeps playing
 * while it fades out over what's under it, and the new one fades in on its first frame, so nothing pops away and no empty
 * (black) video view shows. Each clip loops on its own (VideoBackdrop). [clipModifier]: each clip's own size and place.
 */
@Composable
fun VideoCrossfade(
    url: String?, withSound: Boolean, modifier: Modifier = Modifier, paused: Boolean = false,
    clipModifier: Modifier = Modifier.fillMaxSize()
) {
    val clips = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url != null && clips.lastOrNull() != url) { clips.remove(url); clips.add(url) }
    }
    if (clips.isEmpty()) return
    Box(modifier) {
        for (c in clips.toList()) androidx.compose.runtime.key(c) {
            val shown = c == url
            VideoBackdrop(c, withSound && shown, clipModifier, paused, show = shown, onHidden = { if (c != url) clips.remove(c) })
        }
    }
}

/**
 * (Update 231, #59: leaving a game whose clip had started no longer stalls the screen — MediaPlayer.release() waits for
 * the decoder to shut down, which took a frame or more on the main thread; it now happens on a background thread.)
 * The player is already forgotten by the view (its tag is cleared first) and its listeners are removed here, on the main
 * thread, so nothing — no late "prepared" or "rendering started" event — touches it afterwards.
 */
private fun releaseInBackground(player: MediaPlayer, surface: Surface?) {
    runCatching {
        player.setOnPreparedListener(null)
        player.setOnInfoListener(null)
        player.setOnVideoSizeChangedListener(null)
        player.setOnErrorListener(null)
        player.setOnCompletionListener(null)
    }
    ClipReleaser.execute {
        runCatching { player.release() }
        surface?.let { runCatching { it.release() } }
    }
}

/** (Update 231, #59) One background thread that releases finished clip players, one after another. */
private val ClipReleaser: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "voltcap-clip-release").apply { isDaemon = true } }

/** Update 347: cropped to fill, or (fit) the whole picture inside the view with its own shape. */
private fun sizeClip(view: TextureView, videoWidth: Int, videoHeight: Int, fit: Boolean) {
    if (!fit) { cropToFill(view, videoWidth, videoHeight); return }
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = minOf(viewW / videoWidth, viewH / videoHeight)
    val matrix = Matrix()
    matrix.setScale(scale * videoWidth / viewW, scale * videoHeight / viewH, viewW / 2f, viewH / 2f)
    view.setTransform(matrix)
}

/** TextureView stretches video to its bounds; scale it back so it covers the
 * view while keeping its shape, cropping the overflow evenly. */
private fun cropToFill(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = maxOf(viewW / videoWidth, viewH / videoHeight)
    val matrix = Matrix()
    matrix.setScale(scale * videoWidth / viewW, scale * videoHeight / viewH, viewW / 2f, viewH / 2f)
    view.setTransform(matrix)
}
