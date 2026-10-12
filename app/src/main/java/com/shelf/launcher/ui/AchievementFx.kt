package com.shelf.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Features.AchievementTheme
import com.shelf.launcher.ui.theme.Ds
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------------
// Achievement themes: how badges, their frames and the area behind them look. Independent of the
// launcher theme (or "Follow the app theme"). Intensity scales glow and motion; Low is also the
// performance setting (no animated backdrop).
// ---------------------------------------------------------------------------------------------------

internal data class AchievementStyle(
    val theme: AchievementTheme,
    val frameOn: Color,
    val frameOff: Color,
    val plateOn: List<Color>,
    val plateOff: List<Color>,
    val radius: Dp,
    val glow: Color,
    val text: Color,
    val font: FontFamily? = null,
    /** 0.45 (low) … 1.45 (high). */
    val amount: Float = 1f,
    val animatedBackdrop: Boolean = true
) {
    val shape: Shape get() = RoundedCornerShape(radius)
}

private val GOLD = Color(0xFFF2C14E)

/**
 * The one achievement look, shared by every place achievements appear (lists, badge walls, the
 * profile, pop-ups): it follows the app theme's colours. (There used to be separate "achievement
 * themes"; achievement touches now live inside the normal backdrop instead — BackdropAchievements.)
 */
@Composable
internal fun achievementStyle(): AchievementStyle {
    val intensity = Features.achievementIntensity.state.value
    val accent = ShelfTheme.accent
    return AchievementStyle(
        AchievementTheme.INHERIT, frameOn = accent.copy(alpha = 0.85f), frameOff = Color.White.copy(alpha = 0.12f),
        plateOn = listOf(accent.copy(alpha = 0.42f), Color(0xFF15161C)),
        plateOff = listOf(Color(0xFF2A2C34), Color(0xFF17181D)), radius = 12.dp, glow = accent,
        text = ShelfTheme.colors.textPrimary, amount = intensity.amount,
        animatedBackdrop = intensity != Features.Intensity.LOW
    )
}

/** Behind the badges: nothing extra any more — the app theme's own background shows (kept for callers). */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.achievementBackdrop(style: AchievementStyle, phase: Float): Modifier = this

// ---------------------------------------------------------------------------------------------------
// The automatic new-unlock animation (plays once per unlock, never for old ones):
//   flash / glow → rise and pop out of the slot → light impact → hovers and hops like a capsule →
//   settles back and locks in with another light impact. Several unlocks play one after another,
//   then one summary line: "4 achievements unlocked · 31/70 · 44% complete".
// ---------------------------------------------------------------------------------------------------

/** One badge in the new-unlock stage. */
internal data class StageBadge(val id: Int, val image: String?, val title: String, val points: Int?)

