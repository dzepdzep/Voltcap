package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.shelf.launcher.data.ArtOverride
import com.shelf.launcher.data.ArtSlot
import com.shelf.launcher.data.GameArt
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * One picture in the details window's "Artwork" strip. [model] is an address
 * (String) or one of your own screenshot files; [slot] is the card-art slot
 * "Use as card art" would fill (null = can't be used as card art).
 */
data class GalleryItem(val label: String, val model: Any, val slot: ArtSlot?)

/**
 * Every picture we have for a game, in a fixed order: cover, capsule, hero,
 * logo, then the physical media (box, back, 3D box, disc…), then screenshots
 * and your own. Only what exists; the same address is never listed twice.
 * [showMedia] / [showPhysical] are the Media / Box & disc toggles of the
 * details window.
 */
internal fun buildGalleryItems(
    art: GameArt?,
    override: ArtOverride?,
    physical: List<Pair<String, String>>,
    screenshots: List<String>,
    ownShots: List<java.io.File>,
    showMedia: Boolean,
    showPhysical: Boolean
): List<GalleryItem> {
    val out = ArrayList<GalleryItem>()
    val seen = HashSet<Any>()
    fun add(label: String, model: Any?, slot: ArtSlot?) {
        if (model == null || (model is String && model.isBlank())) return
        if (seen.add(model)) out += GalleryItem(label, model, slot)
    }
    add("Cover", override?.cover ?: art?.gridUrl, ArtSlot.COVER)
    add("Capsule", override?.wide ?: art?.wideGridUrl, ArtSlot.WIDE)
    add("Hero", override?.hero ?: art?.heroUrl, ArtSlot.HERO)
    add("Logo", override?.logo ?: art?.logoUrl, null)
    if (showPhysical) {
        physical.forEach { (label, url) ->
            add(label, url, if (label == "Title screen") ArtSlot.HERO else ArtSlot.COVER)
        }
    }
    if (showMedia) {
        screenshots.forEachIndexed { i, url -> add("Screenshot ${i + 1}", url, ArtSlot.HERO) }
        ownShots.forEach { add("Your screenshot", it, null) }
    }
    return out
}

private fun GalleryItem.tileWidth() = when {
    label == "Logo" -> 120.dp
    label == "Disc / cartridge" || label == "Box (3D)" -> 84.dp
    slot == ArtSlot.COVER -> 68.dp
    else -> 156.dp
}

private val GalleryItem.fits: Boolean
    get() = label == "Logo" || label == "Disc / cartridge" || label == "Box (3D)"

/** The compact strip: small pictures in a row. [focused] rings [selected] (D-pad on the Artwork stop). */
@Composable
internal fun ArtworkStrip(
    items: List<GalleryItem>,
    selected: Int,
    focused: Boolean,
    videoUrl: String?,
    onOpen: (Int) -> Unit,
    onFailed: (GalleryItem) -> Unit
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current.density
    val height = 92.dp
    // Keep the ringed picture on screen when ◀ ▶ walk along the strip.
    LaunchedEffect(selected, focused, items.size) {
        if (!focused) return@LaunchedEffect
        var before = if (videoUrl != null) 164f else 0f
        for (i in 0 until selected.coerceIn(0, items.size)) before += items[i].tileWidth().value + 8f
        scroll.animateScrollTo(((before - 60f).coerceAtLeast(0f) * density).toInt())
    }
    Column {
        Row(
            Modifier.horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (videoUrl != null) {
                // The muted looping clip; not part of the full-screen viewer.
                Box(
                    Modifier.width(156.dp).height(height).clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    androidx.compose.runtime.key(videoUrl) {
                        VideoBackdrop(videoUrl, withSound = false, modifier = Modifier.fillMaxSize())
                    }
                    Text(
                        "▶ CLIP", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            items.forEachIndexed { i, item ->
                val shape = RoundedCornerShape(8.dp)
                val ring = focused && i == selected
                val latestOpen = rememberUpdatedState { onOpen(i) }
                AsyncImage(
                    model = item.model,
                    contentDescription = item.label,
                    contentScale = if (item.fits) ContentScale.Fit else ContentScale.Crop,
                    onError = { onFailed(item) },
                    modifier = Modifier
                        .width(item.tileWidth())
                        .height(height)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(if (ring) 2.dp else 0.dp, if (ring) ShelfTheme.accent else Color.Transparent, shape)
                        .pointerInput(Unit) { detectTapGestures { latestOpen.value() } }
                )
            }
        }
        Text(
            if (focused) items.getOrNull(selected)?.let { "${it.label}  ·  ${selected + 1} / ${items.size}  ·  A to view" }.orEmpty()
            else "",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).height(16.dp)
        )
    }
}

/**
 * The gallery full screen: ◀ ▶ (or the screen's sides) flip, B closes, A uses
 * the picture as this game's card art. [override] is read live so the button
 * turns into a tick once it has been applied.
 */
@Composable
internal fun ArtworkViewer(
    items: List<GalleryItem>,
    start: Int,
    override: ArtOverride?,
    onUseAsCardArt: (ArtSlot, String) -> Unit,
    onDismiss: () -> Unit
) {
    var index by remember(items) { mutableStateOf(start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    val focus = remember { FocusRequester() }
    val item = items.getOrNull(index)
    val url = item?.model as? String
    val slot = item?.slot
    val current = when (slot) {
        ArtSlot.COVER -> override?.cover
        ArtSlot.WIDE -> override?.wide
        ArtSlot.HERO -> override?.hero
        else -> null
    }
    val canUse = slot != null && url != null && current != url
    val isSet = slot != null && url != null && current == url
    val use = rememberUpdatedState { if (canUse && slot != null && url != null) onUseAsCardArt(slot, url) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft, Key.ButtonL1 -> { if (index > 0) index--; true }
                        Key.DirectionRight, Key.ButtonR1 -> { if (index < items.lastIndex) index++; true }
                        Key.ButtonB, Key.Back -> { onDismiss(); true }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            if (canUse) use.value() else onDismiss()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            if (item != null) {
                AsyncImage(
                    model = item.model, contentDescription = item.label,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
                // Taps: left third back, right third forward, middle closes.
                Box(
                    Modifier.fillMaxSize().pointerInput(items) {
                        detectTapGestures { p ->
                            val third = size.width / 3f
                            when {
                                p.x < third -> if (index > 0) index--
                                p.x > third * 2 -> if (index < items.lastIndex) index++
                                else -> onDismiss()
                            }
                            Unit
                        }
                    }
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (canUse || isSet) {
                        val latestUse = use
                        Text(
                            if (canUse) "A   Use as card art for this game" else "✓  This is the card art for this game",
                            color = if (canUse) com.shelf.launcher.ui.onAccentColor() else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (canUse) ShelfTheme.accent else Color.White.copy(alpha = 0.18f))
                                .pointerInput(Unit) { detectTapGestures { latestUse.value() } }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "${item.label}  ·  ${index + 1} / ${items.size}   ◀ ▶ flip · B close",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
