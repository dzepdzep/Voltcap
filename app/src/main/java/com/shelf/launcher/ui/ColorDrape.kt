package com.shelf.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Two colours (taken from a game's art, else the theme's) draped over a panel as soft glows: no picture and no
 * blur, so the panel stays in the Voltcap look but still belongs to the game. Used behind the game pop-up and
 * the detail panes instead of a blurred copy of the art.
 */
@Composable
internal fun ColorDrape(accent: Color, secondary: Color, modifier: Modifier = Modifier) {
    val a by animateColorAsState(accent, tween(500), label = "drapeA")
    val b by animateColorAsState(secondary, tween(500), label = "drapeB")
    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            if (r <= 0f) return@drawBehind // a radial gradient needs a radius
            drawRect(Brush.radialGradient(listOf(a.copy(alpha = 0.30f), Color.Transparent), center = Offset(w * 0.12f, h * 0.30f), radius = r * 0.75f))
            drawRect(Brush.radialGradient(listOf(b.copy(alpha = 0.26f), Color.Transparent), center = Offset(w * 0.92f, h * 0.88f), radius = r * 0.80f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
        }
    )
}
