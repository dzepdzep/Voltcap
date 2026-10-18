package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › Spotlight: the game's art across the screen, small square tiles along the top (the one you're
 * on framed, its name beside it), its big title (or logo), Play, ⋯ and its activity cards.
 * ◀ ▶ move along the tiles; ▼ goes to Play / ⋯ / the cards (◀ ▶ there), ▲ / B come back.
 * Options: Appearance › Game row › Spotlight options.
 * Update 132: the Home menu look (the default): your profile, your shortcuts as icons and the time along the very top,
 * a row of big tiles with the game's name under the one you're on, and what the buttons do along the bottom.
 */
@Composable
internal fun StyleSpotlight(c: StyleCtx, modifier: Modifier) {
    // Update 125: the Row & menu look. Update 132: the Home menu look (the default).
    when (Features.spotLook.state.value) {
        Features.SpotLook.HOME_MENU -> SpotHomeMenu(c, modifier)
        Features.SpotLook.ROW_MENU -> SpotRowMenu(c, modifier)
        Features.SpotLook.CINEMA -> SpotCinema(c, modifier)
    }
}

@Composable
private fun SpotCinema(c: StyleCtx, modifier: Modifier) {
    val g = c.game
    val actions = c.actions
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE)
    // The shade over the art is drawn across the whole screen by StyleBackLayer.
    BoxWithConstraints(modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        // Everything (tabs, tiles, title, Play, cards) needs ~440 dp; below that the cards stay out and the title shrinks.
        val roomy = maxHeight >= 440.dp
        val cards = Features.spotCards.state.value && roomy
        // The D-pad's buttons are exactly the ones on screen.
        // The category names on top (update 101): ▲ reaches them; ◀ ▶ there step through the categories, A goes to the next.
        // Update 109: parts hidden in Edit Home are skipped by the D-pad.
        val tabNext = Features.spotTabs.state.value && c.stopAt(1) != null && c.stops.size > 1 && !LayoutParts.off(com.shelf.launcher.data.HomeLayout.SPOT, "tabs")
        val titleOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.SPOT, "title")
        val cardsOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.SPOT, "cards")
        SideEffect {
            StyleNav.side = emptyList()
            StyleNav.top = if (tabNext) listOf({ actions.category(1) }) else emptyList()
            StyleNav.bar = if (g == null) emptyList() else buildList<() -> Unit> {
                if (!titleOff) {
                    add { actions.play(g) }
                    add { actions.open() }
                }
                if (cards && !cardsOff) {
                    add { if (c.progress(g) != null) actions.achievements(g) else actions.open() }
                    add { actions.open() }
                    add { actions.open() }
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            if (Features.spotTabs.state.value) {
                val tabOn = StyleNav.zone == StyleNav.Zone.TOP
                Row(
                    Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "tabs"), "Category names").then(if (tabOn) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
                        .border(1.5.dp, Color.White, androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp) else Modifier),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (tabOn) Text("◀  ", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    // (Update 212: the layout's own category title — unless its tabs are hidden in Edit Home.)
                    CategoryTitle.Native(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "tabs"))
                    Text(c.categoryLabel, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW))
                    val next = c.stopAt(1)
                    if (next != null && c.stops.size > 1) {
                        Spacer(Modifier.width(28.dp))
                        Text(
                            next.label, color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW),
                            modifier = Modifier.pointerInput(Unit) { detectTapGestures { actions.category(1) } }
                        )
                    }
                    if (tabOn) Text("  ▶", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            }
            // Plain if / else, never an early return@Column (it unbalances Compose's groups, see AppIcons.kt).
            if (g == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                val tilesBottom = Features.spotTilesAt.state.value == Features.SpotSide.BOTTOM
                val tier = if (compact) 0 else if (roomy) 2 else 1
                // Tiles on top: tiles, then title, Play and cards. Tiles at the bottom: the other way round.
                // Update 104: the title block can sit left, centre or right, and near the tiles, in the middle or far
                // from them (Spotlight options › The game's name).
                val across = Features.spotTitleAt.state.value
                val along = Features.spotTitleRow.state.value
                val hAlign = when (across) { Features.SpotAcross.CENTER -> Alignment.CenterHorizontally; Features.SpotAcross.RIGHT -> Alignment.End; else -> Alignment.Start }
                val boxAlign = when (across) { Features.SpotAcross.CENTER -> Alignment.TopCenter; Features.SpotAcross.RIGHT -> Alignment.TopEnd; else -> Alignment.TopStart }
                // Room between the tiles and the title block (before) and after it.
                val before = when (along) { Features.SpotAlong.NEAR -> 0f; Features.SpotAlong.MIDDLE -> 1f; Features.SpotAlong.FAR -> 1f }
                val after = when (along) { Features.SpotAlong.NEAR -> 1f; Features.SpotAlong.MIDDLE -> 1f; Features.SpotAlong.FAR -> 0f }
                if (!tilesBottom) {
                    SpotTiles(c, pulse, compact)
                    if (before > 0f) Spacer(Modifier.weight(before)) else Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
                } else if (after > 0f) Spacer(Modifier.weight(after))
                Column(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "title"), "Title & Play"), horizontalAlignment = hAlign) {
                    AnimatedContent(
                        targetState = g, label = "spotTitle", contentAlignment = boxAlign,
                        transitionSpec = { gameSwap() }
                    ) { shown -> SpotTitle(c, shown, tier, boxAlign) }
                    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                    SpotActions(c, g, compact)
                }
                if (cards) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "cards"), "Activity cards")) { SpotCards(c, g) }
                }
                if (tilesBottom) {
                    if (before > 0f) Spacer(Modifier.weight(before)) else Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
                    SpotTiles(c, pulse, compact)
                } else if (after > 0f) Spacer(Modifier.weight(after))
                Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
            }
        }
    }
}

