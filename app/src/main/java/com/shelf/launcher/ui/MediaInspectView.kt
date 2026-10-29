package com.shelf.launcher.ui

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.Packs
import com.shelf.launcher.data.cardArtFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 162: opening a game from its physical piece. When the game look draws the game as the real thing (a case, a
 * cartridge, a card, a disc, a 3D model), opening it lifts that piece out of its place: it separates, comes forward
 * and grows on one side of the game window while the other games step back (MediaInspect.amount, read by the
 * carousel), then turns slowly round once to show its front, side and back where it has them, and settles facing you.
 * A horizontal drag on it (or the right stick) turns it yourself; let go and it eases back. Closing turns it back,
 * shrinks it and flies it back exactly to its place (GameOrigin, read again then). The window's buttons and info stay
 * beside it. With the window's animation off, Lite effects or the system's animations off it just fades in and out.
 * Everything that moves is read while drawing; the pictures are the cards' own (mediaArtModel, Models3D's caches).
 */

/** Update 162: what the inspected piece is drawn as. */
internal enum class InspectKind {
    /** A case in 3D (Case3D): front, spine, back. */
    BOX,
    /** A cartridge / card: its face, a band of shell for its thickness, a plain rear shell. */
    SLAB,
    /** A disc: its label, a thin edge, the data side. */
    DISC
    // (Update 200: MODEL — a piece of the 3D shape library turned through its pre-rendered angles, 20° apart, like a
    // slide show — is gone: the 3D cartridges looks are drawn shells now, SLAB with the shell's own depth.)
}

/** Update 162: everything the inspected piece needs, worked out from the game look the home screen draws it in. */
internal class InspectPiece(
    val game: Game,
    val kind: InspectKind,
    /** Width / height of its front. */
    val aspect: Float,
    /** SLAB / DISC: its thickness against its longest side. */
    val depth: Float,
    /** The drawn cartridge / disc (as the card draws it). */
    val pick: MediaPick?,
    val label: Any?,
    val labelIsScan: Boolean,
    /** The game's own cartridge / disc picture ("Real discs") instead of a drawn one. */
    val realScan: String?,
    val cover: Any?,
    /** BOX: the case's front; MODEL: the picture in its window. */
    val front: Any?,
    /** BOX: the back cut out of the full box scan (update 170: else its printed back, [PrintedBack]; null: plain plastic). */
    var back: Any?,
    val physical: List<Pair<String, String>>?,
    /** Update 200: unused (it was the retired MODEL kind's 3D piece); always null. */
    val model: String?,
    /** SLAB: its shell's colour (side and back). */
    val tone: Color,
    /** DISC: a mini disc drawn at its real size (70 %). */
    val smallDisc: Boolean
) {
    /** Update 166: MODEL: the game's logo (SteamGridDB's, or yours), printed on the piece's back label. */
    var logo: Any? = null
    /** Update 189: BOX: the disc / cartridge inside it (comes out when the case is opened); null: nothing to show. */
    var content: InspectPiece? = null
}

/**
 * Update 189: the disc / cartridge that's in [g]'s case — its own picture when it was scraped, else the drawn one (its
 * console's disc or cartridge with the cover as the label), else a plain disc for a disc console.
 */
private fun contentPieceFor(g: Game, state: LauncherUiState): InspectPiece? {
    val k = g.stableKey
    val physical = state.physicalMedia[k].withoutFlat() // (Update 208: no flat-colour stand-ins, FlatArt)
    val cover: Any? = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
    val small = Features.bigGcDiscs.state.value.not()
    // Update 202: an Android game's scraped "cartridge" / "disc label" pictures are its store art (ScreenScraper's Android
    // entries), not a piece: WWE All Stars' opened case held a bare cover. It holds Voltcap's own green Android cartridge
    // (mediaFor → ANDROID_CART, or the shell you picked for it) with its cover as the label.
    val scans = if (androidOwnCase(g.platform)) null else physical
    // (Update 293, Toni: "use the real cartridges with the real cases": what lies in a game's case is its real cartridge /
    // disc — its own photo, else its console's authentic shell — never one of the custom looks' pieces.)
    // (Update 294, Toni: "same cartridge in and out of the case as on the home row": a cartridge is the piece the home row /
    // Hub cover flow draws — mediaPiece is the carousel's own choice (a shell picked in the game's menu first, then the
    // look's shell, e.g. your pack's Game Boy / N64 shell), wearing the same real label scan. Only a disc console keeps
    // update 293's rule: its real disc, never a pack's cartridge-shaped optical shell.)
    val discConsole = MediaShell.of(g.platform)?.let { it.disc || it == MediaShell.UMD } == true
    val piece = runCatching {
        if (discConsole) RealMedia.wanted { mediaPiece(g, CardArtType.CARTRIDGE_DISC, scans, cover, small, false) }
        else mediaPiece(g, CardArtType.CARTRIDGE_DISC, scans, cover, small, false)
    }.getOrNull()
    if (piece != null && (piece.kind == InspectKind.DISC || piece.kind == InspectKind.SLAB)) return piece
    return if (mediaRound(g.platform)) InspectPiece(
        g, InspectKind.DISC, 1f, 0.012f, MediaPick(MediaShell.DISC, genericDiscKey(g.platform)), cover, false, null, cover, null, null,
        physical, null, PLAIN_SHELL, false
    ) else null
}

/** Update 198: how far an opened case moves aside (share of its width) and shrinks, beside what came out of it. */
internal const val CASE_ASIDE_SHIFT = 0.25f
internal const val CASE_ASIDE_SHRINK = 0.48f

/** (Update 238, #116/#117: the case's own slight tilt (Case3D's default pitch), and how far a case may be tilted on top of it.) */
internal const val CASE_PITCH = 4f
/**
 * (Update 238, #117: 0° … 176° all told — never past upside down, where its missing bottom face would show.)
 * (Update 240, #117: Case3D draws its bottom now, so a case tilts all the way round like the other pieces, over its top
 * and under its bottom: −180° … 180°, wrapped.)
 */
internal const val BOX_PITCH_MIN = -180f
internal const val BOX_PITCH_MAX = 180f
/** (Update 238, #116: the slow return's curve — a gentle start, a long soft landing.) */
private val RETURN_EASE = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/**
 * Update 198: [g]'s case for the second screen's Details page — always its case (scanned front, printed back), with what's
 * inside for opening it.
 */
internal fun secondCasePiece(g: Game, state: LauncherUiState): InspectPiece {
    val k = g.stableKey
    val cover: Any? = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
    return boxPiece(g, state.physicalMedia[k].withoutFlat(), cover, own = !LookStyles.caseForAll(), mixed = false).also {
        if (it.back == null && Features.printedBacks.state.value) it.back = printedBackFor(g, state, it.aspect)
        it.content = contentPieceFor(g, state)
    }
}

/**
 * Update 198: a case that opens, standing on its own (the second screen's Details page): a tap opens it — its lid swings,
 * the case moves aside and its disc / cartridge comes out next to it (a tap on that puts it back), a drag turns either,
 * and it can start opened (Features.inspectOpenAtStart).
 */
@Composable
internal fun OpenableCase(piece: InspectPiece, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val motion = remember(piece.game.stableKey) { InspectMotion().apply { started = true; lift.floatValue = 1f } }
    val yawOf: () -> Float = remember(motion) { { motion.yaw() } }
    val openOf: () -> Float = remember(motion) { { motion.lid.floatValue } }
    val itemInCase: () -> Boolean = remember(motion) { { motion.out.floatValue <= 0.001f } }
    val caseItem = remember(piece) { caseItemOf(piece.content) }
    val startOpen = Features.inspectOpenAtStart.state.value
    LaunchedEffect(piece.game.stableKey, startOpen) {
        if (startOpen && piece.content != null && !motion.opened) { delay(250); motion.toggleCase(scope, true, true) }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val aspect = piece.aspect.coerceIn(0.25f, 4f)
        val itemPlace = remember(piece, aspect) { openItemPlace(piece, aspect) }
        // Update 202: the manual's booklet in the open lid (only for a game that has a manual).
        val hasManual = rememberHasManual(piece.game)
        val bookPlace = remember(piece, aspect, hasManual) { if (hasManual) openBookletPlace(piece, aspect) else null }
        // (Room for the disc beside it once it's out: the case a little smaller than the whole space.)
        val w = minOf(bw * 0.9f, bh * 0.92f * aspect).coerceAtLeast(8f)
        val h = w / aspect
        val left = (bw - w) / 2f
        val top = (bh - h) / 2f
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer {
                    val o = motion.out.floatValue
                    if (o > 0f) {
                        val s = 1f - CASE_ASIDE_SHRINK * o
                        scaleX = s; scaleY = s
                        translationX = -w * CASE_ASIDE_SHIFT * o
                        translationY = -h * 0.06f * o
                    }
                }
                .pointerInput(piece.content, itemPlace, bookPlace) {
                    detectTapGestures(onTap = { at ->
                        val sw = size.width.toFloat(); val sh = size.height.toFloat()
                        val onItem = itemPlace?.hit(at.x, at.y, sw, sh) == true
                        // (Update 202: a tap on the booklet opens the manual; everything else as before.)
                        if (!tapBooklet(motion, piece, bookPlace, onItem, at.x, at.y, sw, sh)) motion.tapCase(scope, piece.content != null, onItem)
                    })
                }
                .pointerInput(piece.content, itemPlace, bookPlace) {
                    // (Update 319: a short touch that hardly moved is the tap — see inspectTap.)
                    var downAt = androidx.compose.ui.geometry.Offset.Zero
                    var moved = 0f
                    var downTime = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { at -> downAt = at; moved = 0f; downTime = android.os.SystemClock.uptimeMillis() },
                        onDragEnd = {
                            motion.release(scope)
                            if (moved < 28.dp.toPx() && android.os.SystemClock.uptimeMillis() - downTime < 400L) {
                                val sw = size.width.toFloat(); val sh = size.height.toFloat()
                                val onItem = itemPlace?.hit(downAt.x, downAt.y, sw, sh) == true
                                if (!tapBooklet(motion, piece, bookPlace, onItem, downAt.x, downAt.y, sw, sh)) motion.tapCase(scope, piece.content != null, onItem)
                            }
                        },
                        onDragCancel = { motion.release(scope) }
                    ) { change, dx ->
                        change.consume(); moved += kotlin.math.abs(dx); motion.nudge(dx * 0.42f)
                    }
                }
        ) {
            Case3D(
                piece.game, piece.front, piece.physical, 0f, aspect, Modifier.fillMaxSize(),
                back = piece.back, yawOf = yawOf, fill = 0.97f, openOf = openOf, inside = caseItem, showItemOf = itemInCase
            )
        }
        val content = piece.content
        if (content != null) InspectContent(piece, content, motion, left, top, w, h, aspect, scope)
    }
}

/**
 * Update 200: where the disc / cartridge lies in the opened case, as Case3D draws it (the open book fitted in the case's
 * own box: the lid left of the middle, the tray right of it), for telling a tap on it from a tap on the case.
 */
internal class OpenItemPlace(private val shape: Float, private val r: FloatArray, private val onLid: Boolean, private val box: Boolean) {
    fun hit(x: Float, y: Float, bw: Float, bh: Float): Boolean {
        if (box) return true
        val fit = minOf(bh, bw / shape.coerceAtLeast(0.05f)) * 0.97f
        val k = 1f / 1.92f
        val w2 = fit * shape * k; val h2 = fit * k
        val x0 = if (onLid) bw / 2f - w2 else bw / 2f
        val top = bh / 2f - h2 / 2f
        val m = w2 * 0.08f
        return x >= x0 + r[0] * w2 - m && x <= x0 + r[2] * w2 + m && y >= top + r[1] * h2 - m && y <= top + r[3] * h2 + m
    }
}

internal fun openItemPlace(piece: InspectPiece, aspect: Float): OpenItemPlace? {
    val c = piece.content ?: return null
    val disc = c.kind == InspectKind.DISC
    val cAspect = if (disc) 1f else c.aspect.coerceIn(0.3f, 3f)
    val spec = CaseCatalog.forGame(piece.game)
    val kind = CaseInside.kindFor(spec, piece.game.platform, disc)
    val shape = spec?.aspect?.coerceIn(0.55f, 1.85f) ?: aspect
    return OpenItemPlace(shape, CaseInside.itemRect(kind, shape, cAspect, spec), spec?.mediaLeft == true, kind == CaseInside.Kind.BOX)
}

/**
 * Update 202: where the manual's booklet lies in the opened case — on the lid's side, as CaseInside.lid draws it
 * (CaseInside.bookletRect) — for telling a tap on it apart; null where the case has none (a cardboard box, or a case
 * holding its disc in the lid).
 */
internal fun openBookletPlace(piece: InspectPiece, aspect: Float): OpenItemPlace? {
    val c = piece.content
    val spec = CaseCatalog.forGame(piece.game)
    val kind = CaseInside.kindFor(spec, piece.game.platform, c?.let { it.kind == InspectKind.DISC })
    val shape = spec?.aspect?.coerceIn(0.55f, 1.85f) ?: aspect
    val r = CaseInside.bookletRect(spec, kind, shape) ?: return null
    return OpenItemPlace(shape, r, onLid = true, box = false)
}

/**
 * Update 202: a tap on the open case's booklet ([bookPlace]; the lid open and nothing taken out, and not on the disc,
 * [onItem]) opens the game's manual (downloaded first if needed). True when it did — then the case stays as it is.
 */
/**
 * A tap on the inspected piece at [at] (in its [sw] × [sh] box): a case opens / takes out what's in it / opens its
 * manual's booklet; a cartridge, disc or 3D piece turns once round. Update 319: shared by the tap and by a short touch
 * that hardly moved (it used to count as a drag, and the tap was lost).
 */
