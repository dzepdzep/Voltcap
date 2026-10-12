package com.shelf.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features

/**
 * The detail pane's layers (Settings › Appearance › Category screen › Game details per view): the game's
 * cover art, its screenshots taking turns, its preview clip and its logo, stacked behind [content]. Each
 * layer has its own switch (Features.detailLayer*); a layer without a picture is simply skipped. With every
 * layer off, or nothing to show, [content] is drawn alone, exactly as before.
 *
 * [video] is off for panes that already play the clip somewhere else on the same screen.
 */
@Composable
internal fun DetailLayers(
    gameKey: String,
    cover: String?,
    screenshots: List<String>,
    videoUrl: String?,
    logo: String?,
    modifier: Modifier = Modifier,
    video: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("DetailLayers")
    val showCover = Features.detailLayerCover.state.value && cover != null
    val showShots = Features.detailLayerScreenshots.state.value && screenshots.isNotEmpty()
    val showVideo = video && Features.detailLayerVideo.state.value && videoUrl != null
    val showLogo = Features.detailLayerLogo.state.value && logo != null
    val anyBackdrop = showCover || showShots || showVideo
    // Update 90: the pane used to build up in steps (blurred cover, then the dark box, then the screenshot). Now one
    // picture at a time: the screenshot when there is one (else the blurred cover), cross-fading when it changes,
    // and the whole backdrop (box and shade included) fades in only once that picture has loaded. While this game's
    // screenshots may still be on the way, the cover waits a moment so it doesn't flash up first.
    var coverAllowed by remember(gameKey) { mutableStateOf(!Features.detailLayerScreenshots.state.value || screenshots.isNotEmpty()) }
    androidx.compose.runtime.LaunchedEffect(gameKey) {
        if (!coverAllowed) { kotlinx.coroutines.delay(800); coverAllowed = true }
    }
    var index by remember(gameKey, screenshots) { mutableStateOf(0) }
    if (showShots) LaunchedWhileActive(screenshots) {
        while (screenshots.size > 1) {
            kotlinx.coroutines.delay(6_000)
            index = (index + 1) % screenshots.size
        }
    }
    // (url, blurred) of the one picture behind the text.
    // The cover is no longer shown blurred (that read as a smeared picture): its colours are draped over the pane instead.
    // A screenshot that doesn't load is dropped (the cover's colours take over) instead of leaving the backdrop invisible.
    var failedShot by remember(gameKey) { mutableStateOf<String?>(null) }
    val picture: Pair<String, Boolean>? = when {
        showShots -> (screenshots[index % screenshots.size] to false).takeIf { it.first != failedShot }
        else -> null
    }
    val drape = showCover && coverAllowed && picture == null
    val palette = rememberArtPalette(cover, enabled = drape && !Features.themeColorsOnly.state.value)
    var loaded by remember(gameKey) { mutableStateOf(false) }
    val backdropAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (loaded || ((showVideo || drape) && picture == null)) 1f else 0f, tween(320), label = "detailLayerIn"
    )
    Box(modifier) {
        if (anyBackdrop) {
            Box(Modifier.matchParentSize().androidx_alpha(backdropAlpha).clip(uiRounded(14.dp))) {
                if (picture != null) {
                    Crossfade(
                        targetState = picture, animationSpec = tween(if (loaded) 900 else 0), label = "detailLayerShots", modifier = Modifier.fillMaxSize()
                    ) { (url, blurred) ->
                        AsyncImage(
                            model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            onSuccess = { loaded = true },
                            onError = { failedShot = url },
                            modifier = Modifier.fillMaxSize().then(if (blurred) Modifier.blur(18.dp) else Modifier)
                        )
                    }
                }
                if (drape && !showVideo) {
                    ColorDrape(
                        palette?.accent ?: com.shelf.launcher.ui.theme.ShelfTheme.accent,
                        palette?.secondary ?: com.shelf.launcher.ui.theme.ShelfTheme.colors.secondary,
                        Modifier.fillMaxSize()
                    )
                }
                if (showVideo) {
                    androidx.compose.runtime.key(videoUrl) {
                        VideoBackdrop(videoUrl!!, withSound = false, modifier = Modifier.fillMaxSize())
                    }
                }
                // Keeps the text readable over whatever picture is behind it.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.78f)))
                    )
                )
            }
        }
        if (showLogo) {
            AsyncImage(
                model = logo, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.TopStart,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).widthIn(max = 180.dp).heightIn(max = 54.dp)
            )
        }
        content()
    }
}

private fun Modifier.androidx_alpha(a: Float): Modifier = this.then(Modifier.graphicsLayer { alpha = a })
