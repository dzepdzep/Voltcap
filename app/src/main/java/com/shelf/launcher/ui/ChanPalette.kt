package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.ChanPalette

/*
 * Update 421: one palette for the whole Channel grid — backdrop, channel tiles, the curved bar and its round buttons, the
 * corner buttons, the clock, focus rings and the Wii Channel preview window — so nothing is recoloured on its own.
 * Original Pale and OLED Black are exactly the colours the layout had (pale / Dark (OLED)); Light Blue, Light Yellow and
 * Custom follow the same light geometry. Custom works out readable text from its base colour (dark ink on a light base,
 * white on a dark one) and a dark base uses the dark drawing path, so text and focus rings always contrast.
 */

/** The palette in use (older saves without one: Dark on = OLED Black, else Original Pale). */
internal fun chanPaletteNow(): ChanPalette =
    Features.chanPalette.state.value.let { n -> ChanPalette.values().firstOrNull { it.name == n } }
        ?: if (Features.chanDark.state.value) ChanPalette.OLED else ChanPalette.PALE

/** Picks [p]; keeps the older Dark switch in step (OLED Black ⇔ Dark on). Nothing else changes. */
internal fun setChanPalette(p: ChanPalette) {
    Features.chanPalette.value = p.name
    val dark = p == ChanPalette.OLED
    if (Features.chanDark.value != dark) Features.chanDark.value = dark
}

/**
 * The Channel grid's colours. [light]: drawn the light way (dark writing); [pure]: OLED — true black, no theme tint.
 * [surface] is the white of the tiles / buttons, [surface2] / [surface3] its lower gradient stops, [edge] the tile rim,
 * [ink] the writing, [bgTop] / [bgBottom] the backdrop, [darkBase] the dark path's own base (Custom dark) or null.
 */
internal class ChanColors(
    val palette: ChanPalette, val light: Boolean, val pure: Boolean,
    val bgTop: Color, val bgBottom: Color, val surface: Color, val surface2: Color, val surface3: Color,
    val ink: Color, val edge: Color, val darkBase: Color? = null
)

internal fun chanColorsFor(p: ChanPalette, custom: Int): ChanColors = when (p) {
    ChanPalette.PALE -> ChanColors(p, true, false, Color(0xFFF6F8FB), Color(0xFFDDE2E9), Color.White, Color(0xFFE3E8EF), Color(0xFFD5DBE3), Color(0xFF4A5461), Color(0xFFB9C3D0))
    ChanPalette.OLED -> ChanColors(p, false, true, Color.Black, Color(0xFF0A0B0E), Color(0xFF111316), Color(0xFF202328), Color(0xFF17191D), Color.White, Color(0xFF3A414C))
    // (Thor check: the first Light Blue / Light Yellow were too close to Original Pale to tell apart; now clearly tinted.)
    ChanPalette.BLUE -> ChanColors(p, true, false, Color(0xFFD3E9FB), Color(0xFFA9D0F0), Color(0xFFF3F9FF), Color(0xFFD6EAFB), Color(0xFFBBD8F2), Color(0xFF213F5C), Color(0xFF86B6E0))
    ChanPalette.YELLOW -> ChanColors(p, true, false, Color(0xFFFFF4C8), Color(0xFFF2DC8A), Color(0xFFFFFCEE), Color(0xFFF8EBB6), Color(0xFFEFD68E), Color(0xFF4A3B12), Color(0xFFD2B65C))
    ChanPalette.CUSTOM -> {
        val base = Color(custom or 0xFF000000.toInt())
        if (base.luminance() >= 0.42f) ChanColors(
            p, true, false, lerp(base, Color.White, 0.55f), base, lerp(base, Color.White, 0.88f), lerp(base, Color.White, 0.62f),
            lerp(base, Color.White, 0.42f), readableInk(base), lerp(base, Color.Black, 0.22f)
        ) else ChanColors(
            p, false, false, lerp(base, Color.Black, 0.55f), lerp(base, Color.Black, 0.75f), lerp(base, Color.Black, 0.35f),
            lerp(base, Color.Black, 0.2f), lerp(base, Color.Black, 0.5f), Color.White, lerp(base, Color.White, 0.3f), darkBase = base
        )
    }
}

/** Dark writing in the base colour's own hue, dark enough to read on it (contrast ≥ 4.5 against the lightest surface). */
internal fun readableInk(base: Color): Color {
    var k = 0.62f
    var ink = lerp(base, Color.Black, k)
    while (k < 0.95f && chanContrast(ink, lerp(base, Color.White, 0.42f)) < 4.5f) { k += 0.06f; ink = lerp(base, Color.Black, k) }
    return ink
}

private fun chanContrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

internal fun chanColors(): ChanColors = chanColorsFor(chanPaletteNow(), Features.chanCustom.state.value)

/** The Channel grid is drawn the light way: the Pale backdrop is on (Background: Channel colours) and the palette is a light one. */
internal fun chanLightLook(): Boolean = Features.chanLight.state.value && chanColors().light
