package com.shelf.launcher.ui

import com.shelf.launcher.data.BackgroundStyle

/**
 * Update 313: a gameplay clip may replace only the selected game-art background.
 * User wallpapers and animated themes are actual background choices, not a
 * temporary placeholder for an unrelated gameplay video.
 *
 * Embedded videos (e.g. Gaming room monitor and preview/details panels) use
 * their own placement and must not be changed by this fullscreen policy.
 */
internal fun allowsFullscreenGameplayVideo(style: BackgroundStyle): Boolean = when (style) {
    BackgroundStyle.ART, BackgroundStyle.BLURRED -> true
    BackgroundStyle.BLACK, BackgroundStyle.WALLPAPER, BackgroundStyle.ANIMATED -> false
}
