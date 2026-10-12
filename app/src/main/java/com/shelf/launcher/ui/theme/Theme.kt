package com.shelf.launcher.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.shelf.launcher.data.AppearanceSettings
import android.graphics.drawable.BitmapDrawable

/**
 * App theme: the chosen preset, then the person's own colours on top, then —
 * if turned on — an accent taken from the focused game's cover art. Also
 * applies the size setting by scaling density, so every card, text and popup
 * grows or shrinks together.
 */
@Composable
fun ShelfLauncherTheme(
    appTheme: AppTheme = AppTheme.EMBER,
    appearance: AppearanceSettings = AppearanceSettings(),
    /** Cover of the focused game, for the accent-from-art option. */
    dynamicArtUrl: String? = null,
    content: @Composable () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ShelfLauncherTheme")
    // "Now playing": the cover of the playing song decides every colour, so the person's own
    // colours and the accent-from-game-art don't apply; changes fade over almost a second.
    val music = appTheme.motif.reactive
    // This theme's own look (Settings › Appearance › Theme look): where the colours come from. Themes
    // that can't be recoloured (music themes follow the album cover, showcases the game's art) keep
    // their original colours, and Settings says so.
    val look = com.shelf.launcher.data.Features.lookFor(appTheme.name)
    val source = if (appTheme.recolorable) look.colorSource else com.shelf.launcher.data.Features.ColorSource.THEME
    val fromGame = source == com.shelf.launcher.data.Features.ColorSource.GAME
    val custom = source == com.shelf.launcher.data.Features.ColorSource.CUSTOM
    // Which artwork colours win (Appearance › Colours › Colours from): Automatic = album on music themes,
    // the game elsewhere; Game art = always the game you're on (music themes too); Album cover = the song
    // playing whenever something plays (also on "Current game" themes), the game when nothing plays.
    val artSrc = com.shelf.launcher.data.Features.artColorSource.state.value
    val album = com.shelf.launcher.ui.MusicTheme.colors
    val useGame = when (artSrc) {
        com.shelf.launcher.data.Features.ArtColorSource.AUTO -> fromGame && !music
        com.shelf.launcher.data.Features.ArtColorSource.GAME -> fromGame || music
        com.shelf.launcher.data.Features.ArtColorSource.ALBUM -> fromGame && !music && album == null
    }
    val useAlbum = when (artSrc) {
        com.shelf.launcher.data.Features.ArtColorSource.AUTO -> music
        com.shelf.launcher.data.Features.ArtColorSource.GAME -> false
        com.shelf.launcher.data.Features.ArtColorSource.ALBUM -> music || (fromGame && album != null)
    }
    // Current game: the cover's palette replaces the theme's (background, panels, accent), gliding over.
    val gamePalette = com.shelf.launcher.ui.rememberArtPalette(dynamicArtUrl, useGame).takeIf { useGame }
    val base = when {
        useAlbum -> album?.copy(textPrimary = appTheme.colors.textPrimary, textMuted = appTheme.colors.textMuted)?.takeIf { !music } ?: album ?: appTheme.colors
        else -> gamePalette?.copy(textPrimary = appTheme.colors.textPrimary, textMuted = appTheme.colors.textMuted) ?: (if (music) album else null) ?: appTheme.colors
    }
    val customBackground = if (custom && !music) look.background else null
    val sat = if (custom) look.saturation / 100f else 1f
    val bri = if (custom) look.brightness / 100f else 1f
    val background = (customBackground?.let { Color(it) } ?: base.background).tuned(sat, bri)
    // Surfaces are derived from a custom background, so popups still stand out from it.
    val surface = (if (customBackground != null) lerp(background, Color.White, 0.07f) else base.surface.tuned(sat, bri))
    val surfaceRaised = (if (customBackground != null) lerp(background, Color.White, 0.12f) else base.surfaceRaised.tuned(sat, bri))
    val fixedAccent = ((if (custom && !music) look.accent else null)?.let { Color(it) } ?: base.accent).tuned(sat, bri)
    val secondaryTuned = base.secondary.tuned(sat, bri)

    val accent by animateColorAsState(fixedAccent, tween(if (music) 900 else 220), label = "accent")
    val fade: AnimationSpec<Color> = if (music || useAlbum) tween(900) else if (useGame) tween(600) else snap()
    val backgroundA by animateColorAsState(background, fade, label = "background")
    val surfaceA by animateColorAsState(surface, fade, label = "surface")
    val raisedA by animateColorAsState(surfaceRaised, fade, label = "raised")
    val secondaryA by animateColorAsState(secondaryTuned, fade, label = "secondary")
    val textA by animateColorAsState(base.textPrimary, fade, label = "text")
    val mutedA by animateColorAsState(base.textMuted, fade, label = "muted")

    val colors = base.copy(
        background = backgroundA,
        surface = surfaceA,
        surfaceRaised = raisedA,
        accent = accent,
        accentGlow = accent.copy(alpha = 0.45f),
        secondary = secondaryA,
        textPrimary = textA,
        textMuted = mutedA
    )

    val scheme = darkColorScheme(
        background = colors.background,
        surface = colors.surface,
        surfaceVariant = colors.surfaceRaised,
        primary = colors.accent,
        secondary = colors.secondary,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onPrimary = colors.background
    )

    val density = LocalDensity.current
    // "32-bit everywhere": the pixel font's glyphs are wide, so all text is a step smaller (names used to be cut
    // off or wrap onto two lines).
    val pixelAll = com.shelf.launcher.data.Features.uiFont.value == com.shelf.launcher.data.Features.UiFont.PIXEL
    val scaled = remember(density, appearance.density, appearance.textScale, pixelAll) {
        Density(density.density * appearance.density.scale, density.fontScale * appearance.textScale / 100f * (if (pixelAll) 0.84f else 1f))
    }

    CompositionLocalProvider(LocalShelfColors provides colors, LocalDensity provides scaled) {
        val normal = androidx.compose.material3.Typography()
        val mono = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.shelf.launcher.R.font.volt_pixel))
        // Headings only: the 32-bit console style with the normal font (the pixel type where it fits).
        val headings = !pixelAll && com.shelf.launcher.data.Features.uiStyle.value == com.shelf.launcher.data.Features.UiStyle.PIXEL
        fun androidx.compose.ui.text.TextStyle.px(k: Float = 1f) = copy(fontFamily = mono, fontSize = fontSize * k, lineHeight = lineHeight * k)
        val typography = if (pixelAll) normal.copy(
            displayLarge = normal.displayLarge.px(), displayMedium = normal.displayMedium.px(), displaySmall = normal.displaySmall.px(),
            headlineLarge = normal.headlineLarge.px(), headlineMedium = normal.headlineMedium.px(), headlineSmall = normal.headlineSmall.px(),
            titleLarge = normal.titleLarge.px(), titleMedium = normal.titleMedium.px(), titleSmall = normal.titleSmall.px(),
            bodyLarge = normal.bodyLarge.px(), bodyMedium = normal.bodyMedium.px(), bodySmall = normal.bodySmall.px(),
            labelLarge = normal.labelLarge.px(), labelMedium = normal.labelMedium.px(), labelSmall = normal.labelSmall.px()
        ) else if (headings) normal.copy(
            displayLarge = normal.displayLarge.px(0.86f), displayMedium = normal.displayMedium.px(0.86f), displaySmall = normal.displaySmall.px(0.86f),
            headlineLarge = normal.headlineLarge.px(0.86f), headlineMedium = normal.headlineMedium.px(0.86f), headlineSmall = normal.headlineSmall.px(0.86f),
            titleLarge = normal.titleLarge.px(0.86f), titleMedium = normal.titleMedium.px(0.86f), titleSmall = normal.titleSmall.px(0.86f)
        ) else normal
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * A lively accent from a cover image: its average colour, pushed to a bright,
 * saturated tone so it reads well as an accent on dark backgrounds. Null
 * until the image has loaded (the fixed accent shows meanwhile).
 */
