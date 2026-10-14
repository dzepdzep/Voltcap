package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timelapse
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.floor

/*
 * Update 158: Record crate — your games as record sleeves standing in a wooden crate, seen from a little above and to
 * the left. The sleeves (each game's cover, cut square) lean back in a row, packed front to back, the ones further back
 * higher and a little to the left so their edges show. Flipping through moves them forward like flipping records: the
 * one you leave tips forward and drops behind the crate's front board, the next one lifts up out of the crate so its
 * sleeve shows whole, and its record (a black disc with the game's cover as the label, its grooves drawn once) slides
 * half out of the sleeve to the right and turns slowly (Record turning; never while the home screen rests or with Lite
 * effects). The crate is Voltcap's own drawing: wood planks (Wood colour: light or dark), corner battens, a hand hole
 * and the category's name stencilled on the front board.
 * Beside it on a wide screen (under it on a squarer one): the game's name, its console, time played, achievements,
 * and Play / Details.
 * D-pad: ◀ ▶ flip (the home screen's own; with Endless on the crate keeps flipping round), ▼ Play and Details (◀ ▶
 * between them, ▲ / B back), ▲ what lies above. Touch: tap a sleeve to pick it, tap the one you're on to open it,
 * swipe sideways to flip.
 */

/** Update 158: each step back into the crate moves a sleeve this far left / up (shares of a sleeve's size). */
private const val CRATE_STEP_X = -0.075f
private const val CRATE_STEP_Y = -0.05f

/** Update 158: how fast the sleeves shrink going back into the crate (per sleeve). */
private const val CRATE_DEPTH_K = 0.03f

/** Update 158: sleeves drawn behind the one you're on (fewer with Lite effects). */
private const val CRATE_BACK = 6
private const val CRATE_BACK_LITE = 4

/** Update 158: how far the sleeves standing in the crate lean back (degrees). */
private const val CRATE_LEAN = 16f

/** Update 158: the record against its sleeve (across). */
private const val CRATE_DISC = 0.94f

/** Update 158: the whole picture is this many sleeves wide and tall (so it always fits the stage). */
private const val CRATE_WIDE = 2.1f
private const val CRATE_TALL = 1.44f

@Composable
internal fun StyleRecordCrate(c: StyleCtx, modifier: Modifier = Modifier) {
    val g0 = c.game
    // Update 158: the game's info hidden in Edit Home drops its buttons out of the D-pad's way.
    val infoOff = LayoutParts.off(HomeLayout.CRATE, "info")
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        // ▼: Play, then Details.
        StyleNav.bar = if (g0 == null || infoOff) emptyList() else listOf({ c.actions.play(g0) }, { c.actions.open() })
    }
    // Plain if / else, never an early return out of the layout.
    if (g0 == null) StyleEmpty(modifier) else CrateBody(c, g0, modifier)
}

/** Update 158: ▼ goes to Play (always lands on it), ▲ to what lies above; ◀ ▶ are the home screen's own. */
internal fun recordCrateKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionDown -> if (StyleNav.bar.isNotEmpty()) {
        StyleNav.forget(StyleNav.Zone.BAR)
        ctx.enterZone(StyleNav.Zone.BAR)
    } else ctx.reach(false)
    Key.DirectionUp -> ctx.reach(true)
    else -> false
}

/** Update 158: Settings › Home › Layout options › Record crate options. */
@Composable
internal fun RecordCrateSettings() {
    MutedNote("Your games as record sleeves in a wooden crate. The one you're on lifts out of the crate and its record slides out to the right. ◀ ▶ flip through the crate, ▼ goes to Play. Tap a sleeve to pick it, or swipe to flip.")
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        Icons.Filled.Album, "Record turning",
        "The record half out of its sleeve turns slowly. It stops while the home screen rests and with Lite effects. Off: it stands still.",
        Features.crateSpin.state.value, { Features.crateSpin.value = it }
    )
    Spacer(Modifier.height(8.dp))
    StyleChoice("Wood colour", Features.CrateWood.values(), Features.crateWood.state.value, { it.label }) { Features.crateWood.value = it }
}

