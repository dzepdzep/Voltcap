package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.FullScanCuts
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Update 301: Artwork & details › Full case scan › Adjust the cuts… — the scan with the spine's two edges as lines you
 * move: drag them (touch), or pick an edge and nudge it with ◀ ▶ (controller). It starts where the automatic cut would
 * be (BoxTexture.findSpine) or where you set it before. The picture keeps its own proportions; nothing is stretched.
 * Saved per scan on this device (FullScanCuts); "Automatic" goes back to the found cut.
 */
@Composable
internal fun FullScanCutEditor(url: String, platform: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    var bmp by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var auto by remember(url) { mutableStateOf<Pair<Float, Float>?>(null) }
    val saved = remember(url) { FullScanCuts.of(url) }
    var l by remember(url) { mutableFloatStateOf(saved?.first ?: 0.47f) }
    var r by remember(url) { mutableFloatStateOf(saved?.second ?: 0.53f) }
    var edge by remember { mutableIntStateOf(0) } // 0 = left edge, 1 = right edge
    // (Update 317, Toni: "add a zoom when adjusting the edges so it's easier to see": 1×, 3× or 6×, round the edge picked.)
    var zoom by remember { mutableFloatStateOf(1f) }
    // Update 305: the printer's bleed / border round the scan (a share of its height), trimmed off its outer edges.
    var bleed by remember(url) { mutableFloatStateOf(FullScanCuts.bleedOf(url)) }
    LaunchedEffect(url) {
        val b = withContext(Dispatchers.IO) {
            runCatching {
                val req = coil.request.ImageRequest.Builder(context).data(url).size(1600, 1600).allowHardware(false).build()
                drawableBitmap((coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable, 1600)
            }.getOrNull()
        }
        if (b == null) { failed = true; return@LaunchedEffect }
        val found = withContext(Dispatchers.Default) { runCatching { BoxTexture.findSpine(b, platform) }.getOrNull() }
        auto = found?.let { (a, c) -> a.toFloat() / b.width to c.toFloat() / b.width }
        if (saved == null) auto?.let { l = it.first; r = it.second }
        bmp = b
    }
    fun nudge(d: Float) {
        if (edge == 0) l = (l + d).coerceIn(0.05f, r - 0.004f) else r = (r + d).coerceIn(l + 0.004f, 0.95f)
    }
    Column {
        when {
            failed -> MutedNote("The scan couldn't be loaded.")
            bmp == null -> MutedNote("Loading the scan…")
            else -> {
                val b = bmp!!
                val img = remember(b) { b.asImageBitmap() }
                Box(
                    // (At most 220 dp tall, its own proportions: the dialog round it stays scrollable.)
                    Modifier.heightIn(max = 220.dp).fillMaxWidth().aspectRatio(b.width.toFloat() / b.height, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black)
                ) {
                    val accent = ShelfTheme.accent
                    // (Zoomed round the edge picked — it stays where it is, the scan grows round it; dragging moves the edge
                    // as finely as the zoom shows it.)
                    Box(Modifier.matchParentSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (edge == 0) l else r, 0.5f)
                    }) {
                    Image(img, contentDescription = "Full case scan", contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
                    Canvas(
                        Modifier.matchParentSize().pointerInput(url) {
                            // (Sideways only: an up / down swipe still scrolls the dialog.)
                            detectHorizontalDragGestures(
                                onDragStart = { o -> val x = o.x / size.width; edge = if (kotlin.math.abs(x - l) <= kotlin.math.abs(x - r)) 0 else 1 },
                                onHorizontalDrag = { ch, dx -> ch.consume(); nudge(dx / size.width) }
                            )
                        }
                    ) {
                        val w = size.width; val h = size.height
                        // The back and the front dimmed a little, the spine between the lines clear.
                        drawRect(Color.Black.copy(alpha = 0.18f), Offset(0f, 0f), Size(l * w, h))
                        drawRect(Color.Black.copy(alpha = 0.18f), Offset(r * w, 0f), Size(w - r * w, h))
                        // (Thin as ever when zoomed in.)
                        drawLine(if (edge == 0) accent else Color.White, Offset(l * w, 0f), Offset(l * w, h), strokeWidth = (if (edge == 0) 5f else 3f) / zoom)
                        drawLine(if (edge == 1) accent else Color.White, Offset(r * w, 0f), Offset(r * w, h), strokeWidth = (if (edge == 1) 5f else 3f) / zoom)
                        // (Update 305: the bleed trimmed off, shown as a frame inside the scan's edges.)
                        if (bleed > 0f) {
                            val bp = bleed * h
                            drawRect(Color(0xFFFFC107), Offset(bp, bp), Size(w - 2 * bp, h - 2 * bp), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f / zoom))
                        }
                    }
                    }
                    Text("Back", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.5f)))
                    Text("Front", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f)))
                }
                Spacer(Modifier.height(8.dp))
                MutedNote("Drag a line onto the case's fold, or pick an edge and move it with ◀ ▶. The spine is between the lines.")
                ChipFlow {
                    Chip("Left edge", accent = edge == 0) { edge = 0 }
                    Chip("Right edge", accent = edge == 1) { edge = 1 }
                    Chip("◀", accent = false) { nudge(-0.002f) }
                    Chip("▶", accent = false) { nudge(0.002f) }
                    Chip("◀◀", accent = false) { nudge(-0.01f) }
                    Chip("▶▶", accent = false) { nudge(0.01f) }
                }
                ChipFlow {
                    Chip("Zoom 1×", accent = zoom == 1f) { zoom = 1f }
                    Chip("Zoom 3×", accent = zoom == 3f) { zoom = 3f }
                    Chip("Zoom 6×", accent = zoom == 6f) { zoom = 6f }
                }
                Spacer(Modifier.height(6.dp))
                // Update 305: a cover printed with a bleed (or a white border) round it — The Cover Project's wraps often
                // are — has it trimmed off its outer edges, so the case's edges show the print's own edges.
                MutedNote("Bleed / border trimmed off the outer edges: " + if (bleed <= 0f) "none" else String.format(java.util.Locale.US, "%.1f %% of the height", bleed * 100f))
                ChipFlow {
                    Chip("Less", accent = false) { bleed = (bleed - 0.0025f).coerceAtLeast(0f) }
                    Chip("More", accent = false) { bleed = (bleed + 0.0025f).coerceAtMost(0.12f) }
                    Chip("None", accent = false) { bleed = 0f }
                }
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    Chip("Save the cuts") { FullScanCuts.set(url, l to r, bleed); onClose() }
                    Chip(if (auto != null) "Automatic" else "Automatic (none found)", accent = false) { FullScanCuts.set(url, null); auto?.let { l = it.first; r = it.second }; onClose() }
                    Chip("Cancel", accent = false) { onClose() }
                }
            }
        }
    }
}