@Composable
private fun rememberArtAccent(url: String?): Color? {
    com.shelf.launcher.Breadcrumbs.mark("rememberArtAccent")
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(48, 48)
            .allowHardware(false) // pixels must be readable
            // (Update 147: a still picture of an animated cover too — the game's colours never came for those.)
            .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
            .build()
        val bitmap = com.shelf.launcher.ui.drawableBitmap((context.imageLoader.execute(request) as? SuccessResult)?.drawable, 48)
            ?: return@LaunchedEffect
        color = accentFrom(bitmap)
    }
    return color
}

private fun accentFrom(bitmap: Bitmap): Color? = runCatching {
    val pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(pixel, hsv)
    // Greys have no hue worth using — keep the theme's accent for those.
    if (hsv[1] < 0.12f) return@runCatching null
    hsv[1] = hsv[1].coerceIn(0.55f, 0.85f)
    hsv[2] = hsv[2].coerceIn(0.8f, 0.95f)
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrNull()

/** For the colour pickers: a Color as a stored int. */
fun Color.toStoredColor(): Int = toArgb()

/** The colour with its saturation and brightness scaled (Theme look › Custom); unchanged at 1 / 1. */
internal fun Color.tuned(saturation: Float, brightness: Float): Color {
    if (saturation == 1f && brightness == 1f) return this
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * saturation).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * brightness).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
}
