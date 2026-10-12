package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features

/*
 * Update 128: the Cartridges look's own cartridge pack (your designs). Update 131: downloaded at full size (data/Packs.kt,
 * files/packs/carts/<shape>/<o|t>/<id>.webp, lossless; credits in the download's carts_CREDITS.txt), no longer in the app.
 * 14 shapes × 2 finishes (opaque, clear) × 64 consoles. Each picture is the whole shell, 1600 px wide, with the console's
 * logo on its plaque and a see-through window: the game's cover is drawn behind the window and the shell on top.
 * More consoles can be added later as a download into the same folder layout (carts/<shape>/<o|t>/<id>.webp).
 */

/** One of the pack's cartridge shapes: its folder, its name, and its size and window in the pack's own units. */
enum class CartLook(val key: String, val label: String, val w: Float, val h: Float, val win: FloatArray) {
    GB("gb", "Game Boy", 610f, 695f, floatArrayOf(95f, 215f, 420f, 370f, 15f)),
    N64("n64", "Nintendo 64", 1200f, 806f, floatArrayOf(327.5f, 216f, 545f, 510f, 30f)),
    SNES("snes", "Super NES", 1398.5f, 917f, floatArrayOf(286.75f, 247f, 825f, 370f, 30f)),
    GBA("gba", "Game Boy Advance", 640f, 390f, floatArrayOf(105f, 129f, 430f, 199f, 14f)),
    NES("nes", "NES", 1240f, 1380f, floatArrayOf(352f, 255f, 550f, 900f, 18f)),
    FAMICOM("famicom", "Famicom", 1120f, 755f, floatArrayOf(110f, 213f, 900f, 440f, 15f)),
    GENESIS("genesis", "Genesis", 1040f, 745f, floatArrayOf(150f, 211f, 740f, 408f, 18f)),
    GAME_GEAR("gamegear", "Game Gear", 700f, 720f, floatArrayOf(75f, 194f, 550f, 390f, 48f)),
    NDS("nds", "Nintendo DS", 700f, 740f, floatArrayOf(78f, 191f, 544f, 429f, 12f)),
    N3DS("n3ds", "Nintendo 3DS", 730f, 740f, floatArrayOf(73f, 191f, 549f, 429f, 12f)),
    SWITCH("switch", "Switch", 460f, 660f, floatArrayOf(62f, 190f, 336f, 341f, 10f)),
    VITA("psvita", "PS Vita", 480f, 640f, floatArrayOf(66f, 181f, 348f, 341f, 10f)),
    ATARI("atari2600", "Atari 2600", 860f, 1020f, floatArrayOf(74f, 277f, 712f, 554f, 12f)),
    HUCARD("hucard", "HuCard", 570f, 880f, floatArrayOf(58f, 185f, 454f, 480f, 13f));

    /** width / height of the picture */
    val aspect: Float get() = w / h
}

/** Update 128: which cartridges the Cartridges look draws (Settings › Game look, R1 › Cartridges). */
enum class CartSource(val label: String) {
    /** Each console's own cartridge; your pack only for consoles without one (discs, UMDs, Steam, Android, PC, arcade…). */
    ORIGINAL("Original"),
    /** Every console on your pack. */
    CUSTOM("Custom")
}

/** The platform key [mediaFor] hands out for a game on the pack: this + the game's console. */
internal const val CUSTOM_CART = "~cc:"

