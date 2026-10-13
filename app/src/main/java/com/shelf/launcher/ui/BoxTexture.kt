package com.shelf.launcher.ui

import android.graphics.Bitmap
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.size.Size
import coil.transform.Transformation

/*
 * The game's own case, cut out of ScreenScraper's full box scan ("box-texture", saved as "Box (full scan)"): the scan
 * of the whole unfolded case, back | spine | front, side by side. The spine is the strip in the middle, the front the
 * part on its right, the back the part on its left.
 *
 * A cut is asked for with a plain string model (so it goes everywhere a picture address goes, Coil's caches included):
 *     voltcrop:<spine|front|back>:<platform>:<the scan's address>
 * BoxCropInterceptor (installed on the app's image loader, ArtStorage) turns it back into the scan's address with a
 * BoxCrop transformation: the scan is downloaded and saved once and every cut is made from it.
 */

/** Which part of the full box scan. */
internal enum class BoxPart { SPINE, FRONT, BACK, SPINE3D }

internal object BoxTexture {
    private const val PREFIX = "voltcrop:"

    /** The label the full scan is saved under in the game's physical media (ScreenScraper.physicalMedia). */
    const val LABEL = "Box (full scan)"

    /** The model for [part] of the full scan at [url] (a game of [platform], for its case's proportions). */
    fun model(url: String, part: BoxPart, platform: String?): String =
        PREFIX + part.name.lowercase() + ":" + (platform ?: "") + ":" + url

    /** (part, platform, address) of a cut model, or null for anything else. */
    fun parse(data: Any?): Triple<BoxPart, String?, String>? {
        val s = data as? String ?: return null
        if (!s.startsWith(PREFIX)) return null
        val bits = s.removePrefix(PREFIX).split(':', limit = 3)
        if (bits.size != 3 || bits[2].isBlank()) return null
        val part = when (bits[0]) { "spine" -> BoxPart.SPINE; "front" -> BoxPart.FRONT; "back" -> BoxPart.BACK; "spine3d" -> BoxPart.SPINE3D; else -> return null }
        return Triple(part, bits[1].ifBlank { null }, bits[2])
    }

    /** The full box scan's address in a game's physical media, if ScreenScraper has one. */
    fun fullScan(physical: List<Pair<String, String>>?): String? = physical?.firstOrNull { it.first == LABEL }?.second

    /**
     * The game's own spine: ScreenScraper's spine scan when there is one. Null otherwise (then the console's spine from
     * the template pack is used). Update 123: the strips cut out of other scans (often cut in the wrong place) only with
     * Game look › Spines cut from box scans on.
     */
    fun spine(physical: List<Pair<String, String>>?, platform: String?): String? =
        physical?.firstOrNull { it.first == "Spine" }?.second
            ?: if (!com.shelf.launcher.data.Features.spineCuts.state.value) null
            else fullScan(physical)?.let { model(it, BoxPart.SPINE, platform) }
                // Update 108: last of all, the spine face of ScreenScraper's 3D box picture (the side you see on the left).
                ?: physical?.firstOrNull { it.first == "Box (3D)" }?.second?.let { model(it, BoxPart.SPINE3D, platform) }

    /**
     * The case's own scanned front: ScreenScraper's front scan, else the front cut from the full box scan. Also notes
     * the game's own spine for that front, so a case drawn from it can turn to show its real spine ([spineFor]).
     */
    fun front(physical: List<Pair<String, String>>?, platform: String?): String? {
        val f = physical?.firstOrNull { it.first == "Box" }?.second
            ?: fullScan(physical)?.let { model(it, BoxPart.FRONT, platform) }
            ?: return null
        spine(physical, platform)?.let { if (spines.size < 4000) spines[f] = it }
        media(physical)?.let { if (medias.size < 4000) medias[f] = it }
        return f
    }

