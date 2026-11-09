package com.shelf.launcher.ui

import coil.EventListener
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Size

/**
 * Update 474 (QA only — installed on the image loader only while the QA hooks file exists, ArtStorage.createImageLoader):
 * the last pictures Coil delivered, for tools/qa (QaClip dumps them as "images"): how long each took, the size it was asked
 * for (the box it is drawn into), the size it was decoded at (smaller than asked = the source itself is smaller: upscaled on
 * screen), FIT or FILL (FILL crops what doesn't fit), and where it came from (memory / disk / network). The picture's address
 * is never written — only a short hash of it, so a dump can't leak an account in a ScreenScraper address.
 */
internal object QaArt : EventListener {
    private class Rec(val tok: String, val t0: Long) { var t1 = 0L; var w = -1; var h = -1; var dw = -1; var dh = -1; var scale = ""; var src = ""; var ok = false }
    // (Keyed by the request's target — stable when an interceptor copies the request (SavedArtInterceptor's newBuilder()):
    // keyed by the request itself, onSuccess saw a copy, found nothing, and every dump in update 474's first runs had no images.)
    private val live = java.util.IdentityHashMap<Any, Rec>()
    private fun key(r: ImageRequest): Any = r.target ?: r.data
    private val done = ArrayDeque<Rec>()

    private fun tok(data: Any): String = runCatching {
        val d = java.security.MessageDigest.getInstance("SHA-1").digest(data.toString().toByteArray())
        d.take(5).joinToString("") { "%02x".format(it) }
    }.getOrDefault("?")

    private fun px(d: Dimension): Int = (d as? Dimension.Pixels)?.px ?: -1

    @Synchronized override fun onStart(request: ImageRequest) { live[key(request)] = Rec(tok(request.data), android.os.SystemClock.uptimeMillis()) }
    @Synchronized override fun resolveSizeEnd(request: ImageRequest, size: Size) {
        live[key(request)]?.let { it.w = px(size.width); it.h = px(size.height) }
    }
    @Synchronized override fun onSuccess(request: ImageRequest, result: SuccessResult) = finish(request) { r ->
        r.ok = true; r.dw = result.drawable.intrinsicWidth; r.dh = result.drawable.intrinsicHeight
        r.scale = request.scale.name; r.src = result.dataSource.name
    }
    @Synchronized override fun onError(request: ImageRequest, result: ErrorResult) = finish(request) { r -> r.scale = request.scale.name; r.src = "ERROR" }
    @Synchronized override fun onCancel(request: ImageRequest) { live.remove(key(request)) }

    private fun finish(request: ImageRequest, fill: (Rec) -> Unit) {
        val r = live.remove(key(request)) ?: return
        r.t1 = android.os.SystemClock.uptimeMillis(); fill(r)
        done.addLast(r); while (done.size > 120) done.removeFirst()
    }

    /** The finished requests of the last 20 s, newest last. */
    @Synchronized fun json(): String {
        val now = android.os.SystemClock.uptimeMillis()
        return done.filter { now - it.t1 < 20_000 }.joinToString(",", "[", "]") { r ->
            "{\"tok\":\"${r.tok}\",\"ago\":${now - r.t1},\"ms\":${r.t1 - r.t0},\"want\":[${r.w},${r.h}],\"got\":[${r.dw},${r.dh}]," +
                "\"scale\":\"${r.scale}\",\"src\":\"${r.src}\",\"ok\":${r.ok}}"
        }
    }
}
