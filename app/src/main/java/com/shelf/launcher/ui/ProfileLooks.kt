package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features.PickerLook
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * v0.6.67: the looks of "Who's playing?" (Features.pickerLook). Each is plain Compose drawing — gradients, a few soft lights,
 * lines and outlines; no shaders, no new libraries. What changes per look: the backdrop, the shape of the pictures, how the
 * profile you're on is lit, the type and the button hints. A profile's own colour (Profiles.Profile.hue) lights its ring,
 * its highlight and its accents in every look — never the rest of the app.
 */

/** Everything a look decides. */
internal class PickerStyle(
    val look: PickerLook,
    /** Pictures: a circle, or a rounded square (Console classic). */
    val avatarShape: Shape,
    /** The title's colour, size factor, weight, letter spacing and font. */
    val title: Color,
    val titleWeight: FontWeight,
    val titleSpacing: Float,
    val titleFont: FontFamily?,
    /** Names: the one you're on / the others. */
    val nameOn: Color,
    val nameOff: Color,
    /** Hints along the bottom: text and the round button glyph's fill. */
    val hint: Color,
    val hintKey: Color,
    /** A card behind each profile (glass, console tile), or none. */
    val card: Color?,
    val cardEdge: Color?,
    /** The ring of the one you're on: its width; others get [ringOff]. */
    val ringOn: Dp,
    val ringOff: Color,
    /** The soft light round the one you're on (0 = none: Minimal OLED). */
    val glow: Float,
    /** The others step back this much (alpha). */
    val dimOthers: Float,
    /** The ring of the one you're on: two outlines (Neon arcade). */
    val doubleRing: Boolean = false
) {
    /** A profile's own colour at this look's brightness. */
    fun accent(hue: Float): Color = when (look) {
        PickerLook.NEON -> Color.hsv(hue.mod(360f), 0.75f, 1f)
        PickerLook.OLED -> Color.hsv(hue.mod(360f), 0.6f, 1f)
        PickerLook.CONSOLE -> Color.hsv(hue.mod(360f), 0.5f, 0.95f)
        else -> Color.hsv(hue.mod(360f), 0.55f, 1f)
    }
    /** The fill behind an initial (no picture). */
    fun initialFill(hue: Float): Brush = when (look) {
        PickerLook.OLED -> Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF1C1C1C)))
        PickerLook.NEON -> Brush.linearGradient(listOf(Color.hsv(hue.mod(360f), 0.8f, 0.30f), Color(0xFF0A0418)))
        else -> Brush.linearGradient(listOf(Color.hsv(hue.mod(360f), 0.45f, 0.55f), Color.hsv((hue + 25f).mod(360f), 0.6f, 0.30f)))
    }
}

@Composable
internal fun pickerStyle(look: PickerLook): PickerStyle = when (look) {
    PickerLook.AURORA -> PickerStyle(look, CircleShape, Color.White, FontWeight.Light, 1.5f, null, Color.White, Color.White.copy(alpha = 0.72f),
        Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.14f),
        3.dp, Color.White.copy(alpha = 0.22f), 0.6f, 0.62f)
    PickerLook.CONSOLE -> PickerStyle(look, RoundedCornerShape(22), Color(0xFFF2F4F7), FontWeight.SemiBold, 0f, null, Color.White, Color(0xFFB5BCC6),
        Color(0xFFD5DAE1), Color(0xFF3A414B), null, null, 4.dp, Color.White.copy(alpha = 0.10f), 0.25f, 0.7f)
    PickerLook.NEON -> PickerStyle(look, CircleShape, Color(0xFFFF7AE6), FontWeight.Bold, 2f, FontFamily.Monospace, Color.White, Color(0xFFB9A8E8),
        Color(0xFFE6DCFF), Color(0xFF2A1450), null, null, 3.dp, Color(0x55B07CFF), 0.8f, 0.58f, doubleRing = true)
    PickerLook.OLED -> PickerStyle(look, CircleShape, Color.White, FontWeight.Medium, 0.5f, null, Color.White, Color(0xFFBDBDBD),
        Color(0xFFE0E0E0), Color(0xFF262626), null, null, 3.dp, Color.White.copy(alpha = 0.18f), 0f, 0.72f)
    PickerLook.THEME -> PickerStyle(look, CircleShape, ShelfTheme.colors.textPrimary, FontWeight.SemiBold, 0.5f, null, ShelfTheme.colors.textPrimary,
        ShelfTheme.textMuted, ShelfTheme.colors.textPrimary.copy(alpha = 0.8f), ShelfTheme.accent.copy(alpha = 0.22f),
        ShelfTheme.colors.textPrimary.copy(alpha = 0.05f), ShelfTheme.accent.copy(alpha = 0.25f), 3.dp, ShelfTheme.colors.textPrimary.copy(alpha = 0.22f), 0.5f, 0.64f)
    PickerLook.MIDNIGHT -> PickerStyle(look, CircleShape, Color.White, FontWeight.SemiBold, 0f, null, Color.White, Color.White.copy(alpha = 0.7f),
        Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.14f), null, null, 3.dp, Color.White.copy(alpha = 0.25f), 0.55f, 0.62f)
}

