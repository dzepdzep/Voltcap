package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage
import com.shelf.launcher.R
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.CardArtType as Art

/*
 * The game as the real thing: each console's own cartridge, card, UMD (the disc seen through its round window) or disc, with the game's label (ScreenScraper's
 * label scan, else its cover) printed on it. The shells are pictures rendered like real plastic (bevels, a recessed
 * label pocket, grip grooves, light and shadow) by tools/media/shells.py; each has a see-through window where the
 * label shows from behind. Used by "Cartridge / disc" card art and the second screen.
 */

/** A console's game media: its picture, its proportions and where the label sits. */
internal enum class MediaShell(
    val res: Int,
    /** width / height of the picture */
    val aspect: Float,
    /** Cartridges: the label window (left, top, right, bottom fractions). Discs: (padding, label inner radius, label outer radius, -). */
    val label: FloatArray,
    /** The window's corner radius, as a fraction of the picture's width. */
    val corner: Float,
    val disc: Boolean = false
) {
    N64(R.drawable.media_n64, 416f / 288f, floatArrayOf(0.1587f, 0.1736f, 0.8413f, 0.5972f), 3f / 416f),
    SNES(R.drawable.media_snes, 356f / 292f, floatArrayOf(0.191f, 0.2055f, 0.809f, 0.589f), 3f / 356f),
    NES(R.drawable.media_nes, 296f / 336f, floatArrayOf(0.1757f, 0.1786f, 0.8243f, 0.5298f), 2f / 296f),
    GB(R.drawable.media_gb, 284f / 314f, floatArrayOf(0.1972f, 0.2484f, 0.8028f, 0.8089f), 3f / 284f),
    GBC(R.drawable.media_gbc, 284f / 314f, floatArrayOf(0.1972f, 0.2484f, 0.8028f, 0.8089f), 3f / 284f),
    VIRTUAL_BOY(R.drawable.media_vboy, 284f / 314f, floatArrayOf(0.1972f, 0.2484f, 0.8028f, 0.8089f), 3f / 284f),
    GBA(R.drawable.media_gba, 356f / 238f, floatArrayOf(0.1966f, 0.2857f, 0.8034f, 0.7647f), 3f / 356f),
    DS(R.drawable.media_ds, 306f / 292f, floatArrayOf(0.1699f, 0.1712f, 0.8301f, 0.7329f), 3f / 306f),
    DS3(R.drawable.media_ds3, 318f / 292f, floatArrayOf(0.1635f, 0.1712f, 0.7987f, 0.7329f), 3f / 318f),
    SWITCH(R.drawable.media_switch, 228f / 306f, floatArrayOf(0.193f, 0.1438f, 0.807f, 0.7451f), 3f / 228f),
    VITA(R.drawable.media_vita, 236f / 300f, floatArrayOf(0.1864f, 0.1467f, 0.8136f, 0.74f), 3f / 236f),
    CARD(R.drawable.media_small_card, 246f / 306f, floatArrayOf(0.1789f, 0.1438f, 0.8211f, 0.7451f), 3f / 246f),
    HUCARD(R.drawable.media_hucard, 216f / 308f, floatArrayOf(0.1759f, 0.1234f, 0.8241f, 0.7597f), 6f / 216f),
    BLACK_CART(R.drawable.media_black_cart, 318f / 348f, floatArrayOf(0.1761f, 0.1609f, 0.8239f, 0.6338f), 3f / 318f),
    GAME_GEAR(R.drawable.media_game_gear, 318f / 252f, floatArrayOf(0.1698f, 0.1984f, 0.8302f, 0.6587f), 3f / 318f),
    ATARI(R.drawable.media_atari, 318f / 318f, floatArrayOf(0.1635f, 0.4214f, 0.8365f, 0.8491f), 2f / 318f),
    // (Update 206: Sony's published UMD size, approx. 65 W × 64 D × 4.2 mm — it was drawn square.)
    UMD(R.drawable.media_umd, 65f / 64f, floatArrayOf(0.1603f, 0.1795f, 0.8397f, 0.859f), 0f),
    DISC(R.drawable.media_disc, 1f, floatArrayOf(0.0556f, 0.235f, 0.955f, 0f), 0f, disc = true),
    DISC_DARK(R.drawable.media_disc_dark, 1f, floatArrayOf(0.0556f, 0.235f, 0.955f, 0f), 0f, disc = true),
    DISC_WII(R.drawable.media_disc_white, 1f, floatArrayOf(0.0556f, 0.235f, 0.955f, 0f), 0f, disc = true),
    DISC_GD(R.drawable.media_disc_gd, 1f, floatArrayOf(0.0556f, 0.235f, 0.955f, 0f), 0f, disc = true),
    MINI_DISC(R.drawable.media_disc_mini, 1f, floatArrayOf(0.0556f, 0.3f, 0.95f, 0f), 0f, disc = true);

    val isDisc: Boolean get() = disc

    companion object {
        /** The media for a platform, or null where there is no physical game (apps, arcade, PC). */
        fun of(platform: String): MediaShell? = when (platform) {
            "PS1", "Saturn" -> DISC_DARK
            "Dreamcast" -> DISC_GD
            "Wii", "WiiU" -> DISC_WII
            "PS2", "PS3", "PS4", "SegaCD", "NeoGeoCD", "PCEngineCD", "3DO", "Xbox", "Xbox360", "Disc" -> DISC
            // Update 119: Steam games come on a disc too — the plain CD with the game's cover and Steam's band (GenericDisc).
            "Steam" -> DISC
            // Update 125: Android games (and apps) on Voltcap's own green cartridge (mediaFor, ANDROID_CART).
            "App", "Android" -> GB
            "GameCube" -> MINI_DISC
            "PSP" -> UMD
            "NDS" -> DS
            "3DS" -> DS3
            "GB" -> GB
            "GBC" -> GBC
            "VirtualBoy" -> VIRTUAL_BOY
            "GBA" -> GBA
            "SNES" -> SNES
            "N64" -> N64
            "NES", "Atari7800", "ColecoVision", "Intellivision", "Vectrex" -> NES
            "Atari2600" -> ATARI
            "Genesis", "MasterSystem", "32X", "NeoGeo", "Jaguar", "C64" -> BLACK_CART
            "GameGear" -> GAME_GEAR
            "Switch" -> SWITCH
            "PSVita" -> VITA
            "PCEngine" -> HUCARD
            "NeoGeoPocket", "WonderSwan", "Lynx", "PokemonMini" -> CARD
            else -> null
        }
    }
}

