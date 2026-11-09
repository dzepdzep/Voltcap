package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import com.shelf.launcher.ui.theme.legibleArtAccent
import com.shelf.launcher.ui.theme.legibleOn
import com.shelf.launcher.ui.theme.textOn

/**
 * Update 493 — Layout-aware windows, phase 1: one small set of window-frame tokens (corner, surface, rim) chosen from the
 * home layout, used by a window's outer frame only. Content, lists, focus, headers and buttons are untouched. Off by
 * default (Settings › Theme › Interface style › "Windows follow the home layout"); layouts without a look of their own
 * keep the window exactly as before ([Style.OWN] draws what the windows drew).
 */
internal object WindowLook {
    /**
     * Update 497 (1D): a few reusable families, not one look per layout. Colour families ([colored]) draw the window's
     * content in the layout's own panels (Channel grid's palette; Arctic's slate; Cinema's black) with the resolved accent;
     * frame families (Cross menu, [DESK], [SHELF]) keep the theme's content colours and only take the layout's corner and an
     * accent-tinted rim.
     */
    enum class Style(val colored: Boolean = false) { OWN, CHANNEL(true), CROSS, ARCTIC(true), CINEMA(true), DESK, SHELF }

    /** Which frame [layout] gives a window; [OWN] unless following is on and the layout has one. */
    fun styleFor(layout: HomeLayout, follow: Boolean): Style = if (!follow) Style.OWN else when (layout) {
        HomeLayout.CHAN -> Style.CHANNEL
        HomeLayout.CROSS -> Style.CROSS
        HomeLayout.ARCTIC -> Style.ARCTIC
        HomeLayout.CINEMA -> Style.CINEMA
        HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE -> Style.DESK
        HomeLayout.STACKS, HomeLayout.CASEFLOW, HomeLayout.SHELF, HomeLayout.DISCS, HomeLayout.CRATE, HomeLayout.BOX,
        HomeLayout.STORAGE, HomeLayout.COVERDETAILS, HomeLayout.SHOP -> Style.SHELF
        else -> Style.OWN
    }

    @Composable
    fun current(): Style = styleFor(MotionPrefs.layout, Features.windowsFollowLayout.state.value)

    /** The frame's corner: Channel grid rounder, Cross menu nearly square; [own] otherwise (also with square pixel corners). */
    fun corner(style: Style, own: Dp, compact: Boolean, pixel: Boolean): Dp = when {
        pixel || style == Style.OWN -> own
        style == Style.CHANNEL -> if (compact) 20.dp else 30.dp
        // (Update 497: the corners those layouts' own tiles / panels use.)
        style == Style.ARCTIC -> if (compact) 10.dp else 12.dp
        style == Style.DESK -> if (compact) 14.dp else 18.dp
        style == Style.SHELF -> if (compact) 10.dp else 14.dp
        else -> if (compact) 6.dp else 8.dp // Cross menu, Cinema
    }
}

/**
 * A window's outer frame in [style]. [Style.OWN] is exactly the frame the windows had (voltSurface + a faint rim).
 * Channel grid: the layout's own panel colours ([channelWindowTokens]: its palette, light or dark, and the accent the home
 * screen really shows) with a rim in the tile-edge colour. Cross menu: a restrained translucent panel with a hairline rim.
 */
@Composable
internal fun Modifier.windowFrame(style: WindowLook.Style, shape: Shape): Modifier {
    val surface = ShelfTheme.colors.surface
    return when (style) {
        WindowLook.Style.OWN -> this.voltSurface(shape, base = surface).border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        // (Update 495, phase 2: light, like the Channel grid's own game window. Update 497: in the layout's real colours —
        // its palette (Original Pale, Light Blue / Yellow, OLED, Custom) and the accent the home screen shows, not a fixed blue.)
        WindowLook.Style.CHANNEL -> {
            val t = rememberChannelWindowTokens()
            this.clip(shape).background(Brush.verticalGradient(listOf(t.frameTop, t.frameBottom))).border(2.dp, t.rim, shape)
        }
        WindowLook.Style.CROSS -> this.clip(shape)
            .background(surface.copy(alpha = surface.alpha * WINDOW_CROSS_ALPHA))
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), shape)
        // (Update 497, 1D: Arctic's slate / Cinema's black panel, rim in the layout's edge colour mixed with the accent.)
        WindowLook.Style.ARCTIC, WindowLook.Style.CINEMA -> {
            val t = rememberWindowTokens(style)!!
            this.clip(shape).background(Brush.verticalGradient(listOf(t.frameTop, t.frameBottom))).border(1.dp, t.rim, shape)
        }
        // (Update 497, 1D frame families: the theme's own panel, the layout's corner, a rim in the settled accent.)
        WindowLook.Style.DESK, WindowLook.Style.SHELF ->
            this.voltSurface(shape, base = surface).border(if (style == WindowLook.Style.SHELF) 1.5.dp else 1.dp, windowRimColor(style), shape)
    }
}