/**
 * The look's backdrop, full screen. [accent]: the colour of the profile you're on (Aurora / Neon / Theme lean toward it).
 * [animate]: slow drifting light (off with Lite effects, Minimal OLED and Console classic never move).
 */
@Composable
internal fun PickerBackdrop(look: PickerLook, accent: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val moving = animate && look != PickerLook.OLED && look != PickerLook.CONSOLE
    val t = remember { mutableFloatStateOf(0f) }
    // Update 513 (Thor: "Who's playing?" left waiting drew 120 frames a second, ~73 % CPU): the light drifts a few pixels a
    // second, so after ~10 s without input (HomeIdle.settled, as the home's own idle effects since 510 / 511 — Save power when
    // idle) it holds where it is and nothing redraws; the first press carries it on from that same moment (no jump).
    if (moving && !HomeIdle.settled) LaunchedEffect(look) {
        val from = t.floatValue
        val first = withFrameNanos { it }
        val start = first - (from * 1e9f).toLong()
        while (true) withFrameNanos { now -> t.floatValue = (now - start) / 1e9f }
    }
    when (look) {
        PickerLook.AURORA -> Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A1036), Color(0xFF0D1638), Color(0xFF05060F))))) {
            Canvas(Modifier.fillMaxSize()) {
                val time = t.floatValue
                val w = size.width; val h = size.height
                // Three slow curtains of light: violet, blue, and the profile's own colour.
                fun blob(c: Color, x: Float, y: Float, r: Float, a: Float) =
                    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y))
                blob(Color(0xFF8E5CFF), w * (0.22f + 0.04f * kotlin.math.sin(time * 0.13f)), h * 0.18f, w * 0.48f, 0.42f)
                blob(Color(0xFF3D7BFF), w * (0.80f + 0.04f * kotlin.math.cos(time * 0.11f)), h * 0.28f, w * 0.44f, 0.36f)
                blob(accent, w * 0.5f, h * (0.64f + 0.02f * kotlin.math.sin(time * 0.2f)), w * 0.36f, 0.20f)
                // A thin horizon of light under the row.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), startY = h * 0.6f, endY = h * 0.8f))
            }
        }
        PickerLook.CONSOLE -> Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF242A32), Color(0xFF15191E), Color(0xFF0E1013))))) {
            Canvas(Modifier.fillMaxSize()) {
                // A soft spotlight from above and a floor line: a console's user screen, nothing moving.
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width / 2, -size.height * 0.1f),
                    radius = size.width * 0.7f), radius = size.width * 0.7f, center = Offset(size.width / 2, -size.height * 0.1f))
                drawLine(Color.White.copy(alpha = 0.06f), Offset(size.width * 0.08f, size.height * 0.82f), Offset(size.width * 0.92f, size.height * 0.82f), 1.dp.toPx())
            }
        }
        PickerLook.NEON -> Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0418), Color(0xFF140726), Color(0xFF05010A))))) {
            Canvas(Modifier.fillMaxSize()) {
                val time = t.floatValue
                val w = size.width; val h = size.height
                // A perspective grid floor, gently scrolling toward you, and faint scanlines.
                val horizon = h * 0.66f
                val grid = Color(0xFFB04CFF)
                val rows = 9
                for (i in 0 until rows) {
                    val k = ((i + (time * 0.35f) % 1f) / rows)
                    val y = horizon + (h - horizon) * k * k
                    drawLine(grid.copy(alpha = 0.10f + 0.22f * k), Offset(0f, y), Offset(w, y), 1.dp.toPx())
                }
                for (i in -8..8) {
                    val x = w / 2 + i * w * 0.07f
                    drawLine(grid.copy(alpha = 0.16f), Offset(w / 2 + i * w * 0.012f, horizon), Offset(x * 1f + (x - w / 2) * 1.6f, h), 1.dp.toPx())
                }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55FF4FD8), Color.Transparent), startY = horizon - 2.dp.toPx(), endY = horizon + 2.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), center = Offset(w / 2, horizon), radius = w * 0.35f),
                    radius = w * 0.35f, center = Offset(w / 2, horizon))
                var y = 0f
                val step = 4.dp.toPx()
                while (y < h) { drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, y), Offset(w, y), 1f); y += step }
            }
        }
        PickerLook.OLED -> Box(modifier.fillMaxSize().background(Color.Black))
        PickerLook.THEME -> Box(modifier.fillMaxSize().background(ShelfTheme.background)) {
            val themeAccent = ShelfTheme.accent
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.radialGradient(listOf(themeAccent.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.55f),
                    radius = size.width * 0.5f), radius = size.width * 0.5f, center = Offset(size.width / 2, size.height * 0.55f))
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.6f),
                    radius = size.width * 0.3f), radius = size.width * 0.3f, center = Offset(size.width / 2, size.height * 0.6f))
            }
        }
        PickerLook.MIDNIGHT -> Box(modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF16305E), Color(0xFF0A1734), Color(0xFF03060F)), radius = 1600f))) {
            if (animate) MidnightSparks(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().midnightFloor())
        }
    }
}