/** The media kinds the home layout on screen leaves out ("cartridge", "disc"; Appearance › Game row › Cards). */
internal val LocalHiddenMedia = androidx.compose.runtime.staticCompositionLocalOf<Set<String>> { emptySet() }

/** Update 113: every game's box / disc scans on the home screen (for the Spines look's own spines); set by the home screen. */
internal object HomeScans {
    private val whole = androidx.compose.runtime.mutableStateOf<Map<String, List<Pair<String, String>>>>(emptyMap())
    var map: Map<String, List<Pair<String, String>>>
        get() = whole.value
        set(v) {
            if (v === whole.value) return
            whole.value = v
            // (Update 208: only the games whose scans changed hear of it — see [of].)
            for ((k, s) in slots) {
                val n = v[k]
                if (s.scans.value !== n) s.scans.value = n
                val has = n != null || v.containsKey(k)
                if (s.known.value != has) s.known.value = has
            }
        }

    /**
     * Update 208: one game's scans, read so that only this game redraws when they change. Every 3D case read the whole
     * [map]: each scan looked up (they come in one by one while you scroll, or as a category opens) rebuilt every case on
     * the screen — the largest part of a sluggish row.
     */
    fun of(key: String): List<Pair<String, String>>? = slot(key).scans.value

    /** Update 208: [known], read as [of] is (only this game redraws when it changes). */
    fun knownHere(key: String): Boolean = slot(key).known.value

    private class Slot(
        val scans: androidx.compose.runtime.MutableState<List<Pair<String, String>>?>,
        val known: androidx.compose.runtime.MutableState<Boolean>
    )

    private val slots = HashMap<String, Slot>()

    private fun slot(key: String): Slot = slots.getOrPut(key) {
        val m = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { whole.value }
        Slot(androidx.compose.runtime.mutableStateOf(m[key]), androidx.compose.runtime.mutableStateOf(m.containsKey(key)))
    }
    /** Looks a game's scans up (its spine among them); set by the home screen. */
    var need: ((com.shelf.launcher.data.Game) -> Unit)? = null
    /** Update 119: a game's cover (by its key), for a game without any real spine; set by the home screen. */
    var cover: ((String) -> String?)? = null
    /** Update 159: each game's key by its cover's address (set by the home screen), so a 3D case finds the game's scans. */
    var keyOfCover: Map<String, String> = emptyMap()
    /** Update 205: these games were shown (a category's first games): their scans are saved with the rest of the art. */
    var seen: ((Collection<String>) -> Unit)? = null

    /**
     * Update 205: whether [key]'s scans are known (saved ones are all read at start): until then a drawn stand-in (Voltcap's
     * own spine, a plain back) would show first and the real one pop in after, so a neutral placeholder is drawn instead.
     */
    fun known(key: String): Boolean = map.containsKey(key)
}

/** Update 159: the Discs only look is on (set by the home screen), so cartridge games print their cover on their CD. */
internal object DiscLook {
    var on by androidx.compose.runtime.mutableStateOf(false)
}

/** "disc" for discs and UMDs, "cartridge" for everything else (cards, HuCards, carts). */
internal fun mediaKind(platform: String): String =
    MediaShell.of(platform)?.let { if (it.disc || it == MediaShell.UMD) "disc" else "cartridge" } ?: "cartridge"

/**
 * The art one game's card shows (what it is drawn as):
 * - "Box art" stays box art in lists mixing consoles too ([mixed], e.g. Continue playing), each case fitted in its slot;
 * - "Box art · real sizes" is box art (drawn at its real size, see [RealCaseRef]), mixed lists included;
 * - "Discs only": disc / UMD games as their disc, every other game its cover;
 * - "Cartridge / disc" falls back to the cover where that kind is switched off.
 */
internal fun artForGame(
    type: com.shelf.launcher.data.CardArtType, platform: String, hidden: Set<String>, mixed: Boolean = false,
    /** Update 212: the game's key, for an Android game's own Disc / Cartridge choice (AndroidMedia.kt). */
    gameKey: String? = null
): com.shelf.launcher.data.CardArtType {
    if (mixed) mixedArtFor(type, platform, hidden)?.let { return it }
    return artForGameOwn(type, platform, hidden, mixed, gameKey)
}

/**
 * Lists mixing consoles (Appearance › Game row › Mixed categories): by default every game is the basic cover / wide
 * capsule so nothing is bigger or smaller than its neighbour; or all covers, all wide capsules, all CDs, all cartridges.
 * Null = "as chosen above" (the old behaviour).
 */
private fun mixedArtFor(
    type: com.shelf.launcher.data.CardArtType, platform: String, hidden: Set<String>
): com.shelf.launcher.data.CardArtType? {
    return when (Features.mixedArt.state.value) {
        MixedArt.AS_CHOSEN -> null
        // Cartridges / discs (and real-size boxes) stay what you picked; only same-size box art becomes the plain cover
        // (update 84: a cartridges & discs look showed covers in every list with several consoles).
        // (Returning the type as it is skipped the per-game rules: "Discs only" then drew covers in Continue playing.)
        // (Update 87: box art too: picking "Box art" showed plain covers in Continue playing, unlike discs.)
        MixedArt.BASIC -> null
        MixedArt.COVER -> Art.COVER
        MixedArt.CAPSULE -> Art.CAPSULE
        MixedArt.DISC, MixedArt.CARTRIDGE -> {
            // Only games that have a physical console (apps and PC games keep their cover).
            val kind = if (Features.mixedArt.state.value == MixedArt.DISC) "disc" else "cartridge"
            // (Update 128: or a cartridge from your pack, Cartridges look.)
            // (Update 204: every game — apps, Steam, PC and arcade games get Voltcap's plain CD / cartridge, mediaFor;
            // they used to keep their cover.)
            if (kind in hidden) Art.COVER else Art.CARTRIDGE_DISC
        }
    }
}

