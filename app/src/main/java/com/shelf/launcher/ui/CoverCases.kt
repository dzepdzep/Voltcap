package com.shelf.launcher.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features

/*
 * Flat cases from the template pack's cover templates (MediaCovers.kt): the system's own front frame with the game's
 * cover in its window, at the picture's real proportions. Seen from the front there is no spine; the system's spine
 * is only used where a case is seen from the side (shelf of spines, 3D box).
 */

/** A case is "lying down" (spine under the front) when its front is clearly wider than tall. */
private fun CoverTpl.landscape(): Boolean = frontAspect > 1.15f

/** The spine's width / height once turned the way it sits on this case (standing up, or lying under a landscape front). */
private fun CoverTpl.spineShape(): Float {
    val s = spineAspect.coerceAtLeast(0.01f)
    return if (landscape()) (if (s >= 1f) s else 1f / s) else (if (s <= 1f) s else 1f / s)
}

/** Width / height of the whole case: the front alone, or front and spine together. */
internal fun CoverTpl.caseAspect(withSpine: Boolean): Float {
    if (!withSpine) return frontAspect
    val s = spineShape()
    return if (landscape()) 1f / (1f / frontAspect + 1f / s) else frontAspect + s
}

/** Turns a spine picture to stand up ([landscape] false) or lie down: a lying one stands up reading top to bottom. */
internal fun orientSpine(b: android.graphics.Bitmap, landscape: Boolean): android.graphics.Bitmap {
    if ((b.width > b.height) == landscape) return b
    val m = android.graphics.Matrix().apply { postRotate(if (landscape) -90f else 90f) }
    return runCatching { android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true) }.getOrDefault(b)
}

/**
 * Update 298 (Toni: "the PS1 Legend of Dragoon case showed its title twice down the side"): some scraped spine scans
 * ("box-2D-side") are both end flaps of a jewel case's back insert side by side — two copies of the spine. [gray] is the
 * upright scan sampled 16 wide × 64 tall (row by row, 0…255): true when its left and right halves are the same strip
 * (average difference under 33) and it isn't a plain colour (spread over 15). One real spine's halves differ far more
 * (measured, each cell averaged: 40–87 on single PS1, DS, GBC, N64, GameCube and PSP spines; 24–27 on the doubled PS1 one).
 */
internal fun isTwinSpine(gray: IntArray): Boolean {
    if (gray.size != 16 * 64) return false
    var diff = 0L
    for (y in 0 until 64) for (x in 0 until 8) diff += kotlin.math.abs(gray[y * 16 + x] - gray[y * 16 + x + 8])
    val mean = gray.sum() / gray.size.toFloat()
    var spread = 0f
    for (v in gray) spread += kotlin.math.abs(v - mean)
    return diff / 512f < 33f && spread / gray.size > 15f
}

/** Update 298: an upright spine scan that is two copies of the spine side by side ([isTwinSpine]) cut to one; else itself. */
internal fun singleSpine(b: android.graphics.Bitmap): android.graphics.Bitmap = runCatching {
    if (b.width < 16 || b.height < 64 || b.width.toFloat() / b.height < 0.07f) return b
    val soft = if (b.config == android.graphics.Bitmap.Config.HARDWARE) b.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else b
    val g = IntArray(16 * 64)
    // (Each cell the average of 4 × 4 points in it — single points landed on or off thin lettering by chance.)
    for (y in 0 until 64) for (x in 0 until 16) {
        var sum = 0
        for (sy in 0 until 4) for (sx in 0 until 4) {
            val p = soft.getPixel(((x + (sx + 0.5f) / 4f) * soft.width / 16).toInt().coerceIn(0, soft.width - 1), ((y + (sy + 0.5f) / 4f) * soft.height / 64).toInt().coerceIn(0, soft.height - 1))
            sum += (((p shr 16) and 255) * 30 + ((p shr 8) and 255) * 59 + (p and 255) * 11) / 100
        }
        g[y * 16 + x] = sum / 16
    }
    if (isTwinSpine(g)) android.graphics.Bitmap.createBitmap(soft, 0, 0, soft.width / 2, soft.height) else b
}.getOrDefault(b)

/** The picture's average colour (solid pixels only), sampled on a grid. */
internal fun averageColor(b: android.graphics.Bitmap): Int {
    var r = 0L; var g = 0L; var bl = 0L; var n = 0L
    val stepX = (b.width / 12).coerceAtLeast(1); val stepY = (b.height / 24).coerceAtLeast(1)
    var y = 0
    while (y < b.height) {
        var x = 0
        while (x < b.width) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) > 200) { r += (p shr 16) and 255; g += (p shr 8) and 255; bl += p and 255; n++ }
            x += stepX
        }
        y += stepY
    }
    if (n == 0L) return 0xFF1C1E26.toInt()
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
}

/** A spine picture laid over its own average colour, so transparent bits of the pack's spine don't leave holes. */
internal fun flatOnAverage(b: android.graphics.Bitmap): android.graphics.Bitmap {
    val out = android.graphics.Bitmap.createBitmap(b.width, b.height, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    c.drawColor(averageColor(b))
    c.drawBitmap(b, 0f, 0f, null)
    return out
}

/**
 * The console's spine turned to stand up (or lie down) and flattened, with the ink that reads on it; null for the moment
 * it is first being made. Update 134: made once per console (not once per game on the shelf) and away from the screen's
 * thread ([SpineCache]) — every spine used to decode and flatten its own copy while the shelf was drawn, which lagged.
 */
@Composable
internal fun rememberSpine(res: Int, landscape: Boolean): SpineCache.Ready? {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val key = SpineCache.key(res, landscape, null, com.shelf.launcher.data.Packs.version.intValue)
    val now = SpineCache.peekReady(key)
    val made by androidx.compose.runtime.produceState<SpineCache.Ready?>(null, key) {
        // (Kept here too, so a spine pushed out of the cache later never leaves this one blank.)
        value = SpineCache.peekReady(key) ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SpineCache.ready(context, res, landscape, key) }
    }
    return now ?: made?.takeIf { it.key == key }
}

/**
 * Update 134: a console spine with a game's [title] written into it (null: none) and turned to stand up (or lie down), as
 * the 3D boxes and cases use it; null while it's being made (away from the screen's thread, kept for the next time).
 */
@Composable
internal fun rememberTitledSpine(res: Int?, title: String?, landscape: Boolean, accentFrom: Any? = null): SpineCache.Titled {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // (Update 305: Voltcap's own Steam / Android spine is drawn for the game — OwnSpines — with its cover's accent colour,
    // so the cover is part of its key.)
    val own = OwnSpines.isOwn(res)
    val key = if (res == null) "" else SpineCache.key(res, landscape, title, com.shelf.launcher.data.Packs.version.intValue) +
        (if (own) "|own2|" + (accentFrom?.toString() ?: "") else "")
    val now = if (res == null) null else SpineCache.peekTitled(key)
    val made by androidx.compose.runtime.produceState<Pair<String, android.graphics.Bitmap?>?>(null, key) {
        if (res != null) {
            value = key to (SpineCache.peekTitled(key) ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (own) SpineCache.own(context, res, title, OwnSpines.accentFor(context, accentFrom), landscape, key)
                else SpineCache.titled(context, res, title, landscape, key)
            })
        }
    }
    val got = made?.takeIf { it.first == key }
    return when {
        res == null -> SpineCache.Titled(true, null)
        now != null -> SpineCache.Titled(true, now)
        got != null -> SpineCache.Titled(true, got.second)
        // (Update 348: while it's being made, the console's spine without the name — not a dark strip flashing in.)
        else -> SpineCache.Titled(false, if (own) null else SpineCache.peekUntitled(res, landscape))
    }
}

