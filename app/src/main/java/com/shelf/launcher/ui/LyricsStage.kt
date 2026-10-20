package com.shelf.launcher.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.AudioReactive
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.LyricsOptIn
import com.shelf.launcher.data.LyricsRepository
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.data.ShelfNotificationListener
import com.shelf.launcher.data.perf.PerfReader
import com.shelf.launcher.ui.theme.ShelfColors
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Update 200: the "Lyrics stage" layout of the second screen's Music page — the user's own Wallpaper Engine lyrics
// dashboard (a web page with a canvas of effects, synced lyrics and PC stats) rebuilt natively in Compose, so it can run
// on the AYN Thor's lower screen (1240 × 1080, nearly square) next to a game, and still lay out well on wide screens.
//
// How it stays light: every effect is drawn in ONE Canvas, driven by one withFrameNanos loop that keeps to the FPS cap
// and stops when the stage is idle with effects off or Voltcap is not on screen. Moving values are read in the draw /
// layer phase (a frame counter state), so nothing recomposes per frame. Particles, stars, bokeh and rings live in
// arrays allocated once. The blurred background is baked once per cover (rememberBakedBlur) and only scaled per frame.

/** Update 200: the stage's switches as one value, handed to the frame loop so it never reads Compose state. */
private data class StageOpts(
    val particles: Boolean = true,
    val circle: Boolean = true,
    val spectrum: Boolean = false,
    val rings: Boolean = false,
    val aurora: Boolean = false,
    val stars: Boolean = false,
    val bokeh: Boolean = false,
    val lines: Boolean = false,
    val fxReact: Boolean = true,
    val bgReact: Boolean = true,
    val reaction: Float = 1.5f,
    val particleAmount: Int = 90,
    val lowPower: Boolean = false
)

/** Update 200: the dashboard's default colours (before a cover is known) and the "not from the cover" ones. */
private val STAGE_DEFAULT_COLORS = listOf(Color(143, 168, 216), Color(185, 163, 232), Color(143, 216, 201))
private val STAGE_NEUTRAL_COLORS = listOf(Color(207, 216, 255), Color(255, 255, 255), Color(190, 200, 235))

/**
 * Update 200: three glowing colours from the cover's palette (accent, second colour, and the accent turned a little),
 * pushed bright enough to glow on the dark stage — the dashboard's boostColor.
 */
private fun stageColorsOf(c: ShelfColors): List<Color> {
    fun boost(color: Color, hueBy: Float = 0f): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), hsv)
        hsv[0] = (hsv[0] + hueBy + 360f) % 360f
        if (hsv[1] > 0.2f) {
            hsv[1] = hsv[1].coerceAtLeast(0.55f)
            hsv[2] = hsv[2].coerceIn(0.82f, 1f)
        } else hsv[2] = hsv[2].coerceAtLeast(0.86f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    return listOf(boost(c.accent), boost(c.secondary), boost(c.accent, -25f))
}

/**
 * Update 200: everything that moves on the stage — the music analysis (ported from the dashboard's analyse(), with the
 * same auto-gain and beat rule), the colours fading between songs and every effect. [step] runs in the frame loop,
 * [render] in the Canvas; nothing here is Compose state except the cover's place (written after layout).
 */
private class StageFx {
    var opts = StageOpts()
    /** Music is playing and the stage isn't idle (the effects hear it or simulate it). */
    var playing = false
    /** False while idle with effects switched off: nothing is drawn but the vignette. */
    var drawFx = true
    /** The frame loop is running (a new cover fades in only then; otherwise it simply shows). */
    var running = false
    /** Milliseconds of stage time (advances with the frame loop only). */
    private var t = 0f
    private var frameN = 0L

    /** Where the cover's slot and the Canvas sit in the window: the cover is drawn into its slot. */
    val coverInRoot = mutableStateOf(Rect.Zero)
    val canvasInRoot = mutableStateOf(Offset.Zero)
    private var coverLocal = Rect.Zero

    private var cover: ImageBitmap? = null
    private var coverOld: ImageBitmap? = null
    private var coverFadeAt = -10_000f

    fun setCover(img: ImageBitmap?) {
        if (img === cover) return
        coverOld = if (running) cover else null
        cover = img
        coverFadeAt = t
    }

    // ---- music analysis ----
    private val raw = FloatArray(64)
    private val spec = FloatArray(64)
    private val smoothBars = FloatArray(64)
    private val smoothCircle = FloatArray(96)
    private val waveSm = FloatArray(64)
    private var peak = 0.2f
    private var bass = 0f
    private var level = 0f
    private var avgBass = 0f
    private var prevRaw = 0f
    private var lastBeat = -1000f
    private var beat = false
    /** Strength for the effects and for the background (0 when their "react" switch is off). */
    var k = 0f; private set
    var kb = 0f; private set
    /** Nothing audible for a while (the dashboard's m0): beat rings then pulse slowly by themselves. */
    private val silent get() = peak <= 0.121f

    // ---- colours ----
    private val palCur = arrayOf(floatArrayOf(143f, 168f, 216f), floatArrayOf(185f, 163f, 232f), floatArrayOf(143f, 216f, 201f))
    private val palTarget = Array(3) { palCur[it].copyOf() }
    private val pal = Array(3) { Color(palCur[it][0] / 255f, palCur[it][1] / 255f, palCur[it][2] / 255f) }
    private var palVersion = 0

    fun setPalette(cols: List<Color>) {
        for (i in 0..2) {
            val c = cols.getOrElse(i) { cols.first() }
            palTarget[i][0] = c.red * 255f; palTarget[i][1] = c.green * 255f; palTarget[i][2] = c.blue * 255f
        }
    }

    // ---- size ----
    private var w = 0f
    private var h = 0f
    /** One dashboard pixel (it was made for a 1080 px tall screen), never thinner than ~0.6 dp. */
    private var u = 1f

    // ---- particles, stars, bokeh, rings: allocated once ----
    private val pMax = 420
    private val px = FloatArray(pMax); private val py = FloatArray(pMax); private val pr = FloatArray(pMax)
    private val pvx = FloatArray(pMax); private val pvy = FloatArray(pMax); private val pa = FloatArray(pMax)
    private val pph = FloatArray(pMax); private val pc = IntArray(pMax)
    private var pn = 0

    private val sN = 150
    private val sx = FloatArray(sN); private val sy = FloatArray(sN); private val sz = FloatArray(sN); private val sz0 = FloatArray(sN)
    private val sc = IntArray(sN)
    private var starsReady = false

    private val bN = 12
    private val bx = FloatArray(bN); private val by = FloatArray(bN); private val br = FloatArray(bN)
    private val bvx = FloatArray(bN); private val bvy = FloatArray(bN); private val ba = FloatArray(bN)
    private val bc = IntArray(bN)
    private var bokehReady = false

    private val rMax = 24
    private val rr = FloatArray(rMax); private val ra = FloatArray(rMax); private val rc = IntArray(rMax)
    private var rn = 0
    private var ringN = 0

    // ---- drawing caches ----
    private val circleCos = FloatArray(96) { cos(it / 96f * 2f * PI.toFloat() + PI.toFloat() / 2f) }
    private val circleSin = FloatArray(96) { sin(it / 96f * 2f * PI.toFloat() + PI.toFloat() / 2f) }
    private val linePaths = Array(3) { Path() }
    private val wavePath = Path()
    private val coverPath = Path()
    private var coverPathRect = Rect.Zero
    private var brushVersion = -1
    private val auroraBrush = arrayOfNulls<Brush>(3)
    private val bokehBrush = arrayOfNulls<Brush>(3)
    private var vignette: Brush? = null
    private var vignetteSize = Size.Zero
    private var waveBrush: Brush? = null
    private var waveBrushKey = ""
    private var strokeU = -1f
    private var lineStroke = Stroke(2f)
    private var ringStroke = Stroke(3f)

    private fun rnd() = Random.nextFloat()
    private fun on(flag: Boolean, heavy: Boolean = false) = flag && !(heavy && opts.lowPower)

    /** Idle with the effects switched off: forget the moving bits so they start fresh next time. */
    fun clearMoving() { pn = 0; rn = 0 }

    /** One frame of [dtMs] milliseconds. */
    fun step(dtMs: Float) {
        t += dtMs
        frameN++
        // The dashboard's numbers are "per frame at 30 fps": scale the motion so a lower or higher cap moves alike.
        val f = (dtMs / 33.333f).coerceIn(0.3f, 3f)
        analyse()
        stepPalette(f)
        for (i in 0 until 64) {
            val v = spec[i]
            smoothBars[i] += (v - smoothBars[i]) * (if (v > smoothBars[i]) 0.6f else 0.15f)
            waveSm[i] += (v - waveSm[i]) * (if (v > waveSm[i]) 0.6f else 0.2f)
        }
        for (i in 0 until 96) {
            val half = if (i < 48) i else 95 - i // mirrored left / right, bass at the bottom
            val v = spec[min(63, (half / 48f * 44f).toInt())]
            smoothCircle[i] += (v - smoothCircle[i]) * (if (v > smoothCircle[i]) 0.65f else 0.15f)
        }
        if (coverOld != null && t - coverFadeAt > 1000f) coverOld = null
        if (w <= 0f || !drawFx) { clearMoving(); return }
        val o = opts
        if (on(o.particles)) stepParticles(f) else pn = 0
        if (on(o.stars, heavy = true)) stepStars(f)
        if (on(o.bokeh, heavy = true)) stepBokeh(f)
        if (on(o.rings)) stepRings(f) else rn = 0
    }

