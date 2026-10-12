package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 116 (Edit Home overhaul): each part's own style — its backing (as set / none / dark glass / light / your
 * theme's), how see-through it is, an outline, its shape (pill, square, rounded), its size (fixed steps) and whether it
 * stays still (no rise-in, lift or bobbing). Stored in Features.partStyles as "id=bg,alpha,outline,shape,still;…".
 * Picked in Edit Home with the part's ✎ Style (or L3).
 */

internal enum class PartBg(val label: String) { AUTO("As set"), NONE("None (see-through)"), DARK("Dark glass"), LIGHT("Light"), THEME("Theme") }
internal enum class PartShape(val label: String) { AUTO("As set"), PILL("Pill"), ROUNDED("Rounded"), SQUARE("Square") }

internal data class PartStyle(
    val bg: PartBg = PartBg.AUTO,
    /** Opacity of the whole part, 20–100 %. */
    val alpha: Int = 100,
    val outline: Boolean = false,
    val shape: PartShape = PartShape.AUTO,
    /** No animation: it doesn't rise in, lift or bob when the game changes. */
    val still: Boolean = false
) {
    val plain: Boolean get() = this == PartStyle()

    fun encode(): String = "${bg.name},$alpha,${if (outline) 1 else 0},${shape.name},${if (still) 1 else 0}"

    companion object {
        fun decode(s: String): PartStyle? {
            val p = s.split(',')
            if (p.size < 5) return null
            return PartStyle(
                PartBg.values().firstOrNull { it.name == p[0] } ?: PartBg.AUTO,
                p[1].toIntOrNull()?.coerceIn(20, 100) ?: 100,
                p[2] == "1",
                PartShape.values().firstOrNull { it.name == p[3] } ?: PartShape.AUTO,
                p[4] == "1"
            )
        }
    }
}

internal object PartStyles {
    fun parse(s: String): Map<String, PartStyle> = s.split(';').mapNotNull { e ->
        val i = e.lastIndexOf('=')
        if (i <= 0) null else PartStyle.decode(e.substring(i + 1))?.let { e.substring(0, i) to it }
    }.toMap()

    fun format(m: Map<String, PartStyle>): String = m.entries.filter { !it.value.plain }.joinToString(";") { "${it.key}=${it.value.encode()}" }

    /** [id]'s style (reads Compose state). */
    fun of(id: String): PartStyle = parse(Features.partStyles.state.value)[id] ?: PartStyle()

    fun set(id: String, style: PartStyle) {
        Features.partStyles.value = format(parse(Features.partStyles.value) + (id to style))
    }

    /** Parts that stay still out of the box: Play / Continue and the round buttons on their own (update 116). */
    fun stillByDefault(id: String): Boolean = HomeWidgets.baseId(id).let { it == HomeWidgets.H_PLAY || it == HomeWidgets.H_BUTTONS }
}

/** Inside a part set to stay still: rise-ins, lifts and bobbing are off (Motion.kt, PhysicalMedia.kt read it). */
internal val LocalStillPart = compositionLocalOf { false }

/**
 * Update 125: the part's own style handed to its content: "None" takes the content's own backing away too (it stays
 * see-through), a picked shape is the backing's shape. Read by PartBackingBox.
 */
internal class PartLook(val noBacking: Boolean, val shape: Shape?, val bg: Color?)
internal val LocalPartLook = compositionLocalOf<PartLook?> { null }

private fun shapeOf(s: PartShape): Shape? = when (s) {
    PartShape.AUTO -> null
    PartShape.PILL -> RoundedCornerShape(50)
    PartShape.ROUNDED -> RoundedCornerShape(14.dp)
    PartShape.SQUARE -> RoundedCornerShape(2.dp)
}