internal fun inspectTap(
    piece: InspectPiece, motion: InspectMotion, scope: CoroutineScope, itemPlace: OpenItemPlace?, bookPlace: OpenItemPlace?,
    at: androidx.compose.ui.geometry.Offset, sw: Float, sh: Float
) {
    val onItem = itemPlace?.hit(at.x, at.y, sw, sh) == true
    // (Update 191: a cardboard box opens too — its top flap.)
    // (Update 200: a step at a time — open, then a tap on the disc takes it out.)
    // (Update 202: a tap on the booklet in the open lid opens the manual.)
    if (piece.kind == InspectKind.BOX && tapBooklet(motion, piece, bookPlace, onItem, at.x, at.y, sw, sh)) Unit
    else if (piece.kind == InspectKind.BOX) motion.tapCase(scope, piece.content != null, onItem)
    // (Update 199: a cartridge, disc or 3D piece has nothing to open: it turns once round.)
    else motion.spinOnce(scope)
}

internal fun tapBooklet(
    motion: InspectMotion, piece: InspectPiece, bookPlace: OpenItemPlace?, onItem: Boolean, x: Float, y: Float, bw: Float, bh: Float
): Boolean {
    if (bookPlace == null || onItem || !motion.opened || motion.lid.floatValue < 0.9f || motion.out.floatValue > 0.1f) return false
    if (!bookPlace.hit(x, y, bw, bh)) return false
    val open = com.shelf.launcher.data.ManualLinks.open ?: return false
    open(piece.game)
    return true
}

/** Update 189: what the opening case draws lying in it, from its [content]. */
internal fun caseItemOf(content: InspectPiece?): CaseItem? = content?.let { c ->
    // (Update 282: the drawn piece's shell, so the one lying in the case is the same piece — CaseFaces — wearing what it
    // wears out of the case: on a library shell a disc game's cover, never its round disc-label scan, as PhysicalMedia.)
    val pick = c.pick?.platform?.takeIf { c.kind == InspectKind.SLAB && c.realScan == null }
    val discOnShell = pick != null && c.labelIsScan && c.cover != null && (pick == GENERIC_CART || pick.startsWith(SHELL_PREFIX) &&
        MediaShell.of(pick.substringAfter('|', ""))?.let { it.disc || it == MediaShell.UMD } == true)
    CaseItem(c.realScan, if (discOnShell) c.cover else c.label ?: c.cover, if (c.kind == InspectKind.DISC) 1f else c.aspect, c.tone.toArgbInt(), c.kind == InspectKind.DISC,
        pick = pick, scan = c.labelIsScan && !discOnShell)
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

internal val PLAIN_SHELL = Color(0xFF2C2E34)
internal val CUSTOM_SHELL = Color(0xFF55585F)
/** Update 202: Voltcap's own Android cartridge's green (tpl_cart_android). */
internal val ANDROID_SHELL = Color(0xFF26965B)

/** A cartridge's / card's thickness against its longest side. */
internal fun slabDepth(shell: MediaShell?): Float = when (shell) {
    MediaShell.DS, MediaShell.DS3, MediaShell.SWITCH, MediaShell.VITA, MediaShell.CARD, MediaShell.HUCARD -> 0.055f
    MediaShell.UMD -> 0.066f // (Update 207: Sony's 4.2 mm against 64 mm)
    MediaShell.N64, MediaShell.SNES, MediaShell.BLACK_CART -> 0.15f
    else -> 0.12f
}

/** The plain colour of a shell's back and side. */
internal fun shellTone(shell: MediaShell?): Color = when (shell) {
    MediaShell.SNES, MediaShell.NES, MediaShell.GB, MediaShell.GBC -> Color(0xFF9D9FA6)
    MediaShell.HUCARD -> Color(0xFFD5D6DA)
    MediaShell.N64, MediaShell.GBA -> Color(0xFF45474E)
    else -> PLAIN_SHELL
}

/** The shape the card draws [pick] in (a template cartridge has its own; with only the cover, its label frame's). */
private fun slabAspect(pick: MediaPick, labelIsScan: Boolean): Float {
    val own = isCustomPiece(pick.platform)
    if (own) return pick.aspect.coerceIn(0.3f, 3f)
    val tpl = MediaTemplates.of(pick.platform)
    // (Update 169: the plain shell always, as TemplateMedia draws it.)
    val c = tpl?.cart ?: tpl?.label
    return (c?.aspect ?: pick.aspect).coerceIn(0.3f, 3f)
}

/**
 * Update 162: the piece [g] is drawn as on the home screen, or null when its card isn't a physical piece (covers, wide
 * capsules…: the game window opens as before). Read in composition (it follows the look's switches).
 */
internal fun inspectPieceFor(g: Game, state: LauncherUiState): InspectPiece? =
    // (Testing hook's shell first, whatever the look draws: TestHooks `voltcap_test_inspect`.)
    // (Update 291, Toni: "inspect media should show the case as well": an Inspect request (the button / the menu) always
    // takes forcedPieceOf — the game's case with its cartridge / disc inside — before whatever the look draws.)
    ((if (MediaInspect.force == g.stableKey) forcedPieceOf(g, state) else null)
        ?: inspectPieceOf(g, state))?.also {
        it.logo = state.artOverrides[g.stableKey]?.logo ?: state.art[g.stableKey]?.logoUrl
        // Update 170: a case without its back scanned gets a printed one (Settings › Game look › Printed case backs).
        // (Update 302: no back scan — TheGamesDB's is asked for once, with your key; it replaces the printed one when found.)
        if (it.kind == InspectKind.BOX && it.back == null) com.shelf.launcher.data.TgdbBacks.request(g)
        // (Update 305: and its verified details for the printed back — ratings, publisher… — once.)
        if (it.kind == InspectKind.BOX && it.back == null && Features.printedBacks.state.value) com.shelf.launcher.data.PackagingFactsStore.request(g)
        if (it.kind == InspectKind.BOX && it.back == null && Features.printedBacks.state.value) it.back = printedBackFor(g, state, it.aspect)
        // Update 189: what's inside, for opening it.
        if (it.kind == InspectKind.BOX) it.content = contentPieceFor(g, state)
    }

/**
 * Update 170: what [g]'s printed back is made of: its console's back cover (the case's console — R1 › Case picks one for
 * all), the hero art behind (else the cover), its screenshots and its description, in the case's shape [aspect].
 */
internal fun printedBackFor(g: Game, state: LauncherUiState, aspect: Float): PrintedBack {
    val k = g.stableKey
    val o = state.artOverrides[k]; val a = state.art[k]
    // (Update 305: the game's verified details — its Steam store / ScreenScraper record — and its metadata: what a back
    // without a console template prints (CollectorBacks). Steam's own description and screenshots stand in where the
    // scrapers had none.)
    val facts = com.shelf.launcher.data.PackagingFactsStore.of(k)
    val meta = state.metadata[k]
    val shots = state.screenshots[k].orEmpty().ifEmpty { facts?.shots.orEmpty() }
    return PrintedBack(
        // (Update 305: a Steam / Android game's back is its own case's — as its front and spine are, whatever R1 › Case.)
        if (MediaCovers.own(g.platform) != null) g.platform else LookStyles.casePlatform(g.platform),
        o?.hero ?: a?.heroUrl ?: o?.cover ?: a?.gridUrl,
        shots.take(4),
        meta?.summary?.takeIf { it.isNotBlank() } ?: facts?.description,
        aspect,
        // (Update 305: the printed title without the file's bracketed tags.)
        printTitle(g.name),
        publisher = facts?.publisher ?: meta?.publisher?.takeIf { it.isNotBlank() },
        developer = facts?.developer ?: meta?.developer?.takeIf { it.isNotBlank() },
        year = meta?.releaseYear ?: facts?.year,
        players = facts?.players,
        features = facts?.features.orEmpty(),
        rating = facts?.let {
            com.shelf.launcher.data.PackagingFactsStore.pick(it.ratings, Features.backRating.state.value.system, Features.backRatingOthers.state.value)
        }
    )
}

/**
 * Update 274 (Toni): the game's menu › Inspect…: its cartridge / card / disc as the cartridge-and-disc look draws it,
 * else its case — for a look that draws a plain cover (inspectPieceOf has nothing to show then).
 */
private fun forcedPieceOf(g: Game, state: LauncherUiState): InspectPiece? {
    val k = g.stableKey
    val physical = state.physicalMedia[k].withoutFlat()
    val cover: Any? = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
    // (Testing only: TestHooks `voltcap_test_inspect` — this shell, nothing saved.)
    MediaInspect.testShell?.let { id ->
        // (Update 286, testing only: "case" — the game's case as the case looks draw it, whatever the look; for checking
        // every face of the cases and boxes at set angles.)
        if (id == "case") return casePiece(g, physical, cover, false)
        // (Update 276: "pack:<shape>" — a pack shape picked as the shell; "cc:<console>" — the game on your pack.)
        if (id.startsWith("pack:") || id.startsWith("cc:")) {
            val ms = MediaShell.values().first { !it.disc && it != MediaShell.UMD }
            val pick = if (id.startsWith("pack:")) {
                val look = CartLook.values().firstOrNull { it.key == id.removePrefix("pack:") } ?: return@let
                MediaPick(ms, SHELL_PREFIX + "pack:" + look.key + "~opaque|" + g.platform, look.aspect)
            } else id.removePrefix("cc:").let { c -> MediaPick(MediaShell.GB, CUSTOM_CART + c, CustomCarts.aspectFor(c)) }
            return InspectPiece(g, InspectKind.SLAB, pick.aspect, CustomCarts.depthForPick(pick.platform) ?: slabDepth(null), pick, cover, false, null, cover, null, null, physical, null,
                CustomPieceTones.of(pick.platform) ?: CUSTOM_SHELL, false)
        }
        val sh = CartShells.of(id) ?: return@let
        val ms = MediaShell.of(g.platform)?.takeIf { !it.disc && it != MediaShell.UMD } ?: MediaShell.values().first { !it.disc && it != MediaShell.UMD }
        val pick = MediaPick(ms, SHELL_PREFIX + id + "|" + g.platform, sh.aspect)
        return InspectPiece(g, InspectKind.SLAB, sh.aspect, sh.depth.coerceIn(0.02f, 0.4f), pick, cover, false, null, cover, null, null, physical, null, shellTone(ms), false)
    }
    val small = Features.bigGcDiscs.state.value.not()
    val mixed = mixedConsoles(state.visibleGames)
    // (Update 291, Toni: "inspect media should show the case as well": the case first — it opens (a stick click) to the
    // cartridge / disc it holds (contentPieceFor) — and the piece alone only where a case can't be made.)
    return runCatching { casePiece(g, physical, cover, mixed) }.getOrNull()
        ?: runCatching { mediaPiece(g, CardArtType.CARTRIDGE_DISC, physical, cover, small, mixed) }.getOrNull()
}

private fun inspectPieceOf(g: Game, state: LauncherUiState): InspectPiece? {
    val a = state.appearance
    val k = g.stableKey
    val p = g.platform
    val physical = state.physicalMedia[k].withoutFlat() // (Update 208: no flat-colour stand-ins, FlatArt)
    val cover: Any? = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
    val wide: Any? = state.artOverrides[k]?.wide ?: state.art[k]?.wideGridUrl
    val small = Features.bigGcDiscs.state.value.not()
    // (Update 162 (review): a list mixing consoles draws its games by "Mixed categories", as the carousel's cards do.)
    val mixed = mixedConsoles(state.visibleGames)
    // Disc station: every game is a disc there, whatever the look (as LaunchThrow's).
    if (a.homeLayout == HomeLayout.DISCS) {
        val pick = mediaFor(p, asDisc = true)
            ?.takeIf { it.shell.disc || it.shell == MediaShell.UMD || it.platform.startsWith(GENERIC_DISC) }
            ?: MediaPick(MediaShell.DISC, genericDiscKey(if (p == "App") "Android" else p))
        val typed = if (mediaKind(p) != "disc") null else typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, p, g.stableKey)
        val label = typed ?: cover ?: wide
        val isScan = typed != null && label == typed
        return if (pick.shell == MediaShell.UMD) InspectPiece(
            g, InspectKind.SLAB, slabAspect(pick, isScan), slabDepth(MediaShell.UMD), pick, label, isScan, null, cover, null, null,
            physical, null, shellTone(MediaShell.UMD), false
        ) else InspectPiece(
            g, InspectKind.DISC, 1f, 0.012f, pick, label, isScan, null, cover, null, null, physical, null, PLAIN_SHELL,
            pick.shell == MediaShell.MINI_DISC && small
        )
    }
    val chosen = a.cardArtFor()
    // (Update 162 (review): the carousel's 3D cases (MediaInspect.drawnAsCase) count for the case looks too — a console
    // without a template case is still a 3D case there; mixed lists as the cards draw them.)
    val drawnAsCase = MediaInspect.drawnAsCase == k
    return when (artForGame(chosen, p, a.hiddenMedia, mixed, gameKey = g.stableKey)) {
        CardArtType.CASE -> if (!isFreeMedia(CardArtType.CASE, p) && !drawnAsCase) null else casePiece(g, physical, cover, mixed)
        CardArtType.CARTRIDGE_DISC -> mediaPiece(g, chosen, physical, cover, small, mixed)
        // A cover look the carousel draws as 3D cases (it says so for the game you're on): the box with the cover on its
        // front, as the carousel shows it. (Update 200: never the shape library's pre-rendered case any more.)
        CardArtType.COVER -> if (!drawnAsCase) null else boxPiece(g, physical, cover, own = true, mixed = mixed, scanFront = false)
        else -> null
    }
}

