package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game

/**
 * Update 134: a game as a real 3D case (the carousel's "3D cases", and its Arc, Spine wall and Turned row views): a box
 * with the game's cover on the front, its spine on the left side (the same spine the Spines look uses: the game's real
 * spine scan, else your console spine with the name, else a plain one with the name), the plastic edge on the right and
 * a lid on top, drawn in true perspective at any turn — [yaw] in degrees (positive: the front turns to the right, so the
 * spine comes round to face you; 90 = spine on). [shade] darkens it (0–1); [reflect] draws its mirror image on a glossy
 * floor under it; [frame] outlines the front in [frameColor]. Fills [modifier]: the front is as big as fits, centred.
 */
@Composable
internal fun Case3D(
    g: Game, cover: Any?, physical: List<Pair<String, String>>?, yaw: Float, aspect: Float,
    modifier: Modifier, shade: Float = 0f, reflect: Boolean = false,
    frame: Boolean = false, frameColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    pitch: Float = 4f,
    /**
     * Update 162: the turn and shade read while drawing (the carousel's moving place), so a moving case only redraws —
     * nothing rebuilt each frame. Null: [yaw] / [shade].
     */
    yawOf: (() -> Float)? = null,
    shadeOf: (() -> Float)? = null,
    /**
     * Update 162: the box art looks (Box art, Real cases): the game's own scanned case front on the case when there is
     * one (Original cases), as the flat case and the 3D model cases show it — not only its cover.
     */
    realFront: Boolean = false,
    /** Update 162: a picture for the case's back (the game window's inspect turns it round); null: plain plastic. */
    back: Any? = null
) {
    val title = gameDisplayName(g.name)
    // The spine, in the same order as the Spines look (GameSpine): the real scan first, then your console spine.
    val scans = physical ?: HomeScans.map[g.stableKey]
    val ownFront = if (realFront && Features.originalCases.state.value && !LookStyles.caseForAll()) {
        remember(scans, g.platform) { BoxTexture.front(scans, g.platform) }
    } else null
    val front = rememberSoftBitmap(ownFront ?: cover, 480)
    // Not looked up yet (far along the Spine wall): ask once it has stayed on screen a moment, as the Spines look does.
    if (physical == null && !HomeScans.map.containsKey(g.stableKey)) {
        androidx.compose.runtime.LaunchedEffect(g.stableKey) { kotlinx.coroutines.delay(400); HomeScans.need?.invoke(g) }
    }
    val forAll = LookStyles.spineForAll()
    val realSpine = if (forAll) null else scans?.firstOrNull { it.first == "Spine" }?.second
    val own = realSpine ?: if (Features.originalCases.state.value && !forAll) BoxTexture.spine(scans, g.platform) else null
    val ownBmp = rememberSoftBitmap(own, 600)
    // (Update 166: Voltcap's own Steam / Android case always — its spine and its front band, see below.)
    val ownCase = MediaCovers.own(g.platform).takeIf { !forAll }
    val tpl = if (Features.coverTemplates.state.value || forAll || ownCase != null) CustomSpines.of(LookStyles.spinePlatform(g.platform)) else null
    // (Made once per game, away from the screen's thread: SpineCache.)
    val tplSpine = rememberTitledSpine(tpl?.res, title, landscape = false)
    val spine: android.graphics.Bitmap = remember(ownBmp, tplSpine, title) {
        ownBmp?.let { orientSpine(it, landscape = false) }
            ?: tplSpine.bitmap
            // (A plain dark strip for the moment your console spine is being made, then the plain one with the name.)
            ?: if (!tplSpine.done) SpineCache.WAITING else plainSpine(title)
    }
    // How thick the case is (its spine's width against its height): the spine picture's own shape, kept to real cases'.
    val depth = remember(spine, tpl, ownBmp, ownCase) {
        val own = spine.width.toFloat() / spine.height.coerceAtLeast(1)
        // Update 171: Voltcap's own Steam / Android cases slim, like a game card's case (they were as thick as a DVD case).
        if (ownCase != null && ownBmp == null) return@remember OWN_CASE_DEPTH
        // (Update 166: without a spine picture, the console's real case thickness — a Game Boy box is far thicker for its
        // size than a DVD case — not always a DVD case's.)
        (if (ownBmp != null) own else tpl?.aspect ?: realCaseDepth(g.platform) ?: 0.085f).coerceIn(0.05f, 0.22f)
    }
    // Update 166: a Steam / Android game's case front is Voltcap's own: the game's art in its window, the band on top.
    // Update 169: and with Settings › Cover templates on, every console's case front is its template too (the band, the
    // printed border) with the cover in its window — as the flat case shows it; the 3D cases had the bare cover. Not on
    // the game's own scanned front (that is the real thing), nor with R1 › Case (another console's shape for all).
    val caseTpl = ownCase ?: if (Features.coverTemplates.state.value && !forAll && !LookStyles.caseForAll()) MediaCovers.of(g.platform) else null
    val tplFront = caseTpl.takeIf { ownFront == null }
    // (Update 169: Steam / Android cases at a SteamGridDB cover's shape — case3dAspect — so the cover fills the window.)
    val fit = tplFront?.takeIf { it.isOwn() && Features.ownCaseFit.state.value }?.fitTo(SGDB_COVER_ASPECT)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val band = remember(tplFront?.frontRes, fit?.aspect) {
        tplFront?.let { t ->
            runCatching {
                val b = android.graphics.BitmapFactory.decodeResource(ctx.resources, t.frontRes)
                if (fit != null && b != null) fittedFrameBitmap(b, t, fit, b.width.coerceAtMost(600)) else b
            }.getOrNull()
        }
    }
    // Update 173: a game without a cover (an app) gets its plain front — its icon and name — in its case template too
    // (an Android game's case had no Android band then), and the app's icon on it.
    val noArt = cover == null && ownFront == null
    val appIcon = if (noArt && g.sourceType == com.shelf.launcher.data.SourceType.APP) rememberAppIcon(g.packageName) else null
    val noCover = remember(title, g.hue, noArt, appIcon) { if (noArt) plainFront(title, g.hue, appIcon?.asAndroidBitmap()) else null }
    val face = remember(front, noCover, aspect, band) {
        (front ?: noCover)?.let { f ->
            // (Update 171: not over a box scan used as the cover — it has its own band and logo, they showed twice.)
            val whole = tplFront != null && isBoxFront(tplFront, f.width.toFloat() / f.height.coerceAtLeast(1))
            if (band != null && tplFront != null && !whole) framed(f, band, fit?.win ?: tplFront.win) else cropTo(f, aspect)
        }
    }
    // (Update 170: a printed back is made from its parts; a scanned one is a picture.)
    val printedBmp = rememberPrintedBack(back as? PrintedBack)
    val scannedBmp = rememberSoftBitmap(back.takeIf { it !is PrintedBack }, 480)
    val backBmp = printedBmp ?: scannedBmp
    val backFace = remember(backBmp, aspect) { backBmp?.let { cropTo(it, aspect) } }
    // Update 166: while its cover is still loading, the front in the game's own colour (not near-black: scrolling a
    // carousel of 3D cases showed dark fronts for a moment).
    val loading = remember(g.hue) { hueFront(g.hue) }
    val frameArgb = frameColor.toArgb()
    Canvas(modifier) {
        val yawNow = yawOf?.invoke() ?: yaw
        val shadeNow = shadeOf?.invoke() ?: shade
        val fit = minOf(size.height, size.width / aspect) * 0.94f
        val h = fit
        val w = h * aspect
        val d = h * depth
        drawIntoCanvas { canvas ->
            CaseDraw.draw(
                canvas.nativeCanvas, size.width / 2f, size.height / 2f, w, h, d, yawNow, pitch,
                face ?: noCover ?: loading, spine, (1f - shadeNow).coerceIn(0f, 1f), reflect,
                if (frame) frameArgb else 0, 2.5f * density, backFace
            )
        }
    }
}

