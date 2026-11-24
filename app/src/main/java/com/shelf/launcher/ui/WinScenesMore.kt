package com.shelf.launcher.ui

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material.icons.filled.Description

import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.animation.togetherWith
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Update 421 (popup audit, priority 1): the layouts that still opened the generic window get one that belongs to them.
 * Each is its own composition (shape, material, typography, entrance) built on the same WinSceneCtx as the 19 older
 * scenes: Play / Continue is the first control (ctx.playFocus is attached by SceneControl), every page's buttons and
 * every tool / chip stays reachable with the D-pad, B closes (GameWindow), the other pages draw ctx.pageBody inside the
 * scene. None of them owns the opening of a physical piece: on Game shelf / Case stacks / Record crate / Card binder /
 * Disc station the real case / sleeve / disc still comes out beside it (InspectLayout), the scene only replaces the
 * plain window next to it. Text never ends in "…": names wrap and shrink (StableFullText), key rows scroll sideways.
 */

/** The facts the scenes print, label to value, in one order everywhere. */
internal fun sceneFacts(ctx: WinSceneCtx): List<Pair<String, String>> = listOfNotNull(
    "Console" to ctx.console,
    ctx.year?.let { "Year" to it },
    ctx.developer?.let { "Made by" to it },
    ctx.genres?.let { "Genre" to it },
    "Played" to ctx.played,
    ctx.last?.let { "Last played" to it },
    if (ctx.total > 0) "Trophies" to "${ctx.earned} / ${ctx.total}" else null
)

/** Update 471: the facts a window lists, without those it already shows elsewhere ([omit], by label). */
internal fun shownFacts(ctx: WinSceneCtx, omit: Set<String>): List<Pair<String, String>> = shownFacts(sceneFacts(ctx), omit)
internal fun shownFacts(facts: List<Pair<String, String>>, omit: Set<String>): List<Pair<String, String>> = facts.filter { it.first !in omit }

/** Every control of the page: the page's buttons first (Play is the very first), then — on the first page — tools and chips.
 *  (Update 529b, owner: "the last thing that's shown from the options should be the 3 dots, not anything else beside it" - the
 *  round ••• More / Less goes after the chips too: Game shelf's flowing row put "Saves: …" beside it, Flip 2 mlast529b.) */
internal fun sceneKeys(ctx: WinSceneCtx): List<WinAction> =
    (ctx.buttons + (if (ctx.page == WinPage.MAIN) ctx.tools + ctx.chips else emptyList())).let { all ->
        all.filterNot(PopupLab::isMore) + all.filter(PopupLab::isMore)
    }

/** 673g: inside a uniform key group - the key takes exactly the size the group gives every key (KeyRow / UniformKeyRow). */
internal val LocalUniformKey = androidx.compose.runtime.compositionLocalOf { false }

/** 673g: drawn as a status line under a key group, not as a key (WinAction.status). */
internal val LocalKeyAsStatus = androidx.compose.runtime.compositionLocalOf { false }

/** 673g: a key's own drawing fills the size its uniform group gives every key (nothing outside a group). */
@Composable
internal fun Modifier.uniformFill(): Modifier = if (LocalUniformKey.current) this.fillMaxSize() else this

/** 673g: the room each side of a key row that scrolls - its ‹ › show there are more keys that way. */
internal val KEY_ROW_GUTTER = 16.dp

/**
 * 673g (owner brief 15 Nov, "Perfectly uniform console-game menus"): a window's keys as ONE row of identical keys - each as
 * wide as the widest word needs, all as tall as the tallest - sharing the row's whole width where they fit; where they don't,
 * the row scrolls sideways a whole key at a time (UniformKeyRow). Status lines - Save sync not set up, the performance
 * summary (WinAction.status) - are not keys: they stand under the row. More stays the last key (sceneKeys).
 * (Was update 423's wrapping FlowRow: keys of every width on as many lines as they needed.)
 */
@Composable
internal fun KeyRow(keys: List<WinAction>, gap: Dp = 6.dp,
    /** D354 (681p): two-line keys only where one-line keys would show a single key at a time (uniformRowPick). */
    twoLineOnlyWhenAlone: Boolean = false,
    /** 682t: the main page's key count - on the window's own pages the keys keep the main page's slots (rememberKeySlots). */
    slots: Int = 0, key: @Composable (WinAction) -> Unit) {
    val actions = keys.filterNot { it.status }
    val status = keys.filter { it.status }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (actions.isNotEmpty()) UniformKeyRow(actions, gap, twoLineOnlyWhenAlone = twoLineOnlyWhenAlone, slots = slots, key = key)
        if (status.isNotEmpty()) androidx.compose.runtime.CompositionLocalProvider(LocalKeyAsStatus provides true) { status.forEach { key(it) } }
    }
}

/**
 * 673g (pure): a uniform key row's geometry - [n] keys that each need [need] px, in [width] px, [gap] px apart, [gutter] px of
 * room each side when it scrolls. Where all fit, every key gets an equal share of the whole width. Where not, as many whole keys
 * as fit show at once (each widened to fill that view exactly, never narrower than [need]) and the row scrolls by whole keys.
 */
internal data class UniformRowGeom(val keyW: Int, val visible: Int, val scrolls: Boolean, val pitch: Int, val maxOffset: Int, val viewStart: Int, val viewW: Int,
    /** 682t: the first key's own width (Play keeping the main page's slot) - 0: as every key. */
    val firstW: Int = 0)

internal fun uniformRowGeom(n: Int, need: Int, width: Int, gap: Int, gutter: Int): UniformRowGeom {
    if (n <= 0) return UniformRowGeom(need, 0, false, need + gap, 0, 0, 0)
    // (owner brief: "Never stretch a single Play button to occupy an entire empty row" - one key keeps its own width, centred)
    if (n == 1) return UniformRowGeom(need, 1, false, need + gap, 0, ((width - need) / 2).coerceAtLeast(0), need)
    val natural = n * need + (n - 1) * gap
    if (width >= natural) {
        val w = (width - (n - 1) * gap) / n
        return UniformRowGeom(w, n, false, w + gap, 0, 0, n * w + (n - 1) * gap)
    }
    val room = (width - 2 * gutter).coerceAtLeast(1)
    val k = ((room + gap) / (need + gap)).coerceIn(1, n)
    // (one key at a time - a narrow window at large text: the key keeps its own width between the arrows, not stretched)
    val w = if (k == 1) need else maxOf(need, (room - (k - 1) * gap) / k)
    val view = k * w + (k - 1) * gap
    return UniformRowGeom(w, k, true, w + gap, (n - k) * (w + gap), ((width - view) / 2).coerceAtLeast(0), view)
}

/**
 * 682t (pure; owner 18 Nov: "keep Play button size and positioning consistent between native pages within each layout"): a page's
 * key row that has fewer keys than the main page's [slots] keeps the main page's key size and starts where it does - Play stays
 * the same size in the same place, the page's other keys in the main page's next slots. Null where the main page's row would
 * not fit all its keys whole ([need] wider than a slot): the usual row then.
 */
internal fun uniformSlotGeom(n: Int, slots: Int, need: Int, width: Int, gap: Int, mainFit: Boolean = false): UniformRowGeom? {
    if (n <= 0 || slots <= n) return null
    val w = (width - (slots - 1) * gap) / slots
    if (w <= 0) return null
    if (need <= w) return UniformRowGeom(w, n, false, w + gap, 0, 0, n * w + (n - 1) * gap)
    // A page key wider than a slot ("FULL DETAILS" at x1.3, RP Classic 22 Nov: Dashboard, Library, Card binder): Play keeps its
    // slot and the page's other keys share the rest - only where the main page's row showed all its keys whole in their slots
    // ([mainFit]), so Play's word is known to fit its slot. (682v: without that check Play was cut where the main row had
    // scrolled - ShelfText CUBE / CRATE x1.3.)
    if (!mainFit || n < 2) return null
    val rest = (width - w - gap - (n - 2) * gap) / (n - 1)
    if (rest < need) return null
    return UniformRowGeom(rest, n, false, rest + gap, 0, 0, width, firstW = w)
}

/** 682v: [rememberKeySlots] for a window's own key row (sceneKeys). */
@Composable
internal fun sceneKeySlots(ctx: WinSceneCtx): Int = rememberKeySlots(ctx.page, sceneKeys(ctx))

/** 682t: the main page's action-key count, kept while the window turns its own pages (KeyRow's slots) - counted on the main
 *  page with More closed (its open list - "Less" - is not the row Play sits in). [keys] are this page's. */
@Composable
internal fun rememberKeySlots(page: WinPage, keys: List<WinAction>): Int {
    val kept = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val count = keys.count { !it.status }
    if (page == WinPage.MAIN && keys.none { it.label == "Less" } && kept.intValue != count) kept.intValue = count
    return pageKeySlots(page, kept.intValue)
}

/**
 * 682y (Card binder, KONKR / RP Classic x1.3, run pk13 / pr13): a main page with Play alone kept 1 slot, so its pages' rows
 * were laid out each on its own - Play half the row on Details / Achievements (Play + Full details / Full list) but a short
 * key in the middle on Options (Play alone). A page's row has room for Play and the page's own key at least: Play keeps the
 * same slot on every page.
 */
internal fun pageKeySlots(page: WinPage, mainCount: Int): Int = if (page == WinPage.MAIN) mainCount else PAGE_SLOTS

/** 682ab: a window's own page lays its keys out in two slots - Play and the page's own key (Full details, Full list). */
internal const val PAGE_SLOTS = 2

