package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.shelf.launcher.data.Features

/*
 * Update 391: the physical look of an upright spine (Features.spineDepth): drawn OVER the spine's own picture (a real scan
 * first, then the console's template, Voltcap's own) so the artwork never changes, only how it is lit: bevelled edges, the
 * rounded back of a case, a lit top cap, a contact shadow where it stands on the shelf and a small tone difference per
 * game so a packed row doesn't look stamped. Nothing animates and nothing is allocated per frame (brushes are cheap and
 * built in the draw pass only for the level that is on).
 */
@Composable
internal fun Modifier.spineDepth(seed: Int): Modifier {
    val level = Features.spineDepth.state.value
    return if (level == Features.SpineDepth.OFF) this else this.drawWithContent { drawContent(); drawSpineDepth(level, seed) }
}

internal fun DrawScope.drawSpineDepth(level: Features.SpineDepth, seed: Int) {
    val w = size.width
    val h = size.height
    if (w <= 2f || h <= 2f) return
    val real = level == Features.SpineDepth.REALISTIC
    // Tone: each game stands a little differently lit (0 .. 6 % / 0 .. 11 % darker), the same every frame.
    val tone = ((seed and 0x7fffffff) % 100) / 100f * (if (real) 0.11f else 0.06f)
    if (tone > 0f) drawRect(Color.Black.copy(alpha = tone))
    val px = density
    if (real) {
        // The case's rounded back: dark where it turns away at both edges, a soft sheen a third of the way in.
        drawRect(Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.34f), 0.16f to Color.Transparent, 0.30f to Color.White.copy(alpha = 0.10f),
            0.55f to Color.Transparent, 0.84f to Color.Black.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.46f)
        ))
        // The lit front edge (left) and the shaded back edge (right).
        drawRect(Color.White.copy(alpha = 0.34f), Offset.Zero, androidx.compose.ui.geometry.Size(px.coerceAtMost(w / 4f), h))
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(w - px.coerceAtMost(w / 4f), 0f), androidx.compose.ui.geometry.Size(px.coerceAtMost(w / 4f), h))
        // The top cap: a lit bevel.
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 1f to Color.Transparent, endY = (3f * px).coerceAtMost(h / 4f)))
        // Foot: where it stands on the board.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.40f), startY = h * 0.86f, endY = h))
    } else {
        drawRect(Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.12f), 0.12f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.26f)
        ))
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 1f to Color.Transparent, endY = (2f * px).coerceAtMost(h / 4f)))
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f), startY = h * 0.92f, endY = h))
    }
}