/** Update 171: Steam / Android 3D cases' thickness against their height. */
internal const val OWN_CASE_DEPTH = 0.05f

/**
 * Width / height of a game's 3D case: its console's real case shape (update 166: also with the cover templates off — a
 * Game Boy Advance game opened as a DVD-shaped box), else a game case's.
 */
internal fun case3dAspect(g: Game, mixed: Boolean): Float {
    // (Update 169: a Steam / Android case at a SteamGridDB cover's shape, its band on top — Case3D.)
    if (Features.ownCaseFit.state.value && !LookStyles.spineForAll()) MediaCovers.own(g.platform)?.let { o -> return (o.fitTo(SGDB_COVER_ASPECT)?.aspect ?: o.frontAspect).coerceIn(0.55f, 1.45f) }
    // (Update 166: in lists mixing consoles too — each case keeps its own shape there, as the flat box art does.)
    realCaseMm(g.platform)?.let { (w, h) -> if (w > 0f && h > 0f) return (w / h).coerceIn(0.55f, 1.45f) }
    return (if (!mixed && Features.coverTemplates.state.value) MediaCovers.of(g.platform)?.frontAspect else null)?.coerceIn(0.55f, 1.45f) ?: 0.71f
}

/**
 * Update 166: a console's case thickness against its height (approximate, the usual US / EU release), for a case without
 * a spine picture; null: a DVD case's.
 */