/** 682ab: the page key a page row is measured with (UniformKeyRow) - the widest of the pages' own keys. */
internal val PAGE_KEY_REF = WinAction("Full details", androidx.compose.material.icons.Icons.Filled.Description, {})

/** 682ab: a key drawn only to be measured - no focus, touch or sound (SceneControl). */
internal val LocalMeasureOnly = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * 682ab (pure; KONKR + RP Classic x1.0 fk10 / fr10: Play was a third on Achievements, half on Details and a short key in the
 * middle on Options - Card binder, Console hub, Cube, Cinema, Record crate, Resume): a page's keys always take the two halves
 * of the row - Play the first half, the page's own key the second; Play alone keeps the first half. Where a word doesn't fit
 * half the row on one line the keys take two lines ([need2], the longest word); where not even that fits, null (the usual row).
 */
internal fun pageRowGeom(n: Int, need: Int, need2: Int, width: Int, gap: Int): UniformRowGeom? {
    if (n !in 1..2) return null
    val w = (width - gap) / 2
    if (w <= 0 || (need > w && (need2 <= 0 || need2 > w))) return null
    return UniformRowGeom(w, n, false, w + gap, 0, 0, n * w + (n - 1) * gap)
}

/**
 * 674c (pure, item 4 of the 15 Nov directive - narrow windows at x1.3 showed one key at a time): [need] is the widest key's
 * one-line width, [need2] the width at which every key's word takes two lines at most (0: a key can't take a second line).
 * The two-line keys are used only where the one-line row would scroll and the two-line one shows more keys at once.
 */
internal fun uniformRowPick(n: Int, need: Int, need2: Int, width: Int, gap: Int, gutter: Int, onlyWhenAlone: Boolean = false): UniformRowGeom {
    val one = uniformRowGeom(n, need, width, gap, gutter)
    if (!one.scrolls || need2 <= 0 || need2 >= need) return one
    // (D354, 681p: a window whose keys' words must stay on one line - Pocket Quest's "Play game" - keeps one-line keys
    // while at least two show at once; the row scrolls to the rest.)
    if (onlyWhenAlone && one.visible >= 2) return one
    val two = uniformRowGeom(n, need2, width, gap, gutter)
    return if (!two.scrolls || two.visible > one.visible) two else one
}

/** 674c: a two-line key's width - its longest word, or about 62 % of its one-line width (a balanced two-line split of two or
 *  three words plus the icon and padding), whichever is wider. */
internal fun twoLineKeyWidth(minW: Int, maxW: Int): Int = maxOf(minW, kotlin.math.ceil(maxW * 0.62f).toInt()).coerceAtMost(maxW)

/** 673g (pure): the scroll that shows key [i] whole, from [current], moving by whole keys and as little as possible. */
internal fun uniformRowScrollFor(i: Int, current: Int, g: UniformRowGeom): Int {
    if (!g.scrolls || i < 0) return 0
    val left = i * g.pitch
    val to = when {
        left < current -> left
        left + g.keyW > current + g.viewW -> left + g.keyW - g.viewW
        else -> current
    }
    return to.coerceIn(0, g.maxOffset)
}

/**
 * 673g: [keys] in one row of identical keys (uniformRowGeom). Scrolling, the row shows whole keys between its ‹ › and moves a
 * whole key at a time to the key the D-pad is on (or follows a finger and settles on whole keys); the keys outside the view are
 * clipped away (not drawn, not counted as shown). Each key is drawn by [key] - the layout's own look - at the group's size.
 */
@Composable
internal fun UniformKeyRow(keys: List<WinAction>, gap: Dp, twoLine: Boolean = true, twoLineOnlyWhenAlone: Boolean = false, slots: Int = 0, key: @Composable (WinAction) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(density) { gap.roundToPx() }
    val gutterPx = with(density) { KEY_ROW_GUTTER.roundToPx() }
    var geom by remember { mutableStateOf<UniformRowGeom?>(null) }
    var focused by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val anim = rememberAnimationsEnabled()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(focused, geom) {
        val g = geom ?: return@LaunchedEffect
        val to = uniformRowScrollFor(focused, offset.targetValue.toInt().coerceIn(0, g.maxOffset), g).toFloat()
        if (to != offset.targetValue) { if (anim) offset.animateTo(to, androidx.compose.animation.core.tween(170)) else offset.snapTo(to) }
    }
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { d ->
        val g = geom
        if (g != null && g.scrolls) scope.launch { offset.snapTo((offset.value - d).coerceIn(0f, g.maxOffset.toFloat())) }
    }
    val keyW = remember { IntArray(2) }
    val mainFit = remember { BooleanArray(1) }
    val pageRef = slots == PAGE_SLOTS
    val inner = remember(gapPx, pageRef) { uniformKeysPolicy(gapPx, keyW, { pageRef }) { offset.value } }
    androidx.compose.ui.layout.Layout(
        content = {
            // (The row says it scrolls, as a scrolling list does - accessibility, and the popup gate, which counts a key scrolled
            // out of its row as reachable, not cut.)
            Box(Modifier.clipToBounds().semantics {
                horizontalScrollAxisRange = androidx.compose.ui.semantics.ScrollAxisRange(
                    value = { offset.value }, maxValue = { (geom?.maxOffset ?: 0).toFloat() })
            }) {
                androidx.compose.ui.layout.Layout(content = {
                    // (682ab: on a window's own page the row is measured with the page key that asks the most room ("Full
                    // details") even where the page has no such key - Play is the same size on Options as on Details. The
                    // reference is drawn outside the row's view (clipped), without semantics, focus or touch - LocalMeasureOnly.)
                    if (slots == PAGE_SLOTS) Box(Modifier.clearAndSetSemantics { }, propagateMinConstraints = true) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalUniformKey provides true, LocalMeasureOnly provides true) { key(PAGE_KEY_REF) }
                    }
                    keys.forEachIndexed { i, a ->
                        Box(Modifier.onFocusChanged { if (it.hasFocus) focused = i }, propagateMinConstraints = true) {
                            androidx.compose.runtime.CompositionLocalProvider(LocalUniformKey provides true) { key(a) }
                        }
                    }
                }, measurePolicy = inner)
            }
        },
        modifier = Modifier.fillMaxWidth()
            .draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, enabled = geom?.scrolls == true,
                onDragStopped = { _ ->
                    val g = geom
                    if (g != null && g.scrolls) {
                        val to = (kotlin.math.round(offset.value / g.pitch) * g.pitch).coerceIn(0f, g.maxOffset.toFloat())
                        if (anim) offset.animateTo(to, androidx.compose.animation.core.tween(170)) else offset.snapTo(to)
                    }
                })
            .drawWithContent {
                drawContent()
                val g = geom ?: return@drawWithContent
                if (g.scrolls) drawKeyRowChevrons(g, offset.value)
            }
    ) { ms, c ->
        val box = ms.first()
        val n = keys.size
        // (A key whose drawing can't be asked for its size - one built on a SubcomposeLayout - would throw here; the row then
        // shares its width out equally, as a row that fits does, and keeps the keys' own height.)
        val need = runCatching { box.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }.getOrElse {
            if (c.hasBoundedWidth) ((c.maxWidth - (n - 1).coerceAtLeast(0) * gapPx) / n.coerceAtLeast(1)) else 0 }
        val width = if (c.hasBoundedWidth) c.maxWidth else n * need + (n - 1).coerceAtLeast(0) * gapPx
        // (674c: two-line keys where the one-line row would show fewer keys - see uniformRowPick.)
        // ([twoLine] false: a row whose height the window can't give - Storage's keys on its name line at x1.3.)
        val need2 = if (!twoLine) 0 else runCatching { box.minIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }.getOrElse { 0 }
        val g = (if (slots == PAGE_SLOTS) pageRowGeom(n, need, need2, width, gapPx) else uniformSlotGeom(n, slots, need, width, gapPx, mainFit[0])) ?: uniformRowPick(n, need, need2, width, gapPx, gutterPx, onlyWhenAlone = twoLineOnlyWhenAlone)
        // (682x: the main page's row - all its [slots] keys - showed them all whole, unscrolled: its pages may keep Play's slot)
        if (slots > 0 && n == slots) mainFit[0] = !g.scrolls && g.visible >= n
        val h = runCatching { box.maxIntrinsicHeight(g.keyW) }.getOrElse { if (c.hasBoundedHeight) c.maxHeight.coerceAtMost(with(density) { 48.dp.roundToPx() }) else with(density) { 48.dp.roundToPx() } }
            .coerceAtMost(if (c.hasBoundedHeight) c.maxHeight else Int.MAX_VALUE)
        keyW[0] = g.keyW; keyW[1] = g.firstW
        val p = box.measure(androidx.compose.ui.unit.Constraints.fixed(g.viewW.coerceAtLeast(0), h))
        if (g != geom) geom = g
        layout(width, h) { p.place(g.viewStart, 0) }
    }
}

/**
 * 673g (owner brief 15 Nov - vertical menus): [keys] one under another, every key the column's whole width and the tallest
 * key's height, [gap] apart - edges in line, nothing resizing as the D-pad moves. Each key is drawn by [key] at that size.
 */