private fun casePiece(g: Game, physical: List<Pair<String, String>>?, cover: Any?, mixed: Boolean): InspectPiece {
    val casePlatform = LookStyles.casePlatform(g.platform)
    // One console's case picked for all (R1 › Case): its shape only, never this game's scans in it.
    val own = !LookStyles.caseForAll()
    // (Update 200: the shape library's pre-rendered 3D case — R1 › 3D in a case look — is retired: the drawn Case3D.)
    return boxPiece(g, physical, cover, own, mixed)
}

private fun boxPiece(
    g: Game, physical: List<Pair<String, String>>?, cover: Any?, own: Boolean, mixed: Boolean,
    /** Update 162 (review): the scanned case front on it (the case looks); false: the cover, as the cover look's 3D cases. */
    scanFront: Boolean = true
): InspectPiece {
    // The case's own scanned front (Original cases), else the cover; the back cut from the full box scan, if there's one.
    val scan = if (own) BoxTexture.front(physical, g.platform) else null
    // (Update 202: an Android game's own case shows its cover in the Android band's window — its scraped "box" is store
    // art, used only when there's no cover; androidOwnCase.)
    val front = if (scan != null && (cover == null || (scanFront && Features.originalCases.state.value && !androidOwnCase(g.platform)))) scan else cover ?: scan
    // (Update 205: ScreenScraper's own back scan first — the whole back, nothing to cut; the back cut out of the full
    // box scan only without one.)
    val back = if (own) physical?.firstOrNull { it.first == "Back of the box" }?.second
        ?: BoxTexture.fullScan(physical)?.let { BoxTexture.model(it, BoxPart.BACK, g.platform) } else null
    return InspectPiece(
        g, InspectKind.BOX, case3dAspect(g, mixed), 0f, null, null, false, null, cover, front, back, physical, null, PLAIN_SHELL, false
    )
}

private fun mediaPiece(
    g: Game, chosen: CardArtType, physical: List<Pair<String, String>>?, cover: Any?, small: Boolean, mixed: Boolean
): InspectPiece? {
    val p = g.platform
    val typed = typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, p, g.stableKey)
    // (Update 162 (review): [mixed] as the cards ask for it — "All CDs" / "All cartridges" draw the generic piece.)
    val pick = mediaFor(p, mixed, chosen == CardArtType.DISC, gameKey = g.stableKey)
    // (Update 200: the 3D cartridges looks come here as drawn shells — a SLAB at the shell's own depth below, turning
    // smoothly — never the pre-rendered 3D models.)
    // "Real discs": the game's own cartridge / disc picture. (Update 200: not in the 3D cartridges looks — the cards
    // draw their drawn piece there, drawnMediaWanted.)
    // (Update 205: exactly as the cards decide — its own photo when "Use its own photo" is on and it has one, else the
    // drawn piece. Since 202 the cards showed the photo while the window still showed the drawn shell with the cover on
    // it, so a game turned into another cartridge when opened. A shell picked for the game wins in both: typedArtFor.)
    val real = realScanOf(CardArtType.CARTRIDGE_DISC, typed)
    if (real != null) {
        val shell = MediaShell.of(p)
        // (Update 207: a UMD's photo is the UMD — its shell, a flat slab — never a spinning disc; PSP games turned into a
        // round disc here because a UMD counts as round for its label.)
        return if (mediaRound(p) && shell != MediaShell.UMD) InspectPiece(
            g, InspectKind.DISC, 1f, 0.012f, null, null, false, real, cover, null, null, physical, null, PLAIN_SHELL, false
        ) else InspectPiece(
            // (Update 207: at the photo's own shape once it's known, so the back cut to its outline lines up.)
            g, InspectKind.SLAB, ((real as? String)?.let { CoverAspects.rawOf(it) } ?: shell?.aspect ?: 1f).coerceIn(0.3f, 3f), slabDepth(shell), null, null, false, real, cover, null, null,
            physical, null, shellTone(shell), false
        )
    }
    if (pick == null) return null
    // (Update 205: the drawn piece wears its real label scan when it has one, as on the home screen.)
    // (Update 305: the real disc lying in its case — RealMedia, also with Cartridges only on — wears its label: your own
    // label pick or its label scan; it always showed the cover there.)
    val labelScan = if (typed == null) drawnLabelScan(physical, p)
        ?: (if (RealMedia.on && pick.shell.disc) physical?.firstOrNull { it.first == "Disc label" }?.second else null) else null
    val label = typed ?: labelScan ?: cover
    val isScan = typed != null || labelScan != null
    val custom = isCustomPiece(pick.platform)
    val tpl = if (custom) null else MediaTemplates.of(pick.platform)
    return when {
        // "Disc in its case": the card shows the case — that's what comes out.
        tpl != null && discCaseOn(tpl, pick.platform) -> boxPiece(g, physical, cover, own = true, mixed = mixed)
        pick.shell.disc -> InspectPiece(
            g, InspectKind.DISC, 1f, 0.012f, pick, label, isScan, null, cover, null, null, physical, null, PLAIN_SHELL,
            pick.shell == MediaShell.MINI_DISC && small
        )
        else -> InspectPiece(
            g, InspectKind.SLAB, slabAspect(pick, isScan),
            // (Update 195: a library shell at its own real thickness.)
            // (Update 306: a pack shape at its real cartridge's thickness — CustomCarts.depthForPick.)
            if (pick.platform.startsWith(SHELL_PREFIX)) (CartShells.split(pick.platform).first?.shell?.depth ?: CustomCarts.depthForPick(pick.platform) ?: 0.12f).coerceIn(0.02f, 0.4f)
            else if (custom) CustomCarts.depthForPick(pick.platform) ?: slabDepth(null)
            else slabDepth(pick.shell), pick, label, isScan, null,
            cover, null, null, physical, null,
            // (Update 202: Voltcap's Android cartridge is green — its edge and the card lying in the case too.)
            // (Update 228, #36: a custom piece in its own plastic — the library shell's colour, the pack picture's once
            // read — not grey.)
            if (custom) CustomPieceTones.of(pick.platform) ?: CUSTOM_SHELL else if (pick.platform == ANDROID_CART) ANDROID_SHELL else shellTone(pick.shell), false
        )
    }
}

/** Update 162: where [key]'s card is now (window px), when it's marked on this screen ([displayId]); else null. */
internal fun inspectOrigin(key: String, displayId: Int): Rect? {
    val r = GameOrigin.rect ?: return null
    if (GameOrigin.key != key && GameOrigin.key != GameOrigin.FOCUSED) return null
    if (GameOrigin.displayId >= 0 && displayId >= 0 && GameOrigin.displayId != displayId) return null
    if (r.width < 4f || r.height < 4f || r.top == Float.MAX_VALUE) return null
    return r
}

/**
 * Update 162: the inspected piece's motion, owned by the game window. Plain float states, read only while drawing
 * (nothing recomposes while it flies or turns).
 */
/** 0.6.18: a flick's gentle coast — the most it starts with (°/s), the least that counts, where it stops, and the friction (1/s). */
internal const val FLING_MAX = 480f
internal const val FLING_MIN = 60f
internal const val FLING_STOP = 6f
internal const val FLING_FRICTION = 1.9f

internal class InspectMotion {
    /** 0 = in its place on the home screen, 1 = out in front. */
    val lift = mutableFloatStateOf(0f)
    /** The showcase turn, degrees (positive: the front turns right, so its left side — a case's spine — shows). */
    val turn = mutableFloatStateOf(0f)
    /** Your own turn on top (drag / right stick). */
    val manual = mutableFloatStateOf(0f)
    /** Its opacity (the short cross-fade used instead of the flight). */
    val fade = mutableFloatStateOf(1f)
    /**
     * Update 274 (Toni): the gentle sway, degrees — ±24° left and right over [CART_SPIN_MS], as the pieces on the home
     * rows sway — while it's left alone: after its turn back (Features.inspectSway), and all the time with Wobble
     * (Features.inspectMode). Any touch, the stick, Y or a tap folds it into the turn and stops it (no jump).
     */
    val sway = mutableFloatStateOf(0f)
    private var swayJob: Job? = null
    /** Update 274: Wobble — it can't be turned by hand (drag, stick, tap's turn, Y); it only sways. */
    var handsOff = false

    /** Update 274, testing only: held at a set angle (MediaInspect.testPose) — no turn, no sway. */
    var frozen = false
    fun holdAt(yaw: Float, pitchDeg: Float) {
        frozen = true
        spinJob?.cancel(); easeJob?.cancel(); swayJob?.cancel(); sway.floatValue = 0f
        turn.floatValue = 0f; manual.floatValue = yaw; pitch.floatValue = pitchDeg
    }

    /** Update 274: starts the sway, fading in, unless it's swaying already, closing, or a case is open. */
    fun startSway(scope: CoroutineScope) {
        if (closing || !started || frozen) return
        if (!handsOff && !Features.inspectSway.state.value) return
        if (swayJob?.isActive == true) return
        swayJob = scope.launch {
            var t = 0f
            var amp = 0f
            var last = -1L
            while (!closing) {
                val now = withFrameNanos { it }
                if (last > 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    // (Not while a case is open — the open book is drawn from the front only.)
                    val caseBusy = opened || lid.floatValue > 0.001f || out.floatValue > 0.001f
                    val target = if (caseBusy) 0f else 1f
                    amp += (target - amp) * (dt * 1.6f).coerceAtMost(1f)
                    t += dt
                    sway.floatValue = 24f * amp * sin(t * 1000f / CART_SPIN_MS * 2f * PI.toFloat())
                }
                last = now
            }
        }
    }

    /** Update 274: stops the sway where it is, folded into your own turn, so nothing jumps. */
    fun stopSway() {
        swayJob?.cancel(); swayJob = null
        val s = sway.floatValue
        if (s != 0f) { manual.floatValue += s; sway.floatValue = 0f }
    }
    /** The right stick's left / right (−1…1), 0 at rest. */
    val stick = mutableFloatStateOf(0f)
    /** Where it came from (window px); on the way back its place is read again. */
    var from: Rect? = null
    /**
     * Update 330: it comes out of a box where it stood spine-out (Cardboard box): it starts turned to its spine, at its
     * spine's height, and turns to face you as it comes forward — and back to its spine on its way home.
     */
    var fromSpine = false
    /** Update 330: how far it's turned from facing you when it starts out from a spine (90: spine-on). */
    var spineYaw = 90f
    /** Update 400: the case's front picture is drawn-ready (set by its Case3D). */
    @Volatile var artReady = false
    var started = false
    var closing = false
    /** A 3D model: it turns only as far as its pictures go (±60°). */
    var limited = false
    /** Update 189: a case's lid, 0 shut … 1 wide open. */
    val lid = mutableFloatStateOf(0f)
    /** Update 189: 0 the disc / cartridge in its case … 1 out in the middle, the case aside. */
    val out = mutableFloatStateOf(0f)
    /** Update 189: the case is (being) opened. */
    var opened = false
    /** Update 189: your own turn of what came out of the case (a drag on it), degrees. */
    val contentTurn = mutableFloatStateOf(0f)
    private var contentEase: Job? = null

    /** Update 189: turns what came out of the case [deg] more by hand. */
    fun nudgeContent(deg: Float) { contentEase?.cancel(); contentTurn.floatValue += deg }

    /**
     * Update 324 (Toni: "a disc or cartridge taken out of its case can't be turned all the way round, only in the
     * cartridge / disc looks"): it tilts over its top and under its bottom too (−180…180, wrapped), as a piece of the
     * Cartridges & discs looks does; it comes back with [releaseContent].
     */
    val contentPitch = mutableFloatStateOf(0f)
    fun nudgeContentPitch(deg: Float) {
        contentEase?.cancel()
        var p = contentPitch.floatValue + deg
        if (p > 180f) p -= 360f
        if (p < -180f) p += 360f
        contentPitch.floatValue = p
    }

    /**
     * Update 189: let go: it turns back to face you (the short way). (Update 238, #116: after the hold you picked, slowly
     * — or not at all with "Turned pieces turn back by themselves" off; turning it again stops the return at once.)
     */
    fun releaseContent(scope: CoroutineScope) {
        contentEase?.cancel()
        if (!Features.inspectAutoReturn.state.value) return
        val hold = Features.inspectHold.state.value.ms
        val dur = Features.inspectReturnSpeed.state.value.ms
        contentEase = scope.launch {
            delay(hold)
            val now = contentTurn.floatValue
            val to = (now / 360f).roundToInt() * 360f
            val p0 = contentPitch.floatValue
            animate(0f, 1f, animationSpec = tween(dur, easing = RETURN_EASE)) { k, _ ->
                contentTurn.floatValue = now + (to - now) * k
                contentPitch.floatValue = p0 * (1f - k)
            }
            contentTurn.floatValue = 0f
            contentPitch.floatValue = 0f
        }
    }

    /** Update 238 (#117): your tilt over its top / under its bottom, degrees (positive: its top comes towards you). */
    val pitch = mutableFloatStateOf(0f)
    /** Update 238 (#117): the right stick's up / down (−1…1), 0 at rest. */
    val stickY = mutableFloatStateOf(0f)
    /**
     * Update 238 (#117): a case — its own slight tilt (CASE_PITCH) added in yawSign. (Update 240, #117: it tilts all the
     * way round now too, as a cartridge / card / disc does (−180…180, wrapped): Case3D has its bottom face.)
     */
    var boxPitch = false

    /** Update 238 (#117): tilts it [deg] more by hand (stops the showcase turn and any return). Not while a case is open. */
    fun nudgePitch(deg: Float) {
        if (closing || !started || handsOff) return
        // (The open book — CaseOpenDraw — is drawn only from the front.)
        if (opened || lid.floatValue > 0.001f || out.floatValue > 0.001f) return
        spinJob?.cancel(); easeJob?.cancel(); stopSway()
        var p = pitch.floatValue + deg
        // (Update 240, #117: wrapped for a case too — it was held to 0…176° all told.)
        if (p > 180f) p -= 360f else if (p < -180f) p += 360f
        pitch.floatValue = p
    }

