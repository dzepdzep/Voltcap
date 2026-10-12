package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.ConsolePhotos
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.data.isCollectionKey
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * Update 146: Console stack (Home layout › Console stack).
 *
 * Your categories as a pile of hardware on a shelf down the left of the screen: each console a drawn box in its own
 * colour (vents, a slot, a power light) with its logo — or its photo, where Voltcap can find one (Console pictures).
 * All, Favourites, your collections and the other lists sit in the pile too, as plain grey units with their symbol, so
 * ▲ ▼ go through exactly the same stops as L2 / R2. The console of the category you're in is pulled out of the pile
 * and lit; its games slide out of its slot in a fanned row to the right, the one you're on bigger, with its name and
 * time played under the row.
 * ▲ ▼ the console above / below (past the top / bottom one: the status bar or player, HomeReach; in a list outside the
 * cycle, e.g. Rediscover, either one pulls out the unit in the middle), ◀ ▶ the games.
 * Touch: tap a console to pull it out, swipe the pile up / down; tap a game to pick it, tap it again to open it, swipe
 * the row sideways.
 */

/** What the key handler needs from the last drawing: how many units the pile has and which one is pulled out. */
private object ConsoleStackNav {
    @Volatile var stops = 0
    @Volatile var stop = -1
}

@Composable
internal fun StyleConsoleStack(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        // No menus of its own: ▲ ▼ are the pile, ◀ ▶ the games (the round shortcuts stay in the corner ☰).
        StyleNav.side = emptyList()
        StyleNav.bar = emptyList()
        StyleNav.top = emptyList()
        ConsoleStackNav.stops = c.stops.size
        ConsoleStackNav.stop = c.stop
    }
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 380.dp
        val hasPile = c.stops.isNotEmpty()
        // The pile takes about a quarter of the width (narrower on the small square-ish screens, never too thin to read).
        val pileW = if (hasPile) (maxWidth * 0.27f).coerceIn(120.dp, 270.dp) else 0.dp
        val unitH = (pileW * 0.36f).coerceAtMost(maxHeight / 5.2f).coerceAtLeast(34.dp)
        val pull = pileW * 0.16f
        // Room under the row for the game's name and time played; the pulled-out console and the row share one middle line.
        val infoH = if (compact) 50.dp else 64.dp
        val centreY = (maxHeight - infoH) / 2f + 4.dp
        // The row starts right at the pulled-out console's slot (its scale-up adds 3 % each side), so the games come out of it.
        val rowStart = if (hasPile) 6.dp + pileW * 1.03f + pull + 4.dp else 0.dp
        if (hasPile) ConsolePile(
            c, pileW, unitH, pull, centreY, maxHeight,
            Modifier.padding(start = 6.dp).fillMaxHeight().width(pileW * 1.06f + pull + 6.dp)
        )
        val rowMod = Modifier.padding(start = rowStart).fillMaxSize()
        // Plain if / else (never an early return out of the Box: the dexer rejects it).
        if (c.games.isEmpty()) StyleEmpty(rowMod)
        else StackGames(c, centreY, infoH, compact, rowMod.clipToBounds())
    }
}

/**
 * The pile: a unit for every stop of the category cycle, the one you're in held in the middle of the screen and pulled
 * out. It glides up / down as the category changes; only the units near the screen are composed.
 */