/** The small square tiles along the top: the one you're on framed and bigger, its name beside it. */
@Composable
private fun SpotTiles(c: StyleCtx, pulse: Float, compact: Boolean) {
    // One glide moves and resizes the row (AnchoredStrip): no running past and settling back.
    val keys = remember(c.games) { gameKeys(c.games) }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val shape = Features.spotShape.state.value
    val squares = shape == Features.TileShape.SQUARE
    val wideTiles = shape == Features.TileShape.WIDE
    // The R1 Size tile (update 104).
    val sizeK = GameSize.scale(com.shelf.launcher.data.HomeLayout.SPOT)
    val small = (if (compact) 50.dp else 66.dp) * sizeK
    val big = (if (compact) 72.dp else 100.dp) * sizeK
    val corner = 12.dp
    val aspect = when {
        squares -> 1f
        wideTiles -> 1.78f
        else -> styleAspect(c.type).coerceAtMost(1.8f)
    }
    val nameAt = Features.spotNameAt.state.value
    val beside = nameAt == Features.SpotName.RIGHT
    val stacked = nameAt == Features.SpotName.BELOW || nameAt == Features.SpotName.ABOVE
    val nameRoom = if (compact) 190.dp else 230.dp
    // Under / above the tile the name needs a line of its own, so the strip is a little taller.
    val lineRoom = if (stacked) (if (compact) 24.dp else 30.dp) else 0.dp
    AnchoredStrip(
        count = c.games.size, focus = rememberLoopFocus(c), keyOf = { keys[it] }, small = small, big = big,
        widthFor = { it * aspect }, gap = 10.dp, startPad = 14.dp, anchorSlots = 1, extraForFocus = if (beside) nameRoom else 0.dp, alignTop = stacked,
        loop = loopRowOn(c),
        modifier = Modifier.fillMaxWidth().height(big + 16.dp + lineRoom).padding(vertical = 8.dp).categorySlide()
    ) { i, grow, w, h, here ->
        val g = c.games[i]
        val on = here
        val sel = on && StyleNav.zone == StyleNav.Zone.GAMES
        val nameText: @Composable (Modifier) -> Unit = { mod ->
            Text(
                LocalTitleOptions.current.display(g.name), color = Color.White.copy(alpha = grow.coerceIn(0f, 1f)), fontSize = if (compact) 14.sp else 17.sp,
                maxLines = if (beside) 2 else 1, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW), modifier = mod
            )
        }
        val art: @Composable () -> Unit = {
            StyleArt(
                c, g,
                Modifier
                    .width(w).height(h)
                    .then(if (on) Modifier.focusedCard() else Modifier)
                    .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } },
                square = squares, wide = wideTiles, corner = corner, focused = sel, fx = pulse, ring = Color.White, outline = 3.dp
            )
        }
        if (stacked) {
            val above = nameAt == Features.SpotName.ABOVE
            // The strip gives each tile a box of exactly its own size: the name lives outside it (a box that lets its
            // content overflow), above the tile or under it, at the tile's left edge, so it never squashes the art.
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
                Box(Modifier.offset(y = if (above) lineRoom else 0.dp)) { art() }
                if (on || grow > 0.3f) nameText(Modifier.offset(y = if (above) 0.dp else h + 4.dp).width(nameRoom))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                art()
                if (beside && (on || grow > 0.3f)) {
                    Spacer(Modifier.width(14.dp))
                    nameText(Modifier.widthIn(max = nameRoom - 30.dp).padding(end = 16.dp))
                }
            }
        }
    }
}

