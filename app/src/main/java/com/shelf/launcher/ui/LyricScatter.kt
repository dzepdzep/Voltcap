package com.shelf.launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.LyricsRepository
import com.shelf.launcher.data.MusicLyricsStyle
import com.shelf.launcher.data.MusicThemeSettings
import com.shelf.launcher.ui.theme.ShelfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Bands of this screen (top to bottom, in px) the scattered lyrics keep out of: the status bar, the
 * game row, the player, the legend. Provided by the home screen; elsewhere (second screen,
 * screensaver) nothing is in the way.
 */
internal val LocalSceneAvoid = compositionLocalOf { emptyList<ClosedFloatingPointRange<Float>>() }

private class Bubble(
    val id: Long, val text: String, val cx: Float, val cy: Float, val color: Color, val sizeSp: Float,
    val word: Boolean, val delayMs: Long, val holdMs: Long
) {
    /** Too many on screen: this (oldest) one fades out early. */
    var leaving by mutableStateOf(false)
    /** Rough box, for keeping the next ones from landing on top of it. */
    var w = 0f
    var h = 0f
}

/**
 * The Lyric cloud theme's words: each line (or each word, Appearance › Theme) appears at a random free
 * spot, in one of the cover's colours, fading and rising softly in, staying while it's sung and
 * fading out as the next ones come — never popping. Only the fades animate (a few at a time); between
 * lines nothing redraws. Size follows the music theme's Big / Small lyrics; Off shows none.
 */
@Composable
internal fun LyricScatterLayer(colors: ShelfColors, modifier: Modifier) {
    // No early return inside Box (inline): this project's Compose compiler mishandles them (see AppIcons.kt).
    Box(modifier) {
        val playing = MusicTheme.nowPlaying
        val style = MusicThemeSettings.lyrics
        if (playing != null && style != MusicLyricsStyle.OFF) LyricScatterContent(playing, style, colors)
    }
}

