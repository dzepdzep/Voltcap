package com.shelf.launcher.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.Album
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.LyricsRepository
import com.shelf.launcher.data.MusicLyricsStyle
import com.shelf.launcher.data.MusicThemeSettings
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfColors
import com.shelf.launcher.ui.theme.ThemeMotif
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The lyric themes (Theme › Music · lyrics) besides Lyric cloud, drawn over their stage (the shader
 * scene behind): Karaoke (the line being sung fills with colour, a ball hops from word to word, the
 * next line under it), Lyric sheet (all the lines scrolling gently, the sung one bright in the middle)
 * and Neon lyrics (each word lights up as a neon tube as it's sung). They keep to the biggest free
 * stretch of the screen between the status bar, the game row, the player and the legend
 * ([LocalSceneAvoid]). Without lyrics (off, not found) the song's title and artist show instead.
 */
@Composable
internal fun LyricStageLayer(motif: ThemeMotif, colors: ShelfColors, modifier: Modifier) {
    // No early return inside Box (inline): this project's Compose compiler mishandles them (see AppIcons.kt).
    Box(modifier) {
        val playing = MusicTheme.nowPlaying
        val style = MusicThemeSettings.lyrics
        if (playing != null && style != MusicLyricsStyle.OFF) LyricStageContent(motif, playing, style, colors)
    }
}

/** What's on show: the line (or the title card), the next one, and the timing for the fill. */
private data class StageLine(
    val key: String, val text: String, val next: String?, val startMs: Long, val durationMs: Long,
    /** False for the title card and the "♪" gaps: nothing to fill. */
    val timed: Boolean
)

@Composable
private fun LyricStageContent(motif: ThemeMotif, playing: NowPlaying, style: MusicLyricsStyle, colors: ShelfColors) {
    val lookup = rememberSongLyrics(playing)
    val lyrics = (lookup as? LyricsRepository.Lookup.Found)?.lyrics
    // Moving the whole time the song plays (never frozen by Lite / a window on top).
    val active = rememberIsActive() && playing.isPlaying
    val now = rememberFrameNow(active)
    val palette = rememberLyricPalette(MusicTheme.softArt, MusicTheme.colors ?: colors)
    val avoid = LocalSceneAvoid.current
    val density = LocalDensity.current
    var area by remember { mutableStateOf(IntSize.Zero) }
    val big = style == MusicLyricsStyle.BIG
    val align = Features.lyricsAlign.state.value
    val showNext = Features.lyricNextLine.state.value

    // Update 119: the line follows the song to the frame (it used to look every half second, so lines changed late),
    // with your timing nudge (Theme › the lyric theme › Timing).
    val offset = Features.lyricsOffsetMs.state.value
    val lineIndex by remember(lyrics, playing, offset) {
        androidx.compose.runtime.derivedStateOf { lyrics?.let { currentLine(it, playing.currentPosition(now.value) + offset, playing.durationMs) }?.index }
    }
    val line = remember(lyrics, lineIndex, playing.durationMs) { lineIndex?.let { i -> lyrics?.let { lineAt(it, i, playing.durationMs) } } }
    val allLines = remember(lyrics) {
        lyrics?.let { l -> if (l.isSynced) l.synced.map { it.text } else l.plain } ?: emptyList()
    }
    val title = playing.title.orEmpty()
    val artist = playing.artist?.takeIf { it.isNotBlank() }
    val stage: StageLine = remember(line, allLines, title, artist, lookup) {
        val text = line?.text?.trim().orEmpty()
        when {
            line == null || allLines.isEmpty() ->
                // Before the first line, or no lyrics at all: the title card.
                StageLine("title|$title", title, artist ?: noteFor(lookup), 0L, 0L, timed = false)
            text.isEmpty() || text == "♪" ->
                StageLine("gap|$title|${line.index}", "♪", allLines.getOrNull(line.index + 1)?.trim()?.takeIf { it.isNotEmpty() }, 0L, 0L, timed = false)
            else -> StageLine(
                "line|$title|${line.index}", text, allLines.getOrNull(line.index + 1)?.trim()?.takeIf { it.isNotEmpty() && it != "♪" },
                line.startMs, line.durationMs,
                // (Update 119: only lyrics with real timings fill as they're sung; plain ones just show the line.)
                timed = lyrics?.isSynced == true
            )
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { area = it }) {
        if (area.height > 0) {
            val band = remember(area, avoid) { largestFreeBand(area.height.toFloat(), avoid) }
            val bandH = with(density) { (band.endInclusive - band.start).toDp() }
            val compact = bandH < 230.dp
            val boxAlign = when (align) {
                Features.LyricsAlign.START -> Alignment.CenterStart
                Features.LyricsAlign.CENTER -> Alignment.Center
                Features.LyricsAlign.END -> Alignment.CenterEnd
            }
            Box(
                Modifier.fillMaxWidth().offset { IntOffset(0, band.start.roundToInt()) }.height(bandH).padding(horizontal = 28.dp, vertical = 8.dp),
                contentAlignment = boxAlign
            ) {
                when (motif) {
                    ThemeMotif.LYRIC_SHEET ->
                        if (allLines.isEmpty()) KaraokeLines(stage, playing, palette, big, compact, align, false, now)
                        else LyricSheet(allLines, line?.index ?: -1, big, compact, align)
                    ThemeMotif.LYRIC_NEON -> NeonLines(stage, playing, palette, big, compact, align, showNext, now)
                    else -> KaraokeLines(stage, playing, palette, big, compact, align, showNext, now)
                }
            }
        }
    }
}

/** Line [i] of [lyrics] with its timing (as [currentLine] gives it). */
private fun lineAt(lyrics: com.shelf.launcher.data.Lyrics, i: Int, durationMs: Long): SungLine? {
    if (lyrics.isSynced) {
        val l = lyrics.synced.getOrNull(i) ?: return null
        val next = lyrics.synced.getOrNull(i + 1)?.atMs ?: (l.atMs + 5000L)
        return SungLine(i, l.text, next - l.atMs, l.atMs)
    }
    val plain = lyrics.plain
    if (plain.isEmpty() || durationMs <= 0) return null
    val start = durationMs * 0.08f
    val per = (durationMs * 0.92f - start) / plain.size
    return plain.getOrNull(i)?.let { SungLine(i, it, per.toLong(), (start + i * per).toLong()) }
}

private fun noteFor(lookup: LyricsRepository.Lookup?): String? = when (lookup) {
    is LyricsRepository.Lookup.Off -> "Lyrics are off · Settings › Audio"
    is LyricsRepository.Lookup.NotFound -> "No lyrics for this song"
    else -> null
}

/** The biggest stretch of [h] px not covered by the [avoid] bands (all of it when nothing is in the way). */
internal fun largestFreeBand(h: Float, avoid: List<ClosedFloatingPointRange<Float>>): ClosedFloatingPointRange<Float> {
    val blocked = avoid.map { it.start.coerceIn(0f, h)..it.endInclusive.coerceIn(0f, h) }.sortedBy { it.start }
    var best = 0f..0f
    var y = 0f
    for (b in blocked) {
        if (b.start - y > best.endInclusive - best.start) best = y..b.start
        y = maxOf(y, b.endInclusive)
    }
    if (h - y > best.endInclusive - best.start) best = y..h
    // Hardly any room left between the bands: use the whole height rather than a sliver.
    return if (best.endInclusive - best.start < h * 0.12f) 0f..h else best
}

/** A clock for the fills, ~60 times a second while [running] (read only while drawing: no recomposition; update 126, was ~30). */
@Composable
private fun rememberFrameNow(running: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        now.longValue = SystemClock.elapsedRealtime()
        if (running) while (true) {
            withFrameMillis { }
            now.longValue = SystemClock.elapsedRealtime()
            delay(12)
        }
    }
    return now
}

/**
 * How far through the line the singing is (0..1). Update 119: from how long the line takes to sing (about 13
 * letters a second), not from the time until the next line — a line followed by a pause used to fill far too slowly
 * and lag behind the singer. Letters count, spaces and punctuation don't.
 */
private fun sungFraction(s: StageLine, playing: NowPlaying, now: Long): Float {
    if (!s.timed) return 1f
    val pos = playing.currentPosition(now) + Features.lyricsOffsetMs.value
    val letters = s.text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    val span = (letters * 78f + 350f).coerceAtMost(s.durationMs * 0.94f).coerceIn(400f, 9000f)
    return ((pos - s.startMs) / span).coerceIn(0f, 1f)
}

private fun textAlignOf(a: Features.LyricsAlign) = when (a) {
    Features.LyricsAlign.START -> TextAlign.Start
    Features.LyricsAlign.CENTER -> TextAlign.Center
    Features.LyricsAlign.END -> TextAlign.End
}

private fun columnAlignOf(a: Features.LyricsAlign) = when (a) {
    Features.LyricsAlign.START -> Alignment.Start
    Features.LyricsAlign.CENTER -> Alignment.CenterHorizontally
    Features.LyricsAlign.END -> Alignment.End
}

// ---------------------------------------------------------------- Karaoke

@Composable
private fun KaraokeLines(
    stage: StageLine, playing: NowPlaying, palette: List<Color>, big: Boolean, compact: Boolean,
    align: Features.LyricsAlign, showNext: Boolean, now: State<Long>
) {
    val size = (if (big) 40f else 29f) * (if (compact) 0.72f else 1f)
    val fill = palette.firstOrNull() ?: Color(0xFF5CC8FF)
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            // No clipping: the ball hops above the line and the outline reaches past it.
            ((fadeIn(tween(380)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                (fadeOut(tween(300)) + slideOutVertically(tween(380)) { -it / 3 })) using SizeTransform(clip = false)
        },
        contentKey = { it.key },
        label = "karaoke"
    ) { s ->
        Column(horizontalAlignment = columnAlignOf(align)) {
            SungText(s, playing, size, fill, align, now)
            if (showNext && s.next != null) {
                Spacer(Modifier.height((size * 0.35f).dp))
                Text(
                    s.next,
                    style = uiFontStyle().merge(TextStyle(
                        color = Color.White.copy(alpha = 0.55f), fontSize = (size * 0.58f).sp, fontWeight = FontWeight.SemiBold,
                        textAlign = textAlignOf(align), lineHeight = (size * 0.72f).sp,
                        shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f)
                    )),
                    maxLines = 2
                )
            }
        }
    }
}