/** The game's logo, else its name, big. */
@Composable
private fun SpotTitle(c: StyleCtx, g: Game, tier: Int, align: Alignment = Alignment.TopStart) {
    val logo = if (Features.spotLogo.state.value && LocalTitleOptions.current.showLogos) bigLogo(g, c.state) else null
    if (logo != null) {
        FadeInImage(
            logo, g.name, contentScale = ContentScale.Fit,
            alignment = when (align) { Alignment.TopCenter -> Alignment.Center; Alignment.TopEnd -> Alignment.CenterEnd; else -> Alignment.CenterStart },
            modifier = Modifier.heightIn(max = when (tier) { 0 -> 60.dp; 1 -> 84.dp; else -> 104.dp }).widthIn(max = 420.dp)
        )
    } else {
        Text(
            LocalTitleOptions.current.display(g.name), color = Color.White, fontSize = when (tier) { 0 -> 28.sp; 1 -> 36.sp; else -> 44.sp }, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(when (tier) { 0 -> 28.sp; 1 -> 36.sp; else -> 44.sp }), maxFontSize = when (tier) { 0 -> 28.sp; 1 -> 36.sp; else -> 44.sp }),
            lineHeight = androidx.compose.ui.unit.TextUnit(1.14f, androidx.compose.ui.unit.TextUnitType.Em), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = nameTextStyle(NameBackdrop.SHADOW),
            textAlign = when (align) { Alignment.TopCenter -> androidx.compose.ui.text.style.TextAlign.Center; Alignment.TopEnd -> androidx.compose.ui.text.style.TextAlign.End; else -> androidx.compose.ui.text.style.TextAlign.Start },
            modifier = Modifier.widthIn(max = 640.dp)
        )
    }
}

/** Play, ⋯ and the time played. */
@Composable
private fun SpotActions(c: StyleCtx, g: Game, compact: Boolean) {
    val play by rememberUpdatedState(c.actions.play)
    val open by rememberUpdatedState(c.actions.open)
    val accent = ShelfTheme.accent
    Row(verticalAlignment = Alignment.CenterVertically) {
        val onPlay = StyleNav.on(StyleNav.Zone.BAR, 0)
        Box(
            Modifier
                .graphicsLayer { val k = if (onPlay) 1.06f else 1f; scaleX = k; scaleY = k }
                .clip(CircleShape)
                .background(Color(0xFFE9E9EC))
                .border(if (onPlay) 3.dp else 0.dp, if (onPlay) accent else Color.Transparent, CircleShape)
                .pointerInput(g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 0); play(g) } }
                .padding(horizontal = if (compact) 28.dp else 44.dp, vertical = if (compact) 8.dp else 13.dp)
        ) {
            Text(if (c.lastPlayed(g) != null) "Continue" else "Play", color = Color(0xFF16181C), fontSize = if (compact) 15.sp else 19.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(16.dp))
        val onMore = StyleNav.on(StyleNav.Zone.BAR, 1)
        Box(
            Modifier
                .size(if (compact) 38.dp else 52.dp)
                .clip(CircleShape)
                .background(themeDark(Color(0xFF3A3D44)).copy(alpha = 0.9f)) // (update 151: themed)
                .border(if (onMore) 3.dp else 0.dp, if (onMore) accent else Color.Transparent, CircleShape)
                .pointerInput(g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 1); open() } },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MoreHoriz, "More", tint = Color.White) }
        Spacer(Modifier.width(22.dp))
        Text(stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW))
    }
}

