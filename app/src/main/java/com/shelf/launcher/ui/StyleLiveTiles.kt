package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › Live tiles: flat tiles on a pale backdrop. The categories as words across the top (L2 / R2
 * or a tap change them), the games a page at a time (one big tile and four small, one big and two, or six
 * even ones), coloured shortcut tiles under them, and the game's name and time played at the bottom.
 * ◀ ▶ ▲ ▼ move round the tiles (past the edge to the next page); ▼ from the bottom row reaches the shortcut
 * tiles, ▲ / B come back. Options: Appearance › Game row › Live tiles options.
 */
@Composable
internal fun StyleLiveTiles(c: StyleCtx, modifier: Modifier) {
    val page = Features.livePage.state.value
    val bar = c.shortcuts.take(Features.liveBar.state.value)
    val light = Features.liveLight.state.value
    // The D-pad's buttons (update 101): the category names on top (▲ from the top row), the shortcut tiles and then the
    // player's ⏮ ⏯ ⏭ along the bottom (▼).
    // Update 109: the tabs hidden in Edit Home are skipped by the D-pad.
    val tabs = if (Features.liveTabs.state.value && !LayoutParts.off(com.shelf.launcher.data.HomeLayout.LIVE, "tabs")) liveTabs(c) else emptyList()
    // (Update 212: the layout's own category title, CategoryTitle.kt.)
    if (tabs.isNotEmpty()) CategoryTitle.Native()
    val musicOn = styleMusicShown(c) && c.game != null
    SideEffect {
        StyleNav.livePage = page.size
        // Update 156: ◀ ▶ go round the pages with Endless on (LiveLoop.kt).
        LiveLoop.on = loopRowOn(c)
        StyleNav.side = emptyList()
        StyleNav.top = tabs.map { (d, _) -> { if (d != 0) c.actions.category(d) } }
        StyleNav.topStart = tabs.indexOfFirst { it.first == 0 }
        StyleNav.bar = bar.map { it.onClick } + (if (musicOn) styleMusicActions(c) else emptyList())
    }
    val ink = if (light) Color(0xFF2B2B2B) else Color.White
    val g = c.game
    val tint = when (Features.liveColor.state.value) {
        Features.LiveColor.GREEN -> Color(0xFF107C10)
        Features.LiveColor.THEME -> ShelfTheme.accent
        Features.LiveColor.GAME -> g?.let { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.hue.toFloat(), 0.7f, 0.62f))) } ?: ShelfTheme.accent
    }
    // The pale backdrop itself is drawn across the screen by StyleBackLayer.
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp)) {
            val compact = maxHeight < 380.dp
            val fullH = maxHeight
            Column(Modifier.fillMaxSize()) {
                if (Features.liveTabs.state.value) {
                    LiveTabs(c, ink, compact)
                    Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
                }
                // Plain if / else, never an early return@Column (it unbalances Compose's groups, see AppIcons.kt).
                if (g == null) {
                    StyleEmpty(Modifier.weight(1f))
                } else {
                    val gap = 10.dp
                    // The page of games the selection is on, sliding when the page changes.
                    // Update 156: the page is counted on round the ends with Endless on (LiveLoop.kt), so the first page
                    // after the last one slides in like any next page (it used to slide back). The start of the page is
                    // that count times the page's size; without Endless it is the page's first game, as before.
                    val pageN = page.size
                    val pages = ((c.games.size + pageN - 1) / pageN).coerceAtLeast(1)
                    val first = rememberLivePage(c.focus, pageN, c.games.size, loopRowOn(c), c.state.loopTick, c.state.loopStep) * pageN
                    AnimatedContent(
                        targetState = first, label = "livePage",
                        // The next page slides in from the side (update 104), it used to fade.
                        transitionSpec = { pageSlide() },
                        modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds().categorySlide()
                    ) { counted ->
                        // (Update 156: a page counted on past the last one shows the games from the first page again.)
                        val start = if (counted >= 0 && counted < c.games.size) counted else counted.mod(pages * pageN)
                        // The % size (update 105): the page of tiles a little smaller, in the middle.
                        val pageK = GameSize.scale(com.shelf.launcher.data.HomeLayout.LIVE)
                        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = pageK; scaleY = pageK }) { LivePageTiles(c, start, page, gap, ink, tint) }
                    }
                    val music = styleMusicShown(c)
                    if (bar.isNotEmpty() || music) {
                        Spacer(Modifier.height(gap))
                        Row(
                            Modifier.fillMaxWidth().height((fullH * 0.2f).coerceIn(if (music) 92.dp else 52.dp, 110.dp)),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            bar.forEachIndexed { i, s ->
                                val on = StyleNav.on(StyleNav.Zone.BAR, i)
                                val click by rememberUpdatedState(s.onClick)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .graphicsLayer { val k = if (on) 1.03f else 1f; scaleX = k; scaleY = k }
                                        .background(if (i == 0) tint else themeDark(Color(0xFF2E2E2E))) // (update 151: themed)
                                        .border(if (on) 3.dp else 0.dp, if (on) (if (light) Color(0xFF1E1E1E) else Color.White) else Color.Transparent, RectangleShape)
                                        .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, i); click() } }
                                        .padding(10.dp)
                                ) {
                                    Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(if (compact) 18.dp else 24.dp).align(Alignment.TopStart))
                                    Text(
                                        // (Update 136: as written — no all-lower-case labels.)
                                        s.label, color = Color.White, fontSize = if (compact) 12.sp else 15.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(if (compact) 12.sp else 15.sp), maxFontSize = if (compact) 12.sp else 15.sp),
                                        maxLines = 1, overflow = TextOverflow.Clip, softWrap = false, modifier = Modifier.align(Alignment.BottomStart)
                                    )
                                }
                            }
                            // The built-in player: the last tile of the bottom row, bottom right.
                            if (music) {
                                if (bar.isEmpty()) Spacer(Modifier.weight(1f))
                                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { StyleMusicCard(c, width = 240.dp, focusedSlot = styleMusicFocus(bar.size)) }
                            }
                        }
                    }
                    if (!compact) {
                        Spacer(Modifier.height(8.dp))
                        FullText(
                            LocalTitleOptions.current.display(g.name), modifier = Modifier.riseOnChange(g.stableKey), color = ink.copy(alpha = 0.85f), fontSize = 20.sp,
                            fontWeight = FontWeight.Light, maxLines = 1
                        )
                        Text(
                            c.lastPlayed(g)?.let { "Last played: " + lastPlayedLabel(it) + " · " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null) } ?: "Never played",
                            color = ink.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** The category words along the top: the one before (faded), the one you're in (strong), the next ones. */
@Composable
private fun LiveTabs(c: StyleCtx, ink: Color, compact: Boolean) {
    val move by rememberUpdatedState(c.actions.category)
    val tabs = liveTabs(c)
    // (Update 294, Toni: "never show a partly hidden chip": the category words that fit whole, "+N ›" for the rest, the
    // lit one always whole — WholeChipRow. The next words used to run off the right edge, cut.)
    val lit = if (StyleNav.zone == StyleNav.Zone.TOP) StyleNav.index else tabs.indexOfFirst { it.first == 0 }
    WholeChipRow(
        tabs.size, lit, Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.LIVE, "tabs"), "Category tabs"),
        spacing = 0.dp, hintColor = ink.copy(alpha = 0.38f), hintSize = if (compact) 16.sp else 22.sp
    ) { i ->
            val (d, label) = tabs[i]
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            Text(
                // (Update 136: as written — no all-lower-case labels.)
                label,
                color = if (d == 0 || on) ink else ink.copy(alpha = 0.38f),
                fontSize = if (compact) 22.sp else 34.sp, fontWeight = if (on) FontWeight.Normal else FontWeight.Light, maxLines = 1, softWrap = false,
                textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                modifier = Modifier
                    .padding(end = if (compact) 16.dp else 26.dp)
                    .pointerInput(d) { detectTapGestures { if (d != 0) move(d) } }
            )
    }
}

