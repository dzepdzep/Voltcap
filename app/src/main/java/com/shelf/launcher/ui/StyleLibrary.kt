package com.shelf.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.roundToInt

/*
 * Update 146: Library panel — a home laid out like a PC game library.
 *
 * Left: a panel about the game you're on (its cover big at the top, its name and console, three small boxes with the
 * time played, the achievements and when it was last played, a few lines about it, then Play and Details).
 * Right: the category's games as a grid in the layout's card artwork, gliding up and down so the game you're on stays
 * in view. Along the top: the categories as tabs (the one you're in lit, with its game count) and the L2 / R2 hints.
 *
 * D-pad: ▲ ▼ ◀ ▶ move in the grid; ◀ on the first column goes into the panel's buttons (▲ ▼ between them, ▶ / B back);
 * ▲ on the top row goes up to the tabs (◀ ▶ along them, A opens one, ▼ / B back); ▼ on the last row reaches what lies
 * below (round shortcuts / music player). Touch: tap a game to pick it, tap it again to open it; tap a tab, Play or Details.
 * Options: Settings › Home › Layout options (Library panel).
 */

/** What the key handler needs from the last drawing (it runs outside Compose). */
private object LibraryNav {
    /** Games per row as the grid was last laid out: the step for ▲ ▼. */
    @Volatile var cols = 5
}