/** The line being sung: white with a dark outline, filling with [fill] from the first letter as it's sung; the bouncing ball over it. */
@Composable
private fun SungText(s: StageLine, playing: NowPlaying, size: Float, fill: Color, align: Features.LyricsAlign, now: State<Long>) {
    val density = LocalDensity.current.density
    var layout by remember(s.key) { mutableStateOf<TextLayoutResult?>(null) }
    val ball = Features.karaokeBall.state.value
    val base = uiFontStyle().merge(TextStyle(
        fontSize = size.sp, fontWeight = FontWeight.ExtraBold, textAlign = textAlignOf(align), lineHeight = (size * 1.18f).sp
    ))
    val text = s.text
    // Word centres (for the ball): start and end offsets of each word.
    val words = remember(text) {
        Regex("\\S+").findAll(text).map { it.range.first to it.range.last + 1 }.toList()
    }
    // Room above the line for the ball's hop.
    Box(Modifier.padding(top = if (ball && s.timed) (size * 0.75f).dp else 0.dp)) {
        Text(text, maxLines = 3, style = base.merge(TextStyle(
            color = Color.Black.copy(alpha = 0.8f), drawStyle = Stroke(width = size * 0.2f * density, join = StrokeJoin.Round)
        )))
        Text(text, maxLines = 3, style = base.merge(TextStyle(color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 14f))))
        if (s.timed) Text(
            text, maxLines = 3, style = base.merge(TextStyle(color = fill)),
            onTextLayout = { layout = it },
            modifier = Modifier.drawWithContent {
                val l = layout
                if (l != null && l.layoutInput.text.text == text) {
                    val f = sungFraction(s, playing, now.value)
                    val sung = f * text.length
                    val n = sung.toInt().coerceIn(0, text.length)
                    fun xAt(o: Int) = l.getHorizontalPosition(o.coerceIn(0, text.length), usePrimaryDirection = true)
                    for (li in 0 until l.lineCount) {
                        val ls = l.getLineStart(li)
                        val le = l.getLineEnd(li, visibleEnd = true)
                        if (n <= ls && sung <= ls) continue
                        val right = if (n >= le) l.getLineRight(li) + 4f
                            else xAt(n) + (xAt(n + 1) - xAt(n)) * (sung - n)
                        clipRect(left = l.getLineLeft(li) - 6f, top = l.getLineTop(li), right = right, bottom = l.getLineBottom(li)) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    if (ball && words.isNotEmpty() && f < 1f) {
                        // Which word the singing is on, and how far through it: the ball hops from one word's middle to the next.
                        val wi = words.indexOfLast { it.first <= sung }.coerceAtLeast(0)
                        val (ws, we) = words[wi]
                        val phase = ((sung - ws) / (we - ws).coerceAtLeast(1)).coerceIn(0f, 1f)
                        fun centre(i: Int): Offset {
                            val (a, b) = words[i.coerceIn(0, words.lastIndex)]
                            val li = l.getLineForOffset(a)
                            return Offset((xAt(a) + xAt(b)) / 2f, l.getLineTop(li))
                        }
                        val from = centre(wi)
                        val to = if (wi < words.lastIndex) centre(wi + 1) else from
                        val sameLine = abs(from.y - to.y) < 1f
                        val x = if (sameLine) from.x + (to.x - from.x) * phase else from.x
                        val r = size * 0.13f * density
                        val hop = sin(phase * PI.toFloat()) * size * 0.42f * density
                        val y = from.y - r * 1.2f - hop
                        drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.15f, Offset(x + r * 0.15f, y + r * 0.25f))
                        drawCircle(fill, r, Offset(x, y))
                        drawCircle(Color.White.copy(alpha = 0.75f), r * 0.38f, Offset(x - r * 0.3f, y - r * 0.3f))
                    }
                }
            }
        )
    }
}

