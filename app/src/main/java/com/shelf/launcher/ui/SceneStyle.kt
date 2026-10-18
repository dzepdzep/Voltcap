package com.shelf.launcher.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.ui.theme.ShelfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The scene editor's knobs (Appearance › Behind the dock › Scene style), applied by ThemeScene:
 * [speed] scales the scene's clock, [dim] darkens it, [particles] scales the stars, motes and sparks,
 * [shift] moves a scene's subject down (share of the screen height; Theme look › Scene position).
 */
data class SceneTuning(val speed: Float = 1f, val dim: Float = 0f, val particles: Float = 1f, val shift: Float = 0f)

val LocalSceneTuning = compositionLocalOf { SceneTuning() }

/** Where the home screen is right now, for settings that apply per category. */
object HomeContext {
    var categoryKey by mutableStateOf("all")
    var categoryLabel by mutableStateOf("All games")
}

/** A readable name for a category key (see LauncherUiState.sortCategoryKey). */
internal fun categoryKeyLabel(key: String): String = when {
    key == "all" -> "All games"
    key.startsWith("special:") -> runCatching {
        com.shelf.launcher.SpecialFilter.valueOf(key.removePrefix("special:")).label
    }.getOrDefault(key.removePrefix("special:"))
    key.startsWith("cat:") -> key.removePrefix("cat:").removePrefix("collection:").replace('_', ' ')
    else -> key
}

/** Every colour's hue turned by [deg] degrees (saturation and brightness kept). */
internal fun ShelfColors.hueShifted(deg: Float): ShelfColors {
    if (deg == 0f) return this
    fun Color.turn(): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(toArgb(), hsv)
        hsv[0] = ((hsv[0] + deg) % 360f + 360f) % 360f
        return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
    }
    return copy(background = background.turn(), surface = surface.turn(), surfaceRaised = surfaceRaised.turn(),
        accent = accent.turn(), accentGlow = accentGlow.turn(), secondary = secondary.turn())
}

/**
 * The scene's colours at [hour] (0-24, fractional): cool and deep at night, pink-gold at dawn,
 * a little lighter by day, orange-violet at dusk. Blended smoothly between the four.
 */
internal fun ShelfColors.atTimeOfDay(hour: Float): ShelfColors {
    fun bump(h: Float, centre: Float, width: Float): Float {
        var d = kotlin.math.abs(h - centre)
        if (d > 12f) d = 24f - d
        return (1f - d / width).coerceIn(0f, 1f)
    }
    val dawn = bump(hour, 6.5f, 2.5f)
    val dusk = bump(hour, 19f, 2.5f)
    val day = (bump(hour, 13f, 6f) * 1.4f).coerceIn(0f, 1f) * (1f - dawn) * (1f - dusk)
    val night = (1f - dawn - dusk - day).coerceIn(0f, 1f)
    val dawnTint = Color(0xFFFFA48A)
    val duskTint = Color(0xFFB45CFF)
    val nightTint = Color(0xFF3A5BFF)
    fun Color.shade(k: Float) = Color(red * k, green * k, blue * k, alpha)
    fun tone(c: Color, strength: Float): Color {
        var out = c
        out = lerp(out, dawnTint, dawn * 0.22f * strength)
        out = lerp(out, duskTint, dusk * 0.22f * strength)
        out = lerp(out, nightTint, night * 0.12f * strength)
        return out
    }
    val bgK = 1f + 0.18f * day - 0.22f * night
    return copy(
        background = tone(background, 0.6f).shade(bgK),
        surface = tone(surface, 0.6f).shade(bgK),
        accent = tone(accent, 1f),
        secondary = tone(secondary, 1f).let { lerp(it, Color.White, 0.08f * day) }
    )
}

/** The local hour (0-24, fractional), refreshed every few minutes while [enabled]. */
@Composable
internal fun rememberHourOfDay(enabled: Boolean): Float {
    var hour by remember { mutableFloatStateOf(currentHour()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            hour = currentHour()
            delay(5 * 60_000L)
        }
    }
    return hour
}

private fun currentHour(): Float {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) + c.get(java.util.Calendar.MINUTE) / 60f
}

/**
 * Colours taken from a picture of the game you're on (cover or background art), for the animated
 * background. Loaded small through the app's image cache, a moment after you stop on a game (not
 * for every game you scroll past). Null until one is ready.
 */
@Composable
internal fun rememberArtPalette(
    model: Any?,
    enabled: Boolean,
    /** (Update 231, #59) The wait before an unseen picture is loaded; the app theme passes less, as it already waited for the focus to settle. */
    waitMs: Long = 450L
): ShelfColors? {
    val context = LocalContext.current
    var palette by remember { mutableStateOf<ShelfColors?>(model?.let { ArtPaletteCache[it] }) }
    LaunchedEffect(model, enabled) {
        if (!enabled || model == null) return@LaunchedEffect
        // Seen before this session: no decode, no wait.
        ArtPaletteCache[model]?.let { palette = it; return@LaunchedEffect }
        if (waitMs > 0) delay(waitMs)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(model)
                    .size(96)
                    .allowHardware(false)
                    .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
                    .build()
                val drawable = (coil.Coil.imageLoader(context).execute(request) as? coil.request.SuccessResult)?.drawable
                val bmp = drawableBitmap(drawable, 96) ?: return@runCatching null
                MusicTheme.paletteOf(bmp)
            }.getOrNull()
        }
        if (result != null) { ArtPaletteCache[model] = result; palette = result }
    }
    return palette
}

/** Colours picked from artwork this session (game popup, scenes): going back to a game reuses them. */
private object ArtPaletteCache {
    private val cache = android.util.LruCache<Any, ShelfColors>(96)
    operator fun get(key: Any): ShelfColors? = cache.get(key)
    operator fun set(key: Any, value: ShelfColors) { cache.put(key, value) }
}

/** [c] with its scene colours gliding to new values instead of jumping (cover / time-of-day changes). */
@Composable
internal fun animatedSceneColors(c: ShelfColors): ShelfColors {
    val spec = tween<Color>(900)
    val bg by animateColorAsState(c.background, spec, label = "sceneBg")
    val surf by animateColorAsState(c.surface, spec, label = "sceneSurf")
    val acc by animateColorAsState(c.accent, spec, label = "sceneAcc")
    val sec by animateColorAsState(c.secondary, spec, label = "sceneSec")
    return c.copy(background = bg, surface = surf, accent = acc, secondary = sec)
}
