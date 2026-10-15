package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    back: Any? = null,
    /** Update 188: how much of [modifier]'s room the front takes (the rest is room for its turn). */
    fill: Float = 0.94f,
    /** Update 189: how far the lid is open (0…1, read while drawing; CaseOpen.kt); null: it never opens. */
    openOf: (() -> Float)? = null,
    /** Update 189: the disc / card inside, for an opening case. */
    inside: CaseItem? = null,
    /** Update 189: whether the disc / card still lies in the case (read while drawing). */
    showItemOf: (() -> Boolean)? = null
) {
    val title = gameDisplayName(g.name)
    // Update 191: the real case this game came in (its console and region; CaseCatalog.kt).
    val regionPref = Features.caseRegion.state.value
    val casePlatformNow = LookStyles.casePlatform(g.platform)
    val spec = remember(g.stableKey, g.platform, regionPref, casePlatformNow) { CaseCatalog.forGame(g) }
    // The spine, in the same order as the Spines look (GameSpine): the real scan first, then your console spine.
    // (Update 208: without flat-colour stand-ins — ScreenScraper's green fill for a missing back — so the next picture in
    // line shows instead: FlatArt.)
    val flatVersion = FlatArt.version.intValue
    // (Update 208: this game's own scans only — HomeScans.of — so another game's scans arriving don't rebuild this case.)
    val homeScans = if (physical == null) HomeScans.of(g.stableKey) else null
    val scans = remember(physical, homeScans, flatVersion) { (physical ?: homeScans).withoutFlat() }
    // (Update 202: never for an Android game — its case is Voltcap's own with the Android band, androidOwnCase; a scraped
    // "box" of it is store art, it stood bare without the band.)
    val ownFront = if (realFront && Features.originalCases.state.value && !LookStyles.caseForAll() && !androidOwnCase(g.platform)) {
        remember(scans, g.platform) { BoxTexture.front(scans, g.platform) }
    } else null
    // (Update 202: flat strips that are part of the cover's / front scan's file trimmed off, TrimMargins.kt — the
    // carousel's warmSoft for 3D cases asks the same way.)
    val front = rememberSoftBitmap(ownFront ?: cover, 480, trim = true)
    // Not looked up yet (far along the Spine wall): ask once it has stayed on screen a moment, as the Spines look does.
    val scansKnown = physical != null || HomeScans.knownHere(g.stableKey)
    if (!scansKnown) {
        androidx.compose.runtime.LaunchedEffect(g.stableKey) { kotlinx.coroutines.delay(400); HomeScans.need?.invoke(g) }
    }
    val forAll = LookStyles.spineForAll()
    val realSpine = if (forAll) null else scans?.firstOrNull { it.first == "Spine" }?.second
    val own = realSpine ?: if (Features.originalCases.state.value && !forAll) BoxTexture.spine(scans, g.platform) else null
    // (Update 205: loaded with its state — see [spine] below.)
    val ownLoad = rememberSoftLoad(own, 600)
    val ownBmp = ownLoad.bitmap
    // Update 205: the game's real spine is on its way (or its scans aren't known yet): a neutral strip meanwhile, never
    // Voltcap's own console spine that then pops to the real one.
    val spinePending = (own != null && !ownLoad.done) ||
        (!scansKnown && g.sourceType == com.shelf.launcher.data.SourceType.ROM)
    // (Update 166: Voltcap's own Steam / Android case always — its spine and its front band, see below.)
    val ownCase = MediaCovers.own(g.platform).takeIf { !forAll }
    val tpl = if (Features.coverTemplates.state.value || forAll || ownCase != null) CustomSpines.of(LookStyles.spinePlatform(g.platform)) else null
    // (Made once per game, away from the screen's thread: SpineCache.)
    val tplSpine = rememberTitledSpine(tpl?.res, title, landscape = false)
    val spine: android.graphics.Bitmap = remember(ownBmp, tplSpine, title, spinePending) {
        ownBmp?.let { b -> DerivedArt.of(b, "spine|0") { orientSpine(b, landscape = false) } }
            ?: (if (spinePending) SpineCache.WAITING else null)
            ?: tplSpine.bitmap
            // (A plain dark strip for the moment your console spine is being made, then the plain one with the name.)
            ?: if (!tplSpine.done) SpineCache.WAITING else DerivedArt.named("plainSpine|$title") { plainSpine(title) }
    }
    // How thick the case is (its spine's width against its height): the spine picture's own shape, kept to real cases'.
    val depth = remember(spine, tpl, ownBmp, ownCase, spec) {
        val own = spine.width.toFloat() / spine.height.coerceAtLeast(1)
        // Update 171: Voltcap's own Steam / Android cases slim, like a game card's case (they were as thick as a DVD case).
        if (ownCase != null && ownBmp == null) return@remember OWN_CASE_DEPTH
        // (Update 166: without a spine picture, the console's real case thickness — a Game Boy box is far thicker for its
        // size than a DVD case — not always a DVD case's.)
        // (Update 191: the real case's thickness for its region — a PAL PlayStation case is deeper.)
        (if (ownBmp != null) own else spec?.depth ?: tpl?.aspect ?: realCaseDepth(g.platform) ?: 0.085f).coerceIn(0.05f, 0.22f)
    }
    // Update 166: a Steam / Android game's case front is Voltcap's own: the game's art in its window, the band on top.
    // Update 169: and with Settings › Cover templates on, every console's case front is its template too (the band, the
    // printed border) with the cover in its window — as the flat case shows it; the 3D cases had the bare cover. Not on
    // the game's own scanned front (that is the real thing), nor with R1 › Case (another console's shape for all).
    // (Update 191: not a template made for another region's shape — a Super Famicom box isn't a US SNES box's shape.)
    val caseTpl = ownCase ?: if (Features.coverTemplates.state.value && !forAll && !LookStyles.caseForAll()) MediaCovers.of(g.platform)
        ?.takeIf { t -> spec == null || spec.shell == CaseShell.JEWEL || kotlin.math.abs(t.frontAspect / spec.aspect - 1f) < 0.15f } else null
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
    // Update 188: a scanned front (or a box scan used as the cover) keeps its own shape — the case takes it, within a
    // quarter of the console's case shape — instead of being cut to the template's shape: a PlayStation case lost the
    // right side of its cover (the publisher's logo) that way.
    val framedFront = front != null && band != null && tplFront != null &&
        !isBoxFront(tplFront, front.width.toFloat() / front.height.coerceAtLeast(1))
    val shape = remember(front, framedFront, aspect, spec) {
        val f = front
        // (Update 191: the real case's own shape when it's known; its art goes in it as on the real one.)
        if (spec != null) spec.aspect.coerceIn(0.55f, 1.85f)
        else if (f == null || framedFront) aspect
        else (f.width.toFloat() / f.height.coerceAtLeast(1)).coerceIn(aspect * 0.8f, aspect * 1.25f)
    }
    val face = remember(front, noCover, shape, band, spec) {
        (front ?: noCover)?.let { f ->
            // (Update 171: not over a box scan used as the cover — it has its own band and logo, they showed twice.)
            val whole = tplFront != null && isBoxFront(tplFront, f.width.toFloat() / f.height.coerceAtLeast(1))
            val made = if (band != null && tplFront != null && !whole) framed(f, band, fit?.win ?: tplFront.win) else null
            when {
                // Update 191: a jewel case shows its booklet under the clear lid, beside the tray's hinge ridge, with the
                // lid's clear edge round it. (Update 202: the cover fills the booklet, cut only as much as its shape is
                // off — jewelFront; it was letterboxed over the tray's colour.)
                // (Update 193 (Thor): a front scan or booklet — about square — goes in whole; the template's frame cut a
                // PlayStation cover to its own narrower shape first. Only other art gets the template's band.)
                // (Update 195 (Thor, still cut in 194): always the whole picture — the template's frame is never used
                // on a jewel case, whatever the picture's shape.)
                // (Update 197 (Thor): a scan of the whole jewel case's front — its own hinge ribs at the left, about the
                // case's shape — is the front itself: it fills the face, no drawn booklet window or ridge over it.)
                spec?.shell == CaseShell.JEWEL -> if (isWholeCaseScan(f, shape)) f else DerivedArt.of(f, "jewel|$shape|${spec.tray}") { jewelFront(f, shape, spec.tray) }
                // Update 191: a keep case's sleeve, a box's print: the cover over the whole front, at the real case's
                // shape. (Update 202: cut in the middle only as much as it's off — fillCrop, never stretched.)
                spec != null -> made ?: DerivedArt.of(f, "fill|$shape") { fitFace(f, shape) }
                // (Update 202: the case already takes the cover's own shape within a quarter — [shape] above — so this
                // cuts only past that; Update 200 stretched it up to 13 % here.)
                else -> made ?: DerivedArt.of(f, "fill|$shape") { fillCrop(f, shape) }
            }
        }
    }
    // Update 205: a case with no back handed in (the carousels, Case stacks, the shelf's 3D cases) gets its own — its
    // back scan, else the back cut from its full box scan, else a printed one — made the first time it turns far enough
    // to show it (see the drawing below). It showed plain plastic from behind everywhere but the game window.
    // (Update 208: asked for at once — it's decoded while the case stands, so it's there the moment it turns (a back made
    // only once the case had turned 70° came late, or after a second turn). Small handhelds still wait for the turn.)
    val backAtOnce = !com.shelf.launcher.device.MemoryBudget.small(androidx.compose.ui.platform.LocalContext.current)
    var backAsked by remember(g.stableKey) { androidx.compose.runtime.mutableStateOf(false) }
    // (Update 208: a back handed in that turned out a flat-colour stand-in is replaced by the next one in line.)
    val given = back?.takeUnless { FlatArt.isKnown(it) }
    val autoBack: Any? = if (given != null) null else remember(scans, g.platform, cover, aspect) { defaultCaseBack(g, scans, cover, aspect) }
    // (A scanned back at once; a printed one — drawn from its parts — only once it's turned.)
    val backModel: Any? = given ?: autoBack?.takeIf { back != null || backAsked || (backAtOnce && it !is PrintedBack) }
    // (Update 170: a printed back is made from its parts; a scanned one is a picture.)
    val printedBmp = rememberPrintedBack(backModel as? PrintedBack)
    val scannedBmp = rememberSoftBitmap(backModel.takeIf { it !is PrintedBack }, 480)
    val backBmp = printedBmp ?: scannedBmp
    // (Update 188: a back scan a little off the case's shape — a jewel case's back with its side flaps — is fitted, not
    // cut: its edges, text and logos stay.)
    // (Update 205: a jewel case's back insert scanned with its two end flaps — the strips that fold into the case's
    // sides — has them cut off instead of squeezing them onto the back.)
    val jewelBack = spec?.shell == CaseShell.JEWEL || caseStyleOf(g.platform).let { it == CASE_JEWEL || it == CASE_JEWEL_BLACK }
    val backFace = remember(backBmp, shape, jewelBack) { backBmp?.let { b -> DerivedArt.of(b, "back|$shape|$jewelBack") { fitBack(b, shape, jewelBack) } } }
    // Update 166: while its cover is still loading, the front in the game's own colour (not near-black: scrolling a
    // carousel of 3D cases showed dark fronts for a moment).
    val loading = remember(g.hue) { DerivedArt.named("hue|${g.hue}") { hueFront(g.hue) } }
    val frameArgb = frameColor.toArgb()
    // Update 186: what the case is made of, so it looks like the real one (a clear jewel case, a cardboard box, a keep case).
    val style = caseStyleOf(g.platform)
    // Update 191: its plastic's colour and how see-through, its edges, grip and hinge ridge, from the real case.
    // (Update 197: a whole-case scan already shows the tray's ridge — no drawn one over it.)
    val wholeScan = spec?.shell == CaseShell.JEWEL && front != null && isWholeCaseScan(front, shape)
    val look = remember(spec, style, face, wholeScan) { CaseLook.of(spec, style, face).let { if (wholeScan) it.withoutStrip() else it } }
    // Update 189: what an opened case shows inside (made only for a case that can open).
    // (Update 191: from the real case, and from what lies in it — a disc case holding a cartridge shows the cartridge.)
    val openKind = CaseInside.kindFor(spec, g.platform, inside?.round)
    val canOpen = openOf != null && openKind != CaseInside.Kind.BOX
    // Update 191: a cardboard box opens its top flap instead (the tray rises, the cartridge comes out).
    val flapOpen = openOf != null && openKind == CaseInside.Kind.BOX
    val itemReal = rememberSoftBitmap(if (canOpen) inside?.real else null, 480)
    val itemLabel = rememberSoftBitmap(if (canOpen && inside?.real == null) inside?.label else null, 480)
    val itemAspect = inside?.aspect ?: 1f
    val trayBmp = remember(canOpen, g.platform, shape, itemAspect, spec, openKind) { if (canOpen) runCatching { CaseInside.tray(g.platform, shape, itemAspect, spec, openKind) }.getOrNull() else null }
    // Update 202: a game with a manual has it lying in the lid as a booklet (tap it to read; MediaInspectView), wearing
    // its first page once it's been downloaded.
    val hasBooklet = if (canOpen) rememberHasManual(g) else false
    val bookletPage = if (canOpen) rememberManualCover(g, hasBooklet) else null
    val lidBmp = remember(canOpen, g.platform, shape, front, spec, openKind, itemAspect, hasBooklet, bookletPage) {
        if (canOpen) runCatching { CaseInside.lid(g.platform, shape, front, spec, openKind, itemAspect, hasBooklet, bookletPage) }.getOrNull() else null
    }
    val itemBmp = remember(canOpen, itemReal, itemLabel, itemAspect, openKind) {
        if (canOpen && inside != null) runCatching { CaseInside.item(openKind, itemReal, itemLabel, itemAspect, inside.tone) }.getOrNull() else null
    }
    val itemRect = remember(openKind, shape, itemAspect, spec) { CaseInside.itemRect(openKind, shape, itemAspect, spec) }
    val plastic = remember(g.platform, spec) { CaseInside.plastic(spec, g.platform) }
    val itemOnLid = spec?.mediaLeft == true
    Canvas(modifier) {
        val yawNow = yawOf?.invoke() ?: yaw
        // (Update 205: turned far enough that its back will show: its back is made now, once.)
        if (given == null && !backAsked) {
            val a = ((yawNow % 360f) + 360f) % 360f
            if (a > 70f && a < 290f) backAsked = true
        }
        val shadeNow = shadeOf?.invoke() ?: shade
        val fit = minOf(size.height, size.width / shape) * fill
        val h = fit
        val w = h * shape
        val d = h * depth
        val openNow = if (canOpen) (openOf?.invoke() ?: 0f) else 0f
        if (openNow > 0.002f && trayBmp != null && lidBmp != null) drawIntoCanvas { canvas ->
            // Update 189: open — the whole book (lid to the left of the hinge, tray to the right) fitted in the same room.
            val k = 1f / (1f + 0.92f * openNow)
            val w2 = w * k; val h2 = h * k; val d2 = d * k
            CaseOpenDraw.draw(
                canvas.nativeCanvas, size.width / 2f + w2 * 0.5f * openNow, size.height / 2f, w2, h2, d2, yawNow, pitch + 8f * openNow,
                openNow, face ?: noCover ?: loading, spine, backFace, trayBmp, lidBmp, itemBmp, itemRect,
                showItemOf?.invoke() ?: true, plastic, (1f - shadeNow).coerceIn(0f, 1f), itemOnLid,
                // (Update 193: the lid's own thickness and its rim's colour, so the halves part at the seam.)
                look.seam, look.edge
            )
        } else drawIntoCanvas { canvas ->
            CaseDraw.draw(
                canvas.nativeCanvas, size.width / 2f, size.height / 2f, w, h, d, yawNow, pitch,
                face ?: noCover ?: loading, spine, (1f - shadeNow).coerceIn(0f, 1f), reflect,
                if (frame) frameArgb else 0, 2.5f * density, backFace, look,
                if (flapOpen) (openOf?.invoke() ?: 0f) else 0f
            )
        }
    }
}