    private val spines = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val medias = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The game's own cartridge / disc picture (ScreenScraper's "support-2D"), if scraped. */
    fun media(physical: List<Pair<String, String>>?): String? = physical?.firstOrNull { it.first == "Disc / cartridge" }?.second

    /**
     * The game's own cartridge / disc picture for a front picked by [front] (update 104), so a case drawn from it
     * shows the real disc or cartridge beside it rather than a drawn one. Null when there is none (or with Voltcap's
     * drawn media chosen, Settings › Game look).
     */
    fun mediaFor(front: Any?): String? =
        if (com.shelf.launcher.data.Features.mediaShells.value) null else mediaOfFront(front)

    /** Update 173: the game's own cartridge / disc for a scanned front [front], whatever the drawn-media setting says. */
    fun mediaOfFront(front: Any?): String? = (front as? String)?.let { medias[it] }

    /** The game's own spine for a front picked by [front] (null when it has none, or for any other picture). */
    fun spineFor(front: Any?): String? = (front as? String)?.let { spines[it] }

    /**
     * The best picture of the game's case for a card: the front scan (or the front cut from the full scan), else the
     * 3D box, else any other box picture (never the whole unfolded scan).
     */
    fun caseScan(physical: List<Pair<String, String>>?, platform: String?): String? =
        front(physical, platform)
            ?: physical?.firstOrNull { it.first == "Box (3D)" }?.second
            ?: physical?.firstOrNull { it.first.startsWith("Box") && it.first != LABEL }?.second

    /**
     * Where the spine sits in a full box scan: its left and right edge (pixels), or null when this doesn't look like a
     * back | spine | front scan. The spine is found as the pair of straight vertical edges (the case's folds) round
     * the middle; the case's real proportions ([platform]) say roughly how wide it should be.
     */
    fun findSpine(src: Bitmap, platform: String?): Pair<Int, Int>? {
        val w = src.width
        val h = src.height
        if (w < 40 || h < 40) return null
        val ratio = w.toFloat() / h
        // Two fronts side by side plus a spine: clearly wider than tall (a single front or a cross-shaped
        // cardboard box layout isn't this).
        if (ratio < 1.15f || ratio > 2.8f) return null
        // The spine's expected width from the case's front proportions: width = 2 × front + spine.
        val frontAspect = realCaseMm(platform)?.let { it.first / it.second }
        val expected = frontAspect?.let { (ratio - 2f * it) / ratio }?.takeIf { it in 0.012f..0.2f } // share of the width

        // Look at a small copy: a column that is a fold has a colour change across most of the height.
        val aw = minOf(360, w)
        val ah = (h.toLong() * aw / w).toInt().coerceAtLeast(24)
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val small = runCatching { Bitmap.createScaledBitmap(soft, aw, ah, true) }.getOrNull() ?: return null
        val px = IntArray(aw * ah)
        small.getPixels(px, 0, aw, 0, 0, aw, ah)
        if (small !== soft) small.recycle()
        val y0 = (ah * 0.06f).toInt()
        val y1 = (ah * 0.94f).toInt().coerceAtLeast(y0 + 1)
        val rows = (y1 - y0).toFloat()
        val fold = FloatArray(aw)
        for (x in 1 until aw) {
            var n = 0
            for (y in y0 until y1) {
                val a = px[y * aw + x - 1]
                val b = px[y * aw + x]
                val d = kotlin.math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) +
                    kotlin.math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) +
                    kotlin.math.abs((a and 255) - (b and 255))
                if (d > 36) n++
            }
            fold[x] = n / rows
        }
        // A fold can fall between two columns of the small copy: take the stronger neighbour too.
        fun at(x: Int): Float = if (x < 1 || x >= aw) 0f else maxOf(fold[x], if (x + 1 < aw) fold[x + 1] * 0.9f else 0f, fold[x - 1] * 0.9f)

        val mid = aw / 2f
        val sMin: Int
        val sMax: Int
        if (expected != null) {
            val e = expected * aw / 2f
            sMin = (e * 0.6f).toInt().coerceAtLeast(1)
            sMax = (e * 1.45f).toInt().coerceAtLeast(sMin + 1)
        } else {
            sMin = (aw * 0.006f).toInt().coerceAtLeast(1)
            sMax = (aw * 0.08f).toInt().coerceAtLeast(sMin + 1)
        }
        val shift = (aw * 0.03f).toInt().coerceAtLeast(1)
        var best = -1f
        var bestL = 0
        var bestR = 0
        for (c in (mid - shift).toInt()..(mid + shift).toInt()) {
            for (s in sMin..sMax) {
                for (skew in -1..1) {
                    val l = c - s
                    val r = c + s + skew
                    if (l < 2 || r > aw - 2 || r - l < 2) continue
                    // Both folds, a little less for a spine far from the middle (back and front are the same width).
                    var score = at(l) + at(r) - 0.8f * kotlin.math.abs((l + r) / 2f - mid) / aw
                    if (expected != null) score -= 0.25f * kotlin.math.abs((r - l).toFloat() / aw - expected) / expected
                    if (score > best) { best = score; bestL = l; bestR = r }
                }
            }
        }
        val scale = w.toFloat() / aw
        // Clear folds: both edges run along most of the height between them.
        if (best >= 0.55f) {
            val l = ((bestL + 0.5f) * scale).toInt().coerceIn(0, w - 2)
            val r = ((bestR - 0.5f) * scale).toInt().coerceIn(l + 1, w - 1)
            return l to r
        }
        // No clear folds (a spine printed in the same colours as the covers): the expected width, in the middle.
        if (expected != null) {
            val half = expected * w / 2f
            val l = (w / 2f - half).toInt().coerceIn(0, w - 2)
            val r = (w / 2f + half).toInt().coerceIn(l + 1, w - 1)
            return l to r
        }
        return null
    }

    /**
     * The spine face of a 3D box picture (a box turned so its left side shows, on a see-through background): from the
     * box's left edge to the fold where the front starts, between the highest bottom and lowest top of that strip (its
     * edges slant with the turn). Null when the picture doesn't look like that.
     */
    fun findSpine3D(src: Bitmap): android.graphics.Rect? {
        val w = src.width
        val h = src.height
        if (w < 40 || h < 40 || !src.hasAlpha()) return null
        val aw = minOf(360, w)
        val ah = (h.toLong() * aw / w).toInt().coerceAtLeast(24)
        val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        val small = runCatching { Bitmap.createScaledBitmap(soft, aw, ah, true) }.getOrNull() ?: return null
        val px = IntArray(aw * ah)
        small.getPixels(px, 0, aw, 0, 0, aw, ah)
        if (small !== soft) small.recycle()
        fun opaque(x: Int, y: Int) = (px[y * aw + x] ushr 24) > 160
        val tops = IntArray(aw) { -1 }
        val bottoms = IntArray(aw) { -1 }
        for (x in 0 until aw) {
            var t = -1; var b = -1
            for (y in 0 until ah) if (opaque(x, y)) { if (t < 0) t = y; b = y }
            tops[x] = t; bottoms[x] = b
        }
        val boxL = (0 until aw).firstOrNull { tops[it] >= 0 && bottoms[it] - tops[it] > ah * 0.35f } ?: return null
        val boxR = (aw - 1 downTo 0).firstOrNull { tops[it] >= 0 && bottoms[it] - tops[it] > ah * 0.35f } ?: return null
        val boxW = boxR - boxL
        if (boxW < aw * 0.3f) return null
        // The fold: a column where the colour changes along most of the box's height.
        var best = 0f
        var fold = -1
        for (x in (boxL + (boxW * 0.03f).toInt()).coerceAtLeast(boxL + 2)..(boxL + (boxW * 0.3f).toInt()).coerceAtMost(boxR - 2)) {
            val t = maxOf(tops[x], tops[x - 1]); val b = minOf(bottoms[x], bottoms[x - 1])
            if (t < 0 || b - t < ah * 0.3f) continue
            var n = 0
            for (y in t..b) {
                val a = px[y * aw + x - 1]; val c = px[y * aw + x]
                val d = kotlin.math.abs(((a shr 16) and 255) - ((c shr 16) and 255)) +
                    kotlin.math.abs(((a shr 8) and 255) - ((c shr 8) and 255)) + kotlin.math.abs((a and 255) - (c and 255))
                if (d > 30) n++
            }
            val score = n / (b - t + 1f)
            if (score > best) { best = score; fold = x }
        }
        if (fold < 0 || best < 0.45f) return null
        // A box turned to show its left side: that side runs away from you, so the box is shorter at its left edge
        // than at the fold (else this isn't a spine face, e.g. a box seen from the front).
        val hLeft = bottoms[boxL + 1] - tops[boxL + 1]
        val hFold = bottoms[fold] - tops[fold]
        if (hLeft <= 0 || hFold <= 0 || hLeft > hFold * 0.97f) return null
        // The strip between the left edge and the fold, inside its slanted top and bottom edges.
        val l = boxL + 1
        val r = fold - 1
        if (r - l < 2) return null
        var top = 0; var bottom = ah - 1
        for (x in l..r) { top = maxOf(top, tops[x]); bottom = minOf(bottom, bottoms[x]) }
        if (bottom - top < ah * 0.45f) return null
        val k = w.toFloat() / aw
        return android.graphics.Rect((l * k).toInt(), (top * k).toInt() + 1, (r * k).toInt(), (bottom * k).toInt() - 1)
    }
}