private fun artForGameOwn(
    type: com.shelf.launcher.data.CardArtType, platform: String, hidden: Set<String>, mixed: Boolean, gameKey: String? = null
): com.shelf.launcher.data.CardArtType = when (type) {
    com.shelf.launcher.data.CardArtType.CASE -> type
    com.shelf.launcher.data.CardArtType.CASE_REAL -> com.shelf.launcher.data.CardArtType.CASE
    // Discs only: disc / UMD games as their disc; cartridge games as a plain CD with their label (Features.discsForAll),
    // else their cover. Drawn through CARTRIDGE_DISC with mediaFor(…, asDisc = true).
    com.shelf.launcher.data.CardArtType.DISC ->
        if (MediaShell.of(platform) != null && mediaKind(platform) == "disc") com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
        // (Update 212: an Android game set to Disc — this game, or Settings › Game look › Android games — is its disc.)
        else if (AndroidMedia.discPicked(platform, gameKey) && "disc" !in hidden) com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
        // (Update 129: or one console's disc picked for all on R1 › Disc.)
        // (Update 204: every game with Discs — apps, PC and arcade games too, on the plain CD; they kept their cover.)
        else if ((Features.discsForAll.state.value || LookStyles.disc() != null) && "disc" !in hidden) com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC
        else com.shelf.launcher.data.CardArtType.COVER
    // (Update 128: a game on your cartridge pack counts as a cartridge.)
    com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC ->
        // (Update 212: an Android game is the kind it's drawn as — AndroidMedia.)
        if (hidden.isNotEmpty() && (if (AndroidMedia.isAndroid(platform)) (if (AndroidMedia.disc(platform, gameKey, DiscLook.on)) "disc" else "cartridge")
                else if (customCartFor(platform)) "cartridge" else mediaKind(platform)) in hidden) com.shelf.launcher.data.CardArtType.COVER else type
    else -> type
}

/** What "Mixed categories" draws every game of a list with several consoles as (Settings › Appearance › Game row). */
enum class MixedArt(val label: String) {
    BASIC("As picked"),
    AS_CHOSEN("As chosen above (sizes can differ)"),
    COVER("All covers"),
    CAPSULE("All wide capsules"),
    DISC("All CDs"),
    CARTRIDGE("All cartridges")
}

/** The generic media's keys in the template table (MediaVariants): the Game Boy shell without its branding, and the PS2 CD. */
internal const val GENERIC_CART = "~generic-cart"
internal const val GENERIC_DISC = "~generic-disc"
/** Update 125: Android games' cartridge — the generic cartridge in green (res tpl_cart_android). */
internal const val ANDROID_CART = "~android-cart"

/**
 * A game's cartridge / disc picture: the shell and the platform key its template is looked up under, and its
 * proportions (width / height; update 128: your cartridge pack's shape for CUSTOM_CART).
 */
internal class MediaPick(val shell: MediaShell, val platform: String, val aspect: Float = shell.aspect)

/**
 * Update 200: the 3D cartridges looks are on (Features.modelFinish isn't Off — the setting the retired 3D models used,
 * kept so a look picked before still works).
 */
internal fun drawn3dOn(): Boolean = Features.modelFinish.state.value != ModelFinish.OFF

/**
 * Update 200: what a game is drawn as in the 3D cartridges looks — the drawn pieces (CartShells.kt) with real depth,
 * which turn smoothly when inspected, instead of the pre-rendered 3D models:
 *  - 3D cartridges (ModelFinish.REAL): its console's authentic shell, by the game's region (your pack's shape for a
 *    console without one);
 *  - 3D custom cartridges (CUSTOM): your cartridge pack's shape (R1 › Cartridge shape) when that console's part of the
 *    pack is on the device, else the authentic shell;
 *  - 3D clear cartridges (CLEAR): the same in clear plastic.
 * Null for disc consoles (they keep their drawn disc / case, as in the other looks), with "All CDs" in a list mixing
 * consoles, and for a console with neither a shell nor the pack.
 */
internal fun drawn3dPick(platform: String, mixed: Boolean, gameKey: String?): MediaPick? {
    val finish = Features.modelFinish.state.value
    if (finish == ModelFinish.OFF) return null
    val own = MediaShell.of(platform)
    if (own != null && (own.disc || own == MediaShell.UMD)) return null
    if (mixed && Features.mixedArt.state.value == MixedArt.DISC) return null
    val clear = finish == ModelFinish.CLEAR
    val authentic = CartShells.authenticIdFor(platform, CaseCatalog.regionOf(gameKey ?: ""), gameKey)
    val packHere = CustomCarts.idFor(platform)?.let { com.shelf.launcher.data.Packs.hasCart(it) } == true
    val pack = "pack:" + Features.cartLook.state.value.key + "~" + (if (clear) "clear" else "opaque")
    // (Update 210: the game's own real edition — its plastic or special shell — where one is known for it, MediaEditions.)
    val edition = if (clear) null else MediaEditions.autoRaw(platform, gameKey)
    val raw = when {
        finish != ModelFinish.REAL && packHere -> pack
        edition != null -> edition
        authentic != null -> authentic + "~" + (if (clear) "clear" else "")
        packHere -> pack
        else -> return null
    }
    return CartShells.pickOf(raw, platform, gameKey)
}

/**
 * The cartridge / disc a game is drawn as: its console's own, or - in a list mixing consoles with "All CDs" /
 * "All cartridges" - the generic one (a cartridge game gets a plain CD with its label scan on it, a disc game a
 * plain cartridge). Update 204: never null any more — a console with no physical game gets the plain ones.
 */
