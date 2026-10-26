package com.shelf.launcher.ui

import com.shelf.launcher.data.statusOn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import kotlin.math.roundToInt
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.NameBackdrop
import com.shelf.launcher.data.RaBadges
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Three more style homes, laid out like the big consoles' home screens (with Voltcap's own look, never their
 * names or logos):
 *  - Console hub (StyleConsoleHub): the game's art full screen, profile / shortcuts / clock along the top, a row of
 *    tiles with the game you're on big at the left, and three wide cards (Play, Achievements, Details) under them.
 *  - Resume & discover (StyleConsoleResume): the game you're on as a big square at the left with a 2×2 block of
 *    shortcut tiles under it, the next games in a row beside it and "Discover" cards under that.
 *  - Channel grid (StyleChannelGrid): pages of wide tiles on a pale backdrop, a curved bar at the bottom with the
 *    clock and a round button at each side.
 * Keys and zones work as on the other style homes (StyleHomes.kt). Options: StyleHomeSettings.kt.
 */

/** Time and date text that follows the clock. */
@Composable
internal fun rememberConsoleClock(): Pair<String, String> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(10_000)
        }
    }
    val minute = now / 60_000L
    val time = remember(minute) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(minute * 60_000L)) }
    val date = remember(minute) { java.text.SimpleDateFormat("EEE d/M", java.util.Locale.getDefault()).format(java.util.Date(minute * 60_000L)) }
    return time to date
}

/** Avatar, name and points (RetroAchievements), else "Player". */
@Composable
internal fun ConsoleProfile(c: StyleCtx, ink: Color = Color.White) {
    val profile = c.state.raProfile
    val name = profile?.User ?: c.state.raUsername ?: "Player"
    val avatar = profilePictureUrl(RaBadges.mediaUrl(profile?.UserPic))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(ink.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (avatar != null) AsyncImage(avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.Person, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW))
            profile?.TotalPoints?.let { Text("G  $it", color = ink.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, softWrap = false) }
        }
    }
}

/** Clock and battery, small, at a corner. */
@Composable
private fun ConsoleStatus(ink: Color = Color.White) {
    val (time, _) = rememberConsoleClock()
    val battery = rememberBatteryInfo()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(batteryIconFor(battery.percent, battery.charging), null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(time, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW))
    }
}

/** What a wide card shows and does. [next]: the achievements still to earn, sliding past behind it (update 104). */
private class CardSpec(
    val title: String, val sub: String?, val icon: ImageVector, val tint: Color, val art: String?,
    val next: List<com.shelf.launcher.data.RaAchievementDetail> = emptyList(), val act: () -> Unit
)

/** The achievements still to earn, the most earned by other players first (the likeliest next ones). */
private fun nextAchievements(c: StyleCtx, g: Game): List<com.shelf.launcher.data.RaAchievementDetail> =
    c.state.raAchievementDetails[g.stableKey].orEmpty().filter { !it.isUnlocked }
        .sortedWith(compareByDescending<com.shelf.launcher.data.RaAchievementDetail> { it.unlockPercent ?: -1f }.thenBy { it.displayOrder ?: Int.MAX_VALUE })
        .take(12)

/** Play / Achievements / Details for the game you're on. */
private fun consoleCards(c: StyleCtx, g: Game, titles: TitleOptions): List<CardSpec> {
    val progress = c.progress(g)
    return listOf(
        CardSpec(if (c.lastPlayed(g) != null) "Continue" else "Play", titles.display(g.name), Icons.Filled.PlayArrow, Color.White, c.wide(g)) { c.actions.play(g) },
        CardSpec("Achievements", progress?.let { "${it.first} / ${it.second}" } ?: "Game details", Icons.Filled.EmojiEvents, Color(0xFFFFC83D), null,
            next = if (Features.resNextAch.state.value) nextAchievements(c, g) else emptyList()) { c.actions.achievements(g) },
        CardSpec("Details", stylePlaytime(c.playtime(g), c.lastPlayed(g) != null), Icons.Filled.Schedule, Color.White, null) { c.actions.open() }
    )
}

/**
 * The next achievements to earn, faint, sliding slowly up behind the Achievements card as a list (update 104): each
 * one's badge and name; it goes round once it reaches the end. Still with Lite effects or animations off.
 */
/** A badge not earned yet: grey (update 119). */
internal val LockedBadgeFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
)

