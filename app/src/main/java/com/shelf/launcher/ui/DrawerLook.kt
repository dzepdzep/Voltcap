package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * (Update 237, #13: the app drawer follows the active layout.) The home layout on screen comes from MotionPrefs.layout
 * (Compose state the home screen sets every frame, LauncherScreen's SideEffect), so the drawer needs nothing passed in.
 * Layouts with panels of their own lend the drawer's grid their panel (the Dashboard's glass with its look / opacity /
 * cut corners, the Live tiles' pale or theme backdrop, the Side menu's dark strip with its trim, the Spotlight / Icon
 * bar dark cards) and their focus; every other layout keeps the theme's dialog surface and focus ring. The icons take
 * the game look's corner radius (Appearance › Game row › Corners), capped so a small icon doesn't turn into a circle.
 */

internal enum class DrawerKind { DASH, LIVE, SIDE, CARDS, THEME }

internal class DrawerLook(
    val kind: DrawerKind,
    /** Text on the panel. */
    val ink: Color,
    /** The focus colour (outline / glow). */
    val focus: Color,
    /** The grid's panel (null: no panel, the dialog's own surface). */
    val panelShape: Shape?,
    /** The shape of one app's tile (its focus box). */
    val tileShape: Shape,
    val tileCorner: Dp,
    /** Behind an app without an icon. */
    val placeholder: Color
)

/** The game look's corner radius for an app icon of [size], capped at 30 % of it (Update 237, #13). */
@Composable
internal fun appIconCorner(size: Dp): Dp = minOf(LocalCardLook.current.cornerDp.dp, size * 0.3f)

/** The drawer's look for the home layout on screen (Update 237, #13). */
@Composable
internal fun drawerLook(): DrawerLook {
    val accent = ShelfTheme.accent
    val surface = ShelfTheme.colors.surface
    // On a dark dialog surface pure white (what the drawer always used); on a light theme the theme's own text colour.
    val themeInk = if (surface.luminance() < 0.35f) Color.White else ShelfTheme.colors.textPrimary
    val cardCorner = LocalCardLook.current.cornerDp
    val themeCorner = (cardCorner + 4).coerceIn(4, 18).dp
    return when (MotionPrefs.layout) {
        HomeLayout.DASH -> {
            val panel = Features.dashPanel.state.value
            val shape: Shape = if (Features.dashCut.state.value) CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp) else uiRounded(14.dp)
            val ink = panelInk(panel)
            DrawerLook(DrawerKind.DASH, ink, accent, shape, uiRounded(cardCorner.coerceIn(4, 16).dp), cardCorner.coerceIn(4, 16).dp, ink.copy(alpha = 0.12f))
        }
        HomeLayout.LIVE -> {
            val light = Features.liveLight.state.value && !Features.liveArt.state.value
            val ink = if (light) Color(0xFF2B2B2B) else themeInk
            DrawerLook(
                DrawerKind.LIVE, ink, if (light) Color(0xFF1E1E1E) else themeInk,
                if (light) RectangleShape else null, RectangleShape, 0.dp, ink.copy(alpha = 0.10f)
            )
        }
        HomeLayout.SIDE -> {
            val trim = if (Features.sideGold.state.value) Color(0xFFD9B45A) else accent
            DrawerLook(DrawerKind.SIDE, Color.White, trim, RectangleShape, uiRounded(3.dp), 3.dp, trim.copy(alpha = 0.25f))
        }
        HomeLayout.SPOT, HomeLayout.BAR -> DrawerLook(
            DrawerKind.CARDS, Color.White, if (MotionPrefs.layout == HomeLayout.BAR) Color.White else accent,
            uiRounded(12.dp), uiRounded(8.dp), 8.dp, accent.copy(alpha = 0.25f)
        )
        else -> DrawerLook(DrawerKind.THEME, themeInk, accent, null, uiRounded(themeCorner), themeCorner, accent.copy(alpha = 0.25f))
    }
}

/**
 * The grid's panel in the layout's look (Update 237, #13). It sits on the dialog's own surface plus an underlay in the
 * panel's tone, so a see-through glass setting never leaves the app names on whatever is behind the popup.
 */
@Composable
internal fun Modifier.drawerPanel(l: DrawerLook): Modifier {
    val shape = l.panelShape ?: return this
    return when (l.kind) {
        DrawerKind.DASH -> {
            val panel = Features.dashPanel.state.value
            val underlay = when (panel) {
                Features.PanelLook.LIGHT -> Color.White.copy(alpha = 0.55f)
                Features.PanelLook.DARK -> Color.Black.copy(alpha = 0.45f)
                Features.PanelLook.THEME -> ShelfTheme.colors.surface
            }
            // At least 85 % of the panel's own opacity: the drawer is for reading names, not for the art behind.
            val op = (Features.dashOpacity.state.value / 100f).coerceAtLeast(0.85f)
            this.background(underlay, shape).stylePanel(panel, op, shape)
        }
        DrawerKind.LIVE -> this.background(Brush.verticalGradient(listOf(Color(0xFFF3F3F3), Color(0xFFDADADA))), shape)
        DrawerKind.SIDE -> {
            val p = themeDark(Color(0xFF0A0D14))
            this
                .background(Brush.horizontalGradient(0f to p.copy(alpha = 0.97f), 0.85f to p.copy(alpha = 0.92f), 1f to p.copy(alpha = 0.88f)), shape)
                .border(1.dp, Brush.verticalGradient(listOf(Color.Transparent, l.focus.copy(alpha = 0.55f), Color.Transparent)), shape)
        }
        DrawerKind.CARDS -> this.background(themeDark(Color(0xFF1C1F25)).copy(alpha = 0.92f), shape)
        DrawerKind.THEME -> this
    }
}

/**
 * The focus on an app's tile, as the layout draws its own (Update 237, #13). Goes before the tile's clip (the glow
 * spills out); the theme look keeps the usual focus ring, which the tile draws after its clip.
 */
@Composable
internal fun Modifier.drawerFocus(l: DrawerLook, focused: Boolean): Modifier {
    if (!focused) return this
    return when (l.kind) {
        // The style homes' focus: the game look's glow / electric frame and an accent outline (StyleHomes.styleFocus).
        DrawerKind.DASH -> this.styleFocus(true, l.tileCorner, 0.5f, l.focus, 3.dp)
        DrawerKind.LIVE -> this.border(3.dp, l.focus, l.tileShape)
        DrawerKind.SIDE -> this
            .background(Brush.horizontalGradient(listOf(l.focus.copy(alpha = 0.32f), l.focus.copy(alpha = 0.08f))), l.tileShape)
            .border(1.5.dp, l.focus, l.tileShape)
        DrawerKind.CARDS -> this.background(Color.White.copy(alpha = 0.08f), l.tileShape).border(2.5.dp, l.focus, l.tileShape)
        DrawerKind.THEME -> this
    }
}

/** An app icon's clip: the game look's corners (Update 237, #13). */
@Composable
internal fun appIconShape(size: Dp): Shape = RoundedCornerShape(appIconCorner(size))