    private fun analyse() {
        if (playing && AudioReactive.live) {
            // The real sound: the eight equalizer bands spread over 64 bars (squared back from AudioReactive's gentle
            // curve so the bass jumps like the dashboard's raw spectrum), with a small ripple so bars aren't in steps.
            val eq = AudioReactive.eq
            val last = eq.size - 1
            for (i in 0 until 64) {
                val p = i / 63f * last
                val a = p.toInt().coerceIn(0, last)
                val b = (a + 1).coerceAtMost(last)
                val v = eq[a] + (eq[b] - eq[a]) * (p - a)
                raw[i] = v * v * (0.82f + 0.18f * sin(i * 1.9f + t / 300f))
            }
        } else if (playing) {
            // No permission, or the player's sound can't be heard: the dashboard's own simulated beat.
            val beatOn = (t % 520f) < 120f
            for (i in 0 until 64) {
                val v = (sin(t / 380f + i / 5f) * 0.2f + 0.3f + rnd() * 0.2f).coerceAtLeast(0f)
                raw[i] = v * (if (i < 8) (if (beatOn) 1.6f else 0.56f) else 1f)
            }
        } else raw.fill(0f)
        var m = 0f
        for (v in raw) if (v > m) m = v
        peak = maxOf(m, peak * 0.9985f, 0.12f) // follows the loudness of the song
        val g = 1f / peak
        var b = 0f
        var l = 0f
        for (i in 0 until 64) {
            spec[i] = min(1f, raw[i] * g)
            l += spec[i]
            if (i < 6) b += spec[i]
        }
        b /= 6f; l /= 64f
        avgBass += (b - avgBass) * 0.03f
        beat = b > avgBass * 1.25f + 0.08f && b - prevRaw > 0.04f && t - lastBeat > 220f
        if (beat) lastBeat = t
        prevRaw = b
        bass += (b - bass) * (if (b > bass) 0.7f else 0.12f) // fast attack, slow release
        level += (l - level) * 0.25f
        val o = opts
        k = if (o.fxReact) min(1.6f, bass * o.reaction) else 0f
        kb = if (o.bgReact) min(1.6f, bass * o.reaction) else 0f
    }

    private fun stepPalette(f: Float) {
        var moved = false
        val a = (0.08f * f).coerceAtMost(1f)
        for (i in 0..2) {
            for (c in 0..2) {
                val d = palTarget[i][c] - palCur[i][c]
                if (abs(d) > 0.4f) { palCur[i][c] += d * a; moved = true } else palCur[i][c] = palTarget[i][c]
            }
            pal[i] = Color(palCur[i][0] / 255f, palCur[i][1] / 255f, palCur[i][2] / 255f)
        }
        if (moved) palVersion++
    }

    private fun newParticle(i: Int, fromBottom: Boolean) {
        px[i] = rnd() * w
        py[i] = if (fromBottom) h + 10f * u else rnd() * h
        pr[i] = rnd() * 2.4f + 0.8f
        pvy[i] = -(rnd() * 0.45f + 0.12f)
        pvx[i] = (rnd() - 0.5f) * 0.25f
        pa[i] = rnd() * 0.45f + 0.2f
        pph[i] = rnd() * 6.28f
        pc[i] = (rnd() * 3f).toInt().coerceAtMost(2)
    }

    private fun moveParticle(from: Int, to: Int) {
        px[to] = px[from]; py[to] = py[from]; pr[to] = pr[from]; pvx[to] = pvx[from]; pvy[to] = pvy[from]
        pa[to] = pa[from]; pph[to] = pph[from]; pc[to] = pc[from]
    }

    private fun stepParticles(f: Float) {
        val target = if (opts.lowPower) min(opts.particleAmount, 40) else opts.particleAmount.coerceAtMost(pMax - 20)
        while (pn < target) { newParticle(pn, false); pn++ }
        if (k > 0f && beat) repeat(8) { if (pn < pMax) { newParticle(pn, true); pn++ } }
        val boost = 1f + k * 6f + (if (opts.fxReact) level * 2f else 0f)
        var i = pn - 1
        while (i >= 0) {
            py[i] += pvy[i] * boost * u * f
            px[i] += (pvx[i] + sin(t / 1600f + pph[i]) * 0.25f) * u * f
            if (py[i] < -12f * u) {
                if (pn > target) { moveParticle(pn - 1, i); pn--; i--; continue } // the beat's extra ones leave at the top
                py[i] = h + 10f * u; px[i] = rnd() * w
            }
            if (px[i] < -12f * u) px[i] = w + 10f * u else if (px[i] > w + 12f * u) px[i] = -10f * u
            i--
        }
    }

    private fun resetStar(i: Int, fresh: Boolean) {
        sx[i] = (rnd() - 0.5f) * 2f; sy[i] = (rnd() - 0.5f) * 2f
        sz[i] = if (fresh) 1f else rnd() * 0.9f + 0.1f
        sz0[i] = sz[i]
        sc[i] = (rnd() * 3f).toInt().coerceAtMost(2)
    }

    private fun stepStars(f: Float) {
        if (!starsReady) { for (i in 0 until sN) resetStar(i, false); starsReady = true }
        val cx = w / 2f; val cy = h / 2f; val scl = max(w, h) * 0.17f
        val sp = (0.0035f + k * 0.024f) * f
        for (i in 0 until sN) {
            val z1 = sz[i] - sp
            if (z1 <= 0.03f) { resetStar(i, true); continue }
            sz0[i] = sz[i]; sz[i] = z1
            val x1 = cx + sx[i] / z1 * scl; val y1 = cy + sy[i] / z1 * scl
            if (x1 < -20f || x1 > w + 20f || y1 < -20f || y1 > h + 20f) resetStar(i, true)
        }
    }

    private fun stepBokeh(f: Float) {
        if (!bokehReady) {
            for (i in 0 until bN) {
                bx[i] = rnd() * w; by[i] = rnd() * h; br[i] = 50f + rnd() * 110f
                bvx[i] = (rnd() - 0.5f) * 0.28f; bvy[i] = (rnd() - 0.5f) * 0.2f
                ba[i] = 0.05f + rnd() * 0.08f; bc[i] = (rnd() * 3f).toInt().coerceAtMost(2)
            }
            bokehReady = true
        }
        for (i in 0 until bN) {
            val r = br[i] * u
            bx[i] += bvx[i] * u * f; by[i] += bvy[i] * u * f
            if (bx[i] < -r) bx[i] = w + r else if (bx[i] > w + r) bx[i] = -r
            if (by[i] < -r) by[i] = h + r else if (by[i] > h + r) by[i] = -r
        }
    }

    private fun coverRadius(): Float = if (coverLocal.width > 1f) coverLocal.width * 0.74f else 140f * u