    /**
     * Update 238 (#117): −1 while it's tilted past upright (upside down), else 1 — a sideways drag / stick turns it the
     * way your finger goes even then.
     */
    fun yawSign(): Float {
        val total = pitch.floatValue + (if (boxPitch) CASE_PITCH else 0f)
        return if (cos(Math.toRadians(total.toDouble())) < 0.0) -1f else 1f
    }

    /** Update 238 (#117): its tilt back to upright (the short way), over [ms]. */
    private suspend fun levelPitch(ms: Int) {
        val p0 = pitch.floatValue
        if (abs(p0) > 0.1f) animate(p0, 0f, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { v, _ -> pitch.floatValue = v }
        pitch.floatValue = 0f
    }

    /**
     * Update 238 (#117): back to how it first stood — facing you, upright (Y, or a long press on it). At once,
     * whatever the return settings say.
     */
    fun resetPose(scope: CoroutineScope) {
        if (closing || !started || handsOff) return
        spinJob?.cancel(); easeJob?.cancel(); contentEase?.cancel(); stopSway()
        easeJob = scope.launch {
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            turn.floatValue = total; manual.floatValue = 0f
            val p0 = pitch.floatValue
            val c0 = contentTurn.floatValue
            val cTo = (c0 / 360f).roundToInt() * 360f
            animate(0f, 1f, animationSpec = tween(480, easing = FastOutSlowInEasing)) { v, _ ->
                turn.floatValue = total + (front - total) * v
                pitch.floatValue = p0 * (1f - v)
                contentTurn.floatValue = c0 + (cTo - c0) * v
            }
            turn.floatValue = 0f; pitch.floatValue = 0f; contentTurn.floatValue = 0f
            startSway(scope)
        }
    }
    private var caseJob: Job? = null

    /**
     * Update 189: opens the case (it turns to face you, the lid swings open — not a cardboard box, [hinged] false —
     * then, with something inside, [hasContent], the case moves aside and that comes out) or closes it again.
     */
    fun toggleCase(scope: CoroutineScope, hinged: Boolean, hasContent: Boolean) {
        if (closing || !started) return
        caseJob?.cancel(); stopSway()
        opened = !opened
        caseJob = scope.launch {
            if (opened) {
                spinJob?.cancel(); easeJob?.cancel()
                levelPitch(300) // (Update 238, #117: upright first — the open book is drawn from the front only.)
                val total = turn.floatValue + manual.floatValue
                val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
                turn.floatValue = total
                manual.floatValue = 0f
                if (abs(front - total) > 0.5f) animate(total, front, animationSpec = tween(380, easing = FastOutSlowInEasing)) { v, _ -> turn.floatValue = v }
                turn.floatValue = 0f
                if (hinged) animate(lid.floatValue, 1f, animationSpec = tween(760, easing = CubicBezierEasing(0.35f, 0f, 0.2f, 1f))) { v, _ -> lid.floatValue = v }
                if (hasContent) {
                    delay(if (hinged) 260 else 0)
                    animate(out.floatValue, 1f, animationSpec = tween(700, easing = CubicBezierEasing(0.3f, 0f, 0.15f, 1f))) { v, _ -> out.floatValue = v }
                }
            } else {
                if (out.floatValue > 0f) animate(out.floatValue, 0f, animationSpec = tween(560, easing = FastOutSlowInEasing)) { v, _ -> out.floatValue = v }
                if (lid.floatValue > 0f) animate(lid.floatValue, 0f, animationSpec = tween(620, easing = FastOutSlowInEasing)) { v, _ -> lid.floatValue = v }
            }
        }
    }
    /**
     * Update 200: opening a step at a time. [openLid] opens the case (turned to face you, its lid swung open, what's in it
     * still lying in it, so the open case can be looked at and turned); [takeOut] lifts the disc / cartridge out beside it;
     * [putBack] lays it back in the open case; [closeCase] puts it back and shuts the lid.
     */
    fun openLid(scope: CoroutineScope) {
        if (closing || !started) return
        caseJob?.cancel(); stopSway()
        opened = true
        caseJob = scope.launch {
            spinJob?.cancel(); easeJob?.cancel()
            levelPitch(300) // (Update 238, #117: upright first.)
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            turn.floatValue = total
            manual.floatValue = 0f
            if (abs(front - total) > 0.5f) animate(total, front, animationSpec = tween(380, easing = FastOutSlowInEasing)) { v, _ -> turn.floatValue = v }
            turn.floatValue = 0f
            animate(lid.floatValue, 1f, animationSpec = tween(760, easing = CubicBezierEasing(0.35f, 0f, 0.2f, 1f))) { v, _ -> lid.floatValue = v }
        }
    }

    fun takeOut(scope: CoroutineScope) {
        if (closing || !started) return
        caseJob?.cancel(); stopSway()
        opened = true
        caseJob = scope.launch {
            // (Turned to face you first, so it lifts out of where it's seen lying.)
            spinJob?.cancel(); easeJob?.cancel()
            levelPitch(300) // (Update 238, #117: upright too.)
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            turn.floatValue = total; manual.floatValue = 0f
            if (abs(front - total) > 0.5f) animate(total, front, animationSpec = tween(300, easing = FastOutSlowInEasing)) { v, _ -> turn.floatValue = v }
            turn.floatValue = 0f
            if (lid.floatValue < 1f) animate(lid.floatValue, 1f, animationSpec = tween(500, easing = FastOutSlowInEasing)) { v, _ -> lid.floatValue = v }
            animate(out.floatValue, 1f, animationSpec = tween(700, easing = CubicBezierEasing(0.3f, 0f, 0.15f, 1f))) { v, _ -> out.floatValue = v }
        }
    }

    fun putBack(scope: CoroutineScope) {
        if (closing) return
        caseJob?.cancel()
        // (Update 324: back in the case upright.)
        contentEase?.cancel(); contentPitch.floatValue = 0f
        caseJob = scope.launch {
            if (out.floatValue > 0f) animate(out.floatValue, 0f, animationSpec = tween(560, easing = FastOutSlowInEasing)) { v, _ -> out.floatValue = v }
        }
    }

    fun closeCase(scope: CoroutineScope) {
        if (closing) return
        caseJob?.cancel()
        opened = false
        caseJob = scope.launch {
            if (out.floatValue > 0f) animate(out.floatValue, 0f, animationSpec = tween(560, easing = FastOutSlowInEasing)) { v, _ -> out.floatValue = v }
            if (lid.floatValue > 0f) animate(lid.floatValue, 0f, animationSpec = tween(620, easing = FastOutSlowInEasing)) { v, _ -> lid.floatValue = v }
            startSway(scope) // (Update 274: shut again, it sways again.)
        }
    }

    /**
     * Update 200: a tap on the case. Shut: it opens. Open with the disc in it: a tap on the disc ([onItem]) takes it out,
     * a tap elsewhere shuts it. With the disc out: the disc goes back and the case shuts.
     */
    fun tapCase(scope: CoroutineScope, hasContent: Boolean, onItem: Boolean) {
        when {
            !opened -> openLid(scope)
            out.floatValue > 0.5f -> closeCase(scope)
            hasContent && onItem -> takeOut(scope)
            else -> closeCase(scope)
        }
    }

    /** Update 200: a click of either stick: open → take out → put back and shut, one step a click. */
    fun stickClick(scope: CoroutineScope, hasContent: Boolean) {
        when {
            !opened -> openLid(scope)
            hasContent && out.floatValue < 0.5f -> takeOut(scope)
            else -> closeCase(scope)
        }
    }

    private var openJob: Job? = null
    private var spinJob: Job? = null
    private var easeJob: Job? = null

    /** The angle to draw it at now. */
    fun yaw(): Float {
        val k = lift.floatValue
        // A little turn as it flies (and square again when it lands), so it moves like a thing, not a picture.
        val fly = if (fromSpine) {
            // (Update 330: from its spine (90°) to its front, eased.)
            val e = (1f - k).coerceIn(0f, 1f)
            spineYaw * e * e * (3f - 2f * e)
        } else if (k > 0f && k < 1f) -14f * sin(PI.toFloat() * k) else 0f
        val y = turn.floatValue + manual.floatValue + fly + sway.floatValue
        return if (limited) y.coerceIn(-60f, 60f) else y
    }

    /**
     * 0.6.18 (Toni): let go of a drag with speed and it keeps turning, slower and slower, until it stops — then the usual
     * return ([release]). Gentle on purpose: at most [FLING_MAX]° a second and an easy friction, so even a hard flick
     * turns it about three quarters round at most. A new touch / the stick / Y / a tap stop it (nudge, nudgePitch,
     * spinOnce and resetPose cancel spinJob). [vYaw] / [vPitch]: degrees a second as you let go.
     */
    fun fling(scope: CoroutineScope, vYaw: Float, vPitch: Float) {
        if (closing || !started || handsOff) { release(scope); return }
        var vy = vYaw.coerceIn(-FLING_MAX, FLING_MAX)
        val caseBusy = opened || lid.floatValue > 0.001f || out.floatValue > 0.001f
        var vp = if (caseBusy) 0f else vPitch.coerceIn(-FLING_MAX * 0.6f, FLING_MAX * 0.6f)
        if (abs(vy) < FLING_MIN && abs(vp) < FLING_MIN) { release(scope); return }
        spinJob?.cancel(); easeJob?.cancel(); stopSway()
        spinJob = scope.launch {
            var last = -1L
            while ((abs(vy) >= FLING_STOP || abs(vp) >= FLING_STOP) && !closing) {
                val now = withFrameNanos { it }
                if (last > 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    var m = manual.floatValue + vy * dt
                    if (limited) {
                        val lo = -60f - turn.floatValue; val hi = 60f - turn.floatValue
                        if (m < lo || m > hi) { m = m.coerceIn(lo, hi); vy = 0f }
                    }
                    manual.floatValue = m
                    if (vp != 0f) {
                        var p = pitch.floatValue + vp * dt
                        if (p > 180f) p -= 360f else if (p < -180f) p += 360f
                        pitch.floatValue = p
                    }
                    val k = kotlin.math.exp(-FLING_FRICTION * dt)
                    vy *= k; vp *= k
                }
                last = now
            }
            release(scope)
        }
    }

    /** Turns it [deg] more by hand (stops the showcase turn). */
    fun nudge(deg: Float) {
        if (handsOff) return
        spinJob?.cancel(); easeJob?.cancel(); stopSway()
        var m = manual.floatValue + deg
        if (limited) m = m.coerceIn(-60f - turn.floatValue, 60f - turn.floatValue)
        manual.floatValue = m
    }

    /**
     * Update 199: a tap on a cartridge, disc or 3D piece (nothing to open): it turns once round to show every side (a 3D
     * model: to one side, the other and back), from facing you. A drag or the stick stops it.
     */
    fun spinOnce(scope: CoroutineScope) {
        if (closing || !started || handsOff || lift.floatValue < 0.999f || spinJob?.isActive == true) return
        easeJob?.cancel(); stopSway()
        spinJob = scope.launch {
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            turn.floatValue = 0f
            // (Update 238, #117: upright again too, in the same move.)
            val p0 = pitch.floatValue
            animate(0f, 1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ ->
                manual.floatValue = total + (front - total) * v
                pitch.floatValue = p0 * (1f - v)
            }
            manual.floatValue = 0f; pitch.floatValue = 0f
            showcase()
            startSway(scope)
        }
    }

    /**
     * Let go: it eases back to facing you. (Update 238, #116/#117: it stays at its angle — turned and tilted — for the
     * hold you picked (Settings › Game look › More look options › "Stay turned for" / "Turning back"), then turns back
     * slowly and gently, upright as well; with "Turned pieces turn back by themselves" off it stays as you left it. A
     * drag, the stick, a tap or Y stops the return at once — nudge / nudgePitch / spinOnce / resetPose cancel it.)
     */
    fun release(scope: CoroutineScope) {
        if (closing) return
        easeJob?.cancel()
        val hold = Features.inspectHold.state.value.ms
        // (Update 274: left where you let go, it still sways again after the hold.)
        if (!Features.inspectAutoReturn.state.value) { easeJob = scope.launch { delay(hold); startSway(scope) }; return }
        val dur = Features.inspectReturnSpeed.state.value.ms
        easeJob = scope.launch {
            delay(hold)
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            val m0 = manual.floatValue
            val mTo = front - turn.floatValue
            val p0 = pitch.floatValue
            animate(0f, 1f, animationSpec = tween(dur, easing = RETURN_EASE)) { v, _ ->
                manual.floatValue = m0 + (mTo - m0) * v
                pitch.floatValue = p0 * (1f - v)
            }
            pitch.floatValue = 0f
            startSway(scope) // (Update 274: still again, it goes back to its sway.)
        }
    }

    /**
     * Out it comes: [full] the flight (else a short fade, the carousel stepping back at once); [spin] then turns it once
     * round to show it from every side.
     */
    fun open(scope: CoroutineScope, full: Boolean, spin: Boolean) {
        if (started) return
        started = true
        openJob = scope.launch {
            if (!full) {
                lift.floatValue = 1f
                MediaInspect.amount.floatValue = 1f
                fade.floatValue = 0f
                animate(0f, 1f, animationSpec = tween(160)) { v, _ -> fade.floatValue = v }
                startSway(scope)
                return@launch
            }
            animate(0f, 1f, animationSpec = tween(600, easing = CubicBezierEasing(0.3f, 0f, 0.12f, 1f))) { v, _ ->
                lift.floatValue = v
                MediaInspect.amount.floatValue = v
            }
            // (Update 274: Wobble skips the turn once round — it just sways.)
            if (spin && !closing && !handsOff) spinJob = scope.launch { showcase(); startSway(scope) }
            else startSway(scope)
        }
    }

    /** The slow turn once round (a 3D model: to one side, the other, and back), ending facing you. */
    private suspend fun showcase() {
        delay(160)
        val ease = CubicBezierEasing(0.45f, 0f, 0.4f, 1f)
        if (limited) {
            animate(0f, 1f, animationSpec = tween(3600, easing = ease)) { v, _ -> turn.floatValue = 60f * sin(2f * PI.toFloat() * v) }
            turn.floatValue = 0f
        } else {
            animate(0f, 360f, animationSpec = tween(3600, easing = ease)) { v, _ -> turn.floatValue = v }
            // (A full turn is facing you again; counted from 0 so letting go eases the short way.)
            turn.floatValue = 0f
        }
    }

    /** Back it goes: turns to face you, shrinks and flies to its place ([full]; else a short fade). Ends with amount 0. */
    suspend fun close(full: Boolean) {
        stopSway()
        closing = true
        openJob?.cancel(); spinJob?.cancel(); easeJob?.cancel(); caseJob?.cancel()
        // (Update 189: an opened case goes back together first — quickly.)
        if (out.floatValue > 0f || lid.floatValue > 0f) {
            if (full) {
                if (out.floatValue > 0f) animate(out.floatValue, 0f, animationSpec = tween(260)) { v, _ -> out.floatValue = v }
                if (lid.floatValue > 0f) animate(lid.floatValue, 0f, animationSpec = tween(300)) { v, _ -> lid.floatValue = v }
            }
            out.floatValue = 0f; lid.floatValue = 0f; opened = false
        }
        if (!started) { MediaInspect.amount.floatValue = 0f; return }
        if (!full) {
            animate(fade.floatValue, 0f, animationSpec = tween(140)) { v, _ -> fade.floatValue = v }
        } else coroutineScope {
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            // (Both in the same frame: no jump.)
            turn.floatValue = total
            manual.floatValue = 0f
            launch { animate(total, front, animationSpec = tween(420, easing = FastOutSlowInEasing)) { v, _ -> turn.floatValue = v } }
            launch { levelPitch(420) } // (Update 238, #117: upright again on its way back.)
            animate(lift.floatValue, 0f, animationSpec = tween(520, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) { v, _ ->
                lift.floatValue = v
                MediaInspect.amount.floatValue = v
            }
        }
        MediaInspect.amount.floatValue = 0f
    }
}

/**
 * Update 162: the right stick turns the piece (left / right); let go and it eases back. Set on the game window's own
 * view (the stick goes to the focused window); asleep while the stick rests.
 */
@Composable
internal fun InspectStick(motion: InspectMotion) {
    val view = LocalView.current
    val resetScope = rememberCoroutineScope() // (Update 238, #117: Y's reset)
    DisposableEffect(view, motion) {
        view.setOnGenericMotionListener { _, ev ->
            if (ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && ev.action == MotionEvent.ACTION_MOVE) {
                val rx = ev.getAxisValue(MotionEvent.AXIS_Z)
                motion.stick.floatValue = if (abs(rx) > 0.25f) rx else 0f
                // (Update 238, #117: its up / down tilts it.)
                val ry = ev.getAxisValue(MotionEvent.AXIS_RZ)
                motion.stickY.floatValue = if (abs(ry) > 0.25f) ry else 0f
            }
            // (Update 162 (review): never taken — the same event carries the left stick, which Android turns into D-pad
            // presses for the window's buttons only when nobody handles it.)
            false
        }
        onDispose { view.setOnGenericMotionListener(null) }
    }
    // Update 201: the stick click and the right stick, taken where the game window's own window gets them, before any
    // of its parts. Both went only to the part that has the focus — and right after the window opened, or after a finger
    // turned the piece, nothing in it had: the first click was lost (the case opened, took out or closed only on the
    // second click) and the stick didn't turn it.
    DisposableEffect(view, motion) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val original = window?.callback
        if (window == null || original == null) return@DisposableEffect onDispose { }
        val wrapped = object : android.view.Window.Callback by original {
            override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                val code = event.keyCode
                val hook = MediaInspect.stickClick
                if (hook != null && (code == android.view.KeyEvent.KEYCODE_BUTTON_THUMBL || code == android.view.KeyEvent.KEYCODE_BUTTON_THUMBR)) {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) hook()
                    return true
                }
                // Update 238 (#117): Y puts the piece back as it first stood (facing you, upright) — only while it's out
                // and nothing in the window takes Y (the stick clicks stay the case's / the turn's).
                if (code == android.view.KeyEvent.KEYCODE_BUTTON_Y && motion.started && !motion.closing && motion.lift.floatValue >= 0.999f) {
                    if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) motion.resetPose(resetScope)
                    return true
                }
                return original.dispatchKeyEvent(event)
            }
            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
                    val rx = event.getAxisValue(MotionEvent.AXIS_Z)
                    motion.stick.floatValue = if (abs(rx) > 0.25f) rx else 0f
                    val ry = event.getAxisValue(MotionEvent.AXIS_RZ) // (Update 238, #117: the tilt)
                    motion.stickY.floatValue = if (abs(ry) > 0.25f) ry else 0f
                }
                // (Never taken here either: the left stick still moves through the window's buttons.)
                return original.dispatchGenericMotionEvent(event)
            }
        }
        window.callback = wrapped
        onDispose { if (window.callback === wrapped) window.callback = original }
    }
    LaunchedEffect(motion) {
        // (Update 162 (review): ends once it's closing — held stick and closing made this loop spin without ever waiting.)
        // (Update 238, #117: the stick's up / down tilts it too — over its top, under its bottom.)
        val held = { abs(motion.stick.floatValue) > 0.25f || abs(motion.stickY.floatValue) > 0.25f }
        while (!motion.closing) {
            snapshotFlow { held() }.first { it }
            if (motion.closing) break
            var last = -1L
            // (Update 198: with the disc / cartridge out of its case, the stick turns that — it used to flip the case
            // aside behind it.)
            val content = motion.out.floatValue > 0.5f
            while (held() && !motion.closing) {
                val now = withFrameNanos { it }
                if (last > 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    val deg = motion.stick.floatValue * 200f * dt
                    if (content) motion.nudgeContent(deg)
                    else {
                        if (deg != 0f) motion.nudge(deg * motion.yawSign())
                        val tilt = motion.stickY.floatValue * 160f * dt
                        if (tilt != 0f) motion.nudgePitch(tilt)
                    }
                }
                last = now
            }
            if (content) motion.releaseContent(this) else motion.release(this)
        }
    }
}

