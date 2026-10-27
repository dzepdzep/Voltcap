package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.PlayArrow
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What sits behind the row of games (Appearance › Colours & background › Dock backing): nothing, a solid
 * colour of your choice, or your own animated WebP / GIF, as a band behind the row with soft top and
 * bottom edges.
 *
 * The animation is decoded once at the band's size (half that on weaker / small-memory handhelds), loops
 * on its own, and stops whenever the home screen is covered, in the background, the screen is off or
 * the home has been idle for minutes. Files over 40 MB, failed decodes and Android 8 fall back to the
 * first frame (a still), and a picture that can't be read at all falls back to the colour.
 */
@Composable
internal fun BoxScope.DockBacking(rowTopPx: Float, rowBottomPx: Float) {
    val mode = Features.dockBacking.state.value
    if (mode == Features.DockBack.OFF || rowBottomPx <= rowTopPx) return
    val density = LocalDensity.current
    val pad = with(density) { 22.dp.toPx() }
    val top = rowTopPx - pad
    val h = (rowBottomPx - rowTopPx) + pad * 2
    val color = parseHex(Features.dockColor.state.value) ?: 0xFF101218.toInt()
    val opacity = Features.dockOpacity.state.value.toIntOrNull()?.coerceIn(10, 100) ?: 85
    val band = Modifier
        .fillMaxWidth()
        .height(with(density) { h.toDp() })
        .offset(y = with(density) { top.toDp() })
        // Soft edges: the band fades out at the top and bottom instead of a hard line.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = opacity / 100f }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn
            )
        }
    Box(band.align(Alignment.TopStart)) {
        when (mode) {
            Features.DockBack.COLOR -> Box(Modifier.fillMaxSize().background(Color(color)))
            Features.DockBack.IMAGE -> {
                val uri = Features.dockImage.state.value
                if (uri.isBlank()) Box(Modifier.fillMaxSize().background(Color(color)))
                else DockAnimatedImage(uri, Color(color))
            }
            Features.DockBack.OFF -> Unit
        }
    }
}

private const val MAX_BYTES = 40L * 1024 * 1024