/** Update 189: the disc / card in an opening case: its own picture ([real]) or the art for a drawn one ([label]). */
internal class CaseItem(val real: Any?, val label: Any?, val aspect: Float, val tone: Int, val round: Boolean = true)

/**
 * Update 191: how a case's outside is drawn — the colour of its edges and top (a see-through PS4 case blue, a clear
 * jewel case grey over its tray, a cardboard box its own print), its plain back, the thumb grip on its opening edge,
 * the tray's hinge ridge seen through a jewel case's lid, matte cardboard, and a box's inside (for its opening flap).
 */
internal class CaseLook(
    val edge: Int, val top: Int, val back: Int, val grip: Boolean, val strip: Int, val stripW: Float, val matte: Boolean,
    val inner: Int, val flap: Boolean,
    /** Update 193: where the front lid meets the rear shell, across the thickness from the front (0: one piece, a box). */
    val seam: Float = 0f,
    val edgeStyle: Int = EDGE_NONE,
    /** Update 193: the catches / tabs along the opening edge (down it, 0…1). */
    val catches: FloatArray = FloatArray(0)
) {
    /** Update 197: the same without the drawn tray ridge (the scan shows its own). */
    fun withoutStrip(): CaseLook = CaseLook(edge, top, back, grip, 0, 0f, matte, inner, flap, seam, edgeStyle, catches)

    companion object {
        private const val DARK = 0xFF15171B.toInt()
        private val KEEP_CATCHES = floatArrayOf(0.2f, 0.8f)
        private val JEWEL_TABS = floatArrayOf(0.12f, 0.88f)
        private val CLAM_CATCH = floatArrayOf(0.5f)

        fun of(spec: CaseSpec?, style: Int, face: android.graphics.Bitmap?): CaseLook {
            if (spec == null) return when (style) {
                CASE_JEWEL -> CaseLook(0xFF9AA0AB.toInt(), 0xFFB9BEC8.toInt(), 0xFF22242C.toInt(), false, 0, 0f, false, 0, false, 0.23f, EDGE_JEWEL, JEWEL_TABS)
                CASE_JEWEL_BLACK -> CaseLook(0xFF26282D.toInt(), 0xFF3C3E44.toInt(), 0xFF22242C.toInt(), false, 0xF20C0C0E.toInt(), 0.062f, false, 0, false, 0.23f, EDGE_JEWEL, JEWEL_TABS)
                CASE_CARD -> CaseLook(0xFF3A3832.toInt(), 0xFF4A4740.toInt(), 0xFF22242C.toInt(), false, 0, 0f, true, 0xFFEFECE4.toInt(), true)
                else -> CaseLook(0xFF22242C.toInt(), 0xFF3A3D47.toInt(), 0xFF22242C.toInt(), true, 0, 0f, false, 0, false, 0.14f, EDGE_KEEP, KEEP_CATCHES)
            }
            fun lighter(c: Int, k: Float) = CaseInside.mix(c, 0xFFFFFFFF.toInt(), k)
            return when (spec.shell) {
                CaseShell.CARDBOARD, CaseShell.BIGBOX, CaseShell.BOOKBOX -> {
                    // Printed cardboard: the top and side carry on the front's print (its colours there, a little darker).
                    val top = face?.let { averageOf(it, 0f, 0f, 1f, 0.08f) }?.let { CaseInside.mix(it, DARK, 0.15f) } ?: 0xFF4A4740.toInt()
                    val side = face?.let { averageOf(it, 0.92f, 0f, 1f, 1f) }?.let { CaseInside.mix(it, DARK, 0.2f) } ?: 0xFF3A3832.toInt()
                    // (A book box's front is a cardboard cover over the box: a seam where it closes.)
                    if (spec.shell == CaseShell.BOOKBOX) CaseLook(side, top, side, false, 0, 0f, true, spec.tray, false, (2.5f / spec.tMm).coerceIn(0.05f, 0.3f), EDGE_KEEP)
                    else CaseLook(side, top, side, false, 0, 0f, true, spec.tray, true)
                }
                CaseShell.JEWEL -> {
                    // Clear plastic over the tray: the edges are the tray's colour seen through it.
                    val lightTray = luma(spec.tray) > 150
                    val edge = if (lightTray) CaseInside.mix(spec.tray, 0xFF8E949E.toInt(), 0.35f) else CaseInside.mix(spec.tray, 0xFF5A5F68.toInt(), 0.18f)
                    val strip = if (lightTray) (spec.tray and 0x00FFFFFF) or 0x88000000.toInt() else (spec.tray and 0x00FFFFFF) or 0xF2000000.toInt()
                    // (Update 193: the clear lid's edge in front — 2.4 mm, so a deep PAL case shows far more tray.)
                    CaseLook(edge, lighter(edge, 0.15f), edge, false, strip, 0.062f, false, spec.tray, false,
                        (2.4f / spec.tMm).coerceIn(0.08f, 0.32f), EDGE_JEWEL, JEWEL_TABS)
                }
                CaseShell.LONGBOX, CaseShell.CASSETTE -> {
                    val edge = CaseInside.mix(spec.tray, spec.plastic, 0.3f)
                    if (spec.shell == CaseShell.CASSETTE) CaseLook(edge, lighter(edge, 0.12f), edge, false, 0, 0f, false, spec.tray, false, 0.5f, EDGE_CLAM)
                    else CaseLook(edge, lighter(edge, 0.12f), edge, false, 0, 0f, false, spec.tray, false, (3f / spec.tMm).coerceIn(0.08f, 0.3f), EDGE_JEWEL, JEWEL_TABS)
                }
                else -> {
                    // A keep case / clamshell: its plastic, deeper the more see-through it is (you look through its depth).
                    val edge = CaseInside.mix(DARK, spec.plastic, 0.5f + 0.4f * spec.opacity)
                    // (Update 193: the front cover is about 2 mm of a keep case's depth — Switch's thin case shows more of
                    // it; a DS / 3DS / Vita case's halves are about even; a cartridge clamshell's halves even, one catch.)
                    val (seam, style, catches) = when {
                        spec.shell == CaseShell.CLAMSHELL -> Triple(0.5f, EDGE_CLAM, CLAM_CATCH)
                        spec.shell == CaseShell.CARDCASE && spec.platform != "Switch" && spec.platform != "Switch2" ->
                            Triple(if (spec.tMm >= 18f) 0.5f else 0.46f, EDGE_CARD, KEEP_CATCHES)
                        else -> Triple((2f / spec.tMm).coerceIn(0.1f, 0.3f), EDGE_KEEP, KEEP_CATCHES)
                    }
                    CaseLook(edge, lighter(edge, 0.12f), edge, spec.grip, 0, 0f, false, spec.tray, false, seam, style, catches)
                }
            }
        }

        private fun luma(c: Int): Int =
            (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000

        /**
         * The average colour of a part of [b] (fractions l, t, r, b). (Update 208: sampled on a grid, without copying the
         * part and scaling it down — two new pictures per case, made on the screen's thread each time a case came into a
         * row — and kept per picture: DerivedArt.)
         */
        private fun averageOf(b: android.graphics.Bitmap, l: Float, t: Float, r: Float, bt: Float): Int? =
            DerivedArt.of(b, "avg|$l|$t|$r|$bt") { averageNow(b, l, t, r, bt) ?: NO_AVERAGE }.takeIf { it != NO_AVERAGE }

        private const val NO_AVERAGE = 0x00FFFFFF

        private fun averageNow(b: android.graphics.Bitmap, l: Float, t: Float, r: Float, bt: Float): Int? = runCatching {
            val x0 = (l * b.width).toInt().coerceIn(0, b.width - 1); val y0 = (t * b.height).toInt().coerceIn(0, b.height - 1)
            val x1 = (r * b.width).toInt().coerceIn(x0 + 1, b.width); val y1 = (bt * b.height).toInt().coerceIn(y0 + 1, b.height)
            val nx = minOf(12, x1 - x0); val ny = minOf(24, y1 - y0)
            var rs = 0L; var gs = 0L; var bs = 0L
            for (j in 0 until ny) for (i in 0 until nx) {
                val c = b.getPixel(x0 + ((i + 0.5f) * (x1 - x0) / nx).toInt().coerceAtMost(x1 - 1), y0 + ((j + 0.5f) * (y1 - y0) / ny).toInt().coerceAtMost(y1 - 1))
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
            }
            val n = (nx * ny).coerceAtLeast(1)
            0xFF000000.toInt() or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
        }.getOrNull()
    }
}

/** Update 186: a keep case (DVD-style), a clear jewel case (CDs) or a cardboard box (cartridges). */
internal const val CASE_KEEP = 0
internal const val CASE_JEWEL = 1
internal const val CASE_CARD = 2
/** Update 188: a PlayStation jewel case — clear lid over a black tray: the black hinge ridge down the left, smoked edges. */
internal const val CASE_JEWEL_BLACK = 3

internal fun caseStyleOf(platform0: String?): Int = when (LookStyles.casePlatform(platform0)) {
    "PS1" -> CASE_JEWEL_BLACK
    "Dreamcast", "Saturn", "SegaCD", "NeoGeoCD", "PCEngineCD", "3DO", "CDi" -> CASE_JEWEL
    "NES", "SNES", "N64", "GB", "GBC", "GBA", "VirtualBoy", "Atari2600", "Atari7800", "Jaguar", "ColecoVision", "Lynx" -> CASE_CARD
    else -> CASE_KEEP
}

/** Update 193: how a case's opening edge is built (CaseLook.edgeStyle). */
internal const val EDGE_NONE = 0
internal const val EDGE_KEEP = 1
internal const val EDGE_CARD = 2
internal const val EDGE_JEWEL = 3
internal const val EDGE_CLAM = 4

/** Update 171: Steam / Android 3D cases' thickness against their height. */
internal const val OWN_CASE_DEPTH = 0.05f

/**
 * Width / height of a game's 3D case: its console's real case shape (update 166: also with the cover templates off — a
 * Game Boy Advance game opened as a DVD-shaped box), else a game case's.
 */
internal fun case3dAspect(g: Game, mixed: Boolean): Float {
    // (Update 169: a Steam / Android case at a SteamGridDB cover's shape, its band on top — Case3D.)
    if (Features.ownCaseFit.state.value && !LookStyles.spineForAll()) MediaCovers.own(g.platform)?.let { o -> return (o.fitTo(SGDB_COVER_ASPECT)?.aspect ?: o.frontAspect).coerceIn(0.55f, 1.45f) }
    // (Update 191: the real case's shape in the game's region — CaseCatalog.kt.)
    CaseCatalog.forGame(g)?.let { return it.aspect.coerceIn(0.55f, 1.85f) }
    // (Update 166: in lists mixing consoles too — each case keeps its own shape there, as the flat box art does.)
    realCaseMm(g.platform)?.let { (w, h) -> if (w > 0f && h > 0f) return (w / h).coerceIn(0.55f, 1.45f) }
    // (Update 205: the same shape in a list mixing consoles — a game's case never changes with the list it's in.)
    return (if (Features.coverTemplates.state.value) MediaCovers.of(g.platform)?.frontAspect else null)?.coerceIn(0.55f, 1.45f) ?: 0.71f
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
    // (Update 200 stretched a cover up to an eighth off the window's shape to fit it whole.) Update 202: never stretched —
    // cut in the middle to the window's shape, only as much as it's off (object-fit: cover), so it fills the window
    // edge to edge at its own proportions.
    val cut = fillCrop(art, dst.width() / dst.height().coerceAtLeast(1f))
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    c.drawBitmap(cut, null, dst, p)
    c.drawBitmap(frame, null, android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), p)
    out
}.getOrDefault(art)

