package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil.compose.AsyncImage

/*
 * The game as the real thing, from the user's template pack (MediaTemplates.kt): the console's own empty
 * cartridge / card / UMD with the game's label scan behind its window (or, without a scan, the cartridge that
 * has the system's label frame printed on it, with the cover in the frame's art window), discs with the art on
 * the printable ring under the disc's own print, and 3D boxes: the box art (or the cover inside the system's
 * printed border) and the system's spine, put in perspective under the glass overlay, turning slightly.
 */

/** A disc's printable ring (between the hub and the rim). */
private class DiscRing(private val inner: Float, private val outer: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2
        return Outline.Generic(Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(c, r * outer))
            addOval(Rect(c, r * inner))
        })
    }
}

/**
 * Cartridge or disc from the template pack, fitted inside [modifier] with its own proportions. [labelScan] is the
 * game's real label (ScreenScraper's scan), [cover] its cover: with a scan the plain shell is used, with only the
 * cover the shell with the system's label frame. Returns false when the pack has nothing for this console.
 */
@Composable
internal fun TemplateMedia(tpl: MediaTpl, labelScan: Any?, cover: Any?, modifier: Modifier = Modifier, smallDisc: Boolean = false): Boolean {
    val disc = tpl.disc
    // Update 169: never the label versions (tpl_cartl_*): they carry each system's printed brand art (the 3DS / Switch
    // headers, SEGA / GENESIS bars, ratings, seals). The cover goes into the plain shell's window, which has the console's
    // logo where its moulded brand was (res tpl_cart_*, cleaned).
    val useLabelFrame = false
    val cart = if (useLabelFrame) tpl.label else tpl.cart
    if (disc == null && cart == null) return false
    val art = labelScan ?: cover
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (disc != null) {
            val side = minOf(maxWidth, maxHeight) * (if (smallDisc) 0.7f else 1f)
            Box(Modifier.size(side, side)) {
                // Discs are printed paper-white where the art is thin; the art fills the printable ring.
                Box(Modifier.fillMaxSize().clip(DiscRing(MediaTemplates.RING_INNER, MediaTemplates.RING_OUTER)).background(Color(0xFFE9E6DF))) {
                    if (art != null) AsyncImage(mediaArtModel(art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) // (Update 157)
                }
                Image(painterResource(disc), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        } else if (cart != null) {
            val w = minOf(maxWidth, maxHeight * cart.aspect)
            val h = w / cart.aspect
            val win = cart.win
            val mask = ImageBitmap.imageResource(cart.mask)
            Box(Modifier.size(w, h)) {
                Box(
                    Modifier
                        .offset(x = w * win[0], y = h * win[1])
                        .size(w * (win[2] - win[0]), h * (win[3] - win[1]))
                        // Only inside the shell's window (round for a UMD, notched for others): the mask cuts it.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val mw = mask.width.toFloat(); val mh = mask.height.toFloat()
                            drawImage(
                                mask,
                                srcOffset = IntOffset((win[0] * mw).toInt(), (win[1] * mh).toInt()),
                                srcSize = IntSize(((win[2] - win[0]) * mw).toInt().coerceAtLeast(1), ((win[3] - win[1]) * mh).toInt().coerceAtLeast(1)),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        .background(Color(0xFFE9E6DF))
                ) {
                    // (Update 206: stretched to fill the label window exactly — the art adapts to the label, never cut.)
                    // (Update 215: a cover at its own shape, fitted in the window, never stretched; a label scan still fills it.)
                    if (art != null) AsyncImage(mediaArtModel(art), null, contentScale = if (labelScan != null) ContentScale.FillBounds else ContentScale.Fit, modifier = Modifier.fillMaxSize()) // (Update 157)
                }
                Image(painterResource(cart.res), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        }
    }
    return true
}

/**
 * A game picture as a plain (software) bitmap the perspective drawing can use; null while it loads. Update 202: [trim]
 * for a cover (or box front scan) drawn on a case: flat strips that are part of its file trimmed off (TrimMargins.kt),
 * under its own memory key. Off for spines, backs, discs, cartridge labels and the 3D models.
 */
@Composable
internal fun rememberSoftBitmap(model: Any?, maxSide: Int = 720, trim: Boolean = false): android.graphics.Bitmap? {
    val context = LocalContext.current
    // (Update 147: a picture already in memory is there from the first frame — a case or piece coming back on screen
    // used to go blank for a moment while it was looked up again.)
    val key = remember(model, maxSide, trim) { (model as? String)?.let { coil.memory.MemoryCache.Key(softKey(maxSide, it, trim)) } }
    val cached = remember(key) { key?.let { coil.Coil.imageLoader(context).memoryCache?.get(it)?.bitmap } }
        ?.also { b -> (model as? String)?.let { Models3D.noteSource(b, it) } }
        // (Update 208: a flat-colour stand-in is no picture.)
        ?.takeUnless { FlatArt.isKnown(model) }
    val state by produceState(cached, model, trim) {
        if (model == null) { value = null; return@produceState }
        if (cached != null) { value = cached; if (PerfDiag.on) PerfDiag.softHits++; return@produceState }
        if (PerfDiag.on) PerfDiag.softMisses++
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                // (Update 147: always a still picture — an animated cover came back as an animated drawable, not a bitmap,
                // and the 3D pieces and cases stayed a plain grey card.)
                val req = coil.request.ImageRequest.Builder(context).data(model).size(maxSide, maxSide)
                    .precision(coil.size.Precision.INEXACT).allowHardware(false)
                    .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
                    .apply { if (trim) transformations(TrimUniformMargins) }
                    .apply { if (key != null) memoryCacheKey(key) }.build()
                drawableBitmap((coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable, maxSide)
                    // (Update 151: remembered as this address's picture, so the 3D pieces made with it are found again.)
                    ?.also { b -> (model as? String)?.let { Models3D.noteSource(b, it) } }
                    // (Update 208: a flat-colour stand-in is no picture.)
                    ?.takeUnless { b -> FlatArt.check(model, b) }
                    // (Update 232, #60: its upload to the GPU started here, off the screen's thread, not in its first frame.)
                    ?.also { b -> prepareSoft(b) }
            }.getOrNull()
        }
    }
    return state
}

/**
 * Update 208: pictures that are one flat colour — ScreenScraper sometimes serves a pure green 395 × 680 stand-in instead
 * of a missing scan (the green back of the Ben 10 PSP cases; 14 such files were saved on the Thor). They count as no
 * picture: the next one in line (the back cut from the full box scan, a printed back, the cover, Voltcap's spine) shows.
 * Found when decoded; [version] makes the pieces that asked look again.
 */
internal object FlatArt {
    private val known = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun isKnown(model: Any?): Boolean = (model as? String)?.let { it in known } == true

    /** Notes [model] as a stand-in when [b] is one flat colour; true then. */
    fun check(model: Any?, b: android.graphics.Bitmap?): Boolean {
        if (b == null || model !is String || !flat(b)) return false
        if (known.add(model)) android.os.Handler(android.os.Looper.getMainLooper()).post { version.intValue++ }
        return true
    }

    /** Every pixel of a 7 × 7 grid within a few levels of the first (a picture of anything has some variation). */
    fun flat(b: android.graphics.Bitmap): Boolean = runCatching {
        if (b.width < 8 || b.height < 8) return false
        val soft = if (b.config == android.graphics.Bitmap.Config.HARDWARE) b.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else b
        val first = soft.getPixel(soft.width / 2, soft.height / 2)
        for (i in 0..6) for (j in 0..6) {
            val px = soft.getPixel((soft.width - 1) * i / 6, (soft.height - 1) * j / 6)
            for (sh in intArrayOf(0, 8, 16, 24)) if (kotlin.math.abs(((px shr sh) and 255) - ((first shr sh) and 255)) > 6) return false
        }
        true
    }.getOrDefault(false)
}

/** Update 208: [this] without the pictures found to be flat-colour stand-ins ([FlatArt]); the same list when there are none. */
internal fun List<Pair<String, String>>?.withoutFlat(): List<Pair<String, String>>? =
    if (this == null || none { FlatArt.isKnown(it.second) }) this else filterNot { FlatArt.isKnown(it.second) }

/** Update 205: a picture being loaded by [rememberSoftLoad]: the bitmap (null while loading or when it failed) and whether the load is over. */
internal class SoftLoad(val bitmap: android.graphics.Bitmap?, val done: Boolean)

/**
 * Update 205: [rememberSoftBitmap] that also says when it has finished — so a real spine still on its way shows a neutral
 * stand-in instead of Voltcap's own spine popping to the real one, and one that can't load falls back to Voltcap's.
 */
@Composable
internal fun rememberSoftLoad(model: Any?, maxSide: Int = 720): SoftLoad {
    val context = LocalContext.current
    val key = remember(model, maxSide) { (model as? String)?.let { coil.memory.MemoryCache.Key(softKey(maxSide, it, false)) } }
    val cached = remember(key) { key?.let { coil.Coil.imageLoader(context).memoryCache?.get(it)?.bitmap } }
        ?.takeUnless { FlatArt.isKnown(model) }
    val start = remember(model, cached) { SoftLoad(cached, model == null || cached != null) }
    val state by produceState(start, model) {
        if (model == null || cached != null) { value = start; return@produceState }
        value = SoftLoad(null, false)
        val b = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val req = coil.request.ImageRequest.Builder(context).data(model).size(maxSide, maxSide)
                    .precision(coil.size.Precision.INEXACT).allowHardware(false)
                    .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
                    .apply { if (key != null) memoryCacheKey(key) }.build()
                drawableBitmap((coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable, maxSide)
                    ?.also { bmp -> (model as? String)?.let { Models3D.noteSource(bmp, it) } }
                    ?.takeUnless { bmp -> FlatArt.check(model, bmp) }
                    // (Update 232, #60: its upload to the GPU started here, off the screen's thread, not in its first frame.)
                    ?.also { bmp -> prepareSoft(bmp) }
            }.getOrNull()
        }
        value = SoftLoad(b, true)
    }
    return state
}

/**
 * Update 166: decodes [urls] ahead as the plain bitmaps [rememberSoftBitmap] reads (same size, same memory key), so a 3D
 * case or 3D piece sliding into a carousel has its cover at once — the carousel warmed only the flat covers, and the 3D
 * ones came in dark for a moment while scrolling.
 */
internal fun warmSoft(context: android.content.Context, urls: List<String>, maxSide: Int, trim: Boolean = false) {
    val loader = coil.Coil.imageLoader(context)
    for (u in urls) {
        val flight = softKey(maxSide, u, trim)
        val key = coil.memory.MemoryCache.Key(flight)
        if (loader.memoryCache?.get(key) != null) continue
        // (Update 232, #60: a picture still decoding from the step before isn't asked for again — each step re-sent the
        // ones not in memory yet.)
        if (!softInFlight.add(flight)) continue
        try { loader.enqueue(
            coil.request.ImageRequest.Builder(context).data(u).size(maxSide, maxSide)
                .precision(coil.size.Precision.INEXACT).allowHardware(false)
                .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
                // (Update 202: as rememberSoftBitmap with trim — same transformation, same key.)
                .apply { if (trim) transformations(TrimUniformMargins) }
                .memoryCacheKey(key)
                .listener(object : coil.request.ImageRequest.Listener {
                    override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                        softInFlight.remove(flight)
                        // (Update 232, #60: its GPU upload started now, not in the first frame that draws it.)
                        prepareSoft((result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap)
                    }
                    override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) { softInFlight.remove(flight) }
                    override fun onCancel(request: coil.request.ImageRequest) { softInFlight.remove(flight) }
                })
                .build()
        ) } catch (t: Throwable) { softInFlight.remove(flight); throw t }
    }
}

/** (Update 232, #60: the [warmSoft] memory keys being decoded now, so one isn't queued twice.) */
private val softInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

/**
 * (Update 232, #60: a plain bitmap's upload to the GPU started ahead (asynchronous from Android 7), so the 3D case drawing
 * it first doesn't wait for it in that frame.)
 */
internal fun prepareSoft(b: android.graphics.Bitmap?) {
    if (b == null || android.os.Build.VERSION.SDK_INT < 24) return
    runCatching { if (!b.isRecycled) b.prepareToDraw() }
}

/**
 * Update 207: the two pictures a 3D case ([Case3D]) is drawn with, worked out the way it does — its front (the game's box
 * scan with Original cases on and [realFront], else [cover]) and its spine (the real spine scan, else the strip cut from
 * its box scans) — so they can be decoded ahead under the same memory keys ([warmCase3d]). Keep in step with Case3D.
 */
internal fun case3dModels(g: com.shelf.launcher.data.Game, cover: String?, scans0: List<Pair<String, String>>?, realFront: Boolean): Triple<String?, String?, String?> {
    val f = com.shelf.launcher.data.Features
    val scans = scans0.withoutFlat()
    // (Update 235, #24: not with a cover you picked for the game — its cover is the case front, see UserCovers.)
    val front = (if (realFront && UserCovers.of(g.stableKey) == null && f.originalCases.state.value && !LookStyles.caseForAll() && !androidOwnCase(g.platform))
        BoxTexture.front(scans, g.platform) else null) ?: cover
    val spine = if (LookStyles.spineForAll()) null
        else scans?.firstOrNull { it.first == "Spine" }?.second ?: if (f.originalCases.state.value) BoxTexture.spine(scans, g.platform) else null
    // (Update 208: and its back — the scanned one Case3D asks for at once — so a turned case has it.)
    val back = runCatching { defaultCaseBack(g, scans, cover, case3dAspect(g, false)) as? String }.getOrNull()
    return Triple(front, spine, back)
}

/**
 * Update 207: decodes the 3D cases' fronts and spines ahead ([case3dModels]: front 480 px trimmed, spine 600 px — the sizes
 * and keys Case3D reads), so cases sliding in or a category opening show their print at once. They were warmed with the
 * flat cover only: on a category switch every case stood as bare plastic while its box scan and spine were read.
 */
internal fun warmCase3d(context: android.content.Context, models: List<Triple<String?, String?, String?>>) {
    warmSoft(context, models.mapNotNull { it.first }.distinct(), 480, trim = true)
    warmSoft(context, models.mapNotNull { it.second }.distinct(), 600)
    if (!com.shelf.launcher.device.MemoryBudget.small(context)) warmSoft(context, models.mapNotNull { it.third }.distinct(), 480)
}

/** Update 202: the memory key of a [rememberSoftBitmap] / [warmSoft] picture (a trimmed cover has its own). */
private fun softKey(maxSide: Int, url: String, trim: Boolean): String =
    "soft|$maxSide|" + (if (trim) TrimUniformMargins.KEY + "|" else "") + url

/**
 * The game's box in 3D from the template pack: [front] (a real box scan when [frontIsBox], else the cover inside
 * the system's printed border), the system's spine (or a plain one with [title]) and the glass overlay, turning
 * gently when [spin] is on. Returns false when the pack has no box for this console.
 */
@Composable
internal fun Box3D(
    tpl: BoxTpl, front: Any?, frontIsBox: Boolean, title: String, modifier: Modifier = Modifier, spin: Boolean = true,
    /** Decode size of the front picture: small for cards in a row / grid, bigger for the details page. */
    detail: Int = 360,
    /** The game's own spine (its spine scan or the strip cut from its full box scan, BoxTexture.kt); null: the system's. */
    realSpine: Any? = null
): Boolean {
    val overlay = ImageBitmap.imageResource(tpl.res)
    val spineRes = tpl.spineRes
    // (Update 131: your downloaded spine pack too — spineImage loads both kinds.)
    // (Update 134: the console's spine with the name made once, away from the screen's thread — SpineCache.)
    val tplSpine = rememberTitledSpine(spineRes, title, landscape = tpl.hSpine)
    val face = rememberBoxFace(tpl, front, frontIsBox, detail)
    val ownSpine = rememberSoftBitmap(realSpine, 720)
    val density = LocalDensity.current
    // The spine picture (the game's own, else the system's strip, else a plain dark one with the name), turned for landscape boxes.
    val spineBmp: android.graphics.Bitmap = remember(ownSpine, tplSpine, title, tpl.hSpine) {
        // (Update 125: your own console spine gets the game's name written into the box you left for it.)
        if (ownSpine == null && tplSpine.bitmap != null) tplSpine.bitmap
        // (Update 134: a plain dark strip, no name, for the moment the console's spine is being made.)
        else if (ownSpine == null && !tplSpine.done) SpineCache.WAITING
        else orientSpine(ownSpine ?: run {
            val b = android.graphics.Bitmap.createBitmap(64, 600, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            c.drawColor(0xFF1C1E26.toInt())
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE8E8EE.toInt(); textSize = 34f; isFakeBoldText = true
            }
            // Update 125: the name always fits the spine — smaller type for long names (down to 18 px), "…" only past that.
            val room = 560f
            while (p.measureText(title) > room && p.textSize > 18f) p.textSize -= 1f
            var t = title
            if (p.measureText(t) > room) {
                while (t.length > 2 && p.measureText("$t…") > room) t = t.dropLast(1)
                t = t.trimEnd() + "…"
            }
            c.save(); c.rotate(90f); c.translate(20f, -(32f - 0.36f * p.textSize))
            c.drawText(t, 0f, 0f, p); c.restore()
            b
        // Landscape boxes want a lying spine, the others a standing one: turn the picture whichever way it needs.
        }, landscape = tpl.hSpine)
    }
    val spinAnim = if (spin && !LocalLiteEffects.current) androidx.compose.animation.core.rememberInfiniteTransition(label = "box3d") else null
    val angle = spinAnim?.animateFloat(
        -7f, 7f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(3600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "box3dAngle"
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * tpl.aspect)
        val h = w / tpl.aspect
        Box(
            Modifier.size(w, h)
                .graphicsLayer {
                    // Read here (draw phase): the turn never recomposes anything.
                    rotationY = angle?.value ?: 0f
                    flatTextFor3d()
                    cameraDistance = 14f * density.density
                }
                .drawBehind {
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        fun warp(bmp: android.graphics.Bitmap, q: FloatArray) {
                            val src = floatArrayOf(0f, 0f, bmp.width.toFloat(), 0f, bmp.width.toFloat(), bmp.height.toFloat(), 0f, bmp.height.toFloat())
                            val dst = FloatArray(8) { i -> q[i] * (if (i % 2 == 0) size.width else size.height) }
                            val m = android.graphics.Matrix()
                            if (m.setPolyToPoly(src, 0, dst, 0, 4)) {
                                nc.save(); nc.concat(m); nc.drawBitmap(bmp, 0f, 0f, paint); nc.restore()
                            }
                        }
                        warp(spineBmp, tpl.spine)
                        val f = face
                        if (f != null) warp(f, tpl.front) else {
                            // Still loading: the front as a faint panel.
                            val path = android.graphics.Path().apply {
                                moveTo(tpl.front[0] * size.width, tpl.front[1] * size.height)
                                lineTo(tpl.front[2] * size.width, tpl.front[3] * size.height)
                                lineTo(tpl.front[4] * size.width, tpl.front[5] * size.height)
                                lineTo(tpl.front[6] * size.width, tpl.front[7] * size.height)
                                close()
                            }
                            // (Update 119: a light see-through panel, not a black one, for the moment it takes.)
                            nc.drawPath(path, android.graphics.Paint().apply { color = 0x40C8CCD6 })
                        }
                    }
                    drawImage(overlay, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
        )
    }
    return true
}

/** [src] cut in the middle to [aspect] (width / height), no stretching. */
private fun cropToAspect(src: android.graphics.Bitmap, aspect: Float): android.graphics.Bitmap {
    val cur = src.width.toFloat() / src.height
    if (aspect <= 0f || kotlin.math.abs(cur - aspect) < 0.02f) return src
    val w = if (cur > aspect) (src.height * aspect).toInt().coerceAtLeast(1) else src.width
    val h = if (cur > aspect) src.height else (src.width / aspect).toInt().coerceAtLeast(1)
    return runCatching { android.graphics.Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h) }.getOrDefault(src)
}

/** The box's front face, put together once per picture: the cover in the system's printed frame window (or the box scan). */
@Composable
private fun rememberBoxFace(tpl: BoxTpl, front: Any?, frontIsBox: Boolean, detail: Int): android.graphics.Bitmap? {
    val frameRes = tpl.frameRes
    val framePic: ImageBitmap? = if (frameRes != null && !frontIsBox) ImageBitmap.imageResource(frameRes) else null
    // (Update 202: the cover / box scan with flat strips in its file trimmed off, TrimMargins.kt; the window below already
    // fills with a centred crop, never stretched.)
    val art = rememberSoftBitmap(front, detail, trim = true)
    return remember(art, framePic) {
        val src = art ?: return@remember null
        val frame = framePic?.asAndroidBitmap()
        val win = tpl.frameWin
        // Update 202: a cover that is a box scan already (the case front's own shape, its own band and logo — as the flat
        // case tells it, isBoxFront) is the whole front: the template's band over it printed the console's band twice.
        val scanShaped = frame != null && looksLikeBoxFront(tpl.frameAspect, src.width.toFloat() / src.height.coerceAtLeast(1))
        if (frame == null || win == null || scanShaped) {
            // A ready-made box scan: cut to the template front's real proportions so the corners map without squashing it.
            if ((frontIsBox || scanShaped) && tpl.frameRes != null) cropToAspect(src, tpl.frameAspect) else src
        } else runCatching {
            val w = 600; val h = (600 / tpl.frameAspect).toInt().coerceIn(200, 1400)
            val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            c.drawColor(android.graphics.Color.BLACK)
            val dst = android.graphics.RectF(win[0] * w, win[1] * h, win[2] * w, win[3] * h)
            // Cover cropped to fill the window (no stretching).
            val sw = src.width.toFloat(); val sh = src.height.toFloat()
            val scale = maxOf(dst.width() / sw, dst.height() / sh)
            val cw = dst.width() / scale; val ch = dst.height() / scale
            val sx = (sw - cw) / 2f; val sy = (sh - ch) / 2f
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            c.drawBitmap(src, android.graphics.Rect(sx.toInt(), sy.toInt(), (sx + cw).toInt(), (sy + ch).toInt()), dst, paint)
            c.drawBitmap(frame, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
            out
        }.getOrDefault(src)
    }
}

/**
 * The game's box seen straight from the front (grids and rows): its face — the box scan, or the cover in the
 * system's printed frame — as a free-standing object with a thin plastic edge, a glass sheen and a soft shadow,
 * no spine, never turned. Returns false while there's nothing to show yet.
 */
@Composable
internal fun FrontBox(
    tpl: BoxTpl, front: Any?, frontIsBox: Boolean, modifier: Modifier = Modifier, detail: Int = 360,
    /** The game's console, for its real front proportions when the template has no cover frame (null: from the outline). */
    platform: String? = null
) {
    val face = rememberBoxFace(tpl, front, frontIsBox, detail)
    val img = remember(face) { face?.asImageBitmap() }
    // The template pack's own case (its glass overlay): its front, straightened so it faces you, laid over the face.
    val overlay = ImageBitmap.imageResource(tpl.res)
    // The front's real proportions, from the template's front corners (TL, TR, BR, BL; x is a share of the width).
    val q = tpl.front
    fun dist(ax: Float, ay: Float, bx: Float, by: Float) = kotlin.math.hypot(((bx - ax) * tpl.aspect).toDouble(), (by - ay).toDouble()).toFloat()
    val frontW = (dist(q[0], q[1], q[2], q[3]) + dist(q[6], q[7], q[4], q[5])) / 2f
    val frontH = (dist(q[0], q[1], q[6], q[7]) + dist(q[2], q[3], q[4], q[5])) / 2f
    // The cover template's own proportions when there is one (the outline above is foreshortened by the box's angle).
    // (Update 229, #27: without a cover frame, the console's real box shape — the outline is drawn at an angle, so an N64 or PS2 box came out squashed.)
    val realAspect = if (tpl.frameRes == null) (MediaCovers.of(platform)?.frontAspect ?: realCaseMm(platform)?.let { it.first / it.second }) else null
    val aspect = if (tpl.frameRes != null) tpl.frameAspect.coerceIn(0.4f, 2.5f)
        else if (realAspect != null && realAspect > 0f) realAspect.coerceIn(0.4f, 2.5f)
        else if (frontH > 0f) (frontW / frontH).coerceIn(0.4f, 2.5f) else 0.7f
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * aspect)
        val h = w / aspect
        // Update 119: while the face is being put together, the picture itself (usually already loaded) shows in the
        // case's place — not a dark panel first.
        if (face == null && front != null) coil.compose.AsyncImage(
            // (Update 202: trimmed like the face it stands in for.)
            mediaArtModel(front, trim = true), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, // (Update 157; 159: the scale was inside the comment)
            modifier = Modifier.size(w, h).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
        )
        Box(
            Modifier.size(w, h).drawBehind {
                val r = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                // Soft shadow under it.
                drawRoundRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(3.dp.toPx(), 5.dp.toPx()), size, r)
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
                    nc.save()
                    nc.clipRect(0f, 0f, size.width, size.height)
                    // The face, cropped to the case's front (never squashed).
                    val f = face
                    if (f != null) {
                        val scale = maxOf(size.width / f.width, size.height / f.height)
                        val cw = size.width / scale; val ch = size.height / scale
                        val sx = (f.width - cw) / 2f; val sy = (f.height - ch) / 2f
                        nc.drawBitmap(f, android.graphics.Rect(sx.toInt(), sy.toInt(), (sx + cw).toInt(), (sy + ch).toInt()), android.graphics.RectF(0f, 0f, size.width, size.height), paint)
                    }
                    // The case: the overlay picture mapped so its front quad fills this rectangle (the spine falls outside).
                    val ob = overlay.asAndroidBitmap()
                    val src = FloatArray(8) { k -> q[k] * (if (k % 2 == 0) ob.width else ob.height) }
                    val dst = floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height)
                    val m = android.graphics.Matrix()
                    if (m.setPolyToPoly(src, 0, dst, 0, 4)) { nc.concat(m); nc.drawBitmap(ob, 0f, 0f, paint) }
                    nc.restore()
                }
            }
        )
    }
}

