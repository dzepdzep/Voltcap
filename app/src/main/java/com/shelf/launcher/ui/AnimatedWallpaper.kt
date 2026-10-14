package com.shelf.launcher.ui

import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Your wallpaper" behind the dock, now also moving: a GIF, an animated WebP / PNG or a short video
 * (MP4, WebM…) the user picked. Plain pictures still show as before.
 *
 * Battery: it plays only while the home screen is in front with the screen on, and nothing covers it;
 * it holds still while the handheld sits idle for a few minutes (Save power when idle) and while a game
 * runs. Animated pictures are decoded at screen size (smaller on low-end devices); videos are decoded
 * by the hardware, muted, looping.
 */
@Composable
internal fun UserWallpaper(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val kind = remember(uri) { WallpaperKind.of(context, uri) }
    val active = rememberIsActive() && !LocalSceneCovered.current && !HomeIdle.still
    when (kind) {
        // Keyed on the file: the view and its player are made for one video, so a new pick gets a new one.
        WallpaperKind.VIDEO -> androidx.compose.runtime.key(uri) { WallpaperVideo(uri, active, modifier) }
        WallpaperKind.ANIMATED -> if (Build.VERSION.SDK_INT >= 28) WallpaperAnimatedImage(uri, active, modifier)
            else StillWallpaper(uri, modifier)
        WallpaperKind.STILL -> StillWallpaper(uri, modifier)
    }
}

@Composable
private fun StillWallpaper(uri: String, modifier: Modifier) {
    AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
}

internal enum class WallpaperKind {
    STILL, ANIMATED, VIDEO;

    companion object {
        /** From the file's type (GIF / WebP / APNG count as possibly animated; a still WebP just shows as one frame). */
        fun of(context: android.content.Context, uri: String): WallpaperKind {
            val type = runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull().orEmpty().lowercase()
            val name = uri.lowercase()
            return when {
                type.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mkv") -> VIDEO
                type == "image/gif" || type == "image/webp" || type == "image/apng" ||
                    name.endsWith(".gif") || name.endsWith(".webp") -> ANIMATED
                else -> STILL
            }
        }
    }
}

@androidx.annotation.RequiresApi(28)
@Composable
private fun WallpaperAnimatedImage(uri: String, active: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val low = remember { com.shelf.launcher.device.DevicePerformance.isLowEnd(context) || com.shelf.launcher.device.MemoryBudget.small(context) }
    val drawable by produceState<Drawable?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val src = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                    // Never bigger than the screen; half that on low-end devices (it's behind a scrim anyway).
                    val dm = context.resources.displayMetrics
                    val maxSide = maxOf(dm.widthPixels, dm.heightPixels) / (if (low) 2 else 1)
                    val w = info.size.width
                    val h = info.size.height
                    val k = maxSide.toFloat() / maxOf(w, h)
                    if (k < 1f) decoder.setTargetSize((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                }
            }.getOrNull()
        }
    }
    val d = drawable
    if (d == null) { StillWallpaper(uri, modifier); return }
    LaunchedEffect(d, active) {
        (d as? AnimatedImageDrawable)?.let {
            // Loops even when the file says to play once (like the dock backing).
            it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            if (active) it.start() else it.stop()
        }
    }
    DisposableEffect(d) { onDispose { (d as? AnimatedImageDrawable)?.stop() } }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { it.setImageDrawable(d) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
internal fun WallpaperVideo(
    uri: String, active: Boolean, modifier: Modifier,
    /** Update 110: each new video frame on screen, with the player's position (ms). */
    onFrame: ((Long) -> Unit)? = null
) {
    val frameCallback = androidx.compose.runtime.rememberUpdatedState(onFrame)
    // The player, whether it's prepared yet, and whether it should play: start/pause only once prepared
    // (calling start() before that puts MediaPlayer in its error state), and the prepared callback reads
    // the latest wish rather than the one from when the view was made.
    val player = remember(uri) { arrayOfNulls<MediaPlayer>(1) }
    val prepared = remember(uri) { booleanArrayOf(false) }
    val wantPlay = remember(uri) { booleanArrayOf(active) }
    wantPlay[0] = active
    LaunchedEffect(active, uri) {
        val p = player[0] ?: return@LaunchedEffect
        if (!prepared[0]) return@LaunchedEffect
        runCatching { if (active) { if (!p.isPlaying) p.start() } else if (p.isPlaying) p.pause() }
    }
    AndroidView(
        factory = { context ->
            val view = TextureView(context)
            view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                // Released with the player (each one holds a native buffer queue).
                var surface: Surface? = null
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    val mp = MediaPlayer()
                    player[0] = mp
                    prepared[0] = false
                    runCatching {
                        surface = Surface(texture)
                        mp.setSurface(surface)
                        mp.setDataSource(context, Uri.parse(uri))
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        mp.setOnPreparedListener {
                            prepared[0] = true
                            fitVideo(view, it.videoWidth, it.videoHeight)
                            // Show the first frame even when paused.
                            it.start()
                            if (!wantPlay[0]) it.pause()
                        }
                        mp.setOnVideoSizeChangedListener { _, w, h -> fitVideo(view, w, h) }
                        mp.setOnErrorListener { _, _, _ -> true }
                        mp.prepareAsync()
                    }
                }

                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    player[0]?.let { fitVideo(view, it.videoWidth, it.videoHeight) }
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    player[0]?.let { runCatching { it.release() } }
                    player[0] = null
                    prepared[0] = false
                    surface?.let { runCatching { it.release() } }
                    surface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                    val cb = frameCallback.value ?: return
                    val p = player[0] ?: return
                    if (!prepared[0]) return
                    runCatching { p.currentPosition.toLong() }.getOrNull()?.let(cb)
                }
            }
            view
        },
        onRelease = {
            player[0]?.let { runCatching { it.release() } }
            player[0] = null
            prepared[0] = false
        },
        modifier = modifier.fillMaxSize()
    )
}

/** Covers the view, keeping the video's shape (the overflow is cropped evenly). */
private fun fitVideo(view: TextureView, vw: Int, vh: Int) {
    if (vw <= 0 || vh <= 0 || view.width <= 0 || view.height <= 0) return
    val w = view.width.toFloat()
    val h = view.height.toFloat()
    val s = maxOf(w / vw, h / vh)
    val m = android.graphics.Matrix()
    m.setScale(s * vw / w, s * vh / h, w / 2f, h / 2f)
    view.setTransform(m)
}