@Composable
private fun NextAchievementsTicker(list: List<com.shelf.launcher.data.RaAchievementDetail>, modifier: Modifier) {
    val moving = !LocalLiteEffects.current && rememberAnimationsEnabled() && !HomeIdle.calm
    val now = MediaClock.rememberFrameTime(running = moving)
    val rowDp = 26.dp
    val perRowMs = 2400L
    val started = remember(list) { System.nanoTime() }
    // Update 136: fades out above the card's title, so the names sliding past never run through "Achievements 0 / 128".
    Box(
        modifier.clipToBounds()
            .graphicsLayer { alpha = 0.3f; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.3f to Color.Black, 0.5f to Color.Transparent),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
    ) {
        val shown = list + list // drawn twice so it goes round without a gap
        Column(
            Modifier.fillMaxWidth().padding(start = 46.dp, end = 8.dp)
                .graphicsLayer {
                    val ms = if (moving) (now.longValue - started).coerceAtLeast(0L) / 1_000_000L else 0L
                    val rowPx = rowDp.toPx()
                    val loop = rowPx * list.size
                    translationY = 6.dp.toPx() - ((ms.toFloat() / perRowMs) * rowPx) % loop
                }
        ) {
            shown.forEach { a ->
                Row(Modifier.height(rowDp), verticalAlignment = Alignment.CenterVertically) {
                    val badge = com.shelf.launcher.data.RaBadges.url(a.badgeName)
                    // Update 119: in colour only once earned — these are still to get, so grey.
                    if (badge != null) AsyncImage(
                        badge, null, colorFilter = if (a.isUnlocked) null else LockedBadgeFilter,
                        modifier = Modifier.size(20.dp).clip(uiRounded(3.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(a.title.orEmpty(), color = Color.White, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WideCard(modifier: Modifier, on: Boolean, spec: CardSpec, index: Int) {
    val shape = uiRounded(6.dp)
    val act by rememberUpdatedState(spec.act)
    Box(
        modifier
            .clip(shape)
            // Update 151: the card's dark and its selected frame follow the theme.
            .background(themeDark(Color(0xFF14181F)).copy(alpha = 0.8f))
            .border(if (on) 3.dp else 1.dp, if (on) ShelfTheme.accent else Color.White.copy(alpha = 0.18f), shape)
            .pointerInput(index) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, index); act() } }
    ) {
        if (spec.art != null) AsyncImage(spec.art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f })
        if (spec.next.isNotEmpty()) NextAchievementsTicker(spec.next, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(spec.icon, null, tint = spec.tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                FitText(spec.title, Color.White, maxSize = 14.sp, minSize = 9.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                if (spec.sub != null) FitText(spec.sub, Color.White.copy(alpha = 0.78f), maxSize = 11.sp, minSize = 8.sp, maxLines = 1)
            }
        }
    }
}

// ───────────────────────────── Console hub ─────────────────────────────

@Composable
internal fun StyleConsoleHub(c: StyleCtx, modifier: Modifier) {
    val icons = if (Features.hubIcons.state.value) c.shortcuts.take(6) else emptyList()
    val g = c.game
    LaunchedEffect(g?.stableKey) { val gg = g ?: return@LaunchedEffect; kotlinx.coroutines.delay(500); c.actions.needAchievements(gg) }
    val specs = if (g == null) emptyList() else consoleCards(c, g, LocalTitleOptions.current)
    // Update 109: a part hidden in Edit Home is skipped by the D-pad.
    val topOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.HUB, "top")
    val cardsOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.HUB, "cards")
    SideEffect {
        StyleNav.side = emptyList()
        StyleNav.top = if (topOff) emptyList() else icons.map { it.onClick }
        StyleNav.bar = if (cardsOff) emptyList() else specs.map { it.act }
    }
    BoxWithConstraints(modifier.padding(horizontal = 14.dp)) {
        // The R1 Size tile (update 104).
        val small = ((maxHeight * 0.17f).coerceIn(52.dp, 118.dp) * GameSize.scale(com.shelf.launcher.data.HomeLayout.HUB)).coerceAtMost(maxHeight * 0.3f)
        val big = small * 1.7f
        val cardH = (maxHeight * 0.17f).coerceIn(50.dp, 92.dp)
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.HUB, "top"), "Profile, icons & clock"), verticalAlignment = Alignment.CenterVertically) {
            ConsoleProfile(c)
            Spacer(Modifier.weight(1f))
            if (icons.isNotEmpty()) {
                HubIcons(icons)
                Spacer(Modifier.weight(1f))
            }
            // Update 136: not when the status bar is on as well (two clocks and two batteries, one above the other).
            if (!c.state.appearance.statusOn) ConsoleStatus()
        }
        if (g == null) {
            StyleEmpty(Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                HubTiles(c, small, big)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(cardH).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.HUB, "cards"), "Cards").riseOnChange(g.stableKey), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    specs.forEachIndexed { i, s -> WideCard(Modifier.weight(1f).fillMaxHeight(), StyleNav.on(StyleNav.Zone.BAR, i), s, i) }
                }
            }
        }
    }
}

