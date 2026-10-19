package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/*
 * Update 158: Card binder — your games as collectible cards, in Voltcap's own design.
 *
 * Every game is a card: a coloured frame (the console's colour, or the game's art: Card binder › Frame colour), the name
 * on a title bar with a round "type" gem, the cover in a framed window, the console as its type with the card's number,
 * and a stats box: time played, achievements earned / total, last played and a row of rarity stars from the share of
 * achievements earned. A mastered game gets a gold foil frame.
 * The card you're on stands big in the middle, turned a little toward the light; a shiny sheen sweeps across it once
 * when you land on it (Shiny sheen; never with Lite effects or animations off). The others fan out either side,
 * smaller, like a hand of cards (Fan angle).
 * Y (or holding A) turns the card over: a few lines about the game, its release year, genre, who made it and a
 * "Played on" line. B (or Y again) turns it back; moving to another card shows its front.
 *
 * D-pad: ◀ ▶ through the hand (the home screen's own; Endless goes round like one more step), A plays (A starts the
 * game; off: the game's window), ▲ ▼ what lies above / below. Touch: tap a card to pick it, tap the one in the middle to
 * open it, hold it to turn it over, swipe along the hand.
 */

/** Update 158: width / height of a card. */
private const val CARD_ASPECT = 0.7f

/** Update 158: the size of the cards either side against the one in the middle. */
private const val CARD_SIDE_K = 0.62f

/** Update 158: how far the card in the middle is turned toward the light (degrees). */
private const val CARD_TILT_Y = 7f
private const val CARD_TILT_X = 4f

/** Update 158: a hold of A this long (ms) turns the card over instead of playing. */
private const val CARD_HOLD_MS = 450L

/** Update 158: the Card binder's turned-over card and what A does (read by [cardBinderButton], set as the layout draws). */
internal object CardBinder {
    /** The game whose card shows its back, or null. */
    var flipped by mutableStateOf<String?>(null)

    /** The game you're on, and what A does with it, as the layout drew them last. */
    @Volatile var current: String? = null
    @Volatile var play: (() -> Unit)? = null

    /** This press of A has already turned the card over (held): its release does nothing more. */
    @Volatile internal var heldFlip = false

    /** Turns the card you're on over (or back). */
    fun flip() {
        val k = current
        if (k != null) flipped = if (flipped == k) null else k
    }
}

/**
 * Update 158: the Card binder's buttons on the games (called by styleHomeKey before its D-pad handling): Y turns the
 * card over, A plays (a hold of A turns it over), B turns a turned card back. True = handled.
 */
internal fun cardBinderButton(event: KeyEvent, sound: (UiSound) -> Unit): Boolean {
    val n = event.nativeKeyEvent
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    return when (event.key) {
        Key.ButtonY -> {
            if (down && n.repeatCount == 0 && CardBinder.current != null) {
                CardBinder.flip()
                sound(UiSound.OPEN)
            }
            true
        }
        Key.ButtonA, Key.Enter, Key.DirectionCenter -> when {
            down && n.repeatCount == 0 -> {
                CardBinder.heldFlip = false
                true
            }
            down -> {
                // Held (the key repeats): turn the card over, once.
                if (!CardBinder.heldFlip && CardBinder.current != null) {
                    CardBinder.heldFlip = true
                    CardBinder.flip()
                    sound(UiSound.OPEN)
                }
                true
            }
            up -> when {
                // A release Android marks as cancelled belongs to no real press.
                n.isCanceled -> {
                    CardBinder.heldFlip = false
                    true
                }
                CardBinder.heldFlip -> {
                    CardBinder.heldFlip = false
                    true
                }
                // (A controller that doesn't repeat a held key: the hold is measured on release.)
                n.eventTime - n.downTime >= CARD_HOLD_MS -> {
                    if (CardBinder.current != null) {
                        CardBinder.flip()
                        sound(UiSound.OPEN)
                    }
                    true
                }
                Features.cardsAPlays.value && CardBinder.play != null -> {
                    CardBinder.play?.invoke()
                    true
                }
                // A starts the game off: the home screen's own A (the game's window).
                else -> false
            }
            else -> false
        }
        Key.ButtonB, Key.Back -> if (CardBinder.flipped != null) {
            if (up) {
                CardBinder.flipped = null
                sound(UiSound.BACK)
            }
            true
        } else false
        else -> false
    }
}

