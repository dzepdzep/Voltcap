package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.cardArtFor
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/*
 * Update 151: "Starting a game" (Settings › Home › Animations): when a game drawn as the real thing starts, it goes in
 * before the emulator opens, from where it sits on the home screen (GameOrigin, marked by the game you're on):
 *  - a case (Real cases, Cases · real sizes, the 3D cases): its front opens like a door and the disc (or cartridge)
 *    inside flies down and out through the bottom of the screen, spinning;
 *  - a cartridge (Cartridges & discs, Cartridges, the 3D cartridges): it's thrown down spinning and slots into the
 *    bottom edge, as if into a console under the screen, with a small bump;
 *  - a disc (Discs only — every game a disc there — and the disc games of Cartridges & discs): it spins and drops into
 *    a tray at the bottom edge.
 * Covers, capsules and the other looks keep the launch animation (LaunchAnimation.kt) or none. About 0.8 s, always
 * under a second; it ends on black so the home screen never flashes up before the game. Without the game's place on
 * screen or its art, with the system's animations off, or on another screen, the game starts straight away.
 * Drawn above the home screen by LauncherScreen; the clock is read only while drawing (nothing recomposes per frame).
 */

internal enum class ThrowKind(val totalMs: Int) { CASE(860), CART(820), DISC(800) }

/** Everything the animation draws, worked out once when the game starts. */
internal class ThrowPlan(
    val game: Game,
    val kind: ThrowKind,
    /** Where the game sits (window px) and on which screen. */
    val from: Rect,
    val displayId: Int,
    val cover: String?,
    /** CASE: the game's own case front scan (as the card has it). */
    val caseScan: String?,
    /** CASE drawn as a 3D piece of the Cartridges & discs look (your 3D case): that piece is the door. */
    val doorPick: MediaPick?,
    /** The cartridge / disc that's thrown (CASE: the one inside); null with [realScan]. */
    val pick: MediaPick?,
    val label: Any?,
    val labelIsScan: Boolean,
    /** The game's own cartridge / disc picture (Real discs) instead of a drawn one. */
    val realScan: String?,
    val round: Boolean,
    /** Cases · real sizes: the biggest case shown, so the case is drawn at its real size like on the card. */
    val real: RealCaseRef?,
    val casePlatform: String?,
    /** The case's width / height. */
    val caseAspect: Float,
    /** The piece's own width / height (to size it at the bottom of the screen). */
    val pieceAspect: Float
)

internal object LaunchThrow {
    /** The game going in now (LauncherScreen draws [LaunchThrowOverlay] while it's set, and starts the game after). */
    var plan by mutableStateOf<ThrowPlan?>(null)

