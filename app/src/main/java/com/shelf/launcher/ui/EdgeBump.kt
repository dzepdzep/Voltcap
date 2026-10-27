package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * What happens when you push past the end of the games: with "Endless" off the row bumps against the edge and an
 * accent glow lights up there ([hit]); with it on, layouts that can't slide round on their own re-enter from the
 * other side ([wrap]) instead of just jumping back to the first game. The view model calls these; [edgeBump] plays them.
 */
internal object EdgeBump {
    var tick by mutableIntStateOf(0)
        private set
    @Volatile var dir = 0
        private set
    @Volatile var wrapped = false
        private set

    /** Pushed [dir] (+1 forward, -1 back) against the end. */
    fun hit(dir: Int) {
        this.dir = dir.coerceIn(-1, 1)
        wrapped = false
        tick++
    }

    /** Went round from one end to the other in direction [dir]. */
    fun wrap(dir: Int) {
        this.dir = dir.coerceIn(-1, 1)
        wrapped = true
        tick++
    }
}

/** Plays [EdgeBump] on whatever it is put on: a rubber-band nudge with an edge glow, or a slide in from the far side. */
@Composable
internal fun Modifier.edgeBump(): Modifier {
    val offset = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    var shownDir by remember { mutableIntStateOf(0) }
    val tick = EdgeBump.tick
    // A bump that happened before this row came on screen is not replayed.
    val seen = remember { EdgeBump.tick }
    LaunchedEffect(tick) {
        if (tick == seen) return@LaunchedEffect
        val d = EdgeBump.dir
        shownDir = d
        // An older animation cut short must not leave its state behind.
        offset.snapTo(0f); fade.snapTo(1f); glow.snapTo(0f)
        if (EdgeBump.wrapped) {
            // Arrives from the side it went off: a short slide with a fade.
            offset.snapTo(d * 0.16f)
            fade.snapTo(0.35f)
            kotlinx.coroutines.coroutineScope {
                launch { offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
                launch { fade.animateTo(1f, tween(260)) }
            }
        } else {
            glow.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { glow.animateTo(1f, tween(100)); glow.animateTo(0f, tween(420)) }
                launch {
                    offset.snapTo(0f)
                    offset.animateTo(-d * 0.03f, tween(100, easing = FastOutSlowInEasing))
                    offset.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }
    // (QA pass 2, D22: the accent is read while drawing — read here it re-composed the home screen with every frame of
    // the colour glide, since this modifier sits on the row's box in the screen itself.)
    val live = ShelfTheme.live
    return this
        .graphicsLayer {
            translationX = offset.value * size.width
            alpha = fade.value
        }
        .drawWithContent {
            drawContent()
            val g = glow.value
            if (g > 0.01f && shownDir != 0) {
                val accent = live.value.accent
                val w = size.width * 0.12f
                val right = shownDir > 0
                drawRect(
                    Brush.horizontalGradient(
                        if (right) listOf(Color.Transparent, accent.copy(alpha = 0.45f * g)) else listOf(accent.copy(alpha = 0.45f * g), Color.Transparent),
                        startX = if (right) size.width - w else 0f, endX = if (right) size.width else w
                    ),
                    topLeft = Offset(if (right) size.width - w else 0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height)
                )
            }
        }
}
