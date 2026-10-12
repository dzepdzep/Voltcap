package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Update 146: Case stacks — a disc-picker style home in Voltcap's own look.
 *
 * A softly lit room (a faint floor grid running back to the horizon in the theme's colour, a cone of light on the
 * middle). The game you're on stands big in the middle as its case, facing you, its name above it. The other games
 * stand edge-on as two stacks of spines: the ones before it on the left, the next ones on the right, each stack going
 * back into the room in perspective (Stacks in perspective; off: flat, straight on), with a mirror floor under them
 * (Mirror floor). Moving to the next game slides its spine out of the right-hand stack, turns it to face you and grows
 * it into the middle while the old one turns back to its spine and joins the left-hand stack — one glide at the
 * Moving between games speed. A bar of round buttons along the bottom (Favourite, Options, Details, Play).
 *
 * D-pad: ◀ ▶ previous / next game (the home screen's own), ▼ the button bar (◀ ▶ along it, A presses, ▲ / B back),
 * ▲ what lies above (status bar / player). Touch: tap a spine to pick that game, tap the case in the middle to open it,
 * swipe sideways to move along the stacks.
 */

/** Spine width / height in the stacks (a little slimmer than the Spines look's cards, so the stacks hold more games). */
private const val STACKS_SPINE = 0.1f

/** A spine in a stack stands this tall against the big case in the middle. */
private const val STACKS_SPINE_K = 0.84f

/** How fast the stacks shrink going back into the room (per spine), with Stacks in perspective on. */
private const val STACKS_DEPTH_K = 0.075f

/** How far each stack is turned toward the middle (degrees), with Stacks in perspective on. */
private const val STACKS_TILT = 18f

/** At most this many spines per stack (the far ones are tiny anyway; fewer to draw on weak handhelds). */
private const val STACKS_MAX_SIDE = 28

/** How far the reflection reaches under a case, as a share of its height. */
private const val STACKS_MIRROR = 0.42f

/** Update 151: how far the 3D case in the middle is turned (degrees), so its spine shows on the left. */
private const val STACKS_CASE_YAW = 24f

@Composable
internal fun StyleCaseStacks(c: StyleCtx, modifier: Modifier = Modifier) {
    val g0 = c.game
    // A button bar hidden in Edit Home (or switched off) drops out of the D-pad's way: ▼ then reaches what lies below.
    val barOn = Features.stacksBar.state.value && !LayoutParts.off(HomeLayout.STACKS, "bar")
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼: Favourite, Options, Details and Play, in the order they're drawn.
        StyleNav.bar = if (g0 == null || !barOn) emptyList() else listOf(
            { c.actions.favorite(g0) }, { c.actions.options(g0) }, { c.actions.open() }, { c.actions.play(g0) }
        )
    }
    // The spines of the games round the one you're on (StyleHome only asks for them on the Game shelf): the nearest
    // first, a short pause between each so moving along doesn't flood the look-ups.
    val games = c.games
    val needSpine by rememberUpdatedState(c.actions.needSpine)
    LaunchedEffect(c.focus, games.size) {
        kotlinx.coroutines.delay(150)
        // (Update 156: with Endless on the stacks go on round the ends, so their spines are looked up too, each once.)
        val loop = loopRowOn(c)
        val seen = HashSet<Int>()
        for (i in ((c.focus - STACKS_MAX_SIDE)..(c.focus + STACKS_MAX_SIDE)).sortedBy { abs(it - c.focus) }) {
            val at = if (loop) i.mod(games.size) else i
            if (!seen.add(at)) continue
            val g = games.getOrNull(at) ?: continue
            val list = c.state.physicalMedia[g.stableKey]
            if (list == null || (BoxTexture.spine(list, g.platform) == null && abs(i - c.focus) <= 10)) {
                needSpine(g)
                kotlinx.coroutines.delay(60)
            }
        }
    }
    // Plain if / else, never an early return out of the layout (it unbalances Compose's groups).
    if (g0 == null) StyleEmpty(modifier) else StacksBody(c, g0, barOn, modifier)
}

