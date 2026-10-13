package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 122: console logos for the folders (Cross menu, the second screen's Games page) — the emblem alone where a
 * console has one (the PlayStation symbol, the N64 cube, the GameCube "G" cube, the Switch icon, the Dreamcast swirl,
 * the Android robot), else its logo without the extra lines of text under it ("PS2", not "PlayStation 2").
 *
 * From Dan Patrick's "v2 Platform Logos (professionally redrawn official versions)" for LaunchBox, credited in
 * Settings (Cross menu › Consoles as folders) and assets/platform_logos/CREDITS.txt. Shipped in assets/platform_logos/<console key>.svg; a console that isn't in
 * the set falls back to the ES-DE logo Voltcap already had (SystemLogos), then to its name.
 */
object PlatformLogos {
    /** Console keys with a logo in assets/platform_logos. */
    private val KEYS = setOf(
        "NES", "SNES", "N64", "GameCube", "Wii", "WiiU", "Switch", "GB", "GBC", "GBA", "NDS", "3DS", "VirtualBoy", "PokemonMini",
        "PS1", "PS2", "PS3", "PS4", "PSP", "PSVita",
        "Genesis", "MasterSystem", "GameGear", "SegaCD", "32X", "Saturn", "Dreamcast",
        "PCEngine", "PCEngineCD", "NeoGeo", "NeoGeoCD", "NeoGeoPocket", "WonderSwan",
        "Atari2600", "Atari7800", "Jaguar", "Lynx", "Xbox", "Xbox360",
        "Vectrex", "Intellivision", "ColecoVision", "PICO8", "Android"
    )

    /** Logos drawn in black (made for light backgrounds): shown in white, or they'd vanish on a dark folder. */
    private val MONO = setOf("PS3", "PSP", "PSVita", "NDS", "3DS", "SegaCD", "Vectrex", "Saturn", "Atari2600")

    /** Other names a console goes by in Voltcap, mapped to the key of its logo. */
    private val ALIAS = mapOf(
        "Pico8" to "PICO8", "MegaDrive" to "Genesis", "TG16" to "PCEngine", "TGCD" to "PCEngineCD"
    )

    private fun key(platform: String): String? = (ALIAS[platform] ?: platform).takeIf { it in KEYS }

    /** The logo's address (an asset Coil loads with its SVG decoder): the redrawn set first, then ES-DE's, else null. */
    fun url(platform: String): String? =
        key(platform)?.let { "file:///android_asset/platform_logos/$it.svg" } ?: com.shelf.launcher.data.SystemLogos.bundled(platform)

    /** Whether [platform]'s logo is drawn in black and should be shown in white. */
    fun mono(platform: String): Boolean = key(platform) in MONO

    /** The request [PlatformLogo] makes for [url] (one place, so a prefetch fills the same memory-cache entry). */
    internal fun request(context: android.content.Context, url: String): coil.request.ImageRequest =
        coil.request.ImageRequest.Builder(context).data(url)
            .decoderFactory(coil.decode.SvgDecoder.Factory())
            .size(384).build()

    /**
     * Update 130: decodes these consoles' logos ahead (SVG decoding is slow), so a Cross menu folder sliding into the
     * strip has its logo at once instead of popping in after the other icons. (Update 166: the baked ones are read
     * into [baked] here; only the ES-DE fallbacks still go through Coil.)
     */
    fun prefetch(context: android.content.Context, platforms: List<String>) {
        val loader = coil.Coil.imageLoader(context)
        val app = context.applicationContext
        val list = platforms.distinct()
        // (Read away from the screen's thread; the folders find them already decoded.)
        kotlin.concurrent.thread(name = "logos", isDaemon = true) {
            list.forEach { p -> if (baked(app, p) == null) url(p)?.let { loader.enqueue(request(app, it)) } }
        }
    }

    // ---- Update 166: the logos baked into the app ----

    /** Decoded once and kept for as long as the app runs (a few dozen small pictures, about 6 MB at most). */
    private val bakedCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
    private val bakedMissing = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * [platform]'s logo from the app's own PNGs (assets/platform_logos_png, made from the SVGs by
     * tools/render3d/rasterlogos.js), read at once and kept: the Cross menu's folders draw it on their first frame and
     * it never pops in again (the SVGs through Coil were slow to decode and were let go with the image cache). Null:
     * no baked logo for it.
     */
    fun baked(context: android.content.Context, platform: String): androidx.compose.ui.graphics.ImageBitmap? {
        key(platform)?.let { return asset(context, "platform_logos_png/$it.png") }
        // (A console outside the redrawn set: its ES-DE logo, baked the same way.)
        return com.shelf.launcher.data.SystemLogos.bundled(platform)?.let { bakedForUrl(context, it) }
    }

    /**
     * [baked] for a logo address [url] (a Cross menu tile's): the redrawn set's or ES-DE's SVG in the app's assets →
     * its baked PNG (assets/platform_logos_png, assets/system_logos_png); null for anything else.
     */
    fun bakedForUrl(context: android.content.Context, url: String): androidx.compose.ui.graphics.ImageBitmap? {
        val pre = "file:///android_asset/"
        if (!url.startsWith(pre) || !url.endsWith(".svg")) return null
        val path = url.removePrefix(pre).removeSuffix(".svg")
        val dir = path.substringBefore('/', "")
        if (dir != "platform_logos" && dir != "system_logos") return null
        return asset(context, "${dir}_png/${path.substringAfter('/')}.png")
    }

    private fun asset(context: android.content.Context, path: String): androidx.compose.ui.graphics.ImageBitmap? {
        bakedCache[path]?.let { return it }
        if (path in bakedMissing) return null
        val b = runCatching { context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
        if (b == null) { bakedMissing += path; return null }
        val img = b.asImageBitmap().also { it.prepareToDraw() }
        bakedCache[path] = img
        return img
    }
}

/**
 * [platform]'s logo, fitted inside [modifier]. [tint] colours the whole logo (a cut-out); otherwise it keeps its own
 * colours (black logos are shown in white, see [blackToWhite]). Without a logo, or when it fails to load, the console's
 * [name] stands in.
 */
@Composable
internal fun PlatformLogo(
    platform: String,
    name: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    textColor: Color = Color.White,
    textSize: TextUnit = 13.sp,
    /** Black logos in white (for dark backgrounds); off on a light background (the Cross menu's white folders). */
    blackToWhite: Boolean = true
) {
    val context = LocalContext.current
    val url = remember(platform) { PlatformLogos.url(platform) }
    var failed by remember(url) { mutableStateOf(false) }
    // Update 166: the baked logo, there on the first frame (see PlatformLogos.baked).
    val baked = remember(platform) { PlatformLogos.baked(context, platform) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (baked != null) {
            androidx.compose.foundation.Image(
                baked, name, contentScale = ContentScale.Fit,
                colorFilter = when {
                    tint != null -> ColorFilter.tint(tint)
                    blackToWhite && PlatformLogos.mono(platform) -> ColorFilter.tint(Color.White)
                    else -> null
                },
                modifier = Modifier.matchParentSize()
            )
        } else if (url != null && !failed) {
            val request = remember(url) { PlatformLogos.request(context, url) }
            AsyncImage(
                request, name, contentScale = ContentScale.Fit,
                onError = { failed = true },
                colorFilter = when {
                    tint != null -> ColorFilter.tint(tint)
                    blackToWhite && PlatformLogos.mono(platform) -> ColorFilter.tint(Color.White)
                    else -> null
                },
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                name.uppercase(), color = textColor, fontSize = textSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = textSize * 0.5f, maxFontSize = textSize)
            )
        }
    }
}
