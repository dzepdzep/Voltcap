package com.shelf.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Updates 333 / 334: the status bar dressed for the layout it's on (Settings › Look & feel › "Status bar matches the
 * layout", on). One status bar (HomeChrome.StatusBar — everything it shows and does unchanged); per layout how it's put
 * together: what's behind it (nothing, an edge shade, a full-width toolbar, one box, separate small segments, an LCD
 * window, a printed label, a line under it), how its pieces are grouped and spaced and what sits between the groups, its
 * type (size, weight, capitals and spacing, a readout's digits, a pixel or a serif face, a glow), the shape of your
 * picture, and small details of the layout's material (tape, record grooves, circuit traces, a gloss, a spine's band).
 * Layouts with a top bar of their own (Pocket Classic / Ribbon, Spotlight's Home menu, Hero tiles' / Twin showcase's pill,
 * Channel grid's clock row) keep theirs: Voltcap's isn't drawn there (AppearanceSettings.statusOn) — nothing twice.
 */

/** What's behind the bar's pieces. */
internal enum class BarBacking {
    /** A soft shade down from the screen's edge (the plain look). */
    SHADE,
    /** Nothing at all: the type carries itself. */
    NONE,
    /** A band across the whole width, a hairline along its inner edge. */
    TOOLBAR,
    /** One box round the pieces. */
    CAPSULE,
    /** Each group of pieces in its own small box. */
    SEGMENTS,
    /** A dark inset readout window. */
    LCD,
    /** A printed label (paper, card). */
    LABEL,
    /** No box: a fine line under the pieces. */
    UNDERLINE
}

/** The small details of a layout's material, drawn with the bar's box. */
internal enum class BarDecor { NONE, TAPE, GROOVES, TRACES, GLOSS, STRIPE }

internal enum class AvatarShape { ROUNDED, CIRCLE, SQUARE }

internal data class StatusLook(
    val backing: BarBacking,
    /** The box's colour (with alpha); TOOLBAR: the band's. */
    val bg: Color = Color.Transparent,
    /** Icons and text. */
    val ink: Color = Color.White,
    /** Points, the progress line. Unspecified: the theme's. */
    val accent: Color = Color.Unspecified,
    /** The box's rim / the toolbar's hairline / the underline / a spine's band. */
    val rim: Color? = null,
    /** A second rim colour (a card's metal edge, gold to silver). */
    val rim2: Color? = null,
    val rimWidth: Dp = 1.dp,
    val corner: Dp = 0.dp,
    /** Cut corners instead of round ones. */
    val cut: Boolean = false,
    /** The edge shade's darkness (0: none). */
    val shade: Float = 0f,
    val font: FontFamily? = null,
    val weight: FontWeight = FontWeight.Medium,
    val clockWeight: FontWeight = FontWeight.Medium,
    val clockSize: TextUnit = 12.sp,
    val caps: Boolean = false,
    /** Letter spacing, in em. */
    val tracking: Float = 0f,
    val iconSize: Dp = 16.dp,
    /** Room between pieces. */
    val gap: Dp = 8.dp,
    /** Drawn between the groups (time · devices · actions · system · you). */
    val sep: String? = null,
    /** A glow under the type (neon, phosphor). */
    val glow: Color? = null,
    /** A soft dark edge under the type (no box behind it). */
    val shadowed: Boolean = false,
    val avatar: AvatarShape = AvatarShape.ROUNDED,
    val avatarSize: Dp = 26.dp,
    /** A ring round your picture (a console dashboard's). */
    val ring: Color? = null,
    val decor: BarDecor = BarDecor.NONE,
    /** Faint unlit "88:88" behind the time (an LCD's segments). */
    val ghost: Boolean = false,
    /** Battery cells in the ink, not the theme's colours (red only when it's really low). */
    val plainBattery: Boolean = true,
    /** Update 339: in the top left corner instead of the right (Night menu: its list fills the right). */
    val left: Boolean = false
)

/** The plain look (every layout before update 333, and with the switch off). */
internal val PlainStatusLook = StatusLook(BarBacking.SHADE, shade = 0.32f, plainBattery = false)

/** Updates 333 / 334: [layout]'s status bar. [paleBehind]: a pale backdrop under it (update 329). */
@Composable
internal fun statusLookFor(layout: HomeLayout, paleBehind: Boolean): StatusLook {
    if (!Features.statusMatchesLayout.state.value) return PlainStatusLook.copy(shade = if (paleBehind) 0.62f else 0.32f)
    val acc = ShelfTheme.accent
    val pixel = pocketFont()
    return when (layout) {
        // Voltcap row: Voltcap's own — the edge shade only, clean.
        HomeLayout.CLASSIC -> StatusLook(BarBacking.SHADE, shade = if (paleBehind) 0.55f else 0.26f)
        // Tile home: a bolder, bigger time, square picture, wider calm gaps.
        HomeLayout.TILES -> StatusLook(BarBacking.SHADE, shade = if (paleBehind) 0.5f else 0.22f, clockWeight = FontWeight.Bold, clockSize = 14.sp, gap = 11.dp, avatar = AvatarShape.SQUARE)
        // Grid: tight and small over a screen full of covers.
        HomeLayout.GRID -> StatusLook(BarBacking.SHADE, shade = if (paleBehind) 0.5f else 0.3f, iconSize = 14.dp, gap = 6.dp, clockSize = 11.sp, avatarSize = 22.dp)
        // Hero / Spotlight: cinematic — nothing behind, a large light time, fine dots between, a soft dark edge.
        HomeLayout.HERO, HomeLayout.SPOT -> StatusLook(
            BarBacking.NONE, shade = 0.12f, clockSize = 16.sp, clockWeight = FontWeight.Light, weight = FontWeight.Light,
            sep = "·", gap = 9.dp, shadowed = true, avatar = AvatarShape.CIRCLE, avatarSize = 28.dp
        )
        // Case flow: a dark stage — small spaced capitals, nothing else.
        HomeLayout.CASEFLOW -> StatusLook(BarBacking.NONE, ink = Color(0xFFD9DCE2), caps = true, tracking = 0.14f, clockSize = 11.sp, weight = FontWeight.Normal, iconSize = 14.dp, gap = 11.dp, avatar = AvatarShape.CIRCLE, avatarSize = 22.dp)
        // Hero tiles / Twin showcase (their Voltcap bar on): a small dark pill like their own.
        HomeLayout.HERO_TILES, HomeLayout.TWIN_SHOWCASE -> StatusLook(BarBacking.CAPSULE, bg = Color.Black.copy(alpha = 0.5f), corner = 50.dp, iconSize = 14.dp, gap = 6.dp, clockWeight = FontWeight.SemiBold, avatar = AvatarShape.CIRCLE, avatarSize = 22.dp)
        // Icon bar: each group its own round button, like the bar's shortcut icons.
        HomeLayout.BAR -> StatusLook(BarBacking.SEGMENTS, bg = Color(0xB3161A21), rim = Color.White.copy(alpha = 0.16f), corner = 50.dp, gap = 6.dp, iconSize = 15.dp, avatar = AvatarShape.CIRCLE, avatarSize = 24.dp)
        // Dashboard: a frosted panel like its widgets, a big time first, rules between.
        HomeLayout.DASH -> when (Features.dashPanel.state.value) {
            // (Update 339: the panels' own glass — light, dark or the theme's colour.)
            Features.PanelLook.DARK -> StatusLook(BarBacking.CAPSULE, bg = Color(0x99101318), rim = Color.White.copy(alpha = 0.16f), corner = 12.dp, clockSize = 16.sp, clockWeight = FontWeight.SemiBold, sep = "|", decor = BarDecor.GLOSS, avatarSize = 24.dp)
            Features.PanelLook.THEME -> StatusLook(BarBacking.CAPSULE, bg = mixC(acc, Color.Black, 0.55f).copy(alpha = 0.75f), rim = acc.copy(alpha = 0.5f), corner = 12.dp, clockSize = 16.sp, clockWeight = FontWeight.SemiBold, sep = "|", decor = BarDecor.GLOSS, avatarSize = 24.dp)
            else -> StatusLook(BarBacking.CAPSULE, bg = Color.White.copy(alpha = 0.12f), rim = Color.White.copy(alpha = 0.24f), corner = 12.dp, clockSize = 16.sp, clockWeight = FontWeight.SemiBold, sep = "|", decor = BarDecor.GLOSS, avatarSize = 24.dp)
        }
        // Live tiles: flat square tiles, capitals, no rounding anywhere.
        HomeLayout.LIVE -> {
            // (Update 339: the tiles' own colour — the theme's, green, or the game's.)
            val tile = when (Features.liveColor.state.value) {
                Features.LiveColor.GREEN -> Color(0xFF107C10)
                else -> acc
            }
            StatusLook(
                BarBacking.SEGMENTS, bg = if (paleBehind) Color(0xE6303030) else tile.copy(alpha = 0.92f),
                corner = 0.dp, caps = true, weight = FontWeight.Normal, clockWeight = FontWeight.Light, clockSize = 13.sp, gap = 3.dp, avatar = AvatarShape.SQUARE, avatarSize = 24.dp,
                accent = if (paleBehind) tile else Color.White
            )
        }
        // Side menu: a dark band across the top, the theme's trim under it, spaced capitals like a menu.
        HomeLayout.SIDE -> (if (Features.sideGold.state.value) Color(0xFFD9B45A) else acc).let { trim ->
            // (Update 339: the menu's own trim — the theme's colour, or gold.)
            StatusLook(BarBacking.TOOLBAR, bg = Color(0xA6080A0E), rim = trim.copy(alpha = 0.85f), accent = trim, rimWidth = 2.dp, gap = 12.dp, caps = true, tracking = 0.06f, clockSize = 11.sp, iconSize = 15.dp, avatarSize = 22.dp)
        }
        // Cross menu: floating light type, nothing behind (its own icons float the same way).
        HomeLayout.CROSS -> StatusLook(BarBacking.NONE, shade = 0.08f, clockSize = 15.sp, clockWeight = FontWeight.Light, weight = FontWeight.Light, gap = 13.dp, shadowed = true, avatar = AvatarShape.CIRCLE, iconSize = 15.dp)
        // Console hub / Resume: a console dashboard's corner — no box, a bold time, your picture round with the theme's ring.
        HomeLayout.HUB, HomeLayout.RESUME -> StatusLook(
            BarBacking.NONE, shade = 0.16f, shadowed = true, clockSize = 15.sp, clockWeight = FontWeight.Bold, gap = 11.dp,
            avatar = AvatarShape.CIRCLE, avatarSize = 30.dp, ring = acc
        )
        // Channel grid (its Voltcap bar on): a soft pale pill with a thick light rim, grey ink.
        HomeLayout.CHAN -> StatusLook(
            BarBacking.CAPSULE, bg = if (paleBehind) Color(0xF2FFFFFF) else Color(0xCC2A313B), ink = if (paleBehind) Color(0xFF4A5461) else Color.White,
            accent = if (paleBehind) Color(0xFF2E8BFF) else Color.Unspecified,
            rim = if (paleBehind) Color(0xFFB9C6D6) else Color.White.copy(alpha = 0.2f), rimWidth = 2.dp, corner = 50.dp, avatar = AvatarShape.CIRCLE, avatarSize = 24.dp
        )
        // Library panel: a desktop library's toolbar — flat band, small type, square picture, rules between.
        HomeLayout.LIBRARY -> StatusLook(BarBacking.TOOLBAR, bg = Color(0xE61B1F25), rim = Color.White.copy(alpha = 0.1f), clockSize = 11.sp, iconSize = 14.dp, gap = 9.dp, sep = "|", avatar = AvatarShape.SQUARE, avatarSize = 20.dp)
        // TV room: a set's on-screen display — green phosphor capitals with a glow.
        HomeLayout.TV -> StatusLook(
            BarBacking.NONE, ink = Color(0xFFCCFFD2), accent = Color(0xFF8CFF9E), font = FontFamily.Monospace, caps = true,
            clockSize = 14.sp, clockWeight = FontWeight.Bold, glow = Color(0xFF39FF6A), gap = 11.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp, shade = 0.12f
        )
        // Game shelf / Case stacks / Console stack: a spine label — a printed strip, a coloured band at its end.
        HomeLayout.SHELF, HomeLayout.STACKS -> StatusLook(
            BarBacking.LABEL, bg = Color(0xEBE9E2D2), ink = Color(0xFF2A241D), accent = Color(0xFF8A2A1F), corner = 2.dp,
            decor = BarDecor.STRIPE, rim = acc, caps = true, tracking = 0.05f, clockSize = 11.sp, clockWeight = FontWeight.Bold,
            iconSize = 14.dp, gap = 7.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp
        )
        // Console stack: a console's front panel — dark plastic, a lit band at its end, small capitals.
        HomeLayout.CONSOLES -> StatusLook(
            BarBacking.LABEL, bg = Color(0xEB25282E), ink = Color(0xFFE3E6EA), accent = acc, corner = 3.dp, decor = BarDecor.STRIPE, rim = acc,
            caps = true, tracking = 0.06f, clockSize = 11.sp, clockWeight = FontWeight.Bold, iconSize = 14.dp, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp
        )
        // Disc station: a CD player's LCD window — green digits, unlit 88:88 behind.
        HomeLayout.DISCS -> StatusLook(
            // (Update 339: the player's dark panels and the theme's colour lit in the readout, as the layout has them.)
            BarBacking.LCD, bg = mixC(ShelfTheme.colors.surface, Color.Black, 0.45f).copy(alpha = 0.95f), ink = mixC(acc, Color.White, 0.55f), accent = acc,
            rim = mixC(acc, Color.Black, 0.55f), rimWidth = 1.5.dp, corner = 4.dp, font = FontFamily.Monospace, clockSize = 14.sp, clockWeight = FontWeight.Bold,
            ghost = true, iconSize = 14.dp, gap = 7.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp, glow = acc
        )
        // Record crate: a record's centre label — cream, a serif face, printed rings.
        HomeLayout.CRATE -> StatusLook(
            BarBacking.LABEL, bg = Color(0xF2EFE6D2), ink = Color(0xFF3A2A1E), accent = Color(0xFFB0392B), rim = Color(0xFFB89B6A),
            corner = 50.dp, font = FontFamily.Serif, clockSize = 13.sp, clockWeight = FontWeight.Bold, decor = BarDecor.GROOVES,
            avatar = AvatarShape.CIRCLE, avatarSize = 22.dp, iconSize = 15.dp
        )
        // Card binder: a card's header strip — a metal edge (gold to silver), small capitals.
        HomeLayout.CARDS -> StatusLook(
            BarBacking.LABEL, bg = Color(0xE614161C), accent = Color(0xFFE2C77D), rim = Color(0xFFE2C77D), rim2 = Color(0xFFB9C2CC),
            rimWidth = 1.5.dp, corner = 5.dp, caps = true, tracking = 0.08f, clockSize = 11.sp, clockWeight = FontWeight.Bold, gap = 7.dp, avatarSize = 22.dp
        )
        // Cardboard box: a kraft paper label, taped down, typed.
        HomeLayout.BOX -> Features.boxCardboard.state.value.let { b ->
            // (Update 339: a label of the box's own cardboard — brown, white or dark.)
            val dark = b == Features.BoxCardboard.DARK
            StatusLook(
                BarBacking.LABEL, bg = Color(b.top).copy(alpha = 0.95f), ink = if (dark) Color(0xFFEDE4D6) else Color(0xFF34240F), accent = if (dark) Color(0xFFE0A96A) else Color(0xFF7A2E1B), corner = 1.dp,
                font = FontFamily.Monospace, decor = BarDecor.TAPE, clockSize = 12.sp, clockWeight = FontWeight.Bold, iconSize = 14.dp, gap = 7.dp,
                avatar = AvatarShape.SQUARE, avatarSize = 20.dp
            )
        }
        // Storage boxes: small tabs in the box's own wallpaper colours.
        HomeLayout.STORAGE -> (StorageWallTint.color ?: acc).let { w ->
            // (Update 339: pale tabs like the boxes' own white panels, edged in the box's wallpaper colour.)
            StatusLook(BarBacking.SEGMENTS, bg = Color(0xF2F6F8FB), ink = Color(0xFF263040), accent = mixC(w, Color.Black, 0.25f), rim = mixC(w, Color.Black, 0.1f), rimWidth = 1.5.dp, corner = 7.dp, gap = 5.dp, clockWeight = FontWeight.Bold, iconSize = 15.dp, avatarSize = 22.dp)
        }
        // Circuit board: a green chip readout, cut corners, faint traces.
        HomeLayout.CIRCUIT -> when (Features.circuitColors.state.value) {
            // (Update 339: the board's own colours — the theme's, the green board, or the black one.)
            Features.CircuitColors.GREEN -> StatusLook(
                BarBacking.LCD, bg = Color(0xF00B2A12), ink = Color(0xFF8CFF9E), accent = Color(0xFFD9FF8C), rim = Color(0xFF2E7D46),
                corner = 5.dp, cut = true, font = FontFamily.Monospace, decor = BarDecor.TRACES, iconSize = 14.dp, gap = 7.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp
            )
            Features.CircuitColors.DARK -> StatusLook(
                BarBacking.LCD, bg = Color(0xF00E1013), ink = Color(0xFFDCE2EA), accent = acc, rim = Color(0xFF2A2E35),
                corner = 5.dp, cut = true, font = FontFamily.Monospace, decor = BarDecor.TRACES, iconSize = 14.dp, gap = 7.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp
            )
            Features.CircuitColors.THEME -> StatusLook(
                BarBacking.LCD, bg = ShelfTheme.colors.surface.copy(alpha = 0.95f), ink = mixC(acc, Color.White, 0.5f), accent = acc, rim = ShelfTheme.colors.surfaceRaised,
                corner = 5.dp, cut = true, font = FontFamily.Monospace, decor = BarDecor.TRACES, iconSize = 14.dp, gap = 7.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp
            )
        }
        // Timeline: a date strip — no box, spaced capitals, the theme's ruled line with ticks under it.
        HomeLayout.TIMELINE -> StatusLook(BarBacking.UNDERLINE, rim = acc, rimWidth = 2.dp, caps = true, tracking = 0.1f, clockSize = 11.sp, weight = FontWeight.Normal, clockWeight = FontWeight.Bold, gap = 10.dp, shade = 0.18f, sep = "/", avatarSize = 22.dp)
        // Arcade cabinets: a marquee — dark glass, neon capitals.
        // Update 348: the arcade's LED readout — a smoked panel in a dark metal frame, amber pixel digits (unlit segments
        // behind the time), glowing a little; "Your theme" room: the theme-coloured capsule as before.
        HomeLayout.ARCADE -> if (Features.arRoom.state.value != Features.ArRoom.THEME) StatusLook(
            BarBacking.LCD, bg = Color(0xF00B0B0E), ink = Color(0xFFFFB84D), accent = Color(0xFFFFB84D), rim = Color(0xFF3B3D44), rimWidth = 1.5.dp,
            corner = 3.dp, font = pixel, clockSize = 13.sp, clockWeight = FontWeight.Bold, ghost = true, glow = Color(0xFFFF8A1E),
            iconSize = 15.dp, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp
        ) else StatusLook(
            // (Update 339: the cabinets' own colour — the theme's — lit like their marquee.)
            BarBacking.CAPSULE, bg = mixC(acc, Color.Black, 0.82f).copy(alpha = 0.9f), ink = mixC(acc, Color.White, 0.8f), accent = acc, rim = acc.copy(alpha = 0.7f),
            rimWidth = 1.5.dp, corner = 4.dp, caps = true, tracking = 0.1f, glow = acc, clockWeight = FontWeight.Bold, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp
        )
        // Twin desk: instruments printed on the paper — no box, dark ink, thin rules between.
        HomeLayout.TWIN_DESK -> StatusLook(BarBacking.NONE, ink = Color(0xFF1D2430), accent = Color(0xFFE5484D), sep = "|", font = FontFamily.Monospace, clockSize = 12.sp, clockWeight = FontWeight.Bold, iconSize = 15.dp, gap = 9.dp, avatar = AvatarShape.CIRCLE, avatarSize = 22.dp)
        // Twin icons: a clean pale strip like its top screen.
        HomeLayout.TWIN_ICONS -> if (Features.twinIconsTone.state.value == Features.TwinTone.LIGHT)
            // (Update 339: its top screen's own tone — light, or the theme-tinted dark one — and the theme's colour.)
            StatusLook(BarBacking.TOOLBAR, bg = Color(0xE6EEF1F5), ink = Color(0xFF26303C), accent = acc, rim = Color(0x26000000), iconSize = 15.dp, gap = 9.dp, avatarSize = 22.dp)
        else StatusLook(BarBacking.TOOLBAR, bg = Color(0x80161C27), ink = Color.White.copy(alpha = 0.92f), accent = acc, rim = Color.White.copy(alpha = 0.12f), iconSize = 15.dp, gap = 9.dp, avatarSize = 22.dp)
        // Green Core: dark green glass, cut corners, green capitals.
        HomeLayout.GREENCORE -> StatusLook(BarBacking.CAPSULE, bg = Color(0xB30E2A16), ink = Color(0xFFCFFFD8), accent = Color(0xFF6CF29A), rim = Color(0x9939D27A), corner = 8.dp, cut = true, caps = true, tracking = 0.08f, clockSize = 11.sp, clockWeight = FontWeight.Bold, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp)
        // Pocket Quest (with Voltcap's bar): each group in its own blue window with a gold edge, pixel type.
        HomeLayout.POCKET -> StatusLook(BarBacking.SEGMENTS, bg = Color(0xF21B2766), accent = Color(0xFFFFD34A), rim = Color(0xFFFFD34A), rimWidth = 1.5.dp, corner = 3.dp, font = pixel, clockSize = 12.sp, gap = 5.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp)
        // Cube menu: small indigo cubes, one per group.
        HomeLayout.CUBE -> Features.cubeTint.state.value.let { t ->
            // (Update 339: the cube's own tint — indigo, lilac, smoke or the theme's.)
            val tint = if (t == Features.CubeTint.THEME) acc else Color(t.argb)
            StatusLook(BarBacking.SEGMENTS, bg = mixC(tint, Color.Black, 0.55f).copy(alpha = 0.7f), rim = mixC(tint, Color.White, 0.3f).copy(alpha = 0.6f), accent = mixC(tint, Color.White, 0.45f), rimWidth = 1.5.dp, corner = 6.dp, gap = 5.dp, avatar = AvatarShape.ROUNDED, clockWeight = FontWeight.Bold, avatarSize = 22.dp)
        }
        // Memory card: the 2000s browser's pale grey — no box, its dark grey type.
        HomeLayout.MEMCARD -> if (Features.mcBack.state.value == Features.McBack.THEME)
            // (Update 339: on its grey the dark grey type and the browser's gold; on the theme's background, light.)
            StatusLook(BarBacking.NONE, ink = Color(0xFFF2F4F7), accent = acc, shadowed = true, weight = FontWeight.Normal, clockWeight = FontWeight.Normal, clockSize = 13.sp, gap = 10.dp, avatar = AvatarShape.SQUARE, avatarSize = 24.dp)
        else StatusLook(BarBacking.NONE, ink = Color(0xFF3A414B), accent = Color(0xFF9C6408), weight = FontWeight.Normal, clockWeight = FontWeight.Normal, clockSize = 13.sp, gap = 10.dp, avatar = AvatarShape.SQUARE, avatarSize = 24.dp)
        // File select: chunky — a thick gold-rimmed blue box, bold capitals.
        HomeLayout.FILESELECT -> StatusLook(BarBacking.CAPSULE, bg = Color(0xF21F2A8C), accent = Color(0xFFFFD34A), rim = Color(0xFFFFD34A), rimWidth = 2.5.dp, corner = 6.dp, caps = true, weight = FontWeight.Bold, clockWeight = FontWeight.Black, clockSize = 13.sp, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp)
        // Airy home: its two-tone pixel display — no box, pixel type in its slate ink.
        HomeLayout.AIRY -> StatusLook(BarBacking.NONE, ink = Color(0xFF3C4A5A), accent = Color(Features.ahAccent.state.value.argb), font = pixel, clockSize = 12.sp, gap = 9.dp, avatar = AvatarShape.CIRCLE, avatarSize = 22.dp)
        // Night menu: moonlit type, nothing behind (its title sits just under the bar).
        HomeLayout.NIGHT -> Features.nmColor.state.value.let { n ->
            // (Update 339: its own night colour — midnight, crimson, emerald — and up in the left corner, Toni: the menu's
            // slanted list fills the right.)
            StatusLook(BarBacking.NONE, ink = Color(0xFFEAF2FF), accent = Color(n.hi), glow = Color(n.hi).copy(alpha = 0.6f), weight = FontWeight.Light, clockWeight = FontWeight.Normal, clockSize = 13.sp, gap = 10.dp, sep = "·", avatar = AvatarShape.CIRCLE, avatarSize = 22.dp, left = true)
        }
        // Orbit deck: deep space — no box, light type, glowing dots between.
        HomeLayout.ORBIT -> Features.odColor.state.value.let { o ->
            // (Update 339: the deck's own space colour.)
            StatusLook(BarBacking.NONE, accent = mixC(Color(o.glow), Color.White, 0.25f), glow = Color(o.glow), weight = FontWeight.Light, clockWeight = FontWeight.Light, clockSize = 14.sp, tracking = 0.06f, sep = "•", gap = 10.dp, avatar = AvatarShape.CIRCLE, avatarSize = 24.dp)
        }
        // Update 344, Blades: no box, light type over the dark room, the blades' own colour for the points.
        HomeLayout.BLADES -> bladePalette().let { b ->
            StatusLook(BarBacking.NONE, ink = Color(0xFFF2F6EE), accent = b.glow, glow = b.hi.copy(alpha = 0.5f), weight = FontWeight.Normal, clockWeight = FontWeight.Light, clockSize = 15.sp, gap = 10.dp, sep = "·", avatar = AvatarShape.SQUARE, avatarSize = 24.dp)
        }
        // Update 344, Bubble home: a handheld's slim bar — no box, light type with a soft shadow over the wallpaper.
        HomeLayout.BUBBLES -> StatusLook(BarBacking.NONE, ink = Color.White, accent = Color(0xFF7FD0FF), shadowed = true, weight = FontWeight.Medium, clockWeight = FontWeight.Medium, clockSize = 13.sp, gap = 9.dp, avatar = AvatarShape.CIRCLE, avatarSize = 22.dp)
        // Update 344, Pocket camera: its four shades and pixel type, each group in a small chunky box.
        HomeLayout.CAMERA -> Features.pcPalette.state.value.shades.let { s ->
            StatusLook(BarBacking.SEGMENTS, bg = Color(s[1]), ink = Color(s[3]), accent = Color(s[2]), rim = Color(s[0]), rimWidth = 2.dp, corner = 0.dp, font = pixel, clockSize = 12.sp, gap = 5.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp)
        }
        // Update 344, Panel dashboard: a clean light strip in the dashboard's own ink.
        HomeLayout.PANELS -> panelPalette().let { pp ->
            StatusLook(BarBacking.NONE, ink = pp.ink, accent = pp.accent, shadowed = pp.ink.luminance() > 0.5f, weight = FontWeight.Normal, clockWeight = FontWeight.Light, clockSize = 15.sp, gap = 10.dp, avatar = AvatarShape.ROUNDED, avatarSize = 24.dp)
        }
        // Update 344, Game magazine: a printed strip — the issue's paper colour, its ink and accent.
        HomeLayout.MAGAZINE -> magStyle(Features.mgStyle.state.value).let { m ->
            StatusLook(BarBacking.CAPSULE, bg = m.paper.copy(alpha = 0.92f), ink = m.ink, accent = m.accent, rim = m.ink, rimWidth = 1.5.dp, corner = 0.dp, font = m.head, caps = true, tracking = 0.06f, clockWeight = FontWeight.Black, clockSize = 13.sp, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp)
        }
        // Update 344, Rental shop: a black shop-sign strip with the neon's colour for the points.
        HomeLayout.SHOP -> StatusLook(BarBacking.CAPSULE, bg = Color(0xE6121212), ink = Color(0xFFFFF4E0), accent = Color(0xFFFF4FD8), rim = Color(0xFFFF4FD8).copy(alpha = 0.7f), rimWidth = 1.5.dp, corner = 4.dp, caps = true, tracking = 0.08f, glow = Color(0xFFFF4FD8), clockWeight = FontWeight.Black, clockSize = 13.sp, gap = 8.dp, avatar = AvatarShape.SQUARE, avatarSize = 22.dp)
        // Update 344, Terminal: the monitor's own phosphor, fixed-width capitals, a reverse-video box.
        HomeLayout.TERMINAL -> termColors(Features.tmTheme.state.value).let { t ->
            StatusLook(BarBacking.SEGMENTS, bg = t.bg, ink = t.fg, accent = t.accent, rim = t.fg, rimWidth = 1.dp, corner = 0.dp, font = FontFamily.Monospace, caps = true, clockSize = 13.sp, gap = 5.dp, avatar = AvatarShape.SQUARE, avatarSize = 20.dp)
        }
        // Layout Studio: the theme's colours (its palette follows the theme).
        HomeLayout.CUSTOM -> StatusLook(BarBacking.CAPSULE, bg = mixC(acc, Color.Black, 0.7f).copy(alpha = 0.7f), rim = acc.copy(alpha = 0.45f), corner = 10.dp, avatarSize = 24.dp)
        // The Pocket Classic / Ribbon bars are their own (Voltcap's isn't drawn there); anything else: the plain look.
        else -> PlainStatusLook.copy(shade = if (paleBehind) 0.62f else 0.32f)
    }
}

private fun mixC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** [look]'s colours, eased when the layout changes. */
@Composable
internal fun animatedLook(look: StatusLook): StatusLook {
    val bg by animateColorAsState(look.bg, tween(260), label = "barBg")
    val ink by animateColorAsState(look.ink, tween(260), label = "barInk")
    return look.copy(bg = bg, ink = ink)
}

private fun StatusLook.shape(): Shape = if (cut) CutCornerShape(corner) else RoundedCornerShape(corner)

internal fun StatusLook.avatarShape(): Shape = when (avatar) {
    AvatarShape.CIRCLE -> CircleShape
    AvatarShape.SQUARE -> RoundedCornerShape(3.dp)
    AvatarShape.ROUNDED -> RoundedCornerShape(8.dp)
}

/** Whether the pieces sit in one box (CAPSULE, LCD, LABEL). */
internal val StatusLook.boxed: Boolean get() = backing == BarBacking.CAPSULE || backing == BarBacking.LCD || backing == BarBacking.LABEL

/** The box round all the pieces (CAPSULE, LCD, LABEL) or the ruled line under them (UNDERLINE). */
internal fun Modifier.statusContainer(look: StatusLook): Modifier = when {
    look.boxed -> {
        val shape = look.shape()
        val round = look.corner >= 12.dp
        this.drawBehind { look.drawDecorUnder(this) }
            .background(look.bg, shape)
            .then(
                when {
                    look.rim2 != null && look.rim != null -> Modifier.border(look.rimWidth, Brush.linearGradient(listOf(look.rim, look.rim2, look.rim)), shape)
                    look.rim != null && look.decor != BarDecor.STRIPE -> Modifier.border(look.rimWidth, look.rim, shape)
                    else -> Modifier
                }
            )
            .drawBehind { look.drawDecorOver(this) }
            .padding(
                start = if (look.decor == BarDecor.STRIPE) 11.dp else if (round) 11.dp else 8.dp,
                end = if (round) 4.dp else 4.dp, top = 2.dp, bottom = 2.dp
            )
    }
    look.backing == BarBacking.UNDERLINE -> this.drawBehind {
        val r = look.rim ?: return@drawBehind
        val h = look.rimWidth.toPx()
        drawRect(r, Offset(0f, size.height - h), Size(size.width, h))
        // Ticks along it (a timeline's, a horizon's).
        val step = 16.dp.toPx()
        var x = 0f
        while (x <= size.width) { drawRect(r.copy(alpha = 0.55f), Offset(x, size.height - h - 3.dp.toPx()), Size(1.dp.toPx(), 3.dp.toPx())); x += step }
    }.padding(bottom = 6.dp)
    else -> this
}

/** One group's own box (SEGMENTS). */
internal fun Modifier.statusSegment(look: StatusLook): Modifier =
    if (look.backing != BarBacking.SEGMENTS) this
    else {
        val shape = look.shape()
        this.background(look.bg, shape)
            .then(if (look.rim != null) Modifier.border(look.rimWidth, look.rim, shape) else Modifier)
            .padding(horizontal = if (look.corner >= 12.dp) 8.dp else 6.dp, vertical = 3.dp)
    }

/** Under the box: tape strips sticking out past its ends (the kraft label). */
private fun StatusLook.drawDecorUnder(s: DrawScope) = with(s) {
    if (decor == BarDecor.TAPE) {
        val tw = 18.dp.toPx(); val th = size.height * 0.7f
        val tape = Color(0xB3F3EBD3)
        rotate(-14f, Offset(0f, size.height * 0.5f)) { drawRect(tape, Offset(-tw * 0.5f, size.height * 0.15f), Size(tw, th)) }
        rotate(12f, Offset(size.width, size.height * 0.5f)) { drawRect(tape, Offset(size.width - tw * 0.5f, size.height * 0.15f), Size(tw, th)) }
    }
}

/** Over the box (behind the pieces): grooves, traces, a gloss, a spine's band. */
private fun StatusLook.drawDecorOver(s: DrawScope) = with(s) {
    when (decor) {
        BarDecor.GROOVES -> for (k in 1..2) {
            // A record label's printed rings, inset from its edge.
            val inset = (1.5f + k * 2.2f) * density
            val h = size.height - inset * 2
            drawRoundRect(ink.copy(alpha = 0.14f), Offset(inset, inset), Size(size.width - inset * 2, h), CornerRadius(h / 2), style = Stroke(0.8f * density))
        }
        BarDecor.TRACES -> {
            val c = ink.copy(alpha = 0.14f); val w = 1f * density
            val y1 = size.height * 0.2f; val y2 = size.height * 0.84f
            drawLine(c, Offset(0f, y1), Offset(size.width * 0.28f, y1), w)
            drawLine(c, Offset(size.width * 0.28f, y1), Offset(size.width * 0.34f, y2), w)
            drawLine(c, Offset(size.width * 0.34f, y2), Offset(size.width, y2), w)
            drawCircle(c, 1.8f * density, Offset(size.width * 0.28f, y1)); drawCircle(c, 1.8f * density, Offset(size.width * 0.34f, y2))
        }
        BarDecor.GLOSS -> drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent), endY = size.height * 0.55f),
            cornerRadius = CornerRadius(corner.toPx().coerceAtMost(size.height / 2)), size = Size(size.width, size.height * 0.55f)
        )
        BarDecor.STRIPE -> {
            drawRect(rim ?: accent, Offset.Zero, Size(4.dp.toPx(), size.height))
            drawRect(ink.copy(alpha = 0.2f), Offset(0f, size.height - 1f * density), Size(size.width, 1f * density))
        }
        else -> Unit
    }
}

/** Behind the whole bar: the edge shade, and the toolbar's band with its hairline. */
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
            val h = look.rimWidth.toPx()
            drawRect(r, Offset(0f, if (atBottom) 0f else size.height - h), Size(size.width, h))
        }
    }
}

/** The bar's type: the layout's face, spacing and glow / shadow over [base]. */
internal fun StatusLook.textStyle(base: TextStyle): TextStyle {
    var st = base
    font?.let { st = st.merge(TextStyle(fontFamily = it)) }
    if (tracking != 0f) st = st.merge(TextStyle(letterSpacing = TextUnit(tracking, TextUnitType.Em)))
    val sh = glow?.let { Shadow(it.copy(alpha = 0.85f), Offset.Zero, 12f) }
        ?: if (shadowed) Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1.5f), 5f) else null
    if (sh != null) st = st.merge(TextStyle(shadow = sh))
    return st
}

/** [s] in capitals where the look wants them. */
internal fun StatusLook.cased(s: String): String = if (caps) s.uppercase() else s

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
