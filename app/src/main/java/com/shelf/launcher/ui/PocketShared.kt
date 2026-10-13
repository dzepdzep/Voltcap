package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
internal class PocketScope(
    val u: Dp, val width: Float, val density: Float,
    /** Update 182: the screen's whole height in dots (at least the design's), and the design's offset down it. */
    val fullH: Float = POCKET_H, val cy: Float = 0f
) {
    /** The 256-dot design centred in a wider screen. */
    val cx: Float = (width - POCKET_W) / 2f
    fun d(dots: Float): Dp = u * dots
    /** Text [dots] tall (the pixel font's size). */
    fun sp(dots: Float): TextUnit = with(androidx.compose.ui.unit.Density(density)) { (u * dots).toSp() }
    /** A box at [x],[y] (dots, in the 256-wide design), [w] × [h] dots. */
    fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier = this.offset(u * (x + cx), u * y).size(u * w, u * h)
    /** The same, measured from the screen's own left edge (backgrounds, full-width bars). */
    fun Modifier.atScreen(x: Float, y: Float, w: Float, h: Float): Modifier = this.offset(u * x, u * y).size(u * w, u * h)
    /**
     * Update 182: from the screen's own top left corner (backgrounds filling the whole screen). The design is drawn [cy]
     * dots down the screen (in the middle), so this reaches back up past its top.
     */
    fun Modifier.atFull(x: Float, y: Float, w: Float, h: Float): Modifier = this.offset(u * x, u * (y - cy)).size(u * w, u * h)
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
    /** Update 182: the design's height in dots (192, more with the home strip under it). */
    designH: Float = POCKET_H,
    content: @Composable PocketScope.() -> Unit
) {
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fit = minOf(wPx / POCKET_W, hPx / designH)
        // Responsive (the default since update 182): as big as fits. Pixel-perfect: whole pixels per dot, at least one.
        val uPx = if (responsive) fit else floor(fit).coerceAtLeast(1f).let { if (fit < 1f) fit else it }
        // Update 182: the background fills the whole screen (wider up to 16:9, taller as there's room); the design sits
        // in its middle — it used to be a small 4:3 picture letterboxed on the Thor's screens.
        val width = (wPx / uPx).coerceIn(POCKET_W, if (responsive) POCKET_H * 16f / 9f else wPx / uPx)
        val fullH = (hPx / uPx).coerceAtLeast(designH)
        val scope = PocketScope((uPx / density).dp, width, density, fullH, ((fullH - designH) / 2f).coerceAtLeast(0f))
        Box(Modifier.size(scope.d(scope.width), scope.d(fullH))) {
            // The design in the middle of the screen's height; backgrounds reach the whole screen with atFull.
            Box(Modifier.offset(y = scope.d(scope.cy)).size(scope.d(scope.width), scope.d(designH))) {
                scope.content()
            }
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
/** Update 182: redrawn on a 16 × 16 grid, symmetric and even (one dot per cell at the usual sizes). '#' ink, '+' your colour, 'o' light grey. */
internal object PocketIcons {
    val GEAR = listOf(
        "................",
        "......####......",
        "......#++#......",
        "..###.#++#.###..",
        "..#+###++###+#..",
        "..##++++++++##..",
        "...#+++##+++#...",
        ".###++#..#++###.",
        ".#+++#....#+++#.",
        ".###++#..#++###.",
        "...#+++##+++#...",
        "..##++++++++##..",
        "..#+###++###+#..",
        "..###.#++#.###..",
        "......#++#......",
        "......####......"
    )
    val GRAPH = listOf(
        "................",
        "............###.",
        "............#+#.",
        "........###.#+#.",
        "........#+#.#+#.",
        "........#+#.#+#.",
        "....###.#+#.#+#.",
        "....#+#.#+#.#+#.",
        "....#+#.#+#.#+#.",
        "###.#+#.#+#.#+#.",
        "#+#.#+#.#+#.#+#.",
        "#+#.#+#.#+#.#+#.",
        "#+#.#+#.#+#.#+#.",
        "###.###.###.###.",
        "................",
        "################"
    )
    val MOON = listOf(
        "................",
        "................",
        ".....##.....+...",
        "...###.....+++..",
        "..####......+...",
        ".#####..........",
        ".#####..........",
        ".#####..........",
        "######..........",
        "#######.........",
        ".#######........",
        ".############...",
        "..##########....",
        "...########.....",
        "....######......",
        "................"
    )
    val TROPHY = listOf(
        "................",
        "...##########...",
        ".####++++++####.",
        "##.#++++++++#.##",
        "#..#++++++++#..#",
        "#..#++++++++#..#",
        "##.#++++++++#.##",
        ".###++++++++###.",
        "....#++++++#....",
        ".....##++##.....",
        "......####......",
        ".......##.......",
        ".......##.......",
        ".....######.....",
        "....########....",
        "....########...."
    )
    val ALL = listOf(
        "................",
        ".######..######.",
        ".#++++#..#oooo#.",
        ".#++++#..#oooo#.",
        ".#++++#..#oooo#.",
        ".#++++#..#oooo#.",
        ".######..######.",
        "................",
        "................",
        ".######..######.",
        ".#oooo#..#++++#.",
        ".#oooo#..#++++#.",
        ".#oooo#..#++++#.",
        ".#oooo#..#++++#.",
        ".######..######.",
        "................"
    )
    val CARD = listOf(
        "................",
        "..###########...",
        "..#.........##..",
        "..#.#######..#..",
        "..#.#ooooo#..#..",
        "..#.#ooooo#..#..",
        "..#.#ooooo#..#..",
        "..#.#######..#..",
        "..#..........#..",
        "..#..........#..",
        "..#.#.#.#.#..#..",
        "..#.#.#.#.#..#..",
        "..############..",
        "................",
        "................",
        "................"
    )
    val PAD = listOf(
        "................",
        "................",
        "................",
        "..############..",
        ".##..........##.",
        "#....#....+...#.",
        "#...###..+.+..#.",
        "#....#....+...#.",
        "#.............#.",
        ".##...####...##.",
        "..###.#..#.###..",
        "................",
        "................",
        "................",
        "................",
        "................"
    )
    val BACK = listOf(
        "................",
        "................",
        ".....#..........",
        "....##..........",
        "...###########..",
        "..############..",
        "...###########..",
        "....##......##..",
        ".....#......##..",
        "............##..",
        "............##..",
        "....##########..",
        "....##########..",
        "................",
        "................",
        "................"
    )
    val VOLUME = listOf(
        "................",
        "................",
        ".......#........",
        "......##...+....",
        ".....###....+...",
        ".#######..+..+..",
        ".#######...+.+..",
        ".#######...+.+..",
        ".#######...+.+..",
        ".#######..+..+..",
        ".....###....+...",
        "......##...+....",
        ".......#........",
        "................",
        "................",
        "................"
    )
    val PLAY = listOf(
        "................",
        "................",
        "................",
        ".....#..........",
        ".....###........",
        ".....#####......",
        ".....#######....",
        ".....#########..",
        ".....#######....",
        ".....#####......",
        ".....###........",
        ".....#..........",
        "................",
        "................",
        "................",
        "................"
    )
    val PAUSE = listOf(
        "................",
        "................",
        "................",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "....###..###....",
        "................",
        "................",
        "................",
        "................"
    )
    val NEXT = listOf(
        "................",
        "................",
        "................",
        "..#....#....##..",
        "..##...##...##..",
        "..###..###..##..",
        "..####.####.##..",
        "..############..",
        "..####.####.##..",
        "..###..###..##..",
        "..##...##...##..",
        "..#....#....##..",
        "................",
        "................",
        "................",
        "................"
    )
    val PREV = listOf(
        "................",
        "................",
        "................",
        "..##....#....#..",
        "..##...##...##..",
        "..##..###..###..",
        "..##.####.####..",
        "..############..",
        "..##.####.####..",
        "..##..###..###..",
        "..##...##...##..",
        "..##....#....#..",
        "................",
        "................",
        "................",
        "................"
    )
    val SCREEN = listOf(
        "................",
        "..############..",
        "..#++++++++++#..",
        "..#++++++++++#..",
        "..#++++++++++#..",
        "..############..",
        ".......##.......",
        "..############..",
        "..#oooooooooo#..",
        "..#oo#oooo#oo#..",
        "..#oooooooooo#..",
        "..############..",
        "................",
        "................",
        "................",
        "................"
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
            // (Update 182: cell edges on whole pixels, so every dot of the icon is the same size.)
            if (color != null) {
                val x0 = kotlin.math.floor(x * cw); val x1 = kotlin.math.floor((x + 1) * cw)
                val y0 = kotlin.math.floor(y * ch); val y1 = kotlin.math.floor((y + 1) * ch)
                drawRect(color, Offset(x0, y0), Size((x1 - x0).coerceAtLeast(1f), (y1 - y0).coerceAtLeast(1f)))
            }
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

// ---- Update 182: the small tools and the home strip under both Pocket menus ----

/** Spots below the menus (the controller's places), shared by both layouts. Pocket classic's own panels are 0–3. */
internal object PocketSpot {
    const val SET = 4
    const val STATS = 5
    const val MOON = 6
    /** The second screen's own dashboard (two screens only). */
    const val DASH = 7
    /** Pocket ribbon's Achievements tool. */
    const val ACH = 8
    /** The home strip: up to four of your round shortcuts (DOCK + i), then the music buttons. */
    const val DOCK = 10
    const val PREV = 20
    const val PLAY = 21
    const val NEXT = 22
}

/** Update 182: Layout options › Home strip: shortcuts, music and the status under the menu. */
internal fun pocketStripOn(): Boolean = Features.pocketStrip.state.value

/** The strip's height in dots (with its gap above). */
internal const val POCKET_STRIP_H = 28f

/** The small tools along the bottom of the menu: Settings, (Achievements,) Statistics, (Second screen,) Sleep. */
internal fun pocketTools(c: StyleCtx, ribbon: Boolean): List<Pair<Int, List<String>>> = buildList {
    add(PocketSpot.SET to PocketIcons.GEAR)
    if (ribbon) add(PocketSpot.ACH to PocketIcons.TROPHY)
    add(PocketSpot.STATS to PocketIcons.GRAPH)
    if (twinOnSecond(c.state)) add(PocketSpot.DASH to PocketIcons.SCREEN)
    add(PocketSpot.MOON to PocketIcons.MOON)
}

/** Where the [i]th of [n] tools sits (its left edge, dots in the 256 design; each tool 18 dots). */
internal fun pocketToolX(i: Int, n: Int): Float = if (n <= 1) 119f else 4f + i * (230f / (n - 1))

/** The strip's controls with their centre x (design dots), left to right. */
internal fun pocketStripSpots(c: StyleCtx): List<Pair<Int, Float>> = buildList {
    c.shortcuts.take(4).forEachIndexed { i, _ -> add(PocketSpot.DOCK + i to (4f + i * 21f + 9f)) }
    if (c.state.nowPlaying != null) {
        add(PocketSpot.PREV to 166f); add(PocketSpot.PLAY to 180f); add(PocketSpot.NEXT to 194f)
    }
}

/** What a tool or strip control does. */
internal fun pocketShared(c: StyleCtx, spot: Int) {
    when (spot) {
        PocketSpot.SET -> c.actions.settings()
        PocketSpot.STATS -> c.actions.stats()
        PocketSpot.MOON -> c.actions.screensaver()
        PocketSpot.DASH -> TwinScreens.dashboard = true
        PocketSpot.ACH -> { val g = c.game; if (g != null) c.actions.achievements(g) else c.actions.achievementsHub() }
        PocketSpot.PREV -> c.actions.mediaPrevious()
        PocketSpot.PLAY -> c.actions.mediaPlayPause()
        PocketSpot.NEXT -> c.actions.mediaNext()
        else -> if (spot in PocketSpot.DOCK until PocketSpot.DOCK + 4) c.shortcuts.getOrNull(spot - PocketSpot.DOCK)?.onClick?.invoke()
    }
}

/** A tool: its 16-dot icon in a flat white square; the corner brackets while it's the one you're on. */
@Composable
internal fun PocketToolButton(
    s: PocketScope, x: Float, y: Float, icon: List<String>, ink: Color, accent: Color, lit: Boolean, pressed: Boolean, onTap: () -> Unit
) {
    val tap = androidx.compose.runtime.rememberUpdatedState(onTap)
    with(s) {
        val uPx = d(1f).value * density
        Box(Modifier.at(x, y, 18f, 18f).tapOnly { tap.value() }) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(if (pressed) Color(0xFFDCE6F3) else Color.White)
                pocketOutline(uPx, Color(0xFFB9BEC6))
                val i = uPx
                drawContext.transform.translate(i, i)
                val inner = Size(size.width - 2 * i, size.height - 2 * i)
                // The icon in the 16 dots inside the outline.
                pocketIconIn(icon, ink, accent, inner)
                drawContext.transform.translate(-i, -i)
                if (lit) pocketBrackets(uPx, accent, len = 4f, thick = 1f, out = 1f)
            }
        }
    }
}

/** [pocketIcon] into a box of [box] at the current origin. */
internal fun DrawScope.pocketIconIn(icon: List<String>, ink: Color, accent: Color, box: Size, light: Color = Color(0xFFB8BEC6)) {
    val rows = icon.size
    val cols = icon.maxOf { it.length }
    val cw = box.width / cols
    val ch = box.height / rows
    icon.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            val color = when (c) { '#' -> ink; '+' -> accent; 'o' -> light; else -> null }
            if (color != null) {
                val x0 = floor(x * cw); val x1 = floor((x + 1) * cw)
                val y0 = floor(y * ch); val y1 = floor((y + 1) * ch)
                drawRect(color, Offset(x0, y0), Size((x1 - x0).coerceAtLeast(1f), (y1 - y0).coerceAtLeast(1f)))
            }
        }
    }
}

