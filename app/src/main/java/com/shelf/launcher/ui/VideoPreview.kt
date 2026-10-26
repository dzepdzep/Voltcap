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
    // (Update 307: a clip being replaced or put away fades out over the artwork, then goes — VideoCrossfade.)
    val hidden = androidx.compose.runtime.rememberUpdatedState(onHidden)
    androidx.compose.runtime.LaunchedEffect(show) {
        if (show) return@LaunchedEffect
        kotlinx.coroutines.delay(CLIP_FADE_OUT_MS + 60L)
        hidden.value?.invoke()
    }

    Box(modifier.graphicsLayer { this.alpha = alpha }) {
        AndroidView(
            factory = { context ->
                val view = TextureView(context)
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    // Released with the player (each one holds a native buffer queue).
                    var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val player = MediaPlayer()
                        view.tag = player
                        runCatching {
                            surface = Surface(texture)
                            player.setSurface(surface)
                            player.setDataSource(context, Uri.parse(url))
                            player.isLooping = true
                            if (!withSound) player.setVolume(0f, 0f)
                            player.setOnPreparedListener { mp ->
                                sizeClip(view, mp.videoWidth, mp.videoHeight, fit)
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
        // Same shading as the artwork backdrop, so the dock stays readable.
        if (shade) Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to ShelfTheme.background.copy(alpha = 0.55f),
                        0.2f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.8f to ShelfTheme.background.copy(alpha = 0.85f),
                        1.0f to ShelfTheme.background.copy(alpha = 0.97f)
                    )
                )
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
