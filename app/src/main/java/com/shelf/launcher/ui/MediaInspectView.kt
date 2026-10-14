package com.shelf.launcher.ui

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val physical = state.physicalMedia[k]
    val cover: Any? = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
    val small = Features.bigGcDiscs.state.value.not()
    val piece = runCatching { mediaPiece(g, CardArtType.CARTRIDGE_DISC, physical, cover, small, false) }.getOrNull()
    if (piece != null && (piece.kind == InspectKind.DISC || piece.kind == InspectKind.SLAB)) return piece
    return if (mediaRound(g.platform)) InspectPiece(
        g, InspectKind.DISC, 1f, 0.012f, MediaPick(MediaShell.DISC, genericDiscKey(g.platform)), cover, false, null, cover, null, null,
        physical, null, PLAIN_SHELL, false
    ) else null
}

/** Update 198: how far an opened case moves aside (share of its width) and shrinks, beside what came out of it. */
internal const val CASE_ASIDE_SHIFT = 0.25f
internal const val CASE_ASIDE_SHRINK = 0.48f

/**
 * Update 198: [g]'s case for the second screen's Details page — always its case (scanned front, printed back), with what's
 * inside for opening it.
 */
internal fun secondCasePiece(g: Game, state: LauncherUiState): InspectPiece {
    val k = g.stableKey
    val cover: Any? = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
    return boxPiece(g, state.physicalMedia[k], cover, own = !LookStyles.caseForAll(), mixed = false).also {
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
                .pointerInput(piece.content, itemPlace) {
                    detectTapGestures(onTap = { at ->
                        motion.tapCase(scope, piece.content != null, itemPlace?.hit(at.x, at.y, size.width.toFloat(), size.height.toFloat()) == true)
                    })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { motion.release(scope) }, onDragCancel = { motion.release(scope) }) { change, dx ->
                        change.consume(); motion.nudge(dx * 0.42f)
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

/** Update 189: what the opening case draws lying in it, from its [content]. */
internal fun caseItemOf(content: InspectPiece?): CaseItem? = content?.let { c ->
    CaseItem(c.realScan, c.label ?: c.cover, if (c.kind == InspectKind.DISC) 1f else c.aspect, c.tone.toArgbInt(), c.kind == InspectKind.DISC)
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private val PLAIN_SHELL = Color(0xFF2C2E34)
private val CUSTOM_SHELL = Color(0xFF55585F)

/** A cartridge's / card's thickness against its longest side. */
private fun slabDepth(shell: MediaShell?): Float = when (shell) {
    MediaShell.DS, MediaShell.DS3, MediaShell.SWITCH, MediaShell.VITA, MediaShell.CARD, MediaShell.HUCARD -> 0.055f
    MediaShell.UMD -> 0.075f
    MediaShell.N64, MediaShell.SNES, MediaShell.BLACK_CART -> 0.15f
    else -> 0.12f
}

/** The plain colour of a shell's back and side. */
private fun shellTone(shell: MediaShell?): Color = when (shell) {
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
    inspectPieceOf(g, state)?.also {
        it.logo = state.artOverrides[g.stableKey]?.logo ?: state.art[g.stableKey]?.logoUrl
        // Update 170: a case without its back scanned gets a printed one (Settings › Game look › Printed case backs).
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
    return PrintedBack(
        LookStyles.casePlatform(g.platform),
        o?.hero ?: a?.heroUrl ?: o?.cover ?: a?.gridUrl,
        state.screenshots[k].orEmpty().take(4),
        state.metadata[k]?.summary,
        aspect,
        gameDisplayName(g.name)
    )
}

private fun inspectPieceOf(g: Game, state: LauncherUiState): InspectPiece? {
    val a = state.appearance
    val k = g.stableKey
    val p = g.platform
    val physical = state.physicalMedia[k]
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
        val typed = if (mediaKind(p) != "disc") null else typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, p)
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
    return when (artForGame(chosen, p, a.hiddenMedia, mixed)) {
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
    val front = if (scan != null && (cover == null || (scanFront && Features.originalCases.state.value))) scan else cover ?: scan
    val back = if (own) BoxTexture.fullScan(physical)?.let { BoxTexture.model(it, BoxPart.BACK, g.platform) } else null
    return InspectPiece(
        g, InspectKind.BOX, case3dAspect(g, mixed), 0f, null, null, false, null, cover, front, back, physical, null, PLAIN_SHELL, false
    )
}

private fun mediaPiece(
    g: Game, chosen: CardArtType, physical: List<Pair<String, String>>?, cover: Any?, small: Boolean, mixed: Boolean
): InspectPiece? {
    val p = g.platform
    val typed = typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, p)
    // (Update 162 (review): [mixed] as the cards ask for it — "All CDs" / "All cartridges" draw the generic piece.)
    val pick = mediaFor(p, mixed, chosen == CardArtType.DISC, gameKey = g.stableKey)
    // (Update 200: the 3D cartridges looks come here as drawn shells — a SLAB at the shell's own depth below, turning
    // smoothly — never the pre-rendered 3D models.)
    // "Real discs": the game's own cartridge / disc picture. (Update 200: not in the 3D cartridges looks — the cards
    // draw their drawn piece there, drawnMediaWanted.)
    val real = if (drawn3dOn() && pick != null && isCustomPiece(pick.platform)) null else realScanOf(CardArtType.CARTRIDGE_DISC, typed)
    if (real != null) {
        val shell = MediaShell.of(p)
        return if (mediaRound(p)) InspectPiece(
            g, InspectKind.DISC, 1f, 0.012f, null, null, false, real, cover, null, null, physical, null, PLAIN_SHELL, false
        ) else InspectPiece(
            g, InspectKind.SLAB, (shell?.aspect ?: 1f).coerceIn(0.3f, 3f), slabDepth(shell), null, null, false, real, cover, null, null,
            physical, null, shellTone(shell), false
        )
    }
    if (pick == null) return null
    val label = typed ?: cover
    val isScan = typed != null
    val custom = isCustomPiece(pick.platform)
    val tpl = if (custom) null else MediaTemplates.of(pick.platform)
    return when {
        // "Disc in its case": the card shows the case — that's what comes out.
        tpl != null && discCaseOn(tpl) -> boxPiece(g, physical, cover, own = true, mixed = mixed)
        pick.shell.disc -> InspectPiece(
            g, InspectKind.DISC, 1f, 0.012f, pick, label, isScan, null, cover, null, null, physical, null, PLAIN_SHELL,
            pick.shell == MediaShell.MINI_DISC && small
        )
        else -> InspectPiece(
            g, InspectKind.SLAB, slabAspect(pick, isScan),
            // (Update 195: a library shell at its own real thickness.)
            if (pick.platform.startsWith(SHELL_PREFIX)) (CartShells.split(pick.platform).first?.shell?.depth ?: 0.12f).coerceIn(0.02f, 0.4f)
            else slabDepth(if (custom) null else pick.shell), pick, label, isScan, null,
            cover, null, null, physical, null, if (custom) CUSTOM_SHELL else shellTone(pick.shell), false
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
internal class InspectMotion {
    /** 0 = in its place on the home screen, 1 = out in front. */
    val lift = mutableFloatStateOf(0f)
    /** The showcase turn, degrees (positive: the front turns right, so its left side — a case's spine — shows). */
    val turn = mutableFloatStateOf(0f)
    /** Your own turn on top (drag / right stick). */
    val manual = mutableFloatStateOf(0f)
    /** Its opacity (the short cross-fade used instead of the flight). */
    val fade = mutableFloatStateOf(1f)
    /** The right stick's left / right (−1…1), 0 at rest. */
    val stick = mutableFloatStateOf(0f)
    /** Where it came from (window px); on the way back its place is read again. */
    var from: Rect? = null
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

    /** Update 189: let go: it turns back to face you (the short way). */
    fun releaseContent(scope: CoroutineScope) {
        contentEase?.cancel()
        contentEase = scope.launch {
            val now = contentTurn.floatValue
            val to = (now / 360f).roundToInt() * 360f
            animate(now, to, animationSpec = tween(620, easing = FastOutSlowInEasing)) { v, _ -> contentTurn.floatValue = v }
            contentTurn.floatValue = 0f
        }
    }
    private var caseJob: Job? = null

    /**
     * Update 189: opens the case (it turns to face you, the lid swings open — not a cardboard box, [hinged] false —
     * then, with something inside, [hasContent], the case moves aside and that comes out) or closes it again.
     */
    fun toggleCase(scope: CoroutineScope, hinged: Boolean, hasContent: Boolean) {
        if (closing || !started) return
        caseJob?.cancel()
        opened = !opened
        caseJob = scope.launch {
            if (opened) {
                spinJob?.cancel(); easeJob?.cancel()
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
        caseJob?.cancel()
        opened = true
        caseJob = scope.launch {
            spinJob?.cancel(); easeJob?.cancel()
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
        caseJob?.cancel()
        opened = true
        caseJob = scope.launch {
            // (Turned to face you first, so it lifts out of where it's seen lying.)
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
        val fly = if (k > 0f && k < 1f) -14f * sin(PI.toFloat() * k) else 0f
        val y = turn.floatValue + manual.floatValue + fly
        return if (limited) y.coerceIn(-60f, 60f) else y
    }

    /** Turns it [deg] more by hand (stops the showcase turn). */
    fun nudge(deg: Float) {
        spinJob?.cancel(); easeJob?.cancel()
        var m = manual.floatValue + deg
        if (limited) m = m.coerceIn(-60f - turn.floatValue, 60f - turn.floatValue)
        manual.floatValue = m
    }

    /**
     * Update 199: a tap on a cartridge, disc or 3D piece (nothing to open): it turns once round to show every side (a 3D
     * model: to one side, the other and back), from facing you. A drag or the stick stops it.
     */
    fun spinOnce(scope: CoroutineScope) {
        if (closing || !started || lift.floatValue < 0.999f || spinJob?.isActive == true) return
        easeJob?.cancel()
        spinJob = scope.launch {
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            turn.floatValue = 0f
            animate(total, front, animationSpec = tween(220, easing = FastOutSlowInEasing)) { v, _ -> manual.floatValue = v }
            manual.floatValue = 0f
            showcase()
        }
    }

    /** Let go: it eases back to facing you. */
    fun release(scope: CoroutineScope) {
        if (closing) return
        easeJob?.cancel()
        easeJob = scope.launch {
            val total = turn.floatValue + manual.floatValue
            val front = if (limited) 0f else (total / 360f).roundToInt() * 360f
            animate(manual.floatValue, front - turn.floatValue, animationSpec = tween(560, easing = FastOutSlowInEasing)) { v, _ ->
                manual.floatValue = v
            }
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
                return@launch
            }
            animate(0f, 1f, animationSpec = tween(600, easing = CubicBezierEasing(0.3f, 0f, 0.12f, 1f))) { v, _ ->
                lift.floatValue = v
                MediaInspect.amount.floatValue = v
            }
            if (spin && !closing) spinJob = scope.launch { showcase() }
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
    DisposableEffect(view, motion) {
        view.setOnGenericMotionListener { _, ev ->
            if (ev.isFromSource(InputDevice.SOURCE_JOYSTICK) && ev.action == MotionEvent.ACTION_MOVE) {
                val rx = ev.getAxisValue(MotionEvent.AXIS_Z)
                motion.stick.floatValue = if (abs(rx) > 0.25f) rx else 0f
            }
            // (Update 162 (review): never taken — the same event carries the left stick, which Android turns into D-pad
            // presses for the window's buttons only when nobody handles it.)
            false
        }
        onDispose { view.setOnGenericMotionListener(null) }
    }
    LaunchedEffect(motion) {
        // (Update 162 (review): ends once it's closing — held stick and closing made this loop spin without ever waiting.)
        while (!motion.closing) {
            snapshotFlow { abs(motion.stick.floatValue) > 0.25f }.first { it }
            if (motion.closing) break
            var last = -1L
            // (Update 198: with the disc / cartridge out of its case, the stick turns that — it used to flip the case
            // aside behind it.)
            val content = motion.out.floatValue > 0.5f
            while (abs(motion.stick.floatValue) > 0.25f && !motion.closing) {
                val now = withFrameNanos { it }
                if (last > 0L) {
                    val deg = motion.stick.floatValue * 200f * ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    if (content) motion.nudgeContent(deg) else motion.nudge(deg)
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
    // Where this overlay sits in the window (the places are in window px).
    val win = remember { FloatArray(2) }
    val yawOf: () -> Float = remember(motion) { { motion.yaw() } }
    // Update 189: the opening case (CaseOpen.kt): its lid, what lies in it, and whether that's still in it.
    val openOf: () -> Float = remember(motion) { { motion.lid.floatValue } }
    val itemInCase: () -> Boolean = remember(motion) { { motion.out.floatValue <= 0.001f } }
    val caseItem = remember(piece) { caseItemOf(piece.content) }
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
                                val sw = minOf(f.width * 0.94f, f.height * 0.94f * aspect)
                                val s0 = (sw / w).coerceIn(0.05f, 4f)
                                val pop = sin(PI.toFloat() * k)
                                val s = (s0 + (1f - s0) * k) * (1f + 0.06f * pop)
                                scaleX = s; scaleY = s
                                translationX = (f.center.x - (left + w / 2f)) * (1f - k)
                                translationY = (f.center.y - (top + h / 2f)) * (1f - k) - h * 0.07f * pop
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
                    .pointerInput(piece.kind, piece.content, itemPlace) {
                        detectTapGestures(onTap = { at ->
                            // (Update 191: a cardboard box opens too — its top flap.)
                            // (Update 200: a step at a time — open, then a tap on the disc takes it out.)
                            if (piece.kind == InspectKind.BOX) motion.tapCase(
                                scope, piece.content != null,
                                itemPlace?.hit(at.x, at.y, size.width.toFloat(), size.height.toFloat()) == true
                            )
                            // (Update 199: a cartridge, disc or 3D piece has nothing to open: it turns once round.)
                            else motion.spinOnce(scope)
                        })
                    }
                    // A horizontal drag turns it yourself; let go and it eases back.
                    .pointerInput(piece.kind) {
                        detectHorizontalDragGestures(
                            onDragEnd = { motion.release(scope) },
                            onDragCancel = { motion.release(scope) }
                        ) { change, dx ->
                            change.consume()
                            if (!motion.closing) motion.nudge(dx * 0.42f)
                        }
                    }
            ) {
                InspectShadow(motion)
                when (piece.kind) {
                    InspectKind.BOX -> Case3D(
                        piece.game, piece.front, piece.physical, 0f, aspect, Modifier.fillMaxSize(),
                        // (Update 162: Case3D's back face and its angle read while drawing.)
                        back = piece.back, yawOf = yawOf, fill = 0.97f,
                        // (Update 189: it opens.)
                        openOf = openOf, inside = caseItem, showItemOf = itemInCase
                    )
                    InspectKind.SLAB -> InspectSlab(piece, yawOf)
                    InspectKind.DISC -> InspectDisc(piece, yawOf)
                    // (Update 200: no MODEL any more — InspectModel's pre-rendered pictures are retired.)
                }
            }
            // Update 189: what was in the case, out in the middle (CaseInside places where it lay).
            val content = piece.content
            if (content != null && piece.kind == InspectKind.BOX) InspectContent(piece, content, motion, left - win[0], top - win[1], w, h, aspect, scope)
            // Update 189: how to open it, said once it's out.
            if (piece.kind == InspectKind.BOX && piece.content != null) OpenHint(
                motion, left - win[0], top + h - win[1], w,
                when (piece.content?.kind) { InspectKind.DISC -> "disc"; else -> if (piece.content?.game?.platform in setOf("NDS", "3DS", "Switch", "PSVita")) "card" else "cartridge" }
            )
        }
    }
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
                // A drag turns it round (a disc: over to its data side); let go and it comes back.
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { motion.releaseContent(scope) },
                        onDragCancel = { motion.releaseContent(scope) }
                    ) { change, dx ->
                        change.consume()
                        if (motion.out.floatValue > 0.5f) motion.nudgeContent(dx * 0.5f)
                    }
                })
    ) {
        if (disc) InspectDisc(content, yawOf) else InspectSlab(content, yawOf)
    }
}

/**
 * Update 189: "Tap the case to open it", under the case for a few seconds once it's out. Update 200: then, the first time
 * it's opened, "Tap the disc to take it out" (cartridge / card) for a few seconds.
 */
@Composable
private fun OpenHint(motion: InspectMotion, left: Float, bottom: Float, w: Float, what: String) {
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
            .graphicsLayer { alpha = shown.value * (1f - motion.out.floatValue) * (if (second) 1f else (1f - motion.lid.floatValue)) },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            if (second) "Tap the $what (or click a stick) to take it out" else "Tap the case (or click a stick) to open it",
            color = Color.White, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(50))
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
            if (tpl?.disc != null && discCaseOn(tpl)) TemplateMedia(
                MediaTpl(null, null, tpl.disc, null), labelScan = if (p.labelIsScan) p.label else null,
                cover = if (p.labelIsScan) null else p.label, modifier = modifier, smallDisc = p.smallDisc
            )
            else PhysicalMedia(pick.shell, p.label, modifier, platform = pick.platform, labelIsScan = p.labelIsScan, caseCover = p.cover, title = p.game.name)
        }
    }
}

/** Turned by [yawOf]: the face squeezed as it turns and shaded, moved as a slab of [depth] would. */
private fun Modifier.slabFace(yawOf: () -> Float, depth: Float): Modifier = this.graphicsLayer {
    val t = Math.toRadians(yawOf().toDouble())
    val c = cos(t).toFloat()
    val s = sin(t).toFloat()
    val d = depth * maxOf(size.width, size.height)
    // (Never quite 0 wide: a layer scaled to nothing can drop its content for a frame.)
    scaleX = if (abs(c) < 0.01f) (if (c < 0f) -0.01f else 0.01f) else c
    // The front is in front of the middle, the back behind it.
    translationX = (if (c >= 0f) 1f else -1f) * d / 2f * s
    compositingStrategy = CompositingStrategy.Offscreen
}

/**
 * A cartridge / card turning (update 189: a real solid piece). Its face is recorded once and drawn again, darkened,
 * slice after slice through its thickness — so its sides have its own outline and its own colour (a green shell's
 * edge is green, a notched cartridge's edge follows the notch) — then the face in front; from behind, its outline
 * filled with its shell's colour (taken from the picture itself), never the label mirrored. It used to be the face
 * squeezed beside a grey strip that floated apart from it.
 */
@Composable
private fun InspectSlab(p: InspectPiece, yawOf: () -> Float) {
    val holder = remember { arrayOfNulls<androidx.compose.ui.graphics.layer.GraphicsLayer>(1) }
    // Update 195: a library shell's own back (seam, screws, contacts), shown when it's turned round.
    // (Update 200: its researched back; plain — no maker's markings — on another console's shell, or as set.)
    val backStyle = Features.cartBackStyle.state.value
    val shellBack = remember(p.pick?.platform, backStyle) {
        p.pick?.platform?.takeIf { it.startsWith(SHELL_PREFIX) }?.let { pp ->
            val (ch, console) = CartShells.split(pp)
            ch?.shell?.let { sh ->
                val neutral = when (backStyle) {
                    Features.CartBackStyle.AUTHENTIC -> false
                    Features.CartBackStyle.NEUTRAL -> true
                    Features.CartBackStyle.AUTO -> console == null || !CartShells.belongsTo(sh.id, console)
                }
                runCatching { CartShells.back(sh, CartShells.colourOf(sh, ch.colourKey), 640, neutral).asImageBitmap() }.getOrNull()
            }
        }
    }
    var shell by androidx.compose.runtime.remember(p) { androidx.compose.runtime.mutableStateOf<Color?>(null) }
    // Its shell colour, from the face's own pixels (the edge, half-way down), once it's drawn.
    LaunchedEffect(p) {
        delay(700)
        val bmp = runCatching { holder[0]?.toImageBitmap()?.asAndroidBitmap() }.getOrNull() ?: return@LaunchedEffect
        // (Update 192: the layer's picture is a hardware bitmap — its pixels can't be read, that crashed Voltcap on the
        // Thor and the Classic as soon as a cartridge was turned. A readable copy is read, and nothing can crash here.)
        shell = runCatching {
            val soft = if (bmp.config == android.graphics.Bitmap.Config.HARDWARE) bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else bmp
            soft?.let { sampleShell(it) }
        }.getOrNull()
    }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val face = obtainGraphicsLayer()
            holder[0] = face
            val edge = obtainGraphicsLayer()
            val lit = obtainGraphicsLayer()
            val back = obtainGraphicsLayer()
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.18f)))
            onDrawWithContent {
                face.record { this@onDrawWithContent.drawContent() }
                val t = Math.toRadians(yawOf().toDouble())
                val c = cos(t).toFloat()
                val sn = sin(t).toFloat()
                val w = size.width
                val h = size.height
                val depth = p.depth * maxOf(w, h)
                val sx = if (abs(c) < 0.01f) (if (c < 0f) -0.01f else 0.01f) else c
                fun at(z: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
                    withTransform({ translate(z * sn, 0f); scale(sx, 1f, pivot = center) }) { block() }
                // The sides: the face's outline in its own colours, darker, lit a little as they face you.
                edge.colorFilter = SlabLight.of(0.42f + 0.22f * abs(sn))
                edge.record { drawLayer(face) }
                val n = kotlin.math.ceil(depth * abs(sn) / 1.2f).toInt().coerceIn(0, 48)
                if (n > 0) {
                    // Far to near.
                    for (k in 0..n) {
                        val f = k.toFloat() / n
                        val z = if (c >= 0f) -depth / 2f + depth * f else depth / 2f - depth * f
                        at(z) { drawLayer(edge) }
                    }
                }
                if (c >= 0f) {
                    lit.colorFilter = SlabLight.of(1f - 0.4f * abs(sn))
                    lit.record { drawLayer(face) }
                    at(depth / 2f) { drawLayer(lit) }
                } else {
                    val tone = shell ?: p.tone
                    back.record {
                        val sb = shellBack
                        if (sb != null) {
                            // (Update 195: the shell's drawn back, fitted as its front is.)
                            val a = sb.width.toFloat() / sb.height.coerceAtLeast(1)
                            val bw = minOf(size.width, size.height * a); val bh = bw / a
                            drawImage(sb, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - bw) / 2f).toInt(), ((size.height - bh) / 2f).toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(bw.toInt(), bh.toInt()))
                        } else {
                            drawLayer(face)
                            drawRect(tone, blendMode = BlendMode.SrcIn)
                        }
                        drawRect(sheen, blendMode = BlendMode.SrcAtop)
                        drawRect(Color.Black, alpha = 0.4f * abs(sn), blendMode = BlendMode.SrcAtop)
                    }
                    at(-depth / 2f) { drawLayer(back) }
                }
            }
        }
    ) { PieceFace(p, Modifier.fillMaxSize()) }
}

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
private fun InspectDisc(p: InspectPiece, yawOf: () -> Float) {
    // (Update 162 (review): both sides stay composed and the one facing you is picked while drawing — swapping them in
    // composition dropped the label's picture each time it turned over, and recomposed mid-turn.)
    // (Update 189: a mini disc at its real 8 cm against 12 cm.)
    val k = if (p.smallDisc) 80f / 120f else 1f
    // Update 189: its real format, for its data side (DiscUnderside.kt): from the console at once, from the image after.
    val format by produceState(DiscFormats.quick(p.game.platform), p.game.stableKey) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { DiscFormats.of(p.game) }.getOrDefault(value) }
    }
    val rim = remember { Color(0xFFA9ADB6) }
    Box(Modifier.fillMaxSize()) {
        Spacer(
            Modifier.matchParentSize().drawBehind {
                val t = Math.toRadians(yawOf().toDouble())
                val c = cos(t).toFloat()
                val s = sin(t).toFloat()
                val dia = minOf(size.width, size.height) * k * 0.98f
                val d = maxOf(p.depth * dia, 2f)
                val half = d / 2f * abs(s)
                if (half > 0.3f) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val ew = dia * abs(c)
                    // The far face's outline behind, joined to the near one by the edge.
                    val bx = cx - (if (c >= 0f) 1f else -1f) * d / 2f * s
                    // (Update 189: not through the hole — the edge and the far face showed in the middle of the disc.)
                    val holeW = dia * 0.075f * abs(c).coerceAtLeast(0.05f)
                    val hole = androidx.compose.ui.graphics.Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(cx - holeW, cy - dia * 0.075f, cx + holeW, cy + dia * 0.075f))
                    }
                    clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) {
                        drawOval(rim, topLeft = Offset(bx - ew / 2f, cy - dia / 2f), size = Size(ew, dia))
                        drawRect(rim, topLeft = Offset(cx - half, cy - dia / 2f), size = Size(2f * half, dia))
                        drawRect(Color.Black, topLeft = Offset(cx - half, cy - dia / 2f), size = Size(2f * half, dia), alpha = 0.3f)
                    }
                }
            }
        )
        Box(
            Modifier.matchParentSize().slabFace(yawOf, p.depth * k).drawWithContent {
                drawContent()
                val dark = 0.35f * abs(sin(Math.toRadians(yawOf().toDouble())).toFloat())
                if (dark > 0.01f) drawRect(Color.Black, alpha = dark, blendMode = BlendMode.SrcAtop)
            },
            contentAlignment = Alignment.Center
        ) {
            PieceFace(p, Modifier.fillMaxSize().graphicsLayer { alpha = if (cos(Math.toRadians(yawOf().toDouble())) >= 0.0) 1f else 0f })
            DiscDataSide(format, yawOf, Modifier.fillMaxSize(k * 0.98f).graphicsLayer { alpha = if (cos(Math.toRadians(yawOf().toDouble())) >= 0.0) 0f else 1f })
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
