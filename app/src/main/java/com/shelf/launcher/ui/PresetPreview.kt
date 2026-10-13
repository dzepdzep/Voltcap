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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.BackgroundStyle
import com.shelf.launcher.data.Features
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
    corners: com.shelf.launcher.data.CardCorners = com.shelf.launcher.data.CardCorners.ROUNDED,
    /** Update 162: the game look's switches (Tile and Spines with covers, Cartridges and 3D models with cartridges & discs). */
    tiles: Boolean = false,
    spines: Boolean = false,
    carts: Boolean = false,
    models: Boolean = false,
    /** Update 162: the carousel's view when the layout shows its games as the carousel; null = its own row / grid. */
    carousel: CarouselView? = null,
    /** Update 162: the layout options the preset sets (Features key -> value); the others are drawn as they are now. */
    options: Map<String, String> = emptyMap()
) {
    val accent = ShelfTheme.accent
    val bg = ShelfTheme.colors.background
    val surface = ShelfTheme.colors.surface
    Canvas(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(uiRounded(12.dp))) {
        val p = PreviewPainter(this, accent, bg, surface, focusEffect, cardScale, art, corners.radiusDp / 10f, tiles, spines, carts, models, options)
        p.background(layout, background)
        // Update 162: every layout drawn as itself (the newer ones had the nearest older preview), the carousel ones
        // with their carousel view when it is on.
        when (layout) {
            HomeLayout.CLASSIC -> if (carousel != null) p.carouselHome(carousel) else p.classic()
            HomeLayout.TILES -> p.tiles()
            HomeLayout.HERO -> p.hero()
            HomeLayout.GRID -> if (carousel != null) p.carouselHome(carousel) else p.grid()
            HomeLayout.TV -> p.tv(carousel)
            HomeLayout.DASH -> p.dash()
            HomeLayout.LIVE -> p.live()
            HomeLayout.SIDE -> p.side(carousel)
            HomeLayout.CROSS -> p.cross()
            HomeLayout.SPOT -> p.spot()
            HomeLayout.BAR -> p.bar()
            HomeLayout.SHELF -> p.shelf()
            HomeLayout.HUB -> p.hub()
            HomeLayout.RESUME -> p.resume()
            HomeLayout.CHAN -> p.chan()
            HomeLayout.CIRCUIT -> p.circuit()
            HomeLayout.LIBRARY -> p.library()
            HomeLayout.STACKS -> p.stacks(carousel)
            HomeLayout.TIMELINE -> p.timeline()
            HomeLayout.ARCADE -> p.arcade()
            HomeLayout.CONSOLES -> p.consoles()
            HomeLayout.DISCS -> p.discStation()
            HomeLayout.HERO_TILES -> p.heroTiles()
            HomeLayout.TWIN_DESK -> p.twinDesk()
            HomeLayout.TWIN_ICONS -> p.twinIcons()
            HomeLayout.CRATE -> p.crate()
            HomeLayout.CARDS -> p.binder()
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
    val cornerK: Float = 1f,
    /** Update 162: the game look's switches and the preset's layout options. */
    val tiles: Boolean = false,
    val spines: Boolean = false,
    val carts: Boolean = false,
    val models: Boolean = false,
    val options: Map<String, String> = emptyMap()
) {
    val w = d.size.width
    val h = d.size.height
    private val px = w / 320f // one "dp" of a 320-wide mock screen

    // Update 162: a layout option as the preset sets it, else as it is now (read while drawing).
    private fun ob(p: Features.BoolPref): Boolean = options[p.key]?.let { it == "true" } ?: p.value
    private fun oi(p: Features.IntPref): Int = options[p.key]?.toIntOrNull() ?: p.value
    private fun <E : Enum<E>> oe(p: Features.EnumPref<E>): String = options[p.key] ?: p.value.name

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
    fun card(
        x: Float, y: Float, ww: Float, hh: Float, i: Int, on: Boolean = false, r0: Float = 3f, ring: Color = accent,
        /** Update 162: the cover whatever the look (layouts that draw their own square tiles, sleeves, cards…). */
        plainCover: Boolean = false
    ) {
        // Update 162: the game look's switches — Cartridges / 3D models every game as a cartridge, Spines a slim spine
        // beside the game you're on, Tile rounder squares.
        val look = if (plainCover) com.shelf.launcher.data.CardArtType.COVER else this.art
        if (look == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC && (carts || models)) { cartridge(x, y, ww, hh, i, on, ring, models); return }
        if (look == com.shelf.launcher.data.CardArtType.COVER && spines && !on && !plainCover) { spine(x, y, ww, hh, i); return }
        val r = (if (look == com.shelf.launcher.data.CardArtType.COVER && tiles && !plainCover) maxOf(r0, 8f) else r0) * cornerK
        when (look) {
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
        if (look == com.shelf.launcher.data.CardArtType.CASE || look == com.shelf.launcher.data.CardArtType.CASE_REAL) {
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

    /** Update 162: a game as its spine (the Spines look): a slim strip in its colour with the name along it. */
    private fun spine(x: Float, y: Float, ww: Float, hh: Float, i: Int) {
        val sw = ww * 0.32f
        val sx = x + (ww - sw) / 2
        val c = cover(i)
        rect(sx, y, sw, hh, c.copy(red = c.red * 0.7f, green = c.green * 0.7f, blue = c.blue * 0.75f), 1f)
        rect(sx + sw * 0.35f, y + hh * 0.18f, sw * 0.3f, hh * 0.6f, Color.White.copy(alpha = 0.55f), 1f)
        rect(sx, y, sw, hh * 0.1f, Color(0xFF1C1C22).copy(alpha = 0.85f), 1f)
    }

    /** A cartridge in the card's slot: a grey shell with a ridge on top and the game's label ([solid]: a 3D model, its side showing). */
    private fun cartridge(x: Float, y: Float, ww: Float, hh: Float, i: Int, on: Boolean, ring: Color, solid: Boolean = false) {
        val cw = minOf(ww, hh * h / w * 0.9f)
        val cx = x + (ww - cw) / 2
        val ch = hh * 0.92f
        val cy = y + (hh - ch) / 2
        // Update 162: the 3D model's depth, its side and bottom edge showing.
        if (solid) rect(cx + cw * 0.08f, cy + ch * 0.04f, cw, ch, Color(0xFF55575F), 3f)
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
            // Update 162: Hero tiles' pale backdrop.
            HomeLayout.HERO_TILES -> if (ob(Features.heroTilesPale)) { d.drawRect(Brush.verticalGradient(listOf(Color(0xFFF4F5F7), Color(0xFFDDE0E5)))); return }
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
        // (Update 162: dark on Hero tiles' pale backdrop too.)
        val ink = if (layout == HomeLayout.LIVE || (layout == HomeLayout.HERO_TILES && ob(Features.heroTilesPale))) Color(0xFF333333) else Color.White
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

    fun tv(view: CarouselView? = null) {
        rect(0.03f, 0.14f, 0.2f, 0.07f, accent.copy(alpha = 0.8f), 4f)
        rect(0.03f, 0.24f, 0.5f, 0.52f, Color(0xFF5A3A22), 10f)
        rect(0.06f, 0.28f, 0.36f, 0.42f, Color(0xFF9FD4FF).copy(alpha = 0.85f), 14f)
        for (i in 0..1) dot(0.47f, 0.36f + i * 0.12f, 5f, Color(0xFF2B1B10))
        // Update 162: the games beside the TV as the carousel, or as the grid / shelf (Games beside the TV).
        val games = oe(Features.tvRoomGames)
        if (view != null) {
            d.clipRect(0.55f * w, 0f, w, h) { carousel(view, 0.77f, 0.46f, 0.11f) }
        } else if (games == TvRoom.Games.SHELF.name) {
            for (i in 0..9) rect(0.57f + i * 0.04f, 0.2f, 0.034f, 0.5f, cover(i).copy(alpha = 0.9f), 1f)
        } else {
            for (r in 0..1) for (c in 0..2) card(0.58f + c * 0.13f, 0.16f + r * 0.32f, 0.1f, 0.26f, r * 3 + c, on = r == 0 && c == 0, r0 = 2f)
        }
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

    fun side(view: CarouselView? = null) {
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
        // Update 162: the row along the bottom, or the carousel there.
        if (view != null) {
            d.clipRect(0.24f * w, 0f, w, h) { carousel(view, 0.62f, 0.8f, 0.09f) }
        } else {
            for (i in 0..5) card(0.28f + i * 0.115f, 0.67f, 0.095f, 0.26f, i, on = i == 0, r0 = 2f, ring = gold)
        }
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
        // Update 162: how the games stand (spines / fronts / turned) and the shelves' wood.
        val stand = oe(Features.shelfStand)
        val board = when (oe(Features.shelfLook)) {
            Features.ShelfLook.WOOD.name -> Color(0xFFA0703F)
            Features.ShelfLook.GLASS.name -> Color(0xFFCFE6F2).copy(alpha = 0.55f)
            else -> Color.White.copy(alpha = 0.35f)
        }
        line(0.05f, 0.12f, 0.18f, Color.White, 0.035f)
        for (r in 0..1) {
            val y = 0.24f + r * 0.32f
            when (stand) {
                Features.ShelfStand.FRONTS.name -> for (i in 0..7) card(0.06f + i * 0.115f, y, 0.1f, 0.26f, i + r * 7, r0 = 1f, plainCover = true)
                Features.ShelfStand.ANGLED.name -> for (i in 0..14) {
                    val x = 0.06f + i * 0.06f
                    rect(x, y, 0.014f, 0.26f, cover(i + r * 7).copy(alpha = 0.7f), 1f)
                    rect(x + 0.014f, y, 0.038f, 0.26f, cover(i + r * 7), 1f)
                }
                else -> for (i in 0..22) rect(0.06f + i * 0.039f, y, 0.033f, 0.26f, cover(i + r * 7).copy(alpha = 0.9f), 1f)
            }
            rect(0.04f, y + 0.26f, 0.92f, 0.025f, board, 1f)
        }
        card(0.36f, 0.14f, 0.16f, 0.36f, 3, on = true, r0 = 2f)
        line(0.05f, 0.88f, 0.22f, Color.White, 0.03f)
    }

    // ---- Update 162: the carousel views and the newer layouts, each drawn as itself ----

    /** The Voltcap row / Grid as the carousel: the category's name, then the carousel across the screen. */
    fun carouselHome(view: CarouselView) {
        line(0.05f, 0.13f, 0.22f, Color.White, 0.035f)
        carousel(view, 0.5f, 0.55f, 0.15f * k)
        line(0.4f, 0.86f, 0.2f, Color.White, 0.035f)
    }

    /**
     * The games as the carousel in [view] (Carousel.place, the same places the home screen uses), the middle one [bw]
     * wide at [cx], [cy]: the far ones first, turned ones narrower, the 3D-case views with their spines showing.
     */
    fun carousel(view: CarouselView, cx: Float, cy: Float, bw: Float) {
        val bh = (bw * w / h * 1.35f).coerceAtMost(0.62f)
        // Update 162 (review): back to front by the carousel's own drawing order (Place.z), as the home screen draws
        // them — the packed stacks of Box flow and Spine wall need it, or the inner case's front hides the next spine.
        val order = (-8..8).map { r -> r to Carousel.place(r.toFloat(), Carousel.EDGE_SIDE, view) }.sortedBy { it.second.z }
        for ((r, pl) in order) placed(view, pl, r.toFloat(), cx, cy, bw, bh)
    }

    private fun placed(view: CarouselView, pl: Carousel.Place, rel: Float, cx0: Float, cy0: Float, bw: Float, bh: Float) {
        if (pl.alpha <= 0.03f) return
        val sw = bw * pl.scale
        val sh = bh * pl.scale
        val cx = cx0 + pl.x * bw
        if (cx + sw < -0.05f || cx - sw > 1.05f) return
        val cy = cy0 + pl.y * bh
        val turn = kotlin.math.abs(pl.turnY).coerceAtMost(82f)
        val rad = Math.toRadians(turn.toDouble())
        val fw = sw * kotlin.math.cos(rad).toFloat().coerceAtLeast(0.06f)
        val i = rel.toInt() + 9
        val on = rel == 0f
        d.rotate(pl.turnZ, Offset(cx * w, cy * h)) {
            if (view.cases && turn > 3f) {
                // The case's spine beside its front: turned with a positive turnY (Carousel) its left side, the spine,
                // comes round toward you (Update 162 (review): by the turn's sign — in Box flow and Spine wall both stacks
                // show their spines on the left, not toward the middle).
                val spw = sw * 0.2f * kotlin.math.sin(rad).toFloat()
                val x0 = cx - (fw + spw) / 2
                val c = cover(i)
                if (pl.turnY > 0f) {
                    rect(x0, cy - sh / 2, spw, sh, c.copy(red = c.red * 0.45f, green = c.green * 0.45f, blue = c.blue * 0.5f), 1f)
                    card(x0 + spw, cy - sh / 2, fw, sh, i, r0 = 1f)
                } else {
                    card(x0, cy - sh / 2, fw, sh, i, r0 = 1f)
                    rect(x0 + fw, cy - sh / 2, spw, sh, c.copy(red = c.red * 0.45f, green = c.green * 0.45f, blue = c.blue * 0.5f), 1f)
                }
            } else card(cx - fw / 2, cy - sh / 2, fw, sh, i, on = on, r0 = if (view.cases) 1f else 3f)
            val dark = maxOf(pl.shade, 1f - pl.alpha)
            if (dark > 0.02f) rect(cx - fw / 2, cy - sh / 2, fw, sh, Color.Black.copy(alpha = dark.coerceAtMost(0.8f)), 1f)
        }
        // The glossy floor under the 3D-case views (as their dark stage).
        if (on && view.cases) rect(cx - sw * 2.5f, cy + sh / 2 + 0.01f, sw * 5f, 0.02f, Color.White.copy(alpha = 0.1f), 2f)
    }

    /** Library panel: tabs on top, the game panel on the left (cover, name, Play / Details), a grid of games on the right. */
    fun library() {
        val panel = ob(Features.libPanel)
        val cols = oi(Features.libCols).coerceIn(3, 8)
        for (i in 0..4) rect(0.04f + i * 0.13f, 0.12f, 0.11f, 0.05f, if (i == 0) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f), 4f)
        val gx = if (panel) 0.33f else 0.04f
        if (panel) {
            rect(0.04f, 0.21f, 0.26f, 0.73f, Color.Black.copy(alpha = 0.45f), 6f)
            card(0.07f, 0.24f, 0.2f, 0.3f, 0)
            line(0.07f, 0.58f, 0.17f, Color.White, 0.03f)
            for (r in 0..1) line(0.07f, 0.64f + r * 0.05f, 0.14f - r * 0.03f, Color.White.copy(alpha = 0.55f))
            rect(0.07f, 0.8f, 0.09f, 0.07f, accent, 4f)
            rect(0.175f, 0.8f, 0.09f, 0.07f, Color.White.copy(alpha = 0.2f), 4f)
        }
        val cw = (0.96f - gx) / cols - 0.015f
        val ch = cw * w / h * 1.3f
        for (r in 0..3) for (c in 0 until cols) {
            val y = 0.21f + r * (ch + 0.03f)
            if (y + ch > 0.98f) continue
            card(gx + c * (cw + 0.015f), y, cw, ch, r * cols + c, on = r == 0 && c == 1)
        }
    }

    /** Case stacks: the tool strip, the case in the middle with stacks of spines either side (or the carousel), the panel. */
    fun stacks(view: CarouselView?) {
        val cy = 0.47f
        if (ob(Features.stacksToolbar)) for (i in 0..5) rect(0.3f + i * 0.07f, 0.09f, 0.05f, 0.06f, Color.White.copy(alpha = if (i == 0) 0.7f else 0.25f), 2f)
        if (view != null) carousel(view, 0.5f, cy, 0.14f)
        else {
            val depth = ob(Features.stacksDepth)
            for (side in 0..1) for (j in 0..9) {
                val t = j / 9f
                val sh = if (depth) 0.42f - t * 0.16f else 0.42f
                val x = if (side == 0) 0.36f - j * 0.034f else 0.612f + j * 0.034f
                rect(x, cy - sh / 2 - (if (depth) t * 0.04f else 0f), 0.028f, sh, cover(j + side * 10).copy(alpha = 0.9f), 1f)
            }
            card(0.405f, cy - 0.23f, 0.19f, 0.46f, 0, on = true, r0 = 2f)
            if (ob(Features.stacks3d)) rect(0.595f, cy - 0.22f, 0.014f, 0.44f, Color.Black.copy(alpha = 0.35f), 1f)
        }
        if (ob(Features.stacksMirror)) rect(0.05f, cy + 0.25f, 0.9f, 0.05f, Color.White.copy(alpha = 0.08f), 2f)
        if (ob(Features.stacksBar)) {
            rect(0f, 0.8f, 1f, 0.2f, Color.Black.copy(alpha = 0.5f), 0f)
            line(0.05f, 0.85f, 0.25f, Color.White, 0.035f)
            line(0.42f, 0.84f, 0.16f, Color.White, 0.06f)
            for (i in 0..3) rect(0.62f + i * 0.085f, 0.87f, 0.075f, 0.06f, if (i == 3) accent else Color.White.copy(alpha = 0.2f), 3f)
        }
    }

    /** Timeline: a time line down the left, year headings, a row of that time's games each (the first one bigger). */
    fun timeline() {
        line(0.05f, 0.1f, 0.3f, Color.White, 0.035f)
        rect(0.07f, 0.2f, 0.006f, 0.76f, accent.copy(alpha = 0.7f), 1f)
        for (r in 0..2) {
            val y = 0.22f + r * 0.26f
            dot(0.073f, y + 0.02f, 4f, accent)
            line(0.1f, y, 0.07f, Color.White, 0.04f)
            val cw = if (r == 0) 0.1f else 0.075f
            val ch = minOf(cw * w / h * 1.3f, 0.22f)
            for (c in 0..8) {
                val x = 0.2f + c * (cw + 0.015f)
                if (x + cw > 0.98f) break
                card(x, y - 0.02f, cw, ch, r * 9 + c, on = r == 0 && c == 0)
            }
            if (ob(Features.timelineDates) && r == 0) line(0.2f, y - 0.02f + ch + 0.02f, 0.07f, Color.White.copy(alpha = 0.6f), 0.018f)
        }
    }

    /** Arcade cabinets: a big cabinet with its lit marquee and the game on its screen, small cabinets along the bottom. */
    fun arcade() {
        val glow = ob(Features.arcadeGlow)
        val gameColors = ob(Features.arcadeGameColors)
        val body = if (gameColors) Color.hsv(((2 * 47 + 200) % 360).toFloat(), 0.6f, 0.35f) else surface
        if (glow) rect(0.04f, 0.05f, 0.38f, 0.14f, accent.copy(alpha = 0.22f), 8f)
        rect(0.06f, 0.1f, 0.34f, 0.7f, body, 6f)
        rect(0.08f, 0.1f, 0.3f, 0.08f, if (glow) accent else accent.copy(alpha = 0.45f), 3f)
        card(0.1f, 0.22f, 0.26f, 0.32f, 0, r0 = 2f, plainCover = true)
        if (ob(Features.arcadeLines)) for (j in 0..9) rect(0.1f, 0.225f + j * 0.032f, 0.26f, 0.004f, Color.Black.copy(alpha = 0.3f), 0f)
        rect(0.09f, 0.58f, 0.28f, 0.08f, Color.Black.copy(alpha = 0.4f), 2f)
        dot(0.15f, 0.62f, 4f, Color(0xFFE04848)); dot(0.27f, 0.62f, 3.5f, Color(0xFF48A0E0)); dot(0.31f, 0.62f, 3.5f, Color(0xFFE0C048))
        line(0.46f, 0.2f, 0.3f, Color.White, 0.045f)
        for (r in 0..2) line(0.46f, 0.3f + r * 0.06f, 0.24f - r * 0.04f, Color.White.copy(alpha = 0.6f))
        rect(0.46f, 0.52f, 0.12f, 0.07f, accent, 4f)
        for (i in 0..7) {
            val x = 0.06f + i * 0.115f
            rect(x, 0.84f, 0.09f, 0.14f, if (gameColors) Color.hsv(((i * 47 + 200) % 360).toFloat(), 0.55f, 0.4f) else surface, 2f)
            rect(x + 0.012f, 0.865f, 0.066f, 0.05f, cover(i), 1f)
            if (i == 0) frame(x, 0.84f, 0.09f, 0.14f, accent, 2f, 1.6f)
        }
    }

    /** Console stack: consoles piled down the left, the one you're in pulled out and lit, its games fanned out of it. */
    fun consoles() {
        val photos = ob(Features.consolePhotos)
        val n = oi(Features.consoleStackGames).coerceIn(3, 9)
        for (i in 0..5) {
            val y = 0.14f + i * 0.13f
            val on = i == 2
            val x = if (on) 0.09f else 0.04f
            rect(x, y, 0.26f, 0.1f, if (photos) Color(0xFF2A2D35) else Color.hsv(((i * 61 + 20) % 360).toFloat(), 0.45f, 0.55f), 3f)
            rect(x + 0.02f, y + 0.035f, 0.1f, 0.03f, Color.White.copy(alpha = if (photos) 0.35f else 0.7f), 1f)
            if (photos) rect(x + 0.17f, y + 0.02f, 0.07f, 0.06f, Color(0xFF15171C), 1f)
            if (on) frame(x, y, 0.26f, 0.1f, accent, 3f, 1.8f)
        }
        val mid = n / 2
        for (j in 0 until n) {
            val big = j == mid
            val cw = if (big) 0.1f else 0.07f
            val x = 0.4f + j * (0.52f / n)
            val y = 0.4f + kotlin.math.abs(j - mid) * 0.025f - (if (big) 0.04f else 0f)
            card(x, y, cw, cw * w / h * 1.3f, j, on = big, r0 = 2f)
        }
    }

    /** Disc station: the player with the disc in its tray (or the disc on the game's art), the game's details beside it. */
    fun discStation() {
        val onArt = oe(Features.discPlayer) == Features.DiscPlayerLook.ART.name
        if (onArt) d.drawRoundRect(
            Brush.linearGradient(listOf(cover(3), Color(0xFF1B1530)), Offset(0.04f * w, 0.1f * h), Offset(0.54f * w, 0.92f * h)),
            Offset(0.04f * w, 0.1f * h), Size(0.5f * w, 0.82f * h), CornerRadius(12 * px)
        ) else {
            rect(0.04f, 0.1f, 0.5f, 0.82f, Color(0xFFD7DAE0), 14f)
            rect(0.07f, 0.84f, 0.12f, 0.04f, Color(0xFF9CA2AE), 4f)
            dot(0.48f, 0.86f, 4f, accent)
        }
        disc(0.12f, 0.15f, 0.34f, 0.64f, 0, false, accent)
        if (ob(Features.discSpin)) d.drawArc(
            Color.White.copy(alpha = 0.7f), -70f, 50f, false, Offset(0.13f * w, 0.17f * h),
            Size(0.32f * w, 0.6f * h), style = Stroke(1.6f * px)
        )
        line(0.6f, 0.18f, 0.3f, Color.White, 0.05f)
        line(0.6f, 0.27f, 0.14f, accent, 0.025f)
        for (r in 0..1) rect(0.6f + r * 0.17f, 0.34f, 0.15f, 0.1f, Color.Black.copy(alpha = 0.4f), 4f)
        if (ob(Features.discAbout)) for (r in 0..2) line(0.6f, 0.52f + r * 0.06f, 0.34f - r * 0.06f, Color.White.copy(alpha = 0.55f))
        rect(0.6f, 0.8f, 0.14f, 0.08f, accent, 5f)
        rect(0.76f, 0.8f, 0.14f, 0.08f, Color.White.copy(alpha = 0.2f), 5f)
    }

    /** Hero tiles: the game's big picture across the top, one or two rows of square tiles, the dock and round buttons. */
    fun heroTiles() {
        val rows = oi(Features.heroTilesRows).coerceIn(1, 2)
        val size = oi(Features.heroTilesSize).coerceIn(50, 100) / 100f
        val pale = ob(Features.heroTilesPale)
        d.drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF6E5AA8), Color(0xFFE08A5A)), Offset(0.04f * w, 0.08f * h), Offset(0.96f * w, 0.44f * h)),
            Offset(0.04f * w, 0.08f * h), Size(0.92f * w, 0.36f * h), CornerRadius(8 * px)
        )
        line(0.07f, 0.34f, 0.25f, Color.White, 0.045f)
        val tile = (if (rows == 1) 0.26f else 0.17f) * size
        val tw = tile * h / w
        for (r in 0 until rows) for (c in 0..14) {
            val x = 0.04f + c * (tw + 0.012f)
            if (x + tw > 0.96f) break
            val y = 0.48f + r * (tile + 0.02f)
            if (pale) rect(x - 0.004f, y - 0.007f, tw + 0.008f, tile + 0.014f, Color.White, 4f)
            card(x, y, tw, tile, r * 15 + c, on = r == 0 && c == 0, r0 = 3f, plainCover = true)
        }
        val ink = if (pale) Color(0xFF333333) else Color.White
        if (ob(Features.heroTilesDock)) rect(0.36f, 0.9f, 0.28f, 0.07f, ink.copy(alpha = 0.15f), 10f)
        for (i in 0..1) {
            dot(0.06f + i * 0.05f, 0.935f, 6f, ink.copy(alpha = 0.35f))
            dot(0.89f + i * 0.05f, 0.935f, 6f, ink.copy(alpha = 0.35f))
        }
    }

    /** Twin desk: a clock and this month's calendar on squared paper on top, wide game panels on the second screen. */
    fun twinDesk() {
        val digital = oe(Features.twinClock) == Features.TwinClock.DIGITAL.name
        val ink = Color(0xFF2B2B2B)
        rect(0.03f, 0.05f, 0.94f, 0.42f, Color(0xFFF4F1E8), 6f)
        var gx = 0.06f
        while (gx < 0.96f) { rect(gx, 0.05f, 0.002f, 0.42f, Color(0xFF9DB8D8).copy(alpha = 0.5f), 0f); gx += 0.03f }
        var gy = 0.1f
        while (gy < 0.46f) { rect(0.03f, gy, 0.94f, 0.003f, Color(0xFF9DB8D8).copy(alpha = 0.5f), 0f); gy += 0.053f }
        if (digital) {
            rect(0.07f, 0.15f, 0.3f, 0.2f, ink, 4f)
            for (j in 0..3) rect(0.09f + j * 0.068f, 0.19f, 0.048f, 0.12f, accent, 1f)
        } else {
            val c = Offset(0.2f * w, 0.26f * h)
            d.drawCircle(Color.White, 0.15f * h, c)
            d.drawCircle(ink, 0.15f * h, c, style = Stroke(2f * px))
            d.drawLine(ink, c, Offset(c.x, c.y - 0.1f * h), 2f * px)
            d.drawLine(accent, c, Offset(c.x + 0.07f * h, c.y), 2f * px)
        }
        if (ob(Features.twinCalendar)) for (r in 0..4) for (c in 0..6)
            rect(0.5f + c * 0.06f, 0.1f + r * 0.07f, 0.05f, 0.055f, if (r == 2 && c == 3) accent else ink.copy(alpha = 0.12f), 2f)
        rect(0f, 0.5f, 1f, 0.02f, Color.Black.copy(alpha = 0.6f), 0f)
        for (i in 0..2) {
            val y = 0.56f + i * 0.11f
            rect(0.05f, y, 0.72f, 0.09f, Color.White.copy(alpha = if (i == 0) 0.3f else 0.14f), 6f)
            card(0.06f, y + 0.01f, 0.04f, 0.07f, i, r0 = 2f, plainCover = true)
            line(0.13f, y + 0.035f, 0.25f, Color.White)
            if (i == 0) frame(0.05f, y, 0.72f, 0.09f, accent, 6f, 1.6f)
        }
        for (i in 0..2) dot(0.87f, 0.6f + i * 0.12f, 8f, Color.White.copy(alpha = 0.5f))
    }

    /** Twin icons: the game you're on as a big framed icon on top, a grid of square icons and an Open bar below. */
    fun twinIcons() {
        val cols = oi(Features.twinCols).coerceIn(4, 7)
        val s = 0.34f
        val sw = s * h / w
        rect(0.5f - sw / 2 - 0.01f, 0.042f, sw + 0.02f, s + 0.036f, Color.White.copy(alpha = 0.85f), 6f)
        card(0.5f - sw / 2, 0.06f, sw, s, 0, on = true, r0 = 5f, plainCover = true)
        line(0.5f + sw / 2 + 0.04f, 0.2f, 0.2f, Color.White, 0.04f)
        rect(0f, 0.5f, 1f, 0.02f, Color.Black.copy(alpha = 0.6f), 0f)
        for (i in 0..3) rect(0.03f, 0.55f + i * 0.085f, 0.07f, 0.07f, Color.White.copy(alpha = 0.2f), 3f)
        val cell = 0.84f / cols
        val ic = minOf(cell - 0.02f, 0.14f * h / w)
        for (r in 0..1) for (c in 0 until cols) card(0.13f + c * cell, 0.55f + r * 0.165f, ic, ic * w / h, r * cols + c, on = r == 0 && c == 0, r0 = 4f, plainCover = true)
        rect(0.13f, 0.9f, 0.84f, 0.07f, accent.copy(alpha = 0.8f), 8f)
    }

    /** Record crate: sleeves standing in a wooden crate, the one you're on lifted with its record sliding out. */
    fun crate() {
        val dark = oe(Features.crateWood) == Features.CrateWood.DARK.name
        val wood = if (dark) Color(0xFF4A2E1C) else Color(0xFFB98A5A)
        val grain = if (dark) Color(0xFF2E1B10) else Color(0xFF8A6238)
        val sq = 0.42f
        val sqw = sq * h / w
        for (j in 5 downTo 1) card(0.08f + j * 0.012f, 0.3f - j * 0.03f + 0.18f, sqw, sq, j, r0 = 1f, plainCover = true)
        // The record half out of the lifted sleeve.
        val rc = Offset((0.1f + sqw * 0.95f) * w, 0.3f * h)
        val rr = sq * 0.46f * h
        d.drawCircle(Color(0xFF111114), rr, rc)
        for (g in 1..3) d.drawCircle(Color.White.copy(alpha = 0.08f), rr * (0.5f + g * 0.14f), rc, style = Stroke(0.8f * px))
        d.drawCircle(cover(0), rr * 0.32f, rc)
        card(0.1f, 0.09f, sqw, sq, 0, on = true, r0 = 1f, plainCover = true)
        rect(0.04f, 0.56f, 0.5f, 0.38f, wood, 3f)
        for (j in 1..3) rect(0.04f, 0.56f + j * 0.095f, 0.5f, 0.006f, grain, 0f)
        line(0.62f, 0.3f, 0.3f, Color.White, 0.05f)
        for (r in 0..2) line(0.62f, 0.4f + r * 0.06f, 0.24f - r * 0.04f, Color.White.copy(alpha = 0.6f))
        rect(0.62f, 0.66f, 0.14f, 0.08f, accent, 5f)
    }

    /** Card binder: collectible cards fanned out like a hand, the one you're on big in the middle. */
    fun binder() {
        val fan = oi(Features.cardsFan).coerceIn(0, 15).toFloat()
        val artFrame = oe(Features.cardsFrame) == Features.CardFrame.ART.name
        val cw = 0.17f
        val ch = 0.66f
        for (n in 4 downTo 0) for (side in 0..1) {
            if (n == 0 && side == 1) continue
            val rel = if (side == 0) -n else n
            val sc = if (n == 0) 1f else 0.76f - n * 0.03f
            val x = 0.5f + rel * 0.105f
            val y = 0.5f + kotlin.math.abs(rel) * 0.03f
            d.rotate(rel * fan, Offset(x * w, (y + ch * sc / 2) * h)) {
                tradingCard(x - cw * sc / 2, y - ch * sc / 2, cw * sc, ch * sc, rel + 5, rel == 0, artFrame)
            }
        }
    }

    private fun tradingCard(x: Float, y: Float, ww: Float, hh: Float, i: Int, on: Boolean, artFrame: Boolean) {
        val frameC = if (artFrame) cover(i) else Color.hsv(((i * 83 + 30) % 360).toFloat(), 0.45f, 0.62f)
        if (on && effect != FocusEffect.NONE) for (g in 1..3) d.drawRoundRect(
            accent.copy(alpha = 0.16f - g * 0.04f), Offset(x * w - g * 2 * px, y * h - g * 2 * px),
            Size(ww * w + g * 4 * px, hh * h + g * 4 * px), CornerRadius((4 + g * 2) * px)
        )
        rect(x, y, ww, hh, frameC, 4f)
        rect(x + ww * 0.08f, y + hh * 0.05f, ww * 0.84f, hh * 0.06f, Color.White.copy(alpha = 0.75f), 1f)
        card(x + ww * 0.08f, y + hh * 0.14f, ww * 0.84f, hh * 0.46f, i, r0 = 1f, plainCover = true)
        for (r in 0..2) line(x + ww * 0.1f, y + hh * (0.66f + r * 0.07f), ww * 0.7f, Color.White.copy(alpha = 0.65f), 0.015f)
        for (st in 0..2) dot(x + ww * (0.3f + st * 0.2f), y + hh * 0.92f, 2f, Color(0xFFFFD54F))
        if (on) frame(x, y, ww, hh, accent, 4f, 2f)
    }
}