/**
 * Update 134: the console spines, decoded and flattened once each (and the games' titled ones, kept while there's room),
 * made on a background thread. Pictures stay the same until your spine pack changes (its version is in every key).
 */
internal object SpineCache {
    class Ready(val key: String, val image: ImageBitmap, val ink: Color)
    /** A titled spine: [done] once it has been tried ([bitmap] null then: there's no picture for it). */
    data class Titled(val done: Boolean, val bitmap: android.graphics.Bitmap?)
    /** The plain dark strip shown while a spine is being made. */
    val WAITING: android.graphics.Bitmap by lazy {
        android.graphics.Bitmap.createBitmap(8, 120, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF1C1E26.toInt()) }
    }

    private val readyMap = android.util.LruCache<String, Ready>(64)
    // The titled ones, by size (about 24 MB).
    // (Update 348, Toni: "flashing on custom spines when scrolling through them": 24 MB held about thirty of your pack's
    // titled spines, so scrolling a long Case flow made them again and again — a dark strip each time. Up to 48 MB where the
    // app has the memory for it.)
    private val titledMap = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).coerceIn(24L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.allocationByteCount
    }
    /** Update 348: each console spine stood up without a name — shown while a game's titled one is being made. */
    private val untitled = android.util.LruCache<String, android.graphics.Bitmap>(24)
    fun peekUntitled(res: Int, landscape: Boolean): android.graphics.Bitmap? =
        untitled.get("$res|$landscape|" + com.shelf.launcher.data.Packs.version.intValue)
    private val flat = android.util.LruCache<String, android.graphics.Bitmap>(64)

    fun key(res: Int, landscape: Boolean, title: String?, version: Int): String = "$res|$landscape|$version|${title ?: ""}"

    fun peekReady(key: String): Ready? = readyMap.get(key)
    fun peekTitled(key: String): android.graphics.Bitmap? = titledMap.get(key)

    /** The console's picture laid on its own average colour, upright as it comes (decoded once). */
    @Synchronized
    private fun flatOf(context: android.content.Context, res: Int): android.graphics.Bitmap? {
        val k = res.toString() + "|" + com.shelf.launcher.data.Packs.version.intValue
        flat.get(k)?.let { return it }
        val src = runCatching {
            if (res >= 0) android.graphics.BitmapFactory.decodeResource(context.resources, res)
            else PackSpines.load(res).asAndroidBitmap().takeIf { it.width > 1 }
        }.getOrNull() ?: return null
        val out = runCatching { flatOnAverage(src) }.getOrNull() ?: return null
        flat.put(k, out)
        return out
    }

    @Synchronized
    fun ready(context: android.content.Context, res: Int, landscape: Boolean, key: String): Ready? {
        readyMap.get(key)?.let { return it }
        val f = flatOf(context, res) ?: return null
        val img = orientSpine(f, landscape)
        val c = averageColor(f)
        val lum = ((c shr 16) and 255) * 0.3f + ((c shr 8) and 255) * 0.59f + (c and 255) * 0.11f
        val r = Ready(key, img.asImageBitmap(), if (lum > 150f) Color(0xFF1A1A1E) else Color.White)
        readyMap.put(key, r)
        return r
    }

    /** Update 305: a Steam / Android game's own spine (OwnSpines), kept like the titled ones. */
    @Synchronized
    fun own(context: android.content.Context, res: Int, title: String?, accent: Int?, landscape: Boolean, key: String): android.graphics.Bitmap? {
        titledMap.get(key)?.let { return it }
        val out = runCatching { orientSpine(OwnSpines.compose(context, res, title, accent), landscape) }.getOrNull() ?: return null
        titledMap.put(key, out)
        return out
    }

    @Synchronized
    fun titled(context: android.content.Context, res: Int, title: String?, landscape: Boolean, key: String): android.graphics.Bitmap? {
        titledMap.get(key)?.let { return it }
        val f = flatOf(context, res) ?: return null
        val uk = "$res|$landscape|" + com.shelf.launcher.data.Packs.version.intValue
        if (untitled.get(uk) == null) runCatching { untitled.put(uk, orientSpine(f, landscape)) }
        val tpl = CustomSpines.byRes(res)
        val named = if (tpl != null && !title.isNullOrBlank()) runCatching { spineWithTitle(f, tpl, title) }.getOrDefault(f) else f
        val out = orientSpine(named, landscape)
        titledMap.put(key, out)
        return out
    }
}

/**
 * Update 131: your spine pack's pictures are downloaded files, not resources. Each gets a stand-in id below zero (so the
 * spines keep working by "res" everywhere, the 3D boxes included); [spineImage] loads either kind.
 */
internal object PackSpines {
    private val ids = HashMap<String, Int>()
    private val names = HashMap<Int, String>()
    private val cache = android.util.LruCache<Int, ImageBitmap>(24)

    @Synchronized fun idFor(file: String): Int = ids.getOrPut(file) { (-1000 - ids.size).also { names[it] = file } }

    /** The picture behind a stand-in [id] (decoded once, kept for a while), or a 1 px stand-in when it can't be read. */
    fun load(id: Int): ImageBitmap {
        cache.get(id)?.let { return it }
        val file = synchronized(this) { names[id] }?.let { com.shelf.launcher.data.Packs.spineFile(it) }
        val bmp = file?.let { vcTrace("VC:spineDecode") { runCatching { android.graphics.BitmapFactory.decodeFile(it.path) }.getOrNull() } }
        val img = bmp?.asImageBitmap() ?: ImageBitmap(1, 1)
        if (bmp != null) cache.put(id, img)
        return img
    }
}

/** A spine picture by its id: a resource (Voltcap's own spines) or your downloaded pack's ([PackSpines]). */
@Composable
internal fun spineImage(res: Int): ImageBitmap =
    if (res >= 0) com.shelf.launcher.ui.cachedImageResource(res) else remember(res, com.shelf.launcher.data.Packs.version.intValue) { PackSpines.load(res) }

/**
 * Update 169: a case's front drawn to its cover's shape: the frame cut in three along one axis — the band before the
 * window, the window, the band after it — and only the window's part stretched, so the bands keep their look and size
 * and the window takes the cover's width / height. [aspect]: the whole front's new width / height; [win]: the window's
 * new bounds (l, t, r, b); [vertical]: stretched up / down (else sideways).
 */
internal class CaseFit(val aspect: Float, val win: FloatArray, val vertical: Boolean)

/** The front reshaped so its window has [coverAspect] (width / height); null when it already has (within 3 %). */
internal fun CoverTpl.fitTo(coverAspect: Float): CaseFit? {
    val a = frontAspect.coerceAtLeast(0.05f); val w = win
    val ww = (w[2] - w[0]).coerceAtLeast(0.01f); val wh = (w[3] - w[1]).coerceAtLeast(0.01f)
    val winAspect = a * ww / wh
    if (kotlin.math.abs(coverAspect / winAspect - 1f) < 0.03f) return null
    // The window reaches both sides (Steam / Android: a band only on top): it changes height either way, so the band is
    // never stretched; one reaching top and bottom changes width. Else: taller covers grow it down, wider ones sideways.
    val fullWidth = w[0] <= 0.01f && w[2] >= 0.99f
    val fullHeight = w[1] <= 0.01f && w[3] >= 0.99f
    val vertical = if (fullWidth != fullHeight) fullWidth else coverAspect < winAspect
    return if (vertical) {
        // The window's height set by the cover (heights in widths of the front).
        val top = w[1] / a; val bottom = (1f - w[3]) / a; val h = ww / coverAspect
        val t = top + h + bottom
        CaseFit(1f / t, floatArrayOf(w[0], top / t, w[2], (top + h) / t), true)
    } else {
        // The window's width set by the cover (widths in heights of the front).
        val left = w[0] * a; val right = (1f - w[2]) * a; val wd = wh * coverAspect
        val t = left + wd + right
        CaseFit(t, floatArrayOf(left / t, w[1], (left + wd) / t, w[3]), false)
    }
}

