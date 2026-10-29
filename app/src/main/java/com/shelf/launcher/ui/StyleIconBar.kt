package com.shelf.launcher.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.FocusEffect
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Home layout › Icon bar, in the PS5 home's manner: your shortcuts as plain text tabs along the very top; a row of small
 * square tiles under them, the game you're on always at the left and popped out bigger (the others stay small, close
 * together), its name beside it under the small tiles; the game's art behind everything; its logo (or name, big),
 * a line about it, "Play Game" and ••• lower down at the left, the time played at the right.
 * ◀ ▶ move along the tiles; ▲ goes up to the tabs (◀ ▶ there), ▼ / B come back. ▼ from the tiles: Play / Continue and
 * the round buttons (◀ ▶ along them, ▲ / B back); past them the round shortcuts / music player (HomeReach).
 * (Update 235, #08: with Games sit at the Bottom the tiles go along the bottom and the title & Start sit just above them,
 * so ▲ from the tiles is Play and ▲ again the tabs; see StyleHomes.kt's BAR keys.)
 * Options: Settings › Home › Layout options (Icon bar).
 */
@Composable
internal fun StyleIconBar(c: StyleCtx, modifier: Modifier) {
    val tabsOn = Features.barIcons.state.value
    val currentOnly = tabsOn && Features.barTabsCurrent.state.value
    val icons = if (tabsOn && !currentOnly) c.shortcuts else emptyList()
    val stepCategory by rememberUpdatedState(c.actions.category)
    val g0 = c.game
    // Update 109: the title & Start part hidden in Edit Home is skipped by the D-pad.
    val startOn = Features.barStart.state.value && g0 != null && !LayoutParts.off(com.shelf.launcher.data.HomeLayout.BAR, "title")
    SideEffect {
        StyleNav.side = emptyList()
        // ▼ (update 101): Play / Continue, the game's settings, its save sync and its achievements, as drawn under its name.
        StyleNav.bar = if (!startOn || g0 == null) emptyList() else listOf(
            { c.actions.play(g0) }, { c.actions.options(g0) }, { c.actions.sync(g0) }, { c.actions.achievements(g0) }
        )
        // Only the category you're in: A on the tab goes to the next category (◀ ▶ step through them).
        StyleNav.top = if (currentOnly) listOf({ stepCategory(1) }) else icons.map { it.onClick }
    }
    BoxWithConstraints(modifier.padding(horizontal = 12.dp)) {
        val compact = maxHeight < 380.dp
        val roomy = maxHeight >= 430.dp
        Column(Modifier.fillMaxSize()) {
            if (currentOnly) {
                BarCategoryTab(c, compact)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            } else if (icons.isNotEmpty()) {
                BarTabs(icons, compact)
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            }
            val g = c.game
            // Plain if / else, never an early return@Column (it unbalances Compose's groups, see AppIcons.kt).
            if (g == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                val k = Features.barTile.state.value / 100f * if (compact) 0.82f else 1f
                val small = 50.dp * k
                val pop = Features.barGrow.state.value / 100f
                // (Update 235, #08: Games sit at the Bottom — free room under the tabs, then the title & Start, then the
                // tiles along the bottom; Top keeps the tiles under the tabs and the title lower down, as before.)
                val gamesLow = Features.barGamesAt.state.value == Features.SpotSide.BOTTOM
                if (!gamesLow) {
                    BarTiles(c, small, pop)
                    Spacer(Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                // The next game's title rises into place (update 104).
                if (Features.barStart.state.value) androidx.compose.animation.AnimatedContent(targetState = g, label = "barTitle", transitionSpec = { gameSwap() }) { shown ->
                    BarTitle(c, shown, compact, roomy)
                }
                if (gamesLow) {
                    BarTiles(c, small, pop)
                    Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                }
            }
        }
    }
}

/** The one tab: the category you're in, white and bold, with ◀ ▶ (tap, or A on the tab) to step through the categories. */
@Composable
private fun BarCategoryTab(c: StyleCtx, compact: Boolean) {
    val step by rememberUpdatedState(c.actions.category)
    val on = StyleNav.on(StyleNav.Zone.TOP, 0)
    Row(
        Modifier.padding(start = 2.dp)
            .then(if (on) Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).border(1.5.dp, Color.White, CircleShape).padding(horizontal = 12.dp, vertical = 3.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text("◀", color = Color.White.copy(alpha = if (on) 0.9f else 0.45f), fontSize = if (compact) 12.sp else 14.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures { step(-1) } })
        // (Update 212: the layout's own category title, CategoryTitle.kt.)
        CategoryTitle.Native()
        FullText(
            c.categoryLabel, color = Color.White.copy(alpha = if (on) 1f else 0.92f), fontSize = if (compact) 15.sp else 19.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1,
            style = nameTextStyle(NameBackdrop.SHADOW),
            modifier = Modifier.weight(1f, fill = false).pointerInput(Unit) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, 0); step(1) } }
        )
        Text(c.games.size.toString(), color = ShelfTheme.accent, fontSize = if (compact) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold)
        Text("▶", color = Color.White.copy(alpha = if (on) 0.9f else 0.45f), fontSize = if (compact) 12.sp else 14.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures { step(1) } })
    }
}

