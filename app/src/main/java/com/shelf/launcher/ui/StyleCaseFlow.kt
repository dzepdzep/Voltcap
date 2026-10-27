package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * Update 330: Case flow — the games as real 3D cases (Case3D: the game's front, its real spine, its back when turned) on a
 * curved carousel of its own.
 *
 * The case you're on stands in the middle, a little bigger, facing you; the others stand along a curve either side,
 * turned toward the middle (or with their spines toward you: Layout options › Cases either side), smaller and darker the
 * further out, their mirror images on a glossy floor. Moving glides the whole row — every case slides and turns to its
 * new place, nothing appears or vanishes at once (the outermost fade in and out). Each case keeps its console's real
 * proportions (a DS case is smaller than a PS2 one).
 * A brings the case forward and opens the game's window out of it; closing the window (B) sets it back. The window's
 * Inspect takes the case out to turn it round (its back, spine and sides).
 * D-pad: ◀ ▶ the cases (the home screen's own; Endless goes round with the row gliding on), ▲ ▼ what lies above / below.
 * Touch: tap a case to bring it to the middle, tap the middle one to open it, swipe along the row.
 */

/** Update 330: the dark stage behind the cases (Layout options › Stage; off: the theme's background). */
@Composable
internal fun CaseFlowBackdrop(modifier: Modifier) {
    if (!Features.cfStage.state.value) return
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF15171C), Color(0xFF0B0C10), Color(0xFF050608))))
        // A soft light over the middle, where the case you're on stands, and the floor's sheen.
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF3A4152).copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.42f), radius = size.width * 0.42f),
            radius = size.width * 0.42f, center = Offset(size.width / 2f, size.height * 0.42f)
        )
        // The floor: a faint sheen rising gently out of the dark, no hard edge (the reflections lie on it).
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 0.7f to Color.White.copy(alpha = 0.035f), 1f to Color.Transparent))
    }
}

internal fun caseFlowOwnsBackground(): Boolean = Features.cfStage.state.value

/** Update 330: Case flow's own keys on the games: ▲ ▼ go to what lies above / below; ◀ ▶ are the home screen's own. */
internal fun caseFlowKey(ctx: StyleKeyCtx): Boolean = when (ctx.key) {
    androidx.compose.ui.input.key.Key.DirectionUp -> ctx.reach(true)
    androidx.compose.ui.input.key.Key.DirectionDown -> ctx.reach(false)
    else -> false
}

@Composable
internal fun StyleCaseFlow(c: StyleCtx, modifier: Modifier = Modifier) {
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = emptyList()
        StyleNav.bar = emptyList()
    }
    // Plain if / else, never an early return out of the layout.
    if (c.games.isEmpty()) StyleEmpty(modifier) else CaseFlowRow(c, modifier)
}