/** [content] in [id]'s style (a floating part, Edit Home's MovableWidget). */
@Composable
internal fun StyledPart(id: String, content: @Composable () -> Unit) {
    val st = PartStyles.of(id)
    val still = st.still || PartStyles.stillByDefault(id)
    if (st.plain && !still) { content(); return }
    val shape = shapeOf(st.shape) ?: RoundedCornerShape(14.dp)
    val bg = when (st.bg) {
        PartBg.AUTO, PartBg.NONE -> null
        // (Update 133: less see-through — as strong as the Backing slider says.)
        PartBg.DARK -> Color.Black.copy(alpha = 0.45f + backingUnder() * 0.75f)
        PartBg.LIGHT -> Color.White.copy(alpha = 0.85f)
        PartBg.THEME -> ShelfTheme.colors.surface.copy(alpha = 0.9f)
    }
    // Update 125: the backing picked here replaces the part's own one (it used to sit behind it, so a shape or a
    // see-through backing hardly showed); "None" leaves it fully see-through. The outline is a clear, thicker line.
    val look = PartLook(noBacking = st.bg != PartBg.AUTO, shape = shapeOf(st.shape), bg = bg)
    Box(
        Modifier
            .graphicsLayer { alpha = st.alpha / 100f }
            .then(if (bg != null || st.outline || st.shape != PartShape.AUTO) Modifier.clip(shape) else Modifier)
            .then(if (bg != null) Modifier.background(bg) else Modifier)
            .then(if (st.outline) Modifier.border(2.5.dp, ShelfTheme.accent, shape) else Modifier)
            .then(if (bg != null || st.outline) Modifier.padding(horizontal = if (st.shape == PartShape.PILL) 14.dp else 8.dp, vertical = 6.dp) else Modifier)
    ) {
        CompositionLocalProvider(LocalStillPart provides still, LocalPartLook provides look) { content() }
    }
}

/** [id]'s style on a layout's own part (Modifier: opacity, backing, outline and shape; it moves and sizes in Edit Home). */
@Composable
internal fun Modifier.partStyle(id: String): Modifier {
    val st = PartStyles.of(id)
    if (st.plain) return this
    val shape = shapeOf(st.shape) ?: RoundedCornerShape(14.dp)
    val bg = when (st.bg) {
        PartBg.AUTO, PartBg.NONE -> null
        // (Update 133: less see-through — as strong as the Backing slider says.)
        PartBg.DARK -> Color.Black.copy(alpha = 0.45f + backingUnder() * 0.75f)
        PartBg.LIGHT -> Color.White.copy(alpha = 0.85f)
        PartBg.THEME -> ShelfTheme.colors.surface.copy(alpha = 0.9f)
    }
    return this
        .graphicsLayer { alpha = st.alpha / 100f }
        .then(if (bg != null || st.outline || st.shape != PartShape.AUTO) Modifier.clip(shape) else Modifier)
        .then(if (bg != null) Modifier.background(bg) else Modifier)
        .then(if (st.outline) Modifier.border(2.5.dp, ShelfTheme.accent, shape) else Modifier)
}

/**
 * Edit Home › ✎ Style (or L3): the picked part's backing, see-through, outline, shape, still, size and where it lines
 * up (left / middle / right, top / middle / bottom of the screen).
 */