@Composable
internal fun UniformKeyColumn(keys: List<WinAction>, gap: Dp, fill: Boolean = false,
    /** (737a, PlayPlace CAMERA: Options has Play alone, Details / Achievements Play + their own key - a column under a weighted
     *  page put Play a key lower on Options.) The column keeps the room of at least [slots] keys, the keys from its top: Play
     *  stays put on every page. */
    slots: Int = 0, key: @Composable (WinAction) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = with(density) { gap.roundToPx() }
    androidx.compose.ui.layout.Layout(content = {
        keys.forEach { a ->
            Box(propagateMinConstraints = true) { androidx.compose.runtime.CompositionLocalProvider(LocalUniformKey provides true) { key(a) } }
        }
    }) { ms, c ->
        // (the widest key's width - or, [fill], the column's whole width - for every key)
        val need = runCatching { ms.maxOfOrNull { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) } ?: 0 }.getOrElse { if (c.hasBoundedWidth) c.maxWidth else 0 }
        val w = if (fill && c.hasBoundedWidth) c.maxWidth else need.coerceIn(c.minWidth, if (c.hasBoundedWidth) c.maxWidth else Int.MAX_VALUE)
        val h = runCatching { ms.maxOfOrNull { it.maxIntrinsicHeight(w) } ?: 0 }.getOrElse { with(density) { 44.dp.roundToPx() } }
        val ps = ms.map { it.measure(androidx.compose.ui.unit.Constraints.fixed(w, h)) }
        val rows = maxOf(ps.size, if (ps.isEmpty()) 0 else slots)
        val total = if (rows == 0) 0 else rows * h + (rows - 1) * gapPx
        layout(w, total) { ps.forEachIndexed { i, p -> p.place(0, i * (h + gapPx)) } }
    }
}

/**
 * 673g: the keys' own layout inside the row's view - every key at the same size, a pitch apart, shifted by the scroll
 * ([offset], read while placing only). Its intrinsic width is the widest key's, its intrinsic height the tallest key's at
 * the width asked (what UniformKeyRow sizes the group by).
 */
private fun uniformKeysPolicy(gap: Int, keyW: IntArray, refFirst: () -> Boolean = { false }, offset: () -> Float) = object : androidx.compose.ui.layout.MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>, constraints: androidx.compose.ui.unit.Constraints
    ): androidx.compose.ui.layout.MeasureResult {
        val viewW = constraints.maxWidth
        // (the key width UniformKeyRow worked out in the same pass - the view holds whole keys exactly)
        val w = keyW[0].takeIf { it > 0 } ?: (measurables.maxOfOrNull { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) } ?: 0)
        val first = keyW.getOrElse(1) { 0 }.takeIf { it > 0 } ?: w
        val h = constraints.maxHeight
        // (682ab: a page row's measuring reference comes first - it is measured with the keys and placed past the view's end)
        val ref = if (refFirst()) 1 else 0
        val ps = measurables.mapIndexed { i, m -> m.measure(androidx.compose.ui.unit.Constraints.fixed(if (i == ref) first else w, h)) }
        return layout(viewW, h) {
            val ox = offset().toInt()
            ps.forEachIndexed { i, p ->
                if (i < ref) p.place(viewW + w + gap, 0)
                else { val k = i - ref; p.place(if (k == 0) -ox else first + gap + (k - 1) * (w + gap) - ox, 0) }
            }
        }
    }
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int) =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0
    // (674c: the width at which every key's word fits on two lines - UniformKeyRow's narrow fallback. A key that doesn't grow
    // a line when narrowed (its word is single-line, or its drawing isn't text) keeps the group at the one-line width: no
    // word is ever cut to make room.)
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int): Int {
        val maxW = measurables.map { it.maxIntrinsicWidth(height) }
        val one = maxW.maxOrNull() ?: 0
        val need2 = measurables.mapIndexed { i, m -> twoLineKeyWidth(m.minIntrinsicWidth(height), maxW[i]) }.maxOrNull() ?: 0
        if (need2 >= one) return one
        val ok = measurables.withIndex().all { (i, m) -> maxW[i] <= need2 || m.maxIntrinsicHeight(need2) > m.maxIntrinsicHeight(maxW[i]) }
        return if (ok) need2 else one
    }
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int) =
        measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int) =
        measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
}

/** 673g: ‹ › in the gutters of a scrolling key row - a small dark disc with a light chevron, readable on any window; dim where the row can't go. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKeyRowChevrons(g: UniformRowGeom, offset: Float) {
    val r = 8.dp.toPx(); val cy = size.height / 2f
    listOf(true, false).forEach { left ->
        val more = if (left) offset > 0.5f else offset < g.maxOffset - 0.5f
        val cx = if (left) g.viewStart / 2f else g.viewStart + g.viewW + (size.width - g.viewStart - g.viewW) / 2f
        val a = if (more) 0.92f else 0.28f
        drawCircle(Color.Black.copy(alpha = 0.45f * a), r, Offset(cx, cy))
        val d = r * 0.42f; val tip = if (left) -d * 0.55f else d * 0.55f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - tip, cy - d); lineTo(cx + tip, cy); lineTo(cx - tip, cy + d)
        }
        drawPath(path, Color.White.copy(alpha = a), style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** A key drawn as [shape] in [fill] (lit: [onFill]) with an outline; its word in full, never wrapped mid-word. */
@Composable
internal fun SceneKey(
    a: WinAction, fill: Color, onFill: Color, ink: Color, onInk: Color, edge: Color, onEdge: Color, shape: Shape,
    font: FontFamily? = null, size: TextUnit = 12.sp, pad: Dp = 10.dp, height: Dp = 30.dp, upper: Boolean = false,
    /** The caller already draws the primary key (Play) its own way; else Play gets the lit colour as a tint (update 487). */
    primaryStyled: Boolean = false,
    /** 529l: the word's line height (a tight window at large text: the theme's 24 sp line made each key 54 px tall on the
     *  KONKR at x1.3); unspecified = the theme's, as before. */
    lineHeight: TextUnit = TextUnit.Unspecified,
    /** 529m (owner, #15 6865249152): a full-width bar - the window's width, its icon in a fixed column, the word after it,
     *  growing only when the whole word needs a second line. */
    wide: Boolean = false,
    /** 672a: at least this wide, its icon and word centred (Record crate's Play as wide as Sleeve notes); unspecified = its own. */
    minWidth: Dp = Dp.Unspecified
) {
    // Update 487 (E7 popup hierarchy, owner-approved): Play is the clear first action in every window. A window that drew
    // every key alike gets Play tinted with its lit colour (and a firm edge) while another key is lit; a lit key's word is
    // always readable on it (≥ 4.5 : 1: Tiles wrote white on lilac at 2.9 : 1), and a lit Play keeps a ring that shows.
    val k = sceneKeyColors(a.primary, primaryStyled, fill, onFill, ink, onInk, edge, onEdge)
    val lab = PopupLab.active.actions // (523t: approved - everyone)
    // (673g, owner brief 15 Nov: a status line is not a key - under the key row, in the window's ink, still a D-pad stop.)
    if (LocalKeyAsStatus.current) { SceneStatusLine(a, k, font, size); return }
    // (673g, owner brief 15 Nov: "One MORE button, never MORE plus three dots" - batch 523's round ••• key is gone: More /
    // Less is a key like the others, its word and its icon, the last of the row, open (Less) with a lit edge.)
    val less = PopupLab.isMore(a) && a.label == "Less"
    val uniform = LocalUniformKey.current
    // (Update 487, E7 tiny controls: no taller layout box — a 48 dp box grew the key rows and cut the keys of the tight Cover
    // details window on the KONKR (popclip k487). Compose already widens a small clickable's touch area to 48 dp
    // (ViewConfiguration.minimumTouchTargetSize) where nothing else is under the finger.)
    SceneControl(a, if (wide) Modifier.fillMaxWidth() else Modifier) { on ->
        Row(
            // (673g: in a uniform group the key fills the size the group gives every key, its icon and word centred.)
            Modifier.then(if (uniform) Modifier.fillMaxSize() else if (wide) Modifier.fillMaxWidth() else Modifier)
                .then(if (minWidth.isSpecified && !wide && !uniform) Modifier.widthIn(min = minWidth) else Modifier)
                .heightIn(min = height).clip(shape).background(if (on) k.onFill else if (lab && PopupLab.isDetails(a)) Color.Transparent else k.fill)
                .border(if (on || less) 2.dp else if (k.firmEdge) 1.5.dp else 1.dp, if (on) k.onEdge else if (less) onFill.copy(alpha = 1f) else k.edge, shape).padding(horizontal = pad),
            horizontalArrangement = if ((minWidth.isSpecified || uniform) && !wide) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (wide) Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                Icon(a.icon, null, tint = if (on) k.onInk else k.ink, modifier = Modifier.size(if (a.primary) 16.dp else 14.dp))
            } else Icon(a.icon, null, tint = if (on) k.onInk else k.ink, modifier = Modifier.size(if (a.primary) 16.dp else 13.dp))
            Spacer(Modifier.width(if (wide) 10.dp else 5.dp))
            Text(if (upper) sceneLabel(a.label).uppercase() else sceneLabel(a.label), color = if (on) k.onInk else if (a.urgent) Color(0xFFFF8A3D) else k.ink,
                fontSize = if (a.primary) size * 1.15f else size, fontWeight = FontWeight.Bold, fontFamily = font, maxLines = 2,
                modifier = if (wide) Modifier.weight(1f) else Modifier,
                lineHeight = if (lineHeight == TextUnit.Unspecified) TextUnit.Unspecified else if (a.primary) lineHeight * 1.15f else lineHeight)
        }
    }
}

/**
 * 673g (owner brief 15 Nov - status messages in their own place, not among the keys): a status line under a key row - its icon
 * and words in the window's key ink (a problem in orange), the whole width, wrapping in words; the D-pad stops on it (a thin ring)
 * and A opens what it is about, as its chip did.
 */