/**
 * Update 208: what a 3D case makes from its pictures — the cover cut to the case's shape, a jewel case's booklet, the back
 * fitted, the spine turned upright, the colours along a box's edges, the plain fronts and spines — kept, keyed by the
 * picture they came from (held weakly: once the picture itself is let go, its entry is never used again). Each was made
 * again on the screen's thread every time a case came into a row: scrolling a long row of cases, or switching to a
 * category of them, spent most of its frames copying pictures.
 */
internal object DerivedArt {
    private class Entry(val src: java.lang.ref.WeakReference<Any>?, val value: Any, val bytes: Int)

    private val cache = object : android.util.LruCache<String, Entry>(
        (Runtime.getRuntime().maxMemory() / 12).coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Entry): Int = value.bytes
    }

    @Volatile var hits = 0
        private set
    @Volatile var misses = 0
        private set

    private fun bytesOf(v: Any, src: Any?): Int =
        if (v is android.graphics.Bitmap && v !== src) runCatching { v.allocationByteCount }.getOrDefault(64).coerceAtLeast(64) else 64

    /** [make] for [src] (a picture), made once while [src] lives. */
    fun <T : Any> of(src: android.graphics.Bitmap, kind: String, make: () -> T): T {
        val key = "${System.identityHashCode(src)}|${src.width}x${src.height}|$kind"
        val hit = cache.get(key)
        @Suppress("UNCHECKED_CAST")
        val known = if (hit != null && hit.src?.get() === src && !(hit.value is android.graphics.Bitmap && hit.value.isRecycled)) hit.value as? T else null
        if (known != null) { hits++; return known }
        misses++
        val v = make()
        // (Never one holding its own picture: the same picture back needs no keeping.)
        if (v !== src) cache.put(key, Entry(java.lang.ref.WeakReference(src), v, bytesOf(v, src)))
        return v
    }

    /** [make] for a [key] that says everything it's made from (a hue, a name). */
    fun <T : Any> named(key: String, make: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        val known = cache.get("n|$key")?.value?.takeUnless { it is android.graphics.Bitmap && it.isRecycled } as? T
        if (known != null) { hits++; return known }
        misses++
        val v = make()
        cache.put("n|$key", Entry(null, v, bytesOf(v, null)))
        return v
    }
}