/**
 * The game's box on a card. Seen from the front (default): the console's printed cover frame with the game's cover in
 * its window — no spine beside it. With "Box turned to show its spine" the template pack's 3D box stands still, seen a
 * little from the side, so its real spine shows. With "Disc in its case" on, disc games show the case with the spinning
 * disc half out of it. [real] ("Box art · real sizes"): the case drawn at its real size against the biggest one shown,
 * standing on the slot's floor. [caseModifier] goes on the flat case itself (the focus frame hugs the case, not the
 * slot round it). [cover] goes into the system's cover template (covers first, else the box [scan]).
 * False when there's nothing to draw a box with.
 */
@Composable
internal fun CaseArt(
    platform: String?, cover: Any?, scan: Any?, modifier: Modifier = Modifier, title: String = "", detail: Int = 360,
    real: RealCaseRef? = null, caseModifier: Modifier = Modifier,
    /** The game's own spine (BoxTexture.spine), for the box turned to its side. */
    spine: Any? = null,
    /**
     * Update 151: draw the game in its 3D case from your shape library (where there's one). Off for every caller now: Real
     * cases and Cases · real sizes are flat for every console (a PS2 game stood as a 3D case among flat ones); the 3D
     * cases belong to the 3D models look (mediaFor); 3D case + disc draws the template's 3D box for every console.
     */
    // (Update 159: on with R1 › 3D in a case look, Features.caseModels3d. Update 200: ignored — the pre-rendered 3D cases
    // are retired with the R1 3D tile; every case look draws the drawn cases. Kept so callers needn't change.)
    @Suppress("UNUSED_PARAMETER") models3d: Boolean = false
): Boolean {
    // Update 129: one console's case picked for all (R1 › Case): every game's cover in that case, never its own scan.
    val casePlatform = LookStyles.casePlatform(platform)
    val forAll = LookStyles.caseForAll()
    // (Update 235, #24: a cover you picked for the game (UserCovers) goes into the case template, never under its box scan:
    // with Original cases on the scan won and your cover showed only while the scan loaded. Every case look comes here —
    // the cards of every layout, the Grid, the TV room, the style homes — so the callers needn't be told.)
    EnsureUserCovers()
    val caseScan = if (forAll || UserCovers.isPicked(cover)) null else scan
    val caseSpine = if (forAll) null else spine
    val mm = if (real != null) realCaseMm(casePlatform) else null
    // Update 141: in another console's case, only its shape — no branding — and the game's own console's logo.
    val owner = if (forAll && platform != null && casePlatform != platform) platform else null
    // (Update 141 / 151 / 159: the shape library's pre-rendered 3D case, with or without its disc, stood here with 3D
    // models or R1 › 3D on. Update 200: retired — the case looks always draw the drawn cases below.)
    var drawn = true
    androidx.compose.runtime.CompositionLocalProvider(LocalCaseOwner provides owner) {
        if (real == null || mm == null) drawn = CaseArtBody(casePlatform, cover, caseScan, modifier, title, detail, caseModifier, caseSpine)
        else BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
            // Dp per mm: the biggest case shown just fits the slot; this one is its real size against it.
            val k = minOf(maxWidth / real.wMm, maxHeight / real.hMm)
            CaseArtBody(casePlatform, cover, caseScan, Modifier.size(k * mm.first, k * mm.second), title, detail, caseModifier, caseSpine)
        }
    }
    return drawn
}

