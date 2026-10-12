package com.shelf.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** The glow of a game still open in the background (RunningGames): green, like a console's "running". */
internal val RunningGlowColor = Color(0xFF4CE08A)

/**
 * A card whose game is still open in the background: a soft pulsing glow round it and a green ring
 * drawn just *inside* its edge, so it's fully visible even when the card sits flush against the
 * screen's edge (an outside ring was cut off there). Off with Settings › Library › Running games ›
 * "Mark open games". From the shared slow EffectClock: no extra animation per card.
 */
internal fun Modifier.runningGlow(running: Boolean, corner: Dp, color: Color = RunningGlowColor): Modifier =
    if (!running || !com.shelf.launcher.data.RunningGames.marked) this else this.drawWithContent {
        val p = 0.5f + 0.5f * sin(EffectClock.seconds.floatValue * 2.6f)
        val r = corner.toPx()
        for (i in 3 downTo 1) {
            val g = i * 3.dp.toPx()
            drawRoundRect(
                color.copy(alpha = (0.07f + 0.13f * p) * (4 - i) / 3f),
                topLeft = Offset(-g, -g), size = Size(size.width + 2 * g, size.height + 2 * g),
                cornerRadius = CornerRadius(r + g)
            )
        }
        drawContent()
        val w = 2.5.dp.toPx()
        drawRoundRect(
            color.copy(alpha = 0.6f + 0.4f * p),
            topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius((r - w / 2).coerceAtLeast(0f)), style = Stroke(w)
        )
    }

/** The small "● Open" tag on a running game's card, so the green ring explains itself. */
@androidx.compose.runtime.Composable
internal fun RunningTag(modifier: Modifier = Modifier) {
    if (!com.shelf.launcher.data.RunningGames.marked) return
    androidx.compose.material3.Text(
        "● Open",
        color = RunningGlowColor,
        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
