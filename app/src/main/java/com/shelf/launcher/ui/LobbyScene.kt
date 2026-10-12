package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.shelf.launcher.ui.theme.ShelfColors

/*
 * Update 158: the Lobby theme's scene. A calm, light menu backdrop of Voltcap's own: near-white fading to a very
 * light grey from top to bottom, very faint thin lines across the whole screen, and a soft wash of the accent
 * along the bottom edge. The lines drift down very slowly; they hold still with Lite effects, while the home
 * screen sits idle, behind a window, or when the system's animations are off.
 *
 * Cheap on purpose: one gradient, one tiled line pattern (a 1-pixel-wide picture made once per size), one wash.
 * Nothing is made per frame, and the clock runs at most ~20 times a second (the lines move a few pixels a second).
 */
@Composable
internal fun LobbyScene(colors: ShelfColors, modifier: Modifier = Modifier) {
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    val active = rememberIsActive() && rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current && !HomeIdle.idle
    val time = remember { mutableFloatStateOf(0f) }
    val tuning = LocalSceneTuning.current
    SceneClock(active, time, maxOf(idleFrameMs(), 50), tuning.speed)
    val dim = tuning.dim
    Spacer(
        modifier.drawWithCache {
            val top = lerp(colors.background, Color.White, 0.6f)
            val bottom = lerp(colors.background, colors.surface, 0.85f)
            val gradient = Brush.verticalGradient(listOf(top, colors.background, bottom))
            // One line every ~4 dp, one pixel thick, in the text colour at a whisper.
            val period = maxOf(3, (4.dp.toPx()).toInt())
            val tile = android.graphics.Bitmap.createBitmap(1, period, android.graphics.Bitmap.Config.ARGB_8888)
            tile.setPixel(0, 0, colors.textPrimary.copy(alpha = 0.045f).toArgb())
            val lines = ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
            val wash = Brush.verticalGradient(
                listOf(Color.Transparent, colors.accent.copy(alpha = 0.07f)),
                startY = size.height * 0.72f, endY = size.height
            )
            val p = period.toFloat()
            onDrawBehind {
                drawRect(gradient)
                // One step of the pattern every ~2.5 s (reading the time here only redraws, nothing recomposes).
                val off = (time.floatValue / 2.5f % 1f) * p
                translate(top = off) { drawRect(lines, topLeft = Offset(0f, -p), size = Size(size.width, size.height + p)) }
                drawRect(wash)
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        }
    )
}

/** Update 158: the Lobby theme's still picture for the theme browser (no shader of its own to render). */
internal fun lobbyThumb(w: Int, h: Int, colors: ShelfColors): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val top = lerp(colors.background, Color.White, 0.6f).toArgb()
    val bottom = lerp(colors.background, colors.surface, 0.85f).toArgb()
    val paint = android.graphics.Paint()
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 0f, h.toFloat(), intArrayOf(top, colors.background.toArgb(), bottom), null, android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null
    paint.color = colors.textPrimary.copy(alpha = 0.06f).toArgb()
    var y = 0f
    while (y < h) { canvas.drawRect(0f, y, w.toFloat(), y + 1f, paint); y += 3f }
    paint.shader = android.graphics.LinearGradient(
        0f, h * 0.72f, 0f, h.toFloat(), android.graphics.Color.TRANSPARENT, colors.accent.copy(alpha = 0.1f).toArgb(),
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), paint)
    return bmp
}