@Composable
private fun ConsolePile(c: StyleCtx, pileW: Dp, unitH: Dp, pull: Dp, centreY: Dp, height: Dp, modifier: Modifier) {
    val n = c.stops.size
    val here = c.stop.coerceIn(0, n - 1)
    val glide = Features.rowGlide.state.value
    val step = unitH + 12.dp
    val pos = remember { Animatable(here.toFloat()) }
    LaunchedEffect(here) {
        val t = here.toFloat()
        // A long jump (L2 / R2 from the last stop round to the first): start right next to it, not a race up the pile.
        if (abs(pos.value - t) > 3f) pos.snapTo(t + if (pos.value < t) -1f else 1f)
        pos.animateTo(t, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
    }
    val category by rememberUpdatedState(c.actions.category)
    val currentStop by rememberUpdatedState(c.stop)
    val stopCount by rememberUpdatedState(n)
    val stepNow by rememberUpdatedState(step)
    // To unit [i]: the right number of category steps from where you are. Outside the cycle (e.g. Rediscover) the
    // first step counts from the top unit, as L2 / R2 do.
    fun jumpTo(i: Int) {
        val from = currentStop
        if (from < 0) {
            if (i == 0) { category(1); category(-1) } else category(i)
        } else if (i != from) category(i - from)
    }
    val above = ceil(centreY / step).toInt() + 1
    val below = ceil((height - centreY) / step).toInt() + 1
    // (A glide is never more than 3 units long, see above: that's the margin.)
    val lo = (here - above - 3).coerceAtLeast(0)
    val hi = (here + below + 3).coerceAtMost(n - 1)
    val accent = ShelfTheme.accent
    val density = LocalDensity.current
    val centrePx = with(density) { centreY.toPx() }
    val stepPx = with(density) { step.toPx() }
    val unitHPx = with(density) { unitH.toPx() }
    val pullPx = with(density) { pull.toPx() }
    Box(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                // Swipe the pile: one console per unit's height (up = the next one comes into the middle).
                var acc = 0f
                var at = 0
                detectVerticalDragGestures(
                    onDragStart = { acc = 0f; at = currentStop.coerceAtLeast(0) },
                    onDragEnd = { acc = 0f },
                    onDragCancel = { acc = 0f }
                ) { change, dy ->
                    change.consume()
                    acc += dy
                    val s = stepNow.toPx()
                    while (acc <= -s) { acc += s; if (at + 1 < stopCount) { at++; category(1) } }
                    while (acc >= s) { acc -= s; if (at > 0) { at--; category(-1) } }
                }
            }
    ) {
        // The rack: two faint uprights and a shelf board under each unit (drawn from the glide, no recomposing).
        val boardInk = themeDark(Color(0xFF1C1F24)) // Update 151: the boards take the theme's colour.
        Canvas(Modifier.width(pileW).fillMaxHeight()) {
            val rail = Color.White.copy(alpha = 0.07f)
            drawLine(rail, Offset(2.dp.toPx(), 0f), Offset(2.dp.toPx(), size.height), strokeWidth = 3.dp.toPx())
            drawLine(rail, Offset(size.width - 2.dp.toPx(), 0f), Offset(size.width - 2.dp.toPx(), size.height), strokeWidth = 3.dp.toPx())
            val boardH = 4.dp.toPx()
            for (i in lo..hi) {
                val y = centrePx + (i - pos.value) * stepPx + unitHPx / 2f + 2.dp.toPx()
                if (y < -boardH || y > size.height + boardH) continue
                drawRect(boardInk.copy(alpha = 0.85f), Offset(0f, y), Size(size.width, boardH))
                drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
        }
        for (i in lo..hi) {
            val stop = c.stops[i]
            key("${stop.special}|${stop.platform}|${stop.label}") {
                val on = i == c.stop
                // Pulled out (with a little give at the end) and lit.
                val lit = animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = glide.stiffness), label = "stackPull")
                ConsoleUnit(
                    stop, lit, accent, pileW, unitH,
                    Modifier
                        .offset { IntOffset(0, (centrePx + (i - pos.value) * stepPx - unitHPx / 2f).roundToInt()) }
                        .zIndex(if (on) 2f else 1f)
                        .graphicsLayer {
                            val l = lit.value
                            translationX = pullPx * l
                            val s = 1f + 0.06f * l
                            scaleX = s; scaleY = s
                            // The units further up / down fade a little, so the pulled-out one reads first.
                            val dist = abs(i - pos.value)
                            alpha = maxOf((1.15f - dist * 0.17f).coerceIn(0.3f, 1f), l)
                        }
                        .pointerInput(i) { detectTapGestures { jumpTo(i) } }
                )
            }
        }
    }
}