@Composable
private fun HubIcons(icons: List<HomeShortcut>) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        icons.forEachIndexed { i, s ->
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            val click by rememberUpdatedState(s.onClick)
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(if (on) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.25f))
                    .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, i); click() } },
                contentAlignment = Alignment.Center
            ) { Icon(s.icon, s.label, tint = if (on) Color(0xFF222222) else Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun HubTiles(c: StyleCtx, small: Dp, big: Dp) {
    val keys = remember(c.games) { gameKeys(c.games) }
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val inGames = StyleNav.zone == StyleNav.Zone.GAMES
    val corner = LocalCardLook.current.cornerDp.coerceAtMost(8).dp
    Box(Modifier.fillMaxWidth().height(big + 6.dp)) {
        AnchoredStrip(
            count = c.games.size, focus = rememberLoopFocus(c), keyOf = { keys[it] }, small = small, big = big,
            widthFor = { it }, gap = 8.dp, startPad = 2.dp, loop = loopRowOn(c),
            modifier = Modifier.fillMaxSize().categorySlide()
        ) { i, grow, w, h, here ->
            val g = c.games[i]
            val on = here
            val sel = on && inGames
            Box(
                Modifier.width(w).height(h)
                    .then(if (on) Modifier.focusedCard() else Modifier)
                    .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } }
            ) {
                StyleArt(c, g, Modifier.fillMaxSize(), square = true, corner = corner, focused = false, fx = null)
                // (Update 151: the frame in the theme's accent, it was always green.)
                if (sel) Box(Modifier.matchParentSize().border(3.dp, ShelfTheme.accent.copy(alpha = grow.coerceIn(0.4f, 1f)), uiRounded(corner)))
                if (grow >= 0.5f) Box(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).clip(uiRounded(corner))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    FitText(LocalTitleOptions.current.display(g.name), Color.White, maxSize = 14.sp, minSize = 9.sp, maxLines = 2, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ───────────────────────────── Resume & discover ─────────────────────────────

@Composable
internal fun StyleConsoleResume(c: StyleCtx, modifier: Modifier) {
    val side = c.shortcuts.take(4)
    val g = c.game
    // The achievement list for the next-to-earn ticker (update 104), once you rest on the game a moment.
    LaunchedEffect(g?.stableKey) { val gg = g ?: return@LaunchedEffect; kotlinx.coroutines.delay(500); c.actions.needAchievements(gg) }
    val specs = if (g == null) emptyList() else consoleCards(c, g, LocalTitleOptions.current)
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val cardsOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.RESUME, "cards")
    SideEffect {
        StyleNav.side = side.map { it.onClick }
        StyleNav.top = emptyList()
        StyleNav.bar = if (cardsOff) emptyList() else specs.map { it.act }
    }
    // Where the big spot and the row beside it are (for the game flying from one to the other).
    val spotRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val nextRect = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    BoxWithConstraints(modifier.padding(horizontal = 16.dp)) {
        val tile = ((maxHeight * 0.44f).coerceIn(104.dp, 250.dp) * GameSize.scale(com.shelf.launcher.data.HomeLayout.RESUME)).coerceAtMost(maxHeight * 0.6f)
        val cell = (tile - 4.dp) / 2
        val cellH = (maxHeight * 0.11f).coerceIn(34.dp, 70.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.RESUME, "top"), "Profile & clock"), verticalAlignment = Alignment.CenterVertically) {
                ConsoleProfile(c)
                Spacer(Modifier.weight(1f))
                if (!c.state.appearance.statusOn) ConsoleStatus()
            }
            Spacer(Modifier.height(4.dp))
            if (g == null) {
                StyleEmpty(Modifier.weight(1f))
            } else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // (Drawn over the row beside it: the game flying into the big spot passes over the row.)
                    Column(Modifier.width(tile).zIndex(1f)) {
                        Text("Resume", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(tile)
                                .onGloballyPositioned { spotRect.value = it.boundsInRoot() }
                                .pointerInput(g.stableKey) { detectTapGestures { open() } }
                        ) {
                            // Update 125: the next game flies out of the row beside it into the big spot (growing as it comes),
                            // and the one you leave slides off to the left; going back, the game comes in from the left and the
                            // one you leave goes back into the row. ("Little jump" in the Resume options: the same with a bounce.)
                            ResumeSpot(c, tile, spotRect, nextRect)
                            if (StyleNav.zone == StyleNav.Zone.GAMES) Box(Modifier.matchParentSize().border(3.dp, ShelfTheme.accent, uiRounded(3.dp)))
                        }
                        Spacer(Modifier.height(4.dp))
                        for (r in 0 until 2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (cc in 0 until 2) {
                                    val i = r * 2 + cc
                                    val s = side.getOrNull(i)
                                    if (s == null) Spacer(Modifier.size(cell, cellH)) else {
                                        val on = StyleNav.on(StyleNav.Zone.SIDE, i)
                                        val click by rememberUpdatedState(s.onClick)
                                        Box(
                                            // Update 151: in the theme's colours.
                                            Modifier.size(cell, cellH).background(themeDark(Color(0xFF2B2F36)).copy(alpha = 0.9f))
                                                .border(if (on) 3.dp else 0.dp, if (on) ShelfTheme.accent else Color.Transparent, RoundedCornerShape(0.dp))
                                                .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.SIDE, i); click() } },
                                            contentAlignment = Alignment.Center
                                        ) { Icon(s.icon, s.label, tint = Color.White, modifier = Modifier.size(22.dp)) }
                                    }
                                    if (cc == 0) Spacer(Modifier.width(0.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        val rowH = (this@BoxWithConstraints.maxHeight * 0.26f).coerceIn(70.dp, 150.dp)
                        // (index in the list, game): the same game twice keeps its own place, and the keys stay unique.
                        // Update 125: with Endless on the row goes on round to the first games.
                        val loop = loopRowOn(c)
                        val next = remember(c.games, c.focus, loop) {
                            if (loop) (1 until minOf(25, c.games.size)).map { k -> (c.focus + k).mod(c.games.size).let { IndexedValue(it, c.games[it]) } }
                                .distinctBy { it.value.stableKey }
                            else c.games.withIndex().drop(c.focus + 1).take(24).distinctBy { it.value.stableKey }
                        }
                        val state = rememberLazyListState()
                        LaunchedEffect(c.focus) { state.scrollToItem(0) }
                        LazyRow(Modifier.fillMaxWidth().height(rowH).onGloballyPositioned { nextRect.value = it.boundsInRoot() }.categorySlide(), state = state, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Keyed by game (update 112): as the next one goes up into the big spot, the rest glide along.
                            items(next, key = { it.value.stableKey }) { iv ->
                                val i = iv.index
                                val gg = iv.value
                                Box(Modifier.animateItem().size(rowH).pointerInput(i) { detectTapGestures { select(i) } }) {
                                    StyleArt(c, gg, Modifier.fillMaxSize(), square = true, corner = 2.dp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Discover", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, style = nameTextStyle(NameBackdrop.SHADOW))
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().height((this@BoxWithConstraints.maxHeight * 0.3f).coerceIn(70.dp, 160.dp)).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.RESUME, "cards"), "Discover cards").riseOnChange(g.stableKey), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            specs.forEachIndexed { i, s -> WideCard(Modifier.weight(1f).fillMaxHeight(), StyleNav.on(StyleNav.Zone.BAR, i), s, i) }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Channel grid ─────────────────────────────

/** The pale backdrop behind the channel grid (drawn across the whole screen by StyleBackLayer). */
@Composable
internal fun ChannelBackdrop(modifier: Modifier = Modifier) {
    // (Update 329: Dark — true black for OLED screens, a breath of grey at the bottom like the pale one's.)
    if (chanOled()) Box(modifier.background(Brush.verticalGradient(listOf(Color.Black, Color.Black, Color(0xFF0A0B0E)))))
    else Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFFF6F8FB), Color(0xFFDDE2E9)))))
}

/** Update 329: the Channel grid's Dark version is on (Pale backdrop + Dark, Layout options). */
internal fun chanOled(): Boolean = Features.chanLight.state.value && Features.chanDark.state.value

/**
 * Update 329: the Channel grid's dark greys — with Dark on, fixed near-black greys (OLED: no theme tint lifting them);
 * otherwise the theme-tinted ones as before (themeDark).
 */
@Composable
private fun chanShade(base: Color): Color = if (!chanOled()) themeDark(base) else when (base) {
    Color(0xFF1D222B) -> Color(0xFF111316)
    Color(0xFF2E3540) -> Color(0xFF202328)
    Color(0xFF1B2028) -> Color(0xFF0E1012)
    Color(0xFF2A303A) -> Color(0xFF17191D)
    Color(0xFF171B22) -> Color(0xFF050607)
    else -> base
}

@Composable
internal fun StyleChannelGrid(c: StyleCtx, modifier: Modifier) {
    val cols = Features.chanCols.state.value
    val rows = 3
    val per = cols * rows
    // (Update 329: the Dark version is the pale layout drawn in its dark colours.)
    val light = Features.chanLight.state.value && !Features.chanDark.state.value
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    // Update 109: the corner buttons / the clock bar hidden in Edit Home are skipped by the D-pad.
    val corner = Features.chanCorner.state.value
    val cornerOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.CHAN, "corner")
    val barOff = LayoutParts.off(com.shelf.launcher.data.HomeLayout.CHAN, "bar")
    SideEffect {
        StyleNav.side = emptyList()
        // ▲ past the top row (update 104): the corner buttons — profile, Settings, notifications, save sync.
        StyleNav.top = if (corner && !cornerOff) listOf(c.actions.profile, c.actions.settings, c.actions.notifications, c.actions.cloud) else emptyList()
        StyleNav.topStart = 0
        // ▼ past the last row (update 101): the two round buttons, the category (left) and the game's details (right).
        StyleNav.bar = if (c.games.isEmpty() || barOff) emptyList() else listOf({ c.actions.category(1) }, { c.actions.open() })
        // Update 156: Endless on (the keys in styleHomeKey go round past the last page / column).
        StyleLoop.on = loopRowOn(c)
    }
    BoxWithConstraints(modifier) {
        val barH = (maxHeight * 0.22f).coerceIn(64.dp, 120.dp)
        // (Update 112: scrolling continuously there are no pages: the bar shows the game's place instead.)
        val continuous = Features.gridsContinuous.state.value
        val pages = if (continuous) c.games.size.coerceAtLeast(1) else ((c.games.size + per - 1) / per).coerceAtLeast(1)
        val page = if (c.games.isEmpty()) 0 else if (continuous) c.focus else c.focus / per
        // Update 156: Endless on: the pages counted on round, so the last page → the first slides in like any next
        // page (and back), never back across them all; the page shown is that count round the pages.
        val chanTap = remember { LoopTap() }
        // (Update 156, review: the key handler's word that a page went round is taken only once the page itself has
        // changed — it comes a moment before the selection, and a redraw in between must not slide twice.)
        val chanSeen = remember { intArrayOf(page, StyleLoop.tick) }
        if (page != chanSeen[0]) { chanSeen[0] = page; chanSeen[1] = StyleLoop.tick }
        val vPage = rememberLoopPlace(page, pages, loopRowOn(c) && !continuous && pages >= 2, chanSeen[1], StyleLoop.step, c.games, chanTap)
        Column(Modifier.fillMaxSize()) {
            if (corner) ChannelCorner(c, light)
            if (c.games.isEmpty()) {
                StyleEmpty(Modifier.weight(1f))
            } else if (Features.gridsContinuous.state.value) {
                // Update 112: one long strip of columns that glides along as you move (no pages).
                val pageK = GameSize.scale(com.shelf.launcher.data.HomeLayout.CHAN)
                ContinuousGrid(
                    count = c.games.size, focus = c.focus, rows = rows, cols = cols, gap = 8.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().categorySlide().graphicsLayer { scaleX = pageK; scaleY = pageK }.padding(horizontal = 14.dp, vertical = 6.dp),
                    // Update 156: Endless on: the strip carries on with the first columns again after the last.
                    loop = loopRowOn(c), loopTick = StyleLoop.tick, loopStep = StyleLoop.step, loopList = c.games
                ) { i, m -> ChanTile(c, i, light, m, select, open) }
            } else {
                // A new page slides in from the side (update 104), it used to just appear.
                androidx.compose.animation.AnimatedContent(
                    // Update 156: the page counted on round (vPage), so going round slides the same way as a step.
                    targetState = vPage, label = "chanPage", transitionSpec = { pageSlide() },
                    // (Update 112: a new category slides / fades in too — it used to just appear.)
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().categorySlide()
                ) { shownPage ->
                val page = if (loopRowOn(c)) shownPage.mod(pages) else shownPage
                // The % size (update 105): the page of tiles a little smaller, in the middle.
                val pageK = GameSize.scale(com.shelf.launcher.data.HomeLayout.CHAN)
                Column(Modifier.fillMaxSize().graphicsLayer { scaleX = pageK; scaleY = pageK }.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (cc in 0 until cols) {
                                val i = page * per + r * cols + cc
                                val g = c.games.getOrNull(i)
                                if (g == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    ChanTile(c, i, light, Modifier.weight(1f).fillMaxHeight(), select, open)
                                }
                            }
                        }
                    }
                }
                }
            }
            ChannelBar(c, light, barH, page, pages)
        }
    }
}