@Composable
private fun CaseFlowRow(c: StyleCtx, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val needPhysical by rememberUpdatedState(c.actions.needPhysical)
    val latestFocus by rememberUpdatedState(c.focus)
    val count = c.games.size
    val latestCount by rememberUpdatedState(count)
    val loop = loopRowOn(c)
    val latestLoop by rememberUpdatedState(loop)
    val vFocus = rememberLoopFocus(c)
    val animate = rememberAnimationsEnabled()
    val glide = Features.rowGlide.state.value
    val speed = Features.cfSpeed.state.value
    val curve = Features.cfCurve.state.value / 100f
    val spacing = Features.cfSpacing.state.value / 100f
    val sizeK = Features.cfSize.state.value / 100f
    val angle = Features.cfAngle.state.value.toFloat()
    val pitch = Features.cfPitch.state.value.toFloat()
    val spines = Features.cfSide.state.value == Features.CfSide.SPINES
    val reflect = Features.cfReflect.state.value
    val showTitle = Features.cfTitle.state.value
    val keys = remember(c.games) { gameKeys(c.games) }

    // Where the row is (in cases), gliding from case to case.
    val pos = remember { Animatable(vFocus.toFloat()) }
    val shown = remember { arrayOfNulls<Any>(2) }
    val firstKey = keys.firstOrNull()
    LaunchedEffect(vFocus, count, firstKey) {
        val target = vFocus.toFloat()
        val newList = shown[0] != firstKey || shown[1] != count
        shown[0] = firstKey; shown[1] = count
        // A new category or a long jump goes straight there instead of racing past dozens of cases.
        if (!animate || newList || abs(pos.value - target) > 10f) pos.snapTo(target)
        else pos.animateTo(target, spring(dampingRatio = 0.86f, stiffness = glide.stiffness * speed.k))
    }
    // The window for the case you're on: the case comes forward while it's open, and goes back after.
    val g0 = c.game
    val windowOn = GamePopup.game != null && GamePopup.game?.stableKey == g0?.stableKey
    val expand by animateFloatAsState(if (windowOn) 1f else 0f, if (animate) spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow) else tween(0), label = "cfExpand")
    // The draw order changes only when the row passes half way between two cases.
    val centre by remember { derivedStateOf { pos.value.roundToInt() } }
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val nSide = if (loop) min(5, (count - 1) / 2).coerceAtLeast(1) else 5
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val titleH = if (showTitle) 62.dp else 0.dp
        val stageH = maxHeight - titleH
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { stageH.toPx() }
        val baseH = stageH * 0.64f * sizeK
        val baseHPx = with(density) { baseH.toPx() }
        // The middle case's half-width away, then the others close together.
        val x0 = baseHPx * 0.72f * spacing
        val step = baseHPx * 0.27f * spacing
        val cx = wPx / 2f
        val cy = hPx * 0.47f

        Box(
            Modifier.fillMaxWidth().height(stageH)
                .pointerInput(count) {
                    var acc = 0f
                    detectHorizontalDragGestures(onDragStart = { acc = 0f }) { ch, dx ->
                        ch.consume()
                        acc += dx
                        val s = step.coerceAtLeast(24f)
                        while (acc <= -s) {
                            acc += s
                            val n = latestCount
                            val next = latestFocus + 1
                            if (next < n) select(next) else if (latestLoop) select(0)
                        }
                        while (acc >= s) {
                            acc -= s
                            val n = latestCount
                            val prev = latestFocus - 1
                            if (prev >= 0) select(prev) else if (latestLoop) select(n - 1)
                        }
                    }
                }
        ) {
            val from = lo - nSide - 1
            val to = lo + nSide + 2
            for (v in from..to) {
                if (!loop && (v < 0 || v >= count)) continue
                val idx = if (loop) v.mod(count) else v
                val g = c.games[idx]
                key(v) {
                    val k = g.stableKey
                    val physical = c.state.physicalMedia[k]
                    LaunchedEffect(k) { if (physical == null) needPhysical(g) }
                    val aspect = case3dAspect(g, false)
                    val scaleK = realCaseScale(g.platform)
                    val hDp = baseH * scaleK
                    val wDp = hDp * aspect * 1.25f // room for the turn
                    val on = v == vFocus
                    Box(
                        Modifier.size(wDp, hDp).zIndex(-abs(v - centre).toFloat())
                            .graphicsLayer {
                                val r = v - pos.value
                                val a = abs(r)
                                val side = sign(r)
                                val near = min(a, 1f)
                                val far = max(a - 1f, 0f)
                                val x = side * (x0 * near + step * far)
                                translationX = cx + x - size.width / 2f
                                // Along the curve: the further out, the smaller and a little higher (receding).
                                val s = ((1f - 0.2f * near) * (1f - curve * 0.07f * far)).coerceAtLeast(0.35f) * (1f + 0.14f * expand * (1f - near))
                                scaleX = s; scaleY = s
                                translationY = cy - size.height / 2f - curve * baseHPx * 0.035f * far * far.coerceAtMost(4f) - expand * (1f - near) * baseHPx * 0.05f
                                alpha = (nSide + 1.3f - a).coerceIn(0f, 1f) * (1f - 0.35f * expand * near)
                            }
                            .pointerInput(v) {
                                detectTapGestures {
                                    if (on) open() else select(idx)
                                }
                            }
                    ) {
                        MediaFocus(on) {
                            val spin = if (on && expand < 0.01f) rememberCaseSpin() else null
                            Case3D(
                                g, c.cover(g), physical, 0f, aspect, Modifier.fillMaxSize().gameOrigin(k, on),
                                reflect = reflect, pitch = pitch,
                                yawOf = {
                                    val r = v - pos.value
                                    val near = min(abs(r), 1f)
                                    val turn = if (spines) near * 80f else -sign(r) * near * angle
                                    turn + (spin?.invoke() ?: 0f) * (1f - near)
                                },
                                shadeOf = {
                                    val a = abs(v - pos.value)
                                    (0.12f * min(a, 1f) + 0.08f * max(a - 1f, 0f)).coerceAtMost(0.55f)
                                },
                                realFront = c.state.artOverrides[k]?.cover == null, fill = 0.8f
                            )
                        }
                    }
                }
            }
        }
        if (showTitle && g0 != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(titleH).graphicsLayer { alpha = 1f - expand }, contentAlignment = Alignment.TopCenter) {
                AnimatedContent(targetState = g0, label = "cfTitle", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }) { g ->
                    CaseFlowTitle(c, g)
                }
            }
        }
    }
}

