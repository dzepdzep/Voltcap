package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Packs

/*
 * Update 136: your 3D shape library (VoltCap 3D Shape Library v6.3: 81 cartridges, cards, cases and discs, with their
 * logos), pre-rendered from seven angles (−60° … +60°, every 20°) instead of drawn live. Each angle is two pictures in the
 * "3D models" pack (data/Packs.kt, files/packs/models/<id>/):
 *  - <yaw>.webp: the model with its front art window empty (dark);
 *  - <yaw>_g.webp: how much light the window gives back at each spot (its shading, and the tint of a clear shell in
 *    front of it); its alpha is where the window shows.
 * meta.json has the picture's size, the window's picture shape and, for each angle, the window's four corners.
 * The game's cover is warped into those corners, times the shading, added on top of the model: the same light, gloss and
 * clear plastic as the 3D model, at the cost of drawing two pictures (tools/render3d makes the pack from the .glb files).
 *
 * Update 137: the library's v6.8 (your refined designs). The custom cartridges and cards come without a colour or logo of
 * their own: their shell is pre-rendered in a few greys (<yaw>_t0 / _t50 for the solid ones, _t0 / _t25 / _t50 / _t75 /
 * _t100 for the clear ones), <yaw>_b says where the console badge is, and the app paints them in the game's console
 * colours (or yours: R1 › Cartridge colour, one game or all of them) and prints that console's logo on the badge (the
 * "logos" piece of the pack: the ES-DE colour logos and each console's shell / badge colours). A solid shell is exact (the
 * pre-render's tone curve is undone, the colour applied, the curve put back); a clear one blends the nearest greys.
 *
 * Update 166: your library's v6.11 with its blank-branding variants (no native lettering), from 18 angles all the way
 * round (tools/render3d/batch3.js): the art window and logo only where the front faces you, the custom pieces painted on
 * every side. The real cartridges and cards keep their own colours, their blank badge printed with the console's logo.
 */

/** Which of the library's pieces a game is drawn as (Settings › Game look › 3D models; Off = the drawn / scanned media). */
enum class ModelFinish(val label: String) {
    OFF("Off"),
    /** The console's real cartridge / card, its real case for disc consoles. */
    REAL("Real"),
    /** Your custom cartridges (also for disc consoles: the PlayStation, GameCube, Dreamcast and PSP cartridges). */
    CUSTOM("Custom"),
    /** The custom cartridges in clear tinted plastic, the board showing inside. */
    CLEAR("Clear")
}

/** Update 136: the Save Vault's 3D card (Save Vault › Style › 3D card): the SD card or a memory card colour. */
enum class ModelSpare(val label: String, val id: String) {
    SD("SD card", "sd_card_hq"),
    BLACK("Black card", "memory_card_voltcap_black"),
    BLUE("Blue card", "memory_card_voltcap_blue"),
    GREEN("Green card", "memory_card_voltcap_green"),
    RED("Red card", "memory_card_voltcap_red"),
    SMOKE("Smoke card", "memory_card_voltcap_smoke")
}

/** How the games in a row stand: turned a little (their side showing), straight on, or turned with the one you're on facing you. */
enum class ModelAngle(val label: String) {
    FACE_PICKED("Turned, yours facing you"),
    TURNED("Turned"),
    FRONT("Straight on")
}

/** The platform key [mediaFor] hands out for a game drawn as a 3D model: this + the model's id. */
internal const val MODEL_3D = "~m3d:"

internal object Models3D {
    /**
     * The angles in the pack, in degrees (positive: turned so its left side shows, like the 3D cases). Update 166: all
     * the way round, every 20° (180 is its back) — the pieces used to stop at ±60°.
     */
    val ANGLES = IntArray(18) { -160 + it * 20 }

    /** Update 166: [yaw] brought into −180…180. */
    fun wrap(yaw: Float): Float = ((yaw % 360f) + 540f) % 360f - 180f

    /** Real pieces: Voltcap console keys → model ids. */
    private val REAL: Map<String, String> = mapOf(
        "GB" to "gb_cart_real", "GBC" to "gbc_cart_real", "GBA" to "gba_cart_real", "N64" to "n64_cart_real",
        "SNES" to "snes_us_cart_real", "SFC" to "snes_pal_sfc_cart_real",
        "Genesis" to "genesis_megadrive_cart_real", "MegaDrive" to "genesis_megadrive_cart_real",
        "GameGear" to "gamegear_cart_real", "WonderSwan" to "wonderswan_cart_real", "WonderSwanColor" to "wonderswan_cart_real",
        "NDS" to "ds_card_real", "3DS" to "3ds_card_real", "Switch" to "switch_card_real",
        "PSP" to "case_psp", "PS1" to "case_ps1_jewel", "PS2" to "case_ps2_dvd", "PS3" to "case_ps3", "PS4" to "case_ps4",
        "GameCube" to "case_gamecube", "Wii" to "case_wii", "WiiU" to "case_bluray",
        "Xbox" to "case_xbox", "Xbox360" to "case_xbox360",
        "Dreamcast" to "case_dreamcast_jewel", "Saturn" to "case_saturn_jp", "SegaCD" to "case_saturn_us",
        "PCEngineCD" to "case_ps1_jewel", "TGCD" to "case_ps1_jewel", "NeoGeoCD" to "case_ps1_jewel", "3DO" to "case_saturn_us"
    )