/**
 * Update 162: the piece itself, drawn over the game window: it flies between its card ([InspectMotion.from], read
 * again on the way back) and its place [slot] (window px) beside the window's parts. Fills the window (no taps of its
 * own outside the piece).
 */
@Composable
internal fun MediaInspectPiece(piece: InspectPiece, motion: InspectMotion, slot: Rect?, displayId: Int) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Update 292: Inspect is on screen (for the freeze snapshot of a crash report).
    DisposableEffect(displayId) {
        com.shelf.launcher.data.crash.FreezeWatchdog.state("inspectOnDisplay$displayId", "since " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        onDispose { com.shelf.launcher.data.crash.FreezeWatchdog.state("inspectOnDisplay$displayId", null) }
    }
    // Where this overlay sits in the window (the places are in window px).
    val win = remember { FloatArray(2) }
    val yawOf: () -> Float = remember(motion) { { motion.yaw() } }
    // Update 189: the opening case (CaseOpen.kt): its lid, what lies in it, and whether that's still in it.
    val openOf: () -> Float = remember(motion) { { motion.lid.floatValue } }
    val itemInCase: () -> Boolean = remember(motion) { { motion.out.floatValue <= 0.001f } }
    val caseItem = remember(piece) { caseItemOf(piece.content) }
    // Update 238 (#117): its tilt, read while drawing; a case tilts only over its top (InspectMotion.boxPitch).
    val pitchOf: () -> Float = remember(motion) { { motion.pitch.floatValue } }
    SideEffect { motion.boxPitch = piece.kind == InspectKind.BOX }
    // (Update 274, testing only: held at the angle asked for, once it has landed.)
    LaunchedEffect(piece.game.stableKey) {
        val tp = MediaInspect.testPose ?: return@LaunchedEffect
        snapshotFlow { motion.lift.floatValue >= 0.999f }.first { it }
        delay(250)
        motion.holdAt(tp[0], tp.getOrElse(1) { 0f })
    }
    // Update 198: Settings › Game look › "Open cases side by side": once it has landed, it opens by itself.
    val startOpen = Features.inspectOpenAtStart.state.value
    LaunchedEffect(piece.game.stableKey, startOpen) {
        if (startOpen && piece.kind == InspectKind.BOX && piece.content != null) {
            snapshotFlow { motion.lift.floatValue >= 0.999f }.first { it }
            delay(200)
            if (!motion.opened && !motion.closing) motion.toggleCase(scope, true, true)
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { c -> val o = c.positionInWindow(); win[0] = o.x; win[1] = o.y }) {
        if (slot != null) {
            // Update 186: a box at its scan's own shape (CoverAspects, noted when the card drew it), so its front isn't cut.
            // (Update 191: not when the real case's shape is known — CaseCatalog.kt.)
            val scanShape = if (piece.kind == InspectKind.BOX && piece.game.platform != "PS1" && CaseCatalog.forGame(piece.game) == null)
                CoverAspects.rawOf(piece.front?.toString())?.coerceIn(piece.aspect * 0.75f, piece.aspect * 1.33f) else null
            val aspect = (scanShape ?: piece.aspect).coerceIn(0.25f, 4f)
            // As big as fits its side of the window (some room kept for its turn and its shadow).
            // (Update 186: always as big as fits — the real-size scaling made a PlayStation or Game Boy case small here,
            // where it stands alone.)
            // (Update 188: bigger still — it stood small in its space on the AYN Thor.)
            // (Update 199: a case that opens keeps room under it for "Tap the case…" — a tall GameCube case pushed it off
            // the bottom of the screen.)
            val hintRoom = if (piece.kind == InspectKind.BOX && piece.content != null) with(density) { 40.dp.toPx() } else 0f
            val roomH = (slot.height - hintRoom).coerceAtLeast(slot.height * 0.6f)
            val itemPlace = remember(piece, aspect) { if (piece.kind == InspectKind.BOX) openItemPlace(piece, aspect) else null }
            // Update 202: the manual's booklet in the open lid (a game with a manual; tap it to read).
            val hasManual = rememberHasManual(piece.game)
            val bookPlace = remember(piece, aspect, hasManual) { if (hasManual && piece.kind == InspectKind.BOX) openBookletPlace(piece, aspect) else null }
            val w = minOf(slot.width * 0.98f, roomH * 0.95f * aspect).coerceAtLeast(8f)
            val h = w / aspect
            val left = slot.left + (slot.width - w) / 2f
            val top = slot.top + (roomH - h) / 2f
            val key = piece.game.stableKey
            Box(
                Modifier
                    .offset { IntOffset((left - win[0]).roundToInt(), (top - win[1]).roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .graphicsLayer {
                        val k = motion.lift.floatValue
                        alpha = motion.fade.floatValue
                        // (Update 162 (review): not drawn over its card before it comes out — no frame of both at once.)
                        if (!motion.started) alpha = 0f
                        else if (k < 1f) {
                            // On the way back its card's place is read again (the carousel may have settled meanwhile).
                            val f = (if (motion.closing) inspectOrigin(key, displayId) else null) ?: motion.from
                            if (f != null && f.width > 1f && f.top != Float.MAX_VALUE) {
                                // Its size on the card (fitted in its slot as the card draws it), growing to its size here,
                                // a little bigger mid-way as it comes forward, lifted a little on its way.
                                // (Update 330: from a spine — the case turned side-on — at the spine's height.)
                                val sw = if (motion.fromSpine) f.height * aspect else minOf(f.width * 0.94f, f.height * 0.94f * aspect)
                                val s0 = (sw / w).coerceIn(0.05f, 4f)
                                val pop = sin(PI.toFloat() * k)
                                val s = (s0 + (1f - s0) * k) * (1f + 0.06f * pop)
                                scaleX = s; scaleY = s
                                translationX = (f.center.x - (left + w / 2f)) * (1f - k)
                                translationY = (f.center.y - (top + h / 2f)) * (1f - k) - (if (motion.fromSpine) 0f else h * 0.07f * pop)
                            } else {
                                // No card to come from: it grows in where it stands.
                                val s = 0.6f + 0.4f * k
                                scaleX = s; scaleY = s
                                alpha = motion.fade.floatValue * k
                            }
                        }
                    }
                    // Update 189: an opened case moves aside (and shrinks) while what's in it comes out.
                    .graphicsLayer {
                        val o = motion.out.floatValue
                        if (o > 0f) {
                            val s = 1f - CASE_ASIDE_SHRINK * o
                            scaleX = s; scaleY = s
                            // (Update 189 (Thor): not so far — it went off the screen's left edge. Update 198: to the left
                            // half, beside what came out.)
                            translationX = -w * CASE_ASIDE_SHIFT * o
                            translationY = -h * 0.06f * o
                        }
                    }
                    // Update 189: a tap on a case opens it (and closes it again).
                    .pointerInput(piece.kind, piece.content, itemPlace, bookPlace) {
                        // (Update 238, #117: a long press puts it back as it first stood — facing you, upright.)
                        detectTapGestures(onLongPress = { motion.resetPose(scope) }, onTap = { at ->
                            inspectTap(piece, motion, scope, itemPlace, bookPlace, at, size.width.toFloat(), size.height.toFloat())
                        })
                    }
                    // A horizontal drag turns it yourself; let go and it eases back.
                    // (Update 238, #116/#117: a drag up / down tilts it over its top or under its bottom; let go and it
                    // stays a while, then turns back slowly — InspectMotion.release.)
                    .pointerInput(piece.kind) {
                        // (0.6.18: how fast your finger went as you let go — a flick keeps it turning, slowing to a stop.)
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        // (Update 319, Toni: "touching the case doesn't open it — it's realised after some time": a finger
                        // that moved a few pixels made the touch a drag, and the tap was lost. A short touch that hardly
                        // moved counts as the tap.)
                        var downAt = androidx.compose.ui.geometry.Offset.Zero
                        var moved = 0f
                        var downTime = 0L
                        detectDragGestures(
                            onDragStart = { at -> tracker.resetTracking(); downAt = at; moved = 0f; downTime = android.os.SystemClock.uptimeMillis() },
                            onDragEnd = {
                                if (moved < 28.dp.toPx() && android.os.SystemClock.uptimeMillis() - downTime < 400L) {
                                    motion.release(scope)
                                    inspectTap(piece, motion, scope, itemPlace, bookPlace, downAt, size.width.toFloat(), size.height.toFloat())
                                } else {
                                    val v = tracker.calculateVelocity()
                                    motion.fling(scope, v.x * 0.42f * motion.yawSign(), v.y * 0.42f)
                                }
                            },
                            onDragCancel = { motion.release(scope) }
                        ) { change, drag ->
                            change.consume()
                            moved += drag.getDistance()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!motion.closing) {
                                if (drag.x != 0f) motion.nudge(drag.x * 0.42f * motion.yawSign())
                                if (drag.y != 0f) motion.nudgePitch(drag.y * 0.42f)
                            }
                        }
                    }
            ) {
                InspectShadow(motion)
                when (piece.kind) {
                    InspectKind.BOX -> InspectCase(piece, aspect, motion, yawOf, openOf, caseItem, itemInCase)
                    InspectKind.SLAB -> InspectSlab(piece, yawOf, pitchOf)
                    InspectKind.DISC -> InspectDisc(piece, yawOf, pitchOf)
                    // (Update 200: no MODEL any more — InspectModel's pre-rendered pictures are retired.)
                }
            }
            // Update 189: what was in the case, out in the middle (CaseInside places where it lay).
            val content = piece.content
            if (content != null && piece.kind == InspectKind.BOX) InspectContent(piece, content, motion, left - win[0], top - win[1], w, h, aspect, scope)
            // Update 189: how to open it, said once it's out.
            if (piece.kind == InspectKind.BOX && piece.content != null) OpenHint(
                motion, left - win[0], top + h - win[1], w,
                // (Update 260: a PSP game's is a UMD, not a cartridge.)
                when (piece.content?.kind) { InspectKind.DISC -> "disc"; else -> if (piece.content?.game?.platform?.let { MediaShell.of(it) } == MediaShell.UMD) "UMD" else if (piece.content?.game?.platform in setOf("NDS", "3DS", "Switch", "PSVita")) "card" else "cartridge" },
                manual = bookPlace != null
            )
        }
    }
}

/**
 * Update 330: [g]'s case as the game window will show it (the case it's lifted out of the Cardboard box as), drawn unseen
 * where it stands, so its front, back and spine pictures are ready when it comes out — it flew out as a plain coloured
 * slab, then showed its whole box scan for a moment, while they loaded.
 */
@Composable
internal fun PrewarmInspectCase(g: Game, state: LauncherUiState, modifier: Modifier) {
    val k = g.stableKey
    val piece = remember(k, state.physicalMedia[k], state.artOverrides[k], state.art[k]) {
        runCatching { forcedPieceOf(g, state) }.getOrNull()?.also {
            if (it.kind == InspectKind.BOX && it.back == null && Features.printedBacks.state.value) it.back = printedBackFor(g, state, it.aspect)
        }
    } ?: return
    if (piece.kind != InspectKind.BOX) return
    val realScan = Features.originalCases.state.value && !LookStyles.caseForAll() && !androidOwnCase(g.platform) &&
        piece.front != null && piece.front == BoxTexture.front(piece.physical, g.platform)
    Box(modifier.graphicsLayer { alpha = 0.004f }) {
        Case3D(g, piece.front, piece.physical, 0f, piece.aspect, Modifier.fillMaxSize(), pitch = CASE_PITCH, realFront = realScan, back = piece.back, fill = 0.97f)
    }
}

/**
 * Update 238 (#117): the inspected case, tilted by [InspectMotion.pitch] on top of its own slight tilt. Its own
 * composable, so only it follows the tilt (Case3D takes its pitch as a plain value). Case3D (CaseDraw) culls each face
 * by its winding, so the front, spine, edge, top and back show correctly at any tilt from 0° to 176°; it has no bottom
 * face, so the tilt never goes under it (BOX_PITCH_MIN / MAX). (Update 240, #117: it has its bottom now — the whole way
 * round, −180° … 180°.)
 */
@Composable
private fun InspectCase(
    piece: InspectPiece, aspect: Float, motion: InspectMotion, yawOf: () -> Float, openOf: () -> Float,
    caseItem: CaseItem?, itemInCase: () -> Boolean
) {
    val tilt = motion.pitch.floatValue.coerceIn(BOX_PITCH_MIN, BOX_PITCH_MAX)
    // Update 311: InspectPiece.front has already selected the actual Box scan when
    // Original cases is on. Tell Case3D that it is a real, fully printed front,
    // just as the shelf's ScanCase does. Otherwise Case3D sees realFront=false
    // and can paste a second console template on top of it (N64 corner/logo).
    val realScan = Features.originalCases.state.value &&
        !LookStyles.caseForAll() &&
        !androidOwnCase(piece.game.platform) &&
        piece.front != null &&
        piece.front == BoxTexture.front(piece.physical, piece.game.platform)
    Case3D(
        piece.game, piece.front, piece.physical, 0f, aspect, Modifier.fillMaxSize(),
        pitch = CASE_PITCH + tilt,
        realFront = realScan,
        // (Update 162: Case3D's back face and its angle read while drawing.)
        back = piece.back, yawOf = yawOf, fill = 0.97f,
        // (Update 189: it opens.)
        openOf = openOf, inside = caseItem, showItemOf = itemInCase,
        onFront = { motion.artReady = true }
    )
}

/**
 * Update 189: the disc / cartridge out of its case: it rises from where it lay in the open case (or out of a box's top)
 * to the middle of the piece's side, big, and turns gently (a disc spins slowly); a tap puts it back and closes the case.
 */
@Composable
private fun InspectContent(
    piece: InspectPiece, content: InspectPiece, motion: InspectMotion, left: Float, top: Float, w: Float, h: Float, caseAspect: Float,
    scope: CoroutineScope
) {
    val density = LocalDensity.current
    val disc = content.kind == InspectKind.DISC
    val cAspect = if (disc) 1f else content.aspect.coerceIn(0.3f, 3f)
    // (Update 198: side by side with the case — the case on the left, this on the right, no longer over it.)
    val cw = minOf(w * 0.56f, h * 0.84f * cAspect)
    val ch = cw / cAspect
    val endCx = left + w * 0.72f
    val endCy = top + h / 2f
    // (Update 191: the real case — what kind of inside, and where in it — as Case3D draws it; what lies in it decides
    // disc or card, so the disc in the case is the disc that comes out.)
    val spec = remember(piece.game.stableKey, Features.caseRegion.state.value) { CaseCatalog.forGame(piece.game) }
    val kind = CaseInside.kindFor(spec, piece.game.platform, disc)
    // Where it lay: in the open book (the lid left of the hinge, the tray right of it), as Case3D draws it.
    val fw = w * 0.97f; val fh = h * 0.97f
    val k = 1f / 1.92f
    val w2 = fw * k; val h2 = fh * k
    val r = CaseInside.itemRect(kind, caseAspect, cAspect, spec)
    // (Xbox One / Series: in the lid, left of the hinge; its picture runs from the lid's outer edge to the hinge.)
    val onLid = spec?.mediaLeft == true
    val startCx = if (kind == CaseInside.Kind.BOX) left + w / 2f
        else if (onLid) left + w / 2f - w2 + ((r[0] + r[2]) / 2f) * w2
        else left + w / 2f + ((r[0] + r[2]) / 2f) * w2
    // (Update 191: a box's cartridge comes up out of its risen tray.)
    val startCy = if (kind == CaseInside.Kind.BOX) top - h * 0.1f else top + h / 2f - h2 / 2f + ((r[1] + r[3]) / 2f) * h2
    val startW = if (kind == CaseInside.Kind.BOX) cw * 0.5f else (r[2] - r[0]) * w2
    val s0 = (startW / cw).coerceIn(0.1f, 1f)
    val outNow by remember(motion) { androidx.compose.runtime.derivedStateOf { motion.out.floatValue > 0.001f } }
    // (Its slow turn only while it's out.)
    val spin = if (outNow) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "contentSpin")
        t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(12_000, easing = androidx.compose.animation.core.LinearEasing)), label = "contentT")
    } else null
    // A gentle tilt to and fro (a disc shows its edge and, turned by hand, its data side), plus your own turn.
    val yawOf: () -> Float = { (if (disc) 22f else 16f) * sin(2f * PI.toFloat() * (spin?.value ?: 0f) * 2f) + motion.contentTurn.floatValue }
    Box(
        Modifier
            .offset { IntOffset((endCx - cw / 2f).roundToInt(), (endCy - ch / 2f).roundToInt()) }
            .size(with(density) { cw.toDp() }, with(density) { ch.toDp() })
            .graphicsLayer {
                val o = motion.out.floatValue
                alpha = if (o <= 0.001f) 0f else (o * 5f).coerceAtMost(1f) * motion.fade.floatValue
                val e = o
                val sc = s0 + (1f - s0) * e
                scaleX = sc; scaleY = sc
                // A little lift on its way (up and out of the case).
                val lift = sin(PI.toFloat() * e) * h * 0.08f
                translationX = (startCx - endCx) * (1f - e)
                translationY = (startCy - endCy) * (1f - e) - lift
                if (disc) rotationZ = 360f * (spin?.value ?: 0f)
            }
            // (Update 200: only while it's out. Unseen in the case it stood over the case's right half and took the taps
            // meant for the case — a finger on the case didn't open it.)
            .then(if (!outNow) Modifier else Modifier
                .pointerInput(Unit) {
                    // (Update 200: a tap lays it back in the open case; a tap on the case then shuts it.)
                    detectTapGestures(onTap = { if (motion.out.floatValue > 0.5f) motion.putBack(scope) })
                }
                // A drag turns it round (a disc: over to its data side); up / down tilts it over (update 324); let go and it
                // comes back.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { motion.releaseContent(scope) },
                        onDragCancel = { motion.releaseContent(scope) }
                    ) { change, drag ->
                        change.consume()
                        if (motion.out.floatValue > 0.5f) {
                            if (drag.x != 0f) motion.nudgeContent(drag.x * 0.5f)
                            if (drag.y != 0f) motion.nudgeContentPitch(drag.y * 0.5f)
                        }
                    }
                })
    ) {
        val pitchOf: () -> Float = { motion.contentPitch.floatValue }
        if (disc) InspectDisc(content, yawOf, pitchOf) else InspectSlab(content, yawOf, pitchOf)
    }
}