@Composable
private fun CaseFlowTitle(c: StyleCtx, g: Game) {
    val titles = LocalTitleOptions.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FullText(
            titles.display(g.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, textAlign = TextAlign.Center
        )
        val bits = listOfNotNull(
            titles.console(g.platform).takeIf { it.isNotBlank() },
            stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
            c.progress(g)?.let { (e, t) -> "$e / $t achievements" }
        )
        Spacer(Modifier.height(2.dp))
        FullText(bits.joinToString("  ·  "), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1)
    }
}

/** Update 330: Settings › Home › Layout options (Case flow). Every one its own "cf_…" Features pref. */
@Composable
internal fun CaseFlowSettings() {
    MutedNote(
        "Your games as real 3D cases on a curved carousel: the one you're on in the middle facing you, the others turned aside " +
            "along the curve, their mirror images on the floor. ◀ ▶ move, A brings the case forward and opens its window (Inspect " +
            "there turns it round: back, spine, sides), B sets it back. Tap a case to bring it to the middle, tap it again to " +
            "open it, or swipe along. Each console's case keeps its real size."
    )
    Spacer(Modifier.height(10.dp))
    StyleChoice("Cases either side show", Features.CfSide.values(), Features.cfSide.state.value, { it.label }) { Features.cfSide.value = it }
    PercentSlider("Curve", Features.cfCurve.state.value, { Features.cfCurve.value = it.coerceIn(0, 100) }, min = 0, max = 100,
        valueText = { if (it == 0) "Straight" else "$it%" })
    PercentSlider("Spacing", Features.cfSpacing.state.value, { Features.cfSpacing.value = it.coerceIn(60, 160) }, min = 60, max = 160, valueText = { "$it%" })
    PercentSlider("Case size", Features.cfSize.state.value, { Features.cfSize.value = it.coerceIn(70, 120) }, min = 70, max = 120, valueText = { "$it%" })
    PercentSlider("Turn of the cases either side", Features.cfAngle.state.value, { Features.cfAngle.value = it.coerceIn(25, 80) }, min = 25, max = 80, valueText = { "$it°" })
    PercentSlider("Looking down on them", Features.cfPitch.state.value, { Features.cfPitch.value = it.coerceIn(0, 14) }, min = 0, max = 14,
        valueText = { if (it == 0) "Level" else "$it°" })
    StyleChoice("Speed", Features.CfSpeed.values(), Features.cfSpeed.state.value, { it.label }) { Features.cfSpeed.value = it }
    Spacer(Modifier.height(8.dp))
    Toggle(Icons.Filled.Flip, "Reflections", "The cases' mirror images on a glossy floor.", Features.cfReflect)
    Toggle(Icons.Filled.WbSunny, "Dark stage", "A dark stage with a soft light over the middle. Off: your theme's background.", Features.cfStage)
    Toggle(Icons.Filled.Title, "Name under the cases", "The game's name, console, time played and achievements.", Features.cfTitle)
}

/**
 * Update 348: Case flow's carousel shapes for the R1 Carousel tile — each sets the curve, the turn of the cases either
 * side, the spacing and fronts / spines together (Layout options keeps every slider; changed there, the tile says
 * "Your own").
 */
internal enum class CaseFlowShape(val label: String, val curve: Int, val angle: Int, val spacing: Int, val side: Features.CfSide) {
    CURVED("Curved", 45, 60, 100, Features.CfSide.FRONTS),
    DEEP("Deep arc", 85, 70, 110, Features.CfSide.FRONTS),
    FLAT("Flat row", 0, 35, 110, Features.CfSide.FRONTS),
    TURNED("Turned row", 10, 75, 75, Features.CfSide.FRONTS),
    SPINES("Spines", 20, 60, 100, Features.CfSide.SPINES),
    SHELF("Spine shelf", 0, 80, 70, Features.CfSide.SPINES);

    fun apply() {
        Features.cfCurve.value = curve; Features.cfAngle.value = angle; Features.cfSpacing.value = spacing; Features.cfSide.value = side
    }

    companion object {
        /** The shape the sliders are at now (read as state), or null when they're set to something of your own. */
        fun current(): CaseFlowShape? {
            val c = Features.cfCurve.state.value; val a = Features.cfAngle.state.value
            val sp = Features.cfSpacing.state.value; val sd = Features.cfSide.state.value
            return values().firstOrNull { it.curve == c && it.angle == a && it.spacing == sp && it.side == sd }
        }
    }
}