/** Cuts [part] out of a full box scan; fails (so the picture falls back) when the scan isn't laid out that way. */
internal class BoxCrop(private val part: BoxPart, private val platform: String?) : Transformation {
    override val cacheKey: String = "voltcapBoxCrop(${part.name},${platform ?: ""},1)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val rect = if (part == BoxPart.SPINE3D) BoxTexture.findSpine3D(input) ?: throw IllegalStateException("No spine face on this 3D box")
        else {
        val (l, r) = BoxTexture.findSpine(input, platform) ?: throw IllegalStateException("Not a back | spine | front box scan")
        when (part) {
            BoxPart.SPINE -> android.graphics.Rect(l, 0, r, h)
            // A pixel or two in from the fold, so no sliver of the spine shows on the front / back.
            BoxPart.FRONT -> android.graphics.Rect((r + 1).coerceAtMost(w - 1), 0, w, h)
            BoxPart.BACK -> android.graphics.Rect(0, 0, (l - 1).coerceAtLeast(1), h)
            BoxPart.SPINE3D -> android.graphics.Rect(0, 0, 1, 1)
        }
        }
        if (rect.width() < 2 || rect.height() < 2) throw IllegalStateException("Empty cut")
        val maxSide = if (part == BoxPart.SPINE || part == BoxPart.SPINE3D) 720 else 960
        val k = minOf(1f, maxSide.toFloat() / maxOf(rect.width(), rect.height()))
        val out = Bitmap.createBitmap((rect.width() * k).toInt().coerceAtLeast(1), (rect.height() * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val soft = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        android.graphics.Canvas(out).drawBitmap(
            soft, rect, android.graphics.Rect(0, 0, out.width, out.height),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        )
        return out
    }
}

/**
 * Turns a "voltcrop:" model into the full scan's address plus the cut. The scan is decoded large enough for a
 * sharp spine (a spine is only a few percent of the scan's width), whatever size the slot asked for.
 */
internal class BoxCropInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val (part, platform, url) = BoxTexture.parse(chain.request.data) ?: return chain.proceed(chain.request)
        val request = chain.request.newBuilder()
            .data(url)
            .transformations(listOf(BoxCrop(part, platform)) + chain.request.transformations)
            .build()
        return chain.withSize(Size(1600, 1600)).proceed(request)
    }
}
