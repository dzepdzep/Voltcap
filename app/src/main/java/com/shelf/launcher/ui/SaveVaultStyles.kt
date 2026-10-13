package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.SaveVaultStyle
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.sin

// ---- Sync state: an icon + colour + a word, never colour alone ----

internal fun syncIcon(state: SyncState): ImageVector = when (state) {
    SyncState.SYNCED -> Icons.Filled.CloudDone
    SyncState.UPLOADING -> Icons.Filled.CloudUpload
    SyncState.DOWNLOADING -> Icons.Filled.CloudDownload
    SyncState.CONFLICT -> Icons.Filled.SyncProblem
    SyncState.ERROR -> Icons.Filled.ErrorOutline
    SyncState.NEEDS_SIGN_IN -> Icons.Filled.Login
    SyncState.OFFLINE -> Icons.Filled.CloudOff
    SyncState.PENDING -> Icons.Filled.Schedule
}

internal fun syncLabel(state: SyncState): String = when (state) {
    SyncState.SYNCED -> "Synced"
    SyncState.UPLOADING -> "Uploading"
    SyncState.DOWNLOADING -> "Downloading"
    SyncState.CONFLICT -> "Conflict"
    SyncState.ERROR -> "Sync error"
    SyncState.NEEDS_SIGN_IN -> "Sign in needed"
    SyncState.OFFLINE -> "Offline"
    SyncState.PENDING -> "Not synced"
}

@Composable
internal fun syncColor(state: SyncState): Color = when (state) {
    SyncState.SYNCED -> Color(0xFF6FD08C)
    SyncState.CONFLICT -> Color(0xFFFF8A3D)
    SyncState.ERROR, SyncState.NEEDS_SIGN_IN -> Color(0xFFFF5A5A)
    SyncState.UPLOADING, SyncState.DOWNLOADING -> ShelfTheme.accent
    else -> Color(0xFFB8BCC8)
}

/** The sync state as a small round badge with its icon (on every save slot). */
@Composable
internal fun SyncBadge(state: SyncState, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val c = syncColor(state)
    Box(
        modifier.size(size).clip(CircleShape).background(Color(0xE6101218)).border(1.5.dp, c, CircleShape),
        contentAlignment = Alignment.Center
    ) { Icon(syncIcon(state), contentDescription = syncLabel(state), tint = c, modifier = Modifier.size(size * 0.66f)) }
}

/** Legend for the badges (Save Vault's filter panel). */
@Composable
internal fun SyncLegend() {
    ChipFlow {
        listOf(SyncState.SYNCED, SyncState.PENDING, SyncState.UPLOADING, SyncState.CONFLICT, SyncState.ERROR).forEach { st ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp, bottom = 4.dp)) {
                SyncBadge(st, 16.dp)
                Spacer(Modifier.width(5.dp))
                Text(syncLabel(st), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
        }
    }
}

// ---- The physical styles: SD card, memory card, floppy ----

/** An SD card: the top-right corner cut off. */
private val SdShape: Shape = GenericShape { size, _ ->
    val c = size.width * 0.22f
    val r = size.width * 0.06f
    moveTo(r, 0f); lineTo(size.width - c, 0f); lineTo(size.width, c); lineTo(size.width, size.height - r)
    quadraticTo(size.width, size.height, size.width - r, size.height); lineTo(r, size.height)
    quadraticTo(0f, size.height, 0f, size.height - r); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); close()
}

/**
 * The memory card's outline: a grey card with slightly rounded top corners, gently rounded bottom corners and a
 * ridged grip down both sides (an original drawing; no logos).
 */
private val MemCardShape: Shape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    val rt = w * 0.035f; val rb = w * 0.07f
    val ridgeTop = h * 0.42f; val ridgeBottom = h * 0.9f
    val n = 6; val d = w * 0.014f
    moveTo(rt, 0f); lineTo(w - rt, 0f); quadraticTo(w, 0f, w, rt)
    lineTo(w, ridgeTop)
    val step = (ridgeBottom - ridgeTop) / n
    for (i in 0 until n) { val y0 = ridgeTop + step * i; quadraticTo(w - d * 2f, y0 + step / 2f, w, y0 + step) }
    lineTo(w, h - rb); quadraticTo(w, h, w - rb, h); lineTo(rb, h); quadraticTo(0f, h, 0f, h - rb)
    lineTo(0f, ridgeBottom)
    for (i in 0 until n) { val y0 = ridgeBottom - step * i; quadraticTo(d * 2f, y0 - step / 2f, 0f, y0 - step) }
    lineTo(0f, rt); quadraticTo(0f, 0f, rt, 0f); close()
}