/** Achievements / Time played / Last played. */
@Composable
private fun SpotCards(c: StyleCtx, g: Game) {
    val p = c.progress(g)
    val cards = listOf(
        "Achievements" to (p?.let { (e, t) -> "$e/$t · ${(e * 100) / t} %" } ?: "Game details") ,
        "Time played" to stylePlaytime(c.playtime(g), c.lastPlayed(g) != null),
        "Last played" to (c.lastPlayed(g)?.let { lastPlayedLabel(it) } ?: "Not yet")
    )
    val heads = listOf(if (p != null) "Trophies" else "Activity", "Activity", "Activity")
    // Update 110: with Title & Play taken off (Edit Home ✕) the cards are the first buttons on ▼.
    val first = if (LayoutParts.off(com.shelf.launcher.data.HomeLayout.SPOT, "title")) 0 else 2
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        cards.forEachIndexed { i, (title, line) ->
            val on = StyleNav.on(StyleNav.Zone.BAR, first + i)
            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 300.dp)
                    .clip(uiRounded(12.dp))
                    .background(themeDark(Color(0xFF1C1F25)).copy(alpha = 0.88f)) // (update 151: themed)
                    .border(if (on) 2.5.dp else 0.dp, if (on) ShelfTheme.accent else Color.Transparent, uiRounded(12.dp))
                    // Update 144: the card's action is looked up when it is tapped (the list is only refreshed after
                    // the screen is drawn, so reading it earlier ran the previous game's card), and the slot follows
                    // `first` so the right card lights up when Title & Play is hidden.
                    .pointerInput(i, first, g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, first + i); StyleNav.bar.getOrNull(first + i)?.invoke() } }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(heads[i], color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(title, color = Color.White, fontSize = 17.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(17.sp), maxFontSize = 17.sp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(line, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(12.sp), maxFontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(0.4f))
    }
}

// ───────────────────────────── Row & menu (update 125) ─────────────────────────────

/**
 * Spotlight's Row & menu look: a bar of round buttons (your round shortcuts) with your profile, points and the time on
 * top; the row of tiles under it, the game you're on big with its name beside it and Start under it; then Overview ·
 * Recent activity · Related and what the one you're on shows. ▲ the round buttons, ▼ Start and the three (◀ ▶ there).
 */