// ---------------------------------------------------------------- Lyric sheet

/**
 * Every line of the song in a column; the one being sung sits in the middle, bright and full size,
 * the others smaller and dimmer the further they are; the column glides up line by line. The top and
 * bottom fade out.
 */
@Composable
private fun LyricSheet(lines: List<String>, index: Int, big: Boolean, compact: Boolean, align: Features.LyricsAlign) {
    val size = (if (big) 30f else 23f) * (if (compact) 0.76f else 1f)
    val at by animateFloatAsState(index.toFloat().coerceAtLeast(-1f), tween(650, easing = FastOutSlowInEasing), label = "sheet")
    val first = (index - 6).coerceAtLeast(0)
    val last = (index + 9).coerceAtMost(lines.lastIndex)
    val style = uiFontStyle().merge(TextStyle(
        fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = textAlignOf(align), lineHeight = (size * 1.2f).sp,
        shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 12f)
    ))
    val originX = when (align) {
        Features.LyricsAlign.START -> 0f
        Features.LyricsAlign.CENTER -> 0.5f
        Features.LyricsAlign.END -> 1f
    }
    val gap = with(LocalDensity.current) { (size * 0.45f).dp.roundToPx() }
    Layout(
        content = {
            for (j in first..last) key(j) {
                val t = lines[j].trim().ifEmpty { "♪" }
                Text(
                    t, style = style.merge(TextStyle(color = if (j == index) Color.White else Color.White.copy(alpha = 0.62f))),
                    modifier = Modifier.graphicsLayer {
                        val d = abs(j - at)
                        val s = 1f - 0.18f * d.coerceAtMost(1f)
                        scaleX = s; scaleY = s
                        alpha = (1f - 0.22f * (d - 1f).coerceAtLeast(0f)).coerceIn(0.15f, 1f)
                        transformOrigin = TransformOrigin(originX, 0.5f)
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize().clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn
                )
            }
    ) { measurables, c ->
        val ps = measurables.map { it.measure(Constraints(maxWidth = c.maxWidth)) }
        val tops = IntArray(ps.size)
        var y = 0
        ps.forEachIndexed { i, p -> tops[i] = y; y += p.height + gap }
        fun centreOf(j: Int): Float {
            val i = (j - first).coerceIn(0, (ps.size - 1).coerceAtLeast(0))
            return if (ps.isEmpty()) 0f else tops[i] + ps[i].height / 2f
        }
        layout(c.maxWidth, c.maxHeight) {
            // The point between two lines the column is gliding through.
            val a = at.coerceAtLeast(first.toFloat() - 1f)
            val lo = floor(a).toInt()
            val anchor = if (lo < first) centreOf(first) - (ps.firstOrNull()?.height ?: 0) * (first - a)
                else centreOf(lo) + (centreOf(lo + 1) - centreOf(lo)) * (a - lo)
            val shift = c.maxHeight / 2f - anchor
            ps.forEachIndexed { i, p ->
                val x = ((c.maxWidth - p.width) * originX).roundToInt()
                p.placeRelative(x, (tops[i] + shift).roundToInt())
            }
        }
    }
}

// ---------------------------------------------------------------- Neon lyrics

@Composable
private fun NeonLines(
    stage: StageLine, playing: NowPlaying, palette: List<Color>, big: Boolean, compact: Boolean,
    align: Features.LyricsAlign, showNext: Boolean, now: State<Long>
) {
    val size = (if (big) 44f else 32f) * (if (compact) 0.7f else 1f)
    AnimatedContent(
        targetState = stage,
        // No clipping: the glow spreads well past the letters.
        transitionSpec = { (fadeIn(tween(260)) togetherWith fadeOut(tween(420))) using SizeTransform(clip = false) },
        contentKey = { it.key },
        label = "neon"
    ) { s ->
        // Each line takes the next colour of the cover's, so the sign changes colour as the song goes on.
        val colour = if (palette.isEmpty()) Color(0xFFFF4FA3) else palette[Math.floorMod(s.key.hashCode(), palette.size)]
        Column(horizontalAlignment = columnAlignOf(align)) {
            NeonSign(s, playing, size, colour, align, now)
            if (showNext && s.next != null) {
                Spacer(Modifier.height((size * 0.3f).dp))
                // The next line: tubes still off.
                Text(
                    s.next,
                    style = uiFontStyle().merge(TextStyle(
                        color = colour.copy(alpha = 0.28f), fontSize = (size * 0.5f).sp, fontWeight = FontWeight.Medium,
                        textAlign = textAlignOf(align), lineHeight = (size * 0.62f).sp
                    )),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun NeonSign(s: StageLine, playing: NowPlaying, size: Float, colour: Color, align: Features.LyricsAlign, now: State<Long>) {
    val density = LocalDensity.current.density
    val text = s.text
    val words = remember(text) { Regex("\\S+").findAll(text).map { it.range }.toList() }
    val latest by androidx.compose.runtime.rememberUpdatedState(playing)
    // How many words are lit: changes a few times a line (only then does this recompose).
    val lit by remember(s.key) {
        derivedStateOf {
            if (!s.timed || words.isEmpty()) words.size
            else (floor(sungFraction(s, latest, now.value) * words.size).toInt() + 1).coerceIn(1, words.size)
        }
    }
    val core = lerp(colour, Color.White, 0.7f)
    fun layer(litColour: Color, offColour: Color, blur: Float, shadowColour: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        words.forEachIndexed { i, r ->
            val on = i < lit
            addStyle(
                SpanStyle(
                    color = if (on) litColour else offColour,
                    shadow = if (on && blur > 0f) Shadow(shadowColour, Offset.Zero, blur) else null
                ),
                r.first, r.last + 1
            )
        }
    }
    val base = TextStyle(
        fontFamily = FontFamily.Cursive, fontSize = size.sp, fontWeight = FontWeight.Bold,
        textAlign = textAlignOf(align), lineHeight = (size * 1.2f).sp
    )
    val sizePx = size * density
    val glowWide = remember(text, lit, colour) { layer(colour.copy(alpha = 0.9f), Color.Transparent, sizePx * 0.9f, colour) }
    val glowNear = remember(text, lit, colour) { layer(colour, Color.Transparent, sizePx * 0.3f, colour) }
    val tubes = remember(text, lit, colour) { layer(core, colour.copy(alpha = 0.16f), sizePx * 0.07f, Color.White.copy(alpha = 0.8f)) }
    Box {
        // The glow buzzes a little (the tubes themselves stay steady).
        val buzz = Modifier.graphicsLayer {
            val t = now.value / 1000.0
            val n = sin(t * 23.0) * sin(t * 7.3 + 1.0)
            alpha = (0.86f + 0.14f * n.toFloat()).coerceIn(0.7f, 1f)
        }
        Text(glowWide, style = base, maxLines = 3, modifier = buzz)
        Text(glowNear, style = base, maxLines = 3, modifier = buzz)
        Text(tubes, style = base, maxLines = 3)
    }
}

/** Appearance › Theme, while Karaoke, Lyric sheet or Neon lyrics is picked. */
@Composable
internal fun LyricThemeSettings(theme: com.shelf.launcher.ui.theme.AppTheme) {
    val F = Features
    Column {
        SectionLabel(theme.label)
        MutedNote(
            when (theme.motif) {
                ThemeMotif.LYRIC_KARAOKE -> "The line being sung fills with colour as it's sung, like a karaoke video."
                ThemeMotif.LYRIC_SHEET -> "All the song's lines scroll gently, the one being sung bright in the middle."
                else -> "Each word lights up as a neon tube as it's sung."
            } + " Lyrics need to be on (Settings › Audio); Big / Small above sets their size, and Lyrics alignment where they sit."
        )
        if (theme.motif != ThemeMotif.LYRIC_SHEET) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                icon = androidx.compose.material.icons.Icons.Filled.ViewHeadline,
                title = "Show the next line",
                subtitle = "Dimmer, under the line being sung.",
                checked = F.lyricNextLine.state.value,
                onChange = { F.lyricNextLine.value = it }
            )
        }
        if (theme.motif == ThemeMotif.LYRIC_KARAOKE) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                icon = androidx.compose.material.icons.Icons.Filled.SportsBaseball,
                title = "Bouncing ball",
                subtitle = "Hops from word to word over the line being sung.",
                checked = F.karaokeBall.state.value,
                onChange = { F.karaokeBall.value = it }
            )
        }
        // Update 119: the song's cover behind the words, and the timing.
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            icon = androidx.compose.material.icons.Icons.Filled.Album,
            title = "Album cover behind the words",
            subtitle = "The song's cover fills the screen, softened and dimmed. Off: the theme's own music scene.",
            checked = F.lyricCoverBg.state.value,
            onChange = { F.lyricCoverBg.value = it }
        )
        Spacer(Modifier.height(8.dp))
        SectionLabel("Timing (all lyric themes)")
        ChipFlow {
            listOf(-1000, -500, -250, 0, 250, 500, 1000).forEach { v ->
                val label = if (v == 0) "On time" else (if (v > 0) "+" else "−") + (if (kotlin.math.abs(v) >= 1000) "1 s" else "${kotlin.math.abs(v)} ms")
                Chip(label, accent = F.lyricsOffsetMs.state.value == v) { F.lyricsOffsetMs.value = v }
            }
        }
        MutedNote("If the words come too late, pick +; too early, −. Lyrics with timings (most songs) follow the singing; ones without just show the line.")
    }
}