/**
 * Update 497: the rim colour of a window in [style] (also the Save Vault's rim). Frame families mix the settled accent
 * (Material's scheme — not the gliding one, D137) into a faint white edge.
 */
@Composable
internal fun windowRimColor(style: WindowLook.Style): Color = when (style) {
    WindowLook.Style.OWN -> Color.White.copy(alpha = 0.08f)
    WindowLook.Style.CROSS -> Color.White.copy(alpha = 0.14f)
    WindowLook.Style.DESK, WindowLook.Style.SHELF -> {
        val acc = (LocalOuterScheme.current ?: androidx.compose.material3.MaterialTheme.colorScheme).primary
        androidx.compose.runtime.remember(acc, style) { lerp(Color.White.copy(alpha = 0.12f), acc, if (style == WindowLook.Style.SHELF) 0.45f else 0.35f) }
    }
    else -> rememberWindowTokens(style)!!.rim
}

/**
 * Update 497 (1A): everything a Channel grid window is drawn in, worked out from the colours the home layout really uses —
 * the Channel grid's own palette ([ChanColors]: light or dark, the owner's Custom base) and [accent], the theme's resolved
 * accent (layout colours › Custom › game / album art › theme, in ShelfLauncherTheme's own order; nothing is re-decided
 * here). Writing, muted writing, outlines and the accent are nudged in lightness only until they read on the panels.
 */
internal class ChannelWindowTokens(
    val colors: com.shelf.launcher.ui.theme.ShelfColors, val light: Boolean,
    val frameTop: Color, val frameBottom: Color, val rim: Color,
    val track: Color, val outline: Color, val outlineSoft: Color, val soft: Color, val deep: Color, val onAccent: Color
)

/**
 * [darkSurfaces]: the theme's own (background, surface, raised) for a Channel grid drawn on the theme (Channel colours off),
 * as the home layout then does.
 */
internal fun channelWindowTokens(
    accent: Color, pal: ChanColors, lightLook: Boolean, chanOwnDark: Boolean,
    darkSurfaces: Triple<Color, Color, Color>, themeText: Color, themeMuted: Color
): ChannelWindowTokens {
    val (bg, surface, raised) = when {
        lightLook -> Triple(pal.bgTop, pal.surface, pal.surface2)
        pal.pure && chanOwnDark -> Triple(Color.Black, Color(0xFF111316), Color(0xFF202328))
        pal.darkBase != null && chanOwnDark -> Triple(pal.bgBottom, pal.surface, pal.surface2)
        else -> darkSurfaces
    }
    val ink0 = if (lightLook) pal.ink else if (chanOwnDark) Color.White else themeText
    val edge = if (lightLook) pal.edge else if (pal.pure && chanOwnDark) Color(0xFF3A414C) else Color(0xFFB9C3D0)
    return windowTokens(accent, bg, surface, raised, ink0, if (lightLook || chanOwnDark) null else themeMuted, edge, lightLook)
}

/**
 * Update 497 (1D): the shared token maths for every colour family — [muted0] null = the writing faded towards the panel.
 * Lightness-only nudges keep writing ≥ 7:1 (≥ 4.5:1 on raised panels), muted ≥ 4.5:1, the accent ≥ 3:1 with ≥ 4.5:1 text on it.
 */
internal fun windowTokens(
    accent: Color, bg: Color, surface: Color, raised: Color, ink0: Color, muted0: Color?, edge: Color, light: Boolean
): ChannelWindowTokens {
    val lightLook = light
    val panels = listOf(surface, raised)
    val ink = ink0.legibleOn(7f, listOf(surface)).legibleOn(4.5f, panels)
    val muted = (muted0 ?: lerp(ink, surface, 0.28f)).legibleOn(4.5f, panels)
    val acc = legibleArtAccent(accent, surface, raised)
    val onAcc = textOn(acc, Color.White)
    val track = lerp(surface, ink, if (lightLook) 0.16f else 0.22f)
    val outline = lerp(surface, ink, 0.5f).legibleOn(3f, listOf(surface)).legibleOn(2f, listOf(track))
    val soft = lerp(surface, acc, if (lightLook) 0.2f else 0.3f)
    val deep = (if (lightLook) lerp(acc, Color.Black, 0.35f) else lerp(acc, Color.White, 0.55f)).legibleOn(4.5f, listOf(soft))
    val colors = com.shelf.launcher.ui.theme.ShelfColors(
        background = bg, surface = surface, surfaceRaised = raised, accent = acc, accentGlow = acc.copy(alpha = 0.45f),
        secondary = lerp(acc, surface, 0.45f), textPrimary = ink, textMuted = muted
    )
    return ChannelWindowTokens(
        colors, lightLook,
        frameTop = surface, frameBottom = lerp(surface, raised, 0.7f), rim = lerp(edge, acc, 0.3f),
        track = track, outline = outline, outlineSoft = lerp(surface, ink, 0.2f), soft = soft, deep = deep, onAccent = onAcc
    )
}

