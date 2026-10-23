package com.shelf.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.CategoryBars

/*
 * Update 212: the category's title on every layout, one way.
 *
 * - The title is the category or filter you're in (All games, Favorites, Recently played, a console, a collection,
 *   Android…; with a search, the search in it), whatever game you're on — an empty category included.
 * - A layout that shows the category in its own design (its tabs, header, caption…) is left as it is: that label calls
 *   [CategoryTitle.Native] while it's on the screen. A layout showing none right now (Spotlight with its category tabs
 *   off or hidden, the Voltcap row with Categories off or nothing in it, a custom layout without a category text…)
 *   gets the one fallback title: the Category name part (HomeParts.kt › CategoryNamePart) at the layout's free spot
 *   (categoryPartSpot), moved, sized and styled in Edit Home like any part. Never both, never two.
 * - Hiding a layout's category tabs (navigation) doesn't hide the title: the fallback takes its place.
 * - Settings › Home › Category title: Show, then dim (default — clear when you switch category, then dimmed to a
 *   readable 60 %, never gone), Always clear, or Off (no fallback title; the layouts' own labels stay).
 * - Taken off in Edit Home (its ✕ / Hide) it stays off on that layout ([HomeWidgets.remove] remembers it).
 */
internal object CategoryTitle {
    /** How many of the layouts' own category labels are on the screen now. */
    private val native = mutableIntStateOf(0)

    /**
     * Called by a layout's own category label while it's shown. [partId]: the Edit Home part it's drawn in — hidden
     * there, it doesn't count (the fallback shows instead).
     */
    @Composable
    fun Native(partId: String? = null) {
        val on = partId == null || !HomeWidgets.hidden(partId)
        DisposableEffect(on) {
            if (on) native.intValue += 1
            onDispose { if (on) native.intValue = (native.intValue - 1).coerceAtLeast(0) }
        }
    }

    /**
     * Update 315: the layout in use shows its own category caption (Settings › Categories › Category name / the R1
     * Categories tile, per layout — CategoryBars.OWN): off, the caption isn't drawn and nothing takes its place.
     */
    fun captionOn(a: com.shelf.launcher.data.AppearanceSettings): Boolean =
        CategoryBars.of(a.homeLayout, a.categoryBarByLayout, a.categoryBar) != com.shelf.launcher.data.CategoryBar.OFF

    /** A layout's own label is showing the category now (reads state). */
    fun nativeShown(): Boolean = native.intValue > 0

    /** [layout] gets the fallback title now: the setting isn't Off, no own label shows, and it wasn't taken off there. */
    fun fallbackOn(layout: HomeLayout): Boolean =
        Features.categoryTitle.state.value != Features.CategoryTitleMode.OFF && !nativeShown() &&
            HomeWidgets.overlayId(layout, HomeWidgets.CATEGORY) !in Features.hiddenParts.state.value

    /** The title's text: the category, with the search in it when there's one. */
    fun text(categoryKey: String, search: String): String {
        val name = categoryKeyLabel(categoryKey)
        return if (search.isBlank()) name else "“${search.trim()}” in $name"
    }
}

/**
 * Update 212: the fallback title's fade — clear when the category changes ([key]), then (Show, then dim) eased to 60 % after
 * a few seconds; Always clear stays at 100 %. Never hidden. Still while Edit Home is open (you see it as it is).
 */
@Composable
internal fun Modifier.categoryTitleFade(key: String): Modifier {
    val mode = Features.categoryTitle.state.value
    var bright by remember { mutableStateOf(true) }
    LaunchedEffect(key, mode) {
        bright = true
        if (mode == Features.CategoryTitleMode.SUBTLE) { kotlinx.coroutines.delay(3200); bright = false }
    }
    val a by animateFloatAsState(if (bright || HomeEditState.active) 1f else 0.6f, tween(if (bright) 180 else 900), label = "catTitle")
    return this.graphicsLayer { alpha = a }
}