/** Update 158: ▲ ▼ go to what lies above / below the hand; ◀ ▶ are the home screen's own. */
internal fun cardBinderKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionUp -> ctx.reach(true)
    Key.DirectionDown -> ctx.reach(false)
    else -> false
}

@Composable
internal fun StyleCardBinder(c: StyleCtx, modifier: Modifier = Modifier) {
    val g0 = c.game
    val play by rememberUpdatedState(c.actions.play)
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
        val k = g0?.stableKey
        if (CardBinder.current != k) {
            CardBinder.current = k
            // Moving to another card shows its front.
            if (CardBinder.flipped != null && CardBinder.flipped != k) CardBinder.flipped = null
        }
        CardBinder.play = if (g0 == null) null else ({ play(g0) })
    }
    DisposableEffect(Unit) {
        onDispose {
            CardBinder.current = null
            CardBinder.play = null
            CardBinder.flipped = null
        }
    }
    // Plain if / else, never an early return out of the layout.
    if (g0 == null) StyleEmpty(modifier) else CardHand(c, g0, modifier)
}

@Composable
internal fun CardBinderSettings() {
    MutedNote("Your games as collectible cards: the cover in a framed window, the console as its type, and time played, achievements, last played and rarity stars at the bottom. The card you're on stands big in the middle, the others fan out either side. ◀ ▶ move through the hand, A plays, Y (or holding A) turns the card over. Tap a card to pick it, hold the one in the middle to turn it over, or swipe along.")
    Spacer(Modifier.height(10.dp))
    PercentSlider(
        "Fan angle", Features.cardsFan.state.value, { Features.cardsFan.value = it.coerceIn(0, 15) },
        min = 0, max = 15, valueText = { if (it == 0) "Straight" else "$it°" }
    )
    MutedNote("How far each card of the hand turns from the next. Straight: a flat row.")
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.AutoAwesome, "Shiny sheen",
        "A rainbow shine sweeps once across the card you land on (not with Lite effects). Off: no shine.",
        Features.cardsSheen.state.value, { Features.cardsSheen.value = it }
    )
    Spacer(Modifier.height(8.dp))
    StyleChoice("Frame colour", Features.CardFrame.values(), Features.cardsFrame.state.value, { it.label }) { Features.cardsFrame.value = it }
    MutedNote("Mastered games always get a gold frame.")
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.PlayArrow, "A starts the game",
        "A plays the game on the card straight away. Off: A opens the game's window, as on the other layouts. Y or holding A turns the card over either way.",
        Features.cardsAPlays.state.value, { Features.cardsAPlays.value = it }
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * Where the cards of the hand stand, in px (worked out once per screen size / option, read every frame of a glide).
 * [r] is a card's place against the one in the middle: 0 = in the middle, 1 = the first on the right, −1 the first on
 * the left, fractions while it glides.
 */
private class HandGeo(
    val cardW: Dp, val cardH: Dp, val wPx: Float, val hPx: Float,
    val cx: Float, val cy: Float, val x0: Float, val pitch: Float, val fan: Float, val nSide: Int
) {
    fun x(r: Float): Float {
        val a = abs(r)
        val side = if (r < 0f) -1f else 1f
        return if (a < 1f) cx + side * x0 * a else cx + side * (x0 + pitch * (a - 1f))
    }

    fun scale(r: Float): Float {
        val a = abs(r)
        return if (a < 1f) 1f + (CARD_SIDE_K - 1f) * a else CARD_SIDE_K * (1f - 0.04f * (a - 1f)).coerceAtLeast(0.7f)
    }

    /** The hand's fan: each card turned a little more than the one before it. */
    fun angle(r: Float): Float = r.coerceIn(-(nSide + 1f), nSide + 1f) * fan

    /** The cards further out sit a little lower, along the hand's curve. */
    fun y(r: Float): Float {
        val rad = Math.toRadians(angle(r).toDouble())
        return cy + hPx * (1f - scale(r)) * 0.22f + hPx * 1.4f * (1f - cos(rad).toFloat())
    }

    /** 1 = the card in the middle (turned toward the light), 0 = out in the hand. */
    fun tilt(r: Float): Float = (1f - abs(r)).coerceIn(0f, 1f)

    /** The last card or so each side fades out instead of stopping hard. */
    fun alpha(r: Float): Float = (nSide + 1f - abs(r)).coerceIn(0f, 1f)
}