/** SteamGridDB's covers (the "grid" art): 600 × 900. */
private const val SGDB_COVER = 2f / 3f

/**
 * Update 171: whether a cover of shape [artAspect] is already the whole printed front of [cov]'s case — a box scan used
 * as the cover (its own band, logo and seals), so putting it in the template's window printed the console's band twice
 * (a Game Boy Color box inside the Game Boy Color frame). Told by its shape: the case front's own (within 4.5 %), and
 * not SteamGridDB's 2:3 (a Saturn case is nearly that shape). Never for Voltcap's own Steam / Android cases.
 */
internal fun isBoxFront(cov: CoverTpl, artAspect: Float?): Boolean {
    if (cov.isOwn()) return false
    return looksLikeBoxFront(cov.frontAspect, artAspect)
}

/** Update 202: [isBoxFront]'s shape test for a case front of [frontAspect] (the template boxes' framed fronts use it too). */
internal fun looksLikeBoxFront(frontAspect: Float, artAspect: Float?): Boolean {
    val a = artAspect ?: return false
    if (a <= 0f || frontAspect <= 0f) return false
    if (kotlin.math.abs(a / SGDB_COVER - 1f) < 0.02f) return false
    // (Update 317, Toni: N64 fronts "bigger than the box" with the Covers look: an N64 box-front scan a little off the
    // box's shape (5–10 %, its border or the scan's crop) counted as a plain cover and was cut into the template's window,
    // zoomed and its edges lost. A landscape box's front is never mistaken for a cover — covers stand upright — so it is
    // let off further.)
    val slack = if (frontAspect > 1.15f && a > 1.15f) 0.15f else 0.045f
    return kotlin.math.abs(a / frontAspect - 1f) < slack
}

/**
 * Update 202: an Android game / app — its case is always Voltcap's own (the green ANDROID band, cov_front_android): there
 * is no retail case to show, so a "box" scraped for it (ScreenScraper's Android entries are its store art) is never the
 * case and its "cartridge" picture never what lies in it. On the Thor WWE All Stars stood as a plain white picture in
 * the case stacks (its scraped box drawn as a scanned case) and a bare cover came out of its opened case.
 */
internal fun androidOwnCase(platform: String?): Boolean = platform == "App" || platform == "Android"

/**
 * Update 202: whether [platform]'s case (as drawn, R1 › Case included) prints its console's band and logo — every case
 * but the PlayStation's, which is drawn without its black band (update 141 / 143). The console badge stays off it then.
 */
internal fun caseShowsConsole(platform: String?): Boolean =
    LookStyles.casePlatform(platform) != "PS1" &&
        // (Update 235, #27/#38: Voltcap's plain case (PlainCase) has no console band printed on it: the badge stays.)
        plainCaseSpec(platform) == null

/**
 * (Update 235, #24: the covers you picked yourself — Game settings › Artwork, a SteamGridDB pick, a scraper's alternative
 * or a picture of yours: ArtOverride.cover, which only those picks write — by game. A picked cover is your media choice
 * for the game, so in the case looks it goes into the case template (CoverCase / the template box / PlainCase) instead
 * of the game's scraped box scan; with Original cases on the scan always won and your cover only showed while the scan
 * loaded. Read from ArtOverridesStore's own file and kept up to date as it changes, so every look that draws a case can
 * ask by the game's key (typedArtFor) or by the cover itself (CaseArt) without being handed the overrides.)
 */
internal object UserCovers {
    /** ArtOverridesStore's preferences file (its overrides_json). */
    private const val PREFS = "art_overrides"

    private class Picks(val byKey: Map<String, String>, val keysOf: Map<String, List<String>>)

    private val picks = androidx.compose.runtime.mutableStateOf(Picks(emptyMap(), emptyMap()))
    @Volatile private var started = false
    /** Held here: SharedPreferences keeps its listeners only weakly. */
    private var listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Reads the picks once (then follows every change to them). Cheap after the first call. */
    fun ensure(context: android.content.Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            val app = context.applicationContext
            val store = com.shelf.launcher.data.ArtOverridesStore(app)
            fun load() {
                val byKey = runCatching { store.loadAll().mapNotNull { (k, o) -> o.cover?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap() }
                    .getOrDefault(emptyMap())
                picks.value = Picks(byKey, byKey.entries.groupBy({ it.value }, { it.key }))
            }
            load()
            val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> load() }
            listener = l
            runCatching { app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(l) }
        }
    }

    /**
     * The cover you picked for game [gameKey], or null (none picked — or a box front picked for it too, Game settings ›
     * Artwork › Box front: that pick is made for the case looks, so its scan stays the case front).
     */
    fun of(gameKey: String?): String? {
        if (gameKey == null) return null
        val c = picks.value.byKey[gameKey] ?: return null
        return c.takeIf { com.shelf.launcher.data.PhysicalOverrides.inUse(gameKey)["Box"] == null }
    }

    /** Whether [art] is a cover you picked (for a game without its own box front pick, see [of]). */
    fun isPicked(art: Any?): Boolean {
        val s = art as? String ?: return false
        val keys = picks.value.keysOf[s] ?: return false
        return keys.any { com.shelf.launcher.data.PhysicalOverrides.inUse(it)["Box"] == null }
    }
}

/** (Update 235, #24: [UserCovers] read from this screen's context.) */
@Composable
internal fun EnsureUserCovers() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    UserCovers.ensure(ctx)
}

/*
 * (Update 235, #27/#38: Voltcap's plain case. The consoles without a cover template (MediaCovers) or a template pack box
 * (MediaTemplates) — PlayStation 3 / 4 / 5, Xbox, Xbox 360, Xbox One, Xbox Series, Switch 2 (keep / card cases), Neo Geo CD,
 * PC Engine CD and CD-i (CD jewel cases) — showed a flat cover (or ScreenScraper's already turned "Box (3D)" picture, cut
 * square) in the case looks. They're drawn in a plain case of their real case's shape and plastic instead (CaseCatalog:
 * the standard keep case sizes — Blu-ray 135 × 171.5 mm, DVD 135 × 190 mm — and the CD jewel case's 142 × 125 mm), the
 * cover (or with Original cases its scanned front) under the case's clear sleeve. With Cover templates off they stay
 * plain covers, as every console's case does then.)
 */

private val plainCatalog = java.util.concurrent.ConcurrentHashMap<String, Any>()
private val NO_PLAIN = Any()

/** [platform]'s plain case (its real case, CaseCatalog), or null when it has a template case, no retail case, or Cover templates are off. */
internal fun plainCaseSpec(platform: String?): CaseSpec? {
    if (platform == null || !Features.coverTemplates.state.value) return null
    val p = LookStyles.casePlatform(platform) ?: return null
    if (MediaTemplates.of(p)?.box != null || MediaCovers.of(p) != null) return null
    val found = plainCatalog.getOrPut(p) {
        CaseCatalog.of(p, CaseRegion.US)?.takeIf { it.shell == CaseShell.KEEP || it.shell == CaseShell.CARDCASE || it.shell == CaseShell.JEWEL } ?: NO_PLAIN
    }
    return found as? CaseSpec
}

/** The art window under the clear sleeve (l, t, r, b): a keep / card case's sleeve is nearly its whole front, the hinge on the left. */
private val KEEP_WINDOW = floatArrayOf(0.035f, 0.018f, 0.988f, 0.982f)
/** A jewel case: the booklet under the clear lid, the ridged hinge down the left. */
private val JEWEL_WINDOW = floatArrayOf(0.085f, 0.03f, 0.975f, 0.97f)

