package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.AudioReactive
import com.shelf.launcher.data.Features
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * Corner visualizers (Appearance › Music player › Corner visualizers): small space scenes that move with
 * the music in a corner of the home screen — a ringed planet, orbiting moons, a pulsar, a black hole or a
 * nebula bloom. One small canvas each, ~60 fps while music plays (12 fps idling, stopped when the home
 * screen is covered, in deep idle or with no music when "Only while music plays" is on). They follow the
 * real sound when a music theme listens (AudioReactive), else a steady simulated beat.
 */

/** Draws the chosen corner visualizer(s) in this Box (the home screen's root). */
@Composable
internal fun BoxScope.CornerVisualizers(
    playing: Boolean,
    colors: com.shelf.launcher.ui.theme.ShelfColors,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
    /** Corners already holding something (the round shortcuts' ☰, a music player there): step past it. */
    busy: Set<Alignment> = emptySet()
) {
    val kind = Features.cornerViz.state.value
    if (kind == Features.CornerViz.OFF) return
    if (Features.cornerVizOnlyMusic.state.value && !playing) return
    // Only hidden when nothing plays: while music plays it never stops (long idle, a window on top).
    if (!playing && (LocalSceneCovered.current || HomeIdle.deep)) return
    val size = Features.cornerVizSize.state.value.dp
    val corner = Features.cornerVizCorner.state.value
    val zone = safeZone()
    val spots = when (corner) {
        Features.VizCorner.TOP_LEFT -> listOf(Alignment.TopStart)
        Features.VizCorner.TOP_RIGHT -> listOf(Alignment.TopEnd)
        Features.VizCorner.BOTTOM_LEFT -> listOf(Alignment.BottomStart)
        Features.VizCorner.BOTTOM_RIGHT -> listOf(Alignment.BottomEnd)
        Features.VizCorner.BOTH_TOP -> listOf(Alignment.TopStart, Alignment.TopEnd)
    }
    // One clock for both corners.
    var t by remember { mutableFloatStateOf(0f) }
    // While music plays it always runs at full rate (idle or not): the visualizer is what you watch then.
    // (Update 126: 60 while music plays; it was 30.)
    val fps = if (playing) 60 else 12
    LaunchedEffect(fps) {
        // One frame, then sleep until the next step is due: the loop doesn't wake on every display frame.
        val stepMs = 1000L / fps
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now -> t = (now - start) / 1e9f }
            kotlinx.coroutines.delay((stepMs - 4L).coerceAtLeast(1L))
        }
    }
    spots.forEachIndexed { i, a ->
        val top = a == Alignment.TopStart || a == Alignment.TopEnd
        // Something already in this corner: the visualizer sits just past it instead of on top of it.
        // (A corner music player is up to ~90 dp tall with its lyric line: 64 dp ran into its bottom.)
        val step = if (a in busy) 96.dp else 0.dp
        Box(
            Modifier.align(a).padding(
                start = zone.left + 6.dp, end = zone.right + 6.dp,
                top = if (top) maxOf(topInset, zone.top) + step else 0.dp,
                bottom = if (!top) maxOf(bottomInset, zone.bottom) + step else 0.dp
            ).size(size)
        ) {
            val mirror = i == 1
            Canvas(Modifier.size(size)) { drawViz(kind, t + i * 1.7f, playing, colors, mirror) }
        }
    }
}

/** The music right now: bass / mid / high and a kick envelope, real or simulated. */
private class Levels(val bass: Float, val mid: Float, val high: Float, val kick: Float, val eq: FloatArray)

private fun levels(t: Float, playing: Boolean): Levels {
    if (!playing) return Levels(0.08f, 0.06f, 0.05f, 0f, FloatArray(8) { 0.06f })
    if (AudioReactive.live) {
        val age = (System.currentTimeMillis() - AudioReactive.beatAt) / 1000f
        return Levels(AudioReactive.bass, AudioReactive.mid, AudioReactive.high, exp(-age * 5f).coerceIn(0f, 1f), AudioReactive.eq.copyOf())
    }
    // Simulated: 112 bpm with a little swing so it never looks like a metronome.
    val beat = 60f / 112f
    val ph = (t % beat) / beat
    val kick = exp(-ph * 6f)
    val bar = (t / (beat * 8f)).toInt()
    val busy = 0.55f + 0.35f * sin(bar * 1.3f).let { it * it }
    val eq = FloatArray(8) { b -> (busy * (0.35f + 0.5f * kick * (1f - b / 9f)) + 0.15f * sin(t * (2.1f + b * 0.9f)) ).coerceIn(0.03f, 1f) }
    return Levels(0.3f + 0.6f * kick * busy, 0.35f + 0.25f * busy, 0.25f + 0.2f * sin(t * 3f).let { it * it }, kick, eq)
}

