@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.shelf.launcher.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import com.shelf.launcher.data.IntroStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Voltcap's start-up animation.
 *
 * On a deep, warm-black stage two sparks race around the outline of the
 * Voltcap capsule and meet; at that moment (the beat in the intro sound) the
 * capsule lights up, the bolt snaps into it with a soft shock wave, embers
 * drift up, the VOLTCAP letters rise in one by one, a light sweeps across
 * them, and everything fades into the launcher. All drawn in one Canvas with
 * additive light (no bitmaps), so it's crisp at any size and costs one layer.
 */

private val Orange = Color(0xFFF59A2C)
private val Yellow = Color(0xFFFFD84A)
private val Cream = Color(0xFFFFF6D6)
private val Ember = Color(0xFFFF6E28)
private val Stage = Color(0xFF08070B)

/**
 * Lets heavy start-up work (network refreshes, big list updates) wait until the intro hands over, so the
 * animation gets the CPU on weak handhelds — right after a reboot everything else is starting too.
 * Never waits longer than [MAX_WAIT_MS], and not at all when no intro plays.
 */
object StartupGate {
    private const val MAX_WAIT_MS = 4_000L
    private val done = kotlinx.coroutines.flow.MutableStateFlow(false)
    fun release() { done.value = true }
    suspend fun awaitIntro() {
        kotlinx.coroutines.withTimeoutOrNull(MAX_WAIT_MS) { done.first { it } }
    }
}

/** Times in ms. The sound's hit lands at [hit]. */
private class IntroTiming(
    val total: Float,
    val bgIn: Float,
    /** When the sparks start tracing the outline; < 0: the outline is simply there. */
    val traceFrom: Float,
    val hit: Float,
    val boltDraw: Float,
    val fillEnd: Float,
    val lettersFrom: Float,
    val letterStep: Float,
    val letterDur: Float,
    val tagFrom: Float,
    val tagTo: Float,
    val shineFrom: Float,
    val shineTo: Float,
    val reveal: Float
)

private val FULL_TIMING = IntroTiming(
    total = 2700f, bgIn = 350f, traceFrom = 180f, hit = 1050f, boltDraw = 190f, fillEnd = 1650f,
    lettersFrom = 1280f, letterStep = 55f, letterDur = 420f, tagFrom = 1750f, tagTo = 2150f,
    shineFrom = 1900f, shineTo = 2400f, reveal = 2300f
)
private val SHORT_TIMING = IntroTiming(
    total = 950f, bgIn = 60f, traceFrom = -1f, hit = 90f, boltDraw = 120f, fillEnd = 450f,
    lettersFrom = 160f, letterStep = 28f, letterDur = 260f, tagFrom = 330f, tagTo = 560f,
    shineFrom = 420f, shineTo = 720f, reveal = 650f
)

/** Capsule in unit space: half width 1, half height [R]. */
private const val R = 0.44f
private const val LINE = 2f * (1f - R)
private const val ARC = (PI / 2).toFloat() * R
private const val HALF = ARC * 2f + LINE
private const val WORD = "VOLTCAP"
private const val TAGLINE = "PLUG IN  ·  PLAY ON"

/** The bolt glyph, in capsule units. */
private val BOLT = floatArrayOf(
    0.13f, -0.33f, -0.17f, 0.045f, -0.015f, 0.045f, -0.11f, 0.33f, 0.19f, -0.055f, 0.035f, -0.055f
)

private class Spark(val s: Float, val side: Float, val delay: Float, val life: Float, val drift: Float,
                    val rise: Float, val size: Float, val tw: Float)
private class Trail(val k: Float, val off: Float, val life: Float, val size: Float)