internal fun realCaseDepth(platform0: String?): Float? {
    val platform = LookStyles.casePlatform(platform0)
    val mm = realCaseMm(platform) ?: return null
    val d = when (platform) {
        "GB", "GBC", "GBA", "NeoGeoPocket", "WonderSwan", "PokemonMini" -> 24f
        "GameGear", "Lynx" -> 24f
        "NES", "SNES", "N64", "Atari2600", "Atari7800", "ColecoVision", "VirtualBoy", "Jaguar" -> 38f
        "Genesis", "MasterSystem", "32X", "NeoGeo" -> 30f
        "NDS", "3DS", "PSVita" -> 14f
        "Switch" -> 11f
        "PS1", "Dreamcast", "NeoGeoCD", "PCEngineCD" -> 10.4f
        "Saturn", "SegaCD", "3DO" -> 18f
        "PCEngine" -> 20f
        "PSP" -> 14f
        else -> 14f
    }
    // (Case3D draws the thickness against the case's height.)
    return d / mm.second.coerceAtLeast(1f)
}

/**
 * Update 166: how big a console's case shows when it's opened (a game turned in the game window), against a DVD case
 * (190 mm): a Game Boy box comes out smaller than a GameCube case, as they are. Bigger cases fill the room as before.
 */
internal fun realCaseScale(platform: String?): Float =
    // (Update 166: softened — the small ones were hard to see; a Game Boy box about 80 % of a DVD case.)
    realCaseMm(platform)?.let { (w, h) -> Math.pow((maxOf(w, h) / 190f).toDouble(), 0.6).toFloat().coerceIn(0.8f, 1f) } ?: 1f

/** Update 166: [art] cut to the template's window [win] (l, t, r, b fractions) and drawn under [frame] (its band on top). */
private fun framed(art: android.graphics.Bitmap, frame: android.graphics.Bitmap, win: FloatArray): android.graphics.Bitmap = runCatching {
    val w = frame.width.coerceAtMost(600); val h = (w * frame.height / frame.width.toFloat()).toInt().coerceAtLeast(1)
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val dst = android.graphics.RectF(win[0] * w, win[1] * h, win[2] * w, win[3] * h)
    val cut = cropTo(art, dst.width() / dst.height().coerceAtLeast(1f))
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    c.drawBitmap(cut, null, dst, p)
    c.drawBitmap(frame, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), p)
    out
}.getOrDefault(art)

