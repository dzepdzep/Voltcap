package com.shelf.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 529k (owner, 13 Nov: every layout's own Details / Stats / Options / Achievements pages, "literally all popups" - not the shared
 * expanded page in the layout's colours): the shelf family - Game shelf, Case stacks, Console stack, Coverflow details (one window
 * scene, INDEX_CARD). Their pages are further cards of the same make: the next index card is drawn up out of the shelf's slot
 * (from below) over the one it replaces, which sinks back a little and fades; going back runs it the other way. The case, the
 * module, the big cover and the card's own wood / slate / metal stay put - the window never turns into another design.
 */

/** The shelf family's pages in key order (Info, Details, Achievements, Options; L1 / R1 walk them). */
internal val SHELF_TABS = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/** The room a shelf-family page asks for: most of the window's height, so a list has whole rows (Pure). */
internal fun shelfPageHeight(maxH: Dp): Dp = (maxH * 0.86f).coerceAtLeast(200.dp)

/**
 * The inside of a shelf-family card: [main] (Info, as each window draws it) or one of its pages - the page's name on the card's
 * label, the game's name, the page in a well of the card's own ground ([well]) and the card's keys ([key], each window's own
 * style). A page change draws the next card up from the slot; with animations off it is immediate. [main] gets `live` = false
 * for the copy on its way out: its keys must not keep focus requesters (WinAction.inert).
 */
