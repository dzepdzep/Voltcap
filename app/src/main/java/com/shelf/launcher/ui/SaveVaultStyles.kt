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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
internal fun SwayingModel(
    card: String, icon: Any?, name: String, platform: String?, angle: () -> Float, modifier: Modifier = Modifier,
    /** (Update 236, #42: the save's picked shell / badge colours; null: the card's own.) */
    shell: Int? = null, badge: Int? = null
) {
    // Update 207: all the way round and smoothly. It swayed ±60° by swapping the pack's pictures every 20° (a slide show)
    // and never showed its back. Now its front picture (0°) is the face of a solid piece (SolidPiece.kt) turned by the
    // clock itself — any angle, its sides in the card's plastic — and the pack's own back picture (180°) is its back.
    val latest by androidx.compose.runtime.rememberUpdatedState(angle)
    val version = com.shelf.launcher.data.Packs.version.intValue
    val meta = remember(card, version) { Models3D.meta(card) }
    val tint = Models3D.tintFor(card, platform, name, shell, badge)
    // (Update 241, #44/#46: its back carries the SaveVault mark, moulded in the back's own plastic — saveCardBack.)
    val engrave = Features.saveEngraving.state.value
    val backBmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, card, version, tint?.key, engrave, platform) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { saveCardBack(card, tint, engrave, vaultConsole(platform)) }
    }
    // (Update 236, #42: its sides in the picked shell colour too.)
    val tone = remember(meta, shell) { (shell ?: meta?.tint?.own?.first)?.let { Color(it) } ?: Color(0xFF9EA2A8) }
    val spec = remember(meta, backBmp, tone) {
        val b = backBmp?.asImageBitmap()
        // (Update 209: a translucent card stays see-through — SolidSpec.clear.)
        SolidSpec(meta?.aspect, saveCardDepth(card), tone, back = b?.let { img -> { img } }, clear = if (meta?.tint?.clear == true) 0.55f else null)
    }
    ProvidePieceTurn(PieceTurn({ ((latest() % 360f) + 360f) % 360f }, 14f)) {
        Box(modifier.solidPiece(spec, withCartSpin = false)) {
            Model3DArt(card, icon, 0f, Modifier.fillMaxSize(), name = name, alwaysName = true, platform = platform, shellOverride = shell, badgeOverride = badge)
        }
    }
}

/** Update 207: a Save Vault 3D card's thickness against its longest side (a memory card is about 6 mm on 55). */
internal const val SAVE_CARD_DEPTH = 0.1f

/**
 * (Update 255, #89 finding 12, approved: the card's own thickness — an SD card is 2.1 mm on its 32 mm, 0.066, as the
 * inspector's drawn SD card already was; every 3D card was a memory card's 0.1, so the SD card turned half again too thick.)
 */
internal fun saveCardDepth(card: String): Float = if (card.startsWith("sd_card")) 0.066f else SAVE_CARD_DEPTH

/** (Update 241, #44: the 3D cards' backs made so far — key: card, colours, engraved.) */
private val cardBacks = object : android.util.LruCache<String, android.graphics.Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
}

/**
 * The Save Vault 3D card [card]'s back in [tint]'s colours, mirrored (seen from behind once turned, it reads the right way
 * round — as the drawn shells' backs): the pack's own back picture, else its front without a label. Off the main thread.
 * (Update 241, #44/#46: with [engrave], "SaveVault" and the cloud moulded into its plastic first — in the colour that's
 * already there, so it keeps the picture's own light and any colour picked — on the larger blank band of the back above
 * or below its label; left off where no band is big enough to read it, rather than over a label or the badge. Cached.)
 */
