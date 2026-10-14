package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * The Volt look (Settings › Appearance › Interface style): Voltcap's own design language, layered on top of
 * whatever theme is on — it never replaces the theme's colours, it uses them.
 *
 *  - Panels are dark glass: the theme's raised surface with a faint top-lit gradient, a 1 px hairline border
 *    and a brighter sheen along the top edge.
 *  - Focus is the accent: a crisp accent outline with a soft glow inside it (the classic look keeps the
 *    white / dark double ring).
 *  - The "voltage line": a thin accent rule with a spark that runs along it now and then — under dialog
 *    headers, beside the quick settings, along the second screen's header. Voltcap's signature.
 *  - Switches are accent pills, status cards carry a coloured left edge, tabs are underlined.
 *
 * Every piece reads [on] (Compose state), so switching the style redraws everything at once. Classic = the
 * look before update 24, unchanged.
 */
internal object VoltUi {
    /** One of the Volt looks is on (Glass, Energy capsule, 32-bit console); false = Classic. */
    val on: Boolean get() = Features.uiStyle.state.value != Features.UiStyle.CLASSIC
    /** Energy capsule: panels like the Voltcap capsule — dark dot-matrix core, glowing double rim. */
    val capsule: Boolean get() = Features.uiStyle.state.value == Features.UiStyle.CAPSULE
    /** 32-bit console: flat panels with a pixel bevel and faint scanlines, hard pixel focus, no glow. */
    val pixel: Boolean get() = Features.uiStyle.state.value == Features.UiStyle.PIXEL
    /** The voltage line's spark runs (off: a still line). */
    val sparks: Boolean get() = Features.voltLine.state.value
    /** Focus glow on (a bit of extra drawing per focused element). */
    val glow: Boolean get() = Features.uiGlow.state.value

    /** Hairline border on glass. */
    val hairline = Color.White.copy(alpha = 0.09f)
    /** The top sheen of a glass panel. */
    val sheen = Color.White.copy(alpha = 0.16f)
}

/**
 * A panel's surface: Volt = dark glass (gradient, hairline, top sheen); Classic = the plain [base] fill.
 * Clips to [shape]. Use in place of `.clip(shape).background(surfaceRaised)`.
 */
@Composable
internal fun Modifier.voltSurface(
    shape: Shape,
    base: Color = ShelfTheme.colors.surfaceRaised,
    alpha: Float = 1f,
    edge: Color? = null
): Modifier {
    if (!VoltUi.on) return this.clip(shape).background(base.copy(alpha = base.alpha * alpha))
    when (Features.uiStyle.state.value) {
        Features.UiStyle.CAPSULE -> return capsuleSurface(shape, base, alpha, edge, ShelfTheme.accent)
        Features.UiStyle.PIXEL -> return pixelSurface(shape, base, alpha, edge, ShelfTheme.accent)
        Features.UiStyle.MEMORY_CARD -> return memoryCardSurface(shape, base, alpha, edge, ShelfTheme.accent, ShelfTheme.colors.secondary)
        Features.UiStyle.CRT -> return crtSurface(shape, base, alpha, edge, ShelfTheme.accent)
        Features.UiStyle.NEON -> return neonSurface(shape, base, alpha, edge, ShelfTheme.accent)
        Features.UiStyle.CARTRIDGE -> return cartridgeSurface(shape, base, alpha, edge, ShelfTheme.accent)
        else -> Unit
    }
    val top = lerpColor(base, Color.White, 0.05f).copy(alpha = base.alpha * alpha)
    val bottom = lerpColor(base, Color.Black, 0.18f).copy(alpha = base.alpha * alpha)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(1.dp, VoltUi.hairline, shape)
        .drawWithContent {
            drawContent()
            // The sheen: a short bright line along the top, fading to the sides.
            val y = 1.dp.toPx()
            val w = size.width
            if (w > 0f) drawLine(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.25f to VoltUi.sheen, 0.75f to VoltUi.sheen, 1f to Color.Transparent
                ),
                Offset(w * 0.06f, y), Offset(w * 0.94f, y), strokeWidth = 1.dp.toPx()
            )
            // A status edge on the left (notifications, warnings): 3 dp, full height.
            if (edge != null) drawRect(edge, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
        }
}