@Composable
internal fun PartStyleSheet(id: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            // Update 125: scrolls, so the last row (Reset style / Done) is always reachable on a short screen.
            val sheetScroll = androidx.compose.foundation.rememberScrollState()
            androidx.compose.foundation.layout.Column(
                Modifier.heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.82f).dp)
                    .verticalScroll(sheetScroll)
            ) {
            SectionLabel("Style · " + HomeWidgets.label(id))
            val st = PartStyles.of(id)
            fun put(n: PartStyle) = PartStyles.set(id, n)
            val first = remember { FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
            MutedNote("Backing")
            ChipFlow {
                PartBg.values().forEachIndexed { i, b ->
                    Box(if (i == 0) Modifier.focusRequester(first) else Modifier) { Chip(b.label, accent = st.bg == b) { put(st.copy(bg = b)) } }
                }
            }
            // Update 133: sliders instead of steps — the whole part's see-through, and how solid every part's backing is.
            PercentSlider("See-through (this part)", st.alpha, { put(st.copy(alpha = ((it / 5) * 5).coerceIn(20, 100))) }, min = 20, max = 100, valueText = { if (it >= 100) "Solid" else "$it %" })
            Spacer(Modifier.height(4.dp))
            PercentSlider("Backing (every part)", Features.partBackingSolid.state.value, { Features.partBackingSolid.value = ((it / 5) * 5).coerceIn(0, 100) }, min = 0, max = 100, valueText = { if (it == 0) "Light" else "$it %" })
            Spacer(Modifier.height(4.dp))
            MutedNote("Shape and outline")
            ChipFlow {
                PartShape.values().forEach { s -> Chip(s.label, accent = st.shape == s) { put(st.copy(shape = s)) } }
                Chip((if (st.outline) "✓ " else "") + "Outline", accent = st.outline) { put(st.copy(outline = !st.outline)) }
            }
            if (!id.startsWith(LayoutParts.PREFIX)) {
                MutedNote("Movement")
                val stillNow = st.still || PartStyles.stillByDefault(id)
                ChipFlow {
                    Chip((if (stillNow) "✓ " else "") + "Stays still", accent = stillNow) { put(st.copy(still = true)) }
                    if (!PartStyles.stillByDefault(id)) Chip((if (!stillNow) "✓ " else "") + "Animated", accent = !stillNow) { put(st.copy(still = false)) }
                }
            }
            MutedNote("Size")
            ChipFlow {
                val now = HomeEditState.liveScale[id] ?: 1f
                listOf(0.7f to "70 %", 0.85f to "85 %", 1f to "100 %", 1.25f to "125 %", 1.5f to "150 %").forEach { (v, l) ->
                    Chip(l, accent = kotlin.math.abs(now - v) < 0.02f) { HomeEditState.liveScale[id] = v }
                }
            }
            // Update 133: stretched on its own, wider or taller (the same as its side and bottom handles).
            val str = HomeEditState.liveStretch[id] ?: (1f to 1f)
            PercentSlider("Stretch wider", (str.first * 100).toInt(), { v ->
                HomeEditState.liveStretch[id] = (((v / 5) * 5) / 100f).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX) to str.second
            }, min = (HomeWidgets.STRETCH_MIN * 100).toInt(), max = (HomeWidgets.STRETCH_MAX * 100).toInt(), valueText = { "$it %" })
            PercentSlider("Stretch taller", (str.second * 100).toInt(), { v ->
                HomeEditState.liveStretch[id] = str.first to (((v / 5) * 5) / 100f).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX)
            }, min = (HomeWidgets.STRETCH_MIN * 100).toInt(), max = (HomeWidgets.STRETCH_MAX * 100).toInt(), valueText = { "$it %" })
            Spacer(Modifier.height(4.dp))
            if (!id.startsWith(LayoutParts.PREFIX)) {
                MutedNote("Line up on the screen")
                ChipFlow {
                    val cur = HomeEditState.live[id]
                    fun at(x: Float?, y: Float?) {
                        val c = HomeEditState.live[id] ?: (0.5f to 0.5f)
                        HomeEditState.live[id] = (x ?: c.first) to (y ?: c.second)
                    }
                    Chip("Left", accent = cur?.first == 0f) { at(0f, null) }
                    Chip("Centre", accent = cur?.first == 0.5f) { at(0.5f, null) }
                    Chip("Right", accent = cur?.first == 1f) { at(1f, null) }
                    Chip("Top", accent = cur?.second == 0f) { at(null, 0f) }
                    Chip("Middle", accent = cur?.second == 0.5f) { at(null, 0.5f) }
                    Chip("Bottom", accent = cur?.second == 1f) { at(null, 1f) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(Modifier.align(Alignment.CenterEnd)) {
                    Chip("Reset style", accent = false) { PartStyles.set(id, PartStyle()); HomeEditState.liveStretch.remove(id) }
                    Spacer(Modifier.padding(4.dp))
                    Chip("Done", onClick = onDismiss)
                }
            }
            }
        }
    }
}
