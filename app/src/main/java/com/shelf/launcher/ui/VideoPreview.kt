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
fun VideoBackdrop(url: String, withSound: Boolean, modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("VideoBackdrop")
    var playing by remember(url) { mutableStateOf(false) }
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
                                mp.start()
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
                        (view.tag as? MediaPlayer)?.let { runCatching { it.release() } }
                        view.tag = null
                        surface?.let { runCatching { it.release() } }
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
            },
            onRelease = { view ->
                (view.tag as? MediaPlayer)?.let { runCatching { it.release() } }
                view.tag = null
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