internal fun saveCardBack(card: String, tint: Models3D.Tint?, engrave: Boolean, platform: String? = null): android.graphics.Bitmap? = runCatching {
    // (0.6.18: and the console the save comes from, its logo under the mark.)
    val key = "$card|${tint?.key.orEmpty()}|$engrave|${platform.orEmpty()}|${com.shelf.launcher.data.Packs.version.intValue}"
    cardBacks.get(key)?.let { return@runCatching it }
    // (Update 211: the card's front without its label — the plain card — when the pack has no back picture: the turned
    // card was a flat block of colour from behind, half of every turn.)
    val own = Models3D.compose(card, 180, null, null, ArtPos.NONE, tint)
    val src = own ?: Models3D.compose(card, 0, null, null, ArtPos.NONE, tint) ?: return@runCatching null
    // (The pack's pictures are cached: marked on a copy.)
    val work = if (engrave) src.copy(android.graphics.Bitmap.Config.ARGB_8888, true) else src
    if (engrave && work != null) {
        val meta = Models3D.meta(card)
        engraveCardBack(work, if (own != null) meta?.backs?.get(180) else meta?.quads?.get(0), platform)
    }
    val b = work ?: src
    val m = android.graphics.Matrix().apply { setScale(-1f, 1f, b.width / 2f, 0f) }
    val out = android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    if (work != null && work !== src && work !== out) work.recycle()
    cardBacks.put(key, out)
    out
}.getOrNull()

/**
 * (Update 241, #44: the SaveVault mark on a 3D card's back picture [b] (seen from behind, unmirrored): inside the card's
 * own outline (its visible pixels), in the bigger band above or below [label] (the back label's / window's four corners,
 * shares of the picture; null: none) — stacked where it's tall enough, else in one line, else not at all.)
 */