/** [src] cut in the middle to [aspect] (width / height), no stretching. */
private fun cropTo(src: android.graphics.Bitmap, aspect: Float): android.graphics.Bitmap {
    val cur = src.width.toFloat() / src.height
    if (aspect <= 0f || kotlin.math.abs(cur - aspect) < 0.02f) return src
    val w = if (cur > aspect) (src.height * aspect).toInt().coerceAtLeast(1) else src.width
    val h = if (cur > aspect) src.height else (src.width / aspect).toInt().coerceAtLeast(1)
    return runCatching { android.graphics.Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h) }.getOrDefault(src)
}

/** A plain dark spine with the name up it (reads top to bottom), for a game without any spine picture. */
private fun plainSpine(title: String): android.graphics.Bitmap {
    val b = android.graphics.Bitmap.createBitmap(56, 600, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    c.drawColor(0xFF1C1E26.toInt())
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8E8EE.toInt(); textSize = 32f; isFakeBoldText = true
    }
    val room = 560f
    while (p.measureText(title) > room && p.textSize > 16f) p.textSize -= 1f
    var t = title
    if (p.measureText(t) > room) {
        while (t.length > 2 && p.measureText("$t…") > room) t = t.dropLast(1)
        t = t.trimEnd() + "…"
    }
    c.save(); c.rotate(90f); c.translate(20f, -(28f - 0.36f * p.textSize))
    c.drawText(t, 0f, 0f, p); c.restore()
    return b
}

/** A front for a game without a cover: its colour with the name across it. */
/** Update 166: a plain front in [hue] (a soft top-to-bottom shade), for the moment a cover is loading. */
private fun hueFront(hue: Int): android.graphics.Bitmap {
    val b = android.graphics.Bitmap.createBitmap(36, 50, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val h = ((hue % 360) + 360f) % 360f
    val p = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, 50f, android.graphics.Color.HSVToColor(floatArrayOf(h, 0.42f, 0.5f)),
            android.graphics.Color.HSVToColor(floatArrayOf(h, 0.5f, 0.3f)), android.graphics.Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, 36f, 50f, p)
    return b
}

