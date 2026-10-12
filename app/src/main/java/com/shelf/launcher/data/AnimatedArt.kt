package com.shelf.launcher.data

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.runInterruptible
import okio.ByteString.Companion.encodeUtf8

/**
 * Animated artwork (Appearance › Colours & background › Animated artwork): covers, heroes and logos
 * that are GIFs, animated WebPs or animated PNGs - your own, or SteamGridDB's animated ones - play
 * instead of showing their first frame.
 *
 * Done with Android's own decoder (ImageDecoder → AnimatedImageDrawable, Android 9+), plugged into the
 * image loader, so no extra library is needed. Off by default: many moving covers at once cost
 * battery. Small requests (colour picking, tiny thumbnails) always get a still picture.
 */
object AnimatedArt {
    /** Play animated pictures (otherwise their first frame shows, as before). */
    @Volatile var enabled = false
    /** Scraping: ask SteamGridDB for animated covers / heroes first. */
    @Volatile var preferAnimatedScraping = false

    /** Request parameter: play this picture even with Animated artwork off (the artwork picker). */
    const val PARAM_ANIMATE = "voltcap.animate"
    /** Request parameter: keep this picture still even with Animated artwork on (a game set to "Still"). */
    const val PARAM_STILL = "voltcap.still"

    // ---- Update 152: moving pictures rest while you move, and after a few minutes without input ----
    // Measured on the Retroid Pocket Classic: every playing cover is decoded and uploaded to the GPU frame by frame, on
    // the same render thread that draws the home screen, so scrolling through animated covers stuttered, and with the
    // screen left alone they still took most of a CPU core. While you move (and for a moment after) they hold still;
    // after three minutes without input (HomeIdle.deep) they stop until the next press.

    /** Every animated picture decoded (weak: gone with its card). */
    private val live = java.util.Collections.newSetFromMap(java.util.WeakHashMap<AnimatedImageDrawable, Boolean>())
    /** The ones this paused (only these start again). */
    private val held = ArrayList<java.lang.ref.WeakReference<AnimatedImageDrawable>>()
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var moving = false
    private var sleeping = false
    private val resume = Runnable { moving = false; if (!sleeping) restartHeld() }

    private fun register(d: AnimatedImageDrawable) { synchronized(live) { live += d } }

    /** A step on the D-pad (or a swipe): animated pictures hold still until you've stopped for a moment. Main thread. */
    fun onMove() {
        if (!enabled || Build.VERSION.SDK_INT < 28) return
        if (!moving) { moving = true; pauseAll() }
        main.removeCallbacks(resume)
        main.postDelayed(resume, 450L)
    }

    /** No input for a long while (true) / input again (false). */
    fun setSleeping(sleep: Boolean) {
        if (Build.VERSION.SDK_INT < 28) return
        main.post {
            if (sleeping == sleep) return@post
            sleeping = sleep
            if (sleep) pauseAll() else if (!moving) restartHeld()
        }
    }

    @androidx.annotation.RequiresApi(28)
    private fun pauseAll() {
        val now = synchronized(live) { live.toList() }
        now.forEach { d -> if (d.isRunning) { runCatching { d.stop() }; held += java.lang.ref.WeakReference(d) } }
    }

    @androidx.annotation.RequiresApi(28)
    private fun restartHeld() {
        held.forEach { r -> r.get()?.let { runCatching { it.start() } } }
        held.clear()
    }

    /** A picture that's most likely animated, from its address (SteamGridDB's animated art is GIF / WebP). */
    fun looksAnimated(url: String?): Boolean {
        val u = url?.substringBefore('?')?.lowercase() ?: return false
        return u.endsWith(".gif") || u.endsWith(".webp")
    }

    /** The image loader's decoder for animated pictures. */
    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val forced = options.parameters.value<Boolean>(PARAM_ANIMATE) == true
            val still = options.parameters.value<Boolean>(PARAM_STILL) == true
            if (Build.VERSION.SDK_INT < 28 || still || !(enabled || forced)) return null
            // Colour pickers and tiny thumbnails want a plain still bitmap.
            val w = (options.size.width as? Dimension.Pixels)?.px ?: Int.MAX_VALUE
            val h = (options.size.height as? Dimension.Pixels)?.px ?: Int.MAX_VALUE
            if (w <= 160 && h <= 160) return null
            if (!isAnimated(result.source)) return null
            return AnimatedDecoder(result.source, options)
        }

        private fun isAnimated(source: ImageSource): Boolean = runCatching {
            val peek = source.source().peek()
            if (!peek.request(32)) return@runCatching false
            val head = peek.readByteString(32)
            when {
                // GIF87a / GIF89a: let the decoder decide (one-frame GIFs simply don't move).
                head.rangeEquals(0, "GIF8".encodeUtf8(), 0, 4) -> true
                // WebP with the VP8X header and its animation flag.
                head.rangeEquals(0, "RIFF".encodeUtf8(), 0, 4) && head.rangeEquals(8, "WEBPVP8X".encodeUtf8(), 0, 8) ->
                    (head[20].toInt() and 0x02) != 0
                // PNG with an acTL chunk (APNG) somewhere near the start.
                head.rangeEquals(1, "PNG".encodeUtf8(), 0, 3) -> {
                    val more = source.source().peek()
                    more.request(256) && more.readByteString(minOf(256L, more.buffer.size)).indexOf("acTL".encodeUtf8()) >= 0
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    @androidx.annotation.RequiresApi(28)
    private class AnimatedDecoder(private val source: ImageSource, private val options: Options) : Decoder {
        override suspend fun decode(): DecodeResult {
            var sampled = false
            val file = source.file().toFile()
            val drawable = runInterruptible {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    // Requests that read pixels (hardware not allowed) get a software bitmap, as Coil's own decoders do (update 91).
                    decoder.allocator = if (options.config == android.graphics.Bitmap.Config.HARDWARE) ImageDecoder.ALLOCATOR_DEFAULT
                        else ImageDecoder.ALLOCATOR_SOFTWARE
                    val tw = (options.size.width as? Dimension.Pixels)?.px
                    val th = (options.size.height as? Dimension.Pixels)?.px
                    if (tw != null && th != null && (info.size.width > tw || info.size.height > th)) {
                        val k = maxOf(tw.toFloat() / info.size.width, th.toFloat() / info.size.height).coerceAtMost(1f)
                        decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                        sampled = true
                    }
                }
            }
            (drawable as? AnimatedImageDrawable)?.let { it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; register(it) }
            return DecodeResult(drawable, sampled)
        }
    }
}
