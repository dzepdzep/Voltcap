package com.shelf.launcher.ui

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    DISC,
    /** A piece of your 3D shape library, turned through its pre-rendered angles. */
    MODEL
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
    /** MODEL: the shape library's piece. */
    val model: String?,
    /** SLAB: its shell's colour (side and back). */
    val tone: Color,
    /** DISC: a mini disc drawn at its real size (70 %). */
    val smallDisc: Boolean
) {
    /** Update 166: MODEL: the game's logo (SteamGridDB's, or yours), printed on the piece's back label. */
    var logo: Any? = null
}

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
    val own = pick.platform.startsWith(MODEL_3D) || pick.platform.startsWith(CUSTOM_CART)
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
        aspect
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
        // A cover look the carousel draws as 3D cases (it says so for the game you're on): its 3D case model when it
        // draws one (R1 › 3D in a case look), else the box with the cover on its front, as the carousel shows it.
        CardArtType.COVER -> if (!drawnAsCase) null else {
            val m = if (Features.caseModels3d.state.value) Models3D.caseModel(p) else null
            if (m != null) modelPiece(g, m, cover, physical) else boxPiece(g, physical, cover, own = true, mixed = mixed, scanFront = false)
        }
        else -> null
    }
}

private fun casePiece(g: Game, physical: List<Pair<String, String>>?, cover: Any?, mixed: Boolean): InspectPiece {
    val casePlatform = LookStyles.casePlatform(g.platform)
    // One console's case picked for all (R1 › Case): its shape only, never this game's scans in it.
    val own = !LookStyles.caseForAll()
    // Your 3D case from the shape library (R1 › 3D in a case look), as CaseArt draws it.
    if (Features.caseModels3d.state.value && (own || casePlatform == g.platform)) {
        Models3D.caseModel(casePlatform)?.let { return modelPiece(g, it, cover, physical) }
    }
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

private fun modelPiece(g: Game, id: String, cover: Any?, physical: List<Pair<String, String>>?): InspectPiece {
    // A 3D case wears the game's own scanned front (as Model3DArt does, update 159).
    val front = if (Models3D.isCase(id) && Features.originalCases.state.value) BoxTexture.front(physical, g.platform) ?: cover else cover
    return InspectPiece(
        g, InspectKind.MODEL, Models3D.aspect(id).coerceIn(0.25f, 4f), 0f, null, null, false, null, cover, front, null, physical, id,
        PLAIN_SHELL, false
    )
}

private fun mediaPiece(
    g: Game, chosen: CardArtType, physical: List<Pair<String, String>>?, cover: Any?, small: Boolean, mixed: Boolean
): InspectPiece? {
    val p = g.platform
    val typed = typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, p)
    // (Update 162 (review): [mixed] as the cards ask for it — "All CDs" / "All cartridges" draw the generic piece.)
    val pick = mediaFor(p, mixed, chosen == CardArtType.DISC)
    // Your 3D piece (the 3D models look).
    if (pick != null && pick.platform.startsWith(MODEL_3D)) {
        val id = pick.platform.removePrefix(MODEL_3D).substringBefore('|')
        return modelPiece(g, id, cover ?: typed, physical)
    }
    // "Real discs": the game's own cartridge / disc picture.
    val real = realScanOf(CardArtType.CARTRIDGE_DISC, typed)
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
    val custom = pick.platform.startsWith(CUSTOM_CART)
    val tpl = if (custom) null else MediaTemplates.of(pick.platform)
    return when {
        // "Disc in its case": the card shows the case — that's what comes out.
        tpl != null && discCaseOn(tpl) -> boxPiece(g, physical, cover, own = true, mixed = mixed)
        pick.shell.disc -> InspectPiece(
            g, InspectKind.DISC, 1f, 0.012f, pick, label, isScan, null, cover, null, null, physical, null, PLAIN_SHELL,
            pick.shell == MediaShell.MINI_DISC && small
        )
        else -> InspectPiece(
            g, InspectKind.SLAB, slabAspect(pick, isScan), slabDepth(if (custom) null else pick.shell), pick, label, isScan, null,
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
        openJob?.cancel(); spinJob?.cancel(); easeJob?.cancel()
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
            while (abs(motion.stick.floatValue) > 0.25f && !motion.closing) {
                val now = withFrameNanos { it }
                if (last > 0L) motion.nudge(motion.stick.floatValue * 200f * ((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
            motion.release(this)
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
    Box(Modifier.fillMaxSize().onGloballyPositioned { c -> val o = c.positionInWindow(); win[0] = o.x; win[1] = o.y }) {
        if (slot != null) {
            val aspect = piece.aspect.coerceIn(0.25f, 4f)
            // As big as fits its side of the window (some room kept for its turn and its shadow). Update 166: a case at its
            // real size against a DVD case's (a Game Boy box smaller than a GameCube case).
            val real = if (piece.kind == InspectKind.BOX) realCaseScale(piece.game.platform) else 1f
            val w = (minOf(slot.width * 0.9f, slot.height * 0.8f * aspect) * real).coerceAtLeast(8f)
            val h = w / aspect
            val left = slot.left + (slot.width - w) / 2f
            val top = slot.top + (slot.height - h) / 2f
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
                        back = piece.back, yawOf = yawOf
                    )
                    InspectKind.SLAB -> InspectSlab(piece, yawOf)
                    InspectKind.DISC -> InspectDisc(piece, yawOf)
                    InspectKind.MODEL -> piece.model?.let { id -> InspectModel(piece, id, aspect, yawOf) }
                }
            }
        }
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
            val own = pick.platform.startsWith(MODEL_3D) || pick.platform.startsWith(CUSTOM_CART)
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

/** A cartridge / card turning: its face, the shell's side band showing its thickness, its plain back. */
@Composable
private fun InspectSlab(p: InspectPiece, yawOf: () -> Float) {
    val tone = p.tone
    Box(Modifier.fillMaxSize()) {
        // The side between front and back, as it comes round.
        Spacer(
            Modifier.matchParentSize().drawBehind {
                val t = Math.toRadians(yawOf().toDouble())
                val c = cos(t).toFloat()
                val s = sin(t).toFloat()
                val w = size.width
                val h = size.height
                val half = p.depth * maxOf(w, h) / 2f * abs(s)
                if (half > 0.5f) {
                    // The left side shows turning right, the right side turning left.
                    val x = (if (s > 0f) -w / 2f else w / 2f) * c + w / 2f
                    val bandH = h * 0.95f
                    val tl = Offset(x - half - 1.5f, (h - bandH) / 2f)
                    val sz = Size(2f * half + 3f, bandH)
                    val corner = CornerRadius(minOf(half, w * 0.03f))
                    drawRoundRect(tone, topLeft = tl, size = sz, cornerRadius = corner)
                    // Lit as it faces you.
                    drawRoundRect(Color.Black, topLeft = tl, size = sz, cornerRadius = corner, alpha = 0.58f - 0.38f * abs(s))
                }
            }
        )
        Box(
            Modifier.matchParentSize().slabFace(yawOf, p.depth).drawWithCache {
                val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.12f)))
                onDrawWithContent {
                    drawContent()
                    val t = Math.toRadians(yawOf().toDouble())
                    val c = cos(t).toFloat()
                    val s = sin(t).toFloat()
                    if (c < 0f) {
                        // Seen from behind: the plain shell in its outline (never the label mirrored).
                        drawRect(tone, blendMode = BlendMode.SrcIn)
                        drawRect(sheen, blendMode = BlendMode.SrcAtop)
                    }
                    val dark = 0.4f * abs(s)
                    if (dark > 0.01f) drawRect(Color.Black, alpha = dark, blendMode = BlendMode.SrcAtop)
                }
            }
        ) { PieceFace(p, Modifier.fillMaxSize()) }
    }
}

/** A disc turning: its label, a thin edge, its data side. */
@Composable
private fun InspectDisc(p: InspectPiece, yawOf: () -> Float) {
    // (Update 162 (review): both sides stay composed and the one facing you is picked while drawing — swapping them in
    // composition dropped the label's picture each time it turned over, and recomposed mid-turn.)
    val k = if (p.smallDisc) 0.7f else 1f
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
                    drawOval(rim, topLeft = Offset(bx - ew / 2f, cy - dia / 2f), size = Size(ew, dia))
                    drawRect(rim, topLeft = Offset(cx - half, cy - dia / 2f), size = Size(2f * half, dia))
                    drawRect(Color.Black, topLeft = Offset(cx - half, cy - dia / 2f), size = Size(2f * half, dia), alpha = 0.3f)
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
            DiscDataSide(Modifier.fillMaxSize(k * 0.98f).graphicsLayer { alpha = if (cos(Math.toRadians(yawOf().toDouble())) >= 0.0) 0f else 1f })
        }
    }
}