@Composable
private fun SpotRowMenu(c: StyleCtx, modifier: Modifier) {
    val g = c.game
    val actions = c.actions
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE)
    val icons = c.shortcuts.take(8)
    val related = remember(c.games, g?.stableKey) { if (g == null) emptyList() else c.games.filter { it.platform == g.platform && it.stableKey != g.stableKey }.take(8) }
    val barOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.SPOT, "title")
    // The achievements of the game you're on, once you rest on it a moment (for Overview and Recent activity).
    LaunchedEffect(g?.stableKey) { val gg = g ?: return@LaunchedEffect; kotlinx.coroutines.delay(500); actions.needAchievements(gg) }
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = icons.map { it.onClick }
        StyleNav.topStart = -1
        StyleNav.bar = if (g == null || barOff) emptyList() else listOf(
            { actions.play(g) },
            { actions.open() },
            { if (c.progress(g) != null) actions.achievements(g) else actions.open() },
            { related.firstOrNull()?.let { r -> c.games.indexOf(r).takeIf { it >= 0 }?.let { actions.select(it) } } ?: actions.open() }
        )
    }
    // The tab shown: the one the D-pad is on, else the last one picked (Overview to start with).
    val tabState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val navTab = if (StyleNav.zone == StyleNav.Zone.BAR && StyleNav.index in 1..3) StyleNav.index - 1 else -1
    SideEffect { if (navTab >= 0) tabState.intValue = navTab }
    val tab = if (navTab >= 0) navTab else tabState.intValue
    BoxWithConstraints(modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "tabs"), "Round buttons, profile & clock")) {
                SpotTopBar(c, icons, compact)
            }
            Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
            if (g == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                SpotTiles(c, pulse, compact)
                Column(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "title"), "Start & menu")) {
                    // Start under the big tile (the strip keeps the game you're on in the second place).
                    val sizeK = GameSize.scale(com.shelf.launcher.data.HomeLayout.SPOT)
                    val small = (if (compact) 50.dp else 66.dp) * sizeK
                    val big = (if (compact) 72.dp else 100.dp) * sizeK
                    val shape = Features.spotShape.state.value
                    val aspect = when (shape) { Features.TileShape.SQUARE -> 1f; Features.TileShape.WIDE -> 1.78f; else -> styleAspect(c.type).coerceAtMost(1.8f) }
                    val onStart = StyleNav.on(StyleNav.Zone.BAR, 0)
                    val play by rememberUpdatedState(actions.play)
                    Box(
                        // (On the first game of a row that doesn't go round there's no tile before it.)
                        Modifier.padding(start = 14.dp + (if (loopRowOn(c) || c.focus >= 1) small * aspect + 10.dp else 0.dp)).width(big * aspect)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(if (onStart) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.35f))
                            .border(if (onStart) 2.dp else 0.dp, if (onStart) Color.White else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .pointerInput(g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 0); play(g) } }
                            .padding(vertical = if (compact) 4.dp else 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (c.lastPlayed(g) != null) "Continue" else "Start", color = Color.White, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                    // Overview · Recent activity · Related.
                    val names = listOf("Overview", "Recent activity", "Related")
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 26.dp)) {
                        names.forEachIndexed { i, n ->
                            val on = StyleNav.on(StyleNav.Zone.BAR, i + 1)
                            val shown = tab == i
                            Column(
                                Modifier.pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, i + 1); tabState.intValue = i } },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    n, color = if (shown || on) Color.White else Color.White.copy(alpha = 0.6f), fontSize = if (compact) 13.sp else 16.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW)
                                )
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.width(if (on) 40.dp else 22.dp).height(2.dp).background(if (on) ShelfTheme.accent else if (shown) Color.White.copy(alpha = 0.7f) else Color.Transparent))
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    androidx.compose.animation.AnimatedContent(
                        targetState = tab to g.stableKey, label = "spotTab",
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }
                    ) { (t, _) ->
                        Box(Modifier.padding(start = 14.dp)) {
                            when (t) {
                                0 -> SpotOverview(c, g, compact)
                                1 -> SpotRecent(c, g, compact)
                                else -> SpotRelated(c, related, compact)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The bar on top: your round shortcuts as round buttons (▲), then your profile, points and the time. */
@Composable
private fun SpotTopBar(c: StyleCtx, icons: List<HomeShortcut>, compact: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        icons.forEachIndexed { i, s ->
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            val click by rememberUpdatedState(s.onClick)
            Box(
                Modifier.padding(end = 8.dp).size(if (compact) 30.dp else 36.dp)
                    .graphicsLayer { val k = if (on) 1.15f else 1f; scaleX = k; scaleY = k }
                    .clip(CircleShape)
                    .background(if (on) s.tint else Color.Black.copy(alpha = 0.35f))
                    .border(if (on) 2.dp else 1.dp, if (on) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                    .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, i); click() } },
                contentAlignment = Alignment.Center
            ) { Icon(s.icon, s.label, tint = Color.White, modifier = Modifier.size(if (compact) 16.dp else 19.dp)) }
        }
        // The name of the button you're on, beside them.
        val onIcon = if (StyleNav.zone == StyleNav.Zone.TOP) icons.getOrNull(StyleNav.index) else null
        if (onIcon != null) Text(onIcon.label, color = Color.White, fontSize = 12.sp, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW), modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.weight(1f))
        ConsoleProfile(c)
        Spacer(Modifier.width(16.dp))
        val (time, _) = rememberConsoleClock()
        Text(time, color = Color.White, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, style = nameTextStyle(NameBackdrop.SHADOW))
    }
}

/** Overview: time played, last played, achievements (with the progress bar) and the console. */
@Composable
private fun SpotOverview(c: StyleCtx, g: Game, compact: Boolean) {
    val p = c.progress(g)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SpotInfoCard("Time played", stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), compact)
        SpotInfoCard("Last played", c.lastPlayed(g)?.let { lastPlayedLabel(it) } ?: "Not yet", compact)
        if (p != null) SpotInfoCard("Achievements", "${p.first}/${p.second} · ${(p.first * 100) / p.second} %", compact, p.first.toFloat() / p.second)
        SpotInfoCard("Console", LocalTitleOptions.current.console(g.platform), compact)
    }
}

