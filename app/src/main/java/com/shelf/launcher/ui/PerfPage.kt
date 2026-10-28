package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.perf.PerfSampler
import com.shelf.launcher.data.perf.PerfSnapshot
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Keeps the sampler running only while [content] is on screen AND the host is
 * RESUMED (so a stopped activity or a screen that is off costs nothing).
 */
@Composable
internal fun WhileResumedSampling() {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var held = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if (!held) { PerfSampler.acquire(context); held = true }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> if (held) { PerfSampler.release(); held = false }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (held) { PerfSampler.release(); held = false }
        }
    }
}

/** The second-screen Performance page: larger live readouts and 60 s sparklines. */
@Composable
fun PerfPage(modifier: Modifier = Modifier) {
    WhileResumedSampling()
    val s by PerfSampler.snapshot.collectAsState()
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // (Update 112: with no game running the screen's own rate, its note under it.)
            Tile("FPS", s.fps?.let { "${Math.round(it)}" } ?: "n/a", if (s.fps != null && s.fpsNote != null) s.fpsNote else s.frameMs?.let { "%.1f ms / frame".format(it) }, s.fpsHistory, Modifier.weight(1f))
            Tile(
                "CPU", s.cpuMaxMhz?.let { "$it MHz" } ?: "n/a",
                listOfNotNull(s.cpuAvgMhz?.let { "avg $it MHz" }, s.cpuPercent?.let { "$it%" }).joinToString(" · ").ifEmpty { null },
                s.cpuHistory, Modifier.weight(1f)
            )
            Tile(
                "GPU", s.gpuPercent?.let { "$it%" } ?: s.gpuMhz?.let { "$it MHz" } ?: "n/a",
                if (s.gpuPercent != null) s.gpuMhz?.let { "$it MHz" } else null,
                s.gpuHistory, Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(
                "RAM",
                if (s.ramUsedMb != null && s.ramTotalMb != null) "%.1f / %.1f GB".format(s.ramUsedMb!! / 1024f, s.ramTotalMb!! / 1024f) else "n/a",
                null, emptyList(), Modifier.weight(1f)
            )
            Tile("Temp", s.socTempC?.let { "%.0f°C".format(it) } ?: "n/a", s.batteryTempC?.let { "battery %.0f°C".format(it) }, emptyList(), Modifier.weight(1f))
            Tile(
                "Power", s.powerW?.let { "~%.1f W".format(it) } ?: "n/a",
                if (s.powerW != null) (if (s.charging == true) "approx., charging" else "approx.") else null,
                emptyList(), Modifier.weight(1f)
            )
        }
        if (s.fps == null) {
            s.fpsNote?.let { Text(it, color = ShelfTheme.textMuted, fontSize = 11.sp) }
                ?: Text("FPS n/a.", color = ShelfTheme.textMuted, fontSize = 11.sp)
            FpsWhyNa()
        }
    }
}

@Composable
private fun Tile(label: String, value: String, sub: String?, history: List<Float>, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(ShelfTheme.colors.surfaceRaised).padding(12.dp)) {
        Text(label, color = ShelfTheme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = ShelfTheme.colors.textPrimary, fontSize = 22.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(22.sp), maxFontSize = 22.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(sub ?: " ", color = ShelfTheme.textMuted, fontSize = 11.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(11.sp), maxFontSize = 11.sp), minLines = 2, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        // Update 112: every tile keeps the sparkline's room, so all six are the same size.
        Spacer(Modifier.height(6.dp))
        if (history.any { !it.isNaN() }) Sparkline(history, Modifier.fillMaxWidth().height(34.dp))
        else Spacer(Modifier.height(34.dp))
    }
}

/** 60-second line; gaps (NaN) break the line. */
@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier) {
    val color = ShelfTheme.accent
    Canvas(modifier) {
        val real = values.filter { !it.isNaN() }
        if (real.size < 2) return@Canvas
        val lo = real.min()
        val hi = real.max()
        val range = (hi - lo).takeIf { it > 0f } ?: 1f
        val stepX = size.width / 59f
        val offset = 60 - values.size
        val path = Path()
        var pen = false
        values.forEachIndexed { i, v ->
            if (v.isNaN()) { pen = false; return@forEachIndexed }
            val x = (offset + i) * stepX
            val y = size.height - (v - lo) / range * size.height * 0.9f - size.height * 0.05f
            if (!pen) { path.moveTo(x, y); pen = true } else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