/** One of the Channel grid's tiles (game [i]): its wide art in a rounded white (or dark) frame, its name on the one you're on. */
@Composable
private fun ChanTile(c: StyleCtx, i: Int, light: Boolean, modifier: Modifier, select: (Int) -> Unit, open: () -> Unit) {
    val g = c.games.getOrNull(i) ?: return
    val on = i == c.focus
    val sel = on && StyleNav.zone == StyleNav.Zone.GAMES
    val lift = styleLift(sel)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .graphicsLayer { val k = 1f + 0.04f * lift; scaleX = k; scaleY = k }
            .clip(shape)
            .background(if (light) Color.White else chanShade(Color(0xFF1D222B))) // (update 151: themed)
            .border(if (sel) 4.dp else 2.dp, if (sel) ShelfTheme.accent else androidx.compose.ui.graphics.lerp(if (chanOled()) Color(0xFF3A414C) else Color(0xFFB9C3D0), ShelfTheme.accent, 0.3f), shape)
            .pointerInput(i) { detectTapGestures { if (i == GridHome.focus) open() else select(i) } }
    ) {
        StyleArt(c, g, Modifier.fillMaxSize().padding(3.dp), wide = true, corner = 13.dp)
        if (Features.chanNames.state.value && on) Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(6.dp).riseOnEnter().clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 3.dp)
        ) { FitText(LocalTitleOptions.current.display(g.name), Color.White, maxSize = 12.sp, minSize = 8.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * Update 112: a grid that scrolls continuously instead of in pages (Channel grid, Handheld menu). The games run
 * down each column ([rows] of them) and the columns follow one another; [cols] columns are in view and the strip glides
 * along so the game you're on stays in view — ◀ ▶ go a column over, ▲ ▼ along the column.
 */
@Composable
internal fun ContinuousGrid(
    count: Int, focus: Int, rows: Int, cols: Int, gap: androidx.compose.ui.unit.Dp, modifier: Modifier,
    /** Square cells (the Handheld menu's icons); else they fill the height. */
    square: Boolean = false,
    /**
     * Update 113 (Handheld menu): square slots across the whole width, as many columns as fit (at least [cols]),
     * and the slots past the last game drawn empty ([cell] gets an index ≥ [count] for those), like a handheld's menu.
     */
    fill: Boolean = false,
    /**
     * Update 156: Endless on: the strip carries on with the first columns again after the last (and before the first),
     * the column you're on counted on round ([loopTick] / [loopStep]: a key that went round and which way, StyleLoop),
     * so going round slides one column on like any step. Only when there are more columns than fit.
     */
    loop: Boolean = false,
    loopTick: Int = 0,
    loopStep: Int = 0,
    loopList: Any? = null,
    cell: @Composable (Int, Modifier) -> Unit
) {
    val focusCol = focus / rows.coerceAtLeast(1)
    val totalCols = (count + rows - 1) / rows.coerceAtLeast(1)
    val loopTap = androidx.compose.runtime.remember { LoopTap() } // (Update 156)
    // The first column in view moves only as far as needed to keep the game you're on in view (a plain holder: it's
    // worked out from the focus each time, never a state written while composing).
    val startHolder = androidx.compose.runtime.remember { IntArray(1) }
    val scroll = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(startHolder[0].toFloat()) }
    // Update 125: cut off at the grid's own edges (a hair outside, for the lifted tile's frame): the games scrolling out
    // used to show in the margin at the screen's left and right edges.
    BoxWithConstraints(modifier.drawWithContent {
        val e = 5.dp.toPx()
        clipRect(-e, -e, size.width + e, size.height + e) { this@drawWithContent.drawContent() }
    }) {
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        val cellH = (maxHeight - gap * (rows - 1)) / rows
        val w = if (square) minOf(cellW, cellH) else cellW
        val h = if (square) w else cellH
        val stepX = w + gap
        val viewCols = if (fill && square) (((maxWidth + gap) / stepX).toInt()).coerceAtLeast(cols) else cols
        // Update 156: going round (Endless, more columns than fit): the column you're on counted on round.
        val looping = loop && !fill && totalCols > viewCols
        // (Update 156, review: a key's word that it went round is taken only once the column itself has changed.)
        val seenCol = androidx.compose.runtime.remember { intArrayOf(focusCol, loopTick) }
        if (focusCol != seenCol[0]) { seenCol[0] = focusCol; seenCol[1] = loopTick }
        val atCol = rememberLoopPlace(focusCol, totalCols, looping, seenCol[1], loopStep, loopList, loopTap)
        var start = startHolder[0]
        if (atCol < start) start = atCol
        if (atCol > start + viewCols - 1) start = atCol - viewCols + 1
        if (!looping) start = start.coerceIn(0, (totalCols - viewCols).coerceAtLeast(0))
        startHolder[0] = start
        LaunchedEffect(start) {
            if (kotlin.math.abs(scroll.value - start) > viewCols * 2f || MotionPrefs.page() == com.shelf.launcher.data.Features.PageAnim.NONE) scroll.snapTo(start.toFloat())
            else scroll.animateTo(start.toFloat(), androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        // Square cells: the block of columns in the middle (like the paged grid).
        val left = if (square) (maxWidth - (w * viewCols + gap * (viewCols - 1))) / 2 else 0.dp
        val top = if (square) (maxHeight - (h * rows + gap * (rows - 1))) / 2 else 0.dp
        val shownCols = if (fill) maxOf(totalCols, viewCols) else totalCols
        // (Update 156: going round, the columns before the first / after the last are the games again, [col] counted
        // on round; each keeps its own key so nothing already on screen is drawn anew.)
        val from = (kotlin.math.floor(scroll.value).toInt() - 1).let { if (looping) it else it.coerceAtLeast(0) }
        val to = (from + viewCols + 2).let { if (looping) it else it.coerceAtMost(shownCols - 1) }
        // (Update 246, #60: the cells picked before the loop — a `continue` in it made Compose give each pass its own
        // group, so a scroll by a column threw every cell away and built it again.)
        val cells = (from..to).flatMap { col -> (0 until rows).map { r -> col to r } }
            .filter { (col, r) -> (if (looping) col.mod(totalCols) else col) * rows + r < count || fill }
        for ((col, r) in cells) {
            val i = (if (looping) col.mod(totalCols) else col) * rows + r
            androidx.compose.runtime.key(col * rows + r) {
                cell(
                    i,
                    Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(((left + stepX * col).toPx() - stepX.toPx() * scroll.value).roundToInt(), (top + (h + gap) * r).roundToPx()) }
                        .size(w, h)
                        // (Update 156, review: going round, a touch notes which copy of the column it was, so a tapped
                        // game is picked where it is instead of the strip running off the shorter way round.)
                        .then(
                            if (looping) Modifier.pointerInput(col) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                        if (e.changes.any { it.pressed && !it.previousPressed }) loopTap.virtual = col
                                    }
                                }
                            } else Modifier
                        )
                )
            }
        }
    }
}