    /**
     * Update 141: the 3D cases: the consoles your shape library has a case for. Update 151: only the 3D models look draws
     * them (disc consoles stand as their case there, see [idFor]); Real cases and Cases · real sizes are flat for every
     * console.
     */
    private val CASES: Map<String, String> = mapOf(
        "PS1" to "case_ps1_jewel", "PS2" to "case_ps2_dvd", "PS3" to "case_ps3", "PS4" to "case_ps4", "PS5" to "case_ps5",
        "PSP" to "case_psp", "GameCube" to "case_gamecube", "Wii" to "case_wii", "WiiU" to "case_bluray",
        "Xbox" to "case_xbox", "Xbox360" to "case_xbox360", "XboxOne" to "case_xbox_one", "XboxSeries" to "case_xbox_series",
        "Dreamcast" to "case_dreamcast_jewel", "Saturn" to "case_saturn_jp", "SegaCD" to "case_saturn_us", "3DO" to "case_saturn_us",
        "PCEngineCD" to "case_ps1_jewel", "TGCD" to "case_ps1_jewel", "NeoGeoCD" to "case_ps1_jewel",
        "NDS" to "case_ds", "3DS" to "case_3ds", "Switch" to "case_switch"
    )

    /** Update 159: [id] is one of the 3D cases (its front takes the game's own scanned case front, see [Model3DArt]). */
    fun isCase(id: String): Boolean = id in CASES.values

    /** Update 141: the 3D case [platform]'s games are drawn in with a case look, when 3D models are on and it's downloaded. */
    fun caseModel(platform: String?): String? =
        if (platform == null || (Features.modelFinish.state.value == ModelFinish.OFF && !Features.caseModels3d.state.value)) null
        else CASES[platform]?.takeIf { Packs.hasModel(it) }

    /** Update 141: how a piece stands (Settings › Game look › 3D models › Stand); [focused]: it's the game you're on. */
    fun standYaw(focused: Boolean): Float = when (Features.modelAngle.state.value) {
        ModelAngle.FRONT -> 0f
        // Update 158: in both turned stands the one you're on follows "The one you're on" (PickedFacing.kt): facing you
        // by default, or turned like the others. (Turned and Turned, yours facing you are kept in step with it.)
        ModelAngle.TURNED, ModelAngle.FACE_PICKED -> if (focused && PickedFacing.faces) 0f else 20f
    }

    /** Your custom cartridges: Voltcap console keys → model ids (the clear ones add "_translucent"). */
    private val CUSTOM: Map<String, String> = mapOf(
        "Dreamcast" to "cart_dreamcast_custom", "GameCube" to "cart_gamecube_custom", "GameGear" to "cart_gamegear",
        "GBA" to "cart_gba", "GBC" to "cart_gbc_atomic_purple", "GB" to "cart_gb_classic",
        "Genesis" to "cart_genesis", "MegaDrive" to "cart_genesis", "N64" to "cart_n64",
        "PS2" to "cart_playstation_2_custom", "PS1" to "cart_playstation_custom", "PSP" to "cart_psp_custom",
        "SNES" to "cart_snes", "SFC" to "cart_snes", "WonderSwan" to "cart_wonderswan", "WonderSwanColor" to "cart_wonderswan",
        "3DS" to "card_3ds", "NDS" to "card_ds"
    )

    /**
     * Update 137: with Custom / Clear, a console without a custom piece of its own is drawn on this one, in its colours
     * with its logo (when the "logos" piece is on the device).
     */
    private const val GENERIC = "cart_snes"

    /** Update 137: the pack's logos and console colours (model_logos.zip → files/packs/models/logos/). */
    const val LOGOS = "logos"

    /** Every model of the pack (the release has one zip each: model_<id>.zip). */
    val ALL: List<String> = listOf(
        "gb_cart_real", "gbc_cart_real", "gba_cart_real", "n64_cart_real", "snes_us_cart_real", "snes_pal_sfc_cart_real",
        "genesis_megadrive_cart_real", "gamegear_cart_real", "wonderswan_cart_real", "ds_card_real", "3ds_card_real", "switch_card_real",
        "cart_dreamcast_custom", "cart_gamecube_custom", "cart_gamegear", "cart_gba", "cart_gbc_atomic_purple", "cart_gb_classic",
        "cart_genesis", "cart_n64", "cart_playstation_2_custom", "cart_playstation_custom", "cart_psp_custom", "cart_snes",
        "cart_wonderswan", "card_3ds", "card_ds",
        "memory_card_voltcap_black", "memory_card_voltcap_blue", "memory_card_voltcap_green", "memory_card_voltcap_red",
        "memory_card_voltcap_smoke", "sd_card_hq",
        "case_psp", "case_ps1_jewel", "case_ps1_pal", "case_ps2_dvd", "case_gamecube", "case_wii", "case_ps3", "case_ps4", "case_ps5",
        "case_bluray", "case_switch", "case_xbox", "case_xbox360", "case_xbox_one", "case_xbox_series", "case_dreamcast_jewel",
        "case_dreamcast_pal", "case_saturn_jp", "case_saturn_us", "case_ds", "case_ds_eu", "case_3ds",
        "disc_cd_120", "disc_dvd_120", "disc_bluray_120", "disc_gamecube_80", "umd_psp",
        "cart_dreamcast_custom_translucent", "cart_gamecube_custom_translucent", "cart_gba_translucent",
        "cart_gbc_atomic_purple_translucent", "cart_gb_classic_translucent", "cart_genesis_translucent", "cart_n64_translucent",
        "cart_playstation_2_custom_translucent", "cart_playstation_custom_translucent", "cart_psp_custom_translucent",
        "cart_snes_translucent", "cart_wonderswan_translucent", "card_3ds_translucent", "card_ds_translucent",
        "memory_card_voltcap_black_translucent", "memory_card_voltcap_blue_translucent", "memory_card_voltcap_green_translucent",
        "memory_card_voltcap_red_translucent", "memory_card_voltcap_smoke_translucent", "sd_card_hq_translucent",
        "cart_gamegear_translucent"
    )
    private val ALL_SET = ALL.toHashSet()