/** A disc's data side: silver with a faint rainbow sheen, the clear inner ring and the hole. */
@Composable
private fun DiscDataSide(modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val r = minOf(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = Brush.radialGradient(listOf(Color(0xFFDADDE3), Color(0xFFB7BBC4), Color(0xFF8F949E)), center = c, radius = r)
            val rainbow = Brush.sweepGradient(
                listOf(Color(0x55FF7A7A), Color(0x55FFD97A), Color(0x557AFFA6), Color(0x557AC8FF), Color(0x55B87AFF), Color(0x55FF7A7A)),
                center = c
            )
            val ringW = r * 0.012f
            onDrawBehind {
                drawCircle(base, radius = r, center = c)
                drawCircle(rainbow, radius = r * 0.97f, center = c)
                drawCircle(Color(0xFFCBCFD6), radius = r * 0.34f, center = c)
                drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.34f, center = c, style = Stroke(ringW))
                // The hole, through (it's drawn into the face's own layer).
                drawCircle(Color.Black, radius = r * 0.12f, center = c, blendMode = BlendMode.Clear)
            }
        }
    )
}

/** Update 162: [yaw] brought into −180…180. */
private fun inspectNorm(yaw: Float): Float = Models3D.wrap(yaw)

/**
 * Update 166: the pack's angle at or just below [yaw] (−180…160, every 20°: it and the next one up hold [yaw] between
 * them; −180 is the pack's 180, its back).
 */