/**
 * Update 189: "Tap the case to open it", under the case for a few seconds once it's out. Update 200: then, the first time
 * it's opened, "Tap the disc to take it out" (cartridge / card) for a few seconds.
 */
@Composable
private fun OpenHint(motion: InspectMotion, left: Float, bottom: Float, w: Float, what: String, manual: Boolean = false) {
    val density = LocalDensity.current
    val shown = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    var second by androidx.compose.runtime.remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { motion.lift.floatValue >= 0.999f }.first { it }
        shown.animateTo(1f, tween(300))
        // (Until it's opened or for a few seconds.)
        kotlinx.coroutines.withTimeoutOrNull(3_500) { snapshotFlow { motion.lid.floatValue > 0.05f }.first { it } }
        shown.animateTo(0f, tween(300))
        snapshotFlow { motion.lid.floatValue >= 0.99f && motion.out.floatValue < 0.01f }.first { it }
        second = true
        shown.animateTo(1f, tween(300))
        kotlinx.coroutines.withTimeoutOrNull(3_500) { snapshotFlow { motion.out.floatValue > 0.05f || motion.lid.floatValue < 0.9f }.first { it } }
        shown.animateTo(0f, tween(400))
    }
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), (bottom + with(density) { 6.dp.toPx() }).roundToInt()) }
            .size(with(density) { w.toDp() }, 28.dp)
            .graphicsLayer {
                alpha = shown.value * (1f - motion.out.floatValue) * (if (second) 1f else (1f - motion.lid.floatValue))
                // (Update 201: faded without a layer of its own, which would cut it to the case's width.)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            // (Update 202: with a manual in the lid, how to read it too.)
            if (second && manual) "Tap the $what to take it out · the manual to read it"
            else if (second) "Tap the $what (or click a stick) to take it out" else "Tap the case (or click a stick) to open it",
            color = Color.White, fontSize = 12.sp, maxLines = 1,
            // Update 201: as wide as it needs, centred under the case — it was cut to the case's width, so under a narrow
            // case "Tap the cartridge (or click a stick) to take it out" lost its end ("… to").
            modifier = Modifier.wrapContentWidth(unbounded = true)
                .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Its soft shadow on the floor under it, there once it's out. */
@Composable
private fun BoxScope.InspectShadow(motion: InspectMotion) {
    Spacer(
        Modifier.matchParentSize().drawWithCache {
            val r = size.width * 0.56f
            val c = Offset(size.width / 2f, size.height * 1.03f)
            val brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), center = c, radius = r)
            onDrawBehind {
                val k = motion.lift.floatValue * motion.fade.floatValue
                if (k > 0.02f) scale(1f, 0.16f, pivot = c) { drawCircle(brush, radius = r, center = c, alpha = k) }
            }
        }
    )
}

