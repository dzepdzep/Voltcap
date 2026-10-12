package com.shelf.launcher.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * The second screen's Magnifier page (dual-screen mode): the main screen's picture, enlarged, on the second screen.
 * It uses the same screen capture as the Screen Mirror widget (Android asks once on the main screen), but fills the page:
 * pinch to zoom, drag to move, double-tap to go back to the whole picture, zoom steps along the top, and a small
 * map in the corner showing which part of the main screen you are looking at.
 */

/** Whether a real second screen is in use (the Magnifier would only capture its own picture in the virtual panel). Set by the launcher screen. */
internal object SecondScreenReal { var active by androidx.compose.runtime.mutableStateOf(false) }

@Composable
internal fun MagnifierPage(modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("MagnifierPage")
    val context = LocalContext.current
    val running by ScreenMirror.running
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        if (!SecondScreenReal.active) {
            Text(
                "The magnifier needs a real second screen: in the on-screen panel it would only show itself.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        } else if (!running) {
            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Magnifier", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "This screen shows the main screen's picture, enlarged. Android asks for permission to capture the screen on the main screen first.",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Chip("Start magnifier") { ScreenMirror.requestStart() }
            }
        } else MagnifierPicture(onStop = { ScreenMirror.stop(context) })
    }
}

private val ZOOM_STEPS = listOf(100, 150, 200, 300, 400, 600)

@Composable
private fun MagnifierPicture(onStop: () -> Unit) {
    val context = LocalContext.current
    val capture = remember { ScreenMirror.captureSize(context) }
    var scale by remember { mutableFloatStateOf((Features.magnifierZoom.value / 100f).coerceIn(1f, 8f)) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val ratio = capture.first.toFloat() / capture.second.toFloat()
    // Keeps the zoom the next time the page opens.
    DisposableEffect(Unit) { onDispose { Features.magnifierZoom.value = (scale * 100f).toInt().coerceIn(100, 800) } }

    // The picture is as large as fits the page (its own shape, bars where the shapes differ); pan stays inside it.
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { box = it }, contentAlignment = Alignment.Center) {
        val pw = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
        val ph = if (maxWidth / maxHeight > ratio) maxHeight else maxWidth / ratio
        val density = androidx.compose.ui.platform.LocalDensity.current
        val picW = with(density) { pw.toPx() }
        val picH = with(density) { ph.toPx() }
        fun clampPan(p: Offset, s: Float): Offset {
            val mx = (picW * s - picW) / 2f
            val my = (picH * s - picH) / 2f
            return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
        }
        fun zoomTo(s: Float, about: Offset = Offset.Zero) {
            val ns = s.coerceIn(1f, 8f)
            // Keeps the point under the fingers (or the centre) where it is.
            pan = clampPan(about - (about - pan) * (ns / scale), ns)
            scale = ns
            if (ns <= 1.01f) pan = Offset.Zero
        }
        Box(
            Modifier
                .size(pw, ph)
                .clipToBounds()
                .pointerInput(picW, picH) {
                    detectTransformGestures { centroid, drag, zoom, _ ->
                        val about = centroid - Offset(picW / 2f, picH / 2f)
                        val ns = (scale * zoom).coerceIn(1f, 8f)
                        pan = clampPan(about - (about - pan) * (ns / scale) + drag, ns)
                        scale = ns
                        if (ns <= 1.01f) pan = Offset.Zero
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; pan = Offset.Zero }) }
        ) {
            AndroidView(
                factory = { ctx ->
                    val view = TextureView(ctx)
                    view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        var surface: Surface? = null
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            texture.setDefaultBufferSize(capture.first, capture.second)
                            surface = Surface(texture)
                            ScreenMirror.attach(ctx, surface!!, capture.first, capture.second, capture.third)
                        }
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            ScreenMirror.detachSurface(surface)
                            surface?.let { runCatching { it.release() } }
                            surface = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
                    }
                    view
                },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
                }
            )
        }
        // Zoom steps and Stop along the top.
        Row(Modifier.align(Alignment.TopCenter).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ZOOM_STEPS.forEach { z ->
                val on = kotlin.math.abs(scale * 100f - z) < 4f
                Chip((if (z == 100) "Fit" else "${z / 100f}×".replace(".0×", "×")), accent = on) { zoomTo(z / 100f) }
            }
            Chip("Stop", accent = false) { onStop() }
        }
        // The map: where on the main screen this page is looking.
        if (scale > 1.05f) {
            val accent = ShelfTheme.accent
            Canvas(
                Modifier.align(Alignment.BottomEnd).padding(10.dp).size(76.dp, 76.dp / ratio)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            ) {
                val vw = size.width / scale
                val vh = size.height / scale
                val cx = size.width / 2f - pan.x / picW * size.width / scale
                val cy = size.height / 2f - pan.y / picH * size.height / scale
                drawRect(accent.copy(alpha = 0.25f), Offset(cx - vw / 2f, cy - vh / 2f), Size(vw, vh))
                drawRect(accent, Offset(cx - vw / 2f, cy - vh / 2f), Size(vw, vh), style = Stroke(2f))
            }
        }
    }
}
