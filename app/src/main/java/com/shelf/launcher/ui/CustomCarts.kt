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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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

    /**
     * Update 306: the real cartridge a pack shape is drawn after (the one whose connector end it already gets,
     * CartUndersides.packFit) — the N64 shape → the N64 Game Pak, the Game Boy shape → the Game Boy Game Pak… — so a
     * game on your pack turns with that cartridge's thickness and back. Null for the PS Vita and HuCard shapes (flat cards
     * with no researched back to borrow).
     */
    fun authenticShell(look: CartLook): CartShell? = CartUndersides.packFit(look)?.shellId?.let { CartShells.of(it) }

    /**
     * Update 306: the real cartridge behind a custom piece — your pack's cartridge ([CUSTOM_CART] + console, not an
     * optical-system shell) or a pack shape picked for the game ([SHELL_PREFIX] "pack:…") — and the game's console; null
     * for anything else.
     */
    fun authenticForPick(pickPlatform: String): Pair<CartShell, String?>? = when {
        pickPlatform.startsWith(CUSTOM_CART) -> {
            val console = pickPlatform.removePrefix(CUSTOM_CART)
            if (discShellFor(console) != null) null else authenticShell(lookFor(console))?.let { it to console }
        }
        pickPlatform.startsWith(SHELL_PREFIX) -> {
            val (choice, console) = CartShells.split(pickPlatform)
            choice?.takeIf { it.shell == null }?.pack?.let { authenticShell(it) }?.let { it to console }
        }
        else -> null
    }

    /** Update 306: a custom piece's thickness from its real cartridge (against its longest side), or null. */
    fun depthForPick(pickPlatform: String): Float? = authenticForPick(pickPlatform)?.first?.depth?.coerceIn(0.02f, 0.4f)

    /**
     * Update 306: [real]'s researched back in the pack picture's plastic [tone] ([clear]: its see-through edition) — with
     * the game's [console] named in place of the maker's where the cartridge isn't that console's own
     * (CartShells.backFor, as Settings › Game look › Cartridge backs says).
     */
    fun authenticBack(real: CartShell, tone: Color, clear: Boolean, console: String?, style: Features.CartBackStyle, px: Int): android.graphics.Bitmap? = runCatching {
        val argb = tone.copy(alpha = 1f).toArgb()
        val colour = ShellColour("pack:$argb${if (clear) "c" else ""}", "Pack", argb, if (clear) 0.6f else 1f)
        CartShells.backFor(real, colour, px, console, style)
    }.getOrNull()

    /**
     * Update 307: where each pack shape's console plaque sits in its pictures — the plate inside its rim, as left, top,
     * right, bottom shares of the picture. Measured on the pack's pictures (PS2, N64, Game Boy, SNES parts): the same
     * place for every console and both finishes. The PS Vita and HuCard shapes have no real cartridge to go on.
     */
    private val PLAQUE: Map<CartLook, FloatArray> = mapOf(
        CartLook.N64 to floatArrayOf(0.2669f, 0.0995f, 0.7331f, 0.2195f),
        CartLook.GB to floatArrayOf(0.1831f, 0.1114f, 0.8169f, 0.2370f),
        CartLook.SNES to floatArrayOf(0.2056f, 0.0819f, 0.7950f, 0.2038f),
        CartLook.GBA to floatArrayOf(0.1544f, 0.1036f, 0.8450f, 0.2636f),
        CartLook.NES to floatArrayOf(0.2850f, 0.0511f, 0.7262f, 0.1488f),
        CartLook.FAMICOM to floatArrayOf(0.0994f, 0.0816f, 0.9006f, 0.2280f),
        CartLook.GENESIS to floatArrayOf(0.1462f, 0.0864f, 0.8538f, 0.2269f),
        CartLook.GAME_GEAR to floatArrayOf(0.1138f, 0.0784f, 0.8862f, 0.2151f),
        CartLook.NDS to floatArrayOf(0.1131f, 0.0775f, 0.8869f, 0.2158f),
        CartLook.N3DS to floatArrayOf(0.1019f, 0.0777f, 0.8500f, 0.2158f),
        CartLook.SWITCH to floatArrayOf(0.1375f, 0.0945f, 0.8625f, 0.2496f),
        CartLook.ATARI to floatArrayOf(0.0875f, 0.0933f, 0.9125f, 0.2260f)
    )

    private val plaques = android.util.LruCache<String, android.graphics.Bitmap>(32)
    private val fronts = object : android.util.LruCache<String, android.graphics.Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }

    /**
     * Update 307: the console's plaque from your pack picture [file] ([look]'s place, [PLAQUE]) — the plate with its logo,
     * its rounded ends filled with the plate's own colour so it prints as a band. Read once per picture (blocking).
     */
    fun plaque(file: java.io.File, look: CartLook): android.graphics.Bitmap? {
        val r = PLAQUE[look] ?: return null
        val key = "${file.path}|${file.lastModified()}"
        plaques.get(key)?.let { return it }
        return runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
            val full = android.graphics.BitmapFactory.decodeFile(file.path, opts) ?: return@runCatching null
            val bw = full.width; val bh = full.height
            var top = r[1] * bh; var bottom = r[3] * bh
            val ph = bottom - top
            val left = r[0] * bw + ph * 0.04f; val right = r[2] * bw - ph * 0.04f
            top += ph * 0.05f; bottom -= ph * 0.05f
            val x0 = left.toInt().coerceIn(0, bw - 2); val y0 = top.toInt().coerceIn(0, bh - 2)
            val w = (right.toInt() - x0).coerceIn(2, bw - x0); val h = (bottom.toInt() - y0).coerceIn(2, bh - y0)
            val px = IntArray(w * h)
            full.getPixels(px, 0, w, x0, y0, w, h)
            full.recycle()
            // The plate's colour: the middle of its top and bottom rows (channel by channel).
            val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
            for (x in w / 5 until w * 4 / 5) for (y in intArrayOf(1, h - 2)) {
                val c = px[y * w + x]; rs.add((c shr 16) and 0xFF); gs.add((c shr 8) and 0xFF); bs.add(c and 0xFF)
            }
            rs.sort(); gs.sort(); bs.sort()
            val plate = (0xFF shl 24) or (rs[rs.size / 2] shl 16) or (gs[gs.size / 2] shl 8) or bs[bs.size / 2]
            // The plate's round ends (the shell's plastic showed in its corners) and anything see-through: the plate.
            val rad = h * 0.3f
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if ((px[i] ushr 24) < 250) { px[i] = plate; continue }
                val cx = when { x < rad -> rad; x > w - 1 - rad -> w - 1 - rad; else -> -1f }
                val cy = when { y < rad -> rad; y > h - 1 - rad -> h - 1 - rad; else -> -1f }
                if (cx >= 0f && cy >= 0f) {
                    val dx = x - cx; val dy = y - cy
                    if (dx * dx + dy * dy > rad * rad * 0.8f) px[i] = plate
                }
            }
            android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }.getOrNull()?.also { plaques.put(key, it) }
    }

    /**
     * Update 307 (Toni: "reuse the complete authentic cartridge geometry, especially N64, including the front"): a game on
     * your pack's [look] drawn on its real cartridge [real] front — the researched outline, panel lines, label recess and
     * sticker shape (CartShells.front) — in your pack's plastic [tone] ([clear]: see-through), the console's plaque from
     * the pack picture [file] printed across the sticker's top and the game's [art] under it (a box front's console banner
     * cut off and the art filling the sticker, as in the pack's window). A real label [scan] is the whole sticker, as on
     * the drawn shells. Cached; blocking the first time a pack picture is read.
     */
    fun authenticFront(
        real: CartShell, look: CartLook, file: java.io.File?, tone: Color, clear: Boolean, platform: String,
        art: android.graphics.Bitmap?, scan: Boolean, title: String?, px: Int,
        /** Update 314: only one already made (null when it isn't; nothing is read or drawn). */
        peek: Boolean = false
    ): android.graphics.Bitmap? = runCatching {
        val argb = tone.copy(alpha = 1f).toArgb()
        val key = "${real.id}|$argb|$clear|$platform|${file?.path}|${art?.let { System.identityHashCode(it) } ?: 0}|$scan|${title ?: ""}|$px"
        fronts.get(key)?.let { return@runCatching it }
        if (peek) return@runCatching null
        val colour = ShellColour("pack:$argb${if (clear) "c" else ""}", "Pack", argb, if (clear) 0.6f else 1f)
        val head = if (scan) null else file?.let { plaque(it, look) }
        val pic = if (art != null && !scan) {
            val lw = real.label[2]; val lh = real.label[3]
            val hh = head?.let { minOf(lw * it.height / it.width.coerceAtLeast(1), lh * 0.4f) } ?: 0f
            CartShells.packSticker(platform, art, lw / (lh - hh).coerceAtLeast(1f))
        } else art
        CartShells.front(real, colour, pic, com.shelf.launcher.data.ConsoleNames.display(platform), title, px, scan, header = head)
            .also { fronts.put(key, it) }
    }.getOrNull()

    /** Update 131: every console key → pack id (the downloads, data/Packs.kt). */
    fun allIds(): Map<String, String> = ids

    /**
     * The shell picture for console [id] in [look] ([clear]: the translucent edition). Update 131: from the downloaded
     * pack (Settings › Game look › Extra art packs); null while that console's part isn't on the device.
     */
    fun asset(look: CartLook, clear: Boolean, id: String): java.io.File? = com.shelf.launcher.data.Packs.cartFile(id, look.key, clear)

    /** Width / height of what [platform]'s game is drawn on: its disc-console shell, else the cartridge shape picked. */
    // (Update 239, #33: an app's / Android game's own shape — lookFor.)
    fun aspectFor(platform: String): Float = discShellFor(platform)?.aspect ?: lookFor(platform).aspect

    /**
     * Update 239 (#33): the pack shape [platform]'s games are drawn on when no shape was picked for the game or its console
     * (the Cartridge shell picker passes its own): the shape picked for all (R1 › Cartridge shape) — but apps and Android
     * games on the pack's Switch-card shape, once their part of the pack is on the device (every console's part has all
     * 14 shapes). An app's icon or an Android game's cover is square or portrait: in an N64 / SNES / Genesis shape's wide
     * window it came out a small picture in a band of label paper; the Switch card's window is about square. Every
     * console with a cartridge of its own keeps the shape picked (no real cartridge is resized).
     */
    fun lookFor(platform: String): CartLook {
        val all = Features.cartLook.state.value
        if (all == CartLook.SWITCH || !AndroidMedia.isAndroid(platform)) return all
        val id = idFor(platform) ?: return all
        return if (com.shelf.launcher.data.Packs.hasCart(id)) CartLook.SWITCH else all
    }
}

