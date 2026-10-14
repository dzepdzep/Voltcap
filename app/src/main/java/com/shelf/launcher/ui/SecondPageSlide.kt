package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shelf.launcher.data.SecondPage

/** Update 200: how long a page takes to slide across on the second screen. */
private const val PAGE_SLIDE_MS = 300

/**
 * Update 200: the second screen's pages slide instead of just appearing ("going through stuff on the second screen just
 * spawns the next column — I want them to glide like the rest of the animations"). The next page comes in from the right
 * and the previous one from the left (the direction of travel), a third of the way with a fade, ~300 ms; the game window
 * cross-fades in and out over the page. [travel] is the last step's direction ([0]: -1 / 1, [1]: the page's ordinal) so a
 * step that wraps around (last → first) still slides the way it went; anything else (tabs, the dock) goes by [order].
 * Both pages are drawn only while the slide runs; the motion is translation and alpha on a layer (cheap).
 * [content] draws a page: (page, it's the game window).
 */
@Composable
internal fun SecondPageSlide(
    page: SecondPage,
    gameOpen: Boolean,
    order: List<SecondPage>,
    travel: IntArray,
    modifier: Modifier = Modifier,
    content: @Composable (SecondPage, Boolean) -> Unit
) {
    AnimatedContent(
        targetState = page to gameOpen,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val from = initialState
            val to = targetState
            if (from.second || to.second) {
                // The game window opening or closing: a cross-fade.
                (fadeIn(tween(240, delayMillis = 40)) togetherWith fadeOut(tween(200)))
                    .using(SizeTransform(clip = false))
            } else {
                val byOrder = order.indexOf(to.first) - order.indexOf(from.first)
                val dir = if (travel[0] != 0 && travel[1] == to.first.ordinal) travel[0] else if (byOrder < 0) -1 else 1
                travel[0] = 0
                val enter = slideInHorizontally(tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing)) { w -> dir * w / 3 } +
                    fadeIn(tween(PAGE_SLIDE_MS, easing = LinearOutSlowInEasing))
                val exit = slideOutHorizontally(tween(PAGE_SLIDE_MS, easing = FastOutSlowInEasing)) { w -> -dir * w / 3 } +
                    fadeOut(tween(PAGE_SLIDE_MS * 2 / 3))
                // (Not clipped to the page area: the pages glide in from the screen's edge.)
                (enter togetherWith exit).using(SizeTransform(clip = false))
            }
        },
        label = "secondPage"
    ) { key ->
        // A Box like the one the page used to sit in directly, so a page that doesn't fill its space keeps its size.
        Box(Modifier.fillMaxSize()) { content(key.first, key.second) }
    }
}
