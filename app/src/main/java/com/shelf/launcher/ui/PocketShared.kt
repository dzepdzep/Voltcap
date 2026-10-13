package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import kotlin.math.floor

/*
 * Update 181: what the two "Pocket" layouts share (PocketClassic.kt, PocketRibbon.kt): Voltcap's own takes on the menus of
 * the two-screen pocket handhelds of 2004 and 2008, with original art and names only.
 * - A pocket screen: a 4:3 screen of 256 × 192 "dots" (the reference proportions), drawn either at a whole number of
 *   pixels per dot (sharp, Pixel-perfect) or filling the room (Fit the screen: as tall as it can be, wider when there's
 *   room, nothing stretched). Everything inside is placed in dots: [PocketScope.at].
 * - Pixel art: small icons drawn dot by dot from text pictures ([PocketIcons]).
 * - The pixel font, the pinstriped paper, the thin outlines and the corner brackets.
 */

/** Height of a pocket screen in dots, and its reference width. */
internal const val POCKET_H = 192f
internal const val POCKET_W = 256f

/** Where things sit inside a pocket screen: one dot is [u] dp; the 256-dot design is [cx] dots in from the left. */
internal class PocketScope(val u: Dp, val width: Float, val density: Float) {
    /** The 256-dot design centred in a wider screen. */
    val cx: Float = (width - POCKET_W) / 2f
    fun d(dots: Float): Dp = u * dots
    /** Text [dots] tall (the pixel font's size). */
    fun sp(dots: Float): TextUnit = with(androidx.compose.ui.unit.Density(density)) { (u * dots).toSp() }
    /** A box at [x],[y] (dots, in the 256-wide design), [w] × [h] dots. */
    fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier = this.offset(u * (x + cx), u * y).size(u * w, u * h)
    /** The same, measured from the screen's own left edge (backgrounds, full-width bars). */
    fun Modifier.atScreen(x: Float, y: Float, w: Float, h: Float): Modifier = this.offset(u * x, u * y).size(u * w, u * h)
}

/**
 * Update 181: a pocket screen filling [modifier] (letterboxed on [outside]): a 4:3 screen at a whole number of pixels per dot
 * (pixel-perfect), or as big as fits and wider when there's room ([responsive]).
 */
@Composable
internal fun PocketScreen(
    modifier: Modifier,
    responsive: Boolean,
    outside: Color,
    content: @Composable PocketScope.() -> Unit
) {
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fit = minOf(wPx / POCKET_W, hPx / POCKET_H)
        val scope = if (responsive) {
            // As tall as it can be; wider than 4:3 when the room is (up to 16:9), never stretched.
            val uPx = minOf(hPx / POCKET_H, wPx / POCKET_W)
            val width = (wPx / uPx).coerceIn(POCKET_W, POCKET_H * 16f / 9f)
            PocketScope((uPx / density).dp, width, density)
        } else {
            // Whole pixels per dot (sharp), at least one.
            val uPx = floor(fit).coerceAtLeast(1f).let { if (fit < 1f) fit else it }
            PocketScope((uPx / density).dp, POCKET_W, density)
        }
        Box(Modifier.size(scope.d(scope.width), scope.d(POCKET_H))) {
            scope.content()
        }
    }
}

/** The pixel font (Voltcap's own). */
@Composable
internal fun pocketFont(): FontFamily = remember { FontFamily(Font(com.shelf.launcher.R.font.volt_pixel)) }

/** White paper with very faint vertical pinstripes, every [step] dots. */
internal fun DrawScope.pocketPinstripes(u: Float, base: Color, line: Color, step: Float = 2f) {
    drawRect(base)
    var x = 0f
    val w = (u * 0.5f).coerceAtLeast(1f)
    while (x < size.width) {
        drawRect(line, Offset(x, 0f), Size(w, size.height))
        x += u * step
    }
}

/** A grid of thin lines every [pitch] dots, starting at [ox], [oy] dots. */
internal fun DrawScope.pocketGrid(u: Float, line: Color, pitch: Float, ox: Float, oy: Float) {
    val w = (u * 0.6f).coerceAtLeast(1f)
    var x = ox * u
    while (x < size.width) { if (x >= 0f) drawRect(line, Offset(x, 0f), Size(w, size.height)); x += pitch * u }
    var y = oy * u
    while (y < size.height) { if (y >= 0f) drawRect(line, Offset(0f, y), Size(size.width, w)); y += pitch * u }
}

/**
 * The corner brackets round the selected panel: four short L shapes [len] dots long, [thick] dots thick, just outside the
 * panel's edges ([out] dots).
 */
internal fun DrawScope.pocketBrackets(u: Float, color: Color, len: Float = 6f, thick: Float = 1.5f, out: Float = 1.5f) {
    val l = len * u; val t = thick * u; val o = out * u
    val w = size.width; val h = size.height
    // Top left, top right, bottom left, bottom right.
    drawRect(color, Offset(-o, -o), Size(l, t)); drawRect(color, Offset(-o, -o), Size(t, l))
    drawRect(color, Offset(w + o - l, -o), Size(l, t)); drawRect(color, Offset(w + o - t, -o), Size(t, l))
    drawRect(color, Offset(-o, h + o - t), Size(l, t)); drawRect(color, Offset(-o, h + o - l), Size(t, l))
    drawRect(color, Offset(w + o - l, h + o - t), Size(l, t)); drawRect(color, Offset(w + o - t, h + o - l), Size(t, l))
}

/** A thin outline [dots] thick inside the box. */
internal fun DrawScope.pocketOutline(u: Float, color: Color, dots: Float = 1f) {
    val t = (dots * u).coerceAtLeast(1f)
    drawRect(color, Offset.Zero, Size(size.width, t))
    drawRect(color, Offset(0f, size.height - t), Size(size.width, t))
    drawRect(color, Offset.Zero, Size(t, size.height))
    drawRect(color, Offset(size.width - t, 0f), Size(t, size.height))
}