private fun DrawScope.drawViz(kind: Features.CornerViz, t: Float, playing: Boolean, c: com.shelf.launcher.ui.theme.ShelfColors, mirror: Boolean) {
    val l = levels(t, playing)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f
    val a = c.accent
    val s = c.secondary
    // A soft glow behind every scene.
    drawCircle(Brush.radialGradient(listOf(s.copy(alpha = 0.10f + 0.12f * l.bass), a.copy(alpha = 0.05f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    when (kind) {
        Features.CornerViz.PLANET -> {
            val pr = r * (0.34f + 0.04f * l.kick)
            // Ring behind the planet (upper half), the planet, then the ring's front half.
            val tilt = if (mirror) 18f else -18f
            rotate(tilt, Offset(cx, cy)) {
                ring(cx, cy, pr * 1.9f, pr * 0.55f, a, s, l, t, back = true)
            }
            drawCircle(Brush.radialGradient(listOf(lerpC(s, Color.White, 0.25f), s, Color(0xFF0B0D14)), Offset(cx - pr * 0.35f, cy - pr * 0.35f), pr * 1.5f), pr, Offset(cx, cy))
            // Cloud bands that drift with the mids.
            for (b in 0 until 3) {
                val y = cy - pr * 0.45f + b * pr * 0.42f
                drawLine(a.copy(alpha = 0.18f + 0.2f * l.mid), Offset(cx - pr * 0.8f, y + sin(t + b) * 2f), Offset(cx + pr * 0.8f, y - sin(t + b) * 2f), strokeWidth = pr * 0.08f)
            }
            rotate(tilt, Offset(cx, cy)) {
                ring(cx, cy, pr * 1.9f, pr * 0.55f, a, s, l, t, back = false)
            }
        }
        Features.CornerViz.ORBITS -> {
            drawCircle(Brush.radialGradient(listOf(Color.White, a, a.copy(alpha = 0f)), Offset(cx, cy), r * 0.3f), r * (0.16f + 0.05f * l.kick), Offset(cx, cy))
            for (m in 0 until 6) {
                val orbit = r * (0.32f + m * 0.11f)
                drawOval(a.copy(alpha = 0.16f), Offset(cx - orbit, cy - orbit * 0.62f),
                    androidx.compose.ui.geometry.Size(orbit * 2f, orbit * 1.24f), style = Stroke(1.dp.toPx()))
                val speed = (0.9f - m * 0.11f) * (if (mirror) -1f else 1f)
                val ang = t * speed + m * 1.9f
                val e = l.eq[(m + 1).coerceAtMost(7)]
                val mr = r * (0.035f + 0.06f * e)
                val p = Offset(cx + cos(ang) * orbit, cy + sin(ang) * orbit * 0.62f)
                drawCircle(lerpC(a, s, m / 5f).copy(alpha = 0.35f), mr * 2.2f, p)
                drawCircle(lerpC(a, s, m / 5f), mr, p)
            }
        }
        Features.CornerViz.PULSAR -> {
            // Two sweeping beams and rings that leave on every kick.
            val spin = (t * 1.6f + 0.12f * sin(t * 0.7f)) * (if (mirror) -1f else 1f)
            for (k in 0 until 2) {
                val ang = spin + k * PI.toFloat()
                val end = Offset(cx + cos(ang) * r, cy + sin(ang) * r)
                drawLine(Brush.linearGradient(listOf(a.copy(alpha = 0.8f), Color.Transparent), Offset(cx, cy), end), Offset(cx, cy), end, strokeWidth = r * (0.08f + 0.1f * l.high))
            }
            for (w in 0 until 3) {
                val ph = ((t * 0.9f + w / 3f) % 1f)
                drawCircle(s.copy(alpha = (1f - ph) * (0.25f + 0.5f * l.bass)), r * (0.2f + 0.8f * ph), Offset(cx, cy), style = Stroke(2f + 3f * l.kick))
            }
            drawCircle(Color.White, r * (0.08f + 0.05f * l.kick), Offset(cx, cy))
        }
        Features.CornerViz.BLACK_HOLE -> {
            // An accretion disc of streaks spiralling in, brighter with the bass; the dark centre stays still.
            val n = 46
            for (i in 0 until n) {
                val f = i / n.toFloat()
                val life = ((t * 0.22f + f * 7.3f) % 1f)
                val rad = r * (0.95f - 0.7f * life)
                val ang = f * 31f + life * 6f * (if (mirror) -1f else 1f)
                val p = Offset(cx + cos(ang) * rad, cy + sin(ang) * rad * 0.42f)
                val col = lerpC(s, a, life)
                drawCircle(col.copy(alpha = (0.2f + 0.7f * life) * (0.5f + 0.5f * l.bass)), 1.4f + 2.4f * life * (0.6f + l.high), p)
            }
            drawCircle(Brush.radialGradient(listOf(Color.Transparent, a.copy(alpha = 0.6f + 0.3f * l.kick), Color.Transparent), Offset(cx, cy), r * 0.36f), r * 0.36f, Offset(cx, cy))
            drawCircle(Color.Black, r * 0.2f, Offset(cx, cy))
        }
        Features.CornerViz.NEBULA -> {
            // Eight petals of light, one per band, breathing with it.
            for (b in 0 until 8) {
                val e = l.eq[b]
                val ang = b / 8f * 2f * PI.toFloat() + t * 0.15f * (if (mirror) -1f else 1f)
                val d = r * (0.18f + 0.38f * e)
                val p = Offset(cx + cos(ang) * d, cy + sin(ang) * d)
                val col = lerpC(a, s, b / 7f)
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), p, r * (0.22f + 0.2f * e)), r * (0.22f + 0.2f * e), p, blendMode = BlendMode.Plus)
            }
            drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * l.kick), r * 0.05f, Offset(cx, cy))
        }
        Features.CornerViz.OFF -> Unit
    }
}