/** Sizes on a card, from its height (the cards either side are the same card, scaled down). */
private class CardDims(
    val pad: Dp, val gap: Dp, val corner: Dp, val rim: Dp, val titleH: Dp, val typeH: Dp, val statH: Dp, val gem: Dp,
    val icon: Dp, val titleSp: TextUnit, val bodySp: TextUnit, val smallSp: TextUnit
)

private fun cardDims(h: Dp, d: Density): CardDims = with(d) {
    CardDims(
        pad = h * 0.03f, gap = h * 0.014f, corner = h * 0.045f, rim = (h * 0.007f).coerceAtLeast(1.dp),
        titleH = h * 0.085f, typeH = h * 0.06f, statH = h * 0.052f, gem = h * 0.056f, icon = h * 0.036f,
        titleSp = (h * 0.046f).toSp(), bodySp = (h * 0.032f).toSp(), smallSp = (h * 0.027f).toSp()
    )
}

/** The hand: the card you're on in the middle, the others fanned out either side; swipe to move along. */
@Composable
private fun CardHand(c: StyleCtx, g: Game, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val play by rememberUpdatedState(c.actions.play)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val keys = remember(c.games) { gameKeys(c.games) }
    // Endless: the cards repeat round the ends and the place you're on counts on (EndlessRow.kt), so going past the last
    // card to the first is one more step along the hand.
    val loop = loopRowOn(c)
    val latestLoop by rememberUpdatedState(loop)
    val vFocus = rememberLoopFocus(c)
    val glide = Features.rowGlide.state.value
    val animate = rememberAnimationsEnabled()
    val lite = LocalLiteEffects.current
    val fan = Features.cardsFan.state.value.coerceIn(0, 15).toFloat()
    val aPlays = Features.cardsAPlays.state.value
    val sheenOn = Features.cardsSheen.state.value && !lite && animate
    val accent = ShelfTheme.accent
    // Where the hand is (in cards): glides from card to card at the Moving between games speed.
    val pos = remember { Animatable(vFocus.toFloat()) }
    val posRead = remember { { pos.value } }
    val firstKey = keys.firstOrNull()
    val shownList = remember { arrayOfNulls<Any>(2) }
    LaunchedEffect(vFocus, count, firstKey) {
        val target = vFocus.toFloat()
        val newList = shownList[0] != firstKey || shownList[1] != count
        shownList[0] = firstKey
        shownList[1] = count
        // A new category or a long jump goes straight there instead of racing past dozens of cards.
        if (!animate || newList || abs(pos.value - target) > 12f) pos.snapTo(target)
        else pos.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
    }
    // Read where things are drawn, not while composing: only these change the cards' order during a glide.
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val between by remember { derivedStateOf { pos.value - floor(pos.value) > 0.001f } }
    val past by remember { derivedStateOf { pos.value - floor(pos.value) > 0.5f } }

    // Turning the card over: animated on the card you're on; a new card starts on its front at once.
    val flipA = remember { Animatable(0f) }
    var flipFor by remember { mutableStateOf<String?>(null) }
    val flippedNow = CardBinder.flipped == g.stableKey
    LaunchedEffect(flippedNow, g.stableKey) {
        val target = if (flippedNow) 180f else 0f
        if (flipFor != g.stableKey || !animate) {
            flipA.snapTo(target)
            flipFor = g.stableKey
        } else flipA.animateTo(target, tween(420, easing = FastOutSlowInEasing))
    }
    val flipRead = remember { { flipA.value } }

    // The shiny sheen: one sweep across the card you land on (a moment after the glide), never a loop.
    val sheen = remember { Animatable(1f) }
    LaunchedEffect(g.stableKey, sheenOn) {
        if (sheenOn) {
            sheen.snapTo(0f)
            kotlinx.coroutines.delay(170)
            sheen.animateTo(1f, tween(900, easing = LinearEasing))
        } else sheen.snapTo(1f)
    }
    val sheenRead = remember { { sheen.value } }

    // A tap brings the D-pad back to the cards too, then picks / opens the game; a hold turns the middle one over.
    val tap: (Int) -> Unit = remember {
        { i: Int ->
            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
            if (i == GridHome.focus) open() else select(i)
        }
    }
    val hold: (Int) -> Unit = remember {
        { i: Int -> if (i == GridHome.focus) CardBinder.flip() else select(i) }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().categorySlide()) {
        val w = maxWidth
        val h = maxHeight
        val compact = h < 380.dp
        val hintsOn = !LayoutParts.off(HomeLayout.CARDS, "hints")
        val hintH = if (hintsOn) (if (compact) 28.dp else 36.dp) else 0.dp
        val stageH = (h - hintH).coerceAtLeast(120.dp)
        val k = GameSize.scale(HomeLayout.CARDS)
        var cardH = ((stageH - 12.dp) * 0.9f * k).coerceAtMost(stageH - 8.dp).coerceAtLeast(110.dp)
        var cardW = cardH * CARD_ASPECT
        // Narrow screens: never more than about half the width, so the hand keeps room either side.
        val maxW = w * 0.46f
        if (cardW > maxW) { cardW = maxW; cardH = cardW / CARD_ASPECT }
        val geo = remember(w, stageH, cardW, cardH, fan, density) {
            with(density) {
                val wPx = cardW.toPx()
                val hPx = cardH.toPx()
                val half = (w / 2).toPx()
                // The first card each side tucks a little behind the one in the middle; the next ones overlap like a hand.
                val x0 = wPx * (0.5f + CARD_SIDE_K * 0.5f) - wPx * 0.18f
                val pitch = wPx * CARD_SIDE_K * 0.42f
                val nSide = ((half - x0) / pitch).coerceIn(0f, 20f).toInt() + 2
                HandGeo(cardW, cardH, wPx, hPx, half, (stageH / 2).toPx() - hPx * 0.02f, x0, pitch, fan, nSide.coerceIn(2, 6))
            }
        }
        val dims = remember(cardH, density) { cardDims(cardH, density) }
        val stepPx by rememberUpdatedState(geo.pitch * 1.4f)
        val pool = remember(accent) { Brush.radialGradient(listOf(accent.copy(alpha = 0.2f), Color.Transparent)) }

        // A soft pool of light behind the card in the middle.
        Box(Modifier.fillMaxWidth().height(stageH), contentAlignment = Alignment.Center) {
            Box(Modifier.size(cardW * 2.4f, cardH * 1.15f).background(pool))
        }
        Box(
            Modifier.fillMaxWidth().height(stageH).pointerInput(Unit) {
                // Swipe along the hand: a card per step (a swipe to the left brings the next ones in from the right).
                var acc = 0f
                detectHorizontalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
                    change.consume()
                    acc += dx
                    val n = latestCount
                    if (n > 0) {
                        var target = latestFocus
                        while (acc <= -stepPx) {
                            acc += stepPx
                            target = if (latestLoop) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1)
                        }
                        while (acc >= stepPx) {
                            acc -= stepPx
                            target = if (latestLoop) (target - 1).mod(n) else (target - 1).coerceAtLeast(0)
                        }
                        if (target != latestFocus) {
                            if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
                            select(target)
                        }
                    }
                }
            }
        ) {
            // (On the endless row these are places on it; [real] is the game there, one key per copy.)
            val first = if (loop) lo - geo.nSide - 1 else (lo - geo.nSide - 1).coerceAtLeast(0)
            val last = if (loop) lo + geo.nSide + 2 else (lo + geo.nSide + 2).coerceAtMost(count - 1)
            val inGames = StyleNav.zone == StyleNav.Zone.GAMES
            // (Update 246, #60: the places picked before the loop — a `continue` in it made Compose give each pass its
            // own group, so a step that moved every game one place along threw them all away and built them again.)
            for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - lo, count)) }) {
                // (Update 218, #105: going round a short list, each game once — no copies of the same game at the sides.)
                val real = if (loop) i.mod(count) else i
                key(if (loop) "${keys[real]}#${Math.floorDiv(i, count)}" else keys[i]) {
                    // The nearer of the two in the middle (going / coming) is on top, then outward.
                    val near = i == lo || (between && i == lo + 1)
                    val dist = if (near) (if ((i == lo) == !past) 0 else 1) else abs(i - lo)
                    val centre = i == vFocus
                    HandCard(
                        c, i, real, geo, dims, posRead, centre = centre, focused = centre && inGames,
                        flip = if (centre && flipFor == g.stableKey) flipRead else null,
                        sheen = if (centre) sheenRead else null, tap = tap, hold = hold,
                        modifier = Modifier.zIndex(if (near) 1000f - dist else -dist.toFloat())
                    )
                }
            }
        }
        if (hintsOn) CardHints(
            compact, aPlays, onFlip = { CardBinder.flip() }, onA = { if (aPlays) play(g) else open() },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(hintH)
                .editablePart(LayoutParts.id(HomeLayout.CARDS, "hints"), "Button hints")
        )
    }
}

