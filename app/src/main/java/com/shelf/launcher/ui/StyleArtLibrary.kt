package com.shelf.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.shelf.launcher.data.Game
import com.shelf.launcher.SpecialFilter
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlin.math.abs
import kotlin.math.floor

/*
 * Update 374 (Toni: "Artwork library — NeoStation-inspired"): one scene, no page per game. The game's hero art fills the
 * screen; a slim navigation rail on the left (All games, Favourites, Recent, Search, Sort, Achievements, Settings — each one
 * a real Voltcap action, labels open when the D-pad is on it); a tall glass panel with the category's name and logo and the
 * games as a list that glides under a highlight that stays in one place; the game's logo (or its name), facts, achievement
 * progress, playtime and a Play button on the right. Art, logo, facts, launch and Details are Voltcap's own (StyleCtx,
 * actions.play / open), no second launcher. ▲ ▼ the list, ◀ the rail, ▶ Play, A opens / confirms, B steps back.
 */

internal object ArtLibNav {
    enum class Zone { LIST, RAIL, PLAY }

    @Volatile var ctx: StyleCtx? = null
    @Volatile var rail: List<RailAction> = emptyList()
    var zone by mutableStateOf(Zone.LIST)
    var railSel by mutableIntStateOf(0)
    internal val keysDown = HashSet<Int>()
}

internal class RailAction(val label: String, val icon: ImageVector, val run: () -> Unit)

/** Moves the list by [d] games (round the ends with Endless, else stopping at them); false at an end it can't pass. */
private fun artLibStep(c: StyleCtx, d: Int): Boolean {
    val n = c.games.size
    if (n == 0) return false
    val to = if (loopRowOn(c)) (c.focus + d).mod(n) else (c.focus + d).coerceIn(0, n - 1)
    if (to == c.focus) return false
    c.actions.select(to)
    return true
}

internal fun artLibKey(event: KeyEvent, reach: (Boolean) -> Boolean): Boolean {
    val c = ArtLibNav.ctx ?: return false
    val code = event.nativeKeyEvent.keyCode
    val down = event.type == KeyEventType.KeyDown
    val up = event.type == KeyEventType.KeyUp
    if (up && ArtLibNav.keysDown.remove(code)) return true
    val k = event.key
    val isA = k == Key.ButtonA || k == Key.Enter || k == Key.DirectionCenter
    val isBack = k == Key.ButtonB || k == Key.Back
    fun take(): Boolean { ArtLibNav.keysDown += code; return true }
    val first = event.nativeKeyEvent.repeatCount == 0
    when (ArtLibNav.zone) {
        ArtLibNav.Zone.RAIL -> {
            val items = ArtLibNav.rail
            if (isA) {
                if (up) { items.getOrNull(ArtLibNav.railSel)?.run?.invoke(); ArtLibNav.zone = ArtLibNav.Zone.LIST }
                return true
            }
            if (!down) return false
            return when {
                k == Key.DirectionUp -> { ArtLibNav.railSel = (ArtLibNav.railSel - 1).coerceAtLeast(0); take() }
                k == Key.DirectionDown -> { ArtLibNav.railSel = (ArtLibNav.railSel + 1).coerceAtMost((items.size - 1).coerceAtLeast(0)); take() }
                k == Key.DirectionRight || isBack -> { if (first) ArtLibNav.zone = ArtLibNav.Zone.LIST; take() }
                k == Key.DirectionLeft -> take()
                else -> false
            }
        }
        ArtLibNav.Zone.PLAY -> {
            val g = c.game
            if (isA) {
                if (up && g != null) c.actions.play(g)
                return true
            }
            if (!down) return false
            return when {
                k == Key.DirectionLeft || isBack -> { if (first) ArtLibNav.zone = ArtLibNav.Zone.LIST; take() }
                k == Key.DirectionUp -> { ArtLibNav.zone = ArtLibNav.Zone.LIST; artLibStep(c, -1); take() }
                k == Key.DirectionDown -> { ArtLibNav.zone = ArtLibNav.Zone.LIST; artLibStep(c, 1); take() }
                k == Key.DirectionRight -> take()
                else -> false
            }
        }
        ArtLibNav.Zone.LIST -> {
            if (!down) return false
            return when (k) {
                Key.DirectionUp -> { if (!artLibStep(c, -1) && first) reach(true); take() }
                Key.DirectionDown -> { if (!artLibStep(c, 1) && first) reach(false); take() }
                Key.DirectionLeft -> { if (first && ArtLibNav.rail.isNotEmpty()) { ArtLibNav.railSel = 0; ArtLibNav.zone = ArtLibNav.Zone.RAIL }; take() }
                Key.DirectionRight -> { if (first && c.game != null) ArtLibNav.zone = ArtLibNav.Zone.PLAY; take() }
                else -> false
            }
        }
    }
}