    private fun stepRings(f: Float) {
        if ((beat || (silent && frameN % 90L == 0L)) && rn < rMax) {
            rr[rn] = coverRadius(); ra[rn] = 0.6f; rc[rn] = ringN++ % 3; rn++
        }
        var i = rn - 1
        while (i >= 0) {
            rr[i] += (2.2f + bass * 8f) * u * f
            ra[i] -= 0.009f * f
            if (ra[i] <= 0f) { rr[i] = rr[rn - 1]; ra[i] = ra[rn - 1]; rc[i] = rc[rn - 1]; rn-- }
            i--
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------------

    /** Everything on the Canvas: the effects (added light, like the dashboard's "lighter"), the vignette, then the cover. */
    fun DrawScope.render(coverAlpha: Float, waveOn: Boolean) {
        w = size.width; h = size.height
        if (w <= 0f || h <= 0f) return
        u = max(min(w, h) / 1080f, density * 0.6f)
        if (u != strokeU) {
            strokeU = u
            lineStroke = Stroke(2.2f * u, join = StrokeJoin.Round)
            ringStroke = Stroke(3f * u)
        }
        coverLocal = coverInRoot.value.translate(-canvasInRoot.value)
        if (brushVersion != palVersion) rebuildBrushes()
        val o = opts
        if (drawFx) {
            if (on(o.aurora, heavy = true)) drawAurora()
            if (on(o.bokeh, heavy = true) && bokehReady) drawBokeh()
            if (on(o.stars, heavy = true) && starsReady) drawStars()
            if (on(o.lines, heavy = true)) drawLines()
            if (on(o.spectrum)) drawSpectrum()
            if (on(o.circle) && coverLocal.width > 1f && coverAlpha > 0.01f) drawCircleBars(coverAlpha)
            if (on(o.rings)) drawRings()
            if (on(o.particles)) drawParticles()
        }
        if (size != vignetteSize || vignette == null) {
            vignetteSize = size
            vignette = Brush.radialGradient(
                0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f),
                center = Offset(w / 2f, h / 2f), radius = hypot(w, h) / 2f
            )
        }
        drawRect(vignette!!)
        drawCover(coverAlpha, waveOn)
    }

    private fun rebuildBrushes() {
        brushVersion = palVersion
        for (i in 0..2) {
            val c = pal[i]
            // Unit-sized (radius 100) at the origin; moved and scaled when drawn, so they're made once per colour.
            auroraBrush[i] = Brush.radialGradient(0f to c, 1f to c.copy(alpha = 0f), center = Offset.Zero, radius = 100f)
            bokehBrush[i] = Brush.radialGradient(0f to c, 0.7f to c, 1f to c.copy(alpha = 0f), center = Offset.Zero, radius = 100f)
        }
        waveBrushKey = ""
    }

    private fun DrawScope.softLight(brush: Brush?, x: Float, y: Float, r: Float, alpha: Float) {
        if (brush == null || r <= 0f) return
        withTransform({ translate(x, y); scale(r / 100f, r / 100f, Offset.Zero) }) {
            drawCircle(brush, 100f, Offset.Zero, alpha = alpha.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.drawAurora() {
        for (i in 0..2) {
            val x = w * (0.5f + 0.38f * sin(t / 5200f + i * 2.1f))
            val y = h * (0.5f + 0.3f * cos(t / 6100f + i * 1.7f))
            softLight(auroraBrush[i], x, y, max(w, h) * (0.26f + k * 0.14f), 0.13f + k * 0.26f)
        }
    }

    private fun DrawScope.drawBokeh() {
        for (i in 0 until bN) softLight(bokehBrush[bc[i]], bx[i], by[i], br[i] * u * (1f + k * 0.4f), min(0.5f, ba[i] * (1f + k * 1.8f)))
    }

    private fun DrawScope.drawStars() {
        val cx = w / 2f; val cy = h / 2f; val scl = max(w, h) * 0.17f
        for (i in 0 until sN) {
            val z0 = sz0[i]; val z1 = sz[i]
            if (z0 == z1) continue
            drawLine(
                pal[sc[i]], Offset(cx + sx[i] / z0 * scl, cy + sy[i] / z0 * scl), Offset(cx + sx[i] / z1 * scl, cy + sy[i] / z1 * scl),
                strokeWidth = (0.6f + (1f - z1) * 2.4f) * u, cap = StrokeCap.Round,
                alpha = (min(1f, (1f - z1) * 1.2f) * 0.9f).coerceIn(0f, 1f), blendMode = BlendMode.Plus
            )
        }
    }

    private fun DrawScope.drawLines() {
        val stepX = 8f * u
        for (l in 0..2) {
            val path = linePaths[l]
            path.reset()
            var x = 0f
            var first = true
            while (x <= w + stepX) {
                val uu = (x / w).coerceIn(0f, 1f)
                val bin = spec[min(63, (abs(uu - 0.5f) * 2f * 44f).toInt())]
                val y = h * 0.84f + l * h * 0.025f + sin(uu * 9f + t / 650f + l * 1.4f) * (6f * u + bin * h * 0.1f)
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += stepX
            }
            drawPath(path, pal[l], alpha = 0.5f - l * 0.1f, style = lineStroke, blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.drawSpectrum() {
        val n = 64
        val bw = w / (n * 2)
        val maxH = h * 0.34f
        for (i in 0 until n) {
            val v = smoothBars[i]
            val bh = 4f * u + v * maxH
            val c = pal[min(2, i * 3 / n)]
            val a = 0.2f + v * 0.4f
            drawRect(c, Offset(w / 2f - (i + 1) * bw + 1f, h - bh), Size((bw - 2f).coerceAtLeast(1f), bh), alpha = a, blendMode = BlendMode.Plus)
            drawRect(c, Offset(w / 2f + i * bw + 1f, h - bh), Size((bw - 2f).coerceAtLeast(1f), bh), alpha = a, blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.drawCircleBars(coverAlpha: Float) {
        val cx = coverLocal.center.x; val cy = coverLocal.center.y
        val r0 = coverRadius()
        val maxL = min(w, h) * 0.14f
        val sw = max(2.5f * u, w / 560f)
        for (i in 0 until 96) {
            val half = if (i < 48) i else 95 - i
            val v = smoothCircle[i]
            val len = 4f * u + v * maxL
            drawLine(
                pal[min(2, (half / 48f * 3f).toInt())],
                Offset(cx + circleCos[i] * r0, cy + circleSin[i] * r0), Offset(cx + circleCos[i] * (r0 + len), cy + circleSin[i] * (r0 + len)),
                strokeWidth = sw, cap = StrokeCap.Round, alpha = ((0.3f + v * 0.65f) * coverAlpha).coerceIn(0f, 1f), blendMode = BlendMode.Plus
            )
        }
    }

    private fun DrawScope.drawRings() {
        val c = if (coverLocal.width > 1f) coverLocal.center else Offset(w / 2f, h / 2f)
        for (i in 0 until rn) drawCircle(pal[rc[i]], rr[i], c, alpha = ra[i].coerceIn(0f, 1f), style = ringStroke, blendMode = BlendMode.Plus)
    }

    private fun DrawScope.drawParticles() {
        val grow = 1f + k * 1.8f
        for (i in 0 until pn) {
            val r = pr[i] * grow * u
            val c = pal[pc[i]]
            val at = Offset(px[i], py[i])
            drawCircle(c, r * 3f, at, alpha = (min(1f, pa[i] * (0.7f + k * 1.2f)) * 0.35f), blendMode = BlendMode.Plus)
            drawCircle(c, r, at, alpha = min(1f, pa[i] * (0.8f + k)), blendMode = BlendMode.Plus)
        }
    }

    /** The cover in its slot: a soft shadow, the picture (the new one fading in over the old), the wave along its foot. */
    private fun DrawScope.drawCover(alpha: Float, waveOn: Boolean) {
        val r = coverLocal
        if (r.width <= 1f || alpha <= 0.01f) return
        val s = 1f + kb * 0.03f
        val corner = r.width * 0.04f
        withTransform({ scale(s, s, r.center) }) {
            for (g in 1..3) {
                val grow = r.width * 0.022f * g
                drawRoundRect(
                    Color.Black, Offset(r.left - grow, r.top - grow + r.height * 0.05f), Size(r.width + grow * 2f, r.height + grow * 2f),
                    CornerRadius(corner + grow), alpha = 0.11f * alpha
                )
            }
            if (r != coverPathRect) {
                coverPathRect = r
                coverPath.reset()
                coverPath.addRoundRect(RoundRect(r, CornerRadius(corner)))
            }
            clipPath(coverPath) {
                val fade = ((t - coverFadeAt) / 1000f).coerceIn(0f, 1f)
                val old = coverOld
                if (old != null && fade < 1f) coverImage(old, r, alpha)
                val img = cover
                if (img != null) coverImage(img, r, alpha * (if (old != null) fade else 1f))
                else drawRect(Color(0xFFC9D3EA).copy(alpha = 0.25f * alpha), r.topLeft, r.size)
                if (waveOn) drawCoverWave(r, alpha)
            }
        }
    }

    private fun DrawScope.coverImage(img: ImageBitmap, r: Rect, alpha: Float) {
        val side = min(img.width, img.height).coerceAtLeast(1)
        drawImage(
            img,
            srcOffset = IntOffset((img.width - side) / 2, (img.height - side) / 2), srcSize = IntSize(side, side),
            dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
            alpha = alpha.coerceIn(0f, 1f), filterQuality = FilterQuality.Medium
        )
    }

    /** The dashboard's wave on the cover: the spectrum as a soft filled curve across its bottom quarter. */
    private fun DrawScope.drawCoverWave(r: Rect, alpha: Float) {
        val n = 64
        val hh = r.height * 0.24f
        val bottom = r.bottom
        fun xAt(i: Int) = r.left + i / (n - 1f) * r.width
        fun yAt(i: Int) = bottom - (0.08f + waveSm[i] * 0.88f) * hh
        val p = wavePath
        p.reset()
        p.moveTo(r.left, bottom)
        p.lineTo(xAt(0), yAt(0))
        for (i in 1 until n - 1) p.quadraticTo(xAt(i), yAt(i), (xAt(i) + xAt(i + 1)) / 2f, (yAt(i) + yAt(i + 1)) / 2f)
        p.lineTo(r.right, yAt(n - 1))
        p.lineTo(r.right, bottom)
        p.close()
        val key = "${r.left},${r.width},$palVersion"
        if (key != waveBrushKey || waveBrush == null) {
            waveBrushKey = key
            waveBrush = Brush.horizontalGradient(listOf(pal[0], pal[1], pal[2]), startX = r.left, endX = r.right)
        }
        drawPath(p, waveBrush!!, alpha = 0.85f * alpha)
    }
}

/** Update 200: sizes of the stage's text and controls, from the screen's short side and "Interface size". */
private class StageSizes(
    val clock: TextUnit, val date: TextUnit, val week: TextUnit,
    val title: TextUnit, val artist: TextUnit, val time: TextUnit, val line: TextUnit, val note: TextUnit,
    val stat: TextUnit, val statSub: TextUnit,
    val btn: Dp, val play: Dp, val bar: Dp, val gap: Dp, val unit: Dp
)

/**
 * Update 200: the Lyrics stage — the second screen's Music page laid out like the user's Wallpaper Engine lyrics
 * dashboard. [nowPlaying] null = nothing at all is playing (the stage rests: clock, stats and the idle picture). Shows
 * while music plays or is paused; after the "Idle after" time paused it rests the same way. Every switch lives in
 * Features.stage* (Settings › Appearance › Second screen art & music, with "Lyrics stage" picked).
 */
@Composable
internal fun LyricsStage(
    nowPlaying: NowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Update 201: the page's own settings (Settings › Screens › Second screen art & music › Music page layout). */
    onSettings: (() -> Unit)? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("LyricsStage")
    val context = LocalContext.current
    val F = Features
    val np = nowPlaying
    val playing = np?.isPlaying == true
    val active = rememberIsActive()

    // ---- settings (read here, so a change recomposes the stage once) ----
    val opts = StageOpts(
        particles = F.stageParticles.state.value, circle = F.stageCircle.state.value, spectrum = F.stageSpectrum.state.value,
        rings = F.stageRings.state.value, aurora = F.stageAurora.state.value, stars = F.stageStars.state.value,
        bokeh = F.stageBokeh.state.value, lines = F.stageLines.state.value,
        fxReact = F.stageFxReact.state.value, bgReact = F.stageBgReact.state.value,
        reaction = F.stageReaction.state.value / 100f, particleAmount = F.stageParticleAmount.state.value,
        lowPower = F.stageLowPower.state.value
    )
    val idleFx = F.stageIdleFx.state.value
    val idleAfter = F.stageIdleAfter.state.value
    val idlePicture = F.stageIdlePicture.state.value
    val showLyrics = F.stageLyrics.state.value
    val statusMode = F.stageStatus.state.value

    // ---- idle: nothing playing, or paused for longer than "Idle after" ----
    var idle by remember { mutableStateOf(np == null) }
    LaunchedEffect(np == null, playing, idleAfter) {
        if (playing) { idle = false; return@LaunchedEffect }
        if (!idle) {
            // Update 200: nothing at all playing rests after a moment (a player between two songs can drop out briefly).
            delay(if (np == null) 3_000L else idleAfter * 1000L)
            idle = true
        }
    }

    // ---- the cover: the last one stays behind the stage when the session ends ----
    var lastArt by remember { mutableStateOf<Bitmap?>(null) }
    val art = np?.art ?: lastArt
    SideEffect { if (np?.art != null && np.art !== lastArt) lastArt = np.art }
    val coverImage = remember(art) { art?.let { runCatching { it.asImageBitmap() }.getOrNull() } }
    val coverColors = F.stageCoverColors.state.value
    val artPalette = rememberArtPalette(art, coverColors && art != null)
    val colors = remember(artPalette, coverColors) {
        if (!coverColors) STAGE_NEUTRAL_COLORS else artPalette?.let { stageColorsOf(it) } ?: STAGE_DEFAULT_COLORS
    }
    val accent by animateColorAsState(colors[0], tween(900), label = "stageAccent")

    // ---- the frame loop ----
    val fx = remember { StageFx() }
    val tick = remember { mutableLongStateOf(0L) }
    val fps = F.stageFps.state.value
    val fpsNormal = if (opts.lowPower) min(fps, 20) else fps
    val cap = if (!idle) fpsNormal else when (idleFx) {
        Features.StageIdleFx.OFF -> 0
        Features.StageIdleFx.SLOW -> 12
        Features.StageIdleFx.KEEP -> fpsNormal
    }
    val running = active && cap > 0
    SideEffect {
        fx.opts = opts
        fx.playing = playing && !idle
        fx.drawFx = !(idle && idleFx == Features.StageIdleFx.OFF)
        fx.running = running
        fx.setPalette(colors)
        fx.setCover(coverImage)
        if (!fx.drawFx) fx.clearMoving()
    }
    LaunchedEffect(running, cap) {
        if (!running) { tick.longValue = tick.longValue + 1; return@LaunchedEffect } // one last redraw, then rest
        val frameNs = 1_000_000_000L / cap
        // Like the scenes' clock: rest most of the interval after a step instead of waking on every display frame.
        val nap = (1000L / cap - 10L).coerceAtLeast(0L)
        var last = withFrameNanos { it }
        while (true) {
            val stepped = withFrameNanos { now ->
                if (now - last >= frameNs - 2_000_000L) {
                    val dt = ((now - last) / 1_000_000f).coerceAtMost(100f)
                    last = now
                    fx.step(dt)
                    tick.longValue = now
                    true
                } else false
            }
            if (stepped && nap > 0) delay(nap)
        }
    }

    // ---- listening to the music (Microphone permission; otherwise the beat is simulated) ----
    val react = opts.fxReact || opts.bgReact
    var hasMic by remember { mutableStateOf(AudioReactive.hasPermission(context)) }
    val listen = active && playing && !idle && react && hasMic
    DisposableEffect(listen) {
        val held = listen && AudioReactive.acquire(context)
        onDispose { if (held) AudioReactive.release() }
    }
    // Checked once a second while on screen: the permission (it may be granted in Settings meanwhile), notification
    // access, and whether we're listening but hearing nothing (offloaded audio) — for the status line.
    var notifAccess by remember { mutableStateOf(true) }
    var deaf by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf(false) }
    var noShare by remember { mutableStateOf(AudioReactive.unsupported) }
    val listenNow by rememberUpdatedState(listen)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var silentSince = 0L
        while (true) {
            hasMic = AudioReactive.hasPermission(context)
            notifAccess = ShelfNotificationListener.hasAccess(context)
            heard = AudioReactive.live
            noShare = AudioReactive.unsupported
            val now = SystemClock.elapsedRealtime()
            if (listenNow && !AudioReactive.live) { if (silentSince == 0L) silentSince = now } else silentSince = 0L
            deaf = silentSince != 0L && now - silentSince > 20_000L
            delay(1000)
        }
    }

    // ---- the song position, ticking 10 times a second while it plays (read in draw / derived states only) ----
    val clock = produceState(SystemClock.elapsedRealtime(), active, playing, np?.positionAt, idle) {
        value = SystemClock.elapsedRealtime()
        if (active && playing && !idle) while (true) { delay(100); value = SystemClock.elapsedRealtime() }
    }

    // ---- lyrics (looked up only while they're shown) ----
    val lookup = if (np != null && showLyrics) rememberSongLyrics(np) else null

    val uiAlpha = animateFloatAsState(if (idle || np == null) 0f else 1f, tween(1000), label = "stageUi")

    Box(
        modifier.fillMaxSize().background(Color(0xFF0A0D16)).clipToBounds()
    ) {
        // Background: the cover blurred once, scaled a touch bigger than the screen; it breathes with the bass.
        val blur = F.stageBgBlur.state.value
        val baked = rememberBakedBlur(if (blur >= 1) art else null, blur.dp)
        val bgImage = if (blur < 1) coverImage else baked
        val saturate = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.3f) }) }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                tick.longValue
                val s = 1.12f * (1f + fx.kb * 0.06f)
                scaleX = s; scaleY = s
            }
        ) {
            Crossfade(bgImage, animationSpec = tween(1400), label = "stageBg") { img ->
                if (img != null) Image(img, null, contentScale = ContentScale.Crop, colorFilter = saturate, modifier = Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1A2236), Color(0xFF0A0D16)))))
            }
        }
        // Background brightness (the dashboard's brightness filter, as a dark veil).
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - F.stageBgBright.state.value / 100f).coerceIn(0f, 1f))))
        // A soft flash in the cover's colour on the bass.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                tick.longValue
                alpha = (fx.kb * 0.14f).coerceIn(0f, 1f)
            }.background(accent)
        )
        // The picture shown while idle (optional).
        if (idlePicture.isNotEmpty()) {
            val picAlpha = animateFloatAsState(if (idle) 1f else 0f, tween(1400), label = "stageIdlePic")
            if (idle || picAlpha.value > 0.01f) {
                AsyncImage(
                    model = idlePicture, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = picAlpha.value }
                )
            }
        }
        // Every effect, the vignette and the cover, in one Canvas.
        Canvas(
            Modifier.fillMaxSize().onGloballyPositioned { c ->
                val p = c.positionInRoot()
                if (p != fx.canvasInRoot.value) fx.canvasInRoot.value = p
            }
        ) {
            tick.longValue
            with(fx) { render(uiAlpha.value, waveOn = !idle) }
        }

        // ---- the page itself ----
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ui = F.stageUiScale.state.value / 100f
            val vUnit: Dp = minOf(maxHeight, maxWidth) / 100f
            val density = LocalDensity.current
            fun sp(vh: Float, minSp: Float): TextUnit {
                val v = with(density) { (vUnit * vh * ui).toSp() }
                return if (v.value < minSp) minSp.sp else v
            }
            fun dp(vh: Float, minDp: Dp): Dp = (vUnit * vh * ui).coerceAtLeast(minDp)
            val sz = StageSizes(
                clock = sp(7.2f, 22f), date = sp(3.4f, 12f), week = sp(1.9f, 10f),
                title = sp(2.6f, 14f), artist = sp(1.85f, 11f), time = sp(1.5f, 10f), line = sp(3f, 13f), note = sp(2.2f, 12f),
                stat = sp(1.85f, 10f), statSub = sp(1.45f, 9f),
                btn = dp(4.8f, 40.dp), play = dp(5.8f, 48.dp), bar = dp(0.8f, 3.dp), gap = dp(2.4f, 8.dp), unit = vUnit * ui
            )
            val wideStats = maxWidth >= maxHeight * 1.5f
            val narrow = maxWidth < maxHeight * 0.9f
            // (Update 201: the stats panel is gone from the music page — asked for; its reader stays off.)
            val statsOn = false
            val stats = rememberStageStats(statsOn && active)
            val sidePad = maxWidth * 0.03f

            Column(Modifier.fillMaxSize().padding(start = sidePad, end = sidePad, top = vUnit * 1.2f, bottom = vUnit * 4f)) {
                Box(Modifier.fillMaxWidth().height(dp(11f, 40.dp)), contentAlignment = Alignment.Center) {
                    StageClock(sz, F.stageClock24.state.value, F.stageSeconds.state.value)
                }
                if (statsOn && !wideStats && stats != null) StageStatsPanel(stats, compact = true, sz = sz, accent = accent, modifier = Modifier.fillMaxWidth().padding(bottom = sz.gap))
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val mainW = maxWidth
                    val mainH = maxHeight
                    // Room the title, timeline and buttons take under the cover.
                    val infoH = with(density) { (sz.title * 1.4f).toDp() + (sz.artist * 1.4f).toDp() + (sz.time * 1.4f).toDp() } +
                        dp(2.4f, 24.dp) + sz.play + sz.gap * 2f
                    val cardAlpha = Modifier.graphicsLayer { alpha = uiAlpha.value }
                    val card: @Composable (Dp) -> Unit = { coverSize ->
                        if (np != null) StageCard(
                            np, coverSize, sz, accent, clock, enabled = !idle,
                            onPrevious = onPrevious, onPlayPause = onPlayPause, onNext = onNext, onSeek = onSeek,
                            onCoverPlaced = { r -> if (r != fx.coverInRoot.value) fx.coverInRoot.value = r },
                            modifier = cardAlpha
                        )
                    }
                    val lyricsBox: @Composable (Modifier) -> Unit = { m ->
                        if (np != null && showLyrics) Box(m.then(cardAlpha)) {
                            StageLyrics(np, lookup, clock, sz, accent, active && !idle, context)
                        }
                    }
                    if (np == null && idlePicture.isEmpty()) {
                        Text(
                            "Nothing playing. Start music in any app and it shows up here.",
                            color = Color.White.copy(alpha = 0.5f), fontSize = sz.note, textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                        )
                    }
                    if (narrow) {
                        // Tall screens: the cover on top, the lyrics under it.
                        val cover = minOf(mainW * 0.62f, (if (showLyrics) mainH * 0.55f else mainH) - infoH).coerceAtLeast(72.dp)
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            card(cover)
                            if (showLyrics) {
                                Spacer(Modifier.height(sz.gap))
                                lyricsBox(Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                    } else {
                        val statsCol = statsOn && wideStats && stats != null
                        val coverByW = mainW * (if (showLyrics) (if (statsCol) 0.28f else 0.36f) else 0.5f)
                        val cover = minOf(mainH - infoH, coverByW).coerceAtLeast(72.dp)
                        Row(
                            Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(mainW * 0.04f, if (showLyrics || statsCol) Alignment.Start else Alignment.CenterHorizontally)
                        ) {
                            if (statsCol && stats != null) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                    StageStatsPanel(stats, compact = false, sz = sz, accent = accent, modifier = Modifier.widthIn(max = minOf(mainW * 0.26f, 340.dp)))
                                }
                            } else if (showLyrics) Spacer(Modifier.width(mainW * 0.02f))
                            if (np != null) card(cover) else Spacer(Modifier.width(cover))
                            if (showLyrics) lyricsBox(Modifier.weight(1f).fillMaxHeight(0.9f))
                            else if (statsCol) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Update 201: a small gear at the top right — straight to this page's settings (its effects and toggles).
            if (onSettings != null) {
                androidx.compose.foundation.layout.Box(
                    Modifier.align(Alignment.TopEnd).padding(top = vUnit * 1.6f, end = sidePad * 0.6f).size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.32f))
                        .padTarget("stage-settings") { onSettings() }
                        .clickable { onSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.Settings, "Lyrics stage settings", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
            // Volume (bottom-right) and the status line (bottom-left), over everything.
            if (F.stageVolume.state.value) {
                StageVolume(sz, accent, Modifier.align(Alignment.BottomEnd).padding(end = sidePad * 0.6f, bottom = vUnit * 1.2f))
            }
            if (statusMode != Features.StageStatus.NEVER) {
                val problems = buildList {
                    if (!notifAccess) add("Music needs Notification access (Settings › Permissions).")
                    if (react && !hasMic) add("Music reaction needs the Microphone permission: the beat is simulated for now.")
                    else if (react && noShare) add("This device doesn't share its sound: the beat is simulated.")
                    else if (react && deaf) add("No sound reaches Voltcap (the player may send it straight to the hardware): the beat is simulated.")
                    if (np != null && showLyrics) when (lookup) {
                        is LyricsRepository.Lookup.Off -> add("Lyrics are off (Settings › Music & sound › Lyrics).")
                        is LyricsRepository.Lookup.NotFound -> add("No lyrics found for this song.")
                        is LyricsRepository.Lookup.Failed -> add("Couldn't reach the lyrics, trying again.")
                        else -> Unit
                    }
                }
                val always = statusMode == Features.StageStatus.ALWAYS
                if (always || problems.isNotEmpty()) {
                    val found = (lookup as? LyricsRepository.Lookup.Found)?.lyrics
                    val info = if (!always) null else listOf(
                        "Music " + when { np == null -> "none"; playing -> "playing"; else -> "paused" } + if (idle) " · idle" else "",
                        "Beat " + when { !react -> "off"; listen && heard -> "heard"; else -> "simulated" },
                        "Lyrics " + when { !showLyrics -> "hidden"; found == null -> "none"; found.isSynced -> "synced"; else -> "unsynced" },
                        "Effects " + if (cap == 0 || !active) "resting" else "$cap fps cap"
                    ).joinToString(" · ")
                    Text(
                        (problems + listOfNotNull(info)).joinToString("\n"),
                        color = Color(0xFF9FE4A8), fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp)
                            .widthIn(max = maxWidth * 0.62f).clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** Update 200: the clock with the date and weekday beside it, ticking once a second (or a minute without seconds). */
@Composable
private fun StageClock(sz: StageSizes, h24: Boolean, seconds: Boolean) {
    val active = rememberIsActive()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, seconds) {
        if (!active) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            val step = if (seconds) 1000L else 60_000L
            delay(step - now % step + 15L)
        }
    }
    val locale = java.util.Locale.getDefault()
    val date = java.util.Date(now)
    val time = remember(now / 1000, h24, seconds) {
        val pattern = (if (h24) "HH:mm" else "h:mm") + (if (seconds) ":ss" else "") + (if (h24) "" else " a")
        java.text.SimpleDateFormat(pattern, if (h24) locale else java.util.Locale.ENGLISH).format(date)
    }
    val day = remember(now / 60_000) { java.text.SimpleDateFormat("d MMMM", locale).format(date) }
    val weekday = remember(now / 60_000) { java.text.SimpleDateFormat("EEEE", locale).format(date) }
    val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 18f), fontFeatureSettings = "tnum")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sz.unit * 1.6f + 8.dp)) {
        Text(time, color = Color.White, fontSize = sz.clock, fontWeight = FontWeight.Medium, maxLines = 1, style = shadow)
        Column {
            Text(day, color = Color.White, fontSize = sz.date, fontWeight = FontWeight.Medium, maxLines = 1, style = shadow)
            Text(weekday, color = Color.White.copy(alpha = 0.58f), fontSize = sz.week, maxLines = 1)
        }
    }
}

