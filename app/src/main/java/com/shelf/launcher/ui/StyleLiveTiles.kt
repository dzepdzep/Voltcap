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
                        StableFullText(
                            LocalTitleOptions.current.display(g.name), modifier = Modifier.riseOnChange(g.stableKey, floor = 0.5f, ms = 180), color = ink.copy(alpha = 0.85f), fontSize = 20.sp,
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
    val lit = if (StyleNav.zone == StyleNav.Zone.TOP) StyleNav.index else tabs.indexOfFirst { it.first == 0 }
    // 529j (owner video VID20261113180355, compact screens: categories "suddenly spawn at the edge"): the words are a window
    // around the category you're in (one before, the next three), rebuilt at each L2 / R2 - every word changed in place and
    // the next one appeared whole at the right edge in one frame. Now the words lie on one strip that glides a word along
    // (LivePivot); at rest it is exactly the window it was.
    if (c.stop < 0 || c.stops.size <= 1 || LivePivot.legacy) LiveTabsWindow(c, ink, compact, tabs, lit) { move(it) }
    else LiveTabsPivot(c, ink, compact, tabs) { move(it) }
}

/** The category words as before 529j (one word, or the QA A/B's legacy): the window of whole words. */
@Composable
private fun LiveTabsWindow(c: StyleCtx, ink: Color, compact: Boolean, tabs: List<Pair<Int, String>>, lit: Int, move: (Int) -> Unit) {
    WholeChipRow(
        tabs.size, lit, Modifier.fillMaxWidth().editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.LIVE, "tabs"), "Category tabs"),
        spacing = 0.dp, hintColor = ink.copy(alpha = 0.38f), hintSize = if (compact) 16.sp else 22.sp
    ) { i -> LiveWord(tabs[i].first, tabs[i].second, StyleNav.on(StyleNav.Zone.TOP, i), ink, compact, move) }
}

/** One category word (update 136: as written; 481: shrinks, then slides, never off the edge). */
@Composable
private fun LiveWord(d: Int, label: String, on: Boolean, ink: Color, compact: Boolean, move: (Int) -> Unit) {
    val tap by rememberUpdatedState(move)
    StableFullText(
        // (Update 136: as written — no all-lower-case labels.)
        label,
        color = if (d == 0 || on) ink else ink.copy(alpha = 0.38f),
        fontSize = if (compact) 22.sp else 34.sp, fontWeight = if (on) FontWeight.Normal else FontWeight.Light, maxLines = 1,
        style = androidx.compose.material3.LocalTextStyle.current.copy(textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null),
        reserve = false,
        modifier = Modifier
            .padding(end = if (compact) 16.dp else 26.dp)
            .pointerInput(d) { detectTapGestures { if (d != 0) tap(d) } }
    )
}

/**
 * 529j: the category words on one strip. Word [j] on the strip is the category [j] - [LivePivot.Track.virt] steps from the
 * one you're in; the strip's place glides from the old category to the new one, so the words travel together - the one
 * leaving fades out past the left as it goes, the next comes in from the right as it enters (ChipGlide.inside over the
 * span of whole words, update 294's rule at rest).
 */