/** Midnight blue's soft floor of light under the row (as before). */
private fun Modifier.midnightFloor(): Modifier = this.drawBehind {
    drawOval(
        Brush.radialGradient(listOf(Color(0x3363A8FF), Color.Transparent), center = Offset(size.width / 2, size.height * 0.62f), radius = size.width * 0.4f),
        topLeft = Offset(size.width * 0.1f, size.height * 0.5f), size = Size(size.width * 0.8f, size.height * 0.24f)
    )
}

/** Midnight blue's slow sparks drifting up through the dark (the update 332 look), drawn on one canvas. */
@Composable
private fun MidnightSparks(modifier: Modifier) {
    val t = remember { mutableFloatStateOf(0f) }
    // (Update 513: settles with the home's idle settle like PickerBackdrop's light — measured on the RP Nova, 120 frames a
    // second / ~67 % CPU for as long as "Who's playing?" waited — and carries on from the same moment on the next press.)
    if (!HomeIdle.settled) LaunchedEffect(Unit) {
        val from = t.floatValue
        val first = withFrameNanos { it }
        val start = first - (from * 1e9f).toLong()
        while (true) withFrameNanos { now -> t.floatValue = (now - start) / 1e9f }
    }
    val seeds = remember { List(46) { i -> floatArrayOf(((i * 7919) % 1000) / 1000f, ((i * 104729) % 1000) / 1000f, 0.4f + ((i * 31) % 10) / 16f, ((i * 53) % 100) / 100f) } }
    Canvas(modifier) {
        val time = t.floatValue
        for (s in seeds) {
            val speed = 0.012f + 0.02f * s[3]
            val y = (s[1] - time * speed).let { it - kotlin.math.floor(it) }
            val x = s[0] + 0.012f * kotlin.math.sin(time * 0.6f + s[3] * 6f)
            val a = (0.18f + 0.35f * s[3]) * kotlin.math.sin(y * Math.PI.toFloat()).coerceAtLeast(0f)
            drawCircle(Color(0xFFBFD8FF).copy(alpha = a), radius = s[2] * 2.2f, center = Offset(x * size.width, y * size.height))
        }
    }
}