@Composable
private fun StacksBody(c: StyleCtx, g: Game, barOn: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 380.dp
        val nameH = if (compact) 42.dp else 56.dp
        val barH = if (barOn) (if (compact) 62.dp else 76.dp) else 0.dp
        val stageH = (maxHeight - nameH - barH).coerceAtLeast(110.dp)
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(nameH).padding(horizontal = 16.dp)
                    .editablePart(LayoutParts.id(HomeLayout.STACKS, "name"), "Name"),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = g, label = "stacksName", transitionSpec = { gameSwap() }, contentAlignment = Alignment.BottomCenter
                ) { shown -> StacksName(c, shown, compact) }
            }
            StacksStage(c, Modifier.fillMaxWidth().height(stageH))
            if (barOn) Box(
                Modifier.fillMaxWidth().height(barH).editablePart(LayoutParts.id(HomeLayout.STACKS, "bar"), "Button bar"),
                contentAlignment = Alignment.Center
            ) { StacksBar(c, g, compact) }
        }
    }
}

/** The game's name over the case, and a line under it: its console and the time played. */
@Composable
private fun StacksName(c: StyleCtx, g: Game, compact: Boolean) {
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(
            titles.display(g.name), Color.White, maxSize = if (compact) 16.sp else 21.sp, minSize = 11.sp, maxLines = 1,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Text(
            titles.console(g.platform) + "  ·  " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
            color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, softWrap = false,
            style = nameTextStyle(NameBackdrop.SHADOW)
        )
    }
}

/**
 * Where everything stands on the stage, in px (worked out once per screen size / option, read every frame of a glide).
 * [r] everywhere is a game's place against the one in the middle: 0 = in the middle, 1 = the first spine of the right
 * stack, −1 the first of the left one, fractions while it glides.
 */
private class StacksGeo(
    val caseW: Dp, val caseH: Dp, val spineW: Dp,
    val caseWPx: Float, val caseHPx: Float, val spineWPx: Float,
    /** The middle of the stage, the floor line and the horizon the stacks run back to. */
    val cx: Float, val floorY: Float, val horizonY: Float,
    /** From the middle to the first spine's middle; one spine's step at the front of a stack. */
    val x0: Float, val pitch: Float,
    val depthK: Float, val tiltDeg: Float, val nSide: Int
) {
    private fun depthScale(d: Float): Float = if (depthK > 0f) 1f / (1f + depthK * d) else 1f

    /** How far along its stack spine [d] (0 = the first) stands: the steps shrink going back, like things far away. */
    private fun along(d: Float): Float = if (depthK > 0f) pitch * ln(1f + depthK * d) / depthK else pitch * d

    /** 0 = facing you … 1 = edge-on (its spine showing), eased so the turn starts and ends softly. */
    fun turn(r: Float): Float {
        val t = abs(r).coerceAtMost(1f)
        return t * t * (3f - 2f * t)
    }

    fun x(r: Float): Float {
        val a = abs(r)
        val side = if (r < 0f) -1f else 1f
        return if (a < 1f) cx + side * x0 * a else cx + side * (x0 + along(a - 1f))
    }

    fun scale(r: Float): Float {
        val a = abs(r)
        return if (a < 1f) 1f + (STACKS_SPINE_K - 1f) * a else STACKS_SPINE_K * depthScale(a - 1f)
    }

    /** Where its foot stands: on the floor line, or higher up toward the horizon further back. */
    fun base(r: Float): Float {
        val a = abs(r)
        return if (a < 1f || depthK <= 0f) floorY else horizonY + (floorY - horizonY) * depthScale(a - 1f)
    }

    fun tilt(r: Float): Float = (if (r < 0f) -1f else 1f) * tiltDeg * abs(r).coerceAtMost(1f)

    /** The last spine or so of each stack fades out instead of stopping hard. */
    fun alpha(r: Float): Float = (nSide + 1f - abs(r)).coerceIn(0f, 1f)
}