/** The plastic as seen from the front: a see-through case darker and deeper in its colour, a jewel case its tray through the lid. */
private fun plainRim(spec: CaseSpec): Int =
    if (spec.shell == CaseShell.JEWEL) {
        val light = (android.graphics.Color.red(spec.tray) * 299 + android.graphics.Color.green(spec.tray) * 587 + android.graphics.Color.blue(spec.tray) * 114) / 1000 > 150
        if (light) CaseInside.mix(spec.tray, 0xFF8E949E.toInt(), 0.35f) else CaseInside.mix(spec.tray, 0xFF5A5F68.toInt(), 0.18f)
    } else CaseInside.mix(0xFF2A2C30.toInt(), spec.plastic, 0.45f + 0.5f * spec.opacity)

/**
 * (Update 235, #27/#38: Voltcap's plain case seen from the front, for a console with no template case ([plainCaseSpec]):
 * its real case's shape and plastic, [art] filling the window under the clear sleeve (CSS object-fit: cover, as in the
 * template cases), the hinge down the left, the plastic sheen and shadow the other flat cases have. [fallback] (the
 * cover) shows when [art] (a scan) can't be loaded.)
 */
@Composable
internal fun PlainCase(
    spec: CaseSpec, art: Any?, placeholder: Brush, modifier: Modifier = Modifier, caseModifier: Modifier = Modifier,
    fallback: Any? = null
) {
    val aspect = spec.aspect.coerceIn(0.4f, 2.5f)
    val jewel = spec.shell == CaseShell.JEWEL
    val rim = remember(spec) { Color(plainRim(spec)) }
    val win = if (jewel) JEWEL_WINDOW else KEEP_WINDOW
    var failed by remember(art) { androidx.compose.runtime.mutableStateOf(false) }
    val shown = if (failed) fallback else art ?: fallback
    var shownIn by remember(shown) { androidx.compose.runtime.mutableStateOf(false) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * aspect)
        val h = w / aspect
        Box(Modifier.size(w, h).caseShadow().then(caseModifier)) {
            // The shell: its plastic, the hinge groove down the left (a jewel case's ridged hinge).
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.width * (if (jewel) 0.012f else 0.022f))
                drawRoundRect(rim, cornerRadius = r)
                val hinge = size.width * win[0]
                val stroke = 1.dp.toPx().coerceAtLeast(1f)
                if (jewel) {
                    for (i in 1..4) {
                        val x = hinge * i / 5f
                        drawLine(Color.White.copy(alpha = 0.16f), Offset(x, size.height * 0.02f), Offset(x, size.height * 0.98f), stroke)
                        drawLine(Color.Black.copy(alpha = 0.35f), Offset(x + stroke, size.height * 0.02f), Offset(x + stroke, size.height * 0.98f), stroke)
                    }
                } else {
                    val x = hinge * 0.55f
                    drawLine(Color.Black.copy(alpha = 0.38f), Offset(x, 0f), Offset(x, size.height), stroke)
                    drawLine(Color.White.copy(alpha = 0.14f), Offset(x + stroke, 0f), Offset(x + stroke, size.height), stroke)
                }
            }
            Box(
                Modifier.offset(w * win[0], h * win[1]).size(w * (win[2] - win[0]), h * (win[3] - win[1]))
                    .clipToBounds().then(if (shown != null && shownIn) Modifier else Modifier.background(placeholder))
            ) {
                if (shown != null) AsyncImage(
                    cardArtModel(shown), null, contentScale = ContentScale.Crop, alignment = Alignment.Center,
                    onSuccess = { r -> shownIn = true; (shown as? String)?.let { s -> r.painter.intrinsicSize.let { sz -> CoverAspects.note(s, sz.width, sz.height) } } },
                    onError = { if (shown == art && fallback != null && fallback != art) failed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            CaseSheen(Modifier.fillMaxSize())
        }
    }
}

/**
 * Update 169: how [cov] is reshaped for [art] (Settings › Game look › Covers, cases & discs): Voltcap's own Steam /
 * Android cases always fit their cover (a cover not loaded yet counts as SteamGridDB's 2:3, so it rarely moves), the
 * consoles' only with "Console cases too" and once the cover's shape is known. Null: drawn as it is.
 */
internal fun caseFitFor(cov: CoverTpl, art: Any?): CaseFit? {
    val own = cov.isOwn()
    // (Update 171: a box scan as the cover is the whole front already.)
    if (isBoxFront(cov, CoverAspects.rawOf(art as? String))) return null
    if (own) { if (!com.shelf.launcher.data.Features.ownCaseFit.state.value) return null }
    else if (!com.shelf.launcher.data.Features.allCaseFit.state.value) return null
    val url = art as? String ?: return null
    val ca = CoverAspects.of(url) ?: if (own) SGDB_COVER else return null
    // (Update 240, #27/#58: a console's case is reshaped to its cover only a little — within about a sixth of its real
    // shape. A wide picture handed in as the cover (a landscape PAL / Japanese box, a side scan) made the case take its
    // shape outright: an N64 box went half as wide again, flattened. Past that the cover is cut to the case's window.)
    return cov.fitTo(ca)?.takeIf { f -> own || (f.aspect / cov.frontAspect) in 0.85f..1.18f }
}

/** The front's width / height for [art]: reshaped to its cover ([caseFitFor]) or as drawn. */
internal fun CoverTpl.frontAspectFor(art: Any?): Float = caseFitFor(this, art)?.aspect ?: frontAspect

/** SteamGridDB's cover shape, for the 3D cases (they don't know the cover's own when their shape is set). */
internal const val SGDB_COVER_ASPECT = SGDB_COVER

/**
 * Update 169: [frame] (the template's front picture) redrawn reshaped by [fit] onto a bitmap [w] wide — for the 3D
 * cases, which draw their front from one picture.
 */
internal fun fittedFrameBitmap(frame: android.graphics.Bitmap, cov: CoverTpl, fit: CaseFit, w: Int): android.graphics.Bitmap {
    val h = (w / fit.aspect).toInt().coerceAtLeast(1)
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    val src = cov.win; val dst = fit.win
    val s0 = if (fit.vertical) src[1] else src[0]; val s1 = if (fit.vertical) src[3] else src[2]
    val d0 = if (fit.vertical) dst[1] else dst[0]; val d1 = if (fit.vertical) dst[3] else dst[2]
    val iw = frame.width; val ih = frame.height
    for (cut in listOf(floatArrayOf(0f, s0, 0f, d0), floatArrayOf(s0, s1, d0, d1), floatArrayOf(s1, 1f, d1, 1f))) {
        if (cut[1] - cut[0] <= 0f || cut[3] - cut[2] <= 0f) continue
        if (fit.vertical) c.drawBitmap(
            frame, android.graphics.Rect(0, (cut[0] * ih).toInt(), iw, (cut[1] * ih).toInt().coerceAtLeast((cut[0] * ih).toInt() + 1)),
            android.graphics.RectF(0f, cut[2] * h, w.toFloat(), cut[3] * h), p
        ) else c.drawBitmap(
            frame, android.graphics.Rect((cut[0] * iw).toInt(), 0, (cut[1] * iw).toInt().coerceAtLeast((cut[0] * iw).toInt() + 1), ih),
            android.graphics.RectF(cut[2] * w, 0f, cut[3] * w, h.toFloat()), p
        )
    }
    return out
}