    private val spareIds: Set<String> = ModelSpare.values().map { it.id }.flatMap { listOf(it, it + "_translucent") }.toSet()

    /** The pack's pieces → the consoles they serve (for "only the consoles in my library"). */
    fun packFiles(): Map<String, Set<String>> {
        val out = HashMap<String, MutableSet<String>>()
        for ((k, id) in REAL) out.getOrPut(id) { HashSet() } += k
        // (Update 151: the 3D cases the disc consoles stand as.)
        for ((k, id) in CASES) out.getOrPut(id) { HashSet() } += k
        for ((k, id) in CUSTOM) { out.getOrPut(id) { HashSet() } += k; out.getOrPut(id + "_translucent") { HashSet() } += k }
        // The cards are the Save Vault's (any console's saves).
        val all = CustomCarts.allIds().keys.toSet()
        for (id in spareIds) out.getOrPut(id) { HashSet() } += all
        // (Update 137: every console's logo and colours, and the cartridge the consoles without their own are drawn on.)
        out.getOrPut(GENERIC) { HashSet() } += all
        out.getOrPut(GENERIC + "_translucent") { HashSet() } += all
        return ALL.associateWith { (out[it] ?: emptySet<String>()) } + (LOGOS to all)
    }

    /** On: games are drawn as the library's pieces (where they're downloaded). */
    fun on(): Boolean = Features.modelFinish.state.value != ModelFinish.OFF && Packs.anyModels()

    /**
     * The model [platform]'s games are drawn as with the current choice (downloaded or not), or null.
     */
    fun idFor(platform: String): String? {
        val finish = Features.modelFinish.state.value
        if (finish == ModelFinish.OFF) return null
        // Update 166: the 3D looks are cartridge looks (3D cartridges, 3D custom cartridges, 3D clear cartridges): a disc
        // console is never its 3D case here — that's the case looks' own (R1 › 3D in a case look, [caseModel]). It stands
        // as its custom cartridge (the PlayStation, GameCube, Dreamcast and PSP ones), solid with 3D cartridges.
        val clear = finish == ModelFinish.CLEAR
        // Update 137: consoles without a custom piece get the generic one in their colours with their logo (once the logos are here).
        val shape = CUSTOM[platform] ?: GENERIC.takeIf { CustomCarts.idFor(platform) != null && Packs.hasModel(LOGOS) }
        val real = REAL[platform]?.takeUnless { it.startsWith("case_") }
        if (finish == ModelFinish.REAL) return real ?: shape
        val custom = shape?.let { if (clear && (it + "_translucent") in ALL_SET) it + "_translucent" else it }
        // (Consoles without a piece of their own keep their usual look; the SD / memory cards are the Save Vault's.)
        return custom ?: real
    }

    /** The Save Vault's 3D card (Save Vault › Style › 3D card), when it's on the device. */
    fun saveCardId(): String? {
        val base = Features.saveVaultCard.state.value.id
        val id = if (Features.saveVaultClear.state.value) base + "_translucent" else base
        return id.takeIf { Packs.hasModel(it) }
    }

    /** [idFor], only when that model is on the device. */
    fun available(platform: String): String? = idFor(platform)?.takeIf { Packs.hasModel(it) }

    // ---- The pack's pictures ----

    class Meta(val w: Int, val h: Int, val artAspect: Float, val quads: Map<Int, FloatArray>, val tint: TintMeta? = null,
               /** Update 166: the back label's corners where the back faces you (the game's logo goes there), and its shape. */
               val backs: Map<Int, FloatArray> = emptyMap(), val backAspect: Float = 2f) {
        val aspect: Float get() = w.toFloat() / h.coerceAtLeast(1)
    }

    /**
     * Update 137: a paintable piece's extras: its own console ([sys], ES-DE id), clear or not (and how see-through:
     * [trans]), its grey levels (sRGB %), and per angle its logo plate's corners and how lit the plate is.
     */
    class TintMeta(val sys: String, val clear: Boolean, val trans: Float, val levels: IntArray, val logoAspect: Float,
                   val logo: Map<Int, FloatArray>, val light: Map<Int, Float>,
                   /** The Save Vault's cards: their own shell and badge colours (only the logo changes). */
                   val own: Pair<Int, Int>? = null)

    private val metas = HashMap<String, Meta>()

    /** The model's sizes and window corners (read once). */
    @Synchronized
    fun meta(id: String): Meta? {
        metas[id]?.let { return it }
        val dir = Packs.modelDir(id) ?: return null
        val m = runCatching {
            val j = org.json.JSONObject(java.io.File(dir, "meta.json").readText())
            val a = j.getJSONObject("angles")
            val q = HashMap<Int, FloatArray>()
            for (k in a.keys()) { val arr = a.getJSONArray(k); q[k.toInt()] = FloatArray(8) { arr.getDouble(it).toFloat() } }
            val t = j.optJSONObject("tint")?.let { t ->
                val lq = HashMap<Int, FloatArray>(); val lo = t.getJSONObject("logo")
                for (k in lo.keys()) { val arr = lo.getJSONArray(k); lq[k.toInt()] = FloatArray(8) { arr.getDouble(it).toFloat() } }
                val ls = HashMap<Int, Float>(); val l = t.getJSONObject("ls")
                for (k in l.keys()) ls[k.toInt()] = l.getDouble(k).toFloat()
                val lv = t.getJSONArray("levels")
                val own = t.optJSONArray("own")?.let { o -> parseHex(o.getString(0))?.let { a -> parseHex(o.getString(1))?.let { b -> a to b } } }
                TintMeta(t.getString("sys"), t.optBoolean("clear"), t.optDouble("trans", 0.0).toFloat(), IntArray(lv.length()) { lv.getInt(it) },
                    t.optDouble("logoAspect", 4.0).toFloat(), lq, ls, own)
            }
            val bq = HashMap<Int, FloatArray>()
            j.optJSONObject("backs")?.let { b -> for (k in b.keys()) { val arr = b.getJSONArray(k); bq[k.toInt()] = FloatArray(8) { arr.getDouble(it).toFloat() } } }
            Meta(j.getInt("w"), j.getInt("h"), j.optDouble("art", 1.0).toFloat(), q, t, bq, j.optDouble("backAspect", 2.0).toFloat())
        }.getOrNull()
        // (Not kept while it's missing: it may arrive with the next download.)
        if (m != null) metas[id] = m
        return m
    }