/**
 * Update 239 (#34): a cover as the sticker in your pack's window, [winAspect] wide for 1 tall — CartShells.packSticker
 * (its console banner cut off, fitted at its own shape, the rest the colour along its edge).
 */
internal class PackSticker(private val platform: String, private val winAspect: Float) : coil.transform.Transformation {
    override val cacheKey: String = "voltcapPackSticker(2)|$platform|${(winAspect * 1000f).toInt()}"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap =
        CartShells.packSticker(platform, input, winAspect)
}

/**
 * Update 239 (#34): [art] for your pack's window ([winAspect]) — a picture (URL, file, bitmap) with [PackSticker]; other
 * models (and none) as they are. An animated cover stays animated and uncut.
 */
@Composable
private fun rememberPackLabel(art: Any?, platform: String, winAspect: Float): Any? {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(art, platform, winAspect) {
        when (art) {
            is String, is java.io.File, is android.net.Uri, is android.graphics.Bitmap ->
                coil.request.ImageRequest.Builder(context).data(art).size(720, 720)
                    .scale(coil.size.Scale.FIT).precision(coil.size.Precision.INEXACT)
                    .transformations(TrimUniformMargins, PackSticker(platform, winAspect))
                    .allowConversionToBitmap(false)
                    // (Update 317: always still on a cartridge / disc — see mediaArtModel.)
                    .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true, memoryCacheKey = null)
                    .build()
            else -> art
        }
    }
}

