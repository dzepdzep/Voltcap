package com.shelf.launcher.ui

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.LyricsOptIn
import com.shelf.launcher.data.LyricsRepository
import com.shelf.launcher.data.MusicLyricsStyle
import com.shelf.launcher.data.NowPlaying
import com.shelf.launcher.ui.theme.ShelfColors
import kotlinx.coroutines.delay

/**
 * Where the "Now playing" panel and the equalizer wave go: the free band of screen above or below the
 * game row, whatever the row's position (Appearance › Game row: top, middle, bottom).
 *
 * Row bounds come from the home screen (the row's measured top / bottom, in px from the top); until
 * they're known the dock position alone decides. [top] / [bottom] are the free band's edges in px.
 */
internal class MusicSlot(val top: Float, val bottom: Float) {
    val height get() = (bottom - top).coerceAtLeast(0f)
    val centre get() = (top + bottom) / 2f

    companion object {
        fun pick(
            screenH: Float,
            dock: DockPosition,
            rowTop: Float,
            rowBottom: Float,
            /** Kept free at the top (status bar) and bottom (legend, status bar when the row is at the top). */
            reserveTop: Float,
            reserveBottom: Float,
            gap: Float
        ): MusicSlot {
            val known = rowBottom > rowTop && rowBottom > 0f
            val above = MusicSlot(reserveTop, (if (known) rowTop else screenH * 0.55f) - gap)
            val below = MusicSlot((if (known) rowBottom else screenH * 0.45f) + gap, screenH - reserveBottom)
            return when (dock) {
                DockPosition.BOTTOM -> above
                DockPosition.TOP -> below
                DockPosition.MIDDLE -> if (above.height >= below.height) above else below
            }
        }
    }
}

/**
 * Where the "Now playing" pieces go, in px from the top: the equalizer wave's centre line, the player
 * right under the wave (always — same controls wherever the wave is), and the lyric line when it
 * isn't in the player. [spot] picks the wave's place (Appearance › Theme › Equalizer): beside the
 * game row (the free band, as before), or the top, middle or bottom of the screen.
 */
internal class MusicLayout(val waveY: Float, val panelTop: Float, val panelBottom: Float, val lyricY: Float, val waveHalf: Float = 0f) {
    companion object {
        fun compute(
            screenH: Float,
            slot: MusicSlot,
            spot: com.shelf.launcher.data.MusicSpot,
            lyricsSpot: com.shelf.launcher.data.MusicLyricsSpot,
            reserveTop: Float,
            reserveBottom: Float,
            /** Half the room kept for the wave (it swells up and down from its centre line). */
            waveHalf: Float,
            minWaveHalf: Float,
            panelH: Float,
            lyricH: Float,
            gap: Float,
            /** The game row (px; 0 / 0 = not known): "Top" / "Bottom" lyrics go beside it, never under it. */
            rowTop: Float = 0f,
            rowBottom: Float = 0f
        ): MusicLayout {
            val lyricTop = lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.TOP
            val lyricBottom = lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.BOTTOM
            val edges = lyricEdges(screenH, reserveTop, reserveBottom, lyricH, gap, rowTop, rowBottom)
            // Lyrics at the top / bottom of the screen own that strip: the wave and the player keep out of
            // it (they used to be laid out as if it was free, so on short screens the lyric sat on top
            // of the player's buttons).
            val freeTop = if (lyricTop) edges.first + lyricH + gap else reserveTop
            val freeBottom = if (lyricBottom) edges.second - lyricH - gap else screenH - reserveBottom
            val band = MusicSlot(maxOf(slot.top, freeTop), minOf(slot.bottom, freeBottom))
            // Beside the row, wave + player share the free band: the wave gets less room when the band
            // is low, the player always keeps its full size.
            val roomFor = if (spot == com.shelf.launcher.data.MusicSpot.AUTO) band.height else (freeBottom - freeTop)
            // Lyrics above / below the visualizer get their own strip inside the group, so they never
            // sit on the animation (and the group moves as one).
            val above = lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.ABOVE_WAVE
            val below = lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.BELOW_WAVE
            val lyricStrip = if (above || below) lyricH + gap else 0f
            val half = ((roomFor - gap - panelH - lyricStrip) / 2f).coerceIn(minWaveHalf, maxOf(minWaveHalf, waveHalf))
            val group = half * 2f + gap + panelH + lyricStrip
            val top: Float = when (spot) {
                com.shelf.launcher.data.MusicSpot.AUTO ->
                    (band.centre - group / 2f).coerceIn(band.top, maxOf(band.top, band.bottom - group))
                com.shelf.launcher.data.MusicSpot.TOP -> freeTop
                com.shelf.launcher.data.MusicSpot.MIDDLE -> screenH / 2f - group / 2f
                com.shelf.launcher.data.MusicSpot.BOTTOM -> freeBottom - group
            }
            val t = top.coerceIn(freeTop, maxOf(freeTop, freeBottom - group))
            val waveY = t + (if (above) lyricStrip else 0f) + half
            val panelTop = waveY + half + gap + (if (below) lyricStrip else 0f)
            val panelBottom = (if (spot == com.shelf.launcher.data.MusicSpot.AUTO) maxOf(band.bottom, panelTop + panelH)
                else freeBottom).coerceAtLeast(panelTop + panelH).coerceAtMost(maxOf(freeBottom, panelTop + panelH))
            val lyricY = when (lyricsSpot) {
                com.shelf.launcher.data.MusicLyricsSpot.TOP -> edges.first + lyricH / 2f
                com.shelf.launcher.data.MusicLyricsSpot.BOTTOM -> edges.second - lyricH / 2f
                com.shelf.launcher.data.MusicLyricsSpot.ABOVE_WAVE -> waveY - half - gap - lyricH / 2f
                com.shelf.launcher.data.MusicLyricsSpot.BELOW_WAVE -> waveY + half + gap + lyricH / 2f
                else -> waveY
            }
            return MusicLayout(waveY, panelTop, panelBottom, lyricY, half)
        }
    }
}