/**
 * Energy capsule panel, after the Voltcap capsule: a darker core with a faint dot-matrix, light rising from
 * the bottom edge, and a glowing double rim in the accent.
 */
private fun Modifier.capsuleSurface(shape: Shape, base: Color, alpha: Float, edge: Color?, accent: Color): Modifier {
    val core = lerpColor(base, Color.Black, 0.38f).copy(alpha = base.alpha * alpha)
    return this
        .clip(shape)
        .background(core)
        .drawWithCache {
            val step = 5.dp.toPx()
            val dots = ArrayList<Offset>()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) { dots += Offset(x, y); x += step }
                y += step
                if (dots.size > 6000) break
            }
            val dot = 1.2.dp.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            val rim = 1.5.dp.toPx()
            val inset = 3.5.dp.toPx()
            val inner = shape.createOutline(
                androidx.compose.ui.geometry.Size((size.width - inset * 2).coerceAtLeast(1f), (size.height - inset * 2).coerceAtLeast(1f)),
                layoutDirection, this
            )
            onDrawWithContent {
                drawPoints(dots, androidx.compose.ui.graphics.PointMode.Points, Color.White.copy(alpha = 0.045f), strokeWidth = dot, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                // Light rising from the bottom, like the capsule's glow.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.10f)), startY = size.height * 0.55f, endY = size.height))
                drawContent()
                drawOutline(outline, accent.copy(alpha = 0.18f), style = Stroke(rim * 3f))
                drawOutline(outline, accent.copy(alpha = 0.7f), style = Stroke(rim))
                translate(inset, inset) {
                    drawOutline(inner, accent.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
                }
                if (edge != null) drawRect(edge, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
        }
}

/**
 * Memory card panel: smoky translucent plastic tinted with the theme, a label strip along the top, grip ridges
 * down the right edge and a diagonal sheen across it.
 */
private fun Modifier.memoryCardSurface(shape: Shape, base: Color, alpha: Float, edge: Color?, accent: Color, second: Color): Modifier {
    val plastic = lerpColor(base, second, 0.10f).copy(alpha = base.alpha * alpha * 0.86f)
    return this
        .clip(shape)
        .background(plastic)
        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
        .drawWithContent {
            // The label strip (a sticker in the accent), with a light top edge.
            val strip = 7.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.16f)), startY = 0f, endY = strip), size = androidx.compose.ui.geometry.Size(size.width, strip))
            drawRect(Color.White.copy(alpha = 0.2f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            drawContent()
            // Grip ridges down the right edge.
            val rx = size.width - 9.dp.toPx()
            var y = strip + 8.dp.toPx()
            val gap = 4.dp.toPx()
            var n = 0
            while (y < size.height - 8.dp.toPx() && n < 12) {
                drawLine(Color.White.copy(alpha = 0.12f), Offset(rx, y), Offset(rx + 5.dp.toPx(), y), 1.dp.toPx())
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(rx, y + 1.dp.toPx()), Offset(rx + 5.dp.toPx(), y + 1.dp.toPx()), 1.dp.toPx())
                y += gap; n++
            }
            // Diagonal sheen across the plastic.
            drawRect(Brush.linearGradient(
                0f to Color.Transparent, 0.42f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.07f), 0.58f to Color.Transparent, 1f to Color.Transparent,
                start = Offset.Zero, end = Offset(size.width, size.height)
            ))
            if (edge != null) drawRect(edge, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
        }
}

/**
 * CRT panel: a softly lit "screen" — light in the middle, darker toward the corners, fine scanlines, and a
 * red / blue fringe on its edges like a tube's convergence.
 */
private fun Modifier.crtSurface(shape: Shape, base: Color, alpha: Float, edge: Color?, accent: Color): Modifier {
    val fill = base.copy(alpha = base.alpha * alpha)
    return this
        .clip(shape)
        .background(fill)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val vignette = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.07f), Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                center = Offset(size.width / 2f, size.height / 2f), radius = maxOf(size.width, size.height) * 0.75f
            )
            val gap = 3.dp.toPx()
            val f = 1.dp.toPx()
            onDrawWithContent {
                drawRect(vignette)
                drawContent()
                var y = 0f
                while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.09f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += gap }
                translate(-f, 0f) { drawOutline(outline, Color(0xFFFF4060).copy(alpha = 0.35f), style = Stroke(1.dp.toPx())) }
                translate(f, 0f) { drawOutline(outline, Color(0xFF40A0FF).copy(alpha = 0.35f), style = Stroke(1.dp.toPx())) }
                drawOutline(outline, Color.White.copy(alpha = 0.14f), style = Stroke(1.dp.toPx()))
                if (edge != null) drawRect(edge, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
        }
}

