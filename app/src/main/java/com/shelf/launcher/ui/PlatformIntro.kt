package com.shelf.launcher.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A platform's intro video, full screen on black, played after the launch animation and before the game
 * starts. A tap (or any controller key, handled by the launcher screen) skips it; it also ends by itself, and
 * a video that can't be opened (or takes too long to start) never holds the game back.
 */
@Composable
internal fun PlatformIntroVideo(address: String, onDone: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("PlatformIntroVideo")
    val finish by rememberUpdatedState(onDone)
    var started by remember(address) { mutableStateOf(false) }
    // Never wait for a video that doesn't start.
    LaunchedEffect(address) {
        kotlinx.coroutines.delay(9_000)
        if (!started) finish()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { finish() } }
    ) {
        AndroidView(
            factory = { context ->
                val view = TextureView(context)
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val player = MediaPlayer()
                        view.tag = player
                        val ok = runCatching {
                            surface = Surface(texture)
                            player.setSurface(surface)
                            player.setDataSource(context, Uri.parse(address))
                            player.setOnPreparedListener { mp -> fitInside(view, mp.videoWidth, mp.videoHeight); mp.start(); started = true }
                            player.setOnVideoSizeChangedListener { _, w, h -> fitInside(view, w, h) }
                            player.setOnCompletionListener { finish() }
                            player.setOnErrorListener { _, _, _ -> finish(); true }
                            player.prepareAsync()
                        }.isSuccess
                        if (!ok) finish()
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        (view.tag as? MediaPlayer)?.let { fitInside(view, it.videoWidth, it.videoHeight) }
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
            onRelease = { view ->
                (view.tag as? MediaPlayer)?.let { runCatching { it.release() } }
                view.tag = null
            },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            "Skip: any button or tap",
            color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** TextureView stretches video to its bounds: scale it back so the whole picture shows (black bars, no crop). */
private fun fitInside(view: TextureView, videoWidth: Int, videoHeight: Int) {
    if (videoWidth <= 0 || videoHeight <= 0 || view.width <= 0 || view.height <= 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = minOf(viewW / videoWidth, viewH / videoHeight)
    val matrix = Matrix()
    matrix.setScale(scale * videoWidth / viewW, scale * videoHeight / viewH, viewW / 2f, viewH / 2f)
    view.setTransform(matrix)
}