/**
 * Update 131, Cartridges look with Original: a disc console's game on its own shell from your optical-system pack
 * (downloaded), instead of a cartridge of the pack — when that shell is on the device. With Custom the cartridge pack
 * wins wherever it has the console.
 */
internal fun discShellFor(platform: String): DiscShells.Shell? {
    // (Update 279, testing only: TestHooks `voltcap_test_shell opt:<file>` — that optical shell for every game.)
    TestHooks.shell.value?.takeIf { it.startsWith("opt:") }?.let { t ->
        return DiscShells.byFile(t.removePrefix("opt:"))?.takeIf { com.shelf.launcher.data.Packs.discFile(it.file) != null }
    }
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
internal fun CustomCart(
    platform: String, art: Any?, modifier: Modifier,
    /** Update 316: the solid build and the motion seed, applied in [PackCart] (see there); null: none. */
    solid: SolidSpec? = null, motionSeed: Int? = null
) {
    // Update 131: a disc console on its own shell (your optical-system pack).
    val shell = discShellFor(platform)
    if (shell != null) {
        // (Update 317: the hop round the solid piece — see PhysicalMedia.)
        val moving = if (motionSeed != null) modifier.mediaMotion(false, motionSeed) else modifier
        DiscShellArt(shell, art, if (solid != null) moving.solidPiece(solid) else moving, platform)
    } else PackCart(platform, art, modifier, solid = solid, motionSeed = motionSeed)
}

/**
 * The game on the cartridge pack's shell (see [CustomCart]). Update 195: [look0] / [clear0] for a shape picked for this
 * game or console (the Cartridge shell picker), else the pack's shape picked for all.
 */
@Composable
internal fun PackCart(
    platform: String, art: Any?, modifier: Modifier, look0: CartLook? = null, clear0: Boolean? = null,
    /** Update 307: [art] is the game's real label scan (the whole sticker) — on a shape drawn on its real cartridge. */
    labelIsScan: Boolean = false, title: String? = null,
    /**
     * Update 316: the piece's solid build (it may be turned) and its idle motion's seed, applied here so a shape drawn as
     * its real cartridge turns from its finished front picture (solidPiece's faceArt); null: neither.
     */
    solid: SolidSpec? = null, motionSeed: Int? = null
) {
    val id = CustomCarts.idFor(platform)
    // (Update 239, #33: apps / Android games on the Switch-card shape unless a shape was picked for them — lookFor.)
    val look = look0 ?: CustomCarts.lookFor(platform)
    // (Update 205: clear plastic only with Cartridges & discs › Cartridge › Clear plastic, or the flat look from before
    // that set it. With Real shell for each console picked, the Android / Steam / PC games on the pack's shape stood
    // see-through — the clear switch stayed on from an earlier pick.)
    val finish = Features.modelFinish.state.value
    val clear = clear0 ?: (Features.cartClear.state.value && (finish == ModelFinish.CLEAR || finish == ModelFinish.OFF))
    // Update 307: a shape modelled on a real cartridge is drawn as that cartridge's front (its own outline, panels, label
    // recess and sticker), in your pack's plastic and with its console plaque — AuthenticPackCart. The PS Vita and HuCard
    // shapes (no real cartridge) keep the pack picture.
    val real = androidx.compose.runtime.remember(look) { CustomCarts.authenticShell(look) }
    val fit = androidx.compose.runtime.remember(look) { CartUndersides.packFit(look) }
    if (real != null && fit != null && id != null) {
        AuthenticPackCart(platform, id, art, modifier, look, clear, real, fit.frame, labelIsScan, title, solid, motionSeed)
        return
    }
    // (Update 316: the pack picture's shapes — two pictures, not one — turn as before: the solid piece round them.)
    // (Update 317: the hop round the solid piece — see PhysicalMedia.)
    val packMoving = if (motionSeed != null) modifier.mediaMotion(false, motionSeed) else modifier
    BoxWithConstraints(if (solid != null) packMoving.solidPiece(solid) else packMoving, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * look.aspect)
        val h = w / look.aspect
        // Dp per unit of the pack's own measurements.
        val k = w / look.w
        val win = look.win
        // (Update 239, #34: the cover as one printed sticker for this window — its console banner cut off, never
        // stretched, the rest in the colour along its edge: rememberPackLabel.)
        val labelModel = rememberPackLabel(art, platform, win[2] / win[3])
        Box(Modifier.size(w, h)) {
            Box(
                Modifier.offset(x = k * win[0], y = k * win[1]).size(k * win[2], k * win[3])
                    .clip(RoundedCornerShape(k * win[4])).background(CartPaper)
            ) {
                // (Update 206: stretched to the label window exactly — the art adapts to the pack's label, never cut.)
                // (Update 215: never stretched — the cover at its own shape, as big as fits in the window, on the label's
                // paper; stretched to the window it came out wide or tall.)
                if (art != null) AsyncImage(if (labelModel !== art) labelModel else mediaArtModel(art), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            val file = id?.let { CustomCarts.asset(look, clear, it) }
            // (Update 141: the clear shell a little more see-through.)
            if (file != null) AsyncImage(
                rememberShellRequest(file), null, contentScale = ContentScale.FillBounds, alpha = if (clear) 0.82f else 1f, modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Update 307: [PackCart] on the real cartridge [real]: its front (CustomCarts.authenticFront) where the real shell sits in
 * the pack picture ([frame], CartUndersides.packFit — the same box its connector end and back are fitted to), so the piece
 * keeps the pack shape's size, its turn, sides and back line up, and the layouts don't move.
 */
@Composable
private fun AuthenticPackCart(
    platform: String, id: String, art: Any?, modifier: Modifier, look: CartLook, clear: Boolean,
    real: CartShell, frame: FloatArray, scan: Boolean, title: String?,
    solid: SolidSpec? = null, motionSeed: Int? = null
) {
    val file = CustomCarts.asset(look, clear, id)
    val pick = "${SHELL_PREFIX}pack:${look.key}~${if (clear) "clear" else "opaque"}|$platform"
    val bmpArt = rememberSoftBitmap(art, 480)
    // Update 316 (scrolling your pack's cartridges stuttered): the pack picture's plastic read away from the screen's
    // thread (rememberCustomPieceTone; it was read from the file here), and the front — 640 px, the plaque and the label —
    // used at once when it's made, else drawn away from the screen's thread, the one before staying meanwhile.
    val tone = rememberCustomPieceTone(pick) ?: Color(0xFF7C7D82)
    val identity = "$pick|${file?.path}|$scan|$title"
    val madeFront = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String, androidx.compose.ui.graphics.ImageBitmap>?>(null) }
    val prior = madeFront.value
    val cachedFront = androidx.compose.runtime.remember(file?.path, bmpArt, clear, platform, scan, title, tone) {
        runCatching {
            CustomCarts.authenticFront(real, look, file, tone, clear, platform, bmpArt, scan, title, 640, peek = true)
                // (A place that changes to another game draws its own here, as before — never the old game's for a moment.)
                ?: if (prior != null && prior.first != identity) CustomCarts.authenticFront(real, look, file, tone, clear, platform, bmpArt, scan, title, 640) else null
        }.getOrNull()?.asImageBitmap()
    }
    androidx.compose.runtime.LaunchedEffect(file?.path, bmpArt, clear, platform, scan, title, tone) {
        if (cachedFront != null) { madeFront.value = identity to cachedFront; return@LaunchedEffect }
        val made = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { CustomCarts.authenticFront(real, look, file, tone, clear, platform, bmpArt, scan, title, 640)?.asImageBitmap() }.getOrNull()
        }
        if (made != null) madeFront.value = identity to made
    }
    val front = cachedFront ?: prior?.takeIf { it.first == identity }?.second
    // (Update 316: the motion round the solid piece, and the turned piece drawn from the front itself — PieceFace — as the
    // drawn shells are: its sides and face made once, not every frame.)
    val moving = if (motionSeed != null) modifier.mediaMotion(false, motionSeed) else modifier
    // (Update 316: the same PieceFace while the front is the same — a new one rebuilt the piece's drawing.)
    val pieceFace = androidx.compose.runtime.remember(front, look.aspect, frame) { front?.let { PieceFace(it, look.aspect, frame) } }
    val shaped = if (solid != null) moving.solidPiece(solid, faceArt = pieceFace) else moving
    BoxWithConstraints(shaped, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * look.aspect)
        val h = w / look.aspect
        Box(Modifier.size(w, h)) {
            if (front != null) androidx.compose.foundation.Image(
                front, real.name, contentScale = ContentScale.FillBounds,
                modifier = Modifier.offset(x = w * frame[0], y = h * frame[1]).size(w * (frame[2] - frame[0]), h * (frame[3] - frame[1]))
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

    /** Update 279 (testing): a shell by its picture's name. */
    fun byFile(file: String): Shell? = shells[file]

    /** The pack's pictures and the consoles each one is for (the downloads, data/Packs.kt). */
    fun packFiles(): Map<String, Set<String>> =
        shells.keys.associateWith { f -> byConsole.filterValues { it == f }.keys }
}

/** [art] (the game's cover) in [shell]'s window, the shell over it, keeping the shell's proportions inside [modifier]. */
@Composable
private fun DiscShellArt(shell: DiscShells.Shell, art: Any?, modifier: Modifier, platform: String = "") {
    val file = com.shelf.launcher.data.Packs.discFile(shell.file)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * shell.aspect)
        val h = w / shell.aspect
        val win = shell.win
        // (Update 239, #34: the window's shape — its share of the width and height of a picture [aspect] wide for 1 tall.)
        val labelModel = rememberPackLabel(art, platform, shell.aspect * win[2] / win[3].coerceAtLeast(0.01f))
        Box(Modifier.size(w, h)) {
            Box(
                Modifier.offset(x = w * win[0], y = h * win[1]).size(w * win[2], h * win[3])
                    .clip(RoundedCornerShape(w * shell.corner)).background(CartPaper)
            ) {
                // (Update 239, #34: fitted as one printed sticker — rememberPackLabel — instead of cut to the window: a PS2
                // game's portrait cover on the near-square PS2 shell lost its top and bottom, its title and the PS2 header
                // with them, and still wore the header's edge.)
                if (art != null) AsyncImage(if (labelModel !== art) labelModel else mediaArtModel(art), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (file != null) AsyncImage(rememberShellRequest(file), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}


/**
 * Update 159: a pack's cartridge / disc shell picture, decoded once at one size (560 px, the pack's own width) under one
 * key, whatever size it is drawn at. Each slot size (the middle game, the ones aside, another layout) decoded it again
 * from storage, so a game on your pack (the PS2 game in the Cartridges look) went blank for a second while scrolling.
 */
@androidx.compose.runtime.Composable
private fun rememberShellRequest(file: java.io.File): coil.request.ImageRequest {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(file.path) {
        coil.request.ImageRequest.Builder(context).data(file).size(560, 560)
            .scale(coil.size.Scale.FIT).precision(coil.size.Precision.INEXACT)
            .memoryCacheKey("shell|${file.path}|${file.lastModified()}").build()
    }
}

/**
 * (Update 241, #45/#46: the back of one of Voltcap's own platform cartridges — your pack's cartridge for a console, or its
 * optical-system shell (PlayStation, PlayStation 2, GameCube, Dreamcast…) — in its own plastic with that console's logo
 * moulded into it: the app's baked logo (PlatformLogos, the redrawn set already shipped with its credits) as a raised
 * relief in the plastic's own colour, only its shape — never its printed colours — so it stays in relief and readable
 * whatever colour the cartridge is; the console's name in moulded letters where there's no logo. A moulded seam runs round
 * the back. Only for these custom cartridges (CUSTOM_CART / a pack shape picked in the shell picker): never on a real
 * game's own cartridge, scan or disc, and never with SaveVault branding (that's the Save Vault objects', Engraving.kt).
 * Made mirrored, as every drawn back is (seen from behind once turned it reads the right way round), the size of the
 * cartridge's own box: SolidSpec(back = …, fitBackToFace = true) cuts it to the shell's outline. Cached per console,
 * colour and size — made once, never per frame.)
 */
internal object CustomCartBacks {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }

    /** Whether [console] (a Voltcap console key) has a custom cartridge of its own in your pack — the only ones engraved. */
    fun applies(console: String): Boolean = CustomCarts.idFor(console) != null

    /**
     * The back for [console]'s custom cartridge, [aspect] wide for 1 tall, [px] on its longest side, in [tone] (its plastic,
     * opaque ARGB — rememberCustomPieceTone). Null for a console without a custom cartridge.
     */
    fun back(context: android.content.Context, console: String, tone: Int, aspect: Float, px: Int = 480): android.graphics.Bitmap? = runCatching {
        if (!applies(console)) return@runCatching null
        val a = aspect.coerceIn(0.2f, 5f)
        val w = (if (a >= 1f) px.toFloat() else px * a).toInt().coerceAtLeast(16)
        val h = (if (a >= 1f) px / a else px.toFloat()).toInt().coerceAtLeast(16)
        val shell = tone or 0xFF000000.toInt()
        val key = "$console|$shell|$w|$h"
        cache.get(key)?.let { return@runCatching it }
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(shell)
        val wf = w.toFloat(); val hf = h.toFloat()
        val sheen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, hf, intArrayOf(0x1FFFFFFF, 0x00000000, 0x2E000000), floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, wf, hf, sheen)
        // The moulded seam: a dark groove with its lit lip just inside it.
        val m = minOf(wf, hf)
        val inset = m * 0.07f; val r = m * 0.05f
        val sw = maxOf(1.5f, m * 0.008f)
        val groove = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeWidth = sw; color = 0x40000000
        }
        c.drawRoundRect(android.graphics.RectF(inset, inset, wf - inset, hf - inset), r, r, groove)
        groove.color = 0x2EFFFFFF
        c.drawRoundRect(android.graphics.RectF(inset + sw, inset + sw, wf - inset + sw, hf - inset + sw), r, r, groove)
        // The console's logo, raised from the plastic, a little above the middle (mirrored, see above).
        Engraving.platformLogo(context, console, shell, (wf * 0.58f).toInt(), (hf * 0.30f).toInt(), raised = true)?.let { logo ->
            c.save()
            c.scale(-1f, 1f, wf / 2f, 0f)
            c.drawBitmap(logo, (wf - logo.width) / 2f, hf * 0.42f - logo.height / 2f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        cache.put(key, out)
        out
    }.getOrNull()

    /** [back] as an ImageBitmap, for a SolidSpec's back. */
    fun backImage(context: android.content.Context, console: String, tone: Int, aspect: Float, px: Int = 480): androidx.compose.ui.graphics.ImageBitmap? =
        back(context, console, tone, aspect, px)?.asImageBitmap()
}

/**
 * (Update 241, #45: [cartSolid] for a game on your pack's cartridge ([pickPlatform] = CUSTOM_CART + console) with its back
 * engraved — CustomCartBacks, cut to the shell's own outline — instead of a plain silhouette in its plastic. A drop-in
 * for PhysicalMedia's CUSTOM_CART branch: `cartSolid(null, platform, aspect, custom = true, tone = packTone)` →
 * `rememberCustomCartSolid(platform, aspect, packTone)`. The back is made the first time it's seen from behind.)
 */
@Composable
internal fun rememberCustomCartSolid(pickPlatform: String, aspect: Float?, tone: Color?): SolidSpec {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backStyle = Features.cartBackStyle.state.value
    return androidx.compose.runtime.remember(pickPlatform, aspect, tone, backStyle) {
        val base = cartSolid(null, pickPlatform, aspect, custom = true, tone = tone)
        val console = pickPlatform.removePrefix(CUSTOM_CART)
        // (Update 306: a pack shape modelled on a real cartridge turns with that cartridge's thickness and researched back,
        // this game's console named on it — CustomCarts.authenticForPick; the others keep the engraved back below.)
        val real = CustomCarts.authenticForPick(pickPlatform)?.first
        if (real != null) SolidSpec(
            base.aspect, real.depth.coerceIn(0.02f, 0.4f), base.tone,
            back = { CustomCarts.authenticBack(real, base.tone, Features.cartClear.state.value, console, backStyle, 480)?.asImageBitmap() },
            fitBackToFace = true
        )
        else if (!CustomCartBacks.applies(console)) base
        else SolidSpec(
            base.aspect, base.depth, base.tone,
            back = { CustomCartBacks.backImage(context, console, base.tone.toArgb(), base.aspect ?: CustomCarts.aspectFor(console)) },
            fitBackToFace = true
        )
    }
}