/**
 * Neon sign panel: nearly black, edged with a glowing tube in the accent (a wide soft glow, a bright core). It
 * flickers on when it appears.
 */
@Composable
private fun Modifier.neonSurface(shape: Shape, base: Color, alpha: Float, edge: Color?, accent: Color): Modifier {
    val fill = lerpColor(base, Color.Black, 0.55f).copy(alpha = base.alpha * alpha)
    val lite = LocalLiteEffects.current
    val power = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (lite) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (lite) return@LaunchedEffect
        for ((v, ms) in listOf(0.7f to 60L, 0.1f to 70L, 0.9f to 50L, 0.35f to 80L, 1f to 0L)) {
            power.snapTo(v); kotlinx.coroutines.delay(ms)
        }
    }
    val core = lerpColor(accent, Color.White, 0.55f)
    return this
        .clip(shape)
        .background(fill)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val inset = 3.dp.toPx()
            val inner = shape.createOutline(
                androidx.compose.ui.geometry.Size((size.width - inset * 2).coerceAtLeast(1f), (size.height - inset * 2).coerceAtLeast(1f)),
                layoutDirection, this
            )
            onDrawWithContent {
                drawContent()
                val p = power.value
                translate(inset, inset) {
                    drawOutline(inner, accent.copy(alpha = 0.12f * p), style = Stroke(9.dp.toPx()))
                    drawOutline(inner, accent.copy(alpha = 0.35f * p), style = Stroke(4.dp.toPx()))
                    drawOutline(inner, core.copy(alpha = 0.95f * p), style = Stroke(1.4.dp.toPx()))
                }
                drawOutline(outline, Color.White.copy(alpha = 0.05f), style = Stroke(1.dp.toPx()))
                if (edge != null) drawRect(edge, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            }
        }
}

/**
 * Cartridge panel: a plastic shell with grip ridges down both sides and the content on a lighter "label" with
 * an accent stripe at its top, like a game cartridge's sticker.
 */
private fun Modifier.cartridgeSurface(shape: Shape, base: Color, alpha: Float, edge: Color?, accent: Color): Modifier {
    val shell = lerpColor(base, Color(0xFF5A5C66), 0.18f).copy(alpha = base.alpha * alpha)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(lerpColor(shell, Color.White, 0.06f), lerpColor(shell, Color.Black, 0.2f))))
        .drawWithContent {
            val side = 7.dp.toPx()
            val lab = androidx.compose.ui.geometry.Size((size.width - side * 2).coerceAtLeast(0f), (size.height - 6.dp.toPx()).coerceAtLeast(0f))
            // The label.
            drawRoundRect(lerpColor(shell, Color.White, 0.07f), topLeft = Offset(side, 3.dp.toPx()), size = lab, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRect(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.35f))), topLeft = Offset(side, 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(lab.width, 3.dp.toPx()))
            drawContent()
            // Grip ridges down both sides.
            val gap = 5.dp.toPx()
            var y = 10.dp.toPx(); var n = 0
            while (y < size.height - 8.dp.toPx() && n < 14) {
                for (x in listOf(2.dp.toPx(), size.width - side + 2.dp.toPx())) {
                    drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, y), Offset(x + side - 4.dp.toPx(), y), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.10f), Offset(x, y + 1.dp.toPx()), Offset(x + side - 4.dp.toPx(), y + 1.dp.toPx()), 1.dp.toPx())
                }
                y += gap; n++
            }
            if (edge != null) drawRect(edge, topLeft = Offset(side, 0f), size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
        }
}

/**
 * 32-bit console panel: a flat fill with a 2 px bevel (light top-left, dark bottom-right), an accent pixel line
 * under the top bevel and faint scanlines — the menus of the 32-bit era.
 */
