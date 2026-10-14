package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Little pictures for the visual choices in Settings (layouts, card art, second-screen art, interface style,
 * and more), so they read at a glance: each option becomes a tile with its drawing and its name, the
 * picked one outlined in the accent. Drawn with Canvas from simple shapes (no image files), in the theme's
 * colours. [RowChoice] uses them whenever every value of a choice has a picture.
 */

/** A drawing for one option. */
internal enum class Pictogram {
    ROW, TILES, HERO, GRID, LIST, TV, SPINES, SHELF,
    COVER, CAPSULE, CASE, CARTRIDGE, DISC, HERO_ART, SCREENSHOT, SLIDESHOW, SHOWCASE, BACKGROUND,
    STYLE_VOLT, STYLE_CAPSULE, STYLE_PIXEL, STYLE_MEMORY, STYLE_CRT, STYLE_NEON, STYLE_CART, STYLE_CLASSIC,
    MUSIC_FULL, MUSIC_VISUALIZER, MUSIC_CLASSIC,
    DASH, LIVE, SIDE_MENU, CROSS, SPOT, ICON_BAR
}

/** The picture for an option value, or null (then the choice stays plain chips). */
internal fun pictogramFor(value: Any?): Pictogram? = when (value) {
    is com.shelf.launcher.data.HomeLayout -> when (value) {
        com.shelf.launcher.data.HomeLayout.CLASSIC -> Pictogram.ROW
        com.shelf.launcher.data.HomeLayout.TILES -> Pictogram.TILES
        com.shelf.launcher.data.HomeLayout.HERO -> Pictogram.HERO
        com.shelf.launcher.data.HomeLayout.GRID -> Pictogram.GRID
        com.shelf.launcher.data.HomeLayout.TV -> Pictogram.TV
        com.shelf.launcher.data.HomeLayout.DASH -> Pictogram.DASH
        com.shelf.launcher.data.HomeLayout.LIVE -> Pictogram.LIVE
        com.shelf.launcher.data.HomeLayout.SIDE -> Pictogram.SIDE_MENU
        com.shelf.launcher.data.HomeLayout.CROSS -> Pictogram.CROSS
        com.shelf.launcher.data.HomeLayout.SPOT -> Pictogram.SPOT
        com.shelf.launcher.data.HomeLayout.BAR -> Pictogram.ICON_BAR
        com.shelf.launcher.data.HomeLayout.SHELF -> Pictogram.SHELF
        // (no pictures of their own yet: the nearest look)
        com.shelf.launcher.data.HomeLayout.HUB -> Pictogram.SPOT
        com.shelf.launcher.data.HomeLayout.RESUME -> Pictogram.LIVE
        com.shelf.launcher.data.HomeLayout.CHAN -> Pictogram.GRID
        com.shelf.launcher.data.HomeLayout.CIRCUIT -> Pictogram.ROW
        // Update 146 (the nearest pictures).
        com.shelf.launcher.data.HomeLayout.LIBRARY -> Pictogram.GRID
        com.shelf.launcher.data.HomeLayout.STACKS -> Pictogram.SPINES
        com.shelf.launcher.data.HomeLayout.TIMELINE -> Pictogram.ROW
        com.shelf.launcher.data.HomeLayout.ARCADE -> Pictogram.TV
        com.shelf.launcher.data.HomeLayout.CONSOLES -> Pictogram.SHELF
        com.shelf.launcher.data.HomeLayout.DISCS -> Pictogram.DISC
        // Update 158 (the nearest picture: tiles under a big picture).
        com.shelf.launcher.data.HomeLayout.HERO_TILES -> Pictogram.SPOT
        // Update 158: the two-screen layouts (the nearest pictures: a list of panels, a grid of icons).
        com.shelf.launcher.data.HomeLayout.TWIN_DESK -> Pictogram.LIST
        com.shelf.launcher.data.HomeLayout.TWIN_ICONS -> Pictogram.GRID
        // Update 158: Record crate (the nearest picture: a disc).
        com.shelf.launcher.data.HomeLayout.CRATE -> Pictogram.DISC
        // Update 158: Card binder (the nearest picture: a big card with others either side).
        com.shelf.launcher.data.HomeLayout.CARDS -> Pictogram.SHOWCASE
        // Update 176: Custom layout (the nearest picture: a big picture with a row).
        com.shelf.launcher.data.HomeLayout.CUSTOM -> Pictogram.HERO
        // Update 181 (the nearest pictures: a grid of panels, a row).
        com.shelf.launcher.data.HomeLayout.POCKET_CLASSIC -> Pictogram.GRID
        com.shelf.launcher.data.HomeLayout.POCKET_RIBBON -> Pictogram.ROW
        // Update 202: Green Core (the nearest picture: a menu beside a big picture).
        com.shelf.launcher.data.HomeLayout.GREENCORE -> Pictogram.SIDE_MENU
        // Update 202: Pocket Quest (the nearest picture: a big game with others beside it).
        com.shelf.launcher.data.HomeLayout.POCKET -> Pictogram.SHOWCASE
    }
    is TvRoom.Games -> when (value) {
        TvRoom.Games.SPINES -> Pictogram.SPINES
        TvRoom.Games.COVERS -> Pictogram.GRID
        TvRoom.Games.SHELF -> Pictogram.SHELF
        TvRoom.Games.CAROUSEL -> Pictogram.SHOWCASE
    }
    is com.shelf.launcher.data.CardArtType -> when (value) {
        com.shelf.launcher.data.CardArtType.COVER -> Pictogram.COVER
        com.shelf.launcher.data.CardArtType.CAPSULE -> Pictogram.CAPSULE
        com.shelf.launcher.data.CardArtType.CASE -> Pictogram.CASE
        com.shelf.launcher.data.CardArtType.CASE_REAL -> Pictogram.CASE
        com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC -> Pictogram.CARTRIDGE
        com.shelf.launcher.data.CardArtType.DISC -> Pictogram.DISC
        com.shelf.launcher.data.CardArtType.HERO -> Pictogram.HERO_ART
        com.shelf.launcher.data.CardArtType.SCREENSHOT -> Pictogram.SCREENSHOT
    }
    is com.shelf.launcher.data.SecondArtType -> when (value) {
        com.shelf.launcher.data.SecondArtType.HERO -> Pictogram.HERO_ART
        com.shelf.launcher.data.SecondArtType.BACKGROUND -> Pictogram.BACKGROUND
        com.shelf.launcher.data.SecondArtType.COVER -> Pictogram.COVER
        com.shelf.launcher.data.SecondArtType.SCREENSHOTS -> Pictogram.SCREENSHOT
        com.shelf.launcher.data.SecondArtType.SLIDESHOW -> Pictogram.SLIDESHOW
        com.shelf.launcher.data.SecondArtType.CASE -> Pictogram.CASE
        com.shelf.launcher.data.SecondArtType.DISC -> Pictogram.DISC
        com.shelf.launcher.data.SecondArtType.SHOWCASE -> Pictogram.SHOWCASE
    }
    is com.shelf.launcher.data.SecondMusicLayout -> when (value.name) {
        "FULL" -> Pictogram.MUSIC_FULL
        "VISUALIZER" -> Pictogram.MUSIC_VISUALIZER
        // Update 200: the Lyrics stage is a cover with a visualizer round it; it borrows the visualizer's drawing.
        "STAGE" -> Pictogram.MUSIC_VISUALIZER
        else -> Pictogram.MUSIC_CLASSIC
    }
    is Features.UiStyle -> when (value) {
        Features.UiStyle.VOLT -> Pictogram.STYLE_VOLT
        Features.UiStyle.CAPSULE -> Pictogram.STYLE_CAPSULE
        Features.UiStyle.PIXEL -> Pictogram.STYLE_PIXEL
        Features.UiStyle.MEMORY_CARD -> Pictogram.STYLE_MEMORY
        Features.UiStyle.CRT -> Pictogram.STYLE_CRT
        Features.UiStyle.NEON -> Pictogram.STYLE_NEON
        Features.UiStyle.CARTRIDGE -> Pictogram.STYLE_CART
        Features.UiStyle.CLASSIC -> Pictogram.STYLE_CLASSIC
    }
    else -> null
}