/** One unit of the pile: the console's photo on a plate, or its drawn box with its logo (other stops: their symbol). */
@Composable
private fun ConsoleUnit(stop: StyleStop, lit: State<Float>, accent: Color, w: Dp, h: Dp, modifier: Modifier) {
    // Only the real consoles get a console's box and colour; All, the lists and your collections are plain units.
    val platform = stop.platform?.takeIf { stop.special == null && !isCollectionKey(it) }
    val photosOn = Features.consolePhotos.state.value
    val context = LocalContext.current
    val photo by produceState<String?>(null, platform, photosOn) {
        value = if (photosOn && platform != null && ConsolePhotos.hasPhoto(platform)) {
            runCatching { ConsolePhotos.url(context, platform) }.getOrNull()
        } else null
    }
    var failed by remember(photo) { mutableStateOf(false) }
    val color = when {
        platform != null -> consoleColor(platform)
        stop.platform != null -> Color(0xFF6B5634)
        else -> Color(0xFF3B414B)
    }
    val bodyH = h * 0.9f
    val shown = photo
    Box(modifier.size(w, h)) {
        if (platform != null && shown != null && !failed) {
            // The photo on a dark plate (the plate's edge lights up when it's pulled out), the logo beside it.
            Canvas(Modifier.fillMaxSize()) {
                val l = lit.value
                val r = CornerRadius(8.dp.toPx())
                if (l > 0.01f) drawRoundRect(accent.copy(alpha = 0.2f * l), Offset(-3.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(11.dp.toPx()))
                drawRoundRect(Color.Black.copy(alpha = 0.34f), cornerRadius = r)
                drawRoundRect(lerp(Color.White.copy(alpha = 0.12f), accent, l), cornerRadius = r, style = Stroke((1f + l).dp.toPx()))
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PlatformLogo(platform, stop.label, Modifier.weight(0.4f).fillMaxHeight(0.5f), textSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                AsyncImage(
                    shown, stop.label, contentScale = ContentScale.Fit, onError = { failed = true },
                    modifier = Modifier.weight(0.6f).fillMaxHeight()
                )
            }
        } else {
            ConsoleBody(color, accent, { lit.value }, Modifier.fillMaxSize())
            // The front of the box, left of the slot: the logo (or the stop's symbol and name).
            Box(Modifier.offset(x = w * 0.06f, y = bodyH * 0.27f).size(w * 0.5f, bodyH * 0.58f), contentAlignment = Alignment.CenterStart) {
                if (platform != null) {
                    PlatformLogo(platform, stop.label, Modifier.fillMaxSize(), textSize = if (h < 50.dp) 9.sp else 11.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(stop.icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size((bodyH * 0.4f).coerceAtMost(22.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stop.label, color = Color.White.copy(alpha = 0.92f), fontSize = if (h < 50.dp) 9.sp else 11.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * A console's box, Voltcap's own drawing (no real console's shape): a rounded body in [color] with a lid seam, a slot
 * the games come out of, vents under it, a power light and two feet. [lit] (0…1, read while drawing) lights the slot,
 * the power light and the rim in the accent colour.
 */
@Composable
private fun ConsoleBody(color: Color, accent: Color, lit: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val l = lit()
        val w = size.width
        val h = size.height
        val bodyH = h * 0.9f
        val r = CornerRadius(h * 0.12f)
        // Feet.
        val foot = Color.Black.copy(alpha = 0.55f)
        drawRoundRect(foot, Offset(w * 0.08f, bodyH - 2f), Size(w * 0.08f, h - bodyH + 2f), CornerRadius(2.dp.toPx()))
        drawRoundRect(foot, Offset(w * 0.84f, bodyH - 2f), Size(w * 0.08f, h - bodyH + 2f), CornerRadius(2.dp.toPx()))
        // The glow round it when it's pulled out.
        if (l > 0.01f) {
            val g = 4.dp.toPx()
            drawRoundRect(accent.copy(alpha = 0.22f * l), Offset(-g, -g), Size(w + 2 * g, bodyH + 2 * g), CornerRadius(r.x + g))
        }
        // The body: lighter on top, darker at the bottom.
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(color, Color.White, 0.22f), color, lerp(color, Color.Black, 0.45f)), 0f, bodyH),
            size = Size(w, bodyH), cornerRadius = r
        )
        drawLine(Color.White.copy(alpha = 0.28f), Offset(r.x, 1.5.dp.toPx()), Offset(w - r.x, 1.5.dp.toPx()), strokeWidth = 1.dp.toPx())
        // The lid's seam.
        drawLine(Color.Black.copy(alpha = 0.25f), Offset(0f, bodyH * 0.22f), Offset(w, bodyH * 0.22f), strokeWidth = 1.dp.toPx())
        // The slot, lit from inside when the games are out.
        val sx = w * 0.6f
        val sw = w * 0.33f
        val sh = maxOf(bodyH * 0.14f, 3.dp.toPx())
        val sy = bodyH * 0.34f
        drawRoundRect(Color.Black.copy(alpha = 0.75f), Offset(sx, sy), Size(sw, sh), CornerRadius(sh / 2f))
        if (l > 0.01f) drawRoundRect(accent.copy(alpha = 0.85f * l), Offset(sx + 2f, sy + sh * 0.3f), Size(sw - 4f, sh * 0.4f), CornerRadius(sh / 2f))
        // Vents under the slot.
        val vy = bodyH * 0.6f
        val vh = bodyH * 0.22f
        for (v in 0 until 7) {
            val vx = sx + sw * (v + 0.5f) / 7f
            drawLine(Color.Black.copy(alpha = 0.4f), Offset(vx, vy), Offset(vx, vy + vh), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        // The power light, right of the slot.
        val led = Offset(w * 0.96f, bodyH * 0.41f)
        val ledR = maxOf(bodyH * 0.045f, 2.dp.toPx())
        if (l > 0.01f) drawCircle(accent.copy(alpha = 0.35f * l), ledR * 2.6f, led)
        drawCircle(lerp(Color(0xFF3A3F45), accent, l), ledR, led)
        // The rim.
        if (l > 0.01f) drawRoundRect(accent.copy(alpha = l), size = Size(w, bodyH), cornerRadius = r, style = Stroke(2.dp.toPx()))
        else drawRoundRect(Color.White.copy(alpha = 0.12f), size = Size(w, bodyH), cornerRadius = r, style = Stroke(1.dp.toPx()))
    }
}

/**
 * The games sliding out of the pulled-out console: a fanned row, the one you're on big, a game or two before it and the
 * rest after it (Console stack › Games sliding out, as many as fit). Moving glides the row (Moving between games
 * speed); a new console sends its games out of the slot one after another. Only the games near the one you're on are composed.
 */
@Composable
private fun StackGames(c: StyleCtx, centreY: Dp, infoH: Dp, compact: Boolean, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(c.games.size)
    val count = c.games.size
    val keys = remember(c.games) { gameKeys(c.games) }
    val glide = Features.rowGlide.state.value
    val wanted = Features.consoleStackGames.state.value.coerceIn(3, 9)
    // The category you're in (the stop alone is -1 for every list outside the cycle, so its name too).
    val catKey = "${c.stop}|${c.categoryLabel}"
    // Update 156: with Endless on the games repeat round the row and the place you're on counts on (EndlessRow.kt), so
    // going past the last game to the first slides one game on, like any step. Off: just the game you're on.
    val loop = loopRowOn(c)
    val vFocus = rememberLoopFocus(c)
    val latestV by rememberUpdatedState(vFocus)
    val pos = remember { Animatable(vFocus.toFloat()) }
    val lastCat = remember { arrayOf<Any?>(catKey) }
    LaunchedEffect(vFocus, catKey) {
        val t = vFocus.toFloat()
        if (lastCat[0] != catKey) {
            // A new console: its games are placed at once (they slide out of the slot instead, below).
            lastCat[0] = catKey
            pos.snapTo(t)
        } else {
            if (abs(pos.value - t) > 3f) pos.snapTo(t + if (pos.value < t) -1f else 1f)
            pos.animateTo(t, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness))
        }
    }
    // 0 → 1: the games coming out of the slot (each one a little after the one before).
    val out = remember { Animatable(0f) }
    LaunchedEffect(catKey) {
        out.snapTo(0f)
        out.animateTo(1f, tween(glide.carouselMs + 260, easing = FastOutSlowInEasing))
    }
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE)
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            // Swipe the row: one game per step.
            var acc = 0f
            var at = 0
            detectHorizontalDragGestures(onDragStart = { acc = 0f; at = latestFocus }, onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dx ->
                change.consume()
                acc += dx
                val s = 56.dp.toPx()
                while (acc <= -s) { acc += s; if (at + 1 < latestCount) { at++; select(at) } }
                while (acc >= s) { acc -= s; if (at > 0) { at--; select(at) } }
            }
        }
    ) {
        val width = maxWidth
        val aspect = styleAspect(c.type).coerceIn(0.6f, 1.8f)
        val k = GameSize.scale(HomeLayout.CONSOLES)
        // The big card: as tall as the screen allows with the name under it, and narrow enough that a game before it
        // and at least two after it fit beside it.
        // (Never so tall the name under it is cut off at the bottom, even at a large game size; never below a sliver on a
        // tiny window, where a negative size would crash.)
        val bigH = (minOf(maxHeight * 0.58f, maxHeight - infoH - 24.dp, (width - 32.dp) / 3f / aspect) * k)
            .coerceIn(56.dp, 340.dp).coerceAtMost(maxHeight - infoH - 20.dp).coerceAtLeast(24.dp)
        val bigW = bigH * aspect
        val smallRatio = 0.7f
        val smallW = bigW * smallRatio
        val gap = 10.dp
        val near = bigW / 2 + gap + smallW / 2
        val far = smallW * 0.72f
        val before = ((wanted - 1) / 3).coerceAtLeast(1)
        // Where the big card's middle sits: right by the slot on the first game, then far enough in for [before] games.
        fun anchorAt(b: Float): Dp = 8.dp + bigW / 2 + if (b <= 1f) (gap + smallW) * b else gap + smallW + far * (b - 1f)
        val anchorMax = anchorAt(before.toFloat())
        val afterFit = (((width - 4.dp - smallW / 2 - anchorMax - near) / far).toInt() + 1).coerceAtLeast(1)
        val shown = minOf(wanted, before + 1 + afterFit)
        val latestShown by rememberUpdatedState(shown)
        val latestBefore by rememberUpdatedState(before)
        val density = LocalDensity.current
        val bigWPx = with(density) { bigW.toPx() }
        val nearPx = with(density) { near.toPx() }
        val farPx = with(density) { far.toPx() }
        val dropPx = with(density) { 5.dp.toPx() }
        val anchor0Px = with(density) { (8.dp + bigW / 2).toPx() }
        val anchorNearPx = with(density) { (gap + smallW).toPx() }
        // (Update 156: on the endless row these are places on it, not games; [before] games always stand before yours.)
        val focusT = vFocus
        val windowLo = if (loop) focusT - before else (focusT - before).coerceAtLeast(0)
        val lo = if (loop) windowLo - 4 else (windowLo - 4).coerceAtLeast(0)
        val hi = if (loop) windowLo + shown + 3 else (windowLo + shown + 3).coerceAtMost(count - 1)
        // Caption over the row: the category and how many games it has (where there's room above the big card).
        val captionY = centreY - bigH / 2 - 26.dp
        if (captionY >= 0.dp) Row(
            Modifier.offset(x = 8.dp, y = captionY).graphicsLayer { alpha = out.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                c.categoryLabel, color = Color.White, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW),
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                "  ·  " + if (count == 1) "1 game" else "$count games", color = ShelfTheme.accent, fontSize = if (compact) 11.sp else 12.sp,
                maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW)
            )
        }
        for (i in lo..hi) {
            // Update 156: [i] is the place on the row, [real] the game standing there (one key per copy).
            val real = if (loop) i.mod(count) else i
            val g = c.games[real]
            key(if (loop) "${keys[real]}#${Math.floorDiv(i, count)}" else keys[i]) {
                val on = i == focusT
                val sel = on && inGames
                val slot = (i - windowLo).coerceAtLeast(0)
                Box(
                    Modifier
                        .offset(y = centreY - bigH / 2)
                        .size(bigW, bigH)
                        .zIndex(if (on) 20f else 10f - abs(i - focusT).coerceAtMost(9))
                        .graphicsLayer {
                            val p = pos.value
                            val d = i - p
                            val ad = abs(d)
                            val grow = (1f - ad).coerceIn(0f, 1f)
                            val s = smallRatio + (1f - smallRatio) * grow
                            val along = if (ad <= 1f) d * nearPx else sign(d) * (nearPx + (ad - 1f) * farPx)
                            // (Update 156: the endless row always has games before yours, so the big card stays put.)
                            val b = if (loop) before.toFloat() else p.coerceIn(0f, before.toFloat())
                            val anchor = anchor0Px + if (b <= 1f) anchorNearPx * b else anchorNearPx + farPx * (b - 1f)
                            val targetX = anchor + along - bigWPx / 2f
                            // Out of the slot: from just left of the row (hidden behind its edge), one after another.
                            val e = (out.value * (1f + 0.12f * shown) - 0.12f * slot).coerceIn(0f, 1f)
                            val startX = -bigWPx * 0.95f
                            translationX = startX + (targetX - startX) * e
                            // Fanned like a hand of cards: the further from the one you're on, the lower and the more turned.
                            translationY = minOf(ad, 4f) * dropPx * e
                            rotationZ = d.coerceIn(-3f, 3f) * 4f * e
                            scaleX = s
                            scaleY = s
                            // Only [shown] games: the ones past either end fade away as the row moves.
                            val loF = if (loop) p - before else (p - before).coerceAtLeast(0f)
                            val hiF = loF + shown - 1
                            val win = when {
                                i < loF -> 1f - (loF - i)
                                i > hiF -> 1f - (i - hiF)
                                else -> 1f
                            }.coerceIn(0f, 1f)
                            alpha = win * (0.82f + 0.18f * grow) * (0.35f + 0.65f * e)
                        }
                        // (The game window grows out of the card you're on, like on the other layouts. After the layer, so
                        // it reports where the card really is on screen, not where it sits before it's moved into the row.)
                        .then(if (on) Modifier.focusedCard() else Modifier)
                        .pointerInput(i, real, loop) {
                            detectTapGestures {
                                // A faded-out game past the end of the row isn't there to tap.
                                // (Update 156: on the endless row the window is counted in places on it.)
                                val f = if (loop) latestV else GridHome.focus
                                val wLo = if (loop) f - latestBefore else (f - latestBefore).coerceAtLeast(0)
                                if (i in wLo until wLo + latestShown) {
                                    if (i == f || real == GridHome.focus) open() else select(real)
                                }
                            }
                        }
                ) {
                    StyleArt(c, g, Modifier.fillMaxSize(), focused = sel, fx = if (sel) pulse else null)
                }
            }
        }
        // The game's name and time played under the row (the next one's rises into place).
        val g0 = c.game
        if (g0 != null) Box(
            Modifier.offset(x = 8.dp, y = centreY + bigH / 2 + 12.dp).width((width - 16.dp).coerceAtLeast(0.dp)).height(infoH - 8.dp)
                .graphicsLayer { alpha = out.value }
        ) {
            AnimatedContent(targetState = g0, label = "stackInfo", transitionSpec = { gameSwap() }) { shownGame ->
                StackInfo(c, shownGame, compact)
            }
        }
    }
}

/** Name, console, time played and last played of [g]. */
@Composable
private fun StackInfo(c: StyleCtx, g: Game, compact: Boolean) {
    val titles = LocalTitleOptions.current
    Column {
        FitText(
            titles.display(g.name), Color.White, maxSize = if (compact) 16.sp else 20.sp, minSize = 11.sp, maxLines = 1,
            fontWeight = FontWeight.Bold, style = nameTextStyle(NameBackdrop.SHADOW)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            titles.console(g.platform) + "  ·  ◷ " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null) +
                (c.lastPlayed(g)?.let { "  ·  Last played: " + lastPlayedLabel(it) } ?: ""),
            color = Color.White.copy(alpha = 0.82f), fontSize = if (compact) 11.sp else 12.sp, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW)
        )
    }
}

/**
 * Console stack's keys on the games: ▲ ▼ the console above / below (a category step, like L2 / R2); past the top or
 * bottom console, what lies beyond (status bar, music player). ◀ ▶ are the home screen's own (previous / next game).
 */
internal fun consoleStackKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    Key.DirectionUp, Key.DirectionDown -> {
        val down = ctx.key == Key.DirectionDown
        val n = ConsoleStackNav.stops
        val at = ConsoleStackNav.stop
        if (n > 0 && at < 0) {
            // Outside the cycle (e.g. Rediscover) the top unit sits in the middle, not pulled out: ▲ or ▼ pulls it out
            // (a step forward and back lands on it, as a tap on it does), rather than skipping past it or wrapping round.
            ctx.category(true)
            ctx.category(false)
            ConsoleStackNav.stop = 0
            ctx.sound(UiSound.MOVE)
            ctx.take()
        } else if (n > 1 && (if (down) at < n - 1 else at > 0)) {
            ctx.category(down)
            // Where it is now, before the next drawing reports it (a held key repeats faster than that can lag behind,
            // and must not wrap round past the last console).
            ConsoleStackNav.stop = at + if (down) 1 else -1
            ctx.sound(UiSound.MOVE)
            ctx.take()
        } else ctx.reach(!down)
    }
    else -> false
}

@Composable
internal fun ConsoleStackSettings() {
    MutedNote("Your consoles stacked on a shelf down the left: the one you're in is pulled out and lit, and its games slide out of it in a fanned row. ▲ ▼ change the console, ◀ ▶ the game. Tap a console to pull it out, tap a game to pick it (again to open it).")
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        Icons.Filled.Image, "Console pictures",
        "A photo of each console where one can be found (looked up once, needs the internet). Off: each console drawn as a box in its colour with its logo.",
        Features.consolePhotos.state.value, { Features.consolePhotos.value = it }
    )
    Spacer(Modifier.height(10.dp))
    Text("Games sliding out", color = ShelfTheme.textMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    val current = Features.consoleStackGames.state.value
    ChipFlow { (3..9).forEach { v -> Chip("$v", accent = v == current) { Features.consoleStackGames.value = v } } }
    Spacer(Modifier.height(4.dp))
    MutedNote("How many games come out of the console around the one you're on (fewer on a narrow screen, as many as fit).")
    Spacer(Modifier.height(8.dp))
}