internal object CustomCarts {
    /** Voltcap's console keys → the pack's console ids (ES-DE system ids). */
    private val ids: Map<String, String> = mapOf(
        "PS1" to "psx", "PS2" to "ps2", "PS3" to "ps3", "PS4" to "ps4", "PSP" to "psp", "PSVita" to "psvita",
        "GameCube" to "gc", "Wii" to "wii", "WiiU" to "wiiu", "Switch" to "switch",
        "NES" to "nes", "SNES" to "snes", "N64" to "n64", "GB" to "gb", "GBC" to "gbc", "GBA" to "gba",
        "NDS" to "nds", "3DS" to "n3ds", "VirtualBoy" to "virtualboy", "FDS" to "fds", "Famicom" to "famicom", "SFC" to "sfc",
        "Genesis" to "genesis", "MegaDrive" to "megadrive", "MasterSystem" to "mastersystem", "SegaCD" to "segacd",
        "32X" to "sega32x", "Saturn" to "saturn", "Dreamcast" to "dreamcast", "GameGear" to "gamegear",
        "Naomi" to "naomi", "Atomiswave" to "atomiswave",
        "PCEngine" to "pcengine", "PCEngineCD" to "pcenginecd", "TG16" to "tg16", "TGCD" to "tg-cd",
        "NeoGeo" to "neogeo", "NeoGeoCD" to "neogeocd", "NeoGeoPocket" to "ngpc", "NeoGeoPocketColor" to "ngpc",
        "WonderSwan" to "wonderswancolor", "WonderSwanColor" to "wonderswancolor",
        "Atari2600" to "atari2600", "Atari7800" to "atari7800", "Lynx" to "atarilynx", "Jaguar" to "atarijaguar",
        "3DO" to "3do", "Xbox" to "xbox", "Xbox360" to "xbox360",
        "Arcade" to "arcade", "MAME" to "mame", "FBNeo" to "fbneo",
        "DOS" to "dos", "ScummVM" to "scummvm", "MSX" to "msx", "C64" to "c64", "Amiga" to "amiga",
        "PICO8" to "pico8", "Pico8" to "pico8", "PokemonMini" to "pokemini",
        "ColecoVision" to "colecovision", "Intellivision" to "intellivision", "Vectrex" to "vectrex",
        "Android" to "androidgames", "App" to "androidapps", "PC" to "pc", "Steam" to "steam"
    )

    /** The pack's id for [platform], or null where the pack has no cartridge for it. */
    fun idFor(platform: String): String? = ids[platform]

    /** Update 131: every console key → pack id (the downloads, data/Packs.kt). */
    fun allIds(): Map<String, String> = ids

    /**
     * The shell picture for console [id] in [look] ([clear]: the translucent edition). Update 131: from the downloaded
     * pack (Settings › Game look › Extra art packs); null while that console's part isn't on the device.
     */
    fun asset(look: CartLook, clear: Boolean, id: String): java.io.File? = com.shelf.launcher.data.Packs.cartFile(id, look.key, clear)

    /** Width / height of what [platform]'s game is drawn on: its disc-console shell, else the cartridge shape picked. */
    fun aspectFor(platform: String): Float = discShellFor(platform)?.aspect ?: Features.cartLook.state.value.aspect
}

/**
 * Update 131, Cartridges look with Original: a disc console's game on its own shell from your optical-system pack
 * (downloaded), instead of a cartridge of the pack — when that shell is on the device. With Custom the cartridge pack
 * wins wherever it has the console.
 */
internal fun discShellFor(platform: String): DiscShells.Shell? {
    if (!Features.cartsOnly.state.value) return null
    val shell = DiscShells.of(platform) ?: return null
    if (Features.cartSource.state.value == CartSource.CUSTOM && CustomCarts.idFor(platform)?.let { com.shelf.launcher.data.Packs.hasCart(it) } == true) return null
    val own = MediaShell.of(platform)
    val discLike = own == null || own.disc || own == MediaShell.UMD || platform == "Steam" || platform == "App" || platform == "Android"
    return shell.takeIf { discLike && com.shelf.launcher.data.Packs.discFile(it.file) != null }
}

/**
 * Update 128, Cartridges look: [platform]'s games are drawn on your pack —
 * - Custom: every console the pack has;
 * - Original: only consoles without a cartridge of their own (discs, UMDs, Steam, Android / apps, and consoles
 *   Voltcap has no media for, like PC and arcade); the rest keep their own cartridge.
 */
internal fun customCartFor(platform: String): Boolean {
    if (!Features.cartsOnly.state.value) return false
    // Update 131: a disc console with its own shell from your optical-system pack.
    if (discShellFor(platform) != null) return true
    val id = CustomCarts.idFor(platform) ?: return false
    // Update 131: only with that console's part of the pack downloaded (else its own cartridge / disc, as without it).
    if (!com.shelf.launcher.data.Packs.hasCart(id)) return false
    if (Features.cartSource.state.value == CartSource.CUSTOM) return true
    val own = MediaShell.of(platform) ?: return true
    return own.disc || own == MediaShell.UMD || platform == "Steam" || platform == "App" || platform == "Android"
}

/** The sticker's paper colour behind the window while the cover loads. */
private val CartPaper = Color(0xFFE9E6DF)