/** Half of a planet ring made of particles whose brightness follows the equalizer. */
private fun DrawScope.ring(cx: Float, cy: Float, rx: Float, ry: Float, a: Color, s: Color, l: Levels, t: Float, back: Boolean) {
    val n = 64
    for (i in 0 until n) {
        val ang = i / n.toFloat() * 2f * PI.toFloat() + t * 0.3f
        val sy = sin(ang)
        if ((sy < 0f) != back) continue
        val band = l.eq[(i * 8 / n).coerceIn(0, 7)]
        val rr = 1f + 0.08f * band
        val p = Offset(cx + cos(ang) * rx * rr, cy + sy * ry * rr)
        drawCircle(lerpC(a, s, band).copy(alpha = if (back) 0.45f else 0.9f), 1.2f + 2.6f * band, p)
    }
}

private fun lerpC(x: Color, y: Color, f: Float) = androidx.compose.ui.graphics.lerp(x, y, f.coerceIn(0f, 1f))


/** Appearance › Music player › Corner visualizers. */
@Composable
internal fun CornerVisualizerSettings() {
    val f = Features
    FoldGroup("Corner visualizers", summary = f.cornerViz.state.value.label, key = "corner_viz") {
        ChipFlow {
            Features.CornerViz.values().forEach { k -> Chip(k.label, accent = f.cornerViz.state.value == k) { f.cornerViz.value = k } }
        }
        if (f.cornerViz.state.value != Features.CornerViz.OFF) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            ChipFlow {
                Features.VizCorner.values().forEach { k -> Chip(k.label, accent = f.cornerVizCorner.state.value == k) { f.cornerVizCorner.value = k } }
            }
            PercentSlider("Size", f.cornerVizSize.state.value, { f.cornerVizSize.value = it }, min = 70, max = 220, live = true, valueText = { "$it dp" })
            ChipFlow {
                Chip(if (f.cornerVizOnlyMusic.state.value) "✓ Only while music plays" else "Only while music plays", accent = f.cornerVizOnlyMusic.state.value) {
                    f.cornerVizOnlyMusic.toggle()
                }
            }
        }
        MutedNote(
            "Small space scenes in a corner of the home screen that move with the music, in the song's colours: " +
                "a ringed planet, orbiting moons, a pulsar, a black hole or a nebula. They follow the real sound when a " +
                "music theme listens (React to the real beat), otherwise a steady beat. Light: one small drawing, paused " +
                "when the home screen is covered or idle."
        )
    }
}