/**
 * Update 188: a case's back picture at [aspect]: within about a fifth of it, gently stretched (nothing cut); further off,
 * cut in the middle like the front.
 */
private fun fitBack(src: android.graphics.Bitmap, aspect: Float, jewel: Boolean = false): android.graphics.Bitmap {
    val cur = src.width.toFloat() / src.height.coerceAtLeast(1)
    if (aspect <= 0f || kotlin.math.abs(cur - aspect) < 0.02f) return src
    val k = cur / aspect
    // (Update 205: a jewel case's insert wider than its back — its end flaps — cut to the back's shape in the middle.)
    if (jewel && k > 1.05f && k < 1.3f) return cropTo(src, aspect)
    if (k < 0.82f || k > 1.22f) return cropTo(src, aspect)
    val w = (src.height * aspect).toInt().coerceAtLeast(1)
    return runCatching { android.graphics.Bitmap.createScaledBitmap(src, w, src.height, true) }.getOrDefault(src)
}

/**
 * Update 197: a jewel case front scanned whole (the case's own shape, 142 × 125, give or take 8 %) rather than just its
 * booklet (square): ScreenScraper's PlayStation fronts are often the whole case, hinge ribs and all.
 */
internal fun isWholeCaseScan(f: android.graphics.Bitmap, caseAspect: Float): Boolean {
    val a = f.width.toFloat() / f.height.coerceAtLeast(1)
    return caseAspect > 0f && kotlin.math.abs(a / caseAspect - 1f) < 0.08f
}