private fun inspectLowAngle(yaw: Float): Int =
    (kotlin.math.floor((inspectNorm(yaw) + 180f) / 20f).toInt() * 20 - 180).coerceIn(-180, 160)

/** Update 166: the pack's picture for [a] (−180 is its 180). */
private fun inspectPackAngle(a: Int): Int = if (a <= -180) 180 else a

/** [id] at [angle] with its cover: at once when it's already made, else made away from the screen's thread. */
@Composable
private fun rememberInspectPicture(
    id: String, angle: Int, cover: android.graphics.Bitmap?, title: String?, pos: ArtPos, tint: Models3D.Tint?, version: Int, source: Any?,
    backLogo: android.graphics.Bitmap? = null
): android.graphics.Bitmap? {
    val waiting = source != null && cover == null
    val now = if (waiting && source is String) Models3D.cachedFor(id, angle, source, title, pos, tint, backLogo) else Models3D.cached(id, angle, cover, title, pos, tint, backLogo)
    val made by produceState(now, id, angle, cover, version, title, pos.key, tint?.key, backLogo) {
        if (now == null && !waiting) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Models3D.compose(id, angle, cover, title, pos, tint, backLogo) }
    }
    return now ?: made
}

/**
 * A 3D model turning through its pre-rendered angles, the two nearest crossfaded (as the one you're on turns on the
 * home screen), all of them made ahead so the turn never waits.
 */
