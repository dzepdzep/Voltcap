package com.shelf.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * (Update 240, #23: legibility checks for the colours taken from game and album art.)
 * WCAG-style contrast between two colours, and small lightness nudges (HSL: hue and saturation kept) so an art-driven
 * accent stays readable on the background it's drawn on, and text on the accent stays readable too. Pure maths, no
 * allocation besides the result: cheap enough for the theme to run once per colour change (it is remembered there).
 */

/** Contrast ratio of [a] against [b], 1..21 (alpha ignored). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.copy(alpha = 1f).luminance()
    val lb = b.copy(alpha = 1f).luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * [fg] with its HSL lightness moved away from [bg] (lighter on a dark background, darker on a light one) just enough to
 * reach [min] contrast against every colour in [bg]; unchanged when it already does. Hue and saturation are kept.
 */
internal fun Color.legibleOn(min: Float, bg: List<Color>): Color { // (a list: a value class can't be a vararg)
    if (bg.isEmpty() || bg.all { contrastRatio(this, it) >= min }) return this
    // The direction: away from the lightest / darkest background it must stand out from.
    val darkBg = bg.map { it.luminance() }.average() < 0.4
    val hsl = toHsl()
    val start = hsl[2]
    val end = if (darkBg) 1f else 0f
    fun at(l: Float) = hslColor(hsl[0], hsl[1], l, alpha)
    if (bg.any { contrastRatio(at(end), it) < min }) return at(end)
    // Binary search for the smallest change that is enough.
    var lo = 0f
    var hi = 1f
    repeat(14) {
        val mid = (lo + hi) / 2f
        val c = at(start + (end - start) * mid)
        if (bg.all { contrastRatio(c, it) >= min }) hi = mid else lo = mid
    }
    return at(start + (end - start) * hi)
}

/**
 * The colour to write on [accent]: [preferred] when it reaches [min] contrast (the theme's own choice, e.g. its
 * background), else white or black, whichever reads better.
 */
internal fun textOn(accent: Color, preferred: Color, min: Float = 4.5f): Color {
    if (contrastRatio(preferred, accent) >= min) return preferred
    return if (contrastRatio(Color.White, accent) >= contrastRatio(Color.Black, accent)) Color.White else Color.Black
}

/**
 * An art-driven accent made legible: at least [minOnBg] against the background and the raised panels (it's drawn on
 * both), and with white or black text on it reaching 4.5:1 (it's a button colour too). The accent only gets lighter /
 * darker; its hue stays the art's.
 */
internal fun legibleArtAccent(accent: Color, background: Color, raised: Color, minOnBg: Float = 3f): Color {
    var a = accent.legibleOn(minOnBg, listOf(background, raised))
    // Text on it: the best of white / black must reach 4.5:1. Mid-luminance accents (≈0.18) are the only ones that
    // can fail both; nudge them further from the background (they still clear [minOnBg]).
    if (maxOf(contrastRatio(Color.White, a), contrastRatio(Color.Black, a)) < 4.5f) {
        val dark = background.luminance() < 0.4f
        a = a.legibleOn(4.5f, listOf(if (dark) Color.Black else Color.White))
    }
    return a
}

/** HSV saturation of [c] (0 = grey), for the "too grey to use" check. */
internal fun saturationOf(c: Color): Float {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    return if (mx <= 0.001f) 0f else (mx - mn) / mx
}

private fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d < 1e-5f) return floatArrayOf(0f, 0f, l)
    val s = d / (1f - kotlin.math.abs(2f * l - 1f)).coerceAtLeast(1e-5f)
    val h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

private fun hslColor(h: Float, s: Float, l: Float, alpha: Float): Color {
    val ll = l.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * ll - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = ll - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f), alpha)
}
