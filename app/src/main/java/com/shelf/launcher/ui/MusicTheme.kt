package com.shelf.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * State behind the "Now playing" theme: the track that is playing, the colours taken from its
 * cover, and a very soft copy of the cover for the animated background.
 *
 * The home screen feeds it ([update]) while the theme is chosen; the theme (ShelfLauncherTheme) and
 * the scene (NowPlayingScene) read it. The last palette is saved, so after a restart the launcher
 * opens in the colours of the last song instead of flashing the fallback.
 */
object MusicTheme {
    private const val PREFS = "music_theme_palette"

    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set
    /** Colours from the current (or last) cover. Null until a cover has been seen. */
    var colors by mutableStateOf<ShelfColors?>(null)
        private set
    /** The cover, square, scaled down and back up so it is already very soft. */
    var softArt by mutableStateOf<Bitmap?>(null)
        private set
    /** The cover, square and sharp (up to 512 px): record / disc labels, the sleeve, the cover walls. */
    var sharpArt by mutableStateOf<Bitmap?>(null)
        private set

    /**
     * The covers of the last songs (newest first, up to [HISTORY] of them, the current one not
     * included) in one picture: a 4 × 2 grid of 192 px squares. For the scenes that show earlier
     * covers too (cover flow, record shelf, cube, polaroids). [historyCount] = how many tiles are filled.
     */
    var historyAtlas by mutableStateOf<Bitmap?>(null)
        private set
    var historyCount by mutableStateOf(0)
        private set

    /**
     * Where the cover's subject is — x, y (0..1 across the square cover) and its size (0..1, a
     * radius) — found by [findSubject]; size 0 = not known yet. Used by Pan & zoom (it zooms in on it)
     * and by the "away from the cover's subject" lyrics placement.
     */
    var subject by mutableStateOf(floatArrayOf(0.5f, 0.45f, 0f))
        private set
    /** The calmer half of the cover for text: true = the top half is calmer than the bottom. */
    var calmTop by mutableStateOf(false)
        private set
    private const val HISTORY = 8
    private const val TILE = 192
    private val history = ArrayDeque<Bitmap>()
    private var historyKeyNow: String? = null

    /** Identity of the last cover handled (not the bitmap itself: covers can be a few MB each). */
    private var lastArtId = 0
    private var lastKey: String? = null
    @Volatile private var smallMemory = false