/** Update 200: the cover's slot (drawn by the stage's Canvas), title, artist, the timeline and the buttons. */
@Composable
private fun StageCard(
    np: NowPlaying,
    coverSize: Dp,
    sz: StageSizes,
    accent: Color,
    clock: State<Long>,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCoverPlaced: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(coverSize)) {
        // The cover itself is painted by the Canvas (so the circle visualizer, its pulse and the wave stay in one place).
        Spacer(Modifier.size(coverSize).onGloballyPositioned { onCoverPlaced(it.boundsInRoot()) })
        Spacer(Modifier.height(sz.gap))
        val textShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 12f))
        Crossfade(np.title to np.artist, animationSpec = tween(400), label = "stageTitle") { (title, artist) ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    title ?: "Unknown track", color = Color.White, fontSize = sz.title, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = textShadow
                )
                Text(
                    artist?.takeIf { it.isNotBlank() } ?: rememberAppLabel(np.packageName), color = Color.White.copy(alpha = 0.58f),
                    fontSize = sz.artist, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, style = textShadow
                )
            }
        }
        StageSeekBar(np, clock, sz, accent, enabled, onSeek)
        StageTimes(np, clock, sz)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StageButton(Icons.Filled.SkipPrevious, "Previous song", sz.btn, filled = false, enabled = enabled, onClick = onPrevious)
            Spacer(Modifier.width(sz.gap))
            StageButton(
                if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (np.isPlaying) "Pause" else "Play",
                sz.play, filled = true, enabled = enabled, onClick = onPlayPause
            )
            Spacer(Modifier.width(sz.gap))
            StageButton(Icons.Filled.SkipNext, "Next song", sz.btn, filled = false, enabled = enabled, onClick = onNext)
        }
    }
}

