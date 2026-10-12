package com.shelf.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Voltcap's design tokens: one set of spacings, corner radii, icon sizes and colour roles used by every
 * panel, card, tile and row, so screens line up and look like one app.
 *
 * Colour rule: icons and highlights use the theme's accent (or the neutral text colour); the only other
 * colours are the status roles below (success / warning / danger / gold for awards), and only where they
 * mean something. No per-icon rainbow.
 */
object Ds {
    /**
     * Spacing density (Appearance › Size › Spacing): Comfortable 1, Compact ~0.75. Read as Compose state, so
     * everything spaced with these tokens (and the shared cards / rows) tightens up at once. Sizes of cards,
     * text and icons don't change — that's the separate Size setting.
     */
    val space: Float get() = com.shelf.launcher.data.Features.density.state.value.scale.let { if (it < 1f) 0.75f else 1f }

    /** [v] dp at the current spacing density. */
    fun gap(v: Float): androidx.compose.ui.unit.Dp = (v * space).dp

    // Spacing: a 4 dp grid (scaled by [space]).
    val s1 get() = gap(4f)
    val s2 get() = gap(8f)
    val s3 get() = gap(12f)
    val s4 get() = gap(16f)
    val s5 get() = gap(24f)

    // Corner radii.
    val rSmall get() = if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 8.dp
    val rMedium get() = if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 12.dp
    val rLarge get() = if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 16.dp
    val rXLarge get() = if (com.shelf.launcher.data.Features.pixelUi.value) 0.dp else 20.dp

    // Icons.
    val iconSmall = 16.dp
    val icon = 20.dp
    val iconLarge = 24.dp

    // Quick tiles (R1 panel) and icon badges.
    val tileBox = 42.dp
    val badge = 36.dp

    // Type scale.
    val textCaption = 10.sp
    val textSmall = 12.sp
    val textBody = 14.sp
    val textTitle = 16.sp

    // Status colours (meaningful only).
    val success = Color(0xFF5BD68A)
    val warning = Color(0xFFFFB547)
    val danger = Color(0xFFFF6B5E)
    val gold = Color(0xFFFFC94D)

    /** A quiet fill for an off tile / an icon's backplate: the text colour at low strength. */
    const val quietFill = 0.08f
    /** The accent tint behind an icon badge. */
    const val accentFill = 0.16f
}