/** Update 169: the frame drawn reshaped ([CaseFit]): its bands as they are, its window's part stretched. */
@Composable
private fun FittedFrame(cov: CoverTpl, fit: CaseFit) {
    val img = com.shelf.launcher.ui.cachedImageResource(cov.frontRes)
    val src = cov.win; val dst = fit.win
    val s0 = if (fit.vertical) src[1] else src[0]; val s1 = if (fit.vertical) src[3] else src[2]
    val d0 = if (fit.vertical) dst[1] else dst[0]; val d1 = if (fit.vertical) dst[3] else dst[2]
    val cuts = listOf(floatArrayOf(0f, s0, 0f, d0), floatArrayOf(s0, s1, d0, d1), floatArrayOf(s1, 1f, d1, 1f))
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val iw = img.width; val ih = img.height
        val ow = size.width.toInt().coerceAtLeast(1); val oh = size.height.toInt().coerceAtLeast(1)
        for (c in cuts) {
            if (c[1] - c[0] <= 0f || c[3] - c[2] <= 0f) continue
            if (fit.vertical) {
                val sy = (c[0] * ih).toInt(); val sh = ((c[1] * ih).toInt() - sy).coerceAtLeast(1)
                val dy = (c[2] * oh).toInt(); val dh = ((c[3] * oh).toInt() - dy).coerceAtLeast(1)
                drawImage(
                    img, androidx.compose.ui.unit.IntOffset(0, sy), androidx.compose.ui.unit.IntSize(iw, sh),
                    androidx.compose.ui.unit.IntOffset(0, dy), androidx.compose.ui.unit.IntSize(ow, dh),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
                )
            } else {
                val sx = (c[0] * iw).toInt(); val sw = ((c[1] * iw).toInt() - sx).coerceAtLeast(1)
                val dx = (c[2] * ow).toInt(); val dw = ((c[3] * ow).toInt() - dx).coerceAtLeast(1)
                drawImage(
                    img, androidx.compose.ui.unit.IntOffset(sx, 0), androidx.compose.ui.unit.IntSize(sw, ih),
                    androidx.compose.ui.unit.IntOffset(dx, 0), androidx.compose.ui.unit.IntSize(dw, oh),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
                )
            }
        }
    }
}

/**
 * The cover template's front: the game's art behind the frame, filling the frame's window. Update 202: like CSS
 * object-fit: cover — scaled evenly until it covers the whole window, centred, only the overflow cut; never stretched,
 * never shown whole between bars or over a blurred copy of itself.
 */
@Composable
private fun CoverFace(cov: CoverTpl, art: Any?, placeholder: Brush, modifier: Modifier, fit: CaseFit? = null) {
    // Update 141: another console's case picked for every game (R1 › Case): only its shape — its frame in one plain
    // colour, nothing printed on it — with the game's own console's logo in the frame's biggest band.
    val owner = LocalCaseOwner.current
    BoxWithConstraints(modifier) {
        val w = maxWidth; val h = maxHeight
        // Update 171: a box scan used as the cover is the whole front (its own band and logo): it fills the case, unframed.
        val boxFront = owner == null && isBoxFront(cov, CoverAspects.rawOf(art as? String))
        // (Update 169: the window where the reshaped front has it.)
        val win = if (boxFront) floatArrayOf(0f, 0f, 1f, 1f) else fit?.win ?: cov.win
        // Update 136, Art drop / update 137, Art position (R1 › Art position): the cover moved in the window (down under
        // the frame's top band, up, sideways) and sized, the window cutting it off.
        val pos = ArtPlace.of(model = false)
        // Update 154: round a cover shown whole, the case's own colour (not the game-colour placeholder).
        val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
        val frameColor = remember(cov.frontRes) { FrameColors.of(ctx, cov) }
        // Update 202: an app's icon (a bitmap, not a cover) is the only picture still shown whole in the window.
        val icon = art is android.graphics.Bitmap
        // Update 202: once the cover is drawn nothing may show round it: the case colour / placeholder behind the window
        // only while it loads, for an app's icon, or when your Art position (R1) has moved or shrunk the cover. On the
        // Portal the GameCube Zelda's 2:3 cover sat between dark strips of this colour ("a frame inside the frame").
        var artShown by remember(art) { androidx.compose.runtime.mutableStateOf(false) }
        val moved = ArtPlace.moved(model = false)
        Box(
            Modifier.offset(w * win[0], h * win[1]).size(w * (win[2] - win[0]), h * (win[3] - win[1]))
                .clipToBounds().then(
                    when {
                        art != null && artShown && !icon && !moved -> Modifier
                        art != null && owner == null -> Modifier.background(frameColor)
                        else -> Modifier.background(placeholder)
                    }
                )
        ) {
            // Update 202: no soft, blurred copy of the cover behind it any more (it showed beside a cover drawn whole):
            // the cover itself covers the window (ContentScale.Crop below).
            // (Update 143: Art position moves and sizes the cover itself. Update 144: the cover fills the window — no
            // empty strips at its sides, as the Android case had.) Update 202: plain fill-crop (object-fit: cover): scaled
            // evenly until it covers the window, centred, only the overflow cut — never stretched (the 13 % stretch and
            // the "shown whole between bars past 16 %" rule are gone). A cover of the window's own shape loses nothing.
            if (art != null) AsyncImage(
                // (Update 147: the card's own request — a set size, so a cover already in memory shows in the first frame,
                // and its small copy while it loads; the case used to go blank for a moment every time it came back on screen.)
                // (Update 202: an app's icon — not a cover — is cut at most a little, else shown whole: CropLittleOrFit.)
                cardArtModel(art), null, contentScale = if (icon) CropLittleOrFit else ContentScale.Crop, alignment = Alignment.Center,
                // (Update 169: its shape noted, so the case can take it — caseFitFor.)
                onSuccess = { r -> artShown = true; if (art is String) r.painter.intrinsicSize.let { sz -> CoverAspects.note(art, sz.width, sz.height) } },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = pos.zoom; scaleY = pos.zoom
                    translationX = pos.x * size.width * ArtPlace.REACH; translationY = pos.y * size.height * ArtPlace.REACH
                }
            )
        }
        if (owner == null && boxFront) { /* (Update 171: the cover is the printed front itself — no frame over it.) */ }
        else if (owner == null && fit != null) FittedFrame(cov, fit)
        else if (owner == null) Image(com.shelf.launcher.ui.cachedPainterResource(cov.frontRes), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        else PlainFrame(cov, owner, w, h)
    }
}

/**
 * Update 147: [art] as a card's image request when it's an address (a set decode size, its small copy as the stand-in,
 * found in memory at once), else as it is (an app icon bitmap, a file).
 */
@Composable
internal fun cardArtModel(art: Any): Any = if (art is String) rememberCardArtRequest(art, ArtKind.COVER) else art

/*
 * Update 202: FillUnlessMuchCut is gone. It stretched a cover up to 13 % to fill a case window, cut up to 16 %, and
 * past that showed the cover whole over a blurred copy of itself: covers came out squashed or between strips. Covers
 * now fill their window like ContentScale.Crop (object-fit: cover): even scale, centred, only the overflow cut. A
 * SteamGridDB 2:3 cover loses ~7 % of its width in a GameCube window, ~17 % in a PS2 one, never its proportions.
 */

/**
 * Update 202: only for a picture that isn't a cover (an app's icon on its case): fills the window like Crop when that
 * cuts at most 6 %, else shows it whole (the case's colour round it). The same even scale both ways: never stretched.
 */
private object CropLittleOrFit : ContentScale {
    private const val MAX_CUT = 0.06f
    override fun computeScaleFactor(srcSize: androidx.compose.ui.geometry.Size, dstSize: androidx.compose.ui.geometry.Size): androidx.compose.ui.layout.ScaleFactor {
        if (srcSize.width <= 0f || srcSize.height <= 0f || dstSize.width <= 0f || dstSize.height <= 0f) return androidx.compose.ui.layout.ScaleFactor(1f, 1f)
        val sx = dstSize.width / srcSize.width; val sy = dstSize.height / srcSize.height
        val fill = maxOf(sx, sy); val fit = minOf(sx, sy)
        val k = if (fit / fill >= 1f - MAX_CUT) fill else fit
        return androidx.compose.ui.layout.ScaleFactor(k, k)
    }
}

