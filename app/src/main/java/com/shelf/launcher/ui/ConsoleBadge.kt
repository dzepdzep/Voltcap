package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    // (Batch 521b, badge audit: the regional / colour / later models of a family take that family's colour - they fell to the
    // neutral grey: Famicom, Super Famicom, Mega Drive, TurboGrafx, Neo Geo Pocket Color, WonderSwan Color, Xbox 360 / One / Series,
    // Switch 2.)
    "NES", "FDS", "Famicom" -> Color(0xFFC4161C)
    "SNES", "SFC" -> Color(0xFF514689)
    "N64" -> Color(0xFF1E5AA8)
    "GB" -> Color(0xFF5D5F8E)
    "GBC" -> Color(0xFF6B2D8F)
    "GBA" -> Color(0xFF3B2E91)
    "NDS" -> Color(0xFF5E6068)
    "3DS" -> Color(0xFFCE181E)
    "GameCube" -> Color(0xFF5B4FA8)
    "Wii", "WiiU" -> Color(0xFF009AC7)
    "Switch", "Switch2" -> Color(0xFFE60012)
    "VirtualBoy" -> Color(0xFFB0101C)
    "PS1" -> Color(0xFF3D4A9E)
    "PS2" -> Color(0xFF1D3C8F)
    "PS3" -> Color(0xFF2B2B2E)
    // (Batch 522 badge audit: PS4 / PS5 drew the grey "no colour" pill - PlayStation blue and the PS5's lighter blue.)
    "PS4" -> Color(0xFF003791)
    "PS5" -> Color(0xFF0070D1)
    "PSP" -> Color(0xFF2B2B2E)
    "PSVita" -> Color(0xFF1E50A2)
    "Genesis", "MegaDrive", "SegaCD", "MasterSystem", "SG1000" -> Color(0xFF0060A8)
    "32X" -> Color(0xFFC8102E)
    "GameGear" -> Color(0xFF2B2B2E)
    "Saturn" -> Color(0xFF3F3F8F)
    "Dreamcast" -> Color(0xFFE8641C)
    "PCEngine", "PCEngineCD", "TG16", "TGCD" -> Color(0xFFD9002B)
    "NeoGeo", "NeoGeoCD" -> Color(0xFFB08A00)
    "NeoGeoPocket", "NeoGeoPocketColor" -> Color(0xFF2C2C8C)
    "WonderSwan", "WonderSwanColor" -> Color(0xFF0072BC)
    "Arcade", "Naomi", "Atomiswave" -> Color(0xFF9A6B00)
    "Atari2600", "Atari5200", "Atari7800" -> Color(0xFF9A4F1A)
    "Lynx" -> Color(0xFFE06A00)
    "Jaguar" -> Color(0xFFB0101C)
    "Xbox", "Xbox360", "XboxOne", "XboxSeries" -> Color(0xFF107C10)
    "App", "Apps", "Android" -> Color(0xFF1F8A4C)
    // (Batch 523: the PC game stores in their own brand colours - Steam's navy, GOG's purple, Epic's near-black, Amazon's dark
    // "squid ink"; plain PC games keep the neutral grey.)
    "Steam" -> Color(0xFF1B2838)
    "GOG" -> Color(0xFF86328A)
    "Epic" -> Color(0xFF313131)
    "Amazon" -> Color(0xFF232F3E)
    else -> Color(0xFF44484F)
}

/**
 * The console badge for [platform], in the top left corner of the tile it's drawn over (put it last in the tile's
 * Box). Nothing when Console badges is off.
 */
internal fun badgeCornerOr(auto: Alignment): Alignment = when (Features.consoleBadgeCorner.state.value) {
    Features.BadgeCorner.AUTO -> auto
    Features.BadgeCorner.TOP_LEFT -> Alignment.TopStart
    Features.BadgeCorner.TOP_RIGHT -> Alignment.TopEnd
    Features.BadgeCorner.BOTTOM_LEFT -> Alignment.BottomStart
    Features.BadgeCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
}