/** The piece as the card draws it, standing still (no idle hop or spin: it's turned here). */
@Composable
private fun PieceFace(p: InspectPiece, modifier: Modifier) {
    CompositionLocalProvider(LocalStillPart provides true) {
        val pick = p.pick
        val real = p.realScan
        if (real != null) RealMediaPicture(real, p.kind == InspectKind.DISC, modifier)
        else if (pick != null) {
            val own = isCustomPiece(pick.platform)
            val tpl = if (own) null else MediaTemplates.of(pick.platform)
            // A disc that the card shows in its case: here the disc alone.
            if (tpl?.disc != null && discCaseOn(tpl, pick.platform)) TemplateMedia(
                MediaTpl(null, null, tpl.disc, null), labelScan = if (p.labelIsScan) p.label else null,
                cover = if (p.labelIsScan) null else p.label, modifier = modifier, smallDisc = p.smallDisc
            )
            else PhysicalMedia(pick.shell, p.label, modifier, platform = pick.platform, labelIsScan = p.labelIsScan, caseCover = p.cover, title = p.game.name)
        }
    }
}

/**
 * Turned by [yawOf]: the face squeezed as it turns and shaded, moved as a slab of [depth] would. (Update 238, #117: and
 * tilted by [pitchOf] — drawn through [pieceMatrix] instead of the layer's own scale, which can't tilt.)
 */
private fun Modifier.slabFace(yawOf: () -> Float, depth: Float, pitchOf: () -> Float = { 0f }): Modifier =
    this.graphicsLayer {
        val pose = PiecePose(yawOf(), pitchOf())
        val d = depth * maxOf(size.width, size.height)
        // The front is in front of the middle, the back behind it. (Moved as a layer, so its offscreen picture — the
        // face's own size — never cuts it.)
        val z = if (pose.facing >= 0f) d / 2f else -d / 2f
        translationX = z * pose.sy
        translationY = z * pose.cy * pose.sp
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val m = androidx.compose.ui.graphics.Matrix()
        // (Update 294: the face drawn flat into its own picture, and only that squeezed / tilted — its text drawn at a new
        // scale every frame filled Android 13's text cache until the Thor stopped answering.)
        val flat = obtainGraphicsLayer()
        flat.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        onDrawWithContent {
            flat.record { this@onDrawWithContent.drawContent() }
            // (Squeezed and tilted round its middle: a round face stays inside its own box at any angle.)
            PiecePose(yawOf(), pitchOf()).matrix(m, center, 0f)
            withTransform({ transform(m) }) { drawLayer(flat) }
        }
    }

/**
 * Update 238 (#117): a piece turned [yaw]° round its upright, then tilted [pitch]° round the screen's level (positive:
 * its top comes towards you) — the same order as Case3D's CaseDraw, seen straight on (no perspective, as the cartridges
 * and discs always were). A point (x, y) of its face at depth z (towards you) lands at
 * (x·cos yaw + z·sin yaw, y·cos pitch − x·sin yaw·sin pitch + z·cos yaw·sin pitch), and faces you by cos yaw·cos pitch.
 * At pitch 0 it's exactly the old turn (squeezed by cos yaw, moved by z·sin yaw).
 */
private class PiecePose(yaw: Float, pitch: Float) {
    val cy: Float; val sy: Float; val cp: Float; val sp: Float
    init {
        val ty = Math.toRadians(yaw.toDouble()); val tp = Math.toRadians(pitch.toDouble())
        cy = cos(ty).toFloat(); sy = sin(ty).toFloat(); cp = cos(tp).toFloat(); sp = sin(tp).toFloat()
    }
    /** How much the front faces you (−1 its back, full on). */
    val facing: Float get() = cy * cp
    /** How much of its thickness shows (0 face on … 1 edge on); at pitch 0 it's |sin yaw|. */
    val spread: Float get() = kotlin.math.sqrt((1f - facing * facing).coerceAtLeast(0f))
    /** Sets [m] to draw its face (laid out round [pivot]) at depth [z]. */
    fun matrix(m: androidx.compose.ui.graphics.Matrix, pivot: Offset, z: Float) {
        // (Never quite 0 wide / tall: a face squeezed to nothing drops out — the slices of its sides go with it.)
        val a = if (abs(cy) < 0.01f) (if (cy < 0f) -0.01f else 0.01f) else cy
        val dd = if (abs(cp) < 0.01f) (if (cp < 0f) -0.01f else 0.01f) else cp
        val c = -sy * sp
        m.reset()
        val v = m.values
        v[0] = a; v[1] = c; v[4] = 0f; v[5] = dd
        v[12] = pivot.x - a * pivot.x + z * sy
        v[13] = pivot.y - c * pivot.x - dd * pivot.y + z * cy * sp
    }
}

/**
 * A cartridge / card turning (update 189: a real solid piece). Its face is recorded once and drawn again, darkened,
 * slice after slice through its thickness — so its sides have its own outline and its own colour (a green shell's
 * edge is green, a notched cartridge's edge follows the notch) — then the face in front; from behind, its outline
 * filled with its shell's colour (taken from the picture itself), never the label mirrored. It used to be the face
 * squeezed beside a grey strip that floated apart from it.
 */
@Composable
private fun InspectSlab(p: InspectPiece, yawOf: () -> Float, pitchOf: () -> Float = { 0f }) {
    val holder = remember { arrayOfNulls<androidx.compose.ui.graphics.layer.GraphicsLayer>(1) }
    // Update 195: a library shell's own back (seam, screws, contacts), shown when it's turned round.
    // (Update 200: its researched back; plain — no maker's markings — on another console's shell, or as set.)
    val backStyle = Features.cartBackStyle.state.value
    val shellBack = remember(p.pick?.platform, p.realScan, backStyle) {
        // Update 204: the game's own cartridge photo turned round shows its console's real shell's researched back (it
        // was a plain silhouette, so the new backs never showed for the many games with a photo).
        if (p.pick == null && p.realScan != null) ownShellOf(p.game.platform, p.game.stableKey)?.let { sh ->
            // (Update 207: in the photo's own plastic colour when it's been read.)
            val own = ownShellColour(sh, p.game.platform, p.game.stableKey) // (Update 210: its edition's plastic)
            // (Update 228, #36: keyed by the colour — one "photo" key for every photo's back in the cache.)
            val colour = PhotoTones.of(p.realScan)?.let { t -> t.copy(alpha = 1f).toArgb().let { c -> ShellColour("photo:$c", own.name, c, own.opacity) } } ?: own
            runCatching { CartShells.back(sh, colour, 640, backStyle == Features.CartBackStyle.NEUTRAL).asImageBitmap() }.getOrNull()
        }
        else p.pick?.platform?.takeIf { it.startsWith(SHELL_PREFIX) }?.let { pp ->
            val (ch, console) = CartShells.split(pp)
            // (Update 306: on another console's shell, that game's console named where the maker's name is — backFor.)
            ch?.shell?.let { sh ->
                runCatching { CartShells.backFor(sh, CartShells.colourOf(sh, ch.colourKey), 640, console, backStyle).asImageBitmap() }.getOrNull()
            }
        }
    }
    // Update 228 (#36): a custom piece's own plastic from what was picked (CustomPieceTones) wins over the pixel read
    // below — that read failed while the pack picture was still loading (or on a clear shell's faint edge) and was never
    // tried again, so a custom cartridge turned grey here.
    // (Update 240, #92: a library shell's researched bottom — its insertion edge, connector opening and board — drawn
    // on its bottom plane when it's tilted to show it; shells without researched data keep the plain edge.)
    // (Update 275, Toni: the connector end in real 3D — the opening, its walls, the board, its contacts, ribs and plates
    // (CartUnderside.kt) — for a drawn shell, and for a cartridge photo / template on its console's real shell's numbers
    // (stretched to its own shape; no bottom face of its own drawn — its outline isn't the shell's). Shells without
    // researched data keep the plain edge.)
    // (Update 276, Toni: your pack's cartridges too — each pack shape gets the connector end of the real cartridge it's
    // drawn after, fitted to the shell in the picture (CartUndersides.packFit); the optical-system pack's made-up shells
    // a plain Mega Drive-style edge connector (opticalFit). PS Vita / HuCard shapes: flat contacts, plain.)
    val packConsole = p.pick?.platform?.takeIf { it.startsWith(CUSTOM_CART) }?.removePrefix(CUSTOM_CART)
    val packOptical = packConsole?.let { discShellFor(it) } != null
    val packLookNow = packConsole?.takeIf { !packOptical }?.let { CustomCarts.lookFor(it) }
    val packClear = Features.cartClear.state.value &&
        Features.modelFinish.state.value.let { it == ModelFinish.CLEAR || it == ModelFinish.OFF }
    val under = remember(p.pick?.platform, p.realScan, p.game.platform, packOptical, packLookNow, packClear) {
        runCatching {
            val pp = p.pick?.platform
            fun packPlan(fit: CartUndersides.PackFit?, clear: Boolean): UnderPlan? = fit?.let { f ->
                CartShells.of(f.shellId)?.let { sh -> CartUndersides.geo(sh)?.let { g -> UnderPlan(sh, g, if (clear) 0.82f else 1f, full = false, frame = f.frame) } }
            }
            if (pp != null && pp.startsWith(SHELL_PREFIX)) {
                val ch = CartShells.split(pp).first
                if (ch?.pack != null) packPlan(CartUndersides.packFit(ch.pack), ch.clear)
                else ch?.shell?.let { sh -> CartUndersides.geo(sh)?.let { g -> UnderPlan(sh, g, CartShells.colourOf(sh, ch.colourKey).opacity, full = true) } }
            } else if (pp != null && pp.startsWith(CUSTOM_CART)) {
                if (packOptical) packPlan(CartUndersides.opticalFit, false)
                else packLookNow?.let { packPlan(CartUndersides.packFit(it), packClear) }
            } else if (pp == null || (!isCustomPiece(pp) && pp == p.game.platform)) {
                ownShellOf(p.game.platform, p.game.stableKey)?.let { sh ->
                    // (Only when the picture is close to the real shell's shape — else the opening wouldn't sit on it.)
                    if (abs(p.aspect - sh.aspect) / sh.aspect > 0.15f) null
                    else CartUndersides.geo(sh)?.let { g -> UnderPlan(sh, g, 1f, full = false) }
                }
            } else null
        }.getOrNull()
    }
    val customPlatform = p.pick?.platform?.takeIf { isCustomPiece(it) }
    val saved = if (customPlatform != null) rememberCustomPieceTone(customPlatform) else null
    // (Update 241, #45: your pack's cartridge turned round shows its back with its console's logo moulded in —
    // CustomCartBacks — cut to the cartridge's own outline; it was a plain plate.)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val customBack = remember(customPlatform, saved, backStyle) {
        // (Update 306: a pack shape modelled on a real cartridge shows that cartridge's researched back, in the pack's
        // plastic, with this game's console named on it — CustomCarts.authenticForPick; a pack shape picked in Cartridge
        // shell… too, which had a plain plate.)
        customPlatform?.let { cp -> CustomCarts.authenticForPick(cp) }?.let { (real, console) ->
            val clear = if (customPlatform.startsWith(SHELL_PREFIX)) CartShells.split(customPlatform).first?.clear == true else packClear
            CustomCarts.authenticBack(real, (saved ?: p.tone), clear, console, backStyle, 640)?.asImageBitmap()
        } ?: customPlatform?.removePrefix(CUSTOM_CART)?.takeIf { customPlatform.startsWith(CUSTOM_CART) && CustomCartBacks.applies(it) }?.let { console ->
            runCatching { CustomCartBacks.backImage(ctx, console, (saved ?: p.tone).copy(alpha = 1f).toArgb(), CustomCarts.aspectFor(console)) }.getOrNull()
        }
    }
    var shell by androidx.compose.runtime.remember(p) { androidx.compose.runtime.mutableStateOf<Color?>(null) }
    // (Update 257, #35: a PSP game's UMD — not a cartridge shell picked for it — shows its data side from behind.)
    val isUmd = p.pick?.platform?.startsWith(SHELL_PREFIX) != true && customPlatform == null &&
        MediaShell.of(p.game.platform) == MediaShell.UMD
    // (Its frame in a UMD's own white-grey plastic: the colour read off the front is the label's art, often dark.)
    val umdTone = (saved ?: PhotoTones.of(p.realScan) ?: Color(0xFFE2E4E7)).copy(alpha = 1f)
    val umdBack = remember(isUmd, umdTone) {
        if (isUmd) runCatching { UmdBack.picture(umdTone.toArgbInt()).asImageBitmap() }.getOrNull() else null
    }
    // Its shell colour. Update 285 (the Thor's ANR while inspecting, 0.6.21: the main thread waited on the render thread
    // in GraphicsLayer.toImageBitmap → HardwareRenderer.createHardwareBitmap for over 5 s): no longer read back from the
    // drawn face. It comes from what the face is drawn from — a library shell's own colour, else the template / pack
    // picture's edge read off the main thread (its file, not the screen); a photo's tone comes from PhotoTones.
    val ctxTone = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(p) {
        val pp = p.pick?.platform
        shell = when {
            pp == null -> null
            pp.startsWith(SHELL_PREFIX) -> CartShells.split(pp).first?.let { ch -> ch.shell?.let { sh -> Color(CartShells.colourOf(sh, ch.colourKey).argb) } }
            isCustomPiece(pp) -> null
            else -> MediaTemplates.of(pp)?.cart?.res?.let { res ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching {
                        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                        android.graphics.BitmapFactory.decodeResource(ctxTone.resources, res, o)?.let { b -> sampleShell(b).also { b.recycle() } }
                    }.getOrNull()
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val face = obtainGraphicsLayer()
            // (Update 294: drawn flat into its own picture first, then turned / tilted — Android 13's text cache.)
            face.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
            holder[0] = face
            val edge = obtainGraphicsLayer()
            // (Update 205: the sides are the outline in the plastic's colour, in a layer of their own.)
            edge.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
            val lit = obtainGraphicsLayer()
            val back = obtainGraphicsLayer()
            // Update 201: the back is drawn in a layer of its own. Its tone (SrcIn), sheen and shade (SrcAtop) blend with
            // what's already drawn under them — without its own layer that was the game window's whole surface, so each
            // of them filled the piece's whole box: a grey / see-through plate stood over the back of every cartridge
            // (the N64's most of all, its round top inside a square), wider than the cartridge and with its outline
            // showing through. Now they only touch the back itself.
            back.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
            // Update 201: the face's outline made solid (its alpha ×4, the faintest edge pixels dropped) for a back
            // without a drawn one — a clear shell's face is see-through, so its tone was too, and the label (in the
            // slices behind it) showed through mirrored.
            val solid = obtainGraphicsLayer()
            solid.colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 4f, -SIDE_CUT.toFloat() // (Update 325: SIDE_CUT)
            )))
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.18f)))
            val mx = androidx.compose.ui.graphics.Matrix()
            onDrawWithContent {
                face.record { this@onDrawWithContent.drawContent() }
                // Update 238 (#117): turned and tilted (PiecePose) — over its top and under its bottom the same slices
                // through its thickness make its top / bottom edge (the shell's outline in its plastic colour, a
                // connector edge included), and tilted past upright the back shows the right way up for that angle.
                // [c] says which side faces you, [sn] how much of its thickness shows (both as before at no tilt).
                val pose = PiecePose(yawOf(), pitchOf())
                val c = pose.facing
                val sn = pose.spread
                val w = size.width
                val h = size.height
                val depth = p.depth * maxOf(w, h)
                fun at(z: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
                    pose.matrix(mx, center, z)
                    withTransform({ transform(mx) }) { block() }
                }
                // The sides: the face's outline, darker, lit a little as they face you. Update 205: in the shell's plastic
                // colour — copies of the whole face showed its label in stripes along the side as it turned.
                val sideTone = (saved ?: shell ?: PhotoTones.of(p.realScan) ?: p.tone).copy(alpha = 1f)
                solid.record { drawLayer(face) }
                edge.colorFilter = SlabLight.of(0.42f + 0.22f * abs(sn))
                edge.record {
                    drawLayer(solid)
                    drawRect(sideTone, blendMode = BlendMode.SrcIn)
                    drawRect(sheen, blendMode = BlendMode.SrcAtop)
                }
                val n = kotlin.math.ceil(depth * abs(sn) / 1.2f).toInt().coerceIn(0, 48)
                if (n > 0) {
                    // Far to near.
                    for (k in 0..n) {
                        val f = k.toFloat() / n
                        val z = if (c >= 0f) -depth / 2f + depth * f else depth / 2f - depth * f
                        at(z) { drawLayer(edge) }
                    }
                }
                // (Update 275: the bottom face round the openings — a drawn shell's own.)
                val uv = under?.let { u -> CartUndersides.View(u.shell, w, h, depth, pose.cy, pose.sy, pose.cp, pose.sp, stretch = !u.full, frame = u.frame) }
                val plasticArgb = sideTone.toArgb()
                if (under != null && uv != null && under.full) CartUndersides.drawRim(this, under.geo, uv, plasticArgb, under.opacity)
                if (c >= 0f) {
                    lit.colorFilter = SlabLight.of(1f - 0.4f * abs(sn))
                    lit.record { drawLayer(face) }
                    at(depth / 2f) { drawLayer(lit) }
                } else {
                    // (Update 201: opaque — a clear shell's sampled colour is see-through.)
                    val tone = (saved ?: shell ?: PhotoTones.of(p.realScan) ?: p.tone).copy(alpha = 1f)
                    back.record {
                        val cb = customBack
                        val sb = shellBack
                        if (cb != null) {
                            drawLayer(solid)
                            drawImage(cb, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), blendMode = BlendMode.SrcIn)
                        } else if (sb != null && p.pick == null && p.realScan != null) {
                            // (Update 207: a cartridge photo's back over the whole piece, cut to the photo's own outline —
                            // it was the drawn shell's back at the drawn shell's shape, another cartridge from behind.)
                            drawLayer(solid)
                            drawImage(sb, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), blendMode = BlendMode.SrcIn)
                        } else if (sb != null) {
                            // (Update 195: the shell's drawn back, fitted as its front is.)
                            val a = sb.width.toFloat() / sb.height.coerceAtLeast(1)
                            val bw = minOf(size.width, size.height * a); val bh = bw / a
                            drawImage(sb, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - bw) / 2f).toInt(), ((size.height - bh) / 2f).toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(bw.toInt(), bh.toInt()))
                        } else if (umdBack != null) {
                            // (Update 257, #35: a UMD's data side seen through its clear back — UmdBack.kt.)
                            drawLayer(solid)
                            drawImage(umdBack, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()), blendMode = BlendMode.SrcIn)
                        } else {
                            drawLayer(solid)
                            drawRect(tone, blendMode = BlendMode.SrcIn)
                        }
                        drawRect(sheen, blendMode = BlendMode.SrcAtop)
                        drawRect(Color.Black, alpha = 0.4f * abs(sn), blendMode = BlendMode.SrcAtop)
                    }
                    at(-depth / 2f) { drawLayer(back) }
                }
                // (Update 275: what's seen through the openings, over the faces — clipped to each opening.)
                // (Update 296, Toni: "floating objects round some cartridges" — seen on Astérix & Obélix XXL's GBA
                // cartridge from behind: the real GBA's corner notches sit at the real shell's foot, and on your pack's
                // GBA shape (narrower at the bottom) or a photo, that's past the piece's own edge, so their dark inner
                // walls hung in the air beside the plug. Now everything seen through the openings is cut to the piece's
                // own outline through its thickness (its slices' silhouettes): nothing can show outside the cartridge.)
                if (under != null && uv != null) {
                    val cv = drawContext.canvas
                    val room = androidx.compose.ui.geometry.Rect(-w, -h, 2f * w, 2f * h)
                    cv.saveLayer(room, androidx.compose.ui.graphics.Paint())
                    CartUndersides.drawInside(this, under.geo, uv, plasticArgb, under.opacity)
                    cv.saveLayer(room, androidx.compose.ui.graphics.Paint().apply { blendMode = BlendMode.DstIn })
                    val mn = n.coerceAtLeast(1)
                    for (k in 0..mn) at(-depth / 2f + depth * k / mn) { drawLayer(solid) }
                    cv.restore()
                    cv.restore()
                }
            }
        }
    ) { PieceFace(p, Modifier.fillMaxSize()) }
}

