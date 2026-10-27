package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext

/**
 * (QA pass 2, D23) Voltcap's own template pictures (case / cartridge / box frames, masks, spines, room scenes), decoded
 * once per app run instead of once per card. `ImageBitmap.imageResource` remembers only inside one composable, so every
 * card that came on screen decoded its console's template again on the main thread (a 45 ms `decodeBitmap` inside
 * a recomposition in the trace of a category change). Bounded: the least recently used ones are let go past 64 MB.
 */
internal object ResImages {
    private val cache = object : android.util.LruCache<Int, ImageBitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(res: android.content.res.Resources, id: Int): ImageBitmap =
        cache.get(id) ?: vcTrace("VC:resDecode") { ImageBitmap.imageResource(res, id) }.also { cache.put(id, it) }
}

/** [ImageBitmap.imageResource], shared by every card (see [ResImages]). */
@Composable
internal fun cachedImageResource(id: Int): ImageBitmap {
    val res = LocalContext.current.resources
    return remember(id) { ResImages.get(res, id) }
}

/**
 * [androidx.compose.ui.res.painterResource] for Voltcap's template pictures, shared like [cachedImageResource] (painterResource
 * decodes a PNG again in every card: pass 2 trace T3, `decodeBitmap` 46 ms inside a recomposition on a category change).
 * Vector drawables still go through painterResource.
 */
@Composable
internal fun cachedPainterResource(id: Int): androidx.compose.ui.graphics.painter.Painter {
    val res = LocalContext.current.resources
    val vector = remember(id) {
        val v = android.util.TypedValue()
        runCatching { res.getValue(id, v, true) }
        v.string?.toString()?.endsWith(".xml") == true
    }
    return if (vector) androidx.compose.ui.res.painterResource(id)
    else { val img = cachedImageResource(id); remember(img) { androidx.compose.ui.graphics.painter.BitmapPainter(img) } }
}
