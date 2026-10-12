package com.shelf.launcher.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features

/** Picture-only filter: labels and controls keep their full resolution and focus semantics. */
@Composable
internal fun Modifier.pixelArtwork(): Modifier {
    if (!Features.pixelArtwork.value || Build.VERSION.SDK_INT < 33) return this
    // Measured in dp: a few physical pixels were invisible on a 1080p handheld (the effect "did nothing").
    val block = Features.pixelSize.value.px * androidx.compose.ui.platform.LocalDensity.current.density * 1.15f
    val shader = remember(block) { android.graphics.RuntimeShader("""
        uniform shader image;
        uniform float block;
        half4 main(float2 p) {
            half4 c = image.eval((floor(p / block) + 0.5) * block);
            // 32-bit style colours: fewer shades per channel, nudged by a 2x2 ordered dither.
            float2 cell = floor(p / block);
            float d = (mod(cell.x, 2.0) + 2.0 * mod(cell.y, 2.0)) / 4.0 - 0.375;
            return half4(floor(c.rgb * 15.0 + 0.5 + d * 0.6) / 15.0, c.a);
        }
    """).apply { setFloatUniform("block", block) } }
    val effect = remember(shader) { android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect() }
    return graphicsLayer { renderEffect = effect }
}

/*
 * Shapes for the shared UI pieces (cards, chips, rows, folds, tiles, dialogs): rounded normally, and with the
 * 32-bit style's Pixel UI on, square panels with small chamfered corners (a console menu's look). Reading
 * the switch's state makes every piece redraw when it changes.
 */
internal fun uiRounded(size: androidx.compose.ui.unit.Dp): androidx.compose.ui.graphics.Shape =
    if (Features.pixelUi.state.value) androidx.compose.foundation.shape.CutCornerShape(if (size >= 8.dp) 3.dp else 0.dp)
    else androidx.compose.foundation.shape.RoundedCornerShape(size)

internal fun uiRounded(percent: Int): androidx.compose.ui.graphics.Shape =
    if (Features.pixelUi.state.value) androidx.compose.foundation.shape.CutCornerShape(if (percent >= 20) 4.dp else 0.dp)
    else androidx.compose.foundation.shape.RoundedCornerShape(percent)

internal fun uiRounded(px: Float): androidx.compose.ui.graphics.Shape =
    if (Features.pixelUi.state.value) androidx.compose.foundation.shape.CutCornerShape(minOf(px, 6f))
    else androidx.compose.foundation.shape.RoundedCornerShape(px)
