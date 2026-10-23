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
    /** Update 319: one-colour icons (picture, and whether it's the app's own monochrome icon) — [loadMono]. */
    private val monos = LruCache<String, Pair<ImageBitmap, Boolean>>(64)

    /** Bumped when a package changes, so icons already on screen reload. */
    var version by mutableIntStateOf(0)
        private set

    /** An app was updated or reinstalled: forget its icon and banner. */
    fun invalidate(pkg: String) {
        icons.remove(pkg)
        banners.remove(pkg)
        monos.remove(pkg)
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

    fun cachedMono(pkg: String): Pair<ImageBitmap, Boolean>? = monos.get(pkg)

    /**
     * Update 319: [pkg]'s icon as a symbol for one colour: the app's own monochrome icon (Android 13+, true) where it ships
     * one, else its adaptive icon's foreground alone (no background plate; false), else its whole icon (false) — the
     * visible middle of the layer, at 192 px.
     */
    fun loadMono(context: Context, pkg: String): Pair<ImageBitmap, Boolean>? = runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        val size = 192
        if (android.os.Build.VERSION.SDK_INT >= 26 && d is android.graphics.drawable.AdaptiveIconDrawable) {
            val mono = if (android.os.Build.VERSION.SDK_INT >= 33) d.monochrome else null
            val layer = mono ?: d.foreground
            if (layer != null) {
                // An adaptive layer is 108 units across, of which the middle 72 show.
                val full = size * 3 / 2
                val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(out)
                c.translate(-(full - size) / 2f, -(full - size) / 2f)
                layer.setBounds(0, 0, full, full)
                layer.draw(c)
                return@runCatching out.asImageBitmap() to (mono != null)
            }
        }
        d.toBitmap(size, size).asImageBitmap() to false
    }.getOrNull()?.also { monos.put(pkg, it) }

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
    // (Update 319, Toni: "the icons always used for everything": with Icons in the theme's colour on, every app icon in
    // Voltcap is its one-colour symbol in the theme's accent.)
    val style = com.shelf.launcher.data.IconStyles.of(packageName)
    if (com.shelf.launcher.data.Features.secondDockMono.state.value && style != com.shelf.launcher.data.Features.MonoIconStyle.ORIGINAL)
        return rememberMonoAppIcon(packageName, com.shelf.launcher.ui.theme.ShelfTheme.accent, solid = style == com.shelf.launcher.data.Features.MonoIconStyle.SOLID)
    return rememberOwnAppIcon(packageName)
}

/**
 * Update 325: [packageName]'s own icon in its own colours, whatever the icon style — for an app's art printed on a
 * cartridge, disc or case (coverOrAppIcon). (Toni: "some apps keep glitching with their picture on the cartridge — the
 * normal colour, then another colour, then only text": the label wore the one-colour icon in the accent, which follows
 * the game you're on, so every app's cartridge was drawn again in a new colour at each step.)
 */
@Composable
internal fun rememberOwnAppIcon(packageName: String?): ImageBitmap? {
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
    // (Update 319: with the one-colour icons, the symbol alone — no banner, no blurred copy behind it.)
    val mono = com.shelf.launcher.data.Features.secondDockMono.state.value &&
        com.shelf.launcher.data.IconStyles.of(packageName) != com.shelf.launcher.data.Features.MonoIconStyle.ORIGINAL
    val banner = if (wide && !mono) rememberAppBanner(packageName) else null

    // Plain if / else on purpose: an early `return@Box` inside Box (an inline
    // composable) corrupts Compose's internal bookkeeping on this compiler
    // version and crashes the next redraw — which is exactly what happened.
    Box(modifier) {
        if (banner != null) {
            Image(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (icon != null) {
            // Update 130: the enlarged copy is blurred once, in the picture (BakedBlur): the live blur did nothing on
            // Android 11 and older (and was left out in Lite), so a small icon blown up to the card showed its pixels.
            val soft = if (mono) null else rememberBakedBlur(androidx.compose.runtime.remember(icon) { icon.asAndroidBitmap() }, 24.dp, keepLast = false)
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
 * Update 319: [packageName]'s icon in one colour, [tint] — the app's own monochrome icon filled with it, or its symbol in
 * the tint's shades (its light and dark kept) — with nothing behind it. Nothing until it's loaded.
 */
@Composable
internal fun MonoAppIcon(packageName: String?, tint: Color, modifier: Modifier = Modifier) {
    val icon = rememberMonoAppIcon(packageName, tint)
    if (icon != null) Image(icon, null, contentScale = ContentScale.Fit, modifier = modifier) else Box(modifier)
}

/**
 * Update 319: [packageName]'s one-colour symbol ([AppIconCache.loadMono]) already in [tint] — the app's own monochrome
 * icon filled with it, else its symbol in the tint's shades (light and dark kept). Made once per icon and colour.
 */
@Composable
internal fun rememberMonoAppIcon(packageName: String?, tint: Color, solid: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    val st = produceState<Pair<String?, Pair<ImageBitmap, Boolean>?>>(packageName to packageName?.let { AppIconCache.cachedMono(it) }, packageName, AppIconCache.version) {
        if (packageName == null) { value = null to null; return@produceState }
        AppIconCache.cachedMono(packageName)?.let { value = packageName to it; return@produceState }
        value = packageName to withContext(Dispatchers.IO) { AppIconCache.loadMono(context, packageName) }
    }.value
    val icon = (if (st.first == packageName) st.second else null) ?: return null
    return androidx.compose.runtime.remember(icon, tint, solid) { tintedIcon(icon.first, icon.second || solid, tint) }
}

private fun tintedIcon(src: ImageBitmap, trueMono: Boolean, tint: Color): ImageBitmap {
    val s = src.asAndroidBitmap()
    val out = android.graphics.Bitmap.createBitmap(s.width, s.height, android.graphics.Bitmap.Config.ARGB_8888)
    val argb = android.graphics.Color.argb((tint.alpha * 255).toInt(), (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt())
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    // (Update 319, Toni: "Home and Chrome are different colours": shades of the accent made a light icon pale and a dark
    // one deep. Every icon is the accent itself now; an icon without its own monochrome version keeps its drawing as
    // see-through — its light parts solid, its dark parts fainter.)
    p.colorFilter = if (trueMono) android.graphics.PorterDuffColorFilter(argb, android.graphics.PorterDuff.Mode.SRC_IN) else {
        android.graphics.ColorMatrixColorFilter(floatArrayOf(
            0f, 0f, 0f, 0f, tint.red * 255f,
            0f, 0f, 0f, 0f, tint.green * 255f,
            0f, 0f, 0f, 0f, tint.blue * 255f,
            0.65f * 0.299f, 0.65f * 0.587f, 0.65f * 0.114f, 0.35f, 0f
        ))
    }
    android.graphics.Canvas(out).drawBitmap(s, 0f, 0f, p)
    return out.asImageBitmap()
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
    val icon = if (want) rememberOwnAppIcon(g.packageName) else null
    val bmp = icon?.let { androidx.compose.runtime.remember(it) { it.asAndroidBitmap() } }
    return cover ?: bmp
}