@Composable
private fun SceneStatusLine(a: WinAction, k: SceneKeyColors, font: FontFamily?, size: TextUnit) {
    SceneControl(a, Modifier.fillMaxWidth()) { on ->
        val c = if (a.urgent) Color(0xFFFF8A3D) else k.ink.copy(alpha = k.ink.alpha * 0.82f)
        // (674a: tappable - 24 dp at least, as WinStatusChip.)
        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).clip(RoundedCornerShape(4.dp))
            .border(if (on) 1.5.dp else 0.dp, if (on) k.onEdge else Color.Transparent, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(a.icon, null, tint = c, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(sceneLabel(a.label), color = c, fontSize = size, fontFamily = font, fontWeight = FontWeight.SemiBold, maxLines = 3)
        }
    }
}

/** [SceneKey] for the windows that already draw Play their own way (a filled Play key). */
@Composable
internal fun SceneKeyStyled(
    a: WinAction, fill: Color, onFill: Color, ink: Color, onInk: Color, edge: Color, onEdge: Color, shape: Shape,
    font: FontFamily? = null, size: TextUnit = 12.sp, pad: Dp = 10.dp, height: Dp = 30.dp, upper: Boolean = false
) = SceneKey(a, fill, onFill, ink, onInk, edge, onEdge, shape, font, size, pad, height, upper, primaryStyled = true)

internal class SceneKeyColors(val fill: Color, val onFill: Color, val ink: Color, val onInk: Color, val edge: Color, val onEdge: Color, val firmEdge: Boolean)

/** Update 487: the colours a scene key is drawn in (pure; SceneKey487Test). */
internal fun sceneKeyColors(
    primary: Boolean, primaryStyled: Boolean, fill: Color, onFill: Color, ink: Color, onInk: Color, edge: Color, onEdge: Color
): SceneKeyColors {
    // A lit word must read on the lit colour (only judged where that colour is solid; a see-through one depends on the scene).
    val onInkR = if (onFill.alpha >= 0.9f) com.shelf.launcher.ui.theme.textOn(onFill, onInk) else onInk
    val inkR = if (fill.alpha >= 0.9f) com.shelf.launcher.ui.theme.textOn(fill, ink) else ink
    // The ring of a lit key must show against the key itself (Twin: the accent ring on the accent key vanished).
    val ringR = if (onFill.alpha >= 0.9f && onEdge.alpha >= 0.5f && com.shelf.launcher.ui.theme.contrastRatio(onEdge, onFill) < 1.4f) onInkR else onEdge
    if (!primary || primaryStyled) return SceneKeyColors(fill, onFill, inkR, onInkR, edge, ringR, false)
    // Play in a window that draws all keys alike: tinted with the lit colour, its edge in that colour.
    val tintFill = if (fill.alpha >= 0.9f) androidx.compose.ui.graphics.lerp(fill, onFill.copy(alpha = 1f), 0.28f)
        else onFill.copy(alpha = (fill.alpha + 0.24f).coerceAtMost(0.5f))
    val tintInk = if (tintFill.alpha >= 0.9f) com.shelf.launcher.ui.theme.textOn(tintFill, ink) else ink
    return SceneKeyColors(tintFill, onFill, tintInk, onInkR, onFill.copy(alpha = 1f), ringR, true)
}

/**
 * Update 422: a key's word without a symbol the layout's look puts in front of it ("▶ continue" in Hero / Disc looks — the
 * key already draws its icon, so it read "▶ ▶ continue"), first letter capitalised.
 */
internal fun sceneLabel(l: String): String = l.trimStart { !it.isLetterOrDigit() }.ifEmpty { l }.replaceFirstChar { it.uppercase() }

/**
 * 672a: the width a plain [SceneKey] (not wide, not the round More) draws for [a] - its word on one line at [size] (Play's 15 %
 * larger, as SceneKey draws it), its icon, the gap and [pad] each side.
 */
@Composable
internal fun sceneKeyWidth(a: WinAction, size: TextUnit, font: FontFamily?, upper: Boolean, pad: Dp = 10.dp): Dp {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.material3.LocalTextStyle.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val word = if (upper) sceneLabel(a.label).uppercase() else sceneLabel(a.label)
    val sz = if (a.primary) size * 1.15f else size
    return remember(word, sz, font, style, density, pad) {
        val w = measurer.measure(word, style.merge(androidx.compose.ui.text.TextStyle(fontSize = sz, fontWeight = FontWeight.Bold, fontFamily = font)),
            maxLines = 1, softWrap = false).size.width
        with(density) { w.toDp() } + (if (a.primary) 16.dp else 13.dp) + 5.dp + pad * 2
    }
}

/** The first page's facts, label over value in two columns, or the other page's body. */
@Composable
internal fun ColumnScope.SceneFactsBody(ctx: WinSceneCtx, label: Color, value: Color, font: FontFamily? = null, upperLabels: Boolean = true,
    omit: Set<String> = emptySet(), inline: Boolean = false,
    /** Update 508: a larger type for a window that has room to spare on a small screen (Tile home on the GT78); 1 = as before. */
    scale: Float = 1f,
    /** 673e (D328): the story cue's own line height (the theme's is 24 sp, a tall box round 9 sp words); Unspecified = as before. */
    cueLineHeight: TextUnit = TextUnit.Unspecified) {
    // (529m: the copy of a page change draws its own page - LocalShownPage.)
    if ((LocalShownPage.current ?: ctx.page) == WinPage.MAIN) {
        // (Update 424: a fact cut at the window's edge fades out — it scrolls — instead of ending in half a line.)
        val sc = rememberScrollState()
        // (Update 467, popup gate: with large text the keys can leave the facts a sliver that shows the tops of one row
        // — "CONSOLE YEAR" cut in half on the KONKR at 1.3×. Under one whole row's room the box gives its space away
        // instead; the facts stay on the Details page.)
        val minRow = with(androidx.compose.ui.platform.LocalDensity.current) { (9.sp.toDp() * scale + 14.sp.toDp() * scale + 8.dp).roundToPx() }
        // (Update 471, owner review of the 470 PDF: with More open the facts box showed "MADE BY" / "GENRE" and their values
        // were scrolled away under the keys — the actions fitted, the information didn't. [omit] leaves out facts the window
        // already shows (Cover details' header line has the console and year); [inline] puts label and value on one line.
        // A window that doesn't ask for it gets it too when its two-column facts don't fit the room they have: measured once,
        // switched for the next frame, again when the window's keys change (More / Less).)
        var viewH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var factsH by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var tight by androidx.compose.runtime.remember(ctx.g.stableKey, ctx.tools.size, ctx.chips.size) { androidx.compose.runtime.mutableStateOf(false) }
        val asLines = inline || tight
        androidx.compose.runtime.LaunchedEffect(viewH, factsH, asLines) { if (!asLines && viewH > 0 && factsH > viewH) tight = true }
        // (Update 476, D260: a story cut by the room faded out mid-sentence with nothing saying there was more. While the box
        // can scroll further, a small cue at its bottom edge says where the whole story is.)
        // (529m, owner #15: the cue lay over the facts it cut - "Full story on Details ›" across "Action" and "PLAYED" on the
        // KONKR at x1.3. It has its own line under the box now; the box gives it the room.)
        // (673e, D328, Flip 2 x1.3 gate f673g: with More open in Disc station the cue was laid out 0 px high - there, not shown.
        // Where the room left for the facts is shorter than the cue's line, the cue is left out (the facts have given way
        // there too); judged at every layout pass, so it is back as soon as there is room (Less).)
        val cueH = with(androidx.compose.ui.platform.LocalDensity.current) {
            (reservedLinesHeight(9.sp, cueLineHeight, FontWeight.Bold, font) + 2.dp).roundToPx() }
        var cueFits by remember { mutableStateOf(true) }
        // (673e: the window's whole width, so the cue stands at its end whether the facts show or have given way - it stood at
        // the start with More open and at the end with Less, Flip 2 x1.3 f673h.)
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().layout { m, c ->
            cueFits = !c.hasBoundedHeight || c.maxHeight >= cueH - 1
            val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) }
        }) {
        Column(Modifier.weight(1f, fill = false).layout { m, c ->
            if (c.hasBoundedHeight && c.maxHeight < minRow) layout(0, 0) {} else { val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) } }
        }.onSizeChanged { viewH = it.height }.sceneFadeEdges(sc).verticalScroll(sc)) {
            val facts = shownFacts(ctx, omit)
            Column(Modifier.onSizeChanged { factsH = it.height }) {
            // (675r, RP Nova x1.3 n675d: Pocket ribbon's pixel font made "CONSOLE" / "TROPHIES" wider than the fixed 78 dp label
            // column - cut to "CONSOL" / "TROPHI" (rule 11). The column is as wide as its widest label (78 dp at least, as
            // before); past 45 % of the row a label wraps instead of being cut.)
            val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 9.sp * scale, lineHeight = 11.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = font)
            val labelMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            val dens = androidx.compose.ui.platform.LocalDensity.current
            val widestLabel = remember(facts, labelStyle, dens) {
                facts.maxOfOrNull { (l, _) -> labelMeasurer.measure(if (upperLabels) l.uppercase() else l, labelStyle, softWrap = false, maxLines = 1).size.width } ?: 0
            }
            if (asLines) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
              val labelW = factLabelWidth(with(dens) { widestLabel.toDp() }, maxWidth)
              Column { facts.forEach { (l, v) ->
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.Top) {
                    // (Its own line height — the theme's tall one made every row half as tall again as its value: 470's KONKR
                    // run showed three facts of four.)
                    Text(if (upperLabels) l.uppercase() else l, color = label, style = labelStyle,
                        softWrap = true, modifier = Modifier.width(labelW).padding(top = 3.dp, end = 4.dp))
                    // (682t, H16 survey x1.3: Pocket ribbon's pixel font broke "PlayStation 2" inside the word - the value's
                    // words stay whole, stepping down to the readable minimum only when the longest one does not fit.)
                    WholeWordsText(v, color = value, fontSize = 12.sp * scale, lineHeight = 14.sp * scale, fontWeight = FontWeight.SemiBold, fontFamily = font, modifier = Modifier.weight(1f))
                }
              } }
            } else facts.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    pair.forEach { (l, v) ->
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(if (upperLabels) l.uppercase() else l, color = label, fontSize = 9.sp * scale, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = font, softWrap = false)
                            WholeWordsText(v, color = value, fontSize = 12.sp * scale, lineHeight = 14.sp * scale, fontWeight = FontWeight.SemiBold, fontFamily = font)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            }
            ctx.summary?.let { Text(it, color = value.copy(alpha = 0.78f), fontSize = 11.sp * scale, lineHeight = 14.sp * scale, fontFamily = font, modifier = Modifier.padding(top = 2.dp)) }
        }
        if (ctx.summary != null && viewH > 0 && sc.canScrollForward && cueFits) FullText(STORY_CUE, color = label, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, fontFamily = font, lineHeight = cueLineHeight,
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp).background(sceneCueBack(value)).padding(horizontal = 4.dp))
        }
    } else ctx.pageBody(Modifier.weight(1f).fillMaxWidth())
}