/** The round buttons in the top right corner (update 104): profile, Settings, notifications, save sync. */
@Composable
private fun ChannelCorner(c: StyleCtx, light: Boolean) {
    val ink = if (light) Color(0xFF4A5461) else Color.White
    val avatar = profilePictureUrl(RaBadges.mediaUrl(c.state.raProfile?.UserPic))
    val items = listOf(
        Icons.Filled.Person to c.actions.profile, Icons.Filled.Settings to c.actions.settings,
        Icons.Filled.Notifications to c.actions.notifications, Icons.Filled.Cloud to c.actions.cloud
    )
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, end = 14.dp, start = 14.dp), contentAlignment = Alignment.TopEnd) {
    Row(Modifier.editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.CHAN, "corner"), "Corner buttons"), verticalAlignment = Alignment.CenterVertically) {
        val (time, _) = rememberConsoleClock()
        // (Update 330, phase 4: the round shortcuts' ☰ in a top corner sits here, before the clock — in the corner it
        // lay under the category name or on the save sync button.)
        HomeMenuSlot.Room()
        if (!Features.chanStatusBar.state.value) {
            Text(time, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(12.dp))
        }
        items.forEachIndexed { i, (icon, act) ->
            val on = StyleNav.on(StyleNav.Zone.TOP, i)
            Box(
                Modifier.padding(start = 8.dp).size(34.dp)
                    .graphicsLayer { val k = if (on) 1.12f else 1f; scaleX = k; scaleY = k }
                    .clip(CircleShape)
                    .background(if (light) Brush.verticalGradient(listOf(Color.White, Color(0xFFE3E8EF))) else Brush.verticalGradient(listOf(chanShade(Color(0xFF2E3540)), chanShade(Color(0xFF1B2028)))))
                    // (Update 151: the rim and the dark follow the theme.)
                    .border(if (on) 3.dp else 1.5.dp, if (on) ShelfTheme.accent else androidx.compose.ui.graphics.lerp(Color(0xFF9FB6CF), ShelfTheme.accent, 0.45f), CircleShape)
                    .pointerInput(i) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.TOP, i); act() } },
                contentAlignment = Alignment.Center
            ) {
                if (i == 0 && avatar != null) AsyncImage(avatar, "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape))
                else Icon(icon, null, tint = if (on) ShelfTheme.accent else ink.copy(alpha = 0.85f), modifier = Modifier.size(19.dp))
            }
        }
    }
    }
}