    /**
     * The animation for [game] as the home screen draws it, or null when it has none (another look, the setting off,
     * its place on screen unknown, nothing to draw). [focusedKey]: the game you're on.
     */
    fun planFor(game: Game, state: LauncherUiState, focusedKey: String?): ThrowPlan? {
        if (Features.launchThrow.value != Features.LaunchThrow.THROW) return null
        val rect = GameOrigin.rect ?: return null
        val key = GameOrigin.key
        if (key != game.stableKey && !(key == GameOrigin.FOCUSED && focusedKey == game.stableKey)) return null
        if (rect.width < 8f || rect.height < 8f || rect.bottom <= 0f) return null
        val a = state.appearance
        val chosen = a.cardArtFor()
        val type = artForGame(chosen, game.platform, a.hiddenMedia)
        val k = game.stableKey
        val physical = state.physicalMedia[k]
        val cover = state.artOverrides[k]?.cover ?: state.art[k]?.gridUrl
        val wide = state.artOverrides[k]?.wide ?: state.art[k]?.wideGridUrl
        val discLabel = physical?.firstOrNull { it.first == "Disc label" }?.second
        val ownMedia = if (!Features.mediaShells.value) BoxTexture.media(physical) else null
        // Update 154: Disc station — every game is a disc there, whatever the game look: it drops into the tray.
        if (a.homeLayout == com.shelf.launcher.data.HomeLayout.DISCS) {
            val pick = mediaFor(game.platform, asDisc = true)
                ?.takeIf { it.shell.isDisc || it.shell == MediaShell.UMD || it.platform.startsWith(GENERIC_DISC) }
                ?: MediaPick(MediaShell.DISC, genericDiscKey(if (game.platform == "App") "Android" else game.platform))
            val typed = typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, game.platform)
            val label = typed ?: cover ?: wide
            return ThrowPlan(
                game, ThrowKind.DISC, rect, GameOrigin.displayId, cover, null, null, pick, label,
                typed != null && label == typed, null, true, null, null, 1f, 1f
            )
        }
        return when (type) {
            CardArtType.CASE -> {
                if (caseMarker(game.platform) == null) return null
                val shell = MediaShell.of(game.platform) ?: return null
                val scan = typedArtFor(CardArtType.CASE, physical, null, game.platform)
                if (cover == null && scan == null) return null
                val casePlatform = LookStyles.casePlatform(game.platform)
                // (The case looks never stand a 3D model case, see CaseArt.)
                val aspect = realCaseMm(casePlatform)?.let { it.first / it.second } ?: 0.7f
                ThrowPlan(
                    game, ThrowKind.CASE, rect, GameOrigin.displayId, cover, scan, null,
                    MediaPick(shell, game.platform), discLabel ?: cover, discLabel != null, ownMedia,
                    shell.disc || shell == MediaShell.UMD,
                    if (chosen == CardArtType.CASE_REAL) realCaseRef(CardArtType.CASE_REAL, state.visibleGames) else null,
                    casePlatform, aspect.coerceIn(0.3f, 3f), shell.aspect
                )
            }
            CardArtType.CARTRIDGE_DISC -> {
                val asDisc = chosen == CardArtType.DISC
                val typed = typedArtFor(CardArtType.CARTRIDGE_DISC, physical, null, game.platform)
                val drawn = drawnMediaWanted(type, typed)
                val realScan = if (drawn) null else realScanOf(type, typed)
                val pick = if (drawn) mediaFor(game.platform, false, asDisc) else null
                if (pick == null && realScan == null) return null
                val label = typed ?: cover ?: wide
                if (label == null && realScan == null) return null
                val round = if (pick != null) pick.shell.disc || pick.shell == MediaShell.UMD else mediaRound(game.platform)
                val modelPick = pick?.takeIf { it.platform.startsWith(MODEL_3D) }
                val model = modelPick?.platform?.removePrefix(MODEL_3D)?.substringBefore('|')
                val inside = MediaShell.of(game.platform)
                if (modelPick != null && model != null && model.startsWith("case_") && inside != null) {
                    // Your 3D case: it opens and its disc flies out.
                    ThrowPlan(
                        game, ThrowKind.CASE, rect, GameOrigin.displayId, cover, null, modelPick,
                        MediaPick(inside, game.platform), discLabel ?: cover, discLabel != null, ownMedia,
                        inside.disc || inside == MediaShell.UMD, null, game.platform,
                        modelPick.aspect.coerceIn(0.3f, 3f), inside.aspect
                    )
                } else {
                    val kind = when {
                        asDisc -> ThrowKind.DISC
                        model != null -> ThrowKind.CART
                        pick != null && pick.platform.startsWith(CUSTOM_CART) -> ThrowKind.CART
                        round -> ThrowKind.DISC
                        else -> ThrowKind.CART
                    }
                    ThrowPlan(
                        game, kind, rect, GameOrigin.displayId, cover, null, null, pick, label,
                        typed != null && label == typed, realScan, round, null, null, 1f,
                        if (round) 1f else (pick?.aspect ?: 1f).coerceIn(0.3f, 3f)
                    )
                }
            }
            else -> null
        }
    }
}

