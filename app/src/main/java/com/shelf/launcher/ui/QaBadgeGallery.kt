package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Batch 521b (owner: "a complete console badge and logo audit with the actual rendered assets"), QA app / test hooks only:
 * `--es voltcap_test_badgegallery N` draws page N of every console Voltcap names, grouped by maker, each with the REAL
 * composables and assets the app uses — the badge in its three looks (Batch 522: Text, Symbol, Logo on 96 dp tiles, Logo also
 * on a 200 dp tile), the raw wordmark, the folder logo
 * (PlatformLogos, baked), the plain mono icon and the ES-DE system logo — on one grey so differences show. `off` closes it.
 */
internal object QaBadgeGallery {
    val MAKERS: List<Pair<String, List<String>>> = listOf(
        "Nintendo" to listOf("NES", "Famicom", "FDS", "SNES", "SFC", "N64", "GameCube", "Wii", "WiiU", "Switch", "Switch2", "GB", "GBC", "GBA",
            "NDS", "3DS", "VirtualBoy", "PokemonMini"),
        "Sony" to listOf("PS1", "PS2", "PS3", "PS4", "PS5", "PSP", "PSVita"),
        "Microsoft" to listOf("Xbox", "Xbox360", "XboxOne", "XboxSeries"),
        "Sega" to listOf("SG1000", "MasterSystem", "Genesis", "MegaDrive", "SegaCD", "32X", "Saturn", "Dreamcast", "GameGear", "Naomi"),
        "NEC / Hudson" to listOf("PCEngine", "PCEngineCD", "TG16", "TGCD"),
        "SNK" to listOf("NeoGeo", "NeoGeoCD", "NeoGeoPocket", "NeoGeoPocketColor"),
        "Atari" to listOf("Atari2600", "Atari5200", "Atari7800", "Lynx", "Jaguar"),
        "Bandai" to listOf("WonderSwan", "WonderSwanColor"),
        "Other" to listOf("3DO", "CDi", "ColecoVision", "Intellivision", "Vectrex", "Atomiswave", "Arcade", "C64", "Amiga", "MSX", "DOS",
            "ScummVM", "PICO8", "Android"),
        // (Batch 523: the PC game stores - GameNative's / DroidDeck's categories - in the gallery too.)
        "PC / stores" to listOf("Steam", "Epic", "GOG", "Amazon", "PC")
    )
    const val PER_PAGE = 6

    /** Pages: (maker, consoles on it), each maker split into pages of [PER_PAGE]. */
    val pages: List<Pair<String, List<String>>> by lazy {
        MAKERS.flatMap { (m, ks) -> ks.chunked(PER_PAGE).mapIndexed { i, c -> (if (ks.size > PER_PAGE) "$m ${i + 1}" else m) to c } }
    }
}

@Composable
internal fun QaBadgeGalleryPage(page: Int, assetsView: Boolean) {
    val (maker, keys) = QaBadgeGallery.pages.getOrNull(page) ?: return
    val context = LocalContext.current
    val grey = Color(0xFF3A3D44)
    Column(Modifier.fillMaxSize().background(Color(0xFF15171B)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("BADGE AUDIT  ${if (assetsView) "assets" else "badges"}  $maker  (page ${page + 1} / ${QaBadgeGallery.pages.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (!assetsView) listOf("console" to 76.dp, "Text 96" to 96.dp, "Symbol 96" to 96.dp, "Logo 96" to 96.dp, "Logo 200" to 200.dp)
            else listOf("console" to 86.dp, "badge-set wordmark" to 120.dp, "folder logo" to 80.dp, "mono" to 50.dp, "ES-DE logo" to 80.dp)).forEach { (t, w) ->
                Text(t, color = Color(0xFFB0B4BC), fontSize = 9.sp, modifier = Modifier.width(w))
            }
        }
        keys.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(if (assetsView) 86.dp else 76.dp)) {
                    Text(p, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(com.shelf.launcher.data.ConsoleNames.display(p), color = Color(0xFF9AA0A8), fontSize = 8.sp, maxLines = 2, lineHeight = 9.sp)
                }
                // The real badge on a plain tile in each look (the badge sizes itself from the tile).
                // (Batch 522: the three looks - Text, Symbol, Logo - and Logo on bigger tiles, where a wide wordmark shows whole.)
                if (!assetsView) for ((w, st) in listOf(96.dp to com.shelf.launcher.data.Features.BadgeStyle.TEXT, 96.dp to com.shelf.launcher.data.Features.BadgeStyle.SYMBOL, 96.dp to com.shelf.launcher.data.Features.BadgeStyle.LOGO, 200.dp to com.shelf.launcher.data.Features.BadgeStyle.LOGO)) {
                    Box(Modifier.size(w, 40.dp).clip(RoundedCornerShape(6.dp)).background(grey)) {
                        ConsoleBadge(p, cornerAuto = Alignment.TopStart, styleOverride = st)
                    }
                }
                if (assetsView) {
                // The raw wordmark from the badge set (even where the badge uses the symbol), white on the console's colour.
                Box(Modifier.size(120.dp, 26.dp).clip(RoundedCornerShape(50)).background(consoleColor(p)).padding(horizontal = 8.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                    val wm = remember(p) { wordmarkOf(context, p) }
                    if (wm != null) {
                        val req = remember(wm) { coil.request.ImageRequest.Builder(context).data(wm).decoderFactory(coil.decode.SvgDecoder.Factory()).size(384).build() }
                        AsyncImage(req, p, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.fillMaxSize())
                    } else Text("—", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                // Folder logo (PlatformLogos: the redrawn set, baked; else ES-DE baked; else the name).
                Box(Modifier.size(80.dp, 40.dp).clip(RoundedCornerShape(6.dp)).background(grey).padding(4.dp), contentAlignment = Alignment.Center) {
                    // The real folder logo composable (its own fallbacks: redrawn set, ES-DE, then the name).
                    PlatformLogo(p, com.shelf.launcher.data.ConsoleNames.display(p), Modifier.fillMaxSize(), textSize = 9.sp)
                }
                // Mono icon.
                Box(Modifier.size(50.dp, 40.dp).clip(RoundedCornerShape(6.dp)).background(grey).padding(4.dp), contentAlignment = Alignment.Center) {
                    val m = remember(p) { PlatformLogos.plain(context, p) }
                    if (m != null) Image(m, p, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.fillMaxSize())
                    else Text("—", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                // ES-DE colour system logo (what "Console badges" and the CDs use).
                Box(Modifier.size(80.dp, 40.dp).clip(RoundedCornerShape(6.dp)).background(grey).padding(4.dp), contentAlignment = Alignment.Center) {
                    val url = com.shelf.launcher.data.SystemLogos.bundled(p)
                    val bmp = remember(url) { url?.let { PlatformLogos.bakedForUrl(context, it) } }
                    if (bmp != null) Image(bmp, p, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    else Text("—", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                }
            }
        }
    }
}

/** The badge set's own wordmark for [p] (assets/badge_logos); null when none. */
@Suppress("UNUSED_PARAMETER")
private fun wordmarkOf(context: android.content.Context, p: String): String? = BadgeLogos.wordmark(p)?.first // (Batch 522: its own only)

