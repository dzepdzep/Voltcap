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
 * image loader, so no extra library is needed. Small requests (colour picking, tiny thumbnails) always get a
 * still picture.
 *
 * Update 181: by default only the game you're on moves ([focusOnly], Features.animatedFocusOnly): every animated
 * picture is wrapped in a [Gate] that knows the address it came from ([SourceTag] writes it into the request), and it
 * plays only while that address belongs to the game you're on ([setFocus]); the rest show their first frame — the
 * plain cover. Works for every layout and both screens, whatever way they ask for the picture.
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
    /** Update 181: request parameter: the address the picture was asked for by (written by [SourceTag]). */
    const val PARAM_SRC = "voltcap.src"

    /** Update 181: only the game you're on moves (the others hold their first frame). */
    @Volatile var focusOnly = true
        set(v) { if (field != v) { field = v; reapply() } }
    /** The addresses of the game you're on (its cover, banner, hero, logo — scraped and your own). */
    @Volatile private var focusUrls: Set<String> = emptySet()

    /** The game you're on changed: its pictures play, everyone else's hold still. Any thread. */
    fun setFocus(urls: Set<String>) {
        if (urls == focusUrls) return
        focusUrls = urls
        reapply()
    }

    private fun reapply() {
        if (Build.VERSION.SDK_INT < 28) return
        main.post { gates().forEach { it.apply() } }
    }

    // ---- Update 152: moving pictures rest while you move, and after a few minutes without input ----
    // Measured on the Retroid Pocket Classic: every playing cover is decoded and uploaded to the GPU frame by frame, on
    // the same render thread that draws the home screen, so scrolling through animated covers stuttered, and with the
    // screen left alone they still took most of a CPU core. While you move (and for a moment after) they hold still;
    // after three minutes without input (HomeIdle.deep) they stop until the next press.

    /** Every animated picture decoded (weak: gone with its card). */
    private val live = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Gate, Boolean>())
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    @Volatile private var moving = false
    @Volatile private var sleeping = false
    /** Update 187: the battery saver's rest (BatterySaver): held still, like sleeping, but set apart from the idle timer. */
    @Volatile private var resting = false
    private val resume = Runnable { moving = false; if (!sleeping && !resting) restartHeld() }

    private fun register(g: Gate) { synchronized(live) { live += g } }
    private fun gates(): List<Gate> = synchronized(live) { live.toList() }

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
            if (sleep) pauseAll() else if (!moving && !resting) restartHeld()
        }
    }

    /** Update 187: the battery saver resting (true) or not (BatterySaver.sync). */
    fun setResting(rest: Boolean) {
        if (Build.VERSION.SDK_INT < 28) return
        main.post {
            if (resting == rest) return@post
            resting = rest
            if (rest) pauseAll() else if (!moving && !sleeping) restartHeld()
        }
    }

    @androidx.annotation.RequiresApi(28)
    private fun pauseAll() { gates().forEach { it.apply() } }

    @androidx.annotation.RequiresApi(28)
    private fun restartHeld() { gates().forEach { it.apply() } }

    /**
     * Update 181: an animated picture that plays only when it may: its painter asked it to ([start]), you're not
     * moving, the screen isn't resting, and — with [focusOnly] — it's a picture of the game you're on (or forced on:
     * the artwork picker, a game set to always move). Otherwise it shows its first frame.
     */
    @androidx.annotation.RequiresApi(28)
    class Gate internal constructor(
        private val inner: AnimatedImageDrawable,
        private val src: String?,
        private val forced: Boolean
    ) : android.graphics.drawable.DrawableWrapper(inner), android.graphics.drawable.Animatable {
        private var wanted = false

        private fun may(): Boolean = wanted && !moving && !sleeping && !resting &&
            (forced || !focusOnly || src == null || src in focusUrls)

        /** Start or stop the picture to match [may]. Main thread. */
        fun apply() {
            runCatching { if (may()) { if (!inner.isRunning) inner.start() } else if (inner.isRunning) inner.stop() }
        }

        override fun start() { wanted = true; apply() }
        override fun stop() { wanted = false; runCatching { inner.stop() } }
        override fun isRunning(): Boolean = inner.isRunning
    }

    /**
     * Update 181: writes the address a picture is asked for by into its request ([PARAM_SRC], not part of its memory
     * key), so the decoder's [Gate] knows which game it belongs to. Placed after the shared-size interceptor (which
     * only shares requests without parameters) and before the saved-art one (which swaps the address for a file).
     */
    class SourceTag : coil.intercept.Interceptor {
        override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
            val r = chain.request
            val url = r.data as? String
            if (url == null || !enabled || r.parameters.value<String>(PARAM_SRC) != null) return chain.proceed(r)
            return chain.proceed(r.newBuilder().setParameter(PARAM_SRC, url, memoryCacheKey = null).build())
        }
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
            return AnimatedDecoder(result.source, options, options.parameters.value<String>(PARAM_SRC), forced)
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
    private class AnimatedDecoder(
        private val source: ImageSource,
        private val options: Options,
        private val src: String?,
        private val forced: Boolean
    ) : Decoder {
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
            val out = (drawable as? AnimatedImageDrawable)?.let {
                it.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                Gate(it, src, forced).also { g -> register(g) }
            } ?: drawable
            return DecodeResult(out, sampled)
        }
    }
}