/**
 * Update 182: the home strip under a Pocket menu (Layout options › Home strip): your first round shortcuts, the music
 * playing with ◀ ⏯ ▶, the time and the battery — so the menu has what the home screen has. [y]: its top in design dots.
 */
@Composable
internal fun PocketHomeStrip(
    s: PocketScope, c: StyleCtx, y: Float, accent: Color, font: FontFamily,
    ink: Color, muted: Color, edge: Color, paper: Color, lit: Int?
) {
    val np = c.state.nowPlaying
    val battery = rememberBatteryInfo()
    val time = rememberTwinTime(seconds = false)
    val live = androidx.compose.runtime.rememberUpdatedState(c)
    with(s) {
        val uPx = d(1f).value * density
        // The strip's own band across the whole screen.
        Canvas(Modifier.atScreen(0f, y + 2f, width, POCKET_STRIP_H - 2f)) {
            drawRect(paper)
            drawRect(edge, Offset.Zero, Size(size.width, uPx.coerceAtLeast(1f)))
        }
        val top = y + 6f
        // Your round shortcuts.
        c.shortcuts.take(4).forEachIndexed { i, sc ->
            val id = PocketSpot.DOCK + i
            Box(Modifier.at(4f + i * 21f, top, 18f, 18f).tapOnly { PocketNav.spot = id; live.value.shortcuts.getOrNull(i)?.onClick?.invoke() }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.White); pocketOutline(uPx, edge)
                    if (lit == id) pocketBrackets(uPx, accent, len = 4f, thick = 1f, out = 1f)
                }
                androidx.compose.material3.Icon(
                    sc.icon, sc.label, tint = androidx.compose.ui.graphics.lerp(sc.tint, Color.Black, 0.25f),
                    modifier = Modifier.size(d(12f))
                )
            }
        }
        // The music playing.
        if (np != null) {
            Box(Modifier.at(90f, top, 18f, 18f)) {
                Canvas(Modifier.fillMaxSize()) { drawRect(Color.White); pocketOutline(uPx, edge) }
                np.art?.let { bmp ->
                    val img = androidx.compose.runtime.remember(bmp) { bmp.asImageBitmapSafe() }
                    if (img != null) androidx.compose.foundation.Image(img, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(Modifier.padding(d(1f))))
                }
            }
            com.shelf.launcher.ui.PocketText(s, np.title ?: "Music", 111f, top, 48f, 10f, 6.5f, ink, font, androidx.compose.ui.text.style.TextAlign.Start)
            com.shelf.launcher.ui.PocketText(s, np.artist ?: "", 111f, top + 9f, 48f, 9f, 5.5f, muted, font, androidx.compose.ui.text.style.TextAlign.Start)
            listOf(PocketSpot.PREV to PocketIcons.PREV, PocketSpot.PLAY to (if (np.isPlaying) PocketIcons.PAUSE else PocketIcons.PLAY), PocketSpot.NEXT to PocketIcons.NEXT)
                .forEachIndexed { i, (id, icon) ->
                    Box(Modifier.at(160f + i * 14f, top + 3f, 12f, 12f).tapOnly { PocketNav.spot = id; pocketShared(live.value, id) }) {
                        Canvas(Modifier.fillMaxSize()) {
                            pocketIconIn(icon, ink, accent, size)
                            if (lit == id) pocketBrackets(uPx, accent, len = 3f, thick = 1f, out = 1.5f)
                        }
                    }
                }
        } else com.shelf.launcher.ui.PocketText(s, "No music playing", 90f, top, 108f, 18f, 6.5f, muted, font, androidx.compose.ui.text.style.TextAlign.Start)
        // The time and the battery.
        val day = com.shelf.launcher.ui.pocketDay(time.longValue, java.util.TimeZone.getDefault())
        com.shelf.launcher.ui.PocketText(s, "%d:%02d".format(day.hour, day.minute), 202f, top, 32f, 18f, 8f, ink, font, androidx.compose.ui.text.style.TextAlign.End)
        Canvas(Modifier.at(237f, top + 5.5f, 14f, 7f)) { pocketBattery(uPx, battery.percent, ink) }
    }
}

private fun android.graphics.Bitmap.asImageBitmapSafe(): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching { if (isRecycled) null else asImageBitmap() }.getOrNull()

/**
 * Update 182: the controller across rows of places ([rows]: each its spots with their centre x): ◀ ▶ within a row,
 * ▲ ▼ to the nearest place in the row above / below. Returns the new spot, or null when there's no row that way.
 */
internal fun pocketRowStep(rows: List<List<Pair<Int, Float>>>, spot: Int, dir: Int): Int? {
    val r = rows.indexOfFirst { row -> row.any { it.first == spot } }
    if (r < 0) return rows.firstOrNull()?.firstOrNull()?.first
    val row = rows[r]
    val i = row.indexOfFirst { it.first == spot }
    val x = row[i].second
    return when (dir) {
        0 -> row.getOrNull(i - 1)?.first
        1 -> row.getOrNull(i + 1)?.first
        2 -> rows.subList(0, r).lastOrNull { it.isNotEmpty() }?.minByOrNull { kotlin.math.abs(it.second - x) }?.first
        else -> rows.drop(r + 1).firstOrNull { it.isNotEmpty() }?.minByOrNull { kotlin.math.abs(it.second - x) }?.first
    }
}