@Composable
private fun ChannelBar(c: StyleCtx, light: Boolean, barH: Dp, page: Int, pages: Int) {
    val (time, date) = rememberConsoleClock()
    OwnClock.Mark() // (Update 333: the status bar leaves its clock out — one clock on screen.)
    val ink = if (light) Color(0xFF4A5461) else Color.White
    // Update 151: the rims and the dark bar take the theme's colour (they were one fixed blue-grey).
    val rim = androidx.compose.ui.graphics.lerp(Color(0xFF8FB4D8), ShelfTheme.accent, 0.45f)
    val darkTop = chanShade(Color(0xFF2A303A))
    val category by rememberUpdatedState(c.actions.category)
    val open by rememberUpdatedState(c.actions.open)
    Box(Modifier.fillMaxWidth().height(barH).editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.CHAN, "bar"), "Clock bar & round buttons")) {
        Box(
            Modifier.fillMaxSize().padding(top = barH * 0.16f)
                .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
                .background(if (light) Brush.verticalGradient(listOf(Color.White, Color(0xFFD5DBE3))) else Brush.verticalGradient(listOf(darkTop, chanShade(Color(0xFF171B22)))))
                .border(1.dp, rim.copy(alpha = 0.7f), RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
        )
        Column(Modifier.align(Alignment.Center).padding(top = barH * 0.12f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = ink, fontSize = (barH.value * 0.36f).sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
            Text(
                date + if (pages > 1) "   ·   ${page + 1} / $pages" else "",
                color = ink.copy(alpha = 0.85f), fontSize = (barH.value * 0.15f).coerceIn(10f, 16f).sp, maxLines = 1, softWrap = false
            )
        }
        val ring = barH * 0.62f
        val leftOn = StyleNav.on(StyleNav.Zone.BAR, 0)
        val rightOn = StyleNav.on(StyleNav.Zone.BAR, 1)
        Box(
            Modifier.align(Alignment.CenterStart).padding(start = 14.dp, top = barH * 0.1f).size(ring)
                .graphicsLayer { val k = if (leftOn) 1.1f else 1f; scaleX = k; scaleY = k }
                .clip(CircleShape)
                .background(if (light) Color.White else darkTop)
                .border(if (leftOn) 5.dp else 3.dp, if (leftOn) ShelfTheme.accent else rim, CircleShape)
                .pointerInput(Unit) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 0); category(1) } },
            contentAlignment = Alignment.Center
        ) {
            // The console's logo in its colours, inside the circle (update 104); the other categories their icon and name.
            val stop = c.stopAt(0)
            // Update 115: by default the console as a symbol — its small console picture (RetroAchievements' system
            // icon), else its logo in one colour — not the logo in its colours (Channel options › Corner).
            val symbols = Features.chanSymbols.state.value
            val symbol = if (symbols) com.shelf.launcher.data.ConsoleLogos.raIcon(stop?.platform) else null
            val shown = symbol ?: stop?.logo
            var failed by androidx.compose.runtime.remember(shown) { androidx.compose.runtime.mutableStateOf(false) }
            val logo = shown?.takeIf { !failed }
            if (logo != null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val request = remember(logo) {
                    coil.request.ImageRequest.Builder(context).data(logo)
                        .apply { if (logo.endsWith(".svg")) decoderFactory(coil.decode.SvgDecoder.Factory()) }
                        .size(256).build()
                }
                AsyncImage(
                    request, c.categoryLabel, contentScale = ContentScale.Fit, onError = { failed = true },
                    colorFilter = if (symbols && symbol == null) androidx.compose.ui.graphics.ColorFilter.tint(ink) else null,
                    modifier = Modifier.fillMaxSize().padding(ring * (if (symbol != null) 0.22f else 0.17f))
                )
            } else if (stop != null && stop.special != null && symbols) {
                Icon(stop.icon, c.categoryLabel, tint = ShelfTheme.accent, modifier = Modifier.size(ring * 0.42f))
            } else if (stop != null && stop.special != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(stop.icon, null, tint = ShelfTheme.accent, modifier = Modifier.size(ring * 0.3f))
                    FitText(c.categoryLabel, ink, maxSize = 11.sp, minSize = 7.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                }
            } else FitText(c.categoryLabel, ink, maxSize = 13.sp, minSize = 8.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, top = barH * 0.1f).size(ring)
                .graphicsLayer { val k = if (rightOn) 1.1f else 1f; scaleX = k; scaleY = k }
                .clip(CircleShape)
                .background(if (light) Color.White else darkTop)
                .border(if (rightOn) 5.dp else 3.dp, if (rightOn) ShelfTheme.accent else rim, CircleShape)
                .pointerInput(Unit) { detectTapGestures { StyleNav.tapped(StyleNav.Zone.BAR, 1); open() } },
            contentAlignment = Alignment.Center
        ) {
            // Voltcap's bolt in the theme's colour (update 104), where the envelope was.
            Icon(androidx.compose.ui.res.painterResource(com.shelf.launcher.R.drawable.ic_stat_voltcap), "Game", tint = ShelfTheme.accent, modifier = Modifier.size(ring * 0.52f))
        }
    }
}

