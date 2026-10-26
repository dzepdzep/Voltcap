package com.shelf.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 333: the status bar dressed for the layout it's on (Settings › Look & feel › "Status bar matches the layout", on).
 * One status bar (HomeChrome.StatusBar, everything it shows and does unchanged); per layout only how it's drawn: a soft
 * shade from the edge (the plain look), a capsule round the clock and icons, a full-width toolbar, an LCD readout, a
 * paper label… its colours, corners, type. Layouts with a top bar of their own (Pocket layouts, Spotlight's Home menu,
 * Hero tiles' / Twin showcase's status pill, Channel grid's clock row) keep theirs: Voltcap's isn't drawn there at all
 * (AppearanceSettings.statusOn), so nothing shows twice.
 */

/** How the bar is backed. */
internal enum class BarBacking {
    /** The plain look: a soft shade down from the screen's edge, no box. */
    SHADE,
    /** Nothing behind it at all (the type carries itself, a soft shadow under it). */
    NONE,
    /** A capsule (or a box with [StatusLook.corner]) round the clock and icons only. */
    CAPSULE,
    /** A band across the whole width, its own colour, a hairline along its inner edge. */
    TOOLBAR
}

internal data class StatusLook(
    val backing: BarBacking,
    /** The capsule's / toolbar's colour (with its alpha); for SHADE the shade's darkness is [shade]. */
    val bg: Color = Color.Transparent,
    /** Icons and text. */
    val ink: Color = Color.White,
    /** The points, the progress line and readouts. */
    val accent: Color = Color.Unspecified,
    /** A rim round the capsule, or the toolbar's hairline. */
    val rim: Color? = null,
    val rimWidth: Dp = 1.dp,
    val corner: Dp = 0.dp,
    /** The darkness of the edge shade (SHADE, and a little under CAPSULE / NONE on busy pictures). */
    val shade: Float = 0.32f,
    /** Digits drawn as a readout (LCD, OSD). */
    val mono: Boolean = false,
    val weight: FontWeight = FontWeight.Medium
)

/** The plain look (and what every layout had before update 333). */
internal val PlainStatusLook = StatusLook(BarBacking.SHADE)

/** Update 333: [layout]'s status bar. [paleBehind]: a pale backdrop under it (update 329), so a shaded bar shades more. */
@Composable
internal fun statusLookFor(layout: HomeLayout, paleBehind: Boolean): StatusLook {
    if (!Features.statusMatchesLayout.state.value) return PlainStatusLook.copy(shade = if (paleBehind) 0.62f else 0.32f)
    val acc = ShelfTheme.accent
    return when (layout) {
        // Clean, minimal, compact.
        HomeLayout.CLASSIC, HomeLayout.TILES, HomeLayout.GRID ->
            StatusLook(BarBacking.SHADE, shade = if (paleBehind) 0.55f else 0.26f)
        // Cinematic: a whisper of shade over the picture.
        HomeLayout.HERO, HomeLayout.SPOT, HomeLayout.CASEFLOW ->
            StatusLook(BarBacking.SHADE, shade = if (paleBehind) 0.5f else 0.18f, weight = FontWeight.Normal)
        // Hero tiles / Twin showcase (with Voltcap's bar on): the same dark rounded pill as their own.
        HomeLayout.HERO_TILES, HomeLayout.TWIN_SHOWCASE ->
            StatusLook(BarBacking.CAPSULE, bg = Color.Black.copy(alpha = 0.45f), corner = 50.dp, shade = 0f)
        // Icon bar: a sleek toolbar like its round icons.
        HomeLayout.BAR -> StatusLook(BarBacking.CAPSULE, bg = Color(0xCC15181E), rim = Color.White.copy(alpha = 0.12f), corner = 14.dp, shade = 0.12f)
        // Dashboard: frosted glass like its panels.
        HomeLayout.DASH -> StatusLook(BarBacking.CAPSULE, bg = Color.White.copy(alpha = 0.14f), rim = Color.White.copy(alpha = 0.28f), corner = 14.dp, shade = 0.16f)
        // Live tiles: flat, squared.
        HomeLayout.LIVE -> StatusLook(BarBacking.CAPSULE, bg = if (paleBehind) Color(0xE6222222) else Color(0xB3000000), corner = 0.dp, shade = 0f, weight = FontWeight.Normal)
        // Side menu: dark translucent with the theme's trim.
        HomeLayout.SIDE -> StatusLook(BarBacking.TOOLBAR, bg = Color(0x99080A0E), rim = acc.copy(alpha = 0.7f), shade = 0f)
        // Cross menu: floating, nothing behind it.
        HomeLayout.CROSS -> StatusLook(BarBacking.NONE, shade = 0.12f, weight = FontWeight.Light)
        // Console hub / Resume: a console dashboard's glass.
        HomeLayout.HUB, HomeLayout.RESUME -> StatusLook(BarBacking.CAPSULE, bg = Color(0x8C0C1018), rim = Color.White.copy(alpha = 0.16f), corner = 20.dp, shade = 0.1f)
        // Channel grid (its status bar on): soft and pale, rounded.
        HomeLayout.CHAN -> StatusLook(
            BarBacking.CAPSULE, bg = if (paleBehind) Color(0xF2FFFFFF) else Color(0xCC2A313B), ink = if (paleBehind) Color(0xFF4A5461) else Color.White,
            rim = if (paleBehind) Color(0xFFB9C6D6) else Color.White.copy(alpha = 0.2f), rimWidth = 1.5.dp, corner = 20.dp, shade = 0f
        )
        // Library panel: a desktop library's toolbar.
        HomeLayout.LIBRARY -> StatusLook(BarBacking.TOOLBAR, bg = Color(0xD91B1F25), rim = Color.White.copy(alpha = 0.08f), shade = 0f)
        // TV room: a CRT's on-screen display.
        HomeLayout.TV -> StatusLook(BarBacking.NONE, ink = Color(0xFFD7FFD9), accent = Color(0xFF8CFF9E), shade = 0.2f, mono = true)
        // Shelf, Case stacks, Console stack: an understated label strip.
        HomeLayout.SHELF, HomeLayout.STACKS, HomeLayout.CONSOLES ->
            StatusLook(BarBacking.CAPSULE, bg = Color(0xB3211812), rim = Color(0x80A17C55), corner = 4.dp, shade = 0.1f)
        // Disc station: a CD player's LCD.
        HomeLayout.DISCS -> StatusLook(BarBacking.CAPSULE, bg = Color(0xE60E1A12), ink = Color(0xFF8CF5AE), accent = Color(0xFFB8FFCB), rim = Color(0xFF2F4B37), corner = 6.dp, shade = 0f, mono = true)
        // Record crate: a vintage label.
        HomeLayout.CRATE -> StatusLook(BarBacking.CAPSULE, bg = Color(0xF0EFE6D2), ink = Color(0xFF3A2A1E), accent = Color(0xFFB0392B), rim = Color(0xFFB89B6A), corner = 30.dp, shade = 0f)
        // Card binder: a card's header, a little metal.
        HomeLayout.CARDS -> StatusLook(BarBacking.CAPSULE, bg = Color(0xD914161C), accent = Color(0xFFE2C77D), rim = Color(0xFFC9B27A), rimWidth = 1.5.dp, corner = 6.dp, shade = 0.08f)
        // Cardboard box: kraft paper.
        HomeLayout.BOX -> StatusLook(BarBacking.CAPSULE, bg = Color(0xF2C9A877), ink = Color(0xFF3B2A18), accent = Color(0xFF7A2E1B), rim = Color(0xFF8E6E44), corner = 2.dp, shade = 0f)
        // Storage boxes: in the theme's colour.
        // Storage boxes: the box's own wallpaper colours.
        HomeLayout.STORAGE -> (StorageWallTint.color ?: acc).let { w ->
            StatusLook(BarBacking.CAPSULE, bg = mixC(w, Color.Black, 0.45f).copy(alpha = 0.9f), rim = mixC(w, Color.White, 0.35f), rimWidth = 1.5.dp, corner = 10.dp, shade = 0f)
        }
        // Circuit board: a green LCD.
        HomeLayout.CIRCUIT -> StatusLook(BarBacking.CAPSULE, bg = Color(0xE60B2A12), ink = Color(0xFF8CFF9E), accent = Color(0xFFD9FF8C), rim = Color(0xFF2E7D46), corner = 3.dp, shade = 0f, mono = true)
        // Timeline: a thin date strip.
        HomeLayout.TIMELINE -> StatusLook(BarBacking.TOOLBAR, bg = Color(0x66000000), rim = acc.copy(alpha = 0.55f), shade = 0f, weight = FontWeight.Normal)
        // Arcade cabinets: a restrained marquee glow.
        HomeLayout.ARCADE -> StatusLook(BarBacking.CAPSULE, bg = Color(0xD9120A1E), accent = Color(0xFFFF5FD9), rim = Color(0x99FF4FD8), rimWidth = 1.5.dp, corner = 8.dp, shade = 0f)
        // Twin desk: paper instrumentation.
        HomeLayout.TWIN_DESK -> StatusLook(BarBacking.CAPSULE, bg = Color(0xF2FFFFFF), ink = Color(0xFF1D2430), accent = Color(0xFFE5484D), rim = Color(0xFFDDE1E8), corner = 12.dp, shade = 0f)
        // Twin icons: a clean strip like its top screen.
        HomeLayout.TWIN_ICONS -> StatusLook(BarBacking.CAPSULE, bg = Color(0xE6F2F4F8), ink = Color(0xFF26303C), accent = Color(0xFF2E8BFF), corner = 14.dp, shade = 0f)
        // Green Core: dark green glass.
        HomeLayout.GREENCORE -> StatusLook(BarBacking.CAPSULE, bg = Color(0xB30E2A16), ink = Color(0xFFCFFFD8), accent = Color(0xFF6CF29A), rim = Color(0x8039D27A), corner = 10.dp, shade = 0f)
        // Pocket Quest (with Voltcap's bar): blue and gold.
        HomeLayout.POCKET -> StatusLook(BarBacking.CAPSULE, bg = Color(0xE61B2766), accent = Color(0xFFFFD34A), rim = Color(0xFFFFD34A), rimWidth = 1.5.dp, corner = 6.dp, shade = 0f)
        // Cube menu: indigo glass.
        HomeLayout.CUBE -> StatusLook(BarBacking.CAPSULE, bg = Color(0x8C2A2470), rim = Color(0x808C84FF), corner = 16.dp, shade = 0f)
        // Memory card: a 2000s grey menu.
        HomeLayout.MEMCARD -> StatusLook(BarBacking.CAPSULE, bg = Color(0xD92B2F36), ink = Color(0xFFE6E9EE), rim = Color(0x40FFFFFF), corner = 4.dp, shade = 0f, weight = FontWeight.Normal)
        // File select: chunky 1996.
        HomeLayout.FILESELECT -> StatusLook(BarBacking.CAPSULE, bg = Color(0xF21F2A8C), accent = Color(0xFFFFD34A), rim = Color(0xFFFFD34A), rimWidth = 2.dp, corner = 10.dp, shade = 0f, weight = FontWeight.Bold)
        // Airy home: light and soft.
        HomeLayout.AIRY -> StatusLook(BarBacking.CAPSULE, bg = Color(0xBFFFFFFF), ink = Color(0xFF3C4A5A), accent = Color(0xFF2E8BFF), corner = 18.dp, shade = 0f)
        // Night menu: slim moonlit glass.
        HomeLayout.NIGHT -> StatusLook(BarBacking.CAPSULE, bg = Color(0x8C0E1A3A), rim = Color(0x599FB8FF), corner = 6.dp, shade = 0f, weight = FontWeight.Normal)
        // Orbit deck: deep space, a little light at the rim.
        HomeLayout.ORBIT -> StatusLook(BarBacking.CAPSULE, bg = Color(0x9905070F), accent = Color(0xFF8FD3FF), rim = Color(0x596FA8FF), corner = 14.dp, shade = 0f)
        // Layout Studio: the theme's colours (its own palette follows the theme), else the plain look.
        HomeLayout.CUSTOM -> StatusLook(BarBacking.CAPSULE, bg = mixC(acc, Color.Black, 0.7f).copy(alpha = 0.7f), rim = acc.copy(alpha = 0.45f), corner = 12.dp, shade = 0.08f)
        // The Pocket classic / ribbon bars are their own (Voltcap's isn't drawn there); anything else: the plain look.
        else -> PlainStatusLook.copy(shade = if (paleBehind) 0.62f else 0.32f)
    }
}

private fun mixC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** Update 333: [look]'s colours, eased when the layout changes. */
@Composable
internal fun animatedLook(look: StatusLook): StatusLook {
    val bg by animateColorAsState(look.bg, tween(260), label = "barBg")
    val ink by animateColorAsState(look.ink, tween(260), label = "barInk")
    return look.copy(bg = bg, ink = ink)
}

/** The capsule round the clock and icons ([BarBacking.CAPSULE]); nothing for the other backings. */
internal fun Modifier.statusCapsule(look: StatusLook): Modifier =
    if (look.backing != BarBacking.CAPSULE) this
    else {
        val shape = RoundedCornerShape(look.corner)
        this.background(look.bg, shape)
            .then(if (look.rim != null) Modifier.border(look.rimWidth, look.rim, shape) else Modifier)
            .padding(horizontal = if (look.corner >= 14.dp) 10.dp else 8.dp, vertical = 3.dp)
    }

/** The bar's own backing behind everything: the edge shade, the toolbar band and its hairline. */
internal fun Modifier.statusBacking(look: StatusLook, atBottom: Boolean): Modifier = this.drawBehind {
    if (look.shade > 0f) drawRect(
        Brush.verticalGradient(
            if (atBottom) listOf(Color.Transparent, Color.Black.copy(alpha = look.shade))
            else listOf(Color.Black.copy(alpha = look.shade), Color.Transparent),
            startY = 0f, endY = size.height
        )
    )
    if (look.backing == BarBacking.TOOLBAR) {
        drawRect(look.bg)
        look.rim?.let { r ->
            val h = 1.dp.toPx()
            drawRect(r, Offset(0f, if (atBottom) 0f else size.height - h), Size(size.width, h))
        }
    }
}

/** The family for the bar's digits: a readout's monospace, else the layout's own type. */
internal fun StatusLook.family(): FontFamily? = if (mono) FontFamily.Monospace else null

/**
 * Update 333: a layout drawing its own clock on the home screen (Case stacks' panel, Channel grid's bar) marks it while
 * it's there; the status bar then leaves its clock out — never two clocks.
 */
internal object OwnClock {
    var count by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    @Composable
    fun Mark() {
        androidx.compose.runtime.DisposableEffect(Unit) {
            count++
            onDispose { count-- }
        }
    }
}
