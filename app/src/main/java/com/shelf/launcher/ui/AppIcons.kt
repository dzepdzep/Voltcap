package com.shelf.launcher.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An installed app's own launcher icon (and Android TV banner, where the app
 * has one) as art, for apps SteamGridDB knows nothing about. Loaded off the
 * main thread and kept in a small in-memory cache — the package manager is
 * the source of truth, so nothing is written to disk.
 */
internal object AppIconCache {
    private val icons = LruCache<String, ImageBitmap>(96)
    private val banners = LruCache<String, ImageBitmap>(32)
    private val noBanner = HashSet<String>()

    /** Bumped when a package changes, so icons already on screen reload. */
    var version by mutableIntStateOf(0)
        private set

    /** An app was updated or reinstalled: forget its icon and banner. */
    fun invalidate(pkg: String) {
        icons.remove(pkg)
        banners.remove(pkg)
        synchronized(noBanner) { noBanner -= pkg }
        version++
    }

    fun cachedIcon(pkg: String): ImageBitmap? = icons.get(pkg)
    fun cachedBanner(pkg: String): ImageBitmap? = banners.get(pkg)
    fun knownToHaveNoBanner(pkg: String): Boolean = synchronized(noBanner) { pkg in noBanner }

    fun loadIcon(context: Context, pkg: String): ImageBitmap? = runCatching {
        // 192px is sharp on a banner card without holding big bitmaps for
        // every app in the library.
        context.packageManager.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
    }.getOrNull()?.also { icons.put(pkg, it) }

    fun loadBanner(context: Context, pkg: String): ImageBitmap? {
        val drawable = runCatching { context.packageManager.getApplicationBanner(pkg) }.getOrNull()
        if (drawable == null) {
            synchronized(noBanner) { noBanner += pkg }
            return null
        }
        return runCatching { drawable.toBitmap(320, 180).asImageBitmap() }.getOrNull()
            ?.also { banners.put(pkg, it) }
    }
}

@Composable
internal fun rememberAppIcon(packageName: String?): ImageBitmap? {
    com.shelf.launcher.Breadcrumbs.mark("rememberAppIcon")
    val context = LocalContext.current
    // Update 151: the icon is kept with the package it belongs to. produceState keeps its value across key changes, so for
    // the first frame after the package changed (a slot moving on to another app) it still held the LAST app's icon —
    // a wrong icon flashing before the right one. Now that frame shows this app's cached icon, or nothing.
    val st = produceState<Pair<String?, ImageBitmap?>>(initialValue = packageName to packageName?.let { AppIconCache.cachedIcon(it) }, packageName, AppIconCache.version) {
        if (packageName == null) { value = null to null; return@produceState }
        AppIconCache.cachedIcon(packageName)?.let { value = packageName to it; return@produceState }
        value = packageName to null
        value = packageName to withContext(Dispatchers.IO) { AppIconCache.loadIcon(context, packageName) }
    }.value
    return if (st.first == packageName) st.second else packageName?.let { AppIconCache.cachedIcon(it) }
}

@Composable
internal fun rememberAppBanner(packageName: String?): ImageBitmap? {
    com.shelf.launcher.Breadcrumbs.mark("rememberAppBanner")
    val context = LocalContext.current
    // Update 151: kept with its package, like rememberAppIcon (never the last app's banner for a frame).
    val st = produceState<Pair<String?, ImageBitmap?>>(initialValue = packageName to packageName?.let { AppIconCache.cachedBanner(it) }, packageName, AppIconCache.version) {
        if (packageName == null || AppIconCache.knownToHaveNoBanner(packageName)) { value = packageName to null; return@produceState }
        AppIconCache.cachedBanner(packageName)?.let { value = packageName to it; return@produceState }
        value = packageName to null
        value = packageName to withContext(Dispatchers.IO) { AppIconCache.loadBanner(context, packageName) }
    }.value
    return if (st.first == packageName) st.second else packageName?.let { AppIconCache.cachedBanner(it) }
}

/**
 * Card art built from an app's icon: a blurred, enlarged copy of the icon as
 * the backdrop (so each app gets its own colours) with the crisp icon on top.
 * On the wide focused card, an Android TV banner is used instead if the app
 * ships one, and the icon sits to the right so it doesn't collide with the
 * title text on the left.
 */
@Composable
internal fun AppIconArt(
    packageName: String?,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    com.shelf.launcher.Breadcrumbs.mark("AppIconArt")
    val icon = rememberAppIcon(packageName)
    val banner = if (wide) rememberAppBanner(packageName) else null

    // Plain if / else on purpose: an early `return@Box` inside Box (an inline
    // composable) corrupts Compose's internal bookkeeping on this compiler
    // version and crashes the next redraw — which is exactly what happened.
    Box(modifier) {
        if (banner != null) {
            Image(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (icon != null) {
            // Update 130: the enlarged copy is blurred once, in the picture (BakedBlur): the live blur did nothing on
            // Android 11 and older (and was left out in Lite), so a small icon blown up to the card showed its pixels.
            val soft = rememberBakedBlur(androidx.compose.runtime.remember(icon) { icon.asAndroidBitmap() }, 24.dp, keepLast = false)
            if (soft != null) Image(
                soft, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.55f)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.45f))))
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val side = (if (wide) maxHeight * 0.55f else minOf(maxWidth, maxHeight) * 0.6f)
                Image(
                    icon, null,
                    modifier = Modifier
                        .align(if (wide) Alignment.CenterEnd else Alignment.Center)
                        .padding(end = if (wide) 20.dp else 0.dp)
                        .size(side)
                        .clip(RoundedCornerShape(side * 0.22f))
                )
            }
        }
    }
}

/**
 * App icons in the theme's colours (Settings › Round shortcuts › App icons in theme colours): each icon's
 * light and dark are kept, its own colours replaced by the accent — like Android's themed icons, for every
 * app (not only the ones that ship a monochrome icon). Null when the option is off.
 */
@Composable
internal fun themedIconFilter(): androidx.compose.ui.graphics.ColorFilter? {
    if (!com.shelf.launcher.data.Features.shortcutThemedIcons.state.value) return null
    val a = com.shelf.launcher.ui.theme.ShelfTheme.accent
    return androidx.compose.runtime.remember(a) {
        // Brightened a little so dark icons don't vanish on the dark tile.
        val r = 0.35f + 0.9f * a.red; val g = 0.35f + 0.9f * a.green; val b = 0.35f + 0.9f * a.blue
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
                    0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
                    0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}

/**
 * Update 173: [cover], or for an app without art of its own its icon (as the bitmap the drawn cartridges, discs and cases
 * print on their label / in their window) — they stayed blank in the grid, Hero carousel, tile home and case stacks.
 */
@Composable
internal fun coverOrAppIcon(g: com.shelf.launcher.data.Game, cover: Any?): Any? {
    val want = cover == null && g.sourceType == com.shelf.launcher.data.SourceType.APP
    val icon = if (want) rememberAppIcon(g.packageName) else null
    val bmp = icon?.let { androidx.compose.runtime.remember(it) { it.asAndroidBitmap() } }
    return cover ?: bmp
}