/** Recent activity: the achievements you earned last in this game (else when you last played it). */
@Composable
private fun SpotRecent(c: StyleCtx, g: Game, compact: Boolean) {
    val recent = c.state.raAchievementDetails[g.stableKey].orEmpty().filter { it.isUnlocked }
        .sortedByDescending { it.dateEarned ?: it.dateEarnedHardcore ?: "" }.take(4)
    if (recent.isEmpty()) {
        SpotInfoCard("Last played", c.lastPlayed(g)?.let { lastPlayedLabel(it) } ?: "Not yet", compact)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        recent.forEach { a ->
            Row(
                Modifier.widthIn(max = 230.dp).clip(uiRounded(8.dp)).background(themeDark(Color(0xFF1C1F25)).copy(alpha = 0.85f)).padding(8.dp), // (update 151: themed)
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.shelf.launcher.data.RaBadges.url(a.badgeName)?.let { AsyncImage(it, null, modifier = Modifier.size(if (compact) 28.dp else 36.dp).clip(uiRounded(5.dp))) }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(a.title ?: "Achievement", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Unlocked", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Related: more games for the same console (a tap picks one). */
@Composable
private fun SpotRelated(c: StyleCtx, related: List<Game>, compact: Boolean) {
    if (related.isEmpty()) { Text("Nothing else for this console yet.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp); return }
    val select by rememberUpdatedState(c.actions.select)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        related.forEach { r ->
            val idx = c.games.indexOf(r)
            StyleArt(c, r, Modifier.size(if (compact) 50.dp else 66.dp).pointerInput(idx) { detectTapGestures { if (idx >= 0) select(idx) } }, square = true, corner = 6.dp)
        }
    }
}

@Composable
private fun SpotInfoCard(head: String, value: String, compact: Boolean, bar: Float? = null) {
    Column(
        Modifier.widthIn(min = 110.dp, max = 220.dp).clip(uiRounded(8.dp)).background(themeDark(Color(0xFF1C1F25)).copy(alpha = 0.85f)) // (update 151: themed)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 10.dp)
    ) {
        Text(head, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (bar != null) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(90.dp).height(3.dp).clip(uiRounded(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).height(3.dp).background(ShelfTheme.accent))
            }
        }
    }
}

// ───────────────────────────── Home menu (update 132) ─────────────────────────────

/** One button hint along the bottom: the button, what it does, and the same as a tap / A on it. */
private class SpotHint(val glyph: String, val label: String, val run: () -> Unit)

/**
 * Spotlight's Home menu: like a console's home menu. Along the very top your profile, your shortcuts as icons (the name of
 * the one you're on under it, in the line that runs under them) and the battery and the time; then a row of big tiles,
 * the game's name in capitals under the one you're on; along the bottom what the buttons do (each one also a button).
 * ▲ the icons (◀ ▶ along them), ▼ the hints (◀ ▶ along them), B or the way back to the games.
 */
@Composable
private fun SpotHomeMenu(c: StyleCtx, modifier: Modifier) {
    val g = c.game
    val actions = c.actions
    val pulse = rememberFocusPulse(animate = !LocalLiteEffects.current && LocalCardLook.current.focusEffect != FocusEffect.NONE)
    val icons = c.shortcuts.take(8)
    val hintsOn = Features.spotHints.state.value && !LayoutParts.off(com.shelf.launcher.data.HomeLayout.SPOT, "title")
    // What A does on the game (the same as the home screen's A): the game window, the classic details, or straight in.
    val aWord = when {
        !Features.gameWindow.state.value -> if (g != null && c.lastPlayed(g) != null) "Continue" else "Play"
        com.shelf.launcher.data.HomeLayout.SPOT.name in Features.classicWindowLayouts.state.value -> "Details"
        else -> "Open"
    }
    val running = g != null && g.stableKey in com.shelf.launcher.data.RunningGames.keys
    val favourite = g != null && g.stableKey in c.state.favorites
    val hints: List<SpotHint> = if (g == null || !hintsOn) emptyList() else buildList {
        add(SpotHint("A", aWord) { actions.open() })
        add(SpotHint("X", when { running -> "Close game"; favourite -> "Remove favourite"; else -> "Favourite" }) { actions.favorite(g) })
        if (g.raGameId != null) add(SpotHint("Y", "Achievements") { actions.achievements(g) })
        add(SpotHint("START", "Options") { actions.options(g) })
        add(SpotHint("SELECT", "Menu") { actions.settings() })
    }
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = icons.map { it.onClick }
        StyleNav.topStart = -1
        StyleNav.bar = hints.map { it.run }
    }
    BoxWithConstraints(modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        val compact = maxHeight < 380.dp
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "tabs"), "Icons, profile & clock")) {
                SpotMenuTop(c, icons, compact, ownStatus = !Features.spotStatusBar.state.value)
            }
            Spacer(Modifier.height(if (compact) 10.dp else 24.dp))
            if (g == null) {
                StyleEmpty(Modifier.weight(1f))
            } else if (Features.spotTilesAt.state.value == Features.SpotSide.BOTTOM) {
                // (Update 219, #08: "Tiles sit at the bottom" — R1's Spotlight tile and Spotlight options — did nothing on
                // the Home menu, the default look. The tiles now sit low, right above the button hints; ▲ / ▼ stay the
                // same: the icons are still above the tiles and the hints still under them.)
                Spacer(Modifier.weight(1f))
                SpotMenuTiles(c, pulse, compact)
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            } else {
                SpotMenuTiles(c, pulse, compact)
                Spacer(Modifier.weight(1f))
            }
            if (hints.isNotEmpty()) {
                Box(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.SPOT, "title"), "Button hints")) {
                    SpotHints(hints, compact)
                }
            }
        }
    }
}