internal fun mediaFor(platform: String, mixed: Boolean = false, asDisc: Boolean = false, gameKey: String? = null): MediaPick? {
    // Update 212: Android games — disc or cartridge by one rule everywhere (AndroidMedia.kt): this game's choice, a shell
    // or pack you picked, Settings › Game look › Android games, else the look (Discs only, "All CDs").
    if (AndroidMedia.isAndroid(platform)) {
        val lookDisc = asDisc || (mixed && Features.mixedArt.state.value == MixedArt.DISC) || DiscLook.on
        if (AndroidMedia.disc(platform, gameKey, lookDisc)) return MediaPick(MediaShell.DISC, genericDiscKey("Android"))
        // A cartridge: the one it has on its own (its shell, your pack, else the green Android cartridge).
        if (asDisc || mixed) return mediaFor(platform, false, false, gameKey)
    }
    // Update 195: the shell picked for this game or its console (the game's menu › Cartridge shell…, CartShells.kt).
    // (Update 200: [gameKey] too — a 3D piece saved before goes on the console's authentic shell by the game's region.)
    if (!asDisc) CartShellStore.choice(platform, gameKey)?.let { raw -> CartShells.pickOf(raw, platform, gameKey)?.let { return it } }
    // Update 200: the 3D cartridges looks draw the drawn pieces with real depth (they used to draw the pre-rendered 3D
    // models, which turned in 20° steps like a slide show): see drawn3dPick.
    if (!asDisc) drawn3dPick(platform, mixed, gameKey)?.let { return it }
    // Update 128, Cartridges look: the game on your cartridge pack (CustomCarts.kt) — every console with Custom, the
    // ones without a cartridge of their own with Original (consoles Voltcap has no media for included).
    if (!asDisc && customCartFor(platform)) {
        // (Update 131: a disc console's own shell from your optical-system pack has its own shape.)
        return MediaPick(MediaShell.GB, CUSTOM_CART + platform, CustomCarts.aspectFor(platform))
    }
    // Update 195: Authentic drawn cartridges — a cartridge console's own real shell (by the game's region).
    // (Update 205: in lists mixing consoles too — they drew the template's flat cartridge there, a different piece from
    // the one the same game showed in its own console's list.)
    // (Update 206, one cartridge per console: always — not only after Real shell was picked once (Features.authenticCarts,
    // off for anyone who never picked it). Without it a cartridge beside its case, in the Disc station or the launch
    // animation was the template pack's flat picture: a second, different N64 / Game Boy / SNES cartridge.)
    if (!asDisc) {
        val ownShell = MediaShell.of(platform)
        if (ownShell == null || !(ownShell.disc || ownShell == MediaShell.UMD)) {
            val region = CaseCatalog.regionOf(gameKey ?: "")
            // (Update 210: its real edition first — MediaEditions.)
            MediaEditions.autoRaw(platform, gameKey)?.let { raw -> CartShells.pickOf(raw, platform, gameKey)?.let { return it } }
            CartShells.authenticIdFor(platform, region, gameKey)?.let { id -> CartShells.pickOf(id, platform)?.let { return it } }
        }
    }
    // Update 204: a console without a cartridge or disc of its own (arcade, PC, DOS…) gets Voltcap's plain ones — the
    // plain CD with its console's name as a disc, else the plain cartridge — instead of a flat cover among the others.
    val own = MediaShell.of(platform) ?: return if (asDisc || (mixed && Features.mixedArt.state.value == MixedArt.DISC))
        MediaPick(MediaShell.DISC, genericDiscKey(platform)) else MediaPick(MediaShell.GB, GENERIC_CART)
    // Update 129, Discs only with one console's disc picked for all (R1 › Disc): every game on that disc.
    // (Update 141: another console's disc only as a shape — a game of another console gets the plain disc with its own
    // console's branding instead of that console's printed disc; a UMD the plain drawn one.)
    if (asDisc) LookStyles.disc()?.let { s ->
        val sh = MediaShell.of(s)!!
        return when {
            s == platform -> MediaPick(sh, s)
            sh.disc -> MediaPick(MediaShell.DISC, genericDiscKey(platform))
            else -> MediaPick(sh, "plain:$s")
        }
    }
    // Update 125, Cartridges only: a disc / UMD game (and Steam) as the plain cartridge with its cover as the label.
    if (!asDisc && cartsOnlyFor(platform)) return MediaPick(MediaShell.GB, GENERIC_CART)
    // Steam has no disc of its own: always the generic CD with Steam's logo (update 119).
    if (platform == "Steam") return MediaPick(MediaShell.DISC, genericDiscKey(platform))
    // Update 125: Android games on Voltcap's own green cartridge; as a disc (Discs only, All CDs), the plain CD with
    // Android's logo.
    if (platform == "App" || platform == "Android") {
        val disc = asDisc || (mixed && Features.mixedArt.state.value == MixedArt.DISC)
        return if (disc) MediaPick(MediaShell.DISC, genericDiscKey("Android")) else MediaPick(MediaShell.GB, ANDROID_CART)
    }
    // Card artwork "Discs only": a cartridge game as the generic CD.
    if (asDisc && !(own.disc || own == MediaShell.UMD)) return MediaPick(MediaShell.DISC, genericDiscKey(platform))
    if (mixed) {
        val isDiscKind = own.disc || own == MediaShell.UMD
        when (Features.mixedArt.state.value) {
            MixedArt.DISC -> if (!isDiscKind) return MediaPick(MediaShell.DISC, genericDiscKey(platform))
            MixedArt.CARTRIDGE -> if (isDiscKind) return MediaPick(MediaShell.GB, GENERIC_CART)
            else -> {}
        }
    }
    return MediaPick(own, platform)
}

/**
 * Update 125: Cartridges only is on and [platform]'s games aren't cartridges (discs, UMDs, Steam), so they're drawn as
 * the plain cartridge instead of their own disc. Update 128: or on your cartridge pack (customCartFor) — their cover is
 * the label then, never a disc / label scan.
 */
internal fun cartsOnlyFor(platform: String): Boolean {
    if (!Features.cartsOnly.state.value) return false
    if (customCartFor(platform)) return true
    val own = MediaShell.of(platform) ?: return false
    return own.disc || own == MediaShell.UMD || platform == "Steam"
}

/** The card art needs the box / label scans looked up (box art, real-size boxes, cartridges / discs, discs only). */
internal fun com.shelf.launcher.data.CardArtType.wantsMedia(): Boolean =
    this == com.shelf.launcher.data.CardArtType.CASE || this == com.shelf.launcher.data.CardArtType.CASE_REAL ||
        this == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC || this == com.shelf.launcher.data.CardArtType.DISC

/**
 * "Box art · real sizes": the biggest case among the games shown (width and height, mm). Every case is drawn at
 * mm × (the slot / this), so the biggest one fills its slot and the others are their real size next to it.
 */
class RealCaseRef(val wMm: Float, val hMm: Float)

/** Null unless [type] is "Box art · real sizes"; else the biggest case among [games]. */
internal fun realCaseRef(type: com.shelf.launcher.data.CardArtType, games: List<com.shelf.launcher.data.Game>): RealCaseRef? {
    if (type != com.shelf.launcher.data.CardArtType.CASE_REAL) return null
    var w = 0f; var h = 0f
    for (p in games.asSequence().map { it.platform }.distinct()) {
        val mm = realCaseMm(p) ?: continue
        w = maxOf(w, mm.first); h = maxOf(h, mm.second)
    }
    return if (w > 0f && h > 0f) RealCaseRef(w, h) else null
}

/**
 * A console's game case as sold (width, height in mm, the usual US / EU release; approximate). The longest side is
 * from this table, the shape from the console's cover template (MediaCovers), so the drawn case fills it exactly.
 */