@Composable
internal fun NewUnlockStage(
    badges: List<StageBadge>,
    unlocked: Int,
    total: Int,
    modifier: Modifier = Modifier,
    /** Called once every badge has played (the caller marks them as animated then). */
    onPlayed: () -> Unit
) {
    if (badges.isEmpty()) return
    val style = achievementStyle()
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    val withSound = com.shelf.launcher.data.AchievementLook.sounds.value
    val played by rememberUpdatedState(onPlayed)
    // Per badge: 0 = waiting in its slot (dim), 1 = done. The badge being animated reads [t].
    var current by remember { mutableIntStateOf(-1) }
    val t = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // (No endless backdrop clock: the backdrop draws nothing now, and reading it recomposed the stage every frame.)
        if (withSound) RetroFx.warm(context)
        delay(350)
        for (i in badges.indices) {
            current = i
            t.snapTo(0f)
            // 0.00-0.14 flash · 0.14-0.34 rise & pop · 0.34 impact · 0.34-0.78 hover/hop · 0.78-1 settle & lock.
            val total = (1500 / style.amount.coerceIn(0.8f, 1.3f)).toInt()
            launch {
                delay((total * 0.34f).toLong())
                if (withSound) RetroFx.play(context, sounds, RetroSound.FLY, pitch = 1.1f)
                sounds?.buzz(UiSound.MOVE)
                delay((total * 0.62f).toLong())
                if (withSound) RetroFx.play(context, sounds, RetroSound.LOCK, pitch = 1f + (i % 4) * 0.06f)
                sounds?.buzz(UiSound.SELECT)
            }
            t.animateTo(1f, tween(total, easing = LinearEasing))
            delay(140)
        }
        current = badges.size
        finished = true
        played()
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ds.rLarge))
            .background(Brush.verticalGradient(listOf(style.glow.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.25f))))
            .achievementBackdrop(style, 0f)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            if (badges.size == 1) "NEW UNLOCK" else "NEW UNLOCKS", color = style.glow, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontFamily = style.font
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Update 144: many unlocks at once (after playing offline) wrap onto more rows instead of being squashed or cut off.
            val perRow = (maxWidth / 64.dp).toInt().coerceAtLeast(1)
            val cell = (maxWidth / minOf(badges.size, perRow).coerceAtLeast(1)).coerceIn(64.dp, 104.dp)
            // Each slot is the badge + 22 dp wide: that is exactly one cell, so the row never runs past the screen; and no
            // fixed height, so the capsule and the title under the badge get their room (update 91).
            val badge = cell - 22.dp
            Column(Modifier.fillMaxWidth()) {
                badges.withIndex().chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        row.forEach { (i, b) ->
                            val p = when {
                                i < current -> 1f
                                i == current -> t.value
                                else -> 0f
                            }
                            StageSlot(b, p, waiting = i > current || (i == current && p == 0f), size = badge, style = style)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.animation.AnimatedVisibility(visible = finished, enter = androidx.compose.animation.fadeIn(tween(260))) {
            val pct = if (total > 0) unlocked * 100 / total else 0
            Text(
                "${badges.size} achievement${if (badges.size == 1) "" else "s"} unlocked · " +
                    (if (total > 0) "$unlocked/$total · $pct% complete" else "$unlocked unlocked"),
                color = style.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = style.font,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StageSlot(b: StageBadge, p: Float, waiting: Boolean, size: Dp, style: AchievementStyle) {
    // Phases of p (0..1): the capsule charges (outline → bolt → full flare), the badge flashes and pops
    // up out of it, hops on the air above it, then drops back in and locks with a second flare.
    fun seg(a: Float, z: Float) = ((p - a) / (z - a)).coerceIn(0f, 1f)
    val charge = seg(0f, 0.30f)
    val flash = if (p < 0.14f) seg(0f, 0.14f) else 1f - seg(0.14f, 0.30f)
    val rise = FastOutSlowInEasing.transform(seg(0.20f, 0.36f))
    val hover = seg(0.36f, 0.78f)
    val settle = FastOutSlowInEasing.transform(seg(0.78f, 0.92f))
    val lockFlare = (1f - abs(seg(0.88f, 1f) * 2f - 1f)).coerceIn(0f, 1f)
    val amp = style.amount.coerceIn(0.6f, 1.4f)
    // Two small hops while hovering (a capsule bouncing on air).
    val hop = abs(sin(hover * 2 * PI.toFloat())) * 7f * amp * (1f - hover * 0.4f)
    val lift = (rise * 30f * amp + hop) * (1f - settle)
    val scale = 1f + (0.2f * amp) * rise * (1f - settle)
    val done = p >= 1f
    // The capsule: dark before its turn, then charging, flaring at launch and lock, glowing steady after.
    val capsuleCharge = if (waiting) 0.12f else if (done) 1f else charge
    val capsuleFlare = if (waiting) 0f else maxOf(if (p in 0.2f..0.4f) 1f - abs((p - 0.3f) / 0.1f) else 0f, lockFlare)
    // Embers and the lightning use the slow clock below.
    val clock = androidx.compose.animation.core.rememberInfiniteTransition(label = "capsule")
    val phase by clock.animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "embers"
    )
    Column(Modifier.width(size + 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(size + 22.dp).height(size + 30.dp), contentAlignment = Alignment.BottomCenter) {
            // Lightning between the capsule and the badge while it's in the air.
            if (!waiting && lift > 2f) ChargeArc(lift, phase, style.glow, Modifier.matchParentSize())
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .size(size)
                    .graphicsLayer {
                        translationY = -lift.dp.toPx()
                        scaleX = scale; scaleY = scale
                        alpha = if (waiting) 0.3f else 1f
                    }
                    .clip(style.shape)
                    .background(Brush.linearGradient(if (waiting) style.plateOff else style.plateOn))
                    .border(2.dp, if (waiting) style.frameOff else style.frameOn, style.shape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (b.image != null) AsyncImage(
                    model = b.image, contentDescription = b.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(style.radius * 0.6f))
                )
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.75f * flash)))
            }
        }
        ChargeCapsule(
            charge = capsuleCharge, flare = capsuleFlare, phase = phase, color = style.glow,
            embers = !waiting && style.animatedBackdrop,
            modifier = Modifier.width(size * 0.92f).height(size * 0.62f)
        )
        FitText(
            b.title, color = if (waiting) ShelfTheme.textMuted else style.text, maxSize = 10.sp, minSize = 7.sp,
            maxLines = 3, textAlign = TextAlign.Center, style = uiFontStyle().merge(androidx.compose.ui.text.TextStyle(fontFamily = style.font))
        )
    }
}

