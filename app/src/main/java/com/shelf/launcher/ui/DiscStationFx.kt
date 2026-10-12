package com.shelf.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game

/*
 * Update 151: the Disc station's extra life (StyleDiscStation.kt): a soft rainbow sheen on the disc that turns with it,
 * a faint glow ring round the disc in the game's own colour, the game's logo above the disc (else its name in big
 * letters), and the text style with a soft shadow for the panel on the right. All drawn once and cached: the sheen sits
 * inside the disc's turning layer, so a spinning disc never redraws it.
 */

/**
 * A soft rainbow sheen over a disc [frac] of the box across (a GameCube disc is drawn smaller): two faint rainbow fans
 * opposite each other, like light on a real disc, between the hub and the rim. Drawn over the content.
 */
internal fun Modifier.discSheen(frac: Float = 1f): Modifier = this.drawWithCache {
    val r = size.minDimension / 2f * frac
    val inner = r * 0.24f
    val outer = r * 0.97f
    val mid = (inner + outer) / 2f
    val clear = Color(0x00FFFFFF)
    val center = size.center
    val brush = Brush.sweepGradient(
        listOf(
            clear, Color(0x38FF6B8B), Color(0x38FFD36B), Color(0x386BFFB0), Color(0x386BC8FF), Color(0x38C36BFF), clear,
            clear, Color(0x30FF6B8B), Color(0x30FFD36B), Color(0x306BFFB0), Color(0x306BC8FF), Color(0x30C36BFF), clear
        ),
        center = center
    )
    val shine = Brush.radialGradient(listOf(Color(0x22FFFFFF), clear), center = Offset(center.x - r * 0.3f, center.y - r * 0.35f), radius = r * 0.8f)
    val ring = Stroke(width = outer - inner)
    onDrawWithContent {
        drawContent()
        drawCircle(brush, radius = mid, center = center, style = ring)
        drawCircle(shine, radius = outer, center = center)
    }
}

/**
 * A faint glow round a disc [frac] of the box across, in [color]: a soft halo just outside the rim and a thin bright
 * ring on it. Drawn behind the content (cached; redrawn only when the colour or size changes).
 */
internal fun Modifier.discGlow(color: Color, frac: Float = 1f): Modifier = this.drawWithCache {
    val r = size.minDimension / 2f * frac
    val center = size.center
    val halo = Brush.radialGradient(
        0.0f to Color.Transparent, 0.74f to color.copy(alpha = 0.0f), 0.82f to color.copy(alpha = 0.42f), 1f to Color.Transparent,
        center = center, radius = r * 1.22f
    )
    val ringW = r * 0.022f
    onDrawBehind {
        drawCircle(halo, radius = r * 1.22f, center = center)
        drawCircle(color.copy(alpha = 0.55f), radius = r + ringW * 1.5f, center = center, style = Stroke(width = ringW))
    }
}

/**
 * The game's own colour for the glow: its art averaged and pushed bright and rich (rememberArtColor's idea, but light
 * enough to glow); [fallback] (the theme's accent) until the art is there.
 */
@Composable
internal fun rememberDiscGlow(art: Bitmap?, fallback: Color): Color {
    val sampled = remember(art) {
        art?.let { bitmap ->
            runCatching {
                // Scaling to a single pixel averages the whole picture (once per picture).
                val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(pixel, hsv)
                hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
                hsv[2] = 0.95f
                Color(android.graphics.Color.HSVToColor(hsv))
            }.getOrNull()
        }
    }
    return sampled ?: fallback
}

/** Text on the Disc station's panel: the interface font with a soft dark shadow, so it reads over any picture. */
@Composable
internal fun discTextStyle(): TextStyle =
    uiFontStyle().copy(shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 1.5f), 6f))

/** Above the disc: the game's logo, or (no logo, or it failed to load) its name in big letters. */
@Composable
internal fun DiscTitleArt(c: StyleCtx, g: Game, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val logo = c.logo(g)
    var failed by remember(logo) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (logo != null && !failed) {
            AsyncImage(
                logo, titles.display(g.name), contentScale = ContentScale.Fit, onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FitText(
                titles.display(g.name), Color.White, maxSize = if (compact) 20.sp else 28.sp, minSize = 12.sp, maxLines = 2,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center, style = discTextStyle()
            )
        }
    }
}