    /** Memory is tight (a game started): forget the last cover, it's re-read when needed. */
    fun trim() {
        lastArtId = 0
    }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (colors != null || !p.contains("accent")) return
        fun c(key: String) = Color(p.getInt(key, 0))
        colors = palette(c("background"), c("accent"), c("secondary"))
    }

    /** Called whenever the playing track changes. Colours only change when the cover does. */
    suspend fun update(context: Context, playing: NowPlaying?) {
        nowPlaying = playing
        val art = playing?.art ?: return // nothing (or no cover): keep the last colours
        val key = "${playing.title}|${playing.artist}|${art.width}x${art.height}"
        val artId = System.identityHashCode(art)
        if (artId == lastArtId || key == lastKey && softArt != null) return
        smallMemory = runCatching { com.shelf.launcher.device.MemoryBudget.small(context) }.getOrDefault(false)
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val soft = soften(art)
                Triple(soft, extract(soft), sharpen(art))
            }.getOrNull()
        } ?: return
        result.third?.let { sharp ->
            withContext(Dispatchers.Default) { runCatching { findSubject(sharp) }.getOrNull() }?.let { (sub, calm) ->
                subject = sub; calmTop = calm
            }
        }
        softArt = result.first
        // The cover that was showing moves into the history (once per song).
        val previous = sharpArt
        val prevKey = historyKeyNow
        historyKeyNow = "${playing.title}|${playing.artist}"
        if (previous != null && prevKey != null && prevKey != historyKeyNow) {
            withContext(Dispatchers.Default) { runCatching { pushHistory(previous) } }
        }
        sharpArt = result.third
        // (Update 229, #03: a track change during this work left the old cover stuck) Marked done only once it is.
        lastArtId = artId
        lastKey = key
        val pal = result.second ?: return
        colors = pal
        withContext(Dispatchers.IO) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("background", pal.background.toArgb())
                .putInt("accent", pal.accent.toArgb())
                .putInt("secondary", pal.secondary.toArgb())
                .apply()
        }
    }

    /**
     * Centre square at 64 px, then a real blur (three box passes ≈ a Gaussian, radius 6 px, done on
     * the CPU in well under a millisecond). The old 16 px → 96 px upscale left soft square blocks on
     * screen; this is smooth at any size, so the background never looks "squary".
     */
    /** A very soft copy of any cover (the screensaver's background on weaker devices); null if it fails. */
    internal fun softCopy(art: Bitmap): Bitmap? = runCatching { soften(art) }.getOrNull()

    private fun soften(art: Bitmap): Bitmap {
        val src = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) else art
        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val n = 64
        val small = Bitmap.createScaledBitmap(square, n, n, true)
        val px = IntArray(n * n)
        small.getPixels(px, 0, n, 0, 0, n, n)
        val r = FloatArray(n * n) { (px[it] shr 16 and 255).toFloat() }
        val g = FloatArray(n * n) { (px[it] shr 8 and 255).toFloat() }
        val b = FloatArray(n * n) { (px[it] and 255).toFloat() }
        repeat(3) { for (ch in arrayOf(r, g, b)) { boxBlur(ch, n, 6, horizontal = true); boxBlur(ch, n, 6, horizontal = false) } }
        for (i in px.indices) {
            px[i] = (0xFF shl 24) or (r[i].toInt().coerceIn(0, 255) shl 16) or (g[i].toInt().coerceIn(0, 255) shl 8) or b[i].toInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    /**
     * Centre square of the cover, sharp: 512 px (1 MB) for the scenes that show the cover big (wall,
     * ripple, sleeve), 320 px on small-memory devices.
     */
    private fun sharpen(art: Bitmap): Bitmap? = runCatching {
        val src = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) else art
        val side = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val n = if (smallMemory) 320 else 512
        Bitmap.createScaledBitmap(square, minOf(n, side), minOf(n, side), true)
    }.getOrNull()

    /**
     * A small "where would you look" map of the cover (48 × 48): how much each spot stands out from
     * its surroundings in colour (centre against a wide blur), its edges, how colourful it is, skin
     * tones (faces and people count most), and a pull towards the middle. The strongest eighth of the
     * map, weighted, gives the subject's centre; its spread gives the size. Also tells whether the top
     * or the bottom band is calmer (fewer edges, less contrast) — the better place for text.
     */
    private fun findSubject(cover: Bitmap): Pair<FloatArray, Boolean> {
        val n = 48
        val small = Bitmap.createScaledBitmap(cover, n, n, true)
        val px = IntArray(n * n)
        small.getPixels(px, 0, n, 0, 0, n, n)
        val r = FloatArray(n * n) { (px[it] shr 16 and 255) / 255f }
        val g = FloatArray(n * n) { (px[it] shr 8 and 255) / 255f }
        val b = FloatArray(n * n) { (px[it] and 255) / 255f }
        val lum = FloatArray(n * n) { 0.299f * r[it] + 0.587f * g[it] + 0.114f * b[it] }
        // The surroundings: a wide blur of each channel.
        val br = r.copyOf(); val bg = g.copyOf(); val bb = b.copyOf()
        repeat(2) { for (ch in arrayOf(br, bg, bb)) { boxBlurN(ch, n, 7, true); boxBlurN(ch, n, 7, false) } }
        val sal = FloatArray(n * n)
        val edgeAt = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            val dr = r[i] - br[i]; val dg = g[i] - bg[i]; val db = b[i] - bb[i]
            val contrast = kotlin.math.sqrt(dr * dr + dg * dg + db * db)
            val xl = lum[y * n + (x - 1).coerceAtLeast(0)]; val xr = lum[y * n + (x + 1).coerceAtMost(n - 1)]
            val yu = lum[(y - 1).coerceAtLeast(0) * n + x]; val yd = lum[(y + 1).coerceAtMost(n - 1) * n + x]
            val edge = kotlin.math.abs(xr - xl) + kotlin.math.abs(yd - yu)
            edgeAt[i] = edge
            val mx = maxOf(r[i], g[i], b[i]); val mn = minOf(r[i], g[i], b[i])
            val satv = if (mx > 0.01f) (mx - mn) / mx else 0f
            // Skin: reddish, not too saturated, red > green > blue by sensible margins.
            val skin = if (r[i] > 0.35f && r[i] > g[i] && g[i] > b[i] && r[i] - g[i] in 0.04f..0.35f && satv in 0.15f..0.68f) 1f else 0f
            val cx = x / (n - 1f) - 0.5f; val cy = y / (n - 1f) - 0.47f
            val centre = kotlin.math.exp(-(cx * cx + cy * cy) / 0.18f)
            sal[i] = (contrast * 1.6f + edge * 0.6f + satv * 0.25f + skin * 0.55f) * (0.45f + 0.55f * centre)
        }
        val sorted = sal.sortedArray()
        val cut = sorted[(sorted.size * 0.875f).toInt().coerceAtMost(sorted.size - 1)]
        var wx = 0f; var wy = 0f; var wsum = 0f
        for (i in sal.indices) if (sal[i] >= cut) {
            val w = sal[i] - cut + 1e-4f
            wx += (i % n) * w; wy += (i / n) * w; wsum += w
        }
        val sx = if (wsum > 0f) wx / wsum / (n - 1f) else 0.5f
        val sy = if (wsum > 0f) wy / wsum / (n - 1f) else 0.45f
        var spread = 0f; var cnt = 0f
        for (i in sal.indices) if (sal[i] >= cut) {
            val dx = (i % n) / (n - 1f) - sx; val dy = (i / n) / (n - 1f) - sy
            spread += dx * dx + dy * dy; cnt += 1f
        }
        val size = (kotlin.math.sqrt(spread / maxOf(cnt, 1f)) * 1.6f).coerceIn(0.12f, 0.6f)
        // Text bands: the top and bottom thirds; the subject being there counts against them too.
        fun busy(y0: Int, y1: Int): Float {
            var s = 0f
            for (y in y0 until y1) for (x in 0 until n) { val i = y * n + x; s += edgeAt[i] + sal[i] * 0.5f }
            return s / ((y1 - y0) * n)
        }
        val top = busy(0, n / 3) + (if (sy < 0.4f) 1f else 0f)
        val bottom = busy(n - n / 3, n) + (if (sy > 0.6f) 1f else 0f)
        return floatArrayOf(sx.coerceIn(0f, 1f), sy.coerceIn(0f, 1f), size) to (top < bottom)
    }

    private fun boxBlurN(ch: FloatArray, n: Int, radius: Int, horizontal: Boolean) = boxBlur(ch, n, radius, horizontal)

    private fun pushHistory(cover: Bitmap) {
        val tile = Bitmap.createScaledBitmap(cover, TILE, TILE, true)
        history.addFirst(tile)
        while (history.size > HISTORY) history.removeLast()
        val atlas = Bitmap.createBitmap(TILE * 4, TILE * 2, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(atlas)
        history.forEachIndexed { i, b -> canvas.drawBitmap(b, ((i % 4) * TILE).toFloat(), ((i / 4) * TILE).toFloat(), null) }
        historyAtlas = atlas
        historyCount = history.size
    }

    /** One box-blur pass along rows or columns; edges repeat the border pixel. */
    private fun boxBlur(ch: FloatArray, n: Int, radius: Int, horizontal: Boolean) {
        val line = FloatArray(n)
        val w = 2 * radius + 1
        for (a in 0 until n) {
            fun at(i: Int): Float { val c = i.coerceIn(0, n - 1); return if (horizontal) ch[a * n + c] else ch[c * n + a] }
            var sum = 0f
            for (i in -radius..radius) sum += at(i)
            for (i in 0 until n) {
                line[i] = sum / w
                sum += at(i + radius + 1) - at(i - radius)
            }
            for (i in 0 until n) if (horizontal) ch[a * n + i] = line[i] else ch[i * n + a] = line[i]
        }
    }

    /**
     * A palette from the cover: hues are binned (weighted by how colourful and how common they
     * are); the strongest becomes the accent, the strongest clearly different hue the secondary,
     * and the background is a very dark tint of the cover's overall colour. Accents are pushed
     * bright and saturated so they read on the dark background whatever the cover looks like.
     */
    /** The same palette from any picture (the animated background's "colours from the game's cover"). */
    internal fun paletteOf(bitmap: Bitmap): ShelfColors? = runCatching { extract(bitmap) }.getOrNull()

    private fun extract(bitmap: Bitmap): ShelfColors? {
        val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        val bins = 18
        val weight = FloatArray(bins)
        val sumR = FloatArray(bins); val sumG = FloatArray(bins); val sumB = FloatArray(bins)
        var allR = 0f; var allG = 0f; var allB = 0f
        val hsv = FloatArray(3)
        for (c in px) {
            val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
            allR += r; allG += g; allB += b
            android.graphics.Color.colorToHSV(c, hsv)
            val s = hsv[1]; val v = hsv[2]
            if (s < 0.18f || v < 0.12f) continue // greys and near-black say nothing about the hue
            val w = s * s * (0.3f + v)
            val i = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            weight[i] += w; sumR[i] += r * w; sumG[i] += g * w; sumB[i] += b * w
        }
        val n = px.size.toFloat()
        val overall = Color(allR / n, allG / n, allB / n)
        // Smooth neighbouring bins so a hue split across two bins still wins.
        val score = FloatArray(bins) { weight[it] + 0.5f * (weight[(it + 1) % bins] + weight[(it + bins - 1) % bins]) }
        val first = score.indices.maxByOrNull { score[it] } ?: return null
        val accentBase = if (weight[first] > 0f) binColor(first, weight, sumR, sumG, sumB) else null
        var second = -1
        for (i in score.indices) {
            val dist = min(abs(i - first), bins - abs(i - first))
            if (dist >= 3 && weight[i] > 0f && (second < 0 || score[i] > score[second])) second = i
        }
        val accent = vivid(accentBase ?: overall, minSat = if (accentBase == null) 0f else 0.5f)
        val secondary = if (second >= 0 && score[second] > score[first] * 0.12f) {
            vivid(binColor(second, weight, sumR, sumG, sumB), minSat = 0.4f)
        } else {
            // One-colour cover: a lighter, shifted companion of the accent.
            shift(accent, hueBy = 32f, value = 0.98f, satScale = 0.6f)
        }
        return palette(overall, accent, secondary)
    }

    private fun binColor(i: Int, w: FloatArray, r: FloatArray, g: FloatArray, b: FloatArray) =
        Color((r[i] / w[i]).coerceIn(0f, 1f), (g[i] / w[i]).coerceIn(0f, 1f), (b[i] / w[i]).coerceIn(0f, 1f))

    private fun vivid(c: Color, minSat: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[1] = if (hsv[1] < 0.1f) hsv[1] else max(hsv[1], minSat).coerceAtMost(0.9f)
        hsv[2] = hsv[2].coerceIn(0.85f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun shift(c: Color, hueBy: Float, value: Float, satScale: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[0] = (hsv[0] + hueBy) % 360f
        hsv[1] *= satScale
        hsv[2] = value
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** The full theme palette from three colours: [tint] only sets the hue of the dark surfaces. */
    internal fun palette(tint: Color, accent: Color, secondary: Color): ShelfColors {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
        val h = hsv[0]
        val s = (hsv[1] * 0.7f).coerceAtMost(0.45f)
        fun dark(v: Float, sat: Float = s) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
        return ShelfColors(
            background = dark(0.07f),
            surface = dark(0.13f),
            surfaceRaised = dark(0.18f),
            accent = accent,
            accentGlow = accent.copy(alpha = 0.45f),
            secondary = secondary,
            textPrimary = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.04f, 0.98f))),
            textMuted = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 0.12f, 0.70f)))
        )
    }
}

/**
 * Colours for the music scenes: the album cover's, unless Appearance › Colours › Colours from is
 * "Game art" (then the app's colours, which follow the game you're on).
 */
@androidx.compose.runtime.Composable
internal fun musicSceneColors(): com.shelf.launcher.ui.theme.ShelfColors =
    if (com.shelf.launcher.data.Features.artColorSource.state.value == com.shelf.launcher.data.Features.ArtColorSource.GAME) com.shelf.launcher.ui.theme.ShelfTheme.colors
    else MusicTheme.colors ?: com.shelf.launcher.ui.theme.ShelfTheme.colors
