package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.BackdropAchievement
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.sin

/**
 * Achievement touches inside the normal home backdrop (any theme — there is no separate achievement
 * theme): a few recently unlocked badges drifting softly, the focused game's progress ring with its
 * percentage, and faint trophy sparkles. Each is switched on under Appearance › Achievements › In the
 * backdrop. Nothing runs when all are off; the motion is a slow ~12 fps clock that stops while the
 * home screen is covered or idle, and everything stays inside the safe zone.
 *
 * [badgeUrls]: badge pictures of the latest unlocks; [progress]: 0..1 of the game you're on, or null.
 */
@Composable
internal fun BackdropAchievementsLayer(
    badgeUrls: List<String>,
    progress: Float?,
    covered: Boolean,
    modifier: Modifier = Modifier
) {
    val on = Features.backdropAchievements.state.value
    if (on.isEmpty()) return
    val zone = safeZone()
    val active = !covered && rememberIsActive() && rememberAnimationsEnabled() && !HomeIdle.still
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val base = time.floatValue
        val start = withFrameNanos { it }
        // ~12 updates a second, sleeping in between (waiting on every display frame woke the app 60-120
        // times a second just to skip most of them).
        while (true) {
            val now = withFrameNanos { it }
            time.floatValue = base + (now - start) / 1e9f
            kotlinx.coroutines.delay(80)
        }
    }
    val accent = ShelfTheme.accent
    val gold = Color(0xFFF2C14E)
    Box(modifier.fillMaxSize().padding(zone.padding())) {
        if (BackdropAchievement.PARTICLES.name in on) {
            Canvas(Modifier.fillMaxSize()) {
                val t = time.floatValue
                for (i in 0 until 18) {
                    val fx = ((i * 7919) % 1000) / 1000f
                    val fy = ((i * 104729 + 311) % 1000) / 1000f
                    val y = ((fy - t * (0.006f + 0.004f * (i % 3))) % 1f + 1f) % 1f
                    val tw = 0.5f + 0.5f * sin(t * (0.7f + (i % 5) * 0.2f) + i)
                    val c = Offset(fx * size.width, y * size.height)
                    val r = (1.6f + (i % 3)) * density * (0.6f + 0.4f * tw)
                    drawCircle((if (i % 4 == 0) accent else gold).copy(alpha = 0.10f + 0.18f * tw), r * 3f, c)
                    drawCircle(Color.White.copy(alpha = 0.25f * tw), r, c)
                }
            }
        }
        if (BackdropAchievement.BADGES.name in on && badgeUrls.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val size = if (zone.compact) 34.dp else 46.dp
                badgeUrls.take(5).forEachIndexed { i, url ->
                    val fx = listOf(0.06f, 0.88f, 0.18f, 0.74f, 0.5f)[i]
                    val fy = listOf(0.16f, 0.22f, 0.62f, 0.55f, 0.08f)[i]
                    val x = (maxWidth - size) * fx
                    val yBase = (maxHeight - size) * fy
                    AsyncImage(
                        model = url, contentDescription = null,
                        modifier = Modifier
                            .offset(x, yBase)
                            .size(size)
                            .graphicsLayer {
                                val t = time.floatValue
                                translationY = sin(t * 0.5f + i * 1.7f) * 6.dp.toPx()
                                rotationZ = sin(t * 0.3f + i) * 4f
                            }
                            .alpha(0.22f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
        if (BackdropAchievement.PROGRESS.name in on && progress != null) {
            Column(Modifier.align(Alignment.TopEnd).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val ring = if (zone.compact) 44.dp else 58.dp
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(ring).alpha(0.55f)) {
                        val sw = 4.dp.toPx()
                        drawCircle(Color.White.copy(alpha = 0.12f), size.minDimension / 2 - sw, style = Stroke(sw))
                        drawArc(
                            if (progress >= 1f) gold else accent, -90f, 360f * progress.coerceIn(0f, 1f), false,
                            topLeft = Offset(sw, sw), size = androidx.compose.ui.geometry.Size(size.width - 2 * sw, size.height - 2 * sw),
                            style = Stroke(sw, cap = StrokeCap.Round)
                        )
                    }
                    Text("${(progress * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