/**
 * Where "Top" / "Bottom" lyrics may go (px): under the status bar / above the legend, or — when the
 * game row sits right there — just below / above the row instead of hidden underneath it. (They used
 * to be placed at the screen's edge whatever was there, so with the row at the top or bottom, moving
 * the lyrics there seemed to do nothing.)
 */
internal fun lyricEdges(
    screenH: Float, reserveTop: Float, reserveBottom: Float, lyricH: Float, gap: Float, rowTop: Float, rowBottom: Float
): Pair<Float, Float> {
    val known = rowBottom > rowTop && rowBottom > 0f
    var top = reserveTop
    var bottom = screenH - reserveBottom
    if (known && rowTop < reserveTop + lyricH + gap) top = maxOf(top, rowBottom + gap)
    if (known && rowBottom > bottom - lyricH - gap) bottom = minOf(bottom, rowTop - gap)
    // Never upside down on a very short screen.
    if (bottom - top < lyricH) { top = reserveTop; bottom = screenH - reserveBottom }
    return top to bottom
}

/**
 * How much room (share of the screen height, above + below its centre line) each music-reactive
 * scene's visualizer needs, so the player under it never sits on top of it. The scene is told the
 * room it really got (uWaveH) and sizes its wave / bars / ring to fit.
 */
internal fun visualizerHalf(motif: com.shelf.launcher.ui.theme.ThemeMotif): Float =
    if (motif.reactiveHalf > 0f) motif.reactiveHalf else 0.1f

/** Places [content] horizontally centred with its top at [topPx], no taller than down to [bottomPx]. */
@Composable
internal fun PlaceCentredBelow(topPx: Float, bottomPx: Float, sidePadding: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val side = sidePadding.roundToPx()
        val c = Constraints(
            minWidth = 0, maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0),
            minHeight = 0, maxHeight = (bottomPx - topPx).toInt().coerceAtLeast(0)
        )
        val placeables = measurables.map { it.measure(c) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { p -> p.place((constraints.maxWidth - p.width) / 2, topPx.toInt()) }
        }
    }
}