/** The stage: the room's floor, the two stacks and the case in the middle; swipe to move along. */
@Composable
private fun StacksStage(c: StyleCtx, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val keys = remember(c.games) { gameKeys(c.games) }
    val depth = Features.stacksDepth.state.value
    val mirror = Features.stacksMirror.state.value
    val lite = LocalLiteEffects.current
    val glide = Features.rowGlide.state.value
    val animate = rememberAnimationsEnabled()
    val accent = ShelfTheme.accent
    // Update 156: with Endless on the games repeat in both stacks and the place you're on counts on (EndlessRow.kt), so
    // going past the last game to the first slides the next spine out like any step. Off: just the game you're on.
    val loop = loopRowOn(c)
    val vFocus = rememberLoopFocus(c)
    // Where the stage is (in games): glides from game to game at the Moving between games speed, so the spine coming out,
    // its turn and its growing all follow one smooth motion (a held D-pad carries on from where it is, no restarts).
    val pos = remember { Animatable(vFocus.toFloat()) }
    // Update 146: which list the stage was showing, so a new category (other games, maybe with the game you're on near
    // the old place) jumps straight to its game instead of gliding through the new list's spines.
    val firstKey = keys.firstOrNull()
    val shownList = remember { arrayOfNulls<Any>(2) }
    LaunchedEffect(vFocus, count, firstKey) {
        val target = vFocus.toFloat()
        val newList = shownList[0] != firstKey || shownList[1] != count
        shownList[0] = firstKey
        shownList[1] = count
        // A long jump (a new category, the first / last game) goes straight there instead of racing past dozens of spines.
        if (!animate || newList || abs(pos.value - target) > 12f) pos.snapTo(target)
        else pos.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
    }
    // Read where things are drawn, not while composing: a glide moves the spines without rebuilding them every frame.
    // Only these change during a glide: the game left of the middle, whether it is between two games, and which of the
    // two is nearer the middle (that one is drawn on top, so the case coming in isn't hidden behind the one leaving).
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val between by remember { derivedStateOf { pos.value - floor(pos.value) > 0.001f } }
    val past by remember { derivedStateOf { pos.value - floor(pos.value) > 0.5f } }
    // A tap brings the D-pad back to the games too (as on the Library), then picks / opens the game.
    val tap: (Int) -> Unit = remember {
        { i: Int ->
            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
            if (i == GridHome.focus) open() else select(i)
        }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds().categorySlide()) {
        val w = maxWidth
        val h = maxHeight
        val k = GameSize.scale(HomeLayout.STACKS)
        // Room under the floor line for the reflection (or just a little air without it).
        val under = if (mirror) (h * 0.17f).coerceIn(14.dp, 90.dp) else 10.dp
        val floorAt = h - under
        val caseLook = stacksCaseLook(c.type)
        val aspect = if (caseLook) 0.72f else styleAspect(c.type).coerceIn(0.6f, 1.8f)
        var caseH = ((floorAt - 8.dp) * 0.94f * k).coerceIn(60.dp, (floorAt - 4.dp).coerceAtLeast(60.dp))
        var caseW = caseH * aspect
        // Small screens / wide art: never more than about two fifths of the width, so both stacks keep room.
        val maxCaseW = w * 0.42f
        if (caseW > maxCaseW) { caseW = maxCaseW; caseH = caseW / aspect }
        val spineW = caseH * STACKS_SPINE
        val gapSide = (caseW * 0.12f).coerceIn(12.dp, 40.dp)
        val x0 = caseW / 2 + gapSide + spineW * STACKS_SPINE_K / 2
        val pitch = spineW * STACKS_SPINE_K + 1.5.dp
        val avail = (w / 2 - x0 - 4.dp).coerceAtLeast(0.dp)
        val depthK = if (depth) STACKS_DEPTH_K else 0f
        // How many spines fit in each stack before the screen's edge.
        // (Capped before it becomes an Int, so a huge screen / tiny case can't overflow it.)
        val nSide = if (depthK > 0f) {
            ((exp(depthK * (avail / pitch)) - 1f) / depthK).coerceIn(0f, 1000f).toInt() + 2
        } else (avail / pitch).coerceIn(0f, 1000f).toInt() + 2
        val geo = remember(w, h, caseW, caseH, depthK, density, mirror) {
            with(density) {
                StacksGeo(
                    caseW, caseH, spineW, caseW.toPx(), caseH.toPx(), spineW.toPx(),
                    cx = (w / 2).toPx(), floorY = floorAt.toPx(), horizonY = (floorAt - caseH * 0.55f).toPx(),
                    x0 = x0.toPx(), pitch = pitch.toPx(), depthK = depthK, tiltDeg = if (depthK > 0f) STACKS_TILT else 0f,
                    nSide = nSide.coerceIn(1, STACKS_MAX_SIDE)
                )
            }
        }
        val stepPx by rememberUpdatedState(with(density) { (pitch * 1.6f).toPx() })

        // The room: soft light from above, the floor grid in the theme's colour, a pool of light under the middle.
        StacksRoom(geo, accent, Modifier.fillMaxSize())

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Swipe along the stacks: a game per step (a swipe to the left brings the next ones in from the right).
                var acc = 0f
                detectHorizontalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
                    change.consume()
                    acc += dx
                    val n = latestCount
                    if (n > 0) {
                        var target = latestFocus
                        while (acc <= -stepPx) { acc += stepPx; target = (target + 1).coerceAtMost(n - 1) }
                        while (acc >= stepPx) { acc -= stepPx; target = (target - 1).coerceAtLeast(0) }
                        if (target != latestFocus) {
                            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
                            select(target)
                        }
                    }
                }
            }
        ) {
            // (Update 156: on the endless row these are places on it; [real] is the game standing there, one key per copy.)
            val first = if (loop) lo - geo.nSide - 1 else (lo - geo.nSide - 1).coerceAtLeast(0)
            val last = if (loop) lo + geo.nSide + 2 else (lo + geo.nSide + 2).coerceAtMost(count - 1)
            val inGames = StyleNav.zone == StyleNav.Zone.GAMES
            for (i in first..last) {
                val real = if (loop) i.mod(count) else i
                key(if (loop) "${keys[real]}#${Math.floorDiv(i, count)}" else keys[i]) {
                    // The front only for the game(s) in the middle: the one there, and the one coming in while it glides.
                    val near = i == lo || (between && i == lo + 1)
                    // In the middle the nearer of the two (going / coming) is on top; the stacks behind, the far spines last.
                    val dist = if (near) (if ((i == lo) == !past) 0 else 1) else abs(i - lo)
                    StacksItem(
                        c, i, geo, { pos.value }, near = near,
                        // Edge-on exactly in the middle the spine is a sliver: it takes no taps then (the case does).
                        spineTaps = !(i == lo && !between),
                        mirror = mirror && (near || (!lite && dist <= 12) || (lite && dist <= 3)),
                        focused = i == vFocus && inGames, tap = tap,
                        modifier = Modifier.zIndex(if (near) 1000f - dist else -dist.toFloat()),
                        game = real, centre = i == vFocus
                    )
                }
            }
        }
    }
}

