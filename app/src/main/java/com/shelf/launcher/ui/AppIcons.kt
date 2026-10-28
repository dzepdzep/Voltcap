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
/** Update 371: [symbol] the one-colour picture, null when none is fit; [trueMono] it is the app's own monochrome icon; [original] its full-colour icon. */
internal class IconCandidate(val symbol: ImageBitmap?, val trueMono: Boolean, val original: ImageBitmap)

internal object AppIconCache {
    private val icons = LruCache<String, ImageBitmap>(96)
    private val banners = LruCache<String, ImageBitmap>(32)
    private val noBanner = HashSet<String>()
    /** Update 319: one-colour icons (picture, and whether it's the app's own monochrome icon) — [loadMono]. */
    private val monos = LruCache<String, IconCandidate>(64)
    /** Update 371: baked one-colour icons by look, colour and package, so a tile re-showing keeps the same picture. */
    private val tinted = LruCache<String, ImageBitmap>(96)

    /** Bumped when a package changes, so icons already on screen reload. */
    var version by mutableIntStateOf(0)
        private set

    /** An app was updated or reinstalled: forget its icon and banner. */
    fun invalidate(pkg: String) {
        icons.remove(pkg)
        banners.remove(pkg)
        monos.remove(pkg)
        tinted.evictAll()
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

    fun cachedMono(pkg: String): IconCandidate? = monos.get(pkg)

    fun tintedOf(key: String, make: () -> ImageBitmap): ImageBitmap = tinted.get(key) ?: make().also { tinted.put(key, it) }

    /**
     * Update 319 / 371: [pkg]'s one-colour candidate: the app's own monochrome icon (Android 13+) when it is fit, else its
     * adaptive foreground alone (no background plate) when that is fit, else a plain icon when it is fit; [IconCandidate.symbol]
     * is null when none is ([com.shelf.launcher.data.IconQuality]) and the original icon is shown. The original is loaded with
     * it (and shared with [loadIcon]'s cache), so falling back never waits for a second load. The visible middle of the
     * layer, at 192 px.
     */
    fun loadMono(context: Context, pkg: String): IconCandidate? = runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        val size = 192
        val original = icons.get(pkg) ?: d.toBitmap(size, size).asImageBitmap().also { icons.put(pkg, it) }
        fun fit(bmp: android.graphics.Bitmap, trueMono: Boolean): Boolean {
            val px = IntArray(size * size)
            bmp.getPixels(px, 0, size, 0, 0, size, size)
            return com.shelf.launcher.data.IconQuality.assess(px, size, size, trueMono).usable
        }
        var symbol: ImageBitmap? = null
        var trueMono = false
        if (android.os.Build.VERSION.SDK_INT >= 26 && d is android.graphics.drawable.AdaptiveIconDrawable) {
            val mono = if (android.os.Build.VERSION.SDK_INT >= 33) d.monochrome else null
            for ((layer, isMono) in listOf(mono to true, d.foreground to false)) {
                if (layer == null) continue
                // An adaptive layer is 108 units across, of which the middle 72 show.
                val full = size * 3 / 2
                val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(out)
                c.translate(-(full - size) / 2f, -(full - size) / 2f)
                layer.setBounds(0, 0, full, full)
                layer.draw(c)
                if (fit(out, isMono)) { symbol = out.asImageBitmap(); trueMono = isMono; break }
            }
        } else if (fit(original.asAndroidBitmap(), false)) symbol = original
        IconCandidate(symbol, trueMono, original)
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

/**
 * An app's icon in its own colours: the main app drawer, lists, notifications and every home layout. (Update 371: it no longer
 * follows the second screen dock's icon style or Features.secondDockMono; the themed looks are [rememberThemedAppIcon].)
 */
@Composable
internal fun rememberAppIcon(packageName: String?): ImageBitmap? {
    com.shelf.launcher.Breadcrumbs.mark("rememberAppIcon")
    return rememberOwnAppIcon(packageName)
}

/** Update 371: the surfaces that can draw an app's icon in the theme's colour. */
internal enum class IconSurface { SHORTCUTS, SECOND_DOCK }

/**
 * Update 371: [packageName]'s icon for a themed [surface], decided once per package, icon version, look and accent before it
 * shows: the one-colour symbol baked in the accent when the app has a fit one (IconQuality), else the original icon. Null
 * only while the app's icon is still loading: callers reserve the space and show nothing meanwhile (never another app's
 * icon, never a full-colour frame that then turns one-colour). Baked, so no colour filter belongs on the Image.
 */
@Composable
internal fun rememberThemedAppIcon(packageName: String?, surface: IconSurface): ImageBitmap? {
    val f = com.shelf.launcher.data.Features
    val themed = if (surface == IconSurface.SHORTCUTS) f.shortcutThemedIcons.state.value else f.secondDockMono.state.value
    val effective = themedStyle(surface == IconSurface.SHORTCUTS, com.shelf.launcher.data.IconStyles.hasOwn(packageName), com.shelf.launcher.data.IconStyles.of(packageName))
    if (!themed || effective == com.shelf.launcher.data.Features.MonoIconStyle.ORIGINAL) return rememberOwnAppIcon(packageName)
    val tint = com.shelf.launcher.ui.theme.ShelfTheme.accent
    val cand = rememberIconCandidate(packageName) ?: return null
    val look = com.shelf.launcher.data.IconQuality.look(true, effective, cand.symbol != null, cand.trueMono)
    val symbol = cand.symbol
    if (look == com.shelf.launcher.data.IconQuality.Look.ORIGINAL || symbol == null) return cand.original
    val argb = android.graphics.Color.argb((tint.alpha * 255).toInt(), (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt())
    val key = "${surface.name}|${look.name}|$argb|$packageName"
    return androidx.compose.runtime.remember(key, cand) {
        AppIconCache.tintedOf(key) { tintedIcon(symbol, look, tint, surface == IconSurface.SHORTCUTS) }
    }
}

/**
 * The style a themed surface applies: the dock follows the app's own or the global style; L1 shortcuts have no global style,
 * only an app's own "own colours" choice opts it out. (Pure, unit tested.)
 */
internal fun themedStyle(shortcuts: Boolean, hasOwn: Boolean, style: com.shelf.launcher.data.Features.MonoIconStyle): com.shelf.launcher.data.Features.MonoIconStyle =
    if (!shortcuts) style
    else if (hasOwn && style == com.shelf.launcher.data.Features.MonoIconStyle.ORIGINAL) style
    else com.shelf.launcher.data.Features.MonoIconStyle.DETAIL

/** The candidate of [packageName], kept with its package: another app's candidate is never returned for a frame. */
@Composable
private fun rememberIconCandidate(packageName: String?): IconCandidate? {
    val context = LocalContext.current
    val st = produceState<Pair<String?, IconCandidate?>>(packageName to packageName?.let { AppIconCache.cachedMono(it) }, packageName, AppIconCache.version) {
        if (packageName == null) { value = null to null; return@produceState }
        AppIconCache.cachedMono(packageName)?.let { value = packageName to it; return@produceState }
        value = packageName to withContext(Dispatchers.IO) { AppIconCache.loadMono(context, packageName) }
    }.value
    return if (st.first == packageName) st.second else packageName?.let { AppIconCache.cachedMono(it) }
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
    // (Update 371: always the app's own art: the home layouts and second screen cards never take the dock's icon style.)
    val icon = rememberOwnAppIcon(packageName)
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
 * Update 319 / 371: [src] baked in [tint] for [look]: the app's own monochrome icon filled with it, its shape alone (Solid),
 * or its symbol with the light parts solid and the dark ones fainter (Detail). L1 [shortcut] tiles keep the light and dark
 * of the symbol in shades of the accent (the look chosen there). One colour operation only: the Image gets no filter.
 */
private fun tintedIcon(src: ImageBitmap, look: com.shelf.launcher.data.IconQuality.Look, tint: Color, shortcut: Boolean): ImageBitmap {
    val s = src.asAndroidBitmap()
    val out = android.graphics.Bitmap.createBitmap(s.width, s.height, android.graphics.Bitmap.Config.ARGB_8888)
    val argb = android.graphics.Color.argb((tint.alpha * 255).toInt(), (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt())
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    val flat = look == com.shelf.launcher.data.IconQuality.Look.MONO || look == com.shelf.launcher.data.IconQuality.Look.SOLID
    p.colorFilter = if (flat) android.graphics.PorterDuffColorFilter(argb, android.graphics.PorterDuff.Mode.SRC_IN)
    else if (shortcut) {
        // Brightened a little so dark icons don't vanish on the dark tile.
        val r = 0.35f + 0.9f * tint.red; val g = 0.35f + 0.9f * tint.green; val b = 0.35f + 0.9f * tint.blue
        android.graphics.ColorMatrixColorFilter(floatArrayOf(
            0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
            0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
            0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    } else android.graphics.ColorMatrixColorFilter(floatArrayOf(
        0f, 0f, 0f, 0f, tint.red * 255f,
        0f, 0f, 0f, 0f, tint.green * 255f,
        0f, 0f, 0f, 0f, tint.blue * 255f,
        0.65f * 0.299f, 0.65f * 0.587f, 0.65f * 0.114f, 0.35f, 0f
    ))
    android.graphics.Canvas(out).drawBitmap(s, 0f, 0f, p)
    return out.asImageBitmap()
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