/** 675r (pure, Batch675rTest): the inline facts' label column - the widest label plus its 4 dp gap, 78 dp at least (the old fixed
 *  width), 45 % of the row at most (a label wider than that wraps, never cut). */
internal fun factLabelWidth(widest: androidx.compose.ui.unit.Dp, row: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    (widest + 4.dp + 1.dp).coerceAtLeast(78.dp).coerceAtMost(maxOf(78.dp, row * 0.45f))

/** Update 476: the cue's backing — the window's ground behind light ink (dark windows), a pale one behind dark ink. */
internal fun sceneCueBack(ink: Color): Color =
    if (ink.red * 0.3f + ink.green * 0.59f + ink.blue * 0.11f > 0.5f) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.7f)

internal fun sceneEase(k: Float): Float { val c = k.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) * (1f - c) }

// ───────────────────────────── Record crate: the sleeve's back, the record slides out of it ─────────────────────────────

@Composable
internal fun RecordSleeve(ctx: WinSceneCtx) {
    val paper = Color(0xFFEDE4D3); val ink = Color(0xFF2B231B)
    // (Update 505: the sleeve's print — SIDE A, the track numbers, Play and the key you're on — in the Crate home's accent (the
    // theme's, the game's or this layout's own colours, as its turntable and rings), made readable on the paper; was a fixed red.)
    val red = sceneLit(com.shelf.launcher.data.HomeLayout.CRATE, com.shelf.launcher.ui.theme.ShelfTheme.accent, paper)
    val side = minOf(ctx.maxW, ctx.maxH, 460.dp)
    // (529t, owner P3: with a pickup the sleeve itself comes out of the crate as it stands there (PickupOverlay, the home's own
    // picture), comes forward and turns over: this, its back, turns in from edge-on once it is here; closing, the other way.)
    val flip = LocalSceneOrigin.current != null
    // (P2 / 681a: L1 / R1 turn the sleeve's pages wherever the D-pad is - NativePageKit.nativeBumpers.)
    Box(Modifier.size(side).nativeBumpers(ctx).pickupFlipTarget(flip).graphicsLayer {
        if (flip) {
            val k = ctx.reveal()
            // (Edge-on until the front has turned away - drawn, not hidden: its first drawing (art, text) is done before it
            // shows, so the turn doesn't stall there - Flip 2 pkf529u: ~90 ms at the turn.)
            rotationY = -90f * flipBackTurn(k)
            cameraDistance = 14f * density
            flatTextFor3d()
            shadowElevation = 18.dp.toPx() * (1f - flipBackTurn(k))
            return@graphicsLayer
        }
        // The sleeve slides out of the crate sideways, the way a record is pulled from its neighbours.
        val e = sceneEase(ctx.reveal())
        translationX = (1f - e) * -size.width * 0.15f
        rotationZ = (1f - e) * -4f
        alpha = (ctx.reveal() * 2.2f).coerceIn(0f, 1f)
        shadowElevation = 18.dp.toPx() * e
    }) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(paper)
            // Ring wear from the record inside, and the worn corner.
            drawCircle(ink.copy(alpha = 0.06f), size.minDimension * 0.44f, center, style = Stroke(size.minDimension * 0.05f))
            drawRect(Brush.linearGradient(listOf(Color.Transparent, ink.copy(alpha = 0.10f)), Offset(size.width * 0.8f, size.height * 0.8f), Offset(size.width, size.height)))
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // (681s, emulator review 44194071430: at x1.3 the sleeve's full header left the insert one or two tracks - on the
            // sleeve's own pages the header steps back to a small sleeve, so the printed matter gets the room; Info keeps it.)
            val head = crateHeader(ctx.page != WinPage.MAIN)
            val anim = rememberAnimationsEnabled()
            val coverSide by androidx.compose.animation.core.animateDpAsState(side * head.cover,
                if (anim) androidx.compose.animation.core.tween(NATIVE_PAGE_MS) else androidx.compose.animation.core.snap(), label = "crateCover")
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(coverSide).background(ink.copy(alpha = 0.08f)).border(1.dp, ink.copy(alpha = 0.3f))) {
                    ctx.cover?.let { SmartArt(it, null, Modifier.fillMaxSize(), maxCrop = SmartFill.maxCropFor(it, ctx.cover)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // (P2 / 681a: the sleeve's other printed matter names its page here - LINER NOTES, TRACKLIST, PRESSING NOTES.)
                    Text(crateHeaderName(ctx.page, ctx.earned, ctx.total), color = red, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    // (672c, KONKR x1.3 k673b: the name kept three lines' height for a two-line name - the sleeve's keys then had no
                    // room for More's rows. A window's own name takes the lines it needs.)
                    StableFullText(ctx.name, color = ink, fontSize = head.name.sp, maxLines = head.lines, fontWeight = FontWeight.Black, lineHeight = head.line.sp, reserve = false)
                    if (head.console) Text(ctx.console + (ctx.year?.let { " · $it" } ?: ""), color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Serif)
                }
            }
            Box(Modifier.padding(vertical = head.rule.dp).fillMaxWidth().height(2.dp).background(ink))
            // (674f: the room under the rule, measured - the vertical menu shows whole keys only, as many as leave the track list
            // two rows (k674e KONKR x1.3: three keys took all of it; f674e Flip 2: the third key cut at the stack's foot).)
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val regionH = maxHeight
            Column(Modifier.fillMaxSize()) {
            // (P2 / 681a: Info and the sleeve's own pages share the room under the rule - NativePages carries one to the next, the
            // paper inner sleeve sliding out sideways (WinPagesCrate.kt); the keys stay below.)
            NativePages(ctx, Modifier.weight(1f, fill = ctx.page != WinPage.MAIN).fillMaxWidth(), label = "cratePage", move = ::crateMove) { pg, pageLive ->
            if (pg == WinPage.MAIN) {
                // The facts as the track list.
                // (672a, Flip 2 x1.3 popup gate f672e: with More open the keys left the list 2 px - the top of one row, "1. Console",
                // and no whole one. Under one whole row's room the list gives its room away (as SceneFactsBody since 467): the
                // facts come back when More closes and stay on Sleeve notes. And the console and year it began with are already
                // printed under the name: the list starts with what the sleeve doesn't say yet.)
                val facts = shownFacts(ctx, setOf("Console", "Year"))
                var rowH by remember { mutableStateOf(0) }
                val lineH = androidx.compose.material3.LocalTextStyle.current.lineHeight
                val guess = with(androidx.compose.ui.platform.LocalDensity.current) {
                    ((if (lineH.isSp) lineH else 16.sp).toPx().coerceAtLeast(14.sp.toPx()) + 4.dp.toPx()).toInt()
                }
                // (675r, RP Nova x1.3 n675d: with More open the list had less than one whole row and drew none, but kept its
                // weight - an empty band between the rule and PLAY. fill = false: a list that shows no row takes no room; the keys
                // stand under the rule and the sleeve's own paper is left below them.)
                Column(Modifier.layout { m, c ->
                    val minRow = if (rowH > 0) rowH else guess
                    if (c.hasBoundedHeight && c.maxHeight < minRow) layout(c.minWidth, c.minHeight) {}
                    else {
                        // (674g, f674f / k674f: the list showed one row and part of the next. Its view now ends on a whole row; the
                        // rest scroll.)
                        val hh = if (c.hasBoundedHeight) (c.maxHeight / minRow) * minRow else c.maxHeight
                        val pl = m.measure(c.copy(minHeight = minOf(c.minHeight, hh), maxHeight = hh)); layout(pl.width, pl.height) { pl.place(0, 0) }
                    }
                }.verticalScroll(rememberScrollState())) {
                    facts.forEachIndexed { i, (l, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).then(if (i == 0) Modifier.onSizeChanged { rowH = it.height } else Modifier)) {
                            Text("${i + 1}.", color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                            Text(l, color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Serif, modifier = Modifier.width(78.dp))
                            Text(v, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else CratePage(ctx, pg, pageLive, paper, ink, red, Modifier.fillMaxSize())
            }
            // (682v: 4 dp on the sleeve's own pages - with the keys kept vertical there, the tracklist's second track was 2 px short)
            Spacer(Modifier.height(if (ctx.page == WinPage.MAIN) 8.dp else 4.dp))
            // (672a, f672e hierarchy: PLAY 218 px wide beside SLEEVE NOTES 378 px - the sleeve's long word made Play the smaller
            // key. Play is as wide as Sleeve notes now, its word centred - as far as the two still share their row: KONKR x1.3
            // (k673a) has no room for two keys that wide, and a Play alone on its row pushed the ••• off the sleeve.)
            val keys = sceneKeys(ctx)
            val playMin = keys.firstOrNull { PopupLab.isDetails(it) }?.let { sceneKeyWidth(it, 11.sp, null, upper = true) }
                ?.let { dw -> crateKeyMin(dw, side - 33.dp, 6.dp) } ?: Dp.Unspecified
            // (672c, KONKR x1.3 k673b: with More open the sleeve had room for two rows of keys - Save sync, Inspect, the save line
            // and ••• were not drawn at all (FlowRow drops what doesn't fit), so More could not be closed by touch. The keys are
            // measured before the track list (which gives way) and, on a sleeve too small even for them, scroll - every key is
            // there, and the D-pad brings the one it is on into view.)
            val keyScroll = rememberScrollState()
            // (674e, owner decision 15 Nov - Record crate at x1.3, option A: where two of the sleeve's keys can't stand side by
            // side (the row showed one at a time), its actions are a deliberate vertical menu, WWE SmackDown vs. Raw 2011 style:
            // identical keys the sleeve's width, edges in line, even gaps, Play first and MORE last; three at a time, the D-pad
            // scrolling the stack smoothly; the track list keeps its room above. Only here - no other row turns vertical.)
            val actions = keys.filterNot { it.status }
            val widest = actions.maxOfOrNull { sceneKeyWidth(it, 11.sp, null, upper = true) } ?: 0.dp
            // (682v, owner 18 Nov: Play the same size in the same place on every page - the sleeve's own pages keep the main
            // page's arrangement: a vertical menu there (x1.3) stays a vertical menu on the liner notes, the tracklist and the
            // pressing notes, instead of a row whose Play changed size page by page.)
            val vertNow = crateKeysVertical(widest, side - 32.dp, 6.dp, KEY_ROW_GUTTER)
            val mainVert = remember { mutableStateOf<Boolean?>(null) }
            if (ctx.page == WinPage.MAIN && keys.none { it.label == "Less" } && mainVert.value != vertNow) mainVert.value = vertNow
            if (if (ctx.page == WinPage.MAIN) vertNow else mainVert.value ?: vertNow) {
                val keyH = 34.dp
                // (675r, n675d: the status line under the keys ("Save sync is not set up", ~36 dp at x1.3) was not counted, so three
                // keys left the list less than one row. Its height is measured and reserved too.)
                val status = keys.filter { it.status }
                var statusH by remember { mutableStateOf(0.dp) }
                val statusRoom = if (status.isEmpty()) 0.dp else if (statusH > 0.dp) statusH + 6.dp else CRATE_STATUS_GUESS * status.size
                // (682v: on the sleeve's own pages, kept vertical as on the main page, one key in view at a time (the stack scrolls) - the tracklist
                // keeps two whole tracks at x1.3 (CrateInsertRoom681s); Play stays the first, the same width.)
                val shown = crateVisibleKeys(regionH, keyH, 6.dp, (if (ctx.page == WinPage.MAIN) 46.dp else 0.dp) + statusRoom, actions.size)
                    .let { if (ctx.page == WinPage.MAIN) it else it.coerceAtMost(1) }
                // (674g, owner review prep f674f / k674f: the fade at the stack's foot hid the last key's lower edge - it read as cut.
                // The view holds whole keys exactly, so no fade; a hair of room for the keys' borders.)
                // (682ab, PlayPlace x1.3: on Options - Play alone - the stack's box was 2 dp shorter than on the pages with two keys
                // and Play sat 2 px lower. A page's stack keeps its whole view height.)
                val viewH = keyH * shown + 6.dp * (shown - 1).coerceAtLeast(0) + 2.dp
                Box(Modifier.fillMaxWidth().then(if (ctx.page == WinPage.MAIN) Modifier.heightIn(max = viewH) else Modifier.height(viewH)).verticalScroll(keyScroll)) {
                    UniformKeyColumn(actions, 6.dp, fill = true) { a ->
                        SceneKey(a, if (a.primary) red else paper, ink, if (a.primary) com.shelf.launcher.ui.theme.textOn(red, Color.White) else ink, paper, ink.copy(alpha = 0.5f), red,
                            RoundedCornerShape(3.dp), upper = true, size = 11.sp, height = keyH, lineHeight = 14.sp)
                    }
                }
                if (status.isNotEmpty()) androidx.compose.runtime.CompositionLocalProvider(LocalKeyAsStatus provides true) {
                    val dens = androidx.compose.ui.platform.LocalDensity.current
                    Column(Modifier.onSizeChanged { with(dens) { statusH = it.height.toDp() } }) {
                        status.forEach { a -> SceneKey(a, paper, ink, ink, paper, ink.copy(alpha = 0.5f), red, RoundedCornerShape(3.dp), upper = true, size = 11.sp) }
                    }
                }
            } else Box(Modifier.sceneFadeEdges(keyScroll).verticalScroll(keyScroll)) {
                KeyRow(keys, slots = rememberKeySlots(ctx.page, keys)) { a ->
                    SceneKey(a, if (a.primary) red else paper, ink, if (a.primary) com.shelf.launcher.ui.theme.textOn(red, Color.White) else ink, paper, ink.copy(alpha = 0.5f), red, RoundedCornerShape(3.dp), upper = true, size = 11.sp,
                        minWidth = if (a.primary) playMin else Dp.Unspecified)
                }
            }
            }
            }
        }
    }
}

/** 672a (pure, CompactX13Fixes672Test): Play's least width beside a Details key [details] wide in a row [row] wide with [gap]
 *  between them - Details' width, or what the row leaves beside it when two keys that wide don't fit (never below 0). */
/** 674e (pure): Record crate's keys stand as a vertical menu where two keys [widest] wide can't share the sleeve's row [row]
 *  ([gap] between them, the row's ‹ › [gutter]s each side) - the row would show one key at a time. */
internal fun crateKeysVertical(widest: Dp, row: Dp, gap: Dp, gutter: Dp): Boolean = widest * 2 + gap > row - gutter * 2

/** 674f (pure): whole keys [keyH] tall, [gap] apart, the vertical menu shows in [region] (the room under the sleeve's rule, an
 *  8 dp gap above the keys) leaving the track list [facts] - two at least (of [n]), three at most; the rest scroll. */
internal fun crateVisibleKeys(region: Dp, keyH: Dp, gap: Dp, facts: Dp, n: Int): Int {
    if (n <= 0) return 0
    val fit = ((region - 8.dp - facts + gap) / (keyH + gap)).toInt()
    return fit.coerceIn(minOf(2, n), minOf(3, n))
}

/** 675r: room kept for one status line under the vertical menu until its real height is measured (24 dp touch size + a 12 sp line). */
internal val CRATE_STATUS_GUESS = 30.dp

internal fun crateKeyMin(details: Dp, row: Dp, gap: Dp): Dp = minOf(details, row - gap - details).coerceAtLeast(0.dp)

// ───────────────────────────── Card binder: the card flips to its back ─────────────────────────────

// (Update 507: CardBack replaced — WinSceneFamilies.kt.)

// ───────────────────────────── Disc station: a disc player's front panel ─────────────────────────────

/**
 * Update 506 (owner review of the 505 visual report, P0): the deck's panel opens beside the player instead of a dark slab over
 * it — the player and its turning disc stay in view on the left (the dim is light there, sceneVeil), the panel slides out of
 * the deck's right edge where the home's info panel stands, joined to it by the tray, and goes back in the same way on B.
 * Its words are the deck's: the disc kind the home shows (never "CARTRIDGE" on a disc player), DISC 1 · TRACK 01.
 * [discStationDeckRightDp] mirrors the home's own sizing (StyleDiscStation.DiscStation).
 */
@Composable
internal fun DiscPlayerPanel(ctx: WinSceneCtx) {
    // (Update 504, popup identity: the deck in Disc Station's own look — the home's smoked glass panels and the accent its
    // Play pill and focus rings use (the owner's layout / theme accent) — instead of a silver deck with a fixed teal display.)
    val accent = com.shelf.launcher.ui.theme.ShelfTheme.accent
    val metal = Brush.verticalGradient(listOf(Color(0xFF1E2535), Color(0xFF141A2A), Color(0xFF0C111C)))
    val lcdBg = Color(0xFF070B12)
    val lcd = androidx.compose.runtime.remember(accent) { com.shelf.launcher.ui.theme.legibleArtAccent(accent, lcdBg, lcdBg) }
    val labels = discPanelLabels(ctx.g.platform)
    // (Beside an inspected piece the room is the window's part only: the panel fills it, as before.)
    val geo = if (ctx.inspecting) 0f to minOf(ctx.maxW.value, 640f) else discPanelGeometry(ctx.maxW.value, ctx.maxH.value)
    val left = geo.first.dp; val w = geo.second.dp
    val gap = 12.dp
    val rim = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.10f), accent, 0.35f)
    // (673f, native pages - owner directive 14 Nov: "replace the remaining generic popup pages with layout-aware designs". The
    // deck's pages are its tracks: Info is track 01, Details 02, Achievements 03, Options 04 - each plays on the LCD, the panel,
    // its tray and keys stay. L1 / R1 skip a track back / on, as the deck's own skip keys would.)
    val tracks = DISC_TRACKS.filter { it in ctx.pages }
    val anim = rememberAnimationsEnabled()
    Box(Modifier.width(ctx.maxW).height(ctx.maxH).onPreviewKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val step = when (e.key) { androidx.compose.ui.input.key.Key.ButtonL1 -> -1; androidx.compose.ui.input.key.Key.ButtonR1 -> 1; else -> return@onPreviewKeyEvent false }
        val to = pageStep(tracks, ctx.page, step)
        if (to != ctx.page) ctx.open(to)
        true
    }) {
        // The tray: the panel's link to the deck, out of the deck's edge first and back in last.
        Box(Modifier.align(Alignment.CenterStart).padding(start = (left - gap).coerceAtLeast(0.dp)).width(gap + 10.dp).height(14.dp).graphicsLayer {
            val e = sceneEase((ctx.reveal() / 0.45f).coerceIn(0f, 1f))
            scaleX = e; transformOrigin = TransformOrigin(0f, 0.5f); alpha = e
        }.clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2A3346), Color(0xFF0B0F18))))
            .border(1.dp, rim, RoundedCornerShape(3.dp)))
        Column(Modifier.align(Alignment.CenterStart).padding(start = left).width(w).heightIn(max = ctx.maxH).graphicsLayer {
            // Out of the deck's edge: the panel slides right from behind the tray as the tray reaches it; back in on close.
            val e = sceneEase(((ctx.reveal() - 0.15f) / 0.85f).coerceIn(0f, 1f))
            translationX = (1f - e) * -48.dp.toPx()
            alpha = e
            shape = RoundedCornerShape(8.dp); clip = true
            shadowElevation = 14.dp.toPx() * e
        }.background(metal).border(1.dp, rim, RoundedCornerShape(8.dp)).padding(12.dp)) {
            // The tray slot line and the disc-kind plate.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NOW LOADED", color = lcd.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 12.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF05080D)))
                Spacer(Modifier.width(10.dp))
                Text(labels.first, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, lineHeight = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            // The LCD: the title on the display, the facts as its readouts.
            // (673e, D328, Flip 2 x1.3 gate f673g: the LCD's lines kept the theme's 24 sp line height round 9-10 sp readouts and
            // the name kept three lines' height for a two-line name - its facts had no room even with the keys folded, and with
            // More open its story cue was laid out 0 px high. The readouts get their own line heights, the name the lines it
            // needs (a window's own text, rule 478); the room goes to the facts.)
            // (673f: a page change is a track change on the display - the old track scrolls off, the next one comes up from
            // the other edge, forward upward and back downward; the LCD grows to the page's list and back with it.)
            androidx.compose.animation.AnimatedContent(
                targetState = ctx.page,
                transitionSpec = {
                    if (!anim) (androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None)
                        .using(androidx.compose.animation.SizeTransform(clip = true) { _, _ -> androidx.compose.animation.core.snap() })
                    else {
                        val dir = pageDirection(DISC_TRACKS, initialState, targetState)
                        (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { h -> h * dir / 3 } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(NATIVE_PAGE_MS / 2, delayMillis = NATIVE_PAGE_MS / 4))) togetherWith
                            (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { h -> -h * dir / 5 } +
                                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(NATIVE_PAGE_MS / 3))) using
                            androidx.compose.animation.SizeTransform(clip = true) { _, _ -> androidx.compose.animation.core.tween(NATIVE_PAGE_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing) }
                    }
                },
                contentKey = { it },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).background(lcdBg)
                    .border(2.dp, Color(0xFF232B3B), RoundedCornerShape(6.dp)),
                label = "discTrack"
            ) { p ->
                val live = p == ctx.page
                androidx.compose.runtime.CompositionLocalProvider(LocalShownPage provides p) {
                Column(Modifier.fillMaxWidth().then(if (p == WinPage.MAIN) Modifier else Modifier.fillMaxHeight()).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (p == WinPage.MAIN) labels.second else discTrackLabel(p, tracks), color = lcd.copy(alpha = 0.7f), fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (p == WinPage.MAIN) ctx.played.uppercase() else winPageTitle(p).uppercase(), color = lcd, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, lineHeight = 13.sp, fontWeight = if (p == WinPage.MAIN) null else FontWeight.Bold)
                    }
                    if (p == WinPage.MAIN) {
                        StableFullText(ctx.name.uppercase(), color = lcd, fontSize = 18.sp, maxLines = 3, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, reserve = false)
                        Spacer(Modifier.height(4.dp))
                        SceneFactsBody(ctx, lcd.copy(alpha = 0.55f), lcd, FontFamily.Monospace, cueLineHeight = 12.sp)
                    } else {
                        // The track itself: the page's list on the display (its paper is the LCD; the disc in the deck is the
                        // cover, the name stays the page's own heading).
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            androidx.compose.runtime.CompositionLocalProvider(LocalNativePageHost provides NativePageHost(Color.Transparent, dark = true, showsCover = true, showsName = false)) {
                                ctx.pageFor(p, Modifier.fillMaxSize(), live)
                            }
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(10.dp))
            // The transport keys.
            // (673d, D327, KONKR x1.3 gate k673f: with More open the keys wrapped to rows that took the whole deck, the LCD's title
            // squeezed to nothing and its DISC / PLAYED readouts cut. The keys take at most 45 % of the panel's height and scroll
            // past it (the D-pad brings the key it is on into view); the LCD keeps the rest. Where they fit, nothing changes.)
            val keyScroll = rememberScrollState()
            Box(Modifier.heightIn(max = ctx.maxH * 0.45f).sceneFadeEdges(keyScroll).verticalScroll(keyScroll)) {
                KeyRow(sceneKeys(ctx), 8.dp) { a ->
                    SceneKey(a, Color(0xFF263044), Color(0xFF121826), Color(0xFFE6E8EB), lcd, Color.Black.copy(alpha = 0.5f), lcd, RoundedCornerShape(4.dp),
                        font = FontFamily.Monospace, upper = true, size = 11.sp, height = 32.dp)
                }
            }
        }
    }
}