@Composable
private fun CaseArtBody(
    platform: String?, cover: Any?, scan: Any?, modifier0: Modifier, title: String, detail: Int, caseModifier: Modifier,
    spine: Any? = null
): Boolean {
    // Settings › Game look › "Case with its cartridge / disc" (update 93): the case standing still, its cartridge or disc
    // leaning against its lower right; only the cartridge / disc moves (spin / bounce as set).
    val shell = platform?.let { MediaShell.of(it) }
    if (com.shelf.launcher.data.Features.caseWithMedia.state.value && shell != null) {
        val cov0 = MediaCovers.used(platform)
        val box0 = MediaTemplates.of(platform)?.box
        // (Update 235, #27/#38: Voltcap's plain case too, for a console without a template case.)
        val plain0 = if (cov0 == null && box0 == null) plainCaseSpec(platform) else null
        if (cov0 == null && box0 == null && plain0 == null) return false
        val aspect = cov0?.frontAspectFor(cover ?: scan) ?: box0?.let { if (it.frameRes != null) it.frameAspect else it.aspect } ?: plain0?.aspect ?: 0.7f
        val round = shell.disc || shell == MediaShell.UMD
        // The cartridge / disc's height as a share of the case's, and its own width / height.
        val mFrac = if (round) 0.6f else 0.5f
        val mAspect = if (round) 1f else shell.aspect
        BoxWithConstraints(modifier0, contentAlignment = Alignment.Center) {
            val out = 0.62f
            // Update 144: the case as big as a case on its own in the same place; its cartridge / disc sticks out to the
            // right past the slot (it used to shrink the case so both fitted — smaller than the cases with nothing beside them).
            val h = minOf(maxHeight, maxWidth / aspect)
            val caseW = h * aspect
            val mH = h * mFrac
            val mW = mH * mAspect
            Box(
                Modifier.align(Alignment.CenterStart).wrapContentSize(Alignment.CenterStart, unbounded = true)
                    .offset(x = (maxWidth - caseW) / 2).size(caseW + mW * out, h)
            ) {
                Box(Modifier.align(Alignment.TopStart).size(caseW, h)) {
                    CaseArtFace(platform, cover, scan, Modifier.fillMaxSize(), title, detail, caseModifier, allowDiscCase = false, spine = spine)
                }
                // The game's own cartridge / disc picture when it has one (update 104), else Voltcap's drawn one.
                // Update 173: matched to the case — the real cartridge / disc beside the game's real (scanned) case, Voltcap's
                // own beside its template case — whatever the drawn-media setting says.
                // (Update 202: not for an Android game — its scraped "cartridge" is store art, androidOwnCase.)
                val caseReal = com.shelf.launcher.data.Features.originalCases.state.value && scan != null && !LookStyles.caseForAll() && !androidOwnCase(platform)
                val ownMedia = if (caseReal) BoxTexture.mediaOfFront(scan) else null
                if (ownMedia != null) RealMediaPicture(ownMedia, round, Modifier.align(Alignment.BottomEnd).offset(y = -h * 0.03f).size(mW, mH))
                else PhysicalMedia(
                    shell, cover ?: scan, Modifier.align(Alignment.BottomEnd).offset(y = -h * 0.03f).size(mW, mH),
                    platform = platform, caseCover = cover
                )
            }
        }
        return true
    }
    // Settings › Game look › Moving discs, cartridges & cases: the box turns left and right and/or hops.
    val modifier = modifier0.caseMotion((cover?.hashCode() ?: 0) xor (platform?.hashCode() ?: 0))
    return CaseArtFace(platform, cover, scan, modifier, title, detail, caseModifier, allowDiscCase = true, spine = spine)
}