internal fun realCaseMm(platform0: String?): Pair<Float, Float>? {
    // (Update 129: the case picked for all on R1 › Case, when there is one.)
    val platform = LookStyles.casePlatform(platform0)
    // Update 208: the real case's own size from CaseCatalog (North American release) when it's known. The table below put
    // PS3 games in a 190 mm DVD case (a Blu-ray case is 171.5) — PS3 cases came out as tall as PS2 ones — and shaped the
    // rest from the cover templates.
    // (Keep cases and card cases only: their sleeve is the whole front. A jewel case's or box's printed front isn't the
    // case's outline, and the box scan cuts — BoxTexture — go by the cover templates' shapes there.)
    if (platform != null) CaseCatalog.of(platform, CaseRegion.US)?.takeIf { it.shell == CaseShell.KEEP || it.shell == CaseShell.CARDCASE }
        ?.let { return it.wMm to it.hMm }
    val longest = when (platform) {
        "NES", "Genesis", "MasterSystem", "32X", "Intellivision", "Vectrex", "C64", "Pico8" -> 184f
        "SNES", "N64" -> 185f
        "Atari2600", "Atari7800" -> 193f
        "ColecoVision" -> 203f
        "GB", "GBC", "GBA" -> 117f
        "VirtualBoy" -> 215f
        "NDS", "3DS" -> 137f
        "Switch" -> 170f
        // (Update 119: Steam games in a PlayStation 4-sized case; update 125: Android games too.)
        "Steam", "PS4", "App", "Android" -> 170f
        "GameCube", "Wii", "WiiU", "PS2", "Xbox", "Xbox360" -> 190f
        "PS3", "PS5", "XboxOne", "XboxSeries" -> 171.5f
        "PS1", "Dreamcast", "NeoGeoCD", "PCEngineCD" -> 142f
        "Saturn" -> 220f
        "SegaCD" -> 280f
        "3DO" -> 300f
        "PSP" -> 180f
        "PSVita" -> 135f
        "GameGear", "Lynx" -> 152f
        "Jaguar" -> 230f
        "NeoGeo" -> 265f
        "NeoGeoPocket", "WonderSwan" -> 120f
        "PCEngine" -> 128f
        "PokemonMini" -> 90f
        else -> return null
    }
    val aspect = MediaCovers.of(platform)?.frontAspect ?: MediaTemplates.of(platform)?.box?.let { b ->
        if (b.frameRes != null) b.frameAspect else b.aspect
    } ?: 0.7f
    return if (aspect >= 1f) longest to longest / aspect else longest * aspect to longest
}

/**
 * The card's art is the real object — a drawn cartridge / disc, or the template pack's box — shown free-standing
 * (no card behind it) and straight from the front.
 */
internal fun isFreeMedia(type: com.shelf.launcher.data.CardArtType, platform: String): Boolean = when (type) {
    // (Update 128: a game on your cartridge pack too — with "Real discs" as well, there's no scan of it to show.)
    // (Update 129: with "Real discs" too — the game's own scan stands free like the drawn one, see realScanOf.)
    // (Update 200: or a drawn piece of the 3D cartridges looks — the pre-rendered 3D models are retired.)
    // (Update 204: every game — a console without a cartridge or disc of its own gets Voltcap's plain one, mediaFor.)
    com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC -> true
    // Box art always uses the template pack's case when there's one for the console.
    // (Update 129: the case picked for all on R1 › Case, when there is one.)
    com.shelf.launcher.data.CardArtType.CASE -> LookStyles.casePlatform(platform).let { p ->
        MediaTemplates.of(p)?.box != null || MediaCovers.used(p) != null
    }
    else -> false
}

/** Non-null where the game's console has an official case to show (box art): the flat cover frame or the template pack's box. */
internal fun caseMarker(platform: String): Any? =
    if (isFreeMedia(com.shelf.launcher.data.CardArtType.CASE, platform)) Unit else null

/**
 * Under a free-standing cartridge / disc / box you're on there used to be a pool of accent light on the "floor";
 * it's gone (update 84): the object itself grows / lifts instead. Kept as a no-op so the callers stay simple.
 */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.mediaFloorGlow(on: Boolean, color: androidx.compose.ui.graphics.Color, pulse: Float = 0f): Modifier = this

/** Cartridge / disc look: drawn media with the label, or the scraped picture as it is. */
internal val mediaShellsOn: Boolean get() = Features.mediaShells.state.value

/**
 * Update 122: whether a Cartridges & discs card draws the cartridge / disc — with "Drawn discs", or with "Real discs"
 * while the game has no scan of its own yet ([typed] null: not scraped, or ScreenScraper not reachable). It used to
 * fall back to the cover, so "Real discs" showed no discs at all until everything was scraped.
 */
internal fun drawnMediaWanted(type: com.shelf.launcher.data.CardArtType, typed: String?): Boolean =
    // (Update 200: the 3D cartridges looks always drew their pieces. Update 202: the 3D finish is every cartridge look now,
    // so "The game's own photo" (Features.mediaShells off) decides there too: its own photo when found, else the shell.)
    type == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC && (mediaShellsOn || typed == null)

/**
 * Update 129: with "Real discs", the game's own cartridge / disc scan ([typed]) a Cartridges & discs card shows — it
 * stands free like the drawn ones (it used to sit in a dark card in the Voltcap row and the other rows). Null when the
 * drawn one shows.
 */
internal fun realScanOf(type: com.shelf.launcher.data.CardArtType, typed: String?): String? =
    typed?.takeIf { type == com.shelf.launcher.data.CardArtType.CARTRIDGE_DISC && !mediaShellsOn }

/** Update 129: [platform]'s game is round (a disc or UMD), for its own scan's picture. */
// (Update 207: not a UMD — its photo is the UMD in its shell, which isn't round; it was cut to a circle and spun as a disc.)
internal fun mediaRound(platform: String): Boolean = mediaKind(platform) == "disc" && MediaShell.of(platform) != MediaShell.UMD

/** A ring: the printed part of a disc (between the hub and the rim). */
private class RingShape(private val inner: Float, private val outer: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2
        val p = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r * outer))
            addOval(Rect(c, r * inner))
        }
        return Outline.Generic(p)
    }
}

/**
 * The game's cartridge / disc, keeping its proportions inside [modifier], with [label] printed on it (a plain
 * sticker colour shows while it loads). GameCube discs are drawn at their real 8 cm size unless
 * "Bigger GameCube discs" is on.
 */
