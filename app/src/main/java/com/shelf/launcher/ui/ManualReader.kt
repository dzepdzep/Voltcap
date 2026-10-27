package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.ManualLinks
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/*
 * Update 202: the manual reader, and what the rest of Voltcap asks of manuals (whether a game has one, its booklet's
 * cover, the download's progress window).
 */

/**
 * A game's manual, full screen, drawn by Android's own PDF renderer (nothing leaves the device). Each page is rendered
 * at the screen's size on a background thread; the page before and the two after are kept ready, so turning is instant.
 *  - ◀ ▶ (or L1 / R1) turn the page with a slide; zoomed in, ◀ ▶ ▲ ▼ move round the page first (at its edge ◀ ▶ turn).
 *  - L2 / R2: first / last page. A zooms in (2×) and out. B closes.
 *  - Touch: swipe to turn, pinch to zoom (drag moves the zoomed page), tap the left / right edge to turn, the middle to
 *    show or hide the page counter.
 */
@Composable
internal fun ManualReader(file: File, title: String, onDismiss: () -> Unit) {
    val pfd = remember(file) { runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() }
    val renderer = remember(pfd) { pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() } }
    // Closed under the page lock, so a page still rendering finishes first; the renderer owns pfd and closes it too.
    DisposableEffect(renderer) {
        onDispose {
            if (renderer != null) runCatching { synchronized(ManualLinks.pdfLock) { renderer.close() } }
            else runCatching { pfd?.close() }
        }
    }
    val count = renderer?.pageCount ?: 0
    var page by remember(file) { mutableIntStateOf(0) }
    // Rendered pages, by number (the one shown, the one before, the two after).
    val pages = remember(renderer) { mutableStateMapOf<Int, Bitmap>() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // The finger's drag of the page (px), and the slide of a page turn (the new page's offset, easing to 0).
    var dragX by remember { mutableFloatStateOf(0f) }
    val slide = remember { Animatable(0f) }
    var outgoing by remember { mutableStateOf<Int?>(null) }
    var dir by remember { mutableIntStateOf(1) }
    var bar by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val sounds = LocalUiSounds.current
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        // Inside the dialog: its window has to exist before the box can take focus (and the keys).
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0C0F))) {
            val boxW = constraints.maxWidth.coerceAtLeast(1)
            val boxH = constraints.maxHeight.coerceAtLeast(1)
            val w = boxW.toFloat()

            // The pages around the one shown, rendered one after another off the main thread; the rest let go.
            LaunchedEffect(renderer, page, boxW, boxH) {
                val r = renderer ?: return@LaunchedEffect
                val keep = (page - 1)..(page + 2)
                pages.keys.filter { it !in keep && it != outgoing }.forEach { pages.remove(it) }
                for (i in listOf(page, page + 1, page - 1, page + 2)) {
                    if (i !in 0 until count || pages.containsKey(i)) continue
                    val b = withContext(Dispatchers.IO) { renderManualPage(r, i, boxW, boxH, 1f) } ?: continue
                    pages[i] = b
                }
            }
            // Zoomed in: the page shown again, sharper (about twice the screen), once the zoom has settled.
            val sharp by produceState<Pair<Int, Bitmap>?>(null, renderer, page, zoom > 1.25f, boxW, boxH) {
                val r = renderer
                if (r == null || zoom <= 1.25f) { value = null; return@produceState }
                kotlinx.coroutines.delay(180)
                value = withContext(Dispatchers.IO) { renderManualPage(r, page, boxW, boxH, 2.2f) }?.let { page to it }
            }

            // How big the shown page is on screen (fitted), for keeping a zoomed page on the screen.
            fun fitted(b: Bitmap?): Pair<Float, Float> {
                if (b == null) return w to boxH.toFloat()
                val k = minOf(w / b.width, boxH.toFloat() / b.height)
                return b.width * k to b.height * k
            }
            fun clampPan(p: Offset, z: Float = zoom): Offset {
                val (fw, fh) = fitted(pages[page])
                val mx = ((fw * z - w) / 2f).coerceAtLeast(0f)
                val my = ((fh * z - boxH) / 2f).coerceAtLeast(0f)
                return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
            }
            fun settleDrag() {
                val from = dragX
                if (from == 0f) return
                scope.launch { animate(from, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> dragX = v } }
            }
            fun turnTo(to0: Int) {
                val to = to0.coerceIn(0, (count - 1).coerceAtLeast(0))
                if (count == 0 || to == page) { settleDrag(); return }
                val d = if (to > page) 1 else -1
                val startX = slide.value + dragX
                dir = d; outgoing = page; page = to
                zoom = 1f; pan = Offset.Zero; dragX = 0f
                sounds?.play(UiSound.MOVE)
                scope.launch {
                    // The new page comes in from the side it lies on, the old one slides off the other way.
                    slide.snapTo(startX + d * w)
                    slide.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                    outgoing = null
                }
            }
            fun setZoom(z: Float) {
                zoom = z.coerceIn(1f, 4f)
                pan = if (zoom <= 1.01f) Offset.Zero else clampPan(pan)
            }
            /** Zoomed in: moves round the page; false when it's already at that edge. */
            fun panBy(dx: Float, dy: Float): Boolean {
                if (zoom <= 1.01f) return false
                val next = clampPan(pan + Offset(dx, dy))
                if (next == pan) return false
                pan = next
                return true
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val step = w * 0.2f
                        when (e.key) {
                            Key.ButtonR1, Key.PageDown -> { turnTo(page + 1); true }
                            Key.ButtonL1, Key.PageUp -> { turnTo(page - 1); true }
                            Key.DirectionRight -> { if (!panBy(-step, 0f)) turnTo(page + 1); true }
                            Key.DirectionLeft -> { if (!panBy(step, 0f)) turnTo(page - 1); true }
                            Key.DirectionDown -> { panBy(0f, -step); true }
                            Key.DirectionUp -> { panBy(0f, step); true }
                            Key.ButtonL2, Key.MoveHome -> { turnTo(0); true }
                            Key.ButtonR2, Key.MoveEnd -> { turnTo(count - 1); true }
                            Key.ButtonA, Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                setZoom(if (zoom > 1.01f) 1f else 2f); sounds?.play(UiSound.SELECT); true
                            }
                            Key.ButtonY -> { bar = !bar; true }
                            Key.ButtonB, Key.Back, Key.Escape -> { sounds?.play(UiSound.BACK); onDismiss(); true }
                            else -> false
                        }
                    }
                    .pointerInput(count, boxW, boxH) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var moved = 0f
                            var multi = false
                            var swiping = false
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                if (fingers >= 2) multi = true
                                val zc = event.calculateZoom()
                                val p = event.calculatePan()
                                moved += abs(p.x) + abs(p.y)
                                if (multi || zoom > 1.01f) {
                                    // Pinch zooms, a drag moves the zoomed page.
                                    val z = (zoom * zc).coerceIn(1f, 4f)
                                    zoom = z
                                    pan = if (z <= 1.01f) Offset.Zero else clampPan(pan + p, z)
                                } else if (moved > slop || swiping) {
                                    swiping = true
                                    dragX += p.x
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.any { it.pressed })
                            when {
                                swiping && !multi -> when {
                                    dragX < -w * 0.18f -> turnTo(page + 1)
                                    dragX > w * 0.18f -> turnTo(page - 1)
                                    else -> settleDrag()
                                }
                                !multi && moved <= slop && zoom <= 1.01f -> {
                                    val x = down.position.x
                                    when {
                                        x < w * 0.28f -> turnTo(page - 1)
                                        x > w * 0.72f -> turnTo(page + 1)
                                        else -> bar = !bar
                                    }
                                }
                                else -> settleDrag()
                            }
                        }
                    }
            ) {
                if (renderer == null) {
                    Text("This manual can't be opened.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    val x = slide.value + dragX
                    val out = outgoing
                    // The page beside: the one sliding off, or the one a drag is pulling in.
                    val beside: Pair<Int, Float>? = when {
                        out != null -> out to (x - dir * w)
                        x < -0.5f -> (page + 1) to (x + w)
                        x > 0.5f -> (page - 1) to (x - w)
                        else -> null
                    }
                    if (beside != null && beside.first in 0 until count) {
                        ManualPage(pages[beside.first], title, Modifier.fillMaxSize().graphicsLayer { translationX = beside.second })
                    }
                    val shownSharp = sharp?.takeIf { it.first == page && zoom > 1.25f }?.second
                    val current = shownSharp ?: pages[page]
                    if (current != null) {
                        ManualPage(
                            current, title,
                            Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom; scaleY = zoom
                                translationX = slide.value + dragX + pan.x
                                translationY = pan.y
                            }
                        )
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = ShelfTheme.accent, strokeWidth = 3.dp)
                    }
                }
                if (bar || count == 0) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.66f), uiRounded(14.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (count > 0) "$title  ·  ${page + 1} / $count" else title,
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "◀ ▶  L1 R1  pages  ·  A zoom  ·  B close" + if (zoom > 1.01f) "  ·  ▲ ▼ ◀ ▶ move" else "",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1
                        )
                    }
                }
                if (bar) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    CloseButton(onClick = onDismiss)
                }
            }
        }
    }
}