/**
 * Voltcap's charging capsule (smooth, not pixel art): an outlined pill that lights up with embers
 * rising ([charge] 0..0.4), a bolt charging in its middle (0.4..0.75), then full — filled with light,
 * the bolt bright ([charge] 1). [flare] adds the burst (outer glow, a lightning stroke from its top).
 * A faded reflection sits under it, as on a glossy floor.
 */
@Composable
internal fun ChargeCapsule(charge: Float, flare: Float, phase: Float, color: Color, embers: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val capH = size.height * 0.55f
        val capW = w * 0.96f
        val left = (w - capW) / 2f
        val top = size.height * 0.08f
        val r = capH / 2f
        val stroke = (capH * 0.09f).coerceAtLeast(2f)
        val lit = (0.25f + 0.75f * (charge / 0.4f).coerceIn(0f, 1f))
        val boltIn = ((charge - 0.4f) / 0.35f).coerceIn(0f, 1f)
        val full = ((charge - 0.75f) / 0.25f).coerceIn(0f, 1f)
        fun capsule(dx: Float = 0f, dy: Float = 0f, grow: Float = 0f, a: Float, st: Float, fill: Boolean = false, c: Color = color) =
            drawRoundRect(
                c.copy(alpha = a.coerceIn(0f, 1f)),
                topLeft = Offset(left - grow + dx, top - grow + dy),
                size = androidx.compose.ui.geometry.Size(capW + grow * 2, capH + grow * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r + grow),
                style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(st)
            )
        // Outer glow (soft, several wide faint strokes), stronger when full and on a flare.
        val glowA = 0.10f * lit + 0.18f * full + 0.30f * flare
        for (i in 1..4) capsule(grow = stroke * i * 0.9f, a = glowA / i, st = stroke * 1.6f)
        // Inner light when full.
        if (full > 0f || flare > 0f) {
            capsule(a = 0.10f * full + 0.22f * flare, st = 0f, fill = true)
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f * full), Color.Transparent), startY = top, endY = top + capH),
                topLeft = Offset(left + stroke, top + stroke), size = androidx.compose.ui.geometry.Size(capW - stroke * 2, capH * 0.45f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
            )
        }
        // The outline itself: a hot core over a warmer edge.
        capsule(a = 0.55f * lit + 0.45f * full, st = stroke * 1.4f)
        capsule(a = 0.35f * lit + 0.6f * full, st = stroke * 0.55f, c = Color.White.copy(alpha = 1f))
        // The bolt in the middle.
        if (boltIn > 0f) {
            val cx = w / 2f; val cy = top + capH / 2f; val bh = capH * 0.62f * (0.6f + 0.4f * boltIn); val bw = bh * 0.52f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx + bw * 0.18f, cy - bh / 2)
                lineTo(cx - bw * 0.42f, cy + bh * 0.06f)
                lineTo(cx - bw * 0.02f, cy + bh * 0.06f)
                lineTo(cx - bw * 0.20f, cy + bh / 2)
                lineTo(cx + bw * 0.44f, cy - bh * 0.08f)
                lineTo(cx + bw * 0.04f, cy - bh * 0.08f)
                close()
            }
            drawPath(path, color.copy(alpha = 0.35f * boltIn), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 2.2f))
            drawPath(path, color.copy(alpha = boltIn))
            drawPath(path, Color.White.copy(alpha = 0.55f * full + 0.3f * flare))
        }
        // A lightning stroke rising from the capsule's top on a flare / when full.
        val zap = maxOf(flare, full * (if (sin(phase * 2 * PI.toFloat() * 3) > 0.92f) 1f else 0f))
        if (zap > 0.05f) {
            val path = androidx.compose.ui.graphics.Path()
            var x = w / 2f; var y = top - stroke
            path.moveTo(x, y)
            val seg = 6
            for (i in 1..seg) {
                y -= capH * 0.16f
                x += ((i * 37 + (phase * 10).toInt()) % 7 - 3) * capH * 0.05f
                path.lineTo(x, y)
            }
            drawPath(path, color.copy(alpha = 0.5f * zap), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 1.8f))
            drawPath(path, Color.White.copy(alpha = 0.85f * zap), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 0.6f))
        }
        // Embers drifting up.
        if (embers) for (i in 0 until 9) {
            val t = (phase + i / 9f) % 1f
            val ex = left + capW * (((i * 53) % 100) / 100f)
            val ey = top + capH * 0.2f - t * size.height * 0.5f
            val a = (1f - t) * (0.25f + 0.6f * lit) * (0.4f + 0.6f * charge)
            drawRect(color.copy(alpha = a), topLeft = Offset(ex, ey), size = androidx.compose.ui.geometry.Size(stroke * 0.8f, stroke * 0.8f))
        }
        // Reflection on the floor: the capsule again, flipped and fading out.
        val floor = top + capH + stroke * 2.2f
        drawRoundRect(
            Brush.verticalGradient(
                listOf(color.copy(alpha = (0.16f * lit + 0.22f * full + 0.2f * flare).coerceIn(0f, 1f)), Color.Transparent),
                startY = floor, endY = floor + capH * 0.7f
            ),
            topLeft = Offset(left + capW * 0.06f, floor), size = androidx.compose.ui.geometry.Size(capW * 0.88f, capH * 0.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.5f)
        )
    }
}