/** Places [content] centred on the line [centreYPx] (horizontally on [centreXPx], or the middle). */
@Composable
internal fun PlaceCentredOn(
    centreYPx: Float,
    sidePadding: androidx.compose.ui.unit.Dp,
    centreXPx: Float? = null,
    maxWidthPx: Float? = null,
    /** Left / centre / right inside the room (the lyrics layout setting). */
    align: com.shelf.launcher.data.Features.LyricsAlign = com.shelf.launcher.data.Features.LyricsAlign.CENTER,
    content: @Composable () -> Unit
) {
    Layout(content = content) { measurables, constraints ->
        val side = sidePadding.roundToPx()
        val maxW = minOf((constraints.maxWidth - 2 * side).coerceAtLeast(0), maxWidthPx?.toInt() ?: Int.MAX_VALUE)
        val c = Constraints(minWidth = 0, maxWidth = maxW, minHeight = 0, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(c) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { p ->
                val y = (centreYPx - p.height / 2f).toInt().coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0))
                // The room: the whole width (less the side padding), or maxW around centreXPx.
                val roomL = if (centreXPx == null) side.toFloat() else centreXPx - maxW / 2f
                val roomR = if (centreXPx == null) (constraints.maxWidth - side).toFloat() else centreXPx + maxW / 2f
                val x = when (align) {
                    com.shelf.launcher.data.Features.LyricsAlign.START -> roomL
                    com.shelf.launcher.data.Features.LyricsAlign.END -> roomR - p.width
                    com.shelf.launcher.data.Features.LyricsAlign.CENTER -> (roomL + roomR - p.width) / 2f
                }.toInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                p.place(x, y)
            }
        }
    }
}

/**
 * A lone lyric line on the home screen (Settings › Music & sound › Where the lyrics show): its top at [topPx]
 * or its bottom at [bottomPx], left / centre / right per the lyrics layout, drawn above the game row.
 */
@Composable
internal fun PlaceLyricLine(
    topPx: Float?, bottomPx: Float?, sidePadding: androidx.compose.ui.unit.Dp,
    align: com.shelf.launcher.data.Features.LyricsAlign,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val side = sidePadding.roundToPx()
        val c = Constraints(minWidth = 0, maxWidth = ((constraints.maxWidth - 2 * side) * 0.8f).toInt().coerceAtLeast(0), minHeight = 0, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(c) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { p ->
                val y = when {
                    topPx != null -> topPx
                    bottomPx != null -> bottomPx - p.height
                    else -> 0f
                }.toInt().coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0))
                val x = when (align) {
                    com.shelf.launcher.data.Features.LyricsAlign.START -> side
                    com.shelf.launcher.data.Features.LyricsAlign.END -> constraints.maxWidth - side - p.width
                    com.shelf.launcher.data.Features.LyricsAlign.CENTER -> (constraints.maxWidth - p.width) / 2
                }.coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                p.place(x, y)
            }
        }
    }
}

internal fun com.shelf.launcher.data.Features.LyricsAlign.horizontal(): Alignment.Horizontal = when (this) {
    com.shelf.launcher.data.Features.LyricsAlign.START -> Alignment.Start
    com.shelf.launcher.data.Features.LyricsAlign.CENTER -> Alignment.CenterHorizontally
    com.shelf.launcher.data.Features.LyricsAlign.END -> Alignment.End
}

internal fun com.shelf.launcher.data.Features.LyricsAlign.text(): androidx.compose.ui.text.style.TextAlign = when (this) {
    com.shelf.launcher.data.Features.LyricsAlign.START -> androidx.compose.ui.text.style.TextAlign.Start
    com.shelf.launcher.data.Features.LyricsAlign.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
    com.shelf.launcher.data.Features.LyricsAlign.END -> androidx.compose.ui.text.style.TextAlign.End
}

/**
 * "Gentle float" (the lyrics layout setting): the lyric bobs a few dp up and down and sideways, slowly.
 * Only the layer moves (no relayout); it holds still when the home screen is covered, off screen,
 * long idle or with Lite effects.
 */
@Composable
internal fun Modifier.lyricFloat(): Modifier {
    if (com.shelf.launcher.data.Features.lyricsMotion.state.value != com.shelf.launcher.data.Features.LyricsMotion.FLOAT) return this
    val active = rememberIsActive() && (MusicTheme.nowPlaying?.isPlaying == true || (!LocalLiteEffects.current && !HomeIdle.still))
    if (!active) return this
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "lyricFloat")
    val p by t.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(tween(7000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "lyricFloatP"
    )
    return this.graphicsLayer {
        translationY = kotlin.math.sin(p) * 4.dp.toPx()
        translationX = kotlin.math.sin(p * 0.5f + 1f) * 3.dp.toPx()
    }
}