/** The layout's card artwork is a case look (box art, or the plain cover / spines, which stand as a case here). */
private fun stacksCaseLook(type: CardArtType): Boolean =
    type == CardArtType.CASE || type == CardArtType.CASE_REAL || type == CardArtType.COVER

/**
 * One game on the stage: a box with its spine and its front meeting at a corner. Edge-on (in a stack) only the spine
 * shows; turning, both do; in the middle only the front. Everything that moves is read from [pos] while drawing.
 */
@Composable
private fun StacksItem(
    c: StyleCtx, i: Int, geo: StacksGeo, pos: () -> Float, near: Boolean, spineTaps: Boolean, mirror: Boolean,
    focused: Boolean, tap: (Int) -> Unit, modifier: Modifier,
    // Update 156: [i] is the place on the stage (it keeps counting on the endless row), [game] the game standing there,
    // [centre] whether it is the place you're on.
    game: Int = i, centre: Boolean = i == c.focus
) {
    val g = c.games[game]
    val onTap by rememberUpdatedState(tap)
    Box(
        modifier
            .size(geo.caseW, geo.caseH)
            .graphicsLayer {
                val r = i - pos()
                val s = geo.scale(r)
                translationX = geo.x(r) - geo.caseWPx / 2f
                translationY = geo.base(r) - geo.caseHPx
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
                rotationY = geo.tilt(r)
                cameraDistance = 12f * density
                alpha = geo.alpha(r)
            }
            .stacksMirror(mirror)
    ) {
        // The spine face (always there: the stacks are made of it).
        Box(
            Modifier
                .size(geo.spineW, geo.caseH)
                .graphicsLayer {
                    val r = i - pos()
                    val th = geo.turn(r) * 90f
                    val rad = Math.toRadians(th.toDouble())
                    val cs = cos(rad).toFloat()
                    val sn = sin(rad).toFloat()
                    val left0 = (geo.caseWPx - (geo.caseWPx * cs + geo.spineWPx * sn)) / 2f
                    if (r >= 0f) {
                        // Coming from (or going to) the right-hand stack: the spine on the left of the corner.
                        translationX = left0 + geo.spineWPx * sn - geo.spineWPx
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        rotationY = -(90f - th)
                    } else {
                        translationX = left0 + geo.caseWPx * cs
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = 90f - th
                    }
                    cameraDistance = 12f * density
                    alpha = if (th < 1f) 0f else 1f
                }
                .then(if (spineTaps) Modifier.pointerInput(game) { detectTapGestures { onTap(game) } } else Modifier)
        ) {
            GameSpine(g, c.state.physicalMedia[g.stableKey], Modifier.fillMaxSize())
        }
        // The front face: only while it is in (or coming into / leaving) the middle.
        if (near) Box(
            Modifier
                .size(geo.caseW, geo.caseH)
                .graphicsLayer {
                    val r = i - pos()
                    val th = geo.turn(r) * 90f
                    val rad = Math.toRadians(th.toDouble())
                    val cs = cos(rad).toFloat()
                    val sn = sin(rad).toFloat()
                    val left0 = (geo.caseWPx - (geo.caseWPx * cs + geo.spineWPx * sn)) / 2f
                    if (r >= 0f) {
                        translationX = left0 + geo.spineWPx * sn
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = th
                    } else {
                        translationX = left0 + geo.caseWPx * cs - geo.caseWPx
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        rotationY = -th
                    }
                    cameraDistance = 12f * density
                    alpha = if (th > 89f) 0f else 1f
                }
                .then(if (centre) Modifier.focusedCard() else Modifier)
                .pointerInput(game) { detectTapGestures { onTap(game) } }
        ) {
            StacksFront(c, g, focused, Modifier.fillMaxSize())
        }
    }
}