/** One card of the hand; everything that moves is read from [pos] while drawing. */
@Composable
private fun HandCard(
    c: StyleCtx, i: Int, game: Int, geo: HandGeo, dims: CardDims, pos: () -> Float, centre: Boolean, focused: Boolean,
    flip: (() -> Float)?, sheen: (() -> Float)?, tap: (Int) -> Unit, hold: (Int) -> Unit, modifier: Modifier
) {
    val g = c.games[game]
    val onTap by rememberUpdatedState(tap)
    val onHold by rememberUpdatedState(hold)
    // Only the switch from front to back recomposes; the turn itself is drawn.
    val backState = remember(flip) { derivedStateOf { (flip?.invoke() ?: 0f) > 90f } }
    val showBack = backState.value
    Box(
        modifier
            .size(geo.cardW, geo.cardH)
            .graphicsLayer {
                val r = i - pos()
                val s = geo.scale(r)
                val t = geo.tilt(r)
                translationX = geo.x(r) - geo.wPx / 2f
                translationY = geo.y(r) - geo.hPx / 2f
                scaleX = s
                scaleY = s
                rotationZ = geo.angle(r)
                rotationY = -CARD_TILT_Y * t + (flip?.invoke() ?: 0f)
                rotationX = CARD_TILT_X * t
                cameraDistance = 14f * density
                alpha = geo.alpha(r)
                // 0.6.15: always drawn flat in its own layer, then turned — the fanned (rotated) cards' rounded corners
                // were clipped on the CPU every frame (27 % of the render thread while scrolling, Odin 2 Portal).
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .pointerInput(game) { detectTapGestures(onTap = { onTap(game) }, onLongPress = { onHold(game) }) }
    ) {
        // The back is drawn mirrored, so it reads the right way round once the card has turned.
        if (showBack) CardBack(c, g, dims, Modifier.fillMaxSize().graphicsLayer { scaleX = -1f })
        else CardFront(cardFrontData(c, g), g, game, dims, centre, focused, sheen, Modifier.fillMaxSize())
    }
}

/**
 * 0.6.15: what a card's front shows, as plain values. The cards only get this (not the whole home's context, which is new
 * on every step), so moving along the hand recomposes and redraws just the cards whose own picture changed — every card
 * of the hand was drawn again on each step (Odin 2 Portal: 16 % janky frames going sideways).
 */
@androidx.compose.runtime.Immutable
private data class CardFrontData(
    val cover: String?, val wide: String?, val progress: Pair<Int, Int>?, val played: Long?, val playtime: Long?, val count: Int
)

private fun cardFrontData(c: StyleCtx, g: Game) = CardFrontData(c.cover(g), c.wide(g), c.progress(g), c.lastPlayed(g), c.playtime(g), c.games.size)

/** A card's colours: its frame (console / art, gold when mastered) and what reads on it. */
private class CardPalette(val frame: Brush, val ink: Color, val muted: Color, val panel: Color, val rim: Color, val star: Color)

@Composable
private fun rememberCardPalette(c: StyleCtx, g: Game): CardPalette = rememberCardPalette(c.cover(g), c.progress(g), g)

@Composable
private fun rememberCardPalette(cover: String?, progress: Pair<Int, Int>?, g: Game): CardPalette {
    val gold = progress != null && progress.first >= progress.second
    val console = consoleColor(g.platform)
    val fromArt = Features.cardsFrame.state.value == Features.CardFrame.ART && !gold
    // (A small copy of the cover, only for its colour.)
    val bmp = rememberSoftBitmap(if (fromArt) cover else null, 96)
    val artTone = rememberArtColor(bmp, console)
    val base = if (fromArt && bmp != null) artTone.copy(alpha = 1f) else console
    return remember(base, gold) {
        if (gold) CardPalette(
            frame = Brush.linearGradient(
                listOf(Color(0xFFFFF1B8), Color(0xFFD4A63A), Color(0xFFFFE9A0), Color(0xFFB07F22), Color(0xFFF6D77E))
            ),
            ink = Color(0xFF3A2A08), muted = Color(0xFF5E4614), panel = Color.White.copy(alpha = 0.4f),
            rim = Color(0xFF8A6418), star = Color(0xFF8A5A00)
        ) else CardPalette(
            frame = Brush.linearGradient(listOf(lerp(base, Color.White, 0.3f), base, lerp(base, Color.Black, 0.38f))),
            ink = Color.White, muted = Color.White.copy(alpha = 0.72f), panel = Color.Black.copy(alpha = 0.28f),
            rim = Color.White.copy(alpha = 0.6f), star = Color(0xFFFFD45A)
        )
    }
}

/** The front: title bar, the cover in its window, the type line and the stats. */
@Composable
private fun CardFront(f: CardFrontData, g: Game, game: Int, d: CardDims, centre: Boolean, focused: Boolean, sheen: (() -> Float)?, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val p = rememberCardPalette(f.cover, f.progress, g)
    val progress = f.progress
    val played = f.played
    val placeholder = remember(g.hue) { placeholderBrush(g.hue) }
    val url = f.cover ?: f.wide
    Box(modifier.clip(RoundedCornerShape(d.corner)).background(p.frame).cardShine(sheen)) {
        Column(Modifier.fillMaxSize().padding(d.pad)) {
            CardTitle(titles.display(g.name), g, p, d)
            Spacer(Modifier.height(d.gap))
            // The picture in its framed window. (The game window grows out of it on the card you're on.)
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .gameOrigin(g.stableKey, centre && focused)
                    .border(d.rim, p.rim, RoundedCornerShape(d.corner * 0.45f))
                    .padding(d.rim)
                    .clip(RoundedCornerShape(d.corner * 0.35f))
                    .background(placeholder),
                contentAlignment = Alignment.Center
            ) {
                // (Update 159 showed the whole cover in the card's window over a dimmed copy of itself.) Update 202: the
                // cover fills the window edge to edge, centred, cut only as much as its shape is off the window's
                // (object-fit: cover) — no dimmed copy or strips beside it; its file's flat strips trimmed (TrimMargins.kt).
                if (url != null) {
                    val req = rememberStyleArtRequest(url, trim = url == f.cover)
                    AsyncImage(req, g.name, contentScale = ContentScale.Crop, alignment = Alignment.Center, modifier = Modifier.fillMaxSize().pixelArtwork())
                }
                else if (g.sourceType == SourceType.APP) AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                else Text(
                    titles.display(g.name), color = Color.White, fontSize = d.titleSp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(d.pad)
                )
            }
            Spacer(Modifier.height(d.gap))
            // The type line: the console, and the card's number in the category.
            Row(Modifier.fillMaxWidth().height(d.typeH), verticalAlignment = Alignment.CenterVertically) {
                TypeGem(g, d.gem * 0.85f)
                Spacer(Modifier.width(d.gap))
                Text(
                    titles.console(g.platform), color = p.ink, fontSize = d.bodySp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Text("No. ${game + 1} / ${f.count}", color = p.muted, fontSize = d.smallSp, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(d.gap))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(d.corner * 0.5f)).background(p.panel)
                    .padding(horizontal = d.pad, vertical = d.gap)
            ) {
                StatLine(Icons.Filled.Schedule, "Played", stylePlaytime(f.playtime, played != null), p, d)
                StatLine(Icons.Filled.EmojiEvents, "Achievements", progress?.let { "${it.first} / ${it.second}" } ?: "None", p, d)
                StatLine(Icons.Filled.History, "Last played", played?.let { lastPlayedLabel(it) } ?: "Never", p, d)
                RarityStars(progress, p, d)
            }
        }
    }
}

