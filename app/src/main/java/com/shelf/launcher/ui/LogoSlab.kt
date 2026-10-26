package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Update 330: a game's logo or icon (your pick, SteamGridDB's / the scraped logo, its RetroAchievements icon) drawn as a
 * solid 3D badge, the way the PS2's memory-card browser shows its save icons: the picture cut out and extruded (stacked
 * copies behind it shaded as its edge), lit from the front, turning slowly about its upright when it's the one you're on
 * (else held at a three-quarter turn). Turned past the side, its back shows — the picture mirrored and darker. One
 * Canvas, the picture drawn a dozen times: no 3D engine, no text (safe from Android 13's turned-text cache).
 */
@Composable
internal fun LogoSlab(
    model: Any?,
    modifier: Modifier,
    turning: Boolean,
    fast: Boolean = true,
    restAngle: Float = -25f,
    /** The badge's thickness, as a share of its width. */
    depth: Float = 0.07f,
    /** Drawn instead when the picture can't be loaded. */
    fallback: @Composable () -> Unit = {}
) {
    // (A fixed size to load at: drawn by hand below, the painter never got a size from its first draw and so never
    // started loading — Toni: "the games that don't have the icons are shown empty".)
    val context = androidx.compose.ui.platform.LocalContext.current
    val request = androidx.compose.runtime.remember(model) {
        coil.request.ImageRequest.Builder(context).data(model).size(512).allowHardware(true).build()
    }
    val painter = rememberAsyncImagePainter(request)
    val st = painter.state
    if (st is AsyncImagePainter.State.Error || model == null) {
        Box(modifier) { fallback() }
        return
    }
    val turn = if (turning) rememberInfiniteTransition(label = "logoSlab").animateFloat(
        0f, 360f, infiniteRepeatable(tween(if (fast) 7_000 else 14_000, easing = LinearEasing)), label = "logoSlabA"
    ) else null
    Canvas(modifier) {
        val intrinsic = painter.intrinsicSize
        if (intrinsic.width <= 0f || intrinsic.height <= 0f || intrinsic.width.isNaN()) return@Canvas
        val ang = Math.toRadians((turn?.value ?: restAngle).toDouble())
        val c = cos(ang).toFloat(); val s = sin(ang).toFloat()
        // The picture as big as fits with room for the turn and its edge.
        val k = min(size.width * 0.84f / intrinsic.width, size.height * 0.84f / intrinsic.height)
        val w = intrinsic.width * k; val h = intrinsic.height * k
        val d = w * depth
        val layers = 10
        val front = c >= 0f
        // Lit from the front: brightest facing you, dimmer turned away.
        val light = 0.55f + 0.45f * abs(c)
        val edge = Color(0xFF2A2C33)
        for (j in 0 until layers) {
            // Farthest from you first.
            val i = if (front) layers - 1 - j else j
            val z = d * i / (layers - 1f)
            val isFront = i == 0; val isBack = i == layers - 1
            val filter = when {
                isFront -> if (light < 0.98f) ColorFilter.tint(Color.Black.copy(alpha = 1f - light), BlendMode.SrcAtop) else null
                isBack -> ColorFilter.tint(Color.Black.copy(alpha = 0.55f), BlendMode.SrcAtop)
                else -> ColorFilter.tint(edge.copy(alpha = 0.92f), BlendMode.SrcAtop)
            }
            translate(size.width / 2f + z * s, size.height / 2f) {
                scale(c.takeIf { abs(it) > 0.02f } ?: 0.02f, 1f, pivot = Offset.Zero) {
                    translate(-w / 2f, -h / 2f) {
                        with(painter) { draw(Size(w, h), colorFilter = filter) }
                    }
                }
            }
        }
    }
}