@Composable
internal fun ShelfFamilyPages(
    ctx: WinSceneCtx,
    modifier: Modifier,
    label: Color,
    ink: Color,
    well: Color,
    /** A page's key drawn as a full-width bar of this window's own make (SceneKey(wide = true)) - [PageBars] sets its width. */
    key: @Composable (WinAction) -> Unit,
    /** 529o: false where the window already shows the game's name in full beside the card (Coverflow details: the home's big
     *  title next to the case) - the page's card then spends that room on the page. */
    nameOnCard: Boolean = true,
    /** 529o: the height of a page's card; Coverflow details gives its pages the whole panel (the Info panel is shorter). */
    pageHeight: Dp = shelfPageHeight(ctx.maxH),
    main: @Composable ColumnScope.(live: Boolean, keys: List<WinAction>) -> Unit
) {
    val anim = rememberAnimationsEnabled()
    val tabs = SHELF_TABS.filter { it in ctx.pages }
    val keysOf = rememberPageKeys(ctx)
    AnimatedContent(
        targetState = ctx.page,
        transitionSpec = {
            val dir = pageDirection(tabs, initialState, targetState)
            if (!anim) EnterTransition.None togetherWith ExitTransition.None
            // (Forward: the next card rises out of the slot; back: the card drops back and the one before comes down to it.)
            // (529l, frame review k529s1: the leaving card faded over the whole 280 ms while the new one was already whole after
            // 110 ms - for several frames both names and both texts read through each other. The leaving card is gone in 90 ms
            // and the new one fades in just behind it, so there is one readable card per frame.)
            else (slideInVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> h * dir / 3 } + fadeIn(tween(150, delayMillis = 60))) togetherWith
                (slideOutVertically(tween(NATIVE_PAGE_MS, easing = FastOutSlowInEasing)) { h -> -h * dir / 10 } + fadeOut(tween(90)))
        },
        contentKey = { it },
        modifier = modifier.onPreviewKeyEvent { e ->
            // (L1 / R1 walk Info, Details, Achievements, Options - as their keys open them, as on Tile home and Storage boxes.)
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val step = when (e.key) { Key.ButtonL1 -> -1; Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
            val to = pageStep(tabs, ctx.page, step)
            if (to != ctx.page) ctx.open(to)
            true
        },
        label = "shelfPage"
    ) { p ->
        val live = p == ctx.page
        androidx.compose.runtime.CompositionLocalProvider(LocalShownPage provides p) {
        if (p == WinPage.MAIN) Column { main(live, keysOf(p, live)) }
        else Column(Modifier.height(pageHeight)) {
            Text(winPageTitle(p).uppercase(), color = label, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            if (nameOnCard) StableFullText(ctx.name, color = ink, fontSize = 17.sp, maxLines = 2, fontWeight = FontWeight.Bold, reserve = false)
            Spacer(Modifier.height(if (nameOnCard) 8.dp else 6.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(well).padding(6.dp)) {
                // (The page draws on this card's well, without the case and name the card already shows.)
                androidx.compose.runtime.CompositionLocalProvider(LocalNativePageHost provides NativePageHost(Color.Transparent, dark = true, showsCover = true, showsName = true)) {
                    ctx.pageFor(p, Modifier.fillMaxSize(), live)
                }
            }
            Spacer(Modifier.height(8.dp))
            // (529o, owner #15 6866376184: the page's keys as full-width bars like the Info card's, not short mixed-width keys with
            // Back alone on a second row; their room is reserved under the well, which gets the rest.)
            PageBars(keysOf(p, live), 6.dp) { key(it) }
        }
        }
    }
}

/**
 * 529m (KONKR frame review k529l2): the page a native page scene's copy is drawing - the leaving copy of a page change draws
 * its own page, not the one that is opening (Game shelf's leaving Info card showed the Details body with a second cover for
 * the whole change). Null outside a page change: [WinSceneCtx.page].
 */
internal val LocalShownPage = androidx.compose.runtime.compositionLocalOf<WinPage?> { null }

/**
 * 529m: each page's own keys as they last were while it was open, for the copy that is leaving (its keys were the opening
 * page's: Details' card showed "Full list"). Inert for a copy that is not live (WinAction.inert).
 */
@Composable
internal fun rememberPageKeys(ctx: WinSceneCtx): (WinPage, Boolean) -> List<WinAction> {
    val seen = androidx.compose.runtime.remember { HashMap<WinPage, List<WinAction>>() }
    val now = sceneKeys(ctx)
    seen[ctx.page] = now
    return { p, live -> (if (p == ctx.page) now else seen[p] ?: now).map { if (live) it else it.inert() } }
}

/**
 * 529o (owner, #15 6866376184 - compact pages): a page's keys as bars that span the card, as few rows as keep every word whole
 * on one line (Play | Full list | Back), each row exactly the card's width and its bars the same height - never short keys with
 * Back left alone on a second line, and not three stacked bars that squeeze the page. [key] draws a bar that fills
 * the width it is given (SceneKey(wide = true)).
 */
@Composable
internal fun PageBars(keys: List<WinAction>, gap: Dp, key: @Composable (WinAction) -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { c ->
        val w = c.maxWidth
        val g = gap.roundToPx()
        val parts = keys.mapIndexed { i, k -> subcompose(i) { Box(contentAlignment = androidx.compose.ui.Alignment.Center) { key(k) } }.firstOrNull() }
        // (the round ••• keeps its size - it is not a bar - and stays last; the bars share the rest of its row)
        val round = keys.map { PopupLab.active.actions && PopupLab.isMore(it) }
        val need = parts.map { it?.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) ?: 0 }
        val rows = pageBarRows(need, w, g)
        var y = 0
        val placed = ArrayList<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()
        rows.forEachIndexed { r, row ->
            // (Every row spans the card: each bar gets its word's width and an equal part of what is left.)
            val widths = pageBarWidths(row.map { need[it] }, w, g, row.map { round[it] })
            val rowH = row.indices.maxOf { c -> parts[row[c]]?.minIntrinsicHeight(widths[c]) ?: 0 }
            var x = 0
            row.forEachIndexed { col, i ->
                val bw = widths[col]
                val pl = parts[i]?.measure(androidx.compose.ui.unit.Constraints(minWidth = bw, maxWidth = bw, minHeight = rowH, maxHeight = rowH))
                if (pl != null) placed += Triple(pl, x, y)
                x += bw + g
            }
            y += rowH + if (r < rows.lastIndex) g else 0
        }
        layout(w, y) { placed.forEach { (pl, x, yy) -> pl.place(x, yy) } }
    }
}

/**
 * 529o: which bars share a row - the fewest rows in which every bar's word fits on one line (at most three to a row); among
 * those, the order closest to the keys' own (Play first). Play | Full details | Back too wide for one row at large text becomes
 * Play | Back over a whole-width Full details, not three stacked bars.
 */
internal fun pageBarRows(need: List<Int>, width: Int, gap: Int): List<List<Int>> {
    val n = need.size
    if (n == 0) return emptyList()
    fun fits(row: List<Int>) = row.size == 1 || row.size <= 3 && row.sumOf { need[it] } + gap * (row.size - 1) <= width
    for (k in 1..n) {
        var best: List<List<Int>>? = null; var bestCost = Int.MAX_VALUE
        // every way to put the bars into k rows (row of each bar), rows ordered by their first bar, bars in their own order
        val total = Math.pow(k.toDouble(), n.toDouble()).toInt()
        for (code in 0 until total) {
            var c = code
            val rows = List(k) { ArrayList<Int>() }
            for (i in 0 until n) { rows[c % k].add(i); c /= k }
            if (rows.any { it.isEmpty() }) continue
            if (rows.zipWithNext().any { (x, y) -> x.first() > y.first() }) continue
            if (!rows.all { fits(it) }) continue
            val flat = rows.flatten()
            val cost = flat.indices.sumOf { kotlin.math.abs(flat[it] - it) }
            if (cost < bestCost) { bestCost = cost; best = rows }
        }
        if (best != null) return best
    }
    return need.indices.map { listOf(it) }
}

/**
 * 529o (owner #15 6866376184: "a deliberate usable content area plus clear scrolling affordances"): a page's list scrolls as
 * before; on a native page (a layout's own Details / Achievements / Options card) its edge fades where rows run on and a small
 * chevron in the card's ink says there is more below (or above). Elsewhere it is the plain verticalScroll it replaces.
 */
@Composable
internal fun Modifier.pageScroll(): Modifier {
    val state = androidx.compose.foundation.rememberScrollState()
    val host = LocalNativePageHost.current ?: return this.then(Modifier.verticalScroll(state))
    return this.pageScrollCue(state, if (host.dark) Color.White else Color(0xFF2E3640)).verticalScroll(state)
}

/** 529o: [pageScroll]'s cue - drawn over the faded edge, never faded itself; it reads the scroll while drawing. */
internal fun Modifier.pageScrollCue(state: androidx.compose.foundation.ScrollState, ink: Color): Modifier =
    this.drawWithContent {
        drawContent()
        val r = 9.dp.toPx(); val arm = 4.dp.toPx(); val sw = 1.8.dp.toPx()
        fun cue(down: Boolean) {
            val cx = size.width / 2f
            val cy = if (down) size.height - r - 2.dp.toPx() else r + 2.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.55f), r, androidx.compose.ui.geometry.Offset(cx, cy))
            drawCircle(ink.copy(alpha = 0.35f), r, androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val d = if (down) 1f else -1f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - arm, cy - d * arm / 2f); lineTo(cx, cy + d * arm / 2f); lineTo(cx + arm, cy - d * arm / 2f)
            }
            drawPath(p, Color.White.copy(alpha = 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        if (state.canScrollForward) cue(down = true)
        if (state.canScrollBackward) cue(down = false)
    }.sceneFadeEdges(state, 22.dp)

/** 529o: the widths of one row of bars - each its word's width plus an equal part of the room left, the row exactly [width]. */
internal fun pageBarWidths(need: List<Int>, width: Int, gap: Int, fixed: List<Boolean> = need.map { false }): List<Int> {
    if (need.isEmpty()) return emptyList()
    if (need.size == 1) return listOf(if (fixed[0]) need[0] else width)
    val inner = width - gap * (need.size - 1)
    val grow = need.indices.filter { !fixed[it] }
    if (grow.isEmpty()) return need
    val spare = (inner - need.sum()).coerceAtLeast(0)
    val ws = need.indices.map { if (fixed[it]) need[it] else need[it] + spare / grow.size }.toMutableList()
    // a row that is too narrow for the words shares the room evenly (a fixed key keeps its size); the last bar takes the rounding
    if (need.sum() > inner) {
        val left = inner - need.indices.filter { fixed[it] }.sumOf { need[it] }
        for (i in grow) ws[i] = (left / grow.size).coerceAtLeast(0)
    }
    val last = grow.last()
    ws[last] = inner - ws.indices.filter { it != last }.sumOf { ws[it] }
    return ws
}