/** The back: about the game, its year, genre and maker, and where it was played. */
@Composable
private fun CardBack(c: StyleCtx, g: Game, d: CardDims, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val p = rememberCardPalette(c, g)
    val meta = c.state.metadata[g.stableKey]
    val summary = meta?.summary?.trim()?.takeIf { it.isNotEmpty() }
    val genre = meta?.genres?.take(2)?.joinToString(", ")?.takeIf { it.isNotEmpty() }
    val maker = meta?.developer?.takeIf { it.isNotBlank() } ?: meta?.publisher?.takeIf { it.isNotBlank() }
    val playedOn = playedOnLine(c, g)
    Box(modifier.clip(RoundedCornerShape(d.corner)).background(p.frame)) {
        Column(Modifier.fillMaxSize().padding(d.pad)) {
            CardTitle(titles.display(g.name), g, p, d)
            Spacer(Modifier.height(d.gap))
            Column(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(d.corner * 0.5f)).background(p.panel).padding(d.pad)
            ) {
                Text("About", color = p.muted, fontSize = d.smallSp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(d.gap * 0.5f))
                Text(
                    summary ?: "No details yet. Fetch them in Settings › Metadata › Scraping.",
                    color = p.ink, fontSize = d.bodySp, lineHeight = d.bodySp * 1.3f, maxLines = 9, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(d.gap))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(d.corner * 0.5f)).background(p.panel)
                    .padding(horizontal = d.pad, vertical = d.gap)
            ) {
                StatLine(Icons.Filled.DateRange, "Released", meta?.releaseYear?.toString() ?: "Unknown", p, d)
                StatLine(Icons.Filled.Category, "Genre", genre ?: "Unknown", p, d)
                if (maker != null) StatLine(Icons.Filled.Person, "Made by", maker, p, d)
            }
            Spacer(Modifier.height(d.gap))
            Text(
                playedOn, color = p.muted, fontSize = d.smallSp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "Played on <this handheld> with <emulator>", or "Not played yet". */
@Composable
private fun playedOnLine(c: StyleCtx, g: Game): String {
    val pkg = if (g.sourceType == SourceType.APP) null else c.state.emulatorConfigs[g.platform]?.packageName
    val emu = if (pkg != null) rememberAppLabel(pkg) else null
    val device = android.os.Build.MODEL.orEmpty().ifBlank { "this handheld" }
    return when {
        c.lastPlayed(g) == null -> "Not played yet"
        emu != null -> "Played on $device with $emu"
        else -> "Played on $device"
    }
}

/** The name on its bar, the type gem at its end. */
@Composable
private fun CardTitle(name: String, g: Game, p: CardPalette, d: CardDims) {
    Row(
        Modifier.fillMaxWidth().height(d.titleH).clip(RoundedCornerShape(d.corner * 0.5f)).background(p.panel)
            .padding(horizontal = d.pad * 0.7f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name, color = p.ink, fontSize = d.titleSp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(d.gap))
        TypeGem(g, d.gem)
    }
}

/** The card's "type": a round gem in the console's colour with a small controller (or Android) mark. */
@Composable
private fun TypeGem(g: Game, size: Dp) {
    val app = g.sourceType == SourceType.APP
    Box(
        Modifier.size(size).clip(CircleShape).background(consoleColor(g.platform))
            .border((size * 0.07f).coerceAtLeast(1.dp), Color.White.copy(alpha = 0.75f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (app) Icons.Filled.Android else Icons.Filled.SportsEsports, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
private fun StatLine(icon: ImageVector, label: String, value: String, p: CardPalette, d: CardDims) {
    Row(Modifier.fillMaxWidth().height(d.statH), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.muted, modifier = Modifier.size(d.icon))
        Spacer(Modifier.width(d.gap))
        Text(label, color = p.muted, fontSize = d.smallSp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.weight(0.42f))
        Text(
            value, color = p.ink, fontSize = d.bodySp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(0.58f)
        )
    }
}

/** Five stars: how many of the game's achievements you've earned (none without achievements). */
@Composable
private fun RarityStars(progress: Pair<Int, Int>?, p: CardPalette, d: CardDims) {
    val stars = if (progress == null || progress.first <= 0 || progress.second <= 0) 0
    else ceil(5f * progress.first / progress.second).toInt().coerceIn(1, 5)
    Row(Modifier.fillMaxWidth().height(d.statH), verticalAlignment = Alignment.CenterVertically) {
        Text("Rarity", color = p.muted, fontSize = d.smallSp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
        repeat(5) { s ->
            Icon(
                if (s < stars) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                tint = if (s < stars) p.star else p.muted.copy(alpha = 0.5f), modifier = Modifier.size(d.icon * 1.1f)
            )
        }
    }
}

/**
 * The card's shine: a soft light on its top-left corner (turned toward the light), and, while [sheen] runs from 0 to 1,
 * a rainbow band sweeping once across it. The brushes are made once per size; a sweep only moves them.
 */
private fun Modifier.cardShine(sheen: (() -> Float)?): Modifier = this.drawWithCache {
    val gloss = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent),
        center = Offset(size.width * 0.15f, size.height * 0.05f), radius = size.maxDimension * 0.75f
    )
    val bw = size.minDimension * 0.55f
    val band = Brush.linearGradient(
        0f to Color.Transparent, 0.3f to Color(0x40FF7AD9), 0.45f to Color(0x66FFFFFF), 0.6f to Color(0x4D7FE8FF),
        0.78f to Color(0x40FFF07A), 1f to Color.Transparent,
        start = Offset.Zero, end = Offset(bw, bw)
    )
    val travel = (size.width + size.height) / 2f + bw
    onDrawWithContent {
        drawContent()
        drawRect(gloss)
        val t = sheen?.invoke() ?: 1f
        if (t > 0f && t < 1f) {
            val s = -bw + t * travel
            translate(s, s) { drawRect(band, topLeft = Offset(-s, -s), size = size) }
        }
    }
}

/** The button hints under the hand (also buttons to tap). */
@Composable
private fun CardHints(compact: Boolean, aPlays: Boolean, onFlip: () -> Unit, onA: () -> Unit, modifier: Modifier) {
    Row(
        modifier, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HintChip("Y", "Turn over", compact, onFlip)
        HintChip("A", if (aPlays) "Play" else "Open", compact, onA)
    }
}

@Composable
private fun HintChip(letter: String, label: String, compact: Boolean, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { click() } }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(if (compact) 16.dp else 18.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, color = Color(0xFF1B1F27), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
    }
}