private fun Modifier.pixelSurface(shape: Shape, base: Color, alpha: Float, edge: Color?, accent: Color): Modifier {
    val fill = base.copy(alpha = base.alpha * alpha)
    return this
        .clip(shape)
        .background(fill)
        .drawWithContent {
            val px = 2.dp.toPx()
            val gap = 3.dp.toPx()
            var y = 0f
            while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += gap }
            drawContent()
            val w = size.width; val h = size.height
            drawRect(Color.White.copy(alpha = 0.22f), Offset.Zero, androidx.compose.ui.geometry.Size(w, px))
            drawRect(Color.White.copy(alpha = 0.22f), Offset.Zero, androidx.compose.ui.geometry.Size(px, h))
            drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, h - px), androidx.compose.ui.geometry.Size(w, px))
            drawRect(Color.Black.copy(alpha = 0.45f), Offset(w - px, 0f), androidx.compose.ui.geometry.Size(px, h))
            drawRect(accent.copy(alpha = 0.55f), Offset(px, px), androidx.compose.ui.geometry.Size(w - px * 2, px / 2))
            if (edge != null) drawRect(edge, topLeft = Offset(px, 0f), size = androidx.compose.ui.geometry.Size(px * 2, h))
        }
}

/**
 * The theme's palette as a fill: its accent flowing into its second colour, so filled pieces (pills, the
 * picked section, switches, bars) carry the whole theme and not one flat colour. Themes whose two colours
 * are the same simply get a slight sheen.
 */
@Composable
internal fun voltFill(vertical: Boolean = false): Brush {
    val a = ShelfTheme.accent
    val b = lerpColor(a, ShelfTheme.colors.secondary, 0.6f)
    return if (vertical) Brush.verticalGradient(listOf(lerpColor(a, Color.White, 0.12f), a, b))
    else Brush.horizontalGradient(listOf(lerpColor(a, Color.White, 0.08f), a, b))
}

/** The [i]-th colour of the theme's palette (accent, second colour and blends): for sets of buttons. */
@Composable
internal fun voltPaletteColor(i: Int): Color {
    val a = ShelfTheme.accent
    val s = ShelfTheme.colors.secondary
    return when (i.mod(4)) {
        0 -> a
        1 -> s
        2 -> lerpColor(a, s, 0.5f)
        else -> lerpColor(a, Color.White, 0.28f)
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha
    )

/**
 * The Volt focus: an accent outline with a glow inside it and a faint accent wash over the element. Drawn
 * on top of the content and inside its bounds (callers clip), so it never changes a size.
 */
internal fun Modifier.voltFocus(focused: Boolean, shape: Shape, accent: Color, glow: Boolean, pixel: Boolean = false, crt: Boolean = false): Modifier =
    if (!focused) this else if (crt) this.drawWithContent {
        drawContent()
        // CRT: the outline splits into red / blue like a tube's convergence, the accent in the middle.
        val w = 2.dp.toPx()
        if (size.width > w * 6 && size.height > w * 6) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, accent.copy(alpha = 0.08f))
            translate(-w * 0.8f, 0f) { drawOutline(outline, Color(0xFFFF3B5C).copy(alpha = 0.7f), style = Stroke(w)) }
            translate(w * 0.8f, 0f) { drawOutline(outline, Color(0xFF3BA8FF).copy(alpha = 0.7f), style = Stroke(w)) }
            drawOutline(outline, accent, style = Stroke(w))
        }
    } else if (pixel) this.drawWithContent {
        drawContent()
        // 32-bit console: a hard 3 px accent frame with a dark inner line and corner blocks.
        val w = 3.dp.toPx()
        if (size.width > w * 6 && size.height > w * 6) {
            drawRect(accent, style = Stroke(w * 2))
            drawRect(Color.Black.copy(alpha = 0.6f), topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(size.width - w * 2, size.height - w * 2), style = Stroke(1.dp.toPx()))
            val c = w * 2.5f
            for ((x, y) in listOf(0f to 0f, size.width - c to 0f, 0f to size.height - c, size.width - c to size.height - c)) {
                drawRect(Color.White.copy(alpha = 0.85f), Offset(x, y), androidx.compose.ui.geometry.Size(c, c))
            }
        }
    } else this.drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        if (size.width > w * 6 && size.height > w * 6) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, accent.copy(alpha = 0.07f))
            if (glow) {
                inset(w) { drawOutline(shape.createOutline(size, layoutDirection, this), accent.copy(alpha = 0.22f), style = Stroke(w * 2.2f)) }
                inset(w * 2.6f) { drawOutline(shape.createOutline(size, layoutDirection, this), accent.copy(alpha = 0.08f), style = Stroke(w * 2.5f)) }
            }
            drawOutline(outline, accent, style = Stroke(w))
        }
    }

