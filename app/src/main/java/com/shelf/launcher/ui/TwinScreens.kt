package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.HomeLayout

/*
 * Update 158: the two-screen layouts, Twin desk (TwinDesk.kt) and Twin icons (TwinIcons.kt). Each has a top part
 * (the main screen) and a bottom part (the second screen, e.g. the AYN Thor's lower screen). With a second screen up
 * and Settings › Screens › "Use the second screen" on, the main screen draws the top part and the second screen the
 * bottom part (LauncherScreen's SecondScreenContent asks [TwinSecondScreen] for it). Without one, the main screen
 * draws both parts, one above the other (or side by side when that fits much bigger), scaled to fit, so the layout
 * still works on a one-screen handheld. Both parts read the same StyleCtx: the D-pad, taps and the game you're on
 * are shared through the view model.
 */

/** Update 158: the twin layout's context as the main screen drew it last (null while no twin layout is on). */
internal object TwinScreens {
    var ctx by mutableStateOf<StyleCtx?>(null)
}

/** Update 158: one of the two-screen layouts. */
internal val HomeLayout.isTwin: Boolean get() = this == HomeLayout.TWIN_DESK || this == HomeLayout.TWIN_ICONS

/** Update 158: the bottom part is on the second screen right now (Voltcap's own second screen is up and in use). */
internal fun twinOnSecond(state: LauncherUiState): Boolean =
    state.appearance.useSecondScreen && SecondScreenShown.value

/**
 * Update 158: draws a twin layout on the main screen: only [top] while the second screen shows the bottom part,
 * else [top] and [bottom] together, scaled to fit. Each part gets its modifier and whether it is drawn as a small
 * framed screen (true) or fills a whole screen (false).
 */
@Composable
internal fun TwinHost(
    c: StyleCtx,
    modifier: Modifier,
    top: @Composable (Modifier, Boolean) -> Unit,
    bottom: @Composable (Modifier, Boolean) -> Unit
) {
    // The second screen draws the bottom part from this.
    // (Cleared on leaving only while it is still this layout's, so switching to the other twin layout keeps it.)
    val mine = remember { arrayOfNulls<StyleCtx>(1) }
    SideEffect { mine[0] = c; TwinScreens.ctx = c }
    DisposableEffect(Unit) { onDispose { if (TwinScreens.ctx === mine[0]) TwinScreens.ctx = null } }
    if (twinOnSecond(c.state)) {
        top(modifier.fillMaxSize(), false)
    } else {
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val gap = 10.dp
            val topH = 250.dp
            val bottomH = 300.dp
            val minW = 360.dp
            // One above the other (the way the handheld holds its two screens), or side by side on a wide screen
            // when that is clearly bigger.
            val sStack = minOf(maxHeight / (topH + bottomH + gap), maxWidth / minW)
            val sSide = minOf(maxWidth / (minW * 2 + gap), maxHeight / 280.dp)
            val side = sSide > sStack * 1.15f
            if (side) {
                val s = sSide.coerceAtLeast(0.3f)
                val h = minOf(maxHeight / s, 340.dp)
                val w = minOf((maxWidth / s - gap) / 2, 560.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap * s), verticalAlignment = Alignment.CenterVertically) {
                    TwinScaled(w, h, s) { m -> top(m, true) }
                    TwinScaled(w, h, s) { m -> bottom(m, true) }
                }
            } else {
                val s = sStack.coerceAtLeast(0.3f)
                val w = minOf(maxWidth / s, 560.dp)
                Column(verticalArrangement = Arrangement.spacedBy(gap * s), horizontalAlignment = Alignment.CenterHorizontally) {
                    TwinScaled(w, topH, s) { m -> top(m, true) }
                    TwinScaled(w, bottomH, s) { m -> bottom(m, true) }
                }
            }
        }
    }
}

/** Update 158: [content] laid out at [w] x [h] and drawn [s] times that size (taps follow the scale). */
@Composable
private fun TwinScaled(w: Dp, h: Dp, s: Float, content: @Composable (Modifier) -> Unit) {
    Box(Modifier.size(w * s, h * s), contentAlignment = Alignment.Center) {
        content(Modifier.requiredSize(w, h).graphicsLayer { scaleX = s; scaleY = s })
    }
}

/** Update 158: what the second screen shows while a twin layout is on: its bottom part, filling the screen. */
@Composable
internal fun TwinSecondScreen(c: StyleCtx) {
    if (c.state.appearance.homeLayout == HomeLayout.TWIN_ICONS) TwinIconsBottom(c, Modifier.fillMaxSize(), framed = false)
    else TwinDeskBottom(c, Modifier.fillMaxSize(), framed = false)
}

/**
 * Update 158: the time now, refreshed once a second while [seconds] (else once a minute) and only while Voltcap is
 * in front. Read it inside a draw block or a small composable, so only that redraws.
 */
@Composable
internal fun rememberTwinTime(seconds: Boolean): LongState {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val resumed = rememberIsResumed()
    LaunchedEffect(seconds, resumed) {
        if (resumed) {
            while (true) {
                val t = System.currentTimeMillis()
                now.longValue = t
                val step = if (seconds) 1_000L else 60_000L
                kotlinx.coroutines.delay(step - t % step + 15L)
            }
        }
    }
    return now
}

/**
 * Update 158: a list's scroll place (in rows) that glides to [target], or jumps there when it is more than [far]
 * rows away or the list changed size ([size]: a new category).
 */
@Composable
internal fun rememberTwinScroll(target: Float, far: Float, size: Int): Animatable<Float, AnimationVector1D> {
    val anim = remember { Animatable(target) }
    val seen = remember { intArrayOf(size) }
    LaunchedEffect(target, size) {
        val fresh = seen[0] != size
        seen[0] = size
        if (fresh || kotlin.math.abs(anim.value - target) > far) anim.snapTo(target)
        else anim.animateTo(target, tween(200, easing = FastOutSlowInEasing))
    }
    return anim
}