/**
 * Pure (Update 506): where Disc station's panel stands in the window's room ([maxW] x [maxH] dp, the screen less 22 / 14 dp
 * margins) — (left, width) in dp. It starts just right of the home's deck (StyleDiscStation: the deck is min(52 % of the
 * width, the height less its margins) wide, 14 dp in, 8 dp on short screens); never narrower than 300 dp (it then overlaps the
 * deck's edge rather than squeeze its keys).
 */
internal fun discPanelGeometry(maxW: Float, maxH: Float): Pair<Float, Float> {
    val screenW = maxW + 44f; val screenH = maxH + 28f
    val pad = if (screenH < 330f) 8f else 14f
    val deckRight = pad + minOf(screenW * 0.52f, screenH - pad * 2) - 22f
    val minW = minOf(300f, maxW)
    val left = (deckRight + 12f).coerceIn(0f, maxW - minW)
    return left to (maxW - left).coerceAtMost(620f)
}

/** 673f: Disc station's pages as the deck's tracks, in order (L1 / R1 walk them). */
internal val DISC_TRACKS = listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.TROPHIES, WinPage.OPTIONS)

/**
 * 673f (pure): the LCD's track line for page [p] - "DISC 1 · TRACK 02" for the second of the game's [tracks] (the pages it has;
 * a game without achievements plays Options as track 03). A page not in [tracks] counts after them.
 */