@Composable
internal fun BoxScope.ConsoleBadge(
    platform: String, cornerAuto: Alignment = Alignment.TopStart, packageName: String? = null,
    /** Update 202: the tile shows the game's case (its printed band carries the console's logo already). */
    onCase: Boolean = false,
    /** Batch 521b / 522 (QA badge gallery): the badge look for this badge only; null = the setting. */
    styleOverride: Features.BadgeStyle? = null
) {
    if ((styleOverride == null && !Features.consoleBadges.state.value) || LocalArtMirror.current) return
    // (Update 317ao: the corner picked in Game look › Console badges › Where, else the layout's own.)
    val corner = badgeCornerOr(cornerAuto)
    // Update 202: not on a case that prints its console's band (the Portal showed "NINTENDO GAMECUBE" twice on every
    // GameCube case — the badge's pill over the case's own band; the same on PS2, Switch, N64, Android…). Only the
    // PlayStation (PS1) case, drawn without its band, keeps the badge.
    if (onCase && caseShowsConsole(platform)) return
    // (Update 203: turned with a solid cartridge it sits on — the view hands its turn down, SolidPiece.kt.)
    BoxWithConstraints(Modifier.matchParentSize().followTurn()) {
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
        // Batch 522 (owner): one look per setting, never mixed - Text, Symbol or Logo (Features.BadgeStyle); a console the
        // look has no artwork for shows its short name in the same pill.
        val style = styleOverride ?: Features.consoleBadgeStyle.state.value
        // (Batch 523, final polish: one pill height per look - the Symbol and Logo looks' name / compact / ES-DE pills are as
        // tall as their artwork pills (they were 13 % lower than the logos beside them); the Text look keeps its own height.)
        val pillH = if (android || style == Features.BadgeStyle.TEXT) h else h * BadgeLogos.ART_PILL
        val pill = Modifier.align(corner).padding(h * 0.3f).height(pillH).clip(RoundedCornerShape(50)).background(bg.copy(alpha = 0.92f))
        // (Batch 522: a big tile lets the pill run a little wider, so a long wordmark - Game Boy Advance - shows whole there.)
        val cap = maxWidth * (if (maxWidth < 150.dp) 0.78f else 0.85f)
        // The artwork ([url], [aspect] = width / height) in a pill as wide as it needs (Update 125 / 129 / 136). Batch 523: a
        // wordmark whose lettering is only part of its height (emblem + small words, two lines) is drawn up to 30 % taller
        // in the same pill ([BadgeLogos.artHeight]), so its words read about as big as the others'; the pill never grows taller.
        val setArt: @Composable (String, Float, Boolean, Float) -> Unit = { url, aspect, symbol, lettering ->
            val lh = h * BadgeLogos.ART_PILL
            val artH = BadgeLogos.artHeight(aspect, lettering, symbol, h.value, cap.value).dp
            val w = (artH * aspect + lh * 0.7f).coerceIn(if (symbol) lh * 1.3f else lh * 2.2f, cap)
            Box(
                Modifier.align(corner).padding(h * 0.3f).height(lh).width(w).clip(RoundedCornerShape(50))
                    .background(bg.copy(alpha = 0.94f)).padding(horizontal = lh * 0.35f),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val req = remember(url) {
                    coil.request.ImageRequest.Builder(context).data(url)
                        .apply { if (url.endsWith(".svg")) decoderFactory(coil.decode.SvgDecoder.Factory()) }.size(384).build()
                }
                AsyncImage(
                    req, platform, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.fillMaxWidth().height(artH)
                )
            }
        }
        val nameText: @Composable () -> Unit = {
            androidx.compose.foundation.layout.Row(
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
                // (Batch 521b, badge audit: the console's SHORT name — "SWITCH 2", "PS5", "XBOX SERIES" — whole, never cut with
                // "…" (rule 11). A long one steps its size down.)
                Text(
                    (if (android) androidWord else com.shelf.launcher.data.ConsoleNames.short(platform)).uppercase(),
                    color = Color.White, lineHeight = (h.value * 0.6f).sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = (h.value * 0.36f).sp, maxFontSize = (h.value * 0.5f).sp)
                )
            }
        }
        if (android || style == Features.BadgeStyle.TEXT) { nameText(); return@BoxWithConstraints }
        val symbolArt: @Composable () -> Unit = {
            BadgeLogos.symbol(platform)?.let { setArt(it.first, it.second, true, 1f) } ?: nameText()
        }
        if (style == Features.BadgeStyle.SYMBOL) { symbolArt(); return@BoxWithConstraints }
        // LOGO: the console's own wordmark. One too wide for this tile's pill (GBA 11 : 1, Lynx, Genesis…: drawn at under
        // 90 % of its size in the pill), or whose words would be smaller than the Text look's letters here (Batch 523), shows
        // the compact form here - its symbol, else its name - and the whole wordmark on tiles with room for it.
        val mark = BadgeLogos.wordmark(platform)
        if (mark != null) {
            val lettering = BadgeLogos.lettering(platform)
            if (BadgeLogos.showsWordmark(mark.second, lettering, h.value, cap.value)) setArt(mark.first, mark.second, false, lettering)
            else symbolArt()
            return@BoxWithConstraints
        }
        // No wordmark in the set: the ES-DE logo in white (Batch 521b: not one that turns into a solid block in one colour),
        // sized and checked like a wordmark (Batch 523: it sat in a fixed 3.4 x pill, so TurboGrafx / NGPC came out tiny).
        val logo = SystemLogos.bundled(platform)?.takeUnless { SystemLogos.silhouetteUnreadable(platform) }
        val form = logo?.let { BadgeLogos.esdeForm(it) }
        if (logo != null && form != null && BadgeLogos.showsWordmark(form.first, form.second, h.value, cap.value)) {
            setArt(logo, form.first, false, form.second)
        } else nameText()
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
        "ColecoVision" to 3.39f, "PICO8" to 3.89f, "Android" to 6.56f, "Arcade" to 4.82f, "Naomi" to 2.63f,
        "Atomiswave" to 4.31f
    )
    // (Batch 521b, badge audit: a regional / colour model never wears another console's lettering - Mega Drive showed
    // "GENESIS", TurboGrafx-16 / -CD "PC Engine", Famicom "NES", Neo Geo Pocket Color "Neo Geo Pocket". Those now take their
    // own ES-DE logo, or their name, like the other badges. Kept: one console under two keys (Pico8, MAME / FBNeo = Arcade) and
    // the emblems both regions print (the Super Famicom's four buttons, the WonderSwan's mark).)
    private val ALIAS = mapOf(
        "Pico8" to "PICO8", "SFC" to "SNES", "MAME" to "Arcade", "FBNeo" to "Arcade", "WonderSwanColor" to "WonderSwan"
    )

    /**
     * Update 129: the console's symbol alone, without any lettering beside it (cut from the same set, Light - Just
     * White, as assets/badge_icons/<key>.webp), for the consoles that have one; the others keep their logo.
     */
    private val ICON = mapOf(
        "PS1" to 1.28f, "PS2" to 1.28f, "PS3" to 1.28f, "PS4" to 1.3f, "PSVita" to 1.28f, "Xbox" to 1.31f, "Xbox360" to 1.0f,
        "GameCube" to 0.88f, "Switch" to 1.0f, "N64" to 1.0f, "Dreamcast" to 1.13f, "Saturn" to 1.59f, "SNES" to 1.41f,
        "NeoGeo" to 1.03f, "NeoGeoCD" to 1.5f, "Atari2600" to 1.24f, "Atari7800" to 1.24f, "Lynx" to 1.24f,
        "GameGear" to 2.08f, "WonderSwan" to 1.17f, "Atomiswave" to 1.17f
    )

    /** Batch 521b: the badge set's key for [platform] (its own, or the console it borrows from). */
    fun keyFor(platform: String): String = ALIAS[platform] ?: platform

    // (Batch 521b: 3DO left out of both maps - its symbol and wordmark in the set are dark shapes on a white square, not the white
    // set's, so the white badge drew a white rectangle. It shows its name, like the consoles the set doesn't have.)
    /** [platform]'s logo address and its width ÷ height, or null when the set has none (the normal badge shows). */
    fun of(platform: String): Pair<String, Float>? = symbol(platform) ?: wordmark(platform)

    /** Batch 522: [platform]'s symbol (assets/badge_icons) and its width ÷ height; the emblems both models print are shared. */
    fun symbol(platform: String): Pair<String, Float>? {
        val key = ALIAS[platform] ?: platform
        return ICON[key]?.let { "file:///android_asset/badge_icons/$key.webp" to it }
    }

    /**
     * Batch 522: [platform]'s own wordmark (assets/badge_logos) and its width ÷ height. Only the same console under another
     * key borrows one (Pico8, MAME / FBNeo = Arcade): a Super Famicom never wears "SUPER NINTENDO", a WonderSwan Color never
     * the WonderSwan's lettering - those show their ES-DE logo or name.
     */
    fun wordmark(platform: String): Pair<String, Float>? {
        val key = WORDMARK_ALIAS[platform] ?: platform
        return ASPECT[key]?.let { "file:///android_asset/badge_logos/$key.svg" to it }
    }
    private val WORDMARK_ALIAS = mapOf("Pico8" to "PICO8", "MAME" to "Arcade", "FBNeo" to "Arcade")

    /**
     * Batch 522: a wordmark of [aspect] (width ÷ height) is drawn at 90 % or more of its natural size inside a pill no
     * wider than [capDp] on a badge [hDp] tall (the pill sizes of the badge above); narrower = the compact form there.
     */
    fun fitsWordmark(aspect: Float, hDp: Float, capDp: Float): Boolean {
        val lh = hDp * ART_PILL
        return (capDp - lh * 0.7f) / (lh * 0.62f * aspect) >= 0.9f
    }

    /** Batch 523: the Symbol / Logo looks' pill height, × the badge height (the Text look's pill). */
    const val ART_PILL = 1.15f

    /**
     * Batch 523 (owner: "some logos look noticeably smaller"): the height the artwork is drawn at in its pill. A symbol fills
     * 84 % of the pill; a wordmark 62 %, or up to 30 % more (never past the pill, never wider than [capDp]) when its
     * lettering - [lettering], the height of its words ÷ the logo's height - is under 0.75 (an emblem with small words
     * beside it, two lines). Partial (square root), so each logo keeps its own proportions.
     */
    fun artHeight(aspect: Float, lettering: Float, symbol: Boolean, hDp: Float, capDp: Float): Float {
        val lh = hDp * ART_PILL
        if (symbol) return minOf(lh * 0.84f, (capDp - lh * 0.7f) / aspect)
        val boost = kotlin.math.sqrt(0.75f / lettering.coerceIn(0.1f, 1f)).coerceIn(1f, 1.3f)
        return minOf(lh * 0.62f * boost, (capDp - lh * 0.7f) / aspect)
    }

    /**
     * Batch 523: the whole wordmark fits this badge ([fitsWordmark]) and its words come out at least as big as 3/4 of
     * the Text look's capital letters on the same badge (0.26 × badge height); otherwise the compact form shows.
     */
    fun showsWordmark(aspect: Float, lettering: Float, hDp: Float, capDp: Float): Boolean =
        fitsWordmark(aspect, hDp, capDp) && artHeight(aspect, lettering, false, hDp, capDp) * lettering >= hDp * 0.26f

    /**
     * Batch 523: the height of each wordmark's words ÷ the logo's height, measured on the white set (connected shapes,
     * emblems left out, width-weighted; 3DS / DS by eye: their big "3DS" / "DS"). Not listed = 1 (the words are the logo).
     */
    private val LETTERING = mapOf(
        "Atomiswave" to 0.29f, "MasterSystem" to 0.33f, "GameCube" to 0.34f, "N64" to 0.36f, "NES" to 0.38f,
        "NeoGeoPocket" to 0.40f, "Xbox" to 0.43f, "WonderSwan" to 0.47f, "ColecoVision" to 0.48f, "Naomi" to 0.51f,
        "PS2" to 0.51f, "PS1" to 0.52f, "Switch" to 0.53f, "Dreamcast" to 0.56f, "PICO8" to 0.56f, "PS3" to 0.59f,
        "PSVita" to 0.61f, "Lynx" to 0.62f, "PS4" to 0.62f, "Xbox360" to 0.63f, "PCEngineCD" to 0.64f,
        "PokemonMini" to 0.65f, "Saturn" to 0.65f, "PCEngine" to 0.66f, "FDS" to 0.69f, "SegaCD" to 0.71f,
        "Vectrex" to 0.71f, "Jaguar" to 0.72f, "3DS" to 0.85f, "NDS" to 0.85f
    )

    /** Batch 523: [platform]'s wordmark lettering (see [LETTERING]). */
    fun lettering(platform: String): Float = LETTERING[WORDMARK_ALIAS[platform] ?: platform] ?: 1f

    /**
     * Batch 523: the ES-DE logos the Logo look falls back to (consoles without a wordmark in the set): width ÷ height and
     * lettering, measured like [LETTERING]. One not listed has no measured form and shows the name.
     */
    private val ESDE_FORM = mapOf(
        "tg16" to (5.47f to 0.86f), "tg-cd" to (5.47f to 0.86f), "ngpc" to (2.68f to 0.23f),
        "wonderswancolor" to (5.13f to 0.41f), "c64" to (3.26f to 0.45f), "amiga" to (3.06f to 0.54f)
    )

    /** Batch 523: (aspect, lettering) of the ES-DE logo at [url] (file:///android_asset/system_logos/<name>.svg), or null. */
    fun esdeForm(url: String): Pair<Float, Float>? = ESDE_FORM[url.substringAfterLast('/').removeSuffix(".svg")]
}