/** Places [content] centred inside the box [leftPx]..[rightPx] × [topPx]..[bottomPx], never bigger than it. */
@Composable
internal fun PlaceInBox(leftPx: Float, topPx: Float, rightPx: Float, bottomPx: Float, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val w = (rightPx - leftPx).toInt().coerceAtLeast(0)
        val h = (bottomPx - topPx).toInt().coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints(0, w, 0, h)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { p ->
                p.place((leftPx + (w - p.width) / 2f).toInt(), (topPx + (h - p.height) / 2f).toInt())
            }
        }
    }
}

/**
 * Side-by-side "Now playing": the visualizer centred in the left part with the whole free height, the
 * player (and lyrics placed near the visualizer) in a column on the right. All in px.
 */
internal class MusicSideLayout(
    val waveX: Float, val waveY: Float, val waveHalf: Float,
    val panelLeft: Float, val panelTop: Float, val panelRight: Float, val panelBottom: Float,
    val lyricX: Float?, val lyricY: Float, val lyricMaxW: Float?
) {
    companion object {
        fun compute(
            screenW: Float, screenH: Float, slot: MusicSlot,
            spot: com.shelf.launcher.data.MusicSpot, lyricsSpot: com.shelf.launcher.data.MusicLyricsSpot,
            reserveTop: Float, reserveBottom: Float, wantHalf: Float, lyricH: Float, pad: Float, gap: Float,
            rowTop: Float = 0f, rowBottom: Float = 0f
        ): MusicSideLayout {
            val lyricTop = lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.TOP
            val lyricBottom = lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.BOTTOM
            val edges = lyricEdges(screenH, reserveTop, reserveBottom, lyricH, gap, rowTop, rowBottom)
            val freeTop = if (lyricTop) edges.first + lyricH + gap else reserveTop
            val freeBottom = if (lyricBottom) edges.second - lyricH - gap else screenH - reserveBottom
            val band = if (spot == com.shelf.launcher.data.MusicSpot.AUTO) MusicSlot(maxOf(slot.top, freeTop), minOf(slot.bottom, freeBottom))
                else MusicSlot(freeTop, freeBottom)
            // The visualizer: the whole band height (a touch of air), and no wider than its column allows.
            val leftW = screenW * 0.5f
            val half = minOf(wantHalf, band.height / 2f - gap, leftW * 0.36f).coerceAtLeast(16f)
            val waveY = when (spot) {
                com.shelf.launcher.data.MusicSpot.TOP -> band.top + half + gap
                com.shelf.launcher.data.MusicSpot.BOTTOM -> band.bottom - half - gap
                else -> band.centre
            }
            val waveXPx = leftW * 0.56f
            // The player's column: from the middle to the right edge, as tall as the band.
            val pl = screenW * 0.5f + pad * 0.5f
            val pr = screenW - pad
            // Lyrics on / above / below the visualizer stay with it; top / bottom of the screen as usual.
            val lyricX: Float?
            val lyricY: Float
            val lyricMaxW: Float?
            when (lyricsSpot) {
                com.shelf.launcher.data.MusicLyricsSpot.TOP -> { lyricX = null; lyricMaxW = null; lyricY = edges.first + lyricH / 2f }
                com.shelf.launcher.data.MusicLyricsSpot.BOTTOM -> { lyricX = null; lyricMaxW = null; lyricY = edges.second - lyricH / 2f }
                com.shelf.launcher.data.MusicLyricsSpot.ABOVE_WAVE, com.shelf.launcher.data.MusicLyricsSpot.BELOW_WAVE -> {
                    // Under the player in its column (the visualizer has no room above / below it here).
                    lyricX = (pl + pr) / 2f; lyricMaxW = pr - pl; lyricY = (band.bottom - lyricH / 2f).coerceAtLeast(waveY)
                }
                else -> { lyricX = waveXPx; lyricMaxW = leftW - pad; lyricY = waveY }
            }
            val panelBottom = if (lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.ABOVE_WAVE ||
                lyricsSpot == com.shelf.launcher.data.MusicLyricsSpot.BELOW_WAVE) band.bottom - lyricH - gap else band.bottom
            return MusicSideLayout(
                waveX = waveXPx / screenW, waveY = waveY, waveHalf = half,
                panelLeft = pl, panelTop = band.top, panelRight = pr, panelBottom = panelBottom,
                lyricX = lyricX, lyricY = lyricY, lyricMaxW = lyricMaxW
            )
        }
    }
}