/**
 * Update 141: the game whose case is drawn in another console's shape (R1 › Case picks one for all) — its own console
 * key — or null for a game in its own case. Set by [CaseArt].
 */
internal val LocalCaseOwner = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** The frame's plain colour: the average of its printed band (outside the art window), worked out once per picture. */
private object FrameColors {
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    fun of(context: android.content.Context, cov: CoverTpl): Color = Color(cache.getOrPut(cov.frontRes) {
        runCatching {
            val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4; inScaled = false }
            val b = android.graphics.BitmapFactory.decodeResource(context.resources, cov.frontRes, o) ?: return@runCatching 0xFF1C1C1F.toInt()
            var r = 0L; var g = 0L; var bl = 0L; var n = 0L
            val wi = cov.win
            for (y in 0 until b.height) for (x in 0 until b.width) {
                val fx = x / b.width.toFloat(); val fy = y / b.height.toFloat()
                if (fx >= wi[0] && fx <= wi[2] && fy >= wi[1] && fy <= wi[3]) continue
                val p = b.getPixel(x, y)
                if ((p ushr 24) < 200) continue
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
            }
            b.recycle()
            if (n == 0L) 0xFF1C1C1F.toInt() else (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
        }.getOrDefault(0xFF1C1C1F.toInt())
    })
}

/** The case's frame as a plain shape in its own colour, with [owner]'s logo in its biggest band (none: no logo). */
@Composable
private fun PlainFrame(cov: CoverTpl, owner: String, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val color = remember(cov.frontRes) { FrameColors.of(context, cov) }
    val win = cov.win
    // Only what lies outside the art window, so the console's marks printed over the art (ratings, publisher) go.
    Image(
        com.shelf.launcher.ui.cachedPainterResource(cov.frontRes), null, contentScale = ContentScale.FillBounds,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color, androidx.compose.ui.graphics.BlendMode.SrcIn),
        modifier = Modifier.fillMaxSize().drawWithContent {
            val path = androidx.compose.ui.graphics.Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRect(androidx.compose.ui.geometry.Rect(size.width * win[0], size.height * win[1], size.width * win[2], size.height * win[3]))
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        }
    )
    // The biggest band round the window (left, top, right, bottom) gets the logo.
    val bands = listOf(
        floatArrayOf(0f, 0f, win[0], 1f), floatArrayOf(0f, 0f, 1f, win[1]),
        floatArrayOf(win[2], 0f, 1f, 1f), floatArrayOf(0f, win[3], 1f, 1f)
    )
    val band = bands.maxByOrNull { (it[2] - it[0]) * w.value * (it[3] - it[1]) * h.value } ?: return
    val bw = w * (band[2] - band[0]); val bh = h * (band[3] - band[1])
    if (bw < 6.dp || bh < 6.dp) return
    val dark = color.red * 0.3f + color.green * 0.59f + color.blue * 0.11f < 0.55f
    val ink = if (dark) Color.White else Color(0xFF16161A)
    // A tall, narrow band (down the side): the logo runs along it.
    val tall = bh > bw * 1.6f
    Box(Modifier.offset(w * band[0], h * band[1]).size(bw, bh), contentAlignment = Alignment.Center) {
        val lw = if (tall) bh * 0.8f else bw * 0.8f
        val lh = if (tall) bw * 0.62f else bh * 0.62f
        PlatformLogo(
            owner, com.shelf.launcher.data.ConsoleNames.display(owner),
            Modifier.requiredSize(lw, lh).graphicsLayer { if (tall) rotationZ = -90f },
            tint = ink, textColor = ink
        )
    }
}

/**
 * The game as a flat case seen from the front: [art] (its cover) in the system's own cover frame, at the frame's real
 * proportions, filling [modifier] as far as those proportions allow. The spine is never put beside a front: it only
 * shows where the case is seen from the side (the 3D box, the shelf of spines). [caseModifier] goes on the case itself
 * (focus ring, glow), so it hugs the case and not the cell around it.
 */
@Composable
internal fun CoverCase(
    cov: CoverTpl, art: Any?, placeholder: Brush, modifier: Modifier = Modifier,
    caseModifier: Modifier = Modifier
) {
    // Update 169: reshaped to the cover (Steam / Android cases; the consoles' with "Console cases too"), not when it's
    // drawn as another console's plain shape (R1 › Case).
    val fit = if (LocalCaseOwner.current == null) caseFitFor(cov, art) else null
    val aspect = fit?.aspect ?: cov.frontAspect
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * aspect)
        val h = w / aspect
        Box(Modifier.size(w, h).caseShadow().then(caseModifier)) {
            CoverFace(cov, art, placeholder, Modifier.fillMaxSize(), fit)
            CaseSheen(Modifier.fillMaxSize())
        }
    }
}

/**
 * The game's own scanned case seen from the front (update 101): the scan itself is the case (its printed banner and all),
 * at [aspect] (the console's real case proportions), with the plastic sheen and a shadow. [onFail] is called when the
 * scan can't be loaded or cut, so the caller can fall back to the template case.
 */
@Composable
internal fun ScanCase(
    scan: Any, aspect: Float, modifier: Modifier = Modifier, caseModifier: Modifier = Modifier, onFail: () -> Unit = {}, cover: Any? = null,
    /** Update 143: this share of the scan's width is cut off its left side (a PlayStation cover's band, see [PS1_BAND]). */
    cutLeft: Float = 0f,
    /** (Update 240, #27/#58: the game's console, for its real case's shape (CaseCatalog.frontAspect) and the full-scan cut.) */
    platform: String? = null
) {
    // Update 186: the scan's own shape once it's known (CoverAspects), so nothing of it is cut off — a GoldenEye box lost
    // the "G" when drawn at the template's shape. Kept near the template's (a wrong scan can't make a strange case).
    // (Update 240, #27/#58: kept near the real case's shape — CaseCatalog, the console in your region, and the template's —
    // within 8 %, no longer a third, where that shape is known (not a jewel case): a wide or wrong "box" scan (a PAL / Japanese box, a side or a whole unfolded scan) made
    // GoldenEye's N64 box up to 1.82 wide for 1.37, a flattened case among the others. The scan is cut to that shape.
    // A scan far wider than the case — the whole unfolded box, back | spine | front — has its front cut out (BoxTexture).)
    val key = scan.toString()
    val raw = CoverAspects.rawOf(key)
    val real = if (cutLeft > 0f) null else CaseCatalog.frontAspect(platform)
    // (A jewel case's front scan, or a PlayStation one cut at its band, keeps the old leeway: a booklet or the whole case.)
    val lo = if (real != null) minOf(aspect, real) * 0.92f else aspect * 0.75f
    val hi = if (real != null) maxOf(aspect, real) * 1.08f else aspect * 1.33f
    val unfolded = platform != null && cutLeft <= 0f && scan is String && BoxTexture.parse(scan) == null &&
        raw != null && raw > hi * UNFOLDED_SCAN
    val shown: Any = if (unfolded) BoxTexture.model(scan as String, BoxPart.FRONT, platform) else scan
    val shownKey = shown.toString()
    val own = (if (unfolded) CoverAspects.rawOf(shownKey) else raw)?.let { r -> (r * (1f - cutLeft.coerceIn(0f, 0.5f))).coerceIn(lo, hi) }
    val shape = own ?: aspect
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * shape)
        val h = w / shape
        // Update 119: the game's cover (usually already loaded) while its case scan comes in — never a dark box first.
        var scanIn by androidx.compose.runtime.remember(shown) { androidx.compose.runtime.mutableStateOf(false) }
        // Update 202: the grey placeholder only until the scan is in: a scan with see-through corners (or one a hair off
        // the box's shape) never shows grey round it once drawn — the scan itself fills the box (Crop, centred).
        Box(Modifier.size(w, h).caseShadow().then(caseModifier).then(if (scanIn) Modifier else Modifier.background(CaseArtPlaceholder)).then(if (cutLeft > 0f) Modifier.clipToBounds() else Modifier)) {
            if (cover != null && !scanIn) AsyncImage(cardArtModel(cover), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            AsyncImage(
                cardArtModel(shown), null, contentScale = ContentScale.Crop, alignment = if (cutLeft > 0f) Alignment.CenterEnd else Alignment.Center,
                // (Update 240: the front cut's own shape under its own address — the scan's stays the whole scan's.)
                onSuccess = { r -> scanIn = true; r.painter.intrinsicSize.let { sz -> CoverAspects.note(shownKey, sz.width, sz.height) } },
                onError = { onFail() },
                modifier = if (cutLeft > 0f) Modifier.fillMaxHeight().wrapContentWidth(Alignment.End, unbounded = true).width(w / (1f - cutLeft.coerceIn(0f, 0.5f)))
                else Modifier.fillMaxSize()
            )
            CaseSheen(Modifier.fillMaxSize())
        }
    }
}