/** Option tiles: a drawing and a name each, the picked one outlined. Wraps onto more lines. */
@Composable
internal fun <T> PictoChoice(values: Array<T>, current: T, name: (T) -> String, onPick: (T) -> Unit) {
    ChipFlow {
        values.forEach { v ->
            val on = v == current
            val shape = uiRounded(12.dp)
            Column(
                Modifier
                    .width(92.dp)
                    .voltSurface(shape, alpha = if (on) 1f else 0.75f)
                    .border(if (on) 1.5.dp else 0.dp, if (on) ShelfTheme.accent else Color.Transparent, shape)
                    .shelfClickable(shape) { onPick(v) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                PictogramIcon(pictogramFor(v), on, Modifier.size(width = 64.dp, height = 42.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    name(v), color = if (on) ShelfTheme.accent else ShelfTheme.colors.textPrimary,
                    fontSize = 11.sp, lineHeight = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** One drawing. [on] = picked (drawn brighter). */
@Composable
internal fun PictogramIcon(p: Pictogram?, on: Boolean, modifier: Modifier = Modifier) {
    val accent = ShelfTheme.accent
    val ink = ShelfTheme.colors.textPrimary.copy(alpha = if (on) 0.75f else 0.5f)
    val faint = ShelfTheme.colors.textPrimary.copy(alpha = 0.18f)
    val hot = if (on) accent else accent.copy(alpha = 0.75f)
    Canvas(modifier) { if (p != null) drawPictogram(p, hot, ink, faint) }
}

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, c: Color, r: Float = 3f, stroke: Boolean = false) {
    val px = size.width; val py = size.height
    drawRoundRect(
        c, topLeft = Offset(x * px, y * py), size = Size(w * px, h * py), cornerRadius = CornerRadius(r.dp.toPx()),
        style = if (stroke) Stroke(1.5.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill
    )
}

private fun DrawScope.dot(x: Float, y: Float, r: Float, c: Color) = drawCircle(c, r.dp.toPx(), Offset(x * size.width, y * size.height))

private fun DrawScope.drawPictogram(p: Pictogram, hot: Color, ink: Color, faint: Color) {
    when (p) {
        Pictogram.ROW -> { box(0.04f, 0.38f, 0.38f, 0.42f, hot); for (i in 0..2) box(0.48f + i * 0.17f, 0.42f, 0.13f, 0.34f, ink); box(0.04f, 0.12f, 0.3f, 0.08f, faint) }
        // The style homes (StyleHomes.kt).
        Pictogram.DASH -> {
            for (i in 0..3) dot(0.07f, 0.16f + i * 0.2f, 3.2f, if (i == 0) hot else ink)
            box(0.16f, 0.06f, 0.56f, 0.46f, hot.copy(alpha = 0.65f), 2f)
            for (i in 0..3) box(0.16f + i * 0.145f, 0.58f, 0.12f, 0.2f, ink, 1.5f)
            box(0.16f, 0.84f, 0.56f, 0.1f, faint, 1.5f)
            box(0.77f, 0.06f, 0.21f, 0.4f, faint, 2f); box(0.77f, 0.5f, 0.21f, 0.44f, faint, 2f)
        }
        Pictogram.LIVE -> {
            box(0.04f, 0.04f, 0.3f, 0.08f, ink, 0f)
            box(0.04f, 0.18f, 0.4f, 0.5f, hot, 0f)
            for (r in 0..1) for (c in 0..1) box(0.47f + c * 0.25f, 0.18f + r * 0.26f, 0.22f, 0.22f, ink, 0f)
            box(0.04f, 0.74f, 0.21f, 0.2f, hot.copy(alpha = 0.6f), 0f); for (c in 1..3) box(0.04f + c * 0.235f, 0.74f, 0.21f, 0.2f, faint, 0f)
        }
        Pictogram.SIDE_MENU -> {
            box(0f, 0f, 0.28f, 1f, ink.copy(alpha = 0.55f), 0f)
            dot(0.14f, 0.13f, 4f, hot)
            for (i in 0..3) box(0.04f, 0.26f + i * 0.13f, 0.2f, 0.08f, if (i == 0) hot else faint, 1f)
            dot(0.14f, 0.84f, 5f, hot.copy(alpha = 0.6f))
            for (c in 0..3) box(0.33f + c * 0.165f, 0.6f, 0.14f, 0.32f, if (c == 0) hot else ink, 2f)
        }
        Pictogram.CROSS -> {
            for (c in 0..4) dot(0.1f + c * 0.2f, 0.2f, if (c == 1) 6f else 4f, if (c == 1) hot else ink)
            box(0.18f, 0.38f, 0.2f, 0.16f, hot, 1.5f); box(0.42f, 0.42f, 0.4f, 0.08f, hot.copy(alpha = 0.7f), 1f)
            for (r in 0..2) { box(0.2f, 0.6f + r * 0.13f, 0.14f, 0.1f, ink, 1f); box(0.4f, 0.62f + r * 0.13f, 0.32f, 0.05f, faint, 1f) }
        }
        Pictogram.SPOT -> {
            box(0f, 0f, 1f, 1f, faint, 4f)
            for (c in 0..5) box(0.06f + c * 0.15f, 0.08f, if (c == 0) 0.14f else 0.1f, if (c == 0) 0.2f else 0.14f, if (c == 0) hot else ink, 2f)
            box(0.06f, 0.48f, 0.5f, 0.12f, ink, 1f)
            box(0.06f, 0.68f, 0.2f, 0.1f, hot, 5f); dot(0.34f, 0.73f, 3f, ink)
            for (c in 0..2) box(0.06f + c * 0.3f, 0.84f, 0.26f, 0.12f, faint, 2f)
        }
        Pictogram.ICON_BAR -> {
            for (c in 0..5) box(0.2f + c * 0.1f, 0.06f, 0.07f, 0.1f, if (c == 2) hot else faint, 1.5f)
            box(0.04f, 0.3f, 0.3f, 0.38f, hot, 1.5f)
            for (c in 0..3) box(0.37f + c * 0.155f, 0.3f, 0.14f, 0.22f, ink, 1.5f)
            box(0.04f, 0.74f, 0.4f, 0.07f, ink, 1f); box(0.04f, 0.86f, 0.14f, 0.08f, faint, 1f, stroke = true)
        }
        Pictogram.TILES -> { for (i in 0..3) box(0.04f + i * 0.24f, 0.3f, 0.2f, 0.32f, if (i == 1) hot else ink); for (i in 0..3) dot(0.2f + i * 0.2f, 0.85f, 3f, faint) }
        Pictogram.HERO -> { box(0f, 0f, 1f, 1f, faint, 4f); for (i in 0..2) box(0.06f, 0.08f + i * 0.3f, 0.14f, 0.24f, if (i == 1) hot else ink); box(0.3f, 0.6f, 0.45f, 0.1f, ink) }
        Pictogram.GRID -> { for (r in 0..1) for (c in 0..2) box(0.08f + c * 0.3f, 0.06f + r * 0.48f, 0.24f, 0.4f, if (r == 0 && c == 0) hot else ink) }
        Pictogram.TV -> {
            box(0.02f, 0.18f, 0.5f, 0.62f, ink.copy(alpha = 0.6f), 5f); box(0.07f, 0.26f, 0.4f, 0.44f, hot.copy(alpha = 0.6f), 6f)
            box(0.04f, 0.04f, 0.4f, 0.1f, hot, 2f)
            for (r in 0..1) for (c in 0..1) box(0.58f + c * 0.2f, 0.06f + r * 0.4f, 0.16f, 0.34f, if (r == 0 && c == 0) hot else ink, 2f)
            box(0.02f, 0.86f, 0.96f, 0.1f, faint, 2f)
        }
        Pictogram.SPINES -> {
            for (c in 0..2) { box(0.06f + c * 0.31f, 0.14f, 0.05f, 0.72f, if (c == 0) hot else ink, 0f); box(0.11f + c * 0.31f, 0.14f, 0.22f, 0.72f, faint, 1f) }
        }
        Pictogram.SHELF -> {
            for (c in 0..7) box(0.06f + c * 0.075f, 0.14f, 0.06f, 0.72f, if (c == 2) hot else ink, 0f)
            box(0.66f, 0.06f, 0.28f, 0.8f, hot.copy(alpha = 0.6f), 2f)
            box(0.02f, 0.86f, 0.96f, 0.06f, faint, 0f)
        }
        Pictogram.LIST -> { for (r in 0..3) { box(0.06f, 0.06f + r * 0.24f, 0.12f, 0.18f, if (r == 0) hot else ink); box(0.24f, 0.1f + r * 0.24f, if (r == 0) 0.6f else 0.5f, 0.09f, if (r == 0) hot else faint) } }
        Pictogram.COVER -> { box(0.34f, 0.04f, 0.32f, 0.92f, hot); box(0.39f, 0.7f, 0.22f, 0.06f, ink) }
        Pictogram.CAPSULE -> { box(0.08f, 0.2f, 0.84f, 0.6f, hot); box(0.14f, 0.6f, 0.3f, 0.08f, ink) }
        Pictogram.CASE -> { box(0.3f, 0.04f, 0.4f, 0.92f, ink); box(0.3f, 0.04f, 0.4f, 0.16f, hot); box(0.35f, 0.26f, 0.3f, 0.6f, faint) }
        Pictogram.CARTRIDGE -> {
            box(0.28f, 0.06f, 0.44f, 0.88f, ink, 4f); box(0.34f, 0.2f, 0.32f, 0.5f, hot, 2f)
            for (i in 0..4) box(0.33f + i * 0.07f, 0.82f, 0.04f, 0.08f, faint, 1f)
        }
        Pictogram.DISC -> { dot(0.5f, 0.5f, 17f, ink); dot(0.5f, 0.5f, 12f, hot); dot(0.5f, 0.5f, 3.5f, faint) }
        Pictogram.HERO_ART -> { box(0.02f, 0.06f, 0.96f, 0.88f, hot.copy(alpha = 0.55f), 4f); dot(0.75f, 0.32f, 6f, hot); box(0.08f, 0.68f, 0.4f, 0.1f, ink) }
        Pictogram.BACKGROUND -> { box(0.02f, 0.18f, 0.96f, 0.64f, hot.copy(alpha = 0.55f), 4f); box(0.08f, 0.6f, 0.3f, 0.1f, ink) }
        Pictogram.SCREENSHOT -> { box(0.1f, 0.12f, 0.8f, 0.76f, ink, 4f, stroke = true); box(0.16f, 0.2f, 0.68f, 0.6f, hot.copy(alpha = 0.5f), 2f); dot(0.7f, 0.35f, 3f, hot) }
        Pictogram.SLIDESHOW -> { box(0.22f, 0.08f, 0.6f, 0.6f, faint, 3f); box(0.14f, 0.2f, 0.6f, 0.6f, ink, 3f); box(0.06f, 0.32f, 0.6f, 0.6f, hot, 3f) }
        Pictogram.SHOWCASE -> {
            box(0f, 0f, 1f, 1f, faint, 4f); box(0.06f, 0.12f, 0.32f, 0.76f, ink, 3f); box(0.1f, 0.18f, 0.24f, 0.16f, hot, 2f)
            box(0.46f, 0.16f, 0.36f, 0.1f, hot); for (i in 0..3) box(0.46f, 0.36f + i * 0.13f, if (i == 3) 0.28f else 0.46f, 0.06f, ink)
        }
        Pictogram.STYLE_VOLT -> {
            box(0.06f, 0.1f, 0.88f, 0.8f, ink.copy(alpha = 0.35f), 6f); box(0.06f, 0.1f, 0.88f, 0.8f, hot, 6f, stroke = true)
            drawLine(hot, Offset(size.width * 0.14f, size.height * 0.34f), Offset(size.width * 0.86f, size.height * 0.34f), 1.5.dp.toPx())
            dot(0.62f, 0.34f, 3f, Color.White)
        }
        Pictogram.STYLE_CAPSULE -> {
            box(0.04f, 0.18f, 0.92f, 0.64f, Color.Black.copy(alpha = 0.55f), 14f)
            box(0.04f, 0.18f, 0.92f, 0.64f, hot, 14f, stroke = true)
            box(0.1f, 0.27f, 0.8f, 0.46f, hot.copy(alpha = 0.35f), 10f, stroke = true)
            for (r in 0..2) for (c in 0..9) dot(0.16f + c * 0.075f, 0.36f + r * 0.13f, 0.9f, ink.copy(alpha = 0.35f))
        }
        Pictogram.STYLE_PIXEL -> {
            box(0.06f, 0.1f, 0.88f, 0.8f, ink.copy(alpha = 0.4f), 0f)
            box(0.06f, 0.1f, 0.88f, 0.05f, Color.White.copy(alpha = 0.45f), 0f); box(0.06f, 0.1f, 0.03f, 0.8f, Color.White.copy(alpha = 0.45f), 0f)
            box(0.06f, 0.85f, 0.88f, 0.05f, Color.Black.copy(alpha = 0.6f), 0f); box(0.91f, 0.1f, 0.03f, 0.8f, Color.Black.copy(alpha = 0.6f), 0f)
            box(0.16f, 0.3f, 0.3f, 0.1f, hot, 0f); box(0.16f, 0.5f, 0.5f, 0.08f, faint, 0f); box(0.16f, 0.66f, 0.4f, 0.08f, faint, 0f)
        }
        Pictogram.STYLE_MEMORY -> {
            box(0.22f, 0.04f, 0.56f, 0.92f, ink.copy(alpha = 0.35f), 4f)
            box(0.22f, 0.04f, 0.56f, 0.2f, hot.copy(alpha = 0.7f), 4f)
            for (i in 0..5) box(0.68f, 0.34f + i * 0.09f, 0.07f, 0.03f, ink, 0f)
            box(0.3f, 0.82f, 0.36f, 0.06f, faint, 0f)
        }
        Pictogram.STYLE_CRT -> {
            box(0.08f, 0.1f, 0.84f, 0.74f, ink.copy(alpha = 0.3f), 10f)
            box(0.07f, 0.1f, 0.84f, 0.74f, Color(0xFFFF4060).copy(alpha = 0.6f), 10f, stroke = true)
            box(0.09f, 0.1f, 0.84f, 0.74f, Color(0xFF40A0FF).copy(alpha = 0.6f), 10f, stroke = true)
            for (i in 0..5) box(0.12f, 0.18f + i * 0.1f, 0.76f, 0.02f, Color.Black.copy(alpha = 0.3f), 0f)
            box(0.4f, 0.86f, 0.2f, 0.08f, ink, 0f)
        }
        Pictogram.STYLE_NEON -> {
            box(0.06f, 0.14f, 0.88f, 0.72f, Color.Black.copy(alpha = 0.6f), 8f)
            box(0.1f, 0.2f, 0.8f, 0.6f, hot.copy(alpha = 0.3f), 8f, stroke = true)
            box(0.12f, 0.22f, 0.76f, 0.56f, androidx.compose.ui.graphics.lerp(hot, Color.White, 0.5f), 7f, stroke = true)
        }
        Pictogram.STYLE_CART -> {
            box(0.18f, 0.04f, 0.64f, 0.92f, ink.copy(alpha = 0.45f), 4f)
            box(0.26f, 0.1f, 0.48f, 0.6f, faint, 3f); box(0.26f, 0.1f, 0.48f, 0.07f, hot, 0f)
            for (i in 0..5) { box(0.19f, 0.2f + i * 0.1f, 0.05f, 0.025f, Color.Black.copy(alpha = 0.4f), 0f); box(0.76f, 0.2f + i * 0.1f, 0.05f, 0.025f, Color.Black.copy(alpha = 0.4f), 0f) }
        }
        Pictogram.STYLE_CLASSIC -> { box(0.06f, 0.1f, 0.88f, 0.8f, ink.copy(alpha = 0.5f), 10f); box(0.18f, 0.3f, 0.5f, 0.1f, faint); box(0.18f, 0.5f, 0.64f, 0.1f, faint) }
        Pictogram.MUSIC_FULL -> { box(0.06f, 0.08f, 0.88f, 0.84f, faint, 4f); box(0.36f, 0.16f, 0.28f, 0.4f, hot, 3f); box(0.24f, 0.66f, 0.52f, 0.06f, ink); dot(0.5f, 0.82f, 3.5f, hot) }
        Pictogram.MUSIC_VISUALIZER -> { box(0.06f, 0.08f, 0.88f, 0.84f, faint, 4f); for (i in 0..6) { val h = listOf(0.3f, 0.55f, 0.4f, 0.7f, 0.45f, 0.6f, 0.25f)[i]; box(0.14f + i * 0.11f, 0.86f - h, 0.07f, h, if (i == 3) hot else ink, 1.5f) } }
        Pictogram.MUSIC_CLASSIC -> { box(0.1f, 0.3f, 0.8f, 0.4f, ink.copy(alpha = 0.4f), 6f); box(0.16f, 0.36f, 0.18f, 0.28f, hot, 3f); box(0.4f, 0.4f, 0.4f, 0.06f, ink); box(0.4f, 0.54f, 0.28f, 0.05f, faint) }
    }
}