@Composable
internal fun PhysicalMedia(
    shell: MediaShell, label: Any?, modifier: Modifier = Modifier,
    /** The game's console: its cartridge / disc from your template pack when there is one (MediaTemplates). */
    platform: String? = null,
    /** [label] is the game's real label scan (else it's the cover). */
    labelIsScan: Boolean = false,
    /** The game's cover, for the front of its case when "Disc in its case" is on. */
    caseCover: Any? = null,
    /** The game's name (a drawn shell's label shows it while there's no art). */
    title: String? = null,
    /** Update 168: the disc alone even with "Disc in its case" on (the carousels' 3D case draws the case itself). */
    noCase: Boolean = false
) {
    // (Update 200: the pre-rendered 3D models' branch is gone — mediaFor never hands one out any more; the 3D cartridges
    // looks come here as drawn shells, below.)
    // Update 195: a game on a shell from the cartridge library, the cover as its label.
    if (platform != null && platform.startsWith(SHELL_PREFIX)) {
        // (Update 203: a solid piece wherever it's turned — SolidPiece.kt.)
        // (Update 205: the game's real label scan when it's handed one — the whole sticker, no console band over it.)
        CartShellArt(
            platform, if (labelIsScan && label != null) label else caseCover ?: label,
            modifier.solidPiece(rememberShellSolid(platform)).mediaMotion(false, (label?.hashCode() ?: 0) xor platform.hashCode()),
            title = title, labelIsScan = labelIsScan && label != null
        )
        return
    }
    // Update 128: a game on your cartridge pack — its cover in the window (a disc / label scan wouldn't fit it).
    if (platform != null && platform.startsWith(CUSTOM_CART)) {
        // (Update 203: your pack's cartridge solid when turned.)
        // (Update 214: a disc console's game on your optical-system pack is a cartridge-shaped shell too — solid at that
        // shell's own shape, and moving as a cartridge does: rocking slowly left and right. Update 203 gave it the disc's
        // motion, so with Moving discs on it went round and round like a clock's hands.)
        val packShell = discShellFor(platform.removePrefix(CUSTOM_CART))
        CustomCart(
            platform.removePrefix(CUSTOM_CART), caseCover ?: label,
            modifier.solidPiece(cartSolid(null, platform, packShell?.aspect ?: Features.cartLook.state.value.aspect, custom = true))
                .mediaMotion(false, (label?.hashCode() ?: 0) xor platform.hashCode())
        )
        return
    }
    // A game without a disc of its own on the blank CD: its own console's logo and name on it (GenericDisc.kt).
    genericDiscConsole(platform)?.let { console ->
        GenericDisc(console, label, modifier.followTurn().mediaMotion(true, (label?.hashCode() ?: 0) xor console.hashCode()))
        return
    }
    val tpl = MediaTemplates.of(platform)
    // "Disc in its case": the case with the disc half out of it, spinning (disc games only).
    if (tpl != null && discCaseOn(tpl) && !noCase) {
        val front = caseCover ?: label.takeIf { !labelIsScan }
        DiscInCase(tpl, boxFront(tpl.box!!, front, null).first, false, label ?: caseCover, modifier.followTurn(), platform = platform)
        return
    }
    // Discs turn slowly, cartridges give a little hop now and then (each on its own beat).
    val discLook = when {
        tpl?.disc != null -> true
        tpl != null && (tpl.cart != null || tpl.label != null) -> false
        else -> shell.disc
    }
    // (Update 203: a cartridge solid wherever it's turned, at its template's / drawn shell's own shape; a disc turns flat.)
    val modifier = (if (discLook) modifier.followTurn() else modifier.solidPiece(cartSolid(shell, platform, tpl?.cart?.aspect ?: shell.aspect)))
        .mediaMotion(discLook, (label?.hashCode() ?: 0) xor (platform?.hashCode() ?: 0))
    if (tpl != null && TemplateMedia(
            tpl, labelScan = if (labelIsScan) label else null, cover = if (labelIsScan) null else label, modifier = modifier,
            smallDisc = shell == MediaShell.MINI_DISC && !Features.bigGcDiscs.state.value
        )
    ) return
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val small = shell == MediaShell.MINI_DISC && !Features.bigGcDiscs.state.value
        val scale = if (small) 0.7f else 1f
        val w = minOf(maxWidth, maxHeight * shell.aspect) * scale
        val h = w / shell.aspect
        Box(Modifier.size(w, h)) {
            if (shell.disc) {
                val pad = shell.label[0]
                Box(
                    Modifier.offset(x = w * pad, y = h * pad).size(w * (1 - 2 * pad), h * (1 - 2 * pad))
                        .clip(RingShape(shell.label[1], shell.label[2]))
                ) {
                    if (label != null) AsyncImage(mediaArtModel(label), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) // (Update 157)
                }
            } else {
                val l = shell.label
                Box(
                    Modifier.offset(x = w * l[0], y = h * l[1]).size(w * (l[2] - l[0]), h * (l[3] - l[1]))
                        .clip(if (shell == MediaShell.UMD) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(w * shell.corner))
                        .androidx_background()
                ) {
                    // (Update 215: a cover fitted at its own shape, never stretched; a label scan still fills the window.)
                    if (label != null) AsyncImage(mediaArtModel(label), null, contentScale = if (labelIsScan) ContentScale.FillBounds else ContentScale.Fit, modifier = Modifier.fillMaxSize()) // (Update 157; 206: stretched to the label window)
                }
            }
            Image(painterResource(shell.res), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The physical media's idle life, as set in Settings › Game look (Moving discs & cartridges):
 * - a disc spins round slowly like it's playing and/or bobs softly up and down;
 * - a cartridge turns slowly left and right and/or hops up a little and settles (a tiny squash).
 * All of them move in step (update 104: one clock; [seed] is no longer used for that). Read in the draw phase only (no
 * recomposition). Still with Lite effects, animations off, while the home screen rests or is covered. A turning disc
 * or cartridge is drawn flat into its own layer first and then turned (cheap, and it keeps Android's text cache out
 * of it, see flatTextFor3d).
 */
// (Update 151: internal, for the 3D case + disc in DiscCase3D.kt.)
@Composable
internal fun Modifier.mediaMotion(disc: Boolean, seed: Int): Modifier =
    // (Update 203: a cartridge's slow turn is drawn by its solid piece — solidPiece, rememberCartSpin — not turned flat here.)
    motionLayer(disc, (if (disc) Features.discMotion else Features.cartMotion).state.value, seed, turnCart = false)

/** A box (box art): turns slowly left and right and/or hops like a cartridge, as set under Cases (default still). */
@Composable
internal fun Modifier.caseMotion(seed: Int): Modifier = motionLayer(false, Features.caseMotion.state.value, seed)

/** The same motion for a preview in Settings (update 109): [motion] as picked, whatever is saved. */
@Composable
internal fun Modifier.motionPreview(disc: Boolean, motion: Features.MediaMotion): Modifier = motionLayer(disc, motion, 0)

@Composable
private fun Modifier.motionLayer(disc: Boolean, motion: Features.MediaMotion, seed: Int, turnCart: Boolean = true): Modifier {
    val on = !LocalLiteEffects.current && rememberAnimationsEnabled() && rememberIsActive() && !HomeIdle.calm
    if (!on || motion == Features.MediaMotion.OFF) return this
    // Update 112: only the game you're on moves (Home › Animations, per layout too).
    MotionPrefs.observe()
    if (MotionPrefs.focusedOnlyMotion() && !LocalMediaFocused.current) return this
    // Update 116: inside a part set to stay still.
    if (LocalStillPart.current) return this
    // One clock for all of them (update 104): every disc turns and every cartridge hops in step with the others on
    // screen, read off the frame time (the same for everything drawn in a frame) instead of each one's own start.
    val now = MediaClock.rememberFrameTime()
    val spinMs = if (disc) 6000L else 5200L
    val bounceMs = if (disc) 2400L else 3000L
    val spins = motion.spins
    val bounces = motion.bounces
    return this.graphicsLayer {
        val ms = now.longValue / 1_000_000L
        val sp = (ms % spinMs) / spinMs.toFloat()
        val bp = (ms % bounceMs) / bounceMs.toFloat()
        if (disc) {
            if (spins) {
                rotationZ = sp * 360f
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            // A soft bob: up and back down smoothly, no rest, small enough to stay inside its space.
            if (bounces) translationY = -size.height * 0.035f * (0.5f - 0.5f * kotlin.math.cos(bp * 2 * Math.PI).toFloat())
        } else {
            if (spins && turnCart) {
                // Slowly left and right, about ±24°, like it's turning on a stand.
                rotationY = 24f * kotlin.math.sin(sp * 2 * Math.PI).toFloat()
                cameraDistance = 14f * density
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            if (bounces) {
                // Up and down in the first quarter of the beat, a little squash as it lands, then a rest.
                val hop = if (bp < 0.24f) kotlin.math.sin(bp / 0.24f * Math.PI).toFloat() else 0f
                val land = if (bp in 0.24f..0.32f) kotlin.math.sin((bp - 0.24f) / 0.08f * Math.PI).toFloat() else 0f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                translationY = -size.height * 0.045f * hop
                scaleY = 1f + 0.025f * hop - 0.03f * land
                scaleX = 1f - 0.012f * hop + 0.02f * land
            }
        }
    }
}

/** The sticker's paper colour under the label while it loads. */
private fun Modifier.androidx_background(): Modifier =
    this.then(Modifier.background(androidx.compose.ui.graphics.Color(0xFFE9E6DF)))

/** "Disc in its case" is on and the pack has both the disc and the box for this console. */
/** Update 144: how much of a disc sticks out of its case (a share of the disc's width). */
internal const val DISC_OUT = 0.5f

/**
 * Update 112 / 144: the disc's size against its case's height — 120 mm over the case's height (a PS1 jewel case is 125 mm
 * tall, a DVD-style case 190 mm) — measured against the case's edge it comes out of, which the 3D box draws shorter
 * (it's turned away). [scanCase]: the game's own case scan (full height).
 */
internal fun discFrac(platform: String?, aspect: Float, box: BoxTpl, scanCase: Boolean): Float {
    val caseMm = realCaseMm(platform)?.second
    val real = if (caseMm != null && caseMm > 0f) (120f / caseMm).coerceIn(0.4f, 0.95f) else if (aspect >= 0.85f) 0.9f else 0.66f
    val edge = if (scanCase) 1f else (box.front[5] - box.front[3]).coerceIn(0.4f, 1f)
    return real * edge
}

/**
 * Update 144: how far right past its case a game's disc / cartridge sticks out on a case card (Case + disc, Case with its
 * cartridge / disc), as a share of the case's height; 0 when nothing sticks out. The row leaves that much room after the
 * game you're on, so its disc shows whole (the others tuck theirs behind the next game).
 */
internal fun casePeek(type: Art, platform: String?): Float {
    if (type != Art.CASE || platform == null) return 0f
    val p = LookStyles.casePlatform(platform) ?: return 0f
    // Update 151: the 3D case + disc (DiscCase3D.kt) has its own.
    disc3dPeek(p)?.let { return it }
    // (Update 151: no 3D case models in the plain case looks any more — every console's case is flat there, see CaseArt.)
    val shell = MediaShell.of(p)
    if (Features.caseWithMedia.state.value && shell != null) {
        val round = shell.disc || shell == MediaShell.UMD
        return (if (round) 0.6f else 0.5f) * (if (round) 1f else shell.aspect) * 0.62f
    }
    val tpl = MediaTemplates.of(p) ?: return 0f
    val box = tpl.box ?: return 0f
    if (!discCaseOn(tpl)) return 0f
    val cov = MediaCovers.used(p)
    val scan = Features.originalCases.state.value
    val aspect = if (scan) cov?.frontAspect ?: box.aspect else if (p == "PS1") cov?.frontAspect ?: box.aspect else box.aspect
    return discFrac(p, aspect, box, scan) * DISC_OUT
}

internal fun discCaseOn(tpl: MediaTpl?): Boolean =
    tpl != null && tpl.disc != null && tpl.box != null && Features.discCase.state.value

/**
 * A disc game as a case with its disc half out of it, spinning: the game's box (its cover in the system's template, with
 * the system's spine) stands at the left, the disc behind it sticks out at the right and turns like it's playing. [front]
 * is the case's art ([frontIsBox]: a ready-made box scan), [discArt] the disc's printed art. Fits inside [modifier].
 */
@Composable
internal fun DiscInCase(
    tpl: MediaTpl, front: Any?, frontIsBox: Boolean, discArt: Any?, modifier: Modifier = Modifier,
    title: String = "", detail: Int = 360,
    /** The game's own case scan (update 104): the case is that scan rather than the template's box. */
    caseScan: Any? = null,
    /** The game's own disc picture (update 104): the disc is that picture rather than a drawn one. */
    realDisc: Any? = null,
    caseAspect: Float? = null,
    /** Update 112: the console, for the disc's real size against its case (a 12 cm disc in a 12.5 cm jewel case, a 19 cm DVD case…). */
    platform: String? = null,
    /** Update 202: the game's own spine (BoxTexture.realSpine) for the template's 3D box — null: the console's. */
    realSpine: Any? = null
) {
    val box = tpl.box ?: return
    val discOnly = MediaTpl(null, null, tpl.disc, null)
    val seed = (discArt?.hashCode() ?: 0) xor (box.res * 31)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Update 143: a PlayStation (PS1) case is its cover only — no black PlayStation band down the scan's left, and with
        // no scan no 3D box turned to show its side (that showed as a white strip): the flat case with the cover in it.
        val ps1 = platform == "PS1"
        val ps1Cov = if (ps1) MediaCovers.of(platform) else null
        val aspect = if (caseScan != null) caseAspect ?: box.aspect else ps1Cov?.frontAspect ?: box.aspect
        // Update 112: the disc is its real size against the case — 120 mm over the case's height (a PS1 jewel case is
        // 125 mm tall, a DVD-style case 190 mm) — and measured against the case's edge it comes out of, which the 3D
        // box draws shorter (it's turned away). It used to be 0.86 of the full height: bigger than a PS1 case's edge.
        val dFrac = discFrac(platform, aspect, box, caseScan != null)
        val outFrac = DISC_OUT
        // Update 144: the case as big as a case on its own in the same place (it used to shrink so its disc fitted beside
        // it, smaller than the cases with no disc); the disc sticks out to the right past the slot, behind the next game.
        val h = minOf(maxHeight, maxWidth / aspect)
        val d = h * dFrac
        val lead = (maxWidth - h * aspect) / 2
        var scanFailed by androidx.compose.runtime.remember(caseScan) { androidx.compose.runtime.mutableStateOf(false) }
        Box(
            Modifier.align(Alignment.CenterStart).wrapContentSize(Alignment.CenterStart, unbounded = true)
                .offset(x = lead).size(h * aspect + d * outFrac, h)
        ) {
            // The disc, behind the case and sticking out of its right side, turning round.
            Box(Modifier.size(d).align(Alignment.CenterEnd).mediaMotion(true, seed)) {
                if (realDisc != null) coil.compose.AsyncImage(
                    mediaArtModel(realDisc), null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, // (Update 157; 159: the scale was inside the comment)
                    modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape)
                )
                else TemplateMedia(discOnly, labelScan = null, cover = discArt, modifier = Modifier.fillMaxSize())
            }
            if (caseScan != null && !scanFailed) ScanCase(caseScan, aspect, Modifier.size(h * aspect, h).align(Alignment.CenterStart), onFail = { scanFailed = true }, cover = discArt, cutLeft = if (ps1) PS1_BAND else 0f)
            else if (ps1Cov != null) CoverCase(ps1Cov, discArt ?: front, CaseArtPlaceholder, Modifier.size(h * aspect, h).align(Alignment.CenterStart))
            // (Update 202: with the game's real spine, as its closed case shows it — the Portal's game window showed the
            // console's plain template spine.)
            else Box3D(box, front, frontIsBox, title, Modifier.size(h * box.aspect, h).align(Alignment.CenterStart), spin = false, detail = detail, realSpine = realSpine)
        }
    }
}


/**
 * The game's own cartridge / disc picture (update 104), moving like the drawn ones (Settings › Game look › Moving
 * discs & cartridges). [round]: a disc (kept in its circle).
 */
@Composable
internal fun RealMediaPicture(
    model: Any, round: Boolean, modifier: Modifier = Modifier,
    /** Update 204: the game's console and key — a cartridge photo then shows its real shell's researched back when turned. */
    platform: String? = null, gameKey: String? = null
) {
    // (Update 203: the game's own cartridge photo solid when turned; a disc photo turns flat.)
    // (Update 204: at the photo's own shape, with its console's real back — rememberPhotoSolid.)
    // (Update 205: its sides in the photo's own plastic colour, read off the photo once it has loaded.)
    var loaded by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    var tone by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf(PhotoTones.of(model)) }
    androidx.compose.runtime.LaunchedEffect(loaded) {
        val d = loaded ?: return@LaunchedEffect
        if (round || tone != null) return@LaunchedEffect
        val t = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            (d as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { photoShellTone(it) }
        }
        if (t != null) { PhotoTones.put(model, t); tone = t }
    }
    val solid = rememberPhotoSolid(platform, gameKey, (model as? String)?.let { CoverAspects.rawOf(it) }, tone)
    Box((if (round) modifier.followTurn() else modifier.solidPiece(solid)).mediaMotion(round, 0), contentAlignment = Alignment.Center) {
        // (Update 207: a disc photo has its empty margins trimmed and fills its circle — with a margin round it, the disc's
        // drawn rim and edge in the game window stood out round the smaller photo as a second layer behind the disc.)
        val shared = mediaArtModel(model)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val roundModel = androidx.compose.runtime.remember(model, round) {
            if (round && model is String) coil.request.ImageRequest.Builder(ctx).data(model).transformations(TrimToOpaque).build() else null
        }
        coil.compose.AsyncImage(
            roundModel ?: shared, null,
            contentScale = if (round) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit, // (Update 157)
            modifier = Modifier.fillMaxSize().then(if (round) Modifier.clip(androidx.compose.foundation.shape.CircleShape) else Modifier),
            // Update 200: its shape noted, so the launch animation (LaunchThrow) sizes the game's own cartridge at it.
            onSuccess = { r ->
                (model as? String)?.let { k -> r.painter.intrinsicSize.let { sz -> CoverAspects.note(k, sz.width, sz.height) } }
                if (!round) loaded = r.result.drawable
            }
        )
    }
}

/** The frame time all the moving discs and cartridges share (update 104), so the ones on screen move in step. */
internal object MediaClock {
    @Composable
    fun rememberFrameTime(running: Boolean = true): androidx.compose.runtime.MutableLongState {
        val t = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.nanoTime()) }
        androidx.compose.runtime.LaunchedEffect(running) {
            if (!running) return@LaunchedEffect
            while (true) androidx.compose.runtime.withFrameNanos { t.longValue = it }
        }
        return t
    }
}

/** Update 205: the plastic colour read off each cartridge photo (its address), so it's read once per run. */
internal object PhotoTones {
    private val map = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.Color>()
    fun of(model: Any?): androidx.compose.ui.graphics.Color? = (model as? String)?.let { map[it] }
    fun put(model: Any?, c: androidx.compose.ui.graphics.Color) { (model as? String)?.let { if (map.size < 4000) map[it] = c } }
}
