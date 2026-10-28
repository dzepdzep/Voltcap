package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.shelf.launcher.data.Features
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Update 374: the place of a scrolling row / list in games, glided from game to game at the Moving between games speed
 * (the logic Case stacks' stage uses, shared by Coverflow details' shelf and Artwork library's list). A held D-pad carries
 * the glide's speed on instead of restarting; a new category or a re-sort jumps; a far jump glides only its last two steps.
 * [vFocus] is the virtual place ([rememberLoopFocus]), [keys] the games' keys ([gameKeys]).
 */
@Composable
internal fun rememberGlidePos(vFocus: Int, count: Int, keys: List<String>, loop: Boolean): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val pos = remember { Animatable(vFocus.toFloat()) }
    val animate = rememberAnimationsEnabled()
    val glide = Features.rowGlide.state.value
    val scope = rememberCoroutineScope()
    val firstKey = keys.firstOrNull()
    val shownList = remember { arrayOfNulls<Any>(2) }
    val lastTo = remember { intArrayOf(vFocus) }
    val lastGame = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(vFocus, count, firstKey) {
        val target = vFocus.toFloat()
        val newList = shownList[0] != firstKey || shownList[1] != count
        shownList[0] = firstKey
        shownList[1] = count
        val from = lastTo[0]
        lastTo[0] = vFocus
        val gameKey = if (count > 0) keys.getOrNull(if (loop) vFocus.mod(count) else vFocus) else null
        val same = gameKey != null && gameKey == lastGame[0]
        lastGame[0] = gameKey
        if (!animate || newList) pos.snapTo(target)
        else {
            // The same game at a new place (re-sorted, art came in): stay where it is.
            if (vFocus != from && same) pos.snapTo(pos.value + (vFocus - from))
            else if (abs(vFocus - from) > 12) pos.snapTo(target + if (vFocus > from) -2f else 2f)
            if (pos.value != target || pos.targetValue != target) scope.launch {
                pos.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
            }
        }
    }
    return pos
}
