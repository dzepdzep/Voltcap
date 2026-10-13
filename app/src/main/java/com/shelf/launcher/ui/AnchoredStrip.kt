package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor

/**
 * A row of game tiles where the game you're on grows and stays anchored in place (update 90). The style homes used a
 * LazyRow that scrolled to the new game while the tiles changed size underneath it, so the row slid too far left and
 * then settled back. Here one position glides from game to game, and every tile's size and place follow from it, so
 * growing, shrinking and moving are one smooth motion with no correction at the end.
 *
 * [anchorSlots]: how many small tiles stay visible before the game you're on (0 = it sits at the left edge).
 * [extraForFocus]: room kept beside the game you're on (its name, for example), opening as it grows.
 * [item] draws tile [i] at [w] × [h]; `grow` is 1 for the game you're on, 0 for the others, in between while moving;
 * `here` is true for the one tile that stands for the game you're on.
 * [loop] (update 125, Endless): the games repeat on both sides; [focus] is then the virtual place (rememberLoopFocus),
 * and [item] still gets the game's own index.
 */
@Composable
internal fun AnchoredStrip(
    count: Int,
    focus: Int,
    keyOf: (Int) -> Any,
    small: Dp,
    big: Dp,
    widthFor: (Dp) -> Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    startPad: Dp = 2.dp,
    anchorSlots: Int = 0,
    extraForFocus: Dp = 0.dp,
    alignTop: Boolean = true,
    loop: Boolean = false,
    item: @Composable (i: Int, grow: Float, w: Dp, h: Dp, here: Boolean) -> Unit
) {
    val looping = loop && count >= 2
    val pos = remember { Animatable((if (looping) focus else focus.coerceAtLeast(0)).toFloat()) }
    LaunchedEffect(focus, count, looping) {
        val target = (if (looping) focus else focus.coerceIn(0, (count - 1).coerceAtLeast(0))).toFloat()
        // A long jump (a letter, the other end of a looping list): start right next to it.
        if (abs(pos.value - target) > 3f) pos.snapTo(target + if (pos.value < target) -1f else 1f)
        pos.animateTo(target, tween(210, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
    }
    BoxWithConstraints(modifier) {
        if (count <= 0) return@BoxWithConstraints
        val p = if (looping) pos.value else pos.value.coerceIn(0f, (count - 1).toFloat())
        val smallW = widthFor(small)
        fun grow(i: Int) = (1f - abs(i - p)).coerceIn(0f, 1f)
        fun h(i: Int) = small + (big - small) * grow(i)
        fun w(i: Int) = widthFor(h(i)) + extraForFocus * grow(i)
        val fl = floor(p).toInt()
        val first = if (looping) fl - anchorSlots - 1 else (fl - anchorSlots - 1).coerceAtLeast(0)
        // Where tile [i] starts, counted from [first].
        var x = 0.dp
        val starts = HashMap<Int, Dp>()
        var i = first
        val limit = maxWidth + big * 2
        // The glide position measured the same way: the start of tile [fl] plus the part of the way to the next one.
        var flStart = 0.dp
        while (looping || i < count) {
            starts[i] = x
            if (i == fl) flStart = x
            x += w(i) + gap
            if (i > fl && x - flStart > limit) break
            i++
        }
        val frac = p - fl
        val anchor = startPad + (smallW + gap) * (if (looping) anchorSlots.toFloat() else minOf(p, anchorSlots.toFloat()))
        val shift = anchor - (flStart + (w(fl) + gap) * frac)
        starts.toSortedMap().forEach { (idx, sx) ->
            val left = sx + shift
            if (left + w(idx) < -big || left > maxWidth + big) return@forEach
            val hh = h(idx)
            // (Update 174: each game once in a short category.)
            if (looping && !drawnOnce(idx - kotlin.math.round(p).toInt(), count, anchorSlots)) return@forEach
            val real = if (looping) idx.mod(count) else idx
            key(if (looping) "${keyOf(real)}#${Math.floorDiv(idx, count)}" else keyOf(idx)) {
                Box(
                    Modifier
                        .align(if (alignTop) Alignment.TopStart else Alignment.CenterStart)
                        .offset { IntOffset(left.roundToPx(), 0) }
                        .size(w(idx), hh)
                ) { item(real, grow(idx), widthFor(hh), hh, idx == focus) }
            }
        }
    }
}