/**
 * Update 191: [src] at [aspect] for a real case's front ([spec]'s shape). Update 202: always [fillCrop] — Update 191 drew
 * art within 6 % as it was (stretched onto the front by that much) and Update 200 stretched it up to an eighth.
 */
private fun fitFace(src: android.graphics.Bitmap, aspect: Float): android.graphics.Bitmap = fillCrop(src, aspect)

/**
 * Update 202: [src] cut in the middle to [aspect] (width / height), only as much as it's off — the bitmap form of
 * ContentScale.Crop / object-fit: cover. Replaces Update 200's stretchOrCrop, which stretched covers up to 13 % instead
 * of cutting them. The face is then drawn onto a front of exactly [aspect], so nothing is stretched. Within half a
 * percent (a pixel or so) it is left as it is.
 */
private fun fillCrop(src: android.graphics.Bitmap, aspect: Float): android.graphics.Bitmap {
    val cur = src.width.toFloat() / src.height.coerceAtLeast(1)
    if (aspect <= 0f || kotlin.math.abs(cur / aspect - 1f) < 0.005f) return src
    val w = if (cur > aspect) (src.height * aspect).toInt().coerceIn(1, src.width) else src.width
    val h = if (cur > aspect) src.height else (src.width / aspect).toInt().coerceIn(1, src.height)
    return runCatching { android.graphics.Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h) }.getOrDefault(src)
}