/**
 * The tokens for the window being composed. Keyed on Material's scheme from ShelfLauncherTheme (the resolved TARGET colours,
 * handed on once art colours are still — D137) and the Channel grid's palette prefs, so an open window takes a new accent /
 * palette at once without re-composing on every frame of a colour glide.
 */
@Composable
internal fun rememberChannelWindowTokens(): ChannelWindowTokens {
    val outer = androidx.compose.material3.MaterialTheme.colorScheme
    val pal = chanColors()
    val lightLook = chanLightLook()
    val own = Features.chanLight.state.value
    // (Inside a window the scheme is the window's own: take the outer one the window was opened on.)
    val src = LocalOuterScheme.current ?: outer
    return androidx.compose.runtime.remember(src.primary, src.background, src.surface, src.surfaceVariant, src.onSurface, pal.palette, pal.surface, pal.ink, lightLook, own) {
        channelWindowTokens(src.primary, pal, lightLook, own, Triple(src.background, src.surface, src.surfaceVariant),
            src.onSurface, lerp(src.onSurface, src.surface, 0.3f))
    }
}

/** Update 497 (1D): the tokens of a colour family ([WindowLook.Style.colored]); null for the frame-only ones. */
@Composable
internal fun rememberWindowTokens(style: WindowLook.Style): ChannelWindowTokens? = when (style) {
    WindowLook.Style.CHANNEL -> rememberChannelWindowTokens()
    WindowLook.Style.ARCTIC -> {
        val acc = (LocalOuterScheme.current ?: androidx.compose.material3.MaterialTheme.colorScheme).primary
        androidx.compose.runtime.remember(acc) { arcticWindowTokens(acc) }
    }
    WindowLook.Style.CINEMA -> {
        val acc = (LocalOuterScheme.current ?: androidx.compose.material3.MaterialTheme.colorScheme).primary
        // (Cinema's own red when its accent option says so, as the home layout does — else the resolved accent.)
        val red = Features.cnAccent.state.value == Features.CnAccent.CINEMA
        androidx.compose.runtime.remember(acc, red) { cinemaWindowTokens(if (red) CINEMA_RED else acc) }
    }
    else -> null
}

/** Arctic hub's slate panels (StyleArctic: 0D141D / 1B2633 tiles, white focus edge). */
internal fun arcticWindowTokens(accent: Color) = windowTokens(
    accent, Color(0xFF080C12), Color(0xFF0D141D), Color(0xFF1B2633), Color.White, Color(0xFFB7C3D1), Color(0xFF5A6B80), false
)

/** Cinema's near-black panels (StyleCinema: 17171B / 222226 tiles). */
internal fun cinemaWindowTokens(accent: Color) = windowTokens(
    accent, Color(0xFF060607), Color(0xFF17171B), Color(0xFF222226), Color.White, Color(0xFFB3B3B3), Color(0xFF3A3A40), false
)
internal val CINEMA_RED = Color(0xFFE50914)

/** Update 497: Material's scheme of the screen a window opened on (before the window's own), for [rememberChannelWindowTokens]. */
internal val LocalOuterScheme = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.material3.ColorScheme?> { null }

/** Kept for the Channel grid's Original Pale defaults (tests and the Save Vault rim's fallback). */
internal val WINDOW_CHANNEL_BLUE = Color(0xFF2F7BC0)
internal val WINDOW_CHANNEL_PALE = Color(0xFFEAF2FA)
internal val WINDOW_CHANNEL_RIM = Color(0xFF8DBDE6)

/** Update 495 (phase 2) / 497: the colours inside a Channel grid window, for [base]'s accent on Original Pale (tests). */
internal fun channelWindowColors(base: com.shelf.launcher.ui.theme.ShelfColors) =
    channelWindowTokens(base.accent, chanColorsFor(Features.ChanPalette.PALE, 0), true, true,
        Triple(base.background, base.surface, base.surfaceRaised), base.textPrimary, base.textMuted).colors