    /** Forgets what was read (the pack changed). */
    @Synchronized
    fun reset() { metas.clear(); pictures.evictAll(); composed.evictAll(); tinted.evictAll(); colors = null; logos = null }

    /** Width / height of [id]'s pictures (0.75 while unknown). */
    fun aspect(id: String): Float = meta(id)?.aspect ?: 0.75f

    /** The pack's angle nearest to [yaw] (update 166: round the circle, so −175° is its back, 180). */
    fun nearest(yaw: Float): Int {
        val y = wrap(yaw)
        return ANGLES.minByOrNull { val d = kotlin.math.abs(it - y); minOf(d, 360f - d) } ?: 0
    }

    private val pictures = object : android.util.LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }
    private val composed = object : android.util.LruCache<String, android.graphics.Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }

    private fun picture(id: String, name: String): android.graphics.Bitmap? {
        val key = "$id/$name"
        pictures.get(key)?.let { return it }
        val dir = Packs.modelDir(id) ?: return null
        val f = java.io.File(dir, "$name.webp")
        if (!f.exists()) return null
        val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
        val b = runCatching { android.graphics.BitmapFactory.decodeFile(f.path, o) }.getOrNull() ?: return null
        pictures.put(key, b)
        return b
    }

    /**
     * Update 151: where each cover picture came from (its address), so a piece already made is found again after the
     * cover was loaded anew (another picture object of the same cover) — and before it has loaded at all. Keyed by the
     * picture object; forgotten with it.
     */
    private val sources = java.util.Collections.synchronizedMap(java.util.WeakHashMap<android.graphics.Bitmap, String>())

    fun noteSource(cover: android.graphics.Bitmap, source: String) { sources[cover] = source }

    private fun coverTag(cover: android.graphics.Bitmap?): String =
        cover?.let { sources[it] ?: ("#" + System.identityHashCode(it)) } ?: "0"

    // (Update 166: [backLogo] — the game's logo on its back label — only counts at the angles that show the back.)
    private fun keyFor(id: String, yaw: Int, coverTag: String, name: String?, pos: ArtPos, tint: Tint?, backTag: String = "") =
        "$id|$yaw|$coverTag|${name.orEmpty()}|${pos.key}|${tint?.key.orEmpty()}|$backTag"

    private fun backTagOf(id: String, yaw: Int, backLogo: android.graphics.Bitmap?): String =
        if (backLogo == null || meta(id)?.backs?.containsKey(yaw) != true) "" else coverTag(backLogo)

    private fun key(id: String, yaw: Int, cover: android.graphics.Bitmap?, name: String?, pos: ArtPos, tint: Tint?, backLogo: android.graphics.Bitmap? = null) =
        keyFor(id, yaw, coverTag(cover), name, pos, tint, backTagOf(id, yaw, backLogo))

    /** The picture already made for these, if any (drawn at once, no waiting). */
    fun cached(id: String, yaw: Int, cover: android.graphics.Bitmap?, name: String? = null, pos: ArtPos = ArtPos.NONE, tint: Tint? = null,
               backLogo: android.graphics.Bitmap? = null): android.graphics.Bitmap? =
        composed.get(key(id, yaw, cover, name, pos, tint, backLogo))

    /** Update 151: the same, found by the cover's address while its picture is still loading. */
    fun cachedFor(id: String, yaw: Int, coverSource: String, name: String? = null, pos: ArtPos = ArtPos.NONE, tint: Tint? = null,
                  backLogo: android.graphics.Bitmap? = null): android.graphics.Bitmap? =
        composed.get(keyFor(id, yaw, coverSource, name, pos, tint, backTagOf(id, yaw, backLogo)))

    // ---- Update 137: painting the custom pieces ----

    /** A paintable piece's colours (opaque ARGB) and the console whose logo goes on its badge (ES-DE id; null: none). */
    class Tint(val shell: Int, val badge: Int, val logo: String?) {
        val key: String get() = "%08x.%08x.%s".format(shell, badge, logo.orEmpty())
    }

    /** Each console's shell and badge colours (ES-DE id → the pair), from the logos piece. */
    @Volatile private var colors: Map<String, Pair<Int, Int>>? = null

    private fun systemColors(): Map<String, Pair<Int, Int>> {
        colors?.let { return it }
        val dir = Packs.modelDir(LOGOS) ?: return emptyMap()
        val m = runCatching {
            val j = org.json.JSONObject(java.io.File(dir, "systems.json").readText())
            val out = HashMap<String, Pair<Int, Int>>()
            for (k in j.keys()) {
                val a = j.getJSONArray(k)
                val sh = parseHex(a.getString(0)); val bd = parseHex(a.getString(1))
                if (sh != null && bd != null) out[k] = sh to bd
            }
            out as Map<String, Pair<Int, Int>>
        }.getOrNull() ?: return emptyMap()
        colors = m
        return m
    }

    /** The consoles with a logo in the logos piece (ES-DE ids). */
    @Volatile private var logos: Set<String>? = null

    private fun logoIds(): Set<String> {
        logos?.let { return it }
        val dir = Packs.modelDir(LOGOS) ?: return emptySet()
        val ids = dir.list()?.filter { it.endsWith(".webp") }?.map { it.removeSuffix(".webp") }?.toSet() ?: return emptySet()
        logos = ids
        return ids
    }

    /** Console [sys]'s shell colour (ES-DE id), when the logos piece is here: the colour a custom cartridge starts from. */
    fun consoleColor(sys: String?): Int? = sys?.let { systemColors()[it]?.first }

    /** The ES-DE id of [platform] (a Voltcap console key). */
    fun systemOf(platform: String?): String? = platform?.let { CustomCarts.idFor(it) }

    /**
     * How [id] is painted for a game of [platform] called [name]: your colour for that game, else for all custom
     * cartridges, else its console's (and that console's logo). Null for pieces that aren't paintable, or while neither
     * a colour of yours nor the logos piece is here (then the piece shows as the library has it).
     */
    fun tintFor(id: String, platform: String?, name: String?): Tint? {
        val t = meta(id)?.tint ?: return null
        val logoOf = { s: String? -> s?.takeIf { it in logoIds() } }
        // A Save Vault card: its own colours, the save's console logo (none when that's unknown).
        t.own?.let { (sh, bd) -> return Tint(sh, bd, logoOf(systemOf(platform))) }
        val sys = systemOf(platform) ?: t.sys
        val own = systemColors()[sys] ?: systemColors()[t.sys]
        val mine = (if (platform != null && name != null) ModelColors.game(platform, name) else null) ?: ModelColors.all()
        val shell = mine ?: own?.first ?: return null
        val badge = own?.second ?: 0xFFEDF0F2.toInt()
        return Tint(shell, badge, logoOf(sys))
    }

    /** The pre-render's tone curve (ACES, then sRGB) and its inverse, as tables. */
    private object Tone {
        const val X_MAX = 7.3f
        const val N = 8192
        private fun aces(x: Float): Float = (x * (2.51f * x + 0.03f) / (x * (2.43f * x + 0.59f) + 0.14f)).coerceIn(0f, 1f)
        fun srgb(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else 1.055f * Math.pow(c.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
        fun lin(u: Float): Float = if (u <= 0.04045f) u / 12.92f else Math.pow(((u + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        /** x → 0..255 */
        val fwd = IntArray(N) { i -> (srgb(aces(i * X_MAX / (N - 1))) * 255f + 0.5f).toInt().coerceIn(0, 255) }
        /** 0..255 → x */
        val inv = FloatArray(256) { v ->
            if (v == 0) 0f else {
                val target = v / 255f
                var lo = 0f; var hi = X_MAX
                repeat(40) { val mid = (lo + hi) / 2; if (srgb(aces(mid)) < target) lo = mid else hi = mid }
                (lo + hi) / 2
            }
        }
        fun out(x: Float): Int = fwd[((x / X_MAX) * (N - 1)).toInt().coerceIn(0, N - 1)]
        val MID = lin(0.5f)
    }

    private val tinted = object : android.util.LruCache<String, android.graphics.Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }

    /** One of the pack's pictures as straight (not premultiplied) ARGB pixels, read for painting. */
    private fun pixels(id: String, name: String): Triple<IntArray, Int, Int>? {
        val dir = Packs.modelDir(id) ?: return null
        val f = java.io.File(dir, "$name.webp")
        if (!f.exists()) return null
        val o = android.graphics.BitmapFactory.Options().apply {
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888; inPremultiplied = false
        }
        val b = runCatching { android.graphics.BitmapFactory.decodeFile(f.path, o) }.getOrNull() ?: return null
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val r = Triple(px, b.width, b.height)
        b.recycle()
        return r
    }

    /** [id] at [yaw] in [t]'s colours, its logo on the badge (no cover yet); null when its pictures aren't all here. */
    private fun paint(id: String, yaw: Int, m: Meta, t: Tint): android.graphics.Bitmap? {
        val tm = m.tint ?: return null
        val k = "$id|$yaw|${t.key}"
        tinted.get(k)?.let { return it }
        val levels = tm.levels.map { pixels(id, "${yaw}_t$it") ?: return null }
        val bw = pixels(id, "${yaw}_b") ?: return null
        val (p0, w, h) = levels[0]
        if (levels.any { it.second != w || it.third != h } || bw.second != w || bw.third != h) return null
        val n = w * h
        val out = IntArray(n)
        val sh = intArrayOf((t.shell shr 16) and 255, (t.shell shr 8) and 255, t.shell and 255)
        val bd = intArrayOf((t.badge shr 16) and 255, (t.badge shr 8) and 255, t.badge and 255)
        if (!tm.clear) {
            // Solid: the shell's light is linear in its colour before the tone curve — undone, painted, put back.
            val pm = levels[1].first
            val shL = FloatArray(3) { Tone.lin(sh[it] / 255f) }; val bdL = FloatArray(3) { Tone.lin(bd[it] / 255f) }
            for (i in 0 until n) {
                val a0 = p0[i] ushr 24
                if (a0 == 0) continue
                val wgt = (bw.first[i] shr 16 and 255) / 255f
                var rgb = 0
                for (ch in 0 until 3) {
                    val sft = 16 - ch * 8
                    val x0 = Tone.inv[(p0[i] shr sft) and 255]
                    val xb = (Tone.inv[(pm[i] shr sft) and 255] - x0) / Tone.MID
                    val c = shL[ch] + (bdL[ch] - shL[ch]) * wgt
                    rgb = rgb or (Tone.out(x0 + c * xb) shl sft)
                }
                out[i] = (a0 shl 24) or rgb
            }
        } else {
            // Clear: between the nearest pre-rendered greys (their light, and how much shows through).
            val us = FloatArray(tm.levels.size) { tm.levels[it] / 100f }
            val px = levels.map { it.first }
            fun seg(u: Float): Int { var s = 0; while (s < us.size - 2 && u > us[s + 1]) s++; return s }
            for (i in 0 until n) {
                val wgt = (bw.first[i] shr 16 and 255) / 255f
                var aSum = 0f; val col = FloatArray(3)
                for (ch in 0 until 3) {
                    val u = (sh[ch] + (bd[ch] - sh[ch]) * wgt) / 255f
                    val s0 = seg(u); val f = ((u - us[s0]) / (us[s0 + 1] - us[s0])).coerceIn(0f, 1f)
                    val sft = 16 - ch * 8
                    val a = px[s0][i] ushr 24; val b = px[s0 + 1][i] ushr 24
                    // premultiplied, so a see-through part blends right
                    // Update 151: then back to this channel's own straight colour (divided by its own levels' alpha). Each
                    // channel can sit between other levels than the others, and the lighter levels turn the thick edges
                    // see-through: divided by the shared alpha, a blue card's sides and back came out olive.
                    val ac = a * (1 - f) + b * f
                    col[ch] = if (ac < 0.5f) 0f else (((px[s0][i] shr sft) and 255) * a * (1 - f) + ((px[s0 + 1][i] shr sft) and 255) * b * f) / ac
                    aSum += u
                }
                val um = aSum / 3f
                val s0 = seg(um); val f = ((um - us[s0]) / (us[s0 + 1] - us[s0])).coerceIn(0f, 1f)
                val alpha = ((px[s0][i] ushr 24) * (1 - f) + (px[s0 + 1][i] ushr 24) * f)
                if (alpha < 0.5f) continue
                var rgb = 0
                // (Update 151: col is each channel's straight colour now.)
                for (ch in 0 until 3) rgb = rgb or (col[ch].toInt().coerceIn(0, 255) shl (16 - ch * 8))
                out[i] = (alpha.toInt().coerceIn(0, 255) shl 24) or rgb
            }
        }
        // Update 151: a Save Vault card in its own colours is the library's own picture (exact, front and back one colour),
        // only its badge painted for the save's console logo — painted from the grey levels, a clear card's back and sides
        // came out another colour than its front.
        val own = tm.own
        if (own != null && own.first == t.shell) {
            val base = pixels(id, "$yaw")
            if (base != null && base.second == w && base.third == h) {
                val bp = base.first
                for (i in 0 until n) {
                    val wgt = (bw.first[i] shr 16 and 255) / 255f
                    if (wgt <= 0.004f) out[i] = bp[i] else if (wgt < 0.996f) out[i] = mixArgb(bp[i], out[i], wgt)
                }
            }
        }
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        // The console's logo, fitted in the badge's plate (96 % × 90 %), lit like the plate.
        val q = tm.logo[yaw]
        val logo = t.logo?.let { picture(LOGOS, it) }
        if (q != null && logo != null && logo.width > 0 && logo.height > 0) {
            val pw = 1000f; val ph = pw / tm.logoAspect.coerceIn(0.5f, 40f)
            val sc = minOf(pw * 0.96f / logo.width, ph * 0.90f / logo.height)
            val lw = logo.width * sc; val lh = logo.height * sc
            val mx = android.graphics.Matrix()
            val src = floatArrayOf(0f, 0f, pw, 0f, pw, ph, 0f, ph)
            val dst = FloatArray(8) { i -> q[i] * (if (i % 2 == 0) w else h) }
            if (mx.setPolyToPoly(src, 0, dst, 0, 4)) {
                mx.preTranslate((pw - lw) / 2f, (ph - lh) / 2f)
                mx.preScale(sc, sc)
                val l = (tm.light[yaw] ?: 0.85f).coerceIn(0f, 1.2f)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(l, l, l, 1f) })
                }
                android.graphics.Canvas(bmp).drawBitmap(logo, mx, paint)
            }
        }
        tinted.put(k, bmp)
        return bmp
    }

    /** Update 151: straight ARGB [a] → [b] by [t] (0–1), each channel and the alpha. */
    private fun mixArgb(a: Int, b: Int, t: Float): Int {
        var r = 0
        var s = 24
        while (s >= 0) {
            val x = (a ushr s) and 255; val y = (b ushr s) and 255
            r = r or ((x + (y - x) * t).toInt().coerceIn(0, 255) shl s)
            s -= 8
        }
        return r
    }

    private val mulPaint = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY) }

    /** Update 141: how much brighter the art shows behind clear plastic. */
    private const val CLEAR_ART_GAIN = 1.2f

    /** Update 141: thins a clear shell's own colour over the window (black, at the window light's strength × 0.55). */
    private val clearThinPaint = android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0.55f, 0f
        )))
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
    }
    private val addPaint = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD) }

    /**
     * What goes in the window, upright, in the window's shape [aspect]: [cover] cut to fit (no stretching; [pos] moves and
     * sizes it — R1 › Art position — plain paper where it leaves the window empty), a plain light card while there's none,
     * and [name] on a strip along its bottom (Settings › Game look › 3D models › Name on the label).
     */
    private fun labelPicture(cover: android.graphics.Bitmap?, aspect: Float, name: String?, pos: ArtPos): android.graphics.Bitmap {
        val lw = 480; val lh = (lw / aspect).toInt().coerceIn(96, 1440)
        val out = android.graphics.Bitmap.createBitmap(lw, lh, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(0xFFC9CCD2.toInt())
        if (cover != null && cover.width > 0 && cover.height > 0) {
            // (Update 143: Art position moves and sizes the cover itself. Update 147: it always fills the label, like a real
            // cartridge's sticker — fitting a tall cover into a wide label left grey bars at its sides.)
            val cw = cover.width.toFloat(); val ch = cover.height.toFloat()
            val fit = maxOf(lw / cw, lh / ch) * pos.zoom
            val dw = cw * fit; val dh = ch * fit
            val left = (lw - dw) / 2f + pos.x * lw * ArtPlace.REACH; val top = (lh - dh) / 2f + pos.y * lh * ArtPlace.REACH
            c.drawBitmap(
                cover, android.graphics.Rect(0, 0, cover.width, cover.height),
                android.graphics.RectF(left, top, left + dw, top + dh), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )
        }
        if (!name.isNullOrBlank()) {
            val band = (lh * 0.17f).coerceIn(34f, 90f)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.shader = android.graphics.LinearGradient(0f, lh - band * 1.5f, 0f, lh.toFloat(), 0x00000000, 0xE0101216.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, lh - band * 1.5f, lw.toFloat(), lh.toFloat(), p)
            val t = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF4F4F6.toInt(); isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
                textSize = band * 0.62f
            }
            val room = lw * 0.92f
            while (t.measureText(name) > room && t.textSize > band * 0.38f) t.textSize -= 1f
            var shown: String = name
            if (t.measureText(shown) > room) {
                while (shown.length > 2 && t.measureText("$shown…") > room) shown = shown.dropLast(1)
                shown = shown.trimEnd() + "…"
            }
            c.drawText(shown, lw / 2f, lh - band * 0.32f, t)
        }
        return out
    }

    /**
     * The model at angle [yaw] with [cover] in its window (a plain light card while there's none). Off the main
     * thread: two pictures read once, then a few draws.
     */
    fun compose(
        id: String, yaw: Int, cover: android.graphics.Bitmap?, name: String? = null, pos: ArtPos = ArtPos.NONE, tint: Tint? = null,
        /** Update 166: the game's logo, printed on the back label where the back faces you. */
        backLogo: android.graphics.Bitmap? = null
    ): android.graphics.Bitmap? {
        val m = meta(id) ?: return null
        val bq = backLogo?.let { m.backs[yaw] }
        if (backLogo == null || bq == null) return composeFront(id, yaw, cover, name, pos, tint)
        val k = key(id, yaw, cover, name, pos, tint, backLogo)
        composed.get(k)?.let { return it }
        val front = composeFront(id, yaw, cover, name, pos, tint) ?: return null
        return runCatching {
            val out = front.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val w = out.width; val h = out.height
            // Fitted (no stretching) in the label's middle, 88 % × 78 % of it, a little dimmer than paper under the light.
            val pw = 1000f; val ph = pw / m.backAspect.coerceIn(0.3f, 8f)
            val sc = minOf(pw * 0.88f / backLogo.width, ph * 0.78f / backLogo.height)
            val src = floatArrayOf(0f, 0f, pw, 0f, pw, ph, 0f, ph)
            val dst = FloatArray(8) { i -> bq[i] * (if (i % 2 == 0) w else h) }
            val mx = android.graphics.Matrix()
            if (mx.setPolyToPoly(src, 0, dst, 0, 4)) {
                mx.preTranslate((pw - backLogo.width * sc) / 2f, (ph - backLogo.height * sc) / 2f)
                mx.preScale(sc, sc)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(0.92f, 0.92f, 0.92f, 1f) })
                }
                android.graphics.Canvas(out).drawBitmap(backLogo, mx, paint)
            }
            composed.put(k, out)
            out
        }.getOrNull() ?: front
    }

    /** [compose] without the back's logo: the piece (painted) with [cover] in its window. */
    private fun composeFront(id: String, yaw: Int, cover: android.graphics.Bitmap?, name: String?, pos: ArtPos, tint: Tint?): android.graphics.Bitmap? {
        val k = key(id, yaw, cover, name, pos, tint)
        composed.get(k)?.let { return it }
        val m = meta(id) ?: return null
        // Update 137: a custom piece painted (and its logo printed) first; as the library has it when that can't be done.
        val painted = tint?.let { runCatching { paint(id, yaw, m, it) }.getOrNull() }
        val base = painted ?: picture(id, "$yaw") ?: return null
        val clearPainted = painted != null && m.tint?.clear == true
        val gain = (if (clearPainted) picture(id, "${yaw}_gw") else null) ?: picture(id, "${yaw}_g") ?: return base
        val q = m.quads[yaw] ?: return base
        val w = base.width; val h = base.height
        return runCatching {
            val layer = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val lc = android.graphics.Canvas(layer)
            val a = m.artAspect.coerceIn(0.2f, 5f)
            val label = labelPicture(cover, a, name, pos)
            lc.drawColor(android.graphics.Color.BLACK)
            // The label (the cover cut to the window's shape, its name strip) with its corners onto the window's.
            val src = floatArrayOf(0f, 0f, label.width.toFloat(), 0f, label.width.toFloat(), label.height.toFloat(), 0f, label.height.toFloat())
            val dst = FloatArray(8) { i -> q[i] * (if (i % 2 == 0) w else h) }
            val mx = android.graphics.Matrix()
            if (mx.setPolyToPoly(src, 0, dst, 0, 4)) {
                lc.drawBitmap(label, mx, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
            }
            label.recycle()
            // Times the window's light (and nothing outside the window), then added onto the model. (Behind a painted
            // clear shell: its light through white plastic — update 141: in the cover's own colours, no longer tinted
            // by the plastic, and a little brighter.)
            val gp = if (clearPainted) {
                android.graphics.Paint(mulPaint).apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(CLEAR_ART_GAIN, CLEAR_ART_GAIN, CLEAR_ART_GAIN, 1f) })
                }
            } else mulPaint
            lc.drawBitmap(gain, 0f, 0f, gp)
            val out = base.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            // Update 141: a clear shell more see-through over the art — its own colour thinned where the window's light is.
            if (clearPainted) android.graphics.Canvas(out).drawBitmap(gain, 0f, 0f, clearThinPaint)
            android.graphics.Canvas(out).drawBitmap(layer, 0f, 0f, addPaint)
            layer.recycle()
            composed.put(k, out)
            out
        }.getOrNull() ?: base
    }
}