/**
 * Small pixel icons, drawn dot by dot (original art): '#' = the ink, '+' = the accent, 'o' = a lighter grey.
 * Each is 12 × 12 dots.
 */
internal object PocketIcons {
    val GEAR = listOf(
        "....##.##...",
        "...#######..",
        ".##.#####.#.",
        ".#########..",
        "###.....###.",
        ".##..##..##.",
        ".##..##..##.",
        "###.....###.",
        ".#########..",
        ".##.#####.#.",
        "...#######..",
        "....##.##..."
    )
    val GRAPH = listOf(
        "#...........",
        "#.........+.",
        "#........++.",
        "#....#...++.",
        "#...##..+++.",
        "#...##.++++.",
        "#.#.##.++++.",
        "#.#.##.++++.",
        "#.#.##.++++.",
        "#.#.##.++++.",
        "############",
        "............"
    )
    val MOON = listOf(
        "....####....",
        "..###.......",
        ".##.........",
        ".##.........",
        "##..........",
        "##..........",
        "##........#.",
        "##.......##.",
        ".##.....##..",
        ".###..###...",
        "..######....",
        "....##......"
    )
    val TROPHY = listOf(
        "..########..",
        "####++++####",
        "#.#++++++#.#",
        "#.#++++++#.#",
        ".##++++++##.",
        "..##++++##..",
        "...######...",
        ".....##.....",
        ".....##.....",
        "....####....",
        "...######...",
        "...######..."
    )
    val ALL = listOf(
        "####..####..",
        "#++#..#oo#..",
        "#++#..#oo#..",
        "####..####..",
        "............",
        "####..####..",
        "#oo#..#++#..",
        "#oo#..#++#..",
        "####..####..",
        "............",
        "............",
        "............"
    )
    val CARD = listOf(
        "..########..",
        ".#........#.",
        ".#.######.#.",
        ".#.#oooo#.#.",
        ".#.#oooo#.#.",
        ".#.######.#.",
        ".#........#.",
        ".#.#.#.#.##.",
        ".#.#.#.#.##.",
        ".##########.",
        "............",
        "............"
    )
    val PAD = listOf(
        "............",
        "............",
        ".##########.",
        "##.##.....##",
        "#..##..+...#",
        "#.####+.+..#",
        "#..##..+...#",
        "##.##.....##",
        ".####..####.",
        ".##......##.",
        "............",
        "............"
    )
    val BACK = listOf(
        "............",
        "....#.......",
        "...##.......",
        "..#########.",
        ".##########.",
        "..#########.",
        "...##.....#.",
        "....#.....#.",
        "..........#.",
        "...########.",
        "............",
        "............"
    )
    val VOLUME = listOf(
        "............",
        ".....#......",
        "....##...#..",
        "..####.#..#.",
        "..####..#.#.",
        "..####..#.#.",
        "..####.#..#.",
        "....##...#..",
        ".....#......",
        "............",
        "............",
        "............"
    )
}

/** Draws [icon] filling the box (one cell per dot). */
internal fun DrawScope.pocketIcon(icon: List<String>, ink: Color, accent: Color, light: Color = Color(0xFFB8BEC6)) {
    val rows = icon.size
    val cols = icon.maxOf { it.length }
    val cw = size.width / cols
    val ch = size.height / rows
    icon.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = when (c) { '#' -> ink; '+' -> accent; 'o' -> light; else -> null }
            if (color != null) drawRect(color, Offset(x * cw, y * ch), Size(cw + 0.5f, ch + 0.5f))
        }
    }
}

/** Update 181: the pocket layouts' accent colours (the user colour of those menus), and Voltcap's theme accent. */
internal enum class PocketAccent(val label: String, val argb: Long) {
    BLUE("Blue", 0xFF2F7BD6), SKY("Sky", 0xFF35A9E8), PINK("Pink", 0xFFE0408E), RED("Red", 0xFFD6403A),
    ORANGE("Orange", 0xFFE8862A), GREEN("Green", 0xFF3BA64A), PURPLE("Purple", 0xFF7A52C7), GREY("Grey", 0xFF6D7682),
    THEME("Theme", 0)
}

@Composable
internal fun pocketAccent(a: PocketAccent): Color = if (a == PocketAccent.THEME) com.shelf.launcher.ui.theme.ShelfTheme.accent else Color(a.argb)

/** Update 181: how long the pocket layouts' small moves take (Layout options › Animation speed), ms. */
internal fun pocketMs(base: Int): Int = when (Features.pocketAnim.value) {
    Features.PocketAnim.FAST -> base * 6 / 10
    Features.PocketAnim.OFF -> 0
    else -> base
}

/** Update 181: the user name the pocket layouts show (Layout options, else the RetroAchievements name, else "Player"). */
internal fun pocketUser(state: com.shelf.launcher.LauncherUiState): String =
    Features.pocketName.value.ifBlank { state.raUsername?.takeIf { it.isNotBlank() } ?: "Player" }

/** Update 181: behind the Pocket layouts: their paper filling the top screen (two screens), else the handheld's light body. */
@Composable
internal fun PocketBackdrop(modifier: Modifier, paper: Boolean) {
    val density = LocalDensity.current.density
    Canvas(modifier) {
        if (paper) pocketPinstripes(density * 1.5f, Color.White, Color(0xFFF2F3F5))
        else {
            drawRect(Color(0xFFE6E8EB))
            // A soft sheen across the body, like moulded plastic.
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.05f))))
        }
    }
}