/**
 * The lyric line on its own (Appearance › Theme › Lyrics: on the wave, top or bottom): centred, a
 * little larger than in the player, with a soft shadow so it reads over the moving background.
 */
@Composable
internal fun NowPlayingLyricLine(
    playing: NowPlaying,
    style: MusicLyricsStyle,
    colors: ShelfColors,
    /** Height the line may take in px (0 = as much as it needs); the text shrinks / wraps to fit it. */
    maxHeightPx: Int = 0,
    /** Small screen (the lower screen of a dual-screen handheld, a 3.5" device): smaller to start with. */
    compact: Boolean = false
) {
    val shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 12f)
    val big = style == MusicLyricsStyle.BIG
    val scale = when {
        compact -> if (big) 1.12f else 1f
        else -> if (big) 1.35f else 1.1f
    }
    val align = com.shelf.launcher.data.Features.lyricsAlign.state.value
    Column(
        Modifier.widthIn(max = if (compact) 460.dp else 560.dp).lyricFloat(),
        horizontalAlignment = align.horizontal()
    ) {
        PanelLyric(playing, style, colors, shadow, centred = true, scale = scale, maxHeightPx = maxHeightPx, textAlign = align.text())
    }
}

/**
 * Places [content] inside the free band [slot] (vertically centred in it, at the start edge or the end
 * edge), never letting it grow past the band — so it can't cover the game row wherever the row sits.
 */
@Composable
internal fun InMusicSlot(slot: MusicSlot, atEnd: Boolean, sidePadding: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val side = sidePadding.roundToPx()
        val maxH = slot.height.toInt().coerceAtLeast(0)
        val c = Constraints(
            minWidth = 0, maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0),
            minHeight = 0, maxHeight = maxH
        )
        val placeables = measurables.map { it.measure(c) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { p ->
                val x = if (atEnd) constraints.maxWidth - side - p.width else side
                val y = (slot.centre - p.height / 2f).toInt().coerceIn(slot.top.toInt(), (slot.bottom - p.height).toInt().coerceAtLeast(slot.top.toInt()))
                p.place(x, y)
            }
        }
    }
}

/**
 * The "Now playing" theme's own small player, on the background: cover, song, ⏮ ⏯ ⏭, a timeline you
 * can tap or drag through, and the line being sung (Big / Small / Off, Appearance › Theme). Compact on
 * purpose (it used to be a huge block of lyrics) and touch-only, so it never takes the D-pad away from
 * the games; the music buttons (Settings › Controls) work as always. It shrinks to fit the free space:
 * fewer lines when the band beside the row is low.
 */
