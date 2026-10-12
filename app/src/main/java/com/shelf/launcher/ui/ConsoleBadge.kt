package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Android
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.SystemLogos
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 115: a small console badge in the corner of every game's tile (Game look › Console badges): the console's
 * logo in white on a pill in the console's own colour, or in your theme's colour (Features.consoleBadgeTheme).
 * Sized from the tile, so it stays small on small tiles; not on spines (they carry the console already).
 */

/** The console's colour for its badge (its usual brand colour; a neutral grey for the rest). */
internal fun consoleColor(platform: String): Color = when (platform) {
    "NES", "FDS" -> Color(0xFFC4161C)
    "SNES" -> Color(0xFF514689)
    "N64" -> Color(0xFF1E5AA8)
    "GB" -> Color(0xFF5D5F8E)
    "GBC" -> Color(0xFF6B2D8F)
    "GBA" -> Color(0xFF3B2E91)
    "NDS" -> Color(0xFF5E6068)
    "3DS" -> Color(0xFFCE181E)
    "GameCube" -> Color(0xFF5B4FA8)
    "Wii", "WiiU" -> Color(0xFF009AC7)
    "Switch" -> Color(0xFFE60012)
    "VirtualBoy" -> Color(0xFFB0101C)
    "PS1" -> Color(0xFF3D4A9E)
    "PS2" -> Color(0xFF1D3C8F)
    "PS3" -> Color(0xFF2B2B2E)
    "PSP" -> Color(0xFF2B2B2E)
    "PSVita" -> Color(0xFF1E50A2)
    "Genesis", "SegaCD", "MasterSystem" -> Color(0xFF0060A8)
    "32X" -> Color(0xFFC8102E)
    "GameGear" -> Color(0xFF2B2B2E)
    "Saturn" -> Color(0xFF3F3F8F)
    "Dreamcast" -> Color(0xFFE8641C)
    "PCEngine", "PCEngineCD" -> Color(0xFFD9002B)
    "NeoGeo", "NeoGeoCD" -> Color(0xFFB08A00)
    "NeoGeoPocket" -> Color(0xFF2C2C8C)
    "WonderSwan" -> Color(0xFF0072BC)
    "Arcade", "Naomi", "Atomiswave" -> Color(0xFF9A6B00)
    "Atari2600", "Atari7800" -> Color(0xFF9A4F1A)
    "Lynx" -> Color(0xFFE06A00)
    "Jaguar" -> Color(0xFFB0101C)
    "Xbox" -> Color(0xFF107C10)
    "App", "Apps", "Android" -> Color(0xFF1F8A4C)
    else -> Color(0xFF44484F)
}

/**
 * The console badge for [platform], in the top left corner of the tile it's drawn over (put it last in the tile's
 * Box). Nothing when Console badges is off.
 */