/**
 * Update 141: a 3D card turning as a whole, left and right as far as its pictures go (±60°): the picture for the nearest
 * angle, turned the last few degrees to where the clock [angle] (degrees, read while drawing) is, so it moves smoothly.
 */
@Composable
internal fun SwayingModel(card: String, icon: Any?, name: String, platform: String?, angle: () -> Float, modifier: Modifier = Modifier) {
    val latest by androidx.compose.runtime.rememberUpdatedState(angle)
    fun yaw(): Float = kotlin.math.sin(Math.toRadians(latest().toDouble())).toFloat() * 60f
    val frame by remember { androidx.compose.runtime.derivedStateOf { Models3D.nearest(yaw()) } }
    Box(
        modifier.graphicsLayer {
            cameraDistance = 14f * density
            rotationY = (yaw() - frame).coerceIn(-10f, 10f)
            flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
        }
    ) {
        Model3DArt(card, icon, frame.toFloat(), Modifier.fillMaxSize(), name = name, alwaysName = true, platform = platform)
    }
}

/** Width / height of each physical style. */
internal fun SaveVaultStyle.aspect(): Float = when (this) {
    SaveVaultStyle.SD_CARD -> 0.78f
    SaveVaultStyle.MEMORY_CARD -> 0.86f
    SaveVaultStyle.FLOPPY -> 0.95f
    SaveVaultStyle.CAPSULE -> 0.62f
    SaveVaultStyle.CLASSIC -> 1f
    SaveVaultStyle.CARD_3D -> Models3D.saveCardId()?.let { Models3D.aspect(it) } ?: 0.86f
}

/**
 * A save drawn as an SD card, a PlayStation-style memory card or a floppy disk: the game's art sits on
 * the label in its own fixed-aspect window (cropped, never squashed), the sync state as an icon badge.
 */