/** One page, fitted in the screen. */
@Composable
private fun ManualPage(b: Bitmap?, title: String, modifier: Modifier) {
    if (b == null) return
    val img = remember(b) { b.asImageBitmap() }
    Image(img, title, contentScale = ContentScale.Fit, modifier = modifier)
}

/**
 * Page [index] of [r], fitted in [boxW] × [boxH] px times [scale] (kept under about 12 million pixels). Under the
 * app's PDF lock (ManualLinks.pdfLock): one page at a time, and never while the renderer is being closed.
 */
private fun renderManualPage(r: PdfRenderer, index: Int, boxW: Int, boxH: Int, scale: Float): Bitmap? = runCatching {
    synchronized(ManualLinks.pdfLock) {
        r.openPage(index).use { p ->
            val pw = p.width.coerceAtLeast(1).toFloat(); val ph = p.height.coerceAtLeast(1).toFloat()
            var k = minOf(boxW / pw, boxH / ph) * scale
            val maxPixels = 12_000_000f
            if (pw * k * ph * k > maxPixels) k = kotlin.math.sqrt(maxPixels / (pw * ph))
            val w = (pw * k).toInt().coerceIn(1, 4096)
            val h = (ph * k).toInt().coerceIn(1, 4096)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
}.getOrNull()

/**
 * Update 202: whether [g] has a manual — a PDF on the device (beside the ROM, picked, downloaded) or one ScreenScraper
 * lists (ManualLinks) — for the booklet in its opened case and the Manual buttons. Follows new addresses and downloads.
 */
@Composable
internal fun rememberHasManual(g: Game?): Boolean {
    if (g == null) return false
    val context = LocalContext.current.applicationContext
    val listed = ManualLinks.url(g.stableKey) != null
    val v = ManualLinks.version
    val onDevice by produceKeyed(false, g.stableKey, g.stableKey, v, listed) {
        if (listed) return@produceKeyed
        value = withContext(Dispatchers.IO) { runCatching { com.shelf.launcher.data.Manuals.local(context, g) != null }.getOrDefault(false) }
    }
    return listed || onDevice
}

/** Update 202: the manual's first page, small, for the booklet — null while it isn't on the device (or [on] is false). */
@Composable
internal fun rememberManualCover(g: Game, on: Boolean): Bitmap? {
    val context = LocalContext.current.applicationContext
    val v = ManualLinks.version
    val cover by produceKeyed<Bitmap?>(null, g.stableKey, g.stableKey, on, v) {
        value = if (!on) null else withContext(Dispatchers.IO) { runCatching { ManualLinks.firstPage(context, g) }.getOrNull() }
    }
    return cover
}

/**
 * Update 202: the manual on its way down (the first time it's opened): its progress, in Voltcap's window style; B (or
 * Stop) stops it.
 */
@Composable
internal fun ManualDownloadDialog() {
    val p = ManualLinks.progress ?: return
    val stop = { ManualLinks.cancel?.invoke(); Unit }
    ShelfDialog(
        icon = androidx.compose.material.icons.Icons.Filled.MenuBook,
        title = "Getting the manual",
        subtitle = gameDisplayName(p.game.name),
        onDismiss = stop,
        widthFraction = 0.6f
    ) {
        val frac = if (p.total > 0L) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else null
        if (frac != null) {
            LinearProgressIndicator(progress = { frac }, color = ShelfTheme.accent, modifier = Modifier.fillMaxWidth().height(6.dp))
        } else {
            LinearProgressIndicator(color = ShelfTheme.accent, modifier = Modifier.fillMaxWidth().height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        val mb = { b: Long -> String.format(java.util.Locale.ROOT, "%.1f MB", b / 1_048_576f) }
        MutedNote(
            (if (frac != null) "${(frac * 100).toInt()} %  ·  ${mb(p.done)} of ${mb(p.total)}" else "${mb(p.done)} so far") +
                " — downloaded once from ScreenScraper and kept with your saved art. B stops."
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow { Chip("Stop", accent = false, onClick = stop) }
    }
}
