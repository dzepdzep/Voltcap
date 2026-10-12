package com.shelf.launcher.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shelf.launcher.ui.theme.ThemeMotif

/** The game you're on, as the scenes that show it need it (all addresses; nothing is loaded until a scene asks). */
internal data class SceneGame(
    val key: String,
    val platform: String?,
    val cover: String?,
    val hero: String?,
    /** ScreenScraper's picture of the real disc / cartridge ("Disc / cartridge"), when it has one. */
    val scan: String?,
    /** The platform's games come on discs (a UMD counts as a cartridge-like shell: it isn't round). */
    val disc: Boolean
)

/**
 * The game you're on and the covers of your row, for the scenes that show your games (set by the home screen):
 * Specimen lab and Console disc (its cartridge / disc), Poster wall (the row's covers), the console-era music
 * scenes (its art when no music plays). Pictures are decoded small, once, and kept while memory allows.
 */
internal object SceneArt {
    var game by mutableStateOf<SceneGame?>(null)
    /** Covers of the games in the row round the one you're on (that one first), at most [ATLAS_CELLS]. */
    var rowCovers by mutableStateOf<List<String>>(emptyList())

    const val ATLAS_CELLS = 8
    private const val CELL_W = 160
    private const val CELL_H = 224

    /** A picture ready for a scene: [kind] 0 = a disc picture, 1 = a cartridge / card picture, 2 = only the cover. */
    class Media(val url: String, val shader: android.graphics.BitmapShader, val w: Int, val h: Int, val kind: Int)

    /** The row's covers side by side (4 × 2 cells of 160 × 224 px). */
    class Covers(val urls: List<String>, val shader: android.graphics.BitmapShader, val w: Int, val h: Int, val count: Int)

    /** The game's art for a music scene: a very soft square (the background) and a sharp square (labels, screens). */
    class MusicArt(val key: String, val soft: Bitmap, val sharp: Bitmap)

    @Volatile private var lastMedia: Media? = null
    @Volatile private var lastCovers: Covers? = null
    @Volatile private var lastMusic: MusicArt? = null

    /** The scene motifs that read where to move their subject (uFocusY: 0.5 + a shift down). The Gaming room uses its own. */
    fun usesShift(motif: ThemeMotif): Boolean =
        motif != ThemeMotif.GAMING_ROOM && "uFocusY" in SceneSource.uniforms(motif)

    /** The console-era music scenes (Theme › Music · console eras): they can show the game's art instead of the album's. */
    val ERA_MOTIFS = setOf(ThemeMotif.FIRE_32, ThemeMotif.TOWERS_128, ThemeMotif.CARTRIDGE_16, ThemeMotif.HANDHELD, ThemeMotif.POCKET_BEAT)

    /** Scenes that show the game's own cartridge / disc. */
    val MEDIA_MOTIFS = setOf(ThemeMotif.SPECIMEN_LAB, ThemeMotif.CONSOLE_DISC)

    /**
     * 0.5 + how far down (share of the screen height) a scene's subject should move: into the open space when
     * the game row sits in the upper half (never up: a row at the bottom leaves the scene as designed), plus the
     * theme's own Scene position. [avoid] are the bands to keep clear (px, top to bottom) on a [h]-px screen.
     */
    fun shiftFocus(avoid: List<ClosedFloatingPointRange<Float>>, h: Float, user: Float): Float {
        val auto = if (avoid.isEmpty() || h <= 0f) 0f else ((openMiddle(avoid, h) - 0.5f) * 0.8f).coerceAtLeast(0f)
        return 0.5f + (auto + user).coerceIn(-0.15f, 0.3f)
    }

    private suspend fun load(context: android.content.Context, url: String, w: Int, h: Int, fill: Boolean): Bitmap? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(w, h)
                    .scale(if (fill) coil.size.Scale.FILL else coil.size.Scale.FIT)
                    .precision(coil.size.Precision.INEXACT)
                    .allowHardware(false)
                    .memoryCacheKey("scene|$url|${w}x$h|$fill")
                    .build()
                val bmp = ((coil.Coil.imageLoader(context).execute(request) as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bmp?.takeIf { !it.isRecycled }
            }.getOrNull()
        }