/**
 * The very top: your profile, your shortcuts as plain icons spread across, and the battery and the time. A thin line runs
 * under them all; the name of the icon you're on sits in a gap in that line, right under it.
 */
@Composable
private fun SpotMenuTop(c: StyleCtx, icons: List<HomeShortcut>, compact: Boolean, ownStatus: Boolean) {
    val iconSize = if (compact) 24.dp else 30.dp
    val rowH = if (compact) 40.dp else 52.dp
    val labelH = if (compact) 18.dp else 24.dp
    val lineInk = Color.White.copy(alpha = 0.45f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Where the name of the icon you're on is (x from / to in this bar, px), so the line leaves a gap for it.
    var gap by remember { androidx.compose.runtime.mutableStateOf<Pair<Float, Float>?>(null) }
    var barX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val onIndex = if (StyleNav.zone == StyleNav.Zone.TOP) StyleNav.index else -1
    Box(Modifier.fillMaxWidth().onGloballyPositioned { barX = it.positionInWindow().x }) {
        // The line under the icons, with its gap.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val y = with(density) { (rowH + labelH / 2).toPx() }
            val w = with(density) { 1.dp.toPx() }
            val cut = gap?.takeIf { onIndex >= 0 }
            val pad = with(density) { 8.dp.toPx() }
            if (cut == null) drawLine(lineInk, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), w)
            else {
                if (cut.first - pad > 0f) drawLine(lineInk, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(cut.first - pad, y), w)
                if (cut.second + pad < size.width) drawLine(lineInk, androidx.compose.ui.geometry.Offset(cut.second + pad, y), androidx.compose.ui.geometry.Offset(size.width, y), w)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.height(rowH).widthIn(max = 240.dp), contentAlignment = Alignment.CenterStart) { ConsoleProfile(c) }
            Spacer(Modifier.width(12.dp))
            icons.forEachIndexed { i, s ->
                val on = i == onIndex
                val click by rememberUpdatedState(s.onClick)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.height(rowH).fillMaxWidth().pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, i); click() } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            s.icon, s.label, tint = if (on) Color.White else Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(iconSize).graphicsLayer { val k = if (on) 1.25f else 1f; scaleX = k; scaleY = k }
                        )
                    }
                    Box(Modifier.height(labelH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (on) Text(
                            s.label.uppercase(), color = Color.White, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW),
                            // As wide as its words (it may reach past this icon's share of the bar), centred under the icon.
                            modifier = Modifier
                                .layout { m, cons ->
                                    val p = m.measure(cons.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
                                    val w = cons.maxWidth
                                    layout(w, p.height) { p.place((w - p.width) / 2, 0) }
                                }
                                .onGloballyPositioned { val x = it.positionInWindow().x; gap = (x - barX) to (x + it.size.width - barX) }
                        )
                    }
                }
            }
            if (icons.isEmpty()) Spacer(Modifier.weight(1f))
            if (ownStatus) {
                Spacer(Modifier.width(12.dp))
                val (time, _) = rememberConsoleClock()
                val battery = rememberBatteryInfo()
                Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    Icon(batteryIconFor(battery.percent, battery.charging), null, tint = if (battery.percent < 15 && !battery.charging) LowBatteryColor else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(if (compact) 16.dp else 19.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(time, color = Color.White, fontSize = if (compact) 16.sp else 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW))
                }
            }
        }
    }
}