/**
 * The game on your pack's cartridge in the shape and finish picked (Features.cartLook / cartClear), keeping its
 * proportions inside [modifier]: [art] (its cover) in the window, the shell over it.
 */
@Composable
internal fun CustomCart(platform: String, art: Any?, modifier: Modifier) {
    // Update 131: a disc console on its own shell (your optical-system pack).
    val shell = discShellFor(platform)
    if (shell != null) DiscShellArt(shell, art, modifier) else PackCart(platform, art, modifier)
}

/** The game on the cartridge pack's shell (see [CustomCart]). */
@Composable
private fun PackCart(platform: String, art: Any?, modifier: Modifier) {
    val id = CustomCarts.idFor(platform)
    val look = Features.cartLook.state.value
    val clear = Features.cartClear.state.value
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * look.aspect)
        val h = w / look.aspect
        // Dp per unit of the pack's own measurements.
        val k = w / look.w
        val win = look.win
        Box(Modifier.size(w, h)) {
            Box(
                Modifier.offset(x = k * win[0], y = k * win[1]).size(k * win[2], k * win[3])
                    .clip(RoundedCornerShape(k * win[4])).background(CartPaper)
            ) {
                if (art != null) AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            val file = id?.let { CustomCarts.asset(look, clear, it) }
            // (Update 141: the clear shell a little more see-through.)
            if (file != null) AsyncImage(
                file, null, contentScale = ContentScale.FillBounds, alpha = if (clear) 0.82f else 1f, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Update 131: your optical-system pack (downloaded): a shell for each disc console with a see-through window. Sizes and
 * windows are measured from the pack's pictures (cropped to the shell, 560 px wide): window = left, top, width, height
 * as shares of the picture, and its corner as a share of the width.
 */
internal object DiscShells {
    class Shell(val file: String, val aspect: Float, val win: FloatArray, val corner: Float)

    private val shells: Map<String, Shell> = mapOf(
        "3do" to Shell("3do", 0.7507f, floatArrayOf(0.1349f, 0.2963f, 0.7315f, 0.5736f), 0.035f),
        "amiga_cd32" to Shell("amiga_cd32", 0.8195f, floatArrayOf(0.1295f, 0.3101f, 0.7410f, 0.5567f), 0.035f),
        "android" to Shell("android", 0.7691f, floatArrayOf(0.1277f, 0.2611f, 0.7511f, 0.6138f), 0.035f),
        "atari_jaguar_cd" to Shell("atari_jaguar_cd", 0.8058f, floatArrayOf(0.1454f, 0.2939f, 0.7091f, 0.5915f), 0.035f),
        "cd_i" to Shell("cd_i", 0.7745f, floatArrayOf(0.1330f, 0.2893f, 0.7352f, 0.5908f), 0.035f),
        "cdtv" to Shell("cdtv", 0.7655f, floatArrayOf(0.1304f, 0.2855f, 0.7381f, 0.5711f), 0.035f),
        "dreamcast" to Shell("dreamcast", 0.8336f, floatArrayOf(0.1431f, 0.3173f, 0.7138f, 0.5489f), 0.035f),
        "fds" to Shell("fds", 0.8096f, floatArrayOf(0.1437f, 0.3058f, 0.7126f, 0.5585f), 0.035f),
        "gamecube" to Shell("gamecube", 0.8186f, floatArrayOf(0.1495f, 0.3275f, 0.7022f, 0.5110f), 0.035f),
        "sega_cd" to Shell("sega_cd", 0.7941f, floatArrayOf(0.1287f, 0.2854f, 0.7425f, 0.5947f), 0.035f),
        "mega_cd" to Shell("mega_cd", 0.8057f, floatArrayOf(0.1517f, 0.2986f, 0.6956f, 0.5484f), 0.035f),
        "n64dd" to Shell("n64dd", 0.8096f, floatArrayOf(0.1437f, 0.3058f, 0.7126f, 0.5585f), 0.035f),
        "neo_geo_cd" to Shell("neo_geo_cd", 0.7535f, floatArrayOf(0.1252f, 0.2895f, 0.7520f, 0.5720f), 0.035f),
        "pc_engine_cd" to Shell("pc_engine_cd", 0.7666f, floatArrayOf(0.1282f, 0.2796f, 0.7436f, 0.5919f), 0.035f),
        "pc_fx" to Shell("pc_fx", 0.8245f, floatArrayOf(0.1196f, 0.2758f, 0.7620f, 0.5930f), 0.035f),
        "playstation" to Shell("playstation", 0.8096f, floatArrayOf(0.1437f, 0.3058f, 0.7126f, 0.5585f), 0.035f),
        "playstation_2" to Shell("playstation_2", 0.8104f, floatArrayOf(0.1376f, 0.3212f, 0.7259f, 0.5445f), 0.035f),
        "playstation_3" to Shell("playstation_3", 0.7760f, floatArrayOf(0.1130f, 0.2697f, 0.7939f, 0.6057f), 0.035f),
        "playstation_4" to Shell("playstation_4", 0.7696f, floatArrayOf(0.1321f, 0.2575f, 0.7370f, 0.5950f), 0.035f),
        "psp" to Shell("psp", 0.6622f, floatArrayOf(0.1715f, 0.2762f, 0.6272f, 0.5655f), 0.035f),
        "saturn" to Shell("saturn", 0.7915f, floatArrayOf(0.1381f, 0.2743f, 0.7238f, 0.5976f), 0.035f),
        "steam" to Shell("steam", 0.7747f, floatArrayOf(0.1202f, 0.2552f, 0.7806f, 0.6184f), 0.035f),
        "tg_cd" to Shell("tg_cd", 0.8118f, floatArrayOf(0.1418f, 0.3043f, 0.7141f, 0.5598f), 0.035f),
        "wii" to Shell("wii", 0.7627f, floatArrayOf(0.1265f, 0.2522f, 0.7385f, 0.6337f), 0.035f),
        "wii_u" to Shell("wii_u", 0.7859f, floatArrayOf(0.1290f, 0.2735f, 0.7612f, 0.6091f), 0.035f),
        "xbox" to Shell("xbox", 0.7975f, floatArrayOf(0.1256f, 0.2634f, 0.7477f, 0.6150f), 0.035f),
        "xbox_360" to Shell("xbox_360", 0.7644f, floatArrayOf(0.1385f, 0.2748f, 0.7301f, 0.6101f), 0.035f)
    )

    /** Voltcap console → the pack's picture. */
    private val byConsole: Map<String, String> = mapOf(
        "3DO" to "3do", "AmigaCD32" to "amiga_cd32", "Android" to "android", "App" to "android",
        "JaguarCD" to "atari_jaguar_cd", "CDi" to "cd_i", "CDTV" to "cdtv", "Dreamcast" to "dreamcast", "FDS" to "fds",
        "GameCube" to "gamecube", "SegaCD" to "sega_cd", "MegaCD" to "mega_cd", "N64DD" to "n64dd", "NeoGeoCD" to "neo_geo_cd",
        "PCEngineCD" to "pc_engine_cd", "PCFX" to "pc_fx", "PS1" to "playstation", "PS2" to "playstation_2",
        "PS3" to "playstation_3", "PS4" to "playstation_4", "PSP" to "psp", "Saturn" to "saturn", "Steam" to "steam",
        "TGCD" to "tg_cd", "Wii" to "wii", "WiiU" to "wii_u", "Xbox" to "xbox", "Xbox360" to "xbox_360"
    )

    fun of(platform: String): Shell? = byConsole[platform]?.let { shells[it] }

    /** The pack's pictures and the consoles each one is for (the downloads, data/Packs.kt). */
    fun packFiles(): Map<String, Set<String>> =
        shells.keys.associateWith { f -> byConsole.filterValues { it == f }.keys }
}

/** [art] (the game's cover) in [shell]'s window, the shell over it, keeping the shell's proportions inside [modifier]. */
@Composable
private fun DiscShellArt(shell: DiscShells.Shell, art: Any?, modifier: Modifier) {
    val file = com.shelf.launcher.data.Packs.discFile(shell.file)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * shell.aspect)
        val h = w / shell.aspect
        val win = shell.win
        Box(Modifier.size(w, h)) {
            Box(
                Modifier.offset(x = w * win[0], y = h * win[1]).size(w * win[2], h * win[3])
                    .clip(RoundedCornerShape(w * shell.corner)).background(CartPaper)
            ) {
                if (art != null) AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (file != null) AsyncImage(file, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}