@Composable
internal fun StyledSaveCard(
    style: SaveVaultStyle,
    icon: String?,
    name: String,
    state: SyncState,
    selected: Boolean,
    lift: Float,
    /** How far it bobs right now; read only while drawing. */
    bob: () -> Float,
    hidden: Boolean,
    onIconPlaced: (androidx.compose.ui.geometry.Rect) -> Unit,
    /** Update 137: the save's console (its logo on the 3D card's badge). */
    platform: String? = null,
    /** Update 141: the turning clock (degrees, read while drawing); with "3D turn" the picked 3D card sways on it. */
    spin: (() -> Float)? = null
) {
    // Update 136: your 3D shape library's card, the cover on its label and the name under it (the drawn memory card
    // while the "3D models" pack isn't on the device).
    if (style == SaveVaultStyle.CARD_3D) {
        val card = Models3D.saveCardId()
        if (card != null) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(style.aspect())
                    .onGloballyPositioned { onIconPlaced(it.boundsInRoot()) }
                    .graphicsLayer {
                        val b = bob()
                        translationY = -(3.dp.toPx() * b) - 10.dp.toPx() * lift
                        val s = 1f + 0.10f * lift
                        scaleX = s; scaleY = s
                        alpha = if (hidden) 0.25f else 1f
                    }
            ) {
                // (Update 141: the picked one turns as a whole, with "3D turn" on — not just its picture.)
                if (selected && spin != null && Features.saveTurn.state.value) SwayingModel(card, icon, name, platform, spin, Modifier.fillMaxSize())
                else Model3DArt(card, icon, if (selected) 0f else 20f, Modifier.fillMaxSize(), name = name, alwaysName = true, platform = platform)
                if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.accent))
                SyncBadge(state, 18.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
            return
        }
        StyledSaveCard(SaveVaultStyle.MEMORY_CARD, icon, name, state, selected, lift, bob, hidden, onIconPlaced)
        return
    }
    val accent = ShelfTheme.accent
    val shape: Shape = when (style) {
        SaveVaultStyle.SD_CARD -> SdShape
        SaveVaultStyle.MEMORY_CARD -> MemCardShape
        else -> RoundedCornerShape(5.dp)
    }
    val body = when (style) {
        SaveVaultStyle.SD_CARD -> listOf(Color(0xFF2B2F3A), Color(0xFF16181F))
        // Light grey plastic, lit from above.
        SaveVaultStyle.MEMORY_CARD -> listOf(Color(0xFFC7C9CC), Color(0xFFA9ABAF))
        else -> listOf(Color(0xFF22252E), Color(0xFF111318))
    }
    Box(
        Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(style.aspect())
            .graphicsLayer {
                val b = bob()
                translationY = -(3.dp.toPx() * b) - 10.dp.toPx() * lift
                val s = 1f + 0.10f * lift
                scaleX = s; scaleY = s
                rotationZ = if (style == SaveVaultStyle.FLOPPY) b * 1.5f else 0f
                alpha = if (hidden) 0.25f else 1f
            }
            .clip(shape)
            .background(Brush.verticalGradient(body))
            .border(1.dp + 1.5.dp * lift, if (selected) accent else Color.White.copy(alpha = 0.18f), shape)
    ) {
        when (style) {
            SaveVaultStyle.FLOPPY -> {
                // The metal shutter on top with its window, a write-protect notch.
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth(0.56f).fillMaxHeight(0.3f)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF9EA4AE), Color(0xFFD9DDE3), Color(0xFF8E949E))))
                ) {
                    Box(Modifier.align(Alignment.Center).padding(start = 18.dp).fillMaxWidth(0.22f).fillMaxHeight(0.7f).background(Color(0xFF15171C)))
                }
                Box(Modifier.align(Alignment.TopStart).padding(5.dp).size(6.dp, 8.dp).background(Color.Black.copy(alpha = 0.6f)))
            }
            SaveVaultStyle.MEMORY_CARD -> {
                // Like a real grey memory card: two little screw holes, the raised triangle at the top, "MEMORY CARD"
                // pressed into the plastic, the recessed label below (the game's art, drawn further down), the
                // ridges on the sides and a soft sheen.
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.08f))))
                    for (x in floatArrayOf(w * 0.08f, w * 0.92f)) {
                        drawCircle(Color(0xFF8F9195), w * 0.028f, Offset(x, h * 0.05f))
                        drawCircle(Color(0xFF6E7074), w * 0.016f, Offset(x, h * 0.05f))
                    }
                    val tri = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.5f, h * 0.012f); lineTo(w * 0.63f, h * 0.075f); lineTo(w * 0.37f, h * 0.075f); close()
                    }
                    drawPath(tri, Brush.verticalGradient(listOf(Color(0xFFDADCDF), Color(0xFFB4B6BA)), 0f, h * 0.075f))
                    drawPath(tri, Color(0xFF8C8E92), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.006f))
                    // The label's recess: a darker edge below, a lighter one above.
                    val lt = h * 0.47f; val lb = h * 0.82f; val ll = w * 0.11f; val lr = w * 0.89f
                    drawRoundRect(Color(0xFF8C8E92), Offset(ll - 2f, lt - 2f), Size(lr - ll + 4f, lb - lt + 4f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
                    drawRoundRect(Color(0xFFB6B8BC), Offset(ll, lt), Size(lr - ll, lb - lt), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
                    // A pressed-in band near the bottom.
                    drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(w * 0.3f, h * 0.87f), Size(w * 0.4f, h * 0.035f), androidx.compose.ui.geometry.CornerRadius(h * 0.02f))
                }
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.fillMaxHeight(0.3f))
                    FitText("MEMORY CARD", Color(0xFF5F6165), maxSize = 9.sp, minSize = 5.sp, maxLines = 1, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.7f))
                }
            }
            SaveVaultStyle.SD_CARD -> {
                // Gold contacts along the top edge.
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp).fillMaxWidth(0.6f).height(9.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) { repeat(6) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(Color(0xFFD7B355).copy(alpha = 0.8f))) } }
            }
            else -> Unit
        }
        // The label with the game's art: a fixed square window (cropped to fit).
        val labelTop = when (style) { SaveVaultStyle.FLOPPY -> 0.36f; SaveVaultStyle.SD_CARD -> 0.2f; SaveVaultStyle.MEMORY_CARD -> 0.485f; else -> 0.12f }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.fillMaxHeight(labelTop))
            Box(
                Modifier
                    .fillMaxWidth(when (style) { SaveVaultStyle.FLOPPY -> 0.74f; SaveVaultStyle.MEMORY_CARD -> 0.74f; else -> 0.78f })
                    // The memory card's label is wide and short, like the real one's.
                    .aspectRatio(when (style) { SaveVaultStyle.FLOPPY -> 1.25f; SaveVaultStyle.MEMORY_CARD -> 2.05f; else -> 1f })
                    .onGloballyPositioned { onIconPlaced(it.boundsInRoot()) }
                    .graphicsLayer { alpha = if (hidden) 0f else 1f }
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (style == SaveVaultStyle.FLOPPY) Color(0xFFEDEBE6) else Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) AsyncImage(model = icon, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(if (style == SaveVaultStyle.FLOPPY) 3.dp else 0.dp))
                else Text(name.take(2).uppercase(), color = if (style == SaveVaultStyle.FLOPPY) Color(0xFF333333) else Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        SyncBadge(state, 18.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

// ---- The open animation: insert / load, or the capsule cracking open ----

/**
 * Plays once when a save is opened (Features.saveVaultAnimations): the memory card / SD card slides
 * into a slot and a loading bar fills, the floppy goes into a drive with a click and its light
 * blinks, the capsule cracks open with a flash. About a second; [onDone] when it's over.
 */
@Composable
internal fun SaveOpenAnimation(style: SaveVaultStyle, icon: String?, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(if (style == SaveVaultStyle.CAPSULE) 650 else 1000, easing = FastOutSlowInEasing))
        onDone()
    }
    val accent = ShelfTheme.accent
    val t = p.value
    Box(modifier, contentAlignment = Alignment.Center) {
        when (style) {
            SaveVaultStyle.CAPSULE -> {
                // The two halves part (top up, bottom down) with a burst of light between them.
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width * 0.5f
                    val h = size.height * 0.62f
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val gap = h * 0.45f * t
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.9f * (1 - t)), Color.Transparent), Offset(cx, cy), size.minDimension * (0.2f + 0.6f * t)), size.minDimension * (0.2f + 0.6f * t), Offset(cx, cy))
                    drawRoundRect(Color.White.copy(alpha = 0.85f * (1 - t * 0.7f)), Offset(cx - w / 2, cy - h / 2 - gap), Size(w, h / 2), androidx.compose.ui.geometry.CornerRadius(w / 2))
                    drawRoundRect(accent.copy(alpha = 0.9f * (1 - t * 0.7f)), Offset(cx - w / 2, cy + gap), Size(w, h / 2), androidx.compose.ui.geometry.CornerRadius(w / 2))
                }
            }
            SaveVaultStyle.CLASSIC -> Unit
            else -> {
                // A slot (console / reader / drive) with the save sliding in, then a loading bar.
                val insert = (t / 0.55f).coerceIn(0f, 1f)
                val load = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(150.dp, 110.dp), contentAlignment = Alignment.TopCenter) {
                        Box(
                            Modifier.padding(top = (8 + 60 * insert).dp).size(if (style == SaveVaultStyle.FLOPPY) 74.dp else 58.dp, 70.dp)
                                .clip(RoundedCornerShape(if (style == SaveVaultStyle.FLOPPY) 4.dp else 8.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF4A4F5C), Color(0xFF22252E))))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (icon != null) AsyncImage(icon, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(3.dp)))
                        }
                        // The slot's front, over the lower half.
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFF2C3038), Color(0xFF15171C))))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        ) {
                            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).fillMaxWidth(0.62f).height(5.dp).clip(RoundedCornerShape(50)).background(Color.Black))
                            val blink = if (load > 0f) (0.4f + 0.6f * (0.5f + 0.5f * sin(load * 30f))) else 0.15f
                            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(7.dp).clip(CircleShape).background(Color(0xFF6FD08C).copy(alpha = blink)))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (style) { SaveVaultStyle.FLOPPY -> "Reading disk…"; SaveVaultStyle.SD_CARD -> "Reading card…"; else -> "Loading memory card…" },
                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(150.dp).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(load).clip(RoundedCornerShape(50)).background(accent))
                    }
                }
            }
        }
    }
}