private fun engraveCardBack(b: android.graphics.Bitmap, label: FloatArray?, platform: String? = null) {
    val w = b.width; val h = b.height
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
    var y = 0
    while (y < h) {
        var x = 0
        val row = y * w
        while (x < w) {
            if ((px[row + x] ushr 24) > 160) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
            x += 2
        }
        y += 2
    }
    if (x1 <= x0 || y1 <= y0) return
    val bw = (x1 - x0).toFloat(); val bh = (y1 - y0).toFloat()
    // The band free of the label: above it or below it, whichever is taller (the whole card without a label).
    var top = y0 + bh * 0.08f; var bottom = y1 - bh * 0.08f
    var labelRect: android.graphics.RectF? = null
    if (label != null && label.size >= 8) {
        val ly0 = minOf(label[1], label[3], label[5], label[7]) * h
        val ly1 = maxOf(label[1], label[3], label[5], label[7]) * h
        val lx0 = minOf(label[0], label[2], label[4], label[6]) * w
        val lx1 = maxOf(label[0], label[2], label[4], label[6]) * w
        labelRect = android.graphics.RectF(lx0, ly0, lx1, ly1)
        val above = ly0 - top; val below = bottom - ly1
        if (above >= below) bottom = ly0 - bh * 0.03f else top = ly1 + bh * 0.03f
    }
    val ctx = Engraving.app
    val withLogo = platform != null && ctx != null
    val atop = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
    }
    fun logoAt(cx: Float, cy: Float, lw: Int, lh: Int) {
        if (!withLogo || lw < 24 || lh < 12) return
        val tone = Engraving.sampleTone(b, android.graphics.RectF(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f)) ?: return
        // (Embossed: raised from the plastic, as Toni asked.)
        Engraving.platformLogo(ctx!!, platform!!, tone, lw, lh, raised = true, strong = true)?.let { logo ->
            android.graphics.Canvas(b).drawBitmap(logo, cx - logo.width / 2f, cy - logo.height / 2f, atop)
        }
    }
    // 0.6.18: Toni's SaveVault picture goes big on the back's label plate (a grey plate, like the picture's own), and the
    // console the save comes from has its logo in the free band above / below it. A back without a label: both in its band.
    val lr = labelRect
    if (lr != null && lr.width() > bw * 0.3f && lr.height() > bh * 0.12f && label != null) {
        // 0.6.18 (Toni): the grey label plate (a real card's sticker spot, no function here) is filled with the card's own
        // plastic — its shade from just above to just below it — and your SaveVault mark goes big, straight into the plastic.
        val gap = bh * 0.025f
        val above = Engraving.sampleTone(b, android.graphics.RectF(lr.left, lr.top - gap * 2f, lr.right, lr.top - gap))
        val below = Engraving.sampleTone(b, android.graphics.RectF(lr.left, lr.bottom + gap, lr.right, lr.bottom + gap * 2f))
        val fillA = above ?: below
        if (fillA != null) {
            val grow = 1f + (6f / lr.width().coerceAtLeast(1f)) * 2f
            val path = android.graphics.Path()
            for (i in 0 until 4) {
                val px = lr.centerX() + (label[i * 2] * w - lr.centerX()) * grow
                val py = lr.centerY() + (label[i * 2 + 1] * h - lr.centerY()) * grow
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(0f, lr.top, 0f, lr.bottom, fillA, below ?: fillA, android.graphics.Shader.TileMode.CLAMP)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
            }
            android.graphics.Canvas(b).drawPath(path, paint)
        }
        val stackedW = minOf(bw * 0.8f, lr.height() * 1.4f / Engraving.ratio(Engraving.Layout.STACKED))
        Engraving.engraveOnto(b, x0 + bw / 2f, lr.centerY(), stackedW.toInt(), Engraving.Layout.STACKED)
        val band = bottom - top
        logoAt(x0 + bw / 2f, top + band / 2f, (bw * 0.66f).toInt(), (band * 0.78f).toInt())
        return
    }
    val logoH = if (withLogo) (bottom - top) * 0.22f else 0f
    val band = bottom - top - logoH
    val markW = bw * 0.62f
    val layout = when {
        markW * Engraving.ratio(Engraving.Layout.STACKED) <= band -> Engraving.Layout.STACKED
        bw * 0.7f * Engraving.ratio(Engraving.Layout.HORIZONTAL) <= band -> Engraving.Layout.HORIZONTAL
        else -> return
    }
    val mw = if (layout == Engraving.Layout.STACKED) minOf(markW, band / Engraving.ratio(layout) * 0.92f) else bw * 0.7f
    val mh = mw * Engraving.ratio(layout)
    val markCy = top + band / 2f
    Engraving.engraveOnto(b, x0 + bw / 2f, markCy, mw.toInt(), layout)
    if (logoH > 0f) logoAt(x0 + bw / 2f, markCy + mh / 2f + logoH * 0.55f, (bw * 0.42f).toInt(), (logoH * 0.78f).toInt())
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
    spin: (() -> Float)? = null,
    /** (Update 236, #42: this save's colours — SaveLookStore; null: the original look, exactly.) */
    look: SaveLook? = null
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
                if (selected && spin != null && Features.saveTurn.state.value) SwayingModel(card, icon, name, platform, spin, Modifier.fillMaxSize(), shell = look?.shell, badge = look?.trim)
                else Model3DArt(card, icon, if (selected) 0f else 20f, Modifier.fillMaxSize(), name = name, alwaysName = true, platform = platform, shellOverride = look?.shell, badgeOverride = look?.trim)
                if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(50)).background(ShelfTheme.accent))
                SyncBadge(state, 18.dp, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            }
            return
        }
        StyledSaveCard(SaveVaultStyle.MEMORY_CARD, icon, name, state, selected, lift, bob, hidden, onIconPlaced, look = look)
        return
    }
    val accent = ShelfTheme.accent
    val shape: Shape = when (style) {
        SaveVaultStyle.SD_CARD -> SdShape
        SaveVaultStyle.MEMORY_CARD -> MemCardShape
        else -> RoundedCornerShape(5.dp)
    }
    // (Update 236, #42: the body and its details in the save's colours — SaveVaultLooks.kt; with none, the originals below.)
    val mem = memCardTones(look)
    val body = when (style) {
        SaveVaultStyle.SD_CARD -> sdBody(look) // (originally 2B2F3A → 16181F)
        // Light grey plastic, lit from above (originally C7C9CC → A9ABAF).
        SaveVaultStyle.MEMORY_CARD -> mem.body
        else -> floppyBody(look) // (originally 22252E → 111318)
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
            // (Update 241, #44/#46: "SaveVault" and the cloud pressed into the front's blank plastic — the memory card's
            // between its triangle and "MEMORY CARD", the SD card's under its label — in the body's own colour there;
            // only once the object is drawn big enough to read it (Appearance, Inspect), never as noise in the grid.)
            .then(frontMark(style, body)?.let { (tone, where) -> Modifier.saveVaultMark(tone, Engraving.Layout.HORIZONTAL, where = where) } ?: Modifier)
    ) {
        when (style) {
            SaveVaultStyle.FLOPPY -> {
                // The metal shutter on top with its window, a write-protect notch.
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth(0.56f).fillMaxHeight(0.3f)
                        .background(Brush.horizontalGradient(floppyShutter(look)))
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
                        drawCircle(mem.screwOuter, w * 0.028f, Offset(x, h * 0.05f))
                        drawCircle(mem.screwInner, w * 0.016f, Offset(x, h * 0.05f))
                    }
                    val tri = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.5f, h * 0.012f); lineTo(w * 0.63f, h * 0.075f); lineTo(w * 0.37f, h * 0.075f); close()
                    }
                    drawPath(tri, Brush.verticalGradient(mem.tri, 0f, h * 0.075f))
                    drawPath(tri, mem.triEdge, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.006f))
                    // The label's recess: a darker edge below, a lighter one above.
                    val lt = h * 0.47f; val lb = h * 0.82f; val ll = w * 0.11f; val lr = w * 0.89f
                    drawRoundRect(mem.recessEdge, Offset(ll - 2f, lt - 2f), Size(lr - ll + 4f, lb - lt + 4f), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
                    drawRoundRect(mem.recess, Offset(ll, lt), Size(lr - ll, lb - lt), androidx.compose.ui.geometry.CornerRadius(w * 0.02f))
                    // A pressed-in band near the bottom.
                    drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(w * 0.3f, h * 0.87f), Size(w * 0.4f, h * 0.035f), androidx.compose.ui.geometry.CornerRadius(h * 0.02f))
                }
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.fillMaxHeight(0.3f))
                    FitText("MEMORY CARD", mem.text, maxSize = 9.sp, minSize = 5.sp, maxLines = 1, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.7f))
                }
            }
            SaveVaultStyle.SD_CARD -> {
                // Gold contacts along the top edge.
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 4.dp).fillMaxWidth(0.6f).height(9.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) { repeat(6) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(sdContacts(look).copy(alpha = 0.8f))) } }
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

