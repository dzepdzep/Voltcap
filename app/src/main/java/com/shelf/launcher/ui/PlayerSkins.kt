package com.shelf.launcher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 117: the small music player in each layout's own style (Home › Music player › "In the layout's style", on by
 * default): an LCD on the Circuit board, a white pill on the Channel grid, the handheld's grey panel on the Dual-screen
 * menu, a flat tile on Live tiles, a wooden label on the Shelf, a TV-remote plastic in the TV room… The player's
 * controls and the D-pad on them stay the same.
 */
internal class PlayerSkin(
    /** null: the album art's own colour (the Voltcap row's look). */
    val background: Brush?,
    val shape: Shape,
    val border: Color?,
    val ink: Color,
    /** The play button's fill and icon. */
    val button: Color,
    val buttonInk: Color,
    /** The blurred album art behind it. */
    val artBehind: Boolean,
    val mono: Boolean = false
)

/** What the player's text and controls are drawn in (white, unless the skin is light). */
internal val LocalPlayerInk = compositionLocalOf { Color.White }

@Composable
internal fun playerSkinFor(layout: HomeLayout): PlayerSkin {
    val acc = ShelfTheme.accent
    val t = ShelfTheme.colors
    val default = PlayerSkin(null, RoundedCornerShape(8.dp), null, Color.White, Color.White, Color.Black, artBehind = true)
    if (!Features.musicSkins.state.value) return default
    fun solid(c: Color) = Brush.verticalGradient(listOf(c, c))
    return when (layout) {
        HomeLayout.CIRCUIT -> {
            val ink = if (Features.circuitColors.state.value == Features.CircuitColors.GREEN) Color(0xFF7CE0A0) else acc
            val lcd = when (Features.circuitColors.state.value) {
                Features.CircuitColors.THEME -> t.surface
                Features.CircuitColors.GREEN -> Color(0xFF0D1410)
                Features.CircuitColors.DARK -> Color(0xFF0E1013)
            }
            PlayerSkin(solid(lcd), RoundedCornerShape(4.dp), ink, Color.White, ink, Color.Black, artBehind = false, mono = true)
        }
        HomeLayout.CHAN -> if (Features.chanLight.state.value)
            PlayerSkin(Brush.verticalGradient(listOf(Color.White, Color(0xFFDCE2EA))), RoundedCornerShape(50), Color(0xFF8FB4D8), Color(0xFF3B4450), Color(0xFF3B4450), Color.White, artBehind = false)
        else PlayerSkin(Brush.verticalGradient(listOf(Color(0xFF323A46), Color(0xFF1C2129))), RoundedCornerShape(50), Color(0xFF8FB4D8), Color.White, Color.White, Color.Black, artBehind = false)
        HomeLayout.LIVE -> PlayerSkin(solid(acc), RoundedCornerShape(0.dp), null, Color.White, Color.White, acc, artBehind = false)
        HomeLayout.DASH -> PlayerSkin(Brush.verticalGradient(listOf(t.surfaceRaised, t.surface)), RoundedCornerShape(6.dp), null, t.textPrimary, acc, Color.White, artBehind = false)
        HomeLayout.SHELF -> PlayerSkin(Brush.verticalGradient(listOf(Color(0xFFB98757), Color(0xFF9C6D43))), RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 4.dp), Color(0xFF24150B), Color(0xFF2A1A0E), Color(0xFF2A1A0E), Color(0xFFF2D3A6), artBehind = false)
        HomeLayout.TV -> PlayerSkin(Brush.verticalGradient(listOf(Color(0xFF2E3036), Color(0xFF1A1B1F))), RoundedCornerShape(12.dp), Color(0xFF45474F), Color.White, acc, Color.White, artBehind = false)
        HomeLayout.CROSS -> PlayerSkin(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(4.dp), null, Color.White, Color.White, Color.Black, artBehind = false)
        HomeLayout.SPOT, HomeLayout.BAR -> PlayerSkin(solid(Color(0xFFF2F3F5)), RoundedCornerShape(14.dp), null, Color(0xFF1E2228), acc, Color.White, artBehind = false)
        HomeLayout.HERO, HomeLayout.RESUME, HomeLayout.HUB -> PlayerSkin(solid(Color.Black.copy(alpha = 0.4f)), RoundedCornerShape(14.dp), Color.White.copy(alpha = 0.16f), Color.White, Color.White, Color.Black, artBehind = true)
        HomeLayout.SIDE -> PlayerSkin(solid(t.surface.copy(alpha = 0.95f)), RoundedCornerShape(12.dp), t.surfaceRaised, t.textPrimary, acc, Color.White, artBehind = false)
        else -> default
    }
}