// ---- Background ----

/** Save Vault's background (Features.saveVaultBackdrop); floating icons drift slowly on [phase]. */
@Composable
internal fun SaveVaultBackground(phase: () -> Float, icons: List<String>, artUrl: String?, modifier: Modifier = Modifier, themed: @Composable () -> Unit) {
    when (Features.saveVaultBackdrop.state.value) {
        Features.SaveVaultBackdrop.THEME -> themed()
        Features.SaveVaultBackdrop.PLAIN -> Box(modifier.background(Color(0xFF0B0C10)))
        Features.SaveVaultBackdrop.GAME_ART -> Box(modifier.background(Color(0xFF07080C))) {
            if (artUrl != null) {
                val blurred = if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.graphicsLayer {
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(40f, 40f)
                } else Modifier
                androidx.compose.animation.Crossfade(artUrl, label = "vaultArt") { u ->
                    AsyncImage(u, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(blurred).graphicsLayer { alpha = 0.45f })
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.75f)))))
        }
        Features.SaveVaultBackdrop.FLOATING -> Box(modifier) {
            themed()
            FloatingSaveField(icons, Modifier.fillMaxSize())
        }
    }
}

/**
 * The floating save icons behind the vault: a steady stream of them rising from below and drifting out at the top,
 * new ones always coming in, three depths (far ones smaller, fainter, slower). Every motion runs a whole number of
 * times per loop of one long clock, so the loop never shows (it used to jump back at the end).
 */