/** Shapes and random bits, made once (seeded, so it looks the same every time). */
private class IntroGeo {
    private var seed = 20260930L
    private fun rnd(): Float {
        seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return seed.toFloat() / 4294967296f
    }
    val pill = Path().apply { addRoundRect(RoundRect(-1f, -R, 1f, R, CornerRadius(R, R))) }
    val bolt = Path().apply {
        moveTo(BOLT[0], BOLT[1])
        for (i in 1 until BOLT.size / 2) lineTo(BOLT[i * 2], BOLT[i * 2 + 1])
        close()
    }
    val boltLength: Float = run {
        var l = 0f
        val n = BOLT.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            l += kotlin.math.hypot(BOLT[j * 2] - BOLT[i * 2], BOLT[j * 2 + 1] - BOLT[i * 2 + 1])
        }
        l
    }
    val embers = List(26) {
        Spark(rnd(), if (rnd() < 0.5f) -1f else 1f, rnd() * 700f, 1100f + rnd() * 900f, (rnd() - 0.5f) * 0.5f,
            0.35f + rnd() * 0.6f, 0.010f + rnd() * 0.018f, rnd() * 6.28f)
    }
    val trail = List(28) { Trail(rnd(), (rnd() - 0.5f) * 0.08f, 260f + rnd() * 300f, 0.008f + rnd() * 0.012f) }
    // Reused every frame.
    val scratch = Path()
    val scratch2 = Path()
}

/** The measured letters of the wordmark at one size. */
private class Wordmark(val letters: List<TextLayoutResult>, val tracking: Float, val tagline: TextLayoutResult) {
    val width: Float = letters.sumOf { it.size.width.toDouble() }.toFloat() + tracking * (letters.size - 1)
}

/**
 * The start-up animation. [style] picks Full (about 2.7 s) or Short (under a
 * second; also used when system animations are off). [onBolt] fires at the
 * hit (blinks the controller lights), [onReveal] as it starts to fade (the
 * launcher is built then), [onDone] at the end; each at most once. A tap skips it.
 */
@Composable
fun VoltcapIntro(
    onBolt: () -> Unit = {},
    onReveal: () -> Unit = {},
    onDone: () -> Unit,
    style: IntroStyle = IntroStyle.FULL
) {
    com.shelf.launcher.Breadcrumbs.mark("VoltcapIntro")
    val context = LocalContext.current
    // "Remove animations" in the system settings: play the short version.
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    val timing = if ((style == IntroStyle.FULL || style == IntroStyle.PIXEL) && !reduceMotion) FULL_TIMING else SHORT_TIMING
    // The timeline in ms, driven frame by frame (see below), and whether to draw the lighter version.
    var clock by remember { mutableFloatStateOf(0f) }
    var lite by remember { mutableStateOf(false) }
    val geo = remember { IntroGeo() }
    val done by rememberUpdatedState(onDone)
    val bolt by rememberUpdatedState(onBolt)
    val reveal by rememberUpdatedState(onReveal)
    val sounds = LocalUiSounds.current
    // Reveal and done each run at most once, whether the animation ends or a tap skips it.
    val once = remember { booleanArrayOf(false, false) }
    val doReveal = { if (!once[0]) { once[0] = true; StartupGate.release(); reveal() } }
    val doDone = { doReveal(); if (!once[1]) { once[1] = true; done() } }
    LaunchedEffect(Unit) {
        if (style == IntroStyle.OFF) { doDone(); return@LaunchedEffect }
        // Weak devices, especially right after a reboot: the first frames come in bursts while the
        // system (and Voltcap) start up. Hold the dark stage until frames arrive steadily (at most
        // 0.7 s) so the animation doesn't start already behind and stutter through its opening.
        var last = withFrameNanos { it }
        val warmFrom = last
        var steady = 0
        while (steady < 3 && last - warmFrom < 700_000_000L) {
            val now = withFrameNanos { it }
            steady = if (now - last < 40_000_000L) steady + 1 else 0
            last = now
        }
        // The full intro has its own music cue, timed to the hit at 1.05 s; the
        // short one uses the brief launch sound so audio never outlasts it.
        sounds?.playWhenReady(if (timing.total >= 1500f) UiSound.INTRO else UiSound.LAUNCH)
        var boltFired = false
        var t = 0f
        var avg = 16f
        var frames = 0
        while (t < timing.total) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000f
            last = now
            // A single long stall (a few hundred ms of start-up work) is not jumped over in one go,
            // which is what looked like 1 FPS; normal frames keep real time, so the sound stays in step.
            t += dt.coerceAtMost(100f)
            frames++
            avg = avg * 0.85f + dt * 0.15f
            // Consistently slow frames (under ~30 FPS): drop the extra glow passes, keep the look.
            if (!lite && frames > 8 && avg > 33f) lite = true
            clock = t.coerceAtMost(timing.total)
            if (!boltFired && t >= timing.hit) { boltFired = true; bolt() }
            // The launcher isn't built at all until the intro starts to fade,
            // so nothing of it (or its first-run pop-ups) shows behind the logo.
            if (t >= timing.reveal) doReveal()
        }
        doDone()
    }

    val measurer = rememberTextMeasurer(cacheSize = 16)
    val density = LocalDensity.current
    // The background is a plain box, so the whole screen is covered from the very first frame.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - phase(clock, timing.reveal, timing.total) }
            .background(Stage)
            .pointerInput(Unit) { detectTapGestures { doDone() } }
    ) {
        val cache = remember { arrayOfNulls<Wordmark>(1) }
        val cacheKey = remember { floatArrayOf(-1f) }
        Canvas(Modifier.fillMaxSize()) {
            if (size.width < 1f || size.height < 1f) return@Canvas
            val r = min(0.2f * size.width, 0.26f * size.height)
            val fs = 0.40f * r
            if (cacheKey[0] != fs || cache[0] == null) {
                cache[0] = measureWordmark(measurer, density, fs, style == IntroStyle.PIXEL)
                cacheKey[0] = fs
            }
            if (style == IntroStyle.PIXEL) drawPixelIntro(timing, clock, r, cache[0]!!)
            else drawIntro(geo, timing, clock, r, cache[0]!!, lite)
        }
    }
}