@Composable
private fun LiveTabsPivot(c: StyleCtx, ink: Color, compact: Boolean, tabs: List<Pair<Int, String>>, move: (Int) -> Unit) {
    val n = c.stops.size
    val ds = tabs.map { it.first }
    val dMin = ds.minOrNull() ?: 0
    val dMax = ds.maxOrNull() ?: 0
    val track = androidx.compose.runtime.remember { LivePivot.Track(c.stop) }
    val target = track.follow(c.stop, n)
    val anim = rememberAnimationsEnabled()
    val pv = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(target.toFloat()) }
    androidx.compose.runtime.LaunchedEffect(target) {
        if (!anim || kotlin.math.abs(pv.value - target) > 3f) pv.snapTo(target.toFloat())
        else pv.animateTo(target.toFloat(), tween(LivePivot.MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        track.settle(target)
    }
    val js = (track.lo + dMin..track.hi + dMax).toList()
    val hidden = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val hintSize = if (compact) 16.sp else 22.sp
    androidx.compose.ui.layout.Layout(
        content = {
            js.forEach { j ->
                androidx.compose.runtime.key(j) {
                    val d = j - target
                    val i = ds.indexOf(d)
                    LiveWord(d, if (d == 0) c.categoryLabel else c.stopAt(d)?.label.orEmpty(), i >= 0 && StyleNav.on(StyleNav.Zone.TOP, i), ink, compact, move)
                }
            }
            Text("+${hidden.intValue} ›", color = ink.copy(alpha = 0.38f), fontSize = hintSize, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        },
        modifier = Modifier.fillMaxWidth().clipToBounds()
            .editablePart(LayoutParts.id(com.shelf.launcher.data.HomeLayout.LIVE, "tabs"), "Category tabs")
    ) { ms, cs ->
        val hint = ms.last().measure(androidx.compose.ui.unit.Constraints())
        val width = if (cs.hasBoundedWidth) cs.maxWidth else 4000
        val gap = 4.dp.roundToPx()
        val room = (width - hint.width - gap).coerceAtLeast(0)
        val ps = ms.dropLast(1).map { it.measure(androidx.compose.ui.unit.Constraints(0, room, 0, cs.maxHeight)) }
        val ws = IntArray(ps.size) { ps[it].width }
        val first = js.firstOrNull() ?: 0
        val atRest = LivePivot.restAll(ws, first, track.lo, track.hi, ds, width, hint.width + gap)
        hidden.intValue = atRest[target]?.hidden ?: 0
        val height = (ps.maxOfOrNull { it.height } ?: 0).coerceIn(cs.minHeight, cs.maxHeight)
        layout(width, height) {
            val p = pv.value.coerceIn(track.lo.toFloat(), track.hi.toFloat())
            val base = kotlin.math.floor(p).toInt().coerceAtMost(track.hi)
            val frac = p - base
            val a = atRest[base]; val b = atRest[(base + 1).coerceAtMost(track.hi)] ?: a
            if (a == null || b == null) return@layout
            val l = ChipGlide.lerp(a.left, b.left, frac); val r = ChipGlide.lerp(a.right, b.right, frac)
            for (k in ps.indices) {
                val x = ChipGlide.lerp(a.xs[k], b.xs[k], frac)
                val al = ChipGlide.inside(x, ws[k].toFloat(), l, r)
                val y = (height - ps[k].height) / 2
                if (al > 0.001f) ps[k].placeWithLayer(kotlin.math.round(x).toInt(), y) { alpha = al } else ps[k].place(width * 2, y)
            }
            if (QaTrace.on && (pv.isRunning || p != target.toFloat())) ChipGlide.qa("live",
                if (track.hi > track.lo) (p - track.lo) / (track.hi - track.lo) else 1f,
                FloatArray(ps.size) { ChipGlide.lerp(a.xs[it], b.xs[it], frac) },
                FloatArray(ps.size) { ChipGlide.inside(ChipGlide.lerp(a.xs[it], b.xs[it], frac), ws[it].toFloat(), l, r) })
            val ha = ChipGlide.lerp(if (a.hidden > 0) 1f else 0f, if (b.hidden > 0) 1f else 0f, frac)
            if (ha > 0.001f) hint.placeWithLayer(width - hint.width, (height - hint.height) / 2) { alpha = ha } else hint.place(width * 2, 0)
        }
    }
}

/** 529j: the category words' strip (pure parts, LivePivot529Test). */
internal object LivePivot {
    const val MS = 300
    /** QA A/B only (voltcap_test_chipglide legacy): the old window rebuilt at each step. */
    @Volatile var legacy = false

    /** Where the strip is: [virt] counts every step (the category list goes round), [lo]..[hi] the places a glide covers. */
    class Track(start: Int) {
        var last = start; var virt = 0; var lo = 0; var hi = 0
        /** The strip's place for the category [stop] of [n]: one step on (or back) the shorter way round. */
        fun follow(stop: Int, n: Int): Int {
            if (stop != last && n > 0) {
                var d = (stop - last).mod(n)
                if (d > n / 2) d -= n
                virt += d; last = stop
                lo = minOf(lo, virt); hi = maxOf(hi, virt)
            }
            return virt
        }
        /** The glide reached [v]: only its words are needed now. */
        fun settle(v: Int) { if (v == virt) { lo = v; hi = v } }
    }

    /** The words at rest with the strip at one place: each word's x, the span of whole words, how many are past the end. */
    class Rest(val xs: FloatArray, val left: Float, val right: Float, val hidden: Int)

    /**
     * The strip at rest at place [v]: word j (index j - [first] in [ws]) at its place, the word before the category you're in
     * (d = -1, when it is shown) at the left edge; the shown words are the ones with d in [ds] that fit whole in [width] -
     * less [hintRoom] when some don't (update 294's "+N ›"). The category you're in always counts as shown.
     */
    fun rest(ws: IntArray, first: Int, v: Int, ds: List<Int>, width: Int, hintRoom: Int): Rest {
        val xs = FloatArray(ws.size)
        val vi = v - first
        val anchor = if (-1 in ds && vi - 1 in ws.indices) ws[vi - 1] else 0
        var x = anchor.toFloat() - (0 until vi.coerceIn(0, ws.size)).sumOf { ws[it] }.toFloat()
        for (k in ws.indices) { xs[k] = x; x += ws[k] }
        val shown = ds.sorted().map { v + it - first }.filter { it in ws.indices }
        fun whole(limit: Int): List<Int> = shown.filter { k -> xs[k] >= -0.5f && xs[k] + ws[k] <= limit + 0.5f || k == vi }
        var fit = whole(width)
        val missing = shown.size - fit.size
        if (missing > 0) fit = whole(width - hintRoom)
        val l = fit.minOfOrNull { xs[it] } ?: 0f
        val r = fit.maxOfOrNull { xs[it] + ws[it] } ?: 0f
        return Rest(xs, l, r, shown.size - fit.size)
    }

    fun restAll(ws: IntArray, first: Int, lo: Int, hi: Int, ds: List<Int>, width: Int, hintRoom: Int): Map<Int, Rest> =
        (lo..hi).associateWith { rest(ws, first, it, ds, width, hintRoom) }
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
                    maxLines = 2
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

/** Update 426: Live tiles' own dark slate, used instead of a theme background that is the game's art. */
@Composable
internal fun LiveSlateBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF26292E), Color(0xFF15171A))))
        val r = size.minDimension * 0.75f
        drawCircle(Color.White.copy(alpha = 0.04f), r, Offset(size.width * 0.12f, size.height * 0.05f), style = Stroke(width = r * 0.18f))
        drawCircle(Color.White.copy(alpha = 0.03f), r * 0.9f, Offset(size.width * 0.95f, size.height * 1.05f), style = Stroke(width = r * 0.14f))
    }
}