/**
 * The voltage line: a thin accent rule, brightest in the middle, with a spark that runs along it every few
 * seconds. [vertical] draws it top to bottom. Still (no spark) with Lite effects, while idle, when the
 * system's animations are off, or with Settings › Interface › Voltage line sparks off.
 */
/** The shared spark clock of every [VoltageLine]: 1.6 s crossing, then rest, every 4 s. */
private const val VOLT_SPARK_CYCLE = 4_000L
private const val VOLT_SPARK_RUN = 1_600L

@Composable
internal fun VoltageLine(modifier: Modifier = Modifier, vertical: Boolean = false, color: Color = ShelfTheme.accent, thickness: Dp = 1.dp) {
    val color2 = lerpColor(color, ShelfTheme.colors.secondary, 0.7f)
    val pixel = VoltUi.pixel
    val animate = VoltUi.sparks && !LocalLiteEffects.current && !HomeIdle.calm && rememberAnimationsEnabled() && rememberIsActive()
    // The spark crosses in 1.6 s, then the line rests 2.4 s with no frames drawn at all.
    val spark = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(animate) {
        if (!animate) { spark.snapTo(1f); return@LaunchedEffect }
        // Every line on screen runs on one shared 4 s clock, so all sparks cross together (in sync), whenever
        // each line appeared.
        while (true) {
            val inCycle = android.os.SystemClock.uptimeMillis() % VOLT_SPARK_CYCLE
            if (inCycle < VOLT_SPARK_RUN) {
                spark.snapTo(inCycle / VOLT_SPARK_RUN.toFloat())
                spark.animateTo(1f, tween((VOLT_SPARK_RUN - inCycle).toInt(), easing = LinearEasing))
            } else spark.snapTo(1f)
            kotlinx.coroutines.delay(VOLT_SPARK_CYCLE - android.os.SystemClock.uptimeMillis() % VOLT_SPARK_CYCLE)
        }
    }
    val phase = spark.asState()
    Canvas(modifier.then(if (vertical) Modifier.width(thickness * 5) else Modifier.height(thickness * 5))) {
        val len = if (vertical) size.height else size.width
        if (len < 1f) return@Canvas
        val mid = if (vertical) size.width / 2 else size.height / 2
        val stroke = thickness.toPx()
        fun p(at: Float) = if (vertical) Offset(mid, at) else Offset(at, mid)
        val base = Brush.linearGradient(
            0f to color.copy(alpha = 0f), 0.2f to color.copy(alpha = 0.6f), 0.8f to color2.copy(alpha = 0.6f), 1f to color2.copy(alpha = 0f),
            start = p(0f), end = p(len)
        )
        if (pixel) {
            // 32-bit: a dashed pixel rule, the spark a bright block hopping along it.
            val seg = 4.dp.toPx(); val gapL = 2.dp.toPx(); val th = 2.dp.toPx()
            var a = 0f
            while (a < len) {
                val e = minOf(a + seg, len)
                val c = lerpColor(color, color2, a / len)
                if (vertical) drawRect(c.copy(alpha = 0.7f), Offset(mid - th / 2, a), androidx.compose.ui.geometry.Size(th, e - a))
                else drawRect(c.copy(alpha = 0.7f), Offset(a, mid - th / 2), androidx.compose.ui.geometry.Size(e - a, th))
                a += seg + gapL
            }
            val ph = phase.value
            if (ph < 1f) {
                val x = (len * ph / (seg + gapL)).toInt() * (seg + gapL)
                val b = 4.dp.toPx()
                drawRect(Color.White, if (vertical) Offset(mid - b / 2, x) else Offset(x, mid - b / 2), androidx.compose.ui.geometry.Size(b, b))
            }
            return@Canvas
        }
        drawLine(base, p(0f), p(len), strokeWidth = stroke)
        val ph = phase.value
        if (ph < 1f) {
            val x = len * ph
            val tail = len * 0.12f
            drawLine(
                Brush.linearGradient(listOf(color.copy(alpha = 0f), color), start = p(x - tail), end = p(x)),
                p((x - tail).coerceAtLeast(0f)), p(x), strokeWidth = stroke * 2f
            )
            drawCircle(color.copy(alpha = 0.35f), radius = stroke * 4f, center = p(x))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = stroke * 1.4f, center = p(x))
        }
    }
}