/** A low-cost grid-drawn capsule: stepped silhouette, five colours and rising square sparks. */
private fun DrawScope.drawPixelIntro(tm: IntroTiming, t: Float, r: Float, word: Wordmark) {
    val cell = (r / 25f).coerceAtLeast(2f)
    val origin = Offset(size.width / 2f, size.height * 0.40f)
    val charged = phase(t, tm.hit, tm.fillEnd)
    val visible = phase(t, tm.bgIn, tm.hit).coerceAtLeast(0.1f)
    for (y in -11..11) for (x in -25..25) {
        val end = (kotlin.math.abs(x) - 14).coerceAtLeast(0)
        if (end * end + y * y > 121) continue
        val border = kotlin.math.abs(y) >= 9 || end * end + y * y >= 84
        val bolt = (y < 1 && x in (-y / 2 - 3)..(-y / 2 + 2)) || (y >= 1 && x in (-y / 2 - 1)..(-y / 2 + 4))
        val c = when {
            bolt && charged > 0f -> Cream
            border -> if (y < -3) Yellow else Orange
            else -> if (x / 25f < charged * 2f - 1f) Ember else Color(0xFF251B24)
        }
        drawRect(c.copy(alpha = visible), origin + Offset(x * cell, y * cell), Size(cell, cell))
    }
    if (t > tm.hit) for (i in 0..11) {
        val age = phase(t, tm.hit, tm.total)
        val x = origin.x + (i - 5.5f) * cell * 4f
        val y = origin.y - 12 * cell - ((age * (12 + i % 4) * cell).toInt() / cell.toInt().coerceAtLeast(1)) * cell
        drawRect(if (i % 2 == 0) Yellow else Orange, Offset(x, y), Size(cell, cell), alpha = (1 - age) * charged)
    }
    val width = word.width
    var x = (size.width - width) / 2f
    word.letters.forEachIndexed { i, letter ->
        val a = phase(t, tm.lettersFrom + i * tm.letterStep, tm.lettersFrom + i * tm.letterStep + tm.letterDur)
        drawText(letter, Cream.copy(alpha = a), Offset(x, origin.y + 15f * cell + (1 - a) * 3f * cell))
        x += letter.size.width + word.tracking
    }
}

private fun measureWordmark(measurer: TextMeasurer, density: Density, fsPx: Float, pixel: Boolean = false): Wordmark {
    val size = with(density) { fsPx.toSp() }
    val style = TextStyle(fontSize = size, fontWeight = FontWeight.Black, fontFamily = if (pixel) androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.shelf.launcher.R.font.volt_pixel)) else null)
    val letters = WORD.map { measurer.measure(AnnotatedString(it.toString()), style) }
    val tagStyle = TextStyle(
        fontSize = with(density) { (fsPx * 0.25f).toSp() },
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.34.em
    )
    return Wordmark(letters, 0.14f * fsPx, measurer.measure(AnnotatedString(TAGLINE), tagStyle))
}