/**
 * The game facing you: its case (the console's case, or the game's own scanned one) for the case looks; the layout's
 * own artwork for the others. The focus ring in the accent colour while the D-pad is on the games.
 */
@Composable
private fun StacksFront(c: StyleCtx, g: Game, focused: Boolean, modifier: Modifier) {
    val k = g.stableKey
    val on = k == c.game?.stableKey
    val accent = ShelfTheme.accent
    val caseLook = stacksCaseLook(c.type)
    // (The Spines look: the stacks are its spines already, so the middle shows the plain cover, never a case.)
    val spines = spinesOn(c.type)
    if (caseLook && !spines && Features.stacks3d.state.value) {
        // Update 151: a real 3D case (the carousels' case drawing: its cover on the front, its spine on the side), turned a
        // little so its spine shows (Cases in 3D; off: the flat case below).
        Box(modifier.gameOrigin(k, on)) {
            Case3D(
                g, c.cover(g), c.state.physicalMedia[k], STACKS_CASE_YAW, case3dAspect(g, false),
                Modifier.fillMaxSize().awardShine(k, focused), frame = focused, frameColor = accent
            )
            ConsoleBadge(g.platform, packageName = g.packageName)
            StacksProgress(c, g, on)
        }
    } else if (caseLook && !spines && caseMarker(g.platform) != null) {
        val scan = typedArtFor(CardArtType.CASE, c.state.physicalMedia[k], c.state.screenshots[k], g.platform)
        Box(modifier.gameOrigin(k, on)) {
            MediaFocus(on) {
                CaseArt(
                    g.platform, c.cover(g), scan, Modifier.fillMaxSize().awardShine(k, focused), title = gameDisplayName(g.name),
                    caseModifier = if (focused) Modifier.border(2.dp, accent, RoundedCornerShape(2.dp)) else Modifier
                )
            }
            ConsoleBadge(g.platform, packageName = g.packageName)
            StacksProgress(c, g, on)
        }
    } else if (caseLook) {
        // No case for its console (or the Spines look, whose spines are the stacks already): the plain cover.
        val corner = LocalCardLook.current.cornerDp.coerceAtMost(10).dp
        val cover = c.cover(g)
        Box(
            modifier.gameOrigin(k, on)
                .then(if (focused) Modifier.styleFocus(true, corner, 0.75f, accent) else Modifier)
                .clip(uiRounded(corner)).background(placeholderBrush(g.hue))
        ) {
            if (cover != null) AsyncImage(cover, g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
            else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
            else Text(
                LocalTitleOptions.current.display(g.name), color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp,
                textAlign = TextAlign.Center, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(8.dp)
            )
            ConsoleBadge(g.platform, packageName = g.packageName)
            StacksProgress(c, g, on)
        }
    } else {
        // Cartridges / discs, wide capsules, screenshots: the layout's own artwork, with its focus effect.
        val pulse = rememberFocusPulse(animate = focused && !LocalLiteEffects.current && LocalCardLook.current.focusEffect != com.shelf.launcher.data.FocusEffect.NONE)
        StyleArt(c, g, modifier, focused = focused, fx = if (focused) pulse else null)
    }
}

/** The achievement chip on the game you're on (as StyleArt shows it on the other layouts' cards). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.StacksProgress(c: StyleCtx, g: Game, on: Boolean) {
    val look = LocalCardLook.current
    val at = if (on && look.achievementsOnCards) achChipAlign(look.trophyCorner) else null
    val p = if (at != null) c.progress(g) else null
    if (at != null && p != null) GameProgressChip(p.first, p.second, Modifier.align(at))
}

/**
 * The floor mirror: whatever the case / spine draws is drawn again upside down under its foot, fading out. Drawn from
 * one recorded copy of the picture (no second set of images), so it costs a draw, not a rebuild.
 */
private fun Modifier.stacksMirror(on: Boolean): Modifier = if (!on) this else this.drawWithCache {
    val picture = obtainGraphicsLayer()
    val reflection = obtainGraphicsLayer()
    // Its own layer, so the fade only thins the reflection, not what lies under it.
    reflection.compositingStrategy = CompositingStrategy.Offscreen
    val h = size.height
    val reflH = (h * STACKS_MIRROR).coerceAtLeast(1f)
    val fade = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 1f to Color.Transparent, startY = 0f, endY = reflH)
    onDrawWithContent {
        picture.record { this@onDrawWithContent.drawContent() }
        drawLayer(picture)
        reflection.record(IntSize(size.width.roundToInt().coerceAtLeast(1), reflH.roundToInt())) {
            // Flipped about the foot: its bottom edge at the top of the reflection.
            withTransform({
                translate(0f, h)
                scale(1f, -1f, pivot = Offset.Zero)
            }) { drawLayer(picture) }
            drawRect(fade, blendMode = BlendMode.DstIn)
        }
        translate(0f, h) { drawLayer(reflection) }
    }
}