@Composable
private fun LyricScatterContent(playing: com.shelf.launcher.data.NowPlaying, style: MusicLyricsStyle, colors: ShelfColors) {
    run {
        val lookup = rememberSongLyrics(playing)
        val lyrics = (lookup as? LyricsRepository.Lookup.Found)?.lyrics
        val position = rememberPlaybackPosition(playing)
        // Moving the whole time the song plays (never frozen by Lite / a window on top).
        val active = rememberIsActive() && playing.isPlaying
        val palette = rememberLyricPalette(MusicTheme.softArt, MusicTheme.colors ?: colors)
        val avoid = LocalSceneAvoid.current
        val density = LocalDensity.current
        var area by remember { mutableStateOf(IntSize.Zero) }
        val bubbles = remember { mutableStateListOf<Bubble>() }
        val mode = Features.lyricScatter.state.value
        val big = style == MusicLyricsStyle.BIG

        // The line being sung now: its index (changes trigger the next words), text and how long it lasts.
        // Update 144: with the Timing nudge (all lyric themes), like Karaoke, Sheet and Neon.
        val offset = Features.lyricsOffsetMs.state.value
        val sungAt = position + offset
        val line = remember(lyrics, sungAt / 250, playing.durationMs) { lyrics?.let { currentLine(it, sungAt, playing.durationMs) } }
        val key = "${playing.title}|${line?.index}"
        val latestPalette by rememberUpdatedState(palette)
        val latestAvoid by rememberUpdatedState(avoid)
        LaunchedEffect(key, active) {
            val l = line ?: return@LaunchedEffect
            if (!active || area == IntSize.Zero) return@LaunchedEffect
            val text = l.text.trim()
            if (text.isEmpty() || text == "♪") return@LaunchedEffect
            val compact = with(density) { area.height.toDp() } < 430.dp
            val k = if (compact) 0.75f else 1f
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val placer = Placer(area, latestAvoid, bubbles, density.density)
            val rnd = java.util.Random(key.hashCode().toLong())
            fun colour(i: Int) = latestPalette[(i + rnd.nextInt(latestPalette.size)) % latestPalette.size]
            val life = l.durationMs.coerceIn(1800L, 9000L)
            val asWords = mode == Features.LyricScatter.WORDS || (mode == Features.LyricScatter.MIX && words.size <= 3)
            if (asWords) {
                val step = (life * 0.8f / words.size).toLong()
                words.forEachIndexed { i, w ->
                    val sz = (if (big) 24f + rnd.nextFloat() * 22f else 18f + rnd.nextFloat() * 12f) * k
                    placer.place(w, sz, word = true)?.let { (x, y, bw, bh) ->
                        bubbles += Bubble(System.nanoTime() + i, w, x, y, colour(i), sz, true, i * step, life - i * step + 900L).also { it.w = bw; it.h = bh }
                    }
                }
            } else {
                val sz = (if (big) 22f + rnd.nextFloat() * 8f else 16f + rnd.nextFloat() * 5f) * k
                placer.place(text, sz, word = false)?.let { (x, y, bw, bh) ->
                    bubbles += Bubble(System.nanoTime(), text, x, y, colour(0), sz, false, 0L, life + 700L).also { it.w = bw; it.h = bh }
                }
                // "Both": the line's strongest word also lands big somewhere else.
                if (mode == Features.LyricScatter.MIX) {
                    val key2 = words.maxByOrNull { it.trim(',', '.', '!', '?').length }
                    if (key2 != null && key2.length >= 4) {
                        val wsz = (if (big) 38f + rnd.nextFloat() * 12f else 26f + rnd.nextFloat() * 8f) * k
                        placer.place(key2, wsz, word = true)?.let { (x, y, bw, bh) ->
                            bubbles += Bubble(System.nanoTime() + 1, key2, x, y, colour(1), wsz, true, (life * 0.25f).toLong(), (life * 0.75f).toLong() + 900L).also { it.w = bw; it.h = bh }
                        }
                    }
                }
            }
            // Keep it calm: older ones make way when there are too many.
            val limit = if (asWords) 16 else 7
            val alive = bubbles.filter { !it.leaving }
            if (alive.size > limit) alive.take(alive.size - limit).forEach { it.leaving = true }
        }
        // A new song: the old song's words leave.
        LaunchedEffect(playing.title) { bubbles.forEach { it.leaving = true } }

        Box(Modifier.fillMaxSize().onSizeChanged { area = it }) {
            bubbles.forEach { b ->
                androidx.compose.runtime.key(b.id) {
                    LyricBubble(b) { bubbles.remove(b) }
                }
            }
        }
    }
}

@Composable
private fun LyricBubble(b: Bubble, onGone: () -> Unit) {
    val a = remember { Animatable(0f) }
    val gone by rememberUpdatedState(onGone)
    LaunchedEffect(Unit) {
        delay(b.delayMs)
        a.animateTo(1f, tween(if (b.word) 600 else 800, easing = LinearOutSlowInEasing))
        val start = System.currentTimeMillis()
        // Stay while it's sung (or until told to make way).
        while (!b.leaving && System.currentTimeMillis() - start < b.holdMs) delay(120)
        a.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
        gone()
    }
    val shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2f), 14f)
    Text(
        b.text,
        style = uiFontStyle().merge(TextStyle(
            color = b.color, fontSize = b.sizeSp.sp,
            fontWeight = if (b.word) FontWeight.Bold else FontWeight.SemiBold,
            shadow = shadow, textAlign = TextAlign.Center, lineHeight = (b.sizeSp * 1.15f).sp
        )),
        modifier = Modifier
            .layout { m, c ->
                val maxW = (c.maxWidth * 0.6f).toInt().coerceAtLeast(1)
                val p = m.measure(Constraints(maxWidth = maxW))
                layout(c.maxWidth, c.maxHeight) {
                    val x = (b.cx - p.width / 2f).toInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0))
                    val y = (b.cy - p.height / 2f).toInt().coerceIn(0, (c.maxHeight - p.height).coerceAtLeast(0))
                    p.place(x, y)
                }
            }
            .graphicsLayer {
                val v = a.value
                alpha = v
                // Rises softly in and drifts on up as it fades.
                val s = 0.94f + 0.06f * v
                scaleX = s; scaleY = s
                translationY = (1f - v) * 10.dp.toPx()
            }
    )
}