/** The room behind the layout: the theme's dark, the hero art over the whole screen (kept to its own shape: a portrait picture is shown whole over a soft copy of itself) and soft washes on the left (the panel's side) and the bottom (the facts). */
@Composable
internal fun ArtLibraryBackdrop(modifier: Modifier, art: String? = null) {
    val back = themeDark(Color(0xFF070A10), 0.6f)
    Box(modifier.background(Brush.linearGradient(listOf(themeDark(Color(0xFF141B27), 0.6f), themeDark(Color(0xFF080B11), 0.6f)))).clipToBounds()) {
        val portrait = (CoverAspects.rawOf(art) ?: 2f) < 1f
        if (portrait && !LocalLiteEffects.current) HeldImage(art, Modifier.fillMaxSize(), fadeMs = 450, imageModifier = Modifier.blur(28.dp))
        HeldImage(
            art, Modifier.fillMaxSize(), fadeMs = 450,
            contentScale = if (portrait) ContentScale.Fit else ContentScale.Crop,
            alignment = if (portrait) Alignment.CenterEnd else Alignment.Center
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to back.copy(alpha = 0.9f), 0.38f to back.copy(alpha = 0.6f), 0.75f to back.copy(alpha = 0.12f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to back.copy(alpha = 0.3f), 0.5f to Color.Transparent, 1f to back.copy(alpha = 0.85f))))
    }
}