internal fun discTrackLabel(p: WinPage, tracks: List<WinPage>): String {
    val i = tracks.indexOf(p).let { if (it < 0) tracks.size else it }
    return "DISC 1 · TRACK " + (i + 1).toString().padStart(2, '0')
}

// ───────────────────────────── Green Core: a glowing system console ─────────────────────────────

@Composable
internal fun CorePanel(ctx: WinSceneCtx) {
    // (Update 425: the layout's own glow colour — Layout options › accent (greenCoreAccent) — not one fixed neon green.)
    val glow = greenCoreAccent()
    val dim = androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.86f)
    val ink = androidx.compose.ui.graphics.lerp(glow, Color.White, 0.78f)
    val w = minOf(ctx.maxW, 600.dp)
    Column(Modifier.width(w).heightIn(max = ctx.maxH).graphicsLayer {
        // Opens from one glowing line, the console's own boot.
        val e = sceneEase(ctx.reveal())
        scaleY = 0.04f + 0.96f * e
        transformOrigin = TransformOrigin(0.5f, 0.5f)
        alpha = (ctx.reveal() * 3f).coerceIn(0f, 1f)
    }.clip(CutCornerShapeCompat).background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.93f).copy(alpha = 0.96f), androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.97f).copy(alpha = 0.98f))))
        .border(1.5.dp, glow.copy(alpha = 0.8f), CutCornerShapeCompat)
        // (740a, P2 class C: L1 / R1 from anywhere on the console - its own pages SPEC / UNLOCKS / CONTROL, WinPagesCore.kt.)
        .nativeBumpers(ctx).padding(14.dp)) {
        Text("// SYSTEM  ›  TITLE", color = glow.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        StableFullText(ctx.name, color = ink, fontSize = 22.sp, maxLines = 2, fontWeight = FontWeight.Bold)
        Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(glow, glow.copy(alpha = 0f)))))
        if (ctx.page == WinPage.MAIN) Row(Modifier.weight(1f, fill = false)) {
            // (Update 423: the cover follows the room left under the keys — sized by its width it grew past the row and covered
            // the title once the keys wrapped onto more lines.)
            Box(Modifier.widthIn(max = minOf(w * 0.34f, 180.dp)).aspectRatio(0.75f, matchHeightConstraintsFirst = true).border(1.dp, glow.copy(alpha = 0.5f)).background(dim)) {
                ctx.cover?.let { SmartArt(it, null, Modifier.fillMaxSize().padding(2.dp), well = dim) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { SceneFactsBody(ctx, glow.copy(alpha = 0.6f), ink, FontFamily.Monospace) }
        } else {
            // (740a, P2 class C: the console's own pages read out under the glow rule - SPEC / UNLOCKS / CONTROL in its monospace
            // capitals and glow (WinPagesCore.kt); was the cover with the shared page bodies in the facts' place. On its pages the
            // console takes its whole height and its keys keep the page slots, Play first, so Play stays put.)
            val short = ctx.maxH < 380.dp || (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.1f && ctx.maxH < 480.dp)
            CorePageArea(ctx, CoreInk(glow, ink, dim), Modifier.weight(1f).fillMaxWidth(), short)
        }
        Spacer(Modifier.height(10.dp))
        KeyRow(sceneKeys(ctx), slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS) { a ->
            SceneKey(a, dim, glow, glow, androidx.compose.ui.graphics.lerp(glow, Color.Black, 0.9f), glow.copy(alpha = 0.4f), glow, CutCornerShapeCompat, font = FontFamily.Monospace, upper = true, size = 11.sp)
        }
    }
}