/** Update 275: what InspectSlab draws of a piece's connector end (CartUnderside.kt). */
private class UnderPlan(
    val shell: CartShell, val geo: CartUndersides.Geo, val opacity: Float, val full: Boolean,
    /** Update 276: where the shell sits in a pack picture (CartUndersides.View's frame). */
    val frame: FloatArray? = null
)

/** Update 189: the shell's colour in a cartridge / card picture — the first solid pixel in from its left edge, half-way down. */
private fun sampleShell(b: android.graphics.Bitmap): Color? {
    val y = b.height / 2
    for (x in 0 until (b.width / 3)) {
        val px = b.getPixel(x, y)
        if ((px ushr 24) > 200) {
            // A few pixels further in (past the outline).
            val inX = (x + b.width / 40).coerceAtMost(b.width - 1)
            return Color(b.getPixel(inX, y))
        }
    }
    return null
}

/** Update 189: a brightness filter in 20 steps (made once each). */
private object SlabLight {
    private val cache = arrayOfNulls<ColorFilter>(21)
    fun of(l: Float): ColorFilter? {
        val i = (l.coerceIn(0f, 1f) * 20f).roundToInt()
        if (i >= 20) return null
        cache[i]?.let { return it }
        val k = i / 20f
        return ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(k, k, k, 1f) }).also { cache[i] = it }
    }
}

/** A disc turning: its label, a thin edge, its data side. */
@Composable
private fun InspectDisc(p: InspectPiece, yawOf: () -> Float, pitchOf: () -> Float = { 0f }) {
    // (Update 162 (review): both sides stay composed and the one facing you is picked while drawing — swapping them in
    // composition dropped the label's picture each time it turned over, and recomposed mid-turn.)
    // (Update 189: a mini disc at its real 8 cm against 12 cm.)
    val k = if (p.smallDisc) 80f / 120f else 1f
    // Update 189: its real format, for its data side (DiscUnderside.kt): from the console at once, from the image after.
    val format by produceKeyed(DiscFormats.quick(p.game.platform), p.game.stableKey, p.game.stableKey) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { DiscFormats.of(p.game) }.getOrDefault(value) }
    }
    // Update 288: the edge is the disc's own outline — its label face recorded (below) and drawn again, darker, slice
    // after slice through its thickness, as the cartridges' sides are (InspectSlab). It was a grey ring / strip drawn
    // at a set size (98 % of the box), so on a disc whose picture is a little smaller (most photos) a grey band stood
    // out round its rim whenever it was turned or tilted ("a small grey layer on the edges"); now the edge is exactly
    // the disc's own rim, in the rim's own colour, and nothing reaches past it.
    // ([0] the label side, [1] the data side — the edge follows the side facing you: from behind, the label's own
    // outline showed its colour round the smaller data side.)
    val faceLayer = remember { arrayOfNulls<androidx.compose.ui.graphics.layer.GraphicsLayer>(2) }
    // (Read by the edge so it's drawn again once the face has been recorded the first time.)
    val faceReady = remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val mx = androidx.compose.ui.graphics.Matrix()
                val edge = obtainGraphicsLayer()
                onDrawBehind {
                    val pose = PiecePose(yawOf(), pitchOf())
                    val face = faceLayer[if (pose.facing >= 0f) 0 else 1]
                    if (!faceReady.value || face == null) return@onDrawBehind
                    val sn = pose.spread
                    val d = maxOf(p.depth * k * maxOf(size.width, size.height), 2f)
                    val n = kotlin.math.ceil(d * sn / 1.2f).toInt().coerceIn(0, 24)
                    if (n == 0) return@onDrawBehind
                    edge.colorFilter = SlabLight.of(0.42f + 0.22f * sn)
                    edge.record(size = androidx.compose.ui.unit.IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                        // (The data side is a little smaller than the box: in its middle, as it's laid out.)
                        translate((size.width - face.size.width) / 2f, (size.height - face.size.height) / 2f) { drawLayer(face) }
                    }
                    // Far to near (the face itself is drawn over the nearest).
                    val c = pose.facing
                    for (i in 0..n) {
                        val f = i.toFloat() / n
                        val z = if (c >= 0f) -d / 2f + d * f else d / 2f - d * f
                        pose.matrix(mx, center, z)
                        withTransform({ transform(mx) }) { drawLayer(edge) }
                    }
                }
            }
        )
        // (Update 238, #117: label or data side by which one faces you, tilted too — cos yaw · cos pitch, PiecePose.)
        Box(
            Modifier.matchParentSize().slabFace(yawOf, p.depth * k, pitchOf).drawWithContent {
                drawContent()
                val dark = 0.35f * PiecePose(yawOf(), pitchOf()).spread
                if (dark > 0.01f) drawRect(Color.Black, alpha = dark, blendMode = BlendMode.SrcAtop)
            },
            contentAlignment = Alignment.Center
        ) {
            PieceFace(p, Modifier.fillMaxSize().graphicsLayer { alpha = if (PiecePose(yawOf(), pitchOf()).facing >= 0f) 1f else 0f }
                // (Update 288: recorded, unturned, for the edge's slices behind it.)
                .drawWithCache {
                    val l = obtainGraphicsLayer()
                    // (Update 294: flat into its own picture — the edge's slices draw it at a new angle every frame.)
                    l.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    faceLayer[0] = l
                    onDrawWithContent {
                        l.record { this@onDrawWithContent.drawContent() }
                        drawLayer(l)
                        if (!faceReady.value) faceReady.value = true
                    }
                })
            DiscDataSide(format, yawOf, Modifier.fillMaxSize(k * 0.98f).graphicsLayer { alpha = if (PiecePose(yawOf(), pitchOf()).facing >= 0f) 0f else 1f }
                .drawWithCache {
                    val l = obtainGraphicsLayer()
                    l.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                    faceLayer[1] = l
                    onDrawWithContent {
                        l.record { this@onDrawWithContent.drawContent() }
                        drawLayer(l)
                    }
                })
        }
    }
}

/**
 * A disc's data side (update 189: as its real format looks — DiscUnderside.kt — the light and the rainbow moving as it
 * turns). Drawn into the face's own layer so the hole goes through.
 */
@Composable
private fun DiscDataSide(format: DiscFormat, yawOf: () -> Float, modifier: Modifier) {
    Spacer(
        modifier.drawBehind {
            val r = minOf(size.width, size.height) / 2f
            drawDiscUnderside(Offset(size.width / 2f, size.height / 2f), r, format, yawOf())
        }
    )
}

/*
 * (Update 200: InspectModel and its helpers — the 3D shape library's pre-rendered pictures, every 20° round, crossfaded —
 * are gone with the MODEL kind: they turned like a slide show. The drawn pieces turn smoothly as SLABs.)
 */