/**
 * The room the stacks stand in (drawn once per size / colour, nothing moves): a soft cone of light down onto the middle,
 * the back wall darkening toward the horizon, the floor grid running back to it in the theme's colour, and a pool of
 * light where the case stands.
 */
@Composable
private fun StacksRoom(geo: StacksGeo, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val hy = geo.horizonY
        val cx = geo.cx
        // The back wall: darker toward the horizon line.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f), startY = 0f, endY = hy), size = androidx.compose.ui.geometry.Size(w, hy))
        // The floor: the theme's colour faintly at the horizon, darker toward you.
        drawRect(
            Brush.verticalGradient(0f to accent.copy(alpha = 0.10f), 1f to Color.Black.copy(alpha = 0.30f), startY = hy, endY = h),
            topLeft = Offset(0f, hy), size = androidx.compose.ui.geometry.Size(w, (h - hy).coerceAtLeast(0f))
        )
        // The floor grid: lines running back to the middle of the horizon …
        val fan = Brush.verticalGradient(0f to Color.Transparent, 1f to accent.copy(alpha = 0.16f), startY = hy, endY = h)
        for (j in -14..14) {
            drawLine(fan, Offset(cx + j * w * 0.025f, hy), Offset(cx + j * w * 0.14f, h), strokeWidth = 1.dp.toPx())
        }
        // … and across, closer together further back.
        for (n in 0..9) {
            val y = hy + (h - hy) / (1f + 0.5f * (9 - n))
            val a = 0.03f + 0.1f * (n / 9f)
            drawLine(accent.copy(alpha = a), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        // The horizon itself, a faint lit line.
        drawLine(
            Brush.horizontalGradient(0f to Color.Transparent, 0.5f to accent.copy(alpha = 0.28f), 1f to Color.Transparent),
            Offset(0f, hy), Offset(w, hy), strokeWidth = 1.5.dp.toPx()
        )
        // A cone of soft light from above onto the middle.
        val topHalf = geo.caseWPx * 0.32f
        val footHalf = geo.caseWPx * 0.95f
        val cone = Path().apply {
            moveTo(cx - topHalf, 0f); lineTo(cx + topHalf, 0f)
            lineTo(cx + footHalf, geo.floorY); lineTo(cx - footHalf, geo.floorY); close()
        }
        drawPath(cone, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.07f), 1f to Color.White.copy(alpha = 0.015f), startY = 0f, endY = geo.floorY))
        // The pool of light where the case stands, in the accent colour (squashed into an ellipse on the floor).
        val r = geo.caseWPx * 0.95f
        withTransform({ scale(1f, 0.22f, pivot = Offset(cx, geo.floorY)) }) {
            drawCircle(
                Brush.radialGradient(0f to accent.copy(alpha = 0.30f), 1f to Color.Transparent, center = Offset(cx, geo.floorY), radius = r),
                radius = r, center = Offset(cx, geo.floorY)
            )
        }
    }
}