/** The line being sung: its index in the lyrics, its text, when it started and how long it lasts (ms). */
internal data class SungLine(val index: Int, val text: String, val durationMs: Long, val startMs: Long = 0L)

internal fun currentLine(lyrics: com.shelf.launcher.data.Lyrics, positionMs: Long, durationMs: Long): SungLine? {
    if (lyrics.isSynced) {
        val s = lyrics.synced
        val i = s.indexOfLast { it.atMs <= positionMs }
        if (i < 0) return null
        val next = s.getOrNull(i + 1)?.atMs ?: (s[i].atMs + 5000L)
        return SungLine(i, s[i].text, next - s[i].atMs, s[i].atMs)
    }
    val plain = lyrics.plain
    if (plain.isEmpty() || durationMs <= 0) return null
    val start = durationMs * 0.08f
    val end = durationMs * 0.92f
    if (positionMs < start) return null
    val f = ((positionMs - start) / (end - start)).coerceIn(0f, 0.9999f)
    val i = (f * plain.size).toInt().coerceIn(0, plain.lastIndex)
    val per = (end - start) / plain.size
    return SungLine(i, plain[i], per.toLong(), (start + i * per).toLong())
}

/** Picks free spots: away from the bands to avoid and from the words already showing. */
private class Placer(val area: IntSize, val avoid: List<ClosedFloatingPointRange<Float>>, val shown: List<Bubble>, val density: Float) {
    private val rnd = java.util.Random()
    private val taken = ArrayList<FloatArray>().apply { shown.filter { !it.leaving }.forEach { add(floatArrayOf(it.cx, it.cy, it.w, it.h)) } }

    /** Centre and rough size for [text] at [sizeSp], or null when there's no room at all. */
    fun place(text: String, sizeSp: Float, word: Boolean): FloatArray? {
        val W = area.width.toFloat()
        val H = area.height.toFloat()
        val px = sizeSp * density
        val maxW = W * 0.6f
        val oneLine = text.length * px * 0.55f
        val lines = kotlin.math.ceil(oneLine / maxW).coerceAtLeast(1f)
        val w = minOf(oneLine, maxW)
        val h = px * 1.2f * lines
        val pad = 16f * density
        // Free vertical stretches between the bands to avoid (ignored if they leave too little room).
        val usable = freeBands(H, h + pad)
        var best: FloatArray? = null
        var bestScore = -1f
        repeat(14) {
            val band = usable[rnd.nextInt(usable.size)]
            val cy = band.start + h / 2f + rnd.nextFloat() * (band.endInclusive - band.start - h).coerceAtLeast(0f)
            val cx = pad + w / 2f + rnd.nextFloat() * (W - w - 2 * pad).coerceAtLeast(0f)
            // Distance to the nearest word already showing (bigger is better; 0 = overlapping).
            var score = Float.MAX_VALUE
            for (t in taken) {
                val dx = (kotlin.math.abs(cx - t[0]) - (w + t[2]) / 2f).coerceAtLeast(0f)
                val dy = (kotlin.math.abs(cy - t[1]) - (h + t[3]) / 2f).coerceAtLeast(0f)
                score = minOf(score, dx + dy)
            }
            if (score > bestScore) { bestScore = score; best = floatArrayOf(cx, cy, w, h) }
            if (score > 40f * density) return best.also { taken += it!! }
        }
        return best?.also { taken += it }
    }

    private fun freeBands(H: Float, need: Float): List<ClosedFloatingPointRange<Float>> {
        val blocked = avoid.map { it.start.coerceIn(0f, H)..it.endInclusive.coerceIn(0f, H) }.sortedBy { it.start }
        val free = ArrayList<ClosedFloatingPointRange<Float>>()
        var y = 0f
        for (b in blocked) {
            if (b.start - y >= need) free += y..b.start
            y = maxOf(y, b.endInclusive)
        }
        if (H - y >= need) free += y..H
        return free.ifEmpty { listOf(0f..H) }
    }
}