private fun ph(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun eOut3(x: Float) = 1f - (1f - x).pow(3)
private fun eIn2(x: Float) = x * x
private fun eInOut2(x: Float) = if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f
private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k

/**
 * Update 151: the animation for [plan]; [onDone] starts the game — called once, at the end, or right away when it
 * can't play here (system animations off, the game was picked on the other screen).
 */
@Composable
internal fun LaunchThrowOverlay(plan: ThrowPlan, onDone: () -> Unit) {
    com.shelf.launcher.Breadcrumbs.mark("LaunchThrow")
    val done by rememberUpdatedState(onDone)
    val view = LocalView.current
    val here = remember(view) { runCatching { view.display?.displayId }.getOrNull() ?: -1 }
    val sameScreen = plan.displayId < 0 || here < 0 || plan.displayId == here
    // (Update 151: always asked, not behind the &&, so the composable call never comes and goes.)
    val animsOn = rememberAnimationsEnabled()
    val run = sameScreen && animsOn
    val clock = remember(plan) { Animatable(0f) }
    LaunchedEffect(plan) {
        if (run) clock.animateTo(plan.kind.totalMs.toFloat(), tween(plan.kind.totalMs, easing = LinearEasing))
        done()
    }
    if (run) ThrowScene(plan, clock)
}

/** The piece as the card draws it, alone (a disc never in its case here: it's the disc that goes in). */
@Composable
private fun ThrownPiece(p: ThrowPlan, pick: MediaPick?, label: Any?, labelIsScan: Boolean, modifier: Modifier) {
    if (pick == null) {
        if (p.realScan != null) RealMediaPicture(p.realScan, p.round, modifier)
    } else {
        val own = pick.platform.startsWith(MODEL_3D) || pick.platform.startsWith(CUSTOM_CART)
        val tpl = if (own) null else MediaTemplates.of(pick.platform)
        if (tpl?.disc != null && discCaseOn(tpl)) TemplateMedia(
            MediaTpl(null, null, tpl.disc, null), labelScan = if (labelIsScan) label else null,
            cover = if (labelIsScan) null else label, modifier = modifier,
            smallDisc = pick.shell == MediaShell.MINI_DISC && !Features.bigGcDiscs.state.value
        )
        else PhysicalMedia(pick.shell, label, modifier, platform = pick.platform, labelIsScan = labelIsScan, caseCover = p.cover, title = p.game.name)
    }
}

@Composable
private fun ThrowScene(p: ThrowPlan, clock: Animatable<Float, AnimationVector1D>) {
    val density = LocalDensity.current
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    // Where this overlay sits in the window (the game's place is in window px).
    val win = remember { FloatArray(2) }
    val total = p.kind.totalMs.toFloat()
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { c -> val o = c.positionInWindow(); win[0] = o.x; win[1] = o.y }
    ) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val px = density.density
        // The home screen dims behind the piece.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black, alpha = 0.62f * eOut3(ph(clock.value, 0f, 220f)))
        }
        val w = p.from.width
        val h = p.from.height
        val wDp = with(density) { w.toDp() }
        val hDp = with(density) { h.toDp() }
        if (p.kind == ThrowKind.CASE) {
            CaseScene(p, clock, sw, sh, px, win, accent)
        } else {
            val disc = p.kind == ThrowKind.DISC
            val pad = if (p.round) 6f * px else 10f * px
            val innerW = (w - 2 * pad).coerceAtLeast(1f)
            val innerH = (h - 2 * pad).coerceAtLeast(1f)
            // The piece's own height on the card, and the height it ends up at the bottom of the screen.
            val pieceH = minOf(innerH, innerW / p.pieceAspect).coerceAtLeast(1f)
            val endH = if (disc) minOf(sh * 0.42f, sw * 0.36f) else minOf(sh * 0.36f, sw * 0.3f / p.pieceAspect.coerceAtLeast(0.3f))
            val endScale = (endH / pieceH).coerceIn(0.5f, 2.6f)
            val slotW = pieceH * endScale * p.pieceAspect * 1.25f + 24f * px
            val slotH = (if (disc) 16f else 14f) * px
            // Lift-off, flight, into the slot / tray (ms).
            val liftEnd = if (disc) 120f else 140f
            val flyEnd = 560f
            val inEnd = if (disc) 700f else 650f
            Box(
                Modifier.size(wDp, hDp).graphicsLayer {
                    val t = clock.value
                    val cx0 = p.from.left - win[0] + w / 2f
                    val cy0 = p.from.top - win[1] + h / 2f
                    val lift = eOut3(ph(t, 0f, liftEnd))
                    val u = ph(t, liftEnd, flyEnd)
                    val v = ph(t, flyEnd, inEnd)
                    // The piece's height at the end.
                    val shown = pieceH * endScale
                    val restY = if (disc) sh - shown * 0.5f + shown * 0.12f else sh - shown * 0.42f + shown * 0.5f
                    val goneY = if (disc) sh + shown * 0.62f else restY + shown * 0.16f
                    val x = lerp(cx0, sw / 2f, eInOut2(u))
                    // Up a little as it's picked up, then down — a throw, gravity pulling it faster.
                    var y = lerp(cy0 - h * 0.06f * lift, restY, eIn2(u)) - h * 0.12f * sin(PI.toFloat() * u) * (1f - u)
                    if (disc) y = lerp(y, goneY, eIn2(v))
                    else {
                        y = lerp(y, goneY, eOut3(v))
                        // The bump as it clicks in.
                        y += 5f * px * sin(PI.toFloat() * ph(t, flyEnd, flyEnd + 90f))
                    }
                    val k = lerp(1f + (if (disc) 0.08f else 0.1f) * lift, endScale, eOut3(u))
                    translationX = x - w / 2f
                    translationY = y - h / 2f
                    scaleX = k
                    scaleY = k * (if (disc) 1f else 1f - 0.05f * sin(PI.toFloat() * ph(t, flyEnd, flyEnd + 90f)))
                    rotationZ = if (disc) 1080f * ph(t, 0f, inEnd).pow(1.4f) else 720f * eOut3(u)
                }
            ) {
                ThrownPiece(p, p.pick, p.label, p.labelIsScan, Modifier.fillMaxSize().padding(with(density) { pad.toDp() }))
            }
            // The slot (cartridge) or tray (disc) at the bottom edge, over the piece so it goes in.
            val dark = remember { Brush.verticalGradient(listOf(Color(0xFF1C1E27), Color(0xFF07080B))) }
            val glowH = 30f * px
            val glow = remember(sh, slotH, accent) {
                Brush.verticalGradient(listOf(Color.Transparent, accent), startY = sh - slotH - glowH, endY = sh - slotH)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                val appear = eOut3(ph(t, 160f, 380f))
                if (appear > 0f) {
                    val bump = if (disc) 0f else 3f * px * sin(PI.toFloat() * ph(t, flyEnd, flyEnd + 120f))
                    val flash = if (t >= flyEnd) 1f - ph(t, flyEnd, flyEnd + 240f) else 0f
                    val top = sh - slotH * appear + bump
                    val left = (sw - slotW) / 2f
                    // Light from the slot.
                    drawRect(glow, Offset(left, top - glowH), Size(slotW, glowH), alpha = (0.18f + 0.4f * flash) * appear)
                    drawRoundRect(dark, Offset(left, top), Size(slotW, slotH + 8f * px), CornerRadius(6f * px), alpha = appear)
                    // Its lip.
                    drawRect(accent, Offset(left + 6f * px, top), Size(slotW - 12f * px, 2f * px), alpha = (0.55f + 0.45f * flash) * appear)
                    // The tray's opening.
                    if (disc) drawRect(Color.Black, Offset(left + 10f * px, top + 5f * px), Size(slotW - 20f * px, 3f * px), alpha = 0.8f * appear)
                }
            }
        }
        // It all ends on black: the game comes up from black.
        Canvas(Modifier.fillMaxSize()) {
            val b = ph(clock.value, total - 170f, total)
            if (b > 0f) drawRect(Color.Black, alpha = b)
        }
    }
}