/**
 * v0.6.67: how big the pictures are for [count] choices (profiles + Add, 1–9) on a [w]×[h] dp screen. One row, like a
 * console's user screen: as big as fits (≤ 128 dp, ≤ a third of the height), never smaller than 76 dp (64 on a compact
 * screen) — when that many don't fit across, the row scrolls and keeps the one you're on in view ([scroll]). Names get a
 * slot 1.42 × the picture wide, 0.12 × apart, so they never run into each other. Pure maths, unit-tested.
 */
internal object PickerGrid {
    /** A name's words as they may be laid out: split at spaces, and after a hyphen ("Anastasia-", "Katharina"). */
    fun words(name: String): List<String> = name.split(' ').flatMap { w -> Regex("[^-]*-|[^-]+$").findAll(w).map { it.value }.toList() }.filter { it.isNotEmpty() }

    /** The name with a zero-width break after each hyphen inside a word, so a line may end there and nowhere inside a word. */
    fun breakable(name: String): String = name.replace(Regex("-(?=\\S)"), "-\u200B")

    data class Plan(val rows: Int, val perRow: Int, val avatar: Float, val slot: Float, val gap: Float, val scroll: Boolean) {
        /** The row's whole width (dp). */
        val rowWidth: Float get() = perRow * slot + (perRow - 1) * gap
    }

    const val SIDE = 24f

    fun plan(count: Int, w: Float, h: Float, compact: Boolean): Plan {
        val n = count.coerceAtLeast(1)
        val maxAvatar = if (compact) 96f else 128f
        val minAvatar = if (compact) 64f else 76f
        val byW = (w - 2 * SIDE) / (n * 1.42f + (n - 1) * 0.12f)
        val byH = h * 0.34f
        val a = minOf(maxAvatar, byH, maxOf(minAvatar, byW))
        val scroll = byW < a - 0.01f
        return Plan(1, n, a, a * 1.42f, a * 0.12f, scroll)
    }

    /** D-pad step inside the plan: index after pressing ◀ ▶ ▲ ▼ from [i] (clamped; ▲ ▼ keep the column as near as it can). */
    fun step(i: Int, count: Int, perRow: Int, dx: Int, dy: Int): Int {
        if (count <= 0) return 0
        if (dx != 0) return (i + dx).coerceIn(0, count - 1)
        val row = i / perRow
        val col = i % perRow
        val target = row + dy
        val rows = (count + perRow - 1) / perRow
        if (target < 0 || target >= rows) return i
        val inTarget = if (target == rows - 1) count - target * perRow else perRow
        return (target * perRow + col.coerceAtMost(inTarget - 1)).coerceIn(0, count - 1)
    }

    /** The colours offered in a profile's editor (hues, 0–360); the profile's own saved hue is always kept as it is. */
    val PRESETS = listOf(210f, 250f, 280f, 320f, 350f, 15f, 28f, 50f, 90f, 140f, 170f, 195f)
}

/**
 * v0.6.67: Settings' look picker — a small live picture of each look (its backdrop, three pictures with the middle one lit)
 * and its name; A / tap picks it (saved at once). Wraps onto more lines on small screens.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PickerLookThumbs() {
    val current = Features.pickerLook.state.value
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PickerLook.values().forEach { look ->
            val style = pickerStyle(look)
            val on = look == current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(132.dp).height(74.dp)
                        .shelfClickable(RoundedCornerShape(10.dp)) { Features.pickerLook.value = look }
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    PickerBackdrop(look, style.accent(28f), animate = false)
                    Row(
                        Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(210f, 28f, 140f).forEachIndexed { i, h ->
                            val lit = i == 1
                            Box(
                                Modifier.size(if (lit) 28.dp else 22.dp).clip(style.avatarShape).background(style.initialFill(h))
                                    .border(if (lit) 2.dp else 1.dp, if (lit) style.accent(h) else style.ringOff, style.avatarShape)
                            )
                        }
                    }
                    if (on) Box(Modifier.fillMaxSize().border(2.dp, ShelfTheme.accent, RoundedCornerShape(10.dp)))
                }
                Text(look.label, color = if (on) ShelfTheme.colors.textPrimary else ShelfTheme.textMuted, fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