private fun phase(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)
private fun easeOut3(x: Float): Float = 1f - (1f - x).pow(3)
private fun easeInOut3(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeBack(x: Float): Float {
    val c1 = 1.9f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

/** A point on the top (side = -1) or bottom (side = 1) half of the outline, [s] 0..1 from the left end. */
private fun halfPoint(side: Float, s: Float): Offset {
    var d = s * HALF
    if (d < ARC) {
        val a = PI.toFloat() - d / R
        return Offset(-(1f - R) + R * cos(a), side * R * sin(a))
    }
    d -= ARC
    if (d < LINE) return Offset(-(1f - R) + d, side * R)
    d -= LINE
    val a = (PI / 2).toFloat() - d / R
    return Offset((1f - R) + R * cos(a), side * R * sin(a))
}

private fun DrawScope.drawIntro(g: IntroGeo, tm: IntroTiming, t: Float, r: Float, word: Wordmark, lite: Boolean) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.43f
    val out = phase(t, tm.reveal, tm.total)
    val zoom = 1f + 0.05f * easeOut3(out)
    val bgIn = phase(t, 0f, tm.bgIn)
    val struck = t >= tm.hit
    val hitK = if (struck) (1f - phase(t, tm.hit, tm.hit + 900f)).let { it * it } else 0f
    val charge = easeOut3(phase(t, tm.hit, tm.fillEnd))

    // ---- stage: warm glow behind the logo, two slow light drifts, vignette ----
    val ga = 0.10f * bgIn + 0.10f * charge + 0.12f * hitK
    drawRect(
        Brush.radialGradient(
            0f to Orange.copy(alpha = ga), 0.45f to Color(0xFF964614).copy(alpha = ga * 0.35f), 1f to Color.Transparent,
            center = Offset(cx, cy), radius = 3.4f * r
        )
    )
    if (!lite) for (i in 0..1) {
        val ang = t / 1000f * 0.35f + i * 3.1f
        val bx = cx + cos(ang) * r * 1.6f * (if (i == 1) 1f else -1f)
        val by = cy + sin(ang * 0.8f) * r * 0.7f
        val c = if (i == 1) Yellow else Color(0xFFFF6E28)
        drawRect(Brush.radialGradient(listOf(c.copy(alpha = 0.045f * bgIn), Color.Transparent), center = Offset(bx, by), radius = 2.6f * r))
    }
    drawRect(
        Brush.radialGradient(
            0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f),
            center = Offset(cx, h / 2f), radius = max(w, h) * 0.75f
        )
    )

    withTransform({
        translate(cx, cy)
        scale(r * zoom, r * zoom, Offset.Zero)
    }) {
        // ---- the capsule: dark glass ----
        val bodyA = if (tm.traceFrom < 0f) phase(t, 0f, 120f) else phase(t, tm.traceFrom, tm.traceFrom + 500f)
        drawPath(g.pill, Color(0xFF160F0A).copy(alpha = 0.92f * bodyA))
        if (struck) {
            clipPath(g.pill) {
                // Warm energy from the middle, then a soft gloss on the top half.
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFFFFBE5A).copy(alpha = (0.5f * charge + 0.35f * hitK).coerceAtMost(1f)),
                        0.45f to Color(0xFFE66E1E).copy(alpha = 0.3f * charge),
                        1f to Color(0xFF78280A).copy(alpha = 0f),
                        center = Offset.Zero, radius = 0.25f + 1.35f * charge
                    ),
                    topLeft = Offset(-1f, -R), size = Size(2f, 2f * R), blendMode = BlendMode.Plus
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f * charge), Color.Transparent), startY = -R, endY = 0.05f),
                    topLeft = Offset(-1f, -R), size = Size(2f, R)
                )
            }
            drawLine(Cream.copy(alpha = 0.35f * charge), Offset(0f, -R), Offset(0f, R), strokeWidth = 0.012f)
        }

        // ---- the outline, traced by two sparks ----
        val p = if (tm.traceFrom < 0f) 1f else easeInOut3(phase(t, tm.traceFrom, tm.hit))
        val appear = if (tm.traceFrom < 0f) phase(t, 0f, 140f) else 1f
        val rim = Brush.horizontalGradient(listOf(Orange, Yellow), startX = -1f, endX = 1f)
        val pulse = 1f + 0.6f * hitK
        for (side in floatArrayOf(-1f, 1f)) {
            if (p > 0f) {
                val path = g.scratch
                path.reset()
                val n = if (lite) 32 else 64
                for (i in 0..n) {
                    val q = halfPoint(side, p * i / n)
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                if (!lite) drawPath(path, rim, alpha = (0.06f * pulse * appear).coerceAtMost(1f), style = Stroke(0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, rim, alpha = (0.22f * pulse * appear).coerceAtMost(1f), style = Stroke(0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(path, rim, alpha = appear, style = Stroke(0.018f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            }
            if (p > 0f && p < 1f) {
                // The spark's head, and sparkles shed behind it.
                val head = halfPoint(side, p)
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.White, 0.25f to Yellow.copy(alpha = 0.8f), 1f to Color(0xFFFFA028).copy(alpha = 0f),
                        center = head, radius = 0.16f
                    ),
                    radius = 0.16f, center = head, blendMode = BlendMode.Plus
                )
                for ((ti, tr) in g.trail.withIndex()) {
                    if (lite && ti % 2 == 1) continue
                    val born = tm.traceFrom + tr.k * (tm.hit - tm.traceFrom)
                    val age = t - born
                    if (age < 0f || age > tr.life) continue
                    val q = halfPoint(side, easeInOut3(phase(born, tm.traceFrom, tm.hit)))
                    val f = age / tr.life
                    drawCircle(
                        Cream.copy(alpha = (1f - f) * 0.9f), radius = tr.size * (1f - f * 0.6f),
                        center = Offset(q.x + tr.off * (1f + f), q.y + side * tr.off * 0.5f + side * 0.06f * f),
                        blendMode = BlendMode.Plus
                    )
                }
            }
        }

        // ---- the bolt: drawn in light, then filled, with a small pop ----
        if (struck) {
            val drawn = easeOut3(phase(t, tm.hit, tm.hit + tm.boltDraw))
            val pop = easeBack(phase(t, tm.hit, tm.hit + 380f))
            val bs = 1.22f * (0.7f + 0.3f * pop)
            val fillA = phase(t, tm.hit + tm.boltDraw * 0.6f, tm.hit + tm.boltDraw + 120f)
            withTransform({ scale(bs, bs, Offset.Zero) }) {
                val glowK = fillA * (0.9f + 0.8f * hitK)
                if (!lite) drawPath(g.bolt, Color(0xFFFFAA3C).copy(alpha = (0.07f * glowK).coerceAtMost(1f)), style = Stroke(0.20f, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(g.bolt, Color(0xFFFFAA3C).copy(alpha = (0.14f * glowK).coerceAtMost(1f)), style = Stroke(0.11f, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(g.bolt, Color(0xFFFFAA3C).copy(alpha = (0.25f * glowK).coerceAtMost(1f)), style = Stroke(0.05f, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
                drawPath(
                    g.bolt,
                    Brush.verticalGradient(
                        0f to Color.White, 0.45f to Color(0xFFFFF0A8), 1f to Color(0xFFFFC23A),
                        startY = -0.33f, endY = 0.33f
                    ),
                    alpha = fillA
                )
                // The line that draws the bolt.
                val path = g.scratch2
                path.reset()
                var left = drawn * g.boltLength
                val n = BOLT.size / 2
                path.moveTo(BOLT[0], BOLT[1])
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val ax = BOLT[i * 2]; val ay = BOLT[i * 2 + 1]
                    val bx = BOLT[j * 2]; val by = BOLT[j * 2 + 1]
                    val l = kotlin.math.hypot(bx - ax, by - ay)
                    if (left >= l) { path.lineTo(bx, by); left -= l } else {
                        val f = left / l
                        path.lineTo(ax + (bx - ax) * f, ay + (by - ay) * f)
                        break
                    }
                }
                drawPath(path, Cream.copy(alpha = 1f - fillA * 0.7f), style = Stroke(0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
            }
        }

        // ---- the hit: a soft shock ring and a flash of light in the middle ----
        if (struck && t < tm.hit + 600f) {
            val u = phase(t, tm.hit, tm.hit + 600f)
            val rr = 0.5f + 1.5f * easeOut3(u)
            drawOval(
                Color(0xFFFFC86E).copy(alpha = 0.5f * (1f - u) * (1f - u)),
                topLeft = Offset(-rr, -rr * 0.62f), size = Size(2f * rr, 2f * rr * 0.62f),
                style = Stroke(0.05f * (1f - u) + 0.004f), blendMode = BlendMode.Plus
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFFAEB).copy(alpha = 0.8f * (1f - u) * (1f - u)), Color(0xFFFFC85A).copy(alpha = 0f)),
                    center = Offset.Zero, radius = 1.2f
                ),
                radius = 1.2f, center = Offset.Zero, blendMode = BlendMode.Plus
            )
        }

        // ---- embers drifting up off the capsule ----
        if (struck) {
            val delayScale = if (tm.total < 1500f) 0.3f else 1f
            for ((ei, e) in g.embers.withIndex()) {
                if (lite && ei % 2 == 1) continue
                val age = t - tm.hit - e.delay * delayScale
                if (age < 0f || age > e.life) continue
                val f = age / e.life
                val b = halfPoint(e.side, e.s)
                val x = b.x + e.drift * f + 0.03f * sin(age / 260f + e.tw)
                val y = b.y - e.rise * easeOut3(f)
                val twinkle = 0.6f + 0.4f * sin(age / 90f + e.tw)
                drawCircle(
                    (if (e.side < 0f) Yellow else Orange).copy(alpha = ((1f - f) * twinkle).coerceIn(0f, 1f)),
                    radius = e.size * (1f - 0.5f * f), center = Offset(x, y), blendMode = BlendMode.Plus
                )
            }
        }
    }

    // ---- the wordmark: letters rise in one by one, then a light sweeps across ----
    val fs = 0.40f * r * zoom
    val base = cy + (R + 0.62f) * r * zoom + fs * 0.35f
    withTransform({ scale(zoom, zoom, Offset(cx, base)) }) {
        // Laid out at the un-zoomed size; the scale above grows it with the logo.
        val lw = word.width
        var x = cx - lw / 2f
        for ((i, layout) in word.letters.withIndex()) {
            val a = easeOut3(phase(t, tm.lettersFrom + i * tm.letterStep, tm.lettersFrom + i * tm.letterStep + tm.letterDur))
            if (a > 0f) {
                val top = base - layout.firstBaseline + (1f - a) * 0.40f * r * 0.32f
                drawText(
                    layout,
                    brush = Brush.horizontalGradient(
                        0f to Orange, 0.5f to Yellow, 1f to Orange,
                        startX = (cx - lw / 2f) - x, endX = (cx + lw / 2f) - x
                    ),
                    topLeft = Offset(x, top),
                    alpha = a,
                    // The blurred glow is the costliest bit of text drawing; the lighter version keeps a tight one.
                    shadow = Shadow(Color(0xFFFF8C28).copy(alpha = 0.55f), Offset.Zero, 0.40f * r * (if (lite) 0.12f else 0.35f))
                )
            }
            x += layout.size.width + word.tracking
        }
        // The shine.
        val sh = phase(t, tm.shineFrom, tm.shineTo)
        if (sh > 0f && sh < 1f) {
            val fs0 = 0.40f * r
            val band = (cx - lw / 2f - fs0) + (lw + 2f * fs0) * easeInOut3(sh)
            var sx = cx - lw / 2f
            for (layout in word.letters) {
                drawText(
                    layout,
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.85f), 1f to Color.Transparent,
                        startX = band - fs0 * 0.8f - sx, endX = band + fs0 * 0.8f - sx
                    ),
                    topLeft = Offset(sx, base - layout.firstBaseline),
                    blendMode = BlendMode.Plus
                )
                sx += layout.size.width + word.tracking
            }
        }
        // The tagline.
        val ta = phase(t, tm.tagFrom, tm.tagTo)
        if (ta > 0f) {
            val tag = word.tagline
            val fs0 = 0.40f * r
            drawText(
                tag,
                color = Color(0xFFE9D9BF),
                topLeft = Offset(cx - tag.size.width / 2f, base + fs0 * 0.72f - tag.firstBaseline + (1f - easeOut3(ta)) * fs0 * 0.125f),
                alpha = 0.72f * ta
            )
        }
    }
    // ---- a warm flash over everything at the hit ----
    if (struck) {
        val f1 = (1f - phase(t, tm.hit, tm.hit + 260f)).let { it * it }
        if (f1 > 0f) drawRect(Color(0xFFFFC878).copy(alpha = 0.22f * f1), blendMode = BlendMode.Plus)
    }
}