    /** The game's disc / cartridge picture (or its cover when there is none), about 256 px. Null when it has neither. */
    suspend fun media(context: android.content.Context, g: SceneGame): Media? {
        val url = g.scan ?: g.cover ?: return null
        val kind = if (g.scan != null) (if (g.disc) 0 else 1) else 2
        lastMedia?.takeIf { it.url == url && it.kind == kind }?.let { return it }
        val bmp = load(context, url, 256, 256, fill = false) ?: return null
        return Media(
            url,
            android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP),
            bmp.width, bmp.height, kind
        ).also { lastMedia = it }
    }

    /** The covers in one picture for the Poster wall. Null when none loaded. */
    suspend fun covers(context: android.content.Context, urls: List<String>): Covers? {
        if (urls.isEmpty()) return null
        lastCovers?.takeIf { it.urls == urls }?.let { return it }
        val bitmaps = urls.take(ATLAS_CELLS).mapNotNull { load(context, it, CELL_W, CELL_H, fill = true) }
        if (bitmaps.isEmpty()) return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val out = Bitmap.createBitmap(CELL_W * 4, CELL_H * 2, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(out)
                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                bitmaps.forEachIndexed { i, bmp ->
                    // Each cell filled, the cover's middle kept (a wide cover loses its sides).
                    val target = CELL_W.toFloat() / CELL_H
                    val have = bmp.width.toFloat() / bmp.height
                    val src = if (have > target) {
                        val w = (bmp.height * target).toInt()
                        android.graphics.Rect((bmp.width - w) / 2, 0, (bmp.width + w) / 2, bmp.height)
                    } else {
                        val h = (bmp.width / target).toInt()
                        android.graphics.Rect(0, (bmp.height - h) / 2, bmp.width, (bmp.height + h) / 2)
                    }
                    val x = (i % 4) * CELL_W
                    val y = (i / 4) * CELL_H
                    canvas.drawBitmap(bmp, src, android.graphics.Rect(x, y, x + CELL_W, y + CELL_H), paint)
                }
                Covers(
                    urls,
                    android.graphics.BitmapShader(out, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP),
                    out.width, out.height, bitmaps.size
                ).also { lastCovers = it }
            }.getOrNull()
        }
    }

    /** The game's hero (soft background) and cover (sharp square) for a console-era music scene. */
    suspend fun musicArt(context: android.content.Context, g: SceneGame): MusicArt? {
        lastMusic?.takeIf { it.key == g.key }?.let { return it }
        val backUrl = g.hero ?: g.cover ?: return null
        val frontUrl = g.cover ?: g.hero ?: return null
        val back = load(context, backUrl, 256, 256, fill = true) ?: return null
        val front = if (frontUrl == backUrl) back else load(context, frontUrl, 384, 384, fill = true) ?: back
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val soft = MusicTheme.softCopy(back) ?: return@runCatching null
                val side = minOf(front.width, front.height)
                val square = Bitmap.createBitmap(front, (front.width - side) / 2, (front.height - side) / 2, side, side)
                val sharp = if (side > 384) Bitmap.createScaledBitmap(square, 384, 384, true) else square
                MusicArt(g.key, soft, sharp)
            }.getOrNull()?.also { lastMusic = it }
        }
    }
}

/** The game's cartridge / disc picture as it loads (null until then, or when the game has no picture or cover). */
@Composable
internal fun rememberSceneMedia(): State<SceneArt.Media?> {
    val context = LocalContext.current
    val g = SceneArt.game
    return produceState<SceneArt.Media?>(null, g?.key, g?.scan, g?.cover) {
        value = if (g == null) null else SceneArt.media(context, g)
    }
}

/** The row's covers as one picture (null until loaded, or with none). */
@Composable
internal fun rememberRowCovers(): State<SceneArt.Covers?> {
    val context = LocalContext.current
    val urls = SceneArt.rowCovers
    return produceState<SceneArt.Covers?>(null, urls) { value = SceneArt.covers(context, urls) }
}

/** The game's art for a console-era music scene while [enabled] (null otherwise, or until loaded). */
@Composable
internal fun rememberGameMusicArt(enabled: Boolean): State<SceneArt.MusicArt?> {
    val context = LocalContext.current
    val g = if (enabled) SceneArt.game else null
    return produceState<SceneArt.MusicArt?>(null, g?.key, g?.hero, g?.cover) {
        value = if (g == null) null else SceneArt.musicArt(context, g)
    }
}