/**
 * Resume's big spot (update 125). Moving on, the next game flies out of the first place of the row beside it into the
 * spot, growing as it comes, while the one you leave slides off to the left; moving back, the game comes in from the
 * left and the one you leave flies back into the row. [spot] / [row] are where the spot and the row are on screen.
 */
@Composable
private fun ResumeSpot(
    c: StyleCtx, tile: Dp,
    spot: androidx.compose.runtime.State<androidx.compose.ui.geometry.Rect?>,
    row: androidx.compose.runtime.State<androidx.compose.ui.geometry.Rect?>
) {
    val v = rememberLoopFocus(c)
    val p = remember { androidx.compose.animation.core.Animatable(1f) }
    // [game shown before, direction of the last move, virtual place before]
    val prev = remember { intArrayOf(c.focus, 1, v) }
    var leaving by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dir by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    val anim = MotionPrefs.info() != Features.InfoAnim.NONE
    val jump = Features.resumeSwap.value == Features.ResumeSwap.JUMP
    LaunchedEffect(v, c.focus) {
        if (prev[2] == v && prev[0] == c.focus) return@LaunchedEffect
        val d = if (v != prev[2]) (if (v > prev[2]) 1 else -1) else (if (c.focus > prev[0]) 1 else -1)
        leaving = prev[0]
        dir = d
        prev[0] = c.focus; prev[2] = v
        if (!anim) { p.snapTo(1f); leaving = -1; return@LaunchedEffect }
        p.snapTo(0f)
        p.animateTo(
            1f,
            if (jump) androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 260f)
            else androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
        leaving = -1
    }
    val s = spot.value
    val r = row.value
    val density = androidx.compose.ui.platform.LocalDensity.current
    // From the spot to the row's first place: how far, and how much smaller.
    val dx = if (s != null && r != null) r.left - s.left else with(density) { (tile + 14.dp).toPx() }
    val dy = if (s != null && r != null) r.top - s.top else 0f
    val small = if (s != null && r != null && s.height > 0f) (r.height / s.height).coerceIn(0.2f, 1f) else 0.5f
    val q = p.value
    Box(Modifier.fillMaxSize()) {
        // The game you leave.
        val old = c.games.getOrNull(leaving)
        if (old != null && q < 1f) {
            StyleArt(
                c, old,
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    val t = q.coerceIn(0f, 1f)
                    if (dir > 0) {
                        // Off to the left, fading.
                        translationX = -size.width * 0.55f * t
                        val k = 1f - 0.25f * t
                        scaleX = k; scaleY = k
                        alpha = 1f - t
                    } else {
                        // Back into the row.
                        translationX = dx * t; translationY = dy * t
                        val k = 1f + (small - 1f) * t
                        scaleX = k; scaleY = k
                        alpha = 1f - 0.4f * t
                    }
                },
                square = true, corner = 3.dp
            )
        }
        c.game?.let { now ->
            StyleArt(
                c, now,
                Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    val t = 1f - q
                    if (dir > 0) {
                        // Out of the row's first place, growing into the spot.
                        translationX = dx * t; translationY = dy * t
                        val k = 1f + (small - 1f) * t.coerceIn(0f, 1f)
                        scaleX = k; scaleY = k
                    } else {
                        // In from the left.
                        translationX = -size.width * 0.55f * t
                        alpha = (1f - t).coerceIn(0f, 1f)
                    }
                },
                square = true, corner = 3.dp
            )
        }
    }
}
