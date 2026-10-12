package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 151: theme colours on the style homes. Many layouts drew their panels, cards and buttons in one fixed dark grey
 * (and a few fixed greens / blues for the selection), so picking another theme changed almost nothing on them. These
 * keep each layout's own dark look but take the theme's colour into it.
 */

/**
 * [base] (a layout's own dark panel colour) tinted by the theme: towards the theme's surface when that is dark, else
 * (a light theme) just a touch of the accent, so the panel stays dark and its white text stays readable. Keeps
 * [base]'s alpha.
 */
@Composable
@ReadOnlyComposable
internal fun themeDark(base: Color, share: Float = 0.5f): Color {
    val surface = ShelfTheme.colors.surface
    val mixed = if (surface.luminance() < 0.35f) lerp(base, surface.copy(alpha = 1f), share)
    else lerp(base, ShelfTheme.accent.copy(alpha = 1f), share * 0.25f)
    return mixed.copy(alpha = base.alpha)
}