/** Corners cut at 45°, the Green Core's frame. */
private val CutCornerShapeCompat = androidx.compose.foundation.shape.CutCornerShape(10.dp)

// ───────────────────────────── Cube menu: the info on a cube face turning toward you ─────────────────────────────

// (Update 507: CubeFace replaced — WinSceneFamilies.kt.)

// ───────────────────────────── Pocket layouts: a handheld's small LCD ─────────────────────────────

@Composable
internal fun PocketScreen(ctx: WinSceneCtx) {
    // Four greens, the classic handheld's screen.
    val c0 = Color(0xFF0F380F); val c1 = Color(0xFF306230); val c2 = Color(0xFF8BAC0F); val c3 = Color(0xFF9BBC0F)
    val w = minOf(ctx.maxW, 420.dp)
    val h = minOf(ctx.maxH, 320.dp)
    Box(Modifier.width(w).height(h).graphicsLayer {
        // Power on: a line across the screen opens to the picture.
        val e = sceneEase(ctx.reveal())
        scaleY = 0.03f + 0.97f * e
        scaleX = 0.92f + 0.08f * e
        alpha = (ctx.reveal() * 3f).coerceIn(0f, 1f)
    }.clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 34.dp)).background(Color(0xFF4A4E5A)).padding(10.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(c3).border(3.dp, c0.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(minOf(h * 0.28f, 72.dp)).background(c2).border(2.dp, c0)) {
                    ctx.cover?.let { SmartArt(it, null, Modifier.fillMaxSize().padding(2.dp).graphicsLayer { alpha = 0.92f }, well = c2) }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    StableFullText(ctx.name.uppercase(), color = c0, fontSize = 14.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                    Text(ctx.console.uppercase(), color = c1, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(4.dp))
            SceneFactsBody(ctx, c1, c0, FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            KeyRow(sceneKeys(ctx), 4.dp) { a ->
                SceneKey(a, c2, c0, c0, c3, c1, c0, RoundedCornerShape(2.dp), font = FontFamily.Monospace, upper = true, size = 10.sp, pad = 6.dp, height = 24.dp)
            }
        }
    }
}

// ───────────────────────────── Shelf / stacks / coverflow: a library catalogue card ─────────────────────────────

@Composable
internal fun IndexCard(ctx: WinSceneCtx) {
    val paper = Color(0xFFFBF7EC); val rule = Color(0xFFB9D4EA); val red = Color(0xFFC94A3F); val ink = Color(0xFF2A2A30)
    val w = minOf(ctx.maxW, 470.dp)
    // (Update 424: the card grows with its keys — they wrap onto more lines now — up to the screen; at a fixed 360 dp the facts were
    // squeezed to a sliver cut mid-line on the Thor.)
    Box(Modifier.width(w).heightIn(min = minOf(ctx.maxH, 300.dp), max = ctx.maxH).graphicsLayer {
        // Drawn up out of the catalogue drawer, beside the case taken off the shelf.
        val e = sceneEase(ctx.reveal())
        translationY = (1f - e) * size.height * 0.2f
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shadowElevation = 10.dp.toPx() * e
    }.clip(RoundedCornerShape(3.dp)).background(paper)) {
        Canvas(Modifier.matchParentSize()) {
            // The ruled lines, the red header line and the drawer rod's hole.
            val step = 22.dp.toPx(); var y = 58.dp.toPx()
            while (y < size.height) { drawLine(rule, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            drawLine(red, Offset(0f, 50.dp.toPx()), Offset(size.width, 50.dp.toPx()), 2f)
            drawLine(red.copy(alpha = 0.5f), Offset(44.dp.toPx(), 0f), Offset(44.dp.toPx(), size.height), 1f)
            drawCircle(Color(0xFFD8D2C2), 7.dp.toPx(), Offset(size.width / 2f, size.height - 12.dp.toPx()))
        }
        Column(Modifier.fillMaxWidth().padding(start = 52.dp, end = 14.dp, top = 10.dp, bottom = 24.dp)) {
            StableFullText(ctx.name, color = ink, fontSize = 18.sp, maxLines = 2, fontWeight = FontWeight.Bold)
            Text(ctx.console + (ctx.year?.let { " · $it" } ?: ""), color = ink.copy(alpha = 0.65f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            SceneFactsBody(ctx, red.copy(alpha = 0.8f), ink, FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            KeyRow(sceneKeys(ctx)) { a ->
                SceneKey(a, if (a.primary) ink else paper, red, if (a.primary) paper else ink, Color.White, ink.copy(alpha = 0.4f), red, RoundedCornerShape(2.dp),
                    font = FontFamily.Monospace, size = 11.sp)
            }
        }
    }
}

// (Twin layouts: TwinHinge moved to WinSceneTwin.kt — v0.6.66, D142.)

// ───────────────────────────── Artwork library: the artwork grows into a gallery wall with its placard ─────────────────────────────

@Composable
internal fun GalleryPlacard(ctx: WinSceneCtx) {
    val w = minOf(ctx.maxW, 720.dp)
    val h = minOf(ctx.maxH, 520.dp)
    // (Update 505: the Art library's own dark, themed gallery — the wall its glass panels are, the frames darker, the key you're on
    // in the theme's accent — instead of a fixed pale museum wall. The mat and the white placard stay: the work is shown whole.)
    val wall = themeDark(Color(0xFF141B27), 0.6f); val ink = Color(0xFF22201C)
    val lit = sceneLit(com.shelf.launcher.data.HomeLayout.ARTLIB, com.shelf.launcher.ui.theme.ShelfTheme.accent, wall)
    Column(Modifier.width(w).height(h).graphicsLayer {
        // The picture grows out of its place in the library, the wall fading in round it.
        val e = sceneEase(ctx.reveal())
        val s = 0.86f + 0.14f * e
        scaleX = s; scaleY = s
        alpha = (ctx.reveal() * 2f).coerceIn(0f, 1f)
        shape = RoundedCornerShape(4.dp); clip = true
    }.background(wall).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The work itself, whole, in a dark frame with a mat — never stretched.
        Box(Modifier.weight(1f).fillMaxWidth().background(sceneDark(wall, 0.02f)).border(1.dp, lit.copy(alpha = 0.35f)).padding(6.dp).background(Color(0xFFF7F5F0)).padding(10.dp)) {
            if (ctx.page == WinPage.MAIN) {
                val art = ctx.backdrop ?: ctx.wide ?: ctx.cover
                // (Batch 523: the work in a frame of ITS shape - the mat hugs it instead of a whole picture floating in a wide mat.)
                if (art != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var shape by remember(art) { mutableStateOf(CoverAspects.rawOf(art)) }
                    AsyncImage(rememberStyleArtRequest(art), ctx.name,
                        Modifier.then(shape?.let { Modifier.aspectRatio(it.coerceIn(0.4f, 3f), matchHeightConstraintsFirst = true) } ?: Modifier.fillMaxSize()),
                        contentScale = ContentScale.Fit,
                        onSuccess = { st -> val sz = st.painter.intrinsicSize; if (sz.width > 0f && sz.height > 0f && sz.width.isFinite()) { CoverAspects.note(art, sz.width, sz.height); shape = sz.width / sz.height } })
                }
            } else ctx.pageBody(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        // The placard.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).background(Color.White).border(1.dp, ink.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                StableFullText(ctx.name, color = ink, fontSize = 15.sp, maxLines = 2, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(ctx.developer, ctx.year, ctx.console).joinToString(", "), color = ink.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Serif)
                Text(ctx.played + (if (ctx.total > 0) "  ·  ${ctx.earned} / ${ctx.total} trophies" else ""), color = ink.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Serif)
            }
        }
        Spacer(Modifier.height(8.dp))
        KeyRow(sceneKeys(ctx)) { a ->
            SceneKey(a, if (a.primary) lit else Color.White, lit, if (a.primary) com.shelf.launcher.ui.theme.textOn(lit, Color.White) else ink, com.shelf.launcher.ui.theme.textOn(lit, Color.White),
                Color.White.copy(alpha = 0.3f), Color.White, RoundedCornerShape(2.dp))
        }
    }
}