@Composable
internal fun BoxScope.ConsoleBadge(platform: String, corner: Alignment = Alignment.TopStart, packageName: String? = null) {
    if (!Features.consoleBadges.state.value) return
    BoxWithConstraints(Modifier.matchParentSize()) {
        // Too small a tile (a small slot on a small screen): no badge.
        if (maxWidth < 44.dp) return@BoxWithConstraints
        val h = (maxWidth * 0.13f).coerceIn(11.dp, 20.dp)
        val bg = if (Features.consoleBadgeTheme.state.value) ShelfTheme.accent else consoleColor(platform)
        // Update 119: Android games and apps get the Android robot in front of the word; a word badge is as wide as its
        // word (it used to be cut off in a logo-sized pill).
        val android = platform.equals("App", true) || platform.equals("Android", true) || platform.equals("Apps", true)
        // Update 125: the word says what the app is: GAME for games, MEDIA for music and video apps, APP for the rest.
        val androidWord = if (android) {
            val context = LocalContext.current
            remember(packageName) { AppKinds.kind(context, packageName).word }
        } else ""
        val pill = Modifier.align(corner).padding(h * 0.3f).height(h).clip(RoundedCornerShape(50)).background(bg.copy(alpha = 0.92f))
        // Update 125: Console badges › Logos — the console's own logo (Dan Patrick's white set) on its colour, the pill
        // as wide as the logo needs.
        val badgeLogo = if (!android && Features.consoleBadgeLogos.state.value) BadgeLogos.of(platform) else null
        if (badgeLogo != null) {
            // (Update 129: a symbol alone gets a short pill, about as wide as it is.)
            val symbol = badgeLogo.first.endsWith(".webp")
            // Update 136: symbols a little bigger in their pill (they were barely readable on a handheld's screen).
            val lh = h * (if (symbol) 1.3f else 1.15f)
            val w = (lh * (if (symbol) 0.74f else 0.62f) * badgeLogo.second + lh * 0.7f).coerceIn(if (symbol) lh * 1.3f else lh * 2.2f, maxWidth * 0.78f)
            Box(
                Modifier.align(corner).padding(h * 0.3f).height(lh).width(w).clip(RoundedCornerShape(50))
                    .background(bg.copy(alpha = 0.94f)).padding(horizontal = lh * 0.35f, vertical = lh * (if (symbol) 0.13f else 0.19f)),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val req = remember(badgeLogo.first) {
                    coil.request.ImageRequest.Builder(context).data(badgeLogo.first)
                        .apply { if (!symbol) decoderFactory(coil.decode.SvgDecoder.Factory()) }.size(384).build()
                }
                AsyncImage(req, platform, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.fillMaxSize())
            }
            return@BoxWithConstraints
        }
        val logo = SystemLogos.bundled(platform)
        if (logo != null && !android) Box(
            pill.width(h * 3.4f).padding(horizontal = h * 0.35f, vertical = h * 0.18f),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val req = remember(logo) {
                coil.request.ImageRequest.Builder(context).data(logo).decoderFactory(coil.decode.SvgDecoder.Factory()).size(192).build()
            }
            AsyncImage(req, platform, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White), modifier = Modifier.fillMaxSize())
        } else androidx.compose.foundation.layout.Row(
            pill.widthIn(max = maxWidth * 0.8f).padding(horizontal = h * 0.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (android) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Android, null, tint = Color.White,
                    modifier = Modifier.size(h * 0.72f)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(h * 0.18f))
            }
            Text(
                (if (android) androidWord else LocalTitleOptions.current.console(platform)).uppercase(),
                color = Color.White, fontSize = (h.value * 0.5f).sp, lineHeight = (h.value * 0.6f).sp,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Update 125: what an Android app is, for its badge — a game (the app says so: its category or the old game flag), a
 * music / video app (its category, or a few well-known players that don't set one), or any other app. Cached per
 * package; a package that can't be read counts as an app.
 */
internal object AppKinds {
    enum class Kind(val word: String) { GAME("Game"), MEDIA("Media"), APP("App") }

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Kind>()

    /** Players that don't set a media category in their manifest (or set none at all). */
    private val KNOWN_MEDIA = listOf(
        "com.spotify", "com.netflix", "com.google.android.youtube", "com.google.android.apps.youtube", "com.disney",
        "com.amazon.avod", "com.amazon.mp3", "tv.twitch", "com.plexapp", "org.jellyfin", "org.videolan", "org.xbmc",
        "com.hbo", "com.wbd", "com.crunchyroll", "com.soundcloud", "deezer.android", "com.aspiro.tidal",
        "com.apple.android.music", "com.pandora", "com.audible", "com.google.android.apps.youtube.music",
        "com.zhiliaoapp.musically", "tv.pluto", "com.tubitv", "com.dazn", "com.paramountplus", "com.peacocktv"
    )

    fun kind(context: android.content.Context, packageName: String?): Kind {
        if (packageName.isNullOrBlank()) return Kind.APP
        cache[packageName]?.let { return it }
        val k = runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            val cat = if (android.os.Build.VERSION.SDK_INT >= 26) info.category else -1
            @Suppress("DEPRECATION")
            when {
                cat == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                    (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0 -> Kind.GAME
                cat == android.content.pm.ApplicationInfo.CATEGORY_AUDIO || cat == android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> Kind.MEDIA
                KNOWN_MEDIA.any { packageName.startsWith(it) } -> Kind.MEDIA
                else -> Kind.APP
            }
        }.getOrDefault(if (KNOWN_MEDIA.any { packageName.startsWith(it) }) Kind.MEDIA else Kind.APP)
        cache[packageName] = k
        return k
    }
}

/**
 * Update 125: the console logos for Console badges › Logos — Dan Patrick's white set, one per console, in
 * assets/badge_logos/<key>.svg — with each logo's width ÷ height, so the pill is as wide as its logo needs.
 */
internal object BadgeLogos {
    private val ASPECT = mapOf(
        "NES" to 6.98f, "SNES" to 5.57f, "N64" to 4.42f, "GameCube" to 4.43f, "Wii" to 2.09f, "WiiU" to 3.55f, "Switch" to 4.56f,
        "GB" to 5.83f, "GBC" to 8.31f, "GBA" to 11.19f, "NDS" to 6.99f, "3DS" to 8.19f, "VirtualBoy" to 3.74f, "PokemonMini" to 8.13f,
        "FDS" to 4.41f, "PS1" to 5.1f, "PS2" to 5.75f, "PS3" to 4.37f, "PS4" to 4.61f, "PSP" to 5.85f, "PSVita" to 5.42f,
        "Genesis" to 8.56f, "MasterSystem" to 2.78f, "GameGear" to 6.49f, "SegaCD" to 6.86f, "32X" to 3.74f, "Saturn" to 8.09f,
        "Dreamcast" to 5.82f, "PCEngine" to 1.99f, "PCEngineCD" to 2.51f, "NeoGeo" to 5.71f, "NeoGeoCD" to 7.65f,
        "NeoGeoPocket" to 4.49f, "WonderSwan" to 7.79f, "Atari2600" to 4.61f, "Atari7800" to 7.64f, "Jaguar" to 2.82f,
        "Lynx" to 10.58f, "Xbox" to 4.08f, "Xbox360" to 4.83f, "Vectrex" to 3.84f, "Intellivision" to 6.78f,
        "ColecoVision" to 3.39f, "PICO8" to 3.89f, "Android" to 6.56f, "3DO" to 3.28f, "Arcade" to 4.82f, "Naomi" to 2.63f,
        "Atomiswave" to 4.31f
    )
    private val ALIAS = mapOf(
        "Pico8" to "PICO8", "MegaDrive" to "Genesis", "TG16" to "PCEngine", "TGCD" to "PCEngineCD", "Famicom" to "NES",
        "SFC" to "SNES", "MAME" to "Arcade", "FBNeo" to "Arcade", "NeoGeoPocketColor" to "NeoGeoPocket", "WonderSwanColor" to "WonderSwan"
    )

    /**
     * Update 129: the console's symbol alone, without any lettering beside it (cut from the same set, Light - Just
     * White, as assets/badge_icons/<key>.webp), for the consoles that have one; the others keep their logo.
     */
    private val ICON = mapOf(
        "PS1" to 1.28f, "PS2" to 1.28f, "PS3" to 1.28f, "PS4" to 1.3f, "PSVita" to 1.28f, "Xbox" to 1.31f, "Xbox360" to 1.0f,
        "GameCube" to 0.88f, "Switch" to 1.0f, "N64" to 1.0f, "Dreamcast" to 1.13f, "Saturn" to 1.59f, "SNES" to 1.41f,
        "NeoGeo" to 1.03f, "NeoGeoCD" to 1.5f, "3DO" to 0.6f, "Atari2600" to 1.24f, "Atari7800" to 1.24f, "Lynx" to 1.24f,
        "GameGear" to 2.08f, "WonderSwan" to 1.17f, "Atomiswave" to 1.17f
    )

    /** [platform]'s logo address and its width ÷ height, or null when the set has none (the normal badge shows). */
    fun of(platform: String): Pair<String, Float>? {
        val key = ALIAS[platform] ?: platform
        ICON[key]?.let { return "file:///android_asset/badge_icons/$key.webp" to it }
        val a = ASPECT[key] ?: return null
        return "file:///android_asset/badge_logos/$key.svg" to a
    }
}
