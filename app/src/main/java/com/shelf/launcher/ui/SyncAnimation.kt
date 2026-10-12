package com.shelf.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.Dp
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.PI
import kotlin.math.sin

/** Colours for save-sync results, shared by the corner notice and the badges. */
internal val SyncGood = Color(0xFF4CC38A)
internal val SyncRestored = Color(0xFF4FB3FF)
internal val SyncWarn = Color(0xFFFFB547)

/**
 * The save-sync picture, in Voltcap's own look: a little charge capsule with a lightning bolt in it.
 * While saves travel, energy chevrons run through the capsule (towards its tip when backing up, back
 * from it when restoring) over a glowing charge; synced, the capsule is full and green with a check;
 * waiting, it holds a small charge; when something is wrong it's empty with an amber "!". Only the
 * transfer animates, and only while Voltcap is on screen (Compose pauses its frame clock when the
 * launcher is stopped) and not with Lite effects.
 *
 * [size] is the height; the picture is 1.6× as wide.
 */
@Composable
fun SyncAnimation(state: SyncState, size: Dp, modifier: Modifier = Modifier, fresh: Boolean = false) {
    val transferring = state == SyncState.UPLOADING || state == SyncState.DOWNLOADING
    val lite = LocalLiteEffects.current
    // The infinite transition only exists while animating; leaving composition stops it.
    val phase: State<Float>? = if (transferring && !lite) {
        rememberInfiniteTransition(label = "sync").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "syncPhase"
        )
    } else null
    // A just-finished sync: a soft glow that fades out over a few seconds (the result is in the window).
    val glow by androidx.compose.animation.core.animateFloatAsState(
        if (fresh && !transferring) 1f else 0f, tween(if (fresh) 300 else 2500), label = "syncGlow"
    )
    val accent = ShelfTheme.accent
    val muted = Color.White.copy(alpha = 0.6f)
    Canvas(modifier.size(width = size * 1.6f, height = size)) {
        val h = this.size.height
        val w = this.size.width
        val c = Offset(w * 0.45f, h * 0.5f)
        val outline = when {
            transferring -> accent
            state == SyncState.SYNCED -> Color.White.copy(alpha = 0.92f)
            else -> muted
        }
        if (glow > 0.01f) {
            val gc = if (state == SyncState.SYNCED) SyncGood else if (state == SyncState.PENDING) accent else SyncWarn
            drawOval(
                Brush.radialGradient(listOf(gc.copy(alpha = 0.45f * glow), Color.Transparent), center = c, radius = w * 0.5f),
                Offset(c.x - w * 0.5f, c.y - h * 0.6f), Size(w, h * 1.2f)
            )
        }
        // The capsule and its tip.
        val stroke = h * 0.075f
        val left = w * 0.06f
        val top = h * 0.2f
        val bw = w * 0.74f
        val bh = h * 0.6f
        val r = CornerRadius(bh / 2f)
        drawRoundRect(outline, Offset(left + bw + h * 0.04f, c.y - h * 0.11f), Size(h * 0.09f, h * 0.22f), CornerRadius(h * 0.04f))
        val inset = stroke * 1.4f
        val inner = Path().apply {
            addRoundRect(RoundRect(left + inset, top + inset, left + bw - inset, top + bh - inset, CornerRadius((bh - 2 * inset) / 2f)))
        }
        val iw = bw - 2 * inset
        clipPath(inner) {
            when {
                transferring -> {
                    val p = phase?.value ?: 0.3f
                    // The charge, breathing a little with the flow.
                    val level = 0.55f + 0.12f * sin(p * 2f * PI.toFloat())
                    drawRect(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFFFFD84A).copy(alpha = 0.55f)), left, left + bw),
                        Offset(left, top), Size(bw * level, bh)
                    )
                    // Energy chevrons running through it.
                    val dir = if (state == SyncState.DOWNLOADING) -1f else 1f
                    for (k in 0 until 3) {
                        val u = ((p + k / 3f) % 1f)
                        val x = left + inset + (if (dir > 0) u else 1f - u) * iw
                        val fade = sin(u * PI.toFloat())
                        val a = h * 0.11f * dir
                        val chevron = Path().apply {
                            moveTo(x - a, c.y - h * 0.14f); lineTo(x, c.y); lineTo(x - a, c.y + h * 0.14f)
                        }
                        drawPath(chevron, Color.White.copy(alpha = 0.75f * fade), style = Stroke(h * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                    }
                }
                state == SyncState.SYNCED -> drawRect(
                    Brush.horizontalGradient(listOf(SyncGood.copy(alpha = 0.7f), SyncGood), left, left + bw), Offset(left, top), Size(bw, bh)
                )
                state == SyncState.PENDING -> drawRect(accent.copy(alpha = 0.4f), Offset(left, top), Size(bw * 0.35f, bh))
                else -> Unit
            }
        }
        drawRoundRect(outline, Offset(left, top), Size(bw, bh), r, style = Stroke(stroke))
        // The bolt in the middle.
        val boltColor = when {
            transferring || state == SyncState.SYNCED -> Color.White
            state == SyncState.PENDING -> Color.White.copy(alpha = 0.85f)
            else -> muted
        }
        drawBolt(Offset(left + bw / 2f, c.y), h * 0.42f, boltColor)

        // Status mark at the lower right.
        val mark = Offset(w * 0.84f, h * 0.74f)
        when (state) {
            SyncState.SYNCED -> {
                drawCircle(SyncGood, radius = h * 0.17f, center = mark)
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = h * 0.17f, center = mark, style = Stroke(h * 0.03f))
                val path = Path().apply {
                    moveTo(mark.x - h * 0.075f, mark.y)
                    lineTo(mark.x - h * 0.018f, mark.y + h * 0.055f)
                    lineTo(mark.x + h * 0.08f, mark.y - h * 0.055f)
                }
                drawPath(path, Color.White, style = Stroke(width = h * 0.04f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            SyncState.OFFLINE, SyncState.ERROR, SyncState.NEEDS_SIGN_IN, SyncState.CONFLICT -> {
                drawCircle(SyncWarn, radius = h * 0.17f, center = mark)
                drawLine(Color.Black.copy(alpha = 0.85f), Offset(mark.x, mark.y - h * 0.08f), Offset(mark.x, mark.y + h * 0.015f), strokeWidth = h * 0.04f, cap = StrokeCap.Round)
                drawCircle(Color.Black.copy(alpha = 0.85f), radius = h * 0.024f, center = Offset(mark.x, mark.y + h * 0.07f))
            }
            else -> Unit
        }
    }
}

/** Voltcap's lightning bolt, [s] tall, centred on [c]. */
private fun DrawScope.drawBolt(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x + 0.12f * s, c.y - 0.5f * s)
        lineTo(c.x - 0.24f * s, c.y + 0.06f * s)
        lineTo(c.x - 0.01f * s, c.y + 0.06f * s)
        lineTo(c.x - 0.12f * s, c.y + 0.5f * s)
        lineTo(c.x + 0.24f * s, c.y - 0.06f * s)
        lineTo(c.x + 0.01f * s, c.y - 0.06f * s)
        close()
    }
    drawPath(path, Color.Black.copy(alpha = 0.35f), style = Stroke(s * 0.1f, join = StrokeJoin.Round))
    drawPath(path, color)
}