/** The shortcuts as plain text tabs (like "Games  Media"): the one you're on white and bold, the others dimmed; no icons, no underline. */
@Composable
private fun BarTabs(icons: List<HomeShortcut>, compact: Boolean) {
    // (Update 294: whole tabs only, "+N ›" for the rest, the one you're on always whole — WholeChipRow.)
    val lit = if (StyleNav.zone == StyleNav.Zone.TOP) StyleNav.index else -1
    WholeChipRow(
        icons.size, lit, Modifier.fillMaxWidth(), spacing = if (compact) 12.dp else 18.dp, startPadding = 2.dp,
        hintSize = if (compact) 13.sp else 15.sp
    ) { i ->
            val s = icons[i]
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            val click by rememberUpdatedState(s.onClick)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, i); click() } }
            ) {
                Text(
                    s.label, color = Color.White.copy(alpha = if (on) 1f else 0.6f), fontSize = if (compact) 15.sp else 19.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false,
                    textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                    style = nameTextStyle(NameBackdrop.SHADOW)
                )
            }
    }
}

/**
 * The tiles: the game you're on is always the first one at the left, popped out to [pop] times the others' size; the
 * row scrolls under it as you move (nothing jumps sideways). Its name sits beside it, under the small tiles.
 */
@Composable
private fun BarTiles(c: StyleCtx, small: androidx.compose.ui.unit.Dp, pop: Float) {
    // The game you're on stays at the left and pops out; one glide moves and resizes everything (AnchoredStrip),
    // so the row no longer runs too far left and then settles back.
    val keys = remember(c.games) { gameKeys(c.games) }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val squares = Features.barSquares.state.value
    val names = Features.barNames.state.value
    val corner = LocalCardLook.current.cornerDp.coerceAtMost(8).dp
    val big = small * pop
    val gap = 6.dp
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    val aspect = if (squares) 1f else styleAspect(c.type).coerceAtMost(1.8f)
    // With the tiles all one size (grow 100) there is no room beside the big one: the name gets its own line under the row.
    val nameRoom = if (big - small < 16.dp) 18.dp else 0.dp
    Box(Modifier.fillMaxWidth().height(big + 6.dp + nameRoom)) {
        AnchoredStrip(
            count = c.games.size, focus = rememberLoopFocus(c), keyOf = { keys[it] }, small = small, big = big,
            widthFor = { it * aspect }, gap = gap, startPad = 2.dp, loop = loopRowOn(c),
            modifier = Modifier.fillMaxWidth().height(big + 6.dp).categorySlide()
        ) { i, grow, w, h, here ->
            val g = c.games[i]
            val on = here
            val sel = on && inGames
            Box(
                Modifier
                    .width(w).height(h)
                    .then(if (on) Modifier.focusedCard() else Modifier)
                    .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } }
            ) {
                StyleArt(c, g, Modifier.fillMaxSize(), square = squares, corner = corner, focused = sel, fx = null)
                if (sel) Box(Modifier.matchParentSize().border(2.dp, Color.White.copy(alpha = grow.coerceIn(0.4f, 1f)), uiRounded(corner)))
                if (names && grow < 0.5f) Box(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).clip(uiRounded(corner))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    FitText(LocalTitleOptions.current.display(g.name), color = Color.White, maxSize = 8.sp, minSize = 6.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                }
            }
        }
        // The name beside the big tile, under the small ones (drawn over the row, so it never moves the tiles).
        c.game?.let { g ->
            val bigW = big * aspect
            androidx.compose.animation.AnimatedContent(
                targetState = g.name, label = "barName",
                transitionSpec = spec@{ categoryQuiet()?.let { return@spec it }; androidx.compose.animation.ContentTransform(androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)), androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))) },
                // Update 144: on its own line the name goes under the big tile, not under the small ones (it was drawn over the art).
                modifier = Modifier.padding(start = if (nameRoom > 0.dp) 4.dp else 2.dp + bigW + 10.dp, top = (if (nameRoom > 0.dp) big else small) + 6.dp)
            ) { name ->
                // Update 112: on a busy picture the small name sits on a dark pill (Dock › Names over pictures).
                val pill = Features.nameContrast.state.value == Features.NameContrast.PILL
                Text(
                    LocalTitleOptions.current.display(name), color = Color.White, fontSize = 12.sp, maxLines = 1, softWrap = false,
                    style = nameTextStyle(NameBackdrop.SHADOW),
                    modifier = if (pill) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.58f)).padding(horizontal = 8.dp, vertical = 2.dp) else Modifier
                )
            }
        }
    }
}