/** A gold (accent) pill switch: Volt's toggle. Just the indicator — the row around it is the button. */
@Composable
internal fun VoltSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = ShelfTheme.accent
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "voltSwitch")
    val thumbOn = onAccentColor()
    val fill = voltFill()
    val sw: Shape = if (VoltUi.pixel) androidx.compose.foundation.shape.RoundedCornerShape(2.dp) else CircleShape
    Box(
        modifier
            .size(width = 42.dp, height = 24.dp)
            .drawBehind {
                if (t > 0f) drawRoundRect(
                    accent.copy(alpha = 0.28f * t),
                    topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height)
                )
            }
            .clip(sw)
            .background(Color.White.copy(alpha = 0.10f * (1f - t)))
            .background(fill, alpha = t)
            .border(1.dp, if (checked) accent else Color.White.copy(alpha = 0.18f), sw)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = 3.dp + 18.dp * t)
                .size(18.dp)
                .clip(sw)
                .background(if (checked) thumbOn else ShelfTheme.textMuted)
        )
    }
}

/** Underlined tabs (the second screen, pages with views): the selected one in the accent with a bar under it. */
@Composable
internal fun VoltTabs(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier
                    .clip(uiRounded(8.dp))
                    .shelfClickable(uiRounded(8.dp)) { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = if (on) ShelfTheme.accent else ShelfTheme.textMuted,
                    fontSize = 13.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(13.sp), maxFontSize = 13.sp),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(2.dp).clip(CircleShape)
                        .background(if (on) ShelfTheme.accent else Color.Transparent)
                )
            }
        }
    }
}

/**
 * A card with a coloured left edge (notifications, warnings, sync status): [edge] says what kind it is —
 * the accent for news, green for done, amber for attention, red for errors.
 */
@Composable
internal fun VoltEdgeCard(edge: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = uiRounded(12.dp)
    if (VoltUi.on) {
        Column(modifier.fillMaxWidth().voltSurface(shape, edge = edge).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), content = content)
    } else {
        Row(modifier.fillMaxWidth().clip(shape).background(ShelfTheme.colors.surfaceRaised)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(edge))
            Column(Modifier.padding(start = 13.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), content = content)
        }
    }
}

/** A small capsule with a lightning bolt: the node on a vertical voltage rail. */
@Composable
internal fun VoltNode(modifier: Modifier = Modifier) {
    val accent = ShelfTheme.accent
    Box(
        modifier
            .size(width = 18.dp, height = 30.dp)
            .clip(CircleShape)
            .background(voltFill(vertical = true))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // A drawn bolt (an emoji would always be yellow, whatever the theme).
        val ink = onAccentColor()
        Canvas(Modifier.size(width = 9.dp, height = 14.dp)) {
            val w = size.width; val h = size.height
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.62f, 0f); lineTo(w * 0.05f, h * 0.58f); lineTo(w * 0.46f, h * 0.58f)
                lineTo(w * 0.34f, h); lineTo(w * 0.95f, h * 0.38f); lineTo(w * 0.54f, h * 0.38f); close()
            }, ink)
        }
    }
}

/** A section heading in the Volt style: small caps in the accent with a short voltage line after it. */
@Composable
internal fun VoltHeading(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontFamily = headingFont())
        if (VoltUi.on) {
            Spacer(Modifier.width(10.dp))
            VoltageLine(Modifier.weight(1f))
        }
    }
}