@Composable
internal fun NowPlayingPanel(
    playing: NowPlaying,
    colors: ShelfColors,
    lyricsStyle: MusicLyricsStyle,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** The button the D-pad is on (MINI_PREVIOUS / MINI_PLAY / MINI_NEXT, HomeReach), or null. */
    focusedSlot: Int? = null
) {
    com.shelf.launcher.Breadcrumbs.mark("NowPlayingPanel")
    // A tap on the player (not on a button) opens the big music player.
    BoxWithConstraints(modifier.tapOnly { MusicSwipe.openFull?.invoke() }) {
        // The buttons and the timeline are always there (the same controls wherever the player sits);
        // only the lyric line is left out when there's no room for it.
        val roomForLyrics = maxHeight >= 110.dp && lyricsStyle != MusicLyricsStyle.OFF
        // Whatever height is left under the controls is the lyric's; it wraps and shrinks to fit it.
        val lyricRoomPx = with(androidx.compose.ui.platform.LocalDensity.current) {
            if (constraints.hasBoundedHeight) (maxHeight - 80.dp).roundToPx().coerceAtLeast(0) else 0
        }
        val compact = maxWidth < 360.dp
        // What shows (Appearance › Theme › On the background): the song's name, the buttons with the timeline,
        // the lyric. Fewer parts = each one bigger, so a lone part fills the room the player had.
        val showTitle = com.shelf.launcher.data.Features.musicShowTitle.state.value
        val showButtons = com.shelf.launcher.data.Features.musicShowControls.state.value
        val parts = (if (showTitle) 1 else 0) + (if (showButtons) 1 else 0) + (if (roomForLyrics) 1 else 0)
        val grow = when (parts) { 1 -> 1.55f; 2 -> 1.18f; else -> 1f }
        val wholeHeightPx = if (constraints.hasBoundedHeight) constraints.maxHeight else lyricRoomPx
        // No box behind it any more: the song, the buttons and the timeline are drawn straight onto the
        // scene, centred under the equalizer, with a soft shadow so they read on any cover.
        val shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1.5f), 10f)
        Column(
            Modifier.widthIn(max = (460 * grow).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTitle) {
                if (parts == 1) {
                    // Alone: the title big, the artist under it; long names wrap instead of being cut.
                    Text(
                        playing.title ?: "Unknown track", color = colors.textPrimary, fontSize = 24.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(24.sp), maxFontSize = 24.sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.125f, androidx.compose.ui.unit.TextUnitType.Em),
                        fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = uiFontStyle().merge(TextStyle(shadow = shadow))
                    )
                    playing.artist?.let {
                        Text(it, color = colors.textPrimary.copy(alpha = 0.75f), fontSize = 16.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(16.sp), maxFontSize = 16.sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.188f, androidx.compose.ui.unit.TextUnitType.Em), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = uiFontStyle().merge(TextStyle(shadow = shadow)))
                    }
                } else Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        // One line with the buttons and timeline: the space kept for the player (82 dp) has room
                        // for one; two lines pushed the timeline down onto the row / lyric under it.
                        playing.title ?: "Unknown track", color = colors.textPrimary, fontSize = (14 * grow).sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin((14 * grow).sp), maxFontSize = (14 * grow).sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Em),
                        fontWeight = FontWeight.Bold, maxLines = if (showButtons) 1 else 2, overflow = TextOverflow.Ellipsis, style = uiFontStyle().merge(TextStyle(shadow = shadow)),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    playing.artist?.let {
                        Text(
                            "  ·  $it", color = colors.textPrimary.copy(alpha = 0.7f), fontSize = (12 * grow).sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin((12 * grow).sp), maxFontSize = (12 * grow).sp), lineHeight = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Em),
                            maxLines = if (showButtons) 1 else 2, overflow = TextOverflow.Ellipsis, style = uiFontStyle().merge(TextStyle(shadow = shadow)),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
            if (showButtons) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelButton(Icons.Filled.SkipPrevious, "Previous", (32 * grow).dp, onPrevious, focused = focusedSlot == MINI_PREVIOUS)
                Spacer(Modifier.width((10 * grow).dp))
                // Play / pause: a ring in the song's colour rather than a solid white disc.
                val playOn = focusedSlot == MINI_PLAY
                Box(
                    Modifier
                        .size((34 * grow).dp)
                        .clip(CircleShape)
                        .border(if (playOn) 3.dp else 1.5.dp, if (playOn) Color.White else colors.accent.copy(alpha = 0.9f), CircleShape)
                        .background(colors.accent.copy(alpha = if (playOn) 0.5f else 0.18f))
                        .tapOnly(onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing.isPlaying) "Pause" else "Play",
                        tint = Color.White, modifier = Modifier.size((20 * grow).dp)
                    )
                }
                Spacer(Modifier.width((10 * grow).dp))
                PanelButton(Icons.Filled.SkipNext, "Next", (32 * grow).dp, onNext, focused = focusedSlot == MINI_NEXT)
            }
            Box(Modifier.widthIn(max = (420 * grow).dp).fillMaxWidth()) {
                if (playing.durationMs > 0) PanelTimeline(playing, colors, onSeek)
                else Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                    // No length from this player: an empty line keeps the player the same size.
                    Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.18f)))
                }
            }
            }
            if (roomForLyrics) PanelLyric(
                playing, lyricsStyle, colors, shadow, centred = true,
                scale = (if (compact) 0.92f else 1f) * grow,
                // Alone, the lyric has the player's whole height.
                maxHeightPx = if (parts == 1) wholeHeightPx else lyricRoomPx
            )
        }
    }
}

@Composable
private fun PanelButton(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, focused: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.22f)).border(2.dp, Color.White, CircleShape) else Modifier)
            .tapOnly(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}

/**
 * Time · bar · length. Tap anywhere on the bar to jump there; drag to scrub (the knob follows your
 * finger, and the song moves when you let go).
 */