@Composable
private fun InspectModel(p: InspectPiece, id: String, aspect: Float, yawOf: () -> Float) {
    val version = Packs.version.intValue
    val coverBmp = rememberSoftBitmap(p.front, 640)
    val name = gameDisplayName(p.game.name)
    val title = if (Features.modelName.state.value) name else null
    val pos = ArtPlace.of(model = true)
    ModelColors.version.intValue
    val tint = Models3D.tintFor(id, p.game.platform, name)
    // Update 166: the game's logo on its back label (the custom and real cartridges and cards; the cases have none).
    val logoBmp = rememberSoftBitmap(p.logo, 480)
    LaunchedEffect(id, coverBmp, version, title, pos.key, tint?.key, logoBmp) {
        if (p.front != null && coverBmp == null) return@LaunchedEffect
        // (Update 166: the front first, then outward both ways round — the sides and the back last.)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            for (a in Models3D.ANGLES.sortedBy { kotlin.math.abs(it) }) Models3D.compose(id, a, coverBmp, title, pos, tint, logoBmp)
        }
    }
    // Update 166: all the way round on the model's own pictures — its real sides and back, in its own colours (the pack
    // is pre-rendered every 20° round). The two nearest are crossfaded. (Update 162 squeezed the ±60° picture to its
    // edge and showed it mirrored as the back: the sides looked flat and a painted piece kept its colour only in front.)
    val low by remember(id) { derivedStateOf { inspectLowAngle(yawOf()) } }
    val pic0 = rememberInspectPicture(id, inspectPackAngle(low), coverBmp, title, pos, tint, version, p.front, logoBmp)
    val pic1 = rememberInspectPicture(id, inspectPackAngle(low + 20), coverBmp, title, pos, tint, version, p.front, logoBmp)
    val img0 = remember(pic0) { pic0?.asImageBitmap() }
    val img1 = remember(pic1) { pic1?.asImageBitmap() }
    // The last picture shown stays until the next pair is there (never an empty frame).
    val last = remember { arrayOfNulls<ImageBitmap>(1) }
    SideEffect { if (img0 != null) last[0] = img0 }
    // Update 166 (fluent): each of the two pictures is also turned the last few degrees to where the piece is right now
    // (a 3D turn of its layer, as the Save Vault's card does), so it moves smoothly between the pictures 20° apart instead
    // of stepping; they're crossfaded only round the middle between them, where both are turned 10° toward each other.
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    // How far past the lower picture the piece is (0…20°; across −180 the angle runs on from 160).
    fun past(): Float { var d = inspectNorm(yawOf()) - low; if (d < 0f) d += 360f; return d.coerceIn(0f, 20f) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        InspectFrame(img0 ?: (if (img1 == null) last[0] else null), aspect, Modifier.fillMaxSize().graphicsLayer {
            val d = past()
            rotationY = d
            cameraDistance = 14f * dens
            alpha = if (img1 == null) 1f else (1f - ((d - 7f) / 6f)).coerceIn(0f, 1f)
        })
        InspectFrame(img1, aspect, Modifier.fillMaxSize().graphicsLayer {
            val d = past()
            rotationY = d - 20f
            cameraDistance = 14f * dens
            alpha = if (img0 == null) 1f else ((d - 7f) / 6f).coerceIn(0f, 1f)
        })
    }
}

/** Update 166: one of the model's pictures, fitted in the middle (its turn and fade set by [modifier]'s layer). */
@Composable
private fun InspectFrame(img: ImageBitmap?, aspect: Float, modifier: Modifier) {
    if (img == null) return
    Spacer(
        modifier.drawWithCache {
            val fw = minOf(size.width, size.height * aspect)
            val fh = fw / aspect
            val dstOff = IntOffset(((size.width - fw) / 2f).roundToInt(), ((size.height - fh) / 2f).roundToInt())
            val dstSize = IntSize(fw.roundToInt().coerceAtLeast(1), fh.roundToInt().coerceAtLeast(1))
            onDrawBehind { drawImage(img, dstOffset = dstOff, dstSize = dstSize) }
        }
    )
}