/** The row of big tiles: the one you're on bigger and framed, its name in capitals centred under it. */
@Composable
private fun SpotMenuTiles(c: StyleCtx, pulse: Float, compact: Boolean) {
    val keys = remember(c.games) { gameKeys(c.games) }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val shape = Features.spotShape.state.value
    val squares = shape == Features.TileShape.SQUARE
    val wideTiles = shape == Features.TileShape.WIDE
    val sizeK = GameSize.scale(com.shelf.launcher.data.HomeLayout.SPOT)
    val small = (if (compact) 60.dp else 84.dp) * sizeK
    val big = (if (compact) 84.dp else 120.dp) * sizeK
    val aspect = when {
        squares -> 1f
        wideTiles -> 1.78f
        else -> styleAspect(c.type).coerceAtMost(1.8f)
    }
    // Two lines for the name under the tile you're on.
    val lineRoom = if (compact) 38.dp else 50.dp
    AnchoredStrip(
        count = c.games.size, focus = rememberLoopFocus(c), keyOf = { keys[it] }, small = small, big = big,
        widthFor = { it * aspect }, gap = 8.dp, startPad = 4.dp, anchorSlots = 1, alignTop = true,
        loop = loopRowOn(c),
        modifier = Modifier.fillMaxWidth().height(big + 16.dp + lineRoom).padding(vertical = 8.dp).categorySlide()
    ) { i, grow, w, h, here ->
        val g = c.games[i]
        val sel = here && StyleNav.zone == StyleNav.Zone.GAMES
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
            StyleArt(
                c, g,
                Modifier
                    .width(w).height(h)
                    .then(if (here) Modifier.focusedCard() else Modifier)
                    .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } },
                square = squares, wide = wideTiles, corner = 4.dp, focused = sel, fx = pulse, ring = Color.White, outline = 3.dp
            )
            if (here || grow > 0.3f) {
                // Update 136: drawn straight under the tile (measured here, at a fixed width) instead of as a Text laid out
                // past the tile's own box — long names that needed two lines never showed up that way.
                val nameW = maxOf(w + 60.dp, 160.dp)
                val nameText = LocalTitleOptions.current.display(g.name).uppercase()
                val nameStyle = nameTextStyle(NameBackdrop.SHADOW).merge(
                    androidx.compose.ui.text.TextStyle(
                        color = Color.White, fontSize = if (compact) 14.sp else 18.sp, lineHeight = if (compact) 16.sp else 21.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
                // (Update 211: a Text placed under the tile by a zero-size layout, at a fixed width and any height — the
                // name drawn into the tile's spacer (update 136) still never showed when it needed two lines, e.g.
                // "Pokémon - Silver Version" or Yu-Gi-Oh! on the RP Classic; one-line names did.)
                Text(
                    nameText, style = nameStyle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .layout { m, _ ->
                            val wPx = nameW.roundToPx()
                            val pl = m.measure(androidx.compose.ui.unit.Constraints(minWidth = wPx, maxWidth = wPx))
                            layout(0, 0) { pl.placeWithLayer(((w.toPx() - wPx) / 2f).toInt(), (h + 8.dp).roundToPx()) { alpha = grow.coerceIn(0f, 1f) } }
                        }
                )
            }
        }
    }
}

/** What the buttons do, along the bottom: the button, then its word. Each one is also a button (tap, or ▼ then A). */
@Composable
private fun SpotHints(hints: List<SpotHint>, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, bottom = if (compact) 2.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 26.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        hints.forEachIndexed { i, h ->
            val on = StyleNav.on(StyleNav.Zone.BAR, i)
            val run by rememberUpdatedState(h.run)
            Row(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .border(if (on) 1.5.dp else 0.dp, if (on) Color.White else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, i); run() } }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.graphicsLayer { val k = if (compact) 1.1f else 1.35f; scaleX = k; scaleY = k }) { GlyphPill(h.glyph) }
                Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                Text(
                    h.label.uppercase(), color = Color.White, fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW)
                )
            }
        }
    }
}