/** Lower left, like the console: the logo (or the name, big), a line about the game, Play Game and •••; time at the right. */
@Composable
private fun BarTitle(c: StyleCtx, g: com.shelf.launcher.data.Game, compact: Boolean, roomy: Boolean) {
    val titles = LocalTitleOptions.current
    val logo = if (titles.showLogos) c.logo(g) else null
    Box(Modifier.fillMaxWidth().padding(bottom = if (compact) 8.dp else 18.dp).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.BAR, "title"), "Title & Start")) {
        Column(Modifier.fillMaxWidth(0.62f).padding(start = 6.dp)) {
            if (logo != null) {
                // The scraped logo (SteamGridDB), then the game's name under it.
                coil.compose.AsyncImage(
                    logo, titles.display(g.name), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.height(if (compact) 52.dp else 76.dp).fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                FitText(
                    titles.display(g.name), color = Color.White, maxSize = if (compact) 14.sp else 18.sp, minSize = 10.sp,
                    maxLines = 1, fontWeight = FontWeight.Medium, style = nameTextStyle(NameBackdrop.SHADOW)
                )
            } else {
                FitText(
                    titles.display(g.name).uppercase(), color = Color.White, maxSize = if (compact) 22.sp else 32.sp, minSize = 13.sp,
                    maxLines = 2, fontWeight = FontWeight.Light, style = nameTextStyle(NameBackdrop.SHADOW)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                titles.console(g.platform) + (c.lastPlayed(g)?.let { " · Last played: " + lastPlayedLabel(it) } ?: ""),
                color = Color.White.copy(alpha = 0.85f), fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, softWrap = false,
                style = nameTextStyle(NameBackdrop.SHADOW)
            )
            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val play by rememberUpdatedState(c.actions.play)
                val open by rememberUpdatedState(c.actions.open)
                val playOn = StyleNav.on(StyleNav.Zone.BAR, 0)
                Box(
                    Modifier
                        .graphicsLayer { val k = if (playOn) 1.06f else 1f; scaleX = k; scaleY = k }
                        .clip(uiRounded(8.dp)).background(if (playOn) Color.White else Color.White.copy(alpha = 0.16f))
                        .border(if (playOn) 2.dp else 1.dp, Color.White.copy(alpha = if (playOn) 1f else 0.35f), uiRounded(8.dp))
                        .pointerInput(g.stableKey) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 0); play(g) } }
                        .padding(horizontal = 26.dp, vertical = 8.dp)
                ) { Text(if (c.lastPlayed(g) != null) "Continue" else "Play", color = if (playOn) Color(0xFF15181E) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false) }
                Spacer(Modifier.width(10.dp))
                // The game's own settings, its save sync and its achievements.
                val options by rememberUpdatedState(c.actions.options)
                val sync by rememberUpdatedState(c.actions.sync)
                val trophies by rememberUpdatedState(c.actions.achievements)
                BarRound(Icons.Filled.Settings, on = StyleNav.on(StyleNav.Zone.BAR, 1)) { StyleNav.tapped(StyleNav.Zone.BAR, 1); options(g) }
                Spacer(Modifier.width(8.dp))
                BarRound(Icons.Filled.CloudSync, on = StyleNav.on(StyleNav.Zone.BAR, 2)) { StyleNav.tapped(StyleNav.Zone.BAR, 2); sync(g) }
                Spacer(Modifier.width(8.dp))
                BarRound(Icons.Filled.EmojiEvents, tint = Color(0xFFE8BE48), on = StyleNav.on(StyleNav.Zone.BAR, 3)) { StyleNav.tapped(StyleNav.Zone.BAR, 3); trophies(g) }
            }
            // A small achievements card under it, where there's room (like the console's activity cards).
            if (roomy) c.progress(g)?.let { (earned, total) ->
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(uiRounded(8.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.EmojiEvents, null, tint = Color(0xFFE8BE48), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Achievements  $earned / $total", color = Color.White, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        // Time played, at the right like the console's clock chip.
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp).clip(uiRounded(50)).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("◷ " + stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), color = Color.White, fontSize = 11.sp, maxLines = 1, softWrap = false)
        }
    }
}


/** A small round button next to Play (settings, sync, trophies). */
@Composable
private fun BarRound(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, on: Boolean = false, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    Box(
        Modifier.size(34.dp)
            .graphicsLayer { val k = if (on) 1.12f else 1f; scaleX = k; scaleY = k }
            .clip(CircleShape).background(Color.White.copy(alpha = if (on) 0.32f else 0.14f))
            .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
            .pointerInput(Unit) { detectTapGestures { click() } },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
}
