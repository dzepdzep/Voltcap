package com.shelf.launcher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.shelf.launcher.data.HeroMotion

/**
 * The background-art transitions (Appearance › Background art motion, or a game's own in Game settings)
 * as an AnimatedContent transform, for pictures that swap whole (the second screen). The home background
 * draws the same styles itself (HomeChrome.HeroLayers). Decorative only: focus is untouched; Lite effects
 * get a short fade.
 */
internal fun gameTransition(style: HeroMotion, ms: Int, lite: Boolean = false): ContentTransform {
    val duration = if (lite) 100 else ms
    val fade = fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
    if (lite) return fade
    return when (style) {
        HeroMotion.CROSSFADE, HeroMotion.QUICK, HeroMotion.QUICK_ZOOM, HeroMotion.KEN_BURNS -> fade
        HeroMotion.SLIDE, HeroMotion.PARALLAX -> (slideInHorizontally(tween(duration)) { it / 5 } + fadeIn(tween(duration))) togetherWith
            (slideOutHorizontally(tween(duration)) { -it / 5 } + fadeOut(tween(duration)))
        HeroMotion.ZOOM_THROUGH -> (scaleIn(tween(duration), initialScale = 1.12f) + fadeIn(tween(duration))) togetherWith
            (scaleOut(tween(duration), targetScale = 0.92f) + fadeOut(tween(duration)))
        HeroMotion.COVER_FLOW -> (slideInHorizontally(tween(duration)) { it / 3 } + scaleIn(tween(duration), initialScale = 0.88f) + fadeIn(tween(duration))) togetherWith
            (slideOutHorizontally(tween(duration)) { -it / 3 } + scaleOut(tween(duration), targetScale = 0.88f) + fadeOut(tween(duration)))
        HeroMotion.PIXEL -> (expandIn(tween(duration), expandFrom = androidx.compose.ui.Alignment.Center) + fadeIn(tween(duration))) togetherWith
            fadeOut(tween(duration / 2))
        HeroMotion.CRT -> (expandVertically(tween(duration), expandFrom = androidx.compose.ui.Alignment.CenterVertically) + fadeIn(tween(duration))) togetherWith
            (shrinkVertically(tween(duration), shrinkTowards = androidx.compose.ui.Alignment.CenterVertically) + fadeOut(tween(duration)))
        HeroMotion.IRIS -> (scaleIn(tween(duration), initialScale = 0.25f) + fadeIn(tween(duration))) togetherWith
            (scaleOut(tween(duration), targetScale = 1.2f) + fadeOut(tween(duration)))
        HeroMotion.MEMORY_CARD -> (slideInVertically(tween(duration)) { it / 3 } + scaleIn(tween(duration), initialScale = 0.9f) + fadeIn(tween(duration))) togetherWith
            (slideOutVertically(tween(duration)) { -it / 3 } + fadeOut(tween(duration)))
    }
}