/** The round buttons along the bottom: Favourite, Options, Details and Play (▼ reaches them). */
@Composable
private fun StacksBar(c: StyleCtx, g: Game, compact: Boolean) {
    val fav = g.stableKey in c.state.favorites
    val played = c.lastPlayed(g) != null
    val favorite by rememberUpdatedState(c.actions.favorite)
    val options by rememberUpdatedState(c.actions.options)
    val open by rememberUpdatedState(c.actions.open)
    val play by rememberUpdatedState(c.actions.play)
    val game by rememberUpdatedState(g)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StacksButton(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favourite", 0, compact, tint = if (fav) Color(0xFFE8BE48) else Color.White) { favorite(game) }
        StacksButton(Icons.Filled.Tune, "Options", 1, compact) { options(game) }
        StacksButton(Icons.Filled.Info, "Details", 2, compact) { open() }
        StacksButton(Icons.Filled.PlayArrow, if (played) "Continue" else "Play", 3, compact, main = true) { play(game) }
    }
}

@Composable
private fun StacksButton(
    icon: ImageVector, label: String, index: Int, compact: Boolean, tint: Color = Color.White, main: Boolean = false, onClick: () -> Unit
) {
    val click by rememberUpdatedState(onClick)
    val on = StyleNav.on(StyleNav.Zone.BAR, index)
    val accent = ShelfTheme.accent
    val grow by animateFloatAsState(if (on) 1.12f else 1f, spring(stiffness = Features.rowGlide.state.value.stiffness), label = "stacksButton")
    val d = (if (compact) 34.dp else 42.dp) * (if (main) 1.12f else 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(index) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, index); click() } }
    ) {
        Box(
            Modifier.size(d)
                .graphicsLayer { scaleX = grow; scaleY = grow }
                .clip(CircleShape)
                .background(
                    when {
                        main -> accent.copy(alpha = if (on) 1f else 0.7f)
                        on -> accent.copy(alpha = 0.38f)
                        else -> Color.White.copy(alpha = 0.14f)
                    }
                )
                .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(d * 0.5f)) }
        Spacer(Modifier.height(3.dp))
        Text(
            label, color = Color.White.copy(alpha = if (on) 1f else 0.7f), fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false
        )
    }
}

