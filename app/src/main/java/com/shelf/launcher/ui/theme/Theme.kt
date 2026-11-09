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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
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
    // (Update 231, #59: the cover only goes to the palette once you've stayed on a game ~220 ms, so holding ◀ / ▶
    // or stepping quickly no longer starts a colour glide for every game passed; the palette's own wait is shortened
    // by the same amount, so the colours still arrive at the same moment after you stop on an unseen game.)
    // (Update 240, #23: ~300 ms now — ART_SETTLE_MS; a game seen before reuses its cached palette, no decode.)
    var settledArtUrl by remember { mutableStateOf(dynamicArtUrl) }
    LaunchedEffect(dynamicArtUrl, useGame) {
        if (useGame && settledArtUrl != dynamicArtUrl) delay(ART_SETTLE_MS)
        settledArtUrl = dynamicArtUrl
    }
    val gamePalette = com.shelf.launcher.ui.rememberArtPalette(settledArtUrl, useGame, waitMs = 450L - ART_SETTLE_MS).takeIf { useGame }
    val base0 = when {
        useAlbum -> album?.copy(textPrimary = appTheme.colors.textPrimary, textMuted = appTheme.colors.textMuted)?.takeIf { !music } ?: album ?: appTheme.colors
        else -> gamePalette?.copy(textPrimary = appTheme.colors.textPrimary, textMuted = appTheme.colors.textMuted) ?: (if (music) album else null) ?: appTheme.colors
    }
    // (Update 240, #23: colours from art stay readable. The art palette's own dark background with the theme's writing:
    // a light theme's dark writing went dark-on-dark, so the palette's own light writing is used where the theme's doesn't
    // reach 4.5:1 (muted 3:1). A near-grey cover's accent (a grey or black-and-white cover) keeps the theme's accent
    // instead of a washed-out grey. Remembered per palette, not worked out on every frame of a colour glide.)
    val artDriven = base0 !== appTheme.colors
    val artSource = if (useAlbum || (music && gamePalette == null)) album else gamePalette
    val base = remember(base0, artDriven, artSource, appTheme) {
        if (!artDriven) base0 else {
            val text = if (contrastRatio(base0.textPrimary, base0.background) >= 4.5f) base0.textPrimary else artSource?.textPrimary ?: base0.textPrimary
            val muted = if (contrastRatio(base0.textMuted, base0.background) >= 3f) base0.textMuted else artSource?.textMuted ?: base0.textMuted
            val acc = if (saturationOf(base0.accent) < 0.15f) appTheme.colors.accent else base0.accent
            base0.copy(textPrimary = text, textMuted = muted, accent = acc, accentGlow = acc.copy(alpha = 0.45f))
        }
    }
    // (Update 240, #23: Theme › Colours follow › "Keep my accent": the background and panels follow the art, the accent
    // stays yours — your Custom accent when you picked one, else the theme's — and it is never recoloured by the art.)
    val keepAccent = artDriven && appTheme.recolorable && com.shelf.launcher.data.Features.artKeepAccent.state.value
    val manualAccent: Int? = if (keepAccent) look.accent else null
    // (Update 480, C2: the home layout's own colours — a palette or yours — over the theme's; a layout without its own
    // inherits the theme's look. Not for themes that can't be recoloured, nor while music colours the theme.)
    val layoutOwn = if (appTheme.recolorable && !music) com.shelf.launcher.data.Features.layoutColorsFor(appearance.homeLayout.name)
        else com.shelf.launcher.data.Features.LayoutColors()
    val customBackground = layoutOwn.bg ?: if (custom && !music) look.background else null
    val sat = if (custom) look.saturation / 100f else 1f
    val bri = if (custom) look.brightness / 100f else 1f
    val background = (customBackground?.let { Color(it) } ?: base.background).tuned(sat, bri)
    // Surfaces are derived from a custom background, so popups still stand out from it.
    // (Update 480, C2: on a light background the panels step darker, not lighter — white on white didn't stand out.)
    val lift = if (customBackground != null && background.luminance() > 0.5f) Color.Black else Color.White
    val surface = (if (customBackground != null) lerp(background, lift, 0.07f) else base.surface.tuned(sat, bri))
    val surfaceRaised = (if (customBackground != null) lerp(background, lift, 0.12f) else base.surfaceRaised.tuned(sat, bri))
    val chosenAccent = ((layoutOwn.acc ?: (if (custom && !music) look.accent else manualAccent))?.let { Color(it) }
        ?: (if (keepAccent) appTheme.colors.accent else base.accent)).tuned(sat, bri)
    // (Update 240, #23: an accent from the art reaches 3:1 on the background and the raised panels, and white or black
    // writing on it 4.5:1 — only its lightness moves, the hue stays the art's. Your own accent (Custom, or kept with
    // "Keep my accent") is used exactly as you picked it.)
    // (Update 480, C2 readability guard: a layout's own accent is kept readable too — 3:1 on the background, only its
    // lightness moves.)
    val adjustAccent = (artDriven && manualAccent == null && !(custom && !music) && layoutOwn.acc == null) || layoutOwn.acc != null
    // (Update 490, display only: where these colours come from, in the order worked out above — read by Settings, never
    // fed back into the colours.)
    val origin = describeColorOrigin(
        recolorable = appTheme.recolorable, musicTheme = music, useAlbum = useAlbum, albumNow = album != null,
        gamePaletteNow = gamePalette != null, layoutBg = layoutOwn.bg != null, layoutAcc = layoutOwn.acc != null,
        customBg = custom && !music && look.background != null, customAcc = custom && !music && look.accent != null,
        keepAccent = keepAccent, keptAccentPicked = look.accent != null,
        greyArtAccent = artDriven && saturationOf(base0.accent) < 0.15f,
        layoutAsks = com.shelf.launcher.data.Features.layoutColorsFor(appearance.homeLayout.name).let { it.bg != null || it.acc != null }
    )
    androidx.compose.runtime.SideEffect { if (ColorOrigin.now != origin) ColorOrigin.now = origin }
    val fixedAccent = remember(chosenAccent, background, surfaceRaised, adjustAccent) {
        if (adjustAccent) legibleArtAccent(chosenAccent, background, surfaceRaised) else chosenAccent
    }
    val secondaryTuned = base.secondary.tuned(sat, bri)

    // (Update 240, #23: the accent glides as long as the background does when it follows the art — it used to jump in
    // 220 ms while the background took 600 / 900, so the two visibly came apart on every game change.)
    // (QA: the "snap_colors" test fault turns the glide off, to measure what it costs — never on in normal use.)
    val qaSnap = com.shelf.launcher.ui.QaFault.version.intValue >= 0 && com.shelf.launcher.ui.QaFault.on("snap_colors")
    val accent by animateColorAsState(fixedAccent, if (qaSnap) snap() else tween(if (music || useAlbum) 900 else if (useGame) 600 else 220), label = "accent")
    val fade: AnimationSpec<Color> = if (qaSnap) snap() else if (music || useAlbum) tween(900) else if (useGame) tween(600) else snap()
    val backgroundA by animateColorAsState(background, fade, label = "background")
    val surfaceA by animateColorAsState(surface, fade, label = "surface")
    val raisedA by animateColorAsState(surfaceRaised, fade, label = "raised")
    val secondaryA by animateColorAsState(secondaryTuned, fade, label = "secondary")
    // (Update 480, C2 readability guard: on a background of your own — a light palette, a pale custom colour — the
    // theme's writing moves to dark or light when it wouldn't reach 4.5:1 (muted 3:1). It used to stay the theme's light
    // text on a light background.)
    val guardedText = remember(base.textPrimary, background, customBackground) {
        if (customBackground == null || contrastRatio(base.textPrimary, background) >= 4.5f) base.textPrimary
        else if (background.luminance() > 0.4f) Color(0xFF15171B) else Color.White
    }
    val guardedMuted = remember(base.textMuted, background, customBackground) {
        if (customBackground == null || contrastRatio(base.textMuted, background) >= 3f) base.textMuted
        else if (background.luminance() > 0.4f) Color(0xFF4A505A) else Color(0xFFB8BEC8)
    }
    val textA by animateColorAsState(guardedText, fade, label = "text")
    val mutedA by animateColorAsState(guardedMuted, fade, label = "muted")

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

    // Update 158: a light theme (Lobby) gets Material's light scheme, so its own controls (switches, sliders, menus)
    // draw light with dark writing instead of the dark defaults. (A light custom background keeps the dark scheme:
    // its writing stays the theme's light text.)
    // (The theme's own background decides, so a light game cover or album palette on a dark theme can't flip it.)
    val lightTheme = (appTheme.colors.background.luminance() > 0.5f && !useGame && !useAlbum && customBackground == null) ||
        (customBackground != null && background.luminance() > 0.5f) // (Update 480, C2: a light background of your own is a light scheme)
    // (Update 231, #59: Material's scheme is built from the colours being glided TO, once per change, instead of a new
    // scheme every frame of the glide — that re-composed the whole home screen ~70 times per D-pad step while the
    // colours followed the game. Voltcap's own colours (LocalShelfColors) still glide; Material's controls switch.)
    val targetText = guardedText
    val targetMuted = guardedMuted
    val schemeNow = remember(lightTheme, background, surface, surfaceRaised, fixedAccent, secondaryTuned, targetText, targetMuted) {
        if (lightTheme) androidx.compose.material3.lightColorScheme(
            background = background,
            surface = surface,
            surfaceVariant = surfaceRaised,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceRaised,
            surfaceContainerHighest = surfaceRaised,
            primary = fixedAccent,
            secondary = secondaryTuned,
            onBackground = targetText,
            onSurface = targetText,
            onSurfaceVariant = targetMuted,
            // (Update 240, #23: white unless that doesn't reach 4.5:1 on the accent — then black.)
            onPrimary = textOn(fixedAccent, Color.White)
        ) else darkColorScheme(
            background = background,
            surface = surface,
            surfaceVariant = surfaceRaised,
            primary = fixedAccent,
            secondary = secondaryTuned,
            onBackground = targetText,
            onSurface = targetText,
            // (Update 240, #23: the background colour on the accent unless that doesn't reach 4.5:1 — then white / black.)
            onPrimary = textOn(fixedAccent, background)
        )
    }

    // Update 493 (D137, measured on the KONKR): Material's colour scheme is a *static* CompositionLocal, and a ColorScheme
    // is a new object whenever a colour in it changes — so with the colours following the game's art, every game moved to
    // made everything under the theme re-run without skipping: each 3D case in Case stacks ~once per move (33 a move, the
    // carousel's ~31 visible games). Voltcap's own colours (ShelfTheme, gliding as before) carry the art's accent; Material's
    // scheme only gives defaults to its own controls (switches, sliders, fields — given ShelfTheme colours where they show
    // the accent), none of them on the home screen. So while the colours follow the art, the scheme is handed on once they
    // have been still SCHEME_SETTLE_MS; any other change (theme, music, custom colours) is handed on at once, as before.
    val followArt = useGame && artDriven
    var heldScheme by remember { mutableStateOf(schemeNow) }
    LaunchedEffect(schemeNow, followArt) {
        if (followArt) delay(SCHEME_SETTLE_MS)
        heldScheme = schemeNow
    }
    val scheme = if (followArt) heldScheme else schemeNow
    val density = LocalDensity.current
    // "32-bit everywhere": the pixel font's glyphs are wide, so all text is a step smaller (names used to be cut
    // off or wrap onto two lines).
    val pixelAll = com.shelf.launcher.data.Features.uiFont.value == com.shelf.launcher.data.Features.UiFont.PIXEL
    val scaled = remember(density, appearance.density, appearance.textScale, pixelAll) {
        Density(density.density * appearance.density.scale, density.fontScale * appearance.textScale / 100f * (if (pixelAll) 0.84f else 1f))
    }

    // Headings only: the 32-bit console style with the normal font (the pixel type where it fits).
    val headings = !pixelAll && com.shelf.launcher.data.Features.uiStyle.value == com.shelf.launcher.data.Features.UiStyle.PIXEL
    // (Update 231, #59: the type styles are made once per font setting, not again on every recomposition of the theme.)
    val typography = remember(pixelAll, headings) {
        val normal = androidx.compose.material3.Typography()
        val mono = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.shelf.launcher.R.font.volt_pixel))
        fun androidx.compose.ui.text.TextStyle.px(k: Float = 1f) = copy(fontFamily = mono, fontSize = fontSize * k, lineHeight = lineHeight * k)
        if (pixelAll) normal.copy(
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
    }

    // (QA pass 2, D22: the same colours as one stable State, for draw-time readers — see LocalShelfColorsLive.)
    val live = androidx.compose.runtime.rememberUpdatedState(colors)
    CompositionLocalProvider(LocalShelfColors provides colors, LocalShelfColorsLive provides live, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/**
 * (Update 231, #59) How long the focus must rest on a game before its cover colours start to load.
 * (Update 240, #23: 300 ms, was 220 — a quick tap-tap-tap through games no longer starts a glide on each one it rests on
 * for a moment; the palette's own wait is shortened by the same amount, so the colours still arrive ~450 ms after you stop.)
 */
private const val ART_SETTLE_MS = 300L

/** Update 493 (D137): how long the art's colours stay still before Material's colour scheme takes them (see ShelfLauncherTheme). */
internal const val SCHEME_SETTLE_MS = 900L

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


/**
 * Update 490 (owner request, display only): where the colours on screen come from — Theme, Custom, Game, Music or Layout
 * override — for the background and the accent separately, plus why a theme or layout can't be recoloured. Worked out by
 * [describeColorOrigin] in [ShelfLauncherTheme]'s own order; it only describes, nothing reads it to pick a colour.
 */
data class ColorOrigin(val background: String, val accent: String, val note: String? = null) {
    /** One short line: "Background: Game · Accent: Layout override". */
    val line: String get() = if (background == accent) "Colours from: $background" else "Background: $background · Accent: $accent"

    companion object {
        const val THEME = "Theme"
        const val CUSTOM = "Custom"
        const val GAME = "Game"
        const val MUSIC = "Music"
        const val LAYOUT = "Layout override"
        /** The last colours drawn (set by ShelfLauncherTheme). */
        var now by mutableStateOf<ColorOrigin?>(null)
    }
}

/** Update 490: [ShelfLauncherTheme]'s precedence as words — keep in step with it (Batch490Test checks the cases). */
internal fun describeColorOrigin(
    recolorable: Boolean, musicTheme: Boolean, useAlbum: Boolean, albumNow: Boolean, gamePaletteNow: Boolean,
    layoutBg: Boolean, layoutAcc: Boolean, customBg: Boolean, customAcc: Boolean,
    keepAccent: Boolean, keptAccentPicked: Boolean, greyArtAccent: Boolean, layoutAsks: Boolean
): ColorOrigin {
    // base0: the album when it wins and something plays, else the game's palette, else (music themes) the album, else the theme.
    val base = when {
        useAlbum -> if (albumNow) ColorOrigin.MUSIC else ColorOrigin.THEME
        gamePaletteNow -> ColorOrigin.GAME
        musicTheme && albumNow -> ColorOrigin.MUSIC
        else -> ColorOrigin.THEME
    }
    val artDriven = base != ColorOrigin.THEME
    val bg = when {
        layoutBg -> ColorOrigin.LAYOUT
        customBg -> ColorOrigin.CUSTOM
        else -> base
    }
    val acc = when {
        layoutAcc -> ColorOrigin.LAYOUT
        customAcc -> ColorOrigin.CUSTOM
        keepAccent && keptAccentPicked -> ColorOrigin.CUSTOM
        keepAccent -> ColorOrigin.THEME
        artDriven && greyArtAccent -> ColorOrigin.THEME
        else -> base
    }
    val note = when {
        !recolorable && layoutAsks -> "This theme can't be recoloured: it keeps its own colours, so this layout's colours wait until another theme."
        !recolorable -> "This theme can't be recoloured: it keeps its own colours."
        musicTheme && layoutAsks -> "A music theme: the album's colours win over this layout's own colours."
        else -> null
    }
    return ColorOrigin(bg, acc, note)
}
