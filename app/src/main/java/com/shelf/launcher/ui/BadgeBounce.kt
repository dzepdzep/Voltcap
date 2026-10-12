package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.floor

/** What Badge bounce shows: your mastered games and latest unlocked badges (RetroAchievements and Steam), set by the home screen. */
internal object BadgeBounce {
    var items by mutableStateOf<List<TrophyItem>>(emptyList())
    const val MAX = 16
}

/**
 * Badge bounce: your earned badges drifting round the screen and bouncing off its edges, each in its own full
 * quality (loaded at the size the picture really is, scaled smoothly — never pixelated), a little see-through so
 * they stay a background. Mastered games get a thin gold frame, unlocked badges a silver one. Without any badges
 * yet (not signed in), drawn trophies in the theme's colours stand in.
 *
 * Cheap: one canvas, a clock read in the draw phase (nothing recomposes per frame), at most 16 small pictures;
 * a still frame while the home screen is covered, idle for long or with Lite effects.
 */
@Composable
internal fun BadgeBounceLayer(modifier: Modifier) {
    val context = LocalContext.current
    val liteStill = LocalLiteEffects.current && !LocalAnimateOnLite.current
    val active = rememberIsActive() && rememberAnimationsEnabled() && !liteStill && !LocalSceneCovered.current
    val tuning = LocalSceneTuning.current
    val time = remember { mutableFloatStateOf(STILL_FRAME_TIME * 3f) }
    SceneClock(active, time, idleFrameMs(), tuning.speed)

    val items = BadgeBounce.items
    val pictures by produceState(emptyList<Pair<TrophyItem, ImageBitmap>>(), items) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            items.mapNotNull { item ->
                runCatching {
                    val req = coil.request.ImageRequest.Builder(context)
                        .data(item.url)
                        // The picture's own size (badges are 64 px, game icons 96): only big covers are brought down.
                        .size(coil.size.Size(320, 320))
                        .scale(coil.size.Scale.FIT)
                        .precision(coil.size.Precision.INEXACT)
                        .allowHardware(false)
                        .memoryCacheKey("bounce|${item.url}")
                        .build()
                    val d = (coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable
                    val bmp: Bitmap? = (d as? BitmapDrawable)?.bitmap
                    bmp?.takeIf { !it.isRecycled }?.let { item to it.asImageBitmap() }
                }.getOrNull()
            }
        }
    }
    val accent = ShelfTheme.accent
    val secondary = ShelfTheme.colors.secondary
    val badgeDp = 104.dp
    val density = LocalDensity.current
    val sizePx = with(density) { badgeDp.toPx() }
    val speedPx = with(density) { 46.dp.toPx() }
    val stand = remember { Path() }

    Canvas(modifier) {
        val t = time.floatValue
        val n = if (pictures.isNotEmpty()) pictures.size else 7
        for (i in 0 until n) {
            val pic = pictures.getOrNull(i)
            val img = pic?.second
            // Its size: badges square; a game's cover keeps its shape (as tall as a badge).
            val h = sizePx * (if (pic?.first?.mastered == true) 1.12f else 1f)
            val w = if (img != null) h * img.width / img.height.coerceAtLeast(1) else h
            // Each one drifts at its own speed and angle and bounces off the edges (a triangle wave per axis).
            val r1 = hash(i * 3 + 1)
            val r2 = hash(i * 7 + 2)
            val r3 = hash(i * 11 + 3)
            val sp = speedPx * (0.7f + 0.7f * r1)
            val ang = 0.35f + 0.9f * r2
            val vx = sp * kotlin.math.cos(ang) * (if (r3 > 0.5f) 1f else -1f)
            val vy = sp * kotlin.math.sin(ang) * (if (r1 > 0.5f) 1f else -1f)
            val roomX = (size.width - w).coerceAtLeast(1f)
            val roomY = (size.height - h).coerceAtLeast(1f)
            val x = bounce(r2 * roomX + vx * t, roomX)
            val y = bounce(r3 * roomY + vy * t, roomY)
            val corner = CornerRadius(minOf(w, h) * 0.16f)
            // A soft shadow under it.
            drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(x + 6f, y + 10f), Size(w, h), corner)
            if (img != null) {
                stand.reset()
                stand.addRoundRect(androidx.compose.ui.geometry.RoundRect(x, y, x + w, y + h, corner))
                clipPath(stand) {
                    drawImage(
                        img,
                        srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()),
                        alpha = 0.62f,
                        filterQuality = FilterQuality.High
                    )
                }
                val frame = if (pic.first.mastered) Color(0xFFF5C54A) else Color(0xFFD7DCE6)
                drawRoundRect(frame.copy(alpha = 0.55f), Offset(x, y), Size(w, h), corner, style = Stroke(2.dp.toPx()))
            } else {
                // No badges yet: a drawn medal in the theme's colours.
                val c = Offset(x + w / 2f, y + h / 2f)
                val tint = if (i % 2 == 0) accent else secondary
                drawCircle(tint.copy(alpha = 0.28f), w * 0.46f, c)
                drawCircle(tint.copy(alpha = 0.6f), w * 0.46f, c, style = Stroke(3.dp.toPx()))
                drawCircle(tint.copy(alpha = 0.45f), w * 0.3f, c, style = Stroke(2.dp.toPx()))
                val s = w * 0.16f
                stand.reset()
                // A star in the middle.
                for (k in 0 until 10) {
                    val a = (k / 10f) * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
                    val rr = if (k % 2 == 0) s else s * 0.45f
                    val px = c.x + kotlin.math.cos(a) * rr
                    val py = c.y + kotlin.math.sin(a) * rr
                    if (k == 0) stand.moveTo(px, py) else stand.lineTo(px, py)
                }
                stand.close()
                drawPath(stand, tint.copy(alpha = 0.7f))
            }
        }
    }
}

/** Position on a 0..[room] line bouncing back and forth, for an unbounded travelled distance [d]. */
private fun bounce(d: Float, room: Float): Float {
    val u = d / room
    val f = u - 2f * floor(u / 2f)          // 0..2
    return (1f - abs(1f - f)) * room
}

/** A stable 0..1 number for [i]. */
private fun hash(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}
