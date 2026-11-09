package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import com.shelf.launcher.ui.theme.legibleArtAccent

/*
 * Update 505 (owner-approved batch 505): the last game-window scenes that drew a fixed colour of their own — Rental Shop,
 * Bubbles, Spotlight, Library, Art Library, Crate, Pocket classic, Cross menu — take the colours of the home they belong to.
 *
 * One rule, no second colour system:
 *  - a home that draws in the effective theme colours (ShelfTheme.accent / themeDark — Spotlight, Library, Art Library, Crate,
 *    Cross menu's "Theme" wash) → its scene reads the same resolved colours (theme, game art, music and the layout's own
 *    colours already decided in Theme.kt, in that precedence);
 *  - a home with its own colour choice (Rental Shop's wall, Bubbles' wallpaper, Pocket classic's accent, Cross menu's
 *    Dusk / Ocean / Ember wash) → its scene uses that choice, unless you set this layout's own colours (Theme › colours per
 *    layout): then the resolved accent, as everything else on that layout follows it;
 *  - every lit colour is made readable on what it sits on (legibleArtAccent: 3:1, only the lightness moves).
 */

/** Pure: the lit colour of a scene — [effective] when this layout's own colours ask for an accent, else [own]. */
internal fun sceneLitFor(layoutAsksAccent: Boolean, effective: Color, own: Color, ground: Color): Color =
    legibleArtAccent(if (layoutAsksAccent) effective else own, ground, ground)

/** [layout]'s popup lit colour on [ground] (see the file note). Reads Compose state. */
@Composable
internal fun sceneLit(layout: HomeLayout, own: Color, ground: Color): Color =
    sceneLitFor(Features.layoutColorsFor(layout.name).acc != null, ShelfTheme.accent, own, ground)

/** Pure: the Cross menu wash (top, bottom) — null for "None". Shared by the home's backdrop and its options column. */
internal fun crossWash(tint: Features.CrossTint, background: Color, accent: Color): Pair<Color, Color>? = when (tint) {
    Features.CrossTint.DUSK -> Color(0xFF3B2350) to Color(0xFFB59AD8)
    Features.CrossTint.OCEAN -> Color(0xFF0E2A48) to Color(0xFF5E9BD0)
    Features.CrossTint.EMBER -> Color(0xFF3A1014) to Color(0xFFC0704E)
    Features.CrossTint.THEME -> background to accent
    Features.CrossTint.NONE -> null
}

/**
 * Update 505: what's behind a scene's window for the scenes whose home has its own colour choice — the dim in that
 * choice's dark (Rental Shop's wall, Bubbles' wallpaper); everything else keeps [sceneVeil].
 */
@Composable
internal fun sceneVeilFor(scene: WinScene): Brush = when (scene) {
    WinScene.SHOP_TAG -> shopColors(Features.rsWall.state.value).let { s ->
        Brush.verticalGradient(listOf(s.wallBottom.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.85f)))
    }
    WinScene.BUBBLE_CARD -> bubbleWallInk(Features.bvWall.state.value).let { d ->
        Brush.verticalGradient(listOf(d.copy(alpha = 0.6f), lerp(d, Color.Black, 0.5f).copy(alpha = 0.8f)))
    }
    else -> sceneVeil(scene)
}

/** Pure: [c] taken towards black until it is a dark ground white writing reads on (relative luminance ≤ [max]). */
internal fun sceneDark(c: Color, max: Float = 0.06f): Color {
    var d = c.copy(alpha = 1f)
    var i = 0
    while (d.luminance() > max && i < 12) { d = lerp(d, Color.Black, 0.25f); i++ }
    return d
}

/** [c] made readable (3:1) on [ground] — only its lightness moves. */
internal fun legibleOn(c: Color, ground: Color): Color = legibleArtAccent(c, ground, ground)