/** Update 143: how much of a PlayStation (PS1) cover scan's width its black "PlayStation" band down the left takes; cut off. */
internal const val PS1_BAND = 0.0956f

/** The plastic: a soft sheen across the case and a thin lit edge, so it reads as a real case, not a flat picture. */
@Composable
private fun CaseSheen(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.05f)), start = Offset.Zero, end = Offset(size.width, size.height)))
        drawRect(Color.White.copy(alpha = 0.22f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

/** True when [games] come from more than one console (then "Box art" cards are the plain covers, all one size). */
internal fun mixedConsoles(games: List<com.shelf.launcher.data.Game>): Boolean {
    val first = games.firstOrNull()?.platform ?: return false
    return games.any { it.platform != first }
}

/** A soft drop shadow under a free-standing case. */
private fun Modifier.caseShadow(): Modifier = this.drawBehind {
    drawRect(Color.Black.copy(alpha = 0.32f), Offset(2.dp.toPx(), 4.dp.toPx()), size)
}

/**
 * A spine picture for a console: the picture (standing up), and the box the game's name is printed in (fractions l, t, r,
 * b of the picture) with its ink (null: dark or white, whichever reads on the picture).
 */
internal class SpineTpl(
    val res: Int, val nameBox: FloatArray, val ink: Color? = null,
    /** Update 126: the picture's own width / height, so the shelves give it a slot of its shape (nothing cut off). */
    val aspect: Float = 0.0737f
)

/**
 * Update 125: which spine a game without its real spine scan gets. Real spine scans always come first (GameSpine); then
 * your own console spines ([yours]: your spine pack — update 131: downloaded, data/Packs.kt — with the title area you left
 * for the name and the text colour you picked); then Steam's spine; else the plain spine with the name. The template
 * pack's console spines are gone (at your request).
 */
internal object CustomSpines {
    /** Your console spines: console key to its picture, the name's box (left, top, right, bottom as shares) and its ink. */
    private val meta: Map<String, SpineMeta> = mapOf(
        "32X" to SpineMeta("sega32x", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "3DO" to SpineMeta("3do", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFFFFFFFF), 0.0887f),
        "3DS" to SpineMeta("n3ds", floatArrayOf(0.0972f, 0.1642f, 0.9028f, 0.8909f), Color(0xFF161616), 0.0882f),
        "Amiga" to SpineMeta("amiga", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Arcade" to SpineMeta("arcade", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Atari2600" to SpineMeta("atari2600", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Atari7800" to SpineMeta("atari7800", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Atomiswave" to SpineMeta("atomiswave", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "C64" to SpineMeta("c64", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF202020), 0.1125f),
        "ColecoVision" to SpineMeta("colecovision", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "DOS" to SpineMeta("dos", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Dreamcast" to SpineMeta("dreamcast", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFF202020), 0.0887f),
        "FBNeo" to SpineMeta("fbneo", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "FDS" to SpineMeta("fds", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Famicom" to SpineMeta("famicom", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "GB" to SpineMeta("gb", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFF242424), 0.1071f),
        "GBA" to SpineMeta("gba", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "GBC" to SpineMeta("gbc", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFF222222), 0.1071f),
        "GameCube" to SpineMeta("gc", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFFFFFFFF), 0.0737f),
        "GameGear" to SpineMeta("gamegear", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "Genesis" to SpineMeta("megadrive", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Intellivision" to SpineMeta("intellivision", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Jaguar" to SpineMeta("atarijaguar", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "Lynx" to SpineMeta("atarilynx", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "MAME" to SpineMeta("mame", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "MSX" to SpineMeta("msx", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "MasterSystem" to SpineMeta("mastersystem", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF181818), 0.1125f),
        "MegaDrive" to SpineMeta("megadrive", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "N64" to SpineMeta("n64", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "NDS" to SpineMeta("nds", floatArrayOf(0.1000f, 0.1647f, 0.9000f, 0.8907f), Color(0xFF111111), 0.1200f),
        "NES" to SpineMeta("nes", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF202020), 0.1125f),
        "Naomi" to SpineMeta("naomi", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "NeoGeo" to SpineMeta("neogeo", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "NeoGeoCD" to SpineMeta("neogeocd", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFFFFFFFF), 0.0887f),
        "NeoGeoPocket" to SpineMeta("ngpc", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "NeoGeoPocketColor" to SpineMeta("ngpc", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "PC" to SpineMeta("pc", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFFFFFFFF), 0.0737f),
        "PCEngine" to SpineMeta("pcengine", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF1E1E1E), 0.1125f),
        "PCEngineCD" to SpineMeta("pcenginecd", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFF1E1E1E), 0.0887f),
        "PICO8" to SpineMeta("pico8", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "PS1" to SpineMeta("psx", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF111111), 0.1125f),
        "PS2" to SpineMeta("ps2", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFF111111), 0.0737f),
        "PS3" to SpineMeta("ps3", floatArrayOf(0.0962f, 0.1647f, 0.9038f, 0.8904f), Color(0xFFFFFFFF), 0.0760f),
        "PS4" to SpineMeta("ps4", floatArrayOf(0.0962f, 0.1647f, 0.9038f, 0.8904f), Color(0xFFFFFFFF), 0.0760f),
        "PSP" to SpineMeta("psp", floatArrayOf(0.0952f, 0.1647f, 0.9048f, 0.8902f), Color(0xFFFFFFFF), 0.0824f),
        "PSVita" to SpineMeta("psvita", floatArrayOf(0.1000f, 0.1642f, 0.9000f, 0.8907f), Color(0xFFFFFFFF), 0.0741f),
        "Pico8" to SpineMeta("pico8", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "PokemonMini" to SpineMeta("pokemini", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "SFC" to SpineMeta("sfc", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF252525), 0.1125f),
        "SNES" to SpineMeta("snes", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF252525), 0.1125f),
        "Saturn" to SpineMeta("saturn", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFFFFFFFF), 0.0887f),
        "ScummVM" to SpineMeta("scummvm", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "SegaCD" to SpineMeta("megacd", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFFFFFFFF), 0.0887f),
        "Switch" to SpineMeta("switch", floatArrayOf(0.0952f, 0.1646f, 0.9048f, 0.8903f), Color(0xFFFFFFFF), 0.0652f),
        "TG16" to SpineMeta("tg16", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFF1E1E1E), 0.1125f),
        "TGCD" to SpineMeta("tg_cd", floatArrayOf(0.0960f, 0.1645f, 0.9040f, 0.8908f), Color(0xFF1E1E1E), 0.0887f),
        "Vectrex" to SpineMeta("vectrex", floatArrayOf(0.0972f, 0.1646f, 0.9028f, 0.8901f), Color(0xFFFFFFFF), 0.1125f),
        "VirtualBoy" to SpineMeta("virtualboy", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "Wii" to SpineMeta("wii", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFF222222), 0.0737f),
        "WiiU" to SpineMeta("wiiu", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFFFFFFFF), 0.0737f),
        "WonderSwan" to SpineMeta("wonderswancolor", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "WonderSwanColor" to SpineMeta("wonderswancolor", floatArrayOf(0.1000f, 0.1649f, 0.9000f, 0.8905f), Color(0xFFFFFFFF), 0.1071f),
        "Xbox" to SpineMeta("xbox", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFFFFFFFF), 0.0737f),
        "Xbox360" to SpineMeta("xbox360", floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color(0xFF111111), 0.0737f),
    )

    /** Update 131: your spine pack is downloaded (data/Packs.kt): a picture's name, its title box, ink and shape. */
    private class SpineMeta(val file: String, val box: FloatArray, val ink: Color, val aspect: Float)

    /** Every picture of your spine pack and the consoles it is for (the downloads). */
    fun packFiles(): Map<String, Set<String>> = meta.entries.groupBy({ it.value.file }, { it.key }).mapValues { it.value.toSet() }

    /** Your spines on the device (each picture's id stands for its file, see [PackSpines]), rebuilt when the packs change. */
    private var cache: Pair<Int, Map<String, SpineTpl>>? = null
    private val yours: Map<String, SpineTpl>
        get() {
            val v = com.shelf.launcher.data.Packs.version.intValue
            cache?.let { if (it.first == v) return it.second }
            val m = meta.mapNotNull { (k, sm) ->
                if (com.shelf.launcher.data.Packs.spineFile(sm.file) == null) null
                else k to SpineTpl(PackSpines.idFor(sm.file), sm.box, sm.ink, sm.aspect)
            }.toMap()
            cache = v to m
            return m
        }

    /**
     * Steam's (your Steam template) and Android's (Voltcap's own): the name below the logo and its line. Update 126:
     * Android's spine is redrawn like your pack's (logo plaque on top, the title box, the small logo at the bottom) and
     * is used for Android games and apps (it came after the pack's two Android spines before).
     */
    private val builtIn: Map<String, SpineTpl> = mapOf(
        "Steam" to SpineTpl(com.shelf.launcher.R.drawable.cov_spine_steam, floatArrayOf(0.06f, 0.31f, 0.94f, 0.985f), Color.White, 0.0872f),
        "App" to SpineTpl(com.shelf.launcher.R.drawable.cov_spine_android, floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color.White, 0.0737f),
        "Android" to SpineTpl(com.shelf.launcher.R.drawable.cov_spine_android, floatArrayOf(0.0952f, 0.1649f, 0.9048f, 0.8904f), Color.White, 0.0737f)
    )

    fun of(platform: String?): SpineTpl? = platform?.let { yours[it] ?: builtIn[it] }

    /** Update 129: every console with a spine here (R1 › Spine tile). */
    fun keys(): List<String> = (yours.keys + builtIn.keys).toList()

    /** The spine whose picture is [res] (the 3D box knows only the picture), or null. */
    fun byRes(res: Int?): SpineTpl? = if (res == null) null else yours.values.firstOrNull { it.res == res } ?: builtIn.values.firstOrNull { it.res == res }
}

/**
 * Update 125: [src] (a standing spine picture) with [title] written into [tpl]'s name box, reading top to bottom like
 * a real spine. The type is as big as the box allows and gets smaller for long names; "…" only when even the smallest
 * size doesn't fit.
 */
internal fun spineWithTitle(src: android.graphics.Bitmap, tpl: SpineTpl, title: String): android.graphics.Bitmap {
    if (title.isBlank()) return src
    val out = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return src
    val b = tpl.nameBox
    // The box in the picture; turned for a lying picture (wider than tall), so the name always runs along the spine.
    val lying = out.width > out.height
    val left = b[0] * out.width; val top = b[1] * out.height
    val bw = (b[2] - b[0]) * out.width; val bh = (b[3] - b[1]) * out.height
    val along = if (lying) bw else bh
    val across = if (lying) bh else bw
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = (tpl.ink ?: androidx.compose.ui.graphics.Color.White).let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
        isFakeBoldText = true
        textSize = across * 0.62f
    }
    val room = along * 0.94f
    val smallest = across * 0.26f
    while (p.measureText(title) > room && p.textSize > smallest) p.textSize -= 1f
    var t = title
    if (p.measureText(t) > room) {
        while (t.length > 2 && p.measureText("$t…") > room) t = t.dropLast(1)
        t = t.trimEnd() + "…"
    }
    val fm = p.fontMetrics
    val c = android.graphics.Canvas(out)
    c.save()
    if (lying) {
        c.translate(left + (bw - p.measureText(t)) / 2f, top + bh / 2f - (fm.ascent + fm.descent) / 2f)
    } else {
        // Standing: turned a quarter clockwise, centred in the box.
        c.translate(left + bw / 2f, top + (bh - p.measureText(t)) / 2f)
        c.rotate(90f)
        c.translate(0f, -(fm.ascent + fm.descent) / 2f)
    }
    c.drawText(t, 0f, 0f, p)
    c.restore()
    return out
}

/**
 * The console's spine standing alone (a shelf of spines): its picture at its real proportions, [content] over it inside
 * the spine's name box (the ink colour passed on).
 */
@Composable
internal fun SpinePicture(tpl: SpineTpl, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.(Color) -> Unit = {}) {
    val made = rememberSpine(tpl.res, landscape = false)
    val ink = tpl.ink ?: made?.ink ?: Color.White
    BoxWithConstraints(modifier) {
        // Update 126: never cropped (a slot narrower than the picture used to cut its logos off at the sides): the
        // shelves now give each spine a slot of its own shape (SpineTpl.aspect), so filling it is the picture as it is
        // (the Spines look's wider slots stretch it, as before).
        // (Update 134: a plain dark strip for the moment the console's picture is first being made.)
        if (made != null) Image(made.image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Color(0xFF1C1E26)))
        val b = tpl.nameBox
        Box(
            Modifier.offset(maxWidth * b[0], maxHeight * b[1]).size(maxWidth * (b[2] - b[0]), maxHeight * (b[3] - b[1])),
            contentAlignment = Alignment.Center
        ) { content(ink) }
    }
}

/**
 * Update 124: a spine on a shelf is never wider than this share of its height (Game shelf, TV room). Update 126: up to
 * your pack's chunkiest spines (DS 0.12), so none of them is cut to fit.
 */
internal const val SPINE_MAX_ASPECT = 0.125f

/** A spine's width / height as it stands on a shelf (always standing up). */
internal fun CoverTpl.shelfSpineAspect(): Float {
    val s = spineAspect.coerceAtLeast(0.01f)
    return if (s <= 1f) s else 1f / s
}

/** Which picture goes on a box's front and whether it's a ready-made box scan: covers go into the system's cover template. */
internal fun boxFront(tpl: BoxTpl, cover: Any?, scan: Any?): Pair<Any?, Boolean> =
    if (tpl.frameRes != null && Features.coverTemplates.state.value && cover != null) cover to false
    else if (scan != null) scan to true
    else cover to false