/** Readable colours from the cover: its strongest hues, lifted bright enough to read on the dark stage. */
@Composable
internal fun rememberLyricPalette(art: Bitmap?, colors: ShelfColors): List<Color> {
    val fallback = remember(colors) { listOf(colors.accent, colors.secondary, colors.textPrimary).map { readable(it.toArgbInt()) } }
    val fromArt by produceState<List<Color>?>(null, art) {
        value = if (art == null) null else withContext(Dispatchers.Default) { runCatching { coverHues(art) }.getOrNull() }
    }
    return fromArt?.takeIf { it.size >= 2 }?.plus(fallback.take(1)) ?: fallback
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun readable(argb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceIn(0.25f, 0.75f)
    hsv[2] = maxOf(hsv[2], 0.92f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** The cover's main hues: a 12 × 12 sample, bucketed by hue, the four strongest buckets. */
private fun coverHues(art: Bitmap): List<Color> {
    val n = 12
    val weight = FloatArray(12)
    val sum = Array(12) { FloatArray(3) }
    val hsv = FloatArray(3)
    for (j in 0 until n) for (i in 0 until n) {
        val c = art.getPixel((i + 0.5f) * art.width / n, (j + 0.5f) * art.height / n)
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] < 0.2f || hsv[2] < 0.2f) continue
        val bucket = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        val wgt = hsv[1] * hsv[2]
        weight[bucket] += wgt
        sum[bucket][0] += android.graphics.Color.red(c) * wgt
        sum[bucket][1] += android.graphics.Color.green(c) * wgt
        sum[bucket][2] += android.graphics.Color.blue(c) * wgt
    }
    return (0 until 12).filter { weight[it] > 0f }.sortedByDescending { weight[it] }.take(4).map { b ->
        val w = weight[b]
        readable(android.graphics.Color.rgb((sum[b][0] / w).toInt(), (sum[b][1] / w).toInt(), (sum[b][2] / w).toInt()))
    }
}

private fun Bitmap.getPixel(x: Float, y: Float) = getPixel(x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1))

/** Appearance › Theme, while Lyric cloud is picked. */
@Composable
internal fun LyricCloudSettings() {
    val F = Features
    Column {
        SectionLabel("Lyric cloud")
        MutedNote("The song's lyrics appear around the screen in the cover's colours, fading in and out as they're sung. Lyrics need to be on (Settings › Audio); Big / Small above sets their size.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Features.LyricScatter.values().forEach { m ->
                Chip(m.label, accent = F.lyricScatter.state.value == m) { F.lyricScatter.value = m }
            }
        }
        MutedNote("\"Both\": each line, with its strongest word landing big somewhere else (short lines come word by word).")
    }
}

/**
 * Lyrics layout, shared by Settings › Audio and the music theme card (the same setting in both): left /
 * centre / right and an optional gentle float. Applies at once to every lyric line — the home screen's,
 * the music themes', the full player on the second screen and the screensaver.
 */
@Composable
internal fun LyricsLayoutSettings() {
    val F = Features
    Column {
        Spacer(Modifier.height(6.dp))
        SectionLabel("Lyrics alignment")
        ChipFlow {
            Features.LyricsAlign.values().forEach { a ->
                Chip(a.label, accent = F.lyricsAlign.state.value == a) { F.lyricsAlign.value = a }
            }
        }
        Spacer(Modifier.height(6.dp))
        SectionLabel("Lyrics movement")
        ChipFlow {
            Features.LyricsMotion.values().forEach { m ->
                Chip(m.label, accent = F.lyricsMotion.state.value == m) { F.lyricsMotion.value = m }
            }
        }
        MutedNote("Changes show at once on the home screen, in the music themes, on the second screen and in the screensaver. The float pauses when the home screen is idle or covered.")
    }
}
