package com.shelf.launcher.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shelf.launcher.data.Features
import com.shelf.launcher.device.RootShell
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Screen Mirror: the primary screen's picture on the second screen (a widget on the Widget Board).
 * Android asks the person once per session (the system's "start recording" prompt on the main screen), and a
 * foreground service ([MirrorService]) keeps the capture alive. Two ways to use the picture:
 *  - Zoom: pinch to zoom in, drag to move around.
 *  - Trackpad: drag moves a pointer on the primary screen, a tap clicks there (needs the handheld's system shell).
 */
object ScreenMirror {
    val running = mutableStateOf(false)

    /** Set by the launcher screen: shows Android's screen-capture prompt on the main screen. */
    var onRequest: (() -> Unit)? = null

    @Volatile internal var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    /** The surface the capture is drawing on now (a view that is going away only lets go of its own). */
    private var attachedSurface: Surface? = null

    /** Shows Android's capture prompt, unless a capture is already running (a second one would replace the first). */
    fun requestStart() { if (!running.value) onRequest?.invoke() }

    /** What the capture is sized to: the primary screen's shape, at most 1280 px wide. */
    internal fun captureSize(context: Context): Triple<Int, Int, Int> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(metrics)
        val scale = minOf(1f, 1280f / metrics.widthPixels)
        val w = (metrics.widthPixels * scale).toInt().coerceAtLeast(2) and 1.inv()
        val h = (metrics.heightPixels * scale).toInt().coerceAtLeast(2) and 1.inv()
        return Triple(w, h, (metrics.densityDpi * scale).toInt().coerceAtLeast(60))
    }

    /** Real pixel size of the primary screen (for the trackpad's clicks). */
    internal fun primarySize(context: Context): Pair<Int, Int> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    internal fun attach(context: Context, surface: Surface, w: Int, h: Int, dpi: Int) {
        val p = projection ?: return
        // (update 91) One virtual display per capture: Android 14+ refuses a second createVirtualDisplay on the
        // same projection (the picture went black when the widget came back), so a new surface is swapped in.
        display?.let { d ->
            if (runCatching { d.surface = surface }.isSuccess) { attachedSurface = surface; return }
            detach()
        }
        display = runCatching {
            p.createVirtualDisplay("VoltcapMirror", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
        }.getOrNull()
        attachedSurface = if (display != null) surface else null
    }

    /** The widget's picture went away: the capture keeps running, drawing nowhere until the next [attach]. */
    internal fun detachSurface(from: Surface? = null) {
        // A newer picture may already have taken over: the old view going away must not blank it.
        if (from != null && attachedSurface !== from) return
        runCatching { display?.surface = null }
        attachedSurface = null
    }

    internal fun detach() {
        runCatching { display?.release() }
        display = null
        attachedSurface = null
    }

    fun stop(context: Context) {
        detach()
        runCatching { projection?.stop() }
        projection = null
        running.value = false
        runCatching { context.stopService(Intent(context, MirrorService::class.java)) }
    }

    internal fun onProjectionStopped() {
        detach()
        projection = null
        running.value = false
    }

    /** The intent for Android's capture prompt. */
    fun consentIntent(context: Context): Intent {
        val m = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Update 112: Android 14+ offers "a single app" in the prompt, which shows only that app (black once you leave
        // it): ask for the whole main screen, which is what the mirror and the magnifier need.
        if (Build.VERSION.SDK_INT >= 34) runCatching {
            return m.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
        }
        return m.createScreenCaptureIntent()
    }

    /** After the person agreed: starts the service that holds the capture. */
    fun begin(context: Context, resultCode: Int, data: Intent) {
        val intent = Intent(context, MirrorService::class.java).putExtra("code", resultCode).putExtra("data", data)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }
}

/** Keeps the screen capture alive (Android requires a foreground service of the media-projection type). */
class MirrorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("mirror", "Screen mirror", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "mirror") else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("Voltcap is mirroring the main screen")
            .setContentText("Shown in the second screen's Widgets.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(77, notification)
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val projection = runCatching {
            (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
        }.getOrNull()
        if (projection == null) { stopSelf(); return START_NOT_STICKY }
        projection.registerCallback(object : MediaProjection.Callback() {
            // A capture that was replaced by a newer one must not end the newer one with it.
            override fun onStop() { if (ScreenMirror.projection === projection) { ScreenMirror.onProjectionStopped(); stopSelf() } }
        }, Handler(Looper.getMainLooper()))
        // A second capture started while one was running: the first is stopped (it kept recording, with its indicator on).
        ScreenMirror.projection?.takeIf { it !== projection }?.let { old ->
            ScreenMirror.detach()
            ScreenMirror.projection = projection
            runCatching { old.stop() }
        }
        ScreenMirror.projection = projection
        ScreenMirror.running.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ScreenMirror.onProjectionStopped()
        super.onDestroy()
    }
}

/** The Screen Mirror widget: start button, then the picture with the Zoom / Trackpad modes. */
@Composable
internal fun ScreenMirrorWidget(modifier: Modifier = Modifier) {
    com.shelf.launcher.Breadcrumbs.mark("ScreenMirrorWidget")
    val context = LocalContext.current
    val running by ScreenMirror.running
    val mode = Features.mirrorMode.state.value
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        if (!running) {
            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mirror the main screen here", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Android asks for permission on the main screen first.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Chip("Start mirroring") { ScreenMirror.requestStart() }
            }
        } else {
            MirrorPicture(mode)
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                Features.MirrorMode.values().forEach { m ->
                    Chip(m.label, accent = m == mode) { Features.mirrorMode.value = m }
                }
                Chip("Stop", accent = false) { ScreenMirror.stop(context) }
            }
        }
    }
}