/** Crackling lightning between the capsule and the badge above it. */
@Composable
private fun ChargeArc(lift: Float, phase: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val bottom = size.height
        val topY = bottom - lift.dp.toPx() - 4.dp.toPx()
        if (topY >= bottom - 2f) return@Canvas
        for (k in 0..1) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(cx + (k * 2 - 1) * 6f, bottom)
            val steps = 5
            for (i in 1..steps) {
                val y = bottom - (bottom - topY) * i / steps
                val jitter = (((i * 31 + k * 17 + (phase * 24).toInt()) % 9) - 4) * 2.2f
                path.lineTo(cx + jitter + (k * 2 - 1) * 4f, y)
            }
            drawPath(path, color.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f))
            drawPath(path, Color.White.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
        }
    }
}

/** Manual pop: a badge picked by hand rises out of its slot, hovers, and locks back in. Independent of NEW. */
internal fun Modifier.badgePop(trigger: Int, amount: Float = 1f): Modifier = composed {
    val anim = remember { Animatable(1f) }
    val sounds = LocalUiSounds.current
    val context = LocalContext.current
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        anim.snapTo(0f)
        launch {
            delay(560)
            sounds?.buzz(UiSound.SELECT)
            if (com.shelf.launcher.data.AchievementLook.sounds.value) RetroFx.play(context, sounds, RetroSound.LOCK, pitch = 1.15f)
        }
        anim.animateTo(1f, tween(760, easing = LinearEasing))
    }
    graphicsLayer {
        val p = anim.value
        if (p < 1f) {
            val rise = FastOutSlowInEasing.transform((p / 0.3f).coerceIn(0f, 1f))
            val settle = FastOutSlowInEasing.transform(((p - 0.7f) / 0.3f).coerceIn(0f, 1f))
            val hover = ((p - 0.3f) / 0.4f).coerceIn(0f, 1f)
            val hop = abs(sin(hover * 2 * PI.toFloat())) * 4f * amount
            translationY = -((rise * 14f * amount + hop) * (1f - settle)).dp.toPx()
            val s = 1f + 0.12f * amount * rise * (1f - settle)
            scaleX = s; scaleY = s
        }
    }
}