/**
 * Update 191: a jewel case's front at [aspect] (142 × 125 mm): the booklet (120 mm square) under the clear lid, and round
 * it the lid's clear edge over the tray ([tray]'s colour, seen through the plastic). The booklet's place is approximate
 * (left of it the tray's hinge ridge, drawn by CaseDraw). Update 202: the cover always fills the booklet's window edge to
 * edge, cut in the middle only as much as its shape is off ([fillCrop]); a near-square cover used to go in whole,
 * letterboxed over the tray's colour, so strips of it showed beside the art. (A whole-case scan never comes here:
 * isWholeCaseScan, used as it is.)
 */
private fun jewelFront(art: android.graphics.Bitmap, aspect: Float, tray: Int): android.graphics.Bitmap = runCatching {
    val w = 640; val h = (w / aspect).toInt().coerceAtLeast(1)
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    // The lid's clear edge round the booklet (the case itself, not a backdrop for the art: the booklet covers its window).
    c.drawColor(CaseInside.mix(tray, 0xFF2A2D33.toInt(), 0.45f))
    val win = android.graphics.RectF(w * 0.075f, h * 0.018f, w * 0.925f, h * 0.982f)
    val booklet = fillCrop(art, win.width() / win.height())
    c.drawBitmap(booklet, null, win, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
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
        back: android.graphics.Bitmap? = null,
        /** Update 191: how its outside looks (the real case's plastic, grip, ridge…). */
        look: CaseLook,
        /** Update 191: a cardboard box's top flap, open 0…1 (its tray rises out of it). */
        flap: Float = 0f
    ) {
        val ty = Math.toRadians(yaw.toDouble()); val tp = Math.toRadians(pitch.toDouble())
        val cy0 = kotlin.math.cos(ty).toFloat(); val sy0 = kotlin.math.sin(ty).toFloat()
        val cp = kotlin.math.cos(tp).toFloat(); val sp = kotlin.math.sin(tp).toFloat()
        val focal = h * 3.4f
        val hw = w / 2f; val hh = h / 2f; val hd = d / 2f
        // Projects corner [i] of [face] (mirrored under the floor when [mirror]) into [out] at [i]; false when behind the eye.
        // Projects the point (x, y, z) (px from the middle) into [out] at [i]; false when behind the eye.
        fun point(i: Int, x: Float, y: Float, z: Float): Boolean {
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
        // Update 186: a point on the face just projected (corners TL, TR, BR, BL in [out]) at [u] across, [v] down.
        fun px(u: Float, v: Float): Float {
            val top = out[0] + (out[2] - out[0]) * u; val bot = out[6] + (out[4] - out[6]) * u
            return top + (bot - top) * v
        }
        fun py(u: Float, v: Float): Float {
            val top = out[1] + (out[3] - out[1]) * u; val bot = out[7] + (out[5] - out[7]) * u
            return top + (bot - top) * v
        }
        fun lineOn(u0: Float, v0: Float, u1: Float, v1: Float, argb: Int, width: Float) {
            line.strokeWidth = width; line.color = argb
            c.drawLine(px(u0, v0), py(u0, v0), px(u1, v1), py(u1, v1), line)
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
        // A part of the face just projected (u across, v down), filled.
        fun bandOn(u0: Float, v0: Float, u1: Float, v1: Float, argb: Int) {
            path.reset()
            path.moveTo(px(u0, v0), py(u0, v0)); path.lineTo(px(u1, v0), py(u1, v0))
            path.lineTo(px(u1, v1), py(u1, v1)); path.lineTo(px(u0, v1), py(u0, v1)); path.close()
            fill.shader = null; fill.color = argb; c.drawPath(path, fill)
        }
        // Update 193: the opening edge (EDGE face: u 0 = front, 1 = back; v 0 = top).
        fun openingEdge(look: CaseLook, l: Float) {
            val s = look.seam
            val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
            val hair = (0.0045f).coerceAtLeast(0.6f / (h.coerceAtLeast(1f)))
            val gap = hair * (h / d.coerceAtLeast(1f)) * 0.5f
            // The front lid's own edge: a jewel lid clear and lighter, a keep case's cover a touch lighter than the shell.
            val lid = if (look.edgeStyle == EDGE_JEWEL) CaseInside.mix(look.edge, white, 0.38f) else CaseInside.mix(look.edge, white, 0.07f)
            bandOn(0f, 0f, s, 1f, scaled(lid, l))
            // The seam: a fine shadow where the halves meet, the rear shell's lip lit just behind it.
            bandOn(s - gap, 0f, s + gap, 1f, scaled(CaseInside.mix(look.edge, black, 0.65f), l))
            bandOn(s + gap, 0f, (s + gap * 3f).coerceAtMost(1f), 1f, scaled(CaseInside.mix(look.edge, white, 0.16f), l))
            // The catches / tabs: small moulded bumps over the seam.
            val cw = (0.06f).coerceAtMost(s * 0.9f).coerceAtLeast(0.03f)
            for (v in look.catches) {
                val half = if (look.edgeStyle == EDGE_CLAM) 0.04f else 0.022f
                bandOn((s - cw).coerceAtLeast(0f), v - half, (s + cw * 0.6f).coerceAtMost(1f), v + half,
                    scaled(CaseInside.mix(lid, white, 0.14f), l))
            }
        }
        // Update 193: the top where the halves meet (TOP face: u 0 = spine, 1 = opening side; v 0 = back, 1 = front),
        // or a box's top: the fold at the back and the tuck flap's slit at the front.
        fun topEdge(look: CaseLook, l: Float) {
            val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
            val hair = 0.012f
            if (look.seam > 0f) {
                val v = 1f - look.seam
                val lid = if (look.edgeStyle == EDGE_JEWEL) CaseInside.mix(look.top, white, 0.3f) else CaseInside.mix(look.top, white, 0.06f)
                bandOn(0f, v, 1f, 1f, scaled(lid, l))
                bandOn(0f, v - hair, 1f, v + hair, scaled(CaseInside.mix(look.top, black, 0.6f), l))
            } else if (look.flap) {
                bandOn(0.01f, 0.02f, 0.99f, 0.05f, scaled(CaseInside.mix(look.top, white, 0.12f), l))
                bandOn(0.04f, 0.955f, 0.96f, 0.985f, scaled(CaseInside.mix(look.top, black, 0.55f), l))
            }
        }
        // The plastic: the open edge, the top and the back (update 208: one function — a printed box's side above).
        fun plainSide(f: Int, l: Float, mirror: Boolean) {
            // (Update 186: a jewel case's edges are clear, see-through grey plastic; a box's edges cardboard.)
            fill.shader = null
            // (Update 191: a box with its flap open shows its dark inside on top.)
            val opening = f == 3 && !mirror && flap > 0.001f && look.flap
            val base = if (opening) 0xFF141210.toInt() else if (f == 3) look.top else look.edge
            fill.color = scaled(base, l)
            c.drawPath(pathOf(), fill)
            // Update 189: a keep case's opening edge has its thumb grip — a recess in the middle third,
            // where you open it (jewel cases and boxes don't have one; update 191: where the real case has).
            // Update 193: the opening side is two pieces fitted together (CaseLook.seam): the front lid's own
            // edge, a fine seam, the rear shell's lip, the small catches; a jewel case's clear lid over its
            // tray with the lid's tabs; a box's top its fold and tuck slit. The thumb grip sits in the rear
            // shell, behind the seam.
            if (!mirror && f == 2 && look.seam > 0f) openingEdge(look, l)
            if (!mirror && f == 3 && !opening) topEdge(look, l)
            if (look.grip && f == 2) {
                val g0 = if (look.seam > 0f) (look.seam + 0.08f).coerceAtMost(0.5f) else 0.12f
                path.reset()
                path.moveTo(px(g0, 0.36f), py(g0, 0.36f)); path.lineTo(px(0.88f, 0.36f), py(0.88f, 0.36f))
                path.lineTo(px(0.88f, 0.64f), py(0.88f, 0.64f)); path.lineTo(px(g0, 0.64f), py(g0, 0.64f)); path.close()
                fill.color = scaled(CaseInside.mix(look.edge, 0xFF000000.toInt(), 0.5f), l * 0.8f); c.drawPath(path, fill)
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
                            // (Update 186: a cardboard box is matte — no plastic gloss.)
                            if (!look.matte) {
                                fill.shader = gloss
                                c.drawPath(pathOf(), fill)
                                fill.shader = null
                            }
                            val px1 = (w / 260f).coerceIn(0.8f, 3f)
                            // (Update 189: no drawn lines on the cases any more — the hinge, lid, sleeve and fold lines and
                            // the thin white outline read as stray lines on the Thor. A jewel case keeps its tray's hinge
                            // ridge seen through the lid, a solid strip as on the real one: black on PlayStation, clear
                            // or white on Dreamcast — update 191.)
                            if (look.strip != 0) {
                                val sw = look.stripW
                                path.reset()
                                path.moveTo(px(0f, 0f), py(0f, 0f)); path.lineTo(px(sw, 0f), py(sw, 0f))
                                path.lineTo(px(sw, 1f), py(sw, 1f)); path.lineTo(px(0f, 1f), py(0f, 1f)); path.close()
                                fill.shader = null; fill.color = scaled(look.strip, light); c.drawPath(path, fill)
                            }
                            if (frameArgb != 0) { line.strokeWidth = frameWidth; line.color = frameArgb; c.drawPath(pathOf(), line) }
                        }
                    }
                    1 -> warp(spine, filterOf(l))
                    // (Update 162: the back's own picture when there is one.)
                    4 -> if (back != null) warp(back, filterOf(l)) else {
                        fill.shader = null
                        fill.color = scaled(look.back, l)
                        c.drawPath(pathOf(), fill)
                    }
                    // Update 208: a printed cardboard box's other side carries its print too (the same panel as the spine,
                    // as on the real Game Boy / NES / N64 boxes), with the folds along its edges — it was one solid colour
                    // taken from the front's edge, a plain block of plastic down the whole side.
                    2 -> if (look.flap && look.matte) {
                        warp(spine, filterOf(l))
                        if (!mirror) {
                            lineOn(0f, 0f, 0f, 1f, scaled(0x66000000, 1f), (w / 400f).coerceIn(0.6f, 2f))
                            lineOn(1f, 0f, 1f, 1f, scaled(0x55000000, 1f), (w / 400f).coerceIn(0.6f, 2f))
                        }
                    } else plainSide(f, l, mirror)
                    else -> plainSide(f, l, mirror)
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
        // Update 191: a cardboard box opening: the top's tuck flap swings up and back on its fold at the back, the two
        // dust flaps fold out to the sides, and the inner tray rises out of the opening (the cartridge then comes out of
        // it). Which end opens isn't confirmed for every console; the top is drawn.
        if (flap > 0.001f && look.flap) {
            val k = flap.coerceIn(0f, 1f)
            fill.shader = null
            // Fills the quad just projected: [outer] when its outside faces you, else [inner]; skipped behind the eye.
            fun quad(p: FloatArray, outer: Int, inner: Int, lit: Float, cull: Boolean = false) {
                for (i in 0 until 4) if (!point(i, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])) return
                val a = area()
                if (cull && a <= 0f) return
                fill.color = scaled(if (a > 0f) outer else inner, lit * light)
                c.drawPath(pathOf(), fill)
            }
            val cardInside = 0xFFD6CFC0.toInt()
            // The dust flaps, on the sides' top edges: from lying inwards to standing a little outwards.
            val b = Math.toRadians((k * 100f).toDouble())
            val bx = kotlin.math.cos(b).toFloat(); val by = kotlin.math.sin(b).toFloat()
            val dl = minOf(d * 0.45f, hw * 0.5f)
            for (sx in floatArrayOf(-1f, 1f)) {
                val x0 = sx * hw * 0.99f
                val tx = x0 - sx * dl * bx; val ty = -hh - dl * by
                quad(floatArrayOf(x0, -hh, -hd * 0.9f, x0, -hh, hd * 0.9f, tx, ty, hd * 0.85f, tx, ty, -hd * 0.85f), cardInside, cardInside, 0.82f)
            }
            // The tuck flap, on the top's back edge, and its tongue (the part that tucks into the front).
            val a = Math.toRadians((k * 118f).toDouble())
            val ca = kotlin.math.cos(a).toFloat(); val sa = kotlin.math.sin(a).toFloat()
            val fy = -hh - d * sa; val fz = -hd + d * ca
            quad(floatArrayOf(-hw, -hh, -hd, hw, -hh, -hd, hw, fy, fz, -hw, fy, fz), look.top, cardInside, 0.95f)
            val tl = d * 0.4f
            val ey = fy + tl * ca; val ez = fz + tl * sa
            quad(floatArrayOf(-hw * 0.94f, fy, fz, hw * 0.94f, fy, fz, hw * 0.94f, ey, ez, -hw * 0.94f, ey, ez), look.top, cardInside, 0.88f)
            // The inner tray, rising once the flap is out of the way.
            val rise = h * 0.2f * ((k - 0.7f) / 0.3f).coerceIn(0f, 1f)
            if (rise > 0.5f) {
                val tx = hw * 0.9f; val tz = hd * 0.78f
                val top = -hh - rise
                val sides = arrayOf(
                    floatArrayOf(-tx, top, tz, tx, top, tz, tx, -hh, tz, -tx, -hh, tz) to NORMALS[0],
                    floatArrayOf(-tx, top, -tz, -tx, top, tz, -tx, -hh, tz, -tx, -hh, -tz) to NORMALS[1],
                    floatArrayOf(tx, top, tz, tx, top, -tz, tx, -hh, -tz, tx, -hh, tz) to NORMALS[2]
                )
                for ((q, n) in sides) quad(q, look.inner, look.inner, lightOf(n) / light.coerceAtLeast(0.01f), cull = true)
                // Its top, with the empty well the cartridge sat in.
                quad(floatArrayOf(-tx, top, -tz, tx, top, -tz, tx, top, tz, -tx, top, tz), look.inner, look.inner, lightOf(NORMALS[3]) / light.coerceAtLeast(0.01f), cull = true)
                if (area() > 0f) {
                    path.reset()
                    path.moveTo(px(0.16f, 0.22f), py(0.16f, 0.22f)); path.lineTo(px(0.84f, 0.22f), py(0.84f, 0.22f))
                    path.lineTo(px(0.84f, 0.8f), py(0.84f, 0.8f)); path.lineTo(px(0.16f, 0.8f), py(0.16f, 0.8f)); path.close()
                    fill.color = scaled(CaseInside.mix(look.inner, 0xFF000000.toInt(), 0.45f), light); c.drawPath(path, fill)
                }
            }
        }
    }

    /** [argb] with its colour times [l] (alpha kept). */
    private fun scaled(argb: Int, l: Float): Int {
        val k = l.coerceIn(0f, 1f)
        val a = argb ushr 24
        val r = (((argb shr 16) and 255) * k).toInt(); val g = (((argb shr 8) and 255) * k).toInt(); val b = ((argb and 255) * k).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * Update 205: [g]'s case back when none is handed in: its back scan ("Back of the box"), else the back cut from its full box
 * scan, else (Settings › Game look › Printed case backs) a printed one with its cover behind the console's back print.
 * Not the game's own scans with one console's case picked for all (R1 › Case).
 */
internal fun defaultCaseBack(g: Game, scans: List<Pair<String, String>>?, cover: Any?, aspect: Float): Any? {
    if (!LookStyles.caseForAll()) {
        scans?.firstOrNull { it.first == "Back of the box" }?.second?.let { return it }
        BoxTexture.fullScan(scans)?.let { return BoxTexture.model(it, BoxPart.BACK, g.platform) }
    }
    return if (Features.printedBacks.state.value) PrintedBack(LookStyles.casePlatform(g.platform), cover, emptyList(), null, aspect, gameDisplayName(g.name)) else null
}