/**
 * The keys on the games: ◀ ▶ are the home screen's own (previous / next game); ▼ goes to the button bar (landing on Play)
 * or, without one, to what lies below; ▲ to what lies above.
 */
internal fun caseStacksKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) {
        val wasThere = StyleNav.zone == StyleNav.Zone.BAR
        val handled = ctx.enterZone(StyleNav.Zone.BAR)
        // Play is what you want most from the bar: ▼ always lands on it.
        if (!wasThere && StyleNav.zone == StyleNav.Zone.BAR) StyleNav.index = StyleNav.bar.lastIndex
        handled
    } else ctx.reach(false)
    Key.DirectionUp -> ctx.reach(true)
    else -> false
}

@Composable
internal fun CaseStacksSettings() {
    MutedNote("The game you're on stands big in the middle as its case, the others as stacks of spines: the ones before it on the left, the next ones on the right. Moving slides the next spine out of its stack, turns it to face you and puts the old one back. ◀ ▶ move, ▼ the button bar (Favourite, Options, Details, Play). Tap a spine to pick it, tap the case to open it, or swipe along.")
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        Icons.Filled.ViewInAr, "Stacks in perspective",
        "The stacks go back into the room, the far spines smaller and higher, turned toward the middle. Off: flat, straight on.",
        Features.stacksDepth.state.value, { Features.stacksDepth.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.PlayArrow, "Button bar",
        "Round buttons along the bottom: Favourite, Options, Details and Play (▼ reaches them).",
        Features.stacksBar.state.value, { Features.stacksBar.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.WbSunny, "Mirror floor",
        "A soft reflection of the cases and spines on a glossy floor.",
        Features.stacksMirror.state.value, { Features.stacksMirror.value = it }
    )
    // Update 151.
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.ViewInAr, "Cases in 3D",
        "The game in the middle as a real 3D case, turned a little so its spine shows. Off: its flat case, facing you.",
        Features.stacks3d.state.value, { Features.stacks3d.value = it }
    )
}