/**
 * (Update 241, #44: where a drawn object's front has blank plastic for the SaveVault mark — its area in the object's box —
 * and the body's colour there; null for a front without one: the floppy's is shutter and label, so its mark is on its back.)
 */
private fun frontMark(style: SaveVaultStyle, body: List<Color>): Pair<Int, (Size) -> androidx.compose.ui.geometry.Rect?>? = when (style) {
    // Between the raised triangle (to 7.5 %) and "MEMORY CARD" (from 30 %).
    SaveVaultStyle.MEMORY_CARD -> androidx.compose.ui.graphics.lerp(body.first(), body.last(), 0.18f).toArgb() to { s: Size ->
        markArea(s.width * 0.5f, s.height * 0.18f, s.width * 0.5f, s.height * 0.075f)
    }
    // Under the label (it ends at 81 %), clear of the sync badge in the corner.
    SaveVaultStyle.SD_CARD -> androidx.compose.ui.graphics.lerp(body.first(), body.last(), 0.88f).toArgb() to { s: Size ->
        markArea(s.width * 0.36f, s.height * 0.895f, s.width * 0.5f, s.height * 0.08f)
    }
    else -> null
}

/**
 * (Update 241, #44/#49: the back of a drawn Save Vault object — memory card, SD card, floppy, capsule — for the Inspect
 * viewer: the same outline and plastic as its front, in the save's colours (SaveLook), its own back details (the memory
 * card's screws and shallow panel, the SD card's plateau and lock slot, the floppy's shutter, hub and corner holes, the
 * capsule's glass with the save inside it), and "SaveVault" with the cloud moulded into it. Its details are drawn as seen
 * from behind and then mirrored inside the front's outline, as every drawn back is: the viewer turns it round, so from
 * behind it reads the right way round. The capsule's save shows through its glass the other way round, as it would.)
 */