/** The case opens like a door; the disc (or cartridge) inside flies down out of the bottom of the screen, spinning. */
@Composable
private fun CaseScene(
    p: ThrowPlan, clock: Animatable<Float, AnimationVector1D>, sw: Float, sh: Float, px: Float, win: FloatArray, accent: Color
) {
    val density = LocalDensity.current
    // The case's place inside the card (as CaseArt fits it): its real size standing on the floor, else fitted, centred.
    val pad = 4f * px
    val innerW = (p.from.width - 2 * pad).coerceAtLeast(1f)
    val innerH = (p.from.height - 2 * pad).coerceAtLeast(1f)
    val mm = if (p.real != null) realCaseMm(p.casePlatform) else null
    val cw: Float
    val ch: Float
    if (p.real != null && mm != null) {
        val k = minOf(innerW / p.real.wMm, innerH / p.real.hMm)
        cw = k * mm.first; ch = k * mm.second
    } else {
        cw = minOf(innerW, innerH * p.caseAspect); ch = cw / p.caseAspect
    }
    val bottomStand = p.real != null && mm != null
    // The disc / cartridge inside: most of the case.
    val inAspect = if (p.round) 1f else p.pieceAspect.coerceIn(0.3f, 3f)
    val dh = minOf(ch * (if (p.round) 0.78f else 0.62f), cw * 0.86f / inAspect)
    val dw = dh * inAspect
    val trayFill = remember { Brush.verticalGradient(listOf(Color(0xFF2A2D36), Color(0xFF15161C))) }
    Box(
        Modifier.size(with(density) { cw.toDp() }, with(density) { ch.toDp() }).graphicsLayer {
            val t = clock.value
            val left = p.from.left - win[0] + pad + (innerW - cw) / 2f
            val top = p.from.top - win[1] + pad + (if (bottomStand) innerH - ch else (innerH - ch) / 2f)
            translationX = left
            translationY = top - ch * 0.03f * eOut3(ph(t, 0f, 120f))
        }
    ) {
        // The inside of the case: its tray, with the hub the disc sits on.
        Canvas(Modifier.fillMaxSize()) {
            val a = 1f - ph(clock.value, 600f, 780f)
            if (a > 0f) {
                drawRoundRect(trayFill, cornerRadius = CornerRadius(3f * px), alpha = a)
                drawRoundRect(Color.White, cornerRadius = CornerRadius(3f * px), alpha = 0.10f * a, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * px))
                if (p.round) {
                    drawCircle(Color(0xFF0C0D11), radius = dh * 0.16f, alpha = a)
                    drawCircle(accent, radius = dh * 0.16f, alpha = 0.35f * a, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * px))
                }
            }
        }
        // The disc: rises off its hub as the door opens, then flies down out of the screen, spinning.
        Box(
            Modifier.align(Alignment.Center).size(with(density) { dw.toDp() }, with(density) { dh.toDp() }).graphicsLayer {
                val t = clock.value
                val caseTop = p.from.top - win[1] + pad + (if (bottomStand) innerH - ch else (innerH - ch) / 2f)
                val cy0 = caseTop + ch / 2f
                val cx0 = p.from.left - win[0] + pad + innerW / 2f
                val rise = eOut3(ph(t, 330f, 440f))
                val u = ph(t, 440f, 800f)
                val k = 1f + 0.08f * rise + 0.3f * eOut3(u)
                // Well past the bottom edge (turned, a cartridge reaches further than half its height).
                val endY = sh + maxOf(dw, dh) * k * 0.75f + ch * 0.03f
                translationY = -ch * 0.04f * rise + (endY - cy0) * eIn2(u)
                translationX = (sw / 2f - cx0) * 0.35f * eInOut2(u)
                scaleX = k; scaleY = k
                rotationZ = (if (p.round) 900f else 540f) * ph(t, 330f, 800f).pow(1.3f)
            }
        ) {
            ThrownPiece(p, p.pick, p.label, p.labelIsScan, Modifier.fillMaxSize())
        }
        // The front: it swings open on its left edge, like a door.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val t = clock.value
                val angle = 105f * eOut3(ph(t, 120f, 430f))
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = -angle
                cameraDistance = 10f * density.density
                alpha = (1f - ph(angle, 75f, 105f)) * (1f - ph(t, 600f, 760f))
            }
        ) {
            if (p.doorPick != null) ThrownPiece(p, p.doorPick, p.cover, false, Modifier.fillMaxSize())
            else CaseArt(p.game.platform, p.cover, p.caseScan, Modifier.fillMaxSize(), title = gameDisplayName(p.game.name))
        }
    }
}
