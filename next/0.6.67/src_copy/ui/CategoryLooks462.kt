package com.shelf.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout

/*
 * Update 462: the themed category pickers of Channel grid, Cinema and Arctic hub (Pocket Quest, Green Core and Pocket
 * classic use their own pages; ThemedPicker). Each is a look of the one category screen (PlatformBrowser): the same tiles
 * (visible categories, hidden ones left out, the saved order), the same focus, A / B / D-pad / touch handling and the same
 * pick (applyCategory: steps there, never writes a category's own layout). Text is shown whole (FullText).
 */

/** The look a layout's own category picker uses (Settings › Category screen › Themed pickers), or null: the usual one. */
internal fun themedLookFor(l: HomeLayout): Features.PlatformView? = when (l) {
    HomeLayout.CHAN -> Features.PlatformView.WIICHAN
    HomeLayout.CINEMA -> Features.PlatformView.CINEMA
    HomeLayout.ARCTIC -> Features.PlatformView.ARCTIC
    else -> null
}

private fun Modifier.tapPick(index: Int, selected: Int, onFocus: (Int) -> Unit, onPick: (Int) -> Unit): Modifier =
    pointerInput(index, selected) { detectTapGestures { if (index == selected) onPick(index) else onFocus(index) } }

/** Channel grid: white rounded channels on the pinstriped grey, four across, a page of twelve; the lit one in blue. */
@Composable
internal fun WiiChannelLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val per = DRAWER_COLS * 3
    val page = (selected.coerceAtLeast(0)) / per
    val pages = (tiles.size + per - 1) / per
    Box(Modifier.fillMaxSize().background(Color(0xFFE9EAEC))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 6.dp.toPx(); var y = 0f
            while (y < size.height) { drawRect(Color(0xFFDADCDF), Offset(0f, y), Size(size.width, 1.dp.toPx())); y += step }
        }
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 66.dp, bottom = 58.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0 until 3) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (cIdx in 0 until DRAWER_COLS) {
                    val i = page * per + r * DRAWER_COLS + cIdx
                    val t = tiles.getOrNull(i)
                    val lit = i == selected
                    val grow by animateFloatAsState(if (lit) 1.05f else 1f, tween(if (animate) 140 else 0), label = "chan")
                    Box(
                        Modifier.weight(1f).fillMaxHeight().zIndex(if (lit) 1f else 0f).graphicsLayer { scaleX = grow; scaleY = grow }
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (t == null) Color(0xFFD3D5D8) else Color.White)
                            .border(if (lit) 4.dp else 1.dp, if (lit) Color(0xFF34BEED) else Color(0xFFC4C7CB), RoundedCornerShape(18.dp))
                            .then(if (t != null) Modifier.tapPick(i, selected, onFocus, onPick) else Modifier)
                    ) {
                        if (t != null) {
                            CategoryArt(t, CardShape.WIDE, Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(13.dp)))
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White.copy(alpha = 0.88f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                                FullText(t.label, color = Color(0xFF3A3F45), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
        // (The browser's white header and legend need a dark ground: the channel menu's grey bars top and bottom.)
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp).background(Brush.verticalGradient(listOf(Color(0xFF5B626B), Color(0xFF727982)))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp).background(Brush.verticalGradient(listOf(Color(0xFF727982), Color(0xFF5B626B)))))
        // The page dots (more than twelve categories), over the legend.
        if (pages > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(pages) { p -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (p == page) Color(0xFF34BEED) else Color(0xFFAEB3B8))) }
        }
    }
}