@Composable
private fun FloatingSaveField(icons: List<String>, modifier: Modifier) {
    if (icons.isEmpty()) return
    val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "saveField")
    val tState = clock.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(48_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "saveFieldT"
    )
    val count = 18
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val w = maxWidth; val h = maxHeight
        for (i in 0 until count) {
            // Depth: 0 far, 1 middle, 2 near. Rises 1, 2 or 3 times per loop (whole numbers: seamless).
            val depth = i % 3
            val laps = depth + 1
            val size = (34 + depth * 14).dp
            val lane = ((i * 0.61803f) % 1f)
            val start = ((i * 0.37f) % 1f)
            val url = icons[(i * 7) % icons.size]
            AsyncImage(
                url, null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .graphicsLayer {
                        val t = tState.value
                        val p = ((start + t * laps) % 1f)
                        val two = (2 * Math.PI).toFloat()
                        translationX = (w.toPx() - size.toPx()) * lane + sin(two * (t * laps * 2 + lane)) * 18.dp.toPx()
                        translationY = (h.toPx() + size.toPx() * 2) * (1f - p) - size.toPx() * 1.5f
                        rotationZ = sin(two * (t * laps + start)) * 10f
                        // Fades in at the bottom and out at the top, so they come and go softly.
                        val edge = (minOf(p, 1f - p) * 6f).coerceIn(0f, 1f)
                        alpha = (0.07f + depth * 0.035f) * edge
                    }
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

// ---- Settings (Save Vault's own panel and Settings › Appearance › Save Vault) ----

@Composable
internal fun SaveVaultSettings(onDark: Boolean = false) {
    val label = if (onDark) Color.White.copy(alpha = 0.6f) else ShelfTheme.textMuted
    Column {
        Text("Show", color = label, fontSize = 12.sp)
        ChipFlow {
            Features.SaveVaultFilter.values().forEach { f ->
                Chip(f.label, accent = Features.saveVaultFilter.state.value == f) { Features.saveVaultFilter.value = f }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Style", color = label, fontSize = 12.sp)
        ChipFlow {
            SaveVaultStyle.values().forEach { s ->
                Chip(s.label, accent = Features.saveVaultStyle.state.value == s) { Features.saveVaultStyle.value = s }
            }
        }
        if (Features.saveVaultStyle.state.value == SaveVaultStyle.CARD_3D) {
            // Update 136: which card from your 3D shape library ("3D models" pack, Settings › Game look › Extra art packs).
            ChipFlow {
                ModelSpare.values().forEach { c ->
                    Chip(c.label, accent = Features.saveVaultCard.state.value == c) { Features.saveVaultCard.value = c }
                }
                Chip(if (Features.saveVaultClear.state.value) "✓ Clear" else "Clear", accent = Features.saveVaultClear.state.value) {
                    Features.saveVaultClear.value = !Features.saveVaultClear.value
                }
            }
            if (Models3D.saveCardId() == null) Text("The 3D cards come with the 3D models pack (Settings › Game look › Extra art packs); until then it's the drawn memory card.", color = label, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Motion", color = label, fontSize = 12.sp)
        ChipFlow {
            Chip(if (Features.saveTurn.state.value) "✓ 3D turn" else "3D turn", accent = Features.saveTurn.state.value) {
                Features.saveTurn.value = !Features.saveTurn.value
            }
            // The grid repeats, so scrolling never ends (12 saves or more; fewer would repeat side by side).
            Chip(if (Features.saveVaultEndless.state.value) "✓ Endless scrolling" else "Endless scrolling", accent = Features.saveVaultEndless.state.value) {
                Features.saveVaultEndless.toggle()
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Background", color = label, fontSize = 12.sp)
        ChipFlow {
            Features.SaveVaultBackdrop.values().forEach { b ->
                Chip(b.label, accent = Features.saveVaultBackdrop.state.value == b) { Features.saveVaultBackdrop.value = b }
            }
        }
    }
}