private fun plainFront(title: String, hue: Int, icon: android.graphics.Bitmap? = null): android.graphics.Bitmap {
    val b = android.graphics.Bitmap.createBitmap(360, 500, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    c.drawColor(android.graphics.Color.HSVToColor(floatArrayOf(((hue % 360) + 360f) % 360f, 0.45f, 0.32f)))
    // (Update 173: an app's icon above its name.)
    if (icon != null) {
        val side = 170f
        c.drawBitmap(icon, null, android.graphics.RectF(180f - side / 2f, 110f, 180f + side / 2f, 110f + side), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        c.translate(0f, 110f)
    }
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF2F2F6.toInt(); textSize = 40f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER
    }
    // Word-wrapped over up to four lines.
    val lines = ArrayList<String>()
    var cur = ""
    for (word in title.split(' ')) {
        val next = if (cur.isEmpty()) word else "$cur $word"
        if (p.measureText(next) > 320f && cur.isNotEmpty()) { lines.add(cur); cur = word } else cur = next
    }
    if (cur.isNotEmpty()) lines.add(cur)
    val shown = lines.take(4)
    val top = 250f - (shown.size - 1) * 24f
    shown.forEachIndexed { i, l -> c.drawText(l, 180f, top + i * 48f + 14f, p) }
    return b
}

/** The perspective drawing of one case (plain maths, no Compose): see [Case3D]. */
private object CaseDraw {
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    private val fade = android.graphics.Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN) }
    private val matrix = android.graphics.Matrix()
    private val path = android.graphics.Path()
    // Update 162: made once, not per drawing (a moving carousel redraws every case each frame): the corners, the
    // picture's corners, the light filters (one per brightness step) and the gloss / fade shaders, placed by a matrix.
    private val out = FloatArray(8)
    private val src = FloatArray(8)
    private val filters = arrayOfNulls<android.graphics.ColorFilter>(256)
    private val shaderMatrix = android.graphics.Matrix()
    private val m9 = FloatArray(9)
    private val gloss = android.graphics.LinearGradient(0f, 0f, 1f, 0f,
        intArrayOf(0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x14FFFFFF), floatArrayOf(0f, 0.45f, 0.8f, 1f), android.graphics.Shader.TileMode.CLAMP)
    private val fadeDown = android.graphics.LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(0xFF000000.toInt(), 0x00000000), null, android.graphics.Shader.TileMode.CLAMP)

    // The faces' corners (TL, TR, BR, BL as seen from outside, upright), in half sizes: x, y, z of each.
    private val FRONT = floatArrayOf(-1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f)
    private val SPINE = floatArrayOf(-1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, -1f)
    private val EDGE = floatArrayOf(1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f)
    private val TOP = floatArrayOf(-1f, -1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, -1f, -1f, 1f)
    private val BACK = floatArrayOf(1f, -1f, -1f, -1f, -1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f)
    // Each face's outward direction (for its light).
    private val NORMALS = arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, -1f))
    private val FACES = arrayOf(FRONT, SPINE, EDGE, TOP, BACK)

    @Synchronized
    fun draw(
        c: android.graphics.Canvas, cx: Float, cy: Float, w: Float, h: Float, d: Float, yaw: Float, pitch: Float,
        front: android.graphics.Bitmap?, spine: android.graphics.Bitmap, light: Float, reflect: Boolean,
        frameArgb: Int, frameWidth: Float,
        /** Update 162: the back's picture (null: plain plastic). */
        back: android.graphics.Bitmap? = null
    ) {
        val ty = Math.toRadians(yaw.toDouble()); val tp = Math.toRadians(pitch.toDouble())
        val cy0 = kotlin.math.cos(ty).toFloat(); val sy0 = kotlin.math.sin(ty).toFloat()
        val cp = kotlin.math.cos(tp).toFloat(); val sp = kotlin.math.sin(tp).toFloat()
        val focal = h * 3.4f
        val hw = w / 2f; val hh = h / 2f; val hd = d / 2f
        // Projects corner [i] of [face] (mirrored under the floor when [mirror]) into [out] at [i]; false when behind the eye.
        fun project(face: FloatArray, i: Int, mirror: Boolean): Boolean {
            val x = face[i * 3] * hw
            var y = face[i * 3 + 1] * hh
            val z = face[i * 3 + 2] * hd
            if (mirror) y = h - y
            // Turn round the upright, then tip towards you.
            val x1 = x * cy0 + z * sy0
            val z1 = -x * sy0 + z * cy0
            val y2 = y * cp + z1 * sp
            val z2 = -y * sp + z1 * cp
            val k = focal - z2
            if (k <= 1f) return false
            val s = focal / k
            out[i * 2] = cx + x1 * s
            out[i * 2 + 1] = cy + y2 * s
            return true
        }
        fun area(): Float {
            var a = 0f
            for (i in 0 until 4) { val j = (i + 1) % 4; a += out[i * 2] * out[j * 2 + 1] - out[j * 2] * out[i * 2 + 1] }
            return a
        }
        // How much light a face gets: full facing you, darker the more it turns away (the top a little lit from above).
        fun lightOf(n: FloatArray): Float {
            val nz1 = -n[0] * sy0 + n[2] * cy0
            val nz = -n[1] * sp + nz1 * cp
            val up = if (n[1] < 0f) 0.12f else 0f
            return ((0.5f + 0.5f * nz.coerceIn(0f, 1f)) + up).coerceAtMost(1f) * light
        }
        fun filterOf(l: Float): android.graphics.ColorFilter? {
            if (l >= 0.995f) return null
            val v = (l.coerceIn(0f, 1f) * 255f).toInt()
            return filters[v] ?: android.graphics.LightingColorFilter(android.graphics.Color.rgb(v, v, v), 0).also { filters[v] = it }
        }
        fun pathOf(): android.graphics.Path {
            path.reset()
            path.moveTo(out[0], out[1]); path.lineTo(out[2], out[3]); path.lineTo(out[4], out[5]); path.lineTo(out[6], out[7]); path.close()
            return path
        }
        fun warp(bmp: android.graphics.Bitmap, filter: android.graphics.ColorFilter?) {
            src[0] = 0f; src[1] = 0f; src[2] = bmp.width.toFloat(); src[3] = 0f
            src[4] = bmp.width.toFloat(); src[5] = bmp.height.toFloat(); src[6] = 0f; src[7] = bmp.height.toFloat()
            matrix.reset()
            if (matrix.setPolyToPoly(src, 0, out, 0, 4)) {
                paint.colorFilter = filter
                c.save(); c.concat(matrix); c.drawBitmap(bmp, 0f, 0f, paint); c.restore()
                paint.colorFilter = null
            }
        }
        fun drawFaces(mirror: Boolean) {
            for (f in FACES.indices) {
                val face = FACES[f]
                var ok = true
                for (i in 0 until 4) if (!project(face, i, mirror)) { ok = false; break }
                if (!ok) continue
                val a = area()
                // Facing you (the mirror image winds the other way).
                if (if (mirror) a >= 0f else a <= 0f) continue
                val l = lightOf(NORMALS[f])
                when (f) {
                    0 -> {
                        if (front != null) warp(front, filterOf(l)) else {
                            fill.shader = null; fill.color = scaled(0xFF2A2D36.toInt(), l); c.drawPath(pathOf(), fill)
                        }
                        if (!mirror) {
                            // The case's glossy plastic over the cover, and its thin rim.
                            // (The unit gloss laid from the top-left corner to the bottom-right one.)
                            val gx = out[4] - out[0]; val gy = out[5] - out[1]
                            m9[0] = gx; m9[1] = -gy; m9[2] = out[0]; m9[3] = gy; m9[4] = gx; m9[5] = out[1]
                            m9[6] = 0f; m9[7] = 0f; m9[8] = 1f
                            shaderMatrix.setValues(m9)
                            gloss.setLocalMatrix(shaderMatrix)
                            fill.shader = gloss
                            c.drawPath(pathOf(), fill)
                            fill.shader = null
                            line.strokeWidth = 1f; line.color = scaled(0x55FFFFFF, light)
                            c.drawPath(pathOf(), line)
                            if (frameArgb != 0) { line.strokeWidth = frameWidth; line.color = frameArgb; c.drawPath(pathOf(), line) }
                        }
                    }
                    1 -> warp(spine, filterOf(l))
                    // (Update 162: the back's own picture when there is one.)
                    4 -> if (back != null) warp(back, filterOf(l)) else {
                        fill.shader = null
                        fill.color = scaled(0xFF22242C.toInt(), l)
                        c.drawPath(pathOf(), fill)
                    }
                    else -> {
                        // The plastic: the open edge, the top and the back.
                        fill.shader = null
                        fill.color = scaled(if (f == 3) 0xFF3A3D47.toInt() else 0xFF22242C.toInt(), l)
                        c.drawPath(pathOf(), fill)
                    }
                }
            }
        }
        if (reflect) {
            // The floor line (the case's foot) and the mirror image under it, fading out.
            project(FRONT, 2, false); project(FRONT, 3, false)
            val floorY = maxOf(out[5], out[7])
            val l = c.saveLayerAlpha(cx - w * 2f, floorY - d, cx + w * 2f, floorY + h * 1.2f, 80)
            drawFaces(true)
            shaderMatrix.reset()
            shaderMatrix.setScale(1f, h * 0.55f)
            shaderMatrix.postTranslate(0f, floorY)
            fadeDown.setLocalMatrix(shaderMatrix)
            fade.shader = fadeDown
            c.drawRect(cx - w * 2f, floorY - d, cx + w * 2f, floorY + h * 1.2f, fade)
            fade.shader = null
            c.restoreToCount(l)
        }
        drawFaces(false)
    }

    /** [argb] with its colour times [l] (alpha kept). */
    private fun scaled(argb: Int, l: Float): Int {
        val k = l.coerceIn(0f, 1f)
        val a = argb ushr 24
        val r = (((argb shr 16) and 255) * k).toInt(); val g = (((argb shr 8) and 255) * k).toInt(); val b = ((argb and 255) * k).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