@Composable
private fun StageButton(icon: ImageVector, label: String, size: Dp, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (enabled) Modifier.tapOnly(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Update 200: the timeline — tap to jump there, or drag along it (the stage's own, not SeekBar: drags, a thicker bar
 * and a glow in the cover's colour). Drawn in the draw phase, so its 10-a-second movement never recomposes.
 */
@Composable
private fun StageSeekBar(np: NowPlaying, clock: State<Long>, sz: StageSizes, accent: Color, enabled: Boolean, onSeek: (Long) -> Unit) {
    val dur = np.durationMs
    val seekable = enabled && np.canSeek && dur > 0
    var drag by remember { mutableStateOf<Float?>(null) }
    var pending by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(np.positionMs, np.positionAt) { pending = null } // the player reported where it is now
    LaunchedEffect(pending) { if (pending != null) { delay(3_000); pending = null } }
    val seekLatest by rememberUpdatedState(onSeek)
    Box(
        Modifier.fillMaxWidth().height((sz.unit * 2.4f).coerceAtLeast(24.dp))
            .then(
                if (seekable) Modifier
                    .pointerInput(dur) {
                        detectTapGestures { o ->
                            val target = ((o.x / size.width).coerceIn(0f, 1f) * dur).toLong()
                            pending = target; seekLatest(target)
                        }
                    }
                    .pointerInput(dur) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                drag?.let { val target = (it * dur).toLong(); pending = target; seekLatest(target) }
                                drag = null
                            },
                            onDragCancel = { drag = null }
                        ) { change, _ ->
                            change.consume()
                            drag = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                else Modifier
            )
            .drawBehind {
                val f = drag ?: run {
                    val p = pending ?: np.currentPosition(clock.value)
                    if (dur > 0) (p.toFloat() / dur).coerceIn(0f, 1f) else 0f
                }
                val th = sz.bar.toPx()
                val y = size.height / 2f
                drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, y - th / 2f), Size(size.width, th), CornerRadius(th / 2f))
                val fw = size.width * f
                if (fw > 0f) {
                    drawRoundRect(accent.copy(alpha = 0.45f), Offset(0f, y - th), Size(fw, th * 2f), CornerRadius(th))
                    drawRoundRect(Color.White, Offset(0f, y - th / 2f), Size(max(fw, th), th), CornerRadius(th / 2f))
                }
                if (dur > 0) {
                    drawCircle(Color.Black.copy(alpha = 0.35f), th * 1.15f, Offset(fw, y + th * 0.25f))
                    drawCircle(Color.White, th * 0.95f, Offset(fw, y))
                }
            }
    )
}

