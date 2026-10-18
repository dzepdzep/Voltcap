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
fun VideoBackdrop(url: String, withSound: Boolean, modifier: Modifier = Modifier, paused: Boolean = false) {
    com.shelf.launcher.Breadcrumbs.mark("VideoBackdrop")
    var playing by remember(url) { mutableStateOf(false) }
    // Update 221 (#60 / #96): [paused] — the home screen is idle (HomeIdle.calm: 30 s without input, or the battery
    // saver's rest). The clip holds its frame: a looping clip redrew the whole screen ~30 times a second forever (on the
    // RP Classic ~17 ms of GPU per frame, so it never reached 60 and kept the GPU busy). The first press plays it on.
    val pausedNow = androidx.compose.runtime.rememberUpdatedState(paused)
    val prepared = remember(url) { java.util.concurrent.atomic.AtomicBoolean(false) }
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tween(700), label = "video")

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
                                cropToFill(view, mp.videoWidth, mp.videoHeight)
                                prepared.set(true)
                                // (Already idle when it got ready: the first frame is shown, then it holds.)
                                mp.start()
                                if (pausedNow.value) view.postDelayed({ runCatching { if (pausedNow.value && mp.isPlaying) mp.pause() } }, 400)
                            }
                            player.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) playing = true
                                false
                            }
                            player.setOnVideoSizeChangedListener { _, w, h -> cropToFill(view, w, h) }
                            player.setOnErrorListener { _, _, _ -> playing = false; true }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        (view.tag as? MediaPlayer)?.let { cropToFill(view, it.videoWidth, it.videoHeight) }
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
                    runCatching { if (paused && mp.isPlaying) mp.pause() else if (!paused && !mp.isPlaying) mp.start() }
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
        Box(
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
    }
    ClipReleaser.execute {
        runCatching { player.release() }
        surface?.let { runCatching { it.release() } }
    }
}

/** (Update 231, #59) One background thread that releases finished clip players, one after another. */
private val ClipReleaser: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "voltcap-clip-release").apply { isDaemon = true } }

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