@Composable
internal fun StyleLibrary(c: StyleCtx, modifier: Modifier = Modifier) {
    val panelOn = Features.libPanel.state.value
    val tabsOn = Features.libTabs.state.value
    // A part hidden in Edit Home is skipped by the D-pad (like the other layouts, update 109).
    val panelOff = LayoutParts.off(HomeLayout.LIBRARY, "panel")
    val tabsOff = LayoutParts.off(HomeLayout.LIBRARY, "tabs")
    val g0 = c.game
    // Outside the category cycle (e.g. Rediscover) there's only the one tab: the category you're in.
    val single = c.stop < 0 || c.stops.isEmpty()
    SideEffect {
        StyleNav.bar = emptyList()
        // ◀ from the first column: Play, then Details, stacked in the panel (▲ ▼ between them).
        StyleNav.side = if (!panelOn || panelOff || g0 == null) emptyList() else listOf({ c.actions.play(g0) }, { c.actions.open() })
        // ▲ from the top row: the tabs; A on one jumps to that category (as many L2 / R2 steps as it is away).
        StyleNav.top = if (!tabsOn || tabsOff) emptyList()
            else if (single) listOf({ c.actions.category(1) })
            else c.stops.indices.map { t -> { if (t != c.stop) c.actions.category(t - c.stop) } }
        // Going up lands on the tab of the category you're in.
        StyleNav.topStart = if (single) 0 else c.stop
        // Update 156: Endless on (for libraryKey: ◀ / ▶ at the ends go round).
        StyleLoop.on = loopRowOn(c)
    }
    // The tab to start on is this layout's own: another layout's top row starts where it was left again.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { StyleNav.topStart = -1 } }
    BoxWithConstraints(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        val roomy = maxHeight >= 470.dp
        // About a quarter of the width, never so narrow its boxes squash, never so wide the grid starves.
        val panelW = (maxWidth * 0.28f).coerceIn(168.dp, 380.dp)
        Column(Modifier.fillMaxSize()) {
            if (tabsOn) {
                LibraryTabs(c, compact, Modifier.fillMaxWidth().editablePart(LayoutParts.id(HomeLayout.LIBRARY, "tabs"), "Category tabs"))
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (panelOn && g0 != null) {
                    LibraryPanel(
                        c, g0, compact, roomy,
                        Modifier.width(panelW).fillMaxHeight().padding(bottom = 4.dp)
                            .editablePart(LayoutParts.id(HomeLayout.LIBRARY, "panel"), "Game panel")
                    )
                    Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                }
                // Plain if / else (never an early return out of the Row).
                if (c.games.isEmpty()) {
                    StyleEmpty(Modifier.weight(1f))
                } else {
                    LibraryGrid(c, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * The D-pad on the grid (Update 146): row by row. Returns true when handled.
 * ◀ on the first column → the panel's buttons; ▲ on the top row → the tabs (else what lies above);
 * ▼ on the last row → what lies below.
 */
internal fun libraryKey(ctx: StyleKeyCtx): Boolean {
    val count = ctx.count
    if (count <= 0) {
        // No games: the tabs and the panel can still be reached.
        return when (ctx.key) {
            Key.DirectionUp -> if (StyleNav.top.isNotEmpty()) ctx.enterZone(StyleNav.Zone.TOP) else ctx.reach(true)
            Key.DirectionDown -> ctx.reach(false)
            else -> ctx.take()
        }
    }
    val cols = LibraryNav.cols.coerceAtLeast(1)
    val f = ctx.focus.coerceIn(0, count - 1)
    val row = f / cols
    val col = f % cols
    val lastRow = (count - 1) / cols
    // Update 156 (review): every move adds its step to StyleLoop.walked ([d]: -1 / +1 going round), so with Endless the
    // grid counts on exactly (a ▼ onto a shorter last row could be taken as a step back round before).
    fun go(target: Int, d: Int = target - f): Boolean {
        if (target != f && target in 0 until count) {
            StyleLoop.walked += d
            ctx.setFocus(target)
            ctx.sound(UiSound.MOVE)
        }
        return ctx.take()
    }
    return when (ctx.key) {
        Key.DirectionUp -> when {
            row > 0 -> go(f - cols)
            StyleNav.top.isNotEmpty() -> ctx.enterZone(StyleNav.Zone.TOP)
            else -> ctx.reach(true)
        }
        Key.DirectionDown -> when {
            row >= lastRow -> ctx.reach(false)
            f + cols < count -> go(f + cols)
            // The row below is shorter: its last game.
            else -> go(count - 1)
        }
        Key.DirectionLeft -> when {
            col > 0 -> go(f - 1)
            // Into the panel only on a fresh press a moment after landing on the first column (as the other layouts do,
            // update 130), so a held ◀ or a quick second press doesn't shoot past the game into the buttons.
            StyleNav.side.isNotEmpty() -> if (ctx.first && StyleNav.dirAt - StyleNav.movedAt > 280) ctx.enterZone(StyleNav.Zone.SIDE) else ctx.take()
            // Update 156: Endless on, ◀ on the first game: on round to the last game (one step back, see LibraryGrid).
            f == 0 && libraryLoops(count) -> go(count - 1, -1)
            else -> go(f - 1)
        }
        // ▶ on the last column carries on to the first game of the next row.
        // Update 156: Endless on, ▶ on the last game: on round to the first game (one step on: the grid carries on
        // with the games again, see LibraryGrid).
        Key.DirectionRight -> if (f == count - 1 && libraryLoops(count)) go(0, 1) else go(f + 1)
        else -> false
    }
}

/** Update 156: Endless on for the Library panel (set as it draws) and at least two games. */
private fun libraryLoops(count: Int): Boolean = StyleLoop.on && count >= 2

/** The categories as tabs along the top: the one you're in lit (with its game count), L2 / R2 at the ends. */
@Composable
private fun LibraryTabs(c: StyleCtx, compact: Boolean, modifier: Modifier) {
    val step by rememberUpdatedState(c.actions.category)
    val stopNow by rememberUpdatedState(c.stop)
    val single = c.stop < 0 || c.stops.isEmpty()
    val singleNow by rememberUpdatedState(single)
    val tabs: List<Pair<String, ImageVector>> = if (single) listOf(c.categoryLabel to Icons.Filled.Apps) else c.stops.map { it.label to it.icon }
    CategoryTitle.Native() // (Update 212: the layout's own category title.)
    val current = if (single) 0 else c.stop
    val inTop = StyleNav.zone == StyleNav.Zone.TOP
    // The tab the D-pad is on while up there, else the category you're in: that one is kept in view.
    val lit = (if (inTop) StyleNav.index else current).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    LaunchedEffect(lit, tabs.size) {
        val info = list.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.index == lit }
        val whole = seen != null && seen.offset >= info.viewportStartOffset && seen.offset + seen.size <= info.viewportEndOffset
        if (!whole) runCatching { list.animateScrollToItem((lit - 1).coerceAtLeast(0)) }
    }
    val accent = ShelfTheme.accent
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LibraryHint("L2", compact) { step(-1) }
        Spacer(Modifier.width(6.dp))
        LazyRow(
            state = list, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs.size) { t ->
                val (label, icon) = tabs[t]
                val here = t == current
                val dpad = inTop && StyleNav.index == t
                val shape = RoundedCornerShape(50)
                Column(
                    Modifier
                        .clip(shape)
                        .background(if (dpad) Color.White.copy(alpha = 0.16f) else if (here) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .border(if (dpad) 2.dp else 0.dp, if (dpad) accent else Color.Transparent, shape)
                        .pointerInput(t) {
                            detectTapGestures {
                                StyleNav.tapped(StyleNav.Zone.TOP, t)
                                if (singleNow) step(1) else if (t != stopNow && stopNow >= 0) step(t - stopNow)
                            }
                        }
                        .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 3.dp else 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = if (here) accent else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(if (compact) 12.dp else 14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            label, color = Color.White.copy(alpha = if (here || dpad) 1f else 0.62f), fontSize = if (compact) 12.sp else 14.sp,
                            fontWeight = if (here) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
                            style = nameTextStyle(NameBackdrop.SHADOW)
                        )
                        if (here) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                c.games.size.toString(), color = accent, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, softWrap = false
                            )
                        }
                    }
                    // A thin accent line under the category you're in.
                    Box(Modifier.padding(top = 2.dp).width(if (here) 22.dp else 0.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(accent))
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        LibraryHint("R2", compact) { step(1) }
    }
}

/** The small L2 / R2 hint at either end of the tabs (a tap steps the category too). */
@Composable
private fun LibraryHint(text: String, compact: Boolean, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .pointerInput(Unit) { detectTapGestures { tap() } }
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * The panel on the left: the game you're on (its picture, name, console, three small boxes and a few lines about it)
 * swapping in as you move, with Play and Details pinned at the bottom so they never jump.
 */
@Composable
private fun LibraryPanel(c: StyleCtx, g: Game, compact: Boolean, roomy: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.stylePanel(Features.PanelLook.DARK, 0.92f, shape)) {
        androidx.compose.animation.AnimatedContent(
            targetState = g, label = "libraryPanel", transitionSpec = { gameSwap() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { shown -> LibraryPanelInfo(c, shown, compact, roomy) }
        LibraryButtons(c, g, compact)
    }
}

@Composable
private fun LibraryPanelInfo(c: StyleCtx, g: Game, compact: Boolean, roomy: Boolean) {
    val titles = LocalTitleOptions.current
    val open by rememberUpdatedState(c.actions.open)
    Box(Modifier.fillMaxSize()) {
        // The game's backdrop, faint, fading out down the top of the panel (a PC library's header).
        c.backdrop(g)?.let { url ->
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.55f)) {
                AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.32f })
                // (Update 151: fades into the panel's own dark, which now takes the theme's colour — see stylePanel.)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, themeDark(Color(0xFF0A0E15))))))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 8.dp else 12.dp)) {
            // The picture, as big as the room left over allows (a tap opens the game's window).
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().pointerInput(g.stableKey) { detectTapGestures { open() } },
                contentAlignment = Alignment.Center
            ) {
                // Portrait looks (covers, cases, cartridges, discs) show in the layout's own artwork; wide ones and the
                // spines show the cover instead, which fills a narrow panel better.
                val ownArt = !spinesOn(c.type) && styleAspect(c.type) < 1.2f
                val hasCover = c.cover(g) != null
                val aspect = if (ownArt) styleAspect(c.type) else if (hasCover) 0.72f else 1.78f
                val w = minOf(maxWidth, maxHeight * aspect)
                val h = w / aspect
                if (ownArt) {
                    StyleArt(c, g, Modifier.size(w, h), corner = 10.dp)
                } else {
                    Box(Modifier.size(w, h).clip(uiRounded(10.dp)).background(placeholderBrush(g.hue))) {
                        val url = if (hasCover) c.cover(g) else c.wide(g)
                        if (url != null) {
                            AsyncImage(url, g.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().pixelArtwork())
                        } else if (g.sourceType == com.shelf.launcher.data.SourceType.APP) {
                            AppIconArt(g.packageName, wide = false, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(
                                titles.display(g.name), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center,
                                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(8.dp)
                            )
                        }
                        ConsoleBadge(g.platform, packageName = g.packageName)
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            FitText(
                titles.display(g.name), color = Color.White, maxSize = if (compact) 15.sp else 19.sp, minSize = 11.sp,
                maxLines = 2, fontWeight = FontWeight.Bold
            )
            Text(
                titles.console(g.platform), color = ShelfTheme.accent, fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
            LibraryStats(c, g, compact)
            // A few lines about the game, where there's room.
            val summary = c.state.metadata[g.stableKey]?.summary?.trim()?.takeIf { it.isNotEmpty() }
            if (!compact && summary != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    summary, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, lineHeight = 15.sp,
                    maxLines = if (roomy) 3 else 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Time played, achievements (earned / total with a thin bar) and last played: three small boxes, or three lines when narrow. */
@Composable
private fun LibraryStats(c: StyleCtx, g: Game, compact: Boolean) {
    val played = stylePlaytime(c.playtime(g), c.lastPlayed(g) != null)
    val progress = c.progress(g)
    val ach = progress?.let { "${it.first} / ${it.second}" } ?: "None"
    val last = c.lastPlayed(g)?.let { lastPlayedLabel(it) } ?: "Never"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 250.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LibraryStatBox("PLAY TIME", played, null, compact, Modifier.weight(1f))
                LibraryStatBox("ACHIEVEMENTS", ach, progress, compact, Modifier.weight(1f))
                LibraryStatBox("LAST PLAYED", last, null, compact, Modifier.weight(1f))
            }
        } else {
            // A narrow panel (small screens): one line each, label left, value right.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                LibraryStatLine("PLAY TIME", played)
                LibraryStatLine("ACHIEVEMENTS", ach)
                if (progress != null) LibraryBar(progress)
                LibraryStatLine("LAST PLAYED", last)
            }
        }
    }
}

@Composable
private fun LibraryStatBox(label: String, value: String, progress: Pair<Int, Int>?, compact: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 7.dp, vertical = if (compact) 4.dp else 6.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        FitText(value, color = Color.White, maxSize = if (compact) 11.sp else 12.sp, minSize = 8.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
        if (progress != null) {
            Spacer(Modifier.height(3.dp))
            LibraryBar(progress)
        }
    }
}

@Composable
private fun LibraryStatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(6.dp))
        Text(
            value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
}

/** The thin achievements bar (gold once mastered). */
@Composable
private fun LibraryBar(progress: Pair<Int, Int>) {
    val (earned, total) = progress
    val share = if (total > 0) (earned.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(if (earned >= total) com.shelf.launcher.ui.theme.Ds.gold else ShelfTheme.accent))
    }
}

/** Play (accent) and Details, stacked: the panel's buttons (◀ from the first column, ▲ ▼ between them). */
@Composable
private fun LibraryButtons(c: StyleCtx, g: Game, compact: Boolean) {
    val play by rememberUpdatedState(c.actions.play)
    val open by rememberUpdatedState(c.actions.open)
    val game by rememberUpdatedState(g)
    Column(
        Modifier.fillMaxWidth().padding(start = if (compact) 8.dp else 12.dp, end = if (compact) 8.dp else 12.dp, bottom = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
    ) {
        LibraryButton("Play", Icons.Filled.PlayArrow, primary = true, on = StyleNav.on(StyleNav.Zone.SIDE, 0), compact = compact) {
            StyleNav.tapped(StyleNav.Zone.SIDE, 0); play(game)
        }
        LibraryButton("Details", Icons.Filled.Info, primary = false, on = StyleNav.on(StyleNav.Zone.SIDE, 1), compact = compact) {
            StyleNav.tapped(StyleNav.Zone.SIDE, 1); open()
        }
    }
}

@Composable
private fun LibraryButton(label: String, icon: ImageVector, primary: Boolean, on: Boolean, compact: Boolean, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val accent = ShelfTheme.accent
    val shape = RoundedCornerShape(8.dp)
    val ink = if (primary) onAccentColor() else Color.White
    Row(
        Modifier.fillMaxWidth().height(if (compact) 32.dp else 38.dp)
            .graphicsLayer { val s = if (on) 1.03f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .background(if (primary) accent else Color.White.copy(alpha = if (on) 0.2f else 0.1f))
            .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = if (primary) 0f else 0.22f), shape)
            .pointerInput(Unit) { detectTapGestures { tap() } },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = ink, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * The category's games as a grid (only the rows near the screen are composed). It glides up / down with the Moving
 * between games speed so the game you're on keeps about half a row of the games above and below it in view.
 */
@Composable
private fun LibraryGrid(c: StyleCtx, modifier: Modifier) {
    val keys = remember(c.games) { gameKeys(c.games) }
    val glide = Features.rowGlide.state.value
    // Spines would make cells many times taller than wide: they stand in a cell twice as tall as wide instead.
    val aspect = if (spinesOn(c.type)) 0.5f else styleAspect(c.type).coerceIn(0.5f, 1.9f)
    val corner = LocalCardLook.current.cornerDp.coerceAtMost(12).dp
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        // The layout's game size (Small / Medium / Large) gives fewer, bigger games per row or more, smaller ones;
        // never so many that a game is narrower than a thumb.
        val k = GameSize.scale(HomeLayout.LIBRARY).coerceAtLeast(0.5f)
        val maxCols = (maxWidth / 64.dp).toInt().coerceAtLeast(2)
        val cols = (Features.libCols.state.value / k).roundToInt().coerceIn(2, maxCols)
        SideEffect { LibraryNav.cols = cols }
        val vGap = if (maxHeight < 300.dp) 8.dp else 12.dp
        val pad = 10.dp
        val gapPx = with(density) { vGap.toPx() }
        // Update 156: Endless on (and more than one row of games): the games repeat down the grid, each round starting
        // on a new row, and the place you're on counts on round (libraryKey's steps, the ends included),
        // so going from the last game to the first just carries on a row down, like any ▶ — nothing jumps back up.
        val n = c.games.size
        val loop = loopRowOn(c) && n > cols
        val tap = remember { LoopTap() }
        // (Update 156, review: counted by the exact steps libraryKey made, StyleLoop.walked.)
        val v = rememberWalkedPlace(c.focus, n, loop, StyleLoop.walked, c.games, tap)
        // One round of the grid: its games and the empty cells that fill out its last row.
        val stride = ((n + cols - 1) / cols).coerceAtLeast(1) * cols
        val total = if (loop) stride * LOOP_COPIES else n
        val half = LOOP_COPIES / 2
        val focusCell = if (loop) ((half + Math.floorDiv(v, n)) * stride + v.mod(n)).coerceIn(0, total - 1) else c.focus
        val state = rememberLazyGridState(initialFirstVisibleItemIndex = ((focusCell / cols) - 1).coerceAtLeast(0) * cols)
        // The games and the games per row the grid was last moved for: when either changes (a new category, Small /
        // Large) the positions the grid last laid out belong to the old ones, so they can't be glided from.
        val shownFor = remember { arrayOfNulls<Any>(1) }
        LaunchedEffect(focusCell, cols, keys, loop) {
            // (Update 156: the cell you're on, which with Endless is in one of the rounds.)
            val f = focusCell
            if (f < 0 || f >= total) return@LaunchedEffect
            val row = f / cols
            val lastRow = (total - 1) / cols
            val sig = Triple(keys, cols, loop)
            val fresh = shownFor[0] != sig
            shownFor[0] = sig
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == f }
            val near = info.visibleItemsInfo.firstOrNull()
            val nearRow = (near?.index ?: 0) / cols
            if (fresh || near == null || (item == null && kotlin.math.abs(row - nearRow) > 3)) {
                // Far away (a new category, a jump): straight there, a row of context above it.
                state.scrollToItem((row - 1).coerceAtLeast(0) * cols)
                return@LaunchedEffect
            }
            val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = glide.stiffness)
            val h = (item ?: near).size.height
            val step = h + gapPx
            // A game just off the screen (a held ▲ ▼ outrunning the glide): where it is worked out from the first game
            // on screen (every row is as tall), so it still glides there instead of jumping.
            val top = item?.offset?.y?.toFloat() ?: (near.offset.y + (row - nearRow) * step)
            val delta = if (row == 0) {
                // Back on the top row: all the way up (the top row sits at 0 then).
                top
            } else {
                val bottom = top + h
                val topLimit = step * 0.45f
                val bottomLimit = info.viewportEndOffset - with(density) { pad.toPx() } - (if (row < lastRow) step * 0.45f else 0f)
                when {
                    top < topLimit -> top - topLimit
                    // (Never so far that the game's top goes above the limit, on a screen too short for both.)
                    bottom > bottomLimit -> minOf(bottom - bottomLimit, top - topLimit).coerceAtLeast(0f)
                    else -> 0f
                }
            }
            if (kotlin.math.abs(delta) > 0.5f) state.animateScrollBy(delta, spec)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols), state = state,
            modifier = Modifier.fillMaxSize().categorySlide(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(vGap),
            // Room for the glow round the edge games.
            contentPadding = PaddingValues(pad)
        ) {
            // Update 156: with Endless, cell i shows game i % stride of its round (the cells past the last game are empty).
            items(total, key = { i -> if (loop) { val j = i % stride; if (j < n) "${keys[j]}#${i / stride}" else "pad#$i" } else keys[i] }) { i ->
                val j = if (loop) i % stride else i
                if (j < n) {
                    LibraryTile(
                        c, j, aspect, corner, inGames, on = i == focusCell,
                        // A tap picks the round that was tapped (so the grid doesn't run off to another one).
                        onPick = if (loop) ({ tap.virtual = (i / stride - half) * n + j }) else null
                    )
                } else {
                    Spacer(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** One game in the grid: its card art, lifted and lit when you're on it, its name under it. */
@Composable
private fun LibraryTile(
    c: StyleCtx, i: Int, aspect: Float, corner: Dp, inGames: Boolean,
    // Update 156: with Endless only the cell you're on (not every round of the game) is lit; [onPick] runs before a tap selects.
    on: Boolean = i == c.focus, onPick: (() -> Unit)? = null
) {
    val g = c.games[i]
    val sel = on && inGames
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val pick by rememberUpdatedState(onPick) // (Update 156)
    val lift = styleLift(sel)
    // Only the game you're on runs the focus pulse, so the rest of the grid stays still.
    val pulse = if (sel) rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE) else null
    val titles = LocalTitleOptions.current
    Column(Modifier.zIndex(if (on) 1f else 0f)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .graphicsLayer {
                    val s = 1f + 0.06f * lift
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .then(if (on) Modifier.focusedCard() else Modifier)
                .pointerInput(i) {
                    detectTapGestures {
                        // A tap brings the D-pad back to the games too.
                        if (StyleNav.zone != StyleNav.Zone.GAMES) StyleNav.leave()
                        if (i == GridHome.focus) open() else { pick?.invoke(); select(i) } // (Update 156: pick)
                    }
                }
        ) {
            StyleArt(c, g, Modifier.fillMaxSize(), corner = corner, focused = sel, fx = pulse, ring = ShelfTheme.accent)
            // The D-pad is up in the tabs or in the panel: the game you're on keeps a quieter outline.
            if (on && !sel) Box(Modifier.matchParentSize().border(1.5.dp, ShelfTheme.accent.copy(alpha = 0.6f), uiRounded(corner)))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            titles.display(g.name), color = if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = nameTextStyle(NameBackdrop.SHADOW), modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Settings › Home › Layout options (Library panel). */
@Composable
internal fun LibrarySettings() {
    MutedNote("Like a PC game library: the game you're on in a panel on the left (its cover, time played, achievements, last played, a few lines about it, Play and Details), the category's games as a grid on the right, and the categories as tabs along the top. ▲ ▼ ◀ ▶ move in the grid, ◀ from the first column reaches Play and Details, ▲ from the top row the tabs (L2 / R2 change the category too).")
    Spacer(Modifier.height(10.dp))
    Text("Games per row", color = ShelfTheme.textMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    // Chips rather than a slider: the slider's D-pad steps are 5 at a time, too coarse for 3–8.
    ChipFlow { (3..8).forEach { n -> Chip("$n", accent = n == Features.libCols.state.value) { Features.libCols.value = n } } }
    Spacer(Modifier.height(12.dp))
    SwitchRow(
        Icons.Filled.ViewModule, "Game panel on the left",
        "The game you're on with its cover, time played, achievements and Play / Details. Off: the grid fills the width.",
        Features.libPanel.state.value, { Features.libPanel.value = it }
    )
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        Icons.Filled.Tab, "Category tabs along the top",
        "Every category as a tab, the one you're in lit with its game count. ▲ from the top row goes up to them.",
        Features.libTabs.state.value, { Features.libTabs.value = it }
    )
    Spacer(Modifier.height(8.dp))
}
