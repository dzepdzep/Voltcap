package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.BackgroundStyle
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Settings › Presets: a little drawn screen of what a preset looks like (its home layout, background, card size and
 * focus effect), in the theme's colours. Drawn with Canvas (no screenshots or image files), so it costs nothing.
 */
@Composable
internal fun PresetPreview(
    layout: HomeLayout,
    background: BackgroundStyle,
    focusEffect: FocusEffect,
    /** Cards a bit bigger (Large) or smaller (Small) than normal. */
    cardScale: Float,
    showLegend: Boolean,
    modifier: Modifier = Modifier,
    /** The game look: covers, cases, cartridges / discs… drawn in the card slots. */
    art: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    corners: com.shelf.launcher.data.CardCorners = com.shelf.launcher.data.CardCorners.ROUNDED
) {
    val accent = ShelfTheme.accent
    val bg = ShelfTheme.colors.background
    val surface = ShelfTheme.colors.surface
    Canvas(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(uiRounded(12.dp))) {
        val p = PreviewPainter(this, accent, bg, surface, focusEffect, cardScale, art, corners.radiusDp / 10f)
        p.background(layout, background)
        when (layout) {
            HomeLayout.CLASSIC -> p.classic()
            HomeLayout.TILES -> p.tiles()
            HomeLayout.HERO -> p.hero()
            HomeLayout.GRID -> p.grid()
            HomeLayout.TV -> p.tv()
            HomeLayout.DASH -> p.dash()
            HomeLayout.LIVE -> p.live()
            HomeLayout.SIDE -> p.side()
            HomeLayout.CROSS -> p.cross()
            HomeLayout.SPOT -> p.spot()
            HomeLayout.BAR -> p.bar()
            HomeLayout.SHELF -> p.shelf()
            HomeLayout.HUB -> p.hub()
            HomeLayout.RESUME -> p.resume()
            HomeLayout.CHAN -> p.chan()
            HomeLayout.CIRCUIT -> p.circuit()
            // Update 146 (the nearest previews).
            HomeLayout.LIBRARY -> p.grid()
            HomeLayout.STACKS, HomeLayout.CONSOLES -> p.shelf()
            HomeLayout.TIMELINE -> p.classic()
            HomeLayout.ARCADE -> p.tv()
        }
        p.statusBar(layout)
        if (showLegend) p.legend()
    }
}