@Composable
private fun DockAnimatedImage(uri: String, fallback: Color) {
    val context = LocalContext.current
    val active = rememberIsActive() && !LocalSceneCovered.current && !HomeIdle.still
    val low = remember { com.shelf.launcher.device.DevicePerformance.isLowEnd(context) || com.shelf.launcher.device.MemoryBudget.small(context) }
    // null = still loading; Result.failure = use the still / colour.
    val drawable by produceKeyed<Result<Drawable>?>(null, uri, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT < 28) error("no animated decoder")
                val size = runCatching {
                    context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { it.length }
                }.getOrNull() ?: -1L
                if (size > MAX_BYTES) error("too big")
                val src = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
                ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                    // The band is the screen's width and a third of its height at most: decode no bigger.
                    val dm = context.resources.displayMetrics
                    val maxW = dm.widthPixels / (if (low) 2 else 1)
                    val maxH = dm.heightPixels / (if (low) 4 else 2)
                    val k = minOf(1f, maxOf(maxW.toFloat() / info.size.width, maxH.toFloat() / info.size.height))
                    if (k < 1f) decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
                }
            }
        }
    }
    val d = drawable
    when {
        d == null -> Box(Modifier.fillMaxSize().background(fallback))
        d.isFailure -> {
            // A still (first frame) through the image loader; the colour if even that fails.
            Box(Modifier.fillMaxSize().background(fallback)) {
                AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        else -> {
            val dr = d.getOrNull()!!
            LaunchedEffect(dr, active) {
                if (Build.VERSION.SDK_INT >= 28) (dr as? AnimatedImageDrawable)?.let {
                    it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    if (active) it.start() else it.stop()
                }
            }
            DisposableEffect(dr) { onDispose { if (Build.VERSION.SDK_INT >= 28) (dr as? AnimatedImageDrawable)?.stop() } }
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { it.setImageDrawable(dr) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Appearance › Colours & background: the dock backing. */
@Composable
internal fun DockBackingSettings() {
    val context = LocalContext.current
    val F = Features
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            F.dockImage.value = uri.toString()
            F.dockBacking.value = Features.DockBack.IMAGE
        }
    }
    androidx.compose.foundation.layout.Column {
        SectionLabel("Above the row")
        SwitchRow(
            androidx.compose.material.icons.Icons.Filled.PlayArrow, "Game header",
            "The game you're on as a big title with stat boxes and a long Play pill with round buttons, above the row (classic row layout). Off: just the name line.",
            F.homeHeader.state.value, onChange = { F.homeHeader.value = it }
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel("Blur")
        PercentSlider(
            "Blur behind the games", F.rowBlur.state.value, { F.rowBlur.value = it }, min = 0, max = 100, live = true,
            valueText = { if (it == 0) "Off" else "$it %" }
        )
        PercentSlider(
            "Blur behind the names", F.nameBlur.state.value, { F.nameBlur.value = it }, min = 0, max = 100, live = true,
            valueText = { if (it == 0) "Crisp" else "$it %" }
        )
        MutedNote("Behind the games: the soft blur and shade behind the row, its names and the categories. Behind the names: the shadow or glow round the game's name and the category (Home › Name & logo › Style).")
        Spacer(Modifier.height(8.dp))
        // Update 112: names that are hard to read on a busy picture.
        SectionLabel("Names over pictures")
        ChipFlow {
            Features.NameContrast.values().forEach { v -> Chip(v.label, accent = F.nameContrast.state.value == v) { F.nameContrast.value = v } }
        }
        MutedNote("Soft shadow: as before. Dark edge: a tight dark edge round the letters. Dark pill: that, and the Icon bar's small name under the tiles sits on a dark pill.")
        Spacer(Modifier.height(12.dp))
        SectionLabel("Dock backing")
        MutedNote("What sits right behind the row of games: nothing, a solid colour, or your own animated WebP / GIF (loops, pauses when the home screen isn't in view; a still on older Android).")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Features.DockBack.values().forEach { m ->
                Chip(m.label, accent = F.dockBacking.state.value == m) {
                    if (m == Features.DockBack.IMAGE && F.dockImage.value.isBlank()) pick.launch(arrayOf("image/webp", "image/gif", "image/*"))
                    else F.dockBacking.value = m
                }
            }
            if (F.dockBacking.state.value == Features.DockBack.IMAGE) {
                Chip("Choose another…", accent = false) { pick.launch(arrayOf("image/webp", "image/gif", "image/*")) }
            }
        }
        if (F.dockBacking.state.value != Features.DockBack.OFF) {
            Spacer(Modifier.height(10.dp))
            val c = parseHex(F.dockColor.state.value) ?: 0xFF101218.toInt()
            Text2("Colour" + if (F.dockBacking.state.value == Features.DockBack.IMAGE) " (behind / instead of the picture)" else "")
            HueSlider(c) { picked ->
                val h = FloatArray(3).also { android.graphics.Color.colorToHSV(picked, it) }
                val cur = FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }
                F.dockColor.value = hexOf(android.graphics.Color.HSVToColor(floatArrayOf(h[0], cur[1], cur[2])))
            }
            Spacer(Modifier.height(8.dp))
            ColorFineTune("Dock", c) { F.dockColor.value = hexOf(it) }
            val op = F.dockOpacity.state.value.toIntOrNull() ?: 85
            // (update 91) The slider starts at 10 like the saved value (it showed 3 % while 10 % was kept).
            PercentSlider("Opacity", op.coerceIn(10, 100), { F.dockOpacity.value = it.coerceIn(10, 100).toString() }, min = 10)
        }
    }
}

@Composable
private fun Text2(text: String) = androidx.compose.material3.Text(text, color = com.shelf.launcher.ui.theme.ShelfTheme.textMuted, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp))
