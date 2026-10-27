package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.shelf.launcher.ui.theme.ThemeMotif

/**
 * The painted scenes: themes that are the user's own paintings (res/drawable-nodpi/scene_*.webp, 1672 × 941) with
 * motion added on top by their shader (tools/themes/scenes: aurora, meadow, px_dojo and nine chill_* scenes, which sample uPaint). Each
 * painting is decoded once when its theme shows and kept while memory allows (half size on low-memory devices),
 * like the Gaming room's.
 */
internal class ScenePainting private constructor(val shader: android.graphics.BitmapShader, val w: Int, val h: Int) {
    companion object {
        /** The painting each painted motif shows. */
        fun resOf(motif: ThemeMotif): Int? = when (motif) {
            ThemeMotif.AURORA -> com.shelf.launcher.R.drawable.scene_aurora
            ThemeMotif.MEADOW -> com.shelf.launcher.R.drawable.scene_meadow
            ThemeMotif.PX_DOJO -> com.shelf.launcher.R.drawable.scene_dojo
            // Chill home screens painted by Toni (update 73).
            ThemeMotif.CHILL_PEAKS -> com.shelf.launcher.R.drawable.scene_chill_peaks
            ThemeMotif.CHILL_SILK -> com.shelf.launcher.R.drawable.scene_chill_silk
            ThemeMotif.CHILL_POLAR -> com.shelf.launcher.R.drawable.scene_chill_polar
            ThemeMotif.CHILL_MOON -> com.shelf.launcher.R.drawable.scene_chill_moon
            ThemeMotif.CHILL_DUNES -> com.shelf.launcher.R.drawable.scene_chill_dunes
            ThemeMotif.CHILL_GLADE -> com.shelf.launcher.R.drawable.scene_chill_glade
            ThemeMotif.CHILL_CLOUDS -> com.shelf.launcher.R.drawable.scene_chill_clouds
            ThemeMotif.CHILL_KOI -> com.shelf.launcher.R.drawable.scene_chill_koi
            ThemeMotif.CHILL_CITY -> com.shelf.launcher.R.drawable.scene_chill_city
            else -> null
        }

        private val cache = HashMap<ThemeMotif, java.lang.ref.SoftReference<ScenePainting>>()

        fun cached(motif: ThemeMotif): ScenePainting? = synchronized(cache) { cache[motif]?.get() }

        /** Decodes it now (off the main thread). Null when the motif has no painting or it can't be read. */
        fun decode(context: android.content.Context, motif: ThemeMotif): ScenePainting? {
            cached(motif)?.let { return it }
            val res = resOf(motif) ?: return null
            return runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = if (com.shelf.launcher.device.MemoryBudget.small(context)) 2 else 1
                }
                val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, res, opts) ?: return null
                val shader = android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                // Smooth when it's scaled to the screen (1672 px wide paintings on 1080p / 1440p screens).
                if (android.os.Build.VERSION.SDK_INT >= 33) shader.filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
                ScenePainting(shader, bmp.width, bmp.height).also { synchronized(cache) { cache[motif] = java.lang.ref.SoftReference(it) } }
            }.getOrNull()
        }

        suspend fun load(context: android.content.Context, motif: ThemeMotif): ScenePainting? =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decode(context, motif) }
    }
}

/** The painting for [motif] as it decodes (null until then). */
@Composable
internal fun rememberPainting(motif: ThemeMotif): State<ScenePainting?> {
    val context = LocalContext.current
    // (update 91) Always this motif's painting: produceState keeps the last value when the motif changes, so a
    // second painted theme used to keep showing the first one's painting.
    return produceKeyed(ScenePainting.cached(motif), motif, motif) { value = ScenePainting.cached(motif) ?: ScenePainting.load(context, motif) }
}

/** Android 12 and older (no scene shaders): the painting itself, still, filling the screen. */
@Composable
internal fun PaintingStill(res: Int, modifier: Modifier) {
    Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