/** Cinema: the dark theatre — the lit category big as a title card, the others as a row of posters along the bottom. */
@Composable
internal fun CinemaLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val red = if (Features.cnAccent.state.value == Features.CnAccent.CINEMA) Color(0xFFE50914) else com.shelf.launcher.ui.theme.ShelfTheme.accent
    val pos = rememberLookGlide(selected, animate)
    val t0 = tiles.getOrNull(selected) ?: return
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        val w = maxWidth
        // The title card: the art on the right, faded into the dark, the name and count on the left.
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f).tapPick(selected, selected, onFocus, onPick)) {
            CategoryArt(t0, CardShape.WIDE, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(w * 0.62f))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xFF0B0B0D), 0.42f to Color(0xFF0B0B0D).copy(alpha = 0.7f), 0.75f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xFF0B0B0D))))
            Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp, top = 56.dp).width(w * 0.46f)) {
                FullText("CATEGORY", color = red, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                FullText(t0.label, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                FullText(listOfNotNull(lookCount(t0), lookFacts(t0)).joinToString("  ·  "), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
            }
        }
        // The row: posters 2:3, the lit one outlined white and a little larger, gliding with the D-pad.
        val pw = 86.dp; val gap = 12.dp
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(186.dp).padding(bottom = 40.dp).clipToBounds()) {
            val window = lookWindow(selected, pos, 8, tiles.lastIndex)
            for (i in window) {
                val t = tiles[i]
                val lit = i == selected
                Box(
                    Modifier.offset(x = 32.dp + (pw + gap) * (i - pos.value)).align(Alignment.BottomStart).size(pw, 128.dp)
                        .graphicsLayer { val s = if (lit) 1.08f else 1f; scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xFF222226))
                        .border(if (lit) 3.dp else 0.dp, if (lit) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                        .tapPick(i, selected, onFocus, onPick)
                ) {
                    CategoryArt(t, CardShape.TALL, Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))).padding(4.dp)) {
                        FullText(t.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Arctic hub: frosted panes down the left on the deep slate, the lit one bright with an ice-blue edge; its art beside them. */
@Composable
internal fun ArcticLook(tiles: List<CategoryTile>, selected: Int, animate: Boolean, onFocus: (Int) -> Unit, onPick: (Int) -> Unit) {
    val ice = Color(0xFF9BD8FF)
    val pos = rememberLookGlide(selected, animate)
    val t0 = tiles.getOrNull(selected) ?: return
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF15202D), Color(0xFF0D141D), Color(0xFF080C12))))) {
        val w = maxWidth; val h = maxHeight
        // The art of the lit category, in a pane on the right.
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp, top = 66.dp, bottom = 50.dp).width(w * 0.5f).fillMaxHeight()
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B2633)).border(1.dp, ice.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .tapPick(selected, selected, onFocus, onPick)) {
            CategoryArt(t0, CardShape.WIDE, Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE60A1018)))).padding(14.dp)) {
                FullText(t0.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                FullText(listOfNotNull(lookCount(t0), lookFacts(t0)).joinToString("  ·  "), color = ice, fontSize = 12.sp)
            }
        }
        // The panes: a column that glides, the lit one in the middle third.
        val rowH = 54.dp; val gap = 8.dp
        val listW = w * 0.5f - 56.dp
        Box(Modifier.padding(start = 28.dp, top = 62.dp, bottom = 46.dp).width(listW).fillMaxHeight().clipToBounds()) {
            val window = lookWindow(selected, pos, 6, tiles.lastIndex)
            for (i in window) {
                val t = tiles[i]
                val lit = i == selected
                Row(
                    Modifier.offset(y = (h - 108.dp) * 0.5f - rowH / 2 + (rowH + gap) * (i - pos.value)).width(listW).height(rowH)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (lit) Color(0x33E8F6FF) else Color(0x14E8F6FF))
                        .border(if (lit) 2.dp else 1.dp, if (lit) ice else Color(0x22E8F6FF), RoundedCornerShape(12.dp))
                        .tapPick(i, selected, onFocus, onPick).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(4.dp).height(26.dp).clip(RoundedCornerShape(2.dp)).background(if (lit) ice else lookHue(t, 0.5f, 0.45f)))
                    Spacer(Modifier.width(10.dp))
                    FullText(t.label, color = if (lit) Color.White else Color.White.copy(alpha = 0.72f), fontSize = 15.sp, fontWeight = if (lit) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                    FullText(t.count.toString(), color = if (lit) ice else Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        }
    }
}