// ---------------------------------------------------------------------------------------------------
// 100 %: the badges fly together into a wall, the bar fills to 100 % and the game is stamped MASTERED.
// ---------------------------------------------------------------------------------------------------

@Composable
internal fun CompletionCelebration(images: List<String?>, title: String, total: Int, onDone: () -> Unit) {
    val style = achievementStyle()
    val context = LocalContext.current
    val sounds = LocalUiSounds.current
    val assemble = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    val stamp = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        RetroFx.warm(context)
        assemble.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        if (com.shelf.launcher.data.AchievementLook.sounds.value) RetroFx.play(context, sounds, RetroSound.LAND)
        sounds?.buzz(UiSound.LAUNCH)
        bar.animateTo(1f, tween(700))
        stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
        if (com.shelf.launcher.data.AchievementLook.sounds.value) RetroFx.play(context, sounds, RetroSound.LOCK, pitch = 0.8f)
        delay(2600)
        done()
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { done() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ImmersiveDialogWindow()
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f)).achievementBackdrop(style, assemble.value * 6f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.86f)) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp)) {
                    val shown = images.take(48)
                    val cols = kotlin.math.ceil(kotlin.math.sqrt(shown.size * 2.4)).toInt().coerceIn(4, 12)
                    val cell = (maxWidth / cols).coerceAtMost(44.dp)
                    val rows = (shown.size + cols - 1) / cols
                    val gridW = cell * cols
                    val gridH = cell * rows
                    shown.forEachIndexed { i, img ->
                        val col = i % cols; val row = i / cols
                        // Start scattered round the screen edge, end in the wall.
                        val ang = i * 2.39996f
                        val sx = kotlin.math.cos(ang) * 420f
                        val sy = kotlin.math.sin(ang) * 300f
                        val p = ((assemble.value * 1.3f) - (i % 12) * 0.025f).coerceIn(0f, 1f)
                        val e = FastOutSlowInEasing.transform(p)
                        Box(
                            Modifier
                                .offset(x = (maxWidth - gridW) / 2 + cell * col, y = (220.dp - gridH) / 2 + cell * row)
                                .size(cell - 4.dp)
                                .graphicsLayer {
                                    translationX = sx * (1f - e); translationY = sy * (1f - e)
                                    rotationZ = (1f - e) * (if (i % 2 == 0) 40f else -40f)
                                    alpha = e
                                }
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, style.frameOn.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        ) {
                            if (img != null) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                FitText(title, color = Color.White, maxSize = 20.sp, minSize = 13.sp, maxLines = 2, fontWeight = FontWeight.Bold, style = uiFontStyle().merge(androidx.compose.ui.text.TextStyle(fontFamily = style.font)))
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(bar.value).height(6.dp).background(Brush.horizontalGradient(listOf(style.glow, GOLD))))
                }
                Spacer(Modifier.height(6.dp))
                Text("$total/$total · 100%", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = style.font)
                Spacer(Modifier.height(14.dp))
                Text(
                    "MASTERED", color = GOLD, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                    modifier = Modifier.graphicsLayer {
                        val s = 2.4f - 1.4f * stamp.value
                        scaleX = s; scaleY = s; alpha = stamp.value
                        rotationZ = -6f * stamp.value
                    }
                )
            }
        }
    }
}