/** Update 200: elapsed / length under the timeline; recomposes once a second at most. */
@Composable
private fun StageTimes(np: NowPlaying, clock: State<Long>, sz: StageSizes) {
    val sec by remember(np, clock) { derivedStateOf { np.currentPosition(clock.value) / 1000L } }
    val style = TextStyle(fontFeatureSettings = "tnum")
    Row(Modifier.fillMaxWidth()) {
        Text(formatTrackTime(sec * 1000L), color = Color.White.copy(alpha = 0.58f), fontSize = sz.time, style = style)
        Spacer(Modifier.weight(1f))
        Text(if (np.durationMs > 0) formatTrackTime(np.durationMs) else "--:--", color = Color.White.copy(alpha = 0.58f), fontSize = sz.time, style = style)
    }
}

/** Update 200: soft top and bottom edges on the lyrics (the dashboard's mask). */
private fun Modifier.stageFadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val mask = Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent)
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/**
 * Update 200: the lyrics beside (or under) the cover. Synced: the line being sung bright, a little bigger and glowing in
 * the cover's colour, the sung ones dimmer, the list gliding so the current line sits in the middle. Unsynced: the
 * lines scroll slowly with the song's position. The timing setting moves every song's lyrics earlier or later.
 */
@Composable
private fun StageLyrics(
    np: NowPlaying,
    lookup: LyricsRepository.Lookup?,
    clock: State<Long>,
    sz: StageSizes,
    accent: Color,
    active: Boolean,
    context: Context
) {
    val optIn by LyricsOptIn.enabled.collectAsState()
    val lyrics = (lookup as? LyricsRepository.Lookup.Found)?.lyrics
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                !optIn -> Column(Modifier.align(Alignment.CenterStart)) {
                    Text("Lyrics are off. " + LyricsOptIn.NOTE, color = Color.White.copy(alpha = 0.7f), fontSize = sz.note)
                    Spacer(Modifier.height(8.dp))
                    Chip("Turn on lyrics", accent = true) { LyricsOptIn.set(context, true) }
                }
                lyrics == null -> Text(
                    when (lookup) {
                        is LyricsRepository.Lookup.NotFound -> "No lyrics found for this song"
                        is LyricsRepository.Lookup.Failed -> "Couldn't load the lyrics (offline?). Trying again…"
                        else -> "Looking for lyrics…"
                    },
                    color = Color.White.copy(alpha = 0.58f), fontSize = sz.note, modifier = Modifier.align(Alignment.CenterStart)
                )
                // A new song starts the list afresh.
                else -> androidx.compose.runtime.key(np.title, np.artist) {
                    StageLyricLines(np, lyrics, clock, sz, accent, active)
                }
            }
        }
        if (lyrics != null && !lyrics.isSynced) {
            Text("Unsynced lyrics", color = Color.White.copy(alpha = 0.58f), fontSize = sz.time, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StageLyricLines(np: NowPlaying, lyrics: com.shelf.launcher.data.Lyrics, clock: State<Long>, sz: StageSizes, accent: Color, active: Boolean) {
    val synced = lyrics.isSynced
    val texts = remember(lyrics) { if (synced) lyrics.synced.map { it.text.ifBlank { "♪" } } else lyrics.plain }
    val heights = remember(lyrics) { IntArray(texts.size) }
    var layoutVersion by remember(lyrics) { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }
    val offsetMs = Features.stageLyricOffsetMs.state.value
    val index by remember(lyrics, np, clock, offsetMs) {
        derivedStateOf {
            if (!synced) -1 else {
                val p = np.currentPosition(clock.value) + offsetMs
                lyrics.synced.indexOfLast { it.atMs <= p }
            }
        }
    }
    val scroll = remember(lyrics) { Animatable(0f) }
    if (synced) {
        LaunchedEffect(index, layoutVersion, boxH) {
            if (boxH == 0) return@LaunchedEffect
            val i = index.coerceAtLeast(0)
            var top = 0
            for (k in 0 until i.coerceAtMost(heights.size)) top += heights[k]
            val target = boxH / 2f - (top + heights.getOrElse(i) { 0 } / 2f)
            scroll.animateTo(target, tween(550, easing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)))
        }
    } else {
        LaunchedEffect(layoutVersion, boxH, np.isPlaying, np.positionAt, active) {
            if (boxH == 0) return@LaunchedEffect
            while (true) {
                val total = heights.sum().toFloat()
                val frac = if (np.durationMs > 0) (np.currentPosition() / np.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                val target = boxH * 0.35f - frac * max(0f, total - boxH * 0.3f)
                if (!np.isPlaying || !active) { scroll.animateTo(target, tween(500)); break }
                scroll.animateTo(target, tween(500, easing = LinearEasing))
            }
        }
    }
    val padV = (sz.unit * 0.9f).coerceAtLeast(3.dp)
    Box(Modifier.fillMaxSize().clipToBounds().stageFadeEdges().onSizeChanged { boxH = it.height }) {
        Column(
            Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = scroll.value }
        ) {
            texts.forEachIndexed { i, text ->
                val state = when {
                    !synced -> 3
                    i == index -> 2
                    i < index -> 1
                    else -> 0
                }
                StageLine(
                    text, state, sz.line, accent, padV,
                    Modifier.onSizeChanged { if (i < heights.size && heights[i] != it.height) { heights[i] = it.height; layoutVersion++ } }
                )
            }
        }
    }
}

/** One lyric line. [state]: 0 to come, 1 sung, 2 being sung, 3 unsynced. */
@Composable
private fun StageLine(text: String, state: Int, size: TextUnit, accent: Color, padV: Dp, modifier: Modifier) {
    val color by animateColorAsState(
        when (state) {
            2 -> Color.White
            1 -> Color.White.copy(alpha = 0.4f)
            3 -> Color.White.copy(alpha = 0.75f)
            else -> Color.White.copy(alpha = 0.28f)
        }, tween(350), label = "stageLineColor"
    )
    val scale by animateFloatAsState(if (state == 2) 1.07f else 1f, tween(350), label = "stageLineScale")
    val density = LocalDensity.current
    val glow = if (state == 2) Shadow(accent, Offset.Zero, with(density) { size.toPx() } * 0.9f) else null
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, lineHeight = size * 1.3f,
        style = TextStyle(shadow = glow),
        modifier = modifier.fillMaxWidth().padding(vertical = padV).graphicsLayer {
            scaleX = scale; scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Stats panel
// ---------------------------------------------------------------------------------------------

private class StageDrive(val name: String, val percent: Int, val freeBytes: Long, val totalBytes: Long)

/** Update 200: one reading of the stats panel. Null = this device doesn't let an app read it (the row then hides). */
private class StageStatsSnap(
    val cpu: Int?, val gpu: Int?,
    val ramUsed: Long?, val ramTotal: Long?,
    val drives: List<StageDrive>,
    val down: Long?, val up: Long?
)

/**
 * Update 200: what Android lets an app read, for the stats panel (the dashboard got these from a PC helper). CPU = the
 * cores' current clock as a share of their top clock (/proc/stat, real load, is closed to apps on Android 8+); GPU =
 * the driver's busy figure (Adreno / Mali); RAM; the internal storage and SD cards; the network rate. Reuses
 * PerfReader.readText (the Performance page's safe file reader); kept separate from PerfSampler, which also hunts for
 * FPS every second — too much work for a corner of the music page next to a game.
 */
private class StageStatsReader(context: Context) {
    private val app = context.applicationContext
    private val cores: List<Pair<File, Long>> by lazy {
        File("/sys/devices/system/cpu").listFiles().orEmpty()
            .filter { it.name.matches(Regex("cpu[0-9]+")) }
            .mapNotNull { dir ->
                val top = PerfReader.readText(File(dir, "cpufreq/cpuinfo_max_freq"))?.trim()?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                File(dir, "cpufreq/scaling_cur_freq") to top
            }
    }
    /** The storage roots: internal first, then each removable card (found once). */
    private val roots: List<Pair<String, File>> by lazy {
        val list = mutableListOf("Internal" to android.os.Environment.getDataDirectory())
        runCatching {
            val sm = app.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            app.getExternalFilesDirs(null).drop(1).filterNotNull().forEachIndexed { i, dir ->
                val root = File(dir.absolutePath.substringBefore("/Android/"))
                val name = if (android.os.Build.VERSION.SDK_INT >= 24) runCatching { sm?.getStorageVolume(dir)?.getDescription(app) }.getOrNull() else null
                list += (name?.takeIf { it.isNotBlank() } ?: if (i == 0) "SD card" else "SD card ${i + 1}") to root
            }
        }
        list
    }
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    fun sample(): StageStatsSnap {
        val cpu = cores.mapNotNull { (cur, top) ->
            PerfReader.readText(cur)?.trim()?.toLongOrNull()?.let { (it * 100 / top).toInt().coerceIn(0, 100) }
        }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val mem = runCatching {
            val info = android.app.ActivityManager.MemoryInfo()
            (app.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
            if (info.totalMem > 0) (info.totalMem - info.availMem) to info.totalMem else null
        }.getOrNull()
        val drives = roots.mapNotNull { (name, dir) ->
            runCatching {
                val s = android.os.StatFs(dir.path)
                val total = s.totalBytes
                if (total <= 0) null else StageDrive(name, ((total - s.availableBytes) * 100 / total).toInt(), s.availableBytes, total)
            }.getOrNull()
        }
        var down: Long? = null
        var up: Long? = null
        val rx = android.net.TrafficStats.getTotalRxBytes()
        val tx = android.net.TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        if (rx >= 0 && tx >= 0 && rx != android.net.TrafficStats.UNSUPPORTED.toLong()) {
            if (lastAt > 0 && now > lastAt) {
                down = ((rx - lastRx).coerceAtLeast(0) * 1000L / (now - lastAt))
                up = ((tx - lastTx).coerceAtLeast(0) * 1000L / (now - lastAt))
            } else { down = 0L; up = 0L }
            lastRx = rx; lastTx = tx; lastAt = now
        }
        return StageStatsSnap(cpu, gpu(), mem?.first, mem?.second, drives, down, up)
    }

    private fun gpu(): Int? {
        fun pct(path: String) = PerfReader.readText(File(path))?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }?.coerceIn(0, 100)
        // Adreno (Snapdragon handhelds such as the AYN Thor): a plain percentage, else "busy total" counters.
        pct("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")?.let { return it }
        PerfReader.readText(File("/sys/class/kgsl/kgsl-3d0/gpubusy"))?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }
            ?.takeIf { it.size >= 2 && it[1] > 0 }?.let { return (100L * it[0] / it[1]).toInt().coerceIn(0, 100) }
        // Mali and others.
        return pct("/sys/class/misc/mali0/device/utilization") ?: pct("/sys/kernel/gpu/gpu_busy")
    }
}

/** Update 200: the stats, read once a second off the main thread while [on] (the panel shows and Voltcap is on screen). */
@Composable
private fun rememberStageStats(on: Boolean): StageStatsSnap? {
    val context = LocalContext.current
    return produceState<StageStatsSnap?>(null, on) {
        if (!on) { value = null; return@produceState }
        val reader = StageStatsReader(context)
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { reader.sample() }.getOrNull() }
            delay(1000)
        }
    }.value
}