/** The category words: the one you're in (0), the one before it and the next three (each once, however few there are). */
private fun liveTabs(c: StyleCtx): List<Pair<Int, String>> =
    if (c.stop < 0 || c.stops.size <= 1) listOf(0 to c.categoryLabel)
    else listOf(-1, 0, 1, 2, 3).filter { d -> d == 0 || (d < 0 && c.stops.size > 2) || (d > 0 && d < c.stops.size - (if (c.stops.size > 2) 1 else 0)) }
        .map { d -> d to (if (d == 0) c.categoryLabel else c.stopAt(d)?.label.orEmpty()) }

/** One page of tiles starting at game [start]. */
@Composable
private fun LivePageTiles(c: StyleCtx, start: Int, page: Features.LivePage, gap: Dp, ink: Color, tint: Color) {
    @Composable
    fun Tile(slot: Int, modifier: Modifier) {
        val i = start + slot
        val g = c.games.getOrNull(i)
        if (g == null) {
            Box(modifier.background(ink.copy(alpha = 0.05f)))
            return
        }
        LiveTile(c, g, i, i == c.focus && StyleNav.zone == StyleNav.Zone.GAMES, ink, tint, modifier)
    }
    when (page) {
        Features.LivePage.SIX -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (r in 0..1) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0..2) Tile(r * 3 + col, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Features.LivePage.BIG_TWO -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tile(0, Modifier.weight(1.4f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(1, Modifier.fillMaxWidth().weight(1f))
                Tile(2, Modifier.fillMaxWidth().weight(1f))
            }
        }
        Features.LivePage.BIG_FOUR -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tile(0, Modifier.weight(1.15f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0..1) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(1 + r * 2, Modifier.weight(1f).fillMaxHeight())
                    Tile(2 + r * 2, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun LiveTile(c: StyleCtx, g: Game, i: Int, on: Boolean, ink: Color, tint: Color, modifier: Modifier) {
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val lift = styleLift(on)
    Box(
        modifier
            .graphicsLayer { val k = 1f + 0.025f * lift; scaleX = k; scaleY = k }
            .then(if (on) Modifier.focusedCard() else Modifier)
            .border(if (on) 4.dp else 0.dp, if (on) ink.copy(alpha = 0.92f) else Color.Transparent, RectangleShape)
            .padding(if (on) 4.dp else 0.dp)
            .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } }
    ) {
        StyleArt(c, g, Modifier.fillMaxSize(), wide = true, corner = 0.dp)
        if (Features.liveNames.state.value) {
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomStart)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                FullText(
                    LocalTitleOptions.current.display(g.name), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).background(tint))
    }
}

/** The pale backdrop with two big faint rings. */
@Composable
internal fun LiveBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF3F3F3), Color(0xFFDADADA))))
        val r = size.minDimension * 0.75f
        drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(size.width * 0.12f, size.height * 0.05f), style = Stroke(width = r * 0.18f))
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.9f, Offset(size.width * 0.95f, size.height * 1.05f), style = Stroke(width = r * 0.14f))
    }
}