/** Draws the mock screens, in fractions of the preview's width / height. */
private class PreviewPainter(
    val d: DrawScope, val accent: Color, val bg: Color, val surface: Color,
    val effect: FocusEffect, val k: Float,
    val art: com.shelf.launcher.data.CardArtType = com.shelf.launcher.data.CardArtType.COVER,
    /** Corner roundness against the default (Rounded = 1). */
    val cornerK: Float = 1f
) {
    val w = d.size.width
    val h = d.size.height
    private val px = w / 320f // one "dp" of a 320-wide mock screen

    /** Game cover colours: a different hue per card, like a row of real covers. */
    fun cover(i: Int): Color = Color.hsv(((i * 47 + 200) % 360).toFloat(), 0.55f, 0.78f)

    private fun rect(x: Float, y: Float, ww: Float, hh: Float, c: Color, r: Float = 3f) =
        d.drawRoundRect(c, Offset(x * w, y * h), Size(ww * w, hh * h), CornerRadius(r * px))

    private fun frame(x: Float, y: Float, ww: Float, hh: Float, c: Color, r: Float = 3f, width: Float = 1.6f) =
        d.drawRoundRect(c, Offset(x * w, y * h), Size(ww * w, hh * h), CornerRadius(r * px), style = Stroke(width * px))

    private fun dot(x: Float, y: Float, r: Float, c: Color) = d.drawCircle(c, r * px, Offset(x * w, y * h))

    /** A text line stand-in. */
    private fun line(x: Float, y: Float, ww: Float, c: Color = Color.White.copy(alpha = 0.85f), t: Float = 0.022f) = rect(x, y, ww, t, c, 2f)

    /** A cover with a picture-ish gradient; [on] = the game you're on (focus effect + outline). */
    fun card(x: Float, y: Float, ww: Float, hh: Float, i: Int, on: Boolean = false, r0: Float = 3f, ring: Color = accent) {
        val r = r0 * cornerK
        when (art) {
            com.shelf.launcher.data.CardArtType.DISC -> { disc(x, y, ww, hh, i, on, ring); return }
            com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC -> {
                if (i % 2 == 0) disc(x, y, ww, hh, i, on, ring) else cartridge(x, y, ww, hh, i, on, ring)
                return
            }
            else -> Unit
        }
        if (on && effect != FocusEffect.NONE) {
            for (g in 1..4) d.drawRoundRect(
                ring.copy(alpha = 0.16f - g * 0.03f), Offset(x * w - g * 2 * px, y * h - g * 2 * px),
                Size(ww * w + g * 4 * px, hh * h + g * 4 * px), CornerRadius((r + g * 2) * px)
            )
        }
        val c = cover(i)
        d.drawRoundRect(
            Brush.verticalGradient(listOf(c, c.copy(red = c.red * 0.55f, green = c.green * 0.55f, blue = c.blue * 0.6f)), y * h, (y + hh) * h),
            Offset(x * w, y * h), Size(ww * w, hh * h), CornerRadius(r * px)
        )
        d.drawCircle(Color.White.copy(alpha = 0.35f), minOf(ww * w, hh * h) * 0.16f, Offset((x + ww * 0.68f) * w, (y + hh * 0.3f) * h))
        if (art == com.shelf.launcher.data.CardArtType.CASE || art == com.shelf.launcher.data.CardArtType.CASE_REAL) {
            // The case's printed header band and its plastic edge.
            rect(x, y, ww, hh * 0.13f, Color(0xFF1C1C22).copy(alpha = 0.9f), r)
            frame(x, y, ww, hh, Color.White.copy(alpha = 0.35f), r, 1f)
        }
        if (on) {
            frame(x, y, ww, hh, ring, r, 2f)
            if (effect == FocusEffect.ELECTRIC) {
                val path = Path().apply {
                    moveTo(x * w, (y + hh * 0.2f) * h); lineTo((x - 0.006f) * w, (y + hh * 0.35f) * h); lineTo((x + 0.004f) * w, (y + hh * 0.45f) * h)
                    moveTo((x + ww) * w, (y + hh * 0.6f) * h); lineTo((x + ww + 0.007f) * w, (y + hh * 0.72f) * h); lineTo((x + ww - 0.003f) * w, (y + hh * 0.85f) * h)
                }
                d.drawPath(path, Color.White, style = Stroke(1.2f * px))
            }
        }
    }

    /** A disc in the card's slot (round, centred, with its hole and a shine). */
    private fun disc(x: Float, y: Float, ww: Float, hh: Float, i: Int, on: Boolean, ring: Color) {
        val rad = minOf(ww * w, hh * h) * 0.5f
        val c = Offset((x + ww / 2) * w, (y + hh / 2) * h)
        if (on && effect != FocusEffect.NONE) for (g in 1..4) d.drawCircle(ring.copy(alpha = 0.16f - g * 0.03f), rad + g * 2 * px, c)
        d.drawCircle(Brush.sweepGradient(listOf(Color(0xFFDADDE6), Color(0xFF9FA6B8), Color(0xFFE9ECF2), Color(0xFFB6BCCB), Color(0xFFDADDE6)), c), rad, c)
        d.drawCircle(cover(i), rad * 0.62f, c)
        d.drawCircle(Color(0xFFE6E8EE), rad * 0.2f, c)
        d.drawCircle(bg, rad * 0.08f, c)
        if (on) d.drawCircle(ring, rad, c, style = Stroke(2f * px))
    }

    /** A cartridge in the card's slot: a grey shell with a ridge on top and the game's label. */
    private fun cartridge(x: Float, y: Float, ww: Float, hh: Float, i: Int, on: Boolean, ring: Color) {
        val cw = minOf(ww, hh * h / w * 0.9f)
        val cx = x + (ww - cw) / 2
        val ch = hh * 0.92f
        val cy = y + (hh - ch) / 2
        if (on && effect != FocusEffect.NONE) for (g in 1..4) d.drawRoundRect(
            ring.copy(alpha = 0.16f - g * 0.03f), Offset(cx * w - g * 2 * px, cy * h - g * 2 * px),
            Size(cw * w + g * 4 * px, ch * h + g * 4 * px), CornerRadius((3 + g * 2) * px)
        )
        rect(cx, cy, cw, ch, Color(0xFF8E9099), 3f)
        rect(cx + cw * 0.1f, cy + ch * 0.04f, cw * 0.8f, ch * 0.06f, Color(0xFF6E7079), 1f)
        rect(cx + cw * 0.12f, cy + ch * 0.2f, cw * 0.76f, ch * 0.55f, cover(i), 2f)
        if (on) frame(cx, cy, cw, ch, ring, 3f, 2f)
    }

    fun background(layout: HomeLayout, style: BackgroundStyle) {
        when (layout) {
            HomeLayout.LIVE, HomeLayout.CHAN -> { d.drawRect(Brush.verticalGradient(listOf(Color(0xFFF2F2F2), Color(0xFFD8D8D8)))); return }
            HomeLayout.CROSS -> { d.drawRect(Brush.verticalGradient(listOf(Color(0xFF3B2350), Color(0xFFB59AD8)))); waves(); return }
            else -> Unit
        }
        val heroBehind = layout == HomeLayout.HERO || layout == HomeLayout.SIDE || layout == HomeLayout.SPOT || layout == HomeLayout.HUB || layout == HomeLayout.RESUME
        when {
            style == BackgroundStyle.BLACK && !heroBehind -> d.drawRect(Color(0xFF050608))
            style == BackgroundStyle.ART || style == BackgroundStyle.BLURRED || heroBehind -> heroArt(style == BackgroundStyle.BLURRED)
            else -> {
                d.drawRect(Brush.linearGradient(listOf(bg, surface, accent.copy(alpha = 0.35f)), Offset.Zero, Offset(w, h)))
                // The animated scene, hinted by soft lights.
                for (i in 0..5) dot(0.1f + i * 0.17f, 0.2f + (i % 3) * 0.22f, 10f + (i % 2) * 8f, accent.copy(alpha = 0.08f))
            }
        }
    }

    /** A game's hero art across the screen: sky, sun, hills. */
    private fun heroArt(blurred: Boolean) {
        val a = if (blurred) 0.6f else 1f
        d.drawRect(Brush.verticalGradient(listOf(Color(0xFF6E5AA8).copy(alpha = a), Color(0xFFE08A5A).copy(alpha = a), Color(0xFF2B1F33))))
        dot(0.74f, 0.3f, 26f, Color(0xFFFFD9A8).copy(alpha = 0.85f * a))
        val hill = Path().apply {
            moveTo(0f, h * 0.72f); cubicTo(w * 0.3f, h * 0.55f, w * 0.55f, h * 0.85f, w, h * 0.62f); lineTo(w, h); lineTo(0f, h); close()
        }
        d.drawPath(hill, Color(0xFF1E1626).copy(alpha = 0.9f))
    }

    private fun waves() {
        for (k in 0..2) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, h * (0.62f + k * 0.04f))
            while (x <= w) {
                path.lineTo(x, h * (0.62f + k * 0.04f) + kotlin.math.sin(x / w * 7f + k) * h * 0.04f)
                x += w / 40f
            }
            d.drawPath(path, Color.White.copy(alpha = 0.25f - k * 0.06f), style = Stroke(1.2f * px))
        }
    }

    fun statusBar(layout: HomeLayout) {
        val ink = if (layout == HomeLayout.LIVE) Color(0xFF333333) else Color.White
        for (i in 0..3) dot(0.74f + i * 0.045f, 0.06f, 2.2f, ink.copy(alpha = 0.75f))
        line(0.92f, 0.05f, 0.05f, ink.copy(alpha = 0.75f), 0.02f)
    }

    fun legend() {
        rect(0f, 0.93f, 1f, 0.07f, Color.Black.copy(alpha = 0.45f), 0f)
        for (i in 0..3) { dot(0.06f + i * 0.14f, 0.965f, 2.5f, Color.White.copy(alpha = 0.7f)); line(0.08f + i * 0.14f, 0.955f, 0.06f, Color.White.copy(alpha = 0.55f), 0.018f) }
    }

    fun classic() {
        val ch = 0.3f * k
        val y = 0.62f - (ch - 0.3f)
        line(0.05f, y - 0.1f, 0.25f, Color.White, 0.035f)
        card(0.05f, y, 0.3f * k, ch, 0, on = true)
        for (i in 1..5) card(0.07f + 0.3f * k + (i - 1) * 0.135f * k, y + 0.03f, 0.11f * k, ch - 0.03f, i)
    }

    fun tiles() {
        val s = 0.15f * k
        line(0.24f, 0.36f, 0.2f, Color.White, 0.035f)
        for (i in 0..4) card(0.06f + i * (s + 0.025f) + (if (i > 1) 0.03f else 0f), 0.44f - (if (i == 1) 0.03f else 0f), if (i == 1) s + 0.03f else s, (if (i == 1) s + 0.03f else s) * w / h, i, on = i == 1, r0 = 8f)
        for (i in 0..5) dot(0.3f + i * 0.08f, 0.9f, 4f, Color.White.copy(alpha = if (i == 0) 0.9f else 0.45f))
    }

    fun hero() {
        val cx = 0.2f
        for (i in -3..3) {
            val ad = kotlin.math.abs(i).toFloat()
            val sz = if (i == 0) 0.12f else 0.075f - ad * 0.006f
            card(cx - ad * 0.025f - sz / 2, 0.5f + i * 0.13f - sz * 0.7f, sz, sz * 1.4f * w / h * 0.56f, i + 3, on = i == 0)
        }
        rect(0.5f, 0.3f, 0.45f, 0.55f, Color.Black.copy(alpha = 0.45f), 8f)
        line(0.54f, 0.36f, 0.3f, Color.White, 0.04f)
        for (r in 0..2) line(0.54f, 0.47f + r * 0.07f, 0.36f - r * 0.05f, Color.White.copy(alpha = 0.6f))
        rect(0.54f, 0.72f, 0.36f, 0.03f, Color.White.copy(alpha = 0.2f), 2f); rect(0.54f, 0.72f, 0.16f, 0.03f, accent, 2f)
    }

    fun grid() {
        rect(0.04f, 0.13f, 0.18f, 0.06f, Color.Black.copy(alpha = 0.4f), 6f)
        val cw = 0.13f * k
        val ch = cw * 1.4f * w / h * 0.56f
        val cols = (0.92f / (cw + 0.03f)).toInt().coerceIn(3, 7)
        for (r in 0..2) for (c in 0 until cols) {
            val y = 0.24f + r * (ch + 0.07f)
            if (y > 0.98f) continue
            card(0.04f + c * (cw + 0.03f), y, cw, ch, r * cols + c, on = r == 0 && c == 0)
        }
    }

    fun tv() {
        rect(0.03f, 0.14f, 0.2f, 0.07f, accent.copy(alpha = 0.8f), 4f)
        rect(0.03f, 0.24f, 0.5f, 0.52f, Color(0xFF5A3A22), 10f)
        rect(0.06f, 0.28f, 0.36f, 0.42f, Color(0xFF9FD4FF).copy(alpha = 0.85f), 14f)
        for (i in 0..1) dot(0.47f, 0.36f + i * 0.12f, 5f, Color(0xFF2B1B10))
        for (r in 0..1) for (c in 0..2) card(0.58f + c * 0.13f, 0.16f + r * 0.32f, 0.1f, 0.26f, r * 3 + c, on = r == 0 && c == 0, r0 = 2f)
        rect(0.03f, 0.82f, 0.94f, 0.12f, Color.Black.copy(alpha = 0.5f), 6f)
        line(0.06f, 0.86f, 0.3f, Color.White)
    }

    fun dash() {
        line(0.03f, 0.13f, 0.14f, Color.White, 0.04f); line(0.03f, 0.19f, 0.09f, accent, 0.02f)
        for (i in 0..5) {
            val y = 0.27f + i * 0.11f
            if (i == 0) rect(0f, y - 0.045f, 0.2f, 0.09f, accent.copy(alpha = 0.55f), 0f)
            dot(0.05f, y, 7f, if (i == 0) accent else Color.White.copy(alpha = 0.9f))
            line(0.09f, y - 0.012f, 0.08f, Color.White.copy(alpha = 0.85f))
        }
        val glass = Color.White.copy(alpha = 0.55f)
        rect(0.21f, 0.12f, 0.52f, 0.43f, glass, 6f)
        d.drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF16233A), Color(0xFF6E5AA8), Color(0xFFE08A5A))),
            Offset(0.225f * w, 0.17f * h), Size(0.49f * w, 0.35f * h), CornerRadius(4 * px)
        )
        line(0.24f, 0.33f, 0.24f, Color.White, 0.04f)
        rect(0.24f, 0.42f, 0.11f, 0.06f, accent, 2f)
        rect(0.21f, 0.58f, 0.52f, 0.24f, glass, 6f)
        for (i in 0..4) card(0.23f + i * 0.095f, 0.62f, 0.075f, 0.17f, i, on = i == 0)
        rect(0.21f, 0.85f, 0.52f, 0.1f, glass, 6f)
        for (i in 0..4) rect(0.225f + i * 0.1f, 0.87f, 0.09f, 0.06f, if (i == 0) accent else Color(0xFF243140).copy(alpha = 0.15f), 2f)
        rect(0.75f, 0.12f, 0.22f, 0.3f, glass, 6f)
        dot(0.8f, 0.2f, 7f, Color(0xFF243140).copy(alpha = 0.3f)); line(0.84f, 0.18f, 0.1f, Color(0xFF243140))
        rect(0.77f, 0.3f, 0.18f, 0.025f, Color(0xFF6CCB3E), 2f); rect(0.77f, 0.35f, 0.14f, 0.025f, Color(0xFF4AA8E8), 2f)
        rect(0.75f, 0.45f, 0.22f, 0.5f, glass, 6f)
        for (r in 0..3) line(0.78f, 0.52f + r * 0.1f, 0.16f, Color(0xFF243140).copy(alpha = 0.7f), 0.02f)
    }

    fun live() {
        val ink = Color(0xFF2B2B2B)
        line(0.05f, 0.13f, 0.1f, ink.copy(alpha = 0.35f), 0.05f)
        line(0.18f, 0.13f, 0.12f, ink, 0.05f)
        for (i in 0..2) line(0.34f + i * 0.14f, 0.13f, 0.1f, ink.copy(alpha = 0.35f), 0.05f)
        card(0.05f, 0.24f, 0.42f, 0.45f, 0, on = false, r0 = 0f)
        frame(0.05f, 0.24f, 0.42f, 0.45f, ink, 0f, 2.5f)
        for (r in 0..1) for (c in 0..1) card(0.5f + c * 0.23f, 0.24f + r * 0.235f, 0.21f, 0.215f, 1 + r * 2 + c, r0 = 0f)
        for (i in 0..3) rect(0.05f + i * 0.235f, 0.73f, 0.21f, 0.14f, if (i == 0) Color(0xFF107C10) else Color(0xFF2E2E2E), 0f)
        line(0.05f, 0.9f, 0.3f, ink.copy(alpha = 0.7f), 0.03f)
    }

    fun side() {
        val gold = Color(0xFFD9B45A)
        rect(0f, 0f, 0.24f, 1f, Color(0xF00A0D14), 0f)
        dot(0.05f, 0.13f, 8f, gold.copy(alpha = 0.7f)); line(0.09f, 0.12f, 0.1f, gold, 0.02f)
        for (i in 0..4) {
            val y = 0.25f + i * 0.09f
            if (i == 0) frame(0.015f, y - 0.03f, 0.21f, 0.07f, gold, 2f, 1.2f)
            line(0.04f, y, 0.13f, if (i == 0) gold else Color.White.copy(alpha = 0.7f), 0.02f)
        }
        d.drawCircle(Brush.radialGradient(listOf(Color(0xFF6FC8FF), Color(0xFF0B2240)), Offset(0.12f * w, 0.8f * h), 18 * px), 16 * px, Offset(0.12f * w, 0.8f * h))
        line(0.28f, 0.6f, 0.2f, Color.White, 0.03f)
        for (i in 0..5) card(0.28f + i * 0.115f, 0.67f, 0.095f, 0.26f, i, on = i == 0, r0 = 2f, ring = gold)
    }

    fun cross() {
        for (i in -1..4) {
            val on = i == 0
            dot(0.22f + i * 0.13f, 0.24f, if (on) 11f else 7f, Color.White.copy(alpha = if (on) 1f else 0.6f))
        }
        line(0.15f, 0.36f, 0.14f, Color.White, 0.03f)
        card(0.16f, 0.44f, 0.22f, 0.2f, 0, on = true, r0 = 2f, ring = Color.White)
        line(0.41f, 0.47f, 0.3f, Color.White, 0.04f); line(0.41f, 0.54f, 0.14f, Color.White.copy(alpha = 0.7f))
        for (r in 0..1) { card(0.19f, 0.7f + r * 0.15f, 0.15f, 0.12f, r + 1, r0 = 2f); line(0.41f, 0.75f + r * 0.15f, 0.18f, Color.White.copy(alpha = 0.75f)) }
    }

    fun spot() {
        d.drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))
        line(0.05f, 0.11f, 0.08f, Color.White, 0.03f); line(0.17f, 0.11f, 0.07f, Color.White.copy(alpha = 0.5f), 0.03f)
        card(0.05f, 0.18f, 0.1f, 0.18f, 0, on = true, r0 = 5f, ring = Color.White)
        line(0.17f, 0.25f, 0.14f, Color.White, 0.03f)
        for (i in 1..5) card(0.33f + (i - 1) * 0.085f, 0.2f, 0.07f, 0.125f, i, r0 = 4f)
        line(0.05f, 0.5f, 0.42f, Color.White, 0.08f)
        rect(0.05f, 0.64f, 0.14f, 0.08f, Color(0xFFE9E9EC), 20f)
        dot(0.23f, 0.68f, 6f, Color(0xFF3A3D44))
        for (i in 0..2) rect(0.05f + i * 0.2f, 0.78f, 0.18f, 0.15f, Color(0xFF1C1F25).copy(alpha = 0.9f), 5f)
    }

    fun bar() {
        for (i in 0..6) rect(0.27f + i * 0.068f, 0.13f, 0.045f, 0.08f, if (i == 2) accent else Color.White.copy(alpha = 0.15f), 3f)
        card(0.05f, 0.32f, 0.2f * k, 0.36f * k, 0, on = true, r0 = 2f, ring = Color.White)
        for (i in 1..6) card(0.07f + 0.2f * k + (i - 1) * 0.12f, 0.32f, 0.11f, 0.2f, i, r0 = 2f)
        line(0.05f, 0.33f + 0.36f * k + 0.04f, 0.25f, Color.White, 0.035f)
        frame(0.05f, 0.33f + 0.36f * k + 0.11f, 0.1f, 0.07f, Color.White, 2f, 1.2f)
    }

    /** Console hub: profile and clock on top, tiles with the first one big, three wide cards under them. */
    fun hub() {
        dot(0.07f, 0.1f, 6f, Color.White.copy(alpha = 0.6f))
        for (i in 0..3) dot(0.4f + i * 0.07f, 0.1f, 4f, Color.White.copy(alpha = 0.5f))
        card(0.05f, 0.4f, 0.17f * k, 0.3f * k, 0, on = true, r0 = 2f, ring = Color.White)
        for (i in 1..7) card(0.07f + 0.17f * k + (i - 1) * 0.1f, 0.4f, 0.085f, 0.15f, i, r0 = 2f)
        for (i in 0..2) rect(0.05f + i * 0.31f, 0.76f, 0.29f, 0.14f, Color.Black.copy(alpha = 0.55f), 2f)
    }

    /** Resume and discover: a big square, a 2x2 block under it, a row of games and wide cards beside them. */
    fun resume() {
        line(0.05f, 0.1f, 0.12f, Color.White, 0.04f)
        card(0.05f, 0.18f, 0.2f, 0.36f, 0, on = true, r0 = 2f, ring = Color.White)
        for (i in 0..3) rect(0.05f + (i % 2) * 0.102f, 0.58f + (i / 2) * 0.12f, 0.098f, 0.11f, Color.White.copy(alpha = 0.22f), 1f)
        for (i in 1..6) card(0.29f + (i - 1) * 0.11f, 0.18f, 0.1f, 0.18f, i, r0 = 1f)
        line(0.29f, 0.45f, 0.1f, Color.White, 0.035f)
        for (i in 0..2) rect(0.29f + i * 0.23f, 0.54f, 0.21f, 0.24f, Color.Black.copy(alpha = 0.55f), 2f)
    }

    /** Channel grid: three rows of wide tiles, a curved bar with a clock. */
    fun chan() {
        for (r in 0..2) for (c in 0..3) {
            val on = r == 0 && c == 1
            rect(0.05f + c * 0.23f, 0.07f + r * 0.2f, 0.21f, 0.17f, cover(r * 4 + c), 5f)
            if (on) frame(0.05f + c * 0.23f, 0.07f + r * 0.2f, 0.21f, 0.17f, accent, 5f, 2.4f)
        }
        rect(0.0f, 0.74f, 1f, 0.3f, Color.White.copy(alpha = 0.9f), 14f)
        dot(0.1f, 0.88f, 12f, Color.White)
        dot(0.9f, 0.88f, 12f, Color.White)
        rect(0.42f, 0.8f, 0.16f, 0.1f, Color(0xFF8A94A0), 2f)
    }

    /** Circuit board: chips zig-zagging along a glowing line, one lit, a readout and a switch. */
    fun circuit() {
        line(0.04f, 0.5f, 0.92f, accent, 0.012f)
        for (i in 0..7) {
            val x = 0.08f + i * 0.11f
            val up = i % 2 == 0
            rect(x - 0.005f, if (up) 0.42f else 0.5f, 0.01f, 0.08f, accent.copy(alpha = 0.6f), 0f)
            card(x - 0.07f, if (up) 0.16f else 0.58f, 0.14f, 0.26f, i, on = i == 2, r0 = 2f)
        }
        rect(0.04f, 0.88f, 0.34f, 0.08f, Color(0xFF0D1410), 3f)
        rect(0.78f, 0.88f, 0.17f, 0.07f, accent.copy(alpha = 0.7f), 10f)
    }

    /** Game shelf: two shelves of spines, one game pulled out showing its front. */
    fun shelf() {
        line(0.05f, 0.12f, 0.18f, Color.White, 0.035f)
        for (r in 0..1) {
            val y = 0.24f + r * 0.32f
            for (i in 0..22) rect(0.06f + i * 0.039f, y, 0.033f, 0.26f, cover(i + r * 7).copy(alpha = 0.9f), 1f)
            rect(0.04f, y + 0.26f, 0.92f, 0.025f, Color.White.copy(alpha = 0.35f), 1f)
        }
        card(0.36f, 0.14f, 0.16f, 0.36f, 3, on = true, r0 = 2f)
        line(0.05f, 0.88f, 0.22f, Color.White, 0.03f)
    }
}