@Composable
internal fun StyleArtLibrary(c: StyleCtx, modifier: Modifier = Modifier) {
    val accent = ShelfTheme.accent
    val g = c.game
    val rail = artLibRail(c)
    SideEffect {
        ArtLibNav.ctx = c; ArtLibNav.rail = rail
        StyleNav.side = emptyList(); StyleNav.top = emptyList(); StyleNav.bar = emptyList()
    }
    DisposableEffect(Unit) { onDispose { ArtLibNav.keysDown.clear(); ArtLibNav.zone = ArtLibNav.Zone.LIST } }
    androidx.activity.compose.BackHandler(enabled = ArtLibNav.zone != ArtLibNav.Zone.LIST && GamePopup.game == null) { ArtLibNav.zone = ArtLibNav.Zone.LIST }
    LaunchedEffect(g?.stableKey) { val gg = g ?: return@LaunchedEffect; kotlinx.coroutines.delay(250); c.actions.needMetadata(gg) }
    // The art of the games next to this one is fetched ahead, so moving along never shows an empty frame.
    val ctxA = LocalContext.current
    LaunchedEffect(c.focus, c.games.size) {
        kotlinx.coroutines.delay(120)
        val loader = coil.Coil.imageLoader(ctxA)
        for (d in listOf(1, -1, 2, -2, 3)) {
            val n = c.games.size
            if (n == 0) break
            val at = if (loopRowOn(c)) (c.focus + d).mod(n) else c.focus + d
            val url = c.games.getOrNull(at)?.let { c.backdrop(it) } ?: continue
            loader.enqueue(coil.request.ImageRequest.Builder(ctxA).data(url).build())
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth; val h = maxHeight
        val compact = h < 380.dp

        val railW = 60.dp
        val panelX = railW + 20.dp
        val panelW = (w * 0.31f).coerceIn(240.dp, 380.dp)
        // (Update 391: the host already keeps the status bar clear; 40 dp more left ~100 px of empty space above the rail and panel.)
        val topPad = if (compact) 6.dp else 10.dp
        if (g == null) StyleEmpty(Modifier.fillMaxSize().padding(start = panelX))
        else {
            ArtLibPanel(c, accent, compact, Modifier.padding(start = panelX, top = topPad, bottom = 16.dp).width(panelW).fillMaxHeight())
            ArtLibShowcase(
                c, g, accent, compact,
                Modifier.align(Alignment.BottomStart).padding(start = panelX + panelW + 32.dp, end = 32.dp, bottom = if (compact) 18.dp else 30.dp)
                    .widthIn(max = 600.dp)
            )
        }
        ArtLibRail(c, rail, accent, Modifier.zIndex(5f).padding(start = 10.dp, top = topPad, bottom = 16.dp).fillMaxHeight())
    }
}

/** The rail's real actions: the stops that exist, then Search, Sort, Achievements and Settings. */
@Composable
private fun artLibRail(c: StyleCtx): List<RailAction> {
    val stops = c.stops
    val stop = c.stop
    val actions = c.actions
    return remember(stops, stop, actions, c.categoryLabel) {
        buildList {
            fun go(i: Int) { if (stop >= 0 && i != stop) actions.category(i - stop) }
            stops.indexOfFirst { it.special == null && it.platform == null }.takeIf { it >= 0 }?.let { i -> add(RailAction("All games", Icons.Filled.Apps) { go(i) }) }
            stops.indexOfFirst { it.special == SpecialFilter.FAVORITES }.takeIf { it >= 0 }?.let { i -> add(RailAction("Favourites", stops[i].icon) { go(i) }) }
            stops.indexOfFirst { it.special == SpecialFilter.RECENT }.takeIf { it >= 0 }?.let { i -> add(RailAction("Recent", stops[i].icon) { go(i) }) }
            add(RailAction("Search", Icons.Filled.Search) { actions.search() })
            add(RailAction("Sort", Icons.Filled.SortByAlpha) { actions.sort() })
            add(RailAction("Achievements", Icons.Filled.EmojiEvents) { actions.achievementsHub() })
            add(RailAction("Settings", Icons.Filled.Settings) { actions.settings() })
        }
    }
}

internal const val ART_RAIL_OPEN_DP = 196f
internal const val ART_RAIL_CLOSED_DP = 52f
/** Open rail 196 - column padding 2 x 6 - row padding 2 x 8 - icon 22 - gap 10. */
internal const val ART_RAIL_LABEL_DP = 136f

/** Labels are invisible while the rail is still narrow and fully there once it has room (a fade, no movement). */
internal fun artLibRailLabelAlpha(widthDp: Float): Float = ((widthDp - 110f) / 60f).coerceIn(0f, 1f)

@Composable
private fun ArtLibRail(c: StyleCtx, items: List<RailAction>, accent: Color, modifier: Modifier) {
    val on = ArtLibNav.zone == ArtLibNav.Zone.RAIL
    val width by animateDpAsState(if (on) ART_RAIL_OPEN_DP.dp else ART_RAIL_CLOSED_DP.dp, tween(220, easing = FastOutSlowInEasing), label = "alRail")
    val ink = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Column(
        modifier.width(width).clip(RoundedCornerShape(26.dp)).background(themeDark(Color(0xFF080B11), 0.5f).copy(alpha = if (on) 0.94f else 0.55f))
            .border(1.dp, accent.copy(alpha = if (on) 0.4f else 0.14f), RoundedCornerShape(26.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for ((i, item) in items.withIndex()) {
            val sel = on && i == ArtLibNav.railSel
            val inCat = item.label == "All games" && c.stops.getOrNull(c.stop)?.let { s -> s.special == null && s.platform == null } == true ||
                item.label == "Favourites" && c.stops.getOrNull(c.stop)?.special == SpecialFilter.FAVORITES ||
                item.label == "Recent" && c.stops.getOrNull(c.stop)?.special == SpecialFilter.RECENT
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (sel) accent.copy(alpha = 0.92f) else if (inCat) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .pointerInput(i) { detectTapGestures { ArtLibNav.zone = ArtLibNav.Zone.LIST; item.run() } }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, null, tint = if (sel) ink else Color.White.copy(alpha = if (inCat) 1f else 0.78f), modifier = Modifier.size(22.dp))
                // Update 396: the label is always laid out at the OPEN width (never measured at the in-between widths of the
                // animation, which wrapped it letter by letter and made the rows jump) and the rail's clip + a fade by the
                // animated width reveal it: no wrap, no move, no spawn. (R18: a label wraps rather than being cut.)
                Spacer(Modifier.width(10.dp))
                Text(item.label, color = if (sel) ink else Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 17.sp,
                    fontWeight = if (sel || inCat) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(ART_RAIL_LABEL_DP.dp).padding(vertical = 4.dp)
                        .graphicsLayer { alpha = artLibRailLabelAlpha(width.value) })
            }
        }
    }
}

/**
 * Top of the highlighted row inside the list for glide position [pos] (the rows follow at [rowH] each). Endless: always the
 * anchor. Otherwise the list's scroll is clamped to its own ends, so the highlight starts at the top, reaches the anchor and
 * runs on to the bottom for the last games; short lists stay at the top. Continuous in [pos], so no jump.
 */
internal fun artLibRowTop(pos: Float, rowH: Float, anchor: Float, pad: Float, total: Float, listH: Float, loop: Boolean): Float {
    if (loop) return anchor - rowH / 2f
    val scroll = (pad + pos * rowH + rowH / 2f - anchor).coerceIn(0f, maxOf(0f, total - listH))
    return pad + pos * rowH - scroll
}

/** The tall glass panel: the category's name and logo, and the games as a list gliding under a highlight that stays put. */
@Composable
private fun ArtLibPanel(c: StyleCtx, accent: Color, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val count = c.games.size
    val keys = remember(c.games) { gameKeys(c.games) }
    val loop = loopRowOn(c)
    val vFocus = rememberLoopFocus(c)
    val pos = rememberGlidePos(vFocus, count, keys, loop)
    val select by rememberUpdatedState(c.actions.select)
    val open by rememberUpdatedState(c.actions.open)
    val latestFocus by rememberUpdatedState(c.focus)
    val latestCount by rememberUpdatedState(count)
    val latestLoop by rememberUpdatedState(loop)
    val lo by remember { derivedStateOf { floor(pos.value).toInt() } }
    val posFn: () -> Float = remember(pos) { { pos.value } }
    val shape = RoundedCornerShape(24.dp)
    val glass = themeDark(Color(0xFF0A0E15), 0.5f)
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Column(modifier.clip(shape).background(glass.copy(alpha = 0.78f)).border(1.dp, accent.copy(alpha = 0.18f), shape)) {
        // The category: its logo or symbol, its name, how many games; ◀ ▶ step through the categories like L2 / R2.
        val stopNow = c.stops.getOrNull(c.stop)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = if (compact) 8.dp else 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (compact) 28.dp else 36.dp), contentAlignment = Alignment.Center) {
                val logo = stopNow?.logo
                if (logo != null) AsyncImage(logo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Icon(stopNow?.icon ?: Icons.Filled.Apps, null, tint = accent, modifier = Modifier.size(if (compact) 22.dp else 28.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.categoryLabel, color = Color.White, fontSize = if (compact) 14.sp else 17.sp, lineHeight = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold)
                Text("$count games", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            if (c.stops.size > 1 && c.stop >= 0) {
                Icon(Icons.Filled.ChevronLeft, "Previous category", tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(30.dp).clip(CircleShape).pointerInput(Unit) { detectTapGestures { c.actions.category(-1) } }.padding(3.dp))
                Icon(Icons.Filled.ChevronRight, "Next category", tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(30.dp).clip(CircleShape).pointerInput(Unit) { detectTapGestures { c.actions.category(1) } }.padding(3.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val density = LocalDensity.current
            // (Room for a two-line name plus the console line in a mixed category, so the name never touches the highlight edge.)
            val rowH = if (c.mixed) (if (compact) 52.dp else 64.dp) else if (compact) 40.dp else 50.dp
            val rowHPx = with(density) { rowH.toPx() }
            val listHPx = with(density) { maxHeight.toPx() }
            // Update 395: with Endless on the highlight stays at one place (a little above the middle) and the list loops under it.
            // With Endless off the list never scrolls past its own ends (no empty space above the first or under the last game):
            // the highlight starts at the top, glides down to that place, and runs on to the bottom for the last games.
            val anchorPx = listHPx * 0.42f
            val padPx = with(density) { 4.dp.toPx() }
            val rowsFit = (listHPx / rowHPx).toInt() + 2
            val above = if (loop) (anchorPx / rowHPx).toInt() + 2 else rowsFit
            val below = if (loop) ((listHPx - anchorPx) / rowHPx).toInt() + 3 else rowsFit
            val total = count * rowHPx + 2 * padPx
            val focused = ArtLibNav.zone == ArtLibNav.Zone.LIST
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(rowH)
                    .graphicsLayer { translationY = artLibRowTop(posFn(), rowHPx, anchorPx, padPx, total, listHPx, loop) }
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = if (focused) 0.92f else 0.5f))
            )
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(onDragEnd = { acc = 0f }, onDragCancel = { acc = 0f }) { change, dy ->
                        change.consume()
                        acc += dy
                        val n = latestCount
                        if (n > 0) {
                            var target = latestFocus
                            // (Dragging the list up brings the next games in.)
                            while (acc <= -rowHPx) { acc += rowHPx; target = if (latestLoop) (target + 1).mod(n) else (target + 1).coerceAtMost(n - 1) }
                            while (acc >= rowHPx) { acc -= rowHPx; target = if (latestLoop) (target - 1).mod(n) else (target - 1).coerceAtLeast(0) }
                            if (target != latestFocus) { ArtLibNav.zone = ArtLibNav.Zone.LIST; select(target) }
                        }
                    }
                }
            ) {
                val first = if (loop) lo - above else (lo - above).coerceAtLeast(0)
                val last = if (loop) lo + below else (lo + below).coerceAtMost(count - 1)
                for (i in (first..last).filter { i -> !(loop && !drawnOnce(i - lo, count, above)) }) {
                    val real = if (loop) i.mod(count) else i
                    val gm = c.games[real]
                    key(if (loop) "${keys[real]}#${Math.floorDiv(i, count)}" else keys[real]) {
                        Box(
                            Modifier.fillMaxWidth().height(rowH).padding(horizontal = 8.dp)
                                .graphicsLayer {
                                    val p = posFn()
                                    translationY = artLibRowTop(p, rowHPx, anchorPx, padPx, total, listHPx, loop) + (i - p) * rowHPx
                                    // The ends of the list fade out softly (Endless off: only a row cut by an edge that has more beyond it).
                                    alpha = if (loop) (1.15f - abs(translationY + rowHPx / 2f - anchorPx) / (listHPx * 0.62f)).coerceIn(0f, 1f)
                                    else (minOf(translationY + rowHPx, listHPx - translationY) / (rowHPx * 1.2f)).coerceIn(0f, 1f)
                                }
                                .pointerInput(real) { detectTapGestures { ArtLibNav.zone = ArtLibNav.Zone.LIST; if (real == latestFocus) open() else select(real) } }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val sel = real == c.focus
                            // Selected: the whole name, fitted (R18), on the highlight; the others stay readable.
                            Column(verticalArrangement = Arrangement.Center) {
                                FitText(
                                    titles.display(gm.name), if (sel) onAccent else Color.White.copy(alpha = 0.94f), maxSize = if (compact) 14.sp else 16.sp,
                                    minSize = 11.sp, maxLines = 2, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                )
                                if (c.mixed) Text(titles.console(gm.platform), color = (if (sel) onAccent else Color.White).copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The game's logo (or its name), facts, achievement progress and the Play / Details buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtLibShowcase(c: StyleCtx, g: Game, accent: Color, compact: Boolean, modifier: Modifier) {
    val titles = LocalTitleOptions.current
    val meta = c.state.metadata[g.stableKey]
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Column(modifier.riseOnChange(g.stableKey, floor = 0.4f, ms = 240).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.34f)).padding(14.dp)) {
        // One slot for the logo or the name, so what is under it never moves when a game has a logo and the next one hasn't.
        LogoOrName(c.logo(g), titles.display(g.name), Modifier.height(if (compact) 54.dp else 92.dp).fillMaxWidth()) {
            StableFullText(titles.display(g.name), color = Color.White, fontSize = if (compact) 26.sp else 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        val facts = buildList {
            add(titles.console(g.platform))
            meta?.releaseYear?.let { add(it.toString()) }
            meta?.genres?.firstOrNull { it.isNotBlank() }?.let { add(it) }
            (meta?.developer?.takeIf { it.isNotBlank() } ?: meta?.publisher?.takeIf { it.isNotBlank() })?.let { add(it) }
            c.playtime(g)?.let { add(stylePlaytime(it, true)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in facts) Text(
                f, color = Color.White.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        c.progress(g)?.let { (e, t) ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("$e / $t", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(140.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (t > 0) (e.toFloat() / t).coerceIn(0f, 1f) else 0f).background(accent))
                }
            }
        }
        val story = meta?.summary?.takeIf { it.isNotBlank() }
        if (story != null) {
            Spacer(Modifier.height(10.dp))
            RollingStory(story, Color.White.copy(alpha = 0.94f), if (compact) 12.sp else 13.sp, if (compact) 17.sp else 19.sp, if (compact) 36.dp else 58.dp)
        }
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val playFocus = ArtLibNav.zone == ArtLibNav.Zone.PLAY
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(accent)
                    .then(if (playFocus) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                    .pointerInput(g.stableKey) { detectTapGestures { c.actions.play(g) } }
                    .padding(horizontal = 22.dp, vertical = if (compact) 8.dp else 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = onAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Play", color = onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                    .pointerInput(g.stableKey) { detectTapGestures { c.actions.open() } }
                    .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Details", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun ArtLibrarySettings() {
    MutedNote(
        "The game's art fills the screen; a slim rail on the left (All games, Favourites, Recent, Search, Sort, Achievements, Settings), a glass " +
            "panel with the category's games in a list that glides under a highlight, and the game's logo, facts, achievements and Play beside it. " +
            "▲ ▼ move through the list, ◀ opens the rail, ▶ goes to Play, A opens the game, L2 / R2 change category."
    )
}