@Composable
internal fun SaveObjectBack(style: SaveVaultStyle, look: SaveLook?, icon: String?, state: SyncState, modifier: Modifier = Modifier, platform: String? = null) {
    val capsule = style == SaveVaultStyle.CAPSULE
    val shape: Shape = when (style) {
        SaveVaultStyle.SD_CARD -> SdShape
        SaveVaultStyle.MEMORY_CARD -> MemCardShape
        SaveVaultStyle.CAPSULE -> RoundedCornerShape(50)
        else -> RoundedCornerShape(5.dp)
    }
    val mem = memCardTones(look)
    val body = when (style) {
        SaveVaultStyle.SD_CARD -> sdBody(look)
        SaveVaultStyle.FLOPPY -> floppyBody(look)
        else -> mem.body
    }
    val charge = syncColor(state)
    val glass = look?.shell?.let { Color(it) }
    val shutter = floppyShutter(look)
    val plateau = androidx.compose.ui.graphics.lerp(body.first(), Color.White, 0.06f)
    // The mark's place (centre, width and height at most), layout and the plastic under it.
    val markTone = when (style) {
        SaveVaultStyle.MEMORY_CARD -> mem.recess
        SaveVaultStyle.SD_CARD -> plateau
        SaveVaultStyle.CAPSULE -> androidx.compose.ui.graphics.lerp(Color(0xFF0B0D16), charge, 0.32f)
        else -> androidx.compose.ui.graphics.lerp(body.first(), body.last(), 0.85f)
    }.toArgb()
    val layout = if (style == SaveVaultStyle.FLOPPY) Engraving.Layout.HORIZONTAL else Engraving.Layout.STACKED
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(if (capsule) 0.82f else 0.86f)
                .aspectRatio(style.aspect())
                .clip(shape)
                .background(
                    if (capsule) Brush.verticalGradient(
                        0f to (glass?.copy(alpha = 0.30f) ?: Color.White.copy(alpha = 0.07f)),
                        0.6f to (glass?.copy(alpha = 0.16f) ?: Color.White.copy(alpha = 0.04f)),
                        0.6f to charge.copy(alpha = 0.22f),
                        1f to charge.copy(alpha = 0.42f)
                    ) else Brush.verticalGradient(body)
                )
                .border(1.dp, Color.White.copy(alpha = if (capsule) 0.16f else 0.18f), shape)
        ) {
            if (capsule && icon != null) {
                // The save inside, seen through the glass from behind (left as it is: the turn shows it mirrored).
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.fillMaxHeight(0.07f))
                    AsyncImage(
                        icon, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)
                            .graphicsLayer { alpha = 0.22f }
                            .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 18, bottomEndPercent = 18))
                    )
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val dark = Color(0xFF15171C)
                    when (style) {
                        SaveVaultStyle.MEMORY_CARD -> {
                            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Transparent, Color.White.copy(alpha = 0.08f))))
                            for (x in floatArrayOf(w * 0.1f, w * 0.9f)) {
                                drawCircle(mem.screwOuter, w * 0.03f, Offset(x, h * 0.93f))
                                drawCircle(mem.screwInner, w * 0.017f, Offset(x, h * 0.93f))
                            }
                            // The shallow panel the mark is pressed into: a darker edge round a slightly darker floor.
                            val l = w * 0.12f; val t = h * 0.14f; val r = w * 0.88f; val b = h * 0.66f
                            drawRoundRect(mem.recessEdge, Offset(l - 2f, t - 2f), Size(r - l + 4f, b - t + 4f), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
                            drawRoundRect(mem.recess, Offset(l, t), Size(r - l, b - t), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
                            // A moulded arrow (which way it goes in) and a ridged grip.
                            val arrow = androidx.compose.ui.graphics.Path().apply {
                                moveTo(w * 0.5f, h * 0.71f); lineTo(w * 0.545f, h * 0.75f); lineTo(w * 0.455f, h * 0.75f); close()
                            }
                            drawPath(arrow, mem.recessEdge)
                            for (i in 0 until 5) {
                                val y = h * (0.79f + i * 0.022f)
                                drawLine(Color.Black.copy(alpha = 0.10f), Offset(w * 0.34f, y), Offset(w * 0.66f, y), 1.dp.toPx())
                            }
                        }
                        SaveVaultStyle.SD_CARD -> {
                            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.05f))))
                            // The raised plateau, the lock switch's slot on its edge, a few ribs to hold it by.
                            val l = w * 0.10f; val t = h * 0.16f; val r = w * 0.90f; val b = h * 0.70f
                            drawRoundRect(Color.Black.copy(alpha = 0.30f), Offset(l + 1.5f, t + 1.5f), Size(r - l, b - t), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
                            drawRoundRect(plateau, Offset(l, t), Size(r - l, b - t), androidx.compose.ui.geometry.CornerRadius(w * 0.03f))
                            drawRect(dark, Offset(w * 0.955f, h * 0.30f), Size(w * 0.045f, h * 0.18f))
                            for (i in 0 until 4) {
                                val y = h * (0.80f + i * 0.035f)
                                drawLine(Color.White.copy(alpha = 0.07f), Offset(w * 0.30f, y), Offset(w * 0.70f, y), 1.dp.toPx())
                            }
                        }
                        SaveVaultStyle.FLOPPY -> {
                            drawRect(Brush.horizontalGradient(shutter), Offset(w * 0.22f, 0f), Size(w * 0.56f, h * 0.3f))
                            drawRect(dark, Offset(w * 0.40f, h * 0.04f), Size(w * 0.12f, h * 0.21f))
                            // The metal hub in its ring, its spindle hole and drive-pin hole.
                            val hc = Offset(w * 0.5f, h * 0.60f); val hr = w * 0.15f
                            drawCircle(Color.Black.copy(alpha = 0.35f), hr * 1.18f, hc)
                            drawCircle(Brush.radialGradient(shutter, hc, hr), hr, hc)
                            drawRect(dark, Offset(hc.x - hr * 0.22f, hc.y - hr * 0.22f), Size(hr * 0.44f, hr * 0.44f))
                            drawRect(dark, Offset(hc.x + hr * 0.45f, hc.y - hr * 0.10f), Size(hr * 0.20f, hr * 0.32f))
                            // The write-protect and density holes in the bottom corners.
                            drawRect(Color.Black.copy(alpha = 0.6f), Offset(w * 0.05f, h * 0.88f), Size(w * 0.06f, h * 0.07f))
                            drawRect(Color.Black.copy(alpha = 0.6f), Offset(w * 0.89f, h * 0.88f), Size(w * 0.06f, h * 0.07f))
                        }
                        SaveVaultStyle.CAPSULE -> {
                            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, h * 0.6f), Offset(w, h * 0.6f), 1.dp.toPx())
                            drawRoundRect(
                                Color.White.copy(alpha = 0.10f), Offset(5.dp.toPx(), h * 0.19f), Size(4.dp.toPx(), h * 0.62f),
                                androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                        }
                        else -> Unit
                    }
                }
                // The mark, on top of the details it sits in.
                // (0.6.18: the mark is Toni's own SaveVault picture — Engraving — and the console the save comes from has its
                // logo pressed in under it.)
                val withLogo = platform != null
                Box(
                    Modifier.fillMaxSize().saveVaultMark(markTone, layout, minWidth = 0.dp) { s ->
                        when (style) {
                            SaveVaultStyle.MEMORY_CARD -> if (withLogo) markArea(s.width * 0.5f, s.height * 0.33f, s.width * 0.66f, s.height * 0.30f)
                                else markArea(s.width * 0.5f, s.height * 0.40f, s.width * 0.66f, s.height * 0.42f)
                            SaveVaultStyle.SD_CARD -> if (withLogo) markArea(s.width * 0.5f, s.height * 0.36f, s.width * 0.68f, s.height * 0.30f)
                                else markArea(s.width * 0.5f, s.height * 0.43f, s.width * 0.68f, s.height * 0.44f)
                            SaveVaultStyle.FLOPPY -> markArea(s.width * 0.5f, s.height * 0.875f, s.width * 0.52f, s.height * 0.09f)
                            SaveVaultStyle.CAPSULE -> markArea(s.width * 0.5f, s.height * 0.84f, s.width * 0.6f, s.height * 0.22f)
                            else -> null
                        }
                    }.consoleMark(platform, markTone) { s ->
                        when (style) {
                            SaveVaultStyle.MEMORY_CARD -> markArea(s.width * 0.5f, s.height * 0.57f, s.width * 0.5f, s.height * 0.10f)
                            SaveVaultStyle.SD_CARD -> markArea(s.width * 0.5f, s.height * 0.61f, s.width * 0.5f, s.height * 0.10f)
                            SaveVaultStyle.FLOPPY -> markArea(s.width * 0.5f, s.height * 0.365f, s.width * 0.42f, s.height * 0.08f)
                            SaveVaultStyle.CAPSULE -> markArea(s.width * 0.5f, s.height * 0.68f, s.width * 0.5f, s.height * 0.08f)
                            else -> null
                        }
                    }
                )
            }
        }
    }
}

