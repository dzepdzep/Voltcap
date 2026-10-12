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
    val useLabelFrame = labelScan == null && tpl.label != null
    val cart = if (useLabelFrame) tpl.label else tpl.cart
    if (disc == null && cart == null) return false
    val art = labelScan ?: cover
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (disc != null) {
            val side = minOf(maxWidth, maxHeight) * (if (smallDisc) 0.7f else 1f)
            Box(Modifier.size(side, side)) {
                // Discs are printed paper-white where the art is thin; the art fills the printable ring.
                Box(Modifier.fillMaxSize().clip(DiscRing(MediaTemplates.RING_INNER, MediaTemplates.RING_OUTER)).background(Color(0xFFE9E6DF))) {
                    if (art != null) AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
                    if (art != null) AsyncImage(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Image(painterResource(cart.res), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            }
        }
    }
    return true
}

/** A game picture as a plain (software) bitmap the perspective drawing can use; null while it loads. */
@Composable
internal fun rememberSoftBitmap(model: Any?, maxSide: Int = 720): android.graphics.Bitmap? {
    val context = LocalContext.current
    // (Update 147: a picture already in memory is there from the first frame — a case or piece coming back on screen
    // used to go blank for a moment while it was looked up again.)
    val key = remember(model, maxSide) { (model as? String)?.let { coil.memory.MemoryCache.Key("soft|$maxSide|$it") } }
    val cached = remember(key) { key?.let { coil.Coil.imageLoader(context).memoryCache?.get(it)?.bitmap } }
    val state by produceState(cached, model) {
        if (model == null) { value = null; return@produceState }
        if (cached != null) { value = cached; return@produceState }
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                // (Update 147: always a still picture — an animated cover came back as an animated drawable, not a bitmap,
                // and the 3D pieces and cases stayed a plain grey card.)
                val req = coil.request.ImageRequest.Builder(context).data(model).size(maxSide, maxSide)
                    .precision(coil.size.Precision.INEXACT).allowHardware(false)
                    .setParameter(com.shelf.launcher.data.AnimatedArt.PARAM_STILL, true)
                    .apply { if (key != null) memoryCacheKey(key) }.build()
                drawableBitmap((coil.Coil.imageLoader(context).execute(req) as? coil.request.SuccessResult)?.drawable, maxSide)
            }.getOrNull()
        }
    }
    return state
}

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
    val art = rememberSoftBitmap(front, detail)
    return remember(art, framePic) {
        val src = art ?: return@remember null
        val frame = framePic?.asAndroidBitmap()
        val win = tpl.frameWin
        if (frame == null || win == null) {
            // A ready-made box scan: cut to the template front's real proportions so the corners map without squashing it.
            if (frontIsBox && tpl.frameRes != null) cropToAspect(src, tpl.frameAspect) else src
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
internal fun FrontBox(tpl: BoxTpl, front: Any?, frontIsBox: Boolean, modifier: Modifier = Modifier, detail: Int = 360) {
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
    val aspect = if (tpl.frameRes != null) tpl.frameAspect.coerceIn(0.4f, 2.5f) else if (frontH > 0f) (frontW / frontH).coerceIn(0.4f, 2.5f) else 0.7f
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * aspect)
        val h = w / aspect
        // Update 119: while the face is being put together, the picture itself (usually already loaded) shows in the
        // case's place — not a dark panel first.
        if (face == null && front != null) coil.compose.AsyncImage(
            front, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
    spine: Any? = null
): Boolean {
    // Update 129: one console's case picked for all (R1 › Case): every game's cover in that case, never its own scan.
    val casePlatform = LookStyles.casePlatform(platform)
    val forAll = LookStyles.caseForAll()
    val caseScan = if (forAll) null else scan
    val caseSpine = if (forAll) null else spine
    val mm = if (real != null) realCaseMm(casePlatform) else null
    // Update 141: in another console's case, only its shape — no branding — and the game's own console's logo.
    val owner = if (forAll && platform != null && casePlatform != platform) platform else null
    // Update 141: with 3D models on (Settings › Game look › 3D models), the case looks show the game in its console's
    // 3D case from your shape library, standing as Stand says — consoles without one keep their flat case.
    val model = if (owner == null) Models3D.caseModel(casePlatform) else null
    if (model != null) {
        val yaw = Models3D.standYaw(LocalMediaFocused.current)
        val nm = title.takeIf { it.isNotBlank() }
        // Update 144: with "Box art · real sizes" the 3D case is sized like the flat cases — its real size against the
        // biggest case shown, standing on the slot's floor, the focus frame round the case (it used to fill the whole slot).
        if (real != null && mm != null) BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
            val k = minOf(maxWidth / real.wMm, maxHeight / real.hMm)
            Model3DArt(model, cover ?: scan, yaw, Modifier.size(k * mm.first, k * mm.second).then(caseModifier), name = nm, platform = platform)
        }
        else Model3DArt(model, cover ?: scan, yaw, modifier.then(caseModifier), name = nm, platform = platform)
        return true
    }
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
        val cov0 = if (com.shelf.launcher.data.Features.coverTemplates.state.value) MediaCovers.of(platform) else null
        val box0 = MediaTemplates.of(platform)?.box
        if (cov0 == null && box0 == null) return false
        val aspect = cov0?.frontAspect ?: box0?.let { if (it.frameRes != null) it.frameAspect else it.aspect } ?: 0.7f
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
                val ownMedia = BoxTexture.mediaFor(scan)
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
    val cov = if (com.shelf.launcher.data.Features.coverTemplates.state.value) MediaCovers.of(platform) else null
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
    if (com.shelf.launcher.data.Features.originalCases.state.value && scan != null && !scanFailed && !discCase) {
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
    if (discCase) DiscInCase(
        tpl!!, front, isBox, cover ?: scan, modifier, title, detail,
        // Update 104: the game's own case scan and its own disc picture, when there are.
        caseScan = if (com.shelf.launcher.data.Features.originalCases.state.value && !scanFailed) scan else null,
        realDisc = BoxTexture.mediaFor(scan),
        caseAspect = cov?.frontAspect ?: realCaseMm(platform)?.let { it.first / it.second },
        platform = platform
    )
    else if (side) Box3D(box, front, isBox, title, modifier, spin = false, detail = detail, realSpine = if (com.shelf.launcher.data.Features.originalCases.state.value) ownSpine else null)
    else FrontBox(box, front, isBox, modifier, detail)
    return true
}

internal val CaseArtPlaceholder = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2A2D38), androidx.compose.ui.graphics.Color(0xFF181A22)))