/** The case alone (as set: flat official case, 3D box, box turned to its spine, disc in its case). */
@Composable
private fun CaseArtFace(
    platform: String?, cover: Any?, scan: Any?, modifier: Modifier, title: String, detail: Int, caseModifier: Modifier,
    allowDiscCase: Boolean, spine: Any? = null
): Boolean {
    val tpl = MediaTemplates.of(platform)
    val cov = MediaCovers.used(platform)
    val use3d = com.shelf.launcher.data.Features.box3d.state.value
    // Update 143: a PlayStation case never shows its black PlayStation band — neither turned to its side nor printed down
    // the left of its cover scan (it's cut off): just the cover.
    val noBand = platform == "PS1"
    val side = com.shelf.launcher.data.Features.boxSpine.state.value && !noBand
    val box = tpl?.box
    val discCase = allowDiscCase && discCaseOn(tpl)
    // The game's own scanned case (update 101, Settings › Game look › "Original cases"): the scan is the case, with its
    // own spine on the box turned to its side. Falls back to the template case when the scan can't be loaded or cut.
    var scanFailed by androidx.compose.runtime.remember(scan) { androidx.compose.runtime.mutableStateOf(false) }
    val ownSpine = spine ?: BoxTexture.spineFor(scan)
    // (Update 235, #27/#38: a console with no template case (no cover template, no template box) — PS3 / PS4 / PS5, the
    // Xboxes, Neo Geo CD… — in Voltcap's plain case of its real shape and plastic (PlainCase): its scanned front under the
    // sleeve with Original cases on, else its cover. Here CaseArt drew nothing (false), so the cards fell back to a flat
    // cover — or the turned "Box (3D)" picture — cut to the slot.)
    val plain = if (box == null && cov == null) plainCaseSpec(platform) else null
    if (plain != null) {
        val useScan = com.shelf.launcher.data.Features.originalCases.state.value && scan != null && !androidOwnCase(platform)
        PlainCase(plain, if (useScan) scan else cover ?: scan, CaseArtPlaceholder, modifier, caseModifier, fallback = cover)
        return true
    }
    // Update 202: never for an Android game — its case is Voltcap's own (androidOwnCase): WWE All Stars' scraped store
    // art stood in the Thor's case stacks as a plain white "scanned case", without the Android band.
    if (com.shelf.launcher.data.Features.originalCases.state.value && scan != null && !scanFailed && !discCase && !androidOwnCase(platform)) {
        if (side && box != null) {
            Box3D(box, scan, true, title, modifier, spin = false, detail = detail, realSpine = ownSpine)
            return true
        }
        // (Update 104: no drawn 3D box round the scan any more — the scan itself, as the case.)
        val aspect = cov?.frontAspect ?: box?.let { if (it.frameRes != null) it.frameAspect else null } ?: realCaseMm(platform)?.let { it.first / it.second } ?: 0.7f
        ScanCase(scan, aspect, modifier, caseModifier, onFail = { scanFailed = true }, cover = cover, cutLeft = if (noBand) PS1_BAND else 0f)
        return true
    }
    // The official case, as on a shelf, facing you: the console's printed cover frame (its banner and logo) with the
    // game's cover in its window. The spine only shows on the 3D box seen from the side ("Box turned to show its spine").
    if (cov != null && !discCase && (box == null || (!use3d && !side))) {
        CoverCase(cov, cover ?: scan, CaseArtPlaceholder, modifier, caseModifier = caseModifier)
        return true
    }
    if (box == null) return false
    val (front, isBox) = boxFront(box, cover, scan)
    // Update 151: 3D case + disc — the 3D box with the disc half out of it (DiscCase3D.kt); PS1 keeps its flat case.
    if (discCase && disc3dOn(platform) && platform != "PS1") DiscInBox3D(tpl!!, cover, scan, !scanFailed, modifier, title, detail, platform)
    else if (discCase) DiscInCase(
        tpl!!, front, isBox, cover ?: scan, modifier, title, detail,
        // Update 104: the game's own case scan and its own disc picture, when there are.
        caseScan = if (com.shelf.launcher.data.Features.originalCases.state.value && !scanFailed) scan else null,
        realDisc = BoxTexture.mediaFor(scan),
        caseAspect = cov?.frontAspect ?: realCaseMm(platform)?.let { it.first / it.second },
        platform = platform,
        // (Update 202: its real spine on the 3D box's side, as the closed case shows it.)
        realSpine = if (!LookStyles.spineForAll()) ownSpine else null
    )
    // (Update 202: the game's real spine whatever Original cases says, as its closed case and the Spines look show it —
    // not with one console's spine picked for all.)
    else if (side) Box3D(box, front, isBox, title, modifier, spin = false, detail = detail, realSpine = if (!LookStyles.spineForAll()) ownSpine else null)
    else FrontBox(box, front, isBox, modifier, detail, platform = platform) // (Update 229, #27)
    return true
}

internal val CaseArtPlaceholder = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2A2D38), androidx.compose.ui.graphics.Color(0xFF181A22)))