// ---- The open animation: insert / load, or the capsule cracking open ----

/**
 * Plays once when a save is opened (Features.saveVaultAnimations): the memory card / SD card slides
 * into a slot and a loading bar fills, the floppy goes into a drive with a click and its light
 * blinks, the capsule cracks open with a flash. About a second; [onDone] when it's over.
 */
@Composable
internal fun SaveOpenAnimation(
    style: SaveVaultStyle, icon: String?, modifier: Modifier = Modifier,
    /** (Update 236, #43: the save's colours — the theme's accent no longer paints the object.) */
    look: SaveLook? = null,
    onDone: () -> Unit
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(if (style == SaveVaultStyle.CAPSULE) 650 else 1000, easing = FastOutSlowInEasing))
        onDone()
    }
    val accent = ShelfTheme.accent
    // (Update 236, #43: the capsule's halves and burst in its own glass colour (the save's look, else clear white) —
    // they were the theme's accent.)
    val glass = capsuleGlass(look)
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
                    drawCircle(Brush.radialGradient(listOf(glass.copy(alpha = 0.9f * (1 - t)), Color.Transparent), Offset(cx, cy), size.minDimension * (0.2f + 0.6f * t)), size.minDimension * (0.2f + 0.6f * t), Offset(cx, cy))
                    drawRoundRect(glass.copy(alpha = 0.85f * (1 - t * 0.7f)), Offset(cx - w / 2, cy - h / 2 - gap), Size(w, h / 2), androidx.compose.ui.geometry.CornerRadius(w / 2))
                    drawRoundRect(glass.copy(alpha = 0.9f * (1 - t * 0.7f)), Offset(cx - w / 2, cy + gap), Size(w, h / 2), androidx.compose.ui.geometry.CornerRadius(w / 2))
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
                                // (Update 236, #42: the card going in, in the save's shell colour.)
                                .background(Brush.verticalGradient(look?.shell?.let { Color(it) }?.let { listOf(androidx.compose.ui.graphics.lerp(it, Color.White, 0.08f), androidx.compose.ui.graphics.lerp(it, Color.Black, 0.3f)) } ?: listOf(Color(0xFF4A4F5C), Color(0xFF22252E))))
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
        // (Update 241, #44: "SaveVault" and Voltcap's cloud moulded into the objects — big views and their backs only.)
        if (Features.saveVaultStyle.state.value != SaveVaultStyle.CLASSIC) ChipFlow {
            Chip(if (Features.saveEngraving.state.value) "✓ SaveVault engraving" else "SaveVault engraving", accent = Features.saveEngraving.state.value) {
                Features.saveEngraving.toggle()
            }
        }
        // (Update 239, #112: PS2 saves as the console's memory-card browser shows them — their own save icons — whatever
        // the style above; other consoles keep it. Off by default.)
        val context = androidx.compose.ui.platform.LocalContext.current
        remember { com.shelf.launcher.data.Ps2BrowserStyle.load(context) }
        val ps2On = com.shelf.launcher.data.Ps2BrowserStyle.on.value
        ChipFlow {
            Chip(if (ps2On) "✓ PS2 browser style" else "PS2 browser style", accent = ps2On) {
                com.shelf.launcher.data.Ps2BrowserStyle.set(context, !ps2On)
            }
        }
        if (ps2On) Text(
            "PS2 saves show their own save icon from the memory card; a save without a readable one shows a generic card marked \"No save icon\".",
            color = label, fontSize = 11.sp
        )
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