private fun stageRate(bytesPerSec: Long): String =
    if (bytesPerSec > 1_048_576L) "%.1f MB/s".format(bytesPerSec / 1_048_576f) else "${(bytesPerSec / 1024L)} KB/s"

private fun stageSize(bytes: Long): String {
    val gib = bytes / 1_073_741_824.0
    return if (gib >= 1024.0) "%.2f TB".format(gib / 1024.0) else "%.1f GB".format(gib)
}

/** One stat line: what it is, its value, and a bar (red from 90 %). */
private class StageStatRow(val label: String, val value: String, val percent: Int?, val sub: String? = null)

/**
 * Update 200: the stats panel. Wide screens: a panel at the left like the dashboard's. Square or tall screens: a
 * compact strip under the clock. Each row shows only when switched on and readable on this device.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StageStatsPanel(s: StageStatsSnap, compact: Boolean, sz: StageSizes, accent: Color, modifier: Modifier = Modifier) {
    val F = Features
    val rows = buildList {
        if (F.stageCpu.state.value && s.cpu != null) add(StageStatRow("CPU", "${s.cpu}%", s.cpu))
        if (F.stageGpu.state.value && s.gpu != null) add(StageStatRow("GPU", "${s.gpu}%", s.gpu))
        if (F.stageRam.state.value && s.ramUsed != null && s.ramTotal != null && s.ramTotal > 0) {
            add(StageStatRow("RAM", "%.1f / %.0f GB".format(s.ramUsed / 1_073_741_824f, s.ramTotal / 1_073_741_824f), (s.ramUsed * 100 / s.ramTotal).toInt()))
        }
        if (F.stageDrives.state.value) s.drives.forEach { d ->
            add(StageStatRow(d.name, "${d.percent}% used", d.percent, "${stageSize(d.freeBytes)} free of ${stageSize(d.totalBytes)}"))
        }
        if (F.stageNet.state.value && s.down != null && s.up != null) add(StageStatRow("Network", "↓ ${stageRate(s.down)}   ↑ ${stageRate(s.up)}", null))
    }
    if (rows.isEmpty()) return
    val tnum = TextStyle(fontFeatureSettings = "tnum")
    @Composable
    fun Bar(percent: Int, height: Dp) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))) {
            Box(
                Modifier.fillMaxWidth((percent / 100f).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                    .background(if (percent >= 90) Color(0xFFFF7A6B) else accent)
            )
        }
    }
    if (compact) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { r ->
                Column(
                    Modifier.widthIn(min = 84.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF080C16).copy(alpha = 0.62f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, color = Color.White, fontSize = sz.statSub, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Text(r.value, color = Color.White.copy(alpha = 0.58f), fontSize = sz.statSub, maxLines = 1, style = tnum)
                    }
                    if (r.percent != null) {
                        Spacer(Modifier.height(3.dp))
                        Bar(r.percent, 3.dp)
                    }
                }
            }
        }
    } else {
        Column(
            modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF080C16).copy(alpha = 0.62f))
                .padding(horizontal = sz.unit * 1.6f + 8.dp, vertical = sz.unit * 2.2f + 6.dp),
            verticalArrangement = Arrangement.spacedBy(sz.unit * 1.9f + 4.dp)
        ) {
            rows.forEach { r ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, color = Color.White, fontSize = sz.stat, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.weight(1f))
                        Text(r.value, color = Color.White.copy(alpha = 0.58f), fontSize = sz.stat, maxLines = 1, style = tnum)
                    }
                    if (r.percent != null) {
                        Spacer(Modifier.height(sz.unit * 0.6f))
                        Bar(r.percent, sz.bar)
                    }
                    if (r.sub != null) Text(r.sub, color = Color.White.copy(alpha = 0.58f), fontSize = sz.statSub, maxLines = 1, style = tnum)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Volume
// ---------------------------------------------------------------------------------------------

/**
 * Update 200: the media volume (Android's music stream) in the bottom-right corner. A touch screen has no hover, so a
 * small speaker button opens the slider; it closes by itself 3 s after the last touch. Uses the same helpers as the
 * R1 panel's volume (they snap to the system's real steps).
 */
