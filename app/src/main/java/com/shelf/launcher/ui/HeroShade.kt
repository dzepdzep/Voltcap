package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shelf.launcher.data.Features

/*
 * The shade over the game's art when it's the background (Settings › Theme › Background › Shade on the game's art).
 * What makes a console home like the PS5's feel the way it does is mostly this: the art is full-bleed, but it's
 * darkened where the interface sits — a deep fade from the left edge (where the title and text are), a band along the
 * top under the icons, a heavier fade at the bottom under the row — plus a soft vignette, so the middle of the
 * picture stays bright and the eye goes there. "Console" (the default) does exactly that; "Dark corners" is only the
 * vignette; "Light corners" a soft white bloom instead, for dark art.
 */

/** Draws the chosen shade across the whole screen (call right over the art). Nothing when it's switched off. */
@Composable
internal fun HeroShade(modifier: Modifier = Modifier) {
    val mode = Features.heroShade.state.value
    if (mode == Features.HeroShade.OFF) return
    val k = Features.heroShadeStrength.state.value / 100f
    Spacer(modifier.fillMaxSize().drawBehind {
        val w = size.width; val h = size.height
        val far = kotlin.math.hypot(w, h) / 2f
        fun vignette(a: Float, color: Color = Color.Black) = drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.58f to Color.Transparent, 0.82f to color.copy(alpha = a * 0.55f), 1f to color.copy(alpha = a),
                center = Offset(w / 2f, h / 2f), radius = far
            )
        )
        when (mode) {
            Features.HeroShade.CONSOLE -> {
                // From the left edge, where the name and details sit.
                drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.70f * k), 0.28f to Color.Black.copy(alpha = 0.36f * k), 0.58f to Color.Transparent))
                // Along the top, under the status bar / icons.
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f * k), 0.2f to Color.Transparent))
                // The bottom, under the row.
                drawRect(Brush.verticalGradient(0.48f to Color.Transparent, 0.78f to Color.Black.copy(alpha = 0.45f * k), 1f to Color.Black.copy(alpha = 0.82f * k)))
                vignette(0.38f * k)
            }
            Features.HeroShade.CORNERS -> vignette(0.8f * k)
            Features.HeroShade.LIGHT -> vignette(0.42f * k, Color.White)
            Features.HeroShade.OFF -> Unit
        }
    })
}
