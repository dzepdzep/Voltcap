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
                    Image(img, contentDescription = "Full case scan", contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
                    val accent = ShelfTheme.accent
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
                        drawLine(if (edge == 0) accent else Color.White, Offset(l * w, 0f), Offset(l * w, h), strokeWidth = if (edge == 0) 5f else 3f)
                        drawLine(if (edge == 1) accent else Color.White, Offset(r * w, 0f), Offset(r * w, h), strokeWidth = if (edge == 1) 5f else 3f)
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
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    Chip("Save the cuts") { FullScanCuts.set(url, l to r); onClose() }
                    Chip(if (auto != null) "Automatic" else "Automatic (none found)", accent = false) { FullScanCuts.set(url, null); auto?.let { l = it.first; r = it.second }; onClose() }
                    Chip("Cancel", accent = false) { onClose() }
                }
            }
        }
    }
}