@Composable
private fun MirrorPicture(mode: Features.MirrorMode) {
    val context = LocalContext.current
    val capture = remember { ScreenMirror.captureSize(context) }
    val primary = remember { ScreenMirror.primarySize(context) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // The trackpad's pointer, as fractions of the primary screen.
    var pointer by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    // Update 144: called boxSize (not size) so the Canvas below uses its own draw size, not this whole-number one.
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var note by remember { mutableStateOf<String?>(null) }
    val latestMode by rememberUpdatedState(mode)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(mode) {
                if (mode == Features.MirrorMode.ZOOM) {
                    detectTransformGestures { _, drag, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        pan = if (scale <= 1.01f) Offset.Zero else pan + drag
                    }
                } else {
                    detectTapGestures(
                        onTap = {
                            val x = (pointer.x * primary.first).toInt()
                            val y = (pointer.y * primary.second).toInt()
                            if (RootShell.available) { Thread { RootShell.exec("input tap $x $y") }.start(); note = null }
                            else note = "The trackpad needs this handheld's system shell, which isn't available here."
                        },
                        onDoubleTap = { scale = 1f; pan = Offset.Zero }
                    )
                }
            }
            .then(
                if (mode == Features.MirrorMode.TRACKPAD) Modifier.pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // The pointer moves like a trackpad: a drag across the widget crosses about the whole screen.
                        val w = boxSize.width.coerceAtLeast(1)
                        val h = boxSize.height.coerceAtLeast(1)
                        pointer = Offset((pointer.x + drag.x / w * 1.4f).coerceIn(0f, 1f), (pointer.y + drag.y / h * 1.4f).coerceIn(0f, 1f))
                    }
                } else Modifier
            )
    ) {
        AndroidView(
            factory = { ctx ->
                val view = TextureView(ctx)
                view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        texture.setDefaultBufferSize(capture.first, capture.second)
                        surface = Surface(texture)
                        fit(view, capture.first, capture.second)
                        ScreenMirror.attach(ctx, surface!!, capture.first, capture.second, capture.third)
                    }
                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { fit(view, capture.first, capture.second) }
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y }
        )
        if (latestMode == Features.MirrorMode.TRACKPAD) {
            Canvas(Modifier.fillMaxSize()) {
                // The pointer is drawn over the picture where the picture is (it keeps the primary screen's shape).
                // Update 144: fractional fit, so the dot follows the pointer instead of sitting in the middle.
                val fitScale = minOf(size.width / capture.first.toFloat(), size.height / capture.second.toFloat())
                val picW = capture.first * fitScale
                val picH = capture.second * fitScale
                val left = (size.width - picW) / 2f
                val top = (size.height - picH) / 2f
                val c = Offset(left + pointer.x * picW, top + pointer.y * picH)
                drawCircle(Color.White, radius = 9f, center = c)
                drawCircle(Color.Black, radius = 9f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }
        note?.let {
            Text(
                it, color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/** The capture keeps the primary screen's shape: scale the texture so all of it shows (bars at the sides). */
private fun fit(view: TextureView, w: Int, h: Int) {
    if (view.width <= 0 || view.height <= 0) return
    val vw = view.width.toFloat()
    val vh = view.height.toFloat()
    val s = minOf(vw / w, vh / h)
    val m = Matrix()
    m.setScale(s * w / vw, s * h / vh, vw / 2f, vh / 2f)
    view.setTransform(m)
}