@Composable
private fun StageVolume(sz: StageSizes, accent: Color, modifier: Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var level by remember { mutableIntStateOf(mediaVolumePercent(context)) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    var beforeMute by remember { mutableIntStateOf(50) }
    LaunchedEffect(open) { if (open) level = mediaVolumePercent(context) }
    LaunchedEffect(open, touchedAt) { if (open) { delay(3_000); open = false } }
    fun touched() { touchedAt = SystemClock.uptimeMillis() }
    fun set(p: Int) { level = setMediaVolumePercent(context, p); touched() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(Color(0xFF080C16).copy(alpha = 0.66f)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).tapOnly {
                if (!open) open = true
                else if (level > 0) { beforeMute = level; set(0) } else set(beforeMute.coerceAtLeast(10))
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (level == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, if (open) "Mute / unmute" else "Volume", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (open) {
            Box(Modifier.size(36.dp).clip(CircleShape).tapOnly { level = stepMediaVolume(context, -1); touched() }, contentAlignment = Alignment.Center) {
                Text("−", color = Color.White, fontSize = 20.sp)
            }
            val latestSet by rememberUpdatedState<(Int) -> Unit> { set(it) }
            Box(
                Modifier.width(120.dp).height(36.dp)
                    .pointerInput(Unit) { detectTapGestures { o -> latestSet((o.x / size.width * 100f).roundToInt().coerceIn(0, 100)) } }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            latestSet((change.position.x / size.width * 100f).roundToInt().coerceIn(0, 100))
                        }
                    }
                    .drawBehind {
                        val th = 4.dp.toPx()
                        val y = size.height / 2f
                        val fw = size.width * level / 100f
                        drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(0f, y - th / 2f), Size(size.width, th), CornerRadius(th / 2f))
                        drawRoundRect(Color.White, Offset(0f, y - th / 2f), Size(fw, th), CornerRadius(th / 2f))
                        drawCircle(accent.copy(alpha = 0.5f), 10.dp.toPx(), Offset(fw, y))
                        drawCircle(Color.White, 7.dp.toPx(), Offset(fw, y))
                    }
            )
            Box(Modifier.size(36.dp).clip(CircleShape).tapOnly { level = stepMediaVolume(context, 1); touched() }, contentAlignment = Alignment.Center) {
                Text("+", color = Color.White, fontSize = 20.sp)
            }
            Text(
                if (level == 0) "Muted" else "$level%", color = Color.White.copy(alpha = 0.7f), fontSize = sz.time,
                style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.width(48.dp).padding(end = 8.dp), textAlign = TextAlign.End
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

/**
 * Update 200: the Lyrics stage's settings, shown under "Music page layout" when the stage is picked (Settings ›
 * Appearance › Second screen art & music). The same options as the dashboard's Wallpaper Engine properties, grouped.
 */
@Composable
internal fun LyricsStageSettings() {
    com.shelf.launcher.Breadcrumbs.mark("LyricsStageSettings")
    val context = LocalContext.current
    val F = Features
    var hasMic by remember { mutableStateOf(AudioReactive.hasPermission(context)) }
    var micDenied by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        micDenied = !granted
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Kept readable after a restart, like the second screen's own wallpaper.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            F.stageIdlePicture.value = uri.toString()
        }
    }
    val lyricsOn by LyricsOptIn.enabled.collectAsState()

    Column {
        MutedNote("The cover big with a visualizer round it, synced lyrics beside it, a clock and stats, over the cover blurred. Tap the timeline to jump, the speaker for the volume.")
        Spacer(Modifier.height(12.dp))

        SectionLabel("Effects")
        FeatureSwitch(Icons.Filled.AutoAwesome, "Floating particles", "Soft dots drifting upwards, more and faster on the beat.", F.stageParticles)
        FeatureSwitch(Icons.Filled.GraphicEq, "Circle visualizer around the cover", "Bars all round the cover that follow the music, bass at the bottom.", F.stageCircle)
        FeatureSwitch(Icons.Filled.Equalizer, "Spectrum bars (bottom)", "A mirrored spectrum along the bottom: low notes in the middle.", F.stageSpectrum)
        FeatureSwitch(Icons.Filled.Adjust, "Beat rings", "A ring leaves the cover on each beat, each in the next colour.", F.stageRings)
        FeatureSwitch(Icons.Filled.Flare, "Aurora glow", "Three big soft lights drifting in the cover's colours. Off in Low power mode.", F.stageAurora)
        FeatureSwitch(Icons.Filled.Stars, "Starfield warp", "Stars flying out of the middle, faster on the beat. Off in Low power mode.", F.stageStars)
        FeatureSwitch(Icons.Filled.BubbleChart, "Bokeh lights", "Large blurred lights that swell with the bass. Off in Low power mode.", F.stageBokeh)
        FeatureSwitch(Icons.Filled.Waves, "Flowing wave lines", "Three lines waving across the bottom with the music. Off in Low power mode.", F.stageLines)
        PercentSlider("Particle amount", F.stageParticleAmount.state.value, { F.stageParticleAmount.value = it }, min = 20, max = 300, valueText = { "$it" })
        PercentSlider("Animation FPS cap", F.stageFps.state.value, { F.stageFps.value = it }, min = 15, max = 60, valueText = { "$it fps" })
        MutedNote("Lower is lighter on the battery and on a game running beside it.")
        Spacer(Modifier.height(8.dp))
        FeatureSwitch(Icons.Filled.BatterySaver, "Low power mode", "At most 20 fps and 40 particles; the aurora, stars, bokeh and wave lines stay off.", F.stageLowPower)
        Spacer(Modifier.height(6.dp))

        SectionLabel("Reactions")
        FeatureSwitch(Icons.Filled.Insights, "Effects react to music", "Particles, rings and lights grow and speed up with the bass.", F.stageFxReact)
        FeatureSwitch(Icons.Filled.BlurOn, "Background reacts to music", "The blurred cover and the cover itself pulse and zoom gently with the bass.", F.stageBgReact)
        PercentSlider("Reaction strength", F.stageReaction.state.value, { F.stageReaction.value = it }, min = 50, max = 300, valueText = { "$it%" })
        MutedNote(
            when {
                hasMic && AudioReactive.unsupported -> "This device doesn't share its sound, so the beat is simulated."
                hasMic -> "Listening to the music: Android calls this the Microphone permission. Only the sound going to the speaker is read, a few times a second, while the stage is on screen; nothing is recorded or saved."
                micDenied -> "The Microphone permission was refused, so the beat is simulated. It can be allowed in Android's settings for Voltcap."
                else -> "Without the Microphone permission the effects follow a simulated beat. Android calls it that, but only the sound going to the speaker is read; nothing is recorded or saved."
            }
        )
        if (!hasMic) {
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Allow the microphone") { askMic.launch(Manifest.permission.RECORD_AUDIO) } }
        }
        Spacer(Modifier.height(12.dp))

        SectionLabel("Background")
        FeatureSwitch(Icons.Filled.Palette, "Colors follow the album cover", "The effects, the glow of the lyrics and the timeline take the cover's colours. Off: soft white and blue.", F.stageCoverColors)
        PercentSlider("Background blur", F.stageBgBlur.state.value, { F.stageBgBlur.value = it }, min = 0, max = 60, valueText = { if (it == 0) "Sharp" else "$it" })
        PercentSlider("Background brightness", F.stageBgBright.state.value, { F.stageBgBright.value = it }, min = 20, max = 100)
        Spacer(Modifier.height(12.dp))

        SectionLabel("Idle")
        FeatureChoice("When nothing is playing: effects", Features.StageIdleFx.values(), F.stageIdleFx) { it.label }
        PercentSlider("Idle after paused for", F.stageIdleAfter.state.value, { F.stageIdleAfter.value = it }, min = 5, max = 300, valueText = { "$it s" })
        MutedNote("Idle: the cover, the player and the lyrics fade away; the clock and the stats stay.")
        Spacer(Modifier.height(8.dp))
        Text("Picture shown while idle (optional)", color = ShelfTheme.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        ChipFlow {
            Chip(if (F.stageIdlePicture.state.value.isEmpty()) "Pick a picture…" else "Pick another picture…") {
                runCatching { pickPicture.launch(arrayOf("image/*")) }
            }
            if (F.stageIdlePicture.state.value.isNotEmpty()) Chip("No picture", accent = false) { F.stageIdlePicture.value = "" }
        }
        Spacer(Modifier.height(12.dp))

        SectionLabel("Clock & size")
        FeatureSwitch(Icons.Filled.Schedule, "24-hour clock", "Off: a 12-hour clock with AM / PM.", F.stageClock24)
        FeatureSwitch(Icons.Filled.Timer, "Show seconds", "The clock ticks every second.", F.stageSeconds)
        PercentSlider("Interface size", F.stageUiScale.state.value, { F.stageUiScale.value = it }, min = 70, max = 130)
        Spacer(Modifier.height(12.dp))

        SectionLabel("Lyrics & volume")
        FeatureSwitch(Icons.Filled.Lyrics, "Show lyrics", "Synced lyrics beside the cover (under it on a tall screen); unsynced ones scroll with the song.", F.stageLyrics)
        if (F.stageLyrics.state.value && !lyricsOn) {
            MutedNote("Lyrics are switched off. " + LyricsOptIn.NOTE)
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Turn on lyrics") { LyricsOptIn.set(context, true) } }
            Spacer(Modifier.height(8.dp))
        }
        // Stored in ms; the slider moves in 50 ms steps (the D-pad in 250 ms, like the dashboard's 0.25 s).
        PercentSlider(
            "Lyric timing, all songs", F.stageLyricOffsetMs.state.value / 50, { F.stageLyricOffsetMs.value = it * 50 },
            min = -100, max = 100,
            valueText = { v -> if (v == 0) "On time" else (if (v > 0) "+" else "−") + "%.2f s".format(abs(v) * 0.05f) }
        )
        MutedNote("Words come too late? Move it to +. Too early? To −.")
        Spacer(Modifier.height(8.dp))
        FeatureSwitch(Icons.Filled.VolumeUp, "Show volume control", "A speaker button in the bottom-right corner that opens a media volume slider.", F.stageVolume)
        Spacer(Modifier.height(4.dp))

        SectionLabel("Status line")
        FeatureChoice("Status line (bottom-left)", Features.StageStatus.values(), F.stageStatus) { it.label }
        MutedNote("Says what's missing, for example \"Music reaction needs the Microphone permission\" or \"No lyrics found\". Always: also what the stage is doing.")
    }
}
