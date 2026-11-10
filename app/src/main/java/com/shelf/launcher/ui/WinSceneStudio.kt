package com.shelf.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 506 (owner review of the 505 visual report, P0 — Layout Studio was rated Broken): the user-built layout had the
 * generic window, its 16:9 picture band with the name written over it — on the GT78 the cover was flattened into a strip and
 * the two-line name ran into the words printed in the art. Its own window now, in the layout's (theme / your) colours:
 *  - three separate regions: the art in a framed well at its own shape (whole, Fit — never stretched or cropped), the name and
 *    console beside it (never over the art), the facts, then the keys (always whole: KeyRow wraps, More on narrow screens);
 *  - the studio's mark: the art well's crop marks and a faint layout grid, drawn once (no per-frame work beyond the reveal);
 *  - no art: the well keeps its shape as an empty frame with the console's initials, not a black hole;
 *  - narrow / short rooms (the GT78's 640 x 427 dp less margins): art and name side by side on top, facts and keys under them.
 * Motion: the panel rises a little as it fades in, the crop marks close in on the art; back the same way. Read while drawing.
 */

/** Pure: the narrow arrangement (art and name on one line, facts and keys under them) below this room. */
internal fun studioNarrow(maxW: Float, maxH: Float): Boolean = maxW < 460f || maxH < 300f

/** Pure: the art well's width (dp): a third of the panel at most, 190 dp at most, and short enough to leave the keys their room. */
internal fun studioArtWidth(panelW: Float, maxH: Float, narrow: Boolean): Float =
    if (narrow) minOf(panelW * 0.24f, 92f).coerceAtLeast(56f)
    else minOf(panelW * 0.3f, 190f, ((maxH - 130f) * 0.72f).coerceAtLeast(90f))

/** Pure: a console's initials for the empty art well ("Nintendo 64" → "N64", "PlayStation" → "PS"). */
internal fun studioInitials(console: String): String {
    val words = console.split(' ', '-', '/').filter { it.isNotBlank() }
    if (words.isEmpty()) return "?"
    val lead = words.joinToString("") { w -> if (w.all { it.isDigit() }) w else w.first().uppercase() + w.drop(1).filter { it.isUpperCase() } }
    return lead.take(4)
}

@Composable
internal fun StudioPanel(ctx: WinSceneCtx) {
    val t = ShelfTheme.colors
    val a = ctx.accent
    val ink = t.textPrimary
    val muted = ShelfTheme.textMuted
    val w = minOf(ctx.maxW, 700.dp)
    val narrow = studioNarrow(w.value, ctx.maxH.value)
    val artW = studioArtWidth(w.value, ctx.maxH.value, narrow).dp
    val shape = RoundedCornerShape(16.dp)
    val grid = a.copy(alpha = if (t.background.luminance() > 0.5f) 0.07f else 0.05f)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * 24.dp.toPx()
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        this.shape = shape; clip = true
        shadowElevation = 16.dp.toPx() * e
    }.background(Brush.verticalGradient(listOf(t.surfaceRaised.copy(alpha = 0.97f), t.surface.copy(alpha = 0.98f))))
        .drawBehind {
            // The studio's layout grid, faint.
            val step = 18.dp.toPx()
            var x = step; while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = step; while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
        .border(1.dp, a.copy(alpha = 0.4f), shape).padding(if (narrow) 12.dp else 16.dp)) {
        if (narrow) {
            Row(verticalAlignment = Alignment.Top) {
                StudioArt(ctx, artW, a, ink)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { StudioTitle(ctx, a, ink, 18) }
            }
            Spacer(Modifier.height(8.dp))
            SceneFactsBody(ctx, muted, ink, inline = true)
        } else {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.Top) {
                StudioArt(ctx, artW, a, ink)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    StudioTitle(ctx, a, ink, 22)
                    Spacer(Modifier.height(10.dp))
                    SceneFactsBody(ctx, muted, ink)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyRow(sceneKeys(ctx), 8.dp) { k ->
            val onA = if (a.luminance() > 0.5f) Color(0xFF111111) else Color.White
            SceneKeyStyled(k, if (k.primary) a else ink.copy(alpha = 0.07f), if (k.primary) a else ink.copy(alpha = 0.16f), if (k.primary) onA else ink,
                if (k.primary) onA else ink, ink.copy(alpha = 0.16f), if (k.primary) ink else a, RoundedCornerShape(10.dp), height = 36.dp)
        }
    }
}

@Composable
private fun StudioTitle(ctx: WinSceneCtx, accent: Color, ink: Color, size: Int) {
    Text(ctx.console.uppercase(), color = com.shelf.launcher.ui.theme.legibleArtAccent(accent, ShelfTheme.colors.surface, ShelfTheme.colors.surface),
        fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, maxLines = 2)
    Spacer(Modifier.height(2.dp))
    StableFullText(ctx.name, color = ink, fontSize = size.sp, maxLines = 3, fontWeight = FontWeight.Bold)
}

/** The art at its own shape in a framed well, the studio's crop marks round it (they close in as the window opens). */
@Composable
private fun StudioArt(ctx: WinSceneCtx, width: Dp, accent: Color, ink: Color) {
    val art = ctx.cover ?: ctx.wide
    // The well takes the art's shape class: a cover's portrait, a wide picture's landscape (only when there is no cover).
    val ratio = if (ctx.cover != null || art == null) 0.72f else 1.6f
    Box(Modifier.width(width).aspectRatio(ratio).padding(6.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val e = sceneEase(ctx.reveal())
            val o = (1f - e) * 10.dp.toPx() - 4.dp.toPx()
            val l = 12.dp.toPx()
            val c = accent.copy(alpha = 0.85f * e)
            val sw = 2.dp.toPx()
            val r = size
            // Four L-shaped marks just outside the art.
            listOf(Offset(-o, -o) to Offset(1f, 1f), Offset(r.width + o, -o) to Offset(-1f, 1f),
                Offset(-o, r.height + o) to Offset(1f, -1f), Offset(r.width + o, r.height + o) to Offset(-1f, -1f)).forEach { (p, d) ->
                drawLine(c, p, Offset(p.x + d.x * l, p.y), sw); drawLine(c, p, Offset(p.x, p.y + d.y * l), sw)
            }
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.22f))) {
            // (Batch 523: Smart Fill - a light crop, else the whole picture over its soft copy, never bars.)
            if (art != null) SmartArt(art, ctx.name, Modifier.fillMaxSize(), well = Color.Black.copy(alpha = 0.22f))
            else {
                // No art: an empty frame with the console's initials.
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    drawRoundRect(ink.copy(alpha = 0.3f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
                Text(studioInitials(ctx.console), color = ink.copy(alpha = 0.55f), fontSize = 18.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