@Composable
internal fun WindowColors(style: WindowLook.Style, content: @Composable () -> Unit) {
    if (style == WindowLook.Style.OWN) { content(); return }
    val outer = androidx.compose.material3.MaterialTheme.colorScheme
    if (!style.colored) {
        androidx.compose.runtime.CompositionLocalProvider(LocalWindowStyle provides style, LocalOuterScheme provides (LocalOuterScheme.current ?: outer), content = content)
        return
    }
    val t = rememberWindowTokens(style)!!
    val c = t.colors
    val live = androidx.compose.runtime.rememberUpdatedState(c)
    val scheme = androidx.compose.runtime.remember(t) {
        // (Update 496, phase 3: the rest of Material's tones in the window's colours, so its own switches, sliders, fields,
        // menus, dialogs and disabled states match the window. Update 497: from the layout's real palette and accent.)
        if (t.light) androidx.compose.material3.lightColorScheme(
            primary = c.accent, onPrimary = t.onAccent, background = c.background, surface = c.surface,
            surfaceVariant = c.surfaceRaised, surfaceContainer = c.surface, onBackground = c.textPrimary, onSurface = c.textPrimary,
            onSurfaceVariant = c.textMuted, secondary = c.secondary,
            surfaceContainerLowest = c.surface, surfaceContainerLow = lerp(c.surface, c.surfaceRaised, 0.3f), surfaceContainerHigh = c.surfaceRaised,
            surfaceContainerHighest = t.track, outline = t.outline, outlineVariant = t.outlineSoft,
            primaryContainer = t.soft, onPrimaryContainer = t.deep,
            secondaryContainer = t.soft, onSecondaryContainer = t.deep, surfaceTint = c.accent
        ) else androidx.compose.material3.darkColorScheme(
            primary = c.accent, onPrimary = t.onAccent, background = c.background, surface = c.surface,
            surfaceVariant = c.surfaceRaised, surfaceContainer = c.surface, onBackground = c.textPrimary, onSurface = c.textPrimary,
            onSurfaceVariant = c.textMuted, secondary = c.secondary,
            surfaceContainerLowest = c.background, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceRaised,
            surfaceContainerHighest = t.track, outline = t.outline, outlineVariant = t.outlineSoft,
            primaryContainer = t.soft, onPrimaryContainer = t.deep,
            secondaryContainer = t.soft, onSecondaryContainer = t.deep, surfaceTint = c.accent
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalWindowStyle provides style,
        LocalOuterScheme provides (LocalOuterScheme.current ?: outer),
        com.shelf.launcher.ui.theme.LocalShelfColors provides c,
        com.shelf.launcher.ui.theme.LocalShelfColorsLive provides live,
        androidx.compose.material3.LocalContentColor provides c.textPrimary
    ) {
        androidx.compose.material3.MaterialTheme(colorScheme = scheme, typography = androidx.compose.material3.MaterialTheme.typography, content = content)
    }
}
internal const val WINDOW_CROSS_ALPHA = 0.82f

/** Update 496 (phase 3): the window style the content is drawn in ([WindowColors]); [WindowLook.Style.OWN] outside one. */
internal val LocalWindowStyle = androidx.compose.runtime.staticCompositionLocalOf { WindowLook.Style.OWN }

/**
 * Update 496 (phase 3): a faint line / track / edge tint — the window's dark writing colour inside a Channel grid window
 * (white lines vanished on its white panels), exactly Color.White.copy(alpha) everywhere else, so with the switch off
 * nothing changes.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun winTint(alpha: Float): Color =
    if (LocalWindowStyle.current == WindowLook.Style.CHANNEL) ShelfTheme.colors.textPrimary.copy(alpha = (alpha * 1.8f).coerceAtMost(1f))
    else Color.White.copy(alpha = alpha)

/**
 * Update 496 (phase 3): the rim of a window that draws its own scene inside (the Save Vault) — only the frame follows
 * the layout there: Channel grid's soft blue rim, Cross menu's hairline; nothing at all for [WindowLook.Style.OWN].
 */
internal fun Modifier.windowRim(style: WindowLook.Style, shape: Shape, rimColor: Color = WINDOW_CHANNEL_RIM): Modifier {
    if (style == WindowLook.Style.OWN) return this
    val color = rimColor
    val width = when (style) { WindowLook.Style.CHANNEL -> 2.dp; WindowLook.Style.CROSS -> 0.5.dp; else -> 1.dp }
    // Drawn over the content (the vault paints its whole scene inside), inside the bounds: never changes a size.
    return this.drawWithContent {
        drawContent()
        val w = width.toPx()
        if (size.width > w * 4 && size.height > w * 4) {
            drawOutline(shape.createOutline(size, layoutDirection, this), color, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 2))
        }
    }
}

/**
 * Update 495: the faint white tints panels use for lines, tracks and field edges — on a light surface (the Lobby theme,
 * a Channel grid window) the same tint in the dark writing colour instead, which white-on-white hid. Exactly
 * Color.White.copy(alpha) on every dark theme.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun inkTint(alpha: Float): Color {
    val c = ShelfTheme.colors
    return if (c.surface.luminance() > 0.5f) c.textPrimary.copy(alpha = (alpha * 1.8f).coerceAtMost(1f)) else Color.White.copy(alpha = alpha)
}