/**
 * A game drawn as its 3D model [id] at angle [yaw] (the nearest pre-rendered one), [cover] in its window, fitted inside
 * [modifier]. [shade]: darker (games aside in the carousels).
 */
@Composable
internal fun Model3DArt(
    id: String, cover: Any?, yaw: Float, modifier: Modifier = Modifier, shade: Float = 0f, name: String? = null, alwaysName: Boolean = false,
    /** Update 137: the game's console (its colours and logo on a custom piece). */
    platform: String? = null,
    /** Update 151: a game's piece in a row, grid or carousel: drawn with more life (Models3DLife.kt); [focused]: the one you're on. */
    life: Boolean = false,
    focused: Boolean = false
) {
    // Update 159: a 3D case wears the game's own scanned case front when there is one (Original cases, on by default) —
    // the PS2 case showed only the cover, never the real case's printed front with its banner.
    @Suppress("NAME_SHADOWING")
    val cover = if (cover is String && Models3D.isCase(id) && Features.originalCases.state.value) {
        HomeScans.keyOfCover[cover]?.let { k -> BoxTexture.front(HomeScans.map[k], platform) } ?: cover
    } else cover
    if (life) {
        Model3DLive(id, cover, yaw, modifier, shade, name, alwaysName, platform, focused)
        return
    }
    val version = Packs.version.intValue
    val meta = remember(id, version) { Models3D.meta(id) }
    if (meta == null) { Box(modifier); return }
    val coverBmp = rememberSoftBitmap(cover, 640)
    val angle = Models3D.nearest(yaw)
    // Update 136: the name strip (a toggle) and Art drop (update 137: Art position, R1), both drawn into the label.
    val title = if (alwaysName || Features.modelName.state.value) name?.let { gameDisplayName(it) } else null
    val pos = ArtPlace.of(model = true)
    // Update 137: a custom piece in the game's console colours (or yours, R1 › Cartridge colour), its logo on the badge.
    ModelColors.version.intValue
    val tint = Models3D.tintFor(id, platform, name?.let { gameDisplayName(it) })
    val tk = tint?.key
    // (Update 151: while its cover is still loading, the piece made before with that cover — never an empty case first.)
    val waiting = cover != null && coverBmp == null
    val first = if (waiting && cover is String) Models3D.cachedFor(id, angle, cover, title, pos, tint) else Models3D.cached(id, angle, coverBmp, title, pos, tint)
    val bmp by produceState(first, id, angle, coverBmp, version, title, pos.key, tk) {
        if (!waiting) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Models3D.compose(id, angle, coverBmp, title, pos, tint) }
    }
    val shown = bmp
    val dim = (1f - shade).coerceIn(0f, 1f)
    val filter = remember(dim) {
        if (dim >= 0.995f) null
        else ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, dim, 0f, 0f, 0f, 0f, 0f, 1f, 0f
        )))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (shown != null) {
            val img = remember(shown) { shown.asImageBitmap() }
            Image(img, null, contentScale = ContentScale.Fit, colorFilter = filter, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Update 137: your cartridge colours (R1 › Cartridge colour): one game's (by console and name), or every custom
 * cartridge's. Without either, a custom cartridge wears its console's colour.
 */
internal object ModelColors {
    /** Bumped on every change, so the pieces repaint (read it where they're drawn). */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    private var prefs: android.content.SharedPreferences? = null
    private val map = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private const val ALL = "all"

    fun init(c: android.content.Context) {
        val p = c.applicationContext.getSharedPreferences("model_colors", android.content.Context.MODE_PRIVATE)
        prefs = p
        map.clear()
        for ((k, v) in p.all) if (v is Int) map[k] = v
        version.intValue++
    }

    private fun gameKey(platform: String, name: String) = "g:$platform|${name.trim().lowercase()}"

    /** Your colour for the game [name] of [platform] (its shown name), or null. */
    fun game(platform: String, name: String): Int? = map[gameKey(platform, name)]

    /** Your colour for every custom cartridge, or null. */
    fun all(): Int? = map[ALL]

    fun setGame(platform: String, name: String, color: Int?) = put(gameKey(platform, name), color)
    fun setAll(color: Int?) = put(ALL, color)

    private fun put(k: String, color: Int?) {
        if (color == null) map.remove(k) else map[k] = color or 0xFF000000.toInt()
        prefs?.edit()?.apply { if (color == null) remove(k) else putInt(k, color or 0xFF000000.toInt()) }?.apply()
        version.intValue++
    }
}