@Composable
private fun PanelTimeline(playing: NowPlaying, colors: ShelfColors, onSeek: (Long) -> Unit) {
    val position = rememberPlaybackPosition(playing)
    val duration = playing.durationMs
    var scrub by remember { mutableStateOf<Float?>(null) }
    // A seek shows at once instead of waiting for the player's next update.
    var pending by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(position / 1000) { if (pending != null && kotlin.math.abs(position - pending!!) < 1500) pending = null }
    // A seek the player ignored: stop showing it after a moment (the bar used to stay stuck there).
    LaunchedEffect(pending) { if (pending != null) { delay(3_000); pending = null } }
    val shownMs = scrub?.let { (it * duration).toLong() } ?: pending ?: position
    val fraction = (shownMs.toFloat() / duration).coerceIn(0f, 1f)
    val seek by rememberUpdatedState(onSeek)
    val canSeek = playing.canSeek
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTrackTime(shownMs), color = colors.textPrimary.copy(alpha = 0.75f), fontSize = 9.sp, style = uiFontStyle().merge(TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 6f))))
        Spacer(Modifier.width(6.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(18.dp)
                .pointerInput(duration, canSeek) {
                    if (!canSeek || duration <= 0) return@pointerInput
                    detectTapGestures { o ->
                        val ms = (o.x / size.width * duration).toLong().coerceIn(0L, duration)
                        pending = ms; seek(ms)
                    }
                }
                .pointerInput(duration, canSeek) {
                    if (!canSeek || duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { o -> scrub = (o.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            scrub?.let { f -> val ms = (f * duration).toLong(); pending = ms; seek(ms) }
                            scrub = null
                        },
                        onDragCancel = { scrub = null }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val bar = if (scrub != null) 4.dp else 2.dp
            Box(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fraction).height(bar).clip(RoundedCornerShape(2.dp)).background(colors.accent))
            if (canSeek) {
                val knob = if (scrub != null) 12.dp else 9.dp
                Box(
                    Modifier
                        .offset(x = maxWidth * fraction - knob / 2)
                        .size(knob)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(formatTrackTime(duration), color = colors.textPrimary.copy(alpha = 0.75f), fontSize = 9.sp, style = uiFontStyle().merge(TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 6f))))
    }
}

/** Lyrics for the song [playing], looked up again after a network failure (it used to stay blank). */
@Composable
internal fun rememberSongLyrics(playing: NowPlaying): LyricsRepository.Lookup? {
    val lyricsOn by LyricsOptIn.enabled.collectAsState()
    val title = playing.title
    val result by produceState<LyricsRepository.Lookup?>(null, title, playing.artist, lyricsOn) {
        if (title == null) { value = LyricsRepository.Lookup.NotFound; return@produceState }
        if (!lyricsOn) { value = LyricsRepository.Lookup.Off; return@produceState }
        value = null
        var wait = 10_000L
        while (true) {
            val r = LyricsRepository.lookup(title, playing.artist, playing.album, playing.durationMs)
            value = r
            if (r !is LyricsRepository.Lookup.Failed) break
            // Offline (Wi-Fi still waking up after a game, a hiccup at LRCLIB): try again, less often each time.
            delay(wait)
            wait = (wait * 2).coerceAtMost(120_000L)
        }
    }
    return result
}

/**
 * The line to show at [positionMs]: the sung line ("♪" in the intro and instrumental gaps), and the
 * next one. Songs LRCLIB only has untimed lyrics for get their lines spread over the song's length,
 * which follows the singing roughly — better than showing nothing at all.
 */
internal fun lyricLinesAt(lyrics: com.shelf.launcher.data.Lyrics, positionMs: Long, durationMs: Long): Pair<String, String?> {
    if (lyrics.isSynced) {
        val synced = lyrics.synced
        val i = synced.indexOfLast { it.atMs <= positionMs }
        val now = synced.getOrNull(i)?.text?.takeIf { it.isNotBlank() } ?: "\u266A"
        val next = synced.drop(i + 1).firstOrNull { it.text.isNotBlank() }?.text
        return now to next
    }
    val plain = lyrics.plain
    if (plain.isEmpty()) return "\u266A" to null
    if (durationMs <= 0) return plain.first() to plain.getOrNull(1)
    // Singing usually starts a little in and ends a little before the song does.
    val start = durationMs * 0.08f
    val end = durationMs * 0.92f
    val f = ((positionMs - start) / (end - start)).coerceIn(0f, 0.9999f)
    if (positionMs < start) return "\u266A" to plain.first()
    val i = (f * plain.size).toInt().coerceIn(0, plain.lastIndex)
    return plain[i] to plain.getOrNull(i + 1)
}

/**
 * The line being sung (and, Big, the next one faintly under it). "♪" between lines; a short note
 * when lyrics are switched off or the song has none (for the first few seconds of the song only).
 */
@Composable
private fun PanelLyric(
    playing: NowPlaying,
    style: MusicLyricsStyle,
    colors: ShelfColors,
    shadow: Shadow,
    centred: Boolean = false,
    scale: Float = 1f,
    /** Height the lyric may take in px (0 = unlimited). */
    maxHeightPx: Int = 0,
    /** Overrides the text alignment (the lyrics layout setting); null = centred / start as above. */
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    val lookup = rememberSongLyrics(playing)
    // Woken exactly when the next line starts (timed lyrics), not several times a second; untimed
    // lyrics and the notes check once a second.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedWhileActive(playing.isPlaying, playing.positionAt, lookup) {
        if (playing.isPlaying) {
            val synced = (lookup as? LyricsRepository.Lookup.Found)?.lyrics?.synced.orEmpty()
            while (true) {
                now = SystemClock.elapsedRealtime()
                val pos = playing.currentPosition(now)
                val nextAt = synced.firstOrNull { it.atMs > pos }?.atMs
                val wait = if (nextAt != null) ((nextAt - pos) / playing.speed.coerceAtLeast(0.25f)).toLong() + 15L else 1_000L
                delay(wait.coerceIn(60L, 2_000L))
            }
        }
    }
    val position = playing.currentPosition(now)
    val big = style == MusicLyricsStyle.BIG
    val align = textAlign ?: if (centred) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
    // A note instead of lyrics only near the start of a song, so it doesn't sit there for the whole track.
    val early = position < 8_000L
    val (line, next, note) = when (lookup) {
        is LyricsRepository.Lookup.Found -> lyricLinesAt(lookup.lyrics, position, playing.durationMs).let { Triple(it.first, it.second, false) }
        LyricsRepository.Lookup.Off -> if (early) Triple("Lyrics are off \u00B7 Settings \u203A Audio", null, true) else return
        LyricsRepository.Lookup.NotFound -> if (early) Triple("No lyrics for this song", null, true) else return
        LyricsRepository.Lookup.Failed, null -> if (lookup == null) Triple("\u266A", null, true) else return
    }
    if (!centred) Spacer(Modifier.height(6.dp))
    // The next line (Big) gets about a quarter of the room, the sung line the rest. Long lines wrap onto
    // a second (or third) line and get smaller rather than being cut off with "…".
    val showNext = big && next != null
    val lineRoom = if (maxHeightPx > 0) (if (showNext) (maxHeightPx * 0.74f).toInt() else maxHeightPx) else 0
    val nextRoom = if (maxHeightPx > 0) (maxHeightPx - lineRoom).coerceAtLeast(1) else 0
    val baseSize = (if (note) 11f else if (big) 16f else 12f) * scale
    Crossfade(
        targetState = line, animationSpec = tween(300), label = "panelLyric",
        modifier = if (centred) Modifier.fillMaxWidth() else Modifier
    ) { current ->
        FitText(
            current, color = colors.textPrimary.copy(alpha = if (note) 0.55f else 1f),
            maxSize = baseSize.sp, minSize = (baseSize * 0.66f).coerceAtLeast(9f).sp, maxLines = 2,
            fontWeight = if (note) FontWeight.Normal else if (big) FontWeight.Bold else FontWeight.Medium,
            style = uiFontStyle().merge(TextStyle(shadow = shadow)), textAlign = align, maxHeight = lineRoom
        )
    }
    if (showNext && next != null && (maxHeightPx == 0 || nextRoom > 8)) {
        FitText(
            next, color = colors.textPrimary.copy(alpha = 0.45f), maxSize = (11f * scale).sp, minSize = 8.sp, maxLines = 1,
            style = uiFontStyle().merge(TextStyle(shadow = shadow)), textAlign = align, maxHeight = nextRoom
        )
    }
}
