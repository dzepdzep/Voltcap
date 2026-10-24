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
 * Update 212: the selected part's appearance in Edit Home's inspector (HomeEditSession.kt › HomeInspector): its backing,
 * see-through, shape and outline, and whether it stays still. Each change is one step of the session's Undo; nothing is
 * kept until Done (Discard puts the styles back as they were).
 */
@Composable
internal fun PartLookControls(id: String, first: FocusRequester? = null) {
    val st = PartStyles.of(id)
    fun put(n: PartStyle) { HomeEditSession.push("style:$id"); PartStyles.set(id, n) }
    MutedNote("Backing")
    ChipFlow {
        PartBg.values().forEachIndexed { i, b ->
            Box(if (i == 0 && first != null) Modifier.focusRequester(first) else Modifier) { Chip(b.label, accent = st.bg == b) { put(st.copy(bg = b)) } }
        }
    }
    PercentSlider("See-through", st.alpha, { put(st.copy(alpha = ((it / 5) * 5).coerceIn(20, 100))) }, min = 20, max = 100, valueText = { if (it >= 100) "Solid" else "$it %" })
    Spacer(Modifier.height(4.dp))
    MutedNote("Shape and outline")
    ChipFlow {
        PartShape.values().forEach { s -> Chip(s.label, accent = st.shape == s) { put(st.copy(shape = s)) } }
        Chip((if (st.outline) "✓ " else "") + "Outline", accent = st.outline) { put(st.copy(outline = !st.outline)) }
    }
    if (!id.startsWith(LayoutParts.PREFIX)) {
        MutedNote("Animation")
        val stillNow = st.still || PartStyles.stillByDefault(id)
        ChipFlow {
            Chip((if (stillNow) "✓ " else "") + "Stays still", accent = stillNow) { put(st.copy(still = true)) }
            if (!PartStyles.stillByDefault(id)) Chip((if (!stillNow) "✓ " else "") + "Animated", accent = !stillNow) { put(st.copy(still = false)) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Chip("Reset appearance", accent = false) { HomeEditSession.push("style:$id"); PartStyles.set(id, PartStyle()) }
}

/**
 * Update 212: the selected part's size, stretch and (a floating part) where it lines up on the screen — the inspector's
 * Position & size group. The corner / side handles, L2 / R2 and the D-pad do the same on the home screen.
 */
@Composable
internal fun PartSizeControls(id: String, first: FocusRequester? = null) {
    MutedNote("Move")
    ChipFlow {
        val dirs = listOf("◀ Left" to (-1 to 0), "▲ Up" to (0 to -1), "▼ Down" to (0 to 1), "Right ▶" to (1 to 0))
        dirs.forEachIndexed { i, (l, d) ->
            Box(if (i == 0 && first != null) Modifier.focusRequester(first) else Modifier) { Chip(l, accent = false) { HomeEditState.nudge(id, d.first, d.second) } }
        }
    }
    MutedNote("Size")
    ChipFlow {
        val now = HomeEditState.liveScale[id] ?: 1f
        listOf(0.4f to "40 %", 0.55f to "55 %", 0.7f to "70 %", 0.85f to "85 %", 1f to "100 %", 1.25f to "125 %", 1.5f to "150 %").forEach { (v, l) ->
            Chip(l, accent = kotlin.math.abs(now - v) < 0.02f) { HomeEditSession.push("size:$id"); HomeEditState.liveScale[id] = v }
        }
    }
    val str = HomeEditState.liveStretch[id] ?: (1f to 1f)
    PercentSlider("Stretch wider", (str.first * 100).toInt(), { v ->
        HomeEditSession.push("stretch:$id")
        HomeEditState.liveStretch[id] = (((v / 5) * 5) / 100f).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX) to str.second
    }, min = (HomeWidgets.STRETCH_MIN * 100).toInt(), max = (HomeWidgets.STRETCH_MAX * 100).toInt(), valueText = { "$it %" })
    PercentSlider("Stretch taller", (str.second * 100).toInt(), { v ->
        HomeEditSession.push("stretch:$id")
        HomeEditState.liveStretch[id] = str.first to (((v / 5) * 5) / 100f).coerceIn(HomeWidgets.STRETCH_MIN, HomeWidgets.STRETCH_MAX)
    }, min = (HomeWidgets.STRETCH_MIN * 100).toInt(), max = (HomeWidgets.STRETCH_MAX * 100).toInt(), valueText = { "$it %" })
    if (!id.startsWith(LayoutParts.PREFIX)) {
        MutedNote("Line up on the screen")
        ChipFlow {
            val cur = HomeEditState.live[id]
            fun at(x: Float?, y: Float?) {
                HomeEditSession.push("line:$id")
                // (Update 241, #05: the other axis stays where the part is now — it jumped to the middle when the part
                // hadn't been moved yet; and a part lined up on the screen comes out of a hollow part, #06.)
                HomeNest.release(id)
                val c = HomeEditState.placeOf(id)
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
    MutedNote("Dragging snaps to the screen's edges, thirds and middle and to the other parts (a dashed guide shows while it snaps), else to a 2 % grid. The D-pad moves it a grid step; hold A with the D-pad to size it. Everything stays inside the safe zone (Settings › Screens › Safe zone).")
}