@Composable
private fun CrateBody(c: StyleCtx, g: Game, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 380.dp
        val pad = if (compact) 8.dp else 14.dp
        val wood = crateWood(Features.crateWood.state.value)
        // Update 158: a wide screen has the game's info beside the crate, a squarer one (the Retroid Pocket) under it.
        if (maxWidth >= maxHeight * 1.45f) {
            val infoW = (maxWidth * 0.36f).coerceIn(170.dp, 340.dp)
            Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(pad), verticalAlignment = Alignment.CenterVertically) {
                CrateStage(c, wood, Modifier.weight(1f).fillMaxHeight())
                Box(Modifier.width(infoW).fillMaxHeight().editablePart(LayoutParts.id(HomeLayout.CRATE, "info"), "Game info & Play")) {
                    CrateInfo(c, g, compact, true, wood, Modifier.fillMaxSize())
                }
            }
        } else {
            val infoH = if (compact) 78.dp else 96.dp
            Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = pad / 2)) {
                CrateStage(c, wood, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(pad / 2))
                Box(Modifier.fillMaxWidth().height(infoH).editablePart(LayoutParts.id(HomeLayout.CRATE, "info"), "Game info & Play")) {
                    CrateInfo(c, g, compact, false, wood, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** Update 158: the crate's colours. */
private class CrateColours(
    val light: Color, val mid: Color, val dark: Color, val grain: Color, val gap: Color, val paint: Color
)

private val CRATE_LIGHT = CrateColours(
    light = Color(0xFFDDB57F), mid = Color(0xFFC79964), dark = Color(0xFF9E7243), grain = Color(0xFF8A5F35),
    gap = Color(0xFF4A3220), paint = Color(0xFF2B1C10)
)
private val CRATE_DARK = CrateColours(
    light = Color(0xFF7E5636), mid = Color(0xFF61402A), dark = Color(0xFF452B19), grain = Color(0xFF2E1C10),
    gap = Color(0xFF170D06), paint = Color(0xFFEADBC2)
)

private fun crateWood(w: Features.CrateWood): CrateColours = if (w == Features.CrateWood.DARK) CRATE_DARK else CRATE_LIGHT

/**
 * Update 158: where everything stands, in px (worked out once per size, read every frame of a flip). [r] is a sleeve's
 * place against the one you're on: 0 = lifted out of the crate, 1 = the first one standing behind it, −1 the one just
 * flipped forward; fractions while flipping.
 */
private class CrateGeo(
    /** A sleeve's size; its left edge and its foot standing in the front of the crate; how far the one you're on lifts. */
    val s: Float, val sleeveL: Float, val restBottom: Float, val liftPx: Float,
    val stepX: Float, val stepY: Float, val nBack: Int,
    /** The crate: its front board, and its back (up and to the left, a little smaller). */
    val frontL: Float, val frontR: Float, val panelTop: Float, val panelBottom: Float,
    val backL: Float, val backR: Float, val backTop: Float, val backBottom: Float, val backScale: Float
) {
    private fun depth(r: Float): Float = if (r > 0f) r else 0f

    /** 0 … 1 out of the crate, eased. */
    fun lift(r: Float): Float {
        val t = (1f - abs(r)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun scale(r: Float): Float = 1f / (1f + CRATE_DEPTH_K * depth(r))
    fun x(r: Float): Float = sleeveL + depth(r) * stepX

    /** Its foot: further back is higher; lifted is higher still; flipped forward drops a little behind the board. */
    fun bottom(r: Float): Float {
        val drop = if (r < 0f) (-r).coerceAtMost(1f) * s * 0.12f else 0f
        return restBottom + depth(r) * stepY - lift(r) * liftPx + drop
    }

    /** Leaning back in the crate; upright when lifted; tipped forward when flipped past. */
    fun rotX(r: Float): Float = if (r >= 0f) CRATE_LEAN * r.coerceAtMost(1f) else -78f * (-r).coerceAtMost(1f)

    /** The one flipped past fades as it tips forward (gone before it lies flat); the last one at the back fades in. */
    fun alpha(r: Float): Float = if (r < 0f) (1f + 1.25f * r).coerceIn(0f, 1f) else (nBack + 1f - r).coerceIn(0f, 1f)

    /** How far the record is out of its sleeve (px): it slides out over the last part of the lift. */
    fun out(r: Float): Float = ((lift(r) - 0.45f) / 0.55f).coerceIn(0f, 1f) * s * 0.5f

    /** The crate's shade on the sleeves further in. */
    fun shade(r: Float): Float = (depth(r) * 0.07f).coerceAtMost(0.45f)
}

/** Update 158: the crate, its sleeves and the record; swipe to flip. */
@Composable
private fun CrateStage(c: StyleCtx, wood: CrateColours, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val keys = remember(c.games) { gameKeys(c.games) }
    val lite = LocalLiteEffects.current
    val glide = Features.rowGlide.state.value
    val animate = rememberAnimationsEnabled()
    val active = rememberIsActive()
    // The record turns only for the game you're on, never while the home screen rests, with Lite effects or with the
    // system's animations off.
    val spinOn = Features.crateSpin.state.value && !HomeIdle.calm && !lite && animate && active
    // With Endless on the place you're on keeps counting round (EndlessRow.kt): from the last game to the first is one
    // more flip, the first sleeves standing behind the last one again.
    val loop = loopRowOn(c)
    val loopNow by rememberUpdatedState(loop)
    val vFocus = rememberLoopFocus(c)
    val pos = remember { Animatable(vFocus.toFloat()) }
    // Which list the crate was showing: a new category jumps straight to its game instead of flipping through it.
    val firstKey = keys.firstOrNull()
    val shownList = remember { arrayOfNulls<Any>(2) }
    LaunchedEffect(vFocus, count, firstKey) {
        val target = vFocus.toFloat()
        val newList = shownList[0] != firstKey || shownList[1] != count
        shownList[0] = firstKey
        shownList[1] = count
        if (!animate || newList || abs(pos.value - target) > 12f) pos.snapTo(target)
        else pos.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
    }
    // Read while drawing, not while composing: a flip moves the sleeves without rebuilding them every frame.
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val between by remember { derivedStateOf { pos.value - floor(pos.value) > 0.001f } }
    val past by remember { derivedStateOf { pos.value - floor(pos.value) > 0.5f } }
    // The covers of the sleeves in the crate, warmed in the cards' own shape (StyleArtRequests.kt), so they show at once.
    val context = LocalContext.current
    val ctxNow by rememberUpdatedState(c)
    LaunchedEffect(vFocus / 2, count, firstKey) {
        kotlinx.coroutines.delay(120)
        val cc = ctxNow
        val n = cc.games.size
        val urls = ArrayList<String>()
        for (j in (vFocus - 2)..(vFocus + CRATE_BACK + 3)) {
            val at = if (loopNow && n > 0) j.mod(n) else j
            val gg = cc.games.getOrNull(at) ?: continue
            cc.cover(gg)?.let { urls += it }
        }
        // (Update 202: covers, drawn trimmed.)
        val handles = warmStyleArt(context, urls) { true }
        try { kotlinx.coroutines.awaitCancellation() } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { kotlinx.coroutines.delay(1500) }
            handles.forEach { it.dispose() }
        }
    }
    // A tap brings the D-pad back to the games too, then picks / opens the game.
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
        val k = GameSize.scale(HomeLayout.CRATE).coerceIn(0.5f, 1f)
        val fit = minOf((h - 6.dp) / CRATE_TALL, (w - 12.dp) / CRATE_WIDE)
        val s = (fit * k).coerceAtLeast(40.dp)
        val nBack = if (lite) CRATE_BACK_LITE else CRATE_BACK
        // The picture: the back of the crate reaches 0.62 sleeves left of the sleeve, the record 1.47 sleeves right.
        val left0 = (w - s * 2.09f) / 2
        val sleeveL = left0 + s * 0.62f
        val panelH = s * 0.42f
        val panelBottom = h - 4.dp
        val panelTop = panelBottom - panelH
        val plankH = panelH / 3
        val frontL = sleeveL - s * 0.07f
        val frontR = sleeveL + s * 1.07f
        val geo = remember(w, h, s, nBack, density) {
            with(density) {
                val sPx = s.toPx()
                val stepX = CRATE_STEP_X * sPx
                val stepY = CRATE_STEP_Y * sPx
                val restBottom = (panelBottom - s * 0.04f).toPx()
                // Lifted, its foot clears the front board (so the board never cuts into it).
                val liftBottom = (panelTop - s * 0.02f).toPx()
                val units = nBack + 1.4f
                val bsc = 1f / (1f + CRATE_DEPTH_K * units)
                val fL = frontL.toPx()
                val fR = frontR.toPx()
                val pT = panelTop.toPx()
                val pB = panelBottom.toPx()
                val cxBack = (fL + fR) / 2f + units * stepX
                val half = (fR - fL) / 2f * bsc
                val bB = pB + units * stepY
                CrateGeo(
                    s = sPx, sleeveL = sleeveL.toPx(), restBottom = restBottom, liftPx = restBottom - liftBottom,
                    stepX = stepX, stepY = stepY, nBack = nBack,
                    frontL = fL, frontR = fR, panelTop = pT, panelBottom = pB,
                    backL = cxBack - half, backR = cxBack + half, backTop = bB - (pB - pT) * bsc, backBottom = bB, backScale = bsc
                )
            }
        }
        val stepPx by rememberUpdatedState(with(density) { (s * 0.35f).toPx() })

        // Behind the sleeves: the crate's back and its right-hand wall (seen from inside), a warm light, its shadow.
        CrateBack(geo, wood, Modifier.fillMaxSize().zIndex(-1000f))

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                // Swipe to flip: a sleeve per step (to the left brings the next ones forward).
                var acc = 0f
                detectHorizontalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
                    change.consume()
                    acc += dx
                    val n = latestCount
                    if (n > 0) {
                        var target = latestFocus
                        while (acc <= -stepPx) { acc += stepPx; target = if (loopNow) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1) }
                        while (acc >= stepPx) { acc -= stepPx; target = if (loopNow) (target - 1).mod(n) else (target - 1).coerceAtLeast(0) }
                        if (target != latestFocus) {
                            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
                            select(target)
                        }
                    }
                }
            }
        ) {
            // Places on the (endless) row: the one leaving / coming from the front, and the sleeves behind.
            val first = if (loop) lo else lo.coerceAtLeast(0)
            val last = if (loop) lo + nBack + 1 else (lo + nBack + 1).coerceAtMost(count - 1)
            val front = if (between && past) lo + 1 else lo
            val inGames = StyleNav.zone == StyleNav.Zone.GAMES
            for (i in first..last) {
                val real = if (loop) i.mod(count) else i
                key(if (loop) "${keys[real]}#${Math.floorDiv(i, count)}" else keys[i]) {
                    // The record only for the sleeve(s) out of the crate: the one there, and the one coming / going.
                    val near = i == lo || (between && i == lo + 1)
                    // The one nearest the front on top, the ones flipped past in front of the ones still standing.
                    val z = when {
                        i == front -> 100f
                        i < front -> 50f - (front - i)
                        else -> -(i - front).toFloat()
                    }
                    CrateItem(
                        c, i, real, geo, s, { pos.value }, near = near,
                        spin = spinOn && i == vFocus, focused = i == vFocus && inGames, centre = i == vFocus,
                        tap = tap, modifier = Modifier.zIndex(z)
                    )
                }
            }
            // The crate's front board and left-hand side, over the sleeves standing in it (the lifted one clears it).
            CrateFront(geo, wood, Modifier.fillMaxSize().zIndex(200f))
            // The category's name, stencilled on the middle board.
            Box(
                Modifier.zIndex(201f)
                    .offset(x = frontL + s * 0.08f, y = panelTop + plankH)
                    .size(width = frontR - frontL - s * 0.16f, height = plankH),
                contentAlignment = Alignment.Center
            ) {
                val fs = with(density) { (plankH * 0.5f).toSp() }
                Text(
                    c.categoryLabel.uppercase(), color = wood.paint.copy(alpha = 0.74f), fontSize = fs,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .border(1.5.dp, wood.paint.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Update 158: one sleeve in the crate (with its record inside while it's out of the crate). Everything that moves is
 * read from [pos] while drawing. [place] is its place on the (endless) row, [game] the game there.
 */
@Composable
private fun CrateItem(
    c: StyleCtx, place: Int, game: Int, geo: CrateGeo, s: Dp, pos: () -> Float, near: Boolean,
    spin: Boolean, focused: Boolean, centre: Boolean, tap: (Int) -> Unit, modifier: Modifier
) {
    val g = c.games[game]
    val onTap by rememberUpdatedState(tap)
    Box(
        modifier
            .size(s)
            .graphicsLayer {
                val r = place - pos()
                val k = geo.scale(r)
                translationX = geo.x(r)
                translationY = geo.bottom(r) - geo.s
                scaleX = k
                scaleY = k
                transformOrigin = TransformOrigin(0.5f, 1f)
                rotationX = geo.rotX(r)
                cameraDistance = 10f * density
                alpha = geo.alpha(r)
                flatTextFor3d() // Update 177: drawn flat, then turned (Android 13 text cache)
            }
    ) {
        // The record first, so it sits inside the sleeve until it slides out to the right.
        if (near) CrateRecord(
            c, g, s * CRATE_DISC, spin,
            Modifier.align(Alignment.Center).graphicsLayer {
                val o = geo.out(place - pos())
                translationX = o
                alpha = if (o < 0.5f) 0f else 1f
            }
        )
        CrateSleeve(
            c, g, focused, centre,
            Modifier.fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val sh = geo.shade(place - pos())
                    if (sh > 0.005f) drawRect(Color.Black.copy(alpha = sh))
                }
                .pointerInput(game) { detectTapGestures { onTap(game) } }
        )
    }
}

/** Update 158: a game's sleeve: its cover cut square, the paper's light and a faint ring worn in by the record. */
@Composable
private fun CrateSleeve(c: StyleCtx, g: Game, focused: Boolean, centre: Boolean, modifier: Modifier) {
    val k = g.stableKey
    val url = c.cover(g)
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .gameOrigin(k, centre)
            .then(if (centre) Modifier.focusedCard() else Modifier)
            .clip(shape)
            .background(placeholderBrush(g.hue))
    ) {
        when {
            // (Update 159 showed the whole cover on the sleeve over a dimmed copy of itself.) Update 202: the cover fills
            // the square sleeve edge to edge, centred, cut only as much as its shape is off the square (object-fit:
            // cover) — no dimmed copy or strips beside it; its file's flat strips trimmed (TrimMargins.kt).
            url != null -> {
                val req = rememberStyleArtRequest(url, trim = true)
                AsyncImage(req, g.name, contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = Modifier.fillMaxSize().pixelArtwork())
            }
            g.sourceType == com.shelf.launcher.data.SourceType.APP -> AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
            else -> Text(
                LocalTitleOptions.current.display(g.name), color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp,
                textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp)
            )
        }
        // The sleeve's paper: light from the top left, and the ring the record has worn into it (drawn once per size).
        Box(
            Modifier.matchParentSize().drawWithCache {
                val light = Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f),
                    start = Offset.Zero, end = Offset(size.width, size.height)
                )
                val ringR = size.minDimension * 0.44f
                val ring = Stroke(size.minDimension * 0.012f)
                onDrawBehind {
                    drawRect(light)
                    drawCircle(Color.White.copy(alpha = 0.07f), ringR, center, style = ring)
                }
            }
        )
        if (centre) ConsoleBadge(g.platform, packageName = g.packageName)
        Box(
            Modifier.matchParentSize().border(
                if (focused) 2.dp else 1.dp, if (focused) accent else Color.White.copy(alpha = 0.2f), shape
            )
        )
    }
}

/**
 * Update 158: the record: a black disc with its grooves (drawn once per size), the game's cover as its label (the only
 * part that turns, so a turn costs one layer), and a light sheen that stays put as it turns.
 */
@Composable
private fun CrateRecord(c: StyleCtx, g: Game, d: Dp, spin: Boolean, modifier: Modifier) {
    val angle = if (spin) {
        val t = rememberInfiniteTransition(label = "crateSpin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart), label = "crateAngle")
    } else null
    val url = c.cover(g)
    Box(
        modifier.size(d).drawWithCache {
            val r = size.minDimension / 2f
            val body = Brush.radialGradient(
                0f to Color(0xFF1E1E1E), 0.55f to Color(0xFF0D0D0D), 1f to Color(0xFF181818), radius = r
            )
            val groove = Stroke(0.6.dp.toPx())
            val rim = Stroke(1.dp.toPx())
            onDrawBehind {
                drawCircle(body, r, center)
                // The grooves: fine rings from the label out, a wider gap between two tracks.
                for (j in 0 until 16) {
                    val rr = r * (0.40f + j * 0.036f)
                    drawCircle(Color.White.copy(alpha = if (j % 2 == 0) 0.06f else 0.035f), rr, center, style = groove)
                }
                drawCircle(Color.White.copy(alpha = 0.09f), r * 0.66f, center, style = rim)
                drawCircle(Color.White.copy(alpha = 0.14f), r - rim.width, center, style = rim)
            }
        }
    ) {
        // The label: the game's cover, turning.
        Box(
            Modifier.align(Alignment.Center).size(d * 0.36f)
                .graphicsLayer {
                    rotationZ = angle?.value ?: 0f
                    clip = true
                    shape = CircleShape
                }
                .background(placeholderBrush(g.hue))
        ) {
            // (Update 202: the same trimmed decode as the sleeve.)
            if (url != null) AsyncImage(rememberStyleArtRequest(url, trim = true), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // The spindle hole.
        Box(Modifier.align(Alignment.Center).size(d * 0.035f).clip(CircleShape).background(Color(0xFF0B0B0B)))
        // The sheen: two soft wedges of light across the grooves (they stay where the light is).
        Box(
            Modifier.matchParentSize().drawWithCache {
                val r = size.minDimension / 2f
                val sheen = Brush.sweepGradient(
                    0f to Color.Transparent, 0.07f to Color.White.copy(alpha = 0.11f), 0.15f to Color.Transparent,
                    0.5f to Color.Transparent, 0.57f to Color.White.copy(alpha = 0.08f), 0.65f to Color.Transparent,
                    1f to Color.Transparent
                )
                onDrawBehind { drawCircle(sheen, r, center) }
            }
        )
    }
}

/** Update 158: a plank's face: its colour (lit from above), three grain lines, a seam under it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.crateBoard(
    wood: CrateColours, l: Float, t: Float, r: Float, b: Float, seam: Float
) {
    val h = b - t
    drawRect(
        Brush.verticalGradient(0f to wood.light, 0.6f to wood.mid, 1f to wood.dark, startY = t, endY = b),
        Offset(l, t), Size(r - l, h)
    )
    val grainW = (h * 0.025f).coerceAtLeast(1f)
    for (j in 1..3) {
        val y = t + h * (0.2f + j * 0.2f)
        drawLine(wood.grain.copy(alpha = 0.22f), Offset(l + (r - l) * 0.04f * j, y), Offset(r - (r - l) * 0.05f * (4 - j), y + h * 0.04f), grainW)
    }
    drawLine(wood.gap, Offset(l, b), Offset(r, b), seam)
}

/** Update 158: the back of the crate (the back board, the right-hand wall from inside), its shadow and a warm light. */
@Composable
private fun CrateBack(geo: CrateGeo, wood: CrateColours, modifier: Modifier) {
    Box(
        modifier.drawWithCache {
            val s = geo.s
            val cxLift = geo.sleeveL + s / 2f
            val cyLift = geo.restBottom - geo.liftPx - s / 2f
            val glow = Brush.radialGradient(
                0f to wood.light.copy(alpha = 0.20f), 1f to Color.Transparent, center = Offset(cxLift, cyLift), radius = s * 1.15f
            )
            val shadowC = Offset((geo.frontL + geo.frontR) / 2f + (geo.backL - geo.frontL) / 2f, geo.panelBottom)
            val shadowR = (geo.frontR - geo.backL) * 0.62f
            val shadow = Brush.radialGradient(0f to Color.Black.copy(alpha = 0.38f), 1f to Color.Transparent, center = shadowC, radius = shadowR)
            // The right-hand wall, seen from inside: from the front board's top right back to the back board's.
            val rightWall = Path().apply {
                moveTo(geo.frontR, geo.panelTop); lineTo(geo.backR, geo.backTop)
                lineTo(geo.backR, geo.backBottom); lineTo(geo.frontR, geo.panelBottom); close()
            }
            val th = s * 0.03f
            val rightRim = Path().apply {
                moveTo(geo.frontR - th, geo.panelTop); lineTo(geo.frontR, geo.panelTop)
                lineTo(geo.backR, geo.backTop); lineTo(geo.backR - th * geo.backScale, geo.backTop); close()
            }
            val seam = (s * 0.008f).coerceAtLeast(1f)
            val inside = Brush.horizontalGradient(0f to wood.dark, 1f to wood.mid, startX = geo.backR, endX = geo.frontR)
            onDrawBehind {
                drawRect(glow)
                // The crate's shadow on the floor, squashed into an ellipse.
                scale(1f, 0.16f, shadowC) { drawCircle(shadow, shadowR, shadowC) }
                // The back board, in the crate's shade.
                crateBoard(wood, geo.backL, geo.backTop, geo.backR, geo.backBottom, seam)
                drawRect(Color.Black.copy(alpha = 0.45f), Offset(geo.backL, geo.backTop), Size(geo.backR - geo.backL, geo.backBottom - geo.backTop))
                drawPath(rightWall, inside)
                drawPath(rightWall, Color.Black.copy(alpha = 0.3f))
                // Its plank seams, running back.
                for (j in 1..2) {
                    val f = j / 3f
                    drawLine(
                        wood.gap.copy(alpha = 0.7f),
                        Offset(geo.frontR, geo.panelTop + (geo.panelBottom - geo.panelTop) * f),
                        Offset(geo.backR, geo.backTop + (geo.backBottom - geo.backTop) * f), seam
                    )
                }
                drawPath(rightRim, wood.light)
            }
        }
    )
}

/** Update 158: the crate's front board (three planks, corner battens, a hand hole) and its left-hand side. */
@Composable
private fun CrateFront(geo: CrateGeo, wood: CrateColours, modifier: Modifier) {
    Box(
        modifier.drawWithCache {
            val s = geo.s
            val th = s * 0.03f
            val leftSide = Path().apply {
                moveTo(geo.frontL, geo.panelTop); lineTo(geo.backL, geo.backTop)
                lineTo(geo.backL, geo.backBottom); lineTo(geo.frontL, geo.panelBottom); close()
            }
            val leftRim = Path().apply {
                moveTo(geo.frontL, geo.panelTop); lineTo(geo.frontL + th, geo.panelTop)
                lineTo(geo.backL + th * geo.backScale, geo.backTop); lineTo(geo.backL, geo.backTop); close()
            }
            val side = Brush.horizontalGradient(0f to wood.dark, 1f to wood.mid, startX = geo.backL, endX = geo.frontL)
            val seam = (s * 0.008f).coerceAtLeast(1f)
            val plank = (geo.panelBottom - geo.panelTop) / 3f
            val batten = s * 0.06f
            val holeW = s * 0.26f
            val holeH = plank * 0.38f
            val holeL = (geo.frontL + geo.frontR) / 2f - holeW / 2f
            val holeT = geo.panelTop + plank * 0.34f
            val nail = (s * 0.012f).coerceAtLeast(1.5f)
            val battens = floatArrayOf(geo.frontL, geo.frontR - batten)
            onDrawBehind {
                // The left-hand side, in shade, its plank seams running back.
                drawPath(leftSide, side)
                drawPath(leftSide, Color.Black.copy(alpha = 0.18f))
                for (j in 1..2) {
                    val f = j / 3f
                    drawLine(
                        wood.gap,
                        Offset(geo.frontL, geo.panelTop + (geo.panelBottom - geo.panelTop) * f),
                        Offset(geo.backL, geo.backTop + (geo.backBottom - geo.backTop) * f), seam
                    )
                }
                drawPath(leftRim, wood.light)
                // The front: three planks.
                for (j in 0 until 3) {
                    val t = geo.panelTop + plank * j
                    crateBoard(wood, geo.frontL, t, geo.frontR, t + plank, if (j < 2) seam else seam * 1.5f)
                }
                // The front board's top edge, catching the light.
                drawRect(wood.light, Offset(geo.frontL, geo.panelTop), Size(geo.frontR - geo.frontL, th * 0.5f))
                drawRect(Color.White.copy(alpha = 0.18f), Offset(geo.frontL, geo.panelTop), Size(geo.frontR - geo.frontL, th * 0.25f))
                // Corner battens, a shade darker, with their nails.
                for (x in battens) {
                    drawRect(wood.dark.copy(alpha = 0.55f), Offset(x, geo.panelTop), Size(batten, geo.panelBottom - geo.panelTop))
                    drawLine(wood.gap.copy(alpha = 0.6f), Offset(x + batten, geo.panelTop), Offset(x + batten, geo.panelBottom), seam)
                    for (j in 0 until 3) drawCircle(wood.gap, nail, Offset(x + batten / 2f, geo.panelTop + plank * (j + 0.5f)))
                }
                // The hand hole in the top plank.
                drawRoundRect(Color(0xFF0E0906), Offset(holeL, holeT), Size(holeW, holeH), CornerRadius(holeH / 2f))
                drawRoundRect(wood.light.copy(alpha = 0.5f), Offset(holeL, holeT + holeH), Size(holeW, seam), CornerRadius(seam))
            }
        }
    )
}

/** Update 158: the game you're on: its name, console, time played, achievements, Play and Details. */
@Composable
private fun CrateInfo(c: StyleCtx, g: Game, compact: Boolean, wide: Boolean, wood: CrateColours, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val ink = discTextStyle()
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    val played = c.lastPlayed(g) != null
    val time = stylePlaytime(c.playtime(g), played)
    val ach = c.progress(g)
    val achText = if (ach != null) "${ach.first} / ${ach.second}" else "None"
    val panel = Modifier
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF22170E).copy(alpha = 0.66f), Color(0xFF110B06).copy(alpha = 0.8f))))
        .border(1.dp, wood.light.copy(alpha = 0.35f), shape)
    val playLabel = if (played) "Continue" else "Play"
    if (wide) {
        Column(modifier.then(panel).padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp)) {
            Text(
                "FROM THE CRATE", color = wood.light.copy(alpha = 0.9f), fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, maxLines = 1, style = ink
            )
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            AnimatedContent(targetState = g, contentKey = { it.stableKey }, label = "crateInfo", transitionSpec = { gameSwap() }) { shown ->
                Column(Modifier.fillMaxWidth()) {
                    FitText(
                        titles.display(shown.name), color = Color.White, maxSize = if (compact) 19.sp else 26.sp, minSize = 12.sp,
                        maxLines = 2, fontWeight = FontWeight.Bold, style = ink
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        titles.console(shown.platform), color = Color.White.copy(alpha = 0.78f), fontSize = if (compact) 11.sp else 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            CrateFact(Icons.Filled.Timelapse, "Time played", time, compact, ink)
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            CrateFact(Icons.Filled.EmojiEvents, "Achievements", achText, compact, ink)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CratePill(playLabel, StyleNav.on(StyleNav.Zone.BAR, 0), compact, Modifier.weight(1f)) { c.actions.play(g) }
                CrateRound(Icons.Filled.Info, "Details", StyleNav.on(StyleNav.Zone.BAR, 1), compact) { c.actions.open() }
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                "${c.focus + 1} / ${c.games.size}", color = Color.White.copy(alpha = 0.55f), fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 1, style = ink
            )
        }
    } else {
        Row(
            modifier.then(panel).padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = g, contentKey = { it.stableKey }, label = "crateInfo", transitionSpec = { gameSwap() },
                modifier = Modifier.weight(1f)
            ) { shown ->
                Column(Modifier.fillMaxWidth()) {
                    FitText(
                        titles.display(shown.name), color = Color.White, maxSize = if (compact) 17.sp else 21.sp, minSize = 11.sp,
                        maxLines = 1, fontWeight = FontWeight.Bold, style = ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        titles.console(shown.platform), color = Color.White.copy(alpha = 0.78f), fontSize = if (compact) 10.sp else 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink
                    )
                    Spacer(Modifier.height(2.dp))
                    val shownAch = c.progress(shown)
                    Text(
                        stylePlaytime(c.playtime(shown), c.lastPlayed(shown) != null) +
                            (if (shownAch != null) "  ·  ${shownAch.first} / ${shownAch.second} achievements" else ""),
                        color = wood.light.copy(alpha = 0.95f), fontSize = if (compact) 10.sp else 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = ink
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            CratePill(playLabel, StyleNav.on(StyleNav.Zone.BAR, 0), compact, Modifier) { c.actions.play(g) }
            Spacer(Modifier.width(8.dp))
            CrateRound(Icons.Filled.Info, "Details", StyleNav.on(StyleNav.Zone.BAR, 1), compact) { c.actions.open() }
        }
    }
}

@Composable
private fun CrateFact(icon: ImageVector, label: String, value: String, compact: Boolean, ink: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, style = ink)
        Spacer(Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = ink)
    }
}

@Composable
private fun CratePill(label: String, on: Boolean, compact: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val pill = RoundedCornerShape(50)
    val accent = ShelfTheme.accent
    Row(
        modifier
            .graphicsLayer { val k = if (on) 1.06f else 1f; scaleX = k; scaleY = k }
            .clip(pill)
            .background(Color.White)
            .border(if (on) 3.dp else 0.dp, if (on) accent else Color.Transparent, pill)
            .tapOnly(onTap)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF16171B), modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFF16171B), fontSize = if (compact) 13.sp else 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CrateRound(icon: ImageVector, label: String, on: Boolean, compact: Boolean, onTap: () -> Unit) {
    val d = if (compact) 34.dp else 42.dp
    Box(
        Modifier
            .graphicsLayer { val k = if (on) 1.08f else 1f; scaleX = k; scaleY = k }
            .size(d)
            .clip(CircleShape)
            .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (on) 2.dp else 1.dp, if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.25f), CircleShape)
            .tapOnly(onTap),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (compact) 18.dp else 22.dp)) }
}
